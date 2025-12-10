package com.patra.ingest.app.usecase.execution.prepare;

import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import com.patra.ingest.app.usecase.execution.session.ExecutionSession;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;

/// 任务准备阶段。
///
/// 在六边形架构+DDD中的角色: Handler 内部执行阶段，负责任务执行准备。
///
/// 主要职责: 幂等检查、租约获取、会话初始化、上下文加载
///
/// @author linqibin
/// @since 0.1.0
public interface TaskPreparationPhase {

  /// 执行准备(幂等检查、租约获取、会话创建、上下文加载)
  ///
  /// @param command 任务就绪命令
  /// @return 准备结果(会话 + 上下文)
  PrepareResult prepare(TaskReadyCommand command);

  /// 准备结果
  ///
  /// @param session 执行会话(包含心跳句柄)
  /// @param context 执行上下文(配置快照、编译后的表达式)
  record PrepareResult(ExecutionSession session, ExecutionContext context) {}

  /// 任务已成功异常
  ///
  /// 当任务已经成功执行时抛出,用于幂等跳过。
  class TaskAlreadySucceededException extends RuntimeException {
    public TaskAlreadySucceededException(String message) {
      super(message);
    }
  }

  /// 租约获取失败异常
  ///
  /// 当租约获取失败时抛出(被其他工作节点持有)。
  class LeaseAcquisitionFailedException extends RuntimeException {
    public LeaseAcquisitionFailedException(String message) {
      super(message);
    }
  }
}
