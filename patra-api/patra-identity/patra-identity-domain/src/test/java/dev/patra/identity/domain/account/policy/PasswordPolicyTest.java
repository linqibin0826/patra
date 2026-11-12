package dev.patra.identity.domain.account.policy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.account.model.vo.UserIdentifiers;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// PasswordPolicy 单元测试。
@DisplayName("PasswordPolicy 单元测试")
class PasswordPolicyTest {

  /// 不知道是哪个账号：只查密码自身的规则。
  private static final UserIdentifiers UNKNOWN = UserIdentifiers.unknown();

  /// 名单里只放几条，条目已经是比较键的形式。
  private final PasswordPolicy policy =
      new PasswordPolicy(Set.of("123456", "password123", "12345678")::contains);

  @Test
  @DisplayName("7 个码点太短，8 个可以（汉字）")
  void should_check_min_length_in_code_points_with_cjk() {
    assertThat(policy.validate("密码密码密码密", UserFieldViolations.PASSWORD, UNKNOWN))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("TOO_SHORT"));
    assertThat(policy.validate("密码密码密码密码", UserFieldViolations.PASSWORD, UNKNOWN)).isEmpty();
  }

  @Test
  @DisplayName("64 个码点可以，65 个太长（表情，每个占两个 UTF-16 单元）")
  void should_check_max_length_in_code_points_with_emoji() {
    assertThat(policy.validate("😀".repeat(64), UserFieldViolations.PASSWORD, UNKNOWN)).isEmpty();
    assertThat(policy.validate("😀".repeat(65), UserFieldViolations.PASSWORD, UNKNOWN))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("TOO_LONG"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"password123", "Password123", "PASSWORD123", "ｐａｓｓｗｏｒｄ１２３", " password123 "})
  @DisplayName("常见名单不分大小写、全半角和首尾空白")
  void should_reject_common_password_variants(String raw) {
    assertThat(policy.validate(raw, UserFieldViolations.PASSWORD, UNKNOWN))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("TOO_COMMON"));
  }

  @Test
  @DisplayName("原始长度够 8 位、规范化后变短的输入也能命中名单")
  void should_hit_list_when_normalized_key_is_shorter_than_min_length() {
    assertThat(policy.validate(" 123456 ", UserFieldViolations.PASSWORD, UNKNOWN))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("TOO_COMMON"));
  }

  @Test
  @DisplayName("空值和孤立代理项沿用 PlainPassword 的规则")
  void should_delegate_basic_checks_to_plain_password() {
    assertThat(policy.validate(null, UserFieldViolations.PASSWORD, UNKNOWN))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("REQUIRED"));
    assertThat(policy.validate("abcdefgh\uD800", UserFieldViolations.PASSWORD, UNKNOWN))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("INVALID_CHARACTER"));
  }

  @Test
  @DisplayName("1 MB 的密码在长度校验处拒绝，不去查名单")
  void should_reject_huge_password_before_checking_list() {
    PasswordPolicy strict =
        new PasswordPolicy(
            key -> {
              throw new AssertionError("超长输入不该查名单");
            });

    assertThat(strict.validate("a".repeat(1_000_000), UserFieldViolations.PASSWORD, UNKNOWN))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("TOO_LONG"));
  }

  @Test
  @DisplayName("不在名单里、长度合适的密码通过")
  void should_accept_good_password() {
    assertThat(policy.validate("correct horse battery", UserFieldViolations.PASSWORD, UNKNOWN))
        .isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"alice2024", "Alice2024", "ａｌｉｃｅ２０２４", " alice2024 "})
  @DisplayName("和用户名相同的密码被拒，不分大小写、全半角和首尾空白")
  void should_reject_password_equal_to_username(String raw) {
    UserIdentifiers identifiers = UserIdentifiers.of("carol@example.com", "alice2024");

    assertThat(policy.validate(raw, UserFieldViolations.PASSWORD, identifiers))
        .hasValueSatisfying(
            v -> {
              assertThat(v.field()).isEqualTo("password");
              assertThat(v.code()).isEqualTo("TOO_SIMILAR");
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"123alice2024", "alice2024!", "My-Alice2024-Pass"})
  @DisplayName("包含用户名的密码被拒")
  void should_reject_password_containing_username(String raw) {
    UserIdentifiers identifiers = UserIdentifiers.of("carol@example.com", "alice2024");

    assertThat(policy.validate(raw, UserFieldViolations.PASSWORD, identifiers))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("TOO_SIMILAR"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"alice2024@example.com", "Alice2024@Example.com", "alice2024!", "xx-alice2024"})
  @DisplayName("等于邮箱、或包含邮箱 @ 前面那一段的密码被拒")
  void should_reject_password_equal_to_email_or_containing_its_local_part(String raw) {
    UserIdentifiers identifiers = UserIdentifiers.of("alice2024@example.com", "wonderland");

    assertThat(policy.validate(raw, UserFieldViolations.PASSWORD, identifiers))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("TOO_SIMILAR"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"amy-garden-77", "ab-garden-77", "dreamy-cabin"})
  @DisplayName("用户名或邮箱 @ 前面那一段不足 4 个字符时，密码包含它也不拒")
  void should_accept_password_containing_identifier_shorter_than_four_characters(String raw) {
    UserIdentifiers identifiers = UserIdentifiers.of("ab@gmail.com", "amy");

    assertThat(policy.validate(raw, UserFieldViolations.PASSWORD, identifiers)).isEmpty();
  }

  @Test
  @DisplayName("不足 4 个字符的用户名，密码和它相同（只多了首尾空白）仍被拒")
  void should_reject_password_equal_to_short_username() {
    UserIdentifiers identifiers = UserIdentifiers.of("carol@example.com", "amy");

    assertThat(policy.validate("  amy   ", UserFieldViolations.PASSWORD, identifiers))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("TOO_SIMILAR"));
  }

  @Test
  @DisplayName("邮箱或用户名为 null、为空白时不参与比较")
  void should_skip_missing_identifiers() {
    UserIdentifiers missing = UserIdentifiers.of(null, "   ");

    assertThat(policy.validate("correct horse battery", UserFieldViolations.PASSWORD, missing))
        .isEmpty();
    // 全是空白的密码比较键是空串，不能因为用户名也是空白就算「相同」
    assertThat(policy.validate(" ".repeat(8), UserFieldViolations.PASSWORD, missing)).isEmpty();
  }

  @Test
  @DisplayName("按指定字段名报错：改密码、重设用 newPassword")
  void should_report_violation_under_given_field() {
    assertThat(policy.validate("short", "newPassword", UNKNOWN))
        .get()
        .satisfies(
            violation -> {
              assertThat(violation.field()).isEqualTo("newPassword");
              assertThat(violation.code()).isEqualTo("TOO_SHORT");
            });
    assertThat(policy.validate("", "newPassword", UNKNOWN).orElseThrow().field())
        .isEqualTo("newPassword");
    assertThat(policy.validate("123456ab", "newPassword", UNKNOWN)).isEmpty();
  }
}
