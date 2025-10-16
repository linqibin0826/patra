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
 * Validates MULTI std_key accumulation joins values deterministically (docs/expr/08-testing.md
 * §8.2, task P4.1.8).
 */
@DisplayName("Renderer MULTI std_key Tests")
class RendererMultiCollectionTest {

  private ProvenanceSnapshot snapshot;
  private DefaultExprRenderer renderer;

  @BeforeEach
  void setUp() {
    FieldDefinition keywordField =
        new FieldDefinition(
            "keyword", "Keyword", "", DataType.TEXT, Cardinality.MULTI, true, false);

    RenderRule keywordQueryAnyRule =
        new RenderRule(
            "keyword",
            "SOURCE",
            null,
            Atom.Operator.TERM,
            "ANY",
            NegationQualifier.ANY,
            ValueType.STRING,
            EmitType.QUERY,
            "{{v}}",
            null,
            null,
            false,
            Map.of(),
            null,
            Instant.parse("2000-01-01T00:00:00Z"),
            null,
            50);

    RenderRule keywordQueryPhraseRule =
        new RenderRule(
            "keyword",
            "SOURCE",
            null,
            Atom.Operator.TERM,
            "PHRASE",
            NegationQualifier.ANY,
            ValueType.STRING,
            EmitType.QUERY,
            "\"{{v}}\"",
            null,
            null,
            false,
            Map.of(),
            null,
            Instant.parse("2000-01-01T00:00:00Z"),
            null,
            60);

    RenderRule keywordParamAnyRule =
        new RenderRule(
            "keyword",
            "SOURCE",
            null,
            Atom.Operator.TERM,
            "ANY",
            NegationQualifier.ANY,
            ValueType.STRING,
            EmitType.PARAMS,
            null,
            null,
            null,
            false,
            Map.of("filter", "{{v}}"),
            null,
            Instant.parse("2000-01-01T00:00:00Z"),
            null,
            300);

    RenderRule keywordParamPhraseRule =
        new RenderRule(
            "keyword",
            "SOURCE",
            null,
            Atom.Operator.TERM,
            "PHRASE",
            NegationQualifier.ANY,
            ValueType.STRING,
            EmitType.PARAMS,
            null,
            null,
            null,
            false,
            Map.of("filter", "{{quoted}}"),
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
            Map.of("keyword", keywordField),
            Map.of(),
            Map.of(),
            List.of(
                keywordQueryAnyRule,
                keywordQueryPhraseRule,
                keywordParamAnyRule,
                keywordParamPhraseRule));

    renderer = new DefaultExprRenderer(null, ExprMetrics.noop());
  }

  @Test
  @DisplayName("MULTI std_key should accumulate values in priority order")
  void testMultiStdKeyAccumulation() {
    Expr expr =
        Exprs.and(
            List.of(
                Exprs.term("keyword", "ml", TextMatch.ANY),
                Exprs.term("keyword", "deep learning", TextMatch.PHRASE)));

    ExprRenderer.RenderOutcome outcome = renderer.render(expr, snapshot, false);

    assertThat(outcome.params()).containsEntry("filter", "ml||\"deep learning\"");
    assertThat(outcome.query()).isEqualTo("ml AND \"deep learning\"");
    assertThat(outcome.warnings()).isEmpty();
  }
}
