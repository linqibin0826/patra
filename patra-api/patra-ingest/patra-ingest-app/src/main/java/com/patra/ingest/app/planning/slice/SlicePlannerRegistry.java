package com.patra.ingest.app.planning.slice;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 切片策略注册表（Application Layer · Registry）。
 * <p>
 * 职责：收集并按 {@link SliceStrategy} 索引具体 {@link SlicePlanner} 实现，
 * 在规划阶段通过策略枚举快速 O(1) 定位执行策略，避免 if-else / switch 模式膨胀。
 * </p>
 * <p>设计与约束：
 * <ul>
 *   <li>启动期通过构造函数批量注册（Spring 注入全部 {@link SlicePlanner} Bean），注册后只读。</li>
 *   <li>忽略 null；策略冲突时后注册覆盖先注册（支持灰度替换）。</li>
 *   <li>内部使用 {@link EnumMap}，常量时间访问 + 更低内存占用。</li>
 *   <li>线程安全：运行期无并发写操作，仅读访问。</li>
 *   <li>扩展：新增策略仅需新增实现并标记为 Spring Bean。</li>
 *   <li>失败模式：请求不存在策略返回 null（交由上层决定降级 / 报错）。</li>
 * </ul>
 * </p>
 */
@Component
public class SlicePlannerRegistry {

    /** 策略 -> 实现映射，运行期只读。 */
    private final Map<SliceStrategy, SlicePlanner> registry = new EnumMap<>(SliceStrategy.class);

    /**
     * 构造并批量注册所有已发现的 {@link SlicePlanner} Bean。
     * @param planners Spring 注入的全量策略实现列表（可能为空）
     */
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

    /**
     * 依据策略代码获取对应切片器。
     * @param strategy 策略枚举
     * @return 匹配的实现，不存在返回 null
     */
    public SlicePlanner get(SliceStrategy strategy) {
        if (strategy == null) {
            return null;
        }
        return registry.get(strategy);
    }
}
