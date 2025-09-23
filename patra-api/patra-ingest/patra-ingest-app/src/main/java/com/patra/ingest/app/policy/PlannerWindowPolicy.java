package com.patra.ingest.app.policy;

import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;

import java.time.Instant;

/**
 * 计划器窗口策略接口。
 * <p>负责基于触发规范、配置快照和运行时状态，计算出合适的执行窗口。</p>
 * <p>策略实现需要考虑：
 * <ul>
 *   <li>操作类型（HARVEST vs UPDATE）</li>
 *   <li>手动指定窗口的优先级</li>
 *   <li>游标水位线的处理</li>
 *   <li>安全延迟和窗口大小限制</li>
 *   <li>来源特定的配置策略（基于 {@link ProvenanceConfigSnapshot.WindowOffsetConfig}）</li>
 * </ul></p>
 * <p>返回的 {@link PlannerWindow} 使用 UTC 时间的半开区间 [from, to)。</p>
 * 
 * @author linqibin
 * @since 0.1.0
 */
public interface PlannerWindowPolicy {

    /**
     * 解析执行窗口。
     * 
     * @param triggerNorm     触发规范，包含操作类型、手动窗口等信息
     * @param snapshot        来源配置快照，包含 WindowOffsetConfig 等配置
     * @param cursorWatermark 当前游标水位线（上次处理到的时间点）
     * @param currentTime     当前时间（用于计算相对窗口）
     * @return 计算出的执行窗口，null 窗口表示全量处理
     */
    PlannerWindow resolveWindow(PlanTriggerNorm triggerNorm,
                                ProvenanceConfigSnapshot snapshot,
                                Instant cursorWatermark,
                                Instant currentTime);
}

