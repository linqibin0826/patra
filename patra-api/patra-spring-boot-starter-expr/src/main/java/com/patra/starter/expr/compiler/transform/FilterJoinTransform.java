package com.patra.starter.expr.compiler.transform;

import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transform that joins multiple filter key:value pairs with a comma separator for MULTI std_keys.
 * Used for providers like Crossref that accept multiple filters in a single parameter.
 *
 * <p>The transform expects a special delimiter-separated value string (internal format from
 * renderer) and converts it to a comma-separated filter list suitable for provider consumption.
 *
 * <p>Example: Internal format "from-pub-date:2022-01-01||until-pub-date:2022-12-31" → Output
 * "from-pub-date:2022-01-01,until-pub-date:2022-12-31"
 *
 * <p>See: docs/expr/03-compiler-bridge-internals.md §3.8 (MULTI Join Strategy),
 * docs/expr/06-provider-crossref.md §6.7, docs/expr/99-appendix-sample-expressions.md §B.4
 *
 * @since 1.0.0
 */
public class FilterJoinTransform implements ValueTransform {

  private static final Logger log = LoggerFactory.getLogger(FilterJoinTransform.class);
  private static final String CODE = "FILTER_JOIN";
  private static final String INTERNAL_DELIMITER = "||";
  private static final String OUTPUT_SEPARATOR = ",";

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public String apply(String stdKey, String value, ProvenanceSnapshot snapshot) {
    if (value == null || value.isBlank()) {
      log.debug("FILTER_JOIN: stdKey={}, value is null or blank, returning as-is", stdKey);
      return value;
    }

    // Split by internal delimiter and join with output separator
    // Each segment is expected to be a filter key:value pair
    String result =
        Arrays.stream(value.split(Pattern.quote(INTERNAL_DELIMITER)))
            .filter(s -> !s.isBlank())
            .collect(Collectors.joining(OUTPUT_SEPARATOR));

    log.debug(
        "FILTER_JOIN: stdKey={}, input length={}, output length={}, segment count={}",
        stdKey,
        value.length(),
        result.length(),
        value.split(Pattern.quote(INTERNAL_DELIMITER)).length);
    return result;
  }
}
