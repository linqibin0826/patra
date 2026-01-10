package com.patra.ingest.infra.adapter.persistence.converter.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.json.JsonMapperHolder;
import com.patra.ingest.domain.exception.InfrastructureException;
import com.patra.ingest.domain.exception.TaskCheckpointException;
import com.patra.ingest.domain.model.entity.TaskRun;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.TaskRunStatus;
import com.patra.ingest.domain.model.vo.execution.RunContext;
import com.patra.ingest.domain.model.vo.execution.RunStats;
import com.patra.ingest.domain.model.vo.execution.TaskRunCheckpoint;
import com.patra.ingest.infra.adapter.persistence.entity.TaskRunEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/// 任务执行记录 JPA 实体转换器，负责领域对象与 JPA 实体转换。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TaskRunJpaMapper {

  @Mapping(target = "statusCode", source = "status", qualifiedByName = "taskRunStatusToCode")
  @Mapping(target = "stats", source = "stats", qualifiedByName = "runStatsToJson")
  @Mapping(target = "checkpoint", source = "checkpoint", qualifiedByName = "checkpointToJson")
  @Mapping(target = "correlationId", source = "runContext.correlationId")
  // ChildJpaEntity 审计字段由 JPA 管理
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  TaskRunEntity toEntity(TaskRun source);

  default TaskRun toAggregate(TaskRunEntity entity) {
    return toTaskRun(entity);
  }

  static TaskRun toTaskRun(TaskRunEntity entity) {
    if (entity == null) {
      return null;
    }
    TaskRunStatus status = taskRunStatusFromCode(entity.getStatusCode());
    RunStats stats = deriveStats(entity.getStats());
    TaskRunCheckpoint checkpoint = checkpointFromNode(entity.getCheckpoint());
    RunContext context = new RunContext(entity.getCorrelationId());
    return TaskRun.restore(
        entity.getId(),
        entity.getTaskId(),
        entity.getAttemptNo() == null ? 0 : entity.getAttemptNo(),
        ProvenanceCode.parse(entity.getProvenanceCode()),
        entity.getOperationCode(),
        status,
        stats,
        entity.getStartedAt(),
        entity.getFinishedAt(),
        entity.getLastHeartbeat(),
        checkpoint,
        null,
        context,
        entity.getError());
  }

  @Named("taskRunStatusToCode")
  static String taskRunStatusToCode(TaskRunStatus status) {
    return status == null ? null : status.getCode();
  }

  static TaskRunStatus taskRunStatusFromCode(String code) {
    return code == null ? TaskRunStatus.PENDING : TaskRunStatus.fromCode(code);
  }

  @Named("runStatsToJson")
  static JsonNode runStatsToJson(RunStats stats) {
    if (stats == null) {
      return null;
    }
    ObjectNode node = JsonMapperHolder.getObjectMapper().createObjectNode();
    node.put("fetched", stats.fetched());
    node.put("upserted", stats.upserted());
    node.put("failed", stats.failed());
    node.put("pages", stats.pages());
    return node;
  }

  @Named("checkpointToJson")
  static JsonNode checkpointToJson(TaskRunCheckpoint checkpoint) {
    if (checkpoint == null || !checkpoint.isPresent()) {
      return null;
    }
    try {
      return JsonMapperHolder.getObjectMapper().readTree(checkpoint.raw());
    } catch (Exception ex) {
      throw new TaskCheckpointException(
          TaskCheckpointException.Type.PARSE, "Failed to parse checkpoint JSON", ex);
    }
  }

  static RunStats deriveStats(JsonNode statsNode) {
    if (statsNode == null || statsNode.isNull()) {
      return RunStats.empty();
    }
    long fetched = statsNode.has("fetched") ? statsNode.get("fetched").asLong() : 0L;
    long upserted = statsNode.has("upserted") ? statsNode.get("upserted").asLong() : 0L;
    long failed = statsNode.has("failed") ? statsNode.get("failed").asLong() : 0L;
    long pages = statsNode.has("pages") ? statsNode.get("pages").asLong() : 0L;
    return new RunStats(fetched, upserted, failed, pages);
  }

  static TaskRunCheckpoint checkpointFromNode(JsonNode node) {
    if (node == null || node.isNull()) {
      return TaskRunCheckpoint.empty();
    }
    try {
      String raw = JsonMapperHolder.getObjectMapper().writeValueAsString(node);
      return new TaskRunCheckpoint(raw);
    } catch (Exception ex) {
      throw new TaskCheckpointException(
          TaskCheckpointException.Type.SERIALIZE, "Failed to serialize checkpoint JSON", ex);
    }
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
