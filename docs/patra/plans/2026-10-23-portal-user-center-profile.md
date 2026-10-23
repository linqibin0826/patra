# 门户用户中心（PAP-101）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> 本计划按用户约定在当前会话内联执行（superpowers:executing-plans），不派实现子代理。

**Goal:** 门户接上 v0.12 后端：注册加用户名、导航栏头像与菜单、未验证横幅、设置页外壳、个人资料页、隐私页、公开主页 `/u/[username]`、头像上传与转发、浏览器 IP / UA 与设备标识转发。

**Architecture:** 服务端取数在 RSC 里经 `gatewayFetch`；写操作用 Server Action（头像上传例外，走 Route Handler）；浏览器端状态用 TanStack Query（当前用户、建议候选）与 zustand（重发冷却）；表单用 RHF + Zod。生产入口用 `--import` 预加载模块在 Next 之前删掉入站 `X-Forwarded-For`。

**Tech Stack:** Next.js 16.3 App Router（webpack）/ React 19 / TypeScript strict / Tailwind v4 / `@base-ui/react` 1.8 / TanStack Query 5 / zustand 5 / RHF 7 + Zod 4 / Vitest 4 + Testing Library；新依赖 `react-easy-crop@6.2.4`、`@dnd-kit/core@6.3.1`、`@dnd-kit/sortable@10.0.0`、`@dnd-kit/utilities@3.2.2`。

**Spec:** `docs/patra/specs/2026-10-23-portal-user-center-profile-design.md`（下称 spec）。页面、状态与文案：`patra-portal/docs/design-briefs/v0.12/user-center.md`（下称简报）。画板截图：先用 `docs/patra/design/snapshots/2026-10-22-user-center-hifi.zip` 的 `harness/shoot.mjs` 离线渲染（README 有用法），对照 `.dc.html` 的间距与字号。

## Global Constraints

- 所有命令在 `patra-portal/` 下执行；单测 `pnpm exec vitest run <路径>`；收尾 `pnpm lint && pnpm typecheck && pnpm test`。
- TypeScript strict：禁止 `any`、`@ts-ignore`；路径别名 `@/` → `src/`、`@tests/` → `tests/`，新增 `@server/` → `server/`（Task 1）；禁止 `../../`。
- 每个方法 / 组件写 `///` 注释（与现有代码同风格，中文）；Biome 是唯一格式权威。
- 守护测试必须保持全绿：只有 `src/lib/gateway.ts` 直接 `fetch` 网关、读 `PATRA_GATEWAY_BASE_URL`、写 `Authorization`；只有 `src/lib/auth/session-cookie.ts` 调 `cookies()`；颜色只用 `@theme` 已有 token 的标准类名（如 `text-fg-3`、`bg-clay-700`），不写 `[var(--x)]`。
- 文案一律照简报原文（§3、§4），不自拟；Server Action / Route Handler 的结果不带文案，只带 `reason` 与原因码。
- 网关只收两个转发相关头：`X-Forwarded-For`（单个 IP 字面量）与 `User-Agent`；只发给 identity 请求与头像读取转发，catalog 公开读不带（spec §4.2，用户已同意）。
- `traits` 一律按「包含」判断；会话失效 = 401 且 `traits` 不含 `PASSWORD_MISMATCH`。
- 断点 880px；移动输入框 17px、按钮 48px 高；尊重 `prefers-reduced-motion`。
- 提交：每个 Task 末尾提交一次，只 `git add` 本 Task 的文件；提交信息 `type(portal): 中文动词开头的主题 (PAP-101)`，主题不以大写字母开头，末尾带 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。不 push、不开 PR。

## Review Focus

1. 粘贴进来的用户名带首尾空格或大写（` Chen-Yu `）：前端与后端一样先去空白、转小写再判，不能报「只能用小写字母…」——Task 6 加测试 `trims_and_lowercases_before_checking`。
2. 昵称是 emoji 组合序列或以「·」开头：计数按字素（👨‍👩‍👧 算 1），默认头像字跳过开头标点——Task 9 加测试 `counts_zwj_emoji_as_one_grapheme`、`skips_leading_middle_dot`。
3. 设备标识 Cookie 被改坏（长度不对、含 `.`）：换新的再登录，不能让浏览器永远 422——Task 5 加测试 `replaces_a_malformed_device_cookie`。
4. `/u/` 路径带大写或非法字符：大写原样交给后端（后端会规范化），非法字符直接 404 且不打网关——Task 24 加测试 `rejects_invalid_username_without_calling_gateway`、`passes_uppercase_username_through`。
5. 隐私页连点三次、其中一次失败：界面最终停在服务端最后一次确认的值——Task 23 加测试 `rolls_back_to_last_confirmed_value_after_failure_in_a_burst`。

---

### Task 1: 剥掉入站 X-Forwarded-For 的预加载模块

**Files:**
- Create: `patra-portal/server/strip-forwarded-for.mjs`、`patra-portal/server/strip-forwarded-for.d.mts`
- Modify: `patra-portal/tsconfig.json`（`paths` 加 `"@server/*": ["./server/*"]`，`include` 加 `server/**/*.d.mts`）、`patra-portal/vitest.config.ts`（alias `@server`）、`patra-portal/Dockerfile`
- Test: `patra-portal/tests/server/strip-forwarded-for.test.ts`（文件头 `// @vitest-environment node`）

**Interfaces:**
- Produces: `install(): void`（幂等：重复调用不重复包装）；模块被 `--import` 加载时自动调用。

- [ ] **Step 1: 写失败测试**：`it("removes_inbound_x_forwarded_for_before_listeners_run")`：`install()` 后 `http.createServer` 起在 `127.0.0.1:0`，用 `http.request` 发 `X-Forwarded-For: 203.0.113.9`、`X-Custom: keep`，listener 里记下 `req.headers`；断言 `x-forwarded-for` 为 `undefined`、`x-custom` 为 `"keep"`。另一条 `it("is_idempotent")`：调两次 `install()`，`Server.prototype.emit` 只包一层（第二次调用前后 `emit` 引用相同）。
- [ ] **Step 2: 跑测试确认失败**：`pnpm exec vitest run tests/server/strip-forwarded-for.test.ts`，期望模块不存在而失败。
- [ ] **Step 3: 实现**：`node:http` 的 `Server.prototype.emit` 套一层，`event === "request"` 时 `delete req.headers["x-forwarded-for"]`；用一个 `Symbol.for("patra.stripForwardedFor")` 标记已安装。`.d.mts` 只声明 `export function install(): void;`。文件末尾 `install();`。
- [ ] **Step 4: 改 Dockerfile**：runner 阶段加 `COPY --from=builder --chown=nextjs:nodejs /app/server/strip-forwarded-for.mjs ./`，`CMD ["node", "--import", "./strip-forwarded-for.mjs", "server.js"]`，注释写明原因（spec §4.1）。
- [ ] **Step 5: 跑测试与类型检查**：`pnpm exec vitest run tests/server/strip-forwarded-for.test.ts && pnpm typecheck`，全过。
- [ ] **Step 6: 提交**（连同 spec 与本计划）：`git add patra-portal/server patra-portal/tsconfig.json patra-portal/vitest.config.ts patra-portal/Dockerfile patra-portal/tests/server docs/patra/specs/2026-10-23-portal-user-center-profile-design.md docs/patra/plans/2026-10-23-portal-user-center-profile.md`；`feat(portal): 生产入口在 Next 之前删掉入站 X-Forwarded-For (PAP-101)`。

### Task 2: 浏览器来源（IP 与 UA）

**Files:**
- Create: `patra-portal/src/lib/client-origin.ts`
- Modify: `patra-portal/src/lib/gateway.ts`（导出 `isSendableHeaderValue`，`canSendToken` 改为调用它）
- Test: `patra-portal/tests/lib/client-origin.test.ts`

**Interfaces:**
- Produces:
  - `interface ClientOrigin { ip: string | null; userAgent: string | null }`
  - `clientOriginFromHeaders(headers: Headers): ClientOrigin`（纯函数）
  - `readClientOrigin(): Promise<ClientOrigin>`（server-only，读 `next/headers` 的 `headers()`）
  - `isSendableHeaderValue(value: string): boolean`（`gateway.ts`）

- [ ] **Step 1: 写失败测试**（`it.each` 表驱动）：
  - `ip`：`"192.168.97.1"` → 原样；`"::1"` → `"::1"`；`"::ffff:10.0.0.2"` → `"10.0.0.2"`；`"fe80::1%eth0"` → `"fe80::1"`；`"203.0.113.9, 10.0.0.1"` → `null`；`"evil.example.com"` → `null`；`""` / 缺失 → `null`；`" 10.0.0.3 "` → `"10.0.0.3"`。
  - `userAgent`：原值保留；缺失 → `null`；含 `\n` 或码点 > 0xFF → `null`。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现**：IP 判定用 `node:net` 的 `isIP`；`readClientOrigin` 只是 `clientOriginFromHeaders(await headers())`，文件首行 `import "server-only"`。
