package dev.linqibin.patra.starter.provenance.boot;

import dev.linqibin.patra.starter.provenance.common.config.BatchingConfig;
import dev.linqibin.patra.starter.provenance.common.config.HttpConfig;
import dev.linqibin.patra.starter.provenance.common.config.PaginationConfig;
import dev.linqibin.patra.starter.provenance.common.config.ProvenanceConfig;
import dev.linqibin.patra.starter.provenance.common.config.RateLimitConfig;
import dev.linqibin.patra.starter.provenance.common.config.RetryConfig;
import dev.linqibin.patra.starter.provenance.common.config.WindowOffsetConfig;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/// Provenance Starter 配置属性
///
/// 支持通过 `sources` 映射设置全局默认值和数据源特定覆盖配置。 可以通过在 `patra.provenance.sources`
/// 下定义新条目来添加额外的数据源,无需修改代码。
///
/// 配置优先级(从高到低):
///
///
@Data
@ConfigurationProperties(prefix = "patra.provenance")
public class ProvenanceProperties {

  /// 是否启用 Provenance 客户端(默认为 true)
  private boolean enabled = true;

  /// 应用于所有数据源的共享默认配置
  private final SourceProperties defaults = SourceProperties.defaults();

  /// 按数据源代码键入的特定覆盖配置映射
  private final Map<String, SourceProperties> sources = new LinkedHashMap<>();

  public ProvenanceProperties() {
    initializeDefaults();
  }

  /// 用提供的映射替换当前的数据源配置,同时保留 Starter 默认值
  ///
  /// 该方法由 Spring Boot 配置绑定机制调用,将用户配置与内置默认值合并。
  ///
  /// @param newSources 由配置绑定提供的数据源覆盖配置
  public void setSources(Map<String, SourceProperties> newSources) {
    sources.clear();
    initializeDefaults();
    if (newSources == null || newSources.isEmpty()) {
      return;
    }
    newSources.forEach(
        (code, props) -> {
          if (!StringUtils.hasText(code) || props == null) {
            return;
          }
          String normalized = code.trim().toLowerCase(Locale.ROOT);
          SourceProperties base = sources.getOrDefault(normalized, new SourceProperties());
          SourceProperties overrides = normalize(props);
          SourceProperties merged = merge(base, overrides);
          sources.put(normalized, merged);
        });
  }

  private SourceProperties normalize(SourceProperties source) {
    SourceProperties normalized = new SourceProperties();
    if (source == null) {
      return normalized;
    }
    normalized.setBaseUrl(source.getBaseUrl());
    normalized.setHttp(source.getHttp());
    normalized.setPagination(source.getPagination());
    normalized.setWindowOffset(source.getWindowOffset());
    normalized.setBatching(source.getBatching());
    normalized.setRetry(source.getRetry());
    normalized.setRateLimit(source.getRateLimit());
    return normalized;
  }

  /// 返回指定数据源的合并配置属性
  ///
  /// 合并全局默认值和数据源特定覆盖配置。
  ///
  /// @param provenanceCode 数据源标识符(如 `pubmed`)
  /// @return 包含默认值和覆盖配置的合并属性对象
  public SourceProperties getConfigForSource(String provenanceCode) {
    String normalized = normalizeCode(provenanceCode);
    SourceProperties specific = sources.getOrDefault(normalized, new SourceProperties());
    return merge(defaults, specific);
  }

  /// 合并运行时配置覆盖与静态配置(默认值 + 数据源特定配置)
  ///
  /// 合并优先级: 运行时 &gt; 数据源覆盖 &gt; 默认值
  ///
  /// @param provenanceCode 数据源标识符
  /// @param runtime 由 Registry 快照提供的运行时配置(可为 null)
  /// @return 最终的不可变配置对象
  public ProvenanceConfig mergeWithRuntime(String provenanceCode, ProvenanceConfig runtime) {
    SourceProperties mergedSource = getConfigForSource(provenanceCode);
    ProvenanceConfig baseConfig = mergedSource.toProvenanceConfig();
    if (runtime == null) {
      return baseConfig;
    }
    return new ProvenanceConfig(
        firstNonBlank(runtime.baseUrl(), baseConfig.baseUrl()),
        mergeHttpConfig(runtime.http(), baseConfig.http()),
        mergePaginationConfig(runtime.pagination(), baseConfig.pagination()),
        mergeWindowOffsetConfig(runtime.windowOffset(), baseConfig.windowOffset()),
        mergeBatchingConfig(runtime.batching(), baseConfig.batching()),
        mergeRetryConfig(runtime.retry(), baseConfig.retry()),
        mergeRateLimitConfig(runtime.rateLimit(), baseConfig.rateLimit()));
  }

