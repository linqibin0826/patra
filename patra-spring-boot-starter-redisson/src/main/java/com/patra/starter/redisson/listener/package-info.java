///
/// 分布式锁监听器包
///
/// ## 职责
///
/// 提供分布式锁生命周期的监听器，支持日志记录、可观测性集成（追踪、指标）。
///
/// ## 核心组件
///
/// - `LockLoggingRecorder`：日志记录器（基础设施层调试工具，记录锁获取/释放日志）
/// - `LockMetricsRecorder`：Micrometer 指标记录器（可选，需依赖 patra-spring-boot-starter-observability）
/// - `LockTracingRecorder`：SkyWalking 追踪记录器（可选，需依赖 patra-spring-boot-starter-observability）
///
/// ## 使用示例
///
/// ### 自动启用（默认行为）
///
/// 添加 `patra-spring-boot-starter-redisson` 依赖后，`LockLoggingRecorder` 会自动启用。
///
/// ### 配置可观测性
///
/// ```yaml
/// patra:
///   redisson:
///     observability:
///       metrics-enabled: true   # 启用 Micrometer 指标
///       tracing-enabled: true   # 启用 SkyWalking 追踪
///       logging-enabled: true   # 启用日志记录
/// ```
///
/// ### 自定义监听器
///
/// 实现 `LockRecorder` 接口（如果有）或直接在 AOP 切面扩展：
///
/// ```java
/// @Component
/// @Order(5)
/// public class CustomLockRecorder {
///
///     public void beforeAcquire(LockContext context) {
///         // 锁获取前逻辑
///         log.info("准备获取锁: {}", context.getLockKey());
///     }
///
///     public void afterAcquire(LockContext context, boolean success) {
///         // 锁获取后逻辑
///         if (success) {
///             log.info("成功获取锁: {}", context.getLockKey());
///         } else {
///             log.warn("获取锁失败: {}", context.getLockKey());
///         }
///     }
///
///     public void afterRelease(LockContext context) {
///         // 锁释放后逻辑
///         log.info("释放锁: {}", context.getLockKey());
///     }
/// }
/// ```
///
/// ## LockLoggingRecorder 日志格式
///
/// ### 锁获取成功日志
///
/// ```
/// DEBUG 获取分布式锁成功: key=catalog:mesh-import:2024, waitTime=0ms, leaseTime=7200000ms
/// ```
///
/// ### 锁获取失败日志
///
/// ```
/// WARN  无法获取分布式锁: key=catalog:mesh-import:2024, waitTime=0ms, reason=锁已被占用
/// ```
///
/// ### 锁释放日志
///
/// ```
/// DEBUG 释放分布式锁: key=catalog:mesh-import:2024, holdTime=1230456ms
/// ```
///
/// ### 锁持有时间过长警告
///
/// ```
/// WARN  分布式锁持有时间过长: key=catalog:mesh-import:2024, holdTime=7200000ms, threshold=3600000ms
/// ```
///
/// ## LockMetricsRecorder 指标
///
/// 自动记录以下 Micrometer 指标：
///
/// - `redisson.lock.acquired`：锁获取成功计数（Counter）
/// - `redisson.lock.failed`：锁获取失败计数（Counter）
/// - `redisson.lock.wait.time`：锁等待时间（Timer）
/// - `redisson.lock.hold.time`：锁持有时间（Timer）
///
/// **标签（Tags）**：
/// - `key`：锁键
/// - `type`：锁类型（REENTRANT/FAIR/READ/WRITE）
/// - `reason`：失败原因（仅失败计数）
///
/// ### Prometheus 查询示例
///
/// ```promql
/// # 锁获取成功率
/// sum(rate(redisson_lock_acquired_total[5m]))
/// /
/// (sum(rate(redisson_lock_acquired_total[5m])) + sum(rate(redisson_lock_failed_total[5m])))
///
/// # 锁等待时间 P99
/// histogram_quantile(0.99, sum(rate(redisson_lock_wait_time_seconds_bucket[5m])) by (le))
///
/// # 锁持有时间 P99
/// histogram_quantile(0.99, sum(rate(redisson_lock_hold_time_seconds_bucket[5m])) by (le))
///
/// # 按锁键分组的失败率
/// sum(rate(redisson_lock_failed_total[5m])) by (key)
/// ```
///
/// ## LockTracingRecorder 追踪
///
/// 每次锁操作自动创建 SkyWalking Span：`DistributedLock: {lockKey}`
///
/// **记录内容**：
/// - 锁等待时间
/// - 锁持有时间
/// - 锁获取成功/失败状态
///
/// **查看方式**：
/// - **SkyWalking UI** → Trace 列表 → 搜索 "DistributedLock"
/// - **Span 详情**：查看锁操作详细信息
/// - **拓扑图**：查看锁调用链
///
/// ## 可观测性集成
///
/// 添加 `patra-spring-boot-starter-observability` 依赖后，以下监听器自动启用：
///
/// ```xml
/// <dependency>
///     <groupId>com.patra</groupId>
///     <artifactId>patra-spring-boot-starter-observability</artifactId>
/// </dependency>
/// ```
///
/// 配置：
///
/// ```yaml
/// patra:
///   redisson:
///     observability:
///       metrics-enabled: true   # LockMetricsRecorder 自动启用
///       tracing-enabled: true   # LockTracingRecorder 自动启用
/// ```
///
/// ## 监听器执行顺序
///
/// 当多个监听器存在时，执行顺序由 `@Order` 注解控制（数值越小优先级越高）：
///
/// 1. **LockTracingRecorder**（@Order(10)）：创建追踪 Span
/// 2. **LockMetricsRecorder**（@Order(20)）：收集执行指标
/// 3. **LockLoggingRecorder**（@Order(100)）：记录日志
///
/// ## 注意事项
///
/// ### LockLoggingRecorder 不属于可观测性范畴
///
/// `LockLoggingRecorder` 是基础设施层的调试工具，记录结构化日志用于开发和故障排查，
/// 与可观测性（Observability）的追踪（Tracing）、指标（Metrics）不同：
///
/// - **LockLoggingRecorder**：开发调试、故障排查（保留在本 Starter）
/// - **LockMetricsRecorder、LockTracingRecorder**：生产监控、性能分析（在 observability Starter）
///
/// ### 日志级别配置
///
/// 生产环境建议调整日志级别，避免过多 DEBUG 日志：
///
/// ```yaml
/// logging:
///   level:
///     com.patra.starter.redisson.listener.LockLoggingRecorder: INFO  # 仅输出 WARN/ERROR
/// ```
///
/// @author linqibin
/// @since 0.1.0
///
package com.patra.starter.redisson.listener;
