package com.patra.registry.infra.persistence.repository;

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
import com.patra.registry.domain.port.ProvenanceConfigRepository;
import com.patra.registry.infra.mapstruct.ProvenanceEntityConverter;
import com.patra.registry.infra.persistence.entity.provenance.RegProvenanceDO;
import com.patra.registry.infra.persistence.mapper.provenance.RegProvBatchingCfgMapper;
import com.patra.registry.infra.persistence.mapper.provenance.RegProvCredentialMapper;
import com.patra.registry.infra.persistence.mapper.provenance.RegProvEndpointDefMapper;
import com.patra.registry.infra.persistence.mapper.provenance.RegProvHttpCfgMapper;
import com.patra.registry.infra.persistence.mapper.provenance.RegProvPaginationCfgMapper;
import com.patra.registry.infra.persistence.mapper.provenance.RegProvRateLimitCfgMapper;
import com.patra.registry.infra.persistence.mapper.provenance.RegProvRetryCfgMapper;
import com.patra.registry.infra.persistence.mapper.provenance.RegProvWindowOffsetCfgMapper;
import com.patra.registry.infra.persistence.mapper.provenance.RegProvenanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Provenance 配置仓储 MyBatis 实现。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ProvenanceConfigRepositoryMpImpl implements ProvenanceConfigRepository {

    private final RegProvenanceMapper provenanceMapper;
    private final RegProvEndpointDefMapper endpointDefMapper;
    private final RegProvWindowOffsetCfgMapper windowOffsetCfgMapper;
    private final RegProvPaginationCfgMapper paginationCfgMapper;
    private final RegProvHttpCfgMapper httpCfgMapper;
    private final RegProvBatchingCfgMapper batchingCfgMapper;
    private final RegProvRetryCfgMapper retryCfgMapper;
    private final RegProvRateLimitCfgMapper rateLimitCfgMapper;
    private final RegProvCredentialMapper credentialMapper;
    private final ProvenanceEntityConverter converter;

    @Override
    public Optional<Provenance> findProvenanceByCode(String provenanceCode) {
        return provenanceMapper.selectByCode(provenanceCode)
                .map(converter::toDomain);
    }

    @Override
    public List<Provenance> findAllProvenances() {
        List<RegProvenanceDO> entities = provenanceMapper.selectAllActive();
        return entities.stream().map(converter::toDomain).toList();
    }

    @Override
    public Optional<EndpointDefinition> findActiveEndpoint(Long provenanceId,
                                                           String taskType,
                                                           String endpointName,
                                                           Instant at) {
        if (endpointName == null || endpointName.isBlank()) {
            return Optional.empty();
        }
        Instant timestamp = at != null ? at : Instant.now();
        String taskKey = normalizeTaskKey(taskType);

        if (taskType != null) {
            Optional<EndpointDefinition> taskLevel = endpointDefMapper.selectActive(
                            provenanceId, "TASK", taskKey, endpointName.trim(), timestamp)
                    .map(converter::toDomain);
            if (taskLevel.isPresent()) {
                return taskLevel;
            }
        }

        return endpointDefMapper.selectActive(provenanceId, "SOURCE", "ALL", endpointName.trim(), timestamp)
                .map(converter::toDomain);
    }

    @Override
    public Optional<WindowOffsetConfig> findActiveWindowOffset(Long provenanceId,
                                                               String taskType,
                                                               Instant at) {
        Instant timestamp = at != null ? at : Instant.now();
        String taskKey = normalizeTaskKey(taskType);

        if (taskType != null) {
            Optional<WindowOffsetConfig> taskLevel = windowOffsetCfgMapper.selectActive(
                            provenanceId, "TASK", taskKey, timestamp)
                    .map(converter::toDomain);
            if (taskLevel.isPresent()) {
                return taskLevel;
            }
        }

        return windowOffsetCfgMapper.selectActive(provenanceId, "SOURCE", "ALL", timestamp)
                .map(converter::toDomain);
    }

    @Override
    public Optional<PaginationConfig> findActivePagination(Long provenanceId,
                                                           String taskType,
                                                           Instant at) {
        Instant timestamp = at != null ? at : Instant.now();
        String taskKey = normalizeTaskKey(taskType);

        if (taskType != null) {
            Optional<PaginationConfig> taskLevel = paginationCfgMapper.selectActive(
                            provenanceId, "TASK", taskKey, timestamp)
                    .map(converter::toDomain);
            if (taskLevel.isPresent()) {
                return taskLevel;
            }
        }

        return paginationCfgMapper.selectActive(provenanceId, "SOURCE", "ALL", timestamp)
                .map(converter::toDomain);
    }

    @Override
    public Optional<HttpConfig> findActiveHttpConfig(Long provenanceId,
                                                     String taskType,
                                                     Instant at) {
        Instant timestamp = at != null ? at : Instant.now();
        String taskKey = normalizeTaskKey(taskType);

        if (taskType != null) {
            Optional<HttpConfig> taskLevel = httpCfgMapper.selectActive(
                            provenanceId, "TASK", taskKey, timestamp)
                    .map(converter::toDomain);
            if (taskLevel.isPresent()) {
                return taskLevel;
            }
        }

        return httpCfgMapper.selectActive(provenanceId, "SOURCE", "ALL", timestamp)
                .map(converter::toDomain);
    }

    @Override
    public Optional<BatchingConfig> findActiveBatching(Long provenanceId,
                                                       String taskType,
                                                       Long endpointId,
                                                       String credentialName,
                                                       Instant at) {
        Instant timestamp = at != null ? at : Instant.now();
        String taskKey = normalizeTaskKey(taskType);

        if (taskType != null) {
            Optional<BatchingConfig> precise = queryBatching(provenanceId, "TASK", taskKey, endpointId, credentialName, timestamp);
            if (precise.isPresent()) {
                return precise;
            }
            Optional<BatchingConfig> taskGeneral = queryBatching(provenanceId, "TASK", taskKey, null, null, timestamp);
            if (taskGeneral.isPresent()) {
                return taskGeneral;
            }
        }

        Optional<BatchingConfig> sourcePrecise = queryBatching(provenanceId, "SOURCE", "ALL", endpointId, credentialName, timestamp);
        if (sourcePrecise.isPresent()) {
            return sourcePrecise;
        }
        return queryBatching(provenanceId, "SOURCE", "ALL", null, null, timestamp);
    }

    @Override
    public Optional<RetryConfig> findActiveRetry(Long provenanceId,
                                                 String taskType,
                                                 Instant at) {
        Instant timestamp = at != null ? at : Instant.now();
        String taskKey = normalizeTaskKey(taskType);

        if (taskType != null) {
            Optional<RetryConfig> taskLevel = retryCfgMapper.selectActive(
                            provenanceId, "TASK", taskKey, timestamp)
                    .map(converter::toDomain);
            if (taskLevel.isPresent()) {
                return taskLevel;
            }
        }

        return retryCfgMapper.selectActive(provenanceId, "SOURCE", "ALL", timestamp)
                .map(converter::toDomain);
    }

    @Override
    public Optional<RateLimitConfig> findActiveRateLimit(Long provenanceId,
                                                         String taskType,
                                                         Long endpointId,
                                                         String credentialName,
                                                         Instant at) {
        Instant timestamp = at != null ? at : Instant.now();
        String taskKey = normalizeTaskKey(taskType);

        if (taskType != null) {
            Optional<RateLimitConfig> precise = queryRateLimit(provenanceId, "TASK", taskKey, endpointId, credentialName, timestamp);
            if (precise.isPresent()) {
                return precise;
            }
            Optional<RateLimitConfig> taskGeneral = queryRateLimit(provenanceId, "TASK", taskKey, null, null, timestamp);
            if (taskGeneral.isPresent()) {
                return taskGeneral;
            }
        }

        Optional<RateLimitConfig> sourcePrecise = queryRateLimit(provenanceId, "SOURCE", "ALL", endpointId, credentialName, timestamp);
        if (sourcePrecise.isPresent()) {
            return sourcePrecise;
        }
        return queryRateLimit(provenanceId, "SOURCE", "ALL", null, null, timestamp);
    }

    @Override
    public List<Credential> findActiveCredentials(Long provenanceId,
                                                  String taskType,
                                                  Long endpointId,
                                                  Instant at) {
        Instant timestamp = at != null ? at : Instant.now();
        String taskKey = normalizeTaskKey(taskType);

        if (taskType != null) {
            List<Credential> taskScoped = credentialMapper.selectActive(
                            provenanceId, "TASK", taskKey, endpointId, timestamp)
                    .stream()
                    .map(converter::toDomain)
                    .toList();
            if (!taskScoped.isEmpty()) {
                return taskScoped;
            }
        }

        return credentialMapper.selectActive(provenanceId, "SOURCE", "ALL", endpointId, timestamp)
                .stream()
                .map(converter::toDomain)
                .toList();
    }

    @Override
    public Optional<ProvenanceConfiguration> loadConfiguration(Long provenanceId,
                                                               String taskType,
                                                               String endpointName,
                                                               Instant at) {
        Optional<Provenance> provenanceOpt = findProvenanceById(provenanceId);
        if (provenanceOpt.isEmpty()) {
            return Optional.empty();
        }

        Instant timestamp = at != null ? at : Instant.now();
        Provenance provenance = provenanceOpt.get();
        Optional<EndpointDefinition> endpointOpt = findActiveEndpoint(provenanceId, taskType, endpointName, timestamp);
        EndpointDefinition endpoint = endpointOpt.orElse(null);
        Long endpointId = endpoint != null ? endpoint.id() : null;

        Optional<WindowOffsetConfig> window = findActiveWindowOffset(provenanceId, taskType, timestamp);
        Optional<PaginationConfig> pagination = findActivePagination(provenanceId, taskType, timestamp);
        Optional<HttpConfig> httpConfig = findActiveHttpConfig(provenanceId, taskType, timestamp);
        Optional<BatchingConfig> batching = findActiveBatching(provenanceId, taskType, endpointId, null, timestamp);
        Optional<RetryConfig> retry = findActiveRetry(provenanceId, taskType, timestamp);
        Optional<RateLimitConfig> rateLimit = findActiveRateLimit(provenanceId, taskType, endpointId, null, timestamp);
        List<Credential> credentials = findActiveCredentials(provenanceId, taskType, endpointId, timestamp);

        ProvenanceConfiguration configuration = new ProvenanceConfiguration(
                provenance,
                endpoint,
                window.orElse(null),
                pagination.orElse(null),
                httpConfig.orElse(null),
                batching.orElse(null),
                retry.orElse(null),
                rateLimit.orElse(null),
                credentials
        );
        return Optional.of(configuration);
    }

    private Optional<BatchingConfig> queryBatching(Long provenanceId,
                                                   String scopeCode,
                                                   String taskKey,
                                                   Long endpointId,
                                                   String credentialName,
                                                   Instant timestamp) {
        return batchingCfgMapper.selectActive(provenanceId, scopeCode, taskKey, endpointId, credentialName, timestamp)
                .map(converter::toDomain);
    }

    private Optional<RateLimitConfig> queryRateLimit(Long provenanceId,
                                                     String scopeCode,
                                                     String taskKey,
                                                     Long endpointId,
                                                     String credentialName,
                                                     Instant timestamp) {
        return rateLimitCfgMapper.selectActive(provenanceId, scopeCode, taskKey, endpointId, credentialName, timestamp)
                .map(converter::toDomain);
    }

    private String normalizeTaskKey(String taskType) {
        if (taskType == null || taskType.isBlank()) {
            return "ALL";
        }
        return taskType.trim();
    }

    private Optional<Provenance> findProvenanceById(Long provenanceId) {
        if (provenanceId == null) {
            return Optional.empty();
        }
        RegProvenanceDO entity = provenanceMapper.selectById(provenanceId);
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(converter.toDomain(entity));
    }
}
