package com.patra.registry.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
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
    packages = "com.patra.registry"
    // 不设置 importOptions,使用 archunit.properties 中的配置允许加载 JAR
)
class ArchitectureTest {

  // ==================== 六边形架构层级定义 ====================

  private static final String BOOT_LAYER = "Boot";
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
          .layer(BOOT_LAYER)
          .definedBy("com.patra.registry.boot..")
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
          // Boot 层可以访问所有层(负责组装)
          .whereLayer(BOOT_LAYER)
          .mayOnlyAccessLayers(ADAPTER_LAYER, APP_LAYER, INFRA_LAYER, DOMAIN_LAYER, API_LAYER)
          // Domain 层可以被所有层访问,但不能访问 Boot
          .whereLayer(DOMAIN_LAYER)
          .mayOnlyBeAccessedByLayers(APP_LAYER, INFRA_LAYER, ADAPTER_LAYER, API_LAYER, BOOT_LAYER)
          // App 层只能被 Adapter 和 Boot 访问
          .whereLayer(APP_LAYER)
          .mayOnlyBeAccessedByLayers(ADAPTER_LAYER, BOOT_LAYER)
          // Infra 层只能被 Boot 访问
          .whereLayer(INFRA_LAYER)
          .mayOnlyBeAccessedByLayers(BOOT_LAYER)
          // Adapter 层只能被 Boot 访问
          .whereLayer(ADAPTER_LAYER)
          .mayOnlyBeAccessedByLayers(BOOT_LAYER)
          // API 层可以被 Adapter 和 Boot 访问
          .whereLayer(API_LAYER)
          .mayOnlyBeAccessedByLayers(ADAPTER_LAYER, BOOT_LAYER)
          .allowEmptyShould(true)  // 允许层为空(多模块项目中可能无法加载JAR中的类)
          .as("六边形架构依赖方向: boot(组装) → adapter → app → domain ← infra");

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

  @ArchTest
  static final ArchRule no_layer_should_depend_on_boot =
      noClasses()
          .that()
          .resideInAnyPackage(
              "com.patra.registry.domain..",
              "com.patra.registry.app..",
              "com.patra.registry.infra..",
              "com.patra.registry.adapter..",
              "com.patra.registry.api..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.patra.registry.boot..")
          .as("业务层不应依赖 Boot 层(Boot 是最上层的组装模块)");

  // ==================== Boot 层职责规则 ====================

  @ArchTest
  static final ArchRule boot_should_not_contain_business_logic =
      noClasses()
          .that()
          .resideInAPackage("com.patra.registry.boot..")
          .should()
          .beAnnotatedWith("org.springframework.stereotype.Service")
          .orShould()
          .beAnnotatedWith("org.springframework.stereotype.Repository")
          .orShould()
          .beAnnotatedWith("org.springframework.stereotype.Component")
          .orShould()
          .beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
          .allowEmptyShould(true)
          .as("Boot 层不应包含业务逻辑(@Service/@Repository/@Component/@RestController)");

  @ArchTest
  static final ArchRule spring_configurations_only_in_boot =
      classes()
          .that()
          .areAnnotatedWith("org.springframework.context.annotation.Configuration")
          .and()
          .areNotAnnotatedWith("org.springframework.boot.test.context.TestConfiguration")
          .should()
          .resideInAPackage("com.patra.registry.boot..")
          .allowEmptyShould(true)
          .as("Spring @Configuration 类应该只在 boot 模块中(负责组装)");

  @ArchTest
  static final ArchRule spring_boot_application_only_in_boot =
      classes()
          .that()
          .areAnnotatedWith("org.springframework.boot.autoconfigure.SpringBootApplication")
          .should()
          .resideInAPackage("com.patra.registry.boot..")
          .allowEmptyShould(true)
          .as("@SpringBootApplication 应该只在 boot 模块中");

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

  @ArchTest
  static final ArchRule domain_should_not_depend_on_dtos =
      noClasses()
          .that()
          .resideInAPackage("com.patra.registry.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.patra.registry.api.dto..")
          .as("Domain 层不应依赖 API DTO(防止外部契约泄漏到核心领域)");

  // ==================== 循环依赖检查 ====================

  @ArchTest
  static final ArchRule no_cycles_between_packages =
      slices()
          .matching("com.patra.registry.(*)..")
          .should()
          .beFreeOfCycles()
          .as("包之间不应存在循环依赖");

  // ==================== Repository 接口规则 ====================

  @ArchTest
  static final ArchRule repository_interfaces_should_be_in_domain_port =
      classes()
          .that()
          .areInterfaces()
          .and()
          .haveSimpleNameEndingWith("Repository")
          .and()
          .areNotAnnotatedWith("org.apache.ibatis.annotations.Mapper")
          .should()
          .resideInAPackage("com.patra.registry.domain.port.outbound..")
          .allowEmptyShould(true)
          .as("Repository 接口应定义在 domain.port.outbound 包中");

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
  static final ArchRule mapstruct_converters_should_be_interfaces =
      classes()
          .that()
          .areAnnotatedWith("org.mapstruct.Mapper")
          .should()
          .beInterfaces()
          .andShould()
          .haveSimpleNameEndingWith("Converter")
          .orShould()
          .haveSimpleNameEndingWith("Assembler")
          .allowEmptyShould(true)
          .as("MapStruct Converter 应该是接口且遵循命名约定");

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
          .allowEmptyShould(true)
          .as("@RestController 注解只应在 Adapter 层使用");

  @ArchTest
  static final ArchRule service_only_in_app =
      classes()
          .that()
          .areAnnotatedWith("org.springframework.stereotype.Service")
          .should()
          .resideInAPackage("com.patra.registry.app..")
          .allowEmptyShould(true)
          .as("@Service 注解只应在 Application 层使用");

  @ArchTest
  static final ArchRule repository_only_in_infra =
      classes()
          .that()
          .areAnnotatedWith("org.springframework.stereotype.Repository")
          .should()
          .resideInAPackage("com.patra.registry.infra..")
          .allowEmptyShould(true)
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

  // ==================== DDD 领域模型规则 ====================

  @ArchTest
  static final ArchRule value_objects_should_not_use_lombok_data =
      noClasses()
          .that()
          .resideInAPackage("com.patra.registry.domain..vo..")
          .should()
          .beAnnotatedWith("lombok.Data")
          .allowEmptyShould(true)
          .as("Value Object 不应使用 @Data 注解(应使用 @Value 保证不可变性)");

  @ArchTest
  static final ArchRule query_objects_should_be_in_domain_read =
      classes()
          .that()
          .haveSimpleNameEndingWith("Query")
          .and()
          .resideInAPackage("com.patra.registry.domain..")
          .should()
          .resideInAPackage("com.patra.registry.domain..read..")
          .allowEmptyShould(true)
          .as("Query 对象应位于 domain 层的 read 包中");

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

  @ArchTest
  static final ArchRule feign_clients_only_in_api =
      classes()
          .that()
          .areAnnotatedWith("org.springframework.cloud.openfeign.FeignClient")
          .should()
          .resideInAPackage("com.patra.registry.api..")
          .allowEmptyShould(true)
          .as("Feign 客户端应定义在 api 模块中(作为外部契约)");
}
