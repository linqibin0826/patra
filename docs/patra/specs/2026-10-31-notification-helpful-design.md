# 有帮助提醒工程设计（PAP-144）

> 状态：已实施，待验收（2026-10-31，提交在基线 `123205980` 之上，计划 `docs/patra/plans/2026-10-31-notification-helpful.md`）；实施中的偏离见第 15 节
> 日期：2026-10-31
> Issue：PAP-144（GitHub #331），Linear Project P-PAP-13 v0.16 Notification，Milestone BE Ready
> 依据：release spec `docs/patra/release-specs/v0.16-notification.md` 2.1（有帮助怎么合并、进不进红点、发不发邮件）、决策 A、D、第 5 节 D7；设计简报 `patra-portal/docs/design-briefs/v0.16/notification.md` §3.1～§3.3 与附录 A 第 6～12 条；
> PAP-141 事件契约（`docs/patra/specs/2026-10-31-interaction-integration-events-design.md` 第 5 节，`helpful.marked`）；PAP-143 信箱设计（`docs/patra/specs/2026-10-31-notification-inbox-design.md` 第 6、7、10.1 节）
> 编排会话代用户拍板的部分见第 14 节

## 1. 要解决的问题

v0.15 上线了「有帮助」：读者可以给评论和投稿经验点「有帮助」，但作者完全不知道自己被点了。v0.16 让作者在站内收到提醒，同时不能吵：

1. 同一条内容在同一天（北京时间）被几个人点，作者只看到**一条**：「D 等 2 人觉得你的投稿经验有帮助」，名字写最近一位点的人。
2. 当天又有人点：名字换成这个新的人、人数加一、变回未读、挪到最上面。第二天再有人点，另起一条。
3. 有人取消：已发的提醒不撤回，人数不减。
4. 「有帮助」是提醒页的第三个标签，有自己的未读数；**不计入铃铛红点、不发邮件**。
5. 被点的内容删除时，提醒跟着删。

本 Issue 只做通知服务这一头：消费 PAP-141 外送的 `helpful.marked`，在 PAP-143 建好的信箱里写合并条目，并让列表、未读数带上它。门户改接在 PAP-147。

## 2. 先讲清三个概念

### 2.1 提醒合并：把一串相似的提醒压成一条

**是什么**：同一件事反复发生时，不给每一次各发一条提醒，而是合成一条「X 等 N 人……」，后来的人更新这一条。英文里常叫 notification consolidation / aggregation。

**防什么**：刷屏。一条写得好的投稿经验一天被十个人点，作者要是收到十条提醒，真正需要回复的「回复我的」「@ 我的」就被淹没了；人数多的时候，提醒也就不再被当回事。

**业界怎么做**：

| 产品 | 怎么合 | 合并键 |
|---|---|---|
| B 站「收到的赞」 | 「XX 等 N 人赞了你的评论」 | 按被赞的内容（第三方描述，官方未公开） |
| Discourse | 「XX 和 N 人赞了你的帖子」；同一时间窗里的点赞合成一条 | 按点赞的人或按帖子，带时间窗（`consolidation_planner.rb`） |
| Facebook / Instagram | 「XX 和其他 N 人赞了你的照片」，新的人点了这条又亮起来 | 按被赞的内容 |
| GitHub | 按 issue / PR 线程合成一条，线程里有新动静就重新变未读 | 按线程 |

合并发生在什么时候也有两种：

- **写时合并**：新的一次到来时，找到要合进去的那一行原地更新。读的时候一行就是一条，分页、未读数都简单。
- **读时合并**：每一次都单独存一行，列表查询时再按合并键分组。存得简单，但分页（「第 2 页从哪组开始」）、未读数（一组算一个还是 N 个）都要按组算，查询复杂。

**我们**：按「被点的那条内容 × 北京时间一天」写时合并（release spec 2.1 已定），每个合并条目是信箱里的一行（PAP-143 已为此把排序改成可变的 `occurred_at`）。

**代价**：同一天里的变化只留最后的样子：作者看不到「是谁先点的」；要看完整名单得另做页面（本版不做）。

### 2.2 事件时间与处理时间：按哪个时刻切「一天」

**是什么**：一件事有两个时刻。**事件时间**是它真正发生的时刻（C 在 23:59 点了有帮助）；**处理时间**是消费方处理这条消息的时刻（通知服务在 00:01 才收到）。流处理里（Flink、Apache Beam）这是基本区分：按「一天」「一小时」这类窗口切分时，要说清按哪个时刻切。

**防什么**：把一次点击切到错误的一天。RabbitMQ 停机、interaction 的补投（每分钟一次）、消费失败后的重试（1、2、4 秒），都会让处理时间比事件时间晚。按处理时间切，23:59 的点击可能被算进第二天；同一天里先后点的两个人，因为其中一条晚到，可能被拆成两条提醒。

**业界**：流处理框架默认推荐按事件时间开窗，对晚到的数据另行处理（Beam 的 watermark 与 allowed lateness）；Discourse 的合并按 `created_at`（写入数据库的时刻），因为它的点赞与提醒在同一个进程里，两个时刻几乎相同。

**我们**：按事件时间。`helpful.marked` 带 `markedAt`（interaction 写明细的那一刻，补投不变，PAP-141 契约），通知服务用它换算成北京时间的日期作为合并键，也用它当条目的时间（列表里的「14:05」与排序）。晚到的点击照样合进它发生的那一天，哪怕那一行已经是「昨天」。

