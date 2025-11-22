package com.patra.ingest.domain.model.vo.plan;

import java.time.Instant;

/// 规划窗口值对象,表示任务规划的时间窗口。
/// 
/// 使用UTC半开区间 `[from, to)`, `null`值表示无界/全量范围。
/// 
/// 业务约束:
/// 
/// - 如果from和to都非空,则from必须早于to
/// 
/// 典型用法:
/// 
/// - {@link #full()} 返回用于全量刷新的无界窗口
///   - `new PlannerWindow(from, to)` 提供具体的时间切片
///   - {@link #isFull()} 检查窗口是否无界
///   - {@link #isEmpty()} 检测无效(非前向)范围
/// 
/// @param from 起始时间(闭区间,可空表示无下界)
/// @param to 结束时间(开区间,可空表示无上界)
public record PlannerWindow(Instant from, Instant to) {
  public PlannerWindow {
    if (from != null && to != null && !from.isBefore(to)) {
      throw new IllegalArgumentException("窗口起始时间必须早于结束时间");
    }
  }

  /// 检查窗口是否为空或无效(from >= to)。
/// 
/// @return 如果窗口为空或无效则返回true
  public boolean isEmpty() {
    return from != null && to != null && !from.isBefore(to);
  }

  /// 检查窗口是否为无界(from和to都为null)。
/// 
/// @return 如果窗口无界则返回true
  public boolean isFull() {
    return from == null && to == null;
  }

  /// 创建无界/全量窗口(`from = null`, `to = null`)。
/// 
/// @return 无界规划窗口
  public static PlannerWindow full() {
    return new PlannerWindow(null, null);
  }
}
