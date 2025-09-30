package com.patra.starter.rocketmq.core.message;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 统一消息载体，封装业务载荷与元数据。
 *
 * @param <T> 载荷类型
 * @author linqibin
 * @since 0.1.0
 */
public final class Message<T> {

    private final String eventId;
    private final String traceId;
    private final Instant occurredAt;
    private final T payload;

    private Message(Builder<T> builder) {
        this.eventId = Objects.requireNonNull(builder.eventId, "eventId 不能为空");
        this.traceId = builder.traceId;
        this.occurredAt = builder.occurredAt != null ? builder.occurredAt : Instant.now();
        this.payload = Objects.requireNonNull(builder.payload, "payload 不能为空");
    }

    public String getEventId() {
        return eventId;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public T getPayload() {
        return payload;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * 快速创建消息（自动生成 eventId）。
     */
    public static <T> Message<T> of(T payload) {
        return Message.<T>builder()
                .eventId(UUID.randomUUID().toString())
                .payload(payload)
                .build();
    }

    public static final class Builder<T> {
        private String eventId;
        private String traceId;
        private Instant occurredAt;
        private T payload;

        private Builder() {
        }

        public Builder<T> eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder<T> traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder<T> occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public Builder<T> payload(T payload) {
            this.payload = payload;
            return this;
        }

        public Message<T> build() {
            if (eventId == null) {
                eventId = UUID.randomUUID().toString();
            }
            return new Message<>(this);
        }
    }

    @Override
    public String toString() {
        return "Message{eventId='" + eventId + "', traceId='" + traceId + "', occurredAt=" + occurredAt + "}";
    }
}
