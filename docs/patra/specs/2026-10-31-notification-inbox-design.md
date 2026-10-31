# 通知服务接管站内提醒：信箱工程设计（PAP-143）

> 状态：设计定稿，实施中
> 日期：2026-10-31
> Issue：PAP-143（GitHub #330），Linear Project P-PAP-13 v0.16 Notification，Milestone BE Ready
> 依据：release spec `docs/patra/release-specs/v0.16-notification.md` 决策 A、D、H、J，第 3 节 C、L，第 5 节 D2；
> PAP-141 事件契约（`docs/patra/specs/2026-10-31-interaction-integration-events-design.md` 第 5 节）；
> 通知服务骨架（`docs/patra/specs/2026-10-30-notification-skeleton-design.md`）；PAP-128 纯文本规则；PAP-142 偏好与注销清理底座；
> 下游需求：PAP-144（有帮助）、PAP-145（实时推送）、PAP-146（提醒邮件）、PAP-147（门户）
> 编排会话代用户拍板的部分见第 17 节

## 1. 要解决的问题

现在站内提醒住在 interaction 的 `notification` 子域：评论写入的同一个事务里同步写提醒，收件人规则在 `NotificationRules`。v0.16 把提醒交给新服务 `patra-notification`（决策 A），interaction 改成只发「发生了什么」的集成事件（PAP-141）。本 Issue 做通知服务这一头：

1. 消费 interaction 的四种事件（评论发表 / 编辑 / 删除、投稿经验删除），按搬过来的收件人规则写信箱。
2. 事件是「至少一次、不保证顺序」送来的：同一事件到两次只算一次；删除先于发表到达时不生成提醒；晚到的旧编辑不覆盖新的。
3. 门户要的接口：未读数（含 304）、按标签分页的列表、单条已读、全部已读。
4. 5 个月保留、账号注销时清理；拉未读数时记下「在站」（决策 H）。
5. interaction 删掉 `notification` 子域和它的表，不迁移旧数据（第 3 节 L）。
6. 列表要显示的对象信息（链接 key、标题、期刊、是否还在）由 interaction 新增的内部接口读时现查（本 Issue 写）。

## 2. 先讲清几个概念

### 2.1 幂等消费：同一封信收到两次，只处理一次

**是什么**：RabbitMQ 保证消息「至少送到一次」，不保证「只送一次」：interaction 的补投任务可能把已经送到的事件再发一遍（决策 C），消费者处理完还没来得及回 ack 就断线，消息也会重投。**幂等**是指同一条消息处理几次，结果和处理一次一样。

**防什么**：同一条回复生成两条提醒、未读数多算。

**业界**：microservices.io 叫 Idempotent Consumer，标准做法是消费方建一张「已处理消息」表，消息 ID 做主键，和业务数据在同一个事务里写；第二次到达时插入冲突，直接跳过。有些场景业务数据本身天然幂等（比如「把 X 设为 5」），可以不建表，但「插入一条提醒」不是。

**我们**：用 PAP-137 的 `ProcessedEventPort.markProcessed(队列名, eventId)`，表 `public.processed_event` 已由集成分支 `a1eedc4d9` 建好。每个处理器在事务里第一步调它，返回 `false` 就结束。业务失败回滚时这条记录一起回滚，重试不会被误判成重复。

**代价**：每个事件多一次插入；表随事件数增长（清理见第 15 节遗留）。另外信箱的写入本身也做了一层幂等（同一人对同一条评论最多一条提醒，第 6.2 节），两层互为兜底。

### 2.2 乱序：墓碑和版本号

**是什么**：先发生的事件不一定先被处理。比如「评论发表」第一次处理时数据库抖了一下进了重试，几秒后「评论删除」反而先处理完。

**防什么**：被删的评论又冒出提醒；用户改过的评论，提醒里显示改之前的话。

**业界**：Kafka 这类按分区保序的系统靠分区键把同一实体的事件排队；RabbitMQ 一旦重投就不保序，只能在消费端兜底。两个常用手段：

- **墓碑**（tombstone）：删除时不只删数据，还留一条「这个东西已经删了」的记录。后到的「发表」看到墓碑就丢弃。Cassandra、Kafka 的日志压缩都用这个词。
- **版本号**：每次修改版本加一，消费方只接受比手上更新的版本（乐观并发控制同一个思路）。不用时间戳：同一毫秒、时钟回拨都会分不出新旧。

**我们**：

- 每条评论在信箱里有一行「评论快照」：最新版本号、最新纯文本、是否已删除（墓碑就是这一行的删除标记）。发表、编辑只在版本更新时覆盖正文；删除把这一行标成墓碑并删掉它的全部提醒，之后同一条评论的任何事件都丢弃。
- 投稿经验删除时记一条「对象墓碑」，之后该经验下评论的事件都丢弃。
- PAP-141 给评论加了整数版本（发表 = 1，每次编辑 +1）。

**代价**：多两张小表；每种事件都要想「先到」「晚到」两种情况，全部列在 7.4 的表里并逐条有测试。

### 2.3 读时组合：展示信息不进事件

**是什么**：提醒列表除了「谁回复了你」，还要显示「在哪」：文献标题、经验的期刊与投稿月份、点开去哪（门户链接里的 key）、对象还在不在。这些信息归 interaction 管。**读时组合**（microservices.io 叫 API Composition）是指读列表的那一刻，按 ID 去拥有数据的服务批量取，拼好再返回。

**防什么**：把展示信息复制一份存在通知服务里，原件变了副本不知道。具体到 Patra：文献的 key 是 catalog 的内部 ID，catalog 重导后会变（interaction 的 `CommentPublishedEvent` Javadoc 明写了这一点），存下来的链接重导后就是 404；文献下线也没有事件通知，副本会一直显示「还在」。

**业界**：两种都常见。Discourse 在提醒里存一份标题快照（标题改了提醒不跟着改）；GitHub 通知 API 只给对象的 URL，客户端自己去取。用户名的处理和这个同理，Patra 早就定了「只存 ID，展示时现查」。

**我们**：事件只带 `subjectId` 与类型（PAP-141 已按此定稿）。通知服务读列表时：

- 按 `subjectId` 调 interaction 新增的内部接口取对象视图；interaction 挂了就降级，对象信息给 `null`，门户不出链接（门户已支持）。
- 按用户 ID 调 identity 的 `/summaries` 取名片（触发人、摘录里 @ 到的人）。identity 挂了整页 503（同现在）：没有名字的提醒看不懂。

