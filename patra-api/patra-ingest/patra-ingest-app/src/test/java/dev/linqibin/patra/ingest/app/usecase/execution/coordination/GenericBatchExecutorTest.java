package dev.linqibin.patra.ingest.app.usecase.execution.coordination;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.linqibin.commons.type.TypeReference;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import dev.linqibin.patra.common.model.CanonicalPublication;
import dev.linqibin.patra.common.model.DataType;
import dev.linqibin.patra.common.model.enums.PublicationIdentifierType;
import dev.linqibin.patra.ingest.domain.model.vo.batch.Batch;
import dev.linqibin.patra.ingest.domain.model.vo.batch.BatchResult;
import dev.linqibin.patra.ingest.domain.model.vo.execution.ExecutionContext;
import dev.linqibin.patra.ingest.domain.model.vo.query.QuerySession;
import dev.linqibin.patra.ingest.domain.port.ProvenanceDataPort;
import dev.linqibin.patra.ingest.domain.port.ProvenanceDataPort.DataFetchResult;
import dev.linqibin.patra.ingest.domain.port.ProvenanceDataPort.DataFetchResult.ErrorType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/// GenericBatchExecutor 单元测试
///
/// 测试覆盖:
///
/// - ✅ 正常执行：调用数据源端口成功
///   - ✅ 执行失败：数据源端口返回失败结果
///   - ✅ 结果映射：验证返回的 BatchResult
///   - ✅ 出版物发布：成功发布出版物到下游
///   - ✅ 空出版物列表：处理空的出版物列表
///   - ✅ 边界条件：null 参数、空批次号等
///   - ✅ 部分成功：处理部分成功场景
///
/// 注意：重试逻辑、配置转换等已在 ProvenanceDataPortAdapter 中处理，本测试聚焦于应用层编排逻辑。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("GenericBatchExecutor 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GenericBatchExecutorTest {

  @Mock private ProvenanceDataPort provenanceDataPort;
  @Mock private PublicationPublisher publicationPublisher;

  @InjectMocks private GenericBatchExecutor executor;

  private ExecutionContext context;
  private Batch batch;
  private ObjectMapper objectMapper = JsonMapper.builder().build();
  private QuerySession querySession;

  @BeforeEach
  void setUp() {
    // 准备基础测试数据
    dev.linqibin.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.ProvenanceInfo
        provenanceInfo =
            new dev.linqibin.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot
                .ProvenanceInfo(
                1L,
                "pubmed",
                "PubMed",
                "https://test.example.com",
                "UTC",
                "https://docs.example.com",
                true,
                "ACTIVE");

    dev.linqibin.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot configSnapshot =
        new dev.linqibin.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot(
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
            DataType.PUBLICATION, // dataType
            configSnapshot, // configSnapshot
            null, // exprHash
            null, // compiledQuery
            null, // compiledParams
            null, // normalizedExpression
            null // windowSpec
            );

    batch = new Batch(1, "cancer AND 2024", 0, 500);

    // Mock QuerySession
    querySession = QuerySession.empty(ProvenanceCode.PUBMED);
  }

  // ==================== 正常执行场景 ====================

  @Nested
  @DisplayName("正常执行场景")
  class HappyPathTests {

    @Test
    @DisplayName("应该成功执行批次并返回成功结果")
    void shouldExecuteBatchSuccessfully() {
      // Given: Mock 数据源端口返回成功结果
      List<CanonicalPublication> publications = createTestPublications(5);
      DataFetchResult<CanonicalPublication> fetchResult =
          DataFetchResult.success(publications, DataType.PUBLICATION, "nextCursor123");

      when(provenanceDataPort.fetchData(
              any(ExecutionContext.class),
              any(DataType.class),
              any(TypeReference.class),
              any(Batch.class),
              any(dev.linqibin.patra.ingest.domain.model.vo.query.QuerySession.class)))
          .thenReturn(fetchResult);

      // Mock 出版物发布
      PublicationPublisher.PublishResult publishResult =
          PublicationPublisher.PublishResult.of("s3://bucket/pubmed/run-1/batch-1.json", 5);
      when(publicationPublisher.publish(any(), any())).thenReturn(publishResult);

      // When: 执行批次
      BatchResult result = executor.execute(context, batch, querySession);

      // Then: 验证返回成功结果
      assertThat(result.success()).isTrue();
      assertThat(result.batchNo()).isEqualTo(1);
      assertThat(result.fetchedCount()).isEqualTo(5);
      assertThat(result.nextCursorToken()).isEqualTo("nextCursor123");
      assertThat(result.storageKey()).isEqualTo("s3://bucket/pubmed/run-1/batch-1.json");
      assertThat(result.errorMessage()).isNull();

      // 验证调用链
      verify(provenanceDataPort)
          .fetchData(
              eq(context),
              eq(DataType.PUBLICATION),
              any(TypeReference.class),
              eq(batch),
              eq(querySession));
      verify(publicationPublisher).publish(eq(publications), any());
    }

    @Test
    @DisplayName("应该正确处理空出版物列表")
    void shouldHandleEmptyPublicationList() {
      // Given: 数据源端口返回空出版物列表
      DataFetchResult<CanonicalPublication> fetchResult =
          DataFetchResult.success(List.of(), DataType.PUBLICATION, null);
      when(provenanceDataPort.fetchData(
              any(ExecutionContext.class),
              any(DataType.class),
              any(TypeReference.class),
              any(Batch.class),
              any(dev.linqibin.patra.ingest.domain.model.vo.query.QuerySession.class)))
          .thenReturn(fetchResult);

      // When: 执行批次
      BatchResult result = executor.execute(context, batch, querySession);

      // Then: 返回成功但 fetchedCount 为 0
      assertThat(result.success()).isTrue();
      assertThat(result.fetchedCount()).isZero();
      assertThat(result.storageKey()).isNull();

      // 验证不调用发布器（因为出版物列表为空）
      verifyNoInteractions(publicationPublisher);
    }

    @Test
    @DisplayName("应该正确处理 null 出版物列表")
    void shouldHandleNullPublicationList() {
      // Given: 数据源端口返回 null 出版物列表
      DataFetchResult<CanonicalPublication> fetchResult =
          DataFetchResult.success(null, DataType.PUBLICATION, null);
      when(provenanceDataPort.fetchData(
              any(ExecutionContext.class),
              any(DataType.class),
              any(TypeReference.class),
              any(Batch.class),
              any(dev.linqibin.patra.ingest.domain.model.vo.query.QuerySession.class)))
          .thenReturn(fetchResult);

      // When: 执行批次
      BatchResult result = executor.execute(context, batch, querySession);

      // Then: 返回成功
      assertThat(result.success()).isTrue();
      assertThat(result.fetchedCount()).isZero();

      // 验证不调用发布器
      verifyNoInteractions(publicationPublisher);
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
      DataFetchResult<CanonicalPublication> failureResult =
          DataFetchResult.failure(DataType.PUBLICATION, "网络超时", ErrorType.RETRIABLE);

      when(provenanceDataPort.fetchData(
              any(ExecutionContext.class),
              any(DataType.class),
              any(TypeReference.class),
              any(Batch.class),
              any(dev.linqibin.patra.ingest.domain.model.vo.query.QuerySession.class)))
          .thenReturn(failureResult);

      // When: 执行批次
      BatchResult result = executor.execute(context, batch, querySession);

      // Then: 返回失败
      assertThat(result.success()).isFalse();
      assertThat(result.batchNo()).isEqualTo(1);
      assertThat(result.errorMessage()).contains("RETRIABLE").contains("网络超时");

      // 验证没有发布出版物
      verifyNoInteractions(publicationPublisher);
    }

    @Test
    @DisplayName("非重试错误应该返回失败结果")
    void shouldHandleNonRetriableError() {
      // Given: 非重试错误
      DataFetchResult<CanonicalPublication> failureResult =
          DataFetchResult.failure(DataType.PUBLICATION, "API 密钥无效", ErrorType.NON_RETRIABLE);

      when(provenanceDataPort.fetchData(
              any(ExecutionContext.class),
              any(DataType.class),
              any(TypeReference.class),
              any(Batch.class),
              any(dev.linqibin.patra.ingest.domain.model.vo.query.QuerySession.class)))
          .thenReturn(failureResult);

      // When: 执行批次
      BatchResult result = executor.execute(context, batch, querySession);

      // Then: 返回失败
      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("API 密钥无效");

      // 验证没有发布出版物
      verifyNoInteractions(publicationPublisher);
    }
  }

  // ==================== 异常处理场景 ====================

  @Nested
  @DisplayName("异常处理场景")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("数据源端口抛出异常应该返回失败结果")
    void shouldHandleProvenanceDataPortException() {
      // Given: 数据源端口抛出运行时异常
      when(provenanceDataPort.fetchData(
              any(ExecutionContext.class),
              any(DataType.class),
              any(TypeReference.class),
              any(Batch.class),
              any(dev.linqibin.patra.ingest.domain.model.vo.query.QuerySession.class)))
          .thenThrow(new RuntimeException("数据源内部错误"));

      // When: 执行批次
      BatchResult result = executor.execute(context, batch, querySession);

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
      List<CanonicalPublication> publications = createTestPublications(8);
      DataFetchResult<CanonicalPublication> fetchResult =
          DataFetchResult.partialSuccess(
              publications, DataType.PUBLICATION, "nextCursor", "10 条记录中有 2 条解析失败");

      when(provenanceDataPort.fetchData(
              any(ExecutionContext.class),
              any(DataType.class),
              any(TypeReference.class),
              any(Batch.class),
              any(dev.linqibin.patra.ingest.domain.model.vo.query.QuerySession.class)))
          .thenReturn(fetchResult);
      mockPublish(8, "s3://bucket/key");

      // When: 执行批次
      BatchResult result = executor.execute(context, batch, querySession);

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
      assertThatThrownBy(() -> executor.execute(null, batch, querySession))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("执行上下文不能为空");
    }

    @Test
    @DisplayName("null Batch 应该抛出 NullPointerException")
    void shouldThrowExceptionWhenBatchIsNull() {
      // When & Then
      assertThatThrownBy(() -> executor.execute(context, null, querySession))
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
    // Note: cursorToken 已移除，现在使用 offset/limit 模式
    int offset = (batchNo - 1) * 500;
    int limit = 500;
    return new Batch(batchNo, query, offset, limit);
  }

  private List<CanonicalPublication> createTestPublications(int count) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(
            i ->
                CanonicalPublication.builder()
                    .title("Test Publication " + i)
                    .identifiers(
                        List.of(
                            CanonicalPublication.Identifier.builder()
                                .type(PublicationIdentifierType.PMID)
                                .value("PMID-" + (1000 + i))
                                .build()))
                    .build())
        .toList();
  }

  private void mockPublish(int count, String storageKey) {
    PublicationPublisher.PublishResult publishResult =
        PublicationPublisher.PublishResult.of(storageKey, count);
    when(publicationPublisher.publish(any(), any())).thenReturn(publishResult);
  }
}
