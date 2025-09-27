package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.entity.CursorEvent;
import com.patra.ingest.domain.model.enums.CursorDirection;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.vo.CursorLineage;
import com.patra.ingest.infra.persistence.entity.CursorEventDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CursorEventConverter {

    default CursorEventDO toDO(CursorEvent source) {
        if (source == null) {
            return null;
        }
        CursorEventDO entity = new CursorEventDO();
        entity.setId(source.getId());
        entity.setProvenanceCode(source.getProvenanceCode());
        entity.setOperationCode(source.getOperationCode());
        entity.setCursorKey(source.getCursorKey());
        entity.setNamespaceScopeCode(source.getNamespaceScopeCode());
        entity.setNamespaceKey(source.getNamespaceKey());
        entity.setCursorTypeCode(source.getCursorType() == null ? null : source.getCursorType().getCode());
        entity.setPrevValue(source.getPrevValue());
        entity.setNewValue(source.getNewValue());
        entity.setWindowFrom(source.getWindowFrom());
        entity.setWindowTo(source.getWindowTo());
        entity.setDirectionCode(source.getDirection() == null ? null : source.getDirection().getCode());
        entity.setIdempotentKey(source.getIdempotentKey());
        entity.setObservedMaxValue(source.getObservedMaxValue());
        entity.setPrevInstant(source.getPrevInstant());
        entity.setNewInstant(source.getNewInstant());
        entity.setPrevNumeric(source.getPrevNumeric());
        entity.setNewNumeric(source.getNewNumeric());
        CursorLineage lineage = source.getLineage();
        if (lineage != null) {
            entity.setScheduleInstanceId(lineage.scheduleInstanceId());
            entity.setPlanId(lineage.planId());
            entity.setSliceId(lineage.sliceId());
            entity.setTaskId(lineage.taskId());
            entity.setRunId(lineage.runId());
            entity.setBatchId(lineage.batchId());
        }
        entity.setExprHash(source.getExprHash());
        return entity;
    }

    default CursorEvent toDomain(CursorEventDO entity) {
        if (entity == null) {
            return null;
        }
        CursorType type = entity.getCursorTypeCode() == null ? null : CursorType.fromCode(entity.getCursorTypeCode());
        CursorDirection direction = entity.getDirectionCode() == null ? null : CursorDirection.fromCode(entity.getDirectionCode());
        CursorLineage lineage = new CursorLineage(
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
                entity.getWindowFrom(),
                entity.getWindowTo(),
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
}
