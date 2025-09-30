package com.patra.ingest.adapter.inbound.mq;

import com.patra.ingest.domain.model.value.TaskReadyMessage;
import com.patra.starter.rocketmq.consumer.ConsumerMode;
import com.patra.starter.rocketmq.consumer.Consumes;
import com.patra.starter.rocketmq.consumer.PatraMessageHandler;
import com.patra.starter.rocketmq.model.PatraMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Ingest 示例消费者（仅日志）：消费 ingest.task.ready 事件。
 * <p>
 * 注意：本类仅用于演示消费方案落地，不做实际业务处理。
 */
@Slf4j
@Component
@Consumes(channel = "ingest.task.ready",
        consumer = "relay",
        mode = ConsumerMode.CONCURRENT,
        concurrency = 2)
public class TaskReadyLoggingHandler implements PatraMessageHandler<TaskReadyMessage> {

    @Override
    public void handle(PatraMessage<TaskReadyMessage> message) {
        // 仅记录日志，验证链路打通；实际项目请调用应用服务
        log.info("[INGEST][ADAPTER] recv task-ready eventId={} traceId={} payload={}",
                message.getEventId(), message.getTraceId(), message.getPayload());
    }
}
