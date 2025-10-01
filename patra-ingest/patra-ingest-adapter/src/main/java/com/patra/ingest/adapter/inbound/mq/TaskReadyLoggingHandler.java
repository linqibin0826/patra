package com.patra.ingest.adapter.inbound.mq;

import com.patra.ingest.domain.model.vo.TaskReadyMessage;
import com.patra.starter.rocketmq.consumer.ConsumeMode;
import com.patra.starter.rocketmq.consumer.MessageHandler;
import com.patra.starter.rocketmq.consumer.MessageListener;
import com.patra.starter.rocketmq.core.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Ingest 示例消费者（仅日志）：消费 ingest.task.ready 事件。
 * <p>
 * 注意：本类仅用于演示消费方案落地，不做实际业务处理。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
@MessageListener(
        channel = "INGEST.TASK.READY",  // 直接指定 channel 字符串
        consumer = "relay",
        mode = ConsumeMode.CONCURRENT,
        concurrency = 2)
public class TaskReadyLoggingHandler implements MessageHandler<TaskReadyMessage> {

    @Override
    public void handle(Message<TaskReadyMessage> message) {
        // 仅记录日志，验证链路打通；实际项目请调用应用服务
        log.info("[INGEST][ADAPTER] recv task-ready eventId={} traceId={} payload={}",
                message.getEventId(), message.getTraceId(), message.getPayload());
    }
}
