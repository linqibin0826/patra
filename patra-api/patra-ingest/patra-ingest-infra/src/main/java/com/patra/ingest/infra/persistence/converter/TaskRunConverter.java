package com.patra.ingest.infra.persistence.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.json.JsonMapperHolder;
import com.patra.ingest.domain.model.entity.TaskRun;
import com.patra.ingest.domain.model.enums.TaskRunStatus;
import com.patra.ingest.domain.model.vo.ExecutionWindow;
import com.patra.ingest.domain.model.vo.RunContext;
import com.patra.ingest.domain.model.vo.RunStats;
import com.patra.ingest.domain.model.vo.TaskRunCheckpoint;
import com.patra.ingest.infra.persistence.entity.TaskRunDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TaskRunConverter {

    default TaskRunDO toDO(TaskRun source) {
        if (source == null) {
            return null;
        }
        TaskRunDO entity = new TaskRunDO();
        entity.setId(source.getId());
        entity.setTaskId(source.getTaskId());
        entity.setAttemptNo(source.getAttemptNo());
        entity.setProvenanceCode(source.getProvenanceCode());
        entity.setOperationCode(source.getOperationCode());
        entity.setStatusCode(source.getStatus() == null ? null : source.getStatus().getCode());
        entity.setStats(buildStatsNode(source.getStats()));
        entity.setError(source.getError());
        entity.setStartedAt(source.getStartedAt());
        entity.setFinishedAt(source.getFinishedAt());
        entity.setLastHeartbeat(source.getLastHeartbeat());
        entity.setWindowFrom(source.getExecutionWindow() == null ? null : source.getExecutionWindow().windowFrom());
        entity.setWindowTo(source.getExecutionWindow() == null ? null : source.getExecutionWindow().windowTo());
        entity.setCheckpoint(buildCheckpointNode(source.getCheckpoint()));
        if (source.getRunContext() != null) {
            entity.setSchedulerRunId(source.getRunContext().schedulerRunId());
            entity.setCorrelationId(source.getRunContext().correlationId());
        }
        return entity;
    }

    default TaskRun toDomain(TaskRunDO entity) {
        if (entity == null) {
            return null;
        }
        TaskRunStatus status = entity.getStatusCode() == null
                ? TaskRunStatus.PLANNED
                : TaskRunStatus.fromCode(entity.getStatusCode());
        RunStats stats = deriveStats(entity.getStats());
        TaskRunCheckpoint checkpoint = checkpointFromNode(entity.getCheckpoint());
        ExecutionWindow window = new ExecutionWindow(entity.getWindowFrom(), entity.getWindowTo());
        RunContext runContext = new RunContext(entity.getSchedulerRunId(), entity.getCorrelationId());
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
                window,
                runContext,
                entity.getError());
    }

    private ObjectNode buildStatsNode(RunStats stats) {
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

    private RunStats deriveStats(JsonNode statsNode) {
        if (statsNode == null || statsNode.isNull()) {
            return RunStats.empty();
        }
        long fetched = statsNode.has("fetched") ? statsNode.get("fetched").asLong() : 0L;
        long upserted = statsNode.has("upserted") ? statsNode.get("upserted").asLong() : 0L;
        long failed = statsNode.has("failed") ? statsNode.get("failed").asLong() : 0L;
        long pages = statsNode.has("pages") ? statsNode.get("pages").asLong() : 0L;
        return new RunStats(fetched, upserted, failed, pages);
    }

    private JsonNode buildCheckpointNode(TaskRunCheckpoint checkpoint) {
        if (checkpoint == null || !checkpoint.isPresent()) {
            return null;
        }
        try {
            return JsonMapperHolder.getObjectMapper().readTree(checkpoint.raw());
        } catch (Exception e) {
            throw new IllegalArgumentException("Checkpoint JSON 解析失败", e);
        }
    }

    private TaskRunCheckpoint checkpointFromNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return TaskRunCheckpoint.empty();
        }
        try {
            String raw = JsonMapperHolder.getObjectMapper().writeValueAsString(node);
            return new TaskRunCheckpoint(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException("Checkpoint JSON 序列化失败", e);
        }
    }
}
