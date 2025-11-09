# Patra 测试基础设施快速上手指南

**版本**: 1.0.0
**日期**: 2025-11-09
**适用对象**: Patra 项目开发者

---

## 🎯 5 分钟快速上手

### 步骤 1: 添加依赖

**在领域层/应用层/基础设施层/适配器层的 pom.xml**:

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-common-test</artifactId>
    <scope>test</scope>
</dependency>
```

**在 boot 模块的 pom.xml**:

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

### 步骤 2: 编写第一个单元测试

```java
// Domain 层单元测试
class TaskAggregateTest extends BaseUnitTest {

    @Test
    void testCreateTask() {
        // given
        Task task = new Task("完成需求文档");

        // when
        task.markAsCompleted();

        // then
        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        DomainAssertions.assertDomainEventPublished(task, TaskCompletedEvent.class);
    }
}
```

### 步骤 3: 编写第一个集成测试

```java
// Infrastructure 层集成测试
class TaskRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void testSaveAndFindTask() {
        // given
        Task task = new Task("集成测试任务");

        // when
        taskRepository.save(task);

        // then
        Optional<Task> found = taskRepository.findById(task.getId());
        assertThat(found).isPresent();
    }
}
```

### 步骤 4: 编写第一个 E2E 测试

```java
// Adapter 层 E2E 测试
class TaskControllerE2E extends BaseE2ETest {

    @Test
    void testCreateAndQueryTask() throws Exception {
        // 1. 创建任务
        TaskCreateRequest request = new TaskCreateRequest("E2E 测试任务");

        MvcResult createResult = performPost("/api/v1/tasks", request)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andReturn();

        String taskId = extractJsonValue(createResult, "$.id");

        // 2. 查询任务
        performGet("/api/v1/tasks/{id}", taskId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("E2E 测试任务"));
    }
}
```

### 步骤 5: 配置容器复用(本地开发)

**文件位置**: `~/.testcontainers.properties`

```properties
# 启用容器复用,加快本地测试速度
testcontainers.reuse.enable=true
```

**运行测试**:

```bash
# 首次运行(启动容器,较慢)
mvn test -Dtest=TaskRepositoryIT

# 后续运行(复用容器,快速)
mvn test -Dtest=TaskRepositoryIT  # 约 1 秒完成
```

---

## 📚 详细使用指南

### 1. 单元测试指南

#### 1.1 Domain 层单元测试

**目标**: 测试聚合根、实体、值对象的业务逻辑

**特点**:
- ✅ 不依赖 Spring 框架
- ✅ 不依赖数据库
- ✅ 使用 Mockito Mock 仓储接口
- ✅ 测试覆盖率要求 ≥ 80%

**示例**:

```java
class ArticleAggregateTest extends BaseUnitTest {

    @Test
    void testPublishArticle() {
        // given
        Article article = new Article("测试文章", "内容");
        assertThat(article.getStatus()).isEqualTo(ArticleStatus.DRAFT);

        // when
        article.publish();

        // then
        assertThat(article.getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
        DomainAssertions.assertDomainEventPublished(article, ArticlePublishedEvent.class);
    }

    @Test
    void testCannotPublishEmptyArticle() {
        // given
        Article article = new Article("标题", "");

        // when & then
        assertThatThrownBy(() -> article.publish())
            .isInstanceOf(InvalidArticleException.class)
            .hasMessageContaining("文章内容不能为空");
    }
}
```

#### 1.2 Application 层单元测试

**目标**: 测试 Orchestrator 和 Coordinator 的业务流程编排

**特点**:
- ✅ 使用 @Mock 模拟依赖
- ✅ 使用 @InjectMocks 注入被测试对象
- ✅ 验证方法调用和参数

**示例**:

```java
class ArticleOrchestra extends BaseUnitTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ArticleOrchestrator articleOrchestrator;

