package com.patra.common.json;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 时间值强制转换为规范 ISO-8601 格式的辅助类。
 *
 * <p>在 JSON 规范化过程中，将各种格式的日期时间值统一转换为标准的 ISO-8601 格式： {@code yyyy-MM-dd'T'HH:mm:ss.SSS'Z'}（UTC 时区）。
 *
 * <p><b>支持的输入格式</b>:
 *
 * <ul>
 *   <li>Unix 时间戳（秒或毫秒）
 *   <li>ISO-8601 标准格式（如 2023-12-31T23:59:59.123Z）
 *   <li>本地日期时间（如 2023-12-31 23:59:59）
 *   <li>仅日期（如 2023-12-31）
 *   <li>紧凑格式（如 20231231235959）
 * </ul>
 *
 * <p><b>输出格式</b>: 统一输出为 UTC 时区的 ISO-8601 格式，精度为毫秒。
 *
 * @author Patra Team
 * @since 0.1.0
 */
final class TemporalCoercion {
  private static final DateTimeFormatter CANONICAL_INSTANT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  private static final List<DateTimeFormatter> TEMPORAL_FORMATTERS =
      List.of(
          DateTimeFormatter.ISO_INSTANT,
          DateTimeFormatter.ISO_OFFSET_DATE_TIME,
          DateTimeFormatter.ISO_ZONED_DATE_TIME,
          new DateTimeFormatterBuilder()
              .appendPattern("yyyy-MM-dd['T'][' ']HH:mm:ss[.SSS][XXX][XX][X]")
              .toFormatter(),
          new DateTimeFormatterBuilder()
              .appendPattern("yyyy/MM/dd['T'][' ']HH:mm:ss[.SSS][XXX][XX][X]")
              .toFormatter(),
          DateTimeFormatter.ISO_LOCAL_DATE_TIME,
          DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withLocale(Locale.ROOT));

  private static final List<DateTimeFormatter> DATE_ONLY_FORMATTERS =
      List.of(
          DateTimeFormatter.ISO_LOCAL_DATE,
          DateTimeFormatter.ofPattern("yyyy/MM/dd").withLocale(Locale.ROOT),
          DateTimeFormatter.ofPattern("yyyyMMdd").withLocale(Locale.ROOT));

  private TemporalCoercion() {}

  /**
   * 尝试将字符串强制转换为规范的 ISO-8601 格式。
   *
   * @param value 输入字符串（可能是时间戳、日期字符串等）
   * @param defaultZoneId 默认时区，用于处理不带时区信息的日期时间
   * @return 转换后的 ISO-8601 字符串，如果无法转换则返回 {@code Optional.empty()}
   */
  static Optional<String> coerceString(String value, ZoneId defaultZoneId) {
    if (value.isBlank()) {
      return Optional.empty();
    }
    if (value.matches("^-?\\d+$")) {
      try {
        long epoch = Long.parseLong(value);
        Instant instant = epochToInstant(epoch);
        return Optional.of(formatInstant(instant));
      } catch (NumberFormatException ex) {
        return Optional.empty();
      }
    }
    for (DateTimeFormatter formatter : TEMPORAL_FORMATTERS) {
      try {
        TemporalAccessorWrapper accessor = new TemporalAccessorWrapper(formatter.parse(value));
        Instant instant = accessor.toInstant(defaultZoneId);
        return Optional.of(formatInstant(instant));
      } catch (DateTimeParseException ignored) {
        // continue trying other formats
      }
    }
    for (DateTimeFormatter formatter : DATE_ONLY_FORMATTERS) {
      try {
        LocalDate date = LocalDate.parse(value, formatter);
        Instant instant = date.atStartOfDay(defaultZoneId).toInstant();
        return Optional.of(formatInstant(instant));
      } catch (DateTimeParseException ignored) {
        // continue
      }
    }
    return Optional.empty();
  }

  /**
   * 尝试将 BigDecimal 强制转换为规范的 ISO-8601 格式（假设它表示 Unix 时间戳）。
   *
   * @param decimal BigDecimal 数值
   * @return 转换后的 ISO-8601 字符串，如果无法转换则返回 {@code Optional.empty()}
   */
  static Optional<String> coerceBigDecimal(BigDecimal decimal) {
    if (decimal.scale() > 0) {
      return Optional.empty();
    }
    try {
      long epoch = decimal.longValueExact();
      Instant instant = epochToInstant(epoch);
      return Optional.of(formatInstant(instant));
    } catch (ArithmeticException ex) {
      return Optional.empty();
    }
  }

  /**
   * 将 Unix 时间戳转换为 Instant，自动判断是秒还是毫秒。
   *
   * @param epoch Unix 时间戳（秒或毫秒）
   * @return Instant 实例
   * @throws JsonNormalizationException 如果时间戳超出合理范围
   */
  private static Instant epochToInstant(long epoch) {
    if (epoch > 3_000_000_000_000L || epoch < -3_000_000_000_000L) {
      throw new JsonNormalizationException("Epoch value out of range: " + epoch);
    }
    if (Math.abs(epoch) >= 1_000_000_000_000L) {
      return Instant.ofEpochMilli(epoch);
    }
    return Instant.ofEpochSecond(epoch);
  }

  /**
   * 将 Instant 格式化为规范的 ISO-8601 字符串（精度截断到毫秒）。
   *
   * @param instant Instant 实例
   * @return ISO-8601 格式字符串
   */
  private static String formatInstant(Instant instant) {
    Instant truncated = instant.truncatedTo(ChronoUnit.MILLIS);
    return CANONICAL_INSTANT.format(truncated);
  }
}
