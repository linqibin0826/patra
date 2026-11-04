# 门户第三方登录工程设计（v0.19 前端）

> **状态**：实施中（2026-11-04，分支 `feat/v0.19-social-login-portal`）
> **Issue**：PAP-186、PAP-187（Project P-PAP-16）
> **产品决策**：`docs/patra/release-specs/v0.19-social-login.md` 第 2.1 节、第 4 节决策 B、D、E、G
> **接口契约**：`docs/patra/specs/2026-11-04-identity-social-login-design.md` 第 2 节；回调路径以本文第 1 节为准
> **画板**：https://claude.ai/artifact/FC4XR8s8AVQjtJz92C8E7F（`Auth`、`Complete`、`Security` 及手机版）；页面、状态与文案以设计简报 `patra-portal/docs/design-briefs/v0.19/social-login.md` 为准

本文只写门户的落地方式，不重新讨论产品规则。

## 1. 路由

| 门户路由 | 方法 | 作用 |
|---|---|---|
| `/api/auth/oauth/[provider]/start` | GET | 发起登录，查询参数 `from`（`login` / `register`）、`next` |
| `/api/auth/oauth/callback/[provider]` | GET | 回调地址，已在三家后台注册，**不是** `/api/auth/oauth/{provider}/callback` |
| `/api/auth/oauth/registration/code` | POST | 发验证码，重发也走它 |
| `/api/auth/oauth/registration/complete` | POST | 提交验证码，写会话 Cookie |
| `/api/me/login-methods` | GET | 设置页列表 |
| `/register/complete` | 页面 | 补填与验证码，属于专注布局（加入 `FOCUS_PAGES` 和 proxy 的 `AUTH_PAGES`） |

绑定和解绑是 Server Action（`lib/identity/login-method-actions.ts`），和踢设备一样由 `withReauth` 包住。补填的两个写接口用 Route Handler 而不是 Server Action：`complete` 同时写会话 Cookie、清 `ticket`，Server Action 写 Cookie 会重新渲染当前页，补填页读不到 `ticket`，用户会先看到一眼「已过期」（`reauthenticate` 的说明里记过同一个坑）。两个 Route Handler 都做同源校验。

## 2. Cookie

全部由门户写。`Secure` 都跟 `secureEnabled()`（`PATRA_SESSION_COOKIE_SECURE`）。

| 名字 | 内容 | HttpOnly | Path | Max-Age |
|---|---|---|---|---|
| `patra_oauth_state` | 授权地址里的 `state` | 是 | `/api/auth/oauth` | 600 |
| `patra_oauth_return` | 发起页和 `next`，`URLSearchParams` 编码 | 是 | `/api/auth/oauth` | 600 |
| `patra_oauth_ticket` | 待补填凭据 | 是 | `/` | 1800 |
| `patra_oauth_signed_in` | 固定值 `1`，下一页弹「已登录」后清掉 | **否** | `/` | 60 |

- 都不带 `__Host-` 前缀。前两个的 `Path` 不是 `/`，后两个的名字是契约定的或者没有保护价值。
- 清除时写空值和 `Max-Age=0`，其余属性与写入时相同。
- `patra_oauth_ticket` 只从 Cookie 读：identity 不把它绑定到浏览器，防注入全靠门户。查询串、请求体和浏览器自带的 `X-Patra-Registration-Ticket` 都不算数，有测试守。
- 三家标识不改色（简报第 5 节）。Google 是官方 `signin-assets.zip` 的 Light、无文字、Square 版 SVG，逐字节不改；它是 40px 的按钮底板，G 在正中占 20px，用 `object-fit: none` 裁出。GitHub、ORCID 来自设计快照。
- `patra_oauth_return` 契约里没有：平台回调只带 `code` 和 `state`，发起页和 `next` 只能这样跨越第三方往返。读出后 `from` 校验枚举，`next` 重新过 `safeNextPath`。绑定写 `from=settings`。
- `patra_oauth_signed_in` 契约里也没有：登录成功是整页跳转到任意 `next`，不适合往地址栏加参数。里面没有敏感内容。

## 3. 发起与回调

**发起（GET）**：`provider` 不认识回 404。当前域名不开放第三方入口时跳回发起页，不请求网关。其余情况按契约第 2.1 节转发。响应不是带 `state` 的 302 时，跳回发起页并带 `oauth=unavailable`。成功时写两个 Cookie，再把 `Location` 原样 302 给浏览器。

