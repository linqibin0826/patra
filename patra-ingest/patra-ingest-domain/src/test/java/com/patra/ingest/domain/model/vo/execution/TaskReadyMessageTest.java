package com.patra.ingest.domain.model.vo.execution;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.enums.ProvenanceCode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TaskReadyMessage 值对象单元测试")
class TaskReadyMessageTest {

  private static final Instant NOW = Instant.parse("2025-01-05T10:00:00Z");
  private static final Instant SCHEDULED_AT = Instant.parse("2025-01-05T09:00:00Z");
  private static final Instant TRIGGERED_AT = Instant.parse("2025-01-05T09:30:00Z");
  private static final Instant OCCURRED_AT = Instant.parse("2025-01-05T09:35:00Z");
  private static final Instant WINDOW_FROM = Instant.parse("2025-01-01T00:00:00Z");
  private static final Instant WINDOW_TO = Instant.parse("2025-01-31T23:59:59Z");

  @Nested
  @DisplayName("TaskReadyMessage 构造器测试")
  class TaskReadyMessageConstructorTests {

    @Test
    @DisplayName("应该使用有效的 payload 和 header 创建任务就绪消息")
    void shouldCreateValidTaskReadyMessage() {
      // Given
      TaskReadyMessage.Payload payload = createMinimalPayload();
      TaskReadyMessage.Header header = createMinimalHeader();

      // When
      TaskReadyMessage message = new TaskReadyMessage(payload, header);

      // Then
      assertThat(message.payload()).isEqualTo(payload);
      assertThat(message.header()).isEqualTo(header);
    }

    @Test
    @DisplayName("应该允许创建 payload 和 header 都为 null 的消息")
    void shouldAllowCreatingMessageWithNullFields() {
      // When
      TaskReadyMessage message = new TaskReadyMessage(null, null);

      // Then
      assertThat(message.payload()).isNull();
      assertThat(message.header()).isNull();
    }
  }

  @Nested
  @DisplayName("Payload 构造器测试")
  class PayloadConstructorTests {

    @Test
    @DisplayName("应该使用所有字段创建有效的 Payload")
    void shouldCreateValidPayloadWithAllFields() {
      // Given
      TaskReadyMessage.TaskParams taskParams = new TaskReadyMessage.TaskParams(5);
      TaskReadyMessage.PlanSliceParams planSliceParams =
          new TaskReadyMessage.PlanSliceParams("TIME");

      // When
      TaskReadyMessage.Payload payload =
          new TaskReadyMessage.Payload(
              1001L,
              3001L,
              4001L,
              ProvenanceCode.PUBMED,
              "FETCH_CITATIONS",
              "idempotent-key-123",
              10,
              SCHEDULED_AT,
              taskParams,
              "plan-key-abc",
              WINDOW_FROM,
              WINDOW_TO,
              "TIME",
              planSliceParams);

      // Then
      assertThat(payload.taskId()).isEqualTo(1001L);
      assertThat(payload.planId()).isEqualTo(3001L);
      assertThat(payload.sliceId()).isEqualTo(4001L);
      assertThat(payload.provenance()).isEqualTo(ProvenanceCode.PUBMED);
      assertThat(payload.operation()).isEqualTo("FETCH_CITATIONS");
      assertThat(payload.idempotentKey()).isEqualTo("idempotent-key-123");
      assertThat(payload.priority()).isEqualTo(10);
      assertThat(payload.scheduledAt()).isEqualTo(SCHEDULED_AT);
      assertThat(payload.params()).isEqualTo(taskParams);
      assertThat(payload.planKey()).isEqualTo("plan-key-abc");
      assertThat(payload.planWindowFrom()).isEqualTo(WINDOW_FROM);
      assertThat(payload.planWindowTo()).isEqualTo(WINDOW_TO);
      assertThat(payload.planSliceStrategy()).isEqualTo("TIME");
      assertThat(payload.planSliceParams()).isEqualTo(planSliceParams);
    }

