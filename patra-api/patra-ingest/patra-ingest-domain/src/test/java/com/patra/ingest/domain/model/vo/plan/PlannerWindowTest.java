package com.patra.ingest.domain.model.vo.plan;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// PlannerWindow 值对象单元测试。
///
/// 测试策略：
///
/// - 纯 Java 单元测试，不依赖 Spring 容器
///   - 遵循 Given-When-Then 结构
///   - 使用 AssertJ 流畅断言
///   - 使用 @Nested 分组组织测试
///
/// 测试范围：
///
/// - ✅ record 构造函数验证（紧凑构造器）
///   - ✅ from < to 验证规则（from 必须早于 to）
///   - ✅ null 值语义（无界窗口）
///   - ✅ full() 静态工厂方法测试
///   - ✅ isEmpty() 查询方法测试
///   - ✅ isFull() 查询方法测试
///   - ✅ record 自动生成的 equals/hashCode/toString 测试
///   - ✅ 边界值和时间精度测试
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PlannerWindow 值对象单元测试")
class PlannerWindowTest {

  // ========== 构造函数验证测试 - 成功场景 ==========

  @Nested
  @DisplayName("构造函数验证 - 成功场景")
  class ConstructorValidationSuccessTests {

    @Test
    @DisplayName("应该成功创建 PlannerWindow 当 from < to")
    void shouldCreatePlannerWindowWhenFromIsBeforeTo() {
      // Given
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");

      // When
      PlannerWindow window = new PlannerWindow(from, to);

      // Then
      assertThat(window).isNotNull();
      assertThat(window.from()).isEqualTo(from);
      assertThat(window.to()).isEqualTo(to);
    }

    @Test
    @DisplayName("应该成功创建 PlannerWindow 当 from 和 to 都为 null")
    void shouldCreatePlannerWindowWhenBothFromAndToAreNull() {
      // Given
      Instant from = null;
      Instant to = null;

      // When
      PlannerWindow window = new PlannerWindow(from, to);

      // Then
      assertThat(window).isNotNull();
      assertThat(window.from()).isNull();
      assertThat(window.to()).isNull();
    }

    @Test
    @DisplayName("应该成功创建 PlannerWindow 当 from 为 null（无下界）")
    void shouldCreatePlannerWindowWhenFromIsNull() {
      // Given
      Instant from = null;
      Instant to = Instant.parse("2025-01-02T00:00:00Z");

      // When
      PlannerWindow window = new PlannerWindow(from, to);

      // Then
      assertThat(window).isNotNull();
      assertThat(window.from()).isNull();
      assertThat(window.to()).isEqualTo(to);
    }

    @Test
    @DisplayName("应该成功创建 PlannerWindow 当 to 为 null（无上界）")
    void shouldCreatePlannerWindowWhenToIsNull() {
      // Given
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = null;

      // When
      PlannerWindow window = new PlannerWindow(from, to);

      // Then
      assertThat(window).isNotNull();
      assertThat(window.from()).isEqualTo(from);
      assertThat(window.to()).isNull();
    }
  }

  // ========== 构造函数验证测试 - 失败场景 ==========

  @Nested
  @DisplayName("构造函数验证 - 失败场景")
  class ConstructorValidationFailureTests {

    @Test
    @DisplayName("应该抛出异常当 from = to")
    void shouldThrowExceptionWhenFromEqualsTo() {
      // Given
      Instant sameInstant = Instant.parse("2025-01-01T00:00:00Z");
      Instant from = sameInstant;
      Instant to = sameInstant;

      // When & Then
      assertThatThrownBy(() -> new PlannerWindow(from, to))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("窗口起始时间必须早于结束时间");
    }

    @Test
    @DisplayName("应该抛出异常当 from > to")
    void shouldThrowExceptionWhenFromIsAfterTo() {
      // Given
      Instant from = Instant.parse("2025-01-02T00:00:00Z");
      Instant to = Instant.parse("2025-01-01T00:00:00Z");

      // When & Then
      assertThatThrownBy(() -> new PlannerWindow(from, to))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("窗口起始时间必须早于结束时间");
    }

