/// 租约管理包。
///
/// 本包提供任务执行的租约管理和心跳续约功能，防止并发执行和僵尸任务。
///
/// ## 职责
///
/// - 获取任务租约（防止并发执行）
///   - 心跳续约（延长租约有效期）
///   - 释放任务租约
///   - 检测和清理过期租约
///
/// ## 核心组件
///
/// - `LeaseManagementService` - 租约管理服务接口
///   - `LeaseManagementServiceImpl` - 租约管理服务实现
///   - `HeartbeatRenewalService` - 心跳续约服务接口
///   - `HeartbeatRenewalServiceImpl` - 心跳续约服务实现
///
/// ## 租约机制
///
/// ### 租约获取
///
/// ```
///
/// 1. 尝试获取租约（存储在 Redis/DB）
///    ├─ 检查是否已被其他节点占用
///    ├─ 如果未占用 → 写入租约信息
///    └─ 如果已占用 → 返回失败
///
/// 2. 租约信息
///    ├─ taskId: 任务 ID
///    ├─ runId: 执行批次 ID（UUID）
///    ├─ nodeId: 执行节点 ID（如主机名）
///    ├─ acquiredAt: 获取时间
///    └─ expiresAt: 过期时间（如 5 分钟后）
///
/// ```
///
/// ### 心跳续约
///
/// ```
///
/// 1. 启动后台线程（ScheduledExecutorService）
/// 2. 每隔一定时间（如 1 分钟）执行续约
/// 3. 续约逻辑：
///    ├─ 检查租约是否仍然属于当前节点
///    ├─ 如果是 → 延长 expiresAt
///    └─ 如果不是 → 停止心跳（租约被抢占）
///
/// ```
///
/// ### 租约释放
///
/// ```
///
/// 1. 停止心跳线程
/// 2. 删除租约记录
/// 3. 记录释放日志
///
/// ```
///
/// ## 租约过期处理
///
/// - **场景**: 执行节点宕机，租约无法续约
///   - **结果**: 租约在过期时间后自动失效
///   - **恢复**: 其他节点可以重新获取租约并执行任务
///
/// ## 使用示例
///
/// ### 租约管理服务
///
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class LeaseManagementServiceImpl implements LeaseManagementService {
///     private final RedisTemplate<String, String> redisTemplate;
///     private final NodeIdProvider nodeIdProvider;
///
///     @Override
///     public Lease acquireLease(Long taskId, Duration leaseDuration) {
///         var leaseKey = "lease:task:" + taskId;
///         var runId = UUID.randomUUID().toString();
///         var nodeId = nodeIdProvider.getNodeId();
///         var expiresAt = Instant.now().plus(leaseDuration);
///
///         // 尝试获取租约（SET NX EX）
///         var leaseInfo = new LeaseInfo(taskId, runId, nodeId, Instant.now(), expiresAt);
///         var acquired = redisTemplate.opsForValue()
///             .setIfAbsent(leaseKey, leaseInfo.toJson(), leaseDuration);
///
///         if (Boolean.TRUE.equals(acquired)) {
///             log.info("Lease acquired: taskId={, runId={, nodeId={", taskId, runId, nodeId);
///             return Lease.success(taskId, runId, nodeId, expiresAt); else {
///             log.warn("Lease acquisition failed (already held): taskId={", taskId);
///             return Lease.failure(taskId);
///
///     @Override
///     public void renewLease(Lease lease, Duration extension) {
///         var leaseKey = "lease:task:" + lease.getTaskId();
///         var currentInfo = redisTemplate.opsForValue().get(leaseKey);
///
///         if (currentInfo != null && currentInfo.contains(lease.getRunId())) {
///             // 续约（延长过期时间）
///             redisTemplate.expire(leaseKey, extension);
///             log.debug("Lease renewed: taskId={, runId={", lease.getTaskId(), lease.getRunId());
// else {
///             log.warn("Lease renewal failed (lease lost): taskId={, runId={",
///                 lease.getTaskId(), lease.getRunId());
///             throw new LeaseRenewalException("Lease has been taken by another node");
///
///     @Override
///     public void releaseLease(Lease lease) {
///         var leaseKey = "lease:task:" + lease.getTaskId();
///         redisTemplate.delete(leaseKey);
///         log.info("Lease released: taskId={, runId={", lease.getTaskId(), lease.getRunId());
/// ```
///
/// ### 心跳续约服务
///
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class HeartbeatRenewalServiceImpl implements HeartbeatRenewalService {
///     private final LeaseManagementService leaseService;
///     private final ScheduledExecutorService heartbeatExecutor;
///     private final Map<Long, ScheduledFuture<?>> heartbeats = new ConcurrentHashMap<>();
///
///     @Override
///     public void start(ExecutionSession session) {
///         var taskId = session.getContext().getTaskId();
///         var lease = session.getLease();
///
///         // 每 1 分钟续约一次
///         var heartbeat = heartbeatExecutor.scheduleAtFixedRate(
///             () -> {
///                 try {
///                     leaseService.renewLease(lease, Duration.ofMinutes(5)); catch
// (LeaseRenewalException e) {
///                     log.error("Heartbeat renewal failed (lease lost): taskId={", taskId, e);
///                     stop(session);  // 停止心跳,
///             1, 1, TimeUnit.MINUTES
///         );
///
///         heartbeats.put(taskId, heartbeat);
///         log.info("Heartbeat started: taskId={", taskId);
///
///     @Override
///     public void stop(ExecutionSession session) {
///         var taskId = session.getContext().getTaskId();
///         var heartbeat = heartbeats.remove(taskId);
///
///         if (heartbeat != null) {
///             heartbeat.cancel(false);
///             log.info("Heartbeat stopped: taskId={", taskId);
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.usecase.execution.lease;
