# 测试基础设施 API 契约

**版本**: 1.0.0
**日期**: 2025-11-09
**项目**: Patra 测试基础设施模块重构

---

## 📋 概述

本文档定义了测试基础设施模块对外提供的公共 API,包括:
- `patra-common-test`: 纯 Java 测试工具类的公共 API
- `patra-spring-boot-starter-test`: Spring Boot 测试自动配置的公共 API

---

## 1. patra-common-test 公共 API

### 1.1 TestDataBuilder - 测试数据构建器

**包路径**: `com.patra.common.test.builder`

**核心接口**:

```java
/**
 * 测试数据构建器基类
 *
 * @param <T> 构建的目标对象类型
 */
public abstract class TestDataBuilder<T> {

    /**
     * 构建单个对象
     *
     * @return 构建的对象实例
     */
    public abstract T build();

    /**
     * 批量构建多个对象
     *
     * @param count 构建数量
     * @return 对象列表
     */
    public List<T> buildList(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> build())
            .collect(Collectors.toList());
    }

    /**
     * 构建并保存到仓储(需要子类实现)
     *
     * @param repository 仓储实例
     * @return 保存后的对象
     */
    public T buildAndSave(Object repository) {
        throw new UnsupportedOperationException("子类需要实现此方法");
    }
}
```

**使用示例**:

```java
// 使用默认值构建
OutboxMessage message = new OutboxMessageTestBuilder().build();

// 自定义属性
OutboxMessage customMessage = new OutboxMessageTestBuilder()
    .withEventType("TaskUpdated")
    .withStatus(OutboxStatus.SENT)
    .build();

// 批量创建
List<OutboxMessage> messages = new OutboxMessageTestBuilder()
    .buildList(10);
```

---

### 1.2 MockDataFactory - Mock 数据工厂

**包路径**: `com.patra.common.test.factory`

**核心接口**:

```java
/**
 * Mock 数据工厂
 *
 * 提供批量生成随机测试数据的功能
 */
public class MockDataFactory {

    /**
     * 生成随机字符串
     *
     * @param prefix 前缀
     * @param length 长度
     * @return 随机字符串
     */
    public static String randomString(String prefix, int length) {
        return prefix + RandomStringUtils.randomAlphanumeric(length);
    }

    /**
     * 生成随机 UUID
     *
     * @return UUID 字符串
     */
    public static String randomUuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成随机整数
     *
     * @param min 最小值(包含)
     * @param max 最大值(包含)
     * @return 随机整数
     */
    public static int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * 生成随机日期时间
     *
     * @param daysAgo 距离今天的天数(负数表示过去)
     * @return LocalDateTime
     */
    public static LocalDateTime randomDateTime(int daysAgo) {
        return LocalDateTime.now().minusDays(Math.abs(daysAgo));
    }

    /**
     * 从枚举中随机选择一个值
     *
     * @param enumClass 枚举类
     * @return 随机枚举值
     */
    public static <E extends Enum<E>> E randomEnum(Class<E> enumClass) {
        E[] values = enumClass.getEnumConstants();
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }
}
```

**使用示例**:

```java
// 生成随机数据
String taskId = MockDataFactory.randomUuid();
String articleTitle = MockDataFactory.randomString("Article_", 10);
int priority = MockDataFactory.randomInt(1, 10);
LocalDateTime createdAt = MockDataFactory.randomDateTime(-7);
TaskStatus status = MockDataFactory.randomEnum(TaskStatus.class);
```

---

### 1.3 DomainAssertions - 领域断言工具

<!-- 实施变更：见 implementation-log.md#变更-001 -->
> ⚠️ **注意**：实际实现使用纯Java断言而非AssertJ（见implementation-log.md#变更-001），下方示例代码仅作参考

**包路径**: `com.patra.common.test.assertion`

**核心接口**:

