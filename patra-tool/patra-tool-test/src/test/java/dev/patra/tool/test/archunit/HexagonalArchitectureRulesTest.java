package dev.patra.tool.test.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// 六边形层规则在按子域分包的布局下的行为：`layer.子域.x` 也要被接受，放错位置的照样被拒绝。
@DisplayName("六边形层规则：按子域分包的布局")
class HexagonalArchitectureRulesTest {

  /// 按子域布局放置的夹具根包。
  private static final String SUBDOMAIN_LAYOUT =
      "dev.patra.tool.test.archunit.fixtures.subdomainlayout";

  /// 放错位置的夹具根包。
  private static final String MISPLACED = "dev.patra.tool.test.archunit.fixtures.misplaced";

  /// 按子域布局的夹具类。
  private static final JavaClasses LAYOUT_CLASSES =
      new ClassFileImporter().importPackages(SUBDOMAIN_LAYOUT);

  /// 以按子域布局的夹具根包为基础包的规则。
  private static final HexagonalArchitectureRules LAYOUT_RULES =
      new HexagonalArchitectureRules(SUBDOMAIN_LAYOUT);

  /// 子域下的 `model.aggregate` 里的聚合不违规。
  @Test
  void should_accept_aggregate_under_subdomain_model_aggregate() {
    assertThat(
            LAYOUT_RULES
                .aggregatesShouldResideInDomainModelAggregate()
                .evaluate(LAYOUT_CLASSES)
                .hasViolation())
        .isFalse();
  }

  /// 子域下的 `event` 里的领域事件不违规。
  @Test
  void should_accept_event_under_subdomain_event() {
    assertThat(
            LAYOUT_RULES.eventsShouldResideInDomainEvent().evaluate(LAYOUT_CLASSES).hasViolation())
        .isFalse();
  }

  /// 子域下的 `persistence.entity` 里的 JPA 实体不违规。
  @Test
  void should_accept_jpa_entity_under_subdomain_persistence_entity() {
    assertThat(
            LAYOUT_RULES
                .jpaEntitiesShouldResideInPersistenceEntity()
                .evaluate(LAYOUT_CLASSES)
                .hasViolation())
        .isFalse();
  }

  /// 不在 `model.aggregate` 下的聚合仍然违规：放宽的只是中间的层级，不是位置本身。
  @Test
  void should_reject_aggregate_outside_model_aggregate() {
    JavaClasses misplaced = new ClassFileImporter().importPackages(MISPLACED);

    assertThat(
            new HexagonalArchitectureRules(MISPLACED)
                .aggregatesShouldResideInDomainModelAggregate()
                .evaluate(misplaced)
                .hasViolation())
        .isTrue();
  }

  /// 不在 `persistence.entity` 下的 JPA 实体违规；名字以 Entity 结尾的普通接口不算实体。
  @Test
  void should_reject_misplaced_jpa_entity() {
    JavaClasses misplaced = new ClassFileImporter().importPackages(MISPLACED);

    EvaluationResult result =
        new HexagonalArchitectureRules(MISPLACED)
            .jpaEntitiesShouldResideInPersistenceEntity()
            .evaluate(misplaced);

    assertThat(result.hasViolation()).isTrue();
    assertThat(result.getFailureReport().getDetails())
        .anyMatch(line -> line.contains("MisplacedEntity"))
        .noneMatch(line -> line.contains("TemporalEntity"))
        .noneMatch(line -> line.contains("LeakedEntity"));
  }

  /// infra 之外引用 JPA 实体违规。
  @Test
  void should_reject_jpa_entity_used_outside_infra() {
    JavaClasses misplaced = new ClassFileImporter().importPackages(MISPLACED);

    EvaluationResult result =
        new HexagonalArchitectureRules(MISPLACED)
            .jpaEntitiesShouldNotBeAccessedOutsideInfra()
            .evaluate(misplaced);

    assertThat(result.hasViolation()).isTrue();
    assertThat(result.getFailureReport().getDetails())
        .anyMatch(line -> line.contains("UsesLeakedEntity"));
  }
}
