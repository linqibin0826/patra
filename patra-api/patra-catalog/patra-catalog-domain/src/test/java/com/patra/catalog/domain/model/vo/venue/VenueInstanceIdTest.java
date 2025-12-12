package com.patra.catalog.domain.model.vo.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// VenueInstanceId 值对象单元测试。
///
/// @author Patra Lin
/// @since 0.6.0
@DisplayName("VenueInstanceId 值对象")
class VenueInstanceIdTest {

  @Test
  @DisplayName("应正确创建 VenueInstanceId")
  void shouldCreateVenueInstanceId() {
    VenueInstanceId id = VenueInstanceId.of(12345L);

    assertThat(id.value()).isEqualTo(12345L);
    assertThat(id.toString()).isEqualTo("12345");
  }

  @Test
  @DisplayName("null 值应抛出异常")
  void shouldThrowWhenValueIsNull() {
    assertThatThrownBy(() -> VenueInstanceId.of(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不能为空");
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1, -100})
  @DisplayName("非正整数应抛出异常")
  void shouldThrowWhenValueIsNotPositive(long value) {
    assertThatThrownBy(() -> VenueInstanceId.of(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("必须为正整数");
  }

  @Test
  @DisplayName("相同值的 VenueInstanceId 应相等")
  void shouldBeEqualWhenValuesSame() {
    VenueInstanceId id1 = VenueInstanceId.of(123L);
    VenueInstanceId id2 = VenueInstanceId.of(123L);

    assertThat(id1).isEqualTo(id2);
    assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
  }
}
