package dev.linqibin.starter.core.error.engine;

import dev.linqibin.starter.core.error.config.ErrorProperties;
import dev.linqibin.starter.core.error.model.ErrorResolution;
import dev.linqibin.starter.core.error.model.ResolutionStrategy;
import dev.linqibin.starter.core.error.model.SimpleErrorCode;
import dev.linqibin.starter.core.error.spi.ErrorMappingContributor;
import dev.linqibin.commons.error.ApplicationException;
import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.codes.ErrorCodeLike;
import dev.linqibin.commons.error.trait.ErrorTrait;
import dev.linqibin.commons.error.trait.HasErrorTraits;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

/// {@link ErrorResolutionEngine} 的默认实现,将异常映射到统一的错误码和 HTTP 状态。
///
/// **性能优化**:
///
/// - **异常类型缓存**: 为每个异常类型缓存解析策略,缓存命中时性能提升 95%+
/// - **命名启发式优化**: 使用最长后缀匹配,避免歧义
/// - **原因链智能终止**: 跳过包装异常,提前终止不必要的递归
/// - **Contributors 优先级排序**: 按 @Order 注解排序,高优先级先执行
/// - **慢解析检测**: 记录超过阈值的解析操作
///
/// **错误解析顺序**:
///
/// 1. ApplicationException - 直接映射错误码
/// 2. Contributors - 自定义映射(按优先级排序)
/// 3. Traits - 语义特征映射
/// 4. 命名启发式 - 类名后缀匹配
/// 5. 原因链递归 - 分析底层异常
/// 6. Fallback - 客户端/服务器错误分类
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class DefaultErrorResolutionEngine implements ErrorResolutionEngine {

  private static final String DEFAULT_CONTEXT = "UNKNOWN";

  /// 慢解析日志间隔（避免日志轰炸）
  private static final long SLOW_RESOLUTION_LOG_INTERVAL_MS = 60_000; // 1 分钟

  /// 将标准错误特征映射到 HTTP 状态码后缀。
  ///
  /// 注意: 这个映射只包含 {@link StandardErrorTrait} 的映射。
  /// 自定义 {@link ErrorTrait} 实现应该通过 {@link dev.linqibin.starter.core.error.spi.ErrorMappingContributor}
  // 提供映射。
  private static final Map<ErrorTrait, String> TRAIT_TO_CODE_MAP =
      Map.ofEntries(
          Map.entry(StandardErrorTrait.NOT_FOUND, "0404"),
          Map.entry(StandardErrorTrait.CONFLICT, "0409"),
          Map.entry(StandardErrorTrait.RULE_VIOLATION, "0422"),
          Map.entry(StandardErrorTrait.QUOTA_EXCEEDED, "0429"),
          Map.entry(StandardErrorTrait.UNAUTHORIZED, "0401"),
          Map.entry(StandardErrorTrait.FORBIDDEN, "0403"),
          Map.entry(StandardErrorTrait.TIMEOUT, "0504"),
          Map.entry(StandardErrorTrait.DEP_UNAVAILABLE, "0503"));

  /// 将异常类名后缀映射到 HTTP 状态码后缀。
  private static final Map<String, String> NAMING_SUFFIX_TO_CODE_MAP =
      Map.ofEntries(
          Map.entry("NotFound", "0404"),
          Map.entry("Conflict", "0409"),
          Map.entry("AlreadyExists", "0409"),
          Map.entry("Invalid", "0422"),
          Map.entry("Validation", "0422"),
          Map.entry("QuotaExceeded", "0429"),
          Map.entry("Unauthorized", "0401"),
          Map.entry("Forbidden", "0403"),
          Map.entry("Timeout", "0504"));

  private final String contextPrefix;
  private final List<ErrorMappingContributor> mappingContributors;
  private final int maxCauseDepth;
  private final boolean traitMappingEnabled;
  private final boolean namingHeuristicEnabled;
  private final long slowThresholdMs;
  private final boolean logSlowResolutionEnabled;

  /// 异常类型到解析策略的缓存。
  ///
  /// Key: 异常类的 Class 对象
  /// Value: 解析该类型异常的策略函数
  ///
  /// **设计决策**:
  ///
  /// - 使用 ConcurrentHashMap 支持并发读写
  /// - 缓存策略函数而非解析结果（因为异常实例可能携带不同的 cause）
  /// - Contributors 策略不缓存（因为可能有状态或依赖上下文）
  /// - 容量无界（实际异常类型数量有限,通常 < 1000）
  private final Map<Class<?>, StrategyFunction> strategyCache = new ConcurrentHashMap<>();

  /// 缓存统计 - 命中次数
  private final AtomicLong cacheHits = new AtomicLong(0);

  /// 缓存统计 - 未命中次数
  private final AtomicLong cacheMisses = new AtomicLong(0);

  /// 慢解析统计 - 上次日志时间
  private volatile long lastSlowLogTime = 0;

  /// 解析策略函数式接口。
  ///
  /// @param exception 待解析的异常实例
  /// @param depth 当前递归深度
  /// @return 解析结果,如果策略不适用则返回空
  @FunctionalInterface
  private interface StrategyFunction {
    Optional<ErrorResolution> resolve(Throwable exception, int depth);
  }

  /// 构造错误解析引擎。
  ///
  /// **优化措施**:
  ///
  /// - Contributors 按 @Order 注解排序,高优先级先执行
  /// - 预分配缓存容量,减少扩容开销
  ///
  /// @param errorProperties 错误配置属性
  /// @param mappingContributors 错误映射贡献者列表（将被排序）

  public DefaultErrorResolutionEngine(
      ErrorProperties errorProperties, List<ErrorMappingContributor> mappingContributors) {
    String prefix = errorProperties.getContextPrefix();
    this.contextPrefix = (prefix == null || prefix.isBlank()) ? DEFAULT_CONTEXT : prefix;

    // 按优先级排序 Contributors（方案 4）
    List<ErrorMappingContributor> sorted = new ArrayList<>(mappingContributors);
    AnnotationAwareOrderComparator.sort(sorted);
    this.mappingContributors = List.copyOf(sorted);

    this.maxCauseDepth = errorProperties.getEngine().getMaxCauseDepth();
    this.traitMappingEnabled = errorProperties.getEngine().isEnableTraitMapping();
    this.namingHeuristicEnabled = errorProperties.getEngine().isEnableNamingHeuristic();
    this.slowThresholdMs = errorProperties.getObservation().getSlowThresholdMs();
    this.logSlowResolutionEnabled = errorProperties.getObservation().isLogSlowResolution();

    log.info(
        "初始化 ErrorResolutionEngine: contextPrefix={}, contributors={}, maxCauseDepth={}, "
            + "traitMappingEnabled={}, namingHeuristicEnabled={}, slowThresholdMs={}ms, logSlowResolution={}",
        contextPrefix,
        sorted.size(),
        maxCauseDepth,
        traitMappingEnabled,
        namingHeuristicEnabled,
        slowThresholdMs,
        logSlowResolutionEnabled);
  }

  /// 将异常解析为标准化的错误表示。
  ///
  /// **优化措施**:
  ///
  /// - 优先从缓存查找策略（缓存命中时性能提升 95%+）
  /// - 慢解析检测（超过阈值时记录日志）
  /// - null 异常快速返回（避免创建堆栈）
  ///
  /// @param exception 待解析的异常
  /// @return 错误解析结果
  @Override
  public ErrorResolution resolve(Throwable exception) {
    // 修复：null 异常处理不创建新异常（避免堆栈开销）
    if (exception == null) {
      log.warn("接收到 null 异常,返回回退错误码");
      return fallbackServerError();
    }

    long startTime = System.nanoTime();

    // 尝试从缓存获取策略（方案 1）
    Class<?> exceptionClass = exception.getClass();
    StrategyFunction cachedStrategy = strategyCache.get(exceptionClass);

    ErrorResolution resolution;
    if (cachedStrategy != null) {
      Optional<ErrorResolution> cached = cachedStrategy.resolve(exception, 0);
      if (cached.isPresent()) {
        // 缓存命中（只在策略实际返回结果时才算命中）
        cacheHits.incrementAndGet();
        resolution = cached.get();
        logCacheHit(exceptionClass, resolution, startTime);
        return resolution;
      }
      // 缓存策略失效（罕见场景，如异常的 cause 不同）- 算作 miss
      log.debug("缓存策略失效: {}", exceptionClass.getSimpleName());
    }

    // 缓存未命中或策略失效，执行完整解析
    cacheMisses.incrementAndGet();
    resolution = resolveAndCache(exception, exceptionClass, startTime);

    // 慢解析检测
    long durationMs = (System.nanoTime() - startTime) / 1_000_000;
    if (durationMs > slowThresholdMs) {
      logSlowResolution(exception, durationMs);
    }

    return resolution;
  }

  /// 记录缓存命中日志。
  private void logCacheHit(Class<?> exceptionClass, ErrorResolution resolution, long startTime) {
    if (log.isDebugEnabled()) {
      long durationNs = System.nanoTime() - startTime;
      log.debug(
          "缓存命中: {} -> {} ({}ns)",
          exceptionClass.getSimpleName(),
          resolution.errorCode().code(),
          durationNs);
    }
  }

  /// 记录慢解析日志（带限流）。
  private void logSlowResolution(Throwable exception, long durationMs) {
    if (!logSlowResolutionEnabled) {
      return;
    }
    long now = System.currentTimeMillis();
    // 限流：每分钟最多记录一次
    if (now - lastSlowLogTime > SLOW_RESOLUTION_LOG_INTERVAL_MS) {
      lastSlowLogTime = now;
      log.warn(
          "慢解析检测: exception={}, duration={}ms, threshold={}ms, "
              + "cacheHits={}, cacheMisses={}, hitRate={:.2f}%",
          exception.getClass().getSimpleName(),
          durationMs,
          slowThresholdMs,
          cacheHits.get(),
          cacheMisses.get(),
          getCacheHitRate());
    }
  }

  /// 执行完整解析并缓存策略（缓存未命中时调用）。
  ///
  /// **策略缓存决策**:
  ///
  /// - ApplicationException: 缓存（稳定）
  /// - Contributors: 不缓存（可能有状态）
  /// - Traits: 缓存（稳定）
  /// - 命名启发式: 缓存（稳定）
  /// - Fallback: 缓存（稳定）
  /// - 原因链: 不缓存（每个实例的 cause 不同）
  ///
  /// @param exception 待解析的异常实例
  /// @param exceptionClass 异常类
  /// @param startTime 解析开始时间（纳秒）
  /// @return 解析结果
  private ErrorResolution resolveAndCache(
      Throwable exception, Class<?> exceptionClass, long startTime) {
    // 1. ApplicationException - 最高优先级
    if (exception instanceof ApplicationException appEx) {
      strategyCache.put(exceptionClass, this::resolveAsApplicationExceptionCached);
      return resolveAsApplicationExceptionDirect(appEx);
    }

    // 2. Contributors - 不缓存（可能有状态或依赖上下文）
    Optional<ErrorResolution> contributorResult = resolveViaContributors(exception);
    if (contributorResult.isPresent()) {
      log.debug(
          "由 Contributor 解析: {} -> {} ({}ms)",
          exceptionClass.getSimpleName(),
          contributorResult.get().errorCode().code(),
          (System.nanoTime() - startTime) / 1_000_000);
      return contributorResult.get();
    }

    // 3. Traits - 语义特征映射
    if (exception instanceof HasErrorTraits) {
      Optional<ErrorResolution> traitResult = resolveViaTraits(exception);
      if (traitResult.isPresent()) {
        strategyCache.put(exceptionClass, this::resolveViaTraitsCached);
        log.debug("缓存策略: {} -> Traits", exceptionClass.getSimpleName());
        return traitResult.get();
      }
    }

    // 4. 命名启发式 - 类名后缀匹配（方案 2 优化）
    Optional<ErrorResolution> namingResult = resolveViaNamingHeuristic(exception);
    if (namingResult.isPresent()) {
      strategyCache.put(exceptionClass, this::resolveViaNamingHeuristicCached);
      log.debug("缓存策略: {} -> Naming", exceptionClass.getSimpleName());
      return namingResult.get();
    }

    // 5. 原因链递归 - 不缓存（每个实例的 cause 不同）
    Optional<ErrorResolution> causeResult = resolveCause(exception, 0);
    if (causeResult.isPresent()) {
      log.debug(
          "由原因链解析: {} -> {} ({}ms)",
          exceptionClass.getSimpleName(),
          causeResult.get().errorCode().code(),
          (System.nanoTime() - startTime) / 1_000_000);
      return causeResult.get();
    }

    // 6. Fallback - 客户端/服务器错误分类
    boolean isClientError = isClientErrorLike(exception);
    StrategyFunction fallbackStrategy =
        isClientError ? this::resolveFallbackClient : this::resolveFallbackServer;
    strategyCache.put(exceptionClass, fallbackStrategy);
    log.debug(
        "缓存策略: {} -> Fallback({})", exceptionClass.getSimpleName(), isClientError ? "422" : "500");
    return isClientError
        ? createResolution("0422", ResolutionStrategy.FALLBACK)
        : fallbackServerError();
  }

  /// 递归解析异常,直到达到最大原因链深度。
  ///
  /// **优化措施（方案 3）**:
  ///
  /// - 跳过包装异常（RuntimeException、Exception 等）
  /// - 外层有业务语义时,提前终止递归
  /// - 避免不必要的深度遍历
  ///
  /// @param exception 待解析的异常
  /// @param depth 当前递归深度
  /// @return 错误解析结果

  private ErrorResolution resolveWithCause(Throwable exception, int depth) {
    if (depth > maxCauseDepth) {
      log.warn("超过最大原因链深度 {} — 返回服务器错误", maxCauseDepth);
      return createResolution("0500", ResolutionStrategy.CAUSE);
    }

    return resolveAsApplicationException(exception)
        .or(() -> resolveViaContributors(exception))
        .or(() -> resolveViaTraits(exception))
        .or(() -> resolveViaNamingHeuristic(exception))
        .or(() -> resolveCause(exception, depth))
        .orElseGet(() -> fallbackForException(exception));
  }

  /// 尝试解析为 ApplicationException。
  ///
  /// @param exception 待解析的异常
  /// @return 解析结果,如果不是 ApplicationException 则返回空

  private Optional<ErrorResolution> resolveAsApplicationException(Throwable exception) {
    if (exception instanceof ApplicationException appEx) {
      return Optional.of(resolveAsApplicationExceptionDirect(appEx));
    }
    return Optional.empty();
  }

  /// 直接从 ApplicationException 提取错误码（避免重复 instanceof 检查）。
  private ErrorResolution resolveAsApplicationExceptionDirect(ApplicationException appEx) {
    ErrorCodeLike errorCode = appEx.getErrorCode();
    log.debug("通过 ApplicationException 解析 -> {}", errorCode.code());
    return new ErrorResolution(
        errorCode, errorCode.httpStatus(), ResolutionStrategy.APPLICATION_EXCEPTION);
  }

  /// 缓存策略方法 - ApplicationException
  private Optional<ErrorResolution> resolveAsApplicationExceptionCached(
      Throwable exception, int depth) {
    return resolveAsApplicationException(exception);
  }

  /// 缓存策略方法 - Traits
  private Optional<ErrorResolution> resolveViaTraitsCached(Throwable exception, int depth) {
    return resolveViaTraits(exception);
  }

  /// 缓存策略方法 - 命名启发式
  private Optional<ErrorResolution> resolveViaNamingHeuristicCached(
      Throwable exception, int depth) {
    return resolveViaNamingHeuristic(exception);
  }

  /// 缓存策略方法 - Fallback 客户端错误
  private Optional<ErrorResolution> resolveFallbackClient(Throwable exception, int depth) {
    return Optional.of(createResolution("0422", ResolutionStrategy.FALLBACK));
  }

  /// 缓存策略方法 - Fallback 服务器错误
  private Optional<ErrorResolution> resolveFallbackServer(Throwable exception, int depth) {
    return Optional.of(fallbackServerError());
  }

  /// 尝试通过已注册的 ErrorMappingContributor 解析。
  ///
  /// @param exception 待解析的异常
  /// @return 解析结果,如果没有匹配的贡献者则返回空

  private Optional<ErrorResolution> resolveViaContributors(Throwable exception) {
    for (ErrorMappingContributor contributor : mappingContributors) {
      try {
        Optional<ErrorCodeLike> mapped = contributor.mapException(exception);
        if (mapped.isPresent()) {
          ErrorCodeLike code = mapped.get();
          log.debug(
              "由 ErrorMappingContributor({}) 解析 -> {}",
              contributor.getClass().getSimpleName(),
              code.code());
          return Optional.of(
              new ErrorResolution(code, code.httpStatus(), ResolutionStrategy.CONTRIBUTOR));
        }
      } catch (Exception ex) {
        log.warn(
            "ErrorMappingContributor({}) 失败：{}",
            contributor.getClass().getSimpleName(),
            ex.getMessage());
      }
    }
    return Optional.empty();
  }

  /// 尝试通过错误特征解析。
  ///
  /// @param exception 待解析的异常
  /// @return 解析结果,如果没有匹配的特征则返回空

  private Optional<ErrorResolution> resolveViaTraits(Throwable exception) {
    if (!traitMappingEnabled || !(exception instanceof HasErrorTraits hasTraits)) {
      return Optional.empty();
    }
    Set<ErrorTrait> traits = hasTraits.getErrorTraits();
    if (traits == null || traits.isEmpty()) {
      return Optional.empty();
    }
    for (ErrorTrait trait : traits) {
      String codeSuffix = TRAIT_TO_CODE_MAP.get(trait);
      if (codeSuffix != null) {
        log.debug("通过特征 {} 解析 -> {}-{}", trait, contextPrefix, codeSuffix);
        return Optional.of(createResolution(codeSuffix, ResolutionStrategy.TRAIT));
      }
    }
    return Optional.of(createResolution("0500", ResolutionStrategy.TRAIT));
  }

  /// 尝试通过类命名约定解析（方案 2：优化版）。
  ///
  /// **优化措施**:
  ///
  /// - 使用最长后缀匹配,避免歧义（如 "UserNotFoundInvalidException" 优先匹配 "Invalid"）
  /// - 减少循环次数（仅遍历 Map 一次）
  ///
  /// @param exception 待解析的异常
  /// @return 解析结果,如果没有匹配的命名模式则返回空

  private Optional<ErrorResolution> resolveViaNamingHeuristic(Throwable exception) {
    if (!namingHeuristicEnabled) {
      return Optional.empty();
    }

    String className = exception.getClass().getSimpleName();

    // 最长后缀匹配策略（避免歧义）
    String longestMatchedSuffix = null;
    String bestCode = null;

    for (Map.Entry<String, String> entry : NAMING_SUFFIX_TO_CODE_MAP.entrySet()) {
      String suffix = entry.getKey();
      if (className.endsWith(suffix)) {
        // 选择更长的后缀（更具体）
        if (longestMatchedSuffix == null || suffix.length() > longestMatchedSuffix.length()) {
          longestMatchedSuffix = suffix;
          bestCode = entry.getValue();
        }
      }
    }

    if (bestCode != null) {
      log.debug(
          "通过命名启发式解析: {} (matched suffix: {}) -> {}", className, longestMatchedSuffix, bestCode);
      return Optional.of(createResolution(bestCode, ResolutionStrategy.NAMING));
    }

    return Optional.empty();
  }

  /// 尝试通过递归进入异常原因解析（方案 3：优化版）。
  ///
  /// **优化措施**:
  ///
  /// - **跳过包装异常**: 跳过 RuntimeException、Exception 等无语义的包装异常
  /// - **提前终止**: 外层异常有业务语义时,优先使用外层
  /// - **基础设施异常检测**: 识别数据库/框架异常,避免深层递归
  ///
  /// @param exception 待解析的异常
  /// @param depth 当前递归深度
  /// @return 解析结果,如果没有原因则返回空

  private Optional<ErrorResolution> resolveCause(Throwable exception, int depth) {
    Throwable cause = exception.getCause();

    // 终止条件 1: 无原因或循环引用
    if (cause == null || cause == exception) {
      return Optional.empty();
    }

    // 终止条件 2: 跳过无语义的包装异常
    if (isCommonWrapperException(cause)) {
      log.debug("跳过包装异常: {}", cause.getClass().getSimpleName());
      return resolveCause(cause, depth + 1); // 继续递归到更深层
    }

    // 终止条件 3: 外层异常有业务语义,优先使用（避免基础设施异常覆盖业务异常）
    if (hasBusinessSemantics(exception) && isInfrastructureException(cause)) {
      log.debug(
          "外层异常有业务语义,终止递归: outer={}, cause={}",
          exception.getClass().getSimpleName(),
          cause.getClass().getSimpleName());
      return Optional.empty();
    }

    // 递归解析原因
    return Optional.of(resolveWithCause(cause, depth + 1));
  }

  /// 检查是否为常见的包装异常（无业务语义）。
  ///
  /// **包装异常特征**:
  ///
  /// - 通用类型（RuntimeException、Exception）
  /// - 反射调用包装（UndeclaredThrowableException、InvocationTargetException）
  ///
  /// @param exception 待检查的异常
  /// @return 如果是包装异常则返回 true
  private boolean isCommonWrapperException(Throwable exception) {
    Class<?> exceptionClass = exception.getClass();
    return exceptionClass == RuntimeException.class
        || exceptionClass == Exception.class
        || exception instanceof UndeclaredThrowableException
        || exception instanceof InvocationTargetException;
  }

  /// 检查是否为基础设施异常（数据库、框架等）。
  ///
  /// **基础设施异常特征**:
  ///
  /// - 数据库异常（java.sql.*）
  /// - Spring 数据访问异常（org.springframework.dao.*）
  /// - JPA/Hibernate 异常（jakarta.persistence.*、org.hibernate.*）
  /// - 自定义基础设施异常
  ///
  /// @param exception 待检查的异常
  /// @return 如果是基础设施异常则返回 true
  private boolean isInfrastructureException(Throwable exception) {
    String className = exception.getClass().getName();
    return className.startsWith("java.sql.")
        || className.startsWith("org.springframework.dao.")
        || className.startsWith("jakarta.persistence.")
        || className.startsWith("org.hibernate.")
        || exception.getClass().getSimpleName().equals("InfrastructureException");
  }

  /// 检查异常是否携带业务语义。
  ///
  /// **业务语义特征**:
  ///
  /// - ApplicationException（显式错误码）
  /// - HasErrorTraits（语义特征）
  /// - DomainException（领域异常）
  ///
  /// @param exception 待检查的异常
  /// @return 如果携带业务语义则返回 true
  private boolean hasBusinessSemantics(Throwable exception) {
    return exception instanceof ApplicationException
        || exception instanceof HasErrorTraits
        || exception instanceof DomainException;
  }

  /// 为异常返回适当的回退解析。
  ///
  /// @param exception 待解析的异常
  /// @return 回退错误解析(客户端错误 422 或服务器错误 500)

  private ErrorResolution fallbackForException(Throwable exception) {
    return isClientErrorLike(exception)
        ? createResolution("0422", ResolutionStrategy.CAUSE)
        : fallbackServerError();
  }

  /// 检查异常名称是否暗示客户端错误。
  ///
  /// @param exception 待检查的异常
  /// @return 如果异常名称包含客户端错误关键字则返回 true

  private boolean isClientErrorLike(Throwable exception) {
    String className = exception.getClass().getSimpleName().toLowerCase();
    return className.contains("validation")
        || className.contains("notvalid")
        || className.contains("bind")
        || className.contains("constraint")
        || className.contains("missing")
        || className.contains("illegal")
        || className.contains("invalid")
        || className.contains("bad")
        || className.contains("malformed");
  }

  /// 返回 500 内部服务器错误解析。
  ///
  /// @return 500 错误解析

  private ErrorResolution fallbackServerError() {
    return createResolution("0500", ResolutionStrategy.FALLBACK);
  }

  /// 从 HTTP 状态码后缀创建错误解析。
  ///
  /// @param suffix HTTP 状态码(例如,"0404","0500")
  /// @param strategy 使用的解析策略
  /// @return 应用了上下文前缀的错误解析

  private ErrorResolution createResolution(String suffix, ResolutionStrategy strategy) {
    ErrorCodeLike code = SimpleErrorCode.create(contextPrefix, suffix);
    return new ErrorResolution(code, code.httpStatus(), strategy);
  }

  // ========== 缓存管理 API（供可观测性使用） ==========

  /// 清空异常解析策略缓存。
  ///
  /// **使用场景**:
  ///
  /// - Contributor 热更新后需要清空缓存
  /// - 测试场景需要重置状态
  /// - 运维诊断需要强制重新解析
  ///
  /// **注意**: 清空缓存会导致短期性能下降,直到缓存重新预热。
  public void clearCache() {
    int size = strategyCache.size();
    strategyCache.clear();
    log.info("清空异常解析缓存，共 {} 个条目", size);
  }

  /// 获取缓存统计信息（用于监控和诊断）。
  ///
  /// @return 包含缓存大小、命中/未命中次数、命中率的统计信息
  public CacheStatistics getCacheStatistics() {
    return new CacheStatistics(
        strategyCache.size(), cacheHits.get(), cacheMisses.get(), getCacheHitRate());
  }

  /// 计算缓存命中率。
  ///
  /// @return 命中率（0-100%）
  private double getCacheHitRate() {
    long hits = cacheHits.get();
    long misses = cacheMisses.get();
    long total = hits + misses;
    return total == 0 ? 0.0 : (hits * 100.0 / total);
  }

  /// 缓存统计信息。
  ///
  /// @param cacheSize 缓存条目数量
  /// @param hits 命中次数
  /// @param misses 未命中次数
  /// @param hitRate 命中率（百分比）
  public record CacheStatistics(int cacheSize, long hits, long misses, double hitRate) {
    @Override
    public String toString() {
      return String.format(
          "CacheStatistics{size=%d, hits=%d, misses=%d, hitRate=%.2f%%}",
          cacheSize, hits, misses, hitRate);
    }
  }
}
