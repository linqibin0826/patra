package com.patra.ingest.app.usecase.execution.complete;

import com.patra.ingest.domain.model.vo.ExecutionContext;

/**
 * 游标推进器接口。
 * <p>
 * 职责：根据批次执行结果推进游标水位，支持乐观锁防止并发冲突。
 * </p>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>幂等性：使用乐观锁（version）确保同一水位不会被重复推进。</li>
 *   <li>原子性：推进失败时抛出异常，由上层决定重试策略。</li>
 *   <li>窗口感知：根据 WindowSpec 策略确定推进后的新水位。</li>
 *   <li>命名空间：支持 GLOBAL/TASK/PLAN 等不同粒度的游标。</li>
 * </ul>
 * </p>
 * <p>
 * 异常处理：
 * <ul>
 *   <li>乐观锁冲突：抛出 OptimisticLockingFailureException（Spring）。</li>
 *   <li>游标不存在：创建新游标（首次推进）。</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface CursorAdvancer {

    /**
     * 推进游标水位。
     *
     * @param context 执行上下文（包含窗口、数据源等信息）
     * @param taskId 任务ID（用于 TASK 粒度游标）
     * @param runId 运行ID（用于审计）
     * @return true 表示推进成功，false 表示乐观锁冲突（需重试）
     */
    boolean advance(ExecutionContext context, Long taskId, Long runId);
}
