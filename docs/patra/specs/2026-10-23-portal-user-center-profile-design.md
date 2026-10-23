# 门户用户中心：注册、导航、设置页、个人资料、隐私、公开主页工程设计（PAP-101）

> **状态**：已实施（2026-10-23），待用户验收；实施结果见第 18 节
> **Issue**：Linear PAP-101（GitHub #267），v0.12 User Center 的第一个前端 Issue
> **分支**：`feat/v0.12-user-center-portal`（PAP-102 在同一分支继续，两个 Issue 合一个前端 PR）
> **产品定义**：`docs/patra/release-specs/v0.12-user-center.md` 2.1 节与第 4 节
> **页面、状态与文案**：`patra-portal/docs/design-briefs/v0.12/user-center.md`（下称「简报」）；画板快照 `docs/patra/design/snapshots/2026-10-22-user-center-hifi.zip`
> **后端契约**：以 identity / catalog / gateway 的代码为准，参考 `docs/patra/specs/2026-10-20-identity-account-rebuild-design.md`、`2026-10-21-identity-email-verification-design.md`、`2026-10-22-identity-profile-design.md`、`2026-10-22-identity-avatar-design.md`、`2026-10-22-identity-login-devices-design.md`、`2026-10-21-catalog-org-mesh-suggest-design.md`，以及 Linear PAP-101 上 PAP-95 / PAP-97 / PAP-99 留的三条契约评论

## 1. 要解决的问题

后端 PAP-93 ~ PAP-100 已合入 main 并部署到 mini，门户还停在 v0.8：

1. **登录、注册现在是坏的**。登录 / 注册响应已改成 `{ sessionToken, user }`，门户 `actions.ts` 的 `signedInSchema` 还读顶层 `userId` / `email`，解析失败就当「服务不可用」，Cookie 也不写。注册还缺必填的 `username`。
2. 用户没有可见身份：导航栏只有邮箱首字母，没有设置页、资料页、公开主页。
3. 门户不转发浏览器 IP 和 User-Agent，也不带设备标识，登录记录、新设备提醒、安全邮件里的设备和 IP 都是门户容器自己的。

## 2. 范围

**本 Issue 做**（简报 §2.1 归属 PAP-101 的各屏，加上让它们跑起来的底座）：

- 注册页加用户名；登录 / 注册改读新响应、带设备标识；登录 / 注册后 `status = PENDING_DELETION` 时跳取消注销页（页面本身归 PAP-102）
- `/auth/me` 新字段进 `CurrentUser`；导航栏（桌面、移动）头像、昵称、菜单入口
- 未验证邮箱横幅（含「重新发送」与倒计时）
- 设置页外壳（四个分区的导航，账号、安全两项指向 PAP-102 要建的页面）、个人资料页、隐私页
- 头像：默认头像、裁剪上传、移除、同源读取转发
- 机构选择器（ROR + 自由文本）、关注领域选择器（MeSH）
- 公开主页 `/u/[username]`：本人 / 别人 / 匿名三种视角、404、旧名 307 跳转、`noindex`
- 转发链路：剥掉入站 `X-Forwarded-For`、向 identity 转发浏览器 IP 与 UA、设备标识 Cookie
- Vitest 单元测试

**不做**（PAP-102）：账号页、安全页、再认证对话框、忘记密码、四个邮件落地页（含 `/verify-email`）、取消注销页、Playwright smoke；「`PENDING_DELETION` 状态下打开任何页面都跳取消注销页」（简报 §2.2 规则 3 的全站部分）。本 Issue 只在自己发起的请求上处理 `PENDING_DELETION`（第 12.4 节）。

## 3. 已定的前提

- 产品规则、字段长度、文案以 release spec 2.1 节与简报为准，本 spec 不重复，只写工程做法。
- 后端契约以代码为准。2026-10-23 逐个接口核对过的要点：
  - 登录 / 注册 / `/auth/me` 的 `user` 同构：`userId`（字符串）、`email`、`emailVerified`、`username`、`displayName`（永远非空，没设昵称时是用户名）、`avatarUrl`（`/users/{username}/avatar?v={16 位十六进制}` 或 `null`）、`avatarPalette`（0–11）、`status`（`ACTIVE` / `PENDING_DELETION`）、`deletionDueAt`、`nextUsernameChangeAt`（冷却过后仍是过去的时间，不回 `null`）、`accountType`。
  - 注册 409 靠 `errors[].field` 区分：`email` / `ALREADY_REGISTERED`、`username` / `ALREADY_TAKEN`，可同时出现。用户名原因码按判定顺序：`REQUIRED`、`INVALID_CHARACTER`、`TOO_SHORT`、`TOO_LONG`、`INVALID_FORMAT`、`DIGITS_ONLY`、`RESERVED`。
  - ProblemDetail 的 `traits` 是集合序列化，顺序不定，一律用「包含」判断。401 分两种：`traits` 含 `PASSWORD_MISMATCH` 的只是密码错（本 Issue 用不到，判定函数先写好给 PAP-102），其余 401（`GW-0401`、`IDN-0401`）是会话失效。
  - 重发验证邮件是 `POST /patra-identity/me/email/verification/resend`（要登录、无请求体）：202 `{ email, nextResendAt }`；429 带 `retryAfterSeconds`；409 `traits` 含 `EMAIL_ALREADY_VERIFIED` 是已验证、含 `PENDING_DELETION` 是冷静期。
  - `GET /me/profile` 的 `displayName` 是原值（`null` = 跟随用户名），`researchInterests` 是 `[{ ui, name }]`，`organization` 是 `null` / `{ rorId, name }` / `{ rorId: null, name }`。`PUT` 整份替换，请求里 `researchInterests` 只传 UI 数组。没有乐观锁；唯一的并发 409 是「沿用的名称快照被并发删掉」（`traits` 只有 `CONFLICT`），重试即可。503 `IDN-0503` = catalog 不可用、没保存。
  - `GET /users/{username}`：带无效令牌按匿名处理，不会 401；`audience` 在邮箱未验证或账号非 `ACTIVE` 时一律 `SELF`；邮箱未验证时 `links` 对本人也为空；`professional` 全空时为 `null`；`indexable = audience == PUBLIC && searchIndexing`。旧名回 301，体是 `{ username }`，`Location` 缺 `/patra-identity` 前缀，所以必须 `redirect: "manual"` 并读体。
  - 头像 `GET /users/{username}/avatar` 的 `size`（128 / 512）和 `v` 都必填，缺一即 404；200 带 `ETag`、`Cache-Control: public, max-age=31536000, immutable`，支持 304。上传 part 名 `file`，上限 5 MB（超了 413 `IDN-0413`），422 `errors[0]` 是 `file` 字段，原因码 `REQUIRED` / `UNSUPPORTED_TYPE` / `TOO_SMALL` / `TOO_MANY_PIXELS` / `UNREADABLE`。
  - catalog 建议接口：`GET /patra-catalog/portal/organizations/suggest?q=&limit=`、`/portal/mesh/suggest`，匿名，响应 `{ items: [...] }`。机构项 `{ rorId, displayName, zhName, matchedName, countryCode, cityName, types }`（是 `cityName` 不是 `city`），MeSH 项 `{ ui, name, matchedTerm, treeNumbers }`。`q` 经 NFKC、去首尾空白、折叠空白后须 2–100 个码点，否则 422；`limit` 1–20，默认 10。
