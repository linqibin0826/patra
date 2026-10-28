# 门户站内提醒 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans（本会话内联执行，用户已定，不用 subagent-driven）。Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 导航铃铛与未读数（60 秒轮询 + 304）、提醒列表页与已读、换人清缓存、两个账号的 Playwright 冒烟（PAP-126）。

**Architecture:** 浏览器只打门户 Route Handler；未读数的 ETag 由客户端存在查询数据里、自己带 `If-None-Match`（`cache: "no-store"`），304 时原样返回缓存对象。只有 `TopNav` 里一个观察者轮询，其他入口只订阅。列表页客户端取，已读乐观写并先 `cancelQueries`。

**Tech Stack:** Next 16 App Router、React 19、TanStack Query v5、zod 4、Tailwind v4、Vitest + Testing Library、Playwright。

**Spec:** `docs/patra/specs/2026-10-28-portal-notification-design.md`

## Global Constraints

- 所有命令在 `patra-portal/` 下跑；worktree 先 `pnpm install`（不要 cd 出 worktree，用绝对路径）。
- 文案逐字按 spec §3.3、§4.2、§4.3、§4.4；界面不出现「通知」「私信」「帖子」。
- 客户端 `fetch` 以 `/` 开头的模板字面量、与 `fetch(` 同一行。
- 设计系统守护：`animate-pulse` 写 `motion-safe:`；`text-fg-4` 只用于占位符与 spec 写明处（铃铛不可用图标、空状态图标用 `text-fg-4` 前先跑 `tests/design-system/`，被拦就改 `text-fg-3`）；不写 `focus-visible:shadow-none`。
- 共享文件 `query-keys.ts`、`query-client.ts` 只追加 `notifications` 小节与一行清理。
- 不 commit；每个任务结束跑该任务测试 + `pnpm typecheck`。最后整体停在本地等用户验收。
- 每个方法 / 组件写 `///` 中文注释，同现有代码。

## Review Focus

1. 已读请求与一次在路上的轮询竞争：轮询晚回来不能把计数冲回旧值 → Task 3 `mark-read cancels in-flight poll` 测试。
2. 已读后下一次轮询仍带旧 ETag，服务端 304 把旧数「确认」成新数 → Task 3 `after mark read the next poll sends no If-None-Match`。
3. 会话在页面开着时过期，轮询 401 → Task 3 `401 invalidates current user`。
4. 未读数 > 99 与 0 → Task 5 徽标 `99+`、0 不挂。
5. `subject` 为 null / `key` 为 null / `actor` 为 null 但不匿名 → Task 4 行函数测试。

---

### Task 1: 网关条件请求头与服务端读写

**Files:**
- Modify: `src/lib/gateway.ts`（`GatewayRequestInit` 加 `ifNoneMatch?: string | null`，`gatewayFetch` 有值时写 `If-None-Match`）
- Create: `src/lib/interaction/notification-schema.ts`、`src/lib/interaction/notifications.ts`
- Test: `tests/lib/gateway.test.ts`（追加）、`tests/lib/interaction/notifications.test.ts`、`tests/fixtures/notification.ts`

**Interfaces:**
- Produces（schema）：`unreadCountsSchema` / `UnreadCounts = { total: number; replies: number; mentions: number }`；`notificationTypeSchema = z.enum(["COMMENT_REPLY","POST_COMMENT","MENTION"])`；`notificationSubjectSchema`（`type: subjectTypeSchema, key: string|null, available, title|null, journal|null, abbreviation|null, submittedMonth|null, mine`）；`notificationSchema`（`notificationId, type, read, createdAt, actor: cardSchema.nullable(), actorAnonymous, subject: notificationSubjectSchema.nullable(), commentId, commentDeleted, excerpt: string|null, excerptTruncated`）；`notificationPageSchema = { items, nextCursor: string|null }`；`NotificationTab = "replies" | "mentions"`，`notificationTabSchema`。
- Produces（server-only）：
  - `fetchUnreadCounts(ifNoneMatch: string | null): Promise<UnreadRead>`，`UnreadRead = { kind: "ok"; counts: UnreadCounts; etag: string | null } | { kind: "not-modified"; etag: string | null } | { kind: "signed-out" } | { kind: "unavailable" }`
  - `fetchNotifications(tab: NotificationTab, cursor?: string): Promise<Read<NotificationPage>>`
  - `markNotificationRead(id: string): Promise<Read<UnreadCounts>>`
  - `markAllNotificationsRead(): Promise<Read<UnreadCounts>>`

