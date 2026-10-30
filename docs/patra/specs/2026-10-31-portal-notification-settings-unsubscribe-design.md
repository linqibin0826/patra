# 门户通知设置页、退订落地页与一键退订转发（PAP-148）工程 spec

> **Issue**：Linear PAP-148（GitHub #335），v0.16 Notification，Milestone FE Ready；同版前端还有 PAP-147（提醒改接通知服务）、PAP-149（实时推送），按 148 → 147 → 149 串行。
> **产品定义**：release spec `docs/patra/release-specs/v0.16-notification.md` 2.1（偏好三档、默认离站时逐条、有帮助只在站内、安全邮件不进偏好、一键退订）、第 3 节 G（公网只开放门户）、D5。
> **页面、状态、文案**：设计简报 `patra-portal/docs/design-briefs/v0.16/notification.md` §2、§3.5、§3.6、§4.2、§4.3、§6；画布 <https://claude.ai/artifact/8Ff2pvLjosq8sfdRV4PDBP> 的 `Notify` / `Notify-M`、`Unsub` / `Unsub-M`（快照 `docs/patra/design/snapshots/2026-10-30-notification-hifi.zip`）。本 spec 不重抄文案，实现时逐条对照简报的状态 ID；简报没写、由我补的文案在第 9 节列出。
> **接口**：后端工程 spec `docs/patra/specs/2026-10-31-notification-preference-unsubscribe-design.md`（PAP-142，在 `claude/v016-preference-2c3cbe` 分支）第 5、7、8 节；摘要见共享笔记 `PAP-142-preference-contract.md`。后端尚未合并部署：本 Issue 按契约写代码与单测（mock 网关），联调与 Playwright 等 mini 部署后再跑。
> **决策授权**：用户 10/31 凌晨授权子会话按最优方案拍板（经编排会话转达），本 spec 里「我定的」都列在第 11 节。
> **状态**：已实施，待联调（2026-10-31，计划 `docs/patra/plans/2026-10-31-portal-notification-settings-unsubscribe.md`）；Playwright 冒烟等 PAP-142、PAP-146 部署到 mini 后补；实施中的偏离见第 12 节
> **创建日期**：2026-10-31

---

## 1. 要做什么

三件事：

1. **设置 › 通知**（`/settings/notifications`）：「回复我的」「@ 我的」各三档单选，改了立即保存这一类；「有帮助」只有说明；账号安全邮件说明块；邮箱未验证时顶部提示。
2. **退订落地页**（`/unsubscribe#token=…&category=…`，免登录）：打开时只读一次链接信息，点按钮才退订；能退这一类、关闭全部；令牌无效有专门的屏。
3. **一键退订转发**（`/unsubscribe/one-click?token=…&category=…`）：Gmail 等邮箱服务商的 POST 原样转给网关、把状态码交回去；浏览器打开（GET）跳到确认页。

## 2. 先讲清三个前端概念

后端 spec 第 2 节已经讲了退订令牌、RFC 8058 一键退订、「打开不改东西」与幂等，这里只讲门户这边新碰到的三件事。

### 2.1 Route Handler、Server Action、RSC：门户收请求的三种入口

**是什么**：Next 的门户代码跑在服务器上时，有三种方式「接」一个请求。

| 入口 | 谁发请求 | 典型用途 |
|---|---|---|
| RSC（服务端组件，`page.tsx`） | 浏览器打开页面 | 渲染页面前先取数据，比如设置页先读偏好 |
| Server Action（`"use server"` 函数） | 门户自己页面里的按钮、表单 | 页面上点一下要改东西，比如保存档位、点「退订」 |
| Route Handler（`route.ts`） | 任何能发 HTTP 的东西 | 对外暴露一个固定地址，比如未读数接口、文件下载 |

**防什么**：Server Action 的地址是 Next 内部生成的、每次构建都可能变，还会检查请求的 `Origin` 必须是门户自己；RSC 只认浏览器打开页面。邮件里的一键退订地址要**写死在已经发出去的邮件里、长期有效**，而且请求是 **Gmail 的服务器**发的（没有浏览器、没有 Cookie、没有 `Origin`），所以只能是 Route Handler。