- 绿地项目：所有改动按最终形态写，不留兼容分支。

## 4. 方案比较（只列有分歧的三处）

### 4.1 怎么丢掉浏览器带来的 `X-Forwarded-For`

Next 16 在 `base-server.js` 里做 `req.headers['x-forwarded-for'] ??= socket.remoteAddress`。浏览器自己带了这个头，Next 就保留；proxy、Server Action、Route Handler 都拿不到 socket 地址。所以只能在 Next 处理请求之前删掉这个头。

| 方案 | 做法 | 取舍 |
|---|---|---|
| **A. 预加载模块（选）** | `patra-portal/server/strip-forwarded-for.mjs`：给 `http.Server.prototype.emit` 套一层，`request` 事件先 `delete req.headers['x-forwarded-for']`。Dockerfile 的 CMD 改成 `node --import ./strip-forwarded-for.mjs server.js` | 不碰 Next 生成的 `server.js`；standalone 的追踪与优化不受影响；十几行，可以起一个真的 HTTP 服务做单测 |
| B. 自定义 server（官方 custom-server 文档） | 自己 `next({ ... })` + `http.createServer` | 文档写明自定义 server 不走 standalone 的追踪，镜像要带全量依赖；为删一个头换掉整个启动方式不值 |
| C. 门户前面加反向代理 | compose 里加一层 nginx / caddy 覆盖该头 | 多一个容器和一份配置；公开上线时反正会有反向代理，到时可以叠加，但现在不该为它加组件 |

开发模式（`next dev`）不装这个模块：本机门户连 mini 网关时不在网关的受信名单里，网关会把它带的转发头全部剥掉。

### 4.2 IP 与 UA 怎么带给网关

- **选**：`gatewayFetch` / `gatewayStream` 新增显式参数 `client?: ClientOrigin`，只按它写 `X-Forwarded-For` 和 `User-Agent`；不在里面偷偷调 `headers()`。原因：proxy 也调 `gatewayFetch`，proxy 的上下文没有 `next/headers`；在 `gatewayFetch` 里 try/catch `headers()` 会吞掉 Next 用来切动态渲染的信号。
- 新模块 `src/lib/client-origin.ts`（server-only）：`readClientOrigin()` 从 `headers()` 读，`clientOriginFromHeaders(headers)` 是纯函数（proxy 用 `req.headers` 调它，测试也测它）。
- **哪些请求带**：所有发往 identity 的请求（登录、注册、登出、`/auth/me`、资料、隐私、头像上传 / 删除、重发验证邮件、公开主页）和头像读取的转发都带。**catalog 的公开读不带**：catalog 不用 IP 和 UA，带上就得让每个 RSC 取数函数都读请求头、动十来个现有测试，换来的东西 PAP-99 也用不到。这一处偏离了 PAP-99 契约评论里「全部请求都带最简单」的建议（那句是为了省事，不是要求），强制的「必须丢掉入站 XFF」不受影响。

### 4.3 头像上传走 Server Action 还是 Route Handler

- **选 Route Handler** `POST /api/me/avatar`、`DELETE /api/me/avatar`。Server Action 的请求体默认上限 1 MB，裁好的 PNG（1024² 以内）可能超过；为一个动作调大全站 Server Action 的上限不划算。Route Handler 不受 proxy 的 10 MB 上限影响（proxy 的 matcher 不含它）。和头像读取 `/api/users/[username]/avatar` 放在一起，都是 `multipart` / 二进制的透传。
- 其余写操作（资料、隐私、重发验证邮件、登录、注册）沿用 v0.8 的 Server Action。

## 5. 模块与组件

```
patra-portal/
├── server/strip-forwarded-for.mjs          # 新：生产入口预加载（§6.1）
├── Dockerfile                              # 改：COPY 预加载模块，CMD 带 --import
└── src/
    ├── lib/
    │   ├── gateway.ts                      # 改：方法集、body、redirect、client（§6.2）
    │   ├── client-origin.ts                # 新：ClientOrigin、IP 校验与规范化（§6.1）
    │   ├── auth/
    │   │   ├── actions.ts                  # 改：新响应、username、deviceId、409 拆分
    │   │   ├── current-user.ts             # 改：完整 user schema；React cache 包装
    │   │   ├── session-cookie.ts           # 改：加设备标识 Cookie（全仓只有它调 cookies()）
    │   │   ├── types.ts / messages.ts / form-schemas.ts  # 改
    │   │   └── require-user.ts             # 新：设置页的登录守卫（§9.1）
    │   ├── identity/
    │   │   ├── problem.ts                  # 新：ProblemDetail 解析、traits 判定、字段错误
    │   │   ├── user-schema.ts              # 新：user 的 Zod schema（登录 / 注册 / me 共用）
    │   │   ├── profile.ts                  # 新：GET /me/profile、/me/privacy（RSC 用）
    │   │   ├── profile-actions.ts          # 新："use server"：saveProfile、savePrivacy
    │   │   ├── verification-actions.ts     # 新："use server"：resendVerificationEmail
    │   │   ├── public-profile.ts           # 新：GET /users/{username}（RSC 用）
    │   │   ├── avatar.ts                   # 新：avatarSrc、avatarInitial、调色板
    │   │   └── username-rules.ts           # 新：前端用户名规则（与后端同一条）
    │   ├── portal-api/suggest.ts           # 新：catalog 两个建议接口（Route Handler 用）
    │   └── grapheme.ts                     # 新：Intl.Segmenter 计数
    ├── store/verification-resend.ts        # 新：重发冷却（zustand，§8.4）
    ├── app/
    │   ├── api/me/avatar/route.ts          # 新：POST / DELETE
    │   ├── api/users/[username]/avatar/route.ts   # 新：GET 转发
    │   ├── api/organizations/suggest/route.ts     # 新
    │   ├── api/mesh/suggest/route.ts              # 新
    │   ├── settings/layout.tsx · page.tsx · profile/page.tsx · privacy/page.tsx   # 新
    │   └── u/[username]/page.tsx · not-found.tsx  # 新
    └── components/
        ├── ui/dialog.tsx · combobox.tsx · slider.tsx   # 新：base-ui 原语（shadcn base 版，token 化）
        ├── user/Avatar.tsx                  # 新："use client"（图片加载失败要回落默认头像）
        ├── portal/UnverifiedEmailBanner.tsx · ResendVerificationButton.tsx   # 新
        ├── portal/NavAccount.tsx · UserMenu.tsx · MobileAccount.tsx · TopNav.tsx   # 改
        ├── auth/RegisterForm.tsx · LoginForm.tsx · use-auth-submit.ts · UsernameField.tsx   # 改 / 新
        ├── settings/SettingsShell.tsx · SettingsNav.tsx · SettingsCard.tsx · StickySaveBar.tsx · LeaveGuard.tsx   # 新
        ├── settings/profile/ProfileForm.tsx · AvatarCard.tsx · AvatarCropDialog.tsx · LinkListField.tsx · CharCounter.tsx · OrganizationPicker.tsx · MeshInterestPicker.tsx   # 新
        ├── settings/privacy/PrivacyForm.tsx  # 新
        └── profile/ProfileHeader.tsx · ProfileAbout.tsx · ProfileVisibilityNotice.tsx   # 新
```

