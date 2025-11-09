---
name: patra-tdd-development
description: Patra 项目 TDD（测试驱动开发）专家。基于六边形架构+DDD实践TDD。核心：先写测试，再写实现，Red-Green-Refactor循环。当需要开发新功能、添加业务逻辑、重构代码时使用。关键词：TDD、测试先行、Red-Green-Refactor、小步迭代、六边形架构、领域建模。
allowed-tools: Read, Edit, Write, Grep, Glob, Bash, mcp__sequential-thinking__sequentialthinking, mcp__serena__get_symbols_overview, mcp__serena__find_symbol, mcp__context7__resolve-library-id, mcp__context7__get-library-docs, mcp__ide__getDiagnostics, WebSearch, WebFetch, TodoWrite, KillShell
---

# Patra TDD 开发专家

## 🎯 TDD 核心理念

**测试驱动开发（TDD）= 先写测试，再写实现**

### TDD 的价值

1. **清晰的需求定义** - 测试即需求文档
2. **设计先行** - 测试驱动更好的 API 设计
3. **持续反馈** - 立即知道代码是否正确
4. **重构信心** - 有测试保护，放心重构
5. **YAGNI 原则** - 只写让测试通过的代码

### TDD 与六边形架构的结合

```
外层 → 内层开发顺序（Outside-In TDD）：
1. Controller (测试 API 契约) → 定义外部接口
2. Orchestrator (测试编排逻辑) → 定义应用层接口
3. Domain (测试业务逻辑) → 实现核心领域
4. Repository (测试数据访问) → 实现持久化
```

---

## 🔄 TDD 循环：Red-Green-Refactor

### 第一步：🔴 Red - 编写失败的测试

**目标**：定义期望行为，验证测试会失败

```java
@Test
@DisplayName("创建文章时应该生成唯一ID")
void should_generate_unique_id_when_create_article() {
    // given
    var title = "测试文章";
    var content = "测试内容";

    // when
    var article = Article.create(title, content);

    // then
    assertThat(article.getId()).isNotNull();
    assertThat(article.getTitle()).isEqualTo(title);
}
```

**检查点**：
- ✅ 测试编译失败 (Article 类不存在) 或
- ✅ 测试运行失败 (方法未实现)
- ⚠️ 如果测试通过，说明没有测试新行为

### 第二步：🟢 Green - 最简实现让测试通过

**目标**：用最简单的方式让测试通过

```java
public class Article {
    private Long id;
    private String title;
    private String content;

    private Article(Long id, String title, String content) {
        this.id = id;
        this.title = title;
        this.content = content;
    }

    public static Article create(String title, String content) {
        // 最简实现：生成 ID
        Long id = System.currentTimeMillis();
        return new Article(id, title, content);
    }

    // getters
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
}
```

**检查点**：
- ✅ 所有测试通过
- ⚠️ 不追求完美实现，只求测试通过

### 第三步：🔵 Refactor - 重构优化

**目标**：在测试保护下优化代码

```java
// 重构：使用更好的 ID 生成策略
public static Article create(String title, String content) {
    Long id = IdGenerator.nextId(); // 重构：引入 ID 生成器
    return new Article(id, title, content);
}

// 重构：添加验证
public static Article create(String title, String content) {
    Objects.requireNonNull(title, "标题不能为空");
    Objects.requireNonNull(content, "内容不能为空");

    Long id = IdGenerator.nextId();
    return new Article(id, title, content);
}
```

**检查点**：
- ✅ 所有测试仍然通过
- ✅ 代码更清晰、更易维护

---

## 📚 六边形架构各层的 TDD 实践

### Domain 层 TDD（纯业务逻辑）

**特点**：无依赖，纯单元测试，快速反馈

#### 示例：开发 Article 聚合根

**迭代 1：创建文章**