    @Test
    @DisplayName("应该抛出异常当 from 仅比 to 早 1 毫秒且 from 实际上晚于 to")
    void shouldThrowExceptionWhenFromIsOneMillisecondAfterTo() {
      // Given
      Instant to = Instant.parse("2025-01-01T00:00:00Z");
      Instant from = to.plusMillis(1);

      // When & Then
      assertThatThrownBy(() -> new PlannerWindow(from, to))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("窗口起始时间必须早于结束时间");
    }

    @Test
    @DisplayName("应该抛出异常当 from 比 to 早 1 纳秒但实际上晚于 to")
    void shouldThrowExceptionWhenFromIsOneNanosecondAfterTo() {
      // Given
      Instant to = Instant.parse("2025-01-01T00:00:00Z");
      Instant from = to.plusNanos(1);

      // When & Then
      assertThatThrownBy(() -> new PlannerWindow(from, to))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("窗口起始时间必须早于结束时间");
    }
  }

  // ========== full() 静态工厂方法测试 ==========

  @Nested
  @DisplayName("full() 静态工厂方法")
  class FullFactoryMethodTests {

    @Test
    @DisplayName("应该返回 from=null, to=null")
    void shouldReturnWindowWithNullFromAndTo() {
      // When
      PlannerWindow window = PlannerWindow.full();

      // Then
      assertThat(window).isNotNull();
      assertThat(window.from()).isNull();
      assertThat(window.to()).isNull();
    }

    @Test
    @DisplayName("应该返回值相等的对象当多次调用 full()")
    void shouldReturnEqualObjectsWhenCalledMultipleTimes() {
      // When
      PlannerWindow window1 = PlannerWindow.full();
      PlannerWindow window2 = PlannerWindow.full();
      PlannerWindow window3 = PlannerWindow.full();

      // Then - 值语义：相等但不一定是同一个实例
      assertThat(window1).isEqualTo(window2);
      assertThat(window2).isEqualTo(window3);
      assertThat(window1).isEqualTo(window3);
      assertThat(window1.hashCode()).isEqualTo(window2.hashCode());
      assertThat(window2.hashCode()).isEqualTo(window3.hashCode());
    }

    @Test
    @DisplayName("full() 返回的对象 isFull() 应该返回 true")
    void fullWindowShouldReturnTrueForIsFull() {
      // When
      PlannerWindow window = PlannerWindow.full();

      // Then
      assertThat(window.isFull()).isTrue();
    }

    @Test
    @DisplayName("full() 返回的对象 isEmpty() 应该返回 false")
    void fullWindowShouldReturnFalseForIsEmpty() {
      // When
      PlannerWindow window = PlannerWindow.full();

      // Then
      assertThat(window.isEmpty()).isFalse();
    }
  }

  // ========== isEmpty() 查询方法测试 ==========

  @Nested
  @DisplayName("isEmpty() 查询方法")
  class IsEmptyMethodTests {

    @Test
    @DisplayName("应该返回 false 当 from 和 to 都为 null")
    void shouldReturnFalseWhenBothFromAndToAreNull() {
      // Given
      PlannerWindow window = new PlannerWindow(null, null);

      // When
      boolean isEmpty = window.isEmpty();

      // Then
      assertThat(isEmpty).isFalse();
    }

    @Test
    @DisplayName("应该返回 false 当 from 为 null 但 to 有值")
    void shouldReturnFalseWhenFromIsNullButToHasValue() {
      // Given
      PlannerWindow window = new PlannerWindow(null, Instant.parse("2025-01-02T00:00:00Z"));

      // When
      boolean isEmpty = window.isEmpty();

      // Then
      assertThat(isEmpty).isFalse();
    }

    @Test
    @DisplayName("应该返回 false 当 to 为 null 但 from 有值")
    void shouldReturnFalseWhenToIsNullButFromHasValue() {
      // Given
      PlannerWindow window = new PlannerWindow(Instant.parse("2025-01-01T00:00:00Z"), null);

      // When
      boolean isEmpty = window.isEmpty();

      // Then
      assertThat(isEmpty).isFalse();
    }