**和 AC「时钟可注入的测试」的关系**：Linear AC 写「跨午夜按北京时间切分（时钟可注入的测试）」。按事件时间切分以后，切分是 `markedAt` 的纯函数，不读「现在」；测试直接喂北京时间午夜前后的两个时刻（`15:59:59.999999Z` 与 `16:00:00Z`），比注入时钟更直接。如果按处理时间切，反而必须用时钟，而那正是第 2.2 节要避免的做法。处理器仍注入 `Clock`，只用来写处理时刻：信箱行的 `created_at`（PAP-143：写入信箱的时刻，邮件的时间窗口用它）与评论快照的 `updated_at`。

**代价**：作者今天可能看到一条日期是「昨天」、但刚变未读的条目（昨天 23:59 的点击今天才送到）。只在 RabbitMQ 或通知服务出故障时发生。

### 2.3 与顺序无关：记「每个人第一次点的时刻」，而不是数「点了几次」

**是什么**：消息是「至少一次、不保证顺序」送来的（决策 C、D）。合并的结果最好只取决于「哪些人在哪一刻点了」，和消息到达的先后、重复几次都无关。办法是每个合并条目记下**点过的人，以及每个人第一次点的时刻**：

- 人数 = 点过的人数；
- 同一个人的第二条消息（重复投递，或「点 → 取消 → 再点」）只会把他的时刻改成两者中较早的那个；
- 「最近一位」= 第一次点的时刻最晚的那个人（时刻相同取用户 ID 大的），条目的时间就是这个时刻。

产品上这等于「每个人只算他第一次点」：同一个人取消再点，不算「又有人点」。

**防什么**：

- 同一条消息投两次，人数多算一（Linear AC：同一事件投递两次不重复计人）。
- 同一个人反复点、取消，把作者的条目一遍遍刷成未读。
- 先点的人的消息晚到，把「最近一位」错写成他。
- 同一个人的两次点击（取消前后）和别人的点击乱序到达，最后写出的「最近一位」不一样。只记人、不记时刻做不到这一条：X 在 t1 点、取消、t3 再点，Y 在 t2 点，按 e1、Y、e3 的顺序处理会写成 Y，按 e3、Y、e1 的顺序处理会写成 X。

**业界**：分布式系统里，满足交换律、结合律、幂等的合并叫**半格合并**，CRDT（无冲突复制数据类型）就是用它保证「任意顺序、任意重复地合并，结果都一样」。最简单的是只增集合（G-Set）；「每个键取最小值」的映射也是一种。计数器（「人数 + 1」）不满足幂等，要靠外部去重兜住。

**我们**：两层都有：`ProcessedEventPort` 按事件 ID 去重（决策 D，防同一条消息）；合并条目是「人 → 第一次点的时刻」的映射，每个人取最小值（防同一个人的多条消息，并让结果与顺序无关）。点经验时的正文另按「事件时刻最晚的那条为准」保留（也与顺序无关，7.3）。`HelpfulNotificationTest` 用同一组点击的全部排列验证最终结果相同。

**代价**：每个合并条目的点过的人存在一张附表里（7.2）；人数只增不减（产品规则本来就要「取消不减」）；极少数情况下（某人较早的那次点击最后才到）条目的时间会往回挪、位置下移一点，这正是按顺序处理时本该有的样子。

## 3. 目标、非目标、反目标

**目标**

1. AC1：C、D 同一天先后点 A 的投稿经验，A 只有一条「D 等 2 人」的提醒；D 点时重新变未读、排到最前。
2. AC2：第二天再有人点，生成新的一条；跨午夜按北京时间切分。
3. AC3：取消有帮助不撤回已发的提醒；同一事件投递两次不重复计人。
4. AC4：铃铛红点不含有帮助的未读数。
5. D7：mini 上实测上面四条，且不发邮件。
6. 契约先行：给门户（PAP-147）的条目形状写进第 9 节与共享笔记，前端照着写。

**非目标**

- 「谁点了」的完整名单页面。
- 有帮助的邮件、偏好开关（release spec 2.1：不发、不提供邮件选项）。
- 回头为 v0.16 上线之前的点击补提醒：信箱从空开始（release spec 第 3 节 L），上线之前的点击没有事件。
- 投稿经验正文修改后刷新旧条目里的摘录：PAP-141 不外送经验的编辑事件（第 7.3 节代价）。

**反目标**（技术上能跑，但算失败）

1. 有帮助计入铃铛红点，或触发提醒邮件。
2. 合并结果取决于消息到达顺序或重复次数（第 2.3 节）。
3. 匿名经验的作者在别人的提醒里露出身份（第 6.3 节）。
4. 内容删除后条目还留在列表里，或未读数没跟着减。
5. 在 interaction 之外存一份可能过时的对象展示信息（标题、链接 key）：展示信息读时现查（PAP-143 第 2.3 节）。

## 4. 已定的前提

| 来源 | 内容 |
|---|---|
| release spec 2.1 | 按「内容 × 北京时间一天」合并；名字写最近一位；当天再点换人、人数加一、重新未读、时间与排序用最新一次；取消不撤回；不进红点；不发邮件 |
| 决策 A | 通知服务不回头调 interaction 计算收件人；收件人 = 被点内容的作者，事件里带 |
| 决策 D | 按事件 ID 去重；墓碑处理「删除先于发表」 |
| PAP-141 | `helpful.marked` → `HelpfulMarked`：`eventId`、`targetType`（`COMMENT` / `POST`）、`targetId`、`targetAuthorId`、`targetAnonymous`、`userId`、`markedAt`、`subject`（`SubjectContext`：`subjectId`、`type`、`ownerId`、`ownerAnonymous`）、`experience`（目标是经验时）、`targetPlainText`（经验无正文时空）。取消**不外送**。只在真插入明细时发，同一人点 → 取消 → 再点会有两条不同 `eventId` 的事件 |
| PAP-143 | 有帮助存同一张 `inbox.notification`（`category = 'HELPFUL'`）；队列 `notification.inbox`、监听类 `InboxEventListener`、单消费线程；列表、未读数、已读、全部已读、保留期、注销清理按行处理，自动包含有帮助；评论删除与经验删除的处理器按 `comment_id` / `subject_id` 删全部类别的条目；评论正文走 `inbox.comment` 快照，快照不存在时可用 `targetPlainText` 插一行 `version = 0`；墓碑查 `CommentContentRepository.isDeleted`、`SubjectTombstoneRepository.exists`；写入或变回未读时发 `UnreadCountsChangedEvent`；对象展示信息与经验结果行由底座 `SubjectViewPort` 读时现查 |
| 设计简报 §3.2、附录 A 9～12 | 条目带最近一位点的人、人数、对象类型、最近一次被点的时间、未读；你那条内容的纯文本摘录（120 字）；经验无正文时给拼结果行的字段；去处 |

