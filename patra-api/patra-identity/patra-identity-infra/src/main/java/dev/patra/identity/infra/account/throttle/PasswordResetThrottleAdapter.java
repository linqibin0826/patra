package dev.patra.identity.infra.account.throttle;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.policy.PasswordResetPolicy;
import dev.patra.identity.domain.account.port.throttle.PasswordResetThrottlePort;
import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import dev.patra.identity.infra.session.support.ActionThrottleScript;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/// 找回密码限流的 Redis 实现：申请按邮箱冷却 + 邮箱天窗口 + IP 小时窗口；提交按 IP 分钟窗口。
///
/// 邮箱、IP 都哈希后进键名；IP 取不到时所有未知来源共用 `unknown` 一个桶，不放行：提交只有 IP 这一维，
/// 跳过就等于没有限制。
/// Redis 暂时不可用转成 {@link TemporarilyUnavailableException}（503）。
@Component
public final class PasswordResetThrottleAdapter implements PasswordResetThrottlePort {

  private final ActionThrottleScript script;
  private final PasswordResetPolicy policy;

  /// 创建适配器。
  ///
  /// @param redis Redis 模板
  /// @param policy 限流规则
  public PasswordResetThrottleAdapter(StringRedisTemplate redis, PasswordResetPolicy policy) {
    this.script = new ActionThrottleScript(Objects.requireNonNull(redis, "redis 不能为 null"));
    this.policy = Objects.requireNonNull(policy, "policy 不能为 null");
  }

  /// 申请一次：判三个维度并计数。
  ///
  /// @param email 规范化之后的邮箱
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @param now 当前时间
  /// @return 放行时为空；被限时是还要等的时长
  @Override
  public Optional<Duration> acquireRequest(EmailAddress email, String clientIp, Instant now) {
    List<String> keys = requestKeys(email, clientIp);
    List<ActionThrottleScript.Window> windows =
        List.of(
            new ActionThrottleScript.Window(
                keys.get(1), PasswordResetPolicy.DAY_WINDOW, policy.perEmailDaily()),
            new ActionThrottleScript.Window(
                keys.get(2), PasswordResetPolicy.HOUR_WINDOW, policy.perIpHourly()));
    return toWait(script.run(keys.get(0), policy.cooldown(), windows, now, true));
  }

  /// 提交一次：判 IP 分钟窗口并计数；IP 未知时计进共用的 `unknown` 桶。
  ///
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @param now 当前时间
  /// @return 放行时为空；被限时是还要等的时长
  @Override
  public Optional<Duration> acquireSubmit(String clientIp, Instant now) {
    return toWait(
        script.run(
            ActionThrottleScript.NO_COOLDOWN_KEY,
            Duration.ZERO,
            List.of(
                new ActionThrottleScript.Window(
                    submitKey(clientIp),
                    PasswordResetPolicy.MINUTE_WINDOW,
                    policy.submitPerIpMinutely())),
            now,
            true));
  }

  /// 申请的键：邮箱冷却、邮箱天窗口、IP 小时窗口。
  ///
  /// @param email 邮箱
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @return 三个键名
  static List<String> requestKeys(EmailAddress email, String clientIp) {
    String emailHash = ActionThrottleScript.keyHash(email.value());
    return List.of(
        "idn:pw-reset:cooldown:email:" + emailHash,
        "idn:pw-reset:day:email:" + emailHash,
        "idn:pw-reset:hour:ip:" + ipSegment(clientIp));
  }

  /// 提交的键：IP 分钟窗口。
  ///
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @return 键名
  static String submitKey(String clientIp) {
    return "idn:pw-reset-submit:minute:ip:" + ipSegment(clientIp);
  }

  /// 键名里的 IP 段：哈希，取不到时是 `unknown`。
  ///
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @return 键名片段
  private static String ipSegment(String clientIp) {
    return clientIp == null ? "unknown" : ActionThrottleScript.keyHash(clientIp);
  }

  /// 脚本返回值转成等待时长。
  ///
  /// @param wait 毫秒，0 表示放行
  /// @return 放行时为空
  private static Optional<Duration> toWait(long wait) {
    return wait > 0 ? Optional.of(Duration.ofMillis(wait)) : Optional.empty();
  }
}
