package com.patra.starter.jpa.entity;

import static org.assertj.core.api.Assertions.*;

import java.io.Serializable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link ValueObjectJpaEntity} 值对象表基类单元测试。
///
/// 测试策略: 纯单元测试，验证值对象表基类的字段和方法行为。
///
/// 测试覆盖:
///
/// - ✅ 接口实现 - 实现 Serializable 接口
/// - ✅ ID 字段 - 仅有 id 字段，无审计字段
/// - ✅ Builder 模式 - Lombok @SuperBuilder 支持
/// - ✅ Equals/HashCode - 基于 id 的相等性判断
/// - ✅ 无审计字段 - 确保没有继承审计字段
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ValueObjectJpaEntity 值对象表基类单元测试")
class ValueObjectJpaEntityTest {

  private TestValueEntity entity;

  @BeforeEach
  void setUp() {
    entity = new TestValueEntity();
  }

  @Nested
  @DisplayName("接口实现测试")
  class InterfaceTests {

    @Test
    @DisplayName("应该实现 Serializable 接口")
    void shouldImplementSerializable() {
      // Given & When & Then
      assertThat(entity).isInstanceOf(Serializable.class);
    }

    @Test
    @DisplayName("不应该是 BaseJpaEntity 的子类")
    void shouldNotExtendBaseJpaEntity() {
      // Given & When & Then
      assertThat(entity).isNotInstanceOf(BaseJpaEntity.class);
    }
  }

  @Nested
  @DisplayName("ID 字段测试")
  class IdFieldTests {

    @Test
    @DisplayName("新实体的 id 应该为 null")
    void newEntityShouldHaveNullId() {
      // Given & When
      TestValueEntity newEntity = new TestValueEntity();

      // Then
      assertThat(newEntity.getId()).isNull();
    }

    @Test
    @DisplayName("应该能够设置和获取 id")
    void shouldSetAndGetId() {
      // Given
      Long expectedId = 123456789L;

      // When
      entity.setId(expectedId);

      // Then
      assertThat(entity.getId()).isEqualTo(expectedId);
    }
  }

  @Nested
  @DisplayName("无审计字段验证")
  class NoAuditFieldsTests {

    @Test
    @DisplayName("不应该有 version 字段（通过反射验证）")
    void shouldNotHaveVersionField() {
      // Given & When & Then
      assertThatThrownBy(() -> TestValueEntity.class.getDeclaredField("version"))
          .isInstanceOf(NoSuchFieldException.class);
    }

    @Test
    @DisplayName("不应该有 createdAt 字段")
    void shouldNotHaveCreatedAtField() {
      // Given & When & Then
      assertThatThrownBy(() -> TestValueEntity.class.getDeclaredField("createdAt"))
          .isInstanceOf(NoSuchFieldException.class);
    }

    @Test
    @DisplayName("不应该有 updatedAt 字段")
    void shouldNotHaveUpdatedAtField() {
      // Given & When & Then
      assertThatThrownBy(() -> TestValueEntity.class.getDeclaredField("updatedAt"))
          .isInstanceOf(NoSuchFieldException.class);
    }

    @Test
    @DisplayName("不应该有 createdBy 字段")
    void shouldNotHaveCreatedByField() {
      // Given & When & Then
      assertThatThrownBy(() -> TestValueEntity.class.getDeclaredField("createdBy"))
          .isInstanceOf(NoSuchFieldException.class);
    }
  }

  @Nested
  @DisplayName("Builder 模式测试")
  class BuilderTests {

    @Test
    @DisplayName("应该支持使用 Builder 创建实体")
    void shouldSupportBuilderPattern() {
      // Given
      Long expectedId = 999L;

      // When
      TestValueEntity builtEntity = TestValueEntity.builder().id(expectedId).build();

      // Then
      assertThat(builtEntity.getId()).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("Builder 应该支持设置业务字段")
    void builderShouldSupportBusinessFields() {
      // Given
      Long expectedId = 123L;
      String expectedValue = "test-value";

      // When
      TestValueEntity builtEntity =
          TestValueEntity.builder().id(expectedId).businessField(expectedValue).build();

      // Then
      assertThat(builtEntity.getId()).isEqualTo(expectedId);
      assertThat(builtEntity.getBusinessField()).isEqualTo(expectedValue);
    }

    @Test
    @DisplayName("Builder 不设置 id 时应该默认为 null")
    void builderShouldDefaultIdToNull() {
      // Given & When
      TestValueEntity builtEntity = TestValueEntity.builder().build();

      // Then
      assertThat(builtEntity.getId()).isNull();
    }
  }

  @Nested
  @DisplayName("Equals 和 HashCode 测试")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("相同 id 的实体应该相等")
    void entitiesWithSameIdShouldBeEqual() {
      // Given
      TestValueEntity entity1 = TestValueEntity.builder().id(1L).businessField("a").build();
      TestValueEntity entity2 = TestValueEntity.builder().id(1L).businessField("a").build();

      // When & Then
      assertThat(entity1).isEqualTo(entity2);
      assertThat(entity1.hashCode()).isEqualTo(entity2.hashCode());
    }

    @Test
    @DisplayName("不同 id 的实体应该不相等")
    void entitiesWithDifferentIdShouldNotBeEqual() {
      // Given
      TestValueEntity entity1 = TestValueEntity.builder().id(1L).build();
      TestValueEntity entity2 = TestValueEntity.builder().id(2L).build();

      // When & Then
      assertThat(entity1).isNotEqualTo(entity2);
    }

    @Test
    @DisplayName("id 为 null 的两个实体应该相等（值对象语义）")
    void entitiesWithNullIdShouldBeEqual() {
      // Given
      TestValueEntity entity1 = TestValueEntity.builder().businessField("same").build();
      TestValueEntity entity2 = TestValueEntity.builder().businessField("same").build();

      // When & Then
      assertThat(entity1).isEqualTo(entity2);
    }
  }

  /// 测试用具体实体类。
  ///
  /// 继承 ValueObjectJpaEntity 用于测试抽象类的行为。
  @lombok.Getter
  @lombok.Setter
  @lombok.NoArgsConstructor
  @lombok.AllArgsConstructor
  @lombok.experimental.SuperBuilder
  @lombok.EqualsAndHashCode(callSuper = true)
  private static class TestValueEntity extends ValueObjectJpaEntity {

    /// 业务字段，用于测试子类字段支持。
    private String businessField;
  }
}
