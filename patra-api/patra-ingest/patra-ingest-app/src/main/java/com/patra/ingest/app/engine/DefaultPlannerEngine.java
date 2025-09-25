package com.patra.ingest.app.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanAssembly;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.app.strategy.SliceStrategyRegistry;
import com.patra.ingest.app.strategy.SliceStrategy;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.app.model.PlanBlueprintCommand;
import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;
import com.patra.ingest.app.strategy.model.SliceContext;
import com.patra.ingest.app.strategy.model.SliceDraft;
import com.patra.ingest.app.model.PlanBusinessExpr;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private ObjectMapper objectMapper;

    public DefaultPlannerEngine(SliceStrategyRegistry sliceStrategyRegistry) {
        this.sliceStrategyRegistry = sliceStrategyRegistry;
    }

    @Override
    public PlanAssembly assemble(PlanBlueprintCommand command) {
        PlanTriggerNorm norm = command.triggerNorm();
        PlannerWindow window = command.window();
        PlanBusinessExpr planBusinessExpr = command.planBusinessExpr();
        String planExprHash = planBusinessExpr.hash();
        String planExprJson = planBusinessExpr.jsonSnapshot();
        ProvenanceConfigSnapshot config = command.configSnapshot();

        String planKey = buildPlanKey(norm, window);
        PlanAggregate plan = PlanAggregate.create(
                norm.scheduleInstanceId(),
                planKey,
                norm.provenanceCode().getCode(),
                norm.endpoint().name().toLowerCase(),
                norm.operationCode().name(),
                planExprHash,
                planExprJson,
                serializeConfigSnapshot(config),
                null,
                window.from(),
                window.to(),
                determineSliceStrategy(norm),
                buildSliceParams(norm));
        plan.startSlicing();

        SliceStrategy strategy = sliceStrategyRegistry.get(determineSliceStrategy(norm));
        List<SliceDraft> drafts = strategy == null
                ? new ArrayList<>()
                : strategy.slice(new SliceContext(norm, window, planBusinessExpr, config));

        List<PlanSliceAggregate> slices = new ArrayList<>(drafts.size());
        for (SliceDraft d : drafts) {
            slices.add(PlanSliceAggregate.create(
                    null,
                    norm.provenanceCode().getCode(),
                    d.sequence(),
                    d.sliceSignatureSeed(),
                    d.sliceSpecJson(),
                    d.sliceExprHash(),
                    d.sliceExprJson()));
        }

        List<TaskAggregate> tasks = buildTasks(norm, slices);

        if (slices.isEmpty() || tasks.isEmpty()) {
            plan.markFailed();
            return new PlanAssembly(plan, slices, tasks, PlanAssembly.PlanAssemblyStatus.FAILED);
        }

        plan.markReady();
        return new PlanAssembly(plan, slices, tasks, PlanAssembly.PlanAssemblyStatus.READY);
    }

    // buildSlices moved to dedicated strategies (TimeSliceStrategy / SingleSliceStrategy)

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
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
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

