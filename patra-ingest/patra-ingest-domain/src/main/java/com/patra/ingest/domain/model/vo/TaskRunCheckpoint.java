package com.patra.ingest.domain.model.vo;

/**
 * Value object that captures a task run checkpoint snapshot.
 *
 * <p>Stores incremental recovery information (JSON) for restarts or resume-from-checkpoint flows.
 *
 * <ul>
 *   <li>{@code raw}: original JSON string (normalized to {@code null} when blank)
 * </ul>
 *
 * Invariant: blank values normalize to {@code null}; the structure is not parsed until consumption
 * time.
 */
public record TaskRunCheckpoint(String raw) {

  public TaskRunCheckpoint {
    if (raw != null && raw.isBlank()) {
      raw = null;
    }
  }

  /** Create an empty checkpoint with no state. */
  public static TaskRunCheckpoint empty() {
    return new TaskRunCheckpoint(null);
  }

  /** Indicates whether the checkpoint contains serialized state. */
  public boolean isPresent() {
    return raw != null && !raw.isBlank();
  }
}
