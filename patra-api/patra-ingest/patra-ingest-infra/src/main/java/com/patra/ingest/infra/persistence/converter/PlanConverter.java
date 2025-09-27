package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.starter.core.json.JsonNodeSupport;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 计划聚合与数据对象转换器（MapStruct 接口）。
 * 使用 default 方法保留原有手写逻辑，统一组件模型为 Spring。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlanConverter extends JsonNodeSupport {

    default PlanDO toEntity(PlanAggregate aggregate) {
        if (aggregate == null) {
            return null;
        }
        PlanDO entity = new PlanDO();
        entity.setId(aggregate.getId());
        entity.setScheduleInstanceId(aggregate.getScheduleInstanceId());
        entity.setPlanKey(aggregate.getPlanKey());
        entity.setProvenanceCode(aggregate.getProvenanceCode());
        entity.setEndpointName(aggregate.getEndpointName());
        entity.setOperationCode(aggregate.getOperationCode());
        entity.setExprProtoHash(aggregate.getExprProtoHash());
        entity.setExprProtoSnapshot(readJsonNode(aggregate.getExprProtoSnapshotJson()));
        entity.setProvenanceConfigSnapshot(readJsonNode(aggregate.getProvenanceConfigSnapshotJson()));
        entity.setProvenanceConfigHash(aggregate.getProvenanceConfigHash());
        entity.setWindowFrom(aggregate.getWindowFrom());
        entity.setWindowTo(aggregate.getWindowTo());
        entity.setSliceStrategyCode(aggregate.getSliceStrategyCode());
        entity.setSliceParams(readJsonNode(aggregate.getSliceParamsJson()));
        // 使用字典编码
        entity.setStatusCode(aggregate.getStatus() == null ? null : aggregate.getStatus().getCode());
        entity.setVersion(aggregate.getVersion());
        return entity;
    }

    default PlanAggregate toAggregate(PlanDO entity) {
        if (entity == null) {
            return null;
        }
        String exprProtoSnapshot = writeJsonString(entity.getExprProtoSnapshot());
        String provenanceConfigSnapshot = writeJsonString(entity.getProvenanceConfigSnapshot());
        String sliceParams = writeJsonString(entity.getSliceParams());
        PlanStatus status = entity.getStatusCode() == null
                ? PlanStatus.DRAFT
                : PlanStatus.fromCode(entity.getStatusCode());
        long version = entity.getVersion() == null ? 0L : entity.getVersion();
        return PlanAggregate.restore(
                entity.getId(),
                entity.getScheduleInstanceId(),
                entity.getPlanKey(),
                entity.getProvenanceCode(),
                entity.getEndpointName(),
                entity.getOperationCode(),
                entity.getExprProtoHash(),
                exprProtoSnapshot,
                provenanceConfigSnapshot,
                entity.getProvenanceConfigHash(),
                entity.getWindowFrom(),
                entity.getWindowTo(),
                entity.getSliceStrategyCode(),
                sliceParams,
                status,
                version);
    }
}
