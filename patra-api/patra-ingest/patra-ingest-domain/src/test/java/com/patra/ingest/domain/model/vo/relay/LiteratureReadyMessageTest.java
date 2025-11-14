package com.patra.ingest.domain.model.vo.relay;

import com.patra.common.enums.ProvenanceCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LiteratureReadyMessage 单元测试")
class LiteratureReadyMessageTest {

  @Nested
  @DisplayName("Payload Record 测试")
  class PayloadTest {

    @Nested
    @DisplayName("构造与访问器")
    class ConstructorAndAccessorTest {

      @Test
      @DisplayName("应该正确构造 Payload 并访问所有字段")
      void shouldConstructPayloadAndAccessAllFields() {
        // Given
        Long taskId = 123L;
        Long runId = 456L;
        ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
        List<String> storageKeys = List.of("key1", "key2", "key3");
        Integer totalLiteratureCount = 100;
        Integer successBatchCount = 8;
        Integer failedBatchCount = 2;
        Long timestamp = 1699999999999L;

        // When
        LiteratureReadyMessage.Payload payload =
            new LiteratureReadyMessage.Payload(
                taskId,
                runId,
                provenanceCode,
                storageKeys,
                totalLiteratureCount,
                successBatchCount,
                failedBatchCount,
                timestamp);

        // Then
        assertThat(payload.taskId()).isEqualTo(taskId);
        assertThat(payload.runId()).isEqualTo(runId);
        assertThat(payload.provenanceCode()).isEqualTo(provenanceCode);
        assertThat(payload.storageKeys())
            .containsExactly("key1", "key2", "key3")
            .hasSize(3);
        assertThat(payload.totalLiteratureCount()).isEqualTo(totalLiteratureCount);
        assertThat(payload.successBatchCount()).isEqualTo(successBatchCount);
        assertThat(payload.failedBatchCount()).isEqualTo(failedBatchCount);
        assertThat(payload.timestamp()).isEqualTo(timestamp);
      }

      @Test
      @DisplayName("应该支持 null 值字段")
      void shouldSupportNullFields() {
        // Given & When
        LiteratureReadyMessage.Payload payload =
            new LiteratureReadyMessage.Payload(null, null, null, null, null, null, null, null);

        // Then
        assertThat(payload.taskId()).isNull();
        assertThat(payload.runId()).isNull();
        assertThat(payload.provenanceCode()).isNull();
        assertThat(payload.storageKeys()).isNull();
        assertThat(payload.totalLiteratureCount()).isNull();
        assertThat(payload.successBatchCount()).isNull();
        assertThat(payload.failedBatchCount()).isNull();
        assertThat(payload.timestamp()).isNull();
      }

      @Test
      @DisplayName("应该支持空集合作为 storageKeys")
      void shouldSupportEmptyStorageKeys() {
        // Given & When
        LiteratureReadyMessage.Payload payload =
            new LiteratureReadyMessage.Payload(
                1L, 2L, ProvenanceCode.PUBMED, List.of(), 0, 0, 0, 1699999999999L);

        // Then
        assertThat(payload.storageKeys()).isEmpty();
      }
    }

    @Nested
    @DisplayName("equals 和 hashCode")
    class EqualsAndHashCodeTest {

      @Test
      @DisplayName("应该认为相同值的两个 Payload 实例相等")
      void shouldConsiderTwoPayloadsWithSameValuesEqual() {
        // Given
        LiteratureReadyMessage.Payload payload1 =
            new LiteratureReadyMessage.Payload(
                123L, 456L, ProvenanceCode.PUBMED, List.of("key1"), 100, 8, 2, 1699999999999L);
        LiteratureReadyMessage.Payload payload2 =
            new LiteratureReadyMessage.Payload(
                123L, 456L, ProvenanceCode.PUBMED, List.of("key1"), 100, 8, 2, 1699999999999L);

        // When & Then
        assertThat(payload1).isEqualTo(payload2);
        assertThat(payload1.hashCode()).isEqualTo(payload2.hashCode());
      }

