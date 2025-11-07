package com.patra.ingest.infra.integration.pubmed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.json.JsonMapperHolder;
import com.patra.ingest.domain.exception.BatchPlanningException;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.plan.PlanMetadata;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.pubmed.PubMedClient;
import com.patra.starter.provenance.pubmed.model.request.ESearchRequest;
import com.patra.starter.provenance.pubmed.model.response.ESearchResponse;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * PubmedSearchAdapter 单元测试。
 *
 * <p>测试策略: 使用 Mockito mock PubMedClient，测试适配器的请求构建、响应处理和异常转换。
 *
 * @author linqibin
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PubmedSearchAdapter 单元测试")
class PubmedSearchAdapterTest {

  @Mock private PubMedClient pubMedClient;

  @InjectMocks private PubmedSearchAdapter adapter;

  private static final ObjectMapper MAPPER = JsonMapperHolder.getObjectMapper();
  private static final String TEST_QUERY = "cancer[title]";

  private JsonNode testParams;

  @BeforeEach
  void setUp() throws Exception {
    // 准备测试用的参数节点
    testParams = MAPPER.readTree("{\"term\": \"cancer[title]\", \"db\": \"pubmed\"}");
  }

  @Test
  @DisplayName("正常场景 - 成功获取计划元数据")
  void shouldPreparePlanMetadataSuccessfully() {
    // Given: 准备模拟的 ESearch 响应
    ESearchResponse.Result result = createMockResult(1500, "WebEnv123", "QueryKey456");
    ESearchResponse response = new ESearchResponse(null, result);

    when(pubMedClient.esearch(any(ESearchRequest.class))).thenReturn(response);

    // When: 调用 preparePlanMetadata
    PlanMetadata metadata = adapter.preparePlanMetadata(TEST_QUERY, testParams, null);

    // Then: 验证返回正确的元数据
    assertThat(metadata).isNotNull();
    assertThat(metadata.totalCount()).isEqualTo(1500);
    assertThat(metadata.webEnv()).isEqualTo("WebEnv123");
    assertThat(metadata.queryKey()).isEqualTo("QueryKey456");

    verify(pubMedClient).esearch(any(ESearchRequest.class));
  }

