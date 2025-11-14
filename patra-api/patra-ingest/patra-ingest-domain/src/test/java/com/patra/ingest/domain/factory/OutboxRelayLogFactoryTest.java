package com.patra.ingest.domain.factory;

import static org.assertj.core.api.Assertions.*;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.entity.OutboxRelayLog;
import com.patra.ingest.domain.model.enums.RelayStatus;
import com.patra.ingest.domain.model.vo.relay.RelayBatchId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link OutboxRelayLogFactory} 单元测试。
 *
 * <p>测试策略:
 *
 * <ul>
 *   <li>纯 Java 单元测试(Domain 层无框架依赖)
 *   <li>使用固定 Clock 确保时间戳的确定性
 *   <li>验证工厂正确委托给 OutboxMessage.computeNextAttempt()
 *   <li>验证日志创建的一致性和完整性
 * </ul>
 *
 * @author Patra Team
 * @since 0.1.0
 */
@DisplayName("OutboxRelayLogFactory 单元测试")
class OutboxRelayLogFactoryTest {

  private OutboxRelayLogFactory factory;
  private Clock fixedClock;
  private Instant fixedNow;

  private OutboxMessage sampleMessage;
  private RelayBatchId batchId;
  private String leaseOwner;
  private Instant startTime;

  @BeforeEach
  void setUp() {
    // Given: 固定时间戳以确保测试可预测
    fixedNow = Instant.parse("2025-11-05T10:00:00Z");
    fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);
    factory = new OutboxRelayLogFactory(fixedClock);

    // Given: 准备测试数据
    sampleMessage =
        OutboxMessage.builder()
            .id(1001L)
            .aggregateType("Article")
            .aggregateId(5001L)
            .channel("INGEST")
            .opType("CREATED")
            .partitionKey("article-5001")
            .dedupKey("article-5001-created-20251105")
            .payloadJson("{\"title\":\"Test Article\"}")
            .retryCount(2) // 当前已重试 2 次，下次尝试应为 3
            .build();

