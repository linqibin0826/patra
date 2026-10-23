# 门户用户中心：账号、安全、再认证、找回密码、邮件落地页、取消注销工程设计（PAP-102）

> **状态**：已实施（2026-10-23），待用户验收；实施结果见第 18 节
> **Issue**：Linear PAP-102（GitHub #268），v0.12 User Center 的第二个、也是最后一个前端 Issue
> **分支**：`feat/v0.12-user-center-portal`（接在 PAP-101 的提交后面，两个 Issue 合一个前端 PR）
> **产品定义**：`docs/patra/release-specs/v0.12-user-center.md` 2.1 节与第 4 节
> **页面、状态与文案**：`patra-portal/docs/design-briefs/v0.12/user-center.md`（下称「简报」）§2、§3.2、§3.10、§3.11、§3.14 ~ §3.18、§4.2、§4.7 ~ §4.10、§4.12；画板快照 `docs/patra/design/snapshots/2026-10-22-user-center-hifi.zip` 的 `Main` / `Account` / `Security` / `Landing`（含 `-M`）
> **前置**：PAP-101 工程 spec `docs/patra/specs/2026-10-23-portal-user-center-profile-design.md`（下称「PAP-101 spec」）。本 spec 只写增量，PAP-101 已建的底座（`callIdentity`、`settleFailure`、设置页外壳、`Dialog`、`UsernameField`、`ResendVerificationButton` 等）直接复用
> **后端契约**：以 identity 代码为准（2026-10-23 逐个控制器核对），参考 `2026-10-20-identity-account-rebuild-design.md` §8.4 / §16、`2026-10-21-identity-email-verification-design.md` §8.3 / §18、`2026-10-22-identity-account-security-design.md` §18、`2026-10-22-identity-login-devices-design.md` §11、`2026-10-22-identity-account-deletion-design.md` §7 / §8，以及 Linear PAP-102 上 PAP-98 / PAP-99 / PAP-100 的四条契约评论

## 1. 要解决的问题

PAP-101 之后，用户能注册、填资料、看主页，但还管不了自己的账号：

1. 设置页「账号」「安全」两项、横幅的「更改邮箱」、冷静期跳转的 `/account/deletion-pending` 都是 404。
2. 后端已经能改用户名、改密码、改邮箱、踢设备、注销、找回密码，门户没有入口；邮件里的四种链接（验证邮箱、重设密码、确认新邮箱、撤销改邮箱）打开都是 404。
3. 敏感操作超出 10 分钟再认证窗口时后端回 403 `REAUTH_REQUIRED`，门户没有弹密码框、重试原操作的机制。
4. 冷静期账号登录后，除了登录 / 注册那一下会跳取消注销页，其余页面都照常能逛。
5. 用户中心没有任何端到端测试。

## 2. 范围

**本 Issue 做**：

- `/settings/account`：用户名卡片（改名、冷却）、邮箱卡片（验证状态、重发、改邮箱与待确认）、注销卡片与注销对话框
- `/settings/security`：改密码卡片、登录设备卡片（列表、踢单个、踢其他全部、本机退出登录）
- 再认证对话框与「拦 403 → 弹框 → 重试原操作」的机制
- `/forgot-password`；登录页改动（「忘记密码？」、重设后的成功提示与预填）
- 四个邮件落地页：`/verify-email`、`/reset-password`、`/confirm-email-change`、`/revert-email-change`
- `/account/deletion-pending` 与「冷静期打开任何页面都去取消注销页」（简报 §2.2 规则 3 的全站部分）
- `FormAlert` 加 `tone`、`AuthShell` 加「不显示返回」
- Vitest 单元测试；整个用户中心（含 PAP-101 各页）的 Playwright 冒烟

**不做**：邮件模板与后端接口（都已上线）；通知、已连接账号两个分区（release spec 2.1.11）。

## 3. 已定的前提

- 产品规则、文案、状态以简报为准，本 spec 不重复文案表，只写工程做法；简报没写到、由我补的文案在第 15 节列出。
- 绿地项目：按最终形态写，不留兼容分支。
- 后端接口（2026-10-23 核对代码，所有路径都在 `/patra-identity` 下）：

| 接口 | 成功 | 失败（门户要区分的） | 再认证 |
|---|---|---|---|
| `PATCH /me/username` `{ username }` | 200 `{ username, nextUsernameChangeAt }` | 422 `username` 原因码（含 `UNCHANGED`）；409 `errors[]` `username` / `ALREADY_TAKEN`；409 `traits` 含 `USERNAME_CHANGE_COOLDOWN`；409 `PENDING_DELETION` | 否 |
| `POST /me/email/change` `{ newEmail }` | 202 `{ newEmail, expiresAt }` | 422 `newEmail`（含 `UNCHANGED`）；409 `traits` 含 `EMAIL_CHANGE_PENDING`；409 `errors[]` `email` / `ALREADY_REGISTERED`；429；409 `PENDING_DELETION` | 是 |
| `GET /me/email/change` | 200 `{ newEmail, expiresAt }` / 204 没有待确认 | 401 | — |
| `DELETE /me/email/change` | 204（没有待确认也 204） | 409 `PENDING_DELETION` | 否 |
| `PUT /me/password` `{ currentPassword, newPassword }` | 200 `{ sessionToken, user }`（总带新令牌） | 401 `PASSWORD_MISMATCH`；422 `currentPassword` / `newPassword`（`newPassword` / `UNCHANGED`）；429；409 `PENDING_DELETION` | 否（每次都要当前密码） |
| `POST /auth/reauth` `{ password }` | 200 `{ sessionToken \| null, user }`；`null` 时保持现有 Cookie | 401 `PASSWORD_MISMATCH`；429（与登录共用失败限制）；其余 401 是会话没了 | — |
| `GET /me/sessions` | 200 `{ sessions: [...] }`，本机第一 | 401 当前会话已不在 | — |
| `DELETE /me/sessions/{id}` | 200 `{ revokedSessions: 0 \| 1 }` | 409 `CURRENT_SESSION`（本机）；冷静期放行 | 是 |
| `DELETE /me/sessions/others` | 200 `{ revokedSessions: n }` | 冷静期放行 | 是 |
| `POST /me/deletion` `{ username }` | 200 `{ deletionDueAt }`，全部会话（含本机）已撤销 | 422 `username` / `REQUIRED` · `MISMATCH`（先于再认证判）；409 `PENDING_DELETION` | 是 |
| `DELETE /me/deletion` | 200，与 `/auth/me` 同构的 `user`（重复取消也 200） | — | 否 |
| `POST /auth/password/forgot` `{ email }`（公开） | 202 `{ email, nextRequestAt }`（注册与否一样） | 422 `email`；429 | — |
| `POST /auth/password/reset` `{ token, newPassword }`（公开） | 200 `{ email }`，不自动登录，撤销全部会话 | 422 `token` / `INVALID`；422 `newPassword` 原因码同注册；429 | — |
| `POST /auth/email/verify` `{ token }`（公开，带会话就翻译令牌） | 200 `{ email, emailVerifiedAt }` | 422 `token`（`REQUIRED` / `INVALID`） | — |
| `POST /auth/email/change/confirm` `{ token }`（公开） | 200 `{ email, emailVerifiedAt }` | 422 `token`；409 `errors[]` `email` / `ALREADY_REGISTERED` | — |
| `POST /auth/email/change/revert` `{ token }`（公开） | 200 `{ email }`，全部会话下线、密码作废、发重设邮件 | 422 `token`；409（旧地址已被注册） | — |

  - 会话列表项：`{ id: string, current, deviceName: string | null, deviceClass: "DESKTOP" | "MOBILE" | "TABLET" | "UNKNOWN", ipAddress: string | null, ipRegion: string | null, privateNetwork, signedInAt, lastActiveAt }`。`id` 是雪花 ID，门户一律按字符串处理。
  - 验证邮箱只在「当前会话的用户 = 令牌的用户」时保留本机会话，否则撤销该用户全部会话（PAP-96 §8.3 第 6 步）。
  - 冷静期内能用的接口见 PAP-100 §8：取消注销、登出、再认证、踢设备、全部读接口、找回 / 重设密码、撤销改邮箱；其余写接口 409 `PENDING_DELETION`。

