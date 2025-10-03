package com.patra.ingest.infra.messaging;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.vo.RelayPlan;
import com.patra.ingest.domain.port.OutboxPublisherPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 出站消息降级发布实现：在移除 RocketMQ Starter 后，维持 Outbox 流程可用性。
 * <p>当前实现仅记录日志并直接返回成功结果，确保调用方无需调整即可继续运行。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class NoopOutboxPublisher implements OutboxPublisherPort {

    @Override
    public PublishResult publish(OutboxMessage message, RelayPlan plan) {
        log.warn("[INGEST][INFRA] 已降级为本地日志发布，消息不会投递下游 channel={} messageId={} partitionKey={}",
                message.getChannel(), message.getId(), message.getPartitionKey());
        return PublishResult.NONE;
    }
}
