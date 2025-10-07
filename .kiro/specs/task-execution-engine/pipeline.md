# Task Execution Engine 流水线编排方案

> 本文档基于 requirements.md、design.md、tasks.md 三个文档，提供**实现→评审→单测→集成→门禁→文档**的完整流水线编排方案。
>
> **创建时间**：2025-10-07
> **预计工期**：10-13个工作日（80-100小时）

---

## 📋 目录

- [整体策略](#整体策略)
- [批次1：基础设施层（2-3天）](#批次1基础设施层2-3天)
- [批次2：核心能力层（3-4天）](#批次2核心能力层3-4天)
- [批次3：数据采集链路（4-5天）](#批次3数据采集链路4-5天)
- [批次4：编排与交付（2-3天）](#批次4编排与交付2-3天)
- [关键决策点（Gates）](#关键决策点gates)
- [关键风险与缓解措施](#关键风险与缓解措施)
- [工时估算](#工时估算)
- [下一步行动](#下一步行动)

---

## 🎯 整体策略

采用**分批迭代、持续集成**策略，将10个任务划分为4个批次，每个批次完成一轮完整流水线，确保每个批次都是**可交付、可验证的增量**。

### 核心原则

1. **架构优先**：严格遵循六边形架构 + DDD，Domain层不依赖框架
2. **小步快跑**：每个批次2-5天，快速迭代验证
3. **质量内建**：每个批次包含完整的测试和评审环节
4. **持续集成**：每个批次结束后通过质量门禁才能进入下一批次

### 流水线阶段定义

每个批次包含6个标准阶段：

| 阶段 | 负责子代理 | 职责 | 输出 |
|------|-----------|------|------|
| **实现** | architecture-designer → java-developer | 架构设计 + 编码实现 | 可编译的代码 |
| **评审** | architecture-reviewer + code-reviewer | 架构合规 + 代码质量 | 评审报告 + 问题清单 |
| **单测** | qa-unit-tests | JUnit5单元测试 | 单元测试代码 + 覆盖率报告 |
| **集成** | qa-integration-tests | Spring Boot Test + Testcontainers | 集成测试代码 + 测试报告 |
| **门禁** | qa-quality-gates | 汇总测试/静态检查/构建 | PASS/FAIL判定 + 补救建议 |
| **文档** | mermaid-expert + docs-engineer | 图表 + 文档同步 | Mermaid图表 + Markdown文档 |

---

## 📦 批次1：基础设施层（2-3天）

### 任务范围

- **任务1**：建立项目结构和领域模型
- **任务2**：持久化层（校验/补齐）

### 目标

搭建整个执行引擎的基础设施，包括Domain层的聚合根、值对象、领域端口，以及Repository层的数据访问实现。

---

### 阶段1：实现阶段

**子代理**：`architecture-designer`（先出设计方案）→ `java-developer`（落地实现）

#### 产出清单

**Domain层（patra-ingest-domain）**：

1. **聚合根与实体**：
   - `TaskAggregate`：任务聚合根（租约管理/状态流转）
   - `TaskRun`：任务执行实例
   - `TaskRunBatch`：批次执行记录
   - `Cursor`：游标聚合根
   - `CursorEvent`：游标事件

2. **值对象**：
   - `ExecutionWindow`：执行时间窗口
   - `BatchStats`：批次统计信息
   - `RunStats`：运行统计信息
   - `LeaseInfo`：租约信息

3. **领域端口接口**：
   - `ExpressionCompilerPort`：表达式编译端口
   - `StorageAdapter`：对象存储端口
   - （已有）`TaskRepository`、`TaskRunRepository`、`TaskRunBatchRepository`
   - （已有）`CursorRepository`、`CursorEventRepository`
   - （已有）`OutboxPublisherPort`

**Infra层（patra-ingest-infra）**：

1. **数据库迁移**：
   - 校验 `V0.1.0__init_ingest_schema.sql` 与设计对齐
   - 补充必要索引（如批量续租优化索引）

2. **数据对象（DO）**：
   - 校验现有DO与Baseline schema一致
   - 补齐缺失字段或注解（如`@Version`）

3. **Mapper与自定义SQL**：
   - 校验 `TaskMapper` 的租约相关SQL
   - （可选）补充批量续租 `batchRenewLeases`

4. **MapStruct转换器**：
   - 校验现有Converter完整性

5. **仓储实现**：
   - 校验 `*RepositoryMpImpl` 实现完整性
   - 补充缺失方法

#### Definition of Done (DoD)

- ✅ 代码可编译通过（`mvn -q -DskipTests compile`）
- ✅ 依赖方向正确（adapter → app → domain → patra-common）
- ✅ Domain层无任何框架依赖（仅依赖patra-common）
- ✅ 关键业务逻辑有英文注释（解释"为何"，而非"什么"）
- ✅ 无硬编码配置（统一走Nacos/配置文件）

---

### 阶段2：评审阶段

**子代理**：`architecture-reviewer`（架构合规）+ `code-reviewer`（代码质量）

#### 评审关注点

**架构维度**：
- ✅ 六边形架构依赖方向是否正确
- ✅ 聚合根边界是否清晰（TaskAggregate、Cursor）
- ✅ 仓储接口定义是否符合DDD原则（按聚合整体持久化）
- ✅ 端口接口是否解耦（ExpressionCompilerPort、StorageAdapter）

**代码质量维度**：
- ✅ 乐观锁/CAS机制是否正确实现
- ✅ 领域模型方法是否体现业务语义
- ✅ 值对象是否不可变（使用record或@Value）
- ✅ 命名是否清晰一致（领域语言）

#### Definition of Done (DoD)

- ✅ 无Critical级别问题
- ✅ High级别问题已修复或给出补救方案
- ✅ 评审意见已记录并跟踪

---

### 阶段3：单测阶段

**子代理**：`qa-unit-tests`

#### 测试范围

1. **领域模型单元测试**：
   - `TaskAggregate` 的租约管理逻辑（获取/续租/释放）
   - `TaskAggregate` 的状态转换逻辑（PENDING → RUNNING → SUCCEEDED/FAILED）
   - `ExecutionStats` 的状态判断逻辑（是否成功/部分成功/失败）
   - `ConfigSnapshot` 的哈希校验逻辑

2. **仓储层单元测试**（Mock依赖）：
   - Repository接口的基本CRUD操作
   - 租约抢占CAS逻辑

#### Definition of Done (DoD)

- ✅ 领域模型单元测试覆盖率 ≥ 80%
- ✅ 测试可独立运行（`mvn -q test`）
- ✅ 测试稳定（无偶发失败）
- ✅ 测试命名清晰（given_when_then风格）

---

### 阶段4：集成测试阶段

**子代理**：`qa-integration-tests`

#### 测试范围

1. **Repository集成测试**（Testcontainers MySQL）：
   - 租约并发抢占测试（模拟多线程CAS）
   - Outbox唯一约束测试（`(channel, dedup_key)`）
   - 乐观锁版本冲突测试

2. **数据库约束测试**：
   - 外键约束完整性
   - 唯一索引有效性

#### Definition of Done (DoD)

- ✅ Repository集成测试100%通过
- ✅ 测试可重复运行（Testcontainers自动管理环境）
- ✅ 集成测试执行时间 < 2分钟
- ✅ 测试清理完整（每次测试独立数据）

---

### 阶段5：门禁阶段

**子代理**：`qa-quality-gates`

#### 检查项

| 检查项 | 阈值 | 说明 |
|--------|------|------|
| 单元测试覆盖率 | ≥ 80% | JaCoCo报告 |
| 集成测试通过率 | 100% | 核心场景全覆盖 |
| 静态检查问题 | 0 Critical/Blocker | Checkstyle/SpotBugs |
| 构建成功 | mvn package成功 | 包含所有子模块 |

#### Definition of Done (DoD)

- ✅ **质量门禁PASS**
- ✅ 所有检查项达标
- ✅ 遗留问题已记录并排期

---

### 阶段6：文档阶段

**子代理**：`mermaid-expert`（绘图）→ `docs-engineer`（文档）

#### 产出清单

1. **领域模型类图**（Mermaid）：
   ```mermaid
   classDiagram
       TaskAggregate *-- TaskRun
       TaskAggregate *-- LeaseInfo
       TaskRun *-- RunStats
       TaskRunBatch *-- BatchStats
       Cursor *-- CursorEvent
   ```

2. **ER图**（数据库表关系）：
   - ing_task、ing_task_run、ing_task_run_batch
   - ing_cursor、ing_cursor_event
   - ing_outbox_message

3. **模块文档**：
   - `patra-ingest-domain/README.md`：领域模型说明
   - `patra-ingest-infra/README.md`：持久化层说明

#### Definition of Done (DoD)

- ✅ 图表清晰可读（基础版 + 样式版）
- ✅ 文档完整准确
- ✅ 与代码同步（无过期内容）

---

### 批次1交付物

- ✅ Domain层完整（聚合根/值对象/端口接口）
- ✅ Repository层就绪（DO/Mapper/Converter/Repository实现）
- ✅ 单元测试覆盖率 ≥ 80%
- ✅ 集成测试100%通过
- ✅ 质量门禁PASS
- ✅ 领域模型类图 + ER图 + 模块文档

---

## 📦 批次2：核心能力层（3-4天）

### 任务范围

- **任务3**：幂等检查和租约管理
- **任务4**：配置还原和表达式编译

### 目标

实现任务执行引擎的两个核心能力：**分布式租约协调机制**和**配置快照还原与表达式编译**。

---

### 阶段1：实现阶段

**子代理**：`java-developer`

#### 产出清单

**租约管理（patra-ingest-app/usecase/execution/support）**：

1. **LeaseManagementService**（租约管理服务）：
   - `acquireLease(taskId, leaseOwner, leaseDuration)`：租约抢占
   - `renewLease(taskId, leaseOwner, leaseDuration)`：租约续租
   - `releaseLease(taskId, leaseOwner)`：租约释放
   - 封装 `TaskRepository` 的租约方法，添加指标收集

2. **HeartbeatRenewalService**（心跳续租服务）：
   - 使用 `ScheduledExecutorService` 定期续租
   - 实现租约验证逻辑（验证 `lease_owner` 仍为当前节点）
   - 连续失败阈值（默认3次）后主动 `validateLease`
   - 租约被接管则抛 `LeaseRevokedException` 并设置标志位 `leaseRevoked`
   - 实现心跳停止和资源释放逻辑
   - DEBUG级别日志记录心跳续租结果

3. **ExecutionSessionManager**（执行会话管理器）：
   - `createSession(taskId, correlationId, schedulerRunId)`：创建TaskRun记录
   - 在事务内创建TaskRun并更新Task状态为RUNNING，attemptNo自增
   - 启动心跳续租
   - `ExecutionSession` 包含：taskId、leaseOwner、心跳句柄、撤销标志位

4. **BatchHeartbeatRenewalService**（批量心跳续租服务，可选）：
   - 对当前节点持有的任务批量续租
   - 按批量大小分段执行（batch-renewal-size=20）
   - 在 `TaskRepository` 增加 `batchRenewLeases(taskIds, leaseOwner, leaseDuration)`
   - 通过配置项启用：`patra.ingest.task-execution.heartbeat.batch-renewal-enabled=true`

**配置还原与表达式编译（patra-ingest-app/usecase/execution/support）**：

1. **ExecutionContextLoader**（执行上下文加载器）：
   - 从 `Task → Slice → Plan` 还原配置快照与表达式快照
   - 校验 `exprHash/configHash`，失败抛 `ConfigurationTamperedException`
   - 通过 `ExpressionCompilerPort` 编译表达式
   - 编译失败时返回 `isValid=false`，由编排层抛 `ExpressionCompilationException`
   - 耗时超过租约时长的1/3主动续租

2. **ExpressionCompilerPort**（Domain层端口接口）：
   - 定义 `ExprCompilationRequest`（record）
   - 定义 `ExprCompilationResult`（record，包含isValid/validationMessage）

3. **ExpressionCompilerAdapter**（Infra层适配器）：
   - 适配 `patra-spring-boot-starter-expr` 编译器
   - 失败时返回 `isValid=false` 和 `validationMessage`

4. **PubMed/EPMC表达式编译规则**（可选扩展）：
   - PubMed：ESearch参数（term/retmax/sort）
   - EPMC：Search参数（query/pageSize/sort）

#### Definition of Done (DoD)

- ✅ 心跳续租连续失败3次后正确抛 `LeaseRevokedException`
- ✅ 心跳线程设置 `leaseRevoked` 标志位通知主线程
- ✅ 表达式编译失败返回 `isValid=false` 并提供 `validationMessage`
- ✅ 配置哈希校验失败抛 `ConfigurationTamperedException`
- ✅ 批量心跳续租可配置启用/禁用

---

### 阶段2：评审阶段

**子代理**：`code-reviewer`

#### 评审关注点

**租约机制**：
- ✅ 租约CAS并发安全性（SQL是否正确）
- ✅ 心跳续租失败处理逻辑（连续失败阈值/租约验证）
- ✅ `leaseRevoked` 标志位通知机制（volatile/AtomicBoolean）
- ✅ 批量续租SQL性能优化（IN子句大小限制）

**配置还原与表达式编译**：
- ✅ `ExecutionContextLoader` 事务边界（只读事务）
- ✅ `ExpressionCompiler` Port-Adapter解耦（Domain不依赖具体实现）
- ✅ 编译失败异常处理（不抛异常，返回isValid标志）
- ✅ 配置哈希校验算法一致性（与Plan生成时一致）

#### Definition of Done (DoD)

- ✅ 无Critical/High级别问题
- ✅ 关键逻辑有单元测试覆盖（租约CAS/心跳续租/表达式编译）

---

### 阶段3：单测阶段

**子代理**：`qa-unit-tests`

#### 测试范围

1. **租约管理单元测试**：
   - 租约抢占的并发安全性（模拟多节点，使用CountDownLatch）
   - 心跳续租的定时执行（使用TestScheduler或Awaitility）
   - 租约超时后的自动释放
   - 连续失败阈值触发租约验证
   - 租约被接管后抛 `LeaseRevokedException`

2. **配置还原单元测试**：
   - 配置哈希校验成功场景
   - 配置哈希校验失败场景（抛 `ConfigurationTamperedException`）
   - 表达式哈希校验失败场景

3. **表达式编译单元测试**：
   - PubMed表达式编译成功
   - EPMC表达式编译成功
   - 不支持的数据源返回 `isValid=false`
   - 编译失败返回 `validationMessage`

#### Definition of Done (DoD)

- ✅ 单元测试覆盖率 ≥ 80%
- ✅ 测试稳定（无偶发失败）
- ✅ 异步逻辑测试正确（使用Awaitility等待）

---

### 阶段4：集成测试阶段

**子代理**：`qa-integration-tests`

#### 测试范围

1. **租约抢占并发测试**（Testcontainers MySQL）：
   - 模拟3个节点同时抢占同一任务租约
   - 验证只有1个节点成功抢占
   - 验证失败节点收到正确异常

2. **心跳续租集成测试**：
   - 模拟心跳续租连续失败3次
   - 验证触发租约验证
   - 验证租约被接管后抛 `LeaseRevokedException`

3. **表达式编译端到端测试**：
   - 从数据库加载Plan/Slice配置
   - 编译PubMed表达式并验证query参数
   - 编译EPMC表达式并验证query参数

#### Definition of Done (DoD)

- ✅ 核心场景集成测试100%通过
- ✅ 租约并发测试可靠（重复运行10次无失败）
- ✅ 集成测试执行时间 < 3分钟

---

### 阶段5：门禁阶段

**子代理**：`qa-quality-gates`

#### Definition of Done (DoD)

- ✅ 单元测试覆盖率 ≥ 80%
- ✅ 集成测试100%通过
- ✅ 无Critical/Blocker静态检查问题
- ✅ 构建成功

---

### 阶段6：文档阶段

**子代理**：`mermaid-expert` + `docs-engineer`

#### 产出清单

1. **租约状态机图**（Mermaid）：
   ```mermaid
   stateDiagram-v2
       [*] --> Available: 无租约
       Available --> Leased: 抢占成功
       Leased --> Leased: 续租成功
       Leased --> Available: 租约超时/释放
       Leased --> Revoked: 被其他节点接管
   ```

2. **心跳续租时序图**（Mermaid）：
   - 心跳线程 → MySQL → 租约验证 → 标志位通知

3. **表达式编译流程图**（Mermaid）：
   - ExecutionContextLoader → ExpressionCompilerPort → patra-expr-kernel

4. **模块文档**：
   - 租约机制设计说明
   - 表达式编译器对接说明

#### Definition of Done (DoD)

- ✅ 图表清晰可读
- ✅ 文档完整准确

---

### 批次2交付物

- ✅ 租约管理服务完整（抢占/续租/释放/心跳/会话）
- ✅ 配置还原与表达式编译完整
- ✅ 租约并发抢占测试通过
- ✅ 心跳续租连续失败后正确抛异常
- ✅ 表达式编译端到端测试通过
- ✅ 质量门禁PASS
- ✅ 租约状态机图 + 心跳时序图 + 表达式编译流程图

---

## 📦 批次3：数据采集链路（4-5天）

### 任务范围

- **任务5**：批次规划功能
- **任务6**：批次执行功能
- **任务7**：游标推进功能

### 目标

实现完整的数据采集链路：**批次规划 → 批次执行 → 游标推进**。

---

### 阶段1：实现阶段

**子代理**：`java-developer`

#### 产出清单

**批次规划（patra-ingest-infra/strategy/planner）**：

1. **BatchPlanner**（策略接口，Domain层）：
   - `plan(BatchPlanningContext context): BatchPlan`

2. **BatchPlannerRegistry**（Domain层）：
   - 根据 `provenanceCode` 选择规划器

3. **PubMedBatchPlanner**（Infra层）：
   - 调用ESearch API获取总数和WebEnv
   - 计算批次数：`ceil(总量 ÷ 每批大小)`
   - 若 `> maxBatchesPerExecution` 则抛 `BatchPlanningException`
   - 生成批次记录（retstart、retmax、webEnv）
   - 为每批生成幂等键（SHA256(runId + ":" + batchNo)）
   - 分批插入批次记录（按 `insertChunkSize=100`）

4. **EpmcBatchPlanner**（Infra层）：
   - 调用Search API获取第一页和cursor token
   - 生成批次记录（cursorToken）
   - 计算批次数并检查超限
   - 分批插入批次记录

**批次执行（patra-ingest-infra/strategy/executor）**：

1. **BatchExecutor**（策略接口，Domain层）：
   - `execute(BatchExecutionContext context): BatchExecutionResult`

2. **BatchExecutorRegistry**（Domain层）：
   - 根据 `provenanceCode` 选择执行器

3. **StorageAdapter**（Domain层端口接口）：
   - `upload(path, data): StorageUploadResult`

4. **MinIOStorageAdapter**（Infra层）：
   - 上传压缩数据到MinIO
   - 路径生成：`{bucket}/{provenanceCode-lower}/{yyyy}/{MM}/run_{runId}/batch_{batchNo}.json.gz`
   - 重试逻辑：3次指数退避

5. **PubMedBatchExecutor**（Infra层）：
   - 调用EFetch API获取文献数据（XML）
   - 实现批次幂等检查（跳过已成功的批次）
   - 实现响应解析（XML → 领域对象）
   - 实现数据压缩（gzip）
   - 实现对象存储上传
   - 实现Outbox消息发布（在事务内）
   - 实现批次状态更新（RUNNING → SUCCEEDED/FAILED）
   - 实现统计信息记录（recordCount、fileSizeBytes、maxTimestamp）
   - 支持执行模式：`SEQUENTIAL` 或 `PARALLEL`

6. **EpmcBatchExecutor**（Infra层）：
   - 调用Search API获取文献数据（JSON）
   - 实现批次幂等/解析/压缩/上传/Outbox/状态更新
   - 支持执行模式：`SEQUENTIAL` 或 `PARALLEL`

**游标推进（patra-ingest-infra/strategy/advancer）**：

1. **CursorAdvancer**（策略接口，Domain层）：
   - `advance(CursorAdvancementContext context): CursorAdvancementResult`

2. **CursorAdvancerRegistry**（Domain层）：
   - 根据 `operationType` 选择推进器

3. **TimestampCursorAdvancer**（Infra层）：
   - 从所有SUCCEEDED批次的stats中提取最大时间戳
   - 实现乐观锁更新游标表（使用version字段）
   - 实现游标事件插入（带幂等键）
   - 实现重试逻辑：乐观锁冲突时重试最多3次

4. **IdCursorAdvancer**（Infra层）：
   - 从所有SUCCEEDED批次的stats中提取最大记录ID
   - 实现乐观锁更新和游标事件插入
   - 实现重试逻辑

5. **WindowCursorAdvancer**（Infra层，BACKFILL场景）：
   - 记录已完成的时间窗口范围
   - 实现乐观锁更新和游标事件插入

6. **CursorAdvancementRetryService**（App层）：
   - 重试 `CURSOR_PENDING` 任务的游标推进
   - 提供 `retryAdvancement(taskId, runId)` 与 `scanAndRetry()`
   - 定时扫描（每60秒）
   - 维护最多5次重试计数，超过阈值触发告警
   - 幂等保障：利用 `lastAttemptRunId` 与游标事件幂等键

#### Definition of Done (DoD)

- ✅ 批次规划超限直接拒绝（不执行部分批次）
- ✅ 批次幂等键生成正确（SHA256(runId + ":" + batchNo)）
- ✅ 批次记录分批插入（每批100条，独立事务）
- ✅ 游标推进乐观锁冲突重试最多3次
- ✅ CURSOR_PENDING状态任务触发异步重试（最多5次）
- ✅ MinIO上传失败重试3次（指数退避）

---

### 阶段2：评审阶段

**子代理**：`code-reviewer` + `architecture-reviewer`

#### 评审关注点

**架构维度**：
- ✅ 策略模式实现正确性（接口在Domain，实现在Infra）
- ✅ 策略注册表设计（Registry如何选择策略）
- ✅ 批次执行并发模式切换（SEQUENTIAL vs PARALLEL）

**代码质量维度**：
- ✅ 批次级幂等保障机制（幂等键生成/校验）
- ✅ 游标推进乐观锁与事件幂等键
- ✅ Outbox消息发布事务一致性（在批次状态更新同一事务内）
- ✅ 对象存储上传重试逻辑（指数退避实现）

#### Definition of Done (DoD)

- ✅ 架构合规（策略分层正确）
- ✅ 无Critical/High级别问题

---

### 阶段3：单测阶段

**子代理**：`qa-unit-tests`

#### 测试范围

1. **批次规划单元测试**：
   - 批次数计算的正确性
   - 批次参数的生成逻辑（PubMed: retstart/retmax；EPMC: cursorToken）
   - 批次幂等键的唯一性
   - 批次数超限抛 `BatchPlanningException`

2. **批次执行单元测试**：
   - 批次幂等检查逻辑（跳过已成功批次）
   - 部分批次失败不影响其他批次
   - 对象存储上传重试逻辑（Mock StorageAdapter）
   - 数据压缩逻辑（gzip）

3. **游标推进单元测试**：
   - 时间戳聚合的正确性（从多个批次stats提取最大值）
   - 乐观锁冲突重试逻辑（Mock Repository）
   - 游标事件幂等性（幂等键生成）

#### Definition of Done (DoD)

- ✅ 单元测试覆盖率 ≥ 80%
- ✅ 策略模式测试完整（每个策略独立测试）

---

### 阶段4：集成测试阶段

**子代理**：`qa-integration-tests`

#### 测试范围

1. **端到端批次采集测试**（WireMock模拟PubMed/EPMC API）：
   - 批次规划 → 批次执行 → Outbox发布 → 游标推进
   - 验证MinIO上传文件完整性（Testcontainers MinIO）
   - 验证Outbox消息正确性

2. **批次规划超限测试**：
   - 模拟总量超过 `maxBatchesPerExecution`
   - 验证抛 `BatchPlanningException`

3. **批次幂等测试**：
   - 重复执行已成功批次
   - 验证跳过已成功批次

4. **游标推进乐观锁冲突测试**：
   - 模拟并发更新游标（多线程）
   - 验证重试逻辑生效

5. **CURSOR_PENDING异步重试测试**：
   - 模拟游标推进失败
   - 验证任务状态更新为 `CURSOR_PENDING`
   - 验证异步重试服务正确重试

#### Definition of Done (DoD)

- ✅ 核心场景集成测试100%通过
- ✅ 集成测试执行时间 < 5分钟
- ✅ WireMock模拟API稳定

---

### 阶段5：门禁阶段

**子代理**：`qa-quality-gates`

#### Definition of Done (DoD)

- ✅ 单元测试覆盖率 ≥ 80%
- ✅ 集成测试100%通过
- ✅ 无Critical/Blocker静态检查问题
- ✅ 构建成功

---

### 阶段6：文档阶段

**子代理**：`mermaid-expert` + `docs-engineer`

#### 产出清单

1. **批次执行流程图**（Mermaid）：
   ```mermaid
   flowchart TD
       A[开始] --> B[批次规划]
       B --> C{批次数超限?}
       C -->|是| D[抛异常]
       C -->|否| E[分批插入批次记录]
       E --> F[批次执行循环]
       F --> G{幂等检查}
       G -->|已成功| H[跳过]
       G -->|未执行| I[调用API]
       I --> J[解析/压缩/上传]
       J --> K[Outbox发布]
       K --> L[更新批次状态]
       L --> M{更多批次?}
       M -->|是| F
       M -->|否| N[结束]
   ```

2. **游标推进时序图**（Mermaid）：
   - CompleteTaskExecutionUseCase → CursorAdvancer → Cursor Repository → Cursor Event

3. **数据采集链路架构图**（Mermaid）：
   - MQ消费 → 批次规划 → 批次执行 → MinIO上传 → Outbox发布 → 游标推进

4. **模块文档**：
   - 批次规划策略说明（PubMed/EPMC差异）
   - 批次执行并发模式说明（SEQUENTIAL vs PARALLEL）
   - 游标推进策略说明（时间型/ID型/窗口型）

#### Definition of Done (DoD)

- ✅ 图表清晰可读
- ✅ 文档完整准确

---

### 批次3交付物

- ✅ 批次规划策略完整（PubMed/EPMC）
- ✅ 批次执行策略完整（含对象存储上传）
- ✅ 游标推进策略完整（时间型/ID型/重试服务）
- ✅ 批次规划超限测试通过
- ✅ 批次幂等测试通过
- ✅ 游标推进乐观锁冲突测试通过
- ✅ 端到端批次采集测试通过
- ✅ 质量门禁PASS
- ✅ 批次执行流程图 + 游标推进时序图 + 数据采集链路架构图

---

## 📦 批次4：编排与交付（2-3天）

### 任务范围

- **任务8**：任务编排和收尾
- **任务9**：配置管理和自动配置
- **任务10**：文档和部署准备

### 目标

实现任务执行编排器（按ADR-001拆分为3个用例），完成配置管理，产出交付文档。

---

### 阶段1：实现阶段

**子代理**：`java-developer`

#### 产出清单

**任务编排（patra-ingest-app/usecase/execution）**：

1. **PrepareTaskExecutionUseCase**（准备用例）：
   - 幂等检查（IdempotencyChecker）
   - 租约抢占（LeaseManagementService）
   - 会话初始化（ExecutionSessionManager，创建TaskRun + 启动心跳）
   - 上下文加载（ExecutionContextLoader，配置快照 + 表达式编译）
   - 返回 `PrepareResult`（session、executionContext）

2. **ExecuteTaskBatchesUseCase**（执行用例）：
   - 批次规划（BatchPlanner）
   - 批次执行（BatchExecutor，支持SEQUENTIAL/PARALLEL）
   - 返回 `ExecuteResult`（批次统计信息）

3. **CompleteTaskExecutionUseCase**（完成用例）：
   - 游标推进（CursorAdvancer）
   - 最终状态判断（ExecutionFinalizer）：
     * 全部批次成功 + 游标推进成功 → `SUCCEEDED`
     * 全部批次成功 + 游标推进失败 → `CURSOR_PENDING`
     * 部分批次失败 → `PARTIAL`
     * 全部批次失败 → `FAILED`
   - 更新TaskRun和Task记录（在事务内）
   - 清空租约
   - 返回 `CompleteResult`（最终状态）

4. **TaskExecutionOrchestrator**（轻量级编排器，~30行）：
   - 调用3个用例：`prepare()` → `execute()` → `complete()`
   - `finally`：`session.cleanup()` 停止心跳并释放租约
   - 处理 `LeaseRevokedException`：快速失败并返回 `leaseRevoked` 结果
   - 其他异常：记录ERROR指标与日志
   - 添加 `@Timed/@Counted` 注解

5. **IngestStreamConsumers接入**（adapter/consumer）：
   - 在 `ingestTaskReadyConsumer` 内调用 `TaskExecutionOrchestrator.execute()`
   - 实现MDC设置（correlationId、taskId）
   - 异常处理与清理

6. **XXL-Job调度器适配器**（可选）：
   - `TaskExecutionScheduler`：定时触发任务就绪消息

7. **错误码和异常映射**：
   - 定义 `TaskExecutionErrorCode` 枚举（ING-1001..ING-1008）
   - 实现 `ErrorMappingContributor`

**配置管理（patra-ingest-boot/config）**：

1. **TaskExecutionProperties**：
   - `@ConfigurationProperties(prefix = "papertrace.ingest")`
   - 分组：`exec/batch/heartbeat/storage`
   - 关键字段：
     * `exec.lease-ttl-seconds`、`exec.heartbeat-interval-seconds`
     * `batch.max-batches-per-execution`、`batch.insert-chunk-size`
     * `batch.execution-mode`、`batch.parallel-threads`
     * `heartbeat.batch-renewal-enabled`、`heartbeat.batch-renewal-size`
     * `storage.bucket`、`storage.upload-retry-times`

2. **TaskExecutionAutoConfiguration**（可选）：
   - 注册TaskExecution用例/支持服务Bean
   - 复用现有 `IngestAppConfig` 与 `TaskExecutionConfig`

**运维型作业（adapter/scheduler）**：

1. **OrphanBatchCleanupTask**：
   - 每6小时扫描并清理孤儿批次（创建时间 > 24h 且 run 不活跃）
   - 分批删除（每批最多1000条）
   - 记录清理指标 `ingest.cleanup.orphan_batches`
   - 审计日志

#### Definition of Done (DoD)

- ✅ Orchestrator仅协调3个用例，无业务逻辑（~30行）
- ✅ 每个用例职责单一（~50-80行）
- ✅ `LeaseRevokedException` 快速失败并记录WARN
- ✅ 配置属性类与现有 `papertrace.ingest.exec.*` 对齐
- ✅ MQ消费者接入完整（MDC设置/异常处理）

---

### 阶段2：评审阶段

**子代理**：`architecture-reviewer` + `code-reviewer`

#### 评审关注点

**架构维度**：
- ✅ 用例拆分ADR-001是否正确落地（3个独立用例）
- ✅ Orchestrator是否轻量（无业务逻辑，仅协调）
- ✅ 事务边界是否清晰（3个独立事务）
- ✅ 资源清理逻辑是否完整（`finally`块停止心跳+释放租约）

**代码质量维度**：
- ✅ 租约被接管快速失败逻辑
- ✅ 游标推进失败转CURSOR_PENDING逻辑
- ✅ 配置属性类完整性
- ✅ 错误码映射完整性

#### Definition of Done (DoD)

- ✅ 架构合规（用例拆分正确）
- ✅ 无Critical/High级别问题

---

### 阶段3：单测阶段

**子代理**：`qa-unit-tests`

#### 测试范围

1. **编排器单元测试**：
   - 正常流程测试（prepare → execute → complete）
   - 租约被接管快速失败测试
   - 资源清理测试（`finally`块执行）

2. **最终状态判断测试**：
   - 全部批次成功 + 游标推进成功 → `SUCCEEDED`
   - 全部批次成功 + 游标推进失败 → `CURSOR_PENDING`
   - 部分批次失败 → `PARTIAL`
   - 全部批次失败 → `FAILED`

3. **统计信息聚合测试**：
   - 从多个批次聚合recordCount/fileSizeBytes等

#### Definition of Done (DoD)

- ✅ 单元测试覆盖率 ≥ 80%
- ✅ 编排器测试完整（各种分支场景）

---

### 阶段4：集成测试阶段

**子代理**：`qa-integration-tests`

#### 测试范围

1. **端到端完整流程测试**（MQ触发 → 游标推进）：
   - 发送MQ消息到 `ingest.task.ready`
   - 验证任务执行完整流程
   - 验证MinIO文件上传成功
   - 验证Outbox消息发布
   - 验证游标推进成功
   - 验证任务最终状态为 `SUCCEEDED`

2. **部分失败容忍测试**：
   - 模拟某批次API调用失败
   - 验证其他批次继续执行
   - 验证任务最终状态为 `PARTIAL`

3. **租约被接管测试**：
   - 模拟心跳续租连续失败3次
   - 验证租约被其他节点接管
   - 验证任务快速失败（抛 `LeaseRevokedException`）

4. **幂等性测试**：
   - 重复执行已成功的任务
   - 验证幂等检查生效（直接返回）

5. **租约抢占测试**：
   - 模拟多节点并发抢占同一任务
   - 验证只有1个节点成功

#### Definition of Done (DoD)

- ✅ 端到端完整流程测试100%通过
- ✅ 所有核心场景覆盖
- ✅ 集成测试执行时间 < 5分钟

---

### 阶段5：门禁阶段

**子代理**：`qa-quality-gates`

#### 检查项

| 检查项 | 阈值 | 说明 |
|--------|------|------|
| 单元测试覆盖率 | ≥ 80% | JaCoCo报告 |
| 集成测试通过率 | 100% | 所有核心场景覆盖 |
| 静态检查问题 | 0 Critical/Blocker | Checkstyle/SpotBugs |
| 构建成功 | mvn package成功 | 包含所有子模块 |

#### Definition of Done (DoD)

- ✅ **最终质量门禁PASS**
- ✅ 所有检查项达标
- ✅ 遗留问题已记录并排期

---

### 阶段6：文档阶段

**子代理**：`mermaid-expert` + `docs-engineer`

#### 产出清单

1. **总体架构图**（六边形架构分层）：
   ```mermaid
   graph TB
       subgraph Adapter
           MQ[MQ消费者]
           REST[REST端点]
           Scheduler[XXL-Job调度器]
       end
       subgraph Application
           Orchestrator[TaskExecutionOrchestrator]
           Prepare[PrepareUseCase]
           Execute[ExecuteUseCase]
           Complete[CompleteUseCase]
           Support[支持服务]
       end
       subgraph Domain
           Aggregate[聚合根]
           Port[端口接口]
           Strategy[策略接口]
       end
       subgraph Infrastructure
           Repository[仓储实现]
           StrategyImpl[策略实现]
           Adapter[适配器]
       end
       MQ --> Orchestrator
       Orchestrator --> Prepare
       Orchestrator --> Execute
       Orchestrator --> Complete
       Prepare --> Support
       Execute --> Strategy
       Complete --> Strategy
       Strategy --> StrategyImpl
       Support --> Port
       Port --> Repository
   ```

2. **端到端执行流程图**（从MQ消费到游标推进）：
   - MQ消费 → 幂等检查 → 租约抢占 → 心跳启动 → 配置加载 → 表达式编译 → 批次规划 → 批次执行 → 游标推进 → 状态更新 → 心跳停止

3. **patra-ingest/README.md**（快速开始/核心功能/配置说明）：
   - 模块概述
   - 快速开始（本地启动）
   - 核心功能（任务执行引擎/批次规划/批次执行/游标推进）
   - 配置说明（配置项列表 + 默认值）

4. **docs/modules/patra-ingest/deep-dive.md**（详细架构设计/容错机制）：
   - 架构设计（六边形架构/DDD/事件驱动）
   - 执行流程（端到端详细流程）
   - 容错机制（租约协调/心跳续租/批次幂等/游标推进重试）
   - 性能优化（批量心跳续租/批次并发执行）
   - 监控告警（关键指标/告警规则）

5. **运维手册**（部署/监控/故障排查）：
   - 部署指南（Docker Compose/K8s）
   - 监控指标（租约/批次/游标/心跳指标）
   - 告警规则（Critical告警/High告警）
   - 故障排查（常见问题/排查步骤）

6. **Docker Compose配置**：
   - `docker/compose/docker-compose.dev.yaml`：添加patra-ingest服务
   - 配置环境变量、端口映射、依赖关系

#### Definition of Done (DoD)

- ✅ README.md完整（快速开始/核心功能/配置说明）
- ✅ 深度文档完整（架构设计/执行流程/容错机制）
- ✅ 运维手册完整（部署/监控/故障排查）
- ✅ Mermaid图表清晰可读
- ✅ Docker Compose可一键启动

---

### 批次4交付物

- ✅ 任务执行编排器完整（Orchestrator + 3用例）
- ✅ MQ消费者接入完整
- ✅ 配置管理完整（Properties + AutoConfiguration）
- ✅ 端到端完整流程测试通过
- ✅ 部分失败容忍测试通过
- ✅ 租约被接管测试通过
- ✅ 最终质量门禁PASS
- ✅ 总体架构图 + 端到端流程图 + README + 深度文档 + 运维手册 + Docker Compose

---

## 🎯 关键决策点（Gates）

### Gate 1（批次1后）：基础设施层就绪

**检查点**：
- ✅ Domain层完整性检查（聚合根/值对象/端口接口）
- ✅ Repository层就绪（DO/Mapper/Converter/Repository实现）
- ✅ 单元测试覆盖率 ≥ 80%
- ✅ 集成测试100%通过
- ✅ 质量门禁PASS

**决策**：
- ✅ **PASS**：进入批次2
- ❌ **FAIL**：修复Critical/High问题后重新评审

---

### Gate 2（批次2后）：核心能力验证

**检查点**：
- ✅ 租约机制并发安全验证（租约抢占/心跳续租）
- ✅ 表达式编译端到端验证（PubMed/EPMC）
- ✅ 单元测试覆盖率 ≥ 80%
- ✅ 集成测试100%通过
- ✅ 质量门禁PASS

**决策**：
- ✅ **PASS**：进入批次3
- ❌ **FAIL**：修复Critical/High问题后重新评审

---

### Gate 3（批次3后）：数据采集链路验证

**检查点**：
- ✅ 批次采集端到端验证（批次规划 → 批次执行 → 游标推进）
- ✅ 批次规划超限测试通过
- ✅ 批次幂等测试通过
- ✅ 游标推进乐观锁验证
- ✅ 单元测试覆盖率 ≥ 80%
- ✅ 集成测试100%通过
- ✅ 质量门禁PASS

**决策**：
- ✅ **PASS**：进入批次4
- ❌ **FAIL**：修复Critical/High问题后重新评审

---

### Gate 4（批次4后）：交付就绪

**检查点**：
- ✅ 端到端完整流程验证（MQ触发 → 游标推进）
- ✅ 部分失败容忍测试通过
- ✅ 租约被接管测试通过
- ✅ 最终质量门禁PASS
- ✅ 文档完整可读（README + 深度文档 + 运维手册）
- ✅ Docker Compose可一键启动

**决策**：
- ✅ **PASS**：可部署交付
- ❌ **FAIL**：修复Critical/High问题后重新评审

---

## ⚠️ 关键风险与缓解措施

### 风险1：租约机制性能瓶颈

**现象**：
- 高并发心跳续租导致MySQL压力过大
- 租约续租耗时p99 > 500ms

**缓解措施**：
- **短期**：启用批量心跳续租（`batch-renewal-enabled=true`）
- **中期**：引入Redis混合模式（心跳写Redis，60秒同步MySQL）
- **长期**：升级到etcd/Consul专业协调服务

**监控指标**：
- `ingest.heartbeat.renewal.duration.p99 < 200ms`
- `ingest.heartbeat.batch.renewal.count`

---

### 风险2：批次数超限静默丢数据

**现象**：
- 任务批次数 > `maxBatchesPerExecution` 时只执行部分批次
- 用户误以为全部数据已采集

**缓解措施**：
- 批次规划阶段直接拒绝任务（抛 `BatchPlanningException`）
- 分批插入批次记录（`insertChunkSize=100`）
- 定时清理孤儿批次（6小时扫描一次）

**监控指标**：
- `ingest.batch.planning.rejected`（批次数超限任务计数）
- `ingest.batch.planning.total_batches`（批次数分布）

---

### 风险3：游标推进失败导致数据丢失

**现象**：
- 批次全部成功但游标推进失败（乐观锁冲突/DB异常）
- 下次采集会漏掉本次数据

**缓解措施**：
- 引入 `CURSOR_PENDING` 状态
- `CursorAdvancementRetryService` 异步重试（最多5次）
- 超过5次触发Critical告警人工介入

**监控指标**：
- `ingest.cursor.advancement.retry`（重试次数）
- `ingest.cursor.advancement.failed`（最终失败计数）

---

### 风险4：租约被接管但任务仍继续执行

**现象**：
- 心跳续租连续失败，租约被其他节点接管
- 原节点仍在执行批次，导致资源浪费和数据冲突

**缓解措施**：
- 心跳线程设置 `leaseRevoked` 标志位（`volatile boolean` 或 `AtomicBoolean`）
- 批次执行前检查标志位，已撤销立即抛 `LeaseRevokedException`
- 快速失败，避免浪费资源

**监控指标**：
- `ingest.lease.revoked`（租约撤销计数）
- `ingest.lease.validation.failed`（租约验证失败计数）

---

## 📊 工时估算

### 详细工时表

| 批次 | 实现 | 评审 | 单测 | 集成 | 门禁 | 文档 | 小计 |
|------|------|------|------|------|------|------|------|
| **批次1：基础设施层** | 1天 | 0.5天 | 0.5天 | 0.5天 | 0.2天 | 0.3天 | **3天** |
| **批次2：核心能力层** | 2天 | 0.5天 | 0.5天 | 0.5天 | 0.2天 | 0.3天 | **4天** |
| **批次3：数据采集链路** | 2.5天 | 0.5天 | 1天 | 1天 | 0.2天 | 0.3天 | **5.5天** |
| **批次4：编排与交付** | 1.5天 | 0.5天 | 0.5天 | 0.5天 | 0.2天 | 0.3天 | **3.5天** |
| **总计** | **7天** | **2天** | **2.5天** | **2.5天** | **0.8天** | **1.2天** | **16天** |

### 工时说明

- **实现阶段**：包含架构设计、编码实现、自测
- **评审阶段**：包含架构评审、代码评审、评审意见修复
- **单测阶段**：包含单元测试编写、测试调试、覆盖率达标
- **集成阶段**：包含集成测试编写、环境搭建、测试调试
- **门禁阶段**：包含质量指标汇总、分析、补救措施制定
- **文档阶段**：包含图表绘制、文档编写、文档评审

### 实际执行时间

考虑并行开发和等待时间：
- **理想情况**（顺序执行）：16个工作日
- **实际情况**（并行开发 + 等待）：**10-13个工作日**

**假设**：
- 部分阶段可并行（如实现阶段与上一批次的文档阶段）
- 评审等待时间1-2小时
- 门禁等待时间0.5-1小时

---

## ✅ 下一步行动

### 立即行动

1. **确认流水线方案**：
   - 评审本流水线编排方案
   - 确认批次划分合理性
   - 确认质量门禁标准

2. **准备开发环境**：
   - 配置本地Docker Compose环境（MySQL/RocketMQ/MinIO）
   - 准备PubMed/EPMC测试账号（或使用WireMock）
   - 配置IDE插件（Lombok/MapStruct/Checkstyle）

3. **开始批次1实现**：
   - 调用 `architecture-designer` 子代理出设计方案
   - 调用 `java-developer` 子代理实现Domain层和Repository层
   - 按流水线顺序完成6个阶段

### 跟踪与反馈

- 使用 `TodoWrite` 工具标记任务进度（已创建24个待办事项）
- 每个批次结束后召开简短回顾会议（15分钟）
- 关键决策点（Gates）进行正式评审

