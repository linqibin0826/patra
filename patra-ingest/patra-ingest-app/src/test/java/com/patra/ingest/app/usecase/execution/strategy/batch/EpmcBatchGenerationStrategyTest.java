package com.patra.ingest.app.usecase.execution.strategy.batch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.model.DataType;
import com.patra.common.model.plan.EpmcPlanMetadata;
import com.patra.common.model.plan.PlanMetadata;
import com.patra.common.model.plan.PubmedPlanMetadata;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.model.vo.plan.WindowSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * EpmcBatchGenerationStrategy 单元测试
 *
 * <p>测试重点：
 * <ul>
 *   <li>getSupportedType() 返回 EpmcPlanMetadata.class
 *   <li>批次生成逻辑：根据 totalCount 和 pageSize 分页
 *   <li>批次对象属性正确：batchNo, query, params, cursorToken, pageSize
 *   <li>边界情况：totalCount = 0, totalCount < pageSize
 *   <li>类型校验：拒绝非 EpmcPlanMetadata 类型
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
@DisplayName("EpmcBatchGenerationStrategy 单元测试")
class EpmcBatchGenerationStrategyTest {

    private EpmcBatchGenerationStrategy strategy;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        strategy = new EpmcBatchGenerationStrategy();
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("getSupportedType() 方法测试")
    class GetSupportedTypeTests {

        @Test
        @DisplayName("应该返回 EpmcPlanMetadata 类型")
        void should_return_epmc_plan_metadata_type() {
            // when
            Class<? extends PlanMetadata> type = strategy.getSupportedType();

            // then
            assertThat(type).isEqualTo(EpmcPlanMetadata.class);
        }
    }

    @Nested
    @DisplayName("generateBatches() 方法测试")
    class GenerateBatchesTests {

        @Test
        @DisplayName("应该根据 totalCount 和 pageSize 生成正确数量的批次")
        void should_generate_correct_number_of_batches() {
            // given
            int totalCount = 1000;
            int pageSize = 100;
            EpmcPlanMetadata plan = new EpmcPlanMetadata(totalCount, null);
            ExecutionContext ctx = createContext(pageSize);

            // when
            List<Batch> batches = strategy.generateBatches(plan, ctx);

            // then
            int expectedBatchCount = (int) Math.ceil((double) totalCount / pageSize);
            assertThat(batches).hasSize(expectedBatchCount);
            assertThat(batches).hasSize(10);
        }

        @Test
        @DisplayName("应该为每个批次设置正确的 batchNo")
        void should_set_correct_batch_no_for_each_batch() {
            // given
            int totalCount = 250;
            int pageSize = 100;
            EpmcPlanMetadata plan = new EpmcPlanMetadata(totalCount, null);
            ExecutionContext ctx = createContext(pageSize);

            // when
            List<Batch> batches = strategy.generateBatches(plan, ctx);

            // then
            assertThat(batches).hasSize(3);
            assertThat(batches.get(0).batchNo()).isEqualTo(1);
            assertThat(batches.get(1).batchNo()).isEqualTo(2);
            assertThat(batches.get(2).batchNo()).isEqualTo(3);
        }

        @Test
        @DisplayName("应该为每个批次设置正确的查询和参数")
        void should_set_correct_query_and_params_for_each_batch() {
            // given
            int totalCount = 200;
            int pageSize = 100;
            String query = "cancer";
            JsonNode params = objectMapper.createObjectNode().put("format", "json");

            EpmcPlanMetadata plan = new EpmcPlanMetadata(totalCount, null);
            ExecutionContext ctx = createContext(pageSize, query, params);

            // when
            List<Batch> batches = strategy.generateBatches(plan, ctx);

            // then
            assertThat(batches).hasSize(2);
            batches.forEach(batch -> {
                assertThat(batch.query()).isEqualTo(query);
                assertThat(batch.params()).isEqualTo(params);
            });
        }

        @Test
        @DisplayName("应该在有 cursorMark 时生成包含 session 的批次")
        void should_generate_batches_with_cursor_when_cursor_mark_available() {
            // given
            int totalCount = 1000;
            int pageSize = 100;
            String cursorMark = "*";
            EpmcPlanMetadata plan = new EpmcPlanMetadata(totalCount, cursorMark);
            ExecutionContext ctx = createContext(pageSize);

            // when
            List<Batch> batches = strategy.generateBatches(plan, ctx);

            // then
            assertThat(batches).hasSize(10);
            assertThat(plan.hasSessionToken()).isTrue();

            // 验证所有批次都包含 cursorMark session token
            assertThat(batches).allMatch(batch ->
                batch.sessionTokens() != null &&
                !batch.sessionTokens().isEmpty() &&
                batch.sessionTokens().containsKey("cursorMark") &&
                batch.sessionTokens().get("cursorMark").equals(cursorMark)
            );
        }

        @Test
        @DisplayName("应该在无 cursorMark 时生成不含 session 的批次")
        void should_generate_batches_without_session_when_cursor_mark_not_available() {
            // given
            int totalCount = 500;
            int pageSize = 100;
            EpmcPlanMetadata plan = new EpmcPlanMetadata(totalCount, null);
            ExecutionContext ctx = createContext(pageSize);

            // when
            List<Batch> batches = strategy.generateBatches(plan, ctx);

            // then
            assertThat(batches).hasSize(5);
            assertThat(plan.hasSessionToken()).isFalse();

            // 验证所有批次都不包含 session tokens（应该是空 Map）
            assertThat(batches).allMatch(batch ->
                batch.sessionTokens() != null && batch.sessionTokens().isEmpty()
            );
        }

