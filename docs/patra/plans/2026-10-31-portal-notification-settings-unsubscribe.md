# 门户通知设置页、退订落地页与一键退订转发（PAP-148）实施计划

> **For agentic workers:** 本计划在当前会话内联执行（superpowers:executing-plans），严格 TDD。步骤用 `- [ ]` 跟踪。编排会话要求**整个 Issue 只提交一次**（Task 11），各任务不单独提交。

**Goal:** 门户新增设置 › 通知、免登录退订落地页、一键退订转发 Route Handler，按 PAP-142 契约调用通知服务。

**Architecture:** `src/lib/notification/` 是唯一请求 `/patra-notification` 的调用层（同 interaction 的 `request.ts` + schema + Server Action 分法）；设置页 RSC 读、Server Action 写、客户端表单按类排队保存；落地页是客户端状态机，读写都走 Server Action、不带会话令牌；`/unsubscribe/one-click` 是 Route Handler，POST 转发、GET 303 到确认页。

**Tech Stack:** Next 16 App Router（webpack）、React 19、TypeScript strict、Tailwind v4、zod 4、TanStack Query v5、Vitest + Testing Library、Biome。

**Spec:** `docs/patra/specs/2026-10-31-portal-notification-settings-unsubscribe-design.md`（下称 spec）；文案以设计简报 `patra-portal/docs/design-briefs/v0.16/notification.md` §3.5、§3.6 为准，spec 第 9 节是补的文案。

## Global Constraints

- 所有命令在 `patra-portal/` 下跑：`pnpm test <路径>`（单测）、`pnpm typecheck`、`pnpm lint`；Biome 格式化 `pnpm format`。
- 注释用 `///` 中文；代码标识符英文；禁止 `any`、`@ts-ignore`；测试查询优先 `getByRole`；不写快照测试。
- 网关只经 `src/lib/gateway.ts` 的 `gatewayFetch`（`tests/lib/gateway-boundary.test.ts` 守）；语义色用 `text-fg-3` 等标准类，不写 `[var(--x)]`（设计系统守护测试）。
- 令牌（43 个 base64url 字符）不进门户日志：日志里的 `endpoint` 一律不带查询串、不带请求体。
- 三个退订接口不带会话令牌（`auth: "none"`）；偏好接口要会话（`auth: "required"`）。
- 「已保存」显示 2 秒后淡出（`SAVED_MS = 2_000`，同隐私页）。
- 视觉对照 1440×900 与 375×812。

## Review Focus

1. **片段里有令牌但类别缺失或写错（`#token=…&category=reply`）**：直接无效屏、不请求后端。→ Task 3 `readUnsubscribeFragment` 用例 + Task 9 落地页用例。
2. **同一类快速连点三档、中间一次失败**：界面停在最后一次点的值，全部回来后只按最后一次的结果收尾，不闪回中间值。→ Task 6 用例 `only_the_latest_change_of_a_category_settles_it`。
3. **严格模式下 effect 跑两次**：片段已被第一次抹掉，第二次仍要拿到令牌、只发一次读请求。→ Task 8 hook 用例 + Task 9 落地页用 `StrictMode` 渲染的用例。
4. **一键退订的查询串带了别的参数或重复参数（`?token=a&token=b&category=REPLY&next=…`）**：只转第一个 `token`、`category`。→ Task 4 用例。
5. **网关挂了 / 超时时邮箱服务商的 POST**：门户回 502、不抛 500、日志里没有令牌。→ Task 4 用例。

---

### Task 1: 通知服务调用层与 schema

**Files:**
- Create: `patra-portal/src/lib/notification/request.ts`、`patra-portal/src/lib/notification/schema.ts`
- Test: `patra-portal/tests/lib/notification/request.test.ts`

**Interfaces:**
- Produces:
  - `type NotificationReply = IdentityReply`
  - `callNotification(path: string, init?: Omit<GatewayRequestInit, "token" | "client">, options?: { auth?: "required" | "none"; query?: URLSearchParams }): Promise<NotificationReply>`
  - `schema.ts`：`NOTIFICATION_MODES`、`PREFERENCE_CATEGORIES`、`LINK_CATEGORIES`、`UNSUBSCRIBE_CATEGORIES`（`as const` 元组）；类型 `NotificationMode`、`PreferenceCategory`、`LinkCategory`、`UnsubscribeCategory`、`NotificationPreferences`、`UnsubscribeLink`、`UnsubscribeResult`；zod `notificationModeSchema`、`preferencesSchema`、`unsubscribeLinkSchema`、`unsubscribeResultSchema`（字段见 spec 5.2）

