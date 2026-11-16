package dev.patra.tool.test.archunit;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.PackageMatcher;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/// 写侧技术规则：每个服务声明自己用 JPA 还是原生 SQL 写库，规则按声明检查（`.claude/rules/tech/database.md`
/// 第 8 节）。
///
/// 两种技术混在同一个事务里才会出问题：JPA 把改动攒在一级缓存里、提交前才落库，同事务里的原生 SQL 读不到；原生 SQL
/// 改了库，一级缓存里的实体也不知道。所以 JPA 服务的写入只走实体或 Dao 上带两个属性的 `@Query`，原生 SQL 服务里不出现
/// JPA。读适配器（实现了 `port.read` 端口的类）只读不写，两类服务都不限读法。
///
/// 各服务的 `*ArchitectureTest` 按自己声明的技术引用一组：
///
/// ```java
/// PersistenceTechnologyRules.jpaService("dev.patra.catalog", Set.of(锁适配器的全类名))
/// PersistenceTechnologyRules.sqlService("dev.patra.interaction")
/// ```
///
/// 注解和类型都按全类名匹配，规则类不依赖 JPA 工具模块。
public final class PersistenceTechnologyRules {

  private static final Set<String> JDBC_TYPES =
      Set.of(
          "org.springframework.jdbc.core.simple.JdbcClient",
          "org.springframework.jdbc.core.JdbcTemplate",
          "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate");

  private static final String JDBC_CLIENT = "org.springframework.jdbc.core.simple.JdbcClient";
  private static final String MODIFYING = "org.springframework.data.jpa.repository.Modifying";
  private static final String QUERY = "org.springframework.data.jpa.repository.Query";
  private static final String ENTITY = "jakarta.persistence.Entity";
  private static final String BASE_ENTITY = "dev.patra.tool.jpa.entity.BaseJpaEntity";
  private static final String VALUE_OBJECT_ENTITY =
      "dev.patra.tool.jpa.entity.ValueObjectJpaEntity";

  /// 读适配器：实现了 `..port.read..` 包里的接口。
  private static final DescribedPredicate<JavaClass> READ_ADAPTER =
      describe(
          "读适配器（实现 port.read 端口）",
          javaClass ->
              javaClass.getAllRawInterfaces().stream()
                  .anyMatch(
                      port -> PackageMatcher.of("..port.read..").matches(port.getPackageName())));

  private PersistenceTechnologyRules() {}

