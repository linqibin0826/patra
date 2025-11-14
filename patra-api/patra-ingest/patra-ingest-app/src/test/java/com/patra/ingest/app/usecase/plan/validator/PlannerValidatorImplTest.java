package com.patra.ingest.app.usecase.plan.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.common.enums.Priority;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.exception.PlanValidationException;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.plan.PlanTriggerNorm;
import com.patra.ingest.domain.model.vo.plan.PlannerWindow;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link PlannerValidatorImpl} 单元测试
 *
 * <p>测试策略: 纯单元测试,验证各种验证场景和边界条件
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("PlannerValidatorImpl 单元测试")
class PlannerValidatorImplTest {

  private PlannerValidatorImpl validator;

  @BeforeEach
  void setUp() {
    validator = new PlannerValidatorImpl();
  }

  @Nested
  @DisplayName("validateBeforeAssemble - 主验证入口")
  class ValidateBeforeAssembleTest {

    @Test
    @DisplayName("应该通过所有验证 - UPDATE 操作无窗口")
    void shouldPassAllValidations_UpdateOperationWithoutWindow() {
      // Given: UPDATE 操作,无窗口,队列正常
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.UPDATE, null, null);
      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When & Then: 验证通过
      assertThatCode(() -> validator.validateBeforeAssemble(triggerNorm, snapshot, null, 10L))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("应该通过所有验证 - HARVEST 操作有效窗口")
    void shouldPassAllValidations_HarvestOperationWithValidWindow() {
      // Given: HARVEST 操作,有效窗口,队列正常
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.HARVEST, from, to);
      PlannerWindow window = new PlannerWindow(from, to);
      ProvenanceConfigSnapshot snapshot = createSnapshotWithIncrementalCapability();

      // When & Then: 验证通过
      assertThatCode(() -> validator.validateBeforeAssemble(triggerNorm, snapshot, window, 10L))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("应该失败 - 队列背压超过阈值")
    void shouldFail_QueueBackpressureExceeded() {
      // Given: 队列任务数超过阈值
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.UPDATE, null, null);
      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();
      long queuedTasks = 51L; // 超过默认阈值50

