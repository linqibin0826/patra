# CommandHandler 与事务管理指南

## 目录

- Handler 标准模板
- Command 定义模板
- 事务管理模式
- QueryService 模板
- Handler 单元测试模板
- Adapter 层测试

## Handler 标准模板

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

## Command 定义模板

```java
/// 创建用户命令。
public record CreateUserCommand(
    @NotNull String name,
    @NotNull String email
) implements Command<UserId> {

    public CreateUserCommand {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(email, "email must not be null");
    }
}
```

## 事务管理模式

### 标准事务（大多数 Handler）

```java
@Override
@Transactional  // 默认 REQUIRED
public CreatePlanResult handle(CreatePlanCommand command) {
    PlanAggregate plan = assemblePlan(command);
    planRepository.save(plan);
    outboxRepository.save(createOutboxMessage(plan));  // 同一事务
    return new CreatePlanResult(plan.getId());
}
```

### 外部 API 调用：拆分事务

**绝不在 @Transactional 内调用外部 API**（事务过长 → 连接池耗尽）。

```java
@Override
public HarvestResult handle(HarvestCommand command) {
    // 事务 1: 创建计划
    PlanAggregate plan = createPlan(command);

    // 无事务: 调用外部 API
    SearchResult results = pubmedSearchPort.search(plan.getQuery());

    // 事务 2: 保存结果
    saveResults(results);

    return new HarvestResult(plan.getId());
}

@Transactional
protected PlanAggregate createPlan(HarvestCommand command) {
    return planRepository.save(assemblePlan(command));
}

@Transactional
protected void saveResults(SearchResult results) {
    publicationRepository.saveAll(results.publications());
}
```

### 事务传播行为选择

| 传播行为 | 使用场景 |
|---------|---------|
| **REQUIRED**（默认） | 大多数 Handler |
| **REQUIRES_NEW** | 审计日志、独立操作（即使主事务回滚也要提交） |
| **MANDATORY** | 内部 Phase 组件（必须在事务内调用） |
| **readOnly=true** | QueryService 的查询方法 |

## QueryService 模板

```java
@Service
@RequiredArgsConstructor
public class VenueQueryService {

    private final VenueReadPort venueReadPort;

    /// 查询 Venue 分页列表。
    public PageResult<VenueSummaryReadModel> listVenues(VenueListQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        PagingParams paging = PagingParams.normalize(query.page(), query.pageSize());
        VenueFilter filter = VenueFilter.builder()
            .keyword(trimToNull(query.q()))
            .provenanceCode(trimToNull(query.provenanceCode()))
            .build();
        return venueReadPort.findVenuePage(paging, filter);
    }
}
```

**特点**：无接口定义、无 @Transactional（只读）、返回 `PageResult<ReadModel>`。

## Handler 单元测试模板

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

## Adapter 层测试（Mock CommandBus）

```java
@WebMvcTest
@Import(UserController.class)
@AutoConfigureRestTestClient
class UserControllerIT {

    @Autowired
    private RestTestClient restClient;

    @MockitoBean
    private CommandBus commandBus;

    @Test
    void should_delegate_to_command_bus() {
        when(commandBus.handle(any(CreateUserCommand.class)))
            .thenReturn(UserId.of(1L));

        restClient.post().uri("/users")
            .contentType(APPLICATION_JSON)
            .bodyValue("""
                {"name": "John", "email": "john@example.com"}
                """)
            .exchange()
            .expectStatus().isOk();
    }
}
```
