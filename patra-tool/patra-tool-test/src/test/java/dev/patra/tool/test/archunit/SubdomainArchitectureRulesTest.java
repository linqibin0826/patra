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

  /// 服务级错误码的保留包 `api.error` 不属于任何子域，不违规。
  @Test
  void should_accept_reserved_api_error_package() {
    assertThat(
            evaluate("reservederror", rules("reservederror").layersContainOnlyDeclaredSubdomains())
                .hasViolation())
        .isFalse();
  }

  /// 子域不能叫 `error`，避免和保留包冲突。
  @Test
  void should_reject_error_as_subdomain_name() {
    assertThatThrownBy(() -> new SubdomainArchitectureRules("x", "error", Set.of("error")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /// 落在分包模板白名单里的包都不违规。
  @Test
  void should_accept_packages_on_template() {
    assertThat(
            evaluate(
                    "templatecompliant",
                    rules("templatecompliant").componentPackagesFollowTemplate())
                .hasViolation())
        .isFalse();
  }

  /// 白名单之外的包、子域根包里的类、多出来的层级都违规。
  @Test
  void should_reject_packages_off_template() {
    EvaluationResult result =
        evaluate("offtemplate", rules("offtemplate").componentPackagesFollowTemplate());

    assertThat(result.hasViolation()).isTrue();
    assertThat(result.getFailureReport().getDetails())
        .anyMatch(line -> line.contains("CollectionDto"))
        .anyMatch(line -> line.contains("Snapshot"))
        .anyMatch(line -> line.contains("WebController"))
        .anyMatch(line -> line.contains("RepoHelper"))
        .anyMatch(line -> line.contains("RootLevel"))
        .anyMatch(line -> line.contains("NestedPort"))
        .anyMatch(line -> line.contains("UsecaseRoot"));
  }

  /// infra 下的协作者包必须有同名的 domain 端口包；persistence、support、config 不受此限。
  @Test
  void should_reject_infra_package_without_matching_port() {
    EvaluationResult result = evaluate("infraports", rules("infraports").infraPackagesMatchPorts());

    assertThat(result.hasViolation()).isTrue();
    assertThat(result.getFailureReport().getDetails())
        .anyMatch(line -> line.contains("SmtpAdapter"))
        .anyMatch(line -> line.contains("CollectionReadAdapter"))
        .anyMatch(line -> line.contains("CollectionGatewayImpl"))
        .noneMatch(line -> line.contains("MailAdapter"))
        .noneMatch(line -> line.contains("SqlFragments"))
        .noneMatch(line -> line.contains("CollectionRepositoryAdapter"))
        .noneMatch(line -> line.contains("InfraConfig"));
  }

  /// 声明了却在 domain 层没有任何类的子域违规（子域名拼错或声明了空子域）。
  @Test
  void should_reject_declared_subdomain_without_domain_classes() {
    SubdomainArchitectureRules rules =
        new SubdomainArchitectureRules(FIXTURES + "ghost", "subject", Set.of("subject", "ghostly"));

    assertViolationNaming("ghost", rules.everyDeclaredSubdomainHasDomainPackage(), "ghostly");
  }

  /// 不在五个层下的类只能在根包或 `{根}.config` 下：启动类与组装之外不另起包。
  @Test
  void should_reject_package_outside_layers_other_than_root_and_config() {
    EvaluationResult result =
        evaluate("rootescape", rules("rootescape").nonLayerClassesStayInRootOrConfig());

    assertThat(result.hasViolation()).isTrue();
    assertThat(result.getFailureReport().getDetails())
        .anyMatch(line -> line.contains("LooseHelper"))
        .noneMatch(line -> line.contains("RootApplication"))
        .noneMatch(line -> line.contains("Wiring"));
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

  /// 用别的普通子域的仓储违规。
  @Test
  void should_reject_foreign_repository() {
    assertViolationNaming(
        "foreignrepository",
        rules("foreignrepository").crossSubdomainDependenciesGoThroughPublishedPackages(),
        "NoteRepository");
  }

  /// 底座对其他子域完全公开：仓储、app 协作类、infra 辅助类都可以用。
  @Test
  void should_allow_any_foundation_package() {
    assertThat(
            evaluate(
                    "foundationinternals",
                    rules("foundationinternals")
                        .crossSubdomainDependenciesGoThroughPublishedPackages())
                .hasViolation())
        .isFalse();
  }

  /// 用别的普通子域 app 层的类违规。
  @Test
  void should_reject_foreign_app_class() {
    assertViolationNaming(
        "foreignapp",
        rules("foreignapp").crossSubdomainDependenciesGoThroughPublishedPackages(),
        "ResolveNoteHandler");
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

  /// 任意子域的事件契约包都是公开的。
  @Test
  void should_publish_event_contract_of_any_subdomain() {
    assertThat(
            evaluate(
                    "events",
                    rules("events").crossSubdomainDependenciesGoThroughPublishedPackages())
                .hasViolation())
        .isFalse();
  }

  /// 声明了订阅边，就可以依赖对方的事件契约。
  @Test
  void should_accept_declared_subscription() {
    SubdomainArchitectureRules rules = rules("events").allowingSubscription("note", "collection");

    assertThat(evaluate("events", rules.crossSubdomainDependenciesAreDeclared()).hasViolation())
        .isFalse();
  }

  /// 没声明的边，哪怕只是事件契约也违规。
  @Test
  void should_reject_undeclared_subscription() {
    assertViolationNaming(
        "events", rules("events").crossSubdomainDependenciesAreDeclared(), "ItemCollectedEvent");
  }

  /// 订阅边只放行事件契约，读端口不行。
  @Test
  void should_reject_read_port_across_subscription_edge() {
    assertViolationNaming(
        "notereads",
        rules("notereads")
            .allowingSubscription("note", "collection")
            .crossSubdomainDependenciesAreDeclared(),
        "CollectionReadPort");
  }

  /// 声明了依赖边，就可以用对方的全部公开包。
  @Test
  void should_accept_declared_dependency() {
    SubdomainArchitectureRules rules = rules("notereads").allowingDependency("note", "collection");

    assertThat(evaluate("notereads", rules.crossSubdomainDependenciesAreDeclared()).hasViolation())
        .isFalse();
  }

  /// 没声明依赖边就用对方的读端口，违规。
  @Test
  void should_reject_undeclared_dependency() {
    assertViolationNaming(
        "notereads",
        rules("notereads").crossSubdomainDependenciesAreDeclared(),
        "CollectionReadPort");
  }

  /// 底座公开名片端口、名片和领域异常。
  @Test
  void should_publish_foundation_user_directory_and_exceptions() {
    assertThat(
            evaluate(
                    "foundationextras",
                    rules("foundationextras")
                        .crossSubdomainDependenciesGoThroughPublishedPackages())
                .hasViolation())
        .isFalse();
  }

  /// 只依赖底座的子域不用声明任何边。
  @Test
  void should_accept_compliant_without_declarations() {
    assertThat(
            evaluate("compliant", rules("compliant").crossSubdomainDependenciesAreDeclared())
                .hasViolation())
        .isFalse();
  }

  /// 未知子域、自环、从底座出发、指向底座的边都拒绝声明。
  @Test
  void should_reject_invalid_edges() {
    SubdomainArchitectureRules rules = rules("compliant");

    assertThatThrownBy(() -> rules.allowingDependency("unknown", "note"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> rules.allowingSubscription("note", "note"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> rules.allowingDependency("subject", "note"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> rules.allowingSubscription("note", "subject"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /// 全部规则是九条。
  @Test
  void should_list_all_nine_rules() {
    assertThat(rules("compliant").allRules()).hasSize(9);
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
