# 门户认证基础设施工程设计（PAP-67）

> **Issue**：[PAP-67](https://linear.app/papertrace/issue/PAP-67)
> **版本**：[v0.8 Accounts](../release-specs/v0.8-accounts.md)（决策 B、E，边界 I，Done 判定 D3、D4、D5、D7）
> **前置设计**：[identity 账号（PAP-63）](2026-10-05-identity-account-design.md) 第 10、16 节、[identity 会话（PAP-64）](2026-10-09-identity-session-design.md) 第 6、10、16 节、[gateway 鉴权（PAP-65）](2026-10-09-gateway-auth-design.md) 第 6.3、11、15 节；设计简报 [accounts.md](../../../patra-portal/docs/design-briefs/v0.8/accounts.md) §4 与附录 A
> **后续 Issue**：[PAP-68](https://linear.app/papertrace/issue/PAP-68) 登录页、注册页、导航栏的登录入口与用户菜单
> **日期**：2026-10-10
> **状态**：设计中，待评审

## 1. 要解决的问题

门户现在完全是匿名的：服务端向网关取正文数据，浏览器不持有任何身份。v0.8 的后端已经上线了注册、登录、会话和网关鉴权（PAP-62 ~ PAP-66、PAP-69、PAP-70），门户这边还缺一层底座：把会话令牌安全地放进浏览器，服务端调网关时换成 `Authorization` 头，提供注册、登录、登出和「当前用户」的入口，并处理会话失效。完成后，PAP-68 的登录页、注册页和用户菜单只调用这里提供的入口。

同时要收拢网关调用。现在 4 个文件里有 10 处直接向网关发 `fetch`，网关地址也在 9 处各自读取。加上令牌以后，调用点这样分散，很难保证匿名请求不带令牌。

**完成标准**：PAP-67 的 Acceptance Criteria。

## 2. 范围

做：

- 统一网关客户端 `gatewayFetch`，10 处调用点（含 `proxy.ts`）改走它。
- 会话 Cookie 的读写封装和 `Secure` 开关。
- 服务端读当前用户、`GET /api/auth/me`、导航栏用的 `useCurrentUser()`。
- 注册、登录、登出三个 Server Action。
- 401、503 的处理。
- 守护测试、单元测试、本地实测、环境配置和文档。

不做：

- 界面：导航栏账号区、用户菜单、登录页、注册页、表单组件和中文文案（PAP-68）。
- `next` 参数的校验和成功后的跳转（PAP-68，简报 §2 的跳转规则）。
- 前端表单校验规则（PAP-68，口径见 identity 账号设计第 16 节）。
- 重新登录时作废旧会话。旧会话 30 天不活跃后过期，或者在同一用户超过 10 个会话时被挤掉。
- 门户侧校验令牌格式，格式只由网关判断。
- `serverActions.allowedOrigins`。门户现在是直连访问，`Origin` 和 `Host` 天然一致；加反向代理时再配（第 15 节）。
- HTTPS 接入（release spec 边界 I）。

## 3. 已定的前提

| 前提 | 来源 |
|---|---|
| 浏览器只持有一个 HttpOnly Cookie，门户服务端调网关时换成 `Authorization` 头；`HttpOnly`、`SameSite` 始终开启，`Secure` 默认开启，只在显式配置的非 HTTPS 环境关闭 | 决策 E |
| 门户不做会话续期，只刷新 Cookie 自身的过期时间；注册、登录、登出走 Server Action；匿名内容的请求不带令牌，用户态数据单独请求、不缓存；不引入 Auth.js | 决策 E |
| Cookie 放 `sessionToken` 原文，`Max-Age` 30 天，用户活动时刷新；收到 401 清 Cookie；注册、登录请求不传 `clientType` 和 `deviceId` | PAP-64 设计第 16 节 |
| 令牌是 `patra_user_` 加 43 个 base64url 字符；会话 30 天不活跃过期、180 天绝对过期；网关对每个带令牌的请求续期，60 秒内只读不写 | PAP-64 设计第 6 节，PAP-65 设计第 3 节，[会话模块 README](../../../patra-api/patra-identity/patra-identity-session/README.md) |
| 接口：`POST /auth/register` 回 201、`POST /auth/login` 回 200，响应 `{ sessionToken, userId, email }`；`POST /auth/logout` 回 204，公开且幂等；`GET /auth/me` 回 200 `{ userId, email, accountType }`，没有当前用户时回 401 | PAP-64 设计第 10 节，[identity README](../../../patra-api/patra-identity/README.md) 第 2 节 |
| 同一接口里每种失败的状态码都不同；字段错误在 ProblemDetail 的 `errors[]` 里，每项是 `{ field, code, rejectedValue, message }`，`code` 是原因码；429 带 `retryAfterSeconds` 和 `Retry-After` | 简报附录 A.3、A.4，PAP-63 设计第 10 节，`ErrorKeys`、`ValidationError` |
| 网关的 401 是 `GW-0401`，identity 的是 `IDN-0401`，门户收到任何 401 都清 Cookie；带令牌而 Redis 不可用时，任何路由都回 503 `GW-0503`；无效或过期的令牌访问公开路由，按匿名放行 | PAP-65 设计第 6.3、11、15 节 |
| 前端校验的口径：邮箱去掉首尾空格后用 Zod 默认的 `z.email()` 加 `.max(254)`；密码长度按码点计；按原因码选文案，不认识的原因码显示该字段的通用错误；429 读 `retryAfterSeconds`；`userId` 是字符串 | PAP-63 设计第 16 节 |
| 会话失效（过期、撤销、封禁）没有专门的界面状态，下次打开或刷新页面时静默回到未登录态；登出在用户看来总是成功；读当前用户失败时，本次显示未登录态 | 简报 §4.3、附录 A.3 |

本设计新定的事（2026-10-10 与用户商定）：

| 决定 | 内容 | 否决的选项 |
|---|---|---|
| 导航栏怎么读当前用户 | 浏览器请求门户自己的 `GET /api/auth/me`（第 4 节方案 A） | 服务端渲染时读；在 proxy 里统一处理 |
| 「保持登录」的说法 | D3 和 PAP-67 验收改成「30 天内有过访问就保持登录；从登录算起最长 180 天，到期要重新登录」，简报 §0 同样补上 180 天的上限（第 16 节），行为不变 | 去掉 180 天的绝对过期：它用来防 Cookie 被偷、密码被改之后旧会话一直有效（PAP-64 设计第 6 节）。文档不改：承诺和行为不一致 |
| Cookie 名称 | `Secure` 开时叫 `__Host-patra_session`，关时叫 `patra_session` | 固定一个不带前缀的名字：同一域名下的其他子站点可以种入或覆盖会话 Cookie |
| `SameSite` | `Lax` | `Strict`：比 `Lax` 多拦的只有从别的站点点链接进来的 GET 请求，而门户没有会改数据的 GET |
| 门户是否校验令牌格式 | 不校验。值坏了的话，网关按匿名处理，随后 Cookie 被清掉 | 按后端的格式校验：后端一改格式，门户就会把所有人当成未登录 |
| 重新登录时的旧会话 | 不作废 | 登录成功后拿旧令牌调一次登出：多一次网关调用，换来的只是一条闲置会话提前消失，它不活跃 30 天后本来就会过期 |

版本：Next.js 16.3.8、React 19.3.0、TanStack Query 5.103.2、Zod 4.6.5、Vitest 4.1.11。下面用到的框架行为，都对照 16.3.8 的源码或随包文档核实过：

- 服务端组件渲染时，`cookies()` 只能读；`set` 只能在 Server Function（含 Server Action）和 Route Handler 里调用（`node_modules/next/dist/docs/01-app/03-api-reference/04-functions/cookies.md`）。
- Server Action 写入或删除 Cookie 时，`MutableRequestCookiesAdapter` 会把 `workStore.pathWasRevalidated` 设成 `ActionDidRevalidateStaticAndDynamic`。`action-handler` 因此不再跳过页面渲染，而是在同一次响应里把当前页重新渲染一遍，浏览器再按 `RefreshAll` 应用（`next/dist/server/web/spec-extension/adapters/request-cookies.js`、`next/dist/server/app-render/action-handler.js`、`next/dist/client/components/router-reducer/reducers/server-action-reducer.js`）。
- `cookies().delete(name)` 等价于 `set({ name, value: "", expires: new Date(0) })`；序列化时只有显式传了 `secure` 才会写出 `Secure`（`next/dist/compiled/@edge-runtime/cookies`）。带 `__Host-` 前缀的 Cookie，浏览器只接受带 `Secure` 的 `Set-Cookie`，删除也不例外。
- proxy 所在的 `middleware` 层属于 `WEBPACK_LAYERS.GROUP.serverOnly`，`server-only` 在这一层被别名成空模块，所以 `proxy.ts` 可以引入带 `import "server-only"` 的模块（`next/dist/build/create-compiler-aliases.js`、`next/dist/lib/constants.js`）。
- Server Action 只接受 POST，并比对 `Origin` 和 `Host`（或 `X-Forwarded-Host`），不一致就拒绝（`node_modules/next/dist/docs/01-app/02-guides/data-security.md`）。
- proxy 默认运行在 Node.js 运行时。Server Action 是发到所在页面路由的 POST，会经过匹配这个路由的 proxy（`node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/proxy.md`）。

## 4. 方案比较

| | 做法 | 取舍 |
|---|---|---|
| A（采用） | 浏览器请求门户的 `GET /api/auth/me`；它带令牌请求网关，200 时刷新 Cookie，401 时清除；导航栏用 TanStack Query 读它 | Route Handler 能写 Cookie，读用户、刷新、清除都在一处；刷新 Cookie 和网关续期是同一个请求；`error.tsx`、`loading` 里的导航栏同样能用；正文渲染不读 Cookie。代价：每次整页打开多一个同源请求，导航栏先显示占位 |
| B | 服务端渲染时读用户：每个页面把异步的账号区作为插槽传给 TopNav，流式输出 | 首屏 HTML 就带着登录态。但渲染时不能写 Cookie，清除和刷新要另找地方做；`error.tsx` 是客户端组件，拿不到用户；要改 13 处渲染 TopNav 的文件；正文页面要读 Cookie，和登录态绑在一起 |
| C | proxy 扩到所有页面：每个页面请求先调 `/auth/me`，刷新或清除 Cookie，再用请求头把用户传给页面 | 写 Cookie 集中在 proxy。但每个页面请求都要先等一次网关；Next 文档说明 proxy 不适合做慢的数据请求和完整的会话管理；用请求头传用户要防伪造；proxy 现在只负责两类详情页的 404，职责会混在一起 |

## 5. 模块与组件

| 文件 | 职责 |
|---|---|
| `src/lib/gateway.ts` | 统一网关客户端 `gatewayFetch`。全仓库只有这里读 `PATRA_GATEWAY_BASE_URL`、向网关发 `fetch`（`server-only`） |
| `src/lib/auth/session-cookie.ts` | 会话 Cookie 的名称、属性、`Secure` 开关和读写。全仓库只有这里调用 `cookies()`（`server-only`） |
| `src/lib/auth/current-user.ts` | `lookupSession()`、`getCurrentUser()`（`server-only`） |
| `src/lib/auth/actions.ts` | `register`、`login`、`logout`（`"use server"`） |
| `src/lib/auth/types.ts` | 浏览器和服务端共用的类型：`CurrentUser`、`FieldError`、`RegisterResult`、`LoginResult` |
| `src/app/api/auth/me/route.ts` | `GET /api/auth/me` |
| `src/lib/auth/current-user-query.ts` | `useCurrentUser()` |
| `src/lib/query-keys.ts`（改） | 加 `currentUser` |
| `src/proxy.ts`、`src/lib/portal-api/publications.ts`、`publication-search.ts`、`venues.ts`（改） | 改走 `gatewayFetch` |

依赖方向：actions、current-user 依赖 session-cookie 和 gateway；route 依赖 current-user 和 session-cookie；current-user-query 只依赖 types 和 query-keys；正文 fetcher 和 proxy 只依赖 gateway。正文 fetcher 不引用 session-cookie，手里没有令牌，调用 `gatewayFetch` 时也就不会带上令牌。

## 6. 统一网关客户端

```ts
export interface GatewayRequestInit {
  /// 默认 GET
  method?: "GET" | "HEAD" | "POST";
  /// 有值时序列化成 JSON 请求体，并带 `Content-Type: application/json`
  json?: unknown;
  /// 会话令牌，有值时写成 `Authorization: Bearer <token>`
  token?: string;
  /// 超时毫秒数，默认 10_000
  timeoutMs?: number;
}

export async function gatewayFetch(path: string, init?: GatewayRequestInit): Promise<Response>;
```

- `path` 是网关上的完整路径，比如 `/patra-catalog/portal/publications/42`。
- 每次调用都读一次 `PATRA_GATEWAY_BASE_URL`，缺失时抛 `Error("PATRA_GATEWAY_BASE_URL 未配置")`。文字和现在相同，现有测试按 `/PATRA_GATEWAY_BASE_URL/` 断言。
- 固定使用 `cache: "no-store"` 和 `signal: AbortSignal.timeout(timeoutMs)`。
- 只负责把请求发出去：返回原始的 `Response`，不解读状态码；网络错误和超时照常抛出。
- 没有 `token` 就不写 `Authorization`；没有 `json` 就不写请求体和 `Content-Type`。

**调用点迁移**：10 处直接 `fetch` 换成 `gatewayFetch(path)`，各处读地址、判空的代码一起删掉，其余逻辑原样保留，包括各 fetcher 的状态码处理（详情遇到 404 / 400 / 422 返回 null、非 2xx 抛错、格式不对时降级）、报错文字和 React `cache()` 包装。三个 fetcher 文件里的 `FETCH_TIMEOUT_MS` 和 `publication-search.ts` 里的 `gatewayBaseUrl()` 删掉。现有 fetcher 测试断言的是完整 URL 和 `objectContaining({ cache: "no-store", signal })`，不用改。

**`proxy.ts`**：HEAD 检查改成 `gatewayFetch(path, { method: "HEAD", timeoutMs: CHECK_TIMEOUT_MS })`，3 秒的常量保留。失败时（包括缺地址时抛出的错误）照旧得到 `null`，直接放行。职责和 `matcher` 都不变，不加任何登录逻辑。在详情页点登出时，Server Action 的 POST 会经过 proxy，它带着 `Sec-Fetch-Dest: empty`，proxy 照旧直接放行。

## 7. 会话 Cookie

| 项 | 取值 | 理由 |
|---|---|---|
| 名称 | `Secure` 开时叫 `__Host-patra_session`，关时叫 `patra_session` | 带 `__Host-` 前缀的 Cookie，浏览器只接受本站通过 HTTPS 写入、`Path=/`、不带 `Domain` 的那一份，同一域名下的其他子站点没法种入或覆盖它。这个前缀要求必须带 `Secure`，所以 HTTP 环境只能用不带前缀的名字 |
| 值 | 后端返回的 `sessionToken` 原文 | 门户不校验格式（第 3 节） |
| `HttpOnly` | 开 | 页面脚本读不到 |
| `SameSite` | `Lax` | 第 3 节 |
| `Path` | `/` | |
| `Domain` | 不设 | 只发给门户这一个主机 |
| `Max-Age` | 2592000（30 天） | 和会话的不活跃过期时间一致 |
| `Secure` | 默认开 | 见下文 |

**`Secure` 开关**：环境变量 `PATRA_SESSION_COOKIE_SECURE`。

- 不设或设成 `true` 时开，设成 `false` 时关，其他值一律抛 `Error("PATRA_SESSION_COOKIE_SECURE 只能是 true 或 false")`。每次读写 Cookie 时读取。
- 设成 `false` 的地方：mini 的 `patra-infra/docker/.env.portal`；`playwright.config.ts` 的 `webServer.env`；本机的 `.claude/launch.json` 里的 `portal-dev`（这个文件被 git 忽略，只改本机）。`patra-portal/.env.example` 写明用法；本机直接跑 `pnpm dev` 时，写进自己的 `.env.local`。
- 以后上 HTTPS 时，删掉 `.env.portal` 里的这一行。

**读写函数**：

| 函数 | 行为 | 调用方 |
|---|---|---|
| `readSessionToken(): Promise<string \| null>` | 按当前名称读 Cookie，没有或为空时返回 null | current-user、`logout` |
| `writeSessionCookie(token: string): Promise<void>` | 按上表的属性写入 | `register`、`login` 成功时 |
| `refreshSessionCookie(): Promise<void>` | 用原值按相同属性重写一次，`Max-Age` 回到 30 天；没有 Cookie 时什么也不做 | `/api/auth/me` 读到用户时 |
| `clearSessionCookie(): Promise<void>` | 写入空值，属性和写入时相同，`Max-Age=0` | `/api/auth/me` 收到 401 时、`logout` |

清除时不用 `cookies().delete(name)`：它只发 `Expires`、不带 `Secure`，浏览器会拒绝用它删除 `__Host-` 开头的 Cookie（第 3 节）。

**什么时候写入、刷新、清除**：

- 写入：注册、登录成功时。
- 刷新：只在 `/api/auth/me` 读到用户时。网关也在这个请求里给会话续期，所以 Cookie 和会话的 30 天会一起往后顺延。
- 清除：`/api/auth/me` 收到 401 时，以及登出时。
- 会话到了 180 天的绝对上限，下一次 `/api/auth/me` 收到 401，按上一条清除。

## 8. 读当前用户

### 8.1 服务端

```ts
export type SessionLookup =
  | { status: "authenticated"; user: CurrentUser }
  | { status: "anonymous" }
  | { status: "rejected" }
  | { status: "unavailable" };

export async function lookupSession(): Promise<SessionLookup>;
export async function getCurrentUser(): Promise<CurrentUser | null>;
```

`lookupSession()` 读 Cookie，再用 `gatewayFetch("/patra-identity/auth/me", { token })` 请求网关：

| 情况 | 结果 |
|---|---|
| 没有 Cookie | `anonymous`，不请求网关 |
| 200，且 Zod 校验通过：`userId` 和 `email` 都是非空字符串（`accountType` 不读） | `authenticated` |
| 401（`GW-0401` 或 `IDN-0401`） | `rejected` |
| 其他状态码；请求抛出的错误（超时、网络错误、缺网关地址）；200 但响应体格式不对；读 Cookie 时抛错（比如开关的值写错） | `unavailable`，记一条错误日志（第 12 节） |

- `lookupSession()` 不抛异常。
- `getCurrentUser()` 在 `authenticated` 时返回用户，其余情况返回 null，不碰 Cookie。它给服务端渲染的页面用，比如 PAP-68 的登录页、注册页判断「已登录就跳走」。渲染时不能写 Cookie，所以遇到 401 只返回 null，过期的 Cookie 留到下一次 `/api/auth/me` 时清，或者登录时被新值覆盖。

### 8.2 `GET /api/auth/me`

| `lookupSession()` 的结果 | 响应 | Cookie |
|---|---|---|
| `authenticated` | 200 `{ user: { userId, email } }` | `refreshSessionCookie()` |
| `anonymous` | 200 `{ user: null }` | 不动 |
| `rejected` | 200 `{ user: null }` | `clearSessionCookie()` |
| `unavailable` | 503，响应体为空 | 不动 |

- 所有响应都带 `Cache-Control: private, no-store`。
- 和现有的 `/api/venues/suggest` 一样声明 `export const dynamic = "force-dynamic"`。

### 8.3 `useCurrentUser()`

```ts
export type CurrentUserState =
  | { status: "pending" }
  | { status: "anonymous" }
  | { status: "authenticated"; user: CurrentUser };

export function useCurrentUser(): CurrentUserState;
```

- 查询键是 `queryKeys.currentUser`，值为 `["current-user"]`。`queryFn` 请求同源的 `/api/auth/me`：2xx 时取 `user`，非 2xx 时抛错。
- 状态映射：还没有数据（包括重试期间）时是 `pending`；有数据时按 `user` 是否为 null 分成 `anonymous` 和 `authenticated`；最终出错时是 `anonymous`，也就是简报说的「本次显示未登录态」。
- 沿用全局默认值：`staleTime` 60 秒，失败重试 1 次，窗口重新获得焦点时不重新请求。
- 「用户态数据不缓存」在这里指：HTTP 层和 Next 都不缓存，不同用户之间也不共享。同一个标签页里，60 秒内来回切换页面不会重复请求。在别处被撤销的会话，刷新页面后，或者过了 60 秒再切换页面时，会显示为未登录。
- Redis 出故障时，带令牌的请求回 503，导航栏会暂时显示「登录」，但 Cookie 还在；Redis 恢复后刷新页面，就回到登录态。

## 9. Server Action

### 9.1 入参和转发

- `register` 和 `login` 收 `{ email: string; password: string }`，用 Zod 的 `z.object({ email: z.string(), password: z.string() })` 只校验形状。形状不对时返回 `unavailable`，不请求网关。长度、格式在这里不校验：后端会再校验一遍，失败时回 422 并带上原因码。
- 发给网关的请求体只有 `email` 和 `password`（Zod 默认剥掉多余的字段），不带 `clientType` 和 `deviceId`；注册、登录请求不带 `Authorization`。

### 9.2 结果类型

```ts
export interface CurrentUser {
  userId: string;
  email: string;
}

export interface FieldError {
  field: string;
  /// 原因码：REQUIRED、TOO_LONG、INVALID_FORMAT、TOO_SHORT、TOO_COMMON、INVALID_CHARACTER；后端没给时为 null
  code: string | null;
}

export type RegisterResult =
  | { ok: true; user: CurrentUser }
  | { ok: false; reason: "invalid-fields"; fieldErrors: FieldError[] }
  | { ok: false; reason: "email-taken" }
  | { ok: false; reason: "unavailable" };

export type LoginResult =
  | { ok: true; user: CurrentUser }
  | { ok: false; reason: "invalid-fields"; fieldErrors: FieldError[] }
  | { ok: false; reason: "bad-credentials" }
  | { ok: false; reason: "banned" }
  | { ok: false; reason: "throttled"; retryAfterSeconds: number | null }
  | { ok: false; reason: "unavailable" };
```

结果里不带中文文案，由 PAP-68 按 `reason` 和 `code` 选文案。

### 9.3 状态码映射

| 网关响应 | `register` | `login` |
|---|---|---|
| 成功（注册 201、登录 200） | Zod 校验 `sessionToken`、`userId`、`email` 都是非空字符串，然后 `writeSessionCookie`，返回 `{ ok: true, user }`；校验失败时返回 `unavailable`，不写 Cookie | 同左 |
| 422 | `invalid-fields`。`fieldErrors` 取 `errors[]` 里每个 `field` 是字符串的项：`field` 照抄，`code` 是字符串就照抄，否则为 null；一项都取不到（`errors[]` 缺失、为空或格式不对）时返回 `unavailable` | 同左 |
| 409 | `email-taken` | `unavailable` |
| 401 | `unavailable` | `bad-credentials` |
| 403 | `unavailable` | `banned` |
| 429 | `unavailable` | `throttled`。`retryAfterSeconds` 先取响应体里的非负整数，没有就取 `Retry-After` 头的非负整数秒，都没有就是 null |
| 其他状态码、超时、网络错误 | `unavailable` | `unavailable` |

除了成功，其他情况都不写 Cookie。

### 9.4 登出

```ts
export async function logout(): Promise<void>;
```

- 读出令牌。有令牌时，带着它请求 `gatewayFetch("/patra-identity/auth/logout", { method: "POST", token, timeoutMs: 5_000 })`；没有就不请求网关。读令牌和请求网关都放在 `try` 里：非 204 的响应和抛出的错误只记日志，不往外抛。
- 不论结果是 204、503、超时还是网络错误，最后都在 `finally` 里调用 `clearSessionCookie()`，所以登出在用户看来总是成功的。
- 超时设 5 秒，比默认的 10 秒短：删后端会话只是尽力而为，没必要让用户多等。

### 9.5 写 Cookie 之后，Next 会重新渲染当前页

`register`、`login`、`logout` 都会写 Cookie，所以 Next 会在同一次响应里把当前页在服务端重新渲染一遍（第 3 节）。

- 在正文页登出时，这一页的正文会再请求一次。正文不依赖 Cookie，所以渲染结果不变。万一这时 catalog 正好挂了，这次重新渲染会像平时切换页面一样报错，但 Cookie 已经清掉了。这个结果可以接受：catalog 挂了时，切换到任何页面都一样会报错。
- 在登录页、注册页提交成功后，这一页也会带着新 Cookie 重新渲染一遍。PAP-68 如果在渲染时就把已登录的人跳走，这次重新渲染本身就会触发跳转（第 15 节）。
- Server Action 只返回结果，自己不跳转；跳转、`next` 参数的校验和成功提示都归 PAP-68。

### 9.6 已登录时再次登录

直接用新令牌覆盖 Cookie，不作废旧会话（第 3 节）。

## 10. 401、503 和并发

- 除了登出，只有 401 会清 Cookie（`/api/auth/me` 的 `rejected`），不区分 `GW-0401` 和 `IDN-0401`。503、超时、网络错误一律不清。
- 用户态请求失败时不进错误页：`/api/auth/me` 由浏览器查询，失败时按未登录显示；`lookupSession()` 和 `getCurrentUser()` 不抛异常；Server Action 把失败都归成 `unavailable`。
- 令牌在整个会话期间不变，没有轮换，所以同一个令牌并发多少请求，都只会刷新 Cookie。PAP-67 的验收「同时发起多个带令牌的请求，不会把用户登出」靠这个结构满足。
- 有两种窗口极短的竞争，本设计接受：
  1. 在 A 标签页点登出的同时，B 标签页有一个更早发出的 `/api/auth/me` 晚一步回来，结果是 200，于是把已经作废的令牌重新写回 Cookie。影响只是 B 标签页的导航栏晚一点回到未登录：下一次查询会收到 401，并把这个 Cookie 清掉。
  2. 浏览器里留着一个没人检查过的过期 Cookie。用户在 A 标签页登录的那一刻，B 标签页正好打开页面，B 收到的 401 会把刚写入的新 Cookie 删掉，用户只能再登录一次。浏览器执行删除时不看当前的值，服务端没法避免。

## 11. CSRF 防护

不另加 CSRF 令牌，靠三层：

1. Server Action 只接受 POST，Next 会比对请求的 `Origin` 和 `Host`，不一致就拒绝。这一层也挡住了登录 CSRF，即把受害者登录到攻击者的账号上。
2. Cookie 设了 `SameSite=Lax`，跨站 POST 根本不会带上它。
3. `GET /api/auth/me` 只会刷新或清除请求者自己的 Cookie；它的响应没有 CORS 头，别的站点读不到内容。

门户前面加反向代理或上 HTTPS 时，要配 `serverActions.allowedOrigins`（第 15 节）。

## 12. 日志

- 令牌和密码不进日志，也不进抛出的错误消息。
- 网关返回意外的状态码、或者请求失败时，`console.error` 记下接口、状态码，以及 ProblemDetail 的 `code`（有的话），不记响应体。
- 测试断言 `console.*` 的参数里不出现令牌和密码。

## 13. 测试策略

按 TDD 一个一个写，测试文件按现有结构放在 `tests/` 下：

| 测试文件 | 验证什么 |
|---|---|
| `tests/lib/gateway-boundary.test.ts` | 守护测试，规则见下表 |
| `tests/lib/gateway.test.ts` | 地址拼接；`cache: "no-store"`；超时默认 10 秒、可以覆盖（spy `AbortSignal.timeout`，看传入的值）；JSON 请求体和 `Content-Type`；有令牌时带 `Authorization: Bearer`，没有令牌时不带；缺地址时报错；网络错误照常抛出 |
| `tests/lib/auth/session-cookie.test.ts` | 两种名字和全部属性；开关的三种取值；`refreshSessionCookie` 用原值重写、没有 Cookie 时什么也不做；`clearSessionCookie` 的属性和写入时相同，并且 `Max-Age=0` |
| `tests/lib/auth/current-user.test.ts` | 第 8.1 节表格逐行覆盖，`GW-0401` 和 `IDN-0401` 都归为 `rejected`；请求带 `Authorization: Bearer`；`getCurrentUser()` 的映射；日志里没有令牌 |
| `tests/app/api/auth-me.test.ts` | 第 8.2 节表格逐行覆盖，包括 `Cache-Control`；同一个令牌并发请求两次，两次都返回 200、都只刷新不清除；先 503 后 200 时不清除 |
| `tests/lib/auth/actions.test.ts` | 第 9.3 节表格逐行覆盖；请求体只有 `email` 和 `password`；注册、登录不带 `Authorization`（即使已有 Cookie）；入参形状不对时返回 `unavailable`、不请求网关；只有成功时写 Cookie；登出在 204、503、超时、网络错误、没有 Cookie 这几种情况下都清 Cookie，没有 Cookie 时不请求网关；日志里没有密码和令牌 |
| `tests/lib/auth/current-user-query.test.tsx` | `pending`、`anonymous`、`authenticated` 三种状态；503 时归为 `anonymous`；请求的是同源的 `/api/auth/me` |

守护测试复用 `tests/design-system/source-scan.ts` 的扫描方式，扫 `src/` 下全部 `.ts` / `.tsx`：

| 规则 | 匹配 | 例外 |
|---|---|---|
| 网关地址 | `PATRA_GATEWAY_BASE_URL` | `src/lib/gateway.ts` |
| 直接 fetch | `\bfetch\(` | `src/lib/gateway.ts`；第一个参数是以 `/` 开头的字符串或模板字面量（浏览器请求门户自己的接口） |
| Cookie | `\bcookies\(\)` | `src/lib/auth/session-cookie.ts` |
| `Authorization` 头 | `` ["'`]Authorization["'`] ``、`` ["'`]Bearer\s `` | `src/lib/gateway.ts` |

- 在 Next 的请求上下文之外调用 `cookies()` 会直接抛错，所以测试里用 `vi.mock("next/headers")` 换成一个假的 Cookie 存储，放在 `tests/stubs/cookie-jar.ts`，session-cookie、route、actions 三个测试共用。它记下每次写入；测试用 `NextResponse` 自带的 `cookies` 把写入序列化，直接断言 `Set-Cookie` 字符串。
- 现有的 fetcher 测试和 `tests/proxy.test.ts` 一行不改，全部要通过。
- 守护测试最先写。它对现在的代码是红的（10 处直接 `fetch`、9 处读网关地址），等调用点逐个改成 `gatewayFetch` 后变绿。
- 合格线：`pnpm lint && pnpm typecheck && pnpm test`。

## 14. 实施时要实测的点

PAP-67 本身没有页面，所以先用 curl 请求 `/api/auth/me` 验证 Cookie 这条链路。本机门户指向 mini 的网关（`http://100.103.73.27:9528`）。令牌只存在 shell 变量里，输出时遮掉；测试账号用一次性邮箱，dev 数据可以丢弃。

1. 直接调网关的注册接口建一个一次性账号，拿到令牌。
2. 带上 Cookie 请求 `/api/auth/me`：应返回 200 和用户；`Set-Cookie` 的名字是 `patra_session`，带 `Max-Age=2592000`、`HttpOnly`、`SameSite=lax`、`Path=/`，没有 `Secure`。
3. 不设开关重启门户，用 `__Host-patra_session` 再请求一次：`Set-Cookie` 的名字是 `__Host-patra_session`，并带上 `Secure`。
4. 直接调网关登出，再用同一个 Cookie 请求：应返回 200 `{ user: null }`，`Set-Cookie` 是 `Max-Age=0`，并带着和写入时相同的属性。
5. 不带 Cookie 请求：应返回 200 `{ user: null }`，没有 `Set-Cookie`。
6. 匿名页面：Playwright 指向 mini 的网关跑一遍 `tests/e2e/`，用例要全部通过。

Server Action 本身，以及浏览器里「页面脚本读不到令牌」这一条，要等 PAP-68 的页面做好后验收：跑 Playwright 登录冒烟，再用浏览器开发者工具看一遍。

## 15. 交给其他 Issue 的约束

| 约束 | 交给 |
|---|---|
| 导航栏用 `useCurrentUser()`：`pending` 显示占位，`anonymous` 显示「登录」，`authenticated` 显示圆形标识 | PAP-68 |
| 注册、登录成功后，用 `queryClient.setQueryData(queryKeys.currentUser, user)` 写入用户；登出完成后写入 null | PAP-68 |
| 按 `reason` 和字段的 `code` 选简报 §4 的文案；不认识或为 null 的原因码，显示该字段的通用错误；`retryAfterSeconds` 按分钟向上取整，不足 1 分钟按 1 分钟算，为 null 时显示「尝试次数过多，请稍后再试」 | PAP-68 |
| `register`、`login` 的 promise 被拒绝（比如浏览器没连上门户）时，按 `unavailable` 处理。`logout` 的 promise 被拒绝时，Cookie 可能还在，不要直接把用户写成 null，而是让 `queryKeys.currentUser` 重新请求一次，以门户的回答为准；界面照旧没有登出失败态（简报 §4.3） | PAP-68 |
| 写 Cookie 的 Server Action 会让当前页在同一次响应里重新渲染。登录页、注册页如果在渲染时把已登录的人跳走，这次重新渲染就会触发跳转，成功提示和跳转流程要按这一点设计 | PAP-68 |
| 服务端判断是否已登录用 `getCurrentUser()`，它不清除过期的 Cookie | PAP-68 |
| 登录流程的 Playwright 冒烟：`playwright.config.ts` 的 `webServer.env` 已经关掉 `Secure` | PAP-68 |
| 门户前面加反向代理或上 HTTPS 时：配 `serverActions.allowedOrigins`；删掉 `.env.portal` 里的 `PATRA_SESSION_COOKIE_SECURE=false`。切换后 Cookie 改名，旧的 `patra_session` 不再被读取，已登录的人要重新登录一次 | 引入反向代理或 HTTPS 的 Issue |

## 16. 要同步改的文档和 Issue

- `docs/patra/release-specs/v0.8-accounts.md`：D3 的第二条改成「30 天内有过访问就保持登录；从登录算起最长 180 天，到期要重新登录」。
- `patra-portal/docs/design-briefs/v0.8/accounts.md` §0：「登录状态默认保持，30 天不活跃才过期」改成「登录状态默认保持：30 天不活跃才过期，从登录算起最长 180 天」。
- Linear PAP-67：AC 第 3 条的改法同 D3；To-Do 里的 Tech Design 指向本设计；其余 To-Do 和 AC 随实施和实测勾掉。
- `patra-portal/.env.example`：加上 `PATRA_SESSION_COOKIE_SECURE` 的说明。
- `patra-portal/README.md`：快速开始里提一句「复制 `.env.example` 为 `.env.local`」；目录结构里加上 `lib/gateway.ts`、`lib/auth/` 和 `app/api/auth/`。
- `patra-infra/docker/.env.portal`：加上 `PATRA_SESSION_COOKIE_SECURE=false` 和注释。改这个文件时，CD 按「模块图以外的路径」判成全量，所以合并时 6 个后端服务会跟着重新构建、部署一次。
- `playwright.config.ts`：`webServer.env` 加上同一项。
- 本机的 `.claude/launch.json`：`portal-dev` 加上同一项（这个文件被 git 忽略，不进仓库）。
