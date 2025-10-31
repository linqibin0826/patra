package com.patra.ingest.app.usecase.plan.window;

import static com.patra.ingest.app.usecase.plan.window.PlanningWindowSupport.alignFloor;
import static com.patra.ingest.app.usecase.plan.window.PlanningWindowSupport.computeLaggedNow;
import static com.patra.ingest.app.usecase.plan.window.PlanningWindowSupport.isCalendarMode;
import static com.patra.ingest.app.usecase.plan.window.PlanningWindowSupport.maxInstant;
import static com.patra.ingest.app.usecase.plan.window.PlanningWindowSupport.minInstant;
import static com.patra.ingest.app.usecase.plan.window.PlanningWindowSupport.resolveDuration;
import static com.patra.ingest.app.usecase.plan.window.PlanningWindowSupport.resolveWindowSize;
import static com.patra.ingest.app.usecase.plan.window.PlanningWindowSupport.resolveZone;

import cn.hutool.core.util.StrUtil;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.plan.PlanTriggerNorm;
import com.patra.ingest.domain.model.vo.plan.PlannerWindow;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Default planning-window resolver implementation (HARVEST / BACKFILL / UPDATE).
 *
 * <p>Focuses on the "plan-level" window (not the slicing stage). It does not handle slice overlaps
 * or multi-cursor advanced strategies.
 *
 * <h4>Common rules</h4>
 *
 * <ul>
 *   <li>nowSafe = min(currentTime - watermarkLag, currentTime) (no subtraction when config is
 *       absent)
 *   <li>Windows are half-open intervals [from, to). After calendar alignment, if from == to, treat
 *       as an empty window.
 *   <li>If the mode cannot be determined or is not supported, return full() so upstream can decide
 *       to do a full scan.
 *   <li>Minimum non-empty length: if the span is less than 1s, expand to 1s to avoid zero-length
 *       tasks downstream.
 * </ul>
 *
 * <h4>HARVEST</h4>
 *
 * <pre>
 * toCandidate   = min(user.to?, nowSafe)
 * fromCandidate = harvestWM? max(user.from?, harvestWM - lookback) : (user.from? | toCandidate - windowSize)
 * CALENDAR align -> empty-window check
 * </pre>
 *
 * <h4>BACKFILL</h4>
 *
 * <pre>
 * upperAnchor   = min(user.to?, forwardWM?, nowSafe)  // forwardWM is reserved, not currently injected
 * fromCandidate = backfillWM? max(backfillWM, user.from?) : (user.from? | upperAnchor - windowSize)
 * Boundary correction: fromCandidate must not be > upperAnchor
 * CALENDAR align -> empty-window check
 * </pre>
 *
 * <h4>UPDATE</h4>
 *
 * <pre>
 * timeDriven = (offsetType=DATE and a user window exists) OR (any user window provided)
 * if timeDriven:
 *   toCandidate   = min(user.to?, nowSafe) (defaults to nowSafe)
 *   fromCandidate = if updateWM and user.from? -> max(updateWM, user.from?)
 *                |  only updateWM           -> updateWM (then compare with user.from?)
 *                |  only user.from          -> user.from
 *                |  otherwise               -> nowSafe - windowSize
 * else (ID-driven):
 *   if user window exists: toCandidate = min(user.to?, nowSafe) (defaults to nowSafe)
 *                          fromCandidate = user.from? | (toCandidate - windowSize)
 *   else:                  [nowSafe - windowSize, nowSafe]
 * CALENDAR align -> empty-window check
 * </pre>
 *
 * <h4>Design trade-offs</h4>
 *
 * <ul>
 *   <li>forwardWM is not exposed for now; can be added via interface extension or embedded in
 *       triggerNorm when needed.
 *   <li>maxWindowSpanSeconds is not hard-limited here; slicing stage (TimeSlicePlanner) should
 *       control granularity.
 *   <li>When encountering an empty window after alignment, return the "minimal effective window"
 *       instead of null to simplify upstream checks; the original empty state can still be detected
 *       via from==to (plus the +1s expansion).
 * </ul>
 *
 * <h4>Complexity</h4>
 *
 * <p>All branches are O(1) and perform no external IO.
 *
 * <h4>Thread-safety</h4>
 *
 * <p>Stateless; safe to reuse as a singleton.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class PlanningWindowResolverImpl implements PlanningWindowResolver {

  /** Default total window span (24 hours). */
  private static final Duration DEFAULT_WINDOW_SIZE = Duration.ofHours(24);

  /** Default safety lag used to cap "current time" when computing nowSafe. */
  private static final Duration DEFAULT_SAFETY_LAG = Duration.ZERO;

  /** Minimum effective window length (> 0 seconds) to avoid empty windows. */
  private static final Duration MIN_EFFECTIVE_WINDOW = Duration.ofSeconds(1);

  /** {@inheritDoc} */
  @Override
  public PlannerWindow resolveWindow(
      PlanTriggerNorm triggerNorm,
      ProvenanceConfigSnapshot snapshot,
      Instant cursorWatermark,
      Instant currentTime) {
    ProvenanceConfigSnapshot.WindowOffsetConfig cfg =
        snapshot == null ? null : snapshot.windowOffset();
    String timezone =
        snapshot != null && snapshot.provenance() != null
            ? snapshot.provenance().timezoneDefault()
            : null;

    Instant userFrom = triggerNorm.requestedWindowFrom();
    Instant userTo = triggerNorm.requestedWindowTo();

    // Trim the current time using the configured safety lag to avoid ingesting not-yet-stable data
    Instant nowSafe = computeLaggedNow(currentTime, cfg, DEFAULT_SAFETY_LAG);

    if (triggerNorm.isHarvest()) {
      return resolveHarvest(cfg, cursorWatermark, userFrom, userTo, nowSafe, timezone);
    } else if (triggerNorm.isBackfill()) {
      return resolveBackfill(cfg, cursorWatermark, null, userFrom, userTo, nowSafe, timezone);
    } else if (triggerNorm.isUpdate()) {
      return resolveUpdate(cfg, cursorWatermark, userFrom, userTo, nowSafe, timezone);
    }
    return PlannerWindow.full();
  }

  /* ===================== HARVEST ===================== */

  /**
   * Resolve window for HARVEST mode.
   *
   * <p>Uses the current HARVEST watermark (harvestWM) and a configurable lookback to avoid misses.
   * When no watermark exists, treat as the first run and derive from the user-provided lower bound
   * or by rolling back from the upper bound using the default window span.
   */
  private PlannerWindow resolveHarvest(
      ProvenanceConfigSnapshot.WindowOffsetConfig cfg,
      Instant harvestWM,
      Instant userFrom,
      Instant userTo,
      Instant nowSafe,
      String timezone) {
    Duration windowSize = resolveWindowSize(cfg, DEFAULT_WINDOW_SIZE);
    Duration lookback =
        resolveDuration(
            cfg == null ? null : cfg.lookbackValue(),
            cfg == null ? null : cfg.lookbackUnitCode(),
            Duration.ZERO);

    log.debug(
        "Resolving HARVEST window: harvestWM={}, userFrom={}, userTo={}, nowSafe={}, windowSize={}, lookback={}",
        harvestWM,
        userFrom,
        userTo,
        nowSafe,
        windowSize,
        lookback);

    Instant toCandidate = minInstant(userTo, nowSafe);
    if (toCandidate == null) {
      toCandidate = nowSafe; // No user upper bound -> use nowSafe
    }

    Instant fromCandidate;
    if (harvestWM != null) {
      Instant lowerByCursor = harvestWM.minus(lookback);
      fromCandidate = maxInstant(lowerByCursor, userFrom);
      log.debug(
          "Using harvestWM: lowerByCursor={}, fromCandidate={}", lowerByCursor, fromCandidate);
    } else if (userFrom != null) {
      fromCandidate = userFrom;
      log.debug("No harvestWM, using userFrom: {}", fromCandidate);
    } else {
      fromCandidate = toCandidate.minus(windowSize); // Default: roll back one window
      log.debug("No harvestWM or userFrom, rolling back from toCandidate: {}", fromCandidate);
    }

    if (isCalendarMode(cfg) && cfg != null) {
      ZoneId zone = resolveZone(timezone);
      Instant beforeAlign = fromCandidate;
      fromCandidate = alignFloor(fromCandidate, cfg.calendarAlignTo(), zone);
      toCandidate = alignFloor(toCandidate, cfg.calendarAlignTo(), zone);
      log.debug(
          "Calendar alignment applied: from {} -> {}, to {} -> {}",
          beforeAlign,
          fromCandidate,
          toCandidate,
          toCandidate);
    }

    if (!toCandidate.isAfter(fromCandidate)) {
      log.debug("HARVEST window empty after alignment: {} >= {}", fromCandidate, toCandidate);
      return nullWindowIfEmpty(fromCandidate, toCandidate);
    }

    log.debug("Resolved HARVEST window: [{}, {})", fromCandidate, toCandidate);
    return safeWindow(fromCandidate, toCandidate);
  }

  /* ===================== BACKFILL ===================== */

  /**
   * Resolve window for BACKFILL mode.
   *
   * <p>The BACKFILL watermark (backfillWM) controls the minimal lower bound. The upper bound is
   * limited by user-provided 'to' and nowSafe. forwardWM is reserved for future use.
   */
  private PlannerWindow resolveBackfill(
      ProvenanceConfigSnapshot.WindowOffsetConfig cfg,
      Instant backfillWM,
      Instant forwardWM,
      Instant userFrom,
      Instant userTo,
      Instant nowSafe,
      String timezone) {
    Duration windowSize = resolveWindowSize(cfg, DEFAULT_WINDOW_SIZE);

    log.debug(
        "Resolving BACKFILL window: backfillWM={}, forwardWM={}, userFrom={}, userTo={}, nowSafe={}, windowSize={}",
        backfillWM,
        forwardWM,
        userFrom,
        userTo,
        nowSafe,
        windowSize);

    // forwardWM as an upperAnchor candidate (e.g., HARVEST watermark). This resolver gets a single
    // cursorWatermark according to the selected mode. To avoid expanding the interface for now,
    // assume the provided watermark corresponds to BACKFILL. If forwardWM is ever needed, it can be
    // passed via an extended contract or via enriched trigger parameters.
    Instant upperAnchor = minInstant(userTo, forwardWM, nowSafe);
    if (upperAnchor == null) {
      upperAnchor = nowSafe;
    }

    Instant fromCandidate;
    if (backfillWM != null) {
      fromCandidate = maxInstant(backfillWM, userFrom);
      log.debug("Using backfillWM: fromCandidate={}", fromCandidate);
    } else if (userFrom != null) {
      fromCandidate = userFrom;
      log.debug("No backfillWM, using userFrom: {}", fromCandidate);
    } else {
      fromCandidate = upperAnchor.minus(windowSize);
      log.debug("No backfillWM or userFrom, rolling back from upperAnchor: {}", fromCandidate);
    }
    if (fromCandidate.isAfter(upperAnchor)) {
      log.debug("fromCandidate {} exceeds upperAnchor {}, capping", fromCandidate, upperAnchor);
      fromCandidate = upperAnchor; // prevent crossing the upper bound
    }

    if (isCalendarMode(cfg) && cfg != null) {
      ZoneId zone = resolveZone(timezone);
      fromCandidate = alignFloor(fromCandidate, cfg.calendarAlignTo(), zone);
      upperAnchor = alignFloor(upperAnchor, cfg.calendarAlignTo(), zone);
      log.debug("Calendar alignment applied: from={}, to={}", fromCandidate, upperAnchor);
    }
    if (!upperAnchor.isAfter(fromCandidate)) {
      log.debug("BACKFILL window empty: {} >= {}", fromCandidate, upperAnchor);
      return nullWindowIfEmpty(fromCandidate, upperAnchor);
    }

    log.debug("Resolved BACKFILL window: [{}, {})", fromCandidate, upperAnchor);
    return safeWindow(fromCandidate, upperAnchor);
  }

  /* ===================== UPDATE ===================== */

  /**
   * Resolve window for UPDATE mode.
   *
   * <p>Distinguishes between time-driven and ID-driven flows; time-driven requires a user window or
   * an offsetType of DATE.
   */
  private PlannerWindow resolveUpdate(
      ProvenanceConfigSnapshot.WindowOffsetConfig cfg,
      Instant updateWM,
      Instant userFrom,
      Instant userTo,
      Instant nowSafe,
      String timezone) {
    Duration windowSize = resolveWindowSize(cfg, DEFAULT_WINDOW_SIZE);
    boolean timeDriven =
        (cfg != null
                && StrUtil.equalsIgnoreCase(cfg.offsetTypeCode(), "DATE")
                && (userFrom != null || userTo != null))
            || (userFrom != null || userTo != null);

    log.debug(
        "Resolving UPDATE window: updateWM={}, userFrom={}, userTo={}, nowSafe={}, windowSize={}, timeDriven={}",
        updateWM,
        userFrom,
        userTo,
        nowSafe,
        windowSize,
        timeDriven);

    Instant fromCandidate;
    Instant toCandidate;
    if (timeDriven) {
      toCandidate = minInstant(userTo, nowSafe);
      if (toCandidate == null) {
        toCandidate = nowSafe;
      }
      if (updateWM != null && userFrom != null) {
        fromCandidate = maxInstant(updateWM, userFrom);
        log.debug("Time-driven with updateWM and userFrom: fromCandidate={}", fromCandidate);
      } else if (updateWM != null) {
        fromCandidate = updateWM;
        if (userFrom != null && userFrom.isAfter(fromCandidate)) {
          fromCandidate = userFrom;
        }
        log.debug("Time-driven with updateWM: fromCandidate={}", fromCandidate);
      } else if (userFrom != null) {
        fromCandidate = userFrom;
        log.debug("Time-driven with userFrom only: fromCandidate={}", fromCandidate);
      } else {
        fromCandidate = nowSafe.minus(windowSize);
        log.debug("Time-driven default: fromCandidate={}", fromCandidate);
      }
    } else { // ID-driven
      if (userFrom != null || userTo != null) {
        toCandidate = minInstant(userTo, nowSafe);
        if (toCandidate == null) {
          toCandidate = nowSafe;
        }
        fromCandidate = userFrom != null ? userFrom : toCandidate.minus(windowSize);
        log.debug("ID-driven with user bounds: fromCandidate={}", fromCandidate);
      } else {
        toCandidate = nowSafe;
        fromCandidate = nowSafe.minus(windowSize);
        log.debug("ID-driven default: fromCandidate={}", fromCandidate);
      }
    }

    if (isCalendarMode(cfg) && cfg != null) {
      ZoneId zone = resolveZone(timezone);
      fromCandidate = alignFloor(fromCandidate, cfg.calendarAlignTo(), zone);
      toCandidate = alignFloor(toCandidate, cfg.calendarAlignTo(), zone);
      log.debug("Calendar alignment applied: from={}, to={}", fromCandidate, toCandidate);
    }
    if (!toCandidate.isAfter(fromCandidate)) {
      log.debug("UPDATE window empty: {} >= {}", fromCandidate, toCandidate);
      return nullWindowIfEmpty(fromCandidate, toCandidate);
    }

    log.debug("Resolved UPDATE window: [{}, {})", fromCandidate, toCandidate);
    return safeWindow(fromCandidate, toCandidate);
  }

  /* ===================== Helpers ===================== */

  /**
   * Construct a safe window: if the length is below the minimal threshold, expand 'to' to avoid a
   * zero-length window.
   */
  private PlannerWindow safeWindow(Instant from, Instant to) {
    if (Duration.between(from, to).compareTo(MIN_EFFECTIVE_WINDOW) < 0) {
      to = from.plus(MIN_EFFECTIVE_WINDOW);
    }
    return new PlannerWindow(from, to);
  }

  /**
   * Minimal-window fallback: when alignment makes from >= to, return [from, from +
   * MIN_EFFECTIVE_WINDOW] so the caller can keep a linear flow while still being able to detect the
   * original emptiness.
   */
  private PlannerWindow nullWindowIfEmpty(Instant from, Instant to) {
    // Return a minimal window so upstream can proceed and validators can handle the empty case;
    // avoids throwing IllegalArgumentException during planning.
    return new PlannerWindow(from, from.plus(MIN_EFFECTIVE_WINDOW));
  }
}
