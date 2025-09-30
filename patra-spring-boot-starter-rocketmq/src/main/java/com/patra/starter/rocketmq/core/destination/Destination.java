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
        // tag 允许为 null（表示不指定 tag）
    }

    /**
     * 解析字符串格式的 destination（TOPIC:TAG 或 TOPIC）。
     */
    public static Destination parse(String destination) {
        Objects.requireNonNull(destination, "destination 不能为空");
        int idx = destination.indexOf(':');
        if (idx > 0) {
            return new Destination(destination.substring(0, idx), destination.substring(idx + 1));
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
