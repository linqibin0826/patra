# Application 层测试模板

应用层测试主要使用 Mock 来隔离依赖，验证编排逻辑和事件处理。

## Orchestrator 测试 {#orchestrator}

### 基础 Orchestrator 测试模板

```java
package com.patra.{service}.app.orchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlanIngestionOrchestrator 编排器测试")
class PlanIngestionOrchestratorTest {

    @Mock
    private PlanValidationCoordinator validationCoordinator;

    @Mock
    private PlanPersistenceCoordinator persistenceCoordinator;

    @Mock
    private EventPublishingCoordinator eventCoordinator;

    @Mock
    private ProvenancePort provenancePort;

    @InjectMocks
    private PlanIngestionOrchestrator orchestrator;

    private PlanIngestionCommand command;

    @BeforeEach
    void setUp() {
        command = PlanIngestionCommand.builder()
            .provenanceCode("PUBMED")
            .config("{}")
            .build();
    }

    @Test
    @DisplayName("应该按正确顺序编排计划摄入")
    void shouldOrchestrateIngestionInCorrectOrder() {
        // Arrange
        var provenance = createProvenance();
        var plan = createPlan();

        when(provenancePort.findByCode("PUBMED")).thenReturn(provenance);
        when(validationCoordinator.validate(any())).thenReturn(true);
        when(persistenceCoordinator.save(any())).thenReturn(plan);

        // Act
        var result = orchestrator.ingest(command);

        // Assert - 验证调用顺序
        InOrder inOrder = inOrder(
            provenancePort,
            validationCoordinator,
            persistenceCoordinator,
            eventCoordinator
        );

        inOrder.verify(provenancePort).findByCode("PUBMED");
        inOrder.verify(validationCoordinator).validate(any());
        inOrder.verify(persistenceCoordinator).save(any());
        inOrder.verify(eventCoordinator).publishPlanCreated(any());

        assertThat(result).isNotNull();
        assertThat(result.getPlanId()).isEqualTo(plan.getId());
    }

    @Test
    @DisplayName("应该在验证失败时抛出异常")
    void shouldThrowExceptionWhenValidationFails() {
        // Arrange
        when(provenancePort.findByCode("PUBMED")).thenReturn(createProvenance());
        when(validationCoordinator.validate(any()))
            .thenThrow(new ValidationException("Invalid configuration"));

        // Act & Assert
        assertThatThrownBy(() -> orchestrator.ingest(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Invalid configuration");

        // 验证后续步骤未执行
        verify(persistenceCoordinator, never()).save(any());
        verify(eventCoordinator, never()).publishPlanCreated(any());
    }

    @Test
    @DisplayName("应该处理 Provenance 不存在的情况")
    void shouldHandleProvenanceNotFound() {
        // Arrange
        when(provenancePort.findByCode("UNKNOWN")).thenReturn(null);

        command = command.toBuilder()
            .provenanceCode("UNKNOWN")
            .build();

        // Act & Assert
        assertThatThrownBy(() -> orchestrator.ingest(command))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Provenance not found: UNKNOWN");
    }

    @Test
    @DisplayName("应该在事务回滚时不发布事件")
    void shouldNotPublishEventOnRollback() {
        // Arrange
        when(provenancePort.findByCode("PUBMED")).thenReturn(createProvenance());
        when(persistenceCoordinator.save(any()))
            .thenThrow(new DataAccessException("Database error"));

        // Act & Assert
        assertThatThrownBy(() -> orchestrator.ingest(command))
            .isInstanceOf(DataAccessException.class);

        // 验证事件未发布（事务回滚）
        verify(eventCoordinator, never()).publishPlanCreated(any());
    }
}
```

## Coordinator 测试 {#coordinator}

### 关注点分离的 Coordinator 测试

