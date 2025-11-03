package com.patra.ingest.app.usecase.plan.slicer;

import com.patra.ingest.domain.model.enums.SliceStrategy;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 切片策略注册表(应用层·注册表)
 *
 * <p>职责:按 {@link SliceStrategy} 收集和索引 {@link SlicePlanner} 实现。在规划期间提供 O(1) 查找,避免 if-else/switch 爆炸。
 *
 * <p>设计与约束:
 *
 * <ul>
 *   <li>通过构造函数批量注册所有 Spring 注入的 {@link SlicePlanner} Bean;之后只读。
 *   <li>忽略 null;冲突时后注册覆盖先注册(支持灰度替换)。
 *   <li>使用 {@link EnumMap} 实现常数时间访问和低内存占用。
 *   <li>线程安全:运行时无并发写入;仅读取。
 *   <li>可扩展:通过添加 Spring Bean 实现来添加策略。
 *   <li>失败模式:策略未找到时返回 null(调用方决定回退/错误)。
 * </ul>
 */
@Component
public class SlicePlannerRegistry {

  /** 策略 → 实现映射;运行时只读 */
  private final Map<SliceStrategy, SlicePlanner> registry = new EnumMap<>(SliceStrategy.class);

  /** 批量注册所有发现的 {@link SlicePlanner} Bean 的构造函数 */
  public SlicePlannerRegistry(List<SlicePlanner> planners) {
    if (planners == null) {
      return;
    }
    for (SlicePlanner planner : planners) {
      if (planner == null || planner.code() == null) {
        continue;
      }
      registry.put(planner.code(), planner);
    }
  }

  /** 返回给定策略的规划器,缺失时返回 null */
  public SlicePlanner get(SliceStrategy strategy) {
    if (strategy == null) {
      return null;
    }
    return registry.get(strategy);
  }
}
