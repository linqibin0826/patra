package com.patra.starter.rocketmq.core.message;

/**
 * 消息头键常量定义。
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class MessageHeaderKeys {

    public static final String EVENT_ID = "eventId";
    public static final String TRACE_ID = "traceId";
    public static final String OCCURRED_AT = "occurredAt";

    private MessageHeaderKeys() {
    }
}
