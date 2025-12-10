package com.patra.ingest.app.usecase.execution.strategy;

import com.patra.ingest.app.usecase.execution.session.ExecutionSession;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;

/// 批次执行阶段。
///
/// 在六边形架构+DDD中的角色: Handler 内部执行阶段，负责批次处理。
///
/// 主要职责: 批次调度 + 批次执行(支持并发/幂等)
///
/// @author linqibin
/// @since 0.1.0
public interface BatchExecutionPhase {

  /// 执行批次(规划 + 执行)
  ///
  /// @param session 执行会话
  /// @param context 执行上下文
  /// @return 执行结果(包含批次统计)
  ExecuteResult execute(ExecutionSession session, ExecutionContext context);

  /// 执行结果
  ///
  /// @param totalBatches 总批次数
  /// @param succeededBatches 成功批次数
  /// @param failedBatches 失败批次数
  record ExecuteResult(int totalBatches, int succeededBatches, int failedBatches) {}
}
