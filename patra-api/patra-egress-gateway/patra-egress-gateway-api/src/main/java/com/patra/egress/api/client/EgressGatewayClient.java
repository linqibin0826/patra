package com.patra.egress.api.client;

import com.patra.egress.api.dto.ExternalCallRequestDTO;
import com.patra.egress.api.dto.ExternalCallResponseDTO;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Egress Gateway Feign Client
 *
 * <p>Feign client interface for invoking the patra-egress-gateway service.
 * This client provides a type-safe way for business services to make external HTTP calls
 * through the egress gateway with built-in resilience (rate limiting, circuit breaking, retries).</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * @Autowired
 * private EgressGatewayClient egressGatewayClient;
 *
 * ExternalCallRequestDTO request = new ExternalCallRequestDTO(
 *     "https://api.example.com/data",
 *     "GET",
 *     Map.of("Accept", "application/json"),
 *     null,
 *     null // Use default resilience config
 * );
 *
 * ExternalCallResponseDTO response = egressGatewayClient.call(request);
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
@FeignClient(name = "patra-egress-gateway", path = "/api/egress")
public interface EgressGatewayClient {

    /**
     * Execute an external HTTP call through the egress gateway
     *
     * <p>This method delegates the external HTTP call to the egress gateway,
     * which applies resilience patterns (rate limiting, circuit breaking, retries)
     * and returns a standardized response envelope with metadata.</p>
     *
     * @param request the external call request containing URL, method, headers, body, and optional resilience config
     * @return the complete response including envelope, duration, retry count, and trace ID
     * @throws com.patra.egress.api.error.RateLimitExceededException if rate limit is exceeded
     * @throws com.patra.egress.api.error.CircuitBreakerOpenException if circuit breaker is open
     * @throws com.patra.egress.api.error.ExternalCallTimeoutException if the call times out
     * @throws com.patra.egress.api.error.ConfigValidationException if the request config is invalid
     */
    @PostMapping("/call")
    ExternalCallResponseDTO call(@Valid @RequestBody ExternalCallRequestDTO request);
}
