# 门户实时推送接入（PAP-149）工程 spec

> **Issue**：Linear PAP-149，v0.16 Notification，Milestone FE Ready；在 PAP-147（`dd202960f`）之后。
> **产品定义**：release spec `docs/patra/release-specs/v0.16-notification.md` 决策 H、I 与 2.1「后台与断线」。
> **页面**：设计简报 `patra-portal/docs/design-briefs/v0.16/notification.md` §3.4（推送没有新界面；开着提醒页时新条目浅陶土底 2 秒淡出）、§6（减弱动效不做底色）。
> **接口**：共享笔记 `PAP-145-sse-contract.md`（定稿版）；后端尚未部署，按契约写代码与单测，联调与 e2e 等 mini。
> **决策授权**：用户 10/31 授权子会话按最优方案拍板，「我定的」在第 9 节。
> **状态**：已实施，待联调（2026-10-31，计划 `docs/patra/plans/2026-10-31-portal-realtime-push.md`）；联调与 e2e 等后端部署到 mini；实施中的偏离见第 10 节
> **创建日期**：2026-10-31

---

## 1. 要做什么

铃铛、账号菜单、提醒页三个标签的未读数由服务端推过来，别人回复后几秒内变化，不用等 60 秒轮询；标签页切到后台就断开，切回来立即拿到当前值；推送连不上时照旧 60 秒轮询，恢复后自动接上；会话失效时停掉、按已登出处理。开着提醒页时，推送一到就刷新当前标签的列表，新条目浅底淡出。

## 2. 先讲清四个概念

### 2.1 SSE：服务器往浏览器单向推

**是什么**：Server-Sent Events。浏览器发一个普通的 GET，服务器不结束响应，有事就往里写一段 `event: …\ndata: …\n\n`。浏览器自带 `EventSource` 读它，断了会自己重连。

**业界怎么做**：要服务器推，常见三种：

| 做法 | 特点 | 谁在用 |
|---|---|---|
| 轮询 | 最简单；有延迟、空请求多 | Patra 现在（60 秒） |
| SSE | 单向、走普通 HTTP、自带重连 | GitHub 的通知计数、Mastodon 的流式接口 |
| WebSocket | 双向；要单独的协议升级，网关与代理都要支持 | 聊天、协同编辑 |

**我们的选择**：只推三个数字，单向就够，SSE（决策 I）。代价见 2.2、2.3。

### 2.2 `EventSource` 的两个限制，决定了门户要做什么

1. **不能加请求头**：没法自己带 `Authorization`。所以浏览器连门户自己的 `/api/me/notifications/stream`，门户的 Route Handler 从会话 Cookie 读令牌、带上 Bearer 再连网关（同门户其他接口）。
2. **看不到状态码**：连接失败只给一个 `error` 事件。收到非 200 时它不再重连（`readyState` 变成 `CLOSED`），但不告诉你是 401 还是 503。门户的做法：关掉之后立刻发一次普通的未读数请求，它若是 401，现有的轮询逻辑就会让当前用户失效（导航栏变成「登录」、推送和轮询都停）；不是 401 就照常轮询。

### 2.3 为什么切到后台要断开

tailnet 里直连门户走 HTTP/1.1，浏览器对同一站点最多开 6 条连接。每个开着的标签页都占一条长连接，开到第 6 个页面就没法再加载别的东西。所以页面不可见时关掉，切回来再连（连上第一条就是当前值）。

### 2.4 透传不能缓冲、不能压缩

压缩要攒够一块才发，事件会卡在门户里。上游带的 `Cache-Control: no-cache, no-transform` 原样回给浏览器：Next 的压缩与 Cloudflare 都按 `no-transform` 不压缩。浏览器断开时用 `request.signal` 中止上游请求，网关与通知服务才及时释放连接。

## 3. 范围

**做**：`gatewayStream` 带令牌；SSE 透传 Route Handler；客户端推送 hook（可见时连、隐藏时断、连上停轮询、断了回退、401、登出）；推送后刷新开着的提醒列表与新条目淡出；单测。

**不做**：e2e 与联调（mini 部署后；AC 第 1、3 条要在 tailnet 与公网两条路径各验一次，第 4 条的 e2e 也那时写）。

## 4. 服务端

### 4.1 `gatewayStream` 加 `token?: string`

有值时写 `Authorization: Bearer …`（只在 `lib/gateway.ts` 里写，守护测试不变）。另加 `accept?: string` 写 `Accept` 头。

### 4.2 Route Handler `src/app/api/me/notifications/stream/route.ts`（`GET`，`dynamic = "force-dynamic"`）

