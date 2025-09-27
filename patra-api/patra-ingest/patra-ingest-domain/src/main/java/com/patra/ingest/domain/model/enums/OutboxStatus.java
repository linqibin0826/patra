package com.patra.ingest.domain.model.enums;

/**
 * Outbox 消息状态枚举，统一状态机语义。
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    FAILED,
    DEAD
}