    @Test
    @DisplayName("应该允许创建所有字段为 null 的 Payload")
    void shouldAllowCreatingPayloadWithAllNullFields() {
      // When
      TaskReadyMessage.Payload payload =
          new TaskReadyMessage.Payload(
              null, null, null, null, null, null, null, null, null, null, null, null, null, null);

      // Then
      assertThat(payload.taskId()).isNull();
      assertThat(payload.planId()).isNull();
      assertThat(payload.sliceId()).isNull();
      assertThat(payload.provenance()).isNull();
      assertThat(payload.operation()).isNull();
      assertThat(payload.idempotentKey()).isNull();
      assertThat(payload.priority()).isNull();
      assertThat(payload.scheduledAt()).isNull();
      assertThat(payload.params()).isNull();
      assertThat(payload.planKey()).isNull();
      assertThat(payload.planWindowFrom()).isNull();
      assertThat(payload.planWindowTo()).isNull();
      assertThat(payload.planSliceStrategy()).isNull();
      assertThat(payload.planSliceParams()).isNull();
    }

    @Test
    @DisplayName("应该允许创建部分字段为 null 的 Payload")
    void shouldAllowCreatingPayloadWithPartialNullFields() {
      // When
      TaskReadyMessage.Payload payload =
          new TaskReadyMessage.Payload(
              1001L,
              3001L,
              4001L,
              ProvenanceCode.PUBMED,
              "FETCH_CITATIONS",
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(payload.taskId()).isEqualTo(1001L);
      assertThat(payload.planId()).isEqualTo(3001L);
      assertThat(payload.sliceId()).isEqualTo(4001L);
      assertThat(payload.provenance()).isEqualTo(ProvenanceCode.PUBMED);
      assertThat(payload.operation()).isEqualTo("FETCH_CITATIONS");
      assertThat(payload.idempotentKey()).isNull();
      assertThat(payload.params()).isNull();
    }
  }

  @Nested
  @DisplayName("Header 构造器测试")
  class HeaderConstructorTests {

    @Test
    @DisplayName("应该使用所有字段创建有效的 Header")
    void shouldCreateValidHeaderWithAllFields() {
      // When
      TaskReadyMessage.Header header =
          new TaskReadyMessage.Header(
              5001L,
              "XXL-JOB",
              100L,
              200L,
              "CRON",
              TRIGGERED_AT,
              OCCURRED_AT,
              "plan-key-abc",
              "FETCH_CITATIONS",
              "https://api.example.com/endpoint");

      // Then
      assertThat(header.scheduleInstanceId()).isEqualTo(5001L);
      assertThat(header.scheduler()).isEqualTo("XXL-JOB");
      assertThat(header.schedulerJobId()).isEqualTo(100L);
      assertThat(header.schedulerLogId()).isEqualTo(200L);
      assertThat(header.triggerType()).isEqualTo("CRON");
      assertThat(header.triggeredAt()).isEqualTo(TRIGGERED_AT);
      assertThat(header.occurredAt()).isEqualTo(OCCURRED_AT);
      assertThat(header.planKey()).isEqualTo("plan-key-abc");
      assertThat(header.planOperation()).isEqualTo("FETCH_CITATIONS");
      assertThat(header.planEndpoint()).isEqualTo("https://api.example.com/endpoint");
    }

    @Test
    @DisplayName("应该允许创建所有字段为 null 的 Header")
    void shouldAllowCreatingHeaderWithAllNullFields() {
      // When
      TaskReadyMessage.Header header =
          new TaskReadyMessage.Header(null, null, null, null, null, null, null, null, null, null);

      // Then
      assertThat(header.scheduleInstanceId()).isNull();
      assertThat(header.scheduler()).isNull();
      assertThat(header.schedulerJobId()).isNull();
      assertThat(header.schedulerLogId()).isNull();
      assertThat(header.triggerType()).isNull();
      assertThat(header.triggeredAt()).isNull();
      assertThat(header.occurredAt()).isNull();
      assertThat(header.planKey()).isNull();
      assertThat(header.planOperation()).isNull();
      assertThat(header.planEndpoint()).isNull();
    }
  }

  @Nested
  @DisplayName("TaskParams 构造器测试")
  class TaskParamsConstructorTests {

    @Test
    @DisplayName("应该使用切片编号创建有效的 TaskParams")
    void shouldCreateValidTaskParamsWithSliceNo() {
      // When
      TaskReadyMessage.TaskParams params = new TaskReadyMessage.TaskParams(5);

      // Then
      assertThat(params.sliceNo()).isEqualTo(5);
    }

    @Test
    @DisplayName("应该允许创建切片编号为 null 的 TaskParams")
    void shouldAllowCreatingTaskParamsWithNullSliceNo() {
      // When
      TaskReadyMessage.TaskParams params = new TaskReadyMessage.TaskParams(null);

      // Then
      assertThat(params.sliceNo()).isNull();
    }

