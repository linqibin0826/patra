package com.patra.ingest.adapter.inbound.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.adapter.inbound.stream.dto.TaskReadyPayload;
import com.patra.ingest.app.usecase.execution.TaskExecutionUseCase;
import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * RocketMQ 消费者配置：订阅 INGEST_TASK_READY 主题并启动任务执行。
 * <p>
 * 职责：
 * <ul>
 *   <li>解析 MQ 消息的 payload 与 headers</li>
 *   <li>组装 {@link TaskReadyCommand} 并调用应用层用例</li>
 *   <li>处理解析异常（抛出异常触发 MQ 重试）</li>
 * </ul>
 * </p>
 * <p>
 * Spring Cloud Stream RocketMQ Binder 使用的 header keys:
 * <ul>
 *   <li>ROCKET_KEYS: 消息业务键（对应 RocketMQ 的 KEYS 属性）</li>
 *   <li>ROCKET_TAGS: 消息标签（对应 RocketMQ 的 TAGS 属性）</li>
 *   <li>ROCKET_MQ_TOPIC: Topic 名称</li>
 *   <li>ROCKET_MQ_MESSAGE_ID: 消息 ID</li>
 *   <li>partitionKey: 分区键（自定义 header）</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class IngestStreamConsumers {

    private static final String HEADER_KEYS = "ROCKET_KEYS";
    private static final String HEADER_TAGS = "ROCKET_TAGS";
    private static final String HEADER_TOPIC = "ROCKET_MQ_TOPIC";
    private static final String HEADER_MESSAGE_ID = "ROCKET_MQ_MESSAGE_ID";

    private final TaskExecutionUseCase taskExecutionUseCase;
    private final ObjectMapper objectMapper;

    @Bean
    public Consumer<Message<String>> ingestTaskReadyConsumer() {
        return message -> {
            try {
                // DEBUG 日志：打印所有 headers 用于诊断
                if (log.isDebugEnabled()) {
                    log.debug("[INGEST][ADAPTER] Received headers: {}", message.getHeaders());
                }

                // 读取 RocketMQ 相关 headers
                String topic = (String) message.getHeaders().getOrDefault(HEADER_TOPIC, "unknown");
                String keys = (String) message.getHeaders().get(HEADER_KEYS);
                String tags = (String) message.getHeaders().get(HEADER_TAGS);
                String messageId = (String) message.getHeaders().get(HEADER_MESSAGE_ID);
                String partitionKey = (String) message.getHeaders().get("partitionKey");

                log.info("[INGEST][ADAPTER] consume topic={} KEYS={} TAGS={} msgId={} partitionKey={}",
                        topic, keys, tags, messageId, partitionKey);

                // 解析 payload 为 TaskReadyCommand
                TaskReadyCommand command = parsePayload(message.getPayload(), message.getHeaders());

                // 调用应用层用例
                taskExecutionUseCase.startFromReady(command);

            } catch (Exception e) {
                log.error("[INGEST][ADAPTER] failed to consume message, will retry", e);
                throw new RuntimeException("消息消费失败", e); // 抛出异常触发 MQ 重试
            }
        };
    }

    /**
     * 解析 payload 为 TaskReadyCommand。
     *
     * @param payload JSON 字符串
     * @param headers 消息头
     * @return TaskReadyCommand
     * @throws Exception 解析失败时抛出异常
     */
    private TaskReadyCommand parsePayload(String payload, Map<String, Object> headers) throws Exception {
        // 解析 payload 为 POJO
        TaskReadyPayload dto = objectMapper.readValue(payload, TaskReadyPayload.class);

        // 校验必需字段
        dto.validate();

        // 合并 headers（用于追踪与审计）
        Map<String, Object> allHeaders = new HashMap<>(headers);

        return new TaskReadyCommand(
                dto.getTaskId(),
                dto.getIdempotentKey(),
                dto.getProvenance(),
                dto.getOperation(),
                dto.getPriority(),
                dto.getScheduledAt(),
                dto.getPlanWindowFrom(),
        dto.getPlanWindowTo(),
                allHeaders
        );
    }
}
