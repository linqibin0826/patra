package com.patra.starter.rocketmq.core.destination;

import java.util.Objects;

/**
 * RocketMQ 目的地值对象（Topic:Tag 格式）。
 *
 * @author linqibin
 * @since 0.1.0
 */
public record Destination(String topic, String tag) {

    public Destination {
        Objects.requireNonNull(topic, "topic 不能为空");
        if (topic.isBlank()) {
            throw new IllegalArgumentException("topic 不能为空白字符串");
        }
        // tag 允许为 null（表示不指定 tag）
    }

    /**
     * 解析字符串格式的 destination（TOPIC:TAG 或 TOPIC）。
     */
    public static Destination parse(String destination) {
        Objects.requireNonNull(destination, "destination 不能为空");
        if (destination.isBlank()) {
            throw new IllegalArgumentException("destination 不能为空白字符串");
        }
        
        int idx = destination.indexOf(':');
        if (idx > 0) {
            String topic = destination.substring(0, idx);
            String tag = destination.substring(idx + 1);
            if (tag.isBlank()) {
                throw new IllegalArgumentException("tag 不能为空白字符串: " + destination);
            }
            return new Destination(topic, tag);
        }
        return new Destination(destination, null);
    }

    /**
     * 转为字符串格式（TOPIC:TAG 或 TOPIC）。
     */
    @Override
    public String toString() {
        return tag != null ? topic + ":" + tag : topic;
    }
}
