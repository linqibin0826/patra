# Ingest 任务消费与租约设计（流程 C · 步骤 0/1）

作者：linqibin（建议）｜适用：patra-ingest 服务｜版本：v0.1 草案

## 1. 背景与目标
- 背景：当前入站消费者仅用于链路验证（打印 headers/payload），未承载“任务抢占与会话初始化”。
- 目标：在 RocketMQ 消费到 `INGEST_TASK_READY` 后，1 秒内完成：
  1) CAS 抢占租约（避免双跑）；
  2) 初始化本次执行会话：任务置为 RUNNING、创建 `ing_task_run` 尝试记录、计划心跳续租。
- 设计约束：
  - 六边形架构：adapter 只做解析与调用；应用层（app）编排；复杂 SQL 在 infra 的 XML Mapper 中实现。
  - 幂等：以 `idempotent_key` 为幂等边界；已 SUCCEEDED 且窗口一致直接跳过。
  - 可观测：记录 lease 竞争、会话创建与心跳续租日志与指标。

## 2. 契约与依赖（摘）
- Topic：`INGEST_TASK_READY`（UPPER_SNAKE）。入站绑定见：`patra-ingest-boot/src/main/resources/ingest-mq-config.yaml:23`。
- 消息负载（来自 Outbox）：`taskId/planId/sliceId/provenance/operation/idempotentKey/priority/scheduledAt/planWindowFrom/To/...`
  - 构造处：`patra-ingest-app/.../TaskOutboxPublisher.java`（payload/headers 生成）。
- RocketMQ 头：`ROCKET_KEYS`(dedup)、`ROCKET_TAGS`(opType)、`ROCKET_MQ_TOPIC`、`partitionKey`（发布端已注入）。
- 表结构：
  - `ing_task`：租约与运行态字段齐备（`lease_owner`/`leased_until`/`lease_count`/`last_heartbeat_at`/`status_code`/`started_at` 等），见迁移脚本：
    - `patra-ingest-infra/.../V0.1.0__init_ingest_schema.sql:174`（表头）、`:192-202,231`（关键字段与队列索引）
  - `ing_task_run`：attempt 运行摘要，见 `:251,255,260,268,270`。

## 3. 分层与时序（简）
- adapter：解析 MQ → 组装 `TaskReadyCommand` → 调用 app 用例。
- app：`TaskExecutionUseCase.startFromReady(cmd)`：步骤 0（CAS）→ 步骤 1（会话初始化）→ 安排心跳。
- infra：在 `TaskMapper.xml`/`TaskRunMapper.xml` 实现 CAS/续租/置 RUNNING/查询 attemptNo 等复杂 SQL。

时序（概念）：
```
MQ -> Adapter -> UseCase
           (0) tryAcquireLease [<=1s]
           (1) tx{ markRunningWithLease + insert TaskRun + schedule heartbeat }
ACK
```
## 4. 步骤 0：消费消息 → CAS 抢占租约
- 输入解析（payload）：
  - 必要：`taskId`、`idempotentKey`、`provenance`、`operation`
  - 可选：`priority`、`scheduledAt`、`planWindowFrom/To`、`planSliceStrategy/Params`
- headers（用于追踪）：`ROCKET_MQ_MESSAGE_ID`、`id`、`ROCKET_MQ_TOPIC`、`ROCKET_KEYS`、`ROCKET_TAGS`、`partitionKey`、`triggeredAt/occurredAt/scheduler/...`
- execId/workerId：
  - `workerId = <appName>@<hostname>#<pid>`（建议）；`execId = UUID`；
  - `lease_owner = workerId:execId`（或仅 `execId` 亦可，要求唯一且可定位）。
- CAS 规则（MySQL/InnoDB）：
  - 仅针对 `QUEUED`（可扩展至“可重试 FAILED”）且“可接管”任务：`leased_until IS NULL or leased_until<=now OR lease_owner=me`；
  - `scheduled_at` 为 NULL 或 ≤ now（尊重 notBefore）。