```java
/**
 * 领域断言工具
 *
 * 提供业务语义化的断言方法
 */
public class DomainAssertions {

    /**
     * 断言聚合根状态正确
     *
     * @param actual 实际聚合根
     * @param expectedStatus 期望状态
     */
    public static <T extends AggregateRoot<?>> void assertAggregateStatus(
            T actual, Object expectedStatus) {
        assertThat(actual).isNotNull();
        assertThat(actual.getStatus()).isEqualTo(expectedStatus);
    }

    /**
     * 断言领域事件已发布
     *
     * @param aggregate 聚合根
     * @param eventType 事件类型
     */
    public static void assertDomainEventPublished(
            AggregateRoot<?> aggregate, Class<? extends DomainEvent> eventType) {
        assertThat(aggregate.getDomainEvents())
            .isNotEmpty()
            .anySatisfy(event -> assertThat(event).isInstanceOf(eventType));
    }

    /**
     * 断言值对象相等(深度比较)
     *
     * @param actual 实际值对象
     * @param expected 期望值对象
     */
    public static <V> void assertValueObjectEquals(V actual, V expected) {
        assertThat(actual)
            .isNotNull()
            .usingRecursiveComparison()
            .isEqualTo(expected);
    }

    /**
     * 断言集合包含指定数量的元素
     *
     * @param collection 集合
     * @param expectedSize 期望大小
     */
    public static <T> void assertCollectionSize(Collection<T> collection, int expectedSize) {
        assertThat(collection)
            .isNotNull()
            .hasSize(expectedSize);
    }
}
```

**使用示例**:

```java
// 断言聚合根状态
Task task = taskRepository.findById(taskId).orElseThrow();
DomainAssertions.assertAggregateStatus(task, TaskStatus.COMPLETED);

// 断言领域事件
DomainAssertions.assertDomainEventPublished(task, TaskCompletedEvent.class);

// 断言值对象相等
Email actualEmail = user.getEmail();
Email expectedEmail = new Email("test@example.com");
DomainAssertions.assertValueObjectEquals(actualEmail, expectedEmail);
```

---

### 1.4 BaseUnitTest - 单元测试基类

**包路径**: `com.patra.common.test.base`

**核心接口**:

```java
/**
 * 单元测试基类
 *
 * 提供通用的单元测试配置和工具方法
 */
@ExtendWith(MockitoExtension.class)
public abstract class BaseUnitTest {

    /**
     * 打印测试执行时间(可选)
     */
    @Rule
    public TestRule watcher = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            System.out.println("开始测试: " + description.getMethodName());
        }

        @Override
        protected void finished(Description description) {
            System.out.println("完成测试: " + description.getMethodName());
        }
    };

    /**
     * 验证 Mock 对象的交互(便捷方法)
     *
     * @param mock Mock 对象
     * @param times 调用次数
     */
    protected <T> void verifyMockInteraction(T mock, int times) {
        verify(mock, times(times));
    }

    /**
     * 重置 Mock 对象
     *
     * @param mocks Mock 对象列表
     */
    protected void resetMocks(Object... mocks) {
        Mockito.reset(mocks);
    }
}
```

**使用示例**:

```java
class TaskAggregateTest extends BaseUnitTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskOrchestrator taskOrchestrator;

    @Test
    void testCreateTask() {
        // given
        TaskCreateCommand command = new TaskCreateCommand("Test Task");

        // when
        Task task = taskOrchestrator.createTask(command);

        // then
        assertThat(task.getTitle()).isEqualTo("Test Task");
        verifyMockInteraction(taskRepository, 1);
    }
}
```

---

### 1.5 TestConstants - 测试常量

**包路径**: `com.patra.common.test.constant`

**核心接口**:

```java
/**
 * 测试常量定义
 */
public final class TestConstants {

    private TestConstants() {
        // 禁止实例化
    }

    /**
     * 测试用户 ID
     */
    public static final String TEST_USER_ID = "test-user-001";

    /**
     * 测试组织 ID
     */
    public static final String TEST_ORG_ID = "test-org-001";

    /**
     * 测试超时时间(毫秒)
     */
    public static final int TEST_TIMEOUT_MS = 5000;

    /**
     * 测试重试次数
     */
    public static final int TEST_RETRY_COUNT = 3;

    /**
     * 测试数据库名称
     */
    public static final String TEST_DB_NAME = "test_db";
}
```

---

## 2. patra-spring-boot-starter-test 公共 API

### 2.1 BaseIntegrationTest - 集成测试基类

**包路径**: `com.patra.spring.boot.starter.test.base`

**核心接口**:

