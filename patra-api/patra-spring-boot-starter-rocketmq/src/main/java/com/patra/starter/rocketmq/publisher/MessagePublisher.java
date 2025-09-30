package com.patra.starter.rocketmq.publisher;

import com.patra.starter.rocketmq.core.Channel;
import com.patra.starter.rocketmq.core.destination.Destination;
import com.patra.starter.rocketmq.core.message.Message;

/**
 * 消息发布器接口。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface MessagePublisher {

    /**
     * 发送消息到指定目的地。
     *
     * @param destination 目的地（TOPIC:TAG 格式）
     * @param message     消息
     */
    void send(Destination destination, Message<?> message);

    /**
     * 发送消息到指定目的地（字符串格式）。
     */
    default void send(String destination, Message<?> message) {
        send(Destination.parse(destination), message);
    }

    /**
     * 按 Channel 发送消息（自动转换为 Destination）。
     *
     * @param channel 通道
     * @param message 消息
     */
    void sendByChannel(Channel channel, Message<?> message);

    /**
     * 发送顺序消息。
     *
     * @param destination 目的地
     * @param message     消息
     * @param hashKey     顺序键（相同 hashKey 路由到同一队列）
     */
    void sendOrderly(Destination destination, Message<?> message, String hashKey);

    /**
     * 发送延迟消息。
     *
     * @param destination 目的地
     * @param message     消息
     * @param delayLevel  延迟级别（1-18，对应不同延迟时间）
     */
    void sendDelayed(Destination destination, Message<?> message, int delayLevel);
}
