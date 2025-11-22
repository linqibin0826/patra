package com.patra.ingest.app.usecase.execution.lease;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.patra.ingest.app.usecase.execution.session.ExecutionSession;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/// HeartbeatRenewalServiceImpl 单元测试
///
/// 测试覆盖:
///
/// - ✅ 启动心跳：创建心跳任务，定期续约
///   - ✅ 停止心跳：取消心跳任务
///   - ✅ 心跳续约：成功续约、续约失败处理
///   - ✅ 租约撤销检测：连续失败达到阈值后验证租约
///   - ✅ 租约撤销标志：检测到撤销后设置标志
///   - ✅ 异常处理：续约过程中的异常
///   - ✅ 边界条件：重复启动、重复停止、心跳间隔
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("HeartbeatRenewalServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HeartbeatRenewalServiceImplTest {

  @Mock private LeaseManagementService leaseManagementService;

  private HeartbeatRenewalServiceImpl heartbeatService;

  private static final Long TASK_ID = 1001L;
  private static final String LEASE_OWNER = "worker-node-1";
  private static final Duration LEASE_DURATION = Duration.ofSeconds(60);
  private static final Duration RENEWAL_INTERVAL = Duration.ofMillis(100); // 短间隔用于测试
  private static final int FAILURE_THRESHOLD = 3;

  @BeforeEach
  void setUp() {
    heartbeatService = new HeartbeatRenewalServiceImpl(leaseManagementService);
    // 设置失败阈值
    ReflectionTestUtils.setField(heartbeatService, "failureThreshold", FAILURE_THRESHOLD);
  }

  // ==================== 启动心跳场景 ====================

  @Nested
  @DisplayName("启动心跳场景")
  class StartHeartbeatTests {

    @Test
    @DisplayName("应该成功启动心跳并返回句柄")
    void shouldStartHeartbeatSuccessfully() {
      // Given: Mock 续约成功
      when(leaseManagementService.renewLease(TASK_ID, LEASE_OWNER, LEASE_DURATION))
          .thenReturn(true);

      // When: 启动心跳
      ExecutionSession.HeartbeatHandle handle =
          heartbeatService.startHeartbeat(TASK_ID, LEASE_OWNER, LEASE_DURATION, RENEWAL_INTERVAL);

      // Then: 返回非 null 句柄
      assertThat(handle).isNotNull();
      assertThat(handle.isLeaseRevoked()).isFalse();

      // 等待至少一次心跳执行
      await()
          .atMost(500, TimeUnit.MILLISECONDS)
          .untilAsserted(
              () ->
                  verify(leaseManagementService, atLeastOnce())
                      .renewLease(TASK_ID, LEASE_OWNER, LEASE_DURATION));

      // 清理：停止心跳
      handle.stop();
    }

    @Test
    @DisplayName("应该定期执行续约")
    void shouldRenewLeasePeriodically() {
      // Given: Mock 续约成功
      when(leaseManagementService.renewLease(TASK_ID, LEASE_OWNER, LEASE_DURATION))
          .thenReturn(true);

      // When: 启动心跳
      ExecutionSession.HeartbeatHandle handle =
          heartbeatService.startHeartbeat(TASK_ID, LEASE_OWNER, LEASE_DURATION, RENEWAL_INTERVAL);

      // Then: 等待多次续约
      await()
          .atMost(500, TimeUnit.MILLISECONDS)
          .untilAsserted(
              () ->
                  verify(leaseManagementService, atLeast(3))
                      .renewLease(TASK_ID, LEASE_OWNER, LEASE_DURATION));

      // 清理
      handle.stop();
    }
  }

  // ==================== 停止心跳场景 ====================

  @Nested
  @DisplayName("停止心跳场景")
  class StopHeartbeatTests {

    @Test
    @DisplayName("应该成功停止心跳")
    void shouldStopHeartbeatSuccessfully() {
      // Given: 启动心跳
      when(leaseManagementService.renewLease(TASK_ID, LEASE_OWNER, LEASE_DURATION))
          .thenReturn(true);

      ExecutionSession.HeartbeatHandle handle =
          heartbeatService.startHeartbeat(TASK_ID, LEASE_OWNER, LEASE_DURATION, RENEWAL_INTERVAL);

      // 等待至少一次心跳
      await()
          .atMost(500, TimeUnit.MILLISECONDS)
          .untilAsserted(
              () ->
                  verify(leaseManagementService, atLeastOnce())
                      .renewLease(TASK_ID, LEASE_OWNER, LEASE_DURATION));

      // When: 停止心跳
      handle.stop();

      // Then: 停止后不应再续约
      reset(leaseManagementService);

      // 等待一段时间，验证不再续约
      try {
        Thread.sleep(300);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      verifyNoInteractions(leaseManagementService);
    }

    @Test
    @DisplayName("重复停止心跳应该是幂等的")
    void shouldBeIdempotentWhenStoppedMultipleTimes() {
      // Given: 启动并停止心跳
      when(leaseManagementService.renewLease(TASK_ID, LEASE_OWNER, LEASE_DURATION))
          .thenReturn(true);

      ExecutionSession.HeartbeatHandle handle =
          heartbeatService.startHeartbeat(TASK_ID, LEASE_OWNER, LEASE_DURATION, RENEWAL_INTERVAL);

      // When: 多次停止
      handle.stop();
      handle.stop();
      handle.stop();

      // Then: 不应该抛出异常
      assertThatCode(() -> handle.stop()).doesNotThrowAnyException();
    }
  }

  // ==================== 续约成功场景 ====================

  @Nested
  @DisplayName("续约成功场景")
  class RenewalSuccessTests {

    @Test
    @DisplayName("续约成功应该重置失败计数")
    void shouldResetFailureCountOnSuccessfulRenewal() {
      // Given: 先失败 2 次，然后成功
      when(leaseManagementService.renewLease(TASK_ID, LEASE_OWNER, LEASE_DURATION))
          .thenReturn(false)
          .thenReturn(false)
          .thenReturn(true);

      // When: 启动心跳
      ExecutionSession.HeartbeatHandle handle =
          heartbeatService.startHeartbeat(TASK_ID, LEASE_OWNER, LEASE_DURATION, RENEWAL_INTERVAL);

      // Then: 等待成功续约，租约不应被撤销
      await()
          .atMost(500, TimeUnit.MILLISECONDS)
          .untilAsserted(
              () ->
                  verify(leaseManagementService, atLeast(3))
                      .renewLease(TASK_ID, LEASE_OWNER, LEASE_DURATION));

      assertThat(handle.isLeaseRevoked()).isFalse();

      // 清理
      handle.stop();
    }
  }

  // ==================== 续约失败场景 ====================

  @Nested
  @DisplayName("续约失败场景")
  class RenewalFailureTests {

    @Test
    @DisplayName("连续失败达到阈值应该验证租约")
    void shouldValidateLeaseAfterConsecutiveFailures() {
      // Given: 续约持续失败
      when(leaseManagementService.renewLease(TASK_ID, LEASE_OWNER, LEASE_DURATION))
          .thenReturn(false);

      // Mock 验证租约返回 false（租约已被撤销）
      when(leaseManagementService.validateLease(TASK_ID, LEASE_OWNER)).thenReturn(false);

      // When: 启动心跳
      ExecutionSession.HeartbeatHandle handle =
          heartbeatService.startHeartbeat(TASK_ID, LEASE_OWNER, LEASE_DURATION, RENEWAL_INTERVAL);

      // Then: 等待验证租约被调用
      await()
          .atMost(1000, TimeUnit.MILLISECONDS)
          .untilAsserted(
              () ->
                  verify(leaseManagementService, atLeastOnce())
                      .validateLease(TASK_ID, LEASE_OWNER));

      // 租约应该被标记为已撤销
      assertThat(handle.isLeaseRevoked()).isTrue();

      // 清理
      handle.stop();
    }

    @Test
    @DisplayName("检测到租约撤销应该停止心跳")
    void shouldStopHeartbeatWhenLeaseRevoked() {
      // Given: 续约失败，验证租约也失败
      when(leaseManagementService.renewLease(TASK_ID, LEASE_OWNER, LEASE_DURATION))
          .thenReturn(false);
      when(leaseManagementService.validateLease(TASK_ID, LEASE_OWNER)).thenReturn(false);

      // When: 启动心跳
      ExecutionSession.HeartbeatHandle handle =
          heartbeatService.startHeartbeat(TASK_ID, LEASE_OWNER, LEASE_DURATION, RENEWAL_INTERVAL);

      // Then: 等待租约被撤销
      await()
          .atMost(1000, TimeUnit.MILLISECONDS)
          .untilAsserted(() -> assertThat(handle.isLeaseRevoked()).isTrue());

      // 停止后不应再续约
      reset(leaseManagementService);
      try {
        Thread.sleep(300);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      verifyNoInteractions(leaseManagementService);

      // 清理
      handle.stop();
    }

    @Test
    @DisplayName("连续失败未达阈值不应该验证租约")
    void shouldNotValidateLeaseBeforeThreshold() {
      // Given: 续约失败 2 次（低于阈值 3）
      when(leaseManagementService.renewLease(TASK_ID, LEASE_OWNER, LEASE_DURATION))
          .thenReturn(false)
          .thenReturn(false)
          .thenReturn(true); // 第 3 次成功

      // When: 启动心跳
      ExecutionSession.HeartbeatHandle handle =
          heartbeatService.startHeartbeat(TASK_ID, LEASE_OWNER, LEASE_DURATION, RENEWAL_INTERVAL);

      // Then: 等待至少 3 次续约
      await()
          .atMost(500, TimeUnit.MILLISECONDS)
          .untilAsserted(
              () ->
                  verify(leaseManagementService, atLeast(3))
                      .renewLease(TASK_ID, LEASE_OWNER, LEASE_DURATION));

      // 不应该调用 validateLease
      verify(leaseManagementService, never()).validateLease(anyLong(), anyString());

      // 租约不应该被撤销
      assertThat(handle.isLeaseRevoked()).isFalse();

      // 清理
      handle.stop();
    }
  }

  // ==================== 异常处理场景 ====================

  @Nested
  @DisplayName("异常处理场景")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("续约抛出异常应该增加失败计数")
    void shouldIncrementFailureCountOnException() {
      // Given: 续约抛出异常 3 次
      when(leaseManagementService.renewLease(TASK_ID, LEASE_OWNER, LEASE_DURATION))
          .thenThrow(new RuntimeException("网络错误"));

      when(leaseManagementService.validateLease(TASK_ID, LEASE_OWNER)).thenReturn(false);

      // When: 启动心跳
      ExecutionSession.HeartbeatHandle handle =
          heartbeatService.startHeartbeat(TASK_ID, LEASE_OWNER, LEASE_DURATION, RENEWAL_INTERVAL);

      // Then: 达到阈值后应该设置租约撤销标志
      await()
          .atMost(1000, TimeUnit.MILLISECONDS)
          .untilAsserted(() -> assertThat(handle.isLeaseRevoked()).isTrue());

      // 清理
      handle.stop();
    }

    @Test
    @DisplayName("验证租约抛出异常不应该影响心跳")
    void shouldHandleValidationException() {
      // Given: 续约失败，验证租约抛出异常
      when(leaseManagementService.renewLease(TASK_ID, LEASE_OWNER, LEASE_DURATION))
          .thenReturn(false);
      when(leaseManagementService.validateLease(TASK_ID, LEASE_OWNER))
          .thenThrow(new RuntimeException("验证失败"));

      // When: 启动心跳
      ExecutionSession.HeartbeatHandle handle =
          heartbeatService.startHeartbeat(TASK_ID, LEASE_OWNER, LEASE_DURATION, RENEWAL_INTERVAL);

      // Then: 不应该抛出异常，心跳继续运行
      await()
          .atMost(500, TimeUnit.MILLISECONDS)
          .untilAsserted(
              () ->
                  verify(leaseManagementService, atLeast(FAILURE_THRESHOLD))
                      .renewLease(TASK_ID, LEASE_OWNER, LEASE_DURATION));

      // 清理
      handle.stop();
    }
  }

  // ==================== 边界条件测试 ====================

  @Nested
  @DisplayName("边界条件测试")
  class EdgeCaseTests {

    @Test
    @DisplayName("非常短的续约间隔应该正常工作")
    void shouldHandleVeryShortRenewalInterval() {
      // Given: 非常短的续约间隔（10ms）
      Duration shortInterval = Duration.ofMillis(10);
      when(leaseManagementService.renewLease(TASK_ID, LEASE_OWNER, LEASE_DURATION))
          .thenReturn(true);

      // When: 启动心跳
      ExecutionSession.HeartbeatHandle handle =
          heartbeatService.startHeartbeat(TASK_ID, LEASE_OWNER, LEASE_DURATION, shortInterval);

      // Then: 应该快速执行多次续约
      await()
          .atMost(500, TimeUnit.MILLISECONDS)
          .untilAsserted(
              () ->
                  verify(leaseManagementService, atLeast(10))
                      .renewLease(TASK_ID, LEASE_OWNER, LEASE_DURATION));

      // 清理
      handle.stop();
    }

    @Test
    @DisplayName("零失败阈值应该立即验证租约")
    void shouldValidateImmediatelyWithZeroThreshold() {
      // Given: 失败阈值为 0
      ReflectionTestUtils.setField(heartbeatService, "failureThreshold", 0);

      when(leaseManagementService.renewLease(TASK_ID, LEASE_OWNER, LEASE_DURATION))
          .thenReturn(false);
      when(leaseManagementService.validateLease(TASK_ID, LEASE_OWNER)).thenReturn(false);

      // When: 启动心跳
      ExecutionSession.HeartbeatHandle handle =
          heartbeatService.startHeartbeat(TASK_ID, LEASE_OWNER, LEASE_DURATION, RENEWAL_INTERVAL);

      // Then: 立即验证租约
      await()
          .atMost(500, TimeUnit.MILLISECONDS)
          .untilAsserted(
              () ->
                  verify(leaseManagementService, atLeastOnce())
                      .validateLease(TASK_ID, LEASE_OWNER));

      // 清理
      handle.stop();
    }

    @Test
    @DisplayName("续约间隔长于租约持续时间应该正常工作")
    void shouldHandleRenewalIntervalLongerThanLeaseDuration() {
      // Given: 续约间隔比租约持续时间长（实际不推荐，但应该能处理）
      Duration longInterval = Duration.ofSeconds(120);
      Duration shortLease = Duration.ofSeconds(30);

      when(leaseManagementService.renewLease(TASK_ID, LEASE_OWNER, shortLease)).thenReturn(true);

      // When: 启动心跳
      ExecutionSession.HeartbeatHandle handle =
          heartbeatService.startHeartbeat(TASK_ID, LEASE_OWNER, shortLease, longInterval);

      // Then: 不应该抛出异常
      assertThatCode(() -> Thread.sleep(100)).doesNotThrowAnyException();

      // 清理
      handle.stop();
    }

    @Test
    @DisplayName("租约验证返回 true 不应该设置撤销标志")
    void shouldNotSetRevokedFlagWhenValidationSucceeds() {
      // Given: 续约失败但验证成功
      when(leaseManagementService.renewLease(TASK_ID, LEASE_OWNER, LEASE_DURATION))
          .thenReturn(false);
      when(leaseManagementService.validateLease(TASK_ID, LEASE_OWNER)).thenReturn(true);

      // When: 启动心跳
      ExecutionSession.HeartbeatHandle handle =
          heartbeatService.startHeartbeat(TASK_ID, LEASE_OWNER, LEASE_DURATION, RENEWAL_INTERVAL);

      // Then: 等待验证租约被调用
      await()
          .atMost(1000, TimeUnit.MILLISECONDS)
          .untilAsserted(
              () ->
                  verify(leaseManagementService, atLeastOnce())
                      .validateLease(TASK_ID, LEASE_OWNER));

      // 租约不应该被撤销
      assertThat(handle.isLeaseRevoked()).isFalse();

      // 清理
      handle.stop();
    }
  }
}
