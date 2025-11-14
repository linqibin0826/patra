package com.patra.ingest.app.usecase.execution.strategy.batch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.model.DataType;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.model.vo.plan.BatchPlan;
import com.patra.ingest.domain.model.vo.plan.WindowSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * EpmcBatchGenerationStrategy 单元测试
 *
 * <p>测试重点：
 * <ul>
 *   <li>getSupportedDataSourceCode() 返回 "epmc"
 *   <li>批次生成逻辑：根据 totalRecords 和 pageSize 分页
 *   <li>批次对象属性正确：batchNo, query, params, cursorToken, pageSize
 *   <li>边界情况：totalRecords = 0, totalRecords < pageSize
 *   <li>会话令牌传递：cursorMark
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
    @DisplayName("getSupportedDataSourceCode() 方法测试")
    class GetSupportedDataSourceCodeTests {

        @Test
        @DisplayName("应该返回 'epmc' 数据源代码")
        void should_return_epmc_data_source_code() {
            // when
            String supportedCode = strategy.getSupportedDataSourceCode();

            // then
            assertThat(supportedCode).isEqualTo("epmc");
        }
    }

    @Nested
    @DisplayName("generateBatches() 方法测试")
    class GenerateBatchesTests {

        @Test
        @DisplayName("应该根据 totalRecords 和 pageSize 生成正确数量的批次")
        void should_generate_correct_number_of_batches() {
            // given
            int totalRecords = 1000;
            int pageSize = 100;
            BatchPlan plan = createBatchPlan(totalRecords, "epmc", null);
            ExecutionContext ctx = createContext(pageSize);

            // when
            List<Batch> batches = strategy.generateBatches(plan, ctx);

            // then
            int expectedBatchCount = (int) Math.ceil((double) totalRecords / pageSize);
            assertThat(batches).hasSize(expectedBatchCount);
            assertThat(batches).hasSize(10);
        }

        @Test
        @DisplayName("应该为每个批次设置正确的 batchNo")
        void should_set_correct_batch_no_for_each_batch() {
            // given
            int totalRecords = 250;
            int pageSize = 100;
            BatchPlan plan = createBatchPlan(totalRecords, "epmc", null);
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
            int totalRecords = 200;
            int pageSize = 100;
            String query = "cancer";
            JsonNode params = objectMapper.createObjectNode().put("format", "json");

            BatchPlan plan = createBatchPlan(totalRecords, "epmc", null);
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
            int totalRecords = 1000;
            int pageSize = 100;
            String cursorMark = "*";
            Map<String, String> stateToken = Map.of("cursorMark", cursorMark);
            BatchPlan plan = createBatchPlan(totalRecords, "epmc", stateToken);
            ExecutionContext ctx = createContext(pageSize);

            // when
            List<Batch> batches = strategy.generateBatches(plan, ctx);

            // then
            assertThat(batches).hasSize(10);
            assertThat(plan.hasStateToken()).isTrue();

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
            int totalRecords = 500;
            int pageSize = 100;
            BatchPlan plan = createBatchPlan(totalRecords, "epmc", null);
            ExecutionContext ctx = createContext(pageSize);

            // when
            List<Batch> batches = strategy.generateBatches(plan, ctx);

            // then
            assertThat(batches).hasSize(5);
            assertThat(plan.hasStateToken()).isFalse();

            // 验证所有批次都不包含 session tokens（应该是 null 或空 Map）
            assertThat(batches).allMatch(batch ->
                batch.sessionTokens() == null || batch.sessionTokens().isEmpty()
            );
        }

        @Test
        @DisplayName("应该使用基于游标的批次生成模式")
        void should_use_cursor_based_pagination_mode() {
            // given
            int totalRecords = 300;
            int pageSize = 100;
            String cursorMark = "*";
            Map<String, String> stateToken = Map.of("cursorMark", cursorMark);
            BatchPlan plan = createBatchPlan(totalRecords, "epmc", stateToken);
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
        @DisplayName("当 totalRecords 为 0 时应该返回空批次列表")
        void should_return_empty_list_when_total_records_is_zero() {
            // given
            BatchPlan plan = createBatchPlan(0, "epmc", null);
            ExecutionContext ctx = createContext(100);

            // when
            List<Batch> batches = strategy.generateBatches(plan, ctx);

            // then
            assertThat(batches).isEmpty();
        }

        @Test
        @DisplayName("当 totalRecords 小于 pageSize 时应该生成 1 个批次")
        void should_generate_single_batch_when_total_records_less_than_page_size() {
            // given
            int totalRecords = 50;
            int pageSize = 100;
            BatchPlan plan = createBatchPlan(totalRecords, "epmc", null);
            ExecutionContext ctx = createContext(pageSize);

            // when
            List<Batch> batches = strategy.generateBatches(plan, ctx);

            // then
            assertThat(batches).hasSize(1);
            assertThat(batches.get(0).batchNo()).isEqualTo(1);
            assertThat(batches.get(0).pageSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("当 totalRecords 刚好是 pageSize 的整数倍时应该生成正确数量的批次")
        void should_generate_exact_batches_when_total_records_is_multiple_of_page_size() {
            // given
            int totalRecords = 500;
            int pageSize = 100;
            BatchPlan plan = createBatchPlan(totalRecords, "epmc", null);
            ExecutionContext ctx = createContext(pageSize);

            // when
            List<Batch> batches = strategy.generateBatches(plan, ctx);

            // then
            assertThat(batches).hasSize(5);
            assertThat(batches.get(4).batchNo()).isEqualTo(5);
        }

        @Test
        @DisplayName("应该支持大数据量的批次生成")
        void should_support_large_data_volume() {
            // given
            int totalRecords = 10000;
            int pageSize = 100;
            BatchPlan plan = createBatchPlan(totalRecords, "epmc", null);
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
     * 创建测试用的 BatchPlan
     *
     * @param totalRecords 总记录数
     * @param dataSourceCode 数据源代码
     * @param stateToken 状态令牌（可为 null）
     * @return BatchPlan 实例
     */
    private BatchPlan createBatchPlan(int totalRecords, String dataSourceCode, Map<String, String> stateToken) {
        return new BatchPlan() {
            @Override
            public int totalRecords() {
                return totalRecords;
            }

            @Override
            public String dataSourceCode() {
                return dataSourceCode;
            }

            @Override
            public boolean hasStateToken() {
                return stateToken != null && !stateToken.isEmpty();
            }

            @Override
            public Optional<Map<String, String>> stateToken() {
                return Optional.ofNullable(stateToken);
            }
        };
    }

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
            ProvenanceCode.EPMC,    // provenanceCode
            "search",               // operationCode
            DataType.LITERATURE,    // dataType
            configSnapshot,         // configSnapshot
            "expr-hash",            // exprHash
            query,                  // compiledQuery
            params,                 // compiledParams
            "normalized-expr",      // normalizedExpression
            new WindowSpec.Single() // windowSpec
        );
    }
}
