package com.patra.starter.expr.compiler.normalize;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.expr.CaseSensitivity;
import com.patra.expr.Const;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.expr.TextMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultExprNormalizerTest {

  private final DefaultExprNormalizer normalizer = new DefaultExprNormalizer();

  @Test
  void normalize_shouldTrimTermValue() {
    Expr term = Exprs.term("title", "  Hello  ", TextMatch.PHRASE, CaseSensitivity.INSENSITIVE);
    Expr normalized = normalizer.normalize(term, true);
    assertThat(normalized).isInstanceOf(com.patra.expr.Atom.class);
    com.patra.expr.Atom.TermValue value =
        (com.patra.expr.Atom.TermValue) ((com.patra.expr.Atom) normalized).value();
    assertThat(value.text()).isEqualTo("Hello");
  }

  @Test
  void normalize_shouldDeduplicateInValuesAndPromoteSingle() {
    Expr in = Exprs.in("source", List.of(" PubMed ", "pubmed", "PMC"), CaseSensitivity.INSENSITIVE);
    Expr normalized = normalizer.normalize(in, false);
    assertThat(normalized).isInstanceOf(com.patra.expr.Atom.class);
    com.patra.expr.Atom atom = (com.patra.expr.Atom) normalized;
    assertThat(atom.operator()).isEqualTo(com.patra.expr.Atom.Operator.IN);
    com.patra.expr.Atom.InValues values = (com.patra.expr.Atom.InValues) atom.value();
    assertThat(values.values()).containsExactly("PubMed", "PMC");
  }

  @Test
  void normalize_shouldFlattenAndAndRemoveConstants() {
    Expr expr =
        Exprs.and(
            List.of(
                Exprs.constTrue(),
                Exprs.and(List.of(Exprs.term("a", "x", TextMatch.PHRASE), Exprs.constTrue())),
                Exprs.term("a", "x", TextMatch.PHRASE)));
    Expr normalized = normalizer.normalize(expr, false);
    assertThat(normalized).isInstanceOf(com.patra.expr.Atom.class);
  }

  @Test
  void normalize_shouldShortCircuitOrWithTrue() {
    Expr expr =
        Exprs.or(
            List.of(Exprs.constFalse(), Exprs.constTrue(), Exprs.term("a", "x", TextMatch.PHRASE)));
    Expr normalized = normalizer.normalize(expr, false);
    assertThat(normalized).isEqualTo(Const.TRUE);
  }

  @Test
  void normalize_shouldEliminateDoubleNot() {
    Expr expr = Exprs.not(Exprs.not(Exprs.term("a", "b", TextMatch.PHRASE)));
    Expr normalized = normalizer.normalize(expr, false);
    assertThat(normalized).isInstanceOf(com.patra.expr.Atom.class);
  }
}
