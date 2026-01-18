package com.patra.starter.test.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/// 六边形架构规则工厂。
///
/// 提供参数化的架构规则，支持不同服务使用相同的架构约束。
/// 通过构造函数传入服务名（如 "ingest"、"catalog"）生成对应的规则集。
///
/// ### 设计目的
///
/// - **消除代码重复**: 不同服务的架构规则高度相似（仅包名不同）
/// - **统一架构标准**: 所有服务遵循相同的六边形架构约束
/// - **易于维护**: 修改规则只需在一处进行
///
/// ### 使用方式
///
/// ```java
/// class IngestArchitectureTest {
///
///     private static final JavaClasses classes = new ClassFileImporter()
///         .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
///         .importPackages("com.patra.ingest");
///
///     private static final HexagonalArchitectureRules rules =
///         new HexagonalArchitectureRules("ingest");
///
///     @ArchTest
///     static final ArchRule layerDependencies = rules.layerDependenciesAreRespected();
///
///     @ArchTest
///     static final ArchRule domainPurity = rules.domainShouldNotDependOnSpring();
///
///     // 或使用批量验证
///     @Test
///     void allArchitectureRulesAreSatisfied() {
///         rules.allRules().forEach(rule -> rule.check(classes));
///     }
/// }
/// ```
///
/// ### 包含的规则分类
///
/// 1. **层依赖规则**: 验证依赖方向（Domain ← App/Infra ← Adapter）
/// 2. **Domain 层纯净性**: 禁止 Spring 依赖、限制允许的依赖
/// 3. **命名约定**: Port/Repository/DO/Aggregate/Orchestrator 命名和位置
/// 4. **封装规则**: DO 不暴露、Port 必须 public、Event 位置
/// 5. **事务边界**: @Transactional 只在 app 层
///
/// @author linqibin
/// @since 0.1.0
public class HexagonalArchitectureRules {

  /// 服务名（如 "ingest"、"catalog"）。
  private final String serviceName;

  /// 基础包名（如 "com.patra.ingest"）。
  private final String basePackage;

  /// 构造函数。
  ///
  /// @param serviceName 服务名（如 "ingest"、"catalog"），用于生成包名模式
  public HexagonalArchitectureRules(String serviceName) {
    this.serviceName = serviceName;
    this.basePackage = "com.patra." + serviceName;
  }

  // ==================== 层依赖规则 ====================

  /// 四层架构依赖方向必须正确。
  ///
  /// 依赖关系：
  /// - Adapter → App + Domain
  /// - App → Domain（不能访问 Infra）
  /// - Domain → 无（纯净层）
  /// - Infra → Domain（实现 Port）
  ///
  /// @return ArchRule 规则实例
  public ArchRule layerDependenciesAreRespected() {
    return Architectures.layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .layer("Domain")
        .definedBy(".." + serviceName + ".domain..")
        .layer("Application")
        .definedBy(".." + serviceName + ".app..")
        .layer("Infrastructure")
        .definedBy(".." + serviceName + ".infra..")
        .layer("Adapter")
        .definedBy(".." + serviceName + ".adapter..")
        .whereLayer("Domain")
        .mayNotAccessAnyLayer()
        .whereLayer("Application")
        .mayOnlyAccessLayers("Domain")
        .whereLayer("Infrastructure")
        .mayOnlyAccessLayers("Domain")
        .whereLayer("Adapter")
        .mayOnlyAccessLayers("Application", "Domain")
        .as("六边形架构的层依赖方向必须正确：Adapter → App → Domain ← Infra")
        .because("违反依赖方向会导致架构腐化，参考 【CHK-ARCH-002】");
  }

  /// App 层不能直接依赖 Infra 层的类。
  ///
  /// @return ArchRule 规则实例
  public ArchRule appShouldNotDependOnInfraClasses() {
    return noClasses()
        .that()
        .resideInAPackage("..app..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..infra..")
        .as("App 层不能直接依赖 Infra 层的类")
        .because("App 层应通过 Domain 的 Port 接口调用，由 Spring 注入实现（依赖倒置原则 DIP）");
  }

