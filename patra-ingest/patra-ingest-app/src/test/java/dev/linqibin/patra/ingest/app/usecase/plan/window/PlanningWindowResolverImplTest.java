package dev.linqibin.patra.ingest.app.usecase.plan.window;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.ingest.domain.model.enums.OperationCode;
import dev.linqibin.patra.ingest.domain.model.enums.Scheduler;
import dev.linqibin.patra.ingest.domain.model.enums.TriggerType;
import dev.linqibin.patra.ingest.domain.model.vo.plan.PlanTriggerNorm;
import dev.linqibin.patra.ingest.domain.model.vo.plan.PlannerWindow;
import dev.linqibin.commons.enums.Priority;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// PlanningWindowResolverImpl 单元测试
///
/// 测试策略: 纯单元测试,验证窗口解析逻辑
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PlanningWindowResolverImpl 单元测试")
class PlanningWindowResolverImplTest {

  private PlanningWindowResolverImpl windowResolver;
  private Instant currentTime;

  @BeforeEach
  void setUp() {
    windowResolver = new PlanningWindowResolverImpl();
    currentTime = Instant.parse("2024-01-05T12:00:00Z");
  }

  @Test
  @DisplayName("HARVEST 首次运行应该使用默认窗口大小")
  void shouldUseDefaultWindowSizeForFirstHarvest() {
    PlanTriggerNorm triggerNorm = createHarvestTrigger(null, null);
    PlannerWindow window = windowResolver.resolveWindow(triggerNorm, null, null, currentTime);
    assertThat(window).isNotNull();
    assertThat(Duration.between(window.from(), window.to())).isEqualTo(Duration.ofHours(24));
  }

  @Test
  @DisplayName("HARVEST 有水位线应该从水位线开始")
  void shouldStartFromWatermarkForHarvest() {
    Instant harvestWatermark = Instant.parse("2024-01-04T00:00:00Z");
    PlanTriggerNorm triggerNorm = createHarvestTrigger(null, null);
    PlannerWindow window =
        windowResolver.resolveWindow(triggerNorm, null, harvestWatermark, currentTime);
    assertThat(window).isNotNull();
    assertThat(window.from()).isEqualTo(harvestWatermark);
  }

  @Test
  @DisplayName("HARVEST 用户提供窗口应该优先使用")
  void shouldUseUserProvidedWindowForHarvest() {
    Instant userFrom = Instant.parse("2024-01-01T00:00:00Z");
    Instant userTo = Instant.parse("2024-01-03T00:00:00Z");
    PlanTriggerNorm triggerNorm = createHarvestTrigger(userFrom, userTo);
    PlannerWindow window = windowResolver.resolveWindow(triggerNorm, null, null, currentTime);
    assertThat(window).isNotNull();
    assertThat(window.from()).isEqualTo(userFrom);
    assertThat(window.to()).isEqualTo(userTo);
  }

  @Test
  @DisplayName("BACKFILL 首次运行应该使用默认窗口大小")
  void shouldUseDefaultWindowSizeForFirstBackfill() {
    PlanTriggerNorm triggerNorm = createBackfillTrigger(null, null);
    PlannerWindow window = windowResolver.resolveWindow(triggerNorm, null, null, currentTime);
    assertThat(window).isNotNull();
    assertThat(Duration.between(window.from(), window.to())).isEqualTo(Duration.ofHours(24));
  }

  @Test
  @DisplayName("UPDATE 时间驱动模式应该使用用户窗口")
  void shouldUseUserWindowForTimeDrivenUpdate() {
    Instant userFrom = Instant.parse("2024-01-01T00:00:00Z");
    Instant userTo = Instant.parse("2024-01-03T00:00:00Z");
    PlanTriggerNorm triggerNorm = createUpdateTrigger(userFrom, userTo);
    PlannerWindow window = windowResolver.resolveWindow(triggerNorm, null, null, currentTime);
    assertThat(window).isNotNull();
    assertThat(window.from()).isEqualTo(userFrom);
    assertThat(window.to()).isEqualTo(userTo);
  }

  private PlanTriggerNorm createHarvestTrigger(Instant userFrom, Instant userTo) {
    return new PlanTriggerNorm(
        123L,
        ProvenanceCode.PUBMED,
        OperationCode.HARVEST,
        "initial",
        TriggerType.SCHEDULE,
        Scheduler.XXL,
        "job-123",
        "log-456",
        userFrom,
        userTo,
        Priority.NORMAL,
        null);
  }

  private PlanTriggerNorm createBackfillTrigger(Instant userFrom, Instant userTo) {
    return new PlanTriggerNorm(
        124L,
        ProvenanceCode.PUBMED,
        OperationCode.BACKFILL,
        "initial",
        TriggerType.SCHEDULE,
        Scheduler.XXL,
        "job-124",
        "log-457",
        userFrom,
        userTo,
        Priority.NORMAL,
        null);
  }

  private PlanTriggerNorm createUpdateTrigger(Instant userFrom, Instant userTo) {
    return new PlanTriggerNorm(
        125L,
        ProvenanceCode.PUBMED,
        OperationCode.UPDATE,
        "initial",
        TriggerType.SCHEDULE,
        Scheduler.XXL,
        "job-125",
        "log-458",
        userFrom,
        userTo,
        Priority.NORMAL,
        null);
  }
}
