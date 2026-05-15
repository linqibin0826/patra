package dev.linqibin.patra.catalog.domain.model.vo.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// OrganizationId 值对象单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("OrganizationId 值对象")
class OrganizationIdTest {

  @Test
  @DisplayName("应正确创建 OrganizationId")
  void shouldCreateOrganizationId() {
    OrganizationId id = OrganizationId.of(12345L);

    assertThat(id.value()).isEqualTo(12345L);
    assertThat(id.toString()).isEqualTo("12345");
  }

  @Test
  @DisplayName("null 值应抛出异常")
  void shouldThrowWhenValueIsNull() {
    assertThatThrownBy(() -> OrganizationId.of(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不能为空");
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1, -100})
  @DisplayName("非正整数应抛出异常")
  void shouldThrowWhenValueIsNotPositive(long value) {
    assertThatThrownBy(() -> OrganizationId.of(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("必须为正整数");
  }

  @Test
  @DisplayName("相同值的 OrganizationId 应相等")
  void shouldBeEqualWhenValuesSame() {
    OrganizationId id1 = OrganizationId.of(123L);
    OrganizationId id2 = OrganizationId.of(123L);

    assertThat(id1).isEqualTo(id2);
    assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
  }

  @Test
  @DisplayName("不同值的 OrganizationId 应不相等")
  void shouldNotBeEqualWhenValuesDifferent() {
    OrganizationId id1 = OrganizationId.of(123L);
    OrganizationId id2 = OrganizationId.of(456L);

    assertThat(id1).isNotEqualTo(id2);
  }
}
