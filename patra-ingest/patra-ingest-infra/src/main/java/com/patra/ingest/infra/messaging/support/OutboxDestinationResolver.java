package com.patra.ingest.infra.messaging.support;

import com.patra.starter.rocketmq.config.PatraRocketMQProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Outbox channel → RocketMQ 目的地（topic:tag）解析器。
 * <p>
 * 解析规则（示例：<code>ingest.task.ready</code>）：
 * <ol>
 *   <li>前两个段（ingest.task）组成基础 topicCore，第三段及之后（ready...）组成 tag。</li>
 *   <li>所有段校验格式：<code>[a-z0-9]+</code>，并转换为大写（业务命名约定统一）。</li>
 *   <li>如配置了命名空间 namespace（例如 <code>PT</code>），最终 topic = <code>NAMESPACE.TOPICCORE</code>。</li>
 *   <li>返回格式：<code>topic:tag</code>。</li>
 * </ol>
 * </p>
 * <p>错误策略：输入非法（空 / 段数不足 / 非法字符）抛出 {@link IllegalArgumentException}，由上游阻断发布。</p>
 * <p>线程安全：无状态；属性对象只读。</p>
 */
@Component
public class OutboxDestinationResolver {

    /** RocketMQ 命名配置（含命名空间信息） */
    private final PatraRocketMQProperties properties;

    public OutboxDestinationResolver(PatraRocketMQProperties properties) {
        this.properties = properties;
    }

    /**
     * 解析 channel 为 topic:tag。
     * @param channel Outbox channel（格式：domain.resource.event[.subEvent...]）
     * @return RocketMQ 目的地字符串 topic:tag
     * @throws IllegalArgumentException 不符合命名规范
     */
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

    /**
     * 将 channel 片段转换为大写 token。
     * @param value 原始片段
     * @return 规范化大写字符串
     */
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
