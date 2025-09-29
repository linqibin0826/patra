package com.patra.ingest.domain.model.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ExecutionTimeline} 的单元测试。
 */
class ExecutionTimelineTest {

    @Test
    @DisplayName("构造时结束<开始应抛出异常")
    void ctor_shouldValidateOrder() {
        Instant s = Instant.parse("2024-01-01T00:00:00Z");
        Instant f = s.minusSeconds(1);
        assertThrows(IllegalArgumentException.class, () -> new ExecutionTimeline(s, f));
    }

    @Test
    @DisplayName("empty/hasStarted/hasFinished 判定")
    void emptyAndFlags() {
        ExecutionTimeline t = ExecutionTimeline.empty();
        assertFalse(t.hasStarted());
        assertFalse(t.hasFinished());
    }

    @Test
    @DisplayName("onStart 正常与重复调用行为")
    void onStartBehavior() {
        ExecutionTimeline t = ExecutionTimeline.empty();
        Instant s = Instant.parse("2024-02-01T00:00:00Z");
        ExecutionTimeline t2 = t.onStart(s);
        assertTrue(t2.hasStarted());
        assertEquals(s, t2.startedAt());

        // 再次 onStart 不改变 startedAt
        ExecutionTimeline t3 = t2.onStart(Instant.parse("2024-02-02T00:00:00Z"));
        assertEquals(s, t3.startedAt());
    }

    @Test
    @DisplayName("onStart/onFinish 参数与状态校验")
    void onStartFinishValidation() {
        ExecutionTimeline t = ExecutionTimeline.empty();
        assertThrows(IllegalArgumentException.class, () -> t.onStart(null));
        assertThrows(IllegalArgumentException.class, () -> t.onFinish(null));
        assertThrows(IllegalStateException.class, () -> t.onFinish(Instant.now()));

        // 正常完成
        Instant s = Instant.parse("2024-03-01T10:00:00Z");
        Instant f = Instant.parse("2024-03-01T12:00:00Z");
        ExecutionTimeline t2 = t.onStart(s).onFinish(f);
        assertTrue(t2.hasFinished());
        assertEquals(f, t2.finishedAt());
    }
}