    @Test
    @DisplayName("应该允许创建切片编号为 0 的 TaskParams")
    void shouldAllowCreatingTaskParamsWithZeroSliceNo() {
      // When
      TaskReadyMessage.TaskParams params = new TaskReadyMessage.TaskParams(0);

      // Then
      assertThat(params.sliceNo()).isZero();
    }

    @Test
    @DisplayName("应该允许创建负数切片编号的 TaskParams")
    void shouldAllowCreatingTaskParamsWithNegativeSliceNo() {
      // When
      TaskReadyMessage.TaskParams params = new TaskReadyMessage.TaskParams(-1);

      // Then
      assertThat(params.sliceNo()).isEqualTo(-1);
    }
  }

  @Nested
  @DisplayName("PlanSliceParams 构造器测试")
  class PlanSliceParamsConstructorTests {

    @Test
    @DisplayName("应该使用策略创建有效的 PlanSliceParams")
    void shouldCreateValidPlanSliceParamsWithStrategy() {
      // When
      TaskReadyMessage.PlanSliceParams params = new TaskReadyMessage.PlanSliceParams("TIME");

      // Then
      assertThat(params.strategy()).isEqualTo("TIME");
    }

    @Test
    @DisplayName("应该允许创建策略为 null 的 PlanSliceParams")
    void shouldAllowCreatingPlanSliceParamsWithNullStrategy() {
      // When
      TaskReadyMessage.PlanSliceParams params = new TaskReadyMessage.PlanSliceParams(null);

      // Then
      assertThat(params.strategy()).isNull();
    }

