package com.patra.starter.core.error.circuit;

import java.util.function.Supplier;

/**
 * 简单的熔断器接口，用于保护错误映射贡献者，避免级联故障与性能问题。
 *
 * @author linqibin
 * @since 0.1.0
 * @see com.patra.starter.core.error.circuit.DefaultCircuitBreaker 默认实现
 */
public interface CircuitBreaker {
    
    /**
     * 熔断器状态。
     */
    enum State {
        /** 闭合，允许正常调用 */
        CLOSED,
        /** 打开，拒绝调用以防止级联故障 */
        OPEN,
        /** 半开，允许有限探测调用以验证恢复情况 */
        HALF_OPEN
    }
    
    /**
     * 在熔断器保护下执行给定操作。
     *
     * @param supplier 待执行的操作
     * @param <T> 返回类型
     * @return 操作结果
     * @throws CircuitBreakerOpenException 当熔断器处于打开状态
     */
    <T> T execute(Supplier<T> supplier) throws CircuitBreakerOpenException;
    
    /**
     * 获取当前熔断器状态。
     *
     * @return 当前状态
     */
    State getState();
    
    /**
     * 获取失败率（0.0 ~ 1.0）。
     *
     * @return 当前失败率
     */
    double getFailureRate();
    
    /**
     * 获取近期调用计数。
     *
     * @return 近期调用总数
     */
    long getRecentCallCount();
    
    /**
     * 手动打开熔断器。
     */
    void forceOpen();
    
    /**
     * 手动关闭熔断器。
     */
    void forceClose();
}
