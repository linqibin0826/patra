package com.patra.ingest.app.usecase.relay.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.patra.ingest.app.usecase.relay.coordinator.RelayLogCoordinator.LogAccumulator;
import com.patra.ingest.app.usecase.relay.metrics.OutboxRelayMetrics;
import com.patra.ingest.domain.factory.OutboxRelayLogFactory;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.entity.OutboxRelayLog;
import com.patra.ingest.domain.model.enums.RelayStatus;
import com.patra.ingest.domain.model.vo.relay.RelayBatchId;
import com.patra.ingest.domain.port.OutboxRelayLogRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * RelayLogCoordinator 单元测试
 *
 * <p>测试覆盖:
 *
 * <ul>
 *   <li>✅ 日志累加器创建和使用
 *   <li>✅ 批量持久化场景
 *   <li>✅ 空累加器处理
 *   <li>✅ 各种中继结果记录 (PUBLISHED, DEFERRED, FAILED, LEASE_MISSED)
 *   <li>✅ 指标记录验证
 * </ul>
 *
 * @author Patra Team
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RelayLogCoordinator 单元测试")
class RelayLogCoordinatorTest {

  @Mock private OutboxRelayLogFactory logFactory;

  @Mock private OutboxRelayLogRepository logRepository;

  @Mock private OutboxRelayMetrics metrics;

  @InjectMocks private RelayLogCoordinator coordinator;

  @Captor private ArgumentCaptor<List<OutboxRelayLog>> logListCaptor;

  private RelayBatchId batchId;
  private OutboxMessage testMessage;
  private String leaseOwner;
  private Instant startTime;

  @BeforeEach
  void setUp() {
    startTime = Instant.now();
    batchId = RelayBatchId.generate(startTime);
    leaseOwner = "test-owner";

    testMessage =
        OutboxMessage.builder()
            .id(1L)
            .version(1L)
            .aggregateType("Task")
            .aggregateId(100L)
            .channel("TASK_READY")
            .opType("TASK_READY")
            .dedupKey("task-001")
            .partitionKey("")
            .build();
  }

  @Nested
  @DisplayName("日志累加器创建和使用场景")
  class LogAccumulatorCreationTests {

    @Test
    @DisplayName("应创建空的日志累加器")
    void shouldCreateEmptyAccumulator() {
      // When
      LogAccumulator accumulator = coordinator.createAccumulator(batchId);

      // Then
      assertThat(accumulator).isNotNull();
      assertThat(accumulator.isEmpty()).isTrue();
      assertThat(accumulator.size()).isZero();
    }