```java
// 🔴 Red: 先写测试
@Test
@DisplayName("创建文章时应该设置状态为草稿")
void should_set_status_to_draft_when_create() {
    // when
    var article = Article.create("标题", "内容");

    // then
    assertThat(article.getStatus()).isEqualTo(ArticleStatus.DRAFT);
}

// 🟢 Green: 实现
public enum ArticleStatus { DRAFT, PUBLISHED, ARCHIVED }

public class Article {
    private ArticleStatus status;

    public static Article create(String title, String content) {
        // ... 之前的代码
        article.status = ArticleStatus.DRAFT;
        return article;
    }

    public ArticleStatus getStatus() { return status; }
}

// 🔵 Refactor: (暂无需重构)
```

**迭代 2：发布文章**

```java
// 🔴 Red: 先写测试
@Test
@DisplayName("发布文章时应该设置发布时间")
void should_set_published_time_when_publish() {
    // given
    var article = Article.create("标题", "内容");

    // when
    article.publish();

    // then
    assertThat(article.getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
    assertThat(article.getPublishedAt()).isNotNull();
}

@Test
@DisplayName("已发布的文章不能重复发布")
void should_throw_exception_when_publish_already_published_article() {
    // given
    var article = Article.create("标题", "内容");
    article.publish();

    // when & then
    assertThatThrownBy(() -> article.publish())
        .isInstanceOf(ArticleException.class)
        .hasMessage("文章已发布");
}

// 🟢 Green: 实现
public class Article {
    private LocalDateTime publishedAt;

    public void publish() {
        if (status == ArticleStatus.PUBLISHED) {
            throw new ArticleException("文章已发布");
        }
        this.status = ArticleStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public LocalDateTime getPublishedAt() { return publishedAt; }
}

// 🔵 Refactor: 引入 Clock 便于测试
public void publish(Clock clock) {
    if (status == ArticleStatus.PUBLISHED) {
        throw new ArticleException("文章已发布");
    }
    this.status = ArticleStatus.PUBLISHED;
    this.publishedAt = LocalDateTime.now(clock);
}
```

### Application 层 TDD（编排逻辑）

**特点**：使用 Mock，测试编排流程

#### 示例：开发 ArticleOrchestrator

**迭代 1：创建文章编排**

```java
// 🔴 Red: 先写测试
@ExtendWith(MockitoExtension.class)
class ArticleOrchestratorTest {

    @Mock
    private ArticlePort articlePort;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ArticleOrchestrator orchestrator;

    @Test
    @DisplayName("创建文章应该保存并发布事件")
    void should_save_and_publish_event_when_create_article() {
        // given
        var command = new CreateArticleCommand("标题", "内容");
        when(articlePort.save(any(Article.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        var result = orchestrator.createArticle(command);

        // then
        assertThat(result.getId()).isNotNull();

        InOrder inOrder = inOrder(articlePort, eventPublisher);
        inOrder.verify(articlePort).save(any(Article.class));
        inOrder.verify(eventPublisher).publish(any(ArticleCreatedEvent.class));
    }
}

// 🟢 Green: 实现
@Service
public class ArticleOrchestrator {

    private final ArticlePort articlePort;
    private final EventPublisher eventPublisher;

    public ArticleOrchestrator(ArticlePort articlePort, EventPublisher eventPublisher) {
        this.articlePort = articlePort;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ArticleResult createArticle(CreateArticleCommand command) {
        // 创建领域对象
        Article article = Article.create(command.title(), command.content());

        // 保存
        article = articlePort.save(article);

        // 发布事件
        eventPublisher.publish(new ArticleCreatedEvent(article.getId()));

        return new ArticleResult(article.getId(), article.getStatus());
    }
}

// 🔵 Refactor: (当前实现已清晰)
```

**迭代 2：添加权限检查**

```java
// 🔴 Red: 先写测试
@Test
@DisplayName("创建文章时应该检查用户权限")
void should_check_permission_when_create_article() {
    // given
    var command = new CreateArticleCommand("标题", "内容");
    when(permissionPort.hasPermission(any(), eq("ARTICLE_CREATE"))).thenReturn(false);

    // when & then
    assertThatThrownBy(() -> orchestrator.createArticle(command))
        .isInstanceOf(PermissionDeniedException.class);

    verify(articlePort, never()).save(any());
}

// 🟢 Green: 添加实现
@Mock
private PermissionPort permissionPort;

public ArticleResult createArticle(CreateArticleCommand command) {
    // 权限检查
    if (!permissionPort.hasPermission(command.userId(), "ARTICLE_CREATE")) {
        throw new PermissionDeniedException("无创建文章权限");
    }

    // ... 原有逻辑
}
```

