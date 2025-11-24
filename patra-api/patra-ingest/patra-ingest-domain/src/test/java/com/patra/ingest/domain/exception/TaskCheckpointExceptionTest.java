package com.patra.ingest.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.StandardErrorTrait;
import com.patra.ingest.domain.exception.TaskCheckpointException.Type;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link TaskCheckpointException} 的单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("TaskCheckpointException 单元测试")
class TaskCheckpointExceptionTest {

  @Nested
  @DisplayName("构造器测试")
  class ConstructorTests {

    @Test
    @DisplayName("应该使用 type、message 和 cause 构造异常")
    void shouldConstructWithTypeMessageAndCause() {
      // Given
      Type type = Type.PARSE;
      String message = "检查点解析失败";
      Throwable cause = new RuntimeException("JSON 格式错误");

      // When
      TaskCheckpointException exception = new TaskCheckpointException(type, message, cause);

      // Then
      assertThat(exception.getType()).isEqualTo(type);
      assertThat(exception.getMessage()).isEqualTo(message);
      assertThat(exception.getCause()).isEqualTo(cause);
    }
  }

  @Nested
  @DisplayName("异常链测试")
  class ExceptionChainTests {

    @Test
    @DisplayName("应该正确传播异常链")
    void shouldPropagateExceptionChain() {
      // Given
      RuntimeException rootCause = new RuntimeException("根本原因");
      IllegalStateException cause = new IllegalStateException("中间原因", rootCause);
      TaskCheckpointException exception =
          new TaskCheckpointException(Type.SERIALIZE, "序列化失败", cause);

      // When & Then
      assertThat(exception.getCause()).isEqualTo(cause);
      assertThat(exception.getCause().getCause()).isEqualTo(rootCause);
    }
  }

  @Nested
  @DisplayName("Type 枚举测试")
  class TypeEnumTests {

    @Test
    @DisplayName("应该支持所有预定义的类型")
    void shouldSupportAllPredefinedTypes() {
      // Given & When
      Type[] types = Type.values();

      // Then
      assertThat(types).containsExactlyInAnyOrder(Type.PARSE, Type.SERIALIZE);
    }

    @Test
    @DisplayName("应该为不同类型创建异常")
    void shouldCreateExceptionForDifferentTypes() {
      // Given & When & Then
      assertThat(new TaskCheckpointException(Type.PARSE, "msg", new RuntimeException()).getType())
          .isEqualTo(Type.PARSE);
      assertThat(
              new TaskCheckpointException(Type.SERIALIZE, "msg", new RuntimeException()).getType())
          .isEqualTo(Type.SERIALIZE);
    }
  }

  @Nested
  @DisplayName("ErrorTraits 测试")
  class ErrorTraitsTests {

    @Test
    @DisplayName("应该包含 RULE_VIOLATION 错误特征")
    void shouldContainRuleViolationErrorTrait() {
      // Given
      TaskCheckpointException exception =
          new TaskCheckpointException(Type.PARSE, "解析失败", new RuntimeException());

      // When
      Set<ErrorTrait> traits = exception.getErrorTraits();

      // Then
      assertThat(traits).containsExactly(StandardErrorTrait.RULE_VIOLATION);
    }
  }

  @Nested
  @DisplayName("继承关系测试")
  class InheritanceTests {

    @Test
    @DisplayName("应该继承自 IngestException")
    void shouldExtendIngestException() {
      // Given
      TaskCheckpointException exception =
          new TaskCheckpointException(Type.PARSE, "解析失败", new RuntimeException());

      // When & Then
      assertThat(exception).isInstanceOf(IngestException.class);
    }

    @Test
    @DisplayName("应该实现 HasErrorTraits 接口")
    void shouldImplementHasErrorTraits() {
      // Given
      TaskCheckpointException exception =
          new TaskCheckpointException(Type.PARSE, "解析失败", new RuntimeException());

      // When & Then
      assertThat(exception).isInstanceOf(com.patra.common.error.trait.HasErrorTraits.class);
    }
  }
}
