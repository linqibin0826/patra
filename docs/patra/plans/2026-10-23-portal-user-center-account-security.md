# 门户用户中心：账号、安全、再认证、落地页（PAP-102）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> 本计划按用户约定在当前会话内联执行（superpowers:executing-plans），不派实现子代理。

**Goal:** 门户补齐用户中心的账号页、安全页、再认证、忘记密码、四个邮件落地页、取消注销页与冷静期跳转，并给整个用户中心加 Playwright 冒烟。

**Architecture:** 写操作沿用 Server Action（经 `callIdentity` + `settleFailure`），需要再认证的四个 Action 回 `reauth-required`，由客户端 `useReauth().withReauth()` 弹框后重试；登录设备列表走只读 Route Handler + TanStack Query；落地页是客户端状态机，读片段令牌、点按钮才 POST；冷静期跳转由 proxy（整页打开）+ 导航栏兜底 + `requireUser` 三处完成。

**Tech Stack:** Next.js 16.3 App Router（webpack）/ React 19 / TypeScript strict / Tailwind v4 / `@base-ui/react` 1.8 / TanStack Query 5 / zustand 5 / RHF 7 + Zod 4 / Vitest 4 + Testing Library / Playwright 1.63。不新增依赖。

**Spec:** `docs/patra/specs/2026-10-23-portal-user-center-account-security-design.md`（下称 spec）。页面、状态与文案：`patra-portal/docs/design-briefs/v0.12/user-center.md`（下称简报）。前置：`docs/patra/specs/2026-10-23-portal-user-center-profile-design.md`（PAP-101 spec）。画板：`docs/patra/design/snapshots/2026-10-22-user-center-hifi.zip` 解压到 scratchpad，用其 `harness/shoot.mjs` 离线截 `Account` / `Security` / `Landing` / `Main`（含 `-M`）对照。

## Global Constraints

- 命令都在 `patra-portal/` 下执行；单测 `pnpm exec vitest run <路径>`；收尾 `pnpm lint && pnpm typecheck && pnpm test && pnpm build`。
- TypeScript strict：禁止 `any`、`@ts-ignore`；别名 `@/` → `src/`、`@tests/` → `tests/`；禁止 `../../`；不写行内 `style`（用 Tailwind 类或 CSS 变量）。
- 每个函数 / 组件写 `///` 中文注释，与现有代码同风格；Biome 是唯一格式权威。
- 守护测试保持全绿：只有 `src/lib/gateway.ts` 直接 `fetch` 网关、写 `Authorization`；只有 `src/lib/auth/session-cookie.ts` 调 `cookies()`；颜色只用 `@theme` 已有 token 的标准类名，不写 `[var(--x)]`；自定义阴影、`fg-4` 正文、组件自带焦点环、非 `motion-safe:` 的位移过渡都会被 `tests/design-system/*` 拦下。
- 文案照简报原文；简报没有的只用 spec §15 列出的那几条。Action 与 Route Handler 的结果不带文案，只带 `reason` 与原因码，文案集中在 `src/lib/auth/messages.ts`。
- `traits` 一律按「包含」判断；会话失效 = 401 且 `traits` 不含 `PASSWORD_MISMATCH`；再认证 = 403 且 `traits` 含 `REAUTH_REQUIRED`。
- 时间一律 `Asia/Shanghai`（spec §11），只经 `src/lib/format-time.ts`。
- 断点 880px；移动输入框 17px、按钮 48px 高；尊重 `prefers-reduced-motion`。
- 提交：每个 Task 末尾提交一次，只 `git add` 本 Task 的文件（共享工作区，提交前 `git diff --cached --stat` 核对）；信息 `type(portal): 中文动词开头的主题 (PAP-102)`，主题不以大写字母或类名开头；末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。不 push、不开 PR。
- 改了 RSC 取数或 Server Action 写 Cookie 的 Task，Task 末尾跑一次 `pnpm build`（生产构建才暴露被吞的动态渲染信号）。

## Review Focus

1. 用户名确认框里粘贴带首尾空格或大写的用户名（` Chen-Yu `）：去空白、转小写后与当前用户名一致就允许提交——Task 11 加测试 `accepts_pasted_username_with_spaces_and_capitals`。
2. 落地页地址里的片段不是 `#token=…`，而是 `#token=abc&x=1`、`#TOKEN=…` 或令牌后带一个多余字符：只认 `token` 参数且恰好 43 个合法字符，否则无效屏、不打网关——Task 17 加测试 `rejects_token_with_trailing_character`、`ignores_other_fragment_params`。
3. 设备行的 `ipAddress` 为 `null`（老会话没记 IP）或 `deviceName` 为空串：第二行只显示属地、不出现孤零零的「 · 」；空串当「未知设备」——Task 14 加测试 `omits_separator_when_ip_missing`、`treats_blank_device_name_as_unknown`。
4. 冷静期跳转时当前地址带查询串与 `#`（`/papers?q=a#x`）：`next` 保留路径与查询串、丢掉片段，取消后回到 `/papers?q=a`——Task 3 加测试 `keeps_query_in_next_when_redirecting_pending_account`。
5. 待确认改邮箱的 `expiresAt` 在页面打开之后才到期：块在到期那一刻消失、`更改邮箱` 恢复可点，不需要刷新——Task 10 加测试 `hides_pending_block_when_it_expires_while_open`。

---

### Task 1: 时间格式化

**Files:**
- Create: `src/lib/format-time.ts`
- Test: `tests/lib/format-time.test.ts`

**Interfaces:**
- Produces（全部按 `Asia/Shanghai`，入参 ISO 字符串，`now` 为毫秒时间戳）：
  - `formatDate(iso: string): string` → `2026 年 11 月 19 日`
  - `formatDateTime(iso: string): string` → `2026-10-20 09:12`
  - `formatDateMinute(iso: string): string` → `2026 年 11 月 6 日 08:00`（取消注销页副文案）
  - `formatClock(iso: string, now: number): string` → 同一天 `15:42`，否则 `11 月 6 日 15:42`
  - `formatRelative(iso: string, now: number): string` → `刚刚` / `N 分钟前` / `N 小时前` / `昨天 HH:mm` / `N 天前` / `YYYY-MM-DD`
  - `addDays(now: number, days: number): string`（返回 ISO，给注销对话框算「今天 + 15 天」）

- [ ] **Step 1: 写失败测试**（`now = Date.parse("2026-10-23T08:00:00Z")`，北京时间 16:00）：
  - `formatDate("2026-11-19T08:00:00Z")` === `"2026 年 11 月 19 日"`；`formatDate("2026-11-18T16:30:00Z")` === `"2026 年 11 月 19 日"`（UTC 前一天、北京时间已是次日）
  - `formatDateTime("2026-10-20T01:12:00Z")` === `"2026-10-20 09:12"`；`formatDateMinute("2026-11-06T00:00:00Z")` === `"2026 年 11 月 6 日 08:00"`
  - `formatClock("2026-10-23T07:42:00Z", now)` === `"15:42"`；`formatClock("2026-10-23T16:30:00Z", now)` === `"10 月 24 日 00:30"`
  - `formatRelative`：`now - 30s` → `"刚刚"`；`now - 5min` → `"5 分钟前"`；`now - 2h` → `"2 小时前"`；`"2026-10-22T13:40:00Z"` → `"昨天 21:40"`；`now - 3d` → `"3 天前"`；`now - 7d` → `"7 天前"`；`now - 8d` → `"2026-10-15"`；未来时间（时钟偏差）→ `"刚刚"`
  - 「N 小时前」只在同一天内：`"2026-10-22T17:00:00Z"`（北京 23-10-23 01:00，同一天）→ `"7 小时前"`；`"2026-10-22T15:30:00Z"`（北京 10-22 23:30）→ `"昨天 23:30"`
