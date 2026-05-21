package dev.linqibin.starter.redisson.autoconfigure;

import dev.linqibin.starter.redisson.config.RedissonProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/// Redisson 自动配置。
///
/// 基于 Redisson 官方 Starter，提供 RedissonClient Bean 和自定义配置
///
/// @author Patra Team
/// @since 1.0.0
@Slf4j
@AutoConfiguration(after = RedissonAutoConfigurationV2.class)
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnProperty(
    prefix = "linqibin.starter.redisson",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(RedissonProperties.class)
@Configuration
@RequiredArgsConstructor
public class RedissonAutoConfiguration {

  /// Redisson 配置属性
  private final RedissonProperties properties;

  /// 初始化日志。
  ///
  /// Redisson 官方 Starter 已经提供了 RedissonClient Bean，
  /// 这里只需要记录我们的自定义配置。
  @PostConstruct
  public void init() {
    log.info("Redisson Starter 已启用");
    log.info("  - lockWatchdogTimeout: {} ms", properties.getLockWatchdogTimeout());
    log.info("  - lock.keyPrefix: {}", properties.getLock().getKeyPrefix());
    log.info("  - lock.defaultWaitTime: {} ms", properties.getLock().getDefaultWaitTime());
    log.info("  - lock.defaultLeaseTime: {} ms", properties.getLock().getDefaultLeaseTime());
  }
}