    @Test
    @DisplayName("应该返回 false 当 from < to")
    void shouldReturnFalseWhenFromIsBeforeTo() {
      // Given
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlannerWindow window = new PlannerWindow(from, to);

      // When
      boolean isEmpty = window.isEmpty();

      // Then
      assertThat(isEmpty).isFalse();
    }

    @Test
    @DisplayName("isEmpty() 应该是幂等的")
    void isEmptyShouldBeIdempotent() {
      // Given
      PlannerWindow window =
          new PlannerWindow(
              Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-01-02T00:00:00Z"));

      // When
      boolean result1 = window.isEmpty();
      boolean result2 = window.isEmpty();
      boolean result3 = window.isEmpty();

      // Then
      assertThat(result1).isFalse();
      assertThat(result2).isFalse();
      assertThat(result3).isFalse();
      assertThat(result1).isEqualTo(result2).isEqualTo(result3);
    }
  }

  // ========== isFull() 查询方法测试 ==========

  @Nested
  @DisplayName("isFull() 查询方法")
  class IsFullMethodTests {

    @Test
    @DisplayName("应该返回 true 当 from 和 to 都为 null")
    void shouldReturnTrueWhenBothFromAndToAreNull() {
      // Given
      PlannerWindow window = new PlannerWindow(null, null);

      // When
      boolean isFull = window.isFull();

      // Then
      assertThat(isFull).isTrue();
    }

    @Test
    @DisplayName("应该返回 false 当 from 为 null 但 to 有值")
    void shouldReturnFalseWhenFromIsNullButToHasValue() {
      // Given
      PlannerWindow window = new PlannerWindow(null, Instant.parse("2025-01-02T00:00:00Z"));

      // When
      boolean isFull = window.isFull();

      // Then
      assertThat(isFull).isFalse();
    }

    @Test
    @DisplayName("应该返回 false 当 to 为 null 但 from 有值")
    void shouldReturnFalseWhenToIsNullButFromHasValue() {
      // Given
      PlannerWindow window = new PlannerWindow(Instant.parse("2025-01-01T00:00:00Z"), null);

      // When
      boolean isFull = window.isFull();

      // Then
      assertThat(isFull).isFalse();
    }

    @Test
    @DisplayName("应该返回 false 当 from 和 to 都有值")
    void shouldReturnFalseWhenBothFromAndToHaveValues() {
      // Given
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlannerWindow window = new PlannerWindow(from, to);

      // When
      boolean isFull = window.isFull();

      // Then
      assertThat(isFull).isFalse();
    }

    @Test
    @DisplayName("isFull() 应该是幂等的")
    void isFullShouldBeIdempotent() {
      // Given
      PlannerWindow window = new PlannerWindow(null, null);

      // When
      boolean result1 = window.isFull();
      boolean result2 = window.isFull();
      boolean result3 = window.isFull();

      // Then
      assertThat(result1).isTrue();
      assertThat(result2).isTrue();
      assertThat(result3).isTrue();
      assertThat(result1).isEqualTo(result2).isEqualTo(result3);
    }
  }

  // ========== record 语义测试 ==========

  @Nested
  @DisplayName("record 语义")
  class RecordSemanticsTests {

    @Test
    @DisplayName("应该正确返回 from 字段")
    void shouldReturnFromFieldCorrectly() {
      // Given
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlannerWindow window = new PlannerWindow(from, to);

      // When
      Instant retrievedFrom = window.from();

      // Then
      assertThat(retrievedFrom).isEqualTo(from);
    }

    @Test
    @DisplayName("应该正确返回 to 字段")
    void shouldReturnToFieldCorrectly() {
      // Given
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlannerWindow window = new PlannerWindow(from, to);

      // When
      Instant retrievedTo = window.to();

      // Then
      assertThat(retrievedTo).isEqualTo(to);
    }

