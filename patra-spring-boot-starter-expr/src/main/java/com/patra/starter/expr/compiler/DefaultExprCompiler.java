package com.patra.starter.expr.compiler;

import com.patra.expr.Expr;
import com.patra.starter.expr.compiler.boot.CompilerProperties;
import com.patra.starter.expr.compiler.boot.ExprModeProperties;
import com.patra.starter.expr.compiler.check.CapabilityChecker;
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

  public DefaultExprCompiler(
      RuleSnapshotLoader snapshotLoader,
      CapabilityChecker capabilityChecker,
      ExprNormalizer normalizer,
      ExprRenderer renderer,
      TransformRegistry transformRegistry,
      CompilerProperties compilerProperties,
      ExprModeProperties modeProperties) {
    this.snapshotLoader = Objects.requireNonNull(snapshotLoader);
    this.capabilityChecker = Objects.requireNonNull(capabilityChecker);
    this.normalizer = Objects.requireNonNull(normalizer);
    this.renderer = Objects.requireNonNull(renderer);
    this.transformRegistry = Objects.requireNonNull(transformRegistry);
    this.compilerProperties = Objects.requireNonNull(compilerProperties);
    this.modeProperties = Objects.requireNonNull(modeProperties);
  }

  @Override
  public CompileResult compile(CompileRequest request) {
    Objects.requireNonNull(request, "request");

    boolean strictMode = request.options().strict() || modeProperties.isStrict();
    boolean repeatEnabled = modeProperties.getMulti().isRepeatEnabled();
    int queryLengthLimit = resolveMaxQueryLength(request);
    int warnParamCount = compilerProperties.getWarnParamCount();
    int maxParamCount = compilerProperties.getMaxParamCount();
    boolean bridgeEnabled = compilerProperties.getQueryParamBridge().isEnabled();

    ProvenanceSnapshot snapshot =
        snapshotLoader.load(request.provenance(), request.operationType(), request.endpointName());
    Expr normalized = normalizer.normalize(request.expression(), request.options().strict());

    IssueBuckets capabilityBuckets =
        bucketCapabilityIssues(
            capabilityChecker.check(normalized, snapshot, strictMode), strictMode);
    List<Issue> warnings = new ArrayList<>(capabilityBuckets.warnings());
    List<Issue> errors = new ArrayList<>(capabilityBuckets.errors());
    ValidationReport report = new ValidationReport(warnings, errors);

    if (!errors.isEmpty()) {
      log.warn(
          "Capability validation failed for provenance={}, endpoint={}, errorCount={}",
          snapshot.identity().code(),
          request.endpointName(),
          errors.size());
      return new CompileResult(
          "",
          Map.of(),
          normalized,
          report,
          toRef(snapshot, request.endpointName()),
          request.options().traceEnabled() ? new RenderTrace(List.of()) : null);
    }

    ExprRenderer.RenderOutcome outcome =
        renderer.render(normalized, snapshot, request.options().traceEnabled());

    mergeRendererWarnings(outcome.warnings(), warnings, errors, strictMode);

    String renderedQuery = outcome.query() == null ? "" : outcome.query();

    if (queryLengthLimit > 0 && renderedQuery.length() > queryLengthLimit) {
      errors.add(
          Issue.error(
              "E-QUERY-LEN-MAX",
              "Rendered query exceeds length budget",
              Map.of("max", queryLengthLimit, "actual", renderedQuery.length())));
      ValidationReport finalReport = new ValidationReport(warnings, errors);
      log.warn(
          "Query length exceeded for provenance={}, endpoint={}, limit={}, actual={}",
          snapshot.identity().code(),
          request.endpointName(),
          queryLengthLimit,
          renderedQuery.length());
      return new CompileResult(
          "",
          Map.of(),
          normalized,
          finalReport,
          toRef(snapshot, request.endpointName()),
          outcome.trace());
    }

    LinkedHashMap<String, String> providerParams = new LinkedHashMap<>();
    mapStdKeys(
        outcome.params(), snapshot, strictMode, repeatEnabled, warnings, errors, providerParams);

    if (bridgeEnabled && !renderedQuery.isBlank()) {
      bridgeQuery(renderedQuery, snapshot, strictMode, warnings, errors, providerParams);
    }

    applyParamCountLimits(providerParams, warnParamCount, maxParamCount, warnings, errors);

    ValidationReport finalReport = new ValidationReport(warnings, errors);

    if (!errors.isEmpty()) {
      log.warn(
          "Compilation produced errors for provenance={}, endpoint={}, errorCount={}",
          snapshot.identity().code(),
          request.endpointName(),
          errors.size());
      return new CompileResult(
          "",
          Map.of(),
          normalized,
          finalReport,
          toRef(snapshot, request.endpointName()),
          outcome.trace());
    }

    log.info(
        "Compiled expr for provenance={}, endpoint={}, queryHash={}, queryLen={}, paramCount={}",
        snapshot.identity().code(),
        request.endpointName(),
        hashQuery(renderedQuery),
        renderedQuery.length(),
        providerParams.size());

    log.debug(
        "Compiled params detail: {}",
        providerParams.entrySet().stream().map(Object::toString).toList());

    return new CompileResult(
        renderedQuery,
        providerParams,
        normalized,
        finalReport,
        toRef(snapshot, request.endpointName()),
        outcome.trace());
  }

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
      boolean strictMode,
      List<Issue> warnings,
      List<Issue> errors,
      Map<String, String> providerParams) {
    ProvenanceSnapshot.ApiParameter mapping = snapshot.apiParameterMap().get("query");
    if (mapping == null) {
      warnings.add(
          Issue.warn(
              "W-PARAM-MAP-MISSING",
              "Standard key lacks provider parameter mapping",
              Map.of("stdKey", "query")));
      log.warn(
          "Query bridging skipped - missing mapping for stdKey=query, provenance={}",
          snapshot.identity().code());
      return;
    }
    String providerName = mapping.providerParamName();
    if (providerParams.containsKey(providerName)) {
      log.debug("Query bridging skipped - provider param already set: {}", providerName);
      return;
    }
    String bridged =
        applyTransformIfNeeded(
            "query",
            providerName,
            query,
            mapping.transformCode(),
            snapshot,
            strictMode,
            warnings,
            errors);
    providerParams.put(providerName, bridged);
    log.debug("Bridged query into provider param {}", providerName);
  }

  private void mapStdKeys(
      Map<String, String> stdKeyParams,
      ProvenanceSnapshot snapshot,
      boolean strictMode,
      boolean repeatEnabled,
      List<Issue> warnings,
      List<Issue> errors,
      Map<String, String> providerParams) {
    if (stdKeyParams == null || stdKeyParams.isEmpty()) {
      return;
    }
    for (Map.Entry<String, String> entry : stdKeyParams.entrySet()) {
      String stdKey = entry.getKey();
      String value = entry.getValue();
      ProvenanceSnapshot.ApiParameter mapping = snapshot.apiParameterMap().get(stdKey);
      if (mapping == null) {
        warnings.add(
            Issue.warn(
                "W-PARAM-MAP-MISSING",
                "Standard key lacks provider parameter mapping",
                Map.of("stdKey", stdKey)));
        log.warn(
            "Skipping std_key={} - missing provider mapping (provenance={})",
            stdKey,
            snapshot.identity().code());
        continue;
      }

      String providerName = mapping.providerParamName();
      String preparedValue = value == null ? "" : value;

      // MULTI repeat strategy placeholder: keep internal delimiter when repeat is enabled and no
      // transform is provided. Adapter layer will expand repeated params.
      ProvenanceSnapshot.FieldDefinition fieldDefinition = snapshot.fieldDictionary().get(stdKey);
      boolean multi =
          fieldDefinition != null
              && fieldDefinition.cardinality() == ProvenanceSnapshot.Cardinality.MULTI;
      if (multi
          && repeatEnabled
          && (mapping.transformCode() == null || mapping.transformCode().isBlank())) {
        log.debug(
            "MULTI repeat enabled for stdKey={}, providerParam={}, rawValue={}",
            stdKey,
            providerName,
            preparedValue);
        providerParams.put(providerName, preparedValue);
        continue;
      }

      String finalValue =
          applyTransformIfNeeded(
              stdKey,
              providerName,
              preparedValue,
              mapping.transformCode(),
              snapshot,
              strictMode,
              warnings,
              errors);
      providerParams.put(providerName, finalValue);
    }
  }

  private String applyTransformIfNeeded(
      String stdKey,
      String providerParamName,
      String value,
      String transformCode,
      ProvenanceSnapshot snapshot,
      boolean strictMode,
      List<Issue> warnings,
      List<Issue> errors) {
    if (transformCode == null || transformCode.isBlank()) {
      return value;
    }
    Optional<ValueTransform> transformOpt = transformRegistry.find(transformCode);
    if (transformOpt.isEmpty()) {
      Map<String, Object> context =
          Map.of("transformCode", transformCode, "stdKey", stdKey, "param", providerParamName);
      if (strictMode) {
        errors.add(Issue.error("E-TRANSFORM-NOTFOUND", "Transform not found", context));
      } else {
        warnings.add(Issue.warn("W-FN-OR-TRANSFORM-NOTFOUND", "Transform not found", context));
      }
      log.warn(
          "Transform code not found: code={}, stdKey={}, providerParam={}",
          transformCode,
          stdKey,
          providerParamName);
      return value;
    }
    ValueTransform transform = transformOpt.get();
    String result = transform.apply(stdKey, value, snapshot);
    log.debug(
        "Applied transform: code={}, stdKey={}, providerParam={}, beforeLen={}, afterLen={}",
        transformCode,
        stdKey,
        providerParamName,
        value == null ? 0 : value.length(),
        result == null ? 0 : result.length());
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
