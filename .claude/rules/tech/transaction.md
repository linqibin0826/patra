---
paths: patra-api/**/*.java, patra-tool/**/*.java
---

# 事务规范

总原则：**事务里只放数据库操作，越短越好**。没覆盖到的情况按总原则判断，破例在类注释里写明理由。

## 1. 写法

| 场景 | 写法 |
|------|------|
| 写命令 Handler | 注入 `TransactionOperations`，只包「加锁 → 锁内复核 → 写库」；`handle()` 与类上不加 `@Transactional`（ArchUnit 规则 16b） |
| 查询 QueryService | 默认不加事务，读端适配器内映射完再返回；要同一会话多次读 JPA 实体才加 `readOnly = true`，此时方法里不能有远程调用 |
| 加锁、`FOR UPDATE`、条件消费、依赖调用方事务的 Gateway | `@Transactional(propagation = MANDATORY)`，infra 只允许这一种（规则 15） |
| 逐条单独提交的 Persister | 独立 bean 上 `REQUIRES_NEW` + `rollbackFor = Exception.class`，调用方不得持有事务 |
| Spring Batch | 交给 chunk 事务；Processor 里建的参照数据（期刊实例）例外，走 `REQUIRES_NEW`：chunk 回滚重写后还要被引用 |
| 同成同败的事件 | 同步 `@EventListener` |
| 提交后可丢的副作用 | `@TransactionalEventListener(AFTER_COMMIT)`，要写库再加 `REQUIRES_NEW` + `rollbackFor` |
| 提交后不能丢的副作用 | Outbox，与业务同事务写入 |

## 2. 事务里不许放

远程调用、发 MQ / 邮件、文件与对象存储、Redis、耗时计算（哈希、图片、大文件解析）、等待。要用的值（当前用户、`clock.instant()`）在事务前取好；提交后的写入失败只记日志。

**例外：会话的签发与撤销**在锁住用户行后、事务内写 Redis——签发与封禁靠同一把行锁串行化，否则封禁后会冒出新会话。踢设备同样在事务内撤销，但不锁用户行（不改用户状态）。

## 3. 加锁与并发

- 事务外预读只做校验和取锁键，锁内重读为准（参照 `DeleteCommentHandler`）。
- 多把锁顺序：配额锁 → 业务对象锁 → 行锁。
- find-or-create 用 `INSERT … ON CONFLICT DO NOTHING` 再查；禁止 catch `DataIntegrityViolationException` 后继续用同一事务（PG 事务已作废）。
- Outbox 中继：短事务写租约 → 事务外发送 → 短事务标记；没标记成的消息停在 `PUBLISHING`，租约过期后重新领取。

## 4. 细则

- 注解只加 public 方法、不加类上（规则 16）；不自调用带注解的方法；写事务写 `rollbackFor = Exception.class`；一个类只用一种风格。
- 单测事务桩用 `TransactionOperations.withoutTransaction()`，断言事务内外顺序用 `patra-tool-test` 的 `RecordingTransactions`；需要真实提交的 IT 用 `NOT_SUPPORTED` + `TransactionTemplate`。
- 不在 CommandBus 统一包事务：做不到把远程调用放在事务外。
