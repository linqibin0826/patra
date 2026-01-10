package com.patra.starter.jpa.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link SoftDeletableChildJpaEntity} 实体基类单元测试。
///
/// 测试策略: 纯单元测试，验证实体基类的字段和方法行为。
///
/// 测试覆盖:
///
/// - ✅ 继承验证 - 继承自 ChildJpaEntity
/// - ✅ 软删除字段 - deletedAt 字段行为
/// - ✅ 软删除方法 - softDelete() 和 isDeleted() 方法
/// - ✅ Builder 模式 - Lombok @SuperBuilder 支持
/// - ✅ 接口实现 - 实现 SoftDeletable 接口
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("SoftDeletableChildJpaEntity 实体基类单元测试")
class SoftDeletableChildJpaEntityTest {

  private TestEntity entity;

  @BeforeEach
  void setUp() {
    entity = new TestEntity();
  }

  @Nested
  @DisplayName("继承关系测试")
  class InheritanceTests {

    @Test
    @DisplayName("应该继承自 ChildJpaEntity")
    void shouldExtendChildJpaEntity() {
      // Given & When & Then
      assertThat(entity).isInstanceOf(ChildJpaEntity.class);
    }

    @Test
    @DisplayName("应该实现 SoftDeletable 接口")
    void shouldImplementSoftDeletable() {
      // Given & When & Then
      assertThat(entity).isInstanceOf(SoftDeletable.class);
    }

    @Test
    @DisplayName("应该实现 IdAwareEntity 接口")
    void shouldImplementIdAwareEntity() {
      // Given & When & Then
      assertThat(entity).isInstanceOf(IdAwareEntity.class);
    }

