package com.patra.ingest.app.usecase.plan.assembler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.patra.expr.Const;
import com.patra.ingest.app.usecase.plan.dto.PlanAssemblyResult;
import com.patra.ingest.app.usecase.plan.expression.PlanExpressionDescriptor;
import com.patra.ingest.app.usecase.plan.slicer.SlicePlanner;
import com.patra.ingest.app.usecase.plan.slicer.SlicePlannerRegistry;
import com.patra.ingest.app.usecase.plan.slicer.model.SlicePlan;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.SliceStrategy;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.plan.PlanTriggerNorm;
import com.patra.ingest.domain.model.vo.plan.PlannerWindow;
import dev.linqibin.commons.enums.Priority;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// PlanAssemblerImpl 单元测试
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("PlanAssemblerImpl 单元测试")
class PlanAssemblerImplTest {

  @Mock private SlicePlannerRegistry slicePlannerRegistry;
  @InjectMocks private PlanAssemblerImpl planAssembler;

  private PlanTriggerNorm harvestTriggerNorm;
  private PlannerWindow plannerWindow;
  private PlanExpressionDescriptor planExpression;

  @BeforeEach
  void setUp() {
    harvestTriggerNorm =
        new PlanTriggerNorm(
            123L,
            ProvenanceCode.PUBMED,
            OperationCode.HARVEST,
            "initial",
            TriggerType.SCHEDULE,
            Scheduler.XXL,
            "job-123",
            "log-456",
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-01-02T00:00:00Z"),
            Priority.NORMAL,
            null);

    plannerWindow =
        new PlannerWindow(
            Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-02T00:00:00Z"));

    planExpression = new PlanExpressionDescriptor(Const.TRUE, "{\"type\":\"test\"}", "hash123");
  }

  @Test
  @DisplayName("应该成功组装 Plan, Slice 和 Task")
  void shouldAssemblePlanSuccessfully() {
    SlicePlanner mockPlanner = mock(SlicePlanner.class);
    when(slicePlannerRegistry.get(SliceStrategy.DATE)).thenReturn(mockPlanner);

    SlicePlan slicePlan1 =
        new SlicePlan(
            1,
            "seed1",
            "{\"window\":{\"from\":\"2024-01-01T00:00:00Z\",\"to\":\"2024-01-01T12:00:00Z\"}}",
            Const.TRUE);
    when(mockPlanner.slice(any())).thenReturn(List.of(slicePlan1));

    PlanAssemblyRequest request =
        new PlanAssemblyRequest(
            harvestTriggerNorm,
            plannerWindow,
            mock(ProvenanceConfigSnapshot.class),
            planExpression);

    PlanAssemblyResult result = planAssembler.assemble(request);

    assertThat(result.status()).isEqualTo(PlanAssemblyResult.AssemblyStatus.READY);
    assertThat(result.plan()).isNotNull();
    assertThat(result.slices()).hasSize(1);
    assertThat(result.tasks()).hasSize(1);
  }

  @Test
  @DisplayName("切片策略未找到应该返回失败")
  void shouldFailWhenSlicePlannerNotFound() {
    when(slicePlannerRegistry.get(any(SliceStrategy.class))).thenReturn(null);

    PlanAssemblyRequest request =
        new PlanAssemblyRequest(
            harvestTriggerNorm,
            plannerWindow,
            mock(ProvenanceConfigSnapshot.class),
            planExpression);

    PlanAssemblyResult result = planAssembler.assemble(request);

    assertThat(result.status()).isEqualTo(PlanAssemblyResult.AssemblyStatus.FAILED);
    assertThat(result.slices()).isEmpty();
    assertThat(result.tasks()).isEmpty();
  }

  @Test
  @DisplayName("切片生成为空应该返回失败")
  void shouldFailWhenSlicesAreEmpty() {
    SlicePlanner mockPlanner = mock(SlicePlanner.class);
    when(slicePlannerRegistry.get(SliceStrategy.DATE)).thenReturn(mockPlanner);
    when(mockPlanner.slice(any())).thenReturn(List.of());

    PlanAssemblyRequest request =
        new PlanAssemblyRequest(
            harvestTriggerNorm,
            plannerWindow,
            mock(ProvenanceConfigSnapshot.class),
            planExpression);

    PlanAssemblyResult result = planAssembler.assemble(request);

    assertThat(result.status()).isEqualTo(PlanAssemblyResult.AssemblyStatus.FAILED);
    assertThat(result.slices()).isEmpty();
    assertThat(result.tasks()).isEmpty();
  }
}
