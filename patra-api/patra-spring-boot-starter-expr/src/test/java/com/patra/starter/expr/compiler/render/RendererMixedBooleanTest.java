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
 * Unit tests for mixed AND/OR boolean aggregation in DefaultExprRenderer.
 *
 * <p>Verifies correct parentheses placement for mixed boolean operators: - OR nested in AND: A AND
 * (B OR C) - AND nested in OR: (A AND B) OR (C AND D) - Complex nesting with precedence
 * preservation
 *
 * <p>Reference: docs/expr/08-testing.md §8.2 - Mixed AND/OR tests docs/expr/02-architecture.md §2.7
 * - OR wraps in () when nested in AND
 */
@DisplayName("Renderer Mixed AND/OR Tests")
class RendererMixedBooleanTest {

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
  @DisplayName("AND(A, OR(B, C)) should produce 'A AND (B OR C)'")
  void testAndWithNestedOr() {
    // Given: A AND (B OR C)
    Expr expr =
        Exprs.and(
            List.of(
                Exprs.term("title", "cancer", TextMatch.ANY),
                Exprs.or(
                    List.of(
                        Exprs.term("title", "therapy", TextMatch.ANY),
                        Exprs.term("title", "treatment", TextMatch.ANY)))));

    // When: rendering
    ExprRenderer.RenderOutcome outcome = renderer.render(expr, snapshot, false);

    // Then: OR clause should be wrapped in parentheses
    assertThat(outcome.query()).isEqualTo("cancer[TIAB] AND (therapy[TIAB] OR treatment[TIAB])");
    assertThat(outcome.params()).isEmpty();
    assertThat(outcome.warnings()).isEmpty();
  }

  @Test
  @DisplayName("AND(OR(A, B), C) should produce '(A OR B) AND C'")
  void testAndWithLeadingOr() {
    // Given: (A OR B) AND C
    Expr expr =
        Exprs.and(
            List.of(
                Exprs.or(
                    List.of(
                        Exprs.term("title", "cancer", TextMatch.ANY),
                        Exprs.term("title", "tumor", TextMatch.ANY))),
                Exprs.term("title", "therapy", TextMatch.ANY)));

    // When: rendering
    ExprRenderer.RenderOutcome outcome = renderer.render(expr, snapshot, false);

    // Then: OR clause should be wrapped in parentheses
    assertThat(outcome.query()).isEqualTo("(cancer[TIAB] OR tumor[TIAB]) AND therapy[TIAB]");
    assertThat(outcome.params()).isEmpty();
    assertThat(outcome.warnings()).isEmpty();
  }

  @Test
  @DisplayName("AND(OR(A, B), OR(C, D)) should produce '(A OR B) AND (C OR D)'")
  void testAndWithMultipleOrClauses() {
    // Given: (A OR B) AND (C OR D)
    Expr expr =
        Exprs.and(
            List.of(
                Exprs.or(
                    List.of(
                        Exprs.term("title", "cancer", TextMatch.ANY),
                        Exprs.term("title", "tumor", TextMatch.ANY))),
                Exprs.or(
                    List.of(
                        Exprs.term("title", "therapy", TextMatch.ANY),
                        Exprs.term("title", "treatment", TextMatch.ANY)))));

    // When: rendering
    ExprRenderer.RenderOutcome outcome = renderer.render(expr, snapshot, false);

    // Then: both OR clauses should be wrapped
    assertThat(outcome.query())
        .isEqualTo("(cancer[TIAB] OR tumor[TIAB]) AND (therapy[TIAB] OR treatment[TIAB])");
    assertThat(outcome.params()).isEmpty();
    assertThat(outcome.warnings()).isEmpty();
  }

  @Test
  @DisplayName("Complex nesting: AND(A, OR(B, AND(C, D))) should preserve structure")
  void testComplexNesting() {
    // Given: A AND (B OR (C AND D))
    Expr expr =
        Exprs.and(
            List.of(
                Exprs.term("title", "cancer", TextMatch.ANY),
                Exprs.or(
                    List.of(
                        Exprs.term("title", "therapy", TextMatch.ANY),
                        Exprs.and(
                            List.of(
                                Exprs.term("title", "clinical", TextMatch.ANY),
                                Exprs.term("title", "trial", TextMatch.ANY)))))));

    // When: rendering
    ExprRenderer.RenderOutcome outcome = renderer.render(expr, snapshot, false);

    // Then: OR wraps, inner AND does not need wrapping
    assertThat(outcome.query())
        .isEqualTo("cancer[TIAB] AND (therapy[TIAB] OR (clinical[TIAB] AND trial[TIAB]))");
    assertThat(outcome.params()).isEmpty();
    assertThat(outcome.warnings()).isEmpty();
  }
}
