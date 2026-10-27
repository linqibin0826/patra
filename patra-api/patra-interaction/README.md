# patra-interaction

社区互动服务：v0.13 是文献库与收藏夹，v0.14 加文献讨论、投稿经验与站内提醒；以后的关注动态、行为数据也都放进这个服务，各占一个子域。

工程设计：`docs/patra/specs/2026-10-24-interaction-skeleton-design.md`（服务骨架、子域边界、存储与部署接入）、`docs/patra/specs/2026-10-27-interaction-foundation-extension-design.md`（v0.14 底座扩展）。依据 release spec `docs/patra/release-specs/v0.13-library.md` 决策 A、B、G 与 `docs/patra/release-specs/v0.14-discussion.md` 决策 A、B、E、H。

## 1. 子域

| 子域 | 职责 | 本版 |
|---|---|---|
| `subject` | 对象底座：每个被互动过的对象在登记表 `subject.registry` 里一行，互动数据只引用本地 `subject_id`。外部对象（文献、期刊）锚定在外部标识上（`pmid:…`、`nlm:…`），catalog 内部 ID 只是缓存，首次互动时同步向 catalog 解析并登记（catalog 不可用或对象不存在则拒绝），每日对账按锚刷新；本地对象（投稿经验，`post:…`）由所属子域在自己的事务里登记、改状态，带所有者与「是否匿名」，不对账。能力矩阵在 `SubjectType`。另外提供各子域共用的用户名片（`UserDirectoryPort`）、发表守卫（`PublishGuardGateway`：资格回查与按作者计数的频率限制）和注销清理流程（各子域实现 `UserDataCleanupGateway`）。工程设计 `docs/patra/specs/2026-10-24-subject-registry-design.md`、`docs/patra/specs/2026-10-27-interaction-foundation-extension-design.md` | PAP-112、PAP-120 |
| `collection` | 文献库与收藏夹：文献库条目（用户 × 文献唯一，可带备注），扁平的收藏夹（私有 / 公开、一个条目可进多个夹），复制别人的公开收藏夹、批量「我的状态」、导出 key；向底座登记注销清理动作。工程设计 `docs/patra/specs/2026-10-24-interaction-collection-design.md` | PAP-113 |
| `discussion` | 文献与投稿经验下的讨论：评论与回复、两层展示与单楼视图、修改历史、@ 联想；评论写入时在同一事务里发布进程内事件（事件契约 `domain.discussion.event`） | PAP-121 |
| `post` | 投稿经验（按帖子结构存）：帖子基表与经验扩展表、匿名、修改历史、期刊汇总；经验作为本地对象 `POST` 向底座登记 | PAP-122 |
| `notification` | 站内提醒：订阅讨论的事件，按规则在同一事务里写入；未读数、列表、已读、5 个月清理 | PAP-123 |

`InteractionArchitectureTest` 的子域清单已经写满上面五个；以后再新增子域才改清单。子域的各层包随第一批代码建（规则 22b：一个子域只要有一层出现，四层就都要有），不建没有代码的空包和空 schema。

## 2. 模块与包

六层模块照常（domain / app / infra / adapter / api / boot），每层下再按子域分包：

```
dev.patra.interaction.{domain|app|infra|adapter|api}.{subject|collection|discussion|post|notification}..
dev.patra.interaction                  启动类
dev.patra.interaction.config           装配（XXL-Job 执行器等），不属于任何子域
```

层下面只能是已声明的子域，不放 `common` 之类的共享包。子域内部的分包沿用各服务的写法（`domain.collection.model.aggregate`、`domain.collection.port.repository`、`infra.collection.persistence`、`adapter.collection.rest`、`adapter.subject.scheduler`）。api 模块是服务间契约，目前没有别的服务调用 interaction，模块里还没有类。

## 3. 子域边界

子域之间只经对方的**公开包**交互，由 `InteractionArchitectureTest`（`patra-tool-test` 的 `SubdomainArchitectureRules`）守住：

| 子域 | 公开包 |
|---|---|
| 任意子域 X | `domain.X.port.read`（读端口）、`domain.X.model.read`（读端口用到的读模型）、`domain.X.event`（事件契约：X 发布的进程内事件） |
| 底座 `subject` 另加 | `domain.subject.port.gateway`（驱动端口：对象登记、发表守卫，实现在 `app.subject`；以及注销清理扩展点 `UserDataCleanupGateway`，由各子域实现）、`domain.subject.port.user` 与 `domain.subject.model.user`（用户名片）、`domain.subject.model.vo`、`domain.subject.model.enums`（`SubjectId`、`SubjectType` 这类所有互动都引用的值）、`domain.subject.exception`（底座端口抛出的异常，需要降级时可以捕获） |

- 公开包之外的一切（聚合、实体、仓储、领域服务、app / infra / adapter 的任何类）只在子域内部用。
- 子域之间无环；底座不依赖任何其他子域。
- **跨子域依赖必须声明**（规则 27）：任何子域都可以依赖底座；其余的边要在 `InteractionArchitectureTest` 里用 `allowingDependency`（经对方全部公开包）或 `allowingSubscription`（只经对方的事件契约）声明。现在只有一条：提醒订阅讨论的事件（v0.14 决策 A）。讨论、提醒都不依赖投稿经验：「作者」标记与匿名遮蔽只看登记表的所有者两列。
- 推论：非底座子域的读端口签名、事件 record 的字段，只能用 JDK 类型、自己 `model.read` / `event` 里的类型和底座公开的类型。
- 进程内事件：发布方的 app 层在写入事务里调 Spring 的 `ApplicationEventPublisher`；订阅方的 app 层用同步的 `@EventListener`（同一线程、同一事务），不用 `@TransactionalEventListener`，订阅方失败时整个事务回滚。

