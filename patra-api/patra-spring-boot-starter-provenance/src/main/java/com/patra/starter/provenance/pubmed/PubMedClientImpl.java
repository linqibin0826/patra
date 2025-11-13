package com.patra.starter.provenance.pubmed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.starter.provenance.common.config.DefaultConfigProvider;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.converter.ProvenanceConfigConverter;
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
import java.io.IOException;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * PubMed 客户端实现
 *
 * <p>直接通过HTTP调用PubMed E-utilities API。PubMed是美国国家医学图书馆的生物医学文献数据库， 提供超过3400万条引文和摘要。
 *
 * <p>主要功能：
 *
 * <ul>
 *   <li>ESearch：搜索PubMed数据库，返回匹配的PMID列表
 *   <li>EPost：上传PMID列表到服务器，获取WebEnv和QueryKey用于后续批量操作
 *   <li>EFetch：批量获取文章详细元数据（XML格式）
 *   <li>配置优先级处理：优先使用方法级配置，回退到默认配置
 *   <li>可选的Micrometer指标收集：记录API调用时长和成功率
 *   <li>强类型解析：支持JSON和XML两种PubMed响应格式
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class PubMedClientImpl implements PubMedClient {

  private static final ProvenanceCode PROVENANCE = ProvenanceCode.PUBMED;

  private final SimpleHttpClient httpClient;
  private final DefaultConfigProvider configProvider;
  private final ObjectMapper objectMapper;
  private final XmlMapper xmlMapper;
  private final ProvenanceMetrics metrics;

  public PubMedClientImpl(
      SimpleHttpClient httpClient,
      DefaultConfigProvider configProvider,
      ObjectMapper objectMapper,
      XmlMapper xmlMapper,
      ProvenanceMetrics metrics) {
    this.httpClient = httpClient;
    this.configProvider = configProvider;
    this.objectMapper = objectMapper;
    this.xmlMapper = xmlMapper;
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
      // 当Micrometer工具可用时捕获延迟和成功指标
      return metrics.recordApiCall(PROVENANCE, "esearch", () -> executeESearch(request, config));
    }
    return executeESearch(request, config);
  }

  private ESearchResponse executeESearch(ESearchRequest request, ProvenanceConfig config) {
    ProvenanceConfig finalConfig =
        config != null ? config : configProvider.getPubMedDefaultConfig();

    String baseUrl = finalConfig.baseUrl();
    String path = "/esearch.fcgi";

    Map<String, String> queryParams = request.toQueryParams();
    Map<String, String> headers = ProvenanceConfigConverter.extractHeaders(finalConfig);
    HttpResilienceConfig rc = ProvenanceConfigConverter.toHttpResilienceConfig(finalConfig);

    String body = httpClient.get(baseUrl, path, queryParams, headers, rc);
    try {
      return objectMapper.readValue(body, ESearchResponse.class);
    } catch (Exception ex) {
      throw new ProvenanceClientException(
          PROVENANCE.getCode(), "esearch", null, null, body, "解析JSON响应失败", ex);
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

    Map<String, String> queryParams = request.toQueryParams();
    Map<String, String> headers = ProvenanceConfigConverter.extractHeaders(finalConfig);
    HttpResilienceConfig rc = ProvenanceConfigConverter.toHttpResilienceConfig(finalConfig);

    String body = httpClient.get(baseUrl, path, queryParams, headers, rc);
    try {
      String retmode = request.retmode();
      if (retmode == null || retmode.equalsIgnoreCase("xml")) {
        return EFetchResponse.fromXml(xmlMapper, body);
      }
      if ("uilist".equalsIgnoreCase(request.rettype()) && retmode.equalsIgnoreCase("text")) {
        return EFetchResponse.fromUidListText(body);
      }
      throw new IllegalArgumentException(
          "Unsupported EFetch combination: rettype=%s, retmode=%s"
              .formatted(request.rettype(), retmode));
    } catch (IOException | IllegalArgumentException ex) {
      throw new ProvenanceClientException(
          PROVENANCE.getCode(), "efetch", null, null, body, "解析EFetch响应失败", ex);
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

    java.util.Map<String, String> queryParams = request.toQueryParams();
    java.util.Map<String, String> headers = ProvenanceConfigConverter.extractHeaders(finalConfig);
    HttpResilienceConfig rc = ProvenanceConfigConverter.toHttpResilienceConfig(finalConfig);

    String body = httpClient.postForm(baseUrl, path, queryParams, headers, rc);
    try {
      EPostResponse response = xmlMapper.readValue(body, EPostResponse.class);
      if (!response.isValid()) {
        throw new IllegalArgumentException("EPost response missing WebEnv/QueryKey");
      }

      log.debug(
          "[PUBMED] EPost success: idCount={}, WebEnv={}, QueryKey={}",
          request.getIdCount(),
          response.getTruncatedWebEnv(),
          response.queryKey());

      return response;
    } catch (IOException | IllegalArgumentException ex) {
      throw new ProvenanceClientException(
          PROVENANCE.getCode(), "epost", null, null, body, "解析EPost响应失败", ex);
    }
  }
}
