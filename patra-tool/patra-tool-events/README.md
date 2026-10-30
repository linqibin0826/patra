# patra-tool-events

## 能力

服务之间的事件：发布方可靠地把领域事件外送到 RabbitMQ，消费方不重复处理、处理失败有去处。设计与取舍见 `docs/patra/specs/2026-10-30-event-module-design.md`。

- **发布登记（Outbox）**：Spring Modulith 的 JDBC 事件发布登记。事务里 `publishEvent` 时写一行 `event_publication`，和业务数据同成同败；提交后由 Modulith 的 AMQP 外送器发出，成功了这一行才算完成。
- **等确认**：`ConfirmingRabbitMessagingTemplate` 替换 Boot 的 `RabbitMessagingTemplate`，每次发送同步等 RabbitMQ 的 publisher confirm。nack、没有队列可投（消息被退回）、超时都算失败，登记行留着。Modulith 默认的外送（`module-listener` 模式）不等确认，这是本模块补的一步；2.1 的 `outbox` 模式要 Namastack 或 JobRunr 驱动，且不查超时与退回，不用。
- **补投**：XXL-Job 任务 `eventPublicationResubmitJob` 每分钟把早于 1 分钟的未完成登记行重新外送（正在发送、10 分钟内有动静的不重复派发），顺带把补投途中崩溃、在 `RESUBMITTED` 卡了 10 分钟以上的行改回 `FAILED` 再补；尝试 10 次以上的行打 WARN。Modulith 每轮会打一条 INFO「No stale publications of status RESUBMITTED found」，属正常。
- **消费端**：`ProcessedEventPort`（接口在 `patra-tool-core`）在业务事务里按（队列, 事件 ID）去重；`EventQueue` 声明队列与它的死信交换机、死信队列；ack、重试、prefetch 有默认值。

投递是**至少一次**：补投可能把已经送到的事件再发一遍，消费方必须去重。

## 配置（前缀 `patra.events`）

| 键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `publisher.confirm-timeout` | Duration | `5s` | 等 broker 确认的上限 |
| `resubmit.min-age` | Duration | `1m` | 只补投发布时间早于它的登记行 |
| `consumer.trusted-packages` | List<String> | 空 | 按消息头 `__TypeId__` 反序列化时信任的包，按包名精确匹配（不含子包）。一个监听类用多个 `@RabbitHandler` 时要写发布方集成事件所在的包；监听方法参数是具体类型时不用配 |

## 出哪些默认值

由 `EventsDefaultsPostProcessor` 以 `defaultProperties` 并入，服务 yml、Nacos、环境变量都能覆盖。

| 键 | 默认值 | 为什么 |
|---|---|---|
| `spring.modulith.events.jdbc.schema-initialization.enabled` | `false` | 表由 Flyway 建 |
| `spring.modulith.events.completion-mode` | `delete` | 发完即删，表里只剩积压；identity 的安全邮件令牌不久留 |
| `spring.modulith.events.registry-trigger-annotation` | `org.springframework.modulith.events.ApplicationModuleListener` | 只登记 Modulith 的外送器；服务里普通的 `@TransactionalEventListener` 不进登记表、不被补投 |
| `spring.modulith.events.rabbitmq.enable-json` | `false` | JSON 转换器由本模块统一提供 |
| `spring.rabbitmq.publisher-confirm-type` | `correlated` | 等确认的前提，关掉启动即失败 |
| `spring.rabbitmq.publisher-returns` | `true` | 识别没有队列可投，关掉启动即失败 |
| `spring.rabbitmq.connection-timeout` | `5s` | broker 不可达时尽快失败 |
| `management.health.rabbit.enabled` | `false` | broker 停着时业务照常提交、事件留给补投，服务不该判成 DOWN（部署会被回滚）；积压看登记表行数 |
| `spring.rabbitmq.listener.simple.acknowledge-mode` | `auto` | 处理成功后才 ack（RabbitMQ 意义上的手动确认） |
| `spring.rabbitmq.listener.simple.prefetch` | `10` | 少囤消息 |
| `spring.rabbitmq.listener.simple.default-requeue-rejected` | `false` | 失败的消息进死信，不回队列打转 |
| `spring.rabbitmq.listener.simple.retry.enabled` / `max-retries` / `initial-interval` / `multiplier` / `max-interval` | `true` / `3` / `1s` / `2` / `10s` | 本地退避重试 1、2、4 秒，之后进死信 |

另外 `RabbitTemplate` 一律带 `mandatory`（自动配置里直接设，不能关）。

## 发布方怎么接

