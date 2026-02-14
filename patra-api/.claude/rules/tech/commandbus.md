---
paths: patra-*/patra-*-app/**/*.java, patra-*/patra-*-adapter/**/*.java
---

# CommandBus 使用规范

## 概述

CommandBus 是 Adapter 层与 Application 层之间的**统一分发中心**，替代直接注入多个 UseCase 接口的模式。

## 架构图

```
Adapter 层（Controller/Job/Listener）
    ↓ 只注入 CommandBus
CommandBus
    ↓ 拦截器链（Tracing → Logging → Metrics）
    ↓ 自动路由
CommandHandler（Application 层）
    ↓ 调用
Domain + Infrastructure
```

## 核心组件

| 组件 | 位置 | 说明 |
|------|------|------|
| `Command<R>` | patra-common-core | 命令标记接口，泛型 R 为返回类型 |
| `CommandHandler<C,R>` | patra-common-core | 命令处理器接口 |
| `CommandBus` | patra-common-core | 命令总线接口 |
| `CommandInterceptor` | patra-common-core | 拦截器接口 |
| `SimpleCommandBus` | patra-spring-boot-starter-core | Spring 实现 |

## 使用方式

### 1. 定义 Command（Application 层）

```java
// 使用 Record 确保不可变性
public record CreateUserCommand(
    String name,
    String email
) implements Command<UserId> {

    public CreateUserCommand {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(email, "email must not be null");
    }
}
```

### 2. 实现 CommandHandler（Application 层）

```java
@Component
@RequiredArgsConstructor
public class CreateUserHandler implements CommandHandler<CreateUserCommand, UserId> {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public UserId handle(CreateUserCommand command) {
        User user = User.create(command.name(), command.email());
        return userRepository.save(user).getId();
    }
}
```

### 3. 在 Adapter 层使用 CommandBus

```java
@RestController
@RequiredArgsConstructor
public class UserController {

    private final CommandBus commandBus;  // 唯一注入

    @PostMapping("/users")
    public UserId createUser(@RequestBody CreateUserRequest request) {
        return commandBus.handle(new CreateUserCommand(
            request.name(),
            request.email()
        ));
    }
}
```

## 命名约定

| 组件 | 命名规则 | 示例 |
|------|---------|------|
| Command | `{Action}{Entity}Command` | `CreateUserCommand`, `ImportVenueCommand` |
| Handler | `{Action}{Entity}Handler` | `CreateUserHandler`, `ImportVenueHandler` |
| Result | `{Action}{Entity}Result` | `CreateUserResult`, `ImportVenueResult` |

## 内置拦截器

| 拦截器 | Order | 条件 | 说明 |
|--------|-------|------|------|
| `TracingCommandInterceptor` | 50 | ObservationRegistry 存在 | 分布式追踪 |
| `LoggingCommandInterceptor` | 100 | 默认启用 | 日志记录 |
| `MetricsCommandInterceptor` | 200 | MeterRegistry 存在 | 指标采集 |

## 配置

```yaml
patra:
  command-bus:
    async:
      core-pool-size: 4
      max-pool-size: 16
      queue-capacity: 100
    interceptors:
      logging: true
      metrics: true
      tracing: true
```

## 禁止行为

1. 禁止在 Adapter 层直接注入 Handler（应使用 CommandBus）
2. 禁止在 Command 中包含业务逻辑（仅做数据载体）
3. 禁止在 Handler 中调用其他 Handler（使用事件驱动）
4. 禁止让 Handler 依赖框架特定类（保持可测试性）

## 与旧模式的关系

CommandBus 模式已完全取代 Orchestrator/UseCase 模式：

| 维度 | 旧模式（Orchestrator/UseCase） | 当前模式（CommandBus） |
|------|-------------------------------|----------------------|
| Adapter 依赖 | 多个 UseCase 接口 | 单个 CommandBus |
| 调用方式 | `orchestrator.execute(cmd)` | `bus.handle(cmd)` |
| 入口点命名 | `*Orchestrator`, `*UseCase` | `*Handler` |
| 横切关注点 | 需要 AOP | 内置拦截器链 |
| 异步支持 | 需要额外处理 | `handleAsync()` 内置 |

> **注意**：项目中不再使用 Orchestrator 命名。Handler 内部可使用 `*Phase` 表示执行阶段。

