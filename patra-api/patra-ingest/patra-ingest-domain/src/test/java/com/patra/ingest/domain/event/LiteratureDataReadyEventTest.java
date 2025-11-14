package com.patra.ingest.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.enums.ProvenanceCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link LiteratureDataReadyEvent} 的单元测试。
 *
 * <p>测试范围:
 *
 * <ul>
 *   <li>字段访问器 - 验证所有字段的 getter 方法
 *   <li>Builder 构建 - 验证 Lombok @Builder 功能
 *   <li>Record 语义 - equals/hashCode/toString 行为
 *   <li>不可变性 - Record 的不可变特性
 *   <li>边界条件 - 空集合、null 值、边界数值
 * </ul>
 */
@DisplayName("LiteratureDataReadyEvent 单元测试")
class LiteratureDataReadyEventTest {

  // ==================== 测试数据常量 ====================

  private static final Long TASK_ID = 1001L;
  private static final Long RUN_ID = 2001L;
  private static final ProvenanceCode PROVENANCE_CODE = ProvenanceCode.PUBMED;
  private static final List<String> STORAGE_KEYS =
      List.of("s3://bucket/literature/batch-1.json", "s3://bucket/literature/batch-2.json");
  private static final Integer TOTAL_LITERATURE_COUNT = 500;
  private static final Integer SUCCESS_BATCH_COUNT = 2;
  private static final Integer FAILED_BATCH_COUNT = 0;
  private static final Long TIMESTAMP = System.currentTimeMillis();

  // ==================== 辅助方法 ====================

  /** 创建默认的测试事件。 */
  private LiteratureDataReadyEvent createDefaultEvent() {
    return LiteratureDataReadyEvent.builder()
        .taskId(TASK_ID)
        .runId(RUN_ID)
        .provenanceCode(PROVENANCE_CODE)
        .storageKeys(STORAGE_KEYS)
        .totalLiteratureCount(TOTAL_LITERATURE_COUNT)
        .successBatchCount(SUCCESS_BATCH_COUNT)
        .failedBatchCount(FAILED_BATCH_COUNT)
        .timestamp(TIMESTAMP)
        .build();
  }

  // ==================== 字段访问器测试 ====================

  @Nested
  @DisplayName("字段访问器测试")
  class FieldAccessorTests {

    @Test
    @DisplayName("应该正确返回 taskId")
    void shouldReturnTaskId() {
      // Given
      var event = createDefaultEvent();

      // When
      var result = event.taskId();

      // Then
      assertThat(result).isEqualTo(TASK_ID);
    }

    @Test
    @DisplayName("应该正确返回 runId")
    void shouldReturnRunId() {
      // Given
      var event = createDefaultEvent();

      // When
      var result = event.runId();

      // Then
      assertThat(result).isEqualTo(RUN_ID);
    }

    @Test
    @DisplayName("应该正确返回 provenanceCode")
    void shouldReturnProvenanceCode() {
      // Given
      var event = createDefaultEvent();

      // When
      var result = event.provenanceCode();

      // Then
      assertThat(result).isEqualTo(PROVENANCE_CODE);
    }

    @Test
    @DisplayName("应该正确返回 storageKeys")
    void shouldReturnStorageKeys() {
      // Given
      var event = createDefaultEvent();

      // When
      var result = event.storageKeys();

      // Then
      assertThat(result)
          .isNotNull()
          .hasSize(2)
          .containsExactly(
              "s3://bucket/literature/batch-1.json", "s3://bucket/literature/batch-2.json");
    }

    @Test
    @DisplayName("应该正确返回 totalLiteratureCount")
    void shouldReturnTotalLiteratureCount() {
      // Given
      var event = createDefaultEvent();

      // When
      var result = event.totalLiteratureCount();

      // Then
      assertThat(result).isEqualTo(TOTAL_LITERATURE_COUNT);
    }

    @Test
    @DisplayName("应该正确返回 successBatchCount")
    void shouldReturnSuccessBatchCount() {
      // Given
      var event = createDefaultEvent();

      // When
      var result = event.successBatchCount();

      // Then
      assertThat(result).isEqualTo(SUCCESS_BATCH_COUNT);
    }