PAP-143 的类名与方法名以它合入的代码为准，实施时先核对（计划第 0 步）。本 spec 引用的名字按它分支上 10/31 已提交的代码预填：`NotificationType`、`CommentContentRepository.saveIfNewer` / `isDeleted`、`SubjectTombstoneRepository.exists`、`NotificationRepository.deleteByComment` / `deleteBySubject` / `deleteOccurredBefore` / `deleteAllOf`、`InboxEntry`、`InboxReadAdapter`。

## 5. 子域与分包

全部落在 notification 的 `inbox` 子域，只用底座；**不新增跨子域依赖边**。

| 层 | 新增 / 修改 | 包 |
|---|---|---|
| domain | `HelpfulTargetType`（`COMMENT` / `POST`） | `domain.inbox.model.enums` |
| domain | `HelpfulTarget`、`HelpfulMark` | `domain.inbox.model.vo` |
| domain | `HelpfulDay`（北京时间的日期） | `domain.inbox.policy` |
| domain | `HelpfulNotification`（合并条目，聚合） | `domain.inbox.model.aggregate` |
| domain | `HelpfulNotificationRepository` | `domain.inbox.port.repository` |
| domain | PAP-143 的提醒类型枚举加 `COMMENT_HELPFUL`、`POST_HELPFUL`；`InboxEntry` 加 `actorCount` | 跟随 PAP-143 |
| app | `RecordHelpfulMarkedCommand` / `RecordHelpfulMarkedHandler` | 与 PAP-143 的「评论已发表」处理器同一个用例族 |
| infra | `HelpfulNotificationRepositoryAdapter`（JdbcClient） | `infra.inbox.persistence` |
| infra | `InboxReadAdapter`：摘录取 `COALESCE(评论快照, post_plain_text)`；人数有帮助条目数附表行、其余为 1 | 改 PAP-143 的类 |
| adapter | `InboxEventListener` 加 `@RabbitHandler on(HelpfulMarked)`；列表响应加 `actorCount` | 改 PAP-143 的类 |
| boot | 队列 `notification.inbox` 多绑 `helpful.marked`；`trusted-packages` 加 `dev.patra.interaction.api.reaction.event` | 改 PAP-143 的配置 |
| infra 资源 | Flyway `db/migration/inbox/V…__add_helpful_notification.sql` | — |

## 6. 合并规则

### 6.1 合并键与条目里有什么

**合并键** =（收件人，被点的内容，北京时间的那一天）。

- 收件人 = `targetAuthorId`（被点内容的作者；匿名经验也是真实作者，提醒照常发给他）。
- 被点的内容 = `HelpfulTarget`：评论（`comment_id` = `targetId`，`subject_id` = 评论所在对象）或投稿经验（`comment_id` 为空，`subject_id` = 经验自己的登记对象）。
- 那一天 = `HelpfulDay.of(markedAt)`：`markedAt` 换算到 `Asia/Shanghai` 后的日期。

**一个合并条目**（`HelpfulNotification`）：

| 字段 | 含义 |
|---|---|
| `recipientId`、`notificationId` | 收件人；信箱行的雪花 ID |
| `target` | 被点的内容 |
| `day` | 北京时间的那一天 |
| `marks` | 点过的人 → 他第一次点的时刻（`Map<Long, Instant>`） |
| `owner` | 所在对象的所有者与是否匿名（来自事件的 `subject`，同一条内容恒定），用于第 6.3 节的匿名判定 |
| `readAt` | 已读时刻；空 = 未读 |
| `postPlainText`、`postPlainTextAt` | 点经验时，经验正文的纯文本与它来自哪一刻的点击（取事件时刻最晚的那条，7.3）；点评论时都为空 |

由 `marks` 推出、随条目一起写进信箱行的：人数；最近一位（第一次点的时刻最晚的人，时刻相同取用户 ID 大的）→ `actor_id`；他的时刻 → `occurred_at`；他是否匿名 → `actor_anonymous`。

### 6.2 新的一次点击怎么并进去（`HelpfulNotification.absorb`）

| 情形 | 结果 |
|---|---|
| 这一天还没有条目 | 新建：`marks = {点的人: markedAt}`，未读 |
| 新的人 | 加进 `marks`（人数加一）；`read_at` 置空；重算最近一位：他更晚就换成他、条目挪到最上面，他更早（消息晚到）就名字、时间、位置不变 |
| 已在 `marks` 里，这次的时刻不早于记下的（重复投递已被去重挡掉，这里是「点 → 取消 → 再点」） | **什么都不变**：不重新未读、不换名字、不挪位置 |
| 已在 `marks` 里，这次的时刻更早（他较早那次点击的消息晚到） | 把他的时刻改早；重算最近一位（可能换人、时间往回挪）；不重新未读：人没变多 |
| 点经验，且这条事件的时刻晚于 `postPlainTextAt` | 正文换成这条事件带的（与上面几行独立判断） |