- [ ] **Step 2: 跑测试确认失败**：`pnpm exec vitest run tests/lib/format-time.test.ts`
- [ ] **Step 3: 实现**：`Intl.DateTimeFormat("zh-CN", { timeZone: "Asia/Shanghai", ... }).formatToParts` 取年月日时分，自己拼串（不依赖 locale 的标点）；「同一天」「昨天」按北京时间的日历日比较。
- [ ] **Step 4: 跑测试确认通过**
- [ ] **Step 5: 提交**（连同 spec 与本计划）：`git add src/lib/format-time.ts tests/lib/format-time.test.ts ../docs/patra/specs/2026-10-23-portal-user-center-account-security-design.md ../docs/patra/plans/2026-10-23-portal-user-center-account-security.md`；`feat(portal): 按北京时间格式化日期与相对时间 (PAP-102)`

### Task 2: 专注页清单、设置页守卫与共同失败跳转

**Files:**
- Modify: `src/lib/auth/next-path.ts`、`src/lib/auth/require-user.ts`、`src/components/settings/privacy/PrivacyForm.tsx`、`src/components/settings/profile/ProfileForm.tsx`、`src/components/settings/profile/AvatarCard.tsx`、`src/components/portal/ResendVerificationButton.tsx`
- Create: `src/lib/auth/use-common-failure-redirect.ts`
- Test: `tests/lib/auth/next-path.test.ts`、`tests/lib/auth/require-user.test.ts`、`tests/lib/auth/use-common-failure-redirect.test.tsx`

**Interfaces:**
- Produces:
  - `FOCUS_PAGES: ReadonlySet<string>` = `/login`、`/register`、`/forgot-password`、`/verify-email`、`/reset-password`、`/confirm-email-change`、`/revert-email-change`、`/account/deletion-pending`
  - `isFocusPage(pathname: string): boolean`（去掉末尾 `/` 后查集合）
  - `safeNextPath` 拒绝全部 `FOCUS_PAGES`（原来只拒登录、注册）
  - `useCommonFailureRedirect(page: string): (reason: "signed-out" | "pending-deletion") => void`：`pending-deletion` → `router.replace(deletionPendingHref(page))`；`signed-out` → `setQueryData(currentUser, null)` + `router.replace(authPageHref("/login", page))`
  - `requireUser(path)`：用户 `status === "PENDING_DELETION"` → `redirect(deletionPendingHref(path))`

- [ ] **Step 1: 写失败测试**：`next-path` 加 `it.each` 八个专注页 → `safeNextPath` 返回 `"/"`，`isFocusPage("/account/deletion-pending/")` 为 true、`isFocusPage("/settings/account")` 为 false；`require-user` 加「冷静期用户 → `NEXT_REDIRECT:/account/deletion-pending?next=%2Fsettings%2Faccount`」；hook 测试两种 reason 各一条（mock `next/navigation`，断言 `replace` 参数与缓存被写成 `null`）。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**三处；PAP-101 的四个组件把各自的两段跳转换成 `useCommonFailureRedirect(PAGE)`（`ResendVerificationButton` 用当前路径 + 查询串作 `page`）。
- [ ] **Step 4: 跑这三个测试文件与四个组件的现有测试**：`pnpm exec vitest run tests/lib/auth tests/components/settings tests/components/portal/ResendVerificationButton.test.tsx`，全过。
- [ ] **Step 5: 提交**：`feat(portal): 专注页不作 next、设置页守卫拦冷静期账号 (PAP-102)`

### Task 3: proxy 冷静期跳转

**Files:**
- Modify: `src/proxy.ts`
- Test: `tests/proxy.test.ts`

**Interfaces:**
- Consumes: `isFocusPage`、`deletionPendingHref`（Task 2）；`lookupSessionByToken`、`readRequestSessionToken`、`clientOriginFromHeaders`（PAP-101）
- Produces: `config.matcher = ["/((?!api/|_next/|.*\\.[^/]+$).*)"]`（覆盖 `/`）

- [ ] **Step 1: 写失败测试**（`ME_RESPONSE` 改 `status: "PENDING_DELETION"` 作冷静期响应）：
  - `redirects_pending_account_on_document_get`：`/papers?q=a`、带 `patra_session` Cookie、`sec-fetch-dest: document` → 307，`Location` 为 `http://portal.test/account/deletion-pending?next=%2Fpapers%3Fq%3Da`
  - `keeps_query_in_next_when_redirecting_pending_account`（Review Focus 4）：`/papers?q=a#x` → `next` 为 `/papers?q=a`
  - `ACTIVE` 放行；无 Cookie 不请求网关；`sec-fetch-dest: empty` 不请求；`POST` 不请求；每个 `FOCUS_PAGES` 不触发冷静期查询；`/auth/me` 抛错或 500 → 放行
  - `/papers/42` 冷静期 → 307 到取消注销页（优先于 404 检查）；`ACTIVE` 时 404 检查照旧
  - `leaveAuthPage`：冷静期账号打开 `/login?next=%2Fjournals%2F7` → 307 到 `/account/deletion-pending?next=%2Fjournals%2F7`
  - `config.matcher`：把 matcher 去掉外层 `/(` `)` 后用 `new RegExp("^/" + 内部 + "$")` 断言：`/`、`/settings/account`、`/u/chen-yu` 命中；`/api/auth/me`、`/_next/static/x.js`、`/icon.svg` 不命中
- [ ] **Step 2: 跑测试确认失败**：`pnpm exec vitest run tests/proxy.test.ts`
- [ ] **Step 3: 实现**：`proxy()` 开头新增 `pendingDeletionRedirect(req): Promise<NextResponse | null>`；详情页分支里把冷静期查询与 HEAD 检查 `Promise.all` 并行，冷静期优先；`leaveAuthPage` 对 `status === "PENDING_DELETION"` 改送 `deletionPendingHref(next)`。
- [ ] **Step 4: 跑测试确认通过**
- [ ] **Step 5: 提交**：`feat(portal): 冷静期账号整页打开任何页面都去取消注销页 (PAP-102)`

### Task 4: 导航栏的冷静期兜底

**Files:**
- Create: `src/components/portal/PendingDeletionRedirect.tsx`
- Modify: `src/components/portal/TopNav.tsx`（挂在 `UnverifiedEmailBanner` 旁）
- Test: `tests/components/portal/PendingDeletionRedirect.test.tsx`

