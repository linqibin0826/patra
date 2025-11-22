package com.patra.ingest.domain.model.vo.plan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link TaskSchedulerContext} 单元测试
///
/// 验证任务调度器上下文值对象的行为：
///
/// - 构造器验证
///   - 工厂方法
///   - 派生方法
///   - Record 语义（equals/hashCode/toString）
///   - 边界条件
///
@DisplayName("TaskSchedulerContext 单元测试")
class TaskSchedulerContextTest {

  @Nested
  @DisplayName("构造器测试")
  class ConstructorTests {

    @Test
    @DisplayName("应该创建包含有效 correlationId 的上下文")
    void shouldCreateContextWithValidCorrelationId() {
      // Given
      String correlationId = "trace-12345";

      // When
      TaskSchedulerContext context = new TaskSchedulerContext(correlationId);

      // Then
      assertThat(context).isNotNull();
      assertThat(context.correlationId()).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("应该创建包含 null correlationId 的上下文")
    void shouldCreateContextWithNullCorrelationId() {
      // Given & When
      TaskSchedulerContext context = new TaskSchedulerContext(null);

      // Then
      assertThat(context).isNotNull();
      assertThat(context.correlationId()).isNull();
    }

    @Test
    @DisplayName("应该创建包含空字符串 correlationId 的上下文")
    void shouldCreateContextWithEmptyCorrelationId() {
      // Given
      String correlationId = "";

      // When
      TaskSchedulerContext context = new TaskSchedulerContext(correlationId);

      // Then
      assertThat(context).isNotNull();
      assertThat(context.correlationId()).isEmpty();
    }

    @Test
    @DisplayName("应该创建包含空白字符 correlationId 的上下文")
    void shouldCreateContextWithBlankCorrelationId() {
      // Given
      String correlationId = "   ";

      // When
      TaskSchedulerContext context = new TaskSchedulerContext(correlationId);

      // Then
      assertThat(context).isNotNull();
      assertThat(context.correlationId()).isEqualTo(correlationId);
    }
  }

  @Nested
  @DisplayName("empty() 工厂方法测试")
  class EmptyFactoryMethodTests {

    @Test
    @DisplayName("应该创建空上下文（correlationId 为 null）")
    void shouldCreateEmptyContext() {
      // When
      TaskSchedulerContext context = TaskSchedulerContext.empty();

      // Then
      assertThat(context).isNotNull();
      assertThat(context.correlationId()).isNull();
    }

    @Test
    @DisplayName("多次调用 empty() 应该创建不同的实例")
    void shouldCreateDifferentInstancesOnMultipleEmptyCalls() {
      // When
      TaskSchedulerContext context1 = TaskSchedulerContext.empty();
      TaskSchedulerContext context2 = TaskSchedulerContext.empty();

      // Then
      assertThat(context1).isNotSameAs(context2);
    }

    @Test
    @DisplayName("空上下文应该与构造器创建的 null 上下文相等")
    void emptyContextShouldEqualNullConstructorContext() {
      // When
      TaskSchedulerContext emptyContext = TaskSchedulerContext.empty();
      TaskSchedulerContext nullContext = new TaskSchedulerContext(null);

      // Then
      assertThat(emptyContext).isEqualTo(nullContext);
    }
  }

  @Nested
  @DisplayName("withCorrelation() 派生方法测试")
  class WithCorrelationTests {

    @Test
    @DisplayName("应该创建具有新 correlationId 的上下文")
    void shouldCreateContextWithNewCorrelationId() {
      // Given
      TaskSchedulerContext original = new TaskSchedulerContext("old-trace");
      String newCorrelationId = "new-trace";

      // When
      TaskSchedulerContext derived = original.withCorrelation(newCorrelationId);

      // Then
      assertThat(derived).isNotNull();
      assertThat(derived.correlationId()).isEqualTo(newCorrelationId);
    }

    @Test
    @DisplayName("派生上下文应该是新实例（不修改原实例）")
    void derivedContextShouldBeNewInstance() {
      // Given
      TaskSchedulerContext original = new TaskSchedulerContext("original-trace");

      // When
      TaskSchedulerContext derived = original.withCorrelation("new-trace");

      // Then
      assertThat(derived).isNotSameAs(original);
      assertThat(original.correlationId()).isEqualTo("original-trace"); // 原实例未被修改
    }

