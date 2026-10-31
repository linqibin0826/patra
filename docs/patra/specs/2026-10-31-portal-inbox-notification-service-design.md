# 门户提醒改接通知服务、「有帮助」标签与合并条目（PAP-147）工程 spec

> **Issue**：Linear PAP-147，v0.16 Notification，Milestone FE Ready；同版前端 PAP-148（已提交 `b89bc7ecb`）之后、PAP-149（实时推送）之前。
> **产品定义**：release spec `docs/patra/release-specs/v0.16-notification.md` 2.1（三个标签、有帮助按「内容 × 北京时间一天」合并、写最近一位、不进红点）。
> **页面、状态、文案**：设计简报 `patra-portal/docs/design-briefs/v0.16/notification.md` §3.1–§3.3、§4.1、§6；画布 `Inbox` / `Inbox-M`。沿用 PAP-127 方向 A（`docs/patra/specs/2026-10-28-notification-inbox-redesign-design.md`）。实时推送（§3.4）归 PAP-149。
> **接口**：共享笔记 `PAP-143-inbox-contract.md` 第 1 节（10/31 02:14 版）、`PAP-144-helpful-contract.md`（02:20 版）；后端尚未部署，按契约写代码与单测，联调与 e2e 等 mini。
> **决策授权**：用户 10/31 授权子会话按最优方案拍板（经编排会话转达），「我定的」在第 9 节。
> **状态**：已实施，待联调（2026-10-31，计划 `docs/patra/plans/2026-10-31-portal-inbox-notification-service.md`）；e2e 等后端部署到 mini；实施中的偏离见第 10 节
> **创建日期**：2026-10-31

---

## 1. 要做什么

1. 提醒的四个接口从 `/patra-interaction` 改到 `/patra-notification`，按新契约改 schema；删掉 interaction 的旧调用层。
2. 第三个标签「有帮助」（`/notifications/helpful`），合并条目「周敏 等 2 人觉得你的投稿经验有帮助」，点开去被点的内容。
3. 铃铛、账号菜单只数回复和 @；三个标签各自显示未读胶囊；「全部标为已读」覆盖三个标签。

## 2. 范围

**做**：调用层搬家与 schema；三个标签、有帮助行（头像 + 人数角标、文案、摘录或结果行、去处）、第三份空状态；未读数与乐观已读按三类算；单测；1440×900 / 375×812 视觉核对（本地数据桩）。

**不做**：推送后重取第一页与新条目淡出（PAP-149）；画板的「拇指图标」备选头像（简报推荐头像 + 角标）；e2e（等 mini 部署后，`tests/e2e/notifications.spec.ts` 照常对 mini 跑，再补有帮助一例）。

## 3. 契约与现状的差异

| 处 | 现在（interaction） | 新契约 | 门户做法 |
|---|---|---|---|
| 前缀 | `/patra-interaction/me/notifications…` | `/patra-notification/me/notifications…` | 调用层改到 `src/lib/notification/inbox.ts`（同 PAP-148 的 `callNotification`）；门户的 Route Handler 路径不变 |
| 时间 | `createdAt` | `occurredAt`（有帮助再被点时会变，消息乱序时还可能往回挪） | 分组、行尾时间都用它；**不在门户重排**，按服务端顺序展示（第 6 节） |
| `commentId` | 必有 | 可空（有帮助点经验时为空） | 为空时链接不带 `?comment=` |
| `subject.experience` | 无 | 经验才有：`firstDecision`、`finalOutcome`、`firstRoundDays`、`totalDays`（可空） | 有帮助点经验且没有摘录时写结果行 |
| 未读数 | `total`、`replies`、`mentions` | 多 `helpful`；`total` 仍是回复 + @ | 铃铛、菜单用 `total`；三个标签各用自己的数 |
| 标签 | `replies`、`mentions` | 多 `helpful` | 新页 `/notifications/helpful` |
| 类型 | 三种 | 多 `COMMENT_HELPFUL`、`POST_HELPFUL` | 只出现在有帮助标签 |
| `actorCount` | 无 | 所有行都有，有帮助 ≥ 1，回复与 @ 恒为 1 | ≥ 2 时写「等 N 人」、头像压 `+{N−1}` |
| `nextCursor` | 提醒 ID | 不透明 | 原样回传，不解析（现在就没解析） |
| ETag | `"{total}-…"` | `"{replies}-{mentions}-{helpful}"` | 原样转发，门户不拼 |

