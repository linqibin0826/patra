package com.patra.ingest.infra.persistence.converter;

import com.patra.common.enums.ProvenanceCode;
import com.patra.common.json.JsonNodeMappings;
import com.patra.ingest.domain.exception.InfrastructureException;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.ingest.infra.persistence.entity.ScheduleInstanceDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/// 调度实例聚合 ↔ DO 转换器。
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ScheduleInstanceConverter {

  @Mapping(target = "schedulerCode", source = "scheduler", qualifiedByName = "schedulerToCode")
  @Mapping(
      target = "triggerTypeCode",
      source = "triggerType",
      qualifiedByName = "triggerTypeToCode")
  @Mapping(
      target = "triggerParams",
      expression =
          "java(com.patra.common.json.JsonNodeMappings.mapToJsonNode(aggregate.getTriggerParams()))")
  ScheduleInstanceDO toDO(ScheduleInstanceAggregate aggregate);

  default ScheduleInstanceAggregate toDomain(ScheduleInstanceDO entity) {
    return toScheduleInstance(entity);
  }

  static ScheduleInstanceAggregate toScheduleInstance(ScheduleInstanceDO entity) {
    if (entity == null) {
      return null;
    }
    long version = entity.getVersion() == null ? 0L : entity.getVersion();
    return ScheduleInstanceAggregate.restore(
        entity.getId(),
        schedulerFromCode(entity.getSchedulerCode()),
        entity.getSchedulerJobId(),
        entity.getSchedulerLogId(),
        triggerTypeFromCode(entity.getTriggerTypeCode()),
        entity.getTriggeredAt(),
        JsonNodeMappings.jsonNodeToMap(entity.getTriggerParams()),
        ProvenanceCode.parse(entity.getProvenanceCode()),
        version);
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
