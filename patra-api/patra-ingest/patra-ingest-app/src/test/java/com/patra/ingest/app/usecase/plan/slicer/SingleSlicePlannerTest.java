package com.patra.ingest.app.usecase.plan.slicer;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.enums.Priority;
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
import com.patra.ingest.domain.model.vo.plan.PlanTriggerNorm;
import com.patra.ingest.domain.model.vo.plan.PlannerWindow;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/// SingleSlicePlanner 单元测试
/// 
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("SingleSlicePlanner 单元测试")
class SingleSlicePlannerTest {

  private SingleSlicePlanner planner;

  @BeforeEach
  void setUp() {
    planner = new SingleSlicePlanner();
  }

  @Nested
  @DisplayName("code() 测试")
  class CodeTests {

    @Test
    @DisplayName("应返回 SINGLE 策略标识")
    void shouldReturnSingleStrategy() {
      // When
      SliceStrategy result = planner.code();

      // Then
      assertThat(result).isEqualTo(SliceStrategy.SINGLE);
    }
  }

  @Nested
  @DisplayName("slice() 功能测试")
  class FunctionalTests {

    @Test
    @DisplayName("应生成单个切片且序号为1")
    void shouldGenerateSingleSliceWithNumber1() {
      // Given
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-10T00:00:00Z");

      SlicePlanningContext context = createContext(from, to);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).sliceNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("应包含完整的窗口信息")
    void shouldIncludeFullWindowInformation() {
      // Given
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-10T00:00:00Z");

      SlicePlanningContext context = createContext(from, to);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      SlicePlan slice = result.get(0);
      assertThat(slice.windowSpecJson()).isNotBlank();
      assertThat(slice.windowSpecJson()).contains("2024-01-01");
      assertThat(slice.windowSpecJson()).contains("2024-01-10");
    }

    @Test
    @DisplayName("应生成稳定的签名哈希")
    void shouldGenerateStableSignatureHash() {
      // Given
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-10T00:00:00Z");

      SlicePlanningContext context = createContext(from, to);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      SlicePlan slice = result.get(0);
      assertThat(slice.sliceSignatureSeed()).isNotBlank();
      assertThat(slice.sliceSignatureSeed().length()).isEqualTo(64); // SHA-256 哈希长度
    }

    @Test
    @DisplayName("应重用上游Plan表达式")
    void shouldReusePlanExpression() {
      // Given
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-10T00:00:00Z");

      SlicePlanningContext context = createContext(from, to);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      SlicePlan slice = result.get(0);
      assertThat(slice.sliceExpr()).isNotNull();
      assertThat(slice.sliceExpr()).isEqualTo(context.planExpression().expr());
    }

    @Test
    @DisplayName("应处理null窗口场景")
    void shouldHandleNullWindowScenario() {
      // Given
      PlanTriggerNorm norm = createNorm();
      PlannerWindow window = PlannerWindow.full(); // from=null, to=null
      PlanExpressionDescriptor planExpr = createPlanExpression();

      SlicePlanningContext context = new SlicePlanningContext(norm, window, planExpr, null);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).sliceNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("相同上下文应生成相同的签名")
    void shouldGenerateSameSignatureForSameContext() {
      // Given
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-10T00:00:00Z");

      SlicePlanningContext context = createContext(from, to);

      // When
      List<SlicePlan> result1 = planner.slice(context);
      List<SlicePlan> result2 = planner.slice(context);

      // Then
      assertThat(result1.get(0).sliceSignatureSeed())
          .isEqualTo(result2.get(0).sliceSignatureSeed());
    }
  }

  @Nested
  @DisplayName("slice() 数据完整性测试")
  class DataIntegrityTests {

    @Test
    @DisplayName("切片应包含所有必需字段")
    void shouldContainAllRequiredFields() {
      // Given
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-10T00:00:00Z");

      SlicePlanningContext context = createContext(from, to);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      SlicePlan slice = result.get(0);
      assertThat(slice.sliceNo()).isEqualTo(1);
      assertThat(slice.sliceSignatureSeed()).isNotBlank();
      assertThat(slice.windowSpecJson()).isNotBlank();
      assertThat(slice.sliceExpr()).isNotNull();
    }

    @Test
    @DisplayName("windowSpecJson应包含策略标识")
    void shouldIncludeStrategyInSpec() {
      // Given
      Instant from = Instant.parse("2024-01-01T00:00:00Z");
      Instant to = Instant.parse("2024-01-10T00:00:00Z");

      SlicePlanningContext context = createContext(from, to);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      SlicePlan slice = result.get(0);
      assertThat(slice.windowSpecJson()).contains("SINGLE");
    }
  }

  @Nested
  @DisplayName("slice() 特殊场景测试")
  class SpecialScenarioTests {

    @Test
    @DisplayName("应处理无窗口信息的场景")
    void shouldHandleScenarioWithoutWindow() {
      // Given
      PlanTriggerNorm norm = createNorm();
      PlannerWindow window = PlannerWindow.full();
      PlanExpressionDescriptor planExpr = createPlanExpression();

      SlicePlanningContext context = new SlicePlanningContext(norm, window, planExpr, null);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).windowSpecJson()).contains("SINGLE");
    }

    @Test
    @DisplayName("应处理仅有from的窗口")
    void shouldHandleWindowWithOnlyFrom() {
      // Given
      Instant from = Instant.parse("2024-01-01T00:00:00Z");

      PlanTriggerNorm norm = createNorm();
      PlannerWindow window = new PlannerWindow(from, null);
      PlanExpressionDescriptor planExpr = createPlanExpression();

      SlicePlanningContext context = new SlicePlanningContext(norm, window, planExpr, null);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).windowSpecJson()).contains("2024-01-01");
    }

    @Test
    @DisplayName("应处理仅有to的窗口")
    void shouldHandleWindowWithOnlyTo() {
      // Given
      Instant to = Instant.parse("2024-01-10T00:00:00Z");

      PlanTriggerNorm norm = createNorm();
      PlannerWindow window = new PlannerWindow(null, to);
      PlanExpressionDescriptor planExpr = createPlanExpression();

      SlicePlanningContext context = new SlicePlanningContext(norm, window, planExpr, null);

      // When
      List<SlicePlan> result = planner.slice(context);

      // Then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).windowSpecJson()).contains("2024-01-10");
    }
  }

  // ==================== 辅助方法 ====================

  private SlicePlanningContext createContext(Instant from, Instant to) {
    PlanTriggerNorm norm = createNorm();
    PlannerWindow window = new PlannerWindow(from, to);
    PlanExpressionDescriptor planExpr = createPlanExpression();

    return new SlicePlanningContext(norm, window, planExpr, null);
  }

  private PlanTriggerNorm createNorm() {
    return new PlanTriggerNorm(
        1L,
        ProvenanceCode.PUBMED,
        OperationCode.UPDATE,
        null,
        TriggerType.MANUAL,
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
}