- [ ] **Step 1: 写失败测试**（`// @vitest-environment node`，mock `next/headers` 为 `@tests/stubs/cookie-jar`，stub `fetch` 与 `PATRA_GATEWAY_BASE_URL=http://gw.test`，同 `tests/lib/interaction/request.test.ts`）
  - `sends_session_token_by_default`：有 Cookie `patra_session=patra_user_abc` → 请求 `http://gw.test/patra-notification/me/notification-preferences`，带 `Authorization: Bearer patra_user_abc`
  - `required_without_session_does_not_call_gateway`：无 Cookie → `{ kind: "no-session" }`，`fetch` 未调
  - `none_never_sends_authorization`：有 Cookie、`auth: "none"` → 请求头没有 `Authorization`
  - `appends_query_but_logs_path_only`：`query = new URLSearchParams({ token: "t".repeat(43), category: "REPLY" })`，fetch 抛 `TypeError` → 请求地址带查询串；`{ kind: "error" }`；`console.error` 的所有参数拼起来不含 `"t".repeat(43)`、含 `/patra-notification/unsubscribe`
  - `parses_problem`：返回 404 `{ code: "NOTIFICATION-0404", traits: ["NOT_FOUND", "UNSUBSCRIBE_LINK_INVALID"] }` → `kind: "problem"`，`traits` 含 `UNSUBSCRIBE_LINK_INVALID`
- [ ] **Step 2: 跑测试确认失败**：`pnpm test tests/lib/notification/request.test.ts` → FAIL（模块不存在）
- [ ] **Step 3: 实现**：照 `src/lib/interaction/request.ts`；`query` 非空时 `${endpoint}?${query}` 交给 `gatewayFetch`，`logRequestError` 只传 `endpoint`。schema 按 spec 5.2。
- [ ] **Step 4: 跑测试确认通过**

### Task 2: 偏好的读与保存

**Files:**
- Create: `patra-portal/src/lib/notification/preferences.ts`（server-only）、`patra-portal/src/lib/notification/preference-actions.ts`（`"use server"`）
- Test: `patra-portal/tests/lib/notification/preferences.test.ts`、`patra-portal/tests/lib/notification/preference-actions.test.ts`

**Interfaces:**
- Consumes: Task 1 的 `callNotification`、`preferencesSchema`
- Produces:
  - `fetchMyNotificationPreferences(): Promise<NotificationPreferences | "signed-out" | "unavailable">`
  - `type SaveModeResult = { ok: true; preferences: NotificationPreferences } | { ok: false; reason: "signed-out" | "unavailable" }`
  - `saveNotificationMode(category: PreferenceCategory, mode: NotificationMode): Promise<SaveModeResult>`

- [ ] **Step 1: 写失败测试**
  - 读：200 `{ reply: "DIGEST", mention: "INSTANT" }` → 原样；无 Cookie → `"signed-out"`、不请求；401 → `"signed-out"`；503 / 形状不对（`{ reply: "SOMETIMES" }`）/ fetch 抛错 → `"unavailable"` 且记日志；不抛错
  - 写：`saveNotificationMode("MENTION", "OFF")` → `PUT http://gw.test/patra-notification/me/notification-preferences/MENTION`，请求体 `{ mode: "OFF" }`，200 `{ reply: "INSTANT", mention: "OFF" }` → `{ ok: true, preferences }`；入参 `("HELPFUL", "OFF")` 或 `("REPLY", "LOUD")` → `unavailable`、不请求；401 → `signed-out` 且会话 Cookie 被清（cookie-jar 里没有了）；422 → `unavailable` 且 `console.error` 被调；无 Cookie → `signed-out`
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：读照 `toRead` 的分支但返回三值；写照 `savePrivacy`，失效会话用 `isSessionRejected` + `clearSessionCookie`，422 等用 `logUnexpectedResponse`
- [ ] **Step 4: 跑测试确认通过**