- **Server Action 里写 Cookie 会让 Next 重新渲染当前页和它的 layout**（`node_modules/next/dist/docs/01-app/01-getting-started/07-mutating-data.md`：「set or delete a cookie in a Server Action, Next.js re-renders the current page and its layouts」，客户端组件状态保留）。设置页的守卫 `requireUser` 在重新渲染时也会跑：Action 清掉 Cookie，当前设置页就会被重定向到登录页。第 7.3 节注销的做法由这一条决定。

## 4. 方案比较（只列有分歧的三处）

### 4.1 再认证：在哪一层拦 403、怎么重试

Issue 写「统一在网关客户端层拦 403 `REAUTH_REQUIRED`，弹窗成功后重放原请求」。门户没有浏览器端的网关客户端：所有写操作都是 Server Action 在服务端调网关，403 只有 Action 看得见，Action 又弹不了对话框。

| 方案 | 做法 | 取舍 |
|---|---|---|
| **A. Action 回结果 + 客户端统一包装（选）** | 需要再认证的 Action 把 403 `REAUTH_REQUIRED` 收敛成 `{ ok: false, reason: "reauth-required" }`；客户端 `useReauth().withReauth(call)` 调用 `call()`，拿到 `reauth-required` 就弹再认证对话框，验证成功后再调一次 `call()`（同样的参数，闭包里带着） | 「拦」在客户端的一个函数里统一完成，各卡片只多包一层；原操作按钮在整个过程里保持进行中；取消就是一个不报错的结果 |
| B. 浏览器直连 Route Handler，`fetch` 外包一层拦截器 | 敏感操作改成 Route Handler，浏览器端写一个带拦截器的 `fetch` | 要给每个操作补 `Origin` 校验（Server Action 自带）、自己定义响应形状；与 PAP-101「写操作走 Server Action」的约定分叉 |
| C. Action 内先查再认证状态 | 每次敏感操作前先调一个「还在窗口内吗」的接口 | 后端没有这个接口；多一次往返；并发时仍可能 403 |

这是对 Issue 措辞的偏离：拦截点在「客户端调用 Server Action 的那一层」而不是网关客户端，行为（弹框、重试原操作、取消无错误）不变。

### 4.2 冷静期账号打开任何页面都去取消注销页

| 方案 | 做法 | 取舍 |
|---|---|---|
| **A. proxy 拦整页打开 + 客户端兜底（选）** | proxy 的 matcher 放宽到全部页面；只对「带会话 Cookie 的 GET 整页请求（`Sec-Fetch-Dest: document`）」查一次 `/auth/me`（3 秒超时，出错放行），`status = PENDING_DELETION` 就 307 到取消注销页。站内跳转、后退由客户端组件兜底：导航栏里读当前用户，是冷静期就 `router.replace` 过去 | 直接打开、刷新、地址栏输入都不闪内容；站内跳转不多打网关。代价：已登录用户每次**整页打开**多一次 `/auth/me`（站内跳转不受影响） |
| B. 只靠客户端 | 导航栏读到冷静期就跳 | 整页打开时先渲染出原页面再跳走，内容闪一下；Issue To-Do 明写「中间件按 `/auth/me` 的状态重定向」 |
| C. 每个 RSC 页面自己查 | 根 layout 或每页调 `lookupSession` | 根 layout 读 Cookie 会把全站变成动态渲染，首页、文献页的静态与缓存全部失效 |

### 4.3 登录设备列表：服务端渲染还是客户端查询

- **选客户端查询**：Route Handler `GET /api/me/sessions` + TanStack Query。简报要的「三行骨架」「加载失败 + 重试」「行内退出中、淡出移除」「改密码后列表刷新」「最后活跃 N 分钟前」都是客户端状态；相对时间依赖当前时刻，服务端渲染会和浏览器的时间对不上（水合不一致）。
- 不选 RSC + Suspense：重试要整页 `router.refresh()`，删行动画与乐观移除不好做。

## 5. 模块与组件

```
patra-portal/src/
├── proxy.ts                                   # 改：全页 matcher、冷静期跳转（§9.2）
├── lib/
│   ├── auth/
│   │   ├── require-user.ts                    # 改：冷静期跳取消注销页（§9.3）
│   │   ├── next-path.ts                       # 改：FOCUS_PAGES（专注布局页清单，§9.2）
│   │   ├── messages.ts                        # 改：本 Issue 的文案（§15）
│   │   └── form-schemas.ts                    # 改：改密码、重设密码、忘记密码、改邮箱的 Zod schema
│   ├── identity/
│   │   ├── mutation-failure.ts                # 改：加 `isReauthRequired`、`ReauthRequired`（§6.1）
│   │   ├── account.ts                         # 新：GET /me/email/change（RSC 用）
│   │   ├── account-actions.ts                 # 新："use server"：changeUsername、requestEmailChange、cancelEmailChange、requestDeletion、endDeletedSession、cancelDeletion
│   │   ├── security-actions.ts                # 新："use server"：changePassword、revokeSession、revokeOtherSessions
│   │   ├── reauth-actions.ts                  # 新："use server"：reauthenticate
│   │   ├── token-actions.ts                   # 新："use server"：verifyEmail、resetPassword、confirmEmailChange、revertEmailChange、requestPasswordReset
│   │   ├── session-list.ts                    # 新：会话列表的 Zod schema 与类型（Route Handler 与客户端共用）
│   │   └── email-token.ts                     # 新：读片段令牌（格式 `^[A-Za-z0-9_-]{43}$`）
│   ├── format-time.ts                         # 新：Asia/Shanghai 的日期、日期时间、相对时间（§11）
│   ├── reauth/use-reauth.ts                   # 新：`useReauth()`（§6.2）
│   ├── sessions-query.ts                      # 新：`useSessionsQuery()`、失效
│   └── query-keys.ts                          # 改：加 `sessions`
├── store/
│   ├── reauth.ts                              # 新：再认证对话框的打开状态与待决 promise（§6.2）
│   └── auth-handoff.ts                        # 新：登录页 ↔ 忘记密码 ↔ 重设密码之间带邮箱与提示（§8.1）
├── app/
│   ├── api/me/sessions/route.ts               # 新：GET（§7.5）
│   ├── settings/account/page.tsx              # 新
│   ├── settings/security/page.tsx             # 新
│   ├── settings/layout.tsx                    # 改：包 `ReauthProvider`
│   ├── forgot-password/page.tsx               # 新
│   ├── verify-email/page.tsx · reset-password/page.tsx · confirm-email-change/page.tsx · revert-email-change/page.tsx   # 新
│   ├── account/deletion-pending/page.tsx      # 新
│   └── login/page.tsx                         # 改：只传 next；预填与提示在 LoginForm 里读 store
└── components/
    ├── auth/AuthShell.tsx                     # 改：`back` 可为 null（不显示返回）
    ├── auth/FormAlert.tsx                     # 改：`tone` = danger（默认）/ success / info
    ├── auth/LoginForm.tsx                     # 改：「忘记密码？」、重设后提示与预填（§8.2）
    ├── auth/ForgotPasswordForm.tsx            # 新
    ├── auth/ResetPasswordForm.tsx             # 新
    ├── landing/TokenLandingPage.tsx           # 新：四个落地页共用的状态机外壳（§8.3）
    ├── landing/VerifyEmailLanding.tsx · ResetPasswordLanding.tsx · ConfirmEmailChangeLanding.tsx · RevertEmailChangeLanding.tsx   # 新
    ├── account/DeletionPendingPanel.tsx       # 新：取消注销页的按钮与状态
    ├── portal/PendingDeletionRedirect.tsx     # 新：导航栏里的冷静期兜底跳转（§9.2）
    ├── portal/TopNav.tsx                      # 改：挂 `PendingDeletionRedirect`
    ├── reauth/ReauthProvider.tsx · ReauthDialog.tsx   # 新
    ├── settings/account/UsernameCard.tsx · EmailCard.tsx · DeleteAccountCard.tsx · DeleteAccountDialog.tsx   # 新
    ├── settings/security/ChangePasswordCard.tsx · SessionsCard.tsx · SessionRow.tsx   # 新
    └── ui/status-pill.tsx                     # 新：已验证 / 未验证 / 本机 / 仅你可见 这类小胶囊（若 PAP-101 已有同类写法则复用，见 §16）
```