        @Test
        @DisplayName("应该使用基于游标的批次生成模式")
        void should_use_cursor_based_pagination_mode() {
            // given
            int totalCount = 300;
            int pageSize = 100;
            String cursorMark = "*";
            EpmcPlanMetadata plan = new EpmcPlanMetadata(totalCount, cursorMark);
            ExecutionContext ctx = createContext(pageSize);

            // when
            List<Batch> batches = strategy.generateBatches(plan, ctx);

            // then
            assertThat(batches).hasSize(3);

            // 验证批次使用游标模式（没有 pageNo，有 pageSize）
            batches.forEach(batch -> {
                assertThat(batch.pageNo()).isNull();  // EPMC 不使用 pageNo
                assertThat(batch.pageSize()).isEqualTo(pageSize);
                assertThat(batch.cursorToken()).isNull();  // cursorToken 通过 sessionTokens 传递
            });
        }

        @Test
        @DisplayName("当 totalCount 为 0 时应该返回空批次列表")
        void should_return_empty_list_when_total_count_is_zero() {
            // given
            int totalCount = 0;
            EpmcPlanMetadata plan = new EpmcPlanMetadata(totalCount, null);
            ExecutionContext ctx = createContext(100);

            // when
            List<Batch> batches = strategy.generateBatches(plan, ctx);

            // then
            assertThat(batches).isEmpty();
        }

        @Test
        @DisplayName("当 totalCount 小于 pageSize 时应该生成 1 个批次")
        void should_generate_single_batch_when_total_count_less_than_page_size() {
            // given
            int totalCount = 50;
            int pageSize = 100;
            EpmcPlanMetadata plan = new EpmcPlanMetadata(totalCount, null);
            ExecutionContext ctx = createContext(pageSize);

            // when
            List<Batch> batches = strategy.generateBatches(plan, ctx);

            // then
            assertThat(batches).hasSize(1);
            assertThat(batches.get(0).batchNo()).isEqualTo(1);
            assertThat(batches.get(0).pageSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("当 totalCount 刚好是 pageSize 的整数倍时应该生成正确数量的批次")
        void should_generate_exact_batches_when_total_count_is_multiple_of_page_size() {
            // given
            int totalCount = 500;
            int pageSize = 100;
            EpmcPlanMetadata plan = new EpmcPlanMetadata(totalCount, null);
            ExecutionContext ctx = createContext(pageSize);

            // when
            List<Batch> batches = strategy.generateBatches(plan, ctx);

            // then
            assertThat(batches).hasSize(5);
            assertThat(batches.get(4).batchNo()).isEqualTo(5);
        }

        @Test
        @DisplayName("当传入非 EpmcPlanMetadata 类型时应该抛出异常")
        void should_throw_exception_when_plan_type_is_not_epmc() {
            // given
            PubmedPlanMetadata wrongPlan = new PubmedPlanMetadata(1000, null, null);
            ExecutionContext ctx = createContext(100);

            // when & then
            assertThatThrownBy(() -> strategy.generateBatches(wrongPlan, ctx))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EpmcPlanMetadata");
        }

        @Test
        @DisplayName("应该支持大数据量的批次生成")
        void should_support_large_data_volume() {
            // given
            int totalCount = 10000;
            int pageSize = 100;
            EpmcPlanMetadata plan = new EpmcPlanMetadata(totalCount, null);
            ExecutionContext ctx = createContext(pageSize);

            // when
            List<Batch> batches = strategy.generateBatches(plan, ctx);

            // then
            assertThat(batches).hasSize(100);
            assertThat(batches.get(0).batchNo()).isEqualTo(1);
            assertThat(batches.get(99).batchNo()).isEqualTo(100);
        }
    }

    // === 辅助方法 ===

    /**
     * 创建测试用的 ExecutionContext
     */
    private ExecutionContext createContext(int pageSize) {
        return createContext(pageSize, "test-query", objectMapper.createObjectNode());
    }

    /**
     * 创建测试用的 ExecutionContext（完整参数）
     */
    private ExecutionContext createContext(int pageSize, String query, JsonNode params) {
        ProvenanceConfigSnapshot configSnapshot = mock(ProvenanceConfigSnapshot.class);
        ProvenanceConfigSnapshot.PaginationConfig paginationConfig =
            mock(ProvenanceConfigSnapshot.PaginationConfig.class);

        when(configSnapshot.pagination()).thenReturn(paginationConfig);
        when(paginationConfig.pageSizeValue()).thenReturn(pageSize);

        return new ExecutionContext(
            1L,                     // taskId
            1L,                     // runId
            1L,                     // planId
            1L,                     // sliceId
            1L,                     // scheduleInstanceId
            "epmc",                 // provenanceCode
            "search",               // operationCode
            DataType.LITERATURE,    // dataType
            configSnapshot,         // configSnapshot
            "expr-hash",            // exprHash
            query,                  // compiledQuery
            params,                 // compiledParams
            "normalized-expr",      // normalizedExpression
            new WindowSpec.Single(), // windowSpec
            null                    // planMetadata
        );
    }
}
