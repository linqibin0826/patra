# 提醒页重设计与内容删除后的提醒（PAP-127）

> Linear PAP-127（GitHub #307），Backlog 单条，不挂版本。分支 `feat/pap-127-notification-inbox`，前后端一个 PR。
> 画布：https://claude.ai/artifact/Qwn6xpQaJAkTPFnFT6Mdix （定稿板：`DirA` 桌面、`DirA-M` 窄屏 375、`Rule3` 删除规则；`Main` 是现状，`DirB` / `DirC` / `Rule1` / `Rule2` 是未采用的对比稿）。
> 现状出处：设计简报 `patra-portal/docs/design-briefs/v0.14/discussion.md` §3.19、§4.6；后端 `docs/patra/specs/2026-10-27-interaction-notification-design.md` §4.4、§7、§9；门户 `docs/patra/specs/2026-10-28-portal-notification-design.md`。

## 1. 为什么改

v0.14 上线的提醒页（`/notifications`、`/notifications/mentions`）功能可用，用户验收时觉得「看起来很乱」，原话：「全都是已删除，然后一排下去全是提醒，都不知道看啥、啥是重点，得一行行看下去。」两个问题：

1. **没有主次**：每条提醒一样的样式平铺，谁、做了什么、时间、摘录、上下文五样东西挤在三行里，字号接近；长文献标题每条都重复一遍。
2. **删除留下噪音**：评论被删后提醒原位显示「该评论已删除」，经验被删后显示「一条已删除的投稿经验」，内容没了提醒还在、还算未读。

调研（2026-10-28，四组：B站 / 知乎 / 小红书、微博 / 抖音 / 贴吧、Reddit / X / YouTube / Instagram、Discourse / Mastodon / GitHub / Stack Exchange）：闭源平台都没有公开删除后提醒的处理规则，用户反馈的普遍现象是「提醒残留、点进去内容没了」，且被当作毛病抱怨；读源码可证的 Discourse（帖子删除时 `delete_all` 对应通知）与 Mastodon（状态删除时 `dependent: :destroy` 级联删通知）都是**内容删除即删通知**，未读数因此天然一致。回复类、@ 类通知在可查证的产品里都不聚合。

## 2. 目标、不做、反目标

**目标**

1. 列表一眼能分出新旧、一眼能看到别人说了什么：按「今天 / 昨天 / 更早」分组，每条以对方的话为主角。
2. 评论或投稿经验被删除后，对应的提醒随之删除，列表里不再出现「已删除」占位，未读数同步减少。
3. 库里已有的「已删除」提醒（含 v0.14 测试留下的大量条目）上线时清掉。
4. 桌面与 375 宽窄屏按画布定稿呈现。

**不做**

- 按讨论归拢成卡片（画布 `DirB`）；回复类提醒里引用「你的原话」（`DirC`，B站 / 微博接口有先例，以后要做另起）。
- 「A 等 3 人」聚合、删除单条提醒、提醒偏好、实时推送（仍属通知中心那一版）。
- 摘录 / 正文转纯文本的方式（PAP-128）。
- 文献从 catalog 下线（对象 `available = false` 且类型是 publication）的提醒：保持现状，不删、不改展示。
- 编辑时删掉 @ 不撤回已发的提醒（保持后端 spec §4.3 第 3 条）。
- 事务写法（`TransactionOperations` / `@Transactional` 的取舍）：由「[事务] 盘点事务写法与规范」会话统一处理，本版沿用当时主干的写法。

**反目标**（能跑也算失败）

- 列表已经干净了，铃铛上还挂着删不掉的未读数（列表与未读数用不同的「可见」条件）。
- 只在门户把已删除的条目藏起来，接口和未读数照旧返回它们。
- 删除提醒的步骤失败了，评论或经验却删成功了（提醒与内容不一致）。
- 分组标题跟着「加载更多」重复出现（第一页一个「更早」、第二页又一个「更早」）。

## 3. 删除规则（后端）

### 3.1 规则

