# 门户人机验证实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task (Patra 约定内联执行，不派子代理). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 登录、注册、找回密码接入人机验证，厂商由 identity 决定；外网访问时取到浏览器真实 IP。

**Architecture:** 三个页面在服务端读当前厂商，作为属性传给表单。`HumanVerificationWidget` 按厂商选实现，`useHumanVerification` 管令牌和重置。Server Action 把令牌带给后端，并把三种验证错误映射成结果的 `reason`。

**Tech Stack:** Next.js 16、React 19、TypeScript、Vitest + Testing Library、Playwright。

**Spec:** [`docs/patra/specs/2026-11-05-portal-human-verification-design.md`](../specs/2026-11-05-portal-human-verification-design.md)，接口约定见 identity 工程设计第 3 节。

## Global Constraints

- 分支 `feat/v0.20-human-verification-portal`，基于后端分支。按任务本地提交，不推送。
- 线上 `action` 值：`login`、`register`、`password_reset`。
- 文案以 spec 第 5.4 节为准，全部放 `src/lib/auth/messages.ts`。
- 禁止 `any`、inline `style`、`../../`；业务组件单文件单组件；测试查询优先 `getByRole`；提交前 `pnpm lint`。
- Next 的 API 以 `node_modules/next/dist/docs/` 为准。
- 全量测试用 `pnpm exec vitest run --maxWorkers=2`。改了 RSC 取数要跑 `pnpm build`。

## Review Focus

1. **登录被要求验证时，已输入的密码不能丢**，改之前的 `onFailure` 会清空密码。测试放任务 4。
2. **`TrustTurnWidget` 收到别的来源或别的窗口的 `message`**：一律忽略，不能当成令牌。测试放任务 3。
3. **令牌过期或被重置后按钮要回到禁用**，不能拿旧令牌提交。测试放任务 3、任务 4。
4. **脚本加载失败后再次挂载**：能重新加载，不能卡在失败的 promise 上。测试放任务 3。
5. **`SetPasswordDialog` 读配置期间和读到 `null`**：发送按钮都不可用，关闭后再打开重新读。测试放任务 6。

---

### Task 1: 真实 IP（PAP-195）

**Files:** Modify `src/lib/client-origin.ts`、`patra-infra/docker/README.md`；Test `tests/lib/client-origin.test.ts`

- [ ] **Step 1: 写失败测试**：`uses_cf_connecting_ip_when_valid`（同时带 `x-forwarded-for` 时取前者）、`normalizes_ipv4_mapped_cf_connecting_ip`、`falls_back_when_cf_connecting_ip_is_invalid`（`not-an-ip`、含逗号）、`falls_back_when_cf_connecting_ip_is_missing`。
- [ ] **Step 2:** `pnpm exec vitest run tests/lib/client-origin.test.ts`。Expected: 新用例 FAIL。
- [ ] **Step 3:** `clientOriginFromHeaders` 改成 `clientIp(headers.get("cf-connecting-ip")) ?? clientIp(headers.get("x-forwarded-for"))`，更新注释。README 删掉“门户看到的来源都是 `192.168.97.1`”这条限制，写明来源和可伪造范围。
- [ ] **Step 4:** 再跑。Expected: PASS。
- [ ] **Step 5:** 提交 `feat(portal): 从 CF-Connecting-IP 取浏览器真实 IP (PAP-195)`。

### Task 2: 读取厂商与 Server Action

**Files:**
- Create: `src/lib/auth/human-verification.ts`、`src/lib/auth/human-verification-actions.ts`、`src/lib/identity/verification-failure.ts`
- Modify: `src/lib/auth/types.ts`、`src/lib/auth/actions.ts`、`src/lib/identity/token-actions.ts`、`src/lib/auth/messages.ts`
- Test: `tests/lib/auth/human-verification.test.ts`、`tests/lib/identity/verification-failure.test.ts`、`tests/lib/auth/actions.test.ts`、`tests/lib/identity/token-actions.test.ts`、`tests/lib/auth/messages.test.ts`

**Interfaces:**
- Produces:
  - `type HumanVerificationAction = "login" | "register" | "password_reset"`、`interface HumanVerificationConfig { provider: "turnstile" | "trustturn"; siteKey: string }`（放 `types.ts`）
  - `readHumanVerification(): Promise<HumanVerificationConfig | null>`（`server-only`，经 `callIdentity(path, { method: "GET" }, { auth: "optional" })`）
  - `fetchHumanVerification()`：`"use server"`，转调上面，给对话框用
  - `verificationFailureOf(problem: Problem): { ok: false; reason: "verification-required" | "verification-failed" | "verification-unavailable" } | null`
  - `Credentials.verificationToken?: string`；`requestPasswordReset(email: string, verificationToken?: string)`
  - `LoginResult`、`RegisterResult`、`ForgotResult` 增加这三个 `reason`
  - `VERIFICATION_MESSAGES = { required, failed, unavailable, loadFailed }`，`loginAlertMessage` 覆盖新 `reason`