1. 读会话令牌；没有或放不进请求头 → 401，不请求网关。
2. `gatewayStream("/patra-notification/me/notifications/stream", { token, accept: "text/event-stream", signal: request.signal, client })`。等响应头的超时用默认 10 秒；之后的流不设超时（心跳 25 秒，undici 的块间空闲上限 300 秒）。
3. 上游 200：`new Response(upstream.body, { status: 200, headers })`，头为 `Content-Type: text/event-stream`、`Cache-Control: no-cache, no-transform`、`X-Accel-Buffering: no`。
4. 上游非 200：原样回状态码，空响应体（`EventSource` 只看状态码）。
5. 请求抛错（超时、网络、缺配置）：502；浏览器已经断开引起的中止不记日志。
6. 不读写别的 Cookie，不清会话 Cookie（401 由客户端的那次未读数请求处理，同现有路由）。

## 5. 客户端

### 5.1 连接状态 `src/store/notification-stream.ts`（Zustand）

`{ live: boolean; setLive(live) }`：推送连着为真。只给轮询读，不进 TanStack 缓存（不是服务端数据）。

### 5.2 轮询（`useUnreadCountsPolling`）

`refetchInterval: live ? false : 60_000`；`refetchOnWindowFocus` 照旧 `"always"`（TanStack 只在页面变为可见时触发它，那时推送已因隐藏断开、`live` 为假，按 `live` 关掉也不起作用）。

### 5.3 推送 hook `useUnreadStream()`（`src/lib/notifications/stream.ts`）

挂在根布局里常驻的 `<NotificationStream />`（`src/components/notifications/NotificationStream.tsx`，渲染为空，放在 `QueryProvider` 里）：TopNav 是各页面自己挂的，跨板块跳转会卸载重挂，挂在 TopNav 里每次跳转都会断开重连（网关重查会话、通知服务重建订阅）。轮询仍在 TopNav 里。

| 情况 | 做什么 |
|---|---|
| 已登录（`useNotificationsEnabled`）且页面可见 | `new EventSource("/api/me/notifications/stream")` |
| `open` | `live = true` |
| `unread-count` 事件 | 解析 `data`（`unreadCountsSchema`，不对就丢）→ 先取消在路上的未读数请求，再写进未读数缓存 `{ counts, etag: null }`；刷新开着的提醒列表（5.4） |
| `error` 且 `readyState === CONNECTING`（中途断开、30 分钟到点，浏览器 3 秒后自己重连） | `live = false`（重连期间轮询兜底） |
| `error` 且 `readyState === CLOSED`（拿到了非 200） | 关闭；`live = false`；立刻重取一次未读数（是 401 就会让当前用户失效）；60 秒后再试推送（页面仍可见时） |
| 页面变为隐藏 | 关闭，`live = false`，取消重试 |
| 页面变为可见 | 重新连接 |
| 登出、会话失效、冷静期（`useNotificationsEnabled` 变假） | 关闭，`live = false`，取消重试 |

浏览器不支持 `EventSource` 时什么都不做（只轮询）。

### 5.4 推送后刷新提醒页列表

- 每条 `unread-count` 事件（包括每次连上后的第一条：从后台切回来时列表不会自己刷新）都让**有观察者、已有数据、眼下没在请求**的提醒列表查询重取（`refetchQueries({ type: "active", predicate })`，就是开着的提醒页那一个标签；首次加载中、或用户刚点了「加载更多」的不打断，下一条推送会补上）。TanStack 对无限查询的重取会按新数据依次重取所有已加载的页，后几页不丢、也不漏条目（只换第一页的话，已加载两页以上时每来一条新条目，原第一页的最后一条就会从两页之间漏掉）。
- 推送里的数字与缓存相同也照样重取（契约：有帮助条目当天再被点、本来就未读时，数字不变也推一次）。
- 「新」条目：重取前记下列表里每条的 `occurredAt` 与其中最新的时间，重取后比这个时间还新、且 ID 不在其中或 `occurredAt` 变了且未读的（有帮助条目当天又有人点、挪到最上面，画板 `Inbox` push 场景也给它底色），记进 `src/store/fresh-notifications.ts`（Zustand，`ids: Set<string>`），2 秒后清掉。重取前列表没有数据时不记。
- 重取失败就算了（列表照旧显示，下一次推送或进页面会再取）。

### 5.5 新条目淡出

`NotificationRow` 加 `fresh?: boolean`：为真时加 `anim-fresh`（`globals.css`：`@keyframes patra-fresh { from { background-color: var(--clay-50) } to { background-color: transparent } }`，2 秒，只在 `prefers-reduced-motion: no-preference` 里声明）。减弱动效时没有底色。

## 6. 测试（对照 AC）

