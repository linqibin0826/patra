/**
 * REST API endpoints for provenance registry management.
 *
 * <p>This package contains driving adapters that receive HTTP requests and translate them into
 * application use case calls. All classes here are part of the Hexagonal Architecture's adapter
 * layer (External → System direction).
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Implement OpenAPI endpoint contracts defined in {@code patra-registry-api}
 *   <li>Validate request DTOs using {@code @Valid} annotations
 *   <li>Delegate to application orchestrators
 *   <li>Convert domain results to API response DTOs
 *   <li>Map domain exceptions to HTTP ProblemDetail responses
 * </ul>
 *
 * <h2>API Endpoints</h2>
 *
 * <ul>
 *   <li>{@code ProvenanceEndpointImpl} - Provenance configuration CRUD operations
 *   <li>{@code ExprEndpointImpl} - Expression compilation and validation
 * </ul>
 *
 * <h2>Naming Convention</h2>
 *
 * <ul>
 *   <li>Endpoint implementations: {@code *EndpointImpl}
 *   <li>API converters: {@code *ApiConverter}
 * </ul>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * @RestController
 * @RequiredArgsConstructor
 * public class ProvenanceEndpointImpl implements ProvenanceEndpoint {
 *     private final ProvenanceOrchestrator orchestrator;
 *     private final ProvenanceApiConverter converter;
 *
 *     @Override
 *     public ProvenanceResponse createProvenance(@Valid @RequestBody CreateProvenanceRequest request) {
 *         var command = converter.toCommand(request);
 *         var result = orchestrator.createProvenance(command);
 *         return converter.toResponse(result);
 *     }
 * }
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.registry.adapter.rest;