不新增依赖。

## 6. 再认证

### 6.1 Action 侧

- `mutation-failure.ts` 加 `isReauthRequired(problem)`（403 且 `traits` 含 `REAUTH_REQUIRED`）与类型 `ReauthRequired = { ok: false; reason: "reauth-required" }`。四个要再认证的 Action 在 `settleFailure` 之后先判它。不并进 `CommonFailure`：那样 PAP-101 的资料、头像、隐私、重发四处结果类型都会多出一个它们永远收不到的分支，穷举 `switch` 要为它写兜底。
- `reauthenticate(password)`（`reauth-actions.ts`）：`POST /auth/reauth`。结果：
  - 200：`sessionToken` 非空时 `writeSessionCookie(token)`，为 `null` 时不动 Cookie；返回 `{ ok: true, user }`
  - 401 含 `PASSWORD_MISMATCH` → `wrong-password`；429 → `throttled`（`retryAfterSeconds`）；422 `password` → `wrong-password`（前端已挡空值，到这里只可能是异常输入，按密码不对说）
  - 其余经 `settleFailure`：会话没了 → `signed-out`（清 Cookie）、抛错 → `unavailable`
- 写 Cookie 会触发当前页重新渲染（§3 最后一条），客户端状态保留，对话框与原操作的进行中状态不受影响。

### 6.2 客户端侧

- `store/reauth.ts`（zustand）：`{ open: boolean, request(): Promise<boolean>, settle(ok: boolean) }`。`request()` 打开对话框并返回一个 promise，对话框验证成功 `settle(true)`、取消 `settle(false)`。同时只会有一个待决请求：已在等时再 `request()` 复用同一个 promise（两个按钮几乎同时触发也只弹一次）。
- `useReauth()` 返回 `withReauth<R>(call: () => Promise<R | ReauthRequired>): Promise<R | Cancelled>`：
  1. `const result = await call()`；不是 `reauth-required` 就原样返回。
  2. `await request()`；用户取消 → 返回 `{ ok: false, reason: "cancelled" }`，调用方什么都不显示，按钮回到可点。
  3. 验证成功 → 回到第 1 步再调一次。第二次仍是 `reauth-required`（只可能是窗口刚好在两次之间过期）就再弹一次，由用户决定，不自动循环。
- `ReauthProvider` 挂在 `app/settings/layout.tsx`（需要再认证的操作都在设置页里），渲染 `ReauthDialog` 并读 store。
- **`ReauthDialog`**（简报 §3.14、§4.9）：`Dialog size="sm"`，标题 `确认是你本人`；字段 `密码`（`PasswordInput`、`autocomplete="current-password"`、打开时聚焦）、标签行右端 `忘记密码？`（`target="_blank" rel="noopener"` 打开 `/forgot-password`）；按钮 `取消` / `确认`。
  - 空 → `请输入密码`；`wrong-password` → `密码不正确`，清空密码框、焦点回框内；`throttled` → 对话框提示条 `尝试次数过多，请 N 分钟后再试`（向上取整到分钟，至少 1）；`unavailable` → 提示条 `没有验证成功，请稍后再试`。
  - `signed-out` → `settle(false)` 关对话框，`setQueryData(currentUser, null)`，`router.replace(登录页?next=当前路径)`。
  - 成功：`setQueryData(currentUser, user)`、`settle(true)`，不 toast。
  - 验证中：按钮 `验证中…`、`dismissible={false}`（`Esc` 与点遮罩不关）。
  - 关闭（取消 / `Esc` / 遮罩）→ `settle(false)`，焦点回到触发原操作的按钮（base-ui 默认行为）。
- **叠在注销对话框之上**：再认证对话框与注销对话框是两个独立的 base-ui `Dialog.Root`。实施第一步先实测两个模态同时打开时焦点陷阱、`Esc`、遮罩点击只作用于上层；若 base-ui 的两个独立模态互相抢焦点，改为在 `DeleteAccountDialog` 内部嵌套渲染再认证（base-ui 支持嵌套 Dialog），`withReauth` 的接口不变。

### 6.3 哪些操作包 `withReauth`

改邮箱（`requestEmailChange`）、踢单个设备、踢其他全部、发起注销。其余操作不包（后端不要求）。

## 7. 设置页

### 7.1 账号页 `/settings/account`（简报 §3.10、§4.7）

- 页面（RSC）：`const user = await requireUser("/settings/account")`；`fetchPendingEmailChange()`（`account.ts`，经 `callIdentity`，200 → `{ newEmail, expiresAt }`、204 → `null`、会话失效 → 跳登录页，其他 → 抛错交给 `error.tsx`）。标题 `账号 · 设置 · Patra`。
- 三张卡片：`UsernameCard user`、`EmailCard user pending`、`DeleteAccountCard user`，间距同个人资料页。
- 账号页数据变化后的同步：Action 成功 → `invalidateQueries(currentUser)` + `router.refresh()`（设置页 h1 下的 `@用户名` 来自 layout 的 RSC，导航栏菜单来自 query，两边都要刷新）。

**用户名卡片**

- 展示态：`@用户名`（等宽、大号）+ `/u/用户名`（`<Link target="_blank">`）+ `修改`（次按钮）+ 常驻说明。
- 冷却：`nextUsernameChangeAt` 晚于现在 → `修改` 禁用，下方 `下次可修改：YYYY 年 M 月 D 日`（§11 的 Asia/Shanghai 日期）。「现在」在客户端判断：该卡片是客户端组件，首次渲染用服务端传入的 `now`（页面渲染时 `Date.now()`）避免水合不一致，挂载后换成浏览器时间。
- 编辑态：`UsernameField`（PAP-101，转小写、规则提示）预填当前用户名并全选；`保存` / `取消`；前端规则同注册页（`username-rules.ts`），另加「与当前相同」→ `新用户名和现在的一样`（前端先挡，后端 `UNCHANGED` 走同一文案）。
- `changeUsername(username)` 结果：`changed`（`{ username, nextUsernameChangeAt }`）/ `invalid`（`code`）/ `taken` / `cooldown` / `pending-deletion` / `signed-out` / `unavailable`。
  - `changed` → toast `用户名已改为 @新名`，回展示态，同步（见上）。
  - `invalid` / `taken` → 输入框下方文案（`usernameMessage`，`UNCHANGED` 另加一条）。
  - `cooldown` → 卡片提示条 `30 天内只能修改一次，下次可修改：…`，同步后卡片回冷却态；提示条里的日期取同步后 `user.nextUsernameChangeAt`。
  - `unavailable` → 卡片提示条 `没有保存成功，请稍后再试`。
  - `pending-deletion` / `signed-out` → 与 PAP-101 相同的跳转。

**邮箱卡片**（`SettingsCard id="email"`，横幅的 `/settings/account#email` 落在这里）

