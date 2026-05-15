# Patra Batch Starter

Spring Batch 批处理基础设施，提供自动配置、独立数据源支持、可观测性集成。

## 功能特性

- **零配置启动**：基于 `DefaultBatchConfiguration` 自动配置核心组件
- **Schema 自动初始化**：检测并创建 Spring Batch 元数据表，幂等且线程安全
- **独立数据源**：支持将 Batch 元数据存储到独立数据库，与业务数据隔离
- **同步执行**：Spring Batch 6.0 默认使用 `SyncTaskExecutor`，适配 XXL-Job 调度
- **原生可观测性**：自动注入 `ObservationRegistry`，创建 Job/Step 级别的 trace 和 span
- **断点续传**：基于 Spring Batch 原生能力，支持 Job 失败后重启
- **幂等控制**：通过 timestamp 参数控制 Job 实例创建策略

## 快速开始

### 添加依赖

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-batch</artifactId>
    <version>${patra.version}</version>
</dependency>
```

### 定义 Job

```java
@Configuration
public class MyJobConfig {

    @Bean
    public Job myJob(JobRepository jobRepository, Step myStep) {
        return new JobBuilder("myJob", jobRepository)
            .start(myStep)
            .build();
    }

    @Bean
    public Step myStep(JobRepository jobRepository,
                       PlatformTransactionManager transactionManager) {
        return new StepBuilder("myStep", jobRepository)
            .<Input, Output>chunk(100)
            .transactionManager(transactionManager)
            .reader(reader())
            .processor(processor())
            .writer(writer())
            .build();
    }
}
```

### 启动 Job

```java
@Service
@RequiredArgsConstructor
public class MyJobService {

    private final JobOperatorHelper jobOperatorHelper;
    private final Job myJob;

    public Long runJob(String param) {
        return jobOperatorHelper.launch(myJob, Map.of("param", param));
    }
}
```

## 配置属性

```yaml
patra:
  batch:
    enabled: true                    # 总开关（默认启用）
    table-prefix: "BATCH_"           # 元数据表前缀
    chunk:
      default-size: 5000             # 默认 Chunk 大小
      max-size: 10000                # 最大 Chunk 大小
    schema:
      initialize: true               # 自动初始化 Schema（默认启用）
    import-limit:
      max-records: -1                # 最大导入记录数（-1=不限制，正整数=限制后自动终止）
```

## 独立数据源

支持将 Spring Batch 元数据存储到独立数据库，实现与业务数据隔离。

### 配置方式

```yaml
patra:
  batch:
    datasource:
      url: jdbc:mysql://batch-db:3306/batch_meta
      username: batch_user
      password: batch_password
      driver-class-name: com.mysql.cj.jdbc.Driver  # 可选，自动推断
      hikari:
        maximum-pool-size: 5         # 最大连接数（默认 5）
        minimum-idle: 2              # 最小空闲连接（默认 2）
        connection-timeout: 30000    # 连接超时（毫秒）
        idle-timeout: 600000         # 空闲超时（毫秒）
        max-lifetime: 1800000        # 最大生存时间（毫秒）
```

### 数据源优先级

1. 用户自定义 `batchDataSource` Bean（最高）
2. `patra.batch.datasource.*` 配置创建的数据源
3. 主数据源 `@Primary DataSource`（默认回退）

### 应用场景

| 场景 | 建议 |
|------|------|
| 单体应用 | 使用主数据源（默认） |
| 微服务集群共享元数据 | 配置独立数据源 |
| 元数据与业务数据隔离 | 配置独立数据源 |

## Schema 管理

支持自动检测并创建 Spring Batch 元数据表，保证幂等性。

### 初始化逻辑

1. 检查 `BATCH_JOB_INSTANCE` 表是否存在
2. 不存在则执行 `db/batch/schema-mysql.sql` 创建所有元数据表
3. 已存在则跳过（幂等）

### 配置方式

```yaml
patra:
  batch:
    schema:
      initialize: true   # 默认启用
