package com.patra.ingest.app.usecase.execution.strategy.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.model.DataType;
import com.patra.ingest.domain.exception.BatchSchedulingException;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
// Note: 使用完整类名来区分两个不同的 BatchSchedule:
// - com.patra.ingest.domain.model.vo.batch.BatchSchedule: 规划结果（包含批次列表）
// - com.patra.ingest.domain.model.vo.fetch.FetchMetadata: 计划元数据（用于生成批次）
import com.patra.ingest.domain.model.vo.plan.WindowSpec;
import com.patra.ingest.domain.port.DataSourcePort;
import com.patra.ingest.domain.strategy.BatchGenerationStrategy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * UnifiedBatchPlanner 单元测试
 *
 * <p>测试重点：
 *
 * <ul>
 *   <li>策略自动注册（通过 Spring 注入）
 *   <li>策略选择逻辑（根据 PlanMetadata 类型）
 *   <li>PubMed 数据源的规划流程
 *   <li>未知数据类型的异常处理
 *   <li>DataSourcePort.prepareFetchMetadata() 的调用
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UnifiedBatchPlanner 单元测试")
class UnifiedBatchScheduleBuilderTest {

  @Mock private DataSourcePort dataSourcePort;

  @Mock private BatchGenerationStrategy mockStrategy1;

  @Mock private BatchGenerationStrategy mockStrategy2;

  private UnifiedBatchScheduleBuilder builder;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();

    // 配置 mock 策略支持的数据源代码
    when(mockStrategy1.getSupportedDataSourceCode()).thenReturn(ProvenanceCode.PUBMED.getCode());
    when(mockStrategy2.getSupportedDataSourceCode()).thenReturn(ProvenanceCode.EPMC.getCode());

