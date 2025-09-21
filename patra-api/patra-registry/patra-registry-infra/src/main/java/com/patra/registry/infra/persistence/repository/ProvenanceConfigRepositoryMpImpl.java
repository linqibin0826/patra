package com.patra.registry.infra.persistence.repository;

import com.patra.common.constant.RegistryKeys;
import com.patra.common.enums.RegistryConfigScope;
import com.patra.registry.domain.model.aggregate.ProvenanceConfiguration;
import com.patra.registry.domain.model.vo.provenance.*;
import com.patra.registry.domain.port.ProvenanceConfigRepository;
import com.patra.registry.infra.mapstruct.ProvenanceEntityConverter;
import com.patra.registry.infra.persistence.entity.provenance.RegProvenanceDO;
import com.patra.registry.infra.persistence.mapper.provenance.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Provenance 配置仓储 MyBatis 实现。
 * <p>按 TASK → SOURCE 优先级查询并执行覆盖逻辑，若任务级无结果则回退到来源级。</p>
 * <p>所有 scope 查询统一使用 {@link RegistryConfigScope} 常量，避免字符串硬编码。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ProvenanceConfigRepositoryMpImpl implements ProvenanceConfigRepository {

    private static final String SCOPE_SOURCE = RegistryConfigScope.SOURCE.code();
    private static final String SCOPE_TASK = RegistryConfigScope.TASK.code();

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
        return provenanceMapper.selectAllActive().stream()
                .map(converter::toDomain)
                .toList();
    }

    @Override
    public Optional<EndpointDefinition> findActiveEndpoint(Long provenanceId,
                                                           String taskType,
                                                           String endpointName,
                                                           Instant at) {
        if (endpointName == null || endpointName.isBlank()) {
            return Optional.empty();
        }
        Instant timestamp = atOrNow(at);
        String taskKey = normalizeTaskKey(taskType);

        if (taskType != null) {
            Optional<EndpointDefinition> taskLevel = endpointDefMapper.selectActive(
                            provenanceId, SCOPE_TASK, taskKey, endpointName.trim(), timestamp)
                    .map(converter::toDomain);
            if (taskLevel.isPresent()) {
                return taskLevel;
            }
        }
        return endpointDefMapper.selectActive(provenanceId, SCOPE_SOURCE, RegistryKeys.ALL, endpointName.trim(), timestamp)
                .map(converter::toDomain);
    }

    @Override
    public Optional<WindowOffsetConfig> findActiveWindowOffset(Long provenanceId,
                                                               String taskType,
                                                               Instant at) {
        Instant timestamp = atOrNow(at);
        String taskKey = normalizeTaskKey(taskType);
        if (taskType != null) {
            Optional<WindowOffsetConfig> taskLevel = windowOffsetCfgMapper.selectActive(
                            provenanceId, SCOPE_TASK, taskKey, timestamp)
                    .map(converter::toDomain);
            if (taskLevel.isPresent()) {
                return taskLevel;
            }
        }
        return windowOffsetCfgMapper.selectActive(provenanceId, SCOPE_SOURCE, RegistryKeys.ALL, timestamp)
                .map(converter::toDomain);
    }

    @Override
    public Optional<PaginationConfig> findActivePagination(Long provenanceId,
                                                           String taskType,
                                                           Instant at) {
        Instant timestamp = atOrNow(at);
        String taskKey = normalizeTaskKey(taskType);
        if (taskType != null) {
            Optional<PaginationConfig> taskLevel = paginationCfgMapper.selectActive(
                            provenanceId, SCOPE_TASK, taskKey, timestamp)
                    .map(converter::toDomain);
            if (taskLevel.isPresent()) {
                return taskLevel;
            }
        }
        return paginationCfgMapper.selectActive(provenanceId, SCOPE_SOURCE, RegistryKeys.ALL, timestamp)
                .map(converter::toDomain);
    }

    @Override
    public Optional<HttpConfig> findActiveHttpConfig(Long provenanceId,
                                                     String taskType,
                                                     Instant at) {
        Instant timestamp = atOrNow(at);
        String taskKey = normalizeTaskKey(taskType);
        if (taskType != null) {
            Optional<HttpConfig> taskLevel = httpCfgMapper.selectActive(
                            provenanceId, SCOPE_TASK, taskKey, timestamp)
                    .map(converter::toDomain);
            if (taskLevel.isPresent()) {
                return taskLevel;
            }
        }
        return httpCfgMapper.selectActive(provenanceId, SCOPE_SOURCE, RegistryKeys.ALL, timestamp)
                .map(converter::toDomain);
    }

    @Override
    public Optional<BatchingConfig> findActiveBatching(Long provenanceId,
                                                       String taskType,
                                                       Long endpointId,
                                                       String credentialName,
                                                       Instant at) {
        Instant timestamp = atOrNow(at);
        String taskKey = normalizeTaskKey(taskType);
        if (taskType != null) {
            Optional<BatchingConfig> precise = queryBatching(provenanceId, SCOPE_TASK, taskKey, endpointId, credentialName, timestamp);
            if (precise.isPresent()) {
                return precise;
            }
            Optional<BatchingConfig> taskGeneral = queryBatching(provenanceId, SCOPE_TASK, taskKey, null, null, timestamp);
            if (taskGeneral.isPresent()) {
                return taskGeneral;
            }
        }
        Optional<BatchingConfig> sourcePrecise = queryBatching(provenanceId, SCOPE_SOURCE, RegistryKeys.ALL, endpointId, credentialName, timestamp);
        if (sourcePrecise.isPresent()) {
            return sourcePrecise;
        }
        return queryBatching(provenanceId, SCOPE_SOURCE, RegistryKeys.ALL, null, null, timestamp);
    }

    @Override
    public Optional<RetryConfig> findActiveRetry(Long provenanceId,
                                                 String taskType,
                                                 Instant at) {
        Instant timestamp = atOrNow(at);
        String taskKey = normalizeTaskKey(taskType);
        if (taskType != null) {
            Optional<RetryConfig> taskLevel = retryCfgMapper.selectActive(
                            provenanceId, SCOPE_TASK, taskKey, timestamp)
                    .map(converter::toDomain);
            if (taskLevel.isPresent()) {
                return taskLevel;
            }
        }
        return retryCfgMapper.selectActive(provenanceId, SCOPE_SOURCE, RegistryKeys.ALL, timestamp)
                .map(converter::toDomain);
    }

    @Override
    public Optional<RateLimitConfig> findActiveRateLimit(Long provenanceId,
                                                         String taskType,
                                                         Long endpointId,
                                                         String credentialName,
                                                         Instant at) {
        Instant timestamp = atOrNow(at);
        String taskKey = normalizeTaskKey(taskType);
        if (taskType != null) {
            Optional<RateLimitConfig> precise = queryRateLimit(provenanceId, SCOPE_TASK, taskKey, endpointId, credentialName, timestamp);
            if (precise.isPresent()) {
                return precise;
            }
            Optional<RateLimitConfig> taskGeneral = queryRateLimit(provenanceId, SCOPE_TASK, taskKey, null, null, timestamp);
            if (taskGeneral.isPresent()) {
                return taskGeneral;
            }
        }
        Optional<RateLimitConfig> sourcePrecise = queryRateLimit(provenanceId, SCOPE_SOURCE, RegistryKeys.ALL, endpointId, credentialName, timestamp);
        if (sourcePrecise.isPresent()) {
            return sourcePrecise;
        }
        return queryRateLimit(provenanceId, SCOPE_SOURCE, RegistryKeys.ALL, null, null, timestamp);
    }

    @Override
    public List<Credential> findActiveCredentials(Long provenanceId,
                                                  String taskType,
                                                  Long endpointId,
                                                  Instant at) {
        Instant timestamp = atOrNow(at);
        String taskKey = normalizeTaskKey(taskType);
        if (taskType != null) {
            List<Credential> taskScoped = credentialMapper.selectActive(
                            provenanceId, SCOPE_TASK, taskKey, endpointId, timestamp)
                    .stream()
                    .map(converter::toDomain)
                    .toList();
            if (!taskScoped.isEmpty()) {
                return taskScoped;
            }
        }
        return credentialMapper.selectActive(provenanceId, SCOPE_SOURCE, RegistryKeys.ALL, endpointId, timestamp)
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
        Instant timestamp = atOrNow(at);
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
            return RegistryKeys.ALL;
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

    private Instant atOrNow(Instant at) {
        return at != null ? at : Instant.now();
    }
}
