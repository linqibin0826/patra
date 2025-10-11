package com.patra.starter.core.error.engine;

import com.patra.common.error.ApplicationException;
import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

/**
 * Default implementation of {@link ErrorResolutionEngine} that maps exceptions to unified error codes and HTTP statuses.
 *
 * <p>Resolution order:</p>
 * <ol>
 *   <li>{@link ApplicationException}</li>
 *   <li>{@link ErrorMappingContributor}</li>
 *   <li>{@link HasErrorTraits}</li>
 *   <li>Naming heuristics</li>
 *   <li>Fallback strategy</li>
 * </ol>
 */
@Slf4j
public class DefaultErrorResolutionEngine implements ErrorResolutionEngine {

    private static final String DEFAULT_CONTEXT = "UNKNOWN";

    private final ErrorProperties errorProperties;
    private final List<ErrorMappingContributor> mappingContributors;
    private final int maxCauseDepth;
    private final boolean traitMappingEnabled;
    private final boolean namingHeuristicEnabled;

    public DefaultErrorResolutionEngine(ErrorProperties errorProperties,
                                        List<ErrorMappingContributor> mappingContributors) {
        this.errorProperties = errorProperties;
        this.mappingContributors = mappingContributors;
        this.maxCauseDepth = errorProperties.getEngine().getMaxCauseDepth();
        this.traitMappingEnabled = errorProperties.getEngine().isEnableTraitMapping();
        this.namingHeuristicEnabled = errorProperties.getEngine().isEnableNamingHeuristic();
    }

    @Override
    public ErrorResolution resolve(Throwable exception) {
        if (exception == null) {
            log.warn("Received null exception, returning fallback error code");
            return fallbackServerError();
        }
        return resolveWithCause(exception, 0);
    }

    private ErrorResolution resolveWithCause(Throwable exception, int depth) {
        if (depth > maxCauseDepth) {
            log.warn("Exceeded max cause depth {} — returning server error", maxCauseDepth);
            ErrorCodeLike code = createCode("0500");
            return new ErrorResolution(code, code.httpStatus());
        }

        if (exception instanceof ApplicationException appEx) {
            ErrorCodeLike errorCode = appEx.getErrorCode();
            log.debug("Resolved via ApplicationException -> {}", errorCode.code());
            return new ErrorResolution(errorCode, errorCode.httpStatus());
        }

        for (ErrorMappingContributor contributor : mappingContributors) {
            try {
                var mapped = contributor.mapException(exception);
                if (mapped.isPresent()) {
                    ErrorCodeLike code = mapped.get();
                    log.debug("Resolved by ErrorMappingContributor({}) -> {}",
                            contributor.getClass().getSimpleName(), code.code());
                    return new ErrorResolution(code, code.httpStatus());
                }
            } catch (Exception ex) {
                log.warn("ErrorMappingContributor({}) failed to process exception: {}",
                        contributor.getClass().getSimpleName(), ex.getMessage());
            }
        }

        if (traitMappingEnabled && exception instanceof HasErrorTraits hasErrorTraits) {
            Set<ErrorTrait> traits = hasErrorTraits.getErrorTraits();
            if (traits != null && !traits.isEmpty()) {
                ErrorCodeLike code = mapTraitsToCode(traits);
                log.debug("Resolved via traits -> {}", code.code());
                return new ErrorResolution(code, code.httpStatus());
            }
        }

        if (namingHeuristicEnabled) {
            String className = exception.getClass().getSimpleName();
            ErrorResolution resolution = resolveByNamingConvention(className);
            if (resolution != null) {
                log.debug("Resolved via naming heuristic {} -> {}", className, resolution.errorCode().code());
                return resolution;
            }
        }

        Throwable cause = exception.getCause();
        if (cause != null && cause != exception) {
            return resolveWithCause(cause, depth + 1);
        }

        return fallbackForException(exception);
    }

    private ErrorResolution fallbackForException(Throwable exception) {
        if (isClientErrorLike(exception)) {
            ErrorCodeLike code = createCode("0422");
            return new ErrorResolution(code, code.httpStatus());
        }
        return fallbackServerError();
    }

    private ErrorCodeLike mapTraitsToCode(Set<ErrorTrait> traits) {
        if (traits.contains(ErrorTrait.NOT_FOUND)) { return createCode("0404"); }
        if (traits.contains(ErrorTrait.CONFLICT)) { return createCode("0409"); }
        if (traits.contains(ErrorTrait.RULE_VIOLATION)) { return createCode("0422"); }
        if (traits.contains(ErrorTrait.QUOTA_EXCEEDED)) { return createCode("0429"); }
        if (traits.contains(ErrorTrait.UNAUTHORIZED)) { return createCode("0401"); }
        if (traits.contains(ErrorTrait.FORBIDDEN)) { return createCode("0403"); }
        if (traits.contains(ErrorTrait.TIMEOUT)) { return createCode("0504"); }
        if (traits.contains(ErrorTrait.DEP_UNAVAILABLE)) { return createCode("0503"); }
        return createCode("0500");
    }

    private ErrorResolution resolveByNamingConvention(String className) {
        if (className.endsWith("NotFound")) {
            ErrorCodeLike c = createCode("0404");
            return new ErrorResolution(c, c.httpStatus());
        }
        if (className.endsWith("Conflict") || className.endsWith("AlreadyExists")) {
            ErrorCodeLike c = createCode("0409");
            return new ErrorResolution(c, c.httpStatus());
        }
        if (className.endsWith("Invalid") || className.endsWith("Validation")) {
            ErrorCodeLike c = createCode("0422");
            return new ErrorResolution(c, c.httpStatus());
        }
        if (className.endsWith("QuotaExceeded")) {
            ErrorCodeLike c = createCode("0429");
            return new ErrorResolution(c, c.httpStatus());
        }
        if (className.endsWith("Unauthorized")) {
            ErrorCodeLike c = createCode("0401");
            return new ErrorResolution(c, c.httpStatus());
        }
        if (className.endsWith("Forbidden")) {
            ErrorCodeLike c = createCode("0403");
            return new ErrorResolution(c, c.httpStatus());
        }
        if (className.endsWith("Timeout")) {
            ErrorCodeLike c = createCode("0504");
            return new ErrorResolution(c, c.httpStatus());
        }
        return null;
    }

    private boolean isClientErrorLike(Throwable exception) {
        String className = exception.getClass().getSimpleName().toLowerCase();
        return className.contains("validation") ||
                className.contains("notvalid") ||
                className.contains("bind") ||
                className.contains("constraint") ||
                className.contains("missing") ||
                className.contains("illegal") ||
                className.contains("invalid") ||
                className.contains("bad") ||
                className.contains("malformed");
    }

    private ErrorResolution fallbackServerError() {
        ErrorCodeLike code = createCode("0500");
        return new ErrorResolution(code, code.httpStatus());
    }

    private ErrorCodeLike createCode(String suffix) {
        String contextPrefix = errorProperties.getContextPrefix();
        if (contextPrefix == null || contextPrefix.isBlank()) {
            contextPrefix = DEFAULT_CONTEXT;
        }
        final String finalCode = contextPrefix + "-" + suffix;
        int status;
        try {
            status = Integer.parseInt(suffix);
            if (status < 100 || status > 599) {
                status = 500;
            }
        } catch (NumberFormatException e) {
            status = 500;
        }
        final int http = status;
        return new ErrorCodeLike() {
            @Override
            public String code() { return finalCode; }
            @Override
            public int httpStatus() { return http; }
            @Override
            public String toString() { return finalCode; }
        };
    }
}
