package com.patra.starter.provenance.pubmed;

import com.patra.common.enums.ProvenanceCode;
import com.patra.common.provenance.api.constants.PubMedOperation;
import com.patra.common.provenance.api.values.pubmed.RetMode;
import com.patra.common.provenance.api.values.pubmed.RetType;
import com.patra.starter.provenance.common.config.DefaultConfigProvider;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.common.metrics.ProvenanceMetrics;
import com.patra.starter.provenance.pubmed.model.request.EFetchRequest;
import com.patra.starter.provenance.pubmed.model.request.EPostRequest;
import com.patra.starter.provenance.pubmed.model.request.ESearchRequest;
import com.patra.starter.provenance.pubmed.model.response.EFetchResponse;
import com.patra.starter.provenance.pubmed.model.response.EPostResponse;
import com.patra.starter.provenance.pubmed.model.response.ESearchResponse;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.xml.XmlMapper;

/// PubMed 客户端实现（使用 Spring RestClient）
///
/// 直接通过HTTP调用PubMed E-utilities API。PubMed是美国国家医学图书馆的生物医学出版物数据库， 提供超过3400万条引文和摘要。
///
/// 主要功能：
///
/// - ESearch：搜索PubMed数据库，返回匹配的PMID列表
///   - EPost：上传PMID列表到服务器，获取WebEnv和QueryKey用于后续批量操作
///   - EFetch：批量获取文章详细元数据（XML格式）
///   - 配置优先级处理：优先使用方法级配置，回退到默认配置
///   - 可选的Micrometer指标收集：记录API调用时长和成功率
///   - 强类型解析：支持JSON和XML两种PubMed响应格式
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class PubMedClientAdapter implements PubMedClient {

  private static final ProvenanceCode PROVENANCE = ProvenanceCode.PUBMED;

  private final RestClient restClient;
  private final DefaultConfigProvider configProvider;
  private final ObjectMapper objectMapper;
  private final XmlMapper xmlMapper;
  private final ProvenanceMetrics metrics;

  public PubMedClientAdapter(
      RestClient restClient,
      DefaultConfigProvider configProvider,
      ObjectMapper objectMapper,
      XmlMapper xmlMapper,
      ProvenanceMetrics metrics) {
    this.restClient = restClient;
    this.configProvider = configProvider;
    this.objectMapper = objectMapper;
    this.xmlMapper = xmlMapper;
    this.metrics = metrics;
  }

  /// {@inheritDoc}
  @Override
  public ESearchResponse esearch(ESearchRequest request) {
    return esearch(request, null);
  }

  /// {@inheritDoc}
  @Override
  public ESearchResponse esearch(ESearchRequest request, ProvenanceConfig config) {
    if (metrics != null) {
      // 当Micrometer工具可用时捕获延迟和成功指标
      return metrics.recordApiCall(
          PROVENANCE,
          PubMedOperation.ESEARCH.getOperationName(),
          () -> executeESearch(request, config));
    }
    return executeESearch(request, config);
  }

  private ESearchResponse executeESearch(ESearchRequest request, ProvenanceConfig config) {
    ProvenanceConfig finalConfig =
        config != null ? config : configProvider.getPubMedDefaultConfig();
    Map<String, String> queryParams = request.toQueryParams();

    var uriBuilder =
        UriComponentsBuilder.fromUriString(finalConfig.baseUrl())
            .path(PubMedOperation.ESEARCH.getEndpoint());
    queryParams.forEach(uriBuilder::queryParam);

    String body = restClient.get().uri(uriBuilder.build().toUri()).retrieve().body(String.class);

    try {
      return objectMapper.readValue(body, ESearchResponse.class);
    } catch (Exception ex) {
      throw new ProvenanceClientException(
          PROVENANCE.getCode(),
          PubMedOperation.ESEARCH.getOperationName(),
          null,
          null,
          body,
          "解析JSON响应失败",
          ex);
    }
  }

  /// {@inheritDoc}
  @Override
  public EFetchResponse efetch(EFetchRequest request) {
    return efetch(request, null);
  }

  /// {@inheritDoc}
  @Override
  public EFetchResponse efetch(EFetchRequest request, ProvenanceConfig config) {
    if (metrics != null) {
      // Capture latency and success metrics whenever Micrometer instrumentation is available.
      return metrics.recordApiCall(
          PROVENANCE,
          PubMedOperation.EFETCH.getOperationName(),
          () -> executeEFetch(request, config));
    }
    return executeEFetch(request, config);
  }

  private EFetchResponse executeEFetch(EFetchRequest request, ProvenanceConfig config) {
    ProvenanceConfig finalConfig =
        config != null ? config : configProvider.getPubMedDefaultConfig();

    Map<String, String> queryParams = request.toQueryParams();

    var uriBuilder =
        UriComponentsBuilder.fromUriString(finalConfig.baseUrl())
            .path(PubMedOperation.EFETCH.getEndpoint());
    queryParams.forEach(uriBuilder::queryParam);

    String body = restClient.get().uri(uriBuilder.build().toUri()).retrieve().body(String.class);

    try {
      String retmode = request.retmode();
      // 解析 retmode（如果有提供）
      RetMode mode =
          retmode != null ? RetMode.fromStringOrDefault(retmode, RetMode.XML) : RetMode.XML;

      if (mode == RetMode.XML) {
        return EFetchResponse.fromXml(xmlMapper, body);
      }

      // 处理 TEXT 格式的 uilist
      String rettype = request.rettype();
      RetType type = rettype != null ? RetType.fromStringOrDefault(rettype, null) : null;
      if (type == RetType.UILIST && mode == RetMode.TEXT) {
        return EFetchResponse.fromUidListText(body);
      }

      throw new IllegalArgumentException(
          "Unsupported EFetch combination: rettype=%s, retmode=%s"
              .formatted(request.rettype(), retmode));
    } catch (JacksonException | IllegalArgumentException ex) {
      throw new ProvenanceClientException(
          PROVENANCE.getCode(),
          PubMedOperation.EFETCH.getOperationName(),
          null,
          null,
          body,
          "解析EFetch响应失败",
          ex);
    }
  }

  /// {@inheritDoc}
  @Override
  public EPostResponse epost(EPostRequest request) {
    return epost(request, null);
  }

  /// {@inheritDoc}
  @Override
  public EPostResponse epost(EPostRequest request, ProvenanceConfig config) {
    if (metrics != null) {
      // Capture latency and success metrics whenever Micrometer instrumentation is available.
      return metrics.recordApiCall(
          PROVENANCE,
          PubMedOperation.EPOST.getOperationName(),
          () -> executeEPost(request, config));
    }
    return executeEPost(request, config);
  }

  private EPostResponse executeEPost(EPostRequest request, ProvenanceConfig config) {
    ProvenanceConfig finalConfig =
        config != null ? config : configProvider.getPubMedDefaultConfig();

    Map<String, String> formParams = request.toQueryParams();

    // 转换为 MultiValueMap 用于表单提交
    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formParams.forEach(formData::add);

    var uri =
        UriComponentsBuilder.fromUriString(finalConfig.baseUrl())
            .path(PubMedOperation.EPOST.getEndpoint())
            .build()
            .toUri();

    String body =
        restClient
            .post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(formData)
            .retrieve()
            .body(String.class);

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
    } catch (JacksonException | IllegalArgumentException ex) {
      throw new ProvenanceClientException(
          PROVENANCE.getCode(),
          PubMedOperation.EPOST.getOperationName(),
          null,
          null,
          body,
          "解析EPost响应失败",
          ex);
    }
  }
}
