package dev.patra.tool.test.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// 写侧技术规则（`.claude/rules/tech/database.md` 第 8 节）：JPA 服务的写入经实体或 Dao 上的 `@Query`，
/// 原生 SQL 服务不出现 JPA。每个夹具子包当作一个服务的根包。
@DisplayName("写侧技术规则")
class PersistenceTechnologyRulesTest {

  /// 夹具根包。
  private static final String FIXTURES = "dev.patra.tool.test.archunit.fixtures.persistence";

  @Test
  void should_accept_compliant_jpa_service() {
    assertThat(violatesJpa("jpacompliant", Set.of())).isFalse();
  }

  @Test
  void should_reject_jdbc_write_in_jpa_service() {
    assertThat(violatesJpa("jpajdbcwrite", Set.of())).isTrue();
  }

  @Test
  void should_accept_jdbc_in_read_adapter() {
    assertThat(violatesJpa("jpareadadapter", Set.of())).isFalse();
  }

  @Test
  void should_accept_exempt_lock_adapter() {
    String lockAdapter = FIXTURES + ".jpaexempt.infra.kernel.lock.RunLockAdapter";
    assertThat(violatesJpa("jpaexempt", Set.of(lockAdapter))).isFalse();
    assertThat(violatesJpa("jpaexempt", Set.of())).isTrue();
  }

  @Test
  void should_reject_modifying_without_flush_and_clear() {
    assertThat(violatesJpa("jpamodifyingflags", Set.of())).isTrue();
  }

  @Test
  void should_reject_modifying_without_query() {
    assertThat(violatesJpa("jpamodifyingnoquery", Set.of())).isTrue();
  }

  @Test
  void should_reject_execute_update() {
    assertThat(violatesJpa("jpaexecuteupdate", Set.of())).isTrue();
  }

  @Test
  void should_reject_native_query_outside_read_adapter() {
    assertThat(violatesJpa("jpanativequery", Set.of())).isTrue();
  }

  @Test
  void should_reject_entity_without_standard_base() {
    assertThat(violatesJpa("jpaentitybase", Set.of())).isTrue();
  }

  @Test
  void should_accept_compliant_sql_service() {
    assertThat(violatesSql("sqlcompliant")).isFalse();
  }

  @Test
  void should_reject_jpa_in_sql_service() {
    assertThat(violatesSql("sqlentity")).isTrue();
  }

  @Test
  void should_reject_jdbc_client_outside_persistence() {
    assertThat(violatesSql("sqljdbcoutside")).isTrue();
  }

  private static boolean violatesJpa(String scenario, Set<String> exempt) {
    return violates(
        PersistenceTechnologyRules.jpaService(FIXTURES + "." + scenario, exempt), scenario);
  }

  private static boolean violatesSql(String scenario) {
    return violates(PersistenceTechnologyRules.sqlService(FIXTURES + "." + scenario), scenario);
  }

  /// 规则列表里任一条在夹具子包上有违规即算违规。
  private static boolean violates(List<ArchRule> rules, String scenario) {
    JavaClasses classes = new ClassFileImporter().importPackages(FIXTURES + "." + scenario);
    return rules.stream().anyMatch(rule -> rule.evaluate(classes).hasViolation());
  }
}