- 一行：邮箱 + 状态胶囊（`已验证` moss / `未验证` amber）；右侧 `更改邮箱`（次按钮）。
- 未验证：`验证邮件已发到这个地址，24 小时内有效。` + `ResendVerificationButton`（与横幅共用 store 与倒计时）。
- 更改邮箱流程（卡片内展开）：
  1. 输入态：`新邮箱`（`type="email"`、`autocomplete="email"`）+ 说明 + `发送确认邮件` / `取消`。前端校验：空 → `请输入邮箱`，格式 → `邮箱格式不正确`，与当前相同（忽略大小写）→ `新邮箱和现在的一样`。
  2. 提交 → `withReauth(() => requestEmailChange(newEmail))`，按钮 `发送中…`。
  3. 结果：`requested`（`{ newEmail, expiresAt }`）→ 收起输入态，显示待确认块，不 toast（待确认块本身就是反馈）；`invalid`（`code`）/ `taken` → 字段下方；`pending-exists` → 卡片提示条 `已有一个待确认的更改，请先取消或等它过期` + `router.refresh()` 拉回待确认块；`throttled` → 提示条 `今天更改邮箱的次数已用完，请明天再试`；`cancelled` → 不显示任何东西；其余同用户名卡片。
- 待确认块（`slate-50` 底）：`正在等待 new@example.com 确认 · HH:mm 前有效`（Asia/Shanghai 时刻；跨天时写 `M 月 D 日 HH:mm 前有效`）+ `取消更改`；待确认期间 `更改邮箱` 禁用。过期判定在客户端：`expiresAt` 已过就不显示块（不再请求后端）。
- `cancelEmailChange()` → 204 → 收起块、toast `已取消更改邮箱`；失败 → 卡片提示条 `没有保存成功，请稍后再试`。

**注销卡片与对话框**（简报 §3.15）

- 卡片：`variant="danger"`、三行说明、`注销账号…`（危险次按钮）。
- `DeleteAccountDialog`（`size="md"`）三屏：
  1. **后果**：三条说明（列表）+ `到期日：YYYY 年 M 月 D 日`（今天 + 15 天，Asia/Shanghai，提示性）；`取消` / `继续`。
  2. **输入用户名**：`请输入你的用户名 <用户名> 以确认。`（等宽、`select-all`）；输入框转小写；与当前用户名一致前提交按钮 `aria-disabled`；离开字段时不一致 → `和你的用户名不一致`；`返回` / `注销账号`（危险主按钮）。提交 → `withReauth(() => requestDeletion(username))`，按钮 `提交中…`，对话框 `dismissible={false}`。
  3. **已提交**：`CalendarClock` + `注销申请已提交` + 正文（到期日取响应的 `deletionDueAt`、邮箱取本地 `user.email`）+ `好的`。
- `requestDeletion` 结果：`requested`（`deletionDueAt`）/ `mismatch` / `pending-deletion` / `signed-out` / `unavailable` / （经 `withReauth`）`cancelled`。`mismatch` → 字段错误；`unavailable` → 对话框提示条 `没有提交成功，请稍后再试`；`cancelled` → 停在第 2 屏，按钮可点。
- **Action 不清 Cookie**：发起成功时本机会话已在服务端撤销，但若在 Action 里清 Cookie，当前设置页会被重新渲染、被 `requireUser` 重定向到登录页，结果屏就没了（§3 最后一条）。所以结果屏用本地数据渲染，Cookie 留到用户离开时再清：
  - `好的`（以及结果屏上的 `Esc` / 关闭）→ `setQueryData(currentUser, null)` → 调 `endDeletedSession()`：清会话 Cookie 后 `redirect("/")`。Server Action 里的 `redirect` 优先于当前页的重新渲染，浏览器直接到首页、导航栏是未登录态。
  - 用户直接关掉标签页：Cookie 里留着一个已撤销的令牌，下次请求 `/auth/me` 是 401，按未登录处理，无害。

### 7.2 安全页 `/settings/security`（简报 §3.11、§4.8）

- 页面（RSC）：`requireUser("/settings/security")`，标题 `安全 · 设置 · Patra`；两张客户端卡片，不在服务端取会话列表。

**改密码卡片**

- RHF + Zod：`currentPassword`（非空）、`newPassword`（同注册页规则：8 ~ 64 个码点）、`confirmPassword`（非空、相等）。三个都用 `PasswordInput`；`当前密码` 的 `autocomplete="current-password"`，另两个 `new-password`；当前密码标签行右端 `忘记当前密码？` → `/forgot-password`。说明在按钮上方，按钮 `修改密码`（不占满宽）。
- `changePassword({ currentPassword, newPassword })`：200 → `writeSessionCookie(sessionToken)`，返回 `{ ok: true, user }`；`wrong-password`（401 `PASSWORD_MISMATCH`）；`invalid-fields`（字段名 `currentPassword` / `newPassword`，原因码）；`throttled`；`pending-deletion` / `signed-out` / `unavailable`。
  - 成功：`reset()` 清空三个字段、toast `密码已修改，其他设备已退出登录`、`setQueryData(currentUser, user)`、`invalidateQueries(sessions)`。
  - `wrong-password` → 当前密码下方 `当前密码不正确`（不登出）、焦点到当前密码。
  - `newPassword` / `UNCHANGED` → `新密码不能和当前密码相同`；其余密码原因码用注册页同一张表（`fieldErrorMessage("password", code)`）。
  - `throttled` → 卡片提示条 `尝试次数过多，请 N 分钟后再试。在此期间登录也会被限制。`
  - `unavailable` → 卡片提示条 `没有保存成功，请稍后再试`。

**登录设备卡片**

- `useSessionsQuery()` 读 `/api/me/sessions`（§7.5）：加载中三行骨架；失败 → 卡片内 `设备列表加载失败` + `重试`（`refetch`）；401 → 按会话失效处理（`setQueryData(currentUser, null)` + 跳登录页）。
- `SessionRow`：
  - 图标按 `deviceClass`：`Laptop` / `Smartphone` / `Tablet` / `MonitorSmartphone`（`aria-hidden`）。
  - 第一行：`deviceName ?? "未知设备"`；本机加 `本机` 胶囊。
  - 第二行：`privateNetwork` → `内网`，否则 `ipRegion ?? "属地未知"`；`ipAddress` 非空时后接 ` · IP`。
  - 第三行：`登录于 YYYY-MM-DD HH:mm · ` + 本机 `当前在线` / 其他 `最后活跃 <相对时间>`（§11）。
  - 右侧：本机 `退出登录`（`useLogout()`：普通登出，不再认证；之后当前设置页被重新渲染到登录页，与导航栏菜单退出一致）；其他 `退出`（文字危险按钮）。
- 踢单个：`withReauth(() => revokeSession(id))`，行内按钮 `退出中…`；成功（不论 `revokedSessions` 是 0 还是 1）→ 该行 `motion-safe:` 淡出（`dur-normal`）后从缓存里移除（`setQueryData` 过滤掉这一项）、toast `已退出该设备`；失败 → 错误 toast `没有退出成功，请稍后再试`，行恢复；`cancelled` → 行恢复、无提示。
- 踢其他全部：标题行右侧 `退出其他所有设备`（没有其他设备时 `aria-disabled` + `title="没有其他设备登录着"`）→ `withReauth(() => revokeOtherSessions())` → toast `已退出其他 N 台设备`（N 取响应）→ 缓存里只留本机、再 `invalidate`。
- 只有本机时列表下方 `没有其他设备登录着。`
- `revokeSession` / `revokeOtherSessions` 的 `signed-out`（本机会话也没了）→ 跳登录页；409 `CURRENT_SESSION`（界面上不会出现）→ `unavailable`。
- 设备列表不轮询；`useSessionsQuery` 单独打开 `refetchOnWindowFocus`（全局默认关着，`src/lib/query-client.ts`），另一台设备刚登录，切回来就能看到。`staleTime` 用 0。

### 7.3 `requireUser` 与设置页守卫

`requireUser(path)` 拿到的用户 `status = PENDING_DELETION` 时 `redirect(deletionPendingHref(path))`（§9.3）。

### 7.4 再认证 Provider 的位置

