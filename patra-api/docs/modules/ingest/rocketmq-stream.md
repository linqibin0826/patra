# Ingest 模块 RocketMQ 5.x 接入（Spring Cloud Stream + StreamBridge + 动态目的地）

> 状态：草案（Draft）｜作者：Codex｜适用版本：Spring Boot 3.2.x / Spring Cloud 2023.0.x / Spring Cloud Alibaba 2023.0.1.0

## 1. 适用范围与目标
- 仅涉及 `patra-ingest` 服务：在 infra 层实现 Outbox 出站（发布），在 adapter 层实现入站（消费，先日志）。
- 出站采用 Spring Cloud Stream（SCS）+ RocketMQ Binder，通过 `StreamBridge` 动态目的地发布；不修改 domain 与 app，用例仍由 `OutboxRelayExecutor` 编排。
- 主题（topic）命名沿用领域规范：下划线分段 UPPER_SNAKE（如 `INGEST_TASK_READY`）。
- 配置走 Nacos/环境变量；不硬编码密钥/地址；支持配置回退到 `NoopOutboxPublisher`。

TL;DR（选型：纯动态目的地，不预定义生产 binding）
- 生产端：直接调用 `StreamBridge.send(channel, message)`；`channel` 即 RocketMQ topic；不使用 `outbox-out-0` 及 `TARGET_DESTINATION` 头。
- 消费端：示例订阅 `INGEST_TASK_READY`，函数式 `Consumer<Message<String>>` 打印日志验证链路。
- 失败/重试：发送失败抛异常，由 Relay 编排器统一判定重试/失败。
- 回退：`papertrace.ingest.outbox.publisher=noop` 立即停用 MQ 发布。
- 白名单：`strict-channel-whitelist=true` 时，仅允许 `allowed-channels` 中的 channel 发布（默认关闭）

术语映射
- channel（领域统一术语，同时为 StreamBridge.send 的第一个参数） = RocketMQ topic
- dedupKey → RocketMQ KEYS 属性（MessageConst.PROPERTY_KEYS）
- opType → RocketMQ TAGS 属性（MessageConst.PROPERTY_TAGS）

---

## 2. 架构与分层
- domain：`OutboxPublisherPort`（保持不动）
- app：`OutboxRelayExecutor`（保持不动，publish→写回状态）
- infra：新增 `RocketMqOutboxPublisher` 实现端口；与 `NoopOutboxPublisher` 通过配置开关切换
- adapter：新增函数式 `Consumer<Message<String>>` 入站（先打印）
- boot：集中 SCS/binder 绑定与连接配置（推荐托管至 Nacos）

依赖方向：遵循六边形架构，domain 不依赖框架；app 不依赖 binder；基础设施适配在 infra/adapter 内部完成。

---

## 3. 依赖与版本
- 父 POM：已引入 `spring-cloud-alibaba-dependencies`（2023.0.1.0）
- 新增模块依赖：
  - `patra-ingest/patra-ingest-infra`：`com.alibaba.cloud:spring-cloud-starter-stream-rocketmq`
  - `patra-ingest/patra-ingest-adapter`：`com.alibaba.cloud:spring-cloud-starter-stream-rocketmq`
- 不在 domain/app 引入任何框架依赖。

---

## 4. 通道命名与动态发布策略
- 领域规范：统一下划线分段 UPPER_SNAKE（例：`INGEST_TASK_READY`），来源于 `ChannelKey.channel()`。
- 动态发布（纯动态创建）：
  - 不预定义生产 binding（如 `outbox-out-0`），避免创建占位符 topic。
  - 使用 `StreamBridge.send(channel, message)` 按需发送；channel 即实际 RocketMQ topic 名称。
  - 优点：无冗余配置，按需创建，领域 channel 直观映射；权衡：集中生产者高级参数需依赖全局默认或代码控制。
- 可选统一前缀：如需，可采用 `papertrace_INGEST_TASK_READY`（避免点号）。

---

## 5. 出站实现（infra）
- 新增类：`patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/messaging/RocketMqOutboxPublisher.java`
- 职责：实现 `OutboxPublisherPort.publish(message, plan)`，使用 `StreamBridge.send(channel, message)` 动态发布。
**动态 channel 发布核心逻辑**：
  ```java
  String channel = message.getChannel(); // 如 "INGEST_TASK_READY"
  streamBridge.send(channel, messageBuilder.build());
  ```
  - 无需设置 `TARGET_DESTINATION` 头，直接用 channel 作为发送参数
  - binder 按需创建并绑定到 RocketMQ topic
