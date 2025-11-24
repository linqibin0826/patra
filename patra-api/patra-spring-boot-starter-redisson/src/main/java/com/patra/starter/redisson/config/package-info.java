///
/// Redisson 配置属性包
///
/// ## 职责
///
/// 定义 Redisson Starter 的配置属性类，支持通过 `application.yml` 自定义 Redis 客户端和分布式锁行为。
///
/// ## 核心组件
///
/// - `RedissonProperties`：Redisson 配置属性类（配置前缀：`patra.redisson`）
///
/// ## 配置示例
///
/// ```yaml
/// patra:
///   redisson:
///     enabled: true  # 是否启用 Redisson
///     lock-watchdog-timeout: 30000  # 看门狗超时时间（毫秒）
///
///     # 锁配置
///     lock:
///       enabled: true  # 是否启用分布式锁
///       key-prefix: "patra:lock:"  # 锁键前缀
///       default-wait-time: 0  # 默认等待时间（毫秒，0=不等待）
///       default-lease-time: -1  # 默认租期（毫秒，-1=启用看门狗）
///
///     # 可观测性配置
///     observability:
///       metrics-enabled: true   # Micrometer 指标
///       tracing-enabled: true   # SkyWalking 追踪
///       logging-enabled: true   # 日志记录
/// ```
///
/// ## RedissonProperties 属性说明
///
/// ### enabled
///
/// **类型**：`boolean`
/// **默认值**：`true`
/// **说明**：是否启用 Redisson 自动配置
///
/// ### lock-watchdog-timeout
///
/// **类型**：`long`
/// **默认值**：`30000`（30 秒）
/// **说明**：Redisson 看门狗超时时间（毫秒），看门狗每 `watchdogTimeout / 3` 检查一次锁是否仍被持有
///
/// ### lock.enabled
///
/// **类型**：`boolean`
/// **默认值**：`true`
/// **说明**：是否启用分布式锁功能（如果只需要 RedissonClient，可以禁用锁功能）
///
/// ### lock.key-prefix
///
/// **类型**：`String`
/// **默认值**：`"patra:lock:"`
/// **说明**：锁键前缀，自动添加到所有锁键前面，避免与其他服务冲突
///
/// **示例**：
/// ```java
/// @DistributedLock(key = "mesh-import:#{#year}")
/// // 实际锁键：patra:lock:mesh-import:2024
/// ```
///
/// ### lock.default-wait-time
///
/// **类型**：`long`
/// **默认值**：`0`（不等待）
/// **说明**：获取锁的默认等待时间（毫秒），0 表示不等待，立即返回失败
///
/// **可以在注解中覆盖**：
/// ```java
/// @DistributedLock(key = "user:#{#userId}", waitTime = 5000)  // 等待 5 秒
/// ```
///
/// ### lock.default-lease-time
///
/// **类型**：`long`
/// **默认值**：`-1`（启用看门狗）
/// **说明**：锁的默认租期（毫秒），-1 表示启用看门狗自动续期
///
/// **可以在注解中覆盖**：
/// ```java
/// @DistributedLock(key = "user:#{#userId}", leaseTime = 60000)  // 60 秒
/// ```
///
/// ### observability.metrics-enabled
///
/// **类型**：`boolean`
/// **默认值**：`true`
/// **说明**：是否启用 Micrometer 指标收集（需依赖 patra-spring-boot-starter-observability）
///
/// ### observability.tracing-enabled
///
/// **类型**：`boolean`
/// **默认值**：`true`
/// **说明**：是否启用 SkyWalking 追踪（需依赖 patra-spring-boot-starter-observability）
///
/// ### observability.logging-enabled
///
/// **类型**：`boolean`
/// **默认值**：`true`
/// **说明**：是否启用日志记录（LockLoggingRecorder）
///
/// ## 使用示例
///
/// ### 在代码中读取配置
///
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class LockConfigurer {
///
///     private final RedissonProperties properties;
///
///     public void printConfig() {
///         log.info("锁键前缀: {}", properties.getLock().getKeyPrefix());
///         log.info("默认等待时间: {}ms", properties.getLock().getDefaultWaitTime());
///         log.info("看门狗超时: {}ms", properties.getLockWatchdogTimeout());
///     }
/// }
/// ```
///
/// ### 环境特定配置
///
/// ```yaml
/// # application-dev.yml
/// patra:
///   redisson:
///     lock:
///       key-prefix: "dev:lock:"  # 开发环境专属前缀
///       default-wait-time: 10000  # 开发环境等待 10 秒便于调试
///
/// # application-prod.yml
/// patra:
///   redisson:
///     lock:
///       key-prefix: "prod:lock:"  # 生产环境专属前缀
///       default-wait-time: 0  # 生产环境不等待，快速失败
/// ```
///
/// ### 多服务配置隔离
///
/// 不同服务使用不同的锁键前缀，避免冲突：
///
/// ```yaml
/// # patra-catalog/application.yml
/// patra:
///   redisson:
///     lock:
///       key-prefix: "catalog:lock:"
///
/// # patra-ingest/application.yml
/// patra:
///   redisson:
///     lock:
///       key-prefix: "ingest:lock:"
/// ```
///
/// ## Redis 连接配置
///
/// Redisson 使用 Spring Data Redis 的配置：
///
/// ```yaml
/// spring:
///   data:
///     redis:
///       redisson:
///         config: |
///           singleServerConfig:
///             address: "redis://127.0.0.1:6379"
///             database: 0
///             connectionPoolSize: 64
///             connectionMinimumIdleSize: 10
/// ```
///
/// 或使用集群配置：
///
/// ```yaml
/// spring:
///   data:
///     redis:
///       redisson:
///         config: |
///           clusterServersConfig:
///             nodeAddresses:
///               - "redis://127.0.0.1:7000"
///               - "redis://127.0.0.1:7001"
///               - "redis://127.0.0.1:7002"
///             scanInterval: 1000
/// ```
///
/// @author linqibin
/// @since 0.1.0
///
package com.patra.starter.redisson.config;
