package com.patra.ingest.app.orchestration.window;

import com.patra.common.enums.Priority;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.enums.Endpoint;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DefaultPlanningWindowResolverTest {

    private final DefaultPlanningWindowResolver resolver = new DefaultPlanningWindowResolver();

    @Test
    void harvestShouldUseCursorAndLookback() {
        // 验证 HARVEST 在存在水位时使用 lookback 计算窗口起点
        Instant now = Instant.parse("2024-01-05T00:00:00Z");
        Instant cursor = Instant.parse("2024-01-04T12:00:00Z");
        ProvenanceConfigSnapshot snapshot = snapshot("UTC",
                new ProvenanceConfigSnapshot.WindowOffsetConfig(
                        null, null, null, null, null,
                        null, null,
                        "SLIDING",
                        6, "HOUR",
                        null,
                        2, "HOUR",
                        null, null,
                        0,
                        "DATE", "updatedAt", null, null,
                        null, null));
        PlanTriggerNorm norm = norm(OperationCode.HARVEST, null, null);

        PlannerWindow window = resolver.resolveWindow(norm, snapshot, cursor, now);

        Assertions.assertNotNull(window);
        Assertions.assertEquals(Instant.parse("2024-01-04T10:00:00Z"), window.from());
        Assertions.assertEquals(now, window.to());
    }

    @Test
    void calendarModeShouldAlignToDay() {
        // 验证 CALENDAR 模式按日对齐窗口上下界
        Instant now = Instant.parse("2024-03-10T02:30:00Z");
        Instant userFrom = Instant.parse("2024-03-08T15:45:00Z");
        Instant userTo = Instant.parse("2024-03-09T18:15:00Z");
        ProvenanceConfigSnapshot snapshot = snapshot("Asia/Shanghai",
                new ProvenanceConfigSnapshot.WindowOffsetConfig(
                        null, null, null, null, null,
                        null, null,
                        "CALENDAR",
                        1, "DAY",
                        "DAY",
                        null, null,
                        null, null,
                        0,
                        "DATE", null, null, null,
                        null, null));
        PlanTriggerNorm norm = norm(OperationCode.HARVEST, userFrom, userTo);

        PlannerWindow window = resolver.resolveWindow(norm, snapshot, null, now);

        Assertions.assertNotNull(window);
        Assertions.assertEquals(Instant.parse("2024-03-07T16:00:00Z"), window.from());
        Assertions.assertEquals(Instant.parse("2024-03-08T16:00:00Z"), window.to());
    }

    private PlanTriggerNorm norm(OperationCode operationCode, Instant from, Instant to) {
        return new PlanTriggerNorm(
                1L,
                ProvenanceCode.PUBMED,
                Endpoint.SEARCH,
                operationCode,
                null,
                TriggerType.SCHEDULE,
                Scheduler.XXL,
                null,
                null,
                from,
                to,
                Priority.NORMAL,
                Map.of());
    }

    private ProvenanceConfigSnapshot snapshot(String timezone,
                                              ProvenanceConfigSnapshot.WindowOffsetConfig offsetConfig) {
        ProvenanceConfigSnapshot.ProvenanceInfo provenance = new ProvenanceConfigSnapshot.ProvenanceInfo(
                1L,
                "PUBMED",
                "PubMed",
                null,
                timezone,
                null,
                true,
                "ACTIVE");
        return new ProvenanceConfigSnapshot(
                provenance,
                null,
                offsetConfig,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
