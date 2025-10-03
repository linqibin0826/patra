# Ingest 模块 RocketMQ 5.x 接入（Spring Cloud Stream + StreamBridge + 动态目的地）

> 状态：草案（Draft）｜作者：Codex｜适用版本：Spring Boot 3.2.x / Spring Cloud 2023.0.x / Spring Cloud Alibaba 2023.0.1.0

## 1. 适用范围与目标
- 仅涉及 `patra-ingest` 服务：在 infra 层实现 Outbox 出站（发布），在 adapter 层实现入站（消费，先日志）。
- 出站采用 Spring Cloud Stream（SCS）+ RocketMQ Binder，通过 `StreamBridge` 动态目的地发布；不修改 domain 与 app，用例仍由 `OutboxRelayExecutor` 编排。
- 主题（topic）命名沿用领域规范：下划线分段 UPPER_SNAKE（如 `INGEST_TASK_READY`）。
- 配置走 Nacos/环境变量；不硬编码密钥/地址；支持配置回退到 `NoopOutboxPublisher`。

TL;DR
- 生产端：固定使用 `outbox-out-0` binding，通过 `BinderHeaders.TARGET_DESTINATION`=`OutboxMessage.channel()` 动态路由。
- 消费端：为示例通道 `INGEST_TASK_READY` 增加函数式消费者，先打印日志。
- 失败/重试：仍由 app 层（Relay 执行器）统一处理；生产异常直接抛出，让编排决定重试/失败。

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

## 4. 通道命名与动态目的地策略
- 领域规范：统一下划线分段 UPPER_SNAKE（例：`INGEST_TASK_READY`），来源于 `ChannelKey.channel()`。
- 动态目的地：生产端固定使用 `outbox-out-0` binding，通过设置消息头 `BinderHeaders.TARGET_DESTINATION=channel` 路由到实际 topic。
- 可选统一前缀：如需，可采用 `papertrace_INGEST_TASK_READY`（避免点号）。

---

## 5. 出站实现（infra）
- 新增类：`patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/messaging/RocketMqOutboxPublisher.java`
- 职责：实现 `OutboxPublisherPort.publish(message, plan)`，使用 `StreamBridge#send("outbox-out-0", message)` 发布。
- 载荷与头：
  - payload：`message.getPayloadJson()`（字符串 JSON），`content-type=application/json`
  - 业务头：若 `headersJson` 非空，反序列化后注入消息头（勿含敏感信息）
  - RocketMQ 建议头：`KEYS=dedupKey`、`TAGS=opType`；`partitionKey` 放在 headers['partitionKey'] 以配合分区表达式
- 返回值：短期返回 `PublishResult.NONE`（SCS 模式下 msgId 不直观）；后续如需记录 msgId，可引入 RocketMQTemplate 或回调增强
- 条件装配：`@ConditionalOnProperty(name="papertrace.ingest.outbox.publisher", havingValue="rocketmq")`；保持 `NoopOutboxPublisher` 作为默认回退

---

## 6. 入站消费（adapter）
- 新增类：`patra-ingest/patra-ingest-adapter/src/main/java/com/patra/ingest/adapter/inbound/stream/IngestStreamConsumers.java`
- 定义 `@Bean Consumer<Message<String>> ingestTaskReadyConsumer()`，订阅 `INGEST_TASK_READY`，打印关键头与 payload（用于链路验证）
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
      bindings:
        # 生产端统一 binding（目的地由 header 覆盖）
        outbox-out-0:
          binder: rocketmq
          destination: PT_PLACEHOLDER
          content-type: application/json
          producer:
            partition-key-expression: "headers['partitionKey']"
            partition-count: 8

        # 示例消费者：订阅 INGEST_TASK_READY
        ingestTaskReadyConsumer-in-0:
          binder: rocketmq
          destination: INGEST_TASK_READY
          group: ingest-consumer
          content-type: application/json
          consumer:
            concurrency: 2

papertrace:
  ingest:
    outbox:
      publisher: ${PUBLISHER_IMPL:rocketmq}  # rocketmq | noop
