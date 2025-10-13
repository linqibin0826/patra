package com.patra.ingest.domain.model.aggregate;

import static org.junit.jupiter.api.Assertions.*;

import com.patra.ingest.domain.model.enums.PlanStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PlanAggregate}. */
class PlanAggregateTest {

  @Test
  @DisplayName("create/restore: enum parsing and default state")
  void createAndRestore() {
    PlanAggregate agg =
        PlanAggregate.create(
            1L,
            "k",
            "PUBMED",
            "harvest",
            "h",
            "{}",
            "{}",
            "ch",
            Instant.now(),
            Instant.now(),
            "TIME",
            "{}");
    assertEquals(PlanStatus.DRAFT, agg.getStatus());
    assertEquals("HARVEST", agg.getOperationCode());

    // keep null when input is null
    PlanAggregate agg2 =
        PlanAggregate.create(
            1L, "k", "PUBMED", null, null, null, null, null, null, null, null, null);
    assertNull(agg2.getOperation());

    // restore should backfill version
    PlanAggregate agg3 =
        PlanAggregate.restore(
            10L,
            2L,
            "k",
            "P",
            "UPDATE",
            "h",
            "{}",
            "{}",
            "ch",
            null,
            null,
            null,
            null,
            PlanStatus.READY,
            7L);
    assertEquals(7L, agg3.getVersion());
    assertEquals("UPDATE", agg3.getOperationCode());
  }

  @Test
  @DisplayName("state machine: only DRAFT can enter SLICING; other status marks assign directly")
  void stateTransitions() {
    PlanAggregate agg =
        PlanAggregate.create(
            1L, "k", "PUBMED", "HARVEST", null, null, null, null, null, null, null, null);
    // DRAFT can enter SLICING once
    agg.startSlicing();
    assertEquals(PlanStatus.SLICING, agg.getStatus());

    agg.markReady();
    assertEquals(PlanStatus.READY, agg.getStatus());
    agg.markPartial();
    assertEquals(PlanStatus.PARTIAL, agg.getStatus());
    agg.markFailed();
    assertEquals(PlanStatus.FAILED, agg.getStatus());
    agg.markCompleted();
    assertEquals(PlanStatus.COMPLETED, agg.getStatus());

    // non-DRAFT cannot startSlicing again
    assertThrows(IllegalStateException.class, agg::startSlicing);
  }
}