- **通道白名单校验（新增）**：
  - 目的：仅允许在白名单中的 channel 发送，避免误发到未就绪或禁用的通道。
  - 配置：`papertrace.ingest.outbox.strict-channel-whitelist`（布尔，默认 false）、`papertrace.ingest.outbox.allowed-channels`（字符串列表）。
  - 行为：当 strict=true 时，若 `allowed-channels` 为空或当前 channel 不在列表中，直接拒绝发送并抛出 `OutboxPublishException("CHANNEL_NOT_ALLOWED")`，建议 app 层据此判定为“不可重试”。
  - 伪代码：
    ```java
    String channel = message.getChannel();
    Set<String> allowed = props.getAllowedChannels();
    if (props.isStrictChannelWhitelist() && (allowed == null || !allowed.contains(channel))) {
        throw new OutboxPublishException("CHANNEL_NOT_ALLOWED: " + channel);
    }
    boolean ok = streamBridge.send(channel, messageBuilder.build());
    ```
- 载荷与头：
  - payload：`message.getPayloadJson()`（字符串 JSON），`content-type=application/json`
  - 业务头：若 `headersJson` 非空，反序列化后注入消息头（勿含敏感信息）
  - **RocketMQ 标准头（使用常量）**：
    - `MessageConst.PROPERTY_KEYS=dedupKey`（去重/追踪键）
    - `MessageConst.PROPERTY_TAGS=opType`（业务标签）
    - `partitionKey` 放在 headers['partitionKey'] 以配合分区表达式（**含兜底逻辑**：为空时回退到 dedupKey）
- **msgId 写回逻辑**（已有字段 `msg_id`）：
  - 发送前检查：若 `message.getMsgId()` 非空 → 跳过发送（已发送过）
  - 发送后更新：
    - **问题**：`StreamBridge.send()` 仅返回 `boolean`，无法直接获取 broker 返回的 msgId
    - **短期方案**：通过 RocketMQ Binder 的 `SendCallback` 或 `RocketMQTemplate` 包装层获取（需额外适配）
    - **降级方案**：本次实现暂不写回 msgId，先保证发送成功性；后续 Phase 2 改用 `RocketMQTemplate.syncSend()` 获取 `SendResult.msgId` 并更新数据库
- 返回值：
  - 发送成功返回 `PublishResult.SUCCESS`
  - 发送失败（`send()` 返回 false）抛出 `OutboxPublishException`，由 app 层决定重试
  - 已发送跳过返回 `PublishResult.ALREADY_SENT`（msgId 非空场景）
- 条件装配：`@ConditionalOnProperty(name="papertrace.ingest.outbox.publisher", havingValue="rocketmq")`；保持 `NoopOutboxPublisher` 作为默认回退

---

## 6. 入站消费（adapter）
- 新增类：`patra-ingest/patra-ingest-adapter/src/main/java/com/patra/ingest/adapter/inbound/stream/IngestStreamConsumers.java`
- 定义 `@Bean Consumer<Message<String>> ingestTaskReadyConsumer()`，订阅 `INGEST_TASK_READY`，打印关键头与 payload（用于链路验证）
- **消费者配置增强**：
  - 本地重试：`max-attempts=3`，指数退避（`back-off-*`）
  - 并发度：`concurrency=2`（可按实际调整）
  - 延迟重试级别：`delayLevelWhenNextConsume=0`（RocketMQ 特定，0 表示立即重试）
- 后续可扩展：多通道绑定、反序列化为强类型 DTO、校验与路由到用例

---

