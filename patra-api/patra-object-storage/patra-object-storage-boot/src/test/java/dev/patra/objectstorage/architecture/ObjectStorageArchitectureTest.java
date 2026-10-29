package dev.patra.objectstorage.architecture;

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
import org.junit.jupiter.api.Test;

/// patra-object-storage 架构测试。
///
/// 应用 `patra-tool-test` 的三组统一规则：
///
/// - `HexagonalArchitectureRules`：层依赖方向、Domain 纯净性、命名、封装、事务边界
/// - `TestingRules`：测试规范
/// - `SubdomainArchitectureRules`：子域边界与分包模板（唯一子域 `file`，同时声明为底座）
///
/// 导入 patra-object-storage 全部模块的主代码；第三方库、测试代码与 test-fixtures 不导入。
///
/// @see HexagonalArchitectureRules
/// @see TestingRules
/// @see SubdomainArchitectureRules
@DisplayName("patra-object-storage 架构测试")
class ObjectStorageArchitectureTest {

  private static JavaClasses classes;

  /// 参数化的六边形架构规则（基础包: dev.patra.objectstorage）。
  private static final HexagonalArchitectureRules rules =
      new HexagonalArchitectureRules(
          "dev.patra.objectstorage", List.of("dev.patra.common..", "dev.patra.tool.."));

  /// 子域边界规则：单子域服务，唯一的子域 `file` 同时是底座。
  private static final SubdomainArchitectureRules subdomainRules =
      new SubdomainArchitectureRules("dev.patra.objectstorage", "file", Set.of("file"));

  /// 导入 patra-object-storage 全部模块的主代码。
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
                  // 包含 patra-object-storage 各模块（jar 或类目录）
                  if (location.contains("/patra-object-storage-")) {
                    return true;
                  }
                  // 排除第三方库 JAR
                  return !location.contains(".jar");
                })
            .importPackages("dev.patra.objectstorage");
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

  /// 规则 17：测试类命名必须符合规范。
  @Test
  void testClassesShouldFollowNamingConvention() {
    TestingRules.testClassesShouldFollowNamingConvention().check(classes);
  }

  /// 规则 18：Domain 层测试禁止依赖 Spring。
  @Test
  void domainTestsShouldNotDependOnSpring() {
    TestingRules.domainTestsShouldNotDependOnSpring().check(classes);
  }

  /// 规则 19：测试方法必须有 @Test 注解。
  @Test
  void testMethodsShouldHaveTestAnnotation() {
    TestingRules.testMethodsShouldHaveTestAnnotation().check(classes);
  }

  /// 规则 20：Repository 集成测试必须在 infra 模块。
  @Test
  void repositoryIntegrationTestsShouldResideInInfra() {
    TestingRules.repositoryIntegrationTestsShouldResideInInfra().check(classes);
  }

  /// 规则 21：测试类不能依赖其他测试类。
  @Test
  void testsShouldNotDependOnOtherTests() {
    TestingRules.testsShouldNotDependOnOtherTests().check(classes);
  }

  /// 规则 22：E2E 测试必须在 boot 模块。
  @Test
  void e2eTestsShouldResideInBootModule() {
    TestingRules.e2eTestsShouldResideInBootModule().check(classes);
  }

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
