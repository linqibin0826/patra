# 测试反模式

**在以下情况加载此参考：** 编写或修改测试、添加 mock、或想在生产代码中添加仅测试用方法时。

## 概述

测试必须验证真实行为，而非 mock 行为。Mock 是隔离的手段，不是被测试的对象。

**核心原则：** 测试代码做了什么，而非 mock 做了什么。

**严格遵循 TDD 可以防止这些反模式。**

## 铁律

```
1. 绝不测试 mock 行为
2. 绝不在生产类中添加仅测试用的方法
3. 绝不在不理解依赖的情况下使用 mock
```

## 反模式 1：测试 Mock 行为

**违规做法：**
```java
// ❌ 差：只验证 mock 是否被调用，没验证真实行为
@Test
void publishes_order_placed_event() {
    EventBus mockBus = mock(EventBus.class);
    OrderService service = new OrderService(mockBus);

    service.placeOrder(order);

    verify(mockBus).publish(any());  // 测的是"mock 被调用了"
}
```

**为什么这是错误的：**
- 你在验证 mock 能被调，而非业务能产出正确的事件
- mock 拦截了一切——`publish` 收到什么参数？事件类型对吗？字段全吗？都不知道
- 对真实行为一无所知

**你的人类伙伴的纠正：** "我们是在测试 mock 的行为吗？"

**正确做法：**
```java
// ✅ 好：用 fake / 真实实现验证真实结果
@Test
void publishes_order_placed_event() {
    InMemoryEventBus bus = new InMemoryEventBus();
    OrderService service = new OrderService(bus);

    service.placeOrder(order);

    assertThat(bus.publishedEvents())
        .singleElement()
        .isInstanceOfSatisfying(OrderPlacedEvent.class, e -> {
            assertThat(e.orderId()).isEqualTo(order.id());
            assertThat(e.amount()).isEqualTo(order.totalAmount());
        });
}
```

### 门控函数

```
在对任何 mock 元素做 verify(...) 或断言之前：
  问："我是在测试真实业务行为还是仅仅在确认 mock 被调用？"

  如果是确认 mock 被调用：
    停下——换用 fake / InMemory* 实现 / 真实实现
    断言对真实可观察的结果（事件、状态、返回值）

  改为测试真实行为
```

## 反模式 2：在生产代码中添加仅测试用方法

**违规做法：**
```java
// ❌ 差：destroy() 只在测试 @AfterEach 用，生产从不调用
public class Session {
    public void destroy() {  // 看起来像生产 API！
        workspaceManager.destroyWorkspace(this.id);
        // ... 清理资源
    }
}

// 在测试中
@AfterEach
void cleanup() {
    session.destroy();
}
```

**为什么这是错误的：**
- 生产类被仅测试用的代码污染
- 如果在生产环境中意外调用会很危险（destroy 真实资源）
- 违反 YAGNI 和关注点分离
- 混淆了对象生命周期和实体生命周期

**正确做法：**
```java
// ✅ 好：测试工具处理测试清理
// Session 没有 destroy()——它在生产中不直接销毁资源

// 模块内私有 helper → 放 src/test/java/.../testutil/；
// 跨 sourceSet 或跨模块共享的 fixture → 放 src/testFixtures/java/.../
// 在 src/test/java/.../testutil/SessionTestSupport.java（仅本模块使用）
public class SessionTestSupport {
    public static void cleanupSession(Session session, WorkspaceManager wm) {
        WorkspaceInfo workspace = session.workspaceInfo();
        if (workspace != null) {
            wm.destroyWorkspace(workspace.id());
        }
    }
}

// 在测试中
@AfterEach
void cleanup() {
    SessionTestSupport.cleanupSession(session, workspaceManager);
}
```

### 门控函数

```
在向生产类添加任何 public 方法之前：
  问："这只被测试使用吗？"

  如果是：
    停下——不要加到生产类
    模块内私有放 src/test/java/.../testutil/ 工具类；跨 sourceSet/模块共享放 src/testFixtures/java/.../

  问："这个类是否拥有此资源的生命周期？"

  如果否：
    停下——这个方法不属于这个类
```

## 反模式 3：不理解依赖就使用 Mock

**违规做法：**
```java
// ❌ 差：Mock 破坏了测试逻辑
@Test
void detects_duplicate_server() {
    // Mock 拦下了测试依赖的"写入注册表"副作用
    ToolCatalog catalog = mock(ToolCatalog.class);
    when(catalog.discoverAndCache(any())).thenReturn(List.of());

    ServerManager sm = new ServerManager(catalog, configStore);

    sm.addServer(config);
    sm.addServer(config);  // 应该抛 DuplicateServerException——但不会！
}
```

**为什么这是错误的：**
- 被 mock 的方法有测试依赖的副作用（向 catalog 写入条目，后续 addServer 才能查到重复）
- "保险起见"过度 mock 破坏了实际行为
- 测试因错误的原因通过或莫名其妙地失败

**正确做法：**
```java
// ✅ 好：在正确的层级 mock
@Test
void detects_duplicate_server() {
    // Mock 慢/外部的部分（真实启动子进程），保留测试需要的行为
    ServerLauncher mockLauncher = mock(ServerLauncher.class);
    ServerManager sm = new ServerManager(realCatalog, configStore, mockLauncher);

    sm.addServer(config);  // catalog 真实写入

    assertThatThrownBy(() -> sm.addServer(config))
        .isInstanceOf(DuplicateServerException.class);
}
```

### 门控函数

