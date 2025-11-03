package com.patra.ingest.app.usecase.execution.complete;

import com.patra.ingest.app.usecase.execution.session.ExecutionSession;
import com.patra.ingest.app.usecase.execution.strategy.ExecuteTaskBatchesUseCase;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;

/**
 * 完成阶段用例
 *
 * <p>在六边形架构+DDD中的角色:应用层用例,负责任务执行完成阶段的处理。
 *
 * <p>主要职责:
 *
 * <ul>
 *   <li>推进游标(cursor advancement)
 *   <li>更新任务状态(SUCCEEDED/PARTIAL/FAILED)
 *   <li>清理执行资源(心跳/租约)
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface CompleteTaskExecutionUseCase {

  /**
   * 完成执行(推进游标 + 更新状态)
   *
   * @param session 执行会话
   * @param context 执行上下文
   * @param executeResult 执行结果
   */
  void complete(
      ExecutionSession session,
      ExecutionContext context,
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult);
}
