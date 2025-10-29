package com.patra.ingest.app.usecase.execution.batch.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.exception.BatchPlanningException;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.Batch;
import com.patra.ingest.domain.model.vo.BatchPlan;
import com.patra.ingest.domain.model.vo.ExecutionContext;
import com.patra.ingest.domain.model.vo.PlanMetadata;
import com.patra.ingest.domain.port.PubmedSearchPort;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** PubMed-specific batch planner that creates page-based batches using ESearch count. */
@Component
@Slf4j
public class PubmedBatchPlanner implements BatchPlanner {

  private final PubmedSearchPort searchPort;
  private final ObjectMapper objectMapper;
  private final int pubmedRetmaxLimit;

  /**
   * Constructor with mandatory configuration validation.
   *
   * @param searchPort PubMed search port
   * @param objectMapper JSON object mapper
   * @param pubmedRetmaxLimit PubMed API retmax limit (must be configured)
   * @throws IllegalArgumentException if pubmedRetmaxLimit is not positive
   */
  public PubmedBatchPlanner(
      PubmedSearchPort searchPort,
      ObjectMapper objectMapper,
      @Value("${patra.ingest.pubmed.retmax-limit:10000}") int pubmedRetmaxLimit) {
    if (pubmedRetmaxLimit <= 0) {
      throw new IllegalArgumentException(
          "patra.ingest.pubmed.retmax-limit must be configured and positive, got: "
              + pubmedRetmaxLimit);
    }
    this.searchPort = searchPort;
    this.objectMapper = objectMapper;
    this.pubmedRetmaxLimit = pubmedRetmaxLimit;
  }

  @Override
  public ProvenanceCode getProvenanceCode() {
    return ProvenanceCode.PUBMED;
  }

  @Override
  public BatchPlan plan(ExecutionContext ctx) {
    String compiledQuery = ctx.compiledQuery();
    JsonNode compiledParams = ctx.compiledParams();

    // Allow either query or params to be non-empty
    // For DATE RANGE queries, query may be empty while params contain from/to/datetype
    boolean hasQuery = compiledQuery != null && !compiledQuery.isBlank();
    boolean hasParams = compiledParams != null && !compiledParams.isEmpty();

    if (!hasQuery && !hasParams) {
      throw new BatchPlanningException(
          "Both compiledQuery and compiledParams are empty for PubMed batch planning");
    }

    ObjectNode baseParams = toObjectNode(compiledParams);

    log.debug(
        "calling PubMed ESearch to prepare plan metadata queryHash={}", safeHash(compiledQuery));
    PlanMetadata metadata =
        searchPort.preparePlanMetadata(compiledQuery, ctx.compiledParams(), ctx.configSnapshot());
    int total = metadata.totalCount();
    if (total <= 0) {
      log.info("pubmed planner: no results termHash={}", safeHash(compiledQuery));
      return BatchPlan.empty();
    }

    int pageSize = resolvePageSize(baseParams, ctx.configSnapshot());
    int maxPages = resolveMaxPages(ctx.configSnapshot());
    log.debug(
        "batch planning params pageSize={} maxPages={} total={} queryHash={}",
        pageSize,
        maxPages,
        total,
        safeHash(compiledQuery));

    int pagesNeeded = (int) Math.ceil(total / (double) pageSize);
    if (pagesNeeded > maxPages) {
      log.warn(
          "pubmed planner fail-fast: pagesNeeded={} > maxPages={} termHash={} pageSize={} total={}",
          pagesNeeded,
          maxPages,
          safeHash(compiledQuery),
          pageSize,
          total);
      return new BatchPlan(List.of(), pagesNeeded, true);
    }

    int pages = Math.min(pagesNeeded, maxPages);
    List<Batch> batches = new ArrayList<>(pages);
    for (int i = 0; i < pages; i++) {
      int retstart = i * pageSize;
      ObjectNode batchParams = baseParams.deepCopy();
      // Override/ensure batch-related controls
      batchParams.put("retstart", retstart);
      batchParams.put("retmax", pageSize);
      batchParams.put("retmode", "json");
      // Avoid count-only setting for real fetch
      if (batchParams.has("rettype")) {
        batchParams.remove("rettype");
      }
      if (metadata.hasWebEnv()) {
        batchParams.put("WebEnv", metadata.webEnv());
        batchParams.put("query_key", metadata.queryKey());
      }

      batches.add(new Batch(i + 1, compiledQuery, batchParams, null, i + 1, pageSize));
    }

    log.info(
        "pubmed planner: planned {} batches termHash={} pageSize={} total={} webEnv={}",
        pages,
        safeHash(compiledQuery),
        pageSize,
        total,
        metadata.hasWebEnv() ? "enabled" : "disabled");
    return new BatchPlan(batches, pages, false);
  }

  private ObjectNode toObjectNode(JsonNode node) {
    if (node == null || node.isNull()) {
      return objectMapper.createObjectNode();
    }
    if (node.isObject()) {
      return node.deepCopy();
    }
    return objectMapper.valueToTree(node);
  }

  private int resolvePageSize(ObjectNode params, ProvenanceConfigSnapshot snapshot) {
    Integer fromParams = intOrNull(params, "retmax");
    Integer fromCfg =
        snapshot != null && snapshot.pagination() != null
            ? snapshot.pagination().pageSizeValue()
            : null;

    // Both sources missing - configuration is mandatory
    if (fromParams == null && fromCfg == null) {
      throw new BatchPlanningException(
          "Page size configuration is mandatory: neither 'retmax' parameter nor pagination.pageSizeValue is configured");
    }

    int pageSize = fromParams != null ? fromParams : fromCfg;

    // Validate positive
    if (pageSize <= 0) {
      throw new BatchPlanningException("Page size must be positive, got: " + pageSize);
    }

    // Clamp to PubMed API limit
    if (pageSize > pubmedRetmaxLimit) {
      log.warn("pubmed planner: retmax clamped to {} from {}", pubmedRetmaxLimit, pageSize);
      pageSize = pubmedRetmaxLimit;
    }
    return pageSize;
  }

  private int resolveMaxPages(ProvenanceConfigSnapshot snapshot) {
    Integer max =
        snapshot != null && snapshot.pagination() != null
            ? snapshot.pagination().maxPagesPerExecution()
            : null;
    // Conservative default if unset
    return (max != null && max > 0) ? max : 1;
  }

  private static Integer intOrNull(ObjectNode node, String field) {
    JsonNode v = node.get(field);
    if (v == null || v.isNull()) return null;
    if (v.isInt() || v.isLong()) return v.asInt();
    if (v.isTextual()) {
      try {
        return Integer.parseInt(v.asText());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private static String safeHash(String s) {
    if (s == null) return "null";
    int h = s.hashCode();
    return Integer.toHexString(h);
  }
}
