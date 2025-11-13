package com.patra.ingest.domain.strategy;

import com.patra.common.model.plan.PlanMetadata;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;

import java.util.List;

/**
 * 批次生成策略接口
 *
 * <p>职责：定义如何根据计划元数据生成批次列表
 *
 * <p>设计原则：
 * <ul>
 *   <li>策略模式：每个数据源对应一个策略实现
 *   <li>开闭原则：新增数据源无需修改 UnifiedBatchPlanner
 *   <li>单一职责：每个策略类只处理一种数据源的批次生成
 * </ul>
 *
 * <p>使用场景：
 * <ul>
 *   <li>UnifiedBatchPlanner 根据 PlanMetadata 类型选择对应策略
 *   <li>通过 Spring 自动注入所有策略实现
 *   <li>使用 Map 进行策略路由（类似 DataSourceAdapter）
 * </ul>
 *
 * <p><strong>设计要点</strong>：
 * <ul>
 *   <li>{@link #getSupportedType()} 方法消除硬编码类型列表
 *   <li>策略类自己声明支持的类型,完全符合开闭原则
 *   <li>新增数据源时无需修改任何现有代码
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
public interface BatchGenerationStrategy {

    /**
     * 获取策略支持的计划元数据类型
     *
     * <p>此方法允许策略类自己声明支持的类型，避免了在 UnifiedBatchPlanner 中硬编码类型列表。
     *
     * <p><strong>设计理由</strong>：
     * <ul>
     *   <li>消除硬编码：无需在 UnifiedBatchPlanner 中维护已知类型列表
     *   <li>完全符合 OCP：新增数据源零修改
     *   <li>类型安全：编译时检查类型匹配
     *   <li>自动发现：Spring 启动时自动构建策略 Map
     * </ul>
     *
     * @return 支持的 PlanMetadata 类型
     *
     * @example
     * <pre>{@code
     * @Override
     * public Class<? extends PlanMetadata> getSupportedType() {
     *     return PubmedPlanMetadata.class;
     * }
     * }</pre>
     *
     * @since 0.2.1
     */
    Class<? extends PlanMetadata> getSupportedType();

    /**
     * 根据计划元数据生成批次列表
     *
     * <p>批次生成规则由具体策略实现，可能包括：
     * <ul>
     *   <li>根据总数和批次大小计算批次数量
     *   <li>设置每个批次的起始偏移量和大小
     *   <li>附加数据源特定的元数据（如 WebEnv、cursorMark）
     * </ul>
     *
     * @param plan 计划元数据
     * @param ctx 执行上下文（包含配置信息）
     * @return 批次列表
     * @throws IllegalArgumentException 如果 plan 类型不匹配
     */
    List<Batch> generateBatches(PlanMetadata plan, ExecutionContext ctx);
}
