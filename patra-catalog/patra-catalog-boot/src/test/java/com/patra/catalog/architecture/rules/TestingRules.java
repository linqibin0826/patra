package com.patra.catalog.architecture.rules;

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

/// 测试规范规则
/// 
/// 验证测试代码的架构约束和最佳实践：
/// 
/// - 测试类命名规范（*Test / *IT / *E2E）
///   - 测试包结构镜像生产代码
///   - Domain 层测试无框架依赖
///   - 测试方法必须有 @Test 注解
///   - 集成测试位置规范
///   - 测试独立性（测试不依赖测试）
///   - E2E 测试在 boot 模块
/// 
/// **架构约束检查点：**
/// 
/// - 【CHK-TEST-001】Domain 层测试必须是纯单元测试
///   - 【CHK-TEST-003】集成测试应该在被测试组件所在的模块
///   - 【CHK-TEST-005】E2E 测试需要完整的应用上下文
/// 
/// @author linqibin
/// @since 2025-01-10
public final class TestingRules {

  private TestingRules() {
    // 工具类，禁止实例化
  }

  /// 规则 17: 测试类命名必须符合规范
/// 
/// 测试类命名规范：
/// 
/// - 单元测试：*Test（如 MeshDataAggregateTest）
///   - 集成测试：*IT（如 MeshDataRepositoryIT）
///   - 端到端测试：*E2E（如 MeshImportE2E）
/// 
/// **例外：**测试工具类（*Builder、*Support、*Helper、*Collector）不受此规则约束。
/// 
/// **错误示例：**
/// 
/// ```
/// 
/// // ❌ 错误：测试类命名不规范
/// public class MeshDataAggregateTests { ... }        // 应为 MeshDataAggregateTest
/// public class MeshDataRepositoryIntegration { ... } // 应为 MeshDataRepositoryIT
/// 
/// ```
/// 
/// **正确示例：**
/// 
/// ```
/// 
/// // ✅ 正确：符合规范的测试类命名
/// public class MeshDataAggregateTest { ... }         // 单元测试
/// public class MeshDataRepositoryIT { ... }          // 集成测试
/// public class MeshImportE2E { ... }                 // 端到端测试
/// 
/// ```
  public static final ArchRule test_classes_should_follow_naming_convention =
      classes()
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
          .as(
              "测试类命名必须以 Test / IT / E2E 结尾（工具类除外：*Builder/*Support/*Helper/*Collector/*Initializer）")
          .because("统一的命名规范便于识别测试类型和执行范围");

  /// 规则 18: Domain 层测试禁止依赖 Spring 框架
/// 
/// Domain 层测试必须是纯单元测试：
/// 
/// - 不使用 @SpringBootTest
///   - 不使用 @Autowired
///   - 不使用 Spring 上下文
///   - 仅使用 JUnit 5 + AssertJ + Mockito
/// 
/// **原因：**
/// 
/// **错误示例：**
/// 
/// ```
/// 
/// // ❌ 错误：Domain 层测试使用 Spring
/// {@literal @}SpringBootTest
/// class MeshDataAggregateTest {
///     {@literal @}Autowired
///     private MeshDataAggregate data;  // ❌ 不应该使用 Spring 容器
/// }
/// 
/// ```
/// 
/// **正确示例：**
/// 
/// ```
/// 
/// // ✅ 正确：纯单元测试
/// class MeshDataAggregateTest {
///     {@literal @}Test
///     void should_change_status_when_importing() {
///         // Given
///         MeshDataAggregate data = new MeshDataAggregate(...);
/// 
///         // When
///         data.startImport();
/// 
///         // Then
///         assertThat(data.getStatus()).isEqualTo(ImportStatus.IMPORTING);
///     }
/// }
/// 
/// ```
  public static final ArchRule domain_tests_should_not_depend_on_spring =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .and()
          .haveSimpleNameEndingWith("Test")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..", "org.springframework.boot.test..")
          .as("Domain 层测试禁止依赖 Spring 框架")
          .because("Domain 层测试必须是纯单元测试，保持快速和独立（CHK-TEST-001）");

