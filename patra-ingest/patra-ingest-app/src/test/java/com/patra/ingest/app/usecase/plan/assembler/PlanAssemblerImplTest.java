package com.patra.ingest.app.usecase.plan.assembler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.Priority;
import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Exprs;
import com.patra.ingest.app.usecase.plan.dto.PlanAssemblyResult;
import com.patra.ingest.app.usecase.plan.expression.PlanExpressionDescriptor;
import com.patra.ingest.app.usecase.plan.slicer.SlicePlannerRegistry;
import com.patra.ingest.domain.model.enums.Endpoint;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.PlanTriggerNorm;
import com.patra.ingest.domain.model.vo.PlannerWindow;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PlanAssemblerImplTest {
    private final PlanAssemblerImpl assembler = new PlanAssemblerImpl(new SlicePlannerRegistry(List.of()));

    @Test
    void shouldKeepNumericFieldsInConfigSnapshot() throws Exception {
        // 验证配置快照中的数值字段不会被自动推断为布尔或时间类型
        ProvenanceConfigSnapshot snapshot = new ProvenanceConfigSnapshot(
                new ProvenanceConfigSnapshot.ProvenanceInfo(
                        1L,
                        "PUBMED",
                        "PubMed",
                        "https://base",
                        "UTC",
                        "https://docs",
                        true,
                        "ACTIVE"
                ),
                null,
                new ProvenanceConfigSnapshot.WindowOffsetConfig(
                        2L,
                        1L,
                        "TASK",
                        "HARVEST",
                        "HARVEST",
                        Instant.parse("2025-09-01T00:00:00Z"),
                        null,
                        "CALENDAR",
                        24,
                        "HOUR",
                        "DAY",
                        48,
                        "HOUR",
                        2,
                        "HOUR",
                        60,
                        "DATE",
                        "EDAT",
                        "yyyyMMdd",
                        "EDAT",
                        1000,
                        2_592_000
                ),
                null,
                null,
                null,
                null,
                null,
                null
        );

        PlanAssemblyResult result = assembler.assemble(
                new PlanAssemblyRequest(
                        new PlanTriggerNorm(
                                99L,
                                ProvenanceCode.PUBMED,
                                Endpoint.SEARCH,
                                OperationCode.HARVEST,
                                "PT1H",
                                TriggerType.SCHEDULE,
                                Scheduler.XXL,
                                null,
                                null,
                                Instant.parse("2025-09-01T00:00:00Z"),
                                Instant.parse("2025-09-01T02:00:00Z"),
                                Priority.NORMAL,
                                Map.of()
                        ),
                        new PlannerWindow(
                                Instant.parse("2025-09-01T00:00:00Z"),
                                Instant.parse("2025-09-01T02:00:00Z")
                        ),
                        snapshot,
                        new PlanExpressionDescriptor(Exprs.constTrue(), "{}", "hash")
                )
        );
        String snapshotJson = result.plan().getProvenanceConfigSnapshotJson();
        JsonNode root = new ObjectMapper().readTree(snapshotJson);
        JsonNode provenance = root.get("provenance");
        JsonNode windowOffset = root.get("windowOffset");

        Assertions.assertTrue(provenance.get("id").isNumber());
        Assertions.assertEquals(1L, provenance.get("id").asLong());
        Assertions.assertTrue(windowOffset.get("id").isNumber());
        Assertions.assertEquals(2L, windowOffset.get("id").asLong());
        Assertions.assertTrue(windowOffset.get("overlapValue").isNumber());
        Assertions.assertEquals(2, windowOffset.get("overlapValue").asInt());
        Assertions.assertTrue(windowOffset.get("watermarkLagSeconds").isNumber());
        Assertions.assertEquals(60, windowOffset.get("watermarkLagSeconds").asInt());
        Assertions.assertTrue(windowOffset.get("maxIdsPerWindow").isNumber());
        Assertions.assertEquals(1000, windowOffset.get("maxIdsPerWindow").asInt());
    }
}
