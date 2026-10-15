package dev.linqibin.patra.ingest.infra.adapter.persistence.converter.mapper;

import dev.linqibin.patra.common.enums.ProvenanceCode;
import dev.linqibin.patra.ingest.domain.exception.InfrastructureException;
import dev.linqibin.patra.ingest.domain.model.aggregate.TaskAggregate;
import dev.linqibin.patra.ingest.domain.model.enums.OperationCode;
import dev.linqibin.patra.ingest.domain.model.enums.TaskStatus;
import dev.linqibin.patra.ingest.domain.model.vo.execution.ExecutionTimeline;
import dev.linqibin.patra.ingest.domain.model.vo.plan.PlanId;
import dev.linqibin.patra.ingest.domain.model.vo.plan.TaskSchedulerContext;
import dev.linqibin.patra.ingest.domain.model.vo.schedule.ScheduleInstanceId;
import dev.linqibin.patra.ingest.domain.model.vo.shared.LeaseInfo;
import dev.linqibin.patra.ingest.domain.model.vo.slice.PlanSliceId;
import dev.linqibin.patra.ingest.domain.model.vo.task.TaskId;
import dev.linqibin.patra.ingest.infra.adapter.persistence.entity.TaskEntity;
import dev.linqibin.starter.jpa.json.JsonColumnCodec;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

/// 任务 JPA 实体转换器，负责领域对象与 JPA 实体转换。
///
/// 转换规则：任务聚合 {@link TaskAggregate} ↔ {@link TaskEntity} 双向转换。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class TaskJpaMapper {

  /// JSON 列转换器，由容器注入。
  @Autowired protected JsonColumnCodec codec;

  @Mapping(target = "id", source = "id", qualifiedByName = "taskIdToLong")
  @Mapping(
      target = "scheduleInstanceId",
      source = "scheduleInstanceId",
      qualifiedByName = "scheduleInstanceIdToLong")
  @Mapping(target = "planId", source = "planId", qualifiedByName = "planIdToLong")
  @Mapping(target = "sliceId", source = "sliceId", qualifiedByName = "planSliceIdToLong")
  @Mapping(target = "params", expression = "java(codec.read(aggregate.getParamsJson()))")
  @Mapping(target = "statusCode", source = "status", qualifiedByName = "taskStatusToCode")
  @Mapping(target = "leaseOwner", source = "leaseInfo.owner")
  @Mapping(target = "leasedUntil", source = "leaseInfo.leasedUntil")
  @Mapping(target = "leaseCount", source = "leaseInfo.leaseCount")
  @Mapping(target = "startedAt", source = "executionTimeline.startedAt")
  @Mapping(target = "finishedAt", source = "executionTimeline.finishedAt")
  @Mapping(target = "correlationId", source = "schedulerContext.correlationId")
  // 审计字段由 JPA 管理
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdByName", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedByName", ignore = true)
  @Mapping(target = "ipAddress", ignore = true)
  @Mapping(target = "recordRemarks", ignore = true)
  public abstract TaskEntity toEntity(TaskAggregate aggregate);

  public TaskAggregate toAggregate(TaskEntity entity) {
    return toTaskAggregate(entity);
  }

  public TaskAggregate toTaskAggregate(TaskEntity entity) {
    if (entity == null) {
      return null;
    }
    TaskStatus status = taskStatusFromCode(entity.getStatusCode());
    long version = entity.getVersion() == null ? 0L : entity.getVersion();
    String paramsJson = codec.write(entity.getParams());
    LeaseInfo leaseInfo =
        LeaseInfo.snapshotOf(
            entity.getLeaseOwner(), entity.getLeasedUntil(), entity.getLeaseCount());
    ExecutionTimeline timeline =
        new ExecutionTimeline(entity.getStartedAt(), entity.getFinishedAt());
    TaskSchedulerContext schedulerContext = new TaskSchedulerContext(entity.getCorrelationId());
    ScheduleInstanceId scheduleInstanceId =
        entity.getScheduleInstanceId() != null
            ? ScheduleInstanceId.of(entity.getScheduleInstanceId())
            : null;
    PlanId planId = entity.getPlanId() != null ? PlanId.of(entity.getPlanId()) : null;
    PlanSliceId sliceId = entity.getSliceId() != null ? PlanSliceId.of(entity.getSliceId()) : null;
    return TaskAggregate.restore(
        TaskId.of(entity.getId()),
        scheduleInstanceId,
        planId,
        sliceId,
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

  @Named("taskIdToLong")
  public static Long taskIdToLong(TaskId id) {
    return id != null ? id.value() : null;
  }

  @Named("scheduleInstanceIdToLong")
  public static Long scheduleInstanceIdToLong(ScheduleInstanceId id) {
    return id != null ? id.value() : null;
  }

  @Named("planIdToLong")
  public static Long planIdToLong(PlanId id) {
    return id != null ? id.value() : null;
  }

  @Named("planSliceIdToLong")
  public static Long planSliceIdToLong(PlanSliceId id) {
    return id != null ? id.value() : null;
  }

  @Named("taskStatusToCode")
  public static String taskStatusToCode(TaskStatus status) {
    return status == null ? null : status.getCode();
  }

  public static TaskStatus taskStatusFromCode(String code) {
    return code == null ? TaskStatus.QUEUED : TaskStatus.fromCode(code);
  }

  // ========== 枚举转换方法 ==========

  public String map(ProvenanceCode code) {
    return code == null ? null : code.getCode();
  }

  public ProvenanceCode mapProvenanceCode(String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    try {
      return ProvenanceCode.parse(code);
    } catch (IllegalArgumentException e) {
      throw new InfrastructureException("数据库中存在无效的 provenance_code: " + code, e);
    }
  }

  public String map(OperationCode code) {
    return code == null ? null : code.getCode();
  }

  public OperationCode mapOperationCode(String code) {
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
