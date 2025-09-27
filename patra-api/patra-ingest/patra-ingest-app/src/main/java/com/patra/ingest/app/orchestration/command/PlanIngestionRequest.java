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
 */
public record PlanIngestionRequest(
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
    public PlanIngestionRequest {
        Objects.requireNonNull(provenanceCode, "provenanceCode must not be null");
        Objects.requireNonNull(operationCode, "operationCode must not be null");
        Objects.requireNonNull(triggerType, "triggerType must not be null");
        Objects.requireNonNull(scheduler, "scheduler must not be null");
        priority = priority == null ? Priority.NORMAL : priority;
    }
}