### Task 3: 退订的读写 Server Action 与片段解析

**Files:**
- Create: `patra-portal/src/lib/notification/unsubscribe-actions.ts`（`"use server"`）、`patra-portal/src/lib/notification/unsubscribe-link.ts`
- Test: `patra-portal/tests/lib/notification/unsubscribe-actions.test.ts`、`patra-portal/tests/lib/notification/unsubscribe-link.test.ts`

**Interfaces:**
- Consumes: Task 1；`readFragmentToken`（`src/lib/identity/email-token.ts`）
- Produces:
  - `type UnsubscribeFailure = { ok: false; reason: "invalid" | "unavailable" }`
  - `readUnsubscribeLink(token: string, category: LinkCategory): Promise<{ ok: true; link: UnsubscribeLink } | UnsubscribeFailure>`
  - `unsubscribe(token: string, category: UnsubscribeCategory): Promise<{ ok: true; result: UnsubscribeResult } | UnsubscribeFailure>`
  - `readUnsubscribeFragment(hash: string): { token: string; category: LinkCategory } | null`

- [ ] **Step 1: 写失败测试**
  - 读：`GET http://gw.test/patra-notification/unsubscribe?token=<T>&category=MENTION`，有 Cookie 也**没有** `Authorization`；200（spec 8.2 的示例体）→ `{ ok: true, link }`；404 + `UNSUBSCRIBE_LINK_INVALID` → `invalid`；404 无原因码 → `unavailable`；503 → `unavailable`；令牌 42 个字符或类别 `"ALL"` → `invalid`、不请求；`console.error` 的参数里没有令牌
  - 写：`POST http://gw.test/patra-notification/unsubscribe`，请求体 `{ token: T, category: "ALL" }`，无 `Authorization`；200（spec 8.3 示例）→ `{ ok: true, result }`；404 + 原因码 → `invalid`；形状不对 → `unavailable`
  - 片段：`#token=<T>&category=DIGEST` → `{ token: T, category: "DIGEST" }`；`#token=<T>`、`#token=<T>&category=reply`、`#token=<T>&category=ALL`、`#category=REPLY`、`""` → `null`
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：入参先用 `LINK_CATEGORIES` / `UNSUBSCRIBE_CATEGORIES` 与 43 字符正则校验（正则与 `email-token.ts` 的 `TOKEN_PATTERN` 相同：导出它复用）；读走 `query`，写走 `json`，都 `auth: "none"`
- [ ] **Step 4: 跑测试确认通过**

### Task 4: 一键退订 Route Handler、专注页名单、开发期日志

**Files:**
- Create: `patra-portal/src/app/unsubscribe/one-click/route.ts`
- Modify: `patra-portal/src/lib/auth/next-path.ts`（`FOCUS_PAGES` 加 `/unsubscribe`、`/unsubscribe/one-click`，注释同步）、`patra-portal/next.config.ts`（`logging.incomingRequests.ignore: [/\/unsubscribe\/one-click/]`）
- Test: `patra-portal/tests/app/unsubscribe-one-click-route.test.ts`、`patra-portal/tests/lib/auth/next-path.test.ts`（两个新页加进 `it.each`）、`patra-portal/tests/next-config.test.ts`（若它断言 `logging`，加一条）

**Interfaces:**
- Consumes: Task 1 `callNotification`
- Produces: `GET(request: NextRequest): Response`、`POST(request: NextRequest): Promise<Response>`

