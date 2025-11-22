package com.patra.ingest.app.usecase.plan.window;

import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.plan.PlanTriggerNorm;
import com.patra.ingest.domain.model.vo.plan.PlannerWindow;
import java.time.Instant;

/// Planning Window 解析策略(策略接口)
/// 
/// **输入**: 触发规范 + Provenance/Source 配置快照 + 前置游标水位线 + 当前时间 
/// 
/// **输出**: UTC 时区的执行窗口,半开区间 [from, to)
/// 
/// 该接口抽象了窗口计算逻辑,以支持不同的业务模式(HARVEST/BACKFILL/UPDATE 等)和各数据源的特定配置。
/// 
/// ### 实现者的关键考量
/// 
/// - **操作模式**: HARVEST(增量)、BACKFILL(历史回填)、UPDATE(对账/修正)
///   - **用户提供窗口的优先级**: 手动窗口 > 游标引导 > 默认值
///   - **游标水位线**: 决定回溯行为及是否可能出现空窗口(gap)
///   - **安全滞后(watermarkLagSeconds)**: 通过 now 封顶,避免近实时不一致
///   - **日历对齐(calendar_align_to)**: 若对齐后 from == to,视为空窗口
///   - **窗口长度限制**: 可选地强制最大跨度或最小有效长度
/// 
/// ### 返回语义
/// 
/// - **非 null**: 有效的半开窗口
///   - **null**: 全量扫描(无窗口边界)。实现者应仅在语义明确为全量扫描时返回 null
/// 
/// ### 性能复杂度
/// 
/// 典型实现应保持 O(1) 时间复杂度,避免外部 IO。
/// 
/// ### 线程安全
/// 
/// 实现应是无状态的,或确保内部状态线程安全,以便单例复用。
/// 
/// ### 扩展思路
/// 
/// 可能的扩展包括: 多窗口分段、动态策略选择、降级链。
/// 
/// @author linqibin
/// @since 0.1.0
public interface PlanningWindowResolver {

  /// 解析执行窗口
/// 
/// 实现可根据操作模式分支。如需多种游标概念(如水位线 vs. 前向游标), 建议扩展触发规范或快照以传递所需上下文。
/// 
/// @param triggerNorm 触发规范(操作类型、可选的手动窗口输入、模式枚举等)
/// @param snapshot Provenance/Source 配置快照(可为 null;实现者应在缺失时降级)
/// @param cursorWatermark 当前游标水位线(上次成功处理的结束时间;null 表示首次运行)
/// @param currentTime 当前时间(由调用者注入,便于测试和确定性)
/// @return 有效窗口;null 表示全量扫描或无需窗口约束
  PlannerWindow resolveWindow(
      PlanTriggerNorm triggerNorm,
      ProvenanceConfigSnapshot snapshot,
      Instant cursorWatermark,
      Instant currentTime);
}
