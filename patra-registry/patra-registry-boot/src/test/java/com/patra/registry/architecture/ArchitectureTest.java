package com.patra.registry.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;

/**
 * ArchUnit 架构测试 - 验证 patra-registry 的六边形架构规则。
 *
 * <p>本测试类验证以下架构原则：
 *
 * <ul>
 *   <li>六边形架构依赖方向：adapter → app → domain ← infra
 *   <li>Domain 层纯净性：不依赖框架和其他业务层
 *   <li>命名约定：Orchestrator、Repository、Endpoint、Converter、Mapper 等
 *   <li>注解使用规范：@Service、@Repository、@RestController 等的层级限制
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("Patra Registry 架构测试")
@AnalyzeClasses(
    packages = "com.patra.registry",
    importOptions = {ImportOption.DoNotIncludeTests.class}
    // 注意：移除默认的 DoNotIncludeJars，允许从 Maven 依赖 JAR 中加载其他模块的类
)
class ArchitectureTest {

  // ==================== 六边形架构层级定义 ====================

  private static final String DOMAIN_LAYER = "Domain";
  private static final String APP_LAYER = "Application";
  private static final String INFRA_LAYER = "Infrastructure";
  private static final String ADAPTER_LAYER = "Adapter";
  private static final String API_LAYER = "API";

  // ==================== 六边形架构依赖规则 ====================

  @ArchTest
  static final ArchRule hexagonal_architecture_is_respected =
      layeredArchitecture()
          .consideringAllDependencies()
          .layer(DOMAIN_LAYER)
          .definedBy("com.patra.registry.domain..")
          .layer(APP_LAYER)
          .definedBy("com.patra.registry.app..")
          .layer(INFRA_LAYER)
          .definedBy("com.patra.registry.infra..")
          .layer(ADAPTER_LAYER)
          .definedBy("com.patra.registry.adapter..")
          .layer(API_LAYER)
          .definedBy("com.patra.registry.api..")
          .whereLayer(DOMAIN_LAYER)
          .mayOnlyBeAccessedByLayers(APP_LAYER, INFRA_LAYER, ADAPTER_LAYER, API_LAYER)
          .whereLayer(APP_LAYER)
          .mayOnlyBeAccessedByLayers(ADAPTER_LAYER)
          .whereLayer(INFRA_LAYER)
          .mayNotBeAccessedByAnyLayer()
          .whereLayer(ADAPTER_LAYER)
          .mayNotBeAccessedByAnyLayer()
          .as("六边形架构依赖方向必须是: adapter → app → domain ← infra");

  @ArchTest
  static final ArchRule domain_should_not_depend_on_other_layers =
      noClasses()
          .that()
          .resideInAPackage("com.patra.registry.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.patra.registry.app..",
              "com.patra.registry.infra..",
              "com.patra.registry.adapter..")
          .as("Domain 层不应依赖 Application、Infrastructure 或 Adapter 层");

  @ArchTest
  static final ArchRule app_should_only_depend_on_domain =
      noClasses()
          .that()
          .resideInAPackage("com.patra.registry.app..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.patra.registry.infra..", "com.patra.registry.adapter..")
          .as("Application 层只应依赖 Domain 层，不应依赖 Infrastructure 或 Adapter 层");

  @ArchTest
  static final ArchRule infra_should_only_depend_on_domain =
      noClasses()
          .that()
          .resideInAPackage("com.patra.registry.infra..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.patra.registry.app..", "com.patra.registry.adapter..")
          .as("Infrastructure 层只应依赖 Domain 层，不应依赖 Application 或 Adapter 层");

  @ArchTest
  static final ArchRule adapter_should_depend_on_app_and_domain =
      noClasses()
          .that()
          .resideInAPackage("com.patra.registry.adapter..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.patra.registry.infra..")
          .as("Adapter 层应依赖 Application 和 Domain 层，不应依赖 Infrastructure 层");

  // ==================== Domain 层纯净性规则 ====================

  @ArchTest
  static final ArchRule domain_should_not_use_spring_framework =
      noClasses()
          .that()
          .resideInAPackage("com.patra.registry.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta.persistence..",
              "jakarta.validation..",
              "jakarta.servlet..")
          .as("Domain 层必须是纯 Java，不应依赖 Spring Framework 或 Jakarta EE");

  @ArchTest
  static final ArchRule domain_should_not_use_persistence_frameworks =
      noClasses()
          .that()
          .resideInAPackage("com.patra.registry.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.baomidou..", "org.mybatis..", "org.hibernate..")
          .as("Domain 层不应依赖 MyBatis-Plus、Hibernate 等持久化框架");

  // ==================== 命名约定规则 ====================

  @ArchTest
  static final ArchRule orchestrators_should_be_in_app_service_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("Orchestrator")
          .should()
          .resideInAPackage("com.patra.registry.app.service")
          .andShould()
          .beAnnotatedWith("org.springframework.stereotype.Service")
          .as("Orchestrator 类应位于 app.service 包中并使用 @Service 注解");

  @ArchTest
  static final ArchRule repository_implementations_should_be_in_infra =
      classes()
          .that()
          .haveSimpleNameEndingWith("RepositoryMpImpl")
          .should()
          .resideInAPackage("com.patra.registry.infra.persistence.repository")
          .andShould()
          .beAnnotatedWith("org.springframework.stereotype.Repository")
          .as("Repository 实现类应位于 infra.persistence.repository 包中并使用 @Repository 注解");

  @ArchTest
  static final ArchRule endpoint_implementations_should_be_in_adapter =
      classes()
          .that()
          .haveSimpleNameEndingWith("EndpointImpl")
          .should()
          .resideInAPackage("com.patra.registry.adapter.rest")
          .andShould()
          .beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
          .as("Endpoint 实现类应位于 adapter.rest 包中并使用 @RestController 注解");

  @ArchTest
  static final ArchRule converters_should_follow_naming_convention =
      classes()
          .that()
          .resideInAnyPackage("com.patra.registry.app.converter..", "com.patra.registry.infra..")
          .and()
          .haveSimpleNameContaining("Converter")
          .or()
          .haveSimpleNameContaining("Assembler")
          .should()
          .haveSimpleNameEndingWith("Converter")
          .orShould()
          .haveSimpleNameEndingWith("Assembler")
          .as("Converter 和 Assembler 类应遵循命名约定");

  @ArchTest
  static final ArchRule mybatis_mappers_should_end_with_mapper =
      classes()
          .that()
          .resideInAPackage("com.patra.registry.infra.persistence.mapper..")
          .and()
          .areInterfaces()
          .should()
          .haveSimpleNameEndingWith("Mapper")
          .as("MyBatis Mapper 接口应以 Mapper 结尾");

  @ArchTest
  static final ArchRule database_entities_should_end_with_do =
      classes()
          .that()
          .resideInAPackage("com.patra.registry.infra.persistence.entity..")
          .and()
          .areNotNestedClasses()
          .should()
          .haveSimpleNameEndingWith("DO")
          .as("数据库实体类应以 DO 结尾");

  // ==================== 注解使用规范 ====================

  @ArchTest
  static final ArchRule rest_controller_only_in_adapter =
      classes()
          .that()
          .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
          .should()
          .resideInAPackage("com.patra.registry.adapter..")
          .as("@RestController 注解只应在 Adapter 层使用");

  @ArchTest
  static final ArchRule service_only_in_app =
      classes()
          .that()
          .areAnnotatedWith("org.springframework.stereotype.Service")
          .should()
          .resideInAPackage("com.patra.registry.app..")
          .as("@Service 注解只应在 Application 层使用");

  @ArchTest
  static final ArchRule repository_only_in_infra =
      classes()
          .that()
          .areAnnotatedWith("org.springframework.stereotype.Repository")
          .should()
          .resideInAPackage("com.patra.registry.infra..")
          .as("@Repository 注解只应在 Infrastructure 层使用");

  // ==================== Port 接口规则 ====================

  @ArchTest
  static final ArchRule ports_should_be_interfaces_in_domain =
      classes()
          .that()
          .resideInAPackage("com.patra.registry.domain.port..")
          .should()
          .beInterfaces()
          .as("Domain 层的 Port 应该是接口");

  @ArchTest
  static final ArchRule domain_exceptions_should_extend_base_exception =
      classes()
          .that()
          .resideInAPackage("com.patra.registry.domain.exception..")
          .and()
          .haveSimpleNameNotEndingWith("Exception")
          .should()
          .beAssignableTo(com.patra.registry.domain.exception.RegistryException.class)
          .orShould()
          .beAssignableTo(RuntimeException.class)
          .as("Domain 异常应继承自 RegistryException 或 RuntimeException");

  // ==================== API 契约规则 ====================

  @ArchTest
  static final ArchRule api_dtos_should_follow_naming_convention =
      classes()
          .that()
          .resideInAPackage("com.patra.registry.api.dto..")
          .and()
          .areNotNestedClasses()
          .should()
          .haveSimpleNameEndingWith("Resp")
          .orShould()
          .haveSimpleNameEndingWith("Req")
          .as("API DTO 类应以 Resp 或 Req 结尾");

  @ArchTest
  static final ArchRule api_endpoints_should_be_interfaces =
      classes()
          .that()
          .resideInAPackage("com.patra.registry.api.endpoint..")
          .and()
          .haveSimpleNameEndingWith("Endpoint")
          .should()
          .beInterfaces()
          .as("API Endpoint 应该是接口");
}
