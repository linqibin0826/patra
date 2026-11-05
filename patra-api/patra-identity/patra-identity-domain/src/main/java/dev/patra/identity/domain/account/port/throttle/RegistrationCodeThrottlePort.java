package dev.patra.identity.domain.account.port.throttle;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/// 第三方首次注册发验证码的限流：同邮箱冷却与小时窗口，同 IP 小时窗口。
///
/// Redis 暂时不可用时抛带 `DEP_UNAVAILABLE` 特征的异常（503）。
public interface RegistrationCodeThrottlePort {

  /// 发一次：判三个维度并计数。
  ///
  /// @param email 规范化之后的邮箱
  /// @param clientIp 客户端 IP，可以为 `null`，取不到的共用一个桶
  /// @param now 当前时间
  /// @return 放行时为空；被限时是还要等的时长
  Optional<Duration> acquire(EmailAddress email, String clientIp, Instant now);
}