    // 创建 planner 并自动注册策略
    List<BatchGenerationStrategy> strategies = List.of(mockStrategy1, mockStrategy2);
    builder = new UnifiedBatchScheduleBuilder(dataSourcePort, strategies);
  }

  @Test
  @DisplayName("构造函数应该自动注册所有策略")
  void should_register_all_strategies_in_constructor() {
    // 验证：通过日志或后续调用验证策略已注册
    // 这里通过实际使用策略来验证
    com.patra.ingest.domain.model.vo.fetch.FetchMetadata pubmedPlan = createPubmedPlan(1000, false);
    ExecutionContext ctx = createContext("pubmed");

    when(dataSourcePort.prepareFetchMetadata(any(), eq(DataType.LITERATURE))).thenReturn(pubmedPlan);
    when(mockStrategy1.generateBatches(any(), any())).thenReturn(List.of());

    // when
    builder.build(ctx);

    // then
    verify(mockStrategy1).generateBatches(pubmedPlan, ctx);
  }

  @Test
  @DisplayName("getProvenanceCode 应该返回 null（支持所有数据源）")
  void should_return_null_for_provenance_code() {
    // when
    ProvenanceCode provenanceCode = builder.getProvenanceCode();

    // then
    assertThat(provenanceCode).isNull();
  }

  @Test
  @DisplayName("应该成功规划 PubMed 数据源")
  void should_plan_pubmed_data_source_successfully() {
    // given
    int totalCount = 1000;
    com.patra.ingest.domain.model.vo.fetch.FetchMetadata pubmedPlan =
        createPubmedPlan(totalCount, false);
    ExecutionContext ctx = createContext("pubmed");

    List<Batch> mockBatches = List.of(createBatch(1), createBatch(2), createBatch(3));

    when(dataSourcePort.prepareFetchMetadata(ctx, DataType.LITERATURE)).thenReturn(pubmedPlan);
    when(mockStrategy1.generateBatches(pubmedPlan, ctx)).thenReturn(mockBatches);

    // when
    com.patra.ingest.domain.model.vo.batch.BatchSchedule result = builder.build(ctx);

    // then
    assertThat(result.batches()).hasSize(3);
    assertThat(result.totalBatches()).isEqualTo(3);
    assertThat(result.hasBatches()).isTrue();

    verify(dataSourcePort).prepareFetchMetadata(ctx, DataType.LITERATURE);
    verify(mockStrategy1).generateBatches(pubmedPlan, ctx);
  }

  @Test
  @DisplayName("应该成功规划 EPMC 数据源")
  void should_plan_epmc_data_source_successfully() {
    // given
    int totalCount = 2000;
    com.patra.ingest.domain.model.vo.fetch.FetchMetadata epmcPlan =
        createEpmcPlan(totalCount, "cursor-123");
    ExecutionContext ctx = createContext("epmc");

    List<Batch> mockBatches = List.of(createBatch(1), createBatch(2));

    when(dataSourcePort.prepareFetchMetadata(ctx, DataType.LITERATURE)).thenReturn(epmcPlan);
    when(mockStrategy2.generateBatches(epmcPlan, ctx)).thenReturn(mockBatches);

    // when
    com.patra.ingest.domain.model.vo.batch.BatchSchedule result = builder.build(ctx);

    // then
    assertThat(result.batches()).hasSize(2);
    assertThat(result.totalBatches()).isEqualTo(2);

    verify(dataSourcePort).prepareFetchMetadata(ctx, DataType.LITERATURE);
    verify(mockStrategy2).generateBatches(epmcPlan, ctx);
  }

  @Test
  @DisplayName("当 totalCount 为 0 时应该返回空计划")
  void should_return_empty_plan_when_total_count_is_zero() {
    // given
    com.patra.ingest.domain.model.vo.fetch.FetchMetadata emptyPlan = createPubmedPlan(0, false);
    ExecutionContext ctx = createContext("pubmed");

    when(dataSourcePort.prepareFetchMetadata(ctx, DataType.LITERATURE)).thenReturn(emptyPlan);

    // when
    com.patra.ingest.domain.model.vo.batch.BatchSchedule result = builder.build(ctx);

    // then
    assertThat(result.totalBatches()).isZero();
    assertThat(result.batches()).isEmpty();
    assertThat(result.hasBatches()).isFalse();

    verify(dataSourcePort).prepareFetchMetadata(ctx, DataType.LITERATURE);
    verify(mockStrategy1, never()).generateBatches(any(), any());
  }

  @Test
  @DisplayName("当未找到对应策略时应该抛出异常")
  void should_throw_exception_when_strategy_not_found() {
    // given
    // 创建一个未注册策略的 BatchSchedule（使用匿名子类）
    // 注意: ExecutionContext 使用 PUBMED 是合法的,但 BatchSchedule 返回的 dataSourceCode 是未注册的
    com.patra.ingest.domain.model.vo.fetch.FetchMetadata unknownPlan = createUnknownPlan(100);
    ExecutionContext ctx = createContext("pubmed"); // 使用合法的 ProvenanceCode

    when(dataSourcePort.prepareFetchMetadata(ctx, DataType.LITERATURE)).thenReturn(unknownPlan);

    // when & then
    assertThatThrownBy(() -> builder.build(ctx))
        .isInstanceOf(BatchSchedulingException.class)
        .hasMessageContaining("批次调度")
        .hasCauseInstanceOf(IllegalStateException.class);

    verify(dataSourcePort).prepareFetchMetadata(ctx, DataType.LITERATURE);
  }

  @Test
  @DisplayName("当 DataSourcePort 抛出异常时应该包装为 BatchSchedulingException")
  void should_wrap_data_source_exception_as_batch_planning_exception() {
    // given
    ExecutionContext ctx = createContext("pubmed");
    RuntimeException sourceException = new RuntimeException("数据源错误");

    when(dataSourcePort.prepareFetchMetadata(ctx, DataType.LITERATURE)).thenThrow(sourceException);

    // when & then
    assertThatThrownBy(() -> builder.build(ctx))
        .isInstanceOf(BatchSchedulingException.class)
        .hasMessageContaining("批次调度")
        .hasCause(sourceException);

    verify(dataSourcePort).prepareFetchMetadata(ctx, DataType.LITERATURE);
  }

  @Test
  @DisplayName("当策略生成批次失败时应该包装为 BatchSchedulingException")
  void should_wrap_strategy_exception_as_batch_planning_exception() {
    // given
    com.patra.ingest.domain.model.vo.fetch.FetchMetadata pubmedPlan = createPubmedPlan(1000, false);
    ExecutionContext ctx = createContext("pubmed");
    RuntimeException strategyException = new RuntimeException("批次生成错误");

    when(dataSourcePort.prepareFetchMetadata(ctx, DataType.LITERATURE)).thenReturn(pubmedPlan);
    when(mockStrategy1.generateBatches(pubmedPlan, ctx)).thenThrow(strategyException);

    // when & then
    assertThatThrownBy(() -> builder.build(ctx))
        .isInstanceOf(BatchSchedulingException.class)
        .hasMessageContaining("批次调度")
        .hasCause(strategyException);

    verify(dataSourcePort).prepareFetchMetadata(ctx, DataType.LITERATURE);
    verify(mockStrategy1).generateBatches(pubmedPlan, ctx);
  }

  @Test
  @DisplayName("应该正确处理策略返回空列表的情况")
  void should_handle_empty_batch_list_from_strategy() {
    // given
    com.patra.ingest.domain.model.vo.fetch.FetchMetadata pubmedPlan = createPubmedPlan(1000, false);
    ExecutionContext ctx = createContext("pubmed");

    when(dataSourcePort.prepareFetchMetadata(ctx, DataType.LITERATURE)).thenReturn(pubmedPlan);
    when(mockStrategy1.generateBatches(pubmedPlan, ctx)).thenReturn(List.of());

    // when
    com.patra.ingest.domain.model.vo.batch.BatchSchedule result = builder.build(ctx);

    // then
    assertThat(result.batches()).isEmpty();
    assertThat(result.totalBatches()).isZero();
    assertThat(result.hasBatches()).isFalse();
  }

  @Test
  @DisplayName("构造函数应该拒绝返回 null supportedDataSourceCode 的策略")
  void should_warn_about_strategy_with_null_supported_data_source_code() {
    // given
    BatchGenerationStrategy invalidStrategy = mock(BatchGenerationStrategy.class);
    when(invalidStrategy.getSupportedDataSourceCode()).thenReturn(null);

    List<BatchGenerationStrategy> strategies = List.of(mockStrategy1, invalidStrategy);

    // when
    UnifiedBatchScheduleBuilder builder =
        new UnifiedBatchScheduleBuilder(dataSourcePort, strategies);

    // then
    // 验证：planner 应该跳过 null 策略，只注册有效策略
    com.patra.ingest.domain.model.vo.fetch.FetchMetadata pubmedPlan = createPubmedPlan(1000, false);
    ExecutionContext ctx = createContext("pubmed");

    when(dataSourcePort.prepareFetchMetadata(any(), eq(DataType.LITERATURE))).thenReturn(pubmedPlan);
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
    when(duplicateStrategy.getSupportedDataSourceCode())
        .thenReturn(ProvenanceCode.PUBMED.getCode());

    List<BatchGenerationStrategy> strategies = List.of(mockStrategy1, duplicateStrategy);

    // when & then
    assertThatThrownBy(() -> new UnifiedBatchScheduleBuilder(dataSourcePort, strategies))
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
        DataType.LITERATURE, // dataType
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
    return Batch.first("test-query", objectMapper.createObjectNode());
  }

  /** 创建测试用的 BatchSchedule (PubMed) - 计划元数据 */
  private com.patra.ingest.domain.model.vo.fetch.FetchMetadata createPubmedPlan(
      int totalRecords, boolean hasToken) {
    return new com.patra.ingest.domain.model.vo.fetch.FetchMetadata() {
      @Override
      public int totalRecords() {
        return totalRecords;
      }

      @Override
      public String dataSourceCode() {
        return ProvenanceCode.PUBMED.getCode();
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

  /** 创建测试用的 BatchSchedule (EPMC) - 计划元数据 */
  private com.patra.ingest.domain.model.vo.fetch.FetchMetadata createEpmcPlan(
      int totalRecords, String cursorMark) {
    return new com.patra.ingest.domain.model.vo.fetch.FetchMetadata() {
      @Override
      public int totalRecords() {
        return totalRecords;
      }

      @Override
      public String dataSourceCode() {
        return ProvenanceCode.EPMC.getCode();
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

  /** 创建测试用的 BatchSchedule (未知数据源) - 计划元数据 */
  private com.patra.ingest.domain.model.vo.fetch.FetchMetadata createUnknownPlan(int totalRecords) {
    return new com.patra.ingest.domain.model.vo.fetch.FetchMetadata() {
      @Override
      public int totalRecords() {
        return totalRecords;
      }

      @Override
      public String dataSourceCode() {
        return "unknown-source";
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
