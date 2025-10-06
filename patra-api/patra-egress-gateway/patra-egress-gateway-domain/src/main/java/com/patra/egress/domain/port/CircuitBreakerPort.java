package com.patra.egress.domain.port;

import java.util.function.Supplier;

/**
 * 熔断端口接口
 * 定义熔断能力的抽象接口
 * 
 * @author linqibin
 * @since 0.1.0
 */
public interface CircuitBreakerPort {
    
    /**
     * 熔断器状态枚举
     */
    enum CircuitBreakerState {
        /** 关闭状态（正常） */
        CLOSED,
        /** 打开状态（熔断） */
        OPEN,
        /** 半开状态（测试恢复） */
        HALF_OPEN
    }
    
    /**
     * 使用熔断器执行操作
     * 
     * @param key 熔断器键（用于区分不同的熔断对象）
     * @param supplier 要执行的操作
     * @param threshold 熔断阈值（失败次数）
     * @param <T> 返回值类型
     * @return 操作结果
     */
    <T> T executeWithCircuitBreaker(String key, Supplier<T> supplier, int threshold);
    
    /**
     * 获取熔断器状态
     * 
     * @param key 熔断器键
     * @return 熔断器状态
     */
    CircuitBreakerState getState(String key);
}