`app/settings/layout.tsx` 用 `ReauthProvider` 包住 `SettingsShell`（§6.2）。

### 7.5 `GET /api/me/sessions`

- 没有会话 Cookie → 401 `{ ok: false, reason: "signed-out" }`，不打网关。
- `callIdentity("/me/sessions")`：200 且通过 `session-list.ts` 的 schema → 200 `{ sessions }`；会话失效（`isSessionRejected`）→ 清 Cookie、401；其他（含 schema 不符，记日志）→ 502 `{ ok: false, reason: "unavailable" }`。
- `Cache-Control: private, no-store`。只读、不改状态，不做 `Origin` 校验（跨站页面读不到响应体）。

## 8. 专注布局的页面

### 8.1 登录页、忘记密码、重设密码之间带数据

简报要求：登录页已输入的邮箱带到忘记密码页预填；重设成功后跳登录页、预填邮箱、显示成功提示——**邮箱不进地址栏**（§2.2 规则 6）。

- `store/auth-handoff.ts`（zustand，内存）：`{ email: string | null, notice: "password-reset" | null, put(...), take(): {...} }`。`take()` 读完即清，刷新页面或再进一次登录页都不会重复出现。
- 登录页「忘记密码？」点击时 `put({ email: 当前邮箱框的值（去空白）, notice: null })`；忘记密码页挂载时 `take()` 预填。
- 重设成功 `put({ email: 响应的 email, notice: "password-reset" })` 后 `router.replace("/login")`；`LoginForm` 挂载时 `take()`：有 `email` 预填，`notice` 为 `password-reset` 时表单上方 `FormAlert tone="success"` `密码已重设，请用新密码登录`、焦点在密码框（否则照旧在邮箱框）。

### 8.2 登录页（简报 §3.2）

- 密码 `FormField` 的 `aside` 放 `忘记密码？`（`AuthLink`，`href` 为 `/forgot-password` 带上当前 `next`）。
- 冷静期账号登录后跳取消注销页：PAP-101 已做。
- proxy 的 `leaveAuthPage`：已登录用户打开登录 / 注册页时，冷静期账号直接 307 到 `deletionPendingHref(next)`，不再先到 `next` 再被拦一次。

### 8.3 落地页共用状态机 `TokenLandingPage`（简报 §3.18）

客户端组件，props：`title`、`description`、`actionLabel`、`pendingLabel`、`danger?`、`submit(token): Promise<LandingOutcome>`、各状态的渲染函数。状态：

```
reading ──► ready ──点按钮──► submitting ──► succeeded
   │          ▲                   │      ├──► invalid
   │          └── unavailable ◄───┘      └──► conflict（只有确认 / 撤销改邮箱）
   └──► invalid（片段里没有令牌或格式不对）
```

- **reading**：首次渲染（服务端与客户端都是它，避免水合不一致）只画外壳与禁用的按钮。挂载后读 `location.hash` 里的 `token`（`URLSearchParams(hash.slice(1)).get("token")`），立即 `history.replaceState(null, "", pathname + search)` 抹掉片段；令牌匹配 `^[A-Za-z0-9_-]{43}$` → `ready`，否则 → `invalid`（不打网关）。刷新后片段已没了，就是 `invalid`。
- **ready**：标题 + 说明 + 主按钮，焦点在主按钮。**打开页面不发任何请求**（验证邮箱页读当前用户除外，那是读 `/api/auth/me`，不碰令牌）。
- **submitting**：按钮进行中文案、禁用。
- **unavailable**：回到 ready，上方 `FormAlert` `没有成功，请稍后再试`。
- **succeeded / invalid / conflict**：结果屏（成功 `CircleCheck` moss-500；无效与冲突 `CircleAlert` rust-500）+ 标题 + 正文 + 出口按钮，各页自己给。
- 外壳用 `AuthShell`，`back="/"`。

四个落地页的 Action（`token-actions.ts`，都经 `callIdentity`，`auth: "optional"`：有 Cookie 就带令牌）：

| 页 | Action | 结果 |
|---|---|---|
| `/verify-email` | `verifyEmail(token)` | `verified`（`email`）/ `invalid` / `unavailable` |
| `/reset-password` | `resetPassword({ token, newPassword })` | `reset`（`email`）/ `invalid` / `invalid-password`（`code`）/ `throttled` / `unavailable` |
| `/confirm-email-change` | `confirmEmailChange(token)` | `confirmed`（`email`）/ `invalid` / `taken` / `unavailable` |
| `/revert-email-change` | `revertEmailChange(token)` | `reverted`（`email`）/ `invalid` / `taken` / `unavailable` |

`invalid` 只认 422 且 `errors[]` 里有 `token` 字段；其余 422（`newPassword` 除外）按 `unavailable` 记日志。

**验证邮箱 `/verify-email`**

- 打开时照常是就绪态，点按钮才 POST。**不做**简报 §3.18 a「已登录且已验证就直接显示已验证、不发 POST」的捷径：前端判断不了令牌属于谁，浏览器里登录着已验证的 B、打开的是 A 的验证链接时，捷径会显示「B 的邮箱已验证」，A 的令牌永远消费不掉；照常 POST 是安全的（后端只动令牌所属用户的会话）。简报想避免的「已验证的人点旧链接看到『无效』」改在无效屏处理（见下）。
- 成功：`await queryClient.fetchQuery(currentUser)` 重新取一次；已登录且 `user.email` 与响应的 `email` 相同（忽略大小写）→ 有会话文案（`其他设备上的登录已退出。` + `继续浏览` → `/`）；否则无会话文案（`为了安全，所有设备都已退出登录。` + `登录` → `/login`）。以「验证后本机会话还在不在」为准，而不是「发请求时带没带 Cookie」：带着别的账号的会话点链接时，identity 不保留它（§3）。
- 无效：按当前用户分三种出口——已登录且未验证 → `ResendVerificationButton label="重新发送验证邮件"`（就地倒计时）；已登录且已验证 → 改显示「已验证」屏（`<当前邮箱> 已验证。` + `继续浏览`，这时令牌本来就无效，不存在消费不掉的问题）；未登录 → `登录后重新发送`（`/login?next=/settings/account`）。

**重设密码 `/reset-password`**

- ready 屏里是 `ResetPasswordForm`：`新密码`（`至少 8 位`、`new-password`）、`确认新密码`；前端规则同注册页；主按钮 `设置新密码`。
- `reset` → `invalidateQueries(currentUser)`（令牌所属用户的会话已全部撤销；浏览器里登录的若是另一个账号，它的会话还在，所以不直接写 `null`）→ handoff（§8.1）→ `router.replace("/login")`。登录着另一个账号时 proxy 会把 `/login` 送回 `/`，预填与提示随之丢失，这个边角情况接受。
- `invalid-password` → 新密码下方（注册页同一张表），令牌未被消费，可改了再交；`throttled` → 提示条 `请求太频繁，请 N 分钟后再试`；`invalid` → 无效屏（出口 `重新申请` → `/forgot-password`）。

**确认新邮箱 `/confirm-email-change`**

- `confirmed` → 成功屏；已登录时 `invalidateQueries(currentUser)`（邮箱变了）。
- `taken` → 冲突屏 `这个邮箱已被注册`；`invalid` → 无效屏。出口按登录态：已登录 `去账号设置`（`/settings/account`），未登录 `登录`。

**撤销改邮箱 `/revert-email-change`**

- ready 屏说明是三条列表；主按钮 `撤销更改并锁定账号`（危险主按钮）。
- `reverted` → `invalidateQueries(currentUser)`（理由同重设密码）→ 成功屏，无按钮。
- `taken` → 冲突屏 `无法改回原邮箱`；`invalid` → 无效屏，无按钮。
- 冷静期账号也能撤销（PAP-100 §8），所以本页不进冷静期跳转（§9.2）。

### 8.4 忘记密码 `/forgot-password`（简报 §3.17、§4.10）

