package com.patra.ingest.infra.persistence.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.json.JsonMapperHolder;
import com.patra.ingest.domain.exception.TaskCheckpointException;
import com.patra.ingest.domain.model.entity.TaskRun;
import com.patra.ingest.domain.model.enums.TaskRunStatus;
import com.patra.ingest.domain.model.vo.RunContext;
import com.patra.ingest.domain.model.vo.RunStats;
import com.patra.ingest.domain.model.vo.TaskRunCheckpoint;
import com.patra.ingest.infra.persistence.entity.TaskRunDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * TaskRun（单次任务运行）聚合 ↔ DO 转换器。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TaskRunConverter {

    @Mapping(target = "statusCode", source = "status", qualifiedByName = "taskRunStatusToCode")
    @Mapping(target = "stats", source = "stats", qualifiedByName = "runStatsToJson")
    @Mapping(target = "checkpoint", source = "checkpoint", qualifiedByName = "checkpointToJson")
    @Mapping(target = "schedulerRunId", source = "runContext.schedulerRunId")
    @Mapping(target = "correlationId", source = "runContext.correlationId")
    TaskRunDO toDO(TaskRun source);

    default TaskRun toDomain(TaskRunDO entity) {
        return toTaskRun(entity);
    }

    static TaskRun toTaskRun(TaskRunDO entity) {
        if (entity == null) {
            return null;
        }
        TaskRunStatus status = taskRunStatusFromCode(entity.getStatusCode());
        RunStats stats = deriveStats(entity.getStats());
        TaskRunCheckpoint checkpoint = checkpointFromNode(entity.getCheckpoint());
        RunContext context = new RunContext(entity.getSchedulerRunId(), entity.getCorrelationId());
        return TaskRun.restore(
                entity.getId(),
                entity.getTaskId(),
                entity.getAttemptNo() == null ? 0 : entity.getAttemptNo(),
                entity.getProvenanceCode(),
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
        return code == null ? TaskRunStatus.PLANNED : TaskRunStatus.fromCode(code);
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
            throw new TaskCheckpointException(TaskCheckpointException.Type.PARSE, "Checkpoint JSON 解析失败", ex);
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
            throw new TaskCheckpointException(TaskCheckpointException.Type.SERIALIZE, "Checkpoint JSON 序列化失败", ex);
        }
    }
}
