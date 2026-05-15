package dev.linqibin.patra.ingest.app.usecase.execution.strategy.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import dev.linqibin.patra.ingest.domain.exception.BatchSchedulingException;
import dev.linqibin.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import dev.linqibin.patra.ingest.domain.model.vo.batch.Batch;
import dev.linqibin.patra.ingest.domain.model.vo.execution.ExecutionContext;
import dev.linqibin.patra.ingest.domain.model.vo.plan.WindowSpec;
import dev.linqibin.patra.ingest.domain.model.vo.query.QuerySession;
import dev.linqibin.patra.ingest.domain.port.ProvenanceDataPort;
import dev.linqibin.patra.ingest.domain.strategy.BatchGenerationStrategy;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import dev.linqibin.patra.common.model.DataType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * BatchScheduleBuilder 单元测试
 *
 * <p>测试重点：
 *
 * <ul>
 *   <li>策略自动注册（通过 Spring 注入）
 *   <li>策略选择逻辑（根据 QuerySession 类型）
 *   <li>PubMed 数据源的调度构建流程
 *   <li>未知数据类型的异常处理
 *   <li>ProvenanceDataPort.prepareQuerySession() 的调用
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BatchScheduleBuilder 单元测试")
class BatchScheduleBuilderTest {

  @Mock private ProvenanceDataPort provenanceDataPort;

  @Mock private BatchGenerationStrategy mockStrategy1;

  @Mock private BatchGenerationStrategy mockStrategy2;

  private BatchScheduleBuilder builder;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = JsonMapper.builder().build();

    // 配置 mock 策略支持的 Provenance 代码
    when(mockStrategy1.getSupportedProvenanceCode()).thenReturn(ProvenanceCode.PUBMED);
    when(mockStrategy2.getSupportedProvenanceCode()).thenReturn(ProvenanceCode.EPMC);