收件人仍然全部来自事件，决策 A 的「通知服务不回头调 interaction」说的正是这一点：**发提醒不依赖 interaction 在线，看列表的装饰信息才依赖**。

**代价**：列表读时多一次（含经验时两次）对 interaction 的内部调用；interaction 挂了列表缺上下文。

### 2.4 在站信号：用户这会儿在不在

**是什么**：「离站时才发邮件」（PAP-146）要知道用户在提醒生成后的 10 分钟里有没有在站上。在站信号就是一条「这个用户最后一次出现在什么时候」的记录。

**业界**：Discourse 在服务端记 `last_seen_at`，用户每次请求顺手更新（限频）；Mastodon 看有没有推送长连接。

**我们**（决策 H）：两种信号都打到通知服务上：门户每 60 秒拉一次未读数（本 Issue 记），推送连接在线时每 25 秒心跳记一次（PAP-145）。存进表 `kernel.presence`（每人一行，最后出现时刻）。

为什么不放内存：每次合并后端 PR，CD 都会重部署通知服务，内存清零。若重启发生在某条提醒的 10 分钟窗口里，用户明明在站上，到点检查时却查不到记录，就会多发一封邮件。表在重启后还在。

**代价**：每次拉未读数多一次单行 upsert（主键更新，亚毫秒级）。

## 3. 目标、非目标、反目标

**目标**

1. AC：回复、评论经验、@ 三类提醒按规则生成；同一条里既回复又 @ 同一人只生成一条、不提醒作者本人。
2. AC：同一事件投递两次只生成一条；删除先于发表到达时不生成；晚到的旧编辑不覆盖新的。
3. AC：未读数接口的 304 行为与现在一致。
4. AC：interaction 已没有 `notification` 子域，`InteractionArchitectureTest` 通过。
5. D2：Testcontainers 起真 RabbitMQ，发到 `interaction.events` 的事件被通知服务收到并生成提醒。
6. 契约先行：门户（PAP-147）、有帮助（PAP-144）、推送（PAP-145）、邮件（PAP-146）照第 8、10 节先写代码。

**非目标**

- 有帮助提醒（PAP-144）：本 Issue 只把信箱的形状留好（第 10.1 节），不写合并逻辑。
- 推送（PAP-145）、提醒邮件（PAP-146）：本 Issue 只提供它们要的端口与事件。
- 迁移旧提醒（第 3 节 L）。
- `processed_event` 的定期清理（第 15 节遗留）。

**反目标**（技术上能跑，但算失败）

1. 提醒数据或展示信息里出现用户名列（R6；用户名只存 identity）。
2. 列表在 interaction 挂掉时整页报错（对象信息是装饰，降级就行）。
3. 处理器在事务外调 `markProcessed`，或业务失败了去重记录却提交了（重试被当成重复，提醒永远丢了）。
4. 收件人规则的结果取决于事件到达顺序（第 7.3 节；只有一种父评论被删的边缘情况在那里说明并接受）。

## 4. 已定的前提

| 来源 | 内容 |
|---|---|
| 决策 A | 事实型事件；收件人规则搬进通知服务；不迁移旧数据 |
| 决策 D | 按事件 ID 去重；墓碑；版本号 |
| 决策 H | 推送在线或拉过未读数即在站，通知服务自己判断 |
| 决策 J | 正文由 interaction 转成纯文本，通知服务按渠道截断；`<@ID>` 在显示时按 ID 现查名字 |
| PAP-141 | 交换机 `interaction.events`；路由键 `comment.published` / `comment.edited` / `comment.deleted` / `post.deleted` / `helpful.marked`；记录在 `patra-interaction-api`；版本发表 = 1、编辑 +1；删除事件都带 `subjectId` |
| PAP-137 | `ProcessedEventPort` 必须在业务事务里调；`EventQueue` 声明队列与 `.dlx` / `.dlq`；AUTO 确认、重试 3 次（1/2/4 秒）后进死信、prefetch 10；一个队列收多种事件时配 `trusted-packages` |
| PAP-142 | kernel 已建 `NotificationCategory { REPLY, MENTION, HELPFUL }`、`RecipientPort`、`AccountStatePort`、`IdentityUnavailableException`、`UserDataCleanupGateway` 与每天 06:30 的注销清理；boot 已接安全 starter 与 identity 客户端 |
| PAP-140 | 三个未读数，铃铛 = 回复 + @；「全部已读」覆盖三个标签 |
| PAP-128 | 纯文本里每个 `<@ID>` 都是真提及；站内摘录 120 字素、压成一行、不出半个名字、截了由门户加省略号 |
| 骨架 spec 5.2、5.3 | 在站信号、注销清理扩展点、提醒类别进底座；`inbox` 不依赖 `preference` |

## 5. 子域与分包

全部代码落在 notification 的 `inbox` 子域与底座 `kernel`；interaction 侧在 `subject` 底座与 `post` 子域（第 11 节）。**不新增任何跨子域依赖边**：inbox 只用底座。

放进底座的（被两个以上子域用，按骨架 spec 5.2 的判断）：

| 东西 | 谁用 |
|---|---|
| 在站信号 `PresenceGateway` | inbox 写（拉未读数）、realtime 写（心跳）、mail 读（离站判断） |
| 对象视图端口 `SubjectViewPort`（调 interaction） | inbox 列表；mail 邮件里的「在哪」行与「查看并回复」链接 |
| 名片端口 `UserCardPort`（调 identity `/summaries`，带访客） | inbox 列表的触发人与 @ 名字；mail 邮件里 @ 的名字（同一条「主页可见用用户名、否则昵称」规则） |
| 摘录渲染 `ExcerptRenderer` 与提及名字规则 `MentionNames` | inbox 120 字素单行；mail 300 字素多行、140 字素单行（PAP-128 交接表） |
| `SubjectType`（`PUBLICATION` / `POST`） | 对象视图与收件人规则 |

`UserCardPort` 和 PAP-142 的 `RecipientPort` 都调 identity，但用途不同：名片要替访客算「主页是否可见」与头像，收件信息要邮箱与验证状态，两个接口两个端口。

## 6. 存储

### 6.1 Flyway 与 schema

- inbox：`db/migration/inbox/` 下建 schema `inbox` 与三张表（6.2）。
- kernel：`db/migration/kernel/` 下建 schema `kernel` 与 `kernel.presence`（6.3）。集成分支已把 `processed_event`、`event_publication` 的脚本放在这个目录，但表在 `public`，不受影响。
- 版本号取写脚本时的时间戳。只存用户 ID（R6）。`NotificationMigrationIT` 的白名单本就允许五个子域名，不用改。

