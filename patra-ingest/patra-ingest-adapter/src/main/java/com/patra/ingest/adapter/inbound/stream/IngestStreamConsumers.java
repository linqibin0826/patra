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
        logReceivedHeaders(message.getHeaders());
        logMessageMetadata(message.getHeaders());

        log.debug("Parsing task ready message payload");
        TaskReadyCommand command = parsePayload(message.getPayload(), message.getHeaders());
        log.debug(
            "Converted task ready payload to command: taskId [{}], idempotentKey [{}]",
            command.taskId(),
            command.idempotentKey());

        log.debug("Invoking task execution use case for taskId [{}]", command.taskId());
        taskExecutionUseCase.execute(command);
        log.debug("Task execution completed successfully for taskId [{}]", command.taskId());

      } catch (Exception e) {
        log.error(
            "Failed to consume task ready message from topic [{}]: {}",
            message.getHeaders().getOrDefault(HEADER_TOPIC, "unknown"),
            e.getMessage(),
            e);
        throw new RuntimeException("Message consumption failed", e);
      }
    };
  }

  /** Logs all received headers for diagnostics when DEBUG level is enabled. */
  private void logReceivedHeaders(Map<String, Object> headers) {
    if (log.isDebugEnabled()) {
      log.debug("Received message with headers: {}", headers);
    }
  }

  /** Logs key message metadata from RocketMQ headers for tracing and monitoring. */
  private void logMessageMetadata(Map<String, Object> headers) {
    String topic = (String) headers.getOrDefault(HEADER_TOPIC, "unknown");
    String keys = (String) headers.get(HEADER_KEYS);
    String tags = (String) headers.get(HEADER_TAGS);
    String messageId = (String) headers.get(HEADER_MESSAGE_ID);
    String partitionKey = (String) headers.get("partitionKey");

    log.info(
        "Consuming task ready event from topic [{}] with KEYS={} TAGS={} messageId={} partitionKey={}",
        topic,
        keys,
        tags,
        messageId,
        partitionKey);
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
