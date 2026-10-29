package dev.patra.identity.domain.account.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/// DisplayNamePolicy 单元测试：字素与字节边界、NFC、拒绝字符、形态、拒绝词。
///
/// 不可见字符一律用码点构造（`cp(...)`）：google-java-format 会把源码里的 Unicode 转义改写成裸字符，
/// 还会把 U+2028 当成换行把字符串拆开。
@DisplayName("DisplayNamePolicy 单元测试")
class DisplayNamePolicyTest {

  /// 👨‍👩‍👧：5 个码点、18 字节、1 个字素。
  private static final String FAMILY = cp(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467);

  /// 英格兰旗：黑旗加标签字符序列，1 个字素。
  private static final String FLAG_ENGLAND =
      cp(0x1F3F4, 0xE0067, 0xE0062, 0xE0065, 0xE006E, 0xE0067, 0xE007F);

  private static final String IDEOGRAPHIC_SPACE = cp(0x3000);

  private static final String COMBINING_ACUTE = cp(0x0301);

  private final DisplayNamePolicy policy = new DisplayNamePolicy();

  static Stream<Arguments> rejected() {
    return Stream.of(
        Arguments.of(null, "REQUIRED"),
        Arguments.of("  " + IDEOGRAPHIC_SPACE + " ", "REQUIRED"),
        Arguments.of("a\tb", "INVALID_CHARACTER"),
        Arguments.of("a\nb", "INVALID_CHARACTER"),
        Arguments.of("a" + cp(0x200B) + "b", "INVALID_CHARACTER"),
        Arguments.of("a" + cp(0x200D) + "b", "INVALID_CHARACTER"),
        Arguments.of("a" + cp(0x202E) + "b", "INVALID_CHARACTER"),
        Arguments.of("a" + cp(0xFEFF) + "b", "INVALID_CHARACTER"),
        Arguments.of("a" + cp(0x00AD) + "b", "INVALID_CHARACTER"),
        Arguments.of("a" + cp(0x2028) + "b", "INVALID_CHARACTER"),
        Arguments.of(COMBINING_ACUTE + COMBINING_ACUTE, "INVALID_CHARACTER"),
        Arguments.of("张", "TOO_SHORT"),
        Arguments.of(FAMILY, "TOO_SHORT"),
        Arguments.of("汉".repeat(31), "TOO_LONG"),
        Arguments.of("汉".repeat(41), "TOO_LONG"),
        Arguments.of(FAMILY.repeat(7), "TOO_LONG"),
        Arguments.of("patra.dev", "INVALID_FORMAT"),
        Arguments.of("https://x", "INVALID_FORMAT"),
        Arguments.of("www.x", "INVALID_FORMAT"),
        Arguments.of("a@b.cn", "INVALID_FORMAT"),
        Arguments.of("13800138000", "INVALID_FORMAT"),
        Arguments.of("138 1234 5678", "INVALID_FORMAT"),
        Arguments.of("１３８００１３８０００", "INVALID_FORMAT"),
        Arguments.of("加微信abc", "INVALID_FORMAT"),
        Arguments.of("wx: abc123", "INVALID_FORMAT"),
        Arguments.of("vx：abc", "INVALID_FORMAT"),
        Arguments.of("张医生", "RESERVED"),
        Arguments.of("Dr. Wang", "RESERVED"),
        Arguments.of("0fficial", "RESERVED"),
        Arguments.of("Patra 官方", "RESERVED"),
        Arguments.of("admin", "RESERVED"),
        Arguments.of("王 教 授", "RESERVED"));
  }

  static Stream<String> accepted() {
    return Stream.of(
        "张三",
        "Chen Yu",
        "张三·李四",
        "医学生",
        "Andrea Wang",
        "J.R.R. Tolkien",
        cp(0x1F1E8, 0x1F1F3, 0x1F1E8, 0x1F1F3),
        cp(0x1F44D, 0x1F3FD, 0x1F44D, 0x1F3FD));
  }

  @ParameterizedTest
  @MethodSource("rejected")
  @DisplayName("只报第一条不满足的规则")
  void should_report_first_violation(String raw, String code) {
    assertThat(policy.validate(raw))
        .hasValueSatisfying(
            violation -> {
              assertThat(violation.field()).isEqualTo("displayName");
              assertThat(violation.code()).isEqualTo(code);
            });
  }

  @ParameterizedTest
  @MethodSource("accepted")
  @DisplayName("合法的昵称没有错误")
  void should_accept_valid_names(String raw) {
    assertThat(policy.validate(raw)).isEmpty();
  }

  @Test
  @DisplayName("字素与字节的边界：ZWJ 序列、肤色、标签字符都整体算一个字素")
  void should_count_graphemes_and_bytes_at_the_boundary() {
    assertThat(policy.validate("汉".repeat(30))).isEmpty();
    assertThat(policy.validate(FAMILY.repeat(6))).isEmpty();
    assertThat(policy.validate(FAMILY + FAMILY)).isEmpty();
    assertThat(policy.validate(FLAG_ENGLAND + FLAG_ENGLAND)).isEmpty();
    assertThat(ProfileTextRules.graphemeCount(FAMILY.repeat(6))).isEqualTo(6);
  }

  @Test
  @DisplayName("规范化：NFC，去首尾空白，连续空格折成一个")
  void should_normalize_to_nfc_and_collapse_spaces() {
    assertThat(policy.parse("  陈" + IDEOGRAPHIC_SPACE + IDEOGRAPHIC_SPACE + "宇  ").value())
        .isEqualTo("陈 宇");
    assertThat(policy.parse("e" + COMBINING_ACUTE + "e" + COMBINING_ACUTE).value()).isEqualTo("éé");
  }

  /// 按码点拼字符串。
  ///
  /// @param codePoints 码点
  /// @return 字符串
  private static String cp(int... codePoints) {
    return new String(codePoints, 0, codePoints.length);
  }
}
