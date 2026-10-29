package dev.patra.identity.domain.account.model.vo.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Bio 单元测试：换行归一、行内空白折叠、首尾空行。
@DisplayName("Bio 单元测试")
class BioTest {

  private static final String IDEOGRAPHIC_SPACE = new String(Character.toChars(0x3000));

  @Test
  void should_normalize_line_breaks_and_spaces() {
    String raw = "  第一行\r\n第二行" + IDEOGRAPHIC_SPACE + IDEOGRAPHIC_SPACE + "尾  \r第三行\n\n";

    assertThat(Bio.normalize(raw)).isEqualTo("第一行\n第二行 尾\n第三行");
  }

  @Test
  void should_keep_inner_blank_lines() {
    assertThat(Bio.normalize("a\n\nb")).isEqualTo("a\n\nb");
  }

  @Test
  void should_strip_leading_blank_lines() {
    assertThat(Bio.normalize("\n \n a")).isEqualTo("a");
  }

  @Test
  void should_reject_unnormalized_value() {
    assertThatThrownBy(() -> new Bio(" a")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Bio("")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_accept_normalized_value() {
    assertThat(new Bio("a\nb").value()).isEqualTo("a\nb");
  }
}
