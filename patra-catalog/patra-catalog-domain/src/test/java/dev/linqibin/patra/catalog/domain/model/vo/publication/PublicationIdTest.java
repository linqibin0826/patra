package dev.linqibin.patra.catalog.domain.model.vo.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// PublicationId 值对象单元测试。
///
/// @author Patra Lin
/// @since 0.6.0
@DisplayName("PublicationId 值对象")
class PublicationIdTest {

  @Test
  @DisplayName("应正确创建 PublicationId")
  void shouldCreatePublicationId() {
    PublicationId id = PublicationId.of(12345L);

    assertThat(id.value()).isEqualTo(12345L);
    assertThat(id.toString()).isEqualTo("12345");
  }

  @Test
  @DisplayName("null 值应抛出异常")
  void shouldThrowWhenValueIsNull() {
    assertThatThrownBy(() -> PublicationId.of(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不能为空");
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1, -100})
  @DisplayName("非正整数应抛出异常")
  void shouldThrowWhenValueIsNotPositive(long value) {
    assertThatThrownBy(() -> PublicationId.of(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("必须为正整数");
  }

  @Test
  @DisplayName("相同值的 PublicationId 应相等")
  void shouldBeEqualWhenValuesSame() {
    PublicationId id1 = PublicationId.of(123L);
    PublicationId id2 = PublicationId.of(123L);

    assertThat(id1).isEqualTo(id2);
    assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
  }
}