- [ ] **Step 4: 跑测试确认通过**：`pnpm exec vitest run tests/lib/client-origin.test.ts tests/lib/gateway.test.ts`。
- [ ] **Step 5: 提交**：`feat(portal): 从请求头取浏览器 IP 与 UA 并校验 (PAP-101)`。

### Task 3: 网关客户端扩展

**Files:**
- Modify: `patra-portal/src/lib/gateway.ts`、`patra-portal/src/proxy.ts`、`patra-portal/src/lib/auth/current-user.ts`（只改 `lookupSessionByToken` 签名，见下）
- Test: `patra-portal/tests/lib/gateway.test.ts`、`patra-portal/tests/proxy.test.ts`

**Interfaces:**
- Consumes: `ClientOrigin`（Task 2）
- Produces:
  - `GatewayRequestInit` = `{ method?: "GET" | "HEAD" | "POST" | "PUT" | "DELETE"; json?: unknown; body?: FormData; token?: string; client?: ClientOrigin; redirect?: "follow" | "manual"; timeoutMs?: number }`，`redirect` 默认 `"manual"`；`json` 与 `body` 同时给时抛错。
  - `GatewayStreamInit` 加 `client?: ClientOrigin`。
  - `lookupSessionByToken(token: string | null, options?: { timeoutMs?: number; client?: ClientOrigin }): Promise<SessionLookup>`

- [ ] **Step 1: 写失败测试**（`gateway.test.ts` 新增）：
  - `sends_put_and_delete`：`method: "PUT"` / `"DELETE"` 原样传给 `fetch`。
  - `sends_form_data_without_content_type`：`body: new FormData()` 时 `init.body` 是该对象、`headers` 里没有 `Content-Type`。
  - `writes_only_two_forwarding_headers`：`client: { ip: "10.0.0.2", userAgent: "UA/1" }` → headers 恰好含 `X-Forwarded-For: 10.0.0.2`、`User-Agent: UA/1`，不含任何 `X-Forwarded-Host|Proto|Port|Prefix`、`Forwarded`。
  - `omits_null_client_fields`：`{ ip: null, userAgent: null }` → 两个头都不在。
  - `does_not_follow_redirects_by_default`：`init.redirect === "manual"`。
  - `gatewayStream_forwards_client`：同样写两个头。
  - `proxy.test.ts`：已登录打开 `/login` 时，发往 `/auth/me` 的请求带 `X-Forwarded-For`（来自请求的 `x-forwarded-for`）与 `User-Agent`。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现**；`proxy.ts` 用 `clientOriginFromHeaders(req.headers)` 传给 `lookupSessionByToken(token, { timeoutMs: CHECK_TIMEOUT_MS, client })`；`proxy.ts` 里对 catalog 的 `HEAD` 不传 `client`。更新所有现有 `lookupSessionByToken` 调用。
- [ ] **Step 4: 跑全部现有测试**：`pnpm exec vitest run tests/lib tests/proxy.test.ts tests/app`，全过（若「默认 manual」改变了某个现有行为，按 spec §6.3 的备注退回只在 `/users/{username}` 显式传，并在 spec §6.3 记一笔）。
- [ ] **Step 5: 提交**：`feat(portal): 网关客户端支持 PUT / DELETE、multipart、不跟随重定向与转发头 (PAP-101)`。

### Task 4: ProblemDetail 解析与当前用户的新字段

**Files:**
- Create: `patra-portal/src/lib/identity/problem.ts`、`patra-portal/src/lib/identity/user-schema.ts`、`patra-portal/src/lib/identity/request.ts`
- Modify: `patra-portal/src/lib/auth/types.ts`、`patra-portal/src/lib/auth/current-user.ts`、`patra-portal/src/app/api/auth/me/route.ts`
- Test: `patra-portal/tests/lib/identity/problem.test.ts`、`tests/lib/identity/request.test.ts`、`tests/lib/auth/current-user.test.ts`、`tests/app/api/auth-me.test.ts`

**Interfaces:**
- Produces:
  - `user-schema.ts`（不带 server-only）：`userSchema`（Zod：`userId`、`email`、`emailVerified: boolean`、`username`、`displayName`、`avatarUrl: string | null`、`avatarPalette: int 0–11`、`status: "ACTIVE" | "PENDING_DELETION"`、`deletionDueAt: string | null`、`nextUsernameChangeAt: string | null`；`accountType` 不读）；`type CurrentUser = z.infer<typeof userSchema>`，`types.ts` 改为从这里 re-export。
  - `problem.ts`（server-only）：`interface Problem { status: number; code: string | null; traits: ReadonlySet<string>; errors: FieldError[]; retryAfterSeconds: number | null }`；`parseProblem(res: Response, body: unknown): Problem`（`retryAfterSeconds` 先取体、再取 `Retry-After` 头，都要是非负整数）；`isSessionRejected(p: Problem): boolean`。
  - `request.ts`（server-only）：`type IdentityReply = { kind: "ok"; res: Response; body: unknown } | { kind: "problem"; problem: Problem } | { kind: "no-session" } | { kind: "error" }`；`callIdentity(path: string, init?: Omit<GatewayRequestInit, "token" | "client">, options?: { auth?: "required" | "optional" }): Promise<IdentityReply>`——读会话令牌（`auth: "required"` 且没有 / 放不进请求头 → `no-session`，不打网关）与 `readClientOrigin()`，调 `gatewayFetch(\`/patra-identity${path}\`, …)`；2xx 与 3xx 为 `ok`，其余为 `problem`，抛错记日志为 `error`。不清 Cookie（RSC 里不能写 Cookie，调用方决定）。
  - `lookupSession()` 用 React `cache()` 包装，并带 `client: await readClientOrigin()`。

- [ ] **Step 1: 写失败测试**：
  - `problem.test.ts`：traits 顺序不同仍能判断；`errors` 里坏项被丢弃、好项保留；`retryAfterSeconds` 体优先于头；`isSessionRejected` 对 `{401, traits:[UNAUTHORIZED]}` 为真、对 `{401, traits:[UNAUTHORIZED, PASSWORD_MISMATCH]}` 为假、对 `GW-0401` 无 traits 为真。
  - `current-user.test.ts`：200 带完整新字段 → `authenticated` 且 `user` 等于这些字段；`avatarPalette: 12` 或 `status: "BANNED"` → `unavailable`；请求带 `client` 的两个头。
  - `request.test.ts`：无 Cookie 且 `auth: "required"` → `no-session` 且 `fetch` 未调用；301 → `ok`；422 → `problem` 带字段错误；`fetch` 抛错 → `error`。
  - `auth-me.test.ts`：响应 `user` 带新字段。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现**；`gateway-response.ts` 的 `readJson` / 日志函数复用，`isRecord` 继续从那里导出。
- [ ] **Step 4: 跑测试确认通过**：`pnpm exec vitest run tests/lib tests/app`。
- [ ] **Step 5: 提交**：`feat(portal): 当前用户接上 v0.12 字段，统一解析 identity 的错误响应 (PAP-101)`。

### Task 5: 设备标识 Cookie

**Files:**
- Modify: `patra-portal/src/lib/auth/session-cookie.ts`
- Test: `patra-portal/tests/lib/auth/session-cookie.test.ts`

**Interfaces:**
- Produces: `ensureDeviceId(): Promise<string>`；常量 `DEVICE_ID_PATTERN = /^[A-Za-z0-9_-]{43}$/`。

- [ ] **Step 1: 写失败测试**（用 `@tests/stubs/cookie-jar`）：
  - `issues_a_device_id_when_missing`：返回值匹配 `DEVICE_ID_PATTERN`；写入一条 Set-Cookie，含 `patra_device=`、`HttpOnly`、`SameSite=lax`、`Path=/`、`Max-Age=34560000`。
  - `uses_host_prefix_when_secure`：`PATRA_SESSION_COOKIE_SECURE` 不设时名为 `__Host-patra_device` 且带 `Secure`。
  - `keeps_a_valid_device_id_and_extends_it`：Cookie 里已有合法值 → 原样返回，并重写一次（顺延）。
  - `replaces_a_malformed_device_cookie`（Review Focus 3）：值为 `"abc"` 或 43 位但含 `.` → 返回新值、不等于旧值。
  - `clearSessionCookie_does_not_touch_device_cookie`。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现**：`crypto.getRandomValues(new Uint8Array(32))` → `Buffer.from(...).toString("base64url")`。
- [ ] **Step 4: 跑测试确认通过**：`pnpm exec vitest run tests/lib/auth/session-cookie.test.ts tests/lib/gateway-boundary.test.ts`。
- [ ] **Step 5: 提交**：`feat(portal): 种长期设备标识 Cookie (PAP-101)`。

### Task 6: 用户名规则与文案

**Files:**
- Create: `patra-portal/src/lib/identity/username-rules.ts`
- Modify: `patra-portal/src/lib/auth/messages.ts`、`patra-portal/src/lib/auth/form-schemas.ts`
- Test: `patra-portal/tests/lib/identity/username-rules.test.ts`、`tests/lib/auth/messages.test.ts`、`tests/lib/auth/form-schemas.test.ts`

