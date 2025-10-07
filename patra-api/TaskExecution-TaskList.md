# 📋 TaskExecution 用例完整任务清单

> 从 MQ 消费到游标推进的完整实现方案
>
> 最后更新：2025-01-15

---

## ✅ 已实现（步骤 0-4）

### 0. MQ 消费与幂等检查
- 0.1 解析 TaskReadyCommand（taskId/idempotentKey/messageId）
- 0.2 查询 ing_task 记录
- 0.3 幂等闸门：SUCCEEDED + idempotentKey 匹配则跳过

### 1. CAS 抢占租约
- 1.1 生成 lease owner（workerId + UUID）
- 1.2 调用 tryAcquireLease（CAS 更新）
- 1.3 抢占失败则优雅退出

### 2. 初始化执行会话（事务内）
- 2.1 解析任务配置（planId/sliceId → plan/slice/window）
- 2.2 置 ing_task 为 RUNNING（markRunningWithLease）
- 2.3 创建 ing_task_run（attemptNo++）
- 2.4 绑定执行窗口和上下文（schedulerRunId/correlationId）
- 2.5 安排心跳续租（scheduleHeartbeat）

### 3. 还原配置快照
- 3.1 读取 provenance_config_snapshot JSON
- 3.2 读取 expr_snapshot + expr_hash
- 3.3 哈希校验（防配置篡改）
- 3.4 刷新心跳（touchHeartbeat）
- 3.5 条件续租（耗时超 TTL/3）

### 4. 编译表达式
- 4.1 从 ConfigurationSnapshot 获取 exprSnapshotJson
- 4.2 使用 exprCompiler.compile 编译（已完成但结果未使用）

---

## ⏳ 待实现（步骤 5-15）

### 📦 一、基础设施准备

#### 5. MinIO 集成
- 5.1 Docker Compose 添加 MinIO 服务配置
- 5.2 添加 Maven 依赖（minio-java 或 spring-cloud-starter-aws）
- 5.3 创建 MinioProperties 配置类
- 5.4 创建 MinioClient Bean（application context）
- 5.5 创建 MinioDataStorage 工具类
  - upload(bucket, objectKey, stream) 方法
  - download(bucket, objectKey) 方法
  - delete(bucket, objectKey) 方法（可选）
- 5.6 配置 bucket 生命周期策略（7-30 天自动删除）

#### 6. starter-provenance 模块集成
- 6.1 确认 starter-provenance 的 API 接口规范
- 6.2 创建 ProvenanceClient 抽象接口（适配器模式）
- 6.3 实现 PubMedProvenanceClient
  - esearch(term, usehistory=y) 方法
  - efetch(webEnv, queryKey, retstart, retmax) 方法
- 6.4 配置 HTTP 客户端（从 HttpConfigResp 读取超时/重试/代理）
- 6.5 异常处理与错误分类

#### 7. 仓储层扩展
- 7.1 TaskRunBatchRepository 新增方法：
  - saveAll(List<RunBatch>) - 批量插入
  - findByRunIdAndStatus(runId, status) - 查询待执行 batch
  - updateStatus(batchId, status) - CAS 更新
- 7.2 CursorRepository 新增方法：
  - advance(provenance, operation, key, value) - 推进游标（乐观锁）
  - findLatestGlobalTimeWatermark() - 查询当前水位
- 7.3 CursorEventRepository 新增方法：
  - save(event) - 插入事件（幂等检查）

---

### 🎯 二、核心业务逻辑实现

#### 8. RunBatch 规划器（PubMed WebEnv 方案）
- 8.1 创建 RunBatchPlanner 接口
  - plan(CompileResult, ProvenanceConfigSnapshot, ExecutionWindow, TaskRun) → WebEnvContext
- 8.2 创建 PubMedBatchPlanner 实现
  - 8.2.1 调用 esearch API（usehistory=y, retmax=0）
  - 8.2.2 解析响应：total、WebEnv、QueryKey
  - 8.2.3 从 pagination.pageSizeValue 读取 pageSize
  - 8.2.4 从 pagination.maxPagesPerExecution 读取限制
  - 8.2.5 计算 batchCount = min(ceil(total/pageSize), maxPages)
  - 8.2.6 生成 RunBatchPlan 列表（循环）：
    - batch_no = i + 1
    - page_no = i * pageSize（retstart）
    - page_size = pageSize（retmax）
    - idempotent_key = SHA256(runId + ":" + retstart)
  - 8.2.7 批量插入 ing_task_run_batch（status=PENDING）
  - 8.2.8 返回 WebEnvContext（webEnv/queryKey/total）
