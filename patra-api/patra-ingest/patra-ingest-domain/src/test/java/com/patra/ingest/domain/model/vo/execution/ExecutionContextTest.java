package com.patra.ingest.domain.model.vo.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.plan.WindowSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExecutionContext 值对象单元测试")
class ExecutionContextTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final Long TASK_ID = 1001L;
  private static final Long RUN_ID = 2001L;
  private static final Long PLAN_ID = 3001L;
  private static final Long SLICE_ID = 4001L;
  private static final Long SCHEDULE_INSTANCE_ID = 5001L;
  private static final String PROVENANCE_CODE = "pubmed";
  private static final String OPERATION_CODE = "FETCH_CITATIONS";
  private static final String EXPR_HASH = "abc123def456";
  private static final String COMPILED_QUERY = "SELECT * FROM citations WHERE date > ?";
  private static final String NORMALIZED_EXPRESSION = "date > 2025-01-01";

  @Nested
  @DisplayName("构造器测试")
  class ConstructorTests {

    @Test
    @DisplayName("应该使用所有字段创建有效的执行上下文")
    void shouldCreateValidExecutionContextWithAllFields() {
      // Given
      ProvenanceConfigSnapshot configSnapshot = createMinimalConfigSnapshot();
      JsonNode compiledParams = createMinimalJsonNode();
      WindowSpec windowSpec = WindowSpec.ofSingle();

      // When
      ExecutionContext context =
          new ExecutionContext(
              TASK_ID,
              RUN_ID,
              PLAN_ID,
              SLICE_ID,
              SCHEDULE_INSTANCE_ID,
              PROVENANCE_CODE,
              OPERATION_CODE,
              configSnapshot,
              EXPR_HASH,
              COMPILED_QUERY,
              compiledParams,
              NORMALIZED_EXPRESSION,
              windowSpec);

      // Then
      assertThat(context.taskId()).isEqualTo(TASK_ID);
      assertThat(context.runId()).isEqualTo(RUN_ID);
      assertThat(context.planId()).isEqualTo(PLAN_ID);
      assertThat(context.sliceId()).isEqualTo(SLICE_ID);
      assertThat(context.scheduleInstanceId()).isEqualTo(SCHEDULE_INSTANCE_ID);
      assertThat(context.provenanceCode()).isEqualTo(PROVENANCE_CODE);
      assertThat(context.operationCode()).isEqualTo(OPERATION_CODE);
      assertThat(context.configSnapshot()).isEqualTo(configSnapshot);
      assertThat(context.exprHash()).isEqualTo(EXPR_HASH);
      assertThat(context.compiledQuery()).isEqualTo(COMPILED_QUERY);
      assertThat(context.compiledParams()).isEqualTo(compiledParams);
      assertThat(context.normalizedExpression()).isEqualTo(NORMALIZED_EXPRESSION);
      assertThat(context.windowSpec()).isEqualTo(windowSpec);
    }

    @Test
    @DisplayName("应该允许创建所有字段为 null 的上下文")
    void shouldAllowCreatingContextWithAllNullFields() {
      // When
      ExecutionContext context =
          new ExecutionContext(
              null, null, null, null, null, null, null, null, null, null, null, null, null);

      // Then
      assertThat(context.taskId()).isNull();
      assertThat(context.runId()).isNull();
      assertThat(context.planId()).isNull();
      assertThat(context.sliceId()).isNull();
      assertThat(context.scheduleInstanceId()).isNull();
      assertThat(context.provenanceCode()).isNull();
      assertThat(context.operationCode()).isNull();
      assertThat(context.configSnapshot()).isNull();
      assertThat(context.exprHash()).isNull();
      assertThat(context.compiledQuery()).isNull();
      assertThat(context.compiledParams()).isNull();
      assertThat(context.normalizedExpression()).isNull();
      assertThat(context.windowSpec()).isNull();
    }

    @Test
    @DisplayName("应该允许创建部分字段为 null 的上下文")
    void shouldAllowCreatingContextWithPartialNullFields() {
      // When
      ExecutionContext context =
          new ExecutionContext(
              TASK_ID,
              RUN_ID,
              null,
              null,
              null,
              PROVENANCE_CODE,
              OPERATION_CODE,
              null,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(context.taskId()).isEqualTo(TASK_ID);
      assertThat(context.runId()).isEqualTo(RUN_ID);
      assertThat(context.provenanceCode()).isEqualTo(PROVENANCE_CODE);
      assertThat(context.operationCode()).isEqualTo(OPERATION_CODE);
      assertThat(context.configSnapshot()).isNull();
      assertThat(context.windowSpec()).isNull();
    }
  }

  @Nested
  @DisplayName("WindowSpec 集成测试")
  class WindowSpecIntegrationTests {

    @Test
    @DisplayName("应该支持时间窗口规范")
    void shouldSupportTimeWindowSpec() {
      // Given
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-31T23:59:59Z");
      WindowSpec windowSpec = WindowSpec.ofTime(from, to);

      // When
      ExecutionContext context =
          new ExecutionContext(
              TASK_ID,
              RUN_ID,
              PLAN_ID,
              SLICE_ID,
              SCHEDULE_INSTANCE_ID,
              PROVENANCE_CODE,
              OPERATION_CODE,
              null,
              EXPR_HASH,
              COMPILED_QUERY,
              null,
              NORMALIZED_EXPRESSION,
              windowSpec);

      // Then
      assertThat(context.windowSpec()).isInstanceOf(WindowSpec.Time.class);
      assertThat(((WindowSpec.Time) context.windowSpec()).from()).isEqualTo(from);
      assertThat(((WindowSpec.Time) context.windowSpec()).to()).isEqualTo(to);
    }

    @Test
    @DisplayName("应该支持ID范围窗口规范")
    void shouldSupportIdRangeWindowSpec() {
      // Given
      WindowSpec windowSpec = WindowSpec.ofIdRange(1000L, 2000L);

      // When
      ExecutionContext context =
          new ExecutionContext(
              TASK_ID,
              RUN_ID,
              PLAN_ID,
              SLICE_ID,
              SCHEDULE_INSTANCE_ID,
              PROVENANCE_CODE,
              OPERATION_CODE,
              null,
              EXPR_HASH,
              COMPILED_QUERY,
              null,
              NORMALIZED_EXPRESSION,
              windowSpec);

      // Then
      assertThat(context.windowSpec()).isInstanceOf(WindowSpec.IdRange.class);
      assertThat(((WindowSpec.IdRange) context.windowSpec()).from()).isEqualTo(1000L);
      assertThat(((WindowSpec.IdRange) context.windowSpec()).to()).isEqualTo(2000L);
    }

    @Test
    @DisplayName("应该支持游标地标窗口规范")
    void shouldSupportCursorLandmarkWindowSpec() {
      // Given
      WindowSpec windowSpec = WindowSpec.ofCursor("token-start", "token-end");

      // When
      ExecutionContext context =
          new ExecutionContext(
              TASK_ID,
              RUN_ID,
              PLAN_ID,
              SLICE_ID,
              SCHEDULE_INSTANCE_ID,
              PROVENANCE_CODE,
              OPERATION_CODE,
              null,
              EXPR_HASH,
              COMPILED_QUERY,
              null,
              NORMALIZED_EXPRESSION,
              windowSpec);

      // Then
      assertThat(context.windowSpec()).isInstanceOf(WindowSpec.CursorLandmark.class);
      assertThat(((WindowSpec.CursorLandmark) context.windowSpec()).from())
          .isEqualTo("token-start");
      assertThat(((WindowSpec.CursorLandmark) context.windowSpec()).to()).isEqualTo("token-end");
    }

    @Test
    @DisplayName("应该支持容量预算窗口规范")
    void shouldSupportVolumeBudgetWindowSpec() {
      // Given
      WindowSpec windowSpec = WindowSpec.ofVolume(10000, "RECORDS");

      // When
      ExecutionContext context =
          new ExecutionContext(
              TASK_ID,
              RUN_ID,
              PLAN_ID,
              SLICE_ID,
              SCHEDULE_INSTANCE_ID,
              PROVENANCE_CODE,
              OPERATION_CODE,
              null,
              EXPR_HASH,
              COMPILED_QUERY,
              null,
              NORMALIZED_EXPRESSION,
              windowSpec);

      // Then
      assertThat(context.windowSpec()).isInstanceOf(WindowSpec.VolumeBudget.class);
      assertThat(((WindowSpec.VolumeBudget) context.windowSpec()).limit()).isEqualTo(10000);
      assertThat(((WindowSpec.VolumeBudget) context.windowSpec()).unit()).isEqualTo("RECORDS");
    }

    @Test
    @DisplayName("应该支持单一窗口规范")
    void shouldSupportSingleWindowSpec() {
      // Given
      WindowSpec windowSpec = WindowSpec.ofSingle();

      // When
      ExecutionContext context =
          new ExecutionContext(
              TASK_ID,
              RUN_ID,
              PLAN_ID,
              SLICE_ID,
              SCHEDULE_INSTANCE_ID,
              PROVENANCE_CODE,
              OPERATION_CODE,
              null,
              EXPR_HASH,
              COMPILED_QUERY,
              null,
              NORMALIZED_EXPRESSION,
              windowSpec);

      // Then
      assertThat(context.windowSpec()).isInstanceOf(WindowSpec.Single.class);
    }
  }

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemanticsTests {

    @Test
    @DisplayName("相同字段的实例应该相等")
    void instancesWithSameFieldsShouldBeEqual() {
      // Given
      ProvenanceConfigSnapshot configSnapshot = createMinimalConfigSnapshot();
      JsonNode compiledParams = createMinimalJsonNode();
      WindowSpec windowSpec = WindowSpec.ofSingle();

      ExecutionContext context1 =
          new ExecutionContext(
              TASK_ID,
              RUN_ID,
              PLAN_ID,
              SLICE_ID,
              SCHEDULE_INSTANCE_ID,
              PROVENANCE_CODE,
              OPERATION_CODE,
              configSnapshot,
              EXPR_HASH,
              COMPILED_QUERY,
              compiledParams,
              NORMALIZED_EXPRESSION,
              windowSpec);

      ExecutionContext context2 =
          new ExecutionContext(
              TASK_ID,
              RUN_ID,
              PLAN_ID,
              SLICE_ID,
              SCHEDULE_INSTANCE_ID,
              PROVENANCE_CODE,
              OPERATION_CODE,
              configSnapshot,
              EXPR_HASH,
              COMPILED_QUERY,
              compiledParams,
              NORMALIZED_EXPRESSION,
              windowSpec);

      // Then
      assertThat(context1).isEqualTo(context2);
      assertThat(context1.hashCode()).isEqualTo(context2.hashCode());
    }

    @Test
    @DisplayName("不同字段的实例应该不相等")
    void instancesWithDifferentFieldsShouldNotBeEqual() {
      // Given
      ExecutionContext context1 =
          new ExecutionContext(
              TASK_ID,
              RUN_ID,
              PLAN_ID,
              SLICE_ID,
              SCHEDULE_INSTANCE_ID,
              PROVENANCE_CODE,
              OPERATION_CODE,
              null,
              EXPR_HASH,
              COMPILED_QUERY,
              null,
              NORMALIZED_EXPRESSION,
              null);

      ExecutionContext context2 =
          new ExecutionContext(
              TASK_ID + 1, // 不同的 taskId
              RUN_ID,
              PLAN_ID,
              SLICE_ID,
              SCHEDULE_INSTANCE_ID,
              PROVENANCE_CODE,
              OPERATION_CODE,
              null,
              EXPR_HASH,
              COMPILED_QUERY,
              null,
              NORMALIZED_EXPRESSION,
              null);

      // Then
      assertThat(context1).isNotEqualTo(context2);
    }

    @Test
    @DisplayName("toString() 应该包含所有字段信息")
    void toStringShouldContainAllFields() {
      // Given
      ExecutionContext context =
          new ExecutionContext(
              TASK_ID,
              RUN_ID,
              PLAN_ID,
              SLICE_ID,
              SCHEDULE_INSTANCE_ID,
              PROVENANCE_CODE,
              OPERATION_CODE,
              null,
              EXPR_HASH,
              COMPILED_QUERY,
              null,
              NORMALIZED_EXPRESSION,
              null);

      // When
      String result = context.toString();

      // Then
      assertThat(result)
          .contains("ExecutionContext")
          .contains("taskId=" + TASK_ID)
          .contains("runId=" + RUN_ID)
          .contains("provenanceCode=" + PROVENANCE_CODE)
          .contains("operationCode=" + OPERATION_CODE);
    }

    @Test
    @DisplayName("应该支持作为 Map 的键")
    void shouldWorkAsMapKey() {
      // Given
      var map = new java.util.HashMap<ExecutionContext, String>();
      ExecutionContext key1 =
          new ExecutionContext(
              TASK_ID,
              RUN_ID,
              PLAN_ID,
              SLICE_ID,
              SCHEDULE_INSTANCE_ID,
              PROVENANCE_CODE,
              OPERATION_CODE,
              null,
              EXPR_HASH,
              COMPILED_QUERY,
              null,
              NORMALIZED_EXPRESSION,
              null);
      ExecutionContext key2 =
          new ExecutionContext(
              TASK_ID,
              RUN_ID,
              PLAN_ID,
              SLICE_ID,
              SCHEDULE_INSTANCE_ID,
              PROVENANCE_CODE,
              OPERATION_CODE,
              null,
              EXPR_HASH,
              COMPILED_QUERY,
              null,
              NORMALIZED_EXPRESSION,
              null);

      // When
      map.put(key1, "context1");

      // Then
      assertThat(map.get(key2)).isEqualTo("context1"); // 相同值可以检索
      assertThat(map).containsKey(key1);
      assertThat(map).containsKey(key2);
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理空字符串字段")
    void shouldHandleEmptyStringFields() {
      // When
      ExecutionContext context =
          new ExecutionContext(
              TASK_ID, RUN_ID, PLAN_ID, SLICE_ID, SCHEDULE_INSTANCE_ID, "", "", null, "", "", null,
              "", null);

      // Then
      assertThat(context.provenanceCode()).isEmpty();
      assertThat(context.operationCode()).isEmpty();
      assertThat(context.exprHash()).isEmpty();
      assertThat(context.compiledQuery()).isEmpty();
      assertThat(context.normalizedExpression()).isEmpty();
    }

    @Test
    @DisplayName("应该处理非常长的字符串字段")
    void shouldHandleVeryLongStringFields() {
      // Given
      String longString = "x".repeat(10000);

      // When
      ExecutionContext context =
          new ExecutionContext(
              TASK_ID,
              RUN_ID,
              PLAN_ID,
              SLICE_ID,
              SCHEDULE_INSTANCE_ID,
              longString,
              longString,
              null,
              longString,
              longString,
              null,
              longString,
              null);

      // Then
      assertThat(context.provenanceCode()).hasSize(10000);
      assertThat(context.operationCode()).hasSize(10000);
      assertThat(context.exprHash()).hasSize(10000);
      assertThat(context.compiledQuery()).hasSize(10000);
      assertThat(context.normalizedExpression()).hasSize(10000);
    }

    @Test
    @DisplayName("应该处理复杂的 JsonNode")
    void shouldHandleComplexJsonNode() {
      // Given
      ObjectNode complexNode = OBJECT_MAPPER.createObjectNode();
      complexNode.put("param1", "value1");
      complexNode.put("param2", 42);
      complexNode.set("nested", OBJECT_MAPPER.createObjectNode().put("key", "value"));

      // When
      ExecutionContext context =
          new ExecutionContext(
              TASK_ID,
              RUN_ID,
              PLAN_ID,
              SLICE_ID,
              SCHEDULE_INSTANCE_ID,
              PROVENANCE_CODE,
              OPERATION_CODE,
              null,
              EXPR_HASH,
              COMPILED_QUERY,
              complexNode,
              NORMALIZED_EXPRESSION,
              null);

      // Then
      assertThat(context.compiledParams()).isEqualTo(complexNode);
      assertThat(context.compiledParams().has("param1")).isTrue();
      assertThat(context.compiledParams().has("nested")).isTrue();
    }

    @Test
    @DisplayName("应该处理最大长整型值")
    void shouldHandleMaxLongValues() {
      // When
      ExecutionContext context =
          new ExecutionContext(
              Long.MAX_VALUE,
              Long.MAX_VALUE,
              Long.MAX_VALUE,
              Long.MAX_VALUE,
              Long.MAX_VALUE,
              PROVENANCE_CODE,
              OPERATION_CODE,
              null,
              EXPR_HASH,
              COMPILED_QUERY,
              null,
              NORMALIZED_EXPRESSION,
              null);

      // Then
      assertThat(context.taskId()).isEqualTo(Long.MAX_VALUE);
      assertThat(context.runId()).isEqualTo(Long.MAX_VALUE);
      assertThat(context.planId()).isEqualTo(Long.MAX_VALUE);
      assertThat(context.sliceId()).isEqualTo(Long.MAX_VALUE);
      assertThat(context.scheduleInstanceId()).isEqualTo(Long.MAX_VALUE);
    }
  }

  // ============ 辅助方法 ============

  private ProvenanceConfigSnapshot createMinimalConfigSnapshot() {
    ProvenanceConfigSnapshot.ProvenanceInfo provenanceInfo =
        new ProvenanceConfigSnapshot.ProvenanceInfo(
            1L,
            "pubmed",
            "PubMed",
            "https://eutils.ncbi.nlm.nih.gov",
            "UTC",
            "https://www.ncbi.nlm.nih.gov/books/NBK25501/",
            true,
            "ACTIVE");

    return new ProvenanceConfigSnapshot(provenanceInfo, null, null, null, null, null, null);
  }

  private JsonNode createMinimalJsonNode() {
    ObjectNode node = OBJECT_MAPPER.createObjectNode();
    node.put("key", "value");
    return node;
  }
}
