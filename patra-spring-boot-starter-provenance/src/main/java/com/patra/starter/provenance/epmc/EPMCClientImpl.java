package com.patra.starter.provenance.epmc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.provenance.api.constants.EpmcOperation;
import com.patra.starter.provenance.common.config.DefaultConfigProvider;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.common.metrics.ProvenanceMetrics;
import com.patra.starter.provenance.epmc.model.request.SearchRequest;
import com.patra.starter.provenance.epmc.model.response.SearchResponse;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

/**
 * Europe PMC 客户端实现（使用 Spring RestClient）
 *
 * <p>直接通过HTTP调用Europe PMC API。Europe PMC是欧洲的生物医学出版物数据库， 提供PubMed数据以及欧洲特色的开放获取出版物。
 *
 * <p>主要功能：
 *
 * <ul>
 *   <li>配置优先级处理：优先使用方法级配置，回退到默认配置
 *   <li>可选的Micrometer指标收集：记录API调用时长和成功率
 *   <li>防御性响应解析：容忍格式变化和缺失字段
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class EPMCClientImpl implements EPMCClient {

  private static final ProvenanceCode PROVENANCE = ProvenanceCode.EPMC;

  private final RestClient restClient;
  private final DefaultConfigProvider configProvider;
  private final ObjectMapper objectMapper;
  private final ProvenanceMetrics metrics;

  public EPMCClientImpl(
      RestClient restClient,
      DefaultConfigProvider configProvider,
      ObjectMapper objectMapper,
      ProvenanceMetrics metrics) {
    this.restClient = restClient;
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
      // 当Micrometer存在时，包装调用以捕获时长和成功/失败计数器
      return metrics.recordApiCall(PROVENANCE, EpmcOperation.SEARCH.getOperationName(), () -> executeSearch(request, config));
    }
    return executeSearch(request, config);
  }

  private SearchResponse executeSearch(SearchRequest request, ProvenanceConfig config) {
    ProvenanceConfig finalConfig = config != null ? config : configProvider.getEPMCDefaultConfig();

    Map<String, String> queryParams = request.toQueryParams();

    String body =
        restClient
            .get()
            .uri(
                uriBuilder -> {
                  uriBuilder.path(EpmcOperation.SEARCH.getEndpoint());
                  queryParams.forEach(uriBuilder::queryParam);
                  return uriBuilder.build();
                })
            .retrieve()
            .body(String.class);

    try {
      JsonNode root = objectMapper.readTree(body);
      return SearchResponse.from(root);
    } catch (Exception ex) {
      throw new ProvenanceClientException(
          PROVENANCE.getCode(), EpmcOperation.SEARCH.getOperationName(), null, null, body, "解析JSON响应失败", ex);
    }
  }
}
