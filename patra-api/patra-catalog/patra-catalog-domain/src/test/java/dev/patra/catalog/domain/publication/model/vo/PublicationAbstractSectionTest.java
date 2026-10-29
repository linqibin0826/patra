package dev.patra.catalog.domain.publication.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// PublicationAbstractSection 值对象单元测试。
///
/// @author linqibin
/// @since 0.6.0
@DisplayName("PublicationAbstractSection 值对象")
class PublicationAbstractSectionTest {

  @Test
  @DisplayName("带标签段落应保留标签")
  void of_labeledSection_shouldKeepLabel() {
    PublicationAbstractSection section = PublicationAbstractSection.of("METHODS", "some text");

    assertThat(section.label()).isEqualTo("METHODS");
    assertThat(section.hasLabel()).isTrue();
  }

  @Test
  @DisplayName("空白标签应归一化为 null")
  void of_blankLabel_shouldNormalizeToNull() {
    assertThat(PublicationAbstractSection.of("  ", "t").label()).isNull();
    assertThat(PublicationAbstractSection.of("", "t").hasLabel()).isFalse();
    assertThat(PublicationAbstractSection.of(null, "t").label()).isNull();
  }

  @Test
  @DisplayName("标签首尾空白应被 trim")
  void of_labelWithWhitespace_shouldTrim() {
    assertThat(PublicationAbstractSection.of(" RESULTS ", "t").label()).isEqualTo("RESULTS");
  }

  @Test
  @DisplayName("段落内容为空白时应抛异常")
  void of_blankText_shouldThrow() {
    assertThatThrownBy(() -> PublicationAbstractSection.of("A", "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
