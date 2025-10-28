package com.patra.starter.provenance.boot;

import com.patra.starter.provenance.common.config.BatchingConfig;
import com.patra.starter.provenance.common.config.HttpConfig;
import com.patra.starter.provenance.common.config.PaginationConfig;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.config.RateLimitConfig;
import com.patra.starter.provenance.common.config.RetryConfig;
import com.patra.starter.provenance.common.config.WindowOffsetConfig;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Provenance starter configuration properties.
 *
 * <p>Supports global defaults plus source-specific overrides via {@code sources} map. Additional
 * provenance sources can be added without modifying code by defining new entries under {@code
 * patra.provenance.sources}.
 */
@Data
@ConfigurationProperties(prefix = "patra.provenance")
public class ProvenanceProperties {

  private static final String PUBMED = "pubmed";
  private static final String EPMC = "epmc";

  /** Whether to enable provenance clients (default true). */
  private boolean enabled = true;

  /** Shared defaults applied to all provenance sources. */
  private final SourceProperties defaults = SourceProperties.defaults();

  /** Source-specific overrides keyed by provenance code. */
  private final Map<String, SourceProperties> sources = new LinkedHashMap<>();

  public ProvenanceProperties() {
    initializeDefaults();
  }

  /**
   * Replaces the current sources with the provided map while keeping starter defaults.
   *
   * @param newSources source overrides provided by configuration binding
   */
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

  /**
   * Returns merged configuration properties for a provenance source.
   *
   * @param provenanceCode provenance identifier (e.g. {@code pubmed})
   * @return merged source properties containing defaults and overrides
   */
  public SourceProperties getConfigForSource(String provenanceCode) {
    String normalized = normalizeCode(provenanceCode);
    SourceProperties specific = sources.getOrDefault(normalized, new SourceProperties());
    return merge(defaults, specific);
  }

  /**
   * Merges runtime configuration overrides with static configuration (defaults + source-specific).
   *
   * <p>Merge priority: runtime &gt; source overrides &gt; defaults.
   *
   * @param provenanceCode provenance identifier
   * @param runtime runtime configuration supplied by registry snapshot (nullable)
   * @return final immutable configuration
   */
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
        PUBMED, SourceProperties.withBaseUrl("https://eutils.ncbi.nlm.nih.gov/entrez/eutils"));
    sources.putIfAbsent(
        EPMC, SourceProperties.withBaseUrl("https://www.ebi.ac.uk/europepmc/webservices/rest"));
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

  /** Source-specific configuration. */
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
