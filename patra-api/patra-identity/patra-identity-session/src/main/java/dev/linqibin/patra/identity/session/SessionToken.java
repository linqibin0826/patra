package dev.linqibin.patra.identity.session;

import dev.linqibin.patra.common.security.AccountType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/// 会话令牌：`<账号类型前缀><43 个 base64url 字符>`，随机部分 32 字节。
///
/// 前缀只说明令牌属于哪类账号，不含用户信息；网关靠它决定查哪个键空间。
/// Redis 里只存 `hash()`，令牌本身不落任何存储，也不进日志。
///
/// @param value 令牌原文
public record SessionToken(String value) {

  /// 随机部分的字节数。
  static final int RANDOM_BYTES = 32;

  /// 各账号类型的前缀。新账号类型在这里加一行。
  private static final Map<AccountType, String> PREFIXES = Map.of(AccountType.USER, "patra_user_");

  /// 随机部分：32 字节的 base64url 不带填充，恰好 43 个字符。
  private static final Pattern RANDOM_PART = Pattern.compile("[A-Za-z0-9_-]{43}");

  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

  /// 只接受格式合法的值。
  public SessionToken {
    Objects.requireNonNull(value, "value 不能为 null");
    if (accountTypeOf(value).isEmpty()) {
      throw new IllegalArgumentException("会话令牌的格式不合法");
    }
  }

  /// 生成一个新令牌。
  ///
  /// @param accountType 账号类型，决定前缀
  /// @param random 随机源
  /// @return 新令牌
  public static SessionToken generate(AccountType accountType, SecureRandom random) {
    Objects.requireNonNull(random, "random 不能为 null");
    byte[] bytes = new byte[RANDOM_BYTES];
    random.nextBytes(bytes);
    return new SessionToken(prefixOf(accountType) + ENCODER.encodeToString(bytes));
  }

  /// 解析客户端送来的令牌。不合法返回空，调用方按匿名处理；不做任何裁剪。
  ///
  /// @param raw 原始值，可以为 `null`
  /// @return 令牌；格式不对时为空
  public static Optional<SessionToken> parse(String raw) {
    if (raw == null || accountTypeOf(raw).isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new SessionToken(raw));
  }

  /// 令牌属于哪类账号，按前缀判断。
  ///
  /// @return 账号类型
  public AccountType accountType() {
    return accountTypeOf(value).orElseThrow();
  }

  /// 整个令牌 UTF-8 字节的 SHA-256，小写十六进制 64 个字符。Redis 键里用它。
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

  /// 账号类型的前缀。
  ///
  /// @param accountType 账号类型
  /// @return 前缀
  static String prefixOf(AccountType accountType) {
    String prefix = PREFIXES.get(Objects.requireNonNull(accountType, "accountType 不能为 null"));
    if (prefix == null) {
      throw new IllegalArgumentException("没有为 " + accountType + " 定义令牌前缀");
    }
    return prefix;
  }

  /// 按前缀和随机部分的格式判断账号类型。
  ///
  /// @param raw 原始值
  /// @return 账号类型；格式不对时为空
  private static Optional<AccountType> accountTypeOf(String raw) {
    for (Map.Entry<AccountType, String> entry : PREFIXES.entrySet()) {
      String prefix = entry.getValue();
      if (raw.startsWith(prefix) && RANDOM_PART.matcher(raw.substring(prefix.length())).matches()) {
        return Optional.of(entry.getKey());
      }
    }
    return Optional.empty();
  }

  /// 只输出前缀。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "SessionToken[" + prefixOf(accountType()) + "***]";
  }
}
