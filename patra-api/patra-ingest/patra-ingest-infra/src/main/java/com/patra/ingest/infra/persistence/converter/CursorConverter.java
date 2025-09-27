package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.entity.Cursor;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.enums.NamespaceScope;
import com.patra.ingest.domain.model.vo.CursorLineage;
import com.patra.ingest.domain.model.vo.CursorValue;
import com.patra.ingest.domain.model.vo.CursorWatermark;
import com.patra.ingest.infra.persistence.entity.CursorDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CursorConverter {

    default CursorDO toDO(Cursor source) {
        if (source == null) {
            return null;
        }
        CursorDO entity = new CursorDO();
        entity.setId(source.getId());
        entity.setProvenanceCode(source.getProvenanceCode());
        entity.setOperationCode(source.getOperationCode());
        entity.setCursorKey(source.getCursorKey());
        entity.setNamespaceScopeCode(source.getNamespaceScope() == null ? null : source.getNamespaceScope().getCode());
        entity.setNamespaceKey(source.getNamespaceKey());
        entity.setCursorTypeCode(source.getCursorType() == null ? null : source.getCursorType().getCode());
        CursorValue value = source.getValue();
        if (value != null) {
            entity.setCursorValue(value.raw());
            entity.setNormalizedInstant(value.instant());
            entity.setNormalizedNumeric(value.numeric());
        }
        CursorWatermark watermark = source.getWatermark();
        if (watermark != null) {
            entity.setObservedMaxValue(watermark.observedMaxValue());
            if (watermark.hasInstant()) {
                entity.setNormalizedInstant(watermark.normalizedInstant());
            }
            if (watermark.hasNumeric()) {
                entity.setNormalizedNumeric(watermark.normalizedNumeric());
            }
        }
        CursorLineage lineage = source.getLineage();
        if (lineage != null) {
            entity.setScheduleInstanceId(lineage.scheduleInstanceId());
            entity.setPlanId(lineage.planId());
            entity.setSliceId(lineage.sliceId());
            entity.setTaskId(lineage.taskId());
            entity.setLastRunId(lineage.runId());
            entity.setLastBatchId(lineage.batchId());
        }
        entity.setExprHash(source.getExprHash());
        return entity;
    }

    default Cursor toDomain(CursorDO entity) {
        if (entity == null) {
            return null;
        }
        CursorType type = entity.getCursorTypeCode() == null ? null : CursorType.fromCode(entity.getCursorTypeCode());
        NamespaceScope scope = entity.getNamespaceScopeCode() == null ? null : NamespaceScope.fromCode(entity.getNamespaceScopeCode());
        CursorValue value = new CursorValue(
                type,
                entity.getCursorValue(),
                entity.getNormalizedInstant(),
                entity.getNormalizedNumeric());
        CursorWatermark watermark = new CursorWatermark(
                entity.getObservedMaxValue(),
                entity.getNormalizedInstant(),
                entity.getNormalizedNumeric());
        CursorLineage lineage = new CursorLineage(
                entity.getScheduleInstanceId(),
                entity.getPlanId(),
                entity.getSliceId(),
                entity.getTaskId(),
                entity.getLastRunId(),
                entity.getLastBatchId());
        return Cursor.restore(
                entity.getId(),
                entity.getProvenanceCode(),
                entity.getOperationCode(),
                entity.getCursorKey(),
                scope,
                entity.getNamespaceKey(),
                type,
                value,
                watermark,
                lineage,
                entity.getExprHash());
    }
}