    @Test
    @DisplayName("应该正确返回 failedBatchCount")
    void shouldReturnFailedBatchCount() {
      // Given
      var event = createDefaultEvent();

      // When
      var result = event.failedBatchCount();

      // Then
      assertThat(result).isEqualTo(FAILED_BATCH_COUNT);
    }

    @Test
    @DisplayName("应该正确返回 timestamp")
    void shouldReturnTimestamp() {
      // Given
      var event = createDefaultEvent();

      // When
      var result = event.timestamp();

      // Then
      assertThat(result).isEqualTo(TIMESTAMP);
    }
  }

  // ==================== Builder 构建测试 ====================

  @Nested
  @DisplayName("Builder 构建测试")
  class BuilderTests {

    @Test
    @DisplayName("应该通过 Builder 成功构建完整事件")
    void shouldBuildCompleteEventWithBuilder() {
      // When
      var event =
          LiteratureDataReadyEvent.builder()
              .taskId(TASK_ID)
              .runId(RUN_ID)
              .provenanceCode(PROVENANCE_CODE)
              .storageKeys(STORAGE_KEYS)
              .totalLiteratureCount(TOTAL_LITERATURE_COUNT)
              .successBatchCount(SUCCESS_BATCH_COUNT)
              .failedBatchCount(FAILED_BATCH_COUNT)
              .timestamp(TIMESTAMP)
              .build();

      // Then
      assertThat(event).isNotNull();
      assertThat(event.taskId()).isEqualTo(TASK_ID);
      assertThat(event.runId()).isEqualTo(RUN_ID);
      assertThat(event.provenanceCode()).isEqualTo(PROVENANCE_CODE);
      assertThat(event.storageKeys()).isEqualTo(STORAGE_KEYS);
      assertThat(event.totalLiteratureCount()).isEqualTo(TOTAL_LITERATURE_COUNT);
      assertThat(event.successBatchCount()).isEqualTo(SUCCESS_BATCH_COUNT);
      assertThat(event.failedBatchCount()).isEqualTo(FAILED_BATCH_COUNT);
      assertThat(event.timestamp()).isEqualTo(TIMESTAMP);
    }

    @Test
    @DisplayName("应该支持部分字段为 null")
    void shouldSupportNullFields() {
      // When
      var event =
          LiteratureDataReadyEvent.builder()
              .taskId(TASK_ID)
              .runId(RUN_ID)
              .provenanceCode(null) // null 值
              .storageKeys(null) // null 值
              .totalLiteratureCount(null)
              .successBatchCount(null)
              .failedBatchCount(null)
              .timestamp(TIMESTAMP)
              .build();

      // Then
      assertThat(event).isNotNull();
      assertThat(event.provenanceCode()).isNull();
      assertThat(event.storageKeys()).isNull();
      assertThat(event.totalLiteratureCount()).isNull();
      assertThat(event.successBatchCount()).isNull();
      assertThat(event.failedBatchCount()).isNull();
    }

    @Test
    @DisplayName("应该支持空 storageKeys 列表")
    void shouldSupportEmptyStorageKeys() {
      // When
      var event =
          LiteratureDataReadyEvent.builder()
              .taskId(TASK_ID)
              .runId(RUN_ID)
              .provenanceCode(PROVENANCE_CODE)
              .storageKeys(List.of()) // 空列表
              .totalLiteratureCount(0)
              .successBatchCount(0)
              .failedBatchCount(0)
              .timestamp(TIMESTAMP)
              .build();

      // Then
      assertThat(event.storageKeys()).isNotNull().isEmpty();
      assertThat(event.totalLiteratureCount()).isZero();
      assertThat(event.successBatchCount()).isZero();
      assertThat(event.failedBatchCount()).isZero();
    }
  }

  // ==================== Record equals 测试 ====================

  @Nested
  @DisplayName("Record equals 语义测试")
  class EqualsTests {

    @Test
    @DisplayName("相同字段值的两个事件应该相等")
    void shouldBeEqualForSameFieldValues() {
      // Given
      var event1 = createDefaultEvent();
      var event2 =
          LiteratureDataReadyEvent.builder()
              .taskId(TASK_ID)
              .runId(RUN_ID)
              .provenanceCode(PROVENANCE_CODE)
              .storageKeys(STORAGE_KEYS)
              .totalLiteratureCount(TOTAL_LITERATURE_COUNT)
              .successBatchCount(SUCCESS_BATCH_COUNT)
              .failedBatchCount(FAILED_BATCH_COUNT)
              .timestamp(TIMESTAMP)
              .build();

      // When & Then
      assertThat(event1).isEqualTo(event2);
      assertThat(event2).isEqualTo(event1);
    }

