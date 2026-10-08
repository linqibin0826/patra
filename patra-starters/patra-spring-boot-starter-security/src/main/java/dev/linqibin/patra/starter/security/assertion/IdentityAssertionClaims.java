package dev.linqibin.patra.starter.security.assertion;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.authentication.MalformedIdentityException;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.security.oauth2.jwt.Jwt;

/// 身份断言的格式：头和载荷里的固定值、自定义声明的名字，以及从已验签的断言得到当前用户。
///
/// 签名器、验签器、测试支持都只认这里的常量，断言的格式只有这一份定义。
public final class IdentityAssertionClaims {

  /// 头里的 `typ`。显式类型，防止和别的 JWT 混用。
  public static final String TYPE = "patra-identity+jwt";

  /// 载荷里的 `iss`：只有网关签发断言。
  public static final String ISSUER = "patra-gateway";

  /// 载荷里的 `aud`：整个 Patra 是一个信任域，断言可以在服务之间转发。
  public static final String AUDIENCE = "patra";

  /// 会话 ID 的声明名。
  public static final String SESSION_ID = "sid";

  /// 账号类型的声明名。
  public static final String ACCOUNT_TYPE = "account_type";

  /// 客户端类型的声明名。
  public static final String CLIENT_TYPE = "client_type";

  /// 断言的有效期：只覆盖一次请求和它的同步调用。
  public static final Duration LIFETIME = Duration.ofSeconds(60);

  /// 规范写法的正整数：没有符号、没有前导零、只有 ASCII 数字、最多 19 位。
  private static final Pattern POSITIVE_LONG = Pattern.compile("[1-9][0-9]{0,18}");

  /// 工具类，不允许实例化。
  private IdentityAssertionClaims() {}

  /// 从已验签的断言得到当前用户。
  ///
  /// @param jwt 已验签的断言
  /// @return 当前用户
  /// @throws MalformedIdentityException `sub`、`sid` 不是正整数，或两个类型不认识时
  public static CurrentUser toCurrentUser(Jwt jwt) {
    long userId = positiveLong(jwt.getSubject(), "sub");
    long sessionId = positiveLong(jwt.getClaimAsString(SESSION_ID), SESSION_ID);
    AccountType accountType =
        AccountType.fromCode(jwt.getClaimAsString(ACCOUNT_TYPE))
            .orElseThrow(() -> new MalformedIdentityException(ACCOUNT_TYPE + " 的值不认识"));
    ClientType clientType =
        ClientType.fromCode(jwt.getClaimAsString(CLIENT_TYPE))
            .orElseThrow(() -> new MalformedIdentityException(CLIENT_TYPE + " 的值不认识"));
    return CurrentUser.of(userId, sessionId, accountType, clientType);
  }

  /// 把规范写法的正整数解析成 long。
  ///
  /// @param value 声明的值，可能为 `null`
  /// @param claim 声明名，只用于错误信息
  /// @return 解析结果
  /// @throws MalformedIdentityException 写法不规范或超出 Long 范围时
  private static long positiveLong(String value, String claim) {
    if (value == null || !POSITIVE_LONG.matcher(value).matches()) {
      throw new MalformedIdentityException(claim + " 不是正整数");
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException overflow) {
      throw new MalformedIdentityException(claim + " 超出范围");
    }
  }
}
