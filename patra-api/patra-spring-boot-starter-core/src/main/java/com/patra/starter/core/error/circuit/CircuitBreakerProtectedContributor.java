package com.patra.starter.core.error.circuit;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * 为 ErrorMappingContributor 增加熔断保护的包装器。
 *
 * <p>当贡献者变慢或频繁失败时，避免级联故障。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class CircuitBreakerProtectedContributor implements ErrorMappingContributor {
    
    private final ErrorMappingContributor delegate;
    private final CircuitBreaker circuitBreaker;
    private final String contributorName;
    
    /**
     * 构造包装器。
     *
     * @param delegate 被保护的实际贡献者
     * @param circuitBreaker 使用的熔断器
     */
    public CircuitBreakerProtectedContributor(ErrorMappingContributor delegate, CircuitBreaker circuitBreaker) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
        this.contributorName = delegate.getClass().getSimpleName();
    }
    
    @Override
    public Optional<ErrorCodeLike> mapException(Throwable exception) {
        try {
            return circuitBreaker.execute(() -> {
                log.debug("Executing contributor '{}' with circuit breaker protection", contributorName);
                return delegate.mapException(exception);
            });
        } catch (CircuitBreakerOpenException e) {
            log.warn("Circuit breaker open for contributor '{}', skipping execution", contributorName);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Contributor '{}' failed with circuit breaker protection: {}", 
                    contributorName, e.getMessage());
            return Optional.empty();
        }
    }
    
    /** 获取底层被包装的贡献者。 */
    public ErrorMappingContributor getDelegate() {
        return delegate;
    }
    
    /** 获取熔断器实例。 */
    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }
    
    /** 获取被保护贡献者的名称。 */
    public String getContributorName() {
        return contributorName;
    }
}
