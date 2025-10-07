# Requirements Document

## Introduction

本规范定义了 Papertrace 平台的通用数据采集任务执行引擎。该引擎负责从消息队列（MQ）消费任务就绪消息，通过分布式租约机制协调多节点执行，将大任务拆分为小批次并发执行，调用外部数据源 API 采集数据，处理响应并上传至对象存储，最后推进游标记录采集进度。

该执行引擎是 `patra-ingest` 模块的核心组件，支持 PubMed、EPMC、Crossref 等多种医学文献数据源的统一采集流程，确保数据采集的可靠性、可追溯性和可扩展性。

核心价值：
- **可靠采集**：通过多层幂等保障和事务机制，确保数据不丢失、不重复
- **断点续传**：任务中断后能从断点继续，避免重复劳动
- **进度追踪**：通过游标/水位线机制记录采集进度，支持增量采集
- **弹性容错**：部分批次失败不影响整体，支持重试和降级
- **可观测性**：完整的追踪链路和详细的执行日志

## Requirements

### Requirement 1: 任务就绪通知与幂等检查

**User Story:** 作为调度系统，我希望通过 MQ 发送任务就绪消息触发任务执行，并且系统能自动识别已完成的任务避免重复执行，以确保任务执行的幂等性。

#### Acceptance Criteria

1. WHEN 调度器或上游系统发送任务就绪消息到 MQ THEN 系统 SHALL 消费该消息并提取任务 ID 和幂等键
2. WHEN 收到任务就绪消息 THEN 系统 SHALL 查询任务表检查该任务是否已成功完成（状态 = SUCCEEDED 且幂等键匹配）
3. IF 任务已成功完成 THEN 系统 SHALL 跳过执行并记录日志，不抛出异常
4. IF 任务未完成或幂等键不匹配 THEN 系统 SHALL 继续执行后续步骤
5. WHEN 幂等检查失败（数据库异常） THEN 系统 SHALL 抛出异常并触发 MQ 重试

### Requirement 2: 分布式租约抢占

**User Story:** 作为分布式系统，我希望多个工作节点能通过 CAS 机制安全地争抢任务执行权，避免同一任务被多个节点并发执行，以保证任务执行的唯一性。

#### Acceptance Criteria

1. WHEN 节点尝试执行任务 THEN 系统 SHALL 使用 CAS（Compare-And-Set）机制更新任务表的租约字段
2. WHEN 执行 CAS 更新 THEN 系统 SHALL 设置 lease_owner = "节点ID:随机标识" 且 leased_until = 当前时间 + 60秒
3. WHEN CAS 更新条件为 WHERE id=? AND (lease_owner IS NULL OR leased_until < NOW()) THEN 系统 SHALL 仅在租约为空或已过期时成功抢占
4. IF CAS 更新影响行数 = 1 THEN 系统 SHALL 认为抢占成功并继续执行
5. IF CAS 更新影响行数 = 0 THEN 系统 SHALL 认为抢占失败并优雅退出，不抛出异常
6. WHEN 抢占失败 THEN 系统 SHALL 记录 INFO 级别日志说明其他节点正在执行

### Requirement 3: 执行会话初始化与心跳续租

**User Story:** 作为任务执行引擎，我希望在抢占成功后初始化执行会话并启动心跳续租机制，以维持租约有效性并支持节点崩溃后的任务接管。

#### Acceptance Criteria

1. WHEN 租约抢占成功 THEN 系统 SHALL 在一个数据库事务内完成以下操作：更新任务状态为 RUNNING、创建 TaskRun 执行记录、记录 attemptNo、绑定执行窗口和追踪标识
2. WHEN 创建 TaskRun 记录 THEN 系统 SHALL 设置 attemptNo = 上次 attemptNo + 1
3. WHEN 创建 TaskRun 记录 THEN 系统 SHALL 绑定 correlationId 和 schedulerRunId 用于分布式追踪
4. WHEN 事务提交成功 THEN 系统 SHALL 启动心跳续租定时任务
5. WHEN 心跳定时任务执行 THEN 系统 SHALL 每隔 20 秒（租约时长的 1/3）更新 leased_until 字段
6. WHEN 心跳续租 THEN 系统 SHALL 验证 lease_owner 仍为当前节点，防止租约被其他节点接管
7. IF 心跳续租失败（网络故障、节点崩溃） THEN 租约 SHALL 在 60 秒后自动过期，允许其他节点接管
8. WHEN 任务执行完成或失败 THEN 系统 SHALL 停止心跳定时任务并释放资源

