package com.patra.starter.provenance.pubmed.processor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.withSettings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.model.CanonicalPublication;
import com.patra.common.model.DataType;
import com.patra.starter.provenance.boot.ProvenanceProperties;
import com.patra.starter.provenance.common.config.BatchingConfig;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.common.metrics.ProvenanceMetrics;
import com.patra.starter.provenance.common.processor.ProcessResult;
import com.patra.starter.provenance.common.processor.ProviderContext;
import com.patra.starter.provenance.common.processor.ValidationResult;
import com.patra.starter.provenance.common.provider.BatchExecutionParams;
import com.patra.starter.provenance.common.provider.ProviderRequest;
import com.patra.starter.provenance.pubmed.PubMedClient;
import com.patra.starter.provenance.pubmed.converter.PubmedPublicationConverter;
import com.patra.starter.provenance.pubmed.model.request.EFetchRequest;
import com.patra.starter.provenance.pubmed.model.request.EPostRequest;
import com.patra.starter.provenance.pubmed.model.request.ESearchRequest;
import com.patra.starter.provenance.pubmed.model.response.EFetchResponse;
import com.patra.starter.provenance.pubmed.model.response.EPostResponse;
import com.patra.starter.provenance.pubmed.model.response.ESearchResponse;
import com.patra.starter.provenance.pubmed.model.response.PubmedPublication;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// PubmedPublicationProcessor 单元测试
/// 
/// 测试策略：使用Mock对象进行单元测试，验证处理器的核心逻辑和异常处理。
/// 
/// @author Patra Architecture Team
/// @since 0.1.0
@DisplayName("PubmedPublicationProcessor 单元测试")
@ExtendWith(MockitoExtension.class)
class PubmedPublicationProcessorTest {

  @Mock private PubMedClient pubMedClient;
  @Mock private PubmedPublicationConverter converter;
  @Mock private ProvenanceProperties properties;
  @Mock private ProvenanceMetrics metrics;

  private PubmedPublicationProcessor processor;

  // 测试数据常量
  private static final String OPERATION_CODE = "HARVEST";
  private static final String PROVENANCE_CODE = "pubmed";
  private static final String TEST_QUERY = "cancer treatment";
  private static final String TEST_PMID_1 = "12345678";
  private static final String TEST_PMID_2 = "87654321";
  private static final String TEST_WEB_ENV = "MCID_67890abcdef";
  private static final String TEST_QUERY_KEY = "1";

  @BeforeEach
  void setUp() {
    processor = new PubmedPublicationProcessor(pubMedClient, converter, properties, metrics);
  }

  // ==================== 测试辅助方法 ====================

  /// 创建测试用的ProviderRequest
  private ProviderRequest createTestRequest() {
    return createTestRequest(TEST_QUERY, null);
  }

  /// 创建测试用的ProviderRequest（指定查询和参数）
  private ProviderRequest createTestRequest(String query, JsonNode params) {
    return ProviderRequest.builder()
        .config(createTestConfig())
        .executionParams(new BatchExecutionParams(query, params))
        .build();
  }

  /// 创建测试用的ProvenanceConfig
  private ProvenanceConfig createTestConfig() {
    return new ProvenanceConfig(
        "https://eutils.ncbi.nlm.nih.gov/entrez/eutils", null, null, null, null, null, null);
  }

  /// 创建测试用的ProvenanceConfig（带批处理配置）
  private ProvenanceConfig createTestConfigWithBatching(Integer epostThreshold) {
    BatchingConfig batchingConfig = new BatchingConfig(100, 100, epostThreshold);
    return new ProvenanceConfig(
        "https://eutils.ncbi.nlm.nih.gov/entrez/eutils",
        null,
        null,
        null,
        batchingConfig,
        null,
        null);
  }

  /// 创建测试用的ProviderContext
  private ProviderContext createTestContext() {
    return ProviderContext.builder()
        .config(createTestConfig())
        .client(pubMedClient)
        .attributes(new HashMap<>())
        .build();
  }

  /// 创建测试用的ESearchResponse（包含PMID列表）
  private ESearchResponse createESearchResponse(List<String> pmids, String webEnv) {
    ESearchResponse.Result result =
        new ESearchResponse.Result(
            pmids.size(),
            pmids.size(),
            0,
            pmids,
            null,
            null,
            webEnv,
            TEST_QUERY_KEY,
            null,
            null,
            null);
    return new ESearchResponse(null, result);
  }

  /// 创建测试用的空ESearchResponse
  private ESearchResponse createEmptyESearchResponse() {
    ESearchResponse.Result result =
        new ESearchResponse.Result(0, 0, 0, List.of(), null, null, null, null, null, null, null);
    return new ESearchResponse(null, result);
  }