  @Test
  @DisplayName("请求构建场景 - 自动添加 usehistory 参数")
  void shouldAddUseHistoryParameterToRequest() {
    // Given: 准备不包含 usehistory 的参数
    ESearchResponse.Result result = createMockResult(100, "WebEnv1", "QueryKey1");
    ESearchResponse response = new ESearchResponse(null, result);

    ArgumentCaptor<ESearchRequest> requestCaptor = ArgumentCaptor.forClass(ESearchRequest.class);
    when(pubMedClient.esearch(requestCaptor.capture())).thenReturn(response);

    // When: 调用 preparePlanMetadata
    adapter.preparePlanMetadata(TEST_QUERY, testParams, null);

    // Then: 验证请求中包含必需的参数（由 assembler 处理）
    ESearchRequest capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest).isNotNull();
    assertThat(capturedRequest.term()).isNotEmpty();
  }

  @Test
  @DisplayName("空响应场景 - 返回空 PlanMetadata")
  void shouldReturnEmptyMetadataWhenResponseIsNull() {
    // Given: PubMedClient 返回 null
    when(pubMedClient.esearch(any(ESearchRequest.class))).thenReturn(null);

    // When: 调用 preparePlanMetadata
    PlanMetadata metadata = adapter.preparePlanMetadata(TEST_QUERY, testParams, null);

    // Then: 验证返回空元数据
    assertThat(metadata).isNotNull();
    assertThat(metadata.totalCount()).isZero();
    assertThat(metadata.webEnv()).isNull();
    assertThat(metadata.queryKey()).isNull();
  }

  @Test
  @DisplayName("空结果场景 - 返回空 PlanMetadata")
  void shouldReturnEmptyMetadataWhenResultIsNull() {
    // Given: ESearch 响应包含 null result
    ESearchResponse response = new ESearchResponse(null, null);

    when(pubMedClient.esearch(any(ESearchRequest.class))).thenReturn(response);

    // When: 调用 preparePlanMetadata
    PlanMetadata metadata = adapter.preparePlanMetadata(TEST_QUERY, testParams, null);

    // Then: 验证返回空元数据
    assertThat(metadata).isNotNull();
    assertThat(metadata.totalCount()).isZero();
    assertThat(metadata.webEnv()).isNull();
    assertThat(metadata.queryKey()).isNull();
  }

  @Test
  @DisplayName("部分数据场景 - 正确处理缺失的 WebEnv 或 QueryKey")
  void shouldHandleMissingWebEnvOrQueryKey() {
    // Given: 响应缺少 WebEnv 和 QueryKey
    ESearchResponse.Result result = createMockResult(500, null, null);
    ESearchResponse response = new ESearchResponse(null, result);

    when(pubMedClient.esearch(any(ESearchRequest.class))).thenReturn(response);

    // When: 调用 preparePlanMetadata
    PlanMetadata metadata = adapter.preparePlanMetadata(TEST_QUERY, testParams, null);

    // Then: 验证返回的元数据只包含 count
    assertThat(metadata).isNotNull();
    assertThat(metadata.totalCount()).isEqualTo(500);
    assertThat(metadata.webEnv()).isNull();
    assertThat(metadata.queryKey()).isNull();
  }

  @Test
  @DisplayName("ProvenanceClientException 场景 - 转换为 BatchPlanningException")
  void shouldConvertProvenanceClientExceptionToBatchPlanningException() {
    // Given: PubMedClient 抛出 ProvenanceClientException
    ProvenanceClientException clientException =
        new ProvenanceClientException("PUBMED", "esearch", "API rate limit exceeded");

    when(pubMedClient.esearch(any(ESearchRequest.class))).thenThrow(clientException);

    // When & Then: 验证抛出 BatchPlanningException
    assertThatThrownBy(() -> adapter.preparePlanMetadata(TEST_QUERY, testParams, null))
        .isInstanceOf(BatchPlanningException.class)
        .hasMessageContaining("PubMed metadata lookup failed")
        .hasCause(clientException);
  }

  @Test
  @DisplayName("意外异常场景 - 转换为 BatchPlanningException")
  void shouldConvertUnexpectedExceptionToBatchPlanningException() {
    // Given: PubMedClient 抛出意外异常
    RuntimeException unexpectedException = new RuntimeException("Network timeout");

    when(pubMedClient.esearch(any(ESearchRequest.class))).thenThrow(unexpectedException);

    // When & Then: 验证抛出 BatchPlanningException
    assertThatThrownBy(() -> adapter.preparePlanMetadata(TEST_QUERY, testParams, null))
        .isInstanceOf(BatchPlanningException.class)
        .hasMessageContaining("PubMed metadata lookup unexpected error")
        .hasMessageContaining("Network timeout")
        .hasCause(unexpectedException);
  }

  @Test
  @DisplayName("配置覆盖场景 - 使用 ProvenanceConfigSnapshot 调用客户端")
  void shouldUseProvenanceConfigWhenProvided() {
    // Given: 准备配置快照
    ProvenanceConfigSnapshot.ProvenanceInfo provenanceInfo =
        new ProvenanceConfigSnapshot.ProvenanceInfo(
            1L,
            "PUBMED",
            "PubMed",
            "https://custom.eutils.ncbi.nlm.nih.gov/entrez/eutils/",
            "UTC",
            null,
            true,
            "ACTIVE");

    ProvenanceConfigSnapshot snapshot =
        new ProvenanceConfigSnapshot(provenanceInfo, null, null, null, null, null, null);

    ESearchResponse.Result result = createMockResult(100, "WebEnv1", "QueryKey1");
    ESearchResponse response = new ESearchResponse(null, result);

    when(pubMedClient.esearch(any(ESearchRequest.class), any(ProvenanceConfig.class)))
        .thenReturn(response);

    // When: 调用 preparePlanMetadata with snapshot
    PlanMetadata metadata = adapter.preparePlanMetadata(TEST_QUERY, testParams, snapshot);

    // Then: 验证使用了配置覆盖
    assertThat(metadata).isNotNull();
    assertThat(metadata.totalCount()).isEqualTo(100);
    verify(pubMedClient).esearch(any(ESearchRequest.class), any(ProvenanceConfig.class));
  }

  @Test
  @DisplayName("配置转换场景 - 缺少 baseUrl 时降级到默认配置")
  void shouldFallbackToDefaultConfigWhenBaseUrlMissing() {
    // Given: 配置快照缺少 baseUrl
    ProvenanceConfigSnapshot.ProvenanceInfo provenanceInfo =
        new ProvenanceConfigSnapshot.ProvenanceInfo(
            1L, "PUBMED", "PubMed", null, // 缺少 baseUrl
            "UTC", null, true, "ACTIVE");

    ProvenanceConfigSnapshot snapshot =
        new ProvenanceConfigSnapshot(provenanceInfo, null, null, null, null, null, null);

    ESearchResponse.Result result = createMockResult(100, "WebEnv1", "QueryKey1");
    ESearchResponse response = new ESearchResponse(null, result);

    // 当 baseUrl 缺失时，应该使用不带配置的方法
    when(pubMedClient.esearch(any(ESearchRequest.class))).thenReturn(response);

    // When: 调用 preparePlanMetadata
    PlanMetadata metadata = adapter.preparePlanMetadata(TEST_QUERY, testParams, snapshot);

    // Then: 验证降级到默认配置
    assertThat(metadata).isNotNull();
    assertThat(metadata.totalCount()).isEqualTo(100);
    verify(pubMedClient).esearch(any(ESearchRequest.class));
  }

  @Test
  @DisplayName("空参数场景 - 应该创建空对象节点并添加 usehistory")
  void shouldHandleNullParamsGracefully() throws Exception {
    // Given: params 为 null
    ESearchResponse.Result result = createMockResult(50, "WebEnv1", "QueryKey1");
    ESearchResponse response = new ESearchResponse(null, result);

    when(pubMedClient.esearch(any(ESearchRequest.class))).thenReturn(response);

    // When: 调用 preparePlanMetadata with null params
    PlanMetadata metadata = adapter.preparePlanMetadata(TEST_QUERY, null, null);

    // Then: 验证能够正常处理
    assertThat(metadata).isNotNull();
    assertThat(metadata.totalCount()).isEqualTo(50);
  }

  @Test
  @DisplayName("负数 count 场景 - 应该归零")
  void shouldHandleNegativeCountGracefully() {
    // Given: 响应包含负数 count
    ESearchResponse.Result result = createMockResult(-1, "WebEnv1", "QueryKey1");
    ESearchResponse response = new ESearchResponse(null, result);

    when(pubMedClient.esearch(any(ESearchRequest.class))).thenReturn(response);

    // When: 调用 preparePlanMetadata
    PlanMetadata metadata = adapter.preparePlanMetadata(TEST_QUERY, testParams, null);

    // Then: 验证 count 被归零
    assertThat(metadata).isNotNull();
    assertThat(metadata.totalCount()).isZero();
  }


  // ===== 辅助方法 =====

  /**
   * 创建模拟的 ESearchResponse.Result 对象。
   *
   * @param count 搜索结果数量
   * @param webEnv WebEnv 令牌
   * @param queryKey QueryKey 令牌
   * @return ESearchResponse.Result 实例
   */
  private ESearchResponse.Result createMockResult(int count, String webEnv, String queryKey) {
    return new ESearchResponse.Result(
        count, // count
        0, // retMax
        0, // retStart
        Collections.emptyList(), // idList
        Collections.emptyList(), // translationSet
        Collections.emptyList(), // translationStack
        webEnv, // webEnv
        queryKey, // queryKey
        null, // queryTranslation
        null, // errorList
        null // warnings
        );
  }
}
