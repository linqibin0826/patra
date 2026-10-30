# patra-interaction

社区互动服务：v0.13 是文献库与收藏夹，v0.14 加文献讨论、投稿经验与站内提醒，v0.15 加「有帮助」；以后的关注动态、行为数据也都放进这个服务，各占一个子域。

工程设计：`docs/patra/specs/2026-10-24-interaction-skeleton-design.md`（服务骨架、子域边界、存储与部署接入）、`docs/patra/specs/2026-10-27-interaction-foundation-extension-design.md`（v0.14 底座扩展）。依据 release spec `docs/patra/release-specs/v0.13-library.md` 决策 A、B、G 与 `docs/patra/release-specs/v0.14-discussion.md` 决策 A、B、E、H。

## 1. 子域

| 子域 | 职责 | 本版 |
|---|---|---|
| `subject` | 对象底座：每个被互动过的对象在登记表 `subject.registry` 里一行，互动数据只引用本地 `subject_id`。外部对象（文献、期刊）锚定在外部标识上（`pmid:…`、`nlm:…`），catalog 内部 ID 只是缓存，首次互动时同步向 catalog 解析并登记（catalog 不可用或对象不存在则拒绝），每日对账按锚刷新；本地对象（投稿经验，`post:…`）由所属子域在自己的事务里登记、改状态，带所有者与「是否匿名」，不对账。能力矩阵在 `SubjectType`。另外提供各子域共用的用户名片（`UserDirectoryPort`）、发表守卫（`PublishGuardGateway`：资格回查与按作者计数的频率限制）、注销清理流程（各子域实现 `UserDataCleanupGateway`），以及评论与投稿经验共用的正文转纯文本（`PlainTextPort`，按门户的 Markdown 子集转，`<@用户ID>` 保留，PAP-128）。工程设计 `docs/patra/specs/2026-10-24-subject-registry-design.md`、`docs/patra/specs/2026-10-27-interaction-foundation-extension-design.md`、`docs/patra/specs/2026-10-30-plaintext-conversion-design.md` | PAP-112、PAP-120、PAP-128 |
| `collection` | 文献库与收藏夹：文献库条目（用户 × 文献唯一，可带备注），扁平的收藏夹（私有 / 公开、一个条目可进多个夹），复制别人的公开收藏夹、批量「我的状态」、导出 key；向底座登记注销清理动作。工程设计 `docs/patra/specs/2026-10-24-interaction-collection-design.md` | PAP-113 |
| `discussion` | 文献与投稿经验下的讨论：评论与回复（树存、两层展示、「回复 @X」）、单楼视图与对话链、Markdown 正文与 @（存 `<@用户ID>`）、修改历史（发布 5 分钟内的修改不记版本）、有回复的删除留占位、@ 联想；匿名所有者的评论对别人显示「匿名作者」。评论写入、编辑、删除时在同一事务里发布进程内事件（事件契约 `domain.discussion.event`）。工程设计 `docs/patra/specs/2026-10-27-interaction-discussion-design.md` | PAP-121 |
| `post` | 投稿经验（按帖子结构存）：帖子基表 + 经验扩展表 + 修改历史（发布后 5 分钟内的修改不记版本）；字段与首轮 / 最终结果的一致性规则；同一作者同刊同投稿日期只能有一条；匿名由服务端遮蔽（他人拿不到作者 ID）；期刊页汇总读取时由 PG `percentile_cont` 算、Java 半进位；写入经发表守卫（每天 10 条），经验作为本地对象 `POST` 在同一事务里登记、删除时置为失效，并在同一事务里发布进程内事件 `PostDeletedEvent`（事件契约 `domain.post.event`）。工程设计 `docs/patra/specs/2026-10-27-interaction-post-design.md` | PAP-122、PAP-127 |
| `notification` | 站内提醒：订阅讨论的事件，按规则在同一事务里写入；评论或投稿经验删除时删掉对应提醒；未读数、列表、已读、5 个月清理。工程设计 `docs/patra/specs/2026-10-27-interaction-notification-design.md`、`docs/patra/specs/2026-10-28-notification-inbox-redesign-design.md` | PAP-123、PAP-127 |
| `reaction` | 「有帮助」：给别人的评论（根评论、楼内回复）与投稿经验点、取消；明细、每个目标的有帮助数、每个用户的获赞同事务维护（明细真插入 / 删除才 ±1）。资格与每天 300 次复用发表守卫（种类 `HELPFUL`），不能给自己点（含匿名经验作者）；匿名经验与作者在自己匿名经验下的评论不计获赞。订阅评论与投稿经验的删除事件，在删除的同一事务里清掉被删目标的有帮助。不接入注销清理。点、取消真写入时发布进程内事件（事件契约 `domain.reaction.event`），「已点」外送给通知服务（4.7）。工程设计 `docs/patra/specs/2026-10-29-reaction-helpful-design.md` | PAP-131、PAP-141 |

