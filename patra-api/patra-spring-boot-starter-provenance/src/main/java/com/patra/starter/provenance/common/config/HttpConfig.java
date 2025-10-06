package com.patra.starter.provenance.common.config;

import java.util.Map;

/**
 * HTTP configuration
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
}