**Interfaces:**
- Produces: `PendingDeletionRedirect(): null`（`"use client"`，不渲染任何东西）

- [ ] **Step 1: 写失败测试**：缓存里是冷静期用户、`usePathname` `/papers`、`useSearchParams` `q=a` → `router.replace("/account/deletion-pending?next=%2Fpapers%3Fq%3Da")` 调一次；`ACTIVE`、未登录、`pending` 都不调。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：`useEffect` 里判断，依赖 `[status, pathname, query]`。`TopNav` 里因用了 `useSearchParams`，包一层 `<Suspense fallback={null}>`（与 `UnverifiedEmailBanner` 的做法一致，先看它怎么包）。
- [ ] **Step 4: 跑本测试与 `tests/components/portal/TopNav.test.tsx`**
- [ ] **Step 5: 提交**：`feat(portal): 导航栏读到冷静期账号就去取消注销页 (PAP-102)`

### Task 5: FormAlert 的语气与 AuthShell 不显示返回

**Files:**
- Modify: `src/components/auth/FormAlert.tsx`、`src/components/auth/AuthShell.tsx`
- Test: `tests/components/auth/FormAlert.test.tsx`（新）、`tests/components/auth/AuthShell.test.tsx`（新）

**Interfaces:**
- Produces: `FormAlert({ tone?: "danger" | "success" | "info", children })`；`AuthShell({ title, description, back: string | null, children })`

- [ ] **Step 1: 写失败测试**：默认 `role="alert"`；`tone="success"` 与 `"info"` 为 `role="status"`；`back={null}` 时没有名为「返回」的链接，`back="/"` 时有且 `href="/"`。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：success = `bg-status-success-bg` + `CircleCheck`（`text-moss-700`）；info = `bg-status-info-bg` + `Info`（`text-slate-700`，若无该 token 用 `text-fg-2`，以 `@theme` 为准）。
- [ ] **Step 4: 跑测试与 `tests/design-system`**
- [ ] **Step 5: 提交**：`feat(portal): 提示条加成功与信息语气，专注页外壳可不显示返回 (PAP-102)`

### Task 6: 取消注销页

**Files:**
- Create: `src/lib/identity/account-actions.ts`（本 Task 只放 `cancelDeletion`，Task 9 ~ 11 继续加）、`src/components/account/DeletionPendingPanel.tsx`、`src/app/account/deletion-pending/page.tsx`
- Modify: `src/lib/auth/messages.ts`（`TOAST_MESSAGES.deletionCancelled = "已取消注销，欢迎回来"`、`DELETION_PENDING_MESSAGES`）
- Test: `tests/lib/identity/account-actions.test.ts`、`tests/components/account/DeletionPendingPanel.test.tsx`、`tests/app/deletion-pending-page.test.tsx`

**Interfaces:**
- Consumes: `callIdentity`、`settleFailure`、`unexpectedProblem`、`userSchema`、`logout`（`@/lib/auth/actions`）、`formatDateMinute`
- Produces:
  - `cancelDeletion(): Promise<{ ok: true; user: CurrentUser } | CommonFailure>`（`DELETE /me/deletion`）
  - `DeletionPendingPanel({ next: string })`
  - 页面副文案 `账号将于 ${formatDateMinute(deletionDueAt)} 永久删除。`

- [ ] **Step 1: 写失败测试**：
  - Action：200 体为 `ME_RESPONSE`（`status: "ACTIVE"`）→ `{ ok: true, user }`，请求 `DELETE http://gw.test/patra-identity/me/deletion`；200 体缺字段 → `unavailable`；401 → `signed-out` 且清 Cookie；无 Cookie → `signed-out` 不请求。
  - 页面（mock `lookupSession` 与 `redirect`）：匿名 → `NEXT_REDIRECT:/login?next=%2Fpapers`（`searchParams.next = "/papers"`）；`ACTIVE` → `NEXT_REDIRECT:/papers`；冷静期 → 渲染 h1 `你的账号正在注销`、副文案 `账号将于 2026 年 11 月 6 日 08:00 永久删除。`（`deletionDueAt: "2026-11-06T00:00:00Z"`）、没有「返回」链接；`unavailable` → 抛错。
  - Panel：点 `取消注销，恢复账号` → 按钮 `恢复中…` → 成功后缓存写入 `user`、`toast.success("已取消注销，欢迎回来")`、`router.replace("/papers")`；失败 → `FormAlert` `没有取消成功，请稍后再试`；`signed-out` → 登录页；点 `退出登录` → 调 `logout` 后 `router.replace("/")`。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：页面 `robots: noindex`、标题 `取消注销 · Patra`；警示块 `bg-status-warning-bg`；布局照画板 `Landing` 的取消注销场景。
- [ ] **Step 4: 跑测试确认通过；`pnpm build`**
- [ ] **Step 5: 提交**：`feat(portal): 取消注销页 (PAP-102)`

### Task 7: 再认证 Action 与识别

**Files:**
- Create: `src/lib/identity/reauth-actions.ts`
- Modify: `src/lib/identity/mutation-failure.ts`
- Test: `tests/lib/identity/reauth-actions.test.ts`、`tests/lib/identity/mutation-failure.test.ts`

**Interfaces:**
- Produces:
  - `type ReauthRequired = { ok: false; reason: "reauth-required" }`；`isReauthRequired(problem: Problem): boolean`；`REAUTH_REQUIRED: ReauthRequired` 常量
  - `type ReauthResult = { ok: true; user: CurrentUser } | { ok: false; reason: "wrong-password" } | { ok: false; reason: "throttled"; retryAfterSeconds: number | null } | CommonFailure`
  - `reauthenticate(password: string): Promise<ReauthResult>`

- [ ] **Step 1: 写失败测试**：`isReauthRequired` 对 403 + `["FORBIDDEN","REAUTH_REQUIRED"]` 为 true、403 无该 trait 为 false；`reauthenticate`：200 `{ sessionToken: "patra_user_new", user }` → 写一条 `patra_session=patra_user_new; Path=/; Max-Age=2592000; HttpOnly; SameSite=lax`；200 `sessionToken: null` → `jar.written` 为空；请求体 `{ password }`、`POST .../auth/reauth`；401 `PASSWORD_MISMATCH` → `wrong-password` 且不清 Cookie；422 `password` → `wrong-password`；429 → `throttled`（`retryAfterSeconds: 900`）；401 `GW-0401` → `signed-out` 且清 Cookie；网关抛错 → `unavailable`。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过**
- [ ] **Step 5: 提交**：`feat(portal): 再认证 Action，令牌为空时保留现有 Cookie (PAP-102)`

### Task 8: 再认证对话框与 withReauth

**Files:**
- Create: `src/store/reauth.ts`、`src/lib/reauth/use-reauth.ts`、`src/components/reauth/ReauthDialog.tsx`、`src/components/reauth/ReauthProvider.tsx`
- Modify: `src/app/settings/layout.tsx`、`src/lib/auth/messages.ts`（`REAUTH_MESSAGES`）
- Test: `tests/store/reauth.test.ts`、`tests/lib/reauth/use-reauth.test.tsx`、`tests/components/reauth/ReauthDialog.test.tsx`