`InteractionArchitectureTest` 的子域清单已经写满上面六个；以后再新增子域才改清单。子域的各层包随第一批代码建（规则 22b：一个子域只要有一层出现，四层就都要有），不建没有代码的空包和空 schema。

## 2. 模块与包

六层模块照常（domain / app / infra / adapter / api / boot），每层下再按子域分包：

```
dev.patra.interaction.{domain|app|infra|adapter|api}.{subject|collection|discussion|post|notification|reaction}..
dev.patra.interaction                  启动类
dev.patra.interaction.config           装配（XXL-Job 执行器等），不属于任何子域
```

层下面只能是已声明的子域，不放 `common` 之类的共享包。子域内部的分包按全项目统一的分包规范（`.claude/rules/tech/package-layout.md`），由 `SubdomainArchitectureRules` 的白名单规则守住。api 模块是服务间契约，目前没有别的服务调用 interaction，模块里还没有类。

## 3. 子域边界

子域之间只经对方的**公开包**交互，由 `InteractionArchitectureTest`（`patra-tool-test` 的 `SubdomainArchitectureRules`）守住：

| 子域 | 公开包 |
|---|---|
| 任意子域 X | `domain.X.port.read`（读端口）、`domain.X.model.read`（读端口用到的读模型）、`domain.X.event`（事件契约：X 发布的进程内事件） |
| 底座 `subject` | 对其他子域完全公开（所有层、所有包），只放被两个以上子域共用的东西：对象登记与发表守卫的驱动端口（实现在 `app.subject`）、注销清理扩展点 `UserDataCleanupGateway`（由各子域实现）、用户名片 `domain.subject.port.user` 与 `domain.subject.model.read.UserCard`、`SubjectId` / `SubjectType` 这类所有互动都引用的值、正文转纯文本 `domain.subject.port.markdown` 与 `PlainText`、底座端口抛出的异常 |

- 普通子域公开包之外的一切（聚合、实体、仓储、领域服务、app / infra / adapter 的任何类）只在子域内部用。
- 子域之间无环；底座不依赖任何其他子域。
- **跨子域依赖必须声明**（规则 27）：任何子域都可以依赖底座；其余的边要在 `InteractionArchitectureTest` 里用 `allowingDependency`（经对方全部公开包）或 `allowingSubscription`（只经对方的事件契约）声明。现在有四条：提醒订阅讨论的事件（v0.14 决策 A）、提醒订阅投稿经验的删除事件（PAP-127）、有帮助依赖讨论（读端口取评论、订阅删除事件）与依赖投稿经验（订阅删除事件，PAP-131；读端口取经验的结果与正文带进「有帮助已点」事件，PAP-141）。提醒只经事件契约依赖投稿经验，讨论不依赖它；「作者」标记、匿名遮蔽与经验的作者只看登记表的所有者两列。没有子域依赖有帮助：讨论、经验的列表要带有帮助数时只经 SQL 读 `reaction` 的表（Java 依赖会成环）。
- 推论：非底座子域的读端口签名、事件 record 的字段，只能用 JDK 类型、自己 `model.read` / `event` 里的类型和底座公开的类型；例外要有声明的依赖边兜底：`HelpfulMarkedEvent.experience` 用投稿经验的 `model.read.ExperienceOutline`（`reaction → post` 是依赖边），以后只订阅有帮助事件的子域要读它，得同样声明对投稿经验的依赖。
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
- 收藏人数 `collection.subject_stats`（每个对象一行，没有行即为 0）：`LibraryRepositoryAdapter` 改文献库条目的四个方法（收藏、复制收藏夹、移出、注销清理）按 `RETURNING` 到的行在同一事务里 ±1；多行 ±1 按 `subject_id` 升序拿行锁。一人放进多个收藏夹只算一次（PAP-133）。