**Interfaces:**
- Produces:
  - `type UsernameIssue = "REQUIRED" | "INVALID_CHARACTER" | "TOO_SHORT" | "TOO_LONG" | "INVALID_FORMAT" | "DIGITS_ONLY"`
  - `normalizeUsername(raw: string): string`（去首尾空白 + 转小写）
  - `usernameIssue(raw: string): UsernameIssue | null`（先 normalize，再按上面顺序判；格式正则 `^[a-z0-9]([a-z0-9]|[_-][a-z0-9])*$`）
  - `messages.ts`：`usernameMessage(code: string | null): string`（简报 §4.1：`REQUIRED` 请输入用户名、`TOO_SHORT` 用户名至少 3 个字符、`TOO_LONG` 用户名最多 30 个字符、`INVALID_CHARACTER` 只能用小写字母、数字、_ 和 -、`INVALID_FORMAT` 开头和结尾要是字母或数字，_ 和 - 不能连着用、`DIGITS_ONLY` 用户名不能全是数字、`RESERVED` 这个用户名不能使用，请换一个、`ALREADY_TAKEN` 这个用户名已被使用、其他 用户名不符合要求）；`fieldErrorMessages` 扩到 `"email" | "username" | "password"`。
  - `registerSchema` 加 `username`：值经 `normalizeUsername`，问题用 `usernameMessage(usernameIssue(v))`；`RegisterFormValues` 随之变化。

- [ ] **Step 1: 写失败测试**：每个原因码一条（含判定顺序：`"a b"` → `INVALID_CHARACTER` 而不是 `TOO_SHORT`；`"ab"` → `TOO_SHORT`；`"-abc"` → `INVALID_FORMAT`；`"a__b"` → `INVALID_FORMAT`；`"12345"` → `DIGITS_ONLY`；31 个 `a` → `TOO_LONG`）；`trims_and_lowercases_before_checking`（Review Focus 1）：`" Chen-Yu "` → `null`、`normalizeUsername` 得 `"chen-yu"`；`usernameMessage` 每个码一条。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现**。
- [ ] **Step 4: 跑测试确认通过**：`pnpm exec vitest run tests/lib`。
- [ ] **Step 5: 提交**：`feat(portal): 用户名规则与原因码文案 (PAP-101)`。

### Task 7: 登录与注册的 Server Action 接新契约

**Files:**
- Modify: `patra-portal/src/lib/auth/actions.ts`、`patra-portal/src/lib/auth/types.ts`
- Test: `patra-portal/tests/lib/auth/actions.test.ts`

**Interfaces:**
- Consumes: `userSchema`、`parseProblem`（Task 4）；`ensureDeviceId`（Task 5）；`readClientOrigin`（Task 2）
- Produces:
  - `interface RegisterInput { email: string; username: string; password: string }`（`Credentials` 仍是 `{ email, password }`，给登录）
  - `RegisterResult = { ok: true; user: CurrentUser } | { ok: false; reason: "invalid-fields"; fieldErrors: FieldError[] } | { ok: false; reason: "taken"; email: boolean; username: boolean } | { ok: false; reason: "unavailable" }`
  - `LoginResult` 不变，`user` 换成新类型
  - `register(input: RegisterInput)`、`login(input: Credentials)`：请求体加 `deviceId`（`ensureDeviceId()`），带 `client`；成功体 `{ sessionToken, user }`。

- [ ] **Step 1: 写失败测试**：
  - 注册 201 → 写会话 Cookie，`user` 是完整新字段；请求体含 `username`、`deviceId`（43 位），不含 `confirmPassword`；带 `X-Forwarded-For` 与 `User-Agent`（mock `next/headers` 的 `headers()`）。
  - 注册 409 `errors:[{field:"username",code:"ALREADY_TAKEN"}]` → `{ reason: "taken", email: false, username: true }`；两条同时 → 两者都为真；409 无可识别项 → `unavailable`。
  - 注册 422 只有 `deviceId` 错 → `unavailable`（并记日志）；`username` 错 → `invalid-fields`。
  - 登录 200 `status: "PENDING_DELETION"` → `ok: true`、`user.status` 为 `PENDING_DELETION`，写 Cookie。
  - 旧的顶层 `{ sessionToken, userId, email }` 响应 → `unavailable`、不写 Cookie。
  - 已有用例按新响应改写。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现**：`retryAfterSeconds` 改用 `parseProblem`；入参 Zod 加 `username: z.string()`。
- [ ] **Step 4: 跑测试确认通过**：`pnpm exec vitest run tests/lib/auth`。
- [ ] **Step 5: 提交**：`fix(portal): 登录注册接上 { sessionToken, user } 响应并带用户名与设备标识 (PAP-101)`。

### Task 8: 注册页加用户名，登录后按状态分流

**Files:**
- Create: `patra-portal/src/components/auth/UsernameField.tsx`
- Modify: `patra-portal/src/components/auth/RegisterForm.tsx`、`LoginForm.tsx`、`use-auth-submit.ts`、`patra-portal/src/lib/auth/next-path.ts`、`patra-portal/tests/e2e/auth.spec.ts`
- Test: `patra-portal/tests/components/auth/RegisterForm.test.tsx`、`LoginForm.test.tsx`、`tests/lib/auth/next-path.test.ts`

**Interfaces:**
- Consumes: Task 6、7
- Produces:
  - `deletionPendingHref(next: string): string` → `/account/deletion-pending?next=<safeNextPath(next)>`（`next` 为 `/` 时不带参数）
  - `useAuthSubmit<I, F>({ next, action: (input: I) => Promise<{ ok: true; user: CurrentUser } | F>, successToast, onSuccess?: (user: CurrentUser) => void, onFailure })`：成功后 `status === "PENDING_DELETION"` → 不弹 toast，`router.replace(deletionPendingHref(next))`；否则照旧。
  - `UsernameField` props：`FieldControl` + RHF `register` 返回值 + `readOnly`；输入时把值转小写并保持光标位置；常驻提示由调用方经 `FormField.hint` 传入：`3–30 位，小写字母、数字、_ 或 -，用作主页地址 /u/<值>`（值为空时 `/u/你的用户名`）。

- [ ] **Step 1: 写失败测试**：
  - 用户名字段在邮箱下面（`getAllByRole("textbox")` 顺序）、属性 `autocomplete="username"`、`spellcheck="false"`、`autocapitalize="none"`。
  - 键入 `Chen` 显示为 `chen`；提示随输入变成 `/u/chen`。
  - 离开字段校验：`ab` → `用户名至少 3 个字符`。
  - 「请牢记密码，目前不支持找回密码。」不再出现。
  - 后端 `taken` 双字段 → 两处文案（`该邮箱已注册，去登录`、`这个用户名已被使用`），焦点在邮箱；只有用户名 → 焦点在用户名。
  - `invalid-fields` 带 `username`/`RESERVED` → `这个用户名不能使用，请换一个`。
  - 登录成功且 `PENDING_DELETION` → `router.replace("/account/deletion-pending?next=%2Fpapers")`、没有 toast。
  - `next-path.test.ts`：`deletionPendingHref("/papers?q=a")`、`deletionPendingHref("/")`。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现**；`useServerFieldErrors` 的字段集合扩到 `email`、`username`、`password`（`taken` 时按布尔值分别 `show`）；注册成功时 `onSuccess` 调 Task 11 的 `useVerificationResendStore.getState().startCooldown(Date.now() + 60_000)`——Task 11 之前先留 `onSuccess` 参数，store 接线在 Task 11 Step 3 补上。
- [ ] **Step 4: 改 e2e**：`tests/e2e/auth.spec.ts` 的注册用例填用户名（`` `e2e-${Date.now().toString(36)}` ``，满足规则），用户菜单断言从邮箱改成昵称 / `@用户名`。只改不跑（需要真网关，留到 Task 25）。
- [ ] **Step 5: 跑测试确认通过**：`pnpm exec vitest run tests/components/auth tests/lib/auth tests/app/auth-pages.test.tsx`。
- [ ] **Step 6: 提交**：`feat(portal): 注册页加用户名，登录后冷静期账号去取消注销页 (PAP-101)`。

### Task 9: 头像工具与 Avatar 组件

**Files:**
- Create: `patra-portal/src/lib/grapheme.ts`、`patra-portal/src/lib/identity/avatar.ts`、`patra-portal/src/components/user/Avatar.tsx`
- Test: `patra-portal/tests/lib/grapheme.test.ts`、`tests/lib/identity/avatar.test.ts`、`tests/components/user/Avatar.test.tsx`

**Interfaces:**
- Produces:
  - `graphemes(value: string): string[]`、`graphemeCount(value: string): number`（`Intl.Segmenter("zh", { granularity: "grapheme" })`）
  - `AVATAR_PALETTE: readonly string[]`（12 个字面量：`bg-clay-700`、`bg-slate-500`、`bg-moss-500`、`bg-ink-700`、`bg-rust-500`、`bg-amber-700`、`bg-clay-900`、`bg-moss-700`、`bg-ink-500`、`bg-clay-800`、`bg-ink-900`、`bg-ink-600`）；`avatarPaletteClass(index: number): string`（越界取 `index mod 12` 的非负值）
  - `avatarInitial(displayName: string, username: string): string`
  - `avatarSrc(avatarUrl: string | null, size: 128 | 512): string | null`
  - `interface AvatarIdentity { displayName: string; username: string; avatarUrl: string | null; avatarPalette: number }`
  - `<Avatar identity={AvatarIdentity} size={36 | 40 | 72 | 88 | 96 | 128} className?: string />`（`"use client"`；36 / 40 用 128 档、其余 512 档；`<img alt="">` 出错时回落默认头像；默认头像字 `aria-hidden`、衬线、`text-paper-50`、字号按尺寸查静态 class 表）

