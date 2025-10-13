package com.patra.starter.provenance.epmc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.egress.api.client.EgressGatewayClient;
import com.patra.egress.api.dto.ExternalCallRequestDTO;
import com.patra.egress.api.dto.ExternalCallResponseDTO;
import com.patra.egress.api.dto.ResponseEnvelopeDTO;
import com.patra.starter.provenance.common.config.DefaultConfigProvider;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.common.gateway.GatewayRequestBuilder;
import com.patra.starter.provenance.common.metrics.ProvenanceMetrics;
import com.patra.starter.provenance.epmc.model.request.SearchRequest;
import com.patra.starter.provenance.epmc.model.response.SearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * Europe PMC client implementation delegating HTTP calls to the egress gateway.
 *
 * <p>Handles configuration prioritisation, optional Micrometer instrumentation and defensive
 * response parsing.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class EPMCClientImpl implements EPMCClient {

  private static final ProvenanceCode PROVENANCE = ProvenanceCode.EPMC;

  private final EgressGatewayClient gatewayClient;
  private final GatewayRequestBuilder requestBuilder;
  private final DefaultConfigProvider configProvider;
  private final ObjectMapper objectMapper;
  private final ProvenanceMetrics metrics;

  public EPMCClientImpl(
      EgressGatewayClient gatewayClient,
      GatewayRequestBuilder requestBuilder,
      DefaultConfigProvider configProvider,
      ObjectMapper objectMapper,
      ProvenanceMetrics metrics) {
    this.gatewayClient = gatewayClient;
    this.requestBuilder = requestBuilder;
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
    ExternalCallRequestDTO gatewayRequest =
        requestBuilder.build(finalConfig.baseUrl(), "/search", request, finalConfig);

    ExternalCallResponseDTO response = invokeGateway("search", gatewayRequest);
    ResponseEnvelopeDTO envelope = response.envelope();
    try {
      JsonNode root = objectMapper.readTree(envelope.body());
      return SearchResponse.from(root);
    } catch (Exception ex) {
      throw new ProvenanceClientException(
          PROVENANCE.getCode(),
          "search",
          envelope.statusCode(),
          response.traceId(),
          envelope.body(),
          "Failed to parse JSON response",
          ex);
    }
  }

  private ExternalCallResponseDTO invokeGateway(String apiName, ExternalCallRequestDTO request) {
    ExternalCallResponseDTO response = gatewayClient.call(request);
    if (response == null) {
      throw new ProvenanceClientException(
          PROVENANCE.getCode(), apiName, "Gateway returned null response");
    }
    ResponseEnvelopeDTO envelope = response.envelope();
    if (envelope == null) {
      throw new ProvenanceClientException(
          PROVENANCE.getCode(),
          apiName,
          null,
          response.traceId(),
          null,
          "Gateway returned empty envelope",
          null);
    }
    if (!envelope.success()) {
      throw new ProvenanceClientException(
          PROVENANCE.getCode(),
          apiName,
          envelope.statusCode(),
          response.traceId(),
          envelope.body(),
          "Gateway reported failure status",
          null);
    }
    if (!StringUtils.hasText(envelope.body())) {
      throw new ProvenanceClientException(
          PROVENANCE.getCode(),
          apiName,
          envelope.statusCode(),
          response.traceId(),
          null,
          "Gateway returned empty body",
          null);
    }
    return response;
  }
}
