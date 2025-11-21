package com.patra.catalog.architecture;

import com.patra.catalog.architecture.rules.DomainLayerRules;
import com.patra.catalog.architecture.rules.EncapsulationRules;
import com.patra.catalog.architecture.rules.LayerDependencyRules;
import com.patra.catalog.architecture.rules.NamingConventionRules;
import com.patra.catalog.architecture.rules.TestingRules;
import com.patra.catalog.architecture.rules.TransactionBoundaryRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * patra-catalog 架构测试套件
 *
 * <p>验证六边形架构 + DDD 的所有核心约束：
 *
 * <ul>
 *   <li>层依赖方向（Adapter → App → Domain ← Infra）
 *   <li>Domain 层纯净性（零 Spring 依赖）
 *   <li>命名约定（Port/DO/Aggregate/Orchestrator）
 *   <li>封装规则（DO 不泄露、Port 可见性）
 *   <li>事务边界（@Transactional 仅在 App 层）
 *   <li>测试规范（命名规范、测试独立性、分层测试策略）
 * </ul>
 *
 * <p><b>运行方式：</b>
 *
 * <pre>
 * # 单独运行架构测试
 * mvn test -Dtest=CatalogArchitectureTest
 *
 * # 运行所有测试
 * mvn test
 * </pre>
 *
 * <p><b>冻结模式：</b> 首次运行会记录现有违规到 {@code src/test/resources/archunit/} 目录， 后续运行会禁止新增违规，并要求逐步减少现有违规。
 *
 * @author linqibin
 * @since 2025-01-10
 */
@DisplayName("patra-catalog 架构测试")
class CatalogArchitectureTest {

  private static JavaClasses classes;

  @BeforeAll
  static void setup() {
    // 导入 patra-catalog 所有模块的类（domain/app/infra/adapter/boot）
    // 自定义 ImportOption：包含 patra-catalog 模块的 JAR，排除第三方库
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(
                location -> {
                  // 包含 patra-catalog 模块的 JAR
                  if (location.contains("/patra-catalog-")) {
                    return true;
                  }
                  // 排除第三方库 JAR
                  if (location.contains(".jar")) {
                    return false;
                  }
                  // 包含 target/classes 目录（当前模块）
                  return true;
                })
            .importPackages("com.patra.catalog"); // 导入 com.patra.catalog 及其所有子包
  }

  @Nested
  @DisplayName("第一类：层依赖方向规则")
  class LayerDependencyTests {

    @Test
    @DisplayName("规则 1-6: 五层架构依赖方向必须正确")
    void layerDependenciesShouldBeRespected() {
      LayerDependencyRules.layer_dependencies_are_respected.check(classes);
    }

    @Test
    @DisplayName("规则 4: App 层不能直接依赖 Infra 层")
    void appShouldNotDependOnInfra() {
      LayerDependencyRules.app_should_not_depend_on_infra_classes.check(classes);
    }

    @Test
    @DisplayName("规则 5: Adapter 层不能反向依赖 Infra 层")
    void adapterShouldNotDependOnInfra() {
      LayerDependencyRules.adapter_should_not_depend_on_infra.check(classes);
    }

    @Test
    @DisplayName("规则 6: Adapter 层不能依赖 Boot 层")
    void adapterShouldNotDependOnBoot() {
      LayerDependencyRules.adapter_should_not_depend_on_boot.check(classes);
    }

    @Test
    @DisplayName("规则 7: 不允许循环依赖")
    void noCircularDependencies() {
      LayerDependencyRules.no_cycles_between_layers.check(classes);
    }
  }

  @Nested
  @DisplayName("第二类：Domain 层纯净性规则")
  class DomainLayerTests {

    @Test
    @DisplayName("规则 2: Domain 层完全禁止 Spring 框架依赖")
    void domainShouldNotDependOnSpring() {
      DomainLayerRules.domain_should_not_depend_on_spring.check(classes);
    }

    @Test
    @DisplayName("规则 3: Domain 层允许 Jackson 依赖")
    void domainCanUseJackson() {
      DomainLayerRules.domain_allowed_dependencies.check(classes);
    }

    @Test
    @DisplayName("规则 1: Domain 层不依赖其他业务层")
    void domainShouldNotDependOnOtherLayers() {
      DomainLayerRules.domain_should_not_depend_on_other_layers.check(classes);
    }
  }

