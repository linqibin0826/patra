package com.patra.ingest.domain.model.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// OutboxMessage 单元测试。
/// 
/// 测试策略：
/// 
/// - 纯 Java 单元测试，不依赖 Spring 容器
///   - 使用 TestDataBuilder 模式构建测试数据
///   - 遵循 Given-When-Then 结构
///   - 使用 AssertJ 流畅断言
/// 
/// 测试覆盖范围:
/// 
/// - ✅ Builder 模式创建和字段验证
///   - ✅ 必填字段校验
///   - ✅ 默认值设置
///   - ✅ 状态机行为方法
///   - ✅ 租约机制
///   - ✅ 重试机制
///   - ✅ 延迟发布逻辑
///   - ✅ refreshForRetry 方法
///   - ✅ 不变性测试
///   - ✅ 边界条件测试
/// 
/// @author linqibin
/// @since 0.1.0
@DisplayName("OutboxMessage 单元测试")
class OutboxMessageTest {

  // ========== Builder 创建与字段验证 ==========

  @Nested
  @DisplayName("Builder 创建与字段验证")
  class BuilderCreationTests {

    @Test
    @DisplayName("应该成功创建完整的 OutboxMessage")
    void shouldCreateCompleteOutboxMessage() {
      // Given
      Long id = 1001L;
      Long version = 5L;
      String aggregateType = "PlanAggregate";
      Long aggregateId = 2001L;
      String channel = "plan_events";
      String opType = "CREATED";
      String partitionKey = "plan:2001";
      String dedupKey = "plan:2001:created:1640000000000";
      String payloadJson = "{\"planId\":2001,\"status\":\"READY\"}";
      String headersJson = "{\"source\":\"plan-service\"}";
      Instant notBefore = Instant.parse("2025-01-01T10:00:00Z");
      String statusCode = "PENDING";
      Integer retryCount = 0;
      Instant nextRetryAt = null;
      String errorCode = null;
      String errorMsg = null;
      String leaseOwner = null;
      Instant leaseExpireAt = null;

      // When
      OutboxMessage message =
          OutboxMessage.builder()
              .id(id)
              .version(version)
              .aggregateType(aggregateType)
              .aggregateId(aggregateId)
              .channel(channel)
              .opType(opType)
              .partitionKey(partitionKey)
              .dedupKey(dedupKey)
              .payloadJson(payloadJson)
              .headersJson(headersJson)
              .notBefore(notBefore)
              .statusCode(statusCode)
              .retryCount(retryCount)
              .nextRetryAt(nextRetryAt)
              .errorCode(errorCode)
              .errorMsg(errorMsg)
              .leaseOwner(leaseOwner)
              .leaseExpireAt(leaseExpireAt)
              .build();

      // Then
      assertThat(message).isNotNull();
      assertThat(message.getId()).isEqualTo(id);
      assertThat(message.getVersion()).isEqualTo(version);
      assertThat(message.getAggregateType()).isEqualTo(aggregateType);
      assertThat(message.getAggregateId()).isEqualTo(aggregateId);
      assertThat(message.getChannel()).isEqualTo(channel);
      assertThat(message.getOpType()).isEqualTo(opType);
      assertThat(message.getPartitionKey()).isEqualTo(partitionKey);
      assertThat(message.getDedupKey()).isEqualTo(dedupKey);
      assertThat(message.getPayloadJson()).isEqualTo(payloadJson);
      assertThat(message.getHeadersJson()).isEqualTo(headersJson);
      assertThat(message.getNotBefore()).isEqualTo(notBefore);
      assertThat(message.getStatusCode()).isEqualTo(statusCode);
      assertThat(message.getRetryCount()).isEqualTo(retryCount);
      assertThat(message.getNextRetryAt()).isNull();
      assertThat(message.getErrorCode()).isNull();
      assertThat(message.getErrorMsg()).isNull();
      assertThat(message.getLeaseOwner()).isNull();
      assertThat(message.getLeaseExpireAt()).isNull();
    }

    @Test
    @DisplayName("应该使用最小必填字段成功创建")
    void shouldCreateWithMinimalRequiredFields() {
      // Given - 只提供必填字段
      String aggregateType = "TaskAggregate";
      Long aggregateId = 3001L;
      String channel = "task_events";
      String opType = "COMPLETED";
      String partitionKey = "task:3001";
      String dedupKey = "task:3001:completed:1640000000000";

      // When
      OutboxMessage message =
          OutboxMessage.builder()
              .aggregateType(aggregateType)
              .aggregateId(aggregateId)
              .channel(channel)
              .opType(opType)
              .partitionKey(partitionKey)
              .dedupKey(dedupKey)
              .build();

      // Then - 必填字段应该被正确设置
      assertThat(message).isNotNull();
      assertThat(message.getAggregateType()).isEqualTo(aggregateType);
      assertThat(message.getAggregateId()).isEqualTo(aggregateId);
      assertThat(message.getChannel()).isEqualTo(channel);
      assertThat(message.getOpType()).isEqualTo(opType);
      assertThat(message.getPartitionKey()).isEqualTo(partitionKey);
      assertThat(message.getDedupKey()).isEqualTo(dedupKey);

      // 可选字段应该为 null
      assertThat(message.getId()).isNull();
      assertThat(message.getPayloadJson()).isNull();
      assertThat(message.getHeadersJson()).isNull();
      assertThat(message.getNotBefore()).isNull();
      assertThat(message.getNextRetryAt()).isNull();
      assertThat(message.getErrorCode()).isNull();
      assertThat(message.getErrorMsg()).isNull();
      assertThat(message.getLeaseOwner()).isNull();
      assertThat(message.getLeaseExpireAt()).isNull();
    }

