# Ingest 任务消费与租约实现说明

作者：linqibin｜完成时间：2025-01-03｜版本：v0.1

## 1. 概述

本次实现完成了 `patra-ingest` 服务的"任务消费与租约设计"，实现了从 MQ 消费 `INGEST_TASK_READY` 消息后的：
- **步骤 0**：CAS 抢占租约（避免双跑）
- **步骤 1**：初始化执行会话（置 RUNNING + 创建 TaskRun + 安排心跳）

## 2. 核心设计

### 2.1 分层职责

#### Domain 层（patra-ingest-domain）
- **扩展仓储接口**：`TaskRepository` 和 `TaskRunRepository` 新增租约相关方法
  - `tryAcquireLease()`: CAS 抢占租约
  - `markRunningWithLease()`: 置任务为 RUNNING
  - `renewLease()`: 心跳续租
  - `getLatestAttemptNo()`: 获取最新尝试编号

#### App 层（patra-ingest-app）
- **用例接口**：`TaskExecutionUseCase` - 定义任务执行入口
- **用例实现**：`TaskExecutionOrchestrator` - 编排租约抢占与会话初始化
- **命令对象**：`TaskReadyCommand` - 封装 MQ 消息解析后的数据
- **配置类**：`TaskExecutionConfig` - 提供心跳调度器 Bean

#### Infra 层（patra-ingest-infra）
- **Mapper 扩展**：`TaskMapper` 和 `TaskRunMapper` 新增租约相关方法签名
- **Mapper XML**：实现复杂 SQL（CAS、续租、置 RUNNING）
  - `TaskMapper.xml`: 租约抢占、置 RUNNING、续租 SQL
  - `TaskRunMapper.xml`: 查询最新 attemptNo SQL
- **仓储实现**：`TaskRepositoryMpImpl` 和 `TaskRunRepositoryMpImpl` 实现新接口

#### Adapter 层（patra-ingest-adapter）
- **消费者配置**：`IngestStreamConsumers` - 从"打印日志"升级为"调用用例"
  - 解析 MQ 消息 payload 与 headers
  - 使用 POJO（`TaskReadyPayload`）解析 JSON，确保类型安全
  - 组装 `TaskReadyCommand`
  - 调用 `TaskExecutionUseCase.startFromReady()`
- **Payload DTO**：`TaskReadyPayload` - MQ 消息体 POJO
  - 使用 Jackson 注解映射 JSON 字段
  - 内置 `validate()` 方法校验必需字段

### 2.2 关键流程

#### 步骤 0：CAS 抢占租约（≤1s）
```sql
UPDATE ing_task
   SET lease_owner = #{owner},
       leased_until = DATE_ADD(#{now}, INTERVAL #{ttlSec} SECOND),
       lease_count = lease_count + 1
 WHERE id = #{taskId}
   AND status_code = 'QUEUED'
   AND idempotent_key = #{idem}
   AND (scheduled_at IS NULL OR scheduled_at <= #{now})
   AND (leased_until IS NULL OR leased_until <= #{now} OR lease_owner = #{owner})
```

**返回值**：
- `affectedRows=1` → 抢占成功，进入步骤 1
- `affectedRows=0` → 他人持有或条件不满足，优雅退出（不抛异常）

#### 步骤 1：初始化执行会话（事务内完成）
1. 置任务为 RUNNING 状态并更新租约
2. 获取下一个 attemptNo（`latestAttemptNo + 1`）
3. 创建 TaskRun 记录（status=RUNNING）
4. 安排心跳续租（定时任务，周期 = `heartbeatIntervalSeconds`）

#### 心跳续租
- 定时任务以 `heartbeatIntervalSeconds` 为周期调用 `renewLease()`
- 若续租失败（`affectedRows=0`），则租约已丢失，终止心跳
- 日志记录续租成功/失败状态

### 2.3 幂等保障

1. **SUCCEEDED 闸门**：任务已成功且 `idempotentKey` 匹配 → 直接跳过
2. **CAS 竞争**：租约抢占失败 → 优雅退出（记录 info 日志）
3. **数据库异常**：抛出异常 → 触发 MQ binder 本地重试（最多 3 次 + 指数退避）

## 3. 配置项

在 `application.yaml` 中新增：

```yaml
papertrace:
  ingest:
    exec:
      # 租约 TTL（秒），默认 60 秒
      lease-ttl-seconds: ${INGEST_LEASE_TTL:60}
      # 心跳间隔（秒），默认为 TTL 的 1/3
      heartbeat-interval-seconds: ${INGEST_HEARTBEAT_INTERVAL:20}
```

## 4. 关键文件清单