- [ ] **Step 1: 写失败测试**：
  - `counts_zwj_emoji_as_one_grapheme`（Review Focus 2）：`graphemeCount("👨‍👩‍👧a")` 为 2。
  - `avatarInitial`：`("陈宇","chen-yu")` → `陈`；`skips_leading_middle_dot`：`("·小林","x")` → `小`；`("  alice","a")` → `A`；`("9号","n")` → `9`；`("😀abc","u")` → `😀`；`("···","zed")` → `Z`。
  - `avatarSrc`：`("/users/chen-yu/avatar?v=0123456789abcdef", 128)` → `/api/users/chen-yu/avatar?v=0123456789abcdef&size=128`；`v` 不是 16 位十六进制、路径多一段、`null` → `null`。
  - `AVATAR_PALETTE` 长度 12、无重复；`avatarPaletteClass(13)` = 第 1 项。
  - `Avatar`：有图时 `img` 的 `src` 对、`alt` 为空；`fireEvent.error(img)` 后显示「陈」；无图时显示首字且容器带调色板 class。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现**：首字跳过 `/^[\p{P}\p{White_Space}]$/u`；拉丁字母 `toUpperCase()`。
- [ ] **Step 4: 跑测试确认通过**：`pnpm exec vitest run tests/lib tests/components/user tests/design-system`。
- [ ] **Step 5: 提交**：`feat(portal): 头像组件与默认头像（首字素 + 12 色板） (PAP-101)`。

### Task 10: 导航栏账号区

**Files:**
- Modify: `patra-portal/src/components/portal/UserMenu.tsx`、`NavAccount.tsx`、`MobileAccount.tsx`
- Test: `patra-portal/tests/components/portal/NavAccount.test.tsx`、`MobileAccount.test.tsx`

**Interfaces:**
- Consumes: `Avatar`（Task 9）、`CurrentUser`（Task 4）
- Produces: `UserMenu` props 改为 `{ user: CurrentUser; loggingOut: boolean; onLogout: () => void }`。

- [ ] **Step 1: 写失败测试**（简报 §3.3、§4.3 `nav.*`、`mobile.account`）：触发器名「账号菜单」、里面是头像；打开后头部有昵称与 `@chen-yu`，不出现邮箱；菜单项 `个人主页` 链到 `/u/chen-yu`、`设置` 链到 `/settings/profile`、`退出登录` 仍在；30 字素昵称有 `title` 全文；移动账号块显示头像、昵称、`@用户名`，两个链接点击后调用 `onClose`。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现**（菜单宽 `w-60`；链接用 base-ui `Menu.LinkItem` 或 `render={<Link …/>}`，以 `node_modules/@base-ui/react/menu` 的类型为准）。
- [ ] **Step 4: 跑测试确认通过**：`pnpm exec vitest run tests/components/portal tests/components/ui`。
- [ ] **Step 5: 提交**：`feat(portal): 导航栏显示头像，菜单加个人主页与设置 (PAP-101)`。

### Task 11: 重发验证邮件与未验证横幅

**Files:**
- Create: `patra-portal/src/lib/identity/verification-actions.ts`（`"use server"`）、`patra-portal/src/store/verification-resend.ts`、`patra-portal/src/components/portal/ResendVerificationButton.tsx`、`patra-portal/src/components/portal/UnverifiedEmailBanner.tsx`
- Modify: `patra-portal/src/components/portal/TopNav.tsx`、`patra-portal/src/lib/auth/messages.ts`、`patra-portal/src/components/auth/RegisterForm.tsx`（接 Task 8 的 `onSuccess`）
- Test: `tests/lib/identity/verification-actions.test.ts`、`tests/store/verification-resend.test.ts`、`tests/components/portal/UnverifiedEmailBanner.test.tsx`、`tests/components/portal/ResendVerificationButton.test.tsx`

**Interfaces:**
- Consumes: `callIdentity`、`isSessionRejected`（Task 4）；`clearSessionCookie`
- Produces:
  - `resendVerificationEmail(): Promise<ResendResult>`；`ResendResult = { ok: true; nextResendAt: string } | { ok: false; reason: "throttled"; retryAfterSeconds: number | null } | { ok: false; reason: "already-verified" | "pending-deletion" | "signed-out" | "unavailable" }`（`POST /me/email/verification/resend`；`signed-out` 时清会话 Cookie）
  - `useVerificationResendStore`：`{ availableAt: number | null; limited: boolean; startCooldown(until: number): void; markLimited(until: number): void; reset(): void }`
  - `<ResendVerificationButton label?: string />`（默认 `重新发送`）：内部用 store、`resendVerificationEmail`、`queryClient`
  - `<UnverifiedEmailBanner />`

- [ ] **Step 1: 写失败测试**（简报 §3.4 状态表、§4.3 `banner.*`）：
  - Action：202 → `ok` 带 `nextResendAt`；429 → `throttled` 带秒数；409 traits `EMAIL_ALREADY_VERIFIED` → `already-verified`；409 traits `PENDING_DELETION` → `pending-deletion`；401 → `signed-out` 且写了清除 Cookie；无 Cookie → `signed-out` 不打网关。
  - 横幅：`emailVerified: false`、`ACTIVE` 时出现，文案含邮箱；`emailVerified: true` 或 `PENDING_DELETION` 或匿名时不渲染。
  - 按钮：点击后 `发送中…` 禁用；成功后 `已发送 · 60 秒后可重发`（假时钟推进 1 秒变 59），toast `验证邮件已发送`；`throttled` 125 秒 → `发送太频繁，请 3 分钟后再试`，45 秒 → `发送太频繁，请 45 秒后再试`；`already-verified` → toast `邮箱已验证` 且使 currentUser 失效；`unavailable` → 错误 toast `没有发出去，请稍后再试`、按钮恢复；倒计时到 0 恢复 `重新发送`；`pending-deletion` → `router.replace(deletionPendingHref(当前路径))`；`signed-out` → `router.replace` 到登录页。
  - store 已有未来的 `availableAt` 时，按钮一出现就是倒计时态（注册后 60 秒内）。
  - 读屏：倒计时文字不在 `aria-live` 里，结束文案在 `aria-live="polite"` 区域。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现**：`TopNav` 返回 `<><header>…</header><UnverifiedEmailBanner /></>`；横幅桌面一行（`MailWarning` `text-amber-700`、`bg-status-warning-bg`、上下 `border-amber-500/30`、内容对齐 `max-w-page px-gutter`，右侧 `重新发送` · `更改邮箱`→`/settings/account#email`），≤ 880px 两行且不显示「更改邮箱」；`RegisterForm` 的 `onSuccess` 调 `startCooldown(Date.now() + 60_000)`。
- [ ] **Step 4: 跑测试确认通过**：`pnpm exec vitest run tests/components tests/lib tests/store`。
- [ ] **Step 5: 提交**：`feat(portal): 未验证邮箱横幅与重发验证邮件 (PAP-101)`。

### Task 12: 头像读取转发

**Files:**
- Create: `patra-portal/src/lib/portal-api/gateway-file.ts`、`patra-portal/src/app/api/users/[username]/avatar/route.ts`
- Modify: `patra-portal/src/lib/portal-api/fulltext-files.ts`（改用 `proxyGatewayFile`）
- Test: `patra-portal/tests/app/api/users-avatar.test.ts`、现有 `tests/app/api/papers-fulltext-files.test.ts`

**Interfaces:**
- Produces: `proxyGatewayFile(path: string, request: Request, options: { passthrough: readonly string[]; client?: ClientOrigin }): Promise<Response>`（200 / 304 透传白名单头；其他 4xx → 404；5xx 或抛错 → 502）。

- [ ] **Step 1: 写失败测试**：`/api/users/chen-yu/avatar?v=0123456789abcdef&size=128` → 网关路径 `/patra-identity/users/chen-yu/avatar?size=128&v=0123456789abcdef`，带 `If-None-Match` 与 `client` 头；200 只透传 `Content-Type`、`Content-Length`、`ETag`、`Cache-Control`；304 无体；`size=256`、`v=xyz`、用户名含 `/` 或 `.` → 404 且不打网关；网关 404 → 404；网关 503 → 502。全文文件的旧测试照旧全过。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现**（`export const dynamic = "force-dynamic"`；用户名形状 `^[A-Za-z0-9_-]{1,64}$`）。
- [ ] **Step 4: 跑测试确认通过**：`pnpm exec vitest run tests/app/api`。
- [ ] **Step 5: 提交**：`feat(portal): 头像经门户同源转发并透传不可变缓存头 (PAP-101)`。

