package dev.patra.tool.test.archunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// 子域规则：每个场景是 `fixtures.subdomain.<场景>` 下的一棵独立包树，各自像一个微型服务，
/// 底座子域是 `subject`，另外两个子域是 `collection`、`note`。
@DisplayName("子域边界规则")
class SubdomainArchitectureRulesTest {

  /// 夹具场景的公共前缀。
  private static final String FIXTURES = "dev.patra.tool.test.archunit.fixtures.subdomain.";

  /// 夹具声明的全部子域。
  private static final Set<String> SUBDOMAINS = Set.of("subject", "collection", "note");

  /// 合规场景的类全部落在声明的子域或组装根里。
  @Test
  void should_accept_classes_inside_declared_subdomains_and_boot_packages() {
    assertThat(
            evaluate("compliant", rules("compliant").layersContainOnlyDeclaredSubdomains())
                .hasViolation())
        .isFalse();
  }

  /// 直接放在层根下的类违规。
  @Test
  void should_reject_class_directly_under_layer_root() {
    EvaluationResult result =
        evaluate("loose", rules("loose").layersContainOnlyDeclaredSubdomains());

    assertThat(result.hasViolation()).isTrue();
    assertThat(result.getFailureReport().getDetails()).anyMatch(line -> line.contains("Loose"));
  }

  /// 未声明的子域包里的类违规。
  @Test
  void should_reject_undeclared_subdomain_package() {
    EvaluationResult result =
        evaluate("undeclared", rules("undeclared").layersContainOnlyDeclaredSubdomains());

    assertThat(result.hasViolation()).isTrue();
    assertThat(result.getFailureReport().getDetails()).anyMatch(line -> line.contains("Shared"));
  }

  /// 底座不在声明的子域里时拒绝构造。
  @Test
  void should_reject_foundation_missing_from_subdomains() {
    assertThatThrownBy(() -> new SubdomainArchitectureRules("x", "subject", Set.of("collection")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /// 只经底座公开包使用底座、组装根引用两个子域的内部类，都不违规。
  @Test
  void should_allow_published_packages_across_subdomains() {
    assertThat(
            evaluate(
                    "compliant",
                    rules("compliant").crossSubdomainDependenciesGoThroughPublishedPackages())
                .hasViolation())
        .isFalse();
  }

  /// 用别的子域的仓储违规。
  @Test
  void should_reject_foreign_repository() {
    assertViolationNaming(
        "foreignrepository",
        rules("foreignrepository").crossSubdomainDependenciesGoThroughPublishedPackages(),
        "SubjectRepository");
  }

  /// 用别的子域 app 层的类违规。
  @Test
  void should_reject_foreign_app_class() {
    assertViolationNaming(
        "foreignapp",
        rules("foreignapp").crossSubdomainDependenciesGoThroughPublishedPackages(),
        "ResolveSubjectHandler");
  }

  /// 驱动端口只有底座公开：用非底座子域的驱动端口违规。
  @Test
  void should_reject_gateway_of_non_foundation_subdomain() {
    assertViolationNaming(
        "foreigngateway",
        rules("foreigngateway").crossSubdomainDependenciesGoThroughPublishedPackages(),
        "NoteGateway");
  }

  /// 底座依赖别的子域违规，公开包也不行。
  @Test
  void should_reject_foundation_depending_on_other_subdomain() {
    assertViolationNaming(
        "foundationreverse",
        rules("foundationreverse").foundationDependsOnNoOtherSubdomain(),
        "CollectionReadPort");
  }

  /// 合规场景里底座不依赖任何子域。
  @Test
  void should_accept_foundation_free_of_other_subdomains() {
    assertThat(
            evaluate("compliant", rules("compliant").foundationDependsOnNoOtherSubdomain())
                .hasViolation())
        .isFalse();
  }

  /// 两个子域经读端口互相依赖构成环，违规。
  @Test
  void should_reject_cycle_between_subdomains() {
    assertThat(evaluate("cycle", rules("cycle").subdomainsAreFreeOfCycles()).hasViolation())
        .isTrue();
  }

  /// 合规场景的子域之间无环。
  @Test
  void should_accept_acyclic_subdomains() {
    assertThat(evaluate("compliant", rules("compliant").subdomainsAreFreeOfCycles()).hasViolation())
        .isFalse();
  }

  /// 全部规则是四条。
  @Test
  void should_list_all_four_rules() {
    assertThat(rules("compliant").allRules()).hasSize(4);
  }

  /// 断言规则在场景里有违规，且违规文本点名了某个类。
  ///
  /// @param scenario 场景名
  /// @param rule 规则
  /// @param className 违规文本里应出现的类名
  private static void assertViolationNaming(String scenario, ArchRule rule, String className) {
    EvaluationResult result = evaluate(scenario, rule);

    assertThat(result.hasViolation()).isTrue();
    assertThat(result.getFailureReport().getDetails()).anyMatch(line -> line.contains(className));
  }

  /// 以某个场景的根包建规则。
  ///
  /// @param scenario 场景名
  /// @return 规则
  private static SubdomainArchitectureRules rules(String scenario) {
    return new SubdomainArchitectureRules(FIXTURES + scenario, "subject", SUBDOMAINS);
  }

  /// 只导入某个场景的类，执行一条规则。
  ///
  /// @param scenario 场景名
  /// @param rule 规则
  /// @return 评估结果
  private static EvaluationResult evaluate(String scenario, ArchRule rule) {
    JavaClasses classes = new ClassFileImporter().importPackages(FIXTURES + scenario);
    return rule.evaluate(classes);
  }
}
