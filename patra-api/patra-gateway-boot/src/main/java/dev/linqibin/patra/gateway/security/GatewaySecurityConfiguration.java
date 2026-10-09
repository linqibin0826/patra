package dev.linqibin.patra.gateway.security;

import dev.linqibin.patra.identity.session.RedisSessionStore;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/// 网关鉴权的装配：会话存储、签名器、两条过滤器链、出站头过滤器。
///
/// 设计：`docs/patra/specs/2026-10-09-gateway-auth-design.md`。
@Configuration(proxyBeanMethods = false)
public class GatewaySecurityConfiguration {

  /// Redis 里的会话存储，和 identity 共用同一份契约；网关只查和续期。
  ///
  /// @param redis Redis 模板，Boot 按 `spring.data.redis.*` 装配
  /// @param clock 容器里的时钟（starter-core 提供）
  /// @return 存储
  @Bean
  public RedisSessionStore redisSessionStore(StringRedisTemplate redis, Clock clock) {
    return new RedisSessionStore(redis, clock);
  }
}
