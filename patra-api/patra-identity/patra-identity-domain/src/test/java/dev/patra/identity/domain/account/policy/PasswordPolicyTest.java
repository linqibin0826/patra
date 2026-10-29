package dev.patra.identity.domain.account.policy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// PasswordPolicy 单元测试。
@DisplayName("PasswordPolicy 单元测试")
class PasswordPolicyTest {

  /// 名单里只放几条，条目已经是比较键的形式。
  private final PasswordPolicy policy =
      new PasswordPolicy(Set.of("123456", "password123", "12345678")::contains);

  @Test
  @DisplayName("7 个码点太短，8 个可以（汉字）")
  void should_check_min_length_in_code_points_with_cjk() {
    assertThat(policy.validate("密码密码密码密", UserFieldViolations.PASSWORD))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("TOO_SHORT"));
    assertThat(policy.validate("密码密码密码密码", UserFieldViolations.PASSWORD)).isEmpty();
  }

  @Test
  @DisplayName("64 个码点可以，65 个太长（表情，每个占两个 UTF-16 单元）")
  void should_check_max_length_in_code_points_with_emoji() {
    assertThat(policy.validate("😀".repeat(64), UserFieldViolations.PASSWORD)).isEmpty();
    assertThat(policy.validate("😀".repeat(65), UserFieldViolations.PASSWORD))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("TOO_LONG"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"password123", "Password123", "PASSWORD123", "ｐａｓｓｗｏｒｄ１２３", " password123 "})
  @DisplayName("常见名单不分大小写、全半角和首尾空白")
  void should_reject_common_password_variants(String raw) {
    assertThat(policy.validate(raw, UserFieldViolations.PASSWORD))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("TOO_COMMON"));
  }

  @Test
  @DisplayName("原始长度够 8 位、规范化后变短的输入也能命中名单")
  void should_hit_list_when_normalized_key_is_shorter_than_min_length() {
    assertThat(policy.validate(" 123456 ", UserFieldViolations.PASSWORD))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("TOO_COMMON"));
  }

  @Test
  @DisplayName("空值和孤立代理项沿用 PlainPassword 的规则")
  void should_delegate_basic_checks_to_plain_password() {
    assertThat(policy.validate(null, UserFieldViolations.PASSWORD))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("REQUIRED"));
    assertThat(policy.validate("abcdefgh\uD800", UserFieldViolations.PASSWORD))
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

    assertThat(strict.validate("a".repeat(1_000_000), UserFieldViolations.PASSWORD))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("TOO_LONG"));
  }

  @Test
  @DisplayName("不在名单里、长度合适的密码通过")
  void should_accept_good_password() {
    assertThat(policy.validate("correct horse battery", UserFieldViolations.PASSWORD)).isEmpty();
  }

  @Test
  @DisplayName("按指定字段名报错：改密码、重设用 newPassword")
  void should_report_violation_under_given_field() {
    assertThat(policy.validate("short", "newPassword"))
        .get()
        .satisfies(
            violation -> {
              assertThat(violation.field()).isEqualTo("newPassword");
              assertThat(violation.code()).isEqualTo("TOO_SHORT");
            });
    assertThat(policy.validate("", "newPassword").orElseThrow().field()).isEqualTo("newPassword");
    assertThat(policy.validate("123456ab", "newPassword")).isEmpty();
  }
}
