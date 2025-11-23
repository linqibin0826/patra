# patra-spring-boot-starter-batch 架构设计

**版本**: v1.0.0
**创建日期**: 2025-11-23
**状态**: 设计阶段
**作者**: Patra Team

---

## 📋 文档目录

- [一、概述](#一概述)
- [二、设计目标](#二设计目标)
- [三、技术选型](#三技术选型)
- [四、核心功能](#四核心功能)
- [五、架构设计](#五架构设计)
- [六、详细设计](#六详细设计)
- [七、配置管理](#七配置管理)
- [八、使用示例](#八使用示例)
- [九、实施计划](#九实施计划)
- [十、风险评估](#十风险评估)

---

## 一、概述

### 1.1 背景

Patra 医学文献数据平台需要处理大量批处理任务，包括：
- **MeSH 数据导入**：35 万条主题词、限定词、树形编号等（patra-catalog）
- **文献数据导入**：从 PubMed、EPMC 等数据源批量导入文献（patra-ingest）
- **引用关系数据处理**：批量处理文献引用关系（patra-catalog）
- **数据清洗和转换**：定期数据质量检查和修复（各服务）

当前 patra-catalog 采用自定义批处理方案，存在以下问题：
- ❌ 重复造轮子（断点续传、重试、进度跟踪需自己实现）
- ❌ 维护成本高（复杂的状态机、批次管理）
- ❌ 难以扩展（并行处理、分区策略等需大量开发）
- ❌ 不够标准化（团队成员需学习项目特定框架）

### 1.2 设计理念

创建 `patra-spring-boot-starter-batch`，遵循以下原则：

1. **标准化优先**：采用 Spring Batch 行业标准，避免自定义框架
2. **开箱即用**：提供自动配置，最小化业务服务配置
3. **可复用性**：所有服务（catalog、ingest 等）都能使用
4. **六边形架构友好**：
   - Domain 层：专注业务规则验证（可选）
   - Application 层：定义 Job 配置和编排
   - Infrastructure 层：实现 ItemReader/Writer/Processor
   - Starter 提供：基础设施自动配置
5. **可观测性**：集成 SkyWalking、Micrometer、日志记录
6. **分布式支持**：支持多实例并发执行（基于 Redisson 分布式锁）

### 1.3 目标用户

- **后端开发者**：实现具体的批处理 Job（如 MeSH 导入）
- **架构师**：设计批处理方案和编排策略
- **运维人员**：监控批处理任务执行、排查问题

---

## 二、设计目标

### 2.1 功能目标

| 目标 | 描述 | 优先级 |
|------|------|--------|
| **Spring Batch 自动配置** | 自动配置 JobRepository、JobLauncher、DataSource 等核心组件 | P0 |
| **断点续传** | 支持任务中断后从上次位置继续执行 | P0 |
| **批次处理** | 支持 Chunk-oriented 处理（读取-处理-写入） | P0 |
| **分布式锁** | 基于 Redisson 防止多实例并发执行同一 Job | P0 |
| **可观测性集成** | 集成 SkyWalking 追踪、Micrometer 指标、日志记录 | P1 |
| **重试和跳过策略** | 支持批次级别重试、记录级别跳过 | P1 |
| **并行处理** | 支持 Multi-threaded Step、Partitioning | P2 |
| **Job 编排** | 支持 Step 顺序、条件跳转、并行执行 | P1 |
| **XXL-Job 集成** | 支持通过 XXL-Job 触发批处理任务 | P1 |

### 2.2 非功能目标

| 目标 | 指标 | 优先级 |
|------|------|--------|
| **性能** | 处理速度 ≥ 1000 条/秒（基于硬件） | P1 |
| **可靠性** | 批次处理失败率 ≤ 1% | P0 |
| **可扩展性** | 支持水平扩展（多实例并行） | P1 |
| **可维护性** | 配置简单，代码清晰，文档完善 | P0 |
| **安全性** | 元数据表不泄露敏感信息 | P1 |

### 2.3 约束条件

- **技术栈**：Spring Boot 3.5.7、Spring Batch 5.2.x、JDK 25
- **数据库**：MySQL 8.0+（用于 Spring Batch 元数据表）
- **架构风格**：六边形架构 + DDD
- **集成组件**：SkyWalking、Micrometer、Redisson、XXL-Job

---

## 三、技术选型

### 3.1 Spring Batch 核心组件

| 组件 | 用途 | 配置方式 |
|------|------|---------|
| **JobRepository** | 元数据持久化（Job、Step 执行记录） | 自动配置（基于 DataSource） |
| **JobLauncher** | Job 启动器 | 自动配置 |
| **JobExplorer** | Job 执行历史查询 | 自动配置 |
| **JobOperator** | Job 运维操作（停止、重启） | 自动配置 |
| **TransactionManager** | 事务管理器 | 复用 MyBatis DataSource |

### 3.2 依赖关系

```
patra-spring-boot-starter-batch
├── spring-boot-starter-batch (Spring Batch 核心)
├── patra-spring-boot-starter-core (错误处理、可观测性)
├── redisson-spring-boot-starter (分布式锁)
├── mysql-connector-j (MySQL JDBC 驱动)
└── micrometer-core (指标收集)
```

### 3.3 与现有 Starter 的关系

| Starter | 关系 | 说明 |
|---------|------|------|
| `patra-spring-boot-starter-core` | **依赖** | 复用错误处理框架、Clock、可观测性 |
| `patra-spring-boot-starter-mybatis` | **可选依赖** | ItemWriter 可使用 MyBatis Mapper |
| `redisson-spring-boot-starter` | **依赖** | 提供分布式锁能力 |

---

## 四、核心功能

### 4.1 功能清单

#### 4.1.1 自动配置 (P0)

**BatchAutoConfiguration**：
- 配置 Spring Batch 元数据表（BATCH_JOB_INSTANCE、BATCH_JOB_EXECUTION 等）
- 自动创建 JobRepository、JobLauncher Bean
- 配置事务管理器（复用业务 DataSource）

**DistributedLockAutoConfiguration**：
- 配置基于 Redisson 的分布式锁
- 提供 `@DistributedJobLock` 注解，防止多实例并发执行

**ObservabilityAutoConfiguration**：
- 配置 SkyWalking 追踪拦截器（记录 Job/Step Span）
- 配置 Micrometer 指标收集器（Job 执行耗时、成功/失败计数）
- 配置日志拦截器（记录 Job/Step 执行日志）

#### 4.1.2 批处理基础能力 (P0)

**Chunk-oriented 处理**：
- 提供 `AbstractBatchItemReader<T>` 抽象类（简化 ItemReader 实现）
- 提供 `AbstractBatchItemWriter<T>` 抽象类（简化 ItemWriter 实现）
- 提供批次提交策略（可配置批次大小）

**断点续传**：
- 基于 Spring Batch JobRepository 自动管理执行状态
- 支持 JobParameters 唯一标识 Job 实例
- 支持 Step ExecutionContext 存储中间状态

**重试和跳过**：
- 提供 `@RetryableStep` 注解（配置重试策略）
- 提供 `@SkipPolicy` 注解（配置跳过策略）
- 记录跳过的记录到 `batch_skip_records` 表

#### 4.1.3 任务编排 (P1)

**Job 编排 DSL**：
- 支持 Step 顺序执行（`next()`）
- 支持条件跳转（`on().to()`）
- 支持并行执行（`split()`）
- 支持子流程（`flow()`）

**示例**：
```java
@Bean
public Job meshImportJob(JobRepository jobRepository, Step step1, Step step2) {
    return new JobBuilder("meshImportJob", jobRepository)
        .start(step1)
        .next(step2)
        .build();
}
```

#### 4.1.4 分布式锁 (P0)

**DistributedJobLock 注解**：
- 基于 Redisson 实现分布式锁
- 支持自定义锁键（基于 Job 名称 + 业务参数）
- 支持锁超时配置（防止死锁）
- 支持重入锁

**使用方式**：
```java
@XxlJob("meshImportJob")
@DistributedJobLock(key = "mesh-import-#{provenanceCode}")
public void executeMeshImport(String provenanceCode) {
    jobLauncher.run(meshImportJob, new JobParametersBuilder()
        .addString("provenanceCode", provenanceCode)
        .toJobParameters());
}
```

#### 4.1.5 可观测性 (P1)

**SkyWalking 追踪**：
- 自动为每个 Job 创建 Span（`BatchJob: {jobName}`）
- 自动为每个 Step 创建 Span（`BatchStep: {stepName}`）
- 记录 Job/Step 执行耗时、状态、参数

**Micrometer 指标**：
- `batch.job.duration`：Job 执行耗时（Timer）
- `batch.job.status`：Job 执行状态（Counter，tags: status=SUCCESS/FAILED）
- `batch.step.items.read`：Step 读取记录数（Counter）
- `batch.step.items.written`：Step 写入记录数（Counter）
- `batch.step.items.skipped`：Step 跳过记录数（Counter）

**日志记录**：
- Job 启动/完成/失败日志（INFO/ERROR 级别）
- Step 启动/完成日志（INFO 级别）
- 批次提交日志（DEBUG 级别）

#### 4.1.6 XXL-Job 集成 (P1)

**JobLauncherHelper**：
- 封装 JobLauncher 调用逻辑
- 支持从 XXL-Job 参数构建 JobParameters
- 支持异步执行（返回 JobExecution ID）

**示例**：
```java
@Component
@RequiredArgsConstructor
public class MeshImportJobHandler {

    private final JobLauncherHelper jobLauncherHelper;
    private final Job meshImportJob;

    @XxlJob("meshImportJob")
    public void execute() {
        jobLauncherHelper.launch(meshImportJob, Map.of(
            "provenanceCode", "MESH",
            "year", "2024"
        ));
    }
}
```

---

## 五、架构设计

### 5.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                     业务服务 (patra-catalog)                      │
├─────────────────────────────────────────────────────────────────┤
│  Adapter 层                                                       │
│  ├─ XXL-Job Handler: MeshImportJobHandler                       │
│  └─ REST Controller: MeshImportController                       │
├─────────────────────────────────────────────────────────────────┤
│  Application 层                                                   │
│  ├─ Job Configuration: MeshImportJobConfig                      │
│  │   ├─ Job: meshImportJob                                      │
│  │   ├─ Step: importDescriptorStep                              │
│  │   ├─ Step: importQualifierStep                               │
│  │   └─ Step: importTreeNumberStep                              │
│  └─ Job 编排逻辑                                                  │
├─────────────────────────────────────────────────────────────────┤
│  Infrastructure 层                                                │
│  ├─ ItemReader: MeshDescriptorItemReader (XML 解析)             │
│  ├─ ItemProcessor: MeshDescriptorItemProcessor (数据转换)       │
│  └─ ItemWriter: MeshDescriptorItemWriter (批量写入)             │
└─────────────────────────────────────────────────────────────────┘
                              ↓ 依赖
┌─────────────────────────────────────────────────────────────────┐
│              patra-spring-boot-starter-batch                     │
├─────────────────────────────────────────────────────────────────┤
│  自动配置层 (AutoConfiguration)                                  │
│  ├─ BatchAutoConfiguration                                      │
│  │   ├─ JobRepository Bean                                      │
│  │   ├─ JobLauncher Bean                                        │
│  │   ├─ JobExplorer Bean                                        │
│  │   └─ JobOperator Bean                                        │
│  ├─ DistributedLockAutoConfiguration                            │
│  │   ├─ RedissonClient Bean (复用)                              │
│  │   └─ @DistributedJobLock 注解处理器                          │
│  └─ ObservabilityAutoConfiguration                              │
│      ├─ SkyWalking JobListener                                  │
│      ├─ Micrometer JobListener                                  │
│      └─ Logging JobListener                                     │
├─────────────────────────────────────────────────────────────────┤
│  基础组件层                                                       │
│  ├─ JobLauncherHelper: 封装 JobLauncher 调用                    │
│  ├─ AbstractBatchItemReader<T>: ItemReader 抽象基类             │
│  ├─ AbstractBatchItemWriter<T>: ItemWriter 抽象基类             │
│  ├─ JobParametersBuilder: 参数构建器                             │
│  └─ BatchJobLockAspect: 分布式锁 AOP                            │
├─────────────────────────────────────────────────────────────────┤
│  监听器层                                                         │
│  ├─ SkyWalkingJobListener: 追踪 Job/Step                        │
│  ├─ MetricsJobListener: 记录 Micrometer 指标                    │
│  └─ LoggingJobListener: 记录日志                                │
└─────────────────────────────────────────────────────────────────┘
                              ↓ 依赖
┌─────────────────────────────────────────────────────────────────┐
│                       Spring Batch 5.2.x                         │
│  ├─ spring-batch-core                                            │
│  └─ spring-batch-infrastructure                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 5.2 包结构设计

```
patra-spring-boot-starter-batch/
├─ src/main/java/com/patra/starter/batch/
│  ├─ autoconfigure/                    # 自动配置
│  │  ├─ BatchAutoConfiguration.java
│  │  ├─ DistributedLockAutoConfiguration.java
│  │  └─ ObservabilityAutoConfiguration.java
│  ├─ core/                             # 核心组件
│  │  ├─ JobLauncherHelper.java
│  │  ├─ AbstractBatchItemReader.java
│  │  ├─ AbstractBatchItemWriter.java
│  │  └─ BatchJobParametersBuilder.java
│  ├─ lock/                             # 分布式锁
│  │  ├─ DistributedJobLock.java        # 注解
│  │  ├─ BatchJobLockAspect.java        # AOP 实现
│  │  └─ JobLockKeyGenerator.java       # 锁键生成器
│  ├─ listener/                         # 监听器
│  │  ├─ SkyWalkingJobListener.java
│  │  ├─ MetricsJobListener.java
│  │  └─ LoggingJobListener.java
│  ├─ config/                           # 配置类
│  │  └─ BatchProperties.java
│  └─ exception/                        # 异常定义
│     ├─ BatchJobLockException.java
│     └─ BatchJobExecutionException.java
├─ src/main/resources/
│  ├─ META-INF/spring/
│  │  └─ org.springframework.boot.autoconfigure.AutoConfiguration.imports
│  └─ schema-mysql.sql                  # Spring Batch 元数据表 DDL
└─ pom.xml
```

### 5.3 数据库设计

#### Spring Batch 元数据表（由 Spring Batch 自动创建）

| 表名 | 用途 |
|------|------|
| `BATCH_JOB_INSTANCE` | Job 实例表（唯一标识：JobName + JobParameters） |
| `BATCH_JOB_EXECUTION` | Job 执行记录表（记录启动时间、结束时间、状态） |
| `BATCH_JOB_EXECUTION_PARAMS` | Job 参数表（记录 JobParameters） |
| `BATCH_STEP_EXECUTION` | Step 执行记录表（记录读/写/跳过计数） |
| `BATCH_JOB_EXECUTION_CONTEXT` | Job 执行上下文表（断点续传状态） |
| `BATCH_STEP_EXECUTION_CONTEXT` | Step 执行上下文表（批次进度） |

#### 自定义扩展表（可选）

| 表名 | 用途 | 优先级 |
|------|------|--------|
| `batch_skip_records` | 跳过记录表（记录跳过的数据） | P2 |
| `batch_job_lock` | Job 锁表（记录分布式锁持有情况） | P3 |

**`batch_skip_records` 表结构**：
```sql
CREATE TABLE batch_skip_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_execution_id BIGINT NOT NULL,
    step_execution_id BIGINT NOT NULL,
    item_data JSON NOT NULL,               -- 被跳过的数据
    skip_reason TEXT,                       -- 跳过原因
    exception_class VARCHAR(255),           -- 异常类名
    exception_message TEXT,                 -- 异常消息
    created_at TIMESTAMP(6) NOT NULL,
    INDEX idx_job_exec (job_execution_id),
    INDEX idx_step_exec (step_execution_id)
);
```

### 5.4 核心流程设计

#### 5.4.1 Job 启动流程

```
┌─────────────┐
│ XXL-Job     │
│ Trigger     │
└──────┬──────┘
       │
       ↓ @XxlJob
┌────────────────────────────────────┐
│ MeshImportJobHandler               │
│ @DistributedJobLock                │ ← AOP 拦截，获取分布式锁
└─────────┬──────────────────────────┘
          │
          ↓ jobLauncherHelper.launch()
┌────────────────────────────────────┐
│ JobLauncherHelper                  │
│ 1. 构建 JobParameters              │
│ 2. 调用 JobLauncher.run()          │
└─────────┬──────────────────────────┘
          │
          ↓
┌────────────────────────────────────┐
│ Spring Batch JobLauncher           │
│ 1. 检查 Job 是否存在               │
│ 2. 创建 JobExecution               │
│ 3. 触发 JobListener (before)       │
└─────────┬──────────────────────────┘
          │
          ↓
┌────────────────────────────────────┐
│ SkyWalkingJobListener.beforeJob()  │
│ 创建 Span: BatchJob:meshImport     │
└─────────┬──────────────────────────┘
          │
          ↓
┌────────────────────────────────────┐
│ Step 执行                          │
│ 1. importDescriptorStep            │
│ 2. importQualifierStep             │
│ 3. importTreeNumberStep            │
└─────────┬──────────────────────────┘
          │
          ↓
┌────────────────────────────────────┐
│ JobListener.afterJob()             │
│ 1. 记录 Micrometer 指标            │
│ 2. 记录日志                        │
│ 3. 结束 SkyWalking Span            │
└─────────┬──────────────────────────┘
          │
          ↓ 释放分布式锁
┌────────────────────────────────────┐
│ BatchJobLockAspect.afterReturning()│
└────────────────────────────────────┘
```

#### 5.4.2 Step 执行流程（Chunk-oriented）

```
┌─────────────────────────────────────┐
│ Step: importDescriptorStep          │
└─────────────┬───────────────────────┘
              │
              ↓
┌─────────────────────────────────────┐
│ Chunk 循环 (每批 1000 条)            │
│ ┌───────────────────────────────┐   │
│ │ 1. ItemReader.read()          │   │
│ │    ↓                          │   │
│ │ 2. ItemProcessor.process()    │   │
│ │    ↓                          │   │
│ │ 3. 收集到 Chunk (1000 条)     │   │
│ └───────────────────────────────┘   │
│              ↓                      │
│ ┌───────────────────────────────┐   │
│ │ 4. ItemWriter.write(Chunk)    │   │
│ │    ↓                          │   │
│ │ 5. 事务提交                   │   │
│ │    ↓                          │   │
│ │ 6. 更新 StepExecution         │   │
│ └───────────────────────────────┘   │
│              ↓                      │
│         重复直到读完                 │
└─────────────────────────────────────┘
```

---

## 六、详细设计

### 6.1 核心类设计

#### 6.1.1 BatchAutoConfiguration

**职责**：自动配置 Spring Batch 核心组件

```java
package com.patra.starter.batch.autoconfigure;

@Configuration
@ConditionalOnProperty(prefix = "patra.batch", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableBatchProcessing
@AutoConfiguration
public class BatchAutoConfiguration {

    /**
     * 配置 JobRepository（基于数据库）
     */
    @Bean
    public JobRepository jobRepository(
            DataSource dataSource,
            PlatformTransactionManager transactionManager
    ) throws Exception {
        JobRepositoryFactoryBean factory = new JobRepositoryFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTransactionManager(transactionManager);
        factory.setIsolationLevelForCreate("ISOLATION_READ_COMMITTED");
        factory.setTablePrefix("BATCH_");  // 表前缀
        factory.afterPropertiesSet();
        return factory.getObject();
    }

    /**
     * 配置 JobLauncher（异步执行）
     */
    @Bean
    public JobLauncher jobLauncher(JobRepository jobRepository) throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(new SimpleAsyncTaskExecutor());  // 异步执行
        launcher.afterPropertiesSet();
        return launcher;
    }

    /**
     * 配置 JobExplorer（查询 Job 历史）
     */
    @Bean
    public JobExplorer jobExplorer(DataSource dataSource) throws Exception {
        JobExplorerFactoryBean factory = new JobExplorerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTablePrefix("BATCH_");
        factory.afterPropertiesSet();
        return factory.getObject();
    }

    /**
     * 配置 JobOperator（运维操作）
     */
    @Bean
    public JobOperator jobOperator(
            JobExplorer jobExplorer,
            JobLauncher jobLauncher,
            JobRepository jobRepository,
            JobRegistry jobRegistry
    ) {
        SimpleJobOperator operator = new SimpleJobOperator();
        operator.setJobExplorer(jobExplorer);
        operator.setJobLauncher(jobLauncher);
        operator.setJobRepository(jobRepository);
        operator.setJobRegistry(jobRegistry);
        return operator;
    }
}
```

#### 6.1.2 DistributedLockAutoConfiguration

**职责**：配置分布式锁支持

```java
package com.patra.starter.batch.autoconfigure;

@Configuration
@ConditionalOnProperty(prefix = "patra.batch.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(RedissonClient.class)
@AutoConfiguration
public class DistributedLockAutoConfiguration {

    /**
     * 配置分布式锁 AOP 切面
     */
    @Bean
    public BatchJobLockAspect batchJobLockAspect(
            RedissonClient redissonClient,
            BatchProperties properties
    ) {
        return new BatchJobLockAspect(redissonClient, properties);
    }

    /**
     * 配置锁键生成器
     */
    @Bean
    public JobLockKeyGenerator jobLockKeyGenerator() {
        return new JobLockKeyGenerator();
    }
}
```

#### 6.1.3 ObservabilityAutoConfiguration

**职责**：配置可观测性组件

```java
package com.patra.starter.batch.autoconfigure;

@Configuration
@AutoConfiguration
public class ObservabilityAutoConfiguration {

    /**
     * 配置 SkyWalking Job 监听器
     */
    @Bean
    @ConditionalOnProperty(prefix = "patra.batch.observability.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SkyWalkingJobListener skyWalkingJobListener() {
        return new SkyWalkingJobListener();
    }

    /**
     * 配置 Micrometer Job 监听器
     */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "patra.batch.observability.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MetricsJobListener metricsJobListener(MeterRegistry meterRegistry) {
        return new MetricsJobListener(meterRegistry);
    }

    /**
     * 配置日志 Job 监听器
     */
    @Bean
    @ConditionalOnProperty(prefix = "patra.batch.observability.logging", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LoggingJobListener loggingJobListener() {
        return new LoggingJobListener();
    }
}
```

#### 6.1.4 JobLauncherHelper

**职责**：封装 JobLauncher 调用逻辑

```java
package com.patra.starter.batch.core;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobLauncherHelper {

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;

    /**
     * 启动 Job
     *
     * @param job 要执行的 Job
     * @param params Job 参数（Map 形式）
     * @return JobExecution ID
     */
    public Long launch(Job job, Map<String, Object> params) {
        try {
            JobParameters jobParameters = buildJobParameters(params);
            JobExecution execution = jobLauncher.run(job, jobParameters);

            log.info("Job [{}] 启动成功，执行 ID: {}", job.getName(), execution.getId());
            return execution.getId();

        } catch (Exception e) {
            log.error("Job [{}] 启动失败", job.getName(), e);
            throw new BatchJobExecutionException("Failed to launch job: " + job.getName(), e);
        }
    }

    /**
     * 构建 JobParameters
     */
    private JobParameters buildJobParameters(Map<String, Object> params) {
        JobParametersBuilder builder = new JobParametersBuilder();

        params.forEach((key, value) -> {
            if (value instanceof String) {
                builder.addString(key, (String) value);
            } else if (value instanceof Long) {
                builder.addLong(key, (Long) value);
            } else if (value instanceof Double) {
                builder.addDouble(key, (Double) value);
            } else if (value instanceof Date) {
                builder.addDate(key, (Date) value);
            } else {
                builder.addString(key, value.toString());
            }
        });

        // 添加时间戳，确保每次执行都是新实例
        builder.addLong("timestamp", System.currentTimeMillis());

        return builder.toJobParameters();
    }

    /**
     * 查询 Job 执行状态
     */
    public Optional<JobExecution> findJobExecution(Long executionId) {
        return Optional.ofNullable(jobExplorer.getJobExecution(executionId));
    }

    /**
     * 重启失败的 Job
     */
    public Long restart(Long executionId) {
        JobExecution failedExecution = jobExplorer.getJobExecution(executionId);
        if (failedExecution == null) {
            throw new IllegalArgumentException("Job execution not found: " + executionId);
        }

        if (failedExecution.getStatus() != BatchStatus.FAILED) {
            throw new IllegalStateException("Job execution is not in FAILED status: " + executionId);
        }

        // TODO: 实现重启逻辑
        throw new UnsupportedOperationException("Restart not implemented yet");
    }
}
```

#### 6.1.5 DistributedJobLock 注解

**职责**：声明式分布式锁

```java
package com.patra.starter.batch.lock;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedJobLock {

    /**
     * 锁键（支持 SpEL 表达式）
     * 例如：batch:job:mesh-import:#{#provenanceCode}
     */
    String key();

    /**
     * 锁超时时间（秒）
     */
    long timeout() default 3600;

    /**
     * 等待锁的最大时间（秒），0 表示不等待
     */
    long waitTime() default 0;

    /**
     * 是否在获取锁失败时抛出异常
     */
    boolean throwExceptionOnFailure() default true;
}
```

#### 6.1.6 BatchJobLockAspect

**职责**：AOP 实现分布式锁

```java
package com.patra.starter.batch.lock;

@Aspect
@Slf4j
@RequiredArgsConstructor
public class BatchJobLockAspect {

    private final RedissonClient redissonClient;
    private final BatchProperties properties;
    private final JobLockKeyGenerator keyGenerator;

    @Around("@annotation(distributedJobLock)")
    public Object around(ProceedingJoinPoint pjp, DistributedJobLock distributedJobLock) throws Throwable {

        // 生成锁键
        String lockKey = keyGenerator.generateKey(distributedJobLock.key(), pjp);

        log.info("尝试获取分布式锁: {}", lockKey);

        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;

        try {
            // 尝试获取锁
            acquired = lock.tryLock(
                distributedJobLock.waitTime(),
                distributedJobLock.timeout(),
                TimeUnit.SECONDS
            );

            if (!acquired) {
                String message = "无法获取分布式锁: " + lockKey;
                log.error(message);

                if (distributedJobLock.throwExceptionOnFailure()) {
                    throw new BatchJobLockException(message);
                }
                return null;
            }

            log.info("成功获取分布式锁: {}", lockKey);

            // 执行业务逻辑
            return pjp.proceed();

        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("释放分布式锁: {}", lockKey);
            }
        }
    }
}
```

#### 6.1.7 SkyWalkingJobListener

**职责**：集成 SkyWalking 追踪

```java
package com.patra.starter.batch.listener;

@Slf4j
public class SkyWalkingJobListener implements JobExecutionListener {

    private static final String SPAN_PREFIX = "BatchJob:";

    @Override
    public void beforeJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        String spanName = SPAN_PREFIX + jobName;

        // 创建 SkyWalking Span
        ActiveSpan span = Tracer.createLocalSpan(spanName);
        span.tag("job.id", String.valueOf(jobExecution.getJobId()));
        span.tag("job.execution.id", String.valueOf(jobExecution.getId()));
        span.tag("job.parameters", jobExecution.getJobParameters().toString());

        log.info("Job [{}] 启动，执行 ID: {}", jobName, jobExecution.getId());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        BatchStatus status = jobExecution.getStatus();

        // 结束 SkyWalking Span
        ActiveSpan span = Tracer.activeSpan();
        if (span != null) {
            span.tag("job.status", status.name());
            span.tag("job.exit.code", jobExecution.getExitStatus().getExitCode());

            if (status == BatchStatus.FAILED) {
                span.errorOccurred();
                jobExecution.getAllFailureExceptions().forEach(e ->
                    span.log(e)
                );
            }

            Tracer.stopSpan();
        }

        log.info("Job [{}] 完成，状态: {}, 耗时: {} ms",
            jobName,
            status,
            jobExecution.getEndTime().getTime() - jobExecution.getStartTime().getTime()
        );
    }
}
```

#### 6.1.8 MetricsJobListener

**职责**：记录 Micrometer 指标

```java
package com.patra.starter.batch.listener;

@Slf4j
@RequiredArgsConstructor
public class MetricsJobListener implements JobExecutionListener {

    private final MeterRegistry meterRegistry;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        // 记录 Job 启动事件
        meterRegistry.counter(
            "batch.job.started",
            "job", jobExecution.getJobInstance().getJobName()
        ).increment();
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        BatchStatus status = jobExecution.getStatus();

        // 记录 Job 执行状态
        meterRegistry.counter(
            "batch.job.status",
            "job", jobName,
            "status", status.name()
        ).increment();

        // 记录 Job 执行耗时
        long duration = jobExecution.getEndTime().getTime() - jobExecution.getStartTime().getTime();
        meterRegistry.timer(
            "batch.job.duration",
            "job", jobName,
            "status", status.name()
        ).record(duration, TimeUnit.MILLISECONDS);

        // 记录 Step 统计
        jobExecution.getStepExecutions().forEach(stepExecution -> {
            String stepName = stepExecution.getStepName();

            meterRegistry.counter(
                "batch.step.items.read",
                "job", jobName,
                "step", stepName
            ).increment(stepExecution.getReadCount());

            meterRegistry.counter(
                "batch.step.items.written",
                "job", jobName,
                "step", stepName
            ).increment(stepExecution.getWriteCount());

            meterRegistry.counter(
                "batch.step.items.skipped",
                "job", jobName,
                "step", stepName
            ).increment(stepExecution.getSkipCount());
        });
    }
}
```

### 6.2 配置属性设计

#### BatchProperties

```java
package com.patra.starter.batch.config;

@ConfigurationProperties(prefix = "patra.batch")
@Data
public class BatchProperties {

    /**
     * 是否启用批处理自动配置
     */
    private boolean enabled = true;

    /**
     * 表前缀
     */
    private String tablePrefix = "BATCH_";

    /**
     * 分布式锁配置
     */
    private LockProperties lock = new LockProperties();

    /**
     * 可观测性配置
     */
    private ObservabilityProperties observability = new ObservabilityProperties();

    /**
     * Chunk 配置
     */
    private ChunkProperties chunk = new ChunkProperties();

    @Data
    public static class LockProperties {
        /**
         * 是否启用分布式锁
         */
        private boolean enabled = true;

        /**
         * 默认锁超时时间（秒）
         */
        private long defaultTimeout = 3600;

        /**
         * 默认等待时间（秒）
         */
        private long defaultWaitTime = 0;
    }

    @Data
    public static class ObservabilityProperties {
        /**
         * 追踪配置
         */
        private TracingProperties tracing = new TracingProperties();

        /**
         * 指标配置
         */
        private MetricsProperties metrics = new MetricsProperties();

        /**
         * 日志配置
         */
        private LoggingProperties logging = new LoggingProperties();

        @Data
        public static class TracingProperties {
            private boolean enabled = true;
        }

        @Data
        public static class MetricsProperties {
            private boolean enabled = true;
        }

        @Data
        public static class LoggingProperties {
            private boolean enabled = true;
            private String level = "INFO";
        }
    }

    @Data
    public static class ChunkProperties {
        /**
         * 默认批次大小
         */
        private int defaultSize = 1000;

        /**
         * 最大批次大小
         */
        private int maxSize = 10000;
    }
}
```

---

## 七、配置管理

### 7.1 默认配置

**application.yml**（Starter 内置）：

```yaml
patra:
  batch:
    enabled: true
    table-prefix: BATCH_

    # 分布式锁配置
    lock:
      enabled: true
      default-timeout: 3600      # 默认锁超时 1 小时
      default-wait-time: 0       # 不等待锁

    # 可观测性配置
    observability:
      tracing:
        enabled: true
      metrics:
        enabled: true
      logging:
        enabled: true
        level: INFO

    # Chunk 配置
    chunk:
      default-size: 1000
      max-size: 10000

# Spring Batch 配置
spring:
  batch:
    job:
      enabled: false             # 禁止启动时自动运行 Job
    jdbc:
      initialize-schema: always  # 自动创建元数据表
      table-prefix: BATCH_
```

### 7.2 业务服务配置

**patra-catalog 的 application.yml**：

```yaml
patra:
  batch:
    enabled: true

    # 覆盖 Chunk 默认大小
    chunk:
      default-size: 2000  # MeSH 数据量大，使用更大批次

    # 自定义锁配置
    lock:
      default-timeout: 7200  # MeSH 导入可能需要 2 小时

# Spring Batch 数据源（复用业务数据源）
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/patra_catalog?useUnicode=true&characterEncoding=utf8
    username: root
    password: password
```

---

## 八、使用示例

### 8.1 MeSH 数据导入示例

#### 8.1.1 Job 配置（Application 层）

```java
package com.patra.catalog.app.batch;

@Configuration
@RequiredArgsConstructor
public class MeshImportJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final BatchProperties batchProperties;

    /**
     * MeSH 导入 Job
     */
    @Bean
    public Job meshImportJob(
            Step importDescriptorStep,
            Step importQualifierStep,
            Step importTreeNumberStep
    ) {
        return new JobBuilder("meshImportJob", jobRepository)
            .listener(new LoggingJobListener())  // 添加监听器
            .start(importDescriptorStep)
            .next(importQualifierStep)
            .next(importTreeNumberStep)
            .build();
    }

    /**
     * Step 1: 导入 Descriptor
     */
    @Bean
    public Step importDescriptorStep(
            MeshDescriptorItemReader reader,
            MeshDescriptorItemProcessor processor,
            MeshDescriptorItemWriter writer
    ) {
        return new StepBuilder("importDescriptorStep", jobRepository)
            .<MeshDescriptor, MeshDescriptorDO>chunk(batchProperties.getChunk().getDefaultSize(), transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .faultTolerant()
            .skipLimit(100)  // 最多跳过 100 条
            .skip(DataFormatException.class)
            .listener(new MetricsStepListener())
            .build();
    }

    /**
     * Step 2: 导入 Qualifier
     */
    @Bean
    public Step importQualifierStep(
            MeshQualifierItemReader reader,
            MeshQualifierItemProcessor processor,
            MeshQualifierItemWriter writer
    ) {
        return new StepBuilder("importQualifierStep", jobRepository)
            .<MeshQualifier, MeshQualifierDO>chunk(500, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
    }

    /**
     * Step 3: 导入 TreeNumber
     */
    @Bean
    public Step importTreeNumberStep(
            MeshTreeNumberItemReader reader,
            MeshTreeNumberItemProcessor processor,
            MeshTreeNumberItemWriter writer
    ) {
        return new StepBuilder("importTreeNumberStep", jobRepository)
            .<MeshTreeNumber, MeshTreeNumberDO>chunk(2000, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
    }
}
```

#### 8.1.2 ItemReader（Infrastructure 层）

```java
package com.patra.catalog.infra.batch.reader;

@Component
@Slf4j
@RequiredArgsConstructor
public class MeshDescriptorItemReader implements ItemReader<MeshDescriptor> {

    private final XmlParserPort xmlParser;
    private final MeshFileDownloadPort downloadPort;

    private Iterator<MeshDescriptor> descriptorIterator;
    private boolean initialized = false;

    @Override
    public MeshDescriptor read() throws Exception {
        if (!initialized) {
            initialize();
        }

        if (descriptorIterator.hasNext()) {
            return descriptorIterator.next();
        }

        return null;  // 读取完成
    }

    private void initialize() throws Exception {
        log.info("初始化 MeshDescriptorItemReader");

        // 下载 XML 文件
        Path xmlFile = downloadPort.download("https://nlmpubs.nlm.nih.gov/.../desc2024.xml");

        // 解析 XML（流式解析，不全部加载到内存）
        List<MeshDescriptor> descriptors = xmlParser.parseDescriptors(xmlFile);
        this.descriptorIterator = descriptors.iterator();
        this.initialized = true;

        log.info("MeshDescriptorItemReader 初始化完成，共 {} 条记录", descriptors.size());
    }
}
```

#### 8.1.3 ItemProcessor（Infrastructure 层）

```java
package com.patra.catalog.infra.batch.processor;

@Component
@Slf4j
@RequiredArgsConstructor
public class MeshDescriptorItemProcessor implements ItemProcessor<MeshDescriptor, MeshDescriptorDO> {

    private final MeshDescriptorConverter converter;

    @Override
    public MeshDescriptorDO process(MeshDescriptor item) throws Exception {
        // 数据验证
        if (item.getUI() == null || item.getUI().isEmpty()) {
            log.warn("跳过无效 Descriptor: {}", item);
            return null;  // 返回 null 表示跳过
        }

        // 转换为 DO
        MeshDescriptorDO entity = converter.toEntity(item);

        return entity;
    }
}
```

#### 8.1.4 ItemWriter（Infrastructure 层）

```java
package com.patra.catalog.infra.batch.writer;

@Component
@Slf4j
@RequiredArgsConstructor
public class MeshDescriptorItemWriter implements ItemWriter<MeshDescriptorDO> {

    private final MeshDescriptorMapper mapper;

    @Override
    public void write(Chunk<? extends MeshDescriptorDO> chunk) throws Exception {
        List<? extends MeshDescriptorDO> items = chunk.getItems();

        if (items.isEmpty()) {
            return;
        }

        log.debug("批量写入 {} 条 MeshDescriptor", items.size());

        // 批量插入
        for (MeshDescriptorDO item : items) {
            mapper.insert(item);
        }

        log.debug("批量写入完成");
    }
}
```

#### 8.1.5 XXL-Job Handler（Adapter 层）

```java
package com.patra.catalog.adapter.scheduler.job;

@Component
@RequiredArgsConstructor
@Slf4j
public class MeshImportJobHandler {

    private final JobLauncherHelper jobLauncherHelper;
    private final Job meshImportJob;

    /**
     * MeSH 数据导入任务
     */
    @XxlJob("meshImportJob")
    @DistributedJobLock(key = "batch:job:mesh-import", timeout = 7200)
    public void execute() {
        log.info("MeSH 数据导入任务启动");

        Long executionId = jobLauncherHelper.launch(meshImportJob, Map.of(
            "year", "2024",
            "source", "NLM"
        ));

        log.info("MeSH 数据导入任务已提交，执行 ID: {}", executionId);
    }
}
```

#### 8.1.6 REST API（Adapter 层）

```java
package com.patra.catalog.adapter.rest;

@RestController
@RequestMapping("/api/v1/mesh/import")
@RequiredArgsConstructor
@Slf4j
public class MeshImportController {

    private final JobLauncherHelper jobLauncherHelper;
    private final Job meshImportJob;
    private final JobExplorer jobExplorer;

    /**
     * 启动 MeSH 导入任务
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startImport() {
        Long executionId = jobLauncherHelper.launch(meshImportJob, Map.of(
            "year", "2024"
        ));

        return ResponseEntity.ok(Map.of(
            "executionId", executionId,
            "message", "MeSH 导入任务已启动"
        ));
    }

    /**
     * 查询导入进度
     */
    @GetMapping("/progress/{executionId}")
    public ResponseEntity<Map<String, Object>> getProgress(@PathVariable Long executionId) {
        JobExecution execution = jobExplorer.getJobExecution(executionId);

        if (execution == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> progress = new HashMap<>();
        progress.put("status", execution.getStatus().name());
        progress.put("startTime", execution.getStartTime());
        progress.put("endTime", execution.getEndTime());

        // 统计各 Step 进度
        List<Map<String, Object>> steps = execution.getStepExecutions().stream()
            .map(step -> Map.of(
                "name", step.getStepName(),
                "status", step.getStatus().name(),
                "readCount", step.getReadCount(),
                "writeCount", step.getWriteCount(),
                "skipCount", step.getSkipCount()
            ))
            .toList();

        progress.put("steps", steps);

        return ResponseEntity.ok(progress);
    }
}
```

---

## 九、实施计划

### 9.1 开发阶段

| 阶段 | 任务 | 工作量 | 优先级 |
|------|------|--------|--------|
| **阶段 1** | 创建 Starter 模块骨架 | 2h | P0 |
|  | - 创建 Maven 模块 | 0.5h |  |
|  | - 配置 pom.xml 依赖 | 0.5h |  |
|  | - 创建包结构 | 0.5h |  |
|  | - 编写 README.md | 0.5h |  |
| **阶段 2** | 实现核心自动配置 | 8h | P0 |
|  | - BatchAutoConfiguration | 3h |  |
|  | - DistributedLockAutoConfiguration | 2h |  |
|  | - ObservabilityAutoConfiguration | 3h |  |
| **阶段 3** | 实现基础组件 | 6h | P0 |
|  | - JobLauncherHelper | 2h |  |
|  | - BatchJobLockAspect | 2h |  |
|  | - JobLockKeyGenerator | 1h |  |
|  | - BatchProperties | 1h |  |
| **阶段 4** | 实现监听器 | 6h | P1 |
|  | - SkyWalkingJobListener | 2h |  |
|  | - MetricsJobListener | 2h |  |
|  | - LoggingJobListener | 2h |  |
| **阶段 5** | 编写单元测试 | 8h | P1 |
|  | - 自动配置测试 | 3h |  |
|  | - 分布式锁测试 | 2h |  |
|  | - 监听器测试 | 3h |  |
| **阶段 6** | 编写集成测试 | 6h | P1 |
|  | - 完整 Job 执行测试 | 4h |  |
|  | - 断点续传测试 | 2h |  |
| **阶段 7** | 编写文档 | 4h | P1 |
|  | - README.md（使用指南） | 2h |  |
|  | - 示例代码 | 2h |  |

**总计**：约 **40 工时**（5 个工作日）

### 9.2 验证阶段

| 任务 | 工作量 | 优先级 |
|------|--------|--------|
| 在 patra-catalog 中重构 MeSH 导入 | 16h | P0 |
| - 删除自定义批处理代码 | 2h |  |
| - 实现 ItemReader/Writer/Processor | 8h |  |
| - 配置 Job 和 Step | 2h |  |
| - 编写测试 | 4h |  |
| E2E 测试（实际导入 MeSH 数据） | 4h | P0 |
| 性能测试和优化 | 4h | P1 |

**总计**：约 **24 工时**（3 个工作日）

### 9.3 时间表

| 里程碑 | 预计完成时间 | 交付物 |
|--------|------------|--------|
| M1: Starter 开发完成 | D+5 | patra-spring-boot-starter-batch 1.0.0 |
| M2: MeSH 导入重构完成 | D+8 | patra-catalog 集成 Spring Batch |
| M3: 验证和文档完成 | D+10 | 完整的设计文档、使用指南、示例代码 |

---

## 十、风险评估

### 10.1 技术风险

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| Spring Batch 元数据表与业务表冲突 | 低 | 中 | 使用独立 Schema 或表前缀 `BATCH_` |
| 分布式锁死锁 | 中 | 高 | 配置锁超时，添加监控告警 |
| 大数据量导致 OOM | 中 | 高 | 使用流式解析，避免全量加载 |
| JobRepository 性能瓶颈 | 低 | 中 | 优化批次大小，使用数据库索引 |
| XXL-Job 与 Spring Batch 集成问题 | 低 | 中 | 提供 JobLauncherHelper 封装 |

### 10.2 项目风险

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| 开发时间超预期 | 中 | 中 | 分阶段交付，优先 P0 功能 |
| 学习曲线陡峭 | 中 | 低 | 提供详细文档和示例代码 |
| 与现有代码冲突 | 低 | 高 | 全新项目，无历史包袱 |

### 10.3 运维风险

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| 批处理任务卡住 | 低 | 高 | 配置锁超时，添加监控告警 |
| 元数据表数据膨胀 | 中 | 中 | 定期清理历史执行记录 |
| 并发执行导致数据不一致 | 低 | 高 | 使用分布式锁，确保单实例执行 |

---

## 十一、后续优化方向

### 11.1 短期优化（v1.1.0）

- [ ] 支持 Spring Batch Admin UI（可视化监控）
- [ ] 提供批次级别的指标看板（Grafana Dashboard）
- [ ] 支持批次失败重试策略配置
- [ ] 支持自定义跳过记录表

### 11.2 中期优化（v1.2.0）

- [ ] 支持并行处理（Multi-threaded Step）
- [ ] 支持分区策略（Partitioning）
- [ ] 支持远程分块（Remote Chunking）
- [ ] 支持多数据源批处理

### 11.3 长期优化（v2.0.0）

- [ ] 支持 Kubernetes Job 集成
- [ ] 支持流式处理（Spring Cloud Data Flow）
- [ ] 支持实时进度推送（WebSocket）
- [ ] 支持批处理任务编排可视化

---

## 附录

### A. 参考资料

- [Spring Batch 官方文档](https://docs.spring.io/spring-batch/docs/5.2.x/reference/html/)
- [Spring Batch 最佳实践](https://spring.io/guides/gs/batch-processing/)
- [Redisson 分布式锁文档](https://github.com/redisson/redisson/wiki/8.-Distributed-locks-and-synchronizers)
- [SkyWalking Java Agent 文档](https://skywalking.apache.org/docs/skywalking-java/latest/en/setup/service-agent/java-agent/readme/)

### B. 术语表

| 术语 | 定义 |
|------|------|
| **Job** | 批处理任务，由多个 Step 组成 |
| **Step** | 批处理步骤，包含 ItemReader、ItemProcessor、ItemWriter |
| **Chunk** | 批次，一次事务提交的记录数 |
| **JobRepository** | Job 元数据仓储，记录 Job/Step 执行状态 |
| **JobLauncher** | Job 启动器 |
| **JobExecution** | Job 执行实例，记录一次 Job 运行的状态 |
| **StepExecution** | Step 执行实例，记录一次 Step 运行的状态 |

---

**文档结束**
