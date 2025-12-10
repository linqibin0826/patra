package com.patra.ingest.app.usecase.execution.complete;

import com.patra.ingest.app.usecase.execution.session.ExecutionSession;
import com.patra.ingest.app.usecase.execution.strategy.BatchExecutionPhase;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;

/// 任务完成阶段。
///
/// 在六边形架构+DDD中的角色: Handler 内部执行阶段，负责任务执行完成处理。
///
/// 主要职责:
///
/// - 推进游标（cursor advancement）
/// - 更新任务状态（SUCCEEDED/PARTIAL/FAILED）
/// - 清理执行资源（心跳/租约）
///
/// @author linqibin
/// @since 0.1.0
public interface TaskCompletionPhase {

  /// 完成执行（推进游标 + 更新状态）
  ///
  /// @param session 执行会话
  /// @param context 执行上下文
  /// @param executeResult 执行结果
  void complete(
      ExecutionSession session,
      ExecutionContext context,
      BatchExecutionPhase.ExecuteResult executeResult);
}
