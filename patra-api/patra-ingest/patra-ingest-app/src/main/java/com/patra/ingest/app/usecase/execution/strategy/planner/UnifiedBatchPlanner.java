package com.patra.ingest.app.usecase.execution.strategy.planner;

import com.patra.common.enums.ProvenanceCode;
import com.patra.common.model.DataType;
import com.patra.common.model.plan.PlanMetadata;
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
 *   <li>使用 DataSourcePort.preparePlan() 获取计划元数据
 *   <li>使用策略模式委派批次生成逻辑
 *   <li>将 PlanMetadata 附加到 ExecutionContext 供执行阶段使用
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
 *   <li>Framework 层（DataSourceProvider）：只负责获取 PlanMetadata
 *   <li>Application 层策略（BatchGenerationStrategy）：负责根据 PlanMetadata 生成批次
 *   <li>Application 层编排（UnifiedBatchPlanner）：负责策略路由和上下文传递
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
@Component
@Slf4j
public class UnifiedBatchPlanner implements BatchPlanner {

    private final DataSourcePort dataSourcePort;
    private final Map<Class<? extends PlanMetadata>, BatchGenerationStrategy> strategyMap;

    /**
     * 构造函数：自动注入所有批次生成策略
     *
     * <p>Spring 会自动注入所有 BatchGenerationStrategy 实现,
     * 构造函数将其转换为以 PlanMetadata 类型为键的 Map。
     *
     * <p><strong>设计要点</strong>：
     * <ul>
     *   <li>消除硬编码类型列表,通过 {@link BatchGenerationStrategy#getSupportedType()} 自动发现
     *   <li>完全符合开闭原则,新增数据源零修改
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
                strategyMap.keySet().stream()
                        .map(Class::getSimpleName)
                        .toList());
    }

    /**
     * 构建策略 Map
     *
     * <p><strong>实现说明</strong>：
     * 直接调用策略的 getSupportedType() 方法,消除硬编码类型列表。
     */
    private Map<Class<? extends PlanMetadata>, BatchGenerationStrategy> buildStrategyMap(
            List<BatchGenerationStrategy> strategies) {
        Map<Class<? extends PlanMetadata>, BatchGenerationStrategy> map = new HashMap<>();

        for (BatchGenerationStrategy strategy : strategies) {
            Class<? extends PlanMetadata> supportedType = strategy.getSupportedType();

            if (supportedType == null) {
                log.warn("策略 {} 返回 null 的 supportedType，跳过注册",
                        strategy.getClass().getSimpleName());
                continue;
            }

            if (map.containsKey(supportedType)) {
                log.error("检测到重复的策略类型: {}，已有策略: {}，新策略: {}",
                        supportedType.getSimpleName(),
                        map.get(supportedType).getClass().getSimpleName(),
                        strategy.getClass().getSimpleName());
                throw new IllegalStateException(
                    String.format("重复的批次生成策略: %s", supportedType.getSimpleName())
                );
            }

            map.put(supportedType, strategy);
            log.debug("注册批次生成策略: {} -> {}",
                    supportedType.getSimpleName(),
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
    public BatchPlan plan(ExecutionContext ctx) {
        try {
            // 1. 使用统一端口准备计划元数据
            PlanMetadata planMetadata = dataSourcePort.preparePlan(
                    ctx,
                    ctx.dataType()
            );

            if (planMetadata.totalCount() == 0) {
                log.info("计划结果为空: provenance={}", ctx.provenanceCode());
                // 即使为空，也需要将 planMetadata 附加到 context
                ExecutionContext enrichedContext = ctx.withPlanMetadata(planMetadata);
                return BatchPlan.empty(enrichedContext);
            }

            log.info("计划元数据已准备: provenance={}, dataType={}, totalCount={}, hasSessionToken={}",
                    ctx.provenanceCode(),
                    ctx.dataType(),
                    planMetadata.totalCount(),
                    planMetadata.hasSessionToken());

            // 2. 根据 PlanMetadata 类型选择对应策略
            BatchGenerationStrategy strategy = strategyMap.get(planMetadata.getClass());
            if (strategy == null) {
                throw new IllegalStateException(
                    "未找到对应的批次生成策略: " + planMetadata.getClass().getName()
                );
            }

            // 3. 使用策略生成批次
            List<Batch> batches = strategy.generateBatches(planMetadata, ctx);

            // 4. 将 planMetadata 附加到 context 中，供执行阶段使用
            ExecutionContext enrichedContext = ctx.withPlanMetadata(planMetadata);

            log.info("批次计划已生成: provenance={}, batchCount={}, strategy={}",
                    ctx.provenanceCode(),
                    batches.size(),
                    strategy.getClass().getSimpleName());

            return new BatchPlan(batches, enrichedContext);

        } catch (Exception ex) {
            log.error("批次规划失败: provenance={}", ctx.provenanceCode(), ex);
            throw new BatchPlanningException("批次规划失败: " + ex.getMessage(), ex);
        }
    }
}