      // When & Then: 抛出队列背压异常
      assertThatThrownBy(
              () -> validator.validateBeforeAssemble(triggerNorm, snapshot, null, queuedTasks))
          .isInstanceOf(PlanValidationException.class)
          .hasMessageContaining("待处理任务过多")
          .extracting(ex -> ((PlanValidationException) ex).getReason())
          .isEqualTo(PlanValidationException.Reason.QUEUE_BACKPRESSURE);
    }
  }

  @Nested
  @DisplayName("validateWindow - 窗口验证")
  class ValidateWindowTest {

    @Test
    @DisplayName("应该允许 UPDATE 操作使用 null 窗口")
    void shouldAllowNullWindow_ForUpdateOperation() {
      // Given: UPDATE 操作,无窗口
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.UPDATE, null, null);

      // When & Then: 验证通过
      assertThatCode(
              () ->
                  validator.validateBeforeAssemble(triggerNorm, createMinimalSnapshot(), null, 10L))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("应该拒绝 HARVEST 操作使用 null 窗口")
    void shouldRejectNullWindow_ForHarvestOperation() {
      // Given: HARVEST 操作,无窗口
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.HARVEST, null, null);

      // When & Then: 抛出窗口缺失异常
      assertThatThrownBy(
              () ->
                  validator.validateBeforeAssemble(triggerNorm, createMinimalSnapshot(), null, 10L))
          .isInstanceOf(PlanValidationException.class)
          .hasMessageContaining("must not be null")
          .extracting(ex -> ((PlanValidationException) ex).getReason())
          .isEqualTo(PlanValidationException.Reason.WINDOW_MISSING);
    }

    @Test
    @DisplayName("应该拒绝窗口 from 为 null")
    void shouldRejectWindow_WithNullFrom() {
      // Given: 窗口 from 为 null
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.HARVEST, null, to);
      PlannerWindow window = new PlannerWindow(null, to);

      // When & Then: 抛出窗口缺失异常
      assertThatThrownBy(
              () ->
                  validator.validateBeforeAssemble(
                      triggerNorm, createMinimalSnapshot(), window, 10L))
          .isInstanceOf(PlanValidationException.class)
          .hasMessageContaining("需要时间窗口")
          .extracting(ex -> ((PlanValidationException) ex).getReason())
          .isEqualTo(PlanValidationException.Reason.WINDOW_MISSING);
    }

    @Test
    @DisplayName("应该拒绝窗口 to 为 null")
    void shouldRejectWindow_WithNullTo() {
      // Given: 窗口 to 为 null
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.HARVEST, from, null);
      PlannerWindow window = new PlannerWindow(from, null);

      // When & Then: 抛出窗口缺失异常
      assertThatThrownBy(
              () ->
                  validator.validateBeforeAssemble(
                      triggerNorm, createMinimalSnapshot(), window, 10L))
          .isInstanceOf(PlanValidationException.class)
          .hasMessageContaining("需要时间窗口")
          .extracting(ex -> ((PlanValidationException) ex).getReason())
          .isEqualTo(PlanValidationException.Reason.WINDOW_MISSING);
    }

    @Test
    @DisplayName("应该拒绝无效窗口 - from 不早于 to")
    void shouldRejectInvalidWindow_FromNotBeforeTo() {
      // Given: from 和 to 相同
      Instant time = Instant.parse("2025-01-01T00:00:00Z");

      // When & Then: PlannerWindow 构造时抛出异常
      assertThatThrownBy(() -> new PlannerWindow(time, time))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("窗口起始时间必须早于结束时间");
    }

    @Test
    @DisplayName("应该拒绝窗口过大 - 超过30天")
    void shouldRejectWindow_TooLarge() {
      // Given: 窗口跨度31天
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = from.plus(Duration.ofDays(31));
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.HARVEST, from, to);
      PlannerWindow window = new PlannerWindow(from, to);

      // When & Then: 抛出窗口过大异常
      assertThatThrownBy(
              () ->
                  validator.validateBeforeAssemble(
                      triggerNorm, createMinimalSnapshot(), window, 10L))
          .isInstanceOf(PlanValidationException.class)
          .hasMessageContaining("窗口过大")
          .extracting(ex -> ((PlanValidationException) ex).getReason())
          .isEqualTo(PlanValidationException.Reason.WINDOW_TOO_LARGE);
    }

    @Test
    @DisplayName("应该接受窗口 - 恰好30天")
    void shouldAcceptWindow_Exactly30Days() {
      // Given: 窗口跨度恰好30天
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = from.plus(Duration.ofDays(30));
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.HARVEST, from, to);
      PlannerWindow window = new PlannerWindow(from, to);

      // When & Then: 验证通过
      assertThatCode(
              () ->
                  validator.validateBeforeAssemble(
                      triggerNorm, createSnapshotWithIncrementalCapability(), window, 10L))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("应该拒绝窗口过小 - 小于1分钟")
    void shouldRejectWindow_TooSmall() {
      // Given: 窗口跨度30秒
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = from.plus(Duration.ofSeconds(30));
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.HARVEST, from, to);
      PlannerWindow window = new PlannerWindow(from, to);

      // When & Then: 抛出窗口过小异常
      assertThatThrownBy(
              () ->
                  validator.validateBeforeAssemble(
                      triggerNorm, createMinimalSnapshot(), window, 10L))
          .isInstanceOf(PlanValidationException.class)
          .hasMessageContaining("窗口过小")
          .extracting(ex -> ((PlanValidationException) ex).getReason())
          .isEqualTo(PlanValidationException.Reason.WINDOW_TOO_SMALL);
    }

    @Test
    @DisplayName("应该接受窗口 - 恰好1分钟")
    void shouldAcceptWindow_Exactly1Minute() {
      // Given: 窗口跨度恰好1分钟
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = from.plus(Duration.ofMinutes(1));
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.HARVEST, from, to);
      PlannerWindow window = new PlannerWindow(from, to);

      // When & Then: 验证通过
      assertThatCode(
              () ->
                  validator.validateBeforeAssemble(
                      triggerNorm, createSnapshotWithIncrementalCapability(), window, 10L))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("validateQueueBackpressure - 队列背压验证")
  class ValidateQueueBackpressureTest {

    @Test
    @DisplayName("应该通过验证 - 队列任务数为0")
    void shouldPassValidation_QueueEmpty() {
      // Given: 队列为空
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.UPDATE, null, null);

      // When & Then: 验证通过
      assertThatCode(
              () ->
                  validator.validateBeforeAssemble(triggerNorm, createMinimalSnapshot(), null, 0L))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("应该通过验证 - 队列任务数恰好50")
    void shouldPassValidation_QueueExactly50() {
      // Given: 队列任务数恰好50
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.UPDATE, null, null);

      // When & Then: 验证通过
      assertThatCode(
              () ->
                  validator.validateBeforeAssemble(triggerNorm, createMinimalSnapshot(), null, 50L))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("应该失败 - 队列任务数为51")
    void shouldFailValidation_Queue51Tasks() {
      // Given: 队列任务数51
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.UPDATE, null, null);

      // When & Then: 抛出队列背压异常
      assertThatThrownBy(
              () ->
                  validator.validateBeforeAssemble(triggerNorm, createMinimalSnapshot(), null, 51L))
          .isInstanceOf(PlanValidationException.class)
          .hasMessageContaining("待处理任务过多")
          .hasMessageContaining("51 > 50")
          .extracting(ex -> ((PlanValidationException) ex).getReason())
          .isEqualTo(PlanValidationException.Reason.QUEUE_BACKPRESSURE);
    }

    @Test
    @DisplayName("应该失败 - 队列任务数极大")
    void shouldFailValidation_QueueVeryLarge() {
      // Given: 队列任务数1000
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.UPDATE, null, null);

      // When & Then: 抛出队列背压异常
      assertThatThrownBy(
              () ->
                  validator.validateBeforeAssemble(
                      triggerNorm, createMinimalSnapshot(), null, 1000L))
          .isInstanceOf(PlanValidationException.class)
          .extracting(ex -> ((PlanValidationException) ex).getReason())
          .isEqualTo(PlanValidationException.Reason.QUEUE_BACKPRESSURE);
    }
  }

  @Nested
  @DisplayName("validateSourceCapabilities - 数据源能力验证")
  class ValidateSourceCapabilitiesTest {

    @Test
    @DisplayName("应该跳过验证 - 配置快照为 null")
    void shouldSkipValidation_SnapshotIsNull() {
      // Given: 配置快照为 null
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.HARVEST, from, to);
      PlannerWindow window = new PlannerWindow(from, to);

      // When & Then: 验证通过(跳过能力验证)
      assertThatCode(() -> validator.validateBeforeAssemble(triggerNorm, null, window, 10L))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("应该通过验证 - HARVEST 操作有增量能力")
    void shouldPassValidation_HarvestWithIncrementalCapability() {
      // Given: HARVEST 操作,配置了增量能力
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.HARVEST, from, to);
      PlannerWindow window = new PlannerWindow(from, to);
      ProvenanceConfigSnapshot snapshot = createSnapshotWithIncrementalCapability();

      // When & Then: 验证通过
      assertThatCode(() -> validator.validateBeforeAssemble(triggerNorm, snapshot, window, 10L))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("应该失败 - HARVEST 操作无增量能力且无显式窗口")
    void shouldFailValidation_HarvestWithoutIncrementalCapabilityAndNoExplicitWindow() {
      // Given: HARVEST 操作,FULL 模式,triggerNorm.requestedWindowFrom 为 null,但有推断窗口
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlanTriggerNorm triggerNorm =
          createTriggerNorm(OperationCode.HARVEST, null, null); // requestedWindowFrom=null
      PlannerWindow window = new PlannerWindow(from, to); // 但推断出了窗口
      ProvenanceConfigSnapshot snapshot = createSnapshotWithFullMode();

      // When & Then: 抛出能力不匹配异常
      assertThatThrownBy(() -> validator.validateBeforeAssemble(triggerNorm, snapshot, window, 10L))
          .isInstanceOf(PlanValidationException.class)
          .hasMessageContaining("不支持自动增量采集")
          .extracting(ex -> ((PlanValidationException) ex).getReason())
          .isEqualTo(PlanValidationException.Reason.CAPABILITY_MISMATCH);
    }

    @Test
    @DisplayName("应该失败 - DATE 偏移类型缺少日期字段配置")
    void shouldFailValidation_DateOffsetTypeMissingDateField() {
      // Given: DATE 偏移类型,但缺少日期字段配置
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.HARVEST, from, to);
      PlannerWindow window = new PlannerWindow(from, to);
      ProvenanceConfigSnapshot snapshot = createSnapshotWithDateOffsetMissingFields();

      // When & Then: 抛出能力不匹配异常
      assertThatThrownBy(() -> validator.validateBeforeAssemble(triggerNorm, snapshot, window, 10L))
          .isInstanceOf(PlanValidationException.class)
          .hasMessageContaining("但缺少日期字段配置")
          .extracting(ex -> ((PlanValidationException) ex).getReason())
          .isEqualTo(PlanValidationException.Reason.CAPABILITY_MISMATCH);
    }

    @Test
    @DisplayName("应该通过验证 - DATE 偏移类型有 offsetFieldKey")
    void shouldPassValidation_DateOffsetTypeWithOffsetFieldKey() {
      // Given: DATE 偏移类型,有 offsetFieldKey
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.HARVEST, from, to);
      PlannerWindow window = new PlannerWindow(from, to);
      ProvenanceConfigSnapshot snapshot = createSnapshotWithDateOffsetHasOffsetFieldKey();

      // When & Then: 验证通过
      assertThatCode(() -> validator.validateBeforeAssemble(triggerNorm, snapshot, window, 10L))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("应该通过验证 - COMPOSITE 偏移类型有 windowDateFieldKey")
    void shouldPassValidation_CompositeOffsetTypeWithWindowDateFieldKey() {
      // Given: COMPOSITE 偏移类型,有 windowDateFieldKey
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.HARVEST, from, to);
      PlannerWindow window = new PlannerWindow(from, to);
      ProvenanceConfigSnapshot snapshot = createSnapshotWithCompositeOffsetHasWindowDateFieldKey();

      // When & Then: 验证通过
      assertThatCode(() -> validator.validateBeforeAssemble(triggerNorm, snapshot, window, 10L))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("应该跳过验证 - UPDATE 操作")
    void shouldSkipIncrementalValidation_UpdateOperation() {
      // Given: UPDATE 操作
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.UPDATE, null, null);
      ProvenanceConfigSnapshot snapshot = createMinimalSnapshot();

      // When & Then: 验证通过(UPDATE 不需要增量能力验证)
      assertThatCode(() -> validator.validateBeforeAssemble(triggerNorm, snapshot, null, 10L))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("validateWindowConfigCompleteness - 窗口配置完整性验证")
  class ValidateWindowConfigCompletenessTest {

    @Test
    @DisplayName("应该记录警告 - 窗口大小未配置")
    void shouldLogWarning_WindowSizeNotConfigured() {
      // Given: 窗口大小为 null
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.HARVEST, from, to);
      PlannerWindow window = new PlannerWindow(from, to);
      ProvenanceConfigSnapshot snapshot = createSnapshotWithInvalidWindowSize(null);

      // When & Then: 不抛出异常,只记录警告
      assertThatCode(() -> validator.validateBeforeAssemble(triggerNorm, snapshot, window, 10L))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("应该记录警告 - 窗口大小为0")
    void shouldLogWarning_WindowSizeZero() {
      // Given: 窗口大小为 0
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.HARVEST, from, to);
      PlannerWindow window = new PlannerWindow(from, to);
      ProvenanceConfigSnapshot snapshot = createSnapshotWithInvalidWindowSize(0);

      // When & Then: 不抛出异常,只记录警告
      assertThatCode(() -> validator.validateBeforeAssemble(triggerNorm, snapshot, window, 10L))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("应该记录警告 - 最大窗口跨度无效")
    void shouldLogWarning_MaxWindowSpanInvalid() {
      // Given: 最大窗口跨度为 0
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.HARVEST, from, to);
      PlannerWindow window = new PlannerWindow(from, to);
      ProvenanceConfigSnapshot snapshot = createSnapshotWithInvalidMaxWindowSpan(0);

      // When & Then: 不抛出异常,只记录警告
      assertThatCode(() -> validator.validateBeforeAssemble(triggerNorm, snapshot, window, 10L))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("应该跳过验证 - FULL 模式")
    void shouldSkipValidation_FullMode() {
      // Given: FULL 模式
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-02T00:00:00Z");
      PlanTriggerNorm triggerNorm = createTriggerNorm(OperationCode.HARVEST, from, to);
      PlannerWindow window = new PlannerWindow(from, to);
      ProvenanceConfigSnapshot snapshot = createSnapshotWithFullMode();

      // When & Then: 不抛出异常(FULL 模式跳过配置完整性验证)
      assertThatCode(() -> validator.validateBeforeAssemble(triggerNorm, snapshot, window, 10L))
          .doesNotThrowAnyException();
    }
  }

  // ==================== 辅助方法 ====================

  /**
   * 创建触发规范
   *
   * @param operationCode 操作代码
   * @param requestedFrom 请求的窗口起始时间
   * @param requestedTo 请求的窗口结束时间
   * @return 触发规范
   */
  private PlanTriggerNorm createTriggerNorm(
      OperationCode operationCode, Instant requestedFrom, Instant requestedTo) {
    return new PlanTriggerNorm(
        1L,
        ProvenanceCode.PUBMED,
        operationCode,
        null,
        TriggerType.MANUAL,
        Scheduler.XXL,
        "job-1",
        "log-1",
        requestedFrom,
        requestedTo,
        Priority.NORMAL,
        Map.of());
  }

  /**
   * 创建最小化配置快照
   *
   * @return 最小化配置快照
   */
  private ProvenanceConfigSnapshot createMinimalSnapshot() {
    ProvenanceConfigSnapshot.ProvenanceInfo provenanceInfo =
        new ProvenanceConfigSnapshot.ProvenanceInfo(
            1L, "pubmed", "PubMed", "https://api.pubmed.gov", "UTC", null, true, "ACTIVE");

    return new ProvenanceConfigSnapshot(provenanceInfo, null, null, null, null, null, null);
  }

  /**
   * 创建具有增量能力的配置快照
   *
   * @return 配置快照
   */
  private ProvenanceConfigSnapshot createSnapshotWithIncrementalCapability() {
    ProvenanceConfigSnapshot.ProvenanceInfo provenanceInfo =
        new ProvenanceConfigSnapshot.ProvenanceInfo(
            1L, "pubmed", "PubMed", "https://api.pubmed.gov", "UTC", null, true, "ACTIVE");

    ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset =
        new ProvenanceConfigSnapshot.WindowOffsetConfig(
            1L,
            1L,
            null,
            Instant.parse("2024-01-01T00:00:00Z"),
            null,
            "SLIDING",
            1,
            "DAY",
            null,
            0,
            "DAY",
            0,
            "DAY",
            0,
            "DATE",
            "update_date",
            "ISO_INSTANT",
            "update_date",
            null,
            null);

    return new ProvenanceConfigSnapshot(provenanceInfo, windowOffset, null, null, null, null, null);
  }

  /**
   * 创建 FULL 模式的配置快照
   *
   * @return 配置快照
   */
  private ProvenanceConfigSnapshot createSnapshotWithFullMode() {
    ProvenanceConfigSnapshot.ProvenanceInfo provenanceInfo =
        new ProvenanceConfigSnapshot.ProvenanceInfo(
            1L, "pubmed", "PubMed", "https://api.pubmed.gov", "UTC", null, true, "ACTIVE");

    ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset =
        new ProvenanceConfigSnapshot.WindowOffsetConfig(
            1L,
            1L,
            null,
            Instant.parse("2024-01-01T00:00:00Z"),
            null,
            "FULL", // FULL 模式
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    return new ProvenanceConfigSnapshot(provenanceInfo, windowOffset, null, null, null, null, null);
  }

  /**
   * 创建 DATE 偏移类型缺少日期字段配置的配置快照
   *
   * @return 配置快照
   */
  private ProvenanceConfigSnapshot createSnapshotWithDateOffsetMissingFields() {
    ProvenanceConfigSnapshot.ProvenanceInfo provenanceInfo =
        new ProvenanceConfigSnapshot.ProvenanceInfo(
            1L, "pubmed", "PubMed", "https://api.pubmed.gov", "UTC", null, true, "ACTIVE");

    ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset =
        new ProvenanceConfigSnapshot.WindowOffsetConfig(
            1L,
            1L,
            null,
            Instant.parse("2024-01-01T00:00:00Z"),
            null,
            "SLIDING",
            1,
            "DAY",
            null,
            0,
            "DAY",
            0,
            "DAY",
            0,
            "DATE",
            null, // offsetFieldKey 为 null
            "ISO_INSTANT",
            null, // windowDateFieldKey 为 null
            null,
            null);

    return new ProvenanceConfigSnapshot(provenanceInfo, windowOffset, null, null, null, null, null);
  }

  /**
   * 创建 DATE 偏移类型有 offsetFieldKey 的配置快照
   *
   * @return 配置快照
   */
  private ProvenanceConfigSnapshot createSnapshotWithDateOffsetHasOffsetFieldKey() {
    ProvenanceConfigSnapshot.ProvenanceInfo provenanceInfo =
        new ProvenanceConfigSnapshot.ProvenanceInfo(
            1L, "pubmed", "PubMed", "https://api.pubmed.gov", "UTC", null, true, "ACTIVE");

    ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset =
        new ProvenanceConfigSnapshot.WindowOffsetConfig(
            1L,
            1L,
            null,
            Instant.parse("2024-01-01T00:00:00Z"),
            null,
            "SLIDING",
            1,
            "DAY",
            null,
            0,
            "DAY",
            0,
            "DAY",
            0,
            "DATE",
            "update_date", // offsetFieldKey 有值
            "ISO_INSTANT",
            null, // windowDateFieldKey 为 null 也可以
            null,
            null);

    return new ProvenanceConfigSnapshot(provenanceInfo, windowOffset, null, null, null, null, null);
  }

  /**
   * 创建 COMPOSITE 偏移类型有 windowDateFieldKey 的配置快照
   *
   * @return 配置快照
   */
  private ProvenanceConfigSnapshot createSnapshotWithCompositeOffsetHasWindowDateFieldKey() {
    ProvenanceConfigSnapshot.ProvenanceInfo provenanceInfo =
        new ProvenanceConfigSnapshot.ProvenanceInfo(
            1L, "pubmed", "PubMed", "https://api.pubmed.gov", "UTC", null, true, "ACTIVE");

    ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset =
        new ProvenanceConfigSnapshot.WindowOffsetConfig(
            1L,
            1L,
            null,
            Instant.parse("2024-01-01T00:00:00Z"),
            null,
            "SLIDING",
            1,
            "DAY",
            null,
            0,
            "DAY",
            0,
            "DAY",
            0,
            "COMPOSITE",
            null, // offsetFieldKey 为 null 也可以
            null,
            "update_date", // windowDateFieldKey 有值
            null,
            null);

    return new ProvenanceConfigSnapshot(provenanceInfo, windowOffset, null, null, null, null, null);
  }

  /**
   * 创建窗口大小无效的配置快照
   *
   * @param windowSizeValue 窗口大小值
   * @return 配置快照
   */
  private ProvenanceConfigSnapshot createSnapshotWithInvalidWindowSize(Integer windowSizeValue) {
    ProvenanceConfigSnapshot.ProvenanceInfo provenanceInfo =
        new ProvenanceConfigSnapshot.ProvenanceInfo(
            1L, "pubmed", "PubMed", "https://api.pubmed.gov", "UTC", null, true, "ACTIVE");

    ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset =
        new ProvenanceConfigSnapshot.WindowOffsetConfig(
            1L,
            1L,
            null,
            Instant.parse("2024-01-01T00:00:00Z"),
            null,
            "SLIDING",
            windowSizeValue, // 窗口大小无效
            "DAY",
            null,
            0,
            "DAY",
            0,
            "DAY",
            0,
            "DATE",
            "update_date",
            "ISO_INSTANT",
            "update_date",
            null,
            null);

    return new ProvenanceConfigSnapshot(provenanceInfo, windowOffset, null, null, null, null, null);
  }

  /**
   * 创建最大窗口跨度无效的配置快照
   *
   * @param maxWindowSpanSeconds 最大窗口跨度秒数
   * @return 配置快照
   */
  private ProvenanceConfigSnapshot createSnapshotWithInvalidMaxWindowSpan(
      Integer maxWindowSpanSeconds) {
    ProvenanceConfigSnapshot.ProvenanceInfo provenanceInfo =
        new ProvenanceConfigSnapshot.ProvenanceInfo(
            1L, "pubmed", "PubMed", "https://api.pubmed.gov", "UTC", null, true, "ACTIVE");

    ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset =
        new ProvenanceConfigSnapshot.WindowOffsetConfig(
            1L,
            1L,
            null,
            Instant.parse("2024-01-01T00:00:00Z"),
            null,
            "SLIDING",
            1,
            "DAY",
            null,
            0,
            "DAY",
            0,
            "DAY",
            0,
            "DATE",
            "update_date",
            "ISO_INSTANT",
            "update_date",
            null,
            maxWindowSpanSeconds); // 最大窗口跨度无效

    return new ProvenanceConfigSnapshot(provenanceInfo, windowOffset, null, null, null, null, null);
  }
}