  @Nested
  @DisplayName("第三类：命名约定规则")
  class NamingConventionTests {

    @Test
    @DisplayName("规则 7: Port 接口必须在 domain.port 包")
    void portsShouldResideInDomainPortPackage() {
      NamingConventionRules.ports_should_reside_in_domain_port_package.check(classes);
    }

    @Test
    @DisplayName("规则 8: Repository 实现必须在 infra 层")
    void repositoryImplsShouldResideInInfra() {
      NamingConventionRules.repository_impls_should_reside_in_infra.check(classes);
    }

    @Test
    @DisplayName("规则 9: DO 类必须在 infra.persistence.entity 包")
    void doClassesShouldResideInPersistenceEntity() {
      NamingConventionRules.do_classes_should_reside_in_persistence_entity.check(classes);
    }

    @Test
    @DisplayName("规则 10: Aggregate 必须在 domain.model.aggregate 包")
    void aggregatesShouldResideInDomainModelAggregate() {
      NamingConventionRules.aggregates_should_reside_in_domain_model_aggregate.check(classes);
    }

    @Test
    @DisplayName("规则 11: Orchestrator 必须在 app.usecase 包")
    void orchestratorsShouldResideInAppUsecase() {
      NamingConventionRules.orchestrators_should_reside_in_app_usecase.check(classes);
    }
  }

  @Nested
  @DisplayName("第四类：封装规则")
  class EncapsulationTests {

    @Test
    @DisplayName("规则 12: DO 类不能被 infra 层外部访问")
    void dataObjectsShouldNotBeAccessedOutsideInfra() {
      EncapsulationRules.data_objects_should_not_be_accessed_outside_infra.check(classes);
    }

    @Test
    @DisplayName("规则 13: Port 接口必须是 public")
    void portsShouldBePublic() {
      EncapsulationRules.ports_should_be_public.check(classes);
    }

    @Test
    @DisplayName("规则 14: Domain Event 必须在 domain.event 包")
    void eventsShouldResideInDomainEvent() {
      EncapsulationRules.events_should_reside_in_domain_event.check(classes);
    }
  }

  @Nested
  @DisplayName("第五类：事务边界规则")
  class TransactionBoundaryTests {

    @Test
    @DisplayName("规则 15: @Transactional 只能在 app 层")
    void transactionsShouldOnlyBeInAppLayer() {
      TransactionBoundaryRules.transactions_only_in_app_layer.check(classes);
    }

    @Test
    @DisplayName("规则 16: Domain 层严禁使用 @Transactional")
    void domainShouldNotUseTransactions() {
      TransactionBoundaryRules.domain_should_not_use_transactions.check(classes);
    }
  }

  @Nested
  @DisplayName("第六类：测试规范规则")
  class TestingRulesTests {

    @Test
    @DisplayName("规则 17: 测试类命名必须符合规范")
    void testClassesShouldFollowNamingConvention() {
      TestingRules.test_classes_should_follow_naming_convention.check(classes);
    }

    @Test
    @DisplayName("规则 18: Domain 层测试禁止依赖 Spring")
    void domainTestsShouldNotDependOnSpring() {
      TestingRules.domain_tests_should_not_depend_on_spring.check(classes);
    }

    @Test
    @DisplayName("规则 19: 测试方法必须有 @Test 注解")
    void testMethodsShouldHaveTestAnnotation() {
      TestingRules.test_methods_should_have_test_annotation.check(classes);
    }

    @Test
    @DisplayName("规则 20: Repository 集成测试必须在 infra 模块")
    void repositoryIntegrationTestsShouldResideInInfra() {
      TestingRules.repository_integration_tests_should_reside_in_infra.check(classes);
    }

    @Test
    @DisplayName("规则 21: 测试类不能依赖其他测试类")
    void testsShouldNotDependOnOtherTests() {
      TestingRules.tests_should_not_depend_on_other_tests.check(classes);
    }

    @Test
    @DisplayName("规则 22: E2E 测试必须在 boot 模块")
    void e2eTestsShouldResideInBootModule() {
      TestingRules.e2e_tests_should_reside_in_boot_module.check(classes);
    }
  }
}