- 8.3 创建 BatchPlannerRegistry 策略注册器
  - register(ProvenanceCode, RunBatchPlanner)
  - getPlanner(ProvenanceCode) → RunBatchPlanner

#### 9. RunBatch 执行器
- 9.1 创建 RunBatchExecutor 接口
  - execute(WebEnvContext, ProvenanceConfigSnapshot) → ExecutionStats
- 9.2 创建 RunBatchExecutorImpl 实现
  - 9.2.1 查询 PENDING 状态的 batch（按 batch_no 排序）
  - 9.2.2 遍历每个 batch：
    - a. 幂等检查（idempotent_key 已存在且 SUCCEEDED → 跳过）
    - b. 更新 batch.status = RUNNING（事务 1）
    - c. 调用 efetch API（使用 WebEnv/QueryKey/retstart/retmax）
    - d. 解析响应数据（XML/JSON → 领域对象列表）
    - e. 压缩数据（DataCompressor.gzip）
    - f. 上传到 MinIO：
      - bucket: papertrace-ingest
      - objectKey: ingest-data-buffer/{provenanceCode}/{year}/{month}/{runId}/batch_{batchId}.json.gz
    - g. 写入 Outbox + 更新 batch（同一事务 2）：
      - channel: ingest.data.{provenanceCode}
      - dedup_key: batch.idempotent_key
      - partition_key: {provenanceCode}:{operationCode}
      - payload: { storageType: "MINIO", storagePath, recordCount }
      - batch.record_count = records.size()
      - batch.status = SUCCEEDED
      - batch.committed_at = now
      - batch.stats = { minioPath, compressedSize }
    - h. 异常处理：status = FAILED, error = 异常信息
  - 9.2.3 返回 ExecutionStats（totalBatches/totalRecords/succeededCount/failedCount）

#### 10. 游标推进器（Task 完成后执行）
- 10.1 创建 CursorAdvancer 接口
  - advance(TaskRun, List<RunBatch>, OperationCode) → CursorAdvanceResult
- 10.2 创建 CursorAdvancerImpl 实现
  - 10.2.1 判断是否需要推进（根据 operationType）
  - 10.2.2 创建 CursorCalculator 水位计算器：
    - 查询所有 SUCCEEDED 的 batch
    - 从 batch.stats 提取游标值（时间/ID）
    - 聚合计算全局最大值（max(timestamp) 或 max(id)）
  - 10.2.3 构建游标推进参数：
    - provenance_code / operation_code
    - cursor_key（从配置或约定，如 "updated_at"）
    - namespace_scope_code（GLOBAL 或 EXPR）
    - namespace_key（全局=全0，表达式=expr_hash）
    - prev_value / new_value
    - window_from / window_to
  - 10.2.4 更新 ing_cursor（事务 3，乐观锁）
  - 10.2.5 插入 ing_cursor_event（事务 3，幂等 idempotent_key）
- 10.3 创建 CursorStrategy 接口（不同 operation 策略）
  - HarvestCursorStrategy（时间型）
  - BackfillCursorStrategy（窗口型）
  - UpdateCursorStrategy（可能不推进）

#### 11. 执行状态管理器
- 11.1 创建 ExecutionStateManager 接口
  - updateFinalState(TaskRun, ExecutionStats) → void
- 11.2 创建 ExecutionStateManagerImpl 实现
  - 11.2.1 聚合统计信息（从 ExecutionStats）
  - 11.2.2 判断整体状态：
    - 全部成功 → SUCCEEDED
    - 有失败 → FAILED
    - 部分成功 → PARTIAL
  - 11.2.3 更新 ing_task_run（事务 4）：
    - stats = ExecutionStats JSON
    - status = 聚合状态
    - finished_at = now
    - error（若失败）
  - 11.2.4 更新 ing_task（事务 4）：
    - status = 聚合状态
    - finished_at = now
    - 清空 lease_owner / leased_until
    - 更新 retry_count / last_error_code / last_error_msg（若失败）
  - 11.2.5 停止心跳续租任务（HeartbeatManager.cancel）

---

### 🔗 三、主编排器集成

