# 测试基础设施模型设计

**版本**: 1.0.0
**日期**: 2025-11-09
**项目**: Patra 测试基础设施模块重构

---

## 📋 概述

本文档定义了测试基础设施的模型结构设计,包括:
- patra-common-test 的工具类结构
- patra-spring-boot-starter-test 的配置结构
- TestContainers 容器定义
- 测试数据构建器的抽象模型

**注意**: 这不是业务领域模型,而是测试技术架构的抽象模型。

---

## 1. patra-common-test 模型结构

### 1.1 包结构设计

```
com.patra.common.test/
├── builder/                    # 测试数据构建器
│   ├── TestDataBuilder.java   # 抽象基类
│   └── package-info.java
├── factory/                    # Mock 数据工厂
│   ├── MockDataFactory.java   # 静态工具类
│   └── package-info.java
├── assertion/                  # 断言辅助工具
│   ├── AssertionHelper.java   # 通用断言
│   ├── DomainAssertions.java  # 领域断言
│   └── package-info.java
├── base/                       # 测试基类
│   ├── BaseUnitTest.java      # 单元测试基类
│   └── package-info.java
├── constant/                   # 测试常量
│   ├── TestConstants.java     # 常量定义
│   └── package-info.java
└── package-info.java           # 模块级别说明
```

### 1.2 TestDataBuilder 抽象模型

**类图**:

```
┌─────────────────────────────────┐
│   TestDataBuilder<T>            │  (Abstract)
├─────────────────────────────────┤
│ - fields: Map<String, Object>   │
├─────────────────────────────────┤
│ + build(): T                    │ (Abstract)
│ + buildList(count): List<T>     │
│ + buildAndSave(repo): T         │
│ + reset(): this                 │
└─────────────────────────────────┘
           △
           │ extends
           │
┌──────────┴──────────────────────┐
│  OutboxMessageTestBuilder       │  (Concrete)
├─────────────────────────────────┤
│ - aggregateId: String            │
│ - aggregateType: String          │
│ - eventType: String              │
│ - payload: String                │
│ - status: OutboxStatus           │
├─────────────────────────────────┤
│ + withAggregateId(id): this     │
│ + withEventType(type): this     │
│ + withStatus(status): this      │
│ + build(): OutboxMessage        │
└─────────────────────────────────┘
```

**核心属性**:

| 属性 | 类型 | 说明 | 默认值 |
|-----|------|------|-------|
| fields | `Map<String, Object>` | 字段值存储 | 空 Map |

**核心方法**:

| 方法 | 返回类型 | 说明 |
|-----|---------|------|
| `build()` | `T` | 构建单个对象(抽象方法) |
| `buildList(int count)` | `List<T>` | 批量构建对象 |
| `buildAndSave(Object repository)` | `T` | 构建并保存到仓储 |
| `reset()` | `this` | 重置所有字段为默认值 |

**设计模式**: Builder 模式 + 模板方法模式

---

### 1.3 MockDataFactory 模型

**类图**:

```
┌─────────────────────────────────┐
│   MockDataFactory                │  (Final, Static)
├─────────────────────────────────┤
│ - random: ThreadLocalRandom      │
├─────────────────────────────────┤
│ + randomString(prefix, len): String  │
│ + randomUuid(): String           │
│ + randomInt(min, max): int       │
│ + randomLong(min, max): long     │
│ + randomDateTime(daysAgo): LocalDateTime │
│ + randomEnum<E>(Class<E>): E     │
│ + randomBoolean(): boolean       │
│ + randomEmail(): String          │
│ + randomUrl(): String            │
└─────────────────────────────────┘
```

**核心方法分类**:

