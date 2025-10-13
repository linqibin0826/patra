package com.patra.egress.domain.model.vo;

import java.util.Map;

/**
 * Immutable HTTP request value object.
 *
 * @param url target URL of the outbound call
 * @param method HTTP method to execute
 * @param headers request headers sent to the provider
 * @param body request payload serialized as text
 * @author linqibin
 * @since 0.1.0
 */
public record HttpRequest(String url, HttpMethod method, Map<String, String> headers, String body) {
  /** Canonical constructor that enforces immutability and validates input. */
  public HttpRequest {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("URL cannot be null or blank");
    }
    if (method == null) {
      throw new IllegalArgumentException("HTTP method cannot be null");
    }
    // Create an immutable defensive copy to avoid accidental mutation downstream.
    headers = headers != null ? Map.copyOf(headers) : Map.of();
  }
}
