package com.patra.catalog.domain.model.valueobject;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// MeshImportId 单元测试。
/// 
/// 测试策略：
/// 
/// - 测试正常创建：使用有效的正数 ID
///   - 测试验证逻辑：null 值、非正数
///   - 测试 Record 特性：equals、hashCode、toString
///   - 测试不可变性：Record 自动保证
/// 
/// 测试场景：
/// 
/// - ✅ 使用有效 Long 值创建 → 成功
///   - ✅ ID 值相同的实例 → equals 返回 true
///   - ✅ toString() → 返回 ID 字符串
///   - ✅ null 值 → 抛出 IllegalArgumentException
///   - ✅ 零值 → 抛出 IllegalArgumentException
///   - ✅ 负数 → 抛出 IllegalArgumentException
/// 
/// @author Patra Team
/// @since 0.2.0
@DisplayName("MeshImportId 单元测试")
class MeshImportIdTest {

  @Test
  @DisplayName("使用有效Long值创建 - 应该成功")
  void of_whenValidLong_shouldCreateSuccessfully() {
    // Given: 有效的雪花 ID
    Long validId = 1734567890123456789L;

    // When: 创建 MeshImportId
    MeshImportId importId = MeshImportId.of(validId);

    // Then: 成功创建且值正确
    assertThat(importId).isNotNull();
    assertThat(importId.value()).isEqualTo(validId);
  }

  @Test
  @DisplayName("值相同的实例 - equals应该返回true")
  void equals_whenSameValue_shouldReturnTrue() {
    // Given: 两个值相同的 ID
    Long idValue = 1734567890123456789L;
    MeshImportId id1 = MeshImportId.of(idValue);
    MeshImportId id2 = MeshImportId.of(idValue);

    // When & Then: equals 应该返回 true
    assertThat(id1).isEqualTo(id2);
    assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
  }

  @Test
  @DisplayName("值不同的实例 - equals应该返回false")
  void equals_whenDifferentValue_shouldReturnFalse() {
    // Given: 两个值不同的 ID
    MeshImportId id1 = MeshImportId.of(1L);
    MeshImportId id2 = MeshImportId.of(2L);

    // When & Then: equals 应该返回 false
    assertThat(id1).isNotEqualTo(id2);
  }

  @Test
  @DisplayName("toString() - 应该返回ID字符串")
  void toString_shouldReturnIdString() {
    // Given: 一个 MeshImportId
    Long idValue = 1734567890123456789L;
    MeshImportId importId = MeshImportId.of(idValue);

    // When: 调用 toString()
    String result = importId.toString();

    // Then: 返回 ID 的字符串形式
    assertThat(result).isEqualTo("1734567890123456789");
  }

  @Test
  @DisplayName("null值 - 应该抛出IllegalArgumentException")
  void of_whenNull_shouldThrowException() {
    // Given: null 值
    Long nullValue = null;

    // When & Then: 应该抛出 IllegalArgumentException
    assertThatThrownBy(() -> MeshImportId.of(nullValue))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MeSH 导入任务 ID 不能为 null");
  }

  @Test
  @DisplayName("零值 - 应该抛出IllegalArgumentException")
  void of_whenZero_shouldThrowException() {
    // Given: 零值
    Long zeroValue = 0L;

    // When & Then: 应该抛出 IllegalArgumentException
    assertThatThrownBy(() -> MeshImportId.of(zeroValue))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MeSH 导入任务 ID 必须为正数");
  }

  @Test
  @DisplayName("负数 - 应该抛出IllegalArgumentException")
  void of_whenNegative_shouldThrowException() {
    // Given: 负数
    Long negativeValue = -1L;

    // When & Then: 应该抛出 IllegalArgumentException
    assertThatThrownBy(() -> MeshImportId.of(negativeValue))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MeSH 导入任务 ID 必须为正数");
  }

  @Test
  @DisplayName("Record不可变性 - 值对象应该是不可变的")
  void immutability_shouldBeImmutable() {
    // Given: 一个 MeshImportId
    Long originalValue = 123456L;
    MeshImportId importId = MeshImportId.of(originalValue);

    // When: 获取 value（Record 自动生成的 getter）
    Long retrievedValue = importId.value();

    // Then: 值应该相同（Record 保证不可变性）
    assertThat(retrievedValue).isEqualTo(originalValue);

    // 验证：value() 方法返回的是 final 的，无法修改
    // (Record 的所有字段都是 final 的，这是由 Java 编译器保证的)
    assertThat(importId).hasFieldOrProperty("value");
  }
}
