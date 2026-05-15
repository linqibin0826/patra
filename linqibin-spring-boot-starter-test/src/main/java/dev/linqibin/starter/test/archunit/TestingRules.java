package dev.linqibin.starter.test.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

/// 测试规范规则。
///
/// 验证测试代码的架构约束和最佳实践，这些规则是通用的，不需要参数化。
///
/// ### 包含的规则
///
/// - 测试类命名规范（*Test / *IT / *E2E）
/// - Domain 层测试无框架依赖
/// - 测试方法必须有 @Test 注解
/// - Repository 集成测试在 infra 模块
/// - 测试独立性（测试不依赖测试）
/// - E2E 测试在 boot 模块
///
/// ### 使用方式
///
/// ```java
/// class TestArchitectureTest {
///
///     private static final JavaClasses classes = new ClassFileImporter()
///         .importPackages("com.patra");
///
///     @ArchTest
///     static final ArchRule testNaming = TestingRules.testClassesShouldFollowNamingConvention();
///
///     @ArchTest
///     static final ArchRule domainTestPurity = TestingRules.domainTestsShouldNotDependOnSpring();
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
public final class TestingRules {

  private TestingRules() {
    // 工具类，禁止实例化
  }

  /// 测试类命名必须符合规范（*Test / *IT / *E2E）。
  ///
  /// 例外：测试工具类（*Builder、*Support、*Helper、*Collector、*Initializer）不受此规则约束。
  ///
  /// @return ArchRule 规则实例
  public static ArchRule testClassesShouldFollowNamingConvention() {
    return classes()
        .that()
        .resideInAPackage("..test..")
        .and()
        .areNotInterfaces()
        .and()
        .areNotEnums()
        .and(
            new DescribedPredicate<JavaClass>("不是 @Nested 内部类") {
              @Override
              public boolean test(JavaClass javaClass) {
                return !javaClass.isAnnotatedWith("org.junit.jupiter.api.Nested");
              }
            })
        .and(
            new DescribedPredicate<JavaClass>("不是测试工具类") {
              @Override
              public boolean test(JavaClass javaClass) {
                String simpleName = javaClass.getSimpleName();
                return !simpleName.endsWith("Builder")
                    && !simpleName.endsWith("Support")
                    && !simpleName.endsWith("Helper")
                    && !simpleName.endsWith("Collector")
                    && !simpleName.endsWith("Initializer");
              }
            })
        .should(
            new ArchCondition<JavaClass>("命名以 Test/IT/E2E 结尾") {
              @Override
              public void check(JavaClass javaClass, ConditionEvents events) {
                String simpleName = javaClass.getSimpleName();
                boolean isValid =
                    simpleName.endsWith("Test")
                        || simpleName.endsWith("IT")
                        || simpleName.endsWith("E2E");
                if (!isValid) {
                  String message =
                      String.format("测试类 %s 命名不符合规范，应以 Test/IT/E2E 结尾", javaClass.getFullName());
                  events.add(SimpleConditionEvent.violated(javaClass, message));
                }
              }
            })
        .as("测试类命名必须以 Test / IT / E2E 结尾（工具类除外：*Builder/*Support/*Helper/*Collector/*Initializer）")
        .because("统一的命名规范便于识别测试类型和执行范围");
  }

  /// Domain 层测试禁止依赖 Spring 框架。
  ///
  /// Domain 层测试必须是纯单元测试，不使用 @SpringBootTest、@Autowired 等。
  ///
  /// @return ArchRule 规则实例
  public static ArchRule domainTestsShouldNotDependOnSpring() {
    return noClasses()
        .that()
        .resideInAPackage("..domain..")
        .and()
        .haveSimpleNameEndingWith("Test")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework..", "org.springframework.boot.test..")
        .as("Domain 层测试禁止依赖 Spring 框架")
        .because("Domain 层测试必须是纯单元测试，保持快速和独立（CHK-TEST-001）");
  }

