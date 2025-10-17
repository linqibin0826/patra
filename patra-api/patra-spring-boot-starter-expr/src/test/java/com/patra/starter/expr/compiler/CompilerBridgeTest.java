package com.patra.starter.expr.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.expr.TextMatch;
import com.patra.starter.expr.compiler.boot.CompilerProperties;
import com.patra.starter.expr.compiler.boot.ExprModeProperties;
import com.patra.starter.expr.compiler.check.CapabilityChecker;
import com.patra.starter.expr.compiler.metrics.ExprMetrics;
import com.patra.starter.expr.compiler.model.CompileRequest;
import com.patra.starter.expr.compiler.model.CompileRequestBuilder;
import com.patra.starter.expr.compiler.model.CompileResult;
import com.patra.starter.expr.compiler.model.RenderTrace;
import com.patra.starter.expr.compiler.render.ExprRenderer;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import com.patra.starter.expr.compiler.snapshot.RuleSnapshotLoader;
import com.patra.starter.expr.compiler.transform.TransformRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P4.2.1 — Test query bridging: aggregated boolean query is bridged into provider params via
 * std_key=query mapping (docs/expr/08-testing.md §8.2, docs/expr/03-compiler-bridge-internals.md
 * §3.2).
 */
@DisplayName("Compiler Bridge Tests")
class CompilerBridgeTest {

  @Test
  @DisplayName("Aggregated query should be bridged to provider param via std_key=query")
  void aggregatedQueryIsBridged() {
    // Snapshot with only std_key→provider mapping for query→term (PubMed-style)
    ProvenanceSnapshot snapshot =
        new ProvenanceSnapshot(
            new ProvenanceSnapshot.Identity(1L, "PUBMED", "PubMed"),
            ProvenanceSnapshot.Scope.sourceScope(),
            new ProvenanceSnapshot.Operation("SEARCH", "UTC"),
            1L,
            Instant.parse("2024-05-01T00:00:00Z"),
            Map.of(),
            Map.of(
                // keep capabilities minimal for this unit test
                "title",
                new ProvenanceSnapshot.Capability(
                    Set.of("TERM"),
                    Set.of(),
                    true,
                    Set.of("PHRASE"),
                    false,
                    true,
                    0,
                    100,
                    null,
                    100,
                    true,
                    ProvenanceSnapshot.RangeKind.NONE,
                    false,
                    false,
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
            Map.of("query", new ProvenanceSnapshot.ApiParameter("query", "term", null, null)),
            List.of());

    // Stub registry/renderer/capability
    TransformRegistry transformRegistry = code -> java.util.Optional.empty();
    CapabilityChecker capability = (e, s, strict) -> List.of();
    ExprRenderer renderer =
        new ExprRenderer() {
          @Override
          public RenderOutcome render(Expr expression, ProvenanceSnapshot s, boolean traceEnabled) {
            return new RenderOutcome(
                "hello[TIAB] AND world[TIAB]", // aggregated query
                Map.of(), // no std_key params to map in this case
                List.of(),
                new RenderTrace(List.of()));
          }
        };

    DefaultExprCompiler compiler =
        new DefaultExprCompiler(
            new StubSnapshotLoader(snapshot),
            capability,
            new IdentityNormalizer(),
            renderer,
            transformRegistry,
            new CompilerProperties(),
            new ExprModeProperties(),
            ExprMetrics.noop());

    CompileRequest request =
        CompileRequestBuilder.of(
                Exprs.term("title", "hello world", TextMatch.PHRASE), ProvenanceCode.PUBMED)
            .build();

    CompileResult result = compiler.compile(request);

    // Assert query is preserved and bridged to provider param `term`
    assertThat(result.query()).isEqualTo("hello[TIAB] AND world[TIAB]");
    assertThat(result.params()).containsEntry("term", "hello[TIAB] AND world[TIAB]");
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