  /// JPA 服务（registry、catalog、identity）的规则。
  ///
  /// @param basePackage 服务根包，如 `dev.patra.catalog`
  /// @param jdbcExemptClasses 允许用 JDBC 但不改数据的类的全类名，如只执行 advisory lock 的适配器
  /// @return 规则列表
  public static List<ArchRule> jpaService(String basePackage, Set<String> jdbcExemptClasses) {
    DescribedPredicate<JavaClass> exempt =
        describe("登记豁免的类", javaClass -> jdbcExemptClasses.contains(javaClass.getFullName()));
    return List.of(
        noClasses()
            .that()
            .resideInAPackage(basePackage + ".infra..")
            .and(not(READ_ADAPTER))
            .and(not(exempt))
            .should()
            .dependOnClassesThat(
                describe("是 JdbcClient / JdbcTemplate", c -> JDBC_TYPES.contains(c.getFullName())))
            .as("JPA 服务的写侧不用 JdbcClient / JdbcTemplate")
            .because("和 JPA 混在一个事务里会读到还没落库的旧数据（database.md §8）")
            .allowEmptyShould(true),
        methods()
            .that()
            .areAnnotatedWith(MODIFYING)
            .and()
            .areDeclaredInClassesThat()
            .resideInAPackage(basePackage + "..")
            .should(beModifyingQueryWithFlushAndClear())
            .as("@Modifying 只用在 @Query 上，并带 flushAutomatically 与 clearAutomatically")
            .because("批量语句不经一级缓存：执行前要把攒着的改动落库，执行后要清掉过时的实体（database.md §8）")
            .allowEmptyShould(true),
        noClasses()
            .that()
            .resideInAPackage(basePackage + "..")
            .should()
            .callMethodWhere(callTo("jakarta.persistence.Query", "executeUpdate"))
            .as("不经实体的写入只能是 Dao 上的 @Query，不在代码里 executeUpdate")
            .because("写入集中在 Dao 上，由 @Modifying 的两个属性管住一级缓存（database.md §8）")
            .allowEmptyShould(true),
        noClasses()
            .that()
            .resideInAPackage(basePackage + "..")
            .and(not(READ_ADAPTER))
            .should()
            .callMethodWhere(callTo("jakarta.persistence.EntityManager", "createNativeQuery"))
            .as("读适配器以外不在代码里拼原生查询，放到 Dao 的 @Query 上")
            .because("原生语句绕过一级缓存，集中在 Dao 上才看得见、管得住（database.md §8）")
            .allowEmptyShould(true),
        classes()
            .that()
            .areAnnotatedWith(ENTITY)
            .and()
            .resideInAPackage(basePackage + "..")
            .should()
            .beAssignableTo(
                assignableTo(BASE_ENTITY)
                    .or(assignableTo(VALUE_OBJECT_ENTITY))
                    .as("BaseJpaEntity 或 ValueObjectJpaEntity"))
            .as("JPA 实体继承 BaseJpaEntity（主表）或 ValueObjectJpaEntity（其他表）")
            .because("两个基类按表的档位提供必备列的映射（database.md §3）")
            .allowEmptyShould(true));
  }

  /// 原生 SQL 服务（interaction、notification、analytics）的规则。
  ///
  /// @param basePackage 服务根包，如 `dev.patra.interaction`
  /// @return 规则列表
  public static List<ArchRule> sqlService(String basePackage) {
    return List.of(
        noClasses()
            .that()
            .resideInAPackage(basePackage + "..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("jakarta.persistence..")
            .as("原生 SQL 服务里不出现 JPA")
            .because("写侧技术按服务声明，这类服务用 JdbcClient 写库（database.md §8）")
            .allowEmptyShould(true),
        noClasses()
            .that()
            .resideInAPackage(basePackage + "..")
            .and()
            .resideOutsideOfPackage(basePackage + ".infra..persistence..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName(JDBC_CLIENT)
            .as("JdbcClient 只出现在 infra 的 persistence 包")
            .because("读写库的代码集中在仓储和读适配器里（database.md §8）")
            .allowEmptyShould(true));
  }

  /// 调用某个类型（含子类型）上的某个方法。
  private static DescribedPredicate<JavaMethodCall> callTo(String ownerType, String methodName) {
    return describe(
        "调用 " + ownerType + "#" + methodName,
        call ->
            call.getName().equals(methodName) && call.getTargetOwner().isAssignableTo(ownerType));
  }

  /// `@Modifying` 必须和 `@Query` 一起出现，且两个属性都为 `true`。
  private static ArchCondition<JavaMethod> beModifyingQueryWithFlushAndClear() {
    return new ArchCondition<>("带 @Query，且 flushAutomatically、clearAutomatically 都为 true") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        boolean hasQuery = method.isAnnotatedWith(QUERY);
        JavaAnnotation<JavaMethod> modifying = method.getAnnotationOfType(MODIFYING);
        boolean flush = isTrue(modifying.get("flushAutomatically"));
        boolean clear = isTrue(modifying.get("clearAutomatically"));
        boolean satisfied = hasQuery && flush && clear;
        String detail =
            method.getFullName()
                + (hasQuery ? "" : " 没有 @Query")
                + (flush ? "" : " 缺 flushAutomatically = true")
                + (clear ? "" : " 缺 clearAutomatically = true");
        events.add(new SimpleConditionEvent(method, satisfied, detail));
      }
    };
  }

  private static boolean isTrue(Optional<Object> value) {
    return value.map(Boolean.TRUE::equals).orElse(false);
  }
}
