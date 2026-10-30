package dev.patra.notification.domain.preference.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// 退订令牌：32 字节随机数的 base64url，43 个字符；解析去首尾空白，格式不对返回空；不输出原文。
class UnsubscribeTokenTest {

  private static final String VALID = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJK-_0123";

  /// 生成的是 43 个 base64url 字符，两次不同。
  @Test
  void generate_yields_43_base64url_chars() {
    SecureRandom random = new SecureRandom();

    UnsubscribeToken first = UnsubscribeToken.generate(random);
    UnsubscribeToken second = UnsubscribeToken.generate(random);

    assertThat(first.value()).matches("^[A-Za-z0-9_-]{43}$");
    assertThat(second.value()).isNotEqualTo(first.value());
  }

  /// 邮件客户端可能在链接首尾带上空白，去掉后照常认。
  @Test
  void parse_strips_whitespace_and_accepts_valid() {
    assertThat(UnsubscribeToken.parse("  " + VALID + "\n")).contains(new UnsubscribeToken(VALID));
  }

  /// 缺失、被截断、多一位、带 base64 而非 base64url 的字符：都不认，不用去查库。
  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "abc",
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJK-_012",
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJK-_01234",
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJK+/012=",
        "abcdefghijklmnopqrstuvwxyz ABCDEFGHIJK-_012"
      })
  void parse_rejects_malformed(String raw) {
    assertThat(UnsubscribeToken.parse(raw)).isEmpty();
  }

  /// 构造器只收合法值。
  @Test
  void constructor_rejects_malformed() {
    assertThatThrownBy(() -> new UnsubscribeToken("abc"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /// 日志里看不到原文。
  @Test
  void to_string_hides_value() {
    assertThat(new UnsubscribeToken(VALID)).hasToString("UnsubscribeToken[***]");
  }
}
