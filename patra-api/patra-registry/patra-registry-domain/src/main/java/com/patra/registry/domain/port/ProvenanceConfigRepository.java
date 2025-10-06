package com.patra.registry.domain.port;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.domain.model.aggregate.ProvenanceConfiguration;
import com.patra.registry.domain.model.vo.provenance.BatchingConfig;
import com.patra.registry.domain.model.vo.provenance.HttpConfig;
import com.patra.registry.domain.model.vo.provenance.PaginationConfig;
import com.patra.registry.domain.model.vo.provenance.Provenance;
import com.patra.registry.domain.model.vo.provenance.RateLimitConfig;
import com.patra.registry.domain.model.vo.provenance.RetryConfig;
import com.patra.registry.domain.model.vo.provenance.WindowOffsetConfig;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository port for provenance configuration domain objects.
 *
 * <p>This port abstracts persistence concerns for provenance metadata and associated
 * operational configurations (HTTP, retry, rate limit, pagination, batching, window offset).
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ProvenanceConfigRepository {

    /**
     * Finds a provenance by its unique code.
     *
     * @param provenanceCode the provenance code
     * @return optional containing the provenance if found
     */
    Optional<Provenance> findProvenanceByCode(ProvenanceCode provenanceCode);

    /**
     * Retrieves all registered provenances.
     *
     * @return list of all provenances; never null, may be empty
     */
    List<Provenance> findAllProvenances();

    /**
     * Finds the active window offset configuration effective at the given instant.
     *
     * @param provenanceId the provenance identifier
     * @param operationType the operation type; nullable for operation-agnostic queries
     * @param at the effective instant for temporal filtering
     * @return optional containing the configuration if found
     */
    Optional<WindowOffsetConfig> findActiveWindowOffset(Long provenanceId,
                                                        String operationType,
                                                        Instant at);

    /**
     * Finds the active pagination configuration effective at the given instant.
     *
     * @param provenanceId the provenance identifier
     * @param operationType the operation type; nullable for operation-agnostic queries
     * @param at the effective instant for temporal filtering
     * @return optional containing the configuration if found
     */
    Optional<PaginationConfig> findActivePagination(Long provenanceId,
                                                    String operationType,
                                                    Instant at);

    /**
     * Finds the active HTTP configuration effective at the given instant.
     *
     * @param provenanceId the provenance identifier
     * @param operationType the operation type; nullable for operation-agnostic queries
     * @param at the effective instant for temporal filtering
     * @return optional containing the configuration if found
     */
    Optional<HttpConfig> findActiveHttpConfig(Long provenanceId,
                                              String operationType,
                                              Instant at);

    /**
     * Finds the active batching configuration effective at the given instant.
     *
     * @param provenanceId the provenance identifier
     * @param operationType the operation type; nullable for operation-agnostic queries
     * @param at the effective instant for temporal filtering
     * @return optional containing the configuration if found
     */
    Optional<BatchingConfig> findActiveBatching(Long provenanceId,
                                               String operationType,
                                               Instant at);

    /**
     * Finds the active retry configuration effective at the given instant.
     *
     * @param provenanceId the provenance identifier
     * @param operationType the operation type; nullable for operation-agnostic queries
     * @param at the effective instant for temporal filtering
     * @return optional containing the configuration if found
     */
    Optional<RetryConfig> findActiveRetry(Long provenanceId,
                                          String operationType,
                                          Instant at);

    /**
     * Finds the active rate limit configuration effective at the given instant.
     *
     * @param provenanceId the provenance identifier
     * @param operationType the operation type; nullable for operation-agnostic queries
     * @param at the effective instant for temporal filtering
     * @return optional containing the configuration if found
     */
    Optional<RateLimitConfig> findActiveRateLimit(Long provenanceId,
                                                  String operationType,
                                                  Instant at);

    /**
     * Loads the complete provenance configuration aggregate.
     *
     * <p>This method assembles the aggregate by querying all configuration dimensions
     * (provenance, window offset, pagination, HTTP, batching, retry, rate limit) that are
     * effective at the given instant for the specified operation type.
     *
     * @param provenanceId the provenance identifier
     * @param operationType the operation type; nullable for operation-agnostic queries
     * @param at the effective instant for temporal filtering
     * @return optional containing the assembled configuration aggregate if provenance is found
     */
    Optional<ProvenanceConfiguration> loadConfiguration(Long provenanceId,
                                                        String operationType,
                                                        Instant at);
}
