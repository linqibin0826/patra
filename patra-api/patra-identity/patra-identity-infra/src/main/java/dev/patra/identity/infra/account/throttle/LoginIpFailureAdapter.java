package dev.patra.identity.infra.account.throttle;

import dev.patra.identity.domain.account.port.throttle.LoginIpFailurePort;
import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import dev.patra.identity.infra.session.support.TransientRedisFailures;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/// 按客户端网络的登录失败计数的 Redis 实现。
///
/// 一个键，键名带网络的 SHA-256。记一次失败是一段 Lua 脚本，`INCR` 和第一次时设过期原子执行。
/// Redis 暂时不可用转成 {@link TemporarilyUnavailableException}。
@Component
public final class LoginIpFailureAdapter implements LoginIpFailurePort {

  private static final RedisScript<Long> RECORD_SCRIPT =
      RedisScript.of(new ClassPathResource("redis/login-ip-failure-record.lua"), Long.class);

  private final StringRedisTemplate redis;

  /// 创建适配器。
  ///
  /// @param redis Redis 模板
  public LoginIpFailureAdapter(StringRedisTemplate redis) {
    this.redis = Objects.requireNonNull(redis, "redis 不能为 null");
  }

  /// 读失败次数。
  ///
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @return 失败次数；拿不到 IP 时为 0
  @Override
  public int count(String clientIp) {
    Optional<String> network = ClientNetworkKey.of(clientIp);
    if (network.isEmpty()) {
      return 0;
    }
    String value = execute(() -> redis.opsForValue().get(key(network.get())));
    return value == null ? 0 : Integer.parseInt(value);
  }

  /// 记一次失败。
  ///
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @param window 计数窗口
  @Override
  public void record(String clientIp, Duration window) {
    ClientNetworkKey.of(clientIp)
        .ifPresent(
            network ->
                execute(
                    () ->
                        redis.execute(
                            RECORD_SCRIPT,
                            List.of(key(network)),
                            String.valueOf(window.toMillis()))));
  }

  /// 计数键。
  ///
  /// @param network 客户端网络的键
  /// @return Redis 键名
  static String key(String network) {
    return "idn:login-ip-failures:" + network;
  }

  /// 执行 Redis 调用，把暂时失败转成 503。
  ///
  /// @param call 调用
  /// @param <T> 返回值类型
  /// @return 返回值
  private static <T> T execute(Supplier<T> call) {
    try {
      return call.get();
    } catch (RuntimeException e) {
      if (TransientRedisFailures.isTransient(e)) {
        throw new TemporarilyUnavailableException(e);
      }
      throw e;
    }
  }
}
