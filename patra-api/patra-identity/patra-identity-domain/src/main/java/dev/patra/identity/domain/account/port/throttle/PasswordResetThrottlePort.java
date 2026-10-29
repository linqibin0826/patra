package dev.patra.identity.domain.account.port.throttle;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/// 找回密码的限流：申请按邮箱冷却、邮箱每天、IP 每小时；提交按 IP 每分钟。
///
/// 放行即已计数，之后的步骤失败也不退还。时间由调用方按注入的 `Clock` 传入。
/// Redis 不可用时抛 {@link TemporarilyUnavailableException}（503）。
public interface PasswordResetThrottlePort {

  /// 申请一次重设：三个维度都没超就计数并放行，否则告诉调用方还要等多久（取各维度里最长的）。
  /// 对任何邮箱都计数（含未注册的）：只给已注册的计数，429 本身就成了「已注册」的信号。
  ///
  /// @param email 规范化之后的邮箱
  /// @param clientIp 客户端 IP，可以为 `null`（未知时跳过 IP 维度）
  /// @param now 当前时间
  /// @return 放行时为空；被限时是还要等的时长
  Optional<Duration> acquireRequest(EmailAddress email, String clientIp, Instant now);

  /// 提交一次重设令牌：同 IP 每分钟的上限。格式不对的提交也计：这是对猜令牌的限制。
  ///
  /// @param clientIp 客户端 IP，可以为 `null`（未知时放行）
  /// @param now 当前时间
  /// @return 放行时为空；被限时是还要等的时长
  Optional<Duration> acquireSubmit(String clientIp, Instant now);
}