```

### 禁用场景

| 场景 | 建议 |
|------|------|
| Flyway/Liquibase 管理 Schema | 设置 `initialize: false` |
| 多服务共享数据库，仅指定服务初始化 | 非初始化服务设置 `initialize: false` |
| 使用预配置数据库（Schema 已存在） | 设置 `initialize: false` |

### 幂等性保证

- Schema SQL 使用 `CREATE TABLE IF NOT EXISTS` 语法
- 初始化器内部使用 `AtomicBoolean` 保证单次执行
- 多服务并发启动时，首个检测到表不存在的服务创建表，其他服务自动跳过

## JobOperatorHelper

封装 JobOperator 调用逻辑，简化 Job 启动。

> **Spring Batch 6.0 迁移说明**：`JobLauncher` 自 Spring Batch 6.0 起已弃用，`JobOperator` 现在扩展 `JobLauncher` 接口，两者功能合并。

### 方法签名

```java
// 使用 Map 参数启动 Job（每次创建新实例）
Long launch(Job job, Map<String, Object> params)

// 使用 Map 参数启动 Job（控制幂等性）
Long launch(Job job, Map<String, Object> params, boolean addTimestamp)

// 使用强类型 JobParams 启动 Job（推荐）
Long launch(Job job, JobParams params, boolean addTimestamp)

// 查询执行状态
Optional<JobExecution> findJobExecution(Long executionId)
```

### JobParams 接口（推荐）

定义强类型的 Job 参数类，实现 `JobParams` 标记接口：

```java
@Data
@Builder
public class MyJobParams implements JobParams {
    private String filePath;
    private String version;
    private Integer batchSize;
}

// 使用
MyJobParams params = MyJobParams.builder()
    .filePath("/data/input.xml")
    .version("2025")
    .batchSize(1000)
    .build();

jobOperatorHelper.launch(job, params, false);
```

**支持的字段类型**：
- `String`、`Long`、`Integer`、`Double`
- `LocalDate`、`LocalDateTime`、`Instant`

**优势**：
- 编译时类型检查
- IDE 自动补全
- 参数验证集中管理

### 幂等控制

| addTimestamp | 行为 | 适用场景 |
|--------------|------|----------|
| `true`（默认） | 每次创建新 JobInstance | 可重复执行的任务 |
| `false` | 相同参数只执行一次 | 幂等性保证 |

### 使用示例

```java
// 默认：每次执行创建新实例
jobOperatorHelper.launch(job, Map.of("date", "2024-01-01"));

// 幂等：相同参数只执行一次
jobOperatorHelper.launch(job, Map.of("date", "2024-01-01"), false);

// 查询执行状态
jobOperatorHelper.findJobExecution(executionId)
    .ifPresent(execution -> {
        log.info("Status: {}", execution.getStatus());
    });
```

## 可观测性

### 原生 Observation 支持

当 `ObservationRegistry` Bean 存在时（通常由 `patra-spring-boot-starter-observability` 提供），自动启用 Spring Batch 原生可观测性：

- **Job 级别 Trace**：每个 Job 执行创建独立 trace
- **Step 级别 Span**：每个 Step 执行创建子 span
- **零配置**：无需修改 Job 定义代码

### 进度指标（Metrics）

当 `MeterRegistry` Bean 存在时，自动注册 `BatchProgressMetricsListener`，在 Step 执行结束时记录累计统计指标：

| 指标名称 | 类型 | 说明 |
|---------|------|------|
| `batch.step.items.read` | Counter | 累计读取数量 |
| `batch.step.items.written` | Counter | 累计写入数量 |
| `batch.step.items.skipped` | Counter | 累计跳过数量 |
| `batch.step.commits` | Counter | 累计提交次数 |
| `batch.step.rollbacks` | Counter | 累计回滚次数 |

**标签**：`job.name`（Job 名称）、`step.name`（Step 名称）

**与内置指标的互补关系**：
- 内置指标（`spring.batch.*`）：Timer 类型，记录执行耗时
- 补充指标（`batch.step.*`）：Counter 类型，记录数量统计

### 启用方式

添加 observability 依赖：

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-observability</artifactId>
</dependency>
```

