/// 中继协调器包。
///
/// 本包提供中继流程中的各种协调器，负责租约管理、消息发布、日志记录等子流程。
///
/// ## 职责
///
/// - 租约获取和释放（RelayLeaseCoordinator）
///   - 消息发布到 RocketMQ（RelayPublishCoordinator）
///   - 日志记录和指标更新（RelayLogCoordinator）
///
/// ## 核心组件
///
/// - `RelayLeaseCoordinator` - 租约协调器
///
/// - 批量获取消息租约（更新状态 PENDING → PUBLISHING）
///         - 设置租约持有者和过期时间
///         - 使用乐观锁防止并发冲突
///
///   - `RelayPublishCoordinator` - 发布协调器
///
/// - 遍历消息列表，逐条发布到 RocketMQ
///         - 成功 → 更新状态为 PUBLISHED
///         - 失败 → 根据错误类型决定重试或标记为 FAILED
///
///   - `RelayLogCoordinator` - 日志协调器
///
/// - **批处理模式** - 使用 LogAccumulator 在内存中收集日志，批量持久化
///         - 支持所有中继结果：PUBLISHED、DEFERRED、FAILED、LEASE_MISSED
///         - 批量插入 100-500 条日志到单个 SQL 语句
///         - 更新中继指标（OutboxRelayMetrics）
///
/// ## 租约协调器流程
///
/// ```
///
/// 1. 批量更新消息状态
///    UPDATE outbox_message
///    SET status = 'PUBLISHING',
///        lease_owner = ?,
///        lease_expire_at = ?,
///        version = version + 1
///    WHERE id IN (?)
///      AND status = 'PENDING'
///      AND version = ?
///
/// 2. 检查更新结果
///    ├─ 更新成功 → 租约获取成功
///    └─ 更新失败 → 租约被其他节点抢占（乐观锁冲突）
///
/// ```
///
/// ## 发布协调器流程
///
/// ```
///
/// 1. 遍历消息列表
/// 2. 对每条消息：
///    ├─ 构建 RocketMQ 消息
///    ├─ 发送到 RocketMQ
///    ├─ 成功 → 更新状态为 PUBLISHED
///    └─ 失败 → 根据错误类型处理
///        ├─ 可重试错误（网络超时） → 恢复为 PENDING
///        └─ 不可重试错误（格式错误） → 标记为 FAILED
///
/// ```
///
/// ## 日志协调器流程（批处理模式）
///
/// ```
///
/// 1. 创建日志累加器
///    LogAccumulator accumulator = coordinator.createAccumulator(batchId);
///
/// 2. 中继批次执行期间在内存中收集日志
///    for (OutboxMessage msg : batch) {
///      if (!leaseAcquired) {
///        accumulator.recordLeaseMissed(...);
///      } else if (publishSuccess) {
///        accumulator.recordPublished(...);
///      } else if (retryable) {
///        accumulator.recordDeferred(...);
///      } else {
///        accumulator.recordFailed(...);
///      }
///    }
///
/// 3. 批量持久化所有日志到数据库（单条 INSERT 语句处理 N 行）
///    coordinator.persistBatch(accumulator);
///
/// 4. 自动记录指标（每个中继结果）
///    - PUBLISHED: outbox.relay.attempts{channel=X, status=PUBLISHED}
///    - DEFERRED: outbox.relay.attempts{channel=X, status=DEFERRED} + 错误计数器
///    - FAILED: outbox.relay.attempts{channel=X, status=FAILED} + 错误计数器
///    - LEASE_MISSED: outbox.relay.attempts{channel=X, status=LEASE_MISSED}
///
/// ```
///
/// ## 使用示例
///
/// ### 租约协调器
///
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class RelayLeaseCoordinator {
///     private final OutboxMessageRepository outboxRepository;
///
///     public LeaseResult acquireLeases(RelayPlan plan) {
///         var messages = plan.getMessages();
///         var leaseOwner = plan.getLeaseOwner();
///         var leaseExpireAt = plan.getLeaseExpireAt();
///
///         // 批量更新状态和租约信息
///         var acquiredMessages = new ArrayList<OutboxMessage>();
///         for (var message : messages) {
///             try {
///                 message.acquireLease(leaseOwner, leaseExpireAt);
///                 outboxRepository.save(message);
///                 acquiredMessages.add(message);
///             } catch (OptimisticLockingFailureException e) {
///                 log.warn("Lease acquisition failed (concurrent conflict): messageId={}",
///                     message.getId());
///             }
///         }
///         return new LeaseResult(acquiredMessages);
///     }
/// }
/// ```
///
/// ### 发布协调器
///
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class RelayPublishCoordinator {
///     private final RocketMQTemplate rocketMQTemplate;
///     private final OutboxMessageRepository outboxRepository;
///     private final RelayErrorClassifier errorClassifier;
///
///     public PublishResult publish(List<OutboxMessage> messages) {
///         int publishedCount = 0;
///         int failedCount = 0;
///
///         for (var message : messages) {
///             try {
///                 // 1. 构建 RocketMQ 消息
///                 var mqMessage = buildMQMessage(message);
///
///                 // 2. 发送到 RocketMQ
///                 rocketMQTemplate.send(message.getChannel(), mqMessage);
///
///                 // 3. 标记为已发布
///                 message.markAsPublished();
///                 outboxRepository.save(message);
///                 publishedCount++;
///             } catch (Exception e) {
///                 // 4. 错误处理
///                 if (errorClassifier.isRetryable(e)) {
///                     // 可重试错误：恢复为 PENDING
///                     message.markAsPending();
///                 } else {
///                     // 不可重试错误：标记为 FAILED
///                     message.markAsFailed(e.getMessage());
///                 }
///                 outboxRepository.save(message);
///                 failedCount++;
///                 log.error("Message publish failed: messageId={}", message.getId(), e);
///             }
///         }
///         return new PublishResult(publishedCount, failedCount);
///     }
/// }
/// ```
///
/// ### 日志协调器（批处理模式）
///
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class OutboxRelayExecutor {
///     private final RelayLogCoordinator logCoordinator;
///
///     public void executeBatch(RelayBatchId batchId, List<OutboxMessage> messages) {
///         // 1. 创建日志累加器
///         LogAccumulator accumulator = logCoordinator.createAccumulator(batchId);
///
///         // 2. 处理消息并记录结果
///         for (OutboxMessage msg : messages) {
///             Instant startTime = Instant.now();
///
///             // 尝试获取租约
///             boolean leaseAcquired = tryAcquireLease(msg);
///             if (!leaseAcquired) {
///                 accumulator.recordLeaseMissed(msg, leaseOwner, startTime);
///                 continue;
///             }
///
///             // 尝试发布消息
///             try {
///                 publishToMQ(msg);
///                 Instant publishedAt = Instant.now();
///                 accumulator.recordPublished(msg, leaseOwner, startTime, publishedAt);
///             } catch (RetryableException e) {
///                 Instant nextRetryAt = calculateNextRetry();
///                 accumulator.recordDeferred(msg, leaseOwner, startTime, nextRetryAt,
///                     e.getErrorCode(), e.getMessage(), "TRANSIENT");
///             } catch (FatalException e) {
///                 accumulator.recordFailed(msg, leaseOwner, startTime,
///                     e.getErrorCode(), e.getMessage(), "FATAL");
///             }
///         }
///
///         // 3. 批量持久化所有日志（单个 SQL INSERT）
///         logCoordinator.persistBatch(accumulator);
///     }
/// }
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.usecase.relay.coordinator;
