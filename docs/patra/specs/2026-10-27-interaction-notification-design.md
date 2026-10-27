# interaction 提醒工程设计（PAP-123，`notification` 子域）

> **Issue**：[PAP-123](https://linear.app/papertrace/issue/PAP-123)（GitHub #300），Linear Project P-PAP-11 v0.14 Discussion，Milestone BE Ready
> **版本**：[v0.14 Discussion](../release-specs/v0.14-discussion.md)（2.1.4、2.1.5；决策 A、D、E、H）
> **前序**：[底座扩展（PAP-120）](2026-10-27-interaction-foundation-extension-design.md) 第 9、12 节；[讨论（PAP-121）](2026-10-27-interaction-discussion-design.md) 第 7 节事件契约；[投稿经验（PAP-122）](2026-10-27-interaction-post-design.md) 的 POST 快照；[collection（PAP-113）](2026-10-24-interaction-collection-design.md) 的分区写法
> **页面要求**：设计简报 `patra-portal/docs/design-briefs/v0.14/discussion.md` §3.18、§3.19、§3.20、附录 A「PAP-123」、§10 缺口 4
> **下游**：PAP-126（门户提醒入口、提醒列表页）；接口形状同时写进共享笔记 `interaction-PAP-123-handoff.md`
> **日期**：2026-10-27
> **状态**：已实施（2026-10-27）；用户拍板第 12 节第 1、2 条，认可其余由我定的点

## 1. 要解决的问题

被回复、被 @ 的人回到 Patra 时，要在导航栏看到未读数，点进提醒列表，一步落到那条评论（spec 2.1.4）。提醒在评论写入的同一个事务里生成，不会丢；评论回滚时也不留提醒（决策 A）。表结构按以后通知中心的信箱设计，到时整体迁入（决策 D、spec 第 3 节 C）。

## 2. 范围

**做**：`notification` schema 与提醒表（按收件人哈希分 16 区，原生 SQL）；订阅三个讨论事件，按规则生成、更新提醒；未读数（条件请求，304）、按标签分页的列表、单条已读、全部已读；每日清理 5 个月前的提醒（XXL-Job）；注销清理登记到底座的公共流程；README、runbook 第 6.3 节、共享交接笔记。

**不做**（spec 第 3 节 C）：邮件、实时推送、偏好与按类型关闭、「A 等 3 人」聚合、Outbox、通知中心的其他部分。不改讨论、投稿经验、底座的任何代码。

## 3. 已定的前提

| 来源 | 内容 |
|---|---|
| spec 2.1.4 | 两类：「回复我的」（有人回复我的评论、有人评论我的经验）、「@ 我的」；不提醒自己；同一条评论里既回复又 @ 同一人只发一条、归「回复我的」（简报附录 A 第 4 条，用户 10/27 定）；编辑只对新增的 @ 发提醒；匿名作者触发的提醒，触发人显示「匿名作者」；未读数 60 秒轮询、没变化 304；点开即已读、可一键全部已读；跳转走评论永久链接；保留 5 个月，每日清理；只有站内 |
| 决策 A | 子域 `notification`、schema `notification`；同步订阅讨论的进程内事件，同一事务写入；只依赖事件契约（ArchUnit 已声明 `notification → discussion` 一条订阅边，只放行 `domain.discussion.event..`）；提醒表按收件人 `user_id` 哈希分 16 区、原生 SQL |
| 决策 D | 表结构按信箱设计：收件人、类型、触发人、对象、评论、创建时间、已读时间；每条一行，不聚合 |
| 决策 H | 注销后删除他的提醒（他作为收件人的）；走底座的公共流程 `UserDataCleanupGateway` |
| 2.1.5 | 任何表只存用户 ID，不存用户名、昵称、头像 |
| PAP-121 交接 | 三个事件的字段（第 5 节引用）；同步 `@EventListener`，不要 `@TransactionalEventListener`；订阅方抛异常整个评论事务回滚；永久链接要的 key 读时经 `SubjectReadPort` 取，不存进提醒；摘录从 `body` 自己截，`<@用户ID>` 读时换名字；订阅方出错不要抛 `IllegalStateException`（全局错误引擎会映射成 422） |
| PAP-122 交接 | POST 登记行快照：`journal` = 期刊全称、`abbreviation` = 缩写（可空）、`publishedDate` = 投稿月份 `yyyy-MM`；删除后 `state = UNAVAILABLE`、快照不变 |
| 简报附录 A | 未读数：总数 + 两个标签各自的数，ETag → 304；列表按标签、创建时间倒序、游标、每页 20；每条要类型、触发人（或匿名 / 已注销）、对象上下文、评论 id、摘录与是否已删除、创建时间、是否已读、永久链接要的 id；单条已读与全部已读返回新的未读数 |

## 4. 生成规则（`domain.notification.policy.NotificationRules`）

### 4.1 类型与标签

| 类型 `NotificationType` | 动作文案（门户） | 标签 `NotificationTab` |
|---|---|---|
| `COMMENT_REPLY` | 回复了你的评论 | `replies`（回复我的） |
| `POST_COMMENT` | 评论了你的投稿经验 | `replies` |
| `MENTION` | 在评论中 @ 了你 | `mentions`（@ 我的） |

### 4.2 评论已发表（`CommentPublishedEvent`）

按下面的顺序列候选收件人，同一人只取第一次出现时的类型：

1. `parentAuthorId`（非空）→ `COMMENT_REPLY`。
2. 根评论（`parentCommentId == null`）且 `subjectType == POST` 且 `subjectOwner` 非空 → `subjectOwner.userId()` 得 `POST_COMMENT`。**楼里的回复不提醒经验作者**，除非回复的正是作者本人的评论（走第 1 条）。（用户 2026-10-27 拍板：「按推荐就好了」）
3. `mentionedUserIds` 按顺序 → `MENTION`。

最后去掉作者本人（`authorId`）。顺序保证了「既回复又 @ 同一人只发一条、归回复我的」；经验作者在根评论里同时被 @，也只发一条 `POST_COMMENT`。

### 4.3 评论已编辑（`CommentEditedEvent`）

1. 这条评论已有的提醒（所有收件人）刷新摘录（第 6.2 节），让列表显示编辑后的内容。
2. `addedMentionedUserIds` 去掉作者本人 → `MENTION`；这个人对这条评论已有任何一条提醒（被回复、作者、之前 @ 过）时不再发（表上的唯一索引兜底，`ON CONFLICT DO NOTHING`）。
3. 编辑删掉了某人的 @，已发的提醒保留（同 GitHub），不撤回。

### 4.4 评论已删除（`CommentDeletedEvent`）

这条评论的所有提醒：`comment_deleted = true`、摘录清空（删除的内容不再留在别人的提醒里）。占位与整条去掉一样处理，不存 `placeholder`。提醒本身保留，列表显示「该评论已删除」，仍去单楼视图（简报 §2.2 第 3 条）：留了占位的，单楼视图显示占位；整条去掉的，单楼视图回 404 `COMMENT_NOT_FOUND`，门户按「该评论已删除」显示（讨论区本来就要处理这个 404，见交接笔记）。

### 4.5 触发人遮蔽

事件的 `authorAnonymous` 为真（作者是这条经验的匿名所有者）时，提醒行写 `actor_anonymous = true`。读接口对这样的行不返回触发人的任何信息（`actor: null`、`actorAnonymous: true`），用户 ID 只留在库里。匿名标记发布后不能改（决策 E），写入时定下即可，不必读时再查登记表。

## 5. 存储（`db/migration/notification/`）

两条迁移：`V20261027190000__create_notification_schema.sql`（建 schema）、`V20261027190100__create_notification_tables.sql`（建表），时间戳晚于讨论的 `20261027161220`。

### 5.1 `notification.notification`

| 列 | 类型 | 说明 |
|---|---|---|
| `recipient_id` | `BIGINT NOT NULL` | 收件人；分区键 |
| `notification_id` | `BIGINT NOT NULL` | 雪花 ID（时间有序，也是列表的游标） |
| `type` | `VARCHAR(32) NOT NULL` | `COMMENT_REPLY` / `POST_COMMENT` / `MENTION`（CHECK） |
| `actor_id` | `BIGINT NOT NULL` | 触发人 |
| `actor_anonymous` | `BOOLEAN NOT NULL` | 触发人是对象的匿名所有者 |
| `subject_id` | `BIGINT NOT NULL` | 对象在 `subject.registry` 的本地 ID，不建跨 schema 外键 |
| `comment_id` | `BIGINT NULL` | 评论；信箱里以后不是评论引起的类型为空，本版三类都有 |
| `excerpt` | `TEXT NULL` | 摘录：评论转成的纯文本前 300 字素，提及存 `<@ID>`（第 6.2 节写法）；评论删除后为空 |
| `excerpt_truncated` | `BOOLEAN NOT NULL DEFAULT false` | 摘录是否截断 |
| `comment_deleted` | `BOOLEAN NOT NULL DEFAULT false` | 评论已删除（CHECK：为真时 `excerpt` 为空） |
| `created_at` | `timestamptz(6) NOT NULL` | 生成时刻：取事件的 `publishedAt` / `editedAt`（与评论表的时间对得上） |
| `read_at` | `timestamptz(6) NULL` | 已读时刻；空 = 未读 |

`PRIMARY KEY (recipient_id, notification_id)`，`PARTITION BY HASH (recipient_id)` 16 区（`_p00` ~ `_p15`，同 collection）。

索引：

| 索引 | 用途 |
|---|---|
| 主键 `(recipient_id, notification_id)` | 列表按 `notification_id DESC` 倒序扫，类型在扫描时过滤（每人 5 个月的量） |
| `idx_notification_unread (recipient_id, type) WHERE read_at IS NULL` | 未读数、全部已读 |
| `uk_notification_comment (recipient_id, comment_id) WHERE comment_id IS NOT NULL`（唯一） | 一个人对一条评论最多一条提醒（第 4.3 节第 2 条的兜底） |
| `idx_notification_comment (comment_id) WHERE comment_id IS NOT NULL` | 编辑刷新摘录、删除标记（按评论找全部收件人） |
| `idx_notification_created (created_at)` | 每日清理 |

### 5.2 为什么这样存

- **摘录存进提醒行**：提醒子域只能依赖事件契约，读不到讨论的表，摘录只能在事件到达时截下来存。每条评论最多 11 个收件人，重复存几份摘录可以接受。
- **对象 key、标题、期刊快照不存**：文献的 key 在 catalog 重导后会变，读时经 `SubjectReadPort.findByIds` 取（PAP-121 交接）。
- **触发人只存 ID**：名字、头像读时经 `UserDirectoryPort.findCards` 取（仓库规则「用户名只存 identity」）。

## 6. 写入（`app.notification.eventhandler.CommentNotificationListener`）

### 6.1 订阅

三个同步 `@EventListener` 方法，分别处理第 4.2、4.3、4.4 节。不加 `@Transactional`：讨论的写入事务已在，监听方法在同一线程、同一连接上执行。生成的提醒用一条多行 `INSERT … ON CONFLICT (recipient_id, comment_id) WHERE comment_id IS NOT NULL DO NOTHING` 写入，`notification_id` 由雪花生成器给，`created_at` 取事件里的时刻（发表用 `publishedAt`，编辑新增 @ 用 `editedAt`）。

**出错**：监听方法里捕获非 `DomainException` 的 `RuntimeException`，包成 `NotificationWriteFailedException`（`ApplicationException`，错误码 `HttpStdErrors.of("INTERACTION").INTERNAL_ERROR()`，500，原始异常留在原因链里）再抛出，整个评论事务回滚。错误引擎先按 `ApplicationException` 的错误码映射（`DefaultErrorResolutionEngine` 第 1 优先级），不会沿原因链把它映射回 409；回滚流程测试断言状态码就是 500。不让数据库异常原样冒出去：`DataIntegrityViolationException` 会被 `JpaErrorMappingContributor` 映射成 409，按类名兜底的会被映射成 422，都会让发评论的人看到误导的原因。

### 6.2 摘录（用户 2026-10-27 选 B：后端给纯文本）

分两步，写入时转纯文本并存下，读取时换提及、截到 120 字：

1. **写入**（`domain.notification.port.excerpt.PlainTextPort`，实现 `infra.notification.excerpt.CommonmarkPlainTextAdapter`，commonmark-java 0.30.1）：解析评论 Markdown，遍历语法树输出纯文本：
   - 文字、强调、粗体、行内代码、代码块取文字；链接取链接文字（不要网址）；自动链接（PMID、DOI、网址）取原文；图片取替代文字；行内 HTML 原样当文字（门户不渲染 HTML，评论里看到的也是原文）。
   - 段落、列表项、引用、换行之间用一个空格隔开；连续空白合成一个空格，去掉首尾空白。
   - 提及：普通文字与代码块、HTML 块里的 `<@数字>`，数字在事件的 `mentionedUserIds` 里才算提及，保留成 `<@ID>` 标记（讨论只把反引号行内代码当字面，块里的 @ 照常解析并提醒）；反引号行内代码里的、名单外的、超出 long 范围的是字面文字。转义的 `\<@数字>` 解析后与普通文字相同，在名单里时也当提及（接受）。
   - 存储写法 `ExcerptText`（domain 值对象）：`\` 是转义符，字面的 `\` 存成 `\\`、字面的 `<` 存成 `\<`；提及存成 `<@ID>`。这样读取时不会把字面文字误当成提及。
   - 存前 300 个扩展字素簇（`BreakIterator`），不切开转义和提及标记，超出时 `excerpt_truncated = true`。
2. **读取**（`domain.notification.policy.ExcerptRules.render`）：按存储写法还原；提及换成 `@用户名`（主页对收件人可见）/ `@昵称`（不可见，同讨论区）/ `@已注销用户`（identity 不认识）；再截到 120 个扩展字素簇，截了或存的时候已截，`excerptTruncated = true`（门户在末尾加省略号）。

为什么分两步：用户名只存 identity，存储里只能留用户 ID；而「120 字」要按换完名字后的文字算。存 300 字素给换名后的长度变化留余量。

转换出错（解析器异常）按第 6.1 节包成 500，评论一起回滚。commonmark-java 对任何输入都能产出语法树，正常情况下不会发生。

## 7. 读与已读（`app.notification.usecase`）

都要登录（`CurrentUserPort.require()`，未登录 401），只能读写自己的提醒。

### 7.1 未读数 `NotificationQueryService.unreadCounts()`

一条 SQL：`SELECT type, count(*) … WHERE recipient_id = ? AND read_at IS NULL GROUP BY type`（走部分索引）。得 `UnreadCounts(replies, mentions)`，`total = replies + mentions`。

条件请求在 adapter 做：ETag 由两个数算出（`"<replies>-<mentions>"`，强 ETag），请求的 `If-None-Match` 命中时 304、不带响应体。200 与 304 **都用 `ResponseEntity` 显式带** `ETag` 与 `Cache-Control: private, no-cache`（浏览器每次都来问，按 ETag 省下响应体）：不能靠 `checkNotModified` 返回 null，否则 304 上可能被 Spring Security 补成 `no-store`，浏览器丢掉缓存条目，轮询退化成每次全量。流程测试断言 304 的 `Cache-Control` 不含 `no-store`。响应体只有计数，同一个浏览器里换了账号、计数相同时用缓存的响应体也是对的。

### 7.2 列表 `NotificationQueryService.page(tab, cursor)`

- `tab` = `replies`（默认）/ `mentions`，别的值 422；`cursor` 是上一页最后一条的 `notificationId`（正整数字符串），写法不对 422。
- 每页 20 条，按 `notification_id` 倒序（与创建时间同序）；多取一条判断有没有下一页。
- 对象已不可用（经验已删除、文献已下线）时，不返回摘录（`excerpt: null`），触发人照常：经验删除后「下面的回复不再展示」，提醒不能成为这些回复的另一个出口。对象 key 为空（外部来源行已消失）时 `subject.key` 为 null，门户不渲染链接。
- 拼视图：一次 `SubjectReadPort.findByIds`（这一页的全部对象）；`UserDirectoryPort.findCards` 取非匿名的触发人 + 摘录里提及的全部用户（访客 = 收件人，主页可见性按 identity 规则）。`findCards` 一次最多 100 人（`UserDirectoryPort.MAX_USERS`），一页 20 条的触发人加摘录里的提及可能超过，按 100 人一批分批取。identity 不可用时 503（底座异常直接传播），门户显示列表页的失败态。

### 7.3 已读

- **单条** `MarkNotificationReadHandler`：`UPDATE … SET read_at = :now WHERE recipient_id = :me AND notification_id = :id AND read_at IS NULL`；不存在（或不是自己的）→ 404 `NOTIFICATION_NOT_FOUND`；已读的再标一次不报错。返回新的未读数。
- **全部** `MarkAllNotificationsReadHandler`：两个标签一起，`UPDATE … WHERE recipient_id = :me AND read_at IS NULL`。返回新的未读数（两个都是 0，除非这之间又来了新的）。

## 8. 清理

### 8.1 5 个月保留期

- XXL-Job handler `notificationRetentionJob`（`adapter.notification.scheduler.NotificationRetentionScheduleJob`），无参数；命令 `PurgeExpiredNotificationsCommand` → `PurgeExpiredNotificationsHandler`。
- 截止时刻：注入的 `Clock` 当前时刻按北京时间减 5 个自然月；`DELETE … WHERE created_at < :cutoff`（已读未读都删），返回删除行数写进任务日志。量很小（5 个月 × 每人每天几条），一条语句即可，不分批。
- 调度：Cron `0 45 6 * * ?`（在 06:00 注销清理、06:30 对象对账之后），超时 300 秒，不重试；失败次日自动重跑。登记写进 runbook 第 6.3 节。

### 8.2 注销

`app.notification.service.NotificationUserDataCleanupGatewayImpl implements UserDataCleanupGateway`：`subdomain()` = `notification`；`findUsersAfter` = 有提醒的收件人（`SELECT DISTINCT recipient_id … WHERE recipient_id > ? ORDER BY 1 LIMIT ?`）；`deleteAllOf` 删他作为收件人的全部提醒。他作为触发人的提醒留在别人那里，读时 identity 不认识他，显示「已注销用户」（spec 2.1.5）。底座的 `deletedUserCleanupJob` 自动纳入，不另建任务。

## 9. 接口（`adapter.notification.rest.MyNotificationController`，网关前缀 `/patra-interaction`）

| 方法与路径 | 成功 | 说明 |
|---|---|---|
| `GET /me/notifications/unread-count` | 200 `UnreadCounts` / 304 | 带 `ETag`；请求带 `If-None-Match` 且没变化时 304 |
| `GET /me/notifications?tab=replies\|mentions&cursor=` | 200 `{ items: Notification[], nextCursor }` | 每页 20；`nextCursor` 为 null 即没有下一页 |
| `PUT /me/notifications/{notificationId}/read` | 200 `UnreadCounts` | 404 `NOTIFICATION_NOT_FOUND` |
| `PUT /me/notifications/read` | 200 `UnreadCounts` | 全部已读（两个标签） |

`UnreadCounts`：`{ "total": 3, "replies": 2, "mentions": 1 }`。

`Notification`：

```json
{
  "notificationId": "…",
  "type": "COMMENT_REPLY",          // COMMENT_REPLY / POST_COMMENT / MENTION
  "read": false,
  "createdAt": "2026-10-27T08:00:00Z",
  "actor": { 作者对象，同 PAP-121 / PAP-122 } | null,
  "actorAnonymous": false,          // true 时 actor 恒为 null，显示「匿名作者」；不匿名时 actor.state == DELETED（identity 返回的占位，名字与头像为 null）或 actor 为 null，都显示「已注销用户」
  "subject": {
    "type": "publication",          // publication / post
    "key": "123",                   // 门户 URL 里的 ID，可能为 null（不渲染链接）；永久链接 /papers/{key}?comment= 或 /posts/{key}?comment=
    "available": true,              // false：对象已删除 / 已失效（经验已删除 → 「一条已删除的投稿经验」）；此时 excerpt 为 null
    "title": "文献标题",             // 文献；经验为 null
    "journal": "期刊全称",           // 经验；文献为 null
    "abbreviation": "J Am Heart Assoc", // 经验的期刊缩写，可空（空时用全称）
    "submittedMonth": "2026-03",    // 经验的投稿月份；文献为 null
    "mine": true                    // 经验是不是收件人自己的（「你的投稿经验」）；文献恒 false
  } | null,                         // 登记表里找不到（不应出现）时为 null
  "commentId": "…",
  "commentDeleted": false,
  "excerpt": "同意。@chen-yu 补充一点：文中的倾向评分……" | null,   // 纯文本 ≤ 120 字素，提及已换成名字；评论删除或对象不可用时为 null
  "excerptTruncated": true          // 截断过，门户在末尾加省略号
}
```

`actor` 的形状与讨论、投稿经验的作者对象相同：`userId`、`username`、`displayName`、`avatarUrl`、`avatarPalette`、`state`、`profileVisible`；主页对收件人不可见时 `username`、`avatarUrl` 为 null。门户只管显示 `excerpt`，不用解析 Markdown、不用换提及。

**错误**（错误码前缀 `INTERACTION`）：

| 情况 | HTTP | `traits[]` |
|---|---|---|
| 未登录 | 401 | |
| `tab`、`cursor` 不合法 | 422 | `RULE_VIOLATION` |
| 路径里的 `notificationId` 不是数字 | 400 | —（Spring 类型转换，同 collection 的路径参数） |
| 提醒不存在 / 不是自己的 | 404 | `NOT_FOUND`、`NOTIFICATION_NOT_FOUND` |
| identity 不可用（列表） | 503 | `DEP_UNAVAILABLE` |
| 订阅方写提醒失败（发生在发评论、编辑、删除的请求里） | 500 | — |

## 10. 代码落点

| 层 | 包 | 内容 |
|---|---|---|
| domain | `domain.notification.model.enums` | `NotificationType`（带所属标签）、`NotificationTab`（`fromParam`） |
| | `domain.notification.model.vo` | `NotificationDraft`（收件人、类型、触发人、匿名、对象、评论、摘录）、`ExcerptText`（存储写法：追加字面文字 / 提及、解码、取提及 ID、按字素截断） |
| | `domain.notification.model.read` | `NotificationEntry`（一行）、`UnreadCounts` |
| | `domain.notification.policy` | `NotificationRules`（第 4.2、4.3 节，直接收讨论的事件 record）、`ExcerptRules`、`NotificationRetention`（截止时刻） |
| | `domain.notification.exception` | `NotificationErrorTrait`（`NOTIFICATION_NOT_FOUND`）、`NotificationNotFoundException`、`InvalidNotificationRequestException` |
| | `domain.notification.port.excerpt` | `PlainTextPort`（Markdown → `ExcerptText`） |
| | `domain.notification.port.repository` / `port.read` | `NotificationRepository`（雪花 ID、批量插入、刷新摘录、标删除、已读、全部已读、清理、按收件人删、有提醒的收件人）、`NotificationReadPort`（一页、未读数） |
| app | `app.notification.eventhandler` | `CommentNotificationListener`、`NotificationWriteFailedException` |
| | `app.notification.usecase.read` / `usecase.purge` | 已读两个命令、清理命令与处理器 |
| | `app.notification.usecase.query` | `NotificationQueryService` 与视图 |
| | `app.notification.service` | `NotificationUserDataCleanupGatewayImpl` |
| infra | `infra.notification.excerpt` | `CommonmarkPlainTextAdapter` |
| | `infra.notification.persistence` | `NotificationRepositoryAdapter`、`NotificationReadAdapter`（`JdbcClient` 原生 SQL，每条语句带 `recipient_id`，按评论更新的两条除外） |
| adapter | `adapter.notification.rest` | `MyNotificationController`、响应 record |
| | `adapter.notification.scheduler` | `NotificationRetentionScheduleJob` |

订阅边 `notification → discussion` 按子域声明，notification 的任何一层都可以读 `domain.discussion.event`，规则直接收事件 record。四层都有 `notification` 包（规则 22b）。不改 `InteractionArchitectureTest`、`InteractionMigrationIT`；没有新模块。新依赖只有 `org.commonmark:commonmark:0.30.1`（无传递依赖；版本放 `gradle/libs.versions.toml`，在 `PatraDependencyManagement` 里统一管理，interaction-infra 不写版本号）；改了 `*.gradle.kts`，提交前提醒用户在 IDEA 里点「加载 Gradle 更改」。

## 11. 测试

| 测试 | 层 | 内容 |
|---|---|---|
| `NotificationRulesTest` | domain 单元 | 根评论、回复、经验根评论、楼里回复（不提醒经验作者）、@ 列表；不提醒自己；既回复又 @ 只发一条 `COMMENT_REPLY`；经验作者被 @ 只发 `POST_COMMENT`；编辑只对新增 @ |
| `ExcerptTextTest`、`ExcerptRulesTest` | domain 单元 | 转义往返；300 字素截断不切开标记与转义；提及换成用户名 / 昵称 / 已注销用户；120 字素截断与截断标记；emoji 等多码点字素不切开 |
| `CommonmarkPlainTextAdapterTest` | infra 单元 | 粗体、斜体、引用、列表、链接、行内代码、自动链接、图片、HTML 的纯文本；空白合并；提及只认名单里的、行内代码里的是字面；转义的 `\\<@数字>` |
| `NotificationTabTest`、`NotificationRetentionTest` | domain 单元 | 参数解析；北京时间减 5 个自然月（月末、跨年） |
| `CommentNotificationListenerTest` | app 单元 | 三个事件各自调仓储；数据库异常包成 500 类异常，领域异常原样传播 |
| `NotificationQueryServiceTest` 等 | app 单元 | 匿名遮蔽、对象上下文、`mine`、提及实体；已读返回新计数 |
| `NotificationRepositoryAdapterIT`、`NotificationReadAdapterIT` | infra 集成（真实 PG） | 16 区落库；唯一索引去重；按评论刷新摘录、标删除；已读与全部已读只动自己；清理只删截止之前；按收件人删；有提醒的收件人游标 |
| `NotificationFlowIT` | boot 流程（继承 `InteractionFlowITSupport`，不单独 `@Import`） | AC 全部：自己回复自己不提醒；既回复又 @ 只一条；编辑新增 @ 只提醒新增的人；经验根评论提醒作者、楼里回复别人不提醒作者；删除后列表显示已删除、摘录为空；未读数 304（带 `If-None-Match` 再取、标已读后变 200）；单条与全部已读；他人视角拿不到匿名作者的用户 ID（响应全文不含该 ID）；清理任务只删超过 5 个月的；注销后提醒为 0 |
| `NotificationRollbackFlowIT` | boot 流程 | **评论回滚不留提醒**：测试里给 `discussion.comment` 建一个延迟到提交时触发、必定报错的约束触发器，发一条会产生提醒的评论 → 非 2xx，评论、提醒都是 0（与监听方法的执行顺序无关）；**提醒写入失败评论回滚**：给 `notification.notification` 建 `BEFORE INSERT` 报错触发器 → 500，评论、发表计数、提醒都没落库。触发器在 `finally` 里删掉 |

测试 429 之类不涉及。`RestTestClient` 发 `If-None-Match` 用 `headers`。

## 12. 由我定的点 / 待拍板

1. **用户已拍板（2026-10-27「按推荐就好了」）**：楼里的回复不提醒经验作者（第 4.2 节第 2 条），简报 §10 缺口 4。理由：同贴吧，楼主只收新楼的提醒，楼中楼只提醒被回复的人，讨论热起来时作者不被别人之间的对话刷屏；回复作者本人时照样有「回复了你的评论」。
2. **用户已拍板（2026-10-27 选 B）**：后端引入 commonmark-java 转纯文本，截到 120 字，提及换成名字，门户只管显示（第 6.2 节）。用户说以后可以另开一版深入讨论摘录的处理。存储写法、300 / 120 两个长度、提及只认事件名单里的 ID 由我定。
3. 编辑时刷新已有提醒的摘录；删掉 @ 不撤回提醒；评论删除清空摘录。
4. 单条已读对不存在的提醒回 404（门户忽略这个失败照常跳转）；已读的再标不报错。
5. 列表固定每页 20，不开放 `size`。
6. 未读数只用 ETag，不加 `Last-Modified`：计数没有一个自然的「最后修改时间」（已读也会让它变）。
7. 评论整条去掉后不存「占位 / 整条」的区别，门户把单楼视图的 404 `COMMENT_NOT_FOUND` 显示成「该评论已删除」（第 4.4 节）。
8. 对象不可用时隐藏摘录与提及，触发人照常（第 7.2 节）。
9. 清理任务 Cron `0 45 6 * * ?`、超时 300 秒、不重试。

## 13. 实施记录（2026-10-27）

按 `docs/patra/plans/2026-10-27-interaction-notification.md` 在本会话内联执行（11 个任务），之后 opus 整分支终审一次。

### 13.1 与本设计、计划的偏离

| 偏离 | 原因 |
|---|---|
| commonmark 依赖走版本目录别名 `libs.commonmark`（`gradle/libs.versions.toml`），没有写进 `PatraDependencyManagement` | 仓库对直接依赖的惯例就是版本目录（citeproc、jsoup 等），`PatraDependencyManagement` 只放 BOM 与安全覆盖；版本仍只有一处 |
| 计划里「`p*0.05 与 q*2` 不是强调」的预期改为按 CommonMark 判断（它就是强调，纯文本去掉星号；`2 * 3` 两边有空格才不是） | 门户按同一规范渲染成斜体，纯文本跟随渲染结果 |
| 代码块、HTML 块里名单内的标记也换成提及（终审 Minor 2） | 讨论只把反引号行内代码当字面，块里的 @ 照常解析并发提醒；不换的话摘录里会出现裸的用户 ID |
| `ExcerptText.mentionIds()` 返回 `List`（保序去重），不是 `Set` | 规范要求拷贝用 `copyOf`，`Set.copyOf` 不保序 |
| 路径里的 `notificationId` 不是数字回 400，不是 422 | 沿用 collection 的 `@PathVariable long` 写法；流程测试钉住 |

### 13.2 实施中发现的坑

- `switch` 上的 record 解构模式（`case Mention(long userId) ->`）让 SpotBugs 报 `DLS_DEAD_LOCAL_STORE`（javac 生成的局部变量），改用 `instanceof` 类型模式。
- `UserDirectoryPort.findCards` 对空集合抛异常：一页全是匿名所有者触发、摘录里也没有提及时，不能调用。
- `rs.wasNull()` 反映的是最后读的那一列：先读 `comment_id` 再读别的列，NULL 会被映射成 0（终审 Minor 1，改用 `getObject(…, Long.class)`）。
- 提及标记的正则允许 19 位数，超出 long 的值 `parseLong` 抛异常、被包成 500 让评论回滚（终审 Important 1）；超界按字面文字处理。讨论把用户写的这种文字转义存储，commonmark 解析后又还原成普通文字，所以真能碰到。
- Java 默认的 `\s` 不含全角空格 U+3000 与 NBSP，合并空白要 `(?U)\s+`。
- identity 对已注销用户返回 `state: DELETED`、名字与头像为 null 的占位名片，不是省略；门户按 `state` 判断「已注销用户」。
- 父项目 `:patra-api:patra-interaction` 没有 `check` / `spotlessApply` 任务，要逐个子模块调用。

### 13.3 终审（opus，全部改动）

1 条 Important、7 条 Minor，全部修复：

| 发现 | 处理 | 测试 |
|---|---|---|
| Important 1：超出 long 的提及标记让发表、编辑返回 500 | 超界按字面文字 | `CommonmarkPlainTextAdapterTest.should_keep_out_of_range_token_literal` RED→GREEN |
| Minor 1：NULL 的 `comment_id` 读成 0 | `getObject(…, Long.class)` | `NotificationReadAdapterIT.should_read_null_comment_id_as_null` RED→GREEN |
| Minor 2：块里的提及显示裸 ID | 块里名单内的标记也是提及 | `should_turn_listed_token_in_blocks_into_mention` RED→GREEN |
| Minor 3：缺转义标记的用例 | 补用例（行为本来就对） | `should_keep_escaped_unlisted_token_literal` |
| Minor 4：spec 里「已注销用户」的判断写错 | 第 9 节改为看 `actor.state` | — |
| Minor 5：全角空格不合并 | `(?U)\s+` | `should_collapse_unicode_whitespace` RED→GREEN |
| Minor 6：非数字 `notificationId` 的状态码与 spec 不符 | spec 改为 400（同 collection），流程测试钉住 | `NotificationReadFlowIT.should_reject_bad_tab_and_require_login` |
| Minor 7：枚举与私有构造器缺 `///`；`mentionIds` 用 `unmodifiableSet` | 补注释；改返回 `List.copyOf` | — |

终审未判定、由我定为接受的：转义的 `\<@ID>` 在名单里时当提及（第 6.2 节已写明）；有人按用户名 @ 了匿名经验的作者时摘录显示其用户名（与讨论区相同，发帖人本来就知道这个名字）；截到 120 字可能切开 `@用户名`；对象不可用的行也取了摘录里被提及者的名片（多几次批量查询）。

### 13.4 已知限制

- 摘录截断按字素切，可能把末尾的 `@用户名` 切成半个名字；门户以省略号收尾。用户说摘录处理以后另开一版讨论。
- 编辑删掉某人的 @ 不撤回他已收到的提醒（第 4.3 节第 3 条）。
- 未读数的 ETag 只看两个计数：同一浏览器换账号、计数相同时浏览器会用缓存的响应体，内容一样，不影响正确性。

### 13.5 验证

- interaction 各子模块 `check`（含 ArchUnit、SpotBugs、spotless）+ infra / boot 两个 `integrationTest`：通过（终审修复后重跑）。
- 全仓 `./gradlew check`：BUILD SUCCESSFUL（终审前）。
- 新增测试：domain 单元 21、app 单元 17、infra 单元 12、infra 集成 15、boot 流程 17。
