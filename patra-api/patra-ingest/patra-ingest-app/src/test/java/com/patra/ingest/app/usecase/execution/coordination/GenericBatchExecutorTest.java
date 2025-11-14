package com.patra.ingest.app.usecase.execution.coordination;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.patra.common.enums.ProvenanceCode;
import com.patra.common.model.CanonicalLiterature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.model.DataType;
import com.patra.common.type.TypeReference;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.batch.BatchResult;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.port.DataSourcePort;
import com.patra.ingest.domain.port.DataSourcePort.DataFetchResult;
import com.patra.ingest.domain.port.DataSourcePort.DataFetchResult.ErrorType;
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
 *   <li>✅ 正常执行：调用数据源端口成功
 *   <li>✅ 执行失败：数据源端口返回失败结果
 *   <li>✅ 结果映射：验证返回的 BatchResult
 *   <li>✅ 文献发布：成功发布文献到下游
 *   <li>✅ 空文献列表：处理空的文献列表
 *   <li>✅ 边界条件：null 参数、空批次号等
 *   <li>✅ 部分成功：处理部分成功场景
 * </ul>
 *
 * <p>注意：重试逻辑、配置转换等已在 DataSourcePortAdapter 中处理，本测试聚焦于应用层编排逻辑。
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("GenericBatchExecutor 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GenericBatchExecutorTest {

  @Mock private DataSourcePort dataSourcePort;
  @Mock private LiteraturePublisherOrchestrator literaturePublisherOrchestrator;

  @InjectMocks private GenericBatchExecutor executor;

  private ExecutionContext context;
  private Batch batch;
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
            ProvenanceCode.PUBMED, // provenanceCode
            "harvest", // operationCode
            DataType.LITERATURE, // dataType
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
  }

  // ==================== 正常执行场景 ====================

  @Nested
  @DisplayName("正常执行场景")
  class HappyPathTests {

    @Test
    @DisplayName("应该成功执行批次并返回成功结果")
    void shouldExecuteBatchSuccessfully() {
      // Given: Mock 数据源端口返回成功结果
      List<CanonicalLiterature> literatures = createTestLiteratures(5);
      DataFetchResult<CanonicalLiterature> fetchResult =
          DataFetchResult.success(literatures, DataType.LITERATURE, "nextCursor123");

      when(dataSourcePort.fetchData(
          any(ExecutionContext.class),
          any(DataType.class),
          any(TypeReference.class),
          any(Batch.class)))
          .thenReturn(fetchResult);

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
      verify(dataSourcePort).fetchData(
          eq(context),
          eq(DataType.LITERATURE),
          any(TypeReference.class),
          eq(batch));
      verify(literaturePublisherOrchestrator).publish(eq(literatures), any());
    }

    @Test
    @DisplayName("应该正确处理空文献列表")
    void shouldHandleEmptyLiteratureList() {
      // Given: 数据源端口返回空文献列表
      DataFetchResult<CanonicalLiterature> fetchResult =
          DataFetchResult.success(List.of(), DataType.LITERATURE, null);
      when(dataSourcePort.fetchData(
          any(ExecutionContext.class),
          any(DataType.class),
          any(TypeReference.class),
          any(Batch.class)))
          .thenReturn(fetchResult);

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
      // Given: 数据源端口返回 null 文献列表
      DataFetchResult<CanonicalLiterature> fetchResult =
          DataFetchResult.success(null, DataType.LITERATURE, null);
      when(dataSourcePort.fetchData(
          any(ExecutionContext.class),
          any(DataType.class),
          any(TypeReference.class),
          any(Batch.class)))
          .thenReturn(fetchResult);

      // When: 执行批次
      BatchResult result = executor.execute(context, batch);

      // Then: 返回成功
      assertThat(result.success()).isTrue();
      assertThat(result.fetchedCount()).isZero();

      // 验证不调用发布器
      verifyNoInteractions(literaturePublisherOrchestrator);
    }
  }

  // ==================== 失败处理场景 ====================

  @Nested
  @DisplayName("失败处理场景")
  class FailureHandlingTests {

    @Test
    @DisplayName("可重试错误应该返回失败结果")
    void shouldHandleRetriableError() {
      // Given: 数据源端口返回可重试错误（重试已在 infra 层处理）
      DataFetchResult<CanonicalLiterature> failureResult =
          DataFetchResult.failure(DataType.LITERATURE, "网络超时", ErrorType.RETRIABLE);

      when(dataSourcePort.fetchData(
          any(ExecutionContext.class),
          any(DataType.class),
          any(TypeReference.class),
          any(Batch.class)))
          .thenReturn(failureResult);

      // When: 执行批次
      BatchResult result = executor.execute(context, batch);

      // Then: 返回失败
      assertThat(result.success()).isFalse();
      assertThat(result.batchNo()).isEqualTo(1);
      assertThat(result.errorMessage()).contains("RETRIABLE").contains("网络超时");

      // 验证没有发布文献
      verifyNoInteractions(literaturePublisherOrchestrator);
    }

    @Test
    @DisplayName("非重试错误应该返回失败结果")
    void shouldHandleNonRetriableError() {
      // Given: 非重试错误
      DataFetchResult<CanonicalLiterature> failureResult =
          DataFetchResult.failure(DataType.LITERATURE, "API 密钥无效", ErrorType.NON_RETRIABLE);

      when(dataSourcePort.fetchData(
          any(ExecutionContext.class),
          any(DataType.class),
          any(TypeReference.class),
          any(Batch.class)))
          .thenReturn(failureResult);

      // When: 执行批次
      BatchResult result = executor.execute(context, batch);

      // Then: 返回失败
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("API 密钥无效");

      // 验证没有发布文献
      verifyNoInteractions(literaturePublisherOrchestrator);
    }
  }

  // ==================== 异常处理场景 ====================

  @Nested
  @DisplayName("异常处理场景")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("数据源端口抛出异常应该返回失败结果")
    void shouldHandleDataSourcePortException() {
      // Given: 数据源端口抛出运行时异常
      when(dataSourcePort.fetchData(
          any(ExecutionContext.class),
          any(DataType.class),
          any(TypeReference.class),
          any(Batch.class)))
          .thenThrow(new RuntimeException("数据源内部错误"));

      // When: 执行批次
      BatchResult result = executor.execute(context, batch);

      // Then: 返回失败
      assertThat(result.success()).isFalse();
      assertThat(result.batchNo()).isEqualTo(1);
      assertThat(result.errorMessage()).contains("数据源内部错误");
    }
  }

  // ==================== 部分成功场景 ====================

  @Nested
  @DisplayName("部分成功场景")
  class PartialSuccessTests {

    @Test
    @DisplayName("数据源端口返回部分成功应该记录警告并返回成功结果")
    void shouldHandlePartialSuccessWithWarning() {
      // Given: 数据源端口返回部分成功
      List<CanonicalLiterature> literatures = createTestLiteratures(8);
      DataFetchResult<CanonicalLiterature> fetchResult =
          DataFetchResult.partialSuccess(
              literatures, DataType.LITERATURE, "nextCursor", "10 条记录中有 2 条解析失败");

      when(dataSourcePort.fetchData(
          any(ExecutionContext.class),
          any(DataType.class),
          any(TypeReference.class),
          any(Batch.class)))
          .thenReturn(fetchResult);
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
    @DisplayName("批次号为 0 应该抛出异常（Batch 验证）")
    void shouldThrowExceptionForBatchNumberZero() {
      // When & Then: 创建批次号为 0 的 Batch 时应该抛出异常
      assertThatThrownBy(() -> createTestBatch(0, "query", Map.of(), null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("batchNo must be >= 1");
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

  private List<CanonicalLiterature> createTestLiteratures(int count) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(
            i ->
                CanonicalLiterature.builder()
                    .title("Test Literature " + i)
                    .identifiers(
                        List.of(
                            CanonicalLiterature.Identifier.builder()
                                .type("pmid")
                                .value("PMID-" + (1000 + i))
                                .build()))
                    .build())
        .toList();
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