原语选择（我定的）：
- 对话框、组合框、滑块用 `@base-ui/react` 已有的 `Dialog`、`Combobox`、`Slider`，按现有 `dropdown-menu.tsx` 的写法包一层 token 化的薄组件放进 `src/components/ui/`。
- 单选组和开关用 `portal-ui.ts` 里已有的原生 `RADIO_INPUT`、`SWITCH_INPUT`（设计系统第 12 版的开关就是它们），不装 base-ui 的 `RadioGroup` / `Switch`：原生控件的表单语义和读屏都是现成的。
- 身份下拉用原生 `<select>` 套输入框样式加 `ChevronDown`：手机上弹系统选择器，九个固定选项不需要自绘弹层。

新依赖（我定的，评审时请看一眼）：
- `react-easy-crop`：头像裁剪的平移、缩放、滚轮、双指捏合、键盘方向键都现成（MIT，周下载量百万级），不自写指针与手势计算。
- `@dnd-kit/core` + `@dnd-kit/sortable`：关注领域的桌面拖动排序（换行排列的胶囊列表，`rectSortingStrategy`）。

## 6. 转发链路与网关客户端

### 6.1 浏览器 IP

- `server/strip-forwarded-for.mjs`：`Server.prototype.emit` 包一层，`event === "request"` 时 `delete req.headers["x-forwarded-for"]`，其余原样。导出 `install()` 供测试调用；文件被 `--import` 加载时自动安装。只删这一个头：`X-Forwarded-Host` / `-Proto` / `-Port` 门户从不往网关发（§6.2），Next 自己用它们没有安全影响。
- 删掉之后 Next 填进去的就是 socket 地址。`clientOriginFromHeaders(headers)`：
  - `ip`：取 `x-forwarded-for`，必须是**一个**值；`::ffff:a.b.c.d` 还原成 IPv4；去掉 IPv6 的 zone（`%eth0`）；最后用 `node:net` 的 `isIP` 判是不是 IP 字面量，不是就是 `null`。含逗号（多个值）一律 `null`：装了预加载模块后不可能出现，出现就说明入口没装，宁可不发也不发浏览器给的值。
  - `userAgent`：`user-agent` 原值；没有或放不进请求头（`canSendToken` 同一条字符集规则）时为 `null`。
- Dockerfile：`COPY --from=builder /app/server/strip-forwarded-for.mjs ./`，`CMD ["node", "--import", "./strip-forwarded-for.mjs", "server.js"]`。

### 6.2 `gatewayFetch` / `gatewayStream`

```ts
interface GatewayRequestInit {
  method?: "GET" | "HEAD" | "POST" | "PUT" | "DELETE";
  json?: unknown;                 // 与 body 互斥
  body?: FormData;                // multipart，Content-Type 由 fetch 带边界生成
  token?: string;
  client?: ClientOrigin;          // 有值时写 X-Forwarded-For（ip 非空时）与 User-Agent（非空时）
  redirect?: "follow" | "manual"; // 默认 "manual"（§6.3）
  timeoutMs?: number;
}
```

- 只写这两个转发头，不写 `X-Forwarded-Host` / `-Proto` / `-Port` / `-Prefix`、`Forwarded`（网关的 `RemoteIpValve` 对受信连接会按其中三个改写主机与协议）。
- `gatewayStream` 同样加 `client`，头像读取转发时带上。
- 守护测试 `gateway-boundary.test.ts` 不用改：仍然只有 `gateway.ts` 直接 `fetch`、写 `Authorization`。

### 6.3 重定向默认不跟随

`redirect` 默认改成 `"manual"`：网关已设 `DONT_FOLLOW`，上游的 3xx 全部原样到门户；跟随它们只会去请求缺了服务前缀的地址。现有调用方没有依赖跟随（catalog 门户接口不回 3xx）。调用方需要时自己读 3xx。字段保留 `"follow"` 选项是因为 `fetch` 的类型如此，没有调用方用。

> 实现时若发现「默认 manual」让某个现有调用方的测试或行为变了，就改成只在 `/users/{username}` 上显式传 `"manual"`，并在本节记一笔。

### 6.4 设备标识 Cookie

放进 `session-cookie.ts`（守护测试规定全仓只有它调 `cookies()`）：

- 名 `patra_device`，`Secure` 开时 `__Host-patra_device`（与会话 Cookie 同一个 `PATRA_SESSION_COOKIE_SECURE` 开关）；`HttpOnly`、`SameSite=Lax`、`Path=/`、`Max-Age` 400 天。
- `ensureDeviceId(): Promise<string>`：读 Cookie，匹配 `^[A-Za-z0-9_-]{43}$` 就原样返回；否则用 `crypto.getRandomValues(new Uint8Array(32))` 生成，base64url 无填充（43 个字符），写回后返回。每次都重写一遍以顺延 400 天。
- 只在登录、注册的 Server Action 里调用；请求体带 `deviceId`。登出、会话失效都不清它。

## 7. 当前用户

- `src/lib/identity/user-schema.ts`：`user` 的 Zod schema（§3 的字段，`status` 只认 `ACTIVE` / `PENDING_DELETION`，`avatarPalette` 0–11 整数，日期是 ISO 字符串原样保留）。`CurrentUser` 类型由它推出。登录、注册、`/auth/me` 共用。
- `lookupSessionByToken(token, { timeoutMs, client })` 多一个 `client` 参数；`lookupSession()` 自己读 Cookie 和 `readClientOrigin()`，并用 React `cache()` 包住，同一次渲染里（设置页的 layout 与 page、主页的 `generateMetadata` 与 page）只请求一次网关。
- `/api/auth/me` 照旧，返回完整 `user`。浏览器端 `useCurrentUser()` 不变，类型变宽。
- 头像、昵称有变化后：资料保存成功 → `invalidateQueries(currentUser)`；头像上传 / 删除成功 → `setQueryData` 改 `avatarUrl`（上传）或置 `null`（删除）后再 `invalidate`。服务端渲染的页面用 `router.refresh()`。

## 8. 注册、登录、导航栏、横幅

### 8.1 注册页（简报 §3.1、§4.1）

