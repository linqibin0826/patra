package com.patra.ingest.adapter.inbound.mq;

import com.patra.ingest.domain.model.vo.TaskReadyMessage;
import com.patra.starter.rocketmq.consumer.ConsumeMode;
import com.patra.starter.rocketmq.consumer.MessageHandler;
import com.patra.starter.rocketmq.consumer.MessageListener;
import com.patra.starter.rocketmq.core.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Ingest 示例消费者（仅日志）：消费 INGEST_TASK_READY 事件。
 * <p>
 * 注意: 当前消费者和发布者属于同一个应用，因此消息对象直接使用领域模型。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
@MessageListener(
        channel = "INGEST_TASK_READY",  // 直接指定 channel 字符串
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