- 「新的人就重新未读」与到达顺序无关：作者看到的人数变了，就该再看一眼。
- 最终的 `marks`、最近一位、时间、正文只取决于有哪些点击，与顺序和重复无关（第 2.3 节）；是否已读取决于作者什么时候看，本来就和顺序无关。
- `absorb` 返回条目有没有变。**变了就发 `UnreadCountsChangedEvent`**，即使未读数没变（条目本来就未读，又多了一个人）：推送端（PAP-145）据此推一次，开着提醒页的门户重新取第一页，看到「王立 等 3 人」（设计简报 §3.4「当天又有人点有帮助」）。没变就不发、也不写库。
- 不发 `NotificationsCreatedEvent`：那是给提醒邮件排队用的，有帮助不发邮件。

### 6.3 匿名

- **被点的是匿名经验，或作者在自己匿名经验下的评论**（`targetAnonymous`）：提醒照常发给真实作者；条目只有他自己看得到，里面没有任何关于他的身份信息，不需要特殊处理。
- **点的人是匿名经验的作者本人**：他可以给自己匿名经验下别人的评论点有帮助。这时若把他的名字写进被点评论作者的提醒，等于告诉对方「这篇匿名经验是他写的」。所以条目的 `actor_anonymous` = 点的人是对象的匿名所有者（`subject.ownerAnonymous && subject.ownerId == userId`），与回复提醒的判定一致；列表里这一位显示为「匿名作者」、不给名片。最近一位换人时随之重算。
- 集合里的用户 ID 从不出现在接口里，只出一个人数。

### 6.4 被点的内容删除

PAP-143 的删除处理器已经覆盖：评论删除（含留占位）按 `comment_id` 删全部类别的条目；经验删除按 `subject_id` 删，经验自己的有帮助条目和它下面评论的有帮助条目一起删。本 Issue 新加的列在同一行上，附表靠级联外键随父行删除（7.1），所以**不用再订阅删除事件、不用改删除方法**；未读数随之减、推送由 PAP-143 发。

乱序（删除先于点击到达）：写之前查墓碑，见第 7.2 节。

## 7. 存储

### 7.1 迁移：给 `inbox.notification` 加列、建附表

`db/migration/inbox/V{时间戳}__add_helpful_notification.sql`（时间戳晚于 PAP-143 的建表脚本）。

**`inbox.notification` 加三列**：

| 列 | 类型 | 说明 |
|---|---|---|
| `helpful_day` | `DATE NULL` | 合并键里的那一天（北京时间）；有帮助条目必填，其余为空 |
| `post_plain_text` | `TEXT NULL` | 点经验时经验正文的纯文本（PAP-128，全文）；经验没有正文时为空；只有 `POST_HELPFUL` 可以非空 |
| `post_plain_text_at` | `timestamptz(6) NULL` | `post_plain_text` 来自哪一刻的点击；`POST_HELPFUL` 必填，其余为空 |

约束：

- `ck_notification_type` 加 `COMMENT_HELPFUL`、`POST_HELPFUL`；`ck_notification_category_type` 加 `(category = 'HELPFUL' AND type IN ('COMMENT_HELPFUL', 'POST_HELPFUL'))`（两条都删了重建）。PAP-143 的 `ck_notification_comment` 只约束回复与 @，不用动。
- PAP-143 的 `created_at`（写入信箱的时刻，`NOT NULL`）：有帮助条目在**第一次写入**时填处理时刻，之后合并不改。有帮助不发邮件，没有别的读者；保持「第一次能看到它的时刻」这层含义。
- 形状：`(category = 'HELPFUL') = (helpful_day IS NOT NULL)`；`(type = 'POST_HELPFUL') = (post_plain_text_at IS NOT NULL)`；`post_plain_text IS NULL OR type = 'POST_HELPFUL'`；`COMMENT_HELPFUL` 的 `comment_id` 非空、`POST_HELPFUL` 的 `comment_id` 为空。
- 合并唯一约束：`uk_notification_helpful ON inbox.notification (recipient_id, subject_id, comment_id, helpful_day) NULLS NOT DISTINCT WHERE category = 'HELPFUL'`。含分区键 `recipient_id`（分区表的唯一索引必须含它）；`NULLS NOT DISTINCT`（PG 15+）让点经验时为空的 `comment_id` 也算相等，否则同一天同一篇经验能插进两行。单消费线程下它不会被撞到，是存储层的兜底。

**附表 `inbox.helpful_actor`：一个合并条目里点过的人，一人一行**

| 列 | 类型 | 说明 |
|---|---|---|
| `recipient_id` | BIGINT NOT NULL | 条目的收件人 |
| `notification_id` | BIGINT NOT NULL | 条目 |
| `actor_id` | BIGINT NOT NULL | 点的人 |
| `first_marked_at` | timestamptz(6) NOT NULL | 他在这一天对这条内容第一次点的时刻 |

主键 `(recipient_id, notification_id, actor_id)`；外键 `(recipient_id, notification_id)` 引用 `inbox.notification` 的主键，**`ON DELETE CASCADE`**。不分区（行数与有帮助条目同量级，所有访问都带主键前缀）。

列名不含用户名（R6）。不需要别的新索引：按合并键查找走 `uk_notification_helpful`，附表按主键前缀取；列表、未读数走 PAP-143 的索引。

### 7.2 为什么点过的人存附表

