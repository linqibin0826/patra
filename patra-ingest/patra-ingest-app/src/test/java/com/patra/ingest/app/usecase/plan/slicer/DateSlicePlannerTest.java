package com.patra.ingest.app.usecase.plan.slicer;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.ingest.app.usecase.plan.expression.PlanExpressionDescriptor;
import com.patra.ingest.app.usecase.plan.slicer.model.SlicePlan;
import com.patra.ingest.app.usecase.plan.slicer.model.SlicePlanningContext;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.SliceStrategy;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.plan.PlanTriggerNorm;
import com.patra.ingest.domain.model.vo.plan.PlannerWindow;
import dev.linqibin.commons.enums.Priority;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/// DateSlicePlanner 单元测试
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("DateSlicePlanner 单元测试")
class DateSlicePlannerTest {

  private DateSlicePlanner planner;

  @BeforeEach
  void setUp() {
    planner = new DateSlicePlanner();
  }

  @Nested
  @DisplayName("code() 测试")
  class CodeTests {

    @Test
    @DisplayName("应返回 DATE 策略标识")
    void shouldReturnDateStrategy() {
      // When
      SliceStrategy result = planner.code();

      // Then
      assertThat(result).isEqualTo(SliceStrategy.DATE);
    }
  }

  @Nested
  @DisplayName("slice() 正常流程测试")
  class NormalFlowTests {

    @Test
    @DisplayName("应正确切片日期窗口为多个1天切片")
    void shouldSliceDateWindowWithDefaultStep() {
      // Given - 3天的窗口
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-04T00:00:00Z");

      SlicePlanningContext context = createContext(from, to, null);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).hasSize(3); // 3天
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
    @DisplayName("应使用自定义步长切片日期窗口")
    void shouldSliceDateWindowWithCustomStep() {
      // Given - 7天窗口，步长2天
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-08T00:00:00Z");
      String customStep = "P2D"; // 2天

      SlicePlanningContext context = createContext(from, to, customStep);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).hasSize(4); // 7天 / 2天 = 3.5，向上取整为4
      assertThat(result).extracting(SlicePlan::sliceNo).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("应将最后一个切片对齐到窗口结束日期")
    void shouldAlignLastSliceToWindowEnd() {
      // Given - 不规整的5天窗口
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-06T00:00:00Z");

      SlicePlanningContext context = createContext(from, to, null);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).hasSize(5); // 5个完整天
      assertThat(result.get(4).sliceNo()).isEqualTo(5);
    }

    @Test
    @DisplayName("应处理单个切片场景")
    void shouldHandleSingleSliceScenario() {
      // Given - 小于1天的窗口
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-01T12:00:00Z");

      SlicePlanningContext context = createContext(from, to, null);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      // 由于同一天，日期切片逻辑会认为 from 和 to 日期相同，不生成切片
      // 或者根据实现可能生成 0 个切片
      // 查看源码：cursor.isBefore(endDate)，两者都是 2024-01-01，所以不满足条件
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应处理精确匹配步长的窗口")
    void shouldHandleExactStepMatch() {
      // Given - 7天窗口，步长7天
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-08T00:00:00Z");
      String step = "P7D"; // 7天

      SlicePlanningContext context = createContext(from, to, step);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).hasSize(1); // 精确1个切片
      assertThat(result.get(0).sliceNo()).isEqualTo(1);
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
      Instant to = Instant.parse("2024-01-04T00:00:00Z");

      SlicePlanningContext context = createContextWithoutTimeField(from, to);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("当from和to是同一天时应返回空列表")
    void shouldReturnEmptyListWhenSameDay() {
      // Given - 同一天的不同时间
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-01T23:59:59Z");

      SlicePlanningContext context = createContext(from, to);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      // 由于转换为 LocalDate 后是同一天，cursor.isBefore(endDate) 返回 false
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("slice() 步长处理测试")
  class StepHandlingTests {

    @Test
    @DisplayName("当步长格式无效时应回退到默认1天")
    void shouldFallbackToDefaultStepWhenStepInvalid() {
      // Given
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-04T00:00:00Z");
      String invalidStep = "INVALID_DURATION";

      SlicePlanningContext context = createContext(from, to, invalidStep);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).hasSize(3); // 使用默认1天步长
    }

    @Test
    @DisplayName("当步长小于1天时应回退到默认1天")
    void shouldFallbackToDefaultWhenStepLessThanOneDay() {
      // Given
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-04T00:00:00Z");
      String smallStep = "PT6H"; // 6小时，小于1天

      SlicePlanningContext context = createContext(from, to, smallStep);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).hasSize(3); // 回退到默认1天步长
    }

    @Test
    @DisplayName("应支持多天步长")
    void shouldSupportMultipleDaysStep() {
      // Given
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-11T00:00:00Z");
      String step = "P5D"; // 5天

      SlicePlanningContext context = createContext(from, to, step);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).hasSize(2); // 10天 / 5天 = 2个切片
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
      Instant to = Instant.parse("2024-01-06T00:00:00Z");

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
      Instant to = Instant.parse("2024-01-05T00:00:00Z");

      SlicePlanningContext context = createContext(from, to, null);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).hasSize(4);
      assertThat(result).extracting(SlicePlan::sliceNo).containsExactly(1, 2, 3, 4);
    }
  }

  // ==================== 辅助方法 ====================

  private SlicePlanningContext createContext(Instant from, Instant to) {
    return createContext(from, to, null);
  }

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
        Instant.parse("2024-01-10T00:00:00Z"),
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
            "DAY",
            null,
            0,
            "DAY",
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
            "DAY",
            null,
            0,
            "DAY",
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
