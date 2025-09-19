package com.patra.ingest.app.service;

import cn.hutool.core.lang.Assert;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.model.registry.ProvenanceConfigSnapshot;
import com.patra.ingest.app.port.outbound.PatraRegistryProvenancePort;
import com.patra.ingest.app.usecase.StartPlanUseCase;
import com.patra.ingest.app.usecase.command.JobStartPlanCommand;
import com.patra.ingest.app.usecase.IngestRuntimeContext;
import com.patra.ingest.app.model.snapshot.SliceParamsSnapshot;
import com.patra.ingest.app.model.snapshot.TaskParamsSnapshot;
import com.patra.ingest.app.service.slicing.SlicingStrategy;
import com.patra.ingest.app.service.slicing.SlicingStrategyFactory;
import com.patra.ingest.domain.model.enums.IngestOperationType;
import com.patra.ingest.domain.model.aggregate.ScheduleInstance;
import com.patra.ingest.domain.model.aggregate.Plan;
import com.patra.ingest.domain.model.entity.PlanSlice;
import com.patra.ingest.domain.model.aggregate.Task;
import com.patra.ingest.domain.model.enums.SchedulerSource;
import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.ingest.domain.model.enums.SliceStrategy;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.port.ScheduleInstanceRepository;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.starter.expr.compiler.ExprCompiler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

/**
 * 用例实现（app 层）：仅做编排与落库调用，不做外部抓取。
 * 步骤：
 * 1) schedule_instance 落库
 * 2) plan 落库（记录窗口/策略/expr 原型）
 * 3) 生成时间切片 + 局部化表达式（MVP：单窗即单 slice；后续可扩月/日粒度）
 * 4) 对每个 slice 生成 task 并落库 (幂等键)
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StartPlanUseCaseImpl implements StartPlanUseCase {

    private final ScheduleInstanceRepository scheduleInstanceRepository;
    private final PlanRepository planRepository;
    private final PlanSliceRepository planSliceRepository;
    private final TaskRepository taskRepository;
    private final ExprCompiler exprCompiler;
    private final ObjectMapper objectMapper;

    private final PatraRegistryProvenancePort registryProvenancePort;
    private final SlicingStrategyFactory slicingStrategyFactory;

    @Override
    @Transactional
    public Long startPlan(JobStartPlanCommand command) {
        Assert.notNull(command, "startPlanCommand must not be null");

        ProvenanceCode provenanceCode = command.provenanceCode();
        Assert.notNull(provenanceCode, "provenanceCode must not be null");

        IngestOperationType ingestOperationType = command.ingestOperationType();
        Assert.notNull(ingestOperationType, "ingestOperationType must not be null");

        // 仅支持：PUBMED + HARVEST
        Assert.isTrue(provenanceCode == ProvenanceCode.PUBMED, "Only PUBMED is supported in MVP");
        Assert.isTrue(ingestOperationType == IngestOperationType.HARVEST, "Only HARVEST is supported in MVP");

        // 1) registry 配置快照（中立模型）
        ProvenanceConfigSnapshot cfg = retrieveProvenanceConfiguration(provenanceCode);

        // 2) schedule_instance 聚合（MVP：手动触发/XXL 标记，表达式原型暂置空）
        String exprProtoHash = "proto:none"; // MVP 简化：真实实现来自编译器/快照
        String exprProtoSnapshot = null;

        ScheduleInstance sched = ScheduleInstance.create(
                SchedulerSource.XXL,
                null,
                null,
                TriggerType.MANUAL,
                LocalDateTime.now(),
                null,
                provenanceCode,
                toJson(cfg),
                exprProtoHash,
                exprProtoSnapshot);
        sched = scheduleInstanceRepository.save(sched);

        // 3) plan 聚合（MVP：TIME 策略，窗口由 CursorSpec since/until 推导）
        LocalDateTime windowFrom = command.cursorSpec().since()
                .map(i -> LocalDateTime.ofInstant(i, ZoneOffset.UTC))
                .orElse(null);
        LocalDateTime windowTo = command.cursorSpec().until()
                .map(i -> LocalDateTime.ofInstant(i, ZoneOffset.UTC))
                .orElse(null);

        SliceParamsSnapshot sliceParams = SliceParamsSnapshot.builder()
                .strategy("TIME")
                .primaryDateField(cfg.dateFieldDefault())
                .overlapDays(cfg.windowPolicy() == null ? null : cfg.windowPolicy().overlapDays())
                .build();

        Plan plan = Plan.create(
                sched.getId(),
                buildPlanKey(provenanceCode, ingestOperationType),
                exprProtoHash,
                exprProtoSnapshot,
                windowFrom,
                windowTo,
                SliceStrategy.TIME,
                toJson(sliceParams),
                ingestOperationType);
        plan.ready();
        plan = planRepository.save(plan);

        // 4) 切片（MVP 单片）：策略选择 + 生成 slice 快照 JSON
        IngestRuntimeContext ctx = IngestRuntimeContext.ofDailyHarvest(cfg.timezone(), cfg, null);
        SlicingStrategy strategy = slicingStrategyFactory.select(provenanceCode, ingestOperationType);
        var drafts = strategy.slice(
                null, // base expr 暂为空，由策略以 CursorSpec 构造边界
                provenanceCode,
                ingestOperationType,
                exprCompiler,
                cfg,
                command,
                plan.getId(),
                ctx.getProvenanceZone());

        // 5) 落库 PlanSlice + Task（幂等键 = SHA256(sliceSignature + exprHash + operation +
        // trigger + normalized(params))）
        for (var d : drafts) {
            PlanSlice slice = PlanSlice.builder()
                    .planId(plan.getId())
                    .sliceNo(d.sliceNo())
                    .sliceSignatureHash(sha256(d.sliceSpecJson()))
                    .sliceSpec(d.sliceSpecJson())
                    .exprHash(sha256(d.exprSnapshotJson()))
                    .exprSnapshot(d.exprSnapshotJson())
                    .status(SliceStatus.PENDING)
                    .build();
            slice = planSliceRepository.save(slice);

            TaskParamsSnapshot taskParams = TaskParamsSnapshot.builder()
                    .planId(plan.getId())
                    .sliceId(slice.getId())
                    .sliceNo(slice.getSliceNo())
                    .provenance(provenanceCode.toJson())
                    .operation(ingestOperationType.toCode())
                    .exprHash(slice.getExprHash())
                    .sliceSpec(slice.getSliceSpec())
                    .build();

            String normalizedParams = toJson(taskParams);
            String idempotentKey = sha256(slice.getSliceSignatureHash() + "|" + slice.getExprHash() + "|"
                    + ingestOperationType.toCode() + "|manual|" + normalizedParams);

            Task task = Task.create(
                    sched.getId(),
                    plan.getId(),
                    slice.getId(),
                    provenanceCode,
                    ingestOperationType,
                    normalizedParams,
                    idempotentKey,
                    slice.getExprHash(),
                    5,
                    null);
            taskRepository.save(task);
        }

        return plan.getId();
    }

    private ProvenanceConfigSnapshot retrieveProvenanceConfiguration(ProvenanceCode provenanceCode) {
        // Retrieve configuration from Registry service
        return registryProvenancePort.getProvenanceConfigSnapshot(provenanceCode);

    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 failed", e);
        }
    }

    private String buildPlanKey(ProvenanceCode code, IngestOperationType op) {
        long ts = System.currentTimeMillis();
        return code.toJson() + ":" + op.toCode() + ":" + ts;
    }
}
