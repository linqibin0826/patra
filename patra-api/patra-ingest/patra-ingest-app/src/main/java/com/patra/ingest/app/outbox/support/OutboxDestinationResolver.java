package com.patra.ingest.app.outbox.support;

import com.patra.starter.rocketmq.config.PatraRocketMQProperties;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 将频道映射为 RocketMQ 目的地（topic:tag）。
 */
public class OutboxDestinationResolver {

    private final PatraRocketMQProperties properties;

    public OutboxDestinationResolver(PatraRocketMQProperties properties) {
        this.properties = properties;
    }

    public String resolve(String channel) {
        if (!StringUtils.hasText(channel)) {
            throw new IllegalArgumentException("channel must not be blank");
        }
        String[] segments = channel.split("\\.");
        if (segments.length < 3) {
            throw new IllegalArgumentException("channel must contain at least three segments: " + channel);
        }
        String topicCore = Arrays.stream(segments)
                .limit(2)
                .map(this::toUpperToken)
                .collect(Collectors.joining("."));
        String tag = Arrays.stream(segments)
                .skip(2)
                .map(this::toUpperToken)
                .collect(Collectors.joining("."));
        if (!StringUtils.hasText(tag)) {
            throw new IllegalArgumentException("channel must contain event segment: " + channel);
        }
        String namespace = properties.getNaming().getNamespace();
        String topic = StringUtils.hasText(namespace)
                ? toUpperToken(namespace) + "." + topicCore
                : topicCore;
        return topic + ":" + tag;
    }

    private String toUpperToken(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("channel segment must not be blank");
        }
        String trimmed = value.trim();
        if (!trimmed.matches("[a-z0-9]+")) {
            throw new IllegalArgumentException("channel segment must contain lowercase alphanumerics only: " + trimmed);
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }
}
