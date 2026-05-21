package dev.linqibin.starter.jpa.entity;

import static org.assertj.core.api.Assertions.*;

import java.io.Serializable;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link ChildJpaEntity} 子实体基类单元测试。
///
/// 测试策略: 纯单元测试，验证子实体基类的字段和方法行为。
///
/// 测试覆盖:
///
/// - ✅ 接口实现 - 实现 Serializable 接口
/// - ✅ 核心字段 - id, version, createdAt, updatedAt
/// - ✅ 无审计人员字段 - 确保没有 createdBy/updatedBy
/// - ✅ Builder 模式 - Lombok @SuperBuilder 支持
/// - ✅ Equals/HashCode - 基于所有字段的相等性判断
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ChildJpaEntity 子实体基类单元测试")
class ChildJpaEntityTest {

  private TestChildEntity entity;

  @BeforeEach
  void setUp() {
    entity = new TestChildEntity();
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

    @Test
    @DisplayName("不应该是 ValueObjectJpaEntity 的子类")
    void shouldNotExtendValueObjectJpaEntity() {
      // Given & When & Then
      assertThat(entity).isNotInstanceOf(ValueObjectJpaEntity.class);
    }
  }

  @Nested
  @DisplayName("ID 字段测试")
  class IdFieldTests {

    @Test
    @DisplayName("新实体的 id 应该为 null")
    void newEntityShouldHaveNullId() {
      // Given & When
      TestChildEntity newEntity = new TestChildEntity();

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
  @DisplayName("时间戳字段测试")
  class TimestampFieldTests {

    @Test
    @DisplayName("新实体的 createdAt 应该为 null")
    void newEntityShouldHaveNullCreatedAt() {
      // Given & When
      TestChildEntity newEntity = new TestChildEntity();

      // Then
      assertThat(newEntity.getCreatedAt()).isNull();
    }

    @Test
    @DisplayName("应该能够设置和获取 createdAt")
    void shouldSetAndGetCreatedAt() {
      // Given
      Instant expectedCreatedAt = Instant.now();

      // When
      entity.setCreatedAt(expectedCreatedAt);

      // Then
      assertThat(entity.getCreatedAt()).isEqualTo(expectedCreatedAt);
    }

    @Test
    @DisplayName("新实体的 updatedAt 应该为 null")
    void newEntityShouldHaveNullUpdatedAt() {
      // Given & When
      TestChildEntity newEntity = new TestChildEntity();

      // Then
      assertThat(newEntity.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("应该能够设置和获取 updatedAt")
    void shouldSetAndGetUpdatedAt() {
      // Given
      Instant expectedUpdatedAt = Instant.now();

      // When
      entity.setUpdatedAt(expectedUpdatedAt);

      // Then
      assertThat(entity.getUpdatedAt()).isEqualTo(expectedUpdatedAt);
    }
  }

  @Nested
  @DisplayName("版本字段测试")
  class VersionFieldTests {

    @Test
    @DisplayName("新实体的 version 应该为 null")
    void newEntityShouldHaveNullVersion() {
      // Given & When
      TestChildEntity newEntity = new TestChildEntity();

      // Then
      assertThat(newEntity.getVersion()).isNull();
    }

    @Test
    @DisplayName("应该能够设置和获取 version")
    void shouldSetAndGetVersion() {
      // Given
      Long expectedVersion = 5L;

      // When
      entity.setVersion(expectedVersion);

      // Then
      assertThat(entity.getVersion()).isEqualTo(expectedVersion);
    }
  }

  @Nested
  @DisplayName("无审计人员字段验证")
  class NoAuditPersonFieldsTests {

    @Test
    @DisplayName("不应该有 createdBy 字段")
    void shouldNotHaveCreatedByField() {
      // Given & When & Then
      assertThatThrownBy(() -> TestChildEntity.class.getDeclaredField("createdBy"))
          .isInstanceOf(NoSuchFieldException.class);
    }

    @Test
    @DisplayName("不应该有 createdByName 字段")
    void shouldNotHaveCreatedByNameField() {
      // Given & When & Then
      assertThatThrownBy(() -> TestChildEntity.class.getDeclaredField("createdByName"))
          .isInstanceOf(NoSuchFieldException.class);
    }

    @Test
    @DisplayName("不应该有 updatedBy 字段")
    void shouldNotHaveUpdatedByField() {
      // Given & When & Then
      assertThatThrownBy(() -> TestChildEntity.class.getDeclaredField("updatedBy"))
          .isInstanceOf(NoSuchFieldException.class);
    }

    @Test
    @DisplayName("不应该有 updatedByName 字段")
    void shouldNotHaveUpdatedByNameField() {
      // Given & When & Then
      assertThatThrownBy(() -> TestChildEntity.class.getDeclaredField("updatedByName"))
          .isInstanceOf(NoSuchFieldException.class);
    }

    @Test
    @DisplayName("不应该有 recordRemarks 字段")
    void shouldNotHaveRecordRemarksField() {
      // Given & When & Then
      assertThatThrownBy(() -> TestChildEntity.class.getDeclaredField("recordRemarks"))
          .isInstanceOf(NoSuchFieldException.class);
    }

    @Test
    @DisplayName("不应该有 ipAddress 字段")
    void shouldNotHaveIpAddressField() {
      // Given & When & Then
      assertThatThrownBy(() -> TestChildEntity.class.getDeclaredField("ipAddress"))
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
      Long expectedVersion = 1L;

      // When
      TestChildEntity builtEntity =
          TestChildEntity.builder().id(expectedId).version(expectedVersion).build();

      // Then
      assertThat(builtEntity.getId()).isEqualTo(expectedId);
      assertThat(builtEntity.getVersion()).isEqualTo(expectedVersion);
    }

    @Test
    @DisplayName("Builder 应该支持设置时间戳字段")
    void builderShouldSupportTimestampFields() {
      // Given
      Long expectedId = 123L;
      Instant expectedCreatedAt = Instant.now();
      Instant expectedUpdatedAt = Instant.now();

      // When
      TestChildEntity builtEntity =
          TestChildEntity.builder()
              .id(expectedId)
              .createdAt(expectedCreatedAt)
              .updatedAt(expectedUpdatedAt)
              .build();

      // Then
      assertThat(builtEntity.getId()).isEqualTo(expectedId);
      assertThat(builtEntity.getCreatedAt()).isEqualTo(expectedCreatedAt);
      assertThat(builtEntity.getUpdatedAt()).isEqualTo(expectedUpdatedAt);
    }

    @Test
    @DisplayName("Builder 应该支持设置业务字段")
    void builderShouldSupportBusinessFields() {
      // Given
      Long expectedId = 123L;
      String expectedName = "test-name";

      // When
      TestChildEntity builtEntity =
          TestChildEntity.builder().id(expectedId).name(expectedName).build();

      // Then
      assertThat(builtEntity.getId()).isEqualTo(expectedId);
      assertThat(builtEntity.getName()).isEqualTo(expectedName);
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
      TestChildEntity entity1 =
          TestChildEntity.builder()
              .id(1L)
              .createdAt(now)
              .updatedAt(now)
              .version(0L)
              .name("test")
              .build();
      TestChildEntity entity2 =
          TestChildEntity.builder()
              .id(1L)
              .createdAt(now)
              .updatedAt(now)
              .version(0L)
              .name("test")
              .build();

      // When & Then
      assertThat(entity1).isEqualTo(entity2);
      assertThat(entity1.hashCode()).isEqualTo(entity2.hashCode());
    }

    @Test
    @DisplayName("不同 id 的实体应该不相等")
    void entitiesWithDifferentIdShouldNotBeEqual() {
      // Given
      TestChildEntity entity1 = TestChildEntity.builder().id(1L).build();
      TestChildEntity entity2 = TestChildEntity.builder().id(2L).build();

      // When & Then
      assertThat(entity1).isNotEqualTo(entity2);
    }

    @Test
    @DisplayName("不同 version 的实体应该不相等")
    void entitiesWithDifferentVersionShouldNotBeEqual() {
      // Given
      TestChildEntity entity1 = TestChildEntity.builder().id(1L).version(0L).build();
      TestChildEntity entity2 = TestChildEntity.builder().id(1L).version(1L).build();

      // When & Then
      assertThat(entity1).isNotEqualTo(entity2);
    }
  }

  /// 测试用具体实体类。
  ///
  /// 继承 ChildJpaEntity 用于测试抽象类的行为。
  @lombok.Getter
  @lombok.Setter
  @lombok.NoArgsConstructor
  @lombok.AllArgsConstructor
  @lombok.experimental.SuperBuilder
  @lombok.EqualsAndHashCode(callSuper = true)
  private static class TestChildEntity extends ChildJpaEntity {

    /// 业务字段，用于测试子类字段支持。
    private String name;
  }
}