## 4. 调用层

- `src/lib/notification/inbox-schema.ts`（不加 `server-only`，客户端组件要用类型）：`unreadCountsSchema`（四个非负整数）、`notificationTabSchema`（`replies` / `mentions` / `helpful`）、`notificationTypeSchema`（五种）、`notificationSubjectSchema`（原字段 + `experience: { firstDecision, finalOutcome, firstRoundDays, totalDays: number | null } | null`，两个枚举复用 `lib/experience/labels` 的 `FIRST_DECISIONS` / `FINAL_OUTCOMES`）、`notificationSchema`（`occurredAt`、`commentId: string | null`、`actorCount: 正整数`，其余同现在）、`notificationPageSchema`。
- `src/lib/notification/inbox.ts`（server-only）：`fetchUnreadCounts(ifNoneMatch)`、`fetchNotifications(tab, cursor?)`、`markNotificationRead(id)`、`markAllNotificationsRead()`，行为同现在的 interaction 版（304 原样、5 秒超时、`toRead` 归类），只换调用 `callNotification`。游标走 `query`，不再手拼进路径。
- `toRead` 目前在 `lib/interaction/read.ts`，接受同形的回复，直接复用。
- 删掉 `src/lib/interaction/notifications.ts`、`src/lib/interaction/notification-schema.ts` 与它们的测试；所有 import 改到新位置。

## 5. 界面

### 5.1 标签与页面

- `TABS` 加 `{ tab: "helpful", label: "有帮助", href: "/notifications/helpful" }`；新页 `src/app/notifications/helpful/page.tsx`（`requireUser("/notifications/helpful")`）。窄屏三个标签已是等分（`flex-1`）。
- 每个标签的胶囊用自己的未读数（`counts.helpful` 只在这里出现）。
- 「全部标为已读」：三个未读数之和为 0 时置灰；乐观清零三个数（`total` 也清零）。

### 5.2 有帮助的行（`NotificationRow`，同一组件按类型出文案）

- **头像**：最近一位点的人（`actor`；匿名作者、已注销同回复行的规则）。`actorCount ≥ 2` 时右下角压 `+{N−1}`：等宽 10px，`paper-50` 底，1px `border-default` 描边，`fg-2`，`aria-hidden`（名字行里已有人数）。位置照画板 `.pt-iav-n`（右 −6px、下 −4px，高 16、最小宽 18）。
- **上行**：名字加粗（未读）/ 中粗（已读），后面常规字重的 `等 N 人觉得你的评论有帮助` 或 `觉得你的投稿经验有帮助`（N = `actorCount`，< 2 时不写「等 N 人」）；`COMMENT_HELPFUL` 说「评论」，`POST_HELPFUL` 说「投稿经验」。时间靠右，用 `occurredAt`。
- **正文**：摘录（`excerptTruncated` 加 `…`）；颜色恒为 `fg-2`，不随未读加深（简报 §3.2）。`POST_HELPFUL` 且摘录为空、有 `experience`：写结果行，**与经验列表同一个函数**（`formatOutcomeLine`，箭头两侧的双空格换成单空格，同 `ExperienceRow`）。两者都没有时不出这一行。
- **在哪**：同回复行（`contextLabel`）。
- **去处**：`COMMENT_HELPFUL` → `/papers/{key}?comment={commentId}` 或 `/posts/{key}?comment={commentId}`；`POST_HELPFUL` → `/posts/{key}`。规则一般化为「`commentId` 为空就不带 `?comment=`」。点开照常乐观已读。
- **读屏**：`未读，周敏 等 2 人觉得你的投稿经验有帮助，14:05`（现有结构天然如此）。

### 5.3 空状态

`NotificationEmpty` 加第三份：`ThumbsUp` 48px，`还没有收到「有帮助」` / `别人觉得你的评论或投稿经验有帮助时，同一条内容每天合成一条，出现在这里。`

## 6. 列表顺序与去重

- 服务端按 `occurredAt` 倒序返回、游标翻页。门户**保持服务端顺序**：分组按北京时间自然日把条目放进「今天 / 昨天 / 更早」，组内保持传入顺序（现有 `groupByDay` 就是如此），不按时间排序。
- 有帮助条目的 `occurredAt` 会变（当天再有人点往前挪，消息乱序时往回挪），已加载的页之间可能出现同一条两次：拼页时按 `notificationId` 去重，保留第一次出现的（复用 `uniqueBy`）。
- 乐观已读按类型减对应的数：`MENTION` 减 `mentions` 与 `total`；回复两类减 `replies` 与 `total`；有帮助两类只减 `helpful`。

