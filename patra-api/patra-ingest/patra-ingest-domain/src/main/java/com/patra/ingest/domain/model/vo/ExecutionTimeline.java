package com.patra.ingest.domain.model.vo;

import java.time.Instant;

/**
 * 任务执行时间线，记录开始与结束时刻。
 */
public record ExecutionTimeline(Instant startedAt, Instant finishedAt) {

    public ExecutionTimeline {
        if (startedAt != null && finishedAt != null && finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("结束时间不能早于开始时间");
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
            throw new IllegalArgumentException("开始时间不能为空");
        }
        if (hasStarted()) {
            return new ExecutionTimeline(startedAt, finishedAt);
        }
        return new ExecutionTimeline(startAt, finishedAt);
    }

    public ExecutionTimeline onFinish(Instant finishAt) {
        if (finishAt == null) {
            throw new IllegalArgumentException("结束时间不能为空");
        }
        if (!hasStarted()) {
            throw new IllegalStateException("未开始的任务无法结束");
        }
        return new ExecutionTimeline(startedAt, finishAt);
    }
}
