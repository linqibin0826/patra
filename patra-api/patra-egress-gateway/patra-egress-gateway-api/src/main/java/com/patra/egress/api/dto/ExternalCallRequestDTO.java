package com.patra.egress.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * External call request DTO
 *
 * <p>Represents a request to call an external service through the egress gateway. The gateway will
 * apply resilience capabilities (rate limiting, retries, circuit breaking) and return a
 * standardized response envelope.
 *
 * @param url target URL (required, must not be blank)
 * @param method HTTP method (required, e.g., GET, POST, PUT, DELETE)
 * @param headers HTTP request headers (optional, will be passed through to external service)
 * @param body HTTP request body (optional, for POST/PUT requests)
 * @param config optional resilience configuration overrides (will be capped at system maximum)
 * @author linqibin
 * @since 0.1.0
 */
public record ExternalCallRequestDTO(
    @NotBlank(message = "URL cannot be blank") String url,
    @NotBlank(message = "HTTP method cannot be blank") String method,
    Map<String, String> headers,
    String body,
    @Valid ResilienceConfigDTO config) {
  /** Compact constructor for immutability. */
  public ExternalCallRequestDTO {
    // Create an immutable defensive copy of the headers map.
    headers = headers != null ? Map.copyOf(headers) : null;
  }
}
