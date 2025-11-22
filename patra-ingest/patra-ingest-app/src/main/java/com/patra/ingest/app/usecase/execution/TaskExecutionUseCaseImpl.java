package com.patra.ingest.app.usecase.execution;

import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import com.patra.ingest.app.usecase.execution.complete.CompleteTaskExecutionUseCase;
import com.patra.ingest.app.usecase.execution.prepare.PrepareTaskExecutionUseCase;
import com.patra.ingest.app.usecase.execution.session.ExecutionSession;
import com.patra.ingest.app.usecase.execution.strategy.ExecuteTaskBatchesUseCase;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 任务执行用例实现(顶层编排器)
/// 
/// 在六边形架构+DDD中的角色:应用层用例实现,负责编排任务执行的完整流程。
/// 
/// 主要职责:
/// 
/// - 编排准备 → 执行 → 完成三个阶段的子用例
///   - 处理顶层异常并确保资源清理(心跳/租约)
///   - 提供任务执行的容错和可观测性
/// 
/// 设计要点:
/// 
/// - 三阶段编排模式(按照ADR-001架构决策)
///   - 捕获所有异常并确保清理资源(心跳/租约)
///   - 幂等跳过:准备用例抛出TaskAlreadySucceededException时立即返回
///   - 租约获取失败:准备用例抛出LeaseAcquisitionFailedException时立即返回
///   - 执行/完成阶段的失败不会阻止资源清理
/// 
/// 日志策略:
/// 
/// - INFO: 开始、各阶段完成、结束
///   - WARN: 幂等跳过、租约获取失败
///   - ERROR: 执行失败、清理失败
/// 
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskExecutionUseCaseImpl implements TaskExecutionUseCase {

  private final PrepareTaskExecutionUseCase prepareUseCase;
  private final ExecuteTaskBatchesUseCase executeUseCase;
  private final CompleteTaskExecutionUseCase completeUseCase;

  /// 执行任务(准备 → 执行 → 完成)
/// 
/// 业务流程:
/// 
/// @param command 任务就绪命令
/// @throws TaskExecutionException 任务执行失败时抛出
  @Override
  public void execute(TaskReadyCommand command) {
    long taskId = command.taskId();
    String idempotentKey = command.idempotentKey();

    log.info("任务执行开始 taskId={} idemKey={}", taskId, idempotentKey);

    ExecutionSession session = null;
    ExecutionContext context = null;

    try {
      // ========== 阶段0: 准备 ==========
      log.debug("进入准备阶段 taskId={} idemKey={}", taskId, idempotentKey);
      PrepareTaskExecutionUseCase.PrepareResult prepareResult;
      try {
        prepareResult = prepareUseCase.prepare(command);
        session = prepareResult.session();
        context = prepareResult.context();

        log.info(
            "准备阶段完成 taskId={} runId={} provenance={} operation={}",
            taskId,
            session.runId(),
            context.provenanceCode(),
            context.operationCode());

      } catch (PrepareTaskExecutionUseCase.TaskAlreadySucceededException e) {
        // 幂等跳过:任务已成功
        log.warn("任务已成功执行,跳过 taskId={} idemKey={}", taskId, idempotentKey);
        return;

      } catch (PrepareTaskExecutionUseCase.LeaseAcquisitionFailedException e) {
        // 租约获取失败(被其他工作节点持有)
        log.warn("租约获取失败,跳过执行 taskId={}", taskId);
        return;
      }

      // ========== 阶段1: 执行 ==========
      log.debug("进入执行阶段 taskId={} runId={}", taskId, session.runId());
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          executeUseCase.execute(session, context);

      log.info(
          "执行阶段完成 taskId={} runId={} total={} succeeded={} failed={}",
          taskId,
          session.runId(),
          executeResult.totalBatches(),
          executeResult.succeededBatches(),
          executeResult.failedBatches());

      // ========== 阶段2: 完成 ==========
      log.debug("进入完成阶段 taskId={} runId={}", taskId, session.runId());
      completeUseCase.complete(session, context, executeResult);

      log.info("任务执行完成 taskId={} runId={}", taskId, session.runId());

    } catch (Exception e) {
      log.error("任务执行失败 taskId={} idemKey={}", taskId, idempotentKey, e);

      // 失败时尝试清理资源(如果会话已创建)
      if (session != null) {
        try {
          session.cleanup();
          log.info("失败时会话清理成功 taskId={}", taskId);
        } catch (Exception cleanupEx) {
          log.error("会话清理失败 taskId={}", taskId, cleanupEx);
        }
      }

      // 重新抛出;上游(适配器/MQ消费者)决定是否重试
      throw new TaskExecutionException("任务执行失败 taskId=" + taskId, e);
    }
  }

  /// 任务执行异常(顶层)
/// 
/// 封装任务执行过程中的所有异常,用于统一的错误处理和重试机制。
  public static class TaskExecutionException extends RuntimeException {
    public TaskExecutionException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
