package com.patra.starter.expr.compiler.transform;

import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transform that subtracts one day from a date value to convert an exclusive upper bound to an
 * inclusive provider bound.
 *
 * <p>Example: PubMed's {@code maxdate} is inclusive, but the std_key {@code to} is exclusive. This
 * transform converts "2023-12-31" (exclusive) to "2023-12-30" (inclusive).
 *
 * <p>Operates at DATE granularity only (YYYY-MM-DD format).
 *
 * <p>See: docs/expr/03-compiler-bridge-internals.md §3.3.2, docs/expr/04-provider-pubmed.md §4.2
 *
 * @since 1.0.0
 */
public class ToExclusiveMinus1DTransform implements ValueTransform {

  private static final Logger log = LoggerFactory.getLogger(ToExclusiveMinus1DTransform.class);
  private static final String CODE = "TO_EXCLUSIVE_MINUS_1D";
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public String apply(String stdKey, String value, ProvenanceSnapshot snapshot) {
    if (value == null || value.isBlank()) {
      log.warn("TO_EXCLUSIVE_MINUS_1D: received null or blank value for stdKey={}", stdKey);
      return value;
    }

    try {
      LocalDate date = LocalDate.parse(value, DATE_FORMATTER);
      LocalDate adjusted = date.minusDays(1);
      String result = adjusted.format(DATE_FORMATTER);

      log.debug(
          "TO_EXCLUSIVE_MINUS_1D: stdKey={}, original={}, adjusted={}", stdKey, value, result);
      return result;

    } catch (DateTimeParseException e) {
      log.error(
          "TO_EXCLUSIVE_MINUS_1D: failed to parse date value '{}' for stdKey={}. "
              + "Expected ISO_LOCAL_DATE format (YYYY-MM-DD). Returning original value.",
          value,
          stdKey,
          e);
      return value;
    }
  }
}
