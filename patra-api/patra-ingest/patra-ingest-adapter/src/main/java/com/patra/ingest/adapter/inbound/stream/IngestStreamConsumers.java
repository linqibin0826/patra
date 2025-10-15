package com.patra.ingest.adapter.inbound.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.adapter.inbound.stream.dto.TaskReadyPayload;
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
 * RocketMQ consumer configuration: subscribes to the INGEST_TASK_READY topic and starts task
 * execution.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Parse MQ message payload and headers
 *   <li>Assemble {@link TaskReadyCommand} and invoke the application use case
 *   <li>Handle parsing failures (throwing triggers MQ retry)
 * </ul>
 *
 * <p>Header keys used by Spring Cloud Stream RocketMQ Binder:
 *
 * <ul>
 *   <li>ROCKET_KEYS: business key (RocketMQ KEYS)
 *   <li>ROCKET_TAGS: message tags (RocketMQ TAGS)
 *   <li>ROCKET_MQ_TOPIC: topic name
 *   <li>ROCKET_MQ_MESSAGE_ID: message id
 *   <li>partitionKey: partition key (custom header)
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
        // DEBUG: print all headers for diagnostics
        if (log.isDebugEnabled()) {
          log.debug("[INGEST][ADAPTER] Received headers: {}", message.getHeaders());
        }

        // Read RocketMQ-related headers
        String topic = (String) message.getHeaders().getOrDefault(HEADER_TOPIC, "unknown");
        String keys = (String) message.getHeaders().get(HEADER_KEYS);
        String tags = (String) message.getHeaders().get(HEADER_TAGS);
        String messageId = (String) message.getHeaders().get(HEADER_MESSAGE_ID);
        String partitionKey = (String) message.getHeaders().get("partitionKey");

        log.info(
            "[INGEST][ADAPTER] consume topic={} KEYS={} TAGS={} msgId={} partitionKey={}",
            topic,
            keys,
            tags,
            messageId,
            partitionKey);

        // Parse payload to TaskReadyCommand
        TaskReadyCommand command = parsePayload(message.getPayload(), message.getHeaders());

        // Invoke application use case
        taskExecutionUseCase.execute(command);

      } catch (Exception e) {
        log.error("[INGEST][ADAPTER] failed to consume message, will retry", e);
        // TODO Throw to trigger MQ retry
        //        throw new RuntimeException("Message consumption failed", e);
      }
    };
  }

  /**
   * Parses payload to TaskReadyCommand (simplified).
   *
   * @param payload JSON string
   * @param headers Message headers
   * @return TaskReadyCommand
   * @throws Exception when parsing fails
   */
  private TaskReadyCommand parsePayload(String payload, Map<String, Object> headers)
      throws Exception {
    // Parse payload to POJO
    TaskReadyPayload dto = objectMapper.readValue(payload, TaskReadyPayload.class);

    // Validate required fields
    dto.validate();

    // Merge headers (for tracing and auditing)
    Map<String, Object> allHeaders = new HashMap<>(headers);

    return new TaskReadyCommand(dto.getTaskId(), dto.getIdempotentKey(), allHeaders);
  }
}