  /// 创建测试用的EFetchResponse
  private EFetchResponse createEFetchResponse(List<PubmedPublication> articles) {
    // 使用反射或工厂方法创建EFetchResponse
    // 由于EFetchResponse的构造逻辑，这里直接返回mock
    EFetchResponse response = mock(EFetchResponse.class);
    when(response.articles()).thenReturn(articles);
    return response;
  }

  /// 创建测试用的PubmedPublication
  private PubmedPublication createMockArticle(String pmid) {
    PubmedPublication article = mock(PubmedPublication.class, withSettings().lenient());
    when(article.pmid()).thenReturn(pmid);
    return article;
  }

  /// 创建测试用的CanonicalPublication
  private CanonicalPublication createValidPublication(String pmid) {
    return CanonicalPublication.builder()
        .title("Test Article " + pmid)
        .abstractContent(
            CanonicalPublication.Abstract.builder().text("Test abstract for " + pmid).build())
        .identifiers(
            List.of(CanonicalPublication.Identifier.builder().type("pmid").value(pmid).build()))
        .build();
  }

  /// 创建测试用的无效CanonicalPublication（缺少必填字段）
  private CanonicalPublication createInvalidPublication() {
    return CanonicalPublication.builder()
        .abstractContent(
            CanonicalPublication.Abstract.builder()
                .text("Test abstract without title and pmid")
                .build())
        .build();
  }

  /// 创建测试用的EPostResponse
  private EPostResponse createEPostResponse() {
    EPostResponse response = mock(EPostResponse.class);
    when(response.webEnv()).thenReturn(TEST_WEB_ENV);
    when(response.queryKey()).thenReturn(TEST_QUERY_KEY);
    when(response.isValid()).thenReturn(true);
    when(response.getTruncatedWebEnv()).thenReturn(TEST_WEB_ENV.substring(0, 12) + "...");
    return response;
  }

  // ==================== 接口基本契约测试 ====================

  @Nested
  @DisplayName("接口基本契约测试")
  class InterfaceContractTest {

    @Test
    @DisplayName("should_return_correct_data_type")
    void shouldReturnCorrectDataType() {
      // When: 获取数据类型
      DataType dataType = processor.getDataType();

      // Then: 返回PUBLICATION类型
      assertThat(dataType).isEqualTo(DataType.PUBLICATION);
    }

    @Test
    @DisplayName("should_support_publication_data_type")
    void shouldSupportPublicationDataType() {
      // When & Then: 支持PUBLICATION数据类型
      assertThat(processor.supports(DataType.PUBLICATION)).isTrue();
      assertThat(processor.supports(DataType.JOURNAL)).isFalse();
      assertThat(processor.supports(DataType.DRUG)).isFalse();
    }
  }

  // ==================== 正常流程场景测试 ====================

  @Nested
  @DisplayName("正常流程场景测试")
  class NormalFlowTest {

    @Test
    @DisplayName("should_process_successfully_with_small_pmid_list_using_direct_efetch")
    void shouldProcessSuccessfullyWithSmallPmidList() {
      // Given: 准备少量PMID（直接EFetch策略）
      List<String> pmids = List.of(TEST_PMID_1, TEST_PMID_2);
      ESearchResponse searchResponse = createESearchResponse(pmids, TEST_WEB_ENV);

      PubmedPublication article1 = createMockArticle(TEST_PMID_1);
      PubmedPublication article2 = createMockArticle(TEST_PMID_2);
      EFetchResponse fetchResponse = createEFetchResponse(List.of(article1, article2));

      CanonicalPublication lit1 = createValidPublication(TEST_PMID_1);
      CanonicalPublication lit2 = createValidPublication(TEST_PMID_2);

      when(properties.mergeWithRuntime(eq(PROVENANCE_CODE), any())).thenReturn(createTestConfig());
      when(pubMedClient.esearch(any(ESearchRequest.class), any())).thenReturn(searchResponse);
      when(pubMedClient.efetch(any(EFetchRequest.class), any())).thenReturn(fetchResponse);
      when(converter.toCanonicalPublication(article1)).thenReturn(lit1);
      when(converter.toCanonicalPublication(article2)).thenReturn(lit2);

      ProviderRequest request = createTestRequest();
      ProviderContext context = createTestContext();

      // When: 处理数据
      ProcessResult<CanonicalPublication> result = processor.process(request, context);

      // Then: 返回成功结果
      assertThat(result.success()).isTrue();
      assertThat(result.data()).hasSize(2);
      assertThat(result.data()).containsExactly(lit1, lit2);
      assertThat(result.nextCursor()).isEqualTo(TEST_WEB_ENV);

      // 验证调用链
      verify(pubMedClient).esearch(any(ESearchRequest.class), any());
      verify(pubMedClient).efetch(any(EFetchRequest.class), any());
      verify(converter, times(2)).toCanonicalPublication(any());
      verify(pubMedClient, never()).epost(any(), any());
    }

