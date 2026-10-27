package dev.patra.interaction.domain.post.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.patra.tool.core.error.field.FieldViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/// 经验正文：换行统一、NFC、去首尾空白；≤ 2,000 字素且 ≤ 8,000 字节；放行换行与制表符，拒绝其余控制字符。
class PostBodyTest {

  /// 行分隔符 U+2028（源码里不写转义：格式化会把它还原成字面字符、截断字符串）。
  private static final String LINE_SEPARATOR = new String(Character.toChars(0x2028));

  private final List<FieldViolation> violations = new ArrayList<>();

  /// CRLF 与单独的 CR 都换成 LF，首尾空白去掉，组合字符 NFC。
  @Test
  void should_normalize_line_endings_nfc_and_trim() {
    assertThat(PostBody.parse(" a\r\nb\rcé 　", violations))
        .get()
        .extracting(PostBody::value)
        .isEqualTo("a\nb\ncé");
    assertThat(violations).isEmpty();
  }

  /// 空白正文等于没有正文，不算错。
  @Test
  void should_treat_blank_as_absent() {
    assertThat(PostBody.parse(" \n\t ", violations)).isEmpty();
    assertThat(PostBody.parse(null, violations)).isEmpty();
    assertThat(violations).isEmpty();
  }

  /// 2,000 个汉字可以，2,001 个超长。
  @Test
  void should_accept_2000_graphemes_and_reject_2001() {
    assertThat(PostBody.parse("字".repeat(2000), violations)).isPresent();
    assertThat(violations).isEmpty();

    assertThat(PostBody.parse("字".repeat(2001), violations)).isEmpty();
    assertViolation("TOO_LONG");
  }

  /// 字素没超但字节超了 8,000：一个字素塞几个组合符（NFC 后每个字素 10 字节，1,000 个是 10,000 字节）。
  @Test
  void should_reject_more_than_8000_bytes() {
    Optional<PostBody> body = PostBody.parse(("e" + "́".repeat(5)).repeat(1000), violations);

    assertThat(body).isEmpty();
    assertViolation("TOO_LONG");
  }

  /// Markdown 里的换行、缩进用的制表符都留着。
  @Test
  void should_allow_line_feed_and_tab() {
    assertThat(PostBody.parse("- 一\n\t- 二", violations))
        .get()
        .extracting(PostBody::value)
        .isEqualTo("- 一\n\t- 二");
  }

  /// 其他控制字符与行 / 段分隔符拒绝。
  @Test
  void should_reject_control_and_separator_characters() {
    assertThat(PostBody.parse("a\u0007b", violations)).isEmpty();
    assertThat(PostBody.parse("a" + LINE_SEPARATOR + "b", violations)).isEmpty();
    assertThat(violations)
        .extracting(FieldViolation::field, FieldViolation::code)
        .containsExactly(tuple("body", "INVALID_CHARACTER"), tuple("body", "INVALID_CHARACTER"));
  }

  /// 只有一条指定原因码的正文错误。
  ///
  /// @param code 原因码
  private void assertViolation(String code) {
    assertThat(violations)
        .extracting(FieldViolation::field, FieldViolation::code)
        .containsExactly(tuple("body", code));
  }
}