- 页面读 `next`（`safeNextPath`），`AuthShell back` 为 `authPageHref("/login", next)`。
- `ForgotPasswordForm` 两态：
  - 输入态：`邮箱`（handoff 预填、自动聚焦）+ `发送重设邮件` + `想起来了？登录`。空 / 格式错前端挡。
  - 已发送态：`查看你的邮箱` + 正文（邮箱用响应里的 `email`）+ `没收到？先看看垃圾邮件。` + 重新发送（按 `nextRequestAt` 倒计时 `N 秒后可重新发送`，到点变 `重新发送`）+ `换一个邮箱`（回输入态，保留输入）+ `返回登录`。页面标题（h1）随态切换：`AuthShell` 的 `title` / `description` 由表单的态决定，所以页面把 `AuthShell` 放进客户端组件里渲染（`AuthShell` 本身没有 `"use client"`，可以被客户端组件引用）。
- `requestPasswordReset(email)`：`sent`（`email`、`nextRequestAt`）/ `invalid`（邮箱格式）/ `throttled` / `unavailable`。`throttled` 在输入态是提示条、在已发送态替换倒计时那行，文案 `发送太频繁，请 N 分钟后再试`（< 60 秒说秒）；`unavailable` → 提示条 `没有发出去，请稍后再试`。
- 已登录也能用，不影响当前会话。冷却只在本页内存里，换页丢失可接受（再点得到 429）。

### 8.5 取消注销页 `/account/deletion-pending`（简报 §3.16）

- 页面（RSC）读 `next`（`safeNextPath`），`lookupSession()`：
  - `anonymous` / `rejected` → `redirect(authPageHref("/login", next))`（登录后冷静期账号会再回到本页）
  - `authenticated` 且 `ACTIVE` → `redirect(next)`
  - `authenticated` 且 `PENDING_DELETION` → 渲染
  - `unavailable` → 抛错交给 `error.tsx`
- `AuthShell back={null}`（不显示返回）；标题 `你的账号正在注销`；副文案 `账号将于 YYYY 年 M 月 D 日 HH:mm 永久删除。`（`deletionDueAt`，Asia/Shanghai）；警示块；`DeletionPendingPanel`：
  - `取消注销，恢复账号`（主，整宽）→ `cancelDeletion()` → `recovered`（`user`）→ `setQueryData(currentUser, user)`、toast `已取消注销，欢迎回来`、`router.replace(next)`。失败 → 提示条 `没有取消成功，请稍后再试`；`signed-out` → 登录页。
  - `退出登录`（文字按钮）→ `logout()` 后 `router.replace("/")`。
- `robots: noindex`，标题 `取消注销 · Patra`。

### 8.6 `AuthShell` 与 `FormAlert` 的改动

- `AuthShell.back: string | null`，`null` 时页眉右侧不渲染「返回」。
- `FormAlert tone`：`danger`（默认，现状）/ `success`（`status-success-bg` 底、`CircleCheck` moss-700）/ `info`（`status-info-bg`，`Info`）。`success` 与 `info` 用 `role="status"`（不打断朗读），`danger` 保持 `role="alert"`。

## 9. 冷静期跳转

### 9.1 规则

冷静期账号（`/auth/me` 的 `status = PENDING_DELETION`）打开任何**带导航栏的页面**，去 `/account/deletion-pending?next=<当前路径 + 查询串>`。专注布局的页面不跳：

- `/login`、`/register`：已登录访问由 `leaveAuthPage` 处理（§8.2）。
- `/forgot-password`、`/reset-password`、`/revert-email-change`：冷静期内后端放行（PAP-100 §8），忘了密码、要撤销改邮箱的人要能用。跳走的话，片段里的令牌会被 307 带到取消注销页，取消后回 `next` 时片段已丢。
- `/verify-email`、`/confirm-email-change`：冷静期内令牌已作废，打开只会得到「链接无效」，不跳也无害，与其余专注页保持一致。
- `/account/deletion-pending` 本身。

清单在 `next-path.ts` 的 `FOCUS_PAGES` 里，proxy 与 `safeNextPath` 共用（取消注销页本身同样不能作为 `next`）。

### 9.2 实现

- **proxy**：`config.matcher` 改为排除 `api`、`_next`、带扩展名的静态文件之外的全部路径。新的判断放在最前（详情页 404 检查之前），只在同时满足时进行：`GET`、`Sec-Fetch-Dest: document`、不在 `FOCUS_PAGES`、有会话 Cookie。`lookupSessionByToken(token, { timeoutMs: 3_000, client })`：`authenticated` 且 `PENDING_DELETION` → 307 到 `deletionPendingHref(pathname + search)`；其余（含出错、超时）放行。详情页的 404 检查与这次查询并行发出，冷静期跳转优先。
- **客户端兜底** `PendingDeletionRedirect`（挂在 `TopNav` 里，所有带导航栏的页面都有）：`useCurrentUser()` 是已登录且 `PENDING_DELETION` 时 `router.replace(deletionPendingHref(当前路径 + 查询串))`。冷静期账号登录时 `use-auth-submit` 已把带 `PENDING_DELETION` 的用户写进缓存，所以这个兜底读缓存就够，不需要聚焦重拉。覆盖：从取消注销页后退回路由缓存里的页面、站内跳转（`Sec-Fetch-Dest: empty`，proxy 不查）。
- **写接口 409**：PAP-101 已按 `pending-deletion` 跳转，本 Issue 的 Action 沿用。注意「别的标签页刚发起注销」并不会让这一页收到 409：发起注销撤销该用户全部会话（`RequestAccountDeletionHandler`），其他标签页下一个请求是 401，按会话失效处理。

### 9.3 设置页

`requireUser` 读到冷静期 → `redirect(deletionPendingHref(path))`。设置页的服务端渲染在客户端兜底之前就能拦住。

## 10. 结果类型与错误处理

- 沿用 PAP-101：Action 只返回 `reason` 与原因码，文案在客户端按 `messages.ts` 选；共同失败经 `settleFailure`；认不出的 ProblemDetail 经 `unexpectedProblem` 记日志后 `unavailable`；Action 的 promise 被拒绝（网络断开）按 `unavailable`。
- `pending-deletion` → `router.replace(deletionPendingHref(当前路径))`；`signed-out` → `setQueryData(currentUser, null)` + `router.replace(authPageHref("/login", 当前路径))`。两个跳转抽成一个客户端 hook `useCommonFailureRedirect()`，PAP-101 各表单里的同样写法一并换成它（同一分支、同一 PR，不算无关重构）。
- 公开 Action（落地页、忘记密码）没有会话要求：`callIdentity(..., { auth: "optional" })`；它们的 401 不会出现（公开路径），出现按 `unavailable`。

## 11. 时间显示

- 全部按 `Asia/Shanghai` 格式化（`format-time.ts`，`Intl.DateTimeFormat` 固定 `timeZone`），沿用 `PapersInfoRow` 的做法；安全邮件也写「北京时间」。简报 §3.10 写「本地时区」：用户群在国内，固定时区换来服务端与浏览器渲染一致、没有水合差异（第 17 节记一笔）。
- 函数：`formatDate(iso)` → `2026 年 11 月 19 日`；`formatDateTime(iso)` → `2026-10-20 09:12`；`formatClock(iso, now)` → 同一天 `15:42`，否则 `11 月 6 日 15:42`；`formatRelative(iso, now)` → `刚刚`（< 1 分钟）/ `N 分钟前` / `N 小时前`（< 24 小时且同一天）/ `昨天 HH:mm` / `N 天前`（≤ 7 天）/ 再早写日期（今年 `10 月 15 日`，往年带年份，照画板 Security）。
- 相对时间只在客户端组件里算（设备列表是客户端查询，天然不参与服务端渲染）。

## 12. 安全

