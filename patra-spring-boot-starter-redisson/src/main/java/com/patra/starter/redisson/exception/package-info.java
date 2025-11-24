///
/// 分布式锁异常包
///
/// ## 职责
///
/// 定义分布式锁相关的异常类型和错误码，集成 patra-common-core 的统一异常体系。
///
/// ## 核心组件
///
/// - `LockAcquisitionException`：锁获取失败异常
/// - `LockTimeoutException`：锁获取超时异常
/// - `LockInfrastructureException`：锁基础设施异常（Redis 连接失败等）
/// - `LockExpressionException`：SpEL 表达式解析异常
/// - `LockErrorCode`：分布式锁错误码枚举（实现 ErrorTrait）
///
/// ## 异常层次
///
/// ```
/// InfrastructureException (patra-common-core)
///   ├─ LockAcquisitionException (锁获取失败)
///   ├─ LockTimeoutException (锁获取超时)
///   ├─ LockInfrastructureException (Redis 连接失败)
///   └─ LockExpressionException (SpEL 解析失败)
/// ```
///
/// ## 使用示例
///
/// ### 抛出锁获取异常
///
/// ```java
/// @Service
/// public class UserService {
///
///     @DistributedLock(
///         key = "user:#{#userId}",
///         waitTime = 0,
///         throwExceptionOnFailure = true  // 获取失败抛异常
///     )
///     public void updateUser(Long userId, User user) {
///         // 如果锁被占用，抛出 LockAcquisitionException
///         userRepository.update(userId, user);
///     }
/// }
/// ```
///
/// ### 错误码定义
///
/// `LockErrorCode` 枚举：
///
/// - `LOCK_ACQUISITION_FAILED`：锁获取失败（锁被占用）
/// - `LOCK_TIMEOUT`：锁获取超时（等待时间超过 waitTime）
/// - `LOCK_INFRASTRUCTURE_ERROR`：基础设施错误（Redis 连接失败、网络异常）
/// - `LOCK_EXPRESSION_ERROR`：SpEL 表达式解析失败
/// - `LOCK_RELEASE_FAILED`：锁释放失败
///
/// ### 异常处理
///
/// 集成 patra-spring-boot-starter-core 的全局异常处理器后，锁异常会自动转换为统一的错误响应：
///
/// ```json
/// {
///   "code": "LOCK_ACQUISITION_FAILED",
///   "message": "无法获取分布式锁: user:123",
///   "timestamp": "2025-11-24T10:30:00Z",
///   "traceId": "abc123"
/// }
/// ```
///
/// ## LockErrorCode 枚举说明
///
/// ### LOCK_ACQUISITION_FAILED
///
/// **HTTP 状态码**：409 Conflict
/// **说明**：锁获取失败，锁已被其他线程/进程占用
///
/// **触发场景**：
/// - `waitTime=0`（不等待）且锁被占用
/// - 高并发场景下多个请求竞争同一把锁
///
/// **处理建议**：
/// - 返回友好提示："当前操作正在处理中，请稍后重试"
/// - 或增加 `waitTime` 等待一段时间
///
/// ### LOCK_TIMEOUT
///
/// **HTTP 状态码**：408 Request Timeout
/// **说明**：锁获取超时，等待时间超过 `waitTime`
///
/// **触发场景**：
/// - `waitTime > 0` 但在超时时间内未获取到锁
/// - 锁被长时间占用（leaseTime 过长）
///
/// **处理建议**：
/// - 检查锁持有时间是否合理
/// - 优化业务逻辑，减少锁持有时间
///
/// ### LOCK_INFRASTRUCTURE_ERROR
///
/// **HTTP 状态码**：503 Service Unavailable
/// **说明**：Redis 基础设施异常（连接失败、网络异常等）
///
/// **触发场景**：
/// - Redis 服务不可用
/// - 网络故障
/// - RedissonClient 未正确配置
///
/// **处理建议**：
/// - 检查 Redis 服务状态
/// - 检查网络连接
/// - 查看 Redisson 配置是否正确
///
/// ### LOCK_EXPRESSION_ERROR
///
/// **HTTP 状态码**：500 Internal Server Error
/// **说明**：SpEL 表达式解析失败
///
/// **触发场景**：
/// - SpEL 表达式语法错误
/// - 引用的方法参数为 null
/// - 类型不匹配
///
/// **处理建议**：
/// - 检查 SpEL 表达式语法
/// - 使用 Elvis 运算符处理 null：`#{#user?.id ?: 'unknown'}`
/// - 在方法开头检查参数是否为 null
///
/// ### LOCK_RELEASE_FAILED
///
/// **HTTP 状态码**：500 Internal Server Error
/// **说明**：锁释放失败（通常是 Redis 异常）
///
/// **触发场景**：
/// - 释放锁时 Redis 连接断开
/// - 锁已过期（leaseTime 到期）
///
/// **处理建议**：
/// - 检查 `leaseTime` 是否足够长
/// - 检查 Redis 连接稳定性
///
/// ## 异常处理示例
///
/// ### 捕获锁获取异常
///
/// ```java
/// @Service
/// public class UserService {
///
///     public void updateUserSafely(Long userId, User user) {
///         try {
///             updateUser(userId, user);
///         } catch (LockAcquisitionException e) {
///             log.warn("锁被占用，操作取消: {}", userId);
///             throw new ApplicationException("当前用户正在被其他操作修改，请稍后重试");
///         } catch (LockTimeoutException e) {
///             log.error("锁获取超时，操作取消: {}", userId);
///             throw new ApplicationException("操作超时，请稍后重试");
///         }
///     }
///
///     @DistributedLock(key = "user:#{#userId}")
///     private void updateUser(Long userId, User user) {
///         userRepository.update(userId, user);
///     }
/// }
/// ```
///
/// ### 自定义异常处理器
///
/// ```java
/// @ControllerAdvice
/// public class LockExceptionHandler {
///
///     @ExceptionHandler(LockAcquisitionException.class)
///     public ResponseEntity<ErrorResponse> handleLockAcquisitionException(
///         LockAcquisitionException e
///     ) {
///         return ResponseEntity
///             .status(HttpStatus.CONFLICT)
///             .body(ErrorResponse.builder()
///                 .code("LOCK_CONFLICT")
///                 .message("当前操作正在处理中，请稍后重试")
///                 .timestamp(LocalDateTime.now())
///                 .build());
///     }
/// }
/// ```
///
/// ## 错误追踪
///
/// 集成 patra-spring-boot-starter-observability 后，异常会自动关联 SkyWalking TraceID，
/// 便于在分布式追踪系统中定位问题：
///
/// ```
/// ERROR [catalog-service,abc123,xyz456] LockAcquisitionException: 无法获取分布式锁: user:123
/// ```
///
/// 其中 `abc123` 是 TraceID，可以在 SkyWalking UI 中搜索完整调用链。
///
/// @author linqibin
/// @since 0.1.0
///
package com.patra.starter.redisson.exception;
