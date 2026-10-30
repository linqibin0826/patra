package dev.patra.registry.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
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

/// patra-registry 架构测试套件。
///
/// 应用 `patra-tool-test` 的三组统一规则：
///
/// - `HexagonalArchitectureRules`：层依赖方向、Domain 纯净性、命名、封装、事务边界
/// - `TestingRules`：测试规范
/// - `SubdomainArchitectureRules`：子域边界与分包模板
///
/// 导入 patra-registry 全部模块的主代码；第三方库、测试代码与 test-fixtures 不导入。
///
/// @see HexagonalArchitectureRules
/// @see TestingRules
/// @see SubdomainArchitectureRules
@DisplayName("patra-registry 架构测试")
class RegistryArchitectureTest {

  private static JavaClasses classes;

  /// 参数化的六边形架构规则（基础包: dev.patra.registry）。
  private static final HexagonalArchitectureRules rules =
      new HexagonalArchitectureRules("dev.patra.registry", List.of("dev.patra.tool.."));

  /// 底座子域：只在 domain 层，放异常根。
  private static final String FOUNDATION = "kernel";

  /// 子域边界规则：只有一个业务子域 dictionary。
  private static final SubdomainArchitectureRules subdomainRules =
      new SubdomainArchitectureRules(
          "dev.patra.registry", FOUNDATION, Set.of(FOUNDATION, "dictionary"));

  /// 导入 patra-registry 全部模块的主代码。
  @BeforeAll
  static void setup() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(
                location -> {
                  // test-fixtures 不导入：夹具不受子域规则管
                  if (location.contains("test-fixtures") || location.contains("/testFixtures/")) {
                    return false;
                  }
                  return location.contains("/patra-registry-") || !location.contains(".jar");
                })
            .importPackages("dev.patra.registry");
  }

  /// 规则 15：@Transactional 只能在 app 层（infra 只允许 MANDATORY 守卫）。
  @Test
  void transactionsShouldOnlyBeInAppLayer() {
    rules.transactionsOnlyInAppLayer().check(classes);
  }

  /// 规则 16：禁止类级 @Transactional。
  @Test
  void shouldNotUseClassLevelTransactions() {
    rules.noClassLevelTransactions().check(classes);
  }

  /// 规则 16b：命令 Handler 不得标 @Transactional。
  @Test
  void commandHandlersShouldNotBeTransactional() {
    rules.commandHandlersShouldNotBeTransactional().check(classes);
  }

  /// 六边形规则全部执行，汇总所有违规一次报出。
  @Test
  void hexagonalRulesShouldHold() {
    List<ArchRule> all = rules.allRules();
    List<String> violations =
        all.stream()
            .map(rule -> rule.evaluate(classes))
            .filter(EvaluationResult::hasViolation)
            .map(result -> result.getFailureReport().toString())
            .toList();
    assertThat(violations).isEmpty();
  }

  @Nested
  @DisplayName("测试规范规则")
  class TestingRulesTests {

    /// 规则 17：测试类命名必须符合规范。
    @Test
    @DisplayName("规则 17: 测试类命名必须符合规范")
    void testClassesShouldFollowNamingConvention() {
      TestingRules.testClassesShouldFollowNamingConvention().check(classes);
    }

    /// 规则 18：Domain 层测试禁止依赖 Spring。
    @Test
    @DisplayName("规则 18: Domain 层测试禁止依赖 Spring")
    void domainTestsShouldNotDependOnSpring() {
      TestingRules.domainTestsShouldNotDependOnSpring().check(classes);
    }

    /// 规则 19：测试方法必须有 @Test 注解。
    @Test
    @DisplayName("规则 19: 测试方法必须有 @Test 注解")
    void testMethodsShouldHaveTestAnnotation() {
      TestingRules.testMethodsShouldHaveTestAnnotation().check(classes);
    }

    /// 规则 20：Repository 集成测试必须在 infra 模块。
    @Test
    @DisplayName("规则 20: Repository 集成测试必须在 infra 模块")
    void repositoryIntegrationTestsShouldResideInInfra() {
      TestingRules.repositoryIntegrationTestsShouldResideInInfra().check(classes);
    }

    /// 规则 21：测试类不能依赖其他测试类。
    @Test
    @DisplayName("规则 21: 测试类不能依赖其他测试类")
    void testsShouldNotDependOnOtherTests() {
      TestingRules.testsShouldNotDependOnOtherTests().check(classes);
    }

    /// 规则 22：E2E 测试必须在 boot 模块。
    @Test
    @DisplayName("规则 22: E2E 测试必须在 boot 模块")
    void e2eTestsShouldResideInBootModule() {
      TestingRules.e2eTestsShouldResideInBootModule().check(classes);
    }
  }

  @Nested
  @DisplayName("子域边界规则")
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
