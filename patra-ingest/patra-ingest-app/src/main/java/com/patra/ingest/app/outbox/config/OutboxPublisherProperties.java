package com.patra.ingest.app.outbox.config;

import com.patra.ingest.app.outbox.constants.OutboxAggregateTypes;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Configuration properties for Outbox publisher framework.
 * <p>Supports dynamic configuration via Nacos or application.yml:</p>
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
@ConfigurationProperties(prefix = "papertrace.outbox.publisher") // TODO 更换前缀并修改相关代码和注释、文档信息。
public class OutboxPublisherProperties {

    /**
     * Default batch size for bulk insert operations (default: 500).
     * <p>Used for partitioning large batches to avoid exceeding database limits.</p>
     */
    private int batchSize = 500;

    /**
     * Maximum allowed batch size for IN clause queries (default: 500).
     * <p>Prevents performance degradation from large IN queries.</p>
     */
    private int maxBatchSize = 500;

    /**
     * Allowed aggregate types for Micrometer metrics tag cardinality control.
     * <p>Prevents metric label cardinality explosion in Prometheus.</p>
     * @see OutboxAggregateTypes
     */
    private Set<String> allowedAggregateTypes = new HashSet<>(Set.of(
            OutboxAggregateTypes.TASK.getCode()
    ));

    /**
     * Metrics configuration.
     */
    private Metrics metrics = new Metrics();

    /**
     * Nested configuration for metrics.
     */
    @Data
    public static class Metrics {
        /**
         * Whether to enable Micrometer metrics recording (default: true).
         * <p>Set to false to disable all Outbox metrics recording for performance optimization.</p>
         */
        private boolean enabled = true;
    }
}
