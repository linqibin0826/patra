package com.patra.ingest.adapter.inbound.stream;

import lombok.extern.slf4j.Slf4j;
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

    /**
     * Spring Cloud Stream RocketMQ Binder 使用的 header keys:
     * - ROCKET_KEYS: 消息业务键（对应 RocketMQ 的 KEYS 属性）
     * - ROCKET_TAGS: 消息标签（对应 RocketMQ 的 TAGS 属性）
     * - ROCKET_MQ_TOPIC: Topic 名称
     */
    private static final String HEADER_KEYS = "ROCKET_KEYS";
    private static final String HEADER_TAGS = "ROCKET_TAGS";
    private static final String HEADER_TOPIC = "ROCKET_MQ_TOPIC";

    @Bean
    public Consumer<Message<String>> ingestTaskReadyConsumer() {
        return message -> {
            // DEBUG 日志：打印所有 headers 用于诊断
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][ADAPTER] Received headers: {}", message.getHeaders());
            }
            
            // 读取 RocketMQ 相关 headers（使用正确的 key）
            String topic = (String) message.getHeaders().getOrDefault(HEADER_TOPIC, "unknown");
            String keys = (String) message.getHeaders().get(HEADER_KEYS);
            String tags = (String) message.getHeaders().get(HEADER_TAGS);
            String partitionKey = (String) message.getHeaders().get("partitionKey");
            
            log.info("[INGEST][ADAPTER] consume topic={} KEYS={} TAGS={} partitionKey={} payload={}",
                    topic, keys, tags, partitionKey, message.getPayload());
        };
    }
}
