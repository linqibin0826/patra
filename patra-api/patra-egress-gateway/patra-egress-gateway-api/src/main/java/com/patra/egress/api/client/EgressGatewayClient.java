package com.patra.egress.api.client;

import com.patra.egress.api.endpoint.EgressEndpoint;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * Feign client definition for invoking egress gateway internal API.
 *
 * <p>Extends {@link EgressEndpoint} to provide type-safe RPC integration via
 * Spring Cloud OpenFeign.</p>
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
@FeignClient(
        name = "patra-egress-gateway",
        contextId = "egressGatewayClient"
)
public interface EgressGatewayClient extends EgressEndpoint {
}
