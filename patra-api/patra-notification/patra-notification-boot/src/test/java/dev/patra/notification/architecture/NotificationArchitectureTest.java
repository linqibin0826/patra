package dev.patra.notification.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.EvaluationResult;
import dev.patra.tool.test.archunit.HexagonalArchitectureRules;
import dev.patra.tool.test.archunit.SubdomainArchitectureRules;
import dev.patra.tool.test.archunit.TestingRules;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// patra-notification 架构测试套件。
///
/// 应用 `patra-tool-test` 的三组统一规则：
///
/// - `HexagonalArchitectureRules`：层依赖方向、Domain 纯净性、命名、封装、事务边界
/// - `TestingRules`：测试规范
/// - `SubdomainArchitectureRules`：子域边界（层下面只有已声明的子域、子域间无环、
///   跨子域只经公开包、跨子域依赖必须声明、底座不依赖其他子域）
///
/// 导入 notification 全部模块的主代码；test-fixtures 不导入，测试代码不受子域规则管。
///
/// @see HexagonalArchitectureRules
/// @see TestingRules
/// @see SubdomainArchitectureRules
@DisplayName("patra-notification 架构测试")
class NotificationArchitectureTest {

  private static JavaClasses classes;

  /// 参数化的六边形架构规则（基础包: dev.patra.notification，业务公共包: tool）。
  private static final HexagonalArchitectureRules rules =
      new HexagonalArchitectureRules("dev.patra.notification", List.of("dev.patra.tool.."));

  /// 底座子域：被两个以上子域共用的东西（提醒类别、取用户名的端口、在站信号、注销清理扩展点）。
  private static final String FOUNDATION = "kernel";

  /// 声明的全部子域（PAP-139 定）：信箱（PAP-143 / 144）、偏好与退订（PAP-142）、邮件（PAP-105 / 146）、实时推送
  /// （PAP-145）；各子域随第一批代码建自己的类，骨架期只有 `package-info`。
  private static final List<String> SUBDOMAINS =
      List.of(FOUNDATION, "inbox", "preference", "mail", "realtime");

  /// 子域边界规则，三条依赖边：realtime → inbox（PAP-145）；mail → inbox、mail → preference（PAP-146）。
  private static final SubdomainArchitectureRules subdomainRules =
      new SubdomainArchitectureRules("dev.patra.notification", FOUNDATION, Set.copyOf(SUBDOMAINS))
          // 实时推送订阅未读数变化、按用户读当前未读数（PAP-145）
          .allowingDependency("realtime", "inbox")
          // 提醒邮件：同事务入队、到点复查已读、每日汇总取未读；查邮件档位与退订令牌（PAP-146）
          .allowingDependency("mail", "inbox")
          .allowingDependency("mail", "preference");

  /// 导入 notification 全部模块的主代码。
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
                  // 包含 patra-notification 各模块（jar 或类目录）
                  if (location.contains("/patra-notification-")) {
                    return true;
                  }
                  // 排除第三方库 JAR
                  return !location.contains(".jar");
                })
            .importPackages("dev.patra.notification");
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

    /// 子域规则全部执行（`SubdomainArchitectureRules.allRules()`），汇总所有违规一次报出；规则新增时自动生效。
    @Test
    @DisplayName("子域规则：分包模板与子域边界")
    void subdomainRulesShouldHold() {
      List<String> violations =
          subdomainRules.allRules().stream()
              .map(rule -> rule.evaluate(classes))
              .filter(EvaluationResult::hasViolation)
              .map(result -> result.getFailureReport().toString())
              .toList();
      assertThat(violations).isEmpty();
    }
  }
}
