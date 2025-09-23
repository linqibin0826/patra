package com.patra.ingest.app.engine;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanAssembly;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 默认计划编排引擎：从蓝图命令生成 Plan/Slices/Tasks。
 */
@Component
public class DefaultPlannerEngine implements PlannerEngine {

    private static final Duration DEFAULT_SLICE_STEP = Duration.ofHours(1);

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

        List<PlanSliceAggregate> slices = buildSlices(norm, window, exprArtifacts);
        List<TaskAggregate> tasks = buildTasks(norm, slices, exprArtifacts);

        if (slices.isEmpty() || tasks.isEmpty()) {
            plan.markFailed();
            return new PlanAssembly(plan, slices, tasks, PlanAssembly.PlanAssemblyStatus.FAILED);
        }

        plan.markReady();
        return new PlanAssembly(plan, slices, tasks, PlanAssembly.PlanAssemblyStatus.READY);
    }

    private List<PlanSliceAggregate> buildSlices(PlanTriggerNorm norm,
                                                 PlannerWindow window,
                                                 ExprPlanArtifacts exprArtifacts) {
        List<PlanSliceAggregate> result = new ArrayList<>();
        if (norm.isUpdate()) {
            result.add(PlanSliceAggregate.create(
                    null,
                    norm.provenanceCode().getCode(),
                    1,
                    computeSignature(norm, "UPDATE"),
                    "{\"type\":\"SINGLE\"}",
                    exprArtifacts.sliceTemplates().isEmpty() ? exprArtifacts.exprProtoHash() : exprArtifacts.sliceTemplates().getFirst().exprHash(),
                    exprArtifacts.sliceTemplates().isEmpty() ? exprArtifacts.exprProtoSnapshotJson() : exprArtifacts.sliceTemplates().getFirst().exprSnapshotJson()));
            return result;
        }

        Instant from = window.from();
        Instant to = window.to();
        if (from == null || to == null) {
            return result;
        }

        Duration step = DEFAULT_SLICE_STEP;
        Instant cursor = from;
        int index = 1;
        while (cursor.isBefore(to)) {
            Instant upper = cursor.plus(step);
            if (upper.isAfter(to)) {
                upper = to;
            }
            String specJson = "{\"type\":\"TIME\",\"from\":" + cursor.toString() + ",\"to\":" + upper.toString() + "}";
            String signature = computeSignature(norm, specJson + index);
            String exprHash = exprArtifacts.sliceTemplates().isEmpty()
                    ? exprArtifacts.exprProtoHash()
                    : exprArtifacts.sliceTemplates().getFirst().exprHash();
            String exprSnapshot = exprArtifacts.sliceTemplates().isEmpty()
                    ? exprArtifacts.exprProtoSnapshotJson()
                    : exprArtifacts.sliceTemplates().getFirst().exprSnapshotJson();
            result.add(PlanSliceAggregate.create(
                    null,
                    norm.provenanceCode().getCode(),
                    index,
                    signature,
                    specJson,
                    exprHash,
                    exprSnapshot));
            cursor = upper;
            index++;
        }
        return result;
    }

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

