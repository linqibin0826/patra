package com.patra.objectstorage.domain.model.enums;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// FileStatus 枚举单元测试。
/// 
/// 测试策略：
/// 
/// - 纯 Java 单元测试，不依赖 Spring 容器
///   - 遵循 Given-When-Then 结构
///   - 使用 AssertJ 流畅断言
///   - 使用 @Nested 分组组织测试
/// 
/// 测试范围：
/// 
/// - ✅ 枚举值存在性测试（ACTIVE, EXPIRED, DELETED）
///   - ✅ values() 方法测试（数量、包含关系、顺序）
///   - ✅ valueOf() 方法测试（有效字符串、无效字符串、null）
///   - ✅ name() 方法测试（枚举名称验证）
///   - ✅ ordinal() 方法测试（枚举序号验证）
///   - ✅ equals() 和 == 比较测试
///   - ✅ 枚举单例不变性测试
/// 
/// @author linqibin
/// @since 0.2.0
@DisplayName("FileStatus 枚举单元测试")
class FileStatusTest {

  // ========== 枚举值存在性测试 ==========

  @Nested
  @DisplayName("枚举值存在性")
  class EnumValueExistenceTests {

    @Test
    @DisplayName("应该存在 ACTIVE 枚举值")
    void shouldHaveActiveEnumValue() {
      // Given & When
      FileStatus status = FileStatus.ACTIVE;

      // Then
      assertThat(status).isNotNull();
      assertThat(status).isEqualTo(FileStatus.ACTIVE);
    }

    @Test
    @DisplayName("应该存在 EXPIRED 枚举值")
    void shouldHaveExpiredEnumValue() {
      // Given & When
      FileStatus status = FileStatus.EXPIRED;

      // Then
      assertThat(status).isNotNull();
      assertThat(status).isEqualTo(FileStatus.EXPIRED);
    }

    @Test
    @DisplayName("应该存在 DELETED 枚举值")
    void shouldHaveDeletedEnumValue() {
      // Given & When
      FileStatus status = FileStatus.DELETED;

      // Then
      assertThat(status).isNotNull();
      assertThat(status).isEqualTo(FileStatus.DELETED);
    }

    @Test
    @DisplayName("应该有且仅有三个枚举值")
    void shouldHaveExactlyThreeEnumValues() {
      // Given & When
      FileStatus[] values = FileStatus.values();

      // Then
      assertThat(values).hasSize(3);
    }
  }

  // ========== values() 方法测试 ==========

  @Nested
  @DisplayName("values() 方法")
  class ValuesMethodTests {

    @Test
    @DisplayName("应该返回包含 3 个枚举值的数组")
    void shouldReturnArrayWithThreeValues() {
      // Given & When
      FileStatus[] values = FileStatus.values();

      // Then
      assertThat(values).hasSize(3);
    }

    @Test
    @DisplayName("应该返回包含所有枚举值的数组")
    void shouldReturnArrayContainingAllEnumValues() {
      // Given & When
      FileStatus[] values = FileStatus.values();

      // Then
      assertThat(values)
          .containsExactlyInAnyOrder(FileStatus.ACTIVE, FileStatus.EXPIRED, FileStatus.DELETED);
    }

    @Test
    @DisplayName("应该按声明顺序返回枚举值（ACTIVE, EXPIRED, DELETED）")
    void shouldReturnEnumValuesInDeclarationOrder() {
      // Given & When
      FileStatus[] values = FileStatus.values();

      // Then
      assertThat(values).containsExactly(FileStatus.ACTIVE, FileStatus.EXPIRED, FileStatus.DELETED);
    }

    @Test
    @DisplayName("values() 应该每次返回新数组（防御性拷贝）")
    void valuesShouldReturnNewArrayEachTime() {
      // Given
      FileStatus[] values1 = FileStatus.values();
      FileStatus[] values2 = FileStatus.values();

      // When - 修改第一个数组
      values1[0] = FileStatus.DELETED;

      // Then - 第二个数组应该不受影响
      assertThat(values2[0]).isEqualTo(FileStatus.ACTIVE);
      assertThat(values1).isNotSameAs(values2); // 不是同一个数组对象
    }

