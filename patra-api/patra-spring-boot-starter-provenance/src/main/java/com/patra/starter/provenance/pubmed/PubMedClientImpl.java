package com.patra.starter.provenance.pubmed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.starter.provenance.common.config.DefaultConfigProvider;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.converter.ProvenanceConfigConverter;
import com.patra.starter.provenance.common.converter.XmlToJsonConverter;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.common.http.HttpResilienceConfig;
import com.patra.starter.provenance.common.http.SimpleHttpClient;
import com.patra.starter.provenance.common.metrics.ProvenanceMetrics;
import com.patra.starter.provenance.pubmed.model.request.EFetchRequest;
import com.patra.starter.provenance.pubmed.model.request.EPostRequest;
import com.patra.starter.provenance.pubmed.model.request.ESearchRequest;
import com.patra.starter.provenance.pubmed.model.response.EFetchResponse;
import com.patra.starter.provenance.pubmed.model.response.EPostResponse;
import com.patra.starter.provenance.pubmed.model.response.ESearchResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * PubMed client implementation calling E-utilities directly via HTTP.
 *
 * <p>Handles configuration precedence, optional Micrometer instrumentation and XML to JSON
 * conversion for payloads lacking native JSON representations.
 */
@Slf4j
public class PubMedClientImpl implements PubMedClient {

  private static final ProvenanceCode PROVENANCE = ProvenanceCode.PUBMED;

  private final SimpleHttpClient httpClient;
  private final DefaultConfigProvider configProvider;
  private final XmlToJsonConverter xmlConverter;
  private final ObjectMapper objectMapper;
  private final ProvenanceMetrics metrics;

  public PubMedClientImpl(
      SimpleHttpClient httpClient,
      DefaultConfigProvider configProvider,
      XmlToJsonConverter xmlConverter,
      ObjectMapper objectMapper,
      ProvenanceMetrics metrics) {
    this.httpClient = httpClient;
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

    String baseUrl = finalConfig.baseUrl();
    String path = "/esearch.fcgi";
    String fullUrl = baseUrl + path;

    java.util.Map<String, String> queryParams = request.toQueryParams();
    java.util.Map<String, String> headers = ProvenanceConfigConverter.extractHeaders(finalConfig);
    HttpResilienceConfig rc = ProvenanceConfigConverter.toHttpResilienceConfig(finalConfig);

    String body = httpClient.get(baseUrl, path, queryParams, headers, rc);
    try {
      return objectMapper.readValue(body, ESearchResponse.class);
    } catch (Exception ex) {
      throw new ProvenanceClientException(
          PROVENANCE.getCode(), "esearch", null, null, body, "Failed to parse JSON response", ex);
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

    String baseUrl = finalConfig.baseUrl();
    String path = "/efetch.fcgi";
    String fullUrl = baseUrl + path;

    java.util.Map<String, String> queryParams = request.toQueryParams();
    java.util.Map<String, String> headers = ProvenanceConfigConverter.extractHeaders(finalConfig);
    HttpResilienceConfig rc = ProvenanceConfigConverter.toHttpResilienceConfig(finalConfig);

    String body = httpClient.get(baseUrl, path, queryParams, headers, rc);
    try {
      if (request.requiresXmlConversion()) {
        JsonNode root = xmlConverter.convert(body, JsonNode.class);
        return EFetchResponse.from(root);
      }
      JsonNode root = objectMapper.readTree(body);
      return EFetchResponse.from(root);
    } catch (ProvenanceClientException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ProvenanceClientException(
          PROVENANCE.getCode(), "efetch", null, null, body, "Failed to parse EFetch response", ex);
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

    String baseUrl = finalConfig.baseUrl();
    String path = "/epost.fcgi";
    String fullUrl = baseUrl + path;

    java.util.Map<String, String> queryParams = request.toQueryParams();
    java.util.Map<String, String> headers = ProvenanceConfigConverter.extractHeaders(finalConfig);
    HttpResilienceConfig rc = ProvenanceConfigConverter.toHttpResilienceConfig(finalConfig);

    String body = httpClient.postForm(baseUrl, path, queryParams, headers, rc);
    try {
      JsonNode root = objectMapper.readTree(body);
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
          PROVENANCE.getCode(), "epost", null, null, body, "Failed to parse EPost response", ex);
    }
  }
}
