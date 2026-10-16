package dev.patra.ingest.app.usecase.plan.assembler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.common.ProvenanceCode;
import dev.patra.ingest.app.usecase.plan.dto.PlanAssemblyResult;
import dev.patra.ingest.app.usecase.plan.expression.PlanExpressionDescriptor;
import dev.patra.ingest.app.usecase.plan.slicer.SlicePlanner;
import dev.patra.ingest.app.usecase.plan.slicer.SlicePlannerRegistry;
import dev.patra.ingest.app.usecase.plan.slicer.model.SlicePlan;
import dev.patra.ingest.domain.model.enums.OperationCode;
import dev.patra.ingest.domain.model.enums.Priority;
import dev.patra.ingest.domain.model.enums.Scheduler;
import dev.patra.ingest.domain.model.enums.SliceStrategy;
import dev.patra.ingest.domain.model.enums.TriggerType;
import dev.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import dev.patra.ingest.domain.model.vo.plan.PlanTriggerNorm;
import dev.patra.ingest.domain.model.vo.plan.PlannerWindow;
import dev.patra.ingest.expr.Const;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

/// PlanAssemblerImpl 单元测试
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("PlanAssemblerImpl 单元测试")
class PlanAssemblerImplTest {

  @Mock private SlicePlannerRegistry slicePlannerRegistry;
  private PlanAssemblerImpl planAssembler;

  private PlanTriggerNorm harvestTriggerNorm;
  private PlannerWindow plannerWindow;
  private PlanExpressionDescriptor planExpression;

  @BeforeEach
  void setUp() {
    planAssembler = new PlanAssemblerImpl(slicePlannerRegistry, JsonMapper.builder().build());

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
  @DisplayName("应该用注入的 JsonMapper 从 windowSpecJson 解析 Task 计划时间")
  void shouldDeriveTaskScheduledAtFromWindowSpecJson() {
    SlicePlanner mockPlanner = mock(SlicePlanner.class);
    when(slicePlannerRegistry.get(SliceStrategy.DATE)).thenReturn(mockPlanner);
    SlicePlan slicePlan =
        new SlicePlan(
            1,
            "seed1",
            "{\"window\":{\"from\":\"2024-01-01T08:00:00Z\",\"to\":\"2024-01-01T12:00:00Z\"}}",
            Const.TRUE);
    when(mockPlanner.slice(any())).thenReturn(List.of(slicePlan));

    PlanAssemblyRequest request =
        new PlanAssemblyRequest(
            harvestTriggerNorm,
            plannerWindow,
            mock(ProvenanceConfigSnapshot.class),
            planExpression);

    PlanAssemblyResult result = planAssembler.assemble(request);

    assertThat(result.tasks()).hasSize(1);
    assertThat(result.tasks().get(0).getScheduledAt())
        .isEqualTo(Instant.parse("2024-01-01T08:00:00Z"));
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
