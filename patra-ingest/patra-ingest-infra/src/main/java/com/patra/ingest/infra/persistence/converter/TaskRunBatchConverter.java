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
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/** TaskRunBatch（运行批次）聚合 ↔ DO 转换器。 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TaskRunBatchConverter {

  @Mapping(
      target = "idempotentKey",
      source = "idempotentKey",
      qualifiedByName = "idempotentKeyToString")
  @Mapping(target = "recordCount", source = "stats", qualifiedByName = "statsToRecordCount")
  @Mapping(target = "stats", source = "stats", qualifiedByName = "statsToJson")
  @Mapping(target = "statusCode", source = "status", qualifiedByName = "batchStatusToCode")
  @Mapping(target = "storageKey", source = "storageKey")
  TaskRunBatchDO toDO(TaskRunBatch source);

  default TaskRunBatch toDomain(TaskRunBatchDO entity) {
    return toTaskRunBatch(entity);
  }

  static TaskRunBatch toTaskRunBatch(TaskRunBatchDO entity) {
    if (entity == null) {
      return null;
    }
    BatchStatus status = batchStatusFromCode(entity.getStatusCode());
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
        entity.getExprHash(),
        stringToIdempotentKey(entity.getIdempotentKey()),
        status,
        stats,
        entity.getCommittedAt(),
        entity.getError(),
        entity.getStorageKey());
  }

  @Named("batchStatusToCode")
  static String batchStatusToCode(BatchStatus status) {
    return status == null ? null : status.getCode();
  }

  static BatchStatus batchStatusFromCode(String code) {
    return code == null ? BatchStatus.RUNNING : BatchStatus.fromCode(code);
  }

  @Named("idempotentKeyToString")
  static String idempotentKeyToString(IdempotentKey key) {
    return key == null ? null : key.value();
  }

  static IdempotentKey stringToIdempotentKey(String raw) {
    return raw == null ? null : new IdempotentKey(raw);
  }

  @Named("statsToRecordCount")
  static Integer statsToRecordCount(BatchStats stats) {
    return stats == null ? null : stats.recordCount();
  }

  @Named("statsToJson")
  static JsonNode statsToJson(BatchStats stats) {
    if (stats == null) {
      return null;
    }
    ObjectNode node = JsonMapperHolder.getObjectMapper().createObjectNode();
    node.put("recordCount", stats.recordCount());
    return node;
  }

  static BatchStats deriveStats(Integer recordCount, JsonNode statsNode) {
    if (recordCount != null) {
      return BatchStats.of(recordCount);
    }
    if (statsNode != null && statsNode.has("recordCount")) {
      return BatchStats.of(statsNode.get("recordCount").asInt());
    }
    return BatchStats.of(0);
  }
}
