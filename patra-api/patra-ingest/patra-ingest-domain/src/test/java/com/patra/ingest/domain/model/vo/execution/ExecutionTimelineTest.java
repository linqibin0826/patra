package com.patra.ingest.domain.model.vo.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExecutionTimeline 值对象单元测试")
class ExecutionTimelineTest {

  private static final Instant NOW = Instant.parse("2025-01-05T10:00:00Z");
  private static final Instant LATER = Instant.parse("2025-01-05T11:00:00Z");
  private static final Instant MUCH_LATER = Instant.parse("2025-01-05T12:00:00Z");

  @Nested
  @DisplayName("构造器测试")
  class ConstructorTests {

    @Test
    @DisplayName("应该使用有效的开始和结束时间创建时间线")
    void shouldCreateTimelineWithValidStartAndFinishTimes() {
      // When
      ExecutionTimeline timeline = new ExecutionTimeline(NOW, LATER);

      // Then
      assertThat(timeline.startedAt()).isEqualTo(NOW);
      assertThat(timeline.finishedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("应该允许创建只有开始时间的时间线")
    void shouldAllowTimelineWithOnlyStartTime() {
      // When
      ExecutionTimeline timeline = new ExecutionTimeline(NOW, null);

      // Then
      assertThat(timeline.startedAt()).isEqualTo(NOW);
      assertThat(timeline.finishedAt()).isNull();
    }

    @Test
    @DisplayName("应该允许创建两个时间都为 null 的时间线")
    void shouldAllowTimelineWithBothTimesNull() {
      // When
      ExecutionTimeline timeline = new ExecutionTimeline(null, null);

      // Then
      assertThat(timeline.startedAt()).isNull();
      assertThat(timeline.finishedAt()).isNull();
    }

    @Test
    @DisplayName("应该允许开始时间和结束时间相同")
    void shouldAllowStartAndFinishTimesEqual() {
      // When
      ExecutionTimeline timeline = new ExecutionTimeline(NOW, NOW);

      // Then
      assertThat(timeline.startedAt()).isEqualTo(NOW);
      assertThat(timeline.finishedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("当结束时间早于开始时间时应该抛出异常")
    void shouldThrowExceptionWhenFinishTimeBeforeStartTime() {
      // When & Then
      assertThatThrownBy(() -> new ExecutionTimeline(LATER, NOW))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("finish time must not be earlier than start time");
    }

    @Test
    @DisplayName("当开始时间为 null 但结束时间不为 null 时应该允许")
    void shouldAllowNullStartTimeWithNonNullFinishTime() {
      // When
      ExecutionTimeline timeline = new ExecutionTimeline(null, NOW);

      // Then
      assertThat(timeline.startedAt()).isNull();
      assertThat(timeline.finishedAt()).isEqualTo(NOW);
    }
  }

  @Nested
  @DisplayName("静态工厂方法测试")
  class FactoryMethodTests {

    @Test
    @DisplayName("empty() 应该返回开始和结束时间都为 null 的时间线")
    void emptyShouldReturnTimelineWithBothTimesNull() {
      // When
      ExecutionTimeline empty = ExecutionTimeline.empty();

      // Then
      assertThat(empty.startedAt()).isNull();
      assertThat(empty.finishedAt()).isNull();
    }

    @Test
    @DisplayName("empty() 应该返回等同于 null 构造的对象")
    void emptyShouldReturnEquivalentToNullConstructor() {
      // When
      ExecutionTimeline empty = ExecutionTimeline.empty();
      ExecutionTimeline nullTimeline = new ExecutionTimeline(null, null);

      // Then
      assertThat(empty).isEqualTo(nullTimeline);
    }
  }

  @Nested
  @DisplayName("hasStarted() 方法测试")
  class HasStartedMethodTests {

    @Test
    @DisplayName("当开始时间不为 null 时应该返回 true")
    void shouldReturnTrueWhenStartTimeIsNotNull() {
      // Given
      ExecutionTimeline timeline = new ExecutionTimeline(NOW, null);

      // When
      boolean hasStarted = timeline.hasStarted();

      // Then
      assertThat(hasStarted).isTrue();
    }

    @Test
    @DisplayName("当开始时间为 null 时应该返回 false")
    void shouldReturnFalseWhenStartTimeIsNull() {
      // Given
      ExecutionTimeline timeline = ExecutionTimeline.empty();

      // When
      boolean hasStarted = timeline.hasStarted();

      // Then
      assertThat(hasStarted).isFalse();
    }

    @Test
    @DisplayName("当开始和结束时间都存在时应该返回 true")
    void shouldReturnTrueWhenBothTimesExist() {
      // Given
      ExecutionTimeline timeline = new ExecutionTimeline(NOW, LATER);

      // When
      boolean hasStarted = timeline.hasStarted();

      // Then
      assertThat(hasStarted).isTrue();
    }
  }

  @Nested
  @DisplayName("hasFinished() 方法测试")
  class HasFinishedMethodTests {

    @Test
    @DisplayName("当结束时间不为 null 时应该返回 true")
    void shouldReturnTrueWhenFinishTimeIsNotNull() {
      // Given
      ExecutionTimeline timeline = new ExecutionTimeline(NOW, LATER);

      // When
      boolean hasFinished = timeline.hasFinished();

      // Then
      assertThat(hasFinished).isTrue();
    }

    @Test
    @DisplayName("当结束时间为 null 时应该返回 false")
    void shouldReturnFalseWhenFinishTimeIsNull() {
      // Given
      ExecutionTimeline timeline = new ExecutionTimeline(NOW, null);

      // When
      boolean hasFinished = timeline.hasFinished();

      // Then
      assertThat(hasFinished).isFalse();
    }

    @Test
    @DisplayName("当两个时间都为 null 时应该返回 false")
    void shouldReturnFalseWhenBothTimesAreNull() {
      // Given
      ExecutionTimeline timeline = ExecutionTimeline.empty();

      // When
      boolean hasFinished = timeline.hasFinished();

      // Then
      assertThat(hasFinished).isFalse();
    }
  }

  @Nested
  @DisplayName("onStart() 方法测试")
  class OnStartMethodTests {

    @Test
    @DisplayName("应该记录执行开始时间")
    void shouldRecordExecutionStartTime() {
      // Given
      ExecutionTimeline timeline = ExecutionTimeline.empty();

      // When
      ExecutionTimeline started = timeline.onStart(NOW);

      // Then
      assertThat(started.startedAt()).isEqualTo(NOW);
      assertThat(started.finishedAt()).isNull();
    }

    @Test
    @DisplayName("onStart() 应该是不可变操作")
    void onStartShouldBeImmutable() {
      // Given
      ExecutionTimeline original = ExecutionTimeline.empty();

      // When
      ExecutionTimeline started = original.onStart(NOW);

      // Then
      assertThat(original.startedAt()).isNull(); // 原始对象未改变
      assertThat(started.startedAt()).isEqualTo(NOW); // 新对象包含开始时间
    }

    @Test
    @DisplayName("当开始时间为 null 时应该抛出异常")
    void shouldThrowExceptionWhenStartTimeIsNull() {
      // Given
      ExecutionTimeline timeline = ExecutionTimeline.empty();

      // When & Then
      assertThatThrownBy(() -> timeline.onStart(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("start time must not be null");
    }

    @Test
    @DisplayName("当已经开始时应该返回原有的开始时间")
    void shouldReturnCurrentStartTimeWhenAlreadyStarted() {
      // Given
      ExecutionTimeline timeline = new ExecutionTimeline(NOW, null);

      // When
      ExecutionTimeline restarted = timeline.onStart(LATER);

      // Then
      assertThat(restarted.startedAt()).isEqualTo(NOW); // 保持原有开始时间
    }

    @Test
    @DisplayName("当已经开始且已完成时应该保持原有时间线")
    void shouldReturnCurrentTimelineWhenAlreadyStartedAndFinished() {
      // Given
      ExecutionTimeline timeline = new ExecutionTimeline(NOW, LATER);

      // When
      ExecutionTimeline restarted = timeline.onStart(MUCH_LATER);

      // Then
      assertThat(restarted.startedAt()).isEqualTo(NOW);
      assertThat(restarted.finishedAt()).isEqualTo(LATER);
    }
  }

  @Nested
  @DisplayName("onFinish() 方法测试")
  class OnFinishMethodTests {

    @Test
    @DisplayName("应该记录执行结束时间")
    void shouldRecordExecutionFinishTime() {
      // Given
      ExecutionTimeline timeline = new ExecutionTimeline(NOW, null);

      // When
      ExecutionTimeline finished = timeline.onFinish(LATER);

      // Then
      assertThat(finished.startedAt()).isEqualTo(NOW);
      assertThat(finished.finishedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("onFinish() 应该是不可变操作")
    void onFinishShouldBeImmutable() {
      // Given
      ExecutionTimeline original = new ExecutionTimeline(NOW, null);

      // When
      ExecutionTimeline finished = original.onFinish(LATER);

      // Then
      assertThat(original.finishedAt()).isNull(); // 原始对象未改变
      assertThat(finished.finishedAt()).isEqualTo(LATER); // 新对象包含结束时间
    }

    @Test
    @DisplayName("当结束时间为 null 时应该抛出异常")
    void shouldThrowExceptionWhenFinishTimeIsNull() {
      // Given
      ExecutionTimeline timeline = new ExecutionTimeline(NOW, null);

      // When & Then
      assertThatThrownBy(() -> timeline.onFinish(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("finish time must not be null");
    }

    @Test
    @DisplayName("当任务未开始时应该抛出异常")
    void shouldThrowExceptionWhenTaskNotStarted() {
      // Given
      ExecutionTimeline timeline = ExecutionTimeline.empty();

      // When & Then
      assertThatThrownBy(() -> timeline.onFinish(NOW))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Cannot finish a task that has not started");
    }

    @Test
    @DisplayName("应该允许结束时间与开始时间相同")
    void shouldAllowFinishTimeEqualToStartTime() {
      // Given
      ExecutionTimeline timeline = new ExecutionTimeline(NOW, null);

      // When
      ExecutionTimeline finished = timeline.onFinish(NOW);

      // Then
      assertThat(finished.startedAt()).isEqualTo(NOW);
      assertThat(finished.finishedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("当结束时间早于开始时间时应该抛出异常")
    void shouldThrowExceptionWhenFinishTimeBeforeStartTime() {
      // Given
      ExecutionTimeline timeline = new ExecutionTimeline(LATER, null);

      // When & Then
      assertThatThrownBy(() -> timeline.onFinish(NOW))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("finish time must not be earlier than start time");
    }

    @Test
    @DisplayName("应该允许更新已有的结束时间")
    void shouldAllowUpdatingExistingFinishTime() {
      // Given
      ExecutionTimeline timeline = new ExecutionTimeline(NOW, LATER);

      // When
      ExecutionTimeline updated = timeline.onFinish(MUCH_LATER);

      // Then
      assertThat(updated.finishedAt()).isEqualTo(MUCH_LATER);
    }
  }

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemanticsTests {

    @Test
    @DisplayName("相同时间的实例应该相等")
    void instancesWithSameTimesShouldBeEqual() {
      // Given
      ExecutionTimeline timeline1 = new ExecutionTimeline(NOW, LATER);
      ExecutionTimeline timeline2 = new ExecutionTimeline(NOW, LATER);

      // Then
      assertThat(timeline1).isEqualTo(timeline2);
      assertThat(timeline1.hashCode()).isEqualTo(timeline2.hashCode());
    }

    @Test
    @DisplayName("不同时间的实例应该不相等")
    void instancesWithDifferentTimesShouldNotBeEqual() {
      // Given
      ExecutionTimeline timeline1 = new ExecutionTimeline(NOW, LATER);
      ExecutionTimeline timeline2 = new ExecutionTimeline(NOW, MUCH_LATER);

      // Then
      assertThat(timeline1).isNotEqualTo(timeline2);
    }

    @Test
    @DisplayName("两个空时间线应该相等")
    void twoEmptyTimelinesShouldBeEqual() {
      // Given
      ExecutionTimeline empty1 = ExecutionTimeline.empty();
      ExecutionTimeline empty2 = ExecutionTimeline.empty();

      // Then
      assertThat(empty1).isEqualTo(empty2);
      assertThat(empty1.hashCode()).isEqualTo(empty2.hashCode());
    }

    @Test
    @DisplayName("toString() 应该包含时间信息")
    void toStringShouldContainTimeInformation() {
      // Given
      ExecutionTimeline timeline = new ExecutionTimeline(NOW, LATER);

      // When
      String result = timeline.toString();

      // Then
      assertThat(result)
          .contains("ExecutionTimeline")
          .contains(NOW.toString())
          .contains(LATER.toString());
    }

    @Test
    @DisplayName("应该支持作为 Map 的键")
    void shouldWorkAsMapKey() {
      // Given
      var map = new java.util.HashMap<ExecutionTimeline, String>();
      ExecutionTimeline key1 = new ExecutionTimeline(NOW, LATER);
      ExecutionTimeline key2 = new ExecutionTimeline(NOW, LATER);

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
    @DisplayName("应该处理非常早的时间戳")
    void shouldHandleVeryEarlyTimestamps() {
      // Given
      Instant veryEarly = Instant.parse("1970-01-01T00:00:00Z");
      Instant early = Instant.parse("1970-01-01T00:00:01Z");

      // When
      ExecutionTimeline timeline = new ExecutionTimeline(veryEarly, early);

      // Then
      assertThat(timeline.startedAt()).isEqualTo(veryEarly);
      assertThat(timeline.finishedAt()).isEqualTo(early);
    }

    @Test
    @DisplayName("应该处理非常远的未来时间戳")
    void shouldHandleVeryFarFutureTimestamps() {
      // Given
      Instant farFuture = Instant.parse("2999-12-31T23:59:59Z");
      Instant furtherFuture = Instant.parse("3000-01-01T00:00:00Z");

      // When
      ExecutionTimeline timeline = new ExecutionTimeline(farFuture, furtherFuture);

      // Then
      assertThat(timeline.startedAt()).isEqualTo(farFuture);
      assertThat(timeline.finishedAt()).isEqualTo(furtherFuture);
    }

    @Test
    @DisplayName("应该处理毫秒级时间差")
    void shouldHandleMillisecondDifferences() {
      // Given
      Instant start = Instant.parse("2025-01-05T10:00:00.000Z");
      Instant finish = Instant.parse("2025-01-05T10:00:00.001Z");

      // When
      ExecutionTimeline timeline = new ExecutionTimeline(start, finish);

      // Then
      assertThat(timeline.startedAt()).isEqualTo(start);
      assertThat(timeline.finishedAt()).isEqualTo(finish);
    }

    @Test
    @DisplayName("应该处理纳秒级时间差")
    void shouldHandleNanosecondDifferences() {
      // Given
      Instant start = Instant.parse("2025-01-05T10:00:00.000000000Z");
      Instant finish = Instant.parse("2025-01-05T10:00:00.000000001Z");

      // When
      ExecutionTimeline timeline = new ExecutionTimeline(start, finish);

      // Then
      assertThat(timeline.startedAt()).isEqualTo(start);
      assertThat(timeline.finishedAt()).isEqualTo(finish);
    }

    @Test
    @DisplayName("应该处理跨越多天的执行时间")
    void shouldHandleExecutionSpanningMultipleDays() {
      // Given
      Instant start = Instant.parse("2025-01-01T10:00:00Z");
      Instant finish = Instant.parse("2025-01-10T15:30:45Z");

      // When
      ExecutionTimeline timeline = new ExecutionTimeline(start, finish);

      // Then
      assertThat(timeline.startedAt()).isEqualTo(start);
      assertThat(timeline.finishedAt()).isEqualTo(finish);
    }
  }

  @Nested
  @DisplayName("工作流场景测试")
  class WorkflowScenarioTests {

    @Test
    @DisplayName("应该支持完整的生命周期：创建 → 开始 → 完成")
    void shouldSupportFullLifecycleCreateStartFinish() {
      // Given
      ExecutionTimeline timeline = ExecutionTimeline.empty();

      // When
      ExecutionTimeline started = timeline.onStart(NOW);
      ExecutionTimeline finished = started.onFinish(LATER);

      // Then
      assertThat(timeline.hasStarted()).isFalse();
      assertThat(timeline.hasFinished()).isFalse();

      assertThat(started.hasStarted()).isTrue();
      assertThat(started.hasFinished()).isFalse();

      assertThat(finished.hasStarted()).isTrue();
      assertThat(finished.hasFinished()).isTrue();
    }

    @Test
    @DisplayName("应该防止重复开始任务")
    void shouldPreventRestartingTask() {
      // Given
      ExecutionTimeline timeline = ExecutionTimeline.empty().onStart(NOW);

      // When
      ExecutionTimeline restarted = timeline.onStart(LATER);

      // Then
      assertThat(restarted.startedAt()).isEqualTo(NOW); // 保持原有开始时间
    }

    @Test
    @DisplayName("应该允许更新完成时间")
    void shouldAllowUpdatingFinishTime() {
      // Given
      ExecutionTimeline timeline = new ExecutionTimeline(NOW, LATER);

      // When
      ExecutionTimeline updated = timeline.onFinish(MUCH_LATER);

      // Then
      assertThat(updated.finishedAt()).isEqualTo(MUCH_LATER);
    }
  }
}
