package com.patra.ingest.infra.persistence.converter;

import com.patra.common.json.JsonNodeMappings;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.model.vo.ExecutionTimeline;
import com.patra.ingest.domain.model.vo.LeaseInfo;
import com.patra.ingest.domain.model.vo.TaskSchedulerContext;
import com.patra.ingest.infra.persistence.entity.TaskDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct converter for {@link TaskAggregate} ↔ {@link TaskDO} transformations.
 *
 * <p>Handles JSON fields, status enum conversions, and value object decomposition in a single
 * converter to avoid extra support classes.
 */
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
        entity.getProvenanceCode(),
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
}
