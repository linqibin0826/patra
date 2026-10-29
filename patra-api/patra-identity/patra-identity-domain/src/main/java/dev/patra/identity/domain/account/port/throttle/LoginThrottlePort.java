package dev.patra.identity.domain.account.port.throttle;

import dev.patra.identity.domain.account.exception.LoginTemporarilyLockedException;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import dev.patra.tool.core.security.AccountType;
import java.time.Duration;
import java.util.Optional;

/// 登录失败限制。只有确认的失败才计数、才上锁；正在校验的尝试单独登记，只用来限制并发。
///
/// 每次尝试先 {@link #begin}，校验之后必定结算一次：成功、失败或取消。
/// Redis 不可用时，所有方法都抛 {@link TemporarilyUnavailableException}。
public interface LoginThrottlePort {

  /// 开始一次尝试，在校验密码之前调用。
  ///
  /// @param accountType 账号类型
  /// @param email 邮箱
  /// @return 被放行的尝试
  /// @throws LoginTemporarilyLockedException 处于锁定期，或「失败次数 + 在途次数」已到上限
  LoginAttempt begin(AccountType accountType, EmailAddress email);

  /// 按成功结算：删掉在途登记，清零失败计数，不动锁。
  ///
  /// @param attempt 尝试
  void recordSuccess(LoginAttempt attempt);

  /// 按失败结算：删掉在途登记，处于锁定期就不计数，否则失败计数加一，达到上限就上锁。
  ///
  /// @param attempt 尝试
  /// @return 处于锁定期时（包括这次刚上锁）返回剩余时间，否则为空
  Optional<Duration> recordFailure(LoginAttempt attempt);

  /// 按取消结算：只删掉在途登记，不计失败。用于中途出错（哈希排队超时、数据库异常等）。
  ///
  /// @param attempt 尝试
  void cancel(LoginAttempt attempt);

  /// 清零：删掉失败计数和锁，在途登记不动（自然过期）。重设密码成功后用。
  ///
  /// @param accountType 账号类型
  /// @param email 邮箱
  void clear(AccountType accountType, EmailAddress email);
}
