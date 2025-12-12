package com.patra.ingest.app.usecase.execution.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.usecase.execution.lease.HeartbeatRenewalService;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.entity.TaskRun;
import com.patra.ingest.domain.model.vo.task.TaskId;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.ingest.domain.port.TaskRunRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/// 执行会话管理器单元测试
///
/// 测试策略：
///
/// - Mock TaskRepository、TaskRunRepository、HeartbeatRenewalService
///   - 验证 TaskRun 创建和心跳启动
///   - 测试不同的 attemptNo 计算
///
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("执行会话管理器测试")
class ExecutionSessionManagerImplTest {

  @Mock private TaskRepository taskRepository;

  @Mock private TaskRunRepository taskRunRepository;

  @Mock private HeartbeatRenewalService heartbeatRenewalService;

  @InjectMocks private ExecutionSessionManagerImpl executionSessionManager;

  @Mock private ExecutionSession.HeartbeatHandle heartbeatHandle;

  private static final Long TASK_ID = 1L;
  private static final Long RUN_ID = 100L;
  private static final String LEASE_OWNER = "node-1";
  private static final String CORRELATION_ID = "corr-123";
  private static final ProvenanceCode PROVENANCE_CODE = ProvenanceCode.PUBMED;
  private static final String OPERATION_CODE = "ingest";

  @BeforeEach
  void setUp() {
    // 设置配置项
    ReflectionTestUtils.setField(executionSessionManager, "leaseDurationSeconds", 60);
    ReflectionTestUtils.setField(executionSessionManager, "renewalIntervalSeconds", 20);
  }

  @Nested
  @DisplayName("创建会话（通过taskId）")
  class CreateSessionByTaskIdTests {

    @Test
    @DisplayName("成功创建会话，首次尝试（attemptNo=1）")
    void shouldCreateSession_firstAttempt() {
      // Arrange
      TaskAggregate task = createTask();
      when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
      when(taskRunRepository.getLatestAttemptNo(TASK_ID)).thenReturn(0);

      TaskRun savedRun = createTaskRun(RUN_ID, TASK_ID, 1);
      when(taskRunRepository.save(any(TaskRun.class))).thenReturn(savedRun);

      when(heartbeatRenewalService.startHeartbeat(
              eq(TASK_ID), eq(LEASE_OWNER), any(Duration.class), any(Duration.class)))
          .thenReturn(heartbeatHandle);

      // Act
      ExecutionSession session =
          executionSessionManager.createSession(TASK_ID, LEASE_OWNER, CORRELATION_ID);

      // Assert
      assertThat(session).isNotNull();
      assertThat(session.taskId()).isEqualTo(TASK_ID);
      assertThat(session.runId()).isEqualTo(RUN_ID);
      assertThat(session.leaseOwner()).isEqualTo(LEASE_OWNER);
      assertThat(session.heartbeatHandle()).isEqualTo(heartbeatHandle);

      // 验证 TaskRun 创建
      ArgumentCaptor<TaskRun> taskRunCaptor = ArgumentCaptor.forClass(TaskRun.class);
      verify(taskRunRepository).save(taskRunCaptor.capture());
      TaskRun captured = taskRunCaptor.getValue();
      assertThat(captured.getAttemptNo()).isEqualTo(1);
      assertThat(captured.getTaskId()).isEqualTo(TASK_ID);
    }

    @Test
    @DisplayName("成功创建会话，重试场景（attemptNo=3）")
    void shouldCreateSession_retryAttempt() {
      // Arrange
      TaskAggregate task = createTask();
      when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
      when(taskRunRepository.getLatestAttemptNo(TASK_ID)).thenReturn(2); // 已有2次尝试

      TaskRun savedRun = createTaskRun(RUN_ID, TASK_ID, 3);
      when(taskRunRepository.save(any(TaskRun.class))).thenReturn(savedRun);

      when(heartbeatRenewalService.startHeartbeat(
              eq(TASK_ID), eq(LEASE_OWNER), any(Duration.class), any(Duration.class)))
          .thenReturn(heartbeatHandle);

      // Act
      ExecutionSession session =
          executionSessionManager.createSession(TASK_ID, LEASE_OWNER, CORRELATION_ID);

      // Assert
      assertThat(session).isNotNull();
      assertThat(session.runId()).isEqualTo(RUN_ID);

      // 验证 attemptNo
      ArgumentCaptor<TaskRun> taskRunCaptor = ArgumentCaptor.forClass(TaskRun.class);
      verify(taskRunRepository).save(taskRunCaptor.capture());
      assertThat(taskRunCaptor.getValue().getAttemptNo()).isEqualTo(3);
    }

    @Test
    @DisplayName("任务不存在，抛出异常")
    void shouldThrowException_whenTaskNotFound() {
      // Arrange
      when(taskRepository.findById(TASK_ID)).thenReturn(Optional.empty());

      // Act & Assert
      assertThatThrownBy(
              () -> executionSessionManager.createSession(TASK_ID, LEASE_OWNER, CORRELATION_ID))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Task not found");
    }

