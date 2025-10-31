/**
 * Internal REST API endpoints for microservice-to-microservice communication.
 *
 * <p>This package contains driving adapters that receive HTTP requests from other internal
 * microservices (via Feign clients) and translate them into application use case calls. All classes
 * here are part of the Hexagonal Architecture's adapter layer (External → System direction).
 *
 * <h2>API Audience</h2>
 *
 * These endpoints are designed for <strong>internal microservice communication</strong>:
 *
 * <ul>
 *   <li>Consumed by Feign clients in other microservices (e.g., {@code
 *       patra-ingest-infra/integration/storage/})
 *   <li>NOT intended for external public access
 *   <li>May have relaxed security constraints compared to public APIs
 *   <li>Optimized for high-throughput internal operations
 * </ul>
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Implement OpenAPI endpoint contracts defined in {@code patra-storage-api}
 *   <li>Validate request DTOs using {@code @Valid} annotations
 *   <li>Delegate to {@code RecordUploadOrchestrator} and other orchestrators
 *   <li>Convert domain results to API response DTOs
 *   <li>Map domain exceptions to HTTP ProblemDetail responses
 * </ul>
 *
 * <h2>Security Considerations</h2>
 *
 * <ul>
 *   <li>Internal APIs should be protected by network-level security (e.g., service mesh, VPC)
 *   <li>Authentication may use service-to-service tokens (e.g., JWT with service identity)
 *   <li>Consider rate limiting to protect against misconfigured consumers
 * </ul>
 *
 * <h2>Future Extension</h2>
 *
 * If public-facing APIs are needed, create a separate {@code adapter.rest.public} package with
 * appropriate security hardening.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * @RestController
 * @RequiredArgsConstructor
 * public class StorageEndpointImpl implements StorageEndpoint {
 *     private final RecordUploadOrchestrator orchestrator;
 *
 *     @Override
 *     public RecordUploadResponse recordUpload(@Valid @RequestBody UploadRecordRequest request) {
 *         var command = buildCommand(request);
 *         var result = orchestrator.execute(command);
 *         return new RecordUploadResponse(result.metadataId(), result.recordedAt());
 *     }
 * }
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.storage.adapter.rest.internal;
