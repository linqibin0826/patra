package com.patra.ingest.domain.strategy;

import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.model.vo.plan.BatchPlan;

import java.util.List;

/**
 * 批次生成策略接口
 *
 * <p>职责：定义如何根据批次计划生成批次列表
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
 *   <li>UnifiedBatchPlanner 根据数据源代码选择对应策略
 *   <li>通过 Spring 自动注入所有策略实现
 *   <li>使用 Map 进行策略路由（基于数据源代码）
 * </ul>
 *
 * <p><strong>设计要点</strong>：
 * <ul>
 *   <li>{@link #getSupportedDataSourceCode()} 方法消除硬编码类型列表
 *   <li>策略类自己声明支持的数据源代码，完全符合开闭原则
 *   <li>新增数据源时无需修改任何现有代码
 *   <li>解耦外部实现：不依赖 Provenance Starter 的具体类型
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
public interface BatchGenerationStrategy {

    /**
     * 获取策略支持的数据源代码
     *
     * <p>此方法允许策略类自己声明支持的数据源，避免了在 UnifiedBatchPlanner 中硬编码。
     *
     * <p><strong>设计理由</strong>：
     * <ul>
     *   <li>消除硬编码：无需在 UnifiedBatchPlanner 中维护已知类型列表
     *   <li>完全符合 OCP：新增数据源零修改
     *   <li>解耦实现：不依赖外部框架的具体类型
     *   <li>自动发现：Spring 启动时自动构建策略 Map
     * </ul>
     *
     * @return 支持的数据源代码（如 "pubmed", "doaj", "epmc"）
     *
     * @example
     * <pre>{@code
     * @Override
     * public String getSupportedDataSourceCode() {
     *     return "pubmed";
     * }
     * }</pre>
     *
     * @since 0.3.0
     */
    String getSupportedDataSourceCode();

    /**
     * 根据批次计划生成批次列表
     *
     * <p>批次生成规则由具体策略实现，可能包括：
     * <ul>
     *   <li>根据总记录数和批次大小计算批次数量
     *   <li>设置每个批次的起始偏移量和大小
     *   <li>附加状态令牌（opaque，不解析其内容）
     * </ul>
     *
     * @param plan 批次计划（领域模型）
     * @param ctx 执行上下文（包含配置信息）
     * @return 批次列表
     */
    List<Batch> generateBatches(BatchPlan plan, ExecutionContext ctx);
}