- 字段顺序：邮箱、用户名、密码、确认密码；删掉「请牢记密码，目前不支持找回密码。」
- `UsernameField`：输入时把大写转小写（`onChange` 里改写值并保持光标位置），`autocomplete="username"`、`spellCheck={false}`、`autoCapitalize="none"`；常驻提示 `3–30 位，小写字母、数字、_ 或 -，用作主页地址 /u/<值>`，空时 `/u/你的用户名`。PAP-102 的改用户名卡片复用它。
- 前端规则 `username-rules.ts`：与后端判定顺序一致（`REQUIRED` → `INVALID_CHARACTER` → `TOO_SHORT` → `TOO_LONG` → `INVALID_FORMAT` → `DIGITS_ONLY`），返回原因码，文案统一由 `messages.ts` 按原因码取，后端返回的原因码走同一张表。保留字、占用只由后端判。
- `register(input)`：入参 `{ email, username, password }`；Action 里先 `ensureDeviceId()`，请求体加 `deviceId`、带 `client`。结果：
  - 201 → 写会话 Cookie，返回 `{ ok: true, user }`
  - 422 → `invalid-fields`（`email` / `username` / `password` 三个字段；`deviceId` / `clientType` 出错按 `unavailable` 处理并记日志：那是门户自己的 bug）
  - 409 → `taken`，带 `email: boolean`、`username: boolean`（按 `errors[]` 判；一条都对不上按 `unavailable`）
  - 其余 → `unavailable`
- 409 两条同时出现时两处都显示，焦点移到邮箱（简报 §4.1）。

### 8.2 登录

- 请求体加 `deviceId`、带 `client`，改读 `{ sessionToken, user }`。
- 成功后 `use-auth-submit` 按 `user.status` 分流：`ACTIVE` 照旧（toast、回 `next`）；`PENDING_DELETION` 不弹 toast，`router.replace("/account/deletion-pending?next=<next>")`（简报 §2.2 规则 2；页面由 PAP-102 建）。注册不可能返回 `PENDING_DELETION`，但走同一个分流。
- 注册成功另外把 `verification-resend` 的冷却设为「现在 + 60 秒」（§8.4）。

### 8.3 导航栏（简报 §3.3）

- `Avatar` 组件：`user` → 有 `avatarUrl` 时显示图片（`<img>`，`alt=""`，加载出错回落默认头像），否则默认头像；尺寸档 36 / 40 / 72 / 88 / 96 / 128，36 / 40 取 128 档图，72 及以上取 512 档。
- 默认头像：`avatarInitial(displayName, username)` 用 `Intl.Segmenter` 取字素，跳过开头的标点（`\p{P}`）和空白（`\p{White_Space}`），拉丁字母转大写，其余原样；一个都取不到时用用户名首字母大写。底色按 `avatarPalette` 查**静态** class 表（`bg-clay-700`、`bg-slate-500`、`bg-moss-500`、`bg-ink-700`、`bg-rust-500`、`bg-amber-700`、`bg-clay-900`、`bg-moss-700`、`bg-ink-500`、`bg-clay-800`、`bg-ink-900`、`bg-ink-600`，12 个字面量，不拼字符串），字色 `text-paper-50`、衬线、字号约直径 × 0.42，字 `aria-hidden`。
- `avatarSrc(avatarUrl, size)`：校验 `avatarUrl` 形如 `/users/{用户名}/avatar?v={16 位十六进制}`，换成 `/api/users/{用户名}/avatar?v=…&size=128|512`；形状不对返回 `null`（走默认头像）。
- `UserMenu`：触发器 36px 头像；菜单头部 40px 头像 + 昵称（截断，`title` 全文）/ `@用户名`（等宽小字）；分隔线；`个人主页`（`UserRound`，`/u/<用户名>`）、`设置`（`Settings`，`/settings/profile`）；分隔线；`退出登录`（不变）。菜单宽 240px。邮箱不再出现。
- `MobileAccount`：已登录形态改为「40px 头像 + 昵称 / @用户名」一行，下面并排 `个人主页`、`设置` 两个次按钮，再下面整行 `退出登录`；点链接关菜单。

### 8.4 未验证邮箱横幅与重发（简报 §3.4）

- `UnverifiedEmailBanner`（客户端）：`useCurrentUser()` 已登录、`emailVerified === false`、`status === "ACTIVE"` 时渲染。挂在 `TopNav` 返回的 `<header>` 之后（同一个 fragment），所以所有带导航栏的页面都有、随页面滚走；专注布局没有 `TopNav`，自然没有。桌面一行、移动两行（「更改邮箱」只在桌面，指向 `/settings/account#email`，页面归 PAP-102）。
- `ResendVerificationButton`：横幅、主页本人提示条共用（PAP-102 的账号页、验证落地页也会用）。状态机：可发送 → 发送中 → 冷却倒计时（`已发送 · N 秒后可重发`）/ 限流（`发送太频繁，请 N 分钟后再试`，< 60 秒显示秒并倒计时）→ 回到可发送。倒计时只在结束时经 `aria-live="polite"` 播报。
- 冷却状态放 zustand `src/store/verification-resend.ts`：`{ availableAt: number | null, limited: boolean }`，横幅和主页提示条共享，同一标签页里换页不丢。整页刷新会丢，这时点了得到 429，按限流态显示（简报 §10 第 8 条已接受）。
- `resendVerificationEmail()` Server Action 结果：`sent`（带 `nextResendAt`）/ `throttled`（`retryAfterSeconds`）/ `already-verified` / `pending-deletion` / `signed-out` / `unavailable`。`already-verified` → `invalidate(currentUser)` 后横幅消失，toast `邮箱已验证`；`unavailable` → 错误 toast `没有发出去，请稍后再试`。

## 9. 设置页

### 9.1 路由与守卫

- `/settings` → `redirect("/settings/profile")`。
- 每个设置页开头 `const user = await requireUser("/settings/profile")`：`lookupSession()`（有缓存）的结果是 `authenticated` 就返回 user；`anonymous` / `rejected` → `redirect("/login?next=<该页路径>")`；`unavailable` → 抛错交给 `error.tsx`。页面自己知道路径，所以 `next` 准确；layout 拿不到路径，不做守卫。
- `settings/layout.tsx`（RSC）：同样调 `lookupSession()`（同一次渲染共用一次网关请求）取 `@用户名`，渲染 `TopNav` + `SettingsShell` + `Footer`；未登录时只渲染外壳（页面会抛 redirect）。
- 所有设置页 `robots: noindex`，标题 `个人资料 · 设置 · Patra`、`隐私 · 设置 · Patra`。

### 9.2 外壳（简报 §3.5）

- `SettingsShell`：h1 `设置` + 右端 `查看我的主页 ↗` + `@用户名`；桌面两栏（左 200px 吸顶分区导航、右内容列 ≤ 640px），≤ 880px 改为吸顶在导航栏下的四个等宽分段标签。
- `SettingsNav`（客户端，`usePathname` 判当前项）：四项 `个人资料`、`账号`、`安全`、`隐私`（移动 `资料` `账号` `安全` `隐私`）都是 `<Link>`。账号、安全两页由 PAP-102 建，在此之前点进去是 404，同一个 PR 内补齐。
- `SettingsCard`：标题（`text-lg` 衬线）+ 说明（`fg-3`）+ 内容，卡片间距 32px，`variant="danger"` 留给 PAP-102 的注销卡片。