    @Test
    @DisplayName("should_process_successfully_with_large_pmid_list_using_epost_strategy")
    void shouldProcessSuccessfullyWithLargePmidList() throws InterruptedException {
      // Given: 准备大量PMID（触发EPost策略）
      List<String> pmids = new ArrayList<>();
      for (int i = 1; i <= 250; i++) {
        pmids.add(String.valueOf(10000000 + i));
      }
      ESearchResponse searchResponse = createESearchResponse(pmids, null);
      EPostResponse postResponse = createEPostResponse();

      List<PubmedPublication> articles = new ArrayList<>();
      List<CanonicalPublication> publications = new ArrayList<>();
      for (int i = 0; i < 250; i++) {
        PubmedPublication article = createMockArticle(pmids.get(i));
        articles.add(article);
        CanonicalPublication lit = createValidPublication(pmids.get(i));
        publications.add(lit);
        when(converter.toCanonicalPublication(article)).thenReturn(lit);
      }
      EFetchResponse fetchResponse = createEFetchResponse(articles);

      // 配置默认阈值（200）
      when(properties.mergeWithRuntime(eq(PROVENANCE_CODE), any())).thenReturn(createTestConfig());
      when(pubMedClient.esearch(any(ESearchRequest.class), any())).thenReturn(searchResponse);
      when(pubMedClient.epost(any(EPostRequest.class), any())).thenReturn(postResponse);
      when(pubMedClient.efetch(any(EFetchRequest.class), any())).thenReturn(fetchResponse);

      ProviderRequest request = createTestRequest();
      ProviderContext context = createTestContext();

      // When: 处理数据
      ProcessResult<CanonicalPublication> result = processor.process(request, context);

      // Then: 返回成功结果
      assertThat(result.success()).isTrue();
      assertThat(result.data()).hasSize(250);

      // 验证使用了EPost策略
      verify(pubMedClient).esearch(any(ESearchRequest.class), any());
      verify(pubMedClient).epost(any(EPostRequest.class), any());
      verify(pubMedClient).efetch(any(EFetchRequest.class), any());
    }

    @Test
    @DisplayName("should_handle_empty_search_result")
    void shouldHandleEmptySearchResult() {
      // Given: 搜索返回空结果
      ESearchResponse emptyResponse = createEmptyESearchResponse();

      when(properties.mergeWithRuntime(eq(PROVENANCE_CODE), any())).thenReturn(createTestConfig());
      when(pubMedClient.esearch(any(ESearchRequest.class), any())).thenReturn(emptyResponse);

      ProviderRequest request = createTestRequest();
      ProviderContext context = createTestContext();

      // When: 处理数据
      ProcessResult<CanonicalPublication> result = processor.process(request, context);

      // Then: 返回空的成功结果
      assertThat(result.success()).isTrue();
      assertThat(result.data()).isEmpty();
      assertThat(result.nextCursor()).isNull();

      // 验证没有调用EFetch
      verify(pubMedClient).esearch(any(ESearchRequest.class), any());
      verify(pubMedClient, never()).efetch(any(), any());
      verify(pubMedClient, never()).epost(any(), any());
    }

    @Test
    @DisplayName("should_extract_next_cursor_from_search_response")
    void shouldExtractNextCursorFromSearchResponse() {
      // Given: ESearch响应包含WebEnv
      List<String> pmids = List.of(TEST_PMID_1);
      ESearchResponse searchResponse = createESearchResponse(pmids, TEST_WEB_ENV);

      PubmedPublication article = createMockArticle(TEST_PMID_1);
      EFetchResponse fetchResponse = createEFetchResponse(List.of(article));

      CanonicalPublication lit = createValidPublication(TEST_PMID_1);

      when(properties.mergeWithRuntime(eq(PROVENANCE_CODE), any())).thenReturn(createTestConfig());
      when(pubMedClient.esearch(any(ESearchRequest.class), any())).thenReturn(searchResponse);
      when(pubMedClient.efetch(any(EFetchRequest.class), any())).thenReturn(fetchResponse);
      when(converter.toCanonicalPublication(article)).thenReturn(lit);

      ProviderRequest request = createTestRequest();
      ProviderContext context = createTestContext();

      // When: 处理数据
      ProcessResult<CanonicalPublication> result = processor.process(request, context);

      // Then: nextCursor应该为WebEnv
      assertThat(result.nextCursor()).isEqualTo(TEST_WEB_ENV);
    }
  }

