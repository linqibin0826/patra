package dev.linqibin.patra.ingest.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.ingest.domain.model.enums.SliceStatus;
import dev.linqibin.patra.ingest.domain.model.enums.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SliceStatusCalculator 单元测试")
class SliceStatusCalculatorTest {

  @Nested
  @DisplayName("calculate() 方法")
  class CalculateMethod {

    @Nested
    @DisplayName("边界条件")
    class BoundaryConditions {

      @Test
      @DisplayName("当任务状态为 null 时，应抛出 IllegalArgumentException")
      void shouldThrowException_whenTaskStatusIsNull() {
        // When & Then
        assertThatThrownBy(() -> SliceStatusCalculator.calculate(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Task status cannot be null");
      }
    }

    @Nested
    @DisplayName("任务状态映射到切片状态")
    class TaskToSliceStatusMapping {

      @Test
      @DisplayName("当任务状态为 PENDING 时，应映射为切片状态 PENDING")
      void shouldMapToPending_whenTaskStatusIsPending() {
        // When
        SliceStatus result = SliceStatusCalculator.calculate(TaskStatus.PENDING);

        // Then
        assertThat(result).isEqualTo(SliceStatus.PENDING);
      }

      @Test
      @DisplayName("当任务状态为 QUEUED 时，应映射为切片状态 PENDING")
      void shouldMapToPending_whenTaskStatusIsQueued() {
        // When
        SliceStatus result = SliceStatusCalculator.calculate(TaskStatus.QUEUED);

        // Then
        assertThat(result).isEqualTo(SliceStatus.PENDING);
      }

      @Test
      @DisplayName("当任务状态为 RUNNING 时，应映射为切片状态 ASSIGNED")
      void shouldMapToAssigned_whenTaskStatusIsRunning() {
        // When
        SliceStatus result = SliceStatusCalculator.calculate(TaskStatus.RUNNING);

        // Then
        assertThat(result).isEqualTo(SliceStatus.ASSIGNED);
      }

      @Test
      @DisplayName("当任务状态为 SUCCEEDED 时，应映射为切片状态 FINISHED")
      void shouldMapToFinished_whenTaskStatusIsSucceeded() {
        // When
        SliceStatus result = SliceStatusCalculator.calculate(TaskStatus.SUCCEEDED);

        // Then
        assertThat(result).isEqualTo(SliceStatus.FINISHED);
      }

      @Test
      @DisplayName("当任务状态为 FAILED 时，应映射为切片状态 FINISHED")
      void shouldMapToFinished_whenTaskStatusIsFailed() {
        // When
        SliceStatus result = SliceStatusCalculator.calculate(TaskStatus.FAILED);

        // Then
        assertThat(result).isEqualTo(SliceStatus.FINISHED);
      }
    }

    @Nested
    @DisplayName("验证所有任务状态都有对应映射")
    class ValidateAllStatusesMapped {

      @Test
      @DisplayName("验证所有任务状态枚举值都能成功映射")
      void shouldMapAllTaskStatusesSuccessfully() {
        // Given & When & Then - 遍历所有任务状态，确保没有遗漏
        for (TaskStatus taskStatus : TaskStatus.values()) {
          SliceStatus result = SliceStatusCalculator.calculate(taskStatus);
          assertThat(result).isNotNull();
        }
      }
    }
  }

  @Nested
  @DisplayName("isTerminal() 方法")
  class IsTerminalMethod {

    @Test
    @DisplayName("SUCCEEDED 状态应被判断为终态")
    void shouldReturnTrue_forSucceededStatus() {
      // When
      boolean result = SliceStatusCalculator.isTerminal(TaskStatus.SUCCEEDED);

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("FAILED 状态应被判断为终态")
    void shouldReturnTrue_forFailedStatus() {
      // When
      boolean result = SliceStatusCalculator.isTerminal(TaskStatus.FAILED);

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("PENDING 状态应被判断为非终态")
    void shouldReturnFalse_forPendingStatus() {
      // When
      boolean result = SliceStatusCalculator.isTerminal(TaskStatus.PENDING);

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("QUEUED 状态应被判断为非终态")
    void shouldReturnFalse_forQueuedStatus() {
      // When
      boolean result = SliceStatusCalculator.isTerminal(TaskStatus.QUEUED);

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("RUNNING 状态应被判断为非终态")
    void shouldReturnFalse_forRunningStatus() {
      // When
      boolean result = SliceStatusCalculator.isTerminal(TaskStatus.RUNNING);

      // Then
      assertThat(result).isFalse();
    }
  }
}
