package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.vo.RelayPlan;

/**
 * Outbox 消息发布端口。
 * <p>抽象底层发布渠道（如 MQ、Webhook、S3 等），并返回统一的发布结果，便于 relay 流程进行后续状态流转。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface OutboxPublisherPort {

    /**
     * 发布单条 Outbox 消息。
     *
     * @param message Outbox 消息实体，包含消息体、扩展头、重试计数等信息
     * @param plan    发布指令计划，描述重试策略、租约信息等上下文
     * @return 发布结果，包含下游返回的消息 ID；若无 ID 可返回 {@link PublishResult#NONE}
     * @throws Exception 发布过程中发生的异常，由上层决定是重试还是标记失败
     */
    PublishResult publish(OutboxMessage message, RelayPlan plan) throws Exception;

    /**
     * 发布结果值对象。
     *
     * @param messageId 下游系统生成的消息 ID，允许为空
     */
    record PublishResult(String messageId) {
        /** 无返回 ID 的默认结果。 */
        public static final PublishResult NONE = new PublishResult(null);
    }
}