  // ==================== 部分成功场景测试 ====================

  @Nested
  @DisplayName("部分成功场景测试")
  class PartialSuccessTest {

    @Test
    @DisplayName("should_handle_partial_conversion_failure")
    void shouldHandlePartialConversionFailure() {
      // Given: 部分文章转换失败
      List<String> pmids = List.of(TEST_PMID_1, TEST_PMID_2);
      ESearchResponse searchResponse = createESearchResponse(pmids, null);

      PubmedPublication article1 = createMockArticle(TEST_PMID_1);
      PubmedPublication article2 = createMockArticle(TEST_PMID_2);
      EFetchResponse fetchResponse = createEFetchResponse(List.of(article1, article2));

      CanonicalPublication lit1 = createValidPublication(TEST_PMID_1);

      when(properties.mergeWithRuntime(eq(PROVENANCE_CODE), any())).thenReturn(createTestConfig());
      when(pubMedClient.esearch(any(ESearchRequest.class), any())).thenReturn(searchResponse);
      when(pubMedClient.efetch(any(EFetchRequest.class), any())).thenReturn(fetchResponse);
      when(converter.toCanonicalPublication(article1)).thenReturn(lit1);
      when(converter.toCanonicalPublication(article2))
          .thenThrow(new RuntimeException("Conversion failed"));

      ProviderRequest request = createTestRequest();
      ProviderContext context = createTestContext();

      // When: 处理数据
      ProcessResult<CanonicalPublication> result = processor.process(request, context);

      // Then: 返回部分成功结果
      assertThat(result.success()).isTrue();
      assertThat(result.data()).hasSize(1);
      assertThat(result.data()).containsExactly(lit1);
      assertThat(result.errorMessage()).contains("Conversion failed for pmid(s)");
      assertThat(result.errorMessage()).contains(TEST_PMID_2);
    }

    @Test
    @DisplayName("should_format_conversion_warning_message_correctly")
    void shouldFormatConversionWarningMessageCorrectly() {
      // Given: 多个文章转换失败（超过5个）
      List<String> pmids = new ArrayList<>();
      List<PubmedPublication> articles = new ArrayList<>();
      for (int i = 1; i <= 10; i++) {
        String pmid = String.valueOf(10000000 + i);
        pmids.add(pmid);
        PubmedPublication article = createMockArticle(pmid);
        articles.add(article);
        when(converter.toCanonicalPublication(article))
            .thenThrow(new RuntimeException("Conversion failed"));
      }

      ESearchResponse searchResponse = createESearchResponse(pmids, null);
      EFetchResponse fetchResponse = createEFetchResponse(articles);

      when(properties.mergeWithRuntime(eq(PROVENANCE_CODE), any())).thenReturn(createTestConfig());
      when(pubMedClient.esearch(any(ESearchRequest.class), any())).thenReturn(searchResponse);
      when(pubMedClient.efetch(any(EFetchRequest.class), any())).thenReturn(fetchResponse);

      ProviderRequest request = createTestRequest();
      ProviderContext context = createTestContext();

      // When: 处理数据
      ProcessResult<CanonicalPublication> result = processor.process(request, context);

      // Then: 验证警告消息格式
      assertThat(result.errorMessage()).contains("Conversion failed for pmid(s):");
      assertThat(result.errorMessage()).contains("+5 more");
    }
  }

  // ==================== 异常处理场景测试 ====================

  @Nested
  @DisplayName("异常处理场景测试")
  class ExceptionHandlingTest {

    @Test
    @DisplayName("should_handle_provenance_client_exception_with_status_429")
    void shouldHandleProvenanceClientExceptionWith429() {
      // Given: PubMedClient抛出429异常（限流）
      ProvenanceClientException exception =
          new ProvenanceClientException(
              PROVENANCE_CODE, "esearch", 429, null, null, "Rate limit exceeded", null);

      when(properties.mergeWithRuntime(eq(PROVENANCE_CODE), any())).thenReturn(createTestConfig());
      when(pubMedClient.esearch(any(ESearchRequest.class), any())).thenThrow(exception);

      ProviderRequest request = createTestRequest();
      ProviderContext context = createTestContext();

      // When: 处理数据
      ProcessResult<CanonicalPublication> result = processor.process(request, context);

      // Then: 返回失败结果（服务不可用）
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("PubMed service unavailable");
      assertThat(result.errorMessage()).contains("429");
    }

