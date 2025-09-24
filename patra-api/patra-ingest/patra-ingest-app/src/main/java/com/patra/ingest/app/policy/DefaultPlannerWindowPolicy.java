package com.patra.ingest.app.policy;

import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;
import java.util.Optional;

/**
 * 默认计划器窗口策略实现（App Layer · Policy）。
 * <p>基于最新 {@link ProvenanceConfigSnapshot.WindowOffsetConfig} 生成时间窗口；
 * 支持 SLIDING / CALENDAR 两种窗口模式。缺省（无配置或字段为空）视为全量（返回 null-null 窗口）。</p>
 * <p>优先级：
 * <ol>
 *   <li>手动指定窗口 (requestedWindowFrom/To)</li>
 *   <li>UPDATE 操作 → 全量（null-null）</li>
 *   <li>窗口配置（SLIDING / CALENDAR）</li>
 *   <li>降级：无配置 → 全量（null-null）</li>
 * </ol></p>
 * <p>配置字段要点（映射 WindowOffsetConfig）：
 * <ul>
 *   <li>windowModeCode: SLIDING | CALENDAR</li>
 *   <li>windowSizeValue + windowSizeUnitCode (SECOND|MINUTE|HOUR|DAY)</li>
 *   <li>overlapValue + overlapUnitCode：重叠回溯（再次获取边界重叠部分）</li>
 *   <li>lookbackValue + lookbackUnitCode：初始窗口额外回看</li>
 *   <li>watermarkLagSeconds：安全滞后（避免未完全落地的数据）</li>
 *   <li>maxWindowSpanSeconds：硬限制（防止窗口过大）</li>
 *   <li>calendarAlignTo：CALENDAR 模式对齐粒度（HOUR|DAY|WEEK|MONTH）</li>
 * </ul></p>
 * <p>全量刷新：无 windowOffset 或 windowModeCode 为空。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class DefaultPlannerWindowPolicy implements PlannerWindowPolicy {

    private static final Duration DEFAULT_HARVEST_WINDOW = Duration.ofDays(1); // 缺省滑动窗口大小
    private static final Duration DEFAULT_SAFETY_LAG = Duration.ofHours(1);    // 缺省安全滞后
    private static final Duration DEFAULT_MIN_WINDOW = Duration.ofMinutes(1);  // 最小窗口长度
    private static final Duration DEFAULT_MAX_WINDOW = Duration.ofDays(7);     // 最大窗口长度（无配置）

    @Override
    public PlannerWindow resolveWindow(PlanTriggerNorm triggerNorm,
                                       ProvenanceConfigSnapshot snapshot,
                                       Instant cursorWatermark,
                                       Instant currentTime) {
        log.debug("resolving window provenance={}, operation={}, trigger={}",
                triggerNorm.provenanceCode(), triggerNorm.operationType(), triggerNorm.triggerType());

        // 1. UPDATE → 全量
        if (triggerNorm.isUpdate()) {
            log.debug("UPDATE operation → full refresh (null window)");
            return PlannerWindow.full();
        }
        // 2. 手动指定窗口
        if (triggerNorm.requestedWindowFrom() != null && triggerNorm.requestedWindowTo() != null) {
            log.debug("manual window override: {} -> {}", triggerNorm.requestedWindowFrom(), triggerNorm.requestedWindowTo());
            return new PlannerWindow(triggerNorm.requestedWindowFrom(), triggerNorm.requestedWindowTo());
        }
        // 3. 配置驱动
        ProvenanceConfigSnapshot.WindowOffsetConfig cfg = snapshot.windowOffset();
        if (cfg == null || cfg.windowModeCode() == null || cfg.windowModeCode().isBlank()) {
            log.debug("no windowOffset config → full refresh (null window)");
            return PlannerWindow.full();
        }
        String mode = cfg.windowModeCode().toUpperCase();
        return switch (mode) {
            case "SLIDING" -> buildSlidingWindow(cfg, cursorWatermark, currentTime);
            case "CALENDAR" ->
                    buildCalendarWindow(cfg, cursorWatermark, currentTime, snapshot.provenance().timezoneDefault());
            default -> {
                log.warn("unsupported windowModeCode={} → fallback full refresh", mode);
                yield PlannerWindow.full();
            }
        };
    }

    /* ===================== SLIDING MODE ===================== */

    private PlannerWindow buildSlidingWindow(ProvenanceConfigSnapshot.WindowOffsetConfig cfg,
                                             Instant cursorWatermark,
                                             Instant now) {
        Duration watermarkLag = watermarkLag(cfg);
        Instant effectiveNow = now.minus(watermarkLag); // 可见上界

        // 起点：有 cursor → cursor - overlap；否则 (effectiveNow - windowSize - lookback)
        Instant from;
        Duration overlap = parseDuration(cfg.overlapValue(), cfg.overlapUnitCode(), Duration.ZERO);
        if (cursorWatermark != null) {
            from = cursorWatermark.minus(overlap);
        } else {
            Duration windowSize = windowSize(cfg);
            Duration lookback = parseDuration(cfg.lookbackValue(), cfg.lookbackUnitCode(), Duration.ZERO);
            from = effectiveNow.minus(windowSize).minus(lookback);
        }
        Instant to = determineSlidingEnd(cfg, from, effectiveNow);
        if (!to.isAfter(from)) {
            to = from.plusSeconds(1);
        }
        to = applyWindowSpanConstraints(cfg, from, to);
        log.debug("SLIDING window built: {} -> {} ({}s)", from, to, Duration.between(from, to).getSeconds());
        return new PlannerWindow(from, to);
    }

    private Instant determineSlidingEnd(ProvenanceConfigSnapshot.WindowOffsetConfig cfg,
                                        Instant from,
                                        Instant effectiveNow) {
        Duration size = windowSize(cfg);
        Instant calc = from.plus(size);
        // 不得超过可见上界
        return calc.isAfter(effectiveNow) ? effectiveNow : calc;
    }

    /* ===================== CALENDAR MODE ===================== */

    private PlannerWindow buildCalendarWindow(ProvenanceConfigSnapshot.WindowOffsetConfig cfg,
                                              Instant cursorWatermark,
                                              Instant now,
                                              String timezone) {
        ZoneId zoneId = safeZone(timezone);
        Duration watermarkLag = watermarkLag(cfg);
        Instant effectiveNow = now.minus(watermarkLag);

        // 对齐结束边界（floor）
        Instant alignedEnd = alignToBoundary(effectiveNow, cfg.calendarAlignTo(), zoneId);
        if (alignedEnd.isAfter(effectiveNow)) {
            // 防守式：若由于对齐逻辑导致超前，回退一个单位
            alignedEnd = rollBackOneUnit(alignedEnd, cfg.calendarAlignTo(), zoneId);
        }

        Duration windowSize = windowSize(cfg); // 使用 windowSizeValue * unit
        Instant start = alignedEnd.minus(windowSize);

        // lookback: 仅当无 cursor 或首次执行。此处简单：无 cursor 时添加。
        if (cursorWatermark == null) {
            Duration lookback = parseDuration(cfg.lookbackValue(), cfg.lookbackUnitCode(), Duration.ZERO);
            start = start.minus(lookback);
        }

        // overlap: 有 cursor 时再扩大起点
        if (cursorWatermark != null) {
            Duration overlap = parseDuration(cfg.overlapValue(), cfg.overlapUnitCode(), Duration.ZERO);
            Instant overlapStart = cursorWatermark.minus(overlap);
            if (overlapStart.isBefore(start)) {
                start = overlapStart;
            }
        }

        Instant end = alignedEnd; // CALENDAR 模式以对齐点作为结束（不超过 effectiveNow）
        if (!end.isAfter(start)) {
            end = start.plusSeconds(1);
        }
        end = applyWindowSpanConstraints(cfg, start, end);
        log.debug("CALENDAR window built: {} -> {} ({}s) align={}, tz={}", start, end,
                Duration.between(start, end).getSeconds(), cfg.calendarAlignTo(), zoneId);
        return new PlannerWindow(start, end);
    }

    private ZoneId safeZone(String tz) {
        try {
            return tz == null || tz.isBlank() ? ZoneOffset.UTC : ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneOffset.UTC;
        }
    }

    private Instant alignToBoundary(Instant instant, String alignTo, ZoneId zone) {
        if (alignTo == null || alignTo.isBlank()) return truncateToHour(instant, zone); // 默认按小时
        String a = alignTo.toUpperCase();
        ZonedDateTime zdt = instant.atZone(zone);
        return switch (a) {
            case "HOUR" -> zdt.withMinute(0).withSecond(0).withNano(0).toInstant();
            case "DAY" -> zdt.toLocalDate().atStartOfDay(zone).toInstant();
            case "WEEK" -> zdt.with(ChronoField.DAY_OF_WEEK, 1).toLocalDate().atStartOfDay(zone).toInstant(); // ISO 周一
            case "MONTH" -> zdt.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay(zone).toInstant();
            default -> truncateToHour(instant, zone);
        };
    }

    private Instant rollBackOneUnit(Instant end, String alignTo, ZoneId zone) {
        if (alignTo == null) return end.minus(Duration.ofHours(1));
        return switch (alignTo.toUpperCase()) {
            case "HOUR" -> end.minus(Duration.ofHours(1));
            case "DAY" -> end.minus(Duration.ofDays(1));
            case "WEEK" -> end.minus(Duration.ofDays(7));
            case "MONTH" -> end.atZone(zone).minusMonths(1).toInstant();
            default -> end.minus(Duration.ofHours(1));
        };
    }

    private Instant truncateToHour(Instant instant, ZoneId zone) {
        ZonedDateTime z = instant.atZone(zone);
        return z.withMinute(0).withSecond(0).withNano(0).toInstant();
    }

    /* ===================== COMMON HELPERS ===================== */

    private Duration watermarkLag(ProvenanceConfigSnapshot.WindowOffsetConfig cfg) {
        return Optional.ofNullable(cfg.watermarkLagSeconds()).map(Duration::ofSeconds).orElse(DEFAULT_SAFETY_LAG);
    }

    private Duration windowSize(ProvenanceConfigSnapshot.WindowOffsetConfig cfg) {
        if (cfg.windowSizeValue() == null) return DEFAULT_HARVEST_WINDOW;
        return parseDuration(cfg.windowSizeValue(), cfg.windowSizeUnitCode(), DEFAULT_HARVEST_WINDOW);
    }

    private Duration parseDuration(Integer value, String unitCode, Duration defaultIfNull) {
        if (value == null) return defaultIfNull;
        String u = (unitCode == null ? "HOUR" : unitCode).trim().toUpperCase(); // 支持单数/兼容性
        return switch (u) {
            case "SECOND", "SECONDS" -> Duration.ofSeconds(value);
            case "MINUTE", "MINUTES" -> Duration.ofMinutes(value);
            case "HOUR", "HOURS" -> Duration.ofHours(value);
            case "DAY", "DAYS" -> Duration.ofDays(value);
            default -> {
                log.warn("unknown time unit={}, fallback default", u);
                yield defaultIfNull;
            }
        };
    }

    private Instant applyWindowSpanConstraints(ProvenanceConfigSnapshot.WindowOffsetConfig cfg,
                                               Instant from,
                                               Instant to) {
        Instant result = to;
        if (cfg.maxWindowSpanSeconds() != null) {
            Instant maxTo = from.plusSeconds(cfg.maxWindowSpanSeconds());
            if (result.isAfter(maxTo)) {
                result = maxTo;
                log.debug("applied configured maxWindowSpanSeconds={}s", cfg.maxWindowSpanSeconds());
            }
        } else {
            Instant maxTo = from.plus(DEFAULT_MAX_WINDOW);
            if (result.isAfter(maxTo)) {
                result = maxTo;
                log.debug("applied default max window {}s", DEFAULT_MAX_WINDOW.getSeconds());
            }
        }
        // 最小窗口
        Instant minTo = from.plus(DEFAULT_MIN_WINDOW);
        if (result.isBefore(minTo)) {
            result = minTo;
            log.debug("applied min window {}s", DEFAULT_MIN_WINDOW.getSeconds());
        }
        return result;
    }
}
