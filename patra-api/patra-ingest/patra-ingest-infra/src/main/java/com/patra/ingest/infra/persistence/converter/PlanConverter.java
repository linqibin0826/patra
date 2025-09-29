package com.patra.ingest.infra.persistence.converter;

import com.patra.common.json.JsonNodeMappings;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * 计划聚合 {@link PlanAggregate} ↔ 数据对象 {@link PlanDO} 转换器。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlanConverter {

    @Mapping(target = "exprProtoSnapshot", expression = "java(com.patra.common.json.JsonNodeMappings.jsonStringToNode(aggregate.getExprProtoSnapshotJson()))")
    @Mapping(target = "provenanceConfigSnapshot", expression = "java(com.patra.common.json.JsonNodeMappings.jsonStringToNode(aggregate.getProvenanceConfigSnapshotJson()))")
    @Mapping(target = "sliceParams", expression = "java(com.patra.common.json.JsonNodeMappings.jsonStringToNode(aggregate.getSliceParamsJson()))")
    @Mapping(target = "statusCode", source = "status", qualifiedByName = "planStatusToCode")
    PlanDO toEntity(PlanAggregate aggregate);

    default PlanAggregate toAggregate(PlanDO entity) {
        return toPlanAggregate(entity);
    }

    static PlanAggregate toPlanAggregate(PlanDO entity) {
        if (entity == null) {
            return null;
        }
        PlanStatus status = planStatusFromCode(entity.getStatusCode());
        long version = entity.getVersion() == null ? 0L : entity.getVersion();
        return PlanAggregate.restore(
                entity.getId(),
                entity.getScheduleInstanceId(),
                entity.getPlanKey(),
                entity.getProvenanceCode(),
                entity.getEndpointName(),
                entity.getOperationCode(),
                entity.getExprProtoHash(),
                JsonNodeMappings.jsonNodeToString(entity.getExprProtoSnapshot()),
                JsonNodeMappings.jsonNodeToString(entity.getProvenanceConfigSnapshot()),
                entity.getProvenanceConfigHash(),
                entity.getWindowFrom(),
                entity.getWindowTo(),
                entity.getSliceStrategyCode(),
                JsonNodeMappings.jsonNodeToString(entity.getSliceParams()),
                status,
                version);
    }

    @Named("planStatusToCode")
    static String planStatusToCode(PlanStatus status) {
        return status == null ? null : status.getCode();
    }

    static PlanStatus planStatusFromCode(String code) {
        return code == null ? PlanStatus.DRAFT : PlanStatus.fromCode(code);
    }
}
