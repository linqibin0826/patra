package com.patra.starter.provenance.common.converter;

import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.http.HttpResilienceConfig;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Provenance 配置与 HTTP 弹性配置之间的转换器
 *
 * <p>在直接 HTTP 访问场景(非网关模式)下使用,从 Provenance 配置中提取重试、超时和速率限制设置, 转换为内置简单 HTTP 客户端使用的 {@link
 * HttpResilienceConfig}。
 *
 * <p><b>转换逻辑:</b>
 *
 * <ul>
 *   <li>从 ProvenanceConfig.retry 提取重试次数和退避时间
 *   <li>从 ProvenanceConfig.http 提取超时设置和默认头
 *   <li>从 ProvenanceConfig.rateLimit 提取QPS限制
 *   <li>对无效值应用安全默认值(如确保至少1秒超时)
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class ProvenanceConfigConverter {

  private ProvenanceConfigConverter() {
    // Utility class
  }

  /**
   * 将 ProvenanceConfig 转换为轻量级 HTTP 弹性配置
   *
   * @param config provenance 配置
   * @return 简单 HTTP 客户端使用的弹性配置,永不为 null
   */
  public static HttpResilienceConfig toHttpResilienceConfig(ProvenanceConfig config) {
    if (config == null) {
      return new HttpResilienceConfig(null, null, null, null);
    }

    Long timeoutSeconds = extractTimeoutSeconds(config);
    Integer maxRetries = extractMaxRetries(config);
    Long retryBackoffSeconds = extractRetryBackoffSeconds(config);
    Integer rateLimit = extractRateLimit(config);

    return new HttpResilienceConfig(timeoutSeconds, maxRetries, retryBackoffSeconds, rateLimit);
  }

  /**
   * 从 ProvenanceConfig 提取默认请求头
   *
   * @param config provenance 配置
   * @return 默认请求头的不可变映射,若未配置则为空,永不为 null
   */
  public static Map<String, String> extractHeaders(ProvenanceConfig config) {
    if (config == null || config.http() == null || config.http().defaultHeaders() == null) {
      return Map.of();
    }
    return Map.copyOf(config.http().defaultHeaders());
  }

  private static Long extractTimeoutSeconds(ProvenanceConfig config) {
    if (config.http() == null || config.http().timeoutTotalMillis() == null) {
      return null;
    }

    int millis = config.http().timeoutTotalMillis();
    if (millis <= 0) {
      return null;
    }

    long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
    // Ensure at least 1 second for tiny timeouts
    return Math.max(1L, seconds == 0 ? 1L : seconds);
  }

  private static Integer extractMaxRetries(ProvenanceConfig config) {
    if (config.retry() == null || config.retry().maxRetryTimes() == null) {
      return null;
    }

    int maxRetries = config.retry().maxRetryTimes();
    return maxRetries > 0 ? maxRetries : null;
  }

  private static Long extractRetryBackoffSeconds(ProvenanceConfig config) {
    if (config.retry() == null || config.retry().initialDelayMillis() == null) {
      return null;
    }

    long millis = config.retry().initialDelayMillis();
    if (millis <= 0) {
      return 0L;
    }

    long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
    // Ensure at least 1 second for tiny backoffs
    return Math.max(1L, seconds == 0 ? 1L : seconds);
  }

  private static Integer extractRateLimit(ProvenanceConfig config) {
    if (config.rateLimit() == null || config.rateLimit().perCredentialQpsLimit() == null) {
      return null;
    }

    int configuredLimit = config.rateLimit().perCredentialQpsLimit();
    return configuredLimit > 0 ? configuredLimit : null;
  }
}
