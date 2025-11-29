---
type: til
date: 2025-11-28
topics: [spring-batch]
learning_series:
chapters_completed: []
tags:
  - record/til
  - tech/spring-batch
  - tech/batch-processing
  - tech/chunk-oriented
  - tech/job-monitoring
---

# Spring Batch 核心概念与监控

## 场景

在 Patra 项目中实现 MeSH 主题词导入（约 35,000 条记录），需要：
- 大数据量处理，不能一次性加载到内存
- 事务边界控制，避免长事务
- 断点续传，失败后从上次位置继续
- 执行状态监控

## 核心知识

### 1. Spring Batch 核心抽象

| 概念 | 说明 |
|------|------|
| **Job** | 批处理作业，一个完整的批处理任务 |
| **Step** | 作业步骤，Job 由一个或多个 Step 组成 |
| **ItemReader** | 数据读取器，一次读取一条记录 |
| **ItemWriter** | 数据写入器，批量写入一批记录 |
| **Chunk** | 块处理模式，每读取 N 条后统一处理 |

### 2. Chunk-Oriented 处理流程

```
Reader.read() x N  ──▶  Writer.write(chunk)  ──▶  事务提交
      ↑                                               │
      └───────────────── 循环直到 Reader 返回 null ───┘
```

每个 chunk 是一个事务边界，chunk size 决定：
- 事务大小（500 条记录一个事务）
- 断点精度（最多重复处理 chunk size - 1 条）

### 3. 断点续传机制

```java
public class MeshDescriptorItemReader implements ItemStreamReader<MeshDescriptorAggregate> {

    private static final String CURRENT_INDEX_KEY = "mesh.descriptor.current.index";

    @Override
    public void open(ExecutionContext executionContext) {
        // 从 ExecutionContext 恢复进度
        if (executionContext.containsKey(CURRENT_INDEX_KEY)) {
            skipCount = executionContext.getInt(CURRENT_INDEX_KEY);
        }
        // 跳过已处理的记录...
    }

    @Override
    public void update(ExecutionContext executionContext) {
        // 每个 chunk 提交时保存进度
        executionContext.putInt(CURRENT_INDEX_KEY, currentIndex);
    }
}
```

关键点：
- `ExecutionContext` 存储在数据库 `BATCH_STEP_EXECUTION_CONTEXT` 表
- 实现 `ItemStreamReader` 接口获得 `open/update/close` 生命周期

### 4. Job 状态模型

```
STARTING → STARTED → COMPLETED（成功）
                  ↘ FAILED（失败，可重启）
                  ↘ STOPPED（停止，可重启）
                  ↘ ABANDONED（放弃，不可重启）
```

### 5. 状态监控方式

**Spring Batch 核心框架不提供 UI 看板**，监控方式：

| 方式 | 适用场景 |
|------|----------|
| `JobExplorer` API | 代码中查询状态 |
| 直接查数据库 | 开发调试 |
| 自建 REST API | 业务系统内查询 |
| Micrometer + Grafana | 运维监控 |

```java
// 查询 Job 执行状态
Optional<JobExecution> execution = jobLauncherHelper.findJobExecution(executionId);
execution.ifPresent(e -> {
    BatchStatus status = e.getStatus();          // STARTED, COMPLETED, FAILED...
    Collection<StepExecution> steps = e.getStepExecutions();
    // 从 StepExecution 获取 readCount/writeCount 计算进度
});
```

### 6. 元数据表结构

| 表名 | 用途 |
|------|------|
| `BATCH_JOB_INSTANCE` | Job 实例（相同参数只有一个） |
| `BATCH_JOB_EXECUTION` | Job 执行记录 |
| `BATCH_STEP_EXECUTION` | Step 执行记录（含进度） |
| `BATCH_STEP_EXECUTION_CONTEXT` | 断点信息 |

## 代码示例

### Job 配置

```java
@Bean
public Job meshDescriptorImportJob() {
    return new JobBuilder("meshDescriptorImportJob", jobRepository)
        .listener(meshImportJobExecutionListener)
        .start(meshDescriptorImportStep())
        .build();
}

@Bean
public Step meshDescriptorImportStep() {
    return new StepBuilder("meshDescriptorImportStep", jobRepository)
        .<MeshDescriptorAggregate, MeshDescriptorAggregate>chunk(500, transactionManager)
        .reader(meshDescriptorItemReader(null, null))
        .writer(meshDescriptorItemWriter)
        .faultTolerant()
        .skipLimit(100)
        .skip(DataIntegrityViolationException.class)  // 主键冲突时跳过
        .build();
}

@Bean
@StepScope  // Step 作用域，支持 Job 参数注入
public MeshDescriptorItemReader meshDescriptorItemReader(
    @Value("#{jobParameters['filePath']}") String filePath,
    @Value("#{jobParameters['meshVersion']}") String meshVersion) {
    return new MeshDescriptorItemReader(xmlParserPort, filePath, meshVersion);
}
```

### 六边形架构集成

```
Application 层
    └─ MeshImportOrchestrator
        └─ meshDescriptorBatchPort.launchImport(params)  // 调用端口接口
                        ↓
Domain 层
    └─ MeshDescriptorBatchPort (interface)  // 纯接口，不知道 Spring Batch
                        ↓
Infrastructure 层
    └─ MeshDescriptorBatchAdapter (implements Port)
        └─ jobLauncherHelper.launch(job, params)  // 封装 Spring Batch
```

## 实践验证

- ✅ MeSH 主题词导入 35,000 条记录，chunk size 500
- ✅ 断点续传测试：手动中断后可从上次位置继续
- ✅ 增量导入：使用 `skip(DataIntegrityViolationException.class)` 跳过重复记录

## 关联

- 项目实现: `patra-catalog-infra/src/main/java/com/patra/catalog/infra/batch/`
- Starter 封装: `patra-spring-boot-starter-batch`