### 9.3 个人资料页（简报 §3.6 ~ §3.9、§4.5、§4.6）

- 页面（RSC）：`requireUser` 后并行取 `GET /me/profile` 与 `GET /me/privacy`（后者只为「专业背景 · 当前：所有人可见 / 仅自己可见」那一行），交给客户端 `ProfileForm`。
- **表单**：React Hook Form + Zod，`useFieldArray` 管链接；默认值由 `GET /me/profile` 转成表单值（`displayName: null` → 空串、`links` 的 `title: null` → 空串、`organization` → 选择器状态、`researchInterests` → `[{ ui, name }]`）。前端校验与后端同规则（字素数用 `grapheme.ts`；`bio` 行数按 `\n` 计），文案按原因码查 `messages.ts`。
- **保存**：`saveProfile(values)` Server Action 把表单值转成 PUT 请求体（空串 → `null`；链接去掉空标题；机构 `{ rorId }` / `{ name }` / `null`；关注领域只发 UI 数组）。结果：`saved`（新的 profile）/ `invalid-fields`（`errors[]` 原样带回，字段路径如 `links[1].url`、`researchInterests[3]`）/ `catalog-unavailable` / `conflict` / `pending-deletion` / `signed-out` / `unavailable`。
  - `invalid-fields`：按路径映射到 RHF 字段（`links.1.url`）或选择器（机构、某个胶囊），`setError` 后聚焦并滚到第一个出错字段；关注领域的某一项出错时该胶囊红描边、下方一行文案。
  - 503 / 其他：保存条上方的提示条（简报 §4.5 文案）。
  - 409（只有 `CONFLICT`、不含 `PENDING_DELETION`）：后端没有乐观锁，这个 409 只是「沿用的名称快照被另一次并发保存删掉」，原样再提交一次就会重新向 catalog 取名。所以 Action 内部自动重试一次；仍是 409 才返回 `conflict`，提示条 `资料没有保存成功，请再保存一次`，**保留全部输入**。简报 §4.5 的「请刷新后再保存」+「刷新」是按乐观锁写的，刷新会丢掉未保存的修改，不采用（简报 §10 记一笔）。
  - `pending-deletion`：跳 `/account/deletion-pending?next=/settings/profile`；`signed-out`：跳 `/login?next=/settings/profile`。
  - `saved`：表单 `reset` 成保存后的值、保存条收起、toast `资料已保存`、`invalidate(currentUser)`、`router.refresh()`。
- **吸底保存条** `StickySaveBar`：`isDirty` 时出现；`放弃` = `reset()`；保存中字段只读。
- **离开提醒** `LeaveGuard`（`isDirty` 时启用）：App Router 没有拦截路由的 API，做两件事——
  1. `beforeunload` 原生提醒（关标签页、刷新、在地址栏输入）。
  2. `document` 捕获阶段监听 `click`：目标是同源、非 `target=_blank`、无修饰键的 `<a href>`，且路径与当前不同时，`preventDefault` + `stopPropagation`，弹确认对话框（`有未保存的修改，确定离开吗？` · `离开` / `继续编辑`）；选「离开」后 `router.push(href)`。覆盖分区导航、导航栏、页脚的所有链接。
  - 浏览器的后退 / 前进不拦（`popstate` 无法取消，硬拦要改写历史栈，得不偿失）。
- **头像卡片**（即时生效，不进表单）：`上传新头像` → 隐藏的 `<input type="file" accept="image/jpeg,image/png,image/webp">` → 本地校验（类型 `image/jpeg|png|webp`，否则「只支持 JPG、PNG 或 WebP 图片」；> 5 MB；用 `createImageBitmap` 解码失败 →「这个文件打不开」；短边 < 128）→ 通过后打开 `AvatarCropDialog`。`移除` 只在有头像时出现，`DELETE /api/me/avatar`，成功 toast `已恢复默认头像`。错误显示在卡片下方。
- **裁剪对话框**：`react-easy-crop`（`aspect=1`、`cropShape="round"` 的遮罩只是显示，导出仍是正方形）+ base-ui `Slider` 控缩放（1× 到「短边 = 128px」的倍数）；键盘方向键 4px / Shift 16px、`+` / `-` 缩放、`Esc` 取消；右侧（移动在下方）96px 与 36px 两个圆形预览。导出：画到 `canvas`，边长 `min(裁剪边长, 1024)`；源是 JPEG 用 `image/jpeg` q 0.92，PNG / WebP（可能有透明）用 `image/png`。上传 `POST /api/me/avatar`（`FormData`，part `file`），成功关对话框、toast `头像已更新`、更新当前用户；失败对话框不关，标题下提示条，文案按 413 / 422 原因码（简报 §4.6、PAP-97 契约评论）。桌面 520px 对话框，移动全屏。
- **链接** `LinkListField`：0–5 行「标题（可选）」+「网址」+ 删除按钮；满 5 条时「添加链接」换成说明；邮箱未验证时卡片内 amber 提示。
- **计数器** `CharCounter`：按字素计，剩 20% 时 `amber-700`，超出 `status-danger`。
- **机构选择器** `OrganizationPicker`（简报 §3.8）：base-ui `Combobox`；值是 `{ kind: "ror", rorId, name, displayName?, cityName?, countryCode? } | { kind: "free", name } | null`。输入归一（NFKC、去首尾空白、折叠空白）后不足 2 个码点不请求（1 个字时下拉提示「再输入一个字」）；防抖 250ms 后请求 `/api/organizations/suggest?q=`，TanStack Query 按归一后的 `q` 缓存；请求中保留上一批结果并在顶部显示细进度条。候选主行 `zhName ?? displayName`（命中部分加粗）、副行英文名 · 城市 · 国家（`country-name.ts` 已有的 `Intl.DisplayNames` 中文国名）、`matchedName` 与主副行都不同时追加 `· 经「…」命中`、右侧类型小标（简报 §3.8 的映射）。列表末尾固定「直接使用「原文」」（> 200 字素时禁用）。选中后变卡片：ROR 的带 `ROR` 胶囊（`title="来自 ROR 机构库"`），手填的副行「手动填写」；`更换`（回输入态并预填名称）、`清除`。
  - 已保存的 ROR 机构回显时只有 `rorId` 和 `name`（`/me/profile` 不给英文名和城市），卡片副行就只写 `ROR 机构库`；本次会话里新选的才有完整副行。