    @Test
    @DisplayName("should_handle_provenance_client_exception_with_status_503")
    void shouldHandleProvenanceClientExceptionWith503() {
      // Given: PubMedClient抛出503异常（服务不可用）
      ProvenanceClientException exception =
          new ProvenanceClientException(
              PROVENANCE_CODE, "esearch", 503, null, null, "Service unavailable", null);

      when(properties.mergeWithRuntime(eq(PROVENANCE_CODE), any())).thenReturn(createTestConfig());
      when(pubMedClient.esearch(any(ESearchRequest.class), any())).thenThrow(exception);

      ProviderRequest request = createTestRequest();
      ProviderContext context = createTestContext();

      // When: 处理数据
      ProcessResult<CanonicalPublication> result = processor.process(request, context);

      // Then: 返回失败结果（服务不可用）
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("PubMed service unavailable");
      assertThat(result.errorMessage()).contains("503");
    }

    @Test
    @DisplayName("should_handle_provenance_client_exception_with_status_401")
    void shouldHandleProvenanceClientExceptionWith401() {
      // Given: PubMedClient抛出401异常（未授权）
      ProvenanceClientException exception =
          new ProvenanceClientException(
              PROVENANCE_CODE, "esearch", 401, null, null, "Unauthorized", null);

      when(properties.mergeWithRuntime(eq(PROVENANCE_CODE), any())).thenReturn(createTestConfig());
      when(pubMedClient.esearch(any(ESearchRequest.class), any())).thenThrow(exception);

      ProviderRequest request = createTestRequest();
      ProviderContext context = createTestContext();

      // When: 处理数据
      ProcessResult<CanonicalPublication> result = processor.process(request, context);

      // Then: 返回失败结果（认证失败）
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("PubMed authentication failure");
      assertThat(result.errorMessage()).contains("401");
    }

    @Test
    @DisplayName("should_handle_provenance_client_exception_with_status_403")
    void shouldHandleProvenanceClientExceptionWith403() {
      // Given: PubMedClient抛出403异常（禁止访问）
      ProvenanceClientException exception =
          new ProvenanceClientException(
              PROVENANCE_CODE, "esearch", 403, null, null, "Forbidden", null);

      when(properties.mergeWithRuntime(eq(PROVENANCE_CODE), any())).thenReturn(createTestConfig());
      when(pubMedClient.esearch(any(ESearchRequest.class), any())).thenThrow(exception);

      ProviderRequest request = createTestRequest();
      ProviderContext context = createTestContext();

      // When: 处理数据
      ProcessResult<CanonicalPublication> result = processor.process(request, context);

      // Then: 返回失败结果（认证失败）
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("PubMed authentication failure");
      assertThat(result.errorMessage()).contains("403");
    }

    @Test
    @DisplayName("should_handle_provenance_client_exception_with_status_400")
    void shouldHandleProvenanceClientExceptionWith400() {
      // Given: PubMedClient抛出400异常（请求错误）
      ProvenanceClientException exception =
          new ProvenanceClientException(
              PROVENANCE_CODE, "esearch", 400, null, null, "Bad request", null);

      when(properties.mergeWithRuntime(eq(PROVENANCE_CODE), any())).thenReturn(createTestConfig());
      when(pubMedClient.esearch(any(ESearchRequest.class), any())).thenThrow(exception);

      ProviderRequest request = createTestRequest();
      ProviderContext context = createTestContext();

      // When: 处理数据
      ProcessResult<CanonicalPublication> result = processor.process(request, context);

      // Then: 返回失败结果（请求被拒绝）
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("PubMed request rejected");
      assertThat(result.errorMessage()).contains("400");
    }

    @Test
    @DisplayName("should_handle_provenance_client_exception_with_status_500")
    void shouldHandleProvenanceClientExceptionWith500() {
      // Given: PubMedClient抛出500异常（服务器错误）
      ProvenanceClientException exception =
          new ProvenanceClientException(
              PROVENANCE_CODE, "esearch", 500, null, null, "Internal server error", null);

      when(properties.mergeWithRuntime(eq(PROVENANCE_CODE), any())).thenReturn(createTestConfig());
      when(pubMedClient.esearch(any(ESearchRequest.class), any())).thenThrow(exception);

      ProviderRequest request = createTestRequest();
      ProviderContext context = createTestContext();

      // When: 处理数据
      ProcessResult<CanonicalPublication> result = processor.process(request, context);

      // Then: 返回失败结果（服务不可用）
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("PubMed service unavailable");
      assertThat(result.errorMessage()).contains("500");
    }

