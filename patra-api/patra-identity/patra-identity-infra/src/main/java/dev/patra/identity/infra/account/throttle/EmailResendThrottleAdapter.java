package dev.patra.identity.infra.account.throttle;

import dev.patra.identity.domain.account.policy.EmailResendPolicy;
import dev.patra.identity.domain.account.port.throttle.EmailResendThrottlePort;
import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import dev.patra.identity.infra.session.support.ActionThrottleScript;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/// 重发验证邮件限流的 Redis 实现：冷却 + 账号小时、账号天、IP 小时三个滑动窗口，走通用脚本。
///
/// 用户 ID 进键名（不是个人信息），IP 哈希后进键名；IP 未知时不带 IP 窗口。
/// Redis 暂时不可用转成 {@link TemporarilyUnavailableException}（503）。
@Component
public final class EmailResendThrottleAdapter implements EmailResendThrottlePort {

  /// IP 未知时第四个键的占位，只出现在 {@link #keys} 的返回值里，脚本不会碰它。
  static final String NO_IP_KEY = "idn:email-resend:hour:ip:-";

  private final ActionThrottleScript script;
  private final EmailResendPolicy policy;

  /// 创建适配器。
  ///
  /// @param redis Redis 模板
  /// @param policy 限流规则
  public EmailResendThrottleAdapter(StringRedisTemplate redis, EmailResendPolicy policy) {
    this.script = new ActionThrottleScript(Objects.requireNonNull(redis, "redis 不能为 null"));
    this.policy = Objects.requireNonNull(policy, "policy 不能为 null");
  }

  /// 申请发一封：判四个维度并计数。
  ///
  /// @param userId 用户 ID
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @param now 当前时间
  /// @return 放行时为空；被限时是还要等的时长
  @Override
  public Optional<Duration> acquire(long userId, String clientIp, Instant now) {
    List<String> keys = keys(userId, clientIp);
    List<ActionThrottleScript.Window> windows = new ArrayList<>();
    windows.add(
        new ActionThrottleScript.Window(
            keys.get(1), EmailResendPolicy.HOUR_WINDOW, policy.perAccountHourly()));
    windows.add(
        new ActionThrottleScript.Window(
            keys.get(2), EmailResendPolicy.DAY_WINDOW, policy.perAccountDaily()));
    if (clientIp != null) {
      windows.add(
          new ActionThrottleScript.Window(
              keys.get(3), EmailResendPolicy.HOUR_WINDOW, policy.perIpHourly()));
    }
    long wait = script.run(keys.get(0), policy.cooldown(), windows, now, true);
    return wait > 0 ? Optional.of(Duration.ofMillis(wait)) : Optional.empty();
  }

  /// 只计冷却和两个按账号的窗口，不判、不计 IP。
  ///
  /// @param userId 用户 ID
  /// @param now 当前时间
  @Override
  public void markSent(long userId, Instant now) {
    List<String> keys = keys(userId, null);
    script.run(
        keys.get(0),
        policy.cooldown(),
        List.of(
            new ActionThrottleScript.Window(
                keys.get(1), EmailResendPolicy.HOUR_WINDOW, policy.perAccountHourly()),
            new ActionThrottleScript.Window(
                keys.get(2), EmailResendPolicy.DAY_WINDOW, policy.perAccountDaily())),
        now,
        false);
  }

  /// 四个键：冷却、账号小时窗口、账号天窗口、IP 小时窗口。
  ///
  /// @param userId 用户 ID
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @return 键名
  static List<String> keys(long userId, String clientIp) {
    String ipKey =
        clientIp == null
            ? NO_IP_KEY
            : "idn:email-resend:hour:ip:" + ActionThrottleScript.keyHash(clientIp);
    return List.of(
        "idn:email-resend:cooldown:user:" + userId,
        "idn:email-resend:hour:user:" + userId,
        "idn:email-resend:day:user:" + userId,
        ipKey);
  }
}