    @Test
    @DisplayName("应该正确实现 equals - 相同的值")
    void shouldImplementEqualsCorrectlyForSameValues() {
      // Given
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlannerWindow window1 = new PlannerWindow(from, to);
      PlannerWindow window2 = new PlannerWindow(from, to);

      // When & Then
      assertThat(window1).isEqualTo(window2);
      assertThat(window2).isEqualTo(window1); // 对称性
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同的 from")
    void shouldImplementEqualsCorrectlyForDifferentFrom() {
      // Given
      Instant from1 = Instant.parse("2025-01-01T00:00:00Z");
      Instant from2 = Instant.parse("2025-01-01T01:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlannerWindow window1 = new PlannerWindow(from1, to);
      PlannerWindow window2 = new PlannerWindow(from2, to);

      // When & Then
      assertThat(window1).isNotEqualTo(window2);
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同的 to")
    void shouldImplementEqualsCorrectlyForDifferentTo() {
      // Given
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to1 = Instant.parse("2025-01-02T00:00:00Z");
      Instant to2 = Instant.parse("2025-01-03T00:00:00Z");
      PlannerWindow window1 = new PlannerWindow(from, to1);
      PlannerWindow window2 = new PlannerWindow(from, to2);

      // When & Then
      assertThat(window1).isNotEqualTo(window2);
    }

    @Test
    @DisplayName("应该正确实现 equals - 自反性")
    void shouldImplementEqualsReflexivity() {
      // Given
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlannerWindow window = new PlannerWindow(from, to);

      // When & Then
      assertThat(window).isEqualTo(window);
    }

    @Test
    @DisplayName("应该正确实现 equals - 传递性")
    void shouldImplementEqualsTransitivity() {
      // Given
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlannerWindow window1 = new PlannerWindow(from, to);
      PlannerWindow window2 = new PlannerWindow(from, to);
      PlannerWindow window3 = new PlannerWindow(from, to);

      // When & Then - 如果 a == b 且 b == c，则 a == c
      assertThat(window1).isEqualTo(window2);
      assertThat(window2).isEqualTo(window3);
      assertThat(window1).isEqualTo(window3);
    }

    @Test
    @DisplayName("应该正确实现 equals - 与 null 比较")
    void shouldImplementEqualsWithNull() {
      // Given
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlannerWindow window = new PlannerWindow(from, to);

      // When & Then
      assertThat(window).isNotEqualTo(null);
    }

    @Test
    @DisplayName("应该正确实现 equals - 与不同类型比较")
    void shouldImplementEqualsWithDifferentType() {
      // Given
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlannerWindow window = new PlannerWindow(from, to);
      Object other = "PlannerWindow(2025-01-01T00:00:00Z, 2025-01-02T00:00:00Z)";

      // When & Then
      assertThat(window).isNotEqualTo(other);
    }

    @Test
    @DisplayName("应该正确实现 equals - null 字段也相等")
    void shouldImplementEqualsCorrectlyForNullFields() {
      // Given
      PlannerWindow window1 = new PlannerWindow(null, null);
      PlannerWindow window2 = new PlannerWindow(null, null);

      // When & Then
      assertThat(window1).isEqualTo(window2);
    }

    @Test
    @DisplayName("应该正确实现 hashCode - 相同的对象产生相同的 hashCode")
    void shouldImplementHashCodeConsistentlyForEqualObjects() {
      // Given
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlannerWindow window1 = new PlannerWindow(from, to);
      PlannerWindow window2 = new PlannerWindow(from, to);

      // When & Then
      assertThat(window1.hashCode()).isEqualTo(window2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 hashCode - 多次调用返回相同值")
    void shouldImplementHashCodeConsistentlyAcrossMultipleCalls() {
      // Given
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlannerWindow window = new PlannerWindow(from, to);

      // When
      int hashCode1 = window.hashCode();
      int hashCode2 = window.hashCode();
      int hashCode3 = window.hashCode();

      // Then
      assertThat(hashCode1).isEqualTo(hashCode2);
      assertThat(hashCode2).isEqualTo(hashCode3);
    }

    @Test
    @DisplayName("应该正确实现 toString - 包含所有字段")
    void shouldImplementToStringWithAllFields() {
      // Given
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlannerWindow window = new PlannerWindow(from, to);

      // When
      String toString = window.toString();

      // Then
      assertThat(toString).contains("PlannerWindow");
      assertThat(toString).contains("2025-01-01T00:00:00Z");
      assertThat(toString).contains("2025-01-02T00:00:00Z");
    }

    @Test
    @DisplayName("应该正确实现 toString - 格式符合 record 规范")
    void shouldImplementToStringInRecordFormat() {
      // Given
      PlannerWindow window = new PlannerWindow(null, null);

      // When
      String toString = window.toString();

      // Then - record 的 toString 格式: ClassName[field1=value1, field2=value2, ...]
      assertThat(toString).startsWith("PlannerWindow[").endsWith("]");
    }

    @Test
    @DisplayName("应该正确实现 toString - null 字段")
    void shouldImplementToStringWithNullFields() {
      // Given
      PlannerWindow window = new PlannerWindow(null, null);

      // When
      String toString = window.toString();

      // Then
      assertThat(toString).contains("PlannerWindow");
      assertThat(toString).contains("null");
    }
  }

  // ========== 边界情况测试 ==========

  @Nested
  @DisplayName("边界情况")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理 from 和 to 时间差为 1 毫秒")
    void shouldHandleWindowWithOneMillisecondDuration() {
      // Given
      Instant from = Instant.parse("2025-01-01T00:00:00.000Z");
      Instant to = from.plusMillis(1);

      // When
      PlannerWindow window = new PlannerWindow(from, to);

      // Then
      assertThat(window).isNotNull();
      assertThat(window.from()).isEqualTo(from);
      assertThat(window.to()).isEqualTo(to);
      assertThat(window.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("应该处理 from 和 to 时间差为 1 纳秒")
    void shouldHandleWindowWithOneNanosecondDuration() {
      // Given
      Instant from = Instant.parse("2025-01-01T00:00:00.000000000Z");
      Instant to = from.plusNanos(1);

      // When
      PlannerWindow window = new PlannerWindow(from, to);

      // Then
      assertThat(window).isNotNull();
      assertThat(window.from()).isEqualTo(from);
      assertThat(window.to()).isEqualTo(to);
      assertThat(window.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("应该处理 Instant.MIN 和 Instant.MAX")
    void shouldHandleInstantMinAndMax() {
      // Given
      Instant from = Instant.MIN;
      Instant to = Instant.MAX;

      // When
      PlannerWindow window = new PlannerWindow(from, to);

      // Then
      assertThat(window).isNotNull();
      assertThat(window.from()).isEqualTo(Instant.MIN);
      assertThat(window.to()).isEqualTo(Instant.MAX);
      assertThat(window.isEmpty()).isFalse();
      assertThat(window.isFull()).isFalse();
    }

    @Test
    @DisplayName("应该处理跨年的时间窗口")
    void shouldHandleWindowCrossingYearBoundary() {
      // Given
      Instant from = Instant.parse("2024-12-31T23:00:00Z");
      Instant to = Instant.parse("2025-01-01T01:00:00Z");

      // When
      PlannerWindow window = new PlannerWindow(from, to);

      // Then
      assertThat(window).isNotNull();
      assertThat(window.from()).isEqualTo(from);
      assertThat(window.to()).isEqualTo(to);
      assertThat(window.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("应该处理跨月的时间窗口")
    void shouldHandleWindowCrossingMonthBoundary() {
      // Given
      Instant from = Instant.parse("2025-01-31T23:00:00Z");
      Instant to = Instant.parse("2025-02-01T01:00:00Z");

      // When
      PlannerWindow window = new PlannerWindow(from, to);

      // Then
      assertThat(window).isNotNull();
      assertThat(window.from()).isEqualTo(from);
      assertThat(window.to()).isEqualTo(to);
      assertThat(window.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("应该处理跨日的时间窗口")
    void shouldHandleWindowCrossingDayBoundary() {
      // Given
      Instant from = Instant.parse("2025-01-01T23:00:00Z");
      Instant to = Instant.parse("2025-01-02T01:00:00Z");

      // When
      PlannerWindow window = new PlannerWindow(from, to);

      // Then
      assertThat(window).isNotNull();
      assertThat(window.from()).isEqualTo(from);
      assertThat(window.to()).isEqualTo(to);
      assertThat(window.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("应该处理非常长的时间窗口（1年）")
    void shouldHandleVeryLongWindow() {
      // Given
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-01T00:00:00Z");

      // When
      PlannerWindow window = new PlannerWindow(from, to);

      // Then
      assertThat(window).isNotNull();
      assertThat(window.from()).isEqualTo(from);
      assertThat(window.to()).isEqualTo(to);
      assertThat(window.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("应该处理 Instant.EPOCH 作为起始时间")
    void shouldHandleInstantEpochAsFrom() {
      // Given
      Instant from = Instant.EPOCH;
      Instant to = Instant.parse("2025-01-01T00:00:00Z");

      // When
      PlannerWindow window = new PlannerWindow(from, to);

      // Then
      assertThat(window).isNotNull();
      assertThat(window.from()).isEqualTo(Instant.EPOCH);
      assertThat(window.to()).isEqualTo(to);
      assertThat(window.isEmpty()).isFalse();
    }
  }

  // ========== 值对象不变性测试 ==========

  @Nested
  @DisplayName("值对象不变性")
  class ValueObjectImmutabilityTests {

    @Test
    @DisplayName("应该保证所有字段不可变")
    void shouldEnsureAllFieldsAreImmutable() {
      // Given
      Instant originalFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant originalTo = Instant.parse("2025-01-02T00:00:00Z");
      PlannerWindow window = new PlannerWindow(originalFrom, originalTo);

      // When - 多次获取字段
      Instant retrievedFrom = window.from();
      Instant retrievedTo = window.to();

      // Then - 应该返回原始值
      assertThat(retrievedFrom).isEqualTo(originalFrom);
      assertThat(retrievedTo).isEqualTo(originalTo);
      assertThat(window.from()).isEqualTo(originalFrom); // 多次调用返回相同值
      assertThat(window.to()).isEqualTo(originalTo);
    }

    @Test
    @DisplayName("应该保证 PlannerWindow 完全不可变")
    void shouldEnsurePlannerWindowIsCompletelyImmutable() {
      // Given
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlannerWindow window = new PlannerWindow(from, to);
      Instant originalFrom = window.from();
      Instant originalTo = window.to();
      boolean originalIsEmpty = window.isEmpty();
      boolean originalIsFull = window.isFull();

      // When - 多次调用方法
      window.from();
      window.to();
      window.isEmpty();
      window.isFull();

      // Then - 所有值应该保持不变
      assertThat(window.from()).isEqualTo(originalFrom);
      assertThat(window.to()).isEqualTo(originalTo);
      assertThat(window.isEmpty()).isEqualTo(originalIsEmpty);
      assertThat(window.isFull()).isEqualTo(originalIsFull);
    }

    @Test
    @DisplayName("应该保证 Instant 字段不可变（Instant 本身是不可变的）")
    void shouldEnsureInstantFieldsAreImmutable() {
      // Given
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlannerWindow window = new PlannerWindow(from, to);

      // When - 获取字段并尝试修改（Instant 是不可变的，所以修改会返回新对象）
      Instant retrievedFrom = window.from();
      Instant modifiedFrom = retrievedFrom.plusSeconds(3600); // 返回新对象

      // Then - 原对象不应该被修改
      assertThat(window.from()).isEqualTo(from);
      assertThat(window.from()).isNotEqualTo(modifiedFrom);
      assertThat(retrievedFrom).isEqualTo(from);
    }
  }
}
