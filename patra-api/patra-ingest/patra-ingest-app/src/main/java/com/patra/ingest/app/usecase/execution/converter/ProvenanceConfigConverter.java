package com.patra.ingest.app.usecase.execution.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.common.json.JsonMapperHolder;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.starter.provenance.common.config.BatchingConfig;
import com.patra.starter.provenance.common.config.HttpConfig;
import com.patra.starter.provenance.common.config.PaginationConfig;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.config.RateLimitConfig;
import com.patra.starter.provenance.common.config.RetryConfig;
import com.patra.starter.provenance.common.config.WindowOffsetConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Provenance配置转换器
 *
 * <p>在六边形架构+DDD中的角色:应用层转换器,负责将规划时捕获的{@link ProvenanceConfigSnapshot} 转换为starter适配器能够理解的运行时{@link
 * ProvenanceConfig}实例。
 *
 * <p>主要职责:
 *
 * <ul>
 *   <li>将数据库中的配置快照转换为可执行的配置对象
 *   <li>执行防御性转换,确保配置的完整性和有效性
 *   <li>处理可选配置,让下游使用默认值
 * </ul>
 *
 * <p>转换策略:
 *
 * <ul>
 *   <li>当快照缺少最小必需信息(如baseUrl)时返回{@code null}
 *   <li>解析JSON格式的headers为不可变Map,忽略格式错误的条目
 *   <li>省略空的可选配置段,让下游默认值生效
 * </ul>
 */
@Component
@Slf4j
public class ProvenanceConfigConverter {

  /**
   * 将Provenance配置快照转换为starter配置
   *
   * @param provenanceCode Provenance标识符(仅用于日志/调试)
   * @param snapshot 执行上下文中捕获的注册表快照
   * @return 运行时配置,快照不完整/无效时返回{@code null}
   */
  public ProvenanceConfig convert(String provenanceCode, ProvenanceConfigSnapshot snapshot) {
    if (snapshot == null || snapshot.provenance() == null) {
      log.debug("跳过Provenance配置转换,快照缺失 provenanceCode={}", provenanceCode);
      return null;
    }

    String baseUrl = snapshot.provenance().baseUrlDefault();
    if (!StringUtils.hasText(baseUrl)) {
      log.debug("Provenance快照缺少baseUrl,回退到starter默认值 provenanceCode={}", provenanceCode);
      return null;
    }

    try {
      return new ProvenanceConfig(
          baseUrl.trim(),
          toHttpConfig(snapshot.http()),
          toPaginationConfig(snapshot.pagination()),
          toWindowOffsetConfig(snapshot.windowOffset()),
          toBatchingConfig(snapshot.batching()),
          toRetryConfig(snapshot.retry()),
          toRateLimitConfig(snapshot.rateLimit()));
    } catch (IllegalArgumentException ex) {
      log.warn("检测到无效的Provenance快照 provenanceCode={} message={}", provenanceCode, ex.getMessage());
      return null;
    }
  }

  private HttpConfig toHttpConfig(ProvenanceConfigSnapshot.HttpConfig source) {
    if (source == null) {
      return null;
    }
    Map<String, String> headers = parseHeaders(source.defaultHeadersJson());
    return new HttpConfig(
        headers,
        source.timeoutConnectMillis(),
        source.timeoutReadMillis(),
        source.timeoutTotalMillis());
  }

  private PaginationConfig toPaginationConfig(ProvenanceConfigSnapshot.PaginationConfig source) {
    if (source == null) {
      return null;
    }
    if (source.pageSizeValue() == null && source.maxPagesPerExecution() == null) {
      return null;
    }
    return new PaginationConfig(source.pageSizeValue(), source.maxPagesPerExecution());
  }

  private WindowOffsetConfig toWindowOffsetConfig(
      ProvenanceConfigSnapshot.WindowOffsetConfig source) {
    if (source == null) {
      return null;
    }
    if (allNullOrBlank(
        source.windowModeCode(),
        source.windowSizeValue(),
        source.windowSizeUnitCode(),
        source.lookbackValue(),
        source.lookbackUnitCode(),
        source.overlapValue(),
        source.overlapUnitCode(),
        source.offsetTypeCode(),
        source.maxIdsPerWindow())) {
      return null;
    }
    return new WindowOffsetConfig(
        source.windowModeCode(),
        source.windowSizeValue(),
        source.windowSizeUnitCode(),
        source.lookbackValue(),
        source.lookbackUnitCode(),
        source.overlapValue(),
        source.overlapUnitCode(),
        source.offsetTypeCode(),
        source.maxIdsPerWindow());
  }

  private BatchingConfig toBatchingConfig(ProvenanceConfigSnapshot.BatchingConfig source) {
    if (source == null) {
      return null;
    }
    if (source.detailFetchBatchSize() == null && source.maxIdsPerRequest() == null) {
      return null;
    }
    return new BatchingConfig(source.detailFetchBatchSize(), source.maxIdsPerRequest(), null);
  }

  private RetryConfig toRetryConfig(ProvenanceConfigSnapshot.RetryConfig source) {
    if (source == null) {
      return null;
    }
    if (source.maxRetryTimes() == null && source.initialDelayMillis() == null) {
      return null;
    }
    return new RetryConfig(source.maxRetryTimes(), source.initialDelayMillis());
  }

  private RateLimitConfig toRateLimitConfig(ProvenanceConfigSnapshot.RateLimitConfig source) {
    if (source == null) {
      return null;
    }
    if (source.maxConcurrentRequests() == null && source.perCredentialQpsLimit() == null) {
      return null;
    }
    return new RateLimitConfig(source.maxConcurrentRequests(), source.perCredentialQpsLimit());
  }

  private Map<String, String> parseHeaders(String headersJson) {
    if (!StringUtils.hasText(headersJson)) {
      return Map.of();
    }
    try {
      JsonNode node = JsonMapperHolder.getObjectMapper().readTree(headersJson);
      if (node == null || !node.isObject()) {
        return Map.of();
      }
      Map<String, String> headers = new LinkedHashMap<>();
      node.fields()
          .forEachRemaining(
              entry -> {
                JsonNode value = entry.getValue();
                if (value == null || value.isNull()) {
                  return;
                }
                headers.put(entry.getKey(), value.isTextual() ? value.asText() : value.toString());
              });
      return Map.copyOf(headers);
    } catch (Exception ex) {
      log.warn("解析Provenance默认headers JSON失败 message={}", ex.getMessage());
      return Map.of();
    }
  }

  private boolean allNullOrBlank(Object... parts) {
    if (parts == null) {
      return true;
    }
    for (Object part : parts) {
      if (part instanceof String str) {
        if (StringUtils.hasText(str)) {
          return false;
        }
      } else if (part != null) {
        return false;
      }
    }
    return true;
  }
}