- [ ] **Step 1: 写失败测试**（node 环境，stub `fetch`）
  - POST `http://localhost:4000/unsubscribe/one-click?token=<T>&category=REPLY`，请求体 `List-Unsubscribe=One-Click`、无 `Origin` → 网关收到 `POST http://gw.test/patra-notification/unsubscribe/one-click?token=<T>&category=REPLY`、无 `Authorization`（即使有会话 Cookie）、`redirect: "manual"`；网关 200 → 门户 200、空响应体、`Cache-Control: no-store`
  - 网关 404 → 门户 404；网关 fetch 抛错 → 502，`console.error` 参数里没有令牌
  - `?token=a&token=b&category=REPLY&next=%2Fx` → 网关地址查询串恰为 `token=a&category=REPLY`
  - GET 同地址 → 303，`Location` 恰为 `/unsubscribe#token=<T>&category=REPLY`，`Referrer-Policy: no-referrer`，`fetch` 未调
  - GET 无参数 → 303 到 `/unsubscribe`
  - `next-path`：`safeNextPath("/unsubscribe")`、`safeNextPath("/unsubscribe/one-click?token=x")` 都是 `/`
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：参数用 `searchParams.get` 取第一个；POST 不读 `request.body`；GET 返回 `new Response(null, { status: 303, headers: { Location, "Referrer-Policy": "no-referrer", "Cache-Control": "no-store" } })`
- [ ] **Step 4: 跑测试确认通过**（含 `tests/proxy.test.ts` 不回归）

### Task 5: 设置共用组件与导航「通知」

**Files:**
- Create: `patra-portal/src/components/settings/RadioRow.tsx`、`patra-portal/src/components/settings/SaveStatus.tsx`
- Modify: `patra-portal/src/components/settings/privacy/PrivacyForm.tsx`（删掉内部两个组件，改 import）、`patra-portal/src/components/settings/SettingsNav.tsx`
- Test: `patra-portal/tests/components/settings/SettingsNav.test.tsx`（改）、`patra-portal/tests/components/settings/RadioRow.test.tsx`（新）、现有 `PrivacyForm.test.tsx` 保持绿

**Interfaces:**
- Produces:
  - `RadioRow({ name, title, description, checked, onSelect, badge? }: { name: string; title: string; description: string; checked: boolean; onSelect: () => void; badge?: string })`
  - `type SaveState = "saving" | "saved" | "failed"`；`SaveStatus({ status }: { status: SaveState | undefined })`

- [ ] **Step 1: 写失败测试**：`SettingsNav` 两套导航都是 5 项、最后一项 `["通知", "/settings/notifications"]` / 短名「通知」，`/settings/notifications` 时它 `aria-current="page"`；`RadioRow` 带 `badge="默认"` 时单选的可及名包含「离站时逐条 默认」、没有 badge 时不出
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：两个组件原样搬出（`badge` 是标题后 `ml-2 text-xs font-normal text-fg-3` 的 span）；`SettingsNav` 加一项、`grid-cols-5`、注释「五个分区」
- [ ] **Step 4: 跑 `pnpm test tests/components/settings` 确认全绿**

### Task 6: `NotificationSettingsForm`

**Files:**
- Create: `patra-portal/src/components/settings/notifications/NotificationSettingsForm.tsx`、`patra-portal/src/lib/notification/messages.ts`（三档、两类、有帮助、安全说明、落地页全部文案常量，照简报 §3.5 / §3.6 与 spec 第 9 节）
- Test: `patra-portal/tests/components/settings/notifications/NotificationSettingsForm.test.tsx`

**Interfaces:**
- Consumes: Task 2 `saveNotificationMode` / `SaveModeResult`；Task 5 `RadioRow`、`SaveStatus`；`useCommonFailureRedirect`
- Produces: `NotificationSettingsForm({ initial, email }: { initial: NotificationPreferences; email: string })`

