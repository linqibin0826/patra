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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// 按子域分包的服务的子域边界规则。
///
/// 适用于每层下再按子域分包的服务：`{根包}.{层}.{子域}..`，层固定为 `domain`、`app`、`infra`、
/// `adapter`、`api`。不在这五个层下的类（boot 的启动类与装配）是组装根，不属于任何子域，规则不管。
///
/// 子域之间只经对方的**公开包**交互：
///
/// - 任意子域 X：`{根包}.domain.X.port.read..`、`{根包}.domain.X.model.read..`，以及事件契约
///   `{根包}.domain.X.event..`（X 发布的进程内事件）
/// - 底座子域（共享内核）对同服务的其他子域完全公开：所有层、所有包都可以用；底座只放被两个以上子域共用的东西
///
/// 依赖方向也要声明：任何子域都可以依赖底座；其余的边用 {@link #allowingDependency} 或
/// {@link #allowingSubscription} 声明，没声明的边违规（{@link #crossSubdomainDependenciesAreDeclared}）。
///
/// ```java
/// Set<String> subdomains = Set.of("subject", "collection", "discussion", "notification");
/// new SubdomainArchitectureRules("dev.patra.interaction", "subject", subdomains)
///     .allowingSubscription("notification", "discussion")
///     .allRules()
///     .forEach(rule -> rule.check(classes));
/// ```
public final class SubdomainArchitectureRules {

  /// 按子域分包的五个层。
  private static final List<String> LAYERS = List.of("domain", "app", "infra", "adapter", "api");

  /// 每个子域都公开的包，相对 `{根包}.domain.{子域}`：读端口、读模型、事件契约。
  private static final List<String> PUBLISHED = List.of("port.read", "model.read", "event");

  /// 服务级错误码的保留包，相对根包；不属于任何子域，所有子域都可以依赖。
  private static final String RESERVED_ERROR_PACKAGE = "api.error";

  /// 保留包占用的名字，不能用作子域名。
  private static final String RESERVED_SUBDOMAIN_NAME = "error";

  /// 事件契约包，相对 `{根包}.domain.{子域}`。订阅边只放行它。
  private static final String EVENT_CONTRACT = "event";

  /// 服务根包，如 `dev.patra.interaction`。
  private final String basePackage;

  /// 底座子域名，如 `subject`。
  private final String foundationSubdomain;

  /// 声明的全部子域名，含底座。
  private final Set<String> subdomains;

  /// 依赖边：键子域可以经值里各子域的全部公开包依赖它们。
  private final Map<String, Set<String>> dependencies;

  /// 订阅边：键子域只能经值里各子域的事件契约依赖它们。
  private final Map<String, Set<String>> subscriptions;

  /// 创建规则集，不带任何依赖边（各子域只能依赖底座）。
  ///
  /// @param basePackage 服务根包，不能为空白
  /// @param foundationSubdomain 底座子域名，必须在 `subdomains` 里
  /// @param subdomains 声明的全部子域名，不能为空
  /// @throws IllegalArgumentException 参数不满足上面的条件时
  public SubdomainArchitectureRules(
      String basePackage, String foundationSubdomain, Set<String> subdomains) {
    this(basePackage, foundationSubdomain, subdomains, Map.of(), Map.of());
  }

  /// 创建规则集并带上已声明的边。
  ///
  /// @param basePackage 服务根包
  /// @param foundationSubdomain 底座子域名
  /// @param subdomains 声明的全部子域名
  /// @param dependencies 依赖边
  /// @param subscriptions 订阅边
  /// @throws IllegalArgumentException 参数不满足公开构造器的条件时
  private SubdomainArchitectureRules(
      String basePackage,
      String foundationSubdomain,
      Set<String> subdomains,
      Map<String, Set<String>> dependencies,
      Map<String, Set<String>> subscriptions) {
    this.basePackage = Objects.requireNonNull(basePackage, "basePackage 不能为空");
    this.foundationSubdomain =
        Objects.requireNonNull(foundationSubdomain, "foundationSubdomain 不能为空");
    this.subdomains = Set.copyOf(Objects.requireNonNull(subdomains, "subdomains 不能为空"));
    this.dependencies = Map.copyOf(dependencies);
    this.subscriptions = Map.copyOf(subscriptions);
    if (basePackage.isBlank()) {
      throw new IllegalArgumentException("basePackage 不能为空白字符串");
    }
    if (this.subdomains.contains(RESERVED_SUBDOMAIN_NAME)) {
      throw new IllegalArgumentException(
          "子域不能叫 " + RESERVED_SUBDOMAIN_NAME + "：" + RESERVED_ERROR_PACKAGE + " 是保留包");
    }
    if (!this.subdomains.contains(foundationSubdomain)) {
      throw new IllegalArgumentException(
          "底座子域 " + foundationSubdomain + " 必须在声明的子域 " + this.subdomains + " 里");
    }
  }