**Interfaces:**
- Consumes: `reauthenticate`、`ReauthRequired`（Task 7）、`Dialog`、`PasswordInput`、`FormField`、`FormAlert`
- Produces:
  - store：`useReauthStore` → `{ open: boolean; request(): Promise<boolean>; settle(ok: boolean): void }`（已有待决时 `request()` 返回同一个 promise）
  - `type Cancelled = { ok: false; reason: "cancelled" }`
  - `useReauth(): { withReauth<R>(call: () => Promise<R | ReauthRequired>): Promise<R | Cancelled> }`
  - `ReauthProvider({ children })`：渲染 `children` + `ReauthDialog`

- [ ] **Step 1: 写失败测试**：
  - store：两次 `request()` 返回同一个 promise；`settle(true)` 后两者都 resolve `true`、`open` 变 false。
  - `withReauth`：`call` 返回成功 → 原样，`call` 调 1 次；返回 `reauth-required` → `open` 变 true，`settle(true)` 后 `call` 再调一次并返回第二次的结果；`settle(false)` → `{ ok: false, reason: "cancelled" }`、`call` 只调 1 次。
  - 对话框（简报 §4.9 每行）：标题 `确认是你本人`；空提交 → `请输入密码`；`wrong-password` → `密码不正确`、输入框被清空且聚焦；`throttled` 900 秒 → 提示条 `尝试次数过多，请 15 分钟后再试`；`unavailable` → `没有验证成功，请稍后再试`；验证中按钮 `验证中…`；成功 → 缓存写入 `user`、`settle(true)`、无 toast；`signed-out` → `settle(false)`、缓存 `null`、`router.replace("/login?next=%2Fsettings%2Fsecurity")`；点 `取消` → `settle(false)`、无错误文案；`忘记密码？` 链接 `href="/forgot-password"`、`target="_blank"`、`rel` 含 `noopener`。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**；`ReauthProvider` 包进 `settings/layout.tsx` 的 `SettingsShell` 外层。
- [ ] **Step 4: 跑测试确认通过**
- [ ] **Step 5: 提交**：`feat(portal): 再认证对话框，验证后自动重试原操作 (PAP-102)`

### Task 9: 账号页与用户名卡片

**Files:**
- Create: `src/app/settings/account/page.tsx`、`src/components/settings/account/UsernameCard.tsx`
- Modify: `src/lib/identity/account-actions.ts`（加 `changeUsername`）、`src/lib/auth/messages.ts`（`USERNAME_MESSAGES` 加 `UNCHANGED: "新用户名和现在的一样"`；`ACCOUNT_MESSAGES`）
- Test: `tests/lib/identity/account-actions.test.ts`、`tests/components/settings/account/UsernameCard.test.tsx`、`tests/app/settings-pages.test.tsx`

**Interfaces:**
- Produces:
  - `type ChangeUsernameResult = { ok: true; username: string; nextUsernameChangeAt: string } | { ok: false; reason: "invalid"; code: string | null } | { ok: false; reason: "taken" | "cooldown" } | CommonFailure`
  - `changeUsername(username: string): Promise<ChangeUsernameResult>`（`PATCH /me/username`）
  - `UsernameCard({ user: CurrentUser; now: number })`
  - 页面：`AccountSettingsPage()`，本 Task 先只渲染 `UsernameCard`（Task 10、11 再加卡片），`metadata.title = "账号 · 设置 · Patra"`

- [ ] **Step 1: 写失败测试**：
  - Action：200 → `ok`；422 `username`/`UNCHANGED` → `invalid` + `code: "UNCHANGED"`；409 `errors: [{ field: "username", code: "ALREADY_TAKEN" }]` → `taken`；409 `traits` 含 `USERNAME_CHANGE_COOLDOWN` → `cooldown`；409 `PENDING_DELETION` → `pending-deletion`；请求 `PATCH`、体 `{ username }`。
  - 卡片：展示 `@chen-yu` 与 `/u/chen-yu` 链接（`target="_blank"`）；`nextUsernameChangeAt: "2026-11-19T08:00:00Z"`、`now` 早于它 → `修改` 禁用、`下次可修改：2026 年 11 月 19 日`；过去的时间 → 可点；编辑态预填并全选；输入当前名提交 → `新用户名和现在的一样` 且不调 Action；前端规则错误（`ab`）→ `用户名至少 3 个字符`；成功 → `toast.success("用户名已改为 @new-name")`、`invalidateQueries(currentUser)`、`router.refresh()`、回展示态；`taken` → `这个用户名已被使用`；`cooldown` → 卡片提示条 `30 天内只能修改一次，下次可修改：…` 且 `router.refresh()`；`unavailable` → `没有保存成功，请稍后再试`。
  - 页面：`requireUser` 用 `/settings/account`；未登录跳 `/login?next=%2Fsettings%2Faccount`。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：`UsernameCard` 首次渲染用 props 的 `now`，`useEffect` 挂载后换成 `Date.now()`；编辑态复用 `UsernameField`。
- [ ] **Step 4: 跑测试确认通过；`pnpm build`**
- [ ] **Step 5: 提交**：`feat(portal): 账号页改用户名与冷却提示 (PAP-102)`

### Task 10: 邮箱卡片与改邮箱

**Files:**
- Create: `src/lib/identity/account.ts`、`src/components/settings/account/EmailCard.tsx`
- Modify: `src/lib/identity/account-actions.ts`（加 `requestEmailChange`、`cancelEmailChange`）、`src/lib/auth/form-schemas.ts`（`emailChangeSchema(currentEmail)`）、`src/app/settings/account/page.tsx`、`src/lib/auth/messages.ts`
- Test: `tests/lib/identity/account.test.ts`、`tests/lib/identity/account-actions.test.ts`、`tests/components/settings/account/EmailCard.test.tsx`

**Interfaces:**
- Produces:
  - `type PendingEmailChange = { newEmail: string; expiresAt: string }`
  - `fetchPendingEmailChange(): Promise<PendingEmailChange | null | "signed-out">`（200 / 204 / 会话失效；其余抛错）
  - `type RequestEmailChangeResult = { ok: true; pending: PendingEmailChange } | { ok: false; reason: "invalid"; code: string | null } | { ok: false; reason: "taken" | "pending-exists" | "throttled" } | ReauthRequired | CommonFailure`
  - `requestEmailChange(newEmail: string)`（`POST /me/email/change`，体 `{ newEmail }`）；`cancelEmailChange(): Promise<{ ok: true } | CommonFailure>`（`DELETE`）
  - `EmailCard({ user: CurrentUser; pending: PendingEmailChange | null })`，根节点 `SettingsCard id="email"`