    // 创建 planner 并自动注册策略
    List<BatchGenerationStrategy> strategies = List.of(mockStrategy1, mockStrategy2);
    builder = new BatchScheduleBuilder(provenanceDataPort, strategies);
  }

  @Test
  @DisplayName("构造函数应该自动注册所有策略")
  void should_register_all_strategies_in_constructor() {
    // 验证：通过日志或后续调用验证策略已注册
    // 这里通过实际使用策略来验证
    QuerySession pubmedSession = createPubmedSession(1000, false);
    ExecutionContext ctx = createContext("pubmed");

    when(provenanceDataPort.prepareQuerySession(any(), eq(DataType.PUBLICATION)))
        .thenReturn(pubmedSession);
    when(mockStrategy1.generateBatches(any(), any())).thenReturn(List.of());

    // when
    builder.build(ctx);

    // then
    verify(mockStrategy1).generateBatches(pubmedSession, ctx);
  }

  @Test
  @DisplayName("应该成功规划 PubMed 数据源")
  void should_plan_pubmed_data_source_successfully() {
    // given
    int totalCount = 1000;
    QuerySession pubmedSession = createPubmedSession(totalCount, false);
    ExecutionContext ctx = createContext("pubmed");

    List<Batch> mockBatches = List.of(createBatch(1), createBatch(2), createBatch(3));

    when(provenanceDataPort.prepareQuerySession(ctx, DataType.PUBLICATION))
        .thenReturn(pubmedSession);
    when(mockStrategy1.generateBatches(pubmedSession, ctx)).thenReturn(mockBatches);

    // when
    dev.linqibin.patra.ingest.domain.model.vo.batch.BatchSchedule result = builder.build(ctx);

    // then
    assertThat(result.batches()).hasSize(3);
    assertThat(result.totalBatches()).isEqualTo(3);
    assertThat(result.hasBatches()).isTrue();

    verify(provenanceDataPort).prepareQuerySession(ctx, DataType.PUBLICATION);
    verify(mockStrategy1).generateBatches(pubmedSession, ctx);
  }

  @Test
  @DisplayName("应该成功规划 EPMC 数据源")
  void should_plan_epmc_data_source_successfully() {
    // given
    int totalCount = 2000;
    QuerySession epmcSession = createEpmcSession(totalCount, "cursor-123");
    ExecutionContext ctx = createContext("epmc");

    List<Batch> mockBatches = List.of(createBatch(1), createBatch(2));

    when(provenanceDataPort.prepareQuerySession(ctx, DataType.PUBLICATION)).thenReturn(epmcSession);
    when(mockStrategy2.generateBatches(epmcSession, ctx)).thenReturn(mockBatches);

    // when
    dev.linqibin.patra.ingest.domain.model.vo.batch.BatchSchedule result = builder.build(ctx);

    // then
    assertThat(result.batches()).hasSize(2);
    assertThat(result.totalBatches()).isEqualTo(2);

    verify(provenanceDataPort).prepareQuerySession(ctx, DataType.PUBLICATION);
    verify(mockStrategy2).generateBatches(epmcSession, ctx);
  }

  @Test
  @DisplayName("当 totalCount 为 0 时应该返回空计划")
  void should_return_empty_plan_when_total_count_is_zero() {
    // given
    QuerySession emptySession = createPubmedSession(0, false);
    ExecutionContext ctx = createContext("pubmed");

    when(provenanceDataPort.prepareQuerySession(ctx, DataType.PUBLICATION))
        .thenReturn(emptySession);

    // when
    dev.linqibin.patra.ingest.domain.model.vo.batch.BatchSchedule result = builder.build(ctx);

    // then
    assertThat(result.totalBatches()).isZero();
    assertThat(result.batches()).isEmpty();
    assertThat(result.hasBatches()).isFalse();

    verify(provenanceDataPort).prepareQuerySession(ctx, DataType.PUBLICATION);
    verify(mockStrategy1, never()).generateBatches(any(), any());
  }

  @Test
  @DisplayName("当未找到对应策略时应该抛出异常")
  void should_throw_exception_when_strategy_not_found() {
    // given
    // 创建一个未注册策略的 QuerySession（使用匿名子类）
    // 注意: ExecutionContext 使用 PUBMED 是合法的,但 QuerySession 返回的 dataSourceCode 是未注册的
    QuerySession unknownSession = createUnknownSession(100);
    ExecutionContext ctx = createContext("pubmed"); // 使用合法的 ProvenanceCode

    when(provenanceDataPort.prepareQuerySession(ctx, DataType.PUBLICATION))
        .thenReturn(unknownSession);

    // when & then
    assertThatThrownBy(() -> builder.build(ctx))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("未找到对应的批次生成策略")
        .hasMessageContaining("CROSSREF");

    verify(provenanceDataPort).prepareQuerySession(ctx, DataType.PUBLICATION);
  }

  @Test
  @DisplayName("当 ProvenanceDataPort 抛出异常时应该包装为 BatchSchedulingException")
  void should_wrap_data_source_exception_as_batch_planning_exception() {
    // given
    ExecutionContext ctx = createContext("pubmed");
    RuntimeException sourceException = new RuntimeException("数据源错误");

    when(provenanceDataPort.prepareQuerySession(ctx, DataType.PUBLICATION))
        .thenThrow(sourceException);

    // when & then
    assertThatThrownBy(() -> builder.build(ctx))
        .isInstanceOf(BatchSchedulingException.class)
        .hasMessageContaining("准备查询会话失败")
        .hasCause(sourceException);

    verify(provenanceDataPort).prepareQuerySession(ctx, DataType.PUBLICATION);
  }

  @Test
  @DisplayName("当策略生成批次失败时应该包装为 BatchSchedulingException")
  void should_wrap_strategy_exception_as_batch_planning_exception() {
    // given
    QuerySession pubmedSession = createPubmedSession(1000, false);
    ExecutionContext ctx = createContext("pubmed");
    RuntimeException strategyException = new RuntimeException("批次生成错误");

    when(provenanceDataPort.prepareQuerySession(ctx, DataType.PUBLICATION))
        .thenReturn(pubmedSession);
    when(mockStrategy1.generateBatches(pubmedSession, ctx)).thenThrow(strategyException);

    // when & then
    assertThatThrownBy(() -> builder.build(ctx))
        .isInstanceOf(BatchSchedulingException.class)
        .hasMessageContaining("批次生成失败")
        .hasCause(strategyException);

    verify(provenanceDataPort).prepareQuerySession(ctx, DataType.PUBLICATION);
    verify(mockStrategy1).generateBatches(pubmedSession, ctx);
  }

  @Test
  @DisplayName("应该正确处理策略返回空列表的情况")
  void should_handle_empty_batch_list_from_strategy() {
    // given
    QuerySession pubmedSession = createPubmedSession(1000, false);
    ExecutionContext ctx = createContext("pubmed");

    when(provenanceDataPort.prepareQuerySession(ctx, DataType.PUBLICATION))
        .thenReturn(pubmedSession);
    when(mockStrategy1.generateBatches(pubmedSession, ctx)).thenReturn(List.of());

    // when
    dev.linqibin.patra.ingest.domain.model.vo.batch.BatchSchedule result = builder.build(ctx);

    // then
    assertThat(result.batches()).isEmpty();
    assertThat(result.totalBatches()).isZero();
    assertThat(result.hasBatches()).isFalse();
  }

  @Test
  @DisplayName("构造函数应该拒绝返回 null supportedProvenanceCode 的策略")
  void should_warn_about_strategy_with_null_supported_provenance_code() {
    // given
    BatchGenerationStrategy invalidStrategy = mock(BatchGenerationStrategy.class);
    when(invalidStrategy.getSupportedProvenanceCode()).thenReturn(null);

    List<BatchGenerationStrategy> strategies = List.of(mockStrategy1, invalidStrategy);

    // when
    BatchScheduleBuilder builder = new BatchScheduleBuilder(provenanceDataPort, strategies);

    // then
    // 验证：planner 应该跳过 null 策略，只注册有效策略
    QuerySession pubmedSession = createPubmedSession(1000, false);
    ExecutionContext ctx = createContext("pubmed");

    when(provenanceDataPort.prepareQuerySession(any(), eq(DataType.PUBLICATION)))
        .thenReturn(pubmedSession);
    when(mockStrategy1.generateBatches(any(), any())).thenReturn(List.of());

    builder.build(ctx);

    verify(mockStrategy1).generateBatches(any(), any());
    verify(invalidStrategy, never()).generateBatches(any(), any());
  }

  @Test
  @DisplayName("构造函数应该拒绝重复的策略类型")
  void should_throw_exception_when_duplicate_strategy_types() {
    // given
    BatchGenerationStrategy duplicateStrategy = mock(BatchGenerationStrategy.class);
    when(duplicateStrategy.getSupportedProvenanceCode()).thenReturn(ProvenanceCode.PUBMED);

    List<BatchGenerationStrategy> strategies = List.of(mockStrategy1, duplicateStrategy);

    // when & then
    assertThatThrownBy(() -> new BatchScheduleBuilder(provenanceDataPort, strategies))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("重复的批次生成策略");
  }

  // === 辅助方法 ===

  /** 创建测试用的 ExecutionContext */
  private ExecutionContext createContext(String provenanceCode) {
    ProvenanceConfigSnapshot configSnapshot = mock(ProvenanceConfigSnapshot.class);
    JsonNode params = objectMapper.createObjectNode();

    // 将 String 转换为 ProvenanceCode
    ProvenanceCode code = ProvenanceCode.parse(provenanceCode);

    return new ExecutionContext(
        1L, // taskId
        1L, // runId
        1L, // planId
        1L, // sliceId
        1L, // scheduleInstanceId
        code, // provenanceCode
        "search", // operationCode
        DataType.PUBLICATION, // dataType
        configSnapshot, // configSnapshot
        "expr-hash", // exprHash
        "test-query", // compiledQuery
        params, // compiledParams
        "normalized-expr", // normalizedExpression
        new WindowSpec.Single() // windowSpec
        );
  }

  /** 创建测试用的 Batch */
  private Batch createBatch(int batchNo) {
    // 使用新的 Batch 构造器：batchNo, query, offset, limit
    int offset = (batchNo - 1) * 500; // 假设每批 500 条
    int limit = 500;
    return new Batch(batchNo, "test-query", offset, limit);
  }

  /** 创建测试用的 QuerySession (PubMed) - 查询会话 */
  private QuerySession createPubmedSession(int totalRecords, boolean hasToken) {
    return new QuerySession() {
      @Override
      public int totalRecords() {
        return totalRecords;
      }

      @Override
      public ProvenanceCode provenanceCode() {
        return ProvenanceCode.PUBMED;
      }

      @Override
      public boolean hasStateToken() {
        return hasToken;
      }

      @Override
      public Optional<Map<String, String>> stateToken() {
        return hasToken
            ? Optional.of(Map.of("webEnv", "test-env", "queryKey", "1"))
            : Optional.empty();
      }
    };
  }

  /** 创建测试用的 QuerySession (EPMC) - 查询会话 */
  private QuerySession createEpmcSession(int totalRecords, String cursorMark) {
    return new QuerySession() {
      @Override
      public int totalRecords() {
        return totalRecords;
      }

      @Override
      public ProvenanceCode provenanceCode() {
        return ProvenanceCode.EPMC;
      }

      @Override
      public boolean hasStateToken() {
        return cursorMark != null;
      }

      @Override
      public Optional<Map<String, String>> stateToken() {
        return cursorMark != null
            ? Optional.of(Map.of("cursorMark", cursorMark))
            : Optional.empty();
      }
    };
  }

  /** 创建测试用的 QuerySession (未知数据源) - 查询会话 */
  private QuerySession createUnknownSession(int totalRecords) {
    return new QuerySession() {
      @Override
      public int totalRecords() {
        return totalRecords;
      }

      @Override
      public ProvenanceCode provenanceCode() {
        // 使用未注册策略的 ProvenanceCode（测试中只注册了 PUBMED 和 EPMC）
        return ProvenanceCode.CROSSREF;
      }

      @Override
      public boolean hasStateToken() {
        return false;
      }

      @Override
      public Optional<Map<String, String>> stateToken() {
        return Optional.empty();
      }
    };
  }
}