| AC | 测试 |
|---|---|
| 别人回复后 3 秒内铃铛变化 | `stream.test.tsx`：收到 `unread-count` 写进缓存，铃铛（`useUnreadCounts`）读到新数；`stream-route.test.ts`：透传事件流、头部、令牌。真实 3 秒在 mini 上测 |
| 切到后台关闭，切回立即刷新 | `stream.test.tsx`：`visibilitychange` 隐藏时 `close()`，可见时新建连接；连上的第一条事件写进缓存 |
| 停掉通知服务时退回轮询、页面不报错，恢复后自动接上 | `stream.test.tsx`：`CLOSED` 后 `live` 为假、立即请求一次未读数、60 秒后新建连接；`queries.test.tsx`：`live` 时不轮询、断开后恢复 60 秒轮询；路由：上游 503 原样回、网关抛错 502 |
| 401 与登出 | `stream.test.tsx`：`CLOSED` 后的未读数请求 401 → 当前用户失效 → 连接关闭、不再重试；当前用户变成 null 时关闭；路由：没有 Cookie 401 且不请求网关。e2e 在 mini 上补 |

另：`gateway.test.ts`（`gatewayStream` 带令牌与 `Accept`）；`fresh` 行带 `anim-fresh`；推送后开着的列表重取、没有观察者或还在首次加载的不取、新条目与挪上来的未读条目记为「新」；`motion.test.ts` 守住新 keyframe。

## 7. 交接

共享笔记 `PAP-149-portal-handover.md`：门户的推送地址、联调要测的三件事（3 秒内变化、停服务回退与恢复、401 / 登出）。

## 8. 风险与代价

- 推送连着时不再轮询：推送若「连着但不推」（上游卡住、心跳仍在），数字会停在旧值，直到下一次切回前台或 30 分钟到点重连。通知服务每次变化都推、30 分钟必重连，可以接受。
- 一个用户开多个标签页，每页一条连接（都可见时）。后台页不占连接。

## 9. 我定的

1. 401 靠「连接关闭后立刻发一次未读数请求」识别，复用现有的 401 → 当前用户失效逻辑（`EventSource` 看不到状态码）。
2. 拿到非 200 后每 60 秒（页面可见时）再试一次推送，切回前台也重试，满足「恢复后自动接上」。
3. 连接状态放 Zustand（客户端 UI 状态），不放 TanStack 缓存。
4. 推送后重取开着的列表的全部已加载页（TanStack 默认的无限查询重取），不只换第一页：只换第一页在已加载两页以上时会漏条目（builder check 发现）；代价是每次推送按已加载的页数发请求，通常只有一页。
5. 路由在 `/api/me/notifications/stream`（与其他提醒接口同一前缀）。
6. 推送挂在根布局常驻的组件里，不挂 TopNav（跨板块跳转不断线）。
7. 「新」条目包括当天又有人点、挪到最上面的有帮助条目（画板如此）；每次连上后的第一条事件也刷新列表（从后台切回来要看到新条目），首次加载不标「新」。

## 10. 实施记录（2026-10-31）

| # | 偏离或补充 | 原因 |
|---|---|---|
| 1 | builder check 后改了四处（已并进上文）：列表改为重取全部已加载页、「新」包括挪上来变回未读的条目、推送挂根布局、每次连上后的第一条也刷新列表 | 只换第一页会漏条目；画板给挪上来的有帮助条目也上了底色；TopNav 跨板块会重挂；从后台切回来列表要新 |
| 2 | 本地端到端自查：数据桩网关（每 5 秒推一条新回复）+ 真实 `next dev`：事件经 Route Handler 不缓冲（7 秒两条）；连着时 0 次轮询；隐藏断开、可见重连且铃铛更新；桩返回 503 时断开并回退一次请求、页面无报错；60 秒后自动接上；新条目底色中途为 `clay-50` 渐隐，减弱动效时透明，2.5 秒后类名清掉 | 第 6 节 AC 第 1–3 条在 mini 联调前的替代验证 |
| 3 | 独立评审（opus）后补：推送写缓存前取消在路上的未读数请求；正在请求（含「加载更多」）的列表不重取；「新」要比重取前最新一条还新；单条已读失败或 404、全部已读失败回滚后都把未读数标为过期重取一次 | 评审发现的竞态：轮询晚回来覆盖推送值；推送重取与翻页互相取消（翻页白点、或追加的旧页整页闪底）；推送连着时没有轮询来纠正乐观的减 1。各补一例测试，先红后绿 |
| 4 | 评审留下的小问题不改：断开后那次未读数请求沿用客户端默认的 1 次重试；上游 200 但不是事件流时没有改回 502；部署时上游被截断会记一条错误日志；同一条 2 秒内被标两次时第一批的计时器会提前清掉它 | 都不影响正确性，列出供后续处理 |
