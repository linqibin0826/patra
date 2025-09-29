# 模块：patra-spring-boot-starter-rocketmq

为 RocketMQ 交互提供 Papertrace 统一规范封装：

1. 统一消息模型 `PatraMessage<T>`（eventId / traceId / occurredAt / payload）
2. 发布抽象 `PatraMessagePublisher` 隐藏底层模板细节
3. 主题命名规范校验（可配置前缀 / 正则 / Tag 分隔符）
4. 基础重试策略属性占位（供消费端基类使用）
5. 与核心 trace 体系衔接（traceId 透传头 → 载体字段）

---

## 1. 快速开始

```xml
<dependency>
  <groupId>com.papertrace</groupId>
  <artifactId>patra-spring-boot-starter-rocketmq</artifactId>
</dependency>
```

YAML：
```yaml
patra:
  messaging:
    rocketmq:
      enabled: true
      naming:
        namespace: INGEST
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
| `TopicNameValidator` | Topic 规范校验 | 发布前调用 |

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
    publisher.send("INGEST.ORDER.CREATED", PatraMessage.of(evt));
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

示例：`INGEST.ARTICLE.CREATED`

不合规时抛出 `IllegalArgumentException`，杜绝“随机命名”污染。

---

## 7. 扩展点

| 场景 | 做法 |
|------|------|
| 自定义发布逻辑（例如延迟/事务消息） | 自行实现 `PatraMessagePublisher` 并覆盖 Bean |
| 增强命名校验 | Fork/替换 `TopicNameValidator` 或在发送前包装一层校验 |
| 统一 traceId 注入 | 在构建 `PatraMessage` 时集成核心 TraceProvider |

---

## 8. 最佳实践

| 目标 | 建议 |
|------|------|
| 事件可追踪 | 始终填充 traceId，并在消费侧日志输出 eventId + traceId |
| 演进兼容 | payload 使用向前兼容结构（新增字段可空） |
| 排错 | 记录 destination / eventId / traceId / 发生时间 |
| 多环境隔离 | 使用 namespace 前缀 + 不同集群 NameServer 配置 |

---

## 9. Roadmap

| 优先级 | 项目 | 内容 |
|--------|------|------|
| High | 消费端抽象 | 统一监听基类（重试 / DLQ / 指标） |
| High | 事务消息支持 | 包装本地事务 + MQ 二阶段提交流程 |
| Mid | 消费指标 | 每 Topic Lag / 耗时分位统计 |
| Mid | 序列化策略 SPI | Avro / Protobuf 可插拔编码 |
| Low | 批量发送 | 聚合 flush 降低网络开销 |

---

## 10. FAQ

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
