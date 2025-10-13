package com.patra.ingest.app.outbox.config;

import com.patra.ingest.app.outbox.constants.OutboxAggregateTypes;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Outbox publisher framework.
 *
 * <p>Supports dynamic configuration via Nacos or application.yml:
 *
 * <pre>
 * papertrace:
 *   outbox:
 *     publisher:
 *       batch-size: 500
 *       max-batch-size: 500
 *       allowed-aggregate-types:
 *         - Task
 *       metrics:
 *         enabled: true
 * </pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@Component
@ConfigurationProperties(
    prefix = "papertrace.outbox.publisher") // Ensure this prefix matches your configuration source;
// update docs if changed.
public class OutboxPublisherProperties {

  /**
   * Default batch size for bulk insert operations (default: 500).
   *
   * <p>Used for partitioning large batches to avoid exceeding database limits.
   */
  private int batchSize = 500;

  /**
   * Maximum allowed batch size for IN clause queries (default: 500).
   *
   * <p>Prevents performance degradation from large IN queries.
   */
  private int maxBatchSize = 500;

  /**
   * Allowed aggregate types for Micrometer metrics tag cardinality control.
   *
   * <p>Prevents metric label cardinality explosion in Prometheus.
   *
   * @see OutboxAggregateTypes
   */
  private Set<String> allowedAggregateTypes =
      new HashSet<>(Set.of(OutboxAggregateTypes.TASK.getCode()));

  /** Metrics configuration. */
  private Metrics metrics = new Metrics();

  /** Nested configuration for metrics. */
  @Data
  public static class Metrics {
    /**
     * Whether to enable Micrometer metrics recording (default: true).
     *
     * <p>Set to false to disable all Outbox metrics recording for performance optimization.
     */
    private boolean enabled = true;
  }
}