| 分类 | 方法 | 返回类型 | 说明 |
|-----|------|---------|------|
| **字符串生成** | `randomString(prefix, len)` | `String` | 生成随机字符串 |
| | `randomUuid()` | `String` | 生成 UUID |
| | `randomEmail()` | `String` | 生成随机邮箱 |
| | `randomUrl()` | `String` | 生成随机 URL |
| **数值生成** | `randomInt(min, max)` | `int` | 生成随机整数 |
| | `randomLong(min, max)` | `long` | 生成随机长整数 |
| | `randomBoolean()` | `boolean` | 生成随机布尔值 |
| **日期生成** | `randomDateTime(daysAgo)` | `LocalDateTime` | 生成随机日期时间 |
| | `randomDate(daysAgo)` | `LocalDate` | 生成随机日期 |
| **枚举生成** | `randomEnum(Class<E>)` | `E` | 从枚举中随机选择 |

**设计模式**: 静态工厂方法模式

---

### 1.4 DomainAssertions 模型

**类图**:

```
┌─────────────────────────────────┐
│   DomainAssertions               │  (Final, Static)
├─────────────────────────────────┤
│ + assertAggregateStatus(T, status) │
│ + assertDomainEventPublished(T, eventType) │
│ + assertValueObjectEquals(V, V)  │
│ + assertCollectionSize(Collection, size) │
│ + assertEntityEquals(E, E)       │
│ + assertRepositorySaved(Repo, id)│
└─────────────────────────────────┘
```

**断言方法分类**:

| 分类 | 方法 | 说明 |
|-----|------|------|
| **聚合根断言** | `assertAggregateStatus(T, status)` | 断言聚合根状态 |
| | `assertDomainEventPublished(T, eventType)` | 断言领域事件已发布 |
| **值对象断言** | `assertValueObjectEquals(V, V)` | 深度比较值对象 |
| **实体断言** | `assertEntityEquals(E, E)` | 比较实体(忽略版本号) |
| **仓储断言** | `assertRepositorySaved(Repo, id)` | 断言仓储已保存 |
| **集合断言** | `assertCollectionSize(Collection, size)` | 断言集合大小 |

**设计模式**: 静态辅助方法模式

---

## 2. patra-spring-boot-starter-test 模型结构

### 2.1 包结构设计

```
com.patra.spring.boot.starter.test/
├── autoconfigure/                       # 自动配置类
│   ├── TestContainersAutoConfiguration.java
│   ├── MockMvcAutoConfiguration.java
│   ├── WireMockAutoConfiguration.java
│   └── package-info.java
├── base/                                # 测试基类
│   ├── BaseIntegrationTest.java
│   ├── BaseE2ETest.java
│   └── package-info.java
├── container/                           # TestContainers 容器定义
│   ├── MySQLTestContainer.java
│   ├── RedisTestContainer.java
│   ├── NacosTestContainer.java
│   ├── RocketMQTestContainer.java
│   └── package-info.java
├── config/                              # 配置类
│   ├── TestcontainersConfiguration.java
│   ├── TestProperties.java
│   └── package-info.java
└── package-info.java                    # 模块级别说明
```

### 2.2 TestContainers 容器模型

**类图**:

```
┌───────────────────────────────────┐
│   AbstractTestContainer           │  (Abstract)
├───────────────────────────────────┤
│ - containerImage: String           │
│ - exposedPorts: List<Integer>     │
│ - envVars: Map<String, String>    │
│ - reusable: boolean                │
├───────────────────────────────────┤
│ + start(): void                    │
│ + stop(): void                     │
│ + getConnectionUrl(): String       │ (Abstract)
│ + isHealthy(): boolean             │
└───────────────────────────────────┘
           △
           │ extends
    ┌──────┴──────┬──────────┬──────────┐
    │             │          │          │
┌───┴───────┐ ┌──┴────────┐ ┌─┴──────────┐ ┌─┴──────────┐
│ MySQLTest │ │ RedisTest │ │ NacosTest  │ │ RocketMQTest │
│ Container │ │ Container │ │ Container  │ │ Container  │
├───────────┤ ├───────────┤ ├────────────┤ ├────────────┤
│ - port:   │ │ - port:   │ │ - httpPort:│ │ - namePort:│
│   3306    │ │   6379    │ │   8848     │ │   9876     │
│ - dbName  │ │           │ │ - grpcPort:│ │ - brokerPort:│
│ - user    │ │           │ │   9848     │ │   10911    │
│ - password│ │           │ │ - mode:    │ │ - topics:  │
│           │ │           │ │   standalone│ │   List<>   │
└───────────┘ └───────────┘ └────────────┘ └────────────┘
```