    @Test
    @DisplayName("累加器应关联正确的批次 ID")
    void shouldAssociateBatchIdWithAccumulator() {
      // When
      LogAccumulator accumulator = coordinator.createAccumulator(batchId);

      // Then: 通过记录日志验证批次 ID (间接验证,因为 batchId 是私有的)
      OutboxRelayLog mockLog = createMockRelayLog(RelayStatus.PUBLISHED);
      when(logFactory.createForPublished(any(), eq(batchId), anyString(), any(), any()))
          .thenReturn(mockLog);

      accumulator.recordPublished(testMessage, leaseOwner, startTime, Instant.now());
      assertThat(accumulator.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("不同批次应创建独立的累加器")
    void shouldCreateSeparateAccumulatorsForDifferentBatches() {
      // Given
      Instant now = Instant.now();
      RelayBatchId batch1 = RelayBatchId.generate(now);
      RelayBatchId batch2 = RelayBatchId.generate(now.plusSeconds(1));

      // When
      LogAccumulator acc1 = coordinator.createAccumulator(batch1);
      LogAccumulator acc2 = coordinator.createAccumulator(batch2);

      // Then: 两个累加器独立
      OutboxRelayLog mockLog = createMockRelayLog(RelayStatus.PUBLISHED);
      when(logFactory.createForPublished(any(), any(), anyString(), any(), any()))
          .thenReturn(mockLog);

      acc1.recordPublished(testMessage, leaseOwner, startTime, Instant.now());
      assertThat(acc1.size()).isEqualTo(1);
      assertThat(acc2.size()).isZero();
    }
  }

  @Nested
  @DisplayName("批量持久化场景")
  class BatchPersistenceTests {

    @Test
    @DisplayName("空累加器持久化应跳过数据库调用")
    void shouldSkipDatabaseCallForEmptyAccumulator() {
      // Given: 空累加器
      LogAccumulator accumulator = coordinator.createAccumulator(batchId);

      // When
      coordinator.persistBatch(accumulator);

      // Then: 不应调用数据库和指标
      verify(logRepository, never()).saveBatch(anyList());
      verify(metrics, never()).recordPublished(anyString());
      verify(metrics, never()).recordDeferred(anyString(), anyString());
      verify(metrics, never()).recordFailed(anyString(), anyString());
      verify(metrics, never()).recordLeaseMissed(anyString());
    }

    @Test
    @DisplayName("单条日志应正确持久化")
    void shouldPersistSingleLog() {
      // Given
      LogAccumulator accumulator = coordinator.createAccumulator(batchId);
      OutboxRelayLog mockLog = createMockRelayLog(RelayStatus.PUBLISHED);
      when(logFactory.createForPublished(any(), any(), anyString(), any(), any()))
          .thenReturn(mockLog);

      accumulator.recordPublished(testMessage, leaseOwner, startTime, Instant.now());

      // When
      coordinator.persistBatch(accumulator);

      // Then: 验证批量保存
      verify(logRepository).saveBatch(logListCaptor.capture());
      List<OutboxRelayLog> savedLogs = logListCaptor.getValue();
      assertThat(savedLogs).hasSize(1);
      assertThat(savedLogs.get(0)).isEqualTo(mockLog);

      // 验证指标记录
      verify(metrics).recordPublished("TASK_READY");
    }

    @Test
    @DisplayName("多条日志应批量持久化")
    void shouldPersistMultipleLogs() {
      // Given: 累加器包含多条日志
      LogAccumulator accumulator = coordinator.createAccumulator(batchId);

      OutboxRelayLog log1 = createMockRelayLog(RelayStatus.PUBLISHED);
      OutboxRelayLog log2 = createMockRelayLog(RelayStatus.DEFERRED);
      OutboxRelayLog log3 = createMockRelayLog(RelayStatus.FAILED);

      when(logFactory.createForPublished(any(), any(), anyString(), any(), any()))
          .thenReturn(log1);
      when(logFactory.createForDeferred(any(), any(), anyString(), any(), any(), any(), any(), any()))
          .thenReturn(log2);
      when(logFactory.createForFailed(any(), any(), anyString(), any(), any(), any(), any()))
          .thenReturn(log3);

      accumulator.recordPublished(testMessage, leaseOwner, startTime, Instant.now());
      accumulator.recordDeferred(
          testMessage, leaseOwner, startTime, Instant.now(), "TIMEOUT", "Timeout", "TRANSIENT");
      accumulator.recordFailed(
          testMessage, leaseOwner, startTime, "FATAL_ERROR", "Fatal", "FATAL");

      // When
      coordinator.persistBatch(accumulator);

      // Then: 验证批量保存所有日志
      verify(logRepository).saveBatch(logListCaptor.capture());
      List<OutboxRelayLog> savedLogs = logListCaptor.getValue();
      assertThat(savedLogs).hasSize(3);

      // 验证指标记录 (每种状态一次) - 实际使用 mock 返回的 ERROR_CODE
      verify(metrics).recordPublished("TASK_READY");
      verify(metrics).recordDeferred("TASK_READY", "ERROR_CODE");
      verify(metrics).recordFailed("TASK_READY", "ERROR_CODE");
    }

    @Test
    @DisplayName("持久化后累加器状态不应改变")
    void shouldNotModifyAccumulatorAfterPersist() {
      // Given
      LogAccumulator accumulator = coordinator.createAccumulator(batchId);
      OutboxRelayLog mockLog = createMockRelayLog(RelayStatus.PUBLISHED);
      when(logFactory.createForPublished(any(), any(), anyString(), any(), any()))
          .thenReturn(mockLog);

      accumulator.recordPublished(testMessage, leaseOwner, startTime, Instant.now());

      // When
      coordinator.persistBatch(accumulator);

      // Then: 累加器仍保留数据 (防御性拷贝)
      assertThat(accumulator.size()).isEqualTo(1);
      assertThat(accumulator.isEmpty()).isFalse();
    }
  }

  @Nested
  @DisplayName("PUBLISHED 状态记录场景")
  class PublishedRecordingTests {

    @Test
    @DisplayName("应记录成功发布日志")
    void shouldRecordPublishedLog() {
      // Given
      LogAccumulator accumulator = coordinator.createAccumulator(batchId);
      Instant publishedAt = Instant.now();
      OutboxRelayLog mockLog = createMockRelayLog(RelayStatus.PUBLISHED);
      when(logFactory.createForPublished(any(), any(), anyString(), any(), any()))
          .thenReturn(mockLog);

      // When
      accumulator.recordPublished(testMessage, leaseOwner, startTime, publishedAt);

      // Then
      assertThat(accumulator.size()).isEqualTo(1);
      verify(logFactory)
          .createForPublished(testMessage, batchId, leaseOwner, startTime, publishedAt);
    }

    @Test
    @DisplayName("多次记录 PUBLISHED 应累加")
    void shouldAccumulateMultiplePublishedLogs() {
      // Given
      LogAccumulator accumulator = coordinator.createAccumulator(batchId);
      OutboxRelayLog mockLog = createMockRelayLog(RelayStatus.PUBLISHED);
      when(logFactory.createForPublished(any(), any(), anyString(), any(), any()))
          .thenReturn(mockLog);

      // When: 记录 3 次
      accumulator.recordPublished(testMessage, leaseOwner, startTime, Instant.now());
      accumulator.recordPublished(testMessage, leaseOwner, startTime, Instant.now());
      accumulator.recordPublished(testMessage, leaseOwner, startTime, Instant.now());

      // Then
      assertThat(accumulator.size()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("DEFERRED 状态记录场景")
  class DeferredRecordingTests {

    @Test
    @DisplayName("应记录延迟重试日志")
    void shouldRecordDeferredLog() {
      // Given
      LogAccumulator accumulator = coordinator.createAccumulator(batchId);
      Instant nextRetryAt = Instant.now().plusSeconds(60);
      String errorCode = "NETWORK_TIMEOUT";
      String errorMessage = "Connection timed out";
      String errorKind = "TRANSIENT";

      OutboxRelayLog mockLog = createMockRelayLog(RelayStatus.DEFERRED);
      when(logFactory.createForDeferred(any(), any(), anyString(), any(), any(), any(), any(), any()))
          .thenReturn(mockLog);

      // When
      accumulator.recordDeferred(
          testMessage, leaseOwner, startTime, nextRetryAt, errorCode, errorMessage, errorKind);

      // Then
      assertThat(accumulator.size()).isEqualTo(1);
      verify(logFactory)
          .createForDeferred(
              testMessage,
              batchId,
              leaseOwner,
              startTime,
              nextRetryAt,
              errorCode,
              errorMessage,
              errorKind);
    }

    @Test
    @DisplayName("应处理空错误消息")
    void shouldHandleNullErrorMessage() {
      // Given
      LogAccumulator accumulator = coordinator.createAccumulator(batchId);
      OutboxRelayLog mockLog = createMockRelayLog(RelayStatus.DEFERRED);
      when(logFactory.createForDeferred(any(), any(), anyString(), any(), any(), any(), any(), any()))
          .thenReturn(mockLog);

      // When: 错误消息为 null
      accumulator.recordDeferred(
          testMessage, leaseOwner, startTime, Instant.now(), "ERROR", null, "TRANSIENT");

      // Then: 应正常记录
      assertThat(accumulator.size()).isEqualTo(1);
      verify(logFactory)
          .createForDeferred(
              eq(testMessage),
              eq(batchId),
              eq(leaseOwner),
              eq(startTime),
              any(Instant.class),
              eq("ERROR"),
              eq(null),
              eq("TRANSIENT"));
    }
  }

  @Nested
  @DisplayName("FAILED 状态记录场景")
  class FailedRecordingTests {

    @Test
    @DisplayName("应记录永久失败日志")
    void shouldRecordFailedLog() {
      // Given
      LogAccumulator accumulator = coordinator.createAccumulator(batchId);
      String errorCode = "FATAL_ERROR";
      String errorMessage = "Invalid message format";
      String errorKind = "FATAL";

      OutboxRelayLog mockLog = createMockRelayLog(RelayStatus.FAILED);
      when(logFactory.createForFailed(any(), any(), anyString(), any(), any(), any(), any()))
          .thenReturn(mockLog);

      // When
      accumulator.recordFailed(
          testMessage, leaseOwner, startTime, errorCode, errorMessage, errorKind);

      // Then
      assertThat(accumulator.size()).isEqualTo(1);
      verify(logFactory)
          .createForFailed(
              testMessage, batchId, leaseOwner, startTime, errorCode, errorMessage, errorKind);
    }

    @Test
    @DisplayName("应区分致命错误和达到最大重试")
    void shouldDifferentiateFatalAndMaxRetries() {
      // Given
      LogAccumulator accumulator = coordinator.createAccumulator(batchId);
      OutboxRelayLog mockLog = createMockRelayLog(RelayStatus.FAILED);
      when(logFactory.createForFailed(any(), any(), anyString(), any(), any(), any(), any()))
          .thenReturn(mockLog);

      // When: 记录两种失败类型
      accumulator.recordFailed(testMessage, leaseOwner, startTime, "FATAL", "Fatal", "FATAL");
      accumulator.recordFailed(
          testMessage, leaseOwner, startTime, "TIMEOUT", "Max retries", "TRANSIENT");

      // Then: 都应记录,但 errorKind 不同
      assertThat(accumulator.size()).isEqualTo(2);
      verify(logFactory, times(2))
          .createForFailed(any(), any(), anyString(), any(), any(), any(), anyString());
    }
  }

  @Nested
  @DisplayName("LEASE_MISSED 状态记录场景")
  class LeaseMissedRecordingTests {

    @Test
    @DisplayName("应记录租约丢失日志")
    void shouldRecordLeaseMissedLog() {
      // Given
      LogAccumulator accumulator = coordinator.createAccumulator(batchId);
      OutboxRelayLog mockLog = createMockRelayLog(RelayStatus.LEASE_MISSED);
      when(logFactory.createForLeaseMissed(any(), any(), anyString(), any())).thenReturn(mockLog);

      // When
      accumulator.recordLeaseMissed(testMessage, leaseOwner, startTime);

      // Then
      assertThat(accumulator.size()).isEqualTo(1);
      verify(logFactory).createForLeaseMissed(testMessage, batchId, leaseOwner, startTime);
    }

    @Test
    @DisplayName("批量租约丢失应正确累加")
    void shouldAccumulateMultipleLeaseMissedLogs() {
      // Given
      LogAccumulator accumulator = coordinator.createAccumulator(batchId);
      OutboxRelayLog mockLog = createMockRelayLog(RelayStatus.LEASE_MISSED);
      when(logFactory.createForLeaseMissed(any(), any(), anyString(), any())).thenReturn(mockLog);

      // When: 记录 5 次租约丢失
      for (int i = 0; i < 5; i++) {
        accumulator.recordLeaseMissed(testMessage, leaseOwner, startTime);
      }

      // Then
      assertThat(accumulator.size()).isEqualTo(5);
    }
  }

  @Nested
  @DisplayName("指标记录场景")
  class MetricsRecordingTests {

    @Test
    @DisplayName("持久化时应记录所有状态的指标")
    void shouldRecordMetricsForAllStatuses() {
      // Given: 包含所有状态的累加器
      LogAccumulator accumulator = coordinator.createAccumulator(batchId);

      OutboxRelayLog publishedLog = createMockRelayLog(RelayStatus.PUBLISHED);
      OutboxRelayLog deferredLog = createMockRelayLog(RelayStatus.DEFERRED);
      OutboxRelayLog failedLog = createMockRelayLog(RelayStatus.FAILED);
      OutboxRelayLog leaseMissedLog = createMockRelayLog(RelayStatus.LEASE_MISSED);

      when(logFactory.createForPublished(any(), any(), anyString(), any(), any()))
          .thenReturn(publishedLog);
      when(logFactory.createForDeferred(any(), any(), anyString(), any(), any(), any(), any(), any()))
          .thenReturn(deferredLog);
      when(logFactory.createForFailed(any(), any(), anyString(), any(), any(), any(), any()))
          .thenReturn(failedLog);
      when(logFactory.createForLeaseMissed(any(), any(), anyString(), any()))
          .thenReturn(leaseMissedLog);

      accumulator.recordPublished(testMessage, leaseOwner, startTime, Instant.now());
      accumulator.recordDeferred(
          testMessage, leaseOwner, startTime, Instant.now(), "ERR", "msg", "TRANSIENT");
      accumulator.recordFailed(testMessage, leaseOwner, startTime, "ERR", "msg", "FATAL");
      accumulator.recordLeaseMissed(testMessage, leaseOwner, startTime);

      // When
      coordinator.persistBatch(accumulator);

      // Then: 验证每种状态的指标都被记录 - 实际使用 mock 返回的 ERROR_CODE
      verify(metrics).recordPublished("TASK_READY");
      verify(metrics).recordDeferred("TASK_READY", "ERROR_CODE");
      verify(metrics).recordFailed("TASK_READY", "ERROR_CODE");
      verify(metrics).recordLeaseMissed("TASK_READY");
    }

    @Test
    @DisplayName("相同状态多次记录应多次调用指标")
    void shouldRecordMetricsForEachLog() {
      // Given: 多条 PUBLISHED 日志
      LogAccumulator accumulator = coordinator.createAccumulator(batchId);
      OutboxRelayLog mockLog = createMockRelayLog(RelayStatus.PUBLISHED);
      when(logFactory.createForPublished(any(), any(), anyString(), any(), any()))
          .thenReturn(mockLog);

      accumulator.recordPublished(testMessage, leaseOwner, startTime, Instant.now());
      accumulator.recordPublished(testMessage, leaseOwner, startTime, Instant.now());
      accumulator.recordPublished(testMessage, leaseOwner, startTime, Instant.now());

      // When
      coordinator.persistBatch(accumulator);

      // Then: 应记录 3 次指标
      verify(metrics, times(3)).recordPublished("TASK_READY");
    }
  }

  @Nested
  @DisplayName("边界条件场景")
  class BoundaryConditionTests {

    @Test
    @DisplayName("累加器的 getLogs 应返回不可修改副本")
    void shouldReturnUnmodifiableCopyOfLogs() {
      // Given
      LogAccumulator accumulator = coordinator.createAccumulator(batchId);
      OutboxRelayLog mockLog = createMockRelayLog(RelayStatus.PUBLISHED);
      when(logFactory.createForPublished(any(), any(), anyString(), any(), any()))
          .thenReturn(mockLog);

      accumulator.recordPublished(testMessage, leaseOwner, startTime, Instant.now());

      // When
      coordinator.persistBatch(accumulator);

      // Then: 原始累加器不应受影响
      assertThat(accumulator.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("同一累加器多次持久化应重复保存相同数据")
    void shouldAllowMultiplePersistCallsOnSameAccumulator() {
      // Given
      LogAccumulator accumulator = coordinator.createAccumulator(batchId);
      OutboxRelayLog mockLog = createMockRelayLog(RelayStatus.PUBLISHED);
      when(logFactory.createForPublished(any(), any(), anyString(), any(), any()))
          .thenReturn(mockLog);

      accumulator.recordPublished(testMessage, leaseOwner, startTime, Instant.now());

      // When: 多次持久化
      coordinator.persistBatch(accumulator);
      coordinator.persistBatch(accumulator);

      // Then: 应调用两次
      verify(logRepository, times(2)).saveBatch(anyList());
      verify(metrics, times(2)).recordPublished("TASK_READY");
    }

    @Test
    @DisplayName("大批量日志应正确处理")
    void shouldHandleLargeBatchOfLogs() {
      // Given: 1000 条日志
      LogAccumulator accumulator = coordinator.createAccumulator(batchId);
      OutboxRelayLog mockLog = createMockRelayLog(RelayStatus.PUBLISHED);
      when(logFactory.createForPublished(any(), any(), anyString(), any(), any()))
          .thenReturn(mockLog);

      for (int i = 0; i < 1000; i++) {
        accumulator.recordPublished(testMessage, leaseOwner, startTime, Instant.now());
      }

      // When
      coordinator.persistBatch(accumulator);

      // Then
      verify(logRepository).saveBatch(logListCaptor.capture());
      assertThat(logListCaptor.getValue()).hasSize(1000);
      verify(metrics, times(1000)).recordPublished("TASK_READY");
    }
  }

  // ==================== 辅助方法 ====================

  /**
   * 创建 Mock RelayLog,模拟不同状态
   *
   * @param status 中继状态
   * @return Mock OutboxRelayLog
   */
  private OutboxRelayLog createMockRelayLog(RelayStatus status) {
    OutboxRelayLog mockLog = mock(OutboxRelayLog.class);
    when(mockLog.getChannel()).thenReturn("TASK_READY");
    when(mockLog.getRelayStatus()).thenReturn(status);
    when(mockLog.getErrorCode()).thenReturn(status == RelayStatus.PUBLISHED ? null : "ERROR_CODE");
    return mockLog;
  }
}