      @Test
      @DisplayName("应该认为不同值的两个 Payload 实例不相等")
      void shouldConsiderTwoPayloadsWithDifferentValuesNotEqual() {
        // Given
        LiteratureReadyMessage.Payload payload1 =
            new LiteratureReadyMessage.Payload(
                123L, 456L, ProvenanceCode.PUBMED, List.of("key1"), 100, 8, 2, 1699999999999L);
        LiteratureReadyMessage.Payload payload2 =
            new LiteratureReadyMessage.Payload(
                999L, 456L, ProvenanceCode.PUBMED, List.of("key1"), 100, 8, 2, 1699999999999L);

        // When & Then
        assertThat(payload1).isNotEqualTo(payload2);
      }

      @Test
      @DisplayName("应该认为 Payload 与自身相等（自反性）")
      void shouldConsiderPayloadEqualToItself() {
        // Given
        LiteratureReadyMessage.Payload payload =
            new LiteratureReadyMessage.Payload(
                123L, 456L, ProvenanceCode.PUBMED, List.of("key1"), 100, 8, 2, 1699999999999L);

        // When & Then
        assertThat(payload).isEqualTo(payload);
      }

      @Test
      @DisplayName("应该认为 Payload 与 null 不相等")
      void shouldConsiderPayloadNotEqualToNull() {
        // Given
        LiteratureReadyMessage.Payload payload =
            new LiteratureReadyMessage.Payload(
                123L, 456L, ProvenanceCode.PUBMED, List.of("key1"), 100, 8, 2, 1699999999999L);

        // When & Then
        assertThat(payload).isNotEqualTo(null);
      }

      @Test
      @DisplayName("应该认为包含 null 字段的两个 Payload 相等")
      void shouldConsiderTwoPayloadsWithNullFieldsEqual() {
        // Given
        LiteratureReadyMessage.Payload payload1 =
            new LiteratureReadyMessage.Payload(null, null, null, null, null, null, null, null);
        LiteratureReadyMessage.Payload payload2 =
            new LiteratureReadyMessage.Payload(null, null, null, null, null, null, null, null);

        // When & Then
        assertThat(payload1).isEqualTo(payload2);
        assertThat(payload1.hashCode()).isEqualTo(payload2.hashCode());
      }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

      @Test
      @DisplayName("应该返回包含所有字段值的字符串")
      void shouldReturnStringContainingAllFieldValues() {
        // Given
        LiteratureReadyMessage.Payload payload =
            new LiteratureReadyMessage.Payload(
                123L, 456L, ProvenanceCode.PUBMED, List.of("key1", "key2"), 100, 8, 2, 1699999999999L);

        // When
        String result = payload.toString();

        // Then
        assertThat(result)
            .contains("taskId=123")
            .contains("runId=456")
            .contains("provenanceCode=PUBMED")
            .contains("storageKeys=[key1, key2]")
            .contains("totalLiteratureCount=100")
            .contains("successBatchCount=8")
            .contains("failedBatchCount=2")
            .contains("timestamp=1699999999999");
      }

      @Test
      @DisplayName("应该处理 null 字段的 toString")
      void shouldHandleNullFieldsInToString() {
        // Given
        LiteratureReadyMessage.Payload payload =
            new LiteratureReadyMessage.Payload(null, null, null, null, null, null, null, null);

        // When
        String result = payload.toString();

        // Then
        assertThat(result)
            .contains("taskId=null")
            .contains("runId=null")
            .contains("provenanceCode=null")
            .contains("storageKeys=null")
            .contains("totalLiteratureCount=null")
            .contains("successBatchCount=null")
            .contains("failedBatchCount=null")
            .contains("timestamp=null");
      }
    }

    @Nested
    @DisplayName("业务场景")
    class BusinessScenarioTest {

      @Test
      @DisplayName("应该表示成功采集场景（无失败批次）")
      void shouldRepresentSuccessfulHarvestScenario() {
        // Given & When
        LiteratureReadyMessage.Payload payload =
            new LiteratureReadyMessage.Payload(
                1L, 10L, ProvenanceCode.PUBMED, List.of("s3://key1", "s3://key2"), 200, 10, 0, 1699999999999L);

        // Then
        assertThat(payload.failedBatchCount()).isZero();
        assertThat(payload.successBatchCount()).isPositive();
        assertThat(payload.storageKeys()).isNotEmpty();
      }