```java
package com.patra.{service}.app.coordinator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlanPersistenceCoordinator 持久化协调器测试")
class PlanPersistenceCoordinatorTest {

    @Mock
    private PlanPort planPort;

    @Mock
    private SlicePort slicePort;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private PlanPersistenceCoordinator coordinator;

    @Test
    @DisplayName("应该保存计划及其切片")
    void shouldSavePlanWithSlices() {
        // Arrange
        var plan = createPlanWithSlices(3);
        when(planPort.save(any())).thenReturn(plan);
        when(slicePort.saveAll(anyList())).thenReturn(plan.getSlices());

        // Act
        var saved = coordinator.save(plan);

        // Assert
        assertThat(saved).isNotNull();
        verify(planPort).save(plan);
        verify(slicePort).saveAll(argThat(slices -> slices.size() == 3));
        verify(auditService).recordCreation(eq(plan.getId()), any());
    }

    @Test
    @DisplayName("应该处理空切片列表")
    void shouldHandleEmptySlices() {
        // Arrange
        var plan = createPlanWithSlices(0);
        when(planPort.save(any())).thenReturn(plan);

        // Act
        var saved = coordinator.save(plan);

        // Assert
        verify(slicePort, never()).saveAll(any());
        assertThat(saved.getSlices()).isEmpty();
    }

    @Test
    @DisplayName("应该在切片保存失败时回滚")
    void shouldRollbackOnSliceSaveFailure() {
        // Arrange
        var plan = createPlanWithSlices(3);
        when(planPort.save(any())).thenReturn(plan);
        when(slicePort.saveAll(anyList()))
            .thenThrow(new DataAccessException("Slice save failed"));

        // Act & Assert
        assertThatThrownBy(() -> coordinator.save(plan))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("Slice save failed");

        // 审计记录不应该创建
        verify(auditService, never()).recordCreation(any(), any());
    }
}
```

## Event Handler 测试 {#event-handler}

### 事件处理器集成测试模板

```java
package com.patra.{service}.app.eventhandler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    "spring.jpa.show-sql=true",
    "logging.level.org.springframework.transaction=DEBUG"
})
@DisplayName("TaskCompletedEventHandler 事件处理器测试")
class TaskCompletedEventHandlerTest {

    @Autowired
    private TaskCompletedEventHandler eventHandler;

    @Autowired
    private TaskPort taskPort;

    @Autowired
    private SlicePort slicePort;

    @Autowired
    private OutboxMessageRepository outboxRepo;

    @MockBean
    private ApplicationEventPublisher eventPublisher;

    @SpyBean
    private SliceStatusCalculator statusCalculator;

    @Test
    @DisplayName("应该处理任务完成事件并更新切片状态")
    void shouldHandleTaskCompletionAndUpdateSliceStatus() {
        // Arrange
        var slice = createSliceWithTasks(3);
        var event = new TaskCompletedEvent(
            slice.getTasks().get(0).getId(),
            slice.getId(),
            "dedup-task-001"
        );

        // Act
        eventHandler.onTaskCompleted(event);

        // Assert
        // 1. 验证任务状态更新
        var updatedTask = taskPort.findById(event.getTaskId());
        assertThat(updatedTask.getStatus()).isEqualTo(TaskStatus.COMPLETED);

        // 2. 验证切片状态计算
        verify(statusCalculator).calculateSliceStatus(any());

        // 3. 验证后续事件发布
        verify(eventPublisher).publishEvent(argThat(e ->
            e instanceof SliceStatusChangedEvent &&
            ((SliceStatusChangedEvent) e).getSliceId().equals(slice.getId())
        ));
    }

    @Test
    @DisplayName("应该保证幂等性（重复事件不处理）")
    void shouldEnsureIdempotency() {
        // Arrange
        String dedupKey = "duplicate-event-123";

        // 预先创建 Outbox 记录
        outboxRepo.save(OutboxMessage.builder()
            .dedupKey(dedupKey)
            .opType("TaskCompleted")
            .statusCode("PROCESSED")
            .build());

        var event = new TaskCompletedEvent(1L, 1L, dedupKey);

        // Act
        eventHandler.onTaskCompleted(event);

        // Assert
        // 不应该处理重复事件
        verify(eventPublisher, never()).publishEvent(any());
        verify(statusCalculator, never()).calculateSliceStatus(any());
    }

    @Test
    @DisplayName("应该处理乐观锁冲突")
    void shouldHandleOptimisticLockingConflict() {
        // Arrange
        var slice = createSliceWithTasks(3);
        var event = new TaskCompletedEvent(
            slice.getTasks().get(0).getId(),
            slice.getId(),
            "dedup-concurrent"
        );

        // 模拟并发修改导致版本号不匹配
        doThrow(new OptimisticLockingFailureException("Version mismatch"))
            .doCallRealMethod()  // 第二次调用成功
            .when(slicePort).update(any());

        // Act
        eventHandler.onTaskCompleted(event);

        // Assert
        // 验证重试机制
        verify(slicePort, times(2)).update(any());

        // 最终应该成功
        verify(eventPublisher).publishEvent(any(SliceStatusChangedEvent.class));
    }

    @Test
    @DisplayName("应该在 AFTER_COMMIT 阶段发布事件")
    void shouldPublishEventAfterCommit() {
        // Arrange
        var event = new TaskCompletedEvent(1L, 1L, "after-commit-test");

        // Act
        eventHandler.onTaskCompleted(event);

        // Assert
        // 通过 @TransactionalEventListener(phase = AFTER_COMMIT) 确保
        // 事件只在事务提交后发布
        verify(eventPublisher).publishEvent(argThat(e -> {
            // 这个断言会在事务提交后执行
            var outbox = outboxRepo.findByDedupKey("after-commit-test");
            return outbox.isPresent() &&
                   outbox.get().getStatusCode().equals("PUBLISHED");
        }));
    }

    @Test
    @DisplayName("应该触发事件链")
    void shouldTriggerEventChain() {
        // Arrange
        var plan = createPlanWithSlices();
        var lastSlice = plan.getSlices().get(plan.getSlices().size() - 1);

        // 将所有任务标记为完成，触发链式反应
        markAllTasksCompleted(lastSlice);

        var event = new TaskCompletedEvent(
            lastSlice.getTasks().get(0).getId(),
            lastSlice.getId(),
            "chain-trigger"
        );

        // Act
        eventHandler.onTaskCompleted(event);

        // Assert - 验证事件链
        // Task → Slice → Plan
        verify(eventPublisher).publishEvent(any(SliceStatusChangedEvent.class));

        // 由于这是最后一个切片完成，应该触发计划完成事件
        verify(eventPublisher).publishEvent(argThat(e ->
            e instanceof PlanCompletedEvent &&
            ((PlanCompletedEvent) e).getPlanId().equals(plan.getId())
        ));
    }
}
```

