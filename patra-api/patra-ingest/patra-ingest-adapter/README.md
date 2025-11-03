# patra-ingest-adapter — 摄入适配器层

> **驱动适配器(Driving Adapters)**,接收外部触发并委托给应用层编排器。

---

## 概述

`patra-ingest-adapter` 是 **patra-ingest 服务的适配器层(Adapter Layer)**,在六边形架构中位于**外围层**,负责**接收外部触发(Inbound)**。

**架构契约**:
- **方向**: 外部世界 → 系统
- **职责**: 接收外部请求、验证输入、委托给应用编排器
- **禁止**: 直接调用外部资源(数据库、外部 API、MQ 发布者) — 这些属于 `patra-ingest-infra`

**模块分离原则**:
```
patra-ingest-adapter/     ← 驱动适配器(入站,接收外部触发)
patra-ingest-infra/       ← 被驱动适配器(出站,访问外部资源)
```

---

## 核心职责

- **定时任务触发**: 通过 XXL-Job 接收 Cron 触发,执行 Plan 摄入和 Outbox 中继
- **MQ 消息消费**: 通过 RocketMQ 消费任务就绪消息,触发任务执行
- **输入验证**: 验证外部输入的合法性
- **协议适配**: 将外部协议(XXL-Job 参数、MQ 消息)转换为应用层命令对象

---

## 模块结构

```
patra-ingest-adapter/
└─ src/main/java/.../adapter/
   ├─ scheduler/                    # XXL-Job 定时任务
   │  ├─ config/
   │  │  └─ XxlJobConfig.java           # XXL-Job 执行器配置
   │  ├─ job/
   │  │  ├─ AbstractProvenanceScheduleJob.java  # 抽象任务基类
   │  │  ├─ PubmedHarvestJob.java               # PubMed 采集任务
   │  │  └─ OutboxRelayJob.java                 # Outbox 中继任务
   │  └─ param/
   │     ├─ ProvenanceScheduleJobParam.java     # 采集任务参数 DTO
   │     └─ OutboxRelayJobParam.java            # 中继任务参数 DTO
   └─ rocketmq/                     # RocketMQ 消费者
      ├─ TaskReadyMessageListener.java     # 消息消费者
      └─ dto/
         └─ TaskReadyPayload.java          # 任务就绪消息负载
```

---

## 主要组件

### 1. XXL-Job 定时任务

#### AbstractProvenanceScheduleJob (抽象任务基类)

**职责**: 提供 Provenance 采集任务的通用逻辑(参数解析、编排器调用、错误处理)。

**核心方法**:
```java
@Slf4j
public abstract class AbstractProvenanceScheduleJob extends IJobHandler {

    @Override
    public void execute() throws Exception {
        // 1. 解析任务参数
        String param = XxlJobHelper.getJobParam();
        ProvenanceScheduleJobParam jobParam = parseJobParam(param);

        // 2. 构建 Plan 摄入命令
        PlanIngestionCommand command = buildCommand(jobParam);

        // 3. 调用编排器
        PlanIngestionResult result = planIngestionOrchestrator.ingestPlan(command);

        // 4. 记录结果
        log.info("Plan ingestion completed: planId={}, taskCount={}",
            result.getPlanId(), result.getTaskCount());

        XxlJobHelper.handleSuccess("Plan ingestion success");
    }

    protected abstract ProvenanceScheduleJobParam parseJobParam(String param);
    protected abstract PlanIngestionCommand buildCommand(ProvenanceScheduleJobParam param);
}
```

**文件**: `scheduler/job/AbstractProvenanceScheduleJob.java`

#### PubmedHarvestJob (PubMed 采集任务)

**职责**: 触发 PubMed 数据采集的定时任务。

**任务参数示例**:
```json
{
  "provenanceCode": "pubmed",
  "operationCode": "HARVEST",
  "windowFrom": "2025-01-01T00:00:00Z",
  "windowTo": "2025-01-10T00:00:00Z",
  "sliceStrategyCode": "TIME"
}
```

**XXL-Job 配置**:
- **任务名称**: PubMed 增量采集
- **Cron**: `0 0 2 * * ?` (每天凌晨 2 点)
- **路由策略**: 轮询
- **阻塞处理策略**: 单机串行

**文件**: `scheduler/job/PubmedHarvestJob.java`

#### OutboxRelayJob (Outbox 中继任务)

**职责**: 定期轮询 Outbox 表,将领域事件发布到 MQ。

**任务参数示例**:
```json
{
  "batchSize": 100,
  "maxRetries": 3
}
```

**XXL-Job 配置**:
- **任务名称**: Outbox 消息中继
- **Cron**: `0/10 * * * * ?` (每 10 秒执行一次)
- **路由策略**: 轮询
- **阻塞处理策略**: 丢弃后续调度

**文件**: `scheduler/job/OutboxRelayJob.java`

### 2. RocketMQ 消息消费者

#### TaskReadyMessageListener (消息消费者)

**职责**: 消费任务就绪消息,触发任务执行。

**消费配置**:
- **Topic**: `${papertrace.ingest.mq.topics.task-ready}` (配置中为 `INGEST_TASK_READY`)
- **ConsumerGroup**: `${papertrace.ingest.mq.consumer-groups.task-ready}`

