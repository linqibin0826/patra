# 测试指南 - 快速参考

六边形架构 + DDD Spring Boot 项目的测试策略快速决策指南。

## 🎯 测试策略决策树

### 第一步：识别代码模式

```mermaid
Q: 代码在哪一层？
├─ Domain (纯 Java，无 Spring) → 纯单元测试
├─ Application (@Service/@Transactional) → Mock 测试
├─ Infrastructure (Repository/Converter) → 集成测试
└─ Adapter (Controller/Job) → 切片测试
```

### 第二步：选择测试类型

| 代码特征 | 测试类型 | 工具 | 详细指南 |
|---------|---------|------|----------|
| **Domain Entity/VO** | 纯单元测试 | JUnit + AssertJ | [test-templates-domain.md](test-templates-domain.md) |
| **Orchestrator** | Mock 测试 | Mockito + InOrder | [test-templates-application.md](test-templates-application.md#orchestrator) |
| **Event Handler** | 集成测试 | @SpringBootTest | [test-templates-application.md](test-templates-application.md#event-handler) |
| **Repository** | 集成测试 | TestContainers | [test-templates-infrastructure.md](test-templates-infrastructure.md#repository) |
| **Converter** | 单元测试 | MapStruct Test | [test-templates-infrastructure.md](test-templates-infrastructure.md#converter) |
| **REST Controller** | 切片测试 | @WebMvcTest | [test-templates-adapter.md](test-templates-adapter.md#controller) |
| **XXL-Job** | Mock 测试 | MockedStatic | [test-templates-adapter.md](test-templates-adapter.md#xxl-job) |

## 🏗️ 测试金字塔

```
       /\
      /  \     5% E2E (关键业务流程)
     /----\    25% 集成测试 (数据库/消息队列)
    /------\   70% 单元测试 (业务逻辑)
   /________\
```

### 测试类型详解

| 测试类型 | 命名后缀 | 测试位置 | 测试内容 | 示例 |
|---------|---------|---------|---------|------|
| **单元测试** | `Test` | 任何模块 | Mock 依赖,测试单个类 | `OrderServiceTest` |
| **集成测试** | `IT` | `patra-{service}-boot` | 真实依赖,测试多组件协作 | `OrderRepositoryIT` |
| **E2E 测试** | `E2ETest` | `patra-{service}-boot` | 完整业务流程,端到端验证 | `OrderFlowE2ETest` |

### 集成测试 vs E2E 测试

**集成测试 (IT)**:
- 🎯 **范围**: 测试单个或少数组件与外部依赖的集成
- 🔧 **工具**: TestContainers (数据库、消息队列)
- 📍 **位置**: `patra-{service}-boot/src/test/java`
- 📝 **示例**: Repository + 数据库, Publisher + RocketMQ
- ⏱️ **执行时间**: 3-10 秒

**E2E 测试 (E2ETest)**:
- 🎯 **范围**: 测试完整业务流程,从 API 入口到数据持久化
- 🔧 **工具**: @SpringBootTest + TestContainers + MockMvc
- 📍 **位置**: `patra-{service}-boot/src/test/java`
- 📝 **示例**: HTTP 请求 → Orchestrator → Repository → Event → 数据库
- ⏱️ **执行时间**: 10-30 秒

### 覆盖率目标

| 层级 | 目标 | 原因 |
|------|------|------|
| Domain | 90%+ | 核心业务逻辑 |
| Application | 80%+ | 编排逻辑 |
| Infrastructure | 70%+ | 数据访问 |
| Adapter | 75%+ | 输入验证 |
| **Integration** | 60%+ | 关键集成点 |
| **E2E** | 30%+ | 核心业务流程 |

## 📋 快速检查清单

### 测试命名

```java
// 类名（按测试类型）
{被测类}Test.java              // 单元测试（可在任何模块）
{功能}IT.java                   // 集成测试（必须在 patra-{service}-boot 模块）
{功能}E2ETest.java              // E2E 测试（必须在 patra-{service}-boot 模块）

// 测试位置规则
// ✅ domain/app/adapter/infra 层：{被测类}Test.java
// ✅ boot 模块集成测试：{功能}IT.java
// ✅ boot 模块 E2E 测试：{功能}E2ETest.java
// ❌ 错误：在 infra 层使用 IntegrationTest 后缀
// ❌ 错误：在 domain 层使用 IT 后缀

// 方法名
should{行为}When{条件}()        // 有条件时
should{行为}()                  // 无特殊条件时

// 必须使用 @DisplayName 中文描述
@Test
@DisplayName("应该在条件X时执行行为Y")
void shouldDoSomethingWhenCondition() {}
```

### AAA 模式

```java
@Test
void testMethod() {
    // Arrange (准备)
    var input = createTestData();

    // Act (执行)
    var result = systemUnderTest.execute(input);

    // Assert (断言)
    assertThat(result).isEqualTo(expected);
}
```

## 🔍 模式识别表

### Domain 层模式

| 看到 | 就要 |
|-----|------|
| record/sealed interface | 测试不可变性、equals/hashCode |
| Aggregate Root | 测试状态转换、业务规则 |
| Domain Service | 测试计算逻辑、业务规则 |

### Application 层模式

| 看到 | 就要 |
|-----|------|
| @Transactional | 测试事务回滚 |
| Multiple Coordinators | 验证调用顺序 (InOrder) |
| @TransactionalEventListener | 测试幂等性、AFTER_COMMIT |
| ApplicationEventPublisher | Mock 并验证事件发布 |

### Infrastructure 层模式

| 看到 | 就要 |
|-----|------|
| extends BaseMapper | 用 TestContainers 测试 |
| @Mapper (MapStruct) | 测试双向映射 |
| LambdaQueryWrapper | 测试复杂查询条件 |
| @Version | 测试乐观锁冲突 |

### Adapter 层模式

| 看到 | 就要 |
|-----|------|
| @RestController | @WebMvcTest + MockMvc |
| @Valid | 测试 400 验证失败 |
| @XxlJob | Mock XxlJobHelper |
| ProblemDetail | 验证错误响应格式 |

## 🚀 快速开始示例

### Domain 测试 (最简单)

```java
@Test
@DisplayName("应该创建有效的值对象")
void shouldCreateValidValueObject() {
    // 纯 Java，无需 Mock
    var vo = new ProvenanceCode("PUBMED");
    assertThat(vo.value()).isEqualTo("PUBMED");
}
```

### Application 测试 (需要 Mock)

```java
@ExtendWith(MockitoExtension.class)
class OrchestratorTest {
    @Mock private CoordinatorA coordinatorA;
    @Mock private CoordinatorB coordinatorB;
    @InjectMocks private MyOrchestrator orchestrator;

    @Test
    void shouldOrchestrateInOrder() {
        orchestrator.execute();

        InOrder inOrder = inOrder(coordinatorA, coordinatorB);
        inOrder.verify(coordinatorA).doFirst();
        inOrder.verify(coordinatorB).doSecond();
    }
}
```

### Integration 测试 (需要数据库)

```java
// 必须在 patra-{service}-boot/src/test/java 下
@SpringBootTest
@Testcontainers
class RepositoryIT extends BaseIT {
    @Autowired
    private PlanRepositoryPort planRepo;

    @Test
    @DisplayName("应该保存并查询 Plan")
    void shouldPersistEntity() {
        var entity = planRepo.save(createEntity());
        assertThat(entity.getId()).isNotNull();
    }
}
```

## 📚 详细资源

需要更详细的模板和示例？查看以下资源：

### 测试模板
- **[test-templates-domain.md](test-templates-domain.md)** - Domain 层测试模板
- **[test-templates-application.md](test-templates-application.md)** - Orchestrator/Coordinator/EventHandler 测试
- **[test-templates-infrastructure.md](test-templates-infrastructure.md)** - Repository/Converter 测试
- **[test-templates-adapter.md](test-templates-adapter.md)** - Controller/Job 测试

### 高级模式
- **[test-patterns-integration.md](test-patterns-integration.md)** - 事件链、Outbox、事务、并发测试
- **[test-data-management.md](test-data-management.md)** - 测试数据构造器、夹具管理
- **[archunit-tests.md](archunit-tests.md)** - 架构规则验证

## 🎓 核心原则

1. **测试独立性** - 每个测试必须独立运行
2. **AAA 模式** - Arrange → Act → Assert
3. **单一行为** - 每个测试只验证一个行为
4. **描述性命名** - 使用 @DisplayName 中文描述
5. **快速反馈** - 单元测试 < 100ms，集成测试 < 5s

## 🔧 常用 AssertJ 断言

```java
// 基础断言
assertThat(actual).isEqualTo(expected);
assertThat(actual).isNotNull();
assertThat(list).hasSize(3);
assertThat(list).contains(item1, item2);

// 异常断言
assertThatThrownBy(() -> method())
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("invalid");

// 链式断言
assertThat(list)
    .hasSize(3)
    .extracting(Plan::getStatus)
    .containsExactly(DRAFT, READY, COMPLETED);
```

## ⚠️ 常见错误

| ❌ 错误 | ✅ 正确 |
|---------|---------|
| 测试中直接操作数据库 | 使用 @Transactional 自动回滚 |
| 过度 Mock | 只 Mock 边界（Port 接口） |
| 测试实现细节 | 测试行为和契约 |
| 忽略边界情况 | 测试 null、空集合、异常 |
| 硬编码测试数据 | 使用测试数据构造器 |

---

## 🚨 重要提醒：测试位置规则

### ✅ 正确示例

```
patra-registry/
├── patra-registry-domain/src/test/java/
│   └── ProvenanceTest.java              ✅ 单元测试
├── patra-registry-app/src/test/java/
│   └── OrchestratorTest.java            ✅ 单元测试
├── patra-registry-infra/src/test/java/
│   └── ConverterTest.java               ✅ 单元测试
├── patra-registry-adapter/src/test/java/
│   └── ControllerTest.java              ✅ 单元测试（@WebMvcTest）
└── patra-registry-boot/src/test/java/
    ├── ProvenanceRepositoryIT.java      ✅ 集成测试
    ├── RocketMqPublisherIT.java         ✅ 集成测试
    ├── ProvenanceFlowE2ETest.java       ✅ E2E 测试
    └── ArchitectureTest.java            ✅ 架构测试
```

### ❌ 错误示例

```
❌ patra-registry-infra/src/test/java/
   └── RepositoryIntegrationTest.java   # 错误！集成测试必须在 boot 模块

❌ patra-registry-domain/src/test/java/
   └── ProvenanceIT.java                # 错误！Domain 层只有单元测试

❌ patra-registry-app/src/test/java/
   └── OrchestratorE2ETest.java         # 错误！E2E 必须在 boot 模块
```

### 命名规范速查表

| 你想写... | 后缀 | 位置 | 注解 |
|----------|-----|------|------|
| 单元测试（纯 Java） | `Test` | 任何模块 | 无或 `@ExtendWith(MockitoExtension.class)` |
| 单元测试（Mock） | `Test` | 任何模块 | `@ExtendWith(MockitoExtension.class)` |
| 切片测试（Controller） | `Test` | adapter 模块 | `@WebMvcTest` |
| 集成测试（数据库/MQ） | `IT` | **boot 模块** | `@SpringBootTest + @Testcontainers` |
| E2E 测试（完整流程） | `E2ETest` | **boot 模块** | `@SpringBootTest + @Testcontainers` |
| 架构测试 | `ArchitectureTest` | boot 模块 | `@AnalyzeClasses` |

---

**需要生成测试？** 使用 `test-architect` subagent:
```
为 MyOrchestrator 生成测试
审查 MyRepository 的测试覆盖率
```

**需要了解细节？** 查看对应的模板文件。