package com.patra.starter.core.error.circuit;

/**
 * 当熔断器处于打开状态并拒绝调用时抛出。
 *
 * @author linqibin
 * @since 0.1.0
 */
public class CircuitBreakerOpenException extends RuntimeException {
    
    private final String circuitBreakerName;
    
    /**
     * 构造异常。
     *
     * @param circuitBreakerName 熔断器名称
     */
    public CircuitBreakerOpenException(String circuitBreakerName) {
        super("Circuit breaker '" + circuitBreakerName + "' is open");
        this.circuitBreakerName = circuitBreakerName;
    }
    
    /** 获取打开状态的熔断器名称。 */
    public String getCircuitBreakerName() {
        return circuitBreakerName;
    }
}
