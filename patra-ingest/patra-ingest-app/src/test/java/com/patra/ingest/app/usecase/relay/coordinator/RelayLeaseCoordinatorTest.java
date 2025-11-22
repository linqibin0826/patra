package com.patra.ingest.app.usecase.relay.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.vo.relay.RelayPlan;
import com.patra.ingest.domain.port.OutboxRelayRepository;
import java.time.Duration;
import java.time.Instant;
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

/// RelayLeaseCoordinator 单元测试
///
/// 测试覆盖:
///
/// - ✅ 租约获取成功场景
///   - ✅ 租约获取失败场景 (并发竞争)
///   - ✅ 租约参数传递验证
///   - ✅ 乐观锁版本验证
///   - ✅ 租约过期时间计算
///
/// @author Patra Team
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("RelayLeaseCoordinator 单元测试")
class RelayLeaseCoordinatorTest {

  @Mock private OutboxRelayRepository relayStore;

  @InjectMocks private RelayLeaseCoordinator coordinator;

  @Captor private ArgumentCaptor<Long> messageIdCaptor;

  @Captor private ArgumentCaptor<Long> versionCaptor;

  @Captor private ArgumentCaptor<String> leaseOwnerCaptor;

  @Captor private ArgumentCaptor<Instant> leaseExpireAtCaptor;

  private OutboxMessage testMessage;
  private RelayPlan testPlan;

  @BeforeEach
  void setUp() {
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
            .statusCode("PENDING")
            .build();

    Instant now = Instant.now();
    testPlan =
        new RelayPlan(
            null,
            now, // triggeredAt
            100,
            Duration.ofMinutes(5), // leaseDuration
            3,
            Duration.ofSeconds(1),
            2.0,
            Duration.ofMinutes(10),
            "test-owner-001" // leaseOwner
            );
  }

  @Nested
  @DisplayName("租约获取成功场景")
  class LeaseAcquisitionSuccessTests {

    @Test
    @DisplayName("租约获取成功应返回 true")
    void shouldReturnTrueWhenLeaseAcquired() {
      // Given: 租约获取成功
      when(relayStore.acquireLease(anyLong(), anyLong(), anyString(), any(Instant.class)))
          .thenReturn(true);

      // When
      boolean acquired = coordinator.tryAcquire(testMessage, testPlan);

      // Then
      assertThat(acquired).isTrue();
    }

    @Test
    @DisplayName("应正确传递租约参数到存储层")
    void shouldPassCorrectLeaseParametersToStore() {
      // Given
      when(relayStore.acquireLease(anyLong(), anyLong(), anyString(), any(Instant.class)))
          .thenReturn(true);

      // When
      coordinator.tryAcquire(testMessage, testPlan);

      // Then: 验证参数
      verify(relayStore)
          .acquireLease(
              messageIdCaptor.capture(),
              versionCaptor.capture(),
              leaseOwnerCaptor.capture(),
              leaseExpireAtCaptor.capture());

      assertThat(messageIdCaptor.getValue()).isEqualTo(1L);
      assertThat(versionCaptor.getValue()).isEqualTo(1L);
      assertThat(leaseOwnerCaptor.getValue()).isEqualTo("test-owner-001");
      assertThat(leaseExpireAtCaptor.getValue()).isEqualTo(testPlan.leaseExpireAt());
    }

    @Test
    @DisplayName("租约过期时间应等于 triggeredAt + leaseDuration")
    void shouldComputeLeaseExpireAtCorrectly() {
      // Given
      Instant triggeredAt = Instant.parse("2025-01-01T12:00:00Z");
      Duration leaseDuration = Duration.ofMinutes(5);
      RelayPlan plan =
          new RelayPlan(
              null,
              triggeredAt,
              100,
              leaseDuration,
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "owner-001");

      when(relayStore.acquireLease(anyLong(), anyLong(), anyString(), any(Instant.class)))
          .thenReturn(true);

      // When
      coordinator.tryAcquire(testMessage, plan);

      // Then: 验证租约过期时间 = triggeredAt + 5 分钟
      verify(relayStore)
          .acquireLease(anyLong(), anyLong(), anyString(), leaseExpireAtCaptor.capture());

      Instant expectedExpireAt = triggeredAt.plus(leaseDuration);
      assertThat(leaseExpireAtCaptor.getValue()).isEqualTo(expectedExpireAt);
    }