### Task 13: 头像上传与移除接口

**Files:**
- Create: `patra-portal/src/lib/same-origin.ts`、`patra-portal/src/lib/identity/avatar-upload.ts`（结果类型与浏览器端调用函数）、`patra-portal/src/app/api/me/avatar/route.ts`
- Test: `patra-portal/tests/lib/same-origin.test.ts`、`tests/app/api/me-avatar.test.ts`

**Interfaces:**
- Consumes: `callIdentity`、`isSessionRejected`、`clearSessionCookie`
- Produces:
  - `isSameOrigin(request: Request): boolean`（`Origin` 存在且 `new URL(origin).host === request.headers.get("host")`）
  - `type AvatarMutationResult = { ok: true; avatarUrl: string | null } | { ok: false; reason: "too-large" | "rejected" | "pending-deletion" | "signed-out" | "unavailable"; code?: string }`
  - 浏览器端：`uploadAvatar(file: Blob, fileName: string): Promise<AvatarMutationResult>`、`removeAvatar(): Promise<AvatarMutationResult>`（`fetch("/api/me/avatar", …)`；网络错误 → `unavailable`）

- [ ] **Step 1: 写失败测试**：
  - `POST` 无 `Origin` 或 `Origin` 主机不同 → 403、不打网关；`DELETE` 同。
  - 无会话 Cookie → 401 `{ ok:false, reason:"signed-out" }`。
  - `Content-Length: 6291457` → 413 `too-large`、不读体。
  - 没有 `file` part → 422 `{ reason: "rejected", code: "REQUIRED" }`、不打网关。
  - 网关 200 `{ avatarUrl }` → 200 `{ ok:true, avatarUrl }`；转发的 `FormData` 里 `file` 的文件名与类型保留；带 `client` 头。
  - 网关 413 → `too-large`；422 `errors[0].code: "TOO_SMALL"` → `{ reason:"rejected", code:"TOO_SMALL" }`；409 `PENDING_DELETION` → `pending-deletion`；401 → `signed-out` 且清 Cookie；503 → `unavailable`。
  - `DELETE` 网关 204 → 200 `{ ok:true, avatarUrl:null }`。
  - 响应都有 `Cache-Control: private, no-store`。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现**。
- [ ] **Step 4: 跑测试确认通过**：`pnpm exec vitest run tests/app/api tests/lib`。
- [ ] **Step 5: 提交**：`feat(portal): 头像上传与移除接口（同源校验、错误收敛） (PAP-101)`。

### Task 14: 设置页外壳与守卫

**Files:**
- Create: `patra-portal/src/lib/auth/require-user.ts`、`patra-portal/src/app/settings/layout.tsx`、`src/app/settings/page.tsx`、`src/components/settings/SettingsShell.tsx`、`SettingsNav.tsx`、`SettingsCard.tsx`
- Test: `patra-portal/tests/lib/auth/require-user.test.ts`、`tests/components/settings/SettingsNav.test.tsx`、`tests/components/settings/SettingsCard.test.tsx`、`tests/app/settings-pages.test.tsx`

**Interfaces:**
- Consumes: `lookupSession`（Task 4，已 `cache()`）
- Produces:
  - `requireUser(path: string): Promise<CurrentUser>`（`anonymous` / `rejected` → `redirect("/login?next=" + path)`，用 `authPageHref`；`unavailable` → 抛错）
  - `<SettingsShell username: string | null; children />`、`<SettingsNav />`（四项：`/settings/profile` 个人资料 / 资料、`/settings/account` 账号、`/settings/security` 安全、`/settings/privacy` 隐私）
  - `<SettingsCard title: string; description?: ReactNode; aside?: ReactNode; variant?: "default" | "danger"; id?: string; children />`

- [ ] **Step 1: 写失败测试**：`requireUser` 三种结果；`/settings` 页面 `redirect("/settings/profile")`；`SettingsNav` 当前项 `aria-current="page"`，不出现「通知」「已连接账号」；`SettingsShell` 有 h1 `设置`、`@chen-yu`、`查看我的主页` 链到 `/u/chen-yu`；`SettingsCard` 的 `danger` 变体标题带 `text-status-danger`。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现**（简报 §3.5：桌面左栏 200px 吸顶 `top-[calc(3.5rem+1px+24px)]`、内容列 `max-w-160`；≤ 880px 改四个等宽分段标签吸顶在导航栏下；layout 渲染 `TopNav`、`SettingsShell`、`Footer`；metadata 基础 `robots: { index: false }`）。
- [ ] **Step 4: 跑测试确认通过**：`pnpm exec vitest run tests/components/settings tests/lib/auth tests/app`。
- [ ] **Step 5: 提交**：`feat(portal): 设置页外壳、分区导航与登录守卫 (PAP-101)`。

### Task 15: 资料的数据层与保存动作

**Files:**
- Create: `patra-portal/src/lib/identity/profile-schema.ts`（不带 server-only）、`src/lib/identity/occupation.ts`、`src/lib/identity/profile-form.ts`、`src/lib/identity/profile.ts`（server-only）、`src/lib/identity/profile-actions.ts`（`"use server"`）
- Test: `tests/lib/identity/profile-form.test.ts`、`tests/lib/identity/profile.test.ts`、`tests/lib/identity/profile-actions.test.ts`

**Interfaces:**
- Consumes: `callIdentity`、`parseProblem`、`isSessionRejected`、`graphemeCount`
- Produces:
  - `profile-schema.ts`：`myProfileSchema`、`privacySettingsSchema`（spec §3 的字段）；`type MyProfile`、`type PrivacySettings`、`type ProfileVisibility = "PUBLIC" | "SIGNED_IN" | "SELF"`
  - `occupation.ts`：`OCCUPATIONS`（八个枚举，顺序同简报 §3.6）、`type Occupation`、`occupationLabel(o: Occupation): string`（医师、护士、药师、医技、科研人员、学生、行业从业者、其他）
  - `profile-form.ts`：
    - `type OrganizationValue = { kind: "ror"; rorId: string; name: string; detail: string | null } | { kind: "free"; name: string }`
    - `interface ProfileFormValues { displayName: string; bio: string; links: { title: string; url: string }[]; occupation: Occupation | ""; jobTitle: string; organization: OrganizationValue | null; department: string; researchInterests: { ui: string; name: string }[] }`
    - `toFormValues(p: MyProfile): ProfileFormValues`、`toUpdateRequest(v: ProfileFormValues): UpdateProfileRequest`（空串 → `null`；链接空标题 → `null`；`ror` → `{ rorId }`、`free` → `{ name }`；关注领域 → UI 数组）
    - `profileFormSchema`（Zod，规则同 spec §9.3 与 release spec 2.1.2，文案用简报 §4.5）
    - `formPathOf(field: string): string | null`（`"links[1].url"` → `"links.1.url"`，`"organization.rorId"` / `"organization.name"` → `"organization"`，`"researchInterests[3]"` → `"researchInterests.3"`，其余同名或 `null`）
    - `profileFieldMessage(field: string, code: string | null): string`（简报 §4.5 整张表）
  - `profile.ts`：`fetchMyProfile(): Promise<MyProfile | "signed-out">`、`fetchMyPrivacy(): Promise<PrivacySettings | "signed-out">`（其他失败抛错）
  - `profile-actions.ts`：`saveProfile(values: ProfileFormValues): Promise<SaveProfileResult>`；`SaveProfileResult = { ok: true; profile: MyProfile } | { ok: false; reason: "invalid-fields"; fieldErrors: FieldError[] } | { ok: false; reason: "catalog-unavailable" | "conflict" | "pending-deletion" | "signed-out" | "unavailable" }`

- [ ] **Step 1: 写失败测试**：
  - 转换来回：GET 体 → 表单值 → 请求体，字段一一对应；`displayName: null` ↔ `""`；ROR 机构请求体只有 `rorId`。
  - `profileFormSchema`：昵称 1 字 → `昵称至少 2 个字`、31 字素 → `昵称最多 30 个字`、空 → 通过；简介 4 行 → `简介最多 3 行`；网址 `ftp://x` → `请输入以 http:// 或 https:// 开头的完整网址`；第二条与第一条网址大小写不同但相同 → `这条网址和上面重复了`；6 条链接 → `最多 5 条链接`；11 个关注领域 → `最多 10 个`。
  - `formPathOf` 与 `profileFieldMessage` 表驱动。
  - `saveProfile`：PUT 请求体形状对；200 → `ok`；422 → `invalid-fields`；503 → `catalog-unavailable`；409 只有 `CONFLICT` → **重试一次**（`fetch` 调两次），第二次 200 → `ok`、第二次仍 409 → `conflict`；409 `PENDING_DELETION` → 不重试、`pending-deletion`；401 → `signed-out` 且清 Cookie；入参形状不对 → `unavailable` 不打网关。
  - `fetchMyProfile`：200 → 解析值；401 → `"signed-out"`；500 → 抛错。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现**。
