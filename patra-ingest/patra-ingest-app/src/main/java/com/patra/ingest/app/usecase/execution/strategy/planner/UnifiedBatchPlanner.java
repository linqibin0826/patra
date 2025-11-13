package com.patra.ingest.app.usecase.execution.strategy.planner;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.exception.BatchPlanningException;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.batch.BatchPlan;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.port.DataSourcePort;
import com.patra.ingest.domain.strategy.BatchGenerationStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一的批处理规划器（支持多数据源）
 *
 * <p>职责：
 * <ul>
 *   <li>使用 DataSourcePort.preparePlan() 获取批次计划
 *   <li>使用策略模式委派批次生成逻辑
 *   <li>构建完整的 BatchPlan（包含批次列表和执行上下文）
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>开闭原则：新增数据源无需修改此类
 *   <li>策略模式：通过 Map 自动注入所有批次生成策略
 *   <li>与 DataSourceAdapter 设计一致：都使用策略路由
 * </ul>
 *
 * <p>责任边界说明：
 * <ul>
 *   <li>Infrastructure 层（DataSourceAdapter + 防腐层）：获取并翻译计划元数据
 *   <li>Application 层策略（BatchGenerationStrategy）：根据批次计划生成批次
 *   <li>Application 层编排（UnifiedBatchPlanner）：策略路由和流程编排
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
@Component
@Slf4j
public class UnifiedBatchPlanner implements BatchPlanner {

    private final DataSourcePort dataSourcePort;
    private final Map<String, BatchGenerationStrategy> strategyMap;

    /**
     * 构造函数：自动注入所有批次生成策略
     *
     * <p>Spring 会自动注入所有 BatchGenerationStrategy 实现,
     * 构造函数将其转换为以数据源代码为键的 Map。
     *
     * <p><strong>设计要点</strong>：
     * <ul>
     *   <li>消除硬编码类型列表,通过 {@link BatchGenerationStrategy#getSupportedDataSourceCode()} 自动发现
     *   <li>完全符合开闭原则,新增数据源零修改
     *   <li>使用数据源代码（String）作为路由键，而非具体类型
     * </ul>
     *
     * @param dataSourcePort 数据源端口
     * @param strategies 所有批次生成策略
     */
    public UnifiedBatchPlanner(
            DataSourcePort dataSourcePort,
            List<BatchGenerationStrategy> strategies) {
        this.dataSourcePort = dataSourcePort;
        this.strategyMap = buildStrategyMap(strategies);

        log.info("已注册 {} 个批次生成策略: {}",
                strategyMap.size(),
                strategyMap.keySet());
    }

    /**
     * 构建策略 Map
     *
     * <p><strong>实现说明</strong>：
     * 直接调用策略的 getSupportedDataSourceCode() 方法,消除硬编码类型列表。
     */
    private Map<String, BatchGenerationStrategy> buildStrategyMap(
            List<BatchGenerationStrategy> strategies) {
        Map<String, BatchGenerationStrategy> map = new HashMap<>();

        for (BatchGenerationStrategy strategy : strategies) {
            String dataSourceCode = strategy.getSupportedDataSourceCode();

            if (dataSourceCode == null || dataSourceCode.isBlank()) {
                log.warn("策略 {} 返回 null 或空的 dataSourceCode，跳过注册",
                        strategy.getClass().getSimpleName());
                continue;
            }

            if (map.containsKey(dataSourceCode)) {
                log.error("检测到重复的策略数据源代码: {}，已有策略: {}，新策略: {}",
                        dataSourceCode,
                        map.get(dataSourceCode).getClass().getSimpleName(),
                        strategy.getClass().getSimpleName());
                throw new IllegalStateException(
                    String.format("重复的批次生成策略: dataSourceCode=%s", dataSourceCode)
                );
            }

            map.put(dataSourceCode, strategy);
            log.debug("注册批次生成策略: {} -> {}",
                    dataSourceCode,
                    strategy.getClass().getSimpleName());
        }

        if (map.isEmpty()) {
            log.warn("未发现任何批次生成策略，请检查 Spring 配置");
        }

        return map;
    }

    @Override
    public ProvenanceCode getProvenanceCode() {
        // 统一规划器支持所有数据源
        return null;
    }

    @Override
    public com.patra.ingest.domain.model.vo.batch.BatchPlan plan(ExecutionContext ctx) {
        try {
            // 1. 使用统一端口准备批次计划（已通过防腐层翻译为领域模型）
            com.patra.ingest.domain.model.vo.plan.BatchPlan batchPlan = dataSourcePort.preparePlan(
                    ctx,
                    ctx.dataType()
            );

            if (batchPlan.totalRecords() == 0) {
                log.info("计划结果为空: provenance={}", ctx.provenanceCode());
                return com.patra.ingest.domain.model.vo.batch.BatchPlan.empty(ctx);
            }

            log.info("批次计划已准备: provenance={}, dataType={}, totalRecords={}, hasStateToken={}",
                    ctx.provenanceCode(),
                    ctx.dataType(),
                    batchPlan.totalRecords(),
                    batchPlan.hasStateToken());

            // 2. 根据数据源代码选择对应策略
            String dataSourceCode = batchPlan.dataSourceCode();
            BatchGenerationStrategy strategy = strategyMap.get(dataSourceCode);
            if (strategy == null) {
                throw new IllegalStateException(
                    "未找到对应的批次生成策略: dataSourceCode=" + dataSourceCode
                );
            }

            // 3. 使用策略生成批次
            List<Batch> batches = strategy.generateBatches(batchPlan, ctx);

            log.info("批次计划已生成: provenance={}, batchCount={}, strategy={}",
                    ctx.provenanceCode(),
                    batches.size(),
                    strategy.getClass().getSimpleName());

            return new com.patra.ingest.domain.model.vo.batch.BatchPlan(batches, ctx);

        } catch (Exception ex) {
            log.error("批次规划失败: provenance={}", ctx.provenanceCode(), ex);
            throw new BatchPlanningException("批次规划失败: " + ex.getMessage(), ex);
        }
    }
}
