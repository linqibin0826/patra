package com.patra.ingest.infra.integration.registry.converter;

import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.registry.api.dto.provenance.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// 注册中心 API DTO 到采集领域配置快照的转换器。
///
/// 将 patra-registry 的配置响应转换为采集领域所需的快照对象。遵循 MapStruct 最佳实践进行类型安全的映射。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProvenanceConfigSnapshotConverter {

  /// 将完整的配置响应转换为快照。
  ///
  /// @param resp 配置响应
  /// @return 快照
  @Mapping(target = "provenance", source = "provenance")
  @Mapping(target = "windowOffset", source = "windowOffset")
  @Mapping(target = "pagination", source = "pagination")
  @Mapping(target = "http", source = "http")
  @Mapping(target = "batching", source = "batching")
  @Mapping(target = "retry", source = "retry")
  @Mapping(target = "rateLimit", source = "rateLimit")
  ProvenanceConfigSnapshot convert(ProvenanceConfigResp resp);

  /// 映射溯源基础信息。
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
        source.lifecycleStatusCode());
  }

  /// 映射窗口偏移配置。
  default ProvenanceConfigSnapshot.WindowOffsetConfig mapWindowOffsetConfig(
      WindowOffsetResp source) {
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
        source.offsetFieldKey(),
        source.offsetDateFormat(),
        source.windowDateFieldKey(),
        source.maxIdsPerWindow(),
        source.maxWindowSpanSeconds());
  }

  /// 映射分页配置。
  default ProvenanceConfigSnapshot.PaginationConfig mapPaginationConfig(
      PaginationConfigResp source) {
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
        source.sortingDirection());
  }

  /// 映射 HTTP 配置。
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
        source.idempotencyTtlSeconds());
  }

  /// 映射批处理配置。
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
        source.maxIdsPerRequest());
  }

  /// 映射重试配置。
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
        source.circuitCooldownMillis());
  }

  /// 映射限流配置。
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
        source.perCredentialQpsLimit());
  }

  // 凭证维度已从 API 和快照中移除;相关映射已删除。
}
