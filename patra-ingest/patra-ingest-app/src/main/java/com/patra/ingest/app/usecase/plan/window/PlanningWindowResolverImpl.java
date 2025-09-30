package com.patra.ingest.app.usecase.plan.window;

import static com.patra.ingest.app.usecase.plan.window.support.PlanningWindowSupport.alignFloor;
import static com.patra.ingest.app.usecase.plan.window.support.PlanningWindowSupport.computeLaggedNow;
import static com.patra.ingest.app.usecase.plan.window.support.PlanningWindowSupport.isCalendarMode;
import static com.patra.ingest.app.usecase.plan.window.support.PlanningWindowSupport.maxInstant;
import static com.patra.ingest.app.usecase.plan.window.support.PlanningWindowSupport.minInstant;
import static com.patra.ingest.app.usecase.plan.window.support.PlanningWindowSupport.resolveDuration;
import static com.patra.ingest.app.usecase.plan.window.support.PlanningWindowSupport.resolveWindowSize;
import static com.patra.ingest.app.usecase.plan.window.support.PlanningWindowSupport.resolveZone;

import cn.hutool.core.util.StrUtil;
import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认计划窗口解析实现（HARVEST / BACKFILL / UPDATE）。
 * <p>
 * 聚焦“计划级”窗口（非切片级）；不处理 overlap（切片阶段处理）与跨多游标高级策略。
 * </p>
 * <h4>公共规则</h4>
 * <ul>
 *   <li>nowSafe = min(currentTime - watermarkLag, currentTime)（若配置为空则不减）</li>
 *   <li>窗口半开区间 [from, to)，对齐日历后如 from == to → 视为空窗口（返回最小兜底窗）</li>
 *   <li>若无法判定模式或模式不支持 → 返回 full()（上游可按全量处理）</li>
 *   <li>最小非空长度：小于 1s 时扩展为 1s，避免下游创建 0 时长任务</li>
 * </ul>
 * <h4>HARVEST</h4>
 * <pre>
 * toCandidate   = min(user.to?, nowSafe)
 * fromCandidate = harvestWM? max(user.from?, harvestWM - lookback) : (user.from? | toCandidate - windowSize)
 * CALENDAR 对齐 → 空窗检测
 * </pre>
 * <h4>BACKFILL</h4>
 * <pre>
 * upperAnchor   = min(user.to?, forwardWM?, nowSafe)  // forwardWM 目前未注入，留扩展
 * fromCandidate = backfillWM? max(backfillWM, user.from?) : (user.from? | upperAnchor - windowSize)
 * 边界矫正：fromCandidate 不得 > upperAnchor
 * CALENDAR 对齐 → 空窗检测
 * </pre>
 * <h4>UPDATE</h4>
 * <pre>
 * timeDriven = (offsetType=DATE 且存在用户窗口) 或 (任一用户窗口给定)
 * if timeDriven:
 *   toCandidate   = min(user.to?, nowSafe) (缺省回退 nowSafe)
 *   fromCandidate = 有 updateWM 与 user.from? → max(updateWM, user.from?)
 *                | 仅 updateWM → updateWM（并与 user.from? 比较）
 *                | 仅 user.from → user.from
 *                | 否则 nowSafe - windowSize
 * else (ID 驱动):
 *   若无用户窗口: [nowSafe - windowSize, nowSafe]
 *   否则与 timeDriven 类似，但 fromCandidate = user.from? | (toCandidate - windowSize)
 * CALENDAR 对齐 → 空窗检测
 * </pre>
 * <h4>设计取舍</h4>
 * <ul>
 *   <li>forwardWM 暂未暴露；需要时可扩接口或在 triggerNorm.embed()</li>
 *   <li>maxWindowSpanSeconds 未强行截断：交由切片阶段（TimeSlicePlanner）精细化控制</li>
 *   <li>空窗处理返回“最小有效窗”而非 null：简化调用方判空逻辑，但仍可通过 from==to(+1s 扩展) 识别</li>
 * </ul>
 * <h4>复杂度</h4>
 * <p>所有分支 O(1)，无外部 IO。</p>
 * <h4>线程安全</h4>
 * <p>无状态，可单例。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class PlanningWindowResolverImpl implements PlanningWindowResolver {

    /**
     * 默认总窗口跨度（24 小时）。
     */
    private static final Duration DEFAULT_WINDOW_SIZE = Duration.ofHours(24);
    /**
     * 默认安全延迟，用于修剪“当前时间”。
     */
    private static final Duration DEFAULT_SAFETY_LAG = Duration.ZERO;
    /**
     * 最小有效窗口长度（>0 秒，避免产生空窗）。
     */
    private static final Duration MIN_EFFECTIVE_WINDOW = Duration.ofSeconds(1);

    /**
     * {@inheritDoc}
     */
    @Override
    public PlannerWindow resolveWindow(PlanTriggerNorm triggerNorm,
                                       ProvenanceConfigSnapshot snapshot,
                                       Instant cursorWatermark,
                                       Instant currentTime) {
        ProvenanceConfigSnapshot.WindowOffsetConfig cfg = snapshot == null ? null : snapshot.windowOffset();
        String timezone = snapshot != null && snapshot.provenance() != null
                ? snapshot.provenance().timezoneDefault()
                : null;

        Instant userFrom = triggerNorm.requestedWindowFrom();
        Instant userTo = triggerNorm.requestedWindowTo();

        // 根据配置的安全延迟修剪当前时间，避免未落库数据被纳入
        Instant nowSafe = computeLaggedNow(currentTime, cfg, DEFAULT_SAFETY_LAG);

        if (triggerNorm.isHarvest()) {
            return resolveHarvest(cfg, cursorWatermark, userFrom, userTo, nowSafe, timezone);
        } else if (triggerNorm.isBackfill()) {
            return resolveBackfill(cfg, cursorWatermark, null, userFrom, userTo, nowSafe, timezone);
        } else if (triggerNorm.isUpdate()) {
            return resolveUpdate(cfg, cursorWatermark, userFrom, userTo, nowSafe, timezone);
        }
        return PlannerWindow.full();
    }

    /* ===================== HARVEST ===================== */

    /**
     * 解析 HARVEST 模式窗口。
     * <p>利用当前 HARVEST 游标（harvestWM）+ lookback 回退以避免漏数据；无游标视为首次，从用户 from 或默认跨度推导。</p>
     */
    private PlannerWindow resolveHarvest(ProvenanceConfigSnapshot.WindowOffsetConfig cfg,
                                         Instant harvestWM,
                                         Instant userFrom,
                                         Instant userTo,
                                         Instant nowSafe,
                                         String timezone) {
        Duration windowSize = resolveWindowSize(cfg, DEFAULT_WINDOW_SIZE);
        Duration lookback = resolveDuration(cfg == null ? null : cfg.lookbackValue(),
                cfg == null ? null : cfg.lookbackUnitCode(), Duration.ZERO);

        Instant toCandidate = minInstant(userTo, nowSafe);
        if (toCandidate == null) {
            toCandidate = nowSafe; // 没有用户上界 → 使用 nowSafe
        }

        Instant fromCandidate;
        if (harvestWM != null) {
            Instant lowerByCursor = harvestWM.minus(lookback);
            fromCandidate = maxInstant(lowerByCursor, userFrom);
        } else if (userFrom != null) {
            fromCandidate = userFrom;
        } else {
            fromCandidate = toCandidate.minus(windowSize); // 默认回退一窗
        }

        if (isCalendarMode(cfg) && cfg != null) {
            ZoneId zone = resolveZone(timezone);
            fromCandidate = alignFloor(fromCandidate, cfg.calendarAlignTo(), zone);
            toCandidate = alignFloor(toCandidate, cfg.calendarAlignTo(), zone);
        }

        if (!toCandidate.isAfter(fromCandidate)) {
            log.debug("[INGEST][APP] HARVEST window empty after alignment: {} >= {}", fromCandidate, toCandidate);
            return nullWindowIfEmpty(fromCandidate, toCandidate);
        }
        return safeWindow(fromCandidate, toCandidate);
    }

    /* ===================== BACKFILL ===================== */

    /**
     * 解析 BACKFILL 模式窗口。
     * <p>BACKFILL 游标（backfillWM）控制最小 from，上界受用户 to / nowSafe 约束；forwardWM 预留。</p>
     */
    private PlannerWindow resolveBackfill(ProvenanceConfigSnapshot.WindowOffsetConfig cfg,
                                          Instant backfillWM,
                                          Instant forwardWM,
                                          Instant userFrom,
                                          Instant userTo,
                                          Instant nowSafe,
                                          String timezone) {
        Duration windowSize = resolveWindowSize(cfg, DEFAULT_WINDOW_SIZE);

        // forwardWM = harvestWM 作为 upperAnchor 候选，这里 cursorWatermark 已按调用方传入对应 BACKFILL 游标；需要读取前向水位？
        // 设计中 forwardWM 来源 HARVEST 水位：此策略接口当前只拿到单一 cursorWatermark。为了不扩接口，这里假设传入的是 BACKFILL 游标；
        // 若需要 forwardWM 需扩展接口或在上层额外查询后放入 triggerParams；暂以 cursorWatermark 仅当 backfillWM 使用。
        Instant upperAnchor = minInstant(userTo, forwardWM, nowSafe);
        if (upperAnchor == null) {
            upperAnchor = nowSafe;
        }

        Instant fromCandidate;
        if (backfillWM != null) {
            fromCandidate = maxInstant(backfillWM, userFrom);
        } else if (userFrom != null) {
            fromCandidate = userFrom;
        } else {
            fromCandidate = upperAnchor.minus(windowSize);
        }
        if (fromCandidate.isAfter(upperAnchor)) {
            fromCandidate = upperAnchor; // 防越界
        }

        if (isCalendarMode(cfg) && cfg != null) {
            ZoneId zone = resolveZone(timezone);
            fromCandidate = alignFloor(fromCandidate, cfg.calendarAlignTo(), zone);
            upperAnchor = alignFloor(upperAnchor, cfg.calendarAlignTo(), zone);
        }
        if (!upperAnchor.isAfter(fromCandidate)) {
            log.debug("[INGEST][APP] BACKFILL window empty: {} >= {}", fromCandidate, upperAnchor);
            return nullWindowIfEmpty(fromCandidate, upperAnchor);
        }
        return safeWindow(fromCandidate, upperAnchor);
    }

    /* ===================== UPDATE ===================== */

    /**
     * 解析 UPDATE 模式窗口。
     * <p>区分 timeDriven 和 ID 驱动；timeDriven 依赖用户窗口或日期型 offsetType。</p>
     */
    private PlannerWindow resolveUpdate(ProvenanceConfigSnapshot.WindowOffsetConfig cfg,
                                        Instant updateWM,
                                        Instant userFrom,
                                        Instant userTo,
                                        Instant nowSafe,
                                        String timezone) {
        Duration windowSize = resolveWindowSize(cfg, DEFAULT_WINDOW_SIZE);
        boolean timeDriven = (cfg != null && StrUtil.equalsIgnoreCase(cfg.offsetTypeCode(), "DATE") && (userFrom != null || userTo != null))
                || (userFrom != null || userTo != null);

        Instant fromCandidate;
        Instant toCandidate;
        if (timeDriven) {
            toCandidate = minInstant(userTo, nowSafe);
            if (toCandidate == null) {
                toCandidate = nowSafe;
            }
            if (updateWM != null && userFrom != null) {
                fromCandidate = maxInstant(updateWM, userFrom);
            } else if (updateWM != null) {
                fromCandidate = updateWM;
                if (userFrom != null && userFrom.isAfter(fromCandidate)) {
                    fromCandidate = userFrom;
                }
            } else if (userFrom != null) {
                fromCandidate = userFrom;
            } else {
                fromCandidate = nowSafe.minus(windowSize);
            }
        } else { // ID 驱动
            if (userFrom != null || userTo != null) {
                toCandidate = minInstant(userTo, nowSafe);
                if (toCandidate == null) {
                    toCandidate = nowSafe;
                }
                fromCandidate = userFrom != null ? userFrom : toCandidate.minus(windowSize);
            } else {
                toCandidate = nowSafe;
                fromCandidate = nowSafe.minus(windowSize);
            }
        }

        if (isCalendarMode(cfg) && cfg != null) {
            ZoneId zone = resolveZone(timezone);
            fromCandidate = alignFloor(fromCandidate, cfg.calendarAlignTo(), zone);
            toCandidate = alignFloor(toCandidate, cfg.calendarAlignTo(), zone);
        }
        if (!toCandidate.isAfter(fromCandidate)) {
            log.debug("[INGEST][APP] UPDATE window empty: {} >= {}", fromCandidate, toCandidate);
            return nullWindowIfEmpty(fromCandidate, toCandidate);
        }
        return safeWindow(fromCandidate, toCandidate);
    }

    /* ===================== Helpers ===================== */

    /**
     * 构造安全窗口：若长度 < 最小阈值则扩展 to，避免 0 时长。
     */
    private PlannerWindow safeWindow(Instant from, Instant to) {
        if (Duration.between(from, to).compareTo(MIN_EFFECTIVE_WINDOW) < 0) {
            to = from.plus(MIN_EFFECTIVE_WINDOW);
        }
        return new PlannerWindow(from, to);
    }

    /**
     * 空窗兜底：对齐后 from >= to 时，返回 from → from+MIN_EFFECTIVE_WINDOW，供上游继续流程，同时可检测“原始空窗”。
     */
    private PlannerWindow nullWindowIfEmpty(Instant from, Instant to) {
        // 返回一个最小窗口以便上层可感知 empty，再由 validator 处理；避免 IllegalArgumentException
        return new PlannerWindow(from, from.plus(MIN_EFFECTIVE_WINDOW));
    }
}
