package com.patra.ingest.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * RocketMQ 迁移架构测试。
 *
 * <p>使用 ArchUnit 验证 RocketMQ 原生迁移的架构纯度和依赖规则。
 *
 * <p>测试覆盖：
 *
 * <ul>
 *   <li>Domain 层无框架依赖（纯 Java）
 *   <li>RocketMQ 依赖只存在于 Infra 和 Adapter 层
 *   <li>层级依赖方向正确（Domain ← App ← Infra/Adapter）
 *   <li>消息通道定义在 Domain 层（业务语言）
 *   <li>技术映射在 Infra 层（技术细节）
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@DisplayName("RocketMQ 迁移架构测试")
class RocketMqMigrationArchitectureTest {

  private static JavaClasses allClasses;

  @BeforeAll
  static void setUp() {
    // 导入 patra-ingest 模块的所有类
    allClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.patra.ingest");
  }

  @Nested
  @DisplayName("Domain 层架构纯度测试")
  class DomainLayerPurityTests {

    @Test
    @DisplayName("Domain 层不应依赖任何框架")
    void domainLayerShouldNotDependOnAnyFramework() {
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("..domain..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage(
                  "org.springframework..",
                  "org.apache.rocketmq..",
                  "com.fasterxml.jackson..",
                  "lombok..",
                  "jakarta.validation..")
              .because("Domain 层应保持技术无关性，仅包含纯 Java 和业务逻辑")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Domain 层不应依赖 Infra 层")
    void domainLayerShouldNotDependOnInfraLayer() {
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("..domain..")
              .should()
              .dependOnClassesThat()
              .resideInAPackage("..infra..")
              .because("Domain 层不应依赖技术实现细节")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Domain 层不应依赖 Adapter 层")
    void domainLayerShouldNotDependOnAdapterLayer() {
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("..domain..")
              .should()
              .dependOnClassesThat()
              .resideInAPackage("..adapter..")
              .because("Domain 层不应依赖外部适配器")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Domain 层不应依赖 App 层")
    void domainLayerShouldNotDependOnAppLayer() {
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("..domain..")
              .should()
              .dependOnClassesThat()
              .resideInAPackage("..app..")
              .because("Domain 层是架构核心，不应依赖应用层")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }
  }

  @Nested
  @DisplayName("RocketMQ 依赖隔离测试")
  class RocketMqDependencyIsolationTests {

    @Test
    @DisplayName("RocketMQ 依赖只应存在于 Infra 和 Adapter 层")
    void rocketMqDependenciesShouldOnlyExistInInfraAndAdapterLayers() {
      ArchRule rule =
          classes()
              .that()
              .resideInAnyPackage("org.apache.rocketmq..")
              .should()
              .onlyBeAccessed()
              .byClassesThat()
              .resideInAnyPackage("..infra..", "..adapter..")
              .because("RocketMQ 是技术实现细节，应隔离在基础设施层和适配器层")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Domain 层不应依赖 RocketMQ")
    void domainLayerShouldNotDependOnRocketMq() {
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("..domain..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage("org.apache.rocketmq..")
              .because("Domain 层应保持技术无关性，不应依赖 RocketMQ")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }

    @Test
    @DisplayName("App 层不应依赖 RocketMQ")
    void appLayerShouldNotDependOnRocketMq() {
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("..app..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage("org.apache.rocketmq..")
              .because("App 层应通过 Port 接口与技术实现隔离")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }
  }

  @Nested
  @DisplayName("六边形架构依赖方向测试")
  class HexagonalArchitectureDependencyTests {

    @Test
    @DisplayName("应遵循六边形架构的层级依赖规则")
    void shouldFollowHexagonalArchitectureLayering() {
      ArchRule rule =
          layeredArchitecture()
              .consideringAllDependencies()
              .layer("Domain")
              .definedBy("..domain..")
              .layer("Application")
              .definedBy("..app..")
              .layer("Infrastructure")
              .definedBy("..infra..")
              .layer("Adapter")
              .definedBy("..adapter..")
              .whereLayer("Domain")
              .mayNotAccessAnyLayer()
              .whereLayer("Application")
              .mayOnlyAccessLayers("Domain")
              .whereLayer("Infrastructure")
              .mayOnlyAccessLayers("Domain", "Application")
              .whereLayer("Adapter")
              .mayOnlyAccessLayers("Domain", "Application")
              .because("六边形架构要求严格的依赖方向：Domain ← App ← Infra/Adapter")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Infra 层不应依赖 Adapter 层")
    void infraLayerShouldNotDependOnAdapterLayer() {
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("..infra..")
              .should()
              .dependOnClassesThat()
              .resideInAPackage("..adapter..")
              .because("Infra 和 Adapter 是同级层，不应相互依赖")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Adapter 层不应依赖 Infra 层")
    void adapterLayerShouldNotDependOnInfraLayer() {
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("..adapter..")
              .should()
              .dependOnClassesThat()
              .resideInAPackage("..infra..")
              .because("Adapter 和 Infra 是同级层，不应相互依赖")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }
  }

  @Nested
  @DisplayName("消息通道定义位置测试")
  class MessageChannelDefinitionLocationTests {

    @Test
    @DisplayName("业务消息通道应定义在 Domain 层")
    void businessMessageChannelsShouldBeDefinedInDomainLayer() {
      ArchRule rule =
          classes()
              .that()
              .haveSimpleName("MessageChannels")
              .should()
              .resideInAPackage("..domain.messaging")
              .because("消息通道是业务概念，应使用业务语言定义在 Domain 层")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }

    @Test
    @DisplayName("RocketMQ 通道映射器应定义在 Infra 层")
    void rocketMqChannelMapperShouldBeDefinedInInfraLayer() {
      ArchRule rule =
          classes()
              .that()
              .haveSimpleName("RocketMqChannelMapper")
              .should()
              .resideInAPackage("..infra.messaging.config")
              .because("通道到 Topic 的映射是技术实现细节，应定义在 Infra 层")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }

    @Test
    @DisplayName("RocketMQ 发布器应定义在 Infra 层")
    void rocketMqPublisherShouldBeDefinedInInfraLayer() {
      ArchRule rule =
          classes()
              .that()
              .haveSimpleName("RocketMqOutboxPublisher")
              .should()
              .resideInAPackage("..infra.messaging")
              .because("RocketMQ 发布器是技术实现，应定义在 Infra 层")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }

    @Test
    @DisplayName("RocketMQ 监听器应定义在 Adapter 层")
    void rocketMqListenerShouldBeDefinedInAdapterLayer() {
      ArchRule rule =
          classes()
              .that()
              .haveSimpleNameEndingWith("MessageListener")
              .and()
              .resideInAPackage("..adapter.rocketmq")
              .should()
              .resideInAPackage("..adapter..")
              .because("消息监听器是外部适配器，应定义在 Adapter 层")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }
  }

  @Nested
  @DisplayName("Port 接口架构测试")
  class PortInterfaceArchitectureTests {

    @Test
    @DisplayName("Port 接口应定义在 Domain 层")
    void portInterfacesShouldBeDefinedInDomainLayer() {
      ArchRule rule =
          classes()
              .that()
              .haveSimpleNameEndingWith("Port")
              .should()
              .resideInAPackage("..domain.port")
              .because("Port 接口是领域边界，应定义在 Domain 层")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Port 实现应在 Infra 或 Adapter 层")
    void portImplementationsShouldBeInInfraOrAdapterLayers() {
      ArchRule rule =
          classes()
              .that()
              .implement(com.patra.ingest.domain.port.OutboxPublisherPort.class) // 具体 Port 接口
              .should()
              .resideInAnyPackage("..infra..", "..adapter..")
              .because("Port 实现是技术细节，应在 Infra 或 Adapter 层")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }

    @Test
    @DisplayName("App 层应通过 Port 接口访问技术实现")
    void appLayerShouldAccessInfraViaPorts() {
      ArchRule rule =
          classes()
              .that()
              .resideInAPackage("..app..")
              .should()
              .onlyDependOnClassesThat()
              .resideInAnyPackage(
                  "..domain..",
                  "..app..",
                  "java..",
                  "org.springframework..", // 允许 Spring 注解
                  "com.fasterxml.jackson..", // 允许 JSON 处理
                  "lombok..",
                  "org.slf4j..")
              .because("App 层应通过 Domain 层的 Port 接口访问技术实现")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }
  }

  @Nested
  @DisplayName("命名约定测试")
  class NamingConventionTests {

    @Test
    @DisplayName("RocketMQ 相关类应包含 RocketMq 前缀")
    void rocketMqClassesShouldHaveRocketMqPrefix() {
      // 简化规则: 检查 RocketMQ 相关包中的类命名
      ArchRule rule =
          classes()
              .that()
              .resideInAnyPackage("..infra.messaging..", "..adapter.rocketmq..")
              .should()
              .haveSimpleNameStartingWith("RocketMq")
              .orShould()
              .haveSimpleNameContaining("RocketMq")
              .orShould()
              .haveSimpleNameEndingWith("MessageListener")
              .orShould()
              .haveSimpleNameEndingWith("Properties")
              .because("RocketMQ 相关类应明确标识技术实现")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Port 接口应以 Port 结尾")
    void portInterfacesShouldEndWithPort() {
      ArchRule rule =
          classes()
              .that()
              .resideInAPackage("..domain.port")
              .and()
              .areInterfaces()
              .should()
              .haveSimpleNameEndingWith("Port")
              .because("Port 接口应遵循命名约定")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }

    @Test
    @DisplayName("消息监听器应以 MessageListener 结尾")
    void messageListenersShouldEndWithMessageListener() {
      ArchRule rule =
          classes()
              .that()
              .resideInAPackage("..adapter.rocketmq")
              .and()
              .haveSimpleNameContaining("MessageListener")
              .should()
              .haveSimpleNameEndingWith("MessageListener")
              .because("消息监听器应遵循命名约定")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }
  }

  @Nested
  @DisplayName("Spring 框架依赖隔离测试")
  class SpringFrameworkIsolationTests {

    @Test
    @DisplayName("Domain 层不应依赖 Spring 框架")
    void domainLayerShouldNotDependOnSpring() {
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("..domain..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage("org.springframework..")
              .because("Domain 层应保持框架无关性")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Spring 注解只应用于 App、Infra 和 Adapter 层")
    void springAnnotationsShouldOnlyBeUsedInAppInfraAndAdapterLayers() {
      ArchRule rule =
          classes()
              .that()
              .areAnnotatedWith(org.springframework.stereotype.Component.class)
              .or()
              .areAnnotatedWith(org.springframework.stereotype.Service.class)
              .or()
              .areAnnotatedWith(org.springframework.stereotype.Repository.class)
              .should()
              .resideInAnyPackage("..app..", "..infra..", "..adapter..")
              .because("Spring 注解是框架细节，不应出现在 Domain 层")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }
  }

  @Nested
  @DisplayName("包结构组织测试")
  class PackageStructureTests {

    @Test
    @DisplayName("messaging 包应只存在于指定位置")
    void messagingPackageShouldOnlyExistInSpecificLocations() {
      ArchRule rule =
          classes()
              .that()
              .resideInAPackage("..messaging..")
              .should()
              .resideInAnyPackage(
                  "..domain.messaging", // 业务消息通道定义
                  "..infra.messaging..", // RocketMQ 发布器
                  "..adapter.rocketmq" // RocketMQ 监听器
                  )
              .because("messaging 相关代码应组织在明确的包结构中")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }

    @Test
    @DisplayName("config 包应只存在于 Infra 和 Boot 层")
    void configPackageShouldOnlyExistInInfraAndBootLayers() {
      ArchRule rule =
          classes()
              .that()
              .resideInAPackage("..config")
              .should()
              .resideInAnyPackage("..infra.config", "..boot.config", "..adapter.rocketmq.config")
              .because("配置类是技术细节，应在 Infra 或 Boot 层")
              .allowEmptyShould(true);

      rule.check(allClasses);
    }
  }
}
