package com.patra.ingest.domain.model.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ExecutionTimeline}.
 */
class ExecutionTimelineTest {

    @Test
    @DisplayName("constructor throws when end < start")
    void ctor_shouldValidateOrder() {
        Instant s = Instant.parse("2024-01-01T00:00:00Z");
        Instant f = s.minusSeconds(1);
        assertThrows(IllegalArgumentException.class, () -> new ExecutionTimeline(s, f));
    }

    @Test
    @DisplayName("empty/hasStarted/hasFinished flags")
    void emptyAndFlags() {
        ExecutionTimeline t = ExecutionTimeline.empty();
        assertFalse(t.hasStarted());
        assertFalse(t.hasFinished());
    }

    @Test
    @DisplayName("onStart normal and repeat-call behavior")
    void onStartBehavior() {
        ExecutionTimeline t = ExecutionTimeline.empty();
        Instant s = Instant.parse("2024-02-01T00:00:00Z");
        ExecutionTimeline t2 = t.onStart(s);
        assertTrue(t2.hasStarted());
        assertEquals(s, t2.startedAt());

        // Second onStart does not change startedAt
        ExecutionTimeline t3 = t2.onStart(Instant.parse("2024-02-02T00:00:00Z"));
        assertEquals(s, t3.startedAt());
    }

    @Test
    @DisplayName("onStart/onFinish parameter and state validation")
    void onStartFinishValidation() {
        ExecutionTimeline t = ExecutionTimeline.empty();
        assertThrows(IllegalArgumentException.class, () -> t.onStart(null));
        assertThrows(IllegalArgumentException.class, () -> t.onFinish(null));
        assertThrows(IllegalStateException.class, () -> t.onFinish(Instant.now()));

        // Normal completion
        Instant s = Instant.parse("2024-03-01T10:00:00Z");
        Instant f = Instant.parse("2024-03-01T12:00:00Z");
        ExecutionTimeline t2 = t.onStart(s).onFinish(f);
        assertTrue(t2.hasFinished());
        assertEquals(f, t2.finishedAt());
    }
}
