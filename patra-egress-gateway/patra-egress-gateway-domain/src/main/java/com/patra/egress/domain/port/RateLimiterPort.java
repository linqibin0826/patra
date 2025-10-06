package com.patra.egress.domain.port;

import com.patra.egress.domain.model.vo.RateLimitStatus;

/**
 * 限流端口接口
 * 定义限流能力的抽象接口
 * 
 * @author linqibin
 * @since 0.1.0
 */
public interface RateLimiterPort {
    
    /**
     * 尝试获取限流许可
     * 
     * @param key 限流键（用于区分不同的限流对象）
     * @param rateLimit 限流速率（每秒请求数）
     * @return true表示获取成功，false表示被限流
     */
    boolean tryAcquire(String key, int rateLimit);
    
    /**
     * 获取限流状态
     * 
     * @param key 限流键
     * @return 限流状态
     */
    RateLimitStatus getStatus(String key);
}
