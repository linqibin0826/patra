# 模块：patra-spring-boot-starter-rocketmq

为 RocketMQ 交互提供 Papertrace 统一规范封装：

1. 统一消息模型 `PatraMessage<T>`（eventId/traceId/occurredAt/payload）
2. 发布抽象 `PatraMessagePublisher`：`send`/`sendByChannel`/`sendOrderly`/`sendDelay`
3. 命名规范校验：`TopicNameValidator`（支持 namespace 前缀）+ `GroupNameValidator`
4. 启动期注解校验：`RocketMQListenerAnnotationValidator` 对消费端 `topic/tag/group` fail-fast
5. 与核心 trace 衔接：`PatraMessageFactory` 从 `TraceProvider` 注入 traceId

说明：若未显式配置 `naming.namespace`，系统将使用 Spring `profiles.active` 的第一个值推导命名空间（转大写并剔除非字母数字），并强制所有 Topic 以该前缀开头，启动期和发送前均会严格校验。

---

## 1. 快速开始

```xml
<dependency>
  <groupId>com.papertrace</groupId>
  <artifactId>patra-spring-boot-starter-rocketmq</artifactId>
</dependency>
```

YAML（可选，未配置 namespace 时将自动读取 Spring profiles.active 并转为大写作为 namespace，例如 dev→DEV、uat→UAT；会剔除非字母数字字符）：
```yaml
patra:
  messaging:
    rocketmq:
      enabled: true
      naming:
        namespace: ${spring.profiles.active:}   # 建议留空，自动按 profile 推导（如 dev→DEV）
        topic-pattern: "^[A-Z][A-Z0-9]*(\\.[A-Z0-9]+)*$"  # 默认
        tag-delimiter: "."
      retry:
        max-attempts: 3
        backoff: 1s
```

---

## 2. 自动配置

| 类 | 作用 | 条件 |
|----|------|------|
| `PatraRocketMQAutoConfiguration` | 注册 `PatraMessagePublisher` | 存在 `RocketMQTemplate` 且 enabled=true |
| `PatraRocketMQProperties` | 统一属性入口 | 总是 |
| `RocketMQMessagePublisher` | 实际发送实现 | 通过条件注入 |
| `TopicNameValidator` | Topic 规范校验（含 namespace） | 发布前调用 |
| `GroupNameValidator` | 消费组命名校验 | 启动/监听校验 |
| `RocketMQListenerAnnotationValidator` | 启动期校验注解参数 | BeanPostProcessor |
| `DestinationBuilder` | `channel -> destination` 解析 | 通用工具 |
| `PatraMessageFactory` | 从 TraceProvider 注入 traceId | 可选依赖 core |
| `ChannelRegistry` | 统一 channel 注册与校验（可选白名单） | 注解/接口式注册 |

---

## 3. 配置属性 (`patra.messaging.rocketmq`)

| 节点 | 字段 | 默认 | 说明 |
|------|------|------|------|
| 根 | enabled | true | 模块开关 |
| naming | namespace | (null) | 业务命名空间（可用于 Topic 约束扩展） |
| naming | topic-pattern | ^[A-Z][A-Z0-9]*(\.[A-Z0-9]+)*$ | Topic 合法正则（大写+点分段） |
| naming | tag-delimiter | . | Tag 切割符 |
| retry | max-attempts | 3 | 消费（后续扩展）最大尝试次数 |
| retry | backoff | 1s | 指数退避初值（后续消费侧用） |

> 当前版本仅对发布侧使用命名 & 日志规范；消费重试策略字段为后续基类预留。

---

## 4. 消息模型 `PatraMessage<T>`

| 字段 | 说明 |
|------|------|
| eventId | 事件唯一标识（默认 UUID） |
| traceId | 分布式追踪标识（可由上游填充） |
| occurredAt | 事件产生时间（默认 `Instant.now()`） |
| payload | 业务载荷 |

构建：
```java
PatraMessage<OrderCreated> msg = PatraMessage.of(new OrderCreated(orderId));
```
或：
```java
PatraMessage<OrderCreated> msg = PatraMessage.<OrderCreated>builder()
  .traceId(currentTraceId)
  .payload(new OrderCreated(orderId))
  .build();
```

