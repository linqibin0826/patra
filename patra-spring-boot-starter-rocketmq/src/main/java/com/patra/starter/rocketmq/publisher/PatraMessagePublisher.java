package com.patra.starter.rocketmq.publisher;

import com.patra.starter.rocketmq.model.PatraMessage;

/**
 * 统一消息发布接口，隐藏底层 MQ 细节。
 */
public interface PatraMessagePublisher {

    void send(String destination, PatraMessage<?> message);

    /**
     * 通过 Topic + Tag 发送（内部拼装为 destination 字符串）。
     */
    default void send(String topic, String tag, PatraMessage<?> message) {
        String destination = (tag == null || tag.isBlank()) ? topic : topic + ":" + tag;
        send(destination, message);
    }

    /**
     * 按照统一 channel 规范（domain.resource.event[.sub...]）发送。
     * 由实现类使用命名配置将 channel 解析为 destination。
     */
    void sendByChannel(String channel, PatraMessage<?> message);

    /**
     * 顺序消息发送（按 hashKey 路由到队列）。
     */
    void sendOrderly(String destination, PatraMessage<?> message, String hashKey);

    /**
     * 延迟消息发送（delayLevel 取值见 RocketMQ 配置）。
     */
    void sendDelay(String destination, PatraMessage<?> message, long timeoutMs, int delayLevel);
}
