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
 * 统一的批次调度构建器（支持多数据源）
 *
 * <p>职责：
 *
 * <ul>
 *   <li>使用 ProvenanceDataPort.prepareFetchMetadata() 获取批次调度
 *   <li>使用策略模式委派批次生成逻辑
 *   <li>构建完整的 BatchSchedule（包含批次列表和执行上下文）
 * </ul>
 *
 * <p>设计原则：
 *
 * <ul>
 *   <li>开闭原则：新增数据源无需修改此类
 *   <li>策略模式：通过 Map 自动注入所有批次生成策略
 *   <li>与 ProvenanceDataAdapter 设计一致：都使用策略路由
 * </ul>
 *
 * <p>责任边界说明：
 *
 * <ul>
 *   <li>Infrastructure 层（ProvenanceDataAdapter + 防腐层）：获取并翻译计划元数据
 *   <li>Application 层策略（BatchGenerationStrategy）：根据批次调度生成批次
 *   <li>Application 层编排（UnifiedBatchScheduleBuilder）：策略路由和流程编排
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
@Component
@Slf4j
public class UnifiedBatchScheduleBuilder implements BatchScheduleBuilder {

  private final ProvenanceDataPort provenanceDataPort;
  private final Map<ProvenanceCode, BatchGenerationStrategy> strategyMap;

  /**
   * 构造函数：自动注入所有批次生成策略
   *
   * <p>Spring 会自动注入所有 BatchGenerationStrategy 实现, 构造函数将其转换为以 Provenance 代码为键的 Map。
   *
   * <p><strong>设计要点</strong>：
   *
   * <ul>
   *   <li>消除硬编码类型列表,通过 {@link BatchGenerationStrategy#getSupportedProvenanceCode()} 自动发现
   *   <li>完全符合开闭原则,新增数据源零修改
   *   <li>使用 Provenance 代码枚举作为路由键，提供编译期类型安全
   * </ul>
   *
   * @param provenanceDataPort 数据源端口
   * @param strategies 所有批次生成策略
   */
  public UnifiedBatchScheduleBuilder(
      ProvenanceDataPort provenanceDataPort, List<BatchGenerationStrategy> strategies) {
    this.provenanceDataPort = provenanceDataPort;
    this.strategyMap = buildStrategyMap(strategies);

    log.info("已注册 {} 个批次生成策略: {}", strategyMap.size(), strategyMap.keySet());
  }

  /**
   * 构建策略 Map
   *
   * <p><strong>实现说明</strong>： 直接调用策略的 getSupportedProvenanceCode() 方法,消除硬编码类型列表。
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

  @Override
  public ProvenanceCode getProvenanceCode() {
    // 统一构建器支持所有数据源
    return null;
  }

  @Override
  public BatchSchedule build(ExecutionContext ctx) {
    try {
      // 1. 使用统一端口准备抓取元数据（已通过防腐层翻译为领域模型）
      FetchMetadata fetchMetadata = provenanceDataPort.prepareFetchMetadata(ctx, ctx.dataType());

      if (fetchMetadata.totalRecords() == 0) {
        log.info("抓取元数据为空: provenance={}", ctx.provenanceCode());
        return BatchSchedule.empty(ctx);
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
        throw new IllegalStateException("未找到对应的批次生成策略: provenanceCode=" + provenanceCode);
      }

      // 3. 使用策略生成批次
      List<Batch> batches = strategy.generateBatches(fetchMetadata, ctx);

      log.info(
          "批次调度已构建: provenance={}, batchCount={}, strategy={}",
          ctx.provenanceCode(),
          batches.size(),
          strategy.getClass().getSimpleName());

      return new BatchSchedule(batches, ctx);

    } catch (Exception ex) {
      log.error("批次调度构建失败: provenance={}", ctx.provenanceCode(), ex);
      throw new BatchSchedulingException("批次调度构建失败: " + ex.getMessage(), ex);
    }
  }
}