## 异常处理

| 异常类 | HTTP 状态 | 说明 |
|--------|----------|------|
| `BatchJobExecutionException` | 500 | Job 启动或执行失败 |

### 错误码

| 错误码 | HTTP 状态 | 说明 |
|--------|----------|------|
| `BATCH-0500` | 500 | 任务执行失败 |
| `BATCH-0409` | 409 | 任务已在运行中 |
| `BATCH-0404` | 404 | 任务不存在 |

### 使用示例

```java
try {
    jobOperatorHelper.launch(job, params);
} catch (BatchJobExecutionException e) {
    log.error("Job 执行失败: {}", e.getMessage(), e);
}
```

## 设计决策

### Spring Batch 6.0 JobOperator 迁移

**背景**：`JobLauncher` 自 Spring Batch 6.0 起已弃用，将在 6.2+ 版本移除。

**变更**：
- `JobOperator` 现在扩展 `JobLauncher` 接口，两者功能合并
- `JdbcDefaultBatchConfiguration` 自动提供 `JobOperator` Bean
- 不再需要手动创建 `JobLauncher` Bean

**优势**：
- API 统一，减少冗余 Bean
- `JobOperator` 提供更完整的 Job 管理能力（restart、stop、abandon）
- 默认使用 `SyncTaskExecutor`，适配 XXL-Job 调度

### 不使用 @EnableBatchProcessing

**原因**：Spring Boot 4.0 推荐继承 `JdbcDefaultBatchConfiguration`（或 `DefaultBatchConfiguration`）而非使用 `@EnableBatchProcessing` 注解。

**优势**：
- 更灵活的配置覆盖能力
- 避免与 Spring Boot 自动配置冲突
- 更好的条件化配置支持

## 模块结构

```
patra-spring-boot-starter-batch/
├── autoconfigure/
│   ├── BatchAutoConfiguration.java               # 核心自动配置
│   ├── BatchDataSourceConfiguration.java         # 独立数据源配置
│   ├── BatchSchemaInitializerConfiguration.java  # Schema 初始化配置
│   └── BatchProgressMetricsAutoConfiguration.java # 进度指标自动配置
├── config/
│   └── BatchProperties.java                      # 配置属性
├── core/
│   ├── JobOperatorHelper.java                    # Job 启动辅助类
│   └── JobParams.java                            # 强类型参数标记接口
├── metrics/
│   ├── BatchProgressMetricNames.java             # 指标名称常量
│   └── BatchProgressMetricsListener.java         # 进度指标监听器
├── schema/
│   └── BatchSchemaInitializer.java               # Schema 初始化器
├── exception/
│   ├── BatchErrorCode.java                       # 错误码枚举
│   └── BatchJobExecutionException.java           # 执行异常
└── resources/db/batch/
    └── schema-mysql.sql                          # MySQL Schema 脚本
```

## 依赖关系

- `spring-boot-starter-batch`：Spring Batch 核心
- `patra-common-core`：错误码框架
- `patra-spring-boot-starter-core`：核心基础设施
- `patra-spring-boot-starter-redisson`：分布式锁支持
- `micrometer-observation`（可选）：可观测性支持
- `mysql-connector-j`（可选）：MySQL 驱动

## 设计原则

1. **零配置启动**：默认配置即可运行，无需额外配置
2. **独立数据源**：支持元数据与业务数据隔离
3. **调度适配**：同步执行适配 XXL-Job 等外部调度器
4. **可观测性**：原生 Observation 支持，无代码侵入
