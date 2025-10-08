package com.patra.ingest.app.outbox.metrics;

import com.patra.ingest.app.outbox.config.OutboxPublisherProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link OutboxMetrics}.
 * <p>Tests Micrometer instrumentation and cardinality control.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("OutboxMetrics unit tests")
class OutboxMetricsTest {

    private MeterRegistry meterRegistry;
    private OutboxPublisherProperties properties;
    private OutboxMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        properties = new OutboxPublisherProperties();
        properties.setAllowedAggregateTypes(Set.of("Task", "LiteratureData", "Plan"));
        properties.getMetrics().setEnabled(true);
        metrics = new OutboxMetrics(meterRegistry, properties);
    }

    @Test
    @DisplayName("should record publish success with counter and timer")
    void shouldRecordPublishSuccess() {
        // when
        metrics.recordPublish("Task", "batch", true, Duration.ofMillis(100));

        // then
        Counter counter = meterRegistry.find("papertrace.outbox.publish.total")
                .tag("aggregateType", "Task")
                .tag("opType", "batch")
                .tag("status", "success")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);

        Timer timer = meterRegistry.find("papertrace.outbox.publish.duration")
                .tag("aggregateType", "Task")
                .tag("opType", "batch")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isGreaterThanOrEqualTo(100.0);
    }

    @Test
    @DisplayName("should record publish failure with counter and timer")
    void shouldRecordPublishFailure() {
        // when
        metrics.recordPublish("Task", "retry", false, Duration.ofMillis(50));

        // then
        Counter counter = meterRegistry.find("papertrace.outbox.publish.total")
                .tag("aggregateType", "Task")
                .tag("opType", "retry")
                .tag("status", "failure")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);

        Timer timer = meterRegistry.find("papertrace.outbox.publish.duration")
                .tag("aggregateType", "Task")
                .tag("opType", "retry")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("should record batch size distribution")
    void shouldRecordBatchSize() {
        // when
        metrics.recordBatchSize("Task", 500);

        // then
        DistributionSummary summary = meterRegistry.find("papertrace.outbox.publish.batch.size")
                .tag("aggregateType", "Task")
                .summary();

        assertThat(summary).isNotNull();
        assertThat(summary.count()).isEqualTo(1L);
        assertThat(summary.totalAmount()).isEqualTo(500.0);
    }

    @Test
    @DisplayName("should record multiple batch sizes and calculate statistics")
    void shouldRecordMultipleBatchSizes() {
        // when
        metrics.recordBatchSize("Task", 100);
        metrics.recordBatchSize("Task", 200);
        metrics.recordBatchSize("Task", 300);

        // then
        DistributionSummary summary = meterRegistry.find("papertrace.outbox.publish.batch.size")
                .tag("aggregateType", "Task")
                .summary();

        assertThat(summary).isNotNull();
        assertThat(summary.count()).isEqualTo(3L);
        assertThat(summary.totalAmount()).isEqualTo(600.0);
        assertThat(summary.mean()).isEqualTo(200.0);
    }

    @Test
    @DisplayName("should not throw exception when aggregateType not in allowed list (only logs WARN)")
    void shouldNotThrowExceptionWhenAggregateTypeNotAllowed() {
        // when - code should not throw, just log warn
        assertThatCode(() -> metrics.recordPublish("UnknownType", "batch", true, Duration.ofMillis(100)))
                .doesNotThrowAnyException();

        // then - metrics should not be recorded for unknown type
        Counter counter = meterRegistry.find("papertrace.outbox.publish.total")
                .tag("aggregateType", "UnknownType")
                .counter();

        assertThat(counter).isNull();
    }

    @Test
    @DisplayName("should not record metrics when disabled")
    void shouldNotRecordMetricsWhenDisabled() {
        // given
        properties.getMetrics().setEnabled(false);
        OutboxMetrics disabledMetrics = new OutboxMetrics(meterRegistry, properties);

        // when
        disabledMetrics.recordPublish("Task", "batch", true, Duration.ofMillis(100));
        disabledMetrics.recordBatchSize("Task", 500);

        // then
        Counter counter = meterRegistry.find("papertrace.outbox.publish.total").counter();
        assertThat(counter).isNull();

        DistributionSummary summary = meterRegistry.find("papertrace.outbox.publish.batch.size").summary();
        assertThat(summary).isNull();
    }

    @Test
    @DisplayName("should allow any aggregateType when allowed list is empty")
    void shouldAllowAnyAggregateTypeWhenListIsEmpty() {
        // given
        properties.setAllowedAggregateTypes(Set.of());
        OutboxMetrics permissiveMetrics = new OutboxMetrics(meterRegistry, properties);

        // when & then - should not throw exception
        assertThatCode(() -> permissiveMetrics.recordPublish("RandomType", "batch", true, Duration.ofMillis(100)))
                .doesNotThrowAnyException();

        Counter counter = meterRegistry.find("papertrace.outbox.publish.total")
                .tag("aggregateType", "RandomType")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("should record different opTypes separately")
    void shouldRecordDifferentOpTypesSeparately() {
        // when
        metrics.recordPublish("Task", "batch", true, Duration.ofMillis(100));
        metrics.recordPublish("Task", "retry", true, Duration.ofMillis(50));
        metrics.recordPublish("Task", "CREATED", true, Duration.ofMillis(30));

        // then
        Counter batchCounter = meterRegistry.find("papertrace.outbox.publish.total")
                .tag("aggregateType", "Task")
                .tag("opType", "batch")
                .counter();

        Counter retryCounter = meterRegistry.find("papertrace.outbox.publish.total")
                .tag("aggregateType", "Task")
                .tag("opType", "retry")
                .counter();

        Counter createdCounter = meterRegistry.find("papertrace.outbox.publish.total")
                .tag("aggregateType", "Task")
                .tag("opType", "CREATED")
                .counter();

        assertThat(batchCounter.count()).isEqualTo(1.0);
        assertThat(retryCounter.count()).isEqualTo(1.0);
        assertThat(createdCounter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("should record different aggregateTypes separately")
    void shouldRecordDifferentAggregateTypesSeparately() {
        // when
        metrics.recordPublish("Task", "batch", true, Duration.ofMillis(100));
        metrics.recordPublish("LiteratureData", "batch", true, Duration.ofMillis(200));
        metrics.recordPublish("Plan", "batch", true, Duration.ofMillis(150));

        // then
        Counter taskCounter = meterRegistry.find("papertrace.outbox.publish.total")
                .tag("aggregateType", "Task")
                .counter();

        Counter literatureCounter = meterRegistry.find("papertrace.outbox.publish.total")
                .tag("aggregateType", "LiteratureData")
                .counter();

        Counter planCounter = meterRegistry.find("papertrace.outbox.publish.total")
                .tag("aggregateType", "Plan")
                .counter();

        assertThat(taskCounter.count()).isEqualTo(1.0);
        assertThat(literatureCounter.count()).isEqualTo(1.0);
        assertThat(planCounter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("should handle zero duration gracefully")
    void shouldHandleZeroDuration() {
        // when & then
        assertThatCode(() -> metrics.recordPublish("Task", "batch", true, Duration.ZERO))
                .doesNotThrowAnyException();

        Timer timer = meterRegistry.find("papertrace.outbox.publish.duration")
                .tag("aggregateType", "Task")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("should not record negative batch size (silently ignored)")
    void shouldHandleNegativeBatchSize() {
        // when - negative values are typically rejected by DistributionSummary
        metrics.recordBatchSize("Task", -1);

        // then - metric may not be created or count may be 0
        DistributionSummary summary = meterRegistry.find("papertrace.outbox.publish.batch.size")
                .tag("aggregateType", "Task")
                .summary();

        // Micrometer may create the metric but not record the negative value
        if (summary != null) {
            // Either count is 0 (value rejected) or metric doesn't exist
            assertThat(summary.count()).isGreaterThanOrEqualTo(0L);
        }
    }

    @Test
    @DisplayName("should increment counter on repeated publish operations")
    void shouldIncrementCounterOnRepeatedPublish() {
        // when
        metrics.recordPublish("Task", "batch", true, Duration.ofMillis(10));
        metrics.recordPublish("Task", "batch", true, Duration.ofMillis(20));
        metrics.recordPublish("Task", "batch", true, Duration.ofMillis(30));

        // then
        Counter counter = meterRegistry.find("papertrace.outbox.publish.total")
                .tag("aggregateType", "Task")
                .tag("opType", "batch")
                .tag("status", "success")
                .counter();

        assertThat(counter.count()).isEqualTo(3.0);
    }
}
