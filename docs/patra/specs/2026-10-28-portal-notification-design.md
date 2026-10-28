# 门户站内提醒：导航入口、60 秒轮询与提醒列表（PAP-126）

> 版本：v0.14「Discussion」前端第二波，Linear PAP-126（GitHub #303）。
> 产品定义：release spec `docs/patra/release-specs/v0.14-discussion.md` 2.1.4、决策 D、F，Done 判定 D6、D9、D10。
> 页面与文案：设计简报 `patra-portal/docs/design-briefs/v0.14/discussion.md` §2.2 第 3 条、§2.3、§3.18 – §3.21、§4.6（画布第 6 版）。
> 接口：后端 spec `docs/patra/specs/2026-10-27-interaction-notification-design.md` 第 9 节（已在 main，`6d96ac9d6`，已部署到 mini）。
> 依赖：PAP-124 门户讨论区（单楼视图、`authorView`、`NeutralAvatar`、`callInteraction` 的 `auth: "none"`、两条缓存规则）。
> 与 PAP-125（投稿经验）并行：共享文件 `query-keys.ts`、`query-client.ts` 只追加自己的小节。

## 1. 目的

被回复、被 @ 的人回到 Patra 时，在导航栏铃铛上看到未读数，点进提醒列表，一步落到那条评论（单楼视图并高亮）。这是门户第一处轮询，也是 v0.14 整版两个账号冒烟的收尾。

**目标**

1. 桌面顶栏、窄屏顶栏（`检索 · 铃铛 · 菜单`）、桌面用户菜单、移动全屏菜单账号块都有提醒入口，共用一个未读数。
2. 未读数每 60 秒取一次，带 `If-None-Match`；没变化时网关回 304，门户 Route Handler 原样把 304 交给浏览器，页面不重新渲染；切回页面立即取一次。
3. `/notifications`（回复我的）、`/notifications/mentions`（@ 我的）：每页 20 条、加载更多、单条已读、全部已读，点开走评论永久链接。
4. 换人、退出时提醒缓存清空；退出后入口消失；interaction 不可用时入口显示「暂不可用」，导航其余部分照常。
5. 两个账号的 Playwright 冒烟覆盖验证场景 1–4 与 D6 全部条目。

**不做**（release spec 第 3 节 C）：邮件提醒、实时推送、提醒偏好、按类型关闭、「A 等 3 人」聚合、删除提醒。界面上不出现「通知」「私信」「帖子」（`/notifications` 只是地址）。

**反目标**（技术上能跑也算失败）

- 未读数没变却每分钟让铃铛、导航、整页重新渲染。
- 一次在路上的轮询把刚点完已读的计数冲回旧值。
- 换账号后看到上一个人的未读数或提醒行。
- interaction 挂掉时导航栏报错、闪烁或连带搜索、菜单不可用。
- 点提醒时导航被已读请求挡住、要等请求回来才跳转。

## 2. 调用层

### 2.1 网关客户端

`GatewayRequestInit` 加 `ifNoneMatch?: string | null`（与 `gatewayStream` 同名同义）：有值时写成 `If-None-Match` 头，原样转给网关。只加这一个字段，不开放任意请求头。

### 2.2 服务端读写（`src/lib/interaction/notifications.ts`，`server-only`）

| 函数 | 网关 | 结果 |
|---|---|---|
| `fetchUnreadCounts(ifNoneMatch)` | `GET /patra-interaction/me/notifications/unread-count` | `{ kind: "ok", counts, etag }` / `{ kind: "not-modified", etag }` / `signed-out` / `unavailable` |
| `fetchNotifications(tab, cursor)` | `GET /me/notifications?tab=&cursor=` | `Read<NotificationPage>` |
| `markNotificationRead(id)` | `PUT /me/notifications/{id}/read` | `Read<UnreadCounts>`（404 是 `not-found`） |
| `markAllNotificationsRead()` | `PUT /me/notifications/read` | `Read<UnreadCounts>` |

