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
 * Converts {@link ProvenanceConfigSnapshot} records captured at planning time into runtime {@link
 * ProvenanceConfig} instances understood by starter adapters.
 *
 * <p>The converter performs a defensive transformation:
 *
 * <ul>
 *   <li>Returns {@code null} when the snapshot lacks the minimum information (e.g., baseUrl).
 *   <li>Parses JSON headers into an immutable map while ignoring malformed entries.
 *   <li>Omits empty optional sections to let downstream defaults take effect.
 * </ul>
 */
@Component
@Slf4j
public class ProvenanceConfigConverter {

  /**
   * Converts a provenance configuration snapshot into a starter configuration.
   *
   * @param provenanceCode provenance identifier (logging/debug only)
   * @param snapshot registry snapshot captured with the execution context
   * @return runtime configuration or {@code null} when the snapshot is incomplete/invalid
   */
  public ProvenanceConfig convert(String provenanceCode, ProvenanceConfigSnapshot snapshot) {
    if (snapshot == null || snapshot.provenance() == null) {
      log.debug(
          "Skipping provenance config conversion because snapshot is missing. provenanceCode={}",
          provenanceCode);
      return null;
    }

    String baseUrl = snapshot.provenance().baseUrlDefault();
    if (!StringUtils.hasText(baseUrl)) {
      log.debug(
          "Provenance snapshot missing baseUrl, fallback to starter defaults. provenanceCode={}",
          provenanceCode);
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
      log.warn(
          "Invalid provenance snapshot detected. provenanceCode={} message={}",
          provenanceCode,
          ex.getMessage());
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
      log.warn("Failed to parse provenance default headers JSON. message={}", ex.getMessage());
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
