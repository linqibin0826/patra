package dev.patra.identity.app.account.service;

import dev.patra.identity.domain.account.exception.LoginTemporarilyLockedException;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PlainPassword;
import dev.patra.identity.domain.account.policy.PasswordPolicy;
import dev.patra.identity.domain.account.port.throttle.LoginAttempt;
import dev.patra.identity.domain.account.port.throttle.LoginThrottlePort;
import dev.patra.tool.core.security.AccountType;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 受登录失败限制的一次密码校验：开始尝试 → 校验 → 结算，每次放行的尝试恰好结算一次。
///
/// 密码登录、再认证、改密码共用。查找方式、返回值和不匹配时的异常各不相同，由调用方给出，这里只管结算：
///
/// - 校验有结果：按成功结算，返回结果。
/// - 校验没结果，或密码超过登录长度上限（不调校验，也就不查库、不做哈希）：按失败结算，
///   这次失败让账号处于锁定期时抛 429，否则抛调用方给的异常。
/// - 校验中途出错：按取消结算，不计失败，原来的错误照常抛出。
///
/// 调用方要在事务外调用：校验要做慢哈希，结算要写 Redis（`transaction.md` 第 2 节）。
@Slf4j
@Component
@RequiredArgsConstructor
public class ThrottledPasswordCheck {

  private final LoginThrottlePort loginThrottle;

  /// 执行一次受限制的校验。
  ///
  /// @param email 失败限制按它计
  /// @param password 明文密码，用来判断是否超过登录长度上限
  /// @param check 校验；通过时返回调用方要的结果，不通过时返回空，不通过也要耗时一样
  /// @param mismatch 校验不通过且没有处于锁定期时要抛的异常
  /// @param <T> 校验结果的类型
  /// @return 校验结果
  /// @throws LoginTemporarilyLockedException 锁定期内、在途已满，或这次失败触发上锁
  public <T> T run(
      EmailAddress email,
      PlainPassword password,
      Supplier<Optional<T>> check,
      Supplier<? extends RuntimeException> mismatch) {
    LoginAttempt attempt = loginThrottle.begin(AccountType.USER, email);
    Optional<T> matched;
    try {
      matched =
          password.length() <= PasswordPolicy.MAX_LOGIN_LENGTH ? check.get() : Optional.empty();
    } catch (RuntimeException e) {
      cancelQuietly(attempt);
      throw e;
    }
    if (matched.isEmpty()) {
      Optional<Duration> lock = loginThrottle.recordFailure(attempt);
      if (lock.isPresent()) {
        throw new LoginTemporarilyLockedException(lock.get());
      }
      throw mismatch.get();
    }
    loginThrottle.recordSuccess(attempt);
    return matched.get();
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
