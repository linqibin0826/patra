package com.patra.ingest.domain.event;

import static org.assertj.core.api.Assertions.*;

import com.patra.common.domain.DomainEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * TaskCompletedEvent 单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>纯 Java 单元测试，不依赖 Spring 容器
 *   <li>不使用 Mockito，使用真实对象
 *   <li>遵循 Given-When-Then 结构
 *   <li>使用 AssertJ 流畅断言
 *   <li>测试 Record 语义（构造器、访问器、equals/hashCode/toString）
 *   <li>测试 DomainEvent 接口实现
 *   <li>测试工厂方法（of() 和 ofFailure()）
 * </ul>
 *
 * <p>测试范围：
 *
 * <ul>
 *   <li>✅ Record 构造器测试（紧凑构造器逻辑）
 *   <li>✅ 工厂方法测试（of(), ofFailure()）
 *   <li>✅ 访问器方法测试（所有字段）
 *   <li>✅ DomainEvent 接口实现测试
 *   <li>✅ 时间戳自动填充测试
 *   <li>✅ Record 语义测试（equals, hashCode, toString）
 *   <li>✅ 幂等性键测试（taskId）
 *   <li>✅ 成功/失败场景测试
 *   <li>✅ 边界情况测试
 *   <li>✅ 不可变性测试
 *   <li>✅ 序列化测试
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@DisplayName("TaskCompletedEvent 单元测试")
class TaskCompletedEventTest {

  // ========== Record 构造器测试 ==========

  @Nested
  @DisplayName("Record 构造器")
  class RecordConstructorTests {

