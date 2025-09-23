package com.patra.ingest.adapter.in.scheduler.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.enums.Priority;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.ingest.domain.model.enums.SchedulerCode;
import com.patra.ingest.app.command.PlanTriggerCommand;
import com.patra.ingest.app.dto.PlanTriggerResult;
import com.patra.ingest.app.usecase.PlanTriggerUseCase;
import com.patra.ingest.domain.model.enums.EndpointCode;
import com.patra.ingest.domain.model.enums.OperationType;
import com.xxl.job.core.context.XxlJobHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Map;

/**
 * 抽象的 Provenance 调度任务基类。
 *
 * <p>为各个数据来源提供统一的XXL-Job调度任务框架，包含：
 * - JSON参数解析和验证
 * - 调用app层的triggerPlan方法
 * - 错误处理和日志记录
 * - 子类只需实现getProvenanceCode方法</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public abstract class AbstractProvenanceScheduleJob {

    @Autowired
    private PlanTriggerUseCase planTriggerUseCase;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 获取当前任务对应的来源代码。
     */
    protected abstract ProvenanceCode getProvenanceCode();

    /**
     * 获取当前任务对应的操作类型（由子类固定）。
     */
    protected abstract OperationType getOperationType();

    /**
     * 获取端点名称，默认 SEARCH，子类可覆盖。
     */
    protected EndpointCode getEndpointName() {
        return EndpointCode.SEARCH;
    }

    /**
     * 解析XXL-Job参数。
     *
     * @param paramStr JSON格式的参数字符串
     * @return 解析后的PlanTriggerCommand对象
     */
    protected PlanTriggerCommand parseJobParam(String paramStr) {
        // 空参数 -> 空 Map
        if (paramStr == null || paramStr.trim().isEmpty()) {
            return new PlanTriggerCommand(
                    getProvenanceCode(),
                    getEndpointName(),
                    getOperationType(),
                    "PT6H",
                    TriggerType.SCHEDULE,
                    SchedulerCode.XXL,
                    XxlJobHelper.getJobId() + "",
                    "0",
                    null,
                    null,
                    null,
                    Map.of()
            );
        }
        try {
            JsonNode root = objectMapper.readTree(paramStr);
            if (!root.isObject()) {
                throw new IllegalArgumentException("任务参数必须为JSON对象");
            }
            Map<String, Object> triggerParams = objectMapper.convertValue(root, new TypeReference<>() {
            });

            Instant windowFrom = null;
            if (root.hasNonNull("windowFrom")) {
                String wf = root.get("windowFrom").asText();
                if (!wf.isEmpty()) windowFrom = Instant.parse(wf);
            }
            Instant windowTo = null;
            if (root.hasNonNull("windowTo")) {
                String wt = root.get("windowTo").asText();
                if (!wt.isEmpty()) windowTo = Instant.parse(wt);
            }
            Priority priority = null;
            if (root.hasNonNull("priority")) {
                try {
                    priority = Priority.valueOf(root.get("priority").asText().toUpperCase());
                } catch (Exception ignored) { /* 忽略无法解析的优先级 */ }
            }
            return new PlanTriggerCommand(
                    getProvenanceCode(),
                    getEndpointName(),
                    getOperationType(),
                    "PT6H",
                    TriggerType.SCHEDULE,
                    SchedulerCode.XXL,
                    XxlJobHelper.getJobId() + "",
                    "0",
                    windowFrom,
                    windowTo,
                    priority,
                    triggerParams
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON参数解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行调度任务的通用逻辑。
     *
     * @param paramStr XXL-Job JSON参数字符串
     */
    protected void executeScheduleJob(String paramStr) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("开始执行{}:{}:{} 调度任务, 参数: {}", getProvenanceCode().getCode(), getEndpointName(), getOperationType(), paramStr);

            PlanTriggerCommand command = parseJobParam(paramStr);
            PlanTriggerResult result = planTriggerUseCase.triggerPlan(command);

            long duration = System.currentTimeMillis() - startTime;
            log.info("{}:{}:{} 调度任务执行成功, 耗时: {}ms, planId: {}, taskCount: {}",
                    getProvenanceCode().getCode(), getEndpointName(), getOperationType(), duration, result.planId(), result.taskCount());

            XxlJobHelper.handleSuccess(String.format("任务执行成功，耗时%dms，planId=%s，taskCount=%d",
                    duration, result.planId(), result.taskCount()));

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("{}:{}:{} 调度任务执行失败, 耗时: {}ms, 错误: {}",
                    getProvenanceCode().getCode(), getEndpointName(), getOperationType(), duration, e.getMessage(), e);

            XxlJobHelper.handleFail(String.format("任务执行失败: %s", e.getMessage()));
            throw e;
        }
    }
}