---

## 5. 发布 API

```java
@RequiredArgsConstructor
@Service
class OrderEventPublisher {
  private final PatraMessagePublisher publisher;
  public void publish(OrderCreated evt) {
    // 推荐：领域通道目录（ChannelKey）+ 按规范构建 destination
    publisher.sendByChannel(IngestChannels.TASK_READY.channel(), PatraMessage.of(evt));
  }
}
```

内部步骤：
1. 校验 Topic 命名（`TopicNameValidator`）
2. 构造 Spring `Message`，设置头：eventId / traceId / occurredAt
3. 调用 `RocketMQTemplate.convertAndSend`

---

## 6. Topic 命名规范

默认正则：`^[A-Z][A-Z0-9]*(\.[A-Z0-9]+)*$`

建议：
- 顶级段：业务域（如 INGEST / REGISTRY）
- 次级段：聚合或功能（ARTICLE / TASK）
- 末级：事件类型（CREATED / UPDATED）

示例：`INGEST.ARTICLE.CREATED`；channel：`ingest.article.created` -> destination：`INGEST.ARTICLE:CREATED`

不合规时抛出 `IllegalArgumentException`，杜绝“随机命名”污染。

---

## 7. 消费组命名规范（最终采用）

- 正则：`^[a-z][a-z0-9\-]*$`
- 规则：`svc-{service}-{consumer}-cg`
  - `service`：微服务名（如 `ingest`、`registry`）
  - `consumer`：消费职责的简明名词短语（如 `relay`、`task-ready`、`article-indexer`）
- 示例：
  - `svc-ingest-relay-cg`
  - `svc-registry-sync-cg`

启动时会基于该正则校验消费组；不合规直接 fail-fast。

---

## 8. 消费端监听与校验

编写监听器（适配层 `patra-{service}-adapter`）：

```java
@Slf4j
@Component
@RocketMQMessageListener(
  consumerGroup = "svc-ingest-relay-cg",
  topic = "INGEST.TASK",
  selectorExpression = "READY"
)
public class TaskReadyListener extends AbstractPatraMessageListener<TaskReadyMessage> {
  @Override
  protected void handleMessage(PatraMessage<TaskReadyMessage> message) {
    log.info("收到任务：eventId={} traceId={}", message.getEventId(), message.getTraceId());
    // 调用应用服务 ...
  }
}
```

启动时将自动校验 topic/tag/group 命名；不合规直接抛出异常阻止启动。

### 8.1 自定义消费注解（运行时注册版）

为降低维护成本并保证强一致性，Starter 提供 `@Consumes` 注解与 `PatraMessageHandler<T>` 接口：

```java
@Slf4j
@Component
@Consumes(channelEnum = IngestChannels.class,  // 领域通道目录（强类型）
          channelName = "TASK_READY",        // 枚举常量名，避免手写字符串通道
          consumer = "relay",                // 职责名，用于生成 group：svc-{service}-{consumer}-cg
          mode = ConsumerMode.CONCURRENT,     // 并发或顺序
          concurrency = 2)                    // 建议并发度
public class TaskReadyLoggingHandler implements PatraMessageHandler<TaskReadyMessage> {
  @Override
  public void handle(PatraMessage<TaskReadyMessage> message) throws Exception {
    log.info("recv event: id={} traceId={} payload={}", message.getEventId(), message.getTraceId(), message.getPayload());
  }
}
```

启动期：框架按规范将 `channel` → `topic/tag/group` 映射并注册监听容器，自动补齐 `namespace` 前缀（基于 `spring.profiles.active`）。
强校验：
- 目录一致性：`channel` 与 Handler 泛型 `T` 必须匹配领域目录绑定的载荷类型；
- 命名规范：`topic/tag/group` 全量校验（含 namespace 前缀与消费组正则）。

说明：`consumer` 值是“职责名”，用于区分同一服务内不同角色的消费组（如 `relay`/`audit`），形成独立位点与观测标签。

## 9. Channel 注册与白名单（约束发送/接收）

为避免“随意新增 channel”，Starter 提供统一注册器 `ChannelRegistry`，支持两种注册方式（二选一或同时）：

