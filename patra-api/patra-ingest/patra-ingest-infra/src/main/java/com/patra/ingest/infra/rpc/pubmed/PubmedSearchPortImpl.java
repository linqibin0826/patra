package com.patra.ingest.infra.rpc.pubmed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.json.JsonMapperHolder;
import com.patra.common.util.HashUtils;
import com.patra.ingest.domain.exception.BatchPlanningException;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.PlanMetadata;
import com.patra.ingest.domain.port.PubmedSearchPort;
import com.patra.starter.provenance.common.config.BatchingConfig;
import com.patra.starter.provenance.common.config.HttpConfig;
import com.patra.starter.provenance.common.config.PaginationConfig;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.config.RateLimitConfig;
import com.patra.starter.provenance.common.config.RetryConfig;
import com.patra.starter.provenance.common.config.WindowOffsetConfig;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.pubmed.PubMedClient;
import com.patra.starter.provenance.pubmed.model.request.ESearchRequest;
import com.patra.starter.provenance.pubmed.model.response.ESearchResponse;
import com.patra.starter.provenance.pubmed.request.PubMedESearchRequestAssembler;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Infra adapter for {@link PubmedSearchPort} using {@link PubMedClient}. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PubmedSearchPortImpl implements PubmedSearchPort {

  private final PubMedClient pubMedClient;
  private static final PubMedESearchRequestAssembler ASSEMBLER =
      new PubMedESearchRequestAssembler();

  @Override
  public PlanMetadata preparePlanMetadata(
      String query, JsonNode params, ProvenanceConfigSnapshot provenanceConfigSnapshot) {
    try {
      JsonNode enrichedParams = addUseHistory(params);
      ESearchRequest request = ASSEMBLER.buildList(enrichedParams);
      String termHash = safeHash(request.term());
      ProvenanceConfig config = toProvenanceConfig(provenanceConfigSnapshot);
      ESearchResponse response =
          config != null ? pubMedClient.esearch(request, config) : pubMedClient.esearch(request);
      if (response == null || response.result() == null) {
        log.warn("pubmed esearch metadata returned null termHash={}", termHash);
        return PlanMetadata.empty();
      }

      ESearchResponse.Result result = response.result();
      int count = Math.max(result.count(), 0);
      String webEnv = result.webEnv();
      String queryKey = result.queryKey();

      boolean hasWebEnv = StringUtils.hasText(webEnv);
      boolean hasQueryKey = StringUtils.hasText(queryKey);

      log.info(
          "pubmed esearch metadata termHash={} count={} webEnv={} queryKey={}",
          termHash,
          count,
          hasWebEnv ? "present" : "absent",
          hasQueryKey ? "present" : "absent");

      if (hasWebEnv && !hasQueryKey) {
        log.warn(
            "pubmed esearch returned WebEnv without QueryKey termHash={} count={}",
            termHash,
            count);
      }

      return new PlanMetadata(count, webEnv, queryKey);
    } catch (ProvenanceClientException ex) {
      String msg = String.format("PubMed metadata lookup failed: %s", ex.getMessage());
      log.error("{} termHash={}", msg, safeHash(query), ex);
      throw new BatchPlanningException(msg, ex);
    } catch (Exception ex) {
      String msg = String.format("PubMed metadata lookup unexpected error: %s", ex.getMessage());
      log.error("{} termHash={}", msg, safeHash(query), ex);
      throw new BatchPlanningException(msg, ex);
    }
  }

  private ProvenanceConfig toProvenanceConfig(ProvenanceConfigSnapshot snapshot) {
    if (snapshot == null || snapshot.provenance() == null) {
      return null;
    }
    String baseUrl = snapshot.provenance().baseUrlDefault();
    if (!StringUtils.hasText(baseUrl)) {
      log.debug(
          "provenance snapshot missing baseUrl, fallback to default config. provenanceId={}",
          snapshot.provenance().id());
      return null;
    }

    HttpConfig http = toHttpConfig(snapshot.http());
    PaginationConfig pagination = toPaginationConfig(snapshot.pagination());
    WindowOffsetConfig windowOffset = toWindowOffsetConfig(snapshot.windowOffset());
    BatchingConfig batching = toBatchingConfig(snapshot.batching());
    RetryConfig retry = toRetryConfig(snapshot.retry());
    RateLimitConfig rateLimit = toRateLimitConfig(snapshot.rateLimit());

    try {
      return new ProvenanceConfig(
          baseUrl.trim(), http, pagination, windowOffset, batching, retry, rateLimit);
    } catch (IllegalArgumentException ex) {
      log.warn(
          "failed to build provenance config override, fallback to default config. provenanceId={}",
          snapshot.provenance().id(),
          ex);
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
    if (source.windowModeCode() == null
        && source.windowSizeValue() == null
        && source.windowSizeUnitCode() == null
        && source.lookbackValue() == null
        && source.lookbackUnitCode() == null
        && source.overlapValue() == null
        && source.overlapUnitCode() == null
        && source.offsetTypeCode() == null
        && source.maxIdsPerWindow() == null) {
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
    return new BatchingConfig(source.detailFetchBatchSize(), source.maxIdsPerRequest());
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
      if (!node.isObject()) {
        return Map.of();
      }
      Map<String, String> headers = new LinkedHashMap<>();
      node.fields()
          .forEachRemaining(
              entry -> {
                JsonNode value = entry.getValue();
                if (value != null && !value.isNull()) {
                  headers.put(
                      entry.getKey(), value.isTextual() ? value.asText() : value.toString());
                }
              });
      return headers;
    } catch (Exception ex) {
      log.warn(
          "Failed to parse provenance default headers JSON, ignoring overrides. length={}",
          headersJson.length(),
          ex);
      return Map.of();
    }
  }

  private JsonNode addUseHistory(JsonNode params) {
    ObjectNode node;
    if (params == null || params.isNull()) {
      node = JsonMapperHolder.getObjectMapper().createObjectNode();
    } else if (params.isObject()) {
      node = ((ObjectNode) params).deepCopy();
    } else {
      throw new IllegalArgumentException("params must be an object node");
    }

    if (!node.has("usehistory")) {
      node.put("usehistory", "y");
    }
    node.put("retmax", 0);

    return node;
  }

  private static String safeHash(String s) {
    if (s == null) {
      return "null";
    }
    return HashUtils.sha256Hex(s);
  }
}
