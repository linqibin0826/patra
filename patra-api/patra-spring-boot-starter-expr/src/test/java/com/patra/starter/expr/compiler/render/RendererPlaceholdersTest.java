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
 * Validates PARAM placeholders ({{from}}/{{to}}) expand to rendered values (docs/expr/08-testing.md
 * §8.2, task P4.1.5).
 */
@DisplayName("Renderer PARAM placeholder Tests")
class RendererPlaceholdersTest {

  private ProvenanceSnapshot snapshot;
  private DefaultExprRenderer renderer;

  @BeforeEach
  void setUp() {
    FieldDefinition dateField =
        new FieldDefinition(
            "entrez_date", "Entrez Date", "", DataType.DATE, Cardinality.SINGLE, true, true);

    RenderRule noopQueryRule =
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
            100);

    RenderRule paramsRule =
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
            Map.of("from", "{{from}}", "to", "{{to}}"),
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
            Map.of("entrez_date", dateField),
            Map.of(),
            Map.of(),
            List.of(noopQueryRule, paramsRule));

    renderer = new DefaultExprRenderer(null, ExprMetrics.noop());
  }

  @Test
  @DisplayName("Range placeholders should expand into PARAM std_keys")
  void testRangePlaceholdersExpand() {
    Expr expr =
        Exprs.rangeDate("entrez_date", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

    ExprRenderer.RenderOutcome outcome = renderer.render(expr, snapshot, false);

    assertThat(outcome.params())
        .containsEntry("from", "2024-01-01")
        .containsEntry("to", "2024-12-31");
    assertThat(outcome.query()).isEmpty();
    assertThat(outcome.warnings()).isEmpty();
  }
}