      @Test
      @DisplayName("应该表示部分失败场景（有失败批次）")
      void shouldRepresentPartialFailureScenario() {
        // Given & When
        LiteratureReadyMessage.Payload payload =
            new LiteratureReadyMessage.Payload(
                1L, 10L, ProvenanceCode.EPMC, List.of("s3://key1"), 100, 8, 2, 1699999999999L);

        // Then
        assertThat(payload.failedBatchCount()).isPositive();
        assertThat(payload.successBatchCount()).isPositive();
        assertThat(payload.successBatchCount() + payload.failedBatchCount()).isEqualTo(10);
      }

      @Test
      @DisplayName("应该表示完全失败场景（无成功批次）")
      void shouldRepresentCompleteFailureScenario() {
        // Given & When
        LiteratureReadyMessage.Payload payload =
            new LiteratureReadyMessage.Payload(
                1L, 10L, ProvenanceCode.BIORXIV, List.of(), 0, 0, 10, 1699999999999L);

        // Then
        assertThat(payload.successBatchCount()).isZero();
        assertThat(payload.failedBatchCount()).isPositive();
        assertThat(payload.storageKeys()).isEmpty();
      }
    }
  }

  @Nested
  @DisplayName("Header Record 测试")
  class HeaderTest {

    @Nested
    @DisplayName("构造与访问器")
    class ConstructorAndAccessorTest {

      @Test
      @DisplayName("应该正确构造 Header 并访问所有字段")
      void shouldConstructHeaderAndAccessAllFields() {
        // Given
        ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
        Long taskId = 123L;
        Long runId = 456L;
        Integer storageKeyCount = 10;
        Long occurredAt = 1699999999999L;

        // When
        LiteratureReadyMessage.Header header =
            new LiteratureReadyMessage.Header(provenanceCode, taskId, runId, storageKeyCount, occurredAt);

        // Then
        assertThat(header.provenanceCode()).isEqualTo(provenanceCode);
        assertThat(header.taskId()).isEqualTo(taskId);
        assertThat(header.runId()).isEqualTo(runId);
        assertThat(header.storageKeyCount()).isEqualTo(storageKeyCount);
        assertThat(header.occurredAt()).isEqualTo(occurredAt);
      }

      @Test
      @DisplayName("应该支持 null 值字段")
      void shouldSupportNullFields() {
        // Given & When
        LiteratureReadyMessage.Header header =
            new LiteratureReadyMessage.Header(null, null, null, null, null);

        // Then
        assertThat(header.provenanceCode()).isNull();
        assertThat(header.taskId()).isNull();
        assertThat(header.runId()).isNull();
        assertThat(header.storageKeyCount()).isNull();
        assertThat(header.occurredAt()).isNull();
      }

      @Test
      @DisplayName("应该支持零值的 storageKeyCount")
      void shouldSupportZeroStorageKeyCount() {
        // Given & When
        LiteratureReadyMessage.Header header =
            new LiteratureReadyMessage.Header(ProvenanceCode.PUBMED, 1L, 2L, 0, 1699999999999L);

        // Then
        assertThat(header.storageKeyCount()).isZero();
      }
    }

    @Nested
    @DisplayName("equals 和 hashCode")
    class EqualsAndHashCodeTest {

      @Test
      @DisplayName("应该认为相同值的两个 Header 实例相等")
      void shouldConsiderTwoHeadersWithSameValuesEqual() {
        // Given
        LiteratureReadyMessage.Header header1 =
            new LiteratureReadyMessage.Header(ProvenanceCode.PUBMED, 123L, 456L, 10, 1699999999999L);
        LiteratureReadyMessage.Header header2 =
            new LiteratureReadyMessage.Header(ProvenanceCode.PUBMED, 123L, 456L, 10, 1699999999999L);

        // When & Then
        assertThat(header1).isEqualTo(header2);
        assertThat(header1.hashCode()).isEqualTo(header2.hashCode());
      }

      @Test
      @DisplayName("应该认为不同值的两个 Header 实例不相等")
      void shouldConsiderTwoHeadersWithDifferentValuesNotEqual() {
        // Given
        LiteratureReadyMessage.Header header1 =
            new LiteratureReadyMessage.Header(ProvenanceCode.PUBMED, 123L, 456L, 10, 1699999999999L);
        LiteratureReadyMessage.Header header2 =
            new LiteratureReadyMessage.Header(ProvenanceCode.EPMC, 123L, 456L, 10, 1699999999999L);

        // When & Then
        assertThat(header1).isNotEqualTo(header2);
      }