    @Test
    @DisplayName("应该继承 ChildJpaEntity 的 ID 字段")
    void shouldInheritIdFieldFromChildJpaEntity() {
      // Given
      Long expectedId = 123456789L;

      // When
      entity.setId(expectedId);

      // Then
      assertThat(entity.getId()).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("应该继承 ChildJpaEntity 的时间戳字段")
    void shouldInheritTimestampFieldsFromChildJpaEntity() {
      // Given
      Instant createdAt = Instant.now().minus(Duration.ofDays(1));
      Instant updatedAt = Instant.now();

      // When
      entity.setCreatedAt(createdAt);
      entity.setUpdatedAt(updatedAt);

      // Then
      assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
      assertThat(entity.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("应该继承 ChildJpaEntity 的版本字段")
    void shouldInheritVersionFieldFromChildJpaEntity() {
      // Given
      Long expectedVersion = 5L;

      // When
      entity.setVersion(expectedVersion);

      // Then
      assertThat(entity.getVersion()).isEqualTo(expectedVersion);
    }

    @Test
    @DisplayName("不应该有 createdBy/updatedBy 等聚合根审计字段")
    void shouldNotHaveAggregateRootAuditFields() {
      // Given & When & Then
      // 验证 TestEntity（继承 SoftDeletableChildJpaEntity）没有 BaseJpaEntity 的审计字段
      assertThat(entity).isNotInstanceOf(BaseJpaEntity.class);
    }
  }

  @Nested
  @DisplayName("软删除字段测试")
  class SoftDeleteFieldTests {

    @Test
    @DisplayName("新实体的 deletedAt 应该为 null")
    void newEntityShouldHaveNullDeletedAt() {
      // Given & When
      TestEntity newEntity = new TestEntity();

      // Then
      assertThat(newEntity.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("应该能够设置和获取 deletedAt")
    void shouldSetAndGetDeletedAt() {
      // Given
      Instant expectedDeletedAt = Instant.now();

      // When
      entity.setDeletedAt(expectedDeletedAt);

      // Then
      assertThat(entity.getDeletedAt()).isEqualTo(expectedDeletedAt);
    }

    @Test
    @DisplayName("应该能够将 deletedAt 设置为 null")
    void shouldSetDeletedAtToNull() {
      // Given
      entity.setDeletedAt(Instant.now());
      assertThat(entity.getDeletedAt()).isNotNull();

      // When
      entity.setDeletedAt(null);

      // Then
      assertThat(entity.getDeletedAt()).isNull();
    }
  }

  @Nested
  @DisplayName("软删除方法测试")
  class SoftDeleteMethodTests {

    @Test
    @DisplayName("softDelete() 应该设置 deletedAt 为当前时间")
    void softDeleteShouldSetDeletedAtToCurrentTime() {
      // Given
      Instant before = Instant.now();

      // When
      entity.softDelete();

      // Then
      Instant after = Instant.now();
      assertThat(entity.getDeletedAt())
          .isNotNull()
          .isAfterOrEqualTo(before)
          .isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("isDeleted() 应该在 deletedAt 为 null 时返回 false")
    void isDeletedShouldReturnFalseWhenDeletedAtIsNull() {
      // Given
      entity.setDeletedAt(null);

      // When & Then
      assertThat(entity.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("isDeleted() 应该在 deletedAt 有值时返回 true")
    void isDeletedShouldReturnTrueWhenDeletedAtHasValue() {
      // Given
      entity.setDeletedAt(Instant.now());

      // When & Then
      assertThat(entity.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("softDelete() 后 isDeleted() 应该返回 true")
    void isDeletedShouldReturnTrueAfterSoftDelete() {
      // Given
      assertThat(entity.isDeleted()).isFalse();

      // When
      entity.softDelete();

      // Then
      assertThat(entity.isDeleted()).isTrue();
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
      Instant expectedDeletedAt = Instant.now();

      // When
      TestEntity builtEntity =
          TestEntity.builder().id(expectedId).deletedAt(expectedDeletedAt).build();

      // Then
      assertThat(builtEntity.getId()).isEqualTo(expectedId);
      assertThat(builtEntity.getDeletedAt()).isEqualTo(expectedDeletedAt);
    }

    @Test
    @DisplayName("Builder 应该支持设置父类字段")
    void builderShouldSupportParentClassFields() {
      // Given
      Long expectedId = 123L;
      Long expectedVersion = 1L;
      Instant expectedCreatedAt = Instant.now();
      Instant expectedUpdatedAt = Instant.now();

      // When
      TestEntity builtEntity =
          TestEntity.builder()
              .id(expectedId)
              .version(expectedVersion)
              .createdAt(expectedCreatedAt)
              .updatedAt(expectedUpdatedAt)
              .build();

      // Then
      assertThat(builtEntity.getId()).isEqualTo(expectedId);
      assertThat(builtEntity.getVersion()).isEqualTo(expectedVersion);
      assertThat(builtEntity.getCreatedAt()).isEqualTo(expectedCreatedAt);
      assertThat(builtEntity.getUpdatedAt()).isEqualTo(expectedUpdatedAt);
    }

    @Test
    @DisplayName("Builder 不设置 deletedAt 时应该默认为 null")
    void builderShouldDefaultDeletedAtToNull() {
      // Given & When
      TestEntity builtEntity = TestEntity.builder().id(1L).build();

      // Then
      assertThat(builtEntity.getDeletedAt()).isNull();
      assertThat(builtEntity.isDeleted()).isFalse();
    }
  }

  @Nested
  @DisplayName("Equals 和 HashCode 测试")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("相同字段的实体应该相等")
    void entitiesWithSameFieldsShouldBeEqual() {
      // Given
      Instant now = Instant.now();
      TestEntity entity1 = TestEntity.builder().id(1L).deletedAt(now).version(0L).build();
      TestEntity entity2 = TestEntity.builder().id(1L).deletedAt(now).version(0L).build();

      // When & Then
      assertThat(entity1).isEqualTo(entity2);
      assertThat(entity1.hashCode()).isEqualTo(entity2.hashCode());
    }

    @Test
    @DisplayName("不同 deletedAt 的实体应该不相等")
    void entitiesWithDifferentDeletedAtShouldNotBeEqual() {
      // Given
      TestEntity entity1 = TestEntity.builder().id(1L).deletedAt(Instant.now()).version(0L).build();
      TestEntity entity2 = TestEntity.builder().id(1L).deletedAt(null).version(0L).build();

      // When & Then
      assertThat(entity1).isNotEqualTo(entity2);
    }
  }

  @Nested
  @DisplayName("字段数量验证")
  class FieldCountTests {

    @Test
    @DisplayName("应该总共有 5 个持久化字段")
    void shouldHaveFiveFields() {
      // Given
      Long id = 1L;
      Instant createdAt = Instant.now().minus(Duration.ofHours(2));
      Instant updatedAt = Instant.now().minus(Duration.ofHours(1));
      Long version = 3L;
      Instant deletedAt = Instant.now();

      // When
      TestEntity builtEntity =
          TestEntity.builder()
              .id(id)
              .createdAt(createdAt)
              .updatedAt(updatedAt)
              .version(version)
              .deletedAt(deletedAt)
              .build();

      // Then - 验证所有 5 个字段都可以设置和获取
      assertThat(builtEntity.getId()).isEqualTo(id);
      assertThat(builtEntity.getCreatedAt()).isEqualTo(createdAt);
      assertThat(builtEntity.getUpdatedAt()).isEqualTo(updatedAt);
      assertThat(builtEntity.getVersion()).isEqualTo(version);
      assertThat(builtEntity.getDeletedAt()).isEqualTo(deletedAt);
    }
  }

  /// 测试用具体实体类。
  ///
  /// 继承 SoftDeletableChildJpaEntity 用于测试抽象类的行为。
  @lombok.Getter
  @lombok.Setter
  @lombok.experimental.SuperBuilder
  @lombok.EqualsAndHashCode(callSuper = true)
  private static class TestEntity extends SoftDeletableChildJpaEntity {

    /// 无参构造器，用于测试。
    TestEntity() {
      super();
    }
  }
}