    batchId = RelayBatchId.generate(fixedNow);
    leaseOwner = "instance-1-job-1-thread-1-uuid";
    startTime = fixedNow.minus(Duration.ofMillis(150));
  }

  @Nested
  @DisplayName("租约竞争失败场景")
  class LeaseMissedTests {

    @Test
    @DisplayName("应创建 LEASE_MISSED 状态的日志")
    void shouldCreateLeaseMissedLog() {
      // When: 创建租约竞争失败的日志
      OutboxRelayLog log =
          factory.createForLeaseMissed(sampleMessage, batchId, leaseOwner, startTime);

      // Then: 验证基本字段
      assertThat(log.getOutboxMessageId()).isEqualTo(1001L);
      assertThat(log.getRelayBatchId()).isEqualTo(batchId.getValue());
      assertThat(log.getChannel()).isEqualTo("INGEST");
      assertThat(log.getPartitionKey()).isEqualTo("article-5001");
      assertThat(log.getLeaseOwner()).isEqualTo(leaseOwner);
      assertThat(log.getRelayStatus()).isEqualTo(RelayStatus.LEASE_MISSED);
    }

    @Test
    @DisplayName("应正确计算尝试次数（基于 OutboxMessage.computeNextAttempt）")
    void shouldComputeCorrectAttemptNumber() {
      // When: 创建租约竞争失败的日志
      OutboxRelayLog log =
          factory.createForLeaseMissed(sampleMessage, batchId, leaseOwner, startTime);

      // Then: 尝试次数应为 3（retryCount=2 + 1）
      assertThat(log.getAttemptNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("应正确设置时间戳和持续时间")
    void shouldSetCorrectTimestamps() {
      // When: 创建租约竞争失败的日志
      OutboxRelayLog log =
          factory.createForLeaseMissed(sampleMessage, batchId, leaseOwner, startTime);

      // Then: 验证时间戳
      assertThat(log.getStartedAt()).isEqualTo(startTime);
      assertThat(log.getCompletedAt()).isEqualTo(fixedNow);
      assertThat(log.getDurationMs()).isEqualTo(150); // fixedNow - startTime
    }

    @Test
    @DisplayName("应正确处理首次尝试（retryCount 为 null）")
    void shouldHandleFirstAttempt() {
      // Given: 首次尝试的消息（retryCount 为 null）
      OutboxMessage firstAttemptMessage = sampleMessage.toBuilder().retryCount(null).build();

      // When: 创建租约竞争失败的日志
      OutboxRelayLog log =
          factory.createForLeaseMissed(firstAttemptMessage, batchId, leaseOwner, startTime);

      // Then: 尝试次数应为 1
      assertThat(log.getAttemptNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("应正确处理首次尝试（retryCount 为 0）")
    void shouldHandleFirstAttemptWithZeroRetry() {
      // Given: 首次尝试的消息（retryCount 为 0）
      OutboxMessage firstAttemptMessage = sampleMessage.toBuilder().retryCount(0).build();

      // When: 创建租约竞争失败的日志
      OutboxRelayLog log =
          factory.createForLeaseMissed(firstAttemptMessage, batchId, leaseOwner, startTime);

      // Then: 尝试次数应为 1
      assertThat(log.getAttemptNumber()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("发布成功场景")
  class PublishedTests {

    @Test
    @DisplayName("应创建 PUBLISHED 状态的日志")
    void shouldCreatePublishedLog() {
      // Given: 发布完成时间
      Instant publishedAt = fixedNow.minus(Duration.ofMillis(50));

      // When: 创建发布成功的日志
      OutboxRelayLog log =
          factory.createForPublished(sampleMessage, batchId, leaseOwner, startTime, publishedAt);

      // Then: 验证基本字段
      assertThat(log.getOutboxMessageId()).isEqualTo(1001L);
      assertThat(log.getRelayBatchId()).isEqualTo(batchId.getValue());
      assertThat(log.getChannel()).isEqualTo("INGEST");
      assertThat(log.getRelayStatus()).isEqualTo(RelayStatus.PUBLISHED);
    }

    @Test
    @DisplayName("应使用提供的 publishedAt 作为完成时间")
    void shouldUseProvidedPublishedAt() {
      // Given: 发布完成时间
      Instant publishedAt = fixedNow.minus(Duration.ofMillis(50));

      // When: 创建发布成功的日志
      OutboxRelayLog log =
          factory.createForPublished(sampleMessage, batchId, leaseOwner, startTime, publishedAt);

      // Then: 验证时间戳
      assertThat(log.getStartedAt()).isEqualTo(startTime);
      assertThat(log.getCompletedAt()).isEqualTo(publishedAt);
      assertThat(log.getDurationMs()).isEqualTo(100); // publishedAt - startTime
    }

    @Test
    @DisplayName("应正确计算尝试次数")
    void shouldComputeCorrectAttemptNumber() {
      // Given: 发布完成时间
      Instant publishedAt = fixedNow;

      // When: 创建发布成功的日志
      OutboxRelayLog log =
          factory.createForPublished(sampleMessage, batchId, leaseOwner, startTime, publishedAt);

      // Then: 尝试次数应为 3（retryCount=2 + 1）
      assertThat(log.getAttemptNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("不应包含错误信息")
    void shouldNotContainErrorInfo() {
      // Given: 发布完成时间
      Instant publishedAt = fixedNow;

      // When: 创建发布成功的日志
      OutboxRelayLog log =
          factory.createForPublished(sampleMessage, batchId, leaseOwner, startTime, publishedAt);

      // Then: 错误字段应为 null
      assertThat(log.getErrorCode()).isNull();
      assertThat(log.getErrorMessage()).isNull();
      assertThat(log.getErrorKind()).isNull();
      assertThat(log.getNextRetryAt()).isNull();
    }
  }

  @Nested
  @DisplayName("延迟重试场景")
  class DeferredTests {

    @Test
    @DisplayName("应创建 DEFERRED 状态的日志")
    void shouldCreateDeferredLog() {
      // Given: 重试时间和错误信息
      Instant nextRetryAt = fixedNow.plus(Duration.ofMinutes(5));
      String errorCode = "NETWORK_TIMEOUT";
      String errorMessage = "Connection timeout after 30 seconds";
      String errorKind = "TRANSIENT";

      // When: 创建延迟重试的日志
      OutboxRelayLog log =
          factory.createForDeferred(
              sampleMessage,
              batchId,
              leaseOwner,
              startTime,
              nextRetryAt,
              errorCode,
              errorMessage,
              errorKind);

      // Then: 验证基本字段
      assertThat(log.getOutboxMessageId()).isEqualTo(1001L);
      assertThat(log.getRelayStatus()).isEqualTo(RelayStatus.DEFERRED);
      assertThat(log.getErrorCode()).isEqualTo(errorCode);
      assertThat(log.getErrorMessage()).isEqualTo(errorMessage);
      assertThat(log.getErrorKind()).isEqualTo(errorKind);
      assertThat(log.getNextRetryAt()).isEqualTo(nextRetryAt);
    }

    @Test
    @DisplayName("应使用 Clock 生成完成时间")
    void shouldUseClockForCompletedAt() {
      // Given: 重试时间和错误信息
      Instant nextRetryAt = fixedNow.plus(Duration.ofMinutes(5));

      // When: 创建延迟重试的日志
      OutboxRelayLog log =
          factory.createForDeferred(
              sampleMessage,
              batchId,
              leaseOwner,
              startTime,
              nextRetryAt,
              "ERROR_CODE",
              "Error message",
              "TRANSIENT");

      // Then: 完成时间应为 fixedNow
      assertThat(log.getCompletedAt()).isEqualTo(fixedNow);
      assertThat(log.getDurationMs()).isEqualTo(150); // fixedNow - startTime
    }

    @Test
    @DisplayName("应截断超长的错误消息（超过 512 字符）")
    void shouldTruncateLongErrorMessage() {
      // Given: 超过 512 字符的错误消息
      String longErrorMessage = "A".repeat(600);
      Instant nextRetryAt = fixedNow.plus(Duration.ofMinutes(5));

      // When: 创建延迟重试的日志
      OutboxRelayLog log =
          factory.createForDeferred(
              sampleMessage,
              batchId,
              leaseOwner,
              startTime,
              nextRetryAt,
              "ERROR_CODE",
              longErrorMessage,
              "TRANSIENT");

      // Then: 错误消息应被截断为 512 字符
      assertThat(log.getErrorMessage()).hasSize(512);
      assertThat(log.getErrorMessage()).isEqualTo("A".repeat(512));
    }

    @Test
    @DisplayName("应保持错误消息不变（未超过 512 字符）")
    void shouldKeepShortErrorMessageUnchanged() {
      // Given: 短错误消息
      String shortErrorMessage = "Short error message";
      Instant nextRetryAt = fixedNow.plus(Duration.ofMinutes(5));

      // When: 创建延迟重试的日志
      OutboxRelayLog log =
          factory.createForDeferred(
              sampleMessage,
              batchId,
              leaseOwner,
              startTime,
              nextRetryAt,
              "ERROR_CODE",
              shortErrorMessage,
              "TRANSIENT");

      // Then: 错误消息应保持不变
      assertThat(log.getErrorMessage()).isEqualTo(shortErrorMessage);
    }

    @Test
    @DisplayName("应正确处理 null 错误消息")
    void shouldHandleNullErrorMessage() {
      // Given: null 错误消息
      Instant nextRetryAt = fixedNow.plus(Duration.ofMinutes(5));

      // When: 创建延迟重试的日志
      OutboxRelayLog log =
          factory.createForDeferred(
              sampleMessage,
              batchId,
              leaseOwner,
              startTime,
              nextRetryAt,
              "ERROR_CODE",
              null,
              "TRANSIENT");

      // Then: 错误消息应为 null
      assertThat(log.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("应正确计算尝试次数")
    void shouldComputeCorrectAttemptNumber() {
      // Given: 重试时间和错误信息
      Instant nextRetryAt = fixedNow.plus(Duration.ofMinutes(5));

      // When: 创建延迟重试的日志
      OutboxRelayLog log =
          factory.createForDeferred(
              sampleMessage,
              batchId,
              leaseOwner,
              startTime,
              nextRetryAt,
              "ERROR_CODE",
              "Error message",
              "TRANSIENT");

      // Then: 尝试次数应为 3（retryCount=2 + 1）
      assertThat(log.getAttemptNumber()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("永久失败场景")
  class FailedTests {

    @Test
    @DisplayName("应创建 FAILED 状态的日志")
    void shouldCreateFailedLog() {
      // Given: 错误信息
      String errorCode = "MAX_RETRIES_EXCEEDED";
      String errorMessage = "Failed after 5 attempts";
      String errorKind = "FATAL";

      // When: 创建永久失败的日志
      OutboxRelayLog log =
          factory.createForFailed(
              sampleMessage, batchId, leaseOwner, startTime, errorCode, errorMessage, errorKind);

      // Then: 验证基本字段
      assertThat(log.getOutboxMessageId()).isEqualTo(1001L);
      assertThat(log.getRelayStatus()).isEqualTo(RelayStatus.FAILED);
      assertThat(log.getErrorCode()).isEqualTo(errorCode);
      assertThat(log.getErrorMessage()).isEqualTo(errorMessage);
      assertThat(log.getErrorKind()).isEqualTo(errorKind);
    }

    @Test
    @DisplayName("不应包含 nextRetryAt 字段")
    void shouldNotContainNextRetryAt() {
      // When: 创建永久失败的日志
      OutboxRelayLog log =
          factory.createForFailed(
              sampleMessage, batchId, leaseOwner, startTime, "ERROR_CODE", "Error", "FATAL");

      // Then: nextRetryAt 应为 null
      assertThat(log.getNextRetryAt()).isNull();
    }

    @Test
    @DisplayName("应使用 Clock 生成完成时间")
    void shouldUseClockForCompletedAt() {
      // When: 创建永久失败的日志
      OutboxRelayLog log =
          factory.createForFailed(
              sampleMessage, batchId, leaseOwner, startTime, "ERROR_CODE", "Error", "FATAL");

      // Then: 完成时间应为 fixedNow
      assertThat(log.getCompletedAt()).isEqualTo(fixedNow);
      assertThat(log.getDurationMs()).isEqualTo(150); // fixedNow - startTime
    }

    @Test
    @DisplayName("应截断超长的错误消息（超过 512 字符）")
    void shouldTruncateLongErrorMessage() {
      // Given: 超过 512 字符的错误消息
      String longErrorMessage = "B".repeat(700);

      // When: 创建永久失败的日志
      OutboxRelayLog log =
          factory.createForFailed(
              sampleMessage,
              batchId,
              leaseOwner,
              startTime,
              "ERROR_CODE",
              longErrorMessage,
              "FATAL");

      // Then: 错误消息应被截断为 512 字符
      assertThat(log.getErrorMessage()).hasSize(512);
      assertThat(log.getErrorMessage()).isEqualTo("B".repeat(512));
    }

    @Test
    @DisplayName("应保持错误消息不变（未超过 512 字符）")
    void shouldKeepShortErrorMessageUnchanged() {
      // Given: 短错误消息
      String shortErrorMessage = "Fatal error occurred";

      // When: 创建永久失败的日志
      OutboxRelayLog log =
          factory.createForFailed(
              sampleMessage,
              batchId,
              leaseOwner,
              startTime,
              "ERROR_CODE",
              shortErrorMessage,
              "FATAL");

      // Then: 错误消息应保持不变
      assertThat(log.getErrorMessage()).isEqualTo(shortErrorMessage);
    }

    @Test
    @DisplayName("应正确处理 null 错误消息")
    void shouldHandleNullErrorMessage() {
      // When: 创建永久失败的日志（null 错误消息）
      OutboxRelayLog log =
          factory.createForFailed(
              sampleMessage, batchId, leaseOwner, startTime, "ERROR_CODE", null, "FATAL");

      // Then: 错误消息应为 null
      assertThat(log.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("应正确计算尝试次数")
    void shouldComputeCorrectAttemptNumber() {
      // When: 创建永久失败的日志
      OutboxRelayLog log =
          factory.createForFailed(
              sampleMessage, batchId, leaseOwner, startTime, "ERROR_CODE", "Error", "FATAL");

      // Then: 尝试次数应为 3（retryCount=2 + 1）
      assertThat(log.getAttemptNumber()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryTests {

    @Test
    @DisplayName("应正确处理错误消息恰好 512 字符")
    void shouldHandleExactly512CharErrorMessage() {
      // Given: 恰好 512 字符的错误消息
      String exactErrorMessage = "C".repeat(512);
      Instant nextRetryAt = fixedNow.plus(Duration.ofMinutes(5));

      // When: 创建延迟重试的日志
      OutboxRelayLog log =
          factory.createForDeferred(
              sampleMessage,
              batchId,
              leaseOwner,
              startTime,
              nextRetryAt,
              "ERROR_CODE",
              exactErrorMessage,
              "TRANSIENT");

      // Then: 错误消息应保持 512 字符不变
      assertThat(log.getErrorMessage()).hasSize(512);
      assertThat(log.getErrorMessage()).isEqualTo(exactErrorMessage);
    }

    @Test
    @DisplayName("应正确处理空字符串错误消息")
    void shouldHandleEmptyErrorMessage() {
      // Given: 空字符串错误消息
      Instant nextRetryAt = fixedNow.plus(Duration.ofMinutes(5));

      // When: 创建延迟重试的日志
      OutboxRelayLog log =
          factory.createForDeferred(
              sampleMessage,
              batchId,
              leaseOwner,
              startTime,
              nextRetryAt,
              "ERROR_CODE",
              "",
              "TRANSIENT");

      // Then: 错误消息应为空字符串
      assertThat(log.getErrorMessage()).isEmpty();
    }

    @Test
    @DisplayName("应正确处理 startTime 与 completedAt 相同（0ms 持续时间）")
    void shouldHandleZeroDuration() {
      // Given: startTime 与 fixedNow 相同
      Instant sameTime = fixedNow;

      // When: 创建租约竞争失败的日志
      OutboxRelayLog log =
          factory.createForLeaseMissed(sampleMessage, batchId, leaseOwner, sameTime);

      // Then: 持续时间应为 0
      assertThat(log.getDurationMs()).isEqualTo(0);
      assertThat(log.getStartedAt()).isEqualTo(sameTime);
      assertThat(log.getCompletedAt()).isEqualTo(fixedNow);
    }

    @Test
    @DisplayName("应正确处理多种不同场景的持续时间计算")
    void shouldHandleVariousDurationCalculations() {
      // Given: 不同的持续时间场景
      Instant start1 = fixedNow.minus(Duration.ofMillis(1));
      Instant start2 = fixedNow.minus(Duration.ofSeconds(1));
      Instant start3 = fixedNow.minus(Duration.ofMinutes(1));

      // When & Then: 验证各种持续时间计算
      OutboxRelayLog log1 =
          factory.createForLeaseMissed(sampleMessage, batchId, leaseOwner, start1);
      assertThat(log1.getDurationMs()).isEqualTo(1);

      OutboxRelayLog log2 =
          factory.createForLeaseMissed(sampleMessage, batchId, leaseOwner, start2);
      assertThat(log2.getDurationMs()).isEqualTo(1000);

      OutboxRelayLog log3 =
          factory.createForLeaseMissed(sampleMessage, batchId, leaseOwner, start3);
      assertThat(log3.getDurationMs()).isEqualTo(60000);
    }
  }

  @Nested
  @DisplayName("Factory 构造与 Clock 注入")
  class FactoryConstructionTests {

    @Test
    @DisplayName("应正确注入 Clock 并在日志创建中使用")
    void shouldInjectClockAndUseInLogCreation() {
      // Given: 使用不同的固定时间创建工厂
      Instant differentTime = Instant.parse("2025-12-01T12:00:00Z");
      Clock differentClock = Clock.fixed(differentTime, ZoneOffset.UTC);
      OutboxRelayLogFactory factoryWithDifferentClock = new OutboxRelayLogFactory(differentClock);

      // When: 创建租约竞争失败的日志
      OutboxRelayLog log =
          factoryWithDifferentClock.createForLeaseMissed(
              sampleMessage, batchId, leaseOwner, startTime);

      // Then: 完成时间应使用注入的 Clock
      assertThat(log.getCompletedAt()).isEqualTo(differentTime);
    }

    @Test
    @DisplayName("应允许使用系统时钟创建工厂")
    void shouldAllowSystemClockCreation() {
      // When: 使用系统时钟创建工厂
      OutboxRelayLogFactory factoryWithSystemClock = new OutboxRelayLogFactory(Clock.systemUTC());

      // Then: 不应抛出异常
      assertThatCode(
              () ->
                  factoryWithSystemClock.createForLeaseMissed(
                      sampleMessage, batchId, leaseOwner, startTime))
          .doesNotThrowAnyException();
    }
  }
}
