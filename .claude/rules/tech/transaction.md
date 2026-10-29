---
paths: patra-api/**/*.java, patra-tool/**/*.java
---

# 事务规范

一条总原则：**事务里只放数据库操作，并且越短越好**。下面每个场景只规定一种写法；规范没覆盖到的情况，按总原则判断，并在类注释里写明理由。

## 1. 各场景的写法

| 场景 | 写法 | 位置 |
|------|------|------|
| 写命令 Handler | 注入 `TransactionOperations`，用 `execute` / `executeWithoutResult` 只包住「加锁 → 锁内复核 → 写库 → 发同事务事件」这一段；`handle()` 和类上都**不加** `@Transactional` | app |
| 查询 QueryService | 默认不加事务：读端适配器每次查询自成一体，在适配器里映射完再返回（各服务都关了 `open-in-view`，懒加载代理不能带出适配器）。同一方法要多次读 JPA 实体、需要同一个会话时，加 `@Transactional(readOnly = true)`；这种方法里不能有远程调用、Redis（第 2 节），有就把查库部分下沉到读端适配器 | app |
| 依赖调用方事务的方法（加锁、`FOR UPDATE` 读取、条件消费、整组替换子表、被 Handler 在事务里调用的 Gateway） | `@Transactional(propagation = MANDATORY)`，没有外层事务时直接抛 `IllegalTransactionStateException` | app / infra |
| 每条 / 每批单独提交的写入（抓取后落库的 Persister） | 独立 bean 的 public 方法加 `@Transactional(propagation = REQUIRES_NEW, rollbackFor = Exception.class)`；**调用方不得持有事务**（Spring Batch 参照数据除外，见下一行），否则一个线程同时占两条连接 | app |
| 循环里按批提交 | 在 Handler 里对每一批调用一次 `transactions.execute(...)` | app |
| Spring Batch | 由 chunk 事务管理，Processor / Writer 里不另开事务。唯一例外：Processor 里创建的参照数据（如期刊实例），在 chunk 回滚、逐条重写后还要被引用，就调 app 层的 `REQUIRES_NEW` 方法单独提交（每个线程同时占两条连接） | infra |
| 必须和业务一起成功或失败的事件处理（计数、站内提醒） | 同步 `@EventListener`，跟着发布方的事务走 | app |
| 提交后才做、可以丢、可以重建的副作用（缓存、限流计数） | `@TransactionalEventListener(phase = AFTER_COMMIT)`；监听器里要写库时再加 `@Transactional(propagation = REQUIRES_NEW, rollbackFor = Exception.class)` | app |
| 提交后才做、不能丢的副作用（跨服务消息） | Outbox：和业务写入在同一事务里写 outbox 表，由中继发送 | app |

`MANDATORY` 是守卫，不开事务，所以它是 infra 层唯一允许出现的 `@Transactional`，也不算下面第 4 节所说的一种「事务风格」。这是本项目的约定，不是业界通行写法。

## 2. 事务里不许放什么

下列操作一律放在事务开始之前或提交之后：

1. 远程调用：HTTP Interface、其他服务的 Gateway、第三方 API
2. 发送 MQ 消息、发邮件
3. 文件下载 / 上传、对象存储读写
4. Redis 写入（会话、限流、计数、缓存）
5. 耗时计算：密码哈希、图片处理、大文件解析
6. 等待：`sleep`、轮询、阻塞队列

事务里要用的值（当前用户、`clock.instant()`、预读结果）在事务开始前取好。确实要破例时，在类注释里写明理由和失败后的后果。

**唯一的固定例外：会话的签发与撤销**。登录、注册时签发会话，以及封禁、改密、注销、换绑邮箱时撤销会话，都在锁住用户行之后、在事务内写 Redis。理由有两条：

1. 签发和撤销靠同一把用户行锁串行化。如果登录改成提交后才签发，封禁就可能插在「登录提交」和「签发」之间：封禁撤掉了所有会话，随后又冒出一个新会话。会话校验只查 Redis、不查库，这个会话会一直活到过期。
2. 失败的方向是安全的。撤销失败时整个操作回滚，重试即可，不会出现「库里已封禁，会话还活着」。签发后提交失败时，令牌没有返回给客户端，留下的会话没人拿得到，过期后自然消失。

