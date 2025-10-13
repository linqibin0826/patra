package com.patra.ingest.app.usecase.execution.execute;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.exception.BatchPlanningException;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.Batch;
import com.patra.ingest.domain.model.vo.BatchPlan;
import com.patra.ingest.domain.model.vo.ExecutionContext;
import com.patra.ingest.domain.port.PubmedSearchPort;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** PubMed-specific batch planner that creates page-based batches using ESearch count. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PubmedBatchPlanner implements BatchPlanner {

  private static final int DEFAULT_PAGE_SIZE = 500; // as agreed
  private static final int PUBMED_RETMAX_LIMIT = 10_000;

  private final PubmedSearchPort searchPort;
  private final ObjectMapper objectMapper;

  @Override
  public ProvenanceCode getProvenanceCode() {
    return ProvenanceCode.PUBMED;
  }

  @Override
  public BatchPlan plan(ExecutionContext ctx) {
    String term = ctx.compiledQuery();
    if (term == null || term.isBlank()) {
      throw new BatchPlanningException("Compiled query (term) is blank for PubMed batch planning");
    }

    ObjectNode baseParams = toObjectNode(ctx.compiledParams());

    int pageSize = resolvePageSize(baseParams, ctx.configSnapshot());
    int maxPages = resolveMaxPages(ctx.configSnapshot());

    int total = searchPort.estimateCount(term, baseParams);
    if (total <= 0) {
      log.info(
          "[INGEST][APP] pubmed planner: no results termHash={} pageSize={}",
          safeHash(term),
          pageSize);
      return BatchPlan.empty();
    }

    int pagesNeeded = (int) Math.ceil(total / (double) pageSize);
    if (pagesNeeded > maxPages) {
      log.warn(
          "[INGEST][APP] pubmed planner fail-fast: pagesNeeded={} > maxPages={} termHash={} pageSize={} total={}",
          pagesNeeded,
          maxPages,
          safeHash(term),
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

      batches.add(new Batch(i + 1, term, batchParams, null, null));
    }

    log.info(
        "[INGEST][APP] pubmed planner: planned {} batches termHash={} pageSize={} total={}",
        pages,
        safeHash(term),
        pageSize,
        total);
    return new BatchPlan(batches, pages, false);
  }

  private ObjectNode toObjectNode(JsonNode node) {
    if (node == null || node.isNull()) {
      return objectMapper.createObjectNode();
    }
    if (node.isObject()) {
      return ((ObjectNode) node).deepCopy();
    }
    return objectMapper.valueToTree(node);
  }

  private int resolvePageSize(ObjectNode params, ProvenanceConfigSnapshot snapshot) {
    Integer fromParams = intOrNull(params, "retmax");
    Integer fromCfg =
        snapshot != null && snapshot.pagination() != null
            ? snapshot.pagination().pageSizeValue()
            : null;

    int pageSize =
        fromParams != null ? fromParams : (fromCfg != null ? fromCfg : DEFAULT_PAGE_SIZE);
    if (pageSize <= 0) pageSize = DEFAULT_PAGE_SIZE;
    if (pageSize > PUBMED_RETMAX_LIMIT) {
      log.warn(
          "[INGEST][APP] pubmed planner: retmax clamped to {} from {}",
          PUBMED_RETMAX_LIMIT,
          pageSize);
      pageSize = PUBMED_RETMAX_LIMIT;
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