```java
/**
 * 集成测试基类
 *
 * 自动配置 TestContainers (MySQL, Redis, Nacos)
 * 支持 Spring Context 完整加载
 */
@SpringBootTest
@Transactional  // 默认回滚,保持数据隔离
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
    "logging.level.com.patra.test=DEBUG",
    "spring.jpa.show-sql=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class BaseIntegrationTest {

    @Autowired
    protected DataSource dataSource;

    @Autowired(required = false)
    protected JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    protected RedisTemplate<String, String> redisTemplate;

    /**
     * 清理 Redis 数据
     */
    protected void cleanRedis() {
        if (redisTemplate != null) {
            Objects.requireNonNull(redisTemplate.getConnectionFactory())
                .getConnection()
                .flushDb();
        }
    }

    /**
     * 执行 SQL 脚本
     *
     * @param sqlScript SQL 脚本路径
     */
    protected void executeSqlScript(String sqlScript) {
        Resource resource = new ClassPathResource(sqlScript);
        ScriptUtils.executeSqlScript(dataSource.getConnection(), resource);
    }

    /**
     * 验证数据库连接
     */
    @Test
    void contextLoads() {
        assertThat(dataSource).isNotNull();
    }
}
```

**使用示例**:

```java
class RegistryRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private ProvenanceRepository provenanceRepository;

    @Test
    void testSaveAndFindProvenance() {
        // given
        Provenance provenance = new Provenance("PubMed", "https://pubmed.ncbi.nlm.nih.gov");

        // when
        provenanceRepository.save(provenance);

        // then
        Optional<Provenance> found = provenanceRepository.findById(provenance.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("PubMed");
    }

    @AfterEach
    void tearDown() {
        cleanRedis();  // 清理 Redis 数据
    }
}
```

---

### 2.2 BaseE2ETest - E2E 测试基类

**包路径**: `com.patra.spring.boot.starter.test.base`

**核心接口**:

```java
/**
 * E2E 测试基类
 *
 * 启动完整的 Spring Boot 应用
 * 使用 MockMvc 模拟 HTTP 请求
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
    "logging.level.com.patra.test=DEBUG"
})
public abstract class BaseE2ETest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired(required = false)
    protected WireMockServer wireMockServer;

    /**
     * 执行 GET 请求
     *
     * @param url URL 路径
     * @param uriVars URI 变量
     * @return ResultActions
     */
    protected ResultActions performGet(String url, Object... uriVars) throws Exception {
        return mockMvc.perform(
            MockMvcRequestBuilders.get(url, uriVars)
                .accept(MediaType.APPLICATION_JSON)
        );
    }

    /**
     * 执行 POST 请求
     *
     * @param url URL 路径
     * @param body 请求体
     * @return ResultActions
     */
    protected ResultActions performPost(String url, Object body) throws Exception {
        return mockMvc.perform(
            MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        );
    }

    /**
     * 执行 PUT 请求
     *
     * @param url URL 路径
     * @param body 请求体
     * @return ResultActions
     */
    protected ResultActions performPut(String url, Object body) throws Exception {
        return mockMvc.perform(
            MockMvcRequestBuilders.put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        );
    }

    /**
     * 执行 DELETE 请求
     *
     * @param url URL 路径
     * @param uriVars URI 变量
     * @return ResultActions
     */
    protected ResultActions performDelete(String url, Object... uriVars) throws Exception {
        return mockMvc.perform(
            MockMvcRequestBuilders.delete(url, uriVars)
        );
    }

    /**
     * 从响应中提取 JSON 字段值
     *
     * @param mvcResult MvcResult
     * @param jsonPath JSON 路径表达式
     * @return 字段值
     */
    protected String extractJsonValue(MvcResult mvcResult, String jsonPath) throws Exception {
        return JsonPath.read(mvcResult.getResponse().getContentAsString(), jsonPath);
    }
}
```

**使用示例**:

```java
class RegistryFlowE2E extends BaseE2ETest {

    @Test
    void testCreateAndQueryProvenance() throws Exception {
        // 1. 创建 Provenance
        ProvenanceRequest request = new ProvenanceRequest("PubMed", "https://pubmed.ncbi.nlm.nih.gov");

        MvcResult createResult = performPost("/api/v1/provenances", request)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andReturn();

        String id = extractJsonValue(createResult, "$.id");

        // 2. 查询 Provenance
        performGet("/api/v1/provenances/{id}", id)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("PubMed"))
            .andExpect(jsonPath("$.url").value("https://pubmed.ncbi.nlm.nih.gov"));

        // 3. 删除 Provenance
        performDelete("/api/v1/provenances/{id}", id)
            .andExpect(status().isNoContent());
    }
}
```

---

### 2.3 TestcontainersConfiguration - TestContainers 自动配置

**包路径**: `com.patra.spring.boot.starter.test.autoconfigure`

**核心接口**:

