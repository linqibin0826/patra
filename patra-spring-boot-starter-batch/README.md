# patra-spring-boot-starter-batch

Spring Batch 批处理基础设施 Starter，为 Patra 医学文献数据平台提供标准化的批处理能力。

## 核心特性

- ✅ **Spring Batch 自动配置**：开箱即用的 JobRepository、JobLauncher 等核心组件
- ✅ **分布式锁支持**：集成 `patra-spring-boot-starter-redisson`，使用 `@DistributedLock` 防止并发执行
- ✅ **日志监听器**：结构化日志（Job/Step 执行日志，基础设施层调试工具）
- ✅ **断点续传**：基于 Spring Batch JobRepository 自动管理执行状态
- ✅ **Chunk 批次处理**：支持读取-处理-写入模式
- ✅ **重试和跳过策略**：支持批次级别重试、记录级别跳过
- ✅ **XXL-Job 集成**：提供 `JobLauncherHelper` 简化定时任务触发

**可选功能**：
- 可观测性（追踪、指标）: 添加 `patra-spring-boot-starter-observability` 依赖自动启用

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-batch</artifactId>
</dependency>
```

### 2. 配置数据源

Spring Batch 需要数据库存储元数据（Job 执行记录、Step 执行状态等）。

#### 方式一：使用主数据源（默认）

最简配置，Spring Batch 元数据表与业务数据同库：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/patra_catalog
    username: root
    password: password

  batch:
    jdbc:
      initialize-schema: always  # 自动创建 Spring Batch 元数据表
```

#### 方式二：独立数据源（推荐生产环境）

将 Spring Batch 元数据存储到独立数据库（如 `patra_batch_meta`），避免元数据表污染业务库：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/patra_catalog
    username: root
    password: password

  batch:
    jdbc:
      initialize-schema: always

patra:
  batch:
    table-prefix: BATCH_  # 表前缀（可选）
    datasource:
      url: jdbc:mysql://localhost:3306/patra_batch_meta
      username: batch_user
      password: batch_password
      driver-class-name: com.mysql.cj.jdbc.Driver  # 可选，自动检测
      hikari:
        maximum-pool-size: 5    # 默认 5
        minimum-idle: 2         # 默认 2
        connection-timeout: 30000  # 默认 30s
        idle-timeout: 600000    # 默认 10min
```

#### 数据源选择优先级

1. **用户自定义 Bean**（最高）：`@Qualifier("batchDataSource")` 标注的 DataSource
2. **配置创建**：`patra.batch.datasource.url` 配置时自动创建
3. **主数据源**（默认回退）：`@Primary` 标注的 DataSource

### 3. 定义 Job 配置

```java
@Configuration
@RequiredArgsConstructor
public class MeshImportJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public Job meshImportJob(Step importDescriptorStep) {
        return new JobBuilder("meshImportJob", jobRepository)
            .start(importDescriptorStep)
            .build();
    }

    @Bean
    public Step importDescriptorStep(
            MeshDescriptorItemReader reader,
            MeshDescriptorItemProcessor processor,
            MeshDescriptorItemWriter writer
    ) {
        return new StepBuilder("importDescriptorStep", jobRepository)
            .<MeshDescriptor, MeshDescriptorDO>chunk(1000, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
    }
}
```

### 4. 实现 ItemReader/Processor/Writer

```java
@Component
@RequiredArgsConstructor
public class MeshDescriptorItemReader implements ItemReader<MeshDescriptor> {

    private final XmlParserPort xmlParser;
    private Iterator<MeshDescriptor> iterator;
    private boolean initialized = false;

    @Override
    public MeshDescriptor read() throws Exception {
        if (!initialized) {
            List<MeshDescriptor> data = xmlParser.parseDescriptors(...);
            this.iterator = data.iterator();
            this.initialized = true;
        }

        return iterator.hasNext() ? iterator.next() : null;
    }
}
```

### 5. 通过 XXL-Job 触发

```java
@Component
@RequiredArgsConstructor
public class MeshImportJobHandler {

    private final JobLauncherHelper jobLauncherHelper;
    private final Job meshImportJob;

    @XxlJob("meshImportJob")
    @DistributedLock(
        key = "batch:job:mesh-import",
        leaseTime = 7200000  // 2 小时
    )
    public void execute() {
        jobLauncherHelper.launch(meshImportJob, Map.of(
            "year", "2024"
        ));
    }
}
```

## 配置项

```yaml
patra:
  batch:
    enabled: true        # 是否启用批处理自动配置（默认 true）
    table-prefix: BATCH_ # Spring Batch 表前缀（默认 BATCH_）

    # 独立数据源配置（可选）
    datasource:
      url: jdbc:mysql://localhost:3306/patra_batch_meta
      username: batch_user
      password: batch_password
      driver-class-name: com.mysql.cj.jdbc.Driver  # 可选
      hikari:
        maximum-pool-size: 5    # 最大连接数（默认 5）
        minimum-idle: 2         # 最小空闲连接（默认 2）
        connection-timeout: 30000  # 连接超时毫秒（默认 30s）
        idle-timeout: 600000    # 空闲超时毫秒（默认 10min）

    # 可观测性配置
    observability:
      tracing:
        enabled: true  # SkyWalking 追踪
      metrics:
        enabled: true  # Micrometer 指标
      logging:
        enabled: true  # 结构化日志
```

## 核心组件

### JobLauncherHelper

封装 JobLauncher 调用逻辑，简化 Job 启动：

```java
@Autowired
private JobLauncherHelper jobLauncherHelper;

// 启动 Job（每次新实例）
Long executionId = jobLauncherHelper.launch(job, params);

// 启动 Job（幂等执行）
Long executionId = jobLauncherHelper.launch(job, params, false);
```

### 可观测性监听器

自动注册的监听器：
- `SkyWalkingJobListener`：记录 Job/Step 追踪 Span
- `MetricsJobListener`：收集 Micrometer 指标
- `LoggingJobListener`：输出结构化日志

## 设计文档

详细设计文档请参考：
- [架构设计文档](../../docs/patra-spring-boot-starter-batch/architecture-design.md)
- [迁移指南](../../docs/patra-spring-boot-starter-batch/migration-guide.md)

## 依赖关系

```
patra-spring-boot-starter-batch
├── spring-boot-starter-batch (Spring Batch 核心)
├── patra-spring-boot-starter-core (错误处理、可观测性)
├── patra-spring-boot-starter-redisson (分布式锁)
└── mysql-connector-j (MySQL JDBC 驱动)
```

## 许可证

本项目采用 MIT 许可证。
