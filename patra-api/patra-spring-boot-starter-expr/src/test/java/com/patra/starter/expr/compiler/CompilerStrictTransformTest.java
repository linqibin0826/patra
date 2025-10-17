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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** P4.2.8 — STRICT mode: missing transform_code escalates to E-TRANSFORM-NOTFOUND. */
@DisplayName("Compiler STRICT Transform Tests")
class CompilerStrictTransformTest {

  @Test
  @DisplayName("Missing transform should produce E-TRANSFORM-NOTFOUND in STRICT mode")
  void missingTransformIsErrorInStrictMode() {
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

    ExprModeProperties modeProps = new ExprModeProperties();
    modeProps.setStrict(true);

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
                Exprs.term("title", "ignored", TextMatch.PHRASE), ProvenanceCode.PUBMED)
            .build();
    CompileResult result = compiler.compile(request);

    assertThat(result.report().errors())
        .anySatisfy(e -> assertThat(e.code()).isEqualTo("E-TRANSFORM-NOTFOUND"));
    assertThat(result.query()).isEmpty();
    assertThat(result.params()).isEmpty();
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
