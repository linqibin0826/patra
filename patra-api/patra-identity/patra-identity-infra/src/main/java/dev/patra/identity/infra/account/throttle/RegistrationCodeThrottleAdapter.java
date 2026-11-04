package dev.patra.identity.infra.account.throttle;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.policy.ProviderRegistrationPolicy;
import dev.patra.identity.domain.account.port.throttle.RegistrationCodeThrottlePort;
import dev.patra.identity.infra.session.support.ActionThrottleScript;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/// 发验证码限流的 Redis 实现：邮箱、IP 都哈希后进键名；IP 取不到时所有未知来源共用 `unknown` 一个桶。
@Component
public final class RegistrationCodeThrottleAdapter implements RegistrationCodeThrottlePort {

  private final ActionThrottleScript script;
  private final ProviderRegistrationPolicy policy;

  /// 创建适配器。
  ///
  /// @param redis Redis 模板
  /// @param policy 限流规则
  public RegistrationCodeThrottleAdapter(
      StringRedisTemplate redis, ProviderRegistrationPolicy policy) {
    this.script = new ActionThrottleScript(Objects.requireNonNull(redis, "redis 不能为 null"));
    this.policy = Objects.requireNonNull(policy, "policy 不能为 null");
  }

  /// 发一次：判三个维度并计数。
  ///
  /// @param email 规范化之后的邮箱
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @param now 当前时间
  /// @return 放行时为空；被限时是还要等的时长
  @Override
  public Optional<Duration> acquire(EmailAddress email, String clientIp, Instant now) {
    String emailHash = ActionThrottleScript.keyHash(email.value());
    String ipSegment = clientIp == null ? "unknown" : ActionThrottleScript.keyHash(clientIp);
    long wait =
        script.run(
            "idn:oauth-code:cooldown:email:" + emailHash,
            policy.resendCooldown(),
            List.of(
                new ActionThrottleScript.Window(
                    "idn:oauth-code:hour:email:" + emailHash,
                    ProviderRegistrationPolicy.HOUR_WINDOW,
                    policy.perEmailHourly()),
                new ActionThrottleScript.Window(
                    "idn:oauth-code:hour:ip:" + ipSegment,
                    ProviderRegistrationPolicy.HOUR_WINDOW,
                    policy.perIpHourly())),
            now,
            true);
    return wait > 0 ? Optional.of(Duration.ofMillis(wait)) : Optional.empty();
  }
}
