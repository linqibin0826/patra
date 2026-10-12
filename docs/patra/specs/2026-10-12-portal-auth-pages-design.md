# 门户登录页、注册页与用户菜单工程设计（PAP-68）

> **Issue**：[PAP-68](https://linear.app/papertrace/issue/PAP-68)
> **版本**：[v0.8 Accounts](../release-specs/v0.8-accounts.md)（决策 E，Done 判定 D2 ~ D5、D8）
> **前置设计**：[门户认证基础设施（PAP-67）](2026-10-10-portal-auth-design.md) 第 3、9、15 节，[identity 账号（PAP-63）](2026-10-05-identity-account-design.md) 第 16 节，设计简报 [accounts.md](../../../patra-portal/docs/design-briefs/v0.8/accounts.md)，高保真稿快照 [`2026-10-04-accounts-hifi.zip`](../design/snapshots/2026-10-04-accounts-hifi.zip)（[PAP-61](https://linear.app/papertrace/issue/PAP-61)，持久画布 https://claude.ai/artifact/8jupGAB2phFjwVEBVgQHHX）
> **日期**：2026-10-12
> **状态**：待评审

## 1. 要解决的问题

PAP-67 做好了认证的底座：会话 Cookie、统一网关客户端、注册 / 登录 / 登出三个 Server Action、`GET /api/auth/me` 和 `useCurrentUser()`。但门户上还没有任何入口：访客看不到「登录」，也没有登录页和注册页。

PAP-68 补上界面，走通简报 §0 的闭环：任一页面点导航栏「登录」→ 登录页 →（成功）回到原页面，导航栏右上角变成圆形标识 → 用户菜单里点「退出登录」→ 仍在原页面，导航栏回到「登录」。注册同理。匿名浏览保持不变。

**完成标准**：PAP-68 的 7 条 Acceptance Criteria。

## 2. 范围

做：

- `/login`、`/register` 两个页面，以及专注布局的外壳 `AuthShell`。
- 表单组件 `FormField`、`PasswordInput`、`FormAlert`，两张表单 `LoginForm`、`RegisterForm`：前端校验、按原因码显示后端返回的失败、提交中、成功后回到来源页。
- `next` 参数的校验和传递（简报 §2 跳转规则 1 ~ 5、8）。
- 已登录的人打开登录页、注册页时直接跳走（规则 6），在 proxy 里做。
- 导航栏账号区：桌面的 `NavAccount` 和 `UserMenu`，移动端全屏菜单底部的账号块；退出登录。
- 新增原语 `dropdown-menu`：shadcn 的 base-nova 风格，底层是 base-ui 的 Menu，即简报里说的 `menu`。
- 成功提示的外观：`Toaster` 去掉 `richColors`，照高保真稿改。
- PAP-67 代码的三处小改：`session-cookie.ts` 加一个从请求对象读令牌的函数；`current-user.ts` 拆出接收令牌和超时的查询函数；删掉没有调用方的 `getCurrentUser()`。
- 单元测试、组件测试、登录流程的 Playwright 冒烟、两个尺寸的视觉验收。

不做：

- 后端接口。PAP-67 三个 Server Action、`/api/auth/me` 和 `useCurrentUser()` 的行为也都不改。
- 简报 §7「本版边界」里的入口：邮箱验证、找回密码、修改密码、第三方登录、个人资料、验证码、「记住我」、服务条款勾选、收藏。
- 在登录页和注册页之间切换时带上已填的邮箱。
- 不开 JavaScript 时提交表单（第 4.1 节方案 2）。
- 会话失效的专门提示。简报 §4.3 规定下次打开页面时静默回到未登录态，PAP-67 已经做到。
- 暗色模式。

## 3. 已定的前提

| 前提 | 来源 |
|---|---|
| 登录是可选的，匿名浏览与 v0.7 一致；登录入口只在导航栏一处；登录页、注册页用专注布局，不渲染 `TopNav` 和 `Footer` | 简报 §0、§3.1 |
| 跳转规则 1 ~ 8：来源页记在 `next` 参数里，互跳时原样带过去，成功后跳到 `next`；只接受站内地址，不接受登录页、注册页本身；已登录的人打开这两页直接跳走；登出后留在当前页；「返回」回到 `next` | 简报 §2 |
| 页面内容、字段、文案和必做状态。错误写在字段下方或表单级提示条里，不用 toast 报错；toast 只用于三条成功提示 | 简报 §3、§4 |
| 视觉以高保真稿为准：桌面 1440×900、移动 375×812 各一套画板，另有「表单组件」（`Spec-Form`）、「导航栏账号区」（`Spec-Nav`）两块组件规范。`patra.css` 下半段是新增组件的状态样式，注释里写了对应的 Tailwind 类 | PAP-61 快照 |
| 注册、登录只调 `register`、`login`：入参是 `{ email, password }` 对象，返回 `RegisterResult` / `LoginResult`，不抛错。`logout()` 总是清 Cookie。导航栏用 `useCurrentUser()` 的 `pending`、`anonymous`、`authenticated` 三态 | PAP-67 设计第 8.3、9 节 |
| 成功后 `setQueryData(queryKeys.currentUser, user)`，登出后写 null；`register`、`login` 的 promise 被拒绝时按 `unavailable` 处理；`logout` 的 promise 被拒绝时让当前用户重新请求一次，不提示 | PAP-67 设计第 15 节 |
| 前端校验的口径：邮箱去掉首尾空格后用 Zod 默认的 `z.email()` 加 `.max(254)`；密码长度按码点计；按原因码选文案，不认识的原因码显示该字段的通用错误；429 读 `retryAfterSeconds` | PAP-63 设计第 16 节 |
| 后端的原因码：邮箱 `REQUIRED`、`TOO_LONG`、`INVALID_FORMAT`；密码 `REQUIRED`、`INVALID_CHARACTER`、`TOO_SHORT`、`TOO_LONG`、`TOO_COMMON`。登录接口只检查密码非空 | `UserFieldViolations`，简报附录 A.2 |
| 组件不自带焦点环，焦点环来自全局的 `:focus-visible` 规则；`focus-within:border-*` 只能用 `border-focus` | `tests/design-system/focus.test.ts` |
| 全仓库只有 `session-cookie.ts` 调用 `cookies()`，只有 `gateway.ts` 向网关发请求 | PAP-67 设计第 13 节的守护测试 |

本设计新定的事（2026-10-12 与用户商定）：

| 决定 | 内容 | 否决的选项 |
|---|---|---|
| 表单怎么提交 | 客户端表单直接 `await login(values)`，成功后写缓存、弹提示、`router.replace(next)`（第 4.1 节方案 1） | `<form action>` 绑定 Server Action；在 action 里 `redirect(next)` |
| 规则 6 放在哪 | proxy：只拦 GET，读请求里的 Cookie，问网关 `/auth/me`，已登录就 307 到 `next`（第 7 节） | 页面渲染时判断后 `redirect()`：实测有两种情况会闪约 0.8 秒空白（第 4.2 节） |
| proxy 怎么判断已登录 | 请求网关 `/auth/me`，超时 3 秒，失败就放行 | 只看有没有 Cookie：过期的 Cookie 会把还没登录的人从登录页弹走 |
| 成功后目标页多渲染的一次 | 接受，写进已知代价（第 12 节） | 等登录页那次重渲染生效后再跳转：依赖 Next 内部的执行时序，换个版本可能就失效 |
| `next` 的校验 | 规则 4 的前两个字符，加上按 URL 解析后仍是本站（第 6 节） | 只看前两个字符：挡不住 `/\t/evil.com`，浏览器会去掉其中的制表符，它就变成了站外地址 |
| 简报没覆盖的文案 | 邮箱超长（`TOO_LONG`）用「邮箱格式不正确」；密码的通用错误（`INVALID_CHARACTER`、不认识的原因码、原因码为 null）用新文案「密码不符合要求」；字段名对不上时显示表单级「…没有成功，请稍后再试」 | 为 `TOO_LONG` 单写「邮箱最长 254 个字符」：超过 254 个字符的地址本来就不是有效邮箱，用户不需要知道这个上限 |
| 提交中的按钮 | `aria-disabled`，不用 `disabled` | `disabled`：按钮被禁用的那一刻失去焦点，键盘和读屏用户的位置丢了 |
| 提交中离开了表单 | 互跳链接和「返回」仍可点；请求照常完成，成功时照样写缓存、弹提示、跳到 `next` | 离开就丢弃结果：用户其实已经登录了，界面却还是未登录 |
| 加不加 `noindex`、预取 | 两页都加 `noindex`；点提交时预取目标页 | — |

写 spec 时补的决定（讨论中没有出现，评审时请确认）：

| 决定 | 内容 | 理由 |
|---|---|---|
| 删掉 `getCurrentUser()` | 连同它的测试一起删 | 它是给页面渲染时做规则 6 准备的（PAP-67 设计第 8.1、15 节）。规则 6 改到 proxy 后没有调用方；仓库只维护当前在用的接口 |
| 邮箱的通用错误 | 不认识的原因码、原因码为 null，也显示「邮箱格式不正确」 | 和 `TOO_LONG` 的处理一致。邮箱字段只剩「请输入邮箱」「邮箱格式不正确」两种说法 |
| 登录页、注册页里的站内链接用 replace | 互跳、「去登录」、「返回」都替换当前这条历史记录 | 成功后的历史记录里不留登录页、注册页。否则按后退会回到登录页：要么被 proxy 弹走，要么看到客户端缓存里的表单（第 12 节第 4 条） |
| 写入当前用户前先取消进行中的请求 | 登录、注册成功和登出成功时，先 `cancelQueries` 再 `setQueryData` | 成功前发出、成功后才回来的 `/api/auth/me` 会覆盖刚写入的值，导航栏又变回原样 |
| 提交后的重新校验 | `reValidateMode` 也设成 `onBlur`；边输入边校验只用于已经报错的字段 | RHF 默认在提交后对所有字段边输入边校验，输入到一半就会报错，和简报 §6 不一致 |
| 提交时连后端字段错误一起清 | 和提示条一起清掉，再校验、请求 | 再次提交得到的是新结果，旧的错误不该留着 |
| `next` 出现多次 | 页面和 proxy 都取第一个 | 两处的结果一致 |

版本：Next.js 16.3.8、React 19.3、TanStack Query 5.103.2、React Hook Form 7.88、Zod 4.6.5、base-ui 1.8、sonner 2.0.8。下面用到的框架行为，都对照源码、随包文档或实测核实过：

- 写 Cookie 的 Server Action 会让当前页在同一次响应里重新渲染（PAP-67 设计第 3 节）。这次渲染里 `cookies()` 读到的是刚写入的值；页面里的 `redirect()` 默认是 replace（实测，action 响应里是 `NEXT_REDIRECT;replace;…`）。
- action 的返回值先到，页面的重渲染随后才到，由路由的 action 队列应用。这期间客户端发起导航，Next 会把这次 action 标成 discarded，不再应用它的渲染结果；因为它写过 Cookie，等队列空了再补一次 refresh（`node_modules/next/dist/client/components/app-router-instance.js` 的 `dispatchAction`、`runRemainingActions`）。所以登录成功后的跳转如果赶在这次重渲染生效之前，目标页就会在服务端多渲染一次。
- `router.replace` 放进 `startTransition`，transition 会一直挂起到导航完成，这期间可以保持提交中的样子（`node_modules/next/dist/docs/01-app/02-guides/interactive-apps.md` 第 3 步）。
- proxy 对客户端导航发出的 RSC 请求回 307 时，客户端路由会跟着跳过去，地址栏不经过原地址（实测）。
- 静态预渲染的页面里，调用 `useSearchParams()` 的客户端组件要包在 Suspense 里，否则 `next build` 报错（`node_modules/next/dist/docs/01-app/03-api-reference/04-functions/use-search-params.md`）。404 页是静态预渲染的，导航栏也在里面。
- proxy 用 `request.cookies` 读 Cookie，默认跑在 Node.js 运行时，可以引入带 `server-only` 的模块（PAP-67 设计第 3 节）。

## 4. 方案比较

### 4.1 表单怎么提交、成功后怎么回去

| | 做法 | 取舍 |
|---|---|---|
| 方案 1（采用） | 页面只做布局。客户端表单（RHF + Zod）直接 `await login(values)` / `register(values)`，按结果显示错误；成功后写缓存、弹提示、`router.replace(next)` | 完全沿用 PAP-67 的接口；成功后导航栏立刻是圆形标识；实测不闪空白。代价：目标页可能在服务端多渲染一次 |
| 方案 2 | `<form action>` 加 `useActionState`，表单直接绑定 Server Action | 不开 JavaScript 也能提交。但 PAP-67 的 action 接收对象，传 FormData 会直接返回 `unavailable`；失焦校验、出错后聚焦都要客户端逻辑；门户规定表单用 RHF |
| 方案 3 | 给 action 包一层，成功后在 action 里 `redirect(next)` | 登录和渲染目标页在一次往返里完成，目标页只渲染一次。但 action 里跳转时，客户端的 promise 会被拒绝，拿不到 `user`，也没有地方弹成功提示；导航栏要先显示占位、再请求一次，才变成圆形标识；还改掉了 PAP-67「action 只返回结果、不抛错」的约定 |

多渲染的那一次有办法避开：等登录页的重渲染生效之后再跳转。但这依赖 Next 内部的执行时序，换个版本可能就失效。它省下的只是每次登录多出的一次服务端渲染，不值得为此引入这种依赖。

### 4.2 规则 6 放在哪

2026-10-11 用 Next 16.3.8 搭了一个最小应用实测（生产构建，webpack；实验代码没有入库）。登录页渲染时模拟 200 ms 的会话查询，action 150 ms，目标页加载 800 ms；每种做法跑 3 轮，结果一致。「空白」指页面上既没有表单、也没有目标页的时段。

| 做法 | 登录成功 | 已登录，直接打开登录页 | 已登录，在站内点链接进登录页 |
|---|---|---|---|
| 页面渲染时用 `cookies()` 判断，表单不主动跳转，靠重渲染里的 `redirect()` | 空白约 0.8 秒 | 307，不显示表单 | 空白约 0.8 秒，地址栏先变成 `/login` |
| 同上，但表单拿到结果后立刻 `router.replace(next)` | 这次不闪。但表单在跳转前多等 5 ms 以上（比如先写缓存），就和上一行一样空白约 0.8 秒 | 同上 | 同上 |
| 页面渲染时只看原始的 `Cookie` 请求头（重渲染时看不到新 Cookie），表单主动跳转 | 不闪，目标页渲染 2 次 | 同上 | 同上 |
| **proxy 只拦 GET，表单主动跳转（采用）** | 不闪，目标页渲染 2 次；表单一直显示「登录中…」，直到目标页出来 | 同上 | 不闪，直接落到目标页，地址栏不经过 `/login` |

- 四种做法都保留了成功提示，登录页那条历史记录都被替换掉，按后退回到打开登录页之前的页面。
- 对照组：action 不写 Cookie 时，目标页只渲染 1 次。多出的那次来自写 Cookie 后补的 refresh。
- 空白的时长就是目标页的加载时间。页面渲染时判断，在站内点链接进登录页这一种情况下怎么都会闪，所以规则 6 只能放在页面渲染之前，也就是 proxy。

## 5. 模块与组件

| 文件 | 职责 |
|---|---|
| `src/lib/auth/next-path.ts`（新） | `safeNextPath(raw)`：校验 `next`。`authPageHref(page, next)`：拼登录页、注册页的链接。纯函数，浏览器和服务端共用 |
| `src/lib/auth/messages.ts`（新） | 全部文案，以及原因码 → 字段文案、失败结果 → 提示条文案、限流分钟数的映射 |
| `src/lib/auth/form-schemas.ts`（新） | 登录、注册表单的 Zod schema，错误文案取自 `messages.ts` |
| `src/lib/auth/logout-mutation.ts`（新） | `useLogout()`：包装 `logout()` 的 `useMutation`，桌面和移动端共用 |
| `src/lib/auth/session-cookie.ts`（改） | 加 `readRequestSessionToken(req)` |
| `src/lib/auth/current-user.ts`（改） | 拆出 `lookupSessionByToken(token, timeoutMs?)`，`lookupSession()` 改为调用它；删掉 `getCurrentUser()` |
| `src/proxy.ts`（改） | matcher 加 `/login`、`/register`，做规则 6 |
| `src/app/login/page.tsx`、`src/app/register/page.tsx`（新） | 服务端组件：读 `next`，渲染外壳和表单；标题、`noindex` |
| `src/components/auth/AuthShell.tsx`（新） | 专注布局外壳（服务端组件） |
| `src/components/auth/FormField.tsx`、`PasswordInput.tsx`、`FormAlert.tsx`（新） | 表单组件 |
| `src/components/auth/LoginForm.tsx`、`RegisterForm.tsx`（新） | 两张表单（客户端组件） |
| `src/components/auth/use-auth-submit.ts`（新） | 两张表单共用的提交流程：防重复提交、预取、调用 action、成功后写缓存、弹提示、跳转 |
| `src/components/ui/dropdown-menu.tsx`（新） | shadcn 原语 |
| `src/components/portal/NavAccount.tsx`、`UserMenu.tsx`、`MobileAccount.tsx`（新） | 桌面账号区、用户菜单、移动端账号块 |
| `src/components/portal/TopNav.tsx`（改） | 放进账号区和账号块，各包一层 Suspense |
| `src/lib/portal-ui.ts`（改） | 按钮样式支持 `aria-disabled` |
| `src/components/ui/sonner.tsx`、`src/app/layout.tsx`（改） | 成功提示的外观；去掉 `richColors` |

依赖方向：表单依赖 actions、next-path、form-schemas、messages 和 query-keys；导航栏组件依赖 current-user-query、logout-mutation 和 next-path；proxy 依赖 next-path、session-cookie 和 current-user。next-path、messages、form-schemas 不带 `server-only`，客户端组件也能引用。

## 6. `next` 参数

`safeNextPath(raw: string | null | undefined): string`，不合法时一律返回 `/`：

1. 不是非空字符串：不合法。
2. 规则 4：第一个字符必须是 `/`，第二个字符不能是 `/` 或 `\`。
3. 用 `new URL(raw, "http://patra.invalid")` 解析。解析出错，或者解析后的 origin 不再是这个占位地址：不合法。WHATWG URL 解析会去掉制表符和换行、把 `\` 当成 `/`，`/\t/evil.com` 在这一步变成 `//evil.com`，origin 变了，被挡住。
4. 规则 5：解析后的路径去掉末尾的 `/`，是 `/login` 或 `/register`：不合法。`/papers/../login` 解析后就是 `/login`，同样被挡住。
5. 合法时返回解析后的「路径 + 查询串」，`#` 及之后的部分丢掉。

`authPageHref(page: "/login" | "/register", next: string): string`：先过一遍 `safeNextPath`；结果是 `/` 时只返回 `page`，否则返回 `` `${page}?${new URLSearchParams({ next })}` ``。比如在 `/papers?q=GLP-1` 时，导航栏「登录」的链接是 `/login?next=%2Fpapers%3Fq%3DGLP-1`，和简报 §2 的例子一致。

`next` 从哪来、到哪去：

- **导航栏**：用 `usePathname()` 和 `useSearchParams()` 拼出当前地址（不含 `#`），交给 `authPageHref`。用在桌面的「登录」和移动端的「登录」「注册」。
- **页面**：读 `searchParams.next`（出现多次时取第一个），经 `safeNextPath` 后交给外壳和表单。外壳的「返回」直接指向它；表单里的互跳链接和注册页的「去登录」用 `authPageHref`；成功后 `router.replace(next)`。
- **proxy**：`req.nextUrl.searchParams.get("next")`（同样取第一个），经 `safeNextPath` 后作为 307 的目标。

登录页、注册页里的站内链接（互跳、「去登录」、「返回」）都用 `<Link replace>`。走完「登录 → 注册 → 成功」，历史记录里只剩来源页，没有登录页和注册页。左上角的品牌标识回首页，用普通的 push。

## 7. proxy：已登录的人打开登录页、注册页

matcher 改为 `["/papers/:id", "/journals/:id", "/login", "/register"]`。`proxy()` 先按路径分支，详情页那一支一行不改。

登录页、注册页这一支：

1. 只处理 GET，其他方法直接放行。Server Action 是发到当前页的 POST，也在其中。
2. 不沿用详情页「`sec-fetch-dest: empty` 直接放行」的规则：站内点链接、预取都是这类请求，也要拦。
3. 用 `readRequestSessionToken(req)` 读会话令牌。没有 Cookie 就直接放行，不请求网关，所以未登录的人不多付一次网关请求。读 Cookie 抛错（`Secure` 开关写错）也放行。
4. 有令牌时，用 `lookupSessionByToken(token, 3_000)` 问网关 `/auth/me`：
   - `authenticated`：307 到 `safeNextPath(next)`。
   - 其他结果（`rejected`、`unavailable`，包括超时和网关出错）：放行，显示表单。
5. proxy 不写 Cookie，不刷新也不清除。令牌失效的 Cookie 留给 `/api/auth/me` 清，或者被下一次登录覆盖。

对 PAP-67 代码的改动：

- `session-cookie.ts`：加 `readRequestSessionToken(req: NextRequest): string | null`，从 `req.cookies` 读，空值按没有处理。Cookie 的名字仍只出现在这个文件里。
- `current-user.ts`：拆出 `lookupSessionByToken(token: string | null, timeoutMs?: number): Promise<SessionLookup>`。它不读 Cookie，判断规则和原来的 `lookupSession()` 完全相同（PAP-67 设计第 8.1 节的表格）；`timeoutMs` 不传时用 `gatewayFetch` 默认的 10 秒。`lookupSession()` 读出令牌后调用它，行为不变。
- 删掉 `getCurrentUser()` 和它的测试。

## 8. 页面与外壳

**页面**（`src/app/login/page.tsx`、`src/app/register/page.tsx`）：

- 服务端组件，不读 Cookie，也不判断登录状态（proxy 已经判断过）。只做两件事：用 `safeNextPath` 算出 `next`；渲染 `AuthShell` 和对应的表单。
- `metadata`：标题「登录 · Patra」/「注册 · Patra」；`robots: { index: false }`，不让搜索引擎收录。
- 根布局本来就不渲染导航栏和页脚（各页面自己渲染），所以这两页不渲染它们就行，不需要路由分组。

**`AuthShell`**（服务端组件）。版式和尺寸照高保真稿：桌面看 `Main`（登录）、`Register-Default`，移动看 `Login-M-Default`、`Register-M-Default`。

- 左上角品牌标识：`BrandMark` 加「Patra」，桌面另带「医学文献门户」，移动端不带；链接到 `/`，读屏名称「Patra 首页」。
- 「返回」链接：带向左的箭头，指向 `next`。
- 表单区。
- 桌面右侧的墨色品牌面板：`Venation` 叶脉图形、图注「图 1 · 叶脉」、定位语「十个来源，汇入同一根主脉。」。宽度 ≤ 880px 时不显示。
- 桌面端一屏内完成，不出现滚动（简报 §3.1）。

进入页面时焦点在邮箱框（简报 §6），邮箱框用 `autoFocus`。

## 9. 表单

### 9.1 组件

- `FormField`：标签、输入框，下面一行是常驻提示或错误文案。负责 `id` 关联、`aria-invalid` 和 `aria-describedby`（指向当前显示的那一行）。错误文案替换常驻提示，两者不同时出现（简报 §4.1）。错误行带图标，不只靠颜色（简报 §7）。
- `PasswordInput`：输入框右侧是显示 / 隐藏切换按钮，读屏名称「显示密码」/「隐藏密码」，默认隐藏。切换后光标位置和选区不变。注册页两个密码框的切换互不联动。
- `FormAlert`：表单级提示条，`role="alert"`。
- `LoginForm`、`RegisterForm`：客户端组件，RHF 加 `zodResolver`，`<form noValidate>`。

视觉照 `Spec-Form` 和 `patra.css`：

- 聚焦：`focus-within:border-border-focus focus-within:ring-4 ring-clay-500/15`。
- 错误：`has-[[aria-invalid=true]]:border-status-danger`；错误态下聚焦，光晕换成 `ring-rust-500/15`。
- 提交中只读：底色换成 `--bg-sunken`，去掉内阴影。选择器要写 `has-[input:read-only]`，不能照抄注释里的 `has-[:read-only]`：按钮、图标这类不可编辑的元素也匹配 `:read-only`，输入框会一直显示成只读。

### 9.2 校验

| 字段 | 规则 | 页面 |
|---|---|---|
| 邮箱 | 去掉首尾空白后：为空，「请输入邮箱」；超过 254 个字符或不符合 Zod 默认的 `z.email()`，「邮箱格式不正确」。提交的是去掉空白后的值 | 两页 |
| 密码 | 不去空格。为空，「请输入密码」 | 两页 |
| 密码 | 按码点计长度（`[...password].length`）：少于 8，「密码至少 8 位」；多于 64，「密码最长 64 位」 | 注册页 |
| 确认密码 | 为空，「请再输入一次密码」；和密码不同，「两次输入的密码不一致」。只在前端比对，不传给后端 | 注册页 |

前端不比后端严：后端的邮箱正则就是照抄 Zod 4.6.5 默认的那一条，密码长度也按码点计。前端放行、后端拒绝的只剩「邮箱已注册」「密码太常见」「密码含无效字符」，由后端在提交后返回。

### 9.3 校验时机（简报 §6）

- RHF 配 `mode: "onBlur"`、`reValidateMode: "onBlur"`：离开一个字段时校验它。
- 点提交或在输入框里回车时，校验全部字段，焦点移到第一个出错的字段（RHF 的 `shouldFocusError`）。
- 字段显示错误之后，每次输入都重新校验它，改对了错误立即消失。RHF 没有「只在报错后边输入边校验」的模式，所以在字段的 `onChange` 里补一段：这个字段当前有错误时，调用 `trigger(字段)`。
- 注册页：确认密码不为空时，密码每次改动都重新比对确认密码。
- 后端返回的字段错误（`invalid-fields` 里的各项、`email-taken`）放在 RHF 之外，和提交时的值绑在一起：字段的值和提交时相同（邮箱按去掉空白后的值比），就一直显示；值一改，立即消失。放进 RHF 的错误里不行：用户一移出焦点，失焦校验就会把它清掉。
- 同一个字段既有前端错误、又有后端错误时，显示前端错误。

### 9.4 后端返回失败时

| 结果 | 显示 | 之后的表单 |
|---|---|---|
| `invalid-fields` | 各字段下方，文案见下表；同一字段有多条时只显示第一条。一条都对不上 `email`、`password` 时，按表中最后一行 `unavailable` 处理 | 保留全部内容，焦点移到第一个出错的字段 |
| `email-taken`（注册） | 邮箱下方「该邮箱已注册，去登录」，「去登录」是链接，指向 `authPageHref("/login", next)` | 保留全部内容，焦点移到邮箱 |
| `bad-credentials`（登录） | 提示条「邮箱或密码错误」 | 保留邮箱，清空密码，焦点回到密码框 |
| `throttled`（登录） | 提示条「尝试次数过多，请 N 分钟后再试」，N = max(1, ⌈秒数 ÷ 60⌉)；秒数为 null 时「尝试次数过多，请稍后再试」。不做倒计时，不禁用提交按钮 | 同上 |
| `banned`（登录） | 提示条「该账号已被封禁，无法登录」 | 同上 |
| `unavailable`，或 action 的 promise 被拒绝 | 提示条「登录没有成功，请稍后再试」/「注册没有成功，请稍后再试」 | 登录页同上；注册页保留全部内容，焦点留在提交按钮上 |

字段文案：

| 字段 | 原因码 | 文案 |
|---|---|---|
| `email` | `REQUIRED` | 请输入邮箱 |
| `email` | `INVALID_FORMAT`、`TOO_LONG`、其他原因码、null | 邮箱格式不正确 |
| `password` | `REQUIRED` | 请输入密码 |
| `password` | `TOO_SHORT` | 密码至少 8 位 |
| `password` | `TOO_LONG` | 密码最长 64 位 |
| `password` | `TOO_COMMON` | 这个密码太常见，容易被猜到，请换一个 |
| `password` | `INVALID_CHARACTER`、其他原因码、null | 密码不符合要求 |

每次提交（点按钮或回车），先清掉提示条和后端字段错误，再校验、请求。同样的错误再次出现时，提示条是重新插入的，读屏会再读一遍（简报 §4.5）。

### 9.5 提交中

- 从通过校验、发出请求开始，到失败结果显示出来，或者成功后的跳转完成为止。
- 所有输入框 `readOnly`。按钮文字换成「登录中…」/「注册中…」，`title` 是「正在登录」/「正在注册」。
- 按钮用 `aria-disabled="true"`，不用 `disabled`：点击和回车都不再提交，焦点留在按钮上。`portal-ui.ts` 的按钮样式加上 `aria-disabled` 的写法，样子和高保真稿的禁用态相同（不可点的光标，55% 不透明度），也不响应 hover 和按下。现在的样式只认 `disabled`。
- 提交中再次提交（连点、在输入框里回车）一律忽略，只发一次请求。
- 互跳链接和「返回」仍然可以点（简报 §6）。离开后请求照常完成：成功时照样写缓存、弹提示、跳到 `next`；失败时表单已经卸载，什么也不显示。

### 9.6 成功之后

1. 通过校验、调用 action 之前，先 `router.prefetch(next)`。
2. action 返回成功后，先取消正在进行的当前用户请求（`cancelQueries`），再 `setQueryData(queryKeys.currentUser, user)`。不先取消的话，一个成功前发出、成功后才回来的 `/api/auth/me` 会把用户覆盖成 null。
3. 弹出提示：登录是「已登录」，注册是「注册成功，已登录」，都用 `toast.success`。
4. `startTransition(() => router.replace(next))`。transition 结束前保持提交中的样子，防止重复提交。
5. 提示会比目标页早出现一会儿，大约早一个目标页的加载时间。`Toaster` 挂在根布局上，跳过去之后提示还在。
6. replace 替换掉登录页（或注册页）这条历史记录，按后退回到打开登录页之前的那一条。

### 9.7 自动填充与输入

属性照高保真稿：

| 输入框 | `type` | `autocomplete` |
|---|---|---|
| 登录页邮箱 | `email` | `username` |
| 登录页密码 | `password` | `current-password` |
| 注册页邮箱 | `email` | `email` |
| 注册页密码、确认密码 | `password` | `new-password` |

- 输入框都加 `spellCheck={false}`；不禁止粘贴。
- 浏览器自动填充后，输入框底色保持纸色（照 `patra.css` 里 `:-webkit-autofill` 的写法）。
- 移动端输入框字号 17px，避免 iOS 在聚焦时放大页面。

## 10. 导航栏账号区

### 10.1 menu 原语

用 shadcn CLI 添加 `dropdown-menu`（`components.json` 的 style 是 base-nova，生成的组件基于 base-ui 的 Menu），放在 `src/components/ui/`，颜色、圆角、阴影和动效换成 token。CLI 拉不下来时（本机并行连接不稳），照 shadcn registry 里 base-nova 的源码手写进去。

### 10.2 桌面（宽度 > 880px）

`NavAccount` 放在「关于」右边，三种形态：

| 状态 | 呈现 |
|---|---|
| `pending` | 36px 的圆形占位，底色 `--bg-sunken`，`role="status"`，读屏名称「正在确认登录状态」。服务端渲染出的就是它，所以不会先闪一下「登录」 |
| `anonymous` | 「登录」链接，`btnSecondary` 的导航尺寸，指向 `authPageHref("/login", 当前地址)` |
| `authenticated` | 36px 的圆形按钮，底色 `--ink-900`，显示邮箱的第一个字符（按码点取），字母转大写，数字和符号原样；读屏名称「账号菜单」；点开是 `UserMenu` |

`UserMenu`：

- **内容**：第一行是「当前账号」和完整邮箱，不可点击，方向键也不会停在这一行；过长时截断，`title` 是完整邮箱。下面是分隔线，然后是「退出登录」。
- **位置**：圆形下方 8px，与圆形右缘对齐，宽度 240 ~ 320px。
- **动效**：入场时淡入、下落 8px、从 0.98 缩放到 1，300ms，`ease-out-expo`；开启「减弱动态效果」时只淡入。
- **行为**：都用 base-ui 自带的：点圆形开合，方向键在菜单项之间移动，Esc 和点菜单外关闭，关闭后焦点回到圆形。只有「退出登录」点了以后不关菜单（`closeOnClick={false}`），原因见第 10.4 节。

`TopNav` 把 `NavAccount` 包在 Suspense 里，fallback 是同样的圆形占位。

### 10.3 移动端（宽度 ≤ 880px）

顶栏不变。全屏菜单的最底部（「关于 Patra」「GitHub」之后）加一个账号块，上方一条分隔线，三种形态都是 80px 高：

| 状态 | 呈现 |
|---|---|
| `pending` | 80px 的中性占位，底色 `--bg-sunken`，圆角 `--radius-sm`，`role="status"`，读屏名称「正在确认登录状态」 |
| `anonymous` | 「登录」按钮，下面一行「还没有账号？注册」。两个链接都带当前地址作 `next`，点击后先关闭菜单 |
| `authenticated` | 「当前账号」、邮箱（过长截断）和「退出登录」按钮 |

`MobileAccount` 同样包一层 Suspense，fallback 是 80px 的占位。

### 10.4 退出登录

`useLogout()`（`src/lib/auth/logout-mutation.ts`）用 `useMutation` 包装 `logout()`，桌面和移动端共用：

- **点击后**：立即执行，不二次确认。进行中「退出登录」禁用，`title` 是「正在退出登录」；桌面菜单一直开着，直到退出完成。
- **成功**：先取消正在进行的当前用户请求，再 `setQueryData(queryKeys.currentUser, null)`，弹出提示「已退出登录」。留在当前页（规则 7）。
  - 桌面：圆形变回「登录」，焦点移到「登录」上，因为原来聚焦的菜单项已经不存在了。
  - 移动端：关闭全屏菜单。
- **promise 被拒绝**：不写 null，而是 `invalidateQueries(queryKeys.currentUser)`，让当前用户重新请求一次，以门户的回答为准。不提示，也不报错（PAP-67 设计第 15 节；简报 §4.3「登出没有失败态」）。菜单保持打开。

## 11. 成功提示

- 沿用根布局右下角的 `Toaster`，去掉 `richColors`：它会把 `toast.success` 渲染成绿底，和高保真稿的「浅底 + moss 色图标」不一致。现有的普通 `toast()` 不受影响。
- 成功图标用 moss 色（`text-status-success`），阴影用 `--shadow-lg`，底色、文字、边框、圆角沿用 `sonner.tsx` 现有的 token。
- 移动端照高保真稿 `Nav-M-Toast`：距左右和底边各 16px。
- 三条文案：「已登录」「注册成功，已登录」「已退出登录」。

## 12. 已知代价

1. 登录、注册成功后，目标页最多在服务端多渲染一次（第 3、4.2 节）。用户看不出来，但目标页的数据会多取一次。
2. 登出时，当前页在服务端重新渲染一次。写 Cookie 的 Server Action 都会这样（PAP-67 设计第 9.5 节）。
3. 已登录的人打开登录页、注册页时，proxy 要多等一次网关，最多 3 秒。未登录的人没有 Cookie，不请求网关。
4. 已登录的人如果用浏览器后退回到登录页，而 Next 直接用客户端缓存显示它，请求不经过 proxy，会看到表单。站内链接改用 replace 之后（第 6 节），正常的登录、注册流程不会留下这样的历史记录；万一出现，再提交一次也只是重新登录。
5. 多个标签页之间不同步：在 A 标签页登录或登出，B 标签页的导航栏要等下次请求当前用户时才变（PAP-67 的设定：60 秒内不重新请求，切回窗口时也不请求）。

## 13. 安全

- 开放重定向：`next` 只接受站内地址（第 6 节）。proxy 的 307 和表单的 `router.replace` 都只用 `safeNextPath` 的结果。
- 密码只在表单的内存里：不进 URL、不进日志、不进查询缓存。邮箱也不放进 URL。
- 令牌仍只在 HttpOnly Cookie 里，页面脚本读不到；导航栏只拿到 `userId` 和 `email`。
- CSRF 防护沿用 PAP-67 设计第 11 节，本设计不新增会改数据的 GET。

## 14. 测试策略

按 TDD 一个一个写，测试文件放在 `tests/` 下，与现有结构一致。组件测试里，`register`、`login`、`logout`、`next/navigation` 的 `useRouter` 和 sonner 的 `toast` 都打桩。

| 测试文件 | 环境 | 验证什么 |
|---|---|---|
| `tests/lib/auth/next-path.test.ts` | node | 合法地址原样返回，`#` 之后的部分去掉；`//evil.com`、`/\evil.com`、`/\t/evil.com`、`https://evil.com`、`/login`、`/login/`、`/register?x=1`、`/papers/../login`、空串、null 都回到 `/`；`authPageHref` 在 `next` 为 `/` 时不带参数，其余按简报 §2 的例子编码 |
| `tests/proxy.test.ts`（加用例） | node | 已登录时 GET 登录页、注册页回 307 到 `next`，`next` 不合法时到 `/`；没有 Cookie 时放行且不请求网关；令牌失效、网关出错、超时都放行；POST 直接放行、不请求网关；`sec-fetch-dest: empty` 的请求同样会被拦；请求网关的超时是 3 秒。详情页原有的用例一行不改 |
| `tests/lib/auth/session-cookie.test.ts`（加用例） | node | `readRequestSessionToken` 两种 Cookie 名都读得出；空值按没有处理 |
| `tests/lib/auth/current-user.test.ts`（改） | node | `lookupSessionByToken` 覆盖 PAP-67 设计第 8.1 节的表格，`timeoutMs` 传到 `gatewayFetch`；`lookupSession` 原有用例全部保留；`getCurrentUser` 的用例随函数删掉 |
| `tests/lib/auth/messages.test.ts` | node | 每个字段、每个原因码的文案，包括两条通用错误；限流分钟数测 0、59、60、61 秒和 null |
| `tests/lib/auth/form-schemas.test.ts` | node | 邮箱去首尾空白、254 个字符的上限、`z.email()` 格式；密码按码点计长度，汉字和表情各算 1 位，测 8 位、64 位两个边界；登录页的密码只要求非空；确认密码 |
| `tests/components/auth/LoginForm.test.tsx`、`RegisterForm.test.tsx` | jsdom | 第 9.3 节的校验时机；后端每种结果的显示和焦点位置；后端字段错误跟着值走；提交中字段只读、按钮 `aria-disabled`、连点只发一次请求；成功时写缓存、弹对应的提示、调用 `router.replace(next)`；action 的 promise 被拒绝时按 `unavailable` 处理；提交中表单被卸载、之后请求成功，仍然跳转；注册页两个显示 / 隐藏切换互不影响 |
| `tests/components/auth/PasswordInput.test.tsx` | jsdom | 切换、读屏名称、切换后光标位置不变 |
| `tests/components/portal/NavAccount.test.tsx`、`MobileAccount.test.tsx` | jsdom | 三种形态，占位阶段不出现「登录」；链接里的 `next`；三个样例邮箱的首字符 C、P、2；退出进行中禁用，成功后桌面焦点落到「登录」、移动端关闭菜单，失败时重新请求当前用户 |
| `tests/lib/auth/logout-mutation.test.tsx` | jsdom | 成功时写 null 并提示；进行中的 `/api/auth/me` 晚回来，不会把 null 覆盖掉；失败时重新请求、不提示 |
| `tests/components/portal/TopNav.test.tsx`、`tests/app/loading-busy.test.tsx`（改） | jsdom | 补上 QueryClientProvider 和 `/api/auth/me` 的桩，原有断言不变 |

端到端冒烟（Playwright，`tests/e2e/auth.spec.ts`）：

- 没有设置 `PATRA_GATEWAY_BASE_URL` 时跳过。CI 里没有网关，会跳过；本地内联指向 mini 的网关跑。每跑一次，mini 上多一个一次性账号，dev 数据可以丢弃。
- 主流程按简报 §2 的文字流：在 `/papers?q=GLP-1` 点「登录」，再点去注册 → 注册后回到 `/papers?q=GLP-1`，看到「注册成功，已登录」和圆形标识 → 退出登录 → 再登录一次 → 再退出一次。邮箱每次随机生成，密码在运行时生成，不写进仓库。
- 注册成功后按后退，不会回到登录页或注册页。
- 登录后 `document.cookie` 里没有会话 Cookie（页面脚本读不到令牌，PAP-67 设计第 14 节留到这里验收的一条）。
- 规则 6：已登录时直接打开 `/login?next=%2Fjournals`，最后停在 `/journals`。

视觉验收：在 1440×900 和 375×812 两个尺寸下截图，和高保真稿的对应画板逐项比对。范围是登录页、注册页的默认态、几个错误态和提交中，导航栏的未登录态和菜单展开，以及移动端账号块。差异整理成一张对照页，交给用户验收。

合格线：

- `pnpm lint && pnpm typecheck && pnpm test` 全部通过。
- `pnpm build` 通过。`useSearchParams` 漏包 Suspense 只在构建时才报错，所以必须跑。
- Playwright 冒烟和现有 e2e 指向 mini 跑通。现有 e2e 用来确认未登录时的浏览行为没有变化。
- 用户本地验收通过后，和 PAP-67 一起开一个 draft PR，描述里写 `Closes PAP-67`、`Closes PAP-68`。

## 15. 实施时要实测的点

1. 输入框错误态的描边压得过 `focus-within:border-border-focus`（Tailwind 变体的先后顺序）；错误态聚焦时的 `ring-rust-500/15` 能通过 focus 守护测试。
2. sonner 的阴影能改成 `--shadow-lg`，成功图标是 moss 色；移动端的位置和高保真稿一致。
3. 点提交时的 `router.prefetch(next)` 不影响 action 的请求和结果。
4. 站内点「登录」进入登录页后，焦点在邮箱框：App Router 导航后会对新页面做焦点处理，要确认它不会把焦点抢走。
5. 浏览器后退到登录页时，是否经过 proxy（第 12 节第 4 条）。
6. 本地服务端日志里，登录成功后目标页渲染一到两次、登出时当前页重新渲染一次，和第 12 节一致。

实施后把结果写回本节。

## 16. 评审时要重点看的输入

- 奇怪的 `next`：编码过的斜杠和反斜杠（`/%2F/evil.com`、`/%5Cevil.com`）、控制字符、很长的值、出现多次。
- 很长的邮箱（简报 §3.6 的第二个样例）：输入框里横向滚动，菜单和移动端账号块里截断。
- 浏览器自动填充和密码管理器：填充后 RHF 能拿到值，失焦校验不误报，底色保持纸色。
- 提交中离开页面：点互跳、「返回」、浏览器后退。
- 网关慢或不可用：proxy 3 秒后显示表单；提交得到 `unavailable`。

## 17. 要同步改的文档和 Issue

- PAP-67 设计（`2026-10-10-portal-auth-design.md`）：第 5、8.1、10、13 节里的 `getCurrentUser()` 删掉；第 9.5 节和第 15 节里「登录页、注册页在渲染时把已登录的人跳走」「服务端判断是否已登录用 `getCurrentUser()`」两处，改为「规则 6 在 proxy 里做，见 PAP-68 设计第 7 节」。
- 设计简报 §4.1、§4.2：补上两条通用错误（邮箱的其他问题显示「邮箱格式不正确」，密码的其他问题显示「密码不符合要求」），以及字段名对不上时用提示条。
- `patra-portal/README.md`：目录结构加上 `app/login/`、`app/register/`、`components/auth/`；`proxy.ts` 的说明加上「已登录的人打开登录页、注册页时跳走」。
- release spec 顶部的状态行：开 PR 时更新前端的进度。
- Linear PAP-68：To-Do 加上 Tech Design，指向本设计；其余 To-Do 和 AC 随实施和验收勾掉。