    @Test
    @DisplayName("不同的租约持有者标识应正确传递")
    void shouldHandleDifferentLeaseOwners() {
      // Given: 不同的租约持有者
      RelayPlan planWithDifferentOwner =
          new RelayPlan(
              null,
              Instant.now(),
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "instance-002");

      when(relayStore.acquireLease(anyLong(), anyLong(), anyString(), any(Instant.class)))
          .thenReturn(true);

      // When
      coordinator.tryAcquire(testMessage, planWithDifferentOwner);

      // Then
      verify(relayStore)
          .acquireLease(anyLong(), anyLong(), leaseOwnerCaptor.capture(), any(Instant.class));

      assertThat(leaseOwnerCaptor.getValue()).isEqualTo("instance-002");
    }
  }

  @Nested
  @DisplayName("租约获取失败场景")
  class LeaseAcquisitionFailureTests {

    @Test
    @DisplayName("租约获取失败应返回 false (并发竞争)")
    void shouldReturnFalseWhenLeaseLost() {
      // Given: 租约被其他实例抢占
      when(relayStore.acquireLease(anyLong(), anyLong(), anyString(), any(Instant.class)))
          .thenReturn(false);

      // When
      boolean acquired = coordinator.tryAcquire(testMessage, testPlan);

      // Then
      assertThat(acquired).isFalse();
    }

