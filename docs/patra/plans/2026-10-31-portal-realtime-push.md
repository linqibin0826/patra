# 门户实时推送接入（PAP-149）实施计划

> **For agentic workers:** 本计划在当前会话内联执行（superpowers:executing-plans），严格 TDD。步骤用 `- [ ]` 跟踪。整个 Issue 只提交一次（Task 6）。

**Goal:** 未读数经 SSE 推到门户，可见时连、后台断、连上停轮询、断了回退、401 与登出停掉；开着提醒页时推送后刷新列表并让新条目淡出。

**Architecture:** 服务端：`gatewayStream` 带令牌，`/api/me/notifications/stream` 原样透传事件流。客户端：`useUnreadStream`（根布局常驻的 `<NotificationStream />` 里调一次）管理一个 `EventSource`，事件写进现有未读数缓存；连接状态与「新条目」各一个 Zustand store；轮询读连接状态。

**Tech Stack:** Next 16 Route Handler、React 19、TanStack Query v5、Zustand 5、zod 4、Vitest（jsdom，`EventSource` 用测试替身）。

**Spec:** `docs/patra/specs/2026-10-31-portal-realtime-push-design.md`（下称 spec）。

## Global Constraints

- 命令在 `patra-portal/` 下：`pnpm test <路径>`、`pnpm typecheck`、`pnpm lint`；`///` 中文注释；禁止 `any`。
- 网关地址、`Authorization` 只在 `lib/gateway.ts` 里出现（`gateway-boundary.test.ts` 守）；动画只在 `prefers-reduced-motion: no-preference` 里声明（`motion.test.ts` 守）。
- 推送事件名 `unread-count`，数据与 `unread-count` 接口同形；轮询间隔 `POLL_INTERVAL_MS = 60_000`；推送失败后重试间隔也是 60 秒；新条目淡出 2 秒。
- 机器负载高时 vitest 首个用例可能超过 5 秒：先 `uptime`，必要时命令行加 `--testTimeout=30000` 验证，不改配置。

## Review Focus

1. **严格模式下 effect 跑两次**：只留一个打开的 `EventSource`（第一次的被关掉）。→ Task 3 用 `StrictMode` 渲染的用例。
2. **推送数据形状不对（缺 `helpful`、不是 JSON）**：丢弃，不写缓存、不报错。→ Task 3 用例。
3. **连接关闭后的那次未读数请求是 401**：当前用户失效后不再重试推送（60 秒计时器被取消）。→ Task 3 用例。
4. **隐藏时有一个 60 秒重试计时器在等**：隐藏要取消它，切回时立即连，不是等计时器。→ Task 3 用例。
5. **浏览器在流中途断开**：上游请求被中止，不记错误日志。→ Task 2 用例。

---

### Task 1: `gatewayStream` 带令牌与 Accept

**Files:** Modify `patra-portal/src/lib/gateway.ts`；Test `patra-portal/tests/lib/gateway.test.ts`

**Interfaces:** Produces `GatewayStreamInit.token?: string`、`GatewayStreamInit.accept?: string`

- [ ] **Step 1: 写失败测试**：`gatewayStream("/x", { token: "t", accept: "text/event-stream" })` → 请求头 `Authorization: Bearer t`、`Accept: text/event-stream`；不传时都没有
- [ ] **Step 2: 跑测试确认失败** — **Step 3: 实现** — **Step 4: 通过**（含 `gateway-boundary.test.ts`）

### Task 2: SSE 透传 Route Handler

**Files:** Create `patra-portal/src/app/api/me/notifications/stream/route.ts`；Test `patra-portal/tests/app/api/notification-stream-route.test.ts`

**Interfaces:** Consumes Task 1；`readSessionToken`、`canSendToken`、`readClientOrigin`、`logRequestError`。Produces `GET(request: Request): Promise<Response>`

- [ ] **Step 1: 写失败测试**（node 环境，mock `next/headers` 为 cookie-jar，stub `fetch`）
  - 没有 Cookie → 401，`fetch` 未调
  - 有 Cookie → 网关 `GET http://gw.test/patra-notification/me/notifications/stream`，`Authorization: Bearer patra_user_abc`、`Accept: text/event-stream`，`signal` 是请求的 signal 派生（中止请求 → 上游 signal 也中止）
  - 上游 200 带一段 `ReadableStream`（`retry: 3000\nevent: unread-count\ndata: {…}\n\n`）→ 门户 200，响应体逐字相同，`Content-Type: text/event-stream`、`Cache-Control: no-cache, no-transform`、`X-Accel-Buffering: no`
  - 上游 503 / 401 → 原样状态码、空体
  - `fetch` 抛 `TypeError` → 502 并记日志；抛 `AbortError`（浏览器断开）→ 不记日志
- [ ] **Step 2: 跑测试确认失败** — **Step 3: 实现** — **Step 4: 通过**

### Task 3: 推送 hook 与连接状态

