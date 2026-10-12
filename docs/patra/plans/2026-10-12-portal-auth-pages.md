# 门户登录页、注册页与用户菜单 实施计划（PAP-68）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 门户上线登录页、注册页和导航栏账号区：在任一页面点「登录」，登录或注册成功后回到原页面，导航栏右上角变成圆形标识；在用户菜单里退出登录后仍留在原页面。已登录的人打开登录页、注册页时，由 proxy 直接送回 `next`。

**Architecture:** `next` 参数只在 `src/lib/auth/next-path.ts` 的 `safeNextPath` 一处校验。登录页、注册页是服务端组件，只算出 `next`，渲染外壳 `AuthShell` 和客户端表单。表单用 React Hook Form 加 Zod，直接 `await login(values)` / `register(values)`，成功后写入当前用户的查询缓存、弹提示、`router.replace(next)`；提交流程和失焦校验的时机由两张表单共用的 `use-auth-submit.ts`、`use-blur-validation.ts` 负责。proxy 只拦登录页、注册页的 GET：有 Cookie 时用 `lookupSessionByToken(token, 3_000)` 问网关，已登录就 307 到 `next`。导航栏的 `NavAccount`（桌面）和 `MobileAccount`（移动端全屏菜单底部）读 `useCurrentUser()` 的三种状态，退出登录共用 `useLogout()`。`next.config.ts` 关掉开发时 Server Action 参数的打印，密码不进日志。

**Tech Stack:** Next.js 16.3.8（App Router、`proxy.ts`）、React 19.3.0、TypeScript 6.0.3 strict、TanStack Query 5.103.2、React Hook Form 7.88.0 + `@hookform/resolvers` 5.9.1、Zod 4.6.5、`@base-ui/react` 1.8.0（Menu）、sonner 2.0.8、Tailwind CSS 4.3.3、Vitest 4.1.11 + Testing Library + user-event 14.6.7、Biome 2.5.14、Playwright 1.63.0。

**Spec:** `docs/patra/specs/2026-10-12-portal-auth-pages-design.md`

## Global Constraints

- `next` 只经 `safeNextPath(raw)` 校验，不合法时一律返回 `/`；`authPageHref(page, next)` 先过一遍 `safeNextPath`，结果是 `/` 时只返回 `page`。页面和 proxy 遇到多个 `next` 时都取第一个（spec 第 6 节）。
- 登录页、注册页里的站内链接（互跳、「去登录」、「返回」）都用 `replace`；左上角的品牌标识回首页，用普通的 push（spec 第 6 节）。
- proxy 只拦 `/login`、`/register` 的 GET：没有 Cookie 时不请求网关；有令牌时调用 `lookupSessionByToken(token, 3_000)`，`authenticated` 才 307 到 `safeNextPath(next)`，其余结果和读 Cookie 抛错一律放行；proxy 不写 Cookie。详情页那一支一行不改（spec 第 7 节）。
- 全仓库仍然只有 `src/lib/auth/session-cookie.ts` 调用 `cookies()`、只有 `src/lib/gateway.ts` 向网关发请求（PAP-67 的守护测试 `tests/lib/gateway-boundary.test.ts`）。Cookie 的名字只出现在 `session-cookie.ts`。
- 文案一字不差照简报 §4 和 spec 第 9.2、9.4 节，全部来自 `src/lib/auth/messages.ts`。错误写在字段下方或表单级提示条里，不用 toast；toast 只用于三条成功提示「已登录」「注册成功，已登录」「已退出登录」（spec 第 9.4、11 节）。
- 提交中：输入框 `readOnly`；按钮用 `aria-disabled="true"`，不用 `disabled`；按钮文字「登录中…」/「注册中…」，`title` 是「正在登录」/「正在注册」；再次提交一律忽略（spec 第 9.5 节）。
- 成功之后的顺序：调用 action 之前 `router.prefetch(next)`；成功后先 `cancelQueries`，再 `setQueryData(queryKeys.currentUser, user)`，再 `toast.success`，最后 `startTransition(() => router.replace(next))`（spec 第 9.6 节）。登出成功同样先 `cancelQueries` 再写 null；登出的 promise 被拒绝时 `invalidateQueries`，不提示（spec 第 10.4 节）。
- 设计系统的守护测试（`tests/design-system/`）照常要过：组件不自带焦点环，不写 `focus-visible:` 的 ring、shadow、outline；几何动效只写在 `motion-safe:` 下；不写 `rgba(`、`shadow-[`、`shadow-inner`、`text-[Npx]`、`tracking-[…]`；颜色只用主题里有的颜色类。这些测试连注释一起扫描，注释里不写像类名的词。
- 测试放在 `patra-portal/tests/` 下：纯函数和服务端模块的测试文件第一行是 `// @vitest-environment node`；组件测试用默认的 jsdom，`register`、`login`、`logout`、`next/navigation`、sonner 的 `toast` 都打桩。
- 端到端测试只有 `tests/e2e/auth.spec.ts` 一个文件。要连网关的用例在没有 `PATRA_GATEWAY_BASE_URL` 时跳过；账号流程每次用随机邮箱和运行时生成的密码，不写进仓库。跑这个文件时命令前剥掉代理变量（本机的 `next/font` 走代理会反复 `ECONNRESET`），连网关时加 `--workers=1`。每次 `goto` 之后先 `waitForTakeover` 等页面接管表单或导航栏，再操作。跑之前确认 4000 端口空着（`lsof -nP -iTCP:4000 -sTCP:LISTEN` 没有输出）：Playwright 会直接复用端口上已有的服务，那个服务如果不是本分支的代码，或者连的网关不对，结果就不可信。
- Task 17 之前，开发服务器会把登录、注册的参数（含明文密码）打印到它的输出里，这期间不读 `portal-dev` 的日志。Playwright 自己起的开发服务器不显示它的标准输出，e2e 的输出里没有这些行。
- 代码风格：注释用 `///`，内容是 Markdown；路径别名用 `@/`、`@tests/`，不写 `../../`；Biome 是唯一的格式权威，写完文件跑一次 `pnpm exec biome check --write <文件>`。计划里的代码已经是格式化之后的样子，正常应输出 `No fixes applied`。
- 命令一律写绝对路径：portal 用 `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal …`，git 用 `git -C /Users/linqibin/Projects/Products/patra …`，不用 `cd`。
- 跑全量 `pnpm test` 之前看一眼 `sysctl -n vm.loadavg`：iCloud 同步有时把负载推到 50 以上，这时 vitest 会随机超时，每次失败的用例都不一样。等负载降到 12 以下再跑，不要改超时。
- 合格线：`pnpm lint && pnpm typecheck && pnpm test`；`pnpm build` 也要通过，`useSearchParams` 漏包 Suspense 只在构建时报错（spec 第 14 节）。
- 提交：`type(scope): 中文开头的主题 (PAP-68)`，主题不以英文单词开头；正文每行不超过 100 个字符；末尾是 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。提交前看 `git diff --cached --stat`，只提交本任务的路径。不推送。

## Review Focus

spec 第 16 节点名、而且最可能让人碰上的五种情况。前四条在负责的任务里有用例，第五条只能在真实浏览器里看，放在 Task 19 的验收步骤里：

1. 奇怪的 `next`：编码过的斜杠和反斜杠、制表符和换行、出现多次。编码过的 `/%2F/evil.com` 只是本站一个不存在的路径，不会跳到站外 → Task 1 `站外或不以单个 / 开头的 %j 回到 /`（含 `/\t/evil.com`、`/\n/evil.com`）、`编码过的 %j 仍是站内路径`；Task 3、Task 9 的 `next 出现多次时取第一个`。
2. 填了一半就去点下方的链接：离开字段时冒出的错误行把链接、按钮往下挤，这次点击落空 → Task 10 的三条 e2e（鼠标按住 100 毫秒的点击、手机上的轻触）和 `按着指针离开字段时，松开之后才校验：…`。
3. 页面还没接管表单就提交（网慢、设备慢、密码管理器自动提交）：浏览器按表单自己的属性提交，邮箱和密码不能进地址栏 → Task 11 的 `页面还没接管表单就提交时，浏览器用 POST：邮箱、密码不进地址栏` 和禁用脚本的两条 e2e。
4. 提交中离开了页面（点互跳、「返回」、浏览器后退）：请求成功时照样写入、提示、跳转；失败时什么也不做 → Task 7 `提交中离开了页面（表单被卸载），请求成功后仍然写入、提示、跳转`、`提交中离开了页面，之后请求失败：什么也不做，也不报错`。
5. 浏览器自动填充和密码管理器：填充后表单拿得到值，失焦校验不误报，底色保持纸色 → Task 19 Step 6 请用户在系统 Chrome 里保存密码后再登录一次。

## 写计划时已实测的点（2026-10-12）

在 portal 的临时副本（基线和本分支 `f464c18e1` 的 `patra-portal/` 逐个文件一致，`node_modules` 链接到真实目录）里，按本计划的顺序把 Task 1 到 17 全部跑过一遍，Task 18 的文档改动在四个文档的副本上应用过，Task 19 的两个脚本也在副本上跑过；计划里的代码和预期输出都来自这些运行：

- 每个任务做完时的测试文件数、用例数和 Biome 检查的文件数：基线 95 / 810、256；Task 1 后 96 / 836、258；Task 2 后 96 / 850、258；Task 3 后 96 / 864、258；Task 4 后 98 / 905、262；Task 5 后 99 / 909、264；Task 6 后 102 / 924、270；Task 7 后 103 / 952、274；Task 8 后 104 / 969、276；Task 9 后 106 / 980、281；Task 10 后 106 / 984、283；Task 11 后 106 / 986、283；Task 12 后 106 / 990、283；Task 13 后 108 / 1003、289；Task 14 后 109 / 1011、291；Task 15、16 后 109 / 1014、291；Task 17 后 110 / 1015、292。每个任务做完都跑过 `pnpm typecheck`，全部通过。
- 计划写完后，在一个新的副本里照计划原文从 Task 1 回放到 Task 18：副本装了仓库的 pre-commit 钩子和 commitlint 配置，代码块照抄、命令照跑，每条 Expected 都和实际输出核对过；每个任务提交的文件和 `git add` 的一致，提交后的 `patra-portal/` 和上面那次逐个文件一致，Task 18 之后四个文档也一致。第一次回放时新副本冷编译、负载 30 左右，Task 10 Step 2 的三条 e2e 在修之前全部通过了：页面还没被 React 接管就点了下去，走的是浏览器原生的行为（React 19 的服务端渲染直接输出 `autofocus`，邮箱框有焦点不说明页面已经接管）。所以 e2e 和 Task 19 的两个脚本都改成打开页面后先等 React 接管再操作（`waitForTakeover`：看元素上有没有以 `__reactProps$` 开头的属性），改完从 Task 10 起冷编译再回放一遍，没有问题，Step 2 的三条按预期失败。
- Task 15 之后 `pnpm build` 通过，路由表里 `/login`、`/register` 是 `ƒ`（动态），另有 `ƒ Proxy (Middleware)`。把 TopNav 里包住账号区的 Suspense 去掉再构建，报 `useSearchParams() should be wrapped in a suspense boundary at page "/404"`，所以两处 Suspense 都不能省。
- 浏览器里的三件事只有 e2e 测得出来，组件测试测不到：
  - 离开字段时冒出的错误行会让点击落空。鼠标的按下和松开是两个任务，错误行在两者之间插入，松开时落在别的元素上；Chrome 里的手机轻触先派发 `pointerup`，再移交焦点、派发 `click`，失焦校验要排到下一个宏任务才赶得上。Playwright 默认的 `click()` 按下就松开，测不出前一种，要用 `click({ delay: 100 })`。
  - 脚本还没接管表单时提交，浏览器按表单属性提交。表单没写 `method` 时是 GET，邮箱和密码出现在地址栏；写成 `method="post"` 后地址只剩 `?next=…`。Next 对不带 action ID 的 POST 照常渲染这一页（200），表单清空。
  - `next dev` 下多个 worker 并行跑时，同时冷编译好几个路由，Fast Refresh 重建会让页内跳转退化成整页加载，账号流程会点到页面还没接管的表单。连网关跑时用 `--workers=1`，连续跑 5 次都通过。
- Task 10 的失焦校验做过变异测试：去掉「空着离开不校验」、去掉「按着时等松开」、松开后不校验、没按着时从不校验、两张表单任一处的 `mode`、`reValidateMode` 或某个字段的 `onBlur` 改回去，组件测试都会失败。只有「没按着时立即校验、不排到下一个宏任务」组件测试测不出，由手机轻触那条 e2e 兜住。「空着离开不校验」去掉后三条 e2e 仍然通过：它防的是进入页面后点一下别处就冒出「请输入邮箱」，不是点击落空。
- Task 19 的视觉对照在副本上跑过（门户连 mini 的网关；脚本加上等待接管之后，又在 Task 17 之后的代码上跑了一遍）：45 组，失败的流程 0 个，两项检查的结果和 Task 19 Step 3 的预期一致。登录页、注册页各组和高保真稿逐像素比，差异都在 0.7% 以下，来自输入框里的值；导航栏各组只差邮箱和页面正文。这次对照找出一处真偏差：`AuthShell` 两栏原来用 `flex-1`，它平分的是扣掉内边距之后的宽度，1440 宽时左栏 744px，高保真稿是 720px；Task 9 已改成 `grow basis-1/2`。截图前要等过渡结束：`reducedMotion: "reduce"` 只停掉位移，颜色和透明度的过渡照常有，不等的话会截到半透明的菜单、还没变红的描边。
- 开发模式下 `router.prefetch` 和 `<Link>` 的预取都不发请求，spec 第 15 节第 3 条只有生产构建测得出来。在副本上 `next build` 之后 `next start`，全量 e2e 20 条全部通过；`pap68-requests.mjs` 在两种模式下的输出就是 Task 19 Step 4、5 的预期（e2e 和脚本加上等待接管之后，两种模式又各跑了一遍，结果相同）。
- Next 16 开发时默认在终端打印每次 Server Action 调用的参数，登录、注册的参数里有明文密码：跑视觉对照时，开发服务器的输出里有这样的行。加了 Task 17 之后再跑，输出里一行都没有，请求行照常。
- base-ui 1.8.0 的 Menu：`GroupLabel` 不是菜单项，方向键不会停在它上面；禁用的菜单项仍会被方向键高亮，所以高亮底色要排除禁用项；`closeOnClick={false}` 时点了菜单项菜单不关。
- sonner 2.0.8 往 `<head>` 插入的样式不分层，Tailwind 的类在 `@layer utilities` 里，`sonner.tsx` 原来写在类名里的 `--normal-bg` 等变量其实没有生效，要写在行内样式上；提示上的阴影要带 `!` 才压得过。
- Tailwind 4.3 的变体顺序：`focus-within:` 生成在 `hover:` 之前，悬停加聚焦时描边会是悬停色；`has-[…]` 生成在 `hover:` 之后，彼此按方括号里的字母序排，正好是只读压过出错、出错压过聚焦。所以输入框外框的状态都写成 `has-[input:…]`。
- tailwind-merge 把 `text-display-3` 当成文字颜色，和真正的颜色类互相覆盖，`AuthShell` 里这两处类名直接写字符串，不经过 `cn()`。
- 组件测试里 `useSearchParams` 打桩时返回 `new URLSearchParams(…)` 即可，`toString()` 和 `ReadonlyURLSearchParams` 一致。

## 与 spec 的出入

第 1 条改变了 spec 和简报写明的行为，第 2、24 条是 spec 没有写到的改动，这三条评审时请拍板；其余是实现上的取舍。Task 18 把影响到文档说法的几条同步回 spec 和简报。

1. **（请拍板）失焦校验的时机**（spec 第 9.3 节、简报 §6「离开一个字段时校验它」）。RHF 的 `mode`、`reValidateMode` 都设成 `onSubmit`，离开字段时的校验交给 `useBlurValidation`，和直接失焦校验有两处不同：一是空着离开不校验，「请输入…」只在提交时出现；二是按着指针离开时，等松开再校验，没按着时（键盘离开、手机轻触）排到下一个宏任务再校验。第二处是为了不让点击落空，没有它 Task 10 的 e2e 过不了。第一处不是点击落空必需的：进入页面时焦点已经在邮箱框，不这样做的话，用户点一下页面别处、或者直接去点「注册」，就会先冒出「请输入邮箱」。如果不接受第一处，删掉 `use-blur-validation.ts` 里判断空值的三行和 Task 10 里两条「空着离开字段不提示…」的用例即可，其余不变。登录页的密码框不接 `onBlur`：登录页的密码只查非空，空着离开又不校验，离开时没有可查的。
2. **（请拍板）表单加 `method="post"`**（spec 没有写到）。写计划时用 e2e 发现：脚本还没接管表单时提交，浏览器按默认的 GET 提交，邮箱和密码会出现在地址栏、历史记录和服务器日志里。spec 第 2 节「不做：不开 JavaScript 时提交表单」不变，这时仍然登录不了，只是请求改成 POST，页面照常渲染、表单清空。
3. 输入框外框的状态用 `has-[input:focus]`、`has-[input[aria-invalid=true]]`、`has-[input[readonly]]` 加 `ring-0`，不用 spec 第 9.1 节写的 `focus-within:` 和 `has-[input:read-only]`。原因见上一节 Tailwind 的变体顺序；另外切换按钮聚焦时外框不该亮（高保真稿 Spec-Form 02）。
4. spec 第 11 节说「底色、文字、边框、圆角沿用 `sonner.tsx` 现有的 token」，前提不成立：现有写法没有生效（见上一节）。Task 12 改成行内样式，阴影用 `shadow-lg!`。
5. 自动填充的底色写在 `globals.css` 的 base 层（`input:-webkit-autofill`），只读时跟着换成凹陷色，照 `patra.css` 的写法。
6. `messages.ts` 只放提示用的文案：字段错误、提示条和成功提示。标题、副文案、按钮、链接文字是页面结构的一部分，直接写在组件里。spec 第 5 节写的是「全部文案」。
7. 新增 `src/components/auth/AuthLink.tsx`：互跳链接、「去登录」和移动端账号块里的「注册」共用的文字链接，下划线照高保真稿自左画出，「去登录」常驻下划线。spec 第 5 节的文件表里没有它。
8. 新增 `src/lib/auth/use-auth-page-href.ts`：导航栏用 `usePathname()`、`useSearchParams()` 拼 `next`。单独成文件，`next-path.ts` 就不依赖 React，proxy 也能引用。
9. 表单级提示条放在 `<form>` 里第一个位置，副文案到表单的间距随有没有提示条变化（照高保真稿：32px；有提示条时 24px，提示条到第一个字段 20px）。
10. 提示条、字段错误行的入场动效复用 `globals.css` 已有的 `.disclosure-body`，参数和高保真稿 Spec-Form 07 相同；错误行和常驻提示行的 `key` 不同，错误替换提示时节点重新插入，入场过渡才会触发。
11. 注册页 `unavailable` 时焦点留在提交按钮上，按 spec 第 9.4 节；字段名一条都对不上时同样处理。
12. 桌面账号区的品牌副标题「医学文献门户」在宽度 ≤ 880px 时隐藏；断点统一用 `max-[880px]:`，和 `TopNav` 一致，不用 `pointer-coarse:`。
13. 品牌面板的 `Venation` 叶脉保持静止，不加动效。
14. 用户菜单的菜单项在键盘聚焦时仍显示焦点环：焦点环来自全局的 `:focus-visible` 规则，组件不自带，也不去掉。高保真稿的菜单项只画了高亮底色，Task 19 Step 6 请用户用键盘打开菜单确认。
15. 移动端账号块用 `TopNav` 错峰入场的第 5 档（`[--delay:300ms]`），放在全屏菜单最底部（`mt-auto`）。
16. `BTN_BASE` 只加 `aria-disabled:cursor-not-allowed aria-disabled:opacity-55` 和 `not-aria-disabled:` 的 hover、按下限制，不给 `disabled:` 补不透明度：现有用 `disabled` 的按钮外观不变。
17. 两张表单共用的提交流程（防重复提交、预取、取消再写缓存、卸载后照样跳转）只在 `LoginForm` 的测试里逐条测，`RegisterForm` 的测试测注册特有的部分。
18. `dropdown-menu.tsx` 照 shadcn base-nova 的写法手写，只留用到的部件（Root、Trigger、Content、Group、Label、Item、Separator）；`Content` 默认 `align="start"`、`sideOffset={8}`，用户菜单传 `align="end"`。
19. `useLogout()` 由 `NavAccount` 和 `MobileAccount` 各自调用，`UserMenu` 只接收 `loggingOut`、`onLogout`。桌面退出成功后，`NavAccount` 用 effect 把焦点移到「登录」上。
20. 桌面的占位 `NavAccountPlaceholder` 是一个 `span`（`role="status"`），左边距 `ml-1.5` 和圆形标识一致。
21. 移动端「退出登录」进行中用 `aria-disabled`，点击时自己判断、忽略；桌面菜单项用 base-ui 的 `disabled`。
22. `Toaster` 的测试渲染一条真实的成功提示，断言行内样式、阴影和图标颜色；`richColors` 由源码扫描的测试挡住。
23. 端到端测试比 spec 第 14 节多两组：Task 10 的三条（错误行不让点击落空）、Task 11 的两条（禁用脚本时提交是 POST）。账号流程要用 `--workers=1` 跑（见上一节）。
24. **（请拍板）新增 Task 17：开发时不打印 Server Action 的参数**（spec 第 5 节的文件表里没有 `next.config.ts`）。spec 第 13 节写了「密码不进日志」，写计划时发现开发时不成立（见上一节）。`logging.serverFunctions: false` 只关掉这一种打印，请求行照常；生产构建本来就不打印。不接受的话删掉 Task 17，Task 18 里提到 `serverFunctions` 的几处文档改动（PAP-67 设计第 12 节，PAP-68 设计第 3、5、13、14 节）和 PAP-68 设计第 17 节补的那半句跟着删掉。

## 文件结构

| 文件 | 职责 | 任务 |
|---|---|---|
| `patra-portal/src/lib/auth/next-path.ts` | `safeNextPath`、`authPageHref` | 1 |
| `patra-portal/src/lib/auth/session-cookie.ts`（改） | 加 `readRequestSessionToken(req)` | 2 |
| `patra-portal/src/lib/auth/current-user.ts`（改） | 拆出 `lookupSessionByToken(token, timeoutMs?)`，删掉 `getCurrentUser()` | 2 |
| `patra-portal/src/proxy.ts`（改） | 规则 6：已登录的人打开登录页、注册页时 307 到 `next` | 3 |
| `patra-portal/src/lib/auth/messages.ts` | 字段错误、提示条、成功提示的文案，原因码到文案的映射 | 4 建，7 补 |
| `patra-portal/src/lib/auth/form-schemas.ts` | 登录、注册表单的 Zod schema | 4 |
| `patra-portal/src/lib/auth/logout-mutation.ts` | `useLogout()` | 5 |
| `patra-portal/src/lib/portal-ui.ts`（改） | 按钮样式支持 `aria-disabled` | 6 |
| `patra-portal/src/app/globals.css`（改） | 自动填充后的输入框底色 | 6 |
| `patra-portal/src/components/auth/FormField.tsx`、`PasswordInput.tsx`、`FormAlert.tsx` | 表单字段、密码框、提示条 | 6 |
| `patra-portal/src/components/auth/AuthLink.tsx` | 登录、注册相关的文字链接 | 7 建，14 补 |
| `patra-portal/src/components/auth/use-auth-submit.ts` | 两张表单共用的提交流程、后端字段错误 | 7 |
| `patra-portal/src/components/auth/LoginForm.tsx` | 登录表单 | 7 建，10、11 改 |
| `patra-portal/src/components/auth/RegisterForm.tsx` | 注册表单 | 8 建，10、11 改 |
| `patra-portal/src/components/auth/AuthShell.tsx` | 专注布局的外壳 | 9 |
| `patra-portal/src/app/login/page.tsx`、`src/app/register/page.tsx` | 两个页面 | 9 |
| `patra-portal/src/components/auth/use-blur-validation.ts` | 离开字段时的校验时机 | 10 |
| `patra-portal/tests/e2e/auth.spec.ts` | 端到端测试 | 10 建，11、16 补 |
| `patra-portal/src/components/ui/sonner.tsx`、`src/app/layout.tsx`（改） | 成功提示的外观，去掉 `richColors` | 12 |
| `patra-portal/src/components/ui/dropdown-menu.tsx` | 菜单原语 | 13 |
| `patra-portal/src/lib/auth/use-auth-page-href.ts` | 导航栏里带 `next` 的登录页、注册页链接 | 13 |
| `patra-portal/src/components/portal/UserMenu.tsx`、`NavAccount.tsx` | 用户菜单、桌面账号区 | 13 |
| `patra-portal/src/components/portal/MobileAccount.tsx` | 移动端全屏菜单底部的账号块 | 14 |
| `patra-portal/src/components/portal/TopNav.tsx`（改） | 放进账号区和账号块，各包一层 Suspense | 15 |
| `patra-portal/next.config.ts`（改） | 开发时不打印 Server Action 的参数 | 17 |
| `patra-portal/tests/` 下对应的测试文件 | 见各任务 | 1 ~ 15、17 |
| `docs/patra/specs/2026-10-10-portal-auth-design.md`、`patra-portal/docs/design-briefs/v0.8/accounts.md`、`patra-portal/README.md`、`docs/patra/specs/2026-10-12-portal-auth-pages-design.md` | 文档同步 | 18 |
| `docs/patra/specs/2026-10-12-portal-auth-pages-design.md` 第 15 节、Linear PAP-68 和 PAP-67 | 实测结果、Issue | 19 |

## 执行顺序

按 Task 1 到 19 的顺序做。Task 3 用到 Task 1、2；Task 7 用到 Task 1、4、6；Task 8 用到 Task 7 的 `AuthLink`、`use-auth-submit.ts`；Task 9 用到 Task 7、8；Task 10、11 改的是 Task 7、8 的两张表单，e2e 要 Task 9 的页面；Task 13 用到 Task 1、5；Task 14 用到 Task 7 的 `AuthLink` 和 Task 13 的 `use-auth-page-href.ts`；Task 15 用到 Task 13、14；Task 16 要前面全部做完；Task 17 不依赖别的任务，但要在 Task 19 之前做完（Task 19 要读开发服务器的日志）；Task 18 描述做完之后的代码；Task 19 要在本机把门户跑起来。

---

### Task 1: `next` 参数的校验

**Files:**
- Create: `patra-portal/src/lib/auth/next-path.ts`
- Test: `patra-portal/tests/lib/auth/next-path.test.ts`

**Interfaces:**
- Consumes: 无。
- Produces: `safeNextPath(raw: string | null | undefined): string`；`authPageHref(page: "/login" | "/register", next: string): string`。纯函数，不带 `server-only`：Task 3 的 proxy、Task 7 和 8 的表单、Task 9 的页面、Task 13 的 `useAuthPageHref` 都引用它。

- [ ] **Step 1: 写失败的测试**

新建 `patra-portal/tests/lib/auth/next-path.test.ts`：

```ts
// @vitest-environment node
import { describe, expect, it } from "vitest";
import { authPageHref, safeNextPath } from "@/lib/auth/next-path";

describe("safeNextPath", () => {
  it.each([
    ["/papers?q=GLP-1", "/papers?q=GLP-1"],
    ["/journals/7", "/journals/7"],
    ["/", "/"],
    ["/papers?q=GLP-1#results", "/papers?q=GLP-1"],
  ])("站内地址 %j 返回 %j，# 之后的部分去掉", (raw, expected) => {
    expect(safeNextPath(raw)).toBe(expected);
  });

  it("返回解析后的路径：点号段已经合并", () => {
    expect(safeNextPath("/papers/../journals?page=2")).toBe("/journals?page=2");
  });

  it.each([
    "//evil.com",
    "/\\evil.com",
    "/\t/evil.com",
    "/\n/evil.com",
    "https://evil.com",
    "javascript:alert(1)",
    "papers",
  ])("站外或不以单个 / 开头的 %j 回到 /", (raw) => {
    expect(safeNextPath(raw)).toBe("/");
  });

  it.each(["/login", "/login/", "/register?x=1", "/papers/../login", "/register//"])(
    "登录页、注册页本身 %j 回到 /",
    (raw) => {
      expect(safeNextPath(raw)).toBe("/");
    },
  );

  it.each(["", null, undefined])("%j 回到 /", (raw) => {
    expect(safeNextPath(raw)).toBe("/");
  });

  // 编码过的斜杠和反斜杠解析后仍是本站的一个路径（打开是 404 页），不会变成站外地址
  it.each(["/%2F/evil.com", "/%5Cevil.com"])("编码过的 %j 仍是站内路径", (raw) => {
    expect(safeNextPath(raw)).toBe(raw);
  });
});

describe("authPageHref", () => {
  it("next 是站内地址时编码后放进查询串", () => {
    expect(authPageHref("/login", "/papers?q=GLP-1")).toBe("/login?next=%2Fpapers%3Fq%3DGLP-1");
    expect(authPageHref("/register", "/journals/7")).toBe("/register?next=%2Fjournals%2F7");
  });

  it.each(["/", "//evil.com", "/login"])("next 校验后是 / 时（%j）不带参数", (next) => {
    expect(authPageHref("/login", next)).toBe("/login");
    expect(authPageHref("/register", next)).toBe("/register");
  });
});
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/lib/auth/next-path.test.ts`
Expected: FAIL，`Error: Cannot find package '@/lib/auth/next-path' imported from …/tests/lib/auth/next-path.test.ts`，`Tests  no tests`。

- [ ] **Step 3: 写实现**

新建 `patra-portal/src/lib/auth/next-path.ts`：

```ts
/// 解析 `next` 用的占位地址：只用来判断解析后是否还在本站，不会出现在结果里
const PLACEHOLDER_ORIGIN = "http://patra.invalid";

/// 登录页、注册页本身不能作为 `next`（跳转规则 5）
const AUTH_PAGES = new Set(["/login", "/register"]);

/// 校验 `next` 参数：合法时返回解析后的「路径 + 查询串」，`#` 及之后的部分丢掉；不合法时一律返回 `/`。
/// 1. 必须是非空字符串。
/// 2. 第一个字符是 `/`，第二个字符不能是 `/` 或 `\`（跳转规则 4）。
/// 3. 按 URL 解析后仍是本站。解析会去掉制表符和换行、把 `\` 当成 `/`，`/\t/evil.com` 在这一步变成
///    `//evil.com`，被挡住。
/// 4. 解析后的路径去掉末尾的 `/`，不能是登录页或注册页。`/papers/../login` 解析后就是 `/login`。
export function safeNextPath(raw: string | null | undefined): string {
  if (raw?.[0] !== "/" || raw[1] === "/" || raw[1] === "\\") {
    return "/";
  }
  let url: URL;
  try {
    url = new URL(raw, PLACEHOLDER_ORIGIN);
  } catch {
    return "/";
  }
  if (url.origin !== PLACEHOLDER_ORIGIN || AUTH_PAGES.has(url.pathname.replace(/\/+$/, ""))) {
    return "/";
  }
  return `${url.pathname}${url.search}`;
}

/// 登录页、注册页的链接：`next` 先经过 `safeNextPath`，结果是 `/` 时不带参数。
export function authPageHref(page: "/login" | "/register", next: string): string {
  const target = safeNextPath(next);
  return target === "/" ? page : `${page}?${new URLSearchParams({ next: target })}`;
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: 同 Step 2。
Expected: PASS，`Tests  26 passed (26)`。

- [ ] **Step 5: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write src/lib/auth/next-path.ts tests/lib/auth/next-path.test.ts
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0，`lint` 输出 `Checked 258 files`；`Test Files  96 passed (96)`、`Tests  836 passed (836)`。

- [ ] **Step 6: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/src/lib/auth/next-path.ts patra-portal/tests/lib/auth/next-path.test.ts
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
feat(portal): 校验登录页、注册页的 next 参数 (PAP-68)

safeNextPath 只接受站内地址：第二个字符不能是斜杠或反斜杠，按 URL 解析后仍是本站，路径也不是
登录页、注册页本身，不合法时一律回首页。authPageHref 拼出带 next 的登录页、注册页链接。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这两个文件；pre-commit 的各项检查（gitleaks、commitlint 等）都是 Passed 或 Skipped。

---

### Task 2: 从请求读会话令牌，按令牌查会话

**Files:**
- Modify: `patra-portal/src/lib/auth/session-cookie.ts`（加 `readRequestSessionToken`）
- Modify: `patra-portal/src/lib/auth/current-user.ts`（拆出 `lookupSessionByToken`，删掉 `getCurrentUser`）
- Test: `patra-portal/tests/lib/auth/session-cookie.test.ts`、`patra-portal/tests/lib/auth/current-user.test.ts`

**Interfaces:**
- Consumes: PAP-67 `session-cookie.ts` 内部的 `sessionCookie()`（按 `Secure` 开关给出 Cookie 的名字和属性，开关写错时抛错）；`current-user.ts` 已有的 `SessionLookup`、`canSendToken(token)`、`gatewayFetch(path, { token, timeoutMs })`、`logRequestError(endpoint, error)`。
- Produces: `readRequestSessionToken(req: NextRequest): string | null`；`lookupSessionByToken(token: string | null, timeoutMs?: number): Promise<SessionLookup>`，不读也不写 Cookie，不抛异常。`lookupSession()` 的签名和行为不变，`/api/auth/me` 不用改。`getCurrentUser()` 删掉，它没有调用方。Task 3 的 proxy 用前两个函数。

- [ ] **Step 1: 写失败的测试：从请求读令牌**

`patra-portal/tests/lib/auth/session-cookie.test.ts` 的改动如下（diff 里的路径相对仓库根目录，存成文件后用 `git -C /Users/linqibin/Projects/Products/patra apply <文件>` 应用，下同）：

```diff
diff --git a/patra-portal/tests/lib/auth/session-cookie.test.ts b/patra-portal/tests/lib/auth/session-cookie.test.ts
--- a/patra-portal/tests/lib/auth/session-cookie.test.ts
+++ b/patra-portal/tests/lib/auth/session-cookie.test.ts
@@ -1,8 +1,10 @@
 // @vitest-environment node
 import { resetCookieJar } from "@tests/stubs/cookie-jar";
+import { NextRequest } from "next/server";
 import { afterEach, describe, expect, it, vi } from "vitest";
 import {
   clearSessionCookie,
+  readRequestSessionToken,
   readSessionToken,
   refreshSessionCookie,
   writeSessionCookie,
@@ -80,6 +82,37 @@ describe("readSessionToken", () => {
   });
 });

+describe("readRequestSessionToken", () => {
+  /// proxy 收到的请求，`cookie` 是请求头 Cookie 的原文
+  const request = (cookie: string) =>
+    new NextRequest("http://portal.test/login", { headers: { cookie } });
+
+  it("Secure 关时读 patra_session", () => {
+    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
+    const req = request(`patra_session=${TOKEN}; __Host-patra_session=other`);
+    expect(readRequestSessionToken(req)).toBe(TOKEN);
+  });
+
+  it("Secure 开时读 __Host-patra_session", () => {
+    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", undefined);
+    const req = request(`patra_session=other; __Host-patra_session=${TOKEN}`);
+    expect(readRequestSessionToken(req)).toBe(TOKEN);
+  });
+
+  it("没有 Cookie 或值为空时返回 null", () => {
+    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
+    expect(readRequestSessionToken(request(""))).toBeNull();
+    expect(readRequestSessionToken(request("patra_session="))).toBeNull();
+  });
+
+  it("开关写错时报错", () => {
+    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "yes");
+    expect(() => readRequestSessionToken(request(`patra_session=${TOKEN}`))).toThrow(
+      "PATRA_SESSION_COOKIE_SECURE 只能是 true 或 false",
+    );
+  });
+});
+
 describe("refreshSessionCookie", () => {
   it("用原值重写一次，有效期回到 30 天", async () => {
     vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/lib/auth/session-cookie.test.ts`
Expected: FAIL，`Tests  4 failed | 15 passed (19)`，失败原因是 `TypeError: readRequestSessionToken is not a function`（「开关写错时报错」那条报的是抛出的错误不对，原因相同）。

- [ ] **Step 3: 写实现**

```diff
diff --git a/patra-portal/src/lib/auth/session-cookie.ts b/patra-portal/src/lib/auth/session-cookie.ts
--- a/patra-portal/src/lib/auth/session-cookie.ts
+++ b/patra-portal/src/lib/auth/session-cookie.ts
@@ -1,5 +1,6 @@
 import "server-only";
 import { cookies } from "next/headers";
+import type { NextRequest } from "next/server";

 /// 有效期 30 天，和会话的不活跃过期时间一致
 const MAX_AGE_SECONDS = 30 * 24 * 60 * 60;
@@ -37,6 +38,12 @@ export async function readSessionToken(): Promise<string | null> {
   return value ? value : null;
 }

+/// proxy 里用：从请求对象读会话令牌，规则和 `readSessionToken` 相同。
+export function readRequestSessionToken(req: NextRequest): string | null {
+  const value = req.cookies.get(sessionCookie().name)?.value;
+  return value ? value : null;
+}
+
 /// 注册、登录成功时写入令牌，有效期 30 天。
 export async function writeSessionCookie(token: string): Promise<void> {
   (await cookies()).set({ ...sessionCookie(), value: token, maxAge: MAX_AGE_SECONDS });
```

- [ ] **Step 4: 跑测试，确认通过**

Run: 同 Step 2。
Expected: PASS，`Tests  19 passed (19)`。

- [ ] **Step 5: 写失败的测试：按令牌查会话**

`getCurrentUser` 的三条用例换成 `lookupSessionByToken` 的用例，`lookupSession` 原有的用例一行不改：

```diff
diff --git a/patra-portal/tests/lib/auth/current-user.test.ts b/patra-portal/tests/lib/auth/current-user.test.ts
--- a/patra-portal/tests/lib/auth/current-user.test.ts
+++ b/patra-portal/tests/lib/auth/current-user.test.ts
@@ -1,7 +1,7 @@
 // @vitest-environment node
 import { resetCookieJar } from "@tests/stubs/cookie-jar";
 import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
-import { getCurrentUser, lookupSession } from "@/lib/auth/current-user";
+import { lookupSession, lookupSessionByToken } from "@/lib/auth/current-user";

 vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

@@ -112,24 +112,61 @@ describe("lookupSession", () => {
   });
 });

-describe("getCurrentUser", () => {
-  it("已登录时返回用户", async () => {
-    fetchMock.mockResolvedValue(json(200, USER));
-    await expect(getCurrentUser()).resolves.toEqual(USER);
+describe("lookupSessionByToken", () => {
+  it.each([null, ""])("没有令牌（%j）时是 anonymous，不请求网关", async (token) => {
+    await expect(lookupSessionByToken(token)).resolves.toEqual({ status: "anonymous" });
+    expect(fetchMock).not.toHaveBeenCalled();
   });

-  it("401 时返回 null，不清 Cookie", async () => {
-    fetchMock.mockResolvedValue(problem(401, "GW-0401"));
-    const jar = resetCookieJar({ patra_session: TOKEN });
-    await expect(getCurrentUser()).resolves.toBeNull();
-    expect(jar.written).toEqual([]);
+  it("带着传入的令牌请求网关的 /auth/me，不读 Cookie", async () => {
+    // 读 Cookie 会因为开关写错而抛错；不读 Cookie 才能照常拿到用户
+    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "yes");
+    fetchMock.mockResolvedValue(json(200, { ...USER, accountType: "user" }));
+    await expect(lookupSessionByToken("patra_user_other")).resolves.toEqual({
+      status: "authenticated",
+      user: USER,
+    });
+    const [url, init] = fetchMock.mock.calls[0] ?? [];
+    expect(url).toBe("http://gw.test:9528/patra-identity/auth/me");
+    expect(init?.headers).toEqual({ Authorization: "Bearer patra_user_other" });
   });

-  it("没有 Cookie 或网关不可用时返回 null", async () => {
-    resetCookieJar();
-    await expect(getCurrentUser()).resolves.toBeNull();
-    resetCookieJar({ patra_session: TOKEN });
-    fetchMock.mockResolvedValue(problem(503, "GW-0503"));
-    await expect(getCurrentUser()).resolves.toBeNull();
+  it.each(["GW-0401", "IDN-0401"])("401 %s 都是 rejected", async (code) => {
+    fetchMock.mockResolvedValue(problem(401, code));
+    await expect(lookupSessionByToken(TOKEN)).resolves.toEqual({ status: "rejected" });
+  });
+
+  it.each([`${TOKEN}\nX`, `${TOKEN}中`])(
+    "令牌放不进请求头（%j）时是 rejected，不请求网关，也不记日志",
+    async (token) => {
+      await expect(lookupSessionByToken(token)).resolves.toEqual({ status: "rejected" });
+      expect(fetchMock).not.toHaveBeenCalled();
+      expect(console.error).not.toHaveBeenCalled();
+    },
+  );
+
+  it.each([
+    ["503 GW-0503", () => problem(503, "GW-0503")],
+    ["200 但缺 email", () => json(200, { userId: USER.userId })],
+    ["200 但响应体不是 JSON", () => new Response("<html></html>", { status: 200 })],
+  ])("%s 时是 unavailable", async (_label, response) => {
+    fetchMock.mockResolvedValue(response());
+    await expect(lookupSessionByToken(TOKEN)).resolves.toEqual({ status: "unavailable" });
+  });
+
+  it.each([
+    ["超时", new DOMException("The operation was aborted due to timeout", "TimeoutError")],
+    ["网络错误", new TypeError("fetch failed")],
+  ])("%s时是 unavailable", async (_label, error) => {
+    fetchMock.mockRejectedValue(error);
+    await expect(lookupSessionByToken(TOKEN)).resolves.toEqual({ status: "unavailable" });
+  });
+
+  it("超时按传入的毫秒数，不传时是 10 秒", async () => {
+    const timeout = vi.spyOn(AbortSignal, "timeout");
+    fetchMock.mockImplementation(async () => json(200, USER));
+    await lookupSessionByToken(TOKEN, 3_000);
+    await lookupSessionByToken(TOKEN);
+    expect(timeout.mock.calls).toEqual([[3_000], [10_000]]);
   });
 });
```

- [ ] **Step 6: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/lib/auth/current-user.test.ts`
Expected: FAIL，`Tests  13 failed | 17 passed (30)`，失败原因都是 `TypeError: lookupSessionByToken is not a function`。

- [ ] **Step 7: 写实现**

判断规则从 `lookupSession()` 原样搬进 `lookupSessionByToken()`，只是令牌改由参数传入、超时可以指定；`lookupSession()` 读出令牌后调用它，读 Cookie 抛错时仍是 `unavailable` 并记日志：

```diff
diff --git a/patra-portal/src/lib/auth/current-user.ts b/patra-portal/src/lib/auth/current-user.ts
--- a/patra-portal/src/lib/auth/current-user.ts
+++ b/patra-portal/src/lib/auth/current-user.ts
@@ -17,22 +17,26 @@ export type SessionLookup =
 /// `/auth/me` 的 200 响应里门户要用的字段，`accountType` 不读
 const currentUserSchema = z.object({ userId: z.string().min(1), email: z.string().min(1) });

-/// 带着会话令牌问网关当前用户是谁。不抛异常，也不写 Cookie。
-/// - 没有 Cookie：`anonymous`，不请求网关
-/// - Cookie 值放不进请求头（被改坏了）：`rejected`，不请求网关
+/// 带着会话令牌问网关当前用户是谁。不读也不写 Cookie，不抛异常。
+/// - 没有令牌：`anonymous`，不请求网关
+/// - 令牌放不进请求头（Cookie 被改坏了）：`rejected`，不请求网关
 /// - 200 且字段齐全：`authenticated`
 /// - 401（`GW-0401` 或 `IDN-0401`）：`rejected`
-/// - 其他状态码、请求抛错、响应体格式不对、读 Cookie 抛错：`unavailable`
-export async function lookupSession(): Promise<SessionLookup> {
+/// - 其他状态码、请求抛错、响应体格式不对：`unavailable`
+///
+/// `timeoutMs` 不传时用 `gatewayFetch` 默认的 10 秒。proxy 传 3 秒。
+export async function lookupSessionByToken(
+  token: string | null,
+  timeoutMs?: number,
+): Promise<SessionLookup> {
+  if (!token) {
+    return { status: "anonymous" };
+  }
+  if (!canSendToken(token)) {
+    return { status: "rejected" };
+  }
   try {
-    const token = await readSessionToken();
-    if (!token) {
-      return { status: "anonymous" };
-    }
-    if (!canSendToken(token)) {
-      return { status: "rejected" };
-    }
-    const res = await gatewayFetch(ME_ENDPOINT, { token });
+    const res = await gatewayFetch(ME_ENDPOINT, { token, timeoutMs });
     if (res.status === 401) {
       return { status: "rejected" };
     }
@@ -49,9 +53,14 @@ export async function lookupSession(): Promise<SessionLookup> {
   }
 }

-/// 服务端渲染的页面判断是否已登录时用（比如登录页「已登录就跳走」）。渲染时不能写 Cookie，
-/// 所以收到 401 也只返回 null，过期的 Cookie 留给下一次 `/api/auth/me` 清除。
-export async function getCurrentUser(): Promise<CurrentUser | null> {
-  const session = await lookupSession();
-  return session.status === "authenticated" ? session.user : null;
+/// 读出 Cookie 里的令牌，再按 `lookupSessionByToken` 的规则判断。读 Cookie 抛错时是 `unavailable`。
+export async function lookupSession(): Promise<SessionLookup> {
+  let token: string | null;
+  try {
+    token = await readSessionToken();
+  } catch (error) {
+    logRequestError(ME_ENDPOINT, error);
+    return { status: "unavailable" };
+  }
+  return lookupSessionByToken(token);
 }
```

- [ ] **Step 8: 跑测试，确认通过**

Run: 同 Step 6。
Expected: PASS，`Tests  30 passed (30)`。

- [ ] **Step 9: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write src/lib/auth/session-cookie.ts src/lib/auth/current-user.ts tests/lib/auth/session-cookie.test.ts tests/lib/auth/current-user.test.ts
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0，`lint` 输出 `Checked 258 files`；`Test Files  96 passed (96)`、`Tests  850 passed (850)`。`typecheck` 通过也说明仓库里没有别处引用 `getCurrentUser`。

- [ ] **Step 10: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/src/lib/auth/session-cookie.ts patra-portal/src/lib/auth/current-user.ts patra-portal/tests/lib/auth/session-cookie.test.ts patra-portal/tests/lib/auth/current-user.test.ts
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
feat(portal): 从请求读会话令牌、按令牌查会话，删掉 getCurrentUser (PAP-68)

proxy 拿不到 cookies()，只有请求对象：readRequestSessionToken 从请求里读令牌，规则和
readSessionToken 相同；lookupSessionByToken 按传入的令牌问网关，可以指定超时，判断规则从
lookupSession 原样搬过来。getCurrentUser 本来给页面渲染时判断登录状态用，规则 6 改到 proxy
之后没有调用方，连同测试删掉。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这 4 个文件；pre-commit 全部通过。

---

### Task 3: 已登录的人打开登录页、注册页时由 proxy 送回 `next`

**Files:**
- Modify: `patra-portal/src/proxy.ts`
- Test: `patra-portal/tests/proxy.test.ts`（只加用例，详情页原有的用例一行不改）

**Interfaces:**
- Consumes: Task 1 `safeNextPath`；Task 2 `readRequestSessionToken`、`lookupSessionByToken`。
- Produces: `config.matcher` 是 `["/papers/:id", "/journals/:id", "/login", "/register"]`；已登录的人 GET 登录页、注册页时回 307，`Location` 是 `next`（不合法时是 `/`）。Task 9 的页面因此不读 Cookie、也不判断登录状态。

- [ ] **Step 1: 写失败的测试**

```diff
diff --git a/patra-portal/tests/proxy.test.ts b/patra-portal/tests/proxy.test.ts
--- a/patra-portal/tests/proxy.test.ts
+++ b/patra-portal/tests/proxy.test.ts
@@ -1,7 +1,7 @@
 // @vitest-environment node
 import { NextRequest } from "next/server";
 import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
-import { proxy } from "@/proxy";
+import { config, proxy } from "@/proxy";

 const fetchMock = vi.fn<typeof fetch>();

@@ -73,3 +73,107 @@ describe("详情页直接打开时先确认存在", () => {
     expect(fetchMock).not.toHaveBeenCalled();
   });
 });
+
+describe("已登录的人打开登录页、注册页时直接跳走", () => {
+  const TOKEN = "patra_user_abc";
+  const USER = { userId: "319041872872550658", email: "reader@example.com" };
+
+  beforeEach(() => {
+    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
+    vi.spyOn(console, "error").mockImplementation(() => {});
+  });
+
+  afterEach(() => {
+    vi.restoreAllMocks();
+  });
+
+  /// 带着会话 Cookie 的请求，默认是地址栏直接打开
+  const signedIn = (path: string, headers: Record<string, string> = {}) =>
+    request(path, { "sec-fetch-dest": "document", cookie: `patra_session=${TOKEN}`, ...headers });
+  const json = (status: number, body: unknown) =>
+    new Response(JSON.stringify(body), {
+      status,
+      headers: { "Content-Type": "application/json" },
+    });
+  const problem = (status: number, code: string) =>
+    new Response(JSON.stringify({ status, code, title: "x" }), {
+      status,
+      headers: { "Content-Type": "application/problem+json" },
+    });
+
+  it("matcher 包含登录页和注册页，详情页的两项不变", () => {
+    expect(config.matcher).toEqual(["/papers/:id", "/journals/:id", "/login", "/register"]);
+  });
+
+  it.each(["/login", "/register"])("已登录时 GET %s 回 307，跳到 next", async (page) => {
+    fetchMock.mockResolvedValue(json(200, USER));
+    const res = await proxy(signedIn(`${page}?next=%2Fpapers%3Fq%3DGLP-1`));
+    expect(res.status).toBe(307);
+    expect(res.headers.get("location")).toBe("http://portal.test/papers?q=GLP-1");
+    const [url, init] = fetchMock.mock.calls[0] ?? [];
+    expect(url).toBe("http://gw.test/patra-identity/auth/me");
+    expect(init?.headers).toEqual({ Authorization: `Bearer ${TOKEN}` });
+  });
+
+  it.each([
+    ["没有 next", "/login"],
+    ["next 是站外地址", "/login?next=%2F%2Fevil.com"],
+    ["next 是登录页自己", "/register?next=%2Flogin"],
+  ])("%s，跳到首页", async (_label, path) => {
+    fetchMock.mockResolvedValue(json(200, USER));
+    const res = await proxy(signedIn(path));
+    expect(res.headers.get("location")).toBe("http://portal.test/");
+  });
+
+  it("next 出现多次时取第一个", async () => {
+    fetchMock.mockResolvedValue(json(200, USER));
+    const res = await proxy(signedIn("/login?next=%2Fjournals&next=%2Fpapers"));
+    expect(res.headers.get("location")).toBe("http://portal.test/journals");
+  });
+
+  it("站内点链接、预取（sec-fetch-dest: empty）同样会跳走", async () => {
+    fetchMock.mockResolvedValue(json(200, USER));
+    const res = await proxy(signedIn("/login?next=%2Fjournals", { "sec-fetch-dest": "empty" }));
+    expect(res.headers.get("location")).toBe("http://portal.test/journals");
+  });
+
+  it("没有 Cookie 时放行，不请求网关", async () => {
+    const res = await proxy(request("/login?next=%2Fjournals"));
+    expect(res.headers.get("x-middleware-next")).toBe("1");
+    expect(fetchMock).not.toHaveBeenCalled();
+  });
+
+  it.each([
+    ["令牌失效", () => problem(401, "GW-0401")],
+    ["网关出错", () => problem(503, "GW-0503")],
+  ])("%s时放行，显示表单", async (_label, response) => {
+    fetchMock.mockResolvedValue(response());
+    const res = await proxy(signedIn("/login"));
+    expect(res.headers.get("x-middleware-next")).toBe("1");
+  });
+
+  it("网关超时放行；问网关的超时是 3 秒", async () => {
+    const timeout = vi.spyOn(AbortSignal, "timeout");
+    fetchMock.mockRejectedValue(new DOMException("timed out", "TimeoutError"));
+    const res = await proxy(signedIn("/register"));
+    expect(res.headers.get("x-middleware-next")).toBe("1");
+    expect(timeout).toHaveBeenCalledWith(3_000);
+  });
+
+  it("POST（Server Action 发到当前页）直接放行，不请求网关", async () => {
+    const req = new NextRequest("http://portal.test/login", {
+      method: "POST",
+      headers: { cookie: `patra_session=${TOKEN}` },
+    });
+    const res = await proxy(req);
+    expect(res.headers.get("x-middleware-next")).toBe("1");
+    expect(fetchMock).not.toHaveBeenCalled();
+  });
+
+  it("Secure 开关写错、读不了 Cookie 时放行，不请求网关", async () => {
+    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "yes");
+    const res = await proxy(signedIn("/login"));
+    expect(res.headers.get("x-middleware-next")).toBe("1");
+    expect(fetchMock).not.toHaveBeenCalled();
+  });
+});
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/proxy.test.ts`
Expected: FAIL，`Tests  14 failed | 10 passed (24)`。matcher 那条是 `expected [ '/papers/:id', '/journals/:id' ] to deeply equal [ Array(4) ]`；其余大多是 `TypeError: Cannot destructure property 'kind' of 'DETAIL[section]' as it is undefined.`：现在的 proxy 把 `/login` 当成详情页处理。详情页原有的 10 条照常通过。

- [ ] **Step 3: 写实现**

```diff
diff --git a/patra-portal/src/proxy.ts b/patra-portal/src/proxy.ts
--- a/patra-portal/src/proxy.ts
+++ b/patra-portal/src/proxy.ts
@@ -1,4 +1,7 @@
 import { type NextRequest, NextResponse } from "next/server";
+import { lookupSessionByToken } from "@/lib/auth/current-user";
+import { safeNextPath } from "@/lib/auth/next-path";
+import { readRequestSessionToken } from "@/lib/auth/session-cookie";
 import { gatewayFetch } from "@/lib/gateway";

 /// 详情页 404 状态码。详情页有 loading.tsx，服务器先回 200 把骨架流出去，等页面发现文献不存在时
@@ -11,7 +14,36 @@ const DETAIL = {

 const CHECK_TIMEOUT_MS = 3_000;

+const AUTH_PAGES = ["/login", "/register"];
+
+/// 跳转规则 6：已登录的人打开登录页、注册页时，直接 307 到 `next`。只拦 GET：Server Action 是发到
+/// 当前页的 POST，放行。站内点链接、预取也要拦，所以不看 `sec-fetch-dest`。没有 Cookie 时不请求网关；
+/// 读 Cookie 抛错、令牌失效、网关出错或超时都放行，显示表单。这里不写 Cookie。
+async function leaveAuthPage(req: NextRequest): Promise<NextResponse> {
+  if (req.method !== "GET") {
+    return NextResponse.next();
+  }
+  let token: string | null;
+  try {
+    token = readRequestSessionToken(req);
+  } catch {
+    return NextResponse.next();
+  }
+  if (!token) {
+    return NextResponse.next();
+  }
+  const session = await lookupSessionByToken(token, CHECK_TIMEOUT_MS);
+  if (session.status !== "authenticated") {
+    return NextResponse.next();
+  }
+  const next = safeNextPath(req.nextUrl.searchParams.get("next"));
+  return NextResponse.redirect(new URL(next, req.url), 307);
+}
+
 export async function proxy(req: NextRequest) {
+  if (AUTH_PAGES.includes(req.nextUrl.pathname)) {
+    return leaveAuthPage(req);
+  }
   // 站内跳转与预取是页面里发起的 fetch，本来就不看 HTTP 状态码，直接放行。
   // 认它们靠浏览器自带的 Sec-Fetch-Dest（fetch 是 empty，地址栏打开是 document）：
   // Next 交给这里之前会剥掉 rsc 等内部请求头，在这里读不到。
@@ -40,5 +72,5 @@ export async function proxy(req: NextRequest) {
 }

 export const config = {
-  matcher: ["/papers/:id", "/journals/:id"],
+  matcher: ["/papers/:id", "/journals/:id", "/login", "/register"],
 };
```

- [ ] **Step 4: 跑测试，确认通过**

Run: 同 Step 2。
Expected: PASS，`Tests  24 passed (24)`。

- [ ] **Step 5: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write src/proxy.ts tests/proxy.test.ts
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0，`lint` 输出 `Checked 258 files`；`Test Files  96 passed (96)`、`Tests  864 passed (864)`。

- [ ] **Step 6: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/src/proxy.ts patra-portal/tests/proxy.test.ts
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
feat(portal): 已登录的人打开登录页、注册页时由 proxy 送回 next (PAP-68)

跳转规则 6 放在页面渲染之前：只拦 GET，站内点链接和预取也拦；没有 Cookie 不请求网关，有令牌时
按 3 秒超时问网关，已登录才 307 到 next，令牌失效、网关出错或超时都放行，显示表单。proxy 不写
Cookie，详情页那一支不变。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这两个文件；pre-commit 全部通过。

---

### Task 4: 表单的文案和校验规则

**Files:**
- Create: `patra-portal/src/lib/auth/messages.ts`、`patra-portal/src/lib/auth/form-schemas.ts`
- Test: `patra-portal/tests/lib/auth/messages.test.ts`、`patra-portal/tests/lib/auth/form-schemas.test.ts`

**Interfaces:**
- Consumes: PAP-67 `LoginResult`（`src/lib/auth/types.ts`）。
- Produces:
  - `messages.ts`：`FIELD_MESSAGES`（`emailRequired`、`emailInvalid`、`passwordRequired`、`passwordTooShort`、`passwordTooLong`、`passwordTooCommon`、`passwordInvalid`、`confirmRequired`、`confirmMismatch`、`emailTaken`、`emailTakenLink`）、`TOAST_MESSAGES`（`loggedIn`、`registered`、`loggedOut`）、`REGISTER_UNAVAILABLE_MESSAGE`、`fieldErrorMessage(field: "email" | "password", code: string | null): string`、`type LoginAlertFailure`、`loginAlertMessage(failure: LoginAlertFailure): string`。Task 7 再加 `fieldErrorMessages`。
  - `form-schemas.ts`：`loginSchema`、`registerSchema`、`type LoginFormValues`、`type RegisterFormValues`。
  - 两个文件都不带 `server-only`，Task 5、7、8 的客户端代码引用。

- [ ] **Step 1: 写失败的测试**

新建 `patra-portal/tests/lib/auth/messages.test.ts`：

```ts
// @vitest-environment node
import { describe, expect, it } from "vitest";
import {
  FIELD_MESSAGES,
  fieldErrorMessage,
  loginAlertMessage,
  REGISTER_UNAVAILABLE_MESSAGE,
  TOAST_MESSAGES,
} from "@/lib/auth/messages";

describe("fieldErrorMessage", () => {
  it.each([
    ["REQUIRED", "请输入邮箱"],
    ["INVALID_FORMAT", "邮箱格式不正确"],
    ["TOO_LONG", "邮箱格式不正确"],
    ["SOMETHING_NEW", "邮箱格式不正确"],
    [null, "邮箱格式不正确"],
  ])("邮箱的原因码 %j：%s", (code, message) => {
    expect(fieldErrorMessage("email", code)).toBe(message);
  });

  it.each([
    ["REQUIRED", "请输入密码"],
    ["TOO_SHORT", "密码至少 8 位"],
    ["TOO_LONG", "密码最长 64 位"],
    ["TOO_COMMON", "这个密码太常见，容易被猜到，请换一个"],
    ["INVALID_CHARACTER", "密码不符合要求"],
    ["SOMETHING_NEW", "密码不符合要求"],
    [null, "密码不符合要求"],
  ])("密码的原因码 %j：%s", (code, message) => {
    expect(fieldErrorMessage("password", code)).toBe(message);
  });

  // 普通对象按原因码查表时，`constructor` 这类名字会查到对象原型上的属性
  it("原因码和对象原型上的属性同名时，仍是该字段的通用错误", () => {
    expect(fieldErrorMessage("password", "constructor")).toBe("密码不符合要求");
  });
});

describe("loginAlertMessage", () => {
  it.each([
    [{ ok: false, reason: "bad-credentials" } as const, "邮箱或密码错误"],
    [{ ok: false, reason: "banned" } as const, "该账号已被封禁，无法登录"],
    [{ ok: false, reason: "unavailable" } as const, "登录没有成功，请稍后再试"],
  ])("%j：%s", (failure, message) => {
    expect(loginAlertMessage(failure)).toBe(message);
  });

  it.each([
    [0, "尝试次数过多，请 1 分钟后再试"],
    [59, "尝试次数过多，请 1 分钟后再试"],
    [60, "尝试次数过多，请 1 分钟后再试"],
    [61, "尝试次数过多，请 2 分钟后再试"],
    [null, "尝试次数过多，请稍后再试"],
  ])("限流，等待 %j 秒：%s", (retryAfterSeconds, message) => {
    expect(loginAlertMessage({ ok: false, reason: "throttled", retryAfterSeconds })).toBe(message);
  });
});

describe("其余文案", () => {
  it("注册失败的提示条", () => {
    expect(REGISTER_UNAVAILABLE_MESSAGE).toBe("注册没有成功，请稍后再试");
  });

  it("三条成功提示", () => {
    expect(TOAST_MESSAGES).toEqual({
      loggedIn: "已登录",
      registered: "注册成功，已登录",
      loggedOut: "已退出登录",
    });
  });

  it("前端校验用的字段文案", () => {
    expect(FIELD_MESSAGES).toMatchObject({
      emailRequired: "请输入邮箱",
      emailInvalid: "邮箱格式不正确",
      passwordRequired: "请输入密码",
      passwordTooShort: "密码至少 8 位",
      passwordTooLong: "密码最长 64 位",
      confirmRequired: "请再输入一次密码",
      confirmMismatch: "两次输入的密码不一致",
      emailTaken: "该邮箱已注册，",
      emailTakenLink: "去登录",
    });
  });
});
```

新建 `patra-portal/tests/lib/auth/form-schemas.test.ts`：

```ts
// @vitest-environment node
import { describe, expect, it } from "vitest";
import type { z } from "zod";
import { loginSchema, registerSchema } from "@/lib/auth/form-schemas";

/// 每个字段的第一条错误：表单只显示这一条（RHF 的 zodResolver 默认也只取第一条）
function firstErrors(schema: z.ZodType, values: unknown): Record<string, string> {
  const result = schema.safeParse(values);
  const errors: Record<string, string> = {};
  for (const issue of result.success ? [] : result.error.issues) {
    errors[issue.path.join(".")] ??= issue.message;
  }
  return errors;
}

/// 64 + 1 + 185 + 4 = 254 个字符的合法邮箱
const EMAIL_254 = `${"a".repeat(64)}@${"b".repeat(185)}.com`;
const VALID = { email: "reader@example.com", password: "correct horse" };

describe("邮箱（两页相同）", () => {
  it("去掉首尾空白后提交", () => {
    expect(loginSchema.parse({ ...VALID, email: "  reader@example.com\t" }).email).toBe(
      "reader@example.com",
    );
  });

  it.each(["", "   "])("去掉空白后为空（%j）：请输入邮箱", (email) => {
    expect(firstErrors(loginSchema, { ...VALID, email })).toEqual({ email: "请输入邮箱" });
  });

  it.each(["reader", "reader@", "reader@example", "读者@example.com"])(
    "不符合 z.email()（%j）：邮箱格式不正确",
    (email) => {
      expect(firstErrors(loginSchema, { ...VALID, email })).toEqual({ email: "邮箱格式不正确" });
    },
  );

  it("254 个字符可以，255 个字符是「邮箱格式不正确」", () => {
    expect(firstErrors(loginSchema, { ...VALID, email: EMAIL_254 })).toEqual({});
    expect(firstErrors(loginSchema, { ...VALID, email: `a${EMAIL_254}` })).toEqual({
      email: "邮箱格式不正确",
    });
  });
});

describe("登录页的密码", () => {
  it("只要求非空，不去空格、不限长度", () => {
    expect(firstErrors(loginSchema, { ...VALID, password: "" })).toEqual({
      password: "请输入密码",
    });
    expect(loginSchema.parse({ ...VALID, password: " 1 " }).password).toBe(" 1 ");
    expect(firstErrors(loginSchema, { ...VALID, password: "a".repeat(100) })).toEqual({});
  });
});

describe("注册页的密码", () => {
  const register = (password: string, confirmPassword = password) =>
    firstErrors(registerSchema, { email: VALID.email, password, confirmPassword });

  it("为空：请输入密码", () => {
    expect(register("", "x")).toMatchObject({ password: "请输入密码" });
  });

  it("按码点计长度：汉字和表情各算 1 位，8 位、64 位两个边界都可以", () => {
    expect(register("密码密码密码密码")).toEqual({});
    expect(register("😀".repeat(8))).toEqual({});
    expect(register("a".repeat(64))).toEqual({});
    // 33 个表情是 66 个 UTF-16 单元，按码点只有 33 位
    expect(register("😀".repeat(33))).toEqual({});
  });

  it("少于 8 位：密码至少 8 位（7 个表情按 UTF-16 单元算是 14 位，也要拦）", () => {
    expect(register("1234567")).toEqual({ password: "密码至少 8 位" });
    expect(register("😀".repeat(7))).toEqual({ password: "密码至少 8 位" });
  });

  it("多于 64 位：密码最长 64 位", () => {
    expect(register("a".repeat(65))).toEqual({ password: "密码最长 64 位" });
  });

  it("不去空格：首尾的空格算在长度里", () => {
    expect(register("       1")).toEqual({});
  });
});

describe("注册页的确认密码", () => {
  const values = { email: VALID.email, password: "correct horse" };

  it("为空：请再输入一次密码", () => {
    expect(firstErrors(registerSchema, { ...values, confirmPassword: "" })).toEqual({
      confirmPassword: "请再输入一次密码",
    });
  });

  it("和密码不同：两次输入的密码不一致", () => {
    expect(firstErrors(registerSchema, { ...values, confirmPassword: "correct horse " })).toEqual({
      confirmPassword: "两次输入的密码不一致",
    });
  });

  it("其他字段有错时也照样比对", () => {
    expect(firstErrors(registerSchema, { ...values, email: "", confirmPassword: "wrong" })).toEqual(
      { email: "请输入邮箱", confirmPassword: "两次输入的密码不一致" },
    );
  });
});
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/lib/auth/messages.test.ts tests/lib/auth/form-schemas.test.ts`
Expected: FAIL，两个文件都报 `Error: Cannot find package`（`'@/lib/auth/messages'`、`'@/lib/auth/form-schemas'`），`Test Files  2 failed (2)`，`Tests  no tests`。

- [ ] **Step 3: 写实现**

新建 `patra-portal/src/lib/auth/messages.ts`：

```ts
import type { LoginResult } from "@/lib/auth/types";

/// 字段下方的错误文案（简报 §4.1、§4.2）。前端校验和后端的原因码共用。
export const FIELD_MESSAGES = {
  emailRequired: "请输入邮箱",
  emailInvalid: "邮箱格式不正确",
  passwordRequired: "请输入密码",
  passwordTooShort: "密码至少 8 位",
  passwordTooLong: "密码最长 64 位",
  passwordTooCommon: "这个密码太常见，容易被猜到，请换一个",
  passwordInvalid: "密码不符合要求",
  confirmRequired: "请再输入一次密码",
  confirmMismatch: "两次输入的密码不一致",
  /// 注册时邮箱已被占用：后面紧跟「去登录」链接
  emailTaken: "该邮箱已注册，",
  emailTakenLink: "去登录",
} as const;

/// 成功提示（简报 §4.4）
export const TOAST_MESSAGES = {
  loggedIn: "已登录",
  registered: "注册成功，已登录",
  loggedOut: "已退出登录",
} as const;

/// 注册失败、只能请用户稍后再试时的提示条
export const REGISTER_UNAVAILABLE_MESSAGE = "注册没有成功，请稍后再试";

/// 后端返回的字段错误 → 字段下方的文案。邮箱只有「为空」单独一种说法，其余原因码、不认识的原因码
/// 和 null 都是「邮箱格式不正确」；密码不认识的原因码和 null 是「密码不符合要求」。
export function fieldErrorMessage(field: "email" | "password", code: string | null): string {
  if (field === "email") {
    return code === "REQUIRED" ? FIELD_MESSAGES.emailRequired : FIELD_MESSAGES.emailInvalid;
  }
  switch (code) {
    case "REQUIRED":
      return FIELD_MESSAGES.passwordRequired;
    case "TOO_SHORT":
      return FIELD_MESSAGES.passwordTooShort;
    case "TOO_LONG":
      return FIELD_MESSAGES.passwordTooLong;
    case "TOO_COMMON":
      return FIELD_MESSAGES.passwordTooCommon;
    default:
      return FIELD_MESSAGES.passwordInvalid;
  }
}

/// 登录失败时用提示条显示的结果。`invalid-fields` 显示在字段下方，不在这里。
export type LoginAlertFailure = Exclude<LoginResult, { ok: true } | { reason: "invalid-fields" }>;

/// 登录失败时提示条的文案。限流时按等待秒数向上取整到分钟，至少 1 分钟；秒数为 null 时不说时长。
export function loginAlertMessage(failure: LoginAlertFailure): string {
  switch (failure.reason) {
    case "bad-credentials":
      return "邮箱或密码错误";
    case "banned":
      return "该账号已被封禁，无法登录";
    case "throttled":
      return failure.retryAfterSeconds === null
        ? "尝试次数过多，请稍后再试"
        : `尝试次数过多，请 ${Math.max(1, Math.ceil(failure.retryAfterSeconds / 60))} 分钟后再试`;
    case "unavailable":
      return "登录没有成功，请稍后再试";
  }
}
```

新建 `patra-portal/src/lib/auth/form-schemas.ts`：

```ts
import { z } from "zod";
import { FIELD_MESSAGES } from "@/lib/auth/messages";

/// 密码长度按码点计，汉字、表情各算 1 位，和后端一致
const codePoints = (value: string): number => [...value].length;

/// 邮箱：去掉首尾空白后为空是「请输入邮箱」；超过 254 个字符或不符合 Zod 默认的 `z.email()`（后端的
/// 正则照抄的就是它）是「邮箱格式不正确」。解析结果是去掉空白之后的值，提交的也是它。
const emailField = z
  .string()
  .trim()
  .min(1, FIELD_MESSAGES.emailRequired)
  .max(254, FIELD_MESSAGES.emailInvalid)
  .pipe(z.email(FIELD_MESSAGES.emailInvalid));

/// 登录表单：后端的登录接口只检查密码非空，前端也只查这一条，不去空格
export const loginSchema = z.object({
  email: emailField,
  password: z.string().min(1, FIELD_MESSAGES.passwordRequired),
});

/// 注册表单：密码 8 ~ 64 位，不去空格；确认密码只在前端比对，不传给后端
export const registerSchema = z
  .object({
    email: emailField,
    password: z
      .string()
      .min(1, FIELD_MESSAGES.passwordRequired)
      .refine((value) => codePoints(value) >= 8, FIELD_MESSAGES.passwordTooShort)
      .refine((value) => codePoints(value) <= 64, FIELD_MESSAGES.passwordTooLong),
    confirmPassword: z.string().min(1, FIELD_MESSAGES.confirmRequired),
  })
  .refine((values) => values.password === values.confirmPassword, {
    path: ["confirmPassword"],
    error: FIELD_MESSAGES.confirmMismatch,
  });

/// 表单里各字段的原始值
export type LoginFormValues = z.input<typeof loginSchema>;
export type RegisterFormValues = z.input<typeof registerSchema>;
```

- [ ] **Step 4: 跑测试，确认通过**

Run: 同 Step 2。
Expected: PASS，`Test Files  2 passed (2)`，`Tests  41 passed (41)`。

- [ ] **Step 5: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write src/lib/auth/messages.ts src/lib/auth/form-schemas.ts tests/lib/auth/messages.test.ts tests/lib/auth/form-schemas.test.ts
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0，`lint` 输出 `Checked 262 files`；`Test Files  98 passed (98)`、`Tests  905 passed (905)`。

- [ ] **Step 6: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/src/lib/auth/messages.ts patra-portal/src/lib/auth/form-schemas.ts patra-portal/tests/lib/auth/messages.test.ts patra-portal/tests/lib/auth/form-schemas.test.ts
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
feat(portal): 登录、注册表单的文案和校验规则 (PAP-68)

文案照简报第 4 节：后端的原因码按字段选文案，不认识的原因码和 null 显示该字段的通用错误；限流
的分钟数向上取整，至少 1 分钟。schema 的口径和后端一致：邮箱去掉首尾空白后最长 254 个字符，
用 Zod 默认的 z.email()；密码按码点计长度，不去空格；确认密码只在前端比对。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这 4 个文件；pre-commit 全部通过。

---

### Task 5: 退出登录的 `useLogout()`

**Files:**
- Create: `patra-portal/src/lib/auth/logout-mutation.ts`
- Test: `patra-portal/tests/lib/auth/logout-mutation.test.tsx`

**Interfaces:**
- Consumes: PAP-67 `logout(): Promise<void>`（`src/lib/auth/actions.ts`）、`queryKeys.currentUser`（`src/lib/query-keys.ts`）；Task 4 `TOAST_MESSAGES.loggedOut`。
- Produces: `useLogout()`，返回 TanStack Query 的 mutation 结果，后面用到 `mutate`、`isPending`、`isSuccess`。Task 13 的 `NavAccount`、Task 14 的 `MobileAccount` 各自调用。

- [ ] **Step 1: 写失败的测试**

新建 `patra-portal/tests/lib/auth/logout-mutation.test.tsx`：

```tsx
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { logout } from "@/lib/auth/actions";
import { useCurrentUser } from "@/lib/auth/current-user-query";
import { useLogout } from "@/lib/auth/logout-mutation";
import { queryKeys } from "@/lib/query-keys";

vi.mock("@/lib/auth/actions", () => ({ login: vi.fn(), logout: vi.fn(), register: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn() } }));

import { toast } from "sonner";

const USER = { userId: "319041872872550658", email: "reader@example.com" };

/// 一个还没回来的请求，`answer` 让它在测试想要的时候回来
function deferred<T>() {
  let answer: (value: T) => void = () => {};
  const promise = new Promise<T>((resolve) => {
    answer = resolve;
  });
  return { promise, answer };
}

/// 导航栏的当前用户和退出登录放在同一个组件里，和 NavAccount 一样
function renderAccount(client: QueryClient) {
  return renderHook(() => ({ user: useCurrentUser(), logout: useLogout() }), {
    wrapper: ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    ),
  });
}

let client: QueryClient;

beforeEach(() => {
  // 和 createQueryClient 一样，数据 60 秒内不过期：挂载时不会因为数据过期而重新请求
  client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 60_000 } } });
  client.setQueryData(queryKeys.currentUser, USER);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe("useLogout", () => {
  it("调用 logout() 时不带参数：Server Action 的参数会被序列化发给服务端", async () => {
    vi.mocked(logout).mockResolvedValue(undefined);
    const { result } = renderAccount(client);
    act(() => result.current.logout.mutate());
    await waitFor(() => expect(result.current.logout.isSuccess).toBe(true));
    expect(vi.mocked(logout).mock.calls).toEqual([[]]);
  });

  it("进行中是 pending；成功后当前用户写成 null，提示「已退出登录」", async () => {
    const pending = deferred<void>();
    vi.mocked(logout).mockReturnValue(pending.promise);
    const { result } = renderAccount(client);
    act(() => result.current.logout.mutate());
    await waitFor(() => expect(result.current.logout.isPending).toBe(true));
    pending.answer();
    await waitFor(() => expect(result.current.user).toEqual({ status: "anonymous" }));
    expect(client.getQueryData(queryKeys.currentUser)).toBeNull();
    expect(toast.success).toHaveBeenCalledWith("已退出登录");
  });

  it("成功前发出、成功后才回来的 /api/auth/me 不会把 null 覆盖掉", async () => {
    const late = { ok: true, json: vi.fn(async () => ({ user: USER })) };
    const me = deferred<typeof late>();
    vi.stubGlobal(
      "fetch",
      vi.fn(() => me.promise),
    );
    vi.mocked(logout).mockResolvedValue(undefined);
    const { result } = renderAccount(client);
    // 当前用户正在重新请求（比如数据过期了），请求还没回来
    act(() => {
      void client.invalidateQueries({ queryKey: queryKeys.currentUser });
    });
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
    act(() => result.current.logout.mutate());
    await waitFor(() => expect(result.current.logout.isSuccess).toBe(true));
    // 请求这时才回来，响应体也读完了
    me.answer(late);
    await waitFor(() => expect(late.json).toHaveBeenCalled());
    await act(async () => {});
    expect(client.getQueryData(queryKeys.currentUser)).toBeNull();
    expect(result.current.user).toEqual({ status: "anonymous" });
  });

  it("promise 被拒绝：不写 null，重新请求当前用户，不提示", async () => {
    const fetchMock = vi.fn(async () => Response.json({ user: USER }));
    vi.stubGlobal("fetch", fetchMock);
    vi.mocked(logout).mockRejectedValue(new Error("network"));
    const { result } = renderAccount(client);
    act(() => result.current.logout.mutate());
    await waitFor(() => expect(result.current.logout.isError).toBe(true));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith("/api/auth/me"));
    expect(result.current.user).toEqual({ status: "authenticated", user: USER });
    expect(toast.success).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/lib/auth/logout-mutation.test.tsx`
Expected: FAIL，`Error: Failed to resolve import "@/lib/auth/logout-mutation" from "tests/lib/auth/logout-mutation.test.tsx". Does the file exist?`，`Tests  no tests`。

- [ ] **Step 3: 写实现**

新建 `patra-portal/src/lib/auth/logout-mutation.ts`：

```ts
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { logout } from "@/lib/auth/actions";
import { TOAST_MESSAGES } from "@/lib/auth/messages";
import { queryKeys } from "@/lib/query-keys";

/// 退出登录，桌面的用户菜单和移动端的账号块共用。点了就退，不二次确认；进行中 `isPending` 为 true。
/// - 成功：先取消进行中的当前用户请求，再写入 null，弹「已退出登录」。不先取消的话，一个成功前发出、
///   成功后才回来的 `/api/auth/me` 会把用户写回去。
/// - promise 被拒绝：不写 null，让当前用户重新请求一次，以门户的回答为准；不提示（简报 §4.3）。
export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    // 不直接传 `logout`：useMutation 会把变量和上下文作为参数传进来，Server Action 会把参数序列化发给服务端
    mutationFn: () => logout(),
    onSuccess: async () => {
      await queryClient.cancelQueries({ queryKey: queryKeys.currentUser });
      queryClient.setQueryData(queryKeys.currentUser, null);
      toast.success(TOAST_MESSAGES.loggedOut);
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.currentUser });
    },
  });
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: 同 Step 2。
Expected: PASS，`Tests  4 passed (4)`。

- [ ] **Step 5: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write src/lib/auth/logout-mutation.ts tests/lib/auth/logout-mutation.test.tsx
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0，`lint` 输出 `Checked 264 files`；`Test Files  99 passed (99)`、`Tests  909 passed (909)`。

- [ ] **Step 6: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/src/lib/auth/logout-mutation.ts patra-portal/tests/lib/auth/logout-mutation.test.tsx
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
feat(portal): 退出登录共用的 useLogout (PAP-68)

成功时先取消进行中的当前用户请求，再写入 null 并提示「已退出登录」，晚回来的 /api/auth/me 不会
把用户写回去；promise 被拒绝时让当前用户重新请求一次，以门户的回答为准，不提示。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这两个文件；pre-commit 全部通过。

---

### Task 6: 表单字段、密码框、提示条，按钮支持 `aria-disabled`

**Files:**
- Modify: `patra-portal/src/lib/portal-ui.ts`（`BTN_BASE`、`btnPrimary`、`btnSecondary`）
- Modify: `patra-portal/src/app/globals.css`（base 层加自动填充的底色）
- Create: `patra-portal/src/components/auth/FormField.tsx`、`PasswordInput.tsx`、`FormAlert.tsx`
- Test: `patra-portal/tests/lib/portal-ui.test.ts`（加用例）、`patra-portal/tests/components/auth/FormField.test.tsx`、`PasswordInput.test.tsx`、`FormAlert.test.tsx`

**Interfaces:**
- Consumes: 现有的 `btnPrimary`、`btnSecondary`、`btnBlock`；`globals.css` 里折叠区入场用的 `.disclosure-body`；lucide-react 的 `CircleAlert`、`Eye`、`EyeOff`。
- Produces:
  - `FormField({ id, label, hint?, error?, children })`：`children` 是 `(control: FieldControl) => ReactNode`，`FieldControl` 是 `{ id: string; className: string; "aria-invalid": boolean; "aria-describedby"?: string }`，调用方把它展开到输入框上。`hint`、`error` 都是 `ReactNode`，错误替换提示。
  - `PasswordInput(props: ComponentProps<"input">)`：放在 `FormField` 的外框里，属性原样交给输入框，`ref` 同时交给调用方（RHF 的 `register` 靠它找到输入框）。
  - `FormAlert({ children })`：`role="alert"` 的表单级提示条。
  - 按钮：`aria-disabled="true"` 时显示不可点的光标、55% 不透明度，不响应 hover 和按下。
  - Task 7、8 的两张表单用这些组件和按钮样式。

- [ ] **Step 1: 写失败的测试：按钮支持 `aria-disabled`**

```diff
diff --git a/patra-portal/tests/lib/portal-ui.test.ts b/patra-portal/tests/lib/portal-ui.test.ts
--- a/patra-portal/tests/lib/portal-ui.test.ts
+++ b/patra-portal/tests/lib/portal-ui.test.ts
@@ -22,6 +22,22 @@ describe("按钮类串", () => {
     }
   });

+  it.each([
+    ["btnPrimary", btnPrimary],
+    ["btnSecondary", btnSecondary],
+  ])("%s 的 hover / 按下反馈同样不作用于 aria-disabled 的按钮", (_, cls) => {
+    const stateful = tokens(cls).filter((t) => /(?:^|:)(?:hover|active):/.test(t));
+    for (const t of stateful) {
+      expect(t, t).toMatch(/(?:^|:)not-aria-disabled:/);
+    }
+  });
+
+  it("aria-disabled 的按钮显示不可点的光标、55% 不透明度（提交中的按钮用它，不用 disabled）", () => {
+    expect(tokens(btnPrimary)).toEqual(
+      expect.arrayContaining(["aria-disabled:cursor-not-allowed", "aria-disabled:opacity-55"]),
+    );
+  });
+
   it("hover 前移只作用于标了 data-icon=trailing 的末尾图标（前置图标不动）", () => {
     expect(btnPrimary).toMatch(/\[&>svg\[data-icon=trailing\]\]:translate-x-0\.5/);
     expect(btnPrimary).not.toMatch(/svg:last-child/);
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/lib/portal-ui.test.ts`
Expected: FAIL，`Tests  3 failed | 10 passed (13)`。前两条是 `expected 'motion-safe:not-disabled:hover:[&>svg…' to match /(?:^|:)not-aria-disabled:/`，第三条是 `expected [ Array(29) ] to deeply equal ArrayContaining{…}`。

- [ ] **Step 3: 写实现**

```diff
diff --git a/patra-portal/src/lib/portal-ui.ts b/patra-portal/src/lib/portal-ui.ts
--- a/patra-portal/src/lib/portal-ui.ts
+++ b/patra-portal/src/lib/portal-ui.ts
@@ -1,11 +1,12 @@
 /// 详情页 / 状态屏共用的暖纸按钮 class 串（.btn-primary=clay / .btn-sec）。
 /// 末尾图标（箭头 / 外链，标 `data-icon="trailing"`）hover 时向前微移，按下时整体下沉 1px；
-/// 前置图标不动。禁用态不响应 hover / 按下。
+/// 前置图标不动。禁用态不响应 hover / 按下。`aria-disabled="true"` 的按钮（比如提交中的提交按钮，
+/// 不用 `disabled` 以免丢焦点）同样不响应，并显示不可点的光标、55% 不透明度。
 const BTN_BASE =
-  "inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-full border px-5 py-2.5 font-sans text-base font-semibold no-underline outline-none transition duration-200 ease-out-expo disabled:cursor-not-allowed motion-safe:[&>svg]:transition-transform [&>svg]:duration-300 [&>svg]:ease-out-expo motion-safe:not-disabled:hover:[&>svg[data-icon=trailing]]:translate-x-0.5 motion-safe:not-disabled:active:translate-y-px";
+  "inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-full border px-5 py-2.5 font-sans text-base font-semibold no-underline outline-none transition duration-200 ease-out-expo disabled:cursor-not-allowed aria-disabled:cursor-not-allowed aria-disabled:opacity-55 motion-safe:[&>svg]:transition-transform [&>svg]:duration-300 [&>svg]:ease-out-expo motion-safe:not-disabled:not-aria-disabled:hover:[&>svg[data-icon=trailing]]:translate-x-0.5 motion-safe:not-disabled:not-aria-disabled:active:translate-y-px";

-export const btnPrimary = `${BTN_BASE} border-action-primary bg-action-primary text-fg-on-clay not-disabled:hover:border-action-primary-hover not-disabled:hover:bg-action-primary-hover not-disabled:active:bg-action-primary-press`;
-export const btnSecondary = `${BTN_BASE} border-border-default bg-paper-50 text-fg-1 not-disabled:hover:border-border-hover not-disabled:hover:bg-paper-200`;
+export const btnPrimary = `${BTN_BASE} border-action-primary bg-action-primary text-fg-on-clay not-disabled:not-aria-disabled:hover:border-action-primary-hover not-disabled:not-aria-disabled:hover:bg-action-primary-hover not-disabled:not-aria-disabled:active:bg-action-primary-press`;
+export const btnSecondary = `${BTN_BASE} border-border-default bg-paper-50 text-fg-1 not-disabled:not-aria-disabled:hover:border-border-hover not-disabled:not-aria-disabled:hover:bg-paper-200`;
 export const btnBlock = "w-full px-4 py-3";

 /// 自绘复选框：纸面方框，选中为 action-primary 填充 + 纸色勾线描出（见 CHECK_MARK；焦点环走全局规则）。
```

- [ ] **Step 4: 跑测试，确认通过**

新的类名也要过设计系统的守护测试（焦点、动效、对比度），一起跑：

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/lib/portal-ui.test.ts tests/design-system`
Expected: PASS，`Test Files  10 passed (10)`，`Tests  60 passed (60)`。

- [ ] **Step 5: 写失败的测试：三个表单组件**

新建 `patra-portal/tests/components/auth/FormField.test.tsx`：

```tsx
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import type { ReactNode } from "react";
import { FormField } from "@/components/auth/FormField";

function renderField(props: { hint?: ReactNode; error?: ReactNode }) {
  return render(
    <FormField id="login-email" label="邮箱" {...props}>
      {(control) => <input {...control} type="email" />}
    </FormField>,
  );
}

describe("FormField", () => {
  it("标签和输入框关联", () => {
    renderField({});
    expect(screen.getByLabelText("邮箱")).toHaveAttribute("id", "login-email");
  });

  it("没有错误时显示常驻提示，输入框的描述指向它", () => {
    renderField({ hint: "8 ~ 64 位，区分大小写" });
    const input = screen.getByLabelText("邮箱");
    expect(input).toHaveAttribute("aria-invalid", "false");
    expect(input).toHaveAccessibleDescription("8 ~ 64 位，区分大小写");
  });

  it("有错误时错误文案替换常驻提示，带图标，输入框标为无效", () => {
    renderField({ hint: "8 ~ 64 位，区分大小写", error: "邮箱格式不正确" });
    const input = screen.getByLabelText("邮箱");
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).toHaveAccessibleDescription("邮箱格式不正确");
    expect(screen.queryByText("8 ~ 64 位，区分大小写")).not.toBeInTheDocument();
    const line = screen.getByText("邮箱格式不正确").closest("p");
    expect(line?.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
  });

  it("错误替换提示时，错误行是新插入的节点（入场过渡靠插入触发）", () => {
    const field = (error?: string) => (
      <FormField id="register-password" label="密码" hint="至少 8 位" error={error}>
        {(control) => <input {...control} type="password" />}
      </FormField>
    );
    const { rerender } = render(field());
    const hint = screen.getByText("至少 8 位");
    rerender(field("密码至少 8 位"));
    expect(screen.getByText("密码至少 8 位").closest("p")).not.toBe(hint);
  });

  it("错误文案里可以带链接", () => {
    renderField({
      error: (
        <>
          该邮箱已注册，<a href="/login">去登录</a>
        </>
      ),
    });
    expect(screen.getByLabelText("邮箱")).toHaveAccessibleDescription("该邮箱已注册，去登录");
    expect(screen.getByRole("link", { name: "去登录" })).toHaveAttribute("href", "/login");
  });

  it("既没有提示也没有错误时不带描述", () => {
    renderField({});
    expect(screen.getByLabelText("邮箱")).not.toHaveAttribute("aria-describedby");
  });
});
```

新建 `patra-portal/tests/components/auth/PasswordInput.test.tsx`：

```tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { PasswordInput } from "@/components/auth/PasswordInput";

function renderPassword() {
  render(
    <>
      <label htmlFor="pw">密码</label>
      <PasswordInput id="pw" autoComplete="current-password" aria-describedby="pw-hint" />
    </>,
  );
  return screen.getByLabelText("密码") as HTMLInputElement;
}

describe("PasswordInput", () => {
  it("默认隐藏；切换按钮的读屏名称随状态变化", async () => {
    const user = userEvent.setup();
    const input = renderPassword();
    expect(input).toHaveAttribute("type", "password");
    await user.click(screen.getByRole("button", { name: "显示密码" }));
    expect(input).toHaveAttribute("type", "text");
    await user.click(screen.getByRole("button", { name: "隐藏密码" }));
    expect(input).toHaveAttribute("type", "password");
  });

  it("其余属性原样交给输入框", () => {
    const input = renderPassword();
    expect(input).toHaveAttribute("autocomplete", "current-password");
    expect(input).toHaveAttribute("aria-describedby", "pw-hint");
  });

  it("用鼠标切换时焦点留在输入框，光标位置和选区不变", async () => {
    const user = userEvent.setup();
    const input = renderPassword();
    await user.type(input, "secret-42");
    input.setSelectionRange(2, 5);
    await user.click(screen.getByRole("button", { name: "显示密码" }));
    expect(input).toHaveFocus();
    expect([input.selectionStart, input.selectionEnd]).toEqual([2, 5]);
  });

  it("浏览器改 type 时把光标移到了开头，也恢复成切换前的选区", async () => {
    const user = userEvent.setup();
    const input = renderPassword();
    await user.type(input, "secret-42");
    input.setSelectionRange(3, 6);
    // 有的浏览器在 type 改变时重置选区：照常改 type，再把光标放到开头
    const setType = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "type")?.set;
    vi.spyOn(input, "type", "set").mockImplementation(function (this: HTMLInputElement, value) {
      setType?.call(this, value);
      this.setSelectionRange(0, 0);
    });
    await user.click(screen.getByRole("button", { name: "显示密码" }));
    expect(input).toHaveAttribute("type", "text");
    expect([input.selectionStart, input.selectionEnd]).toEqual([3, 6]);
  });

  it("切换按钮不是提交按钮", () => {
    renderPassword();
    expect(screen.getByRole("button", { name: "显示密码" })).toHaveAttribute("type", "button");
  });
});
```

新建 `patra-portal/tests/components/auth/FormAlert.test.tsx`：

```tsx
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { FormAlert } from "@/components/auth/FormAlert";

describe("FormAlert", () => {
  it("是 alert，读屏插入时就读出；带图标，不只靠颜色", () => {
    render(<FormAlert>邮箱或密码错误</FormAlert>);
    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("邮箱或密码错误");
    expect(alert.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
  });
});
```

- [ ] **Step 6: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/components/auth`
Expected: FAIL，三个文件都报 `Error: Failed to resolve import "@/components/auth/…" from "tests/components/auth/….test.tsx". Does the file exist?`，`Test Files  3 failed (3)`，`Tests  no tests`。

- [ ] **Step 7: 写实现**

新建 `patra-portal/src/components/auth/FormField.tsx`：

```tsx
import { CircleAlert } from "lucide-react";
import type { ReactNode } from "react";

/// 交给输入框的属性：`id` 和标签关联，`aria-describedby` 指向下方当前显示的那一行
export interface FieldControl {
  id: string;
  className: string;
  "aria-invalid": boolean;
  "aria-describedby"?: string;
}

interface FormFieldProps {
  id: string;
  label: string;
  /// 常驻提示，比如注册页密码的「至少 8 位」。有错误时被错误文案替换
  hint?: ReactNode;
  /// 错误文案，可以带链接（「该邮箱已注册，去登录」）
  error?: ReactNode;
  children: (control: FieldControl) => ReactNode;
}

/// 输入框本身透明、无描边，描边和聚焦光晕画在外框上（全局焦点环不管文本框）。移动端字号 17px，
/// iOS 聚焦时不放大页面。自动填充后的底色由 globals.css 的 `input:-webkit-autofill` 规则处理。
const INPUT =
  "h-full min-w-0 flex-1 bg-transparent p-0 text-md leading-6 text-ink-900 outline-none placeholder:text-fg-4 read-only:text-fg-2 max-[880px]:text-lg";

/// 外框：内凹的输入井。悬停加深描边；输入框聚焦时换描边、加光晕；出错时描边变红，聚焦光晕也换成
/// 红色；提交中只读时换凹陷底色，去掉内阴影和光晕。三种状态都按输入框判断：切换按钮聚焦时外框不亮
/// （高保真稿 Spec-Form 02）。不用 `focus-within:`：它生成在 `hover:` 之前，悬停加聚焦时描边会是
/// 悬停色；`has-[…]` 生成在 `hover:` 之后，彼此按方括号里的字母序，只读 > 出错 > 聚焦。
/// 里面有切换按钮时右边距收窄，移动端按钮 44px，收得更窄。
const BOX =
  "flex h-12 items-center gap-1 rounded-xl border border-border-strong bg-bg-elevated px-4 shadow-inset ring-clay-500/15 transition-[border-color,box-shadow,background-color] duration-200 hover:border-ink-500 has-[button]:pr-2 max-[880px]:has-[button]:pr-0.5 has-[input:focus]:border-border-focus has-[input:focus]:ring-4 has-[input[aria-invalid=true]]:border-status-danger has-[input[aria-invalid=true]]:ring-rust-500/15 has-[input[readonly]]:border-border-default has-[input[readonly]]:bg-bg-sunken has-[input[readonly]]:shadow-none has-[input[readonly]]:ring-0";

/// 表单字段：标签、输入框，下面一行是常驻提示或错误文案，错误替换提示，两者不同时出现。
/// 输入框由 `children` 渲染，拿到 `id`、`aria-invalid`、`aria-describedby` 和样式。
/// 错误行出现时淡入下落，参数和折叠区相同，直接用 `.disclosure-body`（高保真稿 Spec-Form 07）。
/// 错误行和提示行的 `key` 不同：错误替换提示时节点重新插入，入场过渡才会触发。
export function FormField({ id, label, hint, error, children }: FormFieldProps) {
  const messageId = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
  return (
    <div className="flex min-w-0 flex-col gap-2">
      <label htmlFor={id} className="font-medium text-fg-1 text-sm leading-5">
        {label}
      </label>
      <div className={BOX}>
        {children({
          id,
          className: INPUT,
          "aria-invalid": Boolean(error),
          "aria-describedby": messageId,
        })}
      </div>
      {error ? (
        <p
          key="error"
          id={messageId}
          className="disclosure-body flex items-start gap-1.5 text-sm text-status-danger leading-5"
        >
          <CircleAlert className="mt-0.75 size-3.5 shrink-0" aria-hidden="true" />
          <span>{error}</span>
        </p>
      ) : hint ? (
        <p key="hint" id={messageId} className="text-fg-3 text-sm leading-5">
          {hint}
        </p>
      ) : null}
    </div>
  );
}
```

新建 `patra-portal/src/components/auth/PasswordInput.tsx`：

```tsx
"use client";

import { Eye, EyeOff } from "lucide-react";
import { type ComponentProps, type Ref, useRef, useState } from "react";
import { flushSync } from "react-dom";

/// 把输入框同时交给自己的 ref 和调用方的 ref（RHF 的 `register` 靠它找到输入框）
function assignRef<T>(ref: Ref<T> | undefined, node: T | null) {
  if (typeof ref === "function") {
    ref(node);
  } else if (ref) {
    ref.current = node;
  }
}

/// 密码输入框，右侧是显示 / 隐藏切换按钮，默认隐藏；按钮桌面 32px，移动端 44px。放在 FormField 的外框里，属性原样交给输入框。
/// 切换时焦点留在输入框里，光标位置和选区不变：按下按钮时不抢焦点；有的浏览器改 `type` 时会把光标
/// 移到开头，所以改完再把切换前的选区放回去。
export function PasswordInput({ ref, ...props }: ComponentProps<"input">) {
  const [visible, setVisible] = useState(false);
  const inputRef = useRef<HTMLInputElement | null>(null);

  function toggle() {
    const input = inputRef.current;
    const start = input?.selectionStart ?? null;
    const end = input?.selectionEnd ?? null;
    flushSync(() => setVisible((value) => !value));
    if (input && start !== null && end !== null && document.activeElement === input) {
      input.setSelectionRange(start, end);
    }
  }

  return (
    <>
      <input
        {...props}
        ref={(node) => {
          inputRef.current = node;
          assignRef(ref, node);
        }}
        type={visible ? "text" : "password"}
      />
      <button
        type="button"
        aria-label={visible ? "隐藏密码" : "显示密码"}
        onMouseDown={(event) => event.preventDefault()}
        onClick={toggle}
        className="inline-flex size-8 shrink-0 max-[880px]:size-11 items-center justify-center rounded-full text-fg-3 transition-colors duration-150 hover:bg-paper-200 hover:text-ink-900"
      >
        {visible ? (
          <EyeOff className="size-3.75" aria-hidden="true" />
        ) : (
          <Eye className="size-3.75" aria-hidden="true" />
        )}
      </button>
    </>
  );
}
```

新建 `patra-portal/src/components/auth/FormAlert.tsx`：

```tsx
import { CircleAlert } from "lucide-react";
import type { ReactNode } from "react";

/// 表单级提示条：失败原因不属于某个字段时显示在表单上方。`role="alert"`，插入时读屏立即朗读。
/// 出现时淡入下落，参数和折叠区相同，直接用 `.disclosure-body`（高保真稿 Spec-Form 07）。
export function FormAlert({ children }: { children: ReactNode }) {
  return (
    <div
      role="alert"
      className="disclosure-body flex items-start gap-2.5 rounded-lg bg-status-danger-bg px-3.5 py-3 text-base leading-5 text-fg-1"
    >
      <CircleAlert className="mt-0.5 size-4 shrink-0 text-status-danger" aria-hidden="true" />
      <span className="min-w-0">{children}</span>
    </div>
  );
}
```

自动填充后的底色写在 `globals.css` 的 base 层。这是浏览器自带的样式，jsdom 里测不到，Task 19 在真实浏览器里验收：

```diff
diff --git a/patra-portal/src/app/globals.css b/patra-portal/src/app/globals.css
--- a/patra-portal/src/app/globals.css
+++ b/patra-portal/src/app/globals.css
@@ -225,6 +225,17 @@
   input[type="search"]::-webkit-search-cancel-button {
     appearance: none;
   }
+  /* 浏览器自动填充后保持输入框的底色，不出现浏览器自带的黄 / 蓝底（照高保真稿 patra.css）。
+     登录、注册提交中输入框只读，外框换成凹陷色，自动填充过的输入框跟着换 */
+  input:-webkit-autofill {
+    -webkit-box-shadow: 0 0 0 1000px var(--bg-elevated) inset;
+    -webkit-text-fill-color: var(--ink-900);
+    caret-color: var(--ink-900);
+  }
+  input:-webkit-autofill:read-only {
+    -webkit-box-shadow: 0 0 0 1000px var(--bg-sunken) inset;
+    -webkit-text-fill-color: var(--fg-2);
+  }
 }

 /* ============================================================
```

- [ ] **Step 8: 跑测试，确认通过**

组件的类名同样要过守护测试：

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/components/auth tests/design-system`
Expected: PASS，`Test Files  12 passed (12)`，`Tests  59 passed (59)`。

- [ ] **Step 9: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write src/lib/portal-ui.ts src/app/globals.css src/components/auth/FormField.tsx src/components/auth/PasswordInput.tsx src/components/auth/FormAlert.tsx tests/lib/portal-ui.test.ts tests/components/auth/FormField.test.tsx tests/components/auth/PasswordInput.test.tsx tests/components/auth/FormAlert.test.tsx
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0，`lint` 输出 `Checked 270 files`；`Test Files  102 passed (102)`、`Tests  924 passed (924)`。

- [ ] **Step 10: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/src/lib/portal-ui.ts patra-portal/src/app/globals.css patra-portal/src/components/auth/FormField.tsx patra-portal/src/components/auth/PasswordInput.tsx patra-portal/src/components/auth/FormAlert.tsx patra-portal/tests/lib/portal-ui.test.ts patra-portal/tests/components/auth/FormField.test.tsx patra-portal/tests/components/auth/PasswordInput.test.tsx patra-portal/tests/components/auth/FormAlert.test.tsx
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
feat(portal): 表单字段、密码框和提示条，按钮支持 aria-disabled (PAP-68)

照高保真稿 Spec-Form：FormField 负责标签、aria-invalid 和 aria-describedby，错误替换常驻提示；
输入框外框的聚焦、出错、只读三种状态都按输入框判断。PasswordInput 切换显示时光标和选区不变。
FormAlert 是 role="alert" 的提示条。提交中的按钮用 aria-disabled，样式和禁用态相同。浏览器
自动填充后保持纸色底。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这 9 个文件；pre-commit 全部通过。

---

### Task 7: 登录表单和两张表单共用的提交流程

**Files:**
- Modify: `patra-portal/src/lib/auth/messages.ts`（加 `fieldErrorMessages`）
- Create: `patra-portal/src/components/auth/AuthLink.tsx`、`use-auth-submit.ts`、`LoginForm.tsx`
- Test: `patra-portal/tests/lib/auth/messages.test.ts`（加用例）、`patra-portal/tests/components/auth/LoginForm.test.tsx`

**Interfaces:**
- Consumes: Task 1 `authPageHref`；Task 4 `loginSchema`、`loginAlertMessage`、`fieldErrorMessage`、`TOAST_MESSAGES`；Task 6 `FormField`、`PasswordInput`、`FormAlert`、`btnPrimary`、`btnBlock`；PAP-67 `login(input: Credentials): Promise<LoginResult>`、`queryKeys.currentUser`、`Credentials`、`CurrentUser`、`FieldError`。
- Produces:
  - `fieldErrorMessages(fieldErrors: FieldError[]): Partial<Record<"email" | "password", string>>`。
  - `AuthLink({ href, replace?, drawn?, children })`：登录、注册相关的文字链接。Task 14 加 `onClick?`。
  - `useAuthSubmit<F extends { ok: false }>({ next, action, successToast, onFailure })`，返回 `{ submitting: boolean; submit(credentials: Credentials): Promise<void> }`。`onFailure(failure, credentials)` 拿到失败结果（含 promise 被拒绝时的 `{ ok: false, reason: "unavailable" }`）和这次提交的值。
  - `useServerFieldErrors()`，返回 `{ messageFor(field, value), show(field, value, message), showFieldErrors(fieldErrors, credentials), clear() }`；`field` 是 `"email" | "password"`，`showFieldErrors` 返回第一个出错的字段，一条都对不上时返回 null。
  - `LoginForm({ next }: { next: string })`，`next` 已经过 `safeNextPath`。
  - Task 8 的注册表单复用 `AuthLink`、`useAuthSubmit`、`useServerFieldErrors`；Task 9 的登录页渲染 `LoginForm`；Task 10、11 再改它。

- [ ] **Step 1: 写失败的测试：字段错误的文案**

```diff
diff --git a/patra-portal/tests/lib/auth/messages.test.ts b/patra-portal/tests/lib/auth/messages.test.ts
--- a/patra-portal/tests/lib/auth/messages.test.ts
+++ b/patra-portal/tests/lib/auth/messages.test.ts
@@ -3,6 +3,7 @@ import { describe, expect, it } from "vitest";
 import {
   FIELD_MESSAGES,
   fieldErrorMessage,
+  fieldErrorMessages,
   loginAlertMessage,
   REGISTER_UNAVAILABLE_MESSAGE,
   TOAST_MESSAGES,
@@ -37,6 +38,23 @@ describe("fieldErrorMessage", () => {
   });
 });

+describe("fieldErrorMessages", () => {
+  it("同一字段只取第一条，email、password 以外的字段丢掉", () => {
+    expect(
+      fieldErrorMessages([
+        { field: "deviceId", code: "REQUIRED" },
+        { field: "password", code: "TOO_SHORT" },
+        { field: "password", code: "REQUIRED" },
+        { field: "email", code: null },
+      ]),
+    ).toEqual({ password: "密码至少 8 位", email: "邮箱格式不正确" });
+  });
+
+  it("一条都对不上 email、password 时返回空对象", () => {
+    expect(fieldErrorMessages([{ field: "deviceId", code: "REQUIRED" }])).toEqual({});
+  });
+});
+
 describe("loginAlertMessage", () => {
   it.each([
     [{ ok: false, reason: "bad-credentials" } as const, "邮箱或密码错误"],
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/lib/auth/messages.test.ts`
Expected: FAIL，`Tests  2 failed | 24 passed (26)`，失败原因是 `TypeError: fieldErrorMessages is not a function`。

- [ ] **Step 3: 写实现**

```diff
diff --git a/patra-portal/src/lib/auth/messages.ts b/patra-portal/src/lib/auth/messages.ts
--- a/patra-portal/src/lib/auth/messages.ts
+++ b/patra-portal/src/lib/auth/messages.ts
@@ -1,4 +1,4 @@
-import type { LoginResult } from "@/lib/auth/types";
+import type { FieldError, LoginResult } from "@/lib/auth/types";

 /// 字段下方的错误文案（简报 §4.1、§4.2）。前端校验和后端的原因码共用。
 export const FIELD_MESSAGES = {
@@ -46,6 +46,20 @@ export function fieldErrorMessage(field: "email" | "password", code: string | nu
   }
 }

+/// `invalid-fields` 里的字段错误 → 各字段下方的文案。同一字段只取第一条；`email`、`password`
+/// 以外的字段丢掉，一条都对不上时返回空对象。
+export function fieldErrorMessages(
+  fieldErrors: FieldError[],
+): Partial<Record<"email" | "password", string>> {
+  const messages: Partial<Record<"email" | "password", string>> = {};
+  for (const { field, code } of fieldErrors) {
+    if ((field === "email" || field === "password") && messages[field] === undefined) {
+      messages[field] = fieldErrorMessage(field, code);
+    }
+  }
+  return messages;
+}
+
 /// 登录失败时用提示条显示的结果。`invalid-fields` 显示在字段下方，不在这里。
 export type LoginAlertFailure = Exclude<LoginResult, { ok: true } | { reason: "invalid-fields" }>;

```

- [ ] **Step 4: 跑测试，确认通过**

Run: 同 Step 2。
Expected: PASS，`Tests  26 passed (26)`。

- [ ] **Step 5: 写失败的测试：登录表单**

两张表单共用的提交流程（防重复提交、预取、先取消再写缓存、卸载后照样跳转）都在这里逐条测，Task 8 只测注册特有的部分。新建 `patra-portal/tests/components/auth/LoginForm.test.tsx`：

```tsx
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { LoginForm } from "@/components/auth/LoginForm";
import { login } from "@/lib/auth/actions";
import type { LoginResult } from "@/lib/auth/types";
import { queryKeys } from "@/lib/query-keys";

const router = { replace: vi.fn(), prefetch: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/auth/actions", () => ({ login: vi.fn(), logout: vi.fn(), register: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn() } }));

import { toast } from "sonner";

const NEXT = "/papers?q=GLP-1";
const USER = { userId: "319041872872550658", email: "chen.yu@example.com" };

/// 一个还没回来的请求，`answer` 让它在测试想要的时候回来
function deferred<T>() {
  let answer: (value: T) => void = () => {};
  const promise = new Promise<T>((resolve) => {
    answer = resolve;
  });
  return { promise, answer };
}

function renderLogin() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const user = userEvent.setup();
  const view = render(
    <QueryClientProvider client={client}>
      <LoginForm next={NEXT} />
    </QueryClientProvider>,
  );
  const email = screen.getByLabelText("邮箱") as HTMLInputElement;
  const password = screen.getByLabelText("密码") as HTMLInputElement;
  const submit = screen.getByRole("button", { name: "登录" });
  return { client, user, view, email, password, submit };
}

/// 填好邮箱和密码后提交
async function fillAndSubmit(form: ReturnType<typeof renderLogin>, email = USER.email) {
  await form.user.type(form.email, email);
  await form.user.type(form.password, "correct horse");
  await form.user.click(form.submit);
}

beforeEach(() => {
  vi.mocked(login).mockResolvedValue({ ok: true, user: USER });
});

afterEach(() => {
  vi.clearAllMocks();
  vi.unstubAllGlobals();
});

describe("LoginForm · 输入框", () => {
  it("进入时焦点在邮箱框", () => {
    const { email } = renderLogin();
    expect(email).toHaveFocus();
  });

  it("浏览器和密码管理器能识别：邮箱是用户名，密码是当前密码；不做拼写检查", () => {
    const { email, password } = renderLogin();
    expect(email).toHaveAttribute("type", "email");
    expect(email).toHaveAttribute("autocomplete", "username");
    expect(email).toHaveAttribute("spellcheck", "false");
    expect(email).toHaveAttribute("placeholder", "name@example.com");
    expect(password).toHaveAttribute("type", "password");
    expect(password).toHaveAttribute("autocomplete", "current-password");
    expect(password).toHaveAttribute("spellcheck", "false");
  });

  it("去注册的链接带上 next", () => {
    renderLogin();
    expect(screen.getByRole("link", { name: "注册" })).toHaveAttribute(
      "href",
      "/register?next=%2Fpapers%3Fq%3DGLP-1",
    );
  });
});

describe("LoginForm · 校验时机", () => {
  it("输入时不校验，离开字段时校验它", async () => {
    const { user, email } = renderLogin();
    await user.type(email, "reader");
    expect(screen.queryByText("邮箱格式不正确")).not.toBeInTheDocument();
    await user.tab();
    expect(email).toHaveAccessibleDescription("邮箱格式不正确");
    expect(email).toHaveAttribute("aria-invalid", "true");
  });

  it("显示错误之后随输入重新校验，改对了错误立即消失", async () => {
    const { user, email } = renderLogin();
    await user.type(email, "reader");
    await user.tab();
    await user.type(email, "@example.com");
    expect(screen.queryByText("邮箱格式不正确")).not.toBeInTheDocument();
    expect(email).toHaveAttribute("aria-invalid", "false");
  });

  it("提交时校验全部字段，焦点移到第一个出错的字段，不请求", async () => {
    const { user, email, password, submit } = renderLogin();
    await user.click(submit);
    expect(email).toHaveAccessibleDescription("请输入邮箱");
    expect(password).toHaveAccessibleDescription("请输入密码");
    expect(email).toHaveFocus();
    expect(login).not.toHaveBeenCalled();
  });

  it("提交之后，没报错的字段仍然只在离开时校验", async () => {
    const { user, email, password, submit } = renderLogin();
    await user.type(email, USER.email);
    await user.click(submit);
    expect(password).toHaveAccessibleDescription("请输入密码");
    await user.clear(email);
    await user.type(email, "x");
    expect(screen.queryByText("邮箱格式不正确")).not.toBeInTheDocument();
    await user.tab();
    expect(screen.getByText("邮箱格式不正确")).toBeInTheDocument();
  });

  it("在输入框里回车即提交；邮箱去掉首尾空白，密码原样", async () => {
    const { user, email, password } = renderLogin();
    await user.type(email, `  ${USER.email} `);
    await user.type(password, " pass word {Enter}");
    await waitFor(() => expect(login).toHaveBeenCalledTimes(1));
    expect(login).toHaveBeenCalledWith({ email: USER.email, password: " pass word " });
  });
});

describe("LoginForm · 后端返回失败", () => {
  it.each<[string, LoginResult, string]>([
    ["邮箱或密码错误", { ok: false, reason: "bad-credentials" }, "邮箱或密码错误"],
    [
      "限流 61 秒",
      { ok: false, reason: "throttled", retryAfterSeconds: 61 },
      "尝试次数过多，请 2 分钟后再试",
    ],
    ["封禁", { ok: false, reason: "banned" }, "该账号已被封禁，无法登录"],
    ["服务不可用", { ok: false, reason: "unavailable" }, "登录没有成功，请稍后再试"],
    [
      "字段名一条都对不上",
      {
        ok: false,
        reason: "invalid-fields",
        fieldErrors: [{ field: "deviceId", code: "REQUIRED" }],
      },
      "登录没有成功，请稍后再试",
    ],
  ])("%s：提示条，保留邮箱、清空密码，焦点回到密码框", async (_label, result, message) => {
    vi.mocked(login).mockResolvedValue(result);
    const form = renderLogin();
    await fillAndSubmit(form);
    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(form.email).toHaveValue(USER.email);
    expect(form.password).toHaveValue("");
    expect(form.password).toHaveFocus();
    expect(form.submit).not.toHaveAttribute("aria-disabled");
  });

  it("action 的 promise 被拒绝：按服务不可用处理", async () => {
    vi.mocked(login).mockRejectedValue(new Error("network"));
    const form = renderLogin();
    await fillAndSubmit(form);
    expect(await screen.findByRole("alert")).toHaveTextContent("登录没有成功，请稍后再试");
  });

  it("字段错误：显示在字段下方，同一字段只显示第一条，焦点移到第一个出错的字段", async () => {
    vi.mocked(login).mockResolvedValue({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [
        { field: "password", code: "REQUIRED" },
        { field: "password", code: "TOO_LONG" },
        { field: "email", code: "INVALID_FORMAT" },
      ],
    });
    const form = renderLogin();
    await fillAndSubmit(form);
    await waitFor(() => expect(form.email).toHaveAccessibleDescription("邮箱格式不正确"));
    expect(form.password).toHaveAccessibleDescription("请输入密码");
    expect(form.email).toHaveFocus();
    expect(form.password).toHaveValue("correct horse");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("后端的字段错误跟着值走：离开字段不会消失，值一改就消失（邮箱按去掉空白后的值比）", async () => {
    vi.mocked(login).mockResolvedValue({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [{ field: "email", code: "INVALID_FORMAT" }],
    });
    const form = renderLogin();
    await fillAndSubmit(form);
    await waitFor(() => expect(form.email).toHaveAccessibleDescription("邮箱格式不正确"));
    await form.user.tab();
    expect(form.email).toHaveAccessibleDescription("邮箱格式不正确");
    await form.user.type(form.email, " ");
    expect(form.email).toHaveAccessibleDescription("邮箱格式不正确");
    await form.user.type(form.email, "{Backspace}{Backspace}");
    expect(form.email).toHaveValue("chen.yu@example.co");
    expect(form.email).not.toHaveAccessibleDescription("邮箱格式不正确");
    expect(form.email).toHaveAttribute("aria-invalid", "false");
  });

  it("再次提交时，后端的字段错误也先清掉", async () => {
    vi.mocked(login).mockResolvedValue({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [{ field: "email", code: "INVALID_FORMAT" }],
    });
    const form = renderLogin();
    await fillAndSubmit(form);
    await waitFor(() => expect(form.email).toHaveAccessibleDescription("邮箱格式不正确"));
    vi.mocked(login).mockReturnValue(deferred<LoginResult>().promise);
    await form.user.click(form.submit);
    expect(screen.getByRole("button", { name: "登录中…" })).toBeInTheDocument();
    expect(form.email).toHaveAttribute("aria-invalid", "false");
  });

  it("再次提交时先清掉上一次的提示条；同样的错误再出现，提示条是新插入的", async () => {
    vi.mocked(login).mockResolvedValue({ ok: false, reason: "bad-credentials" });
    const form = renderLogin();
    await fillAndSubmit(form);
    const first = await screen.findByRole("alert");
    const pending = deferred<LoginResult>();
    vi.mocked(login).mockReturnValue(pending.promise);
    await form.user.type(form.password, "another try");
    await form.user.click(form.submit);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    await act(async () => pending.answer({ ok: false, reason: "bad-credentials" }));
    const second = await screen.findByRole("alert");
    expect(second).toHaveTextContent("邮箱或密码错误");
    expect(second).not.toBe(first);
  });
});

describe("LoginForm · 提交中", () => {
  it("字段只读，按钮 aria-disabled 并换文案，焦点留在按钮上", async () => {
    const pending = deferred<LoginResult>();
    vi.mocked(login).mockReturnValue(pending.promise);
    const form = renderLogin();
    await fillAndSubmit(form);
    const busy = screen.getByRole("button", { name: "登录中…" });
    expect(busy).toHaveAttribute("aria-disabled", "true");
    expect(busy).not.toBeDisabled();
    expect(busy).toHaveAttribute("title", "正在登录");
    expect(busy).toHaveFocus();
    expect(form.email).toHaveAttribute("readonly");
    expect(form.password).toHaveAttribute("readonly");
  });

  it("连点、在输入框里回车都只发一次请求", async () => {
    const pending = deferred<LoginResult>();
    vi.mocked(login).mockReturnValue(pending.promise);
    const form = renderLogin();
    await fillAndSubmit(form);
    await form.user.click(screen.getByRole("button", { name: "登录中…" }));
    await form.user.type(form.password, "{Enter}");
    expect(login).toHaveBeenCalledTimes(1);
  });

  it("还没来得及显示提交中就再次提交，也只发一次请求", async () => {
    vi.mocked(login).mockReturnValue(deferred<LoginResult>().promise);
    const form = renderLogin();
    await form.user.type(form.email, USER.email);
    await form.user.type(form.password, "correct horse");
    const element = form.submit.closest("form") as HTMLFormElement;
    fireEvent.submit(element);
    fireEvent.submit(element);
    await waitFor(() => expect(login).toHaveBeenCalled());
    await act(async () => {});
    expect(login).toHaveBeenCalledTimes(1);
  });
});

describe("LoginForm · 成功", () => {
  it("先预取目标页再请求；成功后写入当前用户、提示「已登录」、替换到 next", async () => {
    const form = renderLogin();
    await fillAndSubmit(form);
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith(NEXT));
    expect(router.prefetch).toHaveBeenCalledWith(NEXT);
    expect(vi.mocked(router.prefetch).mock.invocationCallOrder[0]).toBeLessThan(
      vi.mocked(login).mock.invocationCallOrder[0] ?? 0,
    );
    expect(form.client.getQueryData(queryKeys.currentUser)).toEqual(USER);
    expect(toast.success).toHaveBeenCalledWith("已登录");
  });

  it("跳转完成前一直显示提交中，不能再提交", async () => {
    const form = renderLogin();
    await fillAndSubmit(form);
    await waitFor(() => expect(router.replace).toHaveBeenCalled());
    const busy = screen.getByRole("button", { name: "登录中…" });
    expect(busy).toHaveAttribute("aria-disabled", "true");
    await form.user.click(busy);
    expect(login).toHaveBeenCalledTimes(1);
  });

  it("成功前发出、成功后才回来的 /api/auth/me 不会把用户覆盖成 null", async () => {
    const late = { ok: true, json: vi.fn(async () => ({ user: null })) };
    const me = deferred<typeof late>();
    vi.stubGlobal(
      "fetch",
      vi.fn(() => me.promise),
    );
    const form = renderLogin();
    act(() => {
      void form.client.prefetchQuery({
        queryKey: queryKeys.currentUser,
        queryFn: async () => ((await (await fetch("/api/auth/me")).json()) as { user: null }).user,
      });
    });
    await fillAndSubmit(form);
    await waitFor(() => expect(router.replace).toHaveBeenCalled());
    me.answer(late);
    await waitFor(() => expect(late.json).toHaveBeenCalled());
    await act(async () => {});
    expect(form.client.getQueryData(queryKeys.currentUser)).toEqual(USER);
  });

  it("提交中离开了页面（表单被卸载），请求成功后仍然写入、提示、跳转", async () => {
    const pending = deferred<LoginResult>();
    vi.mocked(login).mockReturnValue(pending.promise);
    const form = renderLogin();
    await fillAndSubmit(form);
    form.view.unmount();
    await act(async () => pending.answer({ ok: true, user: USER }));
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith(NEXT));
    expect(form.client.getQueryData(queryKeys.currentUser)).toEqual(USER);
    expect(toast.success).toHaveBeenCalledWith("已登录");
  });

  it("提交中离开了页面，之后请求失败：什么也不做，也不报错", async () => {
    const pending = deferred<LoginResult>();
    vi.mocked(login).mockReturnValue(pending.promise);
    const form = renderLogin();
    await fillAndSubmit(form);
    form.view.unmount();
    await act(async () => pending.answer({ ok: false, reason: "bad-credentials" }));
    expect(router.replace).not.toHaveBeenCalled();
    expect(toast.success).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 6: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/components/auth/LoginForm.test.tsx`
Expected: FAIL，`Error: Failed to resolve import "@/components/auth/LoginForm" from "tests/components/auth/LoginForm.test.tsx". Does the file exist?`，`Tests  no tests`。

- [ ] **Step 7: 写实现**

新建 `patra-portal/src/components/auth/AuthLink.tsx`：

```tsx
import Link from "next/link";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

interface AuthLinkProps {
  href: string;
  /// 替换当前这条历史记录：登录页、注册页里的站内链接都替换（spec 第 6 节）
  replace?: boolean;
  /// 下划线常驻，不只在悬停、键盘聚焦时画出：错误文案里的「去登录」
  drawn?: boolean;
  children: ReactNode;
}

/// 登录、注册相关的文字链接：「注册」「登录」「去登录」。悬停和键盘聚焦时下划线自左画出：
/// 下划线画在里层的 span 上，由外层链接的 hover / focus-visible 触发。移动端上下各加 12px
/// 点按区，又不撑高这一行。
export function AuthLink({ href, replace, drawn, children }: AuthLinkProps) {
  return (
    <Link
      href={href}
      replace={replace}
      className="group/link rounded-xs font-medium text-link hover:text-link-hover max-[880px]:-my-3 max-[880px]:inline-block max-[880px]:py-3"
    >
      <span
        className={cn(
          "link-draw pb-px group-hover/link:[background-size:100%_1px] group-focus-visible/link:[background-size:100%_1px]",
          drawn && "[background-size:100%_1px]",
        )}
      >
        {children}
      </span>
    </Link>
  );
}
```

新建 `patra-portal/src/components/auth/use-auth-submit.ts`：

```ts
import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { type ReactNode, startTransition, useRef, useState } from "react";
import { toast } from "sonner";
import { fieldErrorMessages } from "@/lib/auth/messages";
import type { Credentials, CurrentUser, FieldError } from "@/lib/auth/types";
import { queryKeys } from "@/lib/query-keys";

/// action 的 promise 被拒绝时，按服务不可用处理
const UNAVAILABLE = { ok: false, reason: "unavailable" } as const;

interface AuthSubmitOptions<F> {
  /// 成功后跳去的地址，已经过 `safeNextPath`
  next: string;
  /// `login` 或 `register`
  action: (credentials: Credentials) => Promise<{ ok: true; user: CurrentUser } | F>;
  /// 成功提示
  successToast: string;
  /// 失败时调用，拿到失败结果和这次提交的值
  onFailure: (failure: F | typeof UNAVAILABLE, credentials: Credentials) => void;
}

/// 两张表单共用的提交流程，表单通过校验后调用 `submit`（spec 第 9.5、9.6 节）：
/// 1. 提交中再次调用一律忽略，只发一次请求。
/// 2. 先预取目标页，再调用 action。promise 被拒绝时按服务不可用处理。
/// 3. 成功：先取消进行中的当前用户请求，再写入用户，弹成功提示，在 transition 里替换到 `next`。
///    之后一直保持提交中，直到跳转完成、表单卸载。表单提前卸载了，这几步照样做完。
/// 4. 失败：退出提交中，交给 `onFailure`。
export function useAuthSubmit<F extends { ok: false }>({
  next,
  action,
  successToast,
  onFailure,
}: AuthSubmitOptions<F>) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const busy = useRef(false);
  const [submitting, setSubmitting] = useState(false);

  async function submit(credentials: Credentials) {
    if (busy.current) {
      return;
    }
    busy.current = true;
    setSubmitting(true);
    router.prefetch(next);
    const result = await action(credentials).catch(() => UNAVAILABLE);
    if (result.ok) {
      await queryClient.cancelQueries({ queryKey: queryKeys.currentUser });
      queryClient.setQueryData(queryKeys.currentUser, result.user);
      toast.success(successToast);
      startTransition(() => router.replace(next));
      return;
    }
    busy.current = false;
    setSubmitting(false);
    onFailure(result, credentials);
  }

  return { submitting, submit };
}

/// 有后端字段错误的两个字段，按页面上的顺序
const CREDENTIAL_FIELDS = ["email", "password"] as const;
type CredentialField = (typeof CREDENTIAL_FIELDS)[number];

/// 后端返回的一个字段错误，和提交时字段的值绑在一起
interface ServerFieldError {
  value: string;
  message: ReactNode;
}

/// 后端返回的字段错误放在 RHF 之外（spec 第 9.3 节）：字段的值和提交时相同就一直显示，值一改立即
/// 消失。放进 RHF 的错误里不行：用户一移出焦点，失焦校验就会把它清掉。
export function useServerFieldErrors() {
  const [errors, setErrors] = useState<Partial<Record<CredentialField, ServerFieldError>>>({});
  return {
    /// 字段当前的值（邮箱传去掉空白后的值）和提交时相同时，返回它的错误文案
    messageFor(field: CredentialField, value: string): ReactNode {
      const error = errors[field];
      return error?.value === value ? error.message : undefined;
    },
    /// 在一个字段下方显示错误，比如注册时的「该邮箱已注册」
    show(field: CredentialField, value: string, message: ReactNode) {
      setErrors({ [field]: { value, message } });
    },
    /// 显示 `invalid-fields` 里的字段错误，返回第一个出错的字段。一条都对不上时什么也不显示，返回 null
    showFieldErrors(fieldErrors: FieldError[], credentials: Credentials): CredentialField | null {
      const messages = fieldErrorMessages(fieldErrors);
      const shown: Partial<Record<CredentialField, ServerFieldError>> = {};
      for (const field of CREDENTIAL_FIELDS) {
        const message = messages[field];
        if (message !== undefined) {
          shown[field] = { value: credentials[field], message };
        }
      }
      setErrors(shown);
      return CREDENTIAL_FIELDS.find((field) => shown[field] !== undefined) ?? null;
    },
    clear() {
      setErrors({});
    },
  };
}
```

新建 `patra-portal/src/components/auth/LoginForm.tsx`：

```tsx
"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { type FormEvent, useState } from "react";
import { useForm } from "react-hook-form";
import { AuthLink } from "@/components/auth/AuthLink";
import { FormAlert } from "@/components/auth/FormAlert";
import { FormField } from "@/components/auth/FormField";
import { PasswordInput } from "@/components/auth/PasswordInput";
import { useAuthSubmit, useServerFieldErrors } from "@/components/auth/use-auth-submit";
import { login } from "@/lib/auth/actions";
import { loginSchema } from "@/lib/auth/form-schemas";
import { loginAlertMessage, TOAST_MESSAGES } from "@/lib/auth/messages";
import { authPageHref } from "@/lib/auth/next-path";
import { btnBlock, btnPrimary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

/// 登录表单（spec 第 9 节）：提示条、邮箱、密码、提交按钮、去注册的链接。`next` 已经过 `safeNextPath`。
export function LoginForm({ next }: { next: string }) {
  const [alert, setAlert] = useState<string | null>(null);
  const serverErrors = useServerFieldErrors();
  const {
    register,
    handleSubmit,
    formState: { errors },
    getFieldState,
    trigger,
    setValue,
    setFocus,
    watch,
  } = useForm({
    resolver: zodResolver(loginSchema),
    mode: "onBlur",
    reValidateMode: "onBlur",
    defaultValues: { email: "", password: "" },
  });
  const { submitting, submit } = useAuthSubmit({
    next,
    action: login,
    successToast: TOAST_MESSAGES.loggedIn,
    onFailure: (failure, credentials) => {
      if (failure.reason === "invalid-fields") {
        const first = serverErrors.showFieldErrors(failure.fieldErrors, credentials);
        if (first) {
          setFocus(first);
          return;
        }
      }
      // 字段名一条都对不上时，按服务不可用处理
      setAlert(
        loginAlertMessage(
          failure.reason === "invalid-fields" ? { ok: false, reason: "unavailable" } : failure,
        ),
      );
      setValue("password", "");
      setFocus("password");
    },
  });

  /// 字段显示错误之后，每次输入都重新校验它，改对了错误立即消失（spec 第 9.3 节）
  const revalidateOnChange = (field: "email" | "password") => ({
    onChange: () => {
      if (getFieldState(field).error) {
        void trigger(field);
      }
    },
  });

  const [email, password] = watch(["email", "password"]);
  const emailError = errors.email?.message ?? serverErrors.messageFor("email", email.trim());
  const passwordError = errors.password?.message ?? serverErrors.messageFor("password", password);

  /// 每次提交先清掉提示条和后端字段错误，再校验、请求。提交中再次提交由 `submit` 忽略
  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAlert(null);
    serverErrors.clear();
    void handleSubmit((values) => submit(values))(event);
  }

  return (
    <>
      {/* 副文案到表单 32px；有提示条时，副文案到提示条 24px，提示条到第一个字段 20px */}
      <form
        noValidate
        onSubmit={onSubmit}
        className={cn("flex flex-col gap-5", alert ? "mt-6" : "mt-8")}
      >
        {alert && <FormAlert>{alert}</FormAlert>}
        <FormField id="login-email" label="邮箱" error={emailError}>
          {(control) => (
            <input
              {...control}
              {...register("email", revalidateOnChange("email"))}
              type="email"
              autoComplete="username"
              spellCheck={false}
              placeholder="name@example.com"
              readOnly={submitting}
              // biome-ignore lint/a11y/noAutofocus: 简报 §6 要求进入登录页时焦点在邮箱框
              autoFocus
            />
          )}
        </FormField>
        <FormField id="login-password" label="密码" error={passwordError}>
          {(control) => (
            <PasswordInput
              {...control}
              {...register("password", revalidateOnChange("password"))}
              autoComplete="current-password"
              spellCheck={false}
              readOnly={submitting}
            />
          )}
        </FormField>
        <div className="mt-2">
          <button
            type="submit"
            aria-disabled={submitting || undefined}
            title={submitting ? "正在登录" : undefined}
            className={cn(btnPrimary, btnBlock)}
          >
            {submitting ? "登录中…" : "登录"}
          </button>
        </div>
      </form>
      <p className="mt-5 text-base text-fg-2 leading-5">
        还没有账号？
        <AuthLink replace href={authPageHref("/register", next)}>
          注册
        </AuthLink>
      </p>
    </>
  );
}
```

- [ ] **Step 8: 跑测试，确认通过**

Run: 同 Step 6。
Expected: PASS，`Tests  26 passed (26)`。

- [ ] **Step 9: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write src/lib/auth/messages.ts src/components/auth/AuthLink.tsx src/components/auth/use-auth-submit.ts src/components/auth/LoginForm.tsx tests/lib/auth/messages.test.ts tests/components/auth/LoginForm.test.tsx
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0，`lint` 输出 `Checked 274 files`；`Test Files  103 passed (103)`、`Tests  952 passed (952)`。

- [ ] **Step 10: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/src/lib/auth/messages.ts patra-portal/src/components/auth/AuthLink.tsx patra-portal/src/components/auth/use-auth-submit.ts patra-portal/src/components/auth/LoginForm.tsx patra-portal/tests/lib/auth/messages.test.ts patra-portal/tests/components/auth/LoginForm.test.tsx
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
feat(portal): 登录表单和两张表单共用的提交流程 (PAP-68)

提交流程：提交中再次提交一律忽略；先预取目标页再调 action，promise 被拒绝按服务不可用处理；
成功后先取消进行中的当前用户请求再写缓存，弹提示，在 transition 里替换到 next，表单卸载了也
照样做完。后端的字段错误放在 RHF 之外，跟着提交时的值走。登录失败保留邮箱、清空密码、焦点
回到密码框。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这 6 个文件；pre-commit 全部通过。

---

### Task 8: 注册表单

**Files:**
- Create: `patra-portal/src/components/auth/RegisterForm.tsx`
- Test: `patra-portal/tests/components/auth/RegisterForm.test.tsx`

**Interfaces:**
- Consumes: Task 7 `AuthLink`、`useAuthSubmit`、`useServerFieldErrors`；Task 4 `registerSchema`、`FIELD_MESSAGES`、`REGISTER_UNAVAILABLE_MESSAGE`、`TOAST_MESSAGES`；Task 6 的组件和按钮样式；Task 1 `authPageHref`；PAP-67 `register(input: Credentials): Promise<RegisterResult>`。
- Produces: `RegisterForm({ next }: { next: string })`。Task 9 的注册页渲染它；Task 10、11 再改它。

- [ ] **Step 1: 写失败的测试**

新建 `patra-portal/tests/components/auth/RegisterForm.test.tsx`：

```tsx
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { RegisterForm } from "@/components/auth/RegisterForm";
import { register } from "@/lib/auth/actions";
import type { RegisterResult } from "@/lib/auth/types";
import { queryKeys } from "@/lib/query-keys";

const router = { replace: vi.fn(), prefetch: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/auth/actions", () => ({ login: vi.fn(), logout: vi.fn(), register: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn() } }));

import { toast } from "sonner";

const NEXT = "/journals/7";
const USER = { userId: "319041872872550658", email: "chen.yu@example.com" };
const PASSWORD = "correct horse";

/// 一个还没回来的请求，`answer` 让它在测试想要的时候回来
function deferred<T>() {
  let answer: (value: T) => void = () => {};
  const promise = new Promise<T>((resolve) => {
    answer = resolve;
  });
  return { promise, answer };
}

function renderRegister() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const user = userEvent.setup();
  render(
    <QueryClientProvider client={client}>
      <RegisterForm next={NEXT} />
    </QueryClientProvider>,
  );
  const email = screen.getByLabelText("邮箱") as HTMLInputElement;
  const password = screen.getByLabelText("密码") as HTMLInputElement;
  const confirm = screen.getByLabelText("确认密码") as HTMLInputElement;
  const submit = screen.getByRole("button", { name: "注册" });
  return { client, user, email, password, confirm, submit };
}

/// 三个字段都填好后点「注册」。邮箱前后带空格：type="email" 的输入框读出的值不带首尾空白
async function fillAndSubmit(form: ReturnType<typeof renderRegister>) {
  await form.user.type(form.email, ` ${USER.email} `);
  await form.user.type(form.password, PASSWORD);
  await form.user.type(form.confirm, PASSWORD);
  await form.user.click(form.submit);
}

beforeEach(() => {
  vi.mocked(register).mockResolvedValue({ ok: true, user: USER });
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("RegisterForm · 输入框", () => {
  it("进入时焦点在邮箱框", () => {
    const { email } = renderRegister();
    expect(email).toHaveFocus();
  });

  it("浏览器和密码管理器能识别：邮箱是 email，两个密码框是新密码；不做拼写检查", () => {
    const { email, password, confirm } = renderRegister();
    expect(email).toHaveAttribute("type", "email");
    expect(email).toHaveAttribute("autocomplete", "email");
    expect(email).toHaveAttribute("placeholder", "name@example.com");
    for (const input of [email, password, confirm]) {
      expect(input).toHaveAttribute("spellcheck", "false");
    }
    for (const input of [password, confirm]) {
      expect(input).toHaveAttribute("type", "password");
      expect(input).toHaveAttribute("autocomplete", "new-password");
    }
  });

  it("密码下方常驻「至少 8 位」，出错时被错误文案替换", async () => {
    const { user, password } = renderRegister();
    expect(password).toHaveAccessibleDescription("至少 8 位");
    await user.type(password, "short");
    await user.tab();
    expect(password).toHaveAccessibleDescription("密码至少 8 位");
    expect(screen.queryByText("至少 8 位")).not.toBeInTheDocument();
  });

  it("提交按钮上方提示不支持找回密码", () => {
    renderRegister();
    expect(screen.getByText("请牢记密码，目前不支持找回密码。")).toBeInTheDocument();
  });

  it("两个显示 / 隐藏切换互不影响", async () => {
    const { user, password, confirm } = renderRegister();
    const [passwordToggle] = screen.getAllByRole("button", { name: "显示密码" });
    await user.click(passwordToggle as HTMLElement);
    expect(password).toHaveAttribute("type", "text");
    expect(confirm).toHaveAttribute("type", "password");
  });

  it("去登录的链接带上 next", () => {
    renderRegister();
    expect(screen.getByRole("link", { name: "登录" })).toHaveAttribute(
      "href",
      "/login?next=%2Fjournals%2F7",
    );
  });
});

describe("RegisterForm · 确认密码", () => {
  it("为空时提示再输入一次，和密码不同时提示不一致", async () => {
    const { user, password, confirm, submit } = renderRegister();
    await user.type(password, PASSWORD);
    await user.click(submit);
    expect(confirm).toHaveAccessibleDescription("请再输入一次密码");
    await user.type(confirm, "correct horsf");
    await user.tab();
    expect(confirm).toHaveAccessibleDescription("两次输入的密码不一致");
  });

  it("确认密码不为空时，改密码会立即重新比对", async () => {
    const { user, password, confirm } = renderRegister();
    await user.type(password, PASSWORD);
    await user.type(confirm, PASSWORD);
    await user.type(password, "!");
    expect(confirm).toHaveAccessibleDescription("两次输入的密码不一致");
    await user.type(password, "{Backspace}");
    expect(confirm).toHaveAttribute("aria-invalid", "false");
  });

  it("确认密码为空时，改密码不去校验确认密码", async () => {
    const { user, password, confirm } = renderRegister();
    await user.type(password, PASSWORD);
    expect(confirm).toHaveAttribute("aria-invalid", "false");
  });
});

describe("RegisterForm · 后端返回失败", () => {
  it("邮箱已注册：邮箱下方提示并给出去登录的链接，保留全部内容，焦点移到邮箱", async () => {
    vi.mocked(register).mockResolvedValue({ ok: false, reason: "email-taken" });
    const form = renderRegister();
    await fillAndSubmit(form);
    await waitFor(() => expect(form.email).toHaveAccessibleDescription("该邮箱已注册，去登录"));
    expect(screen.getByRole("link", { name: "去登录" })).toHaveAttribute(
      "href",
      "/login?next=%2Fjournals%2F7",
    );
    expect(form.email).toHaveFocus();
    expect(form.password).toHaveValue(PASSWORD);
    expect(form.confirm).toHaveValue(PASSWORD);
  });

  it("密码太常见：密码下方提示，保留全部内容，焦点移到密码框；改了密码提示就消失", async () => {
    vi.mocked(register).mockResolvedValue({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [{ field: "password", code: "TOO_COMMON" }],
    });
    const form = renderRegister();
    await fillAndSubmit(form);
    await waitFor(() =>
      expect(form.password).toHaveAccessibleDescription("这个密码太常见，容易被猜到，请换一个"),
    );
    expect(form.password).toHaveFocus();
    expect(form.password).toHaveValue(PASSWORD);
    expect(form.confirm).toHaveValue(PASSWORD);
    await form.user.type(form.password, "!");
    expect(form.password).toHaveAccessibleDescription("至少 8 位");
  });

  it.each<[string, () => void]>([
    [
      "服务不可用",
      () => vi.mocked(register).mockResolvedValue({ ok: false, reason: "unavailable" }),
    ],
    ["action 的 promise 被拒绝", () => vi.mocked(register).mockRejectedValue(new Error("network"))],
    [
      "字段名一条都对不上",
      () =>
        vi.mocked(register).mockResolvedValue({
          ok: false,
          reason: "invalid-fields",
          fieldErrors: [{ field: "nickname", code: "REQUIRED" }],
        }),
    ],
  ])("%s：提示条，保留全部内容，焦点留在提交按钮上", async (_label, arrange) => {
    arrange();
    const form = renderRegister();
    await fillAndSubmit(form);
    expect(await screen.findByRole("alert")).toHaveTextContent("注册没有成功，请稍后再试");
    expect(form.email).toHaveValue(USER.email);
    expect(form.password).toHaveValue(PASSWORD);
    expect(form.confirm).toHaveValue(PASSWORD);
    expect(form.submit).toHaveFocus();
    expect(form.submit).not.toHaveAttribute("aria-disabled");
  });
});

describe("RegisterForm · 提交", () => {
  it("提交中：三个输入框只读，按钮 aria-disabled 并换文案", async () => {
    const pending = deferred<RegisterResult>();
    vi.mocked(register).mockReturnValue(pending.promise);
    const form = renderRegister();
    await fillAndSubmit(form);
    const busy = screen.getByRole("button", { name: "注册中…" });
    expect(busy).toHaveAttribute("aria-disabled", "true");
    expect(busy).toHaveAttribute("title", "正在注册");
    for (const input of [form.email, form.password, form.confirm]) {
      expect(input).toHaveAttribute("readonly");
    }
    await form.user.click(busy);
    expect(register).toHaveBeenCalledTimes(1);
    await act(async () => pending.answer({ ok: false, reason: "unavailable" }));
  });

  it("只把邮箱（去掉首尾空白）和密码交给后端，确认密码不传", async () => {
    const form = renderRegister();
    await fillAndSubmit(form);
    await waitFor(() => expect(register).toHaveBeenCalledTimes(1));
    expect(register).toHaveBeenCalledWith({ email: USER.email, password: PASSWORD });
  });

  it("成功：先预取目标页，写入当前用户，提示「注册成功，已登录」，替换到 next", async () => {
    const form = renderRegister();
    await fillAndSubmit(form);
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith(NEXT));
    expect(vi.mocked(router.prefetch).mock.invocationCallOrder[0]).toBeLessThan(
      vi.mocked(register).mock.invocationCallOrder[0] ?? 0,
    );
    expect(form.client.getQueryData(queryKeys.currentUser)).toEqual(USER);
    expect(toast.success).toHaveBeenCalledWith("注册成功，已登录");
  });
});
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/components/auth/RegisterForm.test.tsx`
Expected: FAIL，`Error: Failed to resolve import "@/components/auth/RegisterForm" from "tests/components/auth/RegisterForm.test.tsx". Does the file exist?`，`Tests  no tests`。

- [ ] **Step 3: 写实现**

新建 `patra-portal/src/components/auth/RegisterForm.tsx`：

```tsx
"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { type FormEvent, useState } from "react";
import { useForm } from "react-hook-form";
import { AuthLink } from "@/components/auth/AuthLink";
import { FormAlert } from "@/components/auth/FormAlert";
import { FormField } from "@/components/auth/FormField";
import { PasswordInput } from "@/components/auth/PasswordInput";
import { useAuthSubmit, useServerFieldErrors } from "@/components/auth/use-auth-submit";
import { register as registerAccount } from "@/lib/auth/actions";
import { registerSchema } from "@/lib/auth/form-schemas";
import { FIELD_MESSAGES, REGISTER_UNAVAILABLE_MESSAGE, TOAST_MESSAGES } from "@/lib/auth/messages";
import { authPageHref } from "@/lib/auth/next-path";
import { btnBlock, btnPrimary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

/// 注册表单（spec 第 9 节）：提示条、邮箱、密码、确认密码、不支持找回密码的提醒、提交按钮、去登录的
/// 链接。`next` 已经过 `safeNextPath`。
export function RegisterForm({ next }: { next: string }) {
  const [alert, setAlert] = useState<string | null>(null);
  const serverErrors = useServerFieldErrors();
  const {
    register,
    handleSubmit,
    formState: { errors },
    getFieldState,
    getValues,
    trigger,
    setFocus,
    watch,
  } = useForm({
    resolver: zodResolver(registerSchema),
    mode: "onBlur",
    reValidateMode: "onBlur",
    defaultValues: { email: "", password: "", confirmPassword: "" },
  });
  const loginHref = authPageHref("/login", next);
  const { submitting, submit } = useAuthSubmit({
    next,
    action: registerAccount,
    successToast: TOAST_MESSAGES.registered,
    onFailure: (failure, credentials) => {
      if (failure.reason === "email-taken") {
        serverErrors.show(
          "email",
          credentials.email,
          <>
            {FIELD_MESSAGES.emailTaken}
            <AuthLink replace drawn href={loginHref}>
              {FIELD_MESSAGES.emailTakenLink}
            </AuthLink>
          </>,
        );
        setFocus("email");
        return;
      }
      if (failure.reason === "invalid-fields") {
        const first = serverErrors.showFieldErrors(failure.fieldErrors, credentials);
        if (first) {
          setFocus(first);
          return;
        }
      }
      // 服务不可用，或字段名一条都对不上：保留全部内容，焦点不动（点了提交按钮时就留在按钮上）
      setAlert(REGISTER_UNAVAILABLE_MESSAGE);
    },
  });

  /// 字段显示错误之后，每次输入都重新校验它，改对了错误立即消失（spec 第 9.3 节）
  function revalidate(field: "email" | "password" | "confirmPassword") {
    if (getFieldState(field).error) {
      void trigger(field);
    }
  }

  const [email, password] = watch(["email", "password"]);
  const emailError = errors.email?.message ?? serverErrors.messageFor("email", email.trim());
  const passwordError = errors.password?.message ?? serverErrors.messageFor("password", password);

  /// 每次提交先清掉提示条和后端字段错误，再校验、请求。提交中再次提交由 `submit` 忽略。
  /// 确认密码只在前端比对，不传给后端。
  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAlert(null);
    serverErrors.clear();
    void handleSubmit((values) => submit({ email: values.email, password: values.password }))(
      event,
    );
  }

  return (
    <>
      {/* 副文案到表单 32px；有提示条时，副文案到提示条 24px，提示条到第一个字段 20px */}
      <form
        noValidate
        onSubmit={onSubmit}
        className={cn("flex flex-col gap-5", alert ? "mt-6" : "mt-8")}
      >
        {alert && <FormAlert>{alert}</FormAlert>}
        <FormField id="register-email" label="邮箱" error={emailError}>
          {(control) => (
            <input
              {...control}
              {...register("email", { onChange: () => revalidate("email") })}
              type="email"
              autoComplete="email"
              spellCheck={false}
              placeholder="name@example.com"
              readOnly={submitting}
              // biome-ignore lint/a11y/noAutofocus: 简报 §6 要求进入注册页时焦点在邮箱框
              autoFocus
            />
          )}
        </FormField>
        <FormField id="register-password" label="密码" hint="至少 8 位" error={passwordError}>
          {(control) => (
            <PasswordInput
              {...control}
              {...register("password", {
                onChange: () => {
                  revalidate("password");
                  // 确认密码不为空时，密码每次改动都重新比对确认密码
                  if (getValues("confirmPassword") !== "") {
                    void trigger("confirmPassword");
                  }
                },
              })}
              autoComplete="new-password"
              spellCheck={false}
              readOnly={submitting}
            />
          )}
        </FormField>
        <FormField
          id="register-confirm-password"
          label="确认密码"
          error={errors.confirmPassword?.message}
        >
          {(control) => (
            <PasswordInput
              {...control}
              {...register("confirmPassword", { onChange: () => revalidate("confirmPassword") })}
              autoComplete="new-password"
              spellCheck={false}
              readOnly={submitting}
            />
          )}
        </FormField>
        <div className="flex flex-col gap-4">
          <p className="text-fg-3 text-sm leading-5">请牢记密码，目前不支持找回密码。</p>
          <button
            type="submit"
            aria-disabled={submitting || undefined}
            title={submitting ? "正在注册" : undefined}
            className={cn(btnPrimary, btnBlock)}
          >
            {submitting ? "注册中…" : "注册"}
          </button>
        </div>
      </form>
      <p className="mt-5 text-base text-fg-2 leading-5">
        已有账号？
        <AuthLink replace href={loginHref}>
          登录
        </AuthLink>
      </p>
    </>
  );
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: 同 Step 2。
Expected: PASS，`Tests  17 passed (17)`。

- [ ] **Step 5: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write src/components/auth/RegisterForm.tsx tests/components/auth/RegisterForm.test.tsx
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0，`lint` 输出 `Checked 276 files`；`Test Files  104 passed (104)`、`Tests  969 passed (969)`。

- [ ] **Step 6: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/src/components/auth/RegisterForm.tsx patra-portal/tests/components/auth/RegisterForm.test.tsx
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
feat(portal): 注册表单 (PAP-68)

邮箱、密码、确认密码三个字段，密码下方常驻「至少 8 位」。确认密码只在前端比对，不为空时密码
每次改动都重新比对。邮箱已注册时在邮箱下方给出去登录的链接；服务不可用时显示提示条，保留全部
内容，焦点留在提交按钮上。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这两个文件；pre-commit 全部通过。

---

### Task 9: 登录页、注册页和外壳

**Files:**
- Create: `patra-portal/src/components/auth/AuthShell.tsx`
- Create: `patra-portal/src/app/login/page.tsx`、`patra-portal/src/app/register/page.tsx`
- Test: `patra-portal/tests/components/auth/AuthShell.test.tsx`、`patra-portal/tests/app/auth-pages.test.tsx`

**Interfaces:**
- Consumes: Task 1 `safeNextPath`；Task 7 `LoginForm`；Task 8 `RegisterForm`；现有的 `BrandMark`（`width`）、`Venation`（`tone="inverse"`）。
- Produces: `AuthShell({ title, description, back, children })`；路由 `/login`、`/register`，标题「登录 · Patra」「注册 · Patra」，`robots: { index: false }`。页面不读 Cookie、不判断登录状态（Task 3 的 proxy 已经判断过）。Task 10 起的 e2e 打开这两个页面。

- [ ] **Step 1: 写失败的测试**

新建 `patra-portal/tests/components/auth/AuthShell.test.tsx`：

```tsx
import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { AuthShell } from "@/components/auth/AuthShell";

function renderShell() {
  return render(
    <AuthShell title="登录" description="用邮箱和密码登录 Patra" back="/papers?q=GLP-1">
      <form aria-label="登录表单" />
    </AuthShell>,
  );
}

describe("AuthShell", () => {
  it("标题、副文案和表单在主区域里", () => {
    renderShell();
    const main = screen.getByRole("main");
    expect(within(main).getByRole("heading", { level: 1, name: "登录" })).toBeInTheDocument();
    expect(within(main).getByText("用邮箱和密码登录 Patra")).toBeInTheDocument();
    expect(within(main).getByRole("form", { name: "登录表单" })).toBeInTheDocument();
  });

  it("左上角的品牌标识回首页", () => {
    renderShell();
    expect(screen.getByRole("link", { name: "Patra 首页" })).toHaveAttribute("href", "/");
  });

  it("「返回」指向 next", () => {
    renderShell();
    expect(screen.getByRole("link", { name: "返回" })).toHaveAttribute("href", "/papers?q=GLP-1");
  });
});
```

新建 `patra-portal/tests/app/auth-pages.test.tsx`：

```tsx
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import type { ReactElement } from "react";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";

vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn(), prefetch: vi.fn() }) }));
vi.mock("@/lib/auth/actions", () => ({ login: vi.fn(), logout: vi.fn(), register: vi.fn() }));

import LoginPage, { metadata as loginMetadata } from "@/app/login/page";
import RegisterPage, { metadata as registerMetadata } from "@/app/register/page";

type SearchParams = Record<string, string | string[] | undefined>;

const PAGES = [
  {
    name: "登录页",
    Page: LoginPage,
    metadata: loginMetadata,
    title: "登录 · Patra",
    heading: "登录",
    switchLink: "注册",
    switchPage: "/register",
  },
  {
    name: "注册页",
    Page: RegisterPage,
    metadata: registerMetadata,
    title: "注册 · Patra",
    heading: "注册",
    switchLink: "登录",
    switchPage: "/login",
  },
] as const;

async function renderPage(
  Page: (props: { searchParams: Promise<SearchParams> }) => Promise<ReactElement>,
  searchParams: SearchParams,
) {
  const element = await Page({ searchParams: Promise.resolve(searchParams) });
  render(<QueryClientProvider client={new QueryClient()}>{element}</QueryClientProvider>);
}

describe.each(PAGES)("$name", ({ Page, metadata, title, heading, switchLink, switchPage }) => {
  it("标题，不让搜索引擎收录", () => {
    expect(metadata).toEqual({ title, robots: { index: false } });
  });

  it("「返回」和互跳链接带上校验过的 next", async () => {
    await renderPage(Page, { next: "/papers?q=GLP-1" });
    expect(screen.getByRole("heading", { level: 1, name: heading })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "返回" })).toHaveAttribute("href", "/papers?q=GLP-1");
    expect(screen.getByRole("link", { name: switchLink })).toHaveAttribute(
      "href",
      `${switchPage}?next=%2Fpapers%3Fq%3DGLP-1`,
    );
  });

  it("next 出现多次时取第一个", async () => {
    await renderPage(Page, { next: ["/journals/7", "/papers"] });
    expect(screen.getByRole("link", { name: "返回" })).toHaveAttribute("href", "/journals/7");
  });

  it("next 不合法或没有时，「返回」回首页，互跳链接不带 next", async () => {
    await renderPage(Page, { next: "//evil.com" });
    expect(screen.getByRole("link", { name: "返回" })).toHaveAttribute("href", "/");
    expect(screen.getByRole("link", { name: switchLink })).toHaveAttribute("href", switchPage);
  });
});
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/components/auth/AuthShell.test.tsx tests/app/auth-pages.test.tsx`
Expected: FAIL，两个文件都报 `Error: Failed to resolve import`（`"@/components/auth/AuthShell"`、`"@/app/login/page"`），`Test Files  2 failed (2)`，`Tests  no tests`。

- [ ] **Step 3: 写实现**

新建 `patra-portal/src/components/auth/AuthShell.tsx`：

```tsx
import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { BrandMark } from "@/components/portal/BrandMark";
import { Venation } from "@/components/portal/Venation";

interface AuthShellProps {
  title: string;
  description: string;
  /// 「返回」指向的站内路径，已经过 `safeNextPath`
  back: string;
  children: ReactNode;
}

/// 登录页、注册页的外壳，照高保真稿 Main / Register-Default（桌面）、Login-M-Default /
/// Register-M-Default（移动）。左栏是品牌标识、「返回」和表单区；右栏是墨色品牌面板，宽度
/// ≤ 880px 时不显示。两栏各占一半，用 `basis-1/2`：`flex-1` 平分的是扣掉内边距之后的宽度，
/// 左栏内边距大，会更宽。左栏的左内边距和 TopNav 的品牌左缘对齐。表单区上下两个占位按
/// 2 : 3 分剩余高度，视觉上略偏上；移动端改为顶部对齐。
/// h1 和定位语的类名直接写字符串，不经过 `cn()`：tailwind-merge 会把 `text-display-3`
/// 当成文字颜色，和真正的颜色类互相覆盖。
export function AuthShell({ title, description, back, children }: AuthShellProps) {
  return (
    <div className="flex min-h-screen">
      <div className="flex min-w-0 grow basis-1/2 flex-col pl-[max(var(--gutter),calc((100vw-var(--page-max))/2+var(--gutter)))] pr-gutter max-[880px]:pb-10">
        <header className="flex h-14 shrink-0 items-center justify-between gap-6 max-[880px]:gap-4">
          <Link
            href="/"
            aria-label="Patra 首页"
            className="inline-flex items-center gap-2.5 text-ink-900"
          >
            <BrandMark width={9} />
            <span className="font-serif text-2xl leading-none font-medium tracking-tight">
              Patra
            </span>
            <span className="whitespace-nowrap border-l border-border-strong pl-2.5 font-mono text-3xs tracking-caps text-fg-3 max-[880px]:hidden">
              医学文献门户
            </span>
          </Link>
          {/* 下划线只在悬停时画出，键盘聚焦时只有焦点环（高保真稿 Spec-Form 05） */}
          <Link
            replace
            href={back}
            className="group/back inline-flex items-center gap-1.5 rounded-sm py-2 text-sm leading-5 font-medium text-fg-2 transition-colors hover:text-ink-900 max-[880px]:py-3"
          >
            <ArrowLeft className="size-3.5 shrink-0" strokeWidth={1.75} aria-hidden="true" />
            <span className="link-draw pb-px group-hover/back:[background-size:100%_1px]">
              返回
            </span>
          </Link>
        </header>
        <main className="flex flex-1 flex-col py-6 max-[880px]:pt-10 max-[880px]:pb-0">
          <div aria-hidden="true" className="flex-2 max-[880px]:hidden" />
          <div className="w-full max-w-100">
            <h1 className="font-serif text-display-3 leading-heading font-medium tracking-tight text-fg-1">
              {title}
            </h1>
            <p className="mt-3 text-md leading-relaxed text-fg-2">{description}</p>
            {children}
          </div>
          <div aria-hidden="true" className="flex-3 max-[880px]:hidden" />
        </main>
      </div>
      <aside className="relative flex min-w-0 grow basis-1/2 flex-col justify-end overflow-hidden bg-bg-inverse p-16 text-fg-inverse-2 max-[880px]:hidden">
        <Venation
          tone="inverse"
          className="pointer-events-none absolute top-[7vh] right-[13%] h-[66vh] w-auto -rotate-16"
        />
        <div className="relative border-t border-border-inverse-strong pt-5">
          <p className="font-mono text-2xs leading-relaxed tracking-mono text-fg-inverse-3">
            <span className="text-accent-on-inverse">图 1</span>　叶脉
          </p>
          <p className="mt-3 font-serif text-display-3 leading-heading font-medium tracking-tight whitespace-nowrap text-fg-inverse-1">
            十个来源，
            <br />
            汇入同一根<span className="text-accent-on-inverse">主脉</span>。
          </p>
        </div>
      </aside>
    </div>
  );
}
```

新建 `patra-portal/src/app/login/page.tsx`：

```tsx
import type { Metadata } from "next";
import { AuthShell } from "@/components/auth/AuthShell";
import { LoginForm } from "@/components/auth/LoginForm";
import { safeNextPath } from "@/lib/auth/next-path";

export const metadata: Metadata = { title: "登录 · Patra", robots: { index: false } };

/// 登录页。不读 Cookie，也不判断登录状态：已登录的人在 proxy 那一层就被送回 `next`。
/// `next` 出现多次时取第一个。
export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const raw = (await searchParams).next;
  const next = safeNextPath(Array.isArray(raw) ? raw[0] : raw);
  return (
    <AuthShell title="登录" description="用邮箱和密码登录 Patra" back={next}>
      <LoginForm next={next} />
    </AuthShell>
  );
}
```

新建 `patra-portal/src/app/register/page.tsx`：

```tsx
import type { Metadata } from "next";
import { AuthShell } from "@/components/auth/AuthShell";
import { RegisterForm } from "@/components/auth/RegisterForm";
import { safeNextPath } from "@/lib/auth/next-path";

export const metadata: Metadata = { title: "注册 · Patra", robots: { index: false } };

/// 注册页。不读 Cookie，也不判断登录状态：已登录的人在 proxy 那一层就被送回 `next`。
/// `next` 出现多次时取第一个。
export default async function RegisterPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const raw = (await searchParams).next;
  const next = safeNextPath(Array.isArray(raw) ? raw[0] : raw);
  return (
    <AuthShell title="注册" description="用邮箱建一个 Patra 账号" back={next}>
      <RegisterForm next={next} />
    </AuthShell>
  );
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: 同 Step 2。
Expected: PASS，`Test Files  2 passed (2)`，`Tests  11 passed (11)`。

- [ ] **Step 5: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write src/components/auth/AuthShell.tsx src/app/login/page.tsx src/app/register/page.tsx tests/components/auth/AuthShell.test.tsx tests/app/auth-pages.test.tsx
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0，`lint` 输出 `Checked 281 files`；`Test Files  106 passed (106)`、`Tests  980 passed (980)`。

- [ ] **Step 6: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/src/components/auth/AuthShell.tsx patra-portal/src/app/login/page.tsx patra-portal/src/app/register/page.tsx patra-portal/tests/components/auth/AuthShell.test.tsx patra-portal/tests/app/auth-pages.test.tsx
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
feat(portal): 登录页、注册页和专注布局的外壳 (PAP-68)

页面只做两件事：用 safeNextPath 算出 next，渲染外壳和表单；不读 Cookie，已登录的人在 proxy
那一层已被送回 next。外壳照高保真稿：左栏是品牌标识、「返回」和表单区，右栏是墨色品牌面板，
两栏各占一半；宽度 880px 以下只留表单。两页都不让搜索引擎收录。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这 5 个文件；pre-commit 全部通过。

---

### Task 10: 离开字段时冒出的错误不让下方的点击落空

Task 7、8 的表单用 RHF 的 `mode: "onBlur"`：离开字段立即校验，错误行插进来，把下方的链接、按钮往下挤。用户在邮箱框里填了一半（或者根本没填，进入页面时焦点就在邮箱框），直接去点「注册」或「登录」，按下和松开落在不同元素上，这次点击就没了。只有真实浏览器有布局，所以先写 e2e 复现，再按三轮组件测试改。

**Files:**
- Create: `patra-portal/src/components/auth/use-blur-validation.ts`
- Modify: `patra-portal/src/components/auth/LoginForm.tsx`、`patra-portal/src/components/auth/RegisterForm.tsx`
- Test: `patra-portal/tests/e2e/auth.spec.ts`（新建）、`patra-portal/tests/components/auth/LoginForm.test.tsx`、`patra-portal/tests/components/auth/RegisterForm.test.tsx`（加用例）

**Interfaces:**
- Consumes: RHF 的 `trigger`（`UseFormTrigger<T>`）；Task 7、8 的两张表单；Task 9 的页面。
- Produces: `useBlurValidation<T extends FieldValues>(trigger: UseFormTrigger<T>)`，返回 `(field: FieldPath<T>) => (event: FocusEvent<HTMLInputElement>) => void`，交给 `register(field, { onBlur })`。两张表单的 RHF 改成 `mode: "onSubmit"`、`reValidateMode: "onSubmit"`。`tests/e2e/auth.spec.ts` 由 Task 11、16 继续往里加，其中的 `waitForTakeover(page: Page, selector: string): Promise<void>` Task 16 也用。

- [ ] **Step 1: 写失败的 e2e**

新建 `patra-portal/tests/e2e/auth.spec.ts`。每条用例打开页面后先 `waitForTakeover(page, "form")`，等页面接管了表单再操作：接管之前点下去，走的是浏览器原生的行为（点链接整页跳转，离开字段也不校验），没修也会通过。React 19 的服务端渲染直接输出 `autofocus`，邮箱框有焦点不说明页面已经接管。

```ts
import { expect, type Page, test } from "@playwright/test";

/// 真人点一下要按住一会儿。Playwright 默认的点击按下就松开，测不出按住期间页面的挤动
const PRESS = { delay: 100 };

/// 等页面接管 `selector` 指向的元素：React 接管一个元素时，在它上面挂一个以 `__reactProps$` 开头的
/// 属性。接管之前操作，点链接是浏览器原生的跳转，离开字段也不校验，这里的用例没修好也会通过
async function waitForTakeover(page: Page, selector: string) {
  await page.waitForFunction((target) => {
    const element = document.querySelector(target);
    return element !== null && Object.keys(element).some((key) => key.startsWith("__reactProps$"));
  }, selector);
}

/// 离开字段时冒出的错误行会把下方的链接、按钮往下挤。按下和松开之间挤动了，松开时落在别的元素上，
/// 这次点击就没了。只有真实浏览器有布局，组件测试测不到，所以放在这里
test.describe("登录页、注册页：离开字段时冒出的错误不让下方的点击落空", () => {
  test("登录页：焦点在空的邮箱框里，直接点「注册」", async ({ page }) => {
    await page.goto("/login?next=%2Fpapers%3Fq%3DGLP-1");
    await waitForTakeover(page, "form");
    await expect(page.getByLabel("邮箱")).toBeFocused();
    await page.getByRole("link", { name: "注册" }).click(PRESS);
    await expect(page).toHaveURL("/register?next=%2Fpapers%3Fq%3DGLP-1");
  });

  test("注册页：邮箱填了一半，直接点「登录」", async ({ page }) => {
    await page.goto("/register?next=%2Fjournals%2F7");
    await waitForTakeover(page, "form");
    await page.getByLabel("邮箱").fill("reader");
    await page.getByRole("link", { name: "登录" }).click(PRESS);
    await expect(page).toHaveURL("/login?next=%2Fjournals%2F7");
  });

  test.describe("手机上轻触", () => {
    test.use({ viewport: { width: 375, height: 812 }, isMobile: true, hasTouch: true });

    test("注册页：邮箱填了一半，直接轻触「登录」", async ({ page }) => {
      await page.goto("/register?next=%2Fjournals%2F7");
      await waitForTakeover(page, "form");
      await page.getByLabel("邮箱").fill("reader");
      await page.getByRole("link", { name: "登录" }).tap();
      await expect(page).toHaveURL("/login?next=%2Fjournals%2F7");
    });
  });
});
```

- [ ] **Step 2: 跑 e2e，确认失败**

先确认 4000 端口上没有别的门户在跑（`lsof -nP -iTCP:4000 -sTCP:LISTEN` 没有输出）。有的话 Playwright 会直接复用它；如果那是本分支用 `preview_start` 起的 `portal-dev`，复用也可以。这几条用例不连网关。

Run: `env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY -u all_proxy -u ALL_PROXY pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec playwright test tests/e2e/auth.spec.ts`
Expected: FAIL，`3 failed`。点「注册」的那条是 `Expected: "http://localhost:4000/register?next=%2Fpapers%3Fq%3DGLP-1"`、`Received: "http://localhost:4000/login?next=%2Fpapers%3Fq%3DGLP-1"`；点和轻触「登录」的两条是 `Expected: "http://localhost:4000/login?next=%2Fjournals%2F7"`、`Received: "http://localhost:4000/register?next=%2Fjournals%2F7"`。失败快照里能看到冒出来的「请输入邮箱」「邮箱格式不正确」。

要是有用例通过了，先看一眼负载（`sysctl -n vm.loadavg`）：超过 12 时等它降下来再跑。机器很慢时，错误行可能在松开之后才插进来，这次点击就落不空。

- [ ] **Step 3: 第一轮测试：空着离开不提示**

进入页面时焦点就在邮箱框，点一下页面别处不该冒出「请输入邮箱」；「请输入…」留到提交时：

```diff
diff --git a/patra-portal/tests/components/auth/LoginForm.test.tsx b/patra-portal/tests/components/auth/LoginForm.test.tsx
--- a/patra-portal/tests/components/auth/LoginForm.test.tsx
+++ b/patra-portal/tests/components/auth/LoginForm.test.tsx
@@ -93,6 +93,19 @@ describe("LoginForm · 校验时机", () => {
     expect(email).toHaveAttribute("aria-invalid", "true");
   });

+  it("空着离开字段不提示，提交之后也是；「请输入…」只在提交时出现", async () => {
+    const { user, email, password, submit } = renderLogin();
+    // 进入时焦点就在邮箱框，空着离开
+    await user.tab();
+    expect(email).toHaveAttribute("aria-invalid", "false");
+    await user.type(password, "correct horse");
+    await user.click(submit);
+    expect(email).toHaveAccessibleDescription("请输入邮箱");
+    await user.clear(password);
+    await user.tab();
+    expect(password).toHaveAttribute("aria-invalid", "false");
+  });
+
   it("显示错误之后随输入重新校验，改对了错误立即消失", async () => {
     const { user, email } = renderLogin();
     await user.type(email, "reader");
```

- [ ] **Step 4: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/components/auth/LoginForm.test.tsx`
Expected: FAIL，`Tests  1 failed | 26 passed (27)`，新加的那条报 `aria-invalid` 期望 `"false"`、实际 `"true"`。

- [ ] **Step 5: 第一轮实现**

新建 `patra-portal/src/components/auth/use-blur-validation.ts`：

```ts
import type { FocusEvent } from "react";
import type { FieldPath, FieldValues, UseFormTrigger } from "react-hook-form";

/// 离开字段时校验它（spec 第 9.3 节），返回交给 `register` 的 `onBlur`。表单用 `mode: "onSubmit"`、
/// `reValidateMode: "onSubmit"`，失焦校验全部交给这里。空着离开不校验，「请输入…」留到提交时：
/// 进入页面时焦点已经在邮箱框，点一下别处不该冒出错误。
export function useBlurValidation<T extends FieldValues>(trigger: UseFormTrigger<T>) {
  return (field: FieldPath<T>) => (event: FocusEvent<HTMLInputElement>) => {
    if (event.target.value !== "") {
      void trigger(field);
    }
  };
}
```

`LoginForm` 的 RHF 只在提交时校验，离开邮箱框时交给 `useBlurValidation`。密码框不接 `onBlur`：登录页的密码只查非空，空着离开又不校验，离开时没有可查的。

```diff
diff --git a/patra-portal/src/components/auth/LoginForm.tsx b/patra-portal/src/components/auth/LoginForm.tsx
--- a/patra-portal/src/components/auth/LoginForm.tsx
+++ b/patra-portal/src/components/auth/LoginForm.tsx
@@ -8,6 +8,7 @@ import { FormAlert } from "@/components/auth/FormAlert";
 import { FormField } from "@/components/auth/FormField";
 import { PasswordInput } from "@/components/auth/PasswordInput";
 import { useAuthSubmit, useServerFieldErrors } from "@/components/auth/use-auth-submit";
+import { useBlurValidation } from "@/components/auth/use-blur-validation";
 import { login } from "@/lib/auth/actions";
 import { loginSchema } from "@/lib/auth/form-schemas";
 import { loginAlertMessage, TOAST_MESSAGES } from "@/lib/auth/messages";
@@ -30,10 +31,13 @@ export function LoginForm({ next }: { next: string }) {
     watch,
   } = useForm({
     resolver: zodResolver(loginSchema),
-    mode: "onBlur",
-    reValidateMode: "onBlur",
+    // RHF 只在提交时校验；离开字段、输入时的校验由下面的 onBlur、onChange 触发。密码只查非空，
+    // 空着离开又不校验，离开密码框时没有可查的，所以密码框不接 onBlur
+    mode: "onSubmit",
+    reValidateMode: "onSubmit",
     defaultValues: { email: "", password: "" },
   });
+  const validateOnBlur = useBlurValidation(trigger);
   const { submitting, submit } = useAuthSubmit({
     next,
     action: login,
@@ -91,7 +95,10 @@ export function LoginForm({ next }: { next: string }) {
           {(control) => (
             <input
               {...control}
-              {...register("email", revalidateOnChange("email"))}
+              {...register("email", {
+                onBlur: validateOnBlur("email"),
+                ...revalidateOnChange("email"),
+              })}
               type="email"
               autoComplete="username"
               spellCheck={false}
```

- [ ] **Step 6: 跑测试，确认通过**

Run: 同 Step 4。
Expected: PASS，`Tests  27 passed (27)`。

- [ ] **Step 7: 第二轮测试：按着指针离开时，等松开再校验**

```diff
diff --git a/patra-portal/tests/components/auth/LoginForm.test.tsx b/patra-portal/tests/components/auth/LoginForm.test.tsx
--- a/patra-portal/tests/components/auth/LoginForm.test.tsx
+++ b/patra-portal/tests/components/auth/LoginForm.test.tsx
@@ -106,6 +106,16 @@ describe("LoginForm · 校验时机", () => {
     expect(password).toHaveAttribute("aria-invalid", "false");
   });

+  it("按着指针离开字段时，松开之后才校验：按下和松开之间不插入错误行，下方的链接、按钮不会被挤走", async () => {
+    const { user, email, password } = renderLogin();
+    await user.type(email, "reader");
+    await user.pointer({ keys: "[MouseLeft>]", target: password });
+    expect(password).toHaveFocus();
+    expect(email).toHaveAttribute("aria-invalid", "false");
+    await user.pointer({ keys: "[/MouseLeft]", target: password });
+    await waitFor(() => expect(email).toHaveAccessibleDescription("邮箱格式不正确"));
+  });
+
   it("显示错误之后随输入重新校验，改对了错误立即消失", async () => {
     const { user, email } = renderLogin();
     await user.type(email, "reader");
```

- [ ] **Step 8: 跑测试，确认失败**

Run: 同 Step 4。
Expected: FAIL，`Tests  1 failed | 27 passed (28)`，新加的那条在按下之后就看到了 `aria-invalid="true"`。

- [ ] **Step 9: 第二轮实现**

在捕获阶段记下指针有没有按着：按着时离开字段，记下字段，松开（或 `pointercancel`）时再校验。把 `patra-portal/src/components/auth/use-blur-validation.ts` 整个换成：

```ts
import { type FocusEvent, useEffect, useRef } from "react";
import type { FieldPath, FieldValues, UseFormTrigger } from "react-hook-form";

/// 离开字段时校验它（spec 第 9.3 节），返回交给 `register` 的 `onBlur`。表单用 `mode: "onSubmit"`、
/// `reValidateMode: "onSubmit"`，失焦校验全部交给这里，有两处和直接失焦校验不同：
/// 1. 空着离开不校验，「请输入…」留到提交时：进入页面时焦点已经在邮箱框，点一下别处不该冒出错误。
/// 2. 不在一次点击的中途校验：错误行插进来会把下方的元素往下挤，按下和松开落在不同元素上，这次点击
///    就没了。按着指针离开时，松开再校验。
export function useBlurValidation<T extends FieldValues>(trigger: UseFormTrigger<T>) {
  const pressing = useRef(false);
  const pending = useRef<FieldPath<T> | null>(null);

  useEffect(() => {
    const press = () => {
      pressing.current = true;
    };
    const release = () => {
      pressing.current = false;
      const field = pending.current;
      pending.current = null;
      if (field !== null) {
        void trigger(field);
      }
    };
    document.addEventListener("pointerdown", press, true);
    document.addEventListener("pointerup", release, true);
    document.addEventListener("pointercancel", release, true);
    return () => {
      document.removeEventListener("pointerdown", press, true);
      document.removeEventListener("pointerup", release, true);
      document.removeEventListener("pointercancel", release, true);
    };
  }, [trigger]);

  return (field: FieldPath<T>) => (event: FocusEvent<HTMLInputElement>) => {
    if (event.target.value === "") {
      return;
    }
    if (pressing.current) {
      pending.current = field;
      return;
    }
    void trigger(field);
  };
}
```

- [ ] **Step 10: 跑测试，确认通过**

Run: 同 Step 4。
Expected: PASS，`Tests  28 passed (28)`。

- [ ] **Step 11: 第三轮测试：注册表单**

「填了内容的字段，离开时校验它」现在就能通过，它守的是下一步把 `mode` 改成 `onSubmit` 之后，三个字段的 `onBlur` 都接上了：

```diff
diff --git a/patra-portal/tests/components/auth/RegisterForm.test.tsx b/patra-portal/tests/components/auth/RegisterForm.test.tsx
--- a/patra-portal/tests/components/auth/RegisterForm.test.tsx
+++ b/patra-portal/tests/components/auth/RegisterForm.test.tsx
@@ -110,6 +110,36 @@ describe("RegisterForm · 输入框", () => {
   });
 });

+describe("RegisterForm · 校验时机", () => {
+  it("填了内容的字段，离开时校验它", async () => {
+    const { user, email, password, confirm } = renderRegister();
+    await user.type(email, "reader");
+    await user.tab();
+    expect(email).toHaveAccessibleDescription("邮箱格式不正确");
+    await user.type(password, PASSWORD);
+    await user.type(confirm, "correct horsf");
+    await user.tab();
+    expect(confirm).toHaveAccessibleDescription("两次输入的密码不一致");
+  });
+
+  it("空着离开字段不提示，提交之后也是；「请输入…」只在提交时出现", async () => {
+    const { user, email, password, confirm, submit } = renderRegister();
+    await user.click(password);
+    await user.click(confirm);
+    await user.click(email);
+    for (const input of [email, password, confirm]) {
+      expect(input).toHaveAttribute("aria-invalid", "false");
+    }
+    await user.type(password, PASSWORD);
+    await user.type(confirm, PASSWORD);
+    await user.click(submit);
+    expect(email).toHaveAccessibleDescription("请输入邮箱");
+    await user.clear(confirm);
+    await user.tab();
+    expect(confirm).toHaveAttribute("aria-invalid", "false");
+  });
+});
+
 describe("RegisterForm · 确认密码", () => {
   it("为空时提示再输入一次，和密码不同时提示不一致", async () => {
     const { user, password, confirm, submit } = renderRegister();
```

- [ ] **Step 12: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/components/auth/RegisterForm.test.tsx`
Expected: FAIL，`Tests  1 failed | 18 passed (19)`，失败的是「空着离开字段不提示，提交之后也是；「请输入…」只在提交时出现」。

- [ ] **Step 13: 第三轮实现**

```diff
diff --git a/patra-portal/src/components/auth/RegisterForm.tsx b/patra-portal/src/components/auth/RegisterForm.tsx
--- a/patra-portal/src/components/auth/RegisterForm.tsx
+++ b/patra-portal/src/components/auth/RegisterForm.tsx
@@ -8,6 +8,7 @@ import { FormAlert } from "@/components/auth/FormAlert";
 import { FormField } from "@/components/auth/FormField";
 import { PasswordInput } from "@/components/auth/PasswordInput";
 import { useAuthSubmit, useServerFieldErrors } from "@/components/auth/use-auth-submit";
+import { useBlurValidation } from "@/components/auth/use-blur-validation";
 import { register as registerAccount } from "@/lib/auth/actions";
 import { registerSchema } from "@/lib/auth/form-schemas";
 import { FIELD_MESSAGES, REGISTER_UNAVAILABLE_MESSAGE, TOAST_MESSAGES } from "@/lib/auth/messages";
@@ -31,10 +32,12 @@ export function RegisterForm({ next }: { next: string }) {
     watch,
   } = useForm({
     resolver: zodResolver(registerSchema),
-    mode: "onBlur",
-    reValidateMode: "onBlur",
+    // RHF 只在提交时校验；离开字段、输入时的校验由下面的 onBlur、onChange 触发
+    mode: "onSubmit",
+    reValidateMode: "onSubmit",
     defaultValues: { email: "", password: "", confirmPassword: "" },
   });
+  const validateOnBlur = useBlurValidation(trigger);
   const loginHref = authPageHref("/login", next);
   const { submitting, submit } = useAuthSubmit({
     next,
@@ -102,7 +105,10 @@ export function RegisterForm({ next }: { next: string }) {
           {(control) => (
             <input
               {...control}
-              {...register("email", { onChange: () => revalidate("email") })}
+              {...register("email", {
+                onBlur: validateOnBlur("email"),
+                onChange: () => revalidate("email"),
+              })}
               type="email"
               autoComplete="email"
               spellCheck={false}
@@ -118,6 +124,7 @@ export function RegisterForm({ next }: { next: string }) {
             <PasswordInput
               {...control}
               {...register("password", {
+                onBlur: validateOnBlur("password"),
                 onChange: () => {
                   revalidate("password");
                   // 确认密码不为空时，密码每次改动都重新比对确认密码
@@ -140,7 +147,10 @@ export function RegisterForm({ next }: { next: string }) {
           {(control) => (
             <PasswordInput
               {...control}
-              {...register("confirmPassword", { onChange: () => revalidate("confirmPassword") })}
+              {...register("confirmPassword", {
+                onBlur: validateOnBlur("confirmPassword"),
+                onChange: () => revalidate("confirmPassword"),
+              })}
               autoComplete="new-password"
               spellCheck={false}
               readOnly={submitting}
```

- [ ] **Step 14: 跑测试，确认通过**

Run: 同 Step 12。
Expected: PASS，`Tests  19 passed (19)`。

- [ ] **Step 15: 再跑 e2e**

Run: 同 Step 2。
Expected: FAIL，`1 failed`、`2 passed`。两条鼠标点击通过了；手机轻触那条仍然失败：Chrome 里轻触先派发 `pointerup`，之后才移交焦点、派发 `click`，离开字段时指针已经松开，校验立即执行，错误行赶在 `click` 之前插进来。

- [ ] **Step 16: 没按着时，排到下一个宏任务再校验**

```diff
diff --git a/patra-portal/src/components/auth/use-blur-validation.ts b/patra-portal/src/components/auth/use-blur-validation.ts
--- a/patra-portal/src/components/auth/use-blur-validation.ts
+++ b/patra-portal/src/components/auth/use-blur-validation.ts
@@ -5,7 +5,7 @@ import type { FieldPath, FieldValues, UseFormTrigger } from "react-hook-form";
 /// `reValidateMode: "onSubmit"`，失焦校验全部交给这里，有两处和直接失焦校验不同：
 /// 1. 空着离开不校验，「请输入…」留到提交时：进入页面时焦点已经在邮箱框，点一下别处不该冒出错误。
 /// 2. 不在一次点击的中途校验：错误行插进来会把下方的元素往下挤，按下和松开落在不同元素上，这次点击
-///    就没了。按着指针离开时，松开再校验。
+///    就没了。按着指针离开时，松开再校验；手机上轻触时松开在前、离开在后，点击紧跟着派发，排到它后面。
 export function useBlurValidation<T extends FieldValues>(trigger: UseFormTrigger<T>) {
   const pressing = useRef(false);
   const pending = useRef<FieldPath<T> | null>(null);
@@ -40,6 +40,7 @@ export function useBlurValidation<T extends FieldValues>(trigger: UseFormTrigger
       pending.current = field;
       return;
     }
-    void trigger(field);
+    // 没按着：键盘离开，或者手机上轻触（这一轮接着派发点击）
+    setTimeout(() => void trigger(field));
   };
 }
```

- [ ] **Step 17: 跑 e2e 和两张表单的测试，确认通过**

Run: 同 Step 2。
Expected: PASS，`3 passed`。

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/components/auth/LoginForm.test.tsx tests/components/auth/RegisterForm.test.tsx`
Expected: PASS，`Test Files  2 passed (2)`，`Tests  47 passed (47)`。

- [ ] **Step 18: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write src/components/auth/use-blur-validation.ts src/components/auth/LoginForm.tsx src/components/auth/RegisterForm.tsx tests/components/auth/LoginForm.test.tsx tests/components/auth/RegisterForm.test.tsx tests/e2e/auth.spec.ts
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0，`lint` 输出 `Checked 283 files`；`Test Files  106 passed (106)`、`Tests  984 passed (984)`。

- [ ] **Step 19: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/src/components/auth/use-blur-validation.ts patra-portal/src/components/auth/LoginForm.tsx patra-portal/src/components/auth/RegisterForm.tsx patra-portal/tests/components/auth/LoginForm.test.tsx patra-portal/tests/components/auth/RegisterForm.test.tsx patra-portal/tests/e2e/auth.spec.ts
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
fix(portal): 离开字段时冒出的错误不再让下方的点击落空 (PAP-68)

失焦校验插入的错误行会把下方的链接、按钮往下挤，按下和松开落在不同元素上，点击就没了。两张
表单的 RHF 改为只在提交时校验，离开字段时由 useBlurValidation 决定：空着离开不校验，「请输入」
留到提交时；按着指针离开时等松开再校验；没按着时（键盘离开、手机轻触）排到下一个宏任务。
e2e 用按住 100 毫秒的点击和手机轻触复现。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这 6 个文件；pre-commit 全部通过。

---

### Task 11: 页面接管表单之前提交时用 POST

脚本还没加载完、页面还没接管表单时就提交（网慢、设备慢、密码管理器自动提交），浏览器按表单自己的属性提交。表单没写 `method` 时是 GET，邮箱和密码会出现在地址栏、历史记录和服务器日志里。

**Files:**
- Modify: `patra-portal/src/components/auth/LoginForm.tsx`、`patra-portal/src/components/auth/RegisterForm.tsx`
- Test: `patra-portal/tests/components/auth/LoginForm.test.tsx`、`RegisterForm.test.tsx`（各加一条）、`patra-portal/tests/e2e/auth.spec.ts`（加两条）

**Interfaces:**
- Consumes: Task 10 之后的两张表单和 `tests/e2e/auth.spec.ts`。
- Produces: 两张表单的 `<form method="post">`。Next 对不带 action ID 的 POST 照常渲染这一页，表单清空，可以再提交一次；不开 JavaScript 时仍然登录不了（spec 第 2 节）。

- [ ] **Step 1: 写失败的组件测试**

```diff
diff --git a/patra-portal/tests/components/auth/LoginForm.test.tsx b/patra-portal/tests/components/auth/LoginForm.test.tsx
--- a/patra-portal/tests/components/auth/LoginForm.test.tsx
+++ b/patra-portal/tests/components/auth/LoginForm.test.tsx
@@ -74,6 +74,11 @@ describe("LoginForm · 输入框", () => {
     expect(password).toHaveAttribute("spellcheck", "false");
   });

+  it("页面还没接管表单就提交时，浏览器用 POST：邮箱、密码不进地址栏", () => {
+    const { email } = renderLogin();
+    expect(email.form).toHaveAttribute("method", "post");
+  });
+
   it("去注册的链接带上 next", () => {
     renderLogin();
     expect(screen.getByRole("link", { name: "注册" })).toHaveAttribute(
diff --git a/patra-portal/tests/components/auth/RegisterForm.test.tsx b/patra-portal/tests/components/auth/RegisterForm.test.tsx
--- a/patra-portal/tests/components/auth/RegisterForm.test.tsx
+++ b/patra-portal/tests/components/auth/RegisterForm.test.tsx
@@ -101,6 +101,11 @@ describe("RegisterForm · 输入框", () => {
     expect(confirm).toHaveAttribute("type", "password");
   });

+  it("页面还没接管表单就提交时，浏览器用 POST：邮箱、密码不进地址栏", () => {
+    const { email } = renderRegister();
+    expect(email.form).toHaveAttribute("method", "post");
+  });
+
   it("去登录的链接带上 next", () => {
     renderRegister();
     expect(screen.getByRole("link", { name: "登录" })).toHaveAttribute(
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/components/auth/LoginForm.test.tsx tests/components/auth/RegisterForm.test.tsx`
Expected: FAIL，`Tests  2 failed | 47 passed (49)`，两条都是 `Expected the element to have attribute:` `method="post"`。

- [ ] **Step 3: 写失败的 e2e**

禁用脚本来模拟页面接管表单之前的那段时间：

```diff
diff --git a/patra-portal/tests/e2e/auth.spec.ts b/patra-portal/tests/e2e/auth.spec.ts
--- a/patra-portal/tests/e2e/auth.spec.ts
+++ b/patra-portal/tests/e2e/auth.spec.ts
@@ -43,3 +43,35 @@ test.describe("登录页、注册页：离开字段时冒出的错误不让下
     });
   });
 });
+
+/// 脚本还没加载完、页面还没接管表单时就提交（网慢、设备慢、密码管理器自动提交），浏览器按表单自己的
+/// 属性提交。禁用脚本来模拟这段时间：提交必须是 POST，地址里只有原来的 next；Next 对不带 action 的
+/// POST 照常渲染这一页，表单清空，可以再提交一次
+test.describe("页面接管表单之前就提交：邮箱、密码不进地址栏", () => {
+  test.use({ javaScriptEnabled: false });
+
+  test("登录页", async ({ page }) => {
+    await page.goto("/login?next=%2Fjournals%2F7");
+    await page.getByLabel("邮箱").fill("reader@example.org");
+    await page.getByLabel("密码", { exact: true }).fill("correct horse");
+    const submitted = page.waitForRequest((request) => request.isNavigationRequest());
+    await page.getByRole("button", { name: "登录" }).click();
+    const request = await submitted;
+    expect(request.method()).toBe("POST");
+    expect(new URL(request.url()).search).toBe("?next=%2Fjournals%2F7");
+    await expect(page.getByRole("heading", { level: 1, name: "登录" })).toBeVisible();
+  });
+
+  test("注册页", async ({ page }) => {
+    await page.goto("/register?next=%2Fjournals%2F7");
+    await page.getByLabel("邮箱").fill("reader@example.org");
+    await page.getByLabel("密码", { exact: true }).fill("correct horse");
+    await page.getByLabel("确认密码").fill("correct horse");
+    const submitted = page.waitForRequest((request) => request.isNavigationRequest());
+    await page.getByRole("button", { name: "注册" }).click();
+    const request = await submitted;
+    expect(request.method()).toBe("POST");
+    expect(new URL(request.url()).search).toBe("?next=%2Fjournals%2F7");
+    await expect(page.getByRole("heading", { level: 1, name: "注册" })).toBeVisible();
+  });
+});
```

- [ ] **Step 4: 跑 e2e，确认失败**

Run: `env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY -u all_proxy -u ALL_PROXY pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec playwright test tests/e2e/auth.spec.ts`
Expected: FAIL，`2 failed`、`3 passed`，新加的两条都是 `Expected: "POST"`、`Received: "GET"`。

- [ ] **Step 5: 写实现**

```diff
diff --git a/patra-portal/src/components/auth/LoginForm.tsx b/patra-portal/src/components/auth/LoginForm.tsx
--- a/patra-portal/src/components/auth/LoginForm.tsx
+++ b/patra-portal/src/components/auth/LoginForm.tsx
@@ -86,6 +86,8 @@ export function LoginForm({ next }: { next: string }) {
     <>
       {/* 副文案到表单 32px；有提示条时，副文案到提示条 24px，提示条到第一个字段 20px */}
       <form
+        // 页面还没接管表单就提交时（脚本没加载完），浏览器按这里的 method 提交：用 POST，邮箱、密码不进地址栏
+        method="post"
         noValidate
         onSubmit={onSubmit}
         className={cn("flex flex-col gap-5", alert ? "mt-6" : "mt-8")}
diff --git a/patra-portal/src/components/auth/RegisterForm.tsx b/patra-portal/src/components/auth/RegisterForm.tsx
--- a/patra-portal/src/components/auth/RegisterForm.tsx
+++ b/patra-portal/src/components/auth/RegisterForm.tsx
@@ -96,6 +96,8 @@ export function RegisterForm({ next }: { next: string }) {
     <>
       {/* 副文案到表单 32px；有提示条时，副文案到提示条 24px，提示条到第一个字段 20px */}
       <form
+        // 页面还没接管表单就提交时（脚本没加载完），浏览器按这里的 method 提交：用 POST，邮箱、密码不进地址栏
+        method="post"
         noValidate
         onSubmit={onSubmit}
         className={cn("flex flex-col gap-5", alert ? "mt-6" : "mt-8")}
```

- [ ] **Step 6: 跑测试，确认通过**

Run: 同 Step 2。
Expected: PASS，`Tests  49 passed (49)`。

Run: 同 Step 4。
Expected: PASS，`5 passed`。

- [ ] **Step 7: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write src/components/auth/LoginForm.tsx src/components/auth/RegisterForm.tsx tests/components/auth/LoginForm.test.tsx tests/components/auth/RegisterForm.test.tsx tests/e2e/auth.spec.ts
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0，`lint` 输出 `Checked 283 files`；`Test Files  106 passed (106)`、`Tests  986 passed (986)`。

- [ ] **Step 8: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/src/components/auth/LoginForm.tsx patra-portal/src/components/auth/RegisterForm.tsx patra-portal/tests/components/auth/LoginForm.test.tsx patra-portal/tests/components/auth/RegisterForm.test.tsx patra-portal/tests/e2e/auth.spec.ts
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
fix(portal): 页面接管表单之前提交时用 POST，邮箱和密码不进地址栏 (PAP-68)

脚本还没加载完就提交（网慢、设备慢、密码管理器自动提交），浏览器按表单自己的属性提交，默认
是 GET，邮箱和密码会出现在地址栏和历史记录里。两张表单写上 method="post"；Next 对不带 action
的 POST 照常渲染这一页，表单清空，可以再提交一次。e2e 禁用脚本复现。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这 5 个文件；pre-commit 全部通过。

---

### Task 12: 成功提示的外观

**Files:**
- Modify: `patra-portal/src/components/ui/sonner.tsx`、`patra-portal/src/app/layout.tsx`
- Test: `patra-portal/tests/components/ui/sonner.test.tsx`、`patra-portal/tests/design-system/components.test.ts`（各加用例）

**Interfaces:**
- Consumes: 现有的 `Toaster`（根布局右下角）；token `--bg-elevated`、`--fg-1`、`--border-default`、`--radius-lg`、`shadow-lg`、`text-status-success`。
- Produces: `toast.success` 渲染成浅底、moss 色图标、`shadow-lg` 阴影，和高保真稿一致；根布局的 `Toaster` 不再带 `richColors`。Task 5、7、8 的三条成功提示用它，现有的普通 `toast()` 外观不变。

- [ ] **Step 1: 写失败的测试**

```diff
diff --git a/patra-portal/tests/components/ui/sonner.test.tsx b/patra-portal/tests/components/ui/sonner.test.tsx
--- a/patra-portal/tests/components/ui/sonner.test.tsx
+++ b/patra-portal/tests/components/ui/sonner.test.tsx
@@ -1,4 +1,5 @@
-import { render } from "@testing-library/react";
+import { act, render, screen } from "@testing-library/react";
+import { toast } from "sonner";
 import { describe, expect, it } from "vitest";
 import "@testing-library/jest-dom/vitest";
 import { Toaster } from "@/components/ui/sonner";
@@ -15,3 +16,35 @@ describe("Toaster", () => {
     expect(() => unmount()).not.toThrow();
   });
 });
+
+describe("Toaster · 成功提示的外观", () => {
+  /// 弹一条成功提示，返回提示本身（`<li>`）和装提示的列表（`<ol>`）
+  async function showSuccess() {
+    render(<Toaster />);
+    act(() => {
+      toast.success("已登录");
+    });
+    const item = (await screen.findByText("已登录")).closest("[data-sonner-toast]");
+    const list = item?.closest<HTMLElement>("[data-sonner-toaster]");
+    if (!(item instanceof HTMLElement) || !list) throw new Error("没有找到提示");
+    return { item, list };
+  }
+
+  it("底色、文字、边框、圆角用 token，写在行内样式上：sonner 插入的样式不分层，压得过 Tailwind 的类", async () => {
+    const { list } = await showSuccess();
+    expect(list.style.getPropertyValue("--normal-bg")).toBe("var(--bg-elevated)");
+    expect(list.style.getPropertyValue("--normal-text")).toBe("var(--fg-1)");
+    expect(list.style.getPropertyValue("--normal-border")).toBe("var(--border-default)");
+    expect(list.style.getPropertyValue("--border-radius")).toBe("var(--radius-lg)");
+  });
+
+  it("阴影用 shadow-lg（带 !important 才压得过 sonner 写死的阴影），字体用门户的无衬线栈", async () => {
+    const { item } = await showSuccess();
+    expect(item).toHaveClass("font-sans", "shadow-lg!");
+  });
+
+  it("成功图标是 moss 色", async () => {
+    const { item } = await showSuccess();
+    expect(item.querySelector("[data-icon] svg")).toHaveClass("text-status-success");
+  });
+});
diff --git a/patra-portal/tests/design-system/components.test.ts b/patra-portal/tests/design-system/components.test.ts
--- a/patra-portal/tests/design-system/components.test.ts
+++ b/patra-portal/tests/design-system/components.test.ts
@@ -101,4 +101,8 @@ describe("组件对齐设计系统", () => {
   it("本版不做暗色：sonner 不依赖 next-themes", () => {
     expect(comp("ui/sonner.tsx")).not.toMatch(/next-themes/);
   });
+
+  it("提示不用 richColors：它把成功提示染成绿底绿字，高保真稿是浅底加 moss 色图标", () => {
+    expect(readSrc("app/layout.tsx")).not.toMatch(/richColors/);
+  });
 });
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/components/ui/sonner.test.tsx tests/design-system/components.test.ts`
Expected: FAIL，`Tests  4 failed | 16 passed (20)`：行内样式那条是 `expected '' to be 'var(--bg-elevated)'`，阴影和图标两条是类名断言失败，`richColors` 那条是 `expected 'import type { Metadata, Viewport } fr…' not to match /richColors/`。

- [ ] **Step 3: 写实现**

```diff
diff --git a/patra-portal/src/app/layout.tsx b/patra-portal/src/app/layout.tsx
--- a/patra-portal/src/app/layout.tsx
+++ b/patra-portal/src/app/layout.tsx
@@ -50,7 +50,7 @@ export default function RootLayout({ children }: { children: React.ReactNode })
         <div id="top" aria-hidden />
         <QueryProvider>
           {children}
-          <Toaster richColors position="bottom-right" />
+          <Toaster position="bottom-right" />
         </QueryProvider>
       </body>
     </html>
diff --git a/patra-portal/src/components/ui/sonner.tsx b/patra-portal/src/components/ui/sonner.tsx
--- a/patra-portal/src/components/ui/sonner.tsx
+++ b/patra-portal/src/components/ui/sonner.tsx
@@ -7,15 +7,29 @@ import {
   OctagonXIcon,
   TriangleAlertIcon,
 } from "lucide-react";
+import type { CSSProperties } from "react";
 import { Toaster as Sonner, type ToasterProps } from "sonner";

 const Toaster = ({ ...props }: ToasterProps) => {
   return (
     <Sonner
       theme="light"
-      className="toaster group [--normal-bg:var(--bg-elevated)] [--normal-text:var(--fg-1)] [--normal-border:var(--border-default)] [--border-radius:var(--radius-lg)]"
+      className="toaster group"
+      // sonner 往 <head> 插入的样式不分层，Tailwind 的类在 @layer utilities 里，写在类名里的变量
+      // 压不过它，所以写在行内样式上
+      style={
+        {
+          "--normal-bg": "var(--bg-elevated)",
+          "--normal-text": "var(--fg-1)",
+          "--normal-border": "var(--border-default)",
+          "--border-radius": "var(--radius-lg)",
+        } as CSSProperties
+      }
+      // 每条提示的阴影和字体：sonner 在提示上写死了阴影，带 !important 才压得过；
+      // 字体它写在外层列表上，提示上写一个就盖过去
+      toastOptions={{ classNames: { toast: "font-sans shadow-lg!" } }}
       icons={{
-        success: <CircleCheckIcon className="size-4" />,
+        success: <CircleCheckIcon className="size-4 text-status-success" />,
         info: <InfoIcon className="size-4" />,
         warning: <TriangleAlertIcon className="size-4" />,
         error: <OctagonXIcon className="size-4" />,
```

- [ ] **Step 4: 跑测试，确认通过**

Run: 同 Step 2。
Expected: PASS，`Tests  20 passed (20)`。

- [ ] **Step 5: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write src/components/ui/sonner.tsx src/app/layout.tsx tests/components/ui/sonner.test.tsx tests/design-system/components.test.ts
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0，`lint` 输出 `Checked 283 files`；`Test Files  106 passed (106)`、`Tests  990 passed (990)`。

- [ ] **Step 6: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/src/components/ui/sonner.tsx patra-portal/src/app/layout.tsx patra-portal/tests/components/ui/sonner.test.tsx patra-portal/tests/design-system/components.test.ts
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
feat(portal): 成功提示照高保真稿改外观，去掉 richColors (PAP-68)

richColors 把成功提示染成绿底，高保真稿是浅底加 moss 色图标。sonner 往 head 插入的样式不分层，
原来写在类名里的底色、文字、边框、圆角变量压不过它，其实没有生效，改写在行内样式上；阴影换成
shadow-lg，要带 important 才压得过 sonner 写死的阴影。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这 4 个文件；pre-commit 全部通过。

---

### Task 13: 菜单原语和桌面导航栏的账号区

**Files:**
- Create: `patra-portal/src/components/ui/dropdown-menu.tsx`
- Create: `patra-portal/src/lib/auth/use-auth-page-href.ts`
- Create: `patra-portal/src/components/portal/UserMenu.tsx`、`patra-portal/src/components/portal/NavAccount.tsx`
- Test: `patra-portal/tests/components/ui/dropdown-menu.test.tsx`、`patra-portal/tests/components/portal/NavAccount.test.tsx`

**Interfaces:**
- Consumes: `@base-ui/react/menu` 的 `Menu`；PAP-67 `useCurrentUser(): CurrentUserState`（`pending`、`anonymous`、`authenticated` 带 `user`）；Task 1 `authPageHref`；Task 5 `useLogout()`；`btnSecondary`、`cn`。
- Produces:
  - `dropdown-menu.tsx` 导出 `DropdownMenu`、`DropdownMenuTrigger`、`DropdownMenuContent`（默认 `align="start"`、`sideOffset={8}`）、`DropdownMenuGroup`、`DropdownMenuLabel`、`DropdownMenuItem`、`DropdownMenuSeparator`。
  - `useAuthPageHref(page: "/login" | "/register"): string`：带上当前地址作 `next`。它调用 `useSearchParams()`，用到它的组件要包在 Suspense 里。
  - `UserMenu({ email, loggingOut, onLogout })`；`NavAccount()`、`NavAccountPlaceholder()`。
  - Task 14 的 `MobileAccount` 用 `useAuthPageHref`；Task 15 把 `NavAccount` 放进 `TopNav`，Suspense 的 fallback 是 `NavAccountPlaceholder`。

- [ ] **Step 1: 写失败的测试**

新建 `patra-portal/tests/components/ui/dropdown-menu.test.tsx`：

```tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

function renderMenu() {
  render(
    <DropdownMenu>
      <DropdownMenuTrigger>账号</DropdownMenuTrigger>
      <DropdownMenuContent>
        <DropdownMenuGroup>
          <DropdownMenuLabel>当前账号</DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuItem>退出登录</DropdownMenuItem>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>,
  );
}

describe("DropdownMenu", () => {
  it("初始不显示菜单", () => {
    renderMenu();
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });

  it("点触发器打开菜单；分组用标题命名，标题不是菜单项", async () => {
    const user = userEvent.setup();
    renderMenu();
    await user.click(screen.getByRole("button", { name: "账号" }));
    expect(await screen.findByRole("menu")).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "当前账号" })).toBeInTheDocument();
    expect(screen.getAllByRole("menuitem").map((item) => item.textContent)).toEqual(["退出登录"]);
  });
});
```

新建 `patra-portal/tests/components/portal/NavAccount.test.tsx`：

```tsx
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { NavAccount } from "@/components/portal/NavAccount";
import { logout } from "@/lib/auth/actions";
import type { CurrentUser } from "@/lib/auth/types";
import { queryKeys } from "@/lib/query-keys";

// 覆写 usePathname / useSearchParams，保留 next/navigation 其余导出
let mockPathname = "/papers";
let mockSearch = "q=GLP-1";
vi.mock("next/navigation", async (importOriginal) => {
  const actual = await importOriginal<typeof import("next/navigation")>();
  return {
    ...actual,
    usePathname: () => mockPathname,
    useSearchParams: () => new actual.ReadonlyURLSearchParams(mockSearch),
  };
});
vi.mock("@/lib/auth/actions", () => ({ login: vi.fn(), logout: vi.fn(), register: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn() } }));

import { toast } from "sonner";

const USER: CurrentUser = { userId: "319041872872550658", email: "chen.yu@example.com" };
const LONG_EMAIL =
  "postdoctoral.researcher.zhang@cardiovascular-research.university-hospital.example.org";

/// 一个还没回来的请求，`answer` 让它在测试想要的时候回来
function deferred<T>() {
  let answer: (value: T) => void = () => {};
  const promise = new Promise<T>((resolve) => {
    answer = resolve;
  });
  return { promise, answer };
}

/// 当前用户：`undefined` 表示还没有数据（`/api/auth/me` 一直不回来），`null` 表示未登录
function renderNav(user: CurrentUser | null | undefined) {
  // 和 createQueryClient 一样，数据 60 秒内不过期：挂载时不会因为数据过期而重新请求
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 60_000 } },
  });
  if (user === undefined) {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => new Promise(() => {})),
    );
  } else {
    client.setQueryData(queryKeys.currentUser, user);
  }
  render(
    <QueryClientProvider client={client}>
      <NavAccount />
    </QueryClientProvider>,
  );
  return { client, user: userEvent.setup() };
}

/// 已登录，点开用户菜单
async function openMenu(email = USER.email) {
  const nav = renderNav({ ...USER, email });
  await nav.user.click(screen.getByRole("button", { name: "账号菜单" }));
  return { ...nav, menu: await screen.findByRole("menu") };
}

afterEach(() => {
  mockPathname = "/papers";
  mockSearch = "q=GLP-1";
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe("NavAccount · 三种形态", () => {
  it("登录状态未定：圆形占位，读屏名称「正在确认登录状态」，不先出现「登录」", () => {
    renderNav(undefined);
    expect(screen.getByRole("status", { name: "正在确认登录状态" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "登录" })).not.toBeInTheDocument();
  });

  it.each([
    ["/papers", "q=GLP-1", "/login?next=%2Fpapers%3Fq%3DGLP-1"],
    ["/journals/7", "", "/login?next=%2Fjournals%2F7"],
    ["/", "", "/login"],
  ])("未登录：「登录」带上当前地址作 next（%s?%s）", (pathname, search, href) => {
    mockPathname = pathname;
    mockSearch = search;
    renderNav(null);
    expect(screen.getByRole("link", { name: "登录" })).toHaveAttribute("href", href);
  });

  it.each([
    ["chen.yu@example.com", "C"],
    [LONG_EMAIL, "P"],
    ["2024cohort@example.org", "2"],
  ])("已登录：圆形标识显示邮箱的第一个字符（%s → %s）", (email, initial) => {
    renderNav({ ...USER, email });
    expect(screen.getByRole("button", { name: "账号菜单" })).toHaveTextContent(initial);
    expect(screen.queryByRole("link", { name: "登录" })).not.toBeInTheDocument();
  });
});

describe("NavAccount · 用户菜单", () => {
  it("第一行是「当前账号」和完整邮箱，过长时截断、悬停看全；下面只有「退出登录」一项", async () => {
    const { menu } = await openMenu(LONG_EMAIL);
    expect(within(menu).getByRole("group", { name: `当前账号 ${LONG_EMAIL}` })).toBeInTheDocument();
    const email = within(menu).getByText(LONG_EMAIL);
    expect(email).toHaveAttribute("title", LONG_EMAIL);
    expect(email).toHaveClass("truncate");
    expect(
      within(menu)
        .getAllByRole("menuitem")
        .map((item) => item.textContent),
    ).toEqual(["退出登录"]);
  });

  it("退出进行中：「退出登录」禁用并说明原因，菜单一直开着；再点不会再发一次", async () => {
    const pending = deferred<void>();
    vi.mocked(logout).mockReturnValue(pending.promise);
    const { user, menu } = await openMenu();
    const item = within(menu).getByRole("menuitem", { name: "退出登录" });
    await user.click(item);
    await waitFor(() => expect(item).toHaveAttribute("aria-disabled", "true"));
    expect(item).toHaveAttribute("title", "正在退出登录");
    await user.click(item);
    expect(logout).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("menu")).toBeInTheDocument();
    pending.answer();
    await screen.findByRole("link", { name: "登录" });
  });

  it("退出成功：圆形变回「登录」，焦点落在「登录」上，提示「已退出登录」", async () => {
    vi.mocked(logout).mockResolvedValue(undefined);
    const { user, menu } = await openMenu();
    await user.click(within(menu).getByRole("menuitem", { name: "退出登录" }));
    const login = await screen.findByRole("link", { name: "登录" });
    await waitFor(() => expect(login).toHaveFocus());
    expect(screen.queryByRole("button", { name: "账号菜单" })).not.toBeInTheDocument();
    expect(toast.success).toHaveBeenCalledWith("已退出登录");
  });

  it("退出的 promise 被拒绝：重新请求当前用户，菜单保持打开，「退出登录」恢复可点", async () => {
    vi.mocked(logout).mockRejectedValue(new Error("network"));
    const { user, menu } = await openMenu();
    const fetchMock = vi.fn(async () => Response.json({ user: USER }));
    vi.stubGlobal("fetch", fetchMock);
    const item = within(menu).getByRole("menuitem", { name: "退出登录" });
    await user.click(item);
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith("/api/auth/me"));
    await waitFor(() => expect(item).not.toHaveAttribute("aria-disabled"));
    expect(screen.getByRole("menu")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "账号菜单" })).toBeInTheDocument();
    expect(toast.success).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/components/ui/dropdown-menu.test.tsx tests/components/portal/NavAccount.test.tsx`
Expected: FAIL，两个文件都报 `Error: Failed to resolve import`（`"@/components/ui/dropdown-menu"`、`"@/components/portal/NavAccount"`），`Test Files  2 failed (2)`，`Tests  no tests`。

- [ ] **Step 3: 写实现**

新建 `patra-portal/src/components/ui/dropdown-menu.tsx`：

```tsx
"use client";

import { Menu as MenuPrimitive } from "@base-ui/react/menu";
import { cn } from "@/lib/utils";

// shadcn base-nova 的 dropdown-menu，只留门户用到的部件，颜色、圆角、阴影和动效换成 token
// （高保真稿 Spec-Nav）。没用到的子菜单、勾选项、单选项没有画稿可以对照，不带。

function DropdownMenu({ ...props }: MenuPrimitive.Root.Props) {
  return <MenuPrimitive.Root data-slot="dropdown-menu" {...props} />;
}

function DropdownMenuTrigger({ ...props }: MenuPrimitive.Trigger.Props) {
  return <MenuPrimitive.Trigger data-slot="dropdown-menu-trigger" {...props} />;
}

/// 弹层：默认在触发器下方 8px；宽度取内容宽，240 ~ 320px。入场淡入、自上方 8px 落位并从 0.98
/// 缩放到 1，和快速检索相同；减弱动效时只淡入。阴影是 shadow-lg（它自带 1px 发丝描边），不另加描边。
function DropdownMenuContent({
  align = "start",
  alignOffset = 0,
  side = "bottom",
  sideOffset = 8,
  className,
  ...props
}: MenuPrimitive.Popup.Props &
  Pick<MenuPrimitive.Positioner.Props, "align" | "alignOffset" | "side" | "sideOffset">) {
  return (
    <MenuPrimitive.Portal>
      <MenuPrimitive.Positioner
        className="isolate z-50 outline-none"
        align={align}
        alignOffset={alignOffset}
        side={side}
        sideOffset={sideOffset}
      >
        <MenuPrimitive.Popup
          data-slot="dropdown-menu-content"
          className={cn(
            "max-h-(--available-height) w-max min-w-60 max-w-80 origin-(--transform-origin) overflow-x-hidden overflow-y-auto rounded-lg bg-bg-elevated p-1.5 text-fg-1 shadow-lg outline-none transition-opacity motion-safe:transition-[opacity,scale,translate] duration-300 ease-out-expo data-starting-style:opacity-0 motion-safe:data-starting-style:-translate-y-2 motion-safe:data-starting-style:scale-[0.98] data-ending-style:opacity-0 motion-safe:data-ending-style:-translate-y-1 motion-safe:data-ending-style:scale-[0.98]",
            className,
          )}
          {...props}
        />
      </MenuPrimitive.Positioner>
    </MenuPrimitive.Portal>
  );
}

function DropdownMenuGroup({ ...props }: MenuPrimitive.Group.Props) {
  return <MenuPrimitive.Group data-slot="dropdown-menu-group" {...props} />;
}

/// 分组的标题，不是菜单项，方向键不会停在这里；它的文字就是分组的读屏名称。
function DropdownMenuLabel({ className, ...props }: MenuPrimitive.GroupLabel.Props) {
  return (
    <MenuPrimitive.GroupLabel
      data-slot="dropdown-menu-label"
      className={cn("min-w-0 px-2.5 pt-2 pb-2.5", className)}
      {...props}
    />
  );
}

/// 菜单项：悬停或方向键高亮时换成凹陷底色。禁用时 55% 不透明度、不可点的光标，不关掉指针事件：
/// 关掉的话 `title`（比如「正在退出登录」）就不会显示。base-ui 的禁用项仍能被方向键高亮，
/// 所以高亮底色要排除禁用项。
function DropdownMenuItem({ className, ...props }: MenuPrimitive.Item.Props) {
  return (
    <MenuPrimitive.Item
      data-slot="dropdown-menu-item"
      className={cn(
        "relative flex h-9 w-full cursor-pointer items-center gap-2.5 rounded-md px-2.5 text-left text-base leading-5 text-fg-1 outline-hidden transition-colors select-none data-highlighted:not-data-disabled:bg-bg-sunken data-disabled:cursor-not-allowed data-disabled:opacity-55 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
        className,
      )}
      {...props}
    />
  );
}

/// 分隔线：左右顶到弹层边缘，下方留 6px。
function DropdownMenuSeparator({ className, ...props }: MenuPrimitive.Separator.Props) {
  return (
    <MenuPrimitive.Separator
      data-slot="dropdown-menu-separator"
      className={cn("-mx-1.5 mb-1.5 h-px bg-border-subtle", className)}
      {...props}
    />
  );
}

export {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
};
```

新建 `patra-portal/src/lib/auth/use-auth-page-href.ts`：

```ts
import { usePathname, useSearchParams } from "next/navigation";
import { authPageHref } from "@/lib/auth/next-path";

/// 导航栏里登录页、注册页的链接：带上当前地址（路径加查询串，不含 `#`）作 `next`。
/// 用了 `useSearchParams`：调用它的组件要包在 Suspense 里，否则静态渲染的页面在构建时报错。
export function useAuthPageHref(page: "/login" | "/register"): string {
  const pathname = usePathname();
  const query = useSearchParams().toString();
  return authPageHref(page, query ? `${pathname}?${query}` : pathname);
}
```

新建 `patra-portal/src/components/portal/UserMenu.tsx`：

```tsx
"use client";

import { LogOut } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

/// 圆形标识里的字：邮箱的第一个字符（按码点取），字母转大写，数字和符号原样
function emailInitial(email: string): string {
  return Array.from(email)[0]?.toUpperCase() ?? "";
}

interface UserMenuProps {
  email: string;
  /// 退出登录进行中：「退出登录」禁用
  loggingOut: boolean;
  onLogout: () => void;
}

/// 已登录时导航栏右上角的圆形标识和用户菜单（高保真稿 Nav-LoggedIn、Nav-MenuOpen、
/// Nav-MenuLongEmail、Nav-LoggingOut）。开合、方向键、Esc 和点外面关闭、关闭后焦点回到圆形，
/// 都用 base-ui 自带的。「退出登录」点了不关菜单：退出完成前菜单一直开着，完成后整个菜单
/// 随登录状态换成「登录」。
export function UserMenu({ email, loggingOut, onLogout }: UserMenuProps) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label="账号菜单"
        className="inline-flex size-9 shrink-0 items-center justify-center rounded-full bg-ink-900 font-serif text-md leading-none font-medium text-paper-50 tabular-nums transition-colors hover:bg-ink-800 data-popup-open:bg-ink-800"
      >
        {emailInitial(email)}
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuGroup>
          <DropdownMenuLabel>
            <div className="font-mono text-3xs leading-4 tracking-caps text-fg-3">当前账号</div>
            <div title={email} className="mt-0.5 truncate text-sm leading-5 font-medium text-fg-1">
              {email}
            </div>
          </DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuItem
            closeOnClick={false}
            disabled={loggingOut}
            title={loggingOut ? "正在退出登录" : undefined}
            onClick={onLogout}
          >
            <LogOut className="size-3.75 text-fg-3" aria-hidden="true" />
            退出登录
          </DropdownMenuItem>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
```

新建 `patra-portal/src/components/portal/NavAccount.tsx`：

```tsx
"use client";

import Link from "next/link";
import { useEffect, useRef } from "react";
import { UserMenu } from "@/components/portal/UserMenu";
import { useCurrentUser } from "@/lib/auth/current-user-query";
import { useLogout } from "@/lib/auth/logout-mutation";
import { useAuthPageHref } from "@/lib/auth/use-auth-page-href";
import { btnSecondary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

/// 登录状态还没确认时的圆形占位，也是 TopNav 里 Suspense 的 fallback。服务端渲染出的就是它，
/// 所以不会先闪一下「登录」再变成圆形标识。
export function NavAccountPlaceholder() {
  return (
    <span
      role="status"
      aria-label="正在确认登录状态"
      className="ml-1.5 size-9 shrink-0 rounded-full bg-bg-sunken max-[880px]:hidden"
    />
  );
}

/// 桌面导航栏的账号区，在「关于」右边：登录状态未定时是占位，未登录是「登录」，已登录是
/// 圆形标识和用户菜单。移动端顶栏不显示登录状态（高保真稿 Nav-M-TopBar），账号块在全屏菜单里。
export function NavAccount() {
  const state = useCurrentUser();
  const logout = useLogout();
  const loginHref = useAuthPageHref("/login");
  const loginRef = useRef<HTMLAnchorElement>(null);
  const loggedOut = logout.isSuccess && state.status === "anonymous";

  // 退出成功后圆形变回「登录」，原来聚焦的菜单项已经不在了：焦点移到「登录」上
  useEffect(() => {
    if (loggedOut) {
      loginRef.current?.focus();
    }
  }, [loggedOut]);

  if (state.status === "pending") {
    return <NavAccountPlaceholder />;
  }
  if (state.status === "anonymous") {
    return (
      <Link
        ref={loginRef}
        href={loginHref}
        className={cn(
          btnSecondary,
          "h-9 px-4 py-0 text-sm leading-5 font-medium max-[880px]:hidden",
        )}
      >
        登录
      </Link>
    );
  }
  return (
    <div className="ml-1.5 flex max-[880px]:hidden">
      <UserMenu
        email={state.user.email}
        loggingOut={logout.isPending}
        onLogout={() => logout.mutate()}
      />
    </div>
  );
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: 同 Step 2。
Expected: PASS，`Test Files  2 passed (2)`，`Tests  13 passed (13)`。

- [ ] **Step 5: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write src/components/ui/dropdown-menu.tsx src/lib/auth/use-auth-page-href.ts src/components/portal/UserMenu.tsx src/components/portal/NavAccount.tsx tests/components/ui/dropdown-menu.test.tsx tests/components/portal/NavAccount.test.tsx
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0，`lint` 输出 `Checked 289 files`；`Test Files  108 passed (108)`、`Tests  1003 passed (1003)`。

- [ ] **Step 6: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/src/components/ui/dropdown-menu.tsx patra-portal/src/lib/auth/use-auth-page-href.ts patra-portal/src/components/portal/UserMenu.tsx patra-portal/src/components/portal/NavAccount.tsx patra-portal/tests/components/ui/dropdown-menu.test.tsx patra-portal/tests/components/portal/NavAccount.test.tsx
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
feat(portal): 菜单原语和桌面导航栏的账号区 (PAP-68)

dropdown-menu 照 shadcn base-nova 的写法，只留用到的部件，颜色、阴影、动效换成 token。NavAccount
三种形态：登录状态未定时是圆形占位，未登录是带 next 的「登录」，已登录是邮箱首字符的圆形标识
和用户菜单。「退出登录」进行中禁用、菜单不关；退出成功后焦点移到「登录」上。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这 6 个文件；pre-commit 全部通过。

---

### Task 14: 移动端全屏菜单底部的账号块

**Files:**
- Create: `patra-portal/src/components/portal/MobileAccount.tsx`
- Modify: `patra-portal/src/components/auth/AuthLink.tsx`（加 `onClick`）
- Test: `patra-portal/tests/components/portal/MobileAccount.test.tsx`

**Interfaces:**
- Consumes: PAP-67 `useCurrentUser()`；Task 5 `useLogout()`；Task 7 `AuthLink`；Task 13 `useAuthPageHref`；`btnSecondary`、`cn`。
- Produces: `MobileAccount({ onClose }: { onClose: () => void })`、`MobileAccountPlaceholder()`；`AuthLink` 多一个可选的 `onClick?: () => void`。Task 15 把 `MobileAccount` 放进 `TopNav` 的全屏菜单，`onClose` 关闭菜单，Suspense 的 fallback 是 `MobileAccountPlaceholder`。

- [ ] **Step 1: 写失败的测试**

新建 `patra-portal/tests/components/portal/MobileAccount.test.tsx`：

```tsx
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { MobileAccount } from "@/components/portal/MobileAccount";
import { logout } from "@/lib/auth/actions";
import type { CurrentUser } from "@/lib/auth/types";
import { queryKeys } from "@/lib/query-keys";

// 覆写 usePathname / useSearchParams，保留 next/navigation 其余导出
vi.mock("next/navigation", async (importOriginal) => {
  const actual = await importOriginal<typeof import("next/navigation")>();
  return {
    ...actual,
    usePathname: () => "/papers",
    useSearchParams: () => new actual.ReadonlyURLSearchParams("q=GLP-1"),
  };
});
vi.mock("@/lib/auth/actions", () => ({ login: vi.fn(), logout: vi.fn(), register: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn() } }));

import { toast } from "sonner";

const USER: CurrentUser = { userId: "319041872872550658", email: "chen.yu@example.com" };
const LONG_EMAIL =
  "postdoctoral.researcher.zhang@cardiovascular-research.university-hospital.example.org";

/// 一个还没回来的请求，`answer` 让它在测试想要的时候回来
function deferred<T>() {
  let answer: (value: T) => void = () => {};
  const promise = new Promise<T>((resolve) => {
    answer = resolve;
  });
  return { promise, answer };
}

/// 当前用户：`undefined` 表示还没有数据（`/api/auth/me` 一直不回来），`null` 表示未登录
function renderAccount(user: CurrentUser | null | undefined) {
  // 和 createQueryClient 一样，数据 60 秒内不过期：挂载时不会因为数据过期而重新请求
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 60_000 } },
  });
  if (user === undefined) {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => new Promise(() => {})),
    );
  } else {
    client.setQueryData(queryKeys.currentUser, user);
  }
  const onClose = vi.fn();
  render(
    <QueryClientProvider client={client}>
      <MobileAccount onClose={onClose} />
    </QueryClientProvider>,
  );
  return { onClose, user: userEvent.setup() };
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe("MobileAccount · 三种形态", () => {
  it("登录状态未定：中性占位，读屏名称「正在确认登录状态」，不先出现「登录」", () => {
    renderAccount(undefined);
    expect(screen.getByRole("status", { name: "正在确认登录状态" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "登录" })).not.toBeInTheDocument();
  });

  it("未登录：「登录」按钮，下面一行「还没有账号？注册」，两个链接都带当前地址作 next", () => {
    renderAccount(null);
    expect(screen.getByRole("link", { name: "登录" })).toHaveAttribute(
      "href",
      "/login?next=%2Fpapers%3Fq%3DGLP-1",
    );
    expect(screen.getByText("还没有账号？")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "注册" })).toHaveAttribute(
      "href",
      "/register?next=%2Fpapers%3Fq%3DGLP-1",
    );
  });

  it.each(["登录", "注册"])("未登录：点「%s」先关闭菜单", async (name) => {
    const { user, onClose } = renderAccount(null);
    await user.click(screen.getByRole("link", { name }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("已登录：「当前账号」和邮箱，过长时截断、悬停看全；下面是「退出登录」", () => {
    renderAccount({ ...USER, email: LONG_EMAIL });
    expect(screen.getByText("当前账号")).toBeInTheDocument();
    const email = screen.getByText(LONG_EMAIL);
    expect(email).toHaveAttribute("title", LONG_EMAIL);
    expect(email).toHaveClass("truncate");
    expect(screen.getByRole("button", { name: "退出登录" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "登录" })).not.toBeInTheDocument();
  });
});

describe("MobileAccount · 退出登录", () => {
  it("进行中：「退出登录」禁用并说明原因，菜单不关；再点不会再发一次", async () => {
    const pending = deferred<void>();
    vi.mocked(logout).mockReturnValue(pending.promise);
    const { user, onClose } = renderAccount(USER);
    const button = screen.getByRole("button", { name: "退出登录" });
    await user.click(button);
    await waitFor(() => expect(button).toHaveAttribute("aria-disabled", "true"));
    expect(button).toHaveAttribute("title", "正在退出登录");
    await user.click(button);
    expect(logout).toHaveBeenCalledTimes(1);
    expect(onClose).not.toHaveBeenCalled();
    pending.answer();
    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
  });

  it("成功：关闭菜单，提示「已退出登录」", async () => {
    vi.mocked(logout).mockResolvedValue(undefined);
    const { user, onClose } = renderAccount(USER);
    await user.click(screen.getByRole("button", { name: "退出登录" }));
    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
    expect(toast.success).toHaveBeenCalledWith("已退出登录");
  });

  it("promise 被拒绝：重新请求当前用户，菜单不关，「退出登录」恢复可点", async () => {
    vi.mocked(logout).mockRejectedValue(new Error("network"));
    const { user, onClose } = renderAccount(USER);
    const fetchMock = vi.fn(async () => Response.json({ user: USER }));
    vi.stubGlobal("fetch", fetchMock);
    const button = screen.getByRole("button", { name: "退出登录" });
    await user.click(button);
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith("/api/auth/me"));
    await waitFor(() => expect(button).not.toHaveAttribute("aria-disabled"));
    expect(onClose).not.toHaveBeenCalled();
    expect(toast.success).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/components/portal/MobileAccount.test.tsx`
Expected: FAIL，`Error: Failed to resolve import "@/components/portal/MobileAccount" from "tests/components/portal/MobileAccount.test.tsx". Does the file exist?`，`Tests  no tests`。

- [ ] **Step 3: 写实现**

新建 `patra-portal/src/components/portal/MobileAccount.tsx`：

```tsx
"use client";

import { LogOut } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { AuthLink } from "@/components/auth/AuthLink";
import { useCurrentUser } from "@/lib/auth/current-user-query";
import { useLogout } from "@/lib/auth/logout-mutation";
import { useAuthPageHref } from "@/lib/auth/use-auth-page-href";
import { btnSecondary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

/// 「登录」「退出登录」：48px 高的次按钮，占满一行
const ACCOUNT_BUTTON = cn(btnSecondary, "flex h-12 w-full gap-2 py-0");

/// 账号块的外框：上方一条分隔线。三种形态的内容都是 80px 高，切换时菜单不跳
function Frame({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-3 border-t border-border-default pt-5">
      {children}
    </div>
  );
}

/// 登录状态还没确认时的中性占位，也是 TopNav 里 Suspense 的 fallback
export function MobileAccountPlaceholder() {
  return (
    <Frame>
      <span
        role="status"
        aria-label="正在确认登录状态"
        className="block h-20 rounded-sm bg-bg-sunken"
      />
    </Frame>
  );
}

interface MobileAccountProps {
  /// 关闭全屏菜单：点「登录」「注册」时，以及退出成功后
  onClose: () => void;
}

/// 移动端全屏菜单底部的账号块（高保真稿 Nav-M-MenuLoggedOut、Nav-M-MenuLoggedIn、
/// Nav-M-MenuLongEmail、Nav-M-MenuLoggingOut、Nav-M-MenuPending）。
/// 「退出登录」进行中用 `aria-disabled` 而不是 `disabled`：按钮不丢焦点，`title` 照常显示。
export function MobileAccount({ onClose }: MobileAccountProps) {
  const state = useCurrentUser();
  const logout = useLogout();
  const loginHref = useAuthPageHref("/login");
  const registerHref = useAuthPageHref("/register");

  if (state.status === "pending") {
    return <MobileAccountPlaceholder />;
  }
  if (state.status === "anonymous") {
    return (
      <Frame>
        <Link href={loginHref} onClick={onClose} className={ACCOUNT_BUTTON}>
          登录
        </Link>
        <p className="text-center text-base leading-5 text-fg-2">
          还没有账号？
          <AuthLink href={registerHref} onClick={onClose}>
            注册
          </AuthLink>
        </p>
      </Frame>
    );
  }
  return (
    <Frame>
      <p className="flex min-w-0 items-baseline gap-2.5 text-base leading-5">
        <span className="shrink-0 font-mono text-3xs leading-4 tracking-caps text-fg-3">
          当前账号
        </span>
        <span title={state.user.email} className="min-w-0 truncate font-medium text-fg-1">
          {state.user.email}
        </span>
      </p>
      <button
        type="button"
        aria-disabled={logout.isPending || undefined}
        title={logout.isPending ? "正在退出登录" : undefined}
        onClick={() => {
          if (!logout.isPending) {
            logout.mutate(undefined, { onSuccess: () => onClose() });
          }
        }}
        className={ACCOUNT_BUTTON}
      >
        <LogOut className="size-3.75" aria-hidden="true" />
        退出登录
      </button>
    </Frame>
  );
}
```

「注册」用 `AuthLink`，点击时要先关闭全屏菜单，给它加上 `onClick`；说明多了一句，顺手重新折行：

```diff
diff --git a/patra-portal/src/components/auth/AuthLink.tsx b/patra-portal/src/components/auth/AuthLink.tsx
--- a/patra-portal/src/components/auth/AuthLink.tsx
+++ b/patra-portal/src/components/auth/AuthLink.tsx
@@ -8,17 +8,20 @@ interface AuthLinkProps {
   replace?: boolean;
   /// 下划线常驻，不只在悬停、键盘聚焦时画出：错误文案里的「去登录」
   drawn?: boolean;
+  /// 点击时先做的事：移动端全屏菜单里的「注册」先关闭菜单
+  onClick?: () => void;
   children: ReactNode;
 }

-/// 登录、注册相关的文字链接：「注册」「登录」「去登录」。悬停和键盘聚焦时下划线自左画出：
-/// 下划线画在里层的 span 上，由外层链接的 hover / focus-visible 触发。移动端上下各加 12px
-/// 点按区，又不撑高这一行。
-export function AuthLink({ href, replace, drawn, children }: AuthLinkProps) {
+/// 登录、注册相关的文字链接：「注册」「登录」「去登录」，以及移动端账号块里的「注册」。
+/// 悬停和键盘聚焦时下划线自左画出：下划线画在里层的 span 上，由外层链接的 hover / focus-visible
+/// 触发。移动端上下各加 12px 点按区，又不撑高这一行。
+export function AuthLink({ href, replace, drawn, onClick, children }: AuthLinkProps) {
   return (
     <Link
       href={href}
       replace={replace}
+      onClick={onClick}
       className="group/link rounded-xs font-medium text-link hover:text-link-hover max-[880px]:-my-3 max-[880px]:inline-block max-[880px]:py-3"
     >
       <span
```

- [ ] **Step 4: 跑测试，确认通过**

Run: 同 Step 2。
Expected: PASS，`Tests  8 passed (8)`。

- [ ] **Step 5: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write src/components/portal/MobileAccount.tsx src/components/auth/AuthLink.tsx tests/components/portal/MobileAccount.test.tsx
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0，`lint` 输出 `Checked 291 files`；`Test Files  109 passed (109)`、`Tests  1011 passed (1011)`。

- [ ] **Step 6: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/src/components/portal/MobileAccount.tsx patra-portal/src/components/auth/AuthLink.tsx patra-portal/tests/components/portal/MobileAccount.test.tsx
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
feat(portal): 移动端全屏菜单底部的账号块 (PAP-68)

三种形态都是 80px 高，切换时菜单不跳：登录状态未定时是中性占位；未登录是「登录」和「还没有
账号？注册」，都带当前地址作 next，点了先关闭菜单；已登录是当前账号和「退出登录」，退出进行中
用 aria-disabled，成功后关闭菜单。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这 3 个文件；pre-commit 全部通过。

---

### Task 15: 账号区放进导航栏

**Files:**
- Modify: `patra-portal/src/components/portal/TopNav.tsx`
- Test: `patra-portal/tests/components/portal/TopNav.test.tsx`、`patra-portal/tests/app/loading-busy.test.tsx`（补 QueryClientProvider 和 `/api/auth/me` 的桩）

**Interfaces:**
- Consumes: Task 13 `NavAccount`、`NavAccountPlaceholder`；Task 14 `MobileAccount`、`MobileAccountPlaceholder`。
- Produces: 桌面导航栏「关于」右边是账号区；移动端全屏菜单最底部是账号块，用错峰入场的第 5 档。两处各包一层 Suspense。Task 16 的 e2e 从导航栏走完整个流程。

- [ ] **Step 1: 写失败的测试**

`TopNav` 里多了要读当前用户的组件，两个测试文件都补上 QueryClientProvider 和 `/api/auth/me` 的桩，原有断言不变：

```diff
diff --git a/patra-portal/tests/app/loading-busy.test.tsx b/patra-portal/tests/app/loading-busy.test.tsx
--- a/patra-portal/tests/app/loading-busy.test.tsx
+++ b/patra-portal/tests/app/loading-busy.test.tsx
@@ -1,6 +1,7 @@
+import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
 import { render, screen } from "@testing-library/react";
 import type { ComponentType } from "react";
-import { describe, expect, it, vi } from "vitest";
+import { afterEach, describe, expect, it, vi } from "vitest";
 import "@testing-library/jest-dom/vitest";
 import JournalsLoading from "@/app/journals/(list)/loading";
 import JournalDetailLoading from "@/app/journals/[id]/loading";
@@ -9,7 +10,17 @@ import PaperDetailLoading from "@/app/papers/[id]/loading";

 vi.mock("next/navigation", async (importOriginal) => {
   const actual = await importOriginal<typeof import("next/navigation")>();
-  return { ...actual, usePathname: () => "/", useRouter: () => ({ push: vi.fn() }) };
+  return {
+    ...actual,
+    usePathname: () => "/",
+    useSearchParams: () => new actual.ReadonlyURLSearchParams(),
+    useRouter: () => ({ push: vi.fn() }),
+  };
+});
+vi.mock("@/lib/auth/actions", () => ({ login: vi.fn(), logout: vi.fn(), register: vi.fn() }));
+
+afterEach(() => {
+  vi.unstubAllGlobals();
 });

 describe("各路由加载骨架", () => {
@@ -19,7 +30,16 @@ describe("各路由加载骨架", () => {
     ["/papers/[id]", PaperDetailLoading],
     ["/journals/[id]", JournalDetailLoading],
   ])("%s 的 main 标记 aria-busy", (_, Loading) => {
-    render(<Loading />);
+    // 骨架里的导航栏要读当前用户：`/api/auth/me` 回未登录
+    vi.stubGlobal(
+      "fetch",
+      vi.fn(async () => Response.json({ user: null })),
+    );
+    render(
+      <QueryClientProvider client={new QueryClient()}>
+        <Loading />
+      </QueryClientProvider>,
+    );
     expect(screen.getByRole("main")).toHaveAttribute("aria-busy", "true");
   });
 });
diff --git a/patra-portal/tests/components/portal/TopNav.test.tsx b/patra-portal/tests/components/portal/TopNav.test.tsx
--- a/patra-portal/tests/components/portal/TopNav.test.tsx
+++ b/patra-portal/tests/components/portal/TopNav.test.tsx
@@ -1,40 +1,63 @@
+import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
 import { render, screen, waitFor, within } from "@testing-library/react";
 import userEvent from "@testing-library/user-event";
 import { afterEach, describe, expect, it, vi } from "vitest";
 import "@testing-library/jest-dom/vitest";
 import { TopNav } from "@/components/portal/TopNav";
+import type { CurrentUser } from "@/lib/auth/types";

-// 覆写 usePathname / useRouter，保留 next/navigation 其余导出（Next <Link> 渲染所需）
+// 覆写 usePathname / useSearchParams / useRouter，保留 next/navigation 其余导出（Next <Link> 渲染所需）
 let mockPathname = "/";
 const mockPush = vi.fn<(url: string) => void>();
 vi.mock("next/navigation", async (importOriginal) => {
   const actual = await importOriginal<typeof import("next/navigation")>();
-  return { ...actual, usePathname: () => mockPathname, useRouter: () => ({ push: mockPush }) };
+  return {
+    ...actual,
+    usePathname: () => mockPathname,
+    useSearchParams: () => new actual.ReadonlyURLSearchParams(),
+    useRouter: () => ({ push: mockPush }),
+  };
 });
+vi.mock("@/lib/auth/actions", () => ({ login: vi.fn(), logout: vi.fn(), register: vi.fn() }));
+
+/// 账号区要读当前用户：每次用新的 QueryClient，`/api/auth/me` 回 `user`（默认未登录）
+function renderTopNav(user: CurrentUser | null = null) {
+  vi.stubGlobal(
+    "fetch",
+    vi.fn(async () => Response.json({ user })),
+  );
+  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
+  return render(
+    <QueryClientProvider client={client}>
+      <TopNav />
+    </QueryClientProvider>,
+  );
+}

 afterEach(() => {
   mockPathname = "/";
   mockPush.mockClear();
+  vi.unstubAllGlobals();
 });

 describe("TopNav 快速检索", () => {
   it("⌘K 打开快速检索对话框", async () => {
     const user = userEvent.setup();
-    render(<TopNav />);
+    renderTopNav();
     await user.keyboard("{Meta>}k{/Meta}");
     expect(await screen.findByRole("dialog", { name: "快速检索" })).toBeInTheDocument();
   });

   it("Ctrl+K 同样打开", async () => {
     const user = userEvent.setup();
-    render(<TopNav />);
+    renderTopNav();
     await user.keyboard("{Control>}k{/Control}");
     expect(await screen.findByRole("dialog", { name: "快速检索" })).toBeInTheDocument();
   });

   it("点导航「快速检索」打开对话框，提交关键词跳 /papers?q= 并关闭", async () => {
     const user = userEvent.setup();
-    render(<TopNav />);
+    renderTopNav();
     await user.click(screen.getByRole("button", { name: /快速检索/ }));
     const dialog = await screen.findByRole("dialog", { name: "快速检索" });
     await user.type(within(dialog).getByRole("textbox"), "GLP-1");
@@ -47,7 +70,7 @@ describe("TopNav 快速检索", () => {

   it("对话框的键位提示只写真实可用的按键（不写「Tab 切换检索方式」）", async () => {
     const user = userEvent.setup();
-    render(<TopNav />);
+    renderTopNav();
     await user.keyboard("{Meta>}k{/Meta}");
     const dialog = await screen.findByRole("dialog", { name: "快速检索" });
     expect(within(dialog).getByText("↵ 检索")).toBeInTheDocument();
@@ -55,7 +78,7 @@ describe("TopNav 快速检索", () => {
   });

   it("「关于」指向页脚 #about", () => {
-    render(<TopNav />);
+    renderTopNav();
     expect(screen.getByRole("link", { name: "关于" })).toHaveAttribute("href", "#about");
   });
 });
@@ -69,13 +92,13 @@ describe("TopNav 快捷键提示", () => {

   it("Apple 平台写 ⌘K", async () => {
     setPlatform("MacIntel");
-    render(<TopNav />);
+    renderTopNav();
     expect(await screen.findByText("⌘K")).toBeInTheDocument();
   });

   it("其他平台写 Ctrl K（Windows / Linux 实际按 Ctrl+K）", async () => {
     setPlatform("Win32");
-    render(<TopNav />);
+    renderTopNav();
     expect(await screen.findByText("Ctrl K")).toBeInTheDocument();
     expect(screen.queryByText("⌘K")).not.toBeInTheDocument();
   });
@@ -83,19 +106,19 @@ describe("TopNav 快捷键提示", () => {

 describe("TopNav", () => {
   it("渲染 banner role", () => {
-    render(<TopNav />);
+    renderTopNav();
     expect(screen.getByRole("banner")).toBeInTheDocument();
   });

   it("在首页路径下，首页 link 标记为 aria-current=page", () => {
     mockPathname = "/";
-    render(<TopNav />);
+    renderTopNav();
     const homeLink = screen.getByRole("link", { name: /首页/ });
     expect(homeLink).toHaveAttribute("aria-current", "page");
   });

   it("期刊 link 指向 /journals 且不再 disabled", () => {
-    render(<TopNav />);
+    renderTopNav();
     const journalLink = screen.getByRole("link", { name: /期刊/ });
     expect(journalLink).toHaveAttribute("href", "/journals");
     expect(journalLink).not.toHaveAttribute("aria-disabled");
@@ -103,7 +126,7 @@ describe("TopNav", () => {

   it("在 /journals 路径下，期刊 高亮、首页 不再高亮", () => {
     mockPathname = "/journals";
-    render(<TopNav />);
+    renderTopNav();
     const journalLink = screen.getByRole("link", { name: /期刊/ });
     expect(journalLink).toHaveAttribute("aria-current", "page");
     const homeLink = screen.getByRole("link", { name: /首页/ });
@@ -112,26 +135,26 @@ describe("TopNav", () => {

   it("在 /journals 子路由（详情）下，期刊 仍高亮", () => {
     mockPathname = "/journals/123";
-    render(<TopNav />);
+    renderTopNav();
     const journalLink = screen.getByRole("link", { name: /期刊/ });
     expect(journalLink).toHaveAttribute("aria-current", "page");
   });

   it("主题 tab 已移除（不再渲染）", () => {
-    render(<TopNav />);
+    renderTopNav();
     expect(screen.queryByText("主题")).not.toBeInTheDocument();
   });

   it("点击汉堡按钮打开 Sheet drawer", async () => {
     const user = userEvent.setup();
-    render(<TopNav />);
+    renderTopNav();
     const menuBtn = screen.getByRole("button", { name: /打开菜单|menu/i });
     await user.click(menuBtn);
     expect(await screen.findByRole("dialog")).toBeInTheDocument();
   });

   it("文献 link 指向 /papers，不再 disabled / SOON", () => {
-    render(<TopNav />);
+    renderTopNav();
     const link = screen.getByRole("link", { name: "文献" });
     expect(link).toHaveAttribute("href", "/papers");
     expect(link).not.toHaveAttribute("aria-disabled");
@@ -140,13 +163,13 @@ describe("TopNav", () => {

   it("在 /papers 与 /papers/[id] 下，文献 高亮", () => {
     mockPathname = "/papers/123";
-    render(<TopNav />);
+    renderTopNav();
     expect(screen.getByRole("link", { name: "文献" })).toHaveAttribute("aria-current", "page");
   });

   it("移动菜单的关闭按钮用中文可访问名「关闭」", async () => {
     const user = userEvent.setup();
-    render(<TopNav />);
+    renderTopNav();
     await user.click(screen.getByRole("button", { name: /打开菜单|menu/i }));
     const dialog = await screen.findByRole("dialog");
     expect(within(dialog).getByRole("button", { name: "关闭" })).toBeInTheDocument();
@@ -154,9 +177,39 @@ describe("TopNav", () => {

   it("移动菜单中文献同样可点", async () => {
     const user = userEvent.setup();
-    render(<TopNav />);
+    renderTopNav();
     await user.click(screen.getByRole("button", { name: /打开菜单|menu/i }));
     const dialog = await screen.findByRole("dialog");
     expect(within(dialog).getByRole("link", { name: "文献" })).toHaveAttribute("href", "/papers");
   });
 });
+
+describe("TopNav 账号区", () => {
+  it("桌面：「关于」右边是账号区，确认登录状态前是占位，未登录时换成「登录」", async () => {
+    renderTopNav();
+    const placeholder = screen.getByRole("status", { name: "正在确认登录状态" });
+    const about = screen.getByRole("link", { name: "关于" });
+    expect(
+      about.compareDocumentPosition(placeholder) & Node.DOCUMENT_POSITION_FOLLOWING,
+    ).toBeTruthy();
+    expect(await screen.findByRole("link", { name: "登录" })).toHaveAttribute("href", "/login");
+  });
+
+  it("桌面：已登录时是圆形标识", async () => {
+    renderTopNav({ userId: "319041872872550658", email: "chen.yu@example.com" });
+    expect(await screen.findByRole("button", { name: "账号菜单" })).toHaveTextContent("C");
+  });
+
+  it("移动菜单最底部是账号块，点「登录」先关闭菜单", async () => {
+    const user = userEvent.setup();
+    renderTopNav();
+    await user.click(screen.getByRole("button", { name: /打开菜单|menu/i }));
+    const dialog = await screen.findByRole("dialog");
+    expect(await within(dialog).findByRole("link", { name: "注册" })).toHaveAttribute(
+      "href",
+      "/register",
+    );
+    await user.click(within(dialog).getByRole("link", { name: "登录" }));
+    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
+  });
+});
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/components/portal/TopNav.test.tsx tests/app/loading-busy.test.tsx`
Expected: FAIL，`Tests  3 failed | 22 passed (25)`。三条分别是 `Unable to find an accessible element with the role "status" and name "正在确认登录状态"`、`Unable to find role="button" and name "账号菜单"`、`Unable to find role="link" and name "注册"`。

- [ ] **Step 3: 写实现**

```diff
diff --git a/patra-portal/src/components/portal/TopNav.tsx b/patra-portal/src/components/portal/TopNav.tsx
--- a/patra-portal/src/components/portal/TopNav.tsx
+++ b/patra-portal/src/components/portal/TopNav.tsx
@@ -4,8 +4,10 @@ import { ArrowUpRight, Menu, Search } from "lucide-react";
 import dynamic from "next/dynamic";
 import Link from "next/link";
 import { usePathname } from "next/navigation";
-import { useEffect, useState } from "react";
+import { Suspense, useEffect, useState } from "react";
 import { BrandMark } from "@/components/portal/BrandMark";
+import { MobileAccount, MobileAccountPlaceholder } from "@/components/portal/MobileAccount";
+import { NavAccount, NavAccountPlaceholder } from "@/components/portal/NavAccount";
 import { Venation } from "@/components/portal/Venation";
 import { buttonVariants } from "@/components/ui/button";
 import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
@@ -27,8 +29,14 @@ const NAV_ITEMS = [
   { label: "期刊", href: "/journals" },
 ] as const;

-/// 移动菜单逐项入场的错峰（静态类名，Tailwind 才能生成）
-const STAGGER = ["[--delay:60ms]", "[--delay:120ms]", "[--delay:180ms]", "[--delay:240ms]"];
+/// 移动菜单逐项入场的错峰（静态类名，Tailwind 才能生成）；最后一档给底部的账号块
+const STAGGER = [
+  "[--delay:60ms]",
+  "[--delay:120ms]",
+  "[--delay:180ms]",
+  "[--delay:240ms]",
+  "[--delay:300ms]",
+];

 /// 当前路径是否命中导航项。首页要求精确匹配，其余按前缀匹配以覆盖 `/papers/[id]`、`/journals/[id]` 等子路由。
 function isNavActive(pathname: string, href: string): boolean {
@@ -38,6 +46,8 @@ function isNavActive(pathname: string, href: string): boolean {

 /// 吸顶导航：内容区恒高 56px，另有 1px 下边框（详情页面包屑 / 侧栏的吸顶偏移依赖它），滚动离开顶部后才出现纸色毛玻璃与发丝线。
 /// ⌘K / Ctrl+K 与「快速检索」按钮在任何页面唤起检索对话框。
+/// 账号区：桌面在「关于」右边，移动端在全屏菜单最底部。两处都读查询串拼 `next`，各包一层 Suspense，
+/// 否则静态渲染的页面在构建时报错；fallback 就是登录状态未定时的占位。
 export function TopNav() {
   const [menuOpen, setMenuOpen] = useState(false);
   const [searchOpen, setSearchOpen] = useState(false);
@@ -139,6 +149,9 @@ export function TopNav() {
           >
             <span className="link-draw pb-px">关于</span>
           </a>
+          <Suspense fallback={<NavAccountPlaceholder />}>
+            <NavAccount />
+          </Suspense>
           <Sheet open={menuOpen} onOpenChange={setMenuOpen}>
             <SheetTrigger
               aria-label="打开菜单"
@@ -206,6 +219,11 @@ export function TopNav() {
                   </a>
                 </div>
               </div>
+              <div className={cn("anim-rise mt-auto", STAGGER[4])}>
+                <Suspense fallback={<MobileAccountPlaceholder />}>
+                  <MobileAccount onClose={() => setMenuOpen(false)} />
+                </Suspense>
+              </div>
             </SheetContent>
           </Sheet>
         </div>
```

- [ ] **Step 4: 跑测试，确认通过**

Run: 同 Step 2。
Expected: PASS，`Tests  25 passed (25)`。

- [ ] **Step 5: 生产构建**

`useSearchParams` 漏包 Suspense 只在构建时报错（404 页是静态预渲染的，导航栏也在里面），所以这里要跑一次。Next 16 的 `next build` 和正在跑的 `next dev` 输出目录分开，不会互相影响。

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal build`
Expected: 退出 0，`✓ Compiled successfully`；路由表里有 `ƒ /login`、`ƒ /register` 和 `ƒ Proxy (Middleware)`，原有的路由不变。

- [ ] **Step 6: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write src/components/portal/TopNav.tsx tests/components/portal/TopNav.test.tsx tests/app/loading-busy.test.tsx
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0，`lint` 输出 `Checked 291 files`；`Test Files  109 passed (109)`、`Tests  1014 passed (1014)`。

- [ ] **Step 7: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/src/components/portal/TopNav.tsx patra-portal/tests/components/portal/TopNav.test.tsx patra-portal/tests/app/loading-busy.test.tsx
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
feat(portal): 账号区放进导航栏 (PAP-68)

桌面在「关于」右边，移动端在全屏菜单最底部。两处都读查询串拼 next，各包一层 Suspense，否则
静态渲染的页面在构建时报错；fallback 就是登录状态未定时的占位，服务端渲染出的也是它。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这 3 个文件；pre-commit 全部通过。

---

### Task 16: 账号流程的端到端冒烟

前面的任务都做完之后，从导航栏走一遍完整的流程（spec 第 14 节）。这一步验证的是已经做好的功能，用例一写就应该通过；不通过说明前面哪个任务有问题，按 superpowers:systematic-debugging 查原因，不要改断言凑结果。

**Files:**
- Test: `patra-portal/tests/e2e/auth.spec.ts`（加账号流程）

**Interfaces:**
- Consumes: Task 1 ~ 15 的全部功能；Task 10 的 `waitForTakeover`；mini 上的网关 `http://100.103.73.27:9528`。
- Produces: 账号流程的 e2e：没有 `PATRA_GATEWAY_BASE_URL` 时跳过。每跑一次在 mini 上多一个一次性账号（`e2e-<随机>@example.org`），dev 数据可以丢弃。

- [ ] **Step 1: 写 e2e**

每次 `goto` 之后先 `waitForTakeover(page, "header")`，等页面接管导航栏再点：接管之前点「登录」是整页跳转，不是 replace，历史记录的断言会失败；点「账号菜单」则没有反应。

```diff
diff --git a/patra-portal/tests/e2e/auth.spec.ts b/patra-portal/tests/e2e/auth.spec.ts
--- a/patra-portal/tests/e2e/auth.spec.ts
+++ b/patra-portal/tests/e2e/auth.spec.ts
@@ -1,3 +1,4 @@
+import { randomBytes } from "node:crypto";
 import { expect, type Page, test } from "@playwright/test";

 /// 真人点一下要按住一会儿。Playwright 默认的点击按下就松开，测不出按住期间页面的挤动
@@ -12,6 +13,32 @@ async function waitForTakeover(page: Page, selector: string) {
   }, selector);
 }

+const SOURCE = "/papers?q=GLP-1";
+const SESSION_COOKIES = ["patra_session", "__Host-patra_session"];
+/// 提交后要经过网关和目标页的服务端渲染，比默认的 5 秒宽一些
+const ROUND_TRIP = { timeout: 15_000 };
+
+/// 每跑一次在网关后面注册一个一次性账号：邮箱随机，密码运行时生成，都不写进仓库
+function freshAccount() {
+  return {
+    email: `e2e-${randomBytes(6).toString("hex")}@example.org`,
+    password: randomBytes(12).toString("base64url"),
+  };
+}
+
+/// 当前标签页的历史记录条数
+const historyLength = (page: Page) => page.evaluate(() => history.length);
+
+/// 点开用户菜单，点「退出登录」：仍在当前页，导航栏回到「登录」，焦点在它上面
+async function logOut(page: Page, path: string) {
+  const nav = page.getByRole("banner");
+  await nav.getByRole("button", { name: "账号菜单" }).click();
+  await page.getByRole("menuitem", { name: "退出登录" }).click();
+  await expect(page.getByText("已退出登录")).toBeVisible(ROUND_TRIP);
+  await expect(nav.getByRole("link", { name: "登录" })).toBeFocused();
+  await expect(page).toHaveURL(path);
+}
+
 /// 离开字段时冒出的错误行会把下方的链接、按钮往下挤。按下和松开之间挤动了，松开时落在别的元素上，
 /// 这次点击就没了。只有真实浏览器有布局，组件测试测不到，所以放在这里
 test.describe("登录页、注册页：离开字段时冒出的错误不让下方的点击落空", () => {
@@ -75,3 +102,77 @@ test.describe("页面接管表单之前就提交：邮箱、密码不进地址
     await expect(page.getByRole("heading", { level: 1, name: "注册" })).toBeVisible();
   });
 });
+
+/// 要连上网关（PATRA_GATEWAY_BASE_URL），用单个 worker 跑（--workers=1）：并行时 next dev 同时冷编译
+/// 好几个路由，Fast Refresh 重建会让页内跳转退化成整页加载，流程会点到页面还没接管的表单
+test.describe("账号：注册、登录、退出", () => {
+  test.skip(!process.env.PATRA_GATEWAY_BASE_URL, "未设置 PATRA_GATEWAY_BASE_URL：跳过账号流程");
+
+  test("从文献检索页注册、退出、再登录、再退出，每次都回到原页面", async ({ page }) => {
+    test.setTimeout(90_000);
+    const account = freshAccount();
+    const nav = page.getByRole("banner");
+    const initial = account.email[0]?.toUpperCase() ?? "";
+
+    await test.step("导航栏「登录」→ 登录页 → 互跳到注册页，next 一路带着", async () => {
+      await page.goto(SOURCE);
+      await waitForTakeover(page, "header");
+      const entries = await historyLength(page);
+      await nav.getByRole("link", { name: "登录" }).click();
+      await expect(page).toHaveURL("/login?next=%2Fpapers%3Fq%3DGLP-1");
+      await page.getByRole("link", { name: "注册" }).click();
+      await expect(page).toHaveURL("/register?next=%2Fpapers%3Fq%3DGLP-1");
+
+      await page.getByLabel("邮箱").fill(account.email);
+      await page.getByLabel("密码", { exact: true }).fill(account.password);
+      await page.getByLabel("确认密码").fill(account.password);
+      await page.getByRole("button", { name: "注册" }).click();
+
+      await expect(page).toHaveURL(SOURCE, ROUND_TRIP);
+      await expect(page.getByText("注册成功，已登录")).toBeVisible();
+      await expect(nav.getByRole("button", { name: "账号菜单" })).toHaveText(initial);
+      // 登录页、注册页都被替换掉了：只比点「登录」之前多一条（来源页本身）
+      expect(await historyLength(page)).toBe(entries + 1);
+    });
+
+    await test.step("后退不会回到注册页或登录页", async () => {
+      await page.goBack();
+      await expect(page).toHaveURL(SOURCE);
+      await expect(page.getByRole("heading", { level: 1, name: /登录|注册/ })).toHaveCount(0);
+    });
+
+    await test.step("退出登录：留在原页面", async () => {
+      await logOut(page, SOURCE);
+    });
+
+    await test.step("再登录一次：回到原页面，页面脚本读不到会话 Cookie", async () => {
+      await nav.getByRole("link", { name: "登录" }).click();
+      await page.getByLabel("邮箱").fill(account.email);
+      await page.getByLabel("密码", { exact: true }).fill(account.password);
+      await page.getByRole("button", { name: "登录" }).click();
+
+      await expect(page).toHaveURL(SOURCE, ROUND_TRIP);
+      await expect(page.getByText("已登录", { exact: true })).toBeVisible();
+      await expect(nav.getByRole("button", { name: "账号菜单" })).toHaveText(initial);
+
+      const cookies = await page.context().cookies();
+      const session = cookies.find((cookie) => SESSION_COOKIES.includes(cookie.name));
+      expect(session?.httpOnly).toBe(true);
+      const readable = await page.evaluate(() => document.cookie);
+      for (const name of SESSION_COOKIES) {
+        expect(readable).not.toContain(name);
+      }
+    });
+
+    await test.step("已登录时直接打开登录页：跳到 next，不显示表单", async () => {
+      await page.goto("/login?next=%2Fjournals");
+      await expect(page).toHaveURL("/journals");
+      await waitForTakeover(page, "header");
+      await expect(page.getByLabel("邮箱")).toHaveCount(0);
+    });
+
+    await test.step("再退出一次", async () => {
+      await logOut(page, "/journals");
+    });
+  });
+});
```

- [ ] **Step 2: 不连网关跑，账号流程跳过**

Run: `env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY -u all_proxy -u ALL_PROXY pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec playwright test tests/e2e/auth.spec.ts`
Expected: `5 passed`、`1 skipped`。

- [ ] **Step 3: 连 mini 的网关跑**

4000 端口上要么没有门户在跑，要么是带着同一个网关地址的 `portal-dev`。

Run: `env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY -u all_proxy -u ALL_PROXY PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528 pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec playwright test tests/e2e/auth.spec.ts --workers=1`
Expected: PASS，`6 passed`。账号流程那条几秒到三十秒不等：路由第一次打开时要现编译。

- [ ] **Step 4: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write tests/e2e/auth.spec.ts
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0，`lint` 输出 `Checked 291 files`；`Test Files  109 passed (109)`、`Tests  1014 passed (1014)`。

- [ ] **Step 5: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/tests/e2e/auth.spec.ts
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
test(portal): 账号流程的端到端冒烟 (PAP-68)

从文献检索页点「登录」，互跳到注册页，注册后回到原页面；后退不回登录页、注册页；退出登录、
再登录、再退出，每次都回到原页面；登录后页面脚本读不到会话 Cookie；已登录时打开登录页直接跳到
next。要连网关，没有 PATRA_GATEWAY_BASE_URL 时跳过；单个 worker 跑。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这一个文件；pre-commit 全部通过。

---

### Task 17: 开发时终端不打印 Server Action 的参数

Next 16 开发时默认在终端打印每次 Server Action 调用的参数，形如 `└─ ƒ login({"email":"…","password":"…"}) in 219ms lib/auth/actions.ts`，密码是明文。spec 第 13 节和 PAP-67 设计第 12 节都要求密码不进日志。写计划时在临时副本上跑视觉对照，开发服务器的输出里就有这样的行。只有开发时打印（`node_modules/next/dist/server/app-render/action-handler.js` 里判断了 `NODE_ENV === 'development'`），开关是 `next.config.ts` 的 `logging.serverFunctions`，默认 `true`。全仓库只有 `src/lib/auth/actions.ts` 一个 `"use server"` 文件，关掉不会少了别的调试信息；请求行（`POST /login?next=%2Fpapers 200 in …`）照常打印。

**Files:**
- Modify: `patra-portal/next.config.ts`
- Test: `patra-portal/tests/next-config.test.ts`

**Interfaces:**
- Consumes: 无。
- Produces: 开发服务器的输出里不再有 `└─ ƒ …` 这样的行，请求行照常。Task 19 读 `portal-dev` 的日志时依赖这一点。

- [ ] **Step 1: 写失败的测试**

新建 `patra-portal/tests/next-config.test.ts`。`next.config.ts` 在 portal 的根目录，没有路径别名，这里用相对路径：

```ts
// @vitest-environment node
import { describe, expect, it } from "vitest";
import nextConfig from "../next.config";

describe("next.config", () => {
  it("开发时不在终端打印 Server Action 的参数：登录、注册的参数里有明文密码", () => {
    expect(nextConfig.logging).toMatchObject({ serverFunctions: false });
  });
});
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec vitest run tests/next-config.test.ts`
Expected: FAIL，`AssertionError: expected undefined to match object { serverFunctions: false }`，`Tests  1 failed (1)`。

- [ ] **Step 3: 写实现**

注释照这个文件原有的写法用 `//`：

```diff
diff --git a/patra-portal/next.config.ts b/patra-portal/next.config.ts
--- a/patra-portal/next.config.ts
+++ b/patra-portal/next.config.ts
@@ -4,6 +4,8 @@ const nextConfig: NextConfig = {
   reactStrictMode: true,
   // 产出自包含 Node server 到 .next/standalone（容器化运行，镜像精简）
   output: "standalone",
+  // 开发时 Next 默认在终端打印每次 Server Action 调用的参数，登录、注册的参数里有明文密码
+  logging: { serverFunctions: false },
 };

 export default nextConfig;
```

- [ ] **Step 4: 跑测试，确认通过**

Run: 同 Step 2。
Expected: PASS，`Tests  1 passed (1)`。

- [ ] **Step 5: 格式和合格线**

```bash
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal exec biome check --write next.config.ts tests/next-config.test.ts
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal lint
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal typecheck
pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test
```

Expected: Biome 输出 `No fixes applied`；三条命令都退出 0，`lint` 输出 `Checked 292 files`；`Test Files  110 passed (110)`、`Tests  1015 passed (1015)`。开发服务器上的效果在 Task 19 Step 4 核对。

- [ ] **Step 6: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add patra-portal/next.config.ts patra-portal/tests/next-config.test.ts
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
fix(portal): 开发时终端不再打印登录、注册的参数 (PAP-68)

Next 16 开发时默认在终端打印每次 Server Action 调用的参数，登录、注册的参数里有明文密码，
不符合「密码不进日志」。next.config.ts 把 logging.serverFunctions 设为 false，请求行照常打印；
生产构建本来就不打印。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这两个文件；pre-commit 全部通过。

---

### Task 18: 文档同步

spec 第 17 节列出的文档，加上写计划时补的决定（「与 spec 的出入」第 1、2、3、4、23、24 条）。只改文档。下面四段改动的路径都相对仓库根目录，存成文件后用 `git -C /Users/linqibin/Projects/Products/patra apply <文件>` 应用。

**Files:**
- Modify: `docs/patra/specs/2026-10-10-portal-auth-design.md`（第 5、8.1、9.5、10、12、13、15 节）
- Modify: `patra-portal/docs/design-briefs/v0.8/accounts.md`（§4.1、§4.2、§6）
- Modify: `patra-portal/README.md`（目录结构）
- Modify: `docs/patra/specs/2026-10-12-portal-auth-pages-design.md`（第 2、3、5、9.1、9.3、11、13、14、17 节）

**Interfaces:** 无代码接口。

- [ ] **Step 1: PAP-67 设计**

`getCurrentUser()` 在 Task 2 删掉了，改为提 `lookupSessionByToken()`；「登录页、注册页在渲染时把已登录的人跳走」改为规则 6 在 proxy 里做（Task 3）；第 12 节补上开发时终端里的日志（Task 17）：

````diff
diff --git a/docs/patra/specs/2026-10-10-portal-auth-design.md b/docs/patra/specs/2026-10-10-portal-auth-design.md
--- a/docs/patra/specs/2026-10-10-portal-auth-design.md
+++ b/docs/patra/specs/2026-10-10-portal-auth-design.md
@@ -84,7 +84,7 @@
 |---|---|
 | `src/lib/gateway.ts` | 统一网关客户端 `gatewayFetch`，以及判断令牌能不能放进请求头的 `canSendToken`。全仓库只有这里读 `PATRA_GATEWAY_BASE_URL`、向网关发 `fetch`（`server-only`） |
 | `src/lib/auth/session-cookie.ts` | 会话 Cookie 的名称、属性、`Secure` 开关和读写。全仓库只有这里调用 `cookies()`（`server-only`） |
-| `src/lib/auth/current-user.ts` | `lookupSession()`、`getCurrentUser()`（`server-only`） |
+| `src/lib/auth/current-user.ts` | `lookupSession()`、`lookupSessionByToken()`（`server-only`） |
 | `src/lib/auth/actions.ts` | `register`、`login`、`logout`（`"use server"`） |
 | `src/lib/auth/types.ts` | 浏览器和服务端共用的类型：`CurrentUser`、`FieldError`、`RegisterResult`、`LoginResult` |
 | `src/app/api/auth/me/route.ts` | `GET /api/auth/me` |
@@ -171,10 +171,13 @@ export type SessionLookup =
   | { status: "unavailable" };

 export async function lookupSession(): Promise<SessionLookup>;
-export async function getCurrentUser(): Promise<CurrentUser | null>;
+export async function lookupSessionByToken(
+  token: string | null,
+  timeoutMs?: number,
+): Promise<SessionLookup>;
 ```

-`lookupSession()` 读 Cookie，再用 `gatewayFetch("/patra-identity/auth/me", { token })` 请求网关：
+`lookupSession()` 读 Cookie，交给 `lookupSessionByToken(token)`，后者用 `gatewayFetch("/patra-identity/auth/me", { token, timeoutMs })` 请求网关：

 | 情况 | 结果 |
 |---|---|
@@ -185,7 +188,7 @@ export async function getCurrentUser(): Promise<CurrentUser | null>;
 | 其他状态码；请求抛出的错误（超时、网络错误、缺网关地址）；200 但响应体格式不对；读 Cookie 时抛错（比如开关的值写错） | `unavailable`，记一条错误日志（第 12 节） |

 - `lookupSession()` 不抛异常。
-- `getCurrentUser()` 在 `authenticated` 时返回用户，其余情况返回 null，不碰 Cookie。它给服务端渲染的页面用，比如 PAP-68 的登录页、注册页判断「已登录就跳走」。渲染时不能写 Cookie，所以遇到 401 只返回 null，过期的 Cookie 留到下一次 `/api/auth/me` 时清，或者登录时被新值覆盖。
+- `lookupSessionByToken()` 不读 Cookie，令牌由调用方给出；`timeoutMs` 不传时用 `gatewayFetch` 默认的 10 秒。proxy 用它判断打开登录页、注册页的人是否已登录（规则 6，超时 3 秒，见 PAP-68 设计第 7 节）。proxy 不写 Cookie，所以遇到 401 只是放行，过期的 Cookie 留到下一次 `/api/auth/me` 时清，或者登录时被新值覆盖。

 ### 8.2 `GET /api/auth/me`

@@ -283,7 +286,7 @@ export async function logout(): Promise<void>;
 `register`、`login`、`logout` 都会写 Cookie，所以 Next 会在同一次响应里把当前页在服务端重新渲染一遍（第 3 节）。

 - 在正文页登出时，这一页的正文会再请求一次。正文不依赖 Cookie，所以渲染结果不变。万一这时 catalog 正好挂了，这次重新渲染会像平时切换页面一样报错，但 Cookie 已经清掉了。这个结果可以接受：catalog 挂了时，切换到任何页面都一样会报错。
-- 在登录页、注册页提交成功后，这一页也会带着新 Cookie 重新渲染一遍。PAP-68 如果在渲染时就把已登录的人跳走，这次重新渲染本身就会触发跳转（第 15 节）。
+- 在登录页、注册页提交成功后，这一页也会带着新 Cookie 重新渲染一遍。规则 6（已登录的人打开这两页时跳走）在 proxy 里做，只拦 GET，这次重新渲染照常完成，见 PAP-68 设计第 7 节。
 - Server Action 只返回结果，自己不跳转；跳转、`next` 参数的校验和成功提示都归 PAP-68。

 ### 9.6 已登录时再次登录
@@ -293,7 +296,7 @@ export async function logout(): Promise<void>;
 ## 10. 401、503 和并发

 - 除了登出，只有 401 会清 Cookie（`/api/auth/me` 的 `rejected`），不区分 `GW-0401` 和 `IDN-0401`。503、超时、网络错误一律不清。
-- 用户态请求失败时不进错误页：`/api/auth/me` 由浏览器查询，失败时按未登录显示；`lookupSession()` 和 `getCurrentUser()` 不抛异常；Server Action 把失败都归成 `unavailable`。
+- 用户态请求失败时不进错误页：`/api/auth/me` 由浏览器查询，失败时按未登录显示；`lookupSession()` 和 `lookupSessionByToken()` 不抛异常；Server Action 把失败都归成 `unavailable`。
 - 令牌在整个会话期间不变，没有轮换，所以同一个令牌并发多少请求，都只会刷新 Cookie。PAP-67 的验收「同时发起多个带令牌的请求，不会把用户登出」靠这个结构满足。
 - 有两种窗口极短的竞争，本设计接受：
   1. 在 A 标签页点登出的同时，B 标签页有一个更早发出的 `/api/auth/me` 晚一步回来，结果是 200，于是把已经作废的令牌重新写回 Cookie。影响只是 B 标签页的导航栏晚一点回到未登录：下一次查询会收到 401，并把这个 Cookie 清掉。
@@ -311,7 +314,7 @@ export async function logout(): Promise<void>;

 ## 12. 日志

-- 令牌和密码不进日志，也不进抛出的错误消息。
+- 令牌和密码不进日志，也不进抛出的错误消息。开发时终端里的日志也算：Next 开发时默认打印每次 Server Action 调用的参数，登录、注册的参数里有明文密码，所以 `next.config.ts` 设了 `logging.serverFunctions: false`（PAP-68 写计划时发现；生产构建本来就不打印）。
 - 网关返回意外的状态码、或者请求失败时，`console.error` 记下接口、状态码，以及 ProblemDetail 的 `code`（有的话），不记响应体。
 - 测试断言 `console.*` 的参数里不出现令牌和密码。

@@ -324,7 +327,7 @@ export async function logout(): Promise<void>;
 | `tests/lib/gateway-boundary.test.ts` | 守护测试，规则见下表 |
 | `tests/lib/gateway.test.ts` | 地址拼接；`cache: "no-store"`；超时默认 10 秒、可以覆盖（spy `AbortSignal.timeout`，看传入的值）；JSON 请求体和 `Content-Type`；有令牌时带 `Authorization: Bearer`，没有令牌时不带；缺地址时报错；网络错误照常抛出；`canSendToken` 的字符边界（制表符、空格、0x80–0xFF 能放，换行、回车、NUL、其他控制字符、DEL、0xFF 以上的字符不能放） |
 | `tests/lib/auth/session-cookie.test.ts` | 两种名字和全部属性；开关的三种取值；`refreshSessionCookie` 用原值重写、没有 Cookie 时什么也不做；`clearSessionCookie` 的属性和写入时相同，并且 `Max-Age=0` |
-| `tests/lib/auth/current-user.test.ts` | 第 8.1 节表格逐行覆盖，`GW-0401` 和 `IDN-0401` 都归为 `rejected`；请求带 `Authorization: Bearer`；`getCurrentUser()` 的映射；日志里没有令牌 |
+| `tests/lib/auth/current-user.test.ts` | 第 8.1 节表格逐行覆盖，`GW-0401` 和 `IDN-0401` 都归为 `rejected`；请求带 `Authorization: Bearer`；`lookupSessionByToken` 把 `timeoutMs` 传给 `gatewayFetch`；日志里没有令牌 |
 | `tests/app/api/auth-me.test.ts` | 第 8.2 节表格逐行覆盖，包括 `Cache-Control`；同一个令牌并发请求两次，两次都返回 200、都只刷新不清除；先 503 后 200 时不清除 |
 | `tests/lib/auth/actions.test.ts` | 第 9.3 节表格逐行覆盖；请求体只有 `email` 和 `password`；注册、登录不带 `Authorization`（即使已有 Cookie）；入参形状不对时返回 `unavailable`、不请求网关；只有成功时写 Cookie；登出在 204、503、超时、网络错误、没有 Cookie、Cookie 值放不进请求头这几种情况下都清 Cookie，后两种不请求网关；日志里没有密码和令牌 |
 | `tests/lib/auth/current-user-query.test.tsx` | `pending`、`anonymous`、`authenticated` 三种状态；503 时归为 `anonymous`；请求的是同源的 `/api/auth/me` |
@@ -373,8 +376,8 @@ Server Action 本身，以及浏览器里「页面脚本读不到令牌」这一
 | 注册、登录成功后，用 `queryClient.setQueryData(queryKeys.currentUser, user)` 写入用户；登出完成后写入 null | PAP-68 |
 | 按 `reason` 和字段的 `code` 选简报 §4 的文案；不认识或为 null 的原因码，显示该字段的通用错误；`retryAfterSeconds` 按分钟向上取整，不足 1 分钟按 1 分钟算，为 null 时显示「尝试次数过多，请稍后再试」 | PAP-68 |
 | `register`、`login` 的 promise 被拒绝（比如浏览器没连上门户）时，按 `unavailable` 处理。`logout` 的 promise 被拒绝时，Cookie 可能还在，不要直接把用户写成 null，而是让 `queryKeys.currentUser` 重新请求一次，以门户的回答为准；界面照旧没有登出失败态（简报 §4.3） | PAP-68 |
-| 写 Cookie 的 Server Action 会让当前页在同一次响应里重新渲染。登录页、注册页如果在渲染时把已登录的人跳走，这次重新渲染就会触发跳转，成功提示和跳转流程要按这一点设计 | PAP-68 |
-| 服务端判断是否已登录用 `getCurrentUser()`，它不清除过期的 Cookie | PAP-68 |
+| 写 Cookie 的 Server Action 会让当前页在同一次响应里重新渲染，成功提示和跳转流程要按这一点设计 | PAP-68 |
+| 规则 6（已登录的人打开登录页、注册页时跳走）在 proxy 里做，见 PAP-68 设计第 7 节；判断时不清除过期的 Cookie | PAP-68 |
 | 登录流程的 Playwright 冒烟：`playwright.config.ts` 的 `webServer.env` 已经关掉 `Secure` | PAP-68 |
 | 门户前面加反向代理或上 HTTPS 时：配 `serverActions.allowedOrigins`；删掉 `.env.portal` 里的 `PATRA_SESSION_COOKIE_SECURE=false`。切换后 Cookie 改名，旧的 `patra_session` 不再被读取，已登录的人要重新登录一次 | 引入反向代理或 HTTPS 的 Issue |

````

- [ ] **Step 2: 设计简报**

§4.1、§4.2 补上两条通用错误和字段名对不上时的提示条（Task 4、7、8），§6 的校验时机改成空着离开字段不提示（Task 10）：

````diff
diff --git a/patra-portal/docs/design-briefs/v0.8/accounts.md b/patra-portal/docs/design-briefs/v0.8/accounts.md
--- a/patra-portal/docs/design-briefs/v0.8/accounts.md
+++ b/patra-portal/docs/design-briefs/v0.8/accounts.md
@@ -159,16 +159,18 @@
 | 提交中 | 按钮 | `注册中…`（按钮禁用，字段只读） |
 | 邮箱为空 | 邮箱下方 | `请输入邮箱` |
 | 邮箱格式不对 | 邮箱下方 | `邮箱格式不正确` |
+| 邮箱的其他问题 | 邮箱下方 | `邮箱格式不正确` |
 | 邮箱已注册 | 邮箱下方 | `该邮箱已注册，去登录`（「去登录」是链接，带上 `next`） |
 | 密码为空 | 密码下方 | `请输入密码` |
 | 密码不足 8 位 | 密码下方 | `密码至少 8 位` |
 | 密码超过 64 位 | 密码下方 | `密码最长 64 位` |
 | 密码太常见 | 密码下方 | `这个密码太常见，容易被猜到，请换一个` |
+| 密码的其他问题 | 密码下方 | `密码不符合要求` |
 | 确认密码为空 | 确认密码下方 | `请再输入一次密码` |
 | 两次密码不一致 | 确认密码下方 | `两次输入的密码不一致` |
 | 网络或服务错误 | 提示条 | `注册没有成功，请稍后再试` |

-密码字段出错时，错误文案**替换**常驻提示「至少 8 位」，不同时显示两行。「邮箱已注册」和「密码太常见」由后端在提交后返回，其余在前端校验。
+密码字段出错时，错误文案**替换**常驻提示「至少 8 位」，不同时显示两行。「邮箱已注册」「密码太常见」和两条「其他问题」由后端在提交后返回，其余在前端校验。「其他问题」指后端返回、上表没有单列的原因，比如邮箱超长、密码含无效字符、不认识的原因码。后端返回的字段错误一条都对不上邮箱、密码时，按「网络或服务错误」显示提示条。

 ### 4.2 登录页

@@ -193,6 +195,8 @@

 「被暂时限制」里的分钟数是示例。实际值由后端返回的剩余秒数向上取整到分钟，不足 1 分钟显示「1 分钟」；后端没有给出剩余时间时显示 `尝试次数过多，请稍后再试`。不做倒计时，也不禁用提交按钮。

+后端返回的字段错误按 §4.1 的口径显示：邮箱的其他问题显示「邮箱格式不正确」，密码的其他问题显示「密码不符合要求」；一条都对不上邮箱、密码时，按「网络或服务错误」显示提示条。
+
 ### 4.3 导航栏和全局提示

 - [ ] **未登录态**：桌面「登录」；移动端菜单里的账号块
@@ -260,7 +264,7 @@

 - **进入页面**：焦点落在邮箱框。
 - **回车**：在任一输入框里回车即提交。
-- **校验时机**：离开一个字段时校验它；点提交时校验全部，并把焦点移到第一个出错的字段；出错后随输入重新校验，改对即消。
+- **校验时机**：离开一个填了内容的字段时校验它，空着离开不提示，「请输入…」留到提交时；点提交时校验全部，并把焦点移到第一个出错的字段；出错后随输入重新校验，改对即消。
 - **提交中**：按钮禁用并换成进行中文案，字段只读；互跳链接和「返回」仍可点。
 - **密码显示 / 隐藏**：默认隐藏；切换后光标位置不变。注册页的两个密码框各有一个切换，互不联动。
 - **用户菜单**：点圆形标识开合；点菜单外、按 Esc、选中菜单项都会关闭；方向键在菜单项之间移动；关闭后焦点回到圆形标识。
````

- [ ] **Step 3: README**

目录结构加上 `app/login/`、`app/register/`、`components/auth/`，`proxy.ts` 和 `lib/auth/` 的说明补上这次加的内容：

````diff
diff --git a/patra-portal/README.md b/patra-portal/README.md
--- a/patra-portal/README.md
+++ b/patra-portal/README.md
@@ -39,18 +39,21 @@ src/
 │   ├── papers/[id]/            # /papers/:id 文献详情
 │   ├── journals/(list)/        # /journals 期刊浏览
 │   ├── journals/[id]/          # /journals/:id 期刊详情
+│   ├── login/                  # /login 登录页
+│   ├── register/               # /register 注册页
 │   ├── missing/                # 详情不存在时由 proxy 改写到这里，返回真正的 404
 │   ├── api/                    # health、期刊联想、auth/me（当前用户）
 │   ├── error.tsx               # 路由错误边界
 │   ├── not-found.tsx           # 全局 404
 │   └── globals.css             # Tailwind v4 @theme 映射
-├── proxy.ts                    # 请求拦截层：直接打开详情页时先向后端确认记录存在
+├── proxy.ts                    # 请求拦截层：直接打开详情页时先向后端确认记录存在；已登录的人打开登录页、注册页时送回 next
 ├── components/
+│   ├── auth/                   # 登录页、注册页的外壳和表单
 │   ├── portal/                 # 业务组件（按页面 / 功能分目录）
 │   └── ui/                     # shadcn/ui 原语
 ├── lib/
 │   ├── gateway.ts              # 统一网关客户端：全仓库只有这里读网关地址、向网关发请求
-│   ├── auth/                   # 会话 Cookie、当前用户、注册 / 登录 / 登出的 Server Action
+│   ├── auth/                   # 会话 Cookie、当前用户、注册 / 登录 / 登出的 Server Action，next 参数、表单校验与文案
 │   ├── portal-api/             # 服务端向网关取数、解析检索参数
 │   └── …                       # 工具函数、TanStack Query 工厂与 queryKey
 ├── data/                       # 静态内容（示例检索、主题等）
````

- [ ] **Step 4: PAP-68 设计**

第 3 节加一张「写实施计划时补的决定」的表（「与 spec 的出入」第 1、2、24 条）；第 9.1、9.3、11、13、14 节按实现改写（第 3、4、23 条）；第 2 节的范围、第 5 节的文件表、第 14 节的测试表和第 17 节随之更新。评审时没有接受的决定，这一步跟着删掉对应的几处。

````diff
diff --git a/docs/patra/specs/2026-10-12-portal-auth-pages-design.md b/docs/patra/specs/2026-10-12-portal-auth-pages-design.md
--- a/docs/patra/specs/2026-10-12-portal-auth-pages-design.md
+++ b/docs/patra/specs/2026-10-12-portal-auth-pages-design.md
@@ -26,14 +26,14 @@ PAP-68 补上界面，走通简报 §0 的闭环：任一页面点导航栏「
 - 新增原语 `dropdown-menu`：shadcn 的 base-nova 风格，底层是 base-ui 的 Menu，即简报里说的 `menu`。
 - 成功提示的外观：`Toaster` 去掉 `richColors`，照高保真稿改。
 - PAP-67 代码的三处小改：`session-cookie.ts` 加一个从请求对象读令牌的函数；`current-user.ts` 拆出接收令牌和超时的查询函数；删掉没有调用方的 `getCurrentUser()`。
-- 单元测试、组件测试、登录流程的 Playwright 冒烟、两个尺寸的视觉验收。
+- 单元测试、组件测试、Playwright 端到端测试（登录流程的冒烟，以及两组只有真实浏览器测得出的行为，第 14 节）、两个尺寸的视觉验收。

 不做：

 - 后端接口。PAP-67 三个 Server Action、`/api/auth/me` 和 `useCurrentUser()` 的行为也都不改。
 - 简报 §7「本版边界」里的入口：邮箱验证、找回密码、修改密码、第三方登录、个人资料、验证码、「记住我」、服务条款勾选、收藏。
 - 在登录页和注册页之间切换时带上已填的邮箱。
-- 不开 JavaScript 时提交表单（第 4.1 节方案 2）。
+- 不开 JavaScript 时提交表单（第 4.1 节方案 2）。表单写了 `method="post"`，页面接管表单之前提交时，邮箱和密码不进地址栏，但这时仍然登录不了（第 13 节）。
 - 会话失效的专门提示。简报 §4.3 规定下次打开页面时静默回到未登录态，PAP-67 已经做到。
 - 暗色模式。

@@ -74,10 +74,19 @@ PAP-68 补上界面，走通简报 §0 的闭环：任一页面点导航栏「
 | 邮箱的通用错误 | 不认识的原因码、原因码为 null，也显示「邮箱格式不正确」 | 和 `TOO_LONG` 的处理一致。邮箱字段只剩「请输入邮箱」「邮箱格式不正确」两种说法 |
 | 登录页、注册页里的站内链接用 replace | 互跳、「去登录」、「返回」都替换当前这条历史记录 | 成功后的历史记录里不留登录页、注册页。否则按后退会回到登录页：要么被 proxy 弹走，要么看到客户端缓存里的表单（第 12 节第 4 条） |
 | 写入当前用户前先取消进行中的请求 | 登录、注册成功和登出成功时，先 `cancelQueries` 再 `setQueryData` | 成功前发出、成功后才回来的 `/api/auth/me` 会覆盖刚写入的值，导航栏又变回原样 |
-| 提交后的重新校验 | `reValidateMode` 也设成 `onBlur`；边输入边校验只用于已经报错的字段 | RHF 默认在提交后对所有字段边输入边校验，输入到一半就会报错，和简报 §6 不一致 |
+| 提交后的重新校验 | `mode`、`reValidateMode` 都设成 `onSubmit`，离开字段时的校验另外做（第 9.3 节）；边输入边校验只用于已经报错的字段 | RHF 默认在提交后对所有字段边输入边校验，输入到一半就会报错，和简报 §6 不一致 |
 | 提交时连后端字段错误一起清 | 和提示条一起清掉，再校验、请求 | 再次提交得到的是新结果，旧的错误不该留着 |
 | `next` 出现多次 | 页面和 proxy 都取第一个 | 两处的结果一致 |

+写实施计划时补的决定（计划评审时确认）：
+
+| 决定 | 内容 | 理由 |
+|---|---|---|
+| 空着离开字段不校验 | 「请输入…」只在提交时出现（第 9.3 节） | 进入页面时焦点就在邮箱框，点一下页面别处就会冒出「请输入邮箱」 |
+| 离开字段时的校验晚一步 | 按着指针离开时，等松开再校验；没按着时，排到下一个宏任务（第 9.3 节） | 错误行插进来会把下方的链接、按钮往下挤，用户正在点的那一下会落空 |
+| 表单写 `method="post"` | 两张表单都写（第 13 节） | 页面接管表单之前提交时，浏览器默认用 GET，邮箱和密码会进地址栏 |
+| 开发时不打印 Server Action 的参数 | `next.config.ts` 设 `logging.serverFunctions: false`（第 13 节） | Next 开发时默认在终端打印每次调用的参数，登录、注册的参数里有明文密码 |
+
 版本：Next.js 16.3.8、React 19.3、TanStack Query 5.103.2、React Hook Form 7.88、Zod 4.6.5、base-ui 1.8、sonner 2.0.8。下面用到的框架行为，都对照源码、随包文档或实测核实过：

 - 写 Cookie 的 Server Action 会让当前页在同一次响应里重新渲染（PAP-67 设计第 3 节）。这次渲染里 `cookies()` 读到的是刚写入的值；页面里的 `redirect()` 默认是 replace（实测，action 响应里是 `NEXT_REDIRECT;replace;…`）。
@@ -135,6 +144,7 @@ PAP-68 补上界面，走通简报 §0 的闭环：任一页面点导航栏「
 | `src/components/portal/TopNav.tsx`（改） | 放进账号区和账号块，各包一层 Suspense |
 | `src/lib/portal-ui.ts`（改） | 按钮样式支持 `aria-disabled` |
 | `src/components/ui/sonner.tsx`、`src/app/layout.tsx`（改） | 成功提示的外观；去掉 `richColors` |
+| `next.config.ts`（改） | `logging.serverFunctions: false`：开发时终端不打印 Server Action 的参数 |

 依赖方向：表单依赖 actions、next-path、form-schemas、messages 和 query-keys；导航栏组件依赖 current-user-query、logout-mutation 和 next-path；proxy 依赖 next-path、session-cookie 和 current-user。next-path、messages、form-schemas 不带 `server-only`，客户端组件也能引用。

@@ -207,9 +217,10 @@ matcher 改为 `["/papers/:id", "/journals/:id", "/login", "/register"]`。`prox

 视觉照 `Spec-Form` 和 `patra.css`：

-- 聚焦：`focus-within:border-border-focus focus-within:ring-4 ring-clay-500/15`。
-- 错误：`has-[[aria-invalid=true]]:border-status-danger`；错误态下聚焦，光晕换成 `ring-rust-500/15`。
-- 提交中只读：底色换成 `--bg-sunken`，去掉内阴影。选择器要写 `has-[input:read-only]`，不能照抄注释里的 `has-[:read-only]`：按钮、图标这类不可编辑的元素也匹配 `:read-only`，输入框会一直显示成只读。
+- 聚焦：`has-[input:focus]:border-border-focus has-[input:focus]:ring-4 ring-clay-500/15`。只看输入框本身：密码框的显示 / 隐藏切换聚焦时，外框不亮（`Spec-Form` 02）。
+- 错误：`has-[input[aria-invalid=true]]:border-status-danger`；错误态下聚焦，光晕换成 `ring-rust-500/15`。
+- 提交中只读：`has-[input[readonly]]` 时底色换成 `--bg-sunken`，去掉内阴影和光晕。不能照抄注释里的 `has-[:read-only]`：按钮、图标这类不可编辑的元素也匹配 `:read-only`，输入框会一直显示成只读。
+- 三种状态都写成 `has-[…]`，不用 `focus-within:`：Tailwind 4.3 把 `focus-within:` 生成在 `hover:` 之前，悬停加聚焦时描边会是悬停色；`has-[…]` 生成在 `hover:` 之后，彼此按方括号里的字母序排，正好是只读压过出错、出错压过聚焦。

 ### 9.2 校验

@@ -224,7 +235,10 @@ matcher 改为 `["/papers/:id", "/journals/:id", "/login", "/register"]`。`prox

 ### 9.3 校验时机（简报 §6）

-- RHF 配 `mode: "onBlur"`、`reValidateMode: "onBlur"`：离开一个字段时校验它。
+- RHF 配 `mode: "onSubmit"`、`reValidateMode: "onSubmit"`，离开字段时的校验交给 `src/components/auth/use-blur-validation.ts`：
+  - 离开一个填了内容的字段时校验它。空着离开不校验，「请输入…」留到提交时：进入页面时焦点就在邮箱框，点一下页面别处不该冒出错误。
+  - 按着指针离开时，等松开再校验；没按着时（键盘离开、手机轻触），排到下一个宏任务再校验。离开字段时冒出的错误行会把下方的链接、按钮往下挤，立即校验的话，用户正在点的那一下就落空了：鼠标的按下和松开落在不同元素上；Chrome 里的手机轻触先派发 `pointerup`，再移交焦点、派发 `click`。
+  - 登录页的密码框不接：它只查非空，空着离开又不校验。
 - 点提交或在输入框里回车时，校验全部字段，焦点移到第一个出错的字段（RHF 的 `shouldFocusError`）。
 - 字段显示错误之后，每次输入都重新校验它，改对了错误立即消失。RHF 没有「只在报错后边输入边校验」的模式，所以在字段的 `onChange` 里补一段：这个字段当前有错误时，调用 `trigger(字段)`。
 - 注册页：确认密码不为空时，密码每次改动都重新比对确认密码。
@@ -338,7 +352,8 @@ matcher 改为 `["/papers/:id", "/journals/:id", "/login", "/register"]`。`prox
 ## 11. 成功提示

 - 沿用根布局右下角的 `Toaster`，去掉 `richColors`：它会把 `toast.success` 渲染成绿底，和高保真稿的「浅底 + moss 色图标」不一致。现有的普通 `toast()` 不受影响。
-- 成功图标用 moss 色（`text-status-success`），阴影用 `--shadow-lg`，底色、文字、边框、圆角沿用 `sonner.tsx` 现有的 token。
+- 成功图标用 moss 色（`text-status-success`），阴影用 `shadow-lg!`：sonner 在提示上写死了阴影，带 important 才压得过。
+- 底色、文字、边框、圆角用 `sonner.tsx` 原有的 token，但改写在 `Toaster` 的行内样式上：sonner 往 `<head>` 插入的样式不分层，原来写在类名里的变量压不过它，其实没有生效。
 - 移动端照高保真稿 `Nav-M-Toast`：距左右和底边各 16px。
 - 三条文案：「已登录」「注册成功，已登录」「已退出登录」。

@@ -353,7 +368,8 @@ matcher 改为 `["/papers/:id", "/journals/:id", "/login", "/register"]`。`prox
 ## 13. 安全

 - 开放重定向：`next` 只接受站内地址（第 6 节）。proxy 的 307 和表单的 `router.replace` 都只用 `safeNextPath` 的结果。
-- 密码只在表单的内存里：不进 URL、不进日志、不进查询缓存。邮箱也不放进 URL。
+- 密码只在表单的内存里：不进 URL、不进日志、不进查询缓存。邮箱也不放进 URL。开发时终端里的日志也算：Next 开发时默认打印每次 Server Action 调用的参数，`next.config.ts` 设 `logging.serverFunctions: false` 关掉。
+- 两张表单写 `method="post"`。脚本还没加载完、页面还没接管表单时就提交（网慢、设备慢、密码管理器自动提交），浏览器按表单自己的属性提交；默认的 GET 会把邮箱和密码放进地址栏、历史记录和服务器日志。改成 POST 后，Next 对不带 action ID 的 POST 照常渲染这一页，表单清空，可以再提交一次。
 - 令牌仍只在 HttpOnly Cookie 里，页面脚本读不到；导航栏只拿到 `userId` 和 `email`。
 - CSRF 防护沿用 PAP-67 设计第 11 节，本设计不新增会改数据的 GET。

@@ -374,14 +390,17 @@ matcher 改为 `["/papers/:id", "/journals/:id", "/login", "/register"]`。`prox
 | `tests/components/portal/NavAccount.test.tsx`、`MobileAccount.test.tsx` | jsdom | 三种形态，占位阶段不出现「登录」；链接里的 `next`；三个样例邮箱的首字符 C、P、2；退出进行中禁用，成功后桌面焦点落到「登录」、移动端关闭菜单，失败时重新请求当前用户 |
 | `tests/lib/auth/logout-mutation.test.tsx` | jsdom | 成功时写 null 并提示；进行中的 `/api/auth/me` 晚回来，不会把 null 覆盖掉；失败时重新请求、不提示 |
 | `tests/components/portal/TopNav.test.tsx`、`tests/app/loading-busy.test.tsx`（改） | jsdom | 补上 QueryClientProvider 和 `/api/auth/me` 的桩，原有断言不变 |
+| `tests/next-config.test.ts` | node | `logging.serverFunctions` 是 `false`（第 13 节） |

 端到端冒烟（Playwright，`tests/e2e/auth.spec.ts`）：

-- 没有设置 `PATRA_GATEWAY_BASE_URL` 时跳过。CI 里没有网关，会跳过；本地内联指向 mini 的网关跑。每跑一次，mini 上多一个一次性账号，dev 数据可以丢弃。
+- 账号流程在没有设置 `PATRA_GATEWAY_BASE_URL` 时跳过。CI 里没有网关，会跳过；本地内联指向 mini 的网关跑，用 `--workers=1`：`next dev` 下几个 worker 同时冷编译几个路由，Fast Refresh 重建会让页内跳转退化成整页加载，测试会点到页面还没接管的表单。每跑一次，mini 上多一个一次性账号，dev 数据可以丢弃。
 - 主流程按简报 §2 的文字流：在 `/papers?q=GLP-1` 点「登录」，再点去注册 → 注册后回到 `/papers?q=GLP-1`，看到「注册成功，已登录」和圆形标识 → 退出登录 → 再登录一次 → 再退出一次。邮箱每次随机生成，密码在运行时生成，不写进仓库。
 - 注册成功后按后退，不会回到登录页或注册页。
 - 登录后 `document.cookie` 里没有会话 Cookie（页面脚本读不到令牌，PAP-67 设计第 14 节留到这里验收的一条）。
 - 规则 6：已登录时直接打开 `/login?next=%2Fjournals`，最后停在 `/journals`。
+- 离开字段时冒出的错误不让点击落空（第 9.3 节）：登录页焦点在空的邮箱框里，直接点「注册」；注册页邮箱填了一半，直接点「登录」。鼠标用按住 100 毫秒的点击（Playwright 默认的 `click()` 按下就松开，测不出来），另用手机尺寸的轻触点一次。不连网关。
+- 页面接管表单之前提交（第 13 节）：禁用脚本后在两页各提交一次，请求是 POST，地址栏里没有邮箱和密码。不连网关。

 视觉验收：在 1440×900 和 375×812 两个尺寸下截图，和高保真稿的对应画板逐项比对。范围是登录页、注册页的默认态、几个错误态和提交中，导航栏的未登录态和菜单展开，以及移动端账号块。差异整理成一张对照页，交给用户验收。

@@ -413,8 +432,8 @@ matcher 改为 `["/papers/:id", "/journals/:id", "/login", "/register"]`。`prox

 ## 17. 要同步改的文档和 Issue

-- PAP-67 设计（`2026-10-10-portal-auth-design.md`）：第 5、8.1、10、13 节里的 `getCurrentUser()` 删掉；第 9.5 节和第 15 节里「登录页、注册页在渲染时把已登录的人跳走」「服务端判断是否已登录用 `getCurrentUser()`」两处，改为「规则 6 在 proxy 里做，见 PAP-68 设计第 7 节」。
-- 设计简报 §4.1、§4.2：补上两条通用错误（邮箱的其他问题显示「邮箱格式不正确」，密码的其他问题显示「密码不符合要求」），以及字段名对不上时用提示条。
+- PAP-67 设计（`2026-10-10-portal-auth-design.md`）：第 5、8.1、10、13 节里的 `getCurrentUser()` 删掉；第 9.5 节和第 15 节里「登录页、注册页在渲染时把已登录的人跳走」「服务端判断是否已登录用 `getCurrentUser()`」两处，改为「规则 6 在 proxy 里做，见 PAP-68 设计第 7 节」；第 12 节补上开发时终端里的日志（本设计第 13 节）。
+- 设计简报 §4.1、§4.2：补上两条通用错误（邮箱的其他问题显示「邮箱格式不正确」，密码的其他问题显示「密码不符合要求」），以及字段名对不上时用提示条；§6 的校验时机：空着离开字段不提示（第 9.3 节）。
 - `patra-portal/README.md`：目录结构加上 `app/login/`、`app/register/`、`components/auth/`；`proxy.ts` 的说明加上「已登录的人打开登录页、注册页时跳走」。
 - release spec 顶部的状态行：开 PR 时更新前端的进度。
 - Linear PAP-68：To-Do 加上 Tech Design，指向本设计；其余 To-Do 和 AC 随实施和验收勾掉。
````

- [ ] **Step 5: 核对**

```bash
git -C /Users/linqibin/Projects/Products/patra grep -n 'getCurrentUser' -- docs/patra/specs patra-portal/src patra-portal/tests patra-portal/docs patra-portal/README.md
git -C /Users/linqibin/Projects/Products/patra grep -c 'serverFunctions' -- docs/patra/specs
git -C /Users/linqibin/Projects/Products/patra diff --stat
```

Expected: 第一条只命中 PAP-68 设计的 6 行（第 28、73、135、189、385、435 行），都是在说删掉它；第二条输出 PAP-67 设计 `1`、PAP-68 设计 `4`；第三条是 `4 files changed, 55 insertions(+), 26 deletions(-)`。

- [ ] **Step 6: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add docs/patra/specs/2026-10-10-portal-auth-design.md patra-portal/docs/design-briefs/v0.8/accounts.md patra-portal/README.md docs/patra/specs/2026-10-12-portal-auth-pages-design.md
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
docs(portal): 同步认证相关文档里登录页、注册页的说法 (PAP-68)

PAP-67 设计删掉 getCurrentUser，规则 6 改为在 proxy 里做，日志一节补上开发时的终端。设计简报
补上两条通用错误、字段名对不上时的提示条和新的校验时机。README 的目录结构加上登录页、注册页和
components/auth/。PAP-68 设计写回实施计划里补的决定和实现上的改动。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有这 4 个文件；pre-commit 全部通过。

---

### Task 19: 本地验收，结果写回 spec，同步 Linear

**Files:**
- Modify: `docs/patra/specs/2026-10-12-portal-auth-pages-design.md`（头部的「状态」，第 15 节）
- Modify（本机，临时）：`.claude/launch.json` 加一个 `portal-prod`，用完删掉
- Linear：PAP-68、PAP-67 的描述

**Interfaces:** 无代码接口。本机门户指向 mini 的网关 `http://100.103.73.27:9528`。两个脚本注册的都是一次性账号（邮箱随机，密码运行时生成，不打印、不落盘），dev 数据可以丢弃。

- [ ] **Step 1: 准备高保真稿和两个脚本**

在本会话的 scratchpad 目录里：

1. 新建空目录 `<scratchpad>/hifi68`，把高保真稿快照解压进去：`unzip -q /Users/linqibin/Projects/Products/patra/docs/patra/design/snapshots/2026-10-04-accounts-hifi.zip -d <scratchpad>/hifi68`。解压出来的是数据，脚本不放在这个目录里，也不在这个目录里运行任何东西。
2. 把下面两个脚本存成 `<scratchpad>/pap68-visual.mjs`、`<scratchpad>/pap68-requests.mjs`，不进仓库。

`pap68-visual.mjs`：高保真稿的画板和本机门户的同一状态逐一截图，拼成一张对照页；另做 spec 第 15 节第 4、5 条的检查。画板引用的资源按快照里的路径从本机回环地址提供；每次打开门户的页面，先等页面接管表单或导航栏再操作（和 Task 10 的 `waitForTakeover` 同理，不然第 4 条会被浏览器原生的 `autofocus` 蒙混过去）；截图前把鼠标移开，等字体加载完、过渡结束（`reducedMotion: "reduce"` 只停掉位移，颜色和透明度的过渡照常有）。

```js
// PAP-68 视觉验收：高保真稿的画板和本机门户的同一状态逐一截图，拼成一张对照页。
// 用法：node pap68-visual.mjs <高保真稿快照解压后的目录> <输出的 html 文件>
// 前提：本机门户在 http://localhost:4000 跑着，连着 mini 的网关；本机 7890 端口有 HTTP 代理，画板的
// 字体从 Google Fonts 加载。会在 mini 上注册几个一次性账号：邮箱随机，密码运行时生成，不打印、不落盘。
import { randomBytes } from "node:crypto";
import { readFile, writeFile } from "node:fs/promises";
import { createServer } from "node:http";
import { createRequire } from "node:module";
import { extname, join, normalize } from "node:path";

const require = createRequire("/Users/linqibin/Projects/Products/patra/patra-portal/package.json");
const { chromium } = require("@playwright/test");

const [hifiDir, outFile] = process.argv.slice(2);
const APP = "http://localhost:4000";
const SIZES = { desktop: { width: 1440, height: 900 }, mobile: { width: 375, height: 812 } };
const ROUND_TRIP = { timeout: 20_000 };
const LOGIN = "/login?next=%2Fpapers";
const REGISTER = "/register?next=%2Fpapers";
// 画板里的图片是画布上的素材，换成快照里的本地文件（见快照里 gen.py 的 LOCAL_ASSETS）
const BLOBS = {
  "/_blob/62141f001d9677d8d530452e62eaeceb": "assets/patra-mark.svg",
  "/_blob/f628dc564499e51c54c0b9bd76992555": "assets/venation-paper.svg",
  "/_blob/56e94536e0c132977b72b8a092671077": "assets/venation-inverse.svg",
};
const TYPES = { ".html": "text/html; charset=utf-8", ".css": "text/css", ".svg": "image/svg+xml" };
// 画板上的示例值，只用在请求被拦下或过不了前端校验的地方，不会发到网关
const SAMPLE_EMAIL = "chen.yu@example.com";
const SAMPLE_LONG_EMAIL =
  "postdoctoral.researcher.zhang@cardiovascular-research.university-hospital.example.org";
const TOO_LONG_PASSWORD = "the-quick-brown-fox-jumps-over-the-lazy-dog-and-keeps-running-2026";
// 后端的常见密码表里有它
const COMMON_PASSWORD = "password123";
// 无法在本机复现的画板：封禁和限流要后端的状态，提示条和「邮箱或密码错误」是同一个组件
const SKIPPED = ["Login-Banned", "Login-RateLimited", "Login-M-Banned", "Login-M-RateLimited"];

const hex = () => randomBytes(6).toString("hex");
const secret = () => randomBytes(12).toString("base64url");
const accounts = {
  main: { email: `chen.${hex()}@example.org`, password: secret() },
  long: {
    email: `postdoctoral.researcher.${hex()}@cardiovascular-research.university-hospital.example.org`,
    password: secret(),
  },
  digit: { email: `2${hex()}@example.org`, password: secret() },
  mobile: { email: `m${hex()}@example.org`, password: secret() },
};

/// 起一个只在本机回环地址上的静态服务器，给画板用
function serveHifi() {
  const server = createServer(async (req, res) => {
    const path = decodeURIComponent(new URL(req.url, "http://x").pathname);
    if (path.endsWith("/support.js")) {
      res.writeHead(200, { "content-type": "text/javascript" });
      res.end("");
      return;
    }
    const file = BLOBS[path] ?? normalize(path).replace(/^\/+/, "");
    try {
      if (file.startsWith("..")) throw new Error("越界");
      const body = await readFile(join(hifiDir, file));
      res.writeHead(200, { "content-type": TYPES[extname(file)] ?? "application/octet-stream" });
      res.end(body);
    } catch {
      res.writeHead(404);
      res.end();
    }
  });
  return new Promise((resolve) => server.listen(0, "127.0.0.1", () => resolve(server)));
}

/// 本机门户的浏览器上下文：关掉动效，藏起 next dev 左下角的开发工具标记
async function appContext(browser, size) {
  const context = await browser.newContext({
    baseURL: APP,
    viewport: SIZES[size],
    isMobile: size === "mobile",
    hasTouch: size === "mobile",
    reducedMotion: "reduce",
  });
  await context.addInitScript(() => {
    addEventListener("DOMContentLoaded", () => {
      const style = document.createElement("style");
      style.textContent = "nextjs-portal{display:none!important}";
      document.head.append(style);
    });
  });
  return context;
}

/// 拦下页面发出的 Server Action 请求。`abort` 让它失败；`hold` 让它一直挂着，返回放行的函数
async function interceptAction(page, mode) {
  let release = () => {};
  await page.route("**/*", (route) => {
    const request = route.request();
    if (request.method() !== "POST" || !request.headers()["next-action"]) return route.continue();
    if (mode === "abort") return route.abort();
    release = () => route.continue();
  });
  return () => release();
}

/// 打开页面，等页面接管 `selector` 指向的元素再往下做：React 接管一个元素时，在它上面挂一个以
/// `__reactProps$` 开头的属性。接管之前操作走的是浏览器原生的行为（点链接是整页跳转，点提交是浏览器
/// 自己提交表单），看到的就不是页面代码的行为
async function open(page, path, selector) {
  await page.goto(path);
  await page.waitForFunction((target) => {
    const element = document.querySelector(target);
    return element !== null && Object.keys(element).some((key) => key.startsWith("__reactProps$"));
  }, selector);
}

async function fillLogin(page, email, password) {
  await page.getByLabel("邮箱").fill(email);
  await page.getByLabel("密码", { exact: true }).fill(password);
}

async function fillRegister(page, email, password, confirm = password) {
  await page.getByLabel("邮箱").fill(email);
  await page.getByLabel("密码", { exact: true }).fill(password);
  await page.getByLabel("确认密码").fill(confirm);
}

async function register(page, account) {
  await open(page, REGISTER, "form");
  await fillRegister(page, account.email, account.password);
  await page.getByRole("button", { name: "注册" }).click();
  await page.waitForURL(`${APP}/papers`, ROUND_TRIP);
}

async function logIn(page, account) {
  await open(page, LOGIN, "form");
  await fillLogin(page, account.email, account.password);
  await page.getByRole("button", { name: "登录" }).click();
  await page.waitForURL(`${APP}/papers`, ROUND_TRIP);
}

/// 等右下角的提示出现；`gone` 为真时再等它消失
async function toast(page, text, gone = false) {
  const item = page.getByText(text, { exact: true });
  await item.waitFor(ROUND_TRIP);
  if (gone) await item.waitFor({ state: "detached", ...ROUND_TRIP });
}

async function openMobileMenu(page) {
  await page.getByRole("button", { name: "打开菜单" }).click();
}

const checks = [];

/// 每个流程在新的上下文里跑，`shoot(画板)` 截下当前页面，和同名画板配对
const flows = [
  ["mobile", async (page) => {
    // 先注册后面要用的账号，不截图
    await register(page, accounts.main);
    await page.context().clearCookies();
    await register(page, accounts.long);
  }],
  ...["desktop", "mobile"].flatMap((size) => {
    const m = size === "mobile" ? "-M" : "";
    return [
      [size, async (page, shoot) => {
        await open(page, LOGIN, "form");
        await page.waitForFunction(() => document.activeElement?.getAttribute("type") === "email");
        await shoot(size === "mobile" ? "Login-M-Default" : "Main");
        await page.getByRole("button", { name: "登录" }).click();
        await page.getByText("请输入密码").waitFor();
        await shoot(`Login${m}-Empty`);
        await open(page, LOGIN, "form");
        await page.getByLabel("邮箱").fill("chen.yu@example");
        await page.getByLabel("密码", { exact: true }).click();
        await page.getByText("邮箱格式不正确").waitFor();
        await shoot(`Login${m}-EmailFormat`);
      }],
      [size, async (page, shoot) => {
        await open(page, LOGIN, "form");
        await fillLogin(page, `visual-${hex()}@example.org`, secret());
        await page.getByRole("button", { name: "登录" }).click();
        await page.getByText("邮箱或密码错误").waitFor(ROUND_TRIP);
        await shoot(`Login${m}-BadCredentials`);
        await open(page, LOGIN, "form");
        await interceptAction(page, "abort");
        await fillLogin(page, SAMPLE_EMAIL, secret());
        await page.getByRole("button", { name: "登录" }).click();
        await page.getByText("登录没有成功，请稍后再试").waitFor();
        await shoot(`Login${m}-ServiceError`);
        await page.unroute("**/*");
        await open(page, LOGIN, "form");
        await interceptAction(page, "hold");
        await fillLogin(page, SAMPLE_EMAIL, secret());
        await page.getByRole("button", { name: "登录" }).click();
        await page.getByRole("button", { name: "登录中…" }).waitFor();
        await shoot(`Login${m}-Submitting`);
      }],
      [size, async (page, shoot) => {
        await open(page, REGISTER, "form");
        await page.waitForFunction(() => document.activeElement?.getAttribute("type") === "email");
        await shoot(`Register${m}-Default`);
        await page.getByRole("button", { name: "注册" }).click();
        await page.getByText("请再输入一次密码").waitFor();
        await shoot(`Register${m}-Empty`);
        await open(page, REGISTER, "form");
        await fillRegister(page, "chen.yu@example", "abc123", "abc124");
        await page.getByRole("button", { name: "注册" }).click();
        await page.getByText("两次输入的密码不一致").waitFor();
        await shoot(`Register${m}-Invalid`);
        await open(page, REGISTER, "form");
        await fillRegister(page, SAMPLE_EMAIL, TOO_LONG_PASSWORD);
        await page.getByRole("button", { name: "显示密码" }).first().click();
        await page.getByRole("button", { name: "注册" }).click();
        await page.getByText("密码最长 64 位").waitFor();
        await shoot(`Register${m}-TooLong`);
      }],
      [size, async (page, shoot) => {
        await open(page, REGISTER, "form");
        await fillRegister(page, accounts.main.email, secret());
        await page.getByRole("button", { name: "注册" }).click();
        await page.getByText("该邮箱已注册").waitFor(ROUND_TRIP);
        await shoot(`Register${m}-EmailTaken`);
        await open(page, REGISTER, "form");
        await fillRegister(page, `visual-${hex()}@example.org`, COMMON_PASSWORD);
        await page.getByRole("button", { name: "注册" }).click();
        await page.getByText("这个密码太常见，容易被猜到，请换一个").waitFor(ROUND_TRIP);
        await shoot(`Register${m}-CommonPassword`);
        await open(page, REGISTER, "form");
        await interceptAction(page, "abort");
        await fillRegister(page, SAMPLE_EMAIL, secret());
        await page.getByRole("button", { name: "注册" }).click();
        await page.getByText("注册没有成功，请稍后再试").waitFor();
        await shoot(`Register${m}-ServiceError`);
        await page.unroute("**/*");
        await open(page, REGISTER, "form");
        await interceptAction(page, "hold");
        await fillRegister(page, SAMPLE_LONG_EMAIL, secret());
        await page.getByRole("button", { name: "注册" }).click();
        await page.getByRole("button", { name: "注册中…" }).waitFor();
        await shoot(`Register${m}-Submitting`);
      }],
      [size, async (page, shoot) => {
        // /api/auth/me 一直挂着，登录状态停在未定
        await page.route("**/api/auth/me", () => {});
        await open(page, "/papers", "header");
        if (size === "mobile") await openMobileMenu(page);
        await page.getByRole("status", { name: "正在确认登录状态" }).waitFor();
        await shoot(size === "mobile" ? "Nav-M-MenuPending" : "Nav-Pending");
      }],
    ];
  }),
  ["desktop", async (page, shoot) => {
    await open(page, "/papers", "header");
    const login = page.getByRole("banner").getByRole("link", { name: "登录" });
    await login.waitFor();
    await shoot("Nav-LoggedOut");
    // spec 第 15 节第 4 条：站内点「登录」进入登录页后，焦点在邮箱框
    await login.click();
    await page.waitForURL(`${APP}${LOGIN}`);
    await page.waitForTimeout(1_000);
    const focused = await page.evaluate(() => document.activeElement?.getAttribute("type"));
    checks.push(`站内点「登录」进入登录页后，焦点在：${focused === "email" ? "邮箱框" : `其他元素（${focused}）`}`);
  }],
  ["desktop", async (page, shoot) => {
    await register(page, accounts.digit);
    await toast(page, "注册成功，已登录");
    await shoot("Nav-Registered");
  }],
  ["desktop", async (page, shoot) => {
    await logIn(page, accounts.main);
    await toast(page, "已登录");
    await shoot("Nav-LoggedIn");
    await toast(page, "已登录", true);
    await page.getByRole("banner").getByRole("button", { name: "账号菜单" }).click();
    await page.getByRole("menuitem", { name: "退出登录" }).waitFor();
    await shoot("Nav-MenuOpen");
    const release = await interceptAction(page, "hold");
    await page.getByRole("menuitem", { name: "退出登录" }).click();
    await page.locator('[role="menuitem"][aria-disabled="true"]').waitFor();
    await shoot("Nav-LoggingOut");
    release();
    await toast(page, "已退出登录");
    await shoot("Nav-SignedOut");
  }],
  ["desktop", async (page, shoot) => {
    await logIn(page, accounts.long);
    await toast(page, "已登录", true);
    await page.getByRole("banner").getByRole("button", { name: "账号菜单" }).click();
    await page.getByRole("menuitem", { name: "退出登录" }).waitFor();
    await shoot("Nav-MenuLongEmail");
  }],
  ["desktop", async (page) => {
    // spec 第 15 节第 5 条：已登录的人用浏览器后退回到登录页时，请求经不经过 proxy
    await open(page, "/papers", "header");
    await page.getByRole("banner").getByRole("link", { name: "登录" }).click();
    await page.waitForURL(`${APP}${LOGIN}`);
    await page.getByRole("link", { name: "Patra 首页" }).click();
    await page.waitForURL(`${APP}/`);
    const other = await page.context().newPage();
    await logIn(other, accounts.main);
    await other.close();
    const requests = [];
    page.on("request", (request) => {
      if (new URL(request.url()).pathname === "/login") requests.push(request.method());
    });
    await page.goBack();
    await page.waitForTimeout(3_000);
    const form = await page.getByLabel("邮箱").count();
    checks.push(
      `已登录后用浏览器后退回到登录页：停在 ${new URL(page.url()).pathname}，` +
        `${form ? "显示表单" : "没有表单"}，请求 /login ${requests.length} 次`,
    );
  }],
  ["mobile", async (page, shoot) => {
    await open(page, "/papers", "header");
    await page.getByRole("button", { name: "打开菜单" }).waitFor();
    await shoot("Nav-M-TopBar");
    await openMobileMenu(page);
    await page.getByRole("link", { name: "登录" }).waitFor();
    await shoot("Nav-M-MenuLoggedOut");
  }],
  ["mobile", async (page, shoot) => {
    await register(page, accounts.mobile);
    await toast(page, "注册成功，已登录");
    await shoot("Nav-M-Registered");
  }],
  ["mobile", async (page, shoot) => {
    await logIn(page, accounts.main);
    await toast(page, "已登录");
    await shoot("Nav-M-Toast");
    await toast(page, "已登录", true);
    await openMobileMenu(page);
    await page.getByText("当前账号").waitFor();
    await shoot("Nav-M-MenuLoggedIn");
    const release = await interceptAction(page, "hold");
    await page.getByRole("button", { name: "退出登录" }).click();
    await page.locator('button[aria-disabled="true"]', { hasText: "退出登录" }).waitFor();
    await shoot("Nav-M-MenuLoggingOut");
    release();
    await toast(page, "已退出登录");
    await shoot("Nav-M-SignedOut");
  }],
  ["mobile", async (page, shoot) => {
    await logIn(page, accounts.long);
    await toast(page, "已登录", true);
    await openMobileMenu(page);
    await page.getByText("当前账号").waitFor();
    await shoot("Nav-M-MenuLongEmail");
  }],
];

const browser = await chromium.launch({
  proxy: { server: "http://127.0.0.1:7890", bypass: "localhost,127.0.0.1" },
});
const server = await serveHifi();
const hifiBase = `http://127.0.0.1:${server.address().port}/project`;
const shots = new Map();
let failed = 0;

for (const [size, flow] of flows) {
  const context = await appContext(browser, size);
  const page = await context.newPage();
  try {
    await flow(page, async (board) => {
      // 鼠标移开，免得截到悬停态；再等字体加载完、过渡结束：关掉动效只停掉位移，颜色和透明度的
      // 过渡照常有
      await page.mouse.move(0, 0);
      await page.evaluate(async () => {
        await document.fonts.ready;
        const running = document
          .getAnimations()
          .filter((animation) => animation.effect?.getComputedTiming().iterations !== Infinity);
        await Promise.all(running.map((animation) => animation.finished.catch(() => {})));
      });
      shots.set(board, { size, app: await page.screenshot({ type: "jpeg", quality: 70 }) });
      console.log(`截图：${board}`);
    });
  } catch (error) {
    failed += 1;
    console.log(`失败：${error.message.split("\n")[0]}`);
  }
  await context.close();
}

const canvas = JSON.parse(await readFile(join(hifiDir, "project/canvas.json"), "utf8"));
for (const [board, entry] of shots) {
  const context = await browser.newContext({ viewport: SIZES[entry.size] });
  const page = await context.newPage();
  await page.goto(`${hifiBase}/${board}.dc.html`, { waitUntil: "networkidle" });
  await page.evaluate(async () => {
    await document.fonts.ready;
  });
  entry.hifi = await page.screenshot({ type: "jpeg", quality: 70 });
  entry.title = canvas.boards[`${board}.dc.html`]?.title ?? board;
  await context.close();
}
await browser.close();
server.close();

const img = (buffer) => `<img src="data:image/jpeg;base64,${buffer.toString("base64")}" alt="">`;
const sections = [...shots]
  .map(([board, { size, hifi, app, title }]) =>
    `<section class="${size}"><h2>${title}</h2><p>${board}.dc.html</p><div class="pair">` +
    `<figure><figcaption>高保真稿</figcaption>${img(hifi)}</figure>` +
    `<figure><figcaption>实现</figcaption>${img(app)}</figure></div></section>`,
  )
  .join("\n");
const html = `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>PAP-68 视觉对照</title>
<style>body{margin:24px;font:14px/1.6 system-ui,sans-serif;background:#f6f4ef;color:#1d1b16}
h1{font-size:20px}h2{font-size:16px;margin:32px 0 0}p{margin:0;color:#6b665c}
.pair{display:flex;gap:16px;margin-top:8px}.pair figure{margin:0}
.desktop .pair figure{flex:1;min-width:0}.desktop img{width:100%}
figcaption{font-size:12px;color:#6b665c}img{display:block;border:1px solid #d9d4c7}</style>
<h1>PAP-68 视觉对照（${shots.size} 组，左边高保真稿、右边实现）</h1>
<p>没有截：${SKIPPED.join("、")}。封禁和限流要后端的状态，本机复现不了；它们的提示条和「邮箱或密码错误」是同一个组件。</p>
<p>${checks.join("<br>")}</p>
${sections}</html>`;
await writeFile(outFile, html);
console.log(`对照页：${outFile}，${shots.size} 组，失败的流程 ${failed} 个`);
for (const line of checks) console.log(`检查：${line}`);
```

`pap68-requests.mjs`：记下登录、退出登录时浏览器对 `/login`、`/papers` 发出的请求（spec 第 15 节第 3、6 条），打开登录页、注册页后同样先等页面接管表单：

```js
// PAP-68 实测 spec 第 15 节第 3、6 条：登录、退出登录时，浏览器对 /login、/papers 发出了哪些请求。
// 用法：node pap68-requests.mjs
// 前提：本机门户在 http://localhost:4000 跑着，连着 mini 的网关。先在注册页注册一个一次性账号（邮箱随机，
// 密码运行时生成，不打印、不落盘），清掉 Cookie，再从登录页登录、从用户菜单退出登录。
// 每个请求打印一行「阶段｜方法 路径 [标记]」。标记：action 是 Server Action；prefetch 是预取，segment=… 是
// 预取的哪一段；rsc 是页内导航取的 RSC。每个 action 和每个不带 prefetch 的 rsc 请求，服务端各渲染一次。
import { randomBytes } from "node:crypto";
import { createRequire } from "node:module";

const require = createRequire("/Users/linqibin/Projects/Products/patra/patra-portal/package.json");
const { chromium } = require("@playwright/test");

const APP = "http://localhost:4000";
const email = `requests-${randomBytes(6).toString("hex")}@example.org`;
const password = randomBytes(12).toString("base64url");

/// 打开页面，等页面接管 `selector` 指向的元素再往下做：React 接管一个元素时，在它上面挂一个以
/// `__reactProps$` 开头的属性。接管之前操作走的是浏览器原生的行为（点链接是整页跳转，点提交是浏览器
/// 自己提交表单），看到的就不是页面代码的行为
async function open(page, path, selector) {
  await page.goto(path);
  await page.waitForFunction((target) => {
    const element = document.querySelector(target);
    return element !== null && Object.keys(element).some((key) => key.startsWith("__reactProps$"));
  }, selector);
}

const browser = await chromium.launch();
const context = await browser.newContext();
const page = await context.newPage();

await open(page, `${APP}/register?next=%2Fpapers`, "form");
await page.getByLabel("邮箱").fill(email);
await page.getByLabel("密码", { exact: true }).fill(password);
await page.getByLabel("确认密码").fill(password);
await page.getByRole("button", { name: "注册" }).click();
await page.waitForURL(`${APP}/papers`, { timeout: 20_000 });
await context.clearCookies();

const lines = [];
let phase = "打开登录页";
page.on("request", (request) => {
  const url = new URL(request.url());
  if (url.origin !== APP || !["/login", "/papers"].includes(url.pathname)) return;
  const headers = request.headers();
  const flags = [
    headers["next-action"] ? "action" : "",
    headers["next-router-prefetch"] ? "prefetch" : "",
    headers["next-router-segment-prefetch"] ? `segment=${headers["next-router-segment-prefetch"]}` : "",
    headers.rsc ? "rsc" : "",
  ].filter(Boolean);
  lines.push(`${phase}｜${request.method()} ${url.pathname}${flags.length ? ` [${flags.join(",")}]` : ""}`);
});

await open(page, `${APP}/login?next=%2Fpapers`, "form");
// 预取在页面接管之后才发：再等一秒，让它落在这个阶段里
await page.waitForTimeout(1_000);
await page.getByLabel("邮箱").fill(email);
await page.getByLabel("密码", { exact: true }).fill(password);
phase = "点了登录之后";
await page.getByRole("button", { name: "登录" }).click();
await page.waitForURL(`${APP}/papers`, { timeout: 20_000 });
await page.waitForLoadState("networkidle");
phase = "退出登录";
await page.getByRole("button", { name: "账号菜单" }).click();
await page.getByRole("menuitem", { name: "退出登录" }).click();
await page.getByRole("link", { name: "登录" }).waitFor({ timeout: 20_000 });
await page.waitForLoadState("networkidle");

console.log(lines.join("\n"));
console.log(`最后停在：${new URL(page.url()).pathname}`);
await browser.close();
```

- [ ] **Step 2: 开发模式下跑全量 e2e**

用 `preview_list` 看 `portal-dev` 是否在跑：在跑就 `preview_stop` 停掉，再 `preview_start` 起 `portal-dev`。重启之后日志从头开始，Task 17 之前留下的输出（里面有明文密码）不会出现在 `preview_logs` 里。`lsof -nP -iTCP:4000 -sTCP:LISTEN` 应该只看到它。

Run: `env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY -u all_proxy -u ALL_PROXY PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528 pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test:e2e --workers=1`
Expected: `20 passed`，没有 skipped，也没有 failed：原有的 14 条，加上 `auth.spec.ts` 的 6 条。有 skipped 说明门户没连上 mini 的网关，先查原因再重跑。要是有文献检索的用例因为网关到 catalog 连接超时（`GW-0503`）失败，单独重跑那个 spec 文件一次：以前遇到过 tailnet 到容器网段的间歇抖动，和本 Issue 无关，结果里照实写。

- [ ] **Step 3: 视觉对照**

Run: `node <scratchpad>/pap68-visual.mjs <scratchpad>/hifi68 <scratchpad>/pap68-visual.html`
Expected: 输出的最后三行是：

```
对照页：<scratchpad>/pap68-visual.html，45 组，失败的流程 0 个
检查：站内点「登录」进入登录页后，焦点在：邮箱框
检查：已登录后用浏览器后退回到登录页：停在 /login，显示表单，请求 /login 0 次
```

前面每截一张打印一行 `截图：<画板名>`。失败的流程不是 0 时，看它打印的错误，按 superpowers:systematic-debugging 查原因。

自己先逐组看一遍。已知、可以接受的差异：输入框里的值和导航栏里的邮箱是随机生成的；`Register-TooLong` 里密码框有焦点，内容滚到了末尾；导航栏各组的页面正文是 mini 上的真实数据，高保真稿画的是骨架。除此之外的差异，记下画板名和差在哪里。用 SendUserFile（`display: "render"`）把对照页发给用户，不等回复，接着做 Step 4、5。

- [ ] **Step 4: 开发模式下的日志和请求**

先用 `preview_logs`（`portal-dev` 的 serverId，`search: "actions.ts"`）查一次。

Expected: 没有匹配的行（Task 17 生效）。有的话停下来，不要把内容贴到任何地方，回头查 Task 17。

Run: `node <scratchpad>/pap68-requests.mjs`
Expected:

```
打开登录页｜GET /login
点了登录之后｜POST /login [action]
点了登录之后｜GET /papers [rsc]
退出登录｜POST /papers [action]
最后停在：/papers
```

开发模式下 `router.prefetch` 和 `<Link>` 的预取都不发请求，所以没有 `prefetch`。`点了登录之后` 的 `GET /papers [rsc]` 可能是两行：action 的重渲染被跳转丢弃之后，Next 会补一次 refresh（spec 第 3 节、第 12 节第 1 条）。再用 `preview_logs`（`lines: 10`）看服务端这几次请求：`POST /login?next=%2Fpapers` 之后是一到两行 `GET /papers`，退出登录只有一行 `POST /papers`。日志按请求完成的先后排，注册之后那次 `GET /papers` 可能排在 `GET /login?next=%2Fpapers` 后面。最后可能跟着一行 `⨯ Error: The destination stream closed early.`：脚本结束时关掉浏览器，还在传的响应被中断，可以忽略。

- [ ] **Step 5: 生产构建下的 e2e 和请求**

开发模式不预取，spec 第 15 节第 3 条要在生产构建上测。

1. `preview_stop` 停掉 `portal-dev`。在 `.claude/launch.json` 里复制一份 `portal-dev`，名字改成 `portal-prod`，`runtimeArgs` 最后的 `"dev"` 换成 `"start", "-p", "4000"`。
2. Run: `pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal build`
   Expected: 退出 0，`✓ Compiled successfully`。
3. `preview_start` 起 `portal-prod`。日志里的 `⚠ "next start" does not work with "output: standalone" configuration.` 可以忽略：服务照常起来，镜像里用的是 standalone，这里只是在本机测预取。
4. Run: `env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY -u all_proxy -u ALL_PROXY PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528 pnpm --dir /Users/linqibin/Projects/Products/patra/patra-portal test:e2e --workers=1`
   Expected: `20 passed`，账号流程那条在 3 秒左右（开发模式下几秒到三十秒，路由第一次打开时要现编译）。
5. Run: `node <scratchpad>/pap68-requests.mjs`
   Expected:

   ```
   打开登录页｜GET /login
   打开登录页｜GET /papers [prefetch,segment=/_tree,rsc]
   打开登录页｜GET /papers [prefetch,rsc]
   点了登录之后｜POST /login [action]
   点了登录之后｜GET /papers [rsc]
   退出登录｜POST /papers [action]
   最后停在：/papers
   ```

   登录页的「返回」链接指向 `next`，页面一加载就预取了 `/papers`；点登录时 `router.prefetch(next)` 命中这份预取，不再发请求。action 之后取一次 `/papers`；退出登录时，当前页在 action 的响应里重新渲染，没有别的请求。
6. `preview_stop` 停掉 `portal-prod`，从 `.claude/launch.json` 删掉这个配置，再 `preview_start` 起回 `portal-dev`。

- [ ] **Step 6: 请用户验收**

在这一轮的最后一条消息里，用纯文本请用户做三件事（第 2、3 件是用户自己的操作，不代做）：

1. 看 Step 3 发出的对照页，逐组确认；附上 Step 3 自己看出的差异。
2. 在系统 Chrome 打开 `http://localhost:4000/papers`，点「注册」注册一个账号，让 Chrome 保存密码；退出登录后再点「登录」，用 Chrome 的自动填充登录。看三点：填充后没有冒出错误；输入框底色保持纸色；登录后回到 `/papers`（Review Focus 第 5 条）。
3. 桌面宽度下，Tab 到右上角的圆形标识，按 Enter 打开用户菜单，再按 ↓：菜单项上会有焦点环，高保真稿只画了高亮底色，确认是否接受（「与 spec 的出入」第 14 条）。

等用户回复。用户指出的差异按 superpowers:test-driven-development 修（先写失败的测试，再改），修完重跑受影响的步骤（Step 2 ~ 5 中相关的几步），把新的对照页再发一次。用户确认之后再往下做。

- [ ] **Step 7: 结果写回 spec**

`docs/patra/specs/2026-10-12-portal-auth-pages-design.md`：

- 头部的「状态」改成「已实施（<日期>，分支 `feat/v0.8-accounts-fe`）」，`<日期>` 写验收当天，格式 `YYYY-MM-DD`，下同。
- 第 15 节最后一句「实施后把结果写回本节。」改成「实施后本地做，结果写回本节（<日期> 实测，门户是本分支 Task 18 之后的代码，网关和 identity 是 mini 上在跑的版本）。」
- 第 1 到 6 条下面各加一行 `   - 实测：…`，写实际看到的结果。和 Step 2 ~ 6 的预期一致时，可以照下面写：
  1. 实测：Tailwind 4.3 把 `focus-within:` 生成在 `hover:` 之前，悬停加聚焦时描边是悬停色，三种状态都改成了 `has-[…]`（第 9.1 节）；错误态聚焦时的 `ring-rust-500/15` 通过 focus 守护测试；视觉对照里 `Login-EmailFormat`、`Register-Invalid` 等错误态的描边和高保真稿一致。
  2. 实测：原来写在类名里的 `--normal-*` 变量没有生效，改写在 `Toaster` 的行内样式上，阴影用 `shadow-lg!`（第 11 节）；成功图标是 moss 色；`Nav-LoggedIn`、`Nav-M-Toast` 两组对照和高保真稿一致，移动端的位置也一致。
  3. 实测：开发模式不预取。生产构建下，登录页的「返回」链接一加载就预取了 `/papers`，点登录时 `router.prefetch(next)` 不再发请求；之后是 `POST /login`（action）和一次 `GET /papers`。e2e 在生产构建上 20 条全部通过。
  4. 实测：站内点「登录」进入登录页后，焦点在邮箱框。
  5. 实测：已登录后用浏览器后退回到登录页，停在 `/login`，显示表单，没有请求 `/login`，不经过 proxy（开发模式），即第 12 节第 4 条的情况。
  6. 实测：登录成功后 `GET /papers` 一到两次，退出登录只有 action 那一次 `POST /papers`，之后没有别的请求，和第 12 节一致。开发时终端里只有请求行，没有 Server Action 的参数（第 13 节）。

- [ ] **Step 8: 提交**

```bash
git -C /Users/linqibin/Projects/Products/patra add docs/patra/specs/2026-10-12-portal-auth-pages-design.md
git -C /Users/linqibin/Projects/Products/patra diff --cached --stat
git -C /Users/linqibin/Projects/Products/patra commit -F - <<'MSG'
docs(portal): 记下登录页、注册页与用户菜单的本地验收结果 (PAP-68)

开发模式和生产构建各跑一遍 e2e；两个尺寸的视觉对照；预取和渲染次数；站内进入登录页时的焦点；
后退回登录页的表现。结果写回 spec 第 15 节，spec 状态改为已实施。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
MSG
```

Expected: 暂存区只有 spec 这一个文件；pre-commit 全部通过。

- [ ] **Step 9: 同步 Linear**

用 ToolSearch 加载 Linear 的 `get_issue`、`save_issue`。先 `get_issue` 读当前描述，再用 `save_issue` 只改描述，不改状态。

PAP-68：

- To-Do 最前面加一项「Tech Design（`docs/patra/specs/2026-10-12-portal-auth-pages-design.md`）」并勾上；原有 7 项全部勾上。
- AC 7 条全部勾上。第 2 条里的「被暂时限制」「账号已被封禁」本机复现不了，由 `LoginForm` 的组件测试逐条覆盖（Task 7）；第 5 条以 Step 6 用户确认对照页为准。

PAP-67：勾上 AC 第 1、2、3、5 条。这四条留到这里，是因为要用登录页、注册页和用户菜单在浏览器里走一遍，Step 2 的账号流程和 Step 6 用户的操作已经走过。

Expected: 再 `get_issue` 读一次，两个 Issue 的描述和上面一致，状态没变。

---

## 执行后的收尾

- 全部任务做完后，按所选的执行方式做一次全分支的最终评审（用最强的模型）。评审范围从 spec 提交 `f464c18e1` 到 HEAD，把本计划的 Review Focus 原样交给评审者。最终评审有改动时，重跑 Task 19 Step 2，必要时重跑 Step 3。
- 走 `superpowers:finishing-a-development-branch` 时，先停下来等用户明确说可以开 PR（根 `CLAUDE.md`：改动先本地验收）。用户同意后：
  1. 更新 release spec `docs/patra/release-specs/v0.8-accounts.md` 头部的「状态」：前端 PAP-67、PAP-68 已在 `feat/v0.8-accounts-fe` 完成、PR 评审中，日期写当天。单独提交，主题照以前的写法：`docs(accounts): 状态行改为前端已完成、PR 评审中`。
  2. 推送分支，开一个 draft PR 覆盖 PAP-67 和 PAP-68，描述里逐行写 `Closes PAP-67`、`Closes PAP-68`，末尾是 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`。
  3. `gh pr create` 之后立即启动 Monitor 盯住这个 PR；转 ready 的时机、三位 AI reviewer 的节奏和每条意见的处理状态，照根 `CLAUDE.md` 的「PR 与代码评审」。
- 向用户汇报时列出：执行中的全部 Ruling、最终评审里延后的 minor，以及 Task 19 里用户确认过的差异。