### Infrastructure 层 TDD（数据访问）

**特点**：集成测试，使用 TestContainers

#### 示例：开发 ArticleRepositoryImpl

```java
// 🔴 Red: 先写测试（集成测试）
@DataJpaTest
@Testcontainers
@Import({ArticleRepositoryImpl.class, MapperConfig.class})
class ArticleRepositoryImplIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
    }

    @Autowired
    private ArticleRepositoryImpl repository;

    @Test
    @DisplayName("保存文章后应该能查询到")
    void should_find_article_after_save() {
        // given
        var article = Article.create("测试标题", "测试内容");

        // when
        var saved = repository.save(article);
        var found = repository.findById(saved.getId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("测试标题");
    }
}

// 🟢 Green: 实现
@Repository
public class ArticleRepositoryImpl implements ArticlePort {

    private final ArticleMapper mapper;
    private final ArticleConverter converter;

    @Override
    public Article save(Article article) {
        ArticleEntity entity = converter.toEntity(article);
        mapper.insert(entity);
        return converter.toDomain(entity);
    }

    @Override
    public Optional<Article> findById(Long id) {
        ArticleEntity entity = mapper.selectById(id);
        return Optional.ofNullable(entity)
            .map(converter::toDomain);
    }
}
```

### Adapter 层 TDD（API 接口）

**特点**：使用 MockMvc，测试 HTTP 契约

#### 示例：开发 ArticleController

```java
// 🔴 Red: 先写测试
@WebMvcTest(ArticleController.class)
class ArticleControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ArticleOrchestrator orchestrator;

    @Test
    @DisplayName("创建文章应该返回201和文章ID")
    void should_return_201_when_create_article() throws Exception {
        // given
        var request = """
            {
                "title": "测试标题",
                "content": "测试内容"
            }
            """;
        var result = new ArticleResult(1L, ArticleStatus.DRAFT);
        when(orchestrator.createArticle(any())).thenReturn(result);

        // when & then
        mockMvc.perform(post("/api/v1/articles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("标题为空时应该返回400")
    void should_return_400_when_title_is_empty() throws Exception {
        // given
        var request = """
            {
                "title": "",
                "content": "测试内容"
            }
            """;

        // when & then
        mockMvc.perform(post("/api/v1/articles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest());
    }
}

// 🟢 Green: 实现
@RestController
@RequestMapping("/api/v1/articles")
public class ArticleController {

    private final ArticleOrchestrator orchestrator;

    @PostMapping
    public ResponseEntity<ArticleResponse> createArticle(
            @Valid @RequestBody CreateArticleRequest request) {

        var command = new CreateArticleCommand(request.title(), request.content());
        var result = orchestrator.createArticle(command);

        var response = new ArticleResponse(result.getId(), result.getStatus().name());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

record CreateArticleRequest(
    @NotBlank(message = "标题不能为空") String title,
    @NotBlank(message = "内容不能为空") String content
) {}
```

---

## 🎯 TDD 最佳实践

### 1. 小步前进

**❌ 错误：一次写太多**
```java
// 同时测试多个行为
@Test
void should_create_publish_and_archive_article() { ... }
```

**✅ 正确：每次一个测试**
```java
@Test
void should_create_article() { ... }

@Test
void should_publish_article() { ... }

@Test
void should_archive_article() { ... }
```

### 2. 测试行为，不是实现

**❌ 错误：测试实现细节**
```java
@Test
void should_call_repository_save_method() {
    orchestrator.createArticle(command);
    verify(repository).save(any()); // 测试实现
}
```

**✅ 正确：测试行为结果**
```java
@Test
void should_save_article_when_create() {
    var result = orchestrator.createArticle(command);
    assertThat(result.getId()).isNotNull(); // 测试行为
}
```

