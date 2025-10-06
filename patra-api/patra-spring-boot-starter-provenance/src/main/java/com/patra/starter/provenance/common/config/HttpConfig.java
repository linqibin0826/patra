package com.patra.starter.provenance.common.config;

import java.util.Map;

/**
 * HTTP configuration for provenance API calls.
 *
 * <p>Field descriptions:
 * @param defaultHeaders immutable HTTP headers appended to every request
 * @param timeoutConnectMillis connection establishment timeout in milliseconds
 * @param timeoutReadMillis socket read timeout in milliseconds
 * @param timeoutTotalMillis overall request timeout in milliseconds
 *
 * @author linqibin
 * @since 0.1.0
 */
public record HttpConfig(
    Map<String, String> defaultHeaders,
    Integer timeoutConnectMillis,
    Integer timeoutReadMillis,
    Integer timeoutTotalMillis
) {
    public HttpConfig {
        defaultHeaders = defaultHeaders == null ? Map.of() : Map.copyOf(defaultHeaders);
    }
}
