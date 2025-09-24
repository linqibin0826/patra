package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.ingest.infra.persistence.entity.PlanSliceDO;
import org.springframework.stereotype.Component;

/**
 * 计划切片转换器。
 */
@Component
public class PlanSliceConverter {

    public PlanSliceDO toEntity(PlanSliceAggregate aggregate) {
        PlanSliceDO entity = new PlanSliceDO();
        entity.setId(aggregate.getId());
        entity.setPlanId(aggregate.getPlanId());
        entity.setProvenanceCode(aggregate.getProvenanceCode());
        entity.setSliceNo(aggregate.getSequence());
        entity.setSliceSignatureHash(aggregate.getSliceSignatureHash());
        entity.setSliceSpec(aggregate.getSliceSpecJson());
        entity.setExprHash(aggregate.getExprHash());
        entity.setExprSnapshot(aggregate.getExprSnapshotJson());
        // 使用字典编码
        entity.setStatusCode(aggregate.getStatus() == null ? null : aggregate.getStatus().getCode());
        entity.setVersion(aggregate.getVersion());
        return entity;
    }

    public PlanSliceAggregate toAggregate(PlanSliceDO entity) {
        if (entity == null) {
            return null;
        }
        SliceStatus status = entity.getStatusCode() == null
                ? SliceStatus.PENDING
                : SliceStatus.fromCode(entity.getStatusCode());
        long version = entity.getVersion() == null ? 0L : entity.getVersion();
        return PlanSliceAggregate.restore(
                entity.getId(),
                entity.getPlanId(),
                entity.getProvenanceCode(),
                entity.getSliceNo() == null ? 0 : entity.getSliceNo(),
                entity.getSliceSignatureHash(),
                entity.getSliceSpec(),
                entity.getExprHash(),
                entity.getExprSnapshot(),
                status,
                version);
    }
}
