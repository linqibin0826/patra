package com.patra.ingest.app.usecase.execution.strategy.planner;

import com.patra.common.enums.ProvenanceCode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 批处理规划器注册中心
 *
 * <p>职责: 管理所有 {@link BatchPlanner} 实例,根据 provenanceCode 路由。
 *
 * <h3>设计要点</h3>
 *
 * <ul>
 *   <li><b>自动注册</b>: 通过 Spring 构造器注入自动注册所有实现
 *   <li><b>线程安全</b>: 使用 {@link ConcurrentHashMap} 保证并发安全
 *   <li><b>异常处理</b>: 规划器未找到时抛出 {@link IllegalArgumentException}
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Component
@Slf4j
public class BatchPlannerRegistry {

  private final Map<String, BatchPlanner> planners = new ConcurrentHashMap<>();

  /**
   * 构造器 - 自动注册所有批处理规划器实例
   *
   * @param plannerList 所有由 Spring 注入的 BatchPlanner 实例
   */
  public BatchPlannerRegistry(List<BatchPlanner> plannerList) {
    for (BatchPlanner planner : plannerList) {
      ProvenanceCode provenanceCode = planner.getProvenanceCode();
      String code = provenanceCode.getCode();
      if (planners.containsKey(code)) {
        log.warn("检测到重复的批处理规划器,provenanceCode={}", code);
      }
      planners.put(code, planner);
      log.info("已注册批处理规划器: provenanceCode={}, class={}", code, planner.getClass().getSimpleName());
    }
  }

  /**
   * 根据 Provenance 代码获取批处理规划器
   *
   * @param provenanceCode Provenance 代码
   * @return 批处理规划器
   * @throws IllegalArgumentException 规划器未找到时抛出
   */
  public BatchPlanner get(ProvenanceCode provenanceCode) {
    if (provenanceCode == null) {
      throw new IllegalArgumentException("provenanceCode 不能为 null");
    }
    String code = provenanceCode.getCode();
    BatchPlanner planner = planners.get(code);
    if (planner == null) {
      throw new IllegalArgumentException(
          "未找到批处理规划器: provenanceCode=" + provenanceCode + "; 可用的规划器: " + planners.keySet());
    }
    return planner;
  }

  /** 检查指定 provenanceCode 的规划器是否存在 */
  public boolean contains(ProvenanceCode provenanceCode) {
    if (provenanceCode == null) {
      return false;
    }
    return planners.containsKey(provenanceCode.getCode());
  }
}
