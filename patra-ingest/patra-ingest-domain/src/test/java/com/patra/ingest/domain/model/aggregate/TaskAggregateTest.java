package com.patra.ingest.domain.model.aggregate;

import static org.junit.jupiter.api.Assertions.*;

import com.patra.common.domain.DomainEvent;
import com.patra.ingest.domain.event.TaskQueuedEvent;
import com.patra.ingest.domain.model.vo.ExecutionTimeline;
import com.patra.ingest.domain.model.vo.LeaseInfo;
import com.patra.ingest.domain.model.vo.TaskSchedulerContext;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link TaskAggregate}. */
class TaskAggregateTest {

  @Test
  @DisplayName("create default state and raiseQueuedEvent attaches event")
  void createAndRaiseEvent() {
    TaskAggregate task =
        TaskAggregate.create(1L, 2L, 3L, "P", "O", "{}", "K", "EH", 1, Instant.now());
    assertNotNull(task);
    assertEquals("P", task.getProvenanceCode());
    assertEquals("O", task.getOperationCode());

    TaskQueuedEvent event = task.raiseQueuedEvent();
    List<DomainEvent> events = task.peekDomainEvents();
    assertEquals(1, events.size());
    assertSame(event, events.get(0));
  }

  @Test
  @DisplayName("state transitions and lease/timeline/context changes")
  void statusLeaseTimelineContext() {
    TaskAggregate task =
        TaskAggregate.create(1L, 2L, 3L, "P", "O", "{}", "K", "EH", 1, Instant.now());
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

    // retry preparation: clear context/timeline and requeue
    task.prepareForRetry();
    ExecutionTimeline tl = task.getExecutionTimeline();
    TaskSchedulerContext sc = task.getSchedulerContext();
    assertFalse(tl.hasStarted());
    assertNull(sc.schedulerRunId());
    assertNull(sc.correlationId());
  }
}
