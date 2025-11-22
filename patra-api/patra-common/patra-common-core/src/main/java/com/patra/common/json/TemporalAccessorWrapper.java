package com.patra.common.json;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;

/// TemporalAccessor 包装器，提供带回退逻辑的 Instant 转换功能。
///
/// Java 的 {@link java.time.temporal.TemporalAccessor} 是一个通用接口，可以表示各种日期时间类型 （如
/// Instant、LocalDateTime、LocalDate 等）。此类提供统一的方式将这些类型转换为 Instant， 通过检测支持的字段并应用合适的转换策略。
///
/// **转换策略优先级**:
///
/// @author linqibin
/// @since 0.1.0
final class TemporalAccessorWrapper {
  private final TemporalAccessor accessor;

  TemporalAccessorWrapper(TemporalAccessor accessor) {
    this.accessor = accessor;
  }

  /// 将 TemporalAccessor 转换为 Instant，使用默认时区处理无时区信息的情况。
  ///
  /// @param defaultZone 默认时区，用于 LocalDateTime 和 LocalDate 的转换
  /// @return Instant 实例
  /// @throws JsonNormalizationException 如果 accessor 不支持任何已知的时间字段
  Instant toInstant(ZoneId defaultZone) {
    if (accessor.isSupported(ChronoField.INSTANT_SECONDS)) {
      return Instant.from(accessor);
    }
    if (accessor.isSupported(ChronoField.OFFSET_SECONDS)) {
      return OffsetDateTime.from(accessor).toInstant();
    }
    if (accessor.isSupported(ChronoField.HOUR_OF_DAY)) {
      LocalDateTime dateTime = LocalDateTime.from(accessor);
      return dateTime.atZone(defaultZone).toInstant();
    }
    if (accessor.isSupported(ChronoField.DAY_OF_MONTH)) {
      LocalDate date = LocalDate.from(accessor);
      return date.atStartOfDay(defaultZone).toInstant();
    }
    throw new JsonNormalizationException("Unsupported temporal accessor: " + accessor);
  }
}
