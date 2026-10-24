package dev.patra.tool.test.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/// 按子域分包的服务的子域边界规则。
///
/// 适用于每层下再按子域分包的服务：`{根包}.{层}.{子域}..`，层固定为 `domain`、`app`、`infra`、
/// `adapter`、`api`。不在这五个层下的类（boot 的启动类与装配）是组装根，不属于任何子域，规则不管。
///
/// 子域之间只经对方的**公开包**交互：
///
/// - 任意子域 X：`{根包}.domain.X.port.read..`、`{根包}.domain.X.model.read..`
/// - 底座子域另加：`port.gateway`、`model.vo`、`model.enums`（都在 `{根包}.domain.{底座}` 下）
///
/// ```java
/// Set<String> subdomains = Set.of("subject", "collection");
/// new SubdomainArchitectureRules("dev.patra.interaction", "subject", subdomains)
///     .allRules()
///     .forEach(rule -> rule.check(classes));
/// ```
public final class SubdomainArchitectureRules {

  /// 按子域分包的五个层。
  private static final List<String> LAYERS = List.of("domain", "app", "infra", "adapter", "api");

  /// 每个子域都公开的包，相对 `{根包}.domain.{子域}`。
  private static final List<String> PUBLISHED = List.of("port.read", "model.read");

  /// 底座子域另外公开的包，相对 `{根包}.domain.{底座}`。
  private static final List<String> FOUNDATION_PUBLISHED =
      List.of("port.gateway", "model.vo", "model.enums");

  /// 服务根包，如 `dev.patra.interaction`。
  private final String basePackage;

  /// 底座子域名，如 `subject`。
  private final String foundationSubdomain;

  /// 声明的全部子域名，含底座。
  private final Set<String> subdomains;

  /// 创建规则集。
  ///
  /// @param basePackage 服务根包，不能为空白
  /// @param foundationSubdomain 底座子域名，必须在 `subdomains` 里
  /// @param subdomains 声明的全部子域名，不能为空
  /// @throws IllegalArgumentException 参数不满足上面的条件时
  public SubdomainArchitectureRules(
      String basePackage, String foundationSubdomain, Set<String> subdomains) {
    this.basePackage = Objects.requireNonNull(basePackage, "basePackage 不能为空");
    this.foundationSubdomain =
        Objects.requireNonNull(foundationSubdomain, "foundationSubdomain 不能为空");
    this.subdomains = Set.copyOf(Objects.requireNonNull(subdomains, "subdomains 不能为空"));
    if (basePackage.isBlank()) {
      throw new IllegalArgumentException("basePackage 不能为空白字符串");
    }
    if (!this.subdomains.contains(foundationSubdomain)) {
      throw new IllegalArgumentException(
          "底座子域 " + foundationSubdomain + " 必须在声明的子域 " + this.subdomains + " 里");
    }
  }

  /// 五个层根包下的类必须落在某个已声明子域的包里。
  ///
  /// 直接放在层根下的类、未声明的子域包（如顺手建的 `domain.common`）都违规。
  ///
  /// @return ArchRule 规则实例
  public ArchRule layersContainOnlyDeclaredSubdomains() {
    return classes()
        .that(resideInLayer())
        .should(resideInDeclaredSubdomain())
        .as("层下面只放已声明的子域 " + subdomains)
        .because("子域是拆分与依赖约束的单位；新增子域要改 SubdomainArchitectureRules 的 subdomains 参数")
        .allowEmptyShould(true);
  }

  /// 子域 A 的类依赖子域 B（B ≠ A）的类时，目标必须在 B 的公开包里。
  ///
  /// 公开包之外的一切（聚合、实体、仓储、领域服务、app / infra / adapter 的任何类）只能在子域内部用。
  ///
  /// @return ArchRule 规则实例
  public ArchRule crossSubdomainDependenciesGoThroughPublishedPackages() {
    return classes()
        .that(resideInSomeSubdomain())
        .should(dependOnOtherSubdomainsOnlyThroughPublishedPackages())
        .as("跨子域只经对方的公开包（port.read、model.read；底座另加 port.gateway、model.vo、model.enums）")
        .because("子域之间不直接用对方的实体或仓储，拆分时只需把公开包换成 HTTP 契约")
        .allowEmptyShould(true);
  }

  /// 底座的类不依赖任何其他子域的类，公开包也不行。
  ///
  /// @return ArchRule 规则实例
  public ArchRule foundationDependsOnNoOtherSubdomain() {
    return noClasses()
        .that(resideInSubdomain(foundationSubdomain))
        .should()
        .dependOnClassesThat(resideInSubdomainOtherThan(foundationSubdomain))
        .as("底座子域 " + foundationSubdomain + " 不依赖任何其他子域")
        .because("底座被所有子域依赖，它再依赖别的子域就成了环，也没法先于其他子域拆出去")
        .allowEmptyShould(true);
  }

