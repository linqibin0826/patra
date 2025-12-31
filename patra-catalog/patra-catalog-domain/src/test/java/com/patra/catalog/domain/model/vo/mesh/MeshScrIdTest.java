package com.patra.catalog.domain.model.vo.mesh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// MeshScrId 值对象单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("MeshScrId 值对象测试")
class MeshScrIdTest {

  @Nested
  @DisplayName("创建测试")
  class CreationTests {

    @Test
    @DisplayName("应该成功创建有效的 MeshScrId")
    void shouldCreateValidId() {
      // when
      MeshScrId id = MeshScrId.of(12345L);

      // then
      assertThat(id.value()).isEqualTo(12345L);
    }

    @Test
    @DisplayName("应该拒绝 null 值")
    void shouldRejectNullValue() {
      assertThatThrownBy(() -> MeshScrId.of(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("不能为空");
    }

    @Test
    @DisplayName("应该拒绝零值")
    void shouldRejectZeroValue() {
      assertThatThrownBy(() -> MeshScrId.of(0L))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("必须为正整数");
    }

    @Test
    @DisplayName("应该拒绝负数")
    void shouldRejectNegativeValue() {
      assertThatThrownBy(() -> MeshScrId.of(-1L))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("必须为正整数");
    }
  }

  @Nested
  @DisplayName("相等性测试")
  class EqualityTests {

    @Test
    @DisplayName("相同值的 ID 应该相等")
    void shouldBeEqualForSameValue() {
      // given
      MeshScrId id1 = MeshScrId.of(12345L);
      MeshScrId id2 = MeshScrId.of(12345L);

      // then
      assertThat(id1).isEqualTo(id2);
      assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }

    @Test
    @DisplayName("不同值的 ID 应该不相等")
    void shouldNotBeEqualForDifferentValue() {
      // given
      MeshScrId id1 = MeshScrId.of(12345L);
      MeshScrId id2 = MeshScrId.of(67890L);

      // then
      assertThat(id1).isNotEqualTo(id2);
    }
  }

  @Nested
  @DisplayName("toString 测试")
  class ToStringTests {

    @Test
    @DisplayName("toString 应该返回数值字符串")
    void shouldReturnValueAsString() {
      // given
      MeshScrId id = MeshScrId.of(12345L);

      // then
      assertThat(id.toString()).isEqualTo("12345");
    }
  }
}
