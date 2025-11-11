package com.patra.ingest.infra.integration.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.model.CanonicalLiterature;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.port.DataSourcePort.DataFetchResult;
import com.patra.starter.provenance.common.adapter.AdapterRegistry;
import com.patra.starter.provenance.common.adapter.AdapterRequest;
import com.patra.starter.provenance.common.adapter.AdapterResult;
import com.patra.starter.provenance.common.adapter.DataSourceAdapter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link DataSourcePortAdapter} 单元测试
 *
 * <p>测试策略:
 *
 * <ul>
 *   <li>Mock {@link AdapterRegistry} 和 {@link DataSourceAdapter}
 *   <li>验证参数转换逻辑正确
 *   <li>验证结果转换逻辑正确
 *   <li>验证错误处理逻辑
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataSourcePortAdapter 单元测试")
class DataSourcePortAdapterTest {

  @Mock private AdapterRegistry adapterRegistry;

  @Mock private DataSourceAdapter dataSourceAdapter;

  private DataSourcePortAdapter adapter;

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    adapter = new DataSourcePortAdapter(adapterRegistry);
    objectMapper = new ObjectMapper();
  }

  @Nested
  @DisplayName("正常场景测试")
  class HappyPathTests {

    @Test
    @DisplayName("成功获取数据 - 基础场景")
    void shouldFetchDataSuccessfully() {
      // Given: 准备执行上下文和批次
      ExecutionContext context = buildExecutionContext("pubmed", "HARVEST");
      Batch batch = Batch.withPage(1, "covid-19", buildParams(), 1, 100);

      // Mock 适配器返回成功结果
      when(adapterRegistry.getAdapter("pubmed")).thenReturn(dataSourceAdapter);
      List<CanonicalLiterature> literatures = List.of(buildCanonicalLiterature("Test Literature"));
      AdapterResult adapterResult = AdapterResult.success(literatures, "cursor-token-1");
      when(dataSourceAdapter.fetchData(any(AdapterRequest.class))).thenReturn(adapterResult);

      // When: 调用 fetchData
      DataFetchResult result = adapter.fetchData(context, batch);

      // Then: 验证结果
      assertThat(result).isNotNull();
      assertThat(result.success()).isTrue();
      assertThat(result.literatures()).hasSize(1);
      assertThat(result.literatures().get(0).getTitle()).isEqualTo("Test Literature");
      assertThat(result.nextCursorToken()).isEqualTo("cursor-token-1");
      assertThat(result.fetchedCount()).isEqualTo(1);
      assertThat(result.errorType()).isEqualTo(DataFetchResult.ErrorType.NONE);

      // Verify: 验证调用了正确的方法
      verify(adapterRegistry).getAdapter("pubmed");
      verify(dataSourceAdapter).fetchData(any(AdapterRequest.class));
    }

    @Test
    @DisplayName("成功获取数据 - 验证请求参数构建正确")
    void shouldBuildRequestParametersCorrectly() {
      // Given: 准备执行上下文和批次
      ExecutionContext context = buildExecutionContext("pubmed", "UPDATE");
      Batch batch = Batch.withToken(2, "covid-19", buildParams(), "cursor-token-1", 50);

      // Mock 适配器
      when(adapterRegistry.getAdapter("pubmed")).thenReturn(dataSourceAdapter);
      when(dataSourceAdapter.fetchData(any(AdapterRequest.class)))
          .thenReturn(AdapterResult.success(List.of(), null));

      // When: 调用 fetchData
      adapter.fetchData(context, batch);

      // Then: 捕获并验证 AdapterRequest
      ArgumentCaptor<AdapterRequest> requestCaptor = ArgumentCaptor.forClass(AdapterRequest.class);
      verify(dataSourceAdapter).fetchData(requestCaptor.capture());

      AdapterRequest capturedRequest = requestCaptor.getValue();
      assertThat(capturedRequest.operationCode()).isEqualTo("UPDATE");
      assertThat(capturedRequest.executionParams()).isNotNull();
      assertThat(capturedRequest.executionParams().query()).isEqualTo("covid-19");
      assertThat(capturedRequest.metadata()).isNotNull();
      assertThat(capturedRequest.metadata().batchNo()).isEqualTo(2);
      assertThat(capturedRequest.metadata().cursorToken()).isEqualTo("cursor-token-1");
    }

    @Test
    @DisplayName("成功获取数据 - 空数据列表")
    void shouldHandleEmptyLiteratureList() {
      // Given: Mock 适配器返回空列表
      ExecutionContext context = buildExecutionContext("pubmed", "HARVEST");
      Batch batch = Batch.withPage(1, "query", buildParams(), 1, 100);

      when(adapterRegistry.getAdapter("pubmed")).thenReturn(dataSourceAdapter);
      AdapterResult adapterResult = AdapterResult.success(List.of(), null);
      when(dataSourceAdapter.fetchData(any(AdapterRequest.class))).thenReturn(adapterResult);

      // When: 调用 fetchData
      DataFetchResult result = adapter.fetchData(context, batch);

      // Then: 验证结果
      assertThat(result.success()).isTrue();
      assertThat(result.literatures()).isEmpty();
      assertThat(result.fetchedCount()).isZero();
      assertThat(result.nextCursorToken()).isNull();
    }

    @Test
    @DisplayName("成功获取数据 - 部分成功场景")
    void shouldHandlePartialSuccess() {
      // Given: Mock 适配器返回部分成功
      ExecutionContext context = buildExecutionContext("pubmed", "HARVEST");
      Batch batch = Batch.withPage(1, "query", buildParams(), 1, 100);

      when(adapterRegistry.getAdapter("pubmed")).thenReturn(dataSourceAdapter);
      List<CanonicalLiterature> literatures = List.of(buildCanonicalLiterature("Test Literature"));
      AdapterResult adapterResult =
          AdapterResult.partialSuccess(
              literatures,
              "cursor-token-1",
              "部分记录转换失败",
              10);
      when(dataSourceAdapter.fetchData(any(AdapterRequest.class))).thenReturn(adapterResult);

      // When: 调用 fetchData
      DataFetchResult result = adapter.fetchData(context, batch);

      // Then: 验证结果
      assertThat(result.success()).isTrue();
      assertThat(result.literatures()).hasSize(1);
      assertThat(result.errorType()).isEqualTo(DataFetchResult.ErrorType.PARTIAL_SUCCESS);
      assertThat(result.errorMessage()).isEqualTo("部分记录转换失败");
      assertThat(result.fetchedCount()).isEqualTo(10);
    }
  }

  @Nested
  @DisplayName("错误场景测试")
  class ErrorPathTests {

    @Test
    @DisplayName("适配器返回可重试失败")
    void shouldHandleRetriableFailure() {
      // Given: Mock 适配器返回可重试失败
      ExecutionContext context = buildExecutionContext("pubmed", "HARVEST");
      Batch batch = Batch.withPage(1, "query", buildParams(), 1, 100);

      when(adapterRegistry.getAdapter("pubmed")).thenReturn(dataSourceAdapter);
      AdapterResult adapterResult = AdapterResult.retriableFailure("网络超时");
      when(dataSourceAdapter.fetchData(any(AdapterRequest.class))).thenReturn(adapterResult);

      // When: 调用 fetchData
      DataFetchResult result = adapter.fetchData(context, batch);

      // Then: 验证结果
      assertThat(result.success()).isFalse();
      assertThat(result.errorType()).isEqualTo(DataFetchResult.ErrorType.RETRIABLE);
      assertThat(result.errorMessage()).isEqualTo("网络超时");
      assertThat(result.isRetriable()).isTrue();
      assertThat(result.literatures()).isEmpty();
    }

    @Test
    @DisplayName("适配器返回不可重试失败")
    void shouldHandleNonRetriableFailure() {
      // Given: Mock 适配器返回不可重试失败
      ExecutionContext context = buildExecutionContext("pubmed", "HARVEST");
      Batch batch = Batch.withPage(1, "query", buildParams(), 1, 100);

      when(adapterRegistry.getAdapter("pubmed")).thenReturn(dataSourceAdapter);
      AdapterResult adapterResult = AdapterResult.nonRetriableFailure("认证失败");
      when(dataSourceAdapter.fetchData(any(AdapterRequest.class))).thenReturn(adapterResult);

      // When: 调用 fetchData
      DataFetchResult result = adapter.fetchData(context, batch);

      // Then: 验证结果
      assertThat(result.success()).isFalse();
      assertThat(result.errorType()).isEqualTo(DataFetchResult.ErrorType.NON_RETRIABLE);
      assertThat(result.errorMessage()).isEqualTo("认证失败");
      assertThat(result.isRetriable()).isFalse();
    }

    @Test
    @DisplayName("未找到适配器")
    void shouldHandleAdapterNotFound() {
      // Given: Mock 注册表返回 null
      ExecutionContext context = buildExecutionContext("unknown", "HARVEST");
      Batch batch = Batch.withPage(1, "query", buildParams(), 1, 100);

      when(adapterRegistry.getAdapter("unknown")).thenReturn(null);

      // When: 调用 fetchData
      DataFetchResult result = adapter.fetchData(context, batch);

      // Then: 验证结果
      assertThat(result.success()).isFalse();
      assertThat(result.errorType()).isEqualTo(DataFetchResult.ErrorType.NON_RETRIABLE);
      assertThat(result.errorMessage()).contains("未找到适配器");
      assertThat(result.errorMessage()).contains("unknown");
    }

    @Test
    @DisplayName("适配器抛出异常")
    void shouldHandleAdapterException() {
      // Given: Mock 适配器抛出异常
      ExecutionContext context = buildExecutionContext("pubmed", "HARVEST");
      Batch batch = Batch.withPage(1, "query", buildParams(), 1, 100);

      when(adapterRegistry.getAdapter("pubmed")).thenReturn(dataSourceAdapter);
      when(dataSourceAdapter.fetchData(any(AdapterRequest.class)))
          .thenThrow(new RuntimeException("模拟异常"));

      // When: 调用 fetchData
      DataFetchResult result = adapter.fetchData(context, batch);

      // Then: 验证结果
      assertThat(result.success()).isFalse();
      assertThat(result.errorType()).isEqualTo(DataFetchResult.ErrorType.RETRIABLE);
      assertThat(result.errorMessage()).contains("数据源适配器调用异常");
      assertThat(result.errorMessage()).contains("模拟异常");
    }

    @Test
    @DisplayName("注册表抛出异常")
    void shouldHandleRegistryException() {
      // Given: Mock 注册表抛出异常
      ExecutionContext context = buildExecutionContext("pubmed", "HARVEST");
      Batch batch = Batch.withPage(1, "query", buildParams(), 1, 100);

      when(adapterRegistry.getAdapter("pubmed")).thenThrow(new RuntimeException("注册表异常"));

      // When: 调用 fetchData
      DataFetchResult result = adapter.fetchData(context, batch);

      // Then: 验证结果
      assertThat(result.success()).isFalse();
      assertThat(result.errorType()).isEqualTo(DataFetchResult.ErrorType.NON_RETRIABLE);
      assertThat(result.errorMessage()).contains("未找到适配器");
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class EdgeCaseTests {

    @Test
    @DisplayName("游标令牌为空")
    void shouldHandleNullCursorToken() {
      // Given: 批次无游标令牌
      ExecutionContext context = buildExecutionContext("pubmed", "HARVEST");
      Batch batch = Batch.withPage(1, "query", buildParams(), 1, 100);

      when(adapterRegistry.getAdapter("pubmed")).thenReturn(dataSourceAdapter);
      when(dataSourceAdapter.fetchData(any(AdapterRequest.class)))
          .thenReturn(AdapterResult.success(List.of(), null));

      // When: 调用 fetchData
      DataFetchResult result = adapter.fetchData(context, batch);

      // Then: 验证结果
      assertThat(result.success()).isTrue();
      assertThat(result.nextCursorToken()).isNull();
    }

    @Test
    @DisplayName("配置快照为空 - 使用默认配置")
    void shouldHandleNullConfigSnapshot() {
      // Given: 执行上下文的配置快照为 null
      ExecutionContext context =
          new ExecutionContext(
              1L,
              100L,
              10L,
              1L,
              1000L,
              "pubmed",
              "HARVEST",
              null, // configSnapshot 为 null
              "expr-hash",
              "covid-19",
              buildParams(),
              "normalized-expr",
              null);
      Batch batch = Batch.withPage(1, "query", buildParams(), 1, 100);

      when(adapterRegistry.getAdapter("pubmed")).thenReturn(dataSourceAdapter);
      when(dataSourceAdapter.fetchData(any(AdapterRequest.class)))
          .thenReturn(AdapterResult.success(List.of(), null));

      // When: 调用 fetchData
      DataFetchResult result = adapter.fetchData(context, batch);

      // Then: 验证结果 (应该成功,使用默认配置)
      assertThat(result.success()).isTrue();

      // Verify: 验证传递给适配器的 config 为 null (使用默认配置)
      ArgumentCaptor<AdapterRequest> requestCaptor = ArgumentCaptor.forClass(AdapterRequest.class);
      verify(dataSourceAdapter).fetchData(requestCaptor.capture());
      assertThat(requestCaptor.getValue().config()).isNull();
    }

    @Test
    @DisplayName("批次查询为空 - 使用上下文查询")
    void shouldFallbackToContextQueryWhenBatchQueryIsNull() {
      // Given: Batch.query 为空字符串
      ExecutionContext context = buildExecutionContext("pubmed", "HARVEST");
      Batch batch = Batch.withPage(1, "", buildParams(), 1, 100);

      when(adapterRegistry.getAdapter("pubmed")).thenReturn(dataSourceAdapter);
      when(dataSourceAdapter.fetchData(any(AdapterRequest.class)))
          .thenReturn(AdapterResult.success(List.of(), null));

      // When: 调用 fetchData
      adapter.fetchData(context, batch);

      // Then: 验证使用了 ExecutionContext.compiledQuery
      ArgumentCaptor<AdapterRequest> requestCaptor = ArgumentCaptor.forClass(AdapterRequest.class);
      verify(dataSourceAdapter).fetchData(requestCaptor.capture());
      assertThat(requestCaptor.getValue().executionParams().query())
          .isEqualTo("compiled-query-from-context");
    }
  }

  // ==================== 辅助方法 ====================

  /**
   * 构建执行上下文
   *
   * @param provenanceCode Provenance 代码
   * @param operationCode 操作代码
   * @return 执行上下文
   */
  private ExecutionContext buildExecutionContext(String provenanceCode, String operationCode) {
    ProvenanceConfigSnapshot configSnapshot = buildConfigSnapshot();
    return new ExecutionContext(
        1L,
        100L,
        10L,
        1L,
        1000L,
        provenanceCode,
        operationCode,
        configSnapshot,
        "expr-hash",
        "compiled-query-from-context",
        buildParams(),
        "normalized-expr",
        null);
  }

  /**
   * 构建配置快照
   *
   * @return 配置快照
   */
  private ProvenanceConfigSnapshot buildConfigSnapshot() {
    ProvenanceConfigSnapshot.ProvenanceInfo provenance =
        new ProvenanceConfigSnapshot.ProvenanceInfo(
            1L,
            "pubmed",
            "PubMed",
            "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/",
            "UTC",
            "https://www.ncbi.nlm.nih.gov/books/NBK25500/",
            true,
            "ACTIVE");

    ProvenanceConfigSnapshot.HttpConfig http =
        new ProvenanceConfigSnapshot.HttpConfig(
            1L,
            1L,
            null,
            null,
            null,
            "{}", // defaultHeadersJson
            5000, // timeoutConnectMillis
            10000, // timeoutReadMillis
            30000, // timeoutTotalMillis
            false,
            null,
            null,
            null,
            null,
            null);

    ProvenanceConfigSnapshot.PaginationConfig pagination =
        new ProvenanceConfigSnapshot.PaginationConfig(
            1L,
            1L,
            null,
            null,
            null,
            "PAGE_NUMBER",
            100, // pageSizeValue
            10, // maxPagesPerExecution
            null,
            null);

    return new ProvenanceConfigSnapshot(provenance, null, pagination, http, null, null, null);
  }

  /**
   * 构建参数
   *
   * @return JSON 参数
   */
  private JsonNode buildParams() {
    ObjectNode params = objectMapper.createObjectNode();
    params.put("datetype", "pdat");
    params.put("sort", "date");
    return params;
  }

  /**
   * 构建规范化文献
   *
   * @param title 文献标题
   * @return 规范化文献
   */
  private CanonicalLiterature buildCanonicalLiterature(String title) {
    return CanonicalLiterature.builder()
        .title(title)
        .abstractText("Test abstract")
        .build();
  }
}
