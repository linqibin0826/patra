package dev.linqibin.patra.ingest.infra.adapter.persistence.converter.mapper;

import dev.linqibin.patra.common.enums.ProvenanceCode;
import dev.linqibin.patra.ingest.domain.exception.InfrastructureException;
import dev.linqibin.patra.ingest.domain.exception.TaskCheckpointException;
import dev.linqibin.patra.ingest.domain.model.entity.TaskRun;
import dev.linqibin.patra.ingest.domain.model.enums.OperationCode;
import dev.linqibin.patra.ingest.domain.model.enums.TaskRunStatus;
import dev.linqibin.patra.ingest.domain.model.vo.execution.RunContext;
import dev.linqibin.patra.ingest.domain.model.vo.execution.RunStats;
import dev.linqibin.patra.ingest.domain.model.vo.execution.TaskRunCheckpoint;
import dev.linqibin.patra.ingest.infra.adapter.persistence.entity.TaskRunEntity;
import dev.linqibin.starter.jpa.json.JsonColumnCodec;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/// 任务执行记录 JPA 实体转换器，负责领域对象与 JPA 实体转换。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class TaskRunJpaMapper {

  /// JSON 列转换器，由容器注入。
  @Autowired protected JsonColumnCodec codec;

  @Mapping(target = "statusCode", source = "status", qualifiedByName = "taskRunStatusToCode")
  @Mapping(target = "stats", source = "stats", qualifiedByName = "runStatsToJson")
  @Mapping(target = "checkpoint", source = "checkpoint", qualifiedByName = "checkpointToJson")
  @Mapping(target = "correlationId", source = "runContext.correlationId")
  // ChildJpaEntity 审计字段由 JPA 管理
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  public abstract TaskRunEntity toEntity(TaskRun source);

  public TaskRun toAggregate(TaskRunEntity entity) {
    return toTaskRun(entity);
  }

  public TaskRun toTaskRun(TaskRunEntity entity) {
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
  public static String taskRunStatusToCode(TaskRunStatus status) {
    return status == null ? null : status.getCode();
  }

  public static TaskRunStatus taskRunStatusFromCode(String code) {
    return code == null ? TaskRunStatus.PENDING : TaskRunStatus.fromCode(code);
  }

  @Named("runStatsToJson")
  public static JsonNode runStatsToJson(RunStats stats) {
    if (stats == null) {
      return null;
    }
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("fetched", stats.fetched());
    node.put("upserted", stats.upserted());
    node.put("failed", stats.failed());
    node.put("pages", stats.pages());
    return node;
  }

  @Named("checkpointToJson")
  public JsonNode checkpointToJson(TaskRunCheckpoint checkpoint) {
    if (checkpoint == null || !checkpoint.isPresent()) {
      return null;
    }
    try {
      return codec.read(checkpoint.raw());
    } catch (Exception ex) {
      throw new TaskCheckpointException(
          TaskCheckpointException.Type.PARSE, "Failed to parse checkpoint JSON", ex);
    }
  }

  public static RunStats deriveStats(JsonNode statsNode) {
    if (statsNode == null || statsNode.isNull()) {
      return RunStats.empty();
    }
    long fetched = statsNode.has("fetched") ? statsNode.get("fetched").asLong() : 0L;
    long upserted = statsNode.has("upserted") ? statsNode.get("upserted").asLong() : 0L;
    long failed = statsNode.has("failed") ? statsNode.get("failed").asLong() : 0L;
    long pages = statsNode.has("pages") ? statsNode.get("pages").asLong() : 0L;
    return new RunStats(fetched, upserted, failed, pages);
  }

  public TaskRunCheckpoint checkpointFromNode(JsonNode node) {
    if (node == null || node.isNull()) {
      return TaskRunCheckpoint.empty();
    }
    try {
      String raw = codec.write(node);
      return new TaskRunCheckpoint(raw);
    } catch (Exception ex) {
      throw new TaskCheckpointException(
          TaskCheckpointException.Type.SERIALIZE, "Failed to serialize checkpoint JSON", ex);
    }
  }

  // ========== 枚举转换方法 ==========

  public String map(ProvenanceCode code) {
    return code == null ? null : code.getCode();
  }

  public ProvenanceCode mapProvenanceCode(String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    try {
      return ProvenanceCode.parse(code);
    } catch (IllegalArgumentException e) {
      throw new InfrastructureException("数据库中存在无效的 provenance_code: " + code, e);
    }
  }

  public String map(OperationCode code) {
    return code == null ? null : code.getCode();
  }

  public OperationCode mapOperationCode(String code) {
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
