package dev.linqibin.patra.ingest.infra.adapter.persistence.converter.mapper;

import dev.linqibin.patra.ingest.domain.exception.InfrastructureException;
import dev.linqibin.patra.ingest.domain.model.entity.CursorEvent;
import dev.linqibin.patra.ingest.domain.model.enums.CursorDirection;
import dev.linqibin.patra.ingest.domain.model.enums.CursorType;
import dev.linqibin.patra.ingest.domain.model.enums.OperationCode;
import dev.linqibin.patra.ingest.domain.model.vo.cursor.CursorLineage;
import dev.linqibin.patra.ingest.infra.adapter.persistence.entity.CursorEventEntity;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/// 游标事件 JPA 实体转换器，负责领域对象与 JPA 实体转换。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CursorEventJpaMapper {

  @Mapping(target = "cursorTypeCode", source = "cursorType", qualifiedByName = "cursorTypeToCode")
  @Mapping(
      target = "directionCode",
      source = "direction",
      qualifiedByName = "cursorDirectionToCode")
  @Mapping(target = "scheduleInstanceId", source = "lineage.scheduleInstanceId")
  @Mapping(target = "planId", source = "lineage.planId")
  @Mapping(target = "sliceId", source = "lineage.sliceId")
  @Mapping(target = "taskId", source = "lineage.taskId")
  @Mapping(target = "runId", source = "lineage.runId")
  @Mapping(target = "batchId", source = "lineage.batchId")
  CursorEventEntity toEntity(CursorEvent source);

  default CursorEvent toAggregate(CursorEventEntity entity) {
    return toEvent(entity);
  }

  static CursorEvent toEvent(CursorEventEntity entity) {
    if (entity == null) {
      return null;
    }
    CursorType type = cursorTypeFromCode(entity.getCursorTypeCode());
    CursorDirection direction = cursorDirectionFromCode(entity.getDirectionCode());
    CursorLineage lineage =
        new CursorLineage(
            entity.getScheduleInstanceId(),
            entity.getPlanId(),
            entity.getSliceId(),
            entity.getTaskId(),
            entity.getRunId(),
            entity.getBatchId());
    return CursorEvent.restore(
        entity.getId(),
        ProvenanceCode.parse(entity.getProvenanceCode()),
        entity.getOperationCode(),
        entity.getCursorKey(),
        entity.getNamespaceScopeCode(),
        entity.getNamespaceKey(),
        type,
        entity.getPrevValue(),
        entity.getNewValue(),
        direction,
        entity.getIdempotentKey(),
        entity.getObservedMaxValue(),
        entity.getPrevInstant(),
        entity.getNewInstant(),
        entity.getPrevNumeric(),
        entity.getNewNumeric(),
        lineage,
        entity.getExprHash(),
        entity.getWindowFrom(),
        entity.getWindowTo());
  }

  @Named("cursorTypeToCode")
  static String cursorTypeToCode(CursorType type) {
    return type == null ? null : type.getCode();
  }

  static CursorType cursorTypeFromCode(String code) {
    return code == null ? null : CursorType.fromCode(code);
  }

  @Named("cursorDirectionToCode")
  static String cursorDirectionToCode(CursorDirection direction) {
    return direction == null ? null : direction.getCode();
  }

  static CursorDirection cursorDirectionFromCode(String code) {
    return code == null ? null : CursorDirection.fromCode(code);
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
