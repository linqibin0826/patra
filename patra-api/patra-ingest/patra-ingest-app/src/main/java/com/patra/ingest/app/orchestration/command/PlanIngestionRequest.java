package com.patra.ingest.app.orchestration.command;

import com.patra.common.enums.Priority;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.Endpoint;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Adapter 层传入的计划编排请求，经过缺省解析后进入应用层。
 * <p>封装调度上下文、窗口与优先级等信息。</p>
 *
 * @param provenanceCode 来源编码
 * @param endpoint 采集端点
 * @param operationCode 操作类型
 * @param step 切片步长（ISO-8601 持续时间）
 * @param triggerType 触发类型
 * @param scheduler 调度器类型
 * @param schedulerJobId 调度任务 ID
 * @param schedulerLogId 调度日志 ID
 * @param windowFrom 窗口开始
 * @param windowTo 窗口结束
 * @param priority 调度优先级
 * @param triggeredAt 触发时间
 * @param triggerParams 额外触发参数
 */
public record PlanIngestionRequest(
        ProvenanceCode provenanceCode,
        Endpoint endpoint,
        OperationCode operationCode,
        String step,
        TriggerType triggerType,
        Scheduler scheduler,
        String schedulerJobId,
        String schedulerLogId,
        Instant windowFrom,
        Instant windowTo,
        Priority priority,
        Instant triggeredAt,
        Map<String, Object> triggerParams
) {
    public PlanIngestionRequest {
        Objects.requireNonNull(provenanceCode, "provenanceCode must not be null");
        Objects.requireNonNull(operationCode, "operationCode must not be null");
        Objects.requireNonNull(triggerType, "triggerType must not be null");
        Objects.requireNonNull(scheduler, "scheduler must not be null");
        priority = priority == null ? Priority.NORMAL : priority;
    }
}
