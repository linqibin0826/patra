package com.patra.ingest.app.outbox.constants;

import lombok.Getter;

/**
 * Outbox aggregate type enum.
 *
 * <p>Defines all valid aggregate types used in the Outbox framework for:
 *
 * <ul>
 *   <li>Micrometer metrics tag cardinality control
 *   <li>Database partitioning and indexing strategies
 *   <li>Message routing and filtering
 * </ul>
 *
 * <h3>Usage Example</h3>
 *
 * <pre>{@code
 * @Override
 * protected OutboxAggregateTypes getAggregateType() {
 *     return OutboxAggregateTypes.TASK;
 * }
 * }</pre>
 *
 * <h3>Configuration Reference</h3>
 *
 * <p>These values must match the allowed aggregate types in Nacos configuration:
 *
 * <pre>
 * papertrace:
 *   outbox:
 *     publisher:
 *       allowed-aggregate-types:
 *         - Task
 * </pre>
 *
 * @author linqibin
 * @since 0.1.0
 * @see com.patra.ingest.app.outbox.config.OutboxPublisherProperties#getAllowedAggregateTypes()
 */
@Getter
public enum OutboxAggregateTypes {

  /**
   * Task aggregate type.
   *
   * <p>Used for task queue events (task creation, scheduling, execution).
   */
  TASK("Task", "Task aggregate - for task queue events"),
  ;

  /**
   * -- GETTER -- Returns the aggregate type code.
   *
   * <p>This value is stored in field.
   */
  private final String code;

  /** -- GETTER -- Returns the human-readable description. */
  private final String description;

  OutboxAggregateTypes(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /**
   * Finds enum by code.
   *
   * @param code Aggregate type code
   * @return Matching enum value
   * @throws IllegalArgumentException if code is not found
   */
  public static OutboxAggregateTypes fromCode(String code) {
    for (OutboxAggregateTypes type : values()) {
      if (type.code.equals(code)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown aggregate type code: " + code);
  }
}