  private void initializeDefaults() {
    sources.putIfAbsent(
        ProvenanceCode.PUBMED.lowerCaseCode(),
        SourceProperties.withBaseUrl("https://eutils.ncbi.nlm.nih.gov/entrez/eutils"));
    sources.putIfAbsent(
        ProvenanceCode.EPMC.lowerCaseCode(),
        SourceProperties.withBaseUrl("https://www.ebi.ac.uk/europepmc/webservices/rest"));
  }

  private SourceProperties merge(SourceProperties lower, SourceProperties higher) {
    SourceProperties merged = new SourceProperties();
    merged.setBaseUrl(firstNonBlank(higher.getBaseUrl(), lower.getBaseUrl()));
    merged.setHttp(mergeHttp(lower.getHttp(), higher.getHttp()));
    merged.setPagination(mergePagination(lower.getPagination(), higher.getPagination()));
    merged.setWindowOffset(mergeWindowOffset(lower.getWindowOffset(), higher.getWindowOffset()));
    merged.setBatching(mergeBatching(lower.getBatching(), higher.getBatching()));
    merged.setRetry(mergeRetry(lower.getRetry(), higher.getRetry()));
    merged.setRateLimit(mergeRateLimit(lower.getRateLimit(), higher.getRateLimit()));
    return merged;
  }

  private HttpConfig mergeHttpConfig(HttpConfig runtime, HttpConfig fallback) {
    if (runtime == null) {
      return fallback;
    }
    Map<String, String> headers = new LinkedHashMap<>();
    if (fallback != null && fallback.defaultHeaders() != null) {
      headers.putAll(fallback.defaultHeaders());
    }
    headers.putAll(runtime.defaultHeaders());
    return new HttpConfig(
        headers,
        firstNonNull(
            runtime.timeoutConnectMillis(),
            fallback != null ? fallback.timeoutConnectMillis() : null),
        firstNonNull(
            runtime.timeoutReadMillis(), fallback != null ? fallback.timeoutReadMillis() : null),
        firstNonNull(
            runtime.timeoutTotalMillis(), fallback != null ? fallback.timeoutTotalMillis() : null));
  }

  private PaginationConfig mergePaginationConfig(
      PaginationConfig runtime, PaginationConfig fallback) {
    if (runtime == null) {
      return fallback;
    }
    if (fallback == null) {
      return runtime;
    }
    return new PaginationConfig(
        firstNonNull(runtime.pageSizeValue(), fallback.pageSizeValue()),
        firstNonNull(runtime.maxPagesPerExecution(), fallback.maxPagesPerExecution()));
  }

  private WindowOffsetConfig mergeWindowOffsetConfig(
      WindowOffsetConfig runtime, WindowOffsetConfig fallback) {
    if (runtime == null) {
      return fallback;
    }
    if (fallback == null) {
      return runtime;
    }
    return new WindowOffsetConfig(
        firstNonBlank(runtime.windowModeCode(), fallback.windowModeCode()),
        firstNonNull(runtime.windowSizeValue(), fallback.windowSizeValue()),
        firstNonBlank(runtime.windowSizeUnitCode(), fallback.windowSizeUnitCode()),
        firstNonNull(runtime.lookbackValue(), fallback.lookbackValue()),
        firstNonBlank(runtime.lookbackUnitCode(), fallback.lookbackUnitCode()),
        firstNonNull(runtime.overlapValue(), fallback.overlapValue()),
        firstNonBlank(runtime.overlapUnitCode(), fallback.overlapUnitCode()),
        firstNonBlank(runtime.offsetTypeCode(), fallback.offsetTypeCode()),
        firstNonNull(runtime.maxIdsPerWindow(), fallback.maxIdsPerWindow()));
  }

