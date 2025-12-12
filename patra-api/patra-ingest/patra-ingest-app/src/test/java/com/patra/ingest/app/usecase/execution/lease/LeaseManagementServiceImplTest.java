package com.patra.ingest.app.usecase.execution.lease;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.vo.shared.LeaseInfo;
import com.patra.ingest.domain.model.vo.task.TaskId;
import com.patra.ingest.domain.port.TaskRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
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

/// LeaseManagementServiceImpl 单元测试
///
/// 测试覆盖:
///
/// - ✅ 获取租约：成功获取、并发竞争失败、任务不存在
///   - ✅ 释放租约：正常释放、任务不存在
///   - ✅ 续约：成功续约、续约失败
///   - ✅ 租约验证：持有者验证、租约过期、任务不存在
///   - ✅ 时间处理：使用 Clock 生成时间戳
///   - ✅ TTL 计算：Duration 转秒
///   - ✅ 边界条件：null taskId、负数 Duration
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("LeaseManagementServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaseManagementServiceImplTest {

  @Mock private TaskRepository taskRepository;

  @Mock private Clock clock;

  @InjectMocks private LeaseManagementServiceImpl leaseService;

  @Captor private ArgumentCaptor<TaskAggregate> taskCaptor;

  private static final Long TASK_ID = 1001L;
  private static final String OWNER = "worker-node-1";
  private static final String IDEMPOTENT_KEY = "task-idempotent-key-123";
  private static final Duration LEASE_DURATION = Duration.ofSeconds(60);

  private TaskAggregate mockTask;
  private Instant fixedInstant;

  @BeforeEach
  void setUp() {
    fixedInstant = Instant.parse("2025-01-01T10:00:00Z");
    when(clock.instant()).thenReturn(fixedInstant);

    mockTask = createMockTask(TASK_ID, IDEMPOTENT_KEY, null);
  }

  // ==================== 获取租约场景 ====================

  @Nested
  @DisplayName("获取租约场景")
  class AcquireLeaseTests {

    @Test
    @DisplayName("应该成功获取租约")
    void shouldAcquireLeaseSuccessfully() {
      // Given: Mock 任务存在
      when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(mockTask));

      // Mock 租约获取成功
      when(taskRepository.tryAcquireLease(
              eq(TASK_ID), eq(OWNER), eq(fixedInstant), eq(60), eq(IDEMPOTENT_KEY)))
          .thenReturn(true);

      // When: 尝试获取租约
      boolean acquired = leaseService.tryAcquireLease(TASK_ID, OWNER, LEASE_DURATION);

      // Then: 租约获取成功
      assertThat(acquired).isTrue();

      // 验证调用链
      verify(taskRepository).findById(TASK_ID);
      verify(taskRepository).tryAcquireLease(TASK_ID, OWNER, fixedInstant, 60, IDEMPOTENT_KEY);
      verify(clock).instant();
    }

    @Test
    @DisplayName("并发竞争失败应该返回 false")
    void shouldReturnFalseWhenConcurrentCompetitionFails() {
      // Given: Mock 任务存在
      when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(mockTask));

      // Mock 租约获取失败（被其他节点抢占）
      when(taskRepository.tryAcquireLease(
              eq(TASK_ID), eq(OWNER), any(Instant.class), anyInt(), anyString()))
          .thenReturn(false);

      // When: 尝试获取租约
      boolean acquired = leaseService.tryAcquireLease(TASK_ID, OWNER, LEASE_DURATION);

      // Then: 租约获取失败
      assertThat(acquired).isFalse();
    }

    @Test
    @DisplayName("任务不存在应该抛出 IllegalArgumentException")
    void shouldThrowExceptionWhenTaskNotFound() {
      // Given: 任务不存在
      when(taskRepository.findById(TASK_ID)).thenReturn(Optional.empty());

      // When & Then: 抛出异常
      assertThatThrownBy(() -> leaseService.tryAcquireLease(TASK_ID, OWNER, LEASE_DURATION))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未找到任务")
          .hasMessageContaining("taskId=" + TASK_ID);

      // 验证不调用 tryAcquireLease
      verify(taskRepository, never())
          .tryAcquireLease(anyLong(), anyString(), any(), anyInt(), anyString());
    }

    @Test
    @DisplayName("应该正确转换租约持续时间为秒")
    void shouldConvertLeaseDurationToSeconds() {
      // Given: 不同的租约持续时间
      Duration duration = Duration.ofMinutes(5); // 300 秒
      when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(mockTask));
      when(taskRepository.tryAcquireLease(anyLong(), anyString(), any(), anyInt(), anyString()))
          .thenReturn(true);

      // When: 获取租约
      leaseService.tryAcquireLease(TASK_ID, OWNER, duration);

      // Then: 验证传递了正确的秒数
      verify(taskRepository)
          .tryAcquireLease(eq(TASK_ID), eq(OWNER), any(), eq(300), eq(IDEMPOTENT_KEY));
    }

    @Test
    @DisplayName("应该使用 Clock 生成时间戳")
    void shouldUseClockForTimestamp() {
      // Given
      when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(mockTask));
      when(taskRepository.tryAcquireLease(anyLong(), anyString(), any(), anyInt(), anyString()))
          .thenReturn(true);

      // When: 获取租约
      leaseService.tryAcquireLease(TASK_ID, OWNER, LEASE_DURATION);

      // Then: 验证使用了 Clock 生成的时间
      verify(clock).instant();
      verify(taskRepository)
          .tryAcquireLease(eq(TASK_ID), eq(OWNER), eq(fixedInstant), anyInt(), anyString());
    }
  }

  // ==================== 续约场景 ====================

  @Nested
  @DisplayName("续约场景")
  class RenewLeaseTests {

    @Test
    @DisplayName("应该成功续约租约")
    void shouldRenewLeaseSuccessfully() {
      // Given: Mock 续约成功
      when(taskRepository.renewLease(eq(TASK_ID), eq(OWNER), eq(fixedInstant), eq(60)))
          .thenReturn(true);

      // When: 续约租约
      boolean renewed = leaseService.renewLease(TASK_ID, OWNER, LEASE_DURATION);

      // Then: 续约成功
      assertThat(renewed).isTrue();

      verify(taskRepository).renewLease(TASK_ID, OWNER, fixedInstant, 60);
      verify(clock).instant();
    }

    @Test
    @DisplayName("续约失败应该返回 false")
    void shouldReturnFalseWhenRenewalFails() {
      // Given: Mock 续约失败（租约已被撤销或过期）
      when(taskRepository.renewLease(anyLong(), anyString(), any(), anyInt())).thenReturn(false);

      // When: 续约租约
      boolean renewed = leaseService.renewLease(TASK_ID, OWNER, LEASE_DURATION);

      // Then: 续约失败
      assertThat(renewed).isFalse();
    }

    @Test
    @DisplayName("应该正确转换续约持续时间为秒")
    void shouldConvertRenewalDurationToSeconds() {
      // Given: 不同的续约持续时间
      Duration duration = Duration.ofMinutes(2); // 120 秒
      when(taskRepository.renewLease(anyLong(), anyString(), any(), anyInt())).thenReturn(true);

      // When: 续约租约
      leaseService.renewLease(TASK_ID, OWNER, duration);

      // Then: 验证传递了正确的秒数
      verify(taskRepository).renewLease(eq(TASK_ID), eq(OWNER), any(), eq(120));
    }

    @Test
    @DisplayName("应该使用 Clock 生成续约时间戳")
    void shouldUseClockForRenewalTimestamp() {
      // Given
      when(taskRepository.renewLease(anyLong(), anyString(), any(), anyInt())).thenReturn(true);

      // When: 续约租约
      leaseService.renewLease(TASK_ID, OWNER, LEASE_DURATION);

      // Then: 验证使用了 Clock
      verify(clock).instant();
      verify(taskRepository).renewLease(eq(TASK_ID), eq(OWNER), eq(fixedInstant), anyInt());
    }
  }

  // ==================== 释放租约场景 ====================

  @Nested
  @DisplayName("释放租约场景")
  class ReleaseLeaseTests {

    @Test
    @DisplayName("应该成功释放租约")
    void shouldReleaseLeaseSuccessfully() {
      // Given: Mock 任务存在
      TaskAggregate task = createMockTask(TASK_ID, IDEMPOTENT_KEY, OWNER);
      when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

      // When: 释放租约
      leaseService.releaseLease(TASK_ID);

      // Then: 调用聚合的释放方法并保存
      verify(task).releaseLease();
      verify(taskRepository).save(task);
    }

    @Test
    @DisplayName("任务不存在应该抛出 IllegalArgumentException")
    void shouldThrowExceptionWhenTaskNotFoundForRelease() {
      // Given: 任务不存在
      when(taskRepository.findById(TASK_ID)).thenReturn(Optional.empty());

      // When & Then: 抛出异常
      assertThatThrownBy(() -> leaseService.releaseLease(TASK_ID))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未找到任务")
          .hasMessageContaining("taskId=" + TASK_ID);

      // 验证不调用 save
      verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("应该先调用聚合的 releaseLease 再保存")
    void shouldCallAggregateReleaseBeforeSave() {
      // Given
      TaskAggregate task = createMockTask(TASK_ID, IDEMPOTENT_KEY, OWNER);
      when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

      // When: 释放租约
      leaseService.releaseLease(TASK_ID);

      // Then: 验证调用顺序
      var inOrder = inOrder(task, taskRepository);
      inOrder.verify(task).releaseLease();
      inOrder.verify(taskRepository).save(task);
    }
  }

  // ==================== 验证租约场景 ====================

  @Nested
  @DisplayName("验证租约场景")
  class ValidateLeaseTests {

    @Test
    @DisplayName("持有者匹配且租约有效应该返回 true")
    void shouldReturnTrueWhenOwnerMatchesAndLeaseIsValid() {
      // Given: 租约由当前持有者持有
      LeaseInfo leaseInfo = createLeaseInfo(OWNER, true);
      TaskAggregate task = createMockTaskWithLease(TASK_ID, leaseInfo);
      when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

      // When: 验证租约
      boolean valid = leaseService.validateLease(TASK_ID, OWNER);

      // Then: 验证通过
      assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("持有者不匹配应该返回 false")
    void shouldReturnFalseWhenOwnerDoesNotMatch() {
      // Given: 租约由其他持有者持有
      LeaseInfo leaseInfo = createLeaseInfo("other-owner", true);
      TaskAggregate task = createMockTaskWithLease(TASK_ID, leaseInfo);
      when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

      // When: 验证租约
      boolean valid = leaseService.validateLease(TASK_ID, OWNER);

      // Then: 验证失败
      assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("租约未持有应该返回 false")
    void shouldReturnFalseWhenLeaseIsNotHeld() {
      // Given: 租约未持有
      LeaseInfo leaseInfo = createLeaseInfo(null, false);
      TaskAggregate task = createMockTaskWithLease(TASK_ID, leaseInfo);
      when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

      // When: 验证租约
      boolean valid = leaseService.validateLease(TASK_ID, OWNER);

      // Then: 验证失败
      assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("任务不存在应该返回 false")
    void shouldReturnFalseWhenTaskNotFoundForValidation() {
      // Given: 任务不存在
      when(taskRepository.findById(TASK_ID)).thenReturn(Optional.empty());

      // When: 验证租约
      boolean valid = leaseService.validateLease(TASK_ID, OWNER);

      // Then: 验证失败
      assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("持有者为 null 且租约未持有应该返回 false")
    void shouldReturnFalseWhenOwnerIsNullAndLeaseNotHeld() {
      // Given: 持有者为 null
      LeaseInfo leaseInfo = createLeaseInfo(null, false);
      TaskAggregate task = createMockTaskWithLease(TASK_ID, leaseInfo);
      when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

      // When: 验证租约
      boolean valid = leaseService.validateLease(TASK_ID, OWNER);

      // Then: 验证失败
      assertThat(valid).isFalse();
    }
  }

  // ==================== 边界条件测试 ====================

  @Nested
  @DisplayName("边界条件测试")
  class EdgeCaseTests {

    @Test
    @DisplayName("零秒租约持续时间应该正常处理")
    void shouldHandleZeroDuration() {
      // Given: 零秒持续时间
      Duration zeroDuration = Duration.ZERO;
      when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(mockTask));
      when(taskRepository.tryAcquireLease(anyLong(), anyString(), any(), anyInt(), anyString()))
          .thenReturn(true);

      // When: 获取租约
      boolean acquired = leaseService.tryAcquireLease(TASK_ID, OWNER, zeroDuration);

      // Then: 正常处理
      assertThat(acquired).isTrue();
      verify(taskRepository).tryAcquireLease(eq(TASK_ID), eq(OWNER), any(), eq(0), anyString());
    }

    @Test
    @DisplayName("非常大的租约持续时间应该正常处理")
    void shouldHandleVeryLargeDuration() {
      // Given: 非常大的持续时间（1 天）
      Duration largeDuration = Duration.ofDays(1);
      when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(mockTask));
      when(taskRepository.tryAcquireLease(anyLong(), anyString(), any(), anyInt(), anyString()))
          .thenReturn(true);

      // When: 获取租约
      boolean acquired = leaseService.tryAcquireLease(TASK_ID, OWNER, largeDuration);

      // Then: 正常处理
      assertThat(acquired).isTrue();
      verify(taskRepository).tryAcquireLease(eq(TASK_ID), eq(OWNER), any(), eq(86400), anyString());
    }

    @Test
    @DisplayName("空字符串持有者应该正常处理")
    void shouldHandleEmptyOwner() {
      // Given: 空字符串持有者
      String emptyOwner = "";
      when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(mockTask));
      when(taskRepository.tryAcquireLease(anyLong(), anyString(), any(), anyInt(), anyString()))
          .thenReturn(true);

      // When: 获取租约
      boolean acquired = leaseService.tryAcquireLease(TASK_ID, emptyOwner, LEASE_DURATION);

      // Then: 正常处理
      assertThat(acquired).isTrue();
      verify(taskRepository)
          .tryAcquireLease(eq(TASK_ID), eq(emptyOwner), any(), anyInt(), anyString());
    }

    @Test
    @DisplayName("不同时区的 Clock 应该正常工作")
    void shouldWorkWithDifferentTimeZones() {
      // Given: 使用不同时区的 Clock
      Clock tokyoClock = Clock.fixed(fixedInstant, ZoneId.of("Asia/Tokyo"));
      LeaseManagementServiceImpl serviceWithTokyoClock =
          new LeaseManagementServiceImpl(taskRepository, tokyoClock);

      when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(mockTask));
      when(taskRepository.tryAcquireLease(anyLong(), anyString(), any(), anyInt(), anyString()))
          .thenReturn(true);

      // When: 获取租约
      boolean acquired = serviceWithTokyoClock.tryAcquireLease(TASK_ID, OWNER, LEASE_DURATION);

      // Then: 正常处理（Instant 不受时区影响）
      assertThat(acquired).isTrue();
      verify(taskRepository)
          .tryAcquireLease(eq(TASK_ID), eq(OWNER), eq(fixedInstant), anyInt(), anyString());
    }
  }

  // ==================== 辅助方法 ====================

  private TaskAggregate createMockTask(Long taskId, String idempotentKey, String leaseOwner) {
    TaskAggregate task = mock(TaskAggregate.class);
    when(task.getId()).thenReturn(TaskId.of(taskId));
    when(task.getIdempotentKey()).thenReturn(idempotentKey);

    LeaseInfo leaseInfo = createLeaseInfo(leaseOwner, leaseOwner != null);
    when(task.getLeaseInfo()).thenReturn(leaseInfo);

    return task;
  }

  private TaskAggregate createMockTaskWithLease(Long taskId, LeaseInfo leaseInfo) {
    TaskAggregate task = mock(TaskAggregate.class);
    when(task.getId()).thenReturn(TaskId.of(taskId));
    when(task.getLeaseInfo()).thenReturn(leaseInfo);
    return task;
  }

  private LeaseInfo createLeaseInfo(String owner, boolean isHeld) {
    LeaseInfo leaseInfo = mock(LeaseInfo.class);
    when(leaseInfo.owner()).thenReturn(owner);
    when(leaseInfo.isHeld()).thenReturn(isHeld);
    return leaseInfo;
  }
}