### Requirement 4: 配置快照还原与哈希校验

**User Story:** 作为任务执行引擎，我希望从任务计划时保存的配置快照中还原执行参数，并通过哈希校验确保配置完整性，以保证任务按创建时的配置执行，实现幂等性。

#### Acceptance Criteria

1. WHEN 执行会话初始化完成 THEN 系统 SHALL 从任务记录中加载 provenance_config_snapshot 和 expr_snapshot
2. WHEN 加载 provenance_config_snapshot THEN 系统 SHALL 解析出 API 基础地址、HTTP 超时/重试/代理设置、分页模式、每页大小、批处理策略
3. WHEN 加载 expr_snapshot THEN 系统 SHALL 解析出搜索条件（关键词、时间范围、字段过滤等）和表达式哈希值
4. WHEN 加载配置快照 THEN 系统 SHALL 计算配置的哈希值并与保存的哈希值对比
5. IF 哈希值不匹配 THEN 系统 SHALL 抛出 ConfigurationTamperedException 并标记任务为 FAILED
6. IF 哈希值匹配 THEN 系统 SHALL 继续执行后续步骤
7. WHEN 配置加载耗时超过租约时长的 1/3 THEN 系统 SHALL 主动执行心跳续租
8. WHEN 配置加载完成 THEN 系统 SHALL 刷新心跳时间戳

### Requirement 5: 查询表达式编译

**User Story:** 作为任务执行引擎，我希望将保存的查询表达式（JSON 格式）编译成目标数据源能理解的查询参数，以支持不同数据源的统一表达式管理。

#### Acceptance Criteria

1. WHEN 配置快照还原完成 THEN 系统 SHALL 调用表达式编译器编译 expr_snapshot
2. WHEN 编译表达式 THEN 系统 SHALL 根据 provenanceCode 选择对应的编译器实现
3. WHEN 编译 PubMed 表达式 THEN 系统 SHALL 生成 term、retmax、sort 等 ESearch API 参数
4. WHEN 编译 EPMC 表达式 THEN 系统 SHALL 生成 query、pageSize、sort 等 Search API 参数
5. IF 表达式编译失败（语法错误、不支持的操作符） THEN 系统 SHALL 抛出 ExpressionCompilationException 并标记任务为 FAILED
6. IF 表达式编译成功 THEN 系统 SHALL 返回编译后的查询参数对象

### Requirement 6: 批次规划与批次记录生成

**User Story:** 作为任务执行引擎，我希望根据数据源特性和配置将大任务拆分为多个小批次，并预先生成批次记录落库，以支持并发执行、断点续传和进度追踪。

#### Acceptance Criteria

1. WHEN 表达式编译完成 THEN 系统 SHALL 调用 BatchPlannerRegistry.getPlanner(provenanceCode) 获取对应数据源的批次规划器
2. WHEN 使用 PubMed 批次规划器 THEN 系统 SHALL 调用 ESearch API 获取总记录数和 WebEnv
3. WHEN 使用 EPMC 批次规划器 THEN 系统 SHALL 调用初始 Search API 获取第一页数据和 cursor token
4. WHEN 计算批次数 THEN 系统 SHALL 使用公式：批次数 = min(总量 ÷ 每批大小, maxBatchesPerExecution)
5. WHEN 生成批次记录 THEN 系统 SHALL 为每批生成唯一的幂等键（如 SHA256(runId + ":" + batchNo)）
6. WHEN 生成批次记录 THEN 系统 SHALL 设置批次状态为 PENDING、记录批次参数（起始位置、游标 token、时间范围等）
7. WHEN 批量插入批次记录 THEN 系统 SHALL 在一个事务内插入所有批次到 ing_task_run_batch 表
8. IF 批次规划失败（API 调用失败、网络超时） THEN 系统 SHALL 抛出 BatchPlanningException 并标记任务为 FAILED
9. WHEN 批次规划完成 THEN 系统 SHALL 记录 INFO 级别日志说明生成的批次数和总记录数

### Requirement 7: 批次执行与数据采集

**User Story:** 作为任务执行引擎，我希望按顺序或并发执行每个批次，调用数据源 API 采集数据，解析响应并上传至对象存储，以完成实际的数据采集工作。

#### Acceptance Criteria

