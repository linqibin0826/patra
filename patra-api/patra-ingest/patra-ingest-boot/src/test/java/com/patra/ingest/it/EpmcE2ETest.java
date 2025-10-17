package com.patra.ingest.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.expr.Exprs;
import com.patra.expr.TextMatch;
import com.patra.ingest.domain.model.vo.ExprCompilationRequest;
import com.patra.ingest.domain.model.vo.ExprCompilationResult;
import com.patra.ingest.domain.port.ExpressionCompilerPort;
import com.patra.starter.expr.compiler.DefaultExprCompiler;
import com.patra.starter.expr.compiler.ExprCompiler;
import com.patra.starter.expr.compiler.boot.CompilerProperties;
import com.patra.starter.expr.compiler.boot.ExprModeProperties;
import com.patra.starter.expr.compiler.check.DefaultCapabilityChecker;
import com.patra.starter.expr.compiler.function.DefaultFunctionRegistry;
import com.patra.starter.expr.compiler.function.FunctionRegistry;
import com.patra.starter.expr.compiler.metrics.ExprMetrics;
import com.patra.starter.expr.compiler.normalize.DefaultExprNormalizer;
import com.patra.starter.expr.compiler.render.DefaultExprRenderer;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import com.patra.starter.expr.compiler.snapshot.RuleSnapshotLoader;
import com.patra.starter.expr.compiler.transform.DefaultTransformRegistry;
import com.patra.starter.expr.compiler.transform.FilterJoinTransform;
import com.patra.starter.expr.compiler.transform.ListJoinTransform;
import com.patra.starter.expr.compiler.transform.ToExclusiveMinus1DTransform;
import com.patra.starter.expr.compiler.transform.TransformRegistry;
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

@SpringBootTest(properties = "xxl.job.enabled=false")
@DisplayName("P4.4.2 — EPMC E2E compile→adapter params")
class EpmcE2ETest {

  @Autowired private ExpressionCompilerPort compiler;

  @Test
  @DisplayName("Text + date → query contains date fragment")
  void textAndDate_toEpmcQuery() {
    var expr =
        Exprs.and(
            List.of(
                Exprs.term("text", "cancer", TextMatch.ANY),
                Exprs.rangeDate(
                    "publication_date",
                    LocalDate.parse("2024-01-01"),
                    LocalDate.parse("2024-06-01"))));

    ExprCompilationResult out =
        compiler.compile(new ExprCompilationRequest("EPMC", Exprs.toJson(expr)));

    assertThat(out.isValid()).isTrue();
    JsonNode params = out.params();
    // Validate the compiled query contains both text and date fragments
    assertThat(out.query()).contains("cancer").contains("FIRST_PDATE:[2024-01-01 TO 2024-06-01]");
  }

  @TestConfiguration
  static class SnapshotTestConfig {
    @Bean
    RuleSnapshotLoader testRuleSnapshotLoader() {
      return (code, opType, endpoint) -> epmcSnapshot();
    }

    @Bean
    ExprCompiler testExprCompiler(RuleSnapshotLoader loader) {
      FunctionRegistry fnRegistry = new DefaultFunctionRegistry(List.of());
      TransformRegistry tfRegistry =
          new DefaultTransformRegistry(
              List.of(
                  new ToExclusiveMinus1DTransform(),
                  new ListJoinTransform(),
                  new FilterJoinTransform()));
      return new DefaultExprCompiler(
          loader,
          new DefaultCapabilityChecker(),
          new DefaultExprNormalizer(),
          new DefaultExprRenderer(fnRegistry, ExprMetrics.noop()),
          tfRegistry,
          new CompilerProperties(),
          new ExprModeProperties(),
          ExprMetrics.noop());
    }

    private ProvenanceSnapshot epmcSnapshot() {
      return new ProvenanceSnapshot(
          new ProvenanceSnapshot.Identity(2L, "EPMC", "Europe PMC"),
          ProvenanceSnapshot.Scope.sourceScope(),
          new ProvenanceSnapshot.Operation("SEARCH", "UTC"),
          1L,
          Instant.parse("2025-10-16T00:00:00Z"),
          // fields
          Map.of(
              "text",
              new ProvenanceSnapshot.FieldDefinition(
                  "text",
                  "Full Text",
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
          // param map (bridge query -> query)
          Map.of("query", new ProvenanceSnapshot.ApiParameter("query", "query", null, null)),
          // render rules: text to QUERY, date RANGE to QUERY fragment
          List.of(
              new ProvenanceSnapshot.RenderRule(
                  "text",
                  "SOURCE",
                  null,
                  com.patra.expr.Atom.Operator.TERM,
                  "ANY",
                  ProvenanceSnapshot.NegationQualifier.ANY,
                  ProvenanceSnapshot.ValueType.STRING,
                  ProvenanceSnapshot.EmitType.QUERY,
                  "{{v}}",
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
                  ProvenanceSnapshot.EmitType.QUERY,
                  "FIRST_PDATE:[{{from}} TO {{to}}]",
                  null,
                  null,
                  false,
                  null,
                  null,
                  null,
                  null,
                  100)));
    }
  }
}
