package dev.linqibin.patra.ingest.domain.model.vo.slice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// PlanSliceId 值对象单元测试。
///
/// @author Patra Lin
/// @since 0.6.0
@DisplayName("PlanSliceId 值对象")
class PlanSliceIdTest {

  @Test
  @DisplayName("应正确创建 PlanSliceId")
  void shouldCreatePlanSliceId() {
    PlanSliceId id = PlanSliceId.of(12345L);

    assertThat(id.value()).isEqualTo(12345L);
    assertThat(id.toString()).isEqualTo("12345");
  }

  @Test
  @DisplayName("null 值应抛出异常")
  void shouldThrowWhenValueIsNull() {
    assertThatThrownBy(() -> PlanSliceId.of(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不能为空");
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1, -100})
  @DisplayName("非正整数应抛出异常")
  void shouldThrowWhenValueIsNotPositive(long value) {
    assertThatThrownBy(() -> PlanSliceId.of(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("必须为正整数");
  }

  @Test
  @DisplayName("相同值的 PlanSliceId 应相等")
  void shouldBeEqualWhenValuesSame() {
    PlanSliceId id1 = PlanSliceId.of(123L);
    PlanSliceId id2 = PlanSliceId.of(123L);

    assertThat(id1).isEqualTo(id2);
    assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
  }
}
