package dev.linqibin.patra.ingest.app.usecase.plan.validator;

import dev.linqibin.patra.ingest.domain.exception.PlanValidationException;
import dev.linqibin.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import dev.linqibin.patra.ingest.domain.model.vo.plan.PlanTriggerNorm;
import dev.linqibin.patra.ingest.domain.model.vo.plan.PlannerWindow;

/// Plan 验证器契约(应用层·验证策略接口)
///
/// 实现类在 Plan/Slice/Task 组装之前分析输入和环境,防止下游执行无效工作负载。
///
/// 典型验证维度(实现类可扩展):
///
/// - 窗口合理性(存在性、时间顺序、时长边界)
///   - 队列反压(排队任务阈值)
///   - 溯源能力对齐(增量 vs. 全量、偏移配置)
///   - 配置快照完整性
///
/// 违规应抛出 {@link PlanValidationException}。调用方应表达业务级警告而非系统错误。
public interface PlannerValidator {

  /// 在 Plan 组装之前执行验证
  ///
  /// @param triggerNorm 标准化的触发器(溯源/操作/请求窗口)
  /// @param snapshot 溯源配置快照(可以为 `null`)
  /// @param window 标准化的 Plan 窗口(UPDATE 操作可以为 `null`)
  /// @param currentQueuedTasks 当前排队任务数量,用于反压检查
  /// @throws PlanValidationException 验证失败时抛出
  void validateBeforeAssemble(
      PlanTriggerNorm triggerNorm,
      ProvenanceConfigSnapshot snapshot,
      PlannerWindow window,
      long currentQueuedTasks);
}
