package com.patra.catalog.infra.adapter.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// KeywordEntity 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("KeywordEntity")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class KeywordEntityTest {

  @Test
  @DisplayName("normalize 在超长输入时应截断到 500 字符")
  void should_cap_normalized_term_length_when_input_is_too_long() {
    // given
    String raw = "A".repeat(620);

    // when
    String normalized = KeywordEntity.normalize(raw);

    // then
    assertThat(normalized).hasSize(500);
    assertThat(normalized).isEqualTo("a".repeat(500));
  }

  @Test
  @DisplayName("normalizedTerm 列长度应声明为 500")
  void should_declare_normalized_term_column_length_as_500() throws NoSuchFieldException {
    // given
    Field field = KeywordEntity.class.getDeclaredField("normalizedTerm");

    // when
    Column column = field.getAnnotation(Column.class);

    // then
    assertThat(column).isNotNull();
    assertThat(column.length()).isEqualTo(500);
  }
}
