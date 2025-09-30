# patra-spring-boot-starter-rocketmq

RocketMQ 统一封装：强类型 Channel、消息模型、发布/消费抽象、命名规范校验。

## 1. 模块定位

- **服务/组件作用**：标准化 RocketMQ 消息模型、强制类型安全的 Channel 管理、统一命名规范
- **主要消费者**：`patra-ingest`、`patra-registry` 及其他微服务
- **架构边界**：Starter 负责封装底层 RocketMQ 细节；业务层在 domain 定义 ChannelKey 枚举；提供 Messag## 10. Roadmap 与风险

| 项目 | 状态 | 风险/备注 |
|------|------|--------|
| 灵活 Channel 配置 | ✅ 已完成 | 支持字符串和枚举两种方式 |
| 消息追踪 | ✅ 已完成 | TraceId 自动注入 |
| 消费重试 | ✅ 已完成 | 可配置次数 |
| 事务消息 | 📋 规划中 | 需封装 RocketMQ 事务 API |
| 批量发送 | 📋 规划中 | 提升吞吐量 |
| 消费幂等 | 📋 规划中 | 基于 eventId 去重 |

**风险点**：
- Topic 命名不合规导致启动失败
- NameServer 配置错误导致连接失败
- TraceId 未透传导致链路断档
- Channel 字符串拼写错误可能导致消息无法送达（建议定义常量）端实现

## 2. 核心能力

- **灵活 Channel 配置**：支持字符串形式的 Channel（domain.resource.event），简化配置
- **统一消息模型**：`Message<T>` 封装 eventId/traceId/occurredAt + 业务载荷
- **TraceId 自动注入**：集成 TraceProvider SPI，透传分布式追踪上下文
- **命名规范校验**：Topic/Tag/ConsumerGroup 强制命名规范，避免运维混乱
- **灵活发布**：支持普通/顺序/延迟消息，可通过 Channel 或 Destination 发送
- **简化消费**：`@MessageListener` 注解驱动，直接指定 channel 字符串，自动注册监听容器

## 3. 分层结构与依赖

- 主要包：`autoconfigure`（自动配置）、`core`（核心模型）、`publisher`（发布）、`consumer`（消费）、`naming`（命名）、`validation`（校验）
- 依赖：`patra-spring-boot-starter-core`（TraceProvider）、`rocketmq-spring-boot-starter`

## 4. 运行与配置

### 4.1 Maven 引入

```xml
<dependency>
  <groupId>com.papertrace</groupId>
  <artifactId>patra-spring-boot-starter-rocketmq</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 4.2 基本配置

```yaml
patra:
  messaging:
    rocketmq:
      enabled: true
      naming:
        # 建议留空，自动按 Spring profiles.active 推导（dev→DEV）
        namespace: ${spring.profiles.active:}
        topic-pattern: "^[A-Z][A-Z0-9]*(\\.[A-Z0-9]+)*$"
        consumer-group-pattern: "^[a-z][a-z0-9\\-]*$"
      retry:
        max-attempts: 3

rocketmq:
  name-server: ${MQ_NAMESERVER}
  producer:
    group: ${spring.application.name}-producer
```

## 5. 使用指南

### 5.1 定义 Channel 枚举

在领域层（`domain/messaging`）定义 ChannelKey 枚举：

```java
package com.patra.ingest.domain.messaging;

import com.patra.starter.rocketmq.core.channel.ChannelKey;

public enum IngestChannels implements ChannelKey {
    TASK_READY("ingest", "task", "ready"),
    TASK_COMPLETED("ingest", "task", "completed"),
    ARTICLE_PARSED("ingest", "article", "parsed");

    private final String domain;
    private final String resource;
    private final String event;

    IngestChannels(String domain, String resource, String event) {
        this.domain = domain;
        this.resource = resource;
        this.event = event;
    }

    @Override
    public String domain() { return domain; }
    
    @Override
    public String resource() { return resource; }
    
    @Override
    public String event() { return event; }
}
```

**说明**：
- 三段式标识：domain（领域/服务名）、resource（资源）、event（事件）
- 全部小写，框架自动转为大写 Topic/Tag（如 `INGEST.TASK:READY`）
- 启动时自动扫描并注册到白名单

### 5.2 发布消息

```java
@Service
public class TaskService {
    