- [ ] **Step 1: 写失败测试**：
  - `fetchPendingEmailChange`：200 → 对象；204 → `null`；401 → `"signed-out"`；500 → 抛错。
  - Action：202 → `{ ok: true, pending }`；403 `REAUTH_REQUIRED` → `reauth-required`；409 `EMAIL_CHANGE_PENDING` → `pending-exists`；409 `errors[0]` `email`/`ALREADY_REGISTERED` → `taken`；422 `newEmail`/`UNCHANGED` → `invalid`；429 → `throttled`；`cancelEmailChange` 204 → `{ ok: true }`。
  - 卡片：已验证胶囊 `已验证`、未验证胶囊 `未验证` + `验证邮件已发到这个地址，24 小时内有效。` + `重新发送` 按钮；点 `更改邮箱` 展开；空 → `请输入邮箱`；同当前（大小写不同）→ `新邮箱和现在的一样`；提交经 `withReauth`（mock `useReauth` 直接调用 `call`）；成功 → 待确认块 `正在等待 new@example.com 确认 · 16:42 前有效`（`expiresAt: "2026-10-23T08:42:00Z"`）、`更改邮箱` 禁用；`cancelled` → 无任何错误文案；`pending-exists` → 提示条 + `router.refresh()`；`throttled` → `今天更改邮箱的次数已用完，请明天再试`；`taken` → `这个邮箱已被其他账号使用`；`取消更改` 成功 → 块消失、`toast.success("已取消更改邮箱")`；`hides_pending_block_when_it_expires_while_open`（Review Focus 5，假时钟推过 `expiresAt` 后块消失、按钮可点）。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：页面并行取 `fetchPendingEmailChange()`，`"signed-out"` 跳登录页；到期判定用一个只在有块时运行、到 `expiresAt` 触发一次的 `setTimeout`。状态胶囊先看主页 `仅你可见` 的写法，有就抽成 `src/components/ui/status-pill.tsx` 共用（`tone: "success" | "warning" | "accent" | "neutral"`），主页一并改用。
- [ ] **Step 4: 跑测试确认通过；`pnpm build`**
- [ ] **Step 5: 提交**：`feat(portal): 账号页邮箱状态、重发与改邮箱 (PAP-102)`

### Task 11: 注销卡片与对话框

**Files:**
- Create: `src/components/settings/account/DeleteAccountCard.tsx`、`src/components/settings/account/DeleteAccountDialog.tsx`
- Modify: `src/lib/identity/account-actions.ts`（加 `requestDeletion`、`endDeletedSession`）、`src/app/settings/account/page.tsx`、`src/lib/auth/messages.ts`
- Test: `tests/lib/identity/account-actions.test.ts`、`tests/components/settings/account/DeleteAccountDialog.test.tsx`

**Interfaces:**
- Produces:
  - `type RequestDeletionResult = { ok: true; deletionDueAt: string } | { ok: false; reason: "mismatch" } | ReauthRequired | CommonFailure`
  - `requestDeletion(username: string): Promise<RequestDeletionResult>`（`POST /me/deletion`，体 `{ username }`，**不碰 Cookie**）
  - `endDeletedSession(): Promise<never>`：`clearSessionCookie()` 后 `redirect("/")`
  - `DeleteAccountCard({ user: CurrentUser })`、`DeleteAccountDialog({ open, onOpenChange, user })`

- [ ] **Step 0: 实测两个模态叠放**（spec §14 第 1 条）：在 `tests/components/reauth/stacked-dialogs.test.tsx` 里同时渲染两个独立 `Dialog`（第二个后打开），断言第二个里的输入框能聚焦、键入、`Esc` 只关上层；jsdom 结论再在 Task 22 的真浏览器走查里复核。失败就改为在 `DeleteAccountDialog` 内部嵌套渲染再认证（`useReauth` 接口不变），在 spec §18 记一笔。
- [ ] **Step 1: 写失败测试**：
  - Action：200 → `{ ok: true, deletionDueAt }` 且 `jar.written` 为空；422 `username`/`MISMATCH` → `mismatch`；403 → `reauth-required`；409 `PENDING_DELETION` → `pending-deletion`；`endDeletedSession` 写清除 Cookie 并抛 `NEXT_REDIRECT`（`/`）。
  - 对话框：第 1 屏三条说明 + `到期日：2026 年 11 月 7 日`（`now = 2026-10-23T08:00:00Z` + 15 天）；`继续` → 第 2 屏 `请输入你的用户名 chen-yu 以确认。`；输入不一致时 `注销账号` 为 `aria-disabled`、离开字段显示 `和你的用户名不一致`；`accepts_pasted_username_with_spaces_and_capitals`（Review Focus 1，输入 ` Chen-Yu ` 后可提交，Action 收到 `chen-yu`）；提交中 `提交中…`；成功 → 第 3 屏 `注销申请已提交`，正文含 `2026 年 11 月 7 日` 与 `chen.yu@example.com`；`好的` → 缓存 `null` 并调 `endDeletedSession`；`mismatch` → 字段错误；`unavailable` → 提示条 `没有提交成功，请稍后再试`；`cancelled` → 停在第 2 屏、按钮可点；`返回` 回第 1 屏。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：第 3 屏关闭（`Esc`、遮罩）与 `好的` 同一处理；危险主按钮 `bg-rust-500 text-paper-50`，危险次按钮 `text-rust-500 border-rust-500/40 hover:bg-rust-50`（类名以 `@theme` 为准，抽成 `portal-ui.ts` 的 `btnDanger` / `btnDangerOutline`）。
- [ ] **Step 4: 跑测试确认通过；`pnpm build`**
- [ ] **Step 5: 提交**：`feat(portal): 注销账号两步确认与结果屏 (PAP-102)`

### Task 12: 安全页与改密码

**Files:**
- Create: `src/lib/identity/security-actions.ts`、`src/app/settings/security/page.tsx`、`src/components/settings/security/ChangePasswordCard.tsx`
- Modify: `src/lib/auth/form-schemas.ts`（`changePasswordSchema`）、`src/lib/auth/messages.ts`、`src/lib/query-keys.ts`（`sessions: ["sessions"] as const`）
- Test: `tests/lib/identity/security-actions.test.ts`、`tests/components/settings/security/ChangePasswordCard.test.tsx`、`tests/app/settings-pages.test.tsx`、`tests/lib/auth/form-schemas.test.ts`

**Interfaces:**
- Produces:
  - `type ChangePasswordResult = { ok: true; user: CurrentUser } | { ok: false; reason: "wrong-password" } | { ok: false; reason: "invalid-fields"; fieldErrors: FieldError[] } | { ok: false; reason: "throttled"; retryAfterSeconds: number | null } | CommonFailure`
  - `changePassword(input: { currentPassword: string; newPassword: string }): Promise<ChangePasswordResult>`（`PUT /me/password`，200 总是 `writeSessionCookie`）
  - `changePasswordSchema`：`currentPassword` 非空（`请输入当前密码`）、`newPassword` 同注册页、`confirmPassword` 非空（`请再输入一次新密码`）且相等（`两次输入的密码不一致`）
  - 页面 `SecuritySettingsPage()`，标题 `安全 · 设置 · Patra`

