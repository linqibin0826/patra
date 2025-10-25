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

/**
 * Default implementation of {@link ErrorResolutionEngine} that maps exceptions to unified error
 * codes and HTTP statuses.
 *
 * <p>Resolution order:
 *
 * <ol>
 *   <li>{@link ApplicationException}
 *   <li>{@link ErrorMappingContributor}
 *   <li>{@link HasErrorTraits}
 *   <li>Naming heuristics
 *   <li>Fallback strategy
 * </ol>
 */
@Slf4j
public class DefaultErrorResolutionEngine implements ErrorResolutionEngine {

  private static final String DEFAULT_CONTEXT = "UNKNOWN";

  /** Maps error traits to HTTP status code suffixes. */
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

  /** Maps exception class name suffixes to HTTP status code suffixes. */
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
      log.warn(
          "Received null exception, returning fallback error code",
          new IllegalStateException("Null exception trace"));
      return fallbackServerError();
    }
    return resolveWithCause(exception, 0);
  }

  /** Resolves exception recursively up to max cause depth. */
  private ErrorResolution resolveWithCause(Throwable exception, int depth) {
    if (depth > maxCauseDepth) {
      log.warn("Exceeded max cause depth {} — returning server error", maxCauseDepth);
      return createResolution("0500");
    }

    return resolveAsApplicationException(exception)
        .or(() -> resolveViaContributors(exception))
        .or(() -> resolveViaTraits(exception))
        .or(() -> resolveViaNamingHeuristic(exception))
        .or(() -> resolveCause(exception, depth))
        .orElseGet(() -> fallbackForException(exception));
  }

  /** Attempts to resolve as ApplicationException. */
  private Optional<ErrorResolution> resolveAsApplicationException(Throwable exception) {
    if (exception instanceof ApplicationException appEx) {
      ErrorCodeLike errorCode = appEx.getErrorCode();
      log.debug("Resolved via ApplicationException -> {}", errorCode.code());
      return Optional.of(new ErrorResolution(errorCode, errorCode.httpStatus()));
    }
    return Optional.empty();
  }

  /** Attempts to resolve via registered ErrorMappingContributors. */
  private Optional<ErrorResolution> resolveViaContributors(Throwable exception) {
    for (ErrorMappingContributor contributor : mappingContributors) {
      try {
        Optional<ErrorCodeLike> mapped = contributor.mapException(exception);
        if (mapped.isPresent()) {
          ErrorCodeLike code = mapped.get();
          log.debug(
              "Resolved by ErrorMappingContributor({}) -> {}",
              contributor.getClass().getSimpleName(),
              code.code());
          return Optional.of(new ErrorResolution(code, code.httpStatus()));
        }
      } catch (Exception ex) {
        log.warn(
            "ErrorMappingContributor({}) failed: {}",
            contributor.getClass().getSimpleName(),
            ex.getMessage());
      }
    }
    return Optional.empty();
  }

  /** Attempts to resolve via error traits. */
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
        log.debug("Resolved via trait {} -> {}-{}", trait, contextPrefix, codeSuffix);
        return Optional.of(createResolution(codeSuffix));
      }
    }
    return Optional.of(createResolution("0500"));
  }

  /** Attempts to resolve via class naming conventions. */
  private Optional<ErrorResolution> resolveViaNamingHeuristic(Throwable exception) {
    if (!namingHeuristicEnabled) {
      return Optional.empty();
    }
    String className = exception.getClass().getSimpleName();
    for (Map.Entry<String, String> entry : NAMING_SUFFIX_TO_CODE_MAP.entrySet()) {
      if (className.endsWith(entry.getKey())) {
        log.debug("Resolved via naming heuristic {} -> {}", className, entry.getValue());
        return Optional.of(createResolution(entry.getValue()));
      }
    }
    return Optional.empty();
  }

  /** Attempts to resolve by recursing into exception cause. */
  private Optional<ErrorResolution> resolveCause(Throwable exception, int depth) {
    Throwable cause = exception.getCause();
    if (cause != null && cause != exception) {
      return Optional.of(resolveWithCause(cause, depth + 1));
    }
    return Optional.empty();
  }

  /** Returns appropriate fallback resolution for the exception. */
  private ErrorResolution fallbackForException(Throwable exception) {
    return isClientErrorLike(exception) ? createResolution("0422") : fallbackServerError();
  }

  /** Checks if exception name suggests a client error. */
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

  /** Returns 500 Internal Server Error resolution. */
  private ErrorResolution fallbackServerError() {
    return createResolution("0500");
  }

  /**
   * Creates error resolution from HTTP status suffix.
   *
   * @param suffix HTTP status code (e.g., "0404", "0500")
   * @return error resolution with context prefix applied
   */
  private ErrorResolution createResolution(String suffix) {
    ErrorCodeLike code = SimpleErrorCode.create(contextPrefix, suffix);
    return new ErrorResolution(code, code.httpStatus());
  }
}