| 发生了什么 | 提醒怎么处理 |
|---|---|
| 一条评论被删除（变占位或整条去掉，作者自删） | 以这条评论为触发的**所有收件人**的提醒全部删除（`comment_id = 这条评论`） |
| 一条投稿经验被删除 | 挂在这条经验下的**所有**提醒全部删除（`subject_id = 这条经验的对象`），不论触发评论是否还在 |
| 你被回复的那条原评论被删、对方的回复还在 | 不处理：对方的回复在讨论里仍可见，提醒仍有用 |
| 文献下线 | 不处理（见第 2 节「不做」） |

删除是物理删除（`DELETE`），不留标记、不可恢复。评论与经验目前都没有「恢复」功能，以后要做恢复时也不重建提醒（同 Discourse 的 `recover`）。未读数查询不改：行没了，计数自然一致。

### 3.2 事件

- **评论删除**：沿用 `CommentDeletedEvent`（`domain/discussion/event/`）。`CommentNotificationListener.on(CommentDeletedEvent)` 从 `markCommentDeleted(commentId)` 改为删除这条评论的全部提醒。事件 Javadoc 里「提醒列表据此显示『评论已删除』」改成新语义。
- **经验删除**：新增领域事件 `PostDeletedEvent`，放 `domain/post/event/`（新包）。`DeleteExperienceHandler` 在软删经验、把登记行置 `UNAVAILABLE` 的**同一个事务**里、用 `ApplicationEventPublisher` 发布（与 `DeleteCommentHandler` 发 `CommentDeletedEvent` 同一写法；handler 目前没注入 publisher，要加）；两处幂等返回（锁前、锁内复查）都不发。事件字段至少要让提醒子域能定位到对象：`subjectId`——**这条经验自己在登记表里的那一行**（`target_type` 为 POST、`source_id` 等于帖子 ID，可复用 `PostRegistry` 里按键查 ID 的查找），**不是** `post.subject_id`（那是期刊的对象 ID）；另带 `postId`、`authorId`、`deletedAt` 供以后的订阅方使用。提醒子域新增订阅方法（放进现有监听器或新建一个 post 专用监听器，由实现者按子域边界决定），同步 `@EventListener`、失败时经验删除一起回滚。
- **子域边界**：`InteractionArchitectureTest` 目前只声明了 `.allowingSubscription("notification", "discussion")`，要加 `.allowingSubscription("notification", "post")`。
- 两个事件都只在 interaction 进程内传递，不走 Outbox / RocketMQ；经核对没有其他服务引用评论删除。

### 3.3 端口与存储

- `NotificationRepository`：去掉 `markCommentDeleted`，新增按评论删除、按对象删除两个方法（返回删除行数，供日志）。`refreshExcerpt` 里的 `AND NOT comment_deleted` 条件随列一起去掉。
- 迁移（`db/migration/notification/`，时间戳晚于现有最后一条）：
  1. 删掉已有的 `comment_deleted = true` 的行；
  2. 删掉对象是**已删除投稿经验**的行：关联 `subject.registry` 里 `target_type = 3`（POST）且 `state = 'UNAVAILABLE'` 的行（Flyway 全服务共用一份历史，跨 schema 关联可执行；只在这条一次性数据迁移里这样做，运行时代码不跨子域读表）；
  3. 去掉约束 `ck_notification_deleted_excerpt` 和列 `comment_deleted`（分区父表上执行，16 个分区随之生效）；
  4. 加 `subject_id` 索引：提醒表按收件人哈希分区，现有只有 `comment_id`、`created_at` 与未读索引，按对象删会在删经验的事务里扫全部 16 个分区。
- 仓库里还没有「先迁到指定版本、造数据、再迁到最新」的迁移测试设施（现有迁移 IT 都是一次迁到最新），迁移 IT 的做法由实现者定（如 Flyway `target` 分两段迁）。
- 读模型与接口：`NotificationEntry`、`NotificationView`、`NotificationResponse` 去掉 `commentDeleted`；接口响应不再有这个字段（门户同 PR 一起改：`notification-schema.ts` 里 `commentDeleted: z.boolean()` 是必填，不删会让列表解析失败；没有别的调用方）。`excerpt` 仍可为 null，此时只剩「文献已下线」一种情况。
- 文档：interaction `README.md` 的事件一节补 `PostDeletedEvent`，提醒子域的说明改成「删除即删提醒」；后端 runbook 里与「已删除提醒」相关的核对步骤（如有）同步。

