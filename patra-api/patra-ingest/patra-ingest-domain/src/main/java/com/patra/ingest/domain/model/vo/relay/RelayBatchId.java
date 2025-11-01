package com.patra.ingest.domain.model.vo.relay;

import cn.hutool.core.util.IdUtil;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Relay batch ID value object for grouping relay logs from the same job execution.
 *
 * <p>Format: {@code yyyyMMddHHmmss-xxxxxxxx} where:
 *
 * <ul>
 *   <li>yyyyMMddHHmmss: UTC timestamp when relay batch triggered
 *   <li>xxxxxxxx: 8-character random hex UUID for uniqueness
 * </ul>
 *
 * <p>Example: {@code 20251031150000-a1b2c3d4}
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>Group all relay logs from a single job execution
 *   <li>Enable batch-level statistics and analysis
 *   <li>Facilitate troubleshooting by identifying related relay attempts
 * </ul>
 *
 * @author Papertrace Team
 * @since 2.0
 */
public final class RelayBatchId {

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

  private static final Pattern VALID_PATTERN = Pattern.compile("\\d{14}-[a-f0-9]{8}");

  private final String value;

  private RelayBatchId(String value) {
    this.value = Objects.requireNonNull(value, "RelayBatchId value must not be null");
  }

  /**
   * Generates a new batch ID based on the given trigger timestamp.
   *
   * <p>The generated ID is guaranteed to be unique across concurrent calls due to the random UUID
   * component.
   *
   * @param triggeredAt relay batch trigger time (UTC)
   * @return new RelayBatchId instance
   * @throws NullPointerException if triggeredAt is null
   */
  public static RelayBatchId generate(Instant triggeredAt) {
    Objects.requireNonNull(triggeredAt, "triggeredAt must not be null");
    String timestamp = FORMATTER.format(triggeredAt);
    String uuid = IdUtil.fastSimpleUUID().substring(0, 8);
    return new RelayBatchId(timestamp + "-" + uuid);
  }

  /**
   * Reconstructs RelayBatchId from a string value (with format validation).
   *
   * @param value batch ID string (format: yyyyMMddHHmmss-xxxxxxxx)
   * @return RelayBatchId instance
   * @throws IllegalArgumentException if format is invalid
   */
  public static RelayBatchId of(String value) {
    if (value == null || !VALID_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "Invalid RelayBatchId format: " + value + ", expected: yyyyMMddHHmmss-xxxxxxxx");
    }
    return new RelayBatchId(value);
  }

  /**
   * Gets the underlying string value of this batch ID.
   *
   * @return batch ID string
   */
  public String getValue() {
    return value;
  }

  /**
   * Extracts the timestamp portion of this batch ID.
   *
   * <p>Example: {@code 20251031150000-a1b2c3d4} → {@code 20251031150000}
   *
   * @return timestamp string (yyyyMMddHHmmss format)
   */
  public String getTimestampPart() {
    return value.substring(0, 14);
  }

  /**
   * Extracts the UUID portion of this batch ID.
   *
   * <p>Example: {@code 20251031150000-a1b2c3d4} → {@code a1b2c3d4}
   *
   * @return UUID string (8-character hex)
   */
  public String getUuidPart() {
    return value.substring(15);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof RelayBatchId that)) {
      return false;
    }
    return value.equals(that.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return value;
  }
}