**业界怎么做**：Discourse、GitLab 都是给一键退订单独开一条服务端路由，只认地址里的令牌，不走登录态。

**我们的选择**：`/unsubscribe/one-click` 是 Route Handler；设置页读偏好用 RSC，保存用 Server Action；落地页的读和写都用 Server Action（同现有四个邮件落地页）。

### 2.2 为什么一键退订的 Route Handler 故意不查同源

**是什么**：门户现有的写操作 Route Handler 都先调 `isSameOrigin`：请求必须带 `Origin` 头、而且就是门户自己，用来防跨站请求伪造（CSRF：别的网站诱导你的浏览器替你发请求，带着你的 Cookie 改你的东西）。

**这里为什么不查**：一键退订的请求来自邮箱服务商的服务器，本来就没有 `Origin`，查了就全被挡掉。不查也安全，因为 CSRF 的前提是「浏览器自动带上了你的 Cookie」，而这个地址：

- 不读 Cookie、不带会话令牌去网关（第 6.1 节），能不能退订**只看地址里的令牌**；伪造请求的人得先有令牌，有令牌本来就能退订；
- 只能把邮件**关掉**、不能打开，重复请求结果一样（幂等）；
- 不回任何个人信息（响应体是空的）。

### 2.3 专注页名单（`FOCUS_PAGES`）与 `proxy.ts`

**是什么**：门户的 `proxy.ts`（Next 16 里 middleware 的新名字）在每个页面请求进来时先跑一遍。其中一条规则：账号在注销冷静期的人地址栏打开任何页面，都先被 307 到「取消注销」页，原地址放进 `next` 参数。`FOCUS_PAGES` 是例外名单（登录、注册、四个邮件落地页、取消注销页），名单里的页面不被拦，也不能当 `next`。

**防什么**：冷静期的人点了邮件里的退订链接（后端规定冷静期账号的链接照常有效，PAP-142 第 5.4 节）：

- 不加进名单：`/unsubscribe/one-click?token=…` 被 307 到 `/account/deletion-pending?next=%2Funsubscribe%2Fone-click%3Ftoken%3D…`，**令牌被抄进了另一个地址**，退订也没完成；
- `/unsubscribe` 的令牌在片段里不会被抄走，但人会被带去取消注销页，同样退订不成。

**我们的选择**：两个地址都加进 `FOCUS_PAGES`。一键退订的 POST 本来就不被拦（冷静期检查只看 GET），GET 是浏览器打开，必须靠名单放行。

## 3. 范围

**做**：

1. 调用层：`src/lib/notification/`（请求通知服务、schema、偏好读写、退订读写）。
2. 设置 › 通知：页面、骨架、表单、取不到时的卡片；设置导航加「通知」，窄屏 5 等分。
3. 把隐私页的 `RadioRow`、`SaveStatus` 提到 `components/settings/` 共用（简报 §5 的建议）。
4. 退订落地页 `UnsubscribeLanding`；把 `TokenLandingPage` 里「读片段、抹掉、严格模式下只读一次」的做法提成一个 hook，两者共用。
5. 一键退订 Route Handler（POST 转发、GET 跳转）。
6. `FOCUS_PAGES` 加两个地址；`next.config.ts` 的开发期请求日志忽略一键退订地址（第 6.3 节）。
7. 单测（对照 AC）+ 1440×900 / 375×812 的截图核对（本地数据桩）。

**不做 / 后做**：

- Playwright 冒烟（B 回复 A → Mailpit 收提醒邮件 → 邮件链接回到那一楼；点邮件里的退订 → 落地页确认 → 设置页显示为关）：要 PAP-142、PAP-146 合并并部署到 mini 后才能跑，等编排会话通知。届时 `tests/e2e/support/mailpit.ts` 的 `latestToken` 还能从 `#token=…&category=…` 里取到令牌（正则只认 `#token=` 后 43 个字符），但要另加一个取类别的函数。AC 第 4 条到那时才能勾。
- 邮件本身、退订头、门户地址配置：PAP-146。
- 免登录重新订阅（简报 §3.6 明确不做）。

