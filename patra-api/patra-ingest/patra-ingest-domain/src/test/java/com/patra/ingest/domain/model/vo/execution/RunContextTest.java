package com.patra.ingest.domain.model.vo.execution;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RunContext 值对象单元测试")
class RunContextTest {

  @Nested
  @DisplayName("构造器测试")
  class ConstructorTests {

    @Test
    @DisplayName("应该使用关联ID创建有效的运行上下文")
    void shouldCreateValidRunContextWithCorrelationId() {
      // Given
      String correlationId = "trace-12345-abcde";

      // When
      RunContext context = new RunContext(correlationId);

      // Then
      assertThat(context.correlationId()).isEqualTo("trace-12345-abcde");
    }

    @Test
    @DisplayName("应该允许创建空关联ID的上下文")
    void shouldAllowNullCorrelationId() {
      // When
      RunContext context = new RunContext(null);

      // Then
      assertThat(context.correlationId()).isNull();
    }

    @Test
    @DisplayName("应该允许创建空字符串关联ID的上下文")
    void shouldAllowEmptyCorrelationId() {
      // When
      RunContext context = new RunContext("");

      // Then
      assertThat(context.correlationId()).isEmpty();
    }

    @Test
    @DisplayName("应该保留关联ID的完整内容包括特殊字符")
    void shouldPreserveCorrelationIdWithSpecialCharacters() {
      // Given
      String correlationId = "trace-123:456/abc.def@xyz";

      // When
      RunContext context = new RunContext(correlationId);

      // Then
      assertThat(context.correlationId()).isEqualTo("trace-123:456/abc.def@xyz");
    }
  }

  @Nested
  @DisplayName("静态工厂方法测试")
  class FactoryMethodTests {

    @Test
    @DisplayName("empty() 应该返回空关联ID的上下文")
    void emptyShouldReturnNullCorrelationId() {
      // When
      RunContext empty = RunContext.empty();

      // Then
      assertThat(empty.correlationId()).isNull();
    }

    @Test
    @DisplayName("empty() 应该返回等同于 null 构造的对象")
    void emptyShouldReturnEquivalentToNullConstructor() {
      // When
      RunContext empty = RunContext.empty();
      RunContext nullContext = new RunContext(null);

      // Then
      assertThat(empty).isEqualTo(nullContext);
    }
  }

  @Nested
  @DisplayName("withCorrelation() 方法测试")
  class WithCorrelationMethodTests {

    @Test
    @DisplayName("应该派生带有指定关联ID的新上下文")
    void shouldDeriveNewContextWithCorrelationId() {
      // Given
      RunContext original = RunContext.empty();
      String newCorrelationId = "new-trace-123";

      // When
      RunContext derived = original.withCorrelation(newCorrelationId);

      // Then
      assertThat(derived.correlationId()).isEqualTo("new-trace-123");
    }

    @Test
    @DisplayName("withCorrelation() 应该是不可变操作")
    void withCorrelationShouldBeImmutable() {
      // Given
      RunContext original = new RunContext("original-trace");

      // When
      RunContext derived = original.withCorrelation("new-trace");

      // Then
      assertThat(original.correlationId()).isEqualTo("original-trace"); // 原始对象未改变
      assertThat(derived.correlationId()).isEqualTo("new-trace"); // 新对象包含新值
    }

    @Test
    @DisplayName("应该允许使用 null 派生新上下文")
    void shouldAllowDerivingWithNullCorrelationId() {
      // Given
      RunContext original = new RunContext("original-trace");

      // When
      RunContext derived = original.withCorrelation(null);

      // Then
      assertThat(derived.correlationId()).isNull();
    }

    @Test
    @DisplayName("应该允许使用空字符串派生新上下文")
    void shouldAllowDerivingWithEmptyCorrelationId() {
      // Given
      RunContext original = new RunContext("original-trace");

      // When
      RunContext derived = original.withCorrelation("");

      // Then
      assertThat(derived.correlationId()).isEmpty();
    }

