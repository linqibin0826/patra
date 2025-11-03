package com.patra.ingest.app.usecase.execution.cursor;

import com.patra.ingest.domain.model.vo.execution.ExecutionContext;

/**
 * 游标推进器接口。
 *
 * <p>职责:根据批次结果推进游标水位线;使用乐观锁避免冲突。
 *
 * <p>设计要点:
 *
 * <ul>
 *   <li>幂等性:使用乐观锁(版本号)防止重复推进
 *   <li>原子性:失败时抛出异常;由上游决定重试策略
 *   <li>窗口感知:根据 WindowSpec 策略计算新水位线
 *   <li>命名空间:支持 GLOBAL/TASK/PLAN 粒度
 * </ul>
 *
 * <p>错误处理:
 *
 * <ul>
 *   <li>乐观锁冲突:抛出 Spring 的 OptimisticLockingFailureException
 *   <li>游标缺失:首次推进时创建
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface CursorAdvancer {

  /**
   * 推进游标水位线。
   *
   * @param context 执行上下文(窗口/来源)
   * @param taskId 任务 ID(用于 TASK 粒度游标)
   * @param runId 运行 ID(用于审计)
   * @param batchId 最后成功的批次 ID(用于血缘跟踪)
   * @return true 表示已推进; false 表示乐观锁冲突(稍后重试)
   */
  boolean advance(ExecutionContext context, Long taskId, Long runId, Long batchId);
}
