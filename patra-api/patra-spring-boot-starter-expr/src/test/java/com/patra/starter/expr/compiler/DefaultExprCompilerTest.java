package com.patra.starter.expr.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.expr.TextMatch;
import com.patra.starter.expr.compiler.boot.CompilerProperties;
import com.patra.starter.expr.compiler.boot.ExprModeProperties;
import com.patra.starter.expr.compiler.check.CapabilityChecker;
import com.patra.starter.expr.compiler.metrics.ExprMetrics;
import com.patra.starter.expr.compiler.model.CompileRequest;
import com.patra.starter.expr.compiler.model.CompileRequestBuilder;
import com.patra.starter.expr.compiler.model.CompileResult;
import com.patra.starter.expr.compiler.model.Issue;
import com.patra.starter.expr.compiler.model.RenderTrace;
import com.patra.starter.expr.compiler.render.ExprRenderer;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import com.patra.starter.expr.compiler.snapshot.RuleSnapshotLoader;
import com.patra.starter.expr.compiler.transform.TransformRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultExprCompilerTest {

  private ProvenanceSnapshot snapshot;
  private TransformRegistry transformRegistry;
  private CompilerProperties compilerProperties;
  private ExprModeProperties modeProperties;

  @BeforeEach
  void setUp() {
    ProvenanceSnapshot.FieldDefinition field =
        new ProvenanceSnapshot.FieldDefinition(
            "title",
            "Title",
            "",
            ProvenanceSnapshot.DataType.TEXT,
            ProvenanceSnapshot.Cardinality.SINGLE,
            true,
            false);
    ProvenanceSnapshot.Capability capability =
        new ProvenanceSnapshot.Capability(
            Set.of("TERM"),
            Set.of(),
            false,
            Set.of("PHRASE"),
            false,
            false,
            0,
            100,
            null,
            100,
            false,
            ProvenanceSnapshot.RangeKind.NONE,
            false,
            false,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            true,
            Set.of(),
            null);
    Map<String, ProvenanceSnapshot.ApiParameter> apiParams =
        Map.of(
            "q", new ProvenanceSnapshot.ApiParameter("q", "q", null, null),
            "query", new ProvenanceSnapshot.ApiParameter("query", "query", null, null));
    snapshot =
        new ProvenanceSnapshot(
            new ProvenanceSnapshot.Identity(1L, "PUBMED", "PubMed"),
            ProvenanceSnapshot.Scope.sourceScope(),
            new ProvenanceSnapshot.Operation("SEARCH", "UTC"),
            1L,
            Instant.parse("2024-05-01T00:00:00Z"),
            Map.of("title", field),
            Map.of("title", capability),
            apiParams,
            List.of());
    transformRegistry = code -> java.util.Optional.empty();
    compilerProperties = new CompilerProperties();
    modeProperties = new ExprModeProperties();
  }

  @Test
  void compile_shouldStopWhenCapabilityErrors() {
    Issue error = Issue.error("E", "err", Map.of());
    DefaultExprCompiler compiler =
        new DefaultExprCompiler(
            new StubSnapshotLoader(snapshot),
            new StubCapabilityChecker(List.of(error)),
            new IdentityNormalizer(),
            new StubRenderer("query", Map.of(), List.of(), null),
            transformRegistry,
            compilerProperties,
            modeProperties,
            ExprMetrics.noop());

    CompileRequest request =
        CompileRequestBuilder.of(
                Exprs.term("title", "hello", TextMatch.PHRASE), ProvenanceCode.PUBMED)
            .build();
    CompileResult result = compiler.compile(request);

    assertThat(result.query()).isEmpty();
    assertThat(result.report().errors()).containsExactly(error);
    assertThat(result.snapshot().provenanceCode()).isEqualTo("PUBMED");
  }

  @Test
  void compile_shouldRenderQueryAndMergeWarnings() {
    Issue warn = Issue.warn("W", "warn", Map.of());
    RenderTrace trace = new RenderTrace(List.of(new RenderTrace.Hit("title", "TERM", 1, "rule")));
    DefaultExprCompiler compiler =
        new DefaultExprCompiler(
            new StubSnapshotLoader(snapshot),
            new StubCapabilityChecker(List.of()),
            new IdentityNormalizer(),
            new StubRenderer("title:hello", Map.of("q", "hello"), List.of(warn), trace),
            transformRegistry,
            compilerProperties,
            modeProperties,
            ExprMetrics.noop());

    CompileRequest request =
        CompileRequestBuilder.of(
                Exprs.term("title", "hello", TextMatch.PHRASE), ProvenanceCode.PUBMED)
            .withTraceEnabled(true)
            .build();
    CompileResult result = compiler.compile(request);

    assertThat(result.query()).isEqualTo("title:hello");
    assertThat(result.params()).containsEntry("q", "hello");
    assertThat(result.params()).containsEntry("query", "title:hello");
    assertThat(result.report().warnings()).containsExactly(warn);
    assertThat(result.trace()).isEqualTo(trace);
  }

  @Test
  void compile_shouldAddLengthErrorWhenQueryTooLong() {
    DefaultExprCompiler compiler =
        new DefaultExprCompiler(
            new StubSnapshotLoader(snapshot),
            new StubCapabilityChecker(List.of()),
            new IdentityNormalizer(),
            new StubRenderer("a".repeat(50), Map.of(), List.of(), null),
            transformRegistry,
            compilerProperties,
            modeProperties,
            ExprMetrics.noop());

    CompileRequest request =
        CompileRequestBuilder.of(
                Exprs.term("title", "hello", TextMatch.PHRASE), ProvenanceCode.PUBMED)
            .withMaxQueryLength(10)
            .build();
    CompileResult result = compiler.compile(request);

    assertThat(result.query()).isEmpty();
    assertThat(result.report().errors())
        .anySatisfy(issue -> assertThat(issue.code()).isEqualTo("E-QUERY-LEN-MAX"));
  }

  @Test
  void compile_defaultStrictModeTreatsMissingTransformAsWarning() {
    Map<String, ProvenanceSnapshot.ApiParameter> apiParams =
        Map.of(
            "q", new ProvenanceSnapshot.ApiParameter("q", "q", "UNKNOWN_TRANSFORM", null),
            "query", new ProvenanceSnapshot.ApiParameter("query", "query", null, null));
    ProvenanceSnapshot customSnapshot =
        new ProvenanceSnapshot(
            snapshot.identity(),
            snapshot.scope(),
            snapshot.operation(),
            snapshot.version(),
            snapshot.capturedAt(),
            snapshot.fieldDictionary(),
            snapshot.capabilityMatrix(),
            apiParams,
            snapshot.renderRules());

    DefaultExprCompiler compiler =
        new DefaultExprCompiler(
            new StubSnapshotLoader(customSnapshot),
            new StubCapabilityChecker(List.of()),
            new IdentityNormalizer(),
            new StubRenderer("title:hello", Map.of("q", "hello"), List.of(), null),
            code -> java.util.Optional.empty(),
            compilerProperties,
            modeProperties,
            ExprMetrics.noop());

    CompileRequest request =
        CompileRequestBuilder.of(
                Exprs.term("title", "hello", TextMatch.PHRASE), ProvenanceCode.PUBMED)
            .build();
    CompileResult result = compiler.compile(request);

    assertThat(result.report().errors()).isEmpty();
    assertThat(result.report().warnings())
        .anySatisfy(issue -> assertThat(issue.code()).isEqualTo("W-FN-OR-TRANSFORM-NOTFOUND"));
    assertThat(result.params()).containsEntry("q", "hello");
  }

  @Test
  void bridgeQuery_shouldNotOverrideExistingProviderParam() {
    Map<String, ProvenanceSnapshot.ApiParameter> apiParams =
        Map.of("query", new ProvenanceSnapshot.ApiParameter("query", "term", null, null));
    ProvenanceSnapshot customSnapshot =
        new ProvenanceSnapshot(
            snapshot.identity(),
            snapshot.scope(),
            snapshot.operation(),
            snapshot.version(),
            snapshot.capturedAt(),
            snapshot.fieldDictionary(),
            snapshot.capabilityMatrix(),
            apiParams,
            snapshot.renderRules());

    DefaultExprCompiler compiler =
        new DefaultExprCompiler(
            new StubSnapshotLoader(customSnapshot),
            new StubCapabilityChecker(List.of()),
            new IdentityNormalizer(),
            new StubRenderer("foo:bar", Map.of("query", "manual-term"), List.of(), null),
            transformRegistry,
            compilerProperties,
            modeProperties,
            ExprMetrics.noop());

    CompileRequest request =
        CompileRequestBuilder.of(
                Exprs.term("title", "hello", TextMatch.PHRASE), ProvenanceCode.PUBMED)
            .build();
    CompileResult result = compiler.compile(request);

    assertThat(result.params()).containsEntry("term", "manual-term");
    assertThat(result.report().warnings())
        .anySatisfy(issue -> assertThat(issue.code()).isEqualTo("W-QUERY-BRIDGE-DUP"));
  }

  private record StubSnapshotLoader(ProvenanceSnapshot snapshot) implements RuleSnapshotLoader {
    @Override
    public ProvenanceSnapshot load(
        ProvenanceCode provenanceCode, String operationType, String endpointName) {
      return snapshot;
    }
  }

  private record StubCapabilityChecker(List<Issue> issues) implements CapabilityChecker {
    @Override
    public List<Issue> check(Expr expression, ProvenanceSnapshot snapshot, boolean strictMode) {
      return issues;
    }
  }

  private record StubRenderer(
      String query, Map<String, String> params, List<Issue> warnings, RenderTrace trace)
      implements ExprRenderer {
    @Override
    public RenderOutcome render(
        Expr expression, ProvenanceSnapshot snapshot, boolean traceEnabled) {
      return new RenderOutcome(query, params, warnings, trace);
    }
  }

  private static final class IdentityNormalizer
      implements com.patra.starter.expr.compiler.normalize.ExprNormalizer {
    @Override
    public Expr normalize(Expr expression, boolean strictMode) {
      return expression;
    }
  }
}
