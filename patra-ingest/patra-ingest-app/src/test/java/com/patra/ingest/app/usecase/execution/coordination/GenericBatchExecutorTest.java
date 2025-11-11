package com.patra.ingest.app.usecase.execution.coordination;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.patra.common.model.StandardLiterature;
import com.patra.ingest.app.usecase.execution.converter.ProvenanceConfigConverter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.batch.BatchResult;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.starter.provenance.common.adapter.*;
import com.patra.starter.provenance.common.adapter.AdapterResult.ErrorType;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * GenericBatchExecutor 单元测试
 *
 * <p>测试覆盖:
 *
 * <ul>
 *   <li>✅ 正常执行：调用数据源适配器成功
 *   <li>✅ 执行失败：适配器抛出异常
 *   <li>✅ 批次参数传递：验证传递给适配器的参数
 *   <li>✅ 结果映射：验证返回的 BatchResult
 *   <li>✅ 重试逻辑：可重试错误的重试流程
 *   <li>✅ 非重试错误：不可重试错误的快速失败
 *   <li>✅ 文献发布：成功发布文献到下游
 *   <li>✅ 空文献列表：处理空的文献列表
 *   <li>✅ 边界条件：null 参数、空批次号等
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("GenericBatchExecutor 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GenericBatchExecutorTest {

  @Mock private AdapterRegistry adapterRegistry;
  @Mock private LiteraturePublisherOrchestrator literaturePublisherOrchestrator;
  @Mock private ProvenanceConfigConverter configConverter;

  @Mock private DataSourcePort mockAdapter;

  @Captor private ArgumentCaptor<AdapterRequest> requestCaptor;

  @InjectMocks private GenericBatchExecutor executor;

  private ExecutionContext context;
  private Batch batch;
  private ProvenanceConfig runtimeConfig;
  private ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    // 准备基础测试数据
    com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.ProvenanceInfo provenanceInfo =
        new com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.ProvenanceInfo(
            1L, "pubmed", "PubMed", "https://test.example.com", "UTC", "https://docs.example.com", true, "ACTIVE");

    com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot configSnapshot =
        new com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot(
            provenanceInfo, null, null, null, null, null, null);

    context =
        new ExecutionContext(
            1L, // taskId
            1001L, // runId
            null, // planId
            null, // sliceId
            null, // scheduleInstanceId
            "pubmed", // provenanceCode
            "harvest", // operationCode
            configSnapshot, // configSnapshot
            null, // exprHash
            null, // compiledQuery
            null, // compiledParams
            null, // normalizedExpression
            null // windowSpec
            );

    ObjectNode params = objectMapper.createObjectNode();
    params.put("param1", "value1");
    batch = Batch.withToken(1, "query1", params, "cursor1", 100);

    runtimeConfig = createTestConfig();

    // Mock 通用行为
    lenient().when(adapterRegistry.getAdapter("pubmed")).thenReturn(mockAdapter);
    lenient()
        .when(configConverter.convert(eq("pubmed"), any()))
        .thenReturn(runtimeConfig);
  }

  // ==================== 正常执行场景 ====================

  @Nested
  @DisplayName("正常执行场景")
  class HappyPathTests {

    @Test
    @DisplayName("应该成功执行批次并返回成功结果")
    void shouldExecuteBatchSuccessfully() {
      // Given: Mock 适配器返回成功结果
      List<StandardLiterature> literatures = createTestLiteratures(5);
      AdapterResult adapterResult = AdapterResult.success(literatures, "nextCursor123");

      when(mockAdapter.fetchData(any(AdapterRequest.class))).thenReturn(adapterResult);

      // Mock 文献发布
      LiteraturePublisherOrchestrator.PublishResult publishResult =
          LiteraturePublisherOrchestrator.PublishResult.builder()
              .publishedCount(5)
              .storageKey("s3://bucket/pubmed/run-1/batch-1.json")
              .build();
      when(literaturePublisherOrchestrator.publish(any(), any())).thenReturn(publishResult);

      // When: 执行批次
      BatchResult result = executor.execute(context, batch);

      // Then: 验证返回成功结果
      assertThat(result.success()).isTrue();
      assertThat(result.batchNo()).isEqualTo(1);
      assertThat(result.fetchedCount()).isEqualTo(5);
      assertThat(result.nextCursorToken()).isEqualTo("nextCursor123");
      assertThat(result.storageKey()).isEqualTo("s3://bucket/pubmed/run-1/batch-1.json");
      assertThat(result.errorMessage()).isNull();

      // 验证调用链
      verify(adapterRegistry).getAdapter("pubmed");
      verify(configConverter).convert(eq("pubmed"), any());
      verify(mockAdapter).fetchData(any(AdapterRequest.class));
      verify(literaturePublisherOrchestrator).publish(eq(literatures), any());
    }

    @Test
    @DisplayName("应该正确传递批次参数到适配器")
    void shouldPassCorrectParametersToAdapter() {
      // Given
      AdapterResult adapterResult = AdapterResult.success(List.of(), null);
      when(mockAdapter.fetchData(any(AdapterRequest.class))).thenReturn(adapterResult);

      // Mock 空文献发布
      mockEmptyPublish();

      // When: 执行批次
      executor.execute(context, batch);

      // Then: 验证适配器请求参数
      verify(mockAdapter).fetchData(requestCaptor.capture());
      AdapterRequest capturedRequest = requestCaptor.getValue();

      assertThat(capturedRequest.operationCode()).isEqualTo("harvest");
      assertThat(capturedRequest.config()).isEqualTo(runtimeConfig);
      assertThat(capturedRequest.executionParams().query()).isEqualTo("query1");
      assertThat(capturedRequest.executionParams().params().get("param1").asText())
          .isEqualTo("value1");
      assertThat(capturedRequest.metadata().batchNo()).isEqualTo(1);
      assertThat(capturedRequest.metadata().cursorToken()).isEqualTo("cursor1");
    }

    @Test
    @DisplayName("应该正确处理空文献列表")
    void shouldHandleEmptyLiteratureList() {
      // Given: 适配器返回空文献列表
      AdapterResult adapterResult = AdapterResult.success(List.of(), null);
      when(mockAdapter.fetchData(any(AdapterRequest.class))).thenReturn(adapterResult);

      // When: 执行批次
      BatchResult result = executor.execute(context, batch);

      // Then: 返回成功但 fetchedCount 为 0
      assertThat(result.success()).isTrue();
      assertThat(result.fetchedCount()).isZero();
      assertThat(result.storageKey()).isNull();

      // 验证不调用发布器（因为文献列表为空）
      verifyNoInteractions(literaturePublisherOrchestrator);
    }

    @Test
    @DisplayName("应该正确处理 null 文献列表")
    void shouldHandleNullLiteratureList() {
      // Given: 适配器返回 null 文献列表
      AdapterResult adapterResult = AdapterResult.success(null, null);
      when(mockAdapter.fetchData(any(AdapterRequest.class))).thenReturn(adapterResult);

      // When: 执行批次
      BatchResult result = executor.execute(context, batch);

      // Then: 返回成功
      assertThat(result.success()).isTrue();
      assertThat(result.fetchedCount()).isZero();

      // 验证不调用发布器
      verifyNoInteractions(literaturePublisherOrchestrator);
    }
  }

  // ==================== 重试逻辑场景 ====================

  @Nested
  @DisplayName("重试逻辑场景")
  class RetryTests {

    @Test
    @DisplayName("可重试错误应该执行重试并最终成功")
    void shouldRetryOnRetriableErrorAndSucceed() {
      // Given: 第一次失败，第二次成功
      AdapterResult failureResult = AdapterResult.retriableFailure("临时网络错误");
      AdapterResult successResult = AdapterResult.success(createTestLiteratures(3), "nextCursor");

      when(mockAdapter.fetchData(any(AdapterRequest.class)))
          .thenReturn(failureResult)
          .thenReturn(successResult);

      mockPublish(3, "s3://bucket/key");

      // When: 执行批次
      BatchResult result = executor.execute(context, batch);

      // Then: 最终成功
      assertThat(result.success()).isTrue();
      assertThat(result.fetchedCount()).isEqualTo(3);

      // 验证调用了 2 次适配器（1 次失败 + 1 次成功）
      verify(mockAdapter, times(2)).fetchData(any(AdapterRequest.class));
    }

    @Test
    @DisplayName("可重试错误达到最大重试次数应该返回失败")
    void shouldFailAfterMaxRetries() {
      // Given: 所有尝试都失败
      AdapterResult failureResult = AdapterResult.retriableFailure("持续网络错误");

      when(mockAdapter.fetchData(any(AdapterRequest.class))).thenReturn(failureResult);

      // When: 执行批次
      BatchResult result = executor.execute(context, batch);

      // Then: 返回失败
      assertThat(result.success()).isFalse();
      assertThat(result.batchNo()).isEqualTo(1);
      assertThat(result.errorMessage()).contains("RETRIABLE").contains("持续网络错误");

      // 验证调用了 4 次（默认 3 次重试 + 1 次初始尝试）
      verify(mockAdapter, times(4)).fetchData(any(AdapterRequest.class));

      // 验证没有发布文献
      verifyNoInteractions(literaturePublisherOrchestrator);
    }

    @Test
    @DisplayName("非重试错误应该立即返回失败")
    void shouldFailImmediatelyOnNonRetriableError() {
      // Given: 非重试错误
      AdapterResult failureResult = AdapterResult.nonRetriableFailure("API 密钥无效");

      when(mockAdapter.fetchData(any(AdapterRequest.class))).thenReturn(failureResult);

      // When: 执行批次
      BatchResult result = executor.execute(context, batch);

      // Then: 立即失败，不重试
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("API 密钥无效");

      // 验证只调用了 1 次（不重试）
      verify(mockAdapter, times(1)).fetchData(any(AdapterRequest.class));
    }

    @Test
    @DisplayName("重试期间线程中断应该返回失败")
    void shouldHandleInterruptDuringRetry() {
      // Given: 第一次失败（可重试）
      AdapterResult failureResult = AdapterResult.retriableFailure("临时错误");

      when(mockAdapter.fetchData(any(AdapterRequest.class))).thenReturn(failureResult);

      // 在重试前中断线程
      Thread.currentThread().interrupt();

      try {
        // When: 执行批次
        BatchResult result = executor.execute(context, batch);

        // Then: 返回失败
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("重试被中断");
      } finally {
        // 清除中断标志
        Thread.interrupted();
      }
    }

    @Test
    @DisplayName("应该使用配置的重试参数")
    void shouldUseConfiguredRetryParams() {
      // Given: 自定义重试配置
      ProvenanceConfig customConfig = createCustomRetryConfig(5, 5); // 使用 5ms 延迟以加快测试
      when(configConverter.convert(eq("pubmed"), any())).thenReturn(customConfig);

      AdapterResult failureResult = AdapterResult.retriableFailure("错误");

      when(mockAdapter.fetchData(any(AdapterRequest.class))).thenReturn(failureResult);

      // When: 执行批次
      BatchResult result = executor.execute(context, batch);

      // Then: 应该重试 6 次（5 次重试 + 1 次初始尝试）
      verify(mockAdapter, times(6)).fetchData(any(AdapterRequest.class));
      assertThat(result.success()).isFalse();
    }
  }

  // ==================== 异常处理场景 ====================

  @Nested
  @DisplayName("异常处理场景")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("适配器抛出异常应该返回失败结果")
    void shouldHandleAdapterException() {
      // Given: 适配器抛出运行时异常
      when(mockAdapter.fetchData(any(AdapterRequest.class)))
          .thenThrow(new RuntimeException("适配器内部错误"));

      // When: 执行批次
      BatchResult result = executor.execute(context, batch);

      // Then: 返回失败
      assertThat(result.success()).isFalse();
      assertThat(result.batchNo()).isEqualTo(1);
      assertThat(result.errorMessage()).contains("适配器内部错误");
    }

    @Test
    @DisplayName("配置转换失败应该返回失败结果")
    void shouldHandleConfigConversionFailure() {
      // Given: 配置转换抛出异常
      when(configConverter.convert(eq("pubmed"), any()))
          .thenThrow(new IllegalArgumentException("无效的配置快照"));

      // When: 执行批次
      BatchResult result = executor.execute(context, batch);

      // Then: 返回失败
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("无效的配置快照");
    }

    @Test
    @DisplayName("适配器注册表查找失败应该返回失败结果")
    void shouldHandleAdapterNotFound() {
      // Given: 适配器注册表找不到适配器
      when(adapterRegistry.getAdapter("pubmed"))
          .thenThrow(new IllegalArgumentException("未找到适配器: pubmed"));

      // When: 执行批次
      BatchResult result = executor.execute(context, batch);

      // Then: 返回失败
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("未找到适配器");
    }
  }

  // ==================== 部分成功场景 ====================

  @Nested
  @DisplayName("部分成功场景")
  class PartialSuccessTests {

    @Test
    @DisplayName("适配器返回部分成功应该记录警告并返回成功结果")
    void shouldHandlePartialSuccessWithWarning() {
      // Given: 适配器返回部分成功
      List<StandardLiterature> literatures = createTestLiteratures(8);
      AdapterResult adapterResult =
          AdapterResult.partialSuccess(
              literatures,
              "nextCursor",
              "10 条记录中有 2 条解析失败",
              10);

      when(mockAdapter.fetchData(any(AdapterRequest.class))).thenReturn(adapterResult);
      mockPublish(8, "s3://bucket/key");

      // When: 执行批次
      BatchResult result = executor.execute(context, batch);

      // Then: 返回成功
      assertThat(result.success()).isTrue();
      assertThat(result.fetchedCount()).isEqualTo(8);

      // 注意: 警告日志会被记录，但这里无法直接验证
    }
  }

  // ==================== 边界条件测试 ====================

  @Nested
  @DisplayName("边界条件测试")
  class EdgeCaseTests {

    @Test
    @DisplayName("null ExecutionContext 应该抛出 NullPointerException")
    void shouldThrowExceptionWhenContextIsNull() {
      // When & Then
      assertThatThrownBy(() -> executor.execute(null, batch))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("执行上下文不能为空");
    }

    @Test
    @DisplayName("null Batch 应该抛出 NullPointerException")
    void shouldThrowExceptionWhenBatchIsNull() {
      // When & Then
      assertThatThrownBy(() -> executor.execute(context, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("批次定义不能为空");
    }

    @Test
    @DisplayName("空查询字符串应该正常处理")
    void shouldHandleEmptyQuery() {
      // Given: 空查询
      Batch emptyQueryBatch = createTestBatch(2, "", Map.of(), null);
      AdapterResult adapterResult = AdapterResult.success(List.of(), null);

      when(mockAdapter.fetchData(any(AdapterRequest.class))).thenReturn(adapterResult);

      // When: 执行批次
      BatchResult result = executor.execute(context, emptyQueryBatch);

      // Then: 正常执行
      assertThat(result.success()).isTrue();

      // 验证传递了空查询
      verify(mockAdapter).fetchData(requestCaptor.capture());
      assertThat(requestCaptor.getValue().executionParams().query()).isEmpty();
    }

    @Test
    @DisplayName("null cursor token 应该正常处理")
    void shouldHandleNullCursorToken() {
      // Given: null cursor
      Batch nullCursorBatch = createTestBatch(3, "query", Map.of(), null);
      AdapterResult adapterResult = AdapterResult.success(List.of(), null);

      when(mockAdapter.fetchData(any(AdapterRequest.class))).thenReturn(adapterResult);

      // When: 执行批次
      BatchResult result = executor.execute(context, nullCursorBatch);

      // Then: 正常执行
      assertThat(result.success()).isTrue();

      // 验证传递了 null cursor
      verify(mockAdapter).fetchData(requestCaptor.capture());
      assertThat(requestCaptor.getValue().metadata().cursorToken()).isNull();
    }

    @Test
    @DisplayName("批次号为 0 应该抛出异常（Batch 验证）")
    void shouldThrowExceptionForBatchNumberZero() {
      // When & Then: 创建批次号为 0 的 Batch 时应该抛出异常
      assertThatThrownBy(() -> createTestBatch(0, "query", Map.of(), null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("batchNo must be >= 1");
    }

    @Test
    @DisplayName("空参数 Map 应该正常处理")
    void shouldHandleEmptyParamsMap() {
      // Given: 空参数
      Batch emptyParamsBatch = createTestBatch(4, "query", Map.of(), null);
      AdapterResult adapterResult = AdapterResult.success(List.of(), null);

      when(mockAdapter.fetchData(any(AdapterRequest.class))).thenReturn(adapterResult);

      // When: 执行批次
      BatchResult result = executor.execute(context, emptyParamsBatch);

      // Then: 正常执行
      assertThat(result.success()).isTrue();

      // 验证传递了空参数
      verify(mockAdapter).fetchData(requestCaptor.capture());
      assertThat(requestCaptor.getValue().executionParams().params()).isEmpty();
    }
  }

  // ==================== 辅助方法 ====================

  private Batch createTestBatch(
      int batchNo, String query, Map<String, Object> params, String cursorToken) {
    ObjectNode jsonParams = objectMapper.createObjectNode();
    if (params != null) {
      params.forEach((k, v) -> jsonParams.put(k, v.toString()));
    }
    return Batch.withToken(batchNo, query, jsonParams, cursorToken, 100);
  }

  private ProvenanceConfig createTestConfig() {
    return new ProvenanceConfig(
        "https://test.example.com",
        null, // http
        null, // pagination
        null, // windowOffset
        null, // batching
        new com.patra.starter.provenance.common.config.RetryConfig(3, 10), // 使用 10ms 延迟以加快测试
        null // rateLimit
        );
  }

  private ProvenanceConfig createCustomRetryConfig(int maxRetries, int initialDelay) {
    return new ProvenanceConfig(
        "https://test.example.com",
        null, // http
        null, // pagination
        null, // windowOffset
        null, // batching
        new com.patra.starter.provenance.common.config.RetryConfig(maxRetries, initialDelay),
        null // rateLimit
        );
  }

  private List<StandardLiterature> createTestLiteratures(int count) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(
            i ->
                StandardLiterature.builder()
                    .title("Test Literature " + i)
                    .identifiers(Map.of("sourceId", "PMID-" + (1000 + i)))
                    .build())
        .toList();
  }

  private void mockEmptyPublish() {
    // 对于空文献列表，不会调用发布器
  }

  private void mockPublish(int count, String storageKey) {
    LiteraturePublisherOrchestrator.PublishResult publishResult =
        LiteraturePublisherOrchestrator.PublishResult.builder()
            .publishedCount(count)
            .storageKey(storageKey)
            .build();
    when(literaturePublisherOrchestrator.publish(any(), any())).thenReturn(publishResult);
  }
}
