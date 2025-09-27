package com.patra.ingest.app.orchestration.slice;

import com.patra.common.enums.Priority;
import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Exprs;
import com.patra.ingest.app.orchestration.expression.PlanExpressionDescriptor;
import com.patra.ingest.app.orchestration.slice.model.SlicePlan;
import com.patra.ingest.app.orchestration.slice.model.SlicePlanningContext;
import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.enums.Endpoint;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TimeSlicePlannerTest {

    private final TimeSlicePlanner planner = new TimeSlicePlanner();

    @Test
    void sliceShouldSplitByStepAndAttachTimeConstraint() {
        // 验证切片按照步长拆分并生成时间范围约束
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to = Instant.parse("2024-01-01T03:00:00Z");
        SlicePlanningContext context = new SlicePlanningContext(
                norm("PT1H"),
                new PlannerWindow(from, to),
                new PlanExpressionDescriptor(Exprs.constTrue(), "{}", "hash"),
                snapshotWithTimeField("updatedAt", null, "UTC"));

        List<SlicePlan> plans = planner.slice(context);

        Assertions.assertEquals(3, plans.size());
        Assertions.assertEquals(from, plans.getFirst().windowFrom());
        Assertions.assertEquals(to, plans.getLast().windowTo());
        Assertions.assertTrue(plans.getFirst().sliceSpecJson().contains("\"timezone\":\"UTC\""));
    }

    @Test
    void sliceShouldReturnEmptyWhenNoTimeField() {
        // 无法识别时间字段时返回空结果
        SlicePlanningContext context = new SlicePlanningContext(
                norm("PT1H"),
                new PlannerWindow(Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T02:00:00Z")),
                new PlanExpressionDescriptor(Exprs.constTrue(), "{}", "hash"),
                snapshotWithTimeField(null, null, "UTC"));

        List<SlicePlan> plans = planner.slice(context);
        Assertions.assertTrue(plans.isEmpty());
    }

    private PlanTriggerNorm norm(String step) {
        return new PlanTriggerNorm(
                1L,
                ProvenanceCode.PUBMED,
                Endpoint.SEARCH,
                OperationCode.HARVEST,
                step,
                TriggerType.SCHEDULE,
                Scheduler.XXL,
                null,
                null,
                null,
                null,
                Priority.NORMAL,
                Map.of());
    }

    private ProvenanceConfigSnapshot snapshotWithTimeField(String offsetField,
                                                           String defaultDateField,
                                                           String timezone) {
        ProvenanceConfigSnapshot.ProvenanceInfo provenance = new ProvenanceConfigSnapshot.ProvenanceInfo(
                1L,
                "PUBMED",
                "PubMed",
                null,
                timezone,
                null,
                true,
                "ACTIVE");
        ProvenanceConfigSnapshot.WindowOffsetConfig offset = new ProvenanceConfigSnapshot.WindowOffsetConfig(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "SLIDING",
                1,
                "HOUR",
                null,
                null,
                null,
                null,
                null,
                0,
                offsetField == null ? null : "DATE",
                offsetField,
                null,
                defaultDateField,
                null,
                null);
        return new ProvenanceConfigSnapshot(
                provenance,
                null,
                offset,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
