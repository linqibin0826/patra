package com.patra.starter.core.error.service;

import com.patra.common.error.ApplicationException;
import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.metrics.ErrorMetrics;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import com.patra.starter.core.error.spi.StatusMappingStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 将异常解析为业务错误码与 HTTP 状态码的核心服务。
 *
 * <p>特性：
 * - 支持异常因果链（cause chain）逐级回溯与最大深度限制
 * - 基于异常类型的类级缓存，提升解析性能
 * - 可插拔的 {@link com.patra.starter.core.error.spi.ErrorMappingContributor 映射贡献者} 扩展点
 * - 结合 {@link com.patra.starter.core.error.spi.StatusMappingStrategy 状态映射策略} 与语义特征（traits）
 * - 结合 {@link com.patra.starter.core.error.metrics.ErrorMetrics 指标采集} 进行观测
 *
 * <p>解析优先级：
 * 1) {@link com.patra.common.error.ApplicationException}（优先级最高）
 * 2) {@link com.patra.starter.core.error.spi.ErrorMappingContributor}
 * 3) {@link com.patra.common.error.trait.HasErrorTraits}（基于语义特征）
 * 4) 命名约定启发式
 * 5) 兜底策略（区分客户端/服务端错误）
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class ErrorResolutionService {
    
    /** 因果链遍历的最大深度，避免极端情况下的无限循环 */
    private static final int MAX_CAUSE_DEPTH = 10;
    
    /** 错误处理配置 */
    private final ErrorProperties errorProperties;
    
    /** 状态映射策略 */
    private final StatusMappingStrategy statusMappingStrategy;
    
    /** 错误映射贡献者列表 */
    private final List<ErrorMappingContributor> mappingContributors;
    
    /** 错误指标采集器 */
    private final ErrorMetrics errorMetrics;
    
    // 移除按异常类型的类级缓存，避免同类不同实例（不同错误码/语义）被误复用
    
    /**
     * 构造函数。
     *
     * @param errorProperties 错误处理配置，不能为空
     * @param statusMappingStrategy 状态映射策略，不能为空
     * @param mappingContributors 错误映射贡献者列表，不能为空
     * @param errorMetrics 错误指标采集器，不能为空
     */
    public ErrorResolutionService(ErrorProperties errorProperties,
                                StatusMappingStrategy statusMappingStrategy,
                                List<ErrorMappingContributor> mappingContributors,
                                ErrorMetrics errorMetrics) {
        this.errorProperties = errorProperties;
        this.statusMappingStrategy = statusMappingStrategy;
        this.mappingContributors = mappingContributors;
        this.errorMetrics = errorMetrics;
    }
    
    /**
     * 将异常解析为业务错误码与 HTTP 状态码，并记录相关指标。
     * 支持类级缓存加速重复类型异常的解析。
     *
     * @param exception 待解析的异常，不能为空
     * @return 解析结果，包含错误码与 HTTP 状态码
     */
    public ErrorResolution resolve(Throwable exception) {
        long startTime = System.currentTimeMillis();
        
        if (exception == null) {
            log.warn("Null exception passed to resolve; using fallback resolution");
            return createFallbackResolution();
        }
        
        // 直接解析（不使用类级缓存，避免错误码被误复用）
        Class<?> exceptionClass = exception.getClass();
        boolean cacheHit = false;
        ErrorResolution resolution = resolveWithCauseChain(exception, 0);
        
        // 记录指标
        long resolutionTime = System.currentTimeMillis() - startTime;
        errorMetrics.recordResolutionTime(exceptionClass, resolution.errorCode(), resolutionTime, cacheHit);
        errorMetrics.recordCacheHitMiss(exceptionClass, cacheHit);
        errorMetrics.recordErrorCodeDistribution(resolution.errorCode(), resolution.httpStatus(), 
                                                errorProperties.getContextPrefix());
        
        return resolution;
    }
    
    /**
     * 结合因果链逐级回溯解析异常，直到达成映射或超过最大深度。
     *
     * @param exception 异常对象
     * @param depth 当前回溯深度
     * @return 解析结果
     */
    private ErrorResolution resolveWithCauseChain(Throwable exception, int depth) {
        if (depth > MAX_CAUSE_DEPTH) {
            log.warn("Exceeded max cause-chain depth {}. Falling back to server error", MAX_CAUSE_DEPTH);
            return new ErrorResolution(createCode("0500"), 500);
        }
        
        log.debug("Resolving at depth {}: {}", depth, exception.getClass().getSimpleName());
        
        // 1. ApplicationException（框架内业务异常）优先
        if (exception instanceof ApplicationException appEx) {
            ErrorCodeLike code = appEx.getErrorCode();
            int status = statusMappingStrategy.mapToHttpStatus(code, exception);
            log.debug("Resolved ApplicationException -> code={}, status={}", code.code(), status);
            return new ErrorResolution(code, status);
        }
        
        // 2. ErrorMappingContributor（显式映射）
        for (ErrorMappingContributor contributor : mappingContributors) {
            long contributorStartTime = System.currentTimeMillis();
            boolean success = false;
            
            try {
                var mapped = contributor.mapException(exception);
                if (mapped.isPresent()) {
                    success = true;
                    long executionTime = System.currentTimeMillis() - contributorStartTime;
                    errorMetrics.recordContributorPerformance(contributor.getClass(), success, executionTime);
                    
                    ErrorCodeLike code = mapped.get();
                    int status = statusMappingStrategy.mapToHttpStatus(code, exception);
                    log.debug("Resolved via ErrorMappingContributor -> code={}, status={}", code.code(), status);
                    return new ErrorResolution(code, status);
                }
                success = true; // 未匹配不视为失败
            } catch (Exception e) {
                log.warn("ErrorMappingContributor {} failed: {}", 
                        contributor.getClass().getSimpleName(), e.getMessage());
            } finally {
                long executionTime = System.currentTimeMillis() - contributorStartTime;
                errorMetrics.recordContributorPerformance(contributor.getClass(), success, executionTime);
            }
        }
        
        // 3. HasErrorTraits（基于语义特征分类）
        if (exception instanceof HasErrorTraits traitsEx) {
            Set<ErrorTrait> traits = traitsEx.getErrorTraits();
            if (traits != null && !traits.isEmpty()) {
                ErrorCodeLike code = mapTraitsToCode(traits);
                int status = mapTraitsToStatus(traits);
                log.debug("Resolved via ErrorTraits {} -> code={}, status={}", traits, code.code(), status);
                return new ErrorResolution(code, status);
            }
        }
        
        // 4. 命名约定启发式
        String className = exception.getClass().getSimpleName();
        ErrorResolution namingResolution = resolveByNamingConvention(className);
        if (namingResolution != null) {
            log.debug("Resolved by naming convention '{}' -> code={}, status={}", 
                     className, namingResolution.errorCode().code(), namingResolution.httpStatus());
            return namingResolution;
        }
        
        // 若当前异常未匹配，尝试因果链
        Throwable cause = exception.getCause();
        if (cause != null && cause != exception) {
            log.debug("Following cause chain: {}", cause.getClass().getSimpleName());
            ErrorResolution causeResolution = resolveWithCauseChain(cause, depth + 1);
            errorMetrics.recordCauseChainDepth(depth + 1, true);
            return causeResolution;
        }
        
        // 5. 最终兜底（区分客户端/服务端错误）
        ErrorResolution fallback = isClientError(exception) ? 
            new ErrorResolution(createCode("0422"), 422) : 
            new ErrorResolution(createCode("0500"), 500);
        
        log.debug("Using fallback for {} -> code={}, status={}", 
                 className, fallback.errorCode().code(), fallback.httpStatus());
        
        // 记录进入兜底解析的深度
        errorMetrics.recordCauseChainDepth(depth, false);
        
        return fallback;
    }
    
    /**
     * 根据语义特征映射业务错误码。
     */
    private ErrorCodeLike mapTraitsToCode(Set<ErrorTrait> traits) {
        if (traits.contains(ErrorTrait.NOT_FOUND)) {
            return createCode("0404");
        } else if (traits.contains(ErrorTrait.CONFLICT)) {
            return createCode("0409");
        } else if (traits.contains(ErrorTrait.RULE_VIOLATION)) {
            return createCode("0422");
        } else if (traits.contains(ErrorTrait.QUOTA_EXCEEDED)) {
            return createCode("0429");
        } else if (traits.contains(ErrorTrait.UNAUTHORIZED)) {
            return createCode("0401");
        } else if (traits.contains(ErrorTrait.FORBIDDEN)) {
            return createCode("0403");
        } else if (traits.contains(ErrorTrait.TIMEOUT)) {
            return createCode("0504");
        } else if (traits.contains(ErrorTrait.DEP_UNAVAILABLE)) {
            return createCode("0503");
        }
        return createCode("0500");
    }
    
    /**
     * 根据语义特征映射 HTTP 状态码。
     */
    private int mapTraitsToStatus(Set<ErrorTrait> traits) {
        if (traits.contains(ErrorTrait.NOT_FOUND)) {
            return 404;
        } else if (traits.contains(ErrorTrait.CONFLICT)) {
            return 409;
        } else if (traits.contains(ErrorTrait.RULE_VIOLATION)) {
            return 422;
        } else if (traits.contains(ErrorTrait.QUOTA_EXCEEDED)) {
            return 429;
        } else if (traits.contains(ErrorTrait.UNAUTHORIZED)) {
            return 401;
        } else if (traits.contains(ErrorTrait.FORBIDDEN)) {
            return 403;
        } else if (traits.contains(ErrorTrait.TIMEOUT)) {
            return 504;
        } else if (traits.contains(ErrorTrait.DEP_UNAVAILABLE)) {
            return 503;
        }
        return 500;
    }
    
    /**
     * 基于异常类名的命名约定进行启发式解析。
     */
    private ErrorResolution resolveByNamingConvention(String className) {
        if (className.endsWith("NotFound")) {
            return new ErrorResolution(createCode("0404"), 404);
        } else if (className.endsWith("Conflict") || className.endsWith("AlreadyExists")) {
            return new ErrorResolution(createCode("0409"), 409);
        } else if (className.endsWith("Invalid") || className.endsWith("Validation")) {
            return new ErrorResolution(createCode("0422"), 422);
        } else if (className.endsWith("QuotaExceeded")) {
            return new ErrorResolution(createCode("0429"), 429);
        } else if (className.endsWith("Unauthorized")) {
            return new ErrorResolution(createCode("0401"), 401);
        } else if (className.endsWith("Forbidden")) {
            return new ErrorResolution(createCode("0403"), 403);
        } else if (className.endsWith("Timeout")) {
            return new ErrorResolution(createCode("0504"), 504);
        }
        return null;
    }
    
    /**
     * 判断异常是否更可能属于客户端错误（4xx）。
     */
    private boolean isClientError(Throwable exception) {
        String className = exception.getClass().getSimpleName().toLowerCase();
        // 扩充常见校验/客户端错误的关键词覆盖，降低误判为 5xx 的概率
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
    
    /**
     * 使用配置的上下文前缀创建业务错误码。
     */
    private ErrorCodeLike createCode(String suffix) {
        String contextPrefix = errorProperties.getContextPrefix();
        if (contextPrefix == null || contextPrefix.isEmpty()) {
            log.warn("Context prefix not configured; using 'UNKNOWN'");
            contextPrefix = "UNKNOWN";
        }
        final String finalContextPrefix = contextPrefix;
        return () -> finalContextPrefix + "-" + suffix;
    }
    
    /**
     * 构建严重错误情况下的兜底解析结果。
     */
    private ErrorResolution createFallbackResolution() {
        return new ErrorResolution(createCode("0500"), 500);
    }
}
