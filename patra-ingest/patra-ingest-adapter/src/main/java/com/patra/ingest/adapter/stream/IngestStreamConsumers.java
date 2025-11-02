package com.patra.ingest.adapter.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.adapter.stream.dto.TaskReadyPayload;
import com.patra.ingest.app.usecase.execution.TaskExecutionUseCase;
import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

/**
 * RocketMQ 消费者配置: 订阅 INGEST_TASK_READY 主题并启动任务执行。
 *
 * <p>职责:
 *
 * <ul>
 *   <li>解析 MQ 消息负载和消息头
 *   <li>组装 {@link TaskReadyCommand} 并调用应用用例
 *   <li>处理解析失败(抛出异常触发 MQ 重试)
 * </ul>
 *
 * <p>Spring Cloud Stream RocketMQ Binder 使用的消息头键:
 *
 * <ul>
 *   <li>ROCKET_KEYS: 业务键(RocketMQ KEYS)
 *   <li>ROCKET_TAGS: 消息标签(RocketMQ TAGS)
 *   <li>ROCKET_MQ_TOPIC: 主题名称
 *   <li>ROCKET_MQ_MESSAGE_ID: 消息 ID
 *   <li>partitionKey: 分区键(自定义消息头)
 * </ul>
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
        logReceivedHeaders(message.getHeaders());
        logMessageMetadata(message.getHeaders());

        TaskReadyCommand command = parsePayload(message.getPayload(), message.getHeaders());
        taskExecutionUseCase.execute(command);

      } catch (Exception e) {
        log.error(
            "从主题 [{}] 消费任务就绪消息失败: {}",
            message.getHeaders().getOrDefault(HEADER_TOPIC, "unknown"),
            e.getMessage(),
            e);
        throw new RuntimeException("消息消费失败", e);
      }
    };
  }

  /** 在启用 DEBUG 级别时记录所有接收到的消息头以进行诊断。 */
  private void logReceivedHeaders(Map<String, Object> headers) {
    if (log.isDebugEnabled()) {
      log.debug("收到消息,消息头: {}", headers);
    }
  }

  /** 从 RocketMQ 消息头记录关键消息元数据以进行追踪和监控。 */
  private void logMessageMetadata(Map<String, Object> headers) {
    String topic = (String) headers.getOrDefault(HEADER_TOPIC, "unknown");
    String keys = (String) headers.get(HEADER_KEYS);
    String tags = (String) headers.get(HEADER_TAGS);
    String messageId = (String) headers.get(HEADER_MESSAGE_ID);
    String partitionKey = (String) headers.get("partitionKey");

    log.info(
        "正在从主题 [{}] 消费任务就绪事件,KEYS={} TAGS={} messageId={} partitionKey={}",
        topic,
        keys,
        tags,
        messageId,
        partitionKey);
  }

  /**
   * 将负载解析为 TaskReadyCommand(简化版)。
   *
   * @param payload JSON 字符串
   * @param headers 消息头
   * @return TaskReadyCommand
   * @throws Exception 当解析失败时
   */
  private TaskReadyCommand parsePayload(String payload, Map<String, Object> headers)
      throws Exception {
    // 将负载解析为 POJO
    TaskReadyPayload dto = objectMapper.readValue(payload, TaskReadyPayload.class);

    // 验证必填字段
    dto.validate();

    // 合并消息头(用于追踪和审计)
    Map<String, Object> allHeaders = new HashMap<>(headers);

    return new TaskReadyCommand(dto.getTaskId(), dto.getIdempotentKey(), allHeaders);
  }
}
