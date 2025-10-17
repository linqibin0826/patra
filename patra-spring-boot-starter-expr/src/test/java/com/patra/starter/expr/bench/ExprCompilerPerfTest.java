package com.patra.starter.expr.bench;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.expr.TextMatch;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Simple micro benchmark for the expression compiler.
 *
 * <p>- Disabled by default. Enable with RUN_BENCH=1 to avoid flakiness in CI. - Uses the in-memory
 * test snapshot (no Spring context or HTTP calls). - Prints p50/p95/avg timings to stdout for
 * manual capture in the acceptance report.
 */
class ExprCompilerPerfTest {

  @Test
  @DisplayName("P6.4.3 — 50-100 atoms compile under ~50ms (manual benchmark)")
  void benchmark_compile_latency() throws Exception {
    assumeTrue(runBench(), "Set RUN_BENCH=1 to enable benchmark");

    ProvenanceSnapshot snapshot = loadSnapshot("/golden/crossref/snapshot.json");
    DefaultExprCompiler compiler = compiler(snapshot);

    // Build a complex query: (phrase OR phrase ... 80x) AND (IN 10) AND date range
    Expr expr = complexExpr(80, 10);

    // Warmup: JIT + caches
    for (int i = 0; i < 20; i++) runOnce(compiler, expr);

    // Measure
    int runs = 50;
    List<Long> times = new ArrayList<>(runs);
    for (int i = 0; i < runs; i++) {
      long ns = runOnce(compiler, expr);
      times.add(ns);
    }

    times.sort(Long::compare);
    double avgMs = times.stream().mapToLong(l -> l).average().orElse(0) / 1_000_000.0;
    double p50Ms = times.get(times.size() / 2) / 1_000_000.0;
    double p95Ms = times.get((int) Math.floor(runs * 0.95) - 1) / 1_000_000.0;

    System.out.printf(
        Locale.ROOT,
        "ExprCompiler benchmark (runs=%d, atoms≈%d): avg=%.2fms, p50=%.2fms, p95=%.2fms%n",
        runs,
        80 + 10 + 1,
        avgMs,
        p50Ms,
        p95Ms);

    // Intentionally no hard assertion here to avoid environment flakiness.
    // Use the printed metrics in docs/expr to complete P6.4.3.
  }

  @Test
  @DisplayName("P6.4.4 — Memory footprint sanity (manual observation)")
  void benchmark_memory_footprint() throws Exception {
    assumeTrue(runBench(), "Set RUN_BENCH=1 to enable benchmark");

    ProvenanceSnapshot snapshot = loadSnapshot("/golden/epmc/snapshot.json");
    DefaultExprCompiler compiler = compiler(snapshot);
    Expr expr = complexExpr(60, 8);

    // Warmup
    for (int i = 0; i < 20; i++) runOnce(compiler, expr);

    Runtime rt = Runtime.getRuntime();
    gcQuietly();
    long before = used(rt);

    for (int i = 0; i < 200; i++) runOnce(compiler, expr);

    gcQuietly();
    long after = used(rt);

    long deltaMb = Math.max(0, after - before) / (1024 * 1024);
    System.out.printf(
        Locale.ROOT,
        "ExprCompiler memory delta after 200 compiles: %d MB (before=%d MB, after=%d MB)%n",
        deltaMb,
        before / (1024 * 1024),
        after / (1024 * 1024));

    // No hard assertion; record in acceptance report. Expect small deltas (< ~16MB) in dev envs.
  }

  // ---------------------- helpers ----------------------

  private boolean runBench() {
    String v = System.getenv("RUN_BENCH");
    return v != null && v.equals("1");
  }

  private long used(Runtime rt) {
    return rt.totalMemory() - rt.freeMemory();
  }

  private void gcQuietly() {
    try {
      System.gc();
      Thread.sleep(100);
    } catch (InterruptedException ignored) {
    }
  }

  private long runOnce(DefaultExprCompiler compiler, Expr expr) {
    long t0 = System.nanoTime();
    CompileRequest req =
        CompileRequestBuilder.of(expr, ProvenanceCode.CROSSREF)
            .withStrict(false)
            .withTraceEnabled(false)
            .build();
    CompileResult out = compiler.compile(req);
    // Ensure JVM cannot dead-code-eliminate the result
    if (out == null || (out.query() == null && out.params().isEmpty())) {
      throw new IllegalStateException("Unexpected empty compile result");
    }
    return System.nanoTime() - t0;
  }

  private Expr complexExpr(int phraseCount, int inCount) {
    List<Expr> orTerms = new ArrayList<>(phraseCount);
    for (int i = 0; i < phraseCount; i++) {
      orTerms.add(Exprs.term("title", "term-" + i, TextMatch.PHRASE));
    }

    // IN clause values
    List<String> values = new ArrayList<>(inCount);
    for (int i = 0; i < inCount; i++) values.add("v" + i);

    return Exprs.and(
        List.of(
            Exprs.or(orTerms),
            Exprs.in("lang", values),
            Exprs.rangeDate("date", LocalDate.parse("2024-01-01"), LocalDate.parse("2024-12-31"))));
  }

  private DefaultExprCompiler compiler(ProvenanceSnapshot snapshot) throws Exception {
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

  private ProvenanceSnapshot loadSnapshot(String resourcePath) throws Exception {
    try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
      if (in == null) {
        throw new IllegalArgumentException("Resource not found: " + resourcePath);
      }
      byte[] bytes = in.readAllBytes();
      String json = new String(bytes, StandardCharsets.UTF_8);
      return new com.fasterxml.jackson.databind.ObjectMapper()
          .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
          .readValue(json, ProvenanceSnapshot.class);
    }
  }
}
