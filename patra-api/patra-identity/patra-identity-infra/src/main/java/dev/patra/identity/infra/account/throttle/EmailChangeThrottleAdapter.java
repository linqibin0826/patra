package dev.patra.identity.infra.account.throttle;

import dev.patra.identity.domain.account.policy.EmailChangePolicy;
import dev.patra.identity.domain.account.port.throttle.EmailChangeThrottlePort;
import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import dev.patra.identity.infra.session.support.ActionThrottleScript;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/// 改邮箱限流的 Redis 实现：每账号每天一个滑动窗口，没有冷却。
///
/// Redis 暂时不可用转成 {@link TemporarilyUnavailableException}（503）。
@Component
public final class EmailChangeThrottleAdapter implements EmailChangeThrottlePort {

  private final ActionThrottleScript script;
  private final EmailChangePolicy policy;

  /// 创建适配器。
  ///
  /// @param redis Redis 模板
  /// @param policy 限流规则
  public EmailChangeThrottleAdapter(StringRedisTemplate redis, EmailChangePolicy policy) {
    this.script = new ActionThrottleScript(Objects.requireNonNull(redis, "redis 不能为 null"));
    this.policy = Objects.requireNonNull(policy, "policy 不能为 null");
  }

  /// 申请一次：判每天的窗口并计数。
  ///
  /// @param userId 用户 ID
  /// @param now 当前时间
  /// @return 放行时为空；被限时是还要等的时长
  @Override
  public Optional<Duration> acquire(long userId, Instant now) {
    long wait =
        script.run(
            ActionThrottleScript.NO_COOLDOWN_KEY,
            Duration.ZERO,
            List.of(
                new ActionThrottleScript.Window(
                    key(userId), EmailChangePolicy.DAY_WINDOW, policy.perAccountDaily())),
            now,
            true);
    return wait > 0 ? Optional.of(Duration.ofMillis(wait)) : Optional.empty();
  }

  /// 键：按用户 ID。
  ///
  /// @param userId 用户 ID
  /// @return 键名
  static String key(long userId) {
    return "idn:email-change:day:user:" + userId;
  }
}
