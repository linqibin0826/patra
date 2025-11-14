package com.patra.ingest.app.usecase.execution.strategy.planner;

import com.patra.common.enums.ProvenanceCode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 批次调度构建器注册中心
 *
 * <p>职责: 管理所有 {@link BatchScheduleBuilder} 实例,根据 provenanceCode 路由。
 *
 * <h3>设计要点</h3>
 *
 * <ul>
 *   <li><b>自动注册</b>: 通过 Spring 构造器注入自动注册所有实现
 *   <li><b>线程安全</b>: 使用 {@link ConcurrentHashMap} 保证并发安全
 *   <li><b>异常处理</b>: 构建器未找到时抛出 {@link IllegalArgumentException}
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Component
@Slf4j
public class BatchScheduleBuilderRegistry {

  private final Map<String, BatchScheduleBuilder> builders = new ConcurrentHashMap<>();

  /**
   * 构造器 - 自动注册所有批次调度构建器实例
   *
   * @param builderList 所有由 Spring 注入的 BatchScheduleBuilder 实例
   */
  public BatchScheduleBuilderRegistry(List<BatchScheduleBuilder> builderList) {
    for (BatchScheduleBuilder builder : builderList) {
      ProvenanceCode provenanceCode = builder.getProvenanceCode();
      String code = provenanceCode.getCode();
      if (builders.containsKey(code)) {
        log.warn("检测到重复的批次调度构建器,provenanceCode={}", code);
      }
      builders.put(code, builder);
      log.info("已注册批次调度构建器: provenanceCode={}, class={}", code, builder.getClass().getSimpleName());
    }
  }

  /**
   * 根据 Provenance 代码获取批次调度构建器
   *
   * @param provenanceCode Provenance 代码
   * @return 批次调度构建器
   * @throws IllegalArgumentException 构建器未找到时抛出
   */
  public BatchScheduleBuilder get(ProvenanceCode provenanceCode) {
    if (provenanceCode == null) {
      throw new IllegalArgumentException("provenanceCode 不能为 null");
    }
    String code = provenanceCode.getCode();
    BatchScheduleBuilder builder = builders.get(code);
    if (builder == null) {
      throw new IllegalArgumentException(
          "未找到批次调度构建器: provenanceCode=" + provenanceCode + "; 可用的构建器: " + builders.keySet());
    }
    return builder;
  }

  /** 检查指定 provenanceCode 的构建器是否存在 */
  public boolean contains(ProvenanceCode provenanceCode) {
    if (provenanceCode == null) {
      return false;
    }
    return builders.containsKey(provenanceCode.getCode());
  }
}
