package com.patra.starter.rocketmq.consumer;

/**
 * 消费模式：顺序 or 并发。
 * <p>与 RocketMQ 的 ConsumeMode 对应，避免直接依赖外部枚举带来的兼容风险。</p>
 */
public enum ConsumerMode {
    ORDERLY,
    CONCURRENT
}

