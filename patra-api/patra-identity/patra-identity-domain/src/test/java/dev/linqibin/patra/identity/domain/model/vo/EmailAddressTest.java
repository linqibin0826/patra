package dev.linqibin.patra.identity.domain.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/// EmailAddress 单元测试。
@DisplayName("EmailAddress 单元测试")
class EmailAddressTest {

  /// 在门户 `node_modules` 里实际运行 Zod 4.6.5 `z.email()` 得到的结果（2026-10-06）。
  ///
  /// @return 邮箱和 Zod 是否接受
  static Stream<Arguments> zodEmailVectors() {
    return Stream.of(
        Arguments.of("chen.yu@example.com", true),
        Arguments.of(
            "postdoctoral.researcher.zhang@cardiovascular-research.university-hospital.example.org",
            true),
        Arguments.of("2024cohort@example.org", true),
        Arguments.of("o'brien@example.ie", true),
        Arguments.of("user+tag@example.com", true),
        Arguments.of("a_b-c@sub.example.co", true),
        Arguments.of("UPPER@EXAMPLE.COM", true),
        Arguments.of("-a@example.com", true),
        Arguments.of("a+@example.com", true),
        Arguments.of("_@example.com", true),
        Arguments.of("a@xn--fiqs8s.cn", true),
        Arguments.of("a@example-.com", true),
        Arguments.of("a@b.c", false),
        Arguments.of("a@b", false),
        Arguments.of(".a@example.com", false),
        Arguments.of("a.@example.com", false),
        Arguments.of("a..b@example.com", false),
        Arguments.of("a'@example.com", false),
        Arguments.of("a@-example.com", false),
        Arguments.of("a@example.c0m", false),
        Arguments.of("a b@example.com", false),
        Arguments.of("张三@example.com", false),
        Arguments.of("a@example.com.", false),
        Arguments.of("a@@example.com", false),
        Arguments.of("\"q\"@example.com", false),
        Arguments.of("a@[127.0.0.1]", false),
        Arguments.of("a@localhost", false),
        Arguments.of("a@example..com", false));
  }

  @ParameterizedTest(name = "{0} → {1}")
  @MethodSource("zodEmailVectors")
  @DisplayName("格式规则和 Zod 默认的 z.email() 一致（实测点 2）")
  void should_match_zod_default_email_rule(String raw, boolean accepted) {
    assertThat(EmailAddress.validate(raw).isEmpty()).isEqualTo(accepted);
  }

  @Test
  @DisplayName("去掉首尾空白并转小写")
  void should_strip_and_lowercase() {
    assertThat(EmailAddress.of("  Chen.Yu@Example.COM \t").value())
        .isEqualTo("chen.yu@example.com");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   ", "\t\n"})
  @DisplayName("空的报 REQUIRED")
  void should_require_email(String raw) {
    assertThat(EmailAddress.validate(raw))
        .hasValueSatisfying(
            violation -> {
              assertThat(violation.field()).isEqualTo("email");
              assertThat(violation.code()).isEqualTo("REQUIRED");
            });
  }

  @Test
  @DisplayName("254 个字符可以，255 个报 TOO_LONG")
  void should_limit_length_to_254() {
    String atLimit = "a".repeat(242) + "@example.com";
    String overLimit = "a".repeat(243) + "@example.com";

    assertThat(atLimit).hasSize(254);
    assertThat(EmailAddress.validate(atLimit)).isEmpty();
    assertThat(EmailAddress.validate(overLimit))
        .hasValueSatisfying(violation -> assertThat(violation.code()).isEqualTo("TOO_LONG"));
  }

  @Test
  @DisplayName("1 MB 的输入在长度校验处就被拒绝")
  void should_reject_huge_input_by_length() {
    String huge = "a".repeat(1_000_000) + "@example.com";

    assertThat(EmailAddress.validate(huge))
        .hasValueSatisfying(violation -> assertThat(violation.code()).isEqualTo("TOO_LONG"));
  }

  @Test
  @DisplayName("格式不对报 INVALID_FORMAT")
  void should_report_invalid_format() {
    assertThat(EmailAddress.validate("not-an-email"))
        .hasValueSatisfying(violation -> assertThat(violation.code()).isEqualTo("INVALID_FORMAT"));
  }

  @Test
  @DisplayName("of 遇到不合法的输入抛 InvalidUserFieldsException")
  void should_throw_when_creating_from_invalid_input() {
    assertThatThrownBy(() -> EmailAddress.of("not-an-email"))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            exception ->
                assertThat(((InvalidUserFieldsException) exception).getFieldViolations())
                    .extracting("code")
                    .containsExactly("INVALID_FORMAT"));
  }

  @Test
  @DisplayName("规范构造器只接受规范化之后的值")
  void should_reject_non_normalized_value_in_canonical_constructor() {
    assertThatThrownBy(() -> new EmailAddress("Chen@Example.com"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EmailAddress(" chen@example.com"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new EmailAddress("chen@example.com").value()).isEqualTo("chen@example.com");
  }
}
