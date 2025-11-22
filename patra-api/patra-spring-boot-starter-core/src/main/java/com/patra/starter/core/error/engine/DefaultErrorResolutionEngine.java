package com.patra.starter.core.error.engine;

import com.patra.common.error.ApplicationException;
import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.model.SimpleErrorCode;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/// {@link ErrorResolutionEngine} 的默认实现，将异常映射到统一的错误码和 HTTP 状态。
///
/// 错误解析顺序：
///
///
@Slf4j
public class DefaultErrorResolutionEngine implements ErrorResolutionEngine {

  private static final String DEFAULT_CONTEXT = "UNKNOWN";

  /// 将错误特征映射到 HTTP 状态码后缀。
  private static final Map<ErrorTrait, String> TRAIT_TO_CODE_MAP =
      Map.ofEntries(
          Map.entry(ErrorTrait.NOT_FOUND, "0404"),
          Map.entry(ErrorTrait.CONFLICT, "0409"),
          Map.entry(ErrorTrait.RULE_VIOLATION, "0422"),
          Map.entry(ErrorTrait.QUOTA_EXCEEDED, "0429"),
          Map.entry(ErrorTrait.UNAUTHORIZED, "0401"),
          Map.entry(ErrorTrait.FORBIDDEN, "0403"),
          Map.entry(ErrorTrait.TIMEOUT, "0504"),
          Map.entry(ErrorTrait.DEP_UNAVAILABLE, "0503"));

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

  public DefaultErrorResolutionEngine(
      ErrorProperties errorProperties, List<ErrorMappingContributor> mappingContributors) {
    String prefix = errorProperties.getContextPrefix();
    this.contextPrefix = (prefix == null || prefix.isBlank()) ? DEFAULT_CONTEXT : prefix;
    this.mappingContributors = mappingContributors;
    this.maxCauseDepth = errorProperties.getEngine().getMaxCauseDepth();
    this.traitMappingEnabled = errorProperties.getEngine().isEnableTraitMapping();
    this.namingHeuristicEnabled = errorProperties.getEngine().isEnableNamingHeuristic();
  }

  @Override
  public ErrorResolution resolve(Throwable exception) {
    if (exception == null) {
      log.warn("接收到 null 异常，返回回退错误码", new IllegalStateException("Null 异常追踪"));
      return fallbackServerError();
    }
    return resolveWithCause(exception, 0);
  }

  /// 递归解析异常，直到达到最大原因链深度。
  private ErrorResolution resolveWithCause(Throwable exception, int depth) {
    if (depth > maxCauseDepth) {
      log.warn("超过最大原因链深度 {} — 返回服务器错误", maxCauseDepth);
      return createResolution("0500");
    }

    return resolveAsApplicationException(exception)
        .or(() -> resolveViaContributors(exception))
        .or(() -> resolveViaTraits(exception))
        .or(() -> resolveViaNamingHeuristic(exception))
        .or(() -> resolveCause(exception, depth))
        .orElseGet(() -> fallbackForException(exception));
  }

  /// 尝试解析为 ApplicationException。
  private Optional<ErrorResolution> resolveAsApplicationException(Throwable exception) {
    if (exception instanceof ApplicationException appEx) {
      ErrorCodeLike errorCode = appEx.getErrorCode();
      log.debug("通过 ApplicationException 解析 -> {}", errorCode.code());
      return Optional.of(new ErrorResolution(errorCode, errorCode.httpStatus()));
    }
    return Optional.empty();
  }

  /// 尝试通过已注册的 ErrorMappingContributor 解析。
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
          return Optional.of(new ErrorResolution(code, code.httpStatus()));
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
        return Optional.of(createResolution(codeSuffix));
      }
    }
    return Optional.of(createResolution("0500"));
  }

  /// 尝试通过类命名约定解析。
  private Optional<ErrorResolution> resolveViaNamingHeuristic(Throwable exception) {
    if (!namingHeuristicEnabled) {
      return Optional.empty();
    }
    String className = exception.getClass().getSimpleName();
    for (Map.Entry<String, String> entry : NAMING_SUFFIX_TO_CODE_MAP.entrySet()) {
      if (className.endsWith(entry.getKey())) {
        log.debug("通过命名启发式 {} 解析 -> {}", className, entry.getValue());
        return Optional.of(createResolution(entry.getValue()));
      }
    }
    return Optional.empty();
  }

  /// 尝试通过递归进入异常原因解析。
  private Optional<ErrorResolution> resolveCause(Throwable exception, int depth) {
    Throwable cause = exception.getCause();
    if (cause != null && cause != exception) {
      return Optional.of(resolveWithCause(cause, depth + 1));
    }
    return Optional.empty();
  }

  /// 为异常返回适当的回退解析。
  private ErrorResolution fallbackForException(Throwable exception) {
    return isClientErrorLike(exception) ? createResolution("0422") : fallbackServerError();
  }

  /// 检查异常名称是否暗示客户端错误。
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
  private ErrorResolution fallbackServerError() {
    return createResolution("0500");
  }

  /// 从 HTTP 状态码后缀创建错误解析。
  ///
  /// @param suffix HTTP 状态码（例如，"0404"、"0500"）
  /// @return 应用了上下文前缀的错误解析
  private ErrorResolution createResolution(String suffix) {
    ErrorCodeLike code = SimpleErrorCode.create(contextPrefix, suffix);
    return new ErrorResolution(code, code.httpStatus());
  }
}
