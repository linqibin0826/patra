package dev.linqibin.patra.ingest.app.usecase.plan.slicer;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import dev.linqibin.commons.enums.Priority;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import dev.linqibin.patra.ingest.app.usecase.plan.expression.PlanExpressionDescriptor;
import dev.linqibin.patra.ingest.app.usecase.plan.slicer.model.SlicePlan;
import dev.linqibin.patra.ingest.app.usecase.plan.slicer.model.SlicePlanningContext;
import dev.linqibin.patra.ingest.domain.model.enums.OperationCode;
import dev.linqibin.patra.ingest.domain.model.enums.Scheduler;
import dev.linqibin.patra.ingest.domain.model.enums.SliceStrategy;
import dev.linqibin.patra.ingest.domain.model.enums.TriggerType;
import dev.linqibin.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import dev.linqibin.patra.ingest.domain.model.vo.plan.PlanTriggerNorm;
import dev.linqibin.patra.ingest.domain.model.vo.plan.PlannerWindow;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/// TimeSlicePlanner 单元测试
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("TimeSlicePlanner 单元测试")
class TimeSlicePlannerTest {

  private TimeSlicePlanner planner;

  @BeforeEach
  void setUp() {
    planner = new TimeSlicePlanner();
  }

  @Nested
  @DisplayName("code() 测试")
  class CodeTests {

    @Test
    @DisplayName("应返回 TIME 策略标识")
    void shouldReturnTimeStrategy() {
      // When
      SliceStrategy result = planner.code();

      // Then
      assertThat(result).isEqualTo(SliceStrategy.TIME);
    }
  }

  @Nested
  @DisplayName("slice() 正常流程测试")
  class NormalFlowTests {

    @Test
    @DisplayName("应正确切片时间窗口为多个1小时切片")
    void shouldSliceTimeWindowWithDefaultStep() {
      // Given
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-01T03:00:00Z");

      SlicePlanningContext context = createContext(from, to, null);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).hasSize(3);
      assertThat(result.get(0).sliceNo()).isEqualTo(1);
      assertThat(result.get(1).sliceNo()).isEqualTo(2);
      assertThat(result.get(2).sliceNo()).isEqualTo(3);

      // 验证每个切片的签名唯一
      assertThat(result).extracting(SlicePlan::sliceSignatureSeed).doesNotHaveDuplicates();

      // 验证切片表达式不为空
      assertThat(result).extracting(SlicePlan::sliceExpr).allMatch(expr -> expr != null);

      // 验证 JSON 规格不为空
      assertThat(result)
          .extracting(SlicePlan::windowSpecJson)
          .allMatch(json -> json != null && !json.isBlank());
    }

