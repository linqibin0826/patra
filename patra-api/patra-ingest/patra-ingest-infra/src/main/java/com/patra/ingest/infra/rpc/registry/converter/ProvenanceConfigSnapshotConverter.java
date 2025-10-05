package com.patra.ingest.infra.rpc.registry.converter;

import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.registry.api.rpc.dto.provenance.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Registry API DTO 到 Ingest 领域快照的转换器。
 *
 * <p>负责将从 patra-registry 获取的配置响应转换为 Ingest 领域层所需的快照对象。
 * 遵循 MapStruct 最佳实践，提供类型安全的映射转换。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProvenanceConfigSnapshotConverter {

    /**
     * 转换完整的配置响应为快照。
     *
     * @param resp 配置响应
     * @return 配置快照
     */
    @Mapping(target = "provenance", source = "provenance")
    @Mapping(target = "windowOffset", source = "windowOffset")
    @Mapping(target = "pagination", source = "pagination")
    @Mapping(target = "http", source = "http")
    @Mapping(target = "batching", source = "batching")
    @Mapping(target = "retry", source = "retry")
    @Mapping(target = "rateLimit", source = "rateLimit")
    ProvenanceConfigSnapshot convert(ProvenanceConfigResp resp);

    /**
     * 映射来源基础信息。
     */
    default ProvenanceConfigSnapshot.ProvenanceInfo mapProvenanceInfo(ProvenanceResp source) {
        if (source == null) {
            return null;
        }
        return new ProvenanceConfigSnapshot.ProvenanceInfo(
                source.id(),
                source.code(),
                source.name(),
                source.baseUrlDefault(),
                source.timezoneDefault(),
                source.docsUrl(),
                source.active(),
                source.lifecycleStatusCode()
        );
    }

    /**
     * Map window offset configuration.
     */
    default ProvenanceConfigSnapshot.WindowOffsetConfig mapWindowOffsetConfig(WindowOffsetResp source) {
        if (source == null) {
            return null;
        }
        return new ProvenanceConfigSnapshot.WindowOffsetConfig(
                source.id(),
                source.provenanceId(),
                source.operationType(),
                source.effectiveFrom(),
                source.effectiveTo(),
                source.windowModeCode(),
                source.windowSizeValue(),
                source.windowSizeUnitCode(),
                source.calendarAlignTo(),
                source.lookbackValue(),
                source.lookbackUnitCode(),
                source.overlapValue(),
                source.overlapUnitCode(),
                source.watermarkLagSeconds(),
                source.offsetTypeCode(),
                source.offsetFieldName(),
                source.offsetDateFormat(),
                source.defaultDateFieldName(),
                source.maxIdsPerWindow(),
                source.maxWindowSpanSeconds()
        );
    }

    /**
     * Map pagination configuration.
     */
    default ProvenanceConfigSnapshot.PaginationConfig mapPaginationConfig(PaginationConfigResp source) {
        if (source == null) {
            return null;
        }
        return new ProvenanceConfigSnapshot.PaginationConfig(
                source.id(),
                source.provenanceId(),
                source.operationType(),
                source.effectiveFrom(),
                source.effectiveTo(),
                source.paginationModeCode(),
                source.pageSizeValue(),
                source.maxPagesPerExecution(),
                source.sortFieldParamName(),
                source.sortingDirection()
        );
    }

    /**
     * Map HTTP configuration.
     */
    default ProvenanceConfigSnapshot.HttpConfig mapHttpConfig(HttpConfigResp source) {
        if (source == null) {
            return null;
        }
        return new ProvenanceConfigSnapshot.HttpConfig(
                source.id(),
                source.provenanceId(),
                source.operationType(),
                source.effectiveFrom(),
                source.effectiveTo(),
                source.defaultHeadersJson(),
                source.timeoutConnectMillis(),
                source.timeoutReadMillis(),
                source.timeoutTotalMillis(),
                source.tlsVerifyEnabled(),
                source.proxyUrlValue(),
                source.retryAfterPolicyCode(),
                source.retryAfterCapMillis(),
                source.idempotencyHeaderName(),
                source.idempotencyTtlSeconds()
        );
    }

    /**
     * Map batching configuration.
     */
    default ProvenanceConfigSnapshot.BatchingConfig mapBatchingConfig(BatchingConfigResp source) {
        if (source == null) {
            return null;
        }
        return new ProvenanceConfigSnapshot.BatchingConfig(
                source.id(),
                source.provenanceId(),
                source.operationType(),
                source.effectiveFrom(),
                source.effectiveTo(),
                source.detailFetchBatchSize(),
                source.idsParamName(),
                source.idsJoinDelimiter(),
                source.maxIdsPerRequest()
        );
    }

    /**
     * Map retry configuration.
     */
    default ProvenanceConfigSnapshot.RetryConfig mapRetryConfig(RetryConfigResp source) {
        if (source == null) {
            return null;
        }
        return new ProvenanceConfigSnapshot.RetryConfig(
                source.id(),
                source.provenanceId(),
                source.operationType(),
                source.effectiveFrom(),
                source.effectiveTo(),
                source.maxRetryTimes(),
                source.backoffPolicyTypeCode(),
                source.initialDelayMillis(),
                source.maxDelayMillis(),
                source.expMultiplierValue(),
                source.jitterFactorRatio(),
                source.retryHttpStatusJson(),
                source.giveupHttpStatusJson(),
                source.retryOnNetworkError(),
                source.circuitBreakThreshold(),
                source.circuitCooldownMillis()
        );
    }

    /**
     * Map rate limit configuration.
     */
    default ProvenanceConfigSnapshot.RateLimitConfig mapRateLimitConfig(RateLimitConfigResp source) {
        if (source == null) {
            return null;
        }
        return new ProvenanceConfigSnapshot.RateLimitConfig(
                source.id(),
                source.provenanceId(),
                source.operationType(),
                source.effectiveFrom(),
                source.effectiveTo(),
                source.maxConcurrentRequests(),
                source.perCredentialQpsLimit()
        );
    }

    // Credential dimension removed from API and snapshot; related mappings deleted.
}
