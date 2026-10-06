package dev.linqibin.patra.identity.domain.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/// PlainPassword 单元测试。
@DisplayName("PlainPassword 单元测试")
class PlainPasswordTest {

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {""})
  @DisplayName("null 和空串报 REQUIRED")
  void should_require_password(String raw) {
    assertThat(PlainPassword.validate(raw))
        .hasValueSatisfying(
            violation -> {
              assertThat(violation.field()).isEqualTo("password");
              assertThat(violation.code()).isEqualTo("REQUIRED");
            });
  }

  @Test
  @DisplayName("只有空格不算空，原样保留")
  void should_keep_whitespace_as_is() {
    assertThat(PlainPassword.validate("        ")).isEmpty();
    assertThat(PlainPassword.of(" secret ").value()).isEqualTo(" secret ");
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc\uD800def", "\uDC00abcdefgh", "abcdefgh\uD83D"})
  @DisplayName("含孤立代理项报 INVALID_CHARACTER")
  void should_reject_unpaired_surrogates(String raw) {
    assertThat(PlainPassword.validate(raw))
        .hasValueSatisfying(
            violation -> assertThat(violation.code()).isEqualTo("INVALID_CHARACTER"));
  }

  @Test
  @DisplayName("成对的代理项（表情）是合法的")
  void should_accept_paired_surrogates() {
    assertThat(PlainPassword.validate("😀😀😀😀")).isEmpty();
  }

  @Test
  @DisplayName("长度按码点计")
  void should_count_code_points() {
    assertThat(PlainPassword.of("密码密码密码密码").length()).isEqualTo(8);
    assertThat(PlainPassword.of("😀".repeat(8)).length()).isEqualTo(8);
  }

  @Test
  @DisplayName("normalized 做 NFKC")
  void should_normalize_with_nfkc() {
    assertThat(PlainPassword.of("ｐａｓｓ１２３").normalized()).isEqualTo("pass123");
  }

  @Test
  @DisplayName("比较键：NFKC、小写、去掉首尾空白")
  void should_build_comparison_key() {
    assertThat(PlainPassword.comparisonKeyOf("  Ｐａｓｓｗｏｒｄ１２３ ")).isEqualTo("password123");
  }

  @Test
  @DisplayName("toString 不输出密码")
  void should_hide_password_in_to_string() {
    assertThat(PlainPassword.of("Secret-Value-1").toString())
        .isEqualTo("***")
        .doesNotContain("Secret");
  }
}