| 做法 | 取舍 |
|---|---|
| **附表 + 级联外键**（选这个） | 每个人一行、带第一次点的时刻，正好是第 2.3 节要的映射；`ON DELETE CASCADE` 让 PAP-143 的四处删除（评论删除、经验删除、保留期、注销清理）自动带走附表，它的方法一行不改；人数用 `count(*)` 现算，不会和名单对不上 |
| 同一行上一列 `BIGINT[]` | 只能记人、记不了每个人的时刻，做不到与顺序无关（2.3）；要记时刻就得再加一列平行数组，两列靠下标对齐，容易写错 |
| 同一行上一列 `jsonb`（`{"用户ID": "时刻"}`） | 能记时刻，但人数要展开 JSON 才数得出，约束与类型检查都弱；Discourse 的合并提醒用 `data` JSON，是因为它存的是展示用的名字与人数，不需要按人合并 |
| 只存人数 | 去不了同一个人的重复，AC3 的「取消再点」会多算 |

PostgreSQL 从 12 起支持外键引用分区表，级联删除照常生效；`HelpfulNotificationRepositoryAdapterIT` 验证按评论、按对象、按收件人删父行时附表随之清空。

### 7.3 摘录存哪

- **点评论**：用 PAP-143 的评论快照 `inbox.comment`。快照不存在（评论在 v0.16 上线前发的、或者一直没人回复没人 @）时，用事件的 `targetPlainText` 插一行 `version = 0`；之后评论的编辑（版本 ≥ 2）、晚到的发表（版本 1）都会覆盖它。快照已存在时不动：事件里的正文没有版本号，分不清谁新。
- **点经验**：经验没有编辑事件（PAP-141 只外送经验删除），通知服务拿不到版本号，所以不建快照表，直接存在条目行的 `post_plain_text` 上：每条事件来时，事件时刻晚于 `post_plain_text_at` 就换成它带的正文（「时刻最晚者为准」，与顺序无关）。代价：经验改过正文后，**前几天**的条目还是旧正文；当天再有人点会刷新。经验正文上限 2000 字素，一篇经验最多每天一行、保留 5 个月，存储可以忽略。
- 经验没有正文时 `post_plain_text` 为空，列表里摘录为空，门户按 `subject.experience`（PAP-143 读时现查的结果行字段）写「大修 → 录用 · 审稿 6 周」。事件里的 `experience` 字段因此不用存。

## 8. 消费 `helpful.marked`

### 8.1 接线

- boot：`notification.inbox` 的绑定加路由键 `helpful.marked`（`HelpfulMarked.ROUTING_KEY`）。
- `patra.events.consumer.trusted-packages` 加 `dev.patra.interaction.api.reaction.event`。
- `InboxEventListener` 加 `@RabbitHandler void on(HelpfulMarked event)`：只把集成事件映射成 `RecordHelpfulMarkedCommand`，交给命令总线。

### 8.2 处理器 `RecordHelpfulMarkedHandler`（一个事务）

1. `markProcessed("notification.inbox", eventId)` 为假 → 结束（重复投递）。
2. `targetType` 不认识（不是 `COMMENT` / `POST`）→ 打 WARN、结束（PAP-141 契约：不认识的枚举值别抛异常，否则一直重试进死信）。
3. 对象有墓碑（`SubjectTombstoneRepository.exists(subject.subjectId)`）→ 结束：经验已删除，点经验或点它下面的评论都不再生成。
4. 点评论：
   - `targetPlainText` 非空 → `CommentContentRepository.saveIfNewer(commentId, subjectId, 0, targetPlainText, now)`。快照不存在就以 `version = 0` 新建；已存在（任何版本）就不动；返回假（快照是墓碑）→ 结束。同一步拿到快照行锁，与该评论的删除互斥。
   - `targetPlainText` 为空（PAP-141：评论只有 Markdown 结构、没有文字，或 interaction 查不到它）→ 不补快照（`inbox.comment` 的约束要求没删除的快照必须有正文），只查 `CommentContentRepository.isDeleted(commentId)`，是墓碑就结束。条目照常写，没有快照时列表摘录为空。
5. `day = HelpfulDay.of(markedAt)`；按（收件人，目标，day）加锁读合并条目（信箱行 `FOR UPDATE`，连同附表里的点过的人）。
6. 没有 → `HelpfulNotification.open(...)`，插入信箱行（`created_at` = `now`）与第一行附表；有 → `absorb(...)`，变了才写：附表 upsert 这个人（`first_marked_at` 取较早的），信箱行更新 `actor_id`、`actor_anonymous`、`occurred_at`、`read_at`、`post_plain_text`、`post_plain_text_at`。

`now` 取注入的 `Clock`，整个处理器只取一次。
7. 新建或变了 → 发 `UnreadCountsChangedEvent(Set.of(收件人))`。

### 8.3 乱序与重复：逐条判断（每行一个测试）

| 情形 | 结果 |
|---|---|
| 同一事件到两次 | 第 1 步挡住 |
| 同一个人点 → 取消 → 再点（两个事件 ID） | 第 6 步：人在集合里，什么都不变 |
| 后点的人的消息先到 | 先到的建条目；晚到的早点击只加人数、重新未读，不换最近一位（6.2） |
| 同一个人取消前后的两次点击，与别人的点击乱序到达 | 他的时刻取两次中较早的；最近一位与按顺序处理时相同（2.3，全排列测试） |
| 两条点击跨北京时间午夜 | 两个 `day`，两条条目 |
| 23:59 的点击第二天才处理 | 仍合进前一天那一行（按事件时间，2.2） |
| 评论删除先于点击到达 | 第 4 步看到墓碑，结束 |
| 经验删除先于点击（点经验或点它下面的评论）到达 | 第 3 步看到对象墓碑，结束 |
| 点击之后评论 / 经验被删除 | PAP-143 的删除处理器把条目删掉（6.4） |
| 匿名经验作者给自己经验下的评论点 | 条目 `actor_anonymous` 为真，列表不给名片（6.3） |
| 处理中途失败 | 事务回滚，去重记录一起回滚；1、2、4 秒重试，三次后进 `notification.inbox.dlq` |

