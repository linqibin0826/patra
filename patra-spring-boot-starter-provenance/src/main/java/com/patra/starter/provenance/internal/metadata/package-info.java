/**
 * 数据源计划元数据模型包。
 *
 * <p>本包包含跨模块复用的计划元数据模型，使用继承体系支持多数据源的类型安全扩展。
 *
 * <h2>设计定位：</h2>
 * <p>本包属于 <strong>Shared Kernel</strong>（共享内核）模式，提供跨服务共享的通用领域模型，
 * 确保 Ingest、Export、Synchronization 等多个模块对"计划元数据"概念有一致理解。
 *
 * <h2>核心理念：</h2>
 * <ul>
 *   <li><strong>类型安全</strong>：使用继承体系替代 {@code Map<String, Object>}，提供编译时类型检查</li>
 *   <li><strong>多数据源支持</strong>：每个数据源对应一个 PlanMetadata 子类，封装特定元数据</li>
 *   <li><strong>业务约束验证</strong>：在构造函数中强制验证业务规则（如 totalCount ≥ 0）</li>
 *   <li><strong>扩展性</strong>：新增数据源只需创建新子类，无需修改现有代码（符合开闭原则）</li>
 * </ul>
 *
 * <h2>核心组件：</h2>
 * <ul>
 *   <li>{@link com.patra.common.model.plan.PlanMetadata} - 计划元数据抽象基类</li>
 *   <li>{@link com.patra.common.model.plan.PubmedPlanMetadata} - PubMed 特定元数据（WebEnv + QueryKey）</li>
 *   <li>{@link com.patra.common.model.plan.EpmcPlanMetadata} - EPMC 特定元数据（CursorMark）</li>
 *   <li>{@link com.patra.common.model.plan.DoajPlanMetadata} - DOAJ 特定元数据（ScrollId + PageSize）</li>
 * </ul>
 *
 * <h2>类型层次结构：</h2>
 * <pre>
 * PlanMetadata (抽象基类)
 *  ├─ PubmedPlanMetadata (PubMed: WebEnv + QueryKey)
 *  ├─ EpmcPlanMetadata (EPMC: CursorMark)
 *  ├─ DoajPlanMetadata (DOAJ: ScrollId + PageSize)
 *  └─ (未来可扩展: CrossrefPlanMetadata, ArxivPlanMetadata, ...)
 * </pre>
 *
 * <h2>设计优势：</h2>
 * <ul>
 *   <li><strong>vs Map&lt;String, Object&gt;</strong>：
 *     <ul>
 *       <li>✅ 编译时类型安全（无需运行时类型转换）</li>
 *       <li>✅ IDE 友好（自动补全、重构安全）</li>
 *       <li>✅ 业务约束验证（构造函数强制检查）</li>
 *       <li>✅ 清晰的类型关系（继承体系表达数据源差异）</li>
 *     </ul>
 *   </li>
 *   <li><strong>职责清晰</strong>：
 *     <ul>
 *       <li>Framework 层（starter-provenance）：负责创建 PlanMetadata</li>
 *       <li>Infrastructure 层（DataSourceAdapter）：负责协议转换</li>
 *       <li>Application 层（BatchPlanner）：负责基于 PlanMetadata 生成批次</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>使用场景：</h2>
 * <ul>
 *   <li><strong>批次规划阶段</strong>：{@code DataSourcePort.preparePlan()} 返回 PlanMetadata</li>
 *   <li><strong>批次生成</strong>：{@code UnifiedBatchPlanner} 根据 PlanMetadata 类型生成批次</li>
 *   <li><strong>执行优化</strong>：批次执行器利用会话令牌（如 WebEnv）优化请求</li>
 * </ul>
 *
 * <h2>使用示例：</h2>
 * <pre>{@code
 * // 创建 PubMed 计划元数据
 * PubmedPlanMetadata pubmedPlan = new PubmedPlanMetadata(
 *     1000,           // totalCount
 *     "webenv123",    // webEnv
 *     "1"             // queryKey
 * );
 *
 * // 类型安全的多态处理
 * if (plan instanceof PubmedPlanMetadata pubmedPlan) {
 *     // IDE 自动补全，编译时类型检查
 *     String webEnv = pubmedPlan.webEnv();
 *     String queryKey = pubmedPlan.queryKey();
 *
 *     if (pubmedPlan.hasSessionToken()) {
 *         // 使用会话令牌优化批次请求
 *     }
 * }
 * }</pre>
 *
 * <h2>扩展指南：</h2>
 * <p>新增数据源时，按以下步骤扩展：
 * <ol>
 *   <li>创建新的 PlanMetadata 子类（如 {@code CrossrefPlanMetadata}）</li>
 *   <li>在子类中定义数据源特定的字段和约束</li>
 *   <li>实现 {@code hasSessionToken()} 方法</li>
 *   <li>在 Framework 层的 DataSourceProvider 实现中返回新子类</li>
 *   <li>在 Application 层的 BatchGenerationStrategy 中处理新类型</li>
 * </ol>
 *
 * <h2>架构依赖：</h2>
 * <ul>
 *   <li><strong>上游依赖</strong>：无（纯 Java 模型，无框架依赖）</li>
 *   <li><strong>下游消费者</strong>：
 *     <ul>
 *       <li>patra-ingest-domain（DataSourcePort 返回类型）</li>
 *       <li>patra-ingest-app（批次规划器使用）</li>
 *       <li>patra-spring-boot-starter-provenance（Provider 实现）</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * @since 0.2.0
 * @author Patra Architecture Team
 * @see com.patra.common.model 通用领域模型
 * @see <a href="../../../../../../../../../patra-ingest/patra-ingest-domain/README.md">patra-ingest-domain</a>
 */
package com.patra.starter.provenance.internal.metadata;