**回调（GET）**按这个顺序：

1. `provider` 不认识回 404。
2. 查询串里 `state` 必须恰好一个，且等于 Cookie 的值；否则不转发，跳回发起页并带 `oauth=rejected`。
3. 清掉 `patra_oauth_state`、`patra_oauth_return`。
4. 把查询串原样转发给 `/patra-identity/login/oauth2/code/{provider}`，带 `client`、设备头（`ensureDeviceId`）、会话令牌（有就带，网关对失效令牌按匿名放行）。超时 20 秒：identity 要依次请求平台的令牌、公钥、资料接口。
5. 按下表跳转。

| 响应 | 跳转 | Cookie |
|---|---|---|
| `AUTHENTICATED` | `next`；`PENDING_DELETION` 时 `deletionPendingHref(next)` | 写会话；非冷静期写 `patra_oauth_signed_in` |
| `REGISTRATION_REQUIRED` | `/register/complete?next=…` | 写 `patra_oauth_ticket` |
| `LINKED` | `/settings/security?oauth=linked&provider=…` | |
| 422 `OAUTH_CANCELLED` | 发起页 `oauth=cancelled` | |
| 422 `OAUTH_REJECTED`、403 `LINK_SESSION_MISMATCH` | 发起页 `oauth=rejected` | |
| 503 | 发起页 `oauth=unreachable` | |
| 403 其他 | 发起页 `oauth=banned` | |
| 401 且 `from=settings` | `/login?next=/settings/security`，不带提示 | 清会话 |
| 409 `PROVIDER_ACCOUNT_TAKEN` | 发起页 `oauth=taken` | |
| 409 `PROVIDER_ALREADY_LINKED` | 发起页 `oauth=already-linked` | |
| 其他、响应体不对、请求抛错 | 发起页 `oauth=unavailable` | |

跳转一律用相对 `Location`：`request.url` 在隧道后面不一定是公网地址。响应都带 `Cache-Control: private, no-store`。

**日志**：只用常量接口名调 `logUnexpectedResponse` / `logRequestError`，查询串（含 `code`、`state`）、`ticket`、`sessionToken` 不进日志；有测试守。

**错误只走查询串**：只放 `oauth` 和 `provider` 两个枚举值，解析时都校验，`ticket`、`code`、`state` 不进地址栏。页面显示后用 `writeQuery` 清掉（`history.replaceState`，不触发重新渲染），刷新不再出现。

## 4. 公网判断（决策 G）

`lib/auth/oauth-host.ts`：请求的域名（`X-Forwarded-Host`，其次 `Host`，和 `isSameOrigin` 一致）不分大小写地等于 `PATRA_OAUTH_HOST`，没配时等于 `patra.linqibin.dev`。是白名单：线上公网和本机显示，tailnet IP、主机名都不显示。本机开发在 `.env.local` 写 `PATRA_OAUTH_HOST=localhost:4000`（`.env.example` 已写）。不用改 `patra-infra`。某家平台没配置时，发起路由拿到 404，按「不是 302」回发起页显示 `oauth=unavailable`。

判断只在服务端做（RSC、Route Handler、绑定 Action），以布尔值传给组件，不会闪一下。

## 5. 登录页、注册页

`LoginForm` / `RegisterForm` 多一个可选属性 `oauth: { enabled, notice }`，顺序照画板：提示条 → 三家入口 → 「或用邮箱登录」 → 字段。

- 入口是 `<a href>`：`next/link` 会预取，预取一次就写一次 Cookie、请求一次 identity。
- 点击后本按钮显示「正在前往 GitHub…」，三个按钮都 `aria-disabled`。`pageshow` 时复位：从第三方按后退，页面可能从往返缓存恢复。
- 用户提交邮箱表单时清掉 `oauth` 提示条。

## 6. 补填与验证码（`/register/complete`）

页面先在服务端调 `GET /auth/oauth/registration`。`ticket` 不合格是过期态，请求失败抛给错误页，`pendingCode` 有值直接进验证码步。

提交用 RHF + Zod。用户名规则复用 `usernameIssue`，校验字段从 `registerSchema` 抽出来共用。