  private BatchingConfig mergeBatchingConfig(BatchingConfig runtime, BatchingConfig fallback) {
    if (runtime == null) {
      return fallback;
    }
    if (fallback == null) {
      return runtime;
    }
    return new BatchingConfig(
        firstNonNull(runtime.detailFetchBatchSize(), fallback.detailFetchBatchSize()),
        firstNonNull(runtime.maxIdsPerRequest(), fallback.maxIdsPerRequest()),
        firstNonNull(runtime.epostThreshold(), fallback.epostThreshold()));
  }

  private RetryConfig mergeRetryConfig(RetryConfig runtime, RetryConfig fallback) {
    if (runtime == null) {
      return fallback;
    }
    if (fallback == null) {
      return runtime;
    }
    return new RetryConfig(
        firstNonNull(runtime.maxRetryTimes(), fallback.maxRetryTimes()),
        firstNonNull(runtime.initialDelayMillis(), fallback.initialDelayMillis()));
  }

  private RateLimitConfig mergeRateLimitConfig(RateLimitConfig runtime, RateLimitConfig fallback) {
    if (runtime == null) {
      return fallback;
    }
    if (fallback == null) {
      return runtime;
    }
    return new RateLimitConfig(
        firstNonNull(runtime.maxConcurrentRequests(), fallback.maxConcurrentRequests()),
        firstNonNull(runtime.perCredentialQpsLimit(), fallback.perCredentialQpsLimit()));
  }

  private HttpConfigProperties mergeHttp(HttpConfigProperties lower, HttpConfigProperties higher) {
    HttpConfigProperties merged = new HttpConfigProperties();
    merged.setDefaultHeaders(mergeHeaders(lower, higher));
    merged.setTimeoutConnectMillis(
        firstNonNull(higher.getTimeoutConnectMillis(), lower.getTimeoutConnectMillis()));
    merged.setTimeoutReadMillis(
        firstNonNull(higher.getTimeoutReadMillis(), lower.getTimeoutReadMillis()));
    merged.setTimeoutTotalMillis(
        firstNonNull(higher.getTimeoutTotalMillis(), lower.getTimeoutTotalMillis()));
    return merged;
  }

  private Map<String, String> mergeHeaders(
      HttpConfigProperties lower, HttpConfigProperties higher) {
    Map<String, String> merged = new LinkedHashMap<>();
    if (lower.getDefaultHeaders() != null) {
      merged.putAll(lower.getDefaultHeaders());
    }
    if (higher.getDefaultHeaders() != null) {
      merged.putAll(higher.getDefaultHeaders());
    }
    return merged;
  }

  private PaginationProperties mergePagination(
      PaginationProperties lower, PaginationProperties higher) {
    PaginationProperties merged = new PaginationProperties();
    merged.setPageSizeValue(firstNonNull(higher.getPageSizeValue(), lower.getPageSizeValue()));
    merged.setMaxPagesPerExecution(
        firstNonNull(higher.getMaxPagesPerExecution(), lower.getMaxPagesPerExecution()));
    return merged;
  }

  private WindowOffsetProperties mergeWindowOffset(
      WindowOffsetProperties lower, WindowOffsetProperties higher) {
    WindowOffsetProperties merged = new WindowOffsetProperties();
    merged.setWindowModeCode(firstNonBlank(higher.getWindowModeCode(), lower.getWindowModeCode()));
    merged.setWindowSizeValue(
        firstNonNull(higher.getWindowSizeValue(), lower.getWindowSizeValue()));
    merged.setWindowSizeUnitCode(
        firstNonBlank(higher.getWindowSizeUnitCode(), lower.getWindowSizeUnitCode()));
    merged.setLookbackValue(firstNonNull(higher.getLookbackValue(), lower.getLookbackValue()));
    merged.setLookbackUnitCode(
        firstNonBlank(higher.getLookbackUnitCode(), lower.getLookbackUnitCode()));
    merged.setOverlapValue(firstNonNull(higher.getOverlapValue(), lower.getOverlapValue()));
    merged.setOverlapUnitCode(
        firstNonBlank(higher.getOverlapUnitCode(), lower.getOverlapUnitCode()));
    merged.setOffsetTypeCode(firstNonBlank(higher.getOffsetTypeCode(), lower.getOffsetTypeCode()));
    merged.setMaxIdsPerWindow(
        firstNonNull(higher.getMaxIdsPerWindow(), lower.getMaxIdsPerWindow()));
    return merged;
  }