- 注解方式（推荐）：在任意 Spring Bean 上声明允许的 channel 集合
  ```java
  @Component
  @PatraMessagingChannels({
      "ingest.task.ready",
      "ingest.article.created"
  })
  class IngestMessagingChannels {}
  ```
- 接口方式：提供一个 `ChannelCatalog` Bean 返回集合
  ```java
  @Configuration
  class IngestChannelCatalog implements ChannelCatalog {
    public Collection<String> channels() {
      return Set.of("ingest.task.ready");
    }
  }
  ```

校验策略：
- 统一格式：`^[a-z0-9]+(\.[a-z0-9]+)+$`（至少三段、小写点分段）；
- 可选 domain：`patra.messaging.channels.domain=ingest`（要求第一段等于该 domain）；
- 白名单强制：`patra.messaging.channels.enforce=true`（默认 true）。若未注册任何 channel，将仅做格式校验并告警。

发送路径：`sendByChannel("domain.resource.event")` 会先通过 `ChannelRegistry.validate(channel)` 再解析为目的地并发送。

## 10. 错误处理与异常策略

- 运行期发布入口（`send` / `sendByChannel` / `sendOrderly` / `sendDelay`）：
  - 命名或参数不合规将抛出 `ApplicationException(UNPROCESSABLE/422)`，错误消息包含 `destination`/`channel` 等上下文；
  - Web/Feign 适配层会统一输出 `ProblemDetail`，错误码前缀来自 `patra.error.context-prefix`（缺省 `UNKNOWN`）。
- 启动期/配置期校验（`@RocketMQMessageListener` 注解参数）：
  - 不合规直接抛出 `IllegalArgumentException`，fail‑fast 终止启动，便于定位配置问题。

## 11. 扩展点

| 场景 | 做法 |
|------|------|
| 自定义发布逻辑（例如延迟/事务消息） | 自行实现 `PatraMessagePublisher` 并覆盖 Bean |
| 增强命名校验 | Fork/替换 `TopicNameValidator` 或在发送前包装一层校验 |
| 统一 traceId 注入 | 使用 `PatraMessageFactory`（需引入 core-starter） |

---

## 12. 最佳实践

| 目标 | 建议 |
|------|------|
| 事件可追踪 | 始终填充 traceId，并在消费侧日志输出 eventId + traceId |
| 演进兼容 | payload 使用向前兼容结构（新增字段可空） |
| 排错 | 记录 destination / eventId / traceId / 发生时间 |
| 多环境隔离 | 使用 namespace 前缀 + 不同集群 NameServer 配置 |

---

## 13. Roadmap

| 优先级 | 项目 | 内容 |
|--------|------|------|
| High | 消费端抽象 | 统一监听基类（重试 / DLQ / 指标） |
| High | 事务消息支持 | 包装本地事务 + MQ 二阶段提交流程 |
| Mid | 消费指标 | 每 Topic Lag / 耗时分位统计 |
| Mid | 序列化策略 SPI | Avro / Protobuf 可插拔编码 |
| Low | 批量发送 | 聚合 flush 降低网络开销 |

---

## 14. FAQ

| 问题 | 回答 |
|------|------|
| 为什么再次封装 publisher? | 隐藏模板细节 + 强制注入规范（命名/头） |
| traceId 如何获得? | 由上游 Web / Feign starter 提供 TraceProvider 注入 | 
| 命名校验能否关闭? | 目前不支持，若需要可自定义 Publisher 跳过校验 |
| 如何做延迟消息? | 自定义实现 Publisher 并调用 `rocketMQTemplate.syncSend` 指定 delay level |

---

## 11. 参考源码

| 位置 | 说明 |
|------|------|
| `config/PatraRocketMQAutoConfiguration.java` | 自动配置入口 |
| `config/PatraRocketMQProperties.java` | 属性定义 |
| `publisher/RocketMQMessagePublisher.java` | 默认发送实现 |
| `model/PatraMessage.java` | 通用消息载体 |
| `support/TopicNameValidator.java` | Topic 校验逻辑 |

---

如需新增：请同时说明使用场景 / 与现有能力差异 / 期望指标。