## 测试策略

### Handler 单元测试

```java
@Test
void should_create_user() {
    // given
    var repository = mock(UserRepository.class);
    var handler = new CreateUserHandler(repository);
    var command = new CreateUserCommand("John", "john@example.com");

    when(repository.save(any())).thenAnswer(inv -> {
        User user = inv.getArgument(0);
        return user.withId(UserId.of(1L));
    });

    // when
    UserId result = handler.handle(command);

    // then
    assertThat(result).isEqualTo(UserId.of(1L));
}
```

### Adapter 层测试（Mock CommandBus）

```java
@WebMvcTest(UserController.class)
class UserControllerTest {

    @MockitoBean
    private CommandBus commandBus;

    @Test
    void should_delegate_to_command_bus() {
        // given
        when(commandBus.handle(any(CreateUserCommand.class)))
            .thenReturn(UserId.of(1L));

        // when & then
        mockMvc.perform(post("/users")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"name": "John", "email": "john@example.com"}
                    """))
            .andExpect(status().isOk());
    }
}
```

## Query 端处理策略

在 CQRS 架构中，**查询（Query）操作不需要通过 CommandBus**。这是设计决策：

| 操作类型 | 路由方式 | 说明 |
|---------|---------|------|
| 写操作（Command） | `CommandBus.handle()` | 需要拦截器链（日志、追踪、指标、事务） |
| 读操作（Query） | 直接注入 Service | 无副作用，不需要复杂的横切关注点 |

### 查询服务示例

```java
// 查询服务直接注入，不走 CommandBus
@Service
@RequiredArgsConstructor
public class VenueQueryService {

    private final VenueReadPort venueReadPort;

    /// 查询 Venue 分页列表。
    public PageResult<VenueSummaryReadModel> listVenues(VenueListQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        PagingParams paging = PagingParams.normalize(query.page(), query.pageSize());
        String keyword = normalizeKeyword(query.q());
        return venueReadPort.findVenuePage(paging, keyword);
    }
}
```

### Adapter 层混合使用

```java
@RestController
@RequiredArgsConstructor
public class VenueController {

    private final CommandBus commandBus;               // 写操作
    private final VenueQueryService venueQueryService; // 读操作
    private final VenueApiConverter venueApiConverter;  // 转换器

    @PostMapping("/venues")
    public VenueId create(@RequestBody CreateVenueRequest request) {
        return commandBus.handle(new CreateVenueCommand(request.name()));
    }

    @GetMapping("/venues")
    public PageResult<VenueItemResponse> listVenues(VenueListRequest request) {
        VenueListQuery query = venueApiConverter.toQuery(request);
        return venueQueryService.listVenues(query).map(venueApiConverter::toItemResponse);
    }
}
```

## 特殊约定

### Void 返回类型

对于无返回值的命令，使用 `Void` 作为泛型参数，Handler 返回 `null`：

```java
public record DeleteUserCommand(UserId userId) implements Command<Void> {}

@Component
public class DeleteUserHandler implements CommandHandler<DeleteUserCommand, Void> {

    @Override
    @Transactional
    public Void handle(DeleteUserCommand command) {
        userRepository.delete(command.userId());
        return null;  // Void 类型返回 null
    }
}
```

### Command 字段允许为 null 的场景

某些 Command 的字段允许为 null，由 Handler 内部回退到默认值。这种设计适用于：

1. **配置覆盖场景**：字段为 null 时使用系统配置的默认值
2. **可选参数场景**：字段为 null 表示使用默认行为

```java
// OutboxRelayCommand 示例：所有字段可为 null，Handler 回退到配置默认值
public record OutboxRelayCommand(
    @Nullable Integer batchSize,      // null 时使用配置的 outbox.batch-size
    @Nullable Duration lockTimeout    // null 时使用配置的 outbox.lock-timeout
) implements Command<RelayResult> {
    // 无参数验证 - 设计意图是允许 null
}
```

对于这类 Command，**必须在类 JavaDoc 中说明回退策略**：

```java
/**
 * Outbox 中继命令。
 *
 * <p>所有参数均可为 null，Handler 会回退到 {@code OutboxProperties} 中的配置默认值。
 * 这允许调用方仅覆盖需要调整的参数。
 */
public record OutboxRelayCommand(...) implements Command<RelayResult> {}
```
