package com.patra.ingest.domain.messaging;

import com.patra.ingest.domain.model.value.TaskReadyMessage;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Ingest 模块内的标准通道目录（强类型）。
 * <p>仅声明语义，不包含任何 MQ 细节，供 app/infra/adapter 统一引用，消除魔法值。</p>
 */
public enum IngestChannels implements ChannelKey {
    /** 调度任务准备就绪 */
    TASK_READY("ingest", "task", "ready", TaskReadyMessage.class);

    private final String domain;
    private final String resource;
    private final String event;
    private final Class<?> payloadType;

    IngestChannels(String domain, String resource, String event, Class<?> payloadType) {
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
     * 将规范化字符串（例如 "ingest.task.ready"）解析为目录项。
     */
    public static Optional<IngestChannels> fromChannel(String channel) {
        if (channel == null || channel.isBlank()) return Optional.empty();
        String ch = channel.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(it -> it.channel().equals(ch)).findFirst();
    }
}

