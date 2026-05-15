package com.patra.starter.provenance.epmc;

import com.patra.starter.provenance.common.config.DefaultConfigProvider;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.common.metrics.ProvenanceMetrics;
import com.patra.starter.provenance.epmc.model.request.SearchRequest;
import com.patra.starter.provenance.epmc.model.response.SearchResponse;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import dev.linqibin.patra.common.provenance.api.constants.EpmcOperation;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/// Europe PMC 客户端实现（使用 Spring RestClient）
///
/// 直接通过HTTP调用Europe PMC API。Europe PMC是欧洲的生物医学出版物数据库， 提供PubMed数据以及欧洲特色的开放获取出版物。
///
/// 主要功能：
///
/// - 配置优先级处理：优先使用方法级配置，回退到默认配置
///   - 可选的Micrometer指标收集：记录API调用时长和成功率
///   - 防御性响应解析：容忍格式变化和缺失字段
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class EpmcClientAdapter implements EPMCClient {

  private static final ProvenanceCode PROVENANCE = ProvenanceCode.EPMC;

  private final RestClient restClient;
  private final DefaultConfigProvider configProvider;
  private final ObjectMapper objectMapper;
  private final ProvenanceMetrics metrics;

  public EpmcClientAdapter(
      RestClient restClient,
      DefaultConfigProvider configProvider,
      ObjectMapper objectMapper,
      ProvenanceMetrics metrics) {
    this.restClient = restClient;
    this.configProvider = configProvider;
    this.objectMapper = objectMapper;
    this.metrics = metrics;
  }

  /// {@inheritDoc}
  @Override
  public SearchResponse search(SearchRequest request) {
    return search(request, null);
  }

  /// {@inheritDoc}
  @Override
  public SearchResponse search(SearchRequest request, ProvenanceConfig config) {
    if (metrics != null) {
      // 当Micrometer存在时，包装调用以捕获时长和成功/失败计数器
      return metrics.recordApiCall(
          PROVENANCE,
          EpmcOperation.SEARCH.getOperationName(),
          () -> executeSearch(request, config));
    }
    return executeSearch(request, config);
  }

  private SearchResponse executeSearch(SearchRequest request, ProvenanceConfig config) {
    ProvenanceConfig finalConfig = config != null ? config : configProvider.getEPMCDefaultConfig();

    Map<String, String> queryParams = request.toQueryParams();

    var uriBuilder =
        UriComponentsBuilder.fromUriString(finalConfig.baseUrl())
            .path(EpmcOperation.SEARCH.getEndpoint());
    queryParams.forEach(uriBuilder::queryParam);

    String body = restClient.get().uri(uriBuilder.build().toUri()).retrieve().body(String.class);

    try {
      JsonNode root = objectMapper.readTree(body);
      return SearchResponse.from(root);
    } catch (Exception ex) {
      throw new ProvenanceClientException(
          PROVENANCE.getCode(),
          EpmcOperation.SEARCH.getOperationName(),
          null,
          null,
          body,
          "解析JSON响应失败",
          ex);
    }
  }
}
