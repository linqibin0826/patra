# 门户提醒改接通知服务、「有帮助」标签与合并条目（PAP-147）实施计划

> **For agentic workers:** 本计划在当前会话内联执行（superpowers:executing-plans），严格 TDD。步骤用 `- [ ]` 跟踪。整个 Issue 只提交一次（Task 7）。

**Goal:** 门户提醒四个接口改调 `/patra-notification`，加「有帮助」标签与合并条目，红点只数回复与 @。

**Architecture:** 调用层与 schema 从 `lib/interaction/notification*` 搬到 `lib/notification/inbox*`（复用 PAP-148 的 `callNotification` 与 interaction 的 `toRead`）；界面层 `lib/notifications/*`（行文案、分组、已读、查询）与 `components/notifications/*` 按新 schema 扩展第三个标签与有帮助类型。

**Tech Stack:** Next 16 App Router、React 19、TanStack Query v5、zod 4、Vitest + Testing Library、Biome。

**Spec:** `docs/patra/specs/2026-10-31-portal-inbox-notification-service-design.md`（下称 spec）；文案以设计简报 §3.1–§3.3 为准。

## Global Constraints

- 命令在 `patra-portal/` 下：`pnpm test <路径>`、`pnpm typecheck`、`pnpm lint`；`///` 中文注释；禁止 `any`。
- 有帮助行文案：`{名字} 觉得你的评论有帮助` / `{名字} 等 {N} 人觉得你的投稿经验有帮助`（N = `actorCount`，≥ 2 才写人数）；角标 `+{N−1}`。
- 空状态：`还没有收到「有帮助」` / `别人觉得你的评论或投稿经验有帮助时，同一条内容每天合成一条，出现在这里。`
- 不在门户按时间重排；铃铛与菜单只用 `total`。

## Review Focus

1. **同一条提醒在两页里各出现一次**（有帮助条目在翻页间挪了位置）：只显示一次，React key 不重复。→ Task 4 `useNotificationList` 拼页去重用例。
2. **有帮助点经验、没有摘录也没有 `experience`**（数据缺失）：正文行不出，不报错。→ Task 3 `bodyOf` 用例。
3. **`actorCount` 为 1 的有帮助条目与回复条目**：不出角标、不写「等 1 人」。→ Task 3、Task 5 用例。
4. **有帮助未读很多、回复与 @ 都是 0**：铃铛无徽标，「全部标为已读」可点。→ Task 5 用例。
5. **有帮助行的 `commentId` 为空却是文献对象**（不该出现，`COMMENT_HELPFUL` 必有）：链接退回对象本身，不拼 `?comment=null`。→ Task 3 `notificationHref` 用例。

---

### Task 1: 调用层与 schema 搬到通知服务

**Files:**
- Create: `patra-portal/src/lib/notification/inbox-schema.ts`、`patra-portal/src/lib/notification/inbox.ts`
- Delete: `patra-portal/src/lib/interaction/notification-schema.ts`、`patra-portal/src/lib/interaction/notifications.ts`、`patra-portal/tests/lib/interaction/notifications.test.ts`
- Modify: 所有 import（`src/app/api/me/notifications/**`、`src/lib/notifications/*`、`src/components/notifications/*`、`tests/**`、`tests/fixtures/notification.ts`）
- Test: `patra-portal/tests/lib/notification/inbox.test.ts`（由旧测试改写）、`patra-portal/tests/lib/notification/inbox-schema.test.ts`

**Interfaces:**
- Produces: 类型 `UnreadCounts { total; replies; mentions; helpful }`、`NotificationTab = "replies" | "mentions" | "helpful"`、`NotificationType`（五种）、`NotificationSubject`（+ `experience: NotificationExperience | null`）、`Notification`（`occurredAt`、`commentId: string | null`、`actorCount: number`）、`NotificationPage`；函数签名同旧版：`fetchUnreadCounts(ifNoneMatch: string | null): Promise<UnreadRead>`、`fetchNotifications(tab, cursor?): Promise<Read<NotificationPage>>`、`markNotificationRead(id): Promise<Read<UnreadCounts>>`、`markAllNotificationsRead(): Promise<Read<UnreadCounts>>`

- [ ] **Step 1: 写失败测试**：`inbox.test.ts`——四个接口地址都是 `http://gw.test/patra-notification/me/notifications…`、带 `Authorization`；`tab=helpful&cursor=c%2F1`（游标原样编码进查询串）；304 原样；契约示例行（PAP-144 第 2 节）解析成功；`inbox-schema.test.ts`——`commentId: null`、`experience: null`、`totalDays: null` 通过；缺 `actorCount`、`actorCount: 0`、缺 `helpful` 失败
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：schema 按 spec 第 4 节；`inbox.ts` 照旧版换 `callNotification`，游标用 `query`；删旧文件、改 import；fixture 改 `occurredAt`、`actorCount: 1`、`helpful`
- [ ] **Step 4: 跑 `pnpm test tests/lib tests/app/api/notification-routes.test.ts` 与 `pnpm typecheck` 确认通过**

### Task 2: Route Handler 放行 `tab=helpful`

**Files:** Modify `patra-portal/src/app/api/me/notifications/route.ts`（只换 import，schema 已含 helpful）；Test `patra-portal/tests/app/api/notification-routes.test.ts`

- [ ] **Step 1: 写失败测试**：`GET /api/me/notifications?tab=helpful` 打到网关 `/patra-notification/me/notifications?tab=helpful`；未读数路由打到 `/patra-notification/me/notifications/unread-count` 并原样转 ETag；已读两条打到 `/patra-notification/...`
- [ ] **Step 2–4: 失败 → 实现（Task 1 后多半已通过，若已通过即为守护）→ 通过**

