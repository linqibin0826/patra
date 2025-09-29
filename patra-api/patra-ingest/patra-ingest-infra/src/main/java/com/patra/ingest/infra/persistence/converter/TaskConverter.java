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
 * 任务聚合 {@link TaskAggregate} ↔ 数据对象 {@link TaskDO} 的 MapStruct 转换器。
 * <p>JSON 字段、状态枚举与值对象拆解统一在此转换器中完成，避免额外的 support 类。</p>
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TaskConverter {

    @Mapping(target = "params", expression = "java(com.patra.common.json.JsonNodeMappings.jsonStringToNode(aggregate.getParamsJson()))")
    @Mapping(target = "statusCode", source = "status", qualifiedByName = "taskStatusToCode")
    @Mapping(target = "leaseOwner", source = "leaseInfo.owner")
    @Mapping(target = "leasedUntil", source = "leaseInfo.leasedUntil")
    @Mapping(target = "leaseCount", source = "leaseInfo.leaseCount")
    @Mapping(target = "startedAt", source = "executionTimeline.startedAt")
    @Mapping(target = "finishedAt", source = "executionTimeline.finishedAt")
    @Mapping(target = "schedulerRunId", source = "schedulerContext.schedulerRunId")
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
        LeaseInfo leaseInfo = LeaseInfo.snapshotOf(entity.getLeaseOwner(), entity.getLeasedUntil(), entity.getLeaseCount());
        ExecutionTimeline timeline = new ExecutionTimeline(entity.getStartedAt(), entity.getFinishedAt());
        TaskSchedulerContext schedulerContext = new TaskSchedulerContext(entity.getSchedulerRunId(), entity.getCorrelationId());
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