    @Test
    @DisplayName("验证心跳启动参数正确")
    void shouldStartHeartbeat_withCorrectParameters() {
      // Arrange
      TaskAggregate task = createTask();
      when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
      when(taskRunRepository.getLatestAttemptNo(TASK_ID)).thenReturn(0);

      TaskRun savedRun = createTaskRun(RUN_ID, TASK_ID, 1);
      when(taskRunRepository.save(any(TaskRun.class))).thenReturn(savedRun);

      when(heartbeatRenewalService.startHeartbeat(
              eq(TASK_ID), eq(LEASE_OWNER), any(Duration.class), any(Duration.class)))
          .thenReturn(heartbeatHandle);

      // Act
      executionSessionManager.createSession(TASK_ID, LEASE_OWNER, CORRELATION_ID);

      // Assert
      verify(heartbeatRenewalService)
          .startHeartbeat(
              eq(TASK_ID), eq(LEASE_OWNER), eq(Duration.ofSeconds(60)), eq(Duration.ofSeconds(20)));
    }
  }

  @Nested
  @DisplayName("创建会话（通过Task聚合）")
  class CreateSessionByTaskAggregateTests {

    @Test
    @DisplayName("成功创建会话，避免重复加载Task")
    void shouldCreateSession_withoutReloadingTask() {
      // Arrange
      TaskAggregate task = createTask();
      when(taskRunRepository.getLatestAttemptNo(TASK_ID)).thenReturn(0);

      TaskRun savedRun = createTaskRun(RUN_ID, TASK_ID, 1);
      when(taskRunRepository.save(any(TaskRun.class))).thenReturn(savedRun);

      when(heartbeatRenewalService.startHeartbeat(
              eq(TASK_ID), eq(LEASE_OWNER), any(Duration.class), any(Duration.class)))
          .thenReturn(heartbeatHandle);

      // Act
      ExecutionSession session =
          executionSessionManager.createSession(task, LEASE_OWNER, CORRELATION_ID);

      // Assert
      assertThat(session).isNotNull();
      assertThat(session.taskId()).isEqualTo(TASK_ID);
      assertThat(session.runId()).isEqualTo(RUN_ID);

      // 验证没有调用 findById
      verify(taskRepository, org.mockito.Mockito.never()).findById(anyLong());
    }

    @Test
    @DisplayName("成功创建会话，使用Task中的provenance和operation信息")
    void shouldCreateSession_withTaskMetadata() {
      // Arrange
      TaskAggregate task = createTask();
      when(taskRunRepository.getLatestAttemptNo(TASK_ID)).thenReturn(0);

      TaskRun savedRun = createTaskRun(RUN_ID, TASK_ID, 1);
      when(taskRunRepository.save(any(TaskRun.class))).thenReturn(savedRun);

      when(heartbeatRenewalService.startHeartbeat(
              eq(TASK_ID), eq(LEASE_OWNER), any(Duration.class), any(Duration.class)))
          .thenReturn(heartbeatHandle);

      // Act
      executionSessionManager.createSession(task, LEASE_OWNER, CORRELATION_ID);

      // Assert
      ArgumentCaptor<TaskRun> taskRunCaptor = ArgumentCaptor.forClass(TaskRun.class);
      verify(taskRunRepository).save(taskRunCaptor.capture());
      TaskRun captured = taskRunCaptor.getValue();
      assertThat(captured.getProvenanceCode()).isEqualTo(PROVENANCE_CODE);
      assertThat(captured.getOperationCode()).isEqualTo(OPERATION_CODE);
    }

    @Test
    @DisplayName("边界条件：attemptNo从大值递增")
    void shouldHandleLargeAttemptNo() {
      // Arrange
      TaskAggregate task = createTask();
      when(taskRunRepository.getLatestAttemptNo(TASK_ID)).thenReturn(999);

      TaskRun savedRun = createTaskRun(RUN_ID, TASK_ID, 1000);
      when(taskRunRepository.save(any(TaskRun.class))).thenReturn(savedRun);

      when(heartbeatRenewalService.startHeartbeat(
              eq(TASK_ID), eq(LEASE_OWNER), any(Duration.class), any(Duration.class)))
          .thenReturn(heartbeatHandle);

      // Act
      ExecutionSession session =
          executionSessionManager.createSession(task, LEASE_OWNER, CORRELATION_ID);

      // Assert
      assertThat(session).isNotNull();

      ArgumentCaptor<TaskRun> taskRunCaptor = ArgumentCaptor.forClass(TaskRun.class);
      verify(taskRunRepository).save(taskRunCaptor.capture());
      assertThat(taskRunCaptor.getValue().getAttemptNo()).isEqualTo(1000);
    }
  }

  // ========== 辅助方法 ==========

  private TaskAggregate createTask() {
    // 使用静态工厂方法 restore() 创建测试用的 TaskAggregate
    return TaskAggregate.restore(
        TaskId.of(TASK_ID),
        null, // scheduleInstanceId
        null, // planId
        null, // sliceId
        PROVENANCE_CODE,
        OPERATION_CODE,
        "{}", // paramsJson
        "test-idempotent-key", // idempotentKey
        null, // exprHash
        1, // priority
        java.time.Instant.now(), // scheduledAt
        null, // lastHeartbeatAt
        0, // retryCount
        null, // lastErrorCode
        null, // lastErrorMsg
        com.patra.ingest.domain.model.enums.TaskStatus.QUEUED,
        null, // leaseInfo
        null, // executionTimeline
        null, // schedulerContext
        0L // version
        );
  }

  private TaskRun createTaskRun(Long runId, Long taskId, int attemptNo) {
    return new TaskRun(runId, taskId, attemptNo, PROVENANCE_CODE, OPERATION_CODE);
  }
}