- **关注领域选择器** `MeshInterestPicker`（简报 §3.9）：已选列表 + 计数 `n / 10` + 搜索框（base-ui `Combobox`）。输入含中日韩字符（`\p{Script=Han}`、`\p{Script=Hiragana}`、`\p{Script=Katakana}`、`\p{Script=Hangul}`）不请求，提示只支持英文；不足 2 个字符不请求；防抖 250ms 请求 `/api/mesh/suggest?q=`。候选主行 `name`（命中加粗）、副行 `via <matchedTerm>`、右侧大类小标（第一个树号首字母，简报 §3.9 的映射），已选的禁用并标 `已添加`。回车 / 点击追加到末尾、清空输入、保持焦点；满 10 个时输入框禁用并换占位。排序：桌面用 dnd-kit 拖动；每个胶囊可聚焦，`Alt+↑/↓`（以及 `Alt+←/→`，横排时更直觉）移动；≤ 880px 时每项左侧 `⋮⋮` 按钮打开小菜单（上移 / 下移 / 移除），不能拖。
- **建议接口转发**：`/api/organizations/suggest`、`/api/mesh/suggest` 两个 Route Handler，照 `/api/venues/suggest` 的写法：归一后不足 2 个或超过 100 个码点直接回 `{ items: [] }`、不打网关；`limit` 固定 10；网关失败回 502 + `{ items: [] }`，选择器显示「搜索暂时不可用」那一行。

### 9.4 隐私页（简报 §3.12）

- 页面（RSC）：`requireUser` 后取 `GET /me/privacy`，交给客户端 `PrivacyForm`。
- 两组原生单选（`RADIO_INPUT`）+ 一个开关（`SWITCH_INPUT`），竖排、每项标签 + 说明。
- 每次改动立即调 `savePrivacy({ ... 三项 })`：控件旁 `保存中…` → `已保存`（2 秒后淡出）；失败回滚到改动前的值并显示 `没有保存成功，请重试`。连续快速改动时按顺序串行提交，只保留最后一次的结果状态；回滚回到「最后一次成功保存」的值。
- 邮箱未验证时顶部 amber 说明；主页可见性不是「公开」时开关下多一行 amber 说明；页底 `查看我的主页 ↗`。
- 结果 `pending-deletion` / `signed-out` 同资料页处理。

## 10. 公开主页 `/u/[username]`（简报 §3.13、§4.11）

### 10.1 取数

`fetchPublicProfile(username)`（`cache()` 包装，`generateMetadata` 与 page 共用）：

1. 路径参数先按用户名语法粗筛（`^[A-Za-z0-9_-]{1,64}$`，大小写不敏感）；不合格直接 `not-found`，不打网关。
2. 有会话令牌（Cookie 里有、能放进请求头）就带上——「仅登录用户可见」和本人视角都靠它；`GET /patra-identity/users/{username}`，`redirect: "manual"`，带 `client`。
3. 200 → `{ kind: "found", profile }`；301 / 308 且体里有合法 `username` → `{ kind: "moved", username }`；404 → `not-found`；其他（含 401：公开路径本不该回 401）→ 抛错交给 `error.tsx`。

页面：
- `moved` → `redirect("/u/<新用户名>")`（Next 在 RSC 里是 307，不缓存；release spec 2.1.1 要求临时跳转，因为 90 天内可能改回）。查询串不带。
- `not-found` → `notFound()`；`u/[username]/not-found.tsx` 渲染 `TopNav` + `NotFoundState kind="user"` + `Footer`，并自己导出 `metadata = { title: "没有找到这个用户 · Patra" }`：走 not-found 时 Next 只读 layout 与 `not-found` 文件的元数据，页面的 `generateMetadata` 不参与（`next/dist/lib/metadata/resolve-metadata.js`）；`noindex` 由 Next 在 404 时自动注入。本目录不放 `loading.tsx`，所以 404 是真正的 404 状态码。
- `found` → 判视角：`lookupSession()`（有缓存）得到当前用户，`user.username === profile.username` 即本人；本人视角再取 `GET /me/privacy`（只为专业背景「仅你可见」标注和提示条的原因）。网关请求数：没有会话 Cookie 的匿名访客一次；已登录的别人两次（`/users/{username}` + `/auth/me`）；本人三次。

### 10.2 渲染

- `generateMetadata`：found → 标题 `昵称 (@用户名) · Patra`，`indexable === false` 时 `robots: { index: false }`（输出 `<meta name="robots" content="noindex">`）；not-found / moved 不在这里处理（见上）。
- `ProfileHeader`：头像（桌面 128 / 移动 88，`Avatar` 组件）、昵称（衬线 `display-3`，两行截断）、`@用户名`、简介（保留换行）、链接（横排、`·` 分隔、`Link2` 图标 `aria-hidden`、标题为空显示域名、`rel="nofollow noopener noreferrer ugc"`、新标签页）、`加入于 YYYY 年 M 月`（由 `joinedAt` 的 `YYYY-MM` 直接拆，不经时区换算）。本人视角右上 `编辑资料`。
- `ProfileVisibilityNotice`（只在本人视角、`audience !== PUBLIC` 时）：原因判定——`!user.emailVerified` → 未验证文案 + `ResendVerificationButton`；否则按 `audience`：`SELF` → 仅自己、`SIGNED_IN` → 仅登录用户，都带 `更改可见性`。本人视角且邮箱未验证时，链接位置显示 `验证邮箱后展示链接`。
- `ProfileAbout`：两栏（≤ 880px 单列，专业背景在上）。专业背景逐行 `身份 · 职称`、机构、科室，只显示有的；`professional === null` 不渲染；本人视角且隐私里专业背景是 `SELF` 时标题旁 `仅你可见`。关注领域用 `Pill` 链接到 `/papers?q=<英文名>`。两块都空：别人视角不渲染正文区；本人视角 `你的主页还很空。` + `完善资料`。
- 身份大类的中文名在 `messages.ts` 里集中定义（注册页之外资料页、主页共用）。
- 简报的三处留位（计数行、标签栏、操作区）本版不渲染、不占空白，组件里不写占位元素。

### 10.3 本人视角的时效

`/u/[username]` 是动态渲染（读 Cookie、`no-store` 取数），资料或隐私保存后再打开就是最新的；设置页的 `查看我的主页 ↗` 是普通链接，不需要额外失效。

## 11. 头像转发