## 4. 存储

- 库 `patra_interaction`（现有 PG 实例），每个子域一个 schema，schema 名就是子域名，在迁移脚本里 `CREATE SCHEMA`。
- 整个服务一份 Flyway 历史（`public.flyway_schema_history`）。脚本放 `patra-interaction-infra/src/main/resources/db/migration/{子域}/`，版本号是写脚本时的时间戳 `yyyyMMddHHmmss`（如 `V20261024100000__create_subject_schema.sql`），子域各自加脚本不会撞号。不设 `spring.flyway.schemas`：设了 Flyway 会自己建 schema 并把历史表挪进去。
- 实体用 `@Table(schema = "…")` 写明 schema，原生 SQL 写带 schema 的表名；不设 Hibernate 默认 schema。
- 子域的表不对 `subject` 建跨 schema 外键，只建索引；引用完整性由写入路径和每日对账保证。
- 表里只存用户 ID，不存用户名、昵称、头像（R6 检查守着迁移脚本）。
- 表名不带服务前缀，schema 已经说明归属（如 `subject.registry`）。
- `set_updated_at()` 触发器函数每个 schema 一份（`subject.set_updated_at()`），schema 整体搬走时不依赖 `public`。
- `subject.registry`：类型 1 文献、2 期刊（`nlm:` 锚按字符串存，保留前导零）、3 投稿经验（本地对象，`source_id` 就是帖子 ID）；所有者两列 `owner_user_id`、`owner_anonymous` 只有本地对象有，登记后不变。
- `subject.publish_quota`：发表守卫的计数，每个作者每种发表（评论 / 投稿经验）一行，记北京时间当天的条数与上次发表时刻；同一作者的发表在事务级 advisory lock `subject.publish:{userId}` 上排队。

## 4.1 collection 的存储与并发

- 三张表：`collection.library_item`（用户 × 对象，备注、收藏时间）、`collection.collection`（收藏夹，维护 `item_count`）、`collection.collection_item`（收藏夹 × 对象，加入该夹的时间）。两张状态窄表按 `user_id` 哈希分 16 区，主键以 `user_id` 打头，每条语句都带 `user_id`。
- 每个改某个用户数据的事务先拿事务级 advisory lock `collection.user:{userId}`，再在锁内「查上限 / 查重名 → 写」：上限（文献库 20,000、收藏夹 200、单夹 5,000）与重名都是先查再写，靠这把锁串行。
- 新增收藏经 subject 的 `SubjectGateway`（事务外，可能同步调 catalog）；已有条目只用 `subject_id`（= 对外的 `itemId`），对象失效、catalog 整行消失后仍能改备注、移出、移除。
- 排序：加入时间走 SQL 分页；出版日期、期刊在内存里排（快照经 `SubjectReadPort.findByIds` 每 5,000 个一块取），不把排序键抄进条目。
- 公开资格与可见性只问 identity（批量用户摘要）：设公开回查账号状态与邮箱，读公开收藏夹透传访客 ID、看 `profileVisible`。

## 5. 运行

| 项 | 值 |
|---|---|
| 端口 | 6500；XXL-Job 执行器 6501（不映射到宿主机） |
| 网关前缀 | `/patra-interaction/**` |
| 错误码前缀 | `INTERACTION`（如 `INTERACTION-0404`） |
| 配置 | jar 里的 `application.yml` 放随代码变的默认值；库地址在 Nacos `patra-interaction.yaml`，账号与 xxl-job 管理端地址在 `patra-common.yaml` |
| 定时任务 | 执行器 `patra-interaction`；对象对账 `subjectReconciliationJob`（`0 30 6 * * ?`，超时 600 秒、不重试，PAP-112；只对外部对象，文献按 PMID、期刊按 NLM ID；catalog 换库重建或重跑导入后立即手动触发一次）、注销清理 `deletedUserCleanupJob`（`0 0 6 * * ?`，超时 600 秒、不重试；PAP-120 起是底座的公共流程：合并各子域有数据的用户，分批回查 identity，只删 `DELETED` 用户在各子域登记的个人数据，现在是收藏与发表计数，PAP-123 起加上提醒） |
| 上线 | `docs/patra/runbooks/v0.13-library-go-live-runbook.md`、`docs/patra/runbooks/v0.14-discussion-go-live-runbook.md` |

## 6. 什么时候拆分

各种互动放在一个服务里，是因为单人在一台 mini 上维护，每多一个服务就多一套模块、JVM、库、Nacos 配置和 CD；而各种互动共享「用户对某个对象做动作」这一抽象和同一条读聚合。

当某个子域的**负载、发布节奏或存储引擎**明显和其余部分分化时（比如行为数据要换 ClickHouse、讨论的写入量远超收藏），把它剥成独立服务：

1. 先确认它和其他子域之间只剩公开包上的依赖（ArchUnit 已保证）。
2. 它的 schema 整体搬到新服务的库里；没有跨 schema 外键，不用先拆约束。
3. 它被别的子域用到的公开包换成 HTTP 契约，放进新服务的 api 模块。