- [ ] **Step 1: 写失败测试**（`notifications.test.ts`，mock `callInteraction` 同 `tests/lib/interaction/discussion.test.ts` 的做法；`gateway.test.ts` 用 `fetch` 桩）
  - `gatewayFetch sends If-None-Match when given`：请求头 `If-None-Match` 为 `"2-1"`；未给时没有这个头。
  - `fetchUnreadCounts returns counts and etag on 200`：→ `{ kind: "ok", counts: { total: 3, replies: 2, mentions: 1 }, etag: "\"2-1\"" }`，且 `callInteraction` 收到 `ifNoneMatch`。
  - `fetchUnreadCounts returns not-modified on 304 without parsing body`
  - `fetchUnreadCounts maps 401 to signed-out, 500 / bad shape / error to unavailable, no-session to signed-out`
  - `fetchNotifications calls /me/notifications?tab=mentions&cursor=c1`（cursor 编码）
  - `markNotificationRead PUTs /me/notifications/7/read and maps 404 to not-found`
  - `markAllNotificationsRead PUTs /me/notifications/read`
- [ ] **Step 2: 跑，确认失败** `pnpm vitest run tests/lib/gateway.test.ts tests/lib/interaction/notifications.test.ts`
- [ ] **Step 3: 实现**：都走 `callInteraction(path, { timeoutMs: READ_TIMEOUT_MS, … })`（`auth` 默认 required）与 `toRead`；`fetchUnreadCounts` 在 `toRead` 之前判 `reply.kind === "ok" && reply.res.status === 304`。fixture `notification(overrides)` 产出一条合法的 `Notification` JSON。
- [ ] **Step 4: 跑，确认通过** + `pnpm typecheck`

### Task 2: 门户 Route Handler

**Files:**
- Create: `src/app/api/me/notifications/unread-count/route.ts`、`src/app/api/me/notifications/route.ts`、`src/app/api/me/notifications/[id]/read/route.ts`、`src/app/api/me/notifications/read/route.ts`
- Test: `tests/app/api/notification-routes.test.ts`（照 `tests/app/api/discussion-routes.test.ts` mock `@/lib/interaction/notifications`）

**Interfaces:**
- Consumes: Task 1 的四个函数。
- Produces: `GET /api/me/notifications/unread-count`（200 `UnreadCounts` / 304 无体；两者都带 `ETag`（有值时）与 `Cache-Control: no-cache, private`；401 / 502 同 `readReply`）、`GET /api/me/notifications?tab&cursor`、`PUT /api/me/notifications/{id}/read`、`PUT /api/me/notifications/read`（后两者 200 `UnreadCounts`）。

- [ ] **Step 1: 写失败测试**
  - `unread-count forwards If-None-Match and returns 304 with no body`：`fetchUnreadCounts` 收到 `"\"2-1\""`；状态 304、`await res.text()` 为 `""`、`ETag` 为 `"2-1"`、`Cache-Control` 为 `no-cache, private`。
  - `unread-count returns 200 json with ETag and no-cache`
  - `unread-count without If-None-Match passes null`；`signed-out → 401`、`unavailable → 502`。
  - `list rejects unknown tab with 404 without calling interaction`；`list passes tab and cursor`。
  - `mark one rejects cross-site (403) and bad id (404)`；`mark one returns counts`；`mark one 404 passes through`。
  - `mark all rejects cross-site`；`mark all returns counts`。
- [ ] **Step 2: 跑，确认失败** `pnpm vitest run tests/app/api/notification-routes.test.ts`
- [ ] **Step 3: 实现**：unread-count 自己 `new NextResponse(null, { status: 304, headers })` / `NextResponse.json(counts, { headers })`，常量 `REVALIDATE = "no-cache, private"`；其余用 `readReply`、`isSameOrigin`、`forbidden`、`isEntityId`。都 `export const dynamic = "force-dynamic"`。
- [ ] **Step 4: 跑，确认通过** + `pnpm typecheck`

### Task 3: 查询 key、换人清缓存、未读数轮询与已读

