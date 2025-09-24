package com.patra.ingest.domain.model.command;

import com.patra.common.enums.Priority;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.EndpointCode;
import com.patra.ingest.domain.model.enums.OperationType;
import com.patra.ingest.domain.model.enums.SchedulerCode;
import com.patra.ingest.domain.model.enums.TriggerType;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 触发命令归一化后的领域对象。
 */
public record PlanTriggerNorm(
        Long scheduleInstanceId,
        ProvenanceCode provenanceCode,
        EndpointCode endpointCode,
        OperationType operationType,
        String step,
        TriggerType triggerType,
        SchedulerCode schedulerCode,
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
        Objects.requireNonNull(operationType, "operationType不能为空");
        Objects.requireNonNull(triggerType, "triggerType不能为空");
        Objects.requireNonNull(schedulerCode, "schedulerCode不能为空");
    }

    public boolean isHarvest() {
        return operationType == OperationType.HARVEST;
    }

    public boolean isBackfill() {
        return operationType == OperationType.BACKFILL;
    }

    public boolean isUpdate() {
        return operationType == OperationType.UPDATE;
    }
}