    @Test
    @DisplayName("应该允许创建空字符串策略的 PlanSliceParams")
    void shouldAllowCreatingPlanSliceParamsWithEmptyStrategy() {
      // When
      TaskReadyMessage.PlanSliceParams params = new TaskReadyMessage.PlanSliceParams("");

      // Then
      assertThat(params.strategy()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemanticsTests {

    @Test
    @DisplayName("相同字段的 TaskReadyMessage 实例应该相等")
    void taskReadyMessageInstancesWithSameFieldsShouldBeEqual() {
      // Given
      TaskReadyMessage.Payload payload = createMinimalPayload();
      TaskReadyMessage.Header header = createMinimalHeader();

      TaskReadyMessage message1 = new TaskReadyMessage(payload, header);
      TaskReadyMessage message2 = new TaskReadyMessage(payload, header);

      // Then
      assertThat(message1).isEqualTo(message2);
      assertThat(message1.hashCode()).isEqualTo(message2.hashCode());
    }

    @Test
    @DisplayName("相同字段的 Payload 实例应该相等")
    void payloadInstancesWithSameFieldsShouldBeEqual() {
      // Given
      TaskReadyMessage.TaskParams params = new TaskReadyMessage.TaskParams(5);
      TaskReadyMessage.PlanSliceParams sliceParams = new TaskReadyMessage.PlanSliceParams("TIME");

      TaskReadyMessage.Payload payload1 =
          new TaskReadyMessage.Payload(
              1001L,
              3001L,
              4001L,
              ProvenanceCode.PUBMED,
              "FETCH",
              "key",
              10,
              NOW,
              params,
              "plan-key",
              WINDOW_FROM,
              WINDOW_TO,
              "TIME",
              sliceParams);

      TaskReadyMessage.Payload payload2 =
          new TaskReadyMessage.Payload(
              1001L,
              3001L,
              4001L,
              ProvenanceCode.PUBMED,
              "FETCH",
              "key",
              10,
              NOW,
              params,
              "plan-key",
              WINDOW_FROM,
              WINDOW_TO,
              "TIME",
              sliceParams);

      // Then
      assertThat(payload1).isEqualTo(payload2);
      assertThat(payload1.hashCode()).isEqualTo(payload2.hashCode());
    }

    @Test
    @DisplayName("相同字段的 Header 实例应该相等")
    void headerInstancesWithSameFieldsShouldBeEqual() {
      // Given
      TaskReadyMessage.Header header1 =
          new TaskReadyMessage.Header(
              5001L, "XXL", 100L, 200L, "CRON", NOW, NOW, "key", "op", "endpoint");

      TaskReadyMessage.Header header2 =
          new TaskReadyMessage.Header(
              5001L, "XXL", 100L, 200L, "CRON", NOW, NOW, "key", "op", "endpoint");

      // Then
      assertThat(header1).isEqualTo(header2);
      assertThat(header1.hashCode()).isEqualTo(header2.hashCode());
    }

    @Test
    @DisplayName("相同切片编号的 TaskParams 实例应该相等")
    void taskParamsInstancesWithSameSliceNoShouldBeEqual() {
      // Given
      TaskReadyMessage.TaskParams params1 = new TaskReadyMessage.TaskParams(5);
      TaskReadyMessage.TaskParams params2 = new TaskReadyMessage.TaskParams(5);

      // Then
      assertThat(params1).isEqualTo(params2);
      assertThat(params1.hashCode()).isEqualTo(params2.hashCode());
    }

    @Test
    @DisplayName("相同策略的 PlanSliceParams 实例应该相等")
    void planSliceParamsInstancesWithSameStrategyShouldBeEqual() {
      // Given
      TaskReadyMessage.PlanSliceParams params1 = new TaskReadyMessage.PlanSliceParams("TIME");
      TaskReadyMessage.PlanSliceParams params2 = new TaskReadyMessage.PlanSliceParams("TIME");

      // Then
      assertThat(params1).isEqualTo(params2);
      assertThat(params1.hashCode()).isEqualTo(params2.hashCode());
    }

    @Test
    @DisplayName("toString() 应该包含字段信息")
    void toStringShouldContainFieldInformation() {
      // Given
      TaskReadyMessage.Payload payload = createMinimalPayload();
      TaskReadyMessage.Header header = createMinimalHeader();
      TaskReadyMessage message = new TaskReadyMessage(payload, header);

      // When
      String result = message.toString();

      // Then
      assertThat(result).contains("TaskReadyMessage").contains("payload").contains("header");
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理优先级为最大值的 Payload")
    void shouldHandlePayloadWithMaxPriority() {
      // When
      TaskReadyMessage.Payload payload =
          new TaskReadyMessage.Payload(
              1L,
              2L,
              3L,
              ProvenanceCode.PUBMED,
              "op",
              "key",
              Integer.MAX_VALUE,
              NOW,
              null,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(payload.priority()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("应该处理优先级为负数的 Payload")
    void shouldHandlePayloadWithNegativePriority() {
      // When
      TaskReadyMessage.Payload payload =
          new TaskReadyMessage.Payload(
              1L,
              2L,
              3L,
              ProvenanceCode.PUBMED,
              "op",
              "key",
              -10,
              NOW,
              null,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(payload.priority()).isEqualTo(-10);
    }

    @Test
    @DisplayName("应该处理非常长的字符串字段")
    void shouldHandleVeryLongStringFields() {
      // Given
      String longString = "x".repeat(10000);

      // When
      TaskReadyMessage.Payload payload =
          new TaskReadyMessage.Payload(
              1L,
              2L,
              3L,
              ProvenanceCode.PUBMED,
              longString,
              longString,
              10,
              NOW,
              null,
              longString,
              null,
              null,
              longString,
              null);

      // Then
      assertThat(payload.provenance()).isEqualTo(ProvenanceCode.PUBMED);
      assertThat(payload.operation()).hasSize(10000);
      assertThat(payload.idempotentKey()).hasSize(10000);
      assertThat(payload.planKey()).hasSize(10000);
      assertThat(payload.planSliceStrategy()).hasSize(10000);
    }

    @Test
    @DisplayName("应该处理最大长整型 ID 值")
    void shouldHandleMaxLongIdValues() {
      // When
      TaskReadyMessage.Payload payload =
          new TaskReadyMessage.Payload(
              Long.MAX_VALUE,
              Long.MAX_VALUE,
              Long.MAX_VALUE,
              ProvenanceCode.PUBMED,
              "op",
              "key",
              10,
              NOW,
              null,
              null,
              null,
              null,
              null,
              null);

      TaskReadyMessage.Header header =
          new TaskReadyMessage.Header(
              Long.MAX_VALUE,
              "XXL",
              Long.MAX_VALUE,
              Long.MAX_VALUE,
              "CRON",
              NOW,
              NOW,
              null,
              null,
              null);

      // Then
      assertThat(payload.taskId()).isEqualTo(Long.MAX_VALUE);
      assertThat(payload.planId()).isEqualTo(Long.MAX_VALUE);
      assertThat(payload.sliceId()).isEqualTo(Long.MAX_VALUE);
      assertThat(header.scheduleInstanceId()).isEqualTo(Long.MAX_VALUE);
      assertThat(header.schedulerJobId()).isEqualTo(Long.MAX_VALUE);
      assertThat(header.schedulerLogId()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("应该处理最大整型切片编号")
    void shouldHandleMaxIntegerSliceNo() {
      // When
      TaskReadyMessage.TaskParams params = new TaskReadyMessage.TaskParams(Integer.MAX_VALUE);

      // Then
      assertThat(params.sliceNo()).isEqualTo(Integer.MAX_VALUE);
    }
  }

  @Nested
  @DisplayName("实际场景测试")
  class RealWorldScenarioTests {

    @Test
    @DisplayName("应该支持完整的任务就绪消息场景")
    void shouldSupportFullTaskReadyMessageScenario() {
      // Given
      TaskReadyMessage.TaskParams taskParams = new TaskReadyMessage.TaskParams(5);
      TaskReadyMessage.PlanSliceParams planSliceParams =
          new TaskReadyMessage.PlanSliceParams("TIME");

      TaskReadyMessage.Payload payload =
          new TaskReadyMessage.Payload(
              1001L,
              3001L,
              4001L,
              ProvenanceCode.PUBMED,
              "FETCH_CITATIONS",
              "pubmed:FETCH_CITATIONS:2025-01-05:slice-5",
              10,
              SCHEDULED_AT,
              taskParams,
              "pubmed:FETCH_CITATIONS:2025-01",
              WINDOW_FROM,
              WINDOW_TO,
              "TIME",
              planSliceParams);

      TaskReadyMessage.Header header =
          new TaskReadyMessage.Header(
              5001L,
              "XXL-JOB",
              100L,
              200L,
              "CRON",
              TRIGGERED_AT,
              OCCURRED_AT,
              "pubmed:FETCH_CITATIONS:2025-01",
              "FETCH_CITATIONS",
              "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi");

      // When
      TaskReadyMessage message = new TaskReadyMessage(payload, header);

      // Then
      assertThat(message.payload().taskId()).isEqualTo(1001L);
      assertThat(message.payload().provenance()).isEqualTo(ProvenanceCode.PUBMED);
      assertThat(message.payload().operation()).isEqualTo("FETCH_CITATIONS");
      assertThat(message.payload().params().sliceNo()).isEqualTo(5);
      assertThat(message.header().scheduler()).isEqualTo("XXL-JOB");
      assertThat(message.header().triggerType()).isEqualTo("CRON");
    }

    @Test
    @DisplayName("应该支持无切片的单一任务场景")
    void shouldSupportSingleTaskWithoutSlicing() {
      // Given
      TaskReadyMessage.PlanSliceParams planSliceParams =
          new TaskReadyMessage.PlanSliceParams("SINGLE");

      TaskReadyMessage.Payload payload =
          new TaskReadyMessage.Payload(
              1001L,
              3001L,
              4001L,
              ProvenanceCode.CROSSREF,
              "FETCH_WORKS",
              "crossref:FETCH_WORKS:2025-01-05",
              5,
              SCHEDULED_AT,
              null,
              "crossref:FETCH_WORKS:2025-01",
              null,
              null,
              "SINGLE",
              planSliceParams);

      TaskReadyMessage.Header header =
          new TaskReadyMessage.Header(
              5001L,
              "XXL-JOB",
              100L,
              200L,
              "MANUAL",
              TRIGGERED_AT,
              OCCURRED_AT,
              "crossref:FETCH_WORKS:2025-01",
              "FETCH_WORKS",
              "https://api.crossref.org/works");

      // When
      TaskReadyMessage message = new TaskReadyMessage(payload, header);

      // Then
      assertThat(message.payload().params()).isNull();
      assertThat(message.payload().planSliceStrategy()).isEqualTo("SINGLE");
      assertThat(message.header().triggerType()).isEqualTo("MANUAL");
    }
  }

  // ============ 辅助方法 ============

  private TaskReadyMessage.Payload createMinimalPayload() {
    return new TaskReadyMessage.Payload(
        1001L,
        3001L,
        4001L,
        ProvenanceCode.PUBMED,
        "FETCH",
        "key",
        10,
        NOW,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private TaskReadyMessage.Header createMinimalHeader() {
    return new TaskReadyMessage.Header(
        5001L, "XXL", 100L, 200L, "CRON", NOW, NOW, null, null, null);
  }
}
