///
/// 分布式锁核心包
///
/// ## 职责
///
/// 提供分布式锁的核心功能实现，包括注解、AOP 切面、锁执行器、键生成器等。
///
/// ## 核心组件
///
/// - `@DistributedLock`：声明式分布式锁注解
/// - `LockAspect`：AOP 切面（拦截 @DistributedLock 注解）
/// - `LockExecutor`：锁执行器（核心锁获取/释放逻辑）
/// - `LockKeyGenerator`：锁键生成器（支持 SpEL 表达式）
/// - `LockContext`：锁上下文（封装锁执行参数）
/// - `LockType`：锁类型枚举（REENTRANT、FAIR、READ、WRITE）
///
/// ## 使用示例
///
/// ### 基本用法
///
/// ```java
/// @Service
/// public class UserService {
///
///     @DistributedLock(key = "user:#{#userId}")
///     public void updateUser(Long userId, User user) {
///         // 业务逻辑自动受锁保护
///     }
/// }
/// ```
///
/// ### SpEL 表达式
///
/// ```java
/// // 简单变量
/// @DistributedLock(key = "user:#{#userId}")
/// public void updateUser(Long userId) { ... }
///
/// // 对象属性
/// @DistributedLock(key = "order:#{#order.id}")
/// public void processOrder(Order order) { ... }
///
/// // 复杂表达式
/// @DistributedLock(
///     key = "#{#type}:#{#id}:#{T(java.time.LocalDate).now()}"
/// )
/// public void processTask(String type, Long id) { ... }
/// ```
///
/// ### 多种锁类型
///
/// ```java
/// // 读锁（允许并发读）
/// @DistributedLock(
///     key = "config:#{#key}",
///     type = LockType.READ
/// )
/// public String getConfig(String key) { ... }
///
/// // 写锁（独占）
/// @DistributedLock(
///     key = "config:#{#key}",
///     type = LockType.WRITE
/// )
/// public void updateConfig(String key, String value) { ... }
///
/// // 公平锁（先到先得）
/// @DistributedLock(
///     key = "queue:#{#queueId}",
///     type = LockType.FAIR
/// )
/// public void processQueue(Long queueId) { ... }
/// ```
///
/// ### 等待锁配置
///
/// ```java
/// // 不等待（默认）
/// @DistributedLock(
///     key = "user:#{#userId}",
///     waitTime = 0  // 直接返回失败
/// )
/// public void updateUser(Long userId) { ... }
///
/// // 等待 5 秒
/// @DistributedLock(
///     key = "user:#{#userId}",
///     waitTime = 5000  // 等待最多 5 秒
/// )
/// public void updateUser(Long userId) { ... }
/// ```
///
/// ### 异常处理
///
/// ```java
/// // 获取锁失败抛异常（默认）
/// @DistributedLock(
///     key = "user:#{#userId}",
///     throwExceptionOnFailure = true
/// )
/// public void updateUser(Long userId) { ... }
///
/// // 获取锁失败返回 null（方法不执行）
/// @DistributedLock(
///     key = "user:#{#userId}",
///     throwExceptionOnFailure = false
/// )
/// public void updateUser(Long userId) { ... }
/// ```
///
/// ## LockAspect 工作流程
///
/// ```
/// 1. 拦截 @DistributedLock 注解的方法调用
///    ↓
/// 2. 使用 LockKeyGenerator 解析锁键（支持 SpEL）
///    ↓
/// 3. 创建 LockContext（封装锁参数）
///    ↓
/// 4. 调用 LockExecutor 获取锁
///    ├─ 成功 → 执行业务方法 → 释放锁
///    └─ 失败 → 抛异常或返回 null
/// ```
///
/// ## LockKeyGenerator 工作原理
///
/// ### SpEL 表达式解析
///
/// ```java
/// // 静态键（无需 SpEL 解析，性能最优）
/// @DistributedLock(key = "global-config")
/// public void updateGlobalConfig() { ... }
/// // 实际键：patra:lock:global-config
///
/// // 动态键（走 SpEL 解析，首次解析后缓存）
/// @DistributedLock(key = "user:#{#userId}")
/// public void updateUser(Long userId) { ... }
/// // 实际键：patra:lock:user:123
/// ```
///
/// ### SpEL 表达式缓存
///
/// LockKeyGenerator 内置表达式缓存（`ConcurrentHashMap`），避免重复解析：
///
/// ```java
/// // 第一次调用：解析 SpEL 表达式并缓存
/// @DistributedLock(key = "user:#{#userId}")
/// public void updateUser(Long userId) { ... }
///
/// // 后续调用：直接使用缓存的 Expression 对象
/// // 性能提升：20-30%（高频调用场景）
/// ```
///
/// ## LockExecutor 核心逻辑
///
/// ### 锁获取流程
///
/// ```java
/// public <T> T execute(LockContext context, Supplier<T> action) {
///     RLock lock = getLockByType(context);  // 根据类型创建锁
///     boolean acquired = false;
///
///     try {
///         // 尝试获取锁
///         acquired = lock.tryLock(
///             context.getWaitTime(),
///             context.getLeaseTime(),
///             TimeUnit.SECONDS
///         );
///
///         if (!acquired) {
///             throw new LockAcquisitionException(...);
///         }
///
///         // 执行业务逻辑
///         return action.get();
///     } finally {
///         if (acquired) {
///             lock.unlock();  // 释放锁
///         }
///     }
/// }
/// ```
///
/// ### 锁类型映射
///
/// | LockType | Redisson 锁类型 | 说明 |
/// |----------|----------------|------|
/// | REENTRANT | RLock | 可重入锁（默认） |
/// | FAIR | RLock (fair=true) | 公平锁（先到先得） |
/// | READ | ReadLock | 读锁（允许并发读） |
/// | WRITE | WriteLock | 写锁（独占） |
///
/// ## 性能优化
///
/// ### 1. SpEL 表达式缓存
///
/// 首次解析后缓存 Expression 对象，避免重复解析，性能提升 20-30%。
///
/// ### 2. 静态字符串检测
///
/// 对于静态锁键，跳过 SpEL 解析，直接拼接前缀，性能最优。
///
/// ### 3. 锁键前缀统一管理
///
/// 通过配置文件统一管理锁键前缀，避免硬编码：
///
/// ```yaml
/// patra:
///   redisson:
///     lock:
///       key-prefix: "catalog:lock:"  # patra-catalog 服务专属前缀
/// ```
///
/// ## 注意事项
///
/// ### 避免死锁
///
/// - **推荐**：显式设置 `leaseTime` = 业务执行时间 × 2-3
/// - **避免**：使用 `leaseTime=-1`（看门狗），除非业务时间不确定
///
/// ### 避免锁键冲突
///
/// 不同服务使用相同的锁键会导致竞争，务必加上服务名前缀：
///
/// ```java
/// // ❌ 错误：可能与其他服务冲突
/// @DistributedLock(key = "data:#{#id}")
///
/// // ✅ 正确：添加服务前缀
/// @DistributedLock(key = "catalog:data:#{#id}")
/// ```
///
/// @author linqibin
/// @since 0.1.0
///
package com.patra.starter.redisson.lock;