## 4. 简报与后端的对照

| 处 | 简报 | 后端（PAP-142） | 门户做法 |
|---|---|---|---|
| 偏好读 | 两类三档，默认离站时逐条 | `GET /me/notification-preferences` → `{reply, mention}`，没设过是 `INSTANT` | RSC 读；401 去登录页；其余失败出「暂时打不开」卡片 |
| 偏好写 | 按类即时保存，失败回原值 | `PUT /me/notification-preferences/{REPLY\|MENTION}` `{mode}` → 两类当前档位；422 类别 / 档位不合法 | Server Action；422 是门户自己发错了，按失败处理并记日志 |
| 落地页读 | 打开只读：地址、类别、是否已退订、其余开没开 | `GET /unsubscribe?token&category` → `maskedEmail` 等 6 个字段 | 挂载后读片段再调；`alreadyUnsubscribed` → `unsub.already`；`othersEnabled` 决定出不出「关闭全部」 |
| 落地页写 | 按类退订、关闭全部、结果页「同时关闭其余」 | `POST /unsubscribe` `{token, category}`，类别多一个 `ALL`；回 `turnedOff` 与退订后两类档位、`othersEnabled` | Server Action；`turnedOff` 为空（别处已经关了）出 `unsub.already`（第 7.3 节） |
| 无效链接 | 一个 `unsub.invalid` | 一律 404 + `traits` 含 `UNSUBSCRIBE_LINK_INVALID` | 只有带这个原因码的 404 算无效；裸 404（网关路由缺失）、503 等算服务错误 |
| 读失败 | 简报没有专门的状态 | identity 挂了 503 | 出无效屏不对（链接是好的），出 `unsub.error` 的提示条也不对（还没提交）：我定出一屏「暂时打不开」+ 重试（第 9 节文案） |
| 会话 | 落地页带着登录状态也只按令牌工作 | 门户调三个退订接口不带会话令牌 | 退订的三处调用一律 `auth: "none"`；登录状态只用来决定「去通知设置」的写法 |

## 5. 调用层（`src/lib/notification/`）

### 5.1 `request.ts`（server-only）

`callNotification(path, init, options)`：照 `callInteraction`，前缀 `/patra-notification`，返回同形的 `IdentityReply`（`ok` / `problem` / `no-session` / `error`）。

- `options.auth`：`"required"`（默认，偏好接口：没有可用的会话令牌就不请求，回 `no-session`）或 `"none"`（三个退订接口：不读 Cookie、不带令牌）。
- `options.query`：查询参数，拼进请求地址；**日志只记不带查询串的路径**（`logRequestError` / `logUnexpectedResponse` 的 `endpoint`）。令牌只出现在发给网关的地址里，不进门户日志（PAP-142 第 15 节）。
- 带上浏览器来源（`readClientOrigin()`），同其他服务。

### 5.2 `schema.ts`

```ts
NOTIFICATION_MODES = ["OFF", "INSTANT", "DIGEST"]
PREFERENCE_CATEGORIES = ["REPLY", "MENTION"]            // 设置页能改的两类
LINK_CATEGORIES = ["REPLY", "MENTION", "DIGEST"]        // 邮件链接里的类别
UNSUBSCRIBE_CATEGORIES = [...LINK_CATEGORIES, "ALL"]    // 落地页能提交的类别

preferencesSchema   { reply: mode, mention: mode }
unsubscribeLinkSchema { maskedEmail: string(min 1), category: linkCategory, reply, mention,
                        alreadyUnsubscribed: boolean, othersEnabled: boolean }
unsubscribeResultSchema { category: unsubscribeCategory, turnedOff: ("REPLY"|"MENTION")[],
                          reply, mention, othersEnabled: boolean }
```

### 5.3 偏好（`preferences.ts` server-only、`preference-actions.ts` `"use server"`）

