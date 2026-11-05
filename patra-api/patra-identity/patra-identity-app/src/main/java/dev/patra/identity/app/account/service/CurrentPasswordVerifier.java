package dev.patra.identity.app.account.service;

import dev.patra.identity.domain.account.exception.CurrentPasswordMismatchException;
import dev.patra.identity.domain.account.exception.LoginTemporarilyLockedException;
import dev.patra.identity.domain.account.model.aggregate.UserPasswordCredential;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PasswordHash;
import dev.patra.identity.domain.account.model.vo.PlainPassword;
import dev.patra.identity.domain.account.port.hashing.PasswordHashingPort;
import dev.patra.identity.domain.account.port.repository.UserPasswordCredentialRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 已登录用户的密码校验：再认证、改密码共用，和登录共用同一个失败限制。
///
/// 结算流程在 {@link ThrottledPasswordCheck}；这里按用户 ID 找凭据（凭据不存在时拿假哈希校验一次，耗时一样），
/// 对则返回哈希，错则 401 并带 `PASSWORD_MISMATCH`。
@Component
@RequiredArgsConstructor
public class CurrentPasswordVerifier {

  private final UserPasswordCredentialRepository credentials;
  private final PasswordHashingPort passwordHashing;
  private final ThrottledPasswordCheck passwordCheck;

  /// 校验密码，返回匹配上的那个哈希：调用方在行锁下拿它和凭据再比一次，并发改密码时后到的一方会发现
  /// 自己校验过的已经不是当前密码。
  ///
  /// @param email 用户当前邮箱（失败限制按它计）
  /// @param userId 用户 ID
  /// @param password 明文密码
  /// @return 匹配上的密码哈希
  /// @throws CurrentPasswordMismatchException 密码不正确
  /// @throws LoginTemporarilyLockedException 锁定期内，或这次失败触发上锁
  public PasswordHash verify(EmailAddress email, long userId, PlainPassword password) {
    return passwordCheck.run(
        email, password, () -> matches(userId, password), CurrentPasswordMismatchException::new);
  }

  /// 凭据不存在时拿假哈希校验一次，然后返回空。
  ///
  /// @param userId 用户 ID
  /// @param password 明文密码
  /// @return 匹配上的哈希；不匹配或没有凭据时为空
  private Optional<PasswordHash> matches(long userId, PlainPassword password) {
    Optional<UserPasswordCredential> credential = credentials.findByUserId(userId);
    if (credential.isEmpty()) {
      passwordHashing.verifyAgainstDummy(password);
      return Optional.empty();
    }
    PasswordHash hash = credential.get().getPasswordHash();
    return passwordHashing.matches(password, hash) ? Optional.of(hash) : Optional.empty();
  }
}
