package com.patra.ingest.domain.model.vo;

import java.time.Instant;

/**
 * Execution timeline tracking start and end instants for a task.
 */
public record ExecutionTimeline(Instant startedAt, Instant finishedAt) {

    public ExecutionTimeline {
        if (startedAt != null && finishedAt != null && finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("finish time must not be earlier than start time");
        }
    }

    public static ExecutionTimeline empty() {
        return new ExecutionTimeline(null, null);
    }

    public boolean hasStarted() {
        return startedAt != null;
    }

    public boolean hasFinished() {
        return finishedAt != null;
    }

    public ExecutionTimeline onStart(Instant startAt) {
        if (startAt == null) {
            throw new IllegalArgumentException("start time must not be null");
        }
        if (hasStarted()) {
            return new ExecutionTimeline(startedAt, finishedAt);
        }
        return new ExecutionTimeline(startAt, finishedAt);
    }

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