### 6.2 inbox 的三张表

**`inbox.notification`：提醒，一行一个收件人一条提醒**。按 `recipient_id` 哈希分 16 区（沿用 interaction 的做法）：列表、未读数、已读都带收件人，只扫一个分区。

| 列 | 类型 | 说明 |
|---|---|---|
| `recipient_id` | BIGINT NOT NULL | 收件人；分区键 |
| `notification_id` | BIGINT NOT NULL | 雪花 ID；主键 `(recipient_id, notification_id)` |
| `category` | VARCHAR(16) NOT NULL | 标签：`REPLY` / `MENTION` / `HELPFUL`（CHECK） |
| `type` | VARCHAR(32) NOT NULL | 动作：`COMMENT_REPLY` / `POST_COMMENT` / `MENTION`（CHECK；PAP-144 加自己的） |
| `actor_id` | BIGINT NOT NULL | 触发人（有帮助是最近一位点的人） |
| `actor_anonymous` | BOOLEAN NOT NULL | 触发人是对象的匿名所有者：列表不给名片 |
| `subject_id` | BIGINT NOT NULL | 所在对象（interaction 登记表 ID）；经验删除按它删 |
| `comment_id` | BIGINT NULL | 回复与 @ 必填（CHECK）；有帮助点评论时是被点的评论，点经验时为空 |
| `occurred_at` | timestamptz(6) NOT NULL | **排序时间**：回复与 @ 是评论发表或编辑的时刻；有帮助是最近一次被点的时刻，会变 |
| `created_at` | timestamptz(6) NOT NULL | **写入信箱的时刻**（处理时的 `Clock`），不变。和 `occurred_at` 的区别：broker 停了一小时再补投时，事件时刻是一小时前，写入时刻是现在；提醒邮件的「离站 10 分钟」「过去 24 小时」从用户第一次能看到它的时刻算，用这一列 |
| `read_at` | timestamptz(6) NULL | 已读时刻；空 = 未读 |

索引：

- `idx_notification_list (recipient_id, category, occurred_at DESC, notification_id DESC)`：列表与游标。
- `idx_notification_unread (recipient_id, category) WHERE read_at IS NULL`：未读数、全部已读。
- `uk_notification_comment (recipient_id, comment_id) WHERE category IN ('REPLY', 'MENTION')`：一个人对一条评论最多一条回复或 @ 提醒（「既回复又 @ 只生成一条」的存储兜底，也是重复投递的第二层防线）。有帮助的行不在这条约束里。
- `idx_notification_comment (comment_id) WHERE comment_id IS NOT NULL`：评论删除时按评论删全部收件人的提醒。
- `idx_notification_subject (subject_id)`：经验删除。
- `idx_notification_occurred (occurred_at)`：保留期清理。

**为什么排序用可变的 `occurred_at` 而不是 ID**：有帮助条目当天再被点要挪到最上面（release spec 2.1），雪花 ID 不会变。游标因此是 `(occurred_at, notification_id)` 两段，对门户不透明（8.3）。

**为什么不存摘录**：同一条评论的多个收件人共用一份正文，存在 `inbox.comment` 里，列表按 `comment_id` 关联取；编辑只改一行，而不是改所有收件人的行。

**`inbox.comment`：评论快照与墓碑，一行一条评论**

| 列 | 类型 | 说明 |
|---|---|---|
| `comment_id` | BIGINT PK | 评论 |
| `subject_id` | BIGINT NOT NULL | 所在对象；经验删除时按它删快照 |
| `version` | INTEGER NOT NULL | 已存正文的版本；0 = 版本未知（留给 PAP-144 从有帮助事件补正文） |
| `plain_text` | TEXT NULL | 最新版本的纯文本全文（PAP-128，含 `<@ID>`）；墓碑为空 |
| `deleted_at` | timestamptz(6) NULL | 非空即墓碑：评论已删除，之后的事件全部丢弃 |
| `updated_at` | timestamptz(6) NOT NULL | 最后一次写入的处理时刻（保留期清理用） |

约束：`CHECK ((deleted_at IS NULL) = (plain_text IS NOT NULL))`。索引：`(subject_id)`、`(updated_at)`。

存全文而不是截好的摘录：邮件要 300 字素且保留换行，站内要 120 字素单行，截多长由读的一方按渠道决定（决策 J）。每条评论只存一份，评论正文本身有长度上限。

**`inbox.subject_tombstone`：投稿经验的墓碑**

| 列 | 类型 | 说明 |
|---|---|---|
| `subject_id` | BIGINT PK | 已删除的经验自己的登记对象 |
| `deleted_at` | timestamptz(6) NOT NULL | 删除时刻（事件里的） |

`updated_at` 一类的触发器不需要：三张表都由代码显式写时间。

### 6.3 `kernel.presence`：在站信号

| 列 | 类型 | 说明 |
|---|---|---|
| `user_id` | BIGINT PK | 用户 |
| `last_seen_at` | timestamptz(6) NOT NULL | 最后一次出现的时刻 |

写法：`INSERT … ON CONFLICT (user_id) DO UPDATE SET last_seen_at = GREATEST(presence.last_seen_at, EXCLUDED.last_seen_at)`，不限频：规模小，每次一个主键更新；精确的时刻对「10 分钟窗口」判断有用。

## 7. 消费事件

### 7.1 队列

- 一个队列 `notification.inbox`，绑定交换机 `interaction.events` 的四个路由键：`comment.published`、`comment.edited`、`comment.deleted`、`post.deleted`。死信 `notification.inbox.dlx` / `notification.inbox.dlq` 由 `EventQueue` 一并声明。
- **PAP-144 往同一个队列加 `helpful.marked`**（绑定与 `@RabbitHandler` 方法都加在本 Issue 的类上），不另开队列，理由见 7.5。
- 监听类 `InboxEventListener`（adapter `inbox.mq`）：类上 `@RabbitListener(queues = "notification.inbox", concurrency = "1")`，每种事件一个 `@RabbitHandler`，按消息头 `__TypeId__` 分发。`patra.events.consumer.trusted-packages` 写 `dev.patra.interaction.api.discussion.event`、`dev.patra.interaction.api.post.event`（PAP-144 加 `…reaction.event`）。
- 监听方法只做「集成事件 → 命令」的映射，交给命令总线；处理器在 app 层开事务。
- 依赖：adapter 加 `spring-boot-starter-amqp` 与 `patra-interaction-api`；boot 声明队列（`EventQueue`）。

