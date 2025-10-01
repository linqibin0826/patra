# patra-spring-boot-starter-rocketmq

Papertrace 项目的 RocketMQ 统一消息组件，提供面向领域事件（Domain Events）的消息发布与消费能力。

## 1. 模块定位
- 服务/组件作用：标准化 RocketMQ 消息模型、Channel → Destination 转换、声明式消费者注册、命名规范校验
- 主要消费者：`patra-ingest`、`patra-registry` 等微服务
- 架构边界：遵循六边形架构/DDD；Starter 仅封装基础能力，业务侧实现 `MessageHandler<T>` 即可，不在 Starter 中编写业务逻辑

## 2. 核心能力
- 统一消息模型：`Message<T>` 封装 `eventId/traceId/occurredAt/payload`；`MessageFactory` 自动注入 TraceId
- Channel 抽象：`domain.resource.event`（小写）；`ChannelDestinationConverter` 转为 RocketMQ `TOPIC:TAG`（大写）
- 命名空间隔离：`NamespaceResolver` 从配置或 `spring.profiles.active` 推导 `DEV/TEST/PROD` 等命名空间
- 声明式消费：`@MessageListener(channel, consumer, mode, concurrency, selector)` 自动注册监听容器
- 发布抽象：`MessagePublisher#send|sendByChannel|sendOrderly|sendDelayed`
- 命名规范校验：`TopicValidator`、`GroupValidator` 强制 Topic/Group 命名符合规范

> 注意：`Destination`（字符串形式）要求显式使用大写 `TOPIC:TAG`；若使用 `Channel`，框架会自动转换并大写化。