- [ ] **Step 1: 写失败测试**：
  - Action：200 写新 Cookie 并返回 `user`；401 `PASSWORD_MISMATCH` → `wrong-password`、不清 Cookie；422 → `invalid-fields`（`newPassword`/`UNCHANGED`）；429 → `throttled`；409 `PENDING_DELETION`。
  - 卡片（简报 §4.8 每行）：`忘记当前密码？` 链到 `/forgot-password`；`wrong-password` → 当前密码下方 `当前密码不正确` 且聚焦；`UNCHANGED` → `新密码不能和当前密码相同`；`TOO_COMMON` → 注册页同一句；`throttled` 900 → `尝试次数过多，请 15 分钟后再试。在此期间登录也会被限制。`；修改中 `修改中…`；成功 → 三个框清空、`toast.success("密码已修改，其他设备已退出登录")`、`invalidateQueries(sessions)`。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过；`pnpm build`**
- [ ] **Step 5: 提交**：`feat(portal): 安全页改密码 (PAP-102)`

### Task 13: 登录设备列表接口与查询

**Files:**
- Create: `src/lib/identity/session-list.ts`、`src/app/api/me/sessions/route.ts`、`src/lib/sessions-query.ts`
- Test: `tests/app/api/me-sessions.test.ts`、`tests/lib/sessions-query.test.tsx`

**Interfaces:**
- Produces:
  - `sessionSchema` / `sessionListSchema`（`{ sessions: SessionItem[] }`，`id` 接受数字或字符串并转成字符串，`deviceClass` 枚举四值，可空字段照 spec §3）；`type SessionItem`
  - `GET /api/me/sessions` → 200 `{ sessions }` / 401 `{ ok: false, reason: "signed-out" }` / 502 `{ ok: false, reason: "unavailable" }`，都带 `Cache-Control: private, no-store`
  - `useSessionsQuery(): UseQueryResult<SessionItem[], SessionsError>`，`SessionsError` 有 `signedOut: boolean`；`refetchOnWindowFocus: true`、`staleTime: 0`、`retry` 对 401 不重试

- [ ] **Step 1: 写失败测试**：路由——无 Cookie 401 且不请求网关；200 透传并把数字 `id` 变字符串；体不符 → 502 并记日志；401 → 401 且清 Cookie；网关抛错 → 502。hook——200 返回数组；401 → `error.signedOut === true`。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过**
- [ ] **Step 5: 提交**：`feat(portal): 登录设备列表的只读接口与查询 (PAP-102)`

### Task 14: 登录设备卡片与踢设备

**Files:**
- Create: `src/components/settings/security/SessionsCard.tsx`、`src/components/settings/security/SessionRow.tsx`
- Modify: `src/lib/identity/security-actions.ts`（加 `revokeSession`、`revokeOtherSessions`）、`src/app/settings/security/page.tsx`、`src/lib/auth/messages.ts`
- Test: `tests/lib/identity/security-actions.test.ts`、`tests/components/settings/security/SessionsCard.test.tsx`

**Interfaces:**
- Produces:
  - `type RevokeResult = { ok: true; revokedSessions: number } | ReauthRequired | CommonFailure`
  - `revokeSession(id: string): Promise<RevokeResult>`（`DELETE /me/sessions/{id}`，`id` 须匹配 `^\d{1,19}$`，否则 `unavailable` 不请求）；`revokeOtherSessions(): Promise<RevokeResult>`（`DELETE /me/sessions/others`）
  - `SessionsCard()`、`SessionRow({ session, now, busy, onRevoke })`

- [ ] **Step 1: 写失败测试**：
  - Action：200 `{ revokedSessions: 1 }`；403 → `reauth-required`；409 `CURRENT_SESSION` → `unavailable`；非数字 `id` 不请求。
  - 卡片：加载中三行骨架（`aria-busy`）；失败 → `设备列表加载失败` + `重试` 调 `refetch`；401 → 缓存 `null` + 登录页；本机行 `本机` 胶囊、`当前在线`、`退出登录` 按钮；其他行 `最后活跃 2 小时前`、`退出`；`privateNetwork` → `内网 · 192.168.97.1`；`ipRegion: null` → `属地未知 · 101.88.12.4`；`omits_separator_when_ip_missing`、`treats_blank_device_name_as_unknown`（Review Focus 3）；图标按 `deviceClass`（断言 `data-icon` 或 `svg` 的 `lucide-*` 类名）；踢单个经 `withReauth`，行内 `退出中…`，成功 → 行移除、`toast.success("已退出该设备")`；失败 → `toast.error("没有退出成功，请稍后再试")`；`cancelled` → 无提示；`退出其他所有设备` 只有本机时 `aria-disabled` 且 `title="没有其他设备登录着"`，并显示 `没有其他设备登录着。`；有其他设备时成功 → `toast.success("已退出其他 2 台设备")`、只剩本机；本机 `退出登录` 调 `useLogout` 的 `mutate`。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：淡出用 `motion-safe:transition-opacity duration-(--dur-normal)` + `data-leaving` 状态，`onTransitionEnd` 后从缓存移除；减少动态时直接移除。`now` 每 60 秒更新一次。
- [ ] **Step 4: 跑测试确认通过**
- [ ] **Step 5: 提交**：`feat(portal): 登录设备卡片，踢单个与其他全部 (PAP-102)`

### Task 15: 登录页的忘记密码入口与重设后提示

**Files:**
- Create: `src/store/auth-handoff.ts`
- Modify: `src/components/auth/LoginForm.tsx`
- Test: `tests/store/auth-handoff.test.ts`、`tests/components/auth/LoginForm.test.tsx`

**Interfaces:**
- Produces: `useAuthHandoffStore` → `{ email: string | null; notice: "password-reset" | null; put(v: { email: string | null; notice: "password-reset" | null }): void; take(): { email: string | null; notice: "password-reset" | null } }`（`take` 读完清空）

- [ ] **Step 1: 写失败测试**：store 的 `take` 读一次后再读为空；登录页密码标签行有 `忘记密码？` 链接，`href` 为 `/forgot-password?next=%2Fpapers`（`next="/papers"`），点击时把邮箱框去空白的值 `put` 进 store；store 里有 `{ email: "a@b.com", notice: "password-reset" }` 时挂载 → 邮箱预填、`role="status"` 的 `密码已重设，请用新密码登录`、焦点在密码框；没有时焦点在邮箱框（现有测试不变）。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：`take()` 在 `useState` 初始化函数里调用一次（严格模式下双调用也只取到一次：初始化函数里先读 `getState()` 再在 `useEffect` 里清空）。
- [ ] **Step 4: 跑测试确认通过**
- [ ] **Step 5: 提交**：`feat(portal): 登录页加忘记密码入口与重设后的成功提示 (PAP-102)`

### Task 16: 忘记密码页

**Files:**
- Create: `src/lib/identity/token-actions.ts`（本 Task 放 `requestPasswordReset`）、`src/components/auth/ForgotPasswordForm.tsx`、`src/app/forgot-password/page.tsx`
- Modify: `src/lib/auth/form-schemas.ts`（`forgotPasswordSchema`）、`src/lib/auth/messages.ts`
- Test: `tests/lib/identity/token-actions.test.ts`、`tests/components/auth/ForgotPasswordForm.test.tsx`