    @Test
    @DisplayName("应该通过标准构造器成功创建事件")
    void shouldCreateEventViaStandardConstructor() {
      // Given
      Long taskId = 1001L;
      Long sliceId = 2001L;
      Long planId = 3001L;
      String status = "SUCCEEDED";
      String errorCode = null;
      String errorMessage = null;
      Instant finishedAt = Instant.parse("2024-01-15T10:30:00Z");
      Instant occurredAt = Instant.parse("2024-01-15T10:30:01Z");

      // When
      TaskCompletedEvent event =
          new TaskCompletedEvent(
              taskId, sliceId, planId, status, errorCode, errorMessage, finishedAt, occurredAt);

      // Then
      assertThat(event.taskId()).isEqualTo(taskId);
      assertThat(event.sliceId()).isEqualTo(sliceId);
      assertThat(event.planId()).isEqualTo(planId);
      assertThat(event.status()).isEqualTo(status);
      assertThat(event.errorCode()).isNull();
      assertThat(event.errorMessage()).isNull();
      assertThat(event.finishedAt()).isEqualTo(finishedAt);
      assertThat(event.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("应该自动填充 occurredAt 当传入 null")
    void shouldAutoFillOccurredAtWhenNull() {
      // Given
      Instant before = Instant.now();

      // When
      TaskCompletedEvent event =
          new TaskCompletedEvent(
              1001L,
              2001L,
              3001L,
              "SUCCEEDED",
              null,
              null,
              Instant.parse("2024-01-15T10:30:00Z"),
              null);

      // Then
      Instant after = Instant.now();
      assertThat(event.occurredAt())
          .isNotNull()
          .isAfterOrEqualTo(before)
          .isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("应该保留非 null 的 occurredAt")
    void shouldPreserveNonNullOccurredAt() {
      // Given
      Instant specificTime = Instant.parse("2024-01-15T10:30:01Z");

      // When
      TaskCompletedEvent event =
          new TaskCompletedEvent(
              1001L,
              2001L,
              3001L,
              "SUCCEEDED",
              null,
              null,
              Instant.parse("2024-01-15T10:30:00Z"),
              specificTime);

      // Then
      assertThat(event.occurredAt()).isEqualTo(specificTime);
    }

    @Test
    @DisplayName("应该支持带错误信息的失败事件")
    void shouldSupportFailureEventWithErrorDetails() {
      // Given
      Long taskId = 1001L;
      String errorCode = "FETCH_ERROR";
      String errorMessage = "API rate limit exceeded";

      // When
      TaskCompletedEvent event =
          new TaskCompletedEvent(
              taskId,
              2001L,
              3001L,
              "FAILED",
              errorCode,
              errorMessage,
              Instant.parse("2024-01-15T10:30:00Z"),
              Instant.now());

      // Then
      assertThat(event.taskId()).isEqualTo(taskId);
      assertThat(event.status()).isEqualTo("FAILED");
      assertThat(event.errorCode()).isEqualTo(errorCode);
      assertThat(event.errorMessage()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("应该允许 null 字段（除 occurredAt 外）")
    void shouldAllowNullFields() {
      // When
      TaskCompletedEvent event =
          new TaskCompletedEvent(null, null, null, null, null, null, null, null);

      // Then
      assertThat(event.taskId()).isNull();
      assertThat(event.sliceId()).isNull();
      assertThat(event.planId()).isNull();
      assertThat(event.status()).isNull();
      assertThat(event.errorCode()).isNull();
      assertThat(event.errorMessage()).isNull();
      assertThat(event.finishedAt()).isNull();
      assertThat(event.occurredAt()).isNotNull(); // 自动填充
    }
  }

  // ========== 工厂方法测试 ==========

  @Nested
  @DisplayName("工厂方法")
  class FactoryMethodTests {

    @Nested
    @DisplayName("of() - 成功任务完成")
    class OfMethodTests {

      @Test
      @DisplayName("应该创建成功完成事件且不包含错误信息")
      void shouldCreateSuccessEventWithoutErrorDetails() {
        // Given
        Long taskId = 1001L;
        Long sliceId = 2001L;
        Long planId = 3001L;
        String status = "SUCCEEDED";
        Instant finishedAt = Instant.parse("2024-01-15T10:30:00Z");
        Instant before = Instant.now();

        // When
        TaskCompletedEvent event = TaskCompletedEvent.of(taskId, sliceId, planId, status, finishedAt);

        // Then
        Instant after = Instant.now();
        assertThat(event.taskId()).isEqualTo(taskId);
        assertThat(event.sliceId()).isEqualTo(sliceId);
        assertThat(event.planId()).isEqualTo(planId);
        assertThat(event.status()).isEqualTo(status);
        assertThat(event.errorCode()).isNull();
        assertThat(event.errorMessage()).isNull();
        assertThat(event.finishedAt()).isEqualTo(finishedAt);
        assertThat(event.occurredAt())
            .isNotNull()
            .isAfterOrEqualTo(before)
            .isBeforeOrEqualTo(after);
      }

      @Test
      @DisplayName("应该支持 CURSOR_PENDING 状态")
      void shouldSupportCursorPendingStatus() {
        // Given
        String status = "CURSOR_PENDING";

        // When
        TaskCompletedEvent event =
            TaskCompletedEvent.of(
                1001L, 2001L, 3001L, status, Instant.parse("2024-01-15T10:30:00Z"));

        // Then
        assertThat(event.status()).isEqualTo(status);
        assertThat(event.errorCode()).isNull();
        assertThat(event.errorMessage()).isNull();
      }

      @Test
      @DisplayName("应该支持 PARTIAL 状态")
      void shouldSupportPartialStatus() {
        // Given
        String status = "PARTIAL";

        // When
        TaskCompletedEvent event =
            TaskCompletedEvent.of(
                1001L, 2001L, 3001L, status, Instant.parse("2024-01-15T10:30:00Z"));

        // Then
        assertThat(event.status()).isEqualTo(status);
        assertThat(event.errorCode()).isNull();
        assertThat(event.errorMessage()).isNull();
      }
    }

    @Nested
    @DisplayName("ofFailure() - 失败任务完成")
    class OfFailureMethodTests {

      @Test
      @DisplayName("应该创建失败事件且包含错误详情")
      void shouldCreateFailureEventWithErrorDetails() {
        // Given
        Long taskId = 1001L;
        Long sliceId = 2001L;
        Long planId = 3001L;
        String status = "FAILED";
        String errorCode = "FETCH_ERROR";
        String errorMessage = "API rate limit exceeded";
        Instant finishedAt = Instant.parse("2024-01-15T10:30:00Z");
        Instant before = Instant.now();

        // When
        TaskCompletedEvent event =
            TaskCompletedEvent.ofFailure(
                taskId, sliceId, planId, status, errorCode, errorMessage, finishedAt);

        // Then
        Instant after = Instant.now();
        assertThat(event.taskId()).isEqualTo(taskId);
        assertThat(event.sliceId()).isEqualTo(sliceId);
        assertThat(event.planId()).isEqualTo(planId);
        assertThat(event.status()).isEqualTo(status);
        assertThat(event.errorCode()).isEqualTo(errorCode);
        assertThat(event.errorMessage()).isEqualTo(errorMessage);
        assertThat(event.finishedAt()).isEqualTo(finishedAt);
        assertThat(event.occurredAt())
            .isNotNull()
            .isAfterOrEqualTo(before)
            .isBeforeOrEqualTo(after);
      }

      @Test
      @DisplayName("应该支持不同的错误码")
      void shouldSupportDifferentErrorCodes() {
        // Given
        String[] errorCodes = {"FETCH_ERROR", "PARSE_ERROR", "VALIDATION_ERROR", "TIMEOUT"};

        // When & Then
        for (String errorCode : errorCodes) {
          TaskCompletedEvent event =
              TaskCompletedEvent.ofFailure(
                  1001L,
                  2001L,
                  3001L,
                  "FAILED",
                  errorCode,
                  "Error message",
                  Instant.now());

          assertThat(event.errorCode()).isEqualTo(errorCode);
        }
      }

      @Test
      @DisplayName("应该支持多行错误消息")
      void shouldSupportMultiLineErrorMessage() {
        // Given
        String multiLineMessage =
            "Line 1: Connection failed\nLine 2: Retry exhausted\nLine 3: Cause: timeout";

        // When
        TaskCompletedEvent event =
            TaskCompletedEvent.ofFailure(
                1001L, 2001L, 3001L, "FAILED", "TIMEOUT", multiLineMessage, Instant.now());

        // Then
        assertThat(event.errorMessage()).isEqualTo(multiLineMessage);
      }

      @Test
      @DisplayName("应该支持空错误消息")
      void shouldSupportEmptyErrorMessage() {
        // When
        TaskCompletedEvent event =
            TaskCompletedEvent.ofFailure(
                1001L, 2001L, 3001L, "FAILED", "UNKNOWN", "", Instant.now());

        // Then
        assertThat(event.errorMessage()).isEmpty();
      }

      @Test
      @DisplayName("应该支持 null 错误码和消息")
      void shouldSupportNullErrorCodeAndMessage() {
        // When
        TaskCompletedEvent event =
            TaskCompletedEvent.ofFailure(1001L, 2001L, 3001L, "FAILED", null, null, Instant.now());

        // Then
        assertThat(event.errorCode()).isNull();
        assertThat(event.errorMessage()).isNull();
      }
    }

    @Nested
    @DisplayName("工厂方法对比")
    class FactoryMethodComparisonTests {

      @Test
      @DisplayName("of() 和 ofFailure() 应该生成不同的事件")
      void ofAndOfFailureShouldProduceDifferentEvents() {
        // Given
        Long taskId = 1001L;
        Long sliceId = 2001L;
        Long planId = 3001L;
        Instant finishedAt = Instant.parse("2024-01-15T10:30:00Z");

        // When
        TaskCompletedEvent successEvent =
            TaskCompletedEvent.of(taskId, sliceId, planId, "SUCCEEDED", finishedAt);
        TaskCompletedEvent failureEvent =
            TaskCompletedEvent.ofFailure(
                taskId, sliceId, planId, "FAILED", "ERROR", "Error message", finishedAt);

        // Then
        assertThat(successEvent.status()).isEqualTo("SUCCEEDED");
        assertThat(successEvent.errorCode()).isNull();
        assertThat(successEvent.errorMessage()).isNull();

        assertThat(failureEvent.status()).isEqualTo("FAILED");
        assertThat(failureEvent.errorCode()).isEqualTo("ERROR");
        assertThat(failureEvent.errorMessage()).isEqualTo("Error message");
      }
    }
  }

  // ========== 访问器方法测试 ==========

  @Nested
  @DisplayName("访问器方法")
  class AccessorTests {

    @Test
    @DisplayName("taskId() 应该返回正确的任务 ID")
    void taskIdShouldReturnCorrectValue() {
      // Given
      Long taskId = 1001L;
      TaskCompletedEvent event =
          TaskCompletedEvent.of(taskId, 2001L, 3001L, "SUCCEEDED", Instant.now());

      // When
      Long result = event.taskId();

      // Then
      assertThat(result).isEqualTo(taskId);
    }

    @Test
    @DisplayName("sliceId() 应该返回正确的切片 ID")
    void sliceIdShouldReturnCorrectValue() {
      // Given
      Long sliceId = 2001L;
      TaskCompletedEvent event =
          TaskCompletedEvent.of(1001L, sliceId, 3001L, "SUCCEEDED", Instant.now());

      // When
      Long result = event.sliceId();

      // Then
      assertThat(result).isEqualTo(sliceId);
    }

    @Test
    @DisplayName("planId() 应该返回正确的计划 ID")
    void planIdShouldReturnCorrectValue() {
      // Given
      Long planId = 3001L;
      TaskCompletedEvent event =
          TaskCompletedEvent.of(1001L, 2001L, planId, "SUCCEEDED", Instant.now());

      // When
      Long result = event.planId();

      // Then
      assertThat(result).isEqualTo(planId);
    }

    @Test
    @DisplayName("status() 应该返回正确的状态")
    void statusShouldReturnCorrectValue() {
      // Given
      String status = "SUCCEEDED";
      TaskCompletedEvent event =
          TaskCompletedEvent.of(1001L, 2001L, 3001L, status, Instant.now());

      // When
      String result = event.status();

      // Then
      assertThat(result).isEqualTo(status);
    }

    @Test
    @DisplayName("errorCode() 应该返回正确的错误码")
    void errorCodeShouldReturnCorrectValue() {
      // Given
      String errorCode = "FETCH_ERROR";
      TaskCompletedEvent event =
          TaskCompletedEvent.ofFailure(
              1001L, 2001L, 3001L, "FAILED", errorCode, "Error message", Instant.now());

      // When
      String result = event.errorCode();

      // Then
      assertThat(result).isEqualTo(errorCode);
    }

    @Test
    @DisplayName("errorMessage() 应该返回正确的错误消息")
    void errorMessageShouldReturnCorrectValue() {
      // Given
      String errorMessage = "API rate limit exceeded";
      TaskCompletedEvent event =
          TaskCompletedEvent.ofFailure(
              1001L, 2001L, 3001L, "FAILED", "FETCH_ERROR", errorMessage, Instant.now());

      // When
      String result = event.errorMessage();

      // Then
      assertThat(result).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("finishedAt() 应该返回正确的完成时间")
    void finishedAtShouldReturnCorrectValue() {
      // Given
      Instant finishedAt = Instant.parse("2024-01-15T10:30:00Z");
      TaskCompletedEvent event =
          TaskCompletedEvent.of(1001L, 2001L, 3001L, "SUCCEEDED", finishedAt);

      // When
      Instant result = event.finishedAt();

      // Then
      assertThat(result).isEqualTo(finishedAt);
    }

    @Test
    @DisplayName("occurredAt() 应该返回正确的发生时间")
    void occurredAtShouldReturnCorrectValue() {
      // Given
      Instant occurredAt = Instant.parse("2024-01-15T10:30:01Z");
      TaskCompletedEvent event =
          new TaskCompletedEvent(
              1001L, 2001L, 3001L, "SUCCEEDED", null, null, Instant.now(), occurredAt);

      // When
      Instant result = event.occurredAt();

      // Then
      assertThat(result).isEqualTo(occurredAt);
    }
  }

  // ========== DomainEvent 接口实现测试 ==========

  @Nested
  @DisplayName("DomainEvent 接口实现")
  class DomainEventInterfaceTests {

    @Test
    @DisplayName("应该实现 DomainEvent 接口")
    void shouldImplementDomainEventInterface() {
      // Given
      TaskCompletedEvent event =
          TaskCompletedEvent.of(1001L, 2001L, 3001L, "SUCCEEDED", Instant.now());

      // When & Then
      assertThat(event).isInstanceOf(DomainEvent.class);
    }

    @Test
    @DisplayName("occurredAt() 应该满足 DomainEvent 契约")
    void occurredAtShouldSatisfyDomainEventContract() {
      // Given
      Instant specificTime = Instant.parse("2024-01-15T10:30:01Z");
      TaskCompletedEvent event =
          new TaskCompletedEvent(
              1001L, 2001L, 3001L, "SUCCEEDED", null, null, Instant.now(), specificTime);

      // When
      Instant result = event.occurredAt();

      // Then
      assertThat(result).isNotNull().isEqualTo(specificTime);
    }

    @Test
    @DisplayName("应该支持序列化（DomainEvent 继承 Serializable）")
    void shouldSupportSerialization() throws Exception {
      // Given
      TaskCompletedEvent original =
          TaskCompletedEvent.ofFailure(
              1001L,
              2001L,
              3001L,
              "FAILED",
              "FETCH_ERROR",
              "API error",
              Instant.parse("2024-01-15T10:30:00Z"));

      // When: 序列化
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
        oos.writeObject(original);
      }

      // Then: 反序列化
      byte[] bytes = baos.toByteArray();
      ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
      TaskCompletedEvent deserialized;
      try (ObjectInputStream ois = new ObjectInputStream(bais)) {
        deserialized = (TaskCompletedEvent) ois.readObject();
      }

      assertThat(deserialized).isEqualTo(original);
      assertThat(deserialized.taskId()).isEqualTo(original.taskId());
      assertThat(deserialized.errorCode()).isEqualTo(original.errorCode());
      assertThat(deserialized.occurredAt()).isEqualTo(original.occurredAt());
    }
  }

  // ========== Record 语义测试 ==========

  @Nested
  @DisplayName("Record 语义")
  class RecordSemanticsTests {

    @Nested
    @DisplayName("equals() 测试")
    class EqualsTests {

      @Test
      @DisplayName("相同字段的事件应该相等")
      void eventsShouldBeEqualWithSameFields() {
        // Given
        Instant finishedAt = Instant.parse("2024-01-15T10:30:00Z");
        Instant occurredAt = Instant.parse("2024-01-15T10:30:01Z");

        TaskCompletedEvent event1 =
            new TaskCompletedEvent(
                1001L, 2001L, 3001L, "SUCCEEDED", null, null, finishedAt, occurredAt);
        TaskCompletedEvent event2 =
            new TaskCompletedEvent(
                1001L, 2001L, 3001L, "SUCCEEDED", null, null, finishedAt, occurredAt);

        // When & Then
        assertThat(event1).isEqualTo(event2);
      }

      @Test
      @DisplayName("不同 taskId 的事件应该不相等")
      void eventsShouldNotBeEqualWithDifferentTaskId() {
        // Given
        Instant timestamp = Instant.now();
        TaskCompletedEvent event1 =
            new TaskCompletedEvent(1001L, 2001L, 3001L, "SUCCEEDED", null, null, timestamp, timestamp);
        TaskCompletedEvent event2 =
            new TaskCompletedEvent(1002L, 2001L, 3001L, "SUCCEEDED", null, null, timestamp, timestamp);

        // When & Then
        assertThat(event1).isNotEqualTo(event2);
      }

      @Test
      @DisplayName("不同 status 的事件应该不相等")
      void eventsShouldNotBeEqualWithDifferentStatus() {
        // Given
        Instant timestamp = Instant.now();
        TaskCompletedEvent event1 =
            new TaskCompletedEvent(1001L, 2001L, 3001L, "SUCCEEDED", null, null, timestamp, timestamp);
        TaskCompletedEvent event2 =
            new TaskCompletedEvent(1001L, 2001L, 3001L, "FAILED", null, null, timestamp, timestamp);

        // When & Then
        assertThat(event1).isNotEqualTo(event2);
      }

      @Test
      @DisplayName("不同 errorCode 的事件应该不相等")
      void eventsShouldNotBeEqualWithDifferentErrorCode() {
        // Given
        Instant timestamp = Instant.now();
        TaskCompletedEvent event1 =
            new TaskCompletedEvent(
                1001L, 2001L, 3001L, "FAILED", "ERROR1", "Message", timestamp, timestamp);
        TaskCompletedEvent event2 =
            new TaskCompletedEvent(
                1001L, 2001L, 3001L, "FAILED", "ERROR2", "Message", timestamp, timestamp);

        // When & Then
        assertThat(event1).isNotEqualTo(event2);
      }

      @Test
      @DisplayName("不同 occurredAt 的事件应该不相等")
      void eventsShouldNotBeEqualWithDifferentOccurredAt() {
        // Given
        Instant finishedAt = Instant.parse("2024-01-15T10:30:00Z");
        Instant occurredAt1 = Instant.parse("2024-01-15T10:30:01Z");
        Instant occurredAt2 = Instant.parse("2024-01-15T10:30:02Z");

        TaskCompletedEvent event1 =
            new TaskCompletedEvent(
                1001L, 2001L, 3001L, "SUCCEEDED", null, null, finishedAt, occurredAt1);
        TaskCompletedEvent event2 =
            new TaskCompletedEvent(
                1001L, 2001L, 3001L, "SUCCEEDED", null, null, finishedAt, occurredAt2);

        // When & Then
        assertThat(event1).isNotEqualTo(event2);
      }

      @Test
      @DisplayName("应该满足自反性（x.equals(x) == true）")
      void equalsShouldBeReflexive() {
        // Given
        TaskCompletedEvent event =
            TaskCompletedEvent.of(1001L, 2001L, 3001L, "SUCCEEDED", Instant.now());

        // When & Then
        assertThat(event).isEqualTo(event);
      }

      @Test
      @DisplayName("应该满足对称性（x.equals(y) == y.equals(x)）")
      void equalsShouldBeSymmetric() {
        // Given
        Instant timestamp = Instant.now();
        TaskCompletedEvent event1 =
            new TaskCompletedEvent(1001L, 2001L, 3001L, "SUCCEEDED", null, null, timestamp, timestamp);
        TaskCompletedEvent event2 =
            new TaskCompletedEvent(1001L, 2001L, 3001L, "SUCCEEDED", null, null, timestamp, timestamp);

        // When & Then
        assertThat(event1).isEqualTo(event2);
        assertThat(event2).isEqualTo(event1);
      }

      @Test
      @DisplayName("应该满足传递性（x.equals(y) && y.equals(z) => x.equals(z)）")
      void equalsShouldBeTransitive() {
        // Given
        Instant timestamp = Instant.now();
        TaskCompletedEvent event1 =
            new TaskCompletedEvent(1001L, 2001L, 3001L, "SUCCEEDED", null, null, timestamp, timestamp);
        TaskCompletedEvent event2 =
            new TaskCompletedEvent(1001L, 2001L, 3001L, "SUCCEEDED", null, null, timestamp, timestamp);
        TaskCompletedEvent event3 =
            new TaskCompletedEvent(1001L, 2001L, 3001L, "SUCCEEDED", null, null, timestamp, timestamp);

        // When & Then
        assertThat(event1).isEqualTo(event2);
        assertThat(event2).isEqualTo(event3);
        assertThat(event1).isEqualTo(event3);
      }

      @Test
      @DisplayName("与 null 比较应该返回 false")
      void equalsShouldReturnFalseForNull() {
        // Given
        TaskCompletedEvent event =
            TaskCompletedEvent.of(1001L, 2001L, 3001L, "SUCCEEDED", Instant.now());

        // When & Then
        assertThat(event).isNotEqualTo(null);
      }

      @Test
      @DisplayName("与不同类型比较应该返回 false")
      void equalsShouldReturnFalseForDifferentType() {
        // Given
        TaskCompletedEvent event =
            TaskCompletedEvent.of(1001L, 2001L, 3001L, "SUCCEEDED", Instant.now());
        String differentType = "Not an event";

        // When & Then
        assertThat(event).isNotEqualTo(differentType);
      }
    }

    @Nested
    @DisplayName("hashCode() 测试")
    class HashCodeTests {

      @Test
      @DisplayName("相等的事件应该有相同的 hashCode")
      void equalEventsShouldHaveSameHashCode() {
        // Given
        Instant timestamp = Instant.now();
        TaskCompletedEvent event1 =
            new TaskCompletedEvent(1001L, 2001L, 3001L, "SUCCEEDED", null, null, timestamp, timestamp);
        TaskCompletedEvent event2 =
            new TaskCompletedEvent(1001L, 2001L, 3001L, "SUCCEEDED", null, null, timestamp, timestamp);

        // When & Then
        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
      }

      @Test
      @DisplayName("不相等的事件通常应该有不同的 hashCode")
      void unequalEventsShouldUsuallyHaveDifferentHashCode() {
        // Given
        TaskCompletedEvent event1 =
            TaskCompletedEvent.of(1001L, 2001L, 3001L, "SUCCEEDED", Instant.now());
        TaskCompletedEvent event2 =
            TaskCompletedEvent.of(1002L, 2001L, 3001L, "SUCCEEDED", Instant.now());

        // When & Then
        assertThat(event1.hashCode()).isNotEqualTo(event2.hashCode());
      }

      @Test
      @DisplayName("多次调用应该返回一致的 hashCode")
      void hashCodeShouldBeConsistent() {
        // Given
        TaskCompletedEvent event =
            TaskCompletedEvent.of(1001L, 2001L, 3001L, "SUCCEEDED", Instant.now());

        // When
        int hashCode1 = event.hashCode();
        int hashCode2 = event.hashCode();

        // Then
        assertThat(hashCode1).isEqualTo(hashCode2);
      }
    }

    @Nested
    @DisplayName("toString() 测试")
    class ToStringTests {

      @Test
      @DisplayName("toString() 应该包含类名和所有字段")
      void toStringShouldContainClassNameAndAllFields() {
        // Given
        TaskCompletedEvent event =
            TaskCompletedEvent.ofFailure(
                1001L,
                2001L,
                3001L,
                "FAILED",
                "FETCH_ERROR",
                "API error",
                Instant.parse("2024-01-15T10:30:00Z"));

        // When
        String result = event.toString();

        // Then
        assertThat(result)
            .contains("TaskCompletedEvent")
            .contains("taskId=1001")
            .contains("sliceId=2001")
            .contains("planId=3001")
            .contains("status=FAILED")
            .contains("errorCode=FETCH_ERROR")
            .contains("errorMessage=API error")
            .contains("finishedAt=2024-01-15T10:30:00Z");
      }

      @Test
      @DisplayName("toString() 应该处理 null 字段")
      void toStringShouldHandleNullFields() {
        // Given
        TaskCompletedEvent event =
            TaskCompletedEvent.of(1001L, 2001L, 3001L, "SUCCEEDED", Instant.now());

        // When
        String result = event.toString();

        // Then
        assertThat(result)
            .contains("TaskCompletedEvent")
            .contains("errorCode=null")
            .contains("errorMessage=null");
      }
    }
  }

  // ========== 幂等性键测试 ==========

  @Nested
  @DisplayName("幂等性键")
  class IdempotencyKeyTests {

    @Test
    @DisplayName("taskId 应该作为幂等性唯一键")
    void taskIdShouldServeAsIdempotencyKey() {
      // Given: 同一 taskId 的多个事件（例如：重复发布）
      Long taskId = 1001L;
      TaskCompletedEvent event1 =
          TaskCompletedEvent.of(taskId, 2001L, 3001L, "SUCCEEDED", Instant.now());
      TaskCompletedEvent event2 =
          TaskCompletedEvent.of(taskId, 2001L, 3001L, "SUCCEEDED", Instant.now());

      // When & Then: taskId 相同意味着应该去重
      assertThat(event1.taskId()).isEqualTo(event2.taskId()).isEqualTo(taskId);
    }

    @Test
    @DisplayName("不同 taskId 应该代表不同的事件")
    void differentTaskIdsShouldRepresentDifferentEvents() {
      // Given
      TaskCompletedEvent event1 =
          TaskCompletedEvent.of(1001L, 2001L, 3001L, "SUCCEEDED", Instant.now());
      TaskCompletedEvent event2 =
          TaskCompletedEvent.of(1002L, 2001L, 3001L, "SUCCEEDED", Instant.now());

      // When & Then
      assertThat(event1.taskId()).isNotEqualTo(event2.taskId());
    }

    @Test
    @DisplayName("应该能够基于 taskId 进行去重")
    void shouldEnableDeduplicationBasedOnTaskId() {
      // Given: 模拟事件处理器检查幂等性
      Long taskId = 1001L;
      TaskCompletedEvent event1 =
          TaskCompletedEvent.of(taskId, 2001L, 3001L, "SUCCEEDED", Instant.now());
      TaskCompletedEvent event2 =
          TaskCompletedEvent.of(taskId, 2002L, 3002L, "SUCCEEDED", Instant.now());

      // When & Then: 即使其他字段不同，taskId 相同则认为是重复事件
      assertThat(event1.taskId()).isEqualTo(event2.taskId());
      assertThat(event1).isNotEqualTo(event2); // Record equals 需要所有字段相等
    }
  }

  // ========== 事件链与业务场景测试 ==========

  @Nested
  @DisplayName("事件链与业务场景")
  class EventChainAndBusinessScenariosTests {

    @Test
    @DisplayName("任务成功完成应该触发聚合计算")
    void successfulTaskCompletionShouldTriggerAggregation() {
      // Given: 任务成功完成
      TaskCompletedEvent event =
          TaskCompletedEvent.of(
              1001L, 2001L, 3001L, "SUCCEEDED", Instant.parse("2024-01-15T10:30:00Z"));

      // Then: 事件包含必要信息供下游聚合
      assertThat(event.taskId()).isEqualTo(1001L);
      assertThat(event.sliceId()).isEqualTo(2001L);
      assertThat(event.planId()).isEqualTo(3001L);
      assertThat(event.status()).isEqualTo("SUCCEEDED");
      assertThat(event.errorCode()).isNull();
    }

    @Test
    @DisplayName("任务失败应该记录错误信息用于告警")
    void failedTaskShouldRecordErrorDetailsForAlerting() {
      // Given: 任务失败
      TaskCompletedEvent event =
          TaskCompletedEvent.ofFailure(
              1001L,
              2001L,
              3001L,
              "FAILED",
              "FETCH_ERROR",
              "Connection timeout after 30s",
              Instant.parse("2024-01-15T10:30:00Z"));

      // Then: 事件包含错误详情供监控系统告警
      assertThat(event.status()).isEqualTo("FAILED");
      assertThat(event.errorCode()).isEqualTo("FETCH_ERROR");
      assertThat(event.errorMessage()).contains("timeout");
    }

    @Test
    @DisplayName("CURSOR_PENDING 状态应该触发游标持久化")
    void cursorPendingStatusShouldTriggerCursorPersistence() {
      // Given: 任务完成但有游标待处理
      TaskCompletedEvent event =
          TaskCompletedEvent.of(
              1001L, 2001L, 3001L, "CURSOR_PENDING", Instant.parse("2024-01-15T10:30:00Z"));

      // Then: 事件标记为 CURSOR_PENDING
      assertThat(event.status()).isEqualTo("CURSOR_PENDING");
      assertThat(event.errorCode()).isNull(); // 非错误场景
    }

    @Test
    @DisplayName("PARTIAL 状态应该标记部分成功场景")
    void partialStatusShouldMarkPartialSuccessScenario() {
      // Given: 任务部分成功
      TaskCompletedEvent event =
          TaskCompletedEvent.of(
              1001L, 2001L, 3001L, "PARTIAL", Instant.parse("2024-01-15T10:30:00Z"));

      // Then: 事件标记为 PARTIAL
      assertThat(event.status()).isEqualTo("PARTIAL");
      assertThat(event.errorCode()).isNull(); // 非错误场景
    }

    @Test
    @DisplayName("事件链：TaskCompletedEvent → SliceStatusChangedEvent")
    void eventChainTaskToSlice() {
      // Given: TaskCompletedEvent 发布
      TaskCompletedEvent taskEvent =
          TaskCompletedEvent.of(
              1001L, 2001L, 3001L, "SUCCEEDED", Instant.parse("2024-01-15T10:30:00Z"));

      // Then: 包含 sliceId 用于触发 SliceStatusChangedEvent
      assertThat(taskEvent.sliceId()).isEqualTo(2001L);
      assertThat(taskEvent.planId()).isEqualTo(3001L);
    }
  }

  // ========== 边界情况测试 ==========

  @Nested
  @DisplayName("边界情况")
  class EdgeCaseTests {

    @Test
    @DisplayName("应该支持极长的错误消息")
    void shouldSupportVeryLongErrorMessage() {
      // Given
      String longMessage = "Error: ".repeat(1000); // 7000 个字符

      // When
      TaskCompletedEvent event =
          TaskCompletedEvent.ofFailure(
              1001L, 2001L, 3001L, "FAILED", "LONG_ERROR", longMessage, Instant.now());

      // Then
      assertThat(event.errorMessage()).hasSize(7000);
    }

    @Test
    @DisplayName("应该支持特殊字符的错误消息")
    void shouldSupportSpecialCharactersInErrorMessage() {
      // Given
      String specialMessage = "Error: \n\t\"JSON parse failed\"\n\u0000\uD83D\uDE00";

      // When
      TaskCompletedEvent event =
          TaskCompletedEvent.ofFailure(
              1001L, 2001L, 3001L, "FAILED", "PARSE_ERROR", specialMessage, Instant.now());

      // Then
      assertThat(event.errorMessage()).isEqualTo(specialMessage);
    }

    @Test
    @DisplayName("finishedAt 可以早于 occurredAt（例如：延迟发布）")
    void finishedAtCanBeBeforeOccurredAt() {
      // Given
      Instant finishedAt = Instant.parse("2024-01-15T10:30:00Z");
      Instant occurredAt = Instant.parse("2024-01-15T10:35:00Z"); // 5 分钟延迟

      // When
      TaskCompletedEvent event =
          new TaskCompletedEvent(
              1001L, 2001L, 3001L, "SUCCEEDED", null, null, finishedAt, occurredAt);

      // Then
      assertThat(event.finishedAt()).isBefore(event.occurredAt());
    }

    @Test
    @DisplayName("应该支持极小的 ID 值")
    void shouldSupportVerySmallIdValues() {
      // Given
      Long minId = 1L;

      // When
      TaskCompletedEvent event = TaskCompletedEvent.of(minId, minId, minId, "SUCCEEDED", Instant.now());

      // Then
      assertThat(event.taskId()).isEqualTo(minId);
      assertThat(event.sliceId()).isEqualTo(minId);
      assertThat(event.planId()).isEqualTo(minId);
    }

    @Test
    @DisplayName("应该支持极大的 ID 值")
    void shouldSupportVeryLargeIdValues() {
      // Given
      Long maxId = Long.MAX_VALUE;

      // When
      TaskCompletedEvent event = TaskCompletedEvent.of(maxId, maxId, maxId, "SUCCEEDED", Instant.now());

      // Then
      assertThat(event.taskId()).isEqualTo(maxId);
      assertThat(event.sliceId()).isEqualTo(maxId);
      assertThat(event.planId()).isEqualTo(maxId);
    }

    @Test
    @DisplayName("应该支持负数 ID（如果业务允许）")
    void shouldSupportNegativeIds() {
      // Given
      Long negativeId = -1L;

      // When
      TaskCompletedEvent event =
          TaskCompletedEvent.of(negativeId, negativeId, negativeId, "SUCCEEDED", Instant.now());

      // Then
      assertThat(event.taskId()).isEqualTo(negativeId);
    }

    @Test
    @DisplayName("应该支持 Instant.MIN")
    void shouldSupportInstantMin() {
      // Given
      Instant minInstant = Instant.MIN;

      // When
      TaskCompletedEvent event =
          new TaskCompletedEvent(1001L, 2001L, 3001L, "SUCCEEDED", null, null, minInstant, minInstant);

      // Then
      assertThat(event.finishedAt()).isEqualTo(minInstant);
      assertThat(event.occurredAt()).isEqualTo(minInstant);
    }

    @Test
    @DisplayName("应该支持 Instant.MAX")
    void shouldSupportInstantMax() {
      // Given
      Instant maxInstant = Instant.MAX;

      // When
      TaskCompletedEvent event =
          new TaskCompletedEvent(1001L, 2001L, 3001L, "SUCCEEDED", null, null, maxInstant, maxInstant);

      // Then
      assertThat(event.finishedAt()).isEqualTo(maxInstant);
      assertThat(event.occurredAt()).isEqualTo(maxInstant);
    }
  }

  // ========== 不可变性测试 ==========

  @Nested
  @DisplayName("不可变性")
  class ImmutabilityTests {

    @Test
    @DisplayName("Record 应该是不可变的（无 setter 方法）")
    void recordShouldBeImmutable() {
      // Given
      TaskCompletedEvent event =
          TaskCompletedEvent.of(1001L, 2001L, 3001L, "SUCCEEDED", Instant.now());

      // When & Then: 验证没有公共的 setter 方法
      assertThat(TaskCompletedEvent.class.getMethods())
          .noneMatch(
              method ->
                  method.getName().startsWith("set")
                      && method.getParameterCount() == 1
                      && method.getReturnType() == void.class);
    }

    @Test
    @DisplayName("应该无法修改 Instant 字段（通过访问器获取的引用）")
    void shouldNotBeAbleToModifyInstantFields() {
      // Given
      Instant finishedAt = Instant.parse("2024-01-15T10:30:00Z");
      TaskCompletedEvent event =
          TaskCompletedEvent.of(1001L, 2001L, 3001L, "SUCCEEDED", finishedAt);

      // When: 尝试通过引用修改（Instant 是不可变的）
      Instant retrievedFinishedAt = event.finishedAt();

      // Then: Instant 是不可变的，无法修改
      assertThat(retrievedFinishedAt).isEqualTo(finishedAt);
      assertThat(event.finishedAt()).isEqualTo(finishedAt); // 原值不变
    }

    @Test
    @DisplayName("事件创建后所有字段应该保持不变")
    void allFieldsShouldRemainUnchangedAfterCreation() {
      // Given
      Long taskId = 1001L;
      Long sliceId = 2001L;
      Long planId = 3001L;
      String status = "SUCCEEDED";
      Instant finishedAt = Instant.parse("2024-01-15T10:30:00Z");
      Instant occurredAt = Instant.parse("2024-01-15T10:30:01Z");

      // When
      TaskCompletedEvent event =
          new TaskCompletedEvent(
              taskId, sliceId, planId, status, null, null, finishedAt, occurredAt);

      // Then: 多次访问应该返回相同的值
      assertThat(event.taskId()).isEqualTo(taskId).isEqualTo(event.taskId());
      assertThat(event.sliceId()).isEqualTo(sliceId).isEqualTo(event.sliceId());
      assertThat(event.planId()).isEqualTo(planId).isEqualTo(event.planId());
      assertThat(event.status()).isEqualTo(status).isEqualTo(event.status());
      assertThat(event.finishedAt()).isEqualTo(finishedAt).isEqualTo(event.finishedAt());
      assertThat(event.occurredAt()).isEqualTo(occurredAt).isEqualTo(event.occurredAt());
    }
  }

  // ========== 时间戳相关测试 ==========

  @Nested
  @DisplayName("时间戳相关")
  class TimestampTests {

    @Test
    @DisplayName("occurredAt 应该在 finishedAt 之后或相等")
    void occurredAtShouldBeAfterOrEqualToFinishedAt() {
      // Given
      Instant finishedAt = Instant.parse("2024-01-15T10:30:00Z");

      // When
      TaskCompletedEvent event = TaskCompletedEvent.of(1001L, 2001L, 3001L, "SUCCEEDED", finishedAt);

      // Then
      assertThat(event.occurredAt()).isAfterOrEqualTo(event.finishedAt());
    }

    @Test
    @DisplayName("自动生成的 occurredAt 应该接近当前时间")
    void autoGeneratedOccurredAtShouldBeCloseToNow() {
      // Given
      Instant before = Instant.now();

      // When
      TaskCompletedEvent event =
          TaskCompletedEvent.of(1001L, 2001L, 3001L, "SUCCEEDED", Instant.now());

      // Then
      Instant after = Instant.now();
      assertThat(event.occurredAt())
          .isBetween(before, after.plusMillis(100)); // 允许 100ms 误差
    }

    @Test
    @DisplayName("finishedAt 和 occurredAt 可以相同")
    void finishedAtAndOccurredAtCanBeSame() {
      // Given
      Instant timestamp = Instant.parse("2024-01-15T10:30:00Z");

      // When
      TaskCompletedEvent event =
          new TaskCompletedEvent(1001L, 2001L, 3001L, "SUCCEEDED", null, null, timestamp, timestamp);

      // Then
      assertThat(event.finishedAt()).isEqualTo(event.occurredAt());
    }
  }
}
