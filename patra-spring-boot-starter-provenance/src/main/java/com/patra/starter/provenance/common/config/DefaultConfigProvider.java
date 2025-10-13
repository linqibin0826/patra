package com.patra.starter.provenance.common.config;

import com.patra.starter.provenance.boot.ProvenanceProperties;
import com.patra.starter.provenance.boot.ProvenanceProperties.BatchingProperties;
import com.patra.starter.provenance.boot.ProvenanceProperties.HttpConfigProperties;
import com.patra.starter.provenance.boot.ProvenanceProperties.PaginationProperties;
import com.patra.starter.provenance.boot.ProvenanceProperties.RateLimitProperties;
import com.patra.starter.provenance.boot.ProvenanceProperties.RetryProperties;
import com.patra.starter.provenance.boot.ProvenanceProperties.SourceProperties;
import com.patra.starter.provenance.boot.ProvenanceProperties.WindowOffsetProperties;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

/**
 * Default configuration provider.
 *
 * <p>Builds ProvenanceConfig objects from {@link ProvenanceProperties}. The provider guarantees
 * non-null baseUrl and HTTP configuration so that caller logic can rely on them without additional
 * null guards.
 *
 * @author linqibin
 * @since 0.1.0
 */
public class DefaultConfigProvider {

  private final ProvenanceProperties properties;

  public DefaultConfigProvider(ProvenanceProperties properties) {
    this.properties = Objects.requireNonNull(properties, "properties cannot be null");
  }

  /**
   * Get default configuration for PubMed source.
   *
   * @return immutable default configuration for PubMed
   */
  public ProvenanceConfig getPubMedDefaultConfig() {
    return buildConfig(properties.getPubmed(), "pubmed");
  }

  /**
   * Get default configuration for Europe PMC source.
   *
   * @return immutable default configuration for Europe PMC
   */
  public ProvenanceConfig getEPMCDefaultConfig() {
    return buildConfig(properties.getEpmc(), "epmc");
  }

  private ProvenanceConfig buildConfig(SourceProperties source, String code) {
    if (source == null) {
      throw new IllegalStateException("Missing configuration for provenance source: " + code);
    }

    String baseUrl = normalizeBaseUrl(source.getBaseUrl(), code);
    HttpConfig httpConfig = toHttpConfig(source.getHttp());

    return new ProvenanceConfig(
        baseUrl,
        httpConfig,
        toPaginationConfig(source.getPagination()),
        toWindowOffsetConfig(source.getWindowOffset()),
        toBatchingConfig(source.getBatching()),
        toRetryConfig(source.getRetry()),
        toRateLimitConfig(source.getRateLimit()));
  }

  private String normalizeBaseUrl(String rawBaseUrl, String code) {
    if (!StringUtils.hasText(rawBaseUrl)) {
      throw new IllegalStateException("Base URL must be provided for provenance source: " + code);
    }
    String sanitized = rawBaseUrl.trim();
    if (sanitized.endsWith("/")) {
      sanitized = sanitized.substring(0, sanitized.length() - 1);
    }
    return sanitized;
  }

  private HttpConfig toHttpConfig(HttpConfigProperties props) {
    HttpConfigProperties properties = props != null ? props : new HttpConfigProperties();
    Map<String, String> headers =
        properties.getDefaultHeaders() == null
            ? Map.of()
            : Map.copyOf(properties.getDefaultHeaders());
    return new HttpConfig(
        headers,
        properties.getTimeoutConnectMillis(),
        properties.getTimeoutReadMillis(),
        properties.getTimeoutTotalMillis());
  }

  private PaginationConfig toPaginationConfig(PaginationProperties props) {
    if (props == null) {
      return null;
    }
    if (props.getPageSizeValue() == null && props.getMaxPagesPerExecution() == null) {
      return null;
    }
    return new PaginationConfig(props.getPageSizeValue(), props.getMaxPagesPerExecution());
  }

  private WindowOffsetConfig toWindowOffsetConfig(WindowOffsetProperties props) {
    if (props == null) {
      return null;
    }
    if (props.getWindowModeCode() == null
        && props.getWindowSizeValue() == null
        && props.getWindowSizeUnitCode() == null
        && props.getLookbackValue() == null
        && props.getLookbackUnitCode() == null
        && props.getOverlapValue() == null
        && props.getOverlapUnitCode() == null
        && props.getOffsetTypeCode() == null
        && props.getMaxIdsPerWindow() == null) {
      return null;
    }
    return new WindowOffsetConfig(
        props.getWindowModeCode(),
        props.getWindowSizeValue(),
        props.getWindowSizeUnitCode(),
        props.getLookbackValue(),
        props.getLookbackUnitCode(),
        props.getOverlapValue(),
        props.getOverlapUnitCode(),
        props.getOffsetTypeCode(),
        props.getMaxIdsPerWindow());
  }

  private BatchingConfig toBatchingConfig(BatchingProperties props) {
    if (props == null) {
      return null;
    }
    if (props.getDetailFetchBatchSize() == null && props.getMaxIdsPerRequest() == null) {
      return null;
    }
    return new BatchingConfig(props.getDetailFetchBatchSize(), props.getMaxIdsPerRequest());
  }

  private RetryConfig toRetryConfig(RetryProperties props) {
    if (props == null) {
      return null;
    }
    if (props.getMaxRetryTimes() == null && props.getInitialDelayMillis() == null) {
      return null;
    }
    return new RetryConfig(props.getMaxRetryTimes(), props.getInitialDelayMillis());
  }

  private RateLimitConfig toRateLimitConfig(RateLimitProperties props) {
    if (props == null) {
      return null;
    }
    if (props.getMaxConcurrentRequests() == null && props.getPerCredentialQpsLimit() == null) {
      return null;
    }
    return new RateLimitConfig(props.getMaxConcurrentRequests(), props.getPerCredentialQpsLimit());
  }
}
