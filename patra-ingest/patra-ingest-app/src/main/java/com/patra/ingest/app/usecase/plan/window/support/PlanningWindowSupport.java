package com.patra.ingest.app.usecase.plan.window.support;

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
 * Common utilities for planning window calculations.
 * <p>
 * Aggregates WindowOffset configuration helpers and time utilities for reuse by
 * application-layer window resolution strategies. All methods are pure functions
 * to make unit testing and reuse straightforward.
 * </p>
 *
 * <p>This class is stateless and not instantiable.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class PlanningWindowSupport {

    private PlanningWindowSupport() {
    }

    /**
     * Compute a "lagged now": subtract the configured watermark lag from 'now', and then subtract the
     * default floor lag.
     *
     * @param currentTime  current time
     * @param offsetConfig window offset settings from the provenance/source configuration
     * @param defaultLag   default floor/safety lag
     * @return a capped current-time instant (used as the window upper bound)
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
     * Resolve the window size (fallback to a default when absent).
     *
     * @param offsetConfig WindowOffset configuration
     * @param defaultSize  default window size
     * @return effective window size
     */
    public static Duration resolveWindowSize(ProvenanceConfigSnapshot.WindowOffsetConfig offsetConfig,
                                             Duration defaultSize) {
        if (offsetConfig == null) {
            return ObjectUtil.defaultIfNull(defaultSize, Duration.ZERO);
        }
        return resolveDuration(offsetConfig.windowSizeValue(), offsetConfig.windowSizeUnitCode(), defaultSize);
    }

    /**
     * Resolve a duration from value/unit; returns the default when value is null or unit is unknown.
     *
     * @param value        numeric value
     * @param unitCode     unit code (second/minute/hour/day)
     * @param defaultValue default duration
     * @return parsed Duration
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

    /**
     * Return the minimum of the given instants, ignoring nulls.
     *
     * @param instants collection of instants
     * @return minimum instant; null when all are null
     */
    public static Instant minInstant(Instant... instants) {
        return stream(instants).filter(Objects::nonNull).min(Instant::compareTo).orElse(null);
    }

    /**
     * Return the maximum of the given instants, ignoring nulls.
     *
     * @param instants collection of instants
     * @return maximum instant; null when all are null
     */
    public static Instant maxInstant(Instant... instants) {
        return stream(instants).filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
    }

    /**
     * Resolve ZoneId from a textual id; fallback to UTC when it cannot be parsed.
     *
     * @param timezoneId textual timezone id
     * @return ZoneId instance
     */
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

    /**
     * Whether the window mode is calendar-based.
     *
     * @param offsetConfig WindowOffset configuration
     * @return true if calendar mode
     */
    public static boolean isCalendarMode(ProvenanceConfigSnapshot.WindowOffsetConfig offsetConfig) {
        return offsetConfig != null && StrUtil.equalsIgnoreCase(offsetConfig.windowModeCode(), "CALENDAR");
    }

    /**
     * Align an instant downwards to the specified calendar boundary.
     *
     * @param instant original instant
     * @param alignTo alignment unit (hour/day/week/month)
     * @param zone    timezone
     * @return aligned instant
     */
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

    /**
     * Wrap an array into a stream so null can be handled uniformly.
     */
    private static Stream<Instant> stream(Instant... instants) {
        return instants == null ? Stream.empty() : Stream.of(instants);
    }
}
