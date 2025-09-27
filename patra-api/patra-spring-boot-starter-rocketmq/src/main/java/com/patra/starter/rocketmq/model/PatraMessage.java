package com.patra.starter.rocketmq.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 统一消息载体，封装 traceId / eventId 等公共字段。
 */
public final class PatraMessage<T> {

    private final String eventId;
    private final String traceId;
    private final Instant occurredAt;
    private final T payload;

    private PatraMessage(Builder<T> builder) {
        this.eventId = Objects.requireNonNull(builder.eventId, "eventId 不能为空");
        this.traceId = builder.traceId;
        this.occurredAt = builder.occurredAt == null ? Instant.now() : builder.occurredAt;
        this.payload = builder.payload;
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

    public static <T> PatraMessage<T> of(T payload) {
        return PatraMessage.<T>builder()
                .eventId(UUID.randomUUID().toString())
                .payload(payload)
                .build();
    }

    public static final class Builder<T> {
        private String eventId = UUID.randomUUID().toString();
        private String traceId;
        private Instant occurredAt;
        private T payload;

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

        public PatraMessage<T> build() {
            if (payload == null) {
                throw new IllegalArgumentException("payload 不能为空");
            }
            return new PatraMessage<>(this);
        }
    }
}
