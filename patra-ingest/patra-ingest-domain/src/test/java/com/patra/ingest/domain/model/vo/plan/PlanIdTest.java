package com.patra.ingest.domain.model.vo.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// PlanId 值对象单元测试。
///
/// @author Patra Lin
/// @since 0.6.0
@DisplayName("PlanId 值对象")
class PlanIdTest {

  @Test
  @DisplayName("应正确创建 PlanId")
  void shouldCreatePlanId() {
    PlanId id = PlanId.of(12345L);

    assertThat(id.value()).isEqualTo(12345L);
    assertThat(id.toString()).isEqualTo("12345");
  }

  @Test
  @DisplayName("null 值应抛出异常")
  void shouldThrowWhenValueIsNull() {
    assertThatThrownBy(() -> PlanId.of(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不能为空");
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1, -100})
  @DisplayName("非正整数应抛出异常")
  void shouldThrowWhenValueIsNotPositive(long value) {
    assertThatThrownBy(() -> PlanId.of(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("必须为正整数");
  }

  @Test
  @DisplayName("相同值的 PlanId 应相等")
  void shouldBeEqualWhenValuesSame() {
    PlanId id1 = PlanId.of(123L);
    PlanId id2 = PlanId.of(123L);

    assertThat(id1).isEqualTo(id2);
    assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
  }
}