## 7. 配置清单（Nacos/本地 application.yml）
```yaml
spring:
  cloud:
    stream:
      binders:
        rocketmq:
          type: rocketmq
          environment:
            spring:
              cloud:
                stream:
                  rocketmq:
                    binder:
                      # RocketMQ 5.x：name-server 或 proxy 二选一
                      name-server: ${ROCKETMQ_NAMESRV:127.0.0.1:9876}
                      # proxy: ${ROCKETMQ_PROXY:}
                      # ACL（如启用）
                      access-key: ${ROCKETMQ_AK:}
                      secret-key: ${ROCKETMQ_SK:}
      
      # 纯动态创建，不预定义 outbox-out-0 binding
      # 生产端无需显式 binding 配置，由 StreamBridge 动态创建
      
      # Binder 全局生产者默认配置（可选）
      default:
        producer:
          partition-key-expression: "headers['partitionKey'] != null ? headers['partitionKey'] : headers['KEYS']"
          partition-count: 8  # 确保 broker 中 topic 队列数 >= 8；若关闭自动创建需提前 mqadmin 预创建
      
      bindings:
        # 示例消费者：订阅 INGEST_TASK_READY
        ingestTaskReadyConsumer-in-0:
          binder: rocketmq
          destination: INGEST_TASK_READY
          group: ingest-consumer
          content-type: application/json
          consumer:
            concurrency: 2
            max-attempts: 3                     # 本地重试 3 次
            back-off-initial-interval: 1000     # 首次重试等 1s
            back-off-max-interval: 10000        # 最大等 10s
            back-off-multiplier: 2.0            # 指数退避
            # RocketMQ 特定配置
            delayLevelWhenNextConsume: 0        # 0=立即重试，1-18=预设延迟级别

papertrace:
  ingest:
    outbox:
      publisher: ${PUBLISHER_IMPL:rocketmq}  # rocketmq | noop
      strict-channel-whitelist: ${OUTBOX_STRICT_CHANNEL_WHITELIST:false}
      allowed-channels:
        - INGEST_TASK_READY   # 示例；仅允许在列表中的通道发送
        # - INGEST_TASK_PARSED
```

配置要点
- 纯动态：无 `outbox-out-0` 生产 binding，首次发送触发（或需预创建）实际 topic。
- 分区策略：`partitionKey` 为空回退 `KEYS`，缓解热点。
- 分区与队列：`partition-count` 不得超过 topic 实际队列数；生产环境常关闭自动创建，需预创建。
- 全局默认生产者：可集中管理分区表达式与并发参数。
- 环境变量注入：不硬编码地址/密钥，全部走 Nacos / 环境变量。
- 白名单校验：开启 `strict-channel-whitelist=true` 后，仅 `allowed-channels` 列表中的通道允许发送；列表为空时将拒绝所有发送（安全默认关：默认 false）。
- 命名规范：下划线 UPPER_SNAKE，避免点号。
- 自动创建前提：依赖 broker `autoCreateTopicEnable=true`；关闭时用 `mqadmin updateTopic` 预创建并指定队列数。

---

## 8. 消息契约（最小约束）
- channel：来自 `OutboxMessage.channel()`（例：`INGEST_TASK_READY`），直接作为 RocketMQ topic 名称
- payload：JSON 字符串（与通道关联的 payloadType 匹配）
- headers（建议，使用 RocketMQ 常量）：
  - `MessageConst.PROPERTY_KEYS`=dedupKey（去重/追踪，对应 RocketMQ 原生 KEYS 属性）
  - `MessageConst.PROPERTY_TAGS`=opType（业务标签，对应 RocketMQ 原生 TAGS 属性）
  - `partitionKey`=partitionKey（分区路由，兜底逻辑：为空时使用 dedupKey）
  - 业务自定义头：来自 `headersJson`（非敏感，如 traceId、sourceSystem 等）
- **msgId 字段**（数据库已有）：
  - 表字段：`msg_id varchar(128) comment 'Broker 返回的消息ID（对账/回放标识）'`
  - 当前阶段：暂不写回（StreamBridge 限制）
  - Phase 2：改用 `RocketMQTemplate.syncSend()` 获取 `SendResult.msgId` 并更新到数据库

---

## 9. 运行与验证
1) 启动 RocketMQ 5.x（nameserver 或 proxy），暴露地址到环境变量
2) 启动 `patra-ingest-boot`，设置 `papertrace.ingest.outbox.publisher=rocketmq`
3) 通过 XXL 作业 `ingestOutboxRelayJob` 触发 relay（可指定 `channel=INGEST_TASK_READY`）
4) 观察：
   - `OutboxRelayExecutor` 的 published 计数与状态写回
   - 适配层消费者打印日志（包含 payload 与关键头）

---

## 10. 风险与对策
- **消息头键名兼容性**：
    - **必须使用 RocketMQ 常量**：`MessageConst.PROPERTY_KEYS`/`PROPERTY_TAGS`（需引入 `rocketmq-client` 依赖）
    - 不同 binder 版本可能存在键名映射差异，上线前**必须实测验证**消费端能否正确获取 KEYS/TAGS
- **顺序与分区**：
    - 当前普通消息；若需分区有序，需评估 binder 对顺序消息与队列策略的支持
    - 分区键兜底已实现：为空时回退到 dedupKey，避免热点
