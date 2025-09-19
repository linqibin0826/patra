package com.patra.registry.api.rpc.client;

import com.patra.registry.api.rpc.endpoint.DictionaryEndpoint;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * Feign client for dictionary service integration.
 * Subsystems inject this client to access dictionary capabilities via service discovery.
 * Inherits all methods from DictionaryHttpApi for consistent API access across microservices.
 * 
 * <p>This client enables subsystems to:</p>
 * <ul>
 *   <li>Query dictionary items by type and code for validation and display</li>
 *   <li>Retrieve enabled items for dropdown population</li>
 *   <li>Get default items for fallback values</li>
 *   <li>Validate dictionary references in batch operations</li>
 *   <li>Resolve external system aliases to internal codes</li>
 *   <li>Monitor dictionary system health</li>
 * </ul>
 * 
 * <p>Configuration:</p>
 * <ul>
 *   <li>Service name: "patra-registry" - matches the registry service name in service discovery</li>
 *   <li>Context ID: "dictionaryClient" - unique identifier for this Feign client bean</li>
 *   <li>Load balancing and circuit breaker capabilities provided by Spring Cloud</li>
 * </ul>
 * 
 * <p>Usage example in subsystem:</p>
 * <pre>{@code
 * @Component
 * public class MySubsystemService {
 *     private final DictionaryClient dictionaryClient;
 *     
 *     public void validateEndpointConfig(EndpointConfig config) {
 *         List<DictionaryReference> refs = List.of(
 *             new DictionaryReference("http_method", config.getMethodCode()),
 *             new DictionaryReference("endpoint_type", config.getTypeCode())
 *         );
 *         List<DictionaryValidationQuery> results = dictionaryClient.validateReferences(refs);
 *         // Process validation results...
 *     }
 * }
 * }</pre>
 * 
 * @author linqibin
 * @since 0.1.0
 */
@FeignClient(
    name = "patra-registry",
    contextId = "dictionaryClient"
)
public interface DictionaryClient extends DictionaryEndpoint {
    // Inherits all methods from DictionaryHttpApi
    // Provides service discovery and load balancing through Spring Cloud
    // No additional methods needed - pure delegation to the HTTP API contract
}
