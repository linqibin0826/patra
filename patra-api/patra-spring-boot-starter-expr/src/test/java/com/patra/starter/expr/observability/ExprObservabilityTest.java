package com.patra.starter.expr.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Observability validation: logs and metrics for Phase 6.3. */
class ExprObservabilityTest {

  private Logger compilerLogger;
  private MemoryAppender appender;

  @BeforeEach
  void setUp() {
    compilerLogger = (Logger) LoggerFactory.getLogger(DefaultExprCompiler.class);
    appender = new MemoryAppender();
    appender.setContext(compilerLogger.getLoggerContext());
    appender.start();
    compilerLogger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    if (compilerLogger != null && appender != null) {
      compilerLogger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  @DisplayName("P6.3.1/6.3.2 — INFO redacts query (hash only); DEBUG prints details in non-prod")
  void logs_redaction_and_debug_detail() throws Exception {
    ProvenanceSnapshot snapshot = loadSnapshot("/golden/pubmed/snapshot.json");
    DefaultExprCompiler compiler = compiler(snapshot, /* metrics= */ ExprMetrics.noop());
    Expr expr =
        Exprs.and(
            List.of(
                Exprs.term("tiab", "heart failure", TextMatch.PHRASE),
                Exprs.rangeDate(
                    "entrez_date", LocalDate.parse("2023-01-01"), LocalDate.parse("2023-12-31"))));

    // INFO-level: expect redacted summary (hash), no raw query/params
    compilerLogger.setLevel(Level.INFO);
    appender.clear();
    CompileRequest infoReq =
        CompileRequestBuilder.of(expr, ProvenanceCode.PUBMED)
            .withStrict(false)
            .withTraceEnabled(false)
            .build();
    compiler.compile(infoReq);

    String infoLogs = appender.dump();
    assertThat(infoLogs)
        .contains("Compiled expr for provenance=PUBMED")
        .contains("queryHash=")
        .doesNotContain("\"heart failure\"")
        .doesNotContain("Compiled params detail");

    // DEBUG-level: expect detailed params printed
    compilerLogger.setLevel(Level.DEBUG);
    appender.clear();
    CompileRequest debugReq =
        CompileRequestBuilder.of(expr, ProvenanceCode.PUBMED)
            .withStrict(false)
            .withTraceEnabled(false)
            .build();
    compiler.compile(debugReq);

    String debugLogs = appender.dump();
    assertThat(debugLogs)
        .contains("Compiled expr for provenance=PUBMED")
        .contains("Compiled params detail:")
        .contains("term=\"heart failure\"[TIAB]")
        .contains("mindate=2023-01-01")
        .contains("maxdate=2023-12-30"); // TO_EXCLUSIVE_MINUS_1D applied
  }

  @Test
  @DisplayName("P6.3.3/6.3.4 — Metrics names/tags and miss counters (zero for seeded providers)")
  void metrics_counters_and_labels() throws Exception {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    ExprMetrics metrics = ExprMetrics.of(registry);
    ProvenanceSnapshot snapshot = loadSnapshot("/golden/pubmed/snapshot.json");
    DefaultExprCompiler compiler = compiler(snapshot, metrics);

    Expr expr =
        Exprs.and(
            List.of(
                Exprs.term("tiab", "heart failure", TextMatch.PHRASE),
                Exprs.rangeDate(
                    "entrez_date", LocalDate.parse("2023-01-01"), LocalDate.parse("2023-12-31"))));

    CompileRequest req =
        CompileRequestBuilder.of(expr, ProvenanceCode.PUBMED)
            .withStrict(false)
            .withTraceEnabled(false)
            .build();
    CompileResult out = compiler.compile(req);
    assertThat(out.report().errors()).isEmpty();

    // rule hits and param map hits should be > 0
    Counter ruleHits =
        registry
            .find("expr.render.rule_hits")
            .tag("provenance", "PUBMED")
            .tag("endpoint", "SEARCH")
            .counter();
    Counter mapHits =
        registry
            .find("expr.param.map_hit")
            .tag("provenance", "PUBMED")
            .tag("endpoint", "SEARCH")
            .counter();
    assertThat(ruleHits).isNotNull();
    assertThat(mapHits).isNotNull();
    assertThat(ruleHits.count()).isGreaterThan(0.0);
    assertThat(mapHits.count()).isGreaterThan(0.0);

    // param map miss should be zero for seeded providers; rule miss may be >0 when a field
    // intentionally supports only one emit type (e.g., text→QUERY only, date→PARAMS only)
    Counter mapMiss =
        registry
            .find("expr.param.map_miss")
            .tag("provenance", "PUBMED")
            .tag("endpoint", "SEARCH")
            .counter();
    assertThat(mapMiss == null ? 0.0 : mapMiss.count()).isEqualTo(0.0);

    // transform applied should record with code tag
    Counter transform =
        registry
            .find("expr.transform.applied")
            .tag("provenance", "PUBMED")
            .tag("endpoint", "SEARCH")
            .tag("code", "TO_EXCLUSIVE_MINUS_1D")
            .counter();
    assertThat(transform).isNotNull();
    assertThat(transform.count()).isGreaterThan(0.0);

    // compile duration summary exists
    assertThat(
            registry
                .find("expr.compile.duration_ms")
                .tag("provenance", "PUBMED")
                .tag("endpoint", "SEARCH")
                .summary())
        .isNotNull();

    // label bounding: empty endpoint should map to UNKNOWN
    metrics.transformApplied("PUBMED", "", "X");
    Counter unknownEndpoint =
        registry
            .find("expr.transform.applied")
            .tag("provenance", "PUBMED")
            .tag("endpoint", "UNKNOWN")
            .tag("code", "X")
            .counter();
    assertThat(unknownEndpoint).isNotNull();
    assertThat(unknownEndpoint.count()).isEqualTo(1.0);
  }

  // ---------------- helpers ----------------

  private DefaultExprCompiler compiler(ProvenanceSnapshot snapshot, ExprMetrics metrics) {
    FunctionRegistry fn = new DefaultFunctionRegistry(List.of(new PubmedDatetypeFunction()));
    TransformRegistry tr =
        new DefaultTransformRegistry(
            List.of(
                new ToExclusiveMinus1DTransform(),
                new ListJoinTransform(),
                new FilterJoinTransform()));
    ExprRenderer renderer = new DefaultExprRenderer(fn, metrics);
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
        metrics);
  }

  private ProvenanceSnapshot loadSnapshot(String resourcePath) throws Exception {
    try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
      if (in == null) throw new IllegalArgumentException("Resource not found: " + resourcePath);
      String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      return new com.fasterxml.jackson.databind.ObjectMapper()
          .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
          .readValue(json, ProvenanceSnapshot.class);
    }
  }

  private static class MemoryAppender extends AppenderBase<ILoggingEvent> {
    private final List<ILoggingEvent> events = new ArrayList<>();

    @Override
    protected void append(ILoggingEvent e) {
      events.add(e);
    }

    void clear() {
      events.clear();
    }

    String dump() {
      return events.stream()
          .map(ev -> String.format(Locale.ROOT, "%s %s", ev.getLevel(), ev.getFormattedMessage()))
          .collect(Collectors.joining("\n"));
    }
  }
}
