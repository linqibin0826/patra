package com.patra.ingest.app.usecase.execution.strategy.builder;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.exception.BatchSchedulingException;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.batch.BatchSchedule;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.model.vo.fetch.FetchMetadata;
import com.patra.ingest.domain.port.ProvenanceDataPort;
import com.patra.ingest.domain.strategy.BatchGenerationStrategy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 批次调度构建器
 *
 * <p>职责：
 *
 * <ul>
 *   <li>准备抓取元数据（通过 ProvenanceDataPort）
 *   <li>路由到对应的批次生成策略
 *   <li>构建完整的 BatchSchedule
 * </ul>
 *
 * <p>设计原则：
 *
 * <ul>
 *   <li>策略管理：通过 Map 管理所有 BatchGenerationStrategy 实现
 *   <li>自动注册：Spring 构造器注入自动收集所有策略
 *   <li>开闭原则：新增数据源只需实现 BatchGenerationStrategy，零修改
 * </ul>
 *
 * <p>与 PlanExpressionBuilder 设计一致：
 *
 * <ul>
 *   <li>具体类而非接口（简化设计）
 *   <li>提供单一 build() 方法
 *   <li>封装复杂的构建逻辑
 * </ul>
 *
 * π
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
@Component
@Slf4j
public class BatchScheduleBuilder {

  private final ProvenanceDataPort provenanceDataPort;
  private final Map<ProvenanceCode, BatchGenerationStrategy> strategyMap;

  /**
   * 构造函数：自动注册所有批次生成策略
   *
   * <p>Spring 会自动注入所有 BatchGenerationStrategy 实现，构造函数将其转换为以 ProvenanceCode 为键的 Map。
   *
   * <p><strong>设计要点</strong>：
   *
   * <ul>
   *   <li>消除硬编码类型列表，通过 {@link BatchGenerationStrategy#getSupportedProvenanceCode()} 自动发现
   *   <li>完全符合开闭原则，新增数据源零修改
   *   <li>使用 Provenance 代码枚举作为路由键，提供编译期类型安全
   * </ul>
   *
   * @param provenanceDataPort 数据源端口
   * @param strategies 所有批次生成策略
   */
  public BatchScheduleBuilder(
      ProvenanceDataPort provenanceDataPort, List<BatchGenerationStrategy> strategies) {
    this.provenanceDataPort = provenanceDataPort;
    this.strategyMap = buildStrategyMap(strategies);

    log.info("已注册 {} 个批次生成策略: {}", strategyMap.size(), strategyMap.keySet());
  }

  /**
   * 构建策略 Map
   *
   * <p><strong>实现说明</strong>：直接调用策略的 getSupportedProvenanceCode() 方法，消除硬编码类型列表。
   */
  private Map<ProvenanceCode, BatchGenerationStrategy> buildStrategyMap(
      List<BatchGenerationStrategy> strategies) {
    Map<ProvenanceCode, BatchGenerationStrategy> map = new HashMap<>();

    for (BatchGenerationStrategy strategy : strategies) {
      ProvenanceCode provenanceCode = strategy.getSupportedProvenanceCode();

      if (provenanceCode == null) {
        log.warn("策略 {} 返回 null 的 provenanceCode，跳过注册", strategy.getClass().getSimpleName());
        continue;
      }

      if (map.containsKey(provenanceCode)) {
        log.error(
            "检测到重复的策略 Provenance 代码: {}，已有策略: {}，新策略: {}",
            provenanceCode,
            map.get(provenanceCode).getClass().getSimpleName(),
            strategy.getClass().getSimpleName());
        throw new IllegalStateException(
            String.format("重复的批次生成策略: provenanceCode=%s", provenanceCode));
      }

      map.put(provenanceCode, strategy);
      log.debug("注册批次生成策略: {} -> {}", provenanceCode, strategy.getClass().getSimpleName());
    }

    if (map.isEmpty()) {
      log.warn("未发现任何批次生成策略，请检查 Spring 配置");
    }

    return map;
  }

  /**
   * 构建批次调度
   *
   * <p>流程：
   *
   * <ol>
   *   <li>准备抓取元数据（调用 ProvenanceDataPort）
   *   <li>路由到对应的批次生成策略
   *   <li>生成批次列表
   *   <li>构建并返回 BatchSchedule
   * </ol>
   *
   * @param ctx 执行上下文
   * @return 批次调度
   * @throws BatchSchedulingException 构建失败时抛出
   */
  public BatchSchedule build(ExecutionContext ctx) {
    // 1. 使用统一端口准备抓取元数据（已通过防腐层翻译为领域模型）
    FetchMetadata fetchMetadata;
    try {
      fetchMetadata = provenanceDataPort.prepareFetchMetadata(ctx, ctx.dataType());
    } catch (RuntimeException ex) {
      log.error("准备抓取元数据失败: provenance={}", ctx.provenanceCode(), ex);
      throw new BatchSchedulingException("准备抓取元数据失败: " + ex.getMessage(), ex);
    }

    if (fetchMetadata.totalRecords() == 0) {
      log.info("抓取元数据为空: provenance={}", ctx.provenanceCode());
      return BatchSchedule.empty(ctx, fetchMetadata);
    }

    log.info(
        "抓取元数据已准备: provenance={}, dataType={}, totalRecords={}, hasStateToken={}",
        ctx.provenanceCode(),
        ctx.dataType(),
        fetchMetadata.totalRecords(),
        fetchMetadata.hasStateToken());

    // 2. 根据 Provenance 代码选择对应策略
    ProvenanceCode provenanceCode = fetchMetadata.provenanceCode();
    BatchGenerationStrategy strategy = strategyMap.get(provenanceCode);
    if (strategy == null) {
      // 配置错误：直接抛出 IllegalStateException，不包装
      throw new IllegalStateException("未找到对应的批次生成策略: provenanceCode=" + provenanceCode);
    }

    // 3. 使用策略生成批次
    List<Batch> batches;
    try {
      batches = strategy.generateBatches(fetchMetadata, ctx);
    } catch (RuntimeException ex) {
      log.error("批次生成失败: provenance={}", ctx.provenanceCode(), ex);
      throw new BatchSchedulingException("批次生成失败: " + ex.getMessage(), ex);
    }

    log.info(
        "批次调度已构建: provenance={}, batchCount={}, strategy={}",
        ctx.provenanceCode(),
        batches.size(),
        strategy.getClass().getSimpleName());

    return new BatchSchedule(batches, ctx, fetchMetadata);
  }

  /**
   * 检查是否支持指定的 ProvenanceCode
   *
   * <p>用途：
   *
   * <ul>
   *   <li>诊断工具：验证数据源策略是否已正确注册
   *   <li>健康检查：在应用启动时验证必需的策略是否存在
   *   <li>防御性编程：在调用 {@link #build(ExecutionContext)} 前预先检查
   * </ul>
   *
   * <p>注意：{@link #build(ExecutionContext)} 方法内部已有策略不存在的检查，因此通常不需要提前调用此方法。
   *
   * @param provenanceCode Provenance 代码
   * @return 如果支持返回 true，否则返回 false
   */
  public boolean supports(ProvenanceCode provenanceCode) {
    return strategyMap.containsKey(provenanceCode);
  }
}