- `fetchMyNotificationPreferences(): Promise<NotificationPreferences | "signed-out" | "unavailable">`：没有会话、401 → `signed-out`；200 且形状对 → 数据；其余（超时、5xx、形状不对）记日志 → `unavailable`。**不抛错**：简报要在原地出「暂时打不开」卡片，其余分区照常（隐私页是抛错进错误页，这里不同）。
- `saveNotificationMode(category, mode)` → `{ ok: true; preferences } | { ok: false; reason: "signed-out" | "unavailable" }`：入参先按 schema 校验（不合格直接 `unavailable`，不发请求）；会话失效（`isSessionRejected`）清会话 Cookie → `signed-out`；其余失败记日志 → `unavailable`。

### 5.4 退订（`unsubscribe-actions.ts` `"use server"`）

- `readUnsubscribeLink(token, category)` → `{ ok: true; link } | { ok: false; reason: "invalid" | "unavailable" }`。
- `unsubscribe(token, category)`（`category` 含 `ALL`）→ `{ ok: true; result } | { ok: false; reason: "invalid" | "unavailable" }`。
- 两者入参先校验：令牌不是 43 个 base64url 字符、类别不认识 → `invalid`，不请求网关。
- `invalid` 只认 404 + `UNSUBSCRIBE_LINK_INVALID`；其余失败记日志（不带查询串、不带请求体）→ `unavailable`。

### 5.5 片段解析（`unsubscribe-link.ts`，浏览器用）

`readUnsubscribeFragment(hash): { token; category } | null`：令牌复用 `readFragmentToken`，类别必须是 `REPLY` / `MENTION` / `DIGEST`，任一不对就是 null（落地页直接出无效屏，不请求后端）。

## 6. 一键退订 Route Handler（`src/app/unsubscribe/one-click/route.ts`）

### 6.1 POST：转发

1. 从 `request.nextUrl.searchParams` 取 `token`、`category`（只取这两个，各取第一个；缺了就不带），拼成查询串。
2. `callNotification("/unsubscribe/one-click", { method: "POST" }, { auth: "none", query })`：不带会话令牌、不跟随重定向（`gatewayFetch` 默认 `manual`）。**不转发请求体**：后端不看请求体、令牌与类别只从查询串取（PAP-142 第 8.4 节，10/31 评审后改的：表单体里夹带同名参数会被 Spring 拼进查询参数），门户也就不读它，省得读任意大小的请求体，也不会把伪造的同名参数带过去。
3. 回给对方：网关的状态码、空响应体、`Cache-Control: no-store`。请求抛错（超时、网络、缺配置）→ 502。
4. **不查同源**（第 2.2 节），不读 Cookie，不重定向。

### 6.2 GET：跳确认页

`303 See Other`，`Location: /unsubscribe#token=…&category=…`（相对地址，用 `URLSearchParams` 拼；缺的参数不写，缺令牌时落地页出无效屏），带 `Referrer-Policy: no-referrer` 与 `Cache-Control: no-store`。不请求网关、不改任何东西。用相对地址：门户在 Cloudflare Tunnel 后面，`request.url` 的主机不一定是公网域名，相对地址交给浏览器按当前地址解析，不会跳错主机。

其余方法：Next 对没定义的方法自动回 405（`HEAD` 跟 `GET`，`OPTIONS` 自动带 `Allow`），不用写。

### 6.3 令牌不进日志

- Route Handler 不打 `request.url`。
- `next dev` 默认在终端打印每个进来的请求（带查询串）：`next.config.ts` 加 `logging.incomingRequests.ignore: [/\/unsubscribe\/one-click/]`。只影响开发期（生产构建不打这类日志）。

### 6.4 `proxy.ts`

不改代码，只改名单：

- POST 没有 `Sec-Fetch-Dest`，路径分段 `["", "unsubscribe", "one-click"]` 不是详情页 → 冷静期检查只看 GET → `next()`。
- GET：浏览器打开是 `document`，靠 `FOCUS_PAGES` 放行（第 2.3 节）。`/unsubscribe` 同理。

## 7. 退订落地页（`/unsubscribe`）

### 7.1 页面

`src/app/unsubscribe/page.tsx`：`metadata` `title: "退订提醒邮件 · Patra"`、`robots: { index: false }`；只渲染 `<UnsubscribeLanding />`。版式 `AuthShell`（同四个落地页），`back="/"`。

