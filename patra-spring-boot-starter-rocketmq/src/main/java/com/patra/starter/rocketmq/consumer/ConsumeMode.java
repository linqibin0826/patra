package com.patra.starter.rocketmq.consumer;

/**
 * 消费模式枚举。
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum ConsumeMode {
    /**
     * 顺序消费（同一队列的消息按顺序处理）。
     */
    ORDERLY,

    /**
     * 并发消费（多线程并行处理）。
     */
    CONCURRENT
}