  private BatchingProperties mergeBatching(BatchingProperties lower, BatchingProperties higher) {
    BatchingProperties merged = new BatchingProperties();
    merged.setDetailFetchBatchSize(
        firstNonNull(higher.getDetailFetchBatchSize(), lower.getDetailFetchBatchSize()));
    merged.setMaxIdsPerRequest(
        firstNonNull(higher.getMaxIdsPerRequest(), lower.getMaxIdsPerRequest()));
    merged.setEpostThreshold(firstNonNull(higher.getEpostThreshold(), lower.getEpostThreshold()));
    return merged;
  }

  private RetryProperties mergeRetry(RetryProperties lower, RetryProperties higher) {
    RetryProperties merged = new RetryProperties();
    merged.setMaxRetryTimes(firstNonNull(higher.getMaxRetryTimes(), lower.getMaxRetryTimes()));
    merged.setInitialDelayMillis(
        firstNonNull(higher.getInitialDelayMillis(), lower.getInitialDelayMillis()));
    return merged;
  }

  private RateLimitProperties mergeRateLimit(
      RateLimitProperties lower, RateLimitProperties higher) {
    RateLimitProperties merged = new RateLimitProperties();
    merged.setMaxConcurrentRequests(
        firstNonNull(higher.getMaxConcurrentRequests(), lower.getMaxConcurrentRequests()));
    merged.setPerCredentialQpsLimit(
        firstNonNull(higher.getPerCredentialQpsLimit(), lower.getPerCredentialQpsLimit()));
    return merged;
  }

  private String normalizeCode(String code) {
    return StringUtils.hasText(code) ? code.trim().toLowerCase(Locale.ROOT) : "";
  }

  private <T> T firstNonNull(T primary, T secondary) {
    return primary != null ? primary : secondary;
  }

  private String firstNonBlank(String primary, String secondary) {
    return StringUtils.hasText(primary) ? primary : secondary;
  }

  /// 数据源特定配置属性
  ///
  /// 可变的配置属性类,用于 Spring Boot 配置绑定。 提供 {@link #toProvenanceConfig()} 方法转换为不可变的 {@link
  /// ProvenanceConfig} 对象。
  @Data
  public static class SourceProperties {

    private String baseUrl;
    private HttpConfigProperties http;
    private PaginationProperties pagination;
    private WindowOffsetProperties windowOffset;
    private BatchingProperties batching;
    private RetryProperties retry;
    private RateLimitProperties rateLimit;

    public SourceProperties() {
      this.http = new HttpConfigProperties();
      this.pagination = new PaginationProperties();
      this.windowOffset = new WindowOffsetProperties();
      this.batching = new BatchingProperties();
      this.retry = new RetryProperties();
      this.rateLimit = new RateLimitProperties();
    }

    public static SourceProperties defaults() {
      SourceProperties props = new SourceProperties();
      props.http.setTimeoutConnectMillis(10_000);
      props.http.setTimeoutReadMillis(30_000);
      props.pagination.setPageSizeValue(100);
      props.batching.setEpostThreshold(200);
      props.retry.setMaxRetryTimes(3);
      props.retry.setInitialDelayMillis(1_000);
      props.rateLimit.setMaxConcurrentRequests(10);
      props.rateLimit.setPerCredentialQpsLimit(5);
      return props;
    }

    public static SourceProperties withBaseUrl(String baseUrl) {
      SourceProperties props = defaults();
      props.setBaseUrl(baseUrl);
      return props;
    }

    public ProvenanceConfig toProvenanceConfig() {
      if (!StringUtils.hasText(baseUrl)) {
        throw new IllegalStateException("Base URL must be configured for provenance source");
      }
      return new ProvenanceConfig(
          baseUrl.trim(),
          http.toHttpConfig(),
          pagination.toPaginationConfig(),
          windowOffset.toWindowOffsetConfig(),
          batching.toBatchingConfig(),
          retry.toRetryConfig(),
          rateLimit.toRateLimitConfig());
    }

