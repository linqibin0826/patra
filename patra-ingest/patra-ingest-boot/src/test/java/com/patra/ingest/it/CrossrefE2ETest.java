package com.patra.ingest.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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
@DisplayName("P4.4.3 — Crossref E2E compile→adapter params")
class CrossrefE2ETest {

  @Autowired private ExpressionCompilerPort compiler;

  @Test
  @DisplayName("Phrase + date → query + filter")
  void phraseAndDate_toCrossrefQueryAndFilter() {
    var expr =
        Exprs.and(
            List.of(
                Exprs.term("text", "machine learning", TextMatch.PHRASE),
                Exprs.rangeDate(
                    "publication_date",
                    LocalDate.parse("2022-01-01"),
                    LocalDate.parse("2022-12-31"))));

    ExprCompilationResult out =
        compiler.compile(new ExprCompilationRequest("CROSSREF", Exprs.toJson(expr)));

    assertThat(out.isValid()).isTrue();
    JsonNode params = out.params();
    assertThat(params.get("query").asText()).isEqualTo("\"machine learning\"");
    assertThat(params.get("filter").asText())
        .isEqualTo("from-pub-date:2022-01-01,until-pub-date:2022-12-31");
  }

  @TestConfiguration
  static class SnapshotTestConfig {
    @Bean
    RuleSnapshotLoader testRuleSnapshotLoader() {
      return (code, opType, endpoint) -> crossrefSnapshot();
    }

    private ProvenanceSnapshot crossrefSnapshot() {
      return new ProvenanceSnapshot(
          new ProvenanceSnapshot.Identity(3L, "CROSSREF", "Crossref"),
          ProvenanceSnapshot.Scope.sourceScope(),
          new ProvenanceSnapshot.Operation("SEARCH", "UTC"),
          1L,
          Instant.parse("2025-10-16T00:00:00Z"),
          // fields
          Map.of(
              "text",
              new ProvenanceSnapshot.FieldDefinition(
                  "text",
                  "Text",
                  "",
                  ProvenanceSnapshot.DataType.TEXT,
                  ProvenanceSnapshot.Cardinality.SINGLE,
                  true,
                  false),
              "publication_date",
              new ProvenanceSnapshot.FieldDefinition(
                  "publication_date",
                  "Publication Date",
                  "",
                  ProvenanceSnapshot.DataType.DATE,
                  ProvenanceSnapshot.Cardinality.SINGLE,
                  true,
                  true)),
          // capabilities
          Map.of(
              "text",
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
              "publication_date",
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
          // param mapping (query->query, filter->filter, rows/offset available)
          Map.of(
              "query", new ProvenanceSnapshot.ApiParameter("query", "query", null, null),
              "filter",
                  new ProvenanceSnapshot.ApiParameter("filter", "filter", "FILTER_JOIN", null),
              "limit", new ProvenanceSnapshot.ApiParameter("limit", "rows", null, null),
              "offset", new ProvenanceSnapshot.ApiParameter("offset", "offset", null, null)),
          // rules: text TERM→QUERY, date RANGE→PARAMS filter composed
          List.of(
              new ProvenanceSnapshot.RenderRule(
                  "text",
                  "SOURCE",
                  null,
                  com.patra.expr.Atom.Operator.TERM,
                  "PHRASE",
                  ProvenanceSnapshot.NegationQualifier.ANY,
                  ProvenanceSnapshot.ValueType.STRING,
                  ProvenanceSnapshot.EmitType.QUERY,
                  "\"{{v}}\"",
                  null,
                  null,
                  false,
                  null,
                  null,
                  null,
                  null,
                  100),
              new ProvenanceSnapshot.RenderRule(
                  "publication_date",
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
                  Map.of(
                      "filter",
                      "from-pub-date:{{from}}||until-pub-date:{{to}}" // MULTI internal format
                      ),
                  null,
                  null,
                  null,
                  100)));
    }
  }
}
