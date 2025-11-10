package com.patra.ingest.architecture.rules;

import com.tngtech.archunit.lang.ArchRule;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 事务边界规则
 *
 * <p>验证事务边界的正确位置：
 * <ul>
 *   <li>@Transactional 只能在 app 层（应用层编排器）
 *   <li>Domain 层严禁使用 @Transactional（保持纯净）
 * </ul>
 *
 * <p><b>架构约束检查点：</b>
 * <ul>
 *   <li>【CHK-ARCH-003】事务边界在 Orchestrator（应用层）
 * </ul>
 *
 * <p><b>事务管理原则：</b>
 * <ol>
 *   <li>事务边界由用例（Use Case）决定
 *   <li>Orchestrator/Coordinator 管理事务生命周期
 *   <li>Domain 层只包含业务逻辑，不关心事务
 *   <li>Infra 层执行持久化操作，但不定义事务边界
 * </ol>
 *
 * @author linqibin
 * @since 2025-01-10
 */
public final class TransactionBoundaryRules {

    private TransactionBoundaryRules() {
        // 工具类，禁止实例化
    }

    /**
     * 规则 15: @Transactional 只能在 app 层
     *
     * <p>事务边界应该在应用层的编排器中定义：
     * <ul>
     *   <li>Orchestrator：主编排器，通常需要事务（如 PlanIngestionOrchestrator）
     *   <li>Coordinator：辅助协调器，可能需要事务（如 PlanPersistenceCoordinator）
     *   <li>UseCase：用例实现类，按需使用事务
     * </ul>
     *
     * <p><b>原因：</b>
     * <ol>
     *   <li>用例（Use Case）是事务的自然边界
     *   <li>一个用例的所有操作应该原子性完成或全部回滚
     *   <li>应用层负责协调多个 Domain 聚合和服务
     *   <li>事务是技术关注点，不应泄露到 Domain 层
     * </ol>
     *
     * <p><b>正确示例：</b>
     * <pre>
     * // ✅ 正确：Orchestrator 在 app 层使用事务
     * package com.patra.ingest.app.usecase.plan;
     *
     * {@literal @}Service
     * public class PlanIngestionOrchestrator {
     *
     *     {@literal @}Transactional  // ✅ 事务边界在 app 层
     *     public PlanIngestionResult ingest(PlanIngestionCommand command) {
     *         // 1. 持久化调度实例
     *         // 2. 加载配置快照
     *         // 3. 装配 Plan/Slice/Task
     *         // 4. 持久化聚合
     *         // 5. 发布事件到 Outbox
     *         // 以上操作要么全部成功，要么全部回滚
     *     }
     * }
     * </pre>
     *
     * <p><b>错误示例：</b>
     * <pre>
     * // ❌ 错误：Domain 层使用事务
     * package com.patra.ingest.domain.service;
     *
     * {@literal @}Transactional  // ❌ 不允许
     * public class PlanService {
     *     public void createPlan() { ... }
     * }
     *
     * // ❌ 错误：Adapter 层使用事务
     * package com.patra.ingest.adapter.scheduler;
     *
     * {@literal @}Transactional  // ❌ 不推荐，应该委托给 app 层
     * public void harvestJob() { ... }
     * </pre>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>Adapter 层（如 Controller、Job）应该是瘦层，只负责触发 app 层用例
     *   <li>如果 Adapter 层需要事务，应该委托给 app 层的 Orchestrator
     *   <li>Infra 层的 Repository 实现不应该有 @Transactional，事务由调用方管理
     * </ul>
     */
    public static final ArchRule transactions_only_in_app_layer =
        classes()
            .that().areAnnotatedWith(Transactional.class)
            .should().resideInAPackage("..app..")
            .as("@Transactional 只能在 app 层")
            .because("事务边界应该在应用层的用例编排器中定义（六边形架构原则 CHK-ARCH-003）");

    /**
     * 规则 16: Domain 层严禁使用 @Transactional
     *
     * <p>Domain 层必须保持纯净，不能依赖 Spring 的 @Transactional 注解：
     * <ul>
     *   <li>Domain 层只包含业务逻辑
     *   <li>事务是技术实现细节，属于基础设施关注点
     *   <li>Domain 层不应该知道事务的存在
     * </ul>
     *
     * <p><b>原因：</b>
     * <ol>
     *   <li>符合单一职责原则（SRP）：Domain 层只关心业务逻辑
     *   <li>符合依赖倒置原则（DIP）：Domain 层不依赖框架
     *   <li>便于单元测试：无需启动事务管理器
     *   <li>便于移植：Domain 层可以复用到非 Spring 环境
     * </ol>
     *
     * <p><b>错误示例：</b>
     * <pre>
     * // ❌ 错误：Domain 层使用 @Transactional
     * package com.patra.ingest.domain.service;
     *
     * import org.springframework.transaction.annotation.Transactional;
     *
     * public class PlanService {
     *     {@literal @}Transactional  // ❌ 违反 Domain 层纯净性
     *     public void createPlan() {
     *         // 业务逻辑
     *     }
     * }
     * </pre>
     *
     * <p><b>正确示例：</b>
     * <pre>
     * // ✅ 正确：Domain 层纯 Java 方法
     * package com.patra.ingest.domain.service;
     *
     * public class PlanService {
     *     // ✅ 无 Spring 注解，纯业务逻辑
     *     public Plan createPlan(ProvenanceConfig config, WindowSpec window) {
     *         // 业务不变量检查
     *         // 状态机转换
     *         // 领域事件发布
     *         return plan;
     *     }
     * }
     *
     * // ✅ 事务由 app 层管理
     * package com.patra.ingest.app.usecase;
     *
     * {@literal @}Service
     * public class PlanOrchestrator {
     *     {@literal @}Transactional  // ✅ 事务在这里
     *     public void orchestrate() {
     *         Plan plan = planService.createPlan(...);  // 调用 Domain 层
     *         planRepository.save(plan);                // 持久化
     *     }
     * }
     * </pre>
     */
    public static final ArchRule domain_should_not_use_transactions =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().beAnnotatedWith(Transactional.class)
            .as("Domain 层严禁使用 @Transactional")
            .because("Domain 层必须保持纯净，事务是技术关注点，应该在 app 层管理（CHK-ARCH-001）");
}
