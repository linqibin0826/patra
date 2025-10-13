package com.patra.egress.api.endpoint;

import com.patra.egress.api.dto.ExternalCallRequestDTO;
import com.patra.egress.api.dto.ExternalCallResponseDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Internal API contract for egress gateway external call.
 *
 * <p>Exposes endpoints for executing external HTTP calls with resilience patterns (rate limiting,
 * circuit breaking, retries) to internal microservices via Feign client integration.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface EgressEndpoint {

  String BASE_PATH = "/api/egress";

  /**
   * Execute an external HTTP call through the egress gateway.
   *
   * <p>This method delegates the external HTTP call to the egress gateway, which applies resilience
   * patterns and returns a standardized response envelope with metadata.
   *
   * @param request the external call request containing URL, method, headers, body, and optional
   *     resilience config
   * @return the complete response including envelope, duration, retry count, and trace ID
   */
  @PostMapping(BASE_PATH + "/call")
  ExternalCallResponseDTO call(@Valid @RequestBody ExternalCallRequestDTO request);
}
