package com.patra.ingest.app.usecase.plan.validator;

import cn.hutool.core.util.StrUtil;
import com.patra.ingest.domain.exception.PlanValidationException;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.PlanTriggerNorm;
import com.patra.ingest.domain.model.vo.PlannerWindow;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link PlannerValidator}. Performs baseline checks including window
 * sanity, queue backpressure, and source capability validation.
 *
 * <p>Threshold policy:
 *
 * <ul>
 *   <li>{@code DEFAULT_QUEUE_THRESHOLD = 50}: apply backpressure when the backlog exceeds this
 *       value.
 *   <li>{@code MAX_REASONABLE_WINDOW = 30 days}: prevent oversized windows that inflate task
 *       volume.
 *   <li>{@code MIN_REASONABLE_WINDOW = 1 minute}: avoid extremely small slices that waste
 *       resources.
 * </ul>
 *
 * UPDATE operations may omit a window; HARVEST operations must provide either an explicit window or
 * incremental capability configuration.
 */
@Slf4j
@Component
public class PlannerValidatorImpl implements PlannerValidator {

  private static final long DEFAULT_QUEUE_THRESHOLD = 50L;
  private static final Duration MAX_REASONABLE_WINDOW = Duration.ofDays(30);
  private static final Duration MIN_REASONABLE_WINDOW = Duration.ofMinutes(1);

  @Override
  public void validateBeforeAssemble(
      PlanTriggerNorm triggerNorm,
      ProvenanceConfigSnapshot snapshot,
      PlannerWindow window,
      long currentQueuedTasks) {
    log.debug(
        "[INGEST][APP] Validating plan assembly, provenance={}, operation={}, window={}, queuedTasks={}",
        triggerNorm.provenanceCode(),
        triggerNorm.operationCode(),
        window,
        currentQueuedTasks);

    // Step 1: validate window sanity.
    validateWindow(triggerNorm, window);

    // Step 2: enforce queue backpressure.
    validateQueueBackpressure(currentQueuedTasks);

    // Step 3: ensure provenance capabilities align with the trigger.
    validateSourceCapabilities(triggerNorm, snapshot, window);

    log.debug("[INGEST][APP] Plan assembly validation passed");
  }

  /** Validate the ingestion window: presence, ordering, and duration boundaries. */
  private void validateWindow(PlanTriggerNorm triggerNorm, PlannerWindow window) {
    // UPDATE operations may proceed without a window.
    if (triggerNorm.isUpdate()) {
      log.debug("[INGEST][APP] Update operation detected, allowing null window");
      return;
    }

    if (window == null) {
      throw new PlanValidationException(
          "Plan window must not be null", PlanValidationException.Reason.WINDOW_MISSING);
    }

    // Non-UPDATE operations require fully populated bounds.
    if (window.from() == null || window.to() == null) {
      throw new PlanValidationException(
          String.format("Time window is required for %s operation", triggerNorm.operationCode()),
          PlanValidationException.Reason.WINDOW_MISSING);
    }

    // Enforce chronological ordering.
    if (!window.from().isBefore(window.to())) {
      throw new PlanValidationException(
          String.format("Invalid window: from=%s must be before to=%s", window.from(), window.to()),
          PlanValidationException.Reason.WINDOW_INVALID);
    }

    // Ensure the window duration is within reasonable bounds.
    Duration windowDuration = Duration.between(window.from(), window.to());
    if (windowDuration.compareTo(MAX_REASONABLE_WINDOW) > 0) {
      throw new PlanValidationException(
          String.format(
              "Window too large: %d days exceeds maximum %d days",
              windowDuration.toDays(), MAX_REASONABLE_WINDOW.toDays()),
          PlanValidationException.Reason.WINDOW_TOO_LARGE);
    }

    if (windowDuration.compareTo(MIN_REASONABLE_WINDOW) < 0) {
      throw new PlanValidationException(
          String.format(
              "Window too small: %d seconds below minimum %d seconds",
              windowDuration.toSeconds(), MIN_REASONABLE_WINDOW.toSeconds()),
          PlanValidationException.Reason.WINDOW_TOO_SMALL);
    }

    log.debug("[INGEST][APP] Window validation passed, duration={}min", windowDuration.toMinutes());
  }