**Files:**
- Modify: `src/lib/query-keys.ts`（追加小节）、`src/lib/query-client.ts`（身份变化订阅里追加 `client.removeQueries({ queryKey: queryKeys.notifications })`）
- Create: `src/lib/notifications/queries.ts`、`src/lib/notifications/mark-read.ts`
- Test: `tests/lib/query-client.test.ts`（追加）、`tests/lib/notifications/queries.test.tsx`、`tests/lib/notifications/mark-read.test.tsx`

**Interfaces:**
- Produces:
  - `queryKeys.notifications = ["notifications"]`、`queryKeys.notificationUnread = ["notifications","unread"]`、`queryKeys.notificationList = (tab: NotificationTab) => ["notifications","list",tab]`
  - `type UnreadData = { counts: UnreadCounts; etag: string | null }`
  - `useUnreadCountsPolling(): void`（`TopNav` 调一次）
  - `useUnreadCounts(): { counts: UnreadCounts | undefined; unavailable: boolean }`（`select: d => d.counts`）
  - `useNotificationList(tab)`（`useInfiniteQuery`，`staleTime: 0`、`retry: false`、失败时聚焦 `"always"`）
  - `useMarkRead(): (n: Notification) => void`（未读才发；乐观改所有列表 + 计数减 1，`etag: null`；`fetch` `keepalive: true`）
  - `useMarkAllRead(): { markAll: () => void; pending: boolean; failed: boolean }`
  - `enabled` 条件共用 `useNotificationsEnabled(): boolean` = 已登录且 `user.status !== "PENDING_DELETION"`

- [ ] **Step 1: 写失败测试**（`fetch` 用 `vi.stubGlobal`；渲染用 `renderDiscussion` 那套 QueryClient，但轮询测试的 client 要 `staleTime` 默认、`vi.useFakeTimers({ shouldAdvanceTime: true })`）
  - `query-client: identity change removes notifications`
  - `polling sends no If-None-Match first, then the previous ETag`；`fetch` 第一次 init `cache: "no-store"`。
  - `304 keeps the same data reference and does not re-render readers`：读者组件渲染计数在 304 前后相等。
  - `polls again after 60 seconds`；`refetches immediately on visibilitychange`（`focusManager.setFocused(true)` 或派发事件）。
  - `does not fetch when signed out or pending deletion`
  - `401 invalidates current user`；`500 marks unavailable`，下一次 200 恢复。
  - `mark read: optimistic read flag in both cached tabs and counts minus one by type`；`then counts from response, etag null`。
  - `mark read cancels in-flight poll`：轮询请求挂起 → 点已读 → 轮询晚回旧数 → 计数仍是返回值。
  - `after mark read the next poll sends no If-None-Match`
  - `mark read ignores 404`；`already read: no request`。
  - `mark all: optimistic zero; success toast 已全部标为已读; failure restores rows and counts, failed=true, no toast`
- [ ] **Step 2: 跑，确认失败** `pnpm vitest run tests/lib/query-client.test.ts tests/lib/notifications`
- [ ] **Step 3: 实现**：queryFn 里 `client.getQueryData<UnreadData>(queryKeys.notificationUnread)` 取上一次；304 返回它（没有就当出错）；401 时 `client.invalidateQueries({ queryKey: queryKeys.currentUser })` 后抛错。轮询观察者 `refetchInterval: 60_000`、`refetchOnWindowFocus: "always"`、`retry: false`。toast 用 `sonner` 的 `toast.success`。
- [ ] **Step 4: 跑，确认通过** + `pnpm typecheck`

### Task 4: 提醒行的纯函数

**Files:**
- Create: `src/lib/notifications/row.ts`
- Test: `tests/lib/notifications/row.test.ts`

**Interfaces:**
- Produces:
  - `actorOf(n): AuthorView`（`actorAnonymous` → `{kind:"anonymous", self:false}`，其余 `authorView({ author: n.actor, anonymous: false, mine: false })`）
  - `actorName(view: AuthorView): string`（`匿名作者` / `已注销用户` / `card.displayName`）
  - `actionLabel(type): string`（`回复了你的评论` / `评论了你的投稿经验` / `在评论中 @ 了你`）
  - `excerptOf(n): { kind: "deleted" } | { kind: "text"; text: string } | null`（截断时加 `…`）
  - `contextLabel(subject): string | null`（spec §4.3 第三行；`subject` 为 null → null；`submittedMonth "2026-03"` → `2026 年 3 月投稿`）
  - `notificationHref(n): string | null`
  - `tabOf(type): NotificationTab`（`MENTION` → mentions，其余 replies）

