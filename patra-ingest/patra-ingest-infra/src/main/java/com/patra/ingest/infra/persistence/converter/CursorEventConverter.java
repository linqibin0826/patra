package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.entity.CursorEvent;
import com.patra.ingest.domain.model.enums.CursorDirection;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.vo.CursorLineage;
import com.patra.ingest.infra.persistence.entity.CursorEventDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/** CursorEvent aggregate ↔ DO converter. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CursorEventConverter {

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
  CursorEventDO toDO(CursorEvent source);

  default CursorEvent toDomain(CursorEventDO entity) {
    return toEvent(entity);
  }

  static CursorEvent toEvent(CursorEventDO entity) {
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
        entity.getProvenanceCode(),
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
        entity.getExprHash());
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
}
