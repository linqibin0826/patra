package dev.linqibin.starter.redisson.autoconfigure;

import dev.linqibin.starter.redisson.config.RedissonProperties;
import dev.linqibin.starter.redisson.listener.LockObserver;
import dev.linqibin.starter.redisson.lock.LockAspect;
import dev.linqibin.starter.redisson.lock.LockExecutor;
import dev.linqibin.starter.redisson.lock.LockKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/// 分布式锁自动配置。
///
/// 配置所有锁相关的 Bean（LockKeyGenerator、LockExecutor、LockAspect）。
/// 支持通过 LockObserver SPI 扩展锁生命周期观察（可选）。
///
/// @author Patra Team
/// @since 1.0.0
@Slf4j
@AutoConfiguration(after = RedissonAutoConfiguration.class)
@ConditionalOnClass({RedissonClient.class, RLock.class})
@ConditionalOnBean(RedissonClient.class)
@ConditionalOnProperty(
    prefix = "patra.redisson.lock",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(RedissonProperties.class)
public class LockAutoConfiguration {

  /// 配置锁键生成器。
  ///
  /// @param properties Redisson 配置属性
  /// @return LockKeyGenerator
  @Bean
  public LockKeyGenerator lockKeyGenerator(RedissonProperties properties) {
    log.info("初始化 LockKeyGenerator");
    return new LockKeyGenerator(properties);
  }

  /// 配置锁执行器。
  ///
  /// 自动注入锁观察者（如果存在），否则为 null。
  /// 用户可通过实现 LockObserver SPI 接口扩展锁生命周期观察。
  ///
  /// @param redissonClient Redisson 客户端
  /// @param lockObserver   锁观察者（可选）
  /// @return LockExecutor
  @Bean
  public LockExecutor lockExecutor(
      RedissonClient redissonClient, @Autowired(required = false) LockObserver lockObserver) {
    log.info("初始化 LockExecutor (observer={})", lockObserver != null ? "启用" : "禁用");
    return new LockExecutor(redissonClient, lockObserver);
  }

  /// 配置锁切面。
  ///
  /// @param lockKeyGenerator 锁键生成器
  /// @param lockExecutor     锁执行器
  /// @param properties       Redisson 配置属性
  /// @return LockAspect
  @Bean
  public LockAspect lockAspect(
      LockKeyGenerator lockKeyGenerator, LockExecutor lockExecutor, RedissonProperties properties) {
    log.info("初始化 LockAspect (AOP 切面)");
    return new LockAspect(lockKeyGenerator, lockExecutor, properties);
  }
}
