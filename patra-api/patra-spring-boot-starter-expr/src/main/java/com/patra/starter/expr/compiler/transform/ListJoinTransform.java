package com.patra.starter.expr.compiler.transform;

import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transform that joins multiple values with a comma separator for MULTI std_keys. Used when MULTI
 * std_keys need to be collapsed into a single provider parameter value.
 *
 * <p>The transform expects a special delimiter-separated value string (internal format from
 * renderer) and converts it to a comma-separated list suitable for provider consumption.
 *
 * <p>Example: Internal format "value1||value2||value3" → Output "value1,value2,value3"
 *
 * <p>See: docs/expr/03-compiler-bridge-internals.md §3.8 (MULTI Join Strategy)
 *
 * @since 1.0.0
 */
public class ListJoinTransform implements ValueTransform {

  private static final Logger log = LoggerFactory.getLogger(ListJoinTransform.class);
  private static final String CODE = "LIST_JOIN";
  private static final String INTERNAL_DELIMITER = "||";
  private static final String OUTPUT_SEPARATOR = ",";

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public String apply(String stdKey, String value, ProvenanceSnapshot snapshot) {
    if (value == null || value.isBlank()) {
      log.debug("LIST_JOIN: stdKey={}, value is null or blank, returning as-is", stdKey);
      return value;
    }

    // Split by internal delimiter and join with output separator
    String result =
        Arrays.stream(value.split(INTERNAL_DELIMITER))
            .filter(s -> !s.isBlank())
            .collect(Collectors.joining(OUTPUT_SEPARATOR));

    log.debug(
        "LIST_JOIN: stdKey={}, input length={}, output length={}",
        stdKey,
        value.length(),
        result.length());
    return result;
  }
}