## 4.2 post 的存储与并发

- 三张表：`post.post`（帖子基表：类型、挂在哪个对象下、作者、是否匿名、正文、状态、`revised_at`）、`post.submission_experience`（经验扩展表，一对一）、`post.revision`（被替换的旧版本，结构化字段存 jsonb，各帖子类型共用）。都不分区（按对象读，量很小）。
- 删除是软删：内容留在库里不再展示；列表、汇总、主页都只读 `ACTIVE`。帖子不物理删除，所以列表的游标（上一页最后一条的 ID）总能反查排序键。
- 「同一作者同刊同投稿日期只能有一条未删除的经验」跨两张表、只对 `ACTIVE` 生效，没有唯一索引：写、改经验的事务先拿事务级 advisory lock `post.author:{userId}` 再查重。写经验时先过底座的发表守卫（它拿 `subject.publish:{userId}`），再拿作者锁。
- 登记表里 `POST` 的快照：`journal` = 期刊全称、`abbreviation` = 期刊缩写、`publishedDate` = 投稿月份（提醒的上下文行用）；改投稿月份时刷新。

## 4.3 discussion 的存储与并发

- 两张表：`discussion.comment`（根评论与回复，`root_id` 是所在楼的根评论、根评论等于自己；`parent_id` 是回复的那一条；正文存 Markdown 原文，@ 存 `<@用户ID>`；`mentioned_user_ids` 记这条评论曾经 @ 过的人，编辑时只对第一次 @ 到的人算新增）、`discussion.comment_revision`（被替换下来的旧版本，当前版本在主表）。按对象读、量很小，不分区。
- 树的完整性：`root_id`、`parent_id` 两个自引用外键，还有回复的评论删不掉（只能变占位）；修改历史随评论级联删除。删除时有直接回复的变占位（清正文与修改历史），没有的整行删除，再沿父评论往上删掉已经没有回复的占位。
- 并发：发表回复、删除先拿事务级 advisory lock `discussion.thread:{rootId}`（同一楼的结构变化排队），编辑用 `SELECT … FOR NO KEY UPDATE` 锁行；加锁顺序是作者锁（发表守卫）→ 楼锁 → 行锁。
- 读：对象未登记（还没人讨论过）回空列表，对象已失效回 404 `SUBJECT_UNAVAILABLE`；作者、被回复者、被 @ 的人一次批量向 identity 取名片，主页对访客不可见的去掉用户名与头像地址。
- 进程内事件：`CommentPublishedEvent`、`CommentEditedEvent`、`CommentDeletedEvent`，在写入事务里用 `ApplicationEventPublisher` 发布；提醒子域用同步 `@EventListener` 订阅，订阅方失败时评论一起回滚。

## 4.4 notification 的存储与并发

