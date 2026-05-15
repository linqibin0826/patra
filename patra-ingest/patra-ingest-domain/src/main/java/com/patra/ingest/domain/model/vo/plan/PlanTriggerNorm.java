package com.patra.ingest.domain.model.vo.plan;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import dev.linqibin.commons.enums.Priority;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/// 计划触发规范化值对象,表示规范化的触发命令。
///
/// 不可变性:通过值语义比较相等性
///
/// 业务约束:
///
/// - scheduleInstanceId必须非空
///   - provenanceCode必须非空
///   - operationCode必须非空
///   - triggerType必须非空
///   - scheduler必须非空
///
/// 使用场景:封装来自不同调度器的触发命令,提供统一的领域模型
///
/// @param scheduleInstanceId 调度实例标识符
/// @param provenanceCode 来源代码
/// @param operationCode 操作类型
/// @param step 切片规划步骤
/// @param triggerType 触发类型
/// @param scheduler 调度器类型
/// @param schedulerJobId 调度器作业标识符
/// @param schedulerLogId 调度器日志标识符
/// @param requestedWindowFrom 请求的窗口起始时间
/// @param requestedWindowTo 请求的窗口结束时间
/// @param priority 优先级
/// @param triggerParams 附加触发参数
public record PlanTriggerNorm(
    Long scheduleInstanceId,
    ProvenanceCode provenanceCode,
    OperationCode operationCode,
    String step,
    TriggerType triggerType,
    Scheduler scheduler,
    String schedulerJobId,
    String schedulerLogId,
    Instant requestedWindowFrom,
    Instant requestedWindowTo,
    Priority priority,
    Map<String, Object> triggerParams) {
  public PlanTriggerNorm {
    Objects.requireNonNull(scheduleInstanceId, "scheduleInstanceId不能为null");
    Objects.requireNonNull(provenanceCode, "provenanceCode不能为null");
    Objects.requireNonNull(operationCode, "operationCode不能为null");
    Objects.requireNonNull(triggerType, "triggerType不能为null");
    Objects.requireNonNull(scheduler, "scheduler不能为null");
  }

  /// 检查是否为采集操作。
  ///
  /// @return 如果操作类型为HARVEST则返回true
  public boolean isHarvest() {
    return operationCode == OperationCode.HARVEST;
  }

  /// 检查是否为回填操作。
  ///
  /// @return 如果操作类型为BACKFILL则返回true
  public boolean isBackfill() {
    return operationCode == OperationCode.BACKFILL;
  }

  /// 检查是否为更新操作。
  ///
  /// @return 如果操作类型为UPDATE则返回true
  public boolean isUpdate() {
    return operationCode == OperationCode.UPDATE;
  }
}
