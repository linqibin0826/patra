package dev.patra.interaction.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import dev.patra.tool.test.archunit.HexagonalArchitectureRules;
import dev.patra.tool.test.archunit.SubdomainArchitectureRules;
import dev.patra.tool.test.archunit.TestingRules;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// patra-interaction 架构测试套件。
///
/// 应用 `patra-tool-test` 的三组统一规则：
///
/// - `HexagonalArchitectureRules`：层依赖方向、Domain 纯净性、命名、封装、事务边界
/// - `TestingRules`：测试规范
/// - `SubdomainArchitectureRules`：子域边界（层下面只有已声明的子域、子域间无环、
///   跨子域只经公开包、底座不依赖其他子域）
///
/// 导入 interaction 全部模块的主代码；test-fixtures 不导入，测试代码不受子域规则管。
///
/// @see HexagonalArchitectureRules
/// @see TestingRules
/// @see SubdomainArchitectureRules
@DisplayName("patra-interaction 架构测试")
class InteractionArchitectureTest {

  private static JavaClasses classes;

  /// 参数化的六边形架构规则（基础包: dev.patra.interaction，业务公共包: patra-common、tool）。
  private static final HexagonalArchitectureRules rules =
      new HexagonalArchitectureRules(
          "dev.patra.interaction", List.of("dev.patra.common..", "dev.patra.tool.."));

  /// 子域边界规则：底座 subject，本版两个子域。
  private static final SubdomainArchitectureRules subdomainRules =
      new SubdomainArchitectureRules(
          "dev.patra.interaction", "subject", Set.of("subject", "collection"));

  /// 导入 interaction 全部模块的主代码。
  @BeforeAll
  static void setup() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(
                location -> {
                  // test-fixtures 不导入：夹具（如 infra.config 下的容器初始化器）不受子域规则管
                  if (location.contains("test-fixtures") || location.contains("/testFixtures/")) {
                    return false;
                  }
                  // 包含 patra-interaction 各模块（jar 或类目录）
                  if (location.contains("/patra-interaction-")) {
                    return true;
                  }
                  // 排除第三方库 JAR
                  return !location.contains(".jar");
                })
            .importPackages("dev.patra.interaction");
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
    @DisplayName("规则 8b: Gateway 实现必须在 app 层")
    void gatewayImplsShouldResideInApp() {
      rules.gatewayImplsShouldResideInApp().check(classes);
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

  @Nested
  @DisplayName("第七类：子域边界规则")
  class SubdomainBoundaryTests {

    /// 每层的每个子域包都被导入了：导入条件写错时这里先红，其余规则才不会对空集合判通过。
    @Test
    @DisplayName("规则 22b: 四层的两个子域包都在导入结果里")
    void everySubdomainPackageShouldBeImported() {
      for (String layer : List.of("domain", "app", "infra", "adapter")) {
        for (String subdomain : List.of("subject", "collection")) {
          String packageName = "dev.patra.interaction." + layer + "." + subdomain;
          assertThat(classes.containPackage(packageName)).as(packageName).isTrue();
        }
      }
    }

    @Test
    @DisplayName("规则 23: 层下面只有已声明的子域")
    void layersShouldContainOnlyDeclaredSubdomains() {
      subdomainRules.layersContainOnlyDeclaredSubdomains().check(classes);
    }

    @Test
    @DisplayName("规则 24: 子域之间无环")
    void subdomainsShouldBeFreeOfCycles() {
      subdomainRules.subdomainsAreFreeOfCycles().check(classes);
    }

    @Test
    @DisplayName("规则 25: 跨子域只经对方的公开包")
    void crossSubdomainDependenciesShouldGoThroughPublishedPackages() {
      subdomainRules.crossSubdomainDependenciesGoThroughPublishedPackages().check(classes);
    }

    @Test
    @DisplayName("规则 26: 底座子域不依赖其他子域")
    void foundationShouldDependOnNoOtherSubdomain() {
      subdomainRules.foundationDependsOnNoOtherSubdomain().check(classes);
    }
  }
}
