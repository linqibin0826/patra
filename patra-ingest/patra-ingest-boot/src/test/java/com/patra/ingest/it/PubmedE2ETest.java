package com.patra.ingest.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.expr.Exprs;
import com.patra.expr.TextMatch;
import com.patra.ingest.domain.model.vo.ExprCompilationRequest;
import com.patra.ingest.domain.model.vo.ExprCompilationResult;
import com.patra.ingest.domain.port.ExpressionCompilerPort;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import com.patra.starter.expr.compiler.snapshot.RuleSnapshotLoader;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootTest
@DisplayName("P4.4.1 — PubMed E2E compile→adapter params")
class PubmedE2ETest {

  @Autowired private ExpressionCompilerPort compiler;
  @Autowired private ObjectMapper objectMapper;

  @Test
  @DisplayName("Phrase + date range → term + mindate/maxdate/datetype")
  void phraseAndDateRange_toPubmedParams() {
    var expr =
        Exprs.and(
            List.of(
                Exprs.term("tiab", "heart failure", TextMatch.PHRASE),
                Exprs.rangeDate(
                    "entrez_date", LocalDate.parse("2023-01-01"), LocalDate.parse("2023-12-31"))));

    String json = Exprs.toJson(expr);
    ExprCompilationResult out = compiler.compile(new ExprCompilationRequest("PUBMED", json));

    assertThat(out.isValid()).isTrue();
    JsonNode params = out.params();
    assertThat(params.get("term").asText()).contains("\"heart failure\"[TIAB]");
    assertThat(params.get("mindate").asText()).isEqualTo("2023-01-01");
    // exclusive minus 1d transform → 2023-12-30
    assertThat(params.get("maxdate").asText()).isEqualTo("2023-12-30");
    assertThat(params.get("datetype").asText()).isEqualTo("pdat");
  }

  @TestConfiguration
  static class SnapshotTestConfig {
    @Bean
    RuleSnapshotLoader testRuleSnapshotLoader() {
      return (code, opType, endpoint) -> pubmedSnapshot();
    }

    private ProvenanceSnapshot pubmedSnapshot() {
      return new ProvenanceSnapshot(
          new ProvenanceSnapshot.Identity(1L, "PUBMED", "PubMed"),
          ProvenanceSnapshot.Scope.sourceScope(),
          new ProvenanceSnapshot.Operation("SEARCH", "UTC"),
          1L,
          Instant.parse("2025-10-16T00:00:00Z"),
          // fields
          Map.of(
              "tiab",
              new ProvenanceSnapshot.FieldDefinition(
                  "tiab",
                  "Title/Abstract",
                  "",
                  ProvenanceSnapshot.DataType.TEXT,
                  ProvenanceSnapshot.Cardinality.SINGLE,
                  true,
                  false),
              "entrez_date",
              new ProvenanceSnapshot.FieldDefinition(
                  "entrez_date",
                  "Entrez Date",
                  "",
                  ProvenanceSnapshot.DataType.DATE,
                  ProvenanceSnapshot.Cardinality.SINGLE,
                  true,
                  true)),
          // capabilities
          Map.of(
              "tiab",
              new ProvenanceSnapshot.Capability(
                  Set.of("TERM"),
                  Set.of(),
                  true,
                  Set.of("ANY", "PHRASE"),
                  false,
                  true,
                  0,
                  200,
                  null,
                  100,
                  true,
                  ProvenanceSnapshot.RangeKind.NONE,
                  true,
                  true,
                  false,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  true,
                  Set.of(),
                  null),
              "entrez_date",
              new ProvenanceSnapshot.Capability(
                  Set.of("RANGE"),
                  Set.of(),
                  true,
                  Set.of(),
                  false,
                  true,
                  0,
                  0,
                  null,
                  0,
                  true,
                  ProvenanceSnapshot.RangeKind.DATE,
                  true,
                  true,
                  false,
                  LocalDate.parse("1900-01-01"),
                  LocalDate.parse("2100-12-31"),
                  null,
                  null,
                  null,
                  null,
                  true,
                  Set.of(),
                  null)),
          // api param map
          Map.of(
              "query", new ProvenanceSnapshot.ApiParameter("query", "term", null, null),
              "from", new ProvenanceSnapshot.ApiParameter("from", "mindate", null, null),
              "to",
                  new ProvenanceSnapshot.ApiParameter(
                      "to", "maxdate", "TO_EXCLUSIVE_MINUS_1D", null),
              "datetype", new ProvenanceSnapshot.ApiParameter("datetype", "datetype", null, null)),
          // render rules
          List.of(
              // TIAB PHRASE → QUERY
              new ProvenanceSnapshot.RenderRule(
                  "tiab",
                  "SOURCE",
                  null,
                  com.patra.expr.Atom.Operator.TERM,
                  "PHRASE",
                  ProvenanceSnapshot.NegationQualifier.ANY,
                  ProvenanceSnapshot.ValueType.STRING,
                  ProvenanceSnapshot.EmitType.QUERY,
                  "\"{{v}}\"[TIAB]",
                  null,
                  null,
                  false,
                  null,
                  null,
                  null,
                  null,
                  100),
              // Date RANGE → PARAMS with PUBMED_DATETYPE
              new ProvenanceSnapshot.RenderRule(
                  "entrez_date",
                  "SOURCE",
                  null,
                  com.patra.expr.Atom.Operator.RANGE,
                  null,
                  ProvenanceSnapshot.NegationQualifier.ANY,
                  ProvenanceSnapshot.ValueType.DATE,
                  ProvenanceSnapshot.EmitType.PARAMS,
                  null,
                  null,
                  null,
                  false,
                  Map.of("from", "{{from}}", "to", "{{to}}", "datetype", "{{datetype}}"),
                  "PUBMED_DATETYPE",
                  null,
                  null,
                  100)));
    }
  }
}
