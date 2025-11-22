package com.patra.ingest.domain.service;

import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.enums.SliceStatus;
import java.util.List;

/// 计划状态计算器领域服务。基于子切片状态计算计划状态。
///
/// 这是一个无状态纯函数,封装了计划状态聚合的业务规则。
///
/// 聚合规则（重构后）：
///
/// - 如果任何切片处于 PENDING 或 ASSIGNED 状态 → 计划保持当前状态（尚未全部完成）
///   - 如果所有切片都处于 FINISHED 状态 → 计划转为 ARCHIVED（生命周期关闭）
///   - 如果不存在切片 → 计划保持当前状态（边界情况）
///
/// **注意：**计划状态仅反映其自身生命周期。执行结果（部分成功/失败）应通过聚合数据库中的任务状态查询。
///
/// 此服务仅处理 READY 之后的状态转换。DRAFT → SLICING → READY 的转换由计划装配过程处理。
public final class PlanStatusCalculator {

  private PlanStatusCalculator() {
    // Utility class, prevent instantiation
  }

  /// 基于所有子切片的状态计算计划状态。
  ///
  /// 前提条件：计划必须处于 READY 状态或更晚状态。此方法不处理 DRAFT → SLICING → READY 的转换。
  ///
  /// @param sliceStatuses 切片状态列表（不能为 null,但可以为空）
  /// @param currentPlanStatus 计划当前状态
  /// @return 聚合后的计划状态
  /// @throws IllegalArgumentException 如果 sliceStatuses 为 null
  public static PlanStatus calculate(
      List<SliceStatus> sliceStatuses, PlanStatus currentPlanStatus) {
    if (sliceStatuses == null) {
      throw new IllegalArgumentException("Slice statuses list cannot be null");
    }

    // Edge case: no slices exist (should not happen in normal flow)
    if (sliceStatuses.isEmpty()) {
      return currentPlanStatus; // Keep current status
    }

    // Check if any slice is still in progress
    boolean hasInProgress =
        sliceStatuses.stream().anyMatch(s -> s == SliceStatus.PENDING || s == SliceStatus.ASSIGNED);
    if (hasInProgress) {
      return currentPlanStatus; // Keep current status, not all slices are done
    }

    // All slices are FINISHED (terminal state)
    boolean allFinished = sliceStatuses.stream().allMatch(s -> s == SliceStatus.FINISHED);
    if (allFinished) {
      return PlanStatus.ARCHIVED; // Lifecycle closed
    }

    // Fallback: keep current status
    return currentPlanStatus;
  }

  /// Checks if a Slice status is a terminal state (no further transitions expected).
  ///
  /// @param status slice status
  /// @return true if terminal, false otherwise
  public static boolean isTerminal(SliceStatus status) {
    return status == SliceStatus.FINISHED;
  }
}
