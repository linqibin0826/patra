package com.patra.ingest.app.orchestration.window;

import static com.patra.ingest.app.orchestration.window.support.PlanningWindowSupport.alignFloor;
import static com.patra.ingest.app.orchestration.window.support.PlanningWindowSupport.computeLaggedNow;
import static com.patra.ingest.app.orchestration.window.support.PlanningWindowSupport.isCalendarMode;
import static com.patra.ingest.app.orchestration.window.support.PlanningWindowSupport.maxInstant;
import static com.patra.ingest.app.orchestration.window.support.PlanningWindowSupport.minInstant;
import static com.patra.ingest.app.orchestration.window.support.PlanningWindowSupport.resolveDuration;
import static com.patra.ingest.app.orchestration.window.support.PlanningWindowSupport.resolveWindowSize;
import static com.patra.ingest.app.orchestration.window.support.PlanningWindowSupport.resolveZone;

import cn.hutool.core.util.StrUtil;
import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认计划器窗口策略实现（App Layer · Policy）。
 * <p>
 * 实现 HARVEST / BACKFILL / UPDATE 三类窗口确定算法：
 * <p>
 * HARVEST：
 * to = min(user.to?, now - lag)
 * if cursor exists: fromCandidate = max(user.from?, cursor - lookback)
 * else: fromCandidate = user.from? | (nowSafe - windowSize)
 * CALENDAR: from/to 向下对齐；对齐后相等视为空窗口
 * <p>
 * BACKFILL：
 * upperAnchor = min(user.to?, forwardWM?, now - lag)
 * if backfillCursor exists: fromCandidate = max(backfillWM, user.from?)
 * else: fromCandidate = user.from? | (upperAnchor - windowSize)
 * fromCandidate 不可超过 upperAnchor
 * CALENDAR: 对齐
 * <p>
 * UPDATE：
 * 时间驱动（有用户窗口 or offset_type=DATE 且提供 windowFrom/To）：
 * to = min(user.to?, nowSafe)
 * from = max(user.from?, updateWM?) | (nowSafe - windowSize) 当均为空
 * ID 驱动：
 * 若无用户窗口：from = nowSafe - windowSize, to = nowSafe
 * CALENDAR：对齐
 * <p>
 * 其它细节：
 * - watermark_lag_seconds 为空视为0
 * - lookback / overlap 在 Plan 总窗不应用 overlap（overlap 在 slice 阶段使用），此实现忽略 overlap
 * - maxWindowSpanSeconds 不在总窗强制截断（切片阶段约束 slice），若配置需强制可在此加保护，这里仅做最小防守
 */
@Slf4j
@Component
public class DefaultPlanningWindowResolver implements PlanningWindowResolver {

    private static final Duration DEFAULT_WINDOW_SIZE = Duration.ofHours(24);
    private static final Duration DEFAULT_SAFETY_LAG = Duration.ZERO;
    private static final Duration MIN_EFFECTIVE_WINDOW = Duration.ofSeconds(1); // 避免非正向

