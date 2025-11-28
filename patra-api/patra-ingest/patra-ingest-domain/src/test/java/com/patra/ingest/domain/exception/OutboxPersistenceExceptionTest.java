package com.patra.ingest.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.StandardErrorTrait;
import com.patra.ingest.domain.exception.OutboxPersistenceException.Stage;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link OutboxPersistenceException} 的单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("OutboxPersistenceException 单元测试")
class OutboxPersistenceExceptionTest {

  @Nested
  @DisplayName("构造器测试")
  class ConstructorTests {

    @Test
    @DisplayName("应该使用 stage 和 message 构造异常")
    void shouldConstructWithStageAndMessage() {
      // Given
      Stage stage = Stage.MARK_PUBLISHED;
      String message = "标记为已发布状态失败";

      // When
      OutboxPersistenceException exception = new OutboxPersistenceException(stage, message);

      // Then
      assertThat(exception.getStage()).isEqualTo(stage);
      assertThat(exception.getMessage()).isEqualTo(message);
      assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("应该使用 stage、message 和 cause 构造异常")
    void shouldConstructWithStageMessageAndCause() {
      // Given
      Stage stage = Stage.MARK_RETRY;
      String message = "标记为重试状态失败";
      Throwable cause = new RuntimeException("并发冲突");

      // When
      OutboxPersistenceException exception = new OutboxPersistenceException(stage, message, cause);

      // Then
      assertThat(exception.getStage()).isEqualTo(stage);
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
      OutboxPersistenceException exception =
          new OutboxPersistenceException(Stage.MARK_DEAD, "标记为死信失败", cause);

      // When & Then
      assertThat(exception.getCause()).isEqualTo(cause);
      assertThat(exception.getCause().getCause()).isEqualTo(rootCause);
    }
  }

  @Nested
  @DisplayName("Stage 枚举测试")
  class StageEnumTests {

    @Test
    @DisplayName("应该支持所有预定义的阶段")
    void shouldSupportAllPredefinedStages() {
      // Given & When
      Stage[] stages = Stage.values();

      // Then
      assertThat(stages)
          .containsExactlyInAnyOrder(
              Stage.MARK_PUBLISHED, Stage.MARK_RETRY, Stage.MARK_DEAD, Stage.BATCH_INSERT);
    }

    @Test
    @DisplayName("应该为不同阶段创建异常")
    void shouldCreateExceptionForDifferentStages() {
      // Given & When & Then
      assertThat(new OutboxPersistenceException(Stage.MARK_PUBLISHED, "msg").getStage())
          .isEqualTo(Stage.MARK_PUBLISHED);
      assertThat(new OutboxPersistenceException(Stage.MARK_RETRY, "msg").getStage())
          .isEqualTo(Stage.MARK_RETRY);
      assertThat(new OutboxPersistenceException(Stage.MARK_DEAD, "msg").getStage())
          .isEqualTo(Stage.MARK_DEAD);
    }
  }

  @Nested
  @DisplayName("ErrorTraits 测试")
  class ErrorTraitsTests {

    @Test
    @DisplayName("应该包含 CONFLICT 错误特征")
    void shouldContainConflictErrorTrait() {
      // Given
      OutboxPersistenceException exception =
          new OutboxPersistenceException(Stage.MARK_RETRY, "标记失败");

      // When
      Set<ErrorTrait> traits = exception.getErrorTraits();

      // Then
      assertThat(traits).containsExactly(StandardErrorTrait.CONFLICT);
    }
  }

  @Nested
  @DisplayName("继承关系测试")
  class InheritanceTests {

    @Test
    @DisplayName("应该继承自 IngestException")
    void shouldExtendIngestException() {
      // Given
      OutboxPersistenceException exception =
          new OutboxPersistenceException(Stage.MARK_PUBLISHED, "标记失败");

      // When & Then
      assertThat(exception).isInstanceOf(IngestException.class);
    }

    @Test
    @DisplayName("应该实现 HasErrorTraits 接口")
    void shouldImplementHasErrorTraits() {
      // Given
      OutboxPersistenceException exception =
          new OutboxPersistenceException(Stage.MARK_PUBLISHED, "标记失败");

      // When & Then
      assertThat(exception).isInstanceOf(com.patra.common.error.trait.HasErrorTraits.class);
    }
  }
}