  /** Enforce queue backpressure: halt planning when queued tasks exceed the threshold. */
  private void validateQueueBackpressure(long currentQueuedTasks) {
    if (currentQueuedTasks > DEFAULT_QUEUE_THRESHOLD) {
      throw new PlanValidationException(
          String.format(
              "Too many queued tasks (%d > %d), applying backpressure on plan trigger",
              currentQueuedTasks, DEFAULT_QUEUE_THRESHOLD),
          PlanValidationException.Reason.QUEUE_BACKPRESSURE);
    }
    log.debug("[INGEST][APP] Queue backpressure check passed, queuedTasks={}", currentQueuedTasks);
  }

  /** Validate source capabilities along with offset/window completeness. */
  private void validateSourceCapabilities(
      PlanTriggerNorm triggerNorm, ProvenanceConfigSnapshot snapshot, PlannerWindow window) {

    if (snapshot == null) {
      log.warn("[INGEST][APP] Provenance config snapshot is missing, skip capability validation");
      return;
    }

    // Validate incremental capability for HARVEST operations.
    if (triggerNorm.isHarvest()) {
      validateIncrementalCapability(triggerNorm, snapshot, window);
    }

    // Validate window-related configuration if a window is present.
    if (!triggerNorm.isUpdate() && window != null && window.from() != null && window.to() != null) {
      validateWindowConfigCompleteness(snapshot);
    }
  }

  /**
   * Validate incremental collection capabilities. If the source advertises incremental mode
   * (non-FULL), it must expose offset configuration; otherwise an explicit window is required.
   */
  private void validateIncrementalCapability(
      PlanTriggerNorm triggerNorm, ProvenanceConfigSnapshot snapshot, PlannerWindow window) {

    ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset = snapshot.windowOffset();

    // If no incremental capability is configured, require an explicit window.
    if (windowOffset == null || StrUtil.equalsIgnoreCase(windowOffset.windowModeCode(), "FULL")) {
      if (triggerNorm.requestedWindowFrom() == null && window != null && window.from() != null) {
        throw new PlanValidationException(
            String.format(
                "Source %s does not support automatic incremental harvest; explicit window required",
                triggerNorm.provenanceCode()),
            PlanValidationException.Reason.CAPABILITY_MISMATCH);
      }
    }

    // Verify offset-field configuration for date/composite offsets.
    if (windowOffset != null
        && (StrUtil.equalsIgnoreCase(windowOffset.offsetTypeCode(), "DATE")
            || StrUtil.equalsIgnoreCase(windowOffset.offsetTypeCode(), "COMPOSITE"))) {

      if (StrUtil.isBlank(windowOffset.offsetFieldName())
          && StrUtil.isBlank(windowOffset.defaultDateFieldName())) {
        throw new PlanValidationException(
            String.format(
                "Source %s configured for %s offset but missing date field configuration",
                triggerNorm.provenanceCode(), windowOffset.offsetTypeCode()),
            PlanValidationException.Reason.CAPABILITY_MISMATCH);
      }
    }

    log.debug(
        "[INGEST][APP] Incremental capability validation passed, source={}",
        triggerNorm.provenanceCode());
  }

  /** Issue warnings when optional window configuration (size/span) is invalid. */
  private void validateWindowConfigCompleteness(ProvenanceConfigSnapshot snapshot) {
    ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset = snapshot.windowOffset();

    if (windowOffset != null && !StrUtil.equalsIgnoreCase(windowOffset.windowModeCode(), "FULL")) {
      // Warn when window size is missing or invalid.
      if (windowOffset.windowSizeValue() == null || windowOffset.windowSizeValue() <= 0) {
        log.warn("[INGEST][APP] window size not configured or invalid, fallback to defaults");
      }

      // Warn when the maximum window span is invalid.
      if (windowOffset.maxWindowSpanSeconds() != null && windowOffset.maxWindowSpanSeconds() <= 0) {
        log.warn(
            "[INGEST][APP] invalid max window span configuration: {}",
            windowOffset.maxWindowSpanSeconds());
      }
    }
  }
}
