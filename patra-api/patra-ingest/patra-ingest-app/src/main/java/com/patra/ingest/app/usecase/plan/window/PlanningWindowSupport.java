package com.patra.ingest.app.usecase.plan.window;

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
 * 窗口规划计算的通用工具类。
 *
 * <p>聚合了 WindowOffset 配置辅助方法和时间工具,供应用层窗口解析策略复用。 所有方法都是纯函数,便于单元测试和复用。
 *
 * <p>该类是无状态的,不可实例化。
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class PlanningWindowSupport {

  private PlanningWindowSupport() {}

  /**
   * 计算"延迟的当前时间":从当前时间减去配置的水位线延迟,然后再减去默认的基准延迟。
   *
   * <p>算法步骤:
   *
   * <ol>
   *   <li>从 currentTime 减去 defaultLag 得到基准时间
   *   <li>如果配置了 watermarkLag,再减去该延迟
   *   <li>返回延迟后的时间(用作窗口上界)
   * </ol>
   *
   * @param currentTime 当前时间
   * @param offsetConfig 窗口偏移配置(来自 Provenance/数据源配置)
   * @param defaultLag 默认基准/安全延迟
   * @return 延迟后的当前时间实例(用作窗口上界)
   */
  public static Instant computeLaggedNow(
      Instant currentTime,
      ProvenanceConfigSnapshot.WindowOffsetConfig offsetConfig,
      Duration defaultLag) {
    // 步骤1: 计算基准延迟时间
    Duration fallbackLag = ObjectUtil.defaultIfNull(defaultLag, Duration.ZERO);
    Instant base = currentTime.minus(fallbackLag);

    // 步骤2: 如果没有配置水位线延迟,直接返回基准时间
    if (offsetConfig == null || offsetConfig.watermarkLagSeconds() == null) {
      return base;
    }

    // 步骤3: 应用水位线延迟(确保非负)
    long lagSeconds = Math.max(0L, offsetConfig.watermarkLagSeconds());
    return base.minusSeconds(lagSeconds);
  }

  /**
   * 解析窗口大小(如果缺失则回退到默认值)。
   *
   * @param offsetConfig WindowOffset 配置
   * @param defaultSize 默认窗口大小
   * @return 有效的窗口大小
   */
  public static Duration resolveWindowSize(
      ProvenanceConfigSnapshot.WindowOffsetConfig offsetConfig, Duration defaultSize) {
    if (offsetConfig == null) {
      return ObjectUtil.defaultIfNull(defaultSize, Duration.ZERO);
    }
    return resolveDuration(
        offsetConfig.windowSizeValue(), offsetConfig.windowSizeUnitCode(), defaultSize);
  }

  /**
   * 从值/单位解析时间段;当值为 null 或单位未知时返回默认值。
   *
   * <p>支持的时间单位:
   *
   * <ul>
   *   <li>SECOND/SECONDS - 秒
   *   <li>MINUTE/MINUTES - 分钟
   *   <li>HOUR/HOURS - 小时(默认)
   *   <li>DAY/DAYS - 天
   * </ul>
   *
   * @param value 数值
   * @param unitCode 单位代码(second/minute/hour/day)
   * @param defaultValue 默认时间段
   * @return 解析后的 Duration
   */
  public static Duration resolveDuration(Integer value, String unitCode, Duration defaultValue) {
    if (value == null) {
      return ObjectUtil.defaultIfNull(defaultValue, Duration.ZERO);
    }
    // 标准化单位代码(转大写,默认为 HOUR)
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
   * 返回给定时间点中的最小值,忽略 null 值。
   *
   * @param instants 时间点集合
   * @return 最小时间点;如果全为 null 则返回 null
   */
  public static Instant minInstant(Instant... instants) {
    return stream(instants).filter(Objects::nonNull).min(Instant::compareTo).orElse(null);
  }

  /**
   * 返回给定时间点中的最大值,忽略 null 值。
   *
   * @param instants 时间点集合
   * @return 最大时间点;如果全为 null 则返回 null
   */
  public static Instant maxInstant(Instant... instants) {
    return stream(instants).filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
  }

  /**
   * 从文本 ID 解析 ZoneId;无法解析时回退到 UTC。
   *
   * @param timezoneId 文本形式的时区 ID
   * @return ZoneId 实例
   */
  public static ZoneId resolveZone(String timezoneId) {
    if (StrUtil.isBlank(timezoneId)) {
      return ZoneOffset.UTC;
    }
    try {
      return ZoneId.of(timezoneId.trim());
    } catch (Exception ignored) {
      // 解析失败时回退到 UTC
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
    return offsetConfig != null
        && StrUtil.equalsIgnoreCase(offsetConfig.windowModeCode(), "CALENDAR");
  }

  /**
   * 将时间点向下对齐到指定的日历边界。
   *
   * <p>对齐规则:
   *
   * <ul>
   *   <li>HOUR - 对齐到整点(分钟、秒、纳秒归零)
   *   <li>DAY - 对齐到当天零点
   *   <li>WEEK - 对齐到本周周一零点
   *   <li>MONTH - 对齐到本月1日零点
   * </ul>
   *
   * @param instant 原始时间点
   * @param alignTo 对齐单位(hour/day/week/month)
   * @param zone 时区
   * @return 对齐后的时间点
   */
  public static Instant alignFloor(Instant instant, String alignTo, ZoneId zone) {
    // 标准化对齐单位(默认为 HOUR)
    String normalized = StrUtil.blankToDefault(alignTo, "HOUR").trim().toUpperCase(Locale.ROOT);
    LocalDateTime time = LocalDateTime.ofInstant(instant, zone);

    // 根据对齐单位进行向下对齐
    LocalDateTime aligned =
        switch (normalized) {
          case "HOUR", "HOURS" -> time.withMinute(0).withSecond(0).withNano(0);
          case "DAY", "DAYS" -> time.toLocalDate().atStartOfDay();
          case "WEEK", "WEEKS" ->
              time.with(ChronoField.DAY_OF_WEEK, 1).toLocalDate().atStartOfDay();
          case "MONTH", "MONTHS" ->
              time.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay();
          default -> time.withMinute(0).withSecond(0).withNano(0);
        };
    return aligned.atZone(zone).toInstant();
  }

  /** 将数组包装为流,以便统一处理 null。 */
  private static Stream<Instant> stream(Instant... instants) {
    return instants == null ? Stream.empty() : Stream.of(instants);
  }
}
