package com.patra.ingest.app.usecase.execution.cursor;

import com.patra.ingest.domain.model.entity.Cursor;
import com.patra.ingest.domain.model.vo.CursorLineage;
import com.patra.ingest.domain.model.vo.ExecutionContext;
import com.patra.ingest.domain.model.vo.WindowSpec;
import com.patra.ingest.domain.port.CursorRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Cursor advancer implementation.
 *
 * <p>Responsibility: advance the cursor watermark based on batch results using optimistic locking
 * to avoid concurrent conflicts.
 *
 * <p>Design notes:
 *
 * <ul>
 *   <li>Query cursor by provenanceCode/operationCode/cursorKey/namespace.
 *   <li>Compute new watermark from WindowSpec strategy (TIME uses windowTo).
 *   <li>Update cursor via Cursor.advanceTo(); version checked on save.
 *   <li>Catch OptimisticLockingFailureException; return false to signal retry.
 *   <li>Create a new cursor on first advancement when none exists.
 * </ul>
 *
 * <p>Namespace strategy:
 *
 * <ul>
 *   <li>GLOBAL: global cursor shared across tasks
 *   <li>TASK: per-task cursor (isolated by taskId)
 *   <li>PLAN: per-plan cursor (isolated by planId)
 * </ul>
 *
 * <p>Logging:
 *
 * <ul>
 *   <li>INFO: advancement success (from/to).
 *   <li>WARN: optimistic conflict (retry).
 *   <li>DEBUG: cursor lookup, creation.
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CursorAdvancerImpl implements CursorAdvancer {

  private final CursorRepository cursorRepository;

  /** Advances the cursor watermark. */
  @Override
  public boolean advance(ExecutionContext context, Long taskId, Long runId, Long batchId) {
    // 1) Extract cursor parameters
    String provenanceCode = context.provenanceCode();
    String operationCode = context.operationCode();

    WindowSpec windowSpec = context.windowSpec();
    if (windowSpec == null) {
      log.debug("cursor advance skipped: no window spec taskId={} runId={}", taskId, runId);
      return true; // no window spec, skip advancement
    }

    // 2) Strategy-aware watermark extraction
    Instant newWatermark = extractWatermark(windowSpec, taskId, runId);
    if (newWatermark == null) {
      log.debug(
          "cursor advance skipped: non-TIME strategy or no watermark "
              + "strategy={} taskId={} runId={}",
          windowSpec.strategy(),
          taskId,
          runId);
      return true; // non-TIME strategies currently do not advance watermark
    }

    // 3) Determine cursor key and namespace
    String cursorKey = determineCursorKey(windowSpec);
    String namespaceScope = "GLOBAL";
    String namespaceKey = null;

    log.debug(
        "advancing cursor provenanceCode={} operationCode={} cursorKey={} newWatermark={} taskId={} runId={}",
        provenanceCode,
        operationCode,
        cursorKey,
        newWatermark,
        taskId,
        runId);

    try {
      // 4) Build lineage context from execution context and parameters
      Long scheduleInstanceId = parseSchedulerRunIdAsLong(context.schedulerRunId());
      CursorLineage lineage =
          new CursorLineage(
              scheduleInstanceId, // from ExecutionContext.schedulerRunId
              context.planId(),
              context.sliceId(),
              context.taskId(),
              context.runId(),
              batchId); // from method parameter (last succeeded batch)

      // 5) Lookup current cursor
      Optional<Cursor> cursorOpt =
          cursorRepository.find(
              provenanceCode, operationCode, cursorKey, namespaceScope, namespaceKey);

      Cursor cursor;
      if (cursorOpt.isPresent()) {
        // 5.1 Cursor exists: update watermark and lineage
        cursor = cursorOpt.get();
        Instant oldWatermark = cursor.getCurrentWatermark();

        if (log.isDebugEnabled()) {
          log.debug(
              "cursor found provenanceCode={} endpointName={} currentWatermark={}",
              provenanceCode,
              operationCode,
              oldWatermark);
        }

        // Advance watermark and lineage (domain ensures monotonicity)
        cursor.advanceTo(newWatermark, lineage);

        log.info(
            "cursor advanced provenanceCode={} endpointName={} from={} to={} taskId={} runId={} planId={} sliceId={}",
            provenanceCode,
            operationCode,
            oldWatermark,
            newWatermark,
            taskId,
            runId,
            context.planId(),
            context.sliceId());
      } else {
        // 5.2 Cursor missing: create with lineage
        cursor =
            Cursor.create(
                provenanceCode,
                operationCode,
                cursorKey,
                namespaceScope,
                namespaceKey,
                newWatermark,
                lineage);

        log.info(
            "cursor created provenanceCode={} endpointName={} watermark={} taskId={} runId={} planId={} sliceId={}",
            provenanceCode,
            operationCode,
            newWatermark,
            taskId,
            runId,
            context.planId(),
            context.sliceId());
      }

      // 6) Save cursor (optimistic lock check)
      cursorRepository.save(cursor);
      return true;

    } catch (OptimisticLockingFailureException e) {
      // Optimistic conflict (version mismatch)
      log.warn(
          "cursor advance conflict provenanceCode={} endpointName={} taskId={} runId={}",
          provenanceCode,
          operationCode,
          taskId,
          runId);
      return false; // signal retry

    } catch (Exception e) {
      log.error(
          "cursor advance failed provenanceCode={} endpointName={} taskId={} runId={}",
          provenanceCode,
          operationCode,
          taskId,
          runId,
          e);
      throw new IllegalStateException("Cursor advancement failed", e);
    }
  }

  /**
   * Extracts watermark from WindowSpec (strategy-aware).
   *
   * <p>Currently only the TIME strategy supports timestamp-based watermark advancement.
   *
   * @param windowSpec window specification
   * @param taskId task id (for logs)
   * @param runId run id (for logs)
   * @return watermark timestamp, or null when not supported by the strategy
   */
  private Instant extractWatermark(WindowSpec windowSpec, Long taskId, Long runId) {
    return switch (windowSpec.strategy()) {
      case TIME, DATE -> {
        // Both TIME and DATE strategies use time-based windows
        WindowSpec.Time timeSpec = (WindowSpec.Time) windowSpec;
        yield timeSpec.to(); // Use window end as watermark
      }
      case ID_RANGE, CURSOR_LANDMARK, VOLUME_BUDGET, SINGLE -> {
        // These strategies currently do not use time-based watermark
        // Future: ID_RANGE may use numeric-ID-based watermark
        yield null;
      }
      case HYBRID -> {
        // Future: extract time component from HYBRID spec
        log.warn(
            "HYBRID strategy watermark extraction not yet implemented " + "taskId={} runId={}",
            taskId,
            runId);
        yield null;
      }
    };
  }

  /**
   * Determines the cursor key based on the window strategy.
   *
   * @param windowSpec window specification
   * @return cursor key identifier
   */
  private String determineCursorKey(WindowSpec windowSpec) {
    return switch (windowSpec.strategy()) {
      case TIME, DATE -> "TIME"; // Both TIME and DATE use time-based cursor key
      case ID_RANGE -> "ID";
      case CURSOR_LANDMARK -> "CURSOR";
      case VOLUME_BUDGET, SINGLE, HYBRID -> "GLOBAL";
    };
  }

  /**
   * Parses schedulerRunId to Long for lineage tracking.
   *
   * <p>schedulerRunId may be null or non-numeric; return null if unparseable.
   *
   * @param schedulerRunId scheduler run id string
   * @return parsed Long value, or null if unparseable
   */
  private Long parseSchedulerRunIdAsLong(String schedulerRunId) {
    if (schedulerRunId == null || schedulerRunId.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(schedulerRunId);
    } catch (NumberFormatException e) {
      log.debug("schedulerRunId is not a valid Long: {}", schedulerRunId);
      return null;
    }
  }
}