### 新增文件
- `patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/execution/TaskExecutionUseCase.java`
- `patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/execution/TaskExecutionOrchestrator.java`
- `patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/execution/command/TaskReadyCommand.java`
- `patra-ingest-app/src/main/java/com/patra/ingest/app/config/TaskExecutionConfig.java`
- `patra-ingest-adapter/src/main/java/com/patra/ingest/adapter/inbound/stream/dto/TaskReadyPayload.java`
- `patra-ingest-infra/src/main/resources/mapper/TaskMapper.xml`
- `patra-ingest-infra/src/main/resources/mapper/TaskRunMapper.xml`

### 修改文件
- `patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/TaskRepository.java` - 扩展租约方法
- `patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/TaskRunRepository.java` - 扩展 getLatestAttemptNo
- `patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/mapper/TaskMapper.java` - 扩展方法签名
- `patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/mapper/TaskRunMapper.java` - 扩展方法签名
- `patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/repository/TaskRepositoryMpImpl.java` - 实现租约方法
- `patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/repository/TaskRunRepositoryMpImpl.java` - 实现 getLatestAttemptNo
- `patra-ingest-adapter/src/main/java/com/patra/ingest/adapter/inbound/stream/IngestStreamConsumers.java` - 升级为调用用例
- `patra-ingest-boot/src/main/resources/application.yaml` - 新增执行配置

## 5. 观测与日志

### 日志关键字段
- `taskId`: 任务 ID
- `idemKey` / `idempotentKey`: 幂等键
- `owner`: 租约持有者（格式：`appName@hostname#pid:execId`）
- `attemptNo`: 尝试编号
- `msgId` / `ROCKET_MQ_MESSAGE_ID`: MQ 消息 ID

### 日志示例
```
[INGEST][APP] task execution start taskId=123 idemKey=pubmed:harvest:xxx msgId=yyy
[INGEST][INFRA] task lease acquired taskId=123 owner=patra-ingest@host#12345:abc123
[INGEST][APP] task run created taskId=123 attemptNo=1 status=RUNNING
[INGEST][APP] task execution session initialized taskId=123 owner=patra-ingest@host#12345:abc123
[INGEST][INFRA] task lease renewed taskId=123 owner=patra-ingest@host#12345:abc123
```

### 建议指标（待实现）
- `ingest.task.lease.acquire.count{result=success|miss}` - 租约抢占统计
- `ingest.task.session.start.count` - 会话初始化计数
- `ingest.task.heartbeat.renew.count{result=ok|lost}` - 心跳续租统计
- `ingest.task.run.latency.start.ms` - 消费至 RUNNING 的耗时

## 6. 验收清单

### 功能验收
- [ ] 同一消息多实例竞争：仅 1 实例 CAS 成功，其他实例优雅退出
- [ ] RUNNING 初始化：`ing_task` 和 `ing_task_run` 正确落库，attemptNo 递增
- [ ] 心跳续租：定时续租成功；模拟 owner 变更后心跳终止
- [ ] SUCCEEDED 幂等：相同 `idempotentKey` 再次投递被跳过
- [ ] 重试：DB 故障/解析异常触发 MQ 本地重试（最多 3 次 + 指数退避）

### 代码质量
- [x] 遵循六边形架构：adapter → app → domain ← infra
- [x] 应用层不引入框架依赖（仅 Spring 注解）
- [x] 复杂 SQL 在 XML 中实现
- [x] 完整 JavaDoc 注释
- [x] 日志输出清晰（taskId/owner/attemptNo 等关键字段）
- [x] 编译通过，无语法错误

## 7. 后续优化建议

1. **心跳调度优化**：当前为每个任务创建独立定时任务，可考虑改为"统一调度器 + 任务注册表"模式
2. **租约丢失处理**：当前仅记录 WARN 日志，建议发布领域事件触发任务恢复逻辑
3. **指标埋点**：补充 Micrometer 指标，便于监控与告警
4. **单元测试**：补充 CAS 竞争、会话初始化、心跳续租的单元/集成测试
5. **可配置策略**：支持不同来源/操作的租约 TTL 与心跳间隔差异化配置

## 8. 设计差异说明

与原设计文档（`ingest-consume-lease-design.md`）的主要差异：

1. **心跳调度器**：文档中未明确说明实现方式，本次使用 `ScheduledExecutorService` 实现
2. **workerId 格式**：文档建议 `appName@hostname#pid`，本次实现为 `appName@hostname#pid:execId`（增加 execId 保证唯一性）
3. **TaskRun 初始状态**：文档中未明确，本次使用 `TaskRunStatus.PLANNED` 并在 `start()` 时置为 `RUNNING`
4. **错误处理**：文档中提到"FAILED 可重试的纳入条件"待定，本次实现暂不支持 FAILED 任务重新抢占

## 9. 参考文档

- `docs/modules/ingest/ingest-consume-lease-design.md` - 任务消费与租约设计
- `docs/process/ingest-business-flow.md` - Ingest 业务流程说明
- `docs/process/ingest-dataflow.md` - Ingest 数据流向
- `AGENTS.md` - 智能体工作手册（架构约束与开发规范）
