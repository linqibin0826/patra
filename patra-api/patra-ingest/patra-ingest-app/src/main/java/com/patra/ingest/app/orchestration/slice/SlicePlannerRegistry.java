package com.patra.ingest.app.orchestration.slice;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 切片策略注册中心。
 * <p>
 * 负责在应用启动期间登记所有 {@link SlicePlanner} 实现，并提供按策略编码检索的能力。
 * 该组件属于应用层基础设施，支撑策略扩展与热插拔。
 * </p>
 *
 * <p>注册行为遵循“第一次注册生效”原则，避免外部多重定义覆盖。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class SlicePlannerRegistry {

    /** 策略到实现的映射表。 */
    private final Map<SliceStrategy, SlicePlanner> planners = new EnumMap<>(SliceStrategy.class);

    /**
     * 构造函数：在 Spring 上下文构建期间注册所有策略实现。
     *
     * @param plannerList Spring 注入的策略实现集合
     */
    public SlicePlannerRegistry(List<SlicePlanner> plannerList) {
        for (SlicePlanner planner : plannerList) {
            planners.putIfAbsent(planner.code(), planner);
            log.debug("Slice planner registered, code={}, impl={}", planner.code().getCode(), planner.getClass().getName());
        }
    }

    /**
     * 获取指定策略的实现。
     *
     * @param strategy 策略枚举
     * @return 匹配的策略实现，未注册时返回 null
     */
    public SlicePlanner get(SliceStrategy strategy) {
        return planners.get(strategy);
    }
}
