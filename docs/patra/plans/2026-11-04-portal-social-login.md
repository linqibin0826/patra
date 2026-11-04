# 门户第三方登录 Implementation Plan

> **For agentic workers:** 用 superpowers:executing-plans 在当前会话内联执行，不派子代理。

**Goal:** 门户接上 Google、GitHub、ORCID 登录、首次注册和设置页绑定解绑（PAP-186、PAP-187）。

**Architecture:** BFF：两个 GET Route Handler 转发授权和回调，Cookie 全部由门户写；首次注册用服务端取数的页面加 Route Handler；绑定解绑用 Server Action 加 `withReauth`。

**Tech Stack:** Next.js 16 App Router、React 19、TanStack Query、RHF + Zod、Vitest。

**Spec:** `docs/patra/specs/2026-11-04-portal-social-login-design.md`

## Global Constraints

- 回调路由是 `/api/auth/oauth/callback/{provider}`。
- Cookie 名、属性见 spec 第 2 节；`Secure` 只看 `secureEnabled()`。
- `ticket`、`code`、`state`、`sessionToken` 不进日志，`ticket` 不进 URL。
- 现有密码再认证流程不变：`ReauthDialog.test.tsx`、`stacked-dialogs.test.tsx` 不改断言仍通过。
- 全量测试用 `pnpm exec vitest run --maxWorkers=2`；提交前 `pnpm lint`、`pnpm typecheck`。
- 每个 Task 先写失败测试并看到它失败，再实现。

## Review Focus

- 查询串里 `state` 出现两次：不转发（Task 3）。
- `patra_oauth_return` 被改坏或 `next` 指向站外：回 `/login`，`next` 回退 `/`（Task 3）。
- 从第三方按后退回到往返缓存的页面：入口按钮不能一直「正在前往」（Task 4）。
- 手工改 `?oauth=xxx&provider=evil`：不显示提示（Task 2）。
- 没有密码的用户打开设置页：看不到任何填不了的密码框（Task 7、Task 8）。

---

### Task 1: 基础设施：请求头、`hasPassword`、Cookie、公网判断

**Files:**
- Modify: `src/lib/gateway.ts`、`src/lib/identity/user-schema.ts`、`tests/fixtures/user.ts`、`src/lib/auth/next-path.ts`、`src/proxy.ts`、`.env.example`
- Create: `src/lib/auth/oauth-providers.ts`、`src/lib/auth/oauth-host.ts`（Cookie 函数放进 `session-cookie.ts`：守护测试只允许它调 `cookies()`）
- Test: `tests/lib/gateway.test.ts`（已有则追加）、`tests/lib/auth/oauth-cookies.test.ts`、`tests/lib/auth/oauth-host.test.ts`、`tests/lib/auth/next-path.test.ts`

**Interfaces:**
- `GatewayRequestInit.headers?: Record<string, string>`
- `PROVIDERS`、`Provider`、`isProvider(value)`、`providerName(provider)`
- `writeOAuthFlowCookies(state, ret)`、`readOAuthFlow()`、`clearOAuthFlowCookies()`；`writeTicketCookie`、`readTicket`、`clearTicketCookie`；`writeSignedInFlag`
- `isOAuthHost(headers: Headers): boolean`、`readOAuthEnabled(): Promise<boolean>`

- [ ] 写失败测试：Set-Cookie 原文含 Path、Max-Age、HttpOnly、Secure 开关；白名单默认值与覆盖；`/register/complete` 是专注页。
- [ ] 运行确认失败。
- [ ] 实现，`userSchema` 加必填 `hasPassword`，fixture 同步。
- [ ] 运行通过，提交。

### Task 2: 提示参数与文案

**Files:**
- Create: `src/lib/auth/oauth-notice.ts`
- Modify: `src/lib/auth/messages.ts`
- Test: `tests/lib/auth/oauth-notice.test.ts`

**Interfaces:**
- `OAuthNoticeKind`、`OAuthNotice { kind, provider }`、`parseOAuthNotice(params)`、`oauthNoticeHref(path, notice)`
- `loginNoticeAlert(notice)`、`linkNoticeAlert(notice)` → `{ tone, text } | null`

- [ ] 写失败测试：每种 `kind` 的文案与画板一致；非法 `kind`、`provider` 返回 null；数组参数取第一个。
- [ ] 运行确认失败。
- [ ] 实现。
- [ ] 运行通过，提交。

### Task 3: 发起与回调路由

**Files:**
- Create: `src/lib/identity/oauth-login.ts`、`src/app/api/auth/oauth/[provider]/start/route.ts`、`src/app/api/auth/oauth/callback/[provider]/route.ts`
- Test: `tests/app/api/oauth-start.test.ts`、`tests/app/api/oauth-callback.test.ts`

**Interfaces:**
- `startProviderLogin(provider, from, next): Promise<string>`（返回要跳去的地址）
- `finishProviderCallback(provider, query: URLSearchParams): Promise<string>`
- `rememberOAuthFlow(authorizationUrl, ret): Promise<boolean>`（Task 7 复用）

- [ ] 写失败测试：覆盖 spec 第 3 节表格的每一行；缺 Cookie、不相等、`state` 两个都不调 `fetch`；转发头（`X-Patra-Device-Id`、`Authorization`、`X-Forwarded-For`）；日志不含 `code`、`state`、令牌；非公网域名不请求网关。
- [ ] 运行确认失败。
- [ ] 实现。
- [ ] 运行通过，提交。

### Task 4: 登录页、注册页入口