### 7.2 与 `TokenLandingPage` 的关系

现有 `TokenLandingPage` 是「读片段 → 就绪（不发请求）→ 点一个按钮提交一次 → 结果屏」。退订页不一样：

- 挂载后要先**读一次**（拿脱敏邮箱、当前档位），就绪前就可能是「已经退订过」「无效」；
- 就绪态有**两个**提交动作（这一类、`ALL`）；
- 结果屏还能再提交一次（「同时关闭其余」），所以成功后令牌还得留在手上。

**选择**：`UnsubscribeLanding` 自己写状态机，复用 `AuthShell`、`StateIcon`、`FormAlert`；只把 `TokenLandingPage` 里那段「读片段、立即 `replaceState` 抹掉、严格模式下 effect 跑两次也只读一次」提成 hook `useFragmentOnce(parse)`，两边共用。**代价**：动一次 `TokenLandingPage`（行为不变，靠它现有的测试守住）；不把 `TokenLandingPage` 扩成万能状态机，四个旧页面不受影响。

`AuthShell` 的 `title`、`description` 改为可选：读取中的骨架连标题一起画（画板 `unsub.reading`），这时不出 `h1`，骨架容器 `role="status"` + 读屏文字「正在读取退订链接」。

### 7.3 状态机

| 阶段 | 进入条件 | 屏 |
|---|---|---|
| `reading` | 首次渲染（服务端与浏览器一致） | 骨架：标题、说明、两行信息表、按钮 |
| `unavailable` | 读接口失败（非无效） | 第 9 节「暂时打不开」屏 + 重试（重新读，令牌还在内存里） |
| `invalid` | 片段不对、读或写返回无效 | `unsub.invalid` |
| `ready` | 读到、`alreadyUnsubscribed: false` | `unsub.ready`；`failed` 时信息表下方 `unsub.error` 提示条 |
| `submitting` | 点了任一按钮 | 被点的那个按钮写「退订中…」，两个都 `aria-disabled` |
| `already` | 读到 `alreadyUnsubscribed: true`；或提交这一类后 `turnedOff` 为空 | `unsub.already` |
| `done-type` | 提交这一类、`turnedOff` 非空 | `unsub.done.type` |
| `done-all` | 提交 `ALL` 成功 | `unsub.done.all` |

- `already` / `done-type` 屏上的「同时关闭其余提醒邮件」只在 `othersEnabled` 为真时出（读或写的响应里的那个）；点了提交 `ALL`，提交中按钮写「退订中…」、两个按钮不能点；失败在按钮上方出 `unsub.error` 的提示条，停在本屏；无效 → `invalid`；成功 → `done-all`。
- 就绪态焦点放在主按钮上（同 `TokenLandingPage`）。
- 「去通知设置」：`useCurrentUser()` 是已登录 → `去通知设置` → `/settings/notifications`；否则 `登录后去通知设置` → `/login?next=%2Fsettings%2Fnotifications`。
- 按钮都是 `type="button"`、不包表单：它们只在挂载并读到链接之后才出现（服务端渲染出来的只有骨架），脚本没加载完时页面上没有可点的东西。

### 7.4 文案的计算（对照简报 §3.6、画板 `Unsub` 的 `KIND_TEXT` / `DONE_DESC`）

