package com.patra.starter.expr.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.expr.TextMatch;
import com.patra.starter.expr.compiler.boot.CompilerProperties;
import com.patra.starter.expr.compiler.boot.ExprModeProperties;
import com.patra.starter.expr.compiler.metrics.ExprMetrics;
import com.patra.starter.expr.compiler.model.CompileRequest;
import com.patra.starter.expr.compiler.model.CompileRequestBuilder;
import com.patra.starter.expr.compiler.model.CompileResult;
import com.patra.starter.expr.compiler.render.ExprRenderer;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import com.patra.starter.expr.compiler.snapshot.RuleSnapshotLoader;
import com.patra.starter.expr.compiler.transform.DefaultTransformRegistry;
import com.patra.starter.expr.compiler.transform.FilterJoinTransform;
import com.patra.starter.expr.compiler.transform.ListJoinTransform;
import com.patra.starter.expr.compiler.transform.TransformRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** P4.2.5 — MULTI join transforms produce single provider param value. */
@DisplayName("Compiler MULTI Join Transform Tests")
class CompilerMultiJoinTest {

  @Test
  @DisplayName("LIST_JOIN should collapse internal '||' into comma list")
  void listJoinProducesSingleValue() {
    // MULTI field with mapping using LIST_JOIN
    ProvenanceSnapshot snapshot =
        baseSnapshot(
            Map.of(
                "tags",
                new ProvenanceSnapshot.FieldDefinition(
                    "tags",
                    "Tags",
                    "",
                    ProvenanceSnapshot.DataType.KEYWORD,
                    ProvenanceSnapshot.Cardinality.MULTI,
                    true,
                    false)),
            Map.of("tags", new ProvenanceSnapshot.ApiParameter("tags", "tags", "LIST_JOIN", null)));

    TransformRegistry registry =
        new DefaultTransformRegistry(List.of(new ListJoinTransform(), new FilterJoinTransform()));
    ExprRenderer renderer =
        (expression, s, traceEnabled) ->
            new ExprRenderer.RenderOutcome("", Map.of("tags", "A||B||C"), List.of(), null);

    CompileResult result = compile(snapshot, registry, renderer);
    assertThat(result.params()).containsEntry("tags", "A,B,C");
    assertThat(result.report().errors()).isEmpty();
  }

  @Test
  @DisplayName("FILTER_JOIN should collapse internal '||' into comma-separated filters")
  void filterJoinProducesSingleValue() {
    // MULTI field with mapping using FILTER_JOIN
    ProvenanceSnapshot snapshot =
        baseSnapshot(
            Map.of(
                "filter",
                new ProvenanceSnapshot.FieldDefinition(
                    "filter",
                    "Filter",
                    "",
                    ProvenanceSnapshot.DataType.TEXT,
                    ProvenanceSnapshot.Cardinality.MULTI,
                    true,
                    false)),
            Map.of(
                "filter",
                new ProvenanceSnapshot.ApiParameter("filter", "filter", "FILTER_JOIN", null)));

    TransformRegistry registry =
        new DefaultTransformRegistry(List.of(new ListJoinTransform(), new FilterJoinTransform()));
    ExprRenderer renderer =
        (expression, s, traceEnabled) ->
            new ExprRenderer.RenderOutcome(
                "", Map.of("filter", "type:journal||year:2024"), List.of(), null);

    CompileResult result = compile(snapshot, registry, renderer);
    assertThat(result.params()).containsEntry("filter", "type:journal,year:2024");
    assertThat(result.report().errors()).isEmpty();
  }

  private CompileResult compile(
      ProvenanceSnapshot snapshot, TransformRegistry registry, ExprRenderer renderer) {
    DefaultExprCompiler compiler =
        new DefaultExprCompiler(
            new StubSnapshotLoader(snapshot),
            (expr, s, strict) -> List.of(),
            new IdentityNormalizer(),
            renderer,
            registry,
            new CompilerProperties(),
            new ExprModeProperties(),
            ExprMetrics.noop());
    CompileRequest request =
        CompileRequestBuilder.of(
                Exprs.term("title", "ignored", TextMatch.PHRASE), ProvenanceCode.PUBMED)
            .build();
    return compiler.compile(request);
  }

  private ProvenanceSnapshot baseSnapshot(
      Map<String, ProvenanceSnapshot.FieldDefinition> fields,
      Map<String, ProvenanceSnapshot.ApiParameter> apiParams) {
    return new ProvenanceSnapshot(
        new ProvenanceSnapshot.Identity(1L, "CROSSREF", "Crossref"),
        ProvenanceSnapshot.Scope.sourceScope(),
        new ProvenanceSnapshot.Operation("SEARCH", "UTC"),
        1L,
        Instant.parse("2024-05-01T00:00:00Z"),
        fields,
        Map.of(
            "filter",
            new ProvenanceSnapshot.Capability(
                Set.of("TERM", "RANGE", "IN"),
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
                ProvenanceSnapshot.RangeKind.DATE,
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
                null)),
        apiParams,
        List.of());
  }

  private record StubSnapshotLoader(ProvenanceSnapshot snapshot) implements RuleSnapshotLoader {
    @Override
    public ProvenanceSnapshot load(
        ProvenanceCode provenanceCode, String operationType, String endpointName) {
      return snapshot;
    }
  }

  private static final class IdentityNormalizer
      implements com.patra.starter.expr.compiler.normalize.ExprNormalizer {
    @Override
    public Expr normalize(Expr expression, boolean strictMode) {
      return expression;
    }
  }
}
