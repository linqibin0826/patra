package com.patra.ingest.domain.model.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link LeaseInfo} 的单元测试，覆盖获取/续约/释放全路径。
 */
class LeaseInfoTest {

    @Test
    @DisplayName("构造/快照归一化与非法参数校验")
    void ctorAndSnapshot() {
        assertThrows(IllegalArgumentException.class, () -> new LeaseInfo("a", Instant.now(), -1));

        LeaseInfo none = LeaseInfo.none();
        assertFalse(none.isHeld());
        assertEquals(0, none.leaseCount());

        LeaseInfo snap = LeaseInfo.snapshotOf("owner", Instant.parse("2024-01-01T00:00:00Z"), null);
        assertEquals(0, snap.leaseCount());
    }

    @Test
    @DisplayName("首次获取租约必须指定 owner/until，且当前未被持有")
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
    @DisplayName("续约：仅原持有者可续约，参数校验严格")
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
    @DisplayName("释放：无持有者返回自身；有持有者清空 owner/时间但保留计数")
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
