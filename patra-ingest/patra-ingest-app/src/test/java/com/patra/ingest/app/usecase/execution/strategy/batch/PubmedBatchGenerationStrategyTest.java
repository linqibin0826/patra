package com.patra.ingest.app.usecase.execution.strategy.batch;

import com.patra.common.enums.ProvenanceCode;
import com.patra.common.model.DataType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.model.vo.plan.BatchPlan;
import com.patra.ingest.domain.model.vo.plan.WindowSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PubmedBatchGenerationStrategy 单元测试
 *
 * <p>测试重点：
 * <ul>
 *   <li>getSupportedDataSourceCode() 返回 "pubmed"
 *   <li>批次生成逻辑：根据 totalRecords 和 pageSize 分页
 *   <li>批次对象属性正确：batchNo, query, params, pageNo, pageSize
 *   <li>边界情况：totalRecords = 0, totalRecords < pageSize
 *   <li>会话令牌传递：webEnv 和 queryKey
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
@DisplayName("PubmedBatchGenerationStrategy 单元测试")
class PubmedBatchGenerationStrategyTest {

    private PubmedBatchGenerationStrategy strategy;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        strategy = new PubmedBatchGenerationStrategy();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("getSupportedDataSourceCode 应该返回 'pubmed'")
    void should_return_pubmed_data_source_code() {
        // when
        String supportedCode = strategy.getSupportedDataSourceCode();

        // then
        assertThat(supportedCode).isEqualTo("pubmed");
    }

    @Test
    @DisplayName("应该根据 totalRecords 和 pageSize 生成正确数量的批次")
    void should_generate_correct_number_of_batches() {
        // given
        int totalRecords = 1000;
        int pageSize = 100;
        BatchPlan plan = createBatchPlan(totalRecords, "pubmed", null);
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
        BatchPlan plan = createBatchPlan(totalRecords, "pubmed", null);
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
    @DisplayName("应该为每个批次设置正确的分页参数")
    void should_set_correct_pagination_params_for_each_batch() {
        // given
        int totalRecords = 250;
        int pageSize = 100;
        BatchPlan plan = createBatchPlan(totalRecords, "pubmed", null);
        ExecutionContext ctx = createContext(pageSize);

        // when
        List<Batch> batches = strategy.generateBatches(plan, ctx);

        // then
        assertThat(batches).hasSize(3);

        // 第一批：offset=0, size=100
        assertThat(batches.get(0).pageNo()).isEqualTo(0);
        assertThat(batches.get(0).pageSize()).isEqualTo(100);

        // 第二批：offset=100, size=100
        assertThat(batches.get(1).pageNo()).isEqualTo(100);
        assertThat(batches.get(1).pageSize()).isEqualTo(100);

        // 第三批：offset=200, size=100
        assertThat(batches.get(2).pageNo()).isEqualTo(200);
        assertThat(batches.get(2).pageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("应该为每个批次设置正确的查询和参数")
    void should_set_correct_query_and_params_for_each_batch() {
        // given
        int totalRecords = 200;
        int pageSize = 100;
        String query = "cancer[Title]";
        JsonNode params = objectMapper.createObjectNode().put("db", "pubmed");

        BatchPlan plan = createBatchPlan(totalRecords, "pubmed", null);
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
    @DisplayName("当 totalRecords 为 0 时应该返回空批次列表")
    void should_return_empty_list_when_total_records_is_zero() {
        // given
        BatchPlan plan = createBatchPlan(0, "pubmed", null);
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
        BatchPlan plan = createBatchPlan(totalRecords, "pubmed", null);
        ExecutionContext ctx = createContext(pageSize);

        // when
        List<Batch> batches = strategy.generateBatches(plan, ctx);

        // then
        assertThat(batches).hasSize(1);
        assertThat(batches.get(0).batchNo()).isEqualTo(1);
        assertThat(batches.get(0).pageNo()).isEqualTo(0);
        assertThat(batches.get(0).pageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("当 totalRecords 刚好是 pageSize 的整数倍时应该生成正确数量的批次")
    void should_generate_exact_batches_when_total_records_is_multiple_of_page_size() {
        // given
        int totalRecords = 500;
        int pageSize = 100;
        BatchPlan plan = createBatchPlan(totalRecords, "pubmed", null);
        ExecutionContext ctx = createContext(pageSize);

        // when
        List<Batch> batches = strategy.generateBatches(plan, ctx);

        // then
        assertThat(batches).hasSize(5);
        assertThat(batches.get(4).batchNo()).isEqualTo(5);
        assertThat(batches.get(4).pageNo()).isEqualTo(400);
    }

    @Test
    @DisplayName("应该在有 History Server token 时生成包含 session 的批次")
    void should_generate_batches_with_session_when_available() {
        // given
        int totalRecords = 1000;
        int pageSize = 100;
        String webEnv = "MCID_674c8b5a5e8a9b1234567890";
        String queryKey = "1";
        Map<String, String> stateToken = Map.of("webEnv", webEnv, "queryKey", queryKey);
        BatchPlan plan = createBatchPlan(totalRecords, "pubmed", stateToken);
        ExecutionContext ctx = createContext(pageSize);

        // when
        List<Batch> batches = strategy.generateBatches(plan, ctx);

        // then
        assertThat(batches).hasSize(10);
        assertThat(plan.hasStateToken()).isTrue();

        // 验证所有批次都包含 session tokens
        assertThat(batches).allMatch(batch ->
            batch.sessionTokens() != null &&
            !batch.sessionTokens().isEmpty() &&
            batch.sessionTokens().containsKey("webEnv") &&
            batch.sessionTokens().get("webEnv").equals(webEnv) &&
            batch.sessionTokens().containsKey("queryKey") &&
            batch.sessionTokens().get("queryKey").equals(queryKey)
        );
    }

    @Test
    @DisplayName("应该在无 History Server token 时生成不含 session 的批次")
    void should_generate_batches_without_session_when_not_available() {
        // given
        int totalRecords = 500;
        int pageSize = 100;
        BatchPlan plan = createBatchPlan(totalRecords, "pubmed", null);
        ExecutionContext ctx = createContext(pageSize);

        // when
        List<Batch> batches = strategy.generateBatches(plan, ctx);

        // then
        assertThat(batches).hasSize(5);
        assertThat(plan.hasStateToken()).isFalse();

        // 验证所有批次都不包含 session tokens（应该是空 Map）
        assertThat(batches).allMatch(batch ->
            batch.sessionTokens() != null && batch.sessionTokens().isEmpty()
        );
    }

    @Test
    @DisplayName("应该支持大数据量的批次生成")
    void should_support_large_data_volume() {
        // given
        int totalRecords = 10000;
        int pageSize = 100;
        BatchPlan plan = createBatchPlan(totalRecords, "pubmed", null);
        ExecutionContext ctx = createContext(pageSize);

        // when
        List<Batch> batches = strategy.generateBatches(plan, ctx);

        // then
        assertThat(batches).hasSize(100);
        assertThat(batches.get(0).pageNo()).isEqualTo(0);
        assertThat(batches.get(99).pageNo()).isEqualTo(9900);
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
            ProvenanceCode.PUBMED,  // provenanceCode
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
