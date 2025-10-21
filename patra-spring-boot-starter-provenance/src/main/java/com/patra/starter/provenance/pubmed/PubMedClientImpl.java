package com.patra.starter.provenance.pubmed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.egress.api.client.EgressGatewayClient;
import com.patra.egress.api.dto.ExternalCallRequestDTO;
import com.patra.egress.api.dto.ExternalCallResponseDTO;
import com.patra.egress.api.dto.ResponseEnvelopeDTO;
import com.patra.starter.provenance.common.config.DefaultConfigProvider;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.converter.XmlToJsonConverter;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.common.gateway.GatewayRequestBuilder;
import com.patra.starter.provenance.common.metrics.ProvenanceMetrics;
import com.patra.starter.provenance.pubmed.model.request.EFetchRequest;
import com.patra.starter.provenance.pubmed.model.request.EPostRequest;
import com.patra.starter.provenance.pubmed.model.request.ESearchRequest;
import com.patra.starter.provenance.pubmed.model.response.EFetchResponse;
import com.patra.starter.provenance.pubmed.model.response.EPostResponse;
import com.patra.starter.provenance.pubmed.model.response.ESearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * PubMed client implementation calling E-utilities via the egress gateway.
 *
 * <p>Handles configuration precedence, optional Micrometer instrumentation and XML to JSON
 * conversion for payloads lacking native JSON representations.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class PubMedClientImpl implements PubMedClient {

  private static final ProvenanceCode PROVENANCE = ProvenanceCode.PUBMED;

  private final EgressGatewayClient gatewayClient;
  private final GatewayRequestBuilder requestBuilder;
  private final DefaultConfigProvider configProvider;
  private final XmlToJsonConverter xmlConverter;
  private final ObjectMapper objectMapper;
  private final ProvenanceMetrics metrics;

  public PubMedClientImpl(
      EgressGatewayClient gatewayClient,
      GatewayRequestBuilder requestBuilder,
      DefaultConfigProvider configProvider,
      XmlToJsonConverter xmlConverter,
      ObjectMapper objectMapper,
      ProvenanceMetrics metrics) {
    this.gatewayClient = gatewayClient;
    this.requestBuilder = requestBuilder;
    this.configProvider = configProvider;
    this.xmlConverter = xmlConverter;
    this.objectMapper = objectMapper;
    this.metrics = metrics;
  }

  /** {@inheritDoc} */
  @Override
  public ESearchResponse esearch(ESearchRequest request) {
    return esearch(request, null);
  }

  /** {@inheritDoc} */
  @Override
  public ESearchResponse esearch(ESearchRequest request, ProvenanceConfig config) {
    if (metrics != null) {
      // Capture latency and success metrics whenever Micrometer instrumentation is available.
      return metrics.recordApiCall(PROVENANCE, "esearch", () -> executeESearch(request, config));
    }
    return executeESearch(request, config);
  }

  private ESearchResponse executeESearch(ESearchRequest request, ProvenanceConfig config) {
    ProvenanceConfig finalConfig =
        config != null ? config : configProvider.getPubMedDefaultConfig();
    ExternalCallRequestDTO gatewayRequest =
        requestBuilder.build(finalConfig.baseUrl(), "/esearch.fcgi", request, finalConfig);

    ExternalCallResponseDTO response = invokeGateway("esearch", gatewayRequest);
    ResponseEnvelopeDTO envelope = response.envelope();
    try {
      return objectMapper.readValue(envelope.body(), ESearchResponse.class);
    } catch (Exception ex) {
      throw new ProvenanceClientException(
          PROVENANCE.getCode(),
          "esearch",
          envelope.statusCode(),
          response.traceId(),
          envelope.body(),
          "Failed to parse JSON response",
          ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public EFetchResponse efetch(EFetchRequest request) {
    return efetch(request, null);
  }

  /** {@inheritDoc} */
  @Override
  public EFetchResponse efetch(EFetchRequest request, ProvenanceConfig config) {
    if (metrics != null) {
      // Capture latency and success metrics whenever Micrometer instrumentation is available.
      return metrics.recordApiCall(PROVENANCE, "efetch", () -> executeEFetch(request, config));
    }
    return executeEFetch(request, config);
  }

  private EFetchResponse executeEFetch(EFetchRequest request, ProvenanceConfig config) {
    ProvenanceConfig finalConfig =
        config != null ? config : configProvider.getPubMedDefaultConfig();
    ExternalCallRequestDTO gatewayRequest =
        requestBuilder.build(finalConfig.baseUrl(), "/efetch.fcgi", request, finalConfig);

    ExternalCallResponseDTO response = invokeGateway("efetch", gatewayRequest);
    ResponseEnvelopeDTO envelope = response.envelope();
    try {
      if (request.requiresXmlConversion()) {
        JsonNode root = xmlConverter.convert(envelope.body(), JsonNode.class);
        return EFetchResponse.from(root);
      }
      JsonNode root = objectMapper.readTree(envelope.body());
      return EFetchResponse.from(root);
    } catch (ProvenanceClientException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ProvenanceClientException(
          PROVENANCE.getCode(),
          "efetch",
          envelope.statusCode(),
          response.traceId(),
          envelope.body(),
          "Failed to parse EFetch response",
          ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public EPostResponse epost(EPostRequest request) {
    return epost(request, null);
  }

  /** {@inheritDoc} */
  @Override
  public EPostResponse epost(EPostRequest request, ProvenanceConfig config) {
    if (metrics != null) {
      // Capture latency and success metrics whenever Micrometer instrumentation is available.
      return metrics.recordApiCall(PROVENANCE, "epost", () -> executeEPost(request, config));
    }
    return executeEPost(request, config);
  }

  private EPostResponse executeEPost(EPostRequest request, ProvenanceConfig config) {
    ProvenanceConfig finalConfig =
        config != null ? config : configProvider.getPubMedDefaultConfig();
    ExternalCallRequestDTO gatewayRequest =
        requestBuilder.build(finalConfig.baseUrl(), "/epost.fcgi", request, finalConfig);

    ExternalCallResponseDTO response = invokeGateway("epost", gatewayRequest);
    ResponseEnvelopeDTO envelope = response.envelope();
    try {
      JsonNode root = objectMapper.readTree(envelope.body());
      EPostResponse epostResponse = EPostResponse.from(root);

      log.debug(
          "[PUBMED] EPost success: idCount={}, WebEnv={}, QueryKey={}",
          request.getIdCount(),
          epostResponse.getTruncatedWebEnv(),
          epostResponse.queryKey());

      return epostResponse;
    } catch (ProvenanceClientException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ProvenanceClientException(
          PROVENANCE.getCode(),
          "epost",
          envelope.statusCode(),
          response.traceId(),
          envelope.body(),
          "Failed to parse EPost response",
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
