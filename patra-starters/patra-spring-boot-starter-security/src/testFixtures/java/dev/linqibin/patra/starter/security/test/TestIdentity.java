package dev.linqibin.patra.starter.security.test;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import org.springframework.http.HttpHeaders;

/// 测试里构造「已登录请求」用的请求头。
///
/// 已登录的请求：把 `headers()` 返回的头加到请求上。匿名的请求：什么都不加。
/// 这种写法走的是真实的安全过滤器和真实的验签，切片测试和整应用测试用法相同。
public final class TestIdentity {

  /// 默认的用户 ID。
  public static final long USER_ID = 1001L;

  /// 默认的会话 ID。
  public static final long SESSION_ID = 2001L;

  /// 工具类，不允许实例化。
  private TestIdentity() {}

  /// 默认用户。
  ///
  /// @return 用户 ID 为 `USER_ID`、会话 ID 为 `SESSION_ID` 的前台网页端用户
  public static CurrentUser user() {
    return user(USER_ID);
  }

  /// 指定用户 ID、其余字段取默认值的用户。
  ///
  /// @param userId 用户 ID
  /// @return 用户
  public static CurrentUser user(long userId) {
    return CurrentUser.of(userId, SESSION_ID, AccountType.USER, ClientType.WEB);
  }

  /// 默认用户的断言。
  ///
  /// @return 用测试私钥签出的断言
  public static String assertion() {
    return assertion(user());
  }

  /// 指定用户的断言。
  ///
  /// @param user 用户
  /// @return 用测试私钥签出的断言
  public static String assertion(CurrentUser user) {
    return TestSigningKey.signer().sign(user);
  }

  /// 默认用户的请求头。
  ///
  /// @return 一个 `Authorization: Bearer` 头
  public static HttpHeaders headers() {
    return headers(user());
  }

  /// 指定用户 ID 的请求头。
  ///
  /// @param userId 用户 ID
  /// @return 一个 `Authorization: Bearer` 头
  public static HttpHeaders headers(long userId) {
    return headers(user(userId));
  }

  /// 指定用户的请求头。
  ///
  /// @param user 用户
  /// @return 一个 `Authorization: Bearer` 头，值是用测试私钥签出的断言
  public static HttpHeaders headers(CurrentUser user) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(assertion(user));
    return headers;
  }
}
