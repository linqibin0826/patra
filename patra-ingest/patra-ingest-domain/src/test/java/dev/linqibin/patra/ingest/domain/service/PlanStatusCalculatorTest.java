package dev.linqibin.patra.ingest.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.ingest.domain.model.enums.PlanStatus;
import dev.linqibin.patra.ingest.domain.model.enums.SliceStatus;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PlanStatusCalculator 单元测试")
class PlanStatusCalculatorTest {

  @Nested
  @DisplayName("calculate() 方法")
  class CalculateMethod {

    @Nested
    @DisplayName("边界条件")
    class BoundaryConditions {

      @Test
      @DisplayName("当切片状态列表为 null 时，应抛出 IllegalArgumentException")
      void shouldThrowException_whenSliceStatusesIsNull() {
        // When & Then
        assertThatThrownBy(() -> PlanStatusCalculator.calculate(null, PlanStatus.READY))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Slice statuses list cannot be null");
      }

      @Test
      @DisplayName("当切片状态列表为空时，应保持当前状态")
      void shouldKeepCurrentStatus_whenSliceStatusesIsEmpty() {
        // Given
        List<SliceStatus> emptyList = Collections.emptyList();
        PlanStatus currentStatus = PlanStatus.READY;

        // When
        PlanStatus result = PlanStatusCalculator.calculate(emptyList, currentStatus);

        // Then
        assertThat(result).isEqualTo(currentStatus);
      }
    }

    @Nested
    @DisplayName("存在进行中的切片")
    class HasInProgressSlices {

      @Test
      @DisplayName("当所有切片都是 PENDING 时，应保持当前状态")
      void shouldKeepCurrentStatus_whenAllSlicesArePending() {
        // Given
        List<SliceStatus> sliceStatuses = List.of(SliceStatus.PENDING, SliceStatus.PENDING);
        PlanStatus currentStatus = PlanStatus.READY;

        // When
        PlanStatus result = PlanStatusCalculator.calculate(sliceStatuses, currentStatus);

        // Then
        assertThat(result).isEqualTo(PlanStatus.READY);
      }

      @Test
      @DisplayName("当所有切片都是 ASSIGNED 时，应保持当前状态")
      void shouldKeepCurrentStatus_whenAllSlicesAreAssigned() {
        // Given
        List<SliceStatus> sliceStatuses = List.of(SliceStatus.ASSIGNED, SliceStatus.ASSIGNED);
        PlanStatus currentStatus = PlanStatus.READY;

        // When
        PlanStatus result = PlanStatusCalculator.calculate(sliceStatuses, currentStatus);

        // Then
        assertThat(result).isEqualTo(PlanStatus.READY);
      }

      @Test
      @DisplayName("当部分切片是 PENDING，部分是 FINISHED 时，应保持当前状态")
      void shouldKeepCurrentStatus_whenMixedPendingAndFinished() {
        // Given
        List<SliceStatus> sliceStatuses =
            List.of(SliceStatus.PENDING, SliceStatus.FINISHED, SliceStatus.FINISHED);
        PlanStatus currentStatus = PlanStatus.READY;

        // When
        PlanStatus result = PlanStatusCalculator.calculate(sliceStatuses, currentStatus);

        // Then
        assertThat(result).isEqualTo(PlanStatus.READY);
      }

      @Test
      @DisplayName("当部分切片是 ASSIGNED，部分是 FINISHED 时，应保持当前状态")
      void shouldKeepCurrentStatus_whenMixedAssignedAndFinished() {
        // Given
        List<SliceStatus> sliceStatuses =
            List.of(SliceStatus.ASSIGNED, SliceStatus.FINISHED, SliceStatus.FINISHED);
        PlanStatus currentStatus = PlanStatus.READY;

        // When
        PlanStatus result = PlanStatusCalculator.calculate(sliceStatuses, currentStatus);

        // Then
        assertThat(result).isEqualTo(PlanStatus.READY);
      }

