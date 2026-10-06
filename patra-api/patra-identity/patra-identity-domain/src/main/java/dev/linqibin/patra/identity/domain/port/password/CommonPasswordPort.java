package dev.linqibin.patra.identity.domain.port.password;

/// 常见密码名单。
@FunctionalInterface
public interface CommonPasswordPort {

  /// 比较键是否在名单里。比较键的算法见 `PlainPassword.comparisonKeyOf`。
  ///
  /// @param comparisonKey 比较键
  /// @return 在名单里时为 `true`
  boolean isCommon(String comparisonKey);
}