1. boot 模块 `implementation(project(":patra-tool:patra-tool-events"))`，配好 RabbitMQ 连接（地址与账号按 `patra-infra` 的密钥规则）。
2. Flyway 建 `event_publication`：照抄 `src/integrationTest/resources/db/migration/V1__create_event_publication.sql`（Modulith 固定的表名与列，不能改）。完成模式改成 `archive` 的服务另建同结构的 `event_publication_archive`；改成 `update` 的服务要自己定期清理已完成的行（`CompletedEventPublications.deletePublicationsOlderThan`）。
3. 领域事件带 `UUID eventId`，**在创建时生成**。登记表存的是领域事件，补投时会重新映射；在映射里生成 ID，每次补投都会变，消费端就去不了重。
4. 集成事件 record 放发布方 `-api` 模块，实现 `IntegrationEvent`，`eventId` 取自领域事件。模块把它写进 AMQP `messageId`。
5. boot 层声明交换机与外送配置（领域层不贴 `@Externalized`）：

   ```java
   @Bean
   TopicExchange interactionEvents() {
     return ExchangeBuilder.topicExchange("interaction.events").durable(true).build();
   }

   @Bean
   EventExternalizationConfiguration eventExternalizationConfiguration() {
     return EventExternalizationConfiguration.externalizing()
         .selectByType(CommentPublishedEvent.class)
         .route(
             CommentPublishedEvent.class,
             event -> RoutingTarget.forTarget("interaction.events").andKey("comment.published"))
         .mapping(
             CommentPublishedEvent.class,
             event -> new CommentPublished(event.eventId(), /* 只放别的服务需要的事实 */))
         .build();
   }
   ```

6. 事务里照常 `events.publishEvent(领域事件)`。
7. 服务代码里不写 `@ApplicationModuleListener`：登记表只认带它的监听器（外送器自己带着）。普通的 `@TransactionalEventListener(AFTER_COMMIT)` 照 Spring 原来的行为走，不进登记表。
8. xxl-job-admin 登记 `eventPublicationResubmitJob`：CRON `0 * * * * ?`，路由「第一个」，阻塞处理「丢弃后续调度」，超时 60 秒，不重试。

改外送领域事件的类名或字段之前，先确认登记表是空的：反序列化不了的旧行会让每一轮补投中断在它那里。升级 Modulith 前也一样：外送器的 `listener_id` 随类名变化（2.0 → 2.1 就变过），旧行会因找不到监听器每轮被标 `FAILED`、永远发不出去。

不要用 `EventExternalizationConfiguration.headers(...)` 加消息头：Modulith 2.1.2 默认的外送路径会把它们丢掉。

**只外送有队列订阅的事件**：没有队列可投的消息会被 broker 退回，登记行一直留着、每分钟补投一次；尝试 10 次以上的行每轮打 WARN（事件 ID、类型、发布时间、尝试次数）。上线时先部署消费方（声明队列与绑定），再部署发布方。

## 消费方怎么接

1. boot 模块引入本模块；adapter 写 `@RabbitListener` 要 `spring-boot-starter-amqp`。
2. Flyway 建 `processed_event`：照抄 `src/integrationTest/resources/db/migration/V2__create_processed_event.sql`。`event_publication` 也照建（同发布方第 2 步）：Modulith 关机时会查未完成的登记行，没有表会报 SQL 错。
3. boot 层声明队列（连同死信交换机 `<队列>.dlx`、死信队列 `<队列>.dlq`，以及发布方的交换机）：

   ```java
   @Bean
   Declarables inboxQueue() {
     return EventQueue.named("notification.inbox")
         .bind("interaction.events", "comment.published", "comment.deleted")
         .declarables();
   }
   ```

4. Handler 在业务事务里先去重：

   ```java
   transactionOperations.executeWithoutResult(status -> {
     if (!processedEvents.markProcessed("notification.inbox", event.eventId())) {
       return; // 处理过了
     }
     // 写业务数据……
   });
   ```

   `markProcessed` 必须在已有事务里调用（`MANDATORY`）；业务失败回滚时记录一起回滚，重试不会被当成重复。

5. 死信队列里的消息不会自己回来，在 RabbitMQ 管理界面查看或重放。

## 测试

集成测试用 `patra-tool-test` 的 `PostgreSQLContainerInitializer` 与 `RabbitMQContainerInitializer`（`rabbitmq:4.4.0-management`）。模拟 broker 不可达用 `getRabbitMQContainer().execInContainer("rabbitmqctl", "stop_app")` / `start_app`，容器不停、端口不变。
