package com.patra.ingest.app.outbox.constants;

/**
 * Outbox channel enum.
 *
 * <p>Defines messaging channels used in the Outbox framework for:
 *
 * <ul>
 *   <li>Message routing and topic mapping
 *   <li>Deduplication key scoping (channel + dedupKey uniqueness)
 *   <li>Channel-based filtering and monitoring
 * </ul>
 *
 * <h3>Naming Convention</h3>
 *
 * <p>Channels follow hierarchical underscore structure: {@code MODULE_SEMANTIC_STATE}
 *
 * <h3>Usage Example</h3>
 *
 * <pre>{@code
 * @Override
 * protected String getChannel() {
 *     return OutboxChannels.INGEST_TASK_READY.getCode();
 * }
 * }</pre>
 *
 * <h3>Design Considerations</h3>
 *
 * <ul>
 *   <li>Channel names map to MQ topics/exchanges for routing
 *   <li>Channels provide deduplication scope (unique constraint: channel + dedupKey)
 *   <li>Channel-based metrics enable fine-grained monitoring
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum OutboxChannels {

  /** Ingest task ready channel. */
  INGEST_TASK_READY(
      "INGEST_TASK_READY", "Ingest task ready - scheduler created task and queued for execution"),

  /** Literature data ready channel. */
  LITERATURE_DATA_READY(
      "LITERATURE_DATA_READY",
      "Literature data ready - ingestion batches committed to object storage"),

  /** Storage metadata internal retry channel. */
  STORAGE_METADATA_INTERNAL(
      "storage.metadata.internal",
      "Storage metadata internal - technical retry channel for failed metadata operations");

  private final String code;
  private final String description;

  OutboxChannels(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /**
   * Returns the channel code.
   *
   * <p>This value is stored in {@code ing_outbox_message.channel} field.
   *
   * @return Channel code (e.g., "INGEST_TASK_READY")
   */
  public String getCode() {
    return code;
  }

  /**
   * Returns the human-readable description.
   *
   * @return Description of this channel
   */
  public String getDescription() {
    return description;
  }

  /**
   * Finds enum by code.
   *
   * @param code Channel code
   * @return Matching enum value
   * @throws IllegalArgumentException if code is not found
   */
  public static OutboxChannels fromCode(String code) {
    for (OutboxChannels channel : values()) {
      if (channel.code.equals(code)) {
        return channel;
      }
    }
    throw new IllegalArgumentException("Unknown channel code: " + code);
  }
}
