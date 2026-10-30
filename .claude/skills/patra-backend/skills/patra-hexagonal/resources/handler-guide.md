# CommandHandler 与事务管理指南

## Handler 标准模板

```java
@Component
@RequiredArgsConstructor
public class CreateUserHandler implements CommandHandler<CreateUserCommand, UserId> {

    private final UserRepository userRepository;
    private final TransactionOperations transactions;

    @Override
    public UserId handle(CreateUserCommand command) {
        User user = User.create(command.name(), command.email());
        return transactions.execute(status -> userRepository.save(user).getId());
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

完整规则见 `.claude/rules/tech/transaction.md`。要点：写命令 Handler 注入 `TransactionOperations`，`handle()` 上**不加** `@Transactional`；事务只包「加锁 → 锁内复核 → 写库」，远程调用、发信、Redis、文件都在事务外。

### 标准写法

```java
// identity 的 UnbanUserHandler
@Override
public Void handle(UnbanUserCommand command) {
    transactions.executeWithoutResult(status -> {
        User user = users.findById(command.userId()).orElseThrow(UserNotFoundException::new);
        user.unban();
        users.save(user);
    });
    log.info("前台用户已解封: userId={}", command.userId());
    return null;
}
```

### 有外部调用：事务只包写库段

```java
// identity 的 ChangeAvatarHandler（节选）
@Override
public Avatar handle(ChangeAvatarCommand command) {
    long userId = currentUserPort.require().userId();
    // 无事务：渲染图片、写对象存储
    AvatarRenditions renditions = images.render(command.content());
    Avatar avatar = Avatar.forContent(userId, renditions.large());
    storage.store(avatar, renditions);

    // 事务只包「锁用户行 → 复核状态 → 换头像」
    Optional<Avatar> previous;
    try {
        previous = transactions.execute(status -> swap(userId, avatar));
    } catch (RuntimeException e) {
        deleteQuietly(avatar, "提交失败后删除新头像对象失败");   // 提交失败：补偿删掉刚写的对象
        throw e;
    }
    // 无事务：提交后再删旧对象
    previous.filter(old -> !old.equals(avatar)).ifPresent(old -> deleteQuietly(old, "删除旧头像对象失败"));
    return avatar;
}
```

不要用「同类里另写一个 `@Transactional` 方法再自己调用」来拆事务：自调用不走代理，事务不生效。

### 声明式注解的使用范围

| 写法 | 使用场景 |
|------|---------|
| `@Transactional(readOnly = true)` | QueryService 里要在同一会话多次读 JPA 实体的方法（方法里不能有远程调用） |
| `@Transactional(propagation = MANDATORY)` | 必须在调用方事务里执行的方法：加锁、`FOR UPDATE` 读取、被 Handler 在事务里调用的 Gateway（infra 也允许） |
| `@Transactional(propagation = REQUIRES_NEW, rollbackFor = Exception.class)` | 独立 bean 的逐条落库 Persister；调用方不得持有事务 |

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

**特点**：无接口定义、默认不加事务（需要同一会话多次读 JPA 实体时才加 `readOnly`）、返回 `PageResult<ReadModel>`。

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
