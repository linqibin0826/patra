package com.patra.ingest.adapter.inbound.scheduler.job;

import cn.hutool.core.text.CharSequenceUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.Priority;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.adapter.inbound.scheduler.param.ProvenanceScheduleJobParam;
import com.patra.ingest.app.usecase.plan.PlanIngestionUseCase;
import com.patra.ingest.app.usecase.plan.command.PlanIngestionCommand;
import com.patra.ingest.app.usecase.plan.dto.PlanIngestionResult;
import com.patra.ingest.domain.exception.IngestScheduleParameterException;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.xxl.job.core.context.XxlJobHelper;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Scheduling job base class that provides a unified template for "provenance + operation" jobs
 * (param parsing → orchestration → result/error reporting).
 *
 * <p>Common logic is centralized here. Subclasses only need to define {@link #getProvenanceCode()}
 * and {@link #getOperationCode()}.
 *
 * <p>Defaults and constraints: - If XXL-Job param is blank, fall back to default window and step
 * (currently step=PT6H; can be externalized later). - Parse windowFrom/windowTo as ISO-8601 Instant
 * strings; missing/illegal values are treated as null. - Illegal values of priority are ignored to
 * avoid job failure. - Results are reported via logs and XxlJobHelper; on failure, the original
 * exception chain is thrown to allow retry policies to decide.
 */
@Slf4j
public abstract class AbstractProvenanceScheduleJob {

  private static final String DEFAULT_STEP =
      "P1D"; // Default to 1 day for DATE-based slicing (e.g., PubMed)
  private static final String DEFAULT_SCHEDULER_LOG_ID = "0";

  /** Plan orchestration application service (application layer entry). */
  @Autowired private PlanIngestionUseCase planIngestionUseCase;

  /** JSON object mapper. */
  @Autowired private ObjectMapper objectMapper;

  /**
   * Returns the provenance code for this job (fixed per subclass).
   *
   * @return provenance code
   */
  protected abstract ProvenanceCode getProvenanceCode();

  /**
   * Returns the operation code for this job (fixed per subclass).
   *
   * @return operation code
   */
  protected abstract OperationCode getOperationCode();

  /**
   * Parses XXL-Job JSON param into the application request object.
   *
   * <p>Supported fields: windowFrom, windowTo, priority, step, schedulerLogId, triggeredAt and any
   * additional fields (passed through as triggerParams).
   *
   * <p>Failure policy: if structure is invalid or JSON parsing fails, throws {@link
   * IngestScheduleParameterException} which is caught by the job entrypoint and marked as failed.
   *
   * @param paramStr raw XXL-Job param (JSON string; may be blank)
   * @return PlanIngestionCommand request
   * @throws IngestScheduleParameterException when parameters are invalid
   */
  protected PlanIngestionCommand parseJobParam(String paramStr) {
    if (CharSequenceUtil.isBlank(paramStr)) {
      return buildPlanIngestionCommand(ProvenanceScheduleJobParam.empty(), Map.of());
    }
    try {
      Map<String, Object> rawParams = objectMapper.readValue(paramStr, new TypeReference<>() {});
      ProvenanceScheduleJobParam jobParam =
          rawParams == null
              ? ProvenanceScheduleJobParam.empty()
              : objectMapper.convertValue(rawParams, ProvenanceScheduleJobParam.class);
      if (jobParam == null) {
        jobParam = ProvenanceScheduleJobParam.empty();
      }
      Map<String, Object> triggerParams =
          (rawParams == null || rawParams.isEmpty())
              ? Map.of()
              : Collections.unmodifiableMap(new LinkedHashMap<>(rawParams));
      return buildPlanIngestionCommand(jobParam, triggerParams);
    } catch (Exception e) {
      throw new IngestScheduleParameterException(
          "Failed to parse JSON param: " + e.getMessage(), e);
    }
  }

  private PlanIngestionCommand buildPlanIngestionCommand(
      ProvenanceScheduleJobParam param, Map<String, Object> triggerParams) {
    ProvenanceScheduleJobParam nonNullParam =
        param == null ? ProvenanceScheduleJobParam.empty() : param;
    Map<String, Object> nonNullTriggerParams = triggerParams == null ? Map.of() : triggerParams;
    return new PlanIngestionCommand(
        getProvenanceCode(),
        getOperationCode(),
        resolveStep(nonNullParam.step()),
        TriggerType.SCHEDULE,
        Scheduler.XXL,
        String.valueOf(XxlJobHelper.getJobId()),
        resolveSchedulerLogId(nonNullParam.schedulerLogId()),
        parseInstant(nonNullParam.windowFrom(), "windowFrom"),
        parseInstant(nonNullParam.windowTo(), "windowTo"),
        resolvePriority(nonNullParam.priority()),
        resolveTriggeredAt(nonNullParam.triggeredAt()),
        nonNullTriggerParams);
  }

  private String resolveStep(String step) {
    return CharSequenceUtil.isBlank(step) ? DEFAULT_STEP : CharSequenceUtil.trim(step);
  }

  private String resolveSchedulerLogId(String schedulerLogId) {
    return CharSequenceUtil.isBlank(schedulerLogId)
        ? DEFAULT_SCHEDULER_LOG_ID
        : CharSequenceUtil.trim(schedulerLogId);
  }

  private Priority resolvePriority(String priority) {
    if (CharSequenceUtil.isBlank(priority)) {
      return null;
    }
    String normalized = CharSequenceUtil.trim(priority).toUpperCase();
    try {
      return Priority.valueOf(normalized);
    } catch (IllegalArgumentException ex) {
      log.warn("[INGEST][ADAPTER] Ignoring illegal priority value: {}", priority);
      return null;
    }
  }

  private Instant parseInstant(String value, String fieldName) {
    if (CharSequenceUtil.isBlank(value)) {
      return null;
    }
    try {
      return Instant.parse(CharSequenceUtil.trim(value));
    } catch (Exception ex) {
      throw new IngestScheduleParameterException(
          String.format("Illegal time format for field %s: %s", fieldName, value), ex);
    }
  }

  private Instant resolveTriggeredAt(String triggeredAt) {
    Instant parsed = parseInstant(triggeredAt, "triggeredAt");
    return parsed == null ? Instant.now() : parsed;
  }

  /**
   * Executes the main scheduling flow: logging (start/end/error) + param parsing + orchestration
   * call + result reporting.
   *
   * <p>Execution time is recorded for SLA monitoring; on failure, XxlJobHelper marks the job failed
   * and the exception is rethrown.
   *
   * @param paramStr XXL-Job JSON param string (may be blank)
   */
  protected void executeScheduleJob(String paramStr) {
    // Record start time for SLA metrics
    long startTime = System.currentTimeMillis();

    try {
      log.info(
          "[INGEST][ADAPTER] Starting scheduled job, provenance={}, operation={}, rawParam={}",
          getProvenanceCode().getCode(),
          getOperationCode(),
          paramStr);

      PlanIngestionCommand command = parseJobParam(paramStr);
      PlanIngestionResult result = planIngestionUseCase.ingestPlan(command);

      long duration = System.currentTimeMillis() - startTime;
      log.info(
          "[INGEST][ADAPTER] Scheduled job completed, provenance={}, operation={}, durationMs={}, planId={}, taskCount={}",
          getProvenanceCode().getCode(),
          getOperationCode(),
          duration,
          result.planId(),
          result.taskCount());

      XxlJobHelper.handleSuccess(
          String.format(
              "Job succeeded in %dms, planId=%s, taskCount=%d",
              duration, result.planId(), result.taskCount()));

    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;
      log.error(
          "[INGEST][ADAPTER] Scheduled job failed, provenance={}, operation={}, durationMs={}, error={}",
          getProvenanceCode().getCode(),
          getOperationCode(),
          duration,
          e.getMessage(),
          e);

      XxlJobHelper.handleFail(String.format("Job failed: %s", e.getMessage()));
      throw e;
    }
  }
}
