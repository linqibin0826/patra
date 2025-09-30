package com.patra.ingest.app.usecase.plan.window;

import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;

import java.time.Instant;

/**
 * 计划窗口解析策略（Policy Interface）。
 * <p>
 * 输入：触发规范 + 来源配置快照 + 上次游标水位 + 当前时间；输出：计划执行窗口（UTC 半开区间 [from, to)）。
 * 该接口抽象窗口确定算法，支持不同业务模式（HARVEST/BACKFILL/UPDATE 等）与来源差异化配置。
 * </p>
 * <h4>实现需关注的核心要素</h4>
 * <ul>
 *   <li>操作模式差异：HARVEST（增量）、BACKFILL（历史补回）、UPDATE（回溯修正）</li>
 *   <li>用户手动窗口优先级：手动指定优先，其次游标推导，再次默认跨度</li>
 *   <li>游标水位（cursorWatermark）：决定起点回退（lookback）与是否产生空窗</li>
 *   <li>安全延迟（watermarkLagSeconds）：裁剪 now，防止近实时数据未最终一致</li>
 *   <li>日历对齐（calendar_align_to）：对齐后若 from == to → 视为空窗</li>
 *   <li>窗口长度限制：可根据配置限制最大跨度或保证最小有效长度</li>
 * </ul>
 * <h4>返回值语义</h4>
 * <ul>
 *   <li>非 null：有效窗口（半开区间）</li>
 *   <li>null：全量处理（无窗口边界限制）；实现应谨慎返回 null，仅在确认为全量语义时使用</li>
 * </ul>
 * <h4>复杂度</h4>
 * <p>典型实现应保持 O(1) 时间复杂度，不做外部 IO。</p>
 * <h4>线程安全</h4>
 * <p>实现类应无状态或确保内部状态并发安全，允许单例复用。</p>
 * <h4>扩展点</h4>
 * <p>可新增：多窗口分段、动态策略选择、窗口回退（fallback）链。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PlanningWindowResolver {

    /**
     * 解析执行窗口。
     * <p>实现内部可按操作模式分支；若需要区分多种游标（水位 / 前向水位等），应通过扩展 triggerNorm 或 snapshot。</p>
     *
     * @param triggerNorm     触发规范（包含操作类型、用户窗口输入、模式枚举等）
     * @param snapshot        来源配置快照（可为 null，需自行回退默认）
     * @param cursorWatermark 当前游标水位（上次成功处理终点，可为 null 表示首次）
     * @param currentTime     当前时间（调用方注入，便于测试与回放）
     * @return 有效窗口；返回 null 表示使用“全量模式”或无需窗口限制
     */
    PlannerWindow resolveWindow(PlanTriggerNorm triggerNorm,
                                ProvenanceConfigSnapshot snapshot,
                                Instant cursorWatermark,
                                Instant currentTime);
}