1. WHEN 批次规划完成 THEN 系统 SHALL 按批次号顺序或并发执行每个 PENDING 状态的批次
2. WHEN 执行批次前 THEN 系统 SHALL 检查批次幂等键，IF 批次状态 = SUCCEEDED THEN 跳过该批次
3. WHEN 开始执行批次 THEN 系统 SHALL 更新批次状态为 RUNNING
4. WHEN 调用 PubMed API THEN 系统 SHALL 使用 EFetch API + WebEnv + retstart/retmax 参数
5. WHEN 调用 EPMC API THEN 系统 SHALL 使用 Search API + cursor token 参数
6. WHEN 收到 API 响应 THEN 系统 SHALL 解析 XML/JSON 响应为标准化的领域对象（如 Article 实体）
7. WHEN 解析完成 THEN 系统 SHALL 使用 gzip 压缩原始数据
8. WHEN 压缩完成 THEN 系统 SHALL 上传至对象存储，路径格式为 {bucket}/{provenanceCode}/{year}/{month}/{runId}/batch_{batchNo}.json.gz
9. WHEN 上传成功 THEN 系统 SHALL 在同一事务内：插入 Outbox 消息、更新批次状态为 SUCCEEDED、记录统计信息（记录数、文件大小、最大时间戳等）
10. IF API 调用失败 THEN 系统 SHALL 标记批次为 FAILED、记录错误信息、继续执行下一批次
11. IF 对象存储上传失败 THEN 系统 SHALL 重试 3 次（指数退避），仍失败则标记批次为 FAILED
12. IF 数据库异常 THEN 系统 SHALL 抛出异常，任务整体失败，等待 MQ 重试

### Requirement 8: 游标推进与水位线更新

**User Story:** 作为任务执行引擎，我希望在所有批次执行完成后推进游标记录采集进度，以支持下次增量采集从正确的位置继续，避免漏数据或重复采集。

#### Acceptance Criteria

1. WHEN 所有批次执行完成 THEN 系统 SHALL 根据 operation_type 判断是否需要推进游标
2. IF operation_type = HARVEST 且游标类型 = 时间型 THEN 系统 SHALL 从所有 SUCCEEDED 批次的 stats 中提取最大时间戳
3. IF operation_type = HARVEST 且游标类型 = ID 型 THEN 系统 SHALL 从所有 SUCCEEDED 批次的 stats 中提取最大记录 ID
4. IF operation_type = BACKFILL THEN 系统 SHALL 记录已完成的时间窗口范围
5. IF operation_type = UPDATE THEN 系统 SHALL 根据业务规则决定是否推进游标
6. WHEN 计算新游标值 THEN 系统 SHALL 聚合所有 SUCCEEDED 批次的统计信息计算全局最大值
7. WHEN 更新游标表 THEN 系统 SHALL 使用乐观锁（version 字段）更新 ing_cursor 表
8. WHEN 更新游标表 THEN 系统 SHALL 记录推进后的值和规范化时间（normalized_instant）
9. WHEN 游标更新成功 THEN 系统 SHALL 插入游标事件到 ing_cursor_event 表，记录推进历史（从哪到哪、操作类型、关联的 run_id）
10. WHEN 插入游标事件 THEN 系统 SHALL 使用幂等键防止重复推进
11. IF 游标更新失败（乐观锁冲突） THEN 系统 SHALL 重试最多 3 次
12. IF 游标更新仍失败 THEN 系统 SHALL 记录 ERROR 日志但不影响任务状态（游标推进失败不应导致任务失败）

### Requirement 9: 执行状态更新与任务收尾

**User Story:** 作为任务执行引擎，我希望在任务执行完成后聚合统计信息、判断最终状态并更新数据库，以完成任务收尾并释放资源。

#### Acceptance Criteria

1. WHEN 所有批次执行完成且游标推进完成 THEN 系统 SHALL 聚合统计信息：总批次数、成功批次数、失败批次数、总采集记录数、总文件大小、执行耗时
2. WHEN 判断最终状态 THEN 系统 SHALL 使用规则：IF 全部批次成功 THEN SUCCEEDED；IF 有失败批次 THEN FAILED；IF 部分成功 THEN PARTIAL
3. WHEN 更新执行记录 THEN 系统 SHALL 在一个事务内更新 ing_task_run 表：状态、统计信息（stats JSON）、完成时间、错误信息（如果失败）
4. WHEN 更新任务记录 THEN 系统 SHALL 在同一事务内更新 ing_task 表：状态、完成时间、清空租约（lease_owner = NULL, leased_until = NULL）
5. IF 任务状态 = FAILED THEN 系统 SHALL 更新重试计数和错误码
6. WHEN 数据库更新完成 THEN 系统 SHALL 停止心跳定时任务并释放资源
7. WHEN 任务收尾完成 THEN 系统 SHALL 记录 INFO 级别日志说明任务执行结果和统计信息
8. IF 状态更新失败（数据库异常） THEN 系统 SHALL 抛出异常并触发 MQ 重试