- [ ] **Step 4: 跑测试确认通过**：`pnpm exec vitest run tests/lib/identity`。
- [ ] **Step 5: 提交**：`feat(portal): 个人资料的读取、表单转换与保存动作 (PAP-101)`。

### Task 16: 机构与 MeSH 建议接口转发

**Files:**
- Create: `patra-portal/src/lib/suggest-query.ts`（`normalizeSuggestQuery`，不带 server-only）、`src/lib/portal-api/suggest.ts`、`src/lib/portal-api/suggest-query-hooks.ts`、`src/app/api/organizations/suggest/route.ts`、`src/app/api/mesh/suggest/route.ts`
- Modify: `patra-portal/src/lib/query-keys.ts`
- Test: `tests/lib/suggest-query.test.ts`、`tests/lib/portal-api/suggest.test.ts`、`tests/app/api/suggest-routes.test.ts`

**Interfaces:**
- Produces:
  - `normalizeSuggestQuery(q: string): string`（NFKC、去首尾空白、折叠连续空白）；`suggestQueryLength(q: string): number`（码点数）
  - `interface OrganizationSuggestion { rorId: string; displayName: string; zhName: string | null; matchedName: string | null; cityName: string | null; countryName: string | null; types: string[] }`（`countryName` 在服务端由 `countryName(countryCode)` 算好，避免两端 ICU 不一致）
  - `interface MeshSuggestion { ui: string; name: string; matchedTerm: string | null; treeNumbers: string[] }`
  - `fetchOrganizationSuggestions(q: string): Promise<OrganizationSuggestion[]>`、`fetchMeshSuggestions(q: string): Promise<MeshSuggestion[]>`（`limit=10`，非 2xx 抛错）
  - Route Handler 响应 `{ items: [...] }`；`queryKeys.organizationSuggest(q)`、`queryKeys.meshSuggest(q)`；hooks `useOrganizationSuggestQuery(q)`、`useMeshSuggestQuery(q)`（`q` 为空串时 `enabled: false`）

- [ ] **Step 1: 写失败测试**：`normalizeSuggestQuery("  北京　协和 ")` → `"北京 协和"`；`"ＡＢ"` → `"AB"`；路由：`q` 归一后 1 个码点或 101 个码点 → `{ items: [] }` 且不打网关；正常 → 网关 `/patra-catalog/portal/organizations/suggest?q=…&limit=10`，不带 `X-Forwarded-For`；`countryCode: "CN"` → `countryName: "中国"`；网关 500 → 502 `{ items: [] }`。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现**（照 `src/app/api/venues/suggest/route.ts` 的写法）。
- [ ] **Step 4: 跑测试确认通过**：`pnpm exec vitest run tests/lib tests/app/api`。
- [ ] **Step 5: 提交**：`feat(portal): 机构与 MeSH 建议接口转发 (PAP-101)`。

### Task 17: base-ui 原语（对话框、组合框、滑块）与新依赖

**Files:**
- Create: `patra-portal/src/components/ui/dialog.tsx`、`src/components/ui/combobox.tsx`、`src/components/ui/slider.tsx`
- Modify: `patra-portal/package.json`、`pnpm-lock.yaml`
- Test: `tests/components/ui/dialog.test.tsx`、`combobox.test.tsx`、`slider.test.tsx`

**Interfaces:**
- Produces（先读 `node_modules/@base-ui/react/{dialog,combobox,slider}` 的类型再定 props，按 `dropdown-menu.tsx` 的写法只留用到的部件、颜色换 token）：
  - `dialog.tsx`：`Dialog`、`DialogContent({ title: string; description?: ReactNode; size?: "sm" | "md" | "lg"; children; dismissible?: boolean })`（桌面居中，`sm` 400 / `md` 480 / `lg` 520px；≤ 880px 全屏底部抽屉；`dismissible={false}` 时 `Esc` 与点遮罩不关，用于提交中）、`DialogClose`
  - `combobox.tsx`：`ComboboxRoot`、`ComboboxInput`、`ComboboxPopup`（含顶部进度条槽位）、`ComboboxList`、`ComboboxItem`、`ComboboxStatus`（`aria-live`）
  - `slider.tsx`：`Slider({ value: number; min: number; max: number; step: number; onValueChange; "aria-label": string })`

- [ ] **Step 1: 装依赖**：`pnpm add react-easy-crop@6.2.4 @dnd-kit/core@6.3.1 @dnd-kit/sortable@10.0.0 @dnd-kit/utilities@3.2.2`（本机走国内镜像有问题时按 memory 的代理说明处理）。完成后提醒用户：本 Task 改了 `package.json`（不是 Gradle 脚本，IDEA 不用重新加载）。
- [ ] **Step 2: 写失败测试**：对话框打开后焦点在第一个输入框、`Esc` 关闭并把焦点还给触发按钮、`dismissible={false}` 时 `Esc` 不关；组合框 `role="combobox"`、`aria-expanded` 随打开变化、方向键 + 回车选中触发 `onValueChange`；滑块 `role="slider"`、方向键改变值。
- [ ] **Step 3: 跑测试确认失败**。
- [ ] **Step 4: 实现**。
- [ ] **Step 5: 跑测试确认通过**：`pnpm exec vitest run tests/components/ui tests/design-system`。
- [ ] **Step 6: 提交**：`feat(portal): 加入对话框、组合框、滑块原语与裁剪、拖动排序依赖 (PAP-101)`。

### Task 18: 个人资料表单（公开资料、专业背景、保存条）

**Files:**
- Create: `patra-portal/src/app/settings/profile/page.tsx`、`src/components/settings/profile/ProfileForm.tsx`、`LinkListField.tsx`、`CharCounter.tsx`、`src/components/settings/StickySaveBar.tsx`
- Modify: `patra-portal/src/components/auth/FormField.tsx`（外框的选择器扩到 `textarea` / `select`：`has-[:is(input,textarea,select):focus]` 等，`textarea` 时取消固定高度）、`src/components/auth/FormAlert.tsx`（加 `tone?: "danger" | "warning"`，`warning` 用 `bg-status-warning-bg` + `text-amber-700` 图标，供 amber 提示行）
- Test: `tests/components/settings/profile/ProfileForm.test.tsx`、`LinkListField.test.tsx`、`CharCounter.test.tsx`、`tests/components/settings/StickySaveBar.test.tsx`

**Interfaces:**
- Consumes: Task 14（`requireUser`、`SettingsCard`）、Task 15（全部）
- Produces: `<ProfileForm initial: MyProfile; user: CurrentUser; privacy: PrivacySettings; avatarSlot: ReactNode; organizationField: (props) => ReactNode; interestsField: (props) => ReactNode />`——机构、关注领域、头像在 Task 20 / 21 / 22 接入；本 Task 先放受控的占位（机构用纯文本输入写入 `{ kind: "free" }`，关注领域只渲染已选列表），接入后删掉占位。

- [ ] **Step 1: 写失败测试**（简报 §3.6、§4.5）：
  - 昵称占位符是当前用户名、提示文案对；简介计数 `22 / 200`，到 160 字素变 `text-amber-700`、超 200 变 `text-status-danger` 并显示字段错误。
  - 链接：添加到 5 条后「添加链接」换成 `最多 5 条链接`；删除一行；邮箱未验证时卡片内 `验证邮箱后，链接才会展示在主页上`。
  - 专业背景说明 `当前：所有人可见` / `当前：仅自己可见` + `更改` 链到 `/settings/privacy`；身份下拉有 `不填写` 与八项。
  - 未改动时没有保存条；改动后出现 `有未保存的修改` + `放弃` + `保存`；`放弃` 恢复初值。
  - 保存成功：toast `资料已保存`、保存条消失、`router.refresh()` 被调用、currentUser 失效。
  - `invalid-fields` `links[1].url`/`INVALID_FORMAT` → 第二行网址下方文案、焦点在该输入框。
  - `catalog-unavailable` → 提示条 `机构或主题词暂时无法校验，请稍后再保存。你填的内容都还在。`，输入保留；`conflict` → `资料没有保存成功，请再保存一次`；`unavailable` → `没有保存成功，请稍后再试`；`pending-deletion` → `router.replace("/account/deletion-pending?next=%2Fsettings%2Fprofile")`；`signed-out` → `router.replace("/login?next=%2Fsettings%2Fprofile")`。
  - 保存中按钮 `保存中…`、字段只读。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现**；页面：`requireUser("/settings/profile")` 后 `Promise.all([fetchMyProfile(), fetchMyPrivacy()])`，任一 `"signed-out"` → `redirect` 登录；metadata 标题 `个人资料 · 设置 · Patra`。
- [ ] **Step 4: 跑测试确认通过**：`pnpm exec vitest run tests/components/settings tests/components/auth`。
- [ ] **Step 5: 提交**：`feat(portal): 个人资料页的公开资料、专业背景与吸底保存条 (PAP-101)`。

### Task 19: 未保存离开提醒

**Files:**
- Create: `patra-portal/src/components/settings/LeaveGuard.tsx`
- Modify: `src/components/settings/profile/ProfileForm.tsx`（`<LeaveGuard when={isDirty && !submitting} />`）
- Test: `tests/components/settings/LeaveGuard.test.tsx`

