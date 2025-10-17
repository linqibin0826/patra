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
import com.patra.starter.expr.compiler.transform.ToExclusiveMinus1DTransform;
import com.patra.starter.expr.compiler.transform.TransformRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P4.2.2 — Test transform application: TO_EXCLUSIVE_MINUS_1D converts the std_key "to" value by -1
 * day before mapping to provider param (docs/expr/08-testing.md §8.2, docs/expr/03-compiler-bridge-
 * internals.md §3.3.2).
 */
@DisplayName("Compiler Transform Tests")
class CompilerTransformTest {

  @Test
  @DisplayName("TO_EXCLUSIVE_MINUS_1D should adjust 'to' date when mapping to provider param")
  void toExclusiveMinus1D_appliedOnToParam() {
    // Snapshot with mapping for std_key 'to' -> provider 'maxdate' with transform
    ProvenanceSnapshot snapshot =
        new ProvenanceSnapshot(
            new ProvenanceSnapshot.Identity(1L, "PUBMED", "PubMed"),
            ProvenanceSnapshot.Scope.sourceScope(),
            new ProvenanceSnapshot.Operation("SEARCH", "UTC"),
            1L,
            Instant.parse("2024-05-01T00:00:00Z"),
            Map.of(),
            Map.of(
                "entrez_date",
                new ProvenanceSnapshot.Capability(
                    Set.of("RANGE"),
                    Set.of(),
                    true,
                    Set.of(),
                    false,
                    true,
                    0,
                    100,
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
            Map.of(
                // apply TO_EXCLUSIVE_MINUS_1D to 'to' value when mapping to provider 'maxdate'
                "to",
                new ProvenanceSnapshot.ApiParameter("to", "maxdate", "TO_EXCLUSIVE_MINUS_1D", null),
                // Include a from mapping without transform for completeness (not asserted here)
                "from",
                new ProvenanceSnapshot.ApiParameter("from", "mindate", null, null)),
            List.of());

    // Transform registry with the real TO_EXCLUSIVE_MINUS_1D transform
    TransformRegistry transformRegistry =
        new DefaultTransformRegistry(List.of(new ToExclusiveMinus1DTransform()));

    // Renderer emits std_key params including 'to' value
    ExprRenderer renderer =
        (expression, s, traceEnabled) ->
            new ExprRenderer.RenderOutcome("", Map.of("to", "2024-12-31"), List.of(), null);

    DefaultExprCompiler compiler =
        new DefaultExprCompiler(
            new StubSnapshotLoader(snapshot),
            (expr, s, strict) -> List.of(),
            new IdentityNormalizer(),
            renderer,
            transformRegistry,
            new CompilerProperties(),
            new ExprModeProperties(),
            ExprMetrics.noop());

    CompileRequest request =
        CompileRequestBuilder.of(
                Exprs.term("title", "ignored", TextMatch.PHRASE), ProvenanceCode.PUBMED)
            .build();

    CompileResult result = compiler.compile(request);

    // Expect provider param 'maxdate' adjusted to 2024-12-30
    assertThat(result.params()).containsEntry("maxdate", "2024-12-30");
    // Ensure no compilation errors were produced
    assertThat(result.report().errors()).isEmpty();
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