    @Autowired
    private MessagePublisher publisher;
    
    public void notifyTaskReady(TaskReadyPayload payload) {
        // 方式一：通过 Channel 发送（推荐，类型安全）
        Channel channel = Channel.of(IngestChannels.TASK_READY);
        Message<TaskReadyPayload> message = Message.of(payload);
        publisher.sendByChannel(channel, message);
        
        // 方式二：直接指定 Destination
        Destination destination = Destination.of("INGEST.TASK", "READY");
        publisher.send(destination, message);
    }
    
    // 顺序消息：按 hashKey 路由到同一队列
    public void sendOrderly(String orderId, OrderPayload payload) {
        Destination destination = Destination.of("INGEST.ORDER", "UPDATED");
        publisher.sendOrderly(destination, Message.of(payload), orderId);
    }
    
    // 延迟消息：delayLevel 1-18（1=1s, 2=5s, ..., 13=1h, 18=2h）
    public void sendDelayed(TaskPayload payload) {
        Destination destination = Destination.of("INGEST.TASK", "TIMEOUT");
        publisher.sendDelayed(destination, Message.of(payload), 13); // 1 小时后
    }
}
```

### 5.3 消费消息

**方式一：直接指定 channel 字符串（推荐）**

```java
@Component
@MessageListener(
    channel = "ingest.task.ready",   // 直接指定 channel 字符串（格式：domain.resource.event）
    consumer = "relay",              // 消费者标识（用于生成消费组）
    mode = ConsumeMode.CONCURRENT,   // 并发消费
    concurrency = 2                  // 并发度
)
public class TaskReadyHandler implements MessageHandler<TaskReadyPayload> {
    
    @Override
    public void handle(Message<TaskReadyPayload> message) throws Exception {
        log.info("处理任务就绪事件: eventId={}, traceId={}", 
                message.getEventId(), message.getTraceId());
        
        TaskReadyPayload payload = message.getPayload();
        // 业务逻辑
        // 异常会自动触发重试
    }
}
```

**方式二：使用枚举常量（适合需要编译期检查的场景）**

如果你的领域层定义了 ChannelKey 枚举，可以引用枚举常量：

```java
import static com.patra.ingest.domain.messaging.IngestChannels.TASK_READY;

@Component
@MessageListener(
    channel = TASK_READY.channel(),  // 使用枚举的 channel() 方法
    consumer = "relay",
    mode = ConsumeMode.CONCURRENT,
    concurrency = 2
)
public class TaskReadyHandler implements MessageHandler<TaskReadyPayload> {
    // ... 处理逻辑
}
```

**顺序消费示例**：

```java
@MessageListener(
    channel = "ingest.order.updated",  // 直接指定 channel
    consumer = "processor",
    mode = ConsumeMode.ORDERLY,  // 顺序消费
    concurrency = 1              // 顺序消费必须为 1
)
public class OrderUpdatedProcessor implements MessageHandler<OrderPayload> {
    @Override
    public void handle(Message<OrderPayload> message) throws Exception {
        // 按 hashKey（订单 ID）顺序处理
    }
}
```

**说明**：
- **channel 格式**：必须符合 `domain.resource.event` 格式（至少 3 段，小写，点分段）
- **框架自动解析**：将 channel 解析为 domain/resource/event，并自动转换为 Topic/Tag
- **无需枚举依赖**：消费方无需引入发布方的枚举类，只需知道 channel 字符串即可
- **格式校验**：启动时会验证 channel 格式，格式错误会抛出异常并阻止启动

### 5.4 命名规范

**Topic 命名**：`NAMESPACE.DOMAIN.RESOURCE`
- 示例：`DEV.INGEST.TASK`
- namespace 从环境推导（dev→DEV，prod→PROD）
- domain 和 resource 从 Channel 提取并转大写

**Tag 命名**：`EVENT`
- 示例：`READY`, `COMPLETED`
- 从 Channel 的 event 部分提取并转大写

**消费组命名**：`svc-{service}-{consumer}-cg`
- 示例：`svc-ingest-relay-cg`
- 小写，允许连字符
- service 从 `spring.application.name` 获取
- consumer 从 `@MessageListener` 的 `consumer` 属性获取

## 6. 观测与运维

### 6.1 日志格式

```
[CONSUME][START] channel=ingest.task.ready, group=svc-ingest-relay-cg, eventId=xxx, traceId=yyy
[CONSUME][SUCCESS] channel=ingest.task.ready, group=svc-ingest-relay-cg, costMs=123, eventId=xxx
[CONSUME][FAIL] channel=ingest.task.ready, group=svc-ingest-relay-cg, costMs=45, error=...
```

### 6.2 监控建议

- 结合 RocketMQ Console 监控消费位点和延迟
- 配置 Prometheus Exporter 采集指标（消息量、延迟、失败率）
- SkyWalking 自动追踪跨服务链路（traceId 透传）
- 关注 DLQ（死信队列）消息

## 7. 错误处理

- **发布失败**：抛出 `ApplicationException(UNPROCESSABLE/422)`，包含详细上下文（destination/channel/原因）
- **命名不合规**：启动期 fail-fast（抛出 `IllegalArgumentException`）
- **Channel 格式错误**：消费者启动时验证 channel 格式（必须至少 3 段），格式错误会抛出 `IllegalArgumentException`
- **消费失败**：根据 `retry.max-attempts` 配置重试，超过次数进入死信队列

## 8. 测试策略

```java
@SpringBootTest
class MessagePublisherTest {
    
