package com.patra.ingest.domain.model.vo.execution;

import java.time.Instant;

/// 任务执行时间线值对象。
/// 
/// 跟踪任务的开始和结束时间点。
/// 
/// @param startedAt 任务执行开始时间
/// @param finishedAt 任务执行结束时间
/// @author linqibin
/// @since 0.1.0
public record ExecutionTimeline(Instant startedAt, Instant finishedAt) {

  public ExecutionTimeline {
    if (startedAt != null && finishedAt != null && finishedAt.isBefore(startedAt)) {
      throw new IllegalArgumentException("finish time must not be earlier than start time");
    }
  }

  /// Creates an empty timeline with no start or finish time.
/// 
/// @return empty execution timeline
  public static ExecutionTimeline empty() {
    return new ExecutionTimeline(null, null);
  }

  /// Checks whether execution has started.
/// 
/// @return true if startedAt is not null
  public boolean hasStarted() {
    return startedAt != null;
  }

  /// Checks whether execution has finished.
/// 
/// @return true if finishedAt is not null
  public boolean hasFinished() {
    return finishedAt != null;
  }

  /// Records the execution start time.
/// 
/// @param startAt execution start time
/// @return new timeline with start time set (returns current timeline if already started)
/// @throws IllegalArgumentException if startAt is null
  public ExecutionTimeline onStart(Instant startAt) {
    if (startAt == null) {
      throw new IllegalArgumentException("start time must not be null");
    }
    if (hasStarted()) {
      return new ExecutionTimeline(startedAt, finishedAt);
    }
    return new ExecutionTimeline(startAt, finishedAt);
  }

  /// Records the execution finish time.
/// 
/// @param finishAt execution finish time
/// @return new timeline with finish time set
/// @throws IllegalArgumentException if finishAt is null
/// @throws IllegalStateException if execution has not started
  public ExecutionTimeline onFinish(Instant finishAt) {
    if (finishAt == null) {
      throw new IllegalArgumentException("finish time must not be null");
    }
    if (!hasStarted()) {
      throw new IllegalStateException("Cannot finish a task that has not started");
    }
    return new ExecutionTimeline(startedAt, finishAt);
  }
}