- 都是 `auth: "required"`（没有会话不打网关，回 `signed-out`），超时用 `READ_TIMEOUT_MS`（5 秒）。
- `callInteraction` 把 304 当 `ok`（`< 400`），`readJson` 读到空体：`fetchUnreadCounts` 在解析响应体之前先看 `res.status === 304`，取 `res.headers.get("ETag")` 返回 `not-modified`。200 时用 zod 校验 `{ total, replies, mentions }`（非负整数），ETag 取响应头。
- 响应形状用 zod（`src/lib/interaction/notification-schema.ts`）：`Notification` 按后端交接 §1，`type` 为 `COMMENT_REPLY` / `POST_COMMENT` / `MENTION`，`subject.type` 为 `publication` / `post`，ID 是字符串。`actor` 复用 PAP-124 评论作者的名片 schema（同一个 `UserCard` 形状）。

### 2.3 门户 Route Handler（浏览器只打门户自己）

| 路由 | 说明 |
|---|---|
| `GET /api/me/notifications/unread-count` | 读浏览器的 `If-None-Match` 原样转发。命中：**304、无响应体**，带网关的 `ETag` 与 `Cache-Control: no-cache, private`；没命中：200 JSON，同样两个头。401 / 502 照 `readReply`。 |
| `GET /api/me/notifications?tab=replies\|mentions&cursor=` | `tab` 只认这两个值，其余 404 不打网关；`readReply` |
| `PUT /api/me/notifications/[id]/read` | `isSameOrigin`，`id` 用 `isEntityId` 校验；返回新的 `UnreadCounts`；404 照转（门户忽略） |
| `PUT /api/me/notifications/read` | `isSameOrigin`；返回新的 `UnreadCounts` |

- 未读数这一条**不走 `readReply`**：`readReply` 一律写 `Cache-Control: private, no-store`，而后端要求 200 与 304 都带 `no-cache, private`（304 上被改成 `no-store` 浏览器会丢掉缓存条目）。这条路由自己拼响应，`ETag` 来自网关、不自己算。
- 已读用 Route Handler + `fetch`，不用 Server Action：Server Action 在 App Router 的路由队列里排队，点一行时它会挡住紧接着的跳转；`fetch` 带 `keepalive: true`，跳转、甚至新标签页打开都不影响请求发完。
- 客户端 `fetch` 一律写成以 `/` 开头的模板字面量、和 `fetch(` 同一行（`tests/lib/gateway-boundary.test.ts` 按行匹配）。

## 3. 未读数与轮询

### 3.1 查询

```ts
queryKeys.notifications            // ["notifications"]：公共前缀，换人时整体清
queryKeys.notificationUnread       // ["notifications", "unread"]
queryKeys.notificationList(tab)    // ["notifications", "list", tab]
```

缓存里存 `{ counts: UnreadCounts; etag: string | null }`。`queryFn`：

1. 取缓存里上一次的 `etag`，有就带 `If-None-Match`，`fetch` 用 `cache: "no-store"`——浏览器不插手条件请求，JS 看到的就是服务器的真实状态码（304 不会被浏览器悄悄换成缓存里的 200）。
2. 304：**原样返回缓存里的那个对象**（同一个引用）。TanStack 的结构共享与按属性追踪重渲染都以引用为准，读未读数的组件不会重新渲染。
3. 200：返回新对象（`etag` 取响应头）。数没变但 ETag 丢了（第一次、或刚被已读覆盖过）时，结构共享仍会让 `counts` 保持旧引用。
4. 401：让 `currentUser` 失效（会话在页面开着时过期，导航栏随之换成「登录」，提醒缓存随身份变化被清）；本次查询按出错处理。列表与两个已读请求遇到 401 同样处理；提醒页在当前用户取回为「未登录」（`null`，不含取不到）时去 `/login?next=<当前页>`。
5. 其余非 2xx / 网络错误：抛错，查询进入 `error`。

### 3.2 谁在轮询

- **只有一个观察者带轮询**：`useUnreadCountsPolling()` 在 `TopNav` 里调一次，`enabled` = 已登录，`refetchInterval: 60_000`，`refetchIntervalInBackground: false`（标签页在后台时不轮询），`refetchOnWindowFocus: "always"`（全局默认是 `false`；v5 的 `focusManager` 只听 `visibilitychange`，切回标签页即触发，不管是否过期），`retry: false`（出错直接显示不可用，下一次轮询自动恢复，D9）。它不读返回值，所以 `TopNav` 本身不会随轮询重渲染。
- 铃铛、用户菜单项、移动菜单按钮、列表页标签上的小数字都用 `useUnreadCounts()`：同一个 key，`enabled` 同上，不轮询、不聚焦重取，只订阅缓存。这样多处同时挂载也只有一个 60 秒计时器、一路请求。
- 用户菜单、移动菜单打开时都在 `TopNav` 之内，不用额外处理。

