/**
 * 批次生成策略接口包
 *
 * <p>本包定义了批次生成的策略模式契约，用于支持多数据源的批次生成逻辑。
 *
 * <h2>核心接口</h2>
 *
 * <ul>
 *   <li>{@link com.patra.ingest.domain.strategy.BatchGenerationStrategy} - 批次生成策略接口
 * </ul>
 *
 * <h2>设计理念</h2>
 *
 * <p>使用策略模式实现批次生成逻辑，符合开闭原则（OCP）：
 *
 * <ul>
 *   <li>每个数据源有独立的策略实现类（位于 App 层）
 *   <li>新增数据源无需修改现有代码
 *   <li>策略通过 Spring 自动注册和发现
 *   <li>策略类自己声明支持的 PlanMetadata 类型
 * </ul>
 *
 * <h2>使用场景</h2>
 *
 * <p>UnifiedBatchPlanner 根据 PlanMetadata 类型选择对应策略：
 *
 * <ol>
 *   <li>调用 DataSourcePort.preparePlan() 获取计划元数据
 *   <li>根据元数据类型（如 PubmedPlanMetadata）查找对应策略
 *   <li>委托策略生成批次列表
 *   <li>返回 BatchPlan 供执行阶段使用
 * </ol>
 *
 * <h2>扩展指南</h2>
 *
 * <p>添加新数据源的批次生成策略：
 *
 * <pre>{@code
 * // 1. 在 App 层创建策略实现类
 * @Component
 * public class EpmcBatchGenerationStrategy implements BatchGenerationStrategy {
 *
 *     @Override
 *     public Class<? extends PlanMetadata> getSupportedType() {
 *         return EpmcPlanMetadata.class;  // 声明支持的类型
 *     }
 *
 *     @Override
 *     public List<Batch> generateBatches(PlanMetadata plan, ExecutionContext ctx) {
 *         EpmcPlanMetadata epmcPlan = (EpmcPlanMetadata) plan;
 *         // 实现 EPMC 特定的批次生成逻辑
 *         return batches;
 *     }
 * }
 * }</pre>
 *
 * <h2>架构对齐</h2>
 *
 * <ul>
 *   <li><strong>Domain 层</strong>：定义策略接口（本包）
 *   <li><strong>App 层</strong>：实现具体策略（如 PubmedBatchGenerationStrategy）
 *   <li><strong>自动发现</strong>：Spring 启动时自动注册所有策略实现
 * </ul>
 *
 * <h2>设计优势</h2>
 *
 * <ul>
 *   <li>消除硬编码：无需在 UnifiedBatchPlanner 中维护类型列表
 *   <li>完全符合 OCP：新增数据源零修改现有代码
 *   <li>类型安全：编译时检查类型匹配
 *   <li>策略路由：与 DataSourceAdapter 设计一致
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.ingest.domain.strategy;
