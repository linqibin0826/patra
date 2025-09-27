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
 * <p>
 * 聚合 WindowOffset 配置与时间工具方法，供应用层窗口解析策略复用。
 * 所有方法均为纯函数，便于单元测试与重用。
 * </p>
 *
 * <p>该类保持无状态，禁止实例化。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class PlanningWindowSupport {

    private PlanningWindowSupport() {
    }

    /**
     * 计算安全时间戳：now 减去配置中的 watermark lag，再减去默认兜底 lag。
     *
     * @param currentTime  当前时间
     * @param offsetConfig 来源配置中的窗口偏移设置
     * @param defaultLag   默认兜底延迟
     * @return 安全时间戳（用于窗口上界）
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
     *
     * @param offsetConfig WindowOffset 配置
     * @param defaultSize  默认窗口大小
     * @return 有效窗口大小
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
     *
     * @param value        数值
     * @param unitCode     单位编码（秒/分/小时/天）
     * @param defaultValue 默认值
     * @return 解析后的 Duration
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
     * 返回给定时间集合的最小值（忽略 null）。
     *
     * @param instants 时间集合
     * @return 最小时间；若集合为空返回 null
     */
    public static Instant minInstant(Instant... instants) {
        return stream(instants).filter(Objects::nonNull).min(Instant::compareTo).orElse(null);
    }

    /**
     * 返回给定时间集合的最大值（忽略 null）。
     *
     * @param instants 时间集合
     * @return 最大时间；若集合为空返回 null
     */
    public static Instant maxInstant(Instant... instants) {
        return stream(instants).filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
    }

    /**
     * 解析时区标识，无法解析时回退到 UTC。
     *
     * @param timezoneId 时区字符串
     * @return ZoneId 实例
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
     * 判断窗口模式是否为日历模式。
     *
     * @param offsetConfig WindowOffset 配置
     * @return true 表示日历模式
     */
    public static boolean isCalendarMode(ProvenanceConfigSnapshot.WindowOffsetConfig offsetConfig) {
        return offsetConfig != null && StrUtil.equalsIgnoreCase(offsetConfig.windowModeCode(), "CALENDAR");
    }

    /**
     * 将时间向下对齐到指定的日历边界。
     *
     * @param instant 原始时间
     * @param alignTo 对齐单位（小时/天/周/月）
     * @param zone    时区
     * @return 对齐后的时间
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
     * 将数组包装成 Stream，便于统一处理空值。
     */
    private static Stream<Instant> stream(Instant... instants) {
        return instants == null ? Stream.empty() : Stream.of(instants);
    }
}
