/// 数据源计划元数据模型包。
///
/// 本包包含跨模块复用的计划元数据模型，使用继承体系支持多数据源的类型安全扩展。
///
/// ## 设计定位：
///
/// 本包属于 **Shared Kernel**（共享内核）模式，提供跨服务共享的通用领域模型， 确保 Ingest、Export、Synchronization
/// 等多个模块对"计划元数据"概念有一致理解。
///
/// ## 核心理念：
///
/// - **类型安全**：使用继承体系替代 `Map<String, Object>`，提供编译时类型检查
///   - **多数据源支持**：每个数据源对应一个 PlanMetadata 子类，封装特定元数据
///   - **业务约束验证**：在构造函数中强制验证业务规则（如 totalCount ≥ 0）
///   - **扩展性**：新增数据源只需创建新子类，无需修改现有代码（符合开闭原则）
///
/// ## 核心组件：
///
/// - {@link com.patra.common.model.plan.PlanMetadata} - 计划元数据抽象基类
///   - {@link com.patra.common.model.plan.PubmedPlanMetadata} - PubMed 特定元数据（WebEnv + QueryKey）
///   - {@link com.patra.common.model.plan.EpmcPlanMetadata} - EPMC 特定元数据（CursorMark）
///   - {@link com.patra.common.model.plan.DoajPlanMetadata} - DOAJ 特定元数据（ScrollId + PageSize）
///
/// ## 类型层次结构：
///
/// ```
///
/// PlanMetadata (抽象基类)
///  ├─ PubmedPlanMetadata (PubMed: WebEnv + QueryKey)
///  ├─ EpmcPlanMetadata (EPMC: CursorMark)
///  ├─ DoajPlanMetadata (DOAJ: ScrollId + PageSize)
///  └─ (未来可扩展: CrossrefPlanMetadata, ArxivPlanMetadata, ...)
///
/// ```
///
/// ## 设计优势：
///
/// - **vs Map&lt;String, Object&gt;**：
///
/// - ✅ 编译时类型安全（无需运行时类型转换）
///         - ✅ IDE 友好（自动补全、重构安全）
///         - ✅ 业务约束验证（构造函数强制检查）
///         - ✅ 清晰的类型关系（继承体系表达数据源差异）
///
///   - **职责清晰**：
///
/// - Framework 层（starter-provenance）：负责创建 PlanMetadata
///         - Infrastructure 层（ProvenanceDataAdapter）：负责协议转换
///         - Application 层（BatchPlanner）：负责基于 PlanMetadata 生成批次
///
/// ## 使用场景：
///
/// - **批次规划阶段**：`ProvenanceDataPort.preparePlan()` 返回 PlanMetadata
///   - **批次生成**：`UnifiedBatchPlanner` 根据 PlanMetadata 类型生成批次
///   - **执行优化**：批次执行器利用会话令牌（如 WebEnv）优化请求
///
/// ## 使用示例：
///
/// ```java
/// // 创建 PubMed 计划元数据
/// PubmedPlanMetadata pubmedPlan = new PubmedPlanMetadata(
///     1000,           // totalCount
///     "webenv123",    // webEnv
///     "1"             // queryKey
/// );
///
/// // 类型安全的多态处理
/// if (plan instanceof PubmedPlanMetadata pubmedPlan) {
///     // IDE 自动补全，编译时类型检查
///     String webEnv = pubmedPlan.webEnv();
///     String queryKey = pubmedPlan.queryKey();
///
///     if (pubmedPlan.hasSessionToken()) {
///         // 使用会话令牌优化批次请求
/// ```
///
/// ## 扩展指南：
///
/// 新增数据源时，按以下步骤扩展：
///
/// ## 架构依赖：
///
/// - **上游依赖**：无（纯 Java 模型，无框架依赖）
///   - **下游消费者**：
///
/// - patra-ingest-domain（ProvenanceDataPort 返回类型）
///         - patra-ingest-app（批次规划器使用）
///         - patra-spring-boot-starter-provenance（Provider 实现）
///
/// @since 0.1.0
/// @author linqibin
/// @see com.patra.common.model 通用领域模型
/// @see <a
///
// href="../../../../../../../../../patra-ingest/patra-ingest-domain/README.md">patra-ingest-domain</a>
package com.patra.starter.provenance.internal.metadata;
