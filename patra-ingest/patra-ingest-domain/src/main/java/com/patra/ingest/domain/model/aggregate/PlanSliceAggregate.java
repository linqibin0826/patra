package com.patra.ingest.domain.model.aggregate;

import com.patra.common.domain.AggregateRoot;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.ingest.domain.model.vo.slice.PlanSliceId;
import java.util.Objects;
import lombok.Getter;

/// 计划切片聚合根。建模数据采集计划切片的签名和生命周期。
///
/// 一致性边界：
///
/// - 切片的窗口规范、表达式快照在整个生命周期中保持不可变
///   - 切片签名哈希用于去重和幂等性保证
///   - 切片与任务维持 1:1 关系（重构后简化）
///
/// 业务规则：
///
/// - 切片创建时处于 `PENDING` 状态，等待任务分配
///   - 任务创建后转换为 `ASSIGNED` 状态
///   - 根据任务执行结果，最终转换为 `COMPLETED/FAILED` 状态
///   - 切片签名哈希 = hash(provenance + sliceNo + windowSpec) 确保幂等性
///
/// 状态转换：
///
/// - `PENDING` → `ASSIGNED`: 任务创建并分配
///   - `ASSIGNED` → `COMPLETED`: 任务执行成功
///   - `ASSIGNED` → `FAILED`: 任务执行失败
///
/// 领域事件：切片状态变更由 {@link com.patra.ingest.domain.event.TaskCompletedEvent} 触发。
///
/// @author linqibin
/// @since 0.1.0
@Getter
public class PlanSliceAggregate extends AggregateRoot<PlanSliceId> {

  /// 此切片所属计划的标识。
  private Long planId;

  /// 数据来源代码（如：pubmed）。
  private final ProvenanceCode provenanceCode;

  /// 切片序号。
  private final int sliceNo;

  /// 切片签名哈希（用于去重）。
  private final String sliceSignatureHash;

  /// 窗口规范（JSON 序列化）。
  private final String windowSpecJson;

  /// 切片范围表达式哈希。
  private final String exprHash;

  /// 切片范围表达式快照 JSON。
  private final String exprSnapshotJson;

  /// 切片当前状态。
  private SliceStatus status;

  private PlanSliceAggregate(
      PlanSliceId id,
      Long planId,
      ProvenanceCode provenanceCode,
      int sliceNo,
      String sliceSignatureHash,
      String windowSpecJson,
      String exprHash,
      String exprSnapshotJson,
      SliceStatus status) {
    super(id);
    this.planId = planId;
    this.provenanceCode = provenanceCode;
    this.sliceNo = sliceNo;
    this.sliceSignatureHash = sliceSignatureHash;
    this.windowSpecJson = windowSpecJson;
    this.exprHash = exprHash;
    this.exprSnapshotJson = exprSnapshotJson;
    this.status = status == null ? SliceStatus.PENDING : status;
  }

  public static PlanSliceAggregate create(
      Long planId,
      ProvenanceCode provenanceCode,
      int sliceNo,
      String sliceSignatureHash,
      String windowSpecJson,
      String exprHash,
      String exprSnapshotJson) {
    Objects.requireNonNull(sliceSignatureHash, "sliceSignatureHash must not be null");
    return new PlanSliceAggregate(
        null,
        planId,
        provenanceCode,
        sliceNo,
        sliceSignatureHash,
        windowSpecJson,
        exprHash,
        exprSnapshotJson,
        SliceStatus.PENDING);
  }

  public static PlanSliceAggregate restore(
      PlanSliceId id,
      Long planId,
      ProvenanceCode provenanceCode,
      int sequence,
      String sliceSignatureHash,
      String windowSpecJson,
      String exprHash,
      String exprSnapshotJson,
      SliceStatus status,
      long version) {
    PlanSliceAggregate aggregate =
        new PlanSliceAggregate(
            id,
            planId,
            provenanceCode,
            sequence,
            sliceSignatureHash,
            windowSpecJson,
            exprHash,
            exprSnapshotJson,
            status);
    aggregate.assignVersion(version);
    return aggregate;
  }

  /// 在持久化后将切片绑定到特定计划。
  ///
  /// @param planId 计划标识
  /// @throws IllegalArgumentException 如果 planId 为 null
  public void bindPlan(Long planId) {
    if (planId == null) {
      throw new IllegalArgumentException("planId 不能为 null");
    }
    this.planId = planId;
  }

  /// 将切片标记为已分配（对应的任务已创建）。
  ///
  /// **注意：**重构后强制执行 1:1 切片-任务关系。
  public void markAssigned() {
    this.status = SliceStatus.ASSIGNED;
  }

  /// 更新切片状态为指定值。
  ///
  /// 此方法由事件处理器使用，根据聚合的任务状态更新切片状态。
  ///
  /// @param newStatus 要设置的新状态
  /// @throws IllegalArgumentException 如果 newStatus 为 null
  public void updateStatus(SliceStatus newStatus) {
    if (newStatus == null) {
      throw new IllegalArgumentException("newStatus 不能为 null");
    }
    this.status = newStatus;
  }
}
