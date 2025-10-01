package com.patra.ingest.domain.messaging;

import com.patra.common.messaging.ChannelKey;
import com.patra.ingest.domain.model.vo.TaskReadyMessage;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Ingest 模块对外发布的消息通道目录（Domain 层枚举）。
 * <p>
 * 职责：
 * <ul>
 *   <li>定义模块内部发布的所有消息通道（强类型枚举）</li>
 *   <li>提供通道解析和查询能力（fromChannel）</li>
 *   <li>关联 payload 类型，便于运行时校验</li>
 * </ul>
 * </p>
 *
 * <p><b>使用指南</b>：
 * <ul>
 *   <li><b>内部发送侧</b>：使用枚举实例 {@code IngestPublishingChannels.TASK_READY.channel()}</li>
 *   <li><b>外部消费侧</b>：引用 API 契约 {@code IngestPublishedChannels.TASK_READY}</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum IngestPublishingChannels implements ChannelKey {
    
    /** 调度任务准备就绪事件 */
    TASK_READY("INGEST", "TASK", "READY", TaskReadyMessage.class);

    private final String domain;
    private final String resource;
    private final String event;
    private final Class<?> payloadType;

    IngestPublishingChannels(String domain, String resource, String event, Class<?> payloadType) {
        this.domain = domain;
        this.resource = resource;
        this.event = event;
        this.payloadType = payloadType;
    }

    @Override public String domain() { return domain; }
    @Override public String resource() { return resource; }
    @Override public String event() { return event; }

    /** 约定的 payload 类型（可用于编译期/运行期校验）。 */
    public Class<?> payloadType() { return payloadType; }

    /**
     * 将规范化字符串（例如 "ingest.task.ready"）解析为通道枚举。
     *
     * @param channel 通道字符串（小写点分段）
     * @return 匹配的枚举实例，若无匹配则返回 empty
     */
    public static Optional<IngestPublishingChannels> fromChannel(String channel) {
        if (channel == null || channel.isBlank()) return Optional.empty();
        String ch = channel.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(it -> it.channel().equals(ch)).findFirst();
    }
}

