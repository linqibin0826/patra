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
///   跨子域只经公开包、跨子域依赖必须声明、底座不依赖其他子域）
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

  /// 底座子域。
  private static final String FOUNDATION = "subject";

  /// 声明的全部子域：v0.14 起讨论、投稿经验、提醒（PAP-121 ~ 123）一次写进来，各子域随第一批代码建自己的包。
  private static final List<String> SUBDOMAINS =
      List.of(FOUNDATION, "collection", "discussion", "post", "notification");

  /// 按子域分包的四层（api 模块还没有类）。
  private static final List<String> LAYERS = List.of("domain", "app", "infra", "adapter");

  /// 子域边界规则。依赖方向按 v0.14 决策 A：提醒只经事件契约依赖讨论与投稿经验（PAP-127 经验删除即删提醒），
  /// 其余子域只依赖底座。
  private static final SubdomainArchitectureRules subdomainRules =
      new SubdomainArchitectureRules("dev.patra.interaction", FOUNDATION, Set.copyOf(SUBDOMAINS))
          .allowingSubscription("notification", "discussion")
          .allowingSubscription("notification", "post");

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
    @DisplayName("规则 9: JPA 实体必须在 infra.persistence.entity 包")
    void jpaEntitiesShouldResideInPersistenceEntity() {
      rules.jpaEntitiesShouldResideInPersistenceEntity().check(classes);
    }

    @Test
    @DisplayName("规则 10: Aggregate 必须在 domain.model.aggregate 包")
    void aggregatesShouldResideInDomainModelAggregate() {
      rules.aggregatesShouldResideInDomainModelAggregate().check(classes);
    }
  }

  @Nested
  @DisplayName("第四类：封装规则")
  class EncapsulationTests {

    @Test
    @DisplayName("规则 12: JPA 实体不能被 infra 层外部访问")
    void jpaEntitiesShouldNotBeAccessedOutsideInfra() {
      rules.jpaEntitiesShouldNotBeAccessedOutsideInfra().check(classes);
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
    @DisplayName("规则 15: @Transactional 只能在 app 层（infra 只允许 MANDATORY 守卫）")
    void transactionsShouldOnlyBeInAppLayer() {
      rules.transactionsOnlyInAppLayer().check(classes);
    }

    @Test
    @DisplayName("规则 16: 禁止类级 @Transactional")
    void shouldNotUseClassLevelTransactions() {
      rules.noClassLevelTransactions().check(classes);
    }

    @Test
    @DisplayName("规则 16b: 命令 Handler 不得标 @Transactional")
    void commandHandlersShouldNotBeTransactional() {
      rules.commandHandlersShouldNotBeTransactional().check(classes);
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

    /// 底座四层必须都被导入：导入条件写错时这里先红，其余规则才不会对空集合判通过。其余子域只要有一层出现，
    /// 四层就都要在：新子域谁先加代码谁补齐四层的包，不用改这条测试，也不建没有代码的空包。
    @Test
    @DisplayName("规则 22b: 底座四层都在；其余子域出现在任一层时四层都在")
    void everySubdomainPackageShouldBeImported() {
      for (String subdomain : SUBDOMAINS) {
        boolean present =
            subdomain.equals(FOUNDATION)
                || LAYERS.stream()
                    .anyMatch(layer -> classes.containPackage(packageOf(layer, subdomain)));
        if (present) {
          for (String layer : LAYERS) {
            assertThat(classes.containPackage(packageOf(layer, subdomain)))
                .as(packageOf(layer, subdomain))
                .isTrue();
          }
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

    @Test
    @DisplayName("规则 27: 跨子域依赖必须声明（提醒只经事件契约依赖讨论）")
    void crossSubdomainDependenciesShouldBeDeclared() {
      subdomainRules.crossSubdomainDependenciesAreDeclared().check(classes);
    }

    /// 某层某子域的包名。
    ///
    /// @param layer 层
    /// @param subdomain 子域
    /// @return 包名
    private static String packageOf(String layer, String subdomain) {
      return "dev.patra.interaction." + layer + "." + subdomain;
    }
  }
}
