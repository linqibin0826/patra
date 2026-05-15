package dev.linqibin.patra.catalog.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import dev.linqibin.starter.test.archunit.HexagonalArchitectureRules;
import dev.linqibin.starter.test.archunit.TestingRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// patra-catalog 架构测试套件
///
/// 验证六边形架构 + DDD 的所有核心约束：
///
/// - 层依赖方向（Adapter → App → Domain ← Infra）
/// - Domain 层纯净性（零 Spring 依赖）
/// - 命名约定（Port/DO/Aggregate/Orchestrator）
/// - 封装规则（DO 不泄露、Port 可见性）
/// - 事务边界（@Transactional 仅在 App 层）
/// - 测试规范（命名规范、测试独立性、分层测试策略）
///
/// **运行方式：**
///
/// ```
/// # 单独运行架构测试
/// mvn test -Dtest=CatalogArchitectureTest
///
/// # 运行所有测试
/// mvn test
/// ```
///
/// **使用 starter-test 统一规则：**
///
/// 本测试类使用 `patra-spring-boot-starter-test` 提供的 `HexagonalArchitectureRules`
/// 和 `TestingRules`，确保所有服务遵循相同的架构约束。
///
/// @author linqibin
/// @since 0.1.0
/// @see HexagonalArchitectureRules
/// @see TestingRules
@DisplayName("patra-catalog 架构测试")
class CatalogArchitectureTest {

  private static JavaClasses classes;

  /// 参数化的六边形架构规则（基础包: com.patra.catalog）。
  private static final HexagonalArchitectureRules rules =
      new HexagonalArchitectureRules("dev.linqibin.patra.catalog");

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
            .importPackages("dev.linqibin.patra.catalog"); // 导入 com.patra.catalog 及其所有子包
  }

  @Nested
  @DisplayName("第一类：层依赖方向规则")
  class LayerDependencyTests {

    @Test
    @DisplayName("规则 1-6: 五层架构依赖方向必须正确")
    void layerDependenciesShouldBeRespected() {
      rules.layerDependenciesAreRespected().check(classes);
    }

    @Test
    @DisplayName("规则 4: App 层不能直接依赖 Infra 层")
    void appShouldNotDependOnInfra() {
      rules.appShouldNotDependOnInfraClasses().check(classes);
    }

    @Test
    @DisplayName("规则 5: Adapter 层不能反向依赖 Infra 层")
    void adapterShouldNotDependOnInfra() {
      rules.adapterShouldNotDependOnInfra().check(classes);
    }

    @Test
    @DisplayName("规则 6: Adapter 层不能依赖 Boot 层")
    void adapterShouldNotDependOnBoot() {
      rules.adapterShouldNotDependOnBoot().check(classes);
    }

    @Test
    @DisplayName("规则 7: 不允许循环依赖")
    void noCircularDependencies() {
      rules.noCyclesBetweenLayers().check(classes);
    }
  }

  @Nested
  @DisplayName("第二类：Domain 层纯净性规则")
  class DomainLayerTests {

    @Test
    @DisplayName("规则 2: Domain 层完全禁止 Spring 框架依赖")
    void domainShouldNotDependOnSpring() {
      rules.domainShouldNotDependOnSpring().check(classes);
    }

    @Test
    @DisplayName("规则 3: Domain 层允许 Jackson 依赖")
    void domainCanUseJackson() {
      rules.domainAllowedDependencies().check(classes);
    }

    @Test
    @DisplayName("规则 1: Domain 层不依赖其他业务层")
    void domainShouldNotDependOnOtherLayers() {
      rules.domainShouldNotDependOnOtherLayers().check(classes);
    }
  }

  @Nested
  @DisplayName("第三类：命名约定规则")
  class NamingConventionTests {

    @Test
    @DisplayName("规则 7: Port 接口必须在 domain.port 包")
    void portsShouldResideInDomainPortPackage() {
      rules.portsShouldResideInDomainPortPackage().check(classes);
    }

    @Test
    @DisplayName("规则 8: Repository 实现必须在 infra 层")
    void repositoryImplsShouldResideInInfra() {
      rules.repositoryImplsShouldResideInInfra().check(classes);
    }

    @Test
    @DisplayName("规则 9: DO 类必须在 infra.persistence.entity 包")
    void doClassesShouldResideInPersistenceEntity() {
      rules.doClassesShouldResideInPersistenceEntity().check(classes);
    }

    @Test
    @DisplayName("规则 10: Aggregate 必须在 domain.model.aggregate 包")
    void aggregatesShouldResideInDomainModelAggregate() {
      rules.aggregatesShouldResideInDomainModelAggregate().check(classes);
    }

    @Test
    @DisplayName("规则 11: Orchestrator 必须在 app.usecase 包")
    void orchestratorsShouldResideInAppUsecase() {
      rules.orchestratorsShouldResideInAppUsecase().check(classes);
    }
  }

  @Nested
  @DisplayName("第四类：封装规则")
  class EncapsulationTests {

    @Test
    @DisplayName("规则 12: DO 类不能被 infra 层外部访问")
    void dataObjectsShouldNotBeAccessedOutsideInfra() {
      rules.dataObjectsShouldNotBeAccessedOutsideInfra().check(classes);
    }

    @Test
    @DisplayName("规则 13: Port 接口必须是 public")
    void portsShouldBePublic() {
      rules.portsShouldBePublic().check(classes);
    }

    @Test
    @DisplayName("规则 14: Domain Event 必须在 domain.event 包")
    void eventsShouldResideInDomainEvent() {
      rules.eventsShouldResideInDomainEvent().check(classes);
    }
  }

  @Nested
  @DisplayName("第五类：事务边界规则")
  class TransactionBoundaryTests {

    @Test
    @DisplayName("规则 15: @Transactional 只能在 app 层")
    void transactionsShouldOnlyBeInAppLayer() {
      rules.transactionsOnlyInAppLayer().check(classes);
    }

    @Test
    @DisplayName("规则 16: Domain 层严禁使用 @Transactional")
    void domainShouldNotUseTransactions() {
      rules.domainShouldNotUseTransactions().check(classes);
    }
  }

  @Nested
  @DisplayName("第六类：测试规范规则")
  class TestingRulesTests {

    @Test
    @DisplayName("规则 17: 测试类命名必须符合规范")
    void testClassesShouldFollowNamingConvention() {
      TestingRules.testClassesShouldFollowNamingConvention().check(classes);
    }

    @Test
    @DisplayName("规则 18: Domain 层测试禁止依赖 Spring")
    void domainTestsShouldNotDependOnSpring() {
      TestingRules.domainTestsShouldNotDependOnSpring().check(classes);
    }

    @Test
    @DisplayName("规则 19: 测试方法必须有 @Test 注解")
    void testMethodsShouldHaveTestAnnotation() {
      TestingRules.testMethodsShouldHaveTestAnnotation().check(classes);
    }

    @Test
    @DisplayName("规则 20: Repository 集成测试必须在 infra 模块")
    void repositoryIntegrationTestsShouldResideInInfra() {
      TestingRules.repositoryIntegrationTestsShouldResideInInfra().check(classes);
    }

    @Test
    @DisplayName("规则 21: 测试类不能依赖其他测试类")
    void testsShouldNotDependOnOtherTests() {
      TestingRules.testsShouldNotDependOnOtherTests().check(classes);
    }

    @Test
    @DisplayName("规则 22: E2E 测试必须在 boot 模块")
    void e2eTestsShouldResideInBootModule() {
      TestingRules.e2eTestsShouldResideInBootModule().check(classes);
    }
  }
}
