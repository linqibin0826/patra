package com.patra.common.json;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;

/** Wraps a TemporalAccessor and provides conversion to Instant with fallback logic. */
final class TemporalAccessorWrapper {
  private final TemporalAccessor accessor;

  TemporalAccessorWrapper(TemporalAccessor accessor) {
    this.accessor = accessor;
  }

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