- [ ] **Step 1: 写失败测试**（假定时器同 `PrivacyForm.test.tsx`；mock `@/lib/notification/preference-actions`）
  - 文案：两个 radiogroup `「回复我的」提醒邮件`、`「@ 我的」提醒邮件`，各三项，「离站时逐条」带「默认」；初始 `{ reply: "INSTANT", mention: "OFF" }` 时对应项选中；「提醒邮件发到 chen.yu@example.com」与「更改邮箱」链到 `/settings/account#email`；「只在站内」胶囊与有帮助说明；「账号安全邮件总会发送」
  - 点「@ 我的」的「每日汇总」→ `saveNotificationMode("MENTION", "DIGEST")`，这张卡「保存中…」→「已保存」，2.1 秒后消失；另一张卡无状态
  - 失败 → 这张卡回原值、「没有保存成功，请重试」
  - `only_the_latest_change_of_a_category_settles_it`：同一类点 DIGEST → OFF → INSTANT，第一个成功、第二个失败时仍「保存中…」且选中 INSTANT，第三个失败 → 回到第一个确认的 DIGEST
  - 两类排一条队：「回复我的」请求未回时点「@ 我的」，第二个请求在第一个回来之后才发出，两张卡各自收尾为「已保存」
  - 另一类的采用：「回复我的」保存成功、响应里 `mention: "OFF"`，「@ 我的」没有排队或在途改动 → 「@ 我的」显示为「关」；「@ 我的」有排队的改动时不被覆盖
  - `signed-out` → `router.replace` 到登录页（`next=%2Fsettings%2Fnotifications`）
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：一个 `queue` ref（Promise 链）；每类的 `latest` 序号与 `pending` 计数；成功 / 失败的收尾按 spec 8.3；版式照画板 `Notify`（spec 8.2）
- [ ] **Step 4: 跑测试确认通过**

### Task 7: 设置页、骨架与「暂时打不开」

**Files:**
- Create: `patra-portal/src/app/settings/notifications/page.tsx`、`patra-portal/src/app/settings/notifications/loading.tsx`、`patra-portal/src/components/settings/notifications/NotificationSettingsUnavailable.tsx`、`patra-portal/src/components/settings/notifications/NotificationSettingsSkeleton.tsx`、`patra-portal/src/components/settings/notifications/UnverifiedEmailNotice.tsx`
- Test: `patra-portal/tests/app/notification-settings-page.test.tsx`、`patra-portal/tests/components/settings/notifications/NotificationSettingsUnavailable.test.tsx`

**Interfaces:**
- Consumes: Task 2 `fetchMyNotificationPreferences`；Task 6 表单；`requireUser`；`ResendVerificationButton`
- Produces: 页面默认导出；`metadata.title = "通知 · 设置 · Patra"`

- [ ] **Step 1: 写失败测试**（照 `tests/app/settings-pages.test.tsx` / `security-page.test.tsx` 的 mock 方式）
  - 取到 → 渲染表单（radiogroup 存在）；`emailVerified: false` → 顶部「你的邮箱还没验证：验证之前，不论下面怎么设置，都不会给你发提醒邮件。」+「重新发送验证邮件」按钮
  - `"unavailable"` → 「通知设置暂时打不开」「请稍后再试。账号安全邮件不受影响。」，没有 radiogroup、没有「账号安全邮件总会发送」
  - `"signed-out"` → `redirect("/login?next=%2Fsettings%2Fnotifications")`
  - 卡片「重试」→ `router.refresh()` 被调一次，进行中按钮 `aria-disabled`
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：页面组装顺序按 spec 8.1；`loading.tsx` 渲染 `NotificationSettingsSkeleton`（两张卡：标题条、说明条、三行 40px 条，`aria-hidden`，外层 `role="status"` + sr-only「正在读取通知设置」）
- [ ] **Step 4: 跑测试确认通过**（含 `tests/app/route-structure.test.ts`）

### Task 8: 读片段 hook、`AuthShell` 标题可选

**Files:**
- Create: `patra-portal/src/lib/use-fragment-once.ts`
- Modify: `patra-portal/src/components/landing/TokenLandingPage.tsx`（改用 hook）、`patra-portal/src/components/auth/AuthShell.tsx`（`title?`、`description?`）
- Test: `patra-portal/tests/lib/use-fragment-once.test.tsx`、现有 `tests/components/landing/*.test.tsx` 保持绿、`tests/components/auth/AuthShell.test.tsx`（若无则新建）

**Interfaces:**
- Produces: `useFragmentOnce<T>(parse: (hash: string) => T | null): T | null | undefined`（`undefined` = 还没挂载；读到后立即 `replaceState` 抹掉片段，保留 `pathname + search`；严格模式下第二次 effect 返回第一次的结果）

