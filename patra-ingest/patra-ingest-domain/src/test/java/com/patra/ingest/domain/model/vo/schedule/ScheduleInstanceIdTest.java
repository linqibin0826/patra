package com.patra.ingest.domain.model.vo.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// ScheduleInstanceId 值对象单元测试。
///
/// @author Patra Lin
/// @since 0.6.0
@DisplayName("ScheduleInstanceId 值对象")
class ScheduleInstanceIdTest {

  @Test
  @DisplayName("应正确创建 ScheduleInstanceId")
  void shouldCreateScheduleInstanceId() {
    ScheduleInstanceId id = ScheduleInstanceId.of(12345L);

    assertThat(id.value()).isEqualTo(12345L);
    assertThat(id.toString()).isEqualTo("12345");
  }

  @Test
  @DisplayName("null 值应抛出异常")
  void shouldThrowWhenValueIsNull() {
    assertThatThrownBy(() -> ScheduleInstanceId.of(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不能为空");
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1, -100})
  @DisplayName("非正整数应抛出异常")
  void shouldThrowWhenValueIsNotPositive(long value) {
    assertThatThrownBy(() -> ScheduleInstanceId.of(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("必须为正整数");
  }

  @Test
  @DisplayName("相同值的 ScheduleInstanceId 应相等")
  void shouldBeEqualWhenValuesSame() {
    ScheduleInstanceId id1 = ScheduleInstanceId.of(123L);
    ScheduleInstanceId id2 = ScheduleInstanceId.of(123L);

    assertThat(id1).isEqualTo(id2);
    assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
  }
}