### 3.3 呈现（简报 §3.18）

| 状态 | 条件 | 铃铛 | 菜单项 / 移动按钮 |
|---|---|---|---|
| `bell.anon` | 未登录 | 不渲染 | 不渲染 |
| `bell.pending` | 登录状态未定、或已登录但第一次未读数还没回来 | 只有铃铛 | 无数字 |
| `bell.none` / `some` / `many` | 有数据 | `total` 为 0 不挂徽标，1–99 写数字，> 99 写 `99+` | 同一个数；0 不写 |
| `bell.current` | `pathname` 是 `/notifications` 或其下 | `paper-200` 底、`fg-1`、`aria-current="page"` | — |
| `bell.unavailable` | 查询 `status === "error"` | 图标 `fg-4`、不挂徽标、`title="提醒暂不可用"`，仍可点 | `暂不可用`（`fg-3`） |

- 读屏名称：`提醒` / `提醒，3 条未读` / `提醒，99+ 条未读` / `提醒（暂不可用）`；数字变化不主动播报。
- 徽标数字变化时做一次 1.15 倍回弹（`motion-safe:`，`dur-fast`），第一次出现不弹。
- 已登录但登录状态还在确认时，桌面账号区整个是 `NavAccountPlaceholder`，铃铛随账号区一起出现，不单独占位；窄屏顶栏的铃铛在确认前不渲染（未登录本来就没有它，先占位再消失比晚出现更跳）。

### 3.4 位置（简报 §2.3）

- **桌面**：`NavAccount` 里 `文献库` 与头像之间，36px 圆形图标按钮，`Bell` 18px。`NavAccount` 整体在窄屏隐藏，所以这一个实例只在桌面出现。
- **窄屏顶栏**：`TopNav` 右侧，检索按钮与菜单按钮之间另放一个实例，只在 `≤ 880px` 显示（`hidden max-[880px]:inline-flex`），间距沿用 6px。
- **桌面用户菜单**：`我的文献库` 与 `设置` 之间加 `提醒`（`Bell` 图标，右端未读数等宽 `fg-3`）。
- **移动全屏菜单账号块**：三个并排按钮改为 2×2：`主页`、`文献库` / `提醒`（按钮内右上角挂徽标）、`设置`。

## 4. 提醒列表页

### 4.1 路由与页面壳

- `src/app/notifications/layout.tsx`：`TopNav` + `main` + `Footer`，`metadata`：`title: "提醒 · Patra"`、`robots: { index: false }`。
- `src/app/notifications/page.tsx`（`replies`）、`src/app/notifications/mentions/page.tsx`（`mentions`）：`await requireUser(<自己的路径>)`（未登录去 `/login?next=`，冷静期账号去取消注销页），然后渲染客户端组件 `NotificationInbox tab=…`。
- **列表在客户端取，不做服务端首屏**（我的选择）：已读是乐观写、多处共用缓存，服务端首屏要走 PAP-124 的「首屏只作初值」那套，换来的只是少一次 6 行骨架。页面壳（h1、标签、按钮）是同步渲染的，骨架只在列表区。

### 4.2 结构（简报 §3.19）

- h1 `提醒`（衬线 `display-3`），右端 `全部标为已读`（次按钮小号；移动端缩成文字按钮 `全部已读`；`total` 为 0 或未读数未知时禁用）。
- 标签：`回复我的`（`/notifications`）、`@ 我的`（`/notifications/mentions`），是两个链接，样式同 `ProfileTabs`；当前标签 `aria-current="page"`；未读数 > 0 时标签名后挂小胶囊（同徽标，> 99 写 `99+`）。移动端两个标签各占一半。
- 列表最大宽 760px，文字最大 72ch，`ol` 语义。
- 每页 20 条（后端固定），`useInfiniteQuery` 按 `nextCursor` 翻页，底部 `加载更多提醒`；最后一页下面 `提醒保留 5 个月。`（`text-xs fg-3`）。
- 列表查询：`staleTime: 0`（每次进页面都按缓存先出、后台再取新的），`retry: false`，失败时 `refetchOnWindowFocus: "always"`（同 PAP-124 的 `refetchWhenFailed`）。不随未读数变化自动刷新列表（我的选择：避免用户正在看时行跳动；进页面、切回失败页、点重试都会重取）。
- 空：`回复我的` → `MessageSquare` 48px `fg-4` + `还没有人回复你` + `在文献页参与讨论、在期刊页分享投稿经验，别人的回复会出现在这里。`；`@ 我的` → `AtSign` + `还没有人 @ 你` + `别人在评论里 @ 你时，会出现在这里。`
- 加载：6 行骨架（`motion-safe:animate-pulse`）。失败：`InteractionUnavailable` `提醒暂时打不开` + `请稍后再试。` + `重试`（重取列表）。

