package com.patra.egress.domain.model.vo;

import java.util.List;
import java.util.Map;

/**
 * Immutable HTTP response value object.
 *
 * @param statusCode HTTP status code returned by the provider
 * @param headers    response headers captured from the provider
 * @param body       response payload serialized as text
 * @author linqibin
 * @since 0.1.0
 */
public record HttpResponse(
    int statusCode,
    Map<String, List<String>> headers,
    String body
) {
    /**
     * Canonical constructor that ensures the response headers cannot be mutated.
     */
    public HttpResponse {
        // Create an immutable defensive copy to keep downstream consumers safe.
        headers = headers != null ? Map.copyOf(headers) : Map.of();
    }
    
    /**
     * Determine whether the response is successful (2xx status codes).
     *
     * @return {@code true} if the status code is in the 2xx range; {@code false} otherwise
     */
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }
}
