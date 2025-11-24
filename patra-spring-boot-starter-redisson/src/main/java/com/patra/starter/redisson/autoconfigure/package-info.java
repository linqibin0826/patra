///
/// Redisson 自动配置包
///
/// ## 职责
///
/// 提供 Redisson 的自动配置类，根据条件自动激活 Redis 客户端和分布式锁能力、可观测性集成。
///
/// ## 核心组件
///
/// - `RedissonAutoConfiguration`：Redisson 客户端自动配置（基于官方 Starter）
/// - `LockAutoConfiguration`：分布式锁自动配置（AOP、锁执行器、键生成器）
/// - `ObservabilityAutoConfiguration`：可观测性自动配置（条件激活：依赖 patra-spring-boot-starter-observability）
///
/// ## 自动配置机制
///
/// ### RedissonAutoConfiguration
///
/// **激活条件**：
/// - `patra.redisson.enabled=true`（默认启用）
/// - 类路径存在 `RedissonClient.class`
///
/// **配置内容**：
/// - 加载 `RedissonProperties` 配置属性
/// - 复用 Redisson 官方 Starter 提供的 `RedissonClient` Bean
/// - 输出启动日志（lockWatchdogTimeout、lock.keyPrefix 等）
///
/// ### LockAutoConfiguration
///
/// **激活条件**：
/// - `patra.redisson.lock.enabled=true`（默认启用）
/// - 依赖 `RedissonAutoConfiguration`（确保 RedissonClient 已初始化）
///
/// **配置内容**：
/// - `LockAspect`：AOP 切面（拦截 @DistributedLock 注解）
/// - `LockExecutor`：锁执行器（核心锁获取/释放逻辑）
/// - `LockKeyGenerator`：锁键生成器（支持 SpEL 表达式，内置缓存优化）
/// - `LockLoggingRecorder`：日志记录器（记录锁获取/释放日志）
/// - `LockMetricsRecorder`：指标记录器（可选，需依赖 observability）
///
/// ### ObservabilityAutoConfiguration
///
/// **激活条件**：
/// - `patra.redisson.observability.metrics-enabled=true`（默认启用）
/// - 依赖 `patra-spring-boot-starter-observability`
/// - 类路径存在 `MeterRegistry.class`（Micrometer）
///
/// **配置内容**：
/// - `LockMetricsRecorder`：Micrometer 指标记录器
/// - `LockTracingRecorder`：SkyWalking 追踪记录器（可选）
///
/// ## 使用示例
///
/// ### 自定义配置
///
/// ```yaml
/// patra:
///   redisson:
///     enabled: true  # 启用 Redisson
///     lock-watchdog-timeout: 30000  # 看门狗超时时间（毫秒）
///
///     lock:
///       enabled: true  # 启用分布式锁
///       key-prefix: "patra:lock:"  # 锁键前缀
///       default-wait-time: 0  # 默认等待时间（0=不等待）
///       default-lease-time: -1  # 默认租期（-1=启用看门狗）
///
///     observability:
///       metrics-enabled: true  # 启用指标收集
///       tracing-enabled: true  # 启用追踪
///       logging-enabled: true  # 启用日志记录
/// ```
///
/// ### 禁用自动配置
///
/// ```yaml
/// patra:
///   redisson:
///     enabled: false  # 禁用 Redisson 自动配置
/// ```
///
/// 或单独禁用分布式锁：
///
/// ```yaml
/// patra:
///   redisson:
///     lock:
///       enabled: false  # 禁用分布式锁，仅使用 RedissonClient
/// ```
///
/// ## 扩展 Redisson 配置
///
/// ### 自定义 RedissonClient
///
/// 如果需要完全自定义 RedissonClient，可以覆盖官方 Starter 的 Bean：
///
/// ```java
/// @Configuration
/// public class CustomRedissonConfig {
///
///     @Bean
///     @Primary
///     public RedissonClient customRedissonClient() {
///         Config config = new Config();
///         config.useSingleServer()
///             .setAddress("redis://custom-host:6379")
///             .setConnectionPoolSize(100)
///             .setConnectionMinimumIdleSize(10);
///         return Redisson.create(config);
///     }
/// }
/// ```
///
/// ### 自定义锁执行器
///
/// ```java
/// @Configuration
/// public class CustomLockConfig {
///
///     @Bean
///     @Primary
///     public LockExecutor customLockExecutor(RedissonClient redissonClient) {
///         return new LockExecutor(redissonClient) {
///             @Override
///             protected void beforeAcquire(LockContext context) {
///                 // 自定义锁获取前逻辑
///                 log.info("准备获取锁: {}", context.getLockKey());
///             }
///         };
///     }
/// }
/// ```
///
/// ## 配置优先级
///
/// 1. **代码配置** > YAML 配置
/// 2. **注解参数** > 全局默认值
/// 3. **环境特定配置** > 通用配置
///
/// 示例：
/// ```java
/// @DistributedLock(
///     key = "user:#{#userId}",
///     waitTime = 5000  // 覆盖全局 default-wait-time
/// )
/// public void updateUser(Long userId) { ... }
/// ```
///
/// @author linqibin
/// @since 0.1.0
///
package com.patra.starter.redisson.autoconfigure;