**核心方法**:
```java
@Component
@Slf4j
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = "${papertrace.ingest.mq.topics.task-ready}",
    consumerGroup = "${papertrace.ingest.mq.consumer-groups.task-ready}",
    selectorExpression = "*"
)
public class TaskReadyMessageListener implements RocketMQListener<MessageExt> {

    private final TaskExecutionUseCase taskExecutionUseCase;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MessageExt message) {
        try {
            // 解析消息体
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            TaskReadyPayload dto = objectMapper.readValue(payload, TaskReadyPayload.class);

            Long taskId = dto.getTaskId();
            log.info("Received task ready message: taskId={}, msgId={}", taskId, message.getMsgId());

            // 调用任务执行用例
            taskExecutionUseCase.executeTask(taskId);

            log.info("Task execution completed: taskId={}", taskId);
        } catch (Exception ex) {
            log.error("Task execution failed: msgId={}", message.getMsgId(), ex);
            throw new RuntimeException("消息消费失败", ex);  // 触发 RocketMQ 重试
        }
    }
}
```

**消息格式**:
```json
{
  "taskId": 12345,
  "idempotentKey": "task_pubmed_harvest_20250101_batch1",
  "provenanceCode": "pubmed",
  "operationCode": "HARVEST",
  "priority": 10
}
```

**文件**: `rocketmq/TaskReadyMessageListener.java`

---

## 设计模式

### 1. 模板方法模式 (Template Method Pattern)

`AbstractProvenanceScheduleJob` 定义任务执行的通用流程,子类实现具体细节:
```java
public abstract class AbstractProvenanceScheduleJob {
    // 模板方法
    public void execute() {
        // 1. 解析参数(抽象方法)
        // 2. 构建命令(抽象方法)
        // 3. 调用编排器(通用逻辑)
        // 4. 处理结果(通用逻辑)
    }

    protected abstract ProvenanceScheduleJobParam parseJobParam(String param);
    protected abstract PlanIngestionCommand buildCommand(ProvenanceScheduleJobParam param);
}
```

### 2. 适配器模式 (Adapter Pattern)

将外部协议(XXL-Job、RocketMQ)适配为应用层命令对象:
```
XXL-Job 参数(JSON) → ProvenanceScheduleJobParam → PlanIngestionCommand
RocketMQ 消息(JSON) → TaskReadyPayload → executeTask(taskId)
```

---

## 配置说明

### XXL-Job 配置

```yaml
xxl:
  job:
    admin:
      addresses: http://localhost:8080/xxl-job-admin
    executor:
      appname: patra-ingest-executor
      port: 9999
      logpath: /data/applogs/xxl-job/jobhandler
      logretentiondays: 30
    accessToken: default_token
```

### RocketMQ 消费者配置

```yaml
rocketmq:
  name-server: ${ROCKETMQ_NAMESRV:127.0.0.1:9876}
  producer:
    group: ingest-producer-group
    send-message-timeout: 3000

papertrace:
  ingest:
    mq:
      topics:
        task-ready: ${TOPIC_PREFIX:}INGEST_TASK_READY
      consumer-groups:
        task-ready: ${CONSUMER_GROUP_PREFIX:}ingest-task-ready-consumer-group
```

---

## 使用示例

### 示例 1: 手动触发 PubMed 采集任务

在 XXL-Job 控制台点击 "执行一次":

**任务参数**:
```json
{
  "provenanceCode": "pubmed",
  "operationCode": "HARVEST",
  "windowFrom": "2025-01-01T00:00:00Z",
  "windowTo": "2025-01-10T00:00:00Z",
  "sliceStrategyCode": "TIME"
}
```

**执行日志**:
```
[INFO] Plan ingestion started: provenanceCode=pubmed, operationCode=HARVEST
[INFO] Window resolved: [2025-01-01T00:00:00Z, 2025-01-10T00:00:00Z)
[INFO] Plan assembled: planId=123, sliceCount=10, taskCount=100
[INFO] Tasks published to Outbox: messageCount=100
[INFO] Plan ingestion completed: planId=123, taskCount=100
```

### 示例 2: 消费任务就绪消息

**RocketMQ 消息**:
```json
{
  "taskId": 12345,
  "idempotentKey": "task_pubmed_harvest_20250101_batch1",
  "provenanceCode": "pubmed",
  "operationCode": "HARVEST"
}
```

**消费日志**:
```
[INFO] Received task ready message: taskId=12345
[INFO] Task execution started: taskId=12345, runId=67890
[INFO] Batch 1/10 completed: recordCount=1000
[INFO] Batch 2/10 completed: recordCount=1000
...
[INFO] Task execution completed: taskId=12345, totalRecords=10000
```

---

## 依赖关系

### 上游依赖
- `patra-ingest-app`: 应用层(调用编排器)
- `patra-ingest-api`: API 契约
- `patra-spring-boot-starter-web`: Web Starter
- `xxl-job-core`: XXL-Job 核心库
- `rocketmq-spring-boot-starter`: RocketMQ 官方 Spring Boot Starter

### 下游消费者
- `patra-ingest-boot`: 启动模块(组装所有依赖)

**依赖方向**: Adapter → App → Domain (符合六边形架构)

---

## 命名约定

- **定时任务**: `*Job` (如 `PubmedHarvestJob`)
- **消费者**: `*MessageListener` (如 `TaskReadyMessageListener`)
- **参数 DTO**: `*Param` (如 `ProvenanceScheduleJobParam`)
- **消息负载**: `*Payload` (如 `TaskReadyPayload`)

---

## 技术栈

- **Java**: 25
- **Spring Boot**: 3.5.7
- **XXL-Job**: 2.4.x (分布式任务调度)
- **RocketMQ Spring Boot Starter**: 消息消费
- **MapStruct**: 对象转换

---

**最后更新**: 2025-01-16
**Maven 坐标**: `com.papertrace:patra-ingest-adapter:0.1.0-SNAPSHOT`
**作者**: linqibin