  /// 声明子域 `from` 可以经 `to` 的全部公开包依赖它。
  ///
  /// @param from 依赖方
  /// @param to 被依赖方
  /// @return 多了这条边的新规则集；原规则集不变
  /// @throws IllegalArgumentException 名字未声明、`from == to`、`from` 或 `to` 是底座
  public SubdomainArchitectureRules allowingDependency(String from, String to) {
    requireEdge(from, to);
    return new SubdomainArchitectureRules(
        basePackage,
        foundationSubdomain,
        subdomains,
        withEdge(dependencies, from, to),
        subscriptions);
  }

  /// 声明子域 `from` 只能经 `to` 的事件契约（`{根包}.domain.{to}.event..`）依赖它，即订阅它发布的事件。
  ///
  /// @param from 订阅方
  /// @param to 发布方
  /// @return 多了这条边的新规则集；原规则集不变
  /// @throws IllegalArgumentException 名字未声明、`from == to`、`from` 或 `to` 是底座
  public SubdomainArchitectureRules allowingSubscription(String from, String to) {
    requireEdge(from, to);
    return new SubdomainArchitectureRules(
        basePackage,
        foundationSubdomain,
        subdomains,
        dependencies,
        withEdge(subscriptions, from, to));
  }

  /// 五个层根包下的类必须落在某个已声明子域的包里。
  ///
  /// 直接放在层根下的类、未声明的子域包（如顺手建的 `domain.common`）都违规；服务级错误码的保留包
  /// `api.error` 例外。
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
  /// 公开包之外的一切（聚合、实体、仓储、领域服务、app / infra / adapter 的任何类）只能在子域内部用；底座除外，它对其他子域完全公开。
  ///
  /// @return ArchRule 规则实例
  public ArchRule crossSubdomainDependenciesGoThroughPublishedPackages() {
    return classes()
        .that(resideInSomeSubdomain())
        .should(dependOnOtherSubdomainsOnlyThroughPublishedPackages())
        .as("跨子域只经对方的公开包（port.read、model.read、event）；底座完全公开")
        .because("子域之间不直接用对方的实体或仓储，拆分时只需把公开包换成 HTTP 契约")
        .allowEmptyShould(true);
  }

