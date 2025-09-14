package com.patra.ingest.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.app.model.registry.ProvenanceConfigSnapshot;
import com.patra.ingest.app.usecase.IngestRuntimeContext;
import com.patra.ingest.app.usecase.StartPlanUseCase;
import com.patra.ingest.app.usecase.command.StartPlanCommand;
import com.patra.ingest.domain.model.aggregate.Plan;
import com.patra.ingest.domain.model.aggregate.ScheduleInstance;
import com.patra.ingest.domain.model.aggregate.Task;
import com.patra.ingest.domain.model.entity.PlanSlice;
import com.patra.ingest.domain.model.enums.OperationType;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.ingest.domain.model.enums.SliceStrategy;
import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.port.ScheduleInstanceRepository;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.starter.expr.compiler.ExprCompiler;
import com.patra.expr.Expr;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * 用例实现（app 层）：仅做编排与落库调用，不做外部抓取。
 * 步骤：
 * 1) schedule_instance 落库
 * 2) plan 落库（记录窗口/策略/expr 原型）
 * 3) 生成时间切片 + 局部化表达式（MVP：单窗即单 slice；后续可扩月/日粒度）
 * 4) 对每个 slice 生成 task 并落库（幂等键）
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

    @Override
    @Transactional
    public Long startPlan(StartPlanCommand command, IngestRuntimeContext context) {
        requireNonNull(command, "command");
        requireNonNull(context, "context");

        // 0) 读取来源/操作等上下文
        ProvenanceConfigSnapshot cfg = requireNonNull(context.getProvenanceConfig(), "provenanceConfig");
        var provenance = cfg.provenanceCode();
        var operation = OperationType.HARVEST; // 本 Job 固定为 HARVEST

        var dateField = Optional.ofNullable(cfg.dateFieldDefault()).filter(s -> !s.isBlank())
                .orElse("PDAT"); // 兜底一个字段名，具体渲染由 expr 模块决定

        log.info("开始执行计划启动流程，来源：{}，操作：{}，时间窗口：{} - {}",
                provenance, operation, command.getWindowFromExclusive(), command.getWindowToInclusive());

        // 1) schedule_instance 落库
        ScheduleInstance scheduleInstance = createScheduleInstance(command, context, cfg);
        ScheduleInstance savedScheduleInstance = scheduleInstanceRepository.save(scheduleInstance);
        Long scheduleInstanceId = savedScheduleInstance.getId();

        log.info("调度实例已创建，ID：{}", scheduleInstanceId);

        // 2) plan 落库（先写为 ready/active），记录窗口/策略/expr 原型/切片策略（含 overlapDays）
        Plan plan = createPlan(command, scheduleInstanceId, cfg);
        Plan savedPlan = planRepository.save(plan);
        Long planId = savedPlan.getId();

        // 将计划状态转为就绪并激活
        savedPlan.ready();
        savedPlan.activate();
        planRepository.save(savedPlan);

        log.info("计划已创建并激活，ID：{}", planId);

        // 3) 切片（MVP：当前窗口即 1 个 slice；若后续按月/日切片，可替换此段）

        log.info("生成了 {} 个切片", slices.size());

        // 4) 为每个 slice 落库：slice → task
        for ( :slices){
            // 保存切片


            // 创建任务


            log.info("切片 {} 和对应任务已创建", savedSlice.getId());
        }

        log.info("计划启动流程完成，计划ID：{}", planId);
        return planId;
    }

}
