package com.patra.starter.jpa.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link SoftDeletableJpaEntity} 实体基类单元测试。
///
/// 测试策略: 纯单元测试，验证实体基类的结构和注解配置。
///
/// 测试覆盖:
///
/// - ✅ 继承验证 - 继承自 BaseJpaEntity
/// - ✅ 注解配置 - @SoftDelete 注解配置正确
/// - ✅ Builder 模式 - Lombok @SuperBuilder 支持
///
/// 注意: `@SoftDelete` 由 Hibernate 内部管理 `deleted_at` 列，
/// 应用层不再直接访问该字段。软删除通过 `repository.delete()` 触发。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("SoftDeletableJpaEntity 实体基类单元测试")
class SoftDeletableJpaEntityTest {

  private TestEntity entity;

  @BeforeEach
  void setUp() {
    entity = new TestEntity();
  }

  @Nested
  @DisplayName("继承关系测试")
  class InheritanceTests {

    @Test
    @DisplayName("应该继承自 BaseJpaEntity")
    void shouldExtendBaseJpaEntity() {
      // Given & When & Then
      assertThat(entity).isInstanceOf(BaseJpaEntity.class);
    }

    @Test
    @DisplayName("应该继承 BaseJpaEntity 的 ID 字段")
    void shouldInheritIdFieldFromBaseJpaEntity() {
      // Given
      Long expectedId = 123456789L;

      // When
      entity.setId(expectedId);

      // Then
      assertThat(entity.getId()).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("应该继承 BaseJpaEntity 的审计字段")
    void shouldInheritAuditFieldsFromBaseJpaEntity() {
      // Given
      Instant createdAt = Instant.now().minus(Duration.ofDays(1));
      Instant updatedAt = Instant.now();
      Long createdBy = 100L;
      Long updatedBy = 200L;

      // When
      entity.setCreatedAt(createdAt);
      entity.setUpdatedAt(updatedAt);
      entity.setCreatedBy(createdBy);
      entity.setUpdatedBy(updatedBy);

      // Then
      assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
      assertThat(entity.getUpdatedAt()).isEqualTo(updatedAt);
      assertThat(entity.getCreatedBy()).isEqualTo(createdBy);
      assertThat(entity.getUpdatedBy()).isEqualTo(updatedBy);
    }

    @Test
    @DisplayName("应该继承 BaseJpaEntity 的版本字段")
    void shouldInheritVersionFieldFromBaseJpaEntity() {
      // Given
      Long expectedVersion = 5L;

      // When
      entity.setVersion(expectedVersion);

      // Then
      assertThat(entity.getVersion()).isEqualTo(expectedVersion);
    }
  }

  @Nested
  @DisplayName("@SoftDelete 注解配置测试")
  class SoftDeleteAnnotationTests {

    @Test
    @DisplayName("SoftDeletableJpaEntity 应该有 @SoftDelete 注解")
    void shouldHaveSoftDeleteAnnotation() {
      // Given & When
      SoftDelete annotation = SoftDeletableJpaEntity.class.getAnnotation(SoftDelete.class);

      // Then
      assertThat(annotation).isNotNull();
    }

    @Test
    @DisplayName("@SoftDelete 应该使用 TIMESTAMP 策略")
    void shouldUseTimestampStrategy() {
      // Given & When
      SoftDelete annotation = SoftDeletableJpaEntity.class.getAnnotation(SoftDelete.class);

      // Then
      assertThat(annotation.strategy()).isEqualTo(SoftDeleteType.TIMESTAMP);
    }

    @Test
    @DisplayName("@SoftDelete 应该使用 deleted_at 列名")
    void shouldUseDeletedAtColumnName() {
      // Given & When
      SoftDelete annotation = SoftDeletableJpaEntity.class.getAnnotation(SoftDelete.class);

      // Then
      assertThat(annotation.columnName()).isEqualTo("deleted_at");
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
      Long expectedVersion = 1L;

      // When
      TestEntity builtEntity = TestEntity.builder().id(expectedId).version(expectedVersion).build();

      // Then
      assertThat(builtEntity.getId()).isEqualTo(expectedId);
      assertThat(builtEntity.getVersion()).isEqualTo(expectedVersion);
    }

    @Test
    @DisplayName("Builder 应该支持设置父类字段")
    void builderShouldSupportParentClassFields() {
      // Given
      Long expectedId = 123L;
      Long expectedVersion = 1L;
      Instant expectedCreatedAt = Instant.now();

      // When
      TestEntity builtEntity =
          TestEntity.builder()
              .id(expectedId)
              .version(expectedVersion)
              .createdAt(expectedCreatedAt)
              .build();

      // Then
      assertThat(builtEntity.getId()).isEqualTo(expectedId);
      assertThat(builtEntity.getVersion()).isEqualTo(expectedVersion);
      assertThat(builtEntity.getCreatedAt()).isEqualTo(expectedCreatedAt);
    }
  }

  @Nested
  @DisplayName("Equals 和 HashCode 测试")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("相同字段的实体应该相等")
    void entitiesWithSameFieldsShouldBeEqual() {
      // Given
      TestEntity entity1 = TestEntity.builder().id(1L).version(0L).build();
      TestEntity entity2 = TestEntity.builder().id(1L).version(0L).build();

      // When & Then
      assertThat(entity1).isEqualTo(entity2);
      assertThat(entity1.hashCode()).isEqualTo(entity2.hashCode());
    }

    @Test
    @DisplayName("不同 ID 的实体应该不相等")
    void entitiesWithDifferentIdShouldNotBeEqual() {
      // Given
      TestEntity entity1 = TestEntity.builder().id(1L).version(0L).build();
      TestEntity entity2 = TestEntity.builder().id(2L).version(0L).build();

      // When & Then
      assertThat(entity1).isNotEqualTo(entity2);
    }
  }

  /// 测试用具体实体类。
  ///
  /// 继承 SoftDeletableJpaEntity 用于测试抽象类的行为。
  @lombok.Getter
  @lombok.Setter
  @lombok.experimental.SuperBuilder
  @lombok.EqualsAndHashCode(callSuper = true)
  private static class TestEntity extends SoftDeletableJpaEntity {

    /// 无参构造器，用于测试。
    TestEntity() {
      super();
    }
  }
}