- **msgId 写回限制**：当前阶段策略见第 5 节（暂不写回）；重复发送风险需消费端基于 KEYS（dedupKey）实现幂等。
- **分区数与队列数不一致**：
  - `partition-count=8` 必须 ≤ 目标 topic 队列数；若自动创建关闭需手工创建（示例：`mqadmin updateTopic -n ${ROCKETMQ_NAMESRV} -t INGEST_TASK_READY -c <ClusterName> -r 6 -w 6`）。
---

## 11. 灰度与回滚
- 回滚：配置切回 `publisher=noop` 即可，无需改代码。
- 灰度：按通道在 Nacos 配置白名单，仅允许部分通道使用 RocketMQ 发布，其余仍走 Noop（可在 publisher 内做校验）。

---

## 12. 任务拆解（Task List）
1) **依赖接入**：
   - 在 `patra-ingest-infra/pom.xml` 添加 `spring-cloud-starter-stream-rocketmq`
   - 在 `patra-ingest-adapter/pom.xml` 添加 `spring-cloud-starter-stream-rocketmq`
   - 在 `patra-ingest-infra/pom.xml` 添加 `rocketmq-client`（仅 `<scope>provided</scope>`，用于引入常量类）
2) **配置落地**：
   - 在 `patra-ingest-boot` 的 Nacos 配置（或本地 application.yaml）添加 binder/bindings 与 `papertrace.ingest.outbox.publisher` 开关
   - **移除 `outbox-out-0` binding 配置**，采用纯动态创建
   - 补充消费者重试配置（`max-attempts`/`back-off-*`）
3) **出站实现**：
   - 新增 `RocketMqOutboxPublisher`，装配 `StreamBridge`
  - 实现动态 channel 发布：`streamBridge.send(message.getChannel(), ...)`
   - 使用 `MessageConst.PROPERTY_KEYS`/`PROPERTY_TAGS` 设置消息头
   - 实现分区键兜底逻辑：为空时回退到 dedupKey
   - 增加通道白名单校验：基于 `papertrace.ingest.outbox.strict-channel-whitelist` 与 `allowed-channels`；不在白名单直接拒绝（建议标记为不可重试）
   - **发送前检查 msgId**：若非空则跳过（`ALREADY_SENT`）
   - 发送失败抛出 `OutboxPublishException`
   - 补充条件装配：`@ConditionalOnProperty(name="papertrace.ingest.outbox.publisher", havingValue="rocketmq")`
4) **入站消费者**：
   - 新增 `IngestStreamConsumers`，实现 `ingestTaskReadyConsumer` 日志打印
   - 验证消息头能否正确获取（KEYS/TAGS/partitionKey）
5) **验证联调**：
   - 本地 RocketMQ + XXL 触发 `ingestOutboxRelayJob`
   - 检查发布/消费/写回日志
   - 验证重试配置生效（模拟消费失败）
6) **文档同步**：
   - 本文件合并与完善
   - 在 `docs/README.md` 增加索引条目

---

## 13. 验收标准（Acceptance Criteria）
- 出站：
  - `publisher=rocketmq` 时，Relay 批次 published 计数正确增加；异常进入 app 失败/重试分支
  - 发送成功后消息可在 RocketMQ 控制台查询到（topic=`INGEST_TASK_READY`）
  - 白名单校验：`strict-channel-whitelist=true` 时，非白名单通道发送被拒绝（抛 `OutboxPublishException: CHANNEL_NOT_ALLOWED`），且未触发实际网络发送
  - **msgId 去重**：同一 outbox 记录重复执行时，若 `msg_id` 非空则跳过发送
- 入站：
  - 成功消费 `INGEST_TASK_READY` 并打印包含 payload 与关键头的日志
  - 能正确获取 `KEYS`、`TAGS`、`partitionKey` 头信息（验证常量映射）
  - 消费失败时触发本地重试（最多 3 次），观察退避延迟
- 可回滚：
  - `publisher=noop` 时，Relay 流程不依赖 MQ 仍可完成（仅日志）
- 架构合规：
  - domain/app 未引入框架依赖；仅 infra/adapter 接入 binder；配置由 Nacos/环境变量注入
- 动态创建验证：
  - RocketMQ 控制台中**不存在** `PT_PLACEHOLDER` 等占位符 topic
  - 仅在首次发送时自动创建实际通道对应的 topic（如 `INGEST_TASK_READY`）

---

