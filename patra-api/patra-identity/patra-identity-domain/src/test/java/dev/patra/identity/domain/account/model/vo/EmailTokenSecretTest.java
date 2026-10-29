package dev.patra.identity.domain.account.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// EmailTokenSecret 单元测试：生成、解析、哈希、构造校验、不泄露原文。
@DisplayName("EmailTokenSecret 单元测试")
class EmailTokenSecretTest {

  private static final String VALID = "A".repeat(43);

  @Test
  @DisplayName("生成 43 个 URL 安全字符，每次都不同")
  void should_generate_43_url_safe_characters_and_differ_each_time() {
    SecureRandom random = new SecureRandom();

    EmailTokenSecret first = EmailTokenSecret.generate(random);

    assertThat(first.value()).matches("^[A-Za-z0-9_-]{43}$").hasSize(EmailTokenSecret.LENGTH);
    assertThat(EmailTokenSecret.generate(random).value()).isNotEqualTo(first.value());
  }

  @Test
  @DisplayName("哈希是原文 UTF-8 的 SHA-256，小写十六进制")
  void should_hash_to_lowercase_sha256_hex() throws NoSuchAlgorithmException {
    String expected =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(VALID.getBytes(StandardCharsets.UTF_8)));

    assertThat(new EmailTokenSecret(VALID).hash()).isEqualTo(expected).hasSize(64);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "   ",
        "A",
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA+A",
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/A"
      })
  @DisplayName("解析：长度不对或字符不在 base64url 字母表里就是空")
  void should_reject_wrong_length_or_alphabet(String raw) {
    assertThat(EmailTokenSecret.parse(raw)).isEmpty();
  }

  @Test
  @DisplayName("解析：先去掉首尾空白再校验；null 是空")
  void should_accept_surrounding_whitespace() {
    assertThat(EmailTokenSecret.parse("  " + VALID + "\n")).contains(new EmailTokenSecret(VALID));
    assertThat(EmailTokenSecret.parse(null)).isEmpty();
  }

  @Test
  @DisplayName("构造器只收恰好 43 个 URL 安全字符，连末尾换行也不放过，报错不带原文")
  void should_reject_invalid_value_in_constructor() {
    assertThatThrownBy(() -> new EmailTokenSecret("A".repeat(42)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EmailTokenSecret("A".repeat(44)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EmailTokenSecret("A".repeat(42) + "+"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EmailTokenSecret(VALID + "\n"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageNotContaining("AAAA");
  }

  @Test
  @DisplayName("构造器和生成都不接受 null")
  void should_reject_null_arguments() {
    assertThatThrownBy(() -> new EmailTokenSecret(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> EmailTokenSecret.generate(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("toString 不输出原文")
  void should_mask_value_in_to_string() {
    assertThat(new EmailTokenSecret(VALID).toString())
        .isEqualTo("EmailTokenSecret[***]")
        .doesNotContain(VALID);
  }
}