- [ ] **Step 1: 写失败测试**：`StrictMode` 下渲染用 hook 的小组件，地址 `/x?y=1#token=<T>` → 拿到 `<T>`、`parse` 只被调一次、地址变成 `/x?y=1`；没有片段时返回 `parse("")` 的结果且不调 `replaceState`；`AuthShell` 不传 `title` 时没有 `h1`
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：把 `TokenLandingPage` 的 ref + effect 搬进 hook（返回 state）；`TokenLandingPage` 用 `const token = useFragmentOnce(readFragmentToken)` 推出阶段
- [ ] **Step 4: 跑 `pnpm test tests/components/landing tests/lib/use-fragment-once.test.tsx tests/components/auth` 确认全绿**

### Task 9: 退订落地页

**Files:**
- Create: `patra-portal/src/app/unsubscribe/page.tsx`、`patra-portal/src/components/landing/UnsubscribeLanding.tsx`、`patra-portal/src/lib/notification/unsubscribe-copy.ts`（spec 7.4 的文案计算，纯函数）
- Test: `patra-portal/tests/components/landing/UnsubscribeLanding.test.tsx`、`patra-portal/tests/lib/notification/unsubscribe-copy.test.ts`

**Interfaces:**
- Consumes: Task 3（两个 action、`readUnsubscribeFragment`）、Task 6 `messages.ts`、Task 8 hook 与 `AuthShell`、`useCurrentUser`、`authPageHref`
- Produces:
  - `kindLine(link: Pick<UnsubscribeLink, "category" | "reply" | "mention">): string`（信息表「邮件」）
  - `doneTitle(category: LinkCategory, already: boolean): string`
  - `doneDescription(category: LinkCategory, modes: NotificationPreferences, turnedOff: readonly PreferenceCategory[] | null): string`（`null` = 已经退订过，按此刻是 OFF 的类别写）

- [ ] **Step 1: 写失败测试（文案）**
  - `kindLine({ category: "MENTION", reply: "INSTANT", mention: "INSTANT" })` = `「@ 我的」提醒 · 离站时逐条`；`kindLine({ category: "REPLY", reply: "DIGEST", … })` = `「回复我的」提醒 · 每日汇总`；`kindLine({ category: "DIGEST", reply: "INSTANT", mention: "DIGEST" })` = `每日汇总 · 包括「@ 我的」`；两类都 DIGEST = `每日汇总 · 包括「回复我的」「@ 我的」`
  - `doneTitle("MENTION", false)` = `已退订「@ 我的」提醒邮件`；`doneTitle("DIGEST", false)` = `已退订每日汇总邮件`；`doneTitle("REPLY", true)` = `「回复我的」提醒邮件已经退订过了`
  - `doneDescription("DIGEST", { reply: "OFF", mention: "OFF" }, ["REPLY", "MENTION"])` = 简报「都改为不发邮件」句；`("DIGEST", { reply: "INSTANT", mention: "OFF" }, ["MENTION"])` = `「@ 我的」改为不发邮件，提醒页里照常能看到；「回复我的」仍是离站时逐条。`；`("DIGEST", { reply: "OFF", mention: "OFF" }, ["MENTION"])` = `「@ 我的」改为不发邮件，提醒页里照常能看到。`；`("DIGEST", { reply: "INSTANT", mention: "INSTANT" }, null)` = spec 第 9 节那句；`REPLY` / `MENTION` 是简报两句固定说明
- [ ] **Step 2: 写失败测试（页面，mock 两个 action 与 `useCurrentUser`）**
  - 打开 `#token=<T>&category=MENTION`：骨架（`role="status"`「正在读取退订链接」）→ 就绪「退订提醒邮件」、地址 `c***@example.com`、邮件 `「@ 我的」提醒 · 离站时逐条`、主按钮「退订这类邮件」有焦点、「关闭全部提醒邮件」；地址栏片段已抹掉；`readUnsubscribeLink` 调一次、`unsubscribe` 未调；`StrictMode` 下也只读一次
  - `othersEnabled: false` → 没有「关闭全部提醒邮件」
  - 类别 `DIGEST` → 主按钮「退订每日汇总」
  - 点主按钮：两个按钮 `aria-disabled`、主按钮「退订中…」；成功 `turnedOff: ["MENTION"]` → h1 `已退订「@ 我的」提醒邮件`、「同时关闭其余提醒邮件」、「登录后去通知设置」链到 `/login?next=%2Fsettings%2Fnotifications`、页脚「点错了？登录后在通知设置里改回来。」
  - 结果屏点「同时关闭其余提醒邮件」→ `unsubscribe(T, "ALL")` → h1「已关闭全部提醒邮件」
  - 点「关闭全部提醒邮件」→ `unsubscribe(T, "ALL")` → 「已关闭全部提醒邮件」
  - `turnedOff: []` → h1 `「@ 我的」提醒邮件已经退订过了`
  - 读到 `alreadyUnsubscribed: true` → 已经退订过屏，不出主按钮
  - 提交失败 `unavailable` → 回就绪、提示条「暂时没能退订，请稍后再试。」
  - 读 / 写 `invalid`、片段类别不对 → 「退订链接无效」，片段不对时不调读
  - 读 `unavailable` → spec 第 9 节「暂时打不开退订链接」+「重试」，点重试再读一次、成功进就绪
  - 已登录（`useCurrentUser` → authenticated）→ 「去通知设置」链到 `/settings/notifications`
