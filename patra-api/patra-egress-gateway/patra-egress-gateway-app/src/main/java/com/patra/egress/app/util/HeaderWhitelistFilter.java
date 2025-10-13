package com.patra.egress.app.util;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for filtering response headers by whitelist
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class HeaderWhitelistFilter {

  private HeaderWhitelistFilter() {
    // Utility class, prevent instantiation
  }

  /**
   * Filter response headers by whitelist (case-insensitive) Converts multi-value headers to
   * single-value by taking the first value Preserves the original casing from the whitelist for
   * matched headers
   *
   * @param headers response headers (multi-value)
   * @param whitelist header names whitelist
   * @return filtered headers (single-value) with casing from whitelist
   */
  public static Map<String, String> filter(
      Map<String, List<String>> headers, List<String> whitelist) {
    if (headers == null || headers.isEmpty()) {
      return Map.of();
    }
    if (whitelist == null || whitelist.isEmpty()) {
      return Map.of();
    }

    // Create a map from lowercase header names to original whitelist casing
    Map<String, String> lowerToOriginalCase =
        whitelist.stream()
            .collect(
                Collectors.toMap(
                    String::toLowerCase,
                    name -> name,
                    (existing, replacement) -> existing // Keep first if duplicates
                    ));

    return headers.entrySet().stream()
        .filter(entry -> lowerToOriginalCase.containsKey(entry.getKey().toLowerCase()))
        .collect(
            Collectors.toMap(
                entry ->
                    lowerToOriginalCase.get(entry.getKey().toLowerCase()), // Use whitelist casing
                entry -> {
                  List<String> values = entry.getValue();
                  return values != null && !values.isEmpty() ? values.get(0) : "";
                }));
  }
}
