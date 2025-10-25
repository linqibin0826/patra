package com.patra.common.json;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Helper for coercing temporal values to canonical ISO-8601 format. */
final class TemporalCoercion {
  private static final DateTimeFormatter CANONICAL_INSTANT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
          .withZone(java.time.ZoneOffset.UTC);

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

  private static Instant epochToInstant(long epoch) {
    if (epoch > 3_000_000_000_000L || epoch < -3_000_000_000_000L) {
      throw new JsonNormalizationException("Epoch value out of range: " + epoch);
    }
    if (Math.abs(epoch) >= 1_000_000_000_000L) {
      return Instant.ofEpochMilli(epoch);
    }
    return Instant.ofEpochSecond(epoch);
  }

  private static String formatInstant(Instant instant) {
    Instant truncated = instant.truncatedTo(ChronoUnit.MILLIS);
    return CANONICAL_INSTANT.format(truncated);
  }
}
