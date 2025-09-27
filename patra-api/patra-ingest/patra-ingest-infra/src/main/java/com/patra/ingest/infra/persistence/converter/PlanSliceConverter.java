package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.starter.core.json.JsonNodeSupport;
import com.patra.ingest.infra.persistence.entity.PlanSliceDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 计划切片转换器（MapStruct 接口）。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlanSliceConverter extends JsonNodeSupport {

    default PlanSliceDO toEntity(PlanSliceAggregate aggregate) {
        if (aggregate == null) {
            return null;
        }
        PlanSliceDO entity = new PlanSliceDO();
        entity.setId(aggregate.getId());
        entity.setPlanId(aggregate.getPlanId());
        entity.setProvenanceCode(aggregate.getProvenanceCode());
        entity.setSliceNo(aggregate.getSequence());
        entity.setSliceSignatureHash(aggregate.getSliceSignatureHash());
        entity.setSliceSpec(readJsonNode(aggregate.getSliceSpecJson()));
        entity.setExprHash(aggregate.getExprHash());
        entity.setExprSnapshot(readJsonNode(aggregate.getExprSnapshotJson()));
        // 使用字典编码
        entity.setStatusCode(aggregate.getStatus() == null ? null : aggregate.getStatus().getCode());
        entity.setVersion(aggregate.getVersion());
        return entity;
    }

    default PlanSliceAggregate toAggregate(PlanSliceDO entity) {
        if (entity == null) {
            return null;
        }
        SliceStatus status = entity.getStatusCode() == null
                ? SliceStatus.PENDING
                : SliceStatus.fromCode(entity.getStatusCode());
        long version = entity.getVersion() == null ? 0L : entity.getVersion();
        String sliceSpecJson = writeJsonString(entity.getSliceSpec());
        String exprSnapshotJson = writeJsonString(entity.getExprSnapshot());
        return PlanSliceAggregate.restore(
                entity.getId(),
                entity.getPlanId(),
                entity.getProvenanceCode(),
                entity.getSliceNo() == null ? 0 : entity.getSliceNo(),
                entity.getSliceSignatureHash(),
                sliceSpecJson,
                entity.getExprHash(),
                exprSnapshotJson,
                status,
                version);
    }
}
