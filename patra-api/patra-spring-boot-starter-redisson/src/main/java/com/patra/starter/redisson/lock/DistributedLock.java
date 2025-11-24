package com.patra.starter.redisson.lock;

import java.lang.annotation.*;

/// 分布式锁注解。
///
/// 通过 AOP 拦截方法调用，自动获取和释放 Redisson 分布式锁。
///
/// ## 基本用法
///
/// ```java
/// @DistributedLock(key = "user:#{#userId}")
/// public void updateUser(String userId) {
///     // 方法执行期间持有锁
/// }
/// ```
///
/// ## SpEL 表达式支持
///
/// 支持以下 SpEL 表达式：
///
/// - 方法参数：`#{#userId}`、`#{#request.id}`
/// - 静态字符串：`"user:lock"`（无需 SpEL，性能更高）
/// - 复杂表达式：`"user:#{#userId}:action:#{#action}"`
///
/// ## 看门狗机制
///
/// 当 `leaseTime = -1`（默认值）时，Redisson 会启用看门狗机制：
///
/// - 自动续期：每 10 秒（watchdogTimeout/3）检查锁是否仍被持有，如果是则自动续期
/// - 自动释放：方法执行完成后立即释放锁
/// - 防止死锁：即使应用崩溃，锁也会在 watchdogTimeout 后自动过期
///
/// ## 手动设置 leaseTime 的场景
///
/// 以下情况建议手动设置 `leaseTime`：
///
/// - 短任务（< 1 秒）：避免看门狗开销
/// - 已知最大执行时间：防止异常情况下锁占用过长
/// - 资源受限环境：减少看门狗线程开销
///
/// ## 示例
///
/// ```java
/// // 示例 1: 默认配置（看门狗自动续期）
/// @DistributedLock(key = "payment:#{#orderId}")
/// public void processPayment(String orderId) {
///     // 长时间运行任务，看门狗会自动续期
/// }
///
/// // 示例 2: 手动设置超时（关闭看门狗）
/// @DistributedLock(
///     key = "cache:refresh",
///     waitTime = 0,      // 不等待，获取失败立即抛异常
///     leaseTime = 5000   // 5 秒后自动释放，无看门狗
/// )
/// public void refreshCache() {
///     // 快速任务，不需要看门狗
/// }
///
/// // 示例 3: 读写锁
/// @DistributedLock(
///     key = "config:read",
///     lockType = LockType.READ
/// )
/// public Config readConfig() {
///     // 多个读操作可以并发执行
/// }
///
/// @DistributedLock(
///     key = "config:write",
///     lockType = LockType.WRITE
/// )
/// public void updateConfig(Config config) {
///     // 写操作独占执行
/// }
/// ```
///
/// @author Patra Team
/// @since 1.0.0
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {

    /// 锁的键（支持 SpEL 表达式）。
    ///
    /// 示例：
    ///
    /// - 静态字符串：`"user:lock"`
    /// - 方法参数：`"user:#{#userId}"`
    /// - 对象属性：`"order:#{#request.orderId}"`
    ///
    /// @return 锁键表达式
    String key();

    /// 锁类型，默认 `REENTRANT`。
    ///
    /// @return 锁类型
    LockType lockType() default LockType.REENTRANT;

    /// 等待时间（毫秒），默认 -1。
    ///
    /// 获取锁的最大等待时间，超时未获取到锁则抛出 `LockAcquisitionException`。
    ///
    /// 设置为 0 时，不等待，获取失败立即抛异常（tryLock 模式）。
    ///
    /// 默认: -1（使用配置文件中的 patra.redisson.lock.default-wait-time）
    ///
    /// @return 等待时间（毫秒）
    long waitTime() default -1;

    /// 租约时间（毫秒），默认 -1。
    ///
    /// 锁的自动过期时间，防止死锁。
    ///
    /// **重要：看门狗机制说明**
    ///
    /// - `leaseTime = -1`（默认）：启用看门狗，自动续期直到方法执行完成
    /// - `leaseTime > 0`：关闭看门狗，锁在指定时间后自动释放（即使方法未执行完）
    ///
    /// @return 租约时间（毫秒）
    long leaseTime() default -1;
}