### 3. 只 Mock 外部依赖

**Domain 层**：不 Mock（纯业务逻辑）
**Application 层**：Mock Ports（外部接口）
**Infrastructure 层**：不 Mock（真实集成测试）
**Adapter 层**：Mock Orchestrator（应用服务）

### 4. 测试命名清晰

**使用 BDD 风格：should_[expected]_when_[condition]**

```java
@Test
@DisplayName("库存不足时应该抛出异常")
void should_throw_exception_when_inventory_insufficient() { ... }

@Test
@DisplayName("用户未登录时应该返回401")
void should_return_401_when_user_not_authenticated() { ... }
```

### 5. 先写最简单的测试

```java
// 第一个测试：最简单的正常路径
@Test
void should_create_article() { ... }

// 第二个测试：边界条件
@Test
void should_throw_exception_when_title_is_null() { ... }

// 第三个测试：复杂场景
@Test
void should_publish_event_after_save() { ... }
```

---

## 🚨 TDD 常见陷阱

### 陷阱 1：跳过 Red 阶段

**问题**：直接写实现，后补测试
**后果**：测试可能没有真正验证代码

**解决**：
```bash
# 严格遵循循环
1. 写测试 → 运行 (看到失败)
2. 写实现 → 运行 (看到通过)
3. 重构 → 运行 (确保仍通过)
```

### 陷阱 2：测试过度耦合实现

**问题**：测试依赖实现细节
**后果**：重构时测试大量失败

**解决**：
- 测试公共 API，不测试私有方法
- 测试行为结果，不验证内部调用

### 陷阱 3：测试过于复杂

**问题**：一个测试验证多个行为
**后果**：失败时难以定位问题

**解决**：
- 一个测试只验证一个行为
- 使用 Test Data Builder 简化测试数据构建

### 陷阱 4：忽略重构阶段

**问题**：测试通过后就停止
**后果**：代码质量下降，技术债累积

**解决**：
- 每个绿色阶段后检查是否需要重构
- 消除重复代码
- 改善命名和结构

---

## 📋 TDD 开发检查清单

### 每个新功能开发前

```
[ ] 1. 理解需求 - 明确期望行为
[ ] 2. 识别层级 - Domain/Application/Infrastructure/Adapter
[ ] 3. 写第一个测试 - 最简单的正常路径
[ ] 4. 确认测试失败 - Red 阶段
```

### 实现过程中

```
[ ] 5. 最简实现 - 让测试通过（Green）
[ ] 6. 运行所有测试 - 确保没有破坏已有功能
[ ] 7. 重构优化 - 改善代码质量（Refactor）
[ ] 8. 再次运行测试 - 确保重构正确
[ ] 9. 添加下一个测试 - 回到步骤 3
```

### 完成后

```
[ ] 10. 覆盖率检查 - 确保关键路径都有测试
[ ] 11. 边界条件 - 补充异常场景测试
[ ] 12. 代码审查 - 检查测试质量
```

---

## 📖 详细资源

需要深入了解时，查看以下资源：

- [tdd-workflow.md](resources/tdd-workflow.md) - TDD 详细工作流程
- [tdd-domain-examples.md](resources/tdd-domain-examples.md) - Domain 层 TDD 示例
- [tdd-application-examples.md](resources/tdd-application-examples.md) - Application 层 TDD 示例
- [tdd-infrastructure-examples.md](resources/tdd-infrastructure-examples.md) - Infrastructure 层 TDD 示例
- [tdd-adapter-examples.md](resources/tdd-adapter-examples.md) - Adapter 层 TDD 示例
- [tdd-best-practices.md](resources/tdd-best-practices.md) - TDD 最佳实践

---

## 💡 核心要记住

1. **Red-Green-Refactor** - 永远遵循这个循环
2. **测试先行** - 先写测试，定义期望行为
3. **小步前进** - 每次只添加一个测试
4. **YAGNI** - 只实现让测试通过的代码
5. **持续重构** - 在测试保护下改善代码质量

**TDD 不仅是测试，更是一种设计方法论！**
