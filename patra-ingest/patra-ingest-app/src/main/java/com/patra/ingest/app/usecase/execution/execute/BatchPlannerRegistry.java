package com.patra.ingest.app.usecase.execution.execute;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 批次规划器注册表。
 * <p>
 * 职责：管理所有 BatchPlanner 实例，按 provenanceCode 路由到对应的规划器。
 * </p>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>自动注册：通过 Spring 构造函数注入所有 BatchPlanner 实例。</li>
 *   <li>线程安全：使用 ConcurrentHashMap 存储映射关系。</li>
 *   <li>异常处理：找不到规划器时抛出 IllegalArgumentException。</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Component
@Slf4j
public class BatchPlannerRegistry {

    private final Map<String, BatchPlanner> planners = new ConcurrentHashMap<>();

    /**
     * 构造函数：自动注册所有 BatchPlanner 实例。
     *
     * @param plannerList Spring 注入的所有 BatchPlanner 实例
     */
    public BatchPlannerRegistry(List<BatchPlanner> plannerList) {
        for (BatchPlanner planner : plannerList) {
            String provenanceCode = planner.getProvenanceCode();
            if (planners.containsKey(provenanceCode)) {
                log.warn("[INGEST][APP] duplicate batch planner for provenanceCode={}", provenanceCode);
            }
            planners.put(provenanceCode, planner);
            log.info("[INGEST][APP] registered batch planner provenanceCode={} class={}",
                     provenanceCode, planner.getClass().getSimpleName());
        }
    }

    /**
     * 根据数据源编码获取批次规划器。
     *
     * @param provenanceCode 数据源编码
     * @return 批次规划器
     * @throws IllegalArgumentException 找不到规划器时抛出
     */
    public BatchPlanner get(String provenanceCode) {
        BatchPlanner planner = planners.get(provenanceCode);
        if (planner == null) {
            throw new IllegalArgumentException(
                "未找到批次规划器 provenanceCode=" + provenanceCode
                + " 可用规划器: " + planners.keySet()
            );
        }
        return planner;
    }

    /**
     * 检查是否存在指定数据源的规划器。
     */
    public boolean contains(String provenanceCode) {
        return planners.containsKey(provenanceCode);
    }
}