推荐 SQL（放 XML，见附录 A.1）：
```
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
- 结果分支：
  - `affectedRows=1` → 抢占成功，进入步骤 1；
  - `affectedRows=0` → 视为他人持有/已处理，直接 ACK；仅记录 info（lease_miss）。
- 时限：解析+一次 UPDATE，强约束 ≤1s。

## 5. 步骤 1：初始化会话（本次执行边界）
- 幂等等闸门（初始化前）：若 `ing_task.status_code = SUCCEEDED` 且 `idempotent_key == payload.idempotentKey` → 直接 ACK（跳过）。
- 事务操作（同一事务内）：
  1) 置 RUNNING + 补起始信息（WHERE `lease_owner=me` 防止被窃取）；
  2) 创建 `ing_task_run` 记录（新 attempt）；
  3) 安排心跳（定时续租），`period = ttl/3`。
- 置 RUNNING SQL（放 XML，见附录 A.2）：
```
UPDATE ing_task
   SET status_code = 'RUNNING',
       started_at = #{now},
       last_heartbeat_at = #{now},
       leased_until = DATE_ADD(#{now}, INTERVAL #{ttlSec} SECOND)
 WHERE id = #{taskId}
   AND lease_owner = #{owner}
```
- attemptNo 获取（两种策略）：
  - 简：`SELECT IFNULL(MAX(attempt_no),0) FROM ing_task_run WHERE task_id=#{taskId}`（见附录 A.3），+1 即最新 attempt；
  - 或沿用当前 `findLatest()`（按 `attempt_no` DESC LIMIT 1）。
- 新建 run 记录（可用 MyBatis-Plus 默认 insert；或 XML insert，见附录 A.4）：
  - `status_code=RUNNING`、`started_at=now`、`last_heartbeat=now`、`window_from/to` 取自 payload。
- 心跳续租（见附录 A.5）：
```
UPDATE ing_task
   SET leased_until = DATE_ADD(#{now}, INTERVAL #{ttlSec} SECOND),
       last_heartbeat_at = #{now},
       lease_count = lease_count + 1
 WHERE id = #{taskId}
   AND lease_owner = #{owner}
```
- 失败处理：
  - 任一落库失败 → 抛异常 → 交由 binder 本地重试（`max-attempts/backoff-*` 已配置）。
  - `lease_owner` 不匹配 → 视为租约丢失（并发竞争），直接 ACK（记录 warn），不重试。
## 6. 接口与适配改造
- 新用例（app）：
```java
public interface TaskExecutionUseCase {
    void startFromReady(TaskReadyCommand cmd);
}

public record TaskReadyCommand(
    long taskId,
    String idempotentKey,
    String provenance,
    String operation,
    Integer priority,
    Instant scheduledAt,
    Instant planWindowFrom,
    Instant planWindowTo,
    Map<String, Object> headers
) {}
```
- 适配层（adapter）：`IngestStreamConsumers` 从“打印”升级为“调用用例”。示例：
```java
@Bean
public Consumer<Message<String>> ingestTaskReadyConsumer(TaskExecutionUseCase useCase, ObjectMapper om) {
    return msg -> {
        TaskReadyCommand cmd = mapToCommand(om, msg); // 解析 payload/headers
        useCase.startFromReady(cmd);
    };
}
```
- 仓储扩展（infra）：`TaskRepository` 新增（建议）
```java
boolean tryAcquireLease(Long taskId, String owner, Instant now, int ttlSec, String idemKey);
boolean markRunningWithLease(Long taskId, String owner, Instant now, int ttlSec);
boolean renewLease(Long taskId, String owner, Instant now, int ttlSec);
```
对应 `TaskMapper.xml` 提供同名 SQL；`TaskRunRepository` 复用现有 save/findLatest，或在 XML 增加 `selectLatestAttemptNo`。
## 7. 错误处理与 ACK 策略
- SUCCEEDED 命中（幂等等闸门）→ 直接 ACK，避免重复执行。
- CAS 未命中（被他人持有/刚接管）→ 直接 ACK（正常竞争），打印 info：lease_miss。
- 解析/落库异常 → 抛异常，触发 binder 重试（本地重试 3 次 + 指数退避，见 `ingest-mq-config.yaml`）。
- 心跳续租失败（更新=0）→ 终止心跳，WARN 日志，交由外部恢复任务处理。

## 8. 观测与指标（建议）
- 日志字段：taskId、planId、sliceId、idempotentKey、leaseOwner(execId)、attemptNo、ROCKET_MQ_MESSAGE_ID、retryCount。
- 指标：
  - `ingest.task.lease.acquire.count{result=success|miss}`
  - `ingest.task.session.start.count`、`ingest.task.heartbeat.renew.count{result=ok|lost}`
  - `ingest.task.run.latency.start.ms`（消费至 RUNNING 的耗时）

## 9. 验收清单（QA）
- 同一消息多实例竞争：仅 1 实例 CAS 成功，其他实例 ACK 且无副作用。
- RUNNING 初始化：`ing_task`/`ing_task_run` 正确落库，attemptNo 递增。
- 心跳续租：定时续租成功；模拟 owner 变更后心跳终止。
- SUCCEEDED 幂等：相同 `idempotentKey` 再次投递被跳过（直接 ACK）。
- 重试：DB 故障/解析异常触发本地重试并按退避。

## 10. 落地步骤
1) 新增应用用例接口与实现（app）：`TaskExecutionUseCase` + `TaskExecutionOrchestrator`。
2) 扩展仓储接口与实现（infra）：`TaskRepository`/`TaskMapper.xml`、可选 `TaskRunMapper.xml`。
3) 改造适配层消费者（adapter）：从日志打印改为调用用例。
4) 增加执行期配置：`papertrace.ingest.exec.lease-ttl-seconds`、`heartbeat-interval-seconds`。
5) 补充单元/集成测试：CAS 竞争、会话初始化、心跳续租。

