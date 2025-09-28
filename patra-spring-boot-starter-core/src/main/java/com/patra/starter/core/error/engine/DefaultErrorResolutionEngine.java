package com.patra.starter.core.error.engine;

import com.patra.common.error.ApplicationException;
import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import com.patra.starter.core.error.spi.StatusMappingStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

/**
 * 默认错误解析引擎实现，负责将异常映射到统一错误码与 HTTP 状态。
 *
 * <p>解析顺序：
 * 1. {@link com.patra.common.error.ApplicationException}
 * 2. {@link com.patra.starter.core.error.spi.ErrorMappingContributor}
 * 3. {@link com.patra.common.error.trait.HasErrorTraits}
 * 4. 命名启发式
 * 5. 兜底策略</p>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Slf4j
public class DefaultErrorResolutionEngine implements ErrorResolutionEngine {

    private static final String DEFAULT_CONTEXT = "UNKNOWN";

    private final ErrorProperties errorProperties;
    private final StatusMappingStrategy statusMappingStrategy;
    private final List<ErrorMappingContributor> mappingContributors;
    private final int maxCauseDepth;
    private final boolean traitMappingEnabled;
    private final boolean namingHeuristicEnabled;

    public DefaultErrorResolutionEngine(ErrorProperties errorProperties,
                                        StatusMappingStrategy statusMappingStrategy,
                                        List<ErrorMappingContributor> mappingContributors) {
        this.errorProperties = errorProperties;
        this.statusMappingStrategy = statusMappingStrategy;
        this.mappingContributors = mappingContributors;
        this.maxCauseDepth = errorProperties.getEngine().getMaxCauseDepth();
        this.traitMappingEnabled = errorProperties.getEngine().isEnableTraitMapping();
        this.namingHeuristicEnabled = errorProperties.getEngine().isEnableNamingHeuristic();
    }

    @Override
    public ErrorResolution resolve(Throwable exception) {
        if (exception == null) {
            log.warn("收到空异常，使用兜底错误码");
            return fallbackServerError();
        }
        return resolveWithCause(exception, 0);
    }

    private ErrorResolution resolveWithCause(Throwable exception, int depth) {
        if (depth > maxCauseDepth) {
            log.warn("超过最大因果链深度 {}，直接返回服务器错误", maxCauseDepth);
            return new ErrorResolution(createCode("0500"), 500);
        }

        if (exception instanceof ApplicationException appEx) {
            ErrorCodeLike errorCode = appEx.getErrorCode();
            int status = statusMappingStrategy.mapToHttpStatus(errorCode, exception);
            log.debug("按 ApplicationException 直接解析 -> {}", errorCode.code());
            return new ErrorResolution(errorCode, status);
        }

        for (ErrorMappingContributor contributor : mappingContributors) {
            try {
                var mapped = contributor.mapException(exception);
                if (mapped.isPresent()) {
                    ErrorCodeLike code = mapped.get();
                    int status = statusMappingStrategy.mapToHttpStatus(code, exception);
                    log.debug("通过 ErrorMappingContributor({}) 解析成功 -> {}", contributor.getClass().getSimpleName(), code.code());
                    return new ErrorResolution(code, status);
                }
            } catch (Exception ex) {
                log.warn("ErrorMappingContributor({}) 处理异常失败: {}", contributor.getClass().getSimpleName(), ex.getMessage());
            }
        }

        if (traitMappingEnabled && exception instanceof HasErrorTraits hasErrorTraits) {
            Set<ErrorTrait> traits = hasErrorTraits.getErrorTraits();
            if (traits != null && !traits.isEmpty()) {
                ErrorCodeLike code = mapTraitsToCode(traits);
                int status = mapTraitsToStatus(traits);
                log.debug("通过 Trait 解析 -> {}", code.code());
                return new ErrorResolution(code, status);
            }
        }

        if (namingHeuristicEnabled) {
            String className = exception.getClass().getSimpleName();
            ErrorResolution resolution = resolveByNamingConvention(className);
            if (resolution != null) {
                log.debug("通过命名启发式解析 {} -> {}", className, resolution.errorCode().code());
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
            return new ErrorResolution(createCode("0422"), 422);
        }
        return fallbackServerError();
    }

    private ErrorCodeLike mapTraitsToCode(Set<ErrorTrait> traits) {
        if (traits.contains(ErrorTrait.NOT_FOUND)) {
            return createCode("0404");
        }
        if (traits.contains(ErrorTrait.CONFLICT)) {
            return createCode("0409");
        }
        if (traits.contains(ErrorTrait.RULE_VIOLATION)) {
            return createCode("0422");
        }
        if (traits.contains(ErrorTrait.QUOTA_EXCEEDED)) {
            return createCode("0429");
        }
        if (traits.contains(ErrorTrait.UNAUTHORIZED)) {
            return createCode("0401");
        }
        if (traits.contains(ErrorTrait.FORBIDDEN)) {
            return createCode("0403");
        }
        if (traits.contains(ErrorTrait.TIMEOUT)) {
            return createCode("0504");
        }
        if (traits.contains(ErrorTrait.DEP_UNAVAILABLE)) {
            return createCode("0503");
        }
        return createCode("0500");
    }

    private int mapTraitsToStatus(Set<ErrorTrait> traits) {
        if (traits.contains(ErrorTrait.NOT_FOUND)) {
            return 404;
        }
        if (traits.contains(ErrorTrait.CONFLICT)) {
            return 409;
        }
        if (traits.contains(ErrorTrait.RULE_VIOLATION)) {
            return 422;
        }
        if (traits.contains(ErrorTrait.QUOTA_EXCEEDED)) {
            return 429;
        }
        if (traits.contains(ErrorTrait.UNAUTHORIZED)) {
            return 401;
        }
        if (traits.contains(ErrorTrait.FORBIDDEN)) {
            return 403;
        }
        if (traits.contains(ErrorTrait.TIMEOUT)) {
            return 504;
        }
        if (traits.contains(ErrorTrait.DEP_UNAVAILABLE)) {
            return 503;
        }
        return 500;
    }

    private ErrorResolution resolveByNamingConvention(String className) {
        if (className.endsWith("NotFound")) {
            return new ErrorResolution(createCode("0404"), 404);
        }
        if (className.endsWith("Conflict") || className.endsWith("AlreadyExists")) {
            return new ErrorResolution(createCode("0409"), 409);
        }
        if (className.endsWith("Invalid") || className.endsWith("Validation")) {
            return new ErrorResolution(createCode("0422"), 422);
        }
        if (className.endsWith("QuotaExceeded")) {
            return new ErrorResolution(createCode("0429"), 429);
        }
        if (className.endsWith("Unauthorized")) {
            return new ErrorResolution(createCode("0401"), 401);
        }
        if (className.endsWith("Forbidden")) {
            return new ErrorResolution(createCode("0403"), 403);
        }
        if (className.endsWith("Timeout")) {
            return new ErrorResolution(createCode("0504"), 504);
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
        return new ErrorResolution(createCode("0500"), 500);
    }

    private ErrorCodeLike createCode(String suffix) {
        String contextPrefix = errorProperties.getContextPrefix();
        if (contextPrefix == null || contextPrefix.isBlank()) {
            contextPrefix = DEFAULT_CONTEXT;
        }
        String finalPrefix = contextPrefix;
        return () -> finalPrefix + "-" + suffix;
    }
}