**Files:**
- Create: `src/components/auth/ProviderEntries.tsx`、`src/components/auth/ProviderMark.tsx`、`src/components/auth/use-oauth-notice.ts`、`src/components/auth/SignedInToast.tsx`、`public/oauth/*`
- Modify: `src/app/login/page.tsx`、`src/app/register/page.tsx`、`src/components/auth/LoginForm.tsx`、`src/components/auth/RegisterForm.tsx`、`src/app/layout.tsx`
- Test: `tests/components/auth/ProviderEntries.test.tsx`、`tests/app/auth-pages.test.tsx`、`tests/components/auth/SignedInToast.test.tsx`

- [ ] 写失败测试：公网有三个链接且 `href` 带 `from`、`next`，tailnet 没有且副文案不提第三方；点击后三个都 `aria-disabled`，`pageshow` 后复位；提示条显示后地址栏参数被清；signed-in 标记弹一次并清除。
- [ ] 运行确认失败。
- [ ] 实现，样式照 `.pt-sso-*`。
- [ ] 运行通过，提交。

### Task 5: 补填的服务端部分

**Files:**
- Create: `src/lib/identity/oauth-registration.ts`、`src/app/api/auth/oauth/registration/code/route.ts`、`src/app/api/auth/oauth/registration/complete/route.ts`、`src/lib/auth/post-registration.ts`
- Test: `tests/lib/identity/oauth-registration.test.ts`、`tests/app/api/oauth-registration-routes.test.ts`

**Interfaces:**
- `fetchPendingRegistration(): Promise<PendingRegistration | "expired">`（其他失败抛错）
- `sendRegistrationCode(input): Promise<SendCodeResult>`、`completeRegistration(code): Promise<CompleteResult>`
- 结果原因：`expired`、`invalid-fields`、`email-taken`、`username-taken`、`throttled`、`code-invalid`、`code-expired`、`provider-taken`、`unavailable`

- [ ] 写失败测试：契约第 2.3 节每个状态码；请求头带 `X-Patra-Registration-Ticket`；没有 Cookie 不请求；完成成功写会话、清 ticket；跨站 403。
- [ ] 运行确认失败。
- [ ] 实现。
- [ ] 运行通过，提交。

### Task 6: 补填页与验证码页

**Files:**
- Create: `src/app/register/complete/page.tsx`、`src/components/auth/ProviderRegistrationFlow.tsx`、`src/components/auth/RegistrationFillForm.tsx`、`src/components/auth/RegistrationCodeForm.tsx`
- Modify: `src/lib/auth/form-schemas.ts`、`src/lib/auth/messages.ts`
- Test: `tests/components/auth/ProviderRegistrationFlow.test.tsx`、`tests/app/register-complete-page.test.tsx`

- [ ] 写失败测试：预填与提示文案；撞车块及「换一个邮箱」；验证码只接受 6 位数字；`INVALID`、`EXPIRED` 表现；回到补填步；重发倒计时；过期态；完成后跳 `next`。
- [ ] 运行确认失败。
- [ ] 实现。
- [ ] 运行通过，提交。

### Task 7: 设置页登录方式

**Files:**
- Create: `src/lib/identity/login-methods.ts`、`src/lib/identity/login-method-actions.ts`、`src/lib/login-methods-query.ts`、`src/app/api/me/login-methods/route.ts`、`src/components/settings/security/LoginMethodsCard.tsx`、`src/components/settings/security/LoginMethodRow.tsx`
- Modify: `src/app/settings/security/page.tsx`、`src/lib/query-keys.ts`、`src/lib/auth/messages.ts`
- Test: `tests/lib/identity/login-method-actions.test.ts`、`tests/app/api/me-login-methods.test.ts`、`tests/components/settings/LoginMethodsCard.test.tsx`、`tests/app/security-page.test.tsx`

**Interfaces:**
- `startProviderLink(provider): Promise<LinkResult>`、`unlinkProvider(provider): Promise<UnlinkResult>`；均含 `ReauthRequired`

- [ ] 写失败测试：六种账号状态的行显示；绑定与解绑的各结果；tailnet 隐藏绑定、保留解绑；唯一方式不可解绑；无密码不渲染 `ChangePasswordCard`。
- [ ] 运行确认失败。
- [ ] 实现。
- [ ] 运行通过，提交。

### Task 8: 再认证分支

**Files:**
- Create: `src/components/reauth/SetPasswordDialog.tsx`
- Modify: `src/components/reauth/ReauthDialog.tsx`、`src/lib/auth/messages.ts`
- Test: `tests/components/reauth/ReauthDialog.test.tsx`（只追加）、`tests/components/reauth/SetPasswordDialog.test.tsx`

- [ ] 写失败测试：无密码没有密码框；发送、限流、失败；发送后的已发送态与倒计时；关闭 `settle(false)`；有密码和未加载时保持原样。
- [ ] 运行确认失败。
- [ ] 实现。
- [ ] 运行通过，提交。

### Task 9: 隐私政策与收尾

**Files:**
- Modify: `src/app/privacy/page.tsx`、`tests/app/privacy-page.test.tsx`、`patra-portal/README.md`（如有环境变量表）

- [ ] 写失败测试：spec 第 9 节各条。
- [ ] 运行确认失败。
- [ ] 实现。
- [ ] 全量：`pnpm exec vitest run --maxWorkers=2`、`pnpm lint`、`pnpm typecheck`、`pnpm build`。
- [ ] 本地起页面，用接口桩对照画板检查桌面和手机布局。
- [ ] 提交并报告。
