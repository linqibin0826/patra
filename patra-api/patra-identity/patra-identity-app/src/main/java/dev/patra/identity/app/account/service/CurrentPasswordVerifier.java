package dev.patra.identity.app.account.service;

import dev.patra.identity.domain.account.exception.CurrentPasswordMismatchException;
import dev.patra.identity.domain.account.exception.LoginTemporarilyLockedException;
import dev.patra.identity.domain.account.model.aggregate.UserPasswordCredential;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PasswordHash;
import dev.patra.identity.domain.account.model.vo.PlainPassword;
import dev.patra.identity.domain.account.policy.PasswordPolicy;
import dev.patra.identity.domain.account.port.hashing.PasswordHashingPort;
import dev.patra.identity.domain.account.port.repository.UserPasswordCredentialRepository;
import dev.patra.identity.domain.account.port.throttle.LoginAttempt;
import dev.patra.identity.domain.account.port.throttle.LoginThrottlePort;
import dev.patra.tool.core.security.AccountType;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 已登录用户的密码校验：再认证、改密码共用，和登录共用同一个失败限制。
///
/// 开始一次尝试（锁定期 429）→ 校验（凭据不存在时拿假哈希校验一次，耗时一样）→ 结算：
/// 对则清零失败计数；错则计失败，触发上锁时 429，否则 401 并带 `PASSWORD_MISMATCH`；中途出错按取消结算。
@Slf4j
@Component
@RequiredArgsConstructor
public class CurrentPasswordVerifier {

  private final UserPasswordCredentialRepository credentials;
  private final PasswordHashingPort passwordHashing;
  private final LoginThrottlePort loginThrottle;

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
    LoginAttempt attempt = loginThrottle.begin(AccountType.USER, email);
    Optional<PasswordHash> matched;
    try {
      matched =
          password.length() <= PasswordPolicy.MAX_LOGIN_LENGTH
              ? matches(userId, password)
              : Optional.empty();
    } catch (RuntimeException e) {
      cancelQuietly(attempt);
      throw e;
    }
    if (matched.isEmpty()) {
      Optional<Duration> lock = loginThrottle.recordFailure(attempt);
      if (lock.isPresent()) {
        throw new LoginTemporarilyLockedException(lock.get());
      }
      throw new CurrentPasswordMismatchException();
    }
    loginThrottle.recordSuccess(attempt);
    return matched.get();
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

  /// 按取消结算。取消失败只记日志：在途登记会自动过期，不能盖掉原来的错误。
  ///
  /// @param attempt 尝试
  private void cancelQuietly(LoginAttempt attempt) {
    try {
      loginThrottle.cancel(attempt);
    } catch (RuntimeException e) {
      log.warn("取消登录尝试失败，在途登记会自动过期: {}", e.getMessage());
    }
  }
}
