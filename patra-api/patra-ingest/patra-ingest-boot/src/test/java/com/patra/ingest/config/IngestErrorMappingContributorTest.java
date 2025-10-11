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
 * Unit tests for {@link IngestErrorMappingContributor}.
 */
class IngestErrorMappingContributorTest {

    private final IngestErrorMappingContributor contributor = new IngestErrorMappingContributor();

    @Test
    @DisplayName("Maps missing Registry configuration to ING-1201")
    void shouldMapConfigurationNotFound() {
        RemoteCallException remote = new RemoteCallException("REG-0404", 404, "not found",
                "ProvenanceClient#getConfiguration", "trace-1", Map.of());
        IngestConfigurationException exception = new IngestConfigurationException(
                "PUBMED", "HARVEST", "config not found", remote);

        assertThat(contributor.mapException(exception))
                .contains(IngestErrorCode.ING_1201);
    }

    @Test
    @DisplayName("Maps Registry server-side failure to ING-1203")
    void shouldMapConfigurationServerError() {
        RemoteCallException remote = new RemoteCallException("REG-0500", 502, "bad gateway",
                "ProvenanceClient#getConfiguration", "trace-2", Map.of());
        IngestConfigurationException exception = new IngestConfigurationException(
                "PUBMED", "HARVEST", "registry unavailable", remote);

        assertThat(contributor.mapException(exception))
                .contains(IngestErrorCode.ING_1203);
    }

    @Test
    @DisplayName("Maps scheduler parameter parsing failure to ING-1401")
    void shouldMapSchedulerArgumentErrors() {
        IngestScheduleParameterException exception = new IngestScheduleParameterException("Failed to parse relay param: boom");

        assertThat(contributor.mapException(exception))
                .contains(IngestErrorCode.ING_1401);
    }

    @Test
    @DisplayName("Maps outbox state update failure to ING-1302")
    void shouldMapOutboxStateErrors() {
        OutboxPersistenceException exception = new OutboxPersistenceException(
                OutboxPersistenceException.Stage.MARK_PUBLISHED,
                "Failed to update outbox status, id=1");

        assertThat(contributor.mapException(exception))
                .contains(IngestErrorCode.ING_1302);
    }

    @Test
    @DisplayName("Maps checkpoint parsing failure to ING-1501")
    void shouldMapCheckpointParseError() {
        TaskCheckpointException exception = new TaskCheckpointException(
                TaskCheckpointException.Type.PARSE,
                "Checkpoint JSON parsing failed", new RuntimeException("boom"));

        assertThat(contributor.mapException(exception))
                .contains(IngestErrorCode.ING_1501);
    }

    @Test
    @DisplayName("Maps outbox relay execution exception to ING-1402")
    void shouldMapRelayExecutionError() {
        OutboxRelayExecutionException exception = new OutboxRelayExecutionException("Relay failed", new RuntimeException("boom"));

        assertThat(contributor.mapException(exception))
                .contains(IngestErrorCode.ING_1402);
    }

    @Test
    @DisplayName("Maps plan validation exception to ING-1403")
    void shouldMapPlanValidationError() {
        PlanValidationException exception = new PlanValidationException("window invalid", PlanValidationException.Reason.WINDOW_INVALID);

        assertThat(contributor.mapException(exception))
                .contains(IngestErrorCode.ING_1403);
    }

    @Test
    @DisplayName("Maps plan assembly exception to ING-1601")
    void shouldMapPlanAssemblyError() {
        PlanAssemblyException exception = new PlanAssemblyException("assembly failed", PlanAssemblyException.Reason.EMPTY_RESULT);

        assertThat(contributor.mapException(exception))
                .contains(IngestErrorCode.ING_1601);
    }

    @Test
    @DisplayName("Maps plan persistence exception to ING-1503")
    void shouldMapPlanPersistenceError() {
        PlanPersistenceException exception = new PlanPersistenceException(PlanPersistenceException.Stage.PLAN,
                "persist plan failed");

        assertThat(contributor.mapException(exception))
                .contains(IngestErrorCode.ING_1503);
    }
}