- [ ] **Step 3: 跑测试确认失败**
- [ ] **Step 4: 实现**：状态机按 spec 7.3；版式照画板 `Unsub`（信息表 `dl` 两行、上粗下细线、`dt` 等宽小字；动作区 `mt-8 flex flex-col gap-3.5`；文字按钮居中；页脚 `mt-6 border-t border-border-subtle pt-4 text-sm text-fg-3`）；页面 `metadata` 按 spec 7.1
- [ ] **Step 5: 跑测试确认通过**

### Task 10: 视觉核对（本地数据桩）

**Files:**（都在 scratchpad，不进仓库）
- 数据桩网关：`<scratchpad>/stub-gateway.mjs`（Node `http`，端口 9599）：`/patra-identity/auth/me` 回 `tests/fixtures/user.ts` 的 `ME_RESPONSE`（`emailVerified` 由环境变量切）；`/patra-notification/me/notification-preferences` GET / PUT 按内存状态回；`/patra-notification/unsubscribe` GET / POST 按查询串里的场景回；提醒未读数等其余 `/me/**` 回空；其余 404
- 截图脚本：`<scratchpad>/shoot.mjs`（portal 自带 Playwright，带 Cookie `patra_session`）

- [ ] **Step 1**：起门户（本 worktree 的 `pnpm dev`，`PATRA_GATEWAY_BASE_URL=http://127.0.0.1:9599`，剥代理变量，端口 4010 避免与主检出的 4000 冲突）
- [ ] **Step 2**：截设置页（默认 / 汇总 / 退订后 / 两类都关 / 未验证 / 取不到 / 骨架）与落地页（就绪三类 / 已退订 / 已关闭全部 / 已经退订过 / 无效 / 提交失败 / 读失败），各 1440×900 与 375×812
- [ ] **Step 3**：与快照 `Notify.dc.html` / `Unsub.dc.html` 逐项对照（间距、字号、分隔线、胶囊、说明块、按钮宽度、窄屏 5 等分标签），有差异回到 Task 6 / 7 / 9 修

### Task 11: 收尾

- [ ] **Step 1**：`pnpm lint && pnpm typecheck && pnpm test`，全绿（vitest 随机超时先看 `uptime`，负载降下来再重跑）
- [ ] **Step 2**：派独立评审子代理（`model: "opus"`，只给 spec 路径与 `git diff` 范围），按 superpowers:receiving-code-review 处理意见，修完再跑 Step 1
- [ ] **Step 3**：spec 头部状态改「已实施，待联调」，第 11 节补实施偏离（如有）
- [ ] **Step 4**：只暂存本 Issue 的路径（`git diff --cached --stat` 核对），提交：

```bash
git add patra-portal/src patra-portal/tests patra-portal/next.config.ts docs/patra/specs/2026-10-31-portal-notification-settings-unsubscribe-design.md docs/patra/plans/2026-10-31-portal-notification-settings-unsubscribe.md
git commit -m "feat(portal): 新增通知设置页、退订落地页与一键退订转发 (PAP-148)"
```

- [ ] **Step 5**：给编排会话发验收汇报（提交 SHA、AC ↔ 测试、截图核对结论、遗留小问题、待 mini 部署后的 Playwright 冒烟）
