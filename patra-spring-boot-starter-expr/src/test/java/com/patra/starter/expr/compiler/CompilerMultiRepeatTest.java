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
import com.patra.starter.expr.compiler.transform.TransformRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P4.2.6 — MULTI repeat (when enabled) should preserve internal delimiter for adapter expansion.
 */
@DisplayName("Compiler MULTI Repeat Tests")
class CompilerMultiRepeatTest {

  @Test
  @DisplayName("When repeat enabled and no transform, MULTI param keeps '||' for adapter layer")
  void multiRepeatPreservesInternalDelimiter() {
    // MULTI field mapping without transform; repeat enabled in mode properties
    ProvenanceSnapshot snapshot =
        new ProvenanceSnapshot(
            new ProvenanceSnapshot.Identity(1L, "EPMC", "Europe PMC"),
            ProvenanceSnapshot.Scope.sourceScope(),
            new ProvenanceSnapshot.Operation("SEARCH", "UTC"),
            1L,
            Instant.parse("2024-05-01T00:00:00Z"),
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
                new ProvenanceSnapshot.Capability(
                    Set.of("TERM", "IN"),
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
                    null)),
            Map.of("filter", new ProvenanceSnapshot.ApiParameter("filter", "filter", null, null)),
            List.of());

    // Renderer emits MULTI values encoded with internal delimiter
    ExprRenderer renderer =
        (expression, s, traceEnabled) ->
            new ExprRenderer.RenderOutcome(
                "", Map.of("filter", "OPEN_ACCESS:yes||HAS_PDF:true"), List.of(), null);

    // Enable repeat in mode properties
    ExprModeProperties modeProps = new ExprModeProperties();
    modeProps.getMulti().setRepeatEnabled(true);

    DefaultExprCompiler compiler =
        new DefaultExprCompiler(
            new StubSnapshotLoader(snapshot),
            (expr, s, strict) -> List.of(),
            new IdentityNormalizer(),
            renderer,
            (TransformRegistry) code -> java.util.Optional.empty(),
            new CompilerProperties(),
            modeProps,
            ExprMetrics.noop());

    CompileRequest request =
        CompileRequestBuilder.of(
                Exprs.term("title", "ignored", TextMatch.PHRASE), ProvenanceCode.EPMC)
            .build();
    CompileResult result = compiler.compile(request);

    // Since repeat is enabled and no transform exists, value is preserved for adapter to expand
    assertThat(result.params()).containsEntry("filter", "OPEN_ACCESS:yes||HAS_PDF:true");
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