- `GET /api/users/[username]/avatar?v=&size=`：校验用户名形状、`v` 为 16 位十六进制、`size` ∈ {128, 512}，不合格直接 404；`gatewayStream` 带 `If-None-Match` 与 `client`；200 / 304 透传，响应头白名单 `Content-Type`、`Content-Length`、`ETag`、`Cache-Control`；上游 404 → 404，网关出错 → 502。抽出 `fulltext-files.ts` 现有的头白名单与透传逻辑成通用的 `proxyGatewayFile(path, request, headers)`，全文文件和头像共用。
- `POST /api/me/avatar`：没有会话 Cookie → 401；`Content-Length` 超过 6 MB 直接 413（不读体）；`request.formData()` 取 `file`，不是 `File` → 422；按原样转发 `FormData`（part 名 `file`，文件名与类型保留）；网关的结果收敛成下文的响应形状，裁剪对话框按 `too-large` / `rejected` 的原因码 / 其他选文案，`signed-out` 跳登录、`pending-deletion` 跳取消注销页。
- `DELETE /api/me/avatar`：同样的会话检查，204 透传。
- 两个写接口都 `Cache-Control: private, no-store`。
- **响应形状**：两个写接口不把网关的 ProblemDetail 透传给浏览器，而是在服务端收敛成门户自己的结果（与 Server Action 一致，客户端只认 `reason` + 原因码）：`{ ok: true, avatarUrl }`（上传）/ `{ ok: true }`（删除）/ `{ ok: false, reason: "too-large" | "rejected" | "pending-deletion" | "signed-out" | "unavailable", code? }`，`rejected` 带 422 的 `errors[0].code`。HTTP 状态码照实（200 / 204 / 4xx / 5xx），客户端只读 JSON。会话失效时顺手清会话 Cookie。
- **跨站请求伪造**：Server Action 自带 Origin 与 Host 的比对，Route Handler 没有。`POST` 收 multipart，普通 `<form>` 就能跨站发出；mini 上各服务同一个 tailnet IP、只是端口不同，浏览器把它们算同一站点，`SameSite=Lax` 挡不住。所以两个写接口都先校验：`Origin` 必须存在且其主机（含端口）等于请求的 `Host`（与 Server Action 的规则一致），否则 403、不打网关。`DELETE` 跨域本就要预检，一并校验。

## 12. 错误处理

### 12.1 ProblemDetail 解析

`src/lib/identity/problem.ts`（server-only）：`readProblem(res)` → `{ status, code, traits: Set<string>, errors: FieldError[], retryAfterSeconds }`，任何字段缺失都有安全默认值；`hasTrait(problem, "PENDING_DELETION")`；`isSessionRejected(problem)` = `status === 401 && !traits.has("PASSWORD_MISMATCH")`。现有 `gateway-response.ts` 的 `readJson` / 日志函数保留并复用。

### 12.2 结果类型不带文案

沿用 v0.8：Server Action 只返回 `reason` 和原因码，文案在客户端按 `messages.ts` 选。

### 12.3 会话失效

写操作遇到会话失效（`isSessionRejected`）→ Action 清会话 Cookie、返回 `signed-out`，客户端 `setQueryData(currentUser, null)` 后跳 `/login?next=<当前页>`。

### 12.4 `PENDING_DELETION`

本 Issue 自己的写操作（资料、头像、隐私、重发）遇到 409 `PENDING_DELETION` → 跳 `/account/deletion-pending?next=<当前页>`；登录 / 注册响应 `status = PENDING_DELETION` 同样跳。取消注销页与「冷静期打开任何页面都跳」由 PAP-102 做。

## 13. 安全

- 入站 `X-Forwarded-For` 在 Next 之前删除（§6.1）；`clientOriginFromHeaders` 只放行单个、合法的 IP 字面量。
- 只向网关发 `X-Forwarded-For` 与 `User-Agent` 两个转发相关头。
- 设备标识 Cookie `HttpOnly`，格式由门户先校验，坏值换新的。
- 主页链接 `rel="nofollow noopener noreferrer ugc"`；`href` 只渲染 `http:` / `https:` 开头的值（后端已校验，前端再挡一次，防止以后改规则时漏出 `javascript:`）。
- 头像转发只接受固定形状的参数，不把任意查询串转给网关。
- 主页 404 对所有原因同一个画面、同一句话、同一个状态码。

## 14. 测试策略（Vitest + Testing Library，TDD 逐条红绿）

| 层 | 内容 |
|---|---|
| 纯函数 | `clientOriginFromHeaders`（单值、多值、`::ffff:`、zone、非 IP、缺失、UA 非法字符）、`username-rules`（判定顺序与每条原因码）、`avatarInitial`（标点开头、emoji、拉丁、数字、全标点退回用户名）、`avatarSrc`（合法 / 不合法形状、两档尺寸）、调色板静态表 12 项、`grapheme` 计数、`readProblem` / `isSessionRejected`、资料表单值 ↔ 请求体转换、`fetchPublicProfile` 的 200 / 301 / 404 / 其他 |
| 预加载模块 | 起一个真的 `http.Server`：装了之后带 `X-Forwarded-For` 的请求在 listener 里看不到这个头，其他头不变。`tsconfig` 是 `allowJs: false`、`@/` 只指 `src/`：给 `server/strip-forwarded-for.mjs` 配一份 `strip-forwarded-for.d.mts`，并在 `tsconfig` 与 `vitest.config` 加别名 `@server/` → `server/` |
| 网关客户端 | 新方法、`FormData` 不设 `Content-Type`、`client` 写两个头且只写这两个、`redirect` 默认 `manual` |
| Server Action | 注册（201、422 三字段、409 单 / 双、deviceId 生成与复用与坏值替换、带 client）、登录（新响应、`PENDING_DELETION`）、`saveProfile`（各结果、请求体形状）、`savePrivacy`、`resendVerificationEmail`（各结果） |
| Route Handler | 头像 GET（参数校验、200 / 304 头白名单、404、502）、头像 POST / DELETE（无 Cookie、超长、透传）、两个建议接口（短 q、长 q、失败） |
| 组件 | 注册表单（用户名转小写、规则提示、各原因码文案、409 双字段焦点）、`UserMenu` / `MobileAccount`（头像、昵称、链接）、`Avatar`（图片 / 默认 / 加载失败回落）、横幅与重发按钮各状态（简报 §4.3）、`SettingsNav` 当前项、`ProfileForm`（脏态保存条、放弃、保存成功 / 字段错误聚焦 / 503 / 409 / 跳转）、`LeaveGuard`（拦链接、放行修饰键与外链、确认后跳转）、`LinkListField`、`CharCounter`、两个选择器（短输入、中文拦截、候选渲染、直接使用、满额、键盘排序、移动菜单）、`AvatarCard`（本地校验四种错误）、裁剪对话框（上传成功 / 失败文案，裁剪库本身用 mock）、`PrivacyForm`（即时保存、失败回滚、未验证提示）、主页各状态（简报 §4.11 每个状态 ID 一条） |
| 守护 | 现有 `gateway-boundary`、`route-structure`、`design-system/*` 保持全绿；`route-structure` 加一条：`u/[username]` 下没有 `loading.tsx` |
| 现有 e2e | `tests/e2e/auth.spec.ts` 的注册用例对着真网关跑，注册表单加了用户名后会挂：本 Issue 顺手补上用户名填写（生成不冲突的用户名），并按新的菜单内容（头像、昵称）改断言。新增的 Playwright smoke 归 PAP-102 |

每个简报状态 ID 至少一条测试（简报 §8 handoff 要求）。拖动排序与裁剪手势在 jsdom 里不测交互本身，只测它们的键盘替代与回调。

## 15. 实施时要实测的点

