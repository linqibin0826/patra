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

/** P4.2.4 — Query length enforcement should add E-QUERY-LEN-MAX and return empty output. */
@DisplayName("Compiler Query Length Guard Tests")
class CompilerQueryLengthTest {

  @Test
  @DisplayName("Rendered query exceeding max should yield E-QUERY-LEN-MAX and empty outputs")
  void queryLengthGuardTriggersError() {
    ProvenanceSnapshot snapshot =
        new ProvenanceSnapshot(
            new ProvenanceSnapshot.Identity(1L, "PUBMED", "PubMed"),
            ProvenanceSnapshot.Scope.sourceScope(),
            new ProvenanceSnapshot.Operation("SEARCH", "UTC"),
            1L,
            Instant.parse("2024-05-01T00:00:00Z"),
            Map.of(
                "title",
                new ProvenanceSnapshot.FieldDefinition(
                    "title",
                    "Title",
                    "",
                    ProvenanceSnapshot.DataType.TEXT,
                    ProvenanceSnapshot.Cardinality.SINGLE,
                    true,
                    false)),
            Map.of(
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

    TransformRegistry transformRegistry = code -> java.util.Optional.empty();
    CapabilityChecker capability = (e, s, strict) -> List.of();
    ExprRenderer renderer =
        (expression, s, traceEnabled) ->
            new ExprRenderer.RenderOutcome("a".repeat(50), Map.of(), List.of(), null);

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
                Exprs.term("title", "hello", TextMatch.PHRASE), ProvenanceCode.PUBMED)
            .withMaxQueryLength(10)
            .build();
    CompileResult result = compiler.compile(request);

    assertThat(result.query()).isEmpty();
    assertThat(result.params()).isEmpty();
    assertThat(result.report().errors())
        .anySatisfy(issue -> assertThat(issue.code()).isEqualTo("E-QUERY-LEN-MAX"));
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
