package com.patra.ingest.app.usecase.execution.session;

import com.patra.ingest.domain.model.aggregate.TaskAggregate;

/// 执行会话管理器
/// 
/// 负责创建 TaskRun、启动心跳续期,以及封装清理逻辑(停止心跳 + 释放租约)。
/// 
/// ### 职责
/// 
/// - **会话创建**: 为 Task 创建 TaskRun 并启动心跳续期
///   - **资源管理**: 封装 {@link ExecutionSession} 的生命周期管理
///   - **自动清理**: 通过 try-with-resources 自动停止心跳和释放租约
/// 
/// ### 使用模式
/// 
/// ```java
/// try (ExecutionSession session = sessionManager.createSession(taskId, owner, correlationId)) {
///   // 执行任务逻辑 // 自动清理: 停止心跳 + 释放租约
/// ```
/// 
/// ### 优化策略
/// 
/// 提供两种创建方式:
/// 
/// - {@link #createSession(Long, String, String)}: 从 taskId 开始创建(需查询 Task 聚合)
///   - {@link #createSession(TaskAggregate, String, String)}: 复用已加载的 Task 聚合,避免重复查询
/// 
/// @author linqibin
/// @since 0.1.0
public interface ExecutionSessionManager {

  /// 创建执行会话(创建 TaskRun 并启动心跳)
/// 
/// @param taskId 任务 ID
/// @param leaseOwner 租约拥有者
/// @param correlationId 关联 ID
/// @return 执行会话
  ExecutionSession createSession(Long taskId, String leaseOwner, String correlationId);

  /// 创建执行会话(TaskRun + 心跳) - 优化版,避免重复加载 Task
/// 
/// @param task 任务聚合(已加载)
/// @param leaseOwner 租约拥有者
/// @param correlationId 关联 ID
/// @return 执行会话
  ExecutionSession createSession(TaskAggregate task, String leaseOwner, String correlationId);
}
