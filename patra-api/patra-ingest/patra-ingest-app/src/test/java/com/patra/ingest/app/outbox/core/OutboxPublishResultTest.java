package com.patra.ingest.app.outbox.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link OutboxPublishResult}.
 * <p>Tests factory methods, merge logic, and failure detail records.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("OutboxPublishResult unit tests")
class OutboxPublishResultTest {

    @Test
    @DisplayName("should create success result with correct counts")
    void shouldCreateSuccessResult() {
        // when
        OutboxPublishResult result = OutboxPublishResult.success(10, Duration.ofMillis(100));

        // then
        assertThat(result.getSuccessCount()).isEqualTo(10);
        assertThat(result.getFailureCount()).isEqualTo(0);
        assertThat(result.getFailures()).isEmpty();
        assertThat(result.getDuration()).isEqualTo(Duration.ofMillis(100));
        assertThat(result.hasFailures()).isFalse();
    }

    @Test
    @DisplayName("should create failure result with error message")
    void shouldCreateFailureResult() {
        // when
        OutboxPublishResult result = OutboxPublishResult.failure("Database error", Duration.ofMillis(50));

        // then
        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.hasFailures()).isTrue();
        assertThat(result.getFailures()).hasSize(1);

        OutboxPublishResult.FailureDetail failure = result.getFailures().get(0);
        assertThat(failure.aggregateId()).isNull();
        assertThat(failure.dedupKey()).isNull();
        assertThat(failure.errorMessage()).isEqualTo("Database error");
        assertThat(failure.errorType()).isEqualTo("GENERAL_ERROR");
    }

    @Test
    @DisplayName("should create partial result with mixed success and failures")
    void shouldCreatePartialResult() {
        // given
        List<OutboxPublishResult.FailureDetail> failures = List.of(
                new OutboxPublishResult.FailureDetail("agg-1", "dedup-1", "JSON error", "JSON_ERROR"),
                new OutboxPublishResult.FailureDetail("agg-2", "dedup-2", "Validation error", "VALIDATION_ERROR")
        );

        // when
        OutboxPublishResult result = OutboxPublishResult.partial(8, failures, Duration.ofMillis(200));

        // then
        assertThat(result.getSuccessCount()).isEqualTo(8);
        assertThat(result.getFailureCount()).isEqualTo(2);
        assertThat(result.hasFailures()).isTrue();
        assertThat(result.getFailures()).hasSize(2);
        assertThat(result.getDuration()).isEqualTo(Duration.ofMillis(200));
    }

    @Test
    @DisplayName("should create empty result")
    void shouldCreateEmptyResult() {
        // when
        OutboxPublishResult result = OutboxPublishResult.empty(Duration.ofMillis(5));

        // then
        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getFailureCount()).isEqualTo(0);
        assertThat(result.getFailures()).isEmpty();
        assertThat(result.hasFailures()).isFalse();
        assertThat(result.getDuration()).isEqualTo(Duration.ofMillis(5));
    }

    @Test
    @DisplayName("should merge two success results")
    void shouldMergeTwoSuccessResults() {
        // given
        OutboxPublishResult result1 = OutboxPublishResult.success(5, Duration.ofMillis(100));
        OutboxPublishResult result2 = OutboxPublishResult.success(3, Duration.ofMillis(50));

        // when
        OutboxPublishResult merged = result1.merge(result2);

        // then
        assertThat(merged.getSuccessCount()).isEqualTo(8);
        assertThat(merged.getFailureCount()).isEqualTo(0);
        assertThat(merged.getFailures()).isEmpty();
        assertThat(merged.getDuration()).isEqualTo(Duration.ofMillis(150));
    }

    @Test
    @DisplayName("should merge success and partial results")
    void shouldMergeSuccessAndPartialResults() {
        // given
        OutboxPublishResult result1 = OutboxPublishResult.success(5, Duration.ofMillis(100));

        List<OutboxPublishResult.FailureDetail> failures = List.of(
                new OutboxPublishResult.FailureDetail("agg-1", "dedup-1", "Error", "BUILD_ERROR")
        );
        OutboxPublishResult result2 = OutboxPublishResult.partial(3, failures, Duration.ofMillis(50));

        // when
        OutboxPublishResult merged = result1.merge(result2);

        // then
        assertThat(merged.getSuccessCount()).isEqualTo(8);
        assertThat(merged.getFailureCount()).isEqualTo(1);
        assertThat(merged.getFailures()).hasSize(1);
        assertThat(merged.getDuration()).isEqualTo(Duration.ofMillis(150));
    }

    @Test
    @DisplayName("should merge multiple partial results and aggregate failures")
    void shouldMergeMultiplePartialResults() {
        // given
        List<OutboxPublishResult.FailureDetail> failures1 = List.of(
                new OutboxPublishResult.FailureDetail("agg-1", "dedup-1", "Error 1", "JSON_ERROR")
        );
        OutboxPublishResult result1 = OutboxPublishResult.partial(2, failures1, Duration.ofMillis(100));

        List<OutboxPublishResult.FailureDetail> failures2 = List.of(
                new OutboxPublishResult.FailureDetail("agg-2", "dedup-2", "Error 2", "DB_ERROR")
        );
        OutboxPublishResult result2 = OutboxPublishResult.partial(3, failures2, Duration.ofMillis(50));

        // when
        OutboxPublishResult merged = result1.merge(result2);

        // then
        assertThat(merged.getSuccessCount()).isEqualTo(5);
        assertThat(merged.getFailureCount()).isEqualTo(2);
        assertThat(merged.getFailures()).hasSize(2);
        assertThat(merged.getFailures())
                .extracting(OutboxPublishResult.FailureDetail::errorMessage)
                .containsExactly("Error 1", "Error 2");
    }

    @Test
    @DisplayName("should merge empty results")
    void shouldMergeEmptyResults() {
        // given
        OutboxPublishResult result1 = OutboxPublishResult.empty(Duration.ofMillis(10));
        OutboxPublishResult result2 = OutboxPublishResult.empty(Duration.ofMillis(5));

        // when
        OutboxPublishResult merged = result1.merge(result2);

        // then
        assertThat(merged.getSuccessCount()).isEqualTo(0);
        assertThat(merged.getFailureCount()).isEqualTo(0);
        assertThat(merged.getFailures()).isEmpty();
        assertThat(merged.getDuration()).isEqualTo(Duration.ofMillis(15));
    }

    @Test
    @DisplayName("should return immutable failure list")
    void shouldReturnImmutableFailureList() {
        // given
        List<OutboxPublishResult.FailureDetail> failures = List.of(
                new OutboxPublishResult.FailureDetail("agg-1", "dedup-1", "Error", "BUILD_ERROR")
        );
        OutboxPublishResult result = OutboxPublishResult.partial(1, failures, Duration.ofMillis(100));

        // when & then
        assertThatThrownBy(() -> result.getFailures().add(
                new OutboxPublishResult.FailureDetail("agg-2", "dedup-2", "Error 2", "JSON_ERROR")
        ))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("should construct FailureDetail record correctly")
    void shouldConstructFailureDetailRecord() {
        // when
        OutboxPublishResult.FailureDetail detail = new OutboxPublishResult.FailureDetail(
                "aggregate-123",
                "dedup-xyz",
                "JSON parsing failed",
                "JSON_ERROR"
        );

        // then
        assertThat(detail.aggregateId()).isEqualTo("aggregate-123");
        assertThat(detail.dedupKey()).isEqualTo("dedup-xyz");
        assertThat(detail.errorMessage()).isEqualTo("JSON parsing failed");
        assertThat(detail.errorType()).isEqualTo("JSON_ERROR");
    }

    @Test
    @DisplayName("should generate toString with success/failure counts and duration")
    void shouldGenerateToString() {
        // given
        OutboxPublishResult result = OutboxPublishResult.success(10, Duration.ofMillis(150));

        // when
        String str = result.toString();

        // then
        assertThat(str).contains("success=10");
        assertThat(str).contains("failure=0");
        assertThat(str).contains("duration=150ms");
    }

    @Test
    @DisplayName("should handle zero duration")
    void shouldHandleZeroDuration() {
        // when
        OutboxPublishResult result = OutboxPublishResult.success(1, Duration.ZERO);

        // then
        assertThat(result.getDuration()).isEqualTo(Duration.ZERO);
        assertThat(result.toString()).contains("duration=0ms");
    }

    @Test
    @DisplayName("should throw NullPointerException when duration is null")
    void shouldThrowNullPointerExceptionWhenDurationIsNull() {
        // when & then
        assertThatThrownBy(() -> OutboxPublishResult.success(1, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should throw NullPointerException when failures list is null")
    void shouldThrowNullPointerExceptionWhenFailuresIsNull() {
        // when & then
        assertThatThrownBy(() -> OutboxPublishResult.partial(1, null, Duration.ofMillis(100)))
                .isInstanceOf(NullPointerException.class);
    }
}