- 类别名：`REPLY` →「回复我的」，`MENTION` →「@ 我的」。档位名：`OFF` 关、`INSTANT` 离站时逐条、`DIGEST` 每日汇总。
- 就绪态主按钮：`REPLY` / `MENTION` 是「退订这类邮件」，`DIGEST` 是「退订每日汇总」。
- 信息表「邮件」一行：`REPLY` / `MENTION` 写 `「回复我的」提醒 · {当前档位名}`；`DIGEST` 写 `每日汇总 · 包括` + 此刻处于汇总的类别（如 `每日汇总 · 包括「@ 我的」`）。
- 「这一类邮件」的称呼（结果屏标题）：`REPLY` / `MENTION` 是 `「回复我的」提醒邮件`，`DIGEST` 是 `每日汇总邮件`；`done-type` 标题 `已退订{称呼}`，`already` 标题 `{称呼}已经退订过了`。
- `done-type` / `already` 的说明：`REPLY` / `MENTION` 是简报里两句固定说明。`DIGEST` 按两类档位写：
  - 「关掉的」：`done-type` 用 `turnedOff`，`already` 用此刻是 `OFF` 的类别；「还开着的」：此刻不是 `OFF` 的类别。
  - 关掉两类：`「回复我的」「@ 我的」都改为不发邮件，提醒页里照常能看到。`
  - 关掉一类：`「@ 我的」改为不发邮件，提醒页里照常能看到` + 还开着的那类有就接 `；「回复我的」仍是离站时逐条。`，没有就 `。`
  - 一类都没关（`already`、两类都是离站时逐条）：第 9 节补的文案。

## 8. 设置 › 通知（`/settings/notifications`）

### 8.1 页面与骨架

- `src/app/settings/notifications/page.tsx`：`requireUser(PAGE)`；`fetchMyNotificationPreferences()`；`signed-out` → 登录页（`next` 是本页）；`metadata` `title: "通知 · 设置 · Patra"`。
- 内容自上而下：邮箱未验证提示（有时）→ 取到时是表单，取不到时是「暂时打不开」卡片（只有这张卡，不出有帮助与安全说明，同画板）。
- `src/app/settings/notifications/loading.tsx`：两张卡片骨架（标题、说明、三行单选），`aria-hidden` + 外层读屏文字。只包这一页（`loading.tsx` 放在 `notifications/` 下）。
- 「暂时打不开」卡片的「重试」：`router.refresh()` 包在 `useTransition` 里，进行中按钮不可点。

### 8.2 `NotificationSettingsForm`（客户端）

- 顶部一行：`提醒邮件发到 {user.email}` + `更改邮箱` → `/settings/account#email`。
- 「回复我的」「@ 我的」两张 `SettingsCard`，说明照简报；`role="radiogroup"`，`aria-label` 写 `「回复我的」提醒邮件` / `「@ 我的」提醒邮件`；三行 `RadioRow`，「离站时逐条」标题旁一直有「默认」（`badge` 位）；卡片标题右侧 `SaveStatus`。
- 「有帮助」卡片：灰色胶囊（铃铛 + 只在站内）+ 说明；没有控件。
- 账号安全邮件说明块：`paper-100` 底、`border-subtle` 描边、锁图标，不是卡片。
- 邮箱未验证提示条放在页面里（不在表单里），带 `ResendVerificationButton`（文字按钮，`重新发送验证邮件`）。

### 8.3 保存（两类共用一条队列）

- 改一档：界面立刻显示新档、这张卡「保存中…」；请求排进**一条**队列，按点的顺序逐个发。
  - 为什么不让两类并行：Next 的客户端本来就把 Server Action **逐个派发、逐个等待**（`node_modules/next/dist/docs/01-app/01-getting-started/07-mutating-data.md`「Invoking Server Functions」），两条队列在浏览器里也会排成一条；代码照实际行为写成一条（同隐私页），不假装并行。代价：一类的保存卡住（最长 10 秒超时）时，另一类的「保存中…」也跟着等。
- 一次请求回来：
  - 成功：记下服务端确认的这一类；如果这是这一类最后一次改动，显示它、标「已保存」（2 秒后淡出）。响应里的**另一类**：另一类此刻没有排队或在途的改动时，一并采用（这样从邮件退订了另一类、又在这页改了这一类，另一类也会变成服务端的值）；有就不碰。
  - 失败（`unavailable`）：是这一类最后一次改动 → 回到服务端最后确认的值、标「没有保存成功，请重试」；后面还有在途的 → 继续「保存中…」，等最后一次。
  - `signed-out`：`useCommonFailureRedirect` 去登录页。
- 不出 toast（同隐私页）。

### 8.4 共用组件

- `src/components/settings/RadioRow.tsx`：从 `PrivacyForm` 原样搬出，加可选 `badge`（标题后的小字）。
- `src/components/settings/SaveStatus.tsx`：原样搬出，导出 `SaveState` 类型。
- `PrivacyForm` 改为 import 它们，现有测试保持绿色。

