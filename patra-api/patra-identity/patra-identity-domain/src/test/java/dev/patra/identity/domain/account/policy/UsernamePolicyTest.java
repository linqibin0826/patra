package dev.patra.identity.domain.account.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/// UsernamePolicy 单元测试：长度边界、大小写归一、首尾符号、连续符号、纯数字、保留字与同形归一。
@DisplayName("UsernamePolicy 单元测试")
class UsernamePolicyTest {

  private final UsernamePolicy policy = new UsernamePolicy();

  static Stream<Arguments> rejected() {
    return Stream.of(
        Arguments.of(null, "REQUIRED"),
        Arguments.of("   ", "REQUIRED"),
        Arguments.of("陈宇", "INVALID_CHARACTER"),
        Arguments.of("chen yu", "INVALID_CHARACTER"),
        Arguments.of("chen.yu", "INVALID_CHARACTER"),
        Arguments.of("chen@yu", "INVALID_CHARACTER"),
        Arguments.of("ab", "TOO_SHORT"),
        Arguments.of("a".repeat(31), "TOO_LONG"),
        Arguments.of("_abc", "INVALID_FORMAT"),
        Arguments.of("abc-", "INVALID_FORMAT"),
        Arguments.of("a__b", "INVALID_FORMAT"),
        Arguments.of("a--b", "INVALID_FORMAT"),
        Arguments.of("a_-b", "INVALID_FORMAT"),
        Arguments.of("123456", "DIGITS_ONLY"),
        Arguments.of("admin", "RESERVED"),
        Arguments.of("ADMIN", "RESERVED"),
        Arguments.of("adm1n", "RESERVED"),
        Arguments.of("0fficial", "RESERVED"),
        Arguments.of("ncb1", "RESERVED"),
        Arguments.of("ad-min", "RESERVED"),
        Arguments.of("a_d_m_i_n", "RESERVED"),
        Arguments.of("patra", "RESERVED"),
        Arguments.of("settings", "RESERVED"),
        Arguments.of("deleted-user", "RESERVED"));
  }

  static Stream<String> accepted() {
    return Stream.of(
        "abc",
        "a".repeat(30),
        "chen-yu_01",
        "Chen_Yu",
        "admin2",
        "hello",
        "patra-dev",
        " chen_yu ");
  }

  @ParameterizedTest
  @MethodSource("rejected")
  @DisplayName("只报第一条不满足的规则")
  void should_report_first_violation(String raw, String code) {
    assertThat(policy.validate(raw))
        .hasValueSatisfying(
            violation -> {
              assertThat(violation.field()).isEqualTo("username");
              assertThat(violation.code()).isEqualTo(code);
            });
  }

  @ParameterizedTest
  @MethodSource("accepted")
  @DisplayName("合法的用户名没有错误")
  void should_accept_valid_usernames(String raw) {
    assertThat(policy.validate(raw)).isEmpty();
  }
}