### 2.3 MySQL TestContainer 模型

**属性设计**:

| 属性 | 类型 | 说明 | 默认值 |
|-----|------|------|-------|
| containerImage | `String` | Docker 镜像 | `mysql:8.0.36` |
| exposedPorts | `List<Integer>` | 暴露端口 | `[3306]` |
| databaseName | `String` | 数据库名称 | `test_db` |
| username | `String` | 用户名 | `test` |
| password | `String` | 密码 | `test` |
| reusable | `boolean` | 是否复用 | `true` |
| tmpFs | `Map<String, String>` | 内存文件系统 | `{"/var/lib/mysql": "rw"}` |
| commands | `List<String>` | 启动命令 | `["--character-set-server=utf8mb4"]` |

**生命周期**:

```
┌─────────────────────────────────────────────────────────┐
│                  MySQL Container Lifecycle               │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  1. Initialize                                           │
│     ├─ Load Docker Image                                │
│     └─ Configure Parameters                             │
│                                                          │
│  2. Start (10-30 seconds)                               │
│     ├─ Pull Image (if not exists)                       │
│     ├─ Create Container                                 │
│     ├─ Start Container                                  │
│     └─ Wait for Health Check                            │
│                                                          │
│  3. Ready                                                │
│     ├─ JDBC URL: jdbc:mysql://host:port/db             │
│     └─ Accept Connections                               │
│                                                          │
│  4. Reuse (if enabled)                                  │
│     ├─ Keep Container Running                           │
│     └─ Skip Steps 1-2 on Next Test                     │
│                                                          │
│  5. Stop (if not reusable)                              │
│     ├─ Close Connections                                │
│     ├─ Stop Container                                   │
│     └─ Remove Container                                 │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 2.4 Redis TestContainer 模型

**属性设计**:

| 属性 | 类型 | 说明 | 默认值 |
|-----|------|------|-------|
| containerImage | `String` | Docker 镜像 | `redis:7-alpine` |
| exposedPorts | `List<Integer>` | 暴露端口 | `[6379]` |
| reusable | `boolean` | 是否复用 | `true` |

**连接信息**:

```java
public class RedisTestContainer {

    public String getHost() {
        return container.getHost();
    }

    public Integer getPort() {
        return container.getMappedPort(6379);
    }

    public String getConnectionString() {
        return String.format("redis://%s:%d", getHost(), getPort());
    }
}
```

### 2.5 Nacos TestContainer 模型

**属性设计**:

| 属性 | 类型 | 说明 | 默认值 |
|-----|------|------|-------|
| containerImage | `String` | Docker 镜像 | `nacos/nacos-server:v2.3.0` |
| exposedPorts | `List<Integer>` | 暴露端口 | `[8848, 9848, 9849]` |
| mode | `String` | 运行模式 | `standalone` |
| reusable | `boolean` | 是否复用 | `true` |
| startupTimeout | `Duration` | 启动超时 | `2 分钟` |

**环境变量**:

| 环境变量 | 值 | 说明 |
|---------|---|------|
| `MODE` | `standalone` | 单机模式 |
| `PREFER_HOST_MODE` | `hostname` | 主机模式 |
| `JVM_XMS` | `256m` | 最小堆内存 |
| `JVM_XMX` | `256m` | 最大堆内存 |

**健康检查**:

```java
.waitingFor(Wait.forHttp("/nacos/")
    .forPort(8848)
    .forStatusCode(200)
    .withStartupTimeout(Duration.ofMinutes(2)))
