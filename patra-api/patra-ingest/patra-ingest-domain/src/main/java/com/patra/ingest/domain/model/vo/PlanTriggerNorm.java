package com.patra.ingest.domain.model.vo;

import com.patra.common.enums.Priority;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Domain record representing a normalized trigger command.
 *
 * @param scheduleInstanceId scheduler instance identifier
 * @param provenanceCode provenance code
 * @param operationCode operation type
 * @param step slice planning step
 * @param triggerType trigger type
 * @param scheduler scheduler type
 * @param schedulerJobId scheduler job identifier
 * @param schedulerLogId scheduler log identifier
 * @param requestedWindowFrom requested window start
 * @param requestedWindowTo requested window end
 * @param priority priority level
 * @param triggerParams additional trigger parameters
 */
public record PlanTriggerNorm(
    Long scheduleInstanceId,
    ProvenanceCode provenanceCode,
    OperationCode operationCode,
    String step,
    TriggerType triggerType,
    Scheduler scheduler,
    String schedulerJobId,
    String schedulerLogId,
    Instant requestedWindowFrom,
    Instant requestedWindowTo,
    Priority priority,
    Map<String, Object> triggerParams) {
  public PlanTriggerNorm {
    Objects.requireNonNull(scheduleInstanceId, "scheduleInstanceId must not be null");
    Objects.requireNonNull(provenanceCode, "provenanceCode must not be null");
    Objects.requireNonNull(operationCode, "operationCode must not be null");
    Objects.requireNonNull(triggerType, "triggerType must not be null");
    Objects.requireNonNull(scheduler, "scheduler must not be null");
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
