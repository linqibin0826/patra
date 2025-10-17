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
import com.patra.starter.expr.compiler.model.Issue;
import com.patra.starter.expr.compiler.render.ExprRenderer;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import com.patra.starter.expr.compiler.snapshot.RuleSnapshotLoader;
import com.patra.starter.expr.compiler.transform.TransformRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** P4.2.10 — Non-STRICT mode: downgraded warnings and continued compilation. */
@DisplayName("Compiler Non-STRICT Behavior Tests")
class CompilerNonStrictTest {

  @Test
  @DisplayName("Missing transform becomes W-FN-OR-TRANSFORM-NOTFOUND and params still mapped")
  void missingTransformDowngradesToWarning() {
    ProvenanceSnapshot snapshot =
        new ProvenanceSnapshot(
            new ProvenanceSnapshot.Identity(1L, "PUBMED", "PubMed"),
            ProvenanceSnapshot.Scope.sourceScope(),
            new ProvenanceSnapshot.Operation("SEARCH", "UTC"),
            1L,
            Instant.parse("2024-05-01T00:00:00Z"),
            Map.of(
                "to",
                new ProvenanceSnapshot.FieldDefinition(
                    "to",
                    "To",
                    "",
                    ProvenanceSnapshot.DataType.DATE,
                    ProvenanceSnapshot.Cardinality.SINGLE,
                    true,
                    true)),
            Map.of(),
            Map.of("to", new ProvenanceSnapshot.ApiParameter("to", "maxdate", "MISSING", null)),
            List.of());

    ExprRenderer renderer =
        (expression, s, traceEnabled) ->
            new ExprRenderer.RenderOutcome("", Map.of("to", "2024-12-31"), List.of(), null);

    // Non-STRICT default
    DefaultExprCompiler compiler =
        new DefaultExprCompiler(
            new StubSnapshotLoader(snapshot),
            (expr, s, strict) -> List.of(),
            new IdentityNormalizer(),
            renderer,
            (TransformRegistry) code -> java.util.Optional.empty(),
            new CompilerProperties(),
            new ExprModeProperties(),
            ExprMetrics.noop());

    CompileRequest request =
        CompileRequestBuilder.of(
                Exprs.term("title", "ignored", TextMatch.PHRASE), ProvenanceCode.PUBMED)
            .withStrict(false)
            .build();
    CompileResult result = compiler.compile(request);

    assertThat(result.report().warnings())
        .anySatisfy(w -> assertThat(w.code()).isEqualTo("W-FN-OR-TRANSFORM-NOTFOUND"));
    // Value remains unchanged when transform missing in non-STRICT
    assertThat(result.params()).containsEntry("maxdate", "2024-12-31");
    assertThat(result.report().errors()).isEmpty();
  }

  @Test
  @DisplayName("Unsupported NOT becomes W-NOT-SKIPPED in non-STRICT mode")
  void unsupportedNotDowngradesToWarning() {
    ProvenanceSnapshot snapshot =
        new ProvenanceSnapshot(
            new ProvenanceSnapshot.Identity(1L, "PUBMED", "PubMed"),
            ProvenanceSnapshot.Scope.sourceScope(),
            new ProvenanceSnapshot.Operation("SEARCH", "UTC"),
            1L,
            Instant.parse("2024-05-01T00:00:00Z"),
            Map.of(),
            Map.of(),
            Map.of(),
            List.of());

    // Capability checker produces NOT unsupported error; non-STRICT should downgrade to
    // W-NOT-SKIPPED
    CapabilityChecker capability =
        (expr, s, strict) -> List.of(Issue.error("E-NOT-UNSUPPORTED", "NOT unsupported", Map.of()));

    DefaultExprCompiler compiler =
        new DefaultExprCompiler(
            new StubSnapshotLoader(snapshot),
            capability,
            new IdentityNormalizer(),
            (ExprRenderer)
                (e, s, t) -> new ExprRenderer.RenderOutcome("", Map.of(), List.of(), null),
            (TransformRegistry) code -> java.util.Optional.empty(),
            new CompilerProperties(),
            new ExprModeProperties(),
            ExprMetrics.noop());

    CompileRequest request =
        CompileRequestBuilder.of(
                Exprs.not(Exprs.term("title", "hello", TextMatch.PHRASE)), ProvenanceCode.PUBMED)
            .withStrict(false)
            .build();
    CompileResult result = compiler.compile(request);

    assertThat(result.report().warnings())
        .anySatisfy(w -> assertThat(w.code()).isEqualTo("W-NOT-SKIPPED"));
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
