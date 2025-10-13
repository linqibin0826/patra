package com.patra.ingest.domain.model.vo;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link LeaseInfo}, covering acquire/renew/release flows. */
class LeaseInfoTest {

  @Test
  @DisplayName("constructor/snapshot normalization and invalid-parameter validation")
  void ctorAndSnapshot() {
    assertThrows(IllegalArgumentException.class, () -> new LeaseInfo("a", Instant.now(), -1));

    LeaseInfo none = LeaseInfo.none();
    assertFalse(none.isHeld());
    assertEquals(0, none.leaseCount());

    LeaseInfo snap = LeaseInfo.snapshotOf("owner", Instant.parse("2024-01-01T00:00:00Z"), null);
    assertEquals(0, snap.leaseCount());
  }

  @Test
  @DisplayName("acquire: must provide owner/until on first acquire and must not already be held")
  void acquireValidation() {
    LeaseInfo none = LeaseInfo.none();
    assertThrows(IllegalArgumentException.class, () -> none.acquire(null, Instant.now()));
    assertThrows(IllegalArgumentException.class, () -> none.acquire("", Instant.now()));
    assertThrows(IllegalArgumentException.class, () -> none.acquire("owner", null));

    LeaseInfo held = none.acquire("owner", Instant.parse("2024-01-02T00:00:00Z"));
    assertTrue(held.isHeld());
    assertEquals(1, held.leaseCount());

    assertThrows(IllegalStateException.class, () -> held.acquire("other", Instant.now()));
  }

  @Test
  @DisplayName("renew: only the current owner can renew; strict parameter validation")
  void renewValidation() {
    LeaseInfo none = LeaseInfo.none();
    assertThrows(IllegalStateException.class, () -> none.renew("owner", Instant.now()));

    LeaseInfo held = none.acquire("owner", Instant.parse("2024-01-02T00:00:00Z"));
    assertThrows(IllegalArgumentException.class, () -> held.renew(null, Instant.now()));
    assertThrows(IllegalArgumentException.class, () -> held.renew("", Instant.now()));
    assertThrows(IllegalArgumentException.class, () -> held.renew("other", Instant.now()));
    assertThrows(IllegalArgumentException.class, () -> held.renew("owner", null));

    LeaseInfo renewed = held.renew("owner", Instant.parse("2024-01-03T00:00:00Z"));
    assertEquals(2, renewed.leaseCount());
    assertEquals("owner", renewed.owner());
  }

  @Test
  @DisplayName("release: returns self when not held; when held, clears owner/time but keeps count")
  void releaseBehavior() {
    LeaseInfo none = LeaseInfo.none();
    assertSame(none, none.release());

    LeaseInfo held = none.acquire("owner", Instant.parse("2024-01-02T00:00:00Z"));
    LeaseInfo after = held.release();
    assertFalse(after.isHeld());
    assertNull(after.owner());
    assertNull(after.leasedUntil());
    assertEquals(1, after.leaseCount());
  }
}