    @Test
    @DisplayName("连续派生应该返回最新的关联ID")
    void consecutiveDerivationsShouldReturnLatestCorrelationId() {
      // Given
      RunContext original = RunContext.empty();

      // When
      RunContext derived1 = original.withCorrelation("trace-1");
      RunContext derived2 = derived1.withCorrelation("trace-2");
      RunContext derived3 = derived2.withCorrelation("trace-3");

      // Then
      assertThat(original.correlationId()).isNull();
      assertThat(derived1.correlationId()).isEqualTo("trace-1");
      assertThat(derived2.correlationId()).isEqualTo("trace-2");
      assertThat(derived3.correlationId()).isEqualTo("trace-3");
    }
  }

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemanticsTests {

    @Test
    @DisplayName("相同关联ID的实例应该相等")
    void instancesWithSameCorrelationIdShouldBeEqual() {
      // Given
      RunContext context1 = new RunContext("trace-123");
      RunContext context2 = new RunContext("trace-123");

      // Then
      assertThat(context1).isEqualTo(context2);
      assertThat(context1.hashCode()).isEqualTo(context2.hashCode());
    }

    @Test
    @DisplayName("不同关联ID的实例应该不相等")
    void instancesWithDifferentCorrelationIdsShouldNotBeEqual() {
      // Given
      RunContext context1 = new RunContext("trace-123");
      RunContext context2 = new RunContext("trace-456");

      // Then
      assertThat(context1).isNotEqualTo(context2);
    }

    @Test
    @DisplayName("两个空上下文应该相等")
    void twoEmptyContextsShouldBeEqual() {
      // Given
      RunContext empty1 = RunContext.empty();
      RunContext empty2 = RunContext.empty();

      // Then
      assertThat(empty1).isEqualTo(empty2);
      assertThat(empty1.hashCode()).isEqualTo(empty2.hashCode());
    }

    @Test
    @DisplayName("空上下文与 null 关联ID上下文应该相等")
    void emptyContextShouldEqualNullCorrelationIdContext() {
      // Given
      RunContext empty = RunContext.empty();
      RunContext nullContext = new RunContext(null);

      // Then
      assertThat(empty).isEqualTo(nullContext);
    }

    @Test
    @DisplayName("toString() 应该包含关联ID信息")
    void toStringShouldContainCorrelationId() {
      // Given
      RunContext context = new RunContext("trace-123");

      // When
      String result = context.toString();

      // Then
      assertThat(result).contains("RunContext").contains("trace-123");
    }

    @Test
    @DisplayName("应该支持作为 Map 的键")
    void shouldWorkAsMapKey() {
      // Given
      var map = new java.util.HashMap<RunContext, String>();
      RunContext key1 = new RunContext("trace-123");
      RunContext key2 = new RunContext("trace-123");

      // When
      map.put(key1, "value1");

      // Then
      assertThat(map.get(key2)).isEqualTo("value1"); // 相同值可以检索
      assertThat(map).containsKey(key1);
      assertThat(map).containsKey(key2);
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理非常长的关联ID")
    void shouldHandleVeryLongCorrelationId() {
      // Given
      String longCorrelationId = "trace-" + "a".repeat(1000);

      // When
      RunContext context = new RunContext(longCorrelationId);

      // Then
      assertThat(context.correlationId()).hasSize(1006);
      assertThat(context.correlationId()).startsWith("trace-");
    }

    @Test
    @DisplayName("应该处理包含换行符的关联ID")
    void shouldHandleCorrelationIdWithNewlines() {
      // Given
      String correlationIdWithNewlines = "trace-123\nabc\r\ndef";

      // When
      RunContext context = new RunContext(correlationIdWithNewlines);

      // Then
      assertThat(context.correlationId()).isEqualTo("trace-123\nabc\r\ndef");
    }

    @Test
    @DisplayName("应该处理包含 Unicode 字符的关联ID")
    void shouldHandleCorrelationIdWithUnicode() {
      // Given
      String unicodeCorrelationId = "trace-测试-123-🔍";

      // When
      RunContext context = new RunContext(unicodeCorrelationId);

      // Then
      assertThat(context.correlationId()).isEqualTo("trace-测试-123-🔍");
    }

    @Test
    @DisplayName("应该处理只包含空白字符的关联ID")
    void shouldHandleWhitespaceOnlyCorrelationId() {
      // Given
      String whitespaceCorrelationId = "   \t\n   ";

      // When
      RunContext context = new RunContext(whitespaceCorrelationId);

      // Then
      assertThat(context.correlationId()).isEqualTo("   \t\n   ");
    }
  }
}
