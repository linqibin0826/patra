package com.patra.ingest.app.usecase.execution.strategy;

import com.patra.ingest.app.usecase.execution.coordination.GenericBatchExecutor;
import com.patra.ingest.app.usecase.execution.session.ExecutionSession;
import com.patra.ingest.app.usecase.execution.strategy.planner.BatchPlanner;
import com.patra.ingest.app.usecase.execution.strategy.planner.BatchPlannerRegistry;
import com.patra.ingest.domain.model.entity.TaskRunBatch;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.batch.BatchPlan;
import com.patra.ingest.domain.model.vo.batch.BatchResult;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.port.TaskRunBatchRepository;
import com.patra.ingest.domain.port.TaskRunRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 执行任务批次用例的实现。
 *
 * <p>核心职责: 批次规划 → 批次执行 → 持久化结果 → 返回统计信息。
 *
 * <p>设计要点:
 *
 * <ul>
 *   <li>通过 BatchPlannerRegistry 进行批次规划,构建批次列表
 *   <li>强制执行批次限制,超出时抛出异常
 *   <li>批次执行委托给 GenericBatchExecutor,由适配器注册表支持
 *   <li>通过 TaskRunBatchRepository 立即持久化每个批次结果
 *   <li>每个批次执行前检查租约;租约被撤销时中止执行
 *   <li>错误处理:记录失败并继续(可配置快速失败)
 * </ul>
 *
 * <p>配置项:
 *
 * <ul>
 *   <li>task.execution.fail-fast: 默认 false(继续执行)
 * </ul>
 *
 * <p>日志策略:
 *
 * <ul>
 *   <li>INFO: 计划创建、批次开始/完成、统计信息
 *   <li>WARN: 超出限制、租约撤销、批次失败
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExecuteTaskBatchesUseCaseImpl implements ExecuteTaskBatchesUseCase {

  private final BatchPlannerRegistry plannerRegistry;
  private final GenericBatchExecutor batchExecutor;
  private final TaskRunBatchRepository batchRepository;
  private final TaskRunRepository taskRunRepository;

  @Value("${task.execution.fail-fast:false}")
  private boolean failFast;

  /**
   * 执行批次(规划 + 执行)。
   *
   * <p>执行流程:
   *
   * <ol>
   *   <li>通过 BatchPlanner 规划批次列表
   *   <li>验证批次数量不超过限制
   *   <li>循环执行每个批次:
   *       <ul>
   *         <li>检查租约状态,撤销时中止
   *         <li>执行批次并捕获异常
   *         <li>持久化批次结果
   *         <li>更新心跳时间戳
   *         <li>更新统计计数器
   *       </ul>
   *   <li>返回执行统计结果
   * </ol>
   *
   * @param session 执行会话
   * @param context 执行上下文
   * @return 包含批次统计信息的执行结果
   * @throws BatchLimitExceededException 如果批次数量超过限制
   */
  @Override
  public ExecuteResult execute(ExecutionSession session, ExecutionContext context) {
    Long taskId = session.taskId();
    Long runId = session.runId();
    String provenanceCode =
        context.provenanceCode() != null ? context.provenanceCode().getCode() : null;

    log.info("开始执行批次 taskId={} runId={} provenanceCode={}", taskId, runId, provenanceCode);

    // 步骤1: 规划批次
    log.debug("规划批次中 taskId={} runId={} provenanceCode={}", taskId, runId, provenanceCode);
    BatchPlanner planner = plannerRegistry.get(provenanceCode);
    BatchPlan plan = planner.plan(context);

    // 步骤2: 验证批次数量不超过限制
    if (plan.exceedsLimit()) {
      throw new BatchLimitExceededException(
          "批次数量超过限制 taskId=" + taskId + " totalBatches=" + plan.totalBatches());
    }

    // 步骤3: 如果没有批次,返回空结果
    if (!plan.hasBatches()) {
      log.warn("未规划任何批次 taskId={} runId={}", taskId, runId);
      return new ExecuteResult(0, 0, 0);
    }

    log.info("批次计划已创建 taskId={} runId={} totalBatches={}", taskId, runId, plan.totalBatches());

    // 步骤4: 执行批次循环
    int succeededCount = 0;
    int failedCount = 0;

    for (Batch batch : plan.batches()) {
      // 步骤4.1: 检查租约撤销状态
      log.debug(
          "处理批次 [{}/{}] taskId={} runId={}", batch.batchNo(), plan.totalBatches(), taskId, runId);

      // 如果租约被撤销,立即中止后续批次执行
      if (session.heartbeatHandle() != null && session.heartbeatHandle().isLeaseRevoked()) {
        log.warn("租约已撤销,中止批次执行 taskId={} runId={} batchNo={}", taskId, runId, batch.batchNo());
        break;
      }

      // 步骤4.2: 执行单个批次
      log.info(
          "开始执行批次 taskId={} runId={} batchNo={}/{}",
          taskId,
          runId,
          batch.batchNo(),
          plan.totalBatches());

      BatchResult result;
      try {
        result = batchExecutor.execute(context, batch);
      } catch (Exception e) {
        log.error("批次执行失败 taskId={} runId={} batchNo={}", taskId, runId, batch.batchNo(), e);
        // 异常时创建失败结果
        result = BatchResult.failure(batch.batchNo(), e.getMessage());
      }

      // 步骤4.3: 持久化批次结果
      TaskRunBatch batchEntity = TaskRunBatch.create(context, batch, result);
      batchRepository.save(batchEntity);

      // 步骤4.3.1: 更新 TaskRun 心跳时间戳,反映批次处理活动
      try {
        boolean updated = taskRunRepository.touchHeartbeat(runId, Instant.now());
        if (log.isDebugEnabled()) {
          log.debug(
              "TaskRun 心跳已更新 taskId={} runId={} batchNo={} updated={}",
              taskId,
              runId,
              batch.batchNo(),
              updated);
        }
      } catch (Exception e) {
        log.warn(
            "更新 TaskRun 心跳失败 taskId={} runId={} batchNo={}", taskId, runId, batch.batchNo(), e);
        // 心跳更新失败不影响批次执行
      }

      // 步骤4.4: 更新统计计数器
      if (result.success()) {
        succeededCount++;
        log.info(
            "批次执行成功 taskId={} runId={} batchNo={} fetchedCount={}",
            taskId,
            runId,
            batch.batchNo(),
            result.fetchedCount());
      } else {
        failedCount++;
        log.warn(
            "批次执行失败 taskId={} runId={} batchNo={} error={}",
            taskId,
            runId,
            batch.batchNo(),
            result.errorMessage());

        // 快速失败模式:立即中止剩余批次
        if (failFast) {
          log.warn("快速失败模式已启用,中止剩余批次 taskId={} runId={}", taskId, runId);
          break;
        }
      }
    }

    // 步骤5: 记录执行统计并返回结果
    log.info(
        "批次执行完成 taskId={} runId={} total={} succeeded={} failed={}",
        taskId,
        runId,
        plan.totalBatches(),
        succeededCount,
        failedCount);

    return new ExecuteResult(plan.totalBatches(), succeededCount, failedCount);
  }

  /** 批次数量超过限制异常。 */
  public static class BatchLimitExceededException extends RuntimeException {
    public BatchLimitExceededException(String message) {
      super(message);
    }
  }
}