    @Test
    void testCreateArticle() {
        // given
        CreateArticleCommand command = new CreateArticleCommand("标题", "内容");

        when(articleRepository.nextId()).thenReturn(new ArticleId("article-001"));

        // when
        Article article = articleOrchestrator.createArticle(command);

        // then
        assertThat(article.getTitle()).isEqualTo("标题");
        verify(articleRepository, times(1)).save(any(Article.class));
        verify(eventPublisher, times(1)).publish(any(ArticleCreatedEvent.class));
    }
}
```

---

### 2. 集成测试指南

#### 2.1 Repository 集成测试

**目标**: 测试仓储实现与数据库的交互

**特点**:
- ✅ 使用真实的数据库 (TestContainers MySQL)
- ✅ @Transactional 自动回滚测试数据
- ✅ 测试 CRUD 操作、自定义查询

**示例**:

```java
class ArticleRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private ArticleRepository articleRepository;

    @Test
    void testSaveAndFindArticle() {
        // given
        Article article = new Article("集成测试文章", "内容");

        // when
        articleRepository.save(article);

        // then
        Optional<Article> found = articleRepository.findById(article.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("集成测试文章");
    }

    @Test
    void testCustomQuery() {
        // given
        Article article1 = new Article("文章1", "内容");
        Article article2 = new Article("文章2", "内容");
        article2.publish();

        articleRepository.save(article1);
        articleRepository.save(article2);

        // when
        List<Article> publishedArticles = articleRepository.findByStatus(ArticleStatus.PUBLISHED);

        // then
        assertThat(publishedArticles).hasSize(1);
        assertThat(publishedArticles.get(0).getTitle()).isEqualTo("文章2");
    }

    @Test
    void testPagination() {
        // given
        IntStream.range(0, 25).forEach(i ->
            articleRepository.save(new Article("文章" + i, "内容"))
        );

        // when
        Page<Article> firstPage = articleRepository.findAll(PageRequest.of(0, 10));
        Page<Article> secondPage = articleRepository.findAll(PageRequest.of(1, 10));

        // then
        assertThat(firstPage.getTotalElements()).isEqualTo(25);
        assertThat(firstPage.getContent()).hasSize(10);
        assertThat(secondPage.getContent()).hasSize(10);
    }
}
```

#### 2.2 Orchestrator 集成测试

**目标**: 测试应用层编排逻辑与真实依赖的集成

**特点**:
- ✅ 使用真实的 Spring Context
- ✅ 使用真实的 Repository 和数据库
- ✅ 验证事务边界和事件发布

**示例**:

```java
class ArticleOrchestratorIT extends BaseIntegrationTest {

    @Autowired
    private ArticleOrchestrator articleOrchestrator;

    @Autowired
    private ArticleRepository articleRepository;

    @Test
    void testCreateArticleWithRealDependencies() {
        // given
        CreateArticleCommand command = new CreateArticleCommand("集成测试文章", "内容");

        // when
        Article article = articleOrchestrator.createArticle(command);

        // then
        assertThat(article.getId()).isNotNull();
        assertThat(articleRepository.findById(article.getId())).isPresent();
    }

    @Test
    void testTransactionalBehavior() {
        // given
        CreateArticleCommand command = new CreateArticleCommand("", "内容");  // 无效标题

        // when & then
        assertThatThrownBy(() -> articleOrchestrator.createArticle(command))
            .isInstanceOf(InvalidArticleException.class);

        // 验证事务回滚,数据库中没有数据
        assertThat(articleRepository.findAll()).isEmpty();
    }
}
```

---

### 3. E2E 测试指南

#### 3.1 Controller E2E 测试

**目标**: 测试完整的 HTTP 请求/响应流程

**特点**:
- ✅ 使用 MockMvc 模拟 HTTP 请求
- ✅ 启动完整的 Spring Boot 应用
- ✅ 验证 Controller → Application → Domain → Infrastructure 的完整链路

**示例**:

```java
class ArticleControllerE2E extends BaseE2ETest {

    @Test
    void testArticleLifecycle() throws Exception {
        // 1. 创建文章
        CreateArticleRequest createRequest = new CreateArticleRequest("E2E 测试文章", "文章内容");

        MvcResult createResult = performPost("/api/v1/articles", createRequest)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.title").value("E2E 测试文章"))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andReturn();

        String articleId = extractJsonValue(createResult, "$.id");

        // 2. 查询文章
        performGet("/api/v1/articles/{id}", articleId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("E2E 测试文章"))
            .andExpect(jsonPath("$.content").value("文章内容"));

        // 3. 发布文章
        performPost("/api/v1/articles/{id}/publish", articleId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // 4. 查询已发布文章列表
        performGet("/api/v1/articles?status=PUBLISHED")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content[0].id").value(articleId));

        // 5. 删除文章
        performDelete("/api/v1/articles/{id}", articleId)
            .andExpect(status().isNoContent());

        // 6. 验证文章已删除
        performGet("/api/v1/articles/{id}", articleId)
            .andExpect(status().isNotFound());
    }

    @Test
    void testValidation() throws Exception {
        // 测试请求验证
        CreateArticleRequest invalidRequest = new CreateArticleRequest("", "");  // 无效请求

        performPost("/api/v1/articles", invalidRequest)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").exists())
            .andExpect(jsonPath("$.errors.title").value("标题不能为空"));
    }
}
```

#### 3.2 业务流程 E2E 测试

**目标**: 测试跨多个 API 的复杂业务流程

**示例**:

```java
class ArticlePublishingFlowE2E extends BaseE2ETest {

