package dev.patra.identity.infra.session.throttle;

import dev.patra.identity.domain.session.model.vo.DeviceId;
import dev.patra.identity.domain.session.policy.NewDeviceLoginPolicy;
import dev.patra.identity.domain.session.port.throttle.NewDeviceMailThrottlePort;
import dev.patra.identity.infra.session.support.ActionThrottleScript;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/// 新设备提醒限流的 Redis 实现：每个「账号 + 设备」一个冷却，没有窗口（PAP-99 设计第 12.3 节）。
///
/// 键里放设备标识的哈希而不是原文：它相当于一个长期 Cookie 的值。没有设备标识的登录共用 `none` 桶。
/// Redis 暂时不可用转成 `TemporarilyUnavailableException`（503），由登录处理器接住只记日志。
@Component
public final class NewDeviceMailThrottleAdapter implements NewDeviceMailThrottlePort {

  private final StringRedisTemplate redis;
  private final ActionThrottleScript script;
  private final NewDeviceLoginPolicy policy;

  /// 创建适配器。
  ///
  /// @param redis Redis 模板
  /// @param policy 新设备提醒的规则
  public NewDeviceMailThrottleAdapter(StringRedisTemplate redis, NewDeviceLoginPolicy policy) {
    this.redis = Objects.requireNonNull(redis, "redis 不能为 null");
    this.script = new ActionThrottleScript(redis);
    this.policy = Objects.requireNonNull(policy, "policy 不能为 null");
  }

  /// 取一次发信许可：冷却期内拒绝，放行即已计数。
  ///
  /// @param userId 用户 ID
  /// @param device 设备标识，可以为 `null`
  /// @param now 当前时间
  /// @return 放行时为 `true`
  @Override
  public boolean acquire(long userId, DeviceId device, Instant now) {
    return script.run(key(userId, device), policy.cooldown(), List.of(), now, true) == 0;
  }

  /// 清掉一台设备的冷却：删掉它的冷却键。
  ///
  /// @param userId 用户 ID
  /// @param device 设备标识，可以为 `null`
  @Override
  public void reset(long userId, DeviceId device) {
    redis.delete(key(userId, device));
  }

  /// 冷却键：账号 + 设备标识的哈希，没有设备标识时是 `none`。
  ///
  /// @param userId 用户 ID
  /// @param device 设备标识，可以为 `null`
  /// @return 键名
  static String key(long userId, DeviceId device) {
    String suffix = device == null ? "none" : ActionThrottleScript.keyHash(device.value());
    return "idn:new-device-mail:user:" + userId + ":" + suffix;
  }
}