**Interfaces:**
- Produces: `<LeaveGuard when: boolean />`

- [ ] **Step 1: 写失败测试**：`when` 为真时点站内 `<a href="/papers">` → 不跳转、弹 `有未保存的修改，确定离开吗？`；点 `离开` → `router.push("/papers")`；点 `继续编辑` → 关闭、不跳；带 `metaKey` / `ctrlKey` / `shiftKey` / 中键、`target="_blank"`、外站链接、同一路径的锚点 → 不拦；`when` 为假 → 不拦；`beforeunload` 事件在 `when` 为真时被 `preventDefault`。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现**（`document.addEventListener("click", …, true)`；确认框用 Task 17 的 `DialogContent size="sm"`）。
- [ ] **Step 4: 跑测试确认通过**：`pnpm exec vitest run tests/components/settings`。
- [ ] **Step 5: 提交**：`feat(portal): 资料有未保存修改时离开前提醒 (PAP-101)`。

### Task 20: 机构选择器

**Files:**
- Create: `patra-portal/src/components/settings/profile/OrganizationPicker.tsx`、`src/lib/identity/suggest-labels.ts`
- Modify: `ProfileForm.tsx`（替换 Task 18 的机构占位）
- Test: `tests/components/settings/profile/OrganizationPicker.test.tsx`、`tests/lib/identity/suggest-labels.test.ts`

**Interfaces:**
- Consumes: `useOrganizationSuggestQuery`、`normalizeSuggestQuery`（Task 16）、`OrganizationValue`（Task 15）、组合框原语（Task 17）
- Produces:
  - `<OrganizationPicker value: OrganizationValue | null; onChange; error?: string; readOnly?: boolean; id: string />`
  - `organizationTypeLabel(types: string[]): string | null`（`healthcare` 医疗、`education` 教育、`facility` 设施、`government` 政府、`company` 企业、`nonprofit` 非营利；`funder` 与其他不显示；取第一个可显示的）
  - `organizationDetail(s: OrganizationSuggestion): string | null`（有 `zhName` 时 `displayName`，再接 `· 城市 · 国家`，有的才接）

- [ ] **Step 1: 写失败测试**（简报 §3.8）：输入 1 个字 → 下拉 `再输入一个字`、不请求；2 个字 → 250ms 后请求（假时钟）；候选主行 `北京协和医院`、副行 `Peking Union Medical College Hospital · Beijing · 中国`、类型小标 `医疗`；`matchedName` 与主副行都不同 → 副行末尾 `· 经「PUMCH」命中`；末项 `直接使用「…」` 选中 → `onChange({ kind: "free", name })`；201 字素时该项禁用并显示 `名称最长 200 字`；无结果 → `没有找到匹配的机构`；接口失败 → `机构搜索暂时不可用，可以直接填写名称`；选中 ROR → 卡片带 `ROR` 胶囊（`title="来自 ROR 机构库"`）；回显的 `detail: null` → 副行 `ROR 机构库`；手填卡片副行 `手动填写`、无胶囊；`更换` 回输入态并预填名称；`清除`（名称「清除机构」）→ `onChange(null)`；`Esc` 收起、再按清空。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现**（命中部分加粗：按归一后的输入在主行里做不区分大小写的子串匹配，找不到就不加粗）。
- [ ] **Step 4: 跑测试确认通过**：`pnpm exec vitest run tests/components/settings tests/lib/identity`。
- [ ] **Step 5: 提交**：`feat(portal): 机构选择器（ROR 搜索与手动填写） (PAP-101)`。

### Task 21: 关注领域选择器

**Files:**
- Create: `patra-portal/src/components/settings/profile/MeshInterestPicker.tsx`
- Modify: `src/lib/identity/suggest-labels.ts`、`ProfileForm.tsx`（替换 Task 18 的占位）
- Test: `tests/components/settings/profile/MeshInterestPicker.test.tsx`、`tests/lib/identity/suggest-labels.test.ts`

**Interfaces:**
- Consumes: `useMeshSuggestQuery`（Task 16）、组合框原语（Task 17）、`@dnd-kit/sortable`
- Produces:
  - `<MeshInterestPicker value: { ui: string; name: string }[]; onChange; errors?: Record<number, string>; readOnly?: boolean; id: string />`
  - `meshCategoryLabel(treeNumbers: string[]): string | null`（第一个树号首字母：A 解剖、B 生物、C 疾病、D 化学品和药物、E 技术与设备、F 精神病学与心理学、G 现象与过程、H 学科与职业、I 人类学 · 教育 · 社会、J 技术 · 工业 · 农业、K 人文、L 信息科学、M 人群、N 卫生保健、V 出版特征、Z 地理）
  - `hasCjk(value: string): boolean`

- [ ] **Step 1: 写失败测试**（简报 §3.9）：输入 `心衰` → 不请求、下拉 `关注领域只支持英文主题词，请用英文搜索`；`h` → 不请求；`heart` → 请求；候选 `via Cancer`、大类 `疾病`；已选项禁用并标 `已添加`；选中追加到末尾、输入清空且仍聚焦；计数 `3 / 10`；满 10 个时输入框禁用、占位 `最多 10 个，先移除一些再添加`；`×` 移除；聚焦胶囊按 `Alt+ArrowRight` / `Alt+ArrowDown` 后移一位、`Alt+ArrowLeft` / `Alt+ArrowUp` 前移；窄屏（`matchMedia("(max-width: 880px)")` 为真）时胶囊左侧 `⋮⋮` 按钮打开 `上移` / `下移` / `移除` 菜单；`errors[1]` 时第二个胶囊红描边、下方文案；无结果 `没有找到匹配的主题词`；失败 `主题词搜索暂时不可用，请稍后再试`。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现**（拖动用 `DndContext` + `SortableContext` + `rectSortingStrategy`，只在桌面启用 `PointerSensor`；键盘排序用自写的 `Alt+方向键`，不用 dnd-kit 的 KeyboardSensor）。
- [ ] **Step 4: 跑测试确认通过**：`pnpm exec vitest run tests/components/settings tests/lib/identity`。
- [ ] **Step 5: 提交**：`feat(portal): 关注领域选择器（MeSH 搜索、排序） (PAP-101)`。

### Task 22: 头像卡片与裁剪上传

**Files:**
- Create: `patra-portal/src/components/settings/profile/AvatarCard.tsx`、`AvatarCropDialog.tsx`、`src/lib/identity/avatar-file.ts`
- Modify: `ProfileForm.tsx` 所在页面（把 `AvatarCard` 作为 `avatarSlot` 传入）
- Test: `tests/lib/identity/avatar-file.test.ts`、`tests/components/settings/profile/AvatarCard.test.tsx`、`AvatarCropDialog.test.tsx`

**Interfaces:**
- Consumes: `Avatar`（Task 9）、`uploadAvatar` / `removeAvatar`（Task 13）、对话框与滑块（Task 17）、`react-easy-crop`
- Produces:
  - `type AvatarFileIssue = "type" | "size" | "small" | "unreadable"`；`checkAvatarFile(file: File, decode?: (f: File) => Promise<{ width: number; height: number }>): Promise<AvatarFileIssue | null>`（默认用 `createImageBitmap`；类型只认 `image/jpeg`、`image/png`、`image/webp`；> 5 × 1024 × 1024 字节；短边 < 128）
  - `exportCrop(image: HTMLImageElement, area: { x: number; y: number; width: number; height: number }, sourceType: string): Promise<Blob>`（边长 `min(area.width, 1024)`；JPEG 源 → `image/jpeg` 0.92，其余 → `image/png`）
  - `avatarUploadMessage(result: AvatarMutationResult): string`（简报 §4.6 + PAP-97 评论：`too-large` 图片不能超过 5 MB、`UNSUPPORTED_TYPE` 只支持 JPG、PNG 或 WebP 图片、`TOO_SMALL` 图片太小了，短边至少 128 像素、`UNREADABLE` / `TOO_MANY_PIXELS` 这个文件打不开，换一张试试、其他 头像没有上传成功，请稍后再试）

- [ ] **Step 1: 写失败测试**：`checkAvatarFile` 四种问题 + 通过（`decode` 注入）；`AvatarCard` 选 GIF → 卡片下方 `只支持 JPG、PNG 或 WebP 图片`，不打开对话框；通过 → 打开 `调整头像` 对话框；有头像时才出现 `移除`，点了 → toast `已恢复默认头像`、currentUser 头像置空；移除失败 → `没有移除成功，请稍后再试`；对话框（`react-easy-crop` 用 `vi.mock` 换成一个会回调 `onCropComplete` 的桩）：点 `使用这张头像` → 按钮 `上传中…`、成功后关闭、toast `头像已更新`、currentUser 的 `avatarUrl` 更新；`rejected`/`TOO_SMALL` → 对话框不关、提示条对应文案；`pending-deletion` / `signed-out` 跳转同 Task 18；有 `主页`、`导航栏` 两个预览标签。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现**（裁剪：`aspect={1}`、`cropShape="round"`、`showGrid={false}`、`zoom` 受 `Slider` 控制，最大倍数 `min(width, height) / 128`；键盘 `+` / `-` 缩放，方向键平移交给 react-easy-crop 自带的键盘支持并设 `keyboardStep={4}`，Shift 时 16）。
- [ ] **Step 4: 跑测试确认通过**：`pnpm exec vitest run tests/components/settings tests/lib/identity`。
- [ ] **Step 5: 提交**：`feat(portal): 头像裁剪上传与移除 (PAP-101)`。

