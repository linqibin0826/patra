package com.patra.starter.test.archunit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// MyBatisArchRules 单元测试。
///
/// 验证 ArchUnit 规则能正确检测违规类。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("MyBatisArchRules 单元测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class MyBatisArchRulesTest {

  @Nested
  @DisplayName("noServiceImplInRepository 规则")
  class NoServiceImplInRepositoryTests {

    @Test
    @DisplayName("继承 ServiceImpl 的 RepositoryAdapter 应被检测")
    void shouldDetectRepositoryAdapterExtendingServiceImpl() {
      // Arrange
      JavaClasses classes =
          new ClassFileImporter().importClasses(ViolatingRepositoryAdapter.class);
      ArchRule rule = MyBatisArchRules.noServiceImplInRepository();

      // Act & Assert
      assertThatThrownBy(() -> rule.check(classes))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("ViolatingRepositoryAdapter");
    }

    @Test
    @DisplayName("不继承 ServiceImpl 的 RepositoryAdapter 应通过")
    void shouldPassForRepositoryAdapterNotExtendingServiceImpl() {
      // Arrange
      JavaClasses classes =
          new ClassFileImporter().importClasses(CompliantRepositoryAdapter.class);
      ArchRule rule = MyBatisArchRules.noServiceImplInRepository();

      // Act & Assert
      assertThatCode(() -> rule.check(classes)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("其他类继承 ServiceImpl 不应被检测")
    void shouldNotDetectOtherClassesExtendingServiceImpl() {
      // Arrange - 类名不以 RepositoryAdapter 结尾
      JavaClasses classes =
          new ClassFileImporter().importClasses(SomeServiceExtendingServiceImpl.class);
      // 允许空匹配，因为 SomeServiceExtendingServiceImpl 不匹配 *RepositoryAdapter 模式
      ArchRule rule = MyBatisArchRules.noServiceImplInRepository().allowEmptyShould(true);

      // Act & Assert - 规则只检查 *RepositoryAdapter，其他类不受影响
      assertThatCode(() -> rule.check(classes)).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("noServiceImplInInfraLayer 规则")
  class NoServiceImplInInfraLayerTests {

    @Test
    @DisplayName("infra 包下继承 ServiceImpl 的类应被检测")
    void shouldDetectServiceImplInInfraPackage() {
      // Arrange
      JavaClasses classes =
          new ClassFileImporter()
              .importClasses(
                  com.patra.starter.test.archunit.testfixtures.infra.InfraViolatingClass.class);
      ArchRule rule = MyBatisArchRules.noServiceImplInInfraLayer();

      // Act & Assert
      assertThatThrownBy(() -> rule.check(classes))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("InfraViolatingClass");
    }

    @Test
    @DisplayName("infra 包下不继承 ServiceImpl 的类应通过")
    void shouldPassForInfraClassNotExtendingServiceImpl() {
      // Arrange
      JavaClasses classes =
          new ClassFileImporter()
              .importClasses(
                  com.patra.starter.test.archunit.testfixtures.infra.InfraCompliantClass.class);
      ArchRule rule = MyBatisArchRules.noServiceImplInInfraLayer();

      // Act & Assert
      assertThatCode(() -> rule.check(classes)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("非 infra 包的类不应被检测")
    void shouldNotDetectClassesOutsideInfraPackage() {
      // Arrange - 类不在 infra 包下
      JavaClasses classes =
          new ClassFileImporter().importClasses(ViolatingRepositoryAdapter.class);
      // 允许空匹配，因为 ViolatingRepositoryAdapter 不在 ..infra.. 包下
      ArchRule rule = MyBatisArchRules.noServiceImplInInfraLayer().allowEmptyShould(true);

      // Act & Assert - ViolatingRepositoryAdapter 不在 infra 包下，不受此规则约束
      assertThatCode(() -> rule.check(classes)).doesNotThrowAnyException();
    }
  }

  // ==================== 测试夹具类 ====================

  /// 违规示例：RepositoryAdapter 继承 ServiceImpl。
  @SuppressWarnings("unused")
  static class ViolatingRepositoryAdapter extends ServiceImpl<TestMapper, TestEntity> {}

  /// 合规示例：RepositoryAdapter 不继承 ServiceImpl。
  @SuppressWarnings("unused")
  static class CompliantRepositoryAdapter {}

  /// 其他类继承 ServiceImpl（不是 RepositoryAdapter）。
  @SuppressWarnings("unused")
  static class SomeServiceExtendingServiceImpl extends ServiceImpl<TestMapper, TestEntity> {}

  /// 测试用 Mapper 接口。
  interface TestMapper extends BaseMapper<TestEntity> {}

  /// 测试用实体类。
  static class TestEntity {}
}
