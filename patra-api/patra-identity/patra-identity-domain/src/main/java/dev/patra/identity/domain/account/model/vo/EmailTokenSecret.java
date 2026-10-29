package dev.patra.identity.domain.account.model.vo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/// 一次性邮件令牌的原文：32 字节随机数的 base64url（不带填充），恰好 43 个字符。
///
/// 原文只出现在邮件链接和消费请求里，库里只存 `hash()`，也不进日志；`toString()` 不输出内容。
/// 做法同会话令牌，只是没有前缀。
///
/// @param value 令牌原文
public record EmailTokenSecret(String value) {

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
  public EmailTokenSecret {
    Objects.requireNonNull(value, "value 不能为 null");
    if (!FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("邮件令牌的格式不合法");
    }
  }

  /// 生成一个新令牌。
  ///
  /// @param random 随机源
  /// @return 新令牌
  public static EmailTokenSecret generate(SecureRandom random) {
    Objects.requireNonNull(random, "random 不能为 null");
    byte[] bytes = new byte[RANDOM_BYTES];
    random.nextBytes(bytes);
    return new EmailTokenSecret(ENCODER.encodeToString(bytes));
  }

  /// 解析客户端送来的令牌：先去掉首尾空白，再校验格式。不合法返回空，调用方不必去查库。
  ///
  /// @param raw 原始值，可以为 `null`
  /// @return 令牌；为空白，或长度、字符不对时为空
  public static Optional<EmailTokenSecret> parse(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String stripped = raw.strip();
    if (!FORMAT.matcher(stripped).matches()) {
      return Optional.empty();
    }
    return Optional.of(new EmailTokenSecret(stripped));
  }

  /// 原文 UTF-8 字节的 SHA-256，小写十六进制 64 个字符。库里存的是它，不是原文。
  ///
  /// @return 哈希
  public String hash() {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("JDK 缺少 SHA-256", e);
    }
  }

  /// 不输出原文。
  ///
  /// @return 固定文本 `EmailTokenSecret[***]`
  @Override
  public String toString() {
    return "EmailTokenSecret[***]";
  }
}
