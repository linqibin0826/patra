package dev.linqibin.patra.starter.security.support;

import dev.linqibin.patra.starter.security.test.TestIdentity;
import dev.linqibin.patra.starter.security.test.TestSigningKey;
import java.time.Instant;
import java.util.Date;

/// 集成测试里构造异常断言的帮助方法。合法的断言用 `TestIdentity`，这里只放异常的。
public final class SecurityITAssertions {

  /// 工具类，不允许实例化。
  private SecurityITAssertions() {}

  /// 不是 JWT 的字符串。
  ///
  /// @return 固定文本
  public static String garbage() {
    return "not-a-jwt";
  }

  /// 签名正确但两分钟前就过期的断言，容差 30 秒也救不回来。
  ///
  /// @return 紧凑序列化的断言
  public static String expired() {
    Instant past = Instant.now().minusSeconds(180);
    return TestSigningKey.sign(
        TestSigningKey.claims(TestIdentity.user())
            .issueTime(Date.from(past))
            .expirationTime(Date.from(past.plusSeconds(60)))
            .build());
  }

  /// 签名正确但 `sub` 不是正整数的断言。
  ///
  /// @return 紧凑序列化的断言
  public static String malformed() {
    return TestSigningKey.sign(TestSigningKey.claims(TestIdentity.user()).subject("abc").build());
  }
}