```java
/**
 * TestContainers 自动配置
 *
 * 提供 MySQL, Redis, Nacos 容器的自动初始化
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /**
     * MySQL 容器
     *
     * @return MySQLContainer
     */
    @Bean
    @ServiceConnection
    @RestartScope
    public MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>("mysql:8.0.36")
            .withReuse(true)
            .withTmpFs(Map.of("/var/lib/mysql", "rw"))
            .withCommand(
                "--character-set-server=utf8mb4",
                "--collation-server=utf8mb4_unicode_ci"
            );
    }

    /**
     * Redis 容器
     *
     * @return GenericContainer
     */
    @Bean
    @ServiceConnection(name = "redis")
    @RestartScope
    public GenericContainer<?> redisContainer() {
        return new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withReuse(true);
    }

    /**
     * Nacos 容器
     *
     * @return GenericContainer
     */
    @Bean
    @ServiceConnection(name = "nacos")
    @RestartScope
    public GenericContainer<?> nacosContainer() {
        return new GenericContainer<>("nacos/nacos-server:v2.3.0")
            .withExposedPorts(8848, 9848, 9849)
            .withEnv("MODE", "standalone")
            .waitingFor(Wait.forHttp("/nacos/")
                .forPort(8848)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(2)))
            .withReuse(true);
    }
}
```

---

## 3. 版本兼容性

| 组件 | 最低版本要求 | 推荐版本 |
|-----|------------|---------|
| Java | 17 | 25 |
| Spring Boot | 3.1.0 | 3.5.7 |
| Spring Cloud | 2022.0.0 | 2025.0.0 |
| JUnit | 5.9.0 | 5.11.x |
| Mockito | 5.0.0 | 5.x |
| AssertJ | 3.24.0 | 3.x |
| TestContainers | 1.19.0 | 1.19.x |
| Docker | 20.10.0 | 最新稳定版 |

---

## 4. 使用指南

### 4.1 引入依赖

**在 patra-{service}-domain/app/infra/adapter 模块的 pom.xml**:

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-common-test</artifactId>
    <scope>test</scope>
</dependency>
```

**在 patra-{service}-boot 模块的 pom.xml**:

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

### 4.2 配置 Testcontainers (本地开发)

**文件位置**: `~/.testcontainers.properties`

```properties
# 启用容器复用 (仅本地开发)
testcontainers.reuse.enable=true

# Docker Socket (macOS/Linux)
docker.host=unix:///var/run/docker.sock

# 可选: 禁用启动检查 (稳定环境)
checks.disable=false
```

### 4.3 配置日志级别

**文件位置**: `src/test/resources/application-test.yml`

```yaml
logging:
  level:
    com.patra.test: DEBUG  # 测试基础设施日志
    org.testcontainers: INFO  # TestContainers 日志
    org.springframework.test: DEBUG  # Spring Test 日志
```

---

## 5. 常见问题

### Q1: 如何在集成测试中禁用 TestContainers?

**A**: 使用 Spring Profile 条件化加载:

```java
@TestConfiguration(proxyBeanMethods = false)
@Profile("testcontainers")  // 仅在激活 testcontainers profile 时加载
public class TestcontainersConfiguration {
    // ...
}
```

### Q2: 如何共享容器实例以加快测试速度?

**A**: 启用 Reusable Containers:

1. 配置 `~/.testcontainers.properties`
2. 容器配置中添加 `.withReuse(true)`
3. 首次测试后容器会保持运行,后续测试直接复用

### Q3: 如何调试 TestContainers 启动失败?

**A**: 启用详细日志:

```yaml
logging:
  level:
    org.testcontainers: DEBUG
    com.github.dockerjava: DEBUG
```

检查 Docker 环境:
```bash
docker ps  # 查看运行中的容器
docker logs <container_id>  # 查看容器日志
```

### Q4: 如何在 CI/CD 环境中运行测试?

**A**: 确保 CI 环境:
1. 安装 Docker 20.10+
2. **禁用** Reusable Containers (删除 `testcontainers.reuse.enable=true`)
3. 配置足够的资源(内存 ≥ 4GB, CPU ≥ 2 核)

---

## 6. 后续扩展

未来版本可能增加的 API:

- **WireMockServer 自动配置**: 模拟外部 HTTP API
- **Kafka/RocketMQ TestContainers**: 消息队列测试支持
- **性能测试基类**: 基准测试和负载测试支持
- **测试数据快照**: 录制和回放测试数据
- **ArchUnit 集成**: 架构合规性测试

---

**API 版本**: 1.0.0
**最后更新**: 2025-11-09
**维护者**: Patra 架构团队