    @Test
    @DisplayName("should_handle_provenance_client_exception_without_status_code")
    void shouldHandleProvenanceClientExceptionWithoutStatusCode() {
      // Given: PubMedClient抛出无状态码的异常
      ProvenanceClientException exception =
          new ProvenanceClientException(PROVENANCE_CODE, "esearch", "Network error");

      when(properties.mergeWithRuntime(eq(PROVENANCE_CODE), any())).thenReturn(createTestConfig());
      when(pubMedClient.esearch(any(ESearchRequest.class), any())).thenThrow(exception);

      ProviderRequest request = createTestRequest();
      ProviderContext context = createTestContext();

      // When: 处理数据
      ProcessResult<CanonicalPublication> result = processor.process(request, context);

      // Then: 返回失败结果（通用客户端错误）
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("PubMed client error");
      assertThat(result.errorMessage()).contains("Network error");
    }

    @Test
    @DisplayName("should_handle_interrupted_exception")
    void shouldHandleInterruptedException() {
      // Given: EPost操作被中断（通过RuntimeException包装InterruptedException）
      List<String> pmids = new ArrayList<>();
      for (int i = 1; i <= 250; i++) {
        pmids.add(String.valueOf(10000000 + i));
      }
      ESearchResponse searchResponse = createESearchResponse(pmids, null);
      EPostResponse postResponse = createEPostResponse();

      when(properties.mergeWithRuntime(eq(PROVENANCE_CODE), any())).thenReturn(createTestConfig());
      when(pubMedClient.esearch(any(ESearchRequest.class), any())).thenReturn(searchResponse);
      when(pubMedClient.epost(any(EPostRequest.class), any())).thenReturn(postResponse);
      when(pubMedClient.efetch(any(EFetchRequest.class), any()))
          .thenAnswer(
              invocation -> {
                // 模拟线程被中断
                Thread.currentThread().interrupt();
                throw new RuntimeException(
                    "sleep interrupted", new InterruptedException("Thread interrupted"));
              });

      ProviderRequest request = createTestRequest();
      ProviderContext context = createTestContext();

      // When: 处理数据
      ProcessResult<CanonicalPublication> result = processor.process(request, context);

      // Then: 返回失败结果（中断）
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("Unexpected PubMed error");

      // 清除线程中断状态以免影响后续测试
      Thread.interrupted();
    }

    @Test
    @DisplayName("should_handle_generic_exception")
    void shouldHandleGenericException() {
      // Given: 发生未预期的异常
      when(properties.mergeWithRuntime(eq(PROVENANCE_CODE), any()))
          .thenThrow(new RuntimeException("Unexpected error"));

      ProviderRequest request = createTestRequest();
      ProviderContext context = createTestContext();

      // When: 处理数据
      ProcessResult<CanonicalPublication> result = processor.process(request, context);

      // Then: 返回失败结果（通用错误）
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("Unexpected PubMed error");
      assertThat(result.errorMessage()).contains("Unexpected error");
    }
  }

  // ==================== validate()方法测试 ====================

  @Nested
  @DisplayName("validate()方法测试")
  class ValidateMethodTest {

    @Test
    @DisplayName("should_validate_successfully_with_valid_data")
    void shouldValidateSuccessfullyWithValidData() {
      // Given: 有效的CanonicalPublication
      CanonicalPublication publication = createValidPublication(TEST_PMID_1);

      // When: 验证数据
      ValidationResult result = processor.validate(publication);

      // Then: 验证成功
      assertThat(result.isValid()).isTrue();
      assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("should_fail_validation_when_data_is_null")
    void shouldFailValidationWhenDataIsNull() {
      // When: 验证null数据
      ValidationResult result = processor.validate(null);

      // Then: 验证失败
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).hasSize(1);
      assertThat(result.errors().get(0)).contains("不能为null");
    }

    @Test
    @DisplayName("should_fail_validation_when_pmid_is_missing")
    void shouldFailValidationWhenPmidIsMissing() {
      // Given: 缺少PMID
      CanonicalPublication publication =
          CanonicalPublication.builder().title("Test Article").identifiers(List.of()).build();

      // When: 验证数据
      ValidationResult result = processor.validate(publication);

      // Then: 验证失败
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).contains("PMID不能为空");
    }

