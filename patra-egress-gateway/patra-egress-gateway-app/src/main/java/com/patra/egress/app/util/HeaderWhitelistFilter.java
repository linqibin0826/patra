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
     * Filter response headers by whitelist (case-insensitive)
     * Converts multi-value headers to single-value by taking the first value
     *
     * @param headers response headers (multi-value)
     * @param whitelist header names whitelist
     * @return filtered headers (single-value)
     */
    public static Map<String, String> filter(Map<String, List<String>> headers, List<String> whitelist) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        if (whitelist == null || whitelist.isEmpty()) {
            return Map.of();
        }

        // Convert whitelist to lowercase for case-insensitive comparison
        List<String> lowerCaseWhitelist = whitelist.stream()
                .map(String::toLowerCase)
                .toList();

        return headers.entrySet().stream()
                .filter(entry -> lowerCaseWhitelist.contains(entry.getKey().toLowerCase()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            List<String> values = entry.getValue();
                            return values != null && !values.isEmpty() ? values.get(0) : "";
                        }
                ));
    }
}
