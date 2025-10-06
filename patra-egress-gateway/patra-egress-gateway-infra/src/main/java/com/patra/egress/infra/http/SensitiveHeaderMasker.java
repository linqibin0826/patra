package com.patra.egress.infra.http;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for masking sensitive headers in logs
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class SensitiveHeaderMasker {

    private static final String MASK = "***";

    /**
     * Sensitive header names (case-insensitive)
     */
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization",
            "api-key",
            "x-api-key",
            "cookie",
            "set-cookie",
            "proxy-authorization",
            "x-auth-token",
            "x-csrf-token"
    );

    private SensitiveHeaderMasker() {
        // Utility class, prevent instantiation
    }

    /**
     * Mask sensitive headers in the map
     *
     * @param headers header map
     * @return new map with sensitive headers masked
     */
    public static Map<String, String> mask(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }

        return headers.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> isSensitive(entry.getKey()) ? MASK : entry.getValue()
                ));
    }

    /**
     * Check if header name is sensitive (case-insensitive)
     *
     * @param headerName header name
     * @return true if sensitive
     */
    private static boolean isSensitive(String headerName) {
        if (headerName == null) {
            return false;
        }
        return SENSITIVE_HEADERS.contains(headerName.toLowerCase());
    }
}
