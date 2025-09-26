package com.patra.ingest.adapter.outbound.rest.converter;

import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.registry.api.rpc.dto.provenance.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.Collections;
import java.util.List;

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
    @Mapping(target = "endpoint", source = "endpoint")
    @Mapping(target = "windowOffset", source = "windowOffset")
    @Mapping(target = "pagination", source = "pagination")
    @Mapping(target = "http", source = "http")
    @Mapping(target = "batching", source = "batching")
    @Mapping(target = "retry", source = "retry")
    @Mapping(target = "rateLimit", source = "rateLimit")
    @Mapping(target = "credentials", source = "credentials")
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
     * 映射端点定义。
     */
    default ProvenanceConfigSnapshot.EndpointDefinition mapEndpointDefinition(EndpointDefinitionResp source) {
        if (source == null) {
            return null;
        }
        return new ProvenanceConfigSnapshot.EndpointDefinition(
                source.id(),
                source.provenanceId(),
                source.scopeCode(),
                source.taskType(),
                source.taskTypeKey(),
                source.endpointName(),
                source.endpointUsageCode(),
                source.httpMethodCode(),
                source.pathTemplate(),
                source.defaultQueryParamsJson(),
                source.defaultBodyPayloadJson(),
                source.requestContentType(),
                source.authRequired(),
                source.credentialHintName(),
                source.pageParamName(),
                source.pageSizeParamName(),
                source.cursorParamName(),
                source.idsParamName(),
                source.effectiveFrom(),
                source.effectiveTo()
        );
    }

    /**
     * 映射窗口/指针配置。
     */
    default ProvenanceConfigSnapshot.WindowOffsetConfig mapWindowOffsetConfig(WindowOffsetResp source) {
        if (source == null) {
            return null;
        }
        return new ProvenanceConfigSnapshot.WindowOffsetConfig(
                source.id(),
                source.provenanceId(),
                source.scopeCode(),
                source.taskType(),
                source.taskTypeKey(),
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
     * 映射分页配置。
     */
    default ProvenanceConfigSnapshot.PaginationConfig mapPaginationConfig(PaginationConfigResp source) {
        if (source == null) {
            return null;
        }
        return new ProvenanceConfigSnapshot.PaginationConfig(
                source.id(),
                source.provenanceId(),
                source.scopeCode(),
                source.taskType(),
                source.taskTypeKey(),
                source.effectiveFrom(),
                source.effectiveTo(),
                source.paginationModeCode(),
                source.pageSizeValue(),
                source.maxPagesPerExecution(),
                source.pageNumberParamName(),
                source.pageSizeParamName(),
                source.startPageNumber(),
                source.sortFieldParamName(),
                source.sortDirection(),
                source.cursorParamName(),
                source.initialCursorValue(),
                source.nextCursorJsonpath(),
                source.hasMoreJsonpath(),
                source.totalCountJsonpath(),
                source.nextCursorXpath(),
                source.hasMoreXpath(),
                source.totalCountXpath()
        );
    }

    /**
     * 映射 HTTP 配置。
     */
    default ProvenanceConfigSnapshot.HttpConfig mapHttpConfig(HttpConfigResp source) {
        if (source == null) {
            return null;
        }
        return new ProvenanceConfigSnapshot.HttpConfig(
                source.id(),
                source.provenanceId(),
                source.scopeCode(),
                source.taskType(),
                source.taskTypeKey(),
                source.effectiveFrom(),
                source.effectiveTo(),
                source.baseUrlOverride(),
                source.defaultHeadersJson(),
                source.timeoutConnectMillis(),
                source.timeoutReadMillis(),
                source.timeoutTotalMillis(),
                source.tlsVerifyEnabled(),
                source.proxyUrlValue(),
                source.acceptCompressEnabled(),
                source.preferHttp2Enabled(),
                source.retryAfterPolicyCode(),
                source.retryAfterCapMillis(),
                source.idempotencyHeaderName(),
                source.idempotencyTtlSeconds()
        );
    }

    /**
     * 映射批量配置。
     */
    default ProvenanceConfigSnapshot.BatchingConfig mapBatchingConfig(BatchingConfigResp source) {
        if (source == null) {
            return null;
        }
        return new ProvenanceConfigSnapshot.BatchingConfig(
                source.id(),
                source.provenanceId(),
                source.scopeCode(),
                source.taskType(),
                source.taskTypeKey(),
                source.effectiveFrom(),
                source.effectiveTo(),
                source.detailFetchBatchSize(),
                source.endpointId(),
                source.credentialName(),
                source.idsParamName(),
                source.idsJoinDelimiter(),
                source.maxIdsPerRequest(),
                source.preferCompactPayload(),
                source.payloadCompressStrategyCode(),
                source.appParallelismDegree(),
                source.perHostConcurrencyLimit(),
                source.httpConnPoolSize(),
                source.backpressureStrategyCode(),
                source.requestTemplateJson()
        );
    }

    /**
     * 映射重试配置。
     */
    default ProvenanceConfigSnapshot.RetryConfig mapRetryConfig(RetryConfigResp source) {
        if (source == null) {
            return null;
        }
        return new ProvenanceConfigSnapshot.RetryConfig(
                source.id(),
                source.provenanceId(),
                source.scopeCode(),
                source.taskType(),
                source.taskTypeKey(),
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
     * 映射限流配置。
     */
    default ProvenanceConfigSnapshot.RateLimitConfig mapRateLimitConfig(RateLimitConfigResp source) {
        if (source == null) {
            return null;
        }
        return new ProvenanceConfigSnapshot.RateLimitConfig(
                source.id(),
                source.provenanceId(),
                source.scopeCode(),
                source.taskType(),
                source.taskTypeKey(),
                source.effectiveFrom(),
                source.effectiveTo(),
                source.rateTokensPerSecond(),
                source.burstBucketCapacity(),
                source.maxConcurrentRequests(),
                source.perCredentialQpsLimit(),
                source.bucketGranularityScopeCode(),
                source.smoothingWindowMillis(),
                source.respectServerRateHeader(),
                source.endpointId(),
                source.credentialName()
        );
    }

    /**
     * 映射凭证配置列表。
     */
    default List<ProvenanceConfigSnapshot.CredentialConfig> mapCredentialConfigs(List<CredentialResp> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }

        return source.stream()
                .map(this::mapCredentialConfig)
                .toList();
    }

    /**
     * 映射单个凭证配置。
     */
    default ProvenanceConfigSnapshot.CredentialConfig mapCredentialConfig(CredentialResp source) {
        if (source == null) {
            return null;
        }
        return new ProvenanceConfigSnapshot.CredentialConfig(
                source.id(),
                source.provenanceId(),
                source.scopeCode(),
                source.taskType(),
                source.taskTypeKey(),
                source.endpointId(),
                source.credentialName(),
                source.authType(),
                source.inboundLocationCode(),
                source.credentialFieldName(),
                source.credentialValuePrefix(),
                source.credentialValueRef(),
                source.basicUsernameRef(),
                source.basicPasswordRef(),
                source.oauthTokenUrl(),
                source.oauthClientIdRef(),
                source.oauthClientSecretRef(),
                source.oauthScope(),
                source.oauthAudience(),
                source.extraJson(),
                source.effectiveFrom(),
                source.effectiveTo(),
                source.defaultPreferred(),
                source.lifecycleStatusCode()
        );
    }
}
