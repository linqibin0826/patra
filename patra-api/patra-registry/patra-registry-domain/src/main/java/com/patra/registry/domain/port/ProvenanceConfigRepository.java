package com.patra.registry.domain.port;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.domain.model.aggregate.ProvenanceConfiguration;
import com.patra.registry.domain.model.vo.provenance.BatchingConfig;
import com.patra.registry.domain.model.vo.provenance.Credential;
import com.patra.registry.domain.model.vo.provenance.EndpointDefinition;
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
 * Provenance 配置仓储端口。
 */
public interface ProvenanceConfigRepository {

    Optional<Provenance> findProvenanceByCode(ProvenanceCode provenanceCode);

    List<Provenance> findAllProvenances();

    Optional<EndpointDefinition> findActiveEndpoint(Long provenanceId,
                                                    String taskType,
                                                    String endpointName,
                                                    Instant at);

    Optional<WindowOffsetConfig> findActiveWindowOffset(Long provenanceId,
                                                        String taskType,
                                                        Instant at);

    Optional<PaginationConfig> findActivePagination(Long provenanceId,
                                                    String taskType,
                                                    Instant at);

    Optional<HttpConfig> findActiveHttpConfig(Long provenanceId,
                                              String taskType,
                                              Instant at);

    Optional<BatchingConfig> findActiveBatching(Long provenanceId,
                                               String taskType,
                                               Long endpointId,
                                               String credentialName,
                                               Instant at);

    Optional<RetryConfig> findActiveRetry(Long provenanceId,
                                          String taskType,
                                          Instant at);

    Optional<RateLimitConfig> findActiveRateLimit(Long provenanceId,
                                                  String taskType,
                                                  Long endpointId,
                                                  String credentialName,
                                                  Instant at);

    List<Credential> findActiveCredentials(Long provenanceId,
                                           String taskType,
                                           Long endpointId,
                                           Instant at);

    Optional<ProvenanceConfiguration> loadConfiguration(Long provenanceId,
                                                        String taskType,
                                                        String endpointName,
                                                        Instant at);
}
