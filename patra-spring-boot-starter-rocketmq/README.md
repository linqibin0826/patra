# patra-spring-boot-starter-rocketmq

RocketMQ 统一封装：消息模型、发布抽象、命名规范校验、目的地构建与 Channel 注册。

## 1. 模块定位
- **服务/组件作用**：标准化 RocketMQ 消息的 header/payload、Topic 命名与发布 API
- **主要消费者**：`patra-ingest` Outbox Relay、未来事件驱动服务
- **架构边界**：Starter 负责封装模板和规范约束；提供 `PatraMessageHandler` 接口供消费端实现

## 2. 核心能力
- 消息模型：`PatraMessage<T>` 统一 `eventId`/`traceId`/`occurredAt`
- 发布抽象：`PatraMessagePublisher` 支持 `send`/`sendByChannel`/`sendOrderly`/`sendDelay`
- 命名规范：`TopicNameValidator`（Topic 前缀/正则/namespace）+ `GroupNameValidator`（消费组）
- 启动校验：`RocketMQListenerAnnotationValidator` 对 `@RocketMQMessageListener` 的 `topic/tag/group` 进行 fail-fast 校验
- 目的地构建：`DestinationBuilder.fromChannel("domain.resource.event") -> TOPIC:TAG`
- **API 契约**：发布方在 `{service}-api` 模块暴露 `PublishedChannels`，消费方引用避免硬编码
- Channel 注册：`ChannelRegistry` 统一收集与校验允许的 channel，`sendByChannel` 发送前进行校验
- Trace 衔接：`PatraMessageFactory` 优先从 `TraceProvider` 注入 `traceId`
- 消费者接口：`PatraMessageHandler<T>` + `@Consumes` 注解实现运行时注册

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
          # 建议留空，自动按 Spring profiles.active 推导（dev→DEV；会剔除非字母数字）
          namespace: ${spring.profiles.active:}
          topic-pattern: "^[A-Z][A-Z0-9]*(\\.[A-Z0-9]+)*$"
          tag-delimiter: "."
          consumer-group-pattern: "^[a-z][a-z0-9\\-]*$"
        retry:
          max-attempts: 3
          backoff: 1s
      channels:
        enforce: true         # 默认 true；true 时要求在注册表内
        domain: ingest        # 可选；要求 channel 第一段等于该值
  rocketmq:
    name-server: ${MQ_NAMESERVER}
    producer:
      group: ingest-producer
  ```
- 发布示例：`publisher.send("INGEST.ARTICLE.CREATED", PatraMessage.of(payload))`
- Channel 示例：`publisher.sendByChannel("ingest.task.ready", PatraMessage.of(payload)) // -> INGEST.TASK:READY`
- 消费组命名：`svc-{service}-{consumer}-cg`（正则 `^[a-z][a-z0-9\-]*$`），示例：`svc-ingest-relay-cg`
- 消费者注解：
  ```java
  // 方式一：引用发布方 API 契约（推荐）
  import com.patra.ingest.api.messaging.IngestPublishedChannels;
  
  @Consumes(channel = IngestPublishedChannels.TASK_READY, consumer = "relay")
  
  // 方式二：字符串 channel（不推荐，容易硬编码错误）
  @Consumes(channel = "ingest.task.ready", consumer = "relay")
  
  // 方式三：枚举 channel（内部使用，类型安全）
  @Consumes(channelEnum = IngestChannels.class, channelName = "TASK_READY", consumer = "relay")
  ```
- Channel 注册：提供 `ChannelCatalog` Bean，从领域枚举自动提取（实现 SSOT）
  ```java
  @Configuration
  public class IngestMessagingConfiguration {
      @Bean
      public ChannelCatalog ingestChannelCatalog() {
          // 自动从领域枚举提取所有 channel，保持单一数据源
          return () -> Arrays.stream(IngestChannels.values())
                            .map(ChannelKey::channel)
                            .collect(Collectors.toSet());
      }
  }
  ```

## 5. 观测与运维
- 关键日志：Topic、eventId、traceId、publish result；建议落地统一日志模式
- 建议结合 RocketMQ Console/Exporter 监控 Lag、重试、DLQ
- Topic 命名校验失败会抛异常，避免垃圾 Topic；生产环境需提前校验命名方案

## 6. 错误处理（平台化）
- 运行期发布入口（`send*`/`sendByChannel`）对命名/参数等校验失败，统一抛出 `ApplicationException(UNPROCESSABLE/422)`，消息包含 `destination/channel` 等上下文。
- Web/Feign 适配层会据此输出统一的 `ProblemDetail`，错误码前缀来源于 `patra.error.context-prefix`（未配置时为 `UNKNOWN`）。
- 启动期/配置期（例如 `@RocketMQMessageListener` 注解）不合规仍使用标准异常 `IllegalArgumentException` 直接 fail‑fast，便于快速定位配置问题。

## 7. 测试策略
- 使用 Embedded RocketMQ 或 MockTemplate 验证 Publisher 行为
- 断言 Topic 命名校验、header 注入、 traceId 透传
- 模拟发送失败，确认异常处理与日志输出

## 8. Roadmap 与风险
| 项目 | 状态 | 风险/备注 |
|------|------|-----------|
| 消费端抽象 | 规划 | 需统一重试、死信、指标策略 |
| 事务 / 延迟消息 | 规划 | 需结合 RocketMQ 事务接口及延迟级别 |
| 序列化插件 | 规划 | 提供可插拔编码（Avro/Protobuf）
| 批量发送 | 规划 | 注意吞吐提升与顺序保证冲突 |

风险：Topic 命名不合规、NameServer/Producer 配置缺失、traceId 未透传导致链路断档。

## 9. 参考资料
- 深度文档：`docs/modules/starters/rocketmq.md`
- Outbox 流程：`docs/modules/ingest/deep-dive.md`
- 错误规范：`docs/standards/platform-error-handling.md`
