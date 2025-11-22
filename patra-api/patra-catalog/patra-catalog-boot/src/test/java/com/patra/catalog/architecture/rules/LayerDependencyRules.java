package com.patra.catalog.architecture.rules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

/// 层依赖方向规则
///
/// 验证六边形架构的依赖方向约束：
///
/// - Domain 层：不依赖任何业务层（纯净层）
///   - App 层：只能依赖 Domain 层（不能直接依赖 Infra）
///   - Infra 层：只能依赖 Domain 层（实现 Port 接口）
///   - Adapter 层：可依赖 App + Domain 层
///   - Boot 层：可依赖所有层（启动配置）
///   - 不允许循环依赖
///
/// **架构约束检查点：**
///
/// - 【CHK-ARCH-002】依赖方向必须向内（Adapter → App → Domain ← Infra）
///   - 【CHK-ARCH-005】不允许循环依赖
///
/// @author linqibin
/// @since 0.1.0
public final class LayerDependencyRules {

  private LayerDependencyRules() {
    // 工具类，禁止实例化
  }

  /// 规则 1-4: 四层架构依赖方向必须正确
  ///
  /// 使用 ArchUnit 的 layeredArchitecture() API 验证依赖方向。
  ///
  /// **依赖关系：**
  ///
  /// ```
  ///
  /// Adapter     → App + Domain
  /// App         → Domain（不能访问 Infra）
  /// Domain      → 无（纯净层）
  /// Infra       → Domain（实现 Port）
  ///
  /// ```
  ///
  /// **注意：**Boot 层（启动配置）不在此检查范围内，因为它位于根包， 可以访问所有层，且通常只有启动类和配置，不需要严格的架构约束。
  public static final ArchRule layer_dependencies_are_respected =
      Architectures.layeredArchitecture()
          .consideringOnlyDependenciesInLayers() // 只考虑内部层间依赖，忽略外部库
          // 定义四层核心业务层（Boot 层不参与检查）
          .layer("Domain")
          .definedBy("..catalog.domain..")
          .layer("Application")
          .definedBy("..catalog.app..")
          .layer("Infrastructure")
          .definedBy("..catalog.infra..")
          .layer("Adapter")
          .definedBy("..catalog.adapter..")

          // Domain 层不依赖任何业务层（纯净层）
          .whereLayer("Domain")
          .mayNotAccessAnyLayer()

          // App 层只能访问 Domain 层（不能访问 Infra）
          .whereLayer("Application")
          .mayOnlyAccessLayers("Domain")

          // Infra 层只能访问 Domain 层（实现 Port 接口）
          // 注意：Infra 需要访问 Spring、MyBatis、Slf4j 等外部框架，但 consideringOnlyDependenciesInLayers() 会忽略这些
          .whereLayer("Infrastructure")
          .mayOnlyAccessLayers("Domain")

          // Adapter 层可以访问 App + Domain 层
          // 注意：Adapter 需要访问 Spring、RocketMQ、XxlJob 等外部框架，但 consideringOnlyDependenciesInLayers()
          // 会忽略这些
          .whereLayer("Adapter")
          .mayOnlyAccessLayers("Application", "Domain")
          .as("六边形架构的层依赖方向必须正确：Adapter → App → Domain ← Infra")
          .because("违反依赖方向会导致架构腐化，参考 【CHK-ARCH-002】");

  /// 规则 4: App 层不能直接依赖 Infra 层的类
  ///
  /// App 层应该通过 Domain 层的 Port 接口调用 Infra 层的实现， 运行时由 Spring 注入实现类（依赖倒置原则）。
  ///
  /// **错误示例：**
  ///
  /// ```
  ///
  /// // ❌ 错误：App 层直接依赖 Infra 实现类
  /// public class MeshImportOrchestrator {
  ///     private final MeshDataRepositoryMpImpl repository;  // 直接依赖实现
  /// }
  ///
  /// ```
  ///
  /// **正确示例：**
  ///
  /// ```
  ///
  /// // ✅ 正确：App 层依赖 Domain 的 Port 接口
  /// public class MeshImportOrchestrator {
  ///     private final MeshDataRepository repository;  // 依赖接口
  /// }
  ///
  /// ```
  public static final ArchRule app_should_not_depend_on_infra_classes =
      noClasses()
          .that()
          .resideInAPackage("..app..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..infra..")
          .as("App 层不能直接依赖 Infra 层的类")
          .because("App 层应通过 Domain 的 Port 接口调用，由 Spring 注入实现（依赖倒置原则 DIP）");

  /// 规则 5: Adapter 层不能反向依赖 Infra 层
  ///
  /// Adapter 层负责对接外部触发器（HTTP、MQ、定时任务），不应该依赖 Infra 层：
  ///
  /// - Adapter 通过 App 层服务调用业务逻辑
  ///   - Infra 层负责持久化和外部集成
  ///   - 两者应该保持独立
  ///
  public static final ArchRule adapter_should_not_depend_on_infra =
      noClasses()
          .that()
          .resideInAPackage("..adapter..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..infra..")
          .as("Adapter 层不能反向依赖 Infra 层")
          .because("Adapter 和 Infra 应该通过 App 层协调，避免直接耦合");

  /// 规则 6: Adapter 层不能依赖 Boot 层
  ///
  /// Boot 层是启动配置层，负责组装所有组件，不应该被 Adapter 层依赖：
  ///
  /// - Boot 层位于依赖图的最外层
  ///   - 只有 Boot 层可以依赖其他所有层
  ///   - 反向依赖会导致循环依赖
  ///
  /// **注意：**使用精确的包名（..catalog.boot..）避免匹配 Spring Boot 框架包。
  public static final ArchRule adapter_should_not_depend_on_boot =
      noClasses()
          .that()
          .resideInAPackage("..catalog.adapter..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..catalog.boot..")
          .as("Adapter 层不能依赖 Boot 层")
          .because("Boot 层是最外层，只能被依赖，不能反向依赖");

  /// 规则 7: 不允许循环依赖
  ///
  /// 检测包之间的循环依赖，例如：
  ///
  /// - com.patra.catalog.app.usecase → com.patra.catalog.app.outbox → com.patra.catalog.app.usecase
  ///   - com.patra.catalog.domain.model → com.patra.catalog.domain.port →
  ///       com.patra.catalog.domain.model
  ///
  /// 循环依赖会导致：
  ///
  /// - 代码难以理解和维护
  ///   - 测试困难
  ///   - 重构时容易引入 Bug
  ///
  /// **解决方案：**
  ///
  /// - 提取共享逻辑到新的包
  ///   - 使用事件驱动架构解耦
  ///   - 引入中介者模式
  ///
  public static final ArchRule no_cycles_between_layers =
      slices()
          .matching("com.patra.catalog.(*)..")
          .should()
          .beFreeOfCycles()
          .as("不允许包之间的循环依赖")
          .because("循环依赖会导致代码难以维护和测试，参考 【CHK-ARCH-005】");
}