### 7.2 每种事件怎么处理

四个处理器都在一个事务里按同样的开头走：`markProcessed("notification.inbox", eventId)` 返回 `false` 就结束（重复）。

**评论已发表**（`RecordCommentPublishedHandler`）：

1. 对象有墓碑 → 结束。
2. 写评论快照（upsert）：快照是墓碑 → 结束；已存版本低于事件版本 → 覆盖正文；否则保留已存的（更新的）正文。这一步对快照行加了行锁，和同一条评论的删除互斥。
3. 按收件人规则（7.3 `forPublished`）算收件人，写提醒（`occurred_at` = `publishedAt`），撞上 `uk_notification_comment` 的跳过。
4. 真写入了的收件人：发 `NotificationsCreatedEvent` 与 `UnreadCountsChangedEvent`（第 10.2 节）。

**评论已编辑**（`RecordCommentEditedHandler`）：同上，第 3 步换成 `forEdited`，类型都是 `MENTION`，`occurred_at` = `editedAt`。

**评论已删除**（`RecordCommentDeletedHandler`）：快照标成墓碑（没有就新建一行墓碑，正文置空）；删掉 `comment_id` 等于它的全部提醒（所有收件人、所有类别，PAP-144 的有帮助条目一起删）；被删了提醒的收件人发 `UnreadCountsChangedEvent`。

**投稿经验已删除**（`RecordPostDeletedHandler`）：记对象墓碑；删掉 `subject_id` 等于它的全部提醒（经验下评论的回复与 @、PAP-144 的有帮助条目）与评论快照；发 `UnreadCountsChangedEvent`。

**删除写墓碑与写提醒的互斥**：同一条评论的发表与删除都先写快照行（upsert 拿行锁），所以不会出现「删除先提交、发表后提交又把提醒写进去」。对象墓碑没有行可锁，靠监听容器 `concurrency = "1"` 顺序处理保证（信箱队列只有一个消费线程）；这一条写在监听类的注释里，改并发前要先补锁。

### 7.3 收件人规则（`RecipientRules`，搬自 `NotificationRules`）

**发表**：按顺序列候选人，同一个人只取第一次出现时的类型，最后去掉作者本人。

1. 被回复评论的作者 `parentAuthorId`（`COMMENT_REPLY`，标签「回复我的」）
2. 经验作者 `subject.ownerId`：只在根评论（`parentCommentId` 为空）且对象是 `POST` 时（`POST_COMMENT`，标签「回复我的」）
3. @ 到的人 `mentionedUserIds`（`MENTION`，标签「@ 我的」）

**编辑**：只给 `addedMentionedUserIds`（这条评论**第一次** @ 到的人）发 `MENTION`，去掉作者本人，**另外去掉被回复评论的作者，以及根评论在经验下时的经验作者**。

后一条是本 Issue 新加的，目的是让结果与到达顺序无关：

- 按顺序处理时：发表先给被回复的人写了一条回复提醒，编辑再 @ 他时撞上唯一索引，被跳过，他手里是「回复我的」。
- 编辑先到时，如果不去掉他：编辑先给他写一条 @ 提醒，发表后到时回复提醒撞上唯一索引被跳过，他手里变成「@ 我的」。

两种顺序结果不同。去掉之后，编辑永远不碰这两类人，结果由发表决定，和顺序无关。按顺序处理时行为与现在完全一样（现在这些人也是被唯一索引挡掉的）。

**剩下一种边缘情况，接受**：编辑事件里的 `parentAuthorId` 是编辑时的父作者，父评论已删成占位时为空（PAP-141 契约 5.4）。于是「A 回复 B → B 的评论被删成占位 → A 编辑回复新 @B」且编辑先于发表被处理时，编辑排除不了 B，B 拿到「@ 我的」，后到的发表被唯一索引挡掉；按顺序处理时 B 拿的是「回复我的」。B 都只收到一条提醒，只是标签不同。要消除它得让编辑事件改带发表时的父作者，为这种「父评论被删、又编辑加 @、还恰好乱序」的情况改契约不值得。

不检查账号状态（同现在）：封禁、注销中的人照样收到，注销清理会删。

### 7.4 乱序与重复：逐条判断（每行一个测试）

| 情形 | 结果 |
|---|---|
| 同一事件到两次 | 第二次 `markProcessed` 为 `false`，什么都不做 |
| 两个不同事件都要给同一个人、就同一条评论写提醒 | 唯一索引挡住第二条（7.3 的规则已让这种情况不出现，索引兜底） |
| 编辑晚到（已存版本 ≥ 事件版本） | 正文不覆盖；`addedMentionedUserIds` 的 @ 提醒照常补 |
| 编辑先于发表 | 编辑写下 v2 正文与新增 @ 的提醒；发表到时版本 1 比已存低，正文不动，照常给被回复的人、经验作者、发表时 @ 的人写提醒 |
| 删除先于发表 / 编辑 | 删除留下墓碑；之后的发表、编辑在第 2 步结束 |
| 删除后又来删除（重投） | 去重挡住；即使事件 ID 不同，墓碑与删除都是幂等的 |
| 经验删除先于它下面评论的发表 / 编辑 | 第 1 步看到对象墓碑就结束（同一经验下评论的删除照常留一行评论墓碑，无害，5 个月后随保留期清掉） |
| 处理中途失败（库抖动） | 事务回滚，`processed_event` 也回滚；按 1、2、4 秒重试，三次后进 `notification.inbox.dlq` |

### 7.5 为什么只用一个队列

- 删除联动对两种条目都成立：有帮助条目也在 `inbox.notification` 里、带 `comment_id` / `subject_id`，本 Issue 的删除处理器一并删掉，PAP-144 不用再订阅删除事件。
- 一个队列、一个消费线程：信箱的所有写入顺序发生，「有帮助」与「删除」之间不会并发，不用为对象墓碑加锁。
- 代价：PAP-144 要改本 Issue 的监听类与队列声明（加一个方法、一个路由键），同一个 PR 里先后提交，冲突很小。

## 8. 接口（门户，经网关 `/patra-notification` 前缀）

全部要登录（未登录 401 `NOTIFICATION-0401`，由安全 starter 判定），只读写自己的。路径与现在 interaction 的一致，只换了前缀。

### 8.1 未读数：`GET /me/notifications/unread-count`