## 4. 页面（门户，方向 A）

### 4.1 不变的部分

标题「提醒」与「全部标为已读」（窄屏「全部已读」文字按钮）、「回复我的 / @ 我的」两个标签与未读胶囊、加载 / 失败 / 空状态、每页 20 条与「加载更多提醒」、「提醒保留 5 个月。」、点一行乐观标已读并按永久链接跳转、会话过期跳登录：全部照 v0.14 现状（简报 §3.19）。

### 4.2 分组

- 按**北京时间**的自然日分三组：今天、昨天、更早；「更早」之后不再细分。复用 `src/lib/format-time.ts` 里的北京时间拆分 `partsOf` 与日序号 `dayNumber`（与用户中心同一套，服务端与浏览器结果一致）；这两个函数目前没导出，要么导出、要么把分组用的函数写进同一个文件。
- 分组在合并后的全部已加载条目上计算（`pages.flatMap`），加载更多后新条目并入已有分组，同一个分组标题只出现一次。
- 某组没有条目就不渲染那组标题。
- 分组标题：`text-xs` 中粗、`fg-3`、字距略宽，下方一条 `border-subtle` 分隔线；第一组上边距小于后续组（画布 `DirA`：首组 20px，后续 28px）。语义上每组是一个 `section`，标题用 `h2`。

### 4.3 一条提醒

整行仍是链接（对象 key 为空时是按钮），三层：

1. **上行（小字）**：触发人名字 + 动作 + 时间，`text-sm`。名字字重未读 600、已读 500；名字颜色沿用现有 `nameColor` 规则（普通用户未读 `fg-1` / 已读 `fg-2`，匿名作者未读 `fg-2` / 已读 `fg-3`，已注销用户一律 `fg-3`）；动作 `fg-3`；时间 `fg-3`、等宽数字、**靠右**。窄屏时动作过长截断成省略号，时间不换行。
2. **正文（主角）**：对方的话（后端摘录），`text-base`、行高 24px，最多两行截断；未读 `fg-1`、已读 `fg-2`。`excerpt` 为 null（只剩文献下线这一种）时不渲染这一层。
3. **下行（在哪）**：前置 13px 线性图标（文献：文件图标；投稿经验：笔图标）+ 一行截断的文字，`text-xs` `fg-3`：
   - 文献：文献标题本身（不再包「《》的讨论」），PubMed 内联标记照旧按白名单渲染；没有标题写「一篇文献的讨论」。
   - 自己的经验：`你的投稿经验 · <期刊缩写> · 2026 年 3 月投稿`（同现状）。
   - 别人的经验：`<期刊缩写> 的一条投稿经验`（同现状）。
   - 「一条已删除的投稿经验」这条分支删除（这类提醒已不存在）。

**动作文字**（比现状短，因为「在哪」已由下行说明）：

| 类型 | 现状 | 新 |
|---|---|---|
| `COMMENT_REPLY` | 回复了你的评论 | 回复了你 |
| `POST_COMMENT` | 评论了你的投稿经验 | 评论了你的投稿经验 |
| `MENTION` | 在评论中 @ 了你 | @ 了你 |

**时间写法**（分组已经说明了是哪天）：今天组用相对时间（`刚刚`、`5 分钟前`、`3 小时前`，沿用现有 `formatRelative` 对当天的写法）；昨天组写 `HH:mm`；更早组写 `10 月 21 日`，不是今年时写 `2025 年 10 月 21 日`。悬停 `title` 仍是完整时间 `2026-10-20 09:12`。

**未读 / 已读**：未读在头像左侧页边距里一个 6px `clay-500` 圆点（桌面距内容左缘 16px，窄屏 12px），名字加粗、正文 `fg-1`；**去掉未读行的 `paper-50` 浅底**；已读无圆点、整行文字降一级。行的左缘与 h1、标签栏对齐（圆点不再占行内一列）；悬停 `bg-sunken` 只铺行本身，不向左盖住页边距里的圆点。读屏仍在开头读「未读，」。

**头像**：32px（桌面与窄屏同为 32，现状桌面是 36）；匿名作者、已注销用户的中性头像与名字规则不变（简报 §3.20）。