  /// 按子域切片（同一子域跨层合成一片），切片之间无环。
  ///
  /// 用自定义切片分配而不是包模式：组装根（如 `{根包}.config`）不属于任何子域，不参与切片。
  ///
  /// @return ArchRule 规则实例
  public ArchRule subdomainsAreFreeOfCycles() {
    return slices()
        .assignedFrom(
            new SliceAssignment() {
              @Override
              public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
                return subdomainOf(javaClass.getPackageName())
                    .map(SliceIdentifier::of)
                    .orElseGet(SliceIdentifier::ignore);
              }

              @Override
              public String getDescription() {
                return basePackage + " 的子域";
              }
            })
        .should()
        .beFreeOfCycles()
        .as("子域之间不允许循环依赖")
        .because("有环的子域无法单独拆分，也说明边界划错了")
        .allowEmptyShould(true);
  }

  /// 全部子域规则。
  ///
  /// @return 不可变的规则列表
  public List<ArchRule> allRules() {
    return List.of(
        layersContainOnlyDeclaredSubdomains(),
        subdomainsAreFreeOfCycles(),
        crossSubdomainDependenciesGoThroughPublishedPackages(),
        foundationDependsOnNoOtherSubdomain());
  }

  /// 类属于指定子域。
  ///
  /// @param subdomain 子域名
  /// @return 谓词
  private DescribedPredicate<JavaClass> resideInSubdomain(String subdomain) {
    return DescribedPredicate.describe(
        "属于子域 " + subdomain,
        javaClass -> subdomainOf(javaClass.getPackageName()).filter(subdomain::equals).isPresent());
  }

  /// 类属于指定子域以外的某个子域。
  ///
  /// @param subdomain 排除的子域名
  /// @return 谓词
  private DescribedPredicate<JavaClass> resideInSubdomainOtherThan(String subdomain) {
    return DescribedPredicate.describe(
        "属于 " + subdomain + " 以外的子域",
        javaClass ->
            subdomainOf(javaClass.getPackageName())
                .filter(other -> !other.equals(subdomain))
                .isPresent());
  }

  /// 类属于某个已声明的子域。
  ///
  /// @return 谓词
  private DescribedPredicate<JavaClass> resideInSomeSubdomain() {
    return DescribedPredicate.describe(
        "属于某个子域", javaClass -> subdomainOf(javaClass.getPackageName()).isPresent());
  }

  /// 依赖别的子域时只依赖它的公开包。
  ///
  /// @return 条件
  private ArchCondition<JavaClass> dependOnOtherSubdomainsOnlyThroughPublishedPackages() {
    return new ArchCondition<>("只经公开包依赖别的子域") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        String source = subdomainOf(javaClass.getPackageName()).orElseThrow();
        for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
          String targetPackage =
              dependency.getTargetClass().getBaseComponentType().getPackageName();
          Optional<String> target = subdomainOf(targetPackage);
          if (target.isPresent()
              && !target.get().equals(source)
              && !isPublished(target.get(), targetPackage)) {
            events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
          }
        }
      }
    };
  }

  /// 包是否在某个子域的公开包里。
  ///
  /// @param subdomain 子域名
  /// @param packageName 包名
  /// @return 在公开包（或其子包）里时为真
  private boolean isPublished(String subdomain, String packageName) {
    String domainPackage = basePackage + ".domain." + subdomain + ".";
    List<String> published =
        subdomain.equals(foundationSubdomain)
            ? Stream.concat(PUBLISHED.stream(), FOUNDATION_PUBLISHED.stream()).toList()
            : PUBLISHED;
    return published.stream()
        .map(relative -> domainPackage + relative)
        .anyMatch(prefix -> packageName.equals(prefix) || packageName.startsWith(prefix + "."));
  }

  /// 类在五个层之一的根包下（含层根包本身）。
  ///
  /// @return 谓词
  private DescribedPredicate<JavaClass> resideInLayer() {
    return DescribedPredicate.describe(
        "在 " + basePackage + " 的五个层下",
        javaClass -> layerOf(javaClass.getPackageName()).isPresent());
  }

  /// 类所在的包是已声明的子域。
  ///
  /// @return 条件
  private ArchCondition<JavaClass> resideInDeclaredSubdomain() {
    return new ArchCondition<>("落在已声明的子域里") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        if (subdomainOf(javaClass.getPackageName()).isEmpty()) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass, javaClass.getName() + " 不在已声明的子域 " + subdomains + " 里"));
        }
      }
    };
  }

  /// 包所属的层：包名是 `{根包}.{层}` 或以 `{根包}.{层}.` 开头时返回该层。
  ///
  /// @param packageName 包名
  /// @return 层名；不在任何层下时为空
  private Optional<String> layerOf(String packageName) {
    return LAYERS.stream()
        .filter(
            layer -> {
              String layerPackage = basePackage + "." + layer;
              return packageName.equals(layerPackage) || packageName.startsWith(layerPackage + ".");
            })
        .findFirst();
  }

  /// 包所属的子域：层根包之后的第一段，且是已声明的子域。
  ///
  /// @param packageName 包名
  /// @return 子域名；不在层下、直接在层根、或不是已声明子域时为空
  private Optional<String> subdomainOf(String packageName) {
    return layerOf(packageName)
        .map(layer -> basePackage + "." + layer + ".")
        .filter(packageName::startsWith)
        .map(prefix -> packageName.substring(prefix.length()).split("\\.", 2)[0])
        .filter(subdomains::contains);
  }
}
