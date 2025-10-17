package com.patra.starter.expr.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Expr;
import com.patra.expr.json.ExprJsonCodec;
import com.patra.starter.expr.compiler.DefaultExprCompiler;
import com.patra.starter.expr.compiler.boot.CompilerProperties;
import com.patra.starter.expr.compiler.boot.ExprModeProperties;
import com.patra.starter.expr.compiler.check.CapabilityChecker;
import com.patra.starter.expr.compiler.check.DefaultCapabilityChecker;
import com.patra.starter.expr.compiler.function.DefaultFunctionRegistry;
import com.patra.starter.expr.compiler.function.FunctionRegistry;
import com.patra.starter.expr.compiler.function.PubmedDatetypeFunction;
import com.patra.starter.expr.compiler.metrics.ExprMetrics;
import com.patra.starter.expr.compiler.model.CompileRequest;
import com.patra.starter.expr.compiler.model.CompileRequestBuilder;
import com.patra.starter.expr.compiler.model.CompileResult;
import com.patra.starter.expr.compiler.normalize.DefaultExprNormalizer;
import com.patra.starter.expr.compiler.normalize.ExprNormalizer;
import com.patra.starter.expr.compiler.render.DefaultExprRenderer;
import com.patra.starter.expr.compiler.render.ExprRenderer;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import com.patra.starter.expr.compiler.snapshot.RuleSnapshotLoader;
import com.patra.starter.expr.compiler.transform.DefaultTransformRegistry;
import com.patra.starter.expr.compiler.transform.FilterJoinTransform;
import com.patra.starter.expr.compiler.transform.ListJoinTransform;
import com.patra.starter.expr.compiler.transform.ToExclusiveMinus1DTransform;
import com.patra.starter.expr.compiler.transform.TransformRegistry;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Golden test harness runner. Loads snapshot/expr/expected JSON triplets and asserts deterministic
 * outputs.
 */
@DisplayName("P4.5 — Golden Harness")
class GoldenTestHarness {

  private final ObjectMapper mapper = new ObjectMapper();

  private DefaultExprCompiler compiler(ProvenanceSnapshot snapshot) {
    FunctionRegistry fn = new DefaultFunctionRegistry(List.of(new PubmedDatetypeFunction()));
    TransformRegistry tr =
        new DefaultTransformRegistry(
            List.of(
                new ToExclusiveMinus1DTransform(),
                new ListJoinTransform(),
                new FilterJoinTransform()));
    ExprRenderer renderer = new DefaultExprRenderer(fn, ExprMetrics.noop());
    CapabilityChecker checker = new DefaultCapabilityChecker();
    ExprNormalizer normalizer = new DefaultExprNormalizer();
    RuleSnapshotLoader loader = (prov, opType, endpoint) -> snapshot;

    return new DefaultExprCompiler(
        loader,
        checker,
        normalizer,
        renderer,
        tr,
        new CompilerProperties(),
        new ExprModeProperties(),
        ExprMetrics.noop());
  }

  @Test
  @DisplayName("PubMed — phrase + date (deterministic query/params)")
  void pubmed_phrase_date() throws Exception {
    runCase("pubmed", "expr-phrase-date.json", "expected-phrase-date.json", false);
  }

  @Test
  @DisplayName("PubMed — STRICT mode error (NOT unsupported)")
  void pubmed_strict_error() throws Exception {
    runCase("pubmed", "expr-strict-mode-error.json", "expected-strict-mode-error.json", true);
  }

  @Test
  @DisplayName("Crossref — phrase + date → query + filter")
  void crossref_filter() throws Exception {
    runCase("crossref", "expr-filter.json", "expected-filter.json", false);
  }

  @Test
  @DisplayName("Crossref — warning codes (non-STRICT)")
  void crossref_warning_codes() throws Exception {
    runCase("crossref", "expr-warning-codes.json", "expected-warning-codes.json", false);
  }

  @Test
  @DisplayName("EPMC — date in query")
  void epmc_date_query() throws Exception {
    runCase("epmc", "expr-date-query.json", "expected-date-query.json", false);
  }

  private void runCase(String provider, String exprFile, String expectedFile, boolean strict)
      throws Exception {
    ProvenanceSnapshot snapshot = loadSnapshot(provider + "/snapshot.json");
    Expr expr = loadExpr(provider + "/" + exprFile);
    Expected expected = loadExpected(provider + "/" + expectedFile);

    DefaultExprCompiler compiler = compiler(snapshot);
    CompileRequest req =
        CompileRequestBuilder.of(expr, ProvenanceCode.valueOf(provider.toUpperCase()))
            .withStrict(strict)
            .build();
    CompileResult out = compiler.compile(req);

    // normalize query whitespace
    String outQuery = normalize(out.query());
    assertThat(outQuery).isEqualTo(normalize(expected.query));

    // compare params as map equality
    assertThat(out.params()).isEqualTo(expected.params);

    // compare codes only
    List<String> warnCodes = out.report().warnings().stream().map(w -> w.code()).toList();
    List<String> errCodes = out.report().errors().stream().map(e -> e.code()).toList();
    assertThat(warnCodes).isEqualTo(expected.warnings);
    assertThat(errCodes).isEqualTo(expected.errors);
  }

  private String normalize(String s) {
    if (s == null) return "";
    return s.replaceAll("\\s+", " ").trim();
  }

  private ProvenanceSnapshot loadSnapshot(String path) throws Exception {
    try (InputStream in = resource(path)) {
      return mapper.readValue(in, ProvenanceSnapshot.class);
    }
  }

  private Expr loadExpr(String path) throws Exception {
    try (InputStream in = resource(path)) {
      String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      return ExprJsonCodec.fromJson(json);
    }
  }

  private Expected loadExpected(String path) throws Exception {
    try (InputStream in = resource(path)) {
      Map<String, Object> node = mapper.readValue(in, new TypeReference<>() {});
      String query = (String) node.getOrDefault("query", "");
      @SuppressWarnings("unchecked")
      Map<String, String> params =
          node.containsKey("params")
              ? ((Map<String, Object>) node.get("params"))
                  .entrySet().stream()
                      .collect(
                          Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())))
              : Map.of();
      @SuppressWarnings("unchecked")
      List<String> warnings = (List<String>) node.getOrDefault("warnings", List.of());
      @SuppressWarnings("unchecked")
      List<String> errors = (List<String>) node.getOrDefault("errors", List.of());
      return new Expected(query, params, warnings, errors);
    }
  }

  private InputStream resource(String path) {
    InputStream in =
        GoldenTestHarness.class.getResourceAsStream("/golden/" + path.replace("\\", "/"));
    if (in == null) throw new IllegalArgumentException("Resource not found: " + path);
    return in;
  }

  private record Expected(
      String query, Map<String, String> params, List<String> warnings, List<String> errors) {}
}