并发：信箱队列只有一个消费线程（PAP-143 第 7.5 节），合并条目的读改写不会并发；第 5 步仍用 `FOR UPDATE` 读、第 7.1 节的唯一约束兜底，将来改并发不会静默算错。

## 9. 接口：门户看到什么（契约，给 PAP-147）

接口都是 PAP-143 的，路径不变（经网关加 `/patra-notification`）。本 Issue 只让它们带上有帮助条目，并给列表每一行加一个字段 `actorCount`。

### 9.1 未读数 `GET /me/notifications/unread-count`

`{"total": 3, "replies": 2, "mentions": 1, "helpful": 2}`：`helpful` 是有帮助标签的未读条目数（一个合并条目算一个）；`total` = `replies` + `mentions`，**不含** `helpful`，铃铛与账号菜单只用 `total`。`ETag` 已含 `helpful`（PAP-143），有帮助变化也会让 304 失效。

### 9.2 列表 `GET /me/notifications?tab=helpful&cursor=`

```json
{
  "notificationId": "1849…",
  "type": "POST_HELPFUL",
  "read": false,
  "occurredAt": "2026-10-31T06:05:12.345678Z",
  "actor": {"userId": "18…", "username": "zhoumin", "displayName": "周敏", "avatarUrl": "…", "avatarPalette": 3,
            "state": "ACTIVE", "profileVisible": true},
  "actorAnonymous": false,
  "actorCount": 2,
  "subject": {"type": "post", "key": "1832…", "available": true, "title": null, "journal": "Journal of the American Heart Association",
              "abbreviation": "J Am Heart Assoc", "submittedMonth": "2026-03", "mine": true,
              "experience": {"firstDecision": "MAJOR_REVISION", "finalOutcome": "ACCEPTED", "firstRoundDays": 41, "totalDays": 96}},
  "commentId": null,
  "excerpt": "第二轮小修后编辑直接接收……",
  "excerptTruncated": true
}
```

| 字段 | 有帮助条目的规则 |
|---|---|
| `type` | `COMMENT_HELPFUL`（你的评论被点，根评论与楼内回复都算）/ `POST_HELPFUL`（你的投稿经验被点）。文案：1 人「{名字} 觉得你的评论有帮助」，2 人起「{名字} 等 N 人觉得你的投稿经验有帮助」 |
| `actorCount` | **新增，每一行都有**：有帮助条目是点过的人数（≥ 1，取消不减，已注销的人照算）；回复与 @ 恒为 1。门户只在有帮助条目且 ≥ 2 时写「等 N 人」、压 `+N-1` 角标 |
| `actor` / `actorAnonymous` | 最近一位点的人。`actorAnonymous` 为真（他是匿名经验的作者，点的是自己经验下你的评论）时 `actor` 为空，写「匿名作者」、中性头像；已注销时 `actor` 为空，写「已注销用户」。规则与回复提醒相同 |
| `occurredAt` | 最近一位点的时刻；排序与「今天 / 昨天 / 更早」分组都用它。当天有新的人点时会变，条目挪到最上面 |
| `read` | 当天有新的人点时变回 `false` |
| `subject` | `COMMENT_HELPFUL`：评论所在的对象（文献或经验）；`POST_HELPFUL`：经验自己（`type` 为 `post`、`mine` 为真、带 `experience`）。读时现查，interaction 不可用时为空（不出链接、不出摘录） |
| `commentId` | `COMMENT_HELPFUL` 是被点的评论；`POST_HELPFUL` 为空 |
| `excerpt` / `excerptTruncated` | **你自己那条内容**的摘录，规则同回复提醒（120 字素、单行、`<@ID>` 换名字、截了门户加 `…`）。`POST_HELPFUL` 且经验没有文字时为空：门户按 `subject.experience` 写结果行（同经验列表的 `formatOutcomeLine`，「审稿 N 周」由门户按天数算）；`subject.experience` 也为空就不出正文行（「在哪」行已写着「你的投稿经验」）。`COMMENT_HELPFUL` 在评论没有文字时也可能为空，同样不出正文行 |
| 去处 | `COMMENT_HELPFUL`：文献 `/papers/{subject.key}?comment={commentId}`，经验 `/posts/{subject.key}?comment={commentId}`；`POST_HELPFUL`：`/posts/{subject.key}`。点开时调单条已读 |

### 9.3 已读

`PUT /me/notifications/{id}/read` 与 `PUT /me/notifications/read`（全部，三个标签一起）照常，返回的未读数里 `helpful` 同步减。

### 9.4 不会出现的

- 有帮助不发邮件，偏好接口里也没有它（PAP-142 已如此）。
- 被点的内容删除后，条目从列表消失、未读数减少（推送会通知）。

## 10. 测试

