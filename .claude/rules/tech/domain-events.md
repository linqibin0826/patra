---
paths: patra-api/**/*.java, patra-tool/**/*.java
---

# 领域事件规范

总原则：**聚合产生事件，Handler 发布。** 调研和取舍见 PAP-175；外送到 MQ 的细节见 `patra-backend:patra-events`。

## 1. 谁做什么

| 内容 | 放哪 |
|------|------|
| 这件事算不算发生、要不要发事件 | 聚合的行为方法或领域服务 |
| 构造事件 | 同上，方法返回事件；聚合外的数据和时刻作为参数传入 |
| 写库、发布 | Handler 调 `events.publishEvent(...)`，放在事务最后一步 |

聚合不持有发布器，也不暂存事件。Handler 里不写「要不要发」的业务判断。

```java
// 聚合判断并产生事件，没发生就返回空
Optional<CommentDeletedEvent> event = comment.delete(actor, hasReplies, now);
// Handler 写库后发布
event.ifPresent(events::publishEvent);
```

同步监听器在 `publishEvent` 时当场执行。发布之后还要写库或加锁时，在注释里写明原因，并把监听器的锁算进锁顺序。

## 2. 例外：由 Handler 构造事件

- 要看数据库结果才知道是否发生，如 `INSERT ... ON CONFLICT`
- 没有聚合的用例，如批量标已读
- 纯技术通知，如 `UnreadCountsChangedEvent`

例外里的业务判断仍放 domain。邮件投递端口的实现（`OutboxEmailDeliveryAdapter`）在 infra 发布发信指令，它不是领域事件。

## 3. 订阅选哪种

| 这件后续的事 | 写法 | 例子 |
|------|------|------|
| 要和主业务同成同败 | `@EventListener` | 删评论时清有帮助 |
| 提交后才做、丢了可接受 | `@Async` + `@TransactionalEventListener(AFTER_COMMIT)` | 推未读数 |
| 通知其他服务、不能丢 | 外送到 MQ，服务代码不写 `@ApplicationModuleListener` | 有帮助提醒 |

不用 `@EventListener` + `@Async`：可能抢在提交前读到旧数据，事务回滚了也已经执行。

## 4. 防漏发

- 同一个状态变化只留一个写入口
- 聚合单测断言返回的事件，Handler 单测或集成测试断言确实发布或外送