**行距**：每条上下内边距 16px（窄屏 14px），条与条之间 `border-subtle` 分隔线；头像与文字间距 12px（窄屏 10px）；三层之间 6px。

### 4.4 删掉的展示

- 「该评论已删除」斜体占位、「一条已删除的投稿经验」上下文：随接口字段一起删除。
- `excerptOf` 的 `deleted` 分支、`notificationHref` 里「经验已删除去经验页」的分支：经验删除后不再有提醒，分支删除。文献下线（`available = false`）时仍按现状去文献页的单楼链接。

### 4.5 窄屏（375，画布 `DirA-M`）

页边距 20px；h1 30px；两个标签各占一半宽度；分组、一条提醒的结构与桌面相同，只有第 4.3 节写明的尺寸差异。

## 5. 设计资料

- 画布定稿后存快照：`docs/patra/design/snapshots/2026-10-28-notification-inbox-hifi.zip`（`project/` 全部画板 + `p127.css`、`patra.css` + README 写明哪些是定稿）；`snapshots/README.md` 的索引补一行。
- 设计简报 `patra-portal/docs/design-briefs/v0.14/discussion.md` §3.19 的「一行」与 ASCII 示意改成新版式（指向本 spec 与画布），§4.6 的 `inbox.row.comment-deleted` / `.post-deleted` 两个状态删除、加一个 `inbox.groups`（今天 / 昨天 / 更早）。简报只保留当前版本，不写「旧版」对照。

## 6. 测试与验证

后端（按 `.claude/rules/testing/conventions.md`，TDD）：

- 监听器单测：评论删除事件 → 调按评论删除；经验删除事件 → 调按对象删除。
- `DeleteExperienceHandler` 单测：删除时发布 `PostDeletedEvent`（字段正确）；已删除再删不发；不是作者、不存在时不发。
- `NotificationRepositoryAdapterIT`：按评论删只删这条评论的提醒、跨收件人全删；按对象删只删这个对象的；其他行不动。
- 迁移 IT：造 `comment_deleted = true` 的行、已删除经验下的行、正常行，跑迁移后只剩正常行，列与约束已去掉。
- 流程 IT（`patra-interaction-boot/src/integrationTest/.../notification/` 下现有的 `NotificationRulesFlowIT`、`NotificationReadFlowIT`、`NotificationRollbackFlowIT`，评论删除回滚在 `discussion/DiscussionEventRollbackFlowIT`，按主题补进对应文件）：删评论后，被回复者的列表里没有这条、未读数减 1（ETag 变化，带旧 ETag 取回 200）；删经验后，经验下所有人的相关提醒消失、未读数随之减少；回滚：提醒删除失败时评论 / 经验也没删。
- `InteractionArchitectureTest` 随新订阅边一起通过。
- PR 前全仓 `./gradlew check`。

门户（Vitest + Testing Library，TDD）：

- 分组纯函数：北京时间跨日边界（23:59 / 00:00）、昨天、更早、跨年；加载更多后不重复分组。
- `NotificationRow`：三层结构与文字、动作文字、时间写法按组、未读圆点与读屏文案、文献 / 自己经验 / 别人经验 / 无标题文献四种下行、excerpt 为 null 时没有正文层。
- 删除 `commentDeleted` / 已删除经验相关的旧用例与 fixture 字段。
- Playwright 冒烟 `tests/e2e/notifications.spec.ts`：沿用两个账号的场景；补一条「B 删除回复评论后，A 的提醒列表里这条消失、铃铛数字减 1」。
- 本机对照画布截图检查桌面与 375 窄屏（`DirA`、`DirA-M`）。

上线后（mini）：迁移执行后用户真实账号 A 的「该评论已删除」提醒清零；未读数与列表一致。

## 7. 留给实现者

- `PostDeletedEvent` 的订阅方放在现有 `CommentNotificationListener` 里还是新建监听器、两个仓储方法的命名。
- 迁移第 2 步关联已删除经验的具体 SQL（以 post / subject 登记表的实际列为准）。
- 分组函数与时间写法函数放在 `src/lib/notifications/` 还是 `format-time.ts`，组件拆分方式。
- 实现时先看「[事务] 盘点事务写法与规范」会话是否已改了 `DeleteExperienceHandler` / 监听器的事务写法，按主干最新写法发布事件。
