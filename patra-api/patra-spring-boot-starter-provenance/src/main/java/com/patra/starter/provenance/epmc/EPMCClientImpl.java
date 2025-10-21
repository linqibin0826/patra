package com.patra.starter.provenance.epmc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.starter.provenance.common.config.DefaultConfigProvider;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.converter.ProvenanceConfigConverter;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.common.http.HttpResilienceConfig;
import com.patra.starter.provenance.common.http.SimpleHttpClient;
import com.patra.starter.provenance.common.metrics.ProvenanceMetrics;
import com.patra.starter.provenance.epmc.model.request.SearchRequest;
import com.patra.starter.provenance.epmc.model.response.SearchResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Europe PMC client implementation calling provider APIs directly via HTTP.
 *
 * <p>Handles configuration prioritisation, optional Micrometer instrumentation and defensive
 * response parsing.
 */
@Slf4j
public class EPMCClientImpl implements EPMCClient {

  private static final ProvenanceCode PROVENANCE = ProvenanceCode.EPMC;

  private final SimpleHttpClient httpClient;
  private final DefaultConfigProvider configProvider;
  private final ObjectMapper objectMapper;
  private final ProvenanceMetrics metrics;

  public EPMCClientImpl(
      SimpleHttpClient httpClient,
      DefaultConfigProvider configProvider,
      ObjectMapper objectMapper,
      ProvenanceMetrics metrics) {
    this.httpClient = httpClient;
    this.configProvider = configProvider;
    this.objectMapper = objectMapper;
    this.metrics = metrics;
  }

  /** {@inheritDoc} */
  @Override
  public SearchResponse search(SearchRequest request) {
    return search(request, null);
  }

  /** {@inheritDoc} */
  @Override
  public SearchResponse search(SearchRequest request, ProvenanceConfig config) {
    if (metrics != null) {
      // Wrap the call to capture duration and success/failure counters when Micrometer is present.
      return metrics.recordApiCall(PROVENANCE, "search", () -> executeSearch(request, config));
    }
    return executeSearch(request, config);
  }

  private SearchResponse executeSearch(SearchRequest request, ProvenanceConfig config) {
    ProvenanceConfig finalConfig = config != null ? config : configProvider.getEPMCDefaultConfig();

    String baseUrl = finalConfig.baseUrl();
    String path = "/search";
    java.util.Map<String, String> queryParams = request.toQueryParams();
    java.util.Map<String, String> headers = ProvenanceConfigConverter.extractHeaders(finalConfig);
    HttpResilienceConfig rc = ProvenanceConfigConverter.toHttpResilienceConfig(finalConfig);

    String body = httpClient.get(baseUrl, path, queryParams, headers, rc);
    try {
      JsonNode root = objectMapper.readTree(body);
      return SearchResponse.from(root);
    } catch (Exception ex) {
      throw new ProvenanceClientException(
          PROVENANCE.getCode(), "search", null, null, body, "Failed to parse JSON response", ex);
    }
  }
}