  /// 子域 A 的类依赖子域 B（B ≠ A）的类时，这条边必须声明过：B 是底座；或声明了 A → B 的依赖边；
  /// 或声明了 A → B 的订阅边，且目标在 B 的事件契约里。
  ///
  /// 目标是否在公开包里由 {@link #crossSubdomainDependenciesGoThroughPublishedPackages} 管，这里只管方向。
  ///
  /// @return ArchRule 规则实例
  public ArchRule crossSubdomainDependenciesAreDeclared() {
    return classes()
        .that(resideInSomeSubdomain())
        .should(dependOnOtherSubdomainsOnlyAlongDeclaredEdges())
        .as("跨子域依赖必须声明：都可以依赖底座；其余要声明依赖边，或声明只经对方事件契约的订阅边")
        .because("依赖方向是设计决定：对方恰好公开了读端口，不等于可以随手多一条子域依赖")
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

  /// 子域下的包必须落在分包模板的白名单里（模板见 `.claude/rules/tech/package-layout.md`）。
  ///
  /// 子域根包本身不放类（`package-info` 除外）；`usecase.<用例族>`、`port.<协作者>`、infra 的协作者包名字自定，
  /// infra 协作者包是否有同名端口由 {@link #infraPackagesMatchPorts} 管。
  ///
  /// @return ArchRule 规则实例
  public ArchRule componentPackagesFollowTemplate() {
    return classes()
        .that(resideInSomeSubdomain())
        .should(followTemplate())
        .as("子域下的包落在分包模板的白名单里")
        .because("分包模板见 .claude/rules/tech/package-layout.md")
        .allowEmptyShould(true);
  }

  /// `infra.{子域}.X`（X 不是 `persistence`、`support`、`config`）必须有同名的 `domain.{子域}.port.X`。
  ///
  /// infra 的协作者包按它实现的端口命名，看包名就能找到端口。`read`、`repository`、`gateway` 不能作 infra 包名：
  /// 读端口与仓储的实现放 `persistence`，Gateway 的实现在 app 层。
  ///
  /// @return ArchRule 规则实例
  public ArchRule infraPackagesMatchPorts() {
    return classes()
        .that(resideInSomeSubdomain())
        .should(implementNamesakePort())
        .as("infra 的协作者包与本子域 domain 的端口包同名")
        .because("实现谁就叫谁：infra.{子域}.X 对应 domain.{子域}.port.X")
        .allowEmptyShould(true);
  }

  /// 每个已声明的子域在 domain 层至少有一个类。
  ///
  /// 防止子域名拼错，或声明了还没有代码的空子域。
  ///
  /// @return ArchRule 规则实例
  public ArchRule everyDeclaredSubdomainHasDomainPackage() {
    return classes()
        .that(resideInLayer())
        .should(coverEveryDeclaredSubdomainInDomain())
        .as("每个已声明的子域在 domain 层都有类")
        .because("子域清单要与代码一致：拼错的名字、空子域都会让其他规则静默失效")
        .allowEmptyShould(true);
  }

  /// 不在五个层下的类只能在根包（启动类）或 `{根}.config`（组装）下。
  ///
  /// 防止在层外另起 `{根}.util`、`{根}.common` 之类绕开子域规则的包。
  ///
  /// @return ArchRule 规则实例
  public ArchRule nonLayerClassesStayInRootOrConfig() {
    String config = basePackage + ".config";
    return classes()
        .that(
            DescribedPredicate.describe(
                "在 " + basePackage + " 下、但不在五个层下",
                javaClass -> {
                  String name = javaClass.getPackageName();
                  return (name.equals(basePackage) || name.startsWith(basePackage + "."))
                      && layerOf(name).isEmpty();
                }))
        .should()
        .resideInAnyPackage(basePackage, config, config + "..")
        .as("层外的类只放根包（启动类）或 " + config + "（组装）")
        .because("层外再起包会绕开子域规则；新代码按分包模板放进某个层的子域")
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
        crossSubdomainDependenciesAreDeclared(),
        foundationDependsOnNoOtherSubdomain(),
        componentPackagesFollowTemplate(),
        infraPackagesMatchPorts(),
        everyDeclaredSubdomainHasDomainPackage(),
        nonLayerClassesStayInRootOrConfig());
  }

