package com.patra.ingest.infra.persistence.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.json.JsonMapperHolder;
import com.patra.ingest.domain.model.entity.TaskRunBatch;
import com.patra.ingest.domain.model.enums.BatchStatus;
import com.patra.ingest.domain.model.vo.BatchStats;
import com.patra.ingest.domain.model.vo.IdempotentKey;
import com.patra.ingest.infra.persistence.entity.TaskRunBatchDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TaskRunBatchConverter {

    default TaskRunBatchDO toDO(TaskRunBatch source) {
        if (source == null) {
            return null;
        }
        TaskRunBatchDO entity = new TaskRunBatchDO();
        entity.setId(source.getId());
        entity.setRunId(source.getRunId());
        entity.setTaskId(source.getTaskId());
        entity.setSliceId(source.getSliceId());
        entity.setPlanId(source.getPlanId());
        entity.setExprHash(null); // TODO: 领域模型尚未承载 exprHash
        entity.setProvenanceCode(source.getProvenanceCode());
        entity.setOperationCode(source.getOperationCode());
        entity.setBatchNo(source.getBatchNo());
        entity.setPageNo(source.getPageNo());
        entity.setPageSize(source.getPageSize());
        entity.setBeforeToken(source.getBeforeToken());
        entity.setAfterToken(source.getAfterToken());
        entity.setIdempotentKey(mapIdempotentKey(source.getIdempotentKey()));
        entity.setRecordCount(extractRecordCount(source.getStats()));
        entity.setStatusCode(source.getStatus() == null ? null : source.getStatus().getCode());
        entity.setCommittedAt(source.getCommittedAt());
        entity.setError(source.getError());
        entity.setStats(buildStatsNode(source.getStats()));
        return entity;
    }

    default TaskRunBatch toDomain(TaskRunBatchDO entity) {
        if (entity == null) {
            return null;
        }
        BatchStatus status = entity.getStatusCode() == null
                ? BatchStatus.RUNNING
                : BatchStatus.fromCode(entity.getStatusCode());
        BatchStats stats = deriveStats(entity.getRecordCount(), entity.getStats());
        return TaskRunBatch.restore(
                entity.getId(),
                entity.getRunId(),
                entity.getTaskId(),
                entity.getSliceId(),
                entity.getPlanId(),
                entity.getProvenanceCode(),
                entity.getOperationCode(),
                entity.getBatchNo() == null ? 0 : entity.getBatchNo(),
                entity.getPageNo(),
                entity.getPageSize(),
                entity.getBeforeToken(),
                entity.getAfterToken(),
                mapIdempotentKey(entity.getIdempotentKey()),
                status,
                stats,
                entity.getCommittedAt(),
                entity.getError());
    }

    default IdempotentKey mapIdempotentKey(String raw) {
        return raw == null ? null : new IdempotentKey(raw);
    }

    default String mapIdempotentKey(IdempotentKey key) {
        return key == null ? null : key.value();
    }

    private Integer extractRecordCount(BatchStats stats) {
        return stats == null ? null : stats.recordCount();
    }

    private BatchStats deriveStats(Integer recordCount, JsonNode statsNode) {
        if (recordCount != null) {
            return BatchStats.of(recordCount);
        }
        if (statsNode != null && statsNode.has("recordCount")) {
            return BatchStats.of(statsNode.get("recordCount").asInt());
        }
        return BatchStats.of(0);
    }

    private ObjectNode buildStatsNode(BatchStats stats) {
        if (stats == null) {
            return null;
        }
        ObjectNode node = JsonMapperHolder.getObjectMapper().createObjectNode();
        node.put("recordCount", stats.recordCount());
        return node;
    }
}
