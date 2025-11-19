package com.patra.ingest.domain.model.aggregate;

import com.patra.common.domain.AggregateRoot;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.vo.plan.WindowSpec;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;

/**
 * 采集计划聚合根。封装单个数据采集计划的蓝图及其状态机流转。
 *
 * <p>一致性边界：
 *
 * <ul>
 *   <li>计划的窗口规范、表达式快照、配置快照在整个生命周期中保持不可变
 *   <li>状态转换必须遵循预定义的状态机规则
 *   <li>计划键 (planKey) 保证同一业务场景的计划幂等性
 * </ul>
 *
 * <p>业务规则：
 *
 * <ul>
 *   <li>计划创建时处于 {@code DRAFT} 状态，包含窗口边界、切片策略和配置快照
 *   <li>切片生成开始时转换为 {@code SLICING} 状态
 *   <li>所有切片生成完成后转换为 {@code READY} 状态，准备任务调度
 *   <li>根据任务执行结果聚合，最终转换为 {@code COMPLETED/PARTIAL/FAILED} 状态
 *   <li>计划键 = hash(provenance + operation + window + strategy) 确保幂等性
 * </ul>
 *
 * <p>状态转换：
 *
 * <ul>
 *   <li>{@code DRAFT} → {@code SLICING}: 开始切片生成
 *   <li>{@code SLICING} → {@code READY}: 所有切片生成完成
 *   <li>{@code READY} → {@code COMPLETED}: 所有任务执行成功
 *   <li>{@code READY} → {@code PARTIAL}: 部分任务失败但计划可继续
 *   <li>{@code READY} → {@code FAILED}: 计划执行失败，需补偿
 * </ul>
 *
 * <p>领域事件：计划状态变更由 {@link com.patra.ingest.domain.event.TaskCompletedEvent} 触发的聚合逻辑驱动。
 *
 * <p>线程安全：此聚合根在单线程中创建和变更，不应跨线程共享。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
public class PlanAggregate extends AggregateRoot<Long> {

  /** 关联的调度实例标识（外部触发源）。 */
  private final Long scheduleInstanceId;

  /** 业务幂等键，用于计划去重（hash(provenance + operation + window + strategy)）。 */
  private final String planKey;

  /** 数据来源代码（如：pubmed、epmc）。 */
  private final ProvenanceCode provenanceCode;

  /** 操作类型（全量采集、增量采集、补偿采集等）。 */
  private final OperationCode operationCode;

  /** 表达式原型哈希值，用于变更检测。 */
  private final String exprProtoHash;

  /** 表达式原型快照（JSON 格式，编译前的原始表达式）。 */
  private final String exprProtoSnapshotJson;

  /** 数据来源配置快照（执行时捕获的不可变配置）。 */
  private final String provenanceConfigSnapshotJson;

  /** 数据来源配置哈希值，用于变更检测。 */
  private final String provenanceConfigHash;

  /** 窗口边界规范（支持 TIME/DATE/ID_RANGE/CURSOR_LANDMARK/VOLUME_BUDGET/SINGLE 策略）。 */
  private final WindowSpec windowSpec;

  /** 切片策略代码（如：TIME、DATE、SINGLE）。 */
  private final String sliceStrategyCode;

  /** 切片策略参数 JSON 载荷。 */
  private final String sliceParamsJson;

  /** 计划当前状态。 */
  private PlanStatus status;

  private PlanAggregate(
      Long id,
      Long scheduleInstanceId,
      String planKey,
      ProvenanceCode provenanceCode,
      OperationCode operationCode,
      String exprProtoHash,
      String exprProtoSnapshotJson,
      String provenanceConfigSnapshotJson,
      String provenanceConfigHash,
      WindowSpec windowSpec,
      String sliceStrategyCode,
      String sliceParamsJson,
      PlanStatus status) {
    super(id);
    this.scheduleInstanceId =
        Objects.requireNonNull(scheduleInstanceId, "scheduleInstanceId must not be null");
    this.planKey = Objects.requireNonNull(planKey, "planKey must not be null");
    this.provenanceCode = provenanceCode;
    this.operationCode = operationCode;
    this.exprProtoHash = exprProtoHash;
    this.exprProtoSnapshotJson = exprProtoSnapshotJson;
    this.provenanceConfigSnapshotJson = provenanceConfigSnapshotJson;
    this.provenanceConfigHash = provenanceConfigHash;
    this.windowSpec = Objects.requireNonNull(windowSpec, "windowSpec must not be null");
    this.sliceStrategyCode = sliceStrategyCode;
    this.sliceParamsJson = sliceParamsJson;
    this.status = status == null ? PlanStatus.DRAFT : status;
  }

  /**
   * 创建全新的计划蓝图聚合根，初始状态为 {@link PlanStatus#DRAFT DRAFT}。
   *
   * @param scheduleInstanceId 调度实例标识
   * @param planKey 幂等键
   * @param provenanceCode 数据来源代码
   * @param operationCode 操作代码（将解析为枚举）
   * @param exprProtoHash 表达式原型哈希
   * @param exprProtoSnapshotJson 表达式原型快照 JSON
   * @param provenanceConfigSnapshotJson 数据来源配置快照 JSON
   * @param provenanceConfigHash 数据来源配置哈希
   * @param windowSpec 窗口边界规范
   * @param sliceStrategyCode 切片策略代码
   * @param sliceParamsJson 切片策略参数 JSON
   * @return 新创建的计划聚合根
   */
  public static PlanAggregate create(
      Long scheduleInstanceId,
      String planKey,
      ProvenanceCode provenanceCode,
      String operationCode,
      String exprProtoHash,
      String exprProtoSnapshotJson,
      String provenanceConfigSnapshotJson,
      String provenanceConfigHash,
      WindowSpec windowSpec,
      String sliceStrategyCode,
      String sliceParamsJson) {
    // Parse domain enum once to normalize case and whitespace.
    OperationCode op = operationCode == null ? null : OperationCode.fromCode(operationCode);
    return new PlanAggregate(
        null,
        scheduleInstanceId,
        planKey,
        provenanceCode,
        op,
        exprProtoHash,
        exprProtoSnapshotJson,
        provenanceConfigSnapshotJson,
        provenanceConfigHash,
        windowSpec,
        sliceStrategyCode,
        sliceParamsJson,
        PlanStatus.DRAFT);
  }

  /**
   * 从持久化状态重建已存在的计划聚合根（由仓储层使用）。
   *
   * @param id 主键标识
   * @param scheduleInstanceId 调度实例标识
   * @param planKey 计划幂等键
   * @param provenanceCode 数据来源代码
   * @param operationCode 操作代码字符串
   * @param exprProtoHash 表达式哈希
   * @param exprProtoSnapshotJson 表达式快照 JSON
   * @param provenanceConfigSnapshotJson 配置快照 JSON
   * @param provenanceConfigHash 配置快照哈希
   * @param windowSpec 窗口边界规范
   * @param sliceStrategyCode 切片策略代码
   * @param sliceParamsJson 切片策略参数 JSON
   * @param status 当前计划状态
   * @param version 乐观锁版本
   * @return 从持久化重建的计划聚合根
   */
  public static PlanAggregate restore(
      Long id,
      Long scheduleInstanceId,
      String planKey,
      ProvenanceCode provenanceCode,
      String operationCode,
      String exprProtoHash,
      String exprProtoSnapshotJson,
      String provenanceConfigSnapshotJson,
      String provenanceConfigHash,
      WindowSpec windowSpec,
      String sliceStrategyCode,
      String sliceParamsJson,
      PlanStatus status,
      long version) {
    OperationCode op = operationCode == null ? null : OperationCode.fromCode(operationCode);
    PlanAggregate aggregate =
        new PlanAggregate(
            id,
            scheduleInstanceId,
            planKey,
            provenanceCode,
            op,
            exprProtoHash,
            exprProtoSnapshotJson,
            provenanceConfigSnapshotJson,
            provenanceConfigHash,
            windowSpec,
            sliceStrategyCode,
            sliceParamsJson,
            status);
    aggregate.assignVersion(version);
    return aggregate;
  }

  /**
   * 将计划从 DRAFT 状态转换为 SLICING 状态。
   *
   * @throws IllegalStateException 如果计划不处于 DRAFT 状态
   */
  public void startSlicing() {
    if (this.status != PlanStatus.DRAFT) {
      throw new IllegalStateException("计划状态无效，无法开始切片生成");
    }
    this.status = PlanStatus.SLICING;
  }

  /** 在所有切片生成完成后，将计划标记为就绪状态。 */
  public void markReady() {
    this.status = PlanStatus.READY;
  }

  /**
   * 更新计划状态为指定值。
   *
   * <p>此方法由事件处理器使用，根据聚合的切片状态更新计划状态。
   *
   * @param newStatus 要设置的新状态
   * @throws IllegalArgumentException 如果 newStatus 为 null
   */
  public void updateStatus(PlanStatus newStatus) {
    if (newStatus == null) {
      throw new IllegalArgumentException("newStatus 不能为 null");
    }
    this.status = newStatus;
  }

  /**
   * 获取操作代码字符串（如果存在）。
   *
   * @return 操作代码或 {@code null}
   */
  public String getOperationCode() {
    return operationCode == null ? null : operationCode.getCode();
  }
}