    @Test
    @DisplayName("租约失败仍应调用存储层尝试获取")
    void shouldAttemptAcquisitionEvenIfLost() {
      // Given
      when(relayStore.acquireLease(anyLong(), anyLong(), anyString(), any(Instant.class)))
          .thenReturn(false);

      // When
      coordinator.tryAcquire(testMessage, testPlan);

      // Then: 验证调用了一次
      verify(relayStore, times(1))
          .acquireLease(anyLong(), anyLong(), anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("租约失败后不应重试")
    void shouldNotRetryOnLeaseFailure() {
      // Given
      when(relayStore.acquireLease(anyLong(), anyLong(), anyString(), any(Instant.class)))
          .thenReturn(false);

      // When
      coordinator.tryAcquire(testMessage, testPlan);

      // Then: 只调用一次,不重试
      verify(relayStore, times(1))
          .acquireLease(anyLong(), anyLong(), anyString(), any(Instant.class));
    }
  }

  @Nested
  @DisplayName("乐观锁版本验证场景")
  class OptimisticLockVersionTests {

    @Test
    @DisplayName("应使用消息当前版本进行乐观锁控制")
    void shouldUseCurrentVersionForOptimisticLock() {
      // Given: 消息版本为 5
      OutboxMessage versionedMessage = testMessage.toBuilder().version(5L).build();
      when(relayStore.acquireLease(anyLong(), anyLong(), anyString(), any(Instant.class)))
          .thenReturn(true);

      // When
      coordinator.tryAcquire(versionedMessage, testPlan);

      // Then: 验证使用版本 5
      verify(relayStore)
          .acquireLease(anyLong(), versionCaptor.capture(), anyString(), any(Instant.class));

      assertThat(versionCaptor.getValue()).isEqualTo(5L);
    }

    @Test
    @DisplayName("版本为 null 应正确处理")
    void shouldHandleNullVersion() {
      // Given: 版本为 null (新消息)
      OutboxMessage nullVersionMessage = testMessage.toBuilder().version(null).build();
      when(relayStore.acquireLease(anyLong(), any(), anyString(), any(Instant.class)))
          .thenReturn(true);

      // When
      coordinator.tryAcquire(nullVersionMessage, testPlan);

      // Then: 应传递 null 版本
      verify(relayStore).acquireLease(anyLong(), eq(null), anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("版本冲突导致租约失败应返回 false")
    void shouldReturnFalseOnVersionConflict() {
      // Given: 版本冲突 (乐观锁失败)
      when(relayStore.acquireLease(anyLong(), anyLong(), anyString(), any(Instant.class)))
          .thenReturn(false);

      // When
      boolean acquired = coordinator.tryAcquire(testMessage, testPlan);

      // Then
      assertThat(acquired).isFalse();
    }
  }

  @Nested
  @DisplayName("租约过期时间计算场景")
  class LeaseExpireAtComputationTests {

    @Test
    @DisplayName("静态方法 computeLeaseExpireAt 应正确计算过期时间")
    void shouldComputeLeaseExpireAtUsingStaticMethod() {
      // Given
      Instant triggeredAt = Instant.parse("2025-01-01T10:00:00Z");
      Duration leaseDuration = Duration.ofMinutes(3);
      RelayPlan plan =
          new RelayPlan(
              null,
              triggeredAt,
              100,
              leaseDuration,
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "owner");

      // When
      Instant expireAt = RelayLeaseCoordinator.computeLeaseExpireAt(plan);

      // Then
      Instant expected = Instant.parse("2025-01-01T10:03:00Z"); // +3 分钟
      assertThat(expireAt).isEqualTo(expected);
    }

    @Test
    @DisplayName("不同的租约持续时间应产生不同的过期时间")
    void shouldProduceDifferentExpireTimesForDifferentDurations() {
      // Given
      Instant triggeredAt = Instant.parse("2025-01-01T12:00:00Z");

      RelayPlan shortLease =
          new RelayPlan(
              null,
              triggeredAt,
              100,
              Duration.ofMinutes(1), // 1 分钟
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "owner");

      RelayPlan longLease =
          new RelayPlan(
              null,
              triggeredAt,
              100,
              Duration.ofMinutes(10), // 10 分钟
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "owner");

      // When
      Instant shortExpire = RelayLeaseCoordinator.computeLeaseExpireAt(shortLease);
      Instant longExpire = RelayLeaseCoordinator.computeLeaseExpireAt(longLease);

      // Then
      assertThat(shortExpire).isEqualTo(Instant.parse("2025-01-01T12:01:00Z"));
      assertThat(longExpire).isEqualTo(Instant.parse("2025-01-01T12:10:00Z"));
      assertThat(longExpire).isAfter(shortExpire);
    }

    @Test
    @DisplayName("租约持续时间为零应返回触发时间")
    void shouldReturnTriggeredAtWhenDurationIsZero() {
      // Given
      Instant triggeredAt = Instant.parse("2025-01-01T12:00:00Z");
      RelayPlan zeroLease =
          new RelayPlan(
              null,
              triggeredAt,
              100,
              Duration.ZERO, // 零持续时间
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "owner");

      // When
      Instant expireAt = RelayLeaseCoordinator.computeLeaseExpireAt(zeroLease);

      // Then
      assertThat(expireAt).isEqualTo(triggeredAt);
    }
  }

  @Nested
  @DisplayName("边界条件场景")
  class BoundaryConditionTests {

    @Test
    @DisplayName("消息 ID 为 Long.MAX_VALUE 应正确处理")
    void shouldHandleMaxLongMessageId() {
      // Given
      OutboxMessage maxIdMessage = testMessage.toBuilder().id(Long.MAX_VALUE).build();
      when(relayStore.acquireLease(anyLong(), anyLong(), anyString(), any(Instant.class)))
          .thenReturn(true);

      // When
      boolean acquired = coordinator.tryAcquire(maxIdMessage, testPlan);

      // Then
      assertThat(acquired).isTrue();
      verify(relayStore)
          .acquireLease(eq(Long.MAX_VALUE), anyLong(), anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("租约持有者名称很长应正确传递")
    void shouldHandleLongLeaseOwnerName() {
      // Given: 很长的租约持有者名称
      String longOwner = "instance-" + "x".repeat(100);
      RelayPlan planWithLongOwner =
          new RelayPlan(
              null,
              Instant.now(),
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              longOwner);

      when(relayStore.acquireLease(anyLong(), anyLong(), anyString(), any(Instant.class)))
          .thenReturn(true);

      // When
      coordinator.tryAcquire(testMessage, planWithLongOwner);

      // Then
      verify(relayStore)
          .acquireLease(anyLong(), anyLong(), leaseOwnerCaptor.capture(), any(Instant.class));

      assertThat(leaseOwnerCaptor.getValue()).isEqualTo(longOwner);
    }

    @Test
    @DisplayName("租约过期时间在遥远未来应正确处理")
    void shouldHandleDistantFutureLeaseExpiration() {
      // Given: 很长的租约持续时间
      Instant now = Instant.now();
      RelayPlan longLeasePlan =
          new RelayPlan(
              null,
              now,
              100,
              Duration.ofDays(365), // 1 年
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "owner");

      when(relayStore.acquireLease(anyLong(), anyLong(), anyString(), any(Instant.class)))
          .thenReturn(true);

      // When
      coordinator.tryAcquire(testMessage, longLeasePlan);

      // Then
      verify(relayStore)
          .acquireLease(anyLong(), anyLong(), anyString(), leaseExpireAtCaptor.capture());

      Instant expectedExpire = now.plus(Duration.ofDays(365));
      assertThat(leaseExpireAtCaptor.getValue()).isEqualTo(expectedExpire);
    }
  }
}
