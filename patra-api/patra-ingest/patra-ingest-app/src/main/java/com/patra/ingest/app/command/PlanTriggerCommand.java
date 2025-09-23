package com.patra.ingest.app.command;

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
 * Adapter 层传入的计划触发命令，经过缺省解析后进入应用层。
 */
public record PlanTriggerCommand(
        ProvenanceCode provenanceCode,
        EndpointCode endpointCode,
        OperationType operationType,
        TriggerType triggerType,
        SchedulerCode schedulerCode,
        String schedulerJobId,
        String schedulerLogId,
        Instant windowFrom,
        Instant windowTo,
        Priority priority,
        Map<String, Object> triggerParams
) {
    public PlanTriggerCommand {
        Objects.requireNonNull(provenanceCode, "provenanceCode不能为空");
        Objects.requireNonNull(operationType, "operationType不能为空");
        Objects.requireNonNull(triggerType, "triggerType不能为空");
        Objects.requireNonNull(schedulerCode, "schedulerCode不能为空");
        priority = priority == null ? Priority.NORMAL : priority;
    }
}
