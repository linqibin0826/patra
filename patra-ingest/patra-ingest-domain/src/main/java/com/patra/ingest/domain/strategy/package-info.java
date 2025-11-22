/// 批次生成策略接口包
/// 
/// 本包定义了批次生成的策略模式契约，用于支持多数据源的批次生成逻辑。
/// 
/// ## 核心接口
/// 
/// - {@link com.patra.ingest.domain.strategy.BatchGenerationStrategy} - 批次生成策略接口
/// 
/// ## 设计理念
/// 
/// 使用策略模式实现批次生成逻辑，符合开闭原则（OCP）：
/// 
/// - 每个数据源有独立的策略实现类（位于 App 层）
///   - 新增数据源无需修改现有代码
///   - 策略通过 Spring 自动注册和发现
///   - 策略类自己声明支持的 PlanMetadata 类型
/// 
/// ## 使用场景
/// 
/// UnifiedBatchScheduleBuilder 根据 QuerySession 类型选择对应策略：
/// 
/// ## 扩展指南
/// 
/// 添加新数据源的批次生成策略：
/// 
/// ```java
/// // 1. 在 App 层创建策略实现类
/// @Component
/// public class EpmcBatchGenerationStrategy implements BatchGenerationStrategy {
/// 
///     @Override
///     public Class<? extends PlanMetadata> getSupportedType() {
///         return EpmcPlanMetadata.class;  // 声明支持的类型
/// 
///     @Override
///     public List<Batch> generateBatches(PlanMetadata plan, ExecutionContext ctx) {
///         EpmcPlanMetadata epmcPlan = (EpmcPlanMetadata) plan;
///         // 实现 EPMC 特定的批次生成逻辑
///         return batches;
/// ```
/// 
/// ## 架构对齐
/// 
/// - **Domain 层**：定义策略接口（本包）
///   - **App 层**：实现具体策略（如 PubmedBatchGenerationStrategy）
///   - **自动发现**：Spring 启动时自动注册所有策略实现
/// 
/// ## 设计优势
/// 
/// - 消除硬编码：无需在 UnifiedBatchScheduleBuilder 中维护类型列表
///   - 完全符合 OCP：新增数据源零修改现有代码
///   - 类型安全：编译时检查类型匹配
///   - 策略路由：与 ProvenanceDataAdapter 设计一致
/// 
/// @author linqibin
/// @since 0.1.0
package com.patra.ingest.domain.strategy;
