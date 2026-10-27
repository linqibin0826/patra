package dev.patra.interaction.domain.discussion.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.discussion.exception.InvalidCommentException;
import dev.patra.tool.core.error.field.FieldViolation;
import java.util.List;
import org.junit.jupiter.api.Test;

/// 正文规则：换行统一、NFC、去首尾空白；拒绝空、控制字符与行 / 段分隔符；按字素与字节限长。
class CommentTextTest {

  /// `\r\n`、`\r` 变成 `\n`，组合序列合成一个码点，首尾的空白与全角空格去掉。
  @Test
  void should_normalize_line_breaks_nfc_and_trim() {
    assertThat(CommentText.parse("  é\r\nb\r  　")).isEqualTo("é\nb");
  }

  /// `null` 与只有空白都是空。
  @Test
  void should_reject_blank() {
    assertRejected(null, InvalidCommentException.REQUIRED);
    assertRejected(" \n　", InvalidCommentException.REQUIRED);
  }

  /// 制表符与换行放行。
  @Test
  void should_keep_tab_and_line_feed() {
    assertThat(CommentText.parse("a\tb\nc")).isEqualTo("a\tb\nc");
  }

  /// 其他控制字符与行 / 段分隔符拒绝。
  @Test
  void should_reject_other_control_characters_and_separators() {
    for (String text :
        List.of(
            "a\u0000b",
            "a\u0007b",
            "a\u0085b",
            "a" + new String(Character.toChars(0x2028)) + "b",
            "a" + new String(Character.toChars(0x2029)) + "b")) {
      assertRejected(text, InvalidCommentException.INVALID_CHARACTER);
    }
  }

  /// 最多 5,000 个字素：5,000 个 emoji 正好 20,000 字节，也放行。
  @Test
  void should_limit_graphemes() {
    assertThat(CommentText.parse("a".repeat(5000))).hasSize(5000);
    assertThat(CommentText.parse("😀".repeat(5000))).isEqualTo("😀".repeat(5000));
    assertRejected("a".repeat(5001), InvalidCommentException.TOO_LONG);
    assertRejected("😀".repeat(5001), InvalidCommentException.TOO_LONG);
  }

  /// 字素没超、字节超了也拒绝：`a` 后面跟 11 个组合重音，NFC 后是 1 个字素、22 个字节，1,000 个就是 22,000 字节。
  @Test
  void should_limit_bytes_within_grapheme_limit() {
    assertRejected(("a" + "́".repeat(11)).repeat(1000), InvalidCommentException.TOO_LONG);
  }

  /// 断言被拒，字段码如期。
  ///
  /// @param text 原文
  /// @param code 期望的字段码
  private static void assertRejected(String text, String code) {
    assertThatThrownBy(() -> CommentText.parse(text))
        .isInstanceOfSatisfying(
            InvalidCommentException.class,
            ex ->
                assertThat(ex.getFieldViolations())
                    .extracting(FieldViolation::code)
                    .containsExactly(code));
  }
}