**Interfaces:**
- Produces:
  - `type ForgotResult = { ok: true; email: string; nextRequestAt: string } | { ok: false; reason: "invalid" } | { ok: false; reason: "throttled"; retryAfterSeconds: number | null } | { ok: false; reason: "unavailable" }`
  - `requestPasswordReset(email: string): Promise<ForgotResult>`（公开，`auth: "optional"`）
  - `ForgotPasswordForm({ next: string })`：自己渲染 `AuthShell`（标题随态切换），`back = authPageHref("/login", next)`

- [ ] **Step 1: 写失败测试**：Action 202 / 422 / 429 / 抛错；不带 Cookie 也请求。表单：handoff 预填并聚焦；空 → `请输入邮箱`；发送中 `发送中…`；成功 → h1 `查看你的邮箱`、正文 `如果 a@b.com 注册过 Patra，几分钟内会收到一封邮件。链接 1 小时内有效。`、`52 秒后可重新发送`（`nextRequestAt = now + 52s`）、到点变 `重新发送`；`换一个邮箱` → 回输入态且值保留；`throttled` 125 秒在输入态 → 提示条 `发送太频繁，请 3 分钟后再试`；`unavailable` → `没有发出去，请稍后再试`；`返回登录` 与「返回」都指向 `/login?next=%2Fpapers`。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：页面 `robots: noindex`、标题 `找回密码 · Patra`。
- [ ] **Step 4: 跑测试确认通过**
- [ ] **Step 5: 提交**：`feat(portal): 忘记密码页 (PAP-102)`

### Task 17: 落地页状态机

**Files:**
- Create: `src/lib/identity/email-token.ts`、`src/components/landing/TokenLandingPage.tsx`
- Test: `tests/lib/identity/email-token.test.ts`、`tests/components/landing/TokenLandingPage.test.tsx`

**Interfaces:**
- Produces:
  - `readFragmentToken(hash: string): string | null`（`URLSearchParams(hash.replace(/^#/, "")).get("token")`，须匹配 `^[A-Za-z0-9_-]{43}$`）
  - `type LandingOutcome<S, C = never> = { kind: "succeeded"; data: S } | { kind: "invalid" } | { kind: "conflict"; data: C } | { kind: "unavailable" } | { kind: "stay" }`；`stay` = 回到就绪态、不显示通用提示条（页面已自己显示了错误，如重设密码的字段错误与限流）
  - `TokenLandingPage<S, C>({ title, description, actionLabel, pendingLabel, danger?, children?, submit: (token: string) => Promise<LandingOutcome<S, C>>, renderSucceeded: (data: S) => ReactNode, renderInvalid: () => ReactNode, renderConflict?: (data: C) => ReactNode })`：`children` 是就绪态里按钮上方的内容（重设密码的表单就放这里，见 Task 19）；就绪态的主按钮是 `type="submit"` 的表单按钮，`children` 里的字段属于同一个 `<form>`
  - 结果屏组件 `LandingResult({ tone: "success" | "danger", title, children, actions? })`

- [ ] **Step 1: 写失败测试**：`readFragmentToken`——合法 43 位返回；`rejects_token_with_trailing_character`、`ignores_other_fragment_params`（`#x=1&token=<43位>` 返回令牌、`#TOKEN=…` 返回 null）（Review Focus 2）；空串、无 `token` → null。组件——初始渲染按钮禁用；挂载后 `history.replaceState` 被调用且新地址不含 `#`；合法令牌 → 就绪、主按钮聚焦；无令牌 → 直接无效屏且 `submit` 未调用；点按钮 → `pendingLabel`、禁用；`succeeded` / `invalid` / `conflict` 渲染对应函数；`unavailable` → 回就绪 + 提示条 `没有成功，请稍后再试`；`stay` → 回就绪、没有提示条。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：外壳 `AuthShell back="/"`；结果屏图标 `CircleCheck text-moss-500` / `CircleAlert text-rust-500`。
- [ ] **Step 4: 跑测试确认通过**
- [ ] **Step 5: 提交**：`feat(portal): 邮件落地页的共用状态机 (PAP-102)`

### Task 18: 验证邮箱落地页

**Files:**
- Create: `src/components/landing/VerifyEmailLanding.tsx`、`src/app/verify-email/page.tsx`
- Modify: `src/lib/identity/token-actions.ts`（加 `verifyEmail`）
- Test: `tests/lib/identity/token-actions.test.ts`、`tests/components/landing/VerifyEmailLanding.test.tsx`

**Interfaces:**
- Produces: `verifyEmail(token: string): Promise<{ ok: true; email: string } | { ok: false; reason: "invalid" | "unavailable" }>`（有 Cookie 带令牌）

- [ ] **Step 1: 写失败测试**：Action——200；422 `token`/`INVALID` → `invalid`；422 其他字段 → `unavailable` 并记日志；带 Cookie 时请求头有 `Authorization`、无 Cookie 时没有。页面——就绪态 `验证你的邮箱` / `验证邮箱`；成功后 `fetchQuery` 得到同邮箱已登录用户 → `chen.yu@example.com 已验证。其他设备上的登录已退出。` + `继续浏览`（`href="/"`）；得到未登录或另一个邮箱 → `为了安全，所有设备都已退出登录。` + `登录`；无效 + 已登录未验证 → `重新发送验证邮件` 按钮；无效 + 已登录已验证 → 「已验证」屏；无效 + 未登录 → `登录后重新发送`（`href="/login?next=%2Fsettings%2Faccount"`）；打开页面时不调用 `verifyEmail`。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：页面 `robots: noindex`、标题 `验证邮箱 · Patra`；成功后 `invalidateQueries(currentUser)`。
- [ ] **Step 4: 跑测试确认通过**
- [ ] **Step 5: 提交**：`feat(portal): 验证邮箱落地页 (PAP-102)`

### Task 19: 重设密码落地页

**Files:**
- Create: `src/components/landing/ResetPasswordLanding.tsx`、`src/app/reset-password/page.tsx`
- Modify: `src/lib/identity/token-actions.ts`（加 `resetPassword`）、`src/lib/auth/form-schemas.ts`（`resetPasswordSchema`）
- Test: `tests/lib/identity/token-actions.test.ts`、`tests/components/landing/ResetPasswordLanding.test.tsx`

**Interfaces:**
- Produces: `resetPassword(input: { token: string; newPassword: string }): Promise<{ ok: true; email: string } | { ok: false; reason: "invalid" } | { ok: false; reason: "invalid-password"; code: string | null } | { ok: false; reason: "throttled"; retryAfterSeconds: number | null } | { ok: false; reason: "unavailable" }>`

