# Papertrace RocketMQ 规范（草案）

## 1. 依赖与启用
- 在业务模块 `pom.xml` 中加入：
  ```xml
  <dependency>
      <groupId>com.papertrace</groupId>
      <artifactId>patra-spring-boot-starter-rocketmq</artifactId>
  </dependency>
  ```
- Spring Boot 会自动加载官方 `rocketmq-spring-boot-starter`，并注入 `PatraMessagePublisher`、`PatraRocketMQProperties` 等工具。

## 2. 配置入口
- 仅使用 `patra.messaging.rocketmq.*` 前缀。
- 示例（建议配置在 Nacos）：
  ```yaml
  patra:
    messaging:
      rocketmq:
        enabled: true
        naming:
          namespace: INGEST
          topic-pattern: "^[A-Z][A-Z0-9]*(\\.[A-Z0-9]+)*$"
        retry:
          max-attempts: 5
          backoff: 2s
  rocketmq:
    name-server: ${MQ_NAMESERVER}
    producer:
      group: ingest-producer
  ```
  > 说明：RocketMQ 官方属性（如 `rocketmq.name-server`）仍需保留，用于连接信息。`patra.messaging.rocketmq` 仅承载规范策略。

## 3. 规范要点
- **Topic/Tag 命名**：`TopicNameValidator` 默认要求 `DOMAIN.SUBJECT`（全大写，点号分隔）。自定义场景可通过 `topic-pattern` 调整。
- **消息载荷**：统一使用 `PatraMessage<T>` 包裹业务对象，默认附带 `eventId`、`traceId`、`occurredAt` 字段。
- **消息发布**：注入 `PatraMessagePublisher`，调用 `send("TOPIC:TAG", PatraMessage.of(payload))`；发送前会校验 Topic 命名并写入公共 header。
- **消费者基类**：继承 `AbstractPatraMessageListener<T>`，并使用 `@RocketMQMessageListener` 标注消费组／Topic／Tag。基类已内置日志输出、消费位点策略与重试设置，可通过属性调整最大重试次数。

## 4. 扩展建议
- **Outbox 集成**：发布端统一从 Outbox 表读取后调用 `PatraMessagePublisher`，确保幂等与追踪；Outbox schema 参考 `patra-ingest` 已实施案例。
- **监控与日志**：消费失败会打印 `eventId`/`traceId`，便于 SkyWalking/ELK 检索；后续可在此模块追加 Micrometer 指标。

## 5. 后续计划
- 支持延迟/事务消息包装。
- 补充 Kafka 适配层时复用同一 `PatraMessage` 与 Topic 规范。
