package com.patra.starter.redisson.lock;

import java.lang.annotation.*;

/**
 * 分布式锁注解
 * <p>
 * 通过 AOP 拦截方法调用，自动获取和释放 Redisson 分布式锁。
 *
 * <h2>基本用法</h2>
 * <pre>{@code
 * @DistributedLock(key = "user:#{#userId}")
 * public void updateUser(String userId) {
 *     // 方法执行期间持有锁
 * }
 * }</pre>
 *
 * <h2>SpEL 表达式支持</h2>
 * <p>支持以下 SpEL 表达式：
 * <ul>
 *   <li>方法参数：{@code #{#userId}}、{@code #{#request.id}}</li>
 *   <li>静态字符串：{@code "user:lock"}（无需 SpEL，性能更高）</li>
 *   <li>复杂表达式：{@code "user:#{#userId}:action:#{#action}"}</li>
 * </ul>
 *
 * <h2>看门狗机制</h2>
 * <p>当 {@code leaseTime = -1}（默认值）时，Redisson 会启用看门狗机制：
 * <ul>
 *   <li>自动续期：每 10 秒（watchdogTimeout/3）检查锁是否仍被持有，如果是则自动续期</li>
 *   <li>自动释放：方法执行完成后立即释放锁</li>
 *   <li>防止死锁：即使应用崩溃，锁也会在 watchdogTimeout 后自动过期</li>
 * </ul>
 *
 * <h2>手动设置 leaseTime 的场景</h2>
 * <p>以下情况建议手动设置 {@code leaseTime}：
 * <ul>
 *   <li>短任务（< 1 秒）：避免看门狗开销</li>
 *   <li>已知最大执行时间：防止异常情况下锁占用过长</li>
 *   <li>资源受限环境：减少看门狗线程开销</li>
 * </ul>
 *
 * <h2>示例</h2>
 * <pre>{@code
 * // 示例 1: 默认配置（看门狗自动续期）
 * @DistributedLock(key = "payment:#{#orderId}")
 * public void processPayment(String orderId) {
 *     // 长时间运行任务，看门狗会自动续期
 * }
 *
 * // 示例 2: 手动设置超时（关闭看门狗）
 * @DistributedLock(
 *     key = "cache:refresh",
 *     waitTime = 0,      // 不等待，获取失败立即抛异常
 *     leaseTime = 5000   // 5 秒后自动释放，无看门狗
 * )
 * public void refreshCache() {
 *     // 快速任务，不需要看门狗
 * }
 *
 * // 示例 3: 读写锁
 * @DistributedLock(
 *     key = "config:read",
 *     lockType = LockType.READ
 * )
 * public Config readConfig() {
 *     // 多个读操作可以并发执行
 * }
 *
 * @DistributedLock(
 *     key = "config:write",
 *     lockType = LockType.WRITE
 * )
 * public void updateConfig(Config config) {
 *     // 写操作独占执行
 * }
 * }</pre>
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {

    /**
     * 锁的键（支持 SpEL 表达式）
     * <p>
     * 示例：
     * <ul>
     *   <li>静态字符串：{@code "user:lock"}</li>
     *   <li>方法参数：{@code "user:#{#userId}"}</li>
     *   <li>对象属性：{@code "order:#{#request.orderId}"}</li>
     * </ul>
     *
     * @return 锁键表达式
     */
    String key();

    /**
     * 锁类型
     * <p>
     * 默认: {@link LockType#REENTRANT}
     *
     * @return 锁类型
     */
    LockType lockType() default LockType.REENTRANT;

    /**
     * 等待时间（毫秒）
     * <p>
     * 获取锁的最大等待时间，超时未获取到锁则抛出 {@code LockAcquisitionException}。
     * <p>
     * 设置为 0 时，不等待，获取失败立即抛异常（tryLock 模式）。
     * <p>
     * 默认: -1（使用配置文件中的 patra.redisson.lock.default-wait-time）
     *
     * @return 等待时间（毫秒）
     */
    long waitTime() default -1;

    /**
     * 租约时间（毫秒）
     * <p>
     * 锁的自动过期时间，防止死锁。
     * <p>
     * <strong>重要：看门狗机制说明</strong>
     * <ul>
     *   <li>{@code leaseTime = -1}（默认）：启用看门狗，自动续期直到方法执行完成</li>
     *   <li>{@code leaseTime > 0}：关闭看门狗，锁在指定时间后自动释放（即使方法未执行完）</li>
     * </ul>
     * <p>
     * 默认: -1（启用看门狗）
     *
     * @return 租约时间（毫秒）
     */
    long leaseTime() default -1;
}
