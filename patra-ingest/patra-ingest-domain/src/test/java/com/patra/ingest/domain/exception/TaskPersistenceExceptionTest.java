package com.patra.ingest.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.error.trait.ErrorTrait;
import dev.linqibin.commons.error.trait.HasErrorTraits;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link TaskPersistenceException} 的单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("TaskPersistenceException 单元测试")
class TaskPersistenceExceptionTest {

  @Nested
  @DisplayName("构造器测试")
  class ConstructorTests {

    @Test
    @DisplayName("应该使用 message 构造异常")
    void shouldConstructWithMessage() {
      // Given
      String message = "任务批量插入失败";

      // When
      TaskPersistenceException exception = new TaskPersistenceException(message);

      // Then
      assertThat(exception.getMessage()).isEqualTo(message);
      assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("应该使用 message 和 cause 构造异常")
    void shouldConstructWithMessageAndCause() {
      // Given
      String message = "TaskRunBatch 批量插入失败";
      Throwable cause = new RuntimeException("数据库连接超时");

      // When
      TaskPersistenceException exception = new TaskPersistenceException(message, cause);

      // Then
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
      TaskPersistenceException exception = new TaskPersistenceException("持久化失败", cause);

      // When & Then
      assertThat(exception.getCause()).isEqualTo(cause);
      assertThat(exception.getCause().getCause()).isEqualTo(rootCause);
    }
  }

  @Nested
  @DisplayName("ErrorTraits 测试")
  class ErrorTraitsTests {

    @Test
    @DisplayName("应该包含 DEP_UNAVAILABLE 错误特征")
    void shouldContainDepUnavailableErrorTrait() {
      // Given
      TaskPersistenceException exception = new TaskPersistenceException("持久化失败");

      // When
      Set<ErrorTrait> traits = exception.getErrorTraits();

      // Then
      assertThat(traits).containsExactly(StandardErrorTrait.DEP_UNAVAILABLE);
    }
  }

  @Nested
  @DisplayName("继承关系测试")
  class InheritanceTests {

    @Test
    @DisplayName("应该继承自 IngestException")
    void shouldExtendIngestException() {
      // Given
      TaskPersistenceException exception = new TaskPersistenceException("持久化失败");

      // When & Then
      assertThat(exception).isInstanceOf(IngestException.class);
    }

    @Test
    @DisplayName("应该实现 HasErrorTraits 接口")
    void shouldImplementHasErrorTraits() {
      // Given
      TaskPersistenceException exception = new TaskPersistenceException("持久化失败");

      // When & Then
      assertThat(exception).isInstanceOf(HasErrorTraits.class);
    }
  }
}
