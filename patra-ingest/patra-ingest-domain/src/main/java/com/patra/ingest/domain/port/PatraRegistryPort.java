package com.patra.ingest.domain.port;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;

/**
 * Domain port used to access Patra Registry.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Provide a single entry point for application services to call the registry.</li>
 *   <li>Fetch provenance configuration snapshots per provenance/operation combination.</li>
 *   <li>Hide protocol/client details (Feign/HTTP/gRPC) so the domain layer remains decoupled.</li>
 * </ul>
 *
 * <p>Layering: the interface lives in the domain layer; infrastructure implementations reside under
 * {@code infra.rpc.registry}. Application services orchestrate through this port only.</p>
 *
 * <p>Error semantics (guidance):
 * <ul>
 *   <li>Non-recoverable issues such as 4xx or missing data should be converted to domain/application exceptions
 *   (e.g., {@code IngestConfigurationException}).</li>
 *   <li>Recoverable issues such as 5xx or timeouts may retry/degrade (return a minimal snapshot) or surface to the
 *   caller for higher-level handling.</li>
 *   <li>Implementations should log trace ids and remote error codes for troubleshooting; the interface leaves
 *   specific exception types to the caller.</li>
 * </ul>
 *
 * <p>Thread safety: implementations must be stateless or safe for concurrent reuse; configuration dependencies are
 * managed by Spring.</p>
 */
public interface PatraRegistryPort {

    /**
     * Retrieve the configuration snapshot for a provenance/operation pair.
     *
     * <p>The snapshot should include static parameters required by registry integrations (windowing, pagination,
     * HTTP retry, rate limiting, and so on). Implementations may return a minimal snapshot when the registry is
     * temporarily unavailable but must log the degradation.</p>
     *
     * @param provenanceCode provenance identifier (e.g., PUBMED/EPMC)
     * @param operationCode  operation type (e.g., HARVEST/BACKFILL/UPDATE)
     * @return registry configuration snapshot (never {@code null}; fallback snapshots allowed with reduced scope)
     * @throws RuntimeException when unrecoverable configuration issues occur
     */
    ProvenanceConfigSnapshot fetchConfig(ProvenanceCode provenanceCode,
                                         OperationCode operationCode);
}
