package dev.patra.identity.domain.account.port.hashing;

import dev.patra.identity.domain.account.model.vo.PasswordHash;
import dev.patra.identity.domain.account.model.vo.PlainPassword;
import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;

/// 密码哈希。同时进行的计算有上限，排队超时抛 {@link TemporarilyUnavailableException}。
public interface PasswordHashingPort {

  /// 计算哈希。
  ///
  /// @param password 明文密码
  /// @return 哈希
  PasswordHash hash(PlainPassword password);

  /// 校验密码。
  ///
  /// @param password 明文密码
  /// @param hash 存储的哈希
  /// @return 匹配时为 `true`
  boolean matches(PlainPassword password, PasswordHash hash);

  /// 拿一个固定的假哈希做一次校验，结果丢弃。用户不存在时调用，让耗时和「密码错」一样。
  ///
  /// @param password 明文密码
  void verifyAgainstDummy(PlainPassword password);
}
