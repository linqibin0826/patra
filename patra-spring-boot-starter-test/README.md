# Patra Spring Boot Starter - Testing

统一测试基础设施 Starter，提供 TestContainers 容器工厂、ArchUnit 架构规则和测试工具集。

## 功能概览

- **TestContainers 容器初始化器**: JVM 级别单例容器，支持 MySQL、RocketMQ、MinIO
- **ArchUnit 架构规则**: 六边形架构规则、测试规范规则
- **测试工具集成**: JUnit 5、AssertJ、Mockito、Awaitility、WireMock

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

### 2. 使用容器初始化器

创建服务特定的容器初始化器：

```java
// 继承 MySQLContainerInitializer，指定数据库名
public class CatalogMySQLContainerInitializer extends MySQLContainerInitializer {
    @Override
    protected String getDatabaseName() {
        return "patra_catalog";
    }
}

// 继承 RocketMQContainerInitializer，指定需要创建的 Topic
public class IngestRocketMQContainerInitializer extends RocketMQContainerInitializer {
    @Override
    protected String[] getTopicsToCreate() {
        return new String[] {"INGEST_TASK_READY", "INGEST_PUBLICATION_READY"};
    }
}

// 继承 MinIOContainerInitializer，指定存储桶名
public class CatalogMinIOContainerInitializer extends MinIOContainerInitializer {
    @Override
    protected String getBucketName() {
        return "patra-catalog-cache";
    }
}
```

在测试类中使用：

```java
@SpringBootTest
@ContextConfiguration(initializers = {
    CatalogMySQLContainerInitializer.class,
    IngestRocketMQContainerInitializer.class
})
class MyIntegrationTest {
    // 测试代码
}
```

### 3. 使用 ArchUnit 规则

```java
@AnalyzeClasses(packages = "com.patra.catalog", importOptions = ImportOption.DoNotIncludeTests.class)
class CatalogArchitectureTest {

    private static final String SERVICE_NAME = "catalog";

    @ArchTest
    static final ArchTests hexagonalRules = ArchTests.in(HexagonalArchitectureRules.class);

    @ArchTest
    static final ArchTests testingRules = ArchTests.in(TestingRules.class);

    @ArchTest
    static final ArchRule domainShouldNotDependOnInfra =
        HexagonalArchitectureRules.domainShouldNotDependOnInfrastructure(SERVICE_NAME);

    @ArchTest
    static final ArchRule adapterShouldOnlyDependOnApp =
        HexagonalArchitectureRules.adapterShouldOnlyDependOnApplication(SERVICE_NAME);
}
```

## 核心组件

### 容器初始化器

| 类 | 说明 |
|---|---|
| `MySQLContainerInitializer` | MySQL 容器初始化器基类，支持 Flyway 迁移 |
| `RocketMQContainerInitializer` | RocketMQ 容器初始化器基类，支持 Topic 自动创建 |
| `MinIOContainerInitializer` | MinIO 容器初始化器基类，支持存储桶自动创建 |
| `ContainerRegistry` | JVM 级别容器注册中心，确保单例 |
| `ContainerType` | 支持的容器类型枚举 |

### ArchUnit 规则

| 类 | 规则数量 | 说明 |
|---|---|---|
| `HexagonalArchitectureRules` | 18 条 | 六边形架构依赖规则、命名规范、封装规则 |
| `TestingRules` | 6 条 | 测试命名规范、测试比例、Mock 策略 |

### 自动配置

| 类 | 说明 |
|---|---|
| `TestAutoConfiguration` | 测试环境自动配置入口 |
| `TestMeterRegistryAutoConfiguration` | 测试环境 SimpleMeterRegistry 自动配置 |

## 传递依赖

引入此 Starter 后，以下依赖将自动可用（无需重复声明）：

- JUnit Jupiter 5
- AssertJ
- Mockito (core + junit-jupiter)
- Spring Boot Test
- Spring Boot WebMvc Test（含 RestTestClient 支持）
- Spring Boot RestClient Test
- Spring Boot Data JPA Test
- Spring Boot JDBC Test
- Spring Boot Flyway Test
- TestContainers (core + junit-jupiter + mysql)
- ArchUnit JUnit 5
- Awaitility
- WireMock
- Micrometer Core

## 容器镜像版本

| 容器 | 镜像 |
|---|---|
| MySQL | `mysql:8.0.36` |
| RocketMQ | `apache/rocketmq:5.3.1` |
| MinIO | `minio/minio:RELEASE.2024-01-18T22-51-28Z` |

## 设计原则

1. **JVM 级别单例**: 所有容器在 JVM 生命周期内只启动一次，多个测试类共享
2. **配置化扩展**: 通过继承和重写模板方法实现服务特定配置
3. **线程安全**: `ContainerRegistry` 使用 `ConcurrentHashMap` 保证并发安全
4. **快速启动**: 容器预热 + 连接池复用，显著减少测试启动时间

## 环境要求

- Docker Desktop 运行中
- 至少 4GB 可用内存
- 首次启动需要 ~30-40 秒（拉取镜像 + 启动容器）
