package com.patra.ingest.infra.adapter.persistence.converter.mapper;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.exception.InfrastructureException;
import com.patra.ingest.domain.model.entity.TaskRunBatch;
import com.patra.ingest.domain.model.enums.BatchStatus;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.vo.batch.BatchStats;
import com.patra.ingest.domain.model.vo.shared.IdempotentKey;
import com.patra.ingest.infra.adapter.persistence.entity.TaskRunBatchEntity;
import dev.linqibin.commons.json.JsonMapperHolder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/// 任务执行批次 JPA 实体转换器，负责领域对象与 JPA 实体转换。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TaskRunBatchJpaMapper {

  @Mapping(
      target = "idempotentKey",
      source = "idempotentKey",
      qualifiedByName = "idempotentKeyToString")
  @Mapping(target = "recordCount", source = "stats", qualifiedByName = "statsToRecordCount")
  @Mapping(target = "stats", source = "stats", qualifiedByName = "statsToJson")
  @Mapping(target = "statusCode", source = "status", qualifiedByName = "batchStatusToCode")
  @Mapping(target = "storageKey", source = "storageKey")
  TaskRunBatchEntity toEntity(TaskRunBatch source);

  default TaskRunBatch toAggregate(TaskRunBatchEntity entity) {
    return toTaskRunBatch(entity);
  }

  static TaskRunBatch toTaskRunBatch(TaskRunBatchEntity entity) {
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
        ProvenanceCode.parse(entity.getProvenanceCode()),
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

  // ========== 枚举转换方法 ==========

  default String map(ProvenanceCode code) {
    return code == null ? null : code.getCode();
  }

  default ProvenanceCode mapProvenanceCode(String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    try {
      return ProvenanceCode.parse(code);
    } catch (IllegalArgumentException e) {
      throw new InfrastructureException("数据库中存在无效的 provenance_code: " + code, e);
    }
  }

  default String map(OperationCode code) {
    return code == null ? null : code.getCode();
  }

  default OperationCode mapOperationCode(String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    try {
      return OperationCode.fromCode(code);
    } catch (IllegalArgumentException e) {
      throw new InfrastructureException("数据库中存在无效的 operation_code: " + code, e);
    }
  }
}