### 4.3 一行（`NotificationRow`）

整行是链接（最小高 72px，内边距 16px 0）：

| 列 / 行 | 内容 |
|---|---|
| 圆点列 8px | 未读：`clay-500` 6px 实心圆点；已读：空 |
| 头像 36px（移动 32px） | `actorAnonymous` → `NeutralAvatar kind="anonymous"`；`actor` 为 null 或 `state === "DELETED"` → `NeutralAvatar kind="deleted"`；其余 `AuthorAvatar`（`authorView({ author: actor, anonymous: false, mine: false })`，主页不可见时用昵称与色号画默认头像） |
| 第一行 | `<名字>`（中粗 `fg-1`；已读 `fg-2`）+ 动作（`fg-2`）+ ` · ` + 相对时间（`fg-3`，`title` 完整时间）。名字：`匿名作者` / `已注销用户` / `displayName`。动作：`COMMENT_REPLY` 回复了你的评论、`POST_COMMENT` 评论了你的投稿经验、`MENTION` 在评论中 @ 了你 |
| 第二行 | `commentDeleted` → `该评论已删除`（`fg-3` 斜体）；否则 `excerpt`（纯文本，`excerptTruncated` 时末尾加 `…`），两行截断，`fg-2`；`excerpt` 为 null 且评论没删（对象已不可用）→ 不渲染这一行 |
| 第三行 | `text-xs fg-3` 一行截断。文献：`《title》的讨论`（`title` 为空时 `一篇文献的讨论`）；经验 `available: false` → `一条已删除的投稿经验`；经验 `mine` → `你的投稿经验 · <abbreviation ?? journal> · YYYY 年 M 月投稿`；别人的经验 → `<abbreviation ?? journal> 的一条投稿经验` |

- 底色：未读 `paper-50`，悬停 `bg-sunken`。
- **链接**：`subject.key` 为空 → 整行不是链接（`div`，点了只标已读）；经验 `available: false` → `/posts/{key}`；其余 `/papers/{key}?comment={commentId}` 或 `/posts/{key}?comment={commentId}`。评论被整条去掉时单楼视图回 404，PAP-124 的单楼视图显示「该评论已删除」（用户认可）；文献已下线时照样去文献页，由文献页显示它自己的状态。
- 用 `next/link`，不阻止默认行为：中键、⌘ 点击新开标签页照常可用。

### 4.4 已读

**单条**（点一行，未读时）：

1. `cancelQueries` 未读数与两个列表（PAP-124 规则 1：在路上的轮询会用旧数据冲掉乐观值）；还没有数据的列表（第一次加载）不取消，否则它回到空闲态、骨架一直转；
2. 乐观：所有已缓存列表里这条 `read = true`；未读数按类型减 1（`COMMENT_REPLY` / `POST_COMMENT` 减 `replies`，`MENTION` 减 `mentions`，`total` 同减，不低于 0），`etag` 置 null；
3. `fetch` `PUT /api/me/notifications/{id}/read`（`keepalive: true`），同时跳转（不等它）；
4. 返回 200：用返回的计数覆盖未读数，`etag` 置 null（下一次轮询不带条件头，拿回新的 ETag；不自己拼 ETag，不依赖后端的 ETag 格式）；
5. 404：忽略（提醒已被清理或不是自己的），未读数下一次轮询自然对上；其余失败：不回滚圆点（人已经跳走了），未读数让下一次轮询纠正。

已读的行再点只跳转，不发请求。

**全部已读**：

1. `cancelQueries` 同上；记下旧缓存；
2. 乐观：所有已缓存列表全部 `read = true`，未读数 `{0,0,0}`、`etag` null；
3. `PUT /api/me/notifications/read`；期间按钮禁用；
4. 成功：用返回的计数覆盖，toast `已全部标为已读`；
5. 失败：恢复第 1 步记下的缓存（圆点回来），页面顶部 `FormAlert` 提示条 `没有标记成功，请稍后再试`，不出 toast。