  /// Adapter 层不能反向依赖 Infra 层。
  ///
  /// 注意：此规则仅检查主动适配器层（`{service}.adapter.*`），不包括 infra 模块内部的被动适配器
  /// （`infra.adapter.persistence.*`）。被动适配器作为 Repository 实现，必须依赖 Mapper、Converter、DO。
  ///
  /// @return ArchRule 规则实例
  public ArchRule adapterShouldNotDependOnInfra() {
    return noClasses()
        .that()
        .resideInAPackage(".." + serviceName + ".adapter..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(".." + serviceName + ".infra..")
        .as("Adapter 层不能反向依赖 Infra 层")
        .because("Adapter 和 Infra 应该通过 App 层协调，避免直接耦合");
  }

  /// Adapter 层不能依赖 Boot 层。
  ///
  /// @return ArchRule 规则实例
  public ArchRule adapterShouldNotDependOnBoot() {
    return noClasses()
        .that()
        .resideInAPackage(".." + serviceName + ".adapter..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(".." + serviceName + ".boot..")
        .as("Adapter 层不能依赖 Boot 层")
        .because("Boot 层是最外层，只能被依赖，不能反向依赖");
  }

  /// 不允许循环依赖。
  ///
  /// @return ArchRule 规则实例
  public ArchRule noCyclesBetweenLayers() {
    return slices()
        .matching(basePackage + ".(*)..")
        .should()
        .beFreeOfCycles()
        .as("不允许包之间的循环依赖")
        .because("循环依赖会导致代码难以维护和测试，参考 【CHK-ARCH-005】");
  }

  // ==================== Domain 层纯净性规则 ====================

  /// Domain 层完全禁止 Spring 框架依赖。
  ///
  /// @return ArchRule 规则实例
  public ArchRule domainShouldNotDependOnSpring() {
    return noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework..")
        .as("Domain 层完全禁止 Spring 框架依赖")
        .because("Domain 层必须是纯 Java，保持框架无关性（六边形架构铁律 CHK-ARCH-001）");
  }

  /// Domain 层允许的依赖白名单。
  ///
  /// @return ArchRule 规则实例
  public ArchRule domainAllowedDependencies() {
    return classes()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
            "java..",
            "lombok..",
            "cn.hutool..",
            "tools.jackson..",
            "com.fasterxml.jackson.annotation..",
            "com.patra.common..",
            basePackage + ".domain..")
        .as("Domain 层仅允许依赖：JDK、Lombok、Hutool、Jackson、patra-common")
        .because("限制依赖范围确保 Domain 层纯净（CHK-ARCH-001）");
  }

  /// Domain 层不依赖其他业务层。
  ///
  /// @return ArchRule 规则实例
  public ArchRule domainShouldNotDependOnOtherLayers() {
    return noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..app..", "..infra..", "..adapter..", "..boot..")
        .as("Domain 层不依赖其他业务层（app/infra/adapter/boot）")
        .because("Domain 层是依赖图的最底层，其他层通过 DIP 依赖 Domain（CHK-ARCH-002）");
  }

  // ==================== 命名约定规则 ====================

  /// Port 接口必须在 domain.port 包，命名以 Port、Repository 或 Gateway 结尾。
  ///
  /// - **Repository**: 聚合根持久化接口（实现在 Infra 层）
  /// - **Port**: 被驱动端口/Driven Port（实现在 Infra 层）
  /// - **Gateway**: 驱动端口/Driving Port（实现在 App 层）
  ///
  /// @return ArchRule 规则实例
  public ArchRule portsShouldResideInDomainPortPackage() {
    return classes()
        .that()
        .resideInAPackage("..domain.port..")
        .and()
        .areInterfaces()
        .and()
        .haveSimpleNameNotContaining("package-info")
        .should()
        .haveSimpleNameEndingWith("Port")
        .orShould()
        .haveSimpleNameEndingWith("Repository")
        .orShould()
        .haveSimpleNameEndingWith("Gateway")
        .as("Port 接口必须在 domain.port 包，命名以 Port、Repository 或 Gateway 结尾")
        .because("统一的命名约定便于识别 Port 接口");
  }

  /// Port/Repository 接口实现必须在 infra 层（Gateway 除外）。
  ///
  /// **注意**: Gateway 接口（Driving Port）的实现在 App 层，不受此规则约束。
  ///
  /// @return ArchRule 规则实例
  public ArchRule repositoryImplsShouldResideInInfra() {
    return classes()
        .that()
        .resideInAPackage(".." + serviceName + "..")
        .and()
        .areNotInterfaces()
        .and()
        .implement(
            com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage(
                "..domain.port.."))
        .and()
        .haveSimpleNameNotEndingWith("GatewayImpl")
        .should()
        .resideInAPackage("..infra..")
        .as("Domain Port/Repository 接口的实现类必须在 infra 层（Gateway 除外）")
        .because("Infra 层负责实现 Domain 层定义的 Port 接口（六边形架构依赖倒置原则），Gateway 实现在 App 层");
  }

  /// Gateway 接口实现必须在 app 层。
  ///
  /// Gateway 是 Driving Port（驱动端口），其实现在 Application 层，
  /// 与 Repository/Port（实现在 Infra 层）形成对比。
  ///
  /// @return ArchRule 规则实例
  public ArchRule gatewayImplsShouldResideInApp() {
    return classes()
        .that()
        .resideInAPackage(".." + serviceName + "..")
        .and()
        .areNotInterfaces()
        .and()
        .haveSimpleNameEndingWith("GatewayImpl")
        .should()
        .resideInAPackage("..app..")
        .as("Gateway 接口的实现类必须在 app 层")
        .because("Gateway 是 Driving Port，其实现在 Application 层提供业务服务");
  }

  /// DO 类必须在 infra.persistence.entity 包。
  ///
  /// @return ArchRule 规则实例
  public ArchRule doClassesShouldResideInPersistenceEntity() {
    return classes()
        .that()
        .haveSimpleNameEndingWith("DO")
        .should()
        .resideInAPackage("..infra.persistence.entity..")
        .as("DO 类必须在 infra.persistence.entity 包")
        .because("DO 类是持久化层的概念，不应泄露到其他层（CHK-ARCH-004）");
  }

  /// Aggregate 必须在 domain.model.aggregate 包。
  ///
  /// @return ArchRule 规则实例
  public ArchRule aggregatesShouldResideInDomainModelAggregate() {
    return classes()
        .that()
        .haveSimpleNameEndingWith("Aggregate")
        .should()
        .resideInAPackage("..domain.model.aggregate..")
        .as("Aggregate 必须在 domain.model.aggregate 包")
        .because("Aggregate 是 DDD 的核心概念，属于 Domain 层");
  }

  /// Orchestrator/Coordinator 必须在 app.usecase 包。
  ///
  /// @return ArchRule 规则实例
  public ArchRule orchestratorsShouldResideInAppUsecase() {
    return classes()
        .that()
        .haveSimpleNameEndingWith("Orchestrator")
        .or()
        .haveSimpleNameEndingWith("Coordinator")
        .should()
        .resideInAPackage("..app.usecase..")
        .as("Orchestrator/Coordinator 必须在 app.usecase 包")
        .because("编排器是应用层概念，负责用例流程编排和事务边界（CHK-ARCH-003）");
  }

  // ==================== 封装规则 ====================

  /// DO 类不能被 infra 层外部访问。
  ///
  /// @return ArchRule 规则实例
  public ArchRule dataObjectsShouldNotBeAccessedOutsideInfra() {
    return noClasses()
        .that()
        .resideOutsideOfPackage("..infra..")
        .should()
        .dependOnClassesThat()
        .haveSimpleNameEndingWith("DO")
        .as("DO 类不能被 infra 层外部访问")
        .because("DO 是持久化层的内部实现细节，不应泄露到其他层（六边形架构铁律 CHK-ARCH-004）");
  }

  /// Port 接口必须是 public。
  ///
  /// @return ArchRule 规则实例
  public ArchRule portsShouldBePublic() {
    return classes()
        .that()
        .resideInAPackage("..domain.port..")
        .and()
        .areInterfaces()
        .and()
        .haveSimpleNameNotContaining("package-info")
        .should()
        .haveModifier(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC)
        .as("Port 接口必须是 public")
        .because("Port 接口是 Domain 层的公开契约，需要跨模块访问");
  }

  /// Domain Event 必须在 domain.event 包。
  ///
  /// @return ArchRule 规则实例
  public ArchRule eventsShouldResideInDomainEvent() {
    return classes()
        .that()
        .haveSimpleNameEndingWith("Event")
        .and()
        .resideInAPackage("..domain..")
        .and()
        .haveSimpleNameNotEndingWith("CursorEvent")
        .should()
        .resideInAPackage("..domain.event..")
        .as("Domain Event 必须在 domain.event 包")
        .because("领域事件是 DDD 事件驱动架构的核心，应统一管理");
  }

  // ==================== 事务边界规则 ====================

  /// {@code @Transactional} 只能在 app 层。
  ///
  /// @return ArchRule 规则实例
  public ArchRule transactionsOnlyInAppLayer() {
    return classes()
        .that()
        .areAnnotatedWith(Transactional.class)
        .should()
        .resideInAPackage("..app..")
        .as("@Transactional 只能在 app 层")
        .because("事务边界应该在应用层的用例编排器中定义（六边形架构原则 CHK-ARCH-003）");
  }

  /// Domain 层严禁使用 @Transactional。
  ///
  /// @return ArchRule 规则实例
  public ArchRule domainShouldNotUseTransactions() {
    return noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .beAnnotatedWith(Transactional.class)
        .as("Domain 层严禁使用 @Transactional")
        .because("Domain 层必须保持纯净，事务是技术关注点，应该在 app 层管理（CHK-ARCH-001）");
  }

  // ==================== 便捷方法 ====================

  /// 获取所有架构规则列表。
  ///
  /// 便于批量验证所有规则。
  ///
  /// @return 所有规则的不可变列表
  public List<ArchRule> allRules() {
    List<ArchRule> rules = new ArrayList<>();

    // 层依赖规则
    rules.add(layerDependenciesAreRespected());
    rules.add(appShouldNotDependOnInfraClasses());
    rules.add(adapterShouldNotDependOnInfra());
    rules.add(adapterShouldNotDependOnBoot());
    rules.add(noCyclesBetweenLayers());

    // Domain 层纯净性规则
    rules.add(domainShouldNotDependOnSpring());
    rules.add(domainAllowedDependencies());
    rules.add(domainShouldNotDependOnOtherLayers());

    // 命名约定规则
    rules.add(portsShouldResideInDomainPortPackage());
    rules.add(repositoryImplsShouldResideInInfra());
    rules.add(gatewayImplsShouldResideInApp());
    rules.add(doClassesShouldResideInPersistenceEntity());
    rules.add(aggregatesShouldResideInDomainModelAggregate());
    rules.add(orchestratorsShouldResideInAppUsecase());

    // 封装规则
    rules.add(dataObjectsShouldNotBeAccessedOutsideInfra());
    rules.add(portsShouldBePublic());
    rules.add(eventsShouldResideInDomainEvent());

    // 事务边界规则
    rules.add(transactionsOnlyInAppLayer());
    rules.add(domainShouldNotUseTransactions());

    return Collections.unmodifiableList(rules);
  }

  /// 获取服务名。
  ///
  /// @return 服务名
  public String getServiceName() {
    return serviceName;
  }

  /// 获取基础包名。
  ///
  /// @return 基础包名
  public String getBasePackage() {
    return basePackage;
  }
}