    @Test
    @DisplayName("应该支持从空上下文派生有值上下文")
    void shouldDeriveFromEmptyContext() {
      // Given
      TaskSchedulerContext emptyContext = TaskSchedulerContext.empty();
      String newCorrelationId = "trace-54321";

      // When
      TaskSchedulerContext derived = emptyContext.withCorrelation(newCorrelationId);

      // Then
      assertThat(derived.correlationId()).isEqualTo(newCorrelationId);
      assertThat(emptyContext.correlationId()).isNull(); // 原实例未被修改
    }

    @Test
    @DisplayName("应该支持派生为空上下文（correlationId 为 null）")
    void shouldDeriveToNullCorrelationId() {
      // Given
      TaskSchedulerContext original = new TaskSchedulerContext("trace-99999");

      // When
      TaskSchedulerContext derived = original.withCorrelation(null);

      // Then
      assertThat(derived.correlationId()).isNull();
    }

    @Test
    @DisplayName("应该支持派生为空字符串 correlationId")
    void shouldDeriveToEmptyCorrelationId() {
      // Given
      TaskSchedulerContext original = new TaskSchedulerContext("trace-99999");

      // When
      TaskSchedulerContext derived = original.withCorrelation("");

      // Then
      assertThat(derived.correlationId()).isEmpty();
    }