### 8.5 设置导航

`SettingsNav` 加 `{ href: "/settings/notifications", label: "通知", short: "通知" }`（隐私之后），窄屏 `grid-cols-5`；注释改成「五个分区」。

## 9. 简报没写、我补的文案

| 处 | 文案 |
|---|---|
| 落地页读接口失败（`unavailable` 屏，红色感叹号） | h1 `暂时打不开退订链接`；说明 `请稍后点「重试」。如果已经离开这一页，重新点邮件里的退订链接即可。`；主按钮 `重试`（读中 `读取中…`）；页脚 `账号安全邮件不受通知设置影响。`（片段读到即抹掉，刷新后令牌就没了，所以说明不让人刷新，而是点重试或回邮件重点；重试用内存里的令牌） |
| 每日汇总已经退订过、两类都是离站时逐条 | `「回复我的」「@ 我的」现在都是离站时逐条，不会再收到每日汇总。` |
| 骨架的读屏文字 | 落地页 `正在读取退订链接`；设置页 `正在读取通知设置` |
| 读屏下单选组名 | `「回复我的」提醒邮件`、`「@ 我的」提醒邮件`（画板写的是「标题 + 的提醒邮件」，会读成「回复我的的提醒邮件」） |

## 10. 测试（对照 AC）

| AC | 测试 |
|---|---|
| 设置页能读写偏好，刷新后保持 | `preferences.test.ts`（读：200 / 401 / 503 / 形状不对；不抛错）、`preference-actions.test.ts`（PUT 路径与请求体、两类档位、会话失效清 Cookie、422 记日志按失败）、`NotificationSettingsForm.test.tsx`（初始值、改一档即时保存「保存中…」→「已保存」淡出、失败回原值、同一类连点只回最后确认值、两类排一条队各自收尾、响应里另一类的采用规则、未登录去登录页）、`notification-settings-page.test.tsx`（取不到出卡片 + 重试、未验证提示、`signed-out` 跳登录页）。「刷新后保持」= 页面每次由 RSC 读服务端值，联调时 Playwright 再证 |
| 不登录能在落地页退订这一类、关闭全部；只打开页面不会退订 | `unsubscribe-actions.test.ts`（读 / 写的路径、查询串、请求体；**不带 `Authorization`**；404 + 原因码 → 无效，裸 404 / 503 → 不可用；入参不合格不请求；日志里没有令牌）、`UnsubscribeLanding.test.tsx`（打开只调读、不调写；片段读到即抹掉；类别不对直接无效屏；三类的就绪文案；点「退订这类邮件」→ 已退订；「关闭全部」→ 已关闭全部；结果屏「同时关闭其余」；`othersEnabled: false` 不出；`turnedOff` 为空出已经退订过；提交失败提示条；读失败屏 + 重试；已登录 / 未登录两种「去通知设置」） |
| 对门户的 `/unsubscribe/one-click` 发 POST，这一类直接退订；发 GET 只跳确认页、不改设置 | `unsubscribe-one-click-route.test.ts`（POST：转发到 `/patra-notification/unsubscribe/one-click?token&category`、方法 POST、无 `Authorization`、只带这两个参数、状态码原样、空响应体、不重定向、网关抛错 502、不看 `Origin`；GET：303 到 `/unsubscribe#token=…&category=…`、`Referrer-Policy: no-referrer`、不请求网关） |
| Playwright 冒烟 | mini 部署后补（第 3 节） |
| 1440×900 与 375×812 下按画板显示 | 本地数据桩起门户，Playwright 截图与画板对照（设置页默认 / 汇总 / 退订后 / 未验证 / 取不到 / 骨架；落地页就绪三类 / 结果 / 已退订过 / 无效 / 提交失败） |

其余：`next-path.test.ts`（两个新专注页）、`SettingsNav.test.tsx`（5 个分区）、`TokenLandingPage.test.tsx`（hook 抽出后行为不变）、`unsubscribe-link.test.ts`（片段解析）、`AuthShell` 无标题时不出 `h1`。