    @Test
    @DisplayName("values() 应该返回不可变的枚举值内容")
    void valuesShouldReturnImmutableEnumContent() {
      // Given
      FileStatus[] values = FileStatus.values();
      FileStatus originalFirstValue = values[0];

      // When - 尝试修改数组元素（虽然数组本身可修改，但枚举值不可变）
      values[0] = FileStatus.DELETED;

      // Then - 原始枚举常量应该保持不变
      assertThat(FileStatus.ACTIVE).isEqualTo(originalFirstValue);
      assertThat(FileStatus.values()[0]).isEqualTo(FileStatus.ACTIVE);
    }
  }

  // ========== valueOf() 方法测试 ==========

  @Nested
  @DisplayName("valueOf() 方法")
  class ValueOfMethodTests {

    @Test
    @DisplayName("应该通过字符串 'ACTIVE' 获取 ACTIVE 枚举值")
    void shouldGetActiveEnumFromStringActive() {
      // Given
      String statusName = "ACTIVE";

      // When
      FileStatus status = FileStatus.valueOf(statusName);

      // Then
      assertThat(status).isEqualTo(FileStatus.ACTIVE);
    }

    @Test
    @DisplayName("应该通过字符串 'EXPIRED' 获取 EXPIRED 枚举值")
    void shouldGetExpiredEnumFromStringExpired() {
      // Given
      String statusName = "EXPIRED";

      // When
      FileStatus status = FileStatus.valueOf(statusName);

      // Then
      assertThat(status).isEqualTo(FileStatus.EXPIRED);
    }

    @Test
    @DisplayName("应该通过字符串 'DELETED' 获取 DELETED 枚举值")
    void shouldGetDeletedEnumFromStringDeleted() {
      // Given
      String statusName = "DELETED";

      // When
      FileStatus status = FileStatus.valueOf(statusName);

      // Then
      assertThat(status).isEqualTo(FileStatus.DELETED);
    }

    @Test
    @DisplayName("应该抛出 IllegalArgumentException 当传入无效字符串")
    void shouldThrowIllegalArgumentExceptionForInvalidString() {
      // Given
      String invalidName = "INVALID_STATUS";

      // When & Then
      assertThatThrownBy(() -> FileStatus.valueOf(invalidName))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("No enum constant");
    }