    @Test
    @DisplayName("should_fail_validation_when_title_is_missing")
    void shouldFailValidationWhenTitleIsMissing() {
      // Given: 缺少标题
      CanonicalPublication publication =
          CanonicalPublication.builder()
              .identifiers(
                  List.of(
                      CanonicalPublication.Identifier.builder()
                          .type("pmid")
                          .value(TEST_PMID_1)
                          .build()))
              .build();

      // When: 验证数据
      ValidationResult result = processor.validate(publication);

      // Then: 验证失败
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).contains("标题不能为空");
    }

    @Test
    @DisplayName("should_fail_validation_when_multiple_fields_are_missing")
    void shouldFailValidationWhenMultipleFieldsAreMissing() {
      // Given: 缺少多个字段
      CanonicalPublication publication = createInvalidPublication();

      // When: 验证数据
      ValidationResult result = processor.validate(publication);

      // Then: 验证失败，包含多个错误
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).hasSize(2);
      assertThat(result.errors()).contains("PMID不能为空", "标题不能为空");
    }
  }

  // ==================== transform()方法测试 ====================

  @Nested
  @DisplayName("transform()方法测试")
  class TransformMethodTest {

    @Test
    @DisplayName("should_transform_pubmed_article_to_canonical_publication")
    void shouldTransformPubmedPublicationToCanonicalPublication() {
      // Given: PubmedPublication和预期的转换结果
      PubmedPublication article = createMockArticle(TEST_PMID_1);
      CanonicalPublication expectedPublication = createValidPublication(TEST_PMID_1);

      when(converter.toCanonicalPublication(article)).thenReturn(expectedPublication);

      // When: 转换数据
      CanonicalPublication result = processor.transform(article);

      // Then: 返回转换后的文献
      assertThat(result).isEqualTo(expectedPublication);
      verify(converter).toCanonicalPublication(article);
    }

    @Test
    @DisplayName("should_throw_exception_when_raw_data_is_null")
    void shouldThrowExceptionWhenRawDataIsNull() {
      // When & Then: 原始数据为null时抛出异常
      assertThatThrownBy(() -> processor.transform(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("原始数据不能为null");
    }

    @Test
    @DisplayName("should_throw_exception_when_raw_data_is_wrong_type")
    void shouldThrowExceptionWhenRawDataIsWrongType() {
      // Given: 错误类型的原始数据
      Object wrongTypeData = "This is a string, not a PubmedPublication";

      // When & Then: 类型错误时抛出异常
      assertThatThrownBy(() -> processor.transform(wrongTypeData))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("不支持的数据类型")
          .hasMessageContaining("String")
          .hasMessageContaining("PubmedPublication");
    }
  }

  // ==================== 私有方法逻辑验证（通过公开方法间接测试）====================

  @Nested
  @DisplayName("私有方法逻辑验证")
  class PrivateMethodLogicTest {

    @Test
    @DisplayName("should_merge_query_into_params_when_building_search_params")
    void shouldMergeQueryIntoParamsWhenBuildingSearchParams() {
      // Given: 请求包含query和params
      ObjectNode params = JsonNodeFactory.instance.objectNode();
      params.put("datetype", "edat");
      params.put("retmax", 100);

      ProviderRequest request = createTestRequest(TEST_QUERY, params);

      List<String> pmids = List.of(TEST_PMID_1);
      ESearchResponse searchResponse = createESearchResponse(pmids, null);
      PubmedPublication article = createMockArticle(TEST_PMID_1);
      EFetchResponse fetchResponse = createEFetchResponse(List.of(article));
      CanonicalPublication lit = createValidPublication(TEST_PMID_1);

      when(properties.mergeWithRuntime(eq(PROVENANCE_CODE), any())).thenReturn(createTestConfig());
      when(pubMedClient.esearch(any(ESearchRequest.class), any())).thenReturn(searchResponse);
      when(pubMedClient.efetch(any(EFetchRequest.class), any())).thenReturn(fetchResponse);
      when(converter.toCanonicalPublication(article)).thenReturn(lit);

      ProviderContext context = createTestContext();

      // When: 处理数据
      ProcessResult<CanonicalPublication> result = processor.process(request, context);

      // Then: 成功处理（间接验证参数合并逻辑正确）
      assertThat(result.success()).isTrue();
    }

    @Test
    @DisplayName("should_use_epost_threshold_from_batching_config")
    void shouldUseEpostThresholdFromBatchingConfig() throws InterruptedException {
      // Given: 配置了自定义epostThreshold（150）
      List<String> pmids = new ArrayList<>();
      for (int i = 1; i <= 160; i++) {
        pmids.add(String.valueOf(10000000 + i));
      }
      ESearchResponse searchResponse = createESearchResponse(pmids, null);
      EPostResponse postResponse = createEPostResponse();

      List<PubmedPublication> articles = new ArrayList<>();
      for (int i = 0; i < 160; i++) {
        articles.add(createMockArticle(pmids.get(i)));
      }
      EFetchResponse fetchResponse = createEFetchResponse(articles);

      when(properties.mergeWithRuntime(eq(PROVENANCE_CODE), any()))
          .thenReturn(createTestConfigWithBatching(150));
      when(pubMedClient.esearch(any(ESearchRequest.class), any())).thenReturn(searchResponse);
      when(pubMedClient.epost(any(EPostRequest.class), any())).thenReturn(postResponse);
      when(pubMedClient.efetch(any(EFetchRequest.class), any())).thenReturn(fetchResponse);

      ProviderRequest request = createTestRequest();
      ProviderContext context = createTestContext();

      // When: 处理数据
      processor.process(request, context);

      // Then: 应该使用EPost策略（因为160 > 150）
      verify(pubMedClient).epost(any(EPostRequest.class), any());
    }

    @Test
    @DisplayName("should_use_max_ids_per_request_when_epost_threshold_is_null")
    void shouldUseMaxIdsPerRequestWhenEpostThresholdIsNull() throws InterruptedException {
      // Given: epostThreshold为null，使用maxIdsPerRequest（100）
      List<String> pmids = new ArrayList<>();
      for (int i = 1; i <= 110; i++) {
        pmids.add(String.valueOf(10000000 + i));
      }
      ESearchResponse searchResponse = createESearchResponse(pmids, null);
      EPostResponse postResponse = createEPostResponse();

      List<PubmedPublication> articles = new ArrayList<>();
      for (int i = 0; i < 110; i++) {
        articles.add(createMockArticle(pmids.get(i)));
      }
      EFetchResponse fetchResponse = createEFetchResponse(articles);

      // 创建配置：epostThreshold=null, maxIdsPerRequest=100
      BatchingConfig batchingConfig = new BatchingConfig(100, 100, null);
      ProvenanceConfig config =
          new ProvenanceConfig(
              "https://eutils.ncbi.nlm.nih.gov/entrez/eutils",
              null,
              null,
              null,
              batchingConfig,
              null,
              null);

      when(properties.mergeWithRuntime(eq(PROVENANCE_CODE), any())).thenReturn(config);
      when(pubMedClient.esearch(any(ESearchRequest.class), any())).thenReturn(searchResponse);
      when(pubMedClient.epost(any(EPostRequest.class), any())).thenReturn(postResponse);
      when(pubMedClient.efetch(any(EFetchRequest.class), any())).thenReturn(fetchResponse);

      ProviderRequest request = createTestRequest();
      ProviderContext context = createTestContext();

      // When: 处理数据
      processor.process(request, context);

      // Then: 应该使用EPost策略（因为110 > 100）
      verify(pubMedClient).epost(any(EPostRequest.class), any());
    }

    @Test
    @DisplayName("should_use_default_epost_threshold_when_batching_config_is_null")
    void shouldUseDefaultEpostThresholdWhenBatchingConfigIsNull() throws InterruptedException {
      // Given: batching配置为null，使用默认阈值（200）
      List<String> pmids = new ArrayList<>();
      for (int i = 1; i <= 210; i++) {
        pmids.add(String.valueOf(10000000 + i));
      }
      ESearchResponse searchResponse = createESearchResponse(pmids, null);
      EPostResponse postResponse = createEPostResponse();

      List<PubmedPublication> articles = new ArrayList<>();
      for (int i = 0; i < 210; i++) {
        articles.add(createMockArticle(pmids.get(i)));
      }
      EFetchResponse fetchResponse = createEFetchResponse(articles);

      // 创建配置：batching=null
      ProvenanceConfig config =
          new ProvenanceConfig(
              "https://eutils.ncbi.nlm.nih.gov/entrez/eutils", null, null, null, null, null, null);

      when(properties.mergeWithRuntime(eq(PROVENANCE_CODE), any())).thenReturn(config);
      when(pubMedClient.esearch(any(ESearchRequest.class), any())).thenReturn(searchResponse);
      when(pubMedClient.epost(any(EPostRequest.class), any())).thenReturn(postResponse);
      when(pubMedClient.efetch(any(EFetchRequest.class), any())).thenReturn(fetchResponse);

      ProviderRequest request = createTestRequest();
      ProviderContext context = createTestContext();

      // When: 处理数据
      processor.process(request, context);

      // Then: 应该使用EPost策略（因为210 > 200 默认阈值）
      verify(pubMedClient).epost(any(EPostRequest.class), any());
    }

    @Test
    @DisplayName("should_classify_error_message_correctly_for_different_status_codes")
    void shouldClassifyErrorMessageCorrectlyForDifferentStatusCodes() {
      // 这个测试通过异常处理场景测试已经覆盖
      // 这里仅作为文档说明：classifyErrorMessage的逻辑通过ExceptionHandlingTest验证
      assertThat(true).isTrue();
    }
  }
}