      @Test
      @DisplayName("应该认为 Header 与自身相等（自反性）")
      void shouldConsiderHeaderEqualToItself() {
        // Given
        LiteratureReadyMessage.Header header =
            new LiteratureReadyMessage.Header(ProvenanceCode.PUBMED, 123L, 456L, 10, 1699999999999L);

        // When & Then
        assertThat(header).isEqualTo(header);
      }

      @Test
      @DisplayName("应该认为 Header 与 null 不相等")
      void shouldConsiderHeaderNotEqualToNull() {
        // Given
        LiteratureReadyMessage.Header header =
            new LiteratureReadyMessage.Header(ProvenanceCode.PUBMED, 123L, 456L, 10, 1699999999999L);

        // When & Then
        assertThat(header).isNotEqualTo(null);
      }

      @Test
      @DisplayName("应该认为包含 null 字段的两个 Header 相等")
      void shouldConsiderTwoHeadersWithNullFieldsEqual() {
        // Given
        LiteratureReadyMessage.Header header1 =
            new LiteratureReadyMessage.Header(null, null, null, null, null);
        LiteratureReadyMessage.Header header2 =
            new LiteratureReadyMessage.Header(null, null, null, null, null);

        // When & Then
        assertThat(header1).isEqualTo(header2);
        assertThat(header1.hashCode()).isEqualTo(header2.hashCode());
      }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

      @Test
      @DisplayName("应该返回包含所有字段值的字符串")
      void shouldReturnStringContainingAllFieldValues() {
        // Given
        LiteratureReadyMessage.Header header =
            new LiteratureReadyMessage.Header(ProvenanceCode.PUBMED, 123L, 456L, 10, 1699999999999L);

        // When
        String result = header.toString();

        // Then
        assertThat(result)
            .contains("provenanceCode=PUBMED")
            .contains("taskId=123")
            .contains("runId=456")
            .contains("storageKeyCount=10")
            .contains("occurredAt=1699999999999");
      }

      @Test
      @DisplayName("应该处理 null 字段的 toString")
      void shouldHandleNullFieldsInToString() {
        // Given
        LiteratureReadyMessage.Header header =
            new LiteratureReadyMessage.Header(null, null, null, null, null);

        // When
        String result = header.toString();

        // Then
        assertThat(result)
            .contains("provenanceCode=null")
            .contains("taskId=null")
            .contains("runId=null")
            .contains("storageKeyCount=null")
            .contains("occurredAt=null");
      }
    }
  }

  @Nested
  @DisplayName("LiteratureReadyMessage 外层 Record 测试")
  class MessageTest {

    @Nested
    @DisplayName("构造与访问器")
    class ConstructorAndAccessorTest {

      @Test
      @DisplayName("应该正确构造 LiteratureReadyMessage 并访问 payload 和 header")
      void shouldConstructMessageAndAccessPayloadAndHeader() {
        // Given
        LiteratureReadyMessage.Payload payload =
            new LiteratureReadyMessage.Payload(
                123L, 456L, ProvenanceCode.PUBMED, List.of("key1"), 100, 8, 2, 1699999999999L);
        LiteratureReadyMessage.Header header =
            new LiteratureReadyMessage.Header(ProvenanceCode.PUBMED, 123L, 456L, 1, 1699999999999L);

        // When
        LiteratureReadyMessage message = new LiteratureReadyMessage(payload, header);

        // Then
        assertThat(message.payload()).isEqualTo(payload);
        assertThat(message.header()).isEqualTo(header);
      }

      @Test
      @DisplayName("应该支持 null 的 payload 和 header")
      void shouldSupportNullPayloadAndHeader() {
        // Given & When
        LiteratureReadyMessage message = new LiteratureReadyMessage(null, null);

        // Then
        assertThat(message.payload()).isNull();
        assertThat(message.header()).isNull();
      }
    }

    @Nested
    @DisplayName("equals 和 hashCode")
    class EqualsAndHashCodeTest {