    @Test
    @DisplayName("应该正确处理 toBuilder 复制")
    void shouldHandleToBuilderCorrectly() {
      // Given - 原始消息
      OutboxMessage original = OutboxMessageTestDataBuilder.aPendingMessage().build();

      // When - 使用 toBuilder 创建副本
      OutboxMessage copy = original.toBuilder().build();

      // Then - 所有字段应该相同
      assertThat(copy.getId()).isEqualTo(original.getId());
      assertThat(copy.getVersion()).isEqualTo(original.getVersion());
      assertThat(copy.getAggregateType()).isEqualTo(original.getAggregateType());
      assertThat(copy.getAggregateId()).isEqualTo(original.getAggregateId());
      assertThat(copy.getChannel()).isEqualTo(original.getChannel());
      assertThat(copy.getOpType()).isEqualTo(original.getOpType());
      assertThat(copy.getPartitionKey()).isEqualTo(original.getPartitionKey());
      assertThat(copy.getDedupKey()).isEqualTo(original.getDedupKey());
      assertThat(copy.getPayloadJson()).isEqualTo(original.getPayloadJson());
      assertThat(copy.getHeadersJson()).isEqualTo(original.getHeadersJson());
      assertThat(copy.getNotBefore()).isEqualTo(original.getNotBefore());
      assertThat(copy.getStatusCode()).isEqualTo(original.getStatusCode());
      assertThat(copy.getRetryCount()).isEqualTo(original.getRetryCount());
      assertThat(copy.getNextRetryAt()).isEqualTo(original.getNextRetryAt());
      assertThat(copy.getErrorCode()).isEqualTo(original.getErrorCode());
      assertThat(copy.getErrorMsg()).isEqualTo(original.getErrorMsg());
      assertThat(copy.getLeaseOwner()).isEqualTo(original.getLeaseOwner());
      assertThat(copy.getLeaseExpireAt()).isEqualTo(original.getLeaseExpireAt());
    }

    @Test
    @DisplayName("应该支持通过 toBuilder 修改字段")
    void shouldSupportModifyingFieldsViaToBuilder() {
      // Given - 原始消息
      OutboxMessage original = OutboxMessageTestDataBuilder.aPendingMessage().build();

      // When - 通过 toBuilder 修改状态
      String newStatus = "PUBLISHED";
      OutboxMessage modified = original.toBuilder().statusCode(newStatus).build();

      // Then - 只有修改的字段应该变化
      assertThat(modified.getStatusCode()).isEqualTo(newStatus);
      assertThat(modified.getAggregateType()).isEqualTo(original.getAggregateType());
      assertThat(modified.getAggregateId()).isEqualTo(original.getAggregateId());
    }
  }

  // ========== 必填字段校验 ==========

  @Nested
  @DisplayName("必填字段校验")
  class RequiredFieldValidationTests {

