package com.patra.ingest.infra.persistence.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.json.JsonMapperHolder;
import com.patra.ingest.domain.exception.InfrastructureException;
import com.patra.ingest.domain.model.entity.TaskRunBatch;
import com.patra.ingest.domain.model.enums.BatchStatus;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.vo.batch.BatchStats;
import com.patra.ingest.domain.model.vo.shared.IdempotentKey;
import com.patra.ingest.infra.persistence.entity.TaskRunBatchDO;
import java.time.Instant;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/// TaskRunBatch（运行批次）聚合 ↔ DO 转换器。
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

  /// 为批量插入初始化审计字段默认值。
  ///
  /// 虽然 `insertBatchSomeColumn` 会触发 `MetaObjectHandler.insertFill()`，
  /// 但在 Converter 阶段预设值可以确保：
  /// 1. 批量操作中所有记录的时间戳一致性（同一批次使用相同时间）
  /// 2. 避免依赖 MetaObjectHandler 的隐式行为
  ///
  /// @param target 目标 DO 对象
  /// @param source 源领域对象
  @AfterMapping
  default void initializeDefaults(@MappingTarget TaskRunBatchDO target, TaskRunBatch source) {
    // 仅对新增记录（无 ID）设置默认值
    if (source.getId() == null) {
      Instant now = Instant.now();
      if (target.getCreatedAt() == null) {
        target.setCreatedAt(now);
      }
      if (target.getUpdatedAt() == null) {
        target.setUpdatedAt(now);
      }
      if (target.getVersion() == null) {
        target.setVersion(0L);
      }
    }
  }

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
