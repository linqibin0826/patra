package dev.patra.tool.test.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// 发布器规则（`.claude/rules/tech/domain-events.md`）：事件由 Handler 发布，app 层以外只有写发件箱的 `Outbox*` 适配器能用。
@DisplayName("六边形层规则：事件发布器")
class EventPublisherRulesTest {

  private static final String FIXTURES = "dev.patra.tool.test.archunit.fixtures.eventpublisher";

  /// 规则实例；发布器规则只看包名里的层，与基础包无关。
  private static final HexagonalArchitectureRules RULES = new HexagonalArchitectureRules(FIXTURES);

  /// app 层、`Outbox*` 适配器用发布器不违规；只用 `ApplicationContext` 也不算（它继承发布器接口，但不是在发事件）。
  @Test
  void should_accept_app_layer_and_outbox_adapter() {
    assertThat(violates("compliant")).isFalse();
  }

  /// 普通 infra 适配器用发布器违规。
  @Test
  void should_reject_publisher_in_ordinary_infra_adapter() {
    assertThat(violates("infrapublisher")).isTrue();
  }

  /// adapter 层用发布器违规。
  @Test
  void should_reject_publisher_in_adapter_layer() {
    assertThat(violates("adapterpublisher")).isTrue();
  }

  /// 在某个夹具子包上评估规则。
  ///
  /// @param scenario 夹具子包名
  /// @return 是否有违规
  private static boolean violates(String scenario) {
    JavaClasses classes = new ClassFileImporter().importPackages(FIXTURES + "." + scenario);
    return RULES.eventPublisherOnlyInAppLayer().evaluate(classes).hasViolation();
  }
}