    @Test
    void testCompleteArticlePublishingFlow() throws Exception {
        // 1. 用户创建草稿
        String articleId = createDraftArticle("我的新文章", "精彩内容");

        // 2. 用户预览文章
        performGet("/api/v1/articles/{id}/preview", articleId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("我的新文章"));

        // 3. 用户提交审核
        performPost("/api/v1/articles/{id}/submit-review", articleId)
            .andExpect(status().isOk());

        // 4. 审核员批准
        performPost("/api/v1/articles/{id}/approve", articleId)
            .andExpect(status().isOk());

        // 5. 用户发布文章
        performPost("/api/v1/articles/{id}/publish", articleId)
            .andExpect(status().isOk());

        // 6. 验证文章可公开访问
        performGet("/api/public/articles/{id}", articleId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("我的新文章"));
    }

    private String createDraftArticle(String title, String content) throws Exception {
        CreateArticleRequest request = new CreateArticleRequest(title, content);
        MvcResult result = performPost("/api/v1/articles", request)
            .andExpect(status().isCreated())
            .andReturn();
        return extractJsonValue(result, "$.id");
    }
}
```

---

### 4. 使用 TestDataBuilder

#### 4.1 基本用法

```java
// 使用默认值创建
Article article = new ArticleTestBuilder().build();

// 自定义部分属性
Article customArticle = new ArticleTestBuilder()
    .withTitle("自定义标题")
    .withStatus(ArticleStatus.PUBLISHED)
    .build();

// 批量创建
List<Article> articles = new ArticleTestBuilder()
    .withStatus(ArticleStatus.DRAFT)
    .buildList(10);
```

#### 4.2 创建自定义 Builder

```java
public class ArticleTestBuilder extends TestDataBuilder<Article> {

    private String title = MockDataFactory.randomString("Article_", 10);
    private String content = "默认内容";
    private ArticleStatus status = ArticleStatus.DRAFT;
    private LocalDateTime createdAt = LocalDateTime.now();

    public ArticleTestBuilder withTitle(String title) {
        this.title = title;
        return this;
    }

    public ArticleTestBuilder withContent(String content) {
        this.content = content;
        return this;
    }

    public ArticleTestBuilder withStatus(ArticleStatus status) {
        this.status = status;
        return this;
    }

    public ArticleTestBuilder published() {
        this.status = ArticleStatus.PUBLISHED;
        return this;
    }

    @Override
    public Article build() {
        return Article.builder()
            .title(title)
            .content(content)
            .status(status)
            .createdAt(createdAt)
            .build();
    }

    /**
     * 构建并保存到仓储
     */
    public Article buildAndSave(ArticleRepository repository) {
        Article article = build();
        repository.save(article);
        return article;
    }
}
```

#### 4.3 在测试中使用

```java
class ArticleRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private ArticleRepository articleRepository;

    @Test
    void testFindPublishedArticles() {
        // given
        Article draft = new ArticleTestBuilder().buildAndSave(articleRepository);
        Article published1 = new ArticleTestBuilder().published().buildAndSave(articleRepository);
        Article published2 = new ArticleTestBuilder().published().buildAndSave(articleRepository);

        // when
        List<Article> publishedArticles = articleRepository.findByStatus(ArticleStatus.PUBLISHED);

        // then
        assertThat(publishedArticles).hasSize(2);
    }
}
```

---

### 5. 使用 MockDataFactory

#### 5.1 生成随机数据

```java
// 生成随机字符串
String taskId = MockDataFactory.randomUuid();
String articleTitle = MockDataFactory.randomString("Article_", 10);

// 生成随机数值
int priority = MockDataFactory.randomInt(1, 10);
long articleCount = MockDataFactory.randomLong(100, 1000);

// 生成随机日期时间
LocalDateTime createdAt = MockDataFactory.randomDateTime(-7);  // 7 天前
LocalDate publishDate = MockDataFactory.randomDate(-30);      // 30 天前