## 11. 我定的

1. 落地页自写状态机，只从 `TokenLandingPage` 抽「读片段」hook（第 7.2 节）；`AuthShell` 标题可选，为了读取中画标题骨架。
2. 读接口失败单独一屏「暂时打不开」+ 重试（第 9 节）；提交后 `turnedOff` 为空按「已经退订过」显示（第 7.3 节）。
3. 每日汇总说明按 `turnedOff` / 当前档位计算，补了「两类都是离站时逐条」的一句（第 7.4、9 节）。
4. 一键退订：只转 `token`、`category` 两个参数，不转请求体；网关抛错 502；GET 用 303 + 相对地址（第 6 节）。
5. 两个新地址加进 `FOCUS_PAGES`（第 2.3 节）；开发期请求日志忽略一键退订地址（第 6.3 节）。
6. 设置页取不到偏好时不抛错、原地出卡片；骨架用 `loading.tsx`（第 8.1 节）。
7. 两类共用一条保存队列（Next 本来就逐个派发 Server Action）；响应里另一类只在它没有排队或在途的改动时采用（第 8.3 节）。
8. `RadioRow` / `SaveStatus` 提到 `components/settings/`（简报 §5 的建议）。
9. 落地页「提交中」：被点的按钮写「退订中…」（画板只改了主按钮，点「关闭全部」时也要有反馈）。
10. 单选组的读屏名去掉重复的「的」（第 9 节）。
11. 调用层目录叫 `src/lib/notification/`（单数）：与 `identity/`、`interaction/` 一样按**后端服务**命名，放请求、schema 与 Server Action；现有的 `src/lib/notifications/`（复数）是提醒页的界面逻辑（分组、查询键、行）。PAP-147 把提醒列表的调用从 `interaction/notifications.ts` 搬进 `notification/`，界面逻辑留在 `notifications/`。
12. 视觉核对用的数据桩网关是 scratchpad 里的一次性脚本，不进仓库（会话、提醒未读数、偏好与退订都由它伪造；PAP-142 部署前通知服务的路由在 mini 上还不存在）。

## 12. 实施记录（2026-10-31）

| # | 偏离或补充 | 原因 |
|---|---|---|
| 1 | 通知设置的单选行照画板 `.pt-radio-row` 加了分隔线与 12px 上下留白（`RadioRow` 加 `className`）；隐私页保持原来的 8px、无分隔线 | 画板两页用的是同一个类，隐私页上线时就没照做；本 Issue 不动隐私页（要统一另说） |
| 2 | `email-token.ts` 导出 `TOKEN_PATTERN` | 退订令牌与邮件令牌同形（43 个 base64url 字符），Server Action 的入参校验复用它 |
| 3 | 一键退订地址的 `GET`、`POST` 已用本地数据桩经真实 `next dev` 走通（303 带片段、200 转发）；对网关的真实转发要等 PAP-142 部署 | 网关上还没有通知服务的路由 |
| 4 | 视觉核对：本地数据桩网关 + Playwright，1440×900 与 375×812 共 44 张，与画板 `Notify` / `Unsub` 的渲染逐屏对照 | 第 10 节 AC 第 5 条 |
| 5 | 独立评审（opus）后补：带查询串的请求抛错时只记错误类型，不记报错信息（`callNotification`） | 网关地址配错（如漏写 `http://`）时 fetch 的报错信息是完整地址，令牌就在里面；补了路由与调用层两例测试，先红后绿 |
| 6 | 独立评审后补：`proxy.test.ts` 加两个新专注页与「冷静期账号打开一键退订地址」一例 | 守住第 2.3 节的名单；去掉名单里的两项时这三例都会失败（已实测） |
| 7 | 评审留下的小问题本 Issue 不改：浏览器后退时设置页用缓存里的旧档位（隐私页同样如此，要改两页一起改）；结果屏不移焦点、不播报（四个旧落地页同样如此）；退订后后退看到无效屏；已登录用户在当前用户读到之前短暂看到「登录后去通知设置」 | 都是与现有页面共有的行为，单改这一页会不一致 |
