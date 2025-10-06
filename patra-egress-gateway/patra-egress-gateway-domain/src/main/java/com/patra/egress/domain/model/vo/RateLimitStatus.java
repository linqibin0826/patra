package com.patra.egress.domain.model.vo;

import java.time.Duration;

/**
 * 限流状态值对象
 * 区分网关自身限流和外部服务限流
 * 
 * @param limit 限流上限
 * @param remaining 剩余配额
 * @param resetAfter 重置时间
 * @param externalInfo 外部服务返回的限流信息
 * @author linqibin
 * @since 0.1.0
 */
public record RateLimitStatus(
    int limit,
    int remaining,
    Duration resetAfter,
    ExternalRateLimitInfo externalInfo
) {
    /**
     * 构造函数，确保参数有效性
     */
    public RateLimitStatus {
        if (limit < 0) {
            throw new IllegalArgumentException("Limit cannot be negative");
        }
        if (remaining < 0) {
            throw new IllegalArgumentException("Remaining cannot be negative");
        }
        if (resetAfter == null || resetAfter.isNegative()) {
            throw new IllegalArgumentException("ResetAfter must be non-null and non-negative");
        }
    }

    /**
     * 判断是否已达到限流阈值
     *
     * @return true表示已达到限流阈值
     */
    public boolean isLimited() {
        return remaining <= 0;
    }
}