      @Test
      @DisplayName("当存在 PENDING 和 ASSIGNED 混合时，应保持当前状态")
      void shouldKeepCurrentStatus_whenMixedPendingAssignedAndFinished() {
        // Given
        List<SliceStatus> sliceStatuses =
            List.of(
                SliceStatus.PENDING,
                SliceStatus.ASSIGNED,
                SliceStatus.FINISHED,
                SliceStatus.FINISHED);
        PlanStatus currentStatus = PlanStatus.READY;

        // When
        PlanStatus result = PlanStatusCalculator.calculate(sliceStatuses, currentStatus);

        // Then
        assertThat(result).isEqualTo(PlanStatus.READY);
      }
    }

    @Nested
    @DisplayName("所有切片已完成")
    class AllSlicesFinished {

      @Test
      @DisplayName("当所有切片都是 FINISHED 时，应转换为 ARCHIVED 状态")
      void shouldTransitionToArchived_whenAllSlicesAreFinished() {
        // Given
        List<SliceStatus> sliceStatuses =
            List.of(SliceStatus.FINISHED, SliceStatus.FINISHED, SliceStatus.FINISHED);
        PlanStatus currentStatus = PlanStatus.READY;

        // When
        PlanStatus result = PlanStatusCalculator.calculate(sliceStatuses, currentStatus);

        // Then
        assertThat(result).isEqualTo(PlanStatus.ARCHIVED);
      }

      @Test
      @DisplayName("当仅有一个切片且为 FINISHED 时，应转换为 ARCHIVED 状态")
      void shouldTransitionToArchived_whenSingleSliceIsFinished() {
        // Given
        List<SliceStatus> sliceStatuses = List.of(SliceStatus.FINISHED);
        PlanStatus currentStatus = PlanStatus.READY;

        // When
        PlanStatus result = PlanStatusCalculator.calculate(sliceStatuses, currentStatus);

        // Then
        assertThat(result).isEqualTo(PlanStatus.ARCHIVED);
      }
    }

    @Nested
    @DisplayName("不同初始状态的转换")
    class DifferentInitialStatuses {

      @Test
      @DisplayName("当计划当前状态为 SLICING，且所有切片已完成时，应转换为 ARCHIVED")
      void shouldTransitionToArchived_whenCurrentStatusIsSlicing() {
        // Given
        List<SliceStatus> sliceStatuses = List.of(SliceStatus.FINISHED, SliceStatus.FINISHED);
        PlanStatus currentStatus = PlanStatus.SLICING;

        // When
        PlanStatus result = PlanStatusCalculator.calculate(sliceStatuses, currentStatus);

        // Then
        assertThat(result).isEqualTo(PlanStatus.ARCHIVED);
      }

      @Test
      @DisplayName("当计划当前状态为 DRAFT，且存在进行中切片时，应保持 DRAFT 状态")
      void shouldKeepDraftStatus_whenHasInProgressSlices() {
        // Given
        List<SliceStatus> sliceStatuses = List.of(SliceStatus.PENDING, SliceStatus.ASSIGNED);
        PlanStatus currentStatus = PlanStatus.DRAFT;

        // When
        PlanStatus result = PlanStatusCalculator.calculate(sliceStatuses, currentStatus);

        // Then
        assertThat(result).isEqualTo(PlanStatus.DRAFT);
      }
    }
  }

  @Nested
  @DisplayName("isTerminal() 方法")
  class IsTerminalMethod {

    @Test
    @DisplayName("FINISHED 状态应被判断为终态")
    void shouldReturnTrue_forFinishedStatus() {
      // When
      boolean result = PlanStatusCalculator.isTerminal(SliceStatus.FINISHED);

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("PENDING 状态应被判断为非终态")
    void shouldReturnFalse_forPendingStatus() {
      // When
      boolean result = PlanStatusCalculator.isTerminal(SliceStatus.PENDING);

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("ASSIGNED 状态应被判断为非终态")
    void shouldReturnFalse_forAssignedStatus() {
      // When
      boolean result = PlanStatusCalculator.isTerminal(SliceStatus.ASSIGNED);

      // Then
      assertThat(result).isFalse();
    }
  }
}
