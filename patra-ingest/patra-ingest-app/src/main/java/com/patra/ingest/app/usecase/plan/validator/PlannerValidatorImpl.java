package com.patra.ingest.app.usecase.plan.validator;

import cn.hutool.core.util.StrUtil;
import com.patra.ingest.domain.exception.PlanValidationException;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.plan.PlanTriggerNorm;
import com.patra.ingest.domain.model.vo.plan.PlannerWindow;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link PlannerValidator} 的默认实现 - 执行基线检查,包括窗口合理性、队列背压和数据源能力验证
 *
 * <h3>阈值策略</h3>
 *
 * <ul>
 *   <li><b>DEFAULT_QUEUE_THRESHOLD = 50</b>: 待处理任务超过此值时应用背压
 *   <li><b>MAX_REASONABLE_WINDOW = 30 天</b>: 防止过大窗口导致任务量暴增
 *   <li><b>MIN_REASONABLE_WINDOW = 1 分钟</b>: 避免极小切片浪费资源
 * </ul>
 *
 * <h3>窗口要求</h3>
 *
 * <ul>
 *   <li><b>UPDATE 操作</b>: 可省略窗口
 *   <li><b>HARVEST 操作</b>: 必须提供显式窗口或增量能力配置
 * </ul>
 */
@Slf4j
@Component
public class PlannerValidatorImpl implements PlannerValidator {

  private static final long DEFAULT_QUEUE_THRESHOLD = 50L;
  private static final Duration MAX_REASONABLE_WINDOW = Duration.ofDays(30);
  private static final Duration MIN_REASONABLE_WINDOW = Duration.ofMinutes(1);

  @Override
  public void validateBeforeAssemble(
      PlanTriggerNorm triggerNorm,
      ProvenanceConfigSnapshot snapshot,
      PlannerWindow window,
      long currentQueuedTasks) {
    log.debug(
        "正在验证 Plan 组装,provenance={}, operation={}, window={}, queuedTasks={}",
        triggerNorm.provenanceCode(),
        triggerNorm.operationCode(),
        window,
        currentQueuedTasks);

    // 步骤 1: 验证窗口合理性
    validateWindow(triggerNorm, window);

    // 步骤 2: 强制执行队列背压
    validateQueueBackpressure(currentQueuedTasks);

    // 步骤 3: 确保 Provenance 能力与触发器对齐
    validateSourceCapabilities(triggerNorm, snapshot, window);

    log.debug("Plan 组装验证通过");
  }

  /** 验证采集窗口: 存在性、时序、持续时间边界 */
  private void validateWindow(PlanTriggerNorm triggerNorm, PlannerWindow window) {
    // UPDATE 操作可无需窗口
    if (triggerNorm.isUpdate()) {
      log.debug("检测到 UPDATE 操作,允许 null 窗口");
      return;
    }

    if (window == null) {
      throw new PlanValidationException(
          "Plan window must not be null", PlanValidationException.Reason.WINDOW_MISSING);
    }

    // 非 UPDATE 操作需要完整的边界
    if (window.from() == null || window.to() == null) {
      throw new PlanValidationException(
          String.format("%s 操作需要时间窗口", triggerNorm.operationCode()),
          PlanValidationException.Reason.WINDOW_MISSING);
    }

    // 强制执行时序
    if (!window.from().isBefore(window.to())) {
      throw new PlanValidationException(
          String.format("无效窗口: from=%s 必须早于 to=%s", window.from(), window.to()),
          PlanValidationException.Reason.WINDOW_INVALID);
    }

    // 确保窗口持续时间在合理范围内
    Duration windowDuration = Duration.between(window.from(), window.to());
    if (windowDuration.compareTo(MAX_REASONABLE_WINDOW) > 0) {
      throw new PlanValidationException(
          String.format(
              "窗口过大: %d 天超过最大值 %d 天", windowDuration.toDays(), MAX_REASONABLE_WINDOW.toDays()),
          PlanValidationException.Reason.WINDOW_TOO_LARGE);
    }

    if (windowDuration.compareTo(MIN_REASONABLE_WINDOW) < 0) {
      throw new PlanValidationException(
          String.format(
              "窗口过小: %d 秒低于最小值 %d 秒",
              windowDuration.toSeconds(), MIN_REASONABLE_WINDOW.toSeconds()),
          PlanValidationException.Reason.WINDOW_TOO_SMALL);
    }

    log.debug("窗口验证通过,持续时间={}分钟", windowDuration.toMinutes());
  }

