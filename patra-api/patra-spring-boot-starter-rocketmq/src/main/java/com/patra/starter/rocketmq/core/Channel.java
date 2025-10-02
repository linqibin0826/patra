package com.patra.starter.rocketmq.core;

import com.patra.common.messaging.ChannelKey;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Channel 值对象，表示统一的消息通道标识。
 *
 * <p>格式：domain_resource_event（大写，下划线分段，至少三段）
 * <p>示例：INGEST_TASK_READY
 *
 * @author linqibin
 * @since 0.1.0
 */
public record Channel(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z0-9]+(_[A-Z0-9]+){2,}$");

    public Channel {
        Objects.requireNonNull(value, "channel 不能为空");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "channel 必须符合格式 '^[A-Z0-9]+(_[A-Z0-9]+){2,}$'（大写下划线分段），实际: " + value
            );
        }
    }

    /**
     * 从 ChannelKey 创建。
     */
    public static Channel of(ChannelKey key) {
        return new Channel(key.channel());
    }

    /**
     * 从字符串创建。
     * <p>仅支持大写格式（如 INGEST_TASK_READY）。
     * 
     * @param channelValue channel 字符串，必须是大写格式
     * @return Channel 实例
     * @throws IllegalArgumentException 如果格式不符合要求或不是大写
     */
    public static Channel fromString(String channelValue) {
        Objects.requireNonNull(channelValue, "channel 不能为空");
        return new Channel(channelValue);
    }

    /**
     * 分割并返回各段（懒加载）。
     * <p>使用 limit=3 确保只分割前两个下划线，保持 event 段完整。
     */
    private String[] parts() {
        return value.split("_", 3);
    }

    /**
     * 获取领域（第一段）。
     */
    public String domain() {
        return parts()[0];
    }

    /**
     * 获取资源（第二段）。
     */
    public String resource() {
        return parts()[1];
    }

    /**
     * 获取事件（第三段及之后）。
     */
    public String event() {
        String[] p = parts();
        return p.length > 2 ? p[2] : "";
    }
}
