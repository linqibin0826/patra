package com.patra.starter.rocketmq.support;

import com.patra.starter.rocketmq.config.PatraRocketMQProperties;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 目的地构建工具：统一将 channel（domain.resource.event[.sub...]）
 * 解析为 RocketMQ 的 destination（TOPIC:TAG）。
 */
public final class DestinationBuilder {

    private DestinationBuilder() {
    }

    /**
     * 从点分 channel 构建 destination。命名规则：
     * 1) 前两段组成 Topic 核心（domain.resource），并转为大写；
     * 2) 第三段及其后组成 Tag，并转为大写；
     * 3) 若配置了 namespace，Topic = NAMESPACE + "." + topicCore；
     * 4) 返回格式：TOPIC:TAG。
     */
    public static String fromChannel(String channel, PatraRocketMQProperties.Naming naming) {
        if (!StringUtils.hasText(channel)) {
            throw new IllegalArgumentException("channel must not be blank");
        }
        String[] segments = channel.split("\\.");
        if (segments.length < 3) {
            throw new IllegalArgumentException("channel must contain at least three segments: " + channel);
        }
        String topicCore = Arrays.stream(segments).limit(2)
                .map(DestinationBuilder::toUpperToken)
                .collect(Collectors.joining("."));
        String tag = Arrays.stream(segments).skip(2)
                .map(DestinationBuilder::toUpperToken)
                .collect(Collectors.joining("."));
        String namespace = naming.getNamespace();
        String topic = StringUtils.hasText(namespace)
                ? toUpperToken(namespace) + "." + topicCore
                : topicCore;
        return topic + ":" + tag;
    }

    /** 将 token 规范化为大写英数字 */
    private static String toUpperToken(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.matches("[a-z0-9]+")) {
            throw new IllegalArgumentException("channel segment must contain lowercase alphanumerics only: " + value);
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }
}

