package com.patra.ingest.domain.model.command;

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
 * 触发命令归一化后的领域对象。
 *
 * @param scheduleInstanceId 调度实例 ID
 * @param provenanceCode 来源编码
 * @param endpoint 来源端点
 * @param operationCode 操作类型
 * @param step 切片步长
 * @param triggerType 触发类型
 * @param scheduler 调度器类型
 * @param schedulerJobId 调度任务 ID
 * @param schedulerLogId 调度日志 ID
 * @param requestedWindowFrom 指定窗口开始
 * @param requestedWindowTo 指定窗口结束
 * @param priority 优先级
 * @param triggerParams 其他触发参数
 */
public record PlanTriggerNorm(
        Long scheduleInstanceId,
        ProvenanceCode provenanceCode,
        Endpoint endpoint,
        OperationCode operationCode,
        String step,
        TriggerType triggerType,
        Scheduler scheduler,
        String schedulerJobId,
        String schedulerLogId,
        Instant requestedWindowFrom,
        Instant requestedWindowTo,
        Priority priority,
        Map<String, Object> triggerParams
) {
    public PlanTriggerNorm {
        Objects.requireNonNull(scheduleInstanceId, "scheduleInstanceId不能为空");
        Objects.requireNonNull(provenanceCode, "provenanceCode不能为空");
        Objects.requireNonNull(operationCode, "operationCode不能为空");
        Objects.requireNonNull(triggerType, "triggerType不能为空");
        Objects.requireNonNull(scheduler, "schedulerCode不能为空");
    }

    public boolean isHarvest() {
        return operationCode == OperationCode.HARVEST;
    }

    public boolean isBackfill() {
        return operationCode == OperationCode.BACKFILL;
    }

    public boolean isUpdate() {
        return operationCode == OperationCode.UPDATE;
    }
}
