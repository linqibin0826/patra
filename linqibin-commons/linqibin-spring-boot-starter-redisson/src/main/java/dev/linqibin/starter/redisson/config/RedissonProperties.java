package dev.linqibin.starter.redisson.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// Redisson 配置属性。
///
/// 集成 Redisson 官方 Starter，提供分布式锁和可观测性的自定义配置。
///
/// @author Patra Team
/// @since 1.0.0
@Data
@ConfigurationProperties(prefix = "linqibin.starter.redisson")
public class RedissonProperties {

  /// 是否启用 Redisson Starter，默认 true
  private boolean enabled = true;

  /// 看门狗超时时间（毫秒）。
  ///
  /// 如果未显式设置 leaseTime，Redisson 会使用此值作为锁的默认过期时间，
  /// 并在锁持有期间自动续期（看门狗机制）。
  ///
  /// 默认: 30000 ms（30 秒）
  private long lockWatchdogTimeout = 30000;

  /// 分布式锁配置
  private LockProperties lock = new LockProperties();

  /// 分布式锁配置。
  @Data
  public static class LockProperties {

    /// 是否启用分布式锁，默认 true
    private boolean enabled = true;

    /// 默认等待时间（毫秒）。
    ///
    /// 获取锁的最大等待时间，超时未获取到锁则抛出 LockAcquisitionException。
    ///
    /// 可通过 @DistributedLock 注解的 waitTime 属性覆盖。
    ///
    /// 默认: 3000 ms（3 秒）
    private long defaultWaitTime = 3000;

    /// 默认租约时间（毫秒）。
    ///
    /// 锁的自动过期时间，防止死锁。设置为 -1 时启用看门狗机制（自动续期）。
    ///
    /// 可通过 @DistributedLock 注解的 leaseTime 属性覆盖。
    ///
    /// 默认: -1（启用看门狗）
    private long defaultLeaseTime = -1;

    /// 锁键前缀。
    ///
    /// 所有分布式锁的 Redis 键都会添加此前缀，便于统一管理和监控。
    ///
    /// 默认: "patra:lock:"
    private String keyPrefix = "patra:lock:";
  }
}