```

---

## 3. 测试基类继承层次

### 3.1 继承关系图

```
                  JUnit 5 Test
                       △
                       │
         ┌─────────────┴─────────────┐
         │                           │
   BaseUnitTest               BaseIntegrationTest
   (patra-common-test)        (patra-spring-boot-starter-test)
         △                           △
         │                           │
         │                           │
    DomainTest              RepositoryIT / CoordinatorIT
    (业务模块)                    (业务模块)

                  BaseE2ETest
                  (patra-spring-boot-starter-test)
                       △
                       │
                  ControllerE2E / FlowE2E
                  (业务模块-boot)
```

### 3.2 BaseUnitTest 层次

```
@ExtendWith(MockitoExtension.class)
BaseUnitTest
    ↓
TaskAggregateTest (Domain 层)
ArticleServiceTest (Domain Service)
CoordinatorTest (Application 层)
```

### 3.3 BaseIntegrationTest 层次

```
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
BaseIntegrationTest
    ↓
RepositoryIT (Infrastructure 层)
CoordinatorIT (Application 层)
```

### 3.4 BaseE2ETest 层次

```
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
BaseE2ETest
    ↓
ControllerE2E (Adapter 层)
FlowE2E (完整业务流程)
```

---

## 4. 自动配置机制模型

### 4.1 自动配置流程

```
┌─────────────────────────────────────────────────────────┐
│          Spring Boot Test Auto-Configuration            │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  1. 加载 AutoConfiguration.imports                      │
│     └─ META-INF/spring/org.springframework.boot...      │
│                                                          │
│  2. 条件化配置                                           │
│     ├─ @ConditionalOnClass(TestContainers.class)       │
│     ├─ @ConditionalOnMissingBean                       │
│     └─ @ConditionalOnProperty                          │
│                                                          │
│  3. 创建 Bean                                            │
│     ├─ MySQLContainer (@ServiceConnection)             │
│     ├─ RedisContainer (@ServiceConnection)             │
│     └─ NacosContainer (手动配置属性)                    │
│                                                          │
│  4. 注入依赖                                             │
│     ├─ DataSource (自动配置)                            │
│     ├─ RedisTemplate (自动配置)                         │
│     └─ NacosConfigService (手动配置)                    │
│                                                          │
│  5. 测试就绪                                             │
│     └─ 所有容器启动完成,Spring Context 加载完成         │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 4.2 @ServiceConnection 工作原理

```
@ServiceConnection
MySQLContainer → Spring Boot 识别容器类型
                ↓
              自动配置 DataSource
                ↓
        spring.datasource.url = container.getJdbcUrl()
        spring.datasource.username = container.getUsername()
        spring.datasource.password = container.getPassword()
```

**支持的容器类型**:

| 容器类型 | @ServiceConnection | 自动配置的 Bean |
|---------|-------------------|---------------|
| `MySQLContainer` | `@ServiceConnection` | `DataSource` |
| `PostgreSQLContainer` | `@ServiceConnection` | `DataSource` |
| `GenericContainer("redis")` | `@ServiceConnection(name = "redis")` | `RedisConnectionFactory` |
| `GenericContainer("nacos")` | ❌ 不支持 | 需手动配置 |

---

## 5. 配置属性模型

### 5.1 TestProperties 模型

```java
@ConfigurationProperties(prefix = "patra.test")
public class TestProperties {

    /**
     * 是否启用 TestContainers
     */
    private boolean enabled = true;

    /**
     * 是否启用容器复用
     */
    private boolean reuse = true;

    /**
     * MySQL 配置
     */
    private MysqlProperties mysql = new MysqlProperties();

    /**
     * Redis 配置
     */
    private RedisProperties redis = new RedisProperties();

    /**
     * Nacos 配置
     */
    private NacosProperties nacos = new NacosProperties();

    // Getters and Setters
}
```

### 5.2 配置文件示例

**application-test.yml**:

