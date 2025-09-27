package com.patra.ingest.app.orchestration.assembly;

import com.patra.common.util.HashUtils;
import com.patra.ingest.app.orchestration.expression.PlanExpressionDescriptor;
import com.patra.ingest.app.orchestration.slice.SlicePlanner;
import com.patra.ingest.app.orchestration.slice.SlicePlannerRegistry;
import com.patra.ingest.app.orchestration.slice.model.SlicePlan;
import com.patra.expr.canonical.ExprCanonicalizer;
import com.patra.expr.canonical.ExprCanonicalSnapshot;
import com.patra.ingest.app.orchestration.slice.model.SlicePlanningContext;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanAssembly;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;
import com.patra.common.json.JsonNormalizer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Default coordinator that materializes plan aggregate, slices, and tasks from a trigger request.
 */
@Component
public class DefaultPlanAssemblyService implements PlanAssemblyService {

    private static final String SLICE_STRATEGY_SINGLE = "SINGLE";
    private static final String SLICE_STRATEGY_TIME = "TIME";

    private final SlicePlannerRegistry slicePlannerRegistry;

    public DefaultPlanAssemblyService(SlicePlannerRegistry slicePlannerRegistry) {
        this.slicePlannerRegistry = slicePlannerRegistry;
    }

    @Override
    public PlanAssembly assemble(PlanAssemblyRequest request) {
        PlanTriggerNorm norm = request.triggerNorm();
        PlannerWindow window = request.window();
        PlanExpressionDescriptor planExpression = request.planExpression();
        String planExprHash = planExpression.hash();
        String planExprJson = planExpression.jsonSnapshot();
        ProvenanceConfigSnapshot config = request.configSnapshot();
        JsonNormalizer.Result configSnapshot = normalizeConfigSnapshot(config);
        String configSnapshotJson = configSnapshot == null ? null : configSnapshot.getCanonicalJson();
        String configSnapshotHash = configSnapshot == null ? null : HashUtils.sha256Hex(configSnapshot.getHashMaterial());

        String planKey = buildPlanKey(norm, window);
        PlanAggregate plan = PlanAggregate.create(
                norm.scheduleInstanceId(),
                planKey,
                norm.provenanceCode().getCode(),
                norm.endpoint() == null ? null : norm.endpoint().name(),
                norm.operationCode() == null ? null : norm.operationCode().name(),
                planExprHash,
                planExprJson,
                configSnapshotJson,
                configSnapshotHash,
                window.from(),
                window.to(),
                determineSliceStrategy(norm),
                buildSliceParams(norm));
        plan.startSlicing();

        SlicePlanner planner = slicePlannerRegistry.get(determineSliceStrategy(norm));
        List<SlicePlan> drafts = planner == null
                ? new ArrayList<>()
                : planner.slice(new SlicePlanningContext(norm, window, planExpression, config));

        List<PlanSliceAggregate> slices = new ArrayList<>(drafts.size());
        for (SlicePlan d : drafts) {
            ExprCanonicalSnapshot sliceSnapshot = ExprCanonicalizer.canonicalize(d.sliceExpr());
            slices.add(PlanSliceAggregate.create(
                    null,
                    norm.provenanceCode().getCode(),
                    d.sequence(),
                    d.sliceSignatureSeed(),
                    d.sliceSpecJson(),
                    sliceSnapshot.hash(),
                    sliceSnapshot.canonicalJson()));
        }

        List<TaskAggregate> tasks = buildTasks(norm, slices);

        if (slices.isEmpty() || tasks.isEmpty()) {
            plan.markFailed();
            return new PlanAssembly(plan, slices, tasks, PlanAssembly.PlanAssemblyStatus.FAILED);
        }

        plan.markReady();
        return new PlanAssembly(plan, slices, tasks, PlanAssembly.PlanAssemblyStatus.READY);
    }

    // buildSlices moved to dedicated planners (TimeSlicePlanner / SingleSlicePlanner)

    private List<TaskAggregate> buildTasks(PlanTriggerNorm norm,
                                           List<PlanSliceAggregate> slices) {
        List<TaskAggregate> tasks = new ArrayList<>(slices.size());
        for (PlanSliceAggregate slice : slices) {
            String idemKey = computeSignature(norm, slice.getSliceSignatureHash());
            Integer priorityVal = norm.priority() == null ? null : norm.priority().ordinal();
            tasks.add(TaskAggregate.create(
                    norm.scheduleInstanceId(),
                    null,
                    (long) slice.getSequence(),
                    norm.provenanceCode().getCode(),
                    norm.operationCode().name(),
                    buildTaskParamsJson(slice),
                    idemKey,
                    slice.getExprHash(),
                    priorityVal,
                    norm.requestedWindowFrom()));
        }
        return tasks;
    }

    private String buildPlanKey(PlanTriggerNorm norm, PlannerWindow window) {
        StringBuilder builder = new StringBuilder();
        builder.append(norm.provenanceCode().getCode()).append(":").append(norm.operationCode().name());
        if (norm.endpoint() != null) {
            builder.append(":").append(norm.endpoint().name().toLowerCase());
        }
        if (window.from() != null && window.to() != null) {
            builder.append(":").append(window.from().toEpochMilli()).append("-").append(window.to().toEpochMilli());
        }
        return builder.toString();
    }

    private String determineSliceStrategy(PlanTriggerNorm norm) {
        if (norm.isUpdate()) {
            return SLICE_STRATEGY_SINGLE;
        }
        return SLICE_STRATEGY_TIME;
    }

    private String buildSliceParams(PlanTriggerNorm norm) {
        String strategy = determineSliceStrategy(norm);
        JsonNormalizer.Result normalized = JsonNormalizer.normalizeDefault(Map.of("strategy", strategy));
        return normalized.getCanonicalJson();
    }

    private String buildTaskParamsJson(PlanSliceAggregate slice) {
        JsonNormalizer.Result normalized = JsonNormalizer.normalizeDefault(Map.of("sliceNo", slice.getSequence()));
        return normalized.getCanonicalJson();
    }

    private JsonNormalizer.Result normalizeConfigSnapshot(ProvenanceConfigSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return JsonNormalizer.normalizeDefault(snapshot);
    }

    private String computeSignature(PlanTriggerNorm norm, String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((norm.provenanceCode().getCode() + "|" + norm.operationCode().name()).getBytes(StandardCharsets.UTF_8));
            digest.update(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("cannot compute signature", e);
        }
    }
}