#### 12. TaskExecutionOrchestrator 改造
- 12.1 注入新依赖：
  - BatchPlannerRegistry
  - RunBatchExecutor
  - CursorAdvancer
  - ExecutionStateManager
  - MinioDataStorage
- 12.2 修改 startFromReady 流程：
  - 步骤 3（已有）：编译表达式
  - 步骤 4（新增）：调用 BatchPlanner.plan() 生成 RunBatch 计划
  - 步骤 5（新增）：调用 RunBatchExecutor.execute() 执行所有 batch
  - 步骤 6（新增）：调用 CursorAdvancer.advance() 推进游标
  - 步骤 7（新增）：调用 ExecutionStateManager.updateFinalState() 更新状态
- 12.3 异常处理增强：
  - esearch 失败 → 标记 TaskRun FAILED 并释放租约
  - 租约丢失 → 中断执行并清理资源
  - 部分 batch 失败 → 继续执行并标记 PARTIAL
- 12.4 事务边界明确：
  - 步骤 4：单独事务（batch 批量插入）
  - 步骤 5：每个 batch 两个事务（更新状态 + Outbox 写入）
  - 步骤 6：单独事务（游标推进）
  - 步骤 7：单独事务（最终状态更新）

---

### 🧪 四、支撑组件与工具

#### 13. 辅助组件开发
- 13.1 创建 DataCompressor（压缩工具）
  - gzip(List<Record>) → InputStream
  - ungzip(InputStream) → List<Record>
- 13.2 创建 PubMedResponseParser（响应解析器）
  - parseXml(String xml) → List<PubMedRecord>
  - parseJson(String json) → List<PubMedRecord>
- 13.3 创建 HeartbeatManager（心跳生命周期管理）
  - start(taskId, owner, scheduler) → ScheduledFuture
  - cancel(ScheduledFuture) → void
- 13.4 创建 DTO 类：
  - RunBatchPlan（批次计划）
  - RunBatchResult（批次结果）
  - ExecutionStats（执行统计）
  - WebEnvContext（WebEnv 上下文）
  - CursorAdvanceResult（游标推进结果）

---

## 🧩 五、配置与扩展

#### 14. 配置文件补充
- 14.1 MinIO 配置（bootstrap.yml 或 Nacos）：
  ```yaml
  minio:
    endpoint: http://localhost:9000
    accessKey: minioadmin
    secretKey: minioadmin
    bucket: papertrace-ingest
    lifecycle-days: 7
  ```
- 14.2 starter-provenance 配置：
  ```yaml
  provenance:
    pubmed:
      base-url: https://eutils.ncbi.nlm.nih.gov
      timeout-connect: 5000
      timeout-read: 30000
      retry-max: 3
  ```

#### 15. 未来扩展点预留
- 15.1 创建 EPMC 和 Crossref 的 BatchPlanner（空实现）
- 15.2 创建 EPMC 和 Crossref 的 ProvenanceClient（空实现）
- 15.3 文档说明扩展方式（在 CLAUDE.md 或 README）

---

## 📝 任务清单总结

### 统计

**基础设施（3 项）**：
- 5. MinIO 集成（6 个子任务）
- 6. starter-provenance 集成（5 个子任务）
- 7. 仓储层扩展（3 个子任务）

**核心逻辑（4 项）**：
- 8. RunBatch 规划器（3 个子任务）
- 9. RunBatch 执行器（2 个子任务）
- 10. 游标推进器（3 个子任务）
- 11. 执行状态管理器（2 个子任务）

**集成与完善（4 项）**：
- 12. 主编排器集成（4 个子任务）
- 13. 辅助组件开发（4 个子任务）
- 14. 配置文件补充（2 个子任务）
- 15. 未来扩展点预留（3 个子任务）

**总计：11 大项，约 50+ 子任务**

---

## 📂 目录结构设计

