package com.patra.starter.expr.compiler.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.expr.TextMatch;
import com.patra.starter.expr.compiler.metrics.ExprMetrics;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot.Cardinality;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot.DataType;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot.EmitType;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot.FieldDefinition;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot.NegationQualifier;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot.RenderRule;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot.ValueType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies NOT rendering selects the negated render rule (docs/expr/08-testing.md §8.2, task
 * P4.1.4).
 */
@DisplayName("Renderer NOT Tests")
class RendererNotTest {

  private ProvenanceSnapshot snapshot;
  private DefaultExprRenderer renderer;

  @BeforeEach
  void setUp() {
    FieldDefinition titleField =
        new FieldDefinition("title", "Title", "", DataType.TEXT, Cardinality.SINGLE, true, false);

    RenderRule positiveRule =
        new RenderRule(
            "title",
            "SOURCE",
            null,
            com.patra.expr.Atom.Operator.TERM,
            "ANY",
            NegationQualifier.ANY,
            ValueType.STRING,
            EmitType.QUERY,
            "{{v}}[TIAB]",
            null,
            null,
            false,
            Map.of(),
            null,
            Instant.parse("2000-01-01T00:00:00Z"),
            null,
            100);

    RenderRule negatedRule =
        new RenderRule(
            "title",
            "SOURCE",
            null,
            com.patra.expr.Atom.Operator.TERM,
            "ANY",
            NegationQualifier.TRUE,
            ValueType.STRING,
            EmitType.QUERY,
            "NOT({{v}}[TIAB])",
            null,
            null,
            false,
            Map.of(),
            null,
            Instant.parse("2000-01-01T00:00:00Z"),
            null,
            200);

    snapshot =
        new ProvenanceSnapshot(
            new ProvenanceSnapshot.Identity(1L, "PUBMED", "PubMed"),
            ProvenanceSnapshot.Scope.sourceScope(),
            new ProvenanceSnapshot.Operation("SEARCH", "UTC"),
            1L,
            Instant.now(),
            Map.of("title", titleField),
            Map.of(),
            Map.of(),
            List.of(positiveRule, negatedRule));

    renderer = new DefaultExprRenderer(null, ExprMetrics.noop());
  }

  @Test
  @DisplayName("NOT(term) should use the negated rule template")
  void testNotSelectsNegatedRule() {
    Expr expr = Exprs.not(Exprs.term("title", "cancer", TextMatch.ANY));

    ExprRenderer.RenderOutcome outcome = renderer.render(expr, snapshot, false);

    assertThat(outcome.query()).isEqualTo("NOT(cancer[TIAB])");
    assertThat(outcome.params()).isEmpty();
    assertThat(outcome.warnings()).isEmpty();
  }
}