- [ ] **Step 1: 写失败测试**：Action 各结果。页面：`新密码` 提示 `至少 8 位`；不一致 → `两次输入的密码不一致`（不调 Action）；`invalid-password` `TOO_COMMON` → 注册页同一句，留在就绪态；`throttled` 125 → 提示条 `请求太频繁，请 3 分钟后再试`；成功 → handoff `put({ email, notice: "password-reset" })`、`invalidateQueries(currentUser)`、`router.replace("/login")`；`invalid` → `链接无效或已过期` + `重设链接 1 小时内有效，而且只能用一次。` + `重新申请`（`href="/forgot-password"`）。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：表单作为 `TokenLandingPage` 的 `children`；`invalid-password` 与 `throttled` 由页面自己显示（字段错误 / 提示条）后返回 `{ kind: "stay" }`（Task 17）。
- [ ] **Step 4: 跑测试确认通过**
- [ ] **Step 5: 提交**：`feat(portal): 重设密码落地页 (PAP-102)`

### Task 20: 确认新邮箱与撤销改邮箱落地页

**Files:**
- Create: `src/components/landing/ConfirmEmailChangeLanding.tsx`、`src/components/landing/RevertEmailChangeLanding.tsx`、`src/app/confirm-email-change/page.tsx`、`src/app/revert-email-change/page.tsx`
- Modify: `src/lib/identity/token-actions.ts`（加 `confirmEmailChange`、`revertEmailChange`）
- Test: `tests/lib/identity/token-actions.test.ts`、`tests/components/landing/ConfirmEmailChangeLanding.test.tsx`、`tests/components/landing/RevertEmailChangeLanding.test.tsx`

**Interfaces:**
- Produces: `confirmEmailChange(token)` / `revertEmailChange(token)`: `Promise<{ ok: true; email: string } | { ok: false; reason: "invalid" | "taken" | "unavailable" }>`

- [ ] **Step 1: 写失败测试**：Action——200；422 → `invalid`；409 → `taken`。确认页——成功 `邮箱已更改` / `以后请用 new@example.com 登录。原来的邮箱会收到一封通知。` + `继续浏览`，已登录时 `invalidateQueries(currentUser)`；`taken` → `这个邮箱已被注册` + 正文 + `去账号设置`；无效 → `确认链接 1 小时内有效。可以回到设置重新发起更改。`，已登录出口 `去账号设置`、未登录出口 `登录`。撤销页——就绪态三条列表、危险主按钮 `撤销更改并锁定账号`；成功 `邮箱已改回` + 正文含 `old@example.com`、无按钮，`invalidateQueries(currentUser)`；`taken` → `无法改回原邮箱` / `原邮箱已被另一个账号注册。`；无效 → `撤销链接 7 天内有效。`、无按钮。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：两页 `robots: noindex`，标题 `确认新邮箱 · Patra`、`撤销邮箱更改 · Patra`。
- [ ] **Step 4: 跑测试确认通过；`pnpm build`**
- [ ] **Step 5: 提交**：`feat(portal): 确认新邮箱与撤销改邮箱落地页 (PAP-102)`

### Task 21: 用户中心 Playwright 冒烟

**Files:**
- Create: `tests/e2e/support/mailpit.ts`、`tests/e2e/support/account.ts`、`tests/e2e/user-center.spec.ts`
- Modify: `tests/e2e/auth.spec.ts`（把 `freshAccount`、`waitForTakeover` 挪到 `support/account.ts` 后改为引用）

**Interfaces:**
- Produces:
  - `latestToken(to: string, subjectIncludes: string, opts?: { after?: number }): Promise<string>`：`GET ${PATRA_MAILPIT_BASE_URL}/api/v1/search?query=to:"<to>"`，取 `Created` 晚于 `after` 且主题含 `subjectIncludes` 的最新一封，`GET /api/v1/message/{ID}` 的 `Text` 里 `#token=` 后 43 个字符；每 500ms 轮询，15 秒超时抛错
  - `freshAccount()`、`waitForTakeover(page, selector)`、`register(page, account)`、`login(page, account)`、`verifyEmail(page, account)`

- [ ] **Step 1: 先查 Mailpit 里四种邮件的主题**：`curl -s "$PATRA_MAILPIT_BASE_URL/api/v1/messages?limit=50" | jq -r '.messages[].Subject' | sort -u`，把验证邮箱、重设密码、确认新邮箱的主题关键词写进 `user-center.spec.ts` 的常量。
- [ ] **Step 2: 写 spec §13.2 的七条用例**（文件头 `test.skip(!process.env.PATRA_GATEWAY_BASE_URL || !process.env.PATRA_MAILPIT_BASE_URL, …)`；凭据只在内存，不 `console.log`）。
- [ ] **Step 3: 跑**：`PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528 PATRA_MAILPIT_BASE_URL=http://100.103.73.27:8025 pnpm test:e2e tests/e2e/user-center.spec.ts tests/e2e/auth.spec.ts --workers=1`。失败的先单独重跑一次排除冷编译；真失败按 superpowers:systematic-debugging 查，修产品代码要先补单测。
- [ ] **Step 4: 不带环境变量跑一次确认全部 skip**：`pnpm test:e2e tests/e2e/user-center.spec.ts`
- [ ] **Step 5: 提交**：`test(portal): 用户中心端到端冒烟 (PAP-102)`

### Task 22: 预览走查、文档与收尾

**Files:**
- Modify: `patra-portal/README.md`、`patra-portal/docs/design-briefs/v0.12/user-center.md`（§10「落地时的调整」加两条）、`docs/patra/specs/2026-10-23-portal-user-center-account-security-design.md`（状态行改「已实施，待用户验收」，加第 18 节「实施结果」）
- 视走查结果修改各组件

- [ ] **Step 1: 离线截画板**：在 scratchpad 解压快照，写 `shots.json`（`Account` / `Account-M` / `Security` / `Security-M` / `Landing` / `Landing-M` / `Main` / `Main-M` 的各场景），`node <harness>/shoot.mjs <快照目录> <shots.json> <输出目录> offline`。
- [ ] **Step 2: 本机门户截同场景**：`preview_start portal-dev`；用 Playwright 无头脚本（放 scratchpad，随机注册账号，凭据不打印）在 1440×900 与 375×812 下截账号页三卡片与注销三屏、安全页两卡片（另一个上下文登录造出第二台设备）、再认证对话框（把会话的 `auth_at` 等到 10 分钟外，或用 11 分钟前登录的上下文）、忘记密码两态、四个落地页（Mailpit 真令牌）的就绪 / 成功 / 无效、取消注销页。逐屏与画板对照，差异修掉（每处修改先补或改测试）。
- [ ] **Step 3: 真浏览器复核 spec §14 的六条**并记进 spec §18。
- [ ] **Step 4: 文档**：README 加新路由、再认证、冷静期跳转与 proxy matcher、冒烟跑法（两个环境变量）；简报 §10 加「时间固定 Asia/Shanghai」「再认证在 Action 调用层拦」「验证页不做打开即显示已验证的捷径」三条。
- [ ] **Step 5: 全量门禁**：`pnpm lint && pnpm typecheck && pnpm test && pnpm build`，全部通过；Task 21 的冒烟再跑一遍。
- [ ] **Step 6: 提交**：`docs(portal): 记录 PAP-102 的实施结果与走查调整 (PAP-102)`
- [ ] **Step 7: 停在本地请用户验收**；在共享笔记目录写 `pap-102-portal-account-security-handover.md`（产物、偏离、验收要点）。