## 7. 测试（对照 AC）

| AC | 测试 |
|---|---|
| 三个标签都能看、能标已读，未读数正确 | `inbox.test.ts`（四个接口打到 `/patra-notification`、带会话、304、游标走查询串、新字段解析、有帮助标签）、`notification-routes.test.ts`（`tab=helpful` 放行、不认识的 404）、`NotificationInbox.test.tsx`（三个标签与各自胶囊、全部已读三数之和、空状态第三份）、`mark-read.test.tsx`（有帮助只减 `helpful`、全部已读清零四个数）、`notifications-page.test.tsx`（`/notifications/helpful` 守卫） |
| 合并条目文案与人数正确，点开定位到被点的内容 | `row.test.ts`（`等 N 人` 与两种对象说法、1 人不写人数、结果行、两种去处、`commentId` 为空不带参数）、`NotificationRow.test.tsx`（`+N−1` 角标与 `aria-hidden`、正文颜色不随未读、读屏顺序） |
| 红点不含有帮助的未读数 | `NotificationBell.test.tsx`（`helpful` 很大、`total` 为 0 时不挂徽标）、`UserMenu` / `MobileAccount` 现有测试补 `helpful` |
| 1440×900 与 375×812 按画板 | 本地数据桩 + Playwright 截图，与 `Inbox` / `Inbox-M` 对照 |

另：`groups.test.ts` 改用 `occurredAt`；去重一例（两页里同一条只出现一次）。

## 8. 交接

- 共享笔记 `PAP-147-portal-handover.md`：门户接的四个接口与字段、待 mini 部署后要跑的 e2e。
- PAP-149 接着用：`queryKeys.notificationUnread` 的缓存形状不变（`{ counts, etag }`，`counts` 多 `helpful`）；推送来的数据与 `unread-count` 同形，直接写进这个缓存。

## 9. 我定的

1. 调用层搬到 `src/lib/notification/inbox*.ts`（按后端服务命名，同 PAP-148），interaction 旧版直接删。
2. 没有正文的经验写结果行时用经验列表的 `formatOutcomeLine`（`首轮 41 天 · 大修 → 录用`），不照简报示例另造「审稿 N 周」写法：简报说「同经验列表」，门户里也没有「审稿 N 周」这种格式，一处规则一处实现。
3. `actorCount` 按契约必填（后端三个 Issue 随同一个后端 PR 上线，不存在只上 PAP-143 的中间态）。
4. 不在门户按时间重排；拼页按 ID 去重（第 6 节）。
5. 头像只做「头像 + 人数角标」，不做拇指备选（简报推荐）。

## 10. 实施记录（2026-10-31）

| # | 偏离或补充 | 原因 |
|---|---|---|
| 1 | 有帮助行的行尾时间与另外两个标签一样用 `formatGroupedTime`（近的写相对时间），画板示例写的是「14:05」 | 同一个组件、同一条规则；画板里回复行也是相对时间 |
| 2 | `subject.experience` 解析不了（后端加了新枚举值）时当作 `null`（`.catch(null)`），不让整页失败 | 独立评审（opus）发现：凡是对象为经验的行都带它，一个新值会让三个标签都「暂时打不开」；补了一例测试，先红后绿 |
| 3 | 对照 PAP-144 契约 02:53 版：`COMMENT_HELPFUL` 的摘录也可能为空、`experience` 为空时都不出正文行 | `bodyOf` 本来就如此，`row.test.ts` 覆盖 |
| 4 | 评审留下的小问题不改：拼页去重保留先出现的那份（后一页那份可能更新、是未读，罕见情况下圆点与胶囊对不上，PAP-149 推送后重取第一页会纠正）；列表解析失败的日志不带标签；菜单的「helpful 大、total 为 0」没有单独的测试 | 都不影响正常使用，单独列出供后续处理 |
| 5 | 视觉核对：本地数据桩 + Playwright，1440×900 与 375×812 各 4 屏（有帮助、回复我的、有帮助为空、取不到），与画板 `Inbox` / `Inbox-M` 对照一致 | 第 7 节 AC 第 4 条 |