## 14. 变更文件清单（预期）
- 依赖：
  - `patra-ingest/patra-ingest-infra/pom.xml`
  - `patra-ingest/patra-ingest-adapter/pom.xml`
- 代码：
  - `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/messaging/RocketMqOutboxPublisher.java`（新增）
  - `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/messaging/NoopOutboxPublisher.java`（补充条件装配，可选）
  - `patra-ingest/patra-ingest-adapter/src/main/java/com/patra/ingest/adapter/inbound/stream/IngestStreamConsumers.java`（新增）
- 配置：
  - `patra-ingest-boot` 应用配置（Nacos）增加 stream/binder/bindings 与开关
- 文档：
  - 本文件
  - `docs/README.md` 索引新增一行（参见下节）

---

## 15. 后续演进（非本次范围）
- **Phase 2 - msgId 写回增强**：
  - 改用 `RocketMQTemplate.syncSend()` 替代 `StreamBridge`
  - 获取 `SendResult.msgId` 并更新到 `outbox_message.msg_id` 字段
  - 实现严格去重：发送前检查 msgId，避免网络抖动导致重复发送
- **Phase 3 - 高级特性**：
  - 事务/半消息与回查
  - DLQ（死信队列）/重放工具
  - 统一 Topic 前缀策略（如 `pt_`）
  - 监控指标与告警：发送成功/失败计数、延迟、堆积、消费失败告警
  - 强类型 payload 校验与 schema 演进策略（向后兼容）

---

## 16. 附录：索引与示例片段
- 文档索引建议：在 `docs/README.md` 的"快速入口"新增
    - `modules/ingest/rocketmq-stream.md`（Ingest：RocketMQ 接入指南）
- 示例代码（片段，供参考，不是最终实现）：
- 
```java
// 出站发布（核心要点示例 - 纯动态创建）
import org.apache.rocketmq.common.message.MessageConst;

String channel = message.getChannel(); // 如 "INGEST_TASK_READY"

// 白名单校验（仅允许配置的通道）
Set<String> allowed = props.getAllowedChannels();
if (props.isStrictChannelWhitelist() && (allowed == null || !allowed.contains(channel))) {
    throw new OutboxPublishException("未被允许的通道: " + channel);
}

// 分区键兜底
String partitionKey = StrUtil.isNotBlank(message.getPartitionKey()) 
    ? message.getPartitionKey() 
    : message.getDedupKey();

MessageBuilder<String> mb = MessageBuilder
  .withPayload(message.getPayloadJson())
  .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
  .setHeader(MessageConst.PROPERTY_KEYS, message.getDedupKey())       // 使用常量
  .setHeader(MessageConst.PROPERTY_TAGS, message.getOpType())         // 使用常量
  .setHeader("partitionKey", partitionKey);

// 直接发送到动态 destination，无需 TARGET_DESTINATION 头
boolean ok = streamBridge.send(channel, mb.build());
if (!ok) {
  throw new OutboxPublishException("发送失败: " + channel);
}
```

```java
// 入站消费者（日志验证示例）
@Bean
public Consumer<Message<String>> ingestTaskReadyConsumer() {
  return msg -> {
      log.info("[INGEST][ADAPTER] consume topic={} KEYS={} TAGS={} partitionKey={} headers={} payload={}",
          msg.getHeaders().getOrDefault("rocketmq_TOPIC", "unknown"),
          msg.getHeaders().get(MessageConst.PROPERTY_KEYS),          // 验证常量映射
          msg.getHeaders().get(MessageConst.PROPERTY_TAGS),
          msg.getHeaders().get("partitionKey"),
          msg.getHeaders(),
          msg.getPayload());
  };
}
```

```yaml
# 配置示例（纯动态：无 outbox-out-0 binding）
spring:
  cloud:
    stream:
      binders:
        rocketmq:
          type: rocketmq
          environment:
            spring.cloud.stream.rocketmq.binder:
              name-server: 127.0.0.1:9876
      
      # 全局默认生产者配置（可选）
      default:
        producer:
          partition-key-expression: "headers['partitionKey'] != null ? headers['partitionKey'] : headers['KEYS']"
          partition-count: 8
      
      bindings:
        # 仅定义消费者 binding
        ingestTaskReadyConsumer-in-0:
          binder: rocketmq
          destination: INGEST_TASK_READY
          group: ingest-consumer
          consumer:
            concurrency: 2
            max-attempts: 3
            back-off-initial-interval: 1000
```
