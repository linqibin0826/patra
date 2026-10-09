package dev.linqibin.patra.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.common.security.AccountType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// SessionToken 单元测试。
@DisplayName("SessionToken 单元测试")
class SessionTokenTest {

  private static final SecureRandom RANDOM = new SecureRandom();

  @Test
  @DisplayName("生成的令牌：前缀 patra_user_，随机部分 43 个 base64url 字符，总长 54")
  void should_generate_prefixed_base64url_token() {
    SessionToken token = SessionToken.generate(AccountType.USER, RANDOM);

    assertThat(token.value()).matches("^patra_user_[A-Za-z0-9_-]{43}$").hasSize(54);
    assertThat(token.accountType()).isEqualTo(AccountType.USER);
    assertThat(SessionToken.generate(AccountType.USER, RANDOM).value()).isNotEqualTo(token.value());
  }

  @Test
  @DisplayName("生成的令牌能被解析回来")
  void should_parse_generated_token() {
    SessionToken token = SessionToken.generate(AccountType.USER, RANDOM);

    assertThat(SessionToken.parse(token.value())).contains(token);
  }

  @ParameterizedTest(name = "拒绝: [{0}]")
  @ValueSource(
      strings = {
        "",
        "patra_user_",
        "patra_staff_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "PATRA_USER_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "patra_user_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "patra_user_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "patra_user_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA+/",
        "patra_user_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
      })
  @DisplayName("格式不对的值解析为空，不抛异常")
  void should_reject_malformed_values(String raw) {
    assertThat(SessionToken.parse(raw)).isEmpty();
  }

  @Test
  @DisplayName("null 解析为空")
  void should_reject_null() {
    assertThat(SessionToken.parse(null)).isEmpty();
  }

  @Test
  @DisplayName("首尾带空白或换行的令牌解析为空（Cookie 转头时常见）")
  void should_reject_tokens_with_surrounding_whitespace() {
    String value = SessionToken.generate(AccountType.USER, RANDOM).value();

    assertThat(SessionToken.parse(value + "\n")).isEmpty();
    assertThat(SessionToken.parse(" " + value)).isEmpty();
    assertThat(SessionToken.parse(value + " ")).isEmpty();
  }

  @Test
  @DisplayName("直接构造格式不对的值抛 IllegalArgumentException")
  void should_reject_malformed_value_in_constructor() {
    assertThatThrownBy(() -> new SessionToken("patra_user_short"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("哈希是整个令牌 UTF-8 的 SHA-256，小写十六进制 64 位，同一令牌稳定")
  void should_hash_whole_token_with_sha256() throws Exception {
    SessionToken token = SessionToken.generate(AccountType.USER, RANDOM);
    String expected =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(token.value().getBytes(StandardCharsets.UTF_8)));

    assertThat(token.hash()).isEqualTo(expected).matches("^[0-9a-f]{64}$");
    assertThat(token.hash()).isEqualTo(token.hash());
    assertThat(SessionToken.generate(AccountType.USER, RANDOM).hash()).isNotEqualTo(token.hash());
  }

  @Test
  @DisplayName("toString 只有前缀，不带随机部分")
  void should_mask_random_part_in_to_string() {
    SessionToken token = SessionToken.generate(AccountType.USER, RANDOM);

    assertThat(token.toString()).isEqualTo("SessionToken[patra_user_***]");
    assertThat(token.toString()).doesNotContain(token.value().substring(11));
  }
}