    @Test
    @DisplayName("应使用自定义步长切片时间窗口")
    void shouldSliceTimeWindowWithCustomStep() {
      // Given
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-01T02:00:00Z");
      String customStep = "PT30M"; // 30分钟

      SlicePlanningContext context = createContext(from, to, customStep);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).hasSize(4); // 2小时 / 30分钟 = 4个切片
      assertThat(result).extracting(SlicePlan::sliceNo).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("应将最后一个切片对齐到窗口结束时间")
    void shouldAlignLastSliceToWindowEnd() {
      // Given
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-01T02:30:00Z"); // 不是整小时

      SlicePlanningContext context = createContext(from, to, null);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).hasSize(3); // 0-1, 1-2, 2-2.5
      assertThat(result.get(2).sliceNo()).isEqualTo(3);
    }

    @Test
    @DisplayName("应处理单个切片场景")
    void shouldHandleSingleSliceScenario() {
      // Given
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-01T00:30:00Z"); // 小于1小时

      SlicePlanningContext context = createContext(from, to, null);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).sliceNo()).isEqualTo(1);
      assertThat(result.get(0).sliceSignatureSeed()).isNotBlank();
    }
  }

  @Nested
  @DisplayName("slice() 边界条件测试")
  class BoundaryTests {

    @Test
    @DisplayName("当窗口from和to都为null时应返回空列表")
    void shouldReturnEmptyListWhenWindowIsNull() {
      // Given
      PlanTriggerNorm norm = createNorm(null);
      PlannerWindow window = PlannerWindow.full(); // from=null, to=null
      PlanExpressionDescriptor planExpr = createPlanExpression();
      ProvenanceConfigSnapshot configSnapshot = createConfigSnapshot();

      SlicePlanningContext context =
          new SlicePlanningContext(norm, window, planExpr, configSnapshot);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("当时间字段无法解析时应返回空列表")
    void shouldReturnEmptyListWhenTimeFieldCannotBeResolved() {
      // Given
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-01T02:00:00Z");

      SlicePlanningContext context = createContextWithoutTimeField(from, to);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("slice() 步长处理测试")
  class StepHandlingTests {

    @Test
    @DisplayName("当步长格式无效时应回退到默认1小时")
    void shouldFallbackToDefaultStepWhenStepInvalid() {
      // Given
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-01T03:00:00Z");
      String invalidStep = "INVALID_DURATION";

      SlicePlanningContext context = createContext(from, to, invalidStep);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).hasSize(3); // 使用默认1小时步长
    }

    @Test
    @DisplayName("应支持分钟级步长")
    void shouldSupportMinuteStep() {
      // Given
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-01T00:10:00Z");
      String step = "PT5M"; // 5分钟

      SlicePlanningContext context = createContext(from, to, step);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).hasSize(2); // 10分钟 / 5分钟 = 2个切片
    }
  }

  @Nested
  @DisplayName("slice() 数据完整性测试")
  class DataIntegrityTests {

    @Test
    @DisplayName("每个切片的签名应该唯一")
    void shouldGenerateUniqueSignaturesForEachSlice() {
      // Given
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-01T05:00:00Z");

      SlicePlanningContext context = createContext(from, to, null);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).hasSize(5);
      assertThat(result).extracting(SlicePlan::sliceSignatureSeed).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("切片序号应从1开始连续递增")
    void shouldHaveConsecutiveSliceNumbers() {
      // Given
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-01T04:00:00Z");

      SlicePlanningContext context = createContext(from, to, null);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).hasSize(4);
      assertThat(result).extracting(SlicePlan::sliceNo).containsExactly(1, 2, 3, 4);
    }
  }

  // ==================== 辅助方法 ====================

  private SlicePlanningContext createContext(Instant from, Instant to, String step) {
    PlanTriggerNorm norm = createNorm(step);
    PlannerWindow window = new PlannerWindow(from, to);
    PlanExpressionDescriptor planExpr = createPlanExpression();
    ProvenanceConfigSnapshot configSnapshot = createConfigSnapshot();

    return new SlicePlanningContext(norm, window, planExpr, configSnapshot);
  }

  private SlicePlanningContext createContextWithoutTimeField(Instant from, Instant to) {
    PlanTriggerNorm norm = createNorm(null);
    PlannerWindow window = new PlannerWindow(from, to);
    PlanExpressionDescriptor planExpr = createPlanExpression();
    ProvenanceConfigSnapshot configSnapshot = createConfigSnapshotWithoutTimeField();

    return new SlicePlanningContext(norm, window, planExpr, configSnapshot);
  }

  private PlanTriggerNorm createNorm(String step) {
    return new PlanTriggerNorm(
        1L,
        ProvenanceCode.PUBMED,
        OperationCode.HARVEST,
        step,
        TriggerType.SCHEDULE,
        Scheduler.XXL,
        "job-1",
        "log-1",
        Instant.parse("2024-01-01T00:00:00Z"),
        Instant.parse("2024-01-01T10:00:00Z"),
        Priority.NORMAL,
        Map.of());
  }

  private PlanExpressionDescriptor createPlanExpression() {
    Expr baseExpr = Exprs.constTrue();
    return new PlanExpressionDescriptor(baseExpr, "{\"type\":\"const_true\"}", "hash-12345");
  }

  private ProvenanceConfigSnapshot createConfigSnapshot() {
    ProvenanceConfigSnapshot.ProvenanceInfo provenance =
        new ProvenanceConfigSnapshot.ProvenanceInfo(
            1L,
            "pubmed",
            "PubMed",
            "https://api.pubmed.com",
            "UTC",
            "https://pubmed.ncbi.nlm.nih.gov/",
            true,
            "ACTIVE");

    ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset =
        new ProvenanceConfigSnapshot.WindowOffsetConfig(
            1L,
            1L,
            "HARVEST",
            Instant.parse("2024-01-01T00:00:00Z"),
            null,
            "SLIDING",
            1,
            "HOUR",
            null,
            0,
            "HOUR",
            0,
            "MINUTE",
            0,
            "DATE",
            "publication_date",
            "ISO_INSTANT",
            "pub_date",
            1000,
            86400);

    return new ProvenanceConfigSnapshot(provenance, windowOffset, null, null, null, null, null);
  }

  private ProvenanceConfigSnapshot createConfigSnapshotWithoutTimeField() {
    ProvenanceConfigSnapshot.ProvenanceInfo provenance =
        new ProvenanceConfigSnapshot.ProvenanceInfo(
            1L,
            "pubmed",
            "PubMed",
            "https://api.pubmed.com",
            "UTC",
            "https://pubmed.ncbi.nlm.nih.gov/",
            true,
            "ACTIVE");

    ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset =
        new ProvenanceConfigSnapshot.WindowOffsetConfig(
            1L,
            1L,
            "HARVEST",
            Instant.parse("2024-01-01T00:00:00Z"),
            null,
            "SLIDING",
            1,
            "HOUR",
            null,
            0,
            "HOUR",
            0,
            "MINUTE",
            0,
            "ID",
            null,
            "ISO_INSTANT",
            null,
            1000,
            86400);

    return new ProvenanceConfigSnapshot(provenance, windowOffset, null, null, null, null, null);
  }
}
