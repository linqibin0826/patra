package com.patra.ingest.app.usecase.command;

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
 * Adapter 层传入的计划触发命令，经过缺省解析后进入应用层。
 */
public record PlanTriggerCommand(
        ProvenanceCode provenanceCode,
        Endpoint endpoint,
        OperationCode operationCode,
        String step, /*PT6H  分步的切片大小*/
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
    public PlanTriggerCommand {
        Objects.requireNonNull(provenanceCode, "provenanceCode不能为空");
        Objects.requireNonNull(operationCode, "operationType不能为空");
        Objects.requireNonNull(triggerType, "triggerType不能为空");
        Objects.requireNonNull(scheduler, "schedulerCode不能为空");
        priority = priority == null ? Priority.NORMAL : priority;
    }
}
