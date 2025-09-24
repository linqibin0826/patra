package com.patra.ingest.app.engine;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanAssembly;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.app.strategy.SliceStrategyRegistry;
import com.patra.ingest.app.strategy.SliceStrategy;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.command.PlanBlueprintCommand;
import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.expr.ExprPlanArtifacts;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 默认计划编排引擎：从蓝图命令生成 Plan/Slices/Tasks。
 */
@Component
public class DefaultPlannerEngine implements PlannerEngine {

    private final SliceStrategyRegistry sliceStrategyRegistry;

    public DefaultPlannerEngine(SliceStrategyRegistry sliceStrategyRegistry) {
        this.sliceStrategyRegistry = sliceStrategyRegistry;
    }

    @Override
    public PlanAssembly assemble(PlanBlueprintCommand command) {
        PlanTriggerNorm norm = command.triggerNorm();
        PlannerWindow window = command.window();
        ExprPlanArtifacts exprArtifacts = command.exprArtifacts();
        ProvenanceConfigSnapshot config = command.configSnapshot();

        String planKey = buildPlanKey(norm, window);
        PlanAggregate plan = PlanAggregate.create(
                norm.scheduleInstanceId(),
                planKey,
                norm.provenanceCode().getCode(),
                norm.endpointCode().name().toLowerCase(),
                norm.operationType().name(),
                exprArtifacts.exprProtoHash(),
                exprArtifacts.exprProtoSnapshotJson(),
                serializeConfigSnapshot(config),
                null,
                window.from(),
                window.to(),
                determineSliceStrategy(norm),
                buildSliceParams(norm));
        plan.startSlicing();

    SliceStrategy strategy = sliceStrategyRegistry.get(determineSliceStrategy(norm));
    List<PlanSliceAggregate> slices = strategy == null
        ? new ArrayList<>()
        : strategy.slice(norm, window, exprArtifacts);
        List<TaskAggregate> tasks = buildTasks(norm, slices, exprArtifacts);

        if (slices.isEmpty() || tasks.isEmpty()) {
            plan.markFailed();
            return new PlanAssembly(plan, slices, tasks, PlanAssembly.PlanAssemblyStatus.FAILED);
        }

        plan.markReady();
        return new PlanAssembly(plan, slices, tasks, PlanAssembly.PlanAssemblyStatus.READY);
    }

    // buildSlices moved to dedicated strategies (TimeSliceStrategy / SingleSliceStrategy)

    private List<TaskAggregate> buildTasks(PlanTriggerNorm norm,
                                           List<PlanSliceAggregate> slices,
                                           ExprPlanArtifacts exprArtifacts) {
        List<TaskAggregate> tasks = new ArrayList<>(slices.size());
        for (PlanSliceAggregate slice : slices) {
            String idemKey = computeSignature(norm, slice.getSliceSignatureHash());
        Integer priorityVal = norm.priority() == null ? null : norm.priority().ordinal();
        tasks.add(TaskAggregate.create(
            norm.scheduleInstanceId(),
            null,
            (long) slice.getSequence(),
            norm.provenanceCode().getCode(),
            norm.operationType().name(),
            null,
            buildTaskParamsJson(norm, slice),
            idemKey,
            slice.getExprHash(),
            priorityVal,
            norm.requestedWindowFrom()));
        }
        return tasks;
    }

    private String buildPlanKey(PlanTriggerNorm norm, PlannerWindow window) {
        StringBuilder builder = new StringBuilder();
        builder.append(norm.provenanceCode().getCode()).append(":").append(norm.operationType().name());
        if (norm.endpointCode() != null) {
            builder.append(":").append(norm.endpointCode().name().toLowerCase());
        }
        if (window.from() != null && window.to() != null) {
            builder.append(":").append(window.from().toEpochMilli()).append("-").append(window.to().toEpochMilli());
        }
        return builder.toString();
    }

    private String determineSliceStrategy(PlanTriggerNorm norm) {
        if (norm.isUpdate()) {
            return "SINGLE";
        }
        return "TIME";
    }

    private String buildSliceParams(PlanTriggerNorm norm) {
        if (norm.isUpdate()) {
            return "{\"strategy\":\"SINGLE\"}";
        }
        return "{\"strategy\":\"TIME\"}";
    }

    private String buildTaskParamsJson(PlanTriggerNorm norm, PlanSliceAggregate slice) {
        return "{\"sliceNo\":" + slice.getSequence() + "}";
    }

    private String serializeConfigSnapshot(ProvenanceConfigSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
    return snapshot.provenance() == null ? null : "{\"provenanceCode\":\"" + snapshot.provenance().code() + "\"}";
    }

    private String computeSignature(PlanTriggerNorm norm, String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((norm.provenanceCode().getCode() + "|" + norm.operationType().name()).getBytes(StandardCharsets.UTF_8));
            digest.update(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("cannot compute signature", e);
        }
    }
}