| AC / 约束 | 测试 | 层 |
|---|---|---|
| 北京时间切天：`15:59:59.999999Z` 与 `16:00:00Z` 分属两天；同一北京日的两个 UTC 时刻同一天 | `HelpfulDayTest` | domain 单元 |
| 合并规则（6.2 每一行 + 时刻相同按用户 ID）、匿名判定（6.3）、经验正文「时刻最晚者为准」 | `HelpfulNotificationTest` | domain 单元 |
| 与顺序无关：X 点 → 取消 → 再点、Y 在中间点、再加一个重复，这组事件的**全部排列**得到相同的人数、最近一位、时间、正文 | `HelpfulNotificationTest.result_is_independent_of_arrival_order` | domain 单元 |
| AC1：C、D 同一天点 → 一条、人数 2、最近一位 D、未读、`occurredAt` = D 的时刻 | `RecordHelpfulMarkedHandlerTest` + `HelpfulInboxFlowIT` | app 单元 + boot 集成 |
| AC1：已读后新的人点 → 变回未读、排到最前：另一条内容的有帮助条目后建（雪花 ID 更大）但时间更早，再点之后这条排在它前面 | `HelpfulInboxFlowIT`（经 REST 读 `tab=helpful`） | boot 集成 |
| AC2：第二天再有人点 → 新的一条；跨午夜两条 | `RecordHelpfulMarkedHandlerTest` + `HelpfulNotificationRepositoryAdapterIT` | app + infra |
| AC3：同一事件投两次 → 人数 1（真 RabbitMQ 发两次同一条消息） | `HelpfulInboxFlowIT` | boot 集成 |
| AC3：同一个人点 → 取消 → 再点（两个事件 ID）→ 人数 1、不重新未读 | `RecordHelpfulMarkedHandlerTest` + `HelpfulInboxFlowIT` | app + boot |
| AC3：取消不撤回：取消事件不外送（PAP-141 的外送配置测试）；队列绑定里没有 `helpful.unmarked` | PAP-141 测试 + `InboxQueueDeclarationTest`（或 PAP-143 已有的队列声明测试里加断言） | interaction + boot |
| AC4：有帮助未读 2、回复未读 0 → `total` 0、`helpful` 2 | `HelpfulInboxFlowIT`（经 REST 读未读数） | boot 集成 |
| 乱序 8.3 每行 | `RecordHelpfulMarkedHandlerTest`（mock 端口）；墓碑与删除联动在 `HelpfulInboxFlowIT` 用真 PG 各走一遍 | app + boot |
| 迁移：唯一约束（经验的空 `comment_id` 也撞）、形状约束；按评论、按对象、按收件人、按保留期删父行时附表级联清空 | `HelpfulNotificationRepositoryAdapterIT`、迁移 IT 加断言 | infra 集成 |
| 列表读出 `actorCount`、经验正文摘录、点经验时 `commentId` 为空 | `InboxReadAdapterIT` 加用例、`InboxQueryServiceTest` 加用例 | infra + app |
| 不发 `NotificationsCreatedEvent` | `RecordHelpfulMarkedHandlerTest` | app 单元 |

## 11. 上线步骤（追加到 `docs/patra/runbooks/v0.16-notification-go-live-runbook.md`）

1. 部署顺序同 PAP-143：interaction 先发的 `helpful.marked` 在通知服务声明绑定前会被退回、留在 interaction 的 `event_publication`，通知服务起来后由补投送到，不丢。
2. 通知服务 Flyway 执行 `add_helpful_notification`（给 `inbox.notification` 加三列与约束）。
3. RabbitMQ 管理界面核对 `notification.inbox` 绑定了五个路由键（含 `helpful.marked`）。
4. 无新的 XXL 任务。
5. 验证（D7，版本收尾时在 mini 上做）：用三个现有的 e2e 测试账号（凭据在 `~/.patra/secrets/`，演示数据只用测试账号），下面称作者 X、点的人 Y、Z。
   - Y 给 X 的投稿经验点有帮助 → X 的「有帮助」标签里一条「Y 觉得你的投稿经验有帮助」，标签未读 1；铃铛数字不变。X 点开（变已读）。
   - Z 再点 → 条目变成「Z 等 2 人觉得你的投稿经验有帮助」、重新未读、在最上面。
   - Y 取消再点 → 人数仍是 2、名字仍是 Z、不重新未读。
   - X 没有收到提醒邮件：通知服务 `mail` 子域的发送记录（PAP-105）里没有发给 X 的提醒邮件。
   - 北京时间第二天再有人点 → 另起一条（可在午夜后补测；跨午夜的切分另有 `HelpfulDayTest` 与 IT 覆盖）。
   - `notification.inbox.dlq` 为空；interaction 的 `event_publication` 为 0 行。

## 12. 实施顺序

0. **开工前提**：基线（编排会话给的 SHA）必须同时含 PAP-141（`patra-interaction-api` 里有 `HelpfulMarked` 与它的 `ROUTING_KEY`）和 PAP-143（信箱表、监听类、读适配器）。PAP-143 的事件消费部分本就排在 PAP-141 合入之后，所以它汇合进集成分支时两者都在。先核对 PAP-143 的类名、方法名、表的约束名、队列声明与读适配器的 SQL，必要时把本 spec 第 5、7、8 节里引用的名字改成实际的。
1. 契约：第 9 节摘要写进共享笔记 `PAP-144-helpful-contract.md`（与其他 Issue 的契约笔记同目录），开工前已发编排会话转 PAP-147。
2. 领域：`HelpfulDay`、`HelpfulTargetType`、`HelpfulTarget`、`HelpfulMark`、`HelpfulNotification`。
3. 存储：迁移、`HelpfulNotificationRepositoryAdapter`。
4. 应用：`RecordHelpfulMarkedHandler`。
5. 接线：监听方法、队列绑定、`trusted-packages`。
6. 读：`actorCount` 与经验正文摘录（读适配器、查询服务、响应）。
7. 端到端：`HelpfulInboxFlowIT`。
8. 文档：notification README 的 inbox 一节、runbook。

## 13. 考虑过、没选的做法