```yaml
patra:
  test:
    enabled: true
    reuse: true  # 本地开发启用,CI 环境禁用

    mysql:
      image: mysql:8.0.36
      database-name: test_db
      username: test
      password: test
      tmpfs-enabled: true

    redis:
      image: redis:7-alpine

    nacos:
      image: nacos/nacos-server:v2.3.0
      mode: standalone
      startup-timeout: 120s  # 2 分钟
```

---

## 6. 性能优化模型

### 6.1 容器复用策略

```
┌─────────────────────────────────────────────────────────┐
│              Reusable Containers Strategy                │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  第一次测试:                                             │
│  ├─ Pull Image (5-10s)                                  │
│  ├─ Start Container (10-30s)                            │
│  └─ Wait for Health (5-10s)                             │
│  总计: ~15-50 秒                                         │
│                                                          │
│  后续测试 (启用复用):                                     │
│  ├─ Skip Pull                                           │
│  ├─ Skip Start                                          │
│  └─ Reuse Existing Container (<1s)                      │
│  总计: ~1 秒                                             │
│                                                          │
│  性能提升: 93-98%                                        │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 6.2 内存文件系统优化

```
标准配置:
MySQL Data → Host Disk → Docker Volume → Container
             ↓
          写入延迟 ~10-50ms

tmpfs 优化:
MySQL Data → Container Memory (tmpfs)
             ↓
          写入延迟 ~1-5ms

性能提升: 30-40%
```

---

## 7. 测试数据管理模型

### 7.1 测试数据生命周期

```
┌─────────────────────────────────────────────────────────┐
│              Test Data Lifecycle                         │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  1. Setup (Before Each Test)                            │
│     ├─ 使用 TestDataBuilder 创建测试数据                 │
│     └─ 保存到数据库 (通过 Repository)                    │
│                                                          │
│  2. Execute (Test Method)                                │
│     ├─ 调用业务逻辑                                       │
│     └─ 验证结果                                           │
│                                                          │
│  3. Cleanup (After Each Test)                            │
│     ├─ @Transactional 自动回滚                           │
│     ├─ 清理 Redis 数据 (cleanRedis())                    │
│     └─ 清理文件系统 (如果有)                              │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 7.2 测试数据隔离策略

| 策略 | 优点 | 缺点 | 适用场景 |
|-----|------|------|---------|
| **@Transactional 回滚** | 自动清理,无需手动删除 | 无法测试事务边界 | 大部分集成测试 |
| **独立数据库** | 完全隔离 | 启动慢,资源消耗大 | E2E 测试 |
| **手动清理** | 灵活可控 | 容易遗漏 | 特殊场景 |
| **容器重启** | 彻底清理 | 启动慢 | 极端情况 |

---

## 8. 模型扩展性设计

### 8.1 新增 TestContainer 的步骤

1. **定义容器类** (`container/`):
   ```java
   public class KafkaTestContainer extends AbstractTestContainer {
       // ...
   }
   ```

2. **创建 @Bean** (`config/`):
   ```java
   @Bean
   @ServiceConnection(name = "kafka")
   public KafkaContainer kafkaContainer() {
       // ...
   }
   ```

3. **更新 AutoConfiguration** (`autoconfigure/`):
   ```java
   @ConditionalOnClass(KafkaContainer.class)
   public class KafkaAutoConfiguration {
       // ...
   }
   ```

4. **更新 AutoConfiguration.imports**:
   ```
   com.patra.spring.boot.starter.test.autoconfigure.KafkaAutoConfiguration
   ```

### 8.2 新增 TestDataBuilder 的步骤

1. **继承基类**:
   ```java
   public class ArticleTestBuilder extends TestDataBuilder<Article> {
       // 字段和默认值
   }
   ```

2. **实现 build()**:
   ```java
   @Override
   public Article build() {
       return Article.builder()
           .id(id)
           .title(title)
           .build();
   }
   ```

3. **添加链式方法**:
   ```java
   public ArticleTestBuilder withTitle(String title) {
       this.title = title;
       return this;
   }
   ```

---

**模型版本**: 1.0.0
**最后更新**: 2025-11-09
**维护者**: Patra 架构团队