# patra-spring-boot-starter-rocketmq

RocketMQ 统一封装 Sta### Channel 注册（约束"可用 channel"）
推荐使用 `ChannelCatalog` 接口方式，从领域枚举自动提取：
```java
@Configuration
public class IngestMessagingConfiguration {
    @Bean
    public ChannelCatalog ingestChannelCatalog() {
        return () -> Arrays.stream(IngestChannels.values())
                          .map(ChannelKey::channel)
                          .collect(Collectors.toSet());
    }
}
```
或使用注解方式（适用于简单场景）：
```java
@Component
@PatraMessagingChannels({"ingest.task.ready"})
class ChannelRegistration {}
```
可选配置：息模型、命名/分组规范校验、目的地构建与发布抽象。

## 1. 模块定位
- **服务/组件作用**：标准化 RocketMQ 消息的 header/payload、Topic 命名与发布 API
- **主要消费者**：`patra-ingest` Outbox Relay、未来事件驱动服务
- **架构边界**：Starter 负责封装模板和规范约束；消费端基类将在后续版本提供

## 2. 核心能力
- 消息模型：`PatraMessage<T>` 统一 `eventId`/`traceId`/`occurredAt`
- 发布抽象：`PatraMessagePublisher` 支持 `send`/`sendByChannel`/`sendOrderly`/`sendDelay`
- 命名规范：`TopicNameValidator`（Topic 前缀/正则/namespace）+ `GroupNameValidator`（消费组）
- 启动校验：`RocketMQListenerAnnotationValidator` 对 `@RocketMQMessageListener` 的 `topic/tag/group` 进行 fail-fast 校验
- 目的地构建：`DestinationBuilder.fromChannel("domain.resource.event") -> TOPIC:TAG`
- Channel 注册：`ChannelRegistry` 统一收集与校验允许的 channel（注解/接口式），`sendByChannel` 发送前进行校验
- Trace 衔接：`PatraMessageFactory` 优先从 `TraceProvider` 注入 `traceId`

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
        retry:
          max-attempts: 3
          backoff: 1s
  rocketmq:
    name-server: ${MQ_NAMESERVER}
    producer.group: ingest-producer
  ```
- 发布示例：`publisher.send("INGEST.ARTICLE.CREATED", PatraMessage.of(payload))`
- Channel 示例：`publisher.sendByChannel("ingest.task.ready", PatraMessage.of(payload)) // -> INGEST.TASK:READY`
 - 消费组命名：`svc-{service}-{consumer}-cg`（正则 `^[a-z][a-z0-9\-]*$`），示例：`svc-ingest-relay-cg`

### Channel 注册（约束“可用 channel”）
无需 YAML，推荐在模块内用注解声明：
```java
@Component
@PatraMessagingChannels({"ingest.task.ready"})
class IngestMessagingChannels {}
```
或提供一个 `ChannelCatalog` Bean 返回集合。可选配置：
```yaml
patra:
  messaging:
    channels:
      enforce: true         # 默认 true；true 时要求在注册表内
      domain: ingest        # 可选；要求 channel 第一段等于该值
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

## 8. 参考资料
- 深度文档：`docs/modules/starters/rocketmq.md`
- Outbox 流程：`docs/modules/ingest/deep-dive.md`
- 错误规范：`docs/standards/platform-error-handling.md`
