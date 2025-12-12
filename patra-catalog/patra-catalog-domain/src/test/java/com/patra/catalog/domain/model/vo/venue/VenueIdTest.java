package com.patra.catalog.domain.model.vo.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// VenueId 值对象单元测试。
///
/// @author Patra Lin
/// @since 0.6.0
@DisplayName("VenueId 值对象")
class VenueIdTest {

  @Test
  @DisplayName("应正确创建 VenueId")
  void shouldCreateVenueId() {
    VenueId id = VenueId.of(12345L);

    assertThat(id.value()).isEqualTo(12345L);
    assertThat(id.toString()).isEqualTo("12345");
  }

  @Test
  @DisplayName("null 值应抛出异常")
  void shouldThrowWhenValueIsNull() {
    assertThatThrownBy(() -> VenueId.of(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不能为空");
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1, -100})
  @DisplayName("非正整数应抛出异常")
  void shouldThrowWhenValueIsNotPositive(long value) {
    assertThatThrownBy(() -> VenueId.of(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("必须为正整数");
  }

  @Test
  @DisplayName("相同值的 VenueId 应相等")
  void shouldBeEqualWhenValuesSame() {
    VenueId id1 = VenueId.of(123L);
    VenueId id2 = VenueId.of(123L);

    assertThat(id1).isEqualTo(id2);
    assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
  }

  @Test
  @DisplayName("不同值的 VenueId 应不相等")
  void shouldNotBeEqualWhenValuesDifferent() {
    VenueId id1 = VenueId.of(123L);
    VenueId id2 = VenueId.of(456L);

    assertThat(id1).isNotEqualTo(id2);
  }
}
