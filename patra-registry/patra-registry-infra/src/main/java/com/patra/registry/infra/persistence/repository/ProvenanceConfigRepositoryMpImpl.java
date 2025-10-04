package com.patra.registry.infra.persistence.repository;

import com.patra.registry.domain.support.RegistryKeyPlaceholders;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.enums.RegistryConfigScope;
import com.patra.registry.domain.model.aggregate.ProvenanceConfiguration;
import com.patra.registry.domain.model.vo.provenance.*;
import com.patra.registry.domain.port.ProvenanceConfigRepository;
import com.patra.registry.infra.persistence.converter.ProvenanceEntityConverter;
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
 * <p>
 * 按 TASK → SOURCE 优先级查询并执行覆盖逻辑，若任务级无结果则回退到来源级。
 * </p>
 * <p>
 * 所有 scope 查询统一使用 {@link RegistryConfigScope} 常量，避免字符串硬编码。
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ProvenanceConfigRepositoryMpImpl implements ProvenanceConfigRepository {

    private final RegProvenanceMapper provenanceMapper;
    private final RegProvWindowOffsetCfgMapper windowOffsetCfgMapper;
    private final RegProvPaginationCfgMapper paginationCfgMapper;
    private final RegProvHttpCfgMapper httpCfgMapper;
    private final RegProvBatchingCfgMapper batchingCfgMapper;
    private final RegProvRetryCfgMapper retryCfgMapper;
    private final RegProvRateLimitCfgMapper rateLimitCfgMapper;
    private final RegProvCredentialMapper credentialMapper;
    private final ProvenanceEntityConverter converter;

    @Override
    public Optional<Provenance> findProvenanceByCode(ProvenanceCode provenanceCode) {
        return provenanceMapper.selectByCode(provenanceCode.getCode())
                .map(converter::toDomain);
    }

    @Override
    public List<Provenance> findAllProvenances() {
        return provenanceMapper.selectAllActive().stream()
                .map(converter::toDomain)
                .toList();
    }

    @Override
    public Optional<WindowOffsetConfig> findActiveWindowOffset(Long provenanceId,
                                                               String operationType,
                                                               Instant at) {
        Instant timestamp = atOrNow(at);
        String operationKey = normalizeOperationKey(operationType);
        return windowOffsetCfgMapper.selectActiveMerged(provenanceId, operationKey, timestamp)
                .map(converter::toDomain);
    }

    @Override
    public Optional<PaginationConfig> findActivePagination(Long provenanceId,
                                                           String operationType,
                                                           Instant at) {
        Instant timestamp = atOrNow(at);
        String operationKey = normalizeOperationKey(operationType);
        return paginationCfgMapper.selectActiveMerged(provenanceId, operationKey, timestamp)
                .map(converter::toDomain);
    }

    @Override
    public Optional<HttpConfig> findActiveHttpConfig(Long provenanceId,
                                                     String operationType,
                                                     Instant at) {
        Instant timestamp = atOrNow(at);
        String operationKey = normalizeOperationKey(operationType);
        return httpCfgMapper.selectActiveMerged(provenanceId, operationKey, timestamp)
                .map(converter::toDomain);
    }

    @Override
    public Optional<BatchingConfig> findActiveBatching(Long provenanceId,
                                                       String operationType,
                                                       Instant at) {
        Instant timestamp = atOrNow(at);
        String operationKey = normalizeOperationKey(operationType);
        return batchingCfgMapper.selectActiveMerged(provenanceId, operationKey, timestamp)
                .map(converter::toDomain);
    }

    @Override
    public Optional<RetryConfig> findActiveRetry(Long provenanceId,
                                                 String operationType,
                                                 Instant at) {
        Instant timestamp = atOrNow(at);
        String operationKey = normalizeOperationKey(operationType);
        return retryCfgMapper.selectActiveMerged(provenanceId, operationKey, timestamp)
                .map(converter::toDomain);
    }

    @Override
    public Optional<RateLimitConfig> findActiveRateLimit(Long provenanceId,
                                                         String operationType,
                                                         Instant at) {
        Instant timestamp = atOrNow(at);
        String operationKey = normalizeOperationKey(operationType);
        return rateLimitCfgMapper.selectActiveMerged(provenanceId, operationKey, timestamp)
                .map(converter::toDomain);
    }

    @Override
    public List<Credential> findActiveCredentials(Long provenanceId,
                                                  String operationType,
                                                  Instant at) {
        Instant timestamp = atOrNow(at);
        String operationKey = normalizeOperationKey(operationType);
        return credentialMapper.selectActiveMerged(provenanceId, operationKey, timestamp)
                .stream()
                .map(converter::toDomain)
                .toList();
    }

    @Override
    public Optional<ProvenanceConfiguration> loadConfiguration(Long provenanceId,
                                                               String operationType,
                                                               Instant at) {
        Optional<Provenance> provenanceOpt = findProvenanceById(provenanceId);
        if (provenanceOpt.isEmpty()) {
            return Optional.empty();
        }
        Instant timestamp = atOrNow(at);
        Provenance provenance = provenanceOpt.get();

        Optional<WindowOffsetConfig> window = findActiveWindowOffset(provenanceId, operationType, timestamp);
        Optional<PaginationConfig> pagination = findActivePagination(provenanceId, operationType, timestamp);
        Optional<HttpConfig> httpConfig = findActiveHttpConfig(provenanceId, operationType, timestamp);
        Optional<BatchingConfig> batching = findActiveBatching(provenanceId, operationType, timestamp);
        Optional<RetryConfig> retry = findActiveRetry(provenanceId, operationType, timestamp);
        Optional<RateLimitConfig> rateLimit = findActiveRateLimit(provenanceId, operationType, timestamp);
        List<Credential> credentials = findActiveCredentials(provenanceId, operationType, timestamp);

        ProvenanceConfiguration configuration = new ProvenanceConfiguration(
                provenance,
                window.orElse(null),
                pagination.orElse(null),
                httpConfig.orElse(null),
                batching.orElse(null),
                retry.orElse(null),
                rateLimit.orElse(null),
                credentials);
        return Optional.of(configuration);
    }

    private String normalizeOperationKey(String operationType) {
        if (operationType == null) {
            return RegistryKeyPlaceholders.ALL;
        }
        String trimmed = operationType.trim();
        if (trimmed.isEmpty()) {
            return RegistryKeyPlaceholders.ALL;
        }
        // 非字母数字统一为下划线，然后连续下划线折叠为单个，下划线首尾修剪
        String upper = trimmed.toUpperCase();
        String normalized = upper.replaceAll("[^A-Z0-9]", "_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        if (normalized.isEmpty()) {
            return RegistryKeyPlaceholders.ALL;
        }
        return normalized;
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