      @Test
      @DisplayName("应该认为相同值的两个 Message 实例相等")
      void shouldConsiderTwoMessagesWithSameValuesEqual() {
        // Given
        LiteratureReadyMessage.Payload payload =
            new LiteratureReadyMessage.Payload(
                123L, 456L, ProvenanceCode.PUBMED, List.of("key1"), 100, 8, 2, 1699999999999L);
        LiteratureReadyMessage.Header header =
            new LiteratureReadyMessage.Header(ProvenanceCode.PUBMED, 123L, 456L, 1, 1699999999999L);

        LiteratureReadyMessage message1 = new LiteratureReadyMessage(payload, header);
        LiteratureReadyMessage message2 = new LiteratureReadyMessage(payload, header);

        // When & Then
        assertThat(message1).isEqualTo(message2);
        assertThat(message1.hashCode()).isEqualTo(message2.hashCode());
      }

      @Test
      @DisplayName("应该认为不同 payload 的两个 Message 实例不相等")
      void shouldConsiderTwoMessagesWithDifferentPayloadsNotEqual() {
        // Given
        LiteratureReadyMessage.Payload payload1 =
            new LiteratureReadyMessage.Payload(
                123L, 456L, ProvenanceCode.PUBMED, List.of("key1"), 100, 8, 2, 1699999999999L);
        LiteratureReadyMessage.Payload payload2 =
            new LiteratureReadyMessage.Payload(
                999L, 456L, ProvenanceCode.PUBMED, List.of("key1"), 100, 8, 2, 1699999999999L);
        LiteratureReadyMessage.Header header =
            new LiteratureReadyMessage.Header(ProvenanceCode.PUBMED, 123L, 456L, 1, 1699999999999L);

        LiteratureReadyMessage message1 = new LiteratureReadyMessage(payload1, header);
        LiteratureReadyMessage message2 = new LiteratureReadyMessage(payload2, header);

        // When & Then
        assertThat(message1).isNotEqualTo(message2);
      }

      @Test
      @DisplayName("应该认为不同 header 的两个 Message 实例不相等")
      void shouldConsiderTwoMessagesWithDifferentHeadersNotEqual() {
        // Given
        LiteratureReadyMessage.Payload payload =
            new LiteratureReadyMessage.Payload(
                123L, 456L, ProvenanceCode.PUBMED, List.of("key1"), 100, 8, 2, 1699999999999L);
        LiteratureReadyMessage.Header header1 =
            new LiteratureReadyMessage.Header(ProvenanceCode.PUBMED, 123L, 456L, 1, 1699999999999L);
        LiteratureReadyMessage.Header header2 =
            new LiteratureReadyMessage.Header(ProvenanceCode.EPMC, 123L, 456L, 1, 1699999999999L);

        LiteratureReadyMessage message1 = new LiteratureReadyMessage(payload, header1);
        LiteratureReadyMessage message2 = new LiteratureReadyMessage(payload, header2);

        // When & Then
        assertThat(message1).isNotEqualTo(message2);
      }

      @Test
      @DisplayName("应该认为 Message 与自身相等（自反性）")
      void shouldConsiderMessageEqualToItself() {
        // Given
        LiteratureReadyMessage.Payload payload =
            new LiteratureReadyMessage.Payload(
                123L, 456L, ProvenanceCode.PUBMED, List.of("key1"), 100, 8, 2, 1699999999999L);
        LiteratureReadyMessage.Header header =
            new LiteratureReadyMessage.Header(ProvenanceCode.PUBMED, 123L, 456L, 1, 1699999999999L);
        LiteratureReadyMessage message = new LiteratureReadyMessage(payload, header);

        // When & Then
        assertThat(message).isEqualTo(message);
      }

      @Test
      @DisplayName("应该认为 Message 与 null 不相等")
      void shouldConsiderMessageNotEqualToNull() {
        // Given
        LiteratureReadyMessage.Payload payload =
            new LiteratureReadyMessage.Payload(
                123L, 456L, ProvenanceCode.PUBMED, List.of("key1"), 100, 8, 2, 1699999999999L);
        LiteratureReadyMessage.Header header =
            new LiteratureReadyMessage.Header(ProvenanceCode.PUBMED, 123L, 456L, 1, 1699999999999L);
        LiteratureReadyMessage message = new LiteratureReadyMessage(payload, header);

        // When & Then
        assertThat(message).isNotEqualTo(null);
      }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

