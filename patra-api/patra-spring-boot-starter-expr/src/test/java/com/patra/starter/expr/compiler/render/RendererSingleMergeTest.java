package com.patra.starter.expr.compiler.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.expr.Expr;
import com.patra.expr.Exprs;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Validates SINGLE std_key merge policy prefers higher priority emissions (docs/expr/08-testing.md
 * §8.2, task P4.1.7).
 */
@DisplayName("Renderer SINGLE std_key merge Tests")
class RendererSingleMergeTest {

  private ProvenanceSnapshot snapshot;
  private DefaultExprRenderer renderer;

  @BeforeEach
  void setUp() {
    FieldDefinition entrezDate =
        new FieldDefinition(
            "entrez_date", "Entrez Date", "", DataType.DATE, Cardinality.SINGLE, true, true);
    FieldDefinition onlineDate =
        new FieldDefinition(
            "online_date", "Online Date", "", DataType.DATE, Cardinality.SINGLE, true, true);

    RenderRule noopEntrezQuery =
        new RenderRule(
            "entrez_date",
            "SOURCE",
            null,
            com.patra.expr.Atom.Operator.RANGE,
            null,
            NegationQualifier.ANY,
            ValueType.DATE,
            EmitType.QUERY,
            "",
            null,
            null,
            false,
            Map.of(),
            null,
            Instant.parse("2000-01-01T00:00:00Z"),
            null,
            80);

    RenderRule noopOnlineQuery =
        new RenderRule(
            "online_date",
            "SOURCE",
            null,
            com.patra.expr.Atom.Operator.RANGE,
            null,
            NegationQualifier.ANY,
            ValueType.DATE,
            EmitType.QUERY,
            "",
            null,
            null,
            false,
            Map.of(),
            null,
            Instant.parse("2000-01-01T00:00:00Z"),
            null,
            90);

    RenderRule entrezParams =
        new RenderRule(
            "entrez_date",
            "SOURCE",
            null,
            com.patra.expr.Atom.Operator.RANGE,
            null,
            NegationQualifier.ANY,
            ValueType.DATE,
            EmitType.PARAMS,
            null,
            null,
            null,
            false,
            Map.of("from", "{{from}}"),
            null,
            Instant.parse("2000-01-01T00:00:00Z"),
            null,
            120);

    RenderRule onlineParams =
        new RenderRule(
            "online_date",
            "SOURCE",
            null,
            com.patra.expr.Atom.Operator.RANGE,
            null,
            NegationQualifier.ANY,
            ValueType.DATE,
            EmitType.PARAMS,
            null,
            null,
            null,
            false,
            Map.of("from", "{{from}}"),
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
            Map.of("entrez_date", entrezDate, "online_date", onlineDate),
            Map.of(),
            Map.of(),
            List.of(noopEntrezQuery, noopOnlineQuery, entrezParams, onlineParams));

    renderer = new DefaultExprRenderer(null, ExprMetrics.noop());
  }

  @Test
  @DisplayName("Higher priority emission should replace existing SINGLE std_key value")
  void testHigherPriorityWins() {
    Expr expr =
        Exprs.and(
            List.of(
                Exprs.rangeDate("entrez_date", LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 31)),
                Exprs.rangeDate(
                    "online_date", LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 31))));

    ExprRenderer.RenderOutcome outcome = renderer.render(expr, snapshot, false);

    assertThat(outcome.params()).containsEntry("from", "2023-01-01");
    assertThat(outcome.params()).hasSize(1);
    assertThat(outcome.warnings()).isEmpty();
  }
}
