package com.patra.catalog.domain.model.vo.mesh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// MeshQualifierId 值对象单元测试。
///
/// @author Patra Lin
/// @since 0.6.0
@DisplayName("MeshQualifierId 值对象")
class MeshQualifierIdTest {

  @Test
  @DisplayName("应正确创建 MeshQualifierId")
  void shouldCreateMeshQualifierId() {
    MeshQualifierId id = MeshQualifierId.of(12345L);

    assertThat(id.value()).isEqualTo(12345L);
    assertThat(id.toString()).isEqualTo("12345");
  }

  @Test
  @DisplayName("null 值应抛出异常")
  void shouldThrowWhenValueIsNull() {
    assertThatThrownBy(() -> MeshQualifierId.of(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不能为空");
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1, -100})
  @DisplayName("非正整数应抛出异常")
  void shouldThrowWhenValueIsNotPositive(long value) {
    assertThatThrownBy(() -> MeshQualifierId.of(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("必须为正整数");
  }

  @Test
  @DisplayName("相同值的 MeshQualifierId 应相等")
  void shouldBeEqualWhenValuesSame() {
    MeshQualifierId id1 = MeshQualifierId.of(123L);
    MeshQualifierId id2 = MeshQualifierId.of(123L);

    assertThat(id1).isEqualTo(id2);
    assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
  }
}
