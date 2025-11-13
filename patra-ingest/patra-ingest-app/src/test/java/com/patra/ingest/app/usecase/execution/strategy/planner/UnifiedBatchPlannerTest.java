package com.patra.ingest.app.usecase.execution.strategy.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.model.DataType;
import com.patra.common.model.plan.EpmcPlanMetadata;
import com.patra.common.model.plan.PlanMetadata;
import com.patra.common.model.plan.PubmedPlanMetadata;
import com.patra.ingest.domain.exception.BatchPlanningException;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.batch.BatchPlan;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.model.vo.plan.WindowSpec;
import com.patra.ingest.domain.port.DataSourcePort;
import com.patra.ingest.domain.strategy.BatchGenerationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * UnifiedBatchPlanner 单元测试
 *
 * <p>测试重点：
 * <ul>
 *   <li>策略自动注册（通过 Spring 注入）
 *   <li>策略选择逻辑（根据 PlanMetadata 类型）
 *   <li>PubMed 数据源的规划流程
 *   <li>未知数据类型的异常处理
 *   <li>DataSourcePort.preparePlan() 的调用
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UnifiedBatchPlanner 单元测试")
class UnifiedBatchPlannerTest {

    @Mock
    private DataSourcePort dataSourcePort;

    @Mock
    private BatchGenerationStrategy mockStrategy1;

    @Mock
    private BatchGenerationStrategy mockStrategy2;

    private UnifiedBatchPlanner planner;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // 配置 mock 策略支持的类型
        when(mockStrategy1.getSupportedType()).thenAnswer(invocation -> PubmedPlanMetadata.class);
        when(mockStrategy2.getSupportedType()).thenAnswer(invocation -> EpmcPlanMetadata.class);

