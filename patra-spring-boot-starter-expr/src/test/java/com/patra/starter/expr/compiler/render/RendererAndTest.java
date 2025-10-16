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
 * Unit tests for AND-only boolean aggregation in DefaultExprRenderer.
 *
 * <p>Verifies that multiple TERM atoms in an AND context produce correctly joined query fragments
 * with " AND " separator (no parentheses needed).
 *
 * <p>Reference: docs/expr/08-testing.md §8.2 - Renderer test matrix
 */
@DisplayName("Renderer AND-only Tests")
class RendererAndTest {

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

    // Render rule: TERM PHRASE → "{{v}}"[TIAB]
    RenderRule termPhraseRule =
        new RenderRule(
            "title",
            "SOURCE",
            null,
            Atom.Operator.TERM,
            "PHRASE",
            NegationQualifier.ANY,
            ValueType.STRING,
            EmitType.QUERY,
            "\"{{v}}\"[TIAB]",
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
            List.of(termAnyRule, termPhraseRule));

    renderer = new DefaultExprRenderer(null, ExprMetrics.noop());
  }

  @Test
  @DisplayName("Two TERM atoms should produce 'fragA AND fragB'")
  void testTwoTermAtomsWithAnd() {
    // Given: two TERM atoms in AND context
    Expr expr =
        Exprs.and(
            List.of(
                Exprs.term("title", "cancer", TextMatch.ANY),
                Exprs.term("title", "therapy", TextMatch.ANY)));

    // When: rendering
    ExprRenderer.RenderOutcome outcome = renderer.render(expr, snapshot, false);

    // Then: query should be joined with " AND "
    assertThat(outcome.query()).isEqualTo("cancer[TIAB] AND therapy[TIAB]");
    assertThat(outcome.params()).isEmpty();
    assertThat(outcome.warnings()).isEmpty();
  }

  @Test
  @DisplayName("Three TERM atoms should produce 'fragA AND fragB AND fragC'")
  void testThreeTermAtomsWithAnd() {
    // Given: three TERM atoms in AND context
    Expr expr =
        Exprs.and(
            List.of(
                Exprs.term("title", "cancer", TextMatch.ANY),
                Exprs.term("title", "therapy", TextMatch.ANY),
                Exprs.term("title", "clinical", TextMatch.ANY)));

    // When: rendering
    ExprRenderer.RenderOutcome outcome = renderer.render(expr, snapshot, false);

    // Then: query should be joined with " AND " separator
    assertThat(outcome.query()).isEqualTo("cancer[TIAB] AND therapy[TIAB] AND clinical[TIAB]");
    assertThat(outcome.params()).isEmpty();
    assertThat(outcome.warnings()).isEmpty();
  }

  @Test
  @DisplayName("PHRASE match should use quoted template")
  void testPhraseMatchWithAnd() {
    // Given: two PHRASE TERM atoms
    Expr expr =
        Exprs.and(
            List.of(
                Exprs.term("title", "heart failure", TextMatch.PHRASE),
                Exprs.term("title", "randomized trial", TextMatch.PHRASE)));

    // When: rendering
    ExprRenderer.RenderOutcome outcome = renderer.render(expr, snapshot, false);

    // Then: each phrase should be quoted
    assertThat(outcome.query()).isEqualTo("\"heart failure\"[TIAB] AND \"randomized trial\"[TIAB]");
    assertThat(outcome.params()).isEmpty();
    assertThat(outcome.warnings()).isEmpty();
  }

  @Test
  @DisplayName("Mixed ANY and PHRASE matches should work correctly")
  void testMixedMatchTypes() {
    // Given: mix of ANY and PHRASE matches
    Expr expr =
        Exprs.and(
            List.of(
                Exprs.term("title", "cancer", TextMatch.ANY),
                Exprs.term("title", "heart failure", TextMatch.PHRASE)));

    // When: rendering
    ExprRenderer.RenderOutcome outcome = renderer.render(expr, snapshot, false);

    // Then: each should use appropriate template
    assertThat(outcome.query()).isEqualTo("cancer[TIAB] AND \"heart failure\"[TIAB]");
    assertThat(outcome.params()).isEmpty();
    assertThat(outcome.warnings()).isEmpty();
  }
}