### Requirement 10: 多层幂等保障

**User Story:** 作为任务执行引擎，我希望在任务级别、批次级别、游标级别和消息级别都实现幂等保障，以确保任务可重入、可重试，避免数据重复或丢失。

#### Acceptance Criteria

1. WHEN 任务级别幂等检查 THEN 系统 SHALL 使用 idempotentKey 唯一约束，已成功的任务不会重复执行
2. WHEN 批次级别幂等检查 THEN 系统 SHALL 为每批生成独立的幂等键，已成功的批次自动跳过
3. WHEN 游标级别幂等检查 THEN 系统 SHALL 为游标事件生成幂等键，防止重复推进水位线
4. WHEN 消息级别幂等检查 THEN 系统 SHALL 使用 Outbox 的 (channel, dedup_key) 唯一约束，防止重复发送
5. WHEN 任务重试 THEN 系统 SHALL 能从上次中断的地方继续，不重复执行已成功的部分
6. WHEN 批次重试 THEN 系统 SHALL 只重试 PENDING 或 FAILED 状态的批次
7. WHEN 游标推进重试 THEN 系统 SHALL 使用幂等键防止重复插入游标事件

### Requirement 11: 部分失败容忍与重试机制

**User Story:** 作为任务执行引擎，我希望部分批次失败不影响其他批次的执行，并支持多层重试机制，以提高任务执行的成功率和容错能力。

#### Acceptance Criteria

1. WHEN 某批次执行失败 THEN 系统 SHALL 标记该批次为 FAILED 但继续执行其他批次
2. WHEN 判断任务最终状态 THEN 系统 SHALL 根据成功率判断：全部成功 → SUCCEEDED、有失败 → FAILED、部分成功 → PARTIAL
3. WHEN API 调用失败 THEN 系统 SHALL 根据 retry_config 配置的重试策略进行重试（重试次数、退避策略）
4. WHEN 对象存储上传失败 THEN 系统 SHALL 重试 3 次，使用指数退避策略（1s、2s、4s）
5. WHEN MQ 消费失败 THEN 系统 SHALL 触发 binder 重试机制
6. WHEN 任务状态 = FAILED THEN 系统 SHALL 支持 MQ 层重试或手动重试
7. WHEN 任务状态 = PARTIAL THEN 系统 SHALL 记录部分成功信息，失败部分需人工介入或重试

### Requirement 12: 可观测性与追踪链路

**User Story:** 作为运维人员，我希望任务执行过程有完整的追踪链路和详细的执行日志，以便快速定位问题和分析性能瓶颈。

#### Acceptance Criteria

1. WHEN 任务执行的每个步骤 THEN 系统 SHALL 记录日志，包含 taskId、runId、batchNo、owner、traceId 等关键信息
2. WHEN 记录日志 THEN 系统 SHALL 使用统一的日志前缀格式：[INGEST][LAYER] message
3. WHEN 任务开始执行 THEN 系统 SHALL 生成或传递 correlationId 用于消息级别追踪
4. WHEN 调用外部 API THEN 系统 SHALL 传递 traceId 用于分布式调用链追踪
5. WHEN 批次执行完成 THEN 系统 SHALL 记录统计信息到 stats JSON 字段：执行耗时、记录数、文件大小、最大时间戳等
6. WHEN 任务执行完成 THEN 系统 SHALL 记录 INFO 级别日志说明任务执行结果和聚合统计信息
7. WHEN 发生异常 THEN 系统 SHALL 记录 ERROR 级别日志，包含异常堆栈和上下文信息
8. WHEN 心跳续租 THEN 系统 SHALL 记录 DEBUG 级别日志说明续租结果
9. WHEN 租约抢占失败 THEN 系统 SHALL 记录 INFO 级别日志说明其他节点正在执行
10. WHEN 批次跳过（幂等） THEN 系统 SHALL 记录 DEBUG 级别日志说明跳过原因