- [ ] **Step 1: 写失败测试**（逐条断言 spec 文案）
  - `actor: anonymous / null actor / DELETED / hidden profile / visible`
  - `excerpt: deleted wins; truncated gets …; null when subject unavailable`
  - `context: publication 《T》的讨论; publication without title 一篇文献的讨论; my post 你的投稿经验 · J Am Heart Assoc · 2026 年 3 月投稿; abbreviation null falls back to journal; others' post J Am Heart Assoc 的一条投稿经验; deleted post 一条已删除的投稿经验; null subject`
  - `href: /papers/1?comment=9; /posts/5?comment=9; deleted post /posts/5; null key → null; null subject → null`
- [ ] **Step 2: 跑，确认失败** `pnpm vitest run tests/lib/notifications/row.test.ts`
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑，确认通过**

### Task 5: 铃铛与导航入口

**Files:**
- Create: `src/components/notifications/NotificationBell.tsx`、`src/components/notifications/UnreadBadge.tsx`
- Modify: `src/components/portal/TopNav.tsx`（调 `useUnreadCountsPolling()`；检索按钮后、菜单前放窄屏铃铛 `className="hidden max-[880px]:inline-flex"`）、`NavAccount.tsx`（`文献库` 与 `UserMenu` 之间放铃铛）、`UserMenu.tsx`（`我的文献库` 后加 `提醒` 项）、`MobileAccount.tsx`（2×2）
- Test: `tests/components/notifications/NotificationBell.test.tsx`、追加 `tests/components/portal/TopNav.test.tsx`、`NavAccount.test.tsx`、`UserMenu.test.tsx`（如无则建）、`MobileAccount.test.tsx`

**Interfaces:**
- Consumes: Task 3 `useUnreadCounts`、`useNotificationsEnabled`、`useUnreadCountsPolling`。
- Produces: `NotificationBell({ className?: string })`（自己判登录，未登录返回 null）；`UnreadBadge({ count: number; className?: string })`（0 返回 null，> 99 写 `99+`，数字变化时加一次回弹类）；`formatUnread(count): string`（导出给菜单项用）。

- [ ] **Step 1: 写失败测试**
  - 铃铛：`signed out renders nothing`；`pending shows bell without badge, name 提醒`；`0 → no badge`；`3 → badge 3, name 提醒，3 条未读`；`120 → 99+, name 提醒，99+ 条未读`；`under /notifications/mentions aria-current=page`；`unavailable: name 提醒（暂不可用）, title 提醒暂不可用, no badge, still links to /notifications`。
  - TopNav：已登录时 `getAllByRole("link", { name: /^提醒/ })` 有两个（桌面、窄屏），窄屏那个在检索按钮之后、菜单按钮之前（DOM 顺序）；未登录没有。
  - UserMenu：打开后有 `提醒` 菜单项，右端 `3`；不可用写 `暂不可用`；0 不写数字。
  - MobileAccount：四个链接按顺序 `主页`、`文献库`、`提醒`、`设置`，`提醒` 内有徽标。
  - `logout removes bell`：currentUser 写成 null 后铃铛消失、`notifications` 缓存为空。
- [ ] **Step 2: 跑，确认失败** `pnpm vitest run tests/components/notifications tests/components/portal`
- [ ] **Step 3: 实现**：36px 圆形 `Link`，`Bell` 18px `strokeWidth={1.75}`；徽标 `clay-700` 底 `text-fg-on-clay`（确认 token 名，按 `globals.css` / `@theme` 里实际有的写）`text-3xs` 等宽中粗、`ring-2 ring-bg-canvas`、右上角外偏 2px；回弹用 `motion-safe:` 关键帧类（加在 `styles` 现有动画旁）。移动账号块 `LINK_BUTTON` 改成两行两列 grid。
- [ ] **Step 4: 跑，确认通过** + `pnpm typecheck` + `pnpm vitest run tests/design-system`

### Task 6: 提醒列表页

**Files:**
- Create: `src/app/notifications/layout.tsx`、`src/app/notifications/page.tsx`、`src/app/notifications/mentions/page.tsx`、`src/components/notifications/NotificationInbox.tsx`、`NotificationRow.tsx`、`NotificationSkeleton.tsx`、`NotificationEmpty.tsx`
- Test: `tests/components/notifications/NotificationInbox.test.tsx`、`NotificationRow.test.tsx`、`tests/app/notifications-page.test.tsx`

