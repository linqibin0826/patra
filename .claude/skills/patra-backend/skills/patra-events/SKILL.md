---
name: patra-events
description: |
  Patra 领域事件开发指南。当聚合状态变化需要通知同一服务内的其他子域或聚合，或经 RabbitMQ 通知其他服务时使用此技能。
  触发场景：创建 DomainEvent record、实现 @EventListener / @TransactionalEventListener 处理器、
  外送集成事件（EventExternalizationConfiguration）、写 @RabbitListener 消费并去重、
  选择同步订阅还是提交后订阅、处理事件处理器的 OptimisticLockingFailureException、设计事件链。
  即使用户只说"通知其他子域"或"完成后触发"，也应使用此技能。
  不适用于：创建 Controller/Handler/Port（用 patra-hexagonal）、JPA 数据层（用 patra-jpa）。
allowed-tools: Read, Edit, Write, Grep, Glob, Bash
---

# Patra 领域事件指南

在 Patra 中实现事件驱动通信时，按此指南选择正确的模式。

## 内部事件 vs 外部事件

```
需要通知谁？
│
├── 同一服务内的其他子域 / 聚合
│   └── Spring ApplicationEvent（内部事件）
│       ├── 必须与业务同成败（计数、站内提醒、开放获取标记）→ @EventListener（同步，同一事务）
│       ├── 提交后才做、可丢可重建（缓存、限流）→ @TransactionalEventListener(AFTER_COMMIT)
│       └── 异步处理 → @EventListener + @Async
│
└── 其他微服务
    └── patra-tool-events：事务里 publishEvent(领域事件)，boot 层配置外送到 RabbitMQ（见「跨服务事件」）
        不要在业务代码里直接发 MQ
```

## 内部事件快速模板

以 catalog 为例：全文子域抓到 PMC 全文后，文献子域要在同一事务里把文献标为开放获取。

### 1. 定义事件（domain 层，`domain.{子域}.event`）

```java
/// PMC 全文已抓取：全文子域在写入全文记录的同一事务里发布，文献子域同步订阅，把文献标为 PMC 开放获取。
///
/// @param publicationId 文献 ID
/// @param pmcid PMCID
/// @param licenseCode PMC 元数据里的许可原值，可空
/// @param manuscript 是否作者手稿
public record PmcFullTextFetchedEvent(
    long publicationId, String pmcid, String licenseCode, boolean manuscript) {}
```

**命名规范**：过去时（`PmcFullTextFetchedEvent`、`CommentPublishedEvent`），不用现在时。跨子域订阅要在服务的架构测试里声明订阅边（`allowingSubscription`）。

### 2. 创建监听器（app 层，`app.{子域}.listener`）

```java
/// 同步 `@EventListener`：与发布方同一线程、同一事务；抛出异常时全文记录与开放获取标记一起回滚。
/// 不加 `@Transactional`，也不能用 `@TransactionalEventListener`（提交后才执行，标记会丢）。
@Component
@RequiredArgsConstructor
public class PmcOpenAccessListener {

  private final PublicationRepository publicationRepository;

  @EventListener
  public void on(PmcFullTextFetchedEvent event) {
    publicationRepository.markPmcOpenAccess(
        event.publicationId(),
        PublicationOaLocation.ofPmc(event.pmcid(), event.licenseCode(), event.manuscript()));
  }
}
```

### 3. 发布事件

```java
// 在写库的同一事务里通过 ApplicationEventPublisher 发布
eventPublisher.publishEvent(
    new PmcFullTextFetchedEvent(publicationId, pmcid, licenseCode, manuscript));
```

## 跨服务事件（patra-tool-events）

模块 README（`patra-tool/patra-tool-events/README.md`）有完整接入步骤与 Flyway 模板，这里只列要点。

**发布端**

1. 领域事件带 `UUID eventId`，在**创建时**生成；登记表存的是领域事件，补投会重新映射，在映射里生成 ID 会让消费端去重失效。
2. 集成事件 record 放发布方 `-api` 模块，实现 `IntegrationEvent`，字段只放别的服务需要的事实。
3. boot 层 `EventExternalizationConfiguration`：`externalizing().selectByType(...).route(类型, e -> RoutingTarget.forTarget("<服务>.events").andKey("<事件类型>")).mapping(类型, e -> new 集成事件(e.eventId(), ...))`；领域层不贴 `@Externalized`。
4. 事务里照常 `events.publishEvent(领域事件)`；broker 没收下时登记行留着，由 XXL-Job `eventPublicationResubmitJob` 补投。
5. 服务代码里不写 `@ApplicationModuleListener`：登记表只认带它的监听器（`registry-trigger-annotation`），外送器自己就带着它；普通 `@TransactionalEventListener` 不进登记表。
6. 只外送有队列订阅的事件：没有队列可投时 broker 退回消息，登记行会一直留着、每分钟补投一次。

**消费端**

1. boot 层把 `EventQueue.named("<队列>").bind("<发布方>.events", "<路由键>"...).declarables()` 注册成 bean（自带死信交换机与死信队列）。
2. adapter 的 `@RabbitListener` 只转发给 Handler；Handler 在业务事务里先 `processedEvents.markProcessed("<队列>", event.eventId())`，返回 `false` 直接结束。
3. 删除先于发布到达、旧编辑晚到这类乱序，按事件的业务规则在 Handler 里处理（墓碑、版本号）。

## 注解组合速查

| 场景 | 注解组合 |
|------|---------|
| 与业务同成败 | `@EventListener`（同步，同一事务，不加 `@Transactional`） |
| 事务提交后处理 | `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Transactional(propagation = REQUIRES_NEW)` |
| 异步处理 | `@EventListener` + `@Async` |
| 条件处理 | `@EventListener(condition = "#event.status == 'COMPLETED'")` |

## 常见陷阱

| 问题 | 原因 | 解决 |
|------|------|------|
| 事件重复处理 | 并发或重试 | 幂等性检查 + 乐观锁 |
| 事件循环 | A → B → A | 确保事件链是线性的 |
| 事务回滚但外部副作用已发生 | 在同步 @EventListener 里做了远程调用 / Redis | 副作用改到 `AFTER_COMMIT`；跨服务且不能丢的走 patra-tool-events |
| 消费端把同一事件处理了两次 | 至少一次投递：补投、ack 前崩溃都会重投 | 业务事务里先 `markProcessed`，事件 ID 在领域事件创建时生成 |
| 提交后监听器的写入丢了 | 用了 `@TransactionalEventListener` 却没开新事务 | 加 `@Transactional(propagation = REQUIRES_NEW)` |

## 资源索引

| 场景 | 参考文档 |
|------|----------|
| 内部事件详细模式 | [event-patterns.md](resources/event-patterns.md) |
