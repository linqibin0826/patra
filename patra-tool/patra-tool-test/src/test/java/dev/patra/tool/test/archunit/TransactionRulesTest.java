package dev.patra.tool.test.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// 事务规则（`.claude/rules/tech/transaction.md` 第 6 节）：方法级注解也要查，infra 只放行 `MANDATORY`，
/// 类级注解和命令 Handler 上的注解一律违规。
@DisplayName("六边形层规则：事务边界")
class TransactionRulesTest {

  /// 事务夹具根包。
  private static final String FIXTURES = "dev.patra.tool.test.archunit.fixtures.transaction";

  /// 规则实例；事务规则只看包名里的层，与基础包无关。
  private static final HexagonalArchitectureRules RULES = new HexagonalArchitectureRules(FIXTURES);

  /// app 层方法级事务、infra 层 `MANDATORY` 守卫不违规。
  @Test
  void should_accept_app_method_and_infra_mandatory() {
    assertThat(violates(RULES.transactionsOnlyInAppLayer(), "compliant")).isFalse();
  }

  /// infra 层方法开事务违规。
  @Test
  void should_reject_infra_method_opening_transaction() {
    assertThat(violates(RULES.transactionsOnlyInAppLayer(), "infrarequired")).isTrue();
  }

  /// domain 层方法带事务注解违规。
  @Test
  void should_reject_domain_method_with_transactional() {
    assertThat(violates(RULES.transactionsOnlyInAppLayer(), "domainmethod")).isTrue();
  }

  /// 类级 `@Transactional` 违规。
  @Test
  void should_reject_class_level_transactional() {
    assertThat(violates(RULES.noClassLevelTransactions(), "classlevel")).isTrue();
  }

  /// 方法级注解不算类级违规。
  @Test
  void should_accept_method_level_only() {
    assertThat(violates(RULES.noClassLevelTransactions(), "compliant")).isFalse();
  }

  /// 命令 Handler 上加 `@Transactional` 违规。
  @Test
  void should_reject_transactional_command_handler() {
    assertThat(violates(RULES.commandHandlersShouldNotBeTransactional(), "handler")).isTrue();
  }

  /// 不带注解的命令 Handler 不违规。
  @Test
  void should_accept_command_handler_without_transactional() {
    assertThat(violates(RULES.commandHandlersShouldNotBeTransactional(), "compliant")).isFalse();
  }

  /// 在某个夹具子包上评估规则。
  ///
  /// @param rule 规则
  /// @param scenario 夹具子包名
  /// @return 是否有违规
  private static boolean violates(ArchRule rule, String scenario) {
    JavaClasses classes =
        new ClassFileImporter().importPackages(FIXTURES + "." + scenario, FIXTURES + ".cqrs");
    return rule.evaluate(classes).hasViolation();
  }
}