    @Test
    @DisplayName("应该抛出异常当 aggregateType 为 null")
    void shouldThrowExceptionWhenAggregateTypeIsNull() {
      // Given
      String aggregateType = null;

      // When & Then
      assertThatThrownBy(
              () ->
                  OutboxMessageTestDataBuilder.aPendingMessage()
                      .aggregateType(aggregateType)
                      .build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("aggregateType must not be null");
    }

    @Test
    @DisplayName("应该抛出异常当 aggregateId 为 null")
    void shouldThrowExceptionWhenAggregateIdIsNull() {
      // Given
      Long aggregateId = null;

      // When & Then
      assertThatThrownBy(
              () -> OutboxMessageTestDataBuilder.aPendingMessage().aggregateId(aggregateId).build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("aggregateId must not be null");
    }

    @Test
    @DisplayName("应该抛出异常当 channel 为 null")
    void shouldThrowExceptionWhenChannelIsNull() {
      // Given
      String channel = null;

      // When & Then
      assertThatThrownBy(
              () -> OutboxMessageTestDataBuilder.aPendingMessage().channel(channel).build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("channel must not be null");
    }

    @Test
    @DisplayName("应该抛出异常当 opType 为 null")
    void shouldThrowExceptionWhenOpTypeIsNull() {
      // Given
      String opType = null;

      // When & Then
      assertThatThrownBy(
              () -> OutboxMessageTestDataBuilder.aPendingMessage().opType(opType).build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("opType must not be null");
    }

    @Test
    @DisplayName("应该抛出异常当 partitionKey 为 null")
    void shouldThrowExceptionWhenPartitionKeyIsNull() {
      // Given
      String partitionKey = null;

      // When & Then
      assertThatThrownBy(
              () ->
                  OutboxMessageTestDataBuilder.aPendingMessage().partitionKey(partitionKey).build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("partitionKey must not be null");
    }

    @Test
    @DisplayName("应该抛出异常当 dedupKey 为 null")
    void shouldThrowExceptionWhenDedupKeyIsNull() {
      // Given
      String dedupKey = null;

      // When & Then
      assertThatThrownBy(
              () -> OutboxMessageTestDataBuilder.aPendingMessage().dedupKey(dedupKey).build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("dedupKey must not be null");
    }
  }

  // ========== 默认值设置 ==========

  @Nested
  @DisplayName("默认值设置")
  class DefaultValueTests {

    @Test
    @DisplayName("应该默认 statusCode 为 PENDING")
    void shouldDefaultStatusCodeToPending() {
      // Given - 不设置 statusCode
      OutboxMessage message =
          OutboxMessage.builder()
              .aggregateType("TestAggregate")
              .aggregateId(1L)
              .channel("test_channel")
              .opType("TEST")
              .partitionKey("test:1")
              .dedupKey("test:1:dedup")
              .statusCode(null) // 显式传 null
              .build();

      // When & Then - 应该默认为 PENDING
      assertThat(message.getStatusCode()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("应该默认 retryCount 为 0")
    void shouldDefaultRetryCountToZero() {
      // Given - 不设置 retryCount
      OutboxMessage message =
          OutboxMessage.builder()
              .aggregateType("TestAggregate")
              .aggregateId(1L)
              .channel("test_channel")
              .opType("TEST")
              .partitionKey("test:1")
              .dedupKey("test:1:dedup")
              .retryCount(null) // 显式传 null
              .build();

      // When & Then - 应该默认为 0
      assertThat(message.getRetryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("应该允许覆盖默认的 statusCode")
    void shouldAllowOverridingDefaultStatusCode() {
      // Given - 显式设置 statusCode
      String customStatus = "PUBLISHING";
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage().statusCode(customStatus).build();

      // When & Then - 应该使用自定义值
      assertThat(message.getStatusCode()).isEqualTo(customStatus);
    }

    @Test
    @DisplayName("应该允许覆盖默认的 retryCount")
    void shouldAllowOverridingDefaultRetryCount() {
      // Given - 显式设置 retryCount
      Integer customRetryCount = 5;
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage().retryCount(customRetryCount).build();

      // When & Then - 应该使用自定义值
      assertThat(message.getRetryCount()).isEqualTo(customRetryCount);
    }
  }

  // ========== 状态机行为方法 ==========

  @Nested
  @DisplayName("状态机行为方法")
  class StatusBehaviorTests {

    @Test
    @DisplayName("isPending() 应该在状态为 PENDING 时返回 true")
    void shouldReturnTrueWhenStatusIsPending() {
      // Given
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage().statusCode("PENDING").build();

      // When & Then
      assertThat(message.isPending()).isTrue();
    }

    @Test
    @DisplayName("isPending() 应该在状态非 PENDING 时返回 false")
    void shouldReturnFalseWhenStatusIsNotPending() {
      // Given
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage().statusCode("PUBLISHED").build();

      // When & Then
      assertThat(message.isPending()).isFalse();
    }

    @Test
    @DisplayName("isPublishing() 应该在状态为 PUBLISHING 时返回 true")
    void shouldReturnTrueWhenStatusIsPublishing() {
      // Given
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPublishingMessage().statusCode("PUBLISHING").build();

      // When & Then
      assertThat(message.isPublishing()).isTrue();
    }

    @Test
    @DisplayName("isPublishing() 应该在状态非 PUBLISHING 时返回 false")
    void shouldReturnFalseWhenStatusIsNotPublishing() {
      // Given
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage().statusCode("PENDING").build();

      // When & Then
      assertThat(message.isPublishing()).isFalse();
    }

    @Test
    @DisplayName("isTerminal() 应该在状态为 PUBLISHED 时返回 true")
    void shouldReturnTrueWhenStatusIsPublished() {
      // Given
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage().statusCode("PUBLISHED").build();

      // When & Then
      assertThat(message.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("isTerminal() 应该在状态为 FAILED 时返回 true")
    void shouldReturnTrueWhenStatusIsFailed() {
      // Given
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aFailedMessage().statusCode("FAILED").build();

      // When & Then
      assertThat(message.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("isTerminal() 应该在状态为非终态时返回 false")
    void shouldReturnFalseWhenStatusIsNotTerminal() {
      // Given
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage().statusCode("PENDING").build();

      // When & Then
      assertThat(message.isTerminal()).isFalse();
    }
  }

  // ========== 租约机制测试 ==========

  @Nested
  @DisplayName("租约机制")
  class LeaseMechanismTests {

    @Test
    @DisplayName("hasActiveLease() 应该在持有有效租约时返回 true")
    void shouldReturnTrueWhenHoldingActiveLease() {
      // Given - 持有有效租约的消息
      Instant now = Instant.parse("2025-01-01T10:00:00Z");
      Instant leaseExpireAt = Instant.parse("2025-01-01T10:05:00Z");
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPublishingMessage()
              .leaseOwner("relay-1")
              .leaseExpireAt(leaseExpireAt)
              .build();

      // When & Then - 在租约到期前
      assertThat(message.hasActiveLease(now)).isTrue();
    }

    @Test
    @DisplayName("hasActiveLease() 应该在租约已过期时返回 false")
    void shouldReturnFalseWhenLeaseIsExpired() {
      // Given - 租约已过期
      Instant now = Instant.parse("2025-01-01T10:10:00Z");
      Instant leaseExpireAt = Instant.parse("2025-01-01T10:05:00Z");
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPublishingMessage()
              .leaseOwner("relay-1")
              .leaseExpireAt(leaseExpireAt)
              .build();

      // When & Then - 租约已过期
      assertThat(message.hasActiveLease(now)).isFalse();
    }

    @Test
    @DisplayName("hasActiveLease() 应该在无租约持有者时返回 false")
    void shouldReturnFalseWhenNoLeaseOwner() {
      // Given - 无租约持有者
      Instant now = Instant.parse("2025-01-01T10:00:00Z");
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage()
              .leaseOwner(null)
              .leaseExpireAt(null)
              .build();

      // When & Then
      assertThat(message.hasActiveLease(now)).isFalse();
    }

    @Test
    @DisplayName("hasActiveLease() 应该在租约到期时间为 null 时返回 false")
    void shouldReturnFalseWhenLeaseExpireAtIsNull() {
      // Given - 有持有者但无到期时间
      Instant now = Instant.parse("2025-01-01T10:00:00Z");
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPublishingMessage()
              .leaseOwner("relay-1")
              .leaseExpireAt(null)
              .build();

      // When & Then
      assertThat(message.hasActiveLease(now)).isFalse();
    }

    @Test
    @DisplayName("isLeaseExpired() 应该在租约已过期时返回 true")
    void shouldReturnTrueWhenLeaseIsExpired() {
      // Given
      Instant now = Instant.parse("2025-01-01T10:10:00Z");
      Instant leaseExpireAt = Instant.parse("2025-01-01T10:05:00Z");
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPublishingMessage()
              .leaseOwner("relay-1")
              .leaseExpireAt(leaseExpireAt)
              .build();

      // When & Then
      assertThat(message.isLeaseExpired(now)).isTrue();
    }

    @Test
    @DisplayName("isLeaseExpired() 应该在租约未过期时返回 false")
    void shouldReturnFalseWhenLeaseIsNotExpired() {
      // Given
      Instant now = Instant.parse("2025-01-01T10:00:00Z");
      Instant leaseExpireAt = Instant.parse("2025-01-01T10:05:00Z");
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPublishingMessage()
              .leaseOwner("relay-1")
              .leaseExpireAt(leaseExpireAt)
              .build();

      // When & Then
      assertThat(message.isLeaseExpired(now)).isFalse();
    }

    @Test
    @DisplayName("isLeaseExpired() 应该在租约到期时间为 null 时返回 true")
    void shouldReturnTrueWhenLeaseExpireAtIsNull() {
      // Given
      Instant now = Instant.parse("2025-01-01T10:00:00Z");
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage()
              .leaseOwner(null)
              .leaseExpireAt(null)
              .build();

      // When & Then - 无租约视为已过期
      assertThat(message.isLeaseExpired(now)).isTrue();
    }
  }

  // ========== 重试机制测试 ==========

  @Nested
  @DisplayName("重试机制")
  class RetryMechanismTests {

    @Test
    @DisplayName("computeNextAttempt() 应该在首次尝试时返回 1")
    void shouldReturnOneForFirstAttempt() {
      // Given - retryCount = 0
      OutboxMessage message = OutboxMessageTestDataBuilder.aPendingMessage().retryCount(0).build();

      // When
      int nextAttempt = message.computeNextAttempt();

      // Then
      assertThat(nextAttempt).isEqualTo(1);
    }

    @Test
    @DisplayName("computeNextAttempt() 应该在重试后递增")
    void shouldIncrementAfterRetry() {
      // Given - retryCount = 2
      OutboxMessage message = OutboxMessageTestDataBuilder.aPendingMessage().retryCount(2).build();

      // When
      int nextAttempt = message.computeNextAttempt();

      // Then
      assertThat(nextAttempt).isEqualTo(3);
    }

    @Test
    @DisplayName("computeNextAttempt() 应该处理 null retryCount")
    void shouldHandleNullRetryCount() {
      // Given - retryCount = null（虽然构造器会设为 0）
      OutboxMessage message =
          OutboxMessage.builder()
              .aggregateType("TestAggregate")
              .aggregateId(1L)
              .channel("test_channel")
              .opType("TEST")
              .partitionKey("test:1")
              .dedupKey("test:1:dedup")
              .retryCount(null)
              .build();

      // When
      int nextAttempt = message.computeNextAttempt();

      // Then - 默认为 0，所以下一次是 1
      assertThat(nextAttempt).isEqualTo(1);
    }

    @Test
    @DisplayName("canRetry() 应该在未达到最大尝试次数时返回 true")
    void shouldReturnTrueWhenNotExceedingMaxAttempts() {
      // Given - retryCount = 2, maxAttempts = 5
      OutboxMessage message = OutboxMessageTestDataBuilder.aPendingMessage().retryCount(2).build();
      int maxAttempts = 5;

      // When & Then - 下一次是第 3 次，未超过 5
      assertThat(message.canRetry(maxAttempts)).isTrue();
    }

    @Test
    @DisplayName("canRetry() 应该在达到最大尝试次数时返回 false")
    void shouldReturnFalseWhenExceedingMaxAttempts() {
      // Given - retryCount = 5, maxAttempts = 5
      OutboxMessage message = OutboxMessageTestDataBuilder.aPendingMessage().retryCount(5).build();
      int maxAttempts = 5;

      // When & Then - 下一次是第 6 次，超过 5
      assertThat(message.canRetry(maxAttempts)).isFalse();
    }

    @Test
    @DisplayName("canRetry() 应该在恰好等于最大尝试次数时返回 false")
    void shouldReturnFalseWhenEqualsMaxAttempts() {
      // Given - retryCount = 4, maxAttempts = 5
      OutboxMessage message = OutboxMessageTestDataBuilder.aPendingMessage().retryCount(4).build();
      int maxAttempts = 5;

      // When & Then - 下一次是第 5 次，等于 5，应该允许
      assertThat(message.canRetry(maxAttempts)).isTrue();

      // 再次重试后应该不行
      OutboxMessage retriedMessage =
          OutboxMessageTestDataBuilder.aPendingMessage().retryCount(5).build();
      assertThat(retriedMessage.canRetry(maxAttempts)).isFalse();
    }
  }

  // ========== 延迟发布逻辑测试 ==========

  @Nested
  @DisplayName("延迟发布逻辑")
  class DelayedPublishTests {

    @Test
    @DisplayName("isReadyToRelay() 应该在状态为 PENDING 且时间满足时返回 true")
    void shouldReturnTrueWhenStatusIsPendingAndTimeIsMet() {
      // Given - PENDING 状态，无时间限制
      Instant now = Instant.parse("2025-01-01T10:00:00Z");
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage()
              .statusCode("PENDING")
              .notBefore(null)
              .nextRetryAt(null)
              .build();

      // When & Then
      assertThat(message.isReadyToRelay(now)).isTrue();
    }

    @Test
    @DisplayName("isReadyToRelay() 应该在状态非 PENDING 时返回 false")
    void shouldReturnFalseWhenStatusIsNotPending() {
      // Given - PUBLISHING 状态
      Instant now = Instant.parse("2025-01-01T10:00:00Z");
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPublishingMessage().statusCode("PUBLISHING").build();

      // When & Then
      assertThat(message.isReadyToRelay(now)).isFalse();
    }

    @Test
    @DisplayName("isReadyToRelay() 应该在 notBefore 未到时返回 false")
    void shouldReturnFalseWhenNotBeforeIsNotMet() {
      // Given - notBefore 在未来
      Instant now = Instant.parse("2025-01-01T10:00:00Z");
      Instant notBefore = Instant.parse("2025-01-01T10:05:00Z");
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage()
              .statusCode("PENDING")
              .notBefore(notBefore)
              .nextRetryAt(null)
              .build();

      // When & Then
      assertThat(message.isReadyToRelay(now)).isFalse();
    }

    @Test
    @DisplayName("isReadyToRelay() 应该在 notBefore 已到时返回 true")
    void shouldReturnTrueWhenNotBeforeIsMet() {
      // Given - notBefore 在过去
      Instant now = Instant.parse("2025-01-01T10:05:00Z");
      Instant notBefore = Instant.parse("2025-01-01T10:00:00Z");
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage()
              .statusCode("PENDING")
              .notBefore(notBefore)
              .nextRetryAt(null)
              .build();

      // When & Then
      assertThat(message.isReadyToRelay(now)).isTrue();
    }

    @Test
    @DisplayName("isReadyToRelay() 应该在 nextRetryAt 未到时返回 false")
    void shouldReturnFalseWhenNextRetryAtIsNotMet() {
      // Given - nextRetryAt 在未来
      Instant now = Instant.parse("2025-01-01T10:00:00Z");
      Instant nextRetryAt = Instant.parse("2025-01-01T10:10:00Z");
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage()
              .statusCode("PENDING")
              .notBefore(null)
              .nextRetryAt(nextRetryAt)
              .build();

      // When & Then
      assertThat(message.isReadyToRelay(now)).isFalse();
    }

    @Test
    @DisplayName("isReadyToRelay() 应该在 nextRetryAt 已到时返回 true")
    void shouldReturnTrueWhenNextRetryAtIsMet() {
      // Given - nextRetryAt 在过去
      Instant now = Instant.parse("2025-01-01T10:10:00Z");
      Instant nextRetryAt = Instant.parse("2025-01-01T10:00:00Z");
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage()
              .statusCode("PENDING")
              .notBefore(null)
              .nextRetryAt(nextRetryAt)
              .build();

      // When & Then
      assertThat(message.isReadyToRelay(now)).isTrue();
    }

    @Test
    @DisplayName("isReadyToRelay() 应该同时检查 notBefore 和 nextRetryAt")
    void shouldCheckBothNotBeforeAndNextRetryAt() {
      // Given - notBefore 已满足，但 nextRetryAt 未满足
      Instant now = Instant.parse("2025-01-01T10:05:00Z");
      Instant notBefore = Instant.parse("2025-01-01T10:00:00Z");
      Instant nextRetryAt = Instant.parse("2025-01-01T10:10:00Z");
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage()
              .statusCode("PENDING")
              .notBefore(notBefore)
              .nextRetryAt(nextRetryAt)
              .build();

      // When & Then - 应该返回 false（两个条件都要满足）
      assertThat(message.isReadyToRelay(now)).isFalse();

      // When - 时间推进到 nextRetryAt 之后
      Instant laterNow = Instant.parse("2025-01-01T10:15:00Z");

      // Then - 应该返回 true
      assertThat(message.isReadyToRelay(laterNow)).isTrue();
    }
  }

  // ========== refreshForRetry 方法测试 ==========

  @Nested
  @DisplayName("refreshForRetry 方法")
  class RefreshForRetryTests {

    @Test
    @DisplayName("refreshForRetry() 应该重置状态为 PENDING")
    void shouldResetStatusToPending() {
      // Given - 失败的消息
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aFailedMessage()
              .statusCode("FAILED")
              .retryCount(3)
              .errorCode("ERR_TIMEOUT")
              .errorMsg("Connection timeout")
              .build();

      // When
      String newPayloadJson = "{\"updated\":true}";
      String newHeadersJson = "{\"retry\":true}";
      OutboxMessage refreshed = message.refreshForRetry(newPayloadJson, newHeadersJson);

      // Then - 状态应该重置为 PENDING
      assertThat(refreshed.getStatusCode()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("refreshForRetry() 应该重置 retryCount 为 0")
    void shouldResetRetryCountToZero() {
      // Given
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aFailedMessage()
              .retryCount(5)
              .nextRetryAt(Instant.parse("2025-01-01T10:00:00Z"))
              .build();

      // When
      OutboxMessage refreshed = message.refreshForRetry("{}", "{}");

      // Then
      assertThat(refreshed.getRetryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("refreshForRetry() 应该清除 nextRetryAt")
    void shouldClearNextRetryAt() {
      // Given
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aFailedMessage()
              .nextRetryAt(Instant.parse("2025-01-01T10:00:00Z"))
              .build();

      // When
      OutboxMessage refreshed = message.refreshForRetry("{}", "{}");

      // Then
      assertThat(refreshed.getNextRetryAt()).isNull();
    }

    @Test
    @DisplayName("refreshForRetry() 应该清除错误信息")
    void shouldClearErrorInfo() {
      // Given
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aFailedMessage()
              .errorCode("ERR_NETWORK")
              .errorMsg("Network unreachable")
              .build();

      // When
      OutboxMessage refreshed = message.refreshForRetry("{}", "{}");

      // Then
      assertThat(refreshed.getErrorCode()).isNull();
      assertThat(refreshed.getErrorMsg()).isNull();
    }

    @Test
    @DisplayName("refreshForRetry() 应该更新 payloadJson 和 headersJson")
    void shouldUpdatePayloadAndHeaders() {
      // Given
      OutboxMessage message = OutboxMessageTestDataBuilder.aFailedMessage().build();
      String newPayloadJson = "{\"updated\":true,\"version\":2}";
      String newHeadersJson = "{\"retry\":true,\"attempt\":2}";

      // When
      OutboxMessage refreshed = message.refreshForRetry(newPayloadJson, newHeadersJson);

      // Then
      assertThat(refreshed.getPayloadJson()).isEqualTo(newPayloadJson);
      assertThat(refreshed.getHeadersJson()).isEqualTo(newHeadersJson);
    }

    @Test
    @DisplayName("refreshForRetry() 应该保留不可变标识字段")
    void shouldPreserveImmutableIdentityFields() {
      // Given
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aFailedMessage()
              .aggregateType("PlanAggregate")
              .aggregateId(2001L)
              .channel("plan_events")
              .opType("CREATED")
              .partitionKey("plan:2001")
              .dedupKey("plan:2001:dedup")
              .build();

      // When
      OutboxMessage refreshed = message.refreshForRetry("{\"new\":true}", "{\"new\":true}");

      // Then - 标识字段不应该改变
      assertThat(refreshed.getAggregateType()).isEqualTo("PlanAggregate");
      assertThat(refreshed.getAggregateId()).isEqualTo(2001L);
      assertThat(refreshed.getChannel()).isEqualTo("plan_events");
      assertThat(refreshed.getOpType()).isEqualTo("CREATED");
      assertThat(refreshed.getPartitionKey()).isEqualTo("plan:2001");
      assertThat(refreshed.getDedupKey()).isEqualTo("plan:2001:dedup");
    }
  }

  // ========== 不变性测试 ==========

  @Nested
  @DisplayName("不变性测试")
  class ImmutabilityTests {

    @Test
    @DisplayName("核心标识字段应该在生命周期中保持不变")
    void shouldPreserveCoreIdentityFieldsThroughLifecycle() {
      // Given - 原始消息
      String aggregateType = "TaskAggregate";
      Long aggregateId = 3001L;
      String channel = "task_events";
      String opType = "COMPLETED";
      String partitionKey = "task:3001";
      String dedupKey = "task:3001:completed:1640000000000";

      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage()
              .aggregateType(aggregateType)
              .aggregateId(aggregateId)
              .channel(channel)
              .opType(opType)
              .partitionKey(partitionKey)
              .dedupKey(dedupKey)
              .build();

      // When - 创建各种变体（模拟状态转换）
      OutboxMessage publishing = message.toBuilder().statusCode("PUBLISHING").build();
      OutboxMessage failed =
          message.toBuilder().statusCode("FAILED").retryCount(3).errorCode("ERR_TEST").build();
      OutboxMessage refreshed = message.refreshForRetry("{}", "{}");

      // Then - 核心标识字段应该在所有变体中保持不变
      for (OutboxMessage variant : new OutboxMessage[] {message, publishing, failed, refreshed}) {
        assertThat(variant.getAggregateType()).isEqualTo(aggregateType);
        assertThat(variant.getAggregateId()).isEqualTo(aggregateId);
        assertThat(variant.getChannel()).isEqualTo(channel);
        assertThat(variant.getOpType()).isEqualTo(opType);
        assertThat(variant.getPartitionKey()).isEqualTo(partitionKey);
        assertThat(variant.getDedupKey()).isEqualTo(dedupKey);
      }
    }

    @Test
    @DisplayName("ID 和 version 字段应该不可变")
    void shouldPreserveIdAndVersion() {
      // Given
      Long id = 1001L;
      Long version = 5L;
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage().id(id).version(version).build();

      // When - 使用 toBuilder 创建新实例
      OutboxMessage modified = message.toBuilder().statusCode("PUBLISHED").build();

      // Then - ID 和 version 应该保持不变
      assertThat(modified.getId()).isEqualTo(id);
      assertThat(modified.getVersion()).isEqualTo(version);
    }
  }

  // ========== 边界条件测试 ==========

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理极端时间戳边界 - Unix Epoch")
    void shouldHandleUnixEpochTimestamp() {
      // Given - Unix Epoch
      Instant epoch = Instant.parse("1970-01-01T00:00:00Z");
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage()
              .notBefore(epoch)
              .nextRetryAt(epoch)
              .leaseExpireAt(epoch)
              .build();

      // When & Then
      assertThat(message.getNotBefore()).isEqualTo(epoch);
      assertThat(message.getNextRetryAt()).isEqualTo(epoch);
      assertThat(message.getLeaseExpireAt()).isEqualTo(epoch);
    }

    @Test
    @DisplayName("应该处理极端时间戳边界 - 远期未来")
    void shouldHandleFarFutureTimestamp() {
      // Given - 远期未来
      Instant farFuture = Instant.parse("2099-12-31T23:59:59Z");
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage()
              .notBefore(farFuture)
              .nextRetryAt(farFuture)
              .leaseExpireAt(farFuture)
              .build();

      // When & Then
      assertThat(message.getNotBefore()).isEqualTo(farFuture);
      assertThat(message.getNextRetryAt()).isEqualTo(farFuture);
      assertThat(message.getLeaseExpireAt()).isEqualTo(farFuture);
    }

    @Test
    @DisplayName("应该处理极大消息负载")
    void shouldHandleVeryLargePayload() {
      // Given - 10KB JSON 负载
      String largePayload = "{\"data\":\"" + "x".repeat(10000) + "\"}";
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage().payloadJson(largePayload).build();

      // When & Then
      assertThat(message.getPayloadJson()).hasSize(largePayload.length());
      assertThat(message.getPayloadJson()).contains("x".repeat(10000));
    }

    @Test
    @DisplayName("应该处理空字符串字段")
    void shouldHandleEmptyStringFields() {
      // Given - 空字符串
      String emptyPayload = "";
      String emptyHeaders = "";
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage()
              .payloadJson(emptyPayload)
              .headersJson(emptyHeaders)
              .build();

      // When & Then
      assertThat(message.getPayloadJson()).isEmpty();
      assertThat(message.getHeadersJson()).isEmpty();
    }

    @Test
    @DisplayName("应该处理极大重试次数")
    void shouldHandleVeryLargeRetryCount() {
      // Given - 极大重试次数
      Integer largeRetryCount = Integer.MAX_VALUE;
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage().retryCount(largeRetryCount).build();

      // When & Then
      assertThat(message.getRetryCount()).isEqualTo(largeRetryCount);
    }

    @Test
    @DisplayName("应该处理极长的字符串字段")
    void shouldHandleVeryLongStringFields() {
      // Given - 1000 字符的字段
      String longAggregateType = "A".repeat(1000);
      String longChannel = "C".repeat(1000);
      String longOpType = "O".repeat(1000);
      String longPartitionKey = "P".repeat(1000);
      String longDedupKey = "D".repeat(1000);

      // When
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage()
              .aggregateType(longAggregateType)
              .channel(longChannel)
              .opType(longOpType)
              .partitionKey(longPartitionKey)
              .dedupKey(longDedupKey)
              .build();

      // Then
      assertThat(message.getAggregateType()).hasSize(1000);
      assertThat(message.getChannel()).hasSize(1000);
      assertThat(message.getOpType()).hasSize(1000);
      assertThat(message.getPartitionKey()).hasSize(1000);
      assertThat(message.getDedupKey()).hasSize(1000);
    }

    @Test
    @DisplayName("应该处理特殊字符")
    void shouldHandleSpecialCharacters() {
      // Given - 包含特殊字符的字段
      String specialPayload = "{\"emoji\":\"😀\",\"unicode\":\"\\u4e2d\\u6587\"}";
      String specialHeaders = "{\"key\":\"value with spaces & symbols: !@#$%\"}";

      // When
      OutboxMessage message =
          OutboxMessageTestDataBuilder.aPendingMessage()
              .payloadJson(specialPayload)
              .headersJson(specialHeaders)
              .build();

      // Then
      assertThat(message.getPayloadJson()).isEqualTo(specialPayload);
      assertThat(message.getHeadersJson()).isEqualTo(specialHeaders);
    }
  }

  // ========== TestDataBuilder (辅助类) ==========

  /// OutboxMessage 测试数据构建器。
/// 
/// 遵循 Builder 模式，提供默认值以简化测试数据构建。
  static class OutboxMessageTestDataBuilder {
    private Long id = null;
    private Long version = 0L;
    private String aggregateType = "TestAggregate";
    private Long aggregateId = 1001L;
    private String channel = "test_channel";
    private String opType = "CREATED";
    private String partitionKey = "test:1001";
    private String dedupKey = "test:1001:created:1640000000000";
    private String payloadJson = "{\"test\":true}";
    private String headersJson = "{\"source\":\"test\"}";
    private Instant notBefore = null;
    private String statusCode = "PENDING";
    private Integer retryCount = 0;
    private Instant nextRetryAt = null;
    private String errorCode = null;
    private String errorMsg = null;
    private String leaseOwner = null;
    private Instant leaseExpireAt = null;

    /// 创建一个默认的 PENDING 状态消息构建器。
    public static OutboxMessageTestDataBuilder aPendingMessage() {
      return new OutboxMessageTestDataBuilder().statusCode("PENDING");
    }

    /// 创建一个 PUBLISHING 状态消息构建器。
    public static OutboxMessageTestDataBuilder aPublishingMessage() {
      return new OutboxMessageTestDataBuilder()
          .statusCode("PUBLISHING")
          .leaseOwner("relay-1")
          .leaseExpireAt(Instant.now().plusSeconds(300));
    }

    /// 创建一个 FAILED 状态消息构建器。
    public static OutboxMessageTestDataBuilder aFailedMessage() {
      return new OutboxMessageTestDataBuilder()
          .statusCode("FAILED")
          .retryCount(3)
          .errorCode("ERR_UNKNOWN")
          .errorMsg("Unknown error occurred");
    }

    public OutboxMessageTestDataBuilder id(Long id) {
      this.id = id;
      return this;
    }

    public OutboxMessageTestDataBuilder version(Long version) {
      this.version = version;
      return this;
    }

    public OutboxMessageTestDataBuilder aggregateType(String aggregateType) {
      this.aggregateType = aggregateType;
      return this;
    }

    public OutboxMessageTestDataBuilder aggregateId(Long aggregateId) {
      this.aggregateId = aggregateId;
      return this;
    }

    public OutboxMessageTestDataBuilder channel(String channel) {
      this.channel = channel;
      return this;
    }

    public OutboxMessageTestDataBuilder opType(String opType) {
      this.opType = opType;
      return this;
    }

    public OutboxMessageTestDataBuilder partitionKey(String partitionKey) {
      this.partitionKey = partitionKey;
      return this;
    }

    public OutboxMessageTestDataBuilder dedupKey(String dedupKey) {
      this.dedupKey = dedupKey;
      return this;
    }

    public OutboxMessageTestDataBuilder payloadJson(String payloadJson) {
      this.payloadJson = payloadJson;
      return this;
    }

    public OutboxMessageTestDataBuilder headersJson(String headersJson) {
      this.headersJson = headersJson;
      return this;
    }

    public OutboxMessageTestDataBuilder notBefore(Instant notBefore) {
      this.notBefore = notBefore;
      return this;
    }

    public OutboxMessageTestDataBuilder statusCode(String statusCode) {
      this.statusCode = statusCode;
      return this;
    }

    public OutboxMessageTestDataBuilder retryCount(Integer retryCount) {
      this.retryCount = retryCount;
      return this;
    }

    public OutboxMessageTestDataBuilder nextRetryAt(Instant nextRetryAt) {
      this.nextRetryAt = nextRetryAt;
      return this;
    }

    public OutboxMessageTestDataBuilder errorCode(String errorCode) {
      this.errorCode = errorCode;
      return this;
    }

    public OutboxMessageTestDataBuilder errorMsg(String errorMsg) {
      this.errorMsg = errorMsg;
      return this;
    }

    public OutboxMessageTestDataBuilder leaseOwner(String leaseOwner) {
      this.leaseOwner = leaseOwner;
      return this;
    }

    public OutboxMessageTestDataBuilder leaseExpireAt(Instant leaseExpireAt) {
      this.leaseExpireAt = leaseExpireAt;
      return this;
    }

    /// 构建 OutboxMessage 实例。
    public OutboxMessage build() {
      return OutboxMessage.builder()
          .id(id)
          .version(version)
          .aggregateType(aggregateType)
          .aggregateId(aggregateId)
          .channel(channel)
          .opType(opType)
          .partitionKey(partitionKey)
          .dedupKey(dedupKey)
          .payloadJson(payloadJson)
          .headersJson(headersJson)
          .notBefore(notBefore)
          .statusCode(statusCode)
          .retryCount(retryCount)
          .nextRetryAt(nextRetryAt)
          .errorCode(errorCode)
          .errorMsg(errorMsg)
          .leaseOwner(leaseOwner)
          .leaseExpireAt(leaseExpireAt)
          .build();
    }
  }
}