```json
{"total": 3, "replies": 2, "mentions": 1, "helpful": 0}
```

- `total` = `replies` + `mentions`（铃铛），**不含** `helpful`；`helpful` 在 PAP-144 合入前恒为 0。
- `ETag: "{replies}-{mentions}-{helpful}"`；请求的 `If-None-Match` 命中（逗号分隔、忽略 `W/`、`*` 也算）时 304、无响应体。
- 200 与 304 都显式带 `ETag` 与 `Cache-Control: no-cache, private`（304 上不能被安全过滤器补成 `no-store`，否则浏览器丢掉缓存条目）。**与现在一致**。
- **记在站**：200 与 304 都记（`PresenceGateway.markSeen`）。实现成一个命令 `PollUnreadCountsCommand`：记在站、再读未读数，命名上写明它有写入。

### 8.2 列表：`GET /me/notifications?tab=&cursor=`

- `tab`：`replies`（默认）/ `mentions` / `helpful`；其他值 422 `NOTIFICATION-0422`。
- `cursor`：上一页的 `nextCursor`，不合法 422。每页 20 条，按 `occurredAt` 倒序（同一时刻按 ID 倒序）。

```json
{
  "items": [
    {
      "notificationId": "1849…",
      "type": "COMMENT_REPLY",
      "read": false,
      "occurredAt": "2026-10-31T02:15:30.123456Z",
      "actor": {"userId": "18…", "username": "zhou", "displayName": "周敏", "avatarUrl": "/users/18…/avatar?v=…",
                "avatarPalette": 3, "state": "ACTIVE", "profileVisible": true},
      "actorAnonymous": false,
      "subject": {"type": "post", "key": "1832…", "available": true, "title": null,
                  "journal": "The Lancet", "abbreviation": "Lancet", "submittedMonth": "2026-08", "mine": true,
                  "experience": {"firstDecision": "MAJOR_REVISION", "finalOutcome": "ACCEPTED",
                                 "firstRoundDays": 41, "totalDays": 96}},
      "commentId": "1848…",
      "excerpt": "谢谢分享，@周敏 你们审了多久？",
      "excerptTruncated": false
    }
  ],
  "nextCursor": "MTc2…"
}
```

| 字段 | 规则 |
|---|---|
| `type` | `COMMENT_REPLY`（回复了你的评论）/ `POST_COMMENT`（评论了你的投稿经验）/ `MENTION`（@ 了你）；PAP-144 加有帮助的类型 |
| `occurredAt` | 排序时间（6.2）。**比现在的 `createdAt` 改了名**：有帮助条目会变，叫「创建时间」不对 |
| `actor` | 与讨论、投稿经验的作者对象同形；`profileVisible` 为假时 `username`、`avatarUrl` 为空；`actorAnonymous` 为真时恒为空；identity 不认识时为空。**已注销的人 identity 给的是占位**：`actor` 不为空，`state` 为 `DELETED`、用户名与昵称为空，门户按 `state` 显示「已注销用户」（同 v0.15） |
| `subject` | 读时现查（第 9 节）。查不到或 interaction 挂了为空；`type` 用门户的写法 `publication` / `post`；`title` 只有文献有；`journal`、`abbreviation`、`submittedMonth`、`experience` 只有经验有；`mine` = 经验作者是收件人 |
| `commentId` | 回复与 @ 必有；有帮助点经验时为空（**现在恒非空，PAP-147 注意**） |
| `excerpt` | 评论纯文本按 PAP-128 渲染：`<@ID>` 换成名字（主页对收件人可见用用户名，否则昵称，不认识写「已注销用户」），压成一行，截到 120 字素，不出半个名字；`subject` 为空或 `available` 为假（文献下线）时为空 |
| `excerptTruncated` | 截掉了东西，门户在末尾加 `…` |

ID 一律是字符串（`patra-tool-web` 的 Long → String），时间是 UTC ISO 8601。

### 8.3 游标

`nextCursor` 是不透明字符串（`occurredAt` 微秒与 ID 两段的 Base64URL），**门户原样回传，不要解析**；没有下一页为 `null`。

### 8.4 已读

- `PUT /me/notifications/{notificationId}/read`：单条已读，已读的再标不报错；返回 8.1 同形的未读数（不带 ETag）。不存在或不是自己的 404 `NOTIFICATION-0404`，`traits` 含 `NOTIFICATION_NOT_FOUND`。
- `PUT /me/notifications/read`：三个标签全部已读，返回未读数。

### 8.5 错误码

| 情形 | 状态 | 码 |
|---|---|---|
| 未登录 | 401 | `NOTIFICATION-0401` |
| `tab`、`cursor` 不合法 | 422 | `NOTIFICATION-0422` |
| 提醒不存在或不是自己的 | 404 | `NOTIFICATION-0404` |
| identity 不可用（取名片） | 503 | `NOTIFICATION-0503` |

## 9. 读时拼装

`InboxQueryService.page(tab, cursor)`：

1. 读一页（多取一条判断有没有下一页），每行带关联来的评论正文（墓碑或没有快照为空）。
2. 对象：这一页的 `subjectId` 去重后调 `SubjectViewPort.findViews`（底座，≤ 100 个）。抛 `InteractionUnavailableException` 时打 WARN、按「全部查不到」降级。
3. 名片：非匿名触发人 + 正文里 `<@ID>` 的人，去重后按 100 个一批调 `UserCardPort.findCards(ids, 收件人)`。失败直接 503。
4. 摘录：`ExcerptRenderer.render(plainText, 名字表, 120, 单行)`；名字按 `MentionNames.of(card)`。
5. 拼视图。

**底座端口 `SubjectViewPort`** 的实现 `SubjectViewAdapter`（infra `kernel.interaction`）调 interaction 的两个内部接口（第 11.1 节）：先取对象视图，其中是 `POST` 的再按帖子 ID 取经验结果，合进同一个 `SubjectView`。三种失败（错误响应、连不上 / 超时、注册中心没有实例）都翻成 `InteractionUnavailableException`（503 语义；调用方自己决定降级还是传播）。HTTP 分组 `interaction`：连接 1 秒、读 2 秒（同 interaction 调 identity）。

**底座端口 `UserCardPort`** 的实现 `UserCardAdapter`（infra `kernel.identity`）调 identity 的 `UserEndpoint.summaries`，失败翻成 PAP-142 已有的 `IdentityUnavailableException`。

## 10. 给下游的端口与事件

