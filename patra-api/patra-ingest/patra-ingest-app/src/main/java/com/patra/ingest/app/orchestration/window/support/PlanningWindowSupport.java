package com.patra.ingest.app.orchestration.window.support;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 计划窗口计算通用辅助类。
 */
public final class PlanningWindowSupport {

    private PlanningWindowSupport() {
    }

    /**
     * 计算安全时间戳：now 减去配置中的 watermark lag，再减去默认兜底 lag。
     */
    public static Instant computeLaggedNow(Instant currentTime,
                                           ProvenanceConfigSnapshot.WindowOffsetConfig offsetConfig,
                                           Duration defaultLag) {
        Duration fallbackLag = ObjectUtil.defaultIfNull(defaultLag, Duration.ZERO);
        Instant base = currentTime.minus(fallbackLag);
        if (offsetConfig == null || offsetConfig.watermarkLagSeconds() == null) {
            return base;
        }
        long lagSeconds = Math.max(0L, offsetConfig.watermarkLagSeconds());
        return base.minusSeconds(lagSeconds);
    }

    /**
     * 解析窗口尺寸（为空使用默认值）。
     */
    public static Duration resolveWindowSize(ProvenanceConfigSnapshot.WindowOffsetConfig offsetConfig,
                                             Duration defaultSize) {
        if (offsetConfig == null) {
            return ObjectUtil.defaultIfNull(defaultSize, Duration.ZERO);
        }
        return resolveDuration(offsetConfig.windowSizeValue(), offsetConfig.windowSizeUnitCode(), defaultSize);
    }

    /**
     * 解析持续时间配置；value 空或 unit 未识别时返回默认值。
     */
    public static Duration resolveDuration(Integer value, String unitCode, Duration defaultValue) {
        if (value == null) {
            return ObjectUtil.defaultIfNull(defaultValue, Duration.ZERO);
        }
        String normalized = StrUtil.blankToDefault(unitCode, "HOUR").trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SECOND", "SECONDS" -> Duration.ofSeconds(value);
            case "MINUTE", "MINUTES" -> Duration.ofMinutes(value);
            case "HOUR", "HOURS" -> Duration.ofHours(value);
            case "DAY", "DAYS" -> Duration.ofDays(value);
            default -> ObjectUtil.defaultIfNull(defaultValue, Duration.ZERO);
        };
    }

    public static Instant minInstant(Instant... instants) {
        return stream(instants).filter(Objects::nonNull).min(Instant::compareTo).orElse(null);
    }

    public static Instant maxInstant(Instant... instants) {
        return stream(instants).filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
    }

    public static ZoneId resolveZone(String timezoneId) {
        if (StrUtil.isBlank(timezoneId)) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezoneId.trim());
        } catch (Exception ignored) {
            return ZoneOffset.UTC;
        }
    }

    public static boolean isCalendarMode(ProvenanceConfigSnapshot.WindowOffsetConfig offsetConfig) {
        return offsetConfig != null && StrUtil.equalsIgnoreCase(offsetConfig.windowModeCode(), "CALENDAR");
    }

    public static Instant alignFloor(Instant instant, String alignTo, ZoneId zone) {
        String normalized = StrUtil.blankToDefault(alignTo, "HOUR").trim().toUpperCase(Locale.ROOT);
        LocalDateTime time = LocalDateTime.ofInstant(instant, zone);
        LocalDateTime aligned = switch (normalized) {
            case "HOUR", "HOURS" -> time.withMinute(0).withSecond(0).withNano(0);
            case "DAY", "DAYS" -> time.toLocalDate().atStartOfDay();
            case "WEEK", "WEEKS" -> time.with(ChronoField.DAY_OF_WEEK, 1).toLocalDate().atStartOfDay();
            case "MONTH", "MONTHS" -> time.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay();
            default -> time.withMinute(0).withSecond(0).withNano(0);
        };
        return aligned.atZone(zone).toInstant();
    }

    private static Stream<Instant> stream(Instant... instants) {
        return instants == null ? Stream.empty() : Stream.of(instants);
    }
}
