package com.patra.starter.rocketmq.core.message;

import com.patra.starter.core.error.spi.TraceProvider;

import java.time.Instant;

/**
 * 消息工厂：负责从 TraceProvider 注入 traceId。
 *
 * @author linqibin
 * @since 0.1.0
 */
public class MessageFactory {

    private final TraceProvider traceProvider;

    public MessageFactory(TraceProvider traceProvider) {
        this.traceProvider = traceProvider;
    }

    public MessageFactory() {
        this.traceProvider = null;
    }

    /**
     * 创建消息，自动注入 traceId。
     */
    public <T> Message<T> create(T payload) {
        String traceId = traceProvider != null
                ? traceProvider.getCurrentTraceId().orElse(null)
                : null;

        return Message.<T>builder()
                .traceId(traceId)
                .occurredAt(Instant.now())
                .payload(payload)
                .build();
    }
}