### Task 23: 隐私页

**Files:**
- Create: `patra-portal/src/app/settings/privacy/page.tsx`、`src/components/settings/privacy/PrivacyForm.tsx`
- Modify: `src/lib/identity/profile-actions.ts`（加 `savePrivacy`）
- Test: `tests/lib/identity/profile-actions.test.ts`、`tests/components/settings/privacy/PrivacyForm.test.tsx`

**Interfaces:**
- Consumes: Task 14、15
- Produces: `savePrivacy(settings: PrivacySettings): Promise<SavePrivacyResult>`；`SavePrivacyResult = { ok: true; settings: PrivacySettings } | { ok: false; reason: "pending-deletion" | "signed-out" | "unavailable" }`

- [ ] **Step 1: 写失败测试**（简报 §3.12）：三组控件与说明文案；改主页可见性 → `savePrivacy` 收到三项完整值、旁边 `保存中…` → `已保存`（2 秒后消失）；失败 → 回到原值、`没有保存成功，请重试`；`rolls_back_to_last_confirmed_value_after_failure_in_a_burst`（Review Focus 5）：连点 公开 → 仅登录用户 → 仅自己，第二次失败、第三次成功 → 最终为「仅自己」；第三次失败 → 回到最后一次成功的值；提交串行（同一时刻只有一个请求在途）；邮箱未验证 → 顶部 amber 说明；主页不是公开时开关下 amber 说明；`pending-deletion` / `signed-out` 跳转；页底 `查看我的主页 ↗`。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现**（原生 `RADIO_INPUT`、`SWITCH_INPUT`；页面标题 `隐私 · 设置 · Patra`）。
- [ ] **Step 4: 跑测试确认通过**：`pnpm exec vitest run tests/components/settings tests/lib/identity`。
- [ ] **Step 5: 提交**：`feat(portal): 隐私设置页（改动即时保存） (PAP-101)`。

### Task 24: 公开主页 `/u/[username]`

**Files:**
- Create: `patra-portal/src/lib/identity/public-profile.ts`、`src/app/u/[username]/page.tsx`、`src/app/u/[username]/not-found.tsx`、`src/components/profile/ProfileHeader.tsx`、`ProfileAbout.tsx`、`ProfileVisibilityNotice.tsx`
- Modify: `src/components/portal/status/NotFoundState.tsx`（加 `kind="user"`，标题与正文按 kind 取）、`tests/app/route-structure.test.ts`
- Test: `tests/lib/identity/public-profile.test.ts`、`tests/components/profile/*.test.tsx`、`tests/app/user-page.test.tsx`、`tests/components/portal/status-screens.test.tsx`

**Interfaces:**
- Consumes: `callIdentity`（`auth: "optional"`）、`lookupSession`、`fetchMyPrivacy`、`Avatar`、`ResendVerificationButton`、`occupationLabel`
- Produces:
  - `interface PublicProfile`（spec §3 / 契约 §7 的字段）；`type PublicProfileLookup = { kind: "found"; profile: PublicProfile } | { kind: "moved"; username: string } | { kind: "not-found" }`
  - `fetchPublicProfile = cache(async (username: string): Promise<PublicProfileLookup>)`（非法用户名 → `not-found` 不打网关；200 / 301 / 308 / 404；其他抛错）
  - `joinedLabel(joinedAt: string): string`（`"2026-10"` → `加入于 2026 年 10 月`）、`linkLabel(link: { title: string | null; url: string }): string`（标题或域名）、`isSafeHref(url: string): boolean`

- [ ] **Step 1: 写失败测试**（简报 §3.13、§4.11 每个状态 ID 一条）：
  - 取数：`rejects_invalid_username_without_calling_gateway`（Review Focus 4，如 `a.b`、`%2F`）；`passes_uppercase_username_through`；带有效 Cookie 时请求带令牌；301 体 `{ username: "new-name" }` → `moved`；404 → `not-found`；500 → 抛错。
  - 页面：`moved` → `redirect("/u/new-name")`；`not-found` → `notFound()`；`profile.public-full`（匿名：头部、专业背景、关注领域、链接；链接 `rel="nofollow noopener noreferrer ugc"`、`target="_blank"`；标题为空显示域名；`javascript:` 链接不渲染为 `href`）；`profile.public-minimal`；`profile.self-empty`（`你的主页还很空。` + `完善资料`）；`profile.self-unverified`（提示条 + 重发按钮 + `验证邮箱后展示链接`）；`profile.self-private`、`profile.self-signed-in`（文案 + `更改可见性`）；`profile.self-pro-hidden`（`仅你可见`，此时取了 `/me/privacy`）；`profile.pro-hidden`（`professional: null` 不渲染）；`profile.long`（30 字素昵称有两行截断类）；别人视角不请求 `/me/privacy`；关注领域胶囊链到 `/papers?q=Heart%20Failure`；本人视角 `编辑资料` 链到 `/settings/profile`。
  - `generateMetadata`：标题 `陈宇 (@chen-yu) · Patra`；`indexable: false` → `robots.index === false`。
  - `not-found.tsx` 导出的标题 `没有找到这个用户 · Patra`；`NotFoundState kind="user"` 标题 `没有找到这个用户`、正文 `地址可能有误，或者这个主页没有对你公开。`。
  - `route-structure`：`src/app/u/[username]/loading.tsx` 不存在。
- [ ] **Step 2: 跑测试确认失败**。
- [ ] **Step 3: 实现**（桌面头像 128 / 移动 88；昵称 `font-serif text-display-3 line-clamp-2`；两栏 ≤ 880px 改单列、专业背景在上；不渲染三处留位）。
- [ ] **Step 4: 跑测试确认通过**：`pnpm exec vitest run tests/lib/identity tests/components/profile tests/app tests/components/portal`。
- [ ] **Step 5: 提交**：`feat(portal): 公开主页 /u/[username]（三种视角、404、旧名跳转） (PAP-101)`。

### Task 25: 文档、全量门禁与实测

**Files:**
- Modify: `patra-portal/README.md`（转发链路与预加载模块、设备标识 Cookie、新 Route Handler 一览）、`docs/patra/design/snapshots/README.md`（补 `2026-10-22-user-center-hifi.zip` 一行，照上面各行的写法）、`patra-portal/docs/design-briefs/v0.12/user-center.md`（§10 末尾记一笔：资料保存的 409 改为自动重试并保留输入，见工程 spec §9.3）、spec（第 24 节式的「实施结果」与实测结论）

- [ ] **Step 1: 全量门禁**：`pnpm lint && pnpm typecheck && pnpm test`，全绿（iCloud 负载高导致随机超时时按 memory 等 loadavg 降下来再跑，不改超时）。
- [ ] **Step 2: 预加载模块实测（spec §15.1）**：`pnpm build`（会和正在跑的 dev 并存，Next 16 输出目录分离）；临时加一个打印 `headers().get("x-forwarded-for")` 的 Route Handler（实测后删除、不提交），`cp server/strip-forwarded-for.mjs .next/standalone/ && cd .next/standalone && PORT=4100 node --import ./strip-forwarded-for.mjs server.js`，`curl -H 'X-Forwarded-For: 203.0.113.9' http://127.0.0.1:4100/api/<临时路由>` 应得 `127.0.0.1`（或 `::1` / `::ffff:127.0.0.1`）；不带 `--import` 再跑一次应得 `203.0.113.9`（证明测试有效）。
- [ ] **Step 3: 浏览器走查**：`preview_start portal-dev`（连 mini 网关），自己注册新测试账号（凭据不写进仓库和对话）。桌面 1440×900 与移动 375×812 各走一遍：注册（规则提示、409）、导航栏与菜单、横幅重发（Mailpit 收信 `http://100.103.73.27:8025`）、资料页全部字段（ROR 命中与手填各一、关注领域增删排序、头像裁剪上传与移除、离开提醒）、隐私三项、`/u/<用户名>` 匿名 / 本人 / 另一账号三种视角、404。对照画板截图（计划头部 Spec 一行说明的离线渲染），有出入就修并补测试。
- [ ] **Step 4: 跑现有 e2e 的注册用例**：`PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528 pnpm exec playwright test tests/e2e/auth.spec.ts --workers=1`，全过。
- [ ] **Step 5: 提交**：`docs(portal): 用户中心的转发链路、快照索引与实施结果 (PAP-101)`。
- [ ] **Step 6: 停在本地请用户验收**：汇报改动与实测结果，附桌面 / 移动截图；不 push、不开 PR（等 PAP-102 一起）。用户验收通过后：Linear PAP-101 勾 To-Do / AC，评论附 AC 证据与 spec §4.2 的偏离说明（catalog 读不转发 IP / UA），推到 Review。