// 生成随机枚举值
TaskStatus status = MockDataFactory.randomEnum(TaskStatus.class);

// 生成随机布尔值
boolean isActive = MockDataFactory.randomBoolean();

// 生成随机邮箱和 URL
String email = MockDataFactory.randomEmail();  // test-xxx@example.com
String url = MockDataFactory.randomUrl();      // https://example.com/xxx
```

#### 5.2 批量生成测试数据

```java
@Test
void testBatchInsert() {
    // 批量生成 100 个随机文章
    List<Article> articles = IntStream.range(0, 100)
        .mapToObj(i -> new ArticleTestBuilder()
            .withTitle(MockDataFactory.randomString("Article_", 10))
            .withStatus(MockDataFactory.randomEnum(ArticleStatus.class))
            .build())
        .collect(Collectors.toList());

    // 批量保存
    articleRepository.saveAll(articles);

    // 验证
    assertThat(articleRepository.count()).isEqualTo(100);
}
```

---

### 6. 使用 DomainAssertions

#### 6.1 聚合根断言

```java
@Test
void testTaskCompletion() {
    // given
    Task task = new Task("测试任务");

    // when
    task.markAsCompleted();

    // then
    DomainAssertions.assertAggregateStatus(task, TaskStatus.COMPLETED);
    DomainAssertions.assertDomainEventPublished(task, TaskCompletedEvent.class);
}
```

#### 6.2 值对象断言

```java
@Test
void testEmailValueObject() {
    // given
    Email email1 = new Email("test@example.com");
    Email email2 = new Email("test@example.com");

    // then
    DomainAssertions.assertValueObjectEquals(email1, email2);
}
```

#### 6.3 集合断言

```java
@Test
void testArticleList() {
    // given
    List<Article> articles = new ArticleTestBuilder().buildList(5);

    // then
    DomainAssertions.assertCollectionSize(articles, 5);
}
```

---

### 7. 配置和优化

#### 7.1 配置日志级别

**src/test/resources/application-test.yml**:

```yaml
logging:
  level:
    com.patra.test: DEBUG  # 测试基础设施日志
    com.patra: DEBUG       # 业务代码日志
    org.testcontainers: INFO  # TestContainers 日志
    org.springframework.test: INFO  # Spring Test 日志
    org.hibernate.SQL: DEBUG  # SQL 日志
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE  # SQL 参数日志

spring:
  jpa:
    show-sql: true  # 显示 SQL
    properties:
      hibernate:
        format_sql: true  # 格式化 SQL
```

#### 7.2 启用容器复用(本地开发)

**~/.testcontainers.properties**:

```properties
# 启用容器复用
testcontainers.reuse.enable=true

# Docker Socket
docker.host=unix:///var/run/docker.sock

# 禁用 Ryuk (可选,不推荐)
# ryuk.container.privileged=true
```

**注意**: CI 环境不要启用容器复用,确保每次测试都是干净的环境。

#### 7.3 配置测试超时

```java
@Test
@Timeout(value = 30, unit = TimeUnit.SECONDS)  // 30 秒超时
void testLongRunningOperation() {
    // 长时间运行的测试
}
```

#### 7.4 并行运行测试

**pom.xml**:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <parallel>classes</parallel>
        <threadCount>4</threadCount>  <!-- 并行线程数 -->
    </configuration>
</plugin>
```

---

## 🔧 常见问题

### Q1: TestContainers 启动失败

**问题**: 测试启动时报错 "Could not find a valid Docker environment"

**解决方案**:

1. **检查 Docker 是否运行**:
   ```bash
   docker ps
   ```

2. **检查 Docker 版本**:
   ```bash
   docker --version  # 确保 ≥ 20.10
   ```

3. **macOS 用户**: 确保 Docker Desktop 正在运行

4. **Linux 用户**: 确保当前用户有 Docker 权限
   ```bash
   sudo usermod -aG docker $USER
   ```

---

### Q2: 测试启动很慢

**问题**: 集成测试启动需要 30+ 秒

**解决方案**:

1. **启用容器复用**:
   ```properties
   # ~/.testcontainers.properties
   testcontainers.reuse.enable=true
   ```

2. **代码中启用**:
   ```java
   mysqlContainer.withReuse(true);
   ```

3. **首次测试后,容器会保持运行,后续测试 <1 秒启动**

---

### Q3: 测试数据没有清理

**问题**: 测试后数据仍然残留在数据库中

**解决方案**:

1. **确保测试类继承自 BaseIntegrationTest**:
   ```java
   class MyRepositoryIT extends BaseIntegrationTest {  // 自动回滚
   }
   ```

2. **检查是否有 @Transactional 注解**:
   ```java
   @SpringBootTest
   @Transactional  // 必需!
   class MyTest {
   }
   ```

3. **手动清理 Redis 数据**:
   ```java
   @AfterEach
   void tearDown() {
       cleanRedis();  // BaseIntegrationTest 提供的方法
   }
   ```

---

### Q4: 如何调试测试

**方法 1: IDEA 调试**

1. 在测试代码中设置断点
2. 右键测试方法 → Debug 'testMethod'
3. 单步执行,查看变量值

**方法 2: 日志调试**

```java
@Slf4j
class MyTest extends BaseIntegrationTest {

    @Test
    void testSomething() {
        log.debug("开始测试,taskId={}", taskId);
        // ...
        log.debug("测试完成,result={}", result);
    }
}
```

**方法 3: 查看 TestContainers 日志**

```yaml
logging:
  level:
    org.testcontainers: DEBUG  # 详细日志
    com.github.dockerjava: DEBUG  # Docker 客户端日志
```

---

### Q5: 如何在 CI 环境运行测试

**CI 配置示例 (GitHub Actions)**:

```yaml
name: Test

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 25
        uses: actions/setup-java@v3
        with:
          java-version: '25'

      - name: Install Docker
        run: |
          sudo apt-get update
          sudo apt-get install -y docker.io

      - name: Run Tests
        run: mvn clean test

      - name: Upload Test Results
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-results
          path: target/surefire-reports/
```

**注意**:
- CI 环境不要启用容器复用 (`testcontainers.reuse.enable=false`)
- 确保 CI 环境有足够的资源 (内存 ≥ 4GB, CPU ≥ 2 核)

---

## 📖 参考资料

### 官方文档
- [Testcontainers for Java](https://java.testcontainers.org/)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)

### 项目文档
- [test-api.md](./contracts/test-api.md) - API 契约文档
- [test-infrastructure-model.md](./test-infrastructure-model.md) - 模型设计文档
- [research.md](./research.md) - 技术调研报告

### 示例代码
- `patra-registry-boot/src/test/` - 示例测试代码
- `patra-common-test/src/test/` - 工具类单元测试

---

## 🎓 学习路径

### 初级 (第 1 周)

1. ✅ 理解测试金字塔 (单元测试 > 集成测试 > E2E 测试)
2. ✅ 编写 Domain 层单元测试
3. ✅ 使用 TestDataBuilder 创建测试数据
4. ✅ 使用 DomainAssertions 进行断言

### 中级 (第 2-3 周)

5. ✅ 编写 Repository 集成测试
6. ✅ 理解 TestContainers 工作原理
7. ✅ 编写 Controller E2E 测试
8. ✅ 使用 MockMvc 测试 REST API

### 高级 (第 4+ 周)

9. ✅ 优化测试启动速度 (容器复用、tmpfs)
10. ✅ 编写复杂业务流程 E2E 测试
11. ✅ 创建自定义 TestDataBuilder
12. ✅ 集成 WireMock 模拟外部 API

---

## 💡 最佳实践

### ✅ 应该做

1. **测试命名清晰**: `testCreateArticle_WhenValidRequest_ThenArticleCreated`
2. **使用 Given-When-Then 结构**: 提高可读性
3. **一个测试只验证一个行为**: 保持测试简洁
4. **使用 TestDataBuilder**: 避免重复的测试数据构造代码
5. **集成测试使用 @Transactional**: 自动清理测试数据
6. **本地开发启用容器复用**: 加快测试反馈速度
7. **测试失败时提供清晰的错误信息**: 使用 AssertJ 的描述性断言

### ❌ 不应该做

1. **测试之间有依赖关系**: 每个测试应该独立运行
2. **在测试中使用 `Thread.sleep()`**: 使用 Awaitility 等待异步操作
3. **硬编码测试数据**: 使用 MockDataFactory 生成随机数据
4. **在单元测试中启动 Spring Context**: 单元测试应该快速
5. **在 CI 环境启用容器复用**: 确保每次测试都是干净的环境
6. **忽略测试失败**: 修复测试或标记为 @Disabled 并创建 Issue

---

**版本**: 1.0.0
**最后更新**: 2025-11-09
**维护者**: Patra 架构团队

**祝你测试愉快! 🚀**