1. 预加载模块在 standalone 镜像里生效：本地 `pnpm build` 后 `node --import ./server/strip-forwarded-for.mjs .next/standalone/server.js`，带伪造 `X-Forwarded-For` 请求一个会打印 `headers()` 的临时路由（实测后删掉），确认读到的是 socket 地址。
2. 本机 dev 连 mini 网关：注册新账号（用户名规则、409 两字段）、登录、导航栏头像与菜单、横幅重发（Mailpit 收信）、资料页全部字段（ROR 命中与手填各一、关注领域增删排序）、头像裁剪上传与移除、隐私三项、`/u/<用户名>` 在匿名 / 登录别人 / 本人下的结果、改不了名所以旧名跳转只能在 PAP-102 做完后实测。
3. 桌面 1440×900 与移动 375×812 逐页对照画板截图。
4. 头像图片走 `/api/users/.../avatar` 第二次请求是 304 或浏览器缓存命中。

## 16. 要同步改的文档和 Issue

- `patra-portal/README.md`：转发链路与预加载模块、设备标识 Cookie、新增 Route Handler。
- `docs/patra/design/snapshots/README.md`：补 `2026-10-22-user-center-hifi.zip` 一行（PAP-92 交接时漏了）。
- Linear PAP-101：完成后推 Review，评论附 AC 证据；To-Do 勾选。
- 本 spec 第 4.2 节「catalog 读不转发 IP / UA」写进 PAP-101 评论，说明与 PAP-99 契约评论的差别。

## 17. 设计中由我定的点与待拍板的点

由我定（看到不对就推翻）：

1. 入站 XFF 用 `--import` 预加载模块删除，开发模式不装（§4.1）。
2. `gatewayFetch` 显式 `client` 参数；catalog 公开读不转发 IP / UA（§4.2）。
3. 头像上传走 Route Handler，其余写操作用 Server Action（§4.3）。
4. `gatewayFetch` 的 `redirect` 默认改为 `manual`（§6.3）。
5. 设置页守卫放在页面里（`requireUser(path)`），layout 只取用户名（§9.1）。
6. 账号、安全两项分区导航现在就渲染，PAP-102 之前点进去是 404（§9.2）。
7. 离开提醒拦链接点击 + `beforeunload`，不拦浏览器后退（§9.3）。
8. 新依赖 `react-easy-crop`、`@dnd-kit/core`、`@dnd-kit/sortable`（§5）。
9. 单选、开关、身份下拉用原生控件 + 现有样式（§5）。
10. 已保存的 ROR 机构回显时副行只写「ROR 机构库」（§9.3）。
11. 导出裁剪图：JPEG 源出 JPEG q 0.92，PNG / WebP 源出 PNG（§9.3）。
12. 关注领域键盘排序额外支持 `Alt+←/→`（§9.3）。
13. 资料保存的 409 自动重试一次、保留输入，不用简报的「刷新」（§9.3）。
14. 头像两个写接口校验 `Origin`，并把网关错误收敛成门户自己的结果形状（§11）。

用户拍板（2026-10-23）：

1. 同意：生产容器入口改成 `node --import ./strip-forwarded-for.mjs server.js`（§4.1，动 Dockerfile 的 CMD，随 CD 上 mini）。
2. 同意：catalog 的公开读不转发 IP / UA，与 PAP-99 契约评论「每个请求都带」不同（§4.2）。

## 18. 实施结果（2026-10-23）

按计划 `docs/patra/plans/2026-10-23-portal-user-center-profile.md` 的 25 个任务在 `feat/v0.12-user-center-portal` 上内联 TDD 完成，未推送、未开 PR（等 PAP-102 一起）。

### 18.1 门禁

- `pnpm lint`、`pnpm typecheck`、`pnpm test`（Vitest 180 个文件 1700+ 条）全绿；`pnpm build` 通过。
- `tests/e2e/auth.spec.ts` 补了用户名，对 mini 网关 `--workers=1` 6 条全过。

### 18.2 实测

1. **预加载模块**：本地生产构建，临时探针路由读 `headers().get("x-forwarded-for")`，请求带伪造的 `X-Forwarded-For: 203.0.113.9`：带 `--import ./strip-forwarded-for.mjs` 时读到 `127.0.0.1`，不带时读到 `203.0.113.9`。探针已删除。
2. **本机 dev 连 mini 网关走查**：注册（前端规则、转小写）→ 横幅（注册后倒计时）→ 菜单 → 资料全部字段（机构搜「协和」命中 ROR、关注领域搜英文、中文拦截、Alt + 方向键排序）→ 保存 → 头像裁剪上传与转发（`image/webp`、`Cache-Control: public, max-age=31536000, immutable`、`If-None-Match` 回 304、版本号不对 404）→ 隐私三项即时保存 → 公开主页本人 / 匿名两种视角、`noindex` 随设置切换、「仅登录用户」对匿名回真正的 404、路径大写可访问、非法字符 404。邮箱验证用 Mailpit 里的令牌直接调 `POST /auth/email/verify`（落地页归 PAP-102）。
3. **桌面 1440×900 与移动 375×812**：Playwright 无头浏览器截全套，与画板逐屏对照，差异已修（见 18.3）。走查时浏览器控制台除故意访问的不存在用户外没有报错。

### 18.3 实施中的偏离与修正

1. `actions.ts` 改读嵌套 `{ sessionToken, user }` 提前到计划 Task 4（类型变宽后旧解析编译不过）。
2. 生产构建发现 `lookupSession` / `callIdentity` 的 try/catch 吞掉了 Next 的动态渲染信号，设置页预渲染时被当成网关不可用：catch 里先 `unstable_rethrow`。
3. base-ui 组合框选中候选后用 `reason: "item-press"` 回填输入框：两个选择器都忽略这类回填（真实浏览器复现，jsdom 复现不了）。
4. 头像导出原用脱离文档的 `Image.decode()`，在不渲染的页面里一直不返回：改为 `createImageBitmap(file)`。
5. dnd-kit 默认全局计数器 id 造成水合不一致、窄屏切换时传感器数组长短变化触发 React 警告：`DndContext` 传 `useId()`、读屏拖动说明改中文、传感器始终不变。
6. 裁剪对话框按画板改为 620px 宽（简报文字 520px，已在简报 §10 记一笔）；手机头像卡片头像与按钮同一行。
7. 设计系统守护测试拦下的写法（自定义阴影、`fg-4` 正文、组件自带焦点环、非 `motion-safe` 的位移过渡与 `animate-*`）全部改成允许的写法。
8. 隐私页单选、开关的说明文字用块级元素，读屏名称里标签与说明之间有停顿。

### 18.4 留给 PAP-102 / 之后

- 设置页「账号」「安全」两项指向的页面、`/account/deletion-pending`、`/verify-email` 等落地页由 PAP-102 建；本 Issue 已有的跳转（冷静期 → 取消注销页、横幅「更改邮箱」→ `/settings/account#email`）在那之前是 404。
- 旧用户名 307 跳转要等 PAP-102 的改名功能落地后在 mini 上实测（单测已覆盖）。