## 11. 开放问题
- FAILED 可重试的纳入条件：是否限定错误码/次数（建议后续按业务原因细分）。
- deliveryNo（投递次数）头名兼容性：若无稳定头，默认视为 1，可写入 `run.stats`。
- configVersion/snapshotHash：当前表未单列，可暂存 `run.stats` 或 `checkpoint` JSON。
---

# 附录 A：MyBatis XML 片段（复杂 SQL 放 XML）

> 推荐路径：`patra-ingest/patra-ingest-infra/src/main/resources/mapper/`，命名：`TaskMapper.xml`、`TaskRunMapper.xml`。
> 命名空间需与接口一致：`com.patra.ingest.infra.persistence.mapper.TaskMapper` / `TaskRunMapper`。

## A.1 tryAcquireLease（CAS 抢占）
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.patra.ingest.infra.persistence.mapper.TaskMapper">

    <update id="tryAcquireLease">
        UPDATE ing_task
           SET lease_owner = #{owner},
               leased_until = DATE_ADD(#{now}, INTERVAL #{ttlSec} SECOND),
               lease_count = lease_count + 1
         WHERE id = #{taskId}
           AND status_code = 'QUEUED'
           AND idempotent_key = #{idem}
           AND (scheduled_at IS NULL OR scheduled_at &lt;= #{now})
           AND (leased_until IS NULL OR leased_until &lt;= #{now} OR lease_owner = #{owner})
    </update>
</mapper>
```

## A.2 markRunningWithLease（置 RUNNING + 起始信息）
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.patra.ingest.infra.persistence.mapper.TaskMapper">

    <update id="markRunningWithLease">
        UPDATE ing_task
           SET status_code = 'RUNNING',
               started_at = #{now},
               last_heartbeat_at = #{now},
               leased_until = DATE_ADD(#{now}, INTERVAL #{ttlSec} SECOND)
         WHERE id = #{taskId}
           AND lease_owner = #{owner}
    </update>
</mapper>
```
## A.3 selectLatestAttemptNo（获取最新 attemptNo）
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.patra.ingest.infra.persistence.mapper.TaskRunMapper">

    <select id="selectLatestAttemptNo" resultType="int">
        SELECT IFNULL(MAX(attempt_no), 0)
          FROM ing_task_run
         WHERE task_id = #{taskId}
    </select>
</mapper>
```

## A.4 insertTaskRun（新建运行尝试记录）
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.patra.ingest.infra.persistence.mapper.TaskRunMapper">

    <insert id="insertTaskRun" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO ing_task_run (
            task_id, attempt_no,
            provenance_code, operation_code,
            status_code, started_at, last_heartbeat,
            window_from, window_to,
            checkpoint, stats,
            scheduler_run_id, correlation_id
        ) VALUES (
            #{taskId}, #{attemptNo},
            #{provenanceCode}, #{operationCode},
            'RUNNING', #{now}, #{now},
            #{windowFrom}, #{windowTo},
            #{checkpointJson}, #{statsJson},
            #{schedulerRunId}, #{correlationId}
        )
    </insert>
</mapper>
```

## A.5 renewLease（心跳续租）
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.patra.ingest.infra.persistence.mapper.TaskMapper">

    <update id="renewLease">
        UPDATE ing_task
           SET leased_until = DATE_ADD(#{now}, INTERVAL #{ttlSec} SECOND),
               last_heartbeat_at = #{now},
               lease_count = lease_count + 1
         WHERE id = #{taskId}
           AND lease_owner = #{owner}
    </update>
</mapper>
```
## A.6 Mapper 接口签名建议（与 XML 对齐）
```java
// TaskMapper.java
@Mapper
public interface TaskMapper extends BaseMapper<TaskDO> {
    int tryAcquireLease(@Param("taskId") Long taskId,
                        @Param("owner") String owner,
                        @Param("now") Instant now,
                        @Param("ttlSec") int ttlSec,
                        @Param("idem") String idempotentKey);

    int markRunningWithLease(@Param("taskId") Long taskId,
                             @Param("owner") String owner,
                             @Param("now") Instant now,
                             @Param("ttlSec") int ttlSec);

    int renewLease(@Param("taskId") Long taskId,
                   @Param("owner") String owner,
                   @Param("now") Instant now,
                   @Param("ttlSec") int ttlSec);
}

// TaskRunMapper.java（可选扩展）
@Mapper
public interface TaskRunMapper extends BaseMapper<TaskRunDO> {
    int selectLatestAttemptNo(@Param("taskId") Long taskId);
    int insertTaskRun(TaskRunDO run); // 或直接使用 BaseMapper#insert
}
```
