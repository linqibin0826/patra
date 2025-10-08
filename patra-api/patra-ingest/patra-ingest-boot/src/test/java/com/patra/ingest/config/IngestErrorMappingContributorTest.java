package com.patra.ingest.config;

import com.patra.ingest.api.error.IngestErrorCode;
import com.patra.ingest.domain.exception.IngestConfigurationException;
import com.patra.ingest.domain.exception.IngestScheduleParameterException;
import com.patra.ingest.domain.exception.OutboxPersistenceException;
import com.patra.ingest.domain.exception.OutboxRelayExecutionException;
import com.patra.ingest.domain.exception.PlanAssemblyException;
import com.patra.ingest.domain.exception.PlanPersistenceException;
import com.patra.ingest.domain.exception.PlanValidationException;
import com.patra.ingest.domain.exception.TaskCheckpointException;
import com.patra.starter.feign.error.exception.RemoteCallException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link IngestErrorMappingContributor} 单元测试。
 */
class IngestErrorMappingContributorTest {

    private final IngestErrorMappingContributor contributor = new IngestErrorMappingContributor();

    @Test
    @DisplayName("Registry 未找到时映射到 ING-1201")
    void shouldMapConfigurationNotFound() {
        RemoteCallException remote = new RemoteCallException("REG-0404", 404, "not found",
                "ProvenanceClient#getConfiguration", "trace-1", Map.of());
        IngestConfigurationException exception = new IngestConfigurationException(
                "PUBMED", "HARVEST", "config not found", remote);

        assertThat(contributor.mapException(exception))
                .contains(IngestErrorCode.ING_1201);
    }

    @Test
    @DisplayName("Registry 服务端错误降级为 ING-1203")
    void shouldMapConfigurationServerError() {
        RemoteCallException remote = new RemoteCallException("REG-0500", 502, "bad gateway",
                "ProvenanceClient#getConfiguration", "trace-2", Map.of());
        IngestConfigurationException exception = new IngestConfigurationException(
                "PUBMED", "HARVEST", "registry unavailable", remote);

        assertThat(contributor.mapException(exception))
                .contains(IngestErrorCode.ING_1203);
    }

    @Test
    @DisplayName("调度参数解析失败映射到 ING-1401")
    void shouldMapSchedulerArgumentErrors() {
        IngestScheduleParameterException exception = new IngestScheduleParameterException("Failed to parse relay param: boom");

        assertThat(contributor.mapException(exception))
                .contains(IngestErrorCode.ING_1401);
    }

    @Test
    @DisplayName("Outbox 状态写回失败映射到 ING-1302")
    void shouldMapOutboxStateErrors() {
        OutboxPersistenceException exception = new OutboxPersistenceException(OutboxPersistenceException.Stage.MARK_PUBLISHED,
                "更新 Outbox 状态失败，id=1");

        assertThat(contributor.mapException(exception))
                .contains(IngestErrorCode.ING_1302);
    }

    @Test
    @DisplayName("Checkpoint 解析失败映射到 ING-1501")
    void shouldMapCheckpointParseError() {
        TaskCheckpointException exception = new TaskCheckpointException(TaskCheckpointException.Type.PARSE,
                "Checkpoint JSON 解析失败", new RuntimeException("boom"));

        assertThat(contributor.mapException(exception))
                .contains(IngestErrorCode.ING_1501);
    }

    @Test
    @DisplayName("Outbox Relay 执行异常映射到 ING-1402")
    void shouldMapRelayExecutionError() {
        OutboxRelayExecutionException exception = new OutboxRelayExecutionException("Relay failed", new RuntimeException("boom"));

        assertThat(contributor.mapException(exception))
                .contains(IngestErrorCode.ING_1402);
    }

    @Test
    @DisplayName("计划验证异常映射到 ING-1403")
    void shouldMapPlanValidationError() {
        PlanValidationException exception = new PlanValidationException("window invalid", PlanValidationException.Reason.WINDOW_INVALID);

        assertThat(contributor.mapException(exception))
                .contains(IngestErrorCode.ING_1403);
    }

    @Test
    @DisplayName("计划装配异常映射到 ING-1601")
    void shouldMapPlanAssemblyError() {
        PlanAssemblyException exception = new PlanAssemblyException("assembly failed", PlanAssemblyException.Reason.EMPTY_RESULT);

        assertThat(contributor.mapException(exception))
                .contains(IngestErrorCode.ING_1601);
    }

    @Test
    @DisplayName("计划持久化异常映射到 ING-1503")
    void shouldMapPlanPersistenceError() {
        PlanPersistenceException exception = new PlanPersistenceException(PlanPersistenceException.Stage.PLAN,
                "persist plan failed");

        assertThat(contributor.mapException(exception))
                .contains(IngestErrorCode.ING_1503);
    }
}
