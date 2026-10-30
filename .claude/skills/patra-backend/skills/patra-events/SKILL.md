---
name: patra-events
description: |
  Patra 领域事件开发指南。当聚合状态变化需要通知同一服务内的其他子域或聚合时使用此技能。
  触发场景：创建 DomainEvent record、实现 @EventListener / @TransactionalEventListener 处理器、
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
    └── 需要 Outbox + MQ，仓库里目前没有实现：不要在业务代码里直接发 MQ，先和用户确认方案
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
| 事务回滚但外部副作用已发生 | 在同步 @EventListener 里做了远程调用 / Redis | 副作用改到 `AFTER_COMMIT`，不能丢的要走 Outbox（先确认方案） |
| 提交后监听器的写入丢了 | 用了 `@TransactionalEventListener` 却没开新事务 | 加 `@Transactional(propagation = REQUIRES_NEW)` |

## 资源索引

| 场景 | 参考文档 |
|------|----------|
| 内部事件详细模式 | [event-patterns.md](resources/event-patterns.md) |