    @Autowired
    private MessagePublisher publisher;
    
    @Test
    void shouldSendMessageByChannel() {
        Channel channel = Channel.of(TestChannels.TEST_EVENT);
        Message<String> message = Message.of("test payload");
        
        assertDoesNotThrow(() -> publisher.sendByChannel(channel, message));
    }
    
    @Test
    void shouldValidateChannelWhitelist() {
        Channel invalidChannel = Channel.of("invalid", "channel", "test");
        Message<String> message = Message.of("test");
        
        assertThrows(ApplicationException.class, 
            () -> publisher.sendByChannel(invalidChannel, message));
    }
}
```

## 9. 变更历史

### v0.1.0 (2025-10-01)
- **简化消费者配置**：移除枚举强制依赖，支持直接使用 channel 字符串
  - 修改 `@MessageListener` 注解，`channel` 属性改为接受字符串
  - 移除 `ConsumerBootstrap.resolveChannelKey()` 方法
  - 添加启动时的 channel 格式验证（必须至少 3 段）
  - 消费方无需引入发布方的枚举类，降低耦合
- **移除白名单校验**：删除 `ChannelRegistry` 和 `enforce-whitelist` 配置
  - Channel 现在只能通过 `ChannelKey` 创建，保证类型安全
  - 简化配置，减少启动时扫描开销

## 10. Roadmap 与风险

| 项目 | 状态 | 风险/备注 |
|------|------|-----------|
| 灵活 Channel 配置 | ✅ 已完成 | 支持字符串和枚举两种方式 |
| 自动白名单 | ✅ 已完成 | 从枚举自动提取，可配置关闭 |
| 消息追踪 | ✅ 已完成 | TraceId 自动注入 |
| 消费重试 | ✅ 已完成 | 可配置次数 |
| 事务消息 | 📋 规划中 | 需封装 RocketMQ 事务 API |
| 批量发送 | 📋 规划中 | 提升吞吐量 |
| 消费幂等 | 📋 规划中 | 基于 eventId 去重 |

**风险点**：
- Topic 命名不合规导致启动失败
- NameServer 配置错误导致连接失败
- TraceId 未透传导致链路断档
- Channel 字符串拼写错误可能导致消息无法送达（建议定义常量）

## 11. 参考资料

- Starters 指南：各 Starter 模块 README（`patra-spring-boot-starter-*`、`patra-spring-cloud-starter-feign`）
- Outbox 流程：`docs/modules/ingest/deep-dive.md`
- 错误规范：`docs/standards/platform-error-handling.md`
- 消息通道约定：`patra-common/src/main/java/com/patra/common/messaging/ChannelKey.java`
