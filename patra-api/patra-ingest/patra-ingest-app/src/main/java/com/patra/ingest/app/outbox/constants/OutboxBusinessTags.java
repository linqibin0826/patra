package com.patra.ingest.app.outbox.constants;

/**
 * Outbox business semantic tags enum (op_type field values).
 *
 * <p>Defines business event semantics stored in {@code ing_outbox_message.op_type} field.
 *
 * <h3>Design Principles</h3>
 *
 * <ul>
 *   <li><b>Business-Oriented</b>: Tags describe specific business events, not generic CRUD
 *       operations
 *   <li><b>Domain-Specific</b>: Each tag has clear business meaning within its domain context
 *   <li><b>Event Semantics</b>: Tags represent "what happened" from a business perspective
 * </ul>
 *
 * <h3>SQL Field Reference</h3>
 *
 * <pre>
 * `op_type` VARCHAR(32) NOT NULL COMMENT 'Business semantic tag, e.g., TASK_READY / EVENT_PUBLISHED'
 * </pre>
 *
 * <h3>Usage Example</h3>
 *
 * <pre>{@code
 * // In TaskOutboxPublisher
 * @Override
 * protected String getOperationType(TaskQueuedEvent event) {
 *     return OutboxBusinessTags.TASK_READY.getCode();
 * }
 * }</pre>
 *
 * <h3>Tag Naming Convention</h3>
 *
 * <p>Format: {@code <DOMAIN>_<EVENT_SEMANTIC>}
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum OutboxBusinessTags {

  // ==================== Task Domain ====================

  /** Task ready for execution. */
  TASK_READY("TASK_READY", "Task ready - scheduler created task and queued for execution"),

  // ==================== Literature Domain ====================

  /** Literature data ready for catalog ingestion. */
  LITERATURE_DATA_READY(
      "LITERATURE_DATA_READY",
      "Literature data ready - aggregated object storage payload available"),

  // ==================== Technical Operations ====================

  /** Storage metadata retry for failed recording operations. */
  STORAGE_METADATA_RETRY(
      "STORAGE_METADATA_RETRY",
      "Storage metadata retry - failed metadata recording request pending retry");

  private final String code;
  private final String description;

  OutboxBusinessTags(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /**
   * Returns the business tag code.
   *
   * <p>This value is stored in {@code ing_outbox_message.op_type} field.
   *
   * @return Business tag code (e.g., "TASK_READY", "PLAN_CREATED")
   */
  public String getCode() {
    return code;
  }

  /**
   * Returns the human-readable description.
   *
   * @return Description of this business tag
   */
  public String getDescription() {
    return description;
  }

  /**
   * Finds enum by code.
   *
   * @param code Business tag code
   * @return Matching enum value
   * @throws IllegalArgumentException if code is not found
   */
  public static OutboxBusinessTags fromCode(String code) {
    for (OutboxBusinessTags tag : values()) {
      if (tag.code.equals(code)) {
        return tag;
      }
    }
    throw new IllegalArgumentException("Unknown business tag code: " + code);
  }
}
