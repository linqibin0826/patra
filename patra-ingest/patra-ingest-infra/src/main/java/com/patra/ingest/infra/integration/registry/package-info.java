/**
 * patra-registry service integration adapters.
 *
 * <p>This package contains adapters for integrating with the patra-registry microservice, which
 * serves as the Single Source of Truth (SSOT) for provenance configurations, dictionaries, and
 * metadata.
 *
 * <p>Key components:
 *
 * <ul>
 *   <li>{@link com.patra.ingest.infra.integration.registry.PatraRegistryAdapter} - Implements
 *       {@link com.patra.ingest.domain.port.PatraRegistryPort} for fetching provenance
 *       configurations
 *   <li>{@code converter/} - Anti-Corruption Layer (ACL) for translating registry DTOs to domain
 *       snapshots
 * </ul>
 *
 * @since 0.1.0
 */
package com.patra.ingest.infra.integration.registry;