      @Test
      @DisplayName("应该返回包含 payload 和 header 的字符串")
      void shouldReturnStringContainingPayloadAndHeader() {
        // Given
        LiteratureReadyMessage.Payload payload =
            new LiteratureReadyMessage.Payload(
                123L, 456L, ProvenanceCode.PUBMED, List.of("key1"), 100, 8, 2, 1699999999999L);
        LiteratureReadyMessage.Header header =
            new LiteratureReadyMessage.Header(ProvenanceCode.PUBMED, 123L, 456L, 1, 1699999999999L);
        LiteratureReadyMessage message = new LiteratureReadyMessage(payload, header);

        // When
        String result = message.toString();

        // Then
        assertThat(result).contains("payload=").contains("header=");
      }

      @Test
      @DisplayName("应该处理 null payload 和 header 的 toString")
      void shouldHandleNullPayloadAndHeaderInToString() {
        // Given
        LiteratureReadyMessage message = new LiteratureReadyMessage(null, null);

        // When
        String result = message.toString();

        // Then
        assertThat(result).contains("payload=null").contains("header=null");
      }
    }

    @Nested
    @DisplayName("完整业务场景集成测试")
    class FullBusinessScenarioTest {

      @Test
      @DisplayName("应该表示完整的成功消息场景")
      void shouldRepresentCompleteSuccessMessageScenario() {
        // Given
        List<String> storageKeys = List.of("s3://bucket/run-123/batch-1.json", "s3://bucket/run-123/batch-2.json");
        LiteratureReadyMessage.Payload payload =
            new LiteratureReadyMessage.Payload(
                100L, 123L, ProvenanceCode.PUBMED, storageKeys, 200, 10, 0, 1699999999999L);
        LiteratureReadyMessage.Header header =
            new LiteratureReadyMessage.Header(ProvenanceCode.PUBMED, 100L, 123L, 2, 1699999999999L);

        // When
        LiteratureReadyMessage message = new LiteratureReadyMessage(payload, header);

        // Then
        assertThat(message.payload().provenanceCode())
            .isEqualTo(message.header().provenanceCode());
        assertThat(message.payload().taskId()).isEqualTo(message.header().taskId());
        assertThat(message.payload().runId()).isEqualTo(message.header().runId());
        assertThat(message.payload().storageKeys().size())
            .isEqualTo(message.header().storageKeyCount());
        assertThat(message.payload().failedBatchCount()).isZero();
      }

      @Test
      @DisplayName("应该表示部分失败消息场景")
      void shouldRepresentPartialFailureMessageScenario() {
        // Given
        List<String> storageKeys = List.of("s3://bucket/run-456/batch-1.json");
        LiteratureReadyMessage.Payload payload =
            new LiteratureReadyMessage.Payload(
                200L, 456L, ProvenanceCode.EPMC, storageKeys, 150, 8, 2, 1699999999999L);
        LiteratureReadyMessage.Header header =
            new LiteratureReadyMessage.Header(ProvenanceCode.EPMC, 200L, 456L, 1, 1699999999999L);

        // When
        LiteratureReadyMessage message = new LiteratureReadyMessage(payload, header);

        // Then
        assertThat(message.payload().failedBatchCount()).isPositive();
        assertThat(message.payload().successBatchCount()).isPositive();
        assertThat(message.payload().storageKeys()).isNotEmpty();
      }

      @Test
      @DisplayName("应该表示完全失败消息场景（无存储键）")
      void shouldRepresentCompleteFailureMessageScenario() {
        // Given
        LiteratureReadyMessage.Payload payload =
            new LiteratureReadyMessage.Payload(
                300L, 789L, ProvenanceCode.BIORXIV, List.of(), 0, 0, 10, 1699999999999L);
        LiteratureReadyMessage.Header header =
            new LiteratureReadyMessage.Header(ProvenanceCode.BIORXIV, 300L, 789L, 0, 1699999999999L);

        // When
        LiteratureReadyMessage message = new LiteratureReadyMessage(payload, header);

        // Then
        assertThat(message.payload().successBatchCount()).isZero();
        assertThat(message.payload().failedBatchCount()).isPositive();
        assertThat(message.payload().storageKeys()).isEmpty();
        assertThat(message.header().storageKeyCount()).isZero();
      }
    }
  }
}
