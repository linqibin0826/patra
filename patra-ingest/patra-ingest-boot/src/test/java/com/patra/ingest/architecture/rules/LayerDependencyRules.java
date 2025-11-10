package com.patra.ingest.architecture.rules;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * 层依赖方向规则
 *
 * <p>验证六边形架构的依赖方向约束：
 * <ul>
 *   <li>Domain 层：不依赖任何业务层（纯净层）
 *   <li>App 层：只能依赖 Domain 层（不能直接依赖 Infra）
 *   <li>Infra 层：只能依赖 Domain 层（实现 Port 接口）
 *   <li>Adapter 层：可依赖 App + Domain 层
 *   <li>Boot 层：可依赖所有层（启动配置）
 *   <li>不允许循环依赖
 * </ul>
 *
 * <p><b>架构约束检查点：</b>
 * <ul>
 *   <li>【CHK-ARCH-002】依赖方向必须向内（Adapter → App → Domain ← Infra）
 *   <li>【CHK-ARCH-005】不允许循环依赖
 * </ul>
 *
 * @author linqibin
 * @since 2025-01-10
 */
public final class LayerDependencyRules {

    private LayerDependencyRules() {
        // 工具类，禁止实例化
    }

    /**
     * 规则 1-4: 四层架构依赖方向必须正确
     *
     * <p>使用 ArchUnit 的 layeredArchitecture() API 验证依赖方向。
     *
     * <p><b>依赖关系：</b>
     * <pre>
     * Adapter     → App + Domain
     * App         → Domain（不能访问 Infra）
     * Domain      → 无（纯净层）
     * Infra       → Domain（实现 Port）
     * </pre>
     *
     * <p><b>注意：</b>Boot 层（启动配置）不在此检查范围内，因为它位于根包，
     * 可以访问所有层，且通常只有启动类和配置，不需要严格的架构约束。
     */
    public static final ArchRule layer_dependencies_are_respected =
        Architectures.layeredArchitecture()
            .consideringOnlyDependenciesInLayers()  // 只考虑内部层间依赖，忽略外部库
            // 定义四层核心业务层（Boot 层不参与检查）
            .layer("Domain").definedBy("..ingest.domain..")
            .layer("Application").definedBy("..ingest.app..")
            .layer("Infrastructure").definedBy("..ingest.infra..")
            .layer("Adapter").definedBy("..ingest.adapter..")

            // Domain 层不依赖任何业务层（纯净层）
            .whereLayer("Domain").mayNotAccessAnyLayer()

            // App 层只能访问 Domain 层（不能访问 Infra）
            .whereLayer("Application").mayOnlyAccessLayers("Domain")

            // Infra 层只能访问 Domain 层（实现 Port 接口）
            // 注意：Infra 需要访问 Spring、MyBatis、Slf4j 等外部框架，但 consideringOnlyDependenciesInLayers() 会忽略这些
            .whereLayer("Infrastructure").mayOnlyAccessLayers("Domain")

            // Adapter 层可以访问 App + Domain 层
            // 注意：Adapter 需要访问 Spring、RocketMQ、XxlJob 等外部框架，但 consideringOnlyDependenciesInLayers() 会忽略这些
            .whereLayer("Adapter").mayOnlyAccessLayers("Application", "Domain")

            .as("六边形架构的层依赖方向必须正确：Adapter → App → Domain ← Infra")
            .because("违反依赖方向会导致架构腐化，参考 【CHK-ARCH-002】");

    /**
     * 规则 4: App 层不能直接依赖 Infra 层的类
     *
     * <p>App 层应该通过 Domain 层的 Port 接口调用 Infra 层的实现，
     * 运行时由 Spring 注入实现类（依赖倒置原则）。
     *
     * <p><b>错误示例：</b>
     * <pre>
     * // ❌ 错误：App 层直接依赖 Infra 实现类
     * public class PlanOrchestrator {
     *     private final PlanRepositoryMpImpl repository;  // 直接依赖实现
     * }
     * </pre>
     *
     * <p><b>正确示例：</b>
     * <pre>
     * // ✅ 正确：App 层依赖 Domain 的 Port 接口
     * public class PlanOrchestrator {
     *     private final PlanRepository repository;  // 依赖接口
     * }
     * </pre>
     */
    public static final ArchRule app_should_not_depend_on_infra_classes =
        noClasses()
            .that().resideInAPackage("..app..")
            .should().dependOnClassesThat().resideInAPackage("..infra..")
            .as("App 层不能直接依赖 Infra 层的类")
            .because("App 层应通过 Domain 的 Port 接口调用，由 Spring 注入实现（依赖倒置原则 DIP）");

    /**
     * 规则 5: Adapter 层不能反向依赖 Infra 层
     *
     * <p>Adapter 层负责对接外部触发器（HTTP、MQ、定时任务），不应该依赖 Infra 层：
     * <ul>
     *   <li>Adapter 通过 App 层服务调用业务逻辑
     *   <li>Infra 层负责持久化和外部集成
     *   <li>两者应该保持独立
     * </ul>
     */
    public static final ArchRule adapter_should_not_depend_on_infra =
        noClasses()
            .that().resideInAPackage("..adapter..")
            .should().dependOnClassesThat().resideInAPackage("..infra..")
            .as("Adapter 层不能反向依赖 Infra 层")
            .because("Adapter 和 Infra 应该通过 App 层协调，避免直接耦合");

    /**
     * 规则 6: Adapter 层不能依赖 Boot 层
     *
     * <p>Boot 层是启动配置层，负责组装所有组件，不应该被 Adapter 层依赖：
     * <ul>
     *   <li>Boot 层位于依赖图的最外层
     *   <li>只有 Boot 层可以依赖其他所有层
     *   <li>反向依赖会导致循环依赖
     * </ul>
     *
     * <p><b>注意：</b>使用精确的包名（..ingest.boot..）避免匹配 Spring Boot 框架包。
     */
    public static final ArchRule adapter_should_not_depend_on_boot =
        noClasses()
            .that().resideInAPackage("..ingest.adapter..")
            .should().dependOnClassesThat().resideInAPackage("..ingest.boot..")
            .as("Adapter 层不能依赖 Boot 层")
            .because("Boot 层是最外层，只能被依赖，不能反向依赖");

    /**
     * 规则 7: 不允许循环依赖
     *
     * <p>检测包之间的循环依赖，例如：
     * <ul>
     *   <li>com.patra.ingest.app.usecase → com.patra.ingest.app.outbox → com.patra.ingest.app.usecase
     *   <li>com.patra.ingest.domain.model → com.patra.ingest.domain.port → com.patra.ingest.domain.model
     * </ul>
     *
     * <p>循环依赖会导致：
     * <ul>
     *   <li>代码难以理解和维护
     *   <li>测试困难
     *   <li>重构时容易引入 Bug
     * </ul>
     *
     * <p><b>解决方案：</b>
     * <ul>
     *   <li>提取共享逻辑到新的包
     *   <li>使用事件驱动架构解耦
     *   <li>引入中介者模式
     * </ul>
     */
    public static final ArchRule no_cycles_between_layers =
        slices()
            .matching("com.patra.ingest.(*)..")
            .should().beFreeOfCycles()
            .as("不允许包之间的循环依赖")
            .because("循环依赖会导致代码难以维护和测试，参考 【CHK-ARCH-005】");
}