- [ ] **Step 1: 写失败测试**
  - `human-verification`：`returns_config`；`returns_null_for_malformed_body`；`returns_null_for_empty_site_key`；`returns_null_for_unknown_provider`；`returns_null_when_gateway_down`。
  - `verification-failure`：422 带 `verificationToken`/`VERIFICATION_REQUIRED`、`VERIFICATION_FAILED`；503 带 trait；普通 422、普通 503 返回 `null`。
  - `actions`：`login_sends_verification_token`；`login_omits_token_when_absent`；三种 `reason` 的映射（登录和注册各一组）；`verification_errors_are_not_logged_as_unexpected`。
  - `token-actions`：`forgot_sends_verification_token`；三种 `reason`。
- [ ] **Step 2:** 跑这些测试。Expected: FAIL。
- [ ] **Step 3:** 实现。`login`、`register` 在判断 422 之前先看 `verificationFailureOf(parseProblem(...))`，503 也要经过它。`credentialsSchema` 增加可选的 `verificationToken`。令牌不进日志。
- [ ] **Step 4:** 再跑。Expected: PASS。
- [ ] **Step 5:** 提交 `feat(portal): 读取人机验证厂商并把令牌带给后端 (PAP-194)`。

### Task 3: 两家组件与状态

**Files:** Create `src/components/auth/human-verification/{HumanVerificationWidget,TurnstileWidget,TrustTurnWidget}.tsx`、`use-human-verification.ts`；Test 同名放 `tests/components/auth/human-verification/`

**Interfaces:**
- Consumes: 任务 2 的 `HumanVerificationConfig`、`HumanVerificationAction`、`VERIFICATION_MESSAGES`。
- Produces:
  - `useHumanVerification(initiallyRequired: boolean)` 返回 `{ required, token, resetKey, loadFailed, blocked, demand(), reset(), widgetProps }`。`blocked = required && token === null`；`reset()` 清令牌并让 `resetKey` 加一；`demand()` 让 `required` 变真。
  - `<HumanVerificationWidget config action verification />`：`verification` 是 hook 的返回值。`required` 为假时什么都不渲染；`config` 为 `null` 或 `loadFailed` 时显示 `FormAlert`。容器带 `data-human-verification`，给 e2e 用。

- [ ] **Step 1: 写失败测试**
  - `TurnstileWidget`（假的 `window.turnstile`）：`renders_with_site_key_action_and_language`；`calls_on_token_from_callback`；`calls_on_clear_when_expired`；`calls_reset_when_reset_key_changes`；`removes_on_unmount`；`reports_load_error_when_script_fails`；`retries_script_after_previous_failure`；`injects_script_once_for_two_widgets`；`reports_load_error_after_15_seconds`（fake timers）。
  - `TrustTurnWidget`：`iframe_has_sandbox_and_encoded_src`；`no_script_from_trustturn_domain`；`accepts_ticket_from_iframe`；`ignores_message_from_other_origin`；`ignores_message_from_other_source`；`ignores_other_message_type`；`clears_ticket_after_55_seconds`；`reloads_when_reset_key_changes`；`soft_load_error_after_15_seconds_then_clears_on_ticket`。
  - `use-human-verification`：`blocked_until_token`；`reset_clears_token_and_bumps_key`；`demand_makes_required`。
  - `HumanVerificationWidget`：`renders_nothing_when_not_required`；`shows_unavailable_when_config_null`；`shows_load_failed`；`picks_implementation_by_provider`。
- [ ] **Step 2:** 跑这些测试。Expected: FAIL。
- [ ] **Step 3:** 实现，常量 `LOAD_TIMEOUT_MS = 15_000`、`TICKET_TTL_MS = 55_000`。脚本加载是模块级 promise，失败时清空以便重试。Turnstile 传 `size: "flexible"`、`language: "zh-cn"`。iframe 用 Tailwind 的 `min-h-22 w-full border-0`。
- [ ] **Step 4:** 再跑。Expected: PASS。
- [ ] **Step 5:** 提交 `feat(portal): 增加 Turnstile 和 TRUSTTURN 组件 (PAP-194)`。

### Task 4: 登录表单

**Files:** Modify `src/components/auth/LoginForm.tsx`、`src/app/login/page.tsx`；Test `tests/components/auth/LoginForm.test.tsx`

**Interfaces:** `LoginForm` 增加属性 `verification?: HumanVerificationConfig | null`。测试里 mock `HumanVerificationWidget`，渲染一个“通过验证”按钮调 `verification.widgetProps.onToken`。

