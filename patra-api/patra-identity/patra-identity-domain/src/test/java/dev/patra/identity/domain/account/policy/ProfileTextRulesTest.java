package dev.patra.identity.domain.account.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// ProfileTextRules 单元测试：昵称与资料文本共用的拒绝字符、字素与字节计数。
///
/// 不可见字符一律用码点构造（`cp(...)`），原因见 `DisplayNamePolicyTest`。
@DisplayName("ProfileTextRules 单元测试")
class ProfileTextRulesTest {

  /// 👨‍👩‍👧：5 个码点、18 字节、1 个字素。
  private static final String FAMILY = cp(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467);

  /// 英格兰旗：黑旗加标签字符序列，1 个字素。
  private static final String FLAG_ENGLAND =
      cp(0x1F3F4, 0xE0067, 0xE0062, 0xE0065, 0xE006E, 0xE0067, 0xE007F);

  @Test
  void should_reject_line_feed_unless_allowed() {
    assertThat(ProfileTextRules.hasRejectedCharacter("a\nb", false)).isTrue();
    assertThat(ProfileTextRules.hasRejectedCharacter("a\nb", true)).isFalse();
  }

  @Test
  void should_still_reject_other_line_breaks_when_line_feed_allowed() {
    assertThat(ProfileTextRules.hasRejectedCharacter("a" + cp(0x0D) + "b", true)).isTrue();
    assertThat(ProfileTextRules.hasRejectedCharacter("a" + cp(0x2028) + "b", true)).isTrue();
    assertThat(ProfileTextRules.hasRejectedCharacter("a" + cp(0x0085) + "b", true)).isTrue();
    assertThat(ProfileTextRules.hasRejectedCharacter("a\tb", true)).isTrue();
  }

  @Test
  void should_accept_emoji_zwj_sequences() {
    assertThat(ProfileTextRules.hasRejectedCharacter("家" + FAMILY, false)).isFalse();
    assertThat(ProfileTextRules.hasRejectedCharacter(FLAG_ENGLAND + "队", false)).isFalse();
  }

  @Test
  void should_reject_format_characters_outside_emoji() {
    assertThat(ProfileTextRules.hasRejectedCharacter("a" + cp(0x200B) + "b", false)).isTrue();
    assertThat(ProfileTextRules.hasRejectedCharacter("a" + cp(0x200D) + "b", false)).isTrue();
    assertThat(ProfileTextRules.hasRejectedCharacter("a" + cp(0x202E) + "b", false)).isTrue();
    assertThat(ProfileTextRules.hasRejectedCharacter(cp(0x0301) + "a", true)).isTrue();
  }

  @Test
  void should_count_graphemes_and_bytes() {
    assertThat(ProfileTextRules.graphemeCount(FAMILY.repeat(6))).isEqualTo(6);
    assertThat(ProfileTextRules.utf8Length("张三")).isEqualTo(6);
    assertThat(ProfileTextRules.utf8Length(FAMILY)).isEqualTo(18);
  }

  /// 按码点拼字符串。
  ///
  /// @param codePoints 码点
  /// @return 字符串
  private static String cp(int... codePoints) {
    return new String(codePoints, 0, codePoints.length);
  }
}
