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

/** P4.2.9 — STRICT mode: unsupported NOT should error (no downgrade). */
@DisplayName("Compiler STRICT NOT Capability Tests")
class CompilerStrictNotTest {

  @Test
  @DisplayName("Unsupported NOT results in compilation error in STRICT mode")
  void unsupportedNotIsErrorInStrict() {
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

    // Capability checker produces NOT unsupported error; STRICT=true should keep as error
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
            strictMode(true),
            ExprMetrics.noop());

    CompileRequest request =
        CompileRequestBuilder.of(
                Exprs.not(Exprs.term("title", "hello", TextMatch.PHRASE)), ProvenanceCode.PUBMED)
            .build();
    CompileResult result = compiler.compile(request);

    assertThat(result.report().errors())
        .anySatisfy(e -> assertThat(e.code()).isEqualTo("E-NOT-UNSUPPORTED"));
    assertThat(result.query()).isEmpty();
    assertThat(result.params()).isEmpty();
  }

  private ExprModeProperties strictMode(boolean strict) {
    ExprModeProperties props = new ExprModeProperties();
    props.setStrict(strict);
    return props;
  }

  private record StubSnapshotLoader(ProvenanceSnapshot snapshot) implements RuleSnapshotLoader {
    @Override
    public ProvenanceSnapshot load(
        com.patra.common.enums.ProvenanceCode provenanceCode,
        String operationType,
        String endpointName) {
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
