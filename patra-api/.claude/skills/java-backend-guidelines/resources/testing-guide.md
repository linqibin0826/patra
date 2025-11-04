# 测试指南（单元测试、集成测试、ArchUnit）

六边形架构 + DDD 与 Spring Boot 的完整测试参考。教授 AI 和开发者如何识别代码模式并生成正确的测试。

---

## 目录

1. [Overview & Testing Pyramid](#overview--testing-pyramid)
2. [Testing Pattern Recognition](#testing-pattern-recognition) ⭐
3. [Domain Layer Testing](#domain-layer-testing)
4. [Application Layer Testing](#application-layer-testing)
   - [Orchestrator Testing](#orchestrator-testing)
   - [Coordinator Testing](#coordinator-testing) ⭐
   - [Event Handler Testing](#event-handler-testing) ⭐
5. [Infrastructure Layer Testing](#infrastructure-layer-testing)
   - [Repository Testing](#repository-testing)
   - [Converter Testing](#converter-testing) ⭐
6. [Adapter Layer Testing](#adapter-layer-testing)
   - [REST Controller Testing](#rest-controller-testing)
   - [XXL-Job Testing](#xxl-job-testing) ⭐
7. [Integration Testing Patterns](#integration-testing-patterns) ⭐
   - [Event Chain Testing](#event-chain-testing)
   - [Outbox Pattern Testing](#outbox-pattern-testing)
   - [Transaction Boundary Testing](#transaction-boundary-testing)
   - [Concurrency & Optimistic Locking Testing](#concurrency--optimistic-locking-testing)
8. [Test Data Management](#test-data-management) ⭐
9. [Test Coverage Strategy](#test-coverage-strategy) ⭐
10. [ArchUnit Tests](#archunit-tests)
11. [Testing Best Practices](#testing-best-practices)
12. [Testing Checklists & Quick Reference](#testing-checklists--quick-reference) ⭐

---

## 测试命名规范

### 测试类命名
- **单元测试**: `{被测试类名}Test.java`
  - 示例: `ProvenanceTest.java`, `PlanAggregateTest.java`
- **集成测试**: `{功能描述}IntegrationTest.java` 或 `{功能描述}IT.java`
  - 示例: `EventChainIntegrationTest.java`, `OutboxPatternIT.java`

### 测试方法命名规则

**格式**: `should{预期行为}When{前置条件}` 或 `should{预期行为}`

**规则**:
- ✅ **有前置条件时**使用 When: `shouldThrowExceptionWhenCodeIsNull()`
- ✅ **无特殊条件时**可省略 When: `shouldCreateProvenanceWithValidFields()`
- ✅ **所有测试方法**都必须添加 `@DisplayName` 注解，提供**中文描述**

**示例**:
```java
@Test
@DisplayName("应该使用有效字段创建 Provenance")
void shouldCreateProvenanceWithValidFields() { }

@Test
@DisplayName("当 code 为 null 时应该抛出异常")
void shouldThrowExceptionWhenCodeIsNull() { }

@Test
@DisplayName("应该拒绝空的 code")
void shouldRejectNullCode() { }
```

### 测试类级别描述

给测试类也添加类级别 `@DisplayName`，用中文说明测试的主体：

```java
@DisplayName("Provenance 值对象测试")
class ProvenanceTest { }

@DisplayName("Plan 聚合根测试")
class PlanAggregateTest { }

@DisplayName("Plan 数据持久化协调器测试")
class PlanPersistenceCoordinatorTest { }
```

---

## 测试金字塔概览

Papertrace 项目采用**测试金字塔**方法论：包括大量快速的单元测试、较少的集成测试和最少的端到端测试。测试按层组织（领域层、应用层、基础设施层、适配器层），与六边形架构相匹配。

**核心原则**: 使用适当的测试替身（Mocks、Stubs、TestContainers）对每一层进行隔离测试。

### 测试 Pyramid

```
         /\
        /  \      E2E Tests (few, slow)
       /----\     - Full system scenarios
      /      \    - Critical user journeys
     /--------\   Integration Tests (moderate)
    /          \  - Database interactions
   /------------\ - External API calls
  /______________\ Unit Tests (many, fast)
                   - Domain logic
                   - Business rules
                   - Pure Java
```

**Target Distribution**:
- **70% Unit Tests**: Domain + Application layer logic
- **25% Integration Tests**: Infrastructure + Database
- **5% E2E Tests**: Critical workflows only

### Test Organization

```
patra-{service}/
├── patra-{service}-domain/
│   └── src/test/java/                    # Domain unit tests
│       └── com/patra/{service}/domain/
│           ├── model/
│           │   ├── ProvenanceTest.java
│           │   └── PlanAggregateTest.java
│           └── vo/
│               └── WindowSpecTest.java
│
├── patra-{service}-app/
│   └── src/test/java/                    # Application unit tests
│       └── com/patra/{service}/app/
│           ├── orchestrator/
│           │   └── PlanIngestionOrchestratorTest.java
│           ├── coordinator/
│           │   └── PlanPersistenceCoordinatorTest.java
│           └── eventhandler/
│               └── TaskCompletedEventHandlerTest.java
│
├── patra-{service}-infra/
│   └── src/test/java/                    # Infrastructure unit tests
│       └── com/patra/{service}/infra/
│           └── converter/
│               └── PlanConverterTest.java
│
├── patra-{service}-adapter/
│   └── src/test/java/                    # Adapter unit tests
│       └── com/patra/{service}/adapter/
│           ├── rest/
│           │   └── ProvenanceControllerTest.java
│           └── job/
│               └── PubmedHarvestJobTest.java
│
└── patra-{service}-boot/
    └── src/test/java/                    # Integration tests
        └── com/patra/{service}/
            ├── integration/              # Database + Event integration
            │   ├── EventChainIntegrationTest.java
            │   └── OutboxPatternIntegrationTest.java
            └── architecture/             # ArchUnit tests
                └── ArchitectureTest.java
```

---

## 测试模式识别

**目的**: 帮助开发者识别代码模式并应用正确的测试策略。

### 代码模式 → 测试策略映射

当你看到特定的代码模式时，立即知道应该使用哪种测试方法：

| 代码特性 | 使用场景 | 测试策略 | 关键验证点 | 参考 |
|---------|---------|----------|-----------|------|
| `@TransactionalEventListener(phase = AFTER_COMMIT)` | 事件处理器 | 集成测试 + Mock 发布者 | 事件发布、幂等性检查、乐观锁处理 | [§4.3](#event-handler-testing) |
| `Orchestrator + 多个 Coordinator` | 用例编排 | 分层 Mock | 调用顺序 (InOrder)、异常包装 | [§4.1](#orchestrator-testing), [§4.2](#coordinator-testing) |
| `OutboxMessage.builder()...save()` | Outbox 模式 | 集成测试 + TestContainers | 业务数据原子性、dedupKey 唯一性 | [§7.2](#outbox-pattern-testing) |
| `@XxlJob` 注解 | 定时任务 | Mock XxlJobHelper | 参数解析、任务执行流程 | [§6.2](#xxl-job-testing) |
| `@RestController` + `@Valid` | REST 端点 | @WebMvcTest + MockMvc | 验证失败响应、ProblemDetail | [§6.1](#rest-controller-testing) |
| `MapStruct` 转换器 | DO ↔ 领域模型映射 | 单元测试 | 双向映射、空值处理 | [§5.2](#converter-testing) |
| `@Transactional` | 事务边界 | 集成测试 | 回滚场景、REQUIRES_NEW 传播 | [§7.3](#transaction-boundary-testing) |
| 聚合根带 `version` 字段 | 乐观锁 | 集成测试 | 并发更新冲突 | [§7.4](#concurrency--optimistic-locking-testing) |

### 决策树：选择测试类型

```
问题1: 代码是否有 Spring 依赖 (@Service, @Transactional, @Component)?
├─ 否 → 纯单元测试（领域层）
│   - 无需 Mock
│   - 快速执行 (<100ms)
│   - 仅测试业务规则
│
└─ 是 → 问题2: 是否需要数据库？
    ├─ 否 → Spring Mock 测试（应用层）
    │   - Mock 所有 Port
    │   - 使用 @ExtendWith(MockitoExtension.class)
    │   - 验证编排逻辑
    │
    └─ 是 → 集成测试（集成层）
        - 使用 @SpringBootTest
        - 使用 TestContainers 获取真实数据库
        - 测试完整工作流

问题3: 代码是否发布或处理领域事件?
├─ 否 → 按上述标准测试
│
└─ 是 → 需要事件驱动测试
    - 验证事件发布 (Mock ApplicationEventPublisher)
    - 测试幂等性 (Outbox 中的 dedupKey 检查)
    - 测试乐观锁冲突处理
    - 测试 AFTER_COMMIT 行为

问题4: 代码是否使用 Outbox 模式?
├─ 否 → 标准测试
│
└─ 是 → 需要 Outbox 测试
    - 验证业务数据 + Outbox 消息原子性
    - 测试 dedupKey 唯一性约束
    - 测试中继机制 (租约、发布、重试)
```

### 决策树：测试范围

根据代码中的这些关键特性来确定要测试的内容：

```
代码是否有 @Transactional?
├─ 是 → 必须测试事务回滚场景
└─ 否 → 跳过事务测试

代码是否有 @TransactionalEventListener?
├─ 是 → 必须测试：
│       - AFTER_COMMIT vs BEFORE_COMMIT 行为
│       - 事件发布验证
│       - 幂等性（dedupKey 检查）
│       - OptimisticLockingFailureException 处理
└─ 否 → 跳过事件测试

代码是否创建 OutboxMessage?
├─ 是 → 必须测试与业务表的原子性
└─ 否 → 跳过 Outbox 测试

代码是否有乐观锁字段 (version)?
├─ 是 → 必须测试并发更新冲突
└─ 否 → 跳过并发测试

代码是否有 @Valid 参数验证?
├─ 是 → 必须测试验证失败的响应
└─ 否 → 跳过验证测试

代码是否调用多个 Coordinator?
├─ 是 → 必须用 InOrder 验证调用顺序
└─ 否 → 跳过顺序验证
```

---

## 领域层测试

**目的**: 隔离测试**业务逻辑**和**业务规则**。无需 Spring、数据库或 Mock。

### 特性

- ✅ 纯 Java 测试
- ✅ 快速执行 (<100ms 每个测试)
- ✅ 无外部依赖
- ✅ 测试领域不变量和业务规则
- ✅ 无需 Mock（纯函数）

### Example 1: Testing a Value Object (Record)

```java
package com.patra.registry.domain.model.vo.provenance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Provenance 值对象测试")
class ProvenanceTest {

    @Test
    @DisplayName("应该使用有效字段创建 Provenance")
    void shouldCreateProvenanceWithValidFields() {
        // 准备 & 执行
        Provenance provenance = new Provenance(
            1L,
            "PUBMED",
            "PubMed",
            "https://pubmed.ncbi.nlm.nih.gov",
            "UTC",
            "https://www.ncbi.nlm.nih.gov/books/NBK3827/",
            true,
            "ACTIVE"
        );

        // 断言
        assertThat(provenance.id()).isEqualTo(1L);
        assertThat(provenance.code()).isEqualTo("PUBMED");
        assertThat(provenance.name()).isEqualTo("PubMed");
        assertThat(provenance.baseUrlDefault()).isEqualTo("https://pubmed.ncbi.nlm.nih.gov");
        assertThat(provenance.active()).isTrue();
        assertThat(provenance.isActive()).isTrue();
    }

    @Test
    @DisplayName("当 code 为 null 时应该抛出异常")
    void shouldRejectNullCode() {
        // 执行 & 断言
        assertThatThrownBy(() -> new Provenance(
            1L, null, "Name", "https://url.com", "UTC", "https://docs.com", true, "ACTIVE"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("code cannot be null or blank");
    }

    @Test
    @DisplayName("应该修剪可选字段的空白字符")
    void shouldTrimWhitespaceFromOptionalFields() {
        // 准备 & 执行
        Provenance provenance = new Provenance(
            1L,
            "PUBMED",
            "PubMed",
            "  https://pubmed.ncbi.nlm.nih.gov  ",  // 带有空格
            "UTC",
            "  https://docs.com  ",  // 带有空格
            true,
            "ACTIVE"
        );

        // 断言: whitespace trimmed
        assertThat(provenance.baseUrlDefault()).isEqualTo("https://pubmed.ncbi.nlm.nih.gov");
        assertThat(provenance.docsUrl()).isEqualTo("https://docs.com");
    }
}
```

### Example 2: Testing Sealed Interface (WindowSpec)

```java
package com.patra.ingest.domain.model.vo.plan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@DisplayName("WindowSpec 密封接口测试")
class WindowSpecTest {

    @Test
    @DisplayName("应该使用有效时间戳创建 TIME 窗口")
    void shouldCreateTimeWindow() {
        // 准备
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to = Instant.parse("2024-01-31T23:59:59Z");

        // 执行
        WindowSpec.Time window = WindowSpec.ofTime(from, to);

        // 断言
        assertThat(window.from()).isEqualTo(from);
        assertThat(window.to()).isEqualTo(to);
        assertThat(window.strategy().getCode()).isEqualTo("TIME");
    }

    @Test
    @DisplayName("当 from 在 to 之后时应该拒绝 TIME 窗口")
    void shouldRejectInvalidTimeWindow() {
        // 准备
        Instant from = Instant.parse("2024-12-31T00:00:00Z");
        Instant to = Instant.parse("2024-01-01T00:00:00Z");

        // 执行 & 断言
        assertThatThrownBy(() -> WindowSpec.ofTime(from, to))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("from must be before or equal to to");
    }

    @Test
    @DisplayName("应该将 TIME 窗口转换为 Map")
    void shouldConvertTimeWindowToMap() {
        // 准备
        WindowSpec.Time window = WindowSpec.ofTime(
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-01-31T23:59:59Z")
        );

        // 执行
        var map = window.toMap();

        // 断言
        assertThat(map).containsKey("strategy");
        assertThat(map).containsKey("window");
        assertThat(map.get("strategy")).isEqualTo("TIME");
    }
}
```

### Example 3: Testing Aggregate Root

```java
package com.patra.ingest.domain.model.aggregate;

import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.vo.plan.WindowSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Plan 聚合根测试")
class PlanAggregateTest {

    @Test
    @DisplayName("应该使用 DRAFT 状态创建 Plan 聚合根")
    void shouldCreatePlanWithDraftStatus() {
        // 准备
        WindowSpec windowSpec = WindowSpec.ofTime(
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-01-31T23:59:59Z")
        );

        // 执行
        PlanAggregate plan = PlanAggregate.create(
            100L,                              // 调度实例 ID
            "PUBMED:HARVEST:1704067200000",    // 计划键
            "PUBMED",                          // 数据源代码
            "HARVEST",                         // 操作代码
            "expr-hash-123",                   // 表达式原型哈希
            "{\"fields\":[]}",                 // 表达式原型快照 JSON
            "{\"windowOffset\":{}}",           // 数据源配置快照 JSON
            "config-hash-456",                 // 数据源配置哈希
            windowSpec,                        // 窗口规范
            "TIME",                            // 切片策略代码
            "{}"                               // 切片参数 JSON
        );

        // 断言
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.DRAFT);
        assertThat(plan.getProvenanceCode()).isEqualTo("PUBMED");
        assertThat(plan.getPlanKey()).isEqualTo("PUBMED:HARVEST:1704067200000");
    }

    @Test
    @DisplayName("应该从 DRAFT 转换到 SLICING")
    void shouldTransitionFromDraftToSlicing() {
        // 准备
        PlanAggregate plan = createTestPlan();
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.DRAFT);

        // 执行
        plan.startSlicing();

        // 断言
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.SLICING);
    }

    @Test
    @DisplayName("应该将 Plan 标记为 READY")
    void shouldMarkPlanAsReady() {
        // 准备
        PlanAggregate plan = createTestPlan();
        plan.startSlicing();

        // 执行
        plan.markReady();

        // 断言
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.READY);
    }

    private PlanAggregate createTestPlan() {
        return PlanAggregate.create(
            100L,
            "TEST:HARVEST:12345",
            "PUBMED",
            "HARVEST",
            "hash123",
            "{}",
            "{}",
            "confighash",
            WindowSpec.ofSingle(),
            "SINGLE",
            "{}"
        );
    }
}
```

### Example 4: Testing Domain Service

```java
package com.patra.ingest.domain.service;

import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.model.enums.SliceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Slice 状态计算器测试")
class SliceStatusCalculatorTest {

    private final SliceStatusCalculator calculator = new SliceStatusCalculator();

    @Test
    @DisplayName("当所有任务完成时应该计算为 COMPLETED")
    void shouldCalculateCompletedWhenAllTasksCompleted() {
        // 准备: All tasks are COMPLETED
        List<TaskStatus> taskStatuses = List.of(
            TaskStatus.COMPLETED,
            TaskStatus.COMPLETED,
            TaskStatus.COMPLETED
        );

        // 执行
        SliceStatus result = calculator.calculate(taskStatuses);

        // 断言
        assertThat(result).isEqualTo(SliceStatus.COMPLETED);
    }

    @Test
    @DisplayName("当任何任务失败时应该计算为 FAILED")
    void shouldCalculateFailedWhenAnyTaskFailed() {
        // 准备: One task FAILED
        List<TaskStatus> taskStatuses = List.of(
            TaskStatus.COMPLETED,
            TaskStatus.FAILED,
            TaskStatus.IN_PROGRESS
        );

        // 执行
        SliceStatus result = calculator.calculate(taskStatuses);

        // 断言
        assertThat(result).isEqualTo(SliceStatus.FAILED);
    }

    @Test
    @DisplayName("当某些任务运行时应该计算为 IN_PROGRESS")
    void shouldCalculateInProgressWhenSomeTasksRunning() {
        // 准备
        List<TaskStatus> taskStatuses = List.of(
            TaskStatus.COMPLETED,
            TaskStatus.IN_PROGRESS,
            TaskStatus.PENDING
        );

        // 执行
        SliceStatus result = calculator.calculate(taskStatuses);

        // 断言
        assertThat(result).isEqualTo(SliceStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("应该处理空的任务列表")
    void shouldHandleEmptyTaskList() {
        // 执行 & 断言
        assertThatThrownBy(() -> calculator.calculate(List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Task list cannot be empty");
    }
}
```

---

## 应用层测试

**目的**: 无需数据库或外部依赖地测试**编排逻辑**。

### 特性

- ✅ Mock 领域 Port（接口）
- ✅ 测试协调流程
- ✅ 验证 Port 交互
- ❌ 无数据库（使用 Mock）

### 依赖项

```xml
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

---

### Orchestrator 测试

通过 Mock 所有协调器和 Port 来测试**用例编排**。

#### 示例：测试 Orchestrator

```java
package com.patra.ingest.app.orchestrator.plan;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.port.PatraRegistryPort;
import com.patra.ingest.app.coordinator.PlanPersistenceCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Plan 摄入 Orchestrator 测试")
class PlanIngestionOrchestratorTest {

    @Mock
    private PatraRegistryPort registryPort;

    @Mock
    private PlanPersistenceCoordinator persistenceCoordinator;

    @Mock
    private EventPublishingCoordinator eventCoordinator;

    @InjectMocks
    private PlanIngestionOrchestrator orchestrator;

    @Test
    @DisplayName("应该成功编排 Plan 摄入")
    void shouldOrchestrateSuccessfully() {
        // 准备
        String provenanceCode = "PUBMED";
        String operationCode = "HARVEST";
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to = Instant.parse("2024-01-31T23:59:59Z");

        // Mock 仓储响应
        when(registryPort.fetchConfigSnapshot(provenanceCode, operationCode))
            .thenReturn("{\"windowOffset\":{\"mode\":\"SLIDING\"}}");
        when(registryPort.fetchExprSnapshot(provenanceCode, operationCode, from))
            .thenReturn("{\"fields\":[{\"key\":\"publication_date\"}]}");

        // 执行
        PlanIngestionContext context = new PlanIngestionContext(
            provenanceCode,
            operationCode,
            from,
            to
        );
        orchestrator.ingestPlan(context);

        // 断言: Verify coordinator call order
        InOrder inOrder = inOrder(persistenceCoordinator, eventCoordinator);
        inOrder.verify(persistenceCoordinator).persistPlan(any(PlanAggregate.class));
        inOrder.verify(eventCoordinator).publishPlanCreatedEvent(any(PlanAggregate.class));
    }

    @Test
    @DisplayName("当持久化失败时应该回滚")
    void shouldRollbackOnPersistenceFailure() {
        // 准备
        when(registryPort.fetchConfigSnapshot(anyString(), anyString()))
            .thenReturn("{}");
        when(registryPort.fetchExprSnapshot(anyString(), anyString(), any()))
            .thenReturn("{}");

        // 模拟 persistence failure
        doThrow(new RuntimeException("DB error"))
            .when(persistenceCoordinator).persistPlan(any());

        // 执行 & 断言
        PlanIngestionContext context = new PlanIngestionContext(
            "PUBMED", "HARVEST", Instant.now(), Instant.now()
        );

        assertThatThrownBy(() -> orchestrator.ingestPlan(context))
            .isInstanceOf(RuntimeException.class);

        // 验证 event coordinator never called (transaction rollback)
        verify(eventCoordinator, never()).publishPlanCreatedEvent(any());
    }
}
```

---

### Coordinator 测试

在与编排器隔离的情况下测试**关注点特定的协调**。

#### 目的

Coordinator 处理单一关注点（持久化、事件发布、验证）。使用 Mock 依赖独立测试它们。

#### Example: Testing Persistence Coordinator

```java
package com.patra.ingest.app.coordinator;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.SliceAggregate;
import com.patra.ingest.domain.port.PlanRepositoryPort;
import com.patra.ingest.domain.port.SliceRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessException;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Plan 数据持久化协调器测试")
class PlanPersistenceCoordinatorTest {

    @Mock
    private PlanRepositoryPort planRepo;

    @Mock
    private SliceRepositoryPort sliceRepo;

    @InjectMocks
    private PlanPersistenceCoordinator coordinator;

    @Test
    @DisplayName("应该以原子方式持久化 Plan 和所有 Slice")
    void shouldPersistPlanWithSlices() {
        // 准备: Prepare Plan + Slices
        PlanAggregate plan = PlanTestBuilder.aDefaultPlan()
            .withId(1L)
            .build();

        List<SliceAggregate> slices = List.of(
            SliceTestBuilder.aSlice().withId(1L).withPlanId(1L).build(),
            SliceTestBuilder.aSlice().withId(2L).withPlanId(1L).build()
        );

        when(planRepo.save(any())).thenReturn(plan);

        // 执行
        coordinator.persistPlanWithSlices(plan, slices);

        // 断言: Verify call order (plan first, then slices)
        InOrder inOrder = inOrder(planRepo, sliceRepo);
        inOrder.verify(planRepo).save(plan);
        inOrder.verify(sliceRepo).batchSave(slices);
    }

    @Test
    @DisplayName("应该包装基础设施异常")
    void shouldWrapInfrastructureExceptions() {
        // 准备
        PlanAggregate plan = PlanTestBuilder.aDefaultPlan().build();
        when(planRepo.save(any()))
            .thenThrow(new DataAccessException("DB connection lost") {});

        // 执行 & 断言: Verify exception wrapping
        assertThatThrownBy(() -> coordinator.persistPlanWithSlices(plan, List.of()))
            .isInstanceOf(PlanPersistenceException.class)
            .hasMessageContaining("Failed to persist plan")
            .hasCauseInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("当列表为空时应该跳过 Slice 持久化")
    void shouldSkipSlicePersistenceWhenEmpty() {
        // 准备
        PlanAggregate plan = PlanTestBuilder.aDefaultPlan().build();
        when(planRepo.save(any())).thenReturn(plan);

        // 执行
        coordinator.persistPlanWithSlices(plan, List.of());

        // 断言: sliceRepo never called
        verify(planRepo).save(plan);
        verifyNoInteractions(sliceRepo);
    }
}
```

**Key Testing Points for Coordinators**:
- Mock all dependencies (ports, other coordinators)
- Verify call order with `InOrder`
- Test exception wrapping (infrastructure → domain exceptions)
- Test edge cases (empty lists, null handling)

---

### 事件处理器测试

使用 Spring 上下文和事件发布验证测试**领域事件处理器**。

#### 目的

事件处理器（@TransactionalEventListener）对领域事件做出反应。它们需要特殊的测试考虑：
- AFTER_COMMIT 阶段验证
- 幂等性检查
- 乐观锁冲突处理
- 事件链传播

#### Example 1: Testing Event Publishing and Idempotency

```java
package com.patra.ingest.app.eventhandler;

import com.patra.ingest.domain.event.TaskCompletedEvent;
import com.patra.ingest.domain.event.SliceStatusChangedEvent;
import com.patra.ingest.domain.model.aggregate.SliceAggregate;
import com.patra.ingest.domain.port.SliceRepositoryPort;
import com.patra.ingest.infra.outbox.OutboxMessage;
import com.patra.ingest.infra.outbox.OutboxMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
@DisplayName("任务完成事件处理器测试")
class TaskCompletedEventHandlerTest {

    @Autowired
    private TaskCompletedEventHandler eventHandler;

    @Autowired
    private SliceRepositoryPort sliceRepo;

    @Autowired
    private OutboxMessageRepository outboxRepo;

    @MockBean
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("应该更新 Slice 状态并在事务提交后发布事件")
    void shouldUpdateSliceAndPublishEvent() {
        // 准备: Prepare completed task event
        Long taskId = 1L;
        Long sliceId = 100L;
        String dedupKey = "task-completed-1";

        // 准备现有的 Slice
        SliceAggregate slice = SliceTestBuilder.aSlice()
            .withId(sliceId)
            .withStatus(SliceStatus.IN_PROGRESS)
            .build();
        sliceRepo.save(slice);

        TaskCompletedEvent event = new TaskCompletedEvent(
            taskId, sliceId, TaskStatus.COMPLETED, dedupKey, Instant.now()
        );

        // 执行: Handle event
        eventHandler.onTaskCompleted(event);

        // 断言 1: Verify Slice status updated
        SliceAggregate updated = sliceRepo.findById(sliceId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SliceStatus.COMPLETED);

        // 断言 2: Verify event published (AFTER_COMMIT phase)
        verify(eventPublisher).publishEvent(argThat(e ->
            e instanceof SliceStatusChangedEvent &&
            ((SliceStatusChangedEvent) e).sliceId().equals(sliceId)
        ));

        // 断言 3: Verify idempotency record (Outbox table)
        OutboxMessage outbox = outboxRepo.findByDedupKey(dedupKey).orElseThrow();
        assertThat(outbox.getEventType()).isEqualTo("SliceStatusChangedEvent");
        assertThat(outbox.getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("应该不处理重复事件（幂等性保证）")
    void shouldNotProcessDuplicateEvent() {
        // 准备: Prepare duplicate event (dedupKey already exists)
        String dedupKey = "task-completed-duplicate";
        outboxRepo.save(OutboxMessage.builder()
            .dedupKey(dedupKey)
            .eventType("SliceStatusChangedEvent")
            .status("PUBLISHED")
            .build());

        TaskCompletedEvent event = new TaskCompletedEvent(
            1L, 100L, TaskStatus.COMPLETED, dedupKey, Instant.now()
        );

        // 执行
        eventHandler.onTaskCompleted(event);

        // 断言: Verify business logic NOT executed (idempotency protection)
        verify(eventPublisher, never()).publishEvent(any());
        verifyNoMoreInteractions(sliceRepo);
    }

    @Test
    @DisplayName("应该妥善处理乐观锁冲突异常")
    void shouldHandleOptimisticLockConflict() {
        // 准备: Simulate concurrent update causing optimistic lock conflict
        Long sliceId = 100L;
        String dedupKey = "task-completed-conflict";

        SliceAggregate slice = SliceTestBuilder.aSlice()
            .withId(sliceId)
            .withVersion(1L)
            .build();
        sliceRepo.save(slice);

        TaskCompletedEvent event = new TaskCompletedEvent(
            1L, sliceId, TaskStatus.COMPLETED, dedupKey, Instant.now()
        );

        // 模拟 version conflict (another transaction updated the slice)
        doThrow(new OptimisticLockingFailureException("Version conflict"))
            .when(sliceRepo).save(any());

        // 执行 & 断言: Verify exception handling
        assertThatThrownBy(() -> eventHandler.onTaskCompleted(event))
            .isInstanceOf(OptimisticLockingFailureException.class);

        // 验证 no subsequent event published (transaction rolled back)
        verify(eventPublisher, never()).publishEvent(any());
    }
}
```

#### Example 2: Testing Event Chain Propagation

```java
@SpringBootTest
@Transactional
class SliceStatusChangedEventHandlerTest {

    @Autowired
    private SliceStatusChangedEventHandler eventHandler;

    @Autowired
    private PlanRepositoryPort planRepo;

    @MockBean
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("Should propagate slice status to plan aggregate")
    void shouldPropagateSliceStatusToPlan() {
        // 准备: Prepare Plan with multiple Slices
        Long planId = 1L;
        PlanAggregate plan = PlanTestBuilder.aDefaultPlan()
            .withId(planId)
            .withStatus(PlanStatus.IN_PROGRESS)
            .build();
        planRepo.save(plan);

        // 所有 Slice 已完成
        SliceStatusChangedEvent event = new SliceStatusChangedEvent(
            100L, planId, SliceStatus.COMPLETED, "dedupKey-100", Instant.now()
        );

        // 执行
        eventHandler.onSliceStatusChanged(event);

        // 断言: Plan status updated to COMPLETED (if all slices completed)
        PlanAggregate updated = planRepo.findById(planId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PlanStatus.COMPLETED);

        // 验证 PlanStatusChangedEvent published
        verify(eventPublisher).publishEvent(argThat(e ->
            e instanceof PlanStatusChangedEvent &&
            ((PlanStatusChangedEvent) e).planId().equals(planId)
        ));
    }
}
```

**Event Handler 测试的关键点**:
- 使用 `@SpringBootTest`（需要 Spring 上下文来发布事件）
- Mock `ApplicationEventPublisher` 验证事件发布
- 测试幂等性：重复事件应被忽略
- 测试乐观锁冲突
- 测试事件链传播（Task → Slice → Plan）
- 验证 AFTER_COMMIT 行为（事务提交后发布事件）

---

## 基础设施层 Testing

**目的**: 使用真实数据库测试**数据库交互**和**Repository 实现**。

### 特性

- ✅ 真实数据库（Docker 中的 MySQL）
- ✅ 测试 SQL 查询和映射
- ✅ 验证 MyBatis-Plus 和 MapStruct
- ⚠️ 比单元测试慢（数秒）

### 依赖项

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
</dependency>
```

### TestContainers 配置最佳实践

**推荐配置**：
- MySQL 版本：使用项目生产环境的实际版本（当前项目为 8.0.36）
- 根用户凭证：root / 123456（与生产环境一致）
- 容器重用：`.withReuse(true)` 加速测试运行
- 驱动程序：`com.mysql.cj.jdbc.Driver`（MySQL 8.0+ 推荐）

---

### Repository 测试

使用 TestContainers（真实 MySQL 数据库）测试 Repository 实现。

#### Example: Repository Integration Test

```java
package com.patra.registry.integration;

import com.patra.registry.domain.model.vo.provenance.Provenance;
import com.patra.registry.infra.persistence.repository.ProvenanceRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@DisplayName("Provenance 数据持久化仓储集成测试")
class ProvenanceRepositoryIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("patra_test")
        .withUsername("root")
        .withPassword("123456")
        .withReuse(true);  // 跨测试重用容器以加快运行速度

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private ProvenanceRepositoryImpl repository;

    @Test
    @DisplayName("应该保存并查找 Provenance")
    void shouldSaveAndFindProvenance() {
        // 准备
        Provenance provenance = new Provenance(
            null,  // id will be auto-generated
            "PUBMED",
            "PubMed",
            "https://pubmed.ncbi.nlm.nih.gov",
            "UTC",
            "https://www.ncbi.nlm.nih.gov/books/NBK3827/",
            true,
            "ACTIVE"
        );

        // 执行
        Provenance saved = repository.save(provenance);
        Optional<Provenance> found = repository.findByCode("PUBMED");

        // 断言
        assertThat(saved.id()).isNotNull();
        assertThat(found).isPresent();
        assertThat(found.get().code()).isEqualTo("PUBMED");
        assertThat(found.get().name()).isEqualTo("PubMed");
        assertThat(found.get().active()).isTrue();
    }

    @Test
    @DisplayName("应该查找所有活跃的 Provenance")
    void shouldFindAllActiveProvenances() {
        // 准备
        repository.save(createProvenance("PUBMED", true));
        repository.save(createProvenance("EPMC", true));
        repository.save(createProvenance("CROSSREF", false));

        // 执行
        var activeProvenances = repository.findAllActive();

        // 断言
        assertThat(activeProvenances).hasSize(2);
        assertThat(activeProvenances)
            .extracting(Provenance::code)
            .containsExactlyInAnyOrder("PUBMED", "EPMC");
    }

    private Provenance createProvenance(String code, boolean active) {
        return new Provenance(
            null,
            code,
            code + " Name",
            "https://" + code.toLowerCase() + ".com",
            "UTC",
            "https://docs.com",
            active,
            active ? "ACTIVE" : "INACTIVE"
        );
    }
}
```

#### TestContainers 基础测试类

```java
package com.patra.registry.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("patra_test")
        .withUsername("root")
        .withPassword("123456")
        .withReuse(true);  // 跨测试重用容器以提高性能

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // 动态注入数据库连接配置
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
}
```

---

### 转换器测试

测试**MapStruct 转换器**用于 Domain ↔ DO（数据对象）的映射。

#### 目的

转换器在领域模型和数据库实体之间进行转换。测试双向映射的正确性、空字段处理和复杂映射。

#### Example: Testing MapStruct Converter

```java
package com.patra.registry.infra.converter;

import com.patra.registry.domain.model.vo.provenance.Provenance;
import com.patra.registry.infra.persistence.dataobject.ProvenanceDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Provenance MapStruct 转换器测试")
class ProvenanceConverterTest {

    private final ProvenanceConverter converter = ProvenanceConverter.INSTANCE;

    @Test
    @DisplayName("应该正确将领域模型转换为数据对象")
    void shouldConvertDomainToDO() {
        // 准备
        Provenance domain = new Provenance(
            1L, "PUBMED", "PubMed", "https://pubmed.gov",
            "UTC", "https://docs.com", true, "ACTIVE"
        );

        // 执行
        ProvenanceDO dataObject = converter.toDataObject(domain);

        // 断言: Verify all field mappings
        assertThat(dataObject.getId()).isEqualTo(1L);
        assertThat(dataObject.getCode()).isEqualTo("PUBMED");
        assertThat(dataObject.getName()).isEqualTo("PubMed");
        assertThat(dataObject.getBaseUrl()).isEqualTo("https://pubmed.gov");
        assertThat(dataObject.getActive()).isTrue();
    }

    @Test
    @DisplayName("应该妥善处理空字段")
    void shouldHandleNullFields() {
        // 准备: Optional fields are null
        Provenance domain = new Provenance(
            1L, "PUBMED", "PubMed", "https://pubmed.gov",
            null, null, true, "ACTIVE"  // timezone、docUrl 为 null
        );

        // 执行
        ProvenanceDO dataObject = converter.toDataObject(domain);

        // 断言: null fields correctly converted
        assertThat(dataObject.getTimezone()).isNull();
        assertThat(dataObject.getDocUrl()).isNull();
    }

    @Test
    @DisplayName("应该将数据对象转换回领域模型（双向转换）")
    void shouldConvertDOToDomain() {
        // 准备
        ProvenanceDO dataObject = new ProvenanceDO();
        dataObject.setId(1L);
        dataObject.setCode("PUBMED");
        dataObject.setName("PubMed");
        dataObject.setBaseUrl("https://pubmed.gov");
        dataObject.setActive(true);

        // 执行
        Provenance domain = converter.toDomain(dataObject);

        // 断言: Bidirectional mapping consistency
        assertThat(domain.id()).isEqualTo(1L);
        assertThat(domain.code()).isEqualTo("PUBMED");
        assertThat(domain.name()).isEqualTo("PubMed");
        assertThat(domain.active()).isTrue();
    }

    @Test
    @DisplayName("应该将数据对象列表转换为领域模型列表")
    void shouldConvertListOfDOs() {
        // 准备
        List<ProvenanceDO> dataObjects = List.of(
            createDO("PUBMED", "PubMed"),
            createDO("EPMC", "Europe PMC")
        );

        // 执行
        List<Provenance> domains = converter.toDomainList(dataObjects);

        // 断言
        assertThat(domains).hasSize(2);
        assertThat(domains)
            .extracting(Provenance::code)
            .containsExactly("PUBMED", "EPMC");
    }

    private ProvenanceDO createDO(String code, String name) {
        ProvenanceDO dataObject = new ProvenanceDO();
        dataObject.setCode(code);
        dataObject.setName(name);
        dataObject.setActive(true);
        return dataObject;
    }
}
```

**转换器测试的关键点**:
- 测试双向映射（Domain → DO → Domain）
- 测试空字段处理（可选字段）
- 测试列表转换
- 测试复杂嵌套对象
- 验证字段名称映射（尤其是名称不同的情况）

---

## 适配器层 Testing

**目的**: 在不使用完整应用上下文的情况下测试**适配器**（REST 控制器、定时任务）。

### 特性

- ✅ 测试 HTTP 端点或任务执行
- ✅ 验证请求/响应
- ✅ Mock 应用层
- ❌ 无数据库

---

### REST 控制器测试

使用 MockMvc 测试 REST 端点（无需启动完整应用）。

#### Example: Controller Test

```java
package com.patra.registry.adapter.rest;

import com.patra.registry.app.service.ProvenanceService;
import com.patra.registry.domain.model.vo.provenance.Provenance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProvenanceController.class)
@DisplayName("Provenance REST 控制器测试")
class ProvenanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProvenanceService provenanceService;

    @Test
    @DisplayName("应该通过代码获取 Provenance")
    void shouldGetProvenanceByCode() throws Exception {
        // 准备
        Provenance provenance = new Provenance(
            1L, "PUBMED", "PubMed",
            "https://pubmed.ncbi.nlm.nih.gov",
            "UTC", "https://docs.com", true, "ACTIVE"
        );
        when(provenanceService.findByCode("PUBMED")).thenReturn(provenance);

        // 执行 & 断言
        mockMvc.perform(get("/api/v1/provenances/PUBMED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("PUBMED"))
            .andExpect(jsonPath("$.name").value("PubMed"))
            .andExpect(jsonPath("$.active").value(true));

        verify(provenanceService).findByCode("PUBMED");
    }

    @Test
    @DisplayName("当 Provenance 不存在时应该返回 404")
    void shouldReturnNotFoundWhenProvenanceDoesNotExist() throws Exception {
        // 准备
        when(provenanceService.findByCode("UNKNOWN"))
            .thenThrow(new ProvenanceNotFoundException("UNKNOWN"));

        // 执行 & 断言
        mockMvc.perform(get("/api/v1/provenances/UNKNOWN"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.title").value("Provenance Not Found"));
    }

    @Test
    @DisplayName("应该使用 @Valid 验证请求体")
    void shouldValidateRequestBody() throws Exception {
        // 准备: Invalid JSON (code is null)
        String invalidJson = """
            {
                "name": "Test Provenance",
                "baseUrlDefault": "https://test.com"
            }
            """;

        // 执行 & 断言
        mockMvc.perform(post("/api/v1/provenances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.violations[0].field").value("code"))
            .andExpect(jsonPath("$.violations[0].message").value("must not be null"));
    }
}
```

**REST 控制器测试的关键点**:
- 使用 `@WebMvcTest` 进行控制器切片测试
- Mock 应用层服务
- 使用 `@Valid` 测试验证
- 验证 ProblemDetail 错误响应
- 测试不同 HTTP 状态码（200, 404, 400, 500）

---

### XXL-Job 测试

使用 Mock 的 XxlJobHelper 测试**定时任务**。

#### 目的

XXL-Job 方法需要特殊的测试：Mock XxlJobHelper 来处理参数解析和任务执行流程。

#### Example: Testing XXL-Job

```java
package com.patra.ingest.adapter.job;

import com.patra.ingest.app.orchestrator.PlanIngestionOrchestrator;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import com.xxl.job.core.biz.model.ReturnT;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PubMed 数据采集定时任务测试")
class PubmedHarvestJobTest {

    @Mock
    private PlanIngestionOrchestrator orchestrator;

    @InjectMocks
    private PubmedHarvestJob job;

    @Test
    @DisplayName("应该使用正确的参数执行任务")
    void shouldExecuteJobWithParameters() throws Exception {
        // 准备: Mock XxlJobHelper
        try (MockedStatic<XxlJobHelper> helper = mockStatic(XxlJobHelper.class)) {
            helper.when(XxlJobHelper::getJobParam).thenReturn("provenanceCode=PUBMED");

            // 执行
            ReturnT<String> result = job.harvestPubmedData("provenanceCode=PUBMED");

            // 断言
            assertThat(result.getCode()).isEqualTo(ReturnT.SUCCESS_CODE);
            assertThat(result.getMsg()).contains("Success");

            // 验证 orchestrator called with correct parameter
            verify(orchestrator).ingestFromProvenance("PUBMED");

            // 验证 job log
            helper.verify(() -> XxlJobHelper.log("Starting PUBMED harvest..."));
        }
    }

    @Test
    @DisplayName("应该处理任务执行失败")
    void shouldHandleJobFailure() throws Exception {
        // 准备: Simulate job failure
        try (MockedStatic<XxlJobHelper> helper = mockStatic(XxlJobHelper.class)) {
            helper.when(XxlJobHelper::getJobParam).thenReturn("provenanceCode=PUBMED");

            doThrow(new RuntimeException("Ingestion failed"))
                .when(orchestrator).ingestFromProvenance(any());

            // 执行
            ReturnT<String> result = job.harvestPubmedData("provenanceCode=PUBMED");

            // 断言: Verify failure response
            assertThat(result.getCode()).isEqualTo(ReturnT.FAIL_CODE);
            assertThat(result.getMsg()).contains("Ingestion failed");

            // 验证 error logged
            helper.verify(() -> XxlJobHelper.log(contains("Job failed")));
        }
    }

    @Test
    @DisplayName("应该解析复杂的任务参数")
    void shouldParseComplexParameters() throws Exception {
        // 准备
        try (MockedStatic<XxlJobHelper> helper = mockStatic(XxlJobHelper.class)) {
            String params = "provenanceCode=PUBMED&dateFrom=2024-01-01&dateTo=2024-01-31";
            helper.when(XxlJobHelper::getJobParam).thenReturn(params);

            // 执行
            ReturnT<String> result = job.harvestPubmedDataWithDateRange(params);

            // 断言
            assertThat(result.getCode()).isEqualTo(ReturnT.SUCCESS_CODE);

            // 验证 orchestrator called with parsed parameters
            verify(orchestrator).ingestFromProvenanceWithDateRange(
                eq("PUBMED"),
                eq("2024-01-01"),
                eq("2024-01-31")
            );
        }
    }
}
```

**XXL-Job 测试的关键点**:
- 使用 `MockedStatic` Mock `XxlJobHelper`
- 测试参数解析（`getJobParam()`）
- 测试任务执行流程
- 测试失败场景和错误处理
- 验证任务日志（`XxlJobHelper.log()`）

---

## 集成测试模式

**目的**: 使用真实数据库和事件传播测试**端到端工作流**。

### 特性

- ✅ 真实数据库（TestContainers）
- ✅ 真实事件发布
- ✅ 测试完整工作流
- ⚠️ 最慢的测试（数秒）

---

### 事件链测试

通过多个聚合根测试**端到端事件传播**。

#### 目的

验证领域事件在系统中正确传播（Task → Slice → Plan）。

#### Example: Testing Event Chain

```java
package com.patra.ingest.integration;

import com.patra.ingest.domain.event.TaskCompletedEvent;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.SliceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.port.PlanRepositoryPort;
import com.patra.ingest.domain.port.SliceRepositoryPort;
import com.patra.ingest.domain.port.TaskRepositoryPort;
import com.patra.ingest.infra.outbox.OutboxMessage;
import com.patra.ingest.infra.outbox.OutboxMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Transactional
@DisplayName("任务到 Slice 事件链集成测试")
class TaskToSliceEventChainIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("patra_test")
        .withUsername("root")
        .withPassword("123456")
        .withReuse(true);  // 容器重用

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // 为测试动态配置数据库连接
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private TaskRepositoryPort taskRepo;

    @Autowired
    private SliceRepositoryPort sliceRepo;

    @Autowired
    private PlanRepositoryPort planRepo;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private OutboxMessageRepository outboxRepo;

    @Test
    @DisplayName("应该传播 Task → Slice → Plan 状态更新")
    void shouldPropagateStatusUpdates() {
        // 准备: Prepare Plan → Slice → Task hierarchy
        PlanAggregate plan = planRepo.save(PlanTestBuilder.aDefaultPlan()
            .withStatus(PlanStatus.IN_PROGRESS)
            .build());

        SliceAggregate slice = sliceRepo.save(SliceTestBuilder.aSlice()
            .withPlanId(plan.getId())
            .withStatus(SliceStatus.IN_PROGRESS)
            .build());

        TaskAggregate task = taskRepo.save(TaskTestBuilder.aTask()
            .withSliceId(slice.getId())
            .withStatus(TaskStatus.IN_PROGRESS)
            .build());

        // 执行: Publish TaskCompletedEvent (simulate task completion)
        eventPublisher.publishEvent(new TaskCompletedEvent(
            task.getId(),
            slice.getId(),
            TaskStatus.COMPLETED,
            "dedupKey-" + task.getId(),
            Instant.now()
        ));

        // 等待事件链完成 (AFTER_COMMIT)
        flushAndClear();

        // 断言 1: Slice status updated
        SliceAggregate updatedSlice = sliceRepo.findById(slice.getId()).orElseThrow();
        assertThat(updatedSlice.getStatus()).isEqualTo(SliceStatus.COMPLETED);

        // 断言 2: Plan status updated (assuming all slices completed)
        PlanAggregate updatedPlan = planRepo.findById(plan.getId()).orElseThrow();
        assertThat(updatedPlan.getStatus()).isEqualTo(PlanStatus.COMPLETED);

        // 断言 3: Verify Outbox event records
        List<OutboxMessage> outboxMessages = outboxRepo.findAll();
        assertThat(outboxMessages)
            .extracting(OutboxMessage::getEventType)
            .contains("SliceStatusChangedEvent", "PlanStatusChangedEvent");
    }

    @Test
    @DisplayName("应该处理并发的任务完成事件")
    void shouldHandleConcurrentCompletions() {
        // 准备: Prepare multiple tasks for one slice
        PlanAggregate plan = planRepo.save(PlanTestBuilder.aDefaultPlan().build());
        SliceAggregate slice = sliceRepo.save(SliceTestBuilder.aSlice()
            .withPlanId(plan.getId()).build());

        List<TaskAggregate> tasks = IntStream.range(0, 5)
            .mapToObj(i -> taskRepo.save(TaskTestBuilder.aTask()
                .withSliceId(slice.getId())
                .withStatus(TaskStatus.IN_PROGRESS)
                .build()))
            .toList();

        // 执行: Publish multiple task completion events concurrently
        tasks.parallelStream().forEach(task ->
            eventPublisher.publishEvent(new TaskCompletedEvent(
                task.getId(), slice.getId(), TaskStatus.COMPLETED,
                "dedupKey-" + task.getId(), Instant.now()
            ))
        );

        // 断言：验证幂等性（无重复事件处理）
        long outboxCount = outboxRepo.count();
        assertThat(outboxCount).isLessThanOrEqualTo(tasks.size() + 1); // Task 事件 + 1 个 Slice 事件
    }

    private void flushAndClear() {
        // 辅助方法：刷新和清除实体管理器（用于测试）
        // 实现取决于你的设置
    }
}
```

**事件链测试的关键点**:
- 测试端到端传播（Task → Slice → Plan）
- 验证所有级别的聚合根更新
- 测试并发事件处理
- 验证链中的幂等性
- 检查每个级别的 Outbox 消息创建

---

### Outbox 模式测试

测试**事务性 Outbox** 用于可靠的事件发布。

#### 目的

验证业务数据和 Outbox 消息原子性保存，并且中继机制正确工作。

#### Example: Testing Outbox Atomicity

```java
package com.patra.ingest.integration;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.port.PlanRepositoryPort;
import com.patra.ingest.infra.outbox.OutboxMessage;
import com.patra.ingest.infra.outbox.OutboxMessageRepository;
import com.patra.ingest.app.coordinator.PlanPersistenceCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Transactional
@DisplayName("Outbox 模式集成测试")
class OutboxPatternIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("patra_test")
        .withUsername("root")
        .withPassword("123456")
        .withReuse(true);  // 容器重用以提高测试速度

    // 注意：@DynamicPropertySource 在此省略，会在基类中配置

    @Autowired
    private PlanRepositoryPort planRepo;

    @Autowired
    private OutboxMessageRepository outboxRepo;

    @Autowired
    private PlanPersistenceCoordinator coordinator;

    @Test
    @DisplayName("应该以原子方式持久化业务数据和 Outbox 消息")
    void shouldPersistAtomically() {
        // 准备
        PlanAggregate plan = PlanTestBuilder.aDefaultPlan().build();

        // 执行: Persist Plan + create Outbox message
        coordinator.persistPlanWithOutbox(plan);

        // 断言 1: Plan saved
        PlanAggregate saved = planRepo.findById(plan.getId()).orElseThrow();
        assertThat(saved).isNotNull();

        // 断言 2: Outbox message created (same transaction)
        OutboxMessage outbox = outboxRepo.findByDedupKey("plan-created-" + plan.getId()).orElseThrow();
        assertThat(outbox.getEventType()).isEqualTo("PlanCreatedEvent");
        assertThat(outbox.getStatus()).isEqualTo("PENDING");
        assertThat(outbox.getPayload()).contains(plan.getId().toString());
    }

    @Test
    @DisplayName("当业务操作失败时应该回滚 Outbox 消息")
    void shouldRollbackOutboxOnFailure() {
        // 准备: Mock business logic failure
        PlanAggregate plan = PlanTestBuilder.aDefaultPlan()
            .withInvalidData()  // 触发 validation failure
            .build();

        // 执行 & 断言
        assertThatThrownBy(() -> coordinator.persistPlanWithOutbox(plan))
            .isInstanceOf(ValidationException.class);

        // 验证: Both Plan and Outbox message NOT saved (transaction rollback)
        assertThat(planRepo.findById(plan.getId())).isEmpty();
        assertThat(outboxRepo.findByDedupKey("plan-created-" + plan.getId())).isEmpty();
    }

    @Test
    @DisplayName("应该防止重复的 Outbox 消息（dedupKey 唯一性）")
    void shouldPreventDuplicateOutbox() {
        // 准备: Save one Outbox message
        String dedupKey = "plan-created-123";
        outboxRepo.save(OutboxMessage.builder()
            .dedupKey(dedupKey)
            .eventType("PlanCreatedEvent")
            .status("PUBLISHED")
            .build());

        // 执行: Attempt to create duplicate message
        PlanAggregate plan = PlanTestBuilder.aDefaultPlan().withId(123L).build();

        assertThatThrownBy(() -> coordinator.persistPlanWithOutbox(plan))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("dedupKey");
    }

    @Test
    @DisplayName("应该中继待发的 Outbox 消息")
    void shouldRelayPendingMessages() {
        // 准备: Create PENDING outbox message
        OutboxMessage pending = outboxRepo.save(OutboxMessage.builder()
            .dedupKey("plan-created-999")
            .eventType("PlanCreatedEvent")
            .status("PENDING")
            .payload("{\"planId\":999}")
            .build());

        // 执行: Execute Relay (simulate scheduled job)
        OutboxRelayOrchestrator relayOrchestrator = ...; // inject
        relayOrchestrator.relayPendingMessages();

        // 断言: Message status updated to PUBLISHED
        OutboxMessage relayed = outboxRepo.findById(pending.getId()).orElseThrow();
        assertThat(relayed.getStatus()).isEqualTo("PUBLISHED");
        assertThat(relayed.getPublishedAt()).isNotNull();
    }
}
```

**Outbox 模式测试的关键点**:
- 测试原子性：业务数据 + Outbox 消息在同一事务中
- 测试回滚：失败时两者应一起回滚
- 测试 dedupKey 唯一性约束
- 测试中继机制（PENDING → PUBLISHED）
- 测试 Outbox 级别的幂等性

---

### 事务边界测试

测试**事务传播**和**回滚场景**。

#### Example: Testing Transaction Rollback

```java
@SpringBootTest
@Transactional
class TransactionBoundaryTest {

    @Autowired
    private PlanIngestionOrchestrator orchestrator;

    @Autowired
    private PlanRepositoryPort planRepo;

    @Test
    @DisplayName("应该在失败时回滚整个事务")
    void shouldRollbackOnFailure() {
        // 准备
        PlanIngestionContext context = new PlanIngestionContext(...);

        // Mock 以在持久化期间抛出异常
        doThrow(new RuntimeException("DB error"))
            .when(planRepo).save(any());

        // 执行 & 断言
        assertThatThrownBy(() -> orchestrator.ingestPlan(context))
            .isInstanceOf(RuntimeException.class);

        // 验证: No data persisted (transaction rolled back)
        assertThat(planRepo.findAll()).isEmpty();
    }

    @Test
    @DisplayName("应该支持 REQUIRES_NEW 传播")
    void shouldSupportRequiresNewPropagation() {
        // 测试 REQUIRES_NEW 是否创建独立事务
        // 实现取决于你的编排器设计
    }
}
```

---

### 并发和乐观锁测试

测试**并发更新**和**乐观锁冲突解决**。

#### Example: Testing Optimistic Lock Conflicts

```java
@SpringBootTest
class ConcurrencyTest {

    @Autowired
    private PlanRepositoryPort planRepo;

    @Test
    @DisplayName("应该检测并发更新冲突")
    void shouldDetectConcurrentConflicts() {
        // 准备：保存版本为 1 的 Plan
        PlanAggregate plan = planRepo.save(PlanTestBuilder.aDefaultPlan()
            .withVersion(1L)
            .build());

        // 模拟并发更新（版本不匹配）
        PlanAggregate staleVersion = planRepo.findById(plan.getId()).orElseThrow();
        plan.updateStatus(PlanStatus.COMPLETED);  // 更新原始版本
        planRepo.save(plan);  // 保存原始版本（版本 2）

        // 执行：尝试更新过期版本（仍为版本 1）
        staleVersion.updateStatus(PlanStatus.FAILED);

        // 断言：抛出 OptimisticLockingFailureException
        assertThatThrownBy(() -> planRepo.save(staleVersion))
            .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("应该在乐观锁冲突时重试")
    void shouldRetryOnOptimisticLockConflict() {
        // 测试乐观锁冲突的重试逻辑
        // 实现取决于你的重试机制
    }
}
```

---

## 测试数据管理

**目的**: 高效管理测试数据的创建和隔离。

### 测试数据构造器模式

使用**流畅构造器**以可读的、可维护的代码创建测试数据。

#### Example: Test Data Builder

```java
public class PlanTestBuilder {
    private Long id;
    private String planKey = "TEST:HARVEST:12345";
    private String provenanceCode = "TEST";
    private String operationCode = "HARVEST";
    private PlanStatus status = PlanStatus.DRAFT;
    private WindowSpec windowSpec = WindowSpec.ofSingle();
    private Long version = 1L;

    public static PlanTestBuilder aDefaultPlan() {
        return new PlanTestBuilder();
    }

    public PlanTestBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public PlanTestBuilder withProvenanceCode(String code) {
        this.provenanceCode = code;
        return this;
    }

    public PlanTestBuilder withStatus(PlanStatus status) {
        this.status = status;
        return this;
    }

    public PlanTestBuilder withVersion(Long version) {
        this.version = version;
        return this;
    }

    public PlanTestBuilder withWindowSpec(WindowSpec windowSpec) {
        this.windowSpec = windowSpec;
        return this;
    }

    public PlanAggregate build() {
        return PlanAggregate.create(
            100L,            // 调度实例 ID
            planKey,
            provenanceCode,
            operationCode,
            "hash123",
            "{}",
            "{}",
            "confighash",
            windowSpec,
            "SINGLE",
            "{}"
        );
    }
}
```

#### 使用示例

```java
// 简单 Plan
PlanAggregate plan = aDefaultPlan().build();

// 自定义 Plan
PlanAggregate pubmedPlan = aDefaultPlan()
    .withProvenanceCode("PUBMED")
    .withStatus(PlanStatus.READY)
    .withVersion(5L)
    .build();

// 具有时间窗口的 Plan
PlanAggregate timePlan = aDefaultPlan()
    .withWindowSpec(WindowSpec.ofTime(
        Instant.parse("2024-01-01T00:00:00Z"),
        Instant.parse("2024-01-31T23:59:59Z")
    ))
    .build();
```

### 测试夹具管理

在专用类或方法中组织测试夹具。

#### 示例：夹具类

```java
public class TestFixtures {

    public static Provenance createProvenance(String code) {
        return new Provenance(
            null,
            code,
            code + " Name",
            "https://" + code.toLowerCase() + ".com",
            "UTC",
            "https://docs.com",
            true,
            "ACTIVE"
        );
    }

    public static PlanAggregate createPlanForProvenance(String provenanceCode) {
        return PlanTestBuilder.aDefaultPlan()
            .withProvenanceCode(provenanceCode)
            .build();
    }

    public static List<TaskAggregate> createTasksForSlice(Long sliceId, int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> TaskTestBuilder.aTask()
                .withSliceId(sliceId)
                .withTaskKey("TASK-" + i)
                .build())
            .toList();
    }
}
```

### 测试数据隔离

在测试类上使用 `@Transactional` 以在每个测试后自动回滚。

```java
@SpringBootTest
@Transactional  // 每个测试后自动回滚
class MyIntegrationTest {

    @Test
    void testA() {
        // 创建测试数据
        // 测试后自动回滚
    }

    @Test
    void testB() {
        // 全新数据库状态（前一个测试已回滚）
    }
}
```

---

## 测试覆盖率策略

**目的**: 定义覆盖率目标和测量策略。

### JaCoCo 配置

配置 JaCoCo 以进行自动覆盖率报告。

#### Maven 配置

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>PACKAGE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### 层级特定的覆盖率目标

| 层级 | 目标覆盖率 | 理由 |
|-------|----------------|-----------|
| **Domain（领域层）** | 90%+ | 核心业务逻辑 - 需要彻底测试 |
| **Application（应用层）** | 80%+ | 编排逻辑 - 重要的工作流 |
| **Infrastructure（基础设施层）** | 70%+ | Repository & 转换器 - 存在一些样板代码 |
| **Adapter（适配器层）** | 75%+ | 控制器和任务 - 验证和错误处理 |

### 关键路径识别

专注于高价值的测试覆盖率：

1. **业务关键路径**:
   - 支付处理
   - 用户认证
   - 数据采集工作流

2. **复杂的领域逻辑**:
   - 状态计算算法
   - 聚合规则
   - 验证逻辑

3. **容易出错的地方**:
   - 并发更新
   - 事件传播
   - 外部 API 集成

### AI 测试覆盖率检查清单

生成测试时，确保覆盖以下内容：

```
□ 成功路径（预期流程）
□ 边界情况（边界值、空列表、null）
□ 错误场景（异常、验证失败）
□ 业务规则违反
□ 并发场景（如适用）
□ 幂等性（如适用）
□ 事务回滚（如果使用 @Transactional）
□ 事件发布（如果是事件驱动）
```

---

## ArchUnit 测试

详见 [dependency-rules.md](dependency-rules.md) 了解完整的 ArchUnit 模式。

**快速示例**:

```java
@AnalyzeClasses(packages = "com.patra.registry")
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainLayerIsIndependent =
        classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat(
                resideInAnyPackage(
                    "java..",
                    "lombok..",
                    "cn.hutool..",
                    "com.patra.common..",
                    "..domain.."
                )
            );

    @ArchTest
    static final ArchRule adaptersDependOnApplication =
        classes()
            .that().resideInAPackage("..adapter..")
            .should().dependOnClassesThat(
                resideInAnyPackage(
                    "..app..",
                    "..domain.."
                )
            )
            .andShould().notDependOnClassesThat(
                resideInAPackage("..infra..")
            );
}
```

---

## Testing Best Practices

### 1. 测试命名约定

```java
// ✅ 好：描述行为
@Test
@DisplayName("当提供有效上下文时应该创建 Plan")
void shouldCreatePlanWhenValidContextProvided() { }

@Test
@DisplayName("当 Provenance 未找到时应该抛出异常")
void shouldThrowExceptionWhenProvenanceNotFound() { }

// ❌ 不好：通用名称
@Test
@DisplayName("测试创建 Plan")
void testCreatePlan() { }

@Test
void test1() { }
```

### 2. AAA 模式（准备-执行-断言）

```java
@Test
@DisplayName("应该计算窗口持续时间")
void shouldCalculateWindowDuration() {
    // 准备 (Given) - 设置测试数据
    Instant start = Instant.parse("2024-01-01T00:00:00Z");
    Instant end = Instant.parse("2024-01-31T23:59:59Z");
    WindowSpec.Time window = WindowSpec.ofTime(start, end);

    // 执行 (When) - 执行行为
    long durationSeconds = window.to().getEpochSecond() - window.from().getEpochSecond();

    // 断言 (Then) - 验证结果
    assertThat(durationSeconds).isGreaterThan(0);
}
```

### 3. 使用 AssertJ 进行流畅的断言

```java
// ✅ 好（AssertJ - 流畅且可读）
assertThat(plan.getStatus()).isEqualTo(PlanStatus.DRAFT);
assertThat(plan.getId()).isNotNull();
assertThat(plan.getProvenanceCode()).isEqualTo("PUBMED");

// ❌ 避免（JUnit 断言 - 可读性较差）
assertNotNull(plan.getId());
assertTrue(plan.getStatus() == PlanStatus.DRAFT);
assertEquals("PUBMED", plan.getProvenanceCode());
```

### 4. 避免测试相互依赖

```java
// ❌ 不好：测试依赖执行顺序
@Test
void test1_createProvenance() {
    provenance = provenanceService.create("PUBMED");
}

@Test
void test2_updateProvenance() {
    provenanceService.update(provenance, "Updated");  // 依赖 test1
}

// ✅ 好：每个测试都是独立的
@Test
@DisplayName("应该创建 Provenance")
void shouldCreateProvenance() {
    Provenance provenance = provenanceService.create("PUBMED");
    assertThat(provenance).isNotNull();
}

@Test
@DisplayName("应该更新 Provenance")
void shouldUpdateProvenance() {
    Provenance provenance = createTestProvenance();  // 创建全新实例
    provenanceService.update(provenance, "Updated");
}
```

### 5. 每个测试只测试一个行为

```java
// ❌ 不好：测试多个行为
@Test
void testProvenance() {
    Provenance p = create("PUBMED");
    assertThat(p.code()).isEqualTo("PUBMED");

    update(p, "Updated");
    assertThat(p.name()).isEqualTo("Updated");

    delete(p);
    assertThat(findByCode("PUBMED")).isEmpty();
}

// ✅ 好：每个测试只测试一个行为
@Test
@DisplayName("应该创建 Provenance")
void shouldCreateProvenance() { ... }

@Test
@DisplayName("应该更新 Provenance")
void shouldUpdateProvenance() { ... }

@Test
@DisplayName("应该删除 Provenance")
void shouldDeleteProvenance() { ... }
```

### 6. 使用 DisplayName 提高清晰度

```java
@Test
@DisplayName("应该传播 Task → Slice → Plan 状态更新")
void shouldPropagateStatusUpdates() {
    // 测试实现
}
```

---

## 测试检查清单和快速参考

**目的**: 在生成测试时为 AI 提供快速的决策支持。

### 检查清单：测试 Orchestrator

当你看到 **Orchestrator** 类时：

```
□ Mock 所有 Coordinator 依赖
□ Mock 所有 Port 依赖
□ 使用 InOrder 验证 Coordinator 调用顺序
□ 测试事务回滚场景（@Transactional）
□ 测试异常传播链
□ 测试幂等性（如适用）
□ 目标 80%+ 覆盖率
```

**示例模式**:
```java
@ExtendWith(MockitoExtension.class)
class OrchestratorTest {
    @Mock private CoordinatorA coordinatorA;
    @Mock private CoordinatorB coordinatorB;
    @InjectMocks private MyOrchestrator orchestrator;

    @Test
    void shouldCallCoordinatorsInOrder() {
        orchestrator.execute(context);
        InOrder inOrder = inOrder(coordinatorA, coordinatorB);
        inOrder.verify(coordinatorA).doA(any());
        inOrder.verify(coordinatorB).doB(any());
    }
}
```

---

### 检查清单：测试事件处理器

当你看到 **@TransactionalEventListener** 时：

```
□ 使用 @SpringBootTest（需要 Spring 上下文）
□ 使用 @Transactional 进行自动回滚
□ Mock ApplicationEventPublisher
□ 验证 AFTER_COMMIT 阶段事件发布
□ 测试幂等性检查（针对 Outbox 的 dedupKey 查询）
□ 测试 OptimisticLockingFailureException 处理
□ 验证事件链传播（集成测试）
□ 测试并发事件场景（可选）
```

**示例模式**:
```java
@SpringBootTest
@Transactional
class EventHandlerTest {
    @Autowired private MyEventHandler handler;
    @MockBean private ApplicationEventPublisher publisher;
    @Autowired private OutboxMessageRepository outboxRepo;

    @Test
    void shouldPublishEventAfterCommit() {
        handler.onEvent(event);
        verify(publisher).publishEvent(any(NextEvent.class));
    }

    @Test
    void shouldNotProcessDuplicateEvent() {
        outboxRepo.save(OutboxMessage.builder().dedupKey("dup").build());
        handler.onEvent(eventWithDedupKey("dup"));
        verify(publisher, never()).publishEvent(any());
    }
}
```

---

### 检查清单：测试 Repository

当你看到 **Repository 实现** 时：

```
□ 使用 TestContainers（真实数据库）
□ 继承 BaseIntegrationTest（或配置 MySQL 容器）
□ 测试基本 CRUD 操作
□ 测试复杂查询（Wrapper、分页）
□ 测试唯一性约束违反
□ 测试乐观锁版本冲突
□ 清理测试数据（@Transactional 自动回滚）
```

**示例模式**:
```java
@SpringBootTest
@Testcontainers
class RepositoryIntegrationTest extends BaseIntegrationTest {
    @Autowired private MyRepository repo;

    @Test
    void shouldSaveAndFind() {
        var entity = repo.save(createEntity());
        var found = repo.findById(entity.getId());
        assertThat(found).isPresent();
    }
}
```

---

### 检查清单：测试 Coordinator

当你看到 **Coordinator** 类时：

```
□ 使用 @ExtendWith(MockitoExtension.class)
□ Mock 所有依赖（ports、其他 coordinators）
□ 验证关注点特定的逻辑
□ 测试异常包装（基础设施 → 领域）
□ 测试边界情况（空列表、null 处理）
```

---

### 检查清单：测试转换器

当你看到 **MapStruct** 转换器时：

```
□ 测试 Domain → DO 转换
□ 测试 DO → Domain 转换（往返）
□ 测试空字段处理
□ 测试列表转换
□ 测试复杂嵌套对象
□ 验证字段名称映射
```

---

### 检查清单：测试 XXL-Job

当你看到 **@XxlJob** 时：

```
□ 使用 @ExtendWith(MockitoExtension.class)
□ 使用 MockedStatic Mock XxlJobHelper
□ 测试参数解析（getJobParam）
□ 测试任务执行流程
□ 测试失败场景和错误处理
□ 验证任务日志（XxlJobHelper.log）
```

---

### 模式匹配快速参考

| 看到这个代码特征 | 立即知道 | 验证这些要点 | 示例部分 |
|-----------------------|------------------|---------------------|-----------------|
| `@TransactionalEventListener(phase = AFTER_COMMIT)` | 集成测试 + Mock Publisher | 事件发布、幂等性、乐观锁 | [§4.3](#event-handler-testing) |
| `Orchestrator + 多个 Coordinators` | 分层 Mock | 调用顺序（InOrder）、异常包装 | [§4.1](#orchestrator-testing) |
| `OutboxMessage.builder()...save()` | 集成测试 | 与业务数据的原子性、dedupKey | [§7.2](#outbox-pattern-testing) |
| `@XxlJob` | Mock XxlJobHelper | 参数解析、任务流程 | [§6.2](#xxl-job-testing) |
| `@RestController` + `@Valid` | @WebMvcTest + MockMvc | 验证失败、ProblemDetail | [§6.1](#rest-controller-testing) |
| `MapStruct` 转换器 | 单元测试 | 双向映射、空字段处理 | [§5.2](#converter-testing) |
| `@Transactional` | 集成测试 | 回滚场景、REQUIRES_NEW | [§7.3](#transaction-boundary-testing) |
| 具有 `version` 字段的聚合根 | 集成测试 | 并发更新冲突 | [§7.4](#concurrency--optimistic-locking-testing) |

---

## 总结

**测试策略**:
- **Domain（领域层）**: 纯单元测试，无 Mock
- **Application（应用层）**: Mock port，测试编排
- **Infrastructure（基础设施层）**: TestContainers、真实数据库
- **Adapter（适配器层）**: MockMvc 或任务 Mock、Mock 应用层
- **Integration（集成测试）**: 使用真实数据库和事件的端到端工作流
- **Architecture（架构）**: ArchUnit 用于依赖验证

**关键工具**:
- **JUnit 5**: 测试框架
- **Mockito**: Mock 框架
- **AssertJ**: 流畅的断言
- **TestContainers**: 集成测试的真实数据库
- **ArchUnit**: 架构验证
- **JaCoCo**: 代码覆盖率

**测试金字塔目标**:
- 70% 单元测试（Domain + Application）
- 25% 集成测试（Infrastructure + 集成模式）
- 5% E2E 测试（关键工作流）

**另见**:
- [dependency-rules.md](dependency-rules.md) - ArchUnit 模式
- [architecture-overview.md](architecture-overview.md) - 层级职责
- [orchestrator-coordinator-patterns.md](orchestrator-coordinator-patterns.md) - 应用层模式
- [event-driven-architecture.md](event-driven-architecture.md) - 事件处理模式
- [outbox-pattern.md](outbox-pattern.md) - Outbox 实现细节
