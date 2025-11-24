///
/// Redisson 分布式锁和 Redis 基础设施 Starter
///
/// ## 职责
///
/// 提供基于 Redisson 的分布式锁和 Redis 客户端支持，为 Patra 微服务架构提供分布式协调能力。
///
/// **核心功能**：
/// - RedissonClient 自动配置（支持单机/集群/哨兵/主从模式）
/// - `@DistributedLock` 声明式注解（零代码获取/释放锁）
/// - SpEL 表达式支持（动态生成锁键）
/// - 多种锁类型（可重入锁、公平锁、读写锁）
/// - 统一异常处理（集成 patra-common-core 异常体系）
/// - 可选的可观测性集成（SkyWalking 追踪、Micrometer 指标）
///
/// ## 核心组件
///
/// - `RedissonAutoConfiguration`：RedissonClient 自动配置类
/// - `LockAutoConfiguration`：分布式锁自动配置类
/// - `@DistributedLock`：声明式分布式锁注解
/// - `LockAspect`：AOP 切面（拦截 @DistributedLock 注解）
/// - `LockExecutor`：锁执行器（核心锁获取/释放逻辑）
/// - `LockKeyGenerator`：锁键生成器（支持 SpEL 表达式）
/// - `RedissonProperties`：配置属性类（patra.redisson.*）
///
/// ## 使用示例
///
/// ### 1. 添加依赖
///
/// ```xml
/// <dependency>
///     <groupId>com.patra</groupId>
///     <artifactId>patra-spring-boot-starter-redisson</artifactId>
/// </dependency>
/// ```
///
/// ### 2. 配置 Redis
///
/// ```yaml
/// spring:
///   data:
///     redis:
///       redisson:
///         config: |
///           singleServerConfig:
///             address: "redis://127.0.0.1:6379"
/// ```
///
/// ### 3. 使用 @DistributedLock 注解
///
/// ```java
/// @Service
/// public class UserService {
///
///     @DistributedLock(key = "user:#{#userId}", leaseTime = 30)
///     public void updateUser(Long userId, User user) {
///         // 业务逻辑自动受锁保护
///     }
/// }
/// ```
///
/// ## 高级用法
///
/// ### SpEL 表达式支持
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
/// @DistributedLock(key = "#{#type}:#{#id}:#{T(java.time.LocalDate).now()}")
/// public void processTask(String type, Long id) { ... }
/// ```
///
/// ### 多种锁类型
///
/// ```java
/// // 读锁（允许并发读）
/// @DistributedLock(key = "config:#{#key}", type = LockType.READ)
/// public String getConfig(String key) { ... }
///
/// // 写锁（独占）
/// @DistributedLock(key = "config:#{#key}", type = LockType.WRITE)
/// public void updateConfig(String key, String value) { ... }
///
/// // 公平锁（先到先得）
/// @DistributedLock(key = "queue:#{#queueId}", type = LockType.FAIR)
/// public void processQueue(Long queueId) { ... }
/// ```
///
/// ### 看门狗机制
///
/// ```java
/// // 显式设置 leaseTime（推荐）
/// @DistributedLock(key = "task:#{#taskId}", leaseTime = 60)
/// public void processTask(Long taskId) { ... }
///
/// // 启用看门狗（业务时间不确定）
/// @DistributedLock(key = "task:#{#taskId}", leaseTime = -1)
/// public void processLongTask(Long taskId) { ... }
/// ```
///
/// ## 架构位置
///
/// 在六边形架构中，本 Starter 位于**框架层（Framework Layer）**，为应用层（Application Layer）
/// 提供分布式协调能力。
///
/// ```
/// Application 层 (patra-xxx-app)
///   - Orchestrator（编排器）
///     ↓ uses @DistributedLock
/// Framework 层 (patra-starter-redisson) ← 本 Starter
///   - LockAspect（AOP 拦截）
///   - LockExecutor（锁获取/释放）
///   - RedissonClient（Redis 客户端）
///     ↓ connects to
/// Redis 集群
/// ```
///
/// ## 依赖关系
///
/// - `redisson-spring-boot-starter`：Redisson 官方 Starter
/// - `spring-boot-starter-aop`：AOP 支持
/// - `patra-common-core`：异常体系、工具类
/// - `patra-spring-boot-starter-core`：错误处理、可观测性集成
/// - `patra-spring-boot-starter-observability`：追踪、指标（可选）
///
/// ## 注意事项
///
/// ### leaseTime 配置建议
///
/// - **推荐**：显式设置 leaseTime = 业务执行时间 × 2-3
/// - **避免**：使用 `-1`（看门狗），除非业务时间不确定
///
/// **原因**：看门狗机制虽然方便，但如果业务逻辑挂起（如数据库死锁），锁将永久占用，导致死锁。
///
/// ### 与 @Transactional 一起使用
///
/// ❌ **错误用法**：
///
/// ```java
/// @Transactional
/// @DistributedLock(key = "user:#{#userId}")
/// public void updateUser(Long userId) {
///     userRepository.update(userId, ...);
///     // 锁在此释放，但事务未提交
/// }
/// ```
///
/// ✅ **正确用法**：锁嵌套事务
///
/// ```java
/// @DistributedLock(key = "user:#{#userId}")
/// public void updateUser(Long userId) {
///     updateUserInTransaction(userId);  // 事务已提交，锁再释放
/// }
///
/// @Transactional
/// private void updateUserInTransaction(Long userId) {
///     userRepository.update(userId, ...);
/// }
/// ```
///
/// ### 锁键命名规范
///
/// **推荐格式**：`{服务名}:{业务域}:{业务 ID}`
///
/// ```java
/// // patra-catalog 服务
/// @DistributedLock(key = "catalog:mesh-import:#{#year}")
/// public void importMesh(int year) { ... }
///
/// // patra-ingest 服务
/// @DistributedLock(key = "ingest:harvest:#{#provenance}:#{#date}")
/// public void executeHarvest(String provenance, LocalDate date) { ... }
/// ```
///
/// **配置服务专属前缀**：
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
/// @author linqibin
/// @since 0.1.0
///
package com.patra.starter.redisson;
