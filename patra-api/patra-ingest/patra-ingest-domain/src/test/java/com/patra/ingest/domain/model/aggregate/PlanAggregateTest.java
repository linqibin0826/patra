package com.patra.ingest.domain.model.aggregate;

import com.patra.ingest.domain.model.enums.PlanStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PlanAggregate} 的单元测试。
 */
class PlanAggregateTest {

    @Test
    @DisplayName("create/restore：枚举解析与默认状态")
    void createAndRestore() {
        PlanAggregate agg = PlanAggregate.create(1L, "k", "PUBMED", "search", "harvest",
                "h", "{}", "{}", "ch", Instant.now(), Instant.now(), "TIME", "{}");
        assertEquals(PlanStatus.DRAFT, agg.getStatus());
        assertEquals("SEARCH", agg.getEndpointName());
        assertEquals("HARVEST", agg.getOperationCode());

        // null 时保持为 null
        PlanAggregate agg2 = PlanAggregate.create(1L, "k", "PUBMED", null, null,
                null, null, null, null, null, null, null, null);
        assertNull(agg2.getEndpoint());
        assertNull(agg2.getOperation());

        // restore 会回填版本
        PlanAggregate agg3 = PlanAggregate.restore(10L, 2L, "k", "P", "SEARCH", "UPDATE",
                "h", "{}", "{}", "ch", null, null, null, null, PlanStatus.READY, 7L);
        assertEquals(7L, agg3.getVersion());
        assertEquals("UPDATE", agg3.getOperationCode());
    }

    @Test
    @DisplayName("状态机：仅 DRAFT 可进入 SLICING；其他状态标记直接赋值")
    void stateTransitions() {
        PlanAggregate agg = PlanAggregate.create(1L, "k", "PUBMED", "SEARCH", "HARVEST",
                null, null, null, null, null, null, null, null);
        // DRAFT 首次可进入 SLICING
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

        // 非 DRAFT 再次 startSlicing 抛错
        assertThrows(IllegalStateException.class, agg::startSlicing);
    }
}