    @Test
    @DisplayName("应该抛出 IllegalArgumentException 当传入空字符串")
    void shouldThrowIllegalArgumentExceptionForEmptyString() {
      // Given
      String emptyName = "";

      // When & Then
      assertThatThrownBy(() -> FileStatus.valueOf(emptyName))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("应该抛出 NullPointerException 当传入 null")
    void shouldThrowNullPointerExceptionForNull() {
      // Given
      String nullName = null;

      // When & Then
      assertThatThrownBy(() -> FileStatus.valueOf(nullName))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("应该区分大小写（小写字符串应失败）")
    void shouldBeCaseSensitive() {
      // Given
      String lowerCaseName = "active";

      // When & Then
      assertThatThrownBy(() -> FileStatus.valueOf(lowerCaseName))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("应该区分大小写（混合大小写字符串应失败）")
    void shouldBeCaseSensitiveForMixedCase() {
      // Given
      String mixedCaseName = "Active";

      // When & Then
      assertThatThrownBy(() -> FileStatus.valueOf(mixedCaseName))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("应该拒绝包含前后空格的字符串")
    void shouldRejectStringWithLeadingOrTrailingSpaces() {
      // Given
      String nameWithSpaces = " ACTIVE ";

      // When & Then
      assertThatThrownBy(() -> FileStatus.valueOf(nameWithSpaces))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  // ========== name() 方法测试 ==========

  @Nested
  @DisplayName("name() 方法")
  class NameMethodTests {

    @Test
    @DisplayName("ACTIVE.name() 应该返回 'ACTIVE'")
    void activeNameShouldReturnActive() {
      // Given
      FileStatus status = FileStatus.ACTIVE;

      // When
      String name = status.name();

      // Then
      assertThat(name).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("EXPIRED.name() 应该返回 'EXPIRED'")
    void expiredNameShouldReturnExpired() {
      // Given
      FileStatus status = FileStatus.EXPIRED;

      // When
      String name = status.name();

      // Then
      assertThat(name).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("DELETED.name() 应该返回 'DELETED'")
    void deletedNameShouldReturnDeleted() {
      // Given
      FileStatus status = FileStatus.DELETED;

      // When
      String name = status.name();

      // Then
      assertThat(name).isEqualTo("DELETED");
    }

    @Test
    @DisplayName("name() 应该与 valueOf() 兼容（往返转换）")
    void nameShouldBeCompatibleWithValueOf() {
      // Given
      FileStatus originalStatus = FileStatus.ACTIVE;

      // When - 通过 name() 获取字符串，再通过 valueOf() 转回枚举
      String name = originalStatus.name();
      FileStatus reconstructedStatus = FileStatus.valueOf(name);

      // Then
      assertThat(reconstructedStatus).isEqualTo(originalStatus);
    }

    @Test
    @DisplayName("name() 应该始终返回相同的字符串（幂等性）")
    void nameShouldAlwaysReturnSameString() {
      // Given
      FileStatus status = FileStatus.ACTIVE;

      // When
      String name1 = status.name();
      String name2 = status.name();
      String name3 = status.name();

      // Then - 幂等性
      assertThat(name1).isEqualTo(name2);
      assertThat(name2).isEqualTo(name3);
      assertThat(name1).isSameAs(name2); // 字符串常量应该是同一个对象
    }
  }

  // ========== ordinal() 方法测试 ==========

  @Nested
  @DisplayName("ordinal() 方法")
  class OrdinalMethodTests {

    @Test
    @DisplayName("ACTIVE.ordinal() 应该返回 0")
    void activeOrdinalShouldBeZero() {
      // Given
      FileStatus status = FileStatus.ACTIVE;

      // When
      int ordinal = status.ordinal();

      // Then
      assertThat(ordinal).isEqualTo(0);
    }

    @Test
    @DisplayName("EXPIRED.ordinal() 应该返回 1")
    void expiredOrdinalShouldBeOne() {
      // Given
      FileStatus status = FileStatus.EXPIRED;

      // When
      int ordinal = status.ordinal();

      // Then
      assertThat(ordinal).isEqualTo(1);
    }

    @Test
    @DisplayName("DELETED.ordinal() 应该返回 2")
    void deletedOrdinalShouldBeTwo() {
      // Given
      FileStatus status = FileStatus.DELETED;

      // When
      int ordinal = status.ordinal();

      // Then
      assertThat(ordinal).isEqualTo(2);
    }

    @Test
    @DisplayName("ordinal() 应该按声明顺序递增")
    void ordinalShouldIncreaseInDeclarationOrder() {
      // Given & When
      int activeOrdinal = FileStatus.ACTIVE.ordinal();
      int expiredOrdinal = FileStatus.EXPIRED.ordinal();
      int deletedOrdinal = FileStatus.DELETED.ordinal();

      // Then
      assertThat(activeOrdinal).isLessThan(expiredOrdinal);
      assertThat(expiredOrdinal).isLessThan(deletedOrdinal);
      assertThat(deletedOrdinal - activeOrdinal).isEqualTo(2);
    }

    @Test
    @DisplayName("ordinal() 应该从 0 开始连续递增")
    void ordinalShouldStartFromZeroAndBeContiguous() {
      // Given
      FileStatus[] values = FileStatus.values();

      // When & Then - 验证序号从 0 开始且连续
      for (int i = 0; i < values.length; i++) {
        assertThat(values[i].ordinal()).isEqualTo(i);
      }
    }

    @Test
    @DisplayName("ordinal() 应该始终返回相同的值（幂等性）")
    void ordinalShouldAlwaysReturnSameValue() {
      // Given
      FileStatus status = FileStatus.ACTIVE;

      // When
      int ordinal1 = status.ordinal();
      int ordinal2 = status.ordinal();
      int ordinal3 = status.ordinal();

      // Then - 幂等性
      assertThat(ordinal1).isEqualTo(ordinal2);
      assertThat(ordinal2).isEqualTo(ordinal3);
    }
  }

  // ========== 枚举语义测试（equals、==、单例） ==========

  @Nested
  @DisplayName("枚举语义（equals、==、单例）")
  class EnumSemanticsTests {

    @Test
    @DisplayName("应该支持使用 == 比较枚举值")
    void shouldSupportIdentityComparisonWithDoubleEquals() {
      // Given
      FileStatus status1 = FileStatus.ACTIVE;
      FileStatus status2 = FileStatus.ACTIVE;

      // When
      boolean isSame = (status1 == status2);

      // Then
      assertThat(isSame).isTrue();
      assertThat(status1).isSameAs(status2); // AssertJ 验证
    }

    @Test
    @DisplayName("应该支持使用 == 比较不同枚举值")
    void shouldSupportIdentityComparisonForDifferentValues() {
      // Given
      FileStatus active = FileStatus.ACTIVE;
      FileStatus expired = FileStatus.EXPIRED;

      // When
      boolean isSame = (active == expired);

      // Then
      assertThat(isSame).isFalse();
      assertThat(active).isNotSameAs(expired);
    }

    @Test
    @DisplayName("应该支持使用 equals() 比较枚举值")
    void shouldSupportEqualsComparison() {
      // Given
      FileStatus status1 = FileStatus.ACTIVE;
      FileStatus status2 = FileStatus.ACTIVE;

      // When
      boolean isEqual = status1.equals(status2);

      // Then
      assertThat(isEqual).isTrue();
      assertThat(status1).isEqualTo(status2);
    }

    @Test
    @DisplayName("应该支持使用 equals() 比较不同枚举值")
    void shouldSupportEqualsComparisonForDifferentValues() {
      // Given
      FileStatus active = FileStatus.ACTIVE;
      FileStatus deleted = FileStatus.DELETED;

      // When
      boolean isEqual = active.equals(deleted);

      // Then
      assertThat(isEqual).isFalse();
      assertThat(active).isNotEqualTo(deleted);
    }

    @Test
    @DisplayName("equals() 应该正确处理 null")
    void equalsShouldHandleNull() {
      // Given
      FileStatus status = FileStatus.ACTIVE;

      // When
      boolean isEqual = status.equals(null);

      // Then
      assertThat(isEqual).isFalse();
      assertThat(status).isNotEqualTo(null);
    }

    @Test
    @DisplayName("equals() 应该正确处理不同类型对象")
    void equalsShouldHandleDifferentType() {
      // Given
      FileStatus status = FileStatus.ACTIVE;
      Object other = "ACTIVE";

      // When
      boolean isEqual = status.equals(other);

      // Then
      assertThat(isEqual).isFalse();
      assertThat(status).isNotEqualTo(other);
    }

    @Test
    @DisplayName("枚举值应该是单例（同一枚举值始终是同一对象）")
    void enumValueShouldBeSingleton() {
      // Given
      FileStatus status1 = FileStatus.ACTIVE;
      FileStatus status2 = FileStatus.valueOf("ACTIVE");
      FileStatus status3 = FileStatus.values()[0];

      // When & Then - 所有引用应该指向同一对象
      assertThat(status1).isSameAs(status2);
      assertThat(status2).isSameAs(status3);
      assertThat(status1).isSameAs(status3);
    }

    @Test
    @DisplayName("枚举值应该实现自反性（x.equals(x) 应该为 true）")
    void enumValueShouldImplementReflexivity() {
      // Given
      FileStatus status = FileStatus.ACTIVE;

      // When
      boolean isEqual = status.equals(status);

      // Then
      assertThat(isEqual).isTrue();
      assertThat(status).isEqualTo(status);
    }

    @Test
    @DisplayName("枚举值应该实现对称性（x.equals(y) == y.equals(x)）")
    void enumValueShouldImplementSymmetry() {
      // Given
      FileStatus status1 = FileStatus.ACTIVE;
      FileStatus status2 = FileStatus.ACTIVE;

      // When
      boolean equals1 = status1.equals(status2);
      boolean equals2 = status2.equals(status1);

      // Then
      assertThat(equals1).isEqualTo(equals2);
      assertThat(status1).isEqualTo(status2);
      assertThat(status2).isEqualTo(status1);
    }

    @Test
    @DisplayName("枚举值应该实现传递性（x.equals(y) && y.equals(z) => x.equals(z)）")
    void enumValueShouldImplementTransitivity() {
      // Given
      FileStatus status1 = FileStatus.ACTIVE;
      FileStatus status2 = FileStatus.valueOf("ACTIVE");
      FileStatus status3 = FileStatus.values()[0];

      // When & Then - 如果 x == y 且 y == z，则 x == z
      assertThat(status1).isEqualTo(status2);
      assertThat(status2).isEqualTo(status3);
      assertThat(status1).isEqualTo(status3);
    }

    @Test
    @DisplayName("相同枚举值应该有相同的 hashCode")
    void sameEnumValueShouldHaveSameHashCode() {
      // Given
      FileStatus status1 = FileStatus.ACTIVE;
      FileStatus status2 = FileStatus.ACTIVE;

      // When
      int hashCode1 = status1.hashCode();
      int hashCode2 = status2.hashCode();

      // Then - 相等的对象必须有相同的 hashCode
      assertThat(hashCode1).isEqualTo(hashCode2);
    }

    @Test
    @DisplayName("hashCode() 应该始终返回相同的值（幂等性）")
    void hashCodeShouldAlwaysReturnSameValue() {
      // Given
      FileStatus status = FileStatus.ACTIVE;

      // When
      int hashCode1 = status.hashCode();
      int hashCode2 = status.hashCode();
      int hashCode3 = status.hashCode();

      // Then
      assertThat(hashCode1).isEqualTo(hashCode2);
      assertThat(hashCode2).isEqualTo(hashCode3);
    }
  }

  // ========== 枚举不变性测试 ==========

  @Nested
  @DisplayName("枚举不变性")
  class EnumImmutabilityTests {

    @Test
    @DisplayName("枚举值应该是不可变的常量")
    void enumValueShouldBeImmutableConstant() {
      // Given
      FileStatus originalStatus = FileStatus.ACTIVE;

      // When - 多次访问枚举值
      FileStatus accessedStatus1 = FileStatus.ACTIVE;
      FileStatus accessedStatus2 = FileStatus.ACTIVE;

      // Then - 应该始终返回同一对象
      assertThat(accessedStatus1).isSameAs(originalStatus);
      assertThat(accessedStatus2).isSameAs(originalStatus);
    }

    @Test
    @DisplayName("枚举值应该线程安全（多线程访问返回同一对象）")
    void enumValueShouldBeThreadSafe() throws InterruptedException {
      // Given
      final FileStatus[] capturedStatuses = new FileStatus[2];

      // When - 在多个线程中访问枚举值
      Thread thread1 =
          new Thread(
              () -> {
                capturedStatuses[0] = FileStatus.ACTIVE;
              });
      Thread thread2 =
          new Thread(
              () -> {
                capturedStatuses[1] = FileStatus.ACTIVE;
              });

      thread1.start();
      thread2.start();
      thread1.join();
      thread2.join();

      // Then - 两个线程应该获取到同一对象
      assertThat(capturedStatuses[0]).isSameAs(capturedStatuses[1]);
    }

    @Test
    @DisplayName("values() 返回的数组修改不应影响枚举本身")
    void modifyingValuesArrayShouldNotAffectEnum() {
      // Given
      FileStatus[] values = FileStatus.values();
      FileStatus originalFirstValue = values[0];

      // When - 修改返回的数组
      values[0] = FileStatus.DELETED;

      // Then - 枚举本身应该不受影响
      assertThat(FileStatus.ACTIVE).isEqualTo(originalFirstValue);
      assertThat(FileStatus.values()[0]).isEqualTo(FileStatus.ACTIVE);
    }
  }

  // ========== 真实场景集成测试 ==========

  @Nested
  @DisplayName("真实场景集成测试")
  class RealWorldScenarioTests {

    @Test
    @DisplayName("应该正确表示活跃文件状态")
    void shouldRepresentActiveFileStatus() {
      // Given - 文件刚上传，处于活跃状态
      FileStatus status = FileStatus.ACTIVE;

      // When & Then
      assertThat(status).isEqualTo(FileStatus.ACTIVE);
      assertThat(status.name()).isEqualTo("ACTIVE");
      assertThat(status.ordinal()).isEqualTo(0);
    }

    @Test
    @DisplayName("应该正确表示过期文件状态")
    void shouldRepresentExpiredFileStatus() {
      // Given - 文件超过保留期限
      FileStatus status = FileStatus.EXPIRED;

      // When & Then
      assertThat(status).isEqualTo(FileStatus.EXPIRED);
      assertThat(status.name()).isEqualTo("EXPIRED");
      assertThat(status.ordinal()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该正确表示已删除文件状态")
    void shouldRepresentDeletedFileStatus() {
      // Given - 文件已被软删除
      FileStatus status = FileStatus.DELETED;

      // When & Then
      assertThat(status).isEqualTo(FileStatus.DELETED);
      assertThat(status.name()).isEqualTo("DELETED");
      assertThat(status.ordinal()).isEqualTo(2);
    }

    @Test
    @DisplayName("应该支持文件状态转换场景（活跃 -> 过期）")
    void shouldSupportStatusTransitionFromActiveToExpired() {
      // Given - 文件初始状态为活跃
      FileStatus currentStatus = FileStatus.ACTIVE;

      // When - 文件过期，状态转换
      FileStatus newStatus = FileStatus.EXPIRED;

      // Then
      assertThat(currentStatus).isNotEqualTo(newStatus);
      assertThat(newStatus).isEqualTo(FileStatus.EXPIRED);
    }

    @Test
    @DisplayName("应该支持文件状态转换场景（活跃 -> 删除）")
    void shouldSupportStatusTransitionFromActiveToDeleted() {
      // Given - 文件初始状态为活跃
      FileStatus currentStatus = FileStatus.ACTIVE;

      // When - 文件被删除，状态转换
      FileStatus newStatus = FileStatus.DELETED;

      // Then
      assertThat(currentStatus).isNotEqualTo(newStatus);
      assertThat(newStatus).isEqualTo(FileStatus.DELETED);
    }

    @Test
    @DisplayName("应该支持文件状态检查场景（是否为活跃状态）")
    void shouldSupportCheckingIfStatusIsActive() {
      // Given - 检查文件是否处于活跃状态
      FileStatus status = FileStatus.ACTIVE;

      // When
      boolean isActive = (status == FileStatus.ACTIVE);

      // Then
      assertThat(isActive).isTrue();
    }

    @Test
    @DisplayName("应该支持文件状态检查场景（是否需要清理）")
    void shouldSupportCheckingIfStatusRequiresCleanup() {
      // Given - 检查文件是否需要清理（过期或已删除）
      FileStatus expiredStatus = FileStatus.EXPIRED;
      FileStatus deletedStatus = FileStatus.DELETED;
      FileStatus activeStatus = FileStatus.ACTIVE;

      // When
      boolean expiredRequiresCleanup =
          (expiredStatus == FileStatus.EXPIRED || expiredStatus == FileStatus.DELETED);
      boolean deletedRequiresCleanup =
          (deletedStatus == FileStatus.EXPIRED || deletedStatus == FileStatus.DELETED);
      boolean activeRequiresCleanup =
          (activeStatus == FileStatus.EXPIRED || activeStatus == FileStatus.DELETED);

      // Then
      assertThat(expiredRequiresCleanup).isTrue();
      assertThat(deletedRequiresCleanup).isTrue();
      assertThat(activeRequiresCleanup).isFalse();
    }

    @Test
    @DisplayName("应该支持基于状态的业务逻辑判断")
    void shouldSupportBusinessLogicBasedOnStatus() {
      // Given - 根据文件状态执行不同的业务逻辑
      FileStatus status = FileStatus.ACTIVE;

      // When - 使用 switch 表达式（Java 14+）
      String action =
          switch (status) {
            case ACTIVE -> "文件可用";
            case EXPIRED -> "文件已过期，需要清理";
            case DELETED -> "文件已删除，可以移除";
          };

      // Then
      assertThat(action).isEqualTo("文件可用");
    }

    @Test
    @DisplayName("应该支持状态序列化场景（枚举 -> 字符串）")
    void shouldSupportStatusSerializationToString() {
      // Given - 需要将枚举序列化为字符串存储到数据库
      FileStatus status = FileStatus.ACTIVE;

      // When
      String serialized = status.name();

      // Then
      assertThat(serialized).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("应该支持状态反序列化场景（字符串 -> 枚举）")
    void shouldSupportStatusDeserializationFromString() {
      // Given - 从数据库读取字符串，需要反序列化为枚举
      String serialized = "EXPIRED";

      // When
      FileStatus status = FileStatus.valueOf(serialized);

      // Then
      assertThat(status).isEqualTo(FileStatus.EXPIRED);
    }

    @Test
    @DisplayName("应该支持完整的序列化-反序列化往返转换")
    void shouldSupportCompleteSerializationDeserialization() {
      // Given - 完整的往返转换场景
      FileStatus originalStatus = FileStatus.DELETED;

      // When - 序列化 -> 反序列化
      String serialized = originalStatus.name();
      FileStatus deserialized = FileStatus.valueOf(serialized);

      // Then - 应该恢复为原始枚举值
      assertThat(deserialized).isEqualTo(originalStatus);
      assertThat(deserialized).isSameAs(originalStatus); // 单例保证
    }
  }
}
