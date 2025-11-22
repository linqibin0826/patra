package com.patra.ingest.app.usecase.execution;

import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;

/// 任务执行用例(顶层编排器)
///
/// 在六边形架构+DDD中的角色:应用层用例,负责编排任务执行的完整生命周期。
///
/// 主要职责:
///
/// - 协调准备 → 执行 → 完成三个阶段的子用例
///   - 处理顶层异常和资源清理
///   - 确保任务执行流程的完整性和容错性
///
/// @author linqibin
/// @since 0.1.0
public interface TaskExecutionUseCase {

  /// 执行任务(完整流程: 准备 → 执行 → 完成)
  ///
  /// @param command 任务就绪命令
  void execute(TaskReadyCommand command);
}
