package dev.patra.identity.domain.account.model.vo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/// 第三方首次注册的邮箱验证码：6 位数字。原文只进邮件和用户提交的请求，存储里只有哈希，也不进日志。
///
/// @param value 验证码原文
public record RegistrationCode(String value) {

  private static final Pattern FORMAT = Pattern.compile("^[0-9]{6}$");

  /// 取值上限，不含：6 位数字。
  private static final int BOUND = 1_000_000;

  /// 只接受 6 位数字。
  public RegistrationCode {
    Objects.requireNonNull(value, "value 不能为 null");
    if (!FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("验证码必须是 6 位数字");
    }
  }

  /// 生成一个新验证码。
  ///
  /// @param random 随机源
  /// @return 验证码
  public static RegistrationCode generate(SecureRandom random) {
    return new RegistrationCode("%06d".formatted(random.nextInt(BOUND)));
  }

  /// 解析用户提交的值：先去首尾空白，再校验格式。
  ///
  /// @param raw 原始值，可以为 `null`
  /// @return 验证码；格式不对时为空
  public static Optional<RegistrationCode> parse(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String stripped = raw.strip();
    return FORMAT.matcher(stripped).matches()
        ? Optional.of(new RegistrationCode(stripped))
        : Optional.empty();
  }

  /// 和待补填凭证绑定的 SHA-256：6 位数字的空间很小，不加盐一张表就能反查；有了凭证哈希，每个人都要单独算。
  ///
  /// @param ticketHash 待补填凭证的哈希
  /// @return 小写十六进制 64 个字符
  public String hash(String ticketHash) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest((ticketHash + ":" + value).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("JDK 缺少 SHA-256", e);
    }
  }

  /// 不输出原文。
  ///
  /// @return 固定文本
  @Override
  public String toString() {
    return "RegistrationCode[***]";
  }
}
