package dev.patra.ingest.infra.adapter.persistence.converter.mapper;

import dev.patra.common.ProvenanceCode;
import dev.patra.ingest.domain.exception.InfrastructureException;
import dev.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import dev.patra.ingest.domain.model.enums.OperationCode;
import dev.patra.ingest.domain.model.enums.Scheduler;
import dev.patra.ingest.domain.model.enums.TriggerType;
import dev.patra.ingest.domain.model.vo.schedule.ScheduleInstanceId;
import dev.patra.ingest.infra.adapter.persistence.entity.ScheduleInstanceEntity;
import dev.patra.tool.jpa.json.JsonColumnCodec;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

/// 调度实例 JPA 实体转换器，负责领域对象与 JPA 实体转换。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class ScheduleInstanceJpaMapper {

  /// JSON 列转换器，由容器注入。
  @Autowired protected JsonColumnCodec codec;

  @Mapping(target = "id", source = "id", qualifiedByName = "scheduleInstanceIdToLong")
  @Mapping(target = "schedulerCode", source = "scheduler", qualifiedByName = "schedulerToCode")
  @Mapping(
      target = "triggerTypeCode",
      source = "triggerType",
      qualifiedByName = "triggerTypeToCode")
  @Mapping(
      target = "triggerParams",
      expression = "java(codec.fromMap(aggregate.getTriggerParams()))")
  public abstract ScheduleInstanceEntity toEntity(ScheduleInstanceAggregate aggregate);

  public ScheduleInstanceAggregate toAggregate(ScheduleInstanceEntity entity) {
    return toScheduleInstance(entity);
  }

  public ScheduleInstanceAggregate toScheduleInstance(ScheduleInstanceEntity entity) {
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
        codec.toMap(entity.getTriggerParams()),
        ProvenanceCode.parse(entity.getProvenanceCode()),
        0L);
  }

  @Named("scheduleInstanceIdToLong")
  public static Long scheduleInstanceIdToLong(ScheduleInstanceId id) {
    return id != null ? id.value() : null;
  }

  @Named("schedulerToCode")
  public static String schedulerToCode(Scheduler scheduler) {
    return scheduler == null ? null : scheduler.getCode();
  }

  public static Scheduler schedulerFromCode(String code) {
    return code == null ? null : Scheduler.fromCode(code);
  }

  @Named("triggerTypeToCode")
  public static String triggerTypeToCode(TriggerType triggerType) {
    return triggerType == null ? null : triggerType.getCode();
  }

  public static TriggerType triggerTypeFromCode(String code) {
    return code == null ? null : TriggerType.fromCode(code);
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