    public void setHttp(HttpConfigProperties http) {
      this.http = http != null ? http : new HttpConfigProperties();
    }

    public void setPagination(PaginationProperties pagination) {
      this.pagination = pagination != null ? pagination : new PaginationProperties();
    }

    public void setWindowOffset(WindowOffsetProperties windowOffset) {
      this.windowOffset = windowOffset != null ? windowOffset : new WindowOffsetProperties();
    }

    public void setBatching(BatchingProperties batching) {
      this.batching = batching != null ? batching : new BatchingProperties();
    }

    public void setRetry(RetryProperties retry) {
      this.retry = retry != null ? retry : new RetryProperties();
    }

    public void setRateLimit(RateLimitProperties rateLimit) {
      this.rateLimit = rateLimit != null ? rateLimit : new RateLimitProperties();
    }
  }

  @Data
  public static class HttpConfigProperties {
    private Map<String, String> defaultHeaders = new LinkedHashMap<>();
    private Integer timeoutConnectMillis;
    private Integer timeoutReadMillis;
    private Integer timeoutTotalMillis;

    public HttpConfig toHttpConfig() {
      return new HttpConfig(
          defaultHeaders, timeoutConnectMillis, timeoutReadMillis, timeoutTotalMillis);
    }

    public void setDefaultHeaders(Map<String, String> defaultHeaders) {
      this.defaultHeaders =
          defaultHeaders == null ? new LinkedHashMap<>() : new LinkedHashMap<>(defaultHeaders);
    }
  }

  @Data
  public static class PaginationProperties {
    private Integer pageSizeValue;
    private Integer maxPagesPerExecution;

    public PaginationConfig toPaginationConfig() {
      if (pageSizeValue == null && maxPagesPerExecution == null) {
        return null;
      }
      return new PaginationConfig(pageSizeValue, maxPagesPerExecution);
    }
  }

  @Data
  public static class WindowOffsetProperties {
    private String windowModeCode;
    private Integer windowSizeValue;
    private String windowSizeUnitCode;
    private Integer lookbackValue;
    private String lookbackUnitCode;
    private Integer overlapValue;
    private String overlapUnitCode;
    private String offsetTypeCode;
    private Integer maxIdsPerWindow;

    public WindowOffsetConfig toWindowOffsetConfig() {
      if (!hasAnyValue()) {
        return null;
      }
      return new WindowOffsetConfig(
          windowModeCode,
          windowSizeValue,
          windowSizeUnitCode,
          lookbackValue,
          lookbackUnitCode,
          overlapValue,
          overlapUnitCode,
          offsetTypeCode,
          maxIdsPerWindow);
    }

    private boolean hasAnyValue() {
      return StringUtils.hasText(windowModeCode)
          || windowSizeValue != null
          || StringUtils.hasText(windowSizeUnitCode)
          || lookbackValue != null
          || StringUtils.hasText(lookbackUnitCode)
          || overlapValue != null
          || StringUtils.hasText(overlapUnitCode)
          || StringUtils.hasText(offsetTypeCode)
          || maxIdsPerWindow != null;
    }
  }

  @Data
  public static class BatchingProperties {
    private Integer detailFetchBatchSize;
    private Integer maxIdsPerRequest;
    private Integer epostThreshold;

    public BatchingConfig toBatchingConfig() {
      if (detailFetchBatchSize == null && maxIdsPerRequest == null && epostThreshold == null) {
        return null;
      }
      return new BatchingConfig(detailFetchBatchSize, maxIdsPerRequest, epostThreshold);
    }
  }

  @Data
  public static class RetryProperties {
    private Integer maxRetryTimes;
    private Integer initialDelayMillis;

    public RetryConfig toRetryConfig() {
      if (maxRetryTimes == null && initialDelayMillis == null) {
        return null;
      }
      return new RetryConfig(maxRetryTimes, initialDelayMillis);
    }
  }

  @Data
  public static class RateLimitProperties {
    private Integer maxConcurrentRequests;
    private Integer perCredentialQpsLimit;

    public RateLimitConfig toRateLimitConfig() {
      if (maxConcurrentRequests == null && perCredentialQpsLimit == null) {
        return null;
      }
      return new RateLimitConfig(maxConcurrentRequests, perCredentialQpsLimit);
    }
  }
}