### 10.1 PAP-144（有帮助）怎么接进信箱

| 环节 | 做法 |
|---|---|
| 存哪 | 同一张 `inbox.notification`：`category = HELPFUL`，`type` 加自己的值（改 CHECK），`comment_id` 是被点的评论（点经验时为空），`subject_id` 是所在对象（点经验时是经验自己），`actor_id` 是最近一位点的人，`occurred_at` 是最近一次被点。合并要的列（人数、北京时间的那一天等）与「点过的人」由 PAP-144 用自己的迁移加；合并用的唯一约束也由它加（必须包含 `recipient_id`） |
| 排序与游标 | 当天再被点：`UPDATE` 改 `actor_id`、`occurred_at`、人数，`read_at` 置空，行自然挪到最上面 |
| 列表 | `tab=helpful` 已可用；读适配器按 `category` 取行。点评论时正文走 `inbox.comment` 关联（PAP-144 可在快照不存在时用事件里的 `targetPlainText` 补一行 `version = 0`，之后的编辑会覆盖它）；点经验时的摘录由 PAP-144 加列并在读适配器里补上 |
| 未读数 | 已按 `category` 分三个数，自动包含 |
| 已读 / 全部已读 | 按行，自动包含 |
| 删除联动 | 评论删除按 `comment_id` 删、经验删除按 `subject_id` 删，**自动包含有帮助条目**；PAP-144 自己的附表要在本 Issue 的 `deleteByComment` / `deleteBySubject` 里一并删 |
| 乱序 | 写之前查评论快照是否是墓碑、对象是否有墓碑（`CommentContentRepository.isDeleted`、`SubjectTombstoneRepository.exists`） |
| 保留期 | 按 `occurred_at` 删 5 个月前的，自动包含（附表一并删） |
| 注销清理 | 按收件人删，自动包含 |
| 队列 | 加到 `notification.inbox`（7.5） |

### 10.2 领域事件（`domain.inbox.event`）

都在信箱写入的事务里经 `ApplicationEventPublisher` 发布。它们不进 Modulith 登记表（只有 `@ApplicationModuleListener` 进）。订阅方式按订阅方要不要写库分两种（分包规范「跨子域写：对方用同步 `@EventListener` 在同一事务里处理」）：

| 事件 | 何时发 | 字段 | 谁用、怎么订阅 |
|---|---|---|---|
| `UnreadCountsChangedEvent` | 写入了提醒、单条已读（真的从未读变已读）、全部已读（真的标了）、删除联动删掉了提醒 | `Set<Long> recipientIds` | PAP-145：只推送、不写库，用 `@TransactionalEventListener(AFTER_COMMIT)`，提交后按 ID 经 `InboxReadPort.unreadCounts` 取数；回滚就不推 |
| `NotificationsCreatedEvent` | 写入了提醒 | `List<CreatedNotification>`：`notificationId`、`recipientId`、`category`、`occurredAt`、`createdAt` | PAP-146：要记「10 分钟后检查」的待办，是写库的订阅方，**用同步 `@EventListener` 在同一事务里写**。提交后再写的话，提交与写入之间进程一挂，这封邮件就永远丢了；同事务写，失败时整条消息回滚重试。窗口从 `createdAt` 算 |

保留期清理与注销清理不发（前者删的是 5 个月前的，后者是已注销的人）。

### 10.3 读端口 `InboxReadPort`（`domain.inbox.port.read`）

```java
List<InboxEntry> page(long recipientId, NotificationCategory category, InboxCursor after, int limit);
UnreadCounts unreadCounts(long recipientId);   // replies、mentions、helpful；total() = replies + mentions
```

`InboxEntry`（`domain.inbox.model.read`）：`notificationId`、`type`、`actorId`、`actorAnonymous`、`subjectId`、`commentId`、`plainText`（全文，可空）、`occurredAt`、`readAt`。PAP-146 要的「按 ID 复查已读」「过去 24 小时未读」由它往这个端口加方法。

### 10.4 底座（`dev.patra.notification.domain.kernel`）

| 端口 / 规则 | 签名 | 说明 |
|---|---|---|
| `port.gateway.PresenceGateway` | `void markSeen(long userId)`；`Map<Long, Instant> lastSeen(Collection<Long> userIds)` | 实现 `app.kernel.service.PresenceGatewayImpl`，时刻取注入的 `Clock`。PAP-145 连接建立与每次心跳调 `markSeen`；PAP-146 读 `lastSeen` 判断「10 分钟里在站上」（没有记录的不在结果里） |
| `port.interaction.SubjectViewPort` | `Map<Long, SubjectView> findViews(Collection<Long> subjectIds)`，≤ 100 | 查不到的不在结果里；失败抛 `InteractionUnavailableException` |
| `port.identity.UserCardPort` | `Map<Long, UserCard> findCards(Collection<Long> userIds, Long viewerId)`，≤ 100 | 失败抛 `IdentityUnavailableException` |
| `policy.ExcerptRenderer` | `RenderedExcerpt render(String plainText, Map<Long, String> names, int maxGraphemes, boolean singleLine)` | PAP-128 截断规则；缺名字的按「已注销用户」 |
| `policy.MentionNames` | `String of(UserCard card)` | 可见用用户名，否则昵称，`null` 或已注销写「已注销用户」 |

`SubjectView`：`subjectId`、`type`（`SubjectType`）、`key`（可空）、`available`、`title`、`journal`、`abbreviation`、`publishedDate`（经验是投稿月份 `yyyy-MM`）、`ownerId`（可空）、`ownerAnonymous`、`experience`（`ExperienceOutline`：`firstDecision`、`finalOutcome`、`firstRoundDays`、`totalDays`，都按 interaction 给的原样字符串与数字，只有经验有）。

## 11. interaction 侧

### 11.1 两个内部接口（`/_internal/**`，网关拒绝，服务间直连）

对象视图归底座 `subject`，经验结果归 `post`：各自公开自己的事实，由通知服务的适配器组合。**这与编排会话说的「一个接口带经验结果」不同**：底座不能依赖 `post`，把结果塞进底座的对象视图就得在底座里复制一份经验结果的模型。另一条路是把组合接口放进 `post` 子域（`post` 可以用底座的 `SubjectReadPort`），能少一次调用，但「所有对象的视图」由投稿经验子域对外提供，归属说不通。拆成两个接口，各子域只管自己的；代价是一页里有经验时通知服务多调一次（≤ 20 个 ID，毫秒级）。通知服务那边仍是一个端口 `SubjectViewPort`，下游不受影响。

