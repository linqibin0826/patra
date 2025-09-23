package com.patra.ingest.app.policy;

import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 默认计划器窗口策略实现。
 * <p>负责基于配置快照和运行时状态，生成合适的执行窗口。</p>
 * <p>策略优先级：
 * <ol>
 *   <li>手动指定窗口（triggerNorm 中的 requestedWindowFrom/To）</li>
 *   <li>UPDATE 操作（返回空窗口，表示全量更新）</li>
 *   <li>基于 WindowOffsetConfig 配置生成窗口</li>
 *   <li>降级到默认硬编码策略</li>
 * </ol></p>
 * <p>窗口配置字段映射：
 * <ul>
 *   <li>{@code windowModeCode}: SLIDING|CALENDAR|FULL - 窗口模式</li>
 *   <li>{@code windowSizeValue + windowSizeUnitCode}: 窗口大小</li>
 *   <li>{@code overlapValue + overlapUnitCode}: 重叠量（用于游标水位线回溯）</li>
 *   <li>{@code lookbackValue + lookbackUnitCode}: 回溯量（初始窗口向前扩展）</li>
 *   <li>{@code watermarkLagSeconds}: 水位滞后秒数（安全延迟）</li>
 *   <li>{@code maxWindowSpanSeconds}: 最大窗口跨度限制</li>
 * </ul></p>
 * <p>典型使用场景：
 * <ul>
 *   <li><strong>PubMed Update</strong>: SLIDING + windowSize=1DAY + overlap=1DAY</li>
 *   <li><strong>Crossref Harvest</strong>: SLIDING + 基于indexed字段的增量抓取</li>
 *   <li><strong>全量刷新</strong>: FULL 模式或 windowModeCode=null</li>
 * </ul></p>
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class DefaultPlannerWindowPolicy implements PlannerWindowPolicy {

    private static final Duration DEFAULT_HARVEST_WINDOW = Duration.ofDays(1);
    private static final Duration DEFAULT_SAFETY_LAG = Duration.ofHours(1);
    private static final Duration DEFAULT_MIN_WINDOW = Duration.ofMinutes(1);
    private static final Duration DEFAULT_MAX_WINDOW = Duration.ofDays(7);

    @Override
    public PlannerWindow resolveWindow(PlanTriggerNorm triggerNorm,
                                       ProvenanceConfigSnapshot snapshot,
                                       Instant cursorWatermark,
                                       Instant currentTime) {
        
        log.debug("resolving window for provenance={}, operation={}, trigger={}",
                triggerNorm.provenanceCode(), triggerNorm.operationType(), triggerNorm.triggerType());

        // 1. UPDATE 操作返回空窗口（全量更新）
        if (triggerNorm.isUpdate()) {
            log.debug("UPDATE operation detected, returning null window for full refresh");
            return new PlannerWindow(null, null);
        }

        // 2. 手动指定窗口优先级最高
        if (triggerNorm.requestedWindowFrom() != null && triggerNorm.requestedWindowTo() != null) {
            log.debug("using manual window: {} to {}", triggerNorm.requestedWindowFrom(), triggerNorm.requestedWindowTo());
            return new PlannerWindow(triggerNorm.requestedWindowFrom(), triggerNorm.requestedWindowTo());
        }

        // 3. 基于 WindowOffsetConfig 生成窗口
        return buildWindowFromConfig(snapshot.windowOffset(), cursorWatermark, currentTime);
    }

    /**
     * 基于 WindowOffsetConfig 构建时间窗口。
     */
    private PlannerWindow buildWindowFromConfig(ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset,
                                                Instant cursorWatermark,
                                                Instant currentTime) {
        
        // 检查是否启用窗口模式
        if (windowOffset == null || "FULL".equals(windowOffset.windowModeCode())) {
            log.debug("full mode detected or no window config, returning null window for full refresh");
            return new PlannerWindow(null, null);
        }

        // 解析安全延迟 (watermark lag)
        Duration watermarkLag = Optional.ofNullable(windowOffset.watermarkLagSeconds())
                .map(Duration::ofSeconds)
                .orElse(DEFAULT_SAFETY_LAG);

        // 确定窗口起始时间
        Instant from = determineWindowStart(windowOffset, cursorWatermark, currentTime, watermarkLag);
        
        // 确定窗口结束时间
        Instant to = determineWindowEnd(windowOffset, from, currentTime, watermarkLag);
        
        // 确保结束时间晚于起始时间
        if (!to.isAfter(from)) {
            to = from.plusSeconds(1);
            log.warn("adjusted window end time to avoid invalid range: from={}, to={}", from, to);
        }

        // 应用窗口大小限制
        to = applyWindowSizeConstraints(windowOffset, from, to);

        log.debug("built time window: {} to {} (duration={}min)", 
                from, to, Duration.between(from, to).toMinutes());

        return new PlannerWindow(from, to);
    }

    /**
     * 确定窗口起始时间，考虑重叠和回溯逻辑。
     */
    private Instant determineWindowStart(ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset,
                                         Instant cursorWatermark,
                                         Instant currentTime,
                                         Duration watermarkLag) {
        if (cursorWatermark != null) {
            // 有游标水位线时，考虑重叠量向前回溯
            Duration overlapDuration = parseOverlapDuration(windowOffset);
            Instant from = cursorWatermark.minus(overlapDuration);
            log.debug("using cursor watermark with overlap: cursor={}, overlap={}min, from={}", 
                    cursorWatermark, overlapDuration.toMinutes(), from);
            return from;
        }

        // 无游标时，基于当前时间和配置计算起始时间
        Duration windowSize = parseWindowSize(windowOffset);
        Duration lookbackDuration = parseLookbackDuration(windowOffset);
        
        // 计算基准时间点（当前时间减去水位滞后）
        Instant baseTime = currentTime.minus(watermarkLag);
        
        // 应用回溯和窗口大小
        Instant from = baseTime.minus(windowSize).minus(lookbackDuration);
        
        log.debug("calculated window start: baseTime={}, windowSize={}min, lookback={}min, from={}", 
                baseTime, windowSize.toMinutes(), lookbackDuration.toMinutes(), from);
        return from;
    }

    /**
     * 确定窗口结束时间。
     */
    private Instant determineWindowEnd(ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset,
                                       Instant from,
                                       Instant currentTime,
                                       Duration watermarkLag) {
        
        // 基于窗口大小计算结束时间
        Duration windowSize = parseWindowSize(windowOffset);
        Instant calculatedTo = from.plus(windowSize);
        
        // 不能超过当前时间减去水位滞后
        Instant maxTo = currentTime.minus(watermarkLag);
        
        Instant to = calculatedTo.isBefore(maxTo) ? calculatedTo : maxTo;
        
        log.debug("determined window end: calculatedTo={}, maxTo={}, finalTo={}", 
                calculatedTo, maxTo, to);
        return to;
    }

    /**
     * 解析重叠量配置。
     */
    private Duration parseOverlapDuration(ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset) {
        if (windowOffset.overlapValue() == null) {
            return Duration.ZERO;
        }

        int value = windowOffset.overlapValue();
        String unit = Objects.toString(windowOffset.overlapUnitCode(), "MINUTES").toUpperCase();

        // TODO 用枚举（Common模块）
        Duration duration = switch (unit) {
            case "SECONDS" -> Duration.ofSeconds(value);
            case "MINUTES" -> Duration.ofMinutes(value);
            case "HOURS" -> Duration.ofHours(value);
            case "DAYS" -> Duration.ofDays(value);
            default -> {
                log.warn("unknown overlap unit: {}, using minutes", unit);
                yield Duration.ofMinutes(value);
            }
        };

        log.debug("parsed overlap duration: {} {} = {}min", value, unit, duration.toMinutes());
        return duration;
    }

    /**
     * 解析回溯量配置。
     */
    private Duration parseLookbackDuration(ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset) {
        if (windowOffset.lookbackValue() == null) {
            return Duration.ZERO;
        }

        int value = windowOffset.lookbackValue();
        String unit = Objects.toString(windowOffset.lookbackUnitCode(), "MINUTES").toUpperCase();

        Duration duration = switch (unit) {
            case "SECONDS" -> Duration.ofSeconds(value);
            case "MINUTES" -> Duration.ofMinutes(value);
            case "HOURS" -> Duration.ofHours(value);
            case "DAYS" -> Duration.ofDays(value);
            default -> {
                log.warn("unknown lookback unit: {}, using minutes", unit);
                yield Duration.ofMinutes(value);
            }
        };

        log.debug("parsed lookback duration: {} {} = {}min", value, unit, duration.toMinutes());
        return duration;
    }

    /**
     * 解析窗口大小配置。
     */
    private Duration parseWindowSize(ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset) {
        if (windowOffset.windowSizeValue() == null) {
            return DEFAULT_HARVEST_WINDOW;
        }

        int value = windowOffset.windowSizeValue();
        String unit = Objects.toString(windowOffset.windowSizeUnitCode(), "HOURS").toUpperCase();

        // TODO
        Duration duration = switch (unit) {
            case "SECONDS" -> Duration.ofSeconds(value);
            case "MINUTES" -> Duration.ofMinutes(value);
            case "HOURS" -> Duration.ofHours(value);
            case "DAYS" -> Duration.ofDays(value);
            default -> {
                log.warn("unknown window size unit: {}, using default", unit);
                yield DEFAULT_HARVEST_WINDOW;
            }
        };

        log.debug("parsed window size: {} {} = {}min", value, unit, duration.toMinutes());
        return duration;
    }

    /**
     * 应用窗口大小约束。
     */
    private Instant applyWindowSizeConstraints(ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset,
                                               Instant from,
                                               Instant to) {
        Instant constrainedTo = to;

        // 应用最大窗口限制
        if (windowOffset.maxWindowSpanSeconds() != null) {
            Duration maxWindow = Duration.ofSeconds(windowOffset.maxWindowSpanSeconds());
            Instant maxTo = from.plus(maxWindow);
            if (constrainedTo.isAfter(maxTo)) {
                constrainedTo = maxTo;
                log.debug("applied max window constraint: {}min", maxWindow.toMinutes());
            }
        } else {
            // 使用默认最大窗口限制
            Instant maxTo = from.plus(DEFAULT_MAX_WINDOW);
            if (constrainedTo.isAfter(maxTo)) {
                constrainedTo = maxTo;
                log.debug("applied default max window constraint: {}min", DEFAULT_MAX_WINDOW.toMinutes());
            }
        }

        // 应用最小窗口限制（使用默认值，因为配置中没有专门的最小窗口字段）
        Instant minTo = from.plus(DEFAULT_MIN_WINDOW);
        if (constrainedTo.isBefore(minTo)) {
            constrainedTo = minTo;
            log.debug("applied default min window constraint: {}min", DEFAULT_MIN_WINDOW.toMinutes());
        }

        return constrainedTo;
    }
}