        // 创建 planner 并自动注册策略
        List<BatchGenerationStrategy> strategies = List.of(mockStrategy1, mockStrategy2);
        planner = new UnifiedBatchPlanner(dataSourcePort, strategies);
    }

    @Test
    @DisplayName("构造函数应该自动注册所有策略")
    void should_register_all_strategies_in_constructor() {
        // 验证：通过日志或后续调用验证策略已注册
        // 这里通过实际使用策略来验证
        PubmedPlanMetadata pubmedPlan = new PubmedPlanMetadata(1000, null, null);
        ExecutionContext ctx = createContext("pubmed");

        when(dataSourcePort.preparePlan(any(), eq(DataType.LITERATURE))).thenReturn(pubmedPlan);
        when(mockStrategy1.generateBatches(any(), any())).thenReturn(List.of());

        // when
        planner.plan(ctx);

        // then
        verify(mockStrategy1).generateBatches(pubmedPlan, ctx);
    }

    @Test
    @DisplayName("getProvenanceCode 应该返回 null（支持所有数据源）")
    void should_return_null_for_provenance_code() {
        // when
        ProvenanceCode provenanceCode = planner.getProvenanceCode();

        // then
        assertThat(provenanceCode).isNull();
    }

    @Test
    @DisplayName("应该成功规划 PubMed 数据源")
    void should_plan_pubmed_data_source_successfully() {
        // given
        int totalCount = 1000;
        PubmedPlanMetadata pubmedPlan = new PubmedPlanMetadata(totalCount, null, null);
        ExecutionContext ctx = createContext("pubmed");

        List<Batch> mockBatches = List.of(
            createBatch(1),
            createBatch(2),
            createBatch(3)
        );

        when(dataSourcePort.preparePlan(ctx, DataType.LITERATURE)).thenReturn(pubmedPlan);
        when(mockStrategy1.generateBatches(pubmedPlan, ctx)).thenReturn(mockBatches);

        // when
        BatchPlan result = planner.plan(ctx);

        // then
        assertThat(result.batches()).hasSize(3);
        assertThat(result.totalBatches()).isEqualTo(3);
        assertThat(result.hasBatches()).isTrue();

        verify(dataSourcePort).preparePlan(ctx, DataType.LITERATURE);
        verify(mockStrategy1).generateBatches(pubmedPlan, ctx);
    }

    @Test
    @DisplayName("应该成功规划 EPMC 数据源")
    void should_plan_epmc_data_source_successfully() {
        // given
        int totalCount = 2000;
        EpmcPlanMetadata epmcPlan = new EpmcPlanMetadata(totalCount, "cursor-123");
        ExecutionContext ctx = createContext("epmc");

        List<Batch> mockBatches = List.of(
            createBatch(1),
            createBatch(2)
        );

        when(dataSourcePort.preparePlan(ctx, DataType.LITERATURE)).thenReturn(epmcPlan);
        when(mockStrategy2.generateBatches(epmcPlan, ctx)).thenReturn(mockBatches);

        // when
        BatchPlan result = planner.plan(ctx);

        // then
        assertThat(result.batches()).hasSize(2);
        assertThat(result.totalBatches()).isEqualTo(2);

        verify(dataSourcePort).preparePlan(ctx, DataType.LITERATURE);
        verify(mockStrategy2).generateBatches(epmcPlan, ctx);
    }

    @Test
    @DisplayName("当 totalCount 为 0 时应该返回空计划")
    void should_return_empty_plan_when_total_count_is_zero() {
        // given
        PubmedPlanMetadata emptyPlan = new PubmedPlanMetadata(0, null, null);
        ExecutionContext ctx = createContext("pubmed");

        when(dataSourcePort.preparePlan(ctx, DataType.LITERATURE)).thenReturn(emptyPlan);

        // when
        BatchPlan result = planner.plan(ctx);

        // then
        assertThat(result.totalBatches()).isZero();
        assertThat(result.batches()).isEmpty();
        assertThat(result.hasBatches()).isFalse();

        verify(dataSourcePort).preparePlan(ctx, DataType.LITERATURE);
        verify(mockStrategy1, never()).generateBatches(any(), any());
    }

    @Test
    @DisplayName("当未找到对应策略时应该抛出异常")
    void should_throw_exception_when_strategy_not_found() {
        // given
        // 创建一个未注册策略的 PlanMetadata 类型（使用匿名子类）
        PlanMetadata unknownPlan = new PlanMetadata("unknown-source", 100) {
            @Override
            public boolean hasSessionToken() {
                return false;
            }
        };
        ExecutionContext ctx = createContext("unknown");

        when(dataSourcePort.preparePlan(ctx, DataType.LITERATURE)).thenReturn(unknownPlan);

        // when & then
        assertThatThrownBy(() -> planner.plan(ctx))
            .isInstanceOf(BatchPlanningException.class)
            .hasMessageContaining("批次规划失败")
            .hasCauseInstanceOf(IllegalStateException.class);

        verify(dataSourcePort).preparePlan(ctx, DataType.LITERATURE);
    }

    @Test
    @DisplayName("当 DataSourcePort 抛出异常时应该包装为 BatchPlanningException")
    void should_wrap_data_source_exception_as_batch_planning_exception() {
        // given
        ExecutionContext ctx = createContext("pubmed");
        RuntimeException sourceException = new RuntimeException("数据源错误");

        when(dataSourcePort.preparePlan(ctx, DataType.LITERATURE)).thenThrow(sourceException);

        // when & then
        assertThatThrownBy(() -> planner.plan(ctx))
            .isInstanceOf(BatchPlanningException.class)
            .hasMessageContaining("批次规划失败")
            .hasCause(sourceException);

        verify(dataSourcePort).preparePlan(ctx, DataType.LITERATURE);
    }

    @Test
    @DisplayName("当策略生成批次失败时应该包装为 BatchPlanningException")
    void should_wrap_strategy_exception_as_batch_planning_exception() {
        // given
        PubmedPlanMetadata pubmedPlan = new PubmedPlanMetadata(1000, null, null);
        ExecutionContext ctx = createContext("pubmed");
        RuntimeException strategyException = new RuntimeException("批次生成错误");

        when(dataSourcePort.preparePlan(ctx, DataType.LITERATURE)).thenReturn(pubmedPlan);
        when(mockStrategy1.generateBatches(pubmedPlan, ctx)).thenThrow(strategyException);

        // when & then
        assertThatThrownBy(() -> planner.plan(ctx))
            .isInstanceOf(BatchPlanningException.class)
            .hasMessageContaining("批次规划失败")
            .hasCause(strategyException);

        verify(dataSourcePort).preparePlan(ctx, DataType.LITERATURE);
        verify(mockStrategy1).generateBatches(pubmedPlan, ctx);
    }

    @Test
    @DisplayName("应该正确处理策略返回空列表的情况")
    void should_handle_empty_batch_list_from_strategy() {
        // given
        PubmedPlanMetadata pubmedPlan = new PubmedPlanMetadata(1000, null, null);
        ExecutionContext ctx = createContext("pubmed");

        when(dataSourcePort.preparePlan(ctx, DataType.LITERATURE)).thenReturn(pubmedPlan);
        when(mockStrategy1.generateBatches(pubmedPlan, ctx)).thenReturn(List.of());

        // when
        BatchPlan result = planner.plan(ctx);

        // then
        assertThat(result.batches()).isEmpty();
        assertThat(result.totalBatches()).isZero();
        assertThat(result.hasBatches()).isFalse();
    }

    @Test
    @DisplayName("构造函数应该拒绝返回 null supportedType 的策略")
    void should_warn_about_strategy_with_null_supported_type() {
        // given
        BatchGenerationStrategy invalidStrategy = mock(BatchGenerationStrategy.class);
        when(invalidStrategy.getSupportedType()).thenAnswer(invocation -> null);

        List<BatchGenerationStrategy> strategies = List.of(mockStrategy1, invalidStrategy);

        // when
        UnifiedBatchPlanner planner = new UnifiedBatchPlanner(dataSourcePort, strategies);

        // then
        // 验证：planner 应该跳过 null 策略，只注册有效策略
        PubmedPlanMetadata pubmedPlan = new PubmedPlanMetadata(1000, null, null);
        ExecutionContext ctx = createContext("pubmed");

        when(dataSourcePort.preparePlan(any(), eq(DataType.LITERATURE))).thenReturn(pubmedPlan);
        when(mockStrategy1.generateBatches(any(), any())).thenReturn(List.of());

        planner.plan(ctx);

        verify(mockStrategy1).generateBatches(any(), any());
        verify(invalidStrategy, never()).generateBatches(any(), any());
    }

    @Test
    @DisplayName("构造函数应该拒绝重复的策略类型")
    void should_throw_exception_when_duplicate_strategy_types() {
        // given
        BatchGenerationStrategy duplicateStrategy = mock(BatchGenerationStrategy.class);
        when(duplicateStrategy.getSupportedType()).thenAnswer(invocation -> PubmedPlanMetadata.class);

        List<BatchGenerationStrategy> strategies = List.of(mockStrategy1, duplicateStrategy);

        // when & then
        assertThatThrownBy(() -> new UnifiedBatchPlanner(dataSourcePort, strategies))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("重复的批次生成策略");
    }

    // === 辅助方法 ===

    /**
     * 创建测试用的 ExecutionContext
     */
    private ExecutionContext createContext(String provenanceCode) {
        ProvenanceConfigSnapshot configSnapshot = mock(ProvenanceConfigSnapshot.class);
        JsonNode params = objectMapper.createObjectNode();

        return new ExecutionContext(
            1L,                     // taskId
            1L,                     // runId
            1L,                     // planId
            1L,                     // sliceId
            1L,                     // scheduleInstanceId
            provenanceCode,         // provenanceCode
            "search",               // operationCode
            DataType.LITERATURE,    // dataType
            configSnapshot,         // configSnapshot
            "expr-hash",            // exprHash
            "test-query",           // compiledQuery
            params,                 // compiledParams
            "normalized-expr",      // normalizedExpression
            new WindowSpec.Single(), // windowSpec
            null                    // planMetadata
        );
    }

    /**
     * 创建测试用的 Batch
     */
    private Batch createBatch(int batchNo) {
        return Batch.first("test-query", objectMapper.createObjectNode());
    }
}