| 做法 | 没选的原因 |
|---|---|
| 按处理时间（`Clock`）切天 | 故障与补投会把点击切到错误的一天、拆开同一天的条目（2.2） |
| 读时合并（每次点击一行，列表按组聚合） | 分页、未读数、已读都要按组算；PAP-143 的列表与未读数按行设计（2.1） |
| 只存人数 | 去不了同一个人「点 → 取消 → 再点」，AC3 会多算（2.3） |
| 只记点过的人、不记各自的时刻（同一个人再点什么都不做） | 同一个人取消前后的两次点击与别人的点击乱序到达时，最近一位随顺序变（2.3） |
| 点过的人存数组或 jsonb | 数组记不了时刻；jsonb 数人数、加约束都别扭（7.2） |
| 经验正文建快照表（同评论） | 经验没有编辑事件、没有版本号，快照表分不清新旧；每篇经验每天一行，行上直接存足够（7.3） |
| 事件里的 `experience` 结果行存进条目 | PAP-143 读时已经按对象现查经验结果（`subject.experience`），存一份会过时 |
| 有帮助另开队列 | 与删除并发，要给墓碑加锁；PAP-143 已定同一队列（其第 7.5 节） |
| 新的人点了才重新未读，但晚到的早点击不重新未读 | 规则依赖顺序；人数变了就该再看一眼更简单（6.2） |
| 同一个人再点也挪到最上面 | 反复点、取消就能刷别人的未读（2.3） |
| 发 `NotificationsCreatedEvent` 让邮件侧自己过滤 | 有帮助不发邮件，不发事件最直接，邮件侧不用知道有帮助 |

## 14. 拍板记录

**编排会话代用户拍板（2026-10-31）**

1. `helpful.marked` 带 `markedAt`、目标纯文本全文、经验无正文时的结果行字段与 `subject`；「在哪」不进事件，读时调 interaction 内部接口现查（文献 key 重导会变，存快照链接会 404）。
2. `helpful.unmarked` 不外送；Linear To-Do「消费有帮助已取消事件」划掉：通知侧收到取消什么都不用做（不撤回、不减人数），不外送就没有无人绑定、被无限补投的问题。
3. 有帮助存进 PAP-143 的同一张表、同一个队列，删除联动由 PAP-143 的处理器覆盖。

**本 spec 自己定的（理由见各节）**

1. 合并键用事件时间 `markedAt` 换算北京日期，不注入时钟（2.2）。
2. 每个人只算他第一次点：合并条目记「人 → 第一次点的时刻」，取最小值；同一个人再点（时刻更晚）什么都不变；新的人（不论先后到达）都重新未读（2.3、6.2）。
3. 最近一位 = 第一次点的时刻最晚的人，时刻相同取用户 ID 大的；全排列测试保证与顺序无关（2.3）。
4. 匿名经验作者点自己经验下别人的评论时，条目显示「匿名作者」（6.3）。
5. 点过的人存附表 `inbox.helpful_actor`，级联外键随信箱行删除（7.1、7.2）。
6. 评论摘录用 PAP-143 的快照（缺时插 `version = 0`）；经验正文存条目行上，事件时刻最晚者为准（7.3）。
7. 类型值 `COMMENT_HELPFUL` / `POST_HELPFUL`；列表每行加 `actorCount`（回复与 @ 为 1）（9.2）。
8. 条目变了就发 `UnreadCountsChangedEvent`（即使数字没变），不发 `NotificationsCreatedEvent`（6.2）。

## 15. 实施中的偏离与补充（2026-10-31）

1. **聚合的访问器**照 PAP-142 的 `EmailPreference` 用 Lombok `@Getter`（`getNotificationId()`、`getActorCount()`、`isLatestActorAnonymous()` 等），计划里写的 `notificationId()` 一类改名。
2. **北京时间**复用 `InboxRetention.ZONE`，`HelpfulDay` 不另起常量：信箱子域只留一个「北京时间」的来源。
3. **迁移版本号** `V20261031130000`：晚于 PAP-143 已有的 `V20261031120200`（那批脚本的时间戳是名义时刻），任何执行过 PAP-143 脚本的库上都不乱序。
4. **约束名**：第 7.1 节的三条形状约束落成 `ck_notification_helpful_day`、`ck_notification_helpful_comment`、`ck_notification_post_text`；附表外键 `fk_helpful_actor_notification`。分区表上撞唯一约束时报的是分区里那份索引的名字（`notification_p00_…_idx`），测试按键列断言。
5. **评论没有文字**（PAP-141 10/31 02:52 定稿：评论的 `targetPlainText` 也可能为空）：不补快照、只查 `CommentContentRepository.isDeleted`（第 8.2 节第 4 步）。代价：这种评论的写入不拿快照行锁，和该评论的删除之间靠单消费线程顺序处理。
6. **处理结果**沿用 PAP-143 的 `IngestOutcome`：不认识的目标种类记 `DISCARDED`；同一个人更晚再点（条目没变）记 `APPLIED`。只用于日志。
7. **改了 PAP-143 的测试**：`InboxPersistenceITSupport` 的 `TRUNCATE` 加 `inbox.helpful_actor`（外键引用父表，单独截父表会报错）；`InboxRestFlowIT` 的列表用例加 `actorCount = 1` 的断言；PAP-143 没有监听与队列声明的单测，新建 `InboxEventListenerTest`、`InboxQueueConfigurationTest`。
8. **基线上曾经的失败**（不是本 Issue 引起）：`e5f3f0add` 上 `SecurityMailFlowIT`、`SecurityMailDeadLetterIT` 共 11 例超时（PAP-143 在 boot 的测试 profile 关了 RabbitMQ 监听的 `auto-startup`，PAP-105 的基座没打开），已由基线 `8a314a375` 修好；本 Issue 变基到 `123205980` 后全绿。
9. **独立评审后补的**（opus，0 Critical）：仓储的 `insert` / `update` 也加 `MANDATORY`（两条语句必须同成同败，否则留下没有附表行的条目、这个合并键之后每次都恢复失败）；补测试：匿名经验作者点评论时库里与接口都是匿名、评论删除先于点击到达、同一人取消再点后已读不变；runbook D7 第 4、6 步改正（第 6 步要先取消再点，直接点就是取消）。与 PAP-146 合并时，它抽出的共享 `SELECT` 要带上本 Issue 的 `actor_count` 子查询与 `COALESCE(c.plain_text, n.post_plain_text)`，否则邮件侧的读取找不到 `actor_count` 列。
