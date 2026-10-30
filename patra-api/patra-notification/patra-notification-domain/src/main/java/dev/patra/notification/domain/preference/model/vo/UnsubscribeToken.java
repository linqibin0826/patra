package dev.patra.notification.domain.preference.model.vo;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/// 退订令牌：32 字节随机数的 base64url（不带填充），恰好 43 个字符。每个用户一个，长期有效。
///
/// 和 identity 的一次性邮件令牌不同，库里存的是原文：每封提醒邮件都要把它拼进退订链接（spec 6.2）。
/// `toString()` 不输出原文，令牌不进日志。
///
/// @param value 令牌原文
public record UnsubscribeToken(String value) {

  /// 原文的字符数：32 字节的 base64url 不带填充。
  public static final int LENGTH = 43;

  /// 随机数的字节数。
  private static final int RANDOM_BYTES = 32;

  private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_-]{" + LENGTH + "}$");

  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

  /// 只接受格式合法的值。
  ///
  /// @param value 令牌原文
  /// @throws IllegalArgumentException 不是 43 个 base64url 字符
  public UnsubscribeToken {
    Objects.requireNonNull(value, "value 不能为 null");
    if (!FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("退订令牌的格式不合法");
    }
  }

  /// 生成一个新令牌。
  ///
  /// @param random 随机源
  /// @return 新令牌
  public static UnsubscribeToken generate(SecureRandom random) {
    Objects.requireNonNull(random, "random 不能为 null");
    byte[] bytes = new byte[RANDOM_BYTES];
    random.nextBytes(bytes);
    return new UnsubscribeToken(ENCODER.encodeToString(bytes));
  }

  /// 解析链接里的令牌：先去掉首尾空白，再校验格式。不合法返回空，调用方不必去查库。
  ///
  /// @param raw 原始值，可以为 `null`
  /// @return 令牌；为空白，或长度、字符不对时为空
  public static Optional<UnsubscribeToken> parse(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String stripped = raw.strip();
    if (!FORMAT.matcher(stripped).matches()) {
      return Optional.empty();
    }
    return Optional.of(new UnsubscribeToken(stripped));
  }

  /// 不输出原文。
  ///
  /// @return 固定文本 `UnsubscribeToken[***]`
  @Override
  public String toString() {
    return "UnsubscribeToken[***]";
  }
}
