# patra-spring-boot-starter-rocketmq

RocketMQ 统一封装 Starter，提供消息模型、命名规范与发布抽象。

## 1. 模块定位
- **服务/组件作用**：标准化 RocketMQ 消息的 header/payload、Topic 命名与发布 API
- **主要消费者**：`patra-ingest` Outbox Relay、未来事件驱动服务
- **架构边界**：Starter 负责封装模板和规范约束；消费端基类将在后续版本提供

## 2. 核心能力
- **消息模型 `PatraMessage<T>`**：统一 `eventId` / `traceId` / `occurredAt` 字段
- **发布抽象 `PatraMessagePublisher`**：隐藏 `RocketMQTemplate` 细节，强制命名校验
- **Topic 命名规范**：可配置 namespace、正则、tag 分隔符
- **重试属性占位**：预置消费重试参数，为后续消费端扩展做准备
- **Trace 衔接**：与核心 Starter 的 TraceProvider 集成，透传链路信息

详尽用法、配置表与最佳实践见 `docs/modules/starters/rocketmq.md`。

## 3. 分层结构与依赖
- 主要包：`config`（自动配置/属性）、`model`、`publisher`、`support`
- 依赖：`patra-spring-boot-starter-core`、`rocketmq-spring-boot-starter`

## 4. 运行与配置
- Maven 引入：
  ```xml
  <dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-rocketmq</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  ```
- 基本配置：
  ```yaml
  patra:
    messaging:
      rocketmq:
        enabled: true
        naming:
          namespace: INGEST
          topic-pattern: "^[A-Z][A-Z0-9]*(\\.[A-Z0-9]+)*$"
          tag-delimiter: "."
        retry:
          max-attempts: 3
          backoff: 1s
  rocketmq:
    name-server: ${MQ_NAMESERVER}
    producer.group: ingest-producer
  ```
- 发布示例：`publisher.send("INGEST.ARTICLE.CREATED", PatraMessage.of(payload))`

## 5. 观测与运维
- 关键日志：Topic、eventId、traceId、publish result；建议落地统一日志模式
- 建议结合 RocketMQ Console/Exporter 监控 Lag、重试、DLQ
- Topic 命名校验失败会抛异常，避免垃圾 Topic；生产环境需提前校验命名方案

## 6. 测试策略
- 使用 Embedded RocketMQ 或 MockTemplate 验证 Publisher 行为
- 断言 Topic 命名校验、header 注入、 traceId 透传
- 模拟发送失败，确认异常处理与日志输出

## 7. Roadmap 与风险
| 项目 | 状态 | 风险/备注 |
|------|------|-----------|
| 消费端抽象 | 规划 | 需统一重试、死信、指标策略 |
| 事务 / 延迟消息 | 规划 | 需结合 RocketMQ 事务接口及延迟级别 |
| 序列化插件 | 规划 | 提供可插拔编码（Avro/Protobuf）
| 批量发送 | 规划 | 注意吞吐提升与顺序保证冲突 |

风险：Topic 命名不合规、NameServer/Producer 配置缺失、traceId 未透传导致链路断档。

## 8. 参考资料
- 深度文档：`docs/modules/starters/rocketmq.md`
- Outbox 流程：`docs/modules/ingest/deep-dive.md`
- 错误规范：`docs/standards/platform-error-handling.md`