### 4.5 缓存规则（PAP-124 交接 §4、§5）

1. 写结果并进缓存前先 `cancelQueries`（上面两处）。
2. 首屏数据只作初值：本页不做服务端首屏，不涉及；如以后加，照 `DiscussionCache` 的做法，不用 `HydrationBoundary`。
3. `query-client.ts` 的身份变化订阅里追加一行 `client.removeQueries({ queryKey: queryKeys.notifications })`（换人、退出、注销、会话失效）。ETag 只由计数算出，换人后同计数的 304 也不会给错数据，清缓存是为了不让上一个人的提醒行和数字在下一个人面前停留哪怕一帧。

## 5. 降级（D9）

- 未读数取不到：铃铛 `bell.unavailable`，菜单项 `暂不可用`；导航栏其余部分（品牌、主导航、检索、头像菜单、移动菜单）不依赖这条查询，照常。查询出错不抛到渲染层（不用 `throwOnError`）。下一次轮询或切回页面成功后自动恢复。
- 列表页：`提醒暂时打不开` + `重试`；切回页面自动重取。
- D9 手测步骤（不进 Playwright，停 mini 容器会影响其他会话）：停 interaction 容器 → 任意页铃铛变灰、`title` 为「提醒暂不可用」、导航其他功能可用；进 `/notifications` 显示失败态 → 启动容器并等注册进 Nacos → 切到别的标签页再切回 → 铃铛与列表恢复，不用刷新。

## 6. 测试

### 6.1 单元（Vitest + Testing Library）

- `gatewayFetch`：`ifNoneMatch` 写成请求头，未给不写。
- `fetchUnreadCounts`：200 解析与 ETag；304 不解析体、返回 `not-modified`；401 / 5xx / 形状不对。
- unread-count Route Handler：转发 `If-None-Match`；304 无体且带 `ETag`、`Cache-Control: no-cache, private`；200 同两个头；未登录 401。其余三个路由：`tab` 校验、`isSameOrigin`、`isEntityId`。
- 轮询查询：第二次请求带上一次的 ETag；304 返回同一个引用，读未读数的组件渲染次数不变（计数器断言）；200 新数更新；401 让 `currentUser` 失效；聚焦（`visibilitychange`）立即重取；未登录不发请求。
- `query-client`：身份变化清掉 `notifications` 前缀。
- 铃铛：六个状态（含 `99+`、`aria-current`、不可用的 `title` 与名称）；桌面与窄屏两处位置；用户菜单项、移动菜单 2×2 与数字、`暂不可用`；退出后入口消失。
- 提醒行：三种动作、匿名作者、已注销用户、主页不可见、评论已删除、经验已删除、`mine` 与别人的经验、文献、`key` 为空不渲染链接、各类链接地址、`excerptTruncated` 的省略号。
- 列表：两个标签与胶囊、空、加载、失败与重试、加载更多与「提醒保留 5 个月。」、单条乐观已读与计数刷新、在路上的轮询不冲掉已读（`cancelQueries`）、全部已读成功 toast 与失败回滚提示条、按钮禁用。

### 6.2 Playwright（对 mini，`--workers=1`，桌面 1440 与手机 375）

两个**邮箱已验证**的账号 A、B（B 兼任验证场景里的 C），外加用例里现注册的一个未验证账号 X（主页对别人不可见的正常账号）：

1. **场景 1**：A 在一篇随机文献页发根评论（质疑样本量）；B 回复。
2. **场景 2**：A 在某刊期刊页写一条投稿经验（3 月投稿、首轮 6 周、大修后录用），汇总更新。**依赖 PAP-125**。
3. **场景 3**：B 在 A 的经验下追问并 @A。**依赖 PAP-125**。
4. **场景 4**：A 切回页面（派发 `visibilitychange`）或 60 秒内看到铃铛未读数；`回复我的` 里是场景 1 的回复与场景 3 的经验评论（B 在根评论里 @A 也只有一条，归「回复我的」）；点开进入单楼视图且目标高亮（`aria-current="true"`）；A 在单楼视图里回复；标已读后未读数归零。
5. **304**：未读数没变时，下一次请求 `/api/me/notifications/unread-count` 的响应是 304。
6. **D6 其余条目**：A 在自己的评论里 @ 自己不产生提醒；B 回复 A 的评论并 @A 只产生一条（`回复我的`）；B 编辑一条评论加上新的 @A（之前已 @ 过的人不再提醒）只对新增的人发提醒；B 的 @ 联想里搜 X 的用户名前缀看不到 X（主页不可见），完整输入 `@X用户名` 发表后 X 登录能在 `@ 我的` 看到；X 改用户名后，B 用旧名 @ 仍落到 X（X 的 `@ 我的` 多一条，正文显示新名）。
7. **退出**：A 退出后铃铛消失。

