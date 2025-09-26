package com.patra.starter.core.error.circuit;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 熔断器默认实现，支持可配置的失败阈值与恢复超时。
 *
 * <p>采用滑动窗口统计近期调用数据，用于判定打开/关闭熔断状态。
 *
 * @author linqibin
 * @since 0.1.0
 * @see com.patra.starter.core.error.circuit.CircuitBreaker
 */
@Slf4j
public class DefaultCircuitBreaker implements CircuitBreaker {
    
    private final String name;
    private final int failureThreshold;
    private final double failureRateThreshold;
    private final Duration timeout;
    private final int slidingWindowSize;
    
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger totalCalls = new AtomicInteger(0);
    
    /**
     * 构造函数。
     *
     * @param name 熔断器名称
     * @param failureThreshold 连续失败次数阈值
     * @param failureRateThreshold 失败率阈值（0.0~1.0）
     * @param timeout 从 OPEN 尝试恢复前的超时时长
     * @param slidingWindowSize 滑动窗口大小（统计调用数）
     */
    public DefaultCircuitBreaker(String name, int failureThreshold, double failureRateThreshold, 
                               Duration timeout, int slidingWindowSize) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.failureRateThreshold = failureRateThreshold;
        this.timeout = timeout;
        this.slidingWindowSize = slidingWindowSize;
    }
    
    @Override
    public <T> T execute(Supplier<T> supplier) throws CircuitBreakerOpenException {
        State currentState = state.get();
        
        if (currentState == State.OPEN) {
            if (shouldAttemptReset()) {
                state.compareAndSet(State.OPEN, State.HALF_OPEN);
                log.info("Circuit breaker '{}' transitioning from OPEN to HALF_OPEN", name);
                currentState = State.HALF_OPEN;
            } else {
                throw new CircuitBreakerOpenException(name);
            }
        }
        
        try {
            T result = supplier.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }
    
    @Override
    public State getState() {
        return state.get();
    }
    
    @Override
    public double getFailureRate() {
        int total = totalCalls.get();
        if (total == 0) return 0.0;
        return (double) failureCount.get() / total;
    }
    
    @Override
    public long getRecentCallCount() {
        return totalCalls.get();
    }
    
    @Override
    public void forceOpen() {
        state.set(State.OPEN);
        lastFailureTime.set(System.currentTimeMillis());
        log.warn("Circuit breaker '{}' manually opened", name);
    }
    
    @Override
    public void forceClose() {
        state.set(State.CLOSED);
        resetCounters();
        log.info("Circuit breaker '{}' manually closed", name);
    }
    
    /**
     * 成功执行后的处理。
     */
    private void onSuccess() {
        incrementTotalCalls();
        successCount.incrementAndGet();
        consecutiveFailures.set(0);
        
        State currentState = state.get();
        if (currentState == State.HALF_OPEN) {
            state.compareAndSet(State.HALF_OPEN, State.CLOSED);
            log.info("Circuit breaker '{}' transitioning from HALF_OPEN to CLOSED after success", name);
            resetCounters();
        }
        
        log.debug("Circuit breaker '{}' recorded success, state: {}", name, state.get());
    }
    
    /**
     * 失败执行后的处理。
     */
    private void onFailure() {
        incrementTotalCalls();
        failureCount.incrementAndGet();
        int failures = consecutiveFailures.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        
        State currentState = state.get();
        
        // Check if we should open the circuit
        if (currentState != State.OPEN && shouldOpenCircuit(failures)) {
            state.compareAndSet(currentState, State.OPEN);
            log.warn("Circuit breaker '{}' opening due to failures: consecutive={}, rate={}, state: {}", 
                    name, failures, getFailureRate(), State.OPEN);
        }
        
        log.debug("Circuit breaker '{}' recorded failure, consecutive failures: {}, state: {}", 
                 name, failures, state.get());
    }
    
    /**
      * 增加总调用计数并维护滑动窗口。
      */
    private void incrementTotalCalls() {
        int total = totalCalls.incrementAndGet();
        
        // When window is full, reduce counts to keep recent statistics
        if (total >= slidingWindowSize) {
            int currentSuccess = successCount.get();
            int currentFailure = failureCount.get();
            
            // Keep proportional counts for the new window
            int newSuccess = currentSuccess / 2;
            int newFailure = currentFailure / 2;
            
            successCount.set(newSuccess);
            failureCount.set(newFailure);
            totalCalls.set(newSuccess + newFailure);
            
            log.debug("Circuit breaker '{}' sliding window reset, new totals: success={}, failure={}", 
                     name, newSuccess, newFailure);
        }
    }
    
    /**
     * 根据失败条件判断是否需要打开熔断器。
     */
    private boolean shouldOpenCircuit(int consecutiveFailures) {
        // Open if consecutive failures exceed threshold
        if (consecutiveFailures >= failureThreshold) {
            return true;
        }
        
        // Open if failure rate exceeds threshold and we have enough data
        if (totalCalls.get() >= 10) { // Minimum calls before considering failure rate
            return getFailureRate() >= failureRateThreshold;
        }
        
        return false;
    }
    
    /**
     * 判断是否应从 OPEN 尝试重置为 HALF_OPEN。
     */
    private boolean shouldAttemptReset() {
        long lastFailure = lastFailureTime.get();
        long timeoutMillis = timeout.toMillis();
        return System.currentTimeMillis() - lastFailure >= timeoutMillis;
    }
    
    /**
     * 重置所有统计计数。
     */
    private void resetCounters() {
        successCount.set(0);
        failureCount.set(0);
        totalCalls.set(0);
        consecutiveFailures.set(0);
    }
}