| 结果 | 表现 |
|---|---|
| 发码 202 | 进验证码步，按 `nextResendAt` 倒计时 |
| 发码 409 `email` | 邮箱字段标红，字段下方显示撞车块（画板 `.pt-sso-clash`），不用字段错误文案 |
| 发码 409 `username` | 字段错误「这个用户名已被使用」 |
| 发码 422 字段错误 | 字段错误，复用 `fieldErrorMessages` |
| 发码 / 完成 422 `ticket` | 过期态 |
| 发码 429 | 补填步用提示条；验证码步在重发位置按「发送太频繁」倒计时 |
| 完成 422 `INVALID` | 字段错误「验证码不正确」 |
| 完成 422 `EXPIRED`（过期、已用、错满 5 次） | 提示条「这个验证码已失效，请重新发送一个」，锁住输入，只能重发 |
| 完成 409 `username` | 回补填步，「这个用户名刚被别人用了，请换一个」 |
| 完成 409 `email` | 回补填步，撞车块 |
| 完成 409 `PROVIDER_ACCOUNT_TAKEN` | 结果页「这个账号已经建好了」，回登录页 |

用户刷新验证码页后，用 `pendingCode.username` 和 `email` 直接重发。完成走 `useAuthSubmit`，文案「注册成功，已登录」。

撞车的「去登录」到 `/login?next=/settings/security`，邮箱经 `auth-handoff` 带过去（`notice: "email-clash"`），焦点在密码框。验证码输入框不写 `maxLength`：浏览器会先截断粘贴的内容，带空格的验证码就丢位了。

## 7. 设置页「登录方式」

`/settings/security` 的卡片顺序：登录方式 → 修改密码（`hasPassword` 为 true 才有）→ 登录设备。列表走 `/api/me/login-methods` + `useLoginMethodsQuery`，第一行是「邮箱和密码」，之后按 Google、GitHub、ORCID 固定顺序。

| 操作 | 规则 |
|---|---|
| 绑定 | 只在公网显示；邮箱未验证时禁用并显示警示条（内含 `ResendVerificationButton`）；没有密码时直接开设置密码框；否则 `withReauth(startProviderLink)`，成功 `location.assign` |
| 解绑 | 只剩一种且没有密码时禁用，下方说明原因和设置密码入口；没有密码时直接开设置密码框；否则确认框 → `withReauth(unlinkProvider)` |
| 设置密码 | 「邮箱和密码」一行的按钮、说明里的链接，都打开 `SetPasswordDialog` |
| 结果 | `oauth` 参数由页面传入，`linked` 弹提示并失效列表，其余显示提示条，随后清参数 |

`startProviderLink` 从 `authorizationUrl` 取 `state`，和发起登录写同样的 Cookie（`from=settings`）。它也判一次公网：tailnet 上发起没有意义。

## 8. 再认证

`ReauthDialog` 读 `useCurrentUser()`：`hasPassword === false` 时渲染设置密码的内容（文案 `reason` 版），其余情况包括用户未加载时都是原来的密码框，密码流程一行不动。

`SetPasswordDialog` 的内容两处共用：调现有 `requestPasswordReset(user.email)`，明确说「所有设备都会退出登录，包括这台」；发送后显示「查看你的邮箱」和重发倒计时。关闭时对话框 `settle(false)`，调用方按 `cancelled` 处理。

## 9. 隐私政策

- 收集表加一行「第三方账号」，下面加一段说明令牌不保存，文案照简报第 6.3 节。
- Cookie 表加四个 Cookie。
- 共享节写明点入口时会跳到对方网站，对方知道你在登录 Patra。
- 「保留多久」写明解绑或注销到期时删除。「你可以做什么」加解绑入口。
- 更新日期改 2026-11-04。

## 10. 端到端验证

`pnpm test:e2e:social-login`（`playwright.social-login.config.ts`）：门户真实运行，连 `tests/e2e/social-login/mock-gateway.mjs` 模拟网关和平台（决策 H），桌面和手机各跑一遍，覆盖首次注册、老用户登录、绑定、解绑、无密码分支和 `ticket` 注入。默认的 `test:e2e` 不跑它。

只有部署到公网后才能验证：

- 真实三家手工验收（D1～D5）：回调地址只注册在 `patra.linqibin.dev`。
- 经隧道实测显示入口、经 tailnet 地址实测隐藏入口，验证 Cloudflare Tunnel 后的 `Host`。
- 真实平台在用户拒绝时是否带回 `state`：不带时用户看到的是「这次授权没有成功」。
- 真实 Safari 里 Google 标识的渲染：它的颜色靠 `foreignObject`。