- A、B、X 各用一个独立的 browser context 同时登录。账号 B 的来源待用户定（环境变量名暂按 `PATRA_E2E_VERIFIED_B_EMAIL` / `PATRA_E2E_VERIFIED_B_PASSWORD`），没有 B 时整组跳过并写明原因。
- 场景 2、3 的用例先写好；PAP-125 合回前跳过（`test.skip` 写明原因），合回后跑最终一遍。
- **清理**：用例最后删掉自己发的评论与经验；提醒不能删，最后对 A、B、X 各点一次全部已读。A、B 是真实账号，提醒列表里会留下测试行（保留 5 个月后清理）——验收时向用户说明。X 每次新注册，残留在 mini（dev 数据可丢）。
- 场景 1 与第 6 条都发评论，两条评论间隔 ≥ 10 秒（`PUBLISH_GAP_MS`），用例按此排队。

## 7. 改动清单

| 文件 | 改动 |
|---|---|
| `src/lib/gateway.ts` | `GatewayRequestInit.ifNoneMatch` |
| `src/lib/interaction/notification-schema.ts`、`notifications.ts` | 新增 |
| `src/app/api/me/notifications/**/route.ts` | 新增四个 |
| `src/lib/notifications/*` | 查询 hook、已读 mutation、行的文案与链接（纯函数） |
| `src/lib/query-keys.ts` | 追加 `notifications` 小节 |
| `src/lib/query-client.ts` | 身份变化订阅追加一行 |
| `src/components/notifications/*` | `NotificationBell`、`UnreadBadge`、`NotificationInbox`、`NotificationRow`、骨架与空状态 |
| `src/components/portal/TopNav.tsx`、`NavAccount.tsx`、`UserMenu.tsx`、`MobileAccount.tsx` | 入口 |
| `src/app/notifications/**` | 页面 |
| `tests/**`、`tests/e2e/notifications.spec.ts` | 测试 |
| 共享笔记 `portal-PAP-126-handoff.md` | 入口与轮询的做法 |

## 8. 留给实现者 / 我定的点

- **基线**：先 `git merge feat/v0.14-discussion-portal`（PAP-124 合回之后），直接复用 `authorView`、`NeutralAvatar`、`AuthorAvatar`、作者名片 schema、单楼视图；不在 main 上另补一份。
- `useUnreadCounts()` 用 `select` 只取 `counts`：外层 `{ counts, etag }` 在 ETag 变化时是新引用，但结构共享保住了 `counts` 的引用，读数的组件不重渲染。
- 轮询条件：已登录且账号状态不是 `PENDING_DELETION`（冷静期账号会被 `PendingDeletionRedirect` 带走，不显示铃铛）；邮箱未验证的账号照常有铃铛（能被 @，后端对 `/me/**` 只要求登录）。
- `TopNav` 在各 layout 各渲染一份，跨 layout 导航会卸载重挂轮询观察者：未读数沿用全局 `staleTime` 60 秒，重挂时不过期就不重取，计时器重新计 60 秒，可以接受，不把轮询提到根 Provider。
- 经验类提醒的链接（`/posts/{key}?comment=`、`/posts/{key}`）按后端交接与 PAP-124 交接定，单测断言地址；端到端跳转等 PAP-125 合回后由场景 3、4 覆盖。

- 列表客户端取、不做服务端首屏（4.1）。
- 已读用 Route Handler + `keepalive` 而非 Server Action（2.3）。
- 客户端自己管 ETag、`cache: "no-store"`，不依赖浏览器 HTTP 缓存（3.1）。
- 已读后 `etag` 置 null 而不是自己拼（4.4）。
- 401 让 `currentUser` 失效（3.1）。
- 列表不随未读数变化自动刷新（4.2）。
- 窄屏铃铛在登录状态确认前不渲染（3.3）。
- 文献标题为空时 `一篇文献的讨论`（4.3，简报没写）。
