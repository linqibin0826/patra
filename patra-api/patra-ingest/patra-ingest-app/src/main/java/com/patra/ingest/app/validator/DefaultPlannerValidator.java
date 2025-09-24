package com.patra.ingest.app.validator;

import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 默认计划器验证器实现。
 * <p>负责在计划装配前进行预验证，确保配置合理性和系统健康状态。</p>
 * <p>验证维度：
 * <ul>
 *   <li>窗口合理性验证</li>
 *   <li>队列背压检查</li>
 *   <li>来源能力与操作类型匹配性</li>
 *   <li>窗口配置完整性</li>
 * </ul></p>
 * 
 * @author linqibin
 * @since 0.1.0
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
        
        log.debug("validating plan assembly for provenance={}, operation={}, window={}, queuedTasks={}",
                triggerNorm.provenanceCode(), triggerNorm.operationCode(), window, currentQueuedTasks);

        // 1. 验证窗口合理性
        validateWindow(triggerNorm, window);
        
        // 2. 验证队列背压
        validateQueueBackpressure(currentQueuedTasks);
        
        // 3. 验证来源配置能力
        validateSourceCapabilities(triggerNorm, snapshot, window);
        
        log.debug("plan assembly validation passed");
    }

    /**
     * 验证窗口合理性。
     */
    private void validateWindow(PlanTriggerNorm triggerNorm, PlannerWindow window) {
        // UPDATE 操作允许空窗口
        if (triggerNorm.isUpdate()) {
            log.debug("UPDATE operation, allowing null window");
            return;
        }

        // 非 UPDATE 操作需要有效窗口
        if (window.from() == null || window.to() == null) {
            throw new IllegalStateException(
                    String.format("Time window is required for %s operation", triggerNorm.operationCode()));
        }

        // 验证窗口时间顺序
        if (!window.from().isBefore(window.to())) {
            throw new IllegalStateException(
                    String.format("Invalid window: from=%s must be before to=%s", window.from(), window.to()));
        }

        // 验证窗口大小合理性
        Duration windowDuration = Duration.between(window.from(), window.to());
        if (windowDuration.compareTo(MAX_REASONABLE_WINDOW) > 0) {
            throw new IllegalStateException(
                    String.format("Window too large: %d days exceeds maximum %d days", 
                            windowDuration.toDays(), MAX_REASONABLE_WINDOW.toDays()));
        }

        if (windowDuration.compareTo(MIN_REASONABLE_WINDOW) < 0) {
            throw new IllegalStateException(
                    String.format("Window too small: %d seconds below minimum %d seconds",
                            windowDuration.toSeconds(), MIN_REASONABLE_WINDOW.toSeconds()));
        }

        log.debug("window validation passed: duration={}min", windowDuration.toMinutes());
    }

    /**
     * 验证队列背压。
     */
    private void validateQueueBackpressure(long currentQueuedTasks) {
        if (currentQueuedTasks > DEFAULT_QUEUE_THRESHOLD) {
            throw new IllegalStateException(
                    String.format("Too many queued tasks (%d > %d), applying backpressure on plan trigger",
                            currentQueuedTasks, DEFAULT_QUEUE_THRESHOLD));
        }
        
        log.debug("queue backpressure check passed: {} tasks queued", currentQueuedTasks);
    }

    /**
     * 验证来源配置能力。
     */
    private void validateSourceCapabilities(PlanTriggerNorm triggerNorm, 
                                            ProvenanceConfigSnapshot snapshot, 
                                            PlannerWindow window) {
        
        // 检查 HARVEST 操作的增量能力
        if (triggerNorm.isHarvest()) {
            validateIncrementalCapability(triggerNorm, snapshot, window);
        }

        // 检查窗口配置完整性
        if (!triggerNorm.isUpdate() && window.from() != null && window.to() != null) {
            validateWindowConfigCompleteness(snapshot);
        }
    }

    /**
     * 验证增量采集能力。
     */
    private void validateIncrementalCapability(PlanTriggerNorm triggerNorm,
                                               ProvenanceConfigSnapshot snapshot,
                                               PlannerWindow window) {
        
        ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset = snapshot.windowOffset();
        
        // 如果没有窗口配置，但要求时间窗口，则需要手动指定窗口
        if (windowOffset == null || "FULL".equals(windowOffset.windowModeCode())) {
            if (triggerNorm.requestedWindowFrom() == null && window.from() != null) {
                throw new IllegalStateException(
                        String.format("Source %s does not support automatic incremental harvest; explicit window required",
                                triggerNorm.provenanceCode()));
            }
        }

        // 检查偏移字段配置
        if (windowOffset != null && 
            ("DATE".equals(windowOffset.offsetTypeCode()) || "COMPOSITE".equals(windowOffset.offsetTypeCode()))) {
            
            if (isBlank(windowOffset.offsetFieldName()) && isBlank(windowOffset.defaultDateFieldName())) {
                throw new IllegalStateException(
                        String.format("Source %s configured for %s offset but missing date field configuration",
                                triggerNorm.provenanceCode(), windowOffset.offsetTypeCode()));
            }
        }

        log.debug("incremental capability validation passed for source={}", triggerNorm.provenanceCode());
    }

    /**
     * 验证窗口配置完整性。
     */
    private void validateWindowConfigCompleteness(ProvenanceConfigSnapshot snapshot) {
        ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset = snapshot.windowOffset();
        
        if (windowOffset != null && !"FULL".equals(windowOffset.windowModeCode())) {
            // 验证窗口大小配置
            if (windowOffset.windowSizeValue() == null || windowOffset.windowSizeValue() <= 0) {
                log.warn("Window size not configured or invalid, will use defaults");
            }
            
            // 验证最大窗口跨度
            if (windowOffset.maxWindowSpanSeconds() != null && windowOffset.maxWindowSpanSeconds() <= 0) {
                log.warn("Invalid max window span configuration: {}", windowOffset.maxWindowSpanSeconds());
            }
        }
    }

    /**
     * 检查字符串是否为空。
     */
    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}