  /// 类所在的包在分包模板的白名单里。
  ///
  /// @return 条件
  private ArchCondition<JavaClass> followTemplate() {
    return new ArchCondition<>("落在分包模板的白名单里") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        if (javaClass.getSimpleName().equals("package-info")) {
          return;
        }
        String packageName = javaClass.getPackageName();
        String layer = layerOf(packageName).orElseThrow();
        String subdomainPackage =
            basePackage + "." + layer + "." + subdomainOf(packageName).orElseThrow();
        List<String> segments =
            packageName.equals(subdomainPackage)
                ? List.of()
                : List.of(packageName.substring(subdomainPackage.length() + 1).split("\\."));
        if (!onTemplate(layer, segments)) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  javaClass.getName() + " 的包 " + String.join(".", segments) + " 不在分包模板里"));
        }
      }
    };
  }

  /// infra 协作者包有同名的 domain 端口包。
  ///
  /// @return 条件
  private ArchCondition<JavaClass> implementNamesakePort() {
    return new ArchCondition<>("有同名的 domain 端口包") {
      private final Set<String> portPackages = new HashSet<>();

      @Override
      public void init(Collection<JavaClass> allObjectsToTest) {
        allObjectsToTest.stream()
            .map(JavaClass::getPackageName)
            .filter(name -> layerOf(name).filter("domain"::equals).isPresent())
            .forEach(portPackages::add);
      }

      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        String packageName = javaClass.getPackageName();
        if (layerOf(packageName).filter("infra"::equals).isEmpty()) {
          return;
        }
        String subdomain = subdomainOf(packageName).orElseThrow();
        String infraSubdomain = basePackage + ".infra." + subdomain;
        if (packageName.equals(infraSubdomain)) {
          return;
        }
        String collaborator = packageName.substring(infraSubdomain.length() + 1).split("\\.", 2)[0];
        if (Set.of("persistence", "support", "config").contains(collaborator)) {
          return;
        }
        if (Set.of("read", "repository", "gateway").contains(collaborator)) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  javaClass.getName()
                      + " 放在 infra."
                      + subdomain
                      + "."
                      + collaborator
                      + "：读端口与仓储的实现放 persistence，Gateway 的实现放 app."
                      + subdomain
                      + ".service"));
          return;
        }
        String port = basePackage + ".domain." + subdomain + ".port." + collaborator;
        boolean exists =
            portPackages.stream()
                .anyMatch(name -> name.equals(port) || name.startsWith(port + "."));
        if (!exists) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass, javaClass.getName() + " 所在的 infra 包没有同名端口包 " + port));
        }
      }
    };
  }

  /// 导入的 domain 层类覆盖了全部已声明的子域。
  ///
  /// @return 条件
  private ArchCondition<JavaClass> coverEveryDeclaredSubdomainInDomain() {
    return new ArchCondition<>("覆盖全部已声明的子域") {
      private final Set<String> present = new HashSet<>();

      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        String packageName = javaClass.getPackageName();
        if (layerOf(packageName).filter("domain"::equals).isPresent()) {
          subdomainOf(packageName).ifPresent(present::add);
        }
      }

      @Override
      public void finish(ConditionEvents events) {
        subdomains.stream()
            .filter(subdomain -> !present.contains(subdomain))
            .sorted()
            .forEach(
                subdomain ->
                    events.add(
                        SimpleConditionEvent.violated(
                            subdomain, "已声明的子域 " + subdomain + " 在 domain 层没有任何类")));
      }
    };
  }

  /// 子域下的相对包是否在某层的白名单里。
  ///
  /// @param layer 层名
  /// @param segments 子域包之后的各段，空表示子域根包
  /// @return 在白名单里时为真
  private static boolean onTemplate(String layer, List<String> segments) {
    if (segments.isEmpty()) {
      return false;
    }
    String first = segments.getFirst();
    int depth = segments.size();
    return switch (layer) {
      case "domain" ->
          switch (first) {
            case "model" ->
                depth >= 2
                    && (segments.get(1).equals("vo")
                        || depth == 2
                            && Set.of("aggregate", "entity", "enums", "read")
                                .contains(segments.get(1)));
            case "port" -> depth == 2;
            case "policy", "service", "event", "exception" -> depth == 1;
            default -> false;
          };
      case "app" ->
          switch (first) {
            case "usecase" -> depth == 2;
            case "service", "listener", "config" -> depth == 1;
            default -> false;
          };
      case "infra" ->
          switch (first) {
            case "persistence" ->
                depth == 1
                    || depth == 2
                        && Set.of("entity", "dao", "mapper", "converter").contains(segments.get(1));
            case "support", "config" -> depth == 1;
            default -> true;
          };
      case "adapter" ->
          switch (first) {
            case "rest" ->
                depth == 1
                    || depth == 2
                        && Set.of("request", "response", "converter").contains(segments.get(1));
            case "internal", "scheduler", "mq", "config" -> depth == 1;
            default -> false;
          };
      case "api" -> depth == 1 && Set.of("endpoint", "dto").contains(first);
      default -> false;
    };
  }

  /// 校验一条要声明的边。
  ///
  /// @param from 依赖方
  /// @param to 被依赖方
  /// @throws IllegalArgumentException 名字未声明、自环、从底座出发或指向底座
  private void requireEdge(String from, String to) {
    if (!subdomains.contains(from) || !subdomains.contains(to)) {
      throw new IllegalArgumentException("边 " + from + " → " + to + " 里有未声明的子域，已声明 " + subdomains);
    }
    if (from.equals(to)) {
      throw new IllegalArgumentException("子域不用声明依赖自己：" + from);
    }
    if (from.equals(foundationSubdomain)) {
      throw new IllegalArgumentException("底座 " + from + " 不依赖任何子域");
    }
    if (to.equals(foundationSubdomain)) {
      throw new IllegalArgumentException("依赖底座 " + to + " 无需声明");
    }
  }

  /// 在边表里加一条边，返回新表。
  ///
  /// @param edges 原边表
  /// @param from 起点
  /// @param to 终点
  /// @return 新边表
  private static Map<String, Set<String>> withEdge(
      Map<String, Set<String>> edges, String from, String to) {
    Map<String, Set<String>> copy = new HashMap<>(edges);
    Set<String> targets = new HashSet<>(copy.getOrDefault(from, Set.of()));
    targets.add(to);
    copy.put(from, Set.copyOf(targets));
    return copy;
  }

  /// 依赖别的子域时只沿声明过的边。
  ///
  /// @return 条件
  private ArchCondition<JavaClass> dependOnOtherSubdomainsOnlyAlongDeclaredEdges() {
    return new ArchCondition<>("只沿声明过的边依赖别的子域") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        String source = subdomainOf(javaClass.getPackageName()).orElseThrow();
        for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
          String targetPackage =
              dependency.getTargetClass().getBaseComponentType().getPackageName();
          Optional<String> target = subdomainOf(targetPackage);
          if (target.isPresent()
              && !target.get().equals(source)
              && !isDeclared(source, target.get(), targetPackage)) {
            events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
          }
        }
      }
    };
  }

  /// 从 `source` 到 `target` 子域里这个包的依赖是否声明过。
  ///
  /// @param source 依赖方子域
  /// @param target 被依赖方子域
  /// @param targetPackage 被依赖的类所在的包
  /// @return 声明过时为真
  private boolean isDeclared(String source, String target, String targetPackage) {
    if (target.equals(foundationSubdomain)
        || dependencies.getOrDefault(source, Set.of()).contains(target)) {
      return true;
    }
    String eventContract = basePackage + ".domain." + target + "." + EVENT_CONTRACT;
    return subscriptions.getOrDefault(source, Set.of()).contains(target)
        && (targetPackage.equals(eventContract) || targetPackage.startsWith(eventContract + "."));
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

  /// 包是否在某个子域的公开包里；底座的任何包都算公开。
  ///
  /// @param subdomain 子域名
  /// @param packageName 包名
  /// @return 在公开包（或其子包）里、或属于底座时为真
  private boolean isPublished(String subdomain, String packageName) {
    if (subdomain.equals(foundationSubdomain)) {
      return true;
    }
    String domainPackage = basePackage + ".domain." + subdomain + ".";
    return PUBLISHED.stream()
        .map(relative -> domainPackage + relative)
        .anyMatch(prefix -> packageName.equals(prefix) || packageName.startsWith(prefix + "."));
  }

  /// 类在五个层之一的根包下（含层根包本身），保留包 `api.error` 除外。
  ///
  /// @return 谓词
  private DescribedPredicate<JavaClass> resideInLayer() {
    return DescribedPredicate.describe(
        "在 " + basePackage + " 的五个层下",
        javaClass ->
            layerOf(javaClass.getPackageName()).isPresent()
                && !isReserved(javaClass.getPackageName()));
  }

  /// 包是否在保留包 `{根包}.api.error`（或其子包）里。
  ///
  /// @param packageName 包名
  /// @return 在保留包里时为真
  private boolean isReserved(String packageName) {
    String reserved = basePackage + "." + RESERVED_ERROR_PACKAGE;
    return packageName.equals(reserved) || packageName.startsWith(reserved + ".");
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
