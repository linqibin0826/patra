package com.patra.starter.expr.compiler;

import com.patra.expr.Expr;
import com.patra.starter.expr.compiler.boot.CompilerProperties;
import com.patra.starter.expr.compiler.boot.ExprModeProperties;
import com.patra.starter.expr.compiler.check.CapabilityChecker;
import com.patra.starter.expr.compiler.metrics.ExprMetrics;
import com.patra.starter.expr.compiler.model.CompileRequest;
import com.patra.starter.expr.compiler.model.CompileResult;
import com.patra.starter.expr.compiler.model.Issue;
import com.patra.starter.expr.compiler.model.IssueSeverity;
import com.patra.starter.expr.compiler.model.RenderTrace;
import com.patra.starter.expr.compiler.model.SnapshotRef;
import com.patra.starter.expr.compiler.model.ValidationReport;
import com.patra.starter.expr.compiler.normalize.ExprNormalizer;
import com.patra.starter.expr.compiler.render.ExprRenderer;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import com.patra.starter.expr.compiler.snapshot.RuleSnapshotLoader;
import com.patra.starter.expr.compiler.transform.TransformRegistry;
import com.patra.starter.expr.compiler.transform.ValueTransform;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultExprCompiler implements ExprCompiler {

  private static final Logger log = LoggerFactory.getLogger(DefaultExprCompiler.class);
  private static final String MULTI_DELIMITER = "||";

  private final RuleSnapshotLoader snapshotLoader;
  private final CapabilityChecker capabilityChecker;
  private final ExprNormalizer normalizer;
  private final ExprRenderer renderer;
  private final TransformRegistry transformRegistry;
  private final CompilerProperties compilerProperties;
  private final ExprModeProperties modeProperties;
  private final ExprMetrics metrics;

  public DefaultExprCompiler(
      RuleSnapshotLoader snapshotLoader,
      CapabilityChecker capabilityChecker,
      ExprNormalizer normalizer,
      ExprRenderer renderer,
      TransformRegistry transformRegistry,
      CompilerProperties compilerProperties,
      ExprModeProperties modeProperties,
      ExprMetrics metrics) {
    this.snapshotLoader = Objects.requireNonNull(snapshotLoader);
    this.capabilityChecker = Objects.requireNonNull(capabilityChecker);
    this.normalizer = Objects.requireNonNull(normalizer);
    this.renderer = Objects.requireNonNull(renderer);
    this.transformRegistry = Objects.requireNonNull(transformRegistry);
    this.compilerProperties = Objects.requireNonNull(compilerProperties);
    this.modeProperties = Objects.requireNonNull(modeProperties);
    this.metrics = metrics == null ? ExprMetrics.noop() : metrics;
  }

  @Override
  public CompileResult compile(CompileRequest request) {
    Objects.requireNonNull(request, "request");
    long startNanos = System.nanoTime();
    String provenanceCode = null;
    String endpointName = fallbackEndpoint(request.endpointName());

    try {
      CompileContext ctx = extractCompileContext(request);
      ProvenanceSnapshot snapshot = loadSnapshot(request, ctx);
      provenanceCode = snapshot.identity().code();
      endpointName = resolveEndpoint(snapshot, endpointName);

      Expr normalized = normalizer.normalize(request.expression(), request.options().strict());

      CapabilityCheckResult capabilityCheck =
          performCapabilityCheck(request, snapshot, normalized, endpointName, ctx);
      if (capabilityCheck.failure() != null) {
        return capabilityCheck.failure();
      }

      ExprRenderer.RenderOutcome outcome =
          renderer.render(normalized, snapshot, request.options().traceEnabled());

      CompileResult queryLengthFailure =
          validateQueryLength(outcome, normalized, snapshot, endpointName, provenanceCode, ctx);
      if (queryLengthFailure != null) {
        return queryLengthFailure;
      }

      return buildSuccessResult(
          request,
          outcome,
          normalized,
          snapshot,
          endpointName,
          provenanceCode,
          ctx,
          capabilityCheck.warnings());
    } finally {
      recordMetrics(provenanceCode, endpointName, startNanos);
    }
  }

  private CompileContext extractCompileContext(CompileRequest request) {
    boolean strictMode = request.options().strict() || modeProperties.isStrict();
    boolean repeatEnabled = modeProperties.getMulti().isRepeatEnabled();
    int queryLengthLimit = resolveMaxQueryLength(request);
    int warnParamCount = compilerProperties.getWarnParamCount();
    int maxParamCount = compilerProperties.getMaxParamCount();
    boolean bridgeEnabled = compilerProperties.getQueryParamBridge().isEnabled();
    return new CompileContext(
        strictMode, repeatEnabled, queryLengthLimit, warnParamCount, maxParamCount, bridgeEnabled);
  }

  private ProvenanceSnapshot loadSnapshot(CompileRequest request, CompileContext ctx) {
    return snapshotLoader.load(
        request.provenance(), request.operationType(), request.endpointName());
  }

  private CapabilityCheckResult performCapabilityCheck(
      CompileRequest request,
      ProvenanceSnapshot snapshot,
      Expr normalized,
      String endpointName,
      CompileContext ctx) {
    IssueBuckets capabilityBuckets =
        bucketCapabilityIssues(
            capabilityChecker.check(normalized, snapshot, ctx.strictMode()), ctx.strictMode());
    List<Issue> warnings = new ArrayList<>(capabilityBuckets.warnings());
    List<Issue> errors = new ArrayList<>(capabilityBuckets.errors());

    if (errors.isEmpty()) {
      return new CapabilityCheckResult(warnings, null);
    }

    String provenanceCode = snapshot.identity().code();
    log.warn(
        "Capability validation failed for provenance [{}] at endpoint [{}] with {} errors",
        provenanceCode,
        endpointName,
        errors.size());
    recordCompileErrors(errors);

    ValidationReport report = new ValidationReport(warnings, errors);
    CompileResult failure =
        new CompileResult(
            "",
            Map.of(),
            normalized,
            report,
            toRef(snapshot, endpointName),
            request.options().traceEnabled() ? new RenderTrace(List.of()) : null);

    return new CapabilityCheckResult(warnings, failure);
  }

  private CompileResult validateQueryLength(
      ExprRenderer.RenderOutcome outcome,
      Expr normalized,
      ProvenanceSnapshot snapshot,
      String endpointName,
      String provenanceCode,
      CompileContext ctx) {
    String renderedQuery = outcome.query() == null ? "" : outcome.query();
    if (ctx.queryLengthLimit() <= 0 || renderedQuery.length() <= ctx.queryLengthLimit()) {
      return null;
    }

    List<Issue> errors = new ArrayList<>();
    errors.add(
        Issue.error(
            "E-QUERY-LEN-MAX",
            "Rendered query exceeds maximum allowed length",
            Map.of("max", ctx.queryLengthLimit(), "actual", renderedQuery.length())));

    log.warn(
        "Query length exceeded for provenance [{}] at endpoint [{}]: limit={}, actual={}",
        provenanceCode,
        endpointName,
        ctx.queryLengthLimit(),
        renderedQuery.length());
    recordCompileErrors(errors);

    ValidationReport report = new ValidationReport(List.of(), errors);
    return new CompileResult(
        "", Map.of(), normalized, report, toRef(snapshot, endpointName), outcome.trace());
  }

  private CompileResult buildSuccessResult(
      CompileRequest request,
      ExprRenderer.RenderOutcome outcome,
      Expr normalized,
      ProvenanceSnapshot snapshot,
      String endpointName,
      String provenanceCode,
      CompileContext ctx,
      List<Issue> capabilityWarnings) {
    List<Issue> warnings = new ArrayList<>(capabilityWarnings);
    List<Issue> errors = new ArrayList<>();

    String renderedQuery = outcome.query() == null ? "" : outcome.query();
    LinkedHashMap<String, String> providerParams =
        buildProviderParams(
            outcome, snapshot, endpointName, provenanceCode, renderedQuery, warnings, errors, ctx);

    mergeRendererWarnings(outcome.warnings(), warnings, errors, ctx.strictMode());
    applyParamCountLimits(
        providerParams, ctx.warnParamCount(), ctx.maxParamCount(), warnings, errors);

    ValidationReport finalReport = new ValidationReport(warnings, errors);

    if (!errors.isEmpty()) {
      return handleCompileErrors(
          errors, normalized, snapshot, endpointName, provenanceCode, outcome, providerParams);
    }

    logSuccessfulCompile(provenanceCode, endpointName, renderedQuery, providerParams);

    return new CompileResult(
        renderedQuery,
        providerParams,
        normalized,
        finalReport,
        toRef(snapshot, endpointName),
        outcome.trace());
  }

  private LinkedHashMap<String, String> buildProviderParams(
      ExprRenderer.RenderOutcome outcome,
      ProvenanceSnapshot snapshot,
      String endpointName,
      String provenanceCode,
      String renderedQuery,
      List<Issue> warnings,
      List<Issue> errors,
      CompileContext ctx) {
    LinkedHashMap<String, String> providerParams = new LinkedHashMap<>();

    mapStdKeys(
        outcome.params(),
        snapshot,
        provenanceCode,
        endpointName,
        ctx.strictMode(),
        ctx.repeatEnabled(),
        warnings,
        errors,
        providerParams);

    if (ctx.bridgeEnabled() && !renderedQuery.isBlank()) {
      bridgeQuery(
          renderedQuery,
          snapshot,
          provenanceCode,
          endpointName,
          ctx.strictMode(),
          warnings,
          errors,
          providerParams);
    }

    return providerParams;
  }

  private CompileResult handleCompileErrors(
      List<Issue> errors,
      Expr normalized,
      ProvenanceSnapshot snapshot,
      String endpointName,
      String provenanceCode,
      ExprRenderer.RenderOutcome outcome,
      Map<String, String> providerParams) {
    log.warn(
        "Compilation failed for provenance [{}] at endpoint [{}] with {} errors",
        provenanceCode,
        endpointName,
        errors.size());
    recordCompileErrors(errors);

    boolean hasRenderError =
        errors.stream()
            .anyMatch(
                e ->
                    e.code().equals("E-FN-NOTFOUND")
                        || e.code().equals("E-TRANSFORM-NOTFOUND")
                        || e.code().equals("E-TRANSFORM-EXEC"));

    Map<String, String> errorParams = hasRenderError ? Map.of() : providerParams;
    ValidationReport report = new ValidationReport(List.of(), errors);

    return new CompileResult(
        "", errorParams, normalized, report, toRef(snapshot, endpointName), outcome.trace());
  }

  private void logSuccessfulCompile(
      String provenanceCode, String endpointName, String query, Map<String, String> params) {
    log.info(
        "Successfully compiled expression for provenance [{}] at endpoint [{}]: queryHash={}, "
            + "queryLength={}, paramCount={}",
        provenanceCode,
        endpointName,
        hashQuery(query),
        query.length(),
        params.size());

    log.debug(
        "Compiled parameter details: {}",
        params.entrySet().stream().map(Object::toString).toList());
  }

  private void recordMetrics(String provenanceCode, String endpointName, long startNanos) {
    if (provenanceCode != null) {
      long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
      metrics.compileDuration(provenanceCode, endpointName, durationMillis);
    }
  }

  private record CompileContext(
      boolean strictMode,
      boolean repeatEnabled,
      int queryLengthLimit,
      int warnParamCount,
      int maxParamCount,
      boolean bridgeEnabled) {}

  private record CapabilityCheckResult(List<Issue> warnings, CompileResult failure) {}

  private void applyParamCountLimits(
      Map<String, String> providerParams,
      int warnParamCount,
      int maxParamCount,
      List<Issue> warnings,
      List<Issue> errors) {
    int count = providerParams.size();
    if (maxParamCount > 0 && count > maxParamCount) {
      errors.add(
          Issue.error(
              "E-PARAM-COUNT-LIMIT",
              "Parameter count exceeds hard limit",
              Map.of("max", maxParamCount, "actual", count)));
    } else if (warnParamCount > 0 && count > warnParamCount) {
      warnings.add(
          Issue.warn(
              "W-PARAM-COUNT-LIMIT",
              "Parameter count exceeds soft limit",
              Map.of("warn", warnParamCount, "actual", count)));
    }
  }

  private void bridgeQuery(
      String query,
      ProvenanceSnapshot snapshot,
      String provenanceCode,
      String endpointName,
      boolean strictMode,
      List<Issue> warnings,
      List<Issue> errors,
      Map<String, String> providerParams) {
    ProvenanceSnapshot.ApiParameter mapping = snapshot.apiParameterMap().get("query");
    if (mapping == null) {
      handleMissingQueryMapping(snapshot, provenanceCode, endpointName, warnings);
      return;
    }

    String providerName = mapping.providerParamName();
    if (providerParams.containsKey(providerName)) {
      handleQueryBridgeConflict(providerName, snapshot, errors);
      return;
    }

    applyQueryBridge(
        query,
        mapping,
        providerName,
        snapshot,
        provenanceCode,
        endpointName,
        strictMode,
        warnings,
        errors,
        providerParams);
  }

  private void handleMissingQueryMapping(
      ProvenanceSnapshot snapshot,
      String provenanceCode,
      String endpointName,
      List<Issue> warnings) {
    warnings.add(
        Issue.warn(
            "W-PARAM-MAP-MISSING",
            "Standard key lacks provider parameter mapping",
            Map.of("stdKey", "query")));
    log.warn(
        "Query bridging skipped for provenance [{}]: missing mapping for stdKey=query",
        snapshot.identity().code());
    metrics.paramMapMiss(provenanceCode, endpointName);
  }

  private void handleQueryBridgeConflict(
      String providerName, ProvenanceSnapshot snapshot, List<Issue> errors) {
    errors.add(
        Issue.error(
            "E-QUERY-BRIDGE-DUP",
            "Query bridging conflicted with already populated provider parameter",
            Map.of("param", providerName)));
    log.warn(
        "Query bridging conflict for provenance [{}]: provider param [{}] already populated",
        snapshot.identity().code(),
        providerName);
  }

  private void applyQueryBridge(
      String query,
      ProvenanceSnapshot.ApiParameter mapping,
      String providerName,
      ProvenanceSnapshot snapshot,
      String provenanceCode,
      String endpointName,
      boolean strictMode,
      List<Issue> warnings,
      List<Issue> errors,
      Map<String, String> providerParams) {
    String bridged =
        applyTransformIfNeeded(
            "query",
            providerName,
            query,
            mapping.transformCode(),
            snapshot,
            provenanceCode,
            endpointName,
            strictMode,
            warnings,
            errors);

    providerParams.put(providerName, bridged);
    log.debug("Successfully bridged query into provider parameter [{}]", providerName);
    metrics.paramMapHit(provenanceCode, endpointName);
  }

  private void mapStdKeys(
      Map<String, String> stdKeyParams,
      ProvenanceSnapshot snapshot,
      String provenanceCode,
      String endpointName,
      boolean strictMode,
      boolean repeatEnabled,
      List<Issue> warnings,
      List<Issue> errors,
      Map<String, String> providerParams) {
    if (stdKeyParams == null || stdKeyParams.isEmpty()) {
      return;
    }

    for (Map.Entry<String, String> entry : stdKeyParams.entrySet()) {
      processStdKeyEntry(
          entry,
          snapshot,
          provenanceCode,
          endpointName,
          strictMode,
          repeatEnabled,
          warnings,
          errors,
          providerParams);
    }
  }

  private void processStdKeyEntry(
      Map.Entry<String, String> entry,
      ProvenanceSnapshot snapshot,
      String provenanceCode,
      String endpointName,
      boolean strictMode,
      boolean repeatEnabled,
      List<Issue> warnings,
      List<Issue> errors,
      Map<String, String> providerParams) {
    String stdKey = entry.getKey();
    String value = entry.getValue();

    ProvenanceSnapshot.ApiParameter mapping = snapshot.apiParameterMap().get(stdKey);
    if (mapping == null) {
      handleMissingStdKeyMapping(stdKey, snapshot, provenanceCode, endpointName, warnings);
      return;
    }

    String providerName = mapping.providerParamName();
    String preparedValue = value == null ? "" : value;

    checkMultiRepeatStrategy(stdKey, providerName, mapping, snapshot, repeatEnabled, warnings);

    String finalValue =
        applyTransformIfNeeded(
            stdKey,
            providerName,
            preparedValue,
            mapping.transformCode(),
            snapshot,
            provenanceCode,
            endpointName,
            strictMode,
            warnings,
            errors);

    providerParams.put(providerName, finalValue);
    metrics.paramMapHit(provenanceCode, endpointName);
  }

  private void handleMissingStdKeyMapping(
      String stdKey,
      ProvenanceSnapshot snapshot,
      String provenanceCode,
      String endpointName,
      List<Issue> warnings) {
    warnings.add(
        Issue.warn(
            "W-PARAM-MAP-MISSING",
            "Standard key lacks provider parameter mapping",
            Map.of("stdKey", stdKey)));
    log.warn(
        "Skipping std_key [{}] for provenance [{}]: missing provider mapping",
        stdKey,
        snapshot.identity().code());
    metrics.paramMapMiss(provenanceCode, endpointName);
  }

  private void checkMultiRepeatStrategy(
      String stdKey,
      String providerName,
      ProvenanceSnapshot.ApiParameter mapping,
      ProvenanceSnapshot snapshot,
      boolean repeatEnabled,
      List<Issue> warnings) {
    ProvenanceSnapshot.FieldDefinition fieldDefinition = snapshot.fieldDictionary().get(stdKey);
    boolean isMulti =
        fieldDefinition != null
            && fieldDefinition.cardinality() == ProvenanceSnapshot.Cardinality.MULTI;

    boolean hasNoTransform = mapping.transformCode() == null || mapping.transformCode().isBlank();

    if (isMulti && repeatEnabled && hasNoTransform) {
      warnings.add(
          Issue.warn(
              "W-MULTI-REPEAT-NOTSUPPORTED",
              "MULTI repeat strategy not available; falling back to join encoding",
              Map.of("stdKey", stdKey, "param", providerName)));
      log.warn(
          "MULTI repeat requested but not yet supported: stdKey=[{}], providerParam=[{}]",
          stdKey,
          providerName);
    }
  }

  private void recordCompileErrors(List<Issue> errors) {
    if (errors == null || errors.isEmpty()) {
      return;
    }
    for (Issue error : errors) {
      if (error == null) {
        continue;
      }
      String code = error.code();
      if (code != null && !code.isBlank() && error.severity() == IssueSeverity.ERROR) {
        metrics.compileError(code);
      }
    }
  }

  private String resolveEndpoint(ProvenanceSnapshot snapshot, String fallbackEndpoint) {
    if (snapshot.operation() != null && snapshot.operation().code() != null) {
      String code = snapshot.operation().code();
      if (!code.isBlank()) {
        return code;
      }
    }
    return fallbackEndpoint(fallbackEndpoint);
  }

  private String fallbackEndpoint(String endpoint) {
    return endpoint == null || endpoint.isBlank() ? "SEARCH" : endpoint;
  }

  private String applyTransformIfNeeded(
      String stdKey,
      String providerParamName,
      String value,
      String transformCode,
      ProvenanceSnapshot snapshot,
      String provenanceCode,
      String endpointName,
      boolean strictMode,
      List<Issue> warnings,
      List<Issue> errors) {
    if (transformCode == null || transformCode.isBlank()) {
      return value;
    }

    Optional<ValueTransform> transformOpt = transformRegistry.find(transformCode);
    if (transformOpt.isEmpty()) {
      handleMissingTransform(
          transformCode, stdKey, providerParamName, strictMode, warnings, errors);
      return value;
    }

    return executeTransform(
        transformOpt.get(),
        stdKey,
        providerParamName,
        value,
        transformCode,
        snapshot,
        provenanceCode,
        endpointName);
  }

  private void handleMissingTransform(
      String transformCode,
      String stdKey,
      String providerParamName,
      boolean strictMode,
      List<Issue> warnings,
      List<Issue> errors) {
    Map<String, Object> context =
        Map.of("transformCode", transformCode, "stdKey", stdKey, "param", providerParamName);

    if (strictMode) {
      errors.add(Issue.error("E-TRANSFORM-NOTFOUND", "Transform not found", context));
    } else {
      warnings.add(Issue.warn("W-FN-OR-TRANSFORM-NOTFOUND", "Transform not found", context));
    }

    log.warn(
        "Transform [{}] not found for stdKey=[{}], providerParam=[{}]",
        transformCode,
        stdKey,
        providerParamName);
  }

  private String executeTransform(
      ValueTransform transform,
      String stdKey,
      String providerParamName,
      String value,
      String transformCode,
      ProvenanceSnapshot snapshot,
      String provenanceCode,
      String endpointName) {
    String result = transform.apply(stdKey, value, snapshot);

    log.debug(
        "Applied transform [{}] for stdKey=[{}], providerParam=[{}]: length {} -> {}",
        transformCode,
        stdKey,
        providerParamName,
        value == null ? 0 : value.length(),
        result == null ? 0 : result.length());

    metrics.transformApplied(provenanceCode, endpointName, transformCode);
    return result == null ? "" : result;
  }

  private void mergeRendererWarnings(
      List<Issue> rendererWarnings, List<Issue> warnings, List<Issue> errors, boolean strictMode) {
    if (rendererWarnings == null) {
      return;
    }
    for (Issue warn : rendererWarnings) {
      if (strictMode
          && "W-FN-OR-TRANSFORM-NOTFOUND".equals(warn.code())
          && warn.context() != null
          && warn.context().containsKey("fnCode")) {
        errors.add(Issue.error("E-FN-NOTFOUND", "Function not found", warn.context()));
      } else {
        warnings.add(warn);
      }
    }
  }

  private IssueBuckets bucketCapabilityIssues(List<Issue> issues, boolean strictMode) {
    List<Issue> warnings = new ArrayList<>();
    List<Issue> errors = new ArrayList<>();
    if (issues == null) {
      return new IssueBuckets(warnings, errors);
    }
    for (Issue issue : issues) {
      if (issue.severity() == IssueSeverity.ERROR) {
        if (!strictMode && isRelaxableError(issue)) {
          warnings.add(
              Issue.warn(
                  "W-NOT-SKIPPED",
                  "Negation skipped due to provider limitations",
                  issue.context()));
          log.warn("Downgraded NOT capability error to warning: {}", issue.code());
        } else {
          errors.add(issue);
        }
      } else {
        warnings.add(issue);
      }
    }
    return new IssueBuckets(warnings, errors);
  }

  private boolean isRelaxableError(Issue issue) {
    if (issue == null) {
      return false;
    }
    return "E-NOT-UNSUPPORTED".equals(issue.code()) || "E-NOT-OP-UNSUPPORTED".equals(issue.code());
  }

  private int resolveMaxQueryLength(CompileRequest request) {
    if (request.options().maxQueryLength() > 0) {
      return request.options().maxQueryLength();
    }
    return Math.max(0, compilerProperties.getMaxQueryLength());
  }

  private String hashQuery(String query) {
    if (query == null || query.isBlank()) {
      return "blank";
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(query.getBytes());
      StringBuilder sb = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      return "unavailable";
    }
  }

  private SnapshotRef toRef(ProvenanceSnapshot snapshot, String endpointName) {
    ProvenanceSnapshot.Identity id = snapshot.identity();
    return new SnapshotRef(
        id.provenanceId(), id.code(), endpointName, snapshot.version(), snapshot.capturedAt());
  }

  private record IssueBuckets(List<Issue> warnings, List<Issue> errors) {}
}