```
patra-ingest-app/usecase/execution/
├── TaskExecutionOrchestrator.java       # 主编排器（已有）
├── TaskExecutionUseCase.java            # 接口（已有）
├── command/
│   └── TaskReadyCommand.java            # 命令（已有）
├── dto/                                 # 新建
│   ├── RunBatchPlan.java               # 批次计划
│   ├── RunBatchResult.java             # 批次执行结果
│   ├── ExecutionStats.java             # 执行统计
│   ├── WebEnvContext.java              # WebEnv 上下文
│   └── CursorAdvanceResult.java        # 游标推进结果
├── planner/                            # 新建 - 批次规划
│   ├── RunBatchPlanner.java            # 抽象接口
│   ├── PubMedBatchPlanner.java         # PubMed 实现
│   └── BatchPlannerRegistry.java       # 策略注册器
├── executor/                           # 新建 - 批次执行
│   ├── RunBatchExecutor.java           # 执行器接口
│   └── RunBatchExecutorImpl.java       # 默认实现
├── cursor/                             # 新建 - 游标管理
│   ├── CursorAdvancer.java             # 游标推进器
│   ├── CursorCalculator.java           # 水位计算器
│   └── CursorStrategy.java             # 推进策略接口
├── state/                              # 新建 - 状态管理
│   └── ExecutionStateManager.java      # 状态管理器
└── support/                            # 新建 - 支撑组件
    ├── MinioDataStorage.java           # MinIO 工具
    ├── DataCompressor.java             # 压缩工具
    └── PubMedResponseParser.java       # 响应解析器
```

---

## 🔑 关键设计决策记录

### 1. PubMed 采集方案
- **选择**：方案 B（esearch usehistory + efetch WebEnv）
- **理由**：大量采集走服务端缓存，减少 API 调用次数
- **注意**：WebEnv 有过期时间，task 粒度细化避免过期

### 2. 数据存储方案
- **选择**：MinIO 对象存储
- **理由**：
  - 成本低、扩展性强
  - 为数据湖架构铺路
  - 生命周期自动管理
  - 不影响主库性能

### 3. MQ 发送策略
- **选择**：轻量 Outbox 模式
- **流程**：数据上传 MinIO → Outbox 记录引用 → Relay 发布 → 下游消费
- **理由**：
  - 事务一致性保障
  - Outbox 表不会过大（payload 只存路径）
  - 解耦存储与发送

### 4. RunBatch 落库策略
- **选择**：预生成所有 batch（status=PENDING）
- **理由**：
  - 断点续传友好
  - 进度可见
  - 可审计
  - 幂等保障

### 5. 游标推进时机
- **选择**：整个 Task 完成后才推进
- **理由**：
  - 避免部分成功的游标污染
  - 失败重试不会跳过数据
  - 与 Plan 用例保持一致

### 6. 事务边界设计
- **原则**：每个事务都很小，减少锁持有时间
- **边界**：
  1. Batch 创建：单独事务
  2. Batch 执行：每个 batch 两个事务（状态更新 + Outbox）
  3. 游标推进：单独事务
  4. 最终状态：单独事务

---

## ⚠️ 注意事项

### 异常处理
1. **esearch 失败**：标记 TaskRun FAILED，释放租约，抛异常触发 MQ 重试
2. **efetch 失败**：标记该 batch FAILED，继续执行其他 batch
3. **MinIO 上传失败**：重试 3 次，仍失败则标记 batch FAILED
4. **Outbox 写入失败**：事务回滚，batch 保持 RUNNING（下次幂等跳过）
5. **游标推进失败**：记录 warn，不阻断流程
6. **租约丢失**：中断执行，清理资源，优雅退出

### 幂等保障
- Task 入口：idempotentKey 检查
- RunBatch 执行前：idempotent_key 检查
- 游标推进：CursorEvent.idempotent_key 幂等
- Outbox 发送：(channel, dedup_key) 唯一约束

### 性能优化
- 批量插入 RunBatch（减少 DB 交互）
- 数据压缩后上传（节省带宽和存储）
- MinIO 生命周期自动清理（避免手动管理）
- 心跳续租异步执行（不阻塞主流程）

---

## 📚 参考文档

- [Papertrace 采集编排核心架构](./papertrace-ingest-orchestration-architecture.md) - Memory 文档
- [TaskExecution 流程图](./TaskExecution-Flowchart.md) - 可视化流程
- [ProvenanceConfigResp](./patra-registry/patra-registry-api/src/main/java/com/patra/registry/api/rpc/dto/provenance/ProvenanceConfigResp.java) - 配置结构
- [数据库表结构](./patra-ingest/patra-ingest-infra/src/main/resources/db/migration/V0.1.0__init_ingest_schema.sql) - ing_* 表定义

---

**版本历史**：
- v0.1.0 (2025-01-15)：初始版本，基于与用户的深入讨论生成
