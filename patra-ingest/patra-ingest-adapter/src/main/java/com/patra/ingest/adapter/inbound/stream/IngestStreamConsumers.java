package com.patra.ingest.adapter.inbound.stream;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageConst;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

/**
 * RocketMQ 消费者示例：订阅 INGEST_TASK_READY 主题并打印消息内容。
 */
@Slf4j
@Configuration
public class IngestStreamConsumers {

    private static final String TOPIC_HEADER = "rocketmq_TOPIC";

    @Bean
    public Consumer<Message<String>> ingestTaskReadyConsumer() {
        return message -> {
            Object topic = message.getHeaders().getOrDefault(TOPIC_HEADER, "unknown");
            Object keys = message.getHeaders().get(MessageConst.PROPERTY_KEYS);
            Object tags = message.getHeaders().get(MessageConst.PROPERTY_TAGS);
            Object partitionKey = message.getHeaders().get("partitionKey");
            log.info("[INGEST][ADAPTER] consume topic={} KEYS={} TAGS={} partitionKey={} payload={}",
                    topic, keys, tags, partitionKey, message.getPayload());
        };
    }
}