    @Test
    @DisplayName("同一实例应该等于自身")
    void shouldBeEqualToItself() {
      // Given
      var event = createDefaultEvent();

      // When & Then
      assertThat(event).isEqualTo(event);
    }

    @Test
    @DisplayName("不同 taskId 的事件应该不相等")
    void shouldNotBeEqualForDifferentTaskId() {
      // Given
      var event1 = createDefaultEvent();
      var event2 =
          LiteratureDataReadyEvent.builder()
              .taskId(9999L) // 不同的 taskId
              .runId(RUN_ID)
              .provenanceCode(PROVENANCE_CODE)
              .storageKeys(STORAGE_KEYS)
              .totalLiteratureCount(TOTAL_LITERATURE_COUNT)
              .successBatchCount(SUCCESS_BATCH_COUNT)
              .failedBatchCount(FAILED_BATCH_COUNT)
              .timestamp(TIMESTAMP)
              .build();

      // When & Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("不同 runId 的事件应该不相等")
    void shouldNotBeEqualForDifferentRunId() {
      // Given
      var event1 = createDefaultEvent();
      var event2 =
          LiteratureDataReadyEvent.builder()
              .taskId(TASK_ID)
              .runId(9999L) // 不同的 runId
              .provenanceCode(PROVENANCE_CODE)
              .storageKeys(STORAGE_KEYS)
              .totalLiteratureCount(TOTAL_LITERATURE_COUNT)
              .successBatchCount(SUCCESS_BATCH_COUNT)
              .failedBatchCount(FAILED_BATCH_COUNT)
              .timestamp(TIMESTAMP)
              .build();

      // When & Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("不同 provenanceCode 的事件应该不相等")
    void shouldNotBeEqualForDifferentProvenanceCode() {
      // Given
      var event1 = createDefaultEvent();
      var event2 =
          LiteratureDataReadyEvent.builder()
              .taskId(TASK_ID)
              .runId(RUN_ID)
              .provenanceCode(ProvenanceCode.EPMC) // 不同的 provenanceCode
              .storageKeys(STORAGE_KEYS)
              .totalLiteratureCount(TOTAL_LITERATURE_COUNT)
              .successBatchCount(SUCCESS_BATCH_COUNT)
              .failedBatchCount(FAILED_BATCH_COUNT)
              .timestamp(TIMESTAMP)
              .build();

      // When & Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("不同 storageKeys 的事件应该不相等")
    void shouldNotBeEqualForDifferentStorageKeys() {
      // Given
      var event1 = createDefaultEvent();
      var event2 =
          LiteratureDataReadyEvent.builder()
              .taskId(TASK_ID)
              .runId(RUN_ID)
              .provenanceCode(PROVENANCE_CODE)
              .storageKeys(List.of("s3://different/path.json")) // 不同的 storageKeys
              .totalLiteratureCount(TOTAL_LITERATURE_COUNT)
              .successBatchCount(SUCCESS_BATCH_COUNT)
              .failedBatchCount(FAILED_BATCH_COUNT)
              .timestamp(TIMESTAMP)
              .build();

      // When & Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("不同计数字段的事件应该不相等")
    void shouldNotBeEqualForDifferentCounts() {
      // Given
      var event1 = createDefaultEvent();
      var event2 =
          LiteratureDataReadyEvent.builder()
              .taskId(TASK_ID)
              .runId(RUN_ID)
              .provenanceCode(PROVENANCE_CODE)
              .storageKeys(STORAGE_KEYS)
              .totalLiteratureCount(999) // 不同的计数
              .successBatchCount(SUCCESS_BATCH_COUNT)
              .failedBatchCount(FAILED_BATCH_COUNT)
              .timestamp(TIMESTAMP)
              .build();

      // When & Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("不同 timestamp 的事件应该不相等")
    void shouldNotBeEqualForDifferentTimestamp() {
      // Given
      var event1 = createDefaultEvent();
      var event2 =
          LiteratureDataReadyEvent.builder()
              .taskId(TASK_ID)
              .runId(RUN_ID)
              .provenanceCode(PROVENANCE_CODE)
              .storageKeys(STORAGE_KEYS)
              .totalLiteratureCount(TOTAL_LITERATURE_COUNT)
              .successBatchCount(SUCCESS_BATCH_COUNT)
              .failedBatchCount(FAILED_BATCH_COUNT)
              .timestamp(System.currentTimeMillis() + 1000) // 不同的 timestamp
              .build();

      // When & Then
      assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("事件不应该等于 null")
    void shouldNotBeEqualToNull() {
      // Given
      var event = createDefaultEvent();

      // When & Then
      assertThat(event).isNotEqualTo(null);
    }

    @Test
    @DisplayName("事件不应该等于不同类型的对象")
    void shouldNotBeEqualToDifferentType() {
      // Given
      var event = createDefaultEvent();
      var differentTypeObject = "string";

      // When & Then
      assertThat(event).isNotEqualTo(differentTypeObject);
    }
  }

  // ==================== Record hashCode 测试 ====================

  @Nested
  @DisplayName("Record hashCode 语义测试")
  class HashCodeTests {

    @Test
    @DisplayName("相同字段值的两个事件应该有相同的 hashCode")
    void shouldHaveSameHashCodeForEqualObjects() {
      // Given
      var event1 = createDefaultEvent();
      var event2 =
          LiteratureDataReadyEvent.builder()
              .taskId(TASK_ID)
              .runId(RUN_ID)
              .provenanceCode(PROVENANCE_CODE)
              .storageKeys(STORAGE_KEYS)
              .totalLiteratureCount(TOTAL_LITERATURE_COUNT)
              .successBatchCount(SUCCESS_BATCH_COUNT)
              .failedBatchCount(FAILED_BATCH_COUNT)
              .timestamp(TIMESTAMP)
              .build();

      // When & Then
      assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }

    @Test
    @DisplayName("同一实例多次调用 hashCode 应该返回相同值")
    void shouldReturnConsistentHashCode() {
      // Given
      var event = createDefaultEvent();

      // When
      int hashCode1 = event.hashCode();
      int hashCode2 = event.hashCode();

      // Then
      assertThat(hashCode1).isEqualTo(hashCode2);
    }

    @Test
    @DisplayName("不同字段值的事件通常应该有不同的 hashCode")
    void shouldHaveDifferentHashCodeForDifferentObjects() {
      // Given
      var event1 = createDefaultEvent();
      var event2 =
          LiteratureDataReadyEvent.builder()
              .taskId(9999L) // 不同的 taskId
              .runId(RUN_ID)
              .provenanceCode(PROVENANCE_CODE)
              .storageKeys(STORAGE_KEYS)
              .totalLiteratureCount(TOTAL_LITERATURE_COUNT)
              .successBatchCount(SUCCESS_BATCH_COUNT)
              .failedBatchCount(FAILED_BATCH_COUNT)
              .timestamp(TIMESTAMP)
              .build();

      // When & Then
      // 注意: hashCode 不同不是严格保证,但通常应该不同
      assertThat(event1.hashCode()).isNotEqualTo(event2.hashCode());
    }
  }

  // ==================== Record toString 测试 ====================

  @Nested
  @DisplayName("Record toString 语义测试")
  class ToStringTests {

    @Test
    @DisplayName("toString 应该包含类名")
    void shouldContainClassName() {
      // Given
      var event = createDefaultEvent();

      // When
      var result = event.toString();

      // Then
      assertThat(result).contains("LiteratureDataReadyEvent");
    }

    @Test
    @DisplayName("toString 应该包含所有字段名和值")
    void shouldContainAllFieldsAndValues() {
      // Given
      var event = createDefaultEvent();

      // When
      var result = event.toString();

      // Then
      assertThat(result)
          .contains("taskId=" + TASK_ID)
          .contains("runId=" + RUN_ID)
          .contains("provenanceCode=" + PROVENANCE_CODE)
          .contains("storageKeys=")
          .contains("totalLiteratureCount=" + TOTAL_LITERATURE_COUNT)
          .contains("successBatchCount=" + SUCCESS_BATCH_COUNT)
          .contains("failedBatchCount=" + FAILED_BATCH_COUNT)
          .contains("timestamp=" + TIMESTAMP);
    }

    @Test
    @DisplayName("toString 应该处理 null 字段")
    void shouldHandleNullFields() {
      // Given
      var event =
          LiteratureDataReadyEvent.builder()
              .taskId(TASK_ID)
              .runId(RUN_ID)
              .provenanceCode(null)
              .storageKeys(null)
              .totalLiteratureCount(null)
              .successBatchCount(null)
              .failedBatchCount(null)
              .timestamp(TIMESTAMP)
              .build();

      // When
      var result = event.toString();

      // Then
      assertThat(result)
          .contains("provenanceCode=null")
          .contains("storageKeys=null")
          .contains("totalLiteratureCount=null");
    }

    @Test
    @DisplayName("toString 应该处理空 storageKeys 列表")
    void shouldHandleEmptyStorageKeys() {
      // Given
      var event =
          LiteratureDataReadyEvent.builder()
              .taskId(TASK_ID)
              .runId(RUN_ID)
              .provenanceCode(PROVENANCE_CODE)
              .storageKeys(List.of())
              .totalLiteratureCount(0)
              .successBatchCount(0)
              .failedBatchCount(0)
              .timestamp(TIMESTAMP)
              .build();

      // When
      var result = event.toString();

      // Then
      assertThat(result).contains("storageKeys=[]").contains("totalLiteratureCount=0");
    }
  }

  // ==================== 不可变性测试 ====================

  @Nested
  @DisplayName("不可变性测试")
  class ImmutabilityTests {

    @Test
    @DisplayName("返回的 storageKeys 应该是不可修改的（如果使用 List.of）")
    void shouldReturnUnmodifiableStorageKeys() {
      // Given
      var event = createDefaultEvent();

      // When & Then
      assertThat(event.storageKeys()).isInstanceOf(java.util.List.class);

      // 尝试修改应该抛出异常（如果使用 List.of 创建）
      org.junit.jupiter.api.Assertions.assertThrows(
          UnsupportedOperationException.class, () -> event.storageKeys().add("new-key"));
    }
  }

  // ==================== 边界条件测试 ====================

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该支持零值计数")
    void shouldSupportZeroCounts() {
      // When
      var event =
          LiteratureDataReadyEvent.builder()
              .taskId(TASK_ID)
              .runId(RUN_ID)
              .provenanceCode(PROVENANCE_CODE)
              .storageKeys(List.of())
              .totalLiteratureCount(0)
              .successBatchCount(0)
              .failedBatchCount(0)
              .timestamp(TIMESTAMP)
              .build();

      // Then
      assertThat(event.totalLiteratureCount()).isZero();
      assertThat(event.successBatchCount()).isZero();
      assertThat(event.failedBatchCount()).isZero();
    }

    @Test
    @DisplayName("应该支持大量 storageKeys")
    void shouldSupportLargeNumberOfStorageKeys() {
      // Given
      var largeKeyList =
          java.util.stream.IntStream.range(0, 1000)
              .mapToObj(i -> "s3://bucket/literature/batch-" + i + ".json")
              .toList();

      // When
      var event =
          LiteratureDataReadyEvent.builder()
              .taskId(TASK_ID)
              .runId(RUN_ID)
              .provenanceCode(PROVENANCE_CODE)
              .storageKeys(largeKeyList)
              .totalLiteratureCount(100000)
              .successBatchCount(1000)
              .failedBatchCount(0)
              .timestamp(TIMESTAMP)
              .build();

      // Then
      assertThat(event.storageKeys()).hasSize(1000);
      assertThat(event.totalLiteratureCount()).isEqualTo(100000);
    }

    @Test
    @DisplayName("应该支持失败批次大于成功批次的场景")
    void shouldSupportMoreFailedThanSuccessfulBatches() {
      // When
      var event =
          LiteratureDataReadyEvent.builder()
              .taskId(TASK_ID)
              .runId(RUN_ID)
              .provenanceCode(PROVENANCE_CODE)
              .storageKeys(List.of("s3://bucket/partial.json"))
              .totalLiteratureCount(100)
              .successBatchCount(1)
              .failedBatchCount(9)
              .timestamp(TIMESTAMP)
              .build();

      // Then
      assertThat(event.successBatchCount()).isEqualTo(1);
      assertThat(event.failedBatchCount()).isEqualTo(9);
    }

    @Test
    @DisplayName("应该支持最大 Long 值的 ID 和 timestamp")
    void shouldSupportMaxLongValues() {
      // When
      var event =
          LiteratureDataReadyEvent.builder()
              .taskId(Long.MAX_VALUE)
              .runId(Long.MAX_VALUE)
              .provenanceCode(PROVENANCE_CODE)
              .storageKeys(STORAGE_KEYS)
              .totalLiteratureCount(Integer.MAX_VALUE)
              .successBatchCount(Integer.MAX_VALUE)
              .failedBatchCount(Integer.MAX_VALUE)
              .timestamp(Long.MAX_VALUE)
              .build();

      // Then
      assertThat(event.taskId()).isEqualTo(Long.MAX_VALUE);
      assertThat(event.runId()).isEqualTo(Long.MAX_VALUE);
      assertThat(event.timestamp()).isEqualTo(Long.MAX_VALUE);
      assertThat(event.totalLiteratureCount()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("应该支持最小 Long 值的 ID")
    void shouldSupportMinLongValues() {
      // When
      var event =
          LiteratureDataReadyEvent.builder()
              .taskId(Long.MIN_VALUE)
              .runId(Long.MIN_VALUE)
              .provenanceCode(PROVENANCE_CODE)
              .storageKeys(STORAGE_KEYS)
              .totalLiteratureCount(0)
              .successBatchCount(0)
              .failedBatchCount(0)
              .timestamp(Long.MIN_VALUE)
              .build();

      // Then
      assertThat(event.taskId()).isEqualTo(Long.MIN_VALUE);
      assertThat(event.runId()).isEqualTo(Long.MIN_VALUE);
      assertThat(event.timestamp()).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    @DisplayName("应该支持所有 ProvenanceCode 枚举值")
    void shouldSupportAllProvenanceCodeEnumValues() {
      // Given & When: 测试几个常见的 ProvenanceCode 枚举值
      var pubmedEvent =
          LiteratureDataReadyEvent.builder()
              .taskId(TASK_ID)
              .runId(RUN_ID)
              .provenanceCode(ProvenanceCode.PUBMED)
              .storageKeys(STORAGE_KEYS)
              .totalLiteratureCount(TOTAL_LITERATURE_COUNT)
              .successBatchCount(SUCCESS_BATCH_COUNT)
              .failedBatchCount(FAILED_BATCH_COUNT)
              .timestamp(TIMESTAMP)
              .build();

      var epmcEvent =
          LiteratureDataReadyEvent.builder()
              .taskId(TASK_ID)
              .runId(RUN_ID)
              .provenanceCode(ProvenanceCode.EPMC)
              .storageKeys(STORAGE_KEYS)
              .totalLiteratureCount(TOTAL_LITERATURE_COUNT)
              .successBatchCount(SUCCESS_BATCH_COUNT)
              .failedBatchCount(FAILED_BATCH_COUNT)
              .timestamp(TIMESTAMP)
              .build();

      // Then
      assertThat(pubmedEvent.provenanceCode()).isEqualTo(ProvenanceCode.PUBMED);
      assertThat(epmcEvent.provenanceCode()).isEqualTo(ProvenanceCode.EPMC);
    }

    @Test
    @DisplayName("应该支持特殊字符的 storageKeys")
    void shouldSupportSpecialCharactersInStorageKeys() {
      // Given
      var specialKeys =
          List.of(
              "s3://bucket/文献/batch-1.json", // 中文
              "s3://bucket/literature/batch%201.json", // URL 编码
              "s3://bucket/literature/batch-1 (copy).json", // 括号和空格
              "s3://bucket/literature/batch-1~!@#$%^&*.json" // 特殊符号
              );

      // When
      var event =
          LiteratureDataReadyEvent.builder()
              .taskId(TASK_ID)
              .runId(RUN_ID)
              .provenanceCode(PROVENANCE_CODE)
              .storageKeys(specialKeys)
              .totalLiteratureCount(4)
              .successBatchCount(4)
              .failedBatchCount(0)
              .timestamp(TIMESTAMP)
              .build();

      // Then
      assertThat(event.storageKeys()).hasSize(4).containsExactlyElementsOf(specialKeys);
    }
  }

  // ==================== 业务场景测试 ====================

  @Nested
  @DisplayName("业务场景测试")
  class BusinessScenarioTests {

    @Test
    @DisplayName("应该表示全部成功的采集任务")
    void shouldRepresentFullySuccessfulTask() {
      // When
      var event =
          LiteratureDataReadyEvent.builder()
              .taskId(1001L)
              .runId(2001L)
              .provenanceCode(ProvenanceCode.PUBMED)
              .storageKeys(
                  List.of(
                      "s3://patra-literature/pubmed/2024/batch-1.json",
                      "s3://patra-literature/pubmed/2024/batch-2.json",
                      "s3://patra-literature/pubmed/2024/batch-3.json"))
              .totalLiteratureCount(1500)
              .successBatchCount(3)
              .failedBatchCount(0)
              .timestamp(System.currentTimeMillis())
              .build();

      // Then
      assertThat(event.failedBatchCount()).isZero();
      assertThat(event.successBatchCount()).isEqualTo(3);
      assertThat(event.storageKeys()).hasSize(3);
    }

    @Test
    @DisplayName("应该表示部分失败的采集任务")
    void shouldRepresentPartiallyFailedTask() {
      // When
      var event =
          LiteratureDataReadyEvent.builder()
              .taskId(1002L)
              .runId(2002L)
              .provenanceCode(ProvenanceCode.EPMC)
              .storageKeys(
                  List.of(
                      "s3://patra-literature/epmc/2024/batch-1.json",
                      "s3://patra-literature/epmc/2024/batch-3.json"))
              .totalLiteratureCount(800)
              .successBatchCount(2)
              .failedBatchCount(1)
              .timestamp(System.currentTimeMillis())
              .build();

      // Then
      assertThat(event.successBatchCount()).isEqualTo(2);
      assertThat(event.failedBatchCount()).isEqualTo(1);
      assertThat(event.storageKeys()).hasSize(2); // 只包含成功批次的存储键
    }

    @Test
    @DisplayName("应该表示完全失败的采集任务")
    void shouldRepresentCompletelyFailedTask() {
      // When
      var event =
          LiteratureDataReadyEvent.builder()
              .taskId(1003L)
              .runId(2003L)
              .provenanceCode(ProvenanceCode.BIORXIV)
              .storageKeys(List.of()) // 无成功批次
              .totalLiteratureCount(0)
              .successBatchCount(0)
              .failedBatchCount(5)
              .timestamp(System.currentTimeMillis())
              .build();

      // Then
      assertThat(event.successBatchCount()).isZero();
      assertThat(event.failedBatchCount()).isEqualTo(5);
      assertThat(event.storageKeys()).isEmpty();
      assertThat(event.totalLiteratureCount()).isZero();
    }

    @Test
    @DisplayName("应该支持多数据源事件的区分")
    void shouldDistinguishBetweenDifferentProvenances() {
      // Given
      var pubmedEvent =
          LiteratureDataReadyEvent.builder()
              .taskId(1001L)
              .runId(2001L)
              .provenanceCode(ProvenanceCode.PUBMED)
              .storageKeys(List.of("s3://bucket/pubmed.json"))
              .totalLiteratureCount(1000)
              .successBatchCount(1)
              .failedBatchCount(0)
              .timestamp(TIMESTAMP)
              .build();

      var epmcEvent =
          LiteratureDataReadyEvent.builder()
              .taskId(1002L)
              .runId(2002L)
              .provenanceCode(ProvenanceCode.EPMC)
              .storageKeys(List.of("s3://bucket/epmc.json"))
              .totalLiteratureCount(800)
              .successBatchCount(1)
              .failedBatchCount(0)
              .timestamp(TIMESTAMP)
              .build();

      // Then
      assertThat(pubmedEvent.provenanceCode()).isEqualTo(ProvenanceCode.PUBMED);
      assertThat(epmcEvent.provenanceCode()).isEqualTo(ProvenanceCode.EPMC);
      assertThat(pubmedEvent).isNotEqualTo(epmcEvent);
    }
  }
}
