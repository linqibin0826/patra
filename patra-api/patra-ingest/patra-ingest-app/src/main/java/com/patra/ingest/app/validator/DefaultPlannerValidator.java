package com.patra.ingest.app.validator;

import cn.hutool.core.util.StrUtil;
import com.patra.ingest.domain.exception.PlanValidationException;
import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认计划器验证器实现：提供窗口背压 + 来源能力 + 窗口合理性等基础校验。
 * <p>阈值策略：
 * <ul>
 *   <li>DEFAULT_QUEUE_THRESHOLD=50：超出判定为背压，需暂缓触发。</li>
 *   <li>MAX_REASONABLE_WINDOW=30d：防止一次性规划过大时间跨度导致任务膨胀。</li>
 *   <li>MIN_REASONABLE_WINDOW=1min：避免毫秒级或过小切片浪费。</li>
 * </ul>
 * UPDATE 操作允许缺失窗口；HARVEST 操作需确保窗口或增量能力配置完整。</p>
 */
@Slf4j
@Component
public class DefaultPlannerValidator implements PlannerValidator {

    private static final long DEFAULT_QUEUE_THRESHOLD = 50L;
    private static final Duration MAX_REASONABLE_WINDOW = Duration.ofDays(30);
    private static final Duration MIN_REASONABLE_WINDOW = Duration.ofMinutes(1);

    @Override
    public void validateBeforeAssemble(PlanTriggerNorm triggerNorm,
                                       ProvenanceConfigSnapshot snapshot,
                                       PlannerWindow window,
                                       long currentQueuedTasks) {
        log.debug("Validating plan assembly, provenance={}, operation={}, window={}, queuedTasks={}",
                triggerNorm.provenanceCode(), triggerNorm.operationCode(), window, currentQueuedTasks);

        // 1. 验证窗口合理性
        validateWindow(triggerNorm, window);
        
        // 2. 验证队列背压
        validateQueueBackpressure(currentQueuedTasks);
        
        // 3. 验证来源配置能力
        validateSourceCapabilities(triggerNorm, snapshot, window);
        
        log.debug("Plan assembly validation passed");
    }

    /**
     * 验证窗口合理性：存在性/时间顺序/跨度上下限。
     */
    private void validateWindow(PlanTriggerNorm triggerNorm, PlannerWindow window) {
        // UPDATE 操作允许空窗口
        if (triggerNorm.isUpdate()) {
            log.debug("Update operation detected, allowing null window");
            return;
        }

        if (window == null) {
            throw new PlanValidationException("计划窗口不能为空", PlanValidationException.Reason.WINDOW_MISSING);
        }

        // 非 UPDATE 操作需要有效窗口
        if (window.from() == null || window.to() == null) {
            throw new PlanValidationException(
                    String.format("Time window is required for %s operation", triggerNorm.operationCode()),
                    PlanValidationException.Reason.WINDOW_MISSING);
        }

        // 验证窗口时间顺序
        if (!window.from().isBefore(window.to())) {
            throw new PlanValidationException(
                    String.format("Invalid window: from=%s must be before to=%s", window.from(), window.to()),
                    PlanValidationException.Reason.WINDOW_INVALID);
        }

        // 验证窗口大小合理性
        Duration windowDuration = Duration.between(window.from(), window.to());
        if (windowDuration.compareTo(MAX_REASONABLE_WINDOW) > 0) {
            throw new PlanValidationException(
                    String.format("Window too large: %d days exceeds maximum %d days",
                            windowDuration.toDays(), MAX_REASONABLE_WINDOW.toDays()),
                    PlanValidationException.Reason.WINDOW_TOO_LARGE);
        }

        if (windowDuration.compareTo(MIN_REASONABLE_WINDOW) < 0) {
            throw new PlanValidationException(
                    String.format("Window too small: %d seconds below minimum %d seconds",
                            windowDuration.toSeconds(), MIN_REASONABLE_WINDOW.toSeconds()),
                    PlanValidationException.Reason.WINDOW_TOO_SMALL);
        }

        log.debug("Window validation passed, duration={}min", windowDuration.toMinutes());
    }

