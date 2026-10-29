package dev.patra.registry.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import dev.patra.tool.test.archunit.HexagonalArchitectureRules;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// patra-registry 架构测试：事务边界规则（`.claude/rules/tech/transaction.md` 的规则 15 / 16 / 16b）。
///
/// 导入 patra-registry 全部模块的主代码，第三方库与测试代码不导入。
///
/// @see HexagonalArchitectureRules
@DisplayName("patra-registry 架构测试")
class RegistryArchitectureTest {

  private static JavaClasses classes;

  /// 参数化的六边形架构规则（基础包: dev.patra.registry）。
  private static final HexagonalArchitectureRules rules =
      new HexagonalArchitectureRules(
          "dev.patra.registry", List.of("dev.patra.common..", "dev.patra.tool.."));

  /// 导入 patra-registry 全部模块的主代码。
  @BeforeAll
  static void setup() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(
                location -> location.contains("/patra-registry-") || !location.contains(".jar"))
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
}