  /** 强制执行队列背压: 待处理任务超过阈值时停止规划 */
  private void validateQueueBackpressure(long currentQueuedTasks) {
    if (currentQueuedTasks > DEFAULT_QUEUE_THRESHOLD) {
      throw new PlanValidationException(
          String.format(
              "待处理任务过多 (%d > %d),对 Plan 触发应用背压", currentQueuedTasks, DEFAULT_QUEUE_THRESHOLD),
          PlanValidationException.Reason.QUEUE_BACKPRESSURE);
    }
    log.debug("队列背压检查通过,queuedTasks={}", currentQueuedTasks);
  }

  /** 验证数据源能力以及偏移量/窗口完整性 */
  private void validateSourceCapabilities(
      PlanTriggerNorm triggerNorm, ProvenanceConfigSnapshot snapshot, PlannerWindow window) {

    if (snapshot == null) {
      log.warn("Provenance 配置快照缺失,跳过能力验证");
      return;
    }

    // 验证 HARVEST 操作的增量能力
    if (triggerNorm.isHarvest()) {
      validateIncrementalCapability(triggerNorm, snapshot, window);
    }

    // 若存在窗口,验证窗口相关配置
    if (!triggerNorm.isUpdate() && window != null && window.from() != null && window.to() != null) {
      validateWindowConfigCompleteness(snapshot);
    }
  }

  /** 验证增量采集能力 - 若数据源声明增量模式(非 FULL),必须暴露偏移量配置;否则需要显式窗口 */
  private void validateIncrementalCapability(
      PlanTriggerNorm triggerNorm, ProvenanceConfigSnapshot snapshot, PlannerWindow window) {

    ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset = snapshot.windowOffset();

    // 若未配置增量能力,需要显式窗口
    if (windowOffset == null || StrUtil.equalsIgnoreCase(windowOffset.windowModeCode(), "FULL")) {
      if (triggerNorm.requestedWindowFrom() == null && window != null && window.from() != null) {
        throw new PlanValidationException(
            String.format("数据源 %s 不支持自动增量采集;需要显式窗口", triggerNorm.provenanceCode()),
            PlanValidationException.Reason.CAPABILITY_MISMATCH);
      }
    }

    // 验证日期/复合偏移量的字段配置
    if (windowOffset != null
        && (StrUtil.equalsIgnoreCase(windowOffset.offsetTypeCode(), "DATE")
            || StrUtil.equalsIgnoreCase(windowOffset.offsetTypeCode(), "COMPOSITE"))) {

      if (StrUtil.isBlank(windowOffset.offsetFieldKey())
          && StrUtil.isBlank(windowOffset.windowDateFieldKey())) {
        throw new PlanValidationException(
            String.format(
                "数据源 %s 配置为 %s 偏移量但缺少日期字段配置",
                triggerNorm.provenanceCode(), windowOffset.offsetTypeCode()),
            PlanValidationException.Reason.CAPABILITY_MISMATCH);
      }
    }

    log.debug("增量能力验证通过,数据源={}", triggerNorm.provenanceCode());
  }

  /** 当可选窗口配置(大小/跨度)无效时发出警告 */
  private void validateWindowConfigCompleteness(ProvenanceConfigSnapshot snapshot) {
    ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset = snapshot.windowOffset();

    if (windowOffset != null && !StrUtil.equalsIgnoreCase(windowOffset.windowModeCode(), "FULL")) {
      // 窗口大小缺失或无效时发出警告
      if (windowOffset.windowSizeValue() == null || windowOffset.windowSizeValue() <= 0) {
        log.warn("窗口大小未配置或无效,将使用默认值");
      }

      // 最大窗口跨度无效时发出警告
      if (windowOffset.maxWindowSpanSeconds() != null && windowOffset.maxWindowSpanSeconds() <= 0) {
        log.warn("最大窗口跨度配置无效: {}", windowOffset.maxWindowSpanSeconds());
      }
    }
  }
}