**Interfaces:**
- Consumes: Task 3 `useNotificationList`、`useUnreadCounts`、`useMarkRead`、`useMarkAllRead`；Task 4 全部；Task 5 `UnreadBadge`；PAP-124 `AuthorAvatar`、`useNow`；`InteractionUnavailable`、`FormAlert`。
- Produces: `NotificationInbox({ tab: NotificationTab })`。

- [ ] **Step 1: 写失败测试**
  - 页面：`requireUser("/notifications")` 与 `requireUser("/notifications/mentions")` 被调用；metadata `title: "提醒 · Patra"`、`robots.index: false`。
  - 行：未读有圆点、`paper-50` 底（断言 `data-unread`）；三层文案；整行是链接指向 `notificationHref`；`key` 为空时不是链接；点击调 `markRead`；`title` 为完整时间。
  - Inbox：标签 `回复我的` / `@ 我的`，当前项 `aria-current`，胶囊显示 `replies` / `mentions` 数；空状态两套文案；6 行骨架；失败 `提醒暂时打不开` + `请稍后再试。` + `重试` 重取；有下一页时 `加载更多提醒`，最后一页 `提醒保留 5 个月。`；`全部标为已读` 在 `total` 为 0 / 未知时禁用，失败显示 `没有标记成功，请稍后再试`；移动端文字 `全部已读`（两个按钮文字用响应式类切，断言可访问名称 `全部标为已读`）。
- [ ] **Step 2: 跑，确认失败** `pnpm vitest run tests/components/notifications tests/app/notifications-page.test.tsx`
- [ ] **Step 3: 实现**：版式按 spec §4.2、§4.3；标签样式抄 `ProfileTabs` 的 `tab` 类；列表 `ol`。
- [ ] **Step 4: 跑，确认通过** + `pnpm typecheck` + `pnpm vitest run tests/design-system`
- [ ] **Step 5: 本地起 dev（launch.json 从主检出拷，端口避开 4000）在 1440 与 375 下自查铃铛、菜单、列表页**

### Task 7: 账号 B 与两个账号的 Playwright 冒烟

**Files:**
- Create: `tests/e2e/notifications.spec.ts`、`tests/e2e/support/notifications.ts`
- 本机：`~/.patra/secrets/e2e.env` 追加 `PATRA_E2E_VERIFIED_B_EMAIL` / `PATRA_E2E_VERIFIED_B_PASSWORD`（权限 600，不进仓库）

- [ ] **Step 1: 账号 B**：通过门户在 mini 注册一个测试账号；在 mini 的 identity 库只对这一行执行 `UPDATE idn_user SET email_verified_at = now() WHERE email = '<B 邮箱>' AND email_verified_at IS NULL;`，确认影响 1 行；写进 e2e.env，`chmod 600`。汇报 SQL 与行数，不写凭据。
- [ ] **Step 2: 写用例**（spec §6.2 第 1、4、5、6、7 条；第 2、3 条写好并 `test.skip(!POST_PAGE_READY, "PAP-125 经验页还没合回")`）：A、B、X 各一个 browser context；复用 `pickPaper`、`openDiscussion`、`postRoot`、`replyTo`、`commentWith`、`PUBLISH_GAP_MS`、`login`、`register`；`waitForResponse` 断言 304；`page.evaluate(() => document.dispatchEvent(new Event("visibilitychange")))` 触发切回；末尾删评论、三个账号全部已读。两个视口各一遍。
- [ ] **Step 3: 跑** `set -a; . ~/.patra/secrets/e2e.env; set +a; PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528 pnpm test:e2e tests/e2e/notifications.spec.ts --workers=1`，全部通过（经验场景显示 skipped）。

### Task 8: 全量验证与交接

- [ ] **Step 1:** `pnpm lint && pnpm typecheck && pnpm test && pnpm build` 全过（build 前确认没有 dev 在用同一输出目录）。
- [ ] **Step 2:** 写共享笔记 `portal-PAP-126-handoff.md`：入口位置、`useUnreadCountsPolling` 只挂一处、ETag 存法与 304 引用不变、已读后 `etag` 置 null、`cancelQueries`、Route Handler 不走 `readReply` 的原因、e2e 跑法与账号 B、D9 手测步骤。
- [ ] **Step 3:** 停在本地，请用户在 1440 / 375 下验收；不 commit。