```
在 mock 任何方法之前：
  停下——先不要 mock

  1. 问："真实方法有什么副作用？"
  2. 问："这个测试是否依赖这些副作用？"
  3. 问："我完全理解这个测试需要什么吗？"

  如果依赖副作用：
    在更底层 mock（实际的慢操作 / 外部 IO / 网络调用）
    或使用保留必要行为的 fake / InMemory* 实现
    而非 mock 测试依赖的高层方法

  如果不确定测试依赖什么：
    先用真实实现运行测试
    观察实际需要发生什么
    然后在正确的层级添加最少的 mock

  危险信号：
    - "我 mock 一下保险"
    - "这可能慢，还是 mock 掉吧"
    - 不理解依赖链就 mock
```

## 反模式 4：不完整的 Mock

**违规做法：**
```java
// ❌ 差：部分 mock——只填你认为需要的字段
ApiResponse mockResponse = ApiResponse.builder()
    .status("success")
    .data(new UserData("123", "Alice"))
    .build();
// 缺失：下游代码读取的 metadata

when(apiClient.fetchUser("123")).thenReturn(mockResponse);
// 之后：service 访问 response.metadata().requestId() → NullPointerException
```

**为什么这是错误的：**
- **部分 mock 隐藏了结构假设** ——你只填了你知道的字段
- **下游代码可能依赖你没填的字段** ——静默失败
- **测试通过但集成失败** ——mock 不完整，真实 API 完整
- **虚假的信心** ——测试对真实行为什么也没证明

**铁律：** Mock 真实存在的完整数据结构，而非只填你当前测试用到的字段。

**正确做法：**
```java
// ✅ 好：镜像真实 API 的完整结构
ApiResponse mockResponse = ApiResponse.builder()
    .status("success")
    .data(new UserData("123", "Alice"))
    .metadata(new Metadata("req-789", Instant.parse("2026-01-01T00:00:00Z")))
    // 真实 API 返回的所有字段
    .build();
```

### 门控函数

```
在创建 mock 响应之前：
  检查："真实 API / DTO 包含哪些字段？"

  操作：
    1. 从代码定义 / OpenAPI / 真实响应示例查看完整结构
    2. 包含系统下游可能消费的所有字段
    3. 验证 mock 完全匹配真实响应的结构

  关键：
    如果你在创建 mock，你必须理解完整的结构
    部分 mock 在代码依赖遗漏字段时会静默失败（NPE / 缺字段断言失败）

  不确定时：包含所有文档记录的字段
```

## 反模式 5：集成测试作为事后补充

**违规做法：**
```
✅ 实现完成
❌ 没写集成测试
"准备好测试了"
```

**为什么这是错误的：**
- 测试是实现的一部分，不是可选的后续
- TDD 本可以防止这种情况
- 没有测试就不能声称完成

**正确做法：**
```
TDD 循环：
1. 编写失败的测试（单元 / slice / 集成 任选一层）
2. 实现使其通过
3. 重构
4. 然后才声称完成
```

**patra-api 集成测试约定：**
- 涉及 JPA / 数据库的 → `@DataJpaTest` + TestContainers 起真实 PostgreSQL
- 涉及 Spring MVC / Controller → `@WebMvcTest` slice + MockMvc
- 涉及完整应用启动 → `@SpringBootTest`（重，最后兜底）
- **不要**用 H2 内存数据库代替——跟生产 PG 行为差异会引入幻觉

## 当 Mock 变得过于复杂时

**警告信号：**
- Mock 的 setup 比测试逻辑还长
- 为了让测试通过而 mock 一切
- Mock 缺少真实组件拥有的方法
- Mock 变更时测试就坏了

**你的人类伙伴的问题：** "我们这里真的需要用 mock 吗？"

**考虑：** 使用真实组件的集成测试（`@DataJpaTest` + TestContainers / `@WebMvcTest` slice）往往比复杂的 mock 更简单

## TDD 如何防止这些反模式

**TDD 有帮助的原因：**
1. **先写测试** → 迫使你思考你到底在测什么
2. **看它失败** → 确认测试测的是真实行为，不是 mock
3. **最少实现** → 仅测试用方法不会混入
4. **真实依赖** → 你在 mock 之前看到测试实际需要什么

**如果你在测试 mock 行为，你违反了 TDD** ——你在没有先用真实代码让测试失败的情况下就加了 mock。

## 快速参考

| 反模式 | 修复方式 |
|--------|----------|
| `verify(mock).xxx(...)` 当核心断言 | 测真实结果（事件、状态、返回值）or 用 fake / InMemory* |
| 生产代码中的仅测试用方法 | 模块内私有移 `src/test/java/.../testutil/`；跨 sourceSet/模块共享移 `src/testFixtures/java/.../` |
| 不理解就 mock | 先理解依赖，最少 mock |
| 不完整的 mock | 完整镜像真实数据结构 |
| 测试作为事后补充 | TDD——先写测试 |
| 过于复杂的 mock | 改 `@DataJpaTest` / `@WebMvcTest` slice + TestContainers |

## 危险信号

- 断言只检查 `verify(mock).xxx(...)` 而没有真实结果断言
- 方法仅在测试文件中被调用
- Mock setup 占测试的 >50%
- 移除 mock 测试就失败（说明在测试 mock 本身）
- 无法解释为什么需要 mock
- "保险起见" mock 掉
- 用 H2 代替真实 PostgreSQL "因为更快"

## 底线

**Mock 是隔离的工具，不是被测试的对象。**

如果 TDD 揭示你在测试 mock 行为，你已经走偏了。

修复方法：测试真实行为，或质疑为什么要 mock。