- 令牌只从片段读，读完立即 `replaceState` 抹掉；不进查询串、不进日志、不进 Server Action 以外的任何请求。
- 落地页打开不发请求；直接 GET 不消费令牌（后端本来就只认 POST）。
- 重设成功后的邮箱经内存 store 带到登录页，不进地址栏。
- Server Action 自带 `Origin` 校验；唯一新增的 Route Handler `GET /api/me/sessions` 只读。
- `reauthenticate` 失败计入登录失败限制（后端），对话框不自动重试。
- 再认证对话框的「忘记密码？」新标签页打开，`rel="noopener"`。

## 13. 测试策略

### 13.1 Vitest（TDD 逐条红绿）

| 层 | 内容 |
|---|---|
| 纯函数 | `format-time` 各分支（跨天、昨天、7 天边界、时区固定）、`email-token` 读取与格式、`FOCUS_PAGES` 与 `safeNextPath`（取消注销页不能做 `next`）、改密码 / 重设 / 忘记密码 / 改邮箱的 Zod schema |
| Action | 每个 Action 的请求方法、路径、请求体与每种结果（按 §3 表与 §7、§8 列出的结果逐条）；`isReauthRequired` 与四个 Action 的 `reauth-required`；`reauthenticate` 的令牌为 `null` 时不写 Cookie、非 `null` 时写；`changePassword` 写 Cookie；`requestDeletion` 不碰 Cookie；`endDeletedSession` 清 Cookie 并 `redirect("/")` |
| Route Handler | `/api/me/sessions`：无 Cookie、200、schema 不符、会话失效清 Cookie、网关出错 |
| proxy | 冷静期整页打开 307（带查询串）、`ACTIVE` 放行、无 Cookie 不查、`Sec-Fetch-Dest: empty` 不查、`FOCUS_PAGES` 不查、网关出错放行、详情页 404 检查仍生效；`leaveAuthPage` 对冷静期账号去取消注销页 |
| `useReauth` | 非 `reauth-required` 原样返回；弹框 → 成功 → 重试一次；取消返回 `cancelled`；并发两次只弹一个框 |
| 组件 | `ReauthDialog`（简报 §4.9 每行）、用户名卡片（展示 / 冷却 / 编辑 / 各错误 / 成功同步）、邮箱卡片（已验证 / 未验证与重发 / 改邮箱各结果 / 待确认块与取消 / 过期不显示）、注销对话框（三屏、用户名比对、各结果、`好的`）、改密码卡片（简报 §4.8 每行）、设备卡片（骨架、失败重试、各行文案组合、踢单个与全部、取消再认证、本机退出、只有本机）、`TokenLandingPage` 状态机（无令牌、格式错、replaceState、各结果、服务错误回到 ready）、四个落地页各自的出口与文案（含验证页的「已登录且已验证」、有无会话两种成功文案）、忘记密码两态与倒计时、登录页预填与成功提示、取消注销页（取消成功回 `next`、失败、退出登录）、`PendingDeletionRedirect`、`FormAlert` 三种 tone、`AuthShell back={null}` |
| 守护 | 现有 `gateway-boundary`、`route-structure`、`design-system/*` 保持全绿 |

每个简报状态（§4.2、§4.7 ~ §4.10、§4.12、§3.18 各格）至少一条测试。

### 13.2 Playwright 冒烟 `tests/e2e/user-center.spec.ts`

- 依赖真网关和 Mailpit：`PATRA_GATEWAY_BASE_URL`、`PATRA_MAILPIT_BASE_URL` 任一缺失就 `test.skip`（CI 上自然跳过）；本机内联 `PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528 PATRA_MAILPIT_BASE_URL=http://100.103.73.27:8025` 跑，`--workers=1`（Next 开发模式并行冷编译会超时）。
- `tests/e2e/support/mailpit.ts`：按收件人查最新一封（`GET /api/v1/search?query=to:"<地址>"`，轮询到 15 秒），读 `GET /api/v1/message/{ID}` 的 `Text`，取 `#token=` 后 43 个字符。identity 链接的域名是 mini 的门户，测试只取令牌，拼 `http://localhost:4000/<落地页>#token=…` 打开。
- 账号：每条用例注册新账号（邮箱 `e2e-<随机>@example.org`、随机用户名与密码，运行时生成，不写进仓库、不打印）。
- 用例（同一 `describe.serial` 之外各自独立）：
  1. **注册 → 验证邮箱 → 资料 → 主页**：注册后横幅出现 → Mailpit 取验证令牌 → `/verify-email` 点按钮 → 有会话文案 → 回首页横幅消失 → 资料页填昵称保存 → 新的匿名上下文打开 `/u/<用户名>` 看到昵称。
  2. **改用户名**：账号页改名 → toast、`@新名` → 匿名上下文打开 `/u/<旧名>` 落在 `/u/<新名>`（主页公开需要已验证邮箱，所以先走验证）→ 改名按钮进入冷却。
  3. **改密码后另一会话失效**：两个浏览器上下文登录同一账号 → A 在安全页看到 2 台设备 → A 改密码 → B 刷新设置页落到登录页 → A 的设备列表只剩本机。
  4. **踢设备**：两个上下文登录 → A 在设备列表点 B 的 `退出` → 行消失 → B 刷新落到登录页（注册 / 登录后 10 分钟内不触发再认证；再认证对话框由组件测试覆盖）。
  5. **忘记密码 → 重设 → 登录**：登录页填邮箱点 `忘记密码？` → 预填 → 发送 → Mailpit → `/reset-password` 设新密码 → 登录页预填邮箱、成功提示 → 新密码登录成功。
  6. **改邮箱**：账号页改邮箱 → 待确认块 → Mailpit 取新邮箱的确认令牌 → `/confirm-email-change` → 成功屏 → 账号页显示新邮箱。
  7. **注销 → 冷静期登录 → 取消**：账号页注销两步 → 结果屏 → `好的` 到首页未登录 → 登录 → 取消注销页 → 地址栏打开 `/papers` 仍回到取消注销页（proxy）→ 取消 → 回到 `next`、toast。
- 速率限制：每条用例用自己的新账号，改邮箱（每天 3 次）、找回密码（同邮箱 60 秒、每天 5 次）、登录失败（5 次锁 15 分钟）都碰不到；用例里不故意输错密码。

### 13.3 预览走查

本机 `portal-dev`（端口 4000，连 mini 网关）在桌面 1440×900 与移动 375×812 下逐页走一遍：账号页三张卡片与注销对话框三屏、安全页两张卡片、再认证对话框（等 10 分钟窗口过期或换一个 10 分钟前登录的会话触发一次）、忘记密码两态、四个落地页（用 Mailpit 里的真实令牌）的就绪 / 成功 / 无效、取消注销页；与画板 `Account` / `Security` / `Landing` / `Main`（含 `-M`）逐屏对照。内置浏览器面板收起时截图不可靠，视觉核对用 Playwright 无头截图。

## 14. 实施时要实测的点

1. 两个独立的 base-ui 模态同时打开（注销对话框 + 再认证对话框）：焦点、`Esc`、遮罩只作用于上层；不行就改嵌套（§6.2）。
2. `reauthenticate` / `changePassword` 写 Cookie 引起的重新渲染不打断进行中的状态，重试的 Action 带上新令牌。
3. `endDeletedSession` 的 `redirect("/")` 确实先于设置页的重新渲染生效，不会闪到登录页。
4. proxy 放宽 matcher 后：静态资源、`/_next/*`、`/api/*` 不进 proxy；首页、文献页的静态渲染不受影响（`pnpm build` 的路由表与 PAP-101 对比）。
5. 冷静期整页打开 `/papers` 被 307 到取消注销页，`next` 带回 `/papers`；取消后回到 `/papers`。
6. 落地页 `replaceState` 后地址栏与历史记录里没有 `#token=`；后退不会回到带令牌的地址。

## 15. 由我补的文案

简报没写、由我补（看到不对就改）：