**`POST /_internal/subjects/views`**（`patra-interaction-api` 的 `dev.patra.interaction.api.subject.endpoint.SubjectEndpoint`）

- 请求 `SubjectViewsReq { List<Long> subjectIds }`：0 ~ 100 个，重复的只算一次；缺列表、超出、含 `null` 422（空列表回空，同 identity 的 `/summaries`）。
- 响应 `SubjectViewsResp { List<SubjectViewResp> subjects }`，按请求顺序，查不到的省略：

| 字段 | 说明 |
|---|---|
| `subjectId` | 登记表 ID |
| `type` | `PUBLICATION` / `JOURNAL` / `POST` |
| `key` | 门户链接里的 ID；来源里整行消失时为空 |
| `available` | 状态是 `ACTIVE` |
| `title`、`journal`、`publishedDate`、`abbreviation` | 展示快照，含义见 `SubjectSnapshot`（经验的 `publishedDate` 是投稿月份） |
| `ownerId`、`ownerAnonymous` | 本地对象（经验）的所有者；外部对象为空 / `false` |

- 实现：`adapter.subject.internal.SubjectEndpointImpl` → `app.subject.usecase.query.SubjectViewQueryService` → 现有的 `SubjectReadPort.findByIds`。

**`POST /_internal/posts/outlines`**（`dev.patra.interaction.api.post.endpoint.PostEndpoint`）

- 请求 `ExperienceOutlinesReq { List<Long> postIds }`：0 ~ 100 个，规则同上。
- 响应 `ExperienceOutlinesResp { List<ExperienceOutlineResp> outlines }`，每项 `postId`、`firstDecision`、`finalOutcome`、`firstRoundDays`、`totalDays`（可空）；已删除的也返回（提醒在删除事件到达前可能还在）；查不到的省略。
- 实现：`adapter.post.internal.PostEndpointImpl` → `app.post.usecase.query.ExperienceOutlineQueryService` → `PostReadPort` 新增批量方法 `Map<PostId, ExperienceOutline> findOutlines(Collection<PostId>)`，复用 PAP-141 的 `ExperienceOutline`（在 PAP-141 合入后的基线上写）。
- 经验的帖子 ID 就是它的对象视图里的 `key`。

安全：内部接口不经网关，服务内不要求登录（同 identity 的 `/_internal/users/**`）。

### 11.2 删除 `notification` 子域

- 删四层的 `notification` 包（领域、应用、基础设施、适配器）与它们的测试；`boot/src/integrationTest/…/notification/` 下的流程测试；`ExperienceFlowIT` 里两条提醒相关的用例；`NotificationDeletedCleanupMigrationIT`。
- `InteractionArchitectureTest`：子域清单去掉 `notification`，两条 `allowingSubscription("notification", …)` 删掉。
- 领域事件与各层注释里「订阅方是提醒子域」的说法改成「外送后由通知服务生成」。`CommentPublishedEvent` / `CommentEditedEvent` 里原先只为旧监听器留的 `body` 字段**保留**：PAP-141 的测试设施（`DiscussionEventRecorder` 的失败标记、几个处理器测试的断言）在用它，删掉要改 PAP-141 的一批文件，收益只是少一个字段。
- README 第 4.4 节、子域表与分包清单；infra `build.gradle.kts` 头注释；`HelpfulWriteFailedException` 的 Javadoc。
- **Flyway 不删旧脚本**：mini 的 `flyway_schema_history` 已有那三个版本，脚本删了校验会失败、服务起不来（remove-ingest 时 registry 踩过）。新增一条前向迁移 `db/migration/notification/V20261031…__drop_notification_schema.sql`：`DROP SCHEMA notification CASCADE`，脚本头注释说明「子域已搬到 patra-notification，保留旧脚本只为迁移历史」。`InteractionMigrationIT` 的 schema 白名单去掉 `notification`。
- XXL：mini 上 interaction 执行器的 `notificationRetentionJob` 要删（runbook）。

## 12. 保留期与注销清理

- **保留期**：XXL 任务 `inboxRetentionJob`（执行器 `patra-notification`，每天 06:45，超时 300 秒，不重试）。截止时刻 = 北京时间减 5 个自然月（同现在）。删 `occurred_at` 早于截止的提醒；删 `updated_at` 早于截止、且没有提醒引用的评论快照（墓碑也在其中）；删 `deleted_at` 早于截止的对象墓碑。5 个月后还没送到的事件只可能来自死信重放，届时按新事件处理，可以接受。
- **注销清理**：inbox 实现 PAP-142 的扩展点 `InboxUserDataCleanupGatewayImpl`（`subdomain()` = `inbox`）：枚举有提醒的收件人、删他作为收件人的全部提醒。他触发的提醒留在别人那里，名片查不到时显示为已注销。底座自己的在站记录由 `KernelUserDataCleanupGatewayImpl`（`subdomain()` = `kernel`）清。

## 13. 测试

| AC / 约束 | 测试 | 层 |
|---|---|---|
| 收件人规则（顺序、去重、作者本人、经验作者只对根评论、编辑去掉已覆盖的人） | `RecipientRulesTest` | domain 单元 |
| 摘录渲染（换名、单行、120 字素、不出半个名字、截了去尾空白） | `ExcerptRendererTest`、`MentionNamesTest` | domain 单元 |
| 游标编码与解析、非法游标 | `InboxCursorTest` | domain 单元 |
| 保留期截止 | `InboxRetentionTest` | domain 单元 |
| 去重、墓碑、版本、编辑先到、对象墓碑、处理失败回滚进死信（7.4 每行） | `Record*HandlerTest`（mock 端口） + `InboxEventFlowIT`（真 PG 与 RabbitMQ） | app 单元 + boot 集成 |
| 写入语句：唯一索引、upsert 版本比较、按评论 / 对象删除、游标分页、未读数 | `NotificationRepositoryAdapterIT`、`CommentContentRepositoryAdapterIT`、`InboxReadAdapterIT`、`PresenceRepositoryAdapterIT` | infra 集成 |
| 304：ETag、`If-None-Match`、两种响应的头、记在站 | `MyNotificationControllerTest`（WebMvc 切片） + `InboxRestFlowIT` | adapter + boot |
| 列表拼装：降级、匿名、可见性、对象不可用不给摘录 | `InboxQueryServiceTest` | app 单元 |
| D2：真 RabbitMQ 收到 `interaction.events` 的事件生成提醒；同一消息投两次只一条 | `InboxEventFlowIT`（PG + RabbitMQ 容器，向交换机发 `patra-interaction-api` 记录的 JSON） | boot 集成 |
| 对象视图端口：两个接口的组合、三种失败 | `SubjectViewAdapterTest`、`UserCardAdapterTest` | infra 单元 |
| 注销清理与保留期任务 | `InboxUserDataCleanupGatewayImplTest`、`PurgeExpiredInboxHandlerTest`、`DeletedUserCleanupFlowIT` 加一例 | app + boot |
| interaction 内部接口 | `SubjectViewQueryServiceTest`、`ExperienceOutlineQueryServiceTest`、`SubjectEndpointImplIT`、`PostEndpointImplIT` | interaction |
| AC4：interaction 没有 `notification` 子域 | `InteractionArchitectureTest`、`InteractionMigrationIT` | interaction |