  /// 测试方法必须有 @Test 或 @ParameterizedTest 注解。
  ///
  /// 防止测试方法遗漏注解导致不被执行。
  /// 例外：setUp/tearDown、静态方法、私有方法、生命周期方法。
  ///
  /// @return ArchRule 规则实例
  public static ArchRule testMethodsShouldHaveTestAnnotation() {
    return methods()
        .that()
        .areDeclaredInClassesThat()
        .haveSimpleNameEndingWith("Test")
        .or()
        .areDeclaredInClassesThat()
        .haveSimpleNameEndingWith("IT")
        .or()
        .areDeclaredInClassesThat()
        .haveSimpleNameEndingWith("E2E")
        .and()
        .arePublic()
        .and()
        .areNotStatic()
        .and()
        .doNotHaveName("setUp")
        .and()
        .doNotHaveName("tearDown")
        .and(
            new DescribedPredicate<JavaMethod>(
                "不是 @BeforeEach/@AfterEach/@BeforeAll/@AfterAll 方法") {
              @Override
              public boolean test(JavaMethod method) {
                return !method.isAnnotatedWith("org.junit.jupiter.api.BeforeEach")
                    && !method.isAnnotatedWith("org.junit.jupiter.api.AfterEach")
                    && !method.isAnnotatedWith("org.junit.jupiter.api.BeforeAll")
                    && !method.isAnnotatedWith("org.junit.jupiter.api.AfterAll");
              }
            })
        .should(
            new ArchCondition<JavaMethod>("有 @Test 或 @ParameterizedTest 注解") {
              @Override
              public void check(JavaMethod method, ConditionEvents events) {
                boolean hasTestAnnotation =
                    method.isAnnotatedWith(Test.class)
                        || method.isAnnotatedWith(ParameterizedTest.class)
                        || method.isAnnotatedWith("org.junit.jupiter.api.RepeatedTest")
                        || method.isAnnotatedWith("org.junit.jupiter.api.TestFactory")
                        || method.isAnnotatedWith("org.junit.jupiter.api.TestTemplate");
                if (!hasTestAnnotation) {
                  String message =
                      String.format(
                          "测试方法 %s.%s() 缺少测试注解 (@Test/@ParameterizedTest)",
                          method.getOwner().getSimpleName(), method.getName());
                  events.add(SimpleConditionEvent.violated(method, message));
                }
              }
            })
        .as("测试方法必须有 @Test 或 @ParameterizedTest 注解")
        .because("防止测试方法遗漏注解导致不被执行");
  }

  /// Repository 集成测试必须在 infra 模块。
  ///
  /// @return ArchRule 规则实例
  public static ArchRule repositoryIntegrationTestsShouldResideInInfra() {
    return classes()
        .that()
        .haveSimpleNameEndingWith("IT")
        .and(
            new DescribedPredicate<JavaClass>("类名包含 Repository") {
              @Override
              public boolean test(JavaClass javaClass) {
                return javaClass.getSimpleName().contains("Repository");
              }
            })
        .should()
        .resideInAPackage("..infra..")
        .as("Repository 集成测试必须在 infra 模块")
        .because("集成测试应该在被测试组件所在的模块（CHK-TEST-003）");
  }

  /// 测试类不应该依赖其他测试类（测试工具类除外）。
  ///
  /// @return ArchRule 规则实例
  public static ArchRule testsShouldNotDependOnOtherTests() {
    return noClasses()
        .that()
        .haveSimpleNameEndingWith("Test")
        .or()
        .haveSimpleNameEndingWith("IT")
        .or()
        .haveSimpleNameEndingWith("E2E")
        .should()
        .dependOnClassesThat(
            new DescribedPredicate<JavaClass>("是其他测试类（非工具类）") {
              @Override
              public boolean test(JavaClass javaClass) {
                String simpleName = javaClass.getSimpleName();
                boolean isTestClass =
                    simpleName.endsWith("Test")
                        || simpleName.endsWith("IT")
                        || simpleName.endsWith("E2E");
                boolean isUtilityClass =
                    simpleName.endsWith("Builder")
                        || simpleName.endsWith("Support")
                        || simpleName.endsWith("Helper")
                        || simpleName.endsWith("Collector")
                        || simpleName.endsWith("Initializer");
                return isTestClass && !isUtilityClass;
              }
            })
        .as("测试类不应该依赖其他测试类（测试工具类除外）")
        .because("测试应该独立，避免测试之间的耦合");
  }

  /// E2E 测试必须在 boot 模块。
  ///
  /// @return ArchRule 规则实例
  public static ArchRule e2eTestsShouldResideInBootModule() {
    return classes()
        .that()
        .haveSimpleNameEndingWith("E2E")
        .should()
        .resideInAPackage("..boot..")
        .as("E2E 测试必须在 boot 模块")
        .because("E2E 测试需要完整的应用上下文（CHK-TEST-005）");
  }
}
