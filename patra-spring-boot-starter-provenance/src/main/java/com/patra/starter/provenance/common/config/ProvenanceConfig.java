package com.patra.starter.provenance.common.config;

import java.util.Map;
import org.springframework.util.StringUtils;

/// 数据源配置记录
/// 
/// 封装单个 Provenance 数据源(如 PubMed、EPMC)的完整配置信息,包括 HTTP 客户端设置、 分页参数、时间窗口、批处理、重试策略和限流配置等。该配置对象由
/// {@link DefaultConfigProvider} 从 {@link com.patra.starter.provenance.boot.ProvenanceProperties}
/// 中构建。
/// 
/// @param baseUrl 上游数据源的规范基础 URL,不包含尾部斜杠
/// @param http HTTP 客户端设置,包括超时时间和默认请求头
/// @param pagination 默认分页提示参数,供调用者使用
/// @param windowOffset 增量采集时使用的滑动时间窗口默认配置
/// @param batching 批量获取操作的请求批处理参数
/// @param retry 转发给网关的重试策略覆盖配置
/// @param rateLimit 基于凭证的限流提示配置
/// @author linqibin
/// @since 0.1.0
public record ProvenanceConfig(
    String baseUrl,
    HttpConfig http,
    PaginationConfig pagination,
    WindowOffsetConfig windowOffset,
    BatchingConfig batching,
    RetryConfig retry,
    RateLimitConfig rateLimit) {
  public ProvenanceConfig {
    if (!StringUtils.hasText(baseUrl)) {
      throw new IllegalArgumentException("baseUrl cannot be null or blank");
    }
    baseUrl = trimTrailingSlash(baseUrl.trim());
    http = http != null ? http : new HttpConfig(Map.of(), null, null, null);
  }

  private String trimTrailingSlash(String url) {
    if (url.endsWith("/")) {
      return url.substring(0, url.length() - 1);
    }
    return url;
  }
}
