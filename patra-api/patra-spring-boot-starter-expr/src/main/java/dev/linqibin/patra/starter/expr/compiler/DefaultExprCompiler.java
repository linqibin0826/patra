package dev.linqibin.patra.starter.expr.compiler;

import dev.linqibin.patra.starter.expr.compiler.boot.CompilerProperties;
import dev.linqibin.patra.starter.expr.compiler.boot.ExprModeProperties;
import dev.linqibin.patra.starter.expr.compiler.check.CapabilityChecker;
import dev.linqibin.patra.starter.expr.compiler.metrics.ExprMetrics;
import dev.linqibin.patra.starter.expr.compiler.model.CompileRequest;
import dev.linqibin.patra.starter.expr.compiler.model.CompileResult;
import dev.linqibin.patra.starter.expr.compiler.model.Issue;
import dev.linqibin.patra.starter.expr.compiler.model.IssueSeverity;
import dev.linqibin.patra.starter.expr.compiler.model.RenderTrace;
import dev.linqibin.patra.starter.expr.compiler.model.SnapshotRef;
import dev.linqibin.patra.starter.expr.compiler.model.ValidationReport;
import dev.linqibin.patra.starter.expr.compiler.normalize.ExprNormalizer;
import dev.linqibin.patra.starter.expr.compiler.render.ExprRenderer;
import dev.linqibin.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import dev.linqibin.patra.starter.expr.compiler.snapshot.RuleSnapshotLoader;
import dev.linqibin.patra.starter.expr.compiler.transform.TransformRegistry;
import dev.linqibin.patra.starter.expr.compiler.transform.ValueTransform;
import dev.linqibin.patra.expr.Expr;
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

  /// 构造默认表达式编译器实例。
  ///
  /// @param snapshotLoader 规则快照加载器
  /// @param capabilityChecker 能力检查器
  /// @param normalizer 表达式规范化器
  /// @param renderer 表达式渲染器
  /// @param transformRegistry 值转换注册表
  /// @param compilerProperties 编译器配置属性
  /// @param modeProperties 模式配置属性
  /// @param metrics 编译指标收集器
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

  /// {@inheritDoc}
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

  /// 从编译请求中提取编译上下文。
  ///
  /// @param request 编译请求
  /// @return 编译上下文
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

  /// 编译上下文记录,封装编译过程的配置参数。
  ///
  /// @param strictMode 是否启用严格模式
  /// @param repeatEnabled 是否启用重复策略
  /// @param queryLengthLimit 查询字符串长度限制
  /// @param warnParamCount 参数数量警告阈值
  /// @param maxParamCount 参数数量硬限制
  /// @param bridgeEnabled 是否启用查询桥接
  private record CompileContext(
      boolean strictMode,
      boolean repeatEnabled,
      int queryLengthLimit,
      int warnParamCount,
      int maxParamCount,
      boolean bridgeEnabled) {}

  /// 加载数据源快照。
  ///
  /// @param request 编译请求
  /// @param ctx 编译上下文
  /// @return 数据源快照
  private ProvenanceSnapshot loadSnapshot(CompileRequest request, CompileContext ctx) {
    return snapshotLoader.load(
        request.provenance(), request.operationType(), request.endpointName());
  }

  /// 执行能力检查,验证表达式是否满足数据源能力要求。
  ///
  /// @param request 编译请求
  /// @param snapshot 数据源快照
  /// @param normalized 规范化后的表达式
  /// @param endpointName 端点名称
  /// @param ctx 编译上下文
  /// @return 能力检查结果
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
        "Provenance [{}] 在端点 [{}] 的能力验证失败，包含 {} 个错误", provenanceCode, endpointName, errors.size());
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

  /// 能力检查结果记录。
  ///
  /// @param warnings 警告列表
  /// @param failure 失败时的编译结果,成功时为 null
  private record CapabilityCheckResult(List<Issue> warnings, CompileResult failure) {}

  /// 验证查询字符串长度是否超过限制。
  ///
  /// @param outcome 渲染结果
  /// @param normalized 规范化后的表达式
  /// @param snapshot 数据源快照
  /// @param endpointName 端点名称
  /// @param provenanceCode 数据源代码
  /// @param ctx 编译上下文
  /// @return 超长时返回错误结果,否则返回 null
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

  /// 构建成功的编译结果。
  ///
  /// @param request 编译请求
  /// @param outcome 渲染结果
  /// @param normalized 规范化后的表达式
  /// @param snapshot 数据源快照
  /// @param endpointName 端点名称
  /// @param provenanceCode 数据源代码
  /// @param ctx 编译上下文
  /// @param capabilityWarnings 能力检查的警告列表
  /// @return 编译结果
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

  /// 构建数据源提供商参数映射。
  ///
  /// @param outcome 渲染结果
  /// @param snapshot 数据源快照
  /// @param endpointName 端点名称
  /// @param provenanceCode 数据源代码
  /// @param renderedQuery 渲染后的查询字符串
  /// @param warnings 警告列表(输出参数)
  /// @param errors 错误列表(输出参数)
  /// @param ctx 编译上下文
  /// @return 提供商参数映射
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

  /// 处理编译错误,构建失败结果。
  ///
  /// @param errors 错误列表
  /// @param normalized 规范化后的表达式
  /// @param snapshot 数据源快照
  /// @param endpointName 端点名称
  /// @param provenanceCode 数据源代码
  /// @param outcome 渲染结果
  /// @param providerParams 提供商参数映射
  /// @return 编译失败结果
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

  /// 记录成功编译的日志。
  ///
  /// @param provenanceCode 数据源代码
  /// @param endpointName 端点名称
  /// @param query 查询字符串
  /// @param params 参数映射
  private void logSuccessfulCompile(
      String provenanceCode, String endpointName, String query, Map<String, String> params) {
    if (log.isDebugEnabled()) {
      log.debug(
          "Compiled expression for provenance [{}] at endpoint [{}]: queryHash={}, "
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
  }

  /// 记录编译性能指标。
  ///
  /// @param provenanceCode 数据源代码
  /// @param endpointName 端点名称
  /// @param startNanos 开始时间(纳秒)
  private void recordMetrics(String provenanceCode, String endpointName, long startNanos) {
    if (provenanceCode != null) {
      long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
      metrics.compileDuration(provenanceCode, endpointName, durationMillis);
    }
  }

  /// 应用参数数量限制检查。
  ///
  /// @param providerParams 提供商参数映射
  /// @param warnParamCount 警告阈值
  /// @param maxParamCount 硬限制阈值
  /// @param warnings 警告列表(输出参数)
  /// @param errors 错误列表(输出参数)
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

  /// 桥接查询字符串到提供商参数。
  ///
  /// @param query 查询字符串
  /// @param snapshot 数据源快照
  /// @param provenanceCode 数据源代码
  /// @param endpointName 端点名称
  /// @param strictMode 是否严格模式
  /// @param warnings 警告列表(输出参数)
  /// @param errors 错误列表(输出参数)
  /// @param providerParams 提供商参数映射(输出参数)
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

  /// 处理查询映射缺失的情况。
  ///
  /// @param snapshot 数据源快照
  /// @param provenanceCode 数据源代码
  /// @param endpointName 端点名称
  /// @param warnings 警告列表(输出参数)
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

  /// 处理查询桥接参数冲突。
  ///
  /// @param providerName 提供商参数名
  /// @param snapshot 数据源快照
  /// @param errors 错误列表(输出参数)
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

  /// 应用查询桥接转换。
  ///
  /// @param query 查询字符串
  /// @param mapping API 参数映射
  /// @param providerName 提供商参数名
  /// @param snapshot 数据源快照
  /// @param provenanceCode 数据源代码
  /// @param endpointName 端点名称
  /// @param strictMode 是否严格模式
  /// @param warnings 警告列表(输出参数)
  /// @param errors 错误列表(输出参数)
  /// @param providerParams 提供商参数映射(输出参数)
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

  /// 映射标准键到提供商参数。
  ///
  /// @param stdKeyParams 标准键参数映射
  /// @param snapshot 数据源快照
  /// @param provenanceCode 数据源代码
  /// @param endpointName 端点名称
  /// @param strictMode 是否严格模式
  /// @param repeatEnabled 是否启用重复策略
  /// @param warnings 警告列表(输出参数)
  /// @param errors 错误列表(输出参数)
  /// @param providerParams 提供商参数映射(输出参数)
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

  /// 处理单个标准键条目。
  ///
  /// @param entry 标准键条目
  /// @param snapshot 数据源快照
  /// @param provenanceCode 数据源代码
  /// @param endpointName 端点名称
  /// @param strictMode 是否严格模式
  /// @param repeatEnabled 是否启用重复策略
  /// @param warnings 警告列表(输出参数)
  /// @param errors 错误列表(输出参数)
  /// @param providerParams 提供商参数映射(输出参数)
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

  /// 处理标准键映射缺失的情况。
  ///
  /// @param stdKey 标准键
  /// @param snapshot 数据源快照
  /// @param provenanceCode 数据源代码
  /// @param endpointName 端点名称
  /// @param warnings 警告列表(输出参数)
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

  /// 检查多值重复策略。
  ///
  /// @param stdKey 标准键
  /// @param providerName 提供商参数名
  /// @param mapping API 参数映射
  /// @param snapshot 数据源快照
  /// @param repeatEnabled 是否启用重复策略
  /// @param warnings 警告列表(输出参数)
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

  /// 记录编译错误到指标系统。
  ///
  /// @param errors 错误列表
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

  /// 解析端点名称。
  ///
  /// @param snapshot 数据源快照
  /// @param fallbackEndpoint 默认端点名称
  /// @return 解析后的端点名称
  private String resolveEndpoint(ProvenanceSnapshot snapshot, String fallbackEndpoint) {
    if (snapshot.operation() != null && snapshot.operation().code() != null) {
      String code = snapshot.operation().code();
      if (!code.isBlank()) {
        return code;
      }
    }
    return fallbackEndpoint(fallbackEndpoint);
  }

  /// 提供默认端点名称。
  ///
  /// @param endpoint 端点名称
  /// @return 端点名称或默认值 "SEARCH"
  private String fallbackEndpoint(String endpoint) {
    return endpoint == null || endpoint.isBlank() ? "SEARCH" : endpoint;
  }

  /// 根据需要应用值转换。
  ///
  /// @param stdKey 标准键
  /// @param providerParamName 提供商参数名
  /// @param value 原始值
  /// @param transformCode 转换代码
  /// @param snapshot 数据源快照
  /// @param provenanceCode 数据源代码
  /// @param endpointName 端点名称
  /// @param strictMode 是否严格模式
  /// @param warnings 警告列表(输出参数)
  /// @param errors 错误列表(输出参数)
  /// @return 转换后的值
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

  /// 处理转换器缺失的情况。
  ///
  /// @param transformCode 转换代码
  /// @param stdKey 标准键
  /// @param providerParamName 提供商参数名
  /// @param strictMode 是否严格模式
  /// @param warnings 警告列表(输出参数)
  /// @param errors 错误列表(输出参数)
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

  /// 执行值转换。
  ///
  /// @param transform 转换器
  /// @param stdKey 标准键
  /// @param providerParamName 提供商参数名
  /// @param value 原始值
  /// @param transformCode 转换代码
  /// @param snapshot 数据源快照
  /// @param provenanceCode 数据源代码
  /// @param endpointName 端点名称
  /// @return 转换后的值
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

  /// 合并渲染器产生的警告。
  ///
  /// @param rendererWarnings 渲染器警告列表
  /// @param warnings 警告列表(输出参数)
  /// @param errors 错误列表(输出参数)
  /// @param strictMode 是否严格模式
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

  /// 将能力检查问题分类为警告和错误。
  ///
  /// @param issues 问题列表
  /// @param strictMode 是否严格模式
  /// @return 分类后的问题桶
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

  /// 问题桶记录,分类存储警告和错误。
  ///
  /// @param warnings 警告列表
  /// @param errors 错误列表
  private record IssueBuckets(List<Issue> warnings, List<Issue> errors) {}

  /// 判断错误是否可以在非严格模式下降级为警告。
  ///
  /// @param issue 问题
  /// @return true 表示可以降级
  private boolean isRelaxableError(Issue issue) {
    if (issue == null) {
      return false;
    }
    return "E-NOT-UNSUPPORTED".equals(issue.code()) || "E-NOT-OP-UNSUPPORTED".equals(issue.code());
  }

  /// 解析最大查询长度限制。
  ///
  /// @param request 编译请求
  /// @return 最大查询长度
  private int resolveMaxQueryLength(CompileRequest request) {
    if (request.options().maxQueryLength() > 0) {
      return request.options().maxQueryLength();
    }
    return Math.max(0, compilerProperties.getMaxQueryLength());
  }

  /// 计算查询字符串的 SHA-256 哈希值。
  ///
  /// @param query 查询字符串
  /// @return 哈希值的十六进制表示
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

  /// 将数据源快照转换为引用对象。
  ///
  /// @param snapshot 数据源快照
  /// @param endpointName 端点名称
  /// @return 快照引用
  private SnapshotRef toRef(ProvenanceSnapshot snapshot, String endpointName) {
    ProvenanceSnapshot.Identity id = snapshot.identity();
    return new SnapshotRef(
        id.provenanceId(), id.code(), endpointName, snapshot.version(), snapshot.capturedAt());
  }
}