- 一张表：`notification.notification`，按收件人 `recipient_id` 哈希分 16 区（同 collection），主键 `(recipient_id, notification_id)`。列按以后通知中心的信箱设计：收件人、类型（`COMMENT_REPLY` / `POST_COMMENT` / `MENTION`）、触发人与「是否匿名所有者」、对象、评论、摘录、生成时刻、已读时刻。
- 写入：同步订阅讨论的三个事件与投稿经验的 `PostDeletedEvent`，与评论 / 经验同一事务；规则（谁收、什么类型、不提醒自己、同一条里既回复又 @ 只发一条）在 `domain.notification.policy.NotificationRules`。唯一索引 `(recipient_id, comment_id)` 保证一个人对一条评论最多一条提醒，编辑新增 @ 时用 `ON CONFLICT DO NOTHING` 跳过已被回复或已是经验作者的人。写入失败包成 500（`NotificationWriteFailedException`），评论一起回滚。
- 删除即删提醒（PAP-127）：评论删除（变占位或整条去掉）时删掉这条评论的全部提醒（走 `comment_id` 索引）；投稿经验删除时删掉这个对象下的全部提醒（走 `idx_notification_subject`）。不留「已删除」占位，未读数自然一致；评论与经验都不能恢复，提醒也不重建。
- 摘录：写入时经底座的 `PlainTextPort` 把评论转成纯文本（PAP-128 的规则），压成一行存前 300 个单位，字面的 `\` 与 `<` 转义、提及存 `<@ID>`（`ExcerptText`）；读取时换成 `@用户名` / `@昵称` / `@已注销用户`，再截到 120 个字素，`@名字` 放不下就停在它前面，不切成半个。评论编辑后刷新它全部提醒的摘录。
- 读：未读数走部分索引 `(recipient_id, type) WHERE read_at IS NULL`，接口按两个计数出 ETag，没变化 304；列表按 `notification_id` 倒序、每页 20；对象与触发人读时经登记表与 identity 取，匿名所有者触发的不取他的名片；对象已不可用时不给摘录。
- 清理：每日任务删 5 个月前的；注销清理经底座的 `UserDataCleanupGateway` 删收件人的全部提醒。

## 4.5 reaction 的存储与并发

- 三张表：明细 `reaction.helpful`（主键 `(user_id, target_type, target_id)`，`credited_user_id` 记这一条计入谁的获赞，匿名目标为空）、每个目标的有帮助数 `reaction.target_stats`、每个用户的获赞 `reaction.user_stats`。目标类型存名字 `'COMMENT'` / `'POST'`，ID 是评论 ID / 帖子 ID；没有统计行即为 0。
- 点：事务外解析目标（评论经讨论读端口 + 登记表，经验只读登记表）、查资格、已点过直接返回；事务里发表守卫计数 → 目标锁 `reaction.target:{种类}:{ID}`（advisory）→ 锁内复核 → `ON CONFLICT DO NOTHING` 插明细，真插入才两张统计表各 +1。取消只要登录，目标锁里真删掉才 −1。锁顺序：配额锁 → 业务对象锁 → 目标锁 → 目标行 → 用户行（用户 ID 升序）。
- 删除联动：同步订阅 `CommentDeletedEvent`（变占位与整条去掉）与 `PostDeletedEvent`，拿目标锁后删明细与统计行、扣回获赞；失败包成 500（`HelpfulWriteFailedException`），删除一起回滚。经验下的评论不连带。
- 给讨论串统计与对象统计（PAP-132 / 133）的表名、列与关联写法见工程 spec 第 9 节。

## 4.6 公开计数的读与重算（PAP-133）

- 批量对象统计 `POST /subjects/stats`（不要会话）挂在底座读端：`SubjectStatsReadAdapter` 只经 SQL 读收藏、讨论两张统计表，是本服务唯一一处跨子域读。主页获赞 `GET /users/{userId}/helpful-received` 读 `reaction.user_stats`。
- 按明细重算：底座定扩展点 `StatsRecountGateway<K>`，每项计数一个 `*RecountGatewayImpl`（各在自己子域的 app 层）；`RecountStatsHandler` 先让每项不加锁地找差异，再每个差异键一个短事务「先锁、再用新语句数、不一致才写」。锁与写方相同：收藏人数、获赞、对象评论总数锁统计行，有帮助数拿目标锁，楼统计拿楼锁（根评论已不在的楼统计行删掉）。设计见 `docs/patra/specs/2026-10-29-object-stats-design.md`。

## 4.7 集成事件（PAP-141）

interaction 把提醒要用的事实外送给通知服务：领域事件在业务事务里发布，`patra-tool-events`（Spring Modulith 事件发布登记当 Outbox）与业务同事务写一行 `public.event_publication`，提交后外送到 RabbitMQ 并等 broker 确认，失败留给补投任务。设计与事件契约见 `docs/patra/specs/2026-10-31-interaction-integration-events-design.md`（第 5 节）。

- 外送五种，交换机 `interaction.events`（topic），路由键是事件类型：`comment.published`、`comment.edited`、`comment.deleted`、`post.deleted`、`helpful.marked`。有帮助已取消只在服务内发布：通知服务对它没有动作，没有队列绑定的事件会被退回、一直补投。
- 契约是 `patra-interaction-api` 里的 record（`api.{子域}.event`，实现 `IntegrationEvent`，带 `ROUTING_KEY` 常量）；领域事件到集成事件的选择、路由、映射只在 boot 的 `InteractionEventsConfiguration` 与 `IntegrationEventMapper`，领域层不贴 `@Externalized`，映射只拷字段。
- 领域事件带 `eventId`（发布处生成，补投不变，消费方据此去重）；评论事件带正文版本（`discussion.comment.version`，发表为 1、每次正文确有变化加 1，消费方据此丢弃晚到的旧编辑）与 PAP-128 的全文纯文本。事件只带算收件人与判乱序要用的事实，对象的展示信息由消费方按 `subjectId` 读时现查。
- 上线后改外送领域事件的类名或字段，先确认 `event_publication` 是空的；新增外送事件时通知服务要先绑定它的路由键。

## 5. 运行

| 项 | 值 |
|---|---|
| 端口 | 6500；XXL-Job 执行器 6501（不映射到宿主机） |
| 网关前缀 | `/patra-interaction/**` |
| 错误码前缀 | `INTERACTION`（如 `INTERACTION-0404`） |
| 配置 | jar 里的 `application.yml` 放随代码变的默认值；库地址在 Nacos `patra-interaction.yaml`，账号与 xxl-job 管理端地址在 `patra-common.yaml` |
| 定时任务 | 执行器 `patra-interaction`；对象对账 `subjectReconciliationJob`（`0 30 6 * * ?`，超时 600 秒、不重试，PAP-112；只对外部对象，文献按 PMID、期刊按 NLM ID；catalog 换库重建或重跑导入后立即手动触发一次）、注销清理 `deletedUserCleanupJob`（`0 0 6 * * ?`，超时 600 秒、不重试；PAP-120 起是底座的公共流程：合并各子域有数据的用户，分批回查 identity，只删 `DELETED` 用户在各子域登记的个人数据：收藏、发表计数与提醒）、提醒清理 `notificationRetentionJob`（`0 45 6 * * ?`，超时 300 秒、不重试，PAP-123；删除北京时间 5 个自然月前的提醒）、统计重算 `statsRecountJob`（只手动触发，调度类型「无」，超时 600 秒、不重试，PAP-133；按明细修正各项公开计数，回写「差异 N 行：{…}」）、事件补投 `eventPublicationResubmitJob`（`0 * * * * ?`，路由「第一个」、阻塞处理「丢弃后续调度」、超时 60 秒、不重试，PAP-141；由 `patra-tool-events` 注册） |
| 上线 | `docs/patra/runbooks/v0.13-library-go-live-runbook.md`、`docs/patra/runbooks/v0.14-discussion-go-live-runbook.md`、`docs/patra/runbooks/v0.15-helpful-go-live-runbook.md`、`docs/patra/runbooks/v0.16-notification-go-live-runbook.md` |

## 6. 什么时候拆分

各种互动放在一个服务里，是因为单人在一台 mini 上维护，每多一个服务就多一套模块、JVM、库、Nacos 配置和 CD；而各种互动共享「用户对某个对象做动作」这一抽象和同一条读聚合。

当某个子域的**负载、发布节奏或存储引擎**明显和其余部分分化时（比如行为数据要换 ClickHouse、讨论的写入量远超收藏），把它剥成独立服务：

1. 先确认它和其他子域之间只剩公开包上的依赖（ArchUnit 已保证）。
2. 它的 schema 整体搬到新服务的库里；没有跨 schema 外键，不用先拆约束。
3. 它被别的子域用到的公开包换成 HTTP 契约，放进新服务的 api 模块。