**Files:**
- Create: `patra-portal/src/store/notification-stream.ts`、`patra-portal/src/lib/notifications/stream.ts`
- Create: `patra-portal/src/components/notifications/NotificationStream.tsx`（渲染为空，调 `useUnreadStream()`）
- Modify: `patra-portal/src/lib/notifications/queries.ts`（轮询读 `live`）、`patra-portal/src/app/layout.tsx`（`QueryProvider` 里挂 `<NotificationStream />`）
- Test: `patra-portal/tests/lib/notifications/stream.test.tsx`、`patra-portal/tests/lib/notifications/queries.test.tsx`（补 `live` 用例）、`patra-portal/tests/components/portal/TopNav.test.tsx`（stub `EventSource`）

**Interfaces:**
- Produces: `useNotificationStreamStore`（`{ live: boolean; setLive(live: boolean): void }`）、`useUnreadStream(): void`、`STREAM_RETRY_MS = 60_000`、`STREAM_URL = "/api/me/notifications/stream"`

- [ ] **Step 1: 写失败测试**（`FakeEventSource` 测试替身：记录实例、`readyState`、`close()`，能派发 `open` / 命名事件 / `error`；`document.visibilityState` 用 `Object.defineProperty` 切换并派发 `visibilitychange`；假定时器）
  - 已登录且可见 → 建一个连到 `/api/me/notifications/stream`；未登录不建
  - `open` → `live` 为真；`unread-count` 事件 → 缓存 `notificationUnread` 为 `{ counts, etag: null }`
  - 数据不是 JSON 或缺 `helpful` → 缓存不变
  - `error` + `CONNECTING` → `live` 为假，连接不关
  - `error` + `CLOSED` → `live` 为假、请求了一次 `/api/me/notifications/unread-count`、60 秒后新建一个连接
  - 那次请求 401 → 当前用户失效（缓存被 invalidate 后取回 null）→ 不再新建连接
  - 隐藏 → `close()`、`live` 为假；可见 → 立刻新建（有等待中的重试时也取消它）
  - 当前用户变成 null（登出）→ `close()`
  - `StrictMode` 下只有一个未关闭的连接
  - 轮询：`live` 为真时 60 秒内不发未读数请求；变假后恢复
- [ ] **Step 2: 跑测试确认失败** — **Step 3: 实现**（hook 内 `useEffect([enabled])`：建连接、挂 `visibilitychange`；连接与计时器放 ref；清理时关连接、清计时器、`setLive(false)`） — **Step 4: 通过**

### Task 4: 推送后刷新列表与新条目

**Files:**
- Create: `patra-portal/src/store/fresh-notifications.ts`
- Modify: `patra-portal/src/lib/notifications/stream.ts`（事件处理里调 `refreshActiveLists(client)`）、`patra-portal/src/components/notifications/NotificationInbox.tsx`（把 `fresh` 传给行）、`patra-portal/src/components/notifications/NotificationRow.tsx`（`fresh?: boolean` → `anim-fresh`）、`patra-portal/src/app/globals.css`（`patra-fresh` keyframe 与 `.anim-fresh`）
- Test: `patra-portal/tests/lib/notifications/stream.test.tsx`、`patra-portal/tests/components/notifications/NotificationRow.test.tsx`、`patra-portal/tests/design-system/motion.test.ts`（应自动覆盖）

**Interfaces:**
- Produces: `refreshActiveLists(client: QueryClient): Promise<void>`（导出供测试；`refetchQueries` 活跃且有数据的列表，前后对比 `occurredAt` 记「新」）、`useFreshNotifications`（`{ ids: ReadonlySet<string>; mark(ids: string[]): void }`，`mark` 2 秒后清掉这批）、`FRESH_MS = 2_000`

- [ ] **Step 1: 写失败测试**：挂着 `useNotificationList("replies")` 且已有数据 → 推送后重取（请求 `/api/me/notifications?tab=replies`）；没有观察者的列表不重取；新 ID 与 `occurredAt` 变了且未读的进 `fresh`，2 秒后清空；重取前没有数据不记；请求失败列表照旧；行 `fresh` 时带 `anim-fresh`
- [ ] **Step 2: 跑测试确认失败** — **Step 3: 实现** — **Step 4: 通过**（含 `tests/design-system`）

### Task 5: 收尾前自查

- [ ] 本地数据桩加一个返回事件流的 `/patra-notification/me/notifications/stream`（立刻推当前值，之后每 10 秒推一次递增的数），在 4010 的开发服务器上打开提醒页，确认铃铛数字变化、新条目淡出、切到后台断开（浏览器网络面板 / 桩的连接日志）、桩返回 503 时回退轮询
- [ ] `pnpm lint && pnpm typecheck && pnpm test` 全绿

### Task 6: 评审与提交

- [ ] 派独立评审子代理（`model: "opus"`，只给 spec 与 diff），处理意见后再跑全量
- [ ] spec 状态与实施记录；共享笔记 `PAP-149-portal-handover.md`
- [ ] 只暂存本 Issue 的路径，提交 `feat(portal): 接入提醒实时推送 (PAP-149)`（正文每行 ≤ 100 字符）
- [ ] 给编排会话发验收汇报