    /**
     * 验证队列背压：当前排队任务超过阈值阻断新规划，避免系统过载。
     */
    private void validateQueueBackpressure(long currentQueuedTasks) {
        if (currentQueuedTasks > DEFAULT_QUEUE_THRESHOLD) {
            throw new PlanValidationException(
                    String.format("Too many queued tasks (%d > %d), applying backpressure on plan trigger",
                            currentQueuedTasks, DEFAULT_QUEUE_THRESHOLD),
                    PlanValidationException.Reason.QUEUE_BACKPRESSURE);
        }
        
        log.debug("Queue backpressure check passed, queuedTasks={}", currentQueuedTasks);
    }

    /**
     * 验证来源能力与偏移、窗口配置完整性。
     */
    private void validateSourceCapabilities(PlanTriggerNorm triggerNorm, 
                                            ProvenanceConfigSnapshot snapshot, 
                                            PlannerWindow window) {
        
        if (snapshot == null) {
            log.warn("Provenance config snapshot is missing, skip capability validation");
            return;
        }

        // 检查 HARVEST 操作的增量能力
        if (triggerNorm.isHarvest()) {
            validateIncrementalCapability(triggerNorm, snapshot, window);
        }

        // 检查窗口配置完整性
        if (!triggerNorm.isUpdate() && window != null && window.from() != null && window.to() != null) {
            validateWindowConfigCompleteness(snapshot);
        }
    }

    /**
     * 验证增量采集能力：若声明支持增量（非 FULL），需具备偏移字段；否则要求显式窗口。
     */
    private void validateIncrementalCapability(PlanTriggerNorm triggerNorm,
                                               ProvenanceConfigSnapshot snapshot,
                                               PlannerWindow window) {
        
        ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset = snapshot.windowOffset();
        
        // 如果没有窗口配置，但要求时间窗口，则需要手动指定窗口
        if (windowOffset == null || StrUtil.equalsIgnoreCase(windowOffset.windowModeCode(), "FULL")) {
            if (triggerNorm.requestedWindowFrom() == null && window != null && window.from() != null) {
                throw new PlanValidationException(
                        String.format("Source %s does not support automatic incremental harvest; explicit window required",
                                triggerNorm.provenanceCode()),
                        PlanValidationException.Reason.CAPABILITY_MISMATCH);
            }
        }

        // 检查偏移字段配置
        if (windowOffset != null &&
                (StrUtil.equalsIgnoreCase(windowOffset.offsetTypeCode(), "DATE")
                        || StrUtil.equalsIgnoreCase(windowOffset.offsetTypeCode(), "COMPOSITE"))) {

            if (StrUtil.isBlank(windowOffset.offsetFieldName()) && StrUtil.isBlank(windowOffset.defaultDateFieldName())) {
                throw new PlanValidationException(
                        String.format("Source %s configured for %s offset but missing date field configuration",
                                triggerNorm.provenanceCode(), windowOffset.offsetTypeCode()),
                        PlanValidationException.Reason.CAPABILITY_MISMATCH);
            }
        }

        log.debug("Incremental capability validation passed, source={}", triggerNorm.provenanceCode());
    }

    /**
     * 验证窗口模式相关配置（窗口尺寸、最大跨度等）；仅警告，不中断。
     */
    private void validateWindowConfigCompleteness(ProvenanceConfigSnapshot snapshot) {
        ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset = snapshot.windowOffset();
        
        if (windowOffset != null && !StrUtil.equalsIgnoreCase(windowOffset.windowModeCode(), "FULL")) {
            // 验证窗口大小配置
            if (windowOffset.windowSizeValue() == null || windowOffset.windowSizeValue() <= 0) {
                log.warn("window size not configured or invalid, fallback to defaults");
            }

            // 验证最大窗口跨度
            if (windowOffset.maxWindowSpanSeconds() != null && windowOffset.maxWindowSpanSeconds() <= 0) {
                log.warn("invalid max window span configuration: {}", windowOffset.maxWindowSpanSeconds());
            }
        }
    }
}
