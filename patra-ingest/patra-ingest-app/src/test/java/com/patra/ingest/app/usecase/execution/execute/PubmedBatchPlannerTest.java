package com.patra.ingest.app.usecase.execution.execute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.PaginationConfig;
import com.patra.ingest.domain.model.vo.BatchPlan;
import com.patra.ingest.domain.model.vo.ExecutionContext;
import com.patra.ingest.domain.model.vo.PlanMetadata;
import com.patra.ingest.domain.model.vo.WindowSpec;
import com.patra.ingest.domain.port.PubmedSearchPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class PubmedBatchPlannerTest {

  @Mock private PubmedSearchPort searchPort;

  private ObjectMapper objectMapper;
  private PubmedBatchPlanner planner;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    objectMapper = new ObjectMapper();
    planner = new PubmedBatchPlanner(searchPort, objectMapper, 10000);
  }

  @Test
  void shouldAddWebEnvToAllBatches() {
    PlanMetadata metadata = new PlanMetadata(15000, "webenv123", "querykey456");
    when(searchPort.preparePlanMetadata(any(), any(), any())).thenReturn(metadata);

    ExecutionContext ctx = createExecutionContext(5000, 10);

    BatchPlan plan = planner.plan(ctx);

    assertThat(plan.batches()).hasSize(3);
    plan.batches()
        .forEach(
            batch -> {
              ObjectNode params = (ObjectNode) batch.params();
              assertThat(params.get("WebEnv").asText()).isEqualTo("webenv123");
              assertThat(params.get("query_key").asText()).isEqualTo("querykey456");
            });
  }

  @Test
  void shouldHandleMissingWebEnv() {
    PlanMetadata metadata = new PlanMetadata(5000, null, null);
    when(searchPort.preparePlanMetadata(any(), any(), any())).thenReturn(metadata);

    ExecutionContext ctx = createExecutionContext(5000, 5);

    BatchPlan plan = planner.plan(ctx);

    assertThat(plan.batches()).hasSize(1);
    ObjectNode params = (ObjectNode) plan.batches().get(0).params();
    assertThat(params.has("WebEnv")).isFalse();
    assertThat(params.has("query_key")).isFalse();
  }

  private ExecutionContext createExecutionContext(int pageSize, int maxPages) {
    ObjectNode params = objectMapper.createObjectNode();
    params.put("retmax", pageSize);
    params.put("datetype", "pdat");

    PaginationConfig paginationConfig =
        new PaginationConfig(null, null, null, null, null, null, pageSize, maxPages, null, null);
    ProvenanceConfigSnapshot snapshot =
        new ProvenanceConfigSnapshot(null, null, paginationConfig, null, null, null, null);

    return new ExecutionContext(
        1L,
        1L,
        "PUBMED",
        "HARVEST",
        snapshot,
        "hash",
        "diabetes[Title]",
        params,
        "diabetes[Title]",
        new WindowSpec.Single());
  }
}