## 14. 上线步骤（追加到 `docs/patra/runbooks/v0.16-notification-go-live-runbook.md`）

1. 部署顺序：CD 按 `services.json` 先部署 interaction、再部署 notification。中间这段时间 interaction 发出的事件没有队列可投，被 broker 退回、留在 interaction 的 `event_publication`；notification 起来时声明 `notification.inbox` 与绑定，下一分钟的补投任务（PAP-141 登记的 `eventPublicationResubmitJob`）把它们送到。不丢，只是晚一两分钟（PAP-141 spec 第 7 节写「通知服务先部署」是理想顺序，CD 的顺序反过来也安全）。部署后在 RabbitMQ 管理界面核对 `notification.inbox` 绑定了五个路由键：本 Issue 的四个与 PAP-144 的 `helpful.marked`。后端整个版本一个 PR 合入、一次部署，不存在只有本 Issue、没有 PAP-144 的线上状态；若 PAP-144 没赶上这个 PR，`helpful.marked` 会每分钟补投并被退回，须先补上绑定再上线。
2. 通知服务 Flyway：建 `inbox`、`kernel` 两个 schema 与四张表。
3. interaction Flyway：`DROP SCHEMA notification CASCADE`（旧提醒全部消失，第 3 节 L）。
4. xxl-job-admin：
   - notification 执行器登记 `inboxRetentionJob`：CRON `0 45 6 * * ?`，路由「第一个」，阻塞处理「丢弃后续调度」，超时 300 秒，重试 0 次；
   - **删除** interaction 执行器的 `notificationRetentionJob`（handler 已不存在，留着每天报错）。
5. 验证：用测试账号 B 回复 C 的评论，C 的铃铛在 60 秒内变化，列表里有这条、点开到那一楼；`notification.inbox.dlq` 为空；`patra_notification` 库 `SELECT count(*) FROM kernel.presence` 有 C。

## 15. 实施顺序与留下的事

**顺序**（按基线可用性，编排会话已知）：

1. 底座与信箱的领域、存储、应用层（只依赖 PAP-142）。
2. 门户接口与任务（依赖 PAP-142 的安全接线）。
3. 消费事件、interaction 两个内部接口与删除 `notification` 子域（依赖 PAP-141 合入后的基线，避免改同一批文件）。

**遗留（不在本 Issue 做）**：

- `processed_event` 没有清理，按事件数增长（信箱与 PAP-105 的邮件消费者共用）。清理属于事件模块的能力（`ProcessedEventPort` 加按时间删的方法 + 任务），建议在 `patra-tool-events` 里补。

## 16. 考虑过、没选的做法

| 做法 | 没选的原因 |
|---|---|
| 摘录存在每个收件人的提醒行里（同现在） | 编辑要改所有收件人的行，而且「编辑先到」时没有行可存版本；评论快照一行全解决 |
| 存截好的摘录 | 邮件要 300 字素保留换行、站内 120 字素单行，截多长是读的一方的事（决策 J） |
| 有帮助单独一张表、列表用 UNION | 未读数、已读、保留期、注销清理、删除联动都要写两遍；游标跨两张表更难 |
| 有帮助另开队列 | 有帮助与删除会并发，对象墓碑要加锁；删除联动要订阅两遍 |
| 在站信号放内存 | 每次部署清零，窗口内重启会多发邮件（2.4） |
| 在站信号写入限频 | 规模小，一次主键更新；限频会让「10 分钟窗口」的判断变粗 |
| 删掉 interaction 旧的 Flyway 脚本 | mini 上已执行，校验会失败 |
| interaction 一个内部接口带经验结果 | 底座不能依赖 `post`（11.1） |
| 编辑与发表撞车时按优先级「升级」类型（`ON CONFLICT DO UPDATE`） | 规则藏进 SQL；在规则里排除已覆盖的人更直白 |

## 17. 拍板记录

**编排会话代用户拍板（2026-10-31）**

1. 事件只带 `subjectId` / 类型，对象展示信息读时调 interaction 内部接口现查，内部接口由本 Issue 写；决策 A 的「不回头调 interaction」只针对计算收件人。
2. 评论版本：发表 = 1，每次编辑 +1（含 5 分钟内）。
3. 信箱要满足 PAP-144：可变的排序时间；三个未读数（铃铛 = 回复 + @）；目标可以是经验本身；经验删除时它下面评论的有帮助条目也删。

**本 spec 自己定的（理由见各节）**

1. 评论快照表存全文、版本与墓碑；提醒行不存摘录（6.2、16）。
2. 编辑的 @ 提醒去掉被回复的人与经验作者，使结果与顺序无关；父评论被删成占位的边缘情况接受（7.3）。
11. 提醒行加写入时刻 `created_at`，提醒邮件的时间窗口从它算；`NotificationsCreatedEvent` 由提醒邮件同步订阅、同事务写（10.2）。
3. 一个队列、单消费线程，PAP-144 加到同一队列与监听类（7.5）。
4. 在站信号存表、不限频（2.4、6.3）。
5. 对象视图与经验结果拆成 interaction 的两个内部接口，通知服务一个端口组合（11.1）。
6. 列表在 interaction 挂掉时降级、identity 挂掉时 503（2.3）。
7. 响应字段 `createdAt` 改名 `occurredAt`；`commentId` 可空；未读数加 `helpful`、`total` 不含它；游标不透明（8）。
8. 名片、对象视图、摘录渲染、在站信号进底座（5）。
9. Flyway 用前向迁移删 schema，旧脚本保留（11.2）。
10. `processed_event` 清理留给事件模块（15）。