```

配置要点
- 不在代码中硬编码地址/密钥；全部走环境变量/Nacos（暂时写boot模块的application.yaml中）。
- 目的地动态路由，`destination` 仅占位；实际以消息头覆盖。
- topic 使用下划线命名，避免点号字符。

---

## 8. 消息契约（最小约束）
- destination：来自 `OutboxMessage.channel()`（例：`INGEST_TASK_READY`）
- payload：JSON 字符串（与通道关联的 payloadType 匹配）
- headers（建议）：
  - `KEYS`=dedupKey（去重/追踪）
  - `TAGS`=opType（业务标签）
  - `partitionKey`=partitionKey（分区路由）
  - 业务自定义头：来自 `headersJson`（非敏感）

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
- 头键兼容：不同 binder 版本 RocketMQ 头键可能存在差异。上线前以实际 binder 实测校准（本方案先使用通用 `KEYS/TAGS` 键名）。
- 动态目的地安全：可在 Nacos 维护“允许目的地白名单”，发送前在 publisher 侧进行校验。
- 顺序与分区：当前普通消息；若需分区有序，需评估 binder 对顺序消息与队列策略的支持。
- msgId：短期返回 `NONE`；如必须写回 msgId，后续接入 RocketMQTemplate 或发送回调拦截器。

---

## 11. 灰度与回滚
- 回滚：配置切回 `publisher=noop` 即可，无需改代码。
- 灰度：按通道在 Nacos 配置白名单，仅允许部分通道使用 RocketMQ 发布，其余仍走 Noop（可在 publisher 内做校验）。

---

## 12. 任务拆解（Task List）
1) 依赖接入：在 infra/adapter 模块 POM 添加 `spring-cloud-starter-stream-rocketmq`
2) 配置落地：在 `patra-ingest-boot` 的 Nacos 配置添加 binder/bindings 与 `papertrace.ingest.outbox.publisher` 开关
3) 出站实现：新增 `RocketMqOutboxPublisher`，装配 `StreamBridge`，实现动态目的地与头部映射
4) 入站消费者：新增 `IngestStreamConsumers`，实现 `ingestTaskReadyConsumer` 日志打印
5) 验证联调：本地 RocketMQ + XXL 触发，检查发布/消费/写回
6) 文档同步：本文件合并与完善；在 `docs/README.md` 增加索引条目

---

## 13. 验收标准（Acceptance Criteria）
- 出站：
  - `publisher=rocketmq` 时，Relay 批次 published 计数正确增加；异常进入 app 失败/重试分支
- 入站：
  - 成功消费 `INGEST_TASK_READY` 并打印包含 payload 与关键头的日志
- 可回滚：
  - `publisher=noop` 时，Relay 流程不依赖 MQ 仍可完成（仅日志）
- 架构合规：
  - domain/app 未引入框架依赖；仅 infra/adapter 接入 binder；配置由 Nacos/环境变量注入

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
- 事务/半消息与回查、DLQ/重放工具、统一 Topic 前缀策略、通道白名单校验组件
- 指标与告警：发送成功/失败计数、延迟、堆积、消费失败告警
- 强类型 payload 校验与 schema 演进策略（向后兼容）

---

## 16. 附录：索引与示例片段
- 文档索引建议：在 `docs/README.md` 的“快速入口”新增
  - `modules/ingest/rocketmq-stream.md`（Ingest：RocketMQ 接入指南）
- 示例代码（片段，供参考，不是最终实现）：
```java
// 出站发布（核心要点示例）
MessageBuilder<String> mb = MessageBuilder
  .withPayload(message.getPayloadJson())
  .setHeader(BinderHeaders.TARGET_DESTINATION, message.getChannel())
  .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
  .setHeaderIfAbsent("KEYS", message.getDedupKey())
  .setHeaderIfAbsent("TAGS", message.getOpType())
  .setHeaderIfAbsent("partitionKey", message.getPartitionKey());
boolean ok = streamBridge.send("outbox-out-0", mb.build());
```
```java
// 入站消费者（日志验证示例）
@Bean
public Consumer<Message<String>> ingestTaskReadyConsumer() {
  return msg -> log.info("[INGEST][ADAPTER] consume topic={} keys={} tags={} headers={} payload={}",
      msg.getHeaders().getOrDefault("rocketmq_TOPIC", "unknown"),
      msg.getHeaders().getOrDefault("KEYS", null),
      msg.getHeaders().getOrDefault("TAGS", null),
      msg.getHeaders(),
      msg.getPayload());
}
```