## 3. 分层结构与依赖
- 主要包：`autoconfigure`（自动配置）、`core`（Channel/Destination/Message）、`publisher`、`consumer`、`naming`、`validation`
- 运行依赖：
  - `patra-spring-boot-starter-core`（提供 `TraceProvider` 等 SPI）
  - `org.apache.rocketmq:rocketmq-spring-boot-starter`

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
  spring:
    application:
      name: ingest   # 用于生成消费组：svc-{service}-{consumer}-cg
  rocketmq:
    name-server: 127.0.0.1:9876
  patra:
    messaging:
      rocketmq:
        enabled: true
        naming:
          namespace: DEV            # 可选；不配置时从 spring.profiles.active 推导（转为大写并清洗）
        retry:
          max-attempts: 3           # 预留：消费异常重试次数（当前用于文档约束）
  logging:
    level:
      com.patra.starter.rocketmq: INFO
      org.apache.rocketmq: INFO
  ```
- 命名规则（默认）：
  - Topic：`^[A-Z][A-Z0-9]*(\.[A-Z0-9]+)*$`（必须以命名空间前缀开头，如 `DEV.INGEST.TASK`）
  - Group：`^[a-z][a-z0-9\-]*$`（示例：`svc-ingest-relay-cg`）

## 5. 使用指南
### 5.1 发送消息
- 推荐使用 `Channel` 发送（自动转换命名空间与大小写）：
  ```java
  import com.patra.starter.rocketmq.core.Channel;
  import com.patra.starter.rocketmq.core.message.Message;
  import com.patra.starter.rocketmq.core.message.MessageFactory;
  import com.patra.starter.rocketmq.publisher.MessagePublisher;

  @Service
  public class TaskService {
      private final MessagePublisher publisher;
      private final MessageFactory messageFactory;

      public TaskService(MessagePublisher publisher, MessageFactory messageFactory) {
          this.publisher = publisher;
          this.messageFactory = messageFactory;
      }

      public void publishReady(TaskReadyPayload payload) {
          // Channel：domain.resource.event（小写）
          Channel channel = new Channel("ingest.task.ready");
          // MessageFactory 自动注入 traceId
          Message<TaskReadyPayload> msg = messageFactory.create(payload);
          publisher.sendByChannel(channel, msg);
      }
  }
  ```
- 如需直接使用 Destination（需显式大写）：
  ```java
  import com.patra.starter.rocketmq.core.destination.Destination;
  // 形如 DEV.INGEST.TASK:READY
  publisher.send(Destination.parse("DEV.INGEST.TASK:READY"), Message.of(payload));
  ```

### 5.2 声明式消费
- 使用 `@MessageListener` 标注消费者，框架自动注册并启动监听容器：
  ```java
  import com.patra.starter.rocketmq.consumer.ConsumeMode;
  import com.patra.starter.rocketmq.consumer.MessageHandler;
  import com.patra.starter.rocketmq.consumer.MessageListener;
  import com.patra.starter.rocketmq.core.message.Message;

  @MessageListener(
      channel = "ingest.task.ready",  // domain.resource.event（小写）
      consumer = "relay",              // 生成 group：svc-{service}-relay-cg（小写）
      mode = ConsumeMode.CONCURRENT,   // 或 ORDERLY
      concurrency = 2                  // 并发消费线程数（仅 CONCURRENT 生效）
  )
  public class TaskReadyHandler implements MessageHandler<TaskReadyPayload> {
      @Override
      public void handle(Message<TaskReadyPayload> message) {
          // 业务处理，确保幂等（建议基于 eventId 去重）
      }
  }
  ```
- 选择表达式 `selector`（可选）：默认等于 `event` 大写（如 `READY`）。不支持 `OR` 表达式，需为大写点分段（如 `READY.PARTIAL`）。

### 5.3 顺序与延迟消息
- 顺序消息：
  ```java
  String hashKey = String.valueOf(orderId);
  publisher.sendOrderly(Destination.parse("DEV.ORDER.PAYMENT:CREATED"), Message.of(payload), hashKey);
  ```
- 延迟消息（1-18 级）：
  ```java
  publisher.sendDelayed(Destination.parse("DEV.INGEST.TASK:RETRY"), Message.of(payload), 3);
  ```

## 6. 观测与运维
- 日志关键点：
  ```
  [DEBUG] 发送普通消息: destination=DEV.INGEST.TASK:READY, eventId=...
  [INFO ] 消息发送成功: destination=DEV.INGEST.TASK:READY, eventId=..., traceId=...
  [INFO ] [CONSUME][START] channel=ingest.task.ready, group=svc-ingest-relay-cg, eventId=..., traceId=...
  [INFO ] [CONSUME][SUCCESS] channel=ingest.task.ready, group=svc-ingest-relay-cg, costMs=123, eventId=...
  ```
- TraceId：由 `MessageFactory` 注入，需引入 `patra-spring-boot-starter-core` 并正确启用追踪（如 SkyWalking Agent）
- 排障建议：检查 `rocketmq.name-server`、命名空间前缀是否匹配、Topic/Group 是否符合正则

## 7. Roadmap 与风险
| 项目 | 状态 | 风险/备注 |
|------|------|-----------|
| 事务消息封装 | 规划 | 需要对接 RocketMQ 事务 API，注意幂等与补偿 |
| 批量发送 | 规划 | 需评估吞吐与顺序性策略 |
| 消费幂等示例 | 规划 | 基于 eventId 去重或分布式锁 |

主要风险：
- `Destination` 使用小写将触发命名校验失败；优先使用 `Channel` 发送
- 命名空间未对齐导致跨环境错投；请固定 `namespace` 或校验 `profiles`
- `channel` 字符串拼写错误导致无法路由；建议集中管理为常量或 `ChannelKey`

## 8. 参考资料
- 同类 Starter：`patra-spring-boot-starter-core/README.md`、`patra-spring-boot-starter-web/README.md`、`patra-spring-cloud-starter-feign/README.md`
- Apache RocketMQ 官方文档：https://rocketmq.apache.org/
- RocketMQ Spring Boot Starter：https://github.com/apache/rocketmq-spring
