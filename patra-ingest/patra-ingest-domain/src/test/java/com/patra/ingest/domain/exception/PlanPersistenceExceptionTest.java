package com.patra.ingest.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.ingest.domain.exception.PlanPersistenceException.Stage;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link PlanPersistenceException} 的单元测试。
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("PlanPersistenceException 单元测试")
class PlanPersistenceExceptionTest {

  @Nested
  @DisplayName("构造器测试")
  class ConstructorTests {

    @Test
    @DisplayName("应该使用 stage 和 message 构造异常")
    void shouldConstructWithStageAndMessage() {
      // Given
      Stage stage = Stage.PLAN;
      String message = "计划持久化失败";

      // When
      PlanPersistenceException exception = new PlanPersistenceException(stage, message);

      // Then
      assertThat(exception.getMessage()).isEqualTo(message);
      assertThat(exception.getStage()).isEqualTo(stage);
      assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("应该使用 stage、message 和 cause 构造异常")
    void shouldConstructWithStageMessageAndCause() {
      // Given
      Stage stage = Stage.TASK;
      String message = "任务持久化失败";
      Throwable cause = new RuntimeException("数据库连接超时");

      // When
      PlanPersistenceException exception = new PlanPersistenceException(stage, message, cause);

      // Then
      assertThat(exception.getMessage()).isEqualTo(message);
      assertThat(exception.getStage()).isEqualTo(stage);
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
      PlanPersistenceException exception =
          new PlanPersistenceException(Stage.PLAN_SLICE, "持久化失败", cause);

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
              Stage.SCHEDULE_INSTANCE,
              Stage.PLAN,
              Stage.PLAN_SLICE,
              Stage.TASK,
              Stage.TASK_RETRY);
    }

    @Test
    @DisplayName("应该为不同阶段创建异常")
    void shouldCreateExceptionForDifferentStages() {
      // Given & When & Then
      assertThat(new PlanPersistenceException(Stage.SCHEDULE_INSTANCE, "msg").getStage())
          .isEqualTo(Stage.SCHEDULE_INSTANCE);
      assertThat(new PlanPersistenceException(Stage.PLAN, "msg").getStage())
          .isEqualTo(Stage.PLAN);
      assertThat(new PlanPersistenceException(Stage.PLAN_SLICE, "msg").getStage())
          .isEqualTo(Stage.PLAN_SLICE);
      assertThat(new PlanPersistenceException(Stage.TASK, "msg").getStage()).isEqualTo(Stage.TASK);
      assertThat(new PlanPersistenceException(Stage.TASK_RETRY, "msg").getStage())
          .isEqualTo(Stage.TASK_RETRY);
    }
  }

  @Nested
  @DisplayName("ErrorTraits 测试")
  class ErrorTraitsTests {

    @Test
    @DisplayName("应该包含 DEP_UNAVAILABLE 错误特征")
    void shouldContainDepUnavailableErrorTrait() {
      // Given
      PlanPersistenceException exception =
          new PlanPersistenceException(Stage.PLAN, "持久化失败");

      // When
      Set<ErrorTrait> traits = exception.getErrorTraits();

      // Then
      assertThat(traits).containsExactly(ErrorTrait.DEP_UNAVAILABLE);
    }
  }

  @Nested
  @DisplayName("继承关系测试")
  class InheritanceTests {

    @Test
    @DisplayName("应该继承自 IngestException")
    void shouldExtendIngestException() {
      // Given
      PlanPersistenceException exception =
          new PlanPersistenceException(Stage.PLAN, "持久化失败");

      // When & Then
      assertThat(exception).isInstanceOf(IngestException.class);
    }

    @Test
    @DisplayName("应该实现 HasErrorTraits 接口")
    void shouldImplementHasErrorTraits() {
      // Given
      PlanPersistenceException exception =
          new PlanPersistenceException(Stage.PLAN, "持久化失败");

      // When & Then
      assertThat(exception)
          .isInstanceOf(com.patra.common.error.trait.HasErrorTraits.class);
    }
  }
}