    @Test
    @DisplayName("应该支持链式派生")
    void shouldSupportChainedDerivation() {
      // Given
      TaskSchedulerContext original = TaskSchedulerContext.empty();

      // When
      TaskSchedulerContext step1 = original.withCorrelation("trace-1");
      TaskSchedulerContext step2 = step1.withCorrelation("trace-2");
      TaskSchedulerContext step3 = step2.withCorrelation("trace-3");

      // Then
      assertThat(original.correlationId()).isNull();
      assertThat(step1.correlationId()).isEqualTo("trace-1");
      assertThat(step2.correlationId()).isEqualTo("trace-2");
      assertThat(step3.correlationId()).isEqualTo("trace-3");
    }
  }

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemanticsTests {

    @Nested
    @DisplayName("equals() 测试")
    class EqualsTests {

      @Test
      @DisplayName("相同 correlationId 的上下文应该相等")
      void contextsShouldBeEqualWithSameCorrelationId() {
        // Given
        TaskSchedulerContext context1 = new TaskSchedulerContext("trace-123");
        TaskSchedulerContext context2 = new TaskSchedulerContext("trace-123");

        // Then
        assertThat(context1).isEqualTo(context2);
      }

      @Test
      @DisplayName("不同 correlationId 的上下文应该不相等")
      void contextsShouldNotBeEqualWithDifferentCorrelationId() {
        // Given
        TaskSchedulerContext context1 = new TaskSchedulerContext("trace-123");
        TaskSchedulerContext context2 = new TaskSchedulerContext("trace-456");

        // Then
        assertThat(context1).isNotEqualTo(context2);
      }

      @Test
      @DisplayName("两个空上下文应该相等")
      void emptyContextsShouldBeEqual() {
        // Given
        TaskSchedulerContext context1 = TaskSchedulerContext.empty();
        TaskSchedulerContext context2 = TaskSchedulerContext.empty();

        // Then
        assertThat(context1).isEqualTo(context2);
      }

      @Test
      @DisplayName("空上下文与非空上下文应该不相等")
      void emptyContextShouldNotEqualNonEmptyContext() {
        // Given
        TaskSchedulerContext emptyContext = TaskSchedulerContext.empty();
        TaskSchedulerContext nonEmptyContext = new TaskSchedulerContext("trace-123");

        // Then
        assertThat(emptyContext).isNotEqualTo(nonEmptyContext);
      }

      @Test
      @DisplayName("上下文与 null 比较应该不相等")
      void contextShouldNotEqualNull() {
        // Given
        TaskSchedulerContext context = new TaskSchedulerContext("trace-123");

        // Then
        assertThat(context).isNotEqualTo(null);
      }

      @Test
      @DisplayName("上下文与不同类型对象比较应该不相等")
      void contextShouldNotEqualDifferentType() {
        // Given
        TaskSchedulerContext context = new TaskSchedulerContext("trace-123");

        // Then
        assertThat(context).isNotEqualTo("trace-123");
        assertThat(context).isNotEqualTo(123);
      }

      @Test
      @DisplayName("上下文与自身比较应该相等（反射性）")
      void contextShouldEqualItself() {
        // Given
        TaskSchedulerContext context = new TaskSchedulerContext("trace-123");

        // Then
        assertThat(context).isEqualTo(context);
      }

      @Test
      @DisplayName("equals() 应该满足对称性")
      void equalsShouldBeSymmetric() {
        // Given
        TaskSchedulerContext context1 = new TaskSchedulerContext("trace-123");
        TaskSchedulerContext context2 = new TaskSchedulerContext("trace-123");

        // Then
        assertThat(context1.equals(context2)).isTrue();
        assertThat(context2.equals(context1)).isTrue();
      }

      @Test
      @DisplayName("equals() 应该满足传递性")
      void equalsShouldBeTransitive() {
        // Given
        TaskSchedulerContext context1 = new TaskSchedulerContext("trace-123");
        TaskSchedulerContext context2 = new TaskSchedulerContext("trace-123");
        TaskSchedulerContext context3 = new TaskSchedulerContext("trace-123");

        // Then
        assertThat(context1.equals(context2)).isTrue();
        assertThat(context2.equals(context3)).isTrue();
        assertThat(context1.equals(context3)).isTrue();
      }
    }

    @Nested
    @DisplayName("hashCode() 测试")
    class HashCodeTests {

      @Test
      @DisplayName("相等的上下文应该有相同的 hashCode")
      void equalContextsShouldHaveSameHashCode() {
        // Given
        TaskSchedulerContext context1 = new TaskSchedulerContext("trace-123");
        TaskSchedulerContext context2 = new TaskSchedulerContext("trace-123");

        // Then
        assertThat(context1.hashCode()).isEqualTo(context2.hashCode());
      }

      @Test
      @DisplayName("两个空上下文应该有相同的 hashCode")
      void emptyContextsShouldHaveSameHashCode() {
        // Given
        TaskSchedulerContext context1 = TaskSchedulerContext.empty();
        TaskSchedulerContext context2 = TaskSchedulerContext.empty();

        // Then
        assertThat(context1.hashCode()).isEqualTo(context2.hashCode());
      }

      @Test
      @DisplayName("hashCode() 在多次调用中应该保持一致")
      void hashCodeShouldBeConsistent() {
        // Given
        TaskSchedulerContext context = new TaskSchedulerContext("trace-123");

        // When
        int hash1 = context.hashCode();
        int hash2 = context.hashCode();

        // Then
        assertThat(hash1).isEqualTo(hash2);
      }
    }

    @Nested
    @DisplayName("toString() 测试")
    class ToStringTests {

      @Test
      @DisplayName("toString() 应该包含字段名和值")
      void toStringShouldContainFieldNameAndValue() {
        // Given
        TaskSchedulerContext context = new TaskSchedulerContext("trace-12345");

        // When
        String result = context.toString();

        // Then
        assertThat(result)
            .contains("TaskSchedulerContext")
            .contains("correlationId")
            .contains("trace-12345");
      }

      @Test
      @DisplayName("空上下文的 toString() 应该包含 null")
      void toStringOfEmptyContextShouldContainNull() {
        // Given
        TaskSchedulerContext context = TaskSchedulerContext.empty();

        // When
        String result = context.toString();

        // Then
        assertThat(result)
            .contains("TaskSchedulerContext")
            .contains("correlationId")
            .contains("null");
      }

      @Test
      @DisplayName("toString() 在多次调用中应该保持一致")
      void toStringShouldBeConsistent() {
        // Given
        TaskSchedulerContext context = new TaskSchedulerContext("trace-123");

        // When
        String result1 = context.toString();
        String result2 = context.toString();

        // Then
        assertThat(result1).isEqualTo(result2);
      }
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class EdgeCaseTests {

    @Test
    @DisplayName("应该处理非常长的 correlationId")
    void shouldHandleVeryLongCorrelationId() {
      // Given
      String longCorrelationId = "trace-" + "x".repeat(1000);

      // When
      TaskSchedulerContext context = new TaskSchedulerContext(longCorrelationId);

      // Then
      assertThat(context.correlationId()).isEqualTo(longCorrelationId);
      assertThat(context.correlationId()).hasSize(1006);
    }

    @Test
    @DisplayName("应该处理包含特殊字符的 correlationId")
    void shouldHandleSpecialCharactersInCorrelationId() {
      // Given
      String specialChars = "trace-!@#$%^&*()_+-=[]{}|;':\",./<>?";

      // When
      TaskSchedulerContext context = new TaskSchedulerContext(specialChars);

      // Then
      assertThat(context.correlationId()).isEqualTo(specialChars);
    }

    @Test
    @DisplayName("应该处理包含 Unicode 字符的 correlationId")
    void shouldHandleUnicodeCharactersInCorrelationId() {
      // Given
      String unicodeId = "trace-追踪-トレース-🔍";

      // When
      TaskSchedulerContext context = new TaskSchedulerContext(unicodeId);

      // Then
      assertThat(context.correlationId()).isEqualTo(unicodeId);
    }

    @Test
    @DisplayName("应该处理包含换行符的 correlationId")
    void shouldHandleNewlineCharactersInCorrelationId() {
      // Given
      String multilineId = "trace\n12345";

      // When
      TaskSchedulerContext context = new TaskSchedulerContext(multilineId);

      // Then
      assertThat(context.correlationId()).isEqualTo(multilineId);
    }

    @Test
    @DisplayName("应该处理单字符 correlationId")
    void shouldHandleSingleCharacterCorrelationId() {
      // Given
      String singleChar = "x";

      // When
      TaskSchedulerContext context = new TaskSchedulerContext(singleChar);

      // Then
      assertThat(context.correlationId()).isEqualTo(singleChar);
    }

    @Test
    @DisplayName("应该处理只包含空格的 correlationId")
    void shouldHandleWhitespaceOnlyCorrelationId() {
      // Given
      String whitespace = "     ";

      // When
      TaskSchedulerContext context = new TaskSchedulerContext(whitespace);

      // Then
      assertThat(context.correlationId()).isEqualTo(whitespace);
      assertThat(context.correlationId()).isBlank();
    }
  }

  @Nested
  @DisplayName("不可变性测试")
  class ImmutabilityTests {

    @Test
    @DisplayName("派生操作不应该修改原实例")
    void derivationShouldNotModifyOriginal() {
      // Given
      TaskSchedulerContext original = new TaskSchedulerContext("original-trace");
      String originalValue = original.correlationId();

      // When
      original.withCorrelation("new-trace");

      // Then
      assertThat(original.correlationId()).isEqualTo(originalValue);
    }

    @Test
    @DisplayName("多次派生应该保持原实例不变")
    void multipleDerivationsShouldKeepOriginalUnchanged() {
      // Given
      TaskSchedulerContext original = new TaskSchedulerContext("original-trace");
      String originalValue = original.correlationId();

      // When
      original.withCorrelation("trace-1");
      original.withCorrelation("trace-2");
      original.withCorrelation("trace-3");

      // Then
      assertThat(original.correlationId()).isEqualTo(originalValue);
    }
  }

  @Nested
  @DisplayName("使用场景测试")
  class UseCaseTests {

    @Test
    @DisplayName("场景：分布式追踪 - 创建根追踪上下文")
    void useCaseCreateRootTraceContext() {
      // Given: 收到外部请求，生成根追踪 ID
      String rootTraceId = "root-trace-abc123";

      // When
      TaskSchedulerContext context = new TaskSchedulerContext(rootTraceId);

      // Then
      assertThat(context.correlationId()).isEqualTo(rootTraceId);
    }

    @Test
    @DisplayName("场景：分布式追踪 - 传递追踪上下文到下游服务")
    void useCasePassTraceContextToDownstream() {
      // Given: 从上游接收追踪上下文
      TaskSchedulerContext upstreamContext = new TaskSchedulerContext("parent-trace-xyz");

      // When: 派生子追踪上下文
      TaskSchedulerContext downstreamContext = upstreamContext.withCorrelation("child-trace-xyz");

      // Then
      assertThat(downstreamContext.correlationId()).isEqualTo("child-trace-xyz");
      assertThat(upstreamContext.correlationId()).isEqualTo("parent-trace-xyz"); // 父上下文未变
    }

    @Test
    @DisplayName("场景：内部任务调度 - 从空上下文开始")
    void useCaseInternalTaskWithoutExternalTrace() {
      // Given: 内部定时任务，无外部追踪
      TaskSchedulerContext context = TaskSchedulerContext.empty();

      // When: 生成内部追踪 ID
      TaskSchedulerContext traced = context.withCorrelation("internal-task-12345");

      // Then
      assertThat(traced.correlationId()).isEqualTo("internal-task-12345");
    }
  }
}
