package com.patra.ingest.app.usecase.execution.command;

import java.util.Map;

/**
 * Task Ready Command (simplified).
 *
 * <p>Assembled by adapter layer after consuming INGEST_TASK_READY MQ message. Contains only
 * essential fields required for task execution preparation. All other business data (provenance,
 * operation, params, etc.) should be queried from database.
 *
 * <p>Fields:
 *
 * <ul>
 *   <li>taskId: Task ID (required, for lease acquisition and context loading)
 *   <li>idempotentKey: Idempotent key (required, for deduplication check)
 *   <li>headers: MQ message headers (for tracing: ROCKET_MQ_MESSAGE_ID, traceId, partitionKey,
 *       etc.)
 * </ul>
 *
 * @param taskId Task ID
 * @param idempotentKey Idempotent key
 * @param headers MQ message headers (for tracing and auditing)
 * @author linqibin
 * @since 0.1.0
 */
public record TaskReadyCommand(long taskId, String idempotentKey, Map<String, Object> headers) {
  public TaskReadyCommand {
    if (idempotentKey == null || idempotentKey.isBlank()) {
      throw new IllegalArgumentException("idempotentKey must not be blank");
    }
  }

  /** Extracts tracing fields from headers (helper methods). */
  public String getMessageId() {
    return resolveHeaderAsString("ROCKET_MQ_MESSAGE_ID");
  }

  public String getCorrelationId() {
    return resolveHeaderAsString("correlationId"); // TODO Update producer to include this value
  }

  public String getSchedulerRunId() {
    return resolveHeaderAsString("scheduler"); // TODO Remove if not used
  }

  private String resolveHeaderAsString(String key) {
    if (headers == null) {
      return null;
    }
    Object value = headers.get(key);
    if (value == null) {
      return null;
    }
    // RocketMQ headers may be UUID or other non-string types, unify to string
    return value instanceof String ? (String) value : value.toString();
  }
}