  /// 规则 19: 测试方法必须有 @Test 或 @ParameterizedTest 注解
/// 
/// 防止测试方法遗漏注解导致不被执行。
/// 
/// **例外：**以下方法不需要注解：
/// 
/// - setUp / tearDown 方法（使用 @BeforeEach / @AfterEach）
///   - 静态方法（如 @BeforeAll / @AfterAll）
///   - 私有方法（测试辅助方法）
/// 
/// **错误示例：**
/// 
/// ```
/// 
/// // ❌ 错误：测试方法遗漏 @Test 注解
/// class MeshDataAggregateTest {
///     public void should_change_status_when_importing() {  // ❌ 不会被执行
///         // 测试代码
///     }
/// }
/// 
/// ```
/// 
/// **正确示例：**
/// 
/// ```
/// 
/// // ✅ 正确：测试方法有 @Test 注解
/// class MeshDataAggregateTest {
///     {@literal @}Test
///     void should_change_status_when_importing() {
///         // 测试代码
///     }
/// 
///     private void helperMethod() {  // ✅ 私有方法不需要注解
///         // 辅助方法
///     }
/// }
/// 
/// ```
  public static final ArchRule test_methods_should_have_test_annotation =
      methods()
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

  /// 规则 20: Repository 集成测试必须在 infra 模块
/// 
/// 集成测试位置规范：
/// 
/// - Repository 集成测试 → infra 模块
///   - MQ Publisher 集成测试 → infra 模块
///   - 外部 API 适配器集成测试 → adapter 模块（或 infra）
/// 
/// **原因：**
/// 
/// **错误示例：**
/// 
/// ```
/// 
/// // ❌ 错误：Repository 集成测试在 boot 模块
/// // 位置：patra-catalog-boot/src/test/java/.../MeshDataRepositoryIT.java
/// {@literal @}MybatisTest
/// class MeshDataRepositoryIT { ... }
/// 
/// ```
/// 
/// **正确示例：**
/// 
/// ```
/// 
/// // ✅ 正确：Repository 集成测试在 infra 模块
/// // 位置：patra-catalog-infra/src/test/java/.../MeshDataRepositoryIT.java
/// {@literal @}MybatisTest
/// class MeshDataRepositoryIT { ... }
/// 
/// ```
  public static final ArchRule repository_integration_tests_should_reside_in_infra =
      classes()
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

  /// 规则 21: 测试类不能被其他测试类依赖
/// 
/// 测试应该独立，避免测试之间的耦合。
/// 
/// **例外：**测试工具类（*Builder、*Support、*Helper、*Collector）可以被测试类依赖。
/// 
/// **原因：**
/// 
/// **错误示例：**
/// 
/// ```
/// 
/// // ❌ 错误：测试类依赖其他测试类
/// class MeshDataAggregateTest {
///     {@literal @}Test
///     void test1() {
///         OtherTest otherTest = new OtherTest();  // ❌ 不应该依赖其他测试类
///         otherTest.someTestMethod();
///     }
/// }
/// 
/// ```
/// 
/// **正确示例：**
/// 
/// ```
/// 
/// // ✅ 正确：测试类使用测试工具类
/// class MeshDataAggregateTest {
///     {@literal @}Test
///     void test1() {
///         MeshData data = MeshDataTestBuilder.aDefaultMeshData().build();  // ✅ 使用工具类
///         // 测试代码
///     }
/// }
/// 
/// ```
  public static final ArchRule tests_should_not_depend_on_other_tests =
      noClasses()
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

  /// 规则 22: E2E 测试必须在 boot 模块
/// 
/// 端到端测试需要完整的应用上下文，应该在 boot 模块运行。
/// 
/// **原因：**
/// 
/// **错误示例：**
/// 
/// ```
/// 
/// // ❌ 错误：E2E 测试在 infra 模块
/// // 位置：patra-catalog-infra/src/test/java/.../MeshImportE2E.java
/// {@literal @}SpringBootTest
/// class MeshImportE2E { ... }
/// 
/// ```
/// 
/// **正确示例：**
/// 
/// ```
/// 
/// // ✅ 正确：E2E 测试在 boot 模块
/// // 位置：patra-catalog-boot/src/test/java/.../MeshImportE2E.java
/// {@literal @}SpringBootTest
/// class MeshImportE2E { ... }
/// 
/// ```
  public static final ArchRule e2e_tests_should_reside_in_boot_module =
      classes()
          .that()
          .haveSimpleNameEndingWith("E2E")
          .should()
          .resideInAPackage("..boot..")
          .as("E2E 测试必须在 boot 模块")
          .because("E2E 测试需要完整的应用上下文（CHK-TEST-005）");
}
