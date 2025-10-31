package com.patra.ingest.app.usecase.execution.cursor;

import com.patra.ingest.domain.model.entity.Cursor;
import com.patra.ingest.domain.model.entity.CursorEvent;
import com.patra.ingest.domain.model.enums.CursorDirection;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.vo.cursor.CursorLineage;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.model.vo.plan.WindowSpec;
import com.patra.ingest.domain.model.vo.shared.NamespaceKey;
import com.patra.ingest.domain.port.CursorEventRepository;
import com.patra.ingest.domain.port.CursorRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
  private final CursorEventRepository cursorEventRepository;

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

    // 2) Strategy-aware watermark and window boundary extraction
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

    // Extract window boundaries (only for TIME/DATE strategies)
    Instant windowFrom = null;
    Instant windowTo = null;
    if (windowSpec instanceof WindowSpec.Time timeSpec) {
      windowFrom = timeSpec.from();
      windowTo = timeSpec.to();
    }

    // 3) Determine cursor key and namespace
    String cursorKey = determineCursorKey(windowSpec);
    String namespaceScope = "GLOBAL";
    String namespaceKey = NamespaceKey.global().key();

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
      CursorLineage lineage =
          new CursorLineage(
              context.scheduleInstanceId(), // from TaskAggregate via ExecutionContext
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
      Instant prevWatermark = null;
      String prevValue = null;

      if (cursorOpt.isPresent()) {
        // 5.1 Cursor exists: update watermark and lineage
        cursor = cursorOpt.get();
        Instant oldWatermark = cursor.getCurrentWatermark();
        prevWatermark = oldWatermark;
        prevValue = oldWatermark != null ? oldWatermark.toString() : null;

        if (log.isDebugEnabled()) {
          log.debug(
              "cursor found provenanceCode={} endpointName={} currentWatermark={}",
              provenanceCode,
              operationCode,
              oldWatermark);
        }

        // Advance watermark with expression hash tracking (domain ensures monotonicity)
        Cursor.AdvancementResult result =
            cursor.advanceTo(newWatermark, lineage, context.exprHash());

        // Handle expression hash change: reset cursor to initial position
        if (result == Cursor.AdvancementResult.EXPRESSION_CHANGED) {
          log.info(
              "Expression changed for cursor [{}]: {} -> {}, resetting cursor to initial position",
              cursorKey,
              cursor.getExprHash(),
              context.exprHash());

          // Create a new cursor with the new expression hash
          cursor =
              Cursor.create(
                  provenanceCode,
                  operationCode,
                  cursorKey,
                  namespaceScope,
                  namespaceKey,
                  newWatermark,
                  lineage);

          // Advance the new cursor with the new expression hash
          cursor.advanceTo(newWatermark, lineage, context.exprHash());

          // Update tracking variables for event creation
          prevWatermark = null;
          prevValue = null;
        }

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
        // 5.2 Cursor missing: create with lineage (first advancement)
        cursor =
            Cursor.create(
                provenanceCode,
                operationCode,
                cursorKey,
                namespaceScope,
                namespaceKey,
                newWatermark,
                lineage);

        prevWatermark = null;
        prevValue = null;

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

      // 7) Generate idempotent key for event deduplication
      String idempotentKey =
          generateIdempotentKey(
              provenanceCode,
              operationCode,
              cursorKey,
              namespaceScope,
              namespaceKey != null ? namespaceKey : "",
              prevValue,
              newWatermark.toString(),
              runId,
              batchId);

      // 8) Determine advancement direction
      CursorDirection direction = determineDirection(operationCode);

      // 9) Create and save cursor advancement event
      CursorEvent event =
          CursorEvent.create(
              provenanceCode,
              operationCode,
              cursorKey,
              namespaceScope,
              namespaceKey,
              CursorType.TIME,
              prevValue,
              newWatermark.toString(),
              prevWatermark,
              newWatermark,
              direction,
              idempotentKey,
              lineage,
              context.exprHash(), // Use fresh expr_hash from context
              windowFrom, // Window start (null for non-TIME strategies)
              windowTo); // Window end (null for non-TIME strategies)

      cursorEventRepository.save(event);

      if (log.isDebugEnabled()) {
        log.debug(
            "cursor event recorded idempotentKey={} direction={} taskId={} runId={}",
            idempotentKey,
            direction,
            taskId,
            runId);
      }

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
   * Generates idempotent key for cursor event deduplication.
   *
   * <p>Format: SHA256(provenance|operation|cursorKey|nsScope|nsKey|prev|new|runId|batchId)
   *
   * <p>This ensures that the same advancement (same context and watermark transition) generates the
   * same idempotent key, preventing duplicate event records.
   *
   * @param provenanceCode provenance code
   * @param operationCode operation code
   * @param cursorKey cursor key
   * @param namespaceScopeCode namespace scope code
   * @param namespaceKey namespace key (empty string if null)
   * @param prevValue previous watermark value (NULL string if null)
   * @param newValue new watermark value
   * @param runId run identifier
   * @param batchId batch identifier
   * @return SHA256 hash as idempotent key (64-character hex string)
   */
  private String generateIdempotentKey(
      String provenanceCode,
      String operationCode,
      String cursorKey,
      String namespaceScopeCode,
      String namespaceKey,
      String prevValue,
      String newValue,
      Long runId,
      Long batchId) {

    String composite =
        String.format(
            "%s|%s|%s|%s|%s|%s|%s|%s|%s",
            provenanceCode,
            operationCode,
            cursorKey,
            namespaceScopeCode,
            namespaceKey != null ? namespaceKey : "",
            prevValue != null ? prevValue : "NULL",
            newValue,
            runId,
            batchId);

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(composite.getBytes(StandardCharsets.UTF_8));

      // Convert byte array to hex string
      StringBuilder hexString = new StringBuilder(2 * hashBytes.length);
      for (byte b : hashBytes) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      // Should never happen as SHA-256 is guaranteed to be available
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }

  /**
   * Determines cursor advancement direction based on operation code.
   *
   * <p>BACKFILL operations move cursor backward (historical data ingestion), while all other
   * operations move cursor forward (incremental harvest).
   *
   * @param operationCode operation code (HARVEST/BACKFILL/UPDATE/METRICS)
   * @return BACKFILL if operation is backfill, FORWARD otherwise
   */
  private CursorDirection determineDirection(String operationCode) {
    return "BACKFILL".equalsIgnoreCase(operationCode)
        ? CursorDirection.BACKFILL
        : CursorDirection.FORWARD;
  }
}
