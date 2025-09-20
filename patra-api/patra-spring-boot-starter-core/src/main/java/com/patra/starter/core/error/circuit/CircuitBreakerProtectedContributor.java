package com.patra.starter.core.error.circuit;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Wrapper that adds circuit breaker protection to ErrorMappingContributor implementations.
 * Prevents cascading failures when contributors are slow or failing frequently.
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
     * Creates a new circuit breaker protected contributor.
     * 
     * @param delegate the actual contributor to protect
     * @param circuitBreaker the circuit breaker to use
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
    
    /**
     * Gets the underlying delegate contributor.
     * 
     * @return the delegate contributor
     */
    public ErrorMappingContributor getDelegate() {
        return delegate;
    }
    
    /**
     * Gets the circuit breaker protecting this contributor.
     * 
     * @return the circuit breaker
     */
    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }
    
    /**
     * Gets the name of the protected contributor.
     * 
     * @return the contributor name
     */
    public String getContributorName() {
        return contributorName;
    }
}