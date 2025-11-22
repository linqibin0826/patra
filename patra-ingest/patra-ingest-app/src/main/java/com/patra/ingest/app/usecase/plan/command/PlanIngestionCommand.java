package com.patra.ingest.app.usecase.plan.command;

import com.patra.common.enums.Priority;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/// 计划编排命令（Adapter → Application）。
/// 
/// 由调度作业或外部调用方构建；经 Adapter 解析/默认值处理后，传递到应用层以：
/// 
/// #### Field semantics & constraints
/// 
/// - **provenanceCode / operationCode**: define the business pair; required.
///   - **step**: ISO-8601 Duration (e.g., `PT1H`, `P1D`); optional and
///       strategy-dependent.
///   - **windowFrom/windowTo**: half-open interval [from, to); both may be null for resolver to
///       infer.
///   - **priority**: scheduling priority; defaults to {@link
///       com.patra.common.enums.Priority#NORMAL}.
///   - **triggeredAt**: trigger time; defaults to now when null.
///   - **scheduler/schedulerJobId/schedulerLogId**: scheduler context for tracking; log id may
///       be null.
///   - **triggerParams**: optional user params; must be Jackson-serializable; empty map equals
///       null.
/// 
/// #### Invariants
/// 
/// - `provenanceCode != null`
///   - `operationCode != null`
///   - `triggerType != null`
///   - `scheduler != null`
///   - `priority != null` (coerced in constructor)
/// 
/// #### Validation
/// 
/// - Step syntax is validated by downstream strategy when applicable.
///   - Window ordering is validated by the window resolver/validators.
/// 
/// #### Thread-safety
/// 
/// Record is immutable (no exposed mutable collection references) and safe to share across
/// threads.
/// 
/// @param provenanceCode provenance code (required)
/// @param operationCode operation code (required)
/// @param step slice step (ISO-8601 duration; optional)
/// @param triggerType trigger type (required)
/// @param scheduler scheduler type (required)
/// @param schedulerJobId scheduler job id (nullable)
/// @param schedulerLogId scheduler log id (nullable)
/// @param windowFrom window start (nullable)
/// @param windowTo window end (nullable)
/// @param priority priority (defaults to NORMAL when null)
/// @param triggeredAt trigger time (nullable)
/// @param triggerParams extra trigger params (nullable)
public record PlanIngestionCommand(
    ProvenanceCode provenanceCode,
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
    Map<String, Object> triggerParams) {
  public PlanIngestionCommand {
    Objects.requireNonNull(provenanceCode, "provenanceCode must not be null");
    Objects.requireNonNull(operationCode, "endpointName must not be null");
    Objects.requireNonNull(triggerType, "triggerType must not be null");
    Objects.requireNonNull(scheduler, "scheduler must not be null");
    priority = priority == null ? Priority.NORMAL : priority;
  }
}