- [ ] **Step 1: 写失败测试**：`does_not_render_widget_by_default`；`shows_widget_and_keeps_password_when_required`；`submit_blocked_until_token`（按钮 `aria-disabled`，回车也不发请求）；`sends_token_on_resubmit`；`clears_password_and_resets_on_bad_credentials`；`keeps_password_and_resets_on_verification_failed`；`keeps_password_and_resets_on_unavailable`；`resets_on_throttled`；`alert_text_differs_from_lock`；`blocked_again_after_reset`；`shows_unavailable_when_config_null_and_required`。
- [ ] **Step 2:** 跑。Expected: FAIL。
- [ ] **Step 3:** 实现。组件放提交按钮上方。页面用 `await readHumanVerification()` 传属性。任何失败都 `reset()`；只有 `bad-credentials`、`throttled`、`banned` 清空密码。
- [ ] **Step 4:** 跑。Expected: PASS。
- [ ] **Step 5:** 提交 `feat(portal): 登录被要求时才显示人机验证 (PAP-194)`。

### Task 5: 注册和找回密码

**Files:** Modify `src/components/auth/RegisterForm.tsx`、`ForgotPasswordForm.tsx`、`src/app/register/page.tsx`、`src/app/forgot-password/page.tsx`；Test 对应的表单测试

- [ ] **Step 1: 写失败测试**
  - 注册：`widget_always_shown`；`submit_blocked_until_token`；`sends_token`；`resets_after_failure`（字段错误、占用、不可用各一例）；三种验证 `reason` 的提示条。
  - 找回：`widget_always_shown`；`submit_blocked_until_token`；`sends_token`；`resets_after_failure`；`resend_requires_new_token`（倒计时结束显示组件，拿到令牌才有重新发送按钮）；三种验证 `reason` 的提示条。
  - 现有用例统一先“通过验证”再提交。
- [ ] **Step 2:** 跑。Expected: FAIL。
- [ ] **Step 3:** 实现。两个页面用 `readHumanVerification()` 传属性，`useHumanVerification(true)`。
- [ ] **Step 4:** 跑。Expected: PASS。
- [ ] **Step 5:** 提交 `feat(portal): 注册和找回密码接入人机验证 (PAP-194)`。

### Task 6: 设置密码对话框

**Files:** Modify `src/components/reauth/SetPasswordDialog.tsx`；Test `tests/components/reauth/SetPasswordDialog.test.tsx`

- [ ] **Step 1: 写失败测试**：`reads_config_each_time_it_opens`；`send_blocked_while_reading`；`shows_unavailable_and_blocks_when_config_null`；`send_blocked_until_token`；`sends_token`；`resets_after_failure`；`resend_requires_new_token`；`verification_reason_shows_verification_message`。
- [ ] **Step 2:** 跑。Expected: FAIL。
- [ ] **Step 3:** 实现。`open` 变真时调 `fetchHumanVerification()`，读取期间配置状态是 `undefined`。
- [ ] **Step 4:** 跑。Expected: PASS。
- [ ] **Step 5:** 提交 `feat(portal): 设置密码对话框接入人机验证 (PAP-194)`。

### Task 7: CSP、e2e 与全量验证

**Files:** Modify `next.config.ts`、`tests/next-config.test.ts`、`tests/e2e/support/account.ts`、`tests/e2e/social-login/mock-gateway.mjs`、`patra-portal/README.md`（如有相关章节）

- [ ] **Step 1: 写失败测试** `tests/next-config.test.ts`：三个路径有 CSP；`script-src` 含 `https://challenges.cloudflare.com`，不含 `trustturn`；`frame-src` 含两家；其他路径没有这个头；开发模式才有 `'unsafe-eval'`。
- [ ] **Step 2:** 跑。Expected: FAIL。
- [ ] **Step 3:** 实现 `headers()`。
- [ ] **Step 4:** e2e 辅助：`support/account.ts` 增加 `completeVerification(page)`（有 `[data-human-verification]` 时等提交按钮不再 `aria-disabled`），`register`、`login` 和找回密码的用例调用它。`mock-gateway.mjs` 增加 `GET /auth/human-verification`，返回官方测试 sitekey `1x00000000000000000000AA`。
- [ ] **Step 5:** `pnpm lint`、`pnpm exec tsc --noEmit`、`pnpm exec vitest run --maxWorkers=2`、`pnpm build`。Expected: 全部通过。
- [ ] **Step 6:** 本机验收：起本机 gateway 和 identity（官方测试密钥），`pnpm test:e2e tests/e2e/auth.spec.ts tests/e2e/user-center.spec.ts`，并截图看桌面和手机布局。环境起不来时写明原因，不跳过单测。
- [ ] **Step 7:** 提交 `test(portal): 给人机验证加 CSP 和 e2e 辅助 (PAP-194)`，停在本地等用户验收。