## 事件发布测试

```java
@Test
@DisplayName("应该使用 ApplicationEventPublisher 发布领域事件")
void shouldPublishDomainEvents() {
    // Arrange
    var event = new PlanCreatedEvent(planId, provenanceCode);

    // Act
    orchestrator.createPlan(command);

    // Assert
    ArgumentCaptor<PlanCreatedEvent> captor =
        ArgumentCaptor.forClass(PlanCreatedEvent.class);

    verify(eventPublisher).publishEvent(captor.capture());

    PlanCreatedEvent published = captor.getValue();
    assertThat(published.getPlanId()).isEqualTo(planId);
    assertThat(published.getProvenanceCode()).isEqualTo(provenanceCode);
    assertThat(published.getTimestamp()).isNotNull();
}
```

## Mock 最佳实践

### 1. 使用 @Mock vs @MockBean

```java
// 单元测试用 @Mock
@ExtendWith(MockitoExtension.class)
class UnitTest {
    @Mock private Service service;
}

// 集成测试用 @MockBean
@SpringBootTest
class IntegrationTest {
    @MockBean private ExternalService service;
}
```

### 2. ArgumentCaptor 使用

```java
@Test
void shouldCaptureArguments() {
    // Arrange
    ArgumentCaptor<Plan> planCaptor = ArgumentCaptor.forClass(Plan.class);

    // Act
    service.process(plan);

    // Assert
    verify(repository).save(planCaptor.capture());
    Plan captured = planCaptor.getValue();
    assertThat(captured.getStatus()).isEqualTo(PROCESSED);
}
```

### 3. Answer 用于复杂 Mock 行为

```java
when(service.process(any())).thenAnswer(invocation -> {
    Plan plan = invocation.getArgument(0);
    plan.setProcessedAt(Instant.now());
    return plan;
});
```

### 4. Spy 用于部分 Mock

```java
@SpyBean
private Calculator calculator;

@Test
void shouldUseRealMethodsExceptMocked() {
    // Mock 特定方法
    doReturn(100).when(calculator).complexCalculation();

    // 其他方法使用真实实现
    var result = calculator.simpleCalculation();
}
```