- 重设密码 429 提示条：`提交太频繁，请 N 分钟后再试`（照画板 Landing）
- 改邮箱输入为空：`请输入邮箱`
- 改用户名前端发现与当前相同：用后端 `UNCHANGED` 的 `新用户名和现在的一样`
- 待确认块跨天时的时刻：`M 月 D 日 HH:mm 前有效`
- 相对时间 2 ~ 7 天：`N 天前`
- 改邮箱发出确认邮件：不 toast（待确认块就是反馈；简报 §4.4 的 toast 一览里也没有这一条）

## 16. 要同步改的文档和 Issue

- `patra-portal/README.md`：新路由、再认证机制、冷静期跳转（proxy 的 matcher 变化）、`PATRA_MAILPIT_BASE_URL` 与冒烟的跑法。
- 简报 §10「落地时的调整」：时间固定 Asia/Shanghai（§11）；再认证拦截点在 Action 调用层（§4.1）。
- PAP-101 spec 第 18.4 节的「留给 PAP-102」逐条在本 spec 第 18 节（实施结果）里收口。
- Linear PAP-102：To-Do 勾选、评论附 AC 证据；推 Review 等用户验收之后。
- 实施时若发现 PAP-101 已有可用的小胶囊写法（主页的 `仅你可见`），`status-pill` 改为复用它，不新建。

## 17. 设计中由我定的点

1. 再认证在「客户端调用 Server Action」这一层拦，不是 Issue 字面的「网关客户端层」（§4.1）。
2. `REAUTH_REQUIRED` 由 `isReauthRequired` 识别、只进四个要再认证的 Action，不并进 `CommonFailure`（§6.1）；`ReauthProvider` 挂在设置页 layout（§6.2）。
3. 冷静期跳转：proxy 只查带会话 Cookie 的整页打开 + 导航栏客户端兜底 + `requireUser`；已登录用户每次整页打开多一次 `/auth/me`（§4.2、§9）。
4. 专注布局页不做冷静期跳转（§9.1）。
5. 登录设备列表走 Route Handler + TanStack Query（§4.3）。
6. 注销成功时 Action 不清 Cookie，`好的` 再清并跳首页（§7.1）。
7. 时间一律 Asia/Shanghai（§11），偏离简报 §3.10 的「本地时区」。
8. 登录页 ↔ 忘记密码 ↔ 重设密码之间用内存 store 传邮箱与提示（§8.1）。
9. 验证邮箱成功后按「重新取到的当前用户」选有 / 无会话文案；不做简报「已登录且已验证打开就显示已验证」的捷径，改在无效屏里显示已验证（§8.3）。重设密码、撤销改邮箱成功后失效当前用户而不是直接写未登录（§8.3）。
10. 落地页片段里没有令牌或格式不对时直接显示无效屏，不等点按钮（§8.3）。
11. 冒烟覆盖七条路径，超出 Issue 写的三条：PAP-101 页面与四个落地页里能用真邮件走通的都放进来（§13.2）。
12. 补的文案（§15）。

## 18. 实施结果（2026-10-23）

按计划 `docs/patra/plans/2026-10-23-portal-user-center-account-security.md` 的 22 个任务在 `feat/v0.12-user-center-portal` 上内联 TDD 完成，未推送、未开 PR（与 PAP-101 合一个前端 PR）。

### 18.1 门禁

- `pnpm lint`、`pnpm typecheck`、`pnpm test`（Vitest 213 个文件 2035 条）全绿；`pnpm build` 通过。
- Playwright：`tests/e2e/user-center.spec.ts` 七条 + `auth.spec.ts` 对 mini 网关与 Mailpit `--workers=1` 全过；全部 e2e 一起跑时 `detail-rail-sticky` 偶发一条冷编译超时，单独重跑通过（与本 Issue 无关的老问题）。不带环境变量时七条全部跳过。

### 18.2 实测（spec §14）

1. 两个独立的 base-ui 模态叠放：jsdom 里上层可聚焦、输入，`Esc` 只关上层（`tests/components/reauth/stacked-dialogs.test.tsx`）；真浏览器复核见 18.4。
2. 再认证、改密码写 Cookie 后的重新渲染不打断进行中状态：改密码 e2e（本机仍登录、另一台设备回登录页）与再认证真浏览器核对通过。
3. `endDeletedSession` 的 `redirect("/")` 先于设置页重新渲染生效：注销 e2e 落在首页、导航栏是「登录」，没有闪到登录页。
4. proxy 放宽 matcher 后静态资源、`/api/*` 不进 proxy；首页本来就是动态渲染，路由表无变化。e2e 暴露了一个回归并已修（18.3 第 1 条）。
5. 冷静期整页打开 `/papers` 被 307 到 `/account/deletion-pending?next=%2Fpapers`，取消后回到 `/papers`（e2e）。
6. 落地页读完令牌后地址栏不含 `#token=`（组件测试断言 `location.hash` 为空；e2e 断言 URL 已去掉片段）。

### 18.3 实施中的偏离与修正

1. **proxy 列表页回归**（e2e 发现）：matcher 覆盖全部页面后，`/papers`、`/journals` 列表页进入了详情页的存在检查，空 id 被改写到 404 页。改为只有恰好 `/papers/:id`、`/journals/:id` 两段的地址才检查。
2. **落地页在严格模式下读两次片段**（e2e 发现）：开发模式 effect 跑两次，第二次时片段已被抹掉，显示「链接无效」。令牌改为记在 ref 里只读一次。
3. **倒计时首帧多出一截**（高负载下全量单测暴露）：换了新的截止时间后第一次渲染用的是旧的「当前时间」。`useCountdown` 改为渲染时按当前时间算。PAP-101 的 `ResendVerificationButton` 有同样写法，未改（留作后续）。
4. 会话 `id` 只接受字符串（计划写数字或字符串）：雪花 ID 作为 JSON 数字解析时已丢精度；后端按字符串序列化。
5. `REAUTH_REQUIRED` 没有并进 `CommonFailure`，只在四个要再认证的 Action 里由 `isReauthRequired` 识别（spec §6.1 已按此修订）。
6. 按画板补的组件与写法：专注布局标题上方的 44px 状态图标（`StateIcon`，`AuthShell` / `Dialog` 加 `icon`）、文字按钮与危险按钮（`portal-ui` 的 `btnText`、`btnDanger`、`btnDangerOutline`）、状态小胶囊（`StatusPill`，主页「仅你可见」一并改用）、注销第 2 屏标题「确认注销」、找回密码已发送态的信息图标、验证页无效屏的整宽「重新发送验证邮件」（`ResendVerificationButton variant="block"`）。
7. 文案按画板：重设密码 429 是「提交太频繁，请 N 分钟后再试」（§15 原写「请求太频繁」）；验证页「已登录且已验证」的说法是「你的邮箱 X 已经验证过了。」；设备列表超过 7 天写「10 月 15 日」（往年带年份），§11 原写 `YYYY-MM-DD`。
8. 窄屏下「退出其他所有设备」落到卡片说明下方（`SettingsCard asidePlacement="block"`），照画板 `Security-M`。
9. 落地页状态机加了 `leaving` 结果：重设成功跳登录页期间按钮保持进行中，不能重复提交已消费的令牌。
10. 再认证对话框用组件内状态而不是 RHF（只有一个密码框）；`Dialog` 加 `initialFocus`，焦点落在密码框而不是标签行里的「忘记密码？」。

### 18.4 预览走查

本机门户连 mini 网关，Playwright 无头浏览器在 1440×900 与 375×812 下截了账号页（默认、冷却 + 待确认 + 未验证）、注销对话框三屏、安全页、四个落地页就绪态与无效态、忘记密码、登录页、取消注销页（桌面、移动），与画板 `Account` / `Security` / `Landing` / `Main`（含 `-M`）离线截图逐屏对照，差异已修（18.3 第 7、8 条）。登录页「还没有账号？注册」画板居中、页面左对齐，是 v0.8 起的旧写法，未动。
