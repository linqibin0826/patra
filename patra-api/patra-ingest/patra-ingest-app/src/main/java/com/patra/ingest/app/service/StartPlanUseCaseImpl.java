package com.patra.ingest.app.service;

import cn.hutool.core.lang.Assert;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.model.registry.ProvenanceConfigSnapshot;
import com.patra.ingest.app.port.outbound.PatraRegistryProvenancePort;
import com.patra.ingest.app.usecase.StartPlanUseCase;
import com.patra.ingest.app.usecase.command.JobStartPlanCommand;
import com.patra.ingest.domain.model.enums.IngestOperationType;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.port.ScheduleInstanceRepository;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.starter.expr.compiler.ExprCompiler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    @Transactional
    public Long startPlan(JobStartPlanCommand command) {
        Assert.notNull(command, "startPlanCommand must not be null");

        ProvenanceCode provenanceCode = command.provenanceCode();
        Assert.notNull(provenanceCode, "provenanceCode must not be null");

        IngestOperationType ingestOperationType = command.ingestOperationType();
        Assert.notNull(ingestOperationType, "ingestOperationType must not be null");

        // Retrieve and convert Registry configuration to application layer snapshot
        ProvenanceConfigSnapshot provenanceConfiguration = retrieveProvenanceConfiguration(provenanceCode);

        return null;
    }


    private ProvenanceConfigSnapshot retrieveProvenanceConfiguration(ProvenanceCode provenanceCode) {
        // Retrieve configuration from Registry service
        return registryProvenancePort.getProvenanceConfigSnapshot(provenanceCode);

    }
}
