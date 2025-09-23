package com.patra.ingest.infra.persistence.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.infra.persistence.entity.IngPlanDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 计划聚合与数据对象转换器。
 */
@Component
@RequiredArgsConstructor
public class PlanConverter {

    private final ObjectMapper objectMapper;

    public IngPlanDO toEntity(PlanAggregate aggregate) {
        IngPlanDO entity = new IngPlanDO();
        entity.setId(aggregate.getId());
        entity.setScheduleInstanceId(aggregate.getScheduleInstanceId());
        entity.setPlanKey(aggregate.getPlanKey());
        entity.setProvenanceCode(aggregate.getProvenanceCode());
        entity.setEndpointName(aggregate.getEndpointName());
        entity.setOperationCode(aggregate.getOperationCode());
        entity.setExprProtoHash(aggregate.getExprProtoHash());
        entity.setExprProtoSnapshot(readJson(aggregate.getExprProtoSnapshotJson()));
        entity.setProvenanceConfigSnapshot(readJson(aggregate.getProvenanceConfigSnapshotJson()));
        entity.setProvenanceConfigHash(aggregate.getProvenanceConfigHash());
        entity.setWindowFrom(aggregate.getWindowFrom());
        entity.setWindowTo(aggregate.getWindowTo());
        entity.setSliceStrategyCode(aggregate.getSliceStrategyCode());
        entity.setSliceParams(readJson(aggregate.getSliceParamsJson()));
        entity.setStatusCode(aggregate.getStatus().name());
        entity.setVersion(aggregate.getVersion());
        return entity;
    }

    public PlanAggregate toAggregate(IngPlanDO entity) {
        if (entity == null) {
            return null;
        }
        String exprProtoSnapshot = writeJson(entity.getExprProtoSnapshot());
        String provenanceConfigSnapshot = writeJson(entity.getProvenanceConfigSnapshot());
        String sliceParams = writeJson(entity.getSliceParams());
        PlanStatus status = entity.getStatusCode() == null
                ? PlanStatus.DRAFT
                : PlanStatus.valueOf(entity.getStatusCode());
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

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法解析计划快照JSON", e);
        }
    }

    private String writeJson(JsonNode node) {
        return node == null ? null : node.toString();
    }
}
