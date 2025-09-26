package com.patra.ingest.app.orchestration.window;

import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAdjusters;

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
        ProvenanceConfigSnapshot.WindowOffsetConfig cfg = snapshot.windowOffset();
        Instant userFrom = triggerNorm.requestedWindowFrom();
        Instant userTo = triggerNorm.requestedWindowTo();

        Instant nowSafe = applyLag(currentTime, cfg);

        if (triggerNorm.isHarvest()) {
            return resolveHarvest(cfg, cursorWatermark, userFrom, userTo, nowSafe, snapshot.provenance().timezoneDefault());
        } else if (triggerNorm.isBackfill()) {
            return resolveBackfill(cfg, cursorWatermark, null, userFrom, userTo, nowSafe, snapshot.provenance().timezoneDefault());
        } else if (triggerNorm.isUpdate()) {
            return resolveUpdate(cfg, cursorWatermark, userFrom, userTo, nowSafe, snapshot.provenance().timezoneDefault());
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
        Duration windowSize = windowSize(cfg);
        Duration lookback = parseDuration(cfg == null ? null : cfg.lookbackValue(), cfg == null ? null : cfg.lookbackUnitCode(), Duration.ZERO);

        Instant toCandidate = minNonNull(userTo, nowSafe);
        if (toCandidate == null) {
            toCandidate = nowSafe; // 没有用户上界 → 使用 nowSafe
        }

        Instant fromCandidate;
        if (harvestWM != null) {
            Instant lowerByCursor = harvestWM.minus(lookback);
            fromCandidate = maxNonNull(lowerByCursor, userFrom);
        } else {
            if (userFrom != null) {
                fromCandidate = userFrom;
            } else {
                fromCandidate = toCandidate.minus(windowSize); // 默认回退一窗
            }
        }

        if (cfg != null && isCalendar(cfg)) {
            ZoneId zone = safeZone(timezone);
            fromCandidate = floorAlign(fromCandidate, cfg.calendarAlignTo(), zone);
            toCandidate = floorAlign(toCandidate, cfg.calendarAlignTo(), zone);
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
        Duration windowSize = windowSize(cfg);

        // forwardWM = harvestWM 作为 upperAnchor 候选，这里 cursorWatermark 已按调用方传入对应 BACKFILL 游标；需要读取前向水位？
        // 设计中 forwardWM 来源 HARVEST 水位：此策略接口当前只拿到单一 cursorWatermark。为了不扩接口，这里假设传入的是 BACKFILL 游标；
        // 若需要 forwardWM 需扩展接口或在上层额外查询后放入 triggerParams；暂以 cursorWatermark 仅当 backfillWM 使用。
    Instant upperAnchor = minNonNull(userTo, forwardWM, nowSafe);
        if (upperAnchor == null) upperAnchor = nowSafe;

        Instant fromCandidate;
        if (backfillWM != null) {
            fromCandidate = maxNonNull(backfillWM, userFrom);
        } else {
            if (userFrom != null) {
                fromCandidate = userFrom;
            } else {
                fromCandidate = upperAnchor.minus(windowSize);
            }
        }
        if (fromCandidate.isAfter(upperAnchor)) {
            fromCandidate = upperAnchor; // 防越界
        }

        if (cfg != null && isCalendar(cfg)) {
            ZoneId zone = safeZone(timezone);
            fromCandidate = floorAlign(fromCandidate, cfg.calendarAlignTo(), zone);
            upperAnchor = floorAlign(upperAnchor, cfg.calendarAlignTo(), zone);
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
        Duration windowSize = windowSize(cfg);
        boolean timeDriven = (cfg != null && "DATE".equalsIgnoreCase(cfg.offsetTypeCode()) && (userFrom != null || userTo != null))
                || (userFrom != null || userTo != null);

        Instant fromCandidate;
        Instant toCandidate;
        if (timeDriven) {
            toCandidate = minNonNull(userTo, nowSafe);
            if (toCandidate == null) toCandidate = nowSafe;
            if (updateWM != null && userFrom != null) {
                fromCandidate = maxNonNull(updateWM, userFrom);
            } else if (updateWM != null) {
                fromCandidate = updateWM;
                if (userFrom != null && userFrom.isAfter(fromCandidate)) fromCandidate = userFrom;
            } else if (userFrom != null) {
                fromCandidate = userFrom;
            } else {
                fromCandidate = nowSafe.minus(windowSize);
            }
        } else { // ID 驱动
            if (userFrom != null || userTo != null) {
                toCandidate = minNonNull(userTo, nowSafe);
                if (toCandidate == null) toCandidate = nowSafe;
                fromCandidate = userFrom != null ? userFrom : toCandidate.minus(windowSize);
            } else {
                toCandidate = nowSafe;
                fromCandidate = nowSafe.minus(windowSize);
            }
        }

        if (cfg != null && isCalendar(cfg)) {
            ZoneId zone = safeZone(timezone);
            fromCandidate = floorAlign(fromCandidate, cfg.calendarAlignTo(), zone);
            toCandidate = floorAlign(toCandidate, cfg.calendarAlignTo(), zone);
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

    private boolean isCalendar(ProvenanceConfigSnapshot.WindowOffsetConfig cfg) {
        return cfg != null && cfg.windowModeCode() != null && "CALENDAR".equalsIgnoreCase(cfg.windowModeCode());
    }

    private Instant applyLag(Instant now, ProvenanceConfigSnapshot.WindowOffsetConfig cfg) {
        if (cfg == null || cfg.watermarkLagSeconds() == null) return now.minus(DEFAULT_SAFETY_LAG);
        return now.minusSeconds(cfg.watermarkLagSeconds());
    }

    private Duration windowSize(ProvenanceConfigSnapshot.WindowOffsetConfig cfg) {
        if (cfg == null || cfg.windowSizeValue() == null) return DEFAULT_WINDOW_SIZE;
        return parseDuration(cfg.windowSizeValue(), cfg.windowSizeUnitCode(), DEFAULT_WINDOW_SIZE);
    }

    private Duration parseDuration(Integer value, String unitCode, Duration defaultIfNull) {
        if (value == null) return defaultIfNull;
        String u = (unitCode == null ? "HOUR" : unitCode).trim().toUpperCase();
        return switch (u) {
            case "SECOND", "SECONDS" -> Duration.ofSeconds(value);
            case "MINUTE", "MINUTES" -> Duration.ofMinutes(value);
            case "HOUR", "HOURS" -> Duration.ofHours(value);
            case "DAY", "DAYS" -> Duration.ofDays(value);
            default -> defaultIfNull;
        };
    }

    private Instant minNonNull(Instant... instants) {
        Instant result = null;
        for (Instant i : instants) {
            if (i == null) continue;
            if (result == null || i.isBefore(result)) result = i;
        }
        return result;
    }

    private Instant maxNonNull(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private ZoneId safeZone(String tz) {
        try {
            return tz == null || tz.isBlank() ? ZoneOffset.UTC : ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneOffset.UTC;
        }
    }

    private Instant floorAlign(Instant instant, String alignTo, ZoneId zone) {
        if (alignTo == null || alignTo.isBlank()) return truncateToHour(instant, zone);
        String a = alignTo.toUpperCase();
        ZonedDateTime zdt = instant.atZone(zone);
        return switch (a) {
            case "HOUR" -> zdt.withMinute(0).withSecond(0).withNano(0).toInstant();
            case "DAY" -> zdt.toLocalDate().atStartOfDay(zone).toInstant();
            case "WEEK" -> zdt.with(ChronoField.DAY_OF_WEEK, 1).toLocalDate().atStartOfDay(zone).toInstant();
            case "MONTH" -> zdt.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay(zone).toInstant();
            default -> truncateToHour(instant, zone);
        };
    }

    private Instant truncateToHour(Instant instant, ZoneId zone) {
        ZonedDateTime z = instant.atZone(zone);
        return z.withMinute(0).withSecond(0).withNano(0).toInstant();
    }
}