踢设备（本人撤销某一台或其他全部设备）也在事务内撤销，但不锁用户行：它只动会话与登录记录、不改用户状态，没有要挡的并发登录。撤销先打 Redis 再结束登录记录，事务保证这几条记录一起结束；Redis 失败时什么都还没写，库失败时会话已撤、记录未结束，方向安全（被踢的设备已经用不了），重试即可。

代价是持锁期间多一次 Redis 往返。清限流、写计数这类与会话无关的 Redis 写入不适用此例外，一律放在提交之后；提交后的写入失败只记日志，不影响已经生效的结果。

## 3. 并发与加锁

1. **先预读，再在锁内重读**：事务外的预读只用来做前置校验（404 / 403）和拿到加锁用的键；事务里加锁后必须重新读取，并以重读结果为准。参见 `DeleteCommentHandler`。
2. **加锁方法必须在事务里调用**：`pg_advisory_xact_lock`、`SELECT … FOR UPDATE` 在 autocommit 下语句一结束锁就释放，等于没锁。所以这些方法统一加 `MANDATORY` 守卫。
3. **多把锁的顺序固定**：先拿配额锁，再拿业务对象锁（用户 / 作者 / 楼），最后锁行。Handler 注释里写明拿了哪些锁。
4. **并发 find-or-create**：用原生 SQL `INSERT … ON CONFLICT DO NOTHING`，再 `SELECT` 取回。前提是 PostgreSQL 默认的 READ COMMITTED：`ON CONFLICT` 会等冲突事务结束，随后的 `SELECT` 拿新快照，能看到对方插入的行。**禁止**捕获 `DataIntegrityViolationException` 后继续用同一个事务——PostgreSQL 报错后整个事务已经作废，而且 JPA 的 `save` 可能延迟到提交时才 flush，异常根本进不了 `catch`。
5. **Outbox 中继分三段**：每条消息在短事务里按 `version` 条件更新写租约（抢不到就跳过）→ 在事务外发送 → 短事务里按 `version` 条件更新标记状态。投递语义是「至少一次」：标记失败时租约过期后会被重新领取，消费端按事件 ID 幂等处理。

## 4. 声明式注解细则

1. 只加在 public 方法上，不加在类上。
2. 不在同一个类里自调用带 `@Transactional` 的方法（不走代理，事务不生效）。
3. 写事务一律写 `rollbackFor = Exception.class`；`readOnly` 和 `MANDATORY` 不用写。
4. 同一个类只用一种事务风格：要么注入 `TransactionOperations`，要么用声明式注解。

## 5. 测试

1. Handler 单元测试的事务桩用 `TransactionOperations.withoutTransaction()`。需要断言「远程调用在事务外」时，用记录 `tx-begin` / `tx-end` 顺序的桩（`patra-tool-test` 的 `dev.patra.tool.test.transaction.RecordingTransactions`）。
2. 需要真实提交的集成测试（并发、锁、提交后监听器）：类上加 `@Transactional(propagation = NOT_SUPPORTED)`，用例里自己用 `TransactionTemplate` 开事务。

## 6. 机器守护

由各服务的架构测试（`HexagonalArchitectureRules`）检查，**每个服务都必须有架构测试**：

1. 方法上的 `@Transactional` 只允许出现在 app 层；infra 层只允许 `propagation = MANDATORY`（规则 15）。
2. 禁止类级 `@Transactional`（规则 16）。
3. `CommandHandler` 的实现类不得标 `@Transactional`（规则 16b）。

## 不采用的做法

- **CommandBus 统一包事务**：Handler 必须把远程调用放在事务外，总线统一包事务做不到这一点。业界只有 Axon 这类自带 UnitOfWork 的框架这样做。
- **长事务监控分级告警**：单人开发环境不需要。
