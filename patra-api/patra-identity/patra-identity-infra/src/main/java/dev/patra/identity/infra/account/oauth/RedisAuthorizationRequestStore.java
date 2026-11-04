package dev.patra.identity.infra.account.oauth;

import dev.patra.identity.domain.account.port.oauth.AuthorizationRequestStorePort;
import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import dev.patra.identity.infra.session.support.TransientRedisFailures;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/// 授权请求存储的 Redis 实现：`idn:oauth:authz:<state>`，取出用 `GETDEL`。
@Component
@RequiredArgsConstructor
public class RedisAuthorizationRequestStore implements AuthorizationRequestStorePort {

  private final StringRedisTemplate redis;

  /// 存一个授权请求。
  ///
  /// @param state `state`
  /// @param payload 序列化之后的授权请求
  /// @param ttl 有效期
  @Override
  public void save(String state, String payload, Duration ttl) {
    execute(
        () -> {
          redis.opsForValue().set(key(state), payload, ttl);
          return null;
        });
  }

  /// 读一个授权请求。
  ///
  /// @param state `state`
  /// @return 序列化之后的授权请求
  @Override
  public Optional<String> find(String state) {
    return Optional.ofNullable(execute(() -> redis.opsForValue().get(key(state))));
  }

  /// 取出并删除。
  ///
  /// @param state `state`
  /// @return 序列化之后的授权请求
  @Override
  public Optional<String> consume(String state) {
    return Optional.ofNullable(execute(() -> redis.opsForValue().getAndDelete(key(state))));
  }

  /// 键。
  ///
  /// @param state `state`
  /// @return 键
  static String key(String state) {
    return "idn:oauth:authz:" + state;
  }

  /// 执行 Redis 操作，暂时失败转成 503。
  ///
  /// @param operation 操作
  /// @param <T> 返回类型
  /// @return 操作的返回值
  private static <T> T execute(Supplier<T> operation) {
    try {
      return operation.get();
    } catch (DataAccessException e) {
      if (TransientRedisFailures.isTransient(e)) {
        throw new TemporarilyUnavailableException(e);
      }
      throw e;
    }
  }
}
