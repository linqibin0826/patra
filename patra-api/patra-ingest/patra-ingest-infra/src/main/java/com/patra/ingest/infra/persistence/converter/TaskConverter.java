package com.patra.ingest.infra.persistence.converter;

import com.patra.common.enums.ProvenanceCode;
import com.patra.common.json.JsonNodeMappings;
import com.patra.ingest.domain.exception.InfrastructureException;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.model.vo.execution.ExecutionTimeline;
import com.patra.ingest.domain.model.vo.plan.TaskSchedulerContext;
import com.patra.ingest.domain.model.vo.shared.LeaseInfo;
import com.patra.ingest.infra.persistence.entity.TaskDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/// 任务对象转换器,负责领域对象与数据库实体转换。
///
/// 转换规则: 任务聚合 {@link TaskAggregate} ↔ {@link TaskDO} 双向转换。在单个转换器中处理JSON字段、状态枚举转换和值对象分解,避免额外的支持类。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TaskConverter {

  @Mapping(
      target = "params",
      expression =
          "java(com.patra.common.json.JsonNodeMappings.jsonStringToNode(aggregate.getParamsJson()))")
  @Mapping(target = "statusCode", source = "status", qualifiedByName = "taskStatusToCode")
  @Mapping(target = "leaseOwner", source = "leaseInfo.owner")
  @Mapping(target = "leasedUntil", source = "leaseInfo.leasedUntil")
  @Mapping(target = "leaseCount", source = "leaseInfo.leaseCount")
  @Mapping(target = "startedAt", source = "executionTimeline.startedAt")
  @Mapping(target = "finishedAt", source = "executionTimeline.finishedAt")
  @Mapping(target = "correlationId", source = "schedulerContext.correlationId")
  TaskDO toEntity(TaskAggregate aggregate);

  default TaskAggregate toAggregate(TaskDO entity) {
    return toTaskAggregate(entity);
  }

  static TaskAggregate toTaskAggregate(TaskDO entity) {
    if (entity == null) {
      return null;
    }
    TaskStatus status = taskStatusFromCode(entity.getStatusCode());
    long version = entity.getVersion() == null ? 0L : entity.getVersion();
    String paramsJson = JsonNodeMappings.jsonNodeToString(entity.getParams());
    LeaseInfo leaseInfo =
        LeaseInfo.snapshotOf(
            entity.getLeaseOwner(), entity.getLeasedUntil(), entity.getLeaseCount());
    ExecutionTimeline timeline =
        new ExecutionTimeline(entity.getStartedAt(), entity.getFinishedAt());
    TaskSchedulerContext schedulerContext = new TaskSchedulerContext(entity.getCorrelationId());
    return TaskAggregate.restore(
        entity.getId(),
        entity.getScheduleInstanceId(),
        entity.getPlanId(),
        entity.getSliceId(),
        ProvenanceCode.parse(entity.getProvenanceCode()),
        entity.getOperationCode(),
        paramsJson,
        entity.getIdempotentKey(),
        entity.getExprHash(),
        entity.getPriority(),
        entity.getScheduledAt(),
        entity.getLastHeartbeatAt(),
        entity.getRetryCount(),
        entity.getLastErrorCode(),
        entity.getLastErrorMsg(),
        status,
        leaseInfo,
        timeline,
        schedulerContext,
        version);
  }

  @Named("taskStatusToCode")
  static String taskStatusToCode(TaskStatus status) {
    return status == null ? null : status.getCode();
  }

  static TaskStatus taskStatusFromCode(String code) {
    return code == null ? TaskStatus.QUEUED : TaskStatus.fromCode(code);
  }

  // ========== 枚举转换方法 ==========

  /// ProvenanceCode 枚举 → String（用于 Domain → DO）
  default String map(ProvenanceCode code) {
    return code == null ? null : code.getCode();
  }

  /// String → ProvenanceCode 枚举（用于 DO → Domain）
  default ProvenanceCode mapProvenanceCode(String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    try {
      return ProvenanceCode.parse(code);
    } catch (IllegalArgumentException e) {
      throw new InfrastructureException("数据库中存在无效的 provenance_code: " + code, e);
    }
  }

  /// OperationCode 枚举 → String
  default String map(OperationCode code) {
    return code == null ? null : code.getCode();
  }

  /// String → OperationCode 枚举
  default OperationCode mapOperationCode(String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    try {
      return OperationCode.fromCode(code);
    } catch (IllegalArgumentException e) {
      throw new InfrastructureException("数据库中存在无效的 operation_code: " + code, e);
    }
  }
}
