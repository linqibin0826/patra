package com.patra.starter.expr.compiler.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.expr.Atom;
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
 * Unit tests for OR-only boolean aggregation in DefaultExprRenderer.
 *
 * <p>Verifies that OR expressions produce parentheses-wrapped queries: (fragA OR fragB)
 *
 * <p>Reference: docs/expr/08-testing.md §8.2 - Renderer test matrix docs/expr/02-architecture.md
 * §2.7 - Boolean semantics (OR wrapping)
 */
@DisplayName("Renderer OR-only Tests")
class RendererOrTest {

  private ProvenanceSnapshot snapshot;
  private DefaultExprRenderer renderer;

  @BeforeEach
  void setUp() {
    // Field definitions
    FieldDefinition titleField =
        new FieldDefinition("title", "Title", "", DataType.TEXT, Cardinality.SINGLE, true, false);

    // Render rule: TERM ANY → {{v}}[TIAB]
    RenderRule termAnyRule =
        new RenderRule(
            "title",
            "SOURCE",
            null,
            Atom.Operator.TERM,
            "ANY",
            NegationQualifier.ANY,
            ValueType.STRING,
            EmitType.QUERY,
            "{{v}}[TIAB]",
            null,
            null,
            false,
            null,
            null,
            Instant.parse("2000-01-01T00:00:00Z"),
            null,
            100);

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
            List.of(termAnyRule));

    renderer = new DefaultExprRenderer(null, ExprMetrics.noop());
  }

  @Test
  @DisplayName("Two TERM atoms in OR should produce '(fragA OR fragB)'")
  void testTwoTermAtomsWithOr() {
    // Given: two TERM atoms in OR context
    Expr expr =
        Exprs.or(
            List.of(
                Exprs.term("title", "cancer", TextMatch.ANY),
                Exprs.term("title", "therapy", TextMatch.ANY)));

    // When: rendering
    ExprRenderer.RenderOutcome outcome = renderer.render(expr, snapshot, false);

    // Then: query should be wrapped in parentheses with " OR " joiner
    assertThat(outcome.query()).isEqualTo("(cancer[TIAB] OR therapy[TIAB])");
    assertThat(outcome.params()).isEmpty();
    assertThat(outcome.warnings()).isEmpty();
  }

  @Test
  @DisplayName("Three TERM atoms in OR should produce '(fragA OR fragB OR fragC)'")
  void testThreeTermAtomsWithOr() {
    // Given: three TERM atoms in OR context
    Expr expr =
        Exprs.or(
            List.of(
                Exprs.term("title", "cancer", TextMatch.ANY),
                Exprs.term("title", "therapy", TextMatch.ANY),
                Exprs.term("title", "treatment", TextMatch.ANY)));

    // When: rendering
    ExprRenderer.RenderOutcome outcome = renderer.render(expr, snapshot, false);

    // Then: all fragments should be joined with " OR " and wrapped in parentheses
    assertThat(outcome.query()).isEqualTo("(cancer[TIAB] OR therapy[TIAB] OR treatment[TIAB])");
    assertThat(outcome.params()).isEmpty();
    assertThat(outcome.warnings()).isEmpty();
  }

  @Test
  @DisplayName("Single OR child should still wrap in parentheses")
  void testSingleOrChild() {
    // Given: OR with single child (edge case)
    Expr expr = Exprs.or(List.of(Exprs.term("title", "cancer", TextMatch.ANY)));

    // When: rendering
    ExprRenderer.RenderOutcome outcome = renderer.render(expr, snapshot, false);

    // Then: should wrap even single fragment
    assertThat(outcome.query()).isEqualTo("(cancer[TIAB])");
    assertThat(outcome.params()).isEmpty();
    assertThat(outcome.warnings()).isEmpty();
  }
}
