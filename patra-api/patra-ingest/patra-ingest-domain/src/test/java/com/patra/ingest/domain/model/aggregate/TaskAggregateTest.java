package com.patra.ingest.domain.model.aggregate;

import com.patra.common.domain.DomainEvent;
import com.patra.ingest.domain.model.event.TaskQueuedEvent;
import com.patra.ingest.domain.model.vo.ExecutionTimeline;
import com.patra.ingest.domain.model.vo.LeaseInfo;
import com.patra.ingest.domain.model.vo.TaskSchedulerContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TaskAggregate} 的单元测试。
 */
class TaskAggregateTest {

    @Test
    @DisplayName("创建默认状态与 raiseQueuedEvent 事件挂载")
    void createAndRaiseEvent() {
        TaskAggregate task = TaskAggregate.create(1L, 2L, 3L, "P", "O", "{}", "K", "EH", 1, Instant.now());
        assertNotNull(task);
        assertEquals("P", task.getProvenanceCode());
        assertEquals("O", task.getOperationCode());

        TaskQueuedEvent event = task.raiseQueuedEvent();
        List<DomainEvent> events = task.peekDomainEvents();
        assertEquals(1, events.size());
        assertSame(event, events.get(0));
    }

    @Test
    @DisplayName("状态切换与租约/时间线/上下文变换")
    void statusLeaseTimelineContext() {
        TaskAggregate task = TaskAggregate.create(1L, 2L, 3L, "P", "O", "{}", "K", "EH", 1, Instant.now());
        // running
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        task.markRunning(start, "run-1", "corr-1");
        assertTrue(task.getExecutionTimeline().hasStarted());
        assertEquals("run-1", task.getSchedulerContext().schedulerRunId());
        assertEquals("corr-1", task.getSchedulerContext().correlationId());

        // succeed
        Instant finish = Instant.parse("2024-01-01T01:00:00Z");
        task.markSucceeded(finish);
        assertTrue(task.getExecutionTimeline().hasFinished());

        // lease acquire/renew/release
        task.acquireLease("node-1", Instant.parse("2024-01-01T02:00:00Z"));
        LeaseInfo li = task.getLeaseInfo();
        assertTrue(li.isHeld());
        task.renewLease("node-1", Instant.parse("2024-01-01T03:00:00Z"));
        assertEquals(2, task.getLeaseInfo().leaseCount());
        task.releaseLease();
        assertFalse(task.getLeaseInfo().isHeld());

        // retry 准备：清空上下文/时间线并回队列
        task.prepareForRetry();
        ExecutionTimeline tl = task.getExecutionTimeline();
        TaskSchedulerContext sc = task.getSchedulerContext();
        assertFalse(tl.hasStarted());
        assertNull(sc.schedulerRunId());
        assertNull(sc.correlationId());
    }
}