    @Override
    public PlannerWindow resolveWindow(PlanTriggerNorm triggerNorm,
                                       ProvenanceConfigSnapshot snapshot,
                                       Instant cursorWatermark,
                                       Instant currentTime) {
        ProvenanceConfigSnapshot.WindowOffsetConfig cfg = snapshot == null ? null : snapshot.windowOffset();
        String timezone = snapshot != null && snapshot.provenance() != null
                ? snapshot.provenance().timezoneDefault()
                : null;

        Instant userFrom = triggerNorm.requestedWindowFrom();
        Instant userTo = triggerNorm.requestedWindowTo();

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
    private PlannerWindow resolveHarvest(ProvenanceConfigSnapshot.WindowOffsetConfig cfg,
                                         Instant harvestWM,
                                         Instant userFrom,
                                         Instant userTo,
                                         Instant nowSafe,
                                         String timezone) {
        Duration windowSize = resolveWindowSize(cfg, DEFAULT_WINDOW_SIZE);
        Duration lookback = resolveDuration(cfg == null ? null : cfg.lookbackValue(),
                cfg == null ? null : cfg.lookbackUnitCode(), Duration.ZERO);

        Instant toCandidate = minInstant(userTo, nowSafe);
        if (toCandidate == null) {
            toCandidate = nowSafe; // 没有用户上界 → 使用 nowSafe
        }

        Instant fromCandidate;
        if (harvestWM != null) {
            Instant lowerByCursor = harvestWM.minus(lookback);
            fromCandidate = maxInstant(lowerByCursor, userFrom);
        } else if (userFrom != null) {
            fromCandidate = userFrom;
        } else {
            fromCandidate = toCandidate.minus(windowSize); // 默认回退一窗
        }

        if (isCalendarMode(cfg)) {
            ZoneId zone = resolveZone(timezone);
            fromCandidate = alignFloor(fromCandidate, cfg.calendarAlignTo(), zone);
            toCandidate = alignFloor(toCandidate, cfg.calendarAlignTo(), zone);
        }

        if (!toCandidate.isAfter(fromCandidate)) {
            log.debug("HARVEST window empty after alignment: {} >= {}", fromCandidate, toCandidate);
            return nullWindowIfEmpty(fromCandidate, toCandidate);
        }
        return safeWindow(fromCandidate, toCandidate);
    }

    /* ===================== BACKFILL ===================== */
    private PlannerWindow resolveBackfill(ProvenanceConfigSnapshot.WindowOffsetConfig cfg,
                                          Instant backfillWM,
                                          Instant forwardWM,
                                          Instant userFrom,
                                          Instant userTo,
                                          Instant nowSafe,
                                          String timezone) {
        Duration windowSize = resolveWindowSize(cfg, DEFAULT_WINDOW_SIZE);

        // forwardWM = harvestWM 作为 upperAnchor 候选，这里 cursorWatermark 已按调用方传入对应 BACKFILL 游标；需要读取前向水位？
        // 设计中 forwardWM 来源 HARVEST 水位：此策略接口当前只拿到单一 cursorWatermark。为了不扩接口，这里假设传入的是 BACKFILL 游标；
        // 若需要 forwardWM 需扩展接口或在上层额外查询后放入 triggerParams；暂以 cursorWatermark 仅当 backfillWM 使用。
        Instant upperAnchor = minInstant(userTo, forwardWM, nowSafe);
        if (upperAnchor == null) {
            upperAnchor = nowSafe;
        }

        Instant fromCandidate;
        if (backfillWM != null) {
            fromCandidate = maxInstant(backfillWM, userFrom);
        } else if (userFrom != null) {
            fromCandidate = userFrom;
        } else {
            fromCandidate = upperAnchor.minus(windowSize);
        }
        if (fromCandidate.isAfter(upperAnchor)) {
            fromCandidate = upperAnchor; // 防越界
        }

        if (isCalendarMode(cfg)) {
            ZoneId zone = resolveZone(timezone);
            fromCandidate = alignFloor(fromCandidate, cfg.calendarAlignTo(), zone);
            upperAnchor = alignFloor(upperAnchor, cfg.calendarAlignTo(), zone);
        }
        if (!upperAnchor.isAfter(fromCandidate)) {
            log.debug("BACKFILL window empty: {} >= {}", fromCandidate, upperAnchor);
            return nullWindowIfEmpty(fromCandidate, upperAnchor);
        }
        return safeWindow(fromCandidate, upperAnchor);
    }

    /* ===================== UPDATE ===================== */
    private PlannerWindow resolveUpdate(ProvenanceConfigSnapshot.WindowOffsetConfig cfg,
                                        Instant updateWM,
                                        Instant userFrom,
                                        Instant userTo,
                                        Instant nowSafe,
                                        String timezone) {
        Duration windowSize = resolveWindowSize(cfg, DEFAULT_WINDOW_SIZE);
        boolean timeDriven = (cfg != null && StrUtil.equalsIgnoreCase(cfg.offsetTypeCode(), "DATE") && (userFrom != null || userTo != null))
                || (userFrom != null || userTo != null);

        Instant fromCandidate;
        Instant toCandidate;
        if (timeDriven) {
            toCandidate = minInstant(userTo, nowSafe);
            if (toCandidate == null) {
                toCandidate = nowSafe;
            }
            if (updateWM != null && userFrom != null) {
                fromCandidate = maxInstant(updateWM, userFrom);
            } else if (updateWM != null) {
                fromCandidate = updateWM;
                if (userFrom != null && userFrom.isAfter(fromCandidate)) {
                    fromCandidate = userFrom;
                }
            } else if (userFrom != null) {
                fromCandidate = userFrom;
            } else {
                fromCandidate = nowSafe.minus(windowSize);
            }
        } else { // ID 驱动
            if (userFrom != null || userTo != null) {
                toCandidate = minInstant(userTo, nowSafe);
                if (toCandidate == null) {
                    toCandidate = nowSafe;
                }
                fromCandidate = userFrom != null ? userFrom : toCandidate.minus(windowSize);
            } else {
                toCandidate = nowSafe;
                fromCandidate = nowSafe.minus(windowSize);
            }
        }

        if (isCalendarMode(cfg)) {
            ZoneId zone = resolveZone(timezone);
            fromCandidate = alignFloor(fromCandidate, cfg.calendarAlignTo(), zone);
            toCandidate = alignFloor(toCandidate, cfg.calendarAlignTo(), zone);
        }
        if (!toCandidate.isAfter(fromCandidate)) {
            log.debug("UPDATE window empty: {} >= {}", fromCandidate, toCandidate);
            return nullWindowIfEmpty(fromCandidate, toCandidate);
        }
        return safeWindow(fromCandidate, toCandidate);
    }

    /* ===================== Helpers ===================== */
    private PlannerWindow safeWindow(Instant from, Instant to) {
        if (Duration.between(from, to).compareTo(MIN_EFFECTIVE_WINDOW) < 0) {
            to = from.plus(MIN_EFFECTIVE_WINDOW);
        }
        return new PlannerWindow(from, to);
    }

    private PlannerWindow nullWindowIfEmpty(Instant from, Instant to) {
        // 返回一个最小窗口以便上层可感知 empty，再由 validator 处理；避免 IllegalArgumentException
        return new PlannerWindow(from, from.plus(MIN_EFFECTIVE_WINDOW));
    }
}