### Task 3: 行文案与去处（`lib/notifications/row.ts`）

**Files:** Modify `patra-portal/src/lib/notifications/row.ts`；Test `patra-portal/tests/lib/notifications/row.test.ts`

**Interfaces:**
- Produces: `actionLabel(notification: Notification): string`（原来按 type，改为按整条）、`helpfulMore(notification): string | null`（`+N−1` 或 null）、`bodyOf(notification): { text: string; muted: boolean } | null`（替代 `excerptOf`：摘录或结果行；`muted` 为有帮助类型）、`tabOf(type): NotificationTab`（有帮助两类 → `helpful`）、`notificationHref`（`commentId` 为空不带参数）

- [ ] **Step 1: 写失败测试**：`actionLabel`——`COMMENT_HELPFUL` + `actorCount 1` → `觉得你的评论有帮助`；`POST_HELPFUL` + 2 → `等 2 人觉得你的投稿经验有帮助`；回复三类不变；`helpfulMore`——1 → null，3 → `+2`，回复类 → null；`bodyOf`——有帮助摘录截断加 `…` 且 `muted: true`；`POST_HELPFUL` 无摘录有 `experience {MAJOR_REVISION, ACCEPTED, 41}` → `首轮 41 天 · 大修 → 录用`；都没有 → null；回复类 `muted: false`；`notificationHref`——`POST_HELPFUL` → `/posts/{key}`；`COMMENT_HELPFUL` 在经验下 → `/posts/{key}?comment=9`；`commentId` 为空的文献 → `/papers/{key}`；`tabOf`
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：结果行调 `formatOutcomeLine(...).replace("  →  ", " → ")`
- [ ] **Step 4: 跑测试确认通过**

### Task 4: 已读、未读数与列表拼页（`mark-read.ts`、`queries.ts`、`groups.ts`）

**Files:** Modify `patra-portal/src/lib/notifications/mark-read.ts`、`queries.ts`、`groups.ts`；Test `patra-portal/tests/lib/notifications/mark-read.test.tsx`、`queries.test.tsx`、`groups.test.ts`

**Interfaces:**
- Produces: `useNotificationList(tab)` 返回的 `data.pages` 不变；新增导出 `flattenPages(pages: readonly NotificationPage[]): Notification[]`（拼页 + 按 ID 去重，组件用它）；`groupByDay` 改用 `occurredAt`

- [ ] **Step 1: 写失败测试**：单条已读 `POST_HELPFUL` → `helpful` 减 1、`total` 不变；`MENTION` → `mentions`、`total` 各减 1；全部已读乐观清零四个数、失败恢复；`flattenPages` 两页里同 ID 只留第一次；`groupByDay` 按 `occurredAt` 分组、组内保持传入顺序
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过**

### Task 5: 界面（标签、行、空状态、铃铛、新页）

**Files:**
- Modify: `patra-portal/src/components/notifications/NotificationInbox.tsx`、`NotificationRow.tsx`、`NotificationEmpty.tsx`
- Create: `patra-portal/src/app/notifications/helpful/page.tsx`
- Test: `patra-portal/tests/components/notifications/NotificationInbox.test.tsx`、`NotificationRow.test.tsx`、`NotificationBell.test.tsx`、`patra-portal/tests/app/notifications-page.test.tsx`

- [ ] **Step 1: 写失败测试**
  - Inbox：三个标签链接 `/notifications`、`/notifications/mentions`、`/notifications/helpful`，胶囊各用 `replies` / `mentions` / `helpful`；`tab="helpful"` 当前项；空列表出有帮助空状态文案；`{ total: 0, replies: 0, mentions: 0, helpful: 2 }` 时「全部标为已读」可点，点了清零；四个数都 0 时禁用；两页重复 ID 只渲染一行
  - Row：`POST_HELPFUL` + `actorCount 2` 的链接可及名以 `未读，` 开头、含 `等 2 人觉得你的投稿经验有帮助`；`+1` 角标存在且 `aria-hidden`；`actorCount 1` 无角标；有帮助正文 `text-fg-2`（未读也是）；无摘录经验出结果行；行尾 `<time dateTime>` 是 `occurredAt`
  - Bell：`helpful: 9`、`total: 0` → 无徽标、读屏名「提醒」
  - 页面：`/notifications/helpful` 守卫用这一页的路径
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：角标照 spec 5.2（`absolute -right-1.5 -bottom-1 h-4 min-w-4.5 rounded-full bg-paper-50 px-1 font-mono text-[10px]`，若守护测试不放行任意值则用最近的 token 字号并 ledger）；`ThumbsUp` 空状态
- [ ] **Step 4: 跑 `pnpm test tests/components/notifications tests/app/notifications-page.test.tsx tests/components/portal` 确认通过**

### Task 6: 视觉核对（本地数据桩）

- [ ] 数据桩加 `/patra-notification/me/notifications*` 四个接口（画板 `Inbox` 的示例数据：有帮助四条、回复五条、@ 两条），截 1440×900 / 375×812：有帮助默认、回复我的、有帮助空、加载中；与 `ref/d-inbox-*.png`、`ref/m-inbox-helpful.png` 对照，有差异回 Task 5 修

### Task 7: 收尾

- [ ] `pnpm lint && pnpm typecheck && pnpm test` 全绿
- [ ] 派独立评审子代理（`model: "opus"`，只给 spec 与 diff），处理意见后再跑全量
- [ ] spec 头部状态、实施记录；共享笔记 `PAP-147-portal-handover.md`
- [ ] 只暂存本 Issue 的路径，提交 `feat(portal): 提醒改接通知服务，新增有帮助标签与合并条目 (PAP-147)`（正文每行 ≤ 100 字符）
- [ ] 给编排会话发验收汇报
