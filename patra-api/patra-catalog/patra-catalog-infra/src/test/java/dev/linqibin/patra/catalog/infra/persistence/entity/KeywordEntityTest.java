package dev.linqibin.patra.catalog.infra.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// KeywordEntity 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("KeywordEntity")
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
  @DisplayName("of 在 term 超长时应截断到 500 码点")
  void should_cap_term_length_when_input_exceeds_max() {
    // given
    String longTerm = "K".repeat(620);

    // when
    KeywordEntity entity = KeywordEntity.of(longTerm, "MeSH", "eng");

    // then
    assertThat(entity.getTerm().codePointCount(0, entity.getTerm().length())).isEqualTo(500);
  }

  @Test
  @DisplayName("of 在 term 未超长时应保留原始值")
  void should_keep_original_term_when_within_limit() {
    // given
    String normalTerm = "Cardiovascular Diseases";

    // when
    KeywordEntity entity = KeywordEntity.of(normalTerm, "MeSH", "eng");

    // then
    assertThat(entity.getTerm()).isEqualTo(normalTerm);
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
