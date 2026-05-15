package com.patra.ingest.infra.adapter.persistence.converter.mapper;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.exception.InfrastructureException;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.ingest.domain.model.vo.schedule.ScheduleInstanceId;
import com.patra.ingest.infra.adapter.persistence.entity.ScheduleInstanceEntity;
import dev.linqibin.commons.json.JsonNodeMappings;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/// 调度实例 JPA 实体转换器，负责领域对象与 JPA 实体转换。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ScheduleInstanceJpaMapper {

  @Mapping(target = "id", source = "id", qualifiedByName = "scheduleInstanceIdToLong")
  @Mapping(target = "schedulerCode", source = "scheduler", qualifiedByName = "schedulerToCode")
  @Mapping(
      target = "triggerTypeCode",
      source = "triggerType",
      qualifiedByName = "triggerTypeToCode")
  @Mapping(
      target = "triggerParams",
      expression =
          "java(dev.linqibin.commons.json.JsonNodeMappings.mapToJsonNode(aggregate.getTriggerParams()))")
  ScheduleInstanceEntity toEntity(ScheduleInstanceAggregate aggregate);

  default ScheduleInstanceAggregate toAggregate(ScheduleInstanceEntity entity) {
    return toScheduleInstance(entity);
  }

  static ScheduleInstanceAggregate toScheduleInstance(ScheduleInstanceEntity entity) {
    if (entity == null) {
      return null;
    }
    // ValueObjectJpaEntity 使用 DELETE/INSERT 模式，无需乐观锁
    return ScheduleInstanceAggregate.restore(
        ScheduleInstanceId.of(entity.getId()),
        schedulerFromCode(entity.getSchedulerCode()),
        entity.getSchedulerJobId(),
        entity.getSchedulerLogId(),
        triggerTypeFromCode(entity.getTriggerTypeCode()),
        entity.getTriggeredAt(),
        JsonNodeMappings.jsonNodeToMap(entity.getTriggerParams()),
        ProvenanceCode.parse(entity.getProvenanceCode()),
        0L);
  }

  @Named("scheduleInstanceIdToLong")
  static Long scheduleInstanceIdToLong(ScheduleInstanceId id) {
    return id != null ? id.value() : null;
  }

  @Named("schedulerToCode")
  static String schedulerToCode(Scheduler scheduler) {
    return scheduler == null ? null : scheduler.getCode();
  }

  static Scheduler schedulerFromCode(String code) {
    return code == null ? null : Scheduler.fromCode(code);
  }

  @Named("triggerTypeToCode")
  static String triggerTypeToCode(TriggerType triggerType) {
    return triggerType == null ? null : triggerType.getCode();
  }

  static TriggerType triggerTypeFromCode(String code) {
    return code == null ? null : TriggerType.fromCode(code);
  }

  // ========== 枚举转换方法 ==========

  default String map(ProvenanceCode code) {
    return code == null ? null : code.getCode();
  }

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

  default String map(OperationCode code) {
    return code == null ? null : code.getCode();
  }

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
