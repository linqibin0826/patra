# 门户埋点设计

> **Issue**：PAP-173（v0.18 Analytics，Milestone FE Ready）
> **日期**：2026-11-02
> **依据**：release spec [`v0.18-analytics.md`](../release-specs/v0.18-analytics.md) 2.1、决策 A / F / H；[埋点方案](2026-11-01-analytics-tracking-plan-design.md)（下称「埋点方案」）；[采集端点设计](2026-11-01-analytics-collector-design.md)（下称「采集设计」）；[阅读历史设计](2026-11-02-analytics-reading-history-design.md) §2 给本 Issue 的约束
> **契约**：`patra-api/patra-analytics/patra-analytics-api/src/main/resources/analytics/`（JSON Schema 与样例），本 Issue 不改它
> **范围**：只有 `patra-portal/`（外加 CI 变更检测脚本的一行，§8.3）；没有新界面，PAP-174 的展示另起一份 spec

---

## 1. 要做什么

埋点方案定了「记哪些事、长什么样、怎么算」，本 Issue 是它在门户里的落地：

1. **标识**：所有访客第一次打开任何页面就拿到设备 Cookie；会话 Cookie 30 分钟无活动换新（§3）。
2. **服务端事件**：门户服务端在渲染文献详情、检索结果，回传 PDF 与导出文件，收藏成功时各发一条事件，放在响应之后发，不拖慢页面（§4）。
3. **浏览器事件**：页面浏览、卡片曝光、卡片点击、全文停留、复制引用，攒成批发到门户自己的路径（§5、§6）。
4. **第一方转发**：门户的 Route Handler 收下浏览器批次，按服务端会话补上设备、会话、用户、IP、UA，组装成采集信封，经网关转给采集端点（§6.3）。
5. **类型与一致性**：事件的 TypeScript 类型由 JSON Schema 生成并提交；单测检查生成结果与 schema 一致，并用 schema 校验门户造出来的每一种事件（§8）。

埋点失败不抛错、不影响页面、浏览器控制台不报错（§7）。

**不做**：计数、热读、看过还看了、阅读历史页、隐私政策页（PAP-174）；`list_type` 里 `feed_hot`、`related`、`related_topic` 三种列表（它们的列表 PAP-174 才有，到时只需包一层，§5.2）；检索的「检索无结果」之外的任何检索分析；连 mini 的联调与 Playwright 冒烟（等后端部署到 mini）。

## 2. 先讲清几个概念

### 2.1 为什么浏览器不直接打采集端点

浏览器发来的东西谁都能伪造，所以浏览器批次里**没有任何身份字段**（`browser-event` schema 本来就不允许）。身份由门户服务端从 httpOnly Cookie 和会话里取：设备 ID 读设备 Cookie，用户 ID 拿会话令牌问 identity。这样一个人没法把浏览记录记到别人账号上，阅读历史（PAP-169）才可信。这是阅读历史设计 §2 的硬约束，§6.3 用显式字段白名单 + 测试守住。

路径用门户自己的域名、不叫 `/analytics`、`/ingest`：广告拦截器的规则表认这类名字，被拦的访客会整个从「真人会话」里消失（埋点方案 §6.5）。

### 2.2 `after()`：响应发完再做

Next 的 `after(callback)` 把回调排到响应（或流式渲染）结束之后执行。服务端事件都放在它里面发：页面先给读者，事件慢慢发，采集端点慢或挂了都不影响页面。限制：在服务端组件（页面、布局）里，`after` 的回调**不能**再读 `cookies()`、`headers()`，要在渲染时读好、把值传进去；Route Handler 与 Server Action 里没有这个限制。

### 2.3 预取为什么不会多算一次浏览

埋点方案 §6.6 说「带 `Next-Router-Prefetch` 头的请求不发服务端事件」。实测 Next 16.3 把这个头连同 `RSC` 等内部头从 proxy 与 `headers()` 里都剥掉了（`FLIGHT_HEADERS` → `HIDDEN_REQUEST_HEADERS`），门户读不到。但效果一样能达到：**带 `loading.tsx` 的动态路由，预取只渲染到 loading 边界，`page.tsx` 根本不执行**（Next 文档 Prefetching：dynamic route with `loading.js` → layout to first loading boundary）。文献详情页（`/papers/[id]`）与检索页（`/papers`）都有 `loading.tsx`，所以只要事件在 `page.tsx` 或它下面的组件里发，预取就不会触发。门户没有任何 `prefetch={true}` 的链接（那会预取整页）；§10 写一条用例守住「这两个路由保留 `loading.tsx`」。

Server Action 回渲染：Server Action 的请求带 `Next-Action` 头，它不在隐藏列表里。页面里读到它就不发 `article_viewed` / `search_performed`。`router.refresh()`（全文区「重新加载」按钮等）会让页面重新渲染并再发一次 `article_viewed`：看过人数按人长期去重（埋点方案 §6.8），多一条只影响面板的事件级统计，接受。

## 3. 设备与会话

### 3.1 设备 Cookie

- 名字、属性沿用 `lib/auth/session-cookie.ts` 的 `deviceCookie()`：开着 Secure 叫 `__Host-patra_device`，关着叫 `patra_device`；httpOnly、`SameSite=Lax`、`Path=/`、400 天。值是 32 字节随机数的 base64url（43 个字符）。
- **在 `proxy.ts` 里下发**：每个经过 proxy 的页面请求，Cookie 里的值格式对就沿用，不对或没有就生成新的；两种情况都写回一次（顺延 400 天）。登录、注册时的 `ensureDeviceId()` 照旧（它读到的就是 proxy 已经种下的值，所以登录后设备 ID 不变）。
- `/api/` 不经过 proxy（matcher 排除了它）。要发事件的 Route Handler（批次路由、PDF、导出）自己读设备 Cookie，没有就生成并写回（§3.3 的同一个函数）。

### 3.2 会话 Cookie

- 名字：开着 Secure 叫 `__Host-patra_sid`，关着叫 `patra_sid`（埋点方案 §6.2）；httpOnly、`SameSite=Lax`、`Path=/`，**不设过期时间**（浏览器关闭即失效）。
- 值：`<会话 ID>.<最后活动时刻的毫秒数>`，如 `0193e1a3-…-445566778899.1762069330177`。会话 ID 是小写 UUIDv7。
- 规则（纯函数 `rollSession(raw, now)`）：值解析不了、时间戳在未来超过 1 分钟、或 `now − 最后活动 > 30 分钟` → 新会话 ID；否则沿用 ID。两种情况最后活动都改成 `now` 并写回。正好 30 分钟整算「没超过」，沿用。
- 谁调：proxy（每个页面请求，含站内跳转与预取：预取发生在鼠标悬停时，本身也是活动）、批次路由、PDF 与导出路由、收藏的 Server Action。

### 3.3 proxy 怎么改

现在的 `proxy` 有七个返回点（登录页跳走、冷静期跳转、站内跳转直接放行、详情页改写到 404……），每一个都要带上 Cookie，站内跳转也是活动。改法：

1. `proxy(req)` 先算好 `TrackingIds = { deviceId, sessionId, cookies: 要写回的两条 }`（纯函数，输入请求的两个 Cookie 值与 `now`）。
2. 原来的路由逻辑原样搬进一个内部函数，它构造 `NextResponse.next()` / `rewrite()` 时都带上 `request: { headers }`，把 `x-patra-device-id`、`x-patra-session-id` 写进**传给渲染层的请求头**（先删掉浏览器自己带来的同名头，再写；proxy 匹配所有页面，所以页面里读到的这两个头一定是 proxy 写的）。
3. 无论走哪个返回点，最后统一 `response.cookies.set(...)` 写回两条 Cookie。

这样页面渲染、Server Action 都从 `headers()` 拿到本次请求确定的设备与会话 ID，第一次访问（请求里还没有 Cookie）也拿得到。没用 `x-middleware-set-cookie` 让 `cookies()` 读到新值：Next 源码里那段合并还挂着 TODO，请求头是官方文档写明的做法。

Cookie 读写放在 `lib/analytics/server/identity-cookies.ts`：`deviceCookie()` 从 `session-cookie.ts` 挪过来共用（`ensureDeviceId()` 改为调用它），新增 `sessionTrackingCookie()`。

## 4. 服务端事件

### 4.1 发送

`lib/analytics/server/send.ts` 的 `sendEnvelopes(envelopes)`：

- `gatewayFetch("/patra-analytics/events", { method: "POST", json: { events }, timeoutMs: 6_000 })`（采集设计 §4.1、§4.5：门户超时 6 秒）。
- 202 就结束；其他状态码、超时、网络错误一律丢弃这一批，**不重试**（采集设计 §2.3），打一行 `console.warn`（带状态码或错误名，不带事件内容）。永远不抛。
- 只在 `after()` 里调用。

信封由 `buildServerEnvelope(event, ctx)` 组装，`ctx`：

| 字段 | 从哪来 |
|---|---|
| `device_id`、`session_id` | 页面与 Server Action：proxy 写的请求头（§3.3）；Route Handler：§3.1 的函数 |
| `user_id` | 会话令牌问 identity（`lookupSessionByToken`），`authenticated` 时取 `user.userId`，其余（匿名、令牌失效、identity 不可用）为 `null` |
| `client_ip` | `readClientOrigin().ip`；为 `null` 时填 `"0.0.0.0"`（schema 要求必填字符串，§11 第 2 条） |
| `user_agent` | `readClientOrigin().userAgent`；为 `null` 时填 `""`；超过 1,024 个字符截断 |
| `page_path` | 请求路径（不含查询串）；页面里由调用方传入（`/papers/${id}`），Route Handler 取 `request.url` 的路径 |
| `page_type` | 页面由调用方传入；Route Handler 与 Server Action 按 `Referer` 的路径推断（§5.1 的 `pageTypeOf`），没有 `Referer` 为 `other` |
| `referrer_host` | `Referer` 的主机名，与本次请求的主机（先 `X-Forwarded-Host` 再 `Host`）相同、没有或解析不了时为 `null` |
| `event_id` | UUIDv7（`uuid` 包的 `v7()`） |
| `created_at`、`sent_at` | 事件发生时刻与发出时刻，`toISOString()`（UTC、毫秒） |
| `source`、`is_synthetic` | `"server"`、`false` |

页面里 `after` 的回调不能读请求 API（§2.2），所以页面的做法是：渲染时读好令牌、浏览器来源、两个 ID 头、`Referer` 与 `Next-Action`，`after` 里再调 `lookupSessionByToken(token, { client })`。代价：登录用户每打开一篇文献、每做一次检索，门户多问一次 identity（在响应之后，读者感觉不到）；匿名访客没有令牌，不问。不复用 `cache()` 版的 `lookupSession`：它在 `after` 里执行时还会去读 Cookie。

### 4.2 五个事件各在哪发

| 事件 | 位置 | 时机与属性 |
|---|---|---|
| `article_viewed` | `app/papers/[id]/page.tsx` | 文献存在（`notFound()` 之后）、不是 Server Action 回渲染。`{ article_id }` |
| `search_performed` | `components/portal/papers/PaperResults.tsx` | 拿到结果后（含空结果，`result_count = 0`）；无任何条件的浏览态（没有检索词、没有筛选、第 1 页、默认排序）不发；Server Action 回渲染不发。精确定位命中 1 条直跳详情时，页面顶层在 `redirect()` 之前发一条（`result_count = 1`），`PaperResults` 不会再渲染，不会重复 |
| `pdf_downloaded` | `app/api/papers/[id]/pdf/route.ts` | 网关回 200、开始回传文件时（304、404、502 不发）。`{ article_id }` |
| `export_completed` | `app/api/publications/export/route.ts` | 网关回 200、开始回传时。`{ format, scope, article_count: ids.length }` |
| `article_collected` | `lib/interaction/library-actions.ts` 的 `collectPublication` | 收藏写入成功且 `created = true`（本来就在库里的再点一次不算，取消收藏不发）。「已从文献库移除」提示里点「撤销」触发的重新收藏也不算（是恢复，不是新收藏）：`collectPublication(key, { restore: true })`，`SavePopover` 与 `LibraryView` 的两处撤销传它。`{ article_id }` |

`search_performed` 的属性由纯函数 `searchPerformedProperties(query, searchId, total)` 从门户的 `PaperSearchQuery` 映射：

- `mode`：`isExactLookup` 时按是 PMID 还是 DOI 取 `exact_pmid` / `exact_doi`，否则 `keyword`；
- `query`：普通检索是检索词去首尾空白后截到 200 个字符，只用筛选没写检索词时为 `""`；精确定位时是 PMID 或 DOI 本身（埋点方案 §3.3、契约样例 `collect-envelope--search-exact-pmid.json`）；
- `filters`：只放用到的键（`author`、`year_from`、`year_to`、`type`、`evidence`、`lang`、`venue`，`oa` 只在为真时出现），数组去重、截到 20 项，字符串按 schema 的长度截；
- `sort`：门户的 `PaperSortId`；`page`、`result_count` 照搬；
- `search_id`：UUIDv7，同一个值作为这一页结果列表的 `list_id` 传给客户端（§5.2）。

**导出的 `scope`**：发导出请求的两处都在请求体里加 `scope`，Route Handler 的 zod 校验加这个枚举（必填）：

- `ExportDialog`（文献库、公开收藏夹）按 `DialogScope` 映射：`items` → `items`；`library` 且 `view = all` → `library_all`；`library` 且 `view = unfiled` → `library_unfiled`；`collection`（自己的收藏夹）→ `collection`；公开收藏夹 → `public_collection`。
- 文献详情页的分享面板 `SharePanel`（单篇导出）→ `items`。

## 5. 浏览器事件

### 5.1 页面浏览 `page_viewed`

- 根布局里挂一个客户端组件 `<AnalyticsRoot />`（放在 `<Suspense fallback={null}>` 里），用 `usePathname()` 监听：首次加载和每次**路径**变化发一条。只改查询串（翻页、改筛选）不算新页面：检索由 `search_performed` 记，`page_path` 本来也不含查询串。
- 页面不可见（后台打开的标签页）时先不发，等第一次变为可见再发；可见之前又跳走了就不发。
- `page_type` 由纯函数 `pageTypeOf(pathname)` 按表映射：`/` → `home`；`/papers` → `search`；`/papers/:id` → `article`；`/journals`、`/journals/:id` → `journal`；`/library/**` → `library`；`/collections/:id` → `collection`；`/u/**` → `profile`；`/settings/**`、`/account/**` → `settings`；`/notifications/**` → `notifications`；`/posts/:id` → `post`；`/history` → `history`；`/privacy` → `privacy`；`/login`、`/register`、`/forgot-password`、`/reset-password`、`/verify-email`、`/confirm-email-change`、`/revert-email-change` → `auth`；其余 → `other`。`history`、`privacy` 两个页面 PAP-174 才有，映射先写上。

### 5.2 列表、曝光与点击

文献卡片（`PaperCard`、`PaperListItem`）保持服务端组件不动，外面包两层客户端组件：

- `<TrackedList listType listId? >`：一个列表实例。`listId` 不传时在首次挂载时生成 UUIDv7（`useState` 的初始化函数，StrictMode 的重复挂载不会换值）；检索结果传 `search_id`。它在 context 里提供 `list_type`、`list_id` 与这个实例的「已曝光」集合。
- `<TrackedArticle articleId position>`：包一张卡片的 `div`。负责它的曝光判定与点击。

**曝光**（埋点方案 §6.4）：

- 判定写成与 DOM 无关的状态机 `createImpressionTimer({ onImpress, now, setTimer })`，喂三种输入：可见比例变化、标签页可见性变化、计时器到点。规则：比例 ≥ 0.5 **且**标签页在前台时开始计时，连续满 1,000 ms 发一次；中途比例 < 0.5 或切到后台就清零，下次满足时重新计时。
- 组件里用一个 `IntersectionObserver`（`threshold: [0, 0.5]`）喂比例，`visibilitychange` 喂可见性。发出前查列表的「已曝光」集合，有就不发、没有就记上：同一列表实例里同一篇只算一次；两个列表各算各的。
- 浏览器没有 `IntersectionObserver` 时不做曝光（不报错）。

**点击**：`TrackedArticle` 用 `onClickCapture` 看点击目标所在的最近一个链接，`href` 指向 `/papers/{这篇的 ID}` 时发 `article_clicked`（刊名链接、收藏按钮不算）。在事件处理里同步入队，然后让导航照常进行；站内跳转不卸载页面，队列还在，到时一起发。

**位置**：从 1 开始、跨页连续。检索结果 `(page − 1) × pageSize + i + 1`；首页文献流就是现有的 `index`；文献库与公开收藏夹按各自的分页同样算。

**本版包的列表**：

| 列表 | 组件 | `list_type` |
|---|---|---|
| 首页文献流「最新」「高被引」 | `explore-feed/index.tsx` | `feed_latest`、`feed_cited`（`FeedTab` 是 `recent` / `cited`） |
| 检索结果 | `PaperResults` | `search`，`list_id = search_id` |
| 公开收藏夹 | `PublicCollectionView` | `collection` |
| 我的文献库（全部、未归类、自己的收藏夹） | `LibraryItem` 所在列表 | `library` |

`feed_hot`、`related`、`related_topic` 的列表由 PAP-174 建，届时直接包 `TrackedList` / `TrackedArticle`。

### 5.3 全文停留 `fulltext_read`

- 只包 `FullTextSection` 里真正渲染出正文的那个分支（`rendered.content`，即 `FullTextBody` 渲染出的正文）；PDF、全文不可用这类说明面板不包，不计时。
- 判定（埋点方案 §6.3）：正文在视口里的可见高度 ≥ 视口高度的 50%，或正文本身矮于半个视口且整段可见；并且标签页在前台。
- 累计写成状态机 `createDwellTracker({ thresholdMs: 10_000, onReach, now, setTimer })`：「在读」时累计，不在读时暂停，累计到 10 秒发一次 `{ article_id, visible_seconds }`（取整秒，≥ 10），同一个页面实例只发一次；离开时不足 10 秒不发。
- 「在读」由纯函数 `isReadingVisible(rect, viewportHeight)` 算，组件在滚动、窗口大小变化（`requestAnimationFrame` 节流）、`visibilitychange` 时读一次 `getBoundingClientRect()` 喂给状态机。不用 `intersectionRatio`：长文超过两屏时「正文 50% 可见」永远达不到。

### 5.4 复制引用 `citation_copied`

`CopyButton` 加可选的 `onCopied` 回调，`useCopy().copy()` 返回 `copied` 时调用（降级成「已选中，按 ⌘C」时不算，因为不知道用户最后有没有复制）。复制引用有两个入口，都传回调，打开、切换格式都不发：

- `CiteDialog`（文献库、公开收藏夹）：`{ article_ids: 这次预览用的 ID（去重，最多 100）, style }`；
- 文献详情页分享面板 `SharePanel` 的「复制引用」：`{ article_ids: [这篇的 ID], style }`。分享面板里的「复制链接」不是引用，不发。

## 6. 上报

### 6.1 队列

`lib/analytics/client/queue.ts`，整个页面一个实例：

- `track(name, properties)`：补上 `event_id`（UUIDv7）、`schema_version`（生成的版本表，§8）、`created_at`、`page_path`（`location.pathname`）、`page_type`（`pageTypeOf`），放进缓冲。签名按生成的类型约束：事件名与属性对不上编译不过。
- **何时发**：缓冲里第一条事件进来后 5 秒，或攒满 20 条，用 `fetch(路径, { method: "POST", keepalive: true })` 发；`visibilitychange` 变为 `hidden` 时把剩下的全部用 `navigator.sendBeacon` 发出，`pagehide` 兜底（有的浏览器关标签页时不触发 `visibilitychange`）。
- **分批**：每批最多 50 条、序列化后不超过 48 KiB（埋点方案 §4.1），按顺序切。一条事件本身就超过 48 KiB 的直接丢（正常用法碰不到）。
- **失败**：`fetch` 抛错或非 2xx 时丢弃，不重试；`sendBeacon` 返回 `false` 时丢弃。所有分支都 `catch`，不往控制台打错误。
- 只在浏览器里运行；服务端渲染时 `track` 什么都不做。

### 6.2 第一方路径

`POST /api/reading-signals`（`app/api/reading-signals/route.ts`）。名字只是不被拦截器认出，没有别的含义。

### 6.3 Route Handler 做什么

1. `isSameOrigin` 不过 → 403；请求体超过 64 KiB → 413；不是 JSON 或不符合 `browser-batch` 外壳（`sent_at` 时间格式、`events` 1～50 个对象）→ 400。外壳用 zod 校验，**不逐条校验事件**（埋点方案 §5：坏事件只让它自己进 dlq）。
2. 读设备 Cookie、滚动会话 Cookie（§3.1、§3.2，写回）。
3. 立刻回 204，转发放进 `after()`：问 identity 拿 `user_id`，把每条事件**按白名单取字段**组装成采集信封——只取 `event_id`、`event_name`、`schema_version`、`created_at`、`page_path`、`page_type`、`properties`，加上 `source: "client"`、`sent_at`（批次的）、`device_id`、`session_id`、`user_id`、`referrer_host: null`、`client_ip`、`user_agent`、`is_synthetic: false`。浏览器给的其他任何字段（`user_id`、`device_id`、`is_synthetic`……）一律不带过去。
4. 发给采集端点（§4.1）。

`referrer_host` 对浏览器事件恒为 `null`：`browser-event` 不让浏览器带来源，批次请求自己的 `Referer` 是本站（§11 第 3 条）。

## 7. 失败与降级

| 情况 | 结果 |
|---|---|
| `patra-analytics` 停了、Kafka 503、网关超时 | 服务端事件在 `after` 里丢弃并打一行警告；页面、PDF、导出、收藏照常。批次路由已经回过 204，浏览器什么都不知道 |
| identity 不可用 | `user_id` 记 `null`，事件照发 |
| 浏览器不支持 `IntersectionObserver` / `sendBeacon` | 不做曝光 / 用 `fetch keepalive` 代替 |
| 门户自己的批次路由出错（不应发生） | 浏览器端 `catch` 吞掉，不打日志 |
| proxy 里生成 ID 抛错（不应发生） | 不写 Cookie、不写请求头，页面照常；该请求的服务端事件因为没有 ID 而不发 |

## 8. 类型与一致性

### 8.1 生成什么

脚本 `scripts/generate-analytics-types.mjs`（`pnpm gen:analytics`），读 `../patra-api/patra-analytics/patra-analytics-api/src/main/resources/analytics/schema/`，写 `src/lib/analytics/generated/events.ts`（提交进仓库：门户的 Docker 构建上下文只有 `patra-portal/`）：

- 用 `json-schema-to-typescript`（开 `ignoreMinAndMaxItems`，否则 `maxItems: 20` 会展开成 21 个元组）把 `events/*.schema.json` 逐个生成属性接口（`ArticleImpressed` 等），以及 `defs` 里的 `PageType`、`ListType` 联合类型。
- 脚本自己从 `event-catalog.schema.json` 读出每个事件名属于 `server_event` 还是 `client_event`，从各事件 schema 的 `x-patra-version` 读出版本，产出 `ClientEventProperties` / `ServerEventProperties` 两个「事件名 → 属性类型」映射接口与 `SCHEMA_VERSIONS` 常量表。
- 整条事件的 `oneOf` 不交给生成器：试过，它生成的交叉类型里 `properties: {}` 与各分支叠在一起，没法用；判别联合用上面的映射在手写的 `types.ts` 里拼（`{ [K in keyof ClientEventProperties]: { event_name: K; properties: ClientEventProperties[K] } }[…]`）。

### 8.2 一致性检查

`tests/lib/analytics/generated-types.test.ts`：在测试里用同一个脚本的生成函数重新生成一遍，与提交的文件逐字比较；不一致就失败，提示跑 `pnpm gen:analytics`。

类型一致只是弱保证，另外用 `ajv`（2020-12 草案，`x-patra-version` 注册为注解关键字）做两件事：

- 跑 `examples/` 下全部 `browser-batch--*`、`browser-event--*` 样例：合格的过、不合格的不过（证明门户用的校验器与后端对同一份 schema 的理解一致）。
- 门户自己造的东西按 schema 校验：队列造出的每一种浏览器事件按 `browser-event`；批次路由与 `buildServerEnvelope` 造出的每一种信封按 `collect-envelope`（含 `client_ip` 为 `null` 回退、`user_agent` 超长截断、检索的全部筛选键）。

`ajv` 只是开发依赖，运行时不用它（门户不逐条校验事件，§6.3）。

### 8.3 CI 怎么跑到它

上面的测试在 `pnpm test` 里，CI 的 portal job 本来就跑。但 schema 在 `patra-api/` 下，只改 schema 时 `patra-infra/cd/detect-changes.sh` 不认为门户变了，portal job 不跑。所以给它加一条规则：`patra-api/patra-analytics/patra-analytics-api/src/main/resources/analytics/schema/` 下有变动时 `portal_changed = true`，`detect-changes.test.sh` 加对应用例。

## 9. 文件布局

```text
patra-portal/
├── scripts/generate-analytics-types.mjs          # §8.1
├── src/lib/analytics/
│   ├── generated/events.ts                       # 生成，勿手改
│   ├── types.ts                                  # 判别联合、信封类型
│   ├── page-type.ts                              # pageTypeOf
│   ├── ids.ts                                    # newId()（UUIDv7）、newDeviceId()
│   ├── client/
│   │   ├── queue.ts                              # §6.1
│   │   ├── impression.ts                         # createImpressionTimer
│   │   ├── dwell.ts                              # createDwellTracker、isReadingVisible
│   │   ├── TrackedList.tsx、TrackedArticle.tsx    # §5.2
│   │   ├── FulltextReadTracker.tsx               # §5.3
│   │   └── AnalyticsRoot.tsx                     # §5.1、队列的页面生命周期
│   └── server/
│       ├── identity-cookies.ts                   # §3：设备 / 会话 Cookie、rollSession
│       ├── context.ts                            # 页面 / Route Handler / Action 读上下文
│       ├── envelope.ts                           # buildServerEnvelope、browser → collect
│       ├── search-properties.ts                  # searchPerformedProperties
│       └── send.ts                               # sendEnvelopes
└── src/app/api/reading-signals/route.ts          # §6.2
```

新增依赖：`uuid`（运行时，UUIDv7）；`json-schema-to-typescript`、`ajv`（开发）。

## 10. 测试

TDD，一条用例一个红绿。jsdom 没有 `IntersectionObserver`，所以判定逻辑都是不碰 DOM 的状态机，用假时钟测；组件只做薄接线，测接线本身。

| 对象 | 用例 |
|---|---|
| `rollSession` | 没有 Cookie、格式坏、时间戳在未来 → 新 ID；29 分 59 秒、正好 30 分 → 沿用；30 分 1 毫秒 → 新 ID；都把最后活动改成 now |
| proxy | 从没来过的访客打开首页：响应写了设备与会话 Cookie，传给渲染层的请求头带着同样的两个 ID；已有合法设备 Cookie 时值不变；浏览器伪造 `x-patra-device-id` 被覆盖；七个返回点（含站内跳转放行、详情页改写、冷静期跳转、登录页跳走）都带 Cookie |
| 登录 | 登录后设备 ID 与 proxy 种下的一样（`ensureDeviceId` 沿用） |
| `createImpressionTimer` | 60% 可见 0.8 秒后滚走 → 不发；再回来连续 1.2 秒 → 发一次；之后再滚出滚入 → 不再发（集合去重）；不足 50% → 不发；可见但标签页在后台 → 不发，回到前台重新计 1 秒；两个列表实例各发一次 |
| `createDwellTracker` / `isReadingVisible` | 正文占视口 70% 停 6 秒 → 切走 1 分钟 → 回来 4 秒：此刻发出、`visible_seconds = 10`；只露出视口底部 20% 不计时；矮于半个视口且整段可见计时；发过一次后不再发；不足 10 秒卸载不发 |
| `pageTypeOf` | 表驱动覆盖 §5.1 每一行 |
| 队列 | 5 秒到点发；满 20 条立刻发；`hidden` 时用 `sendBeacon` 发剩余；超过 50 条或 48 KiB 切成多批；`fetch` 抛错 / 500 不抛、不打 `console.error`；服务端环境 `track` 不做事 |
| `TrackedArticle` | 点标题链接入队 `article_clicked` 带 `list_type`、`list_id`、`position`；点刊名链接不入队 |
| `CopyButton` / `CiteDialog` / `SharePanel` | 复制成功调 `onCopied` 并入队 `citation_copied`（多篇、单篇各一条）；降级到「已选中」不调；分享面板复制链接不发；分享面板单篇导出带 `scope: "items"` |
| 批次路由 | 403 / 413 / 400 / 204；批次里塞 `user_id`、`device_id`、`is_synthetic: true` 的事件，转出去的信封里 `user_id` 是会话里的值（匿名为 `null`）、`device_id` 是 Cookie 里的、`is_synthetic` 为 `false`；采集端点 503 / 超时时仍是 204、不抛 |
| `buildServerEnvelope` 与五个事件 | 每个事件的信封按 `collect-envelope` 校验通过；登录时带 `user_id`、匿名为 `null`；信封里没有 IP 之外的多余字段（`client_ip` 是 schema 的必填字段，给采集端点用完即丢） |
| `article_viewed` | 文献存在时 `after` 里发一条；不存在不发；带 `Next-Action` 头不发 |
| `search_performed` | 浏览态不发；检索词、各筛选、精确定位、空结果的属性映射；`list_id` 与 `search_id` 相同 |
| PDF / 导出 / 收藏 | 200 时发、304 / 4xx / 5xx 不发；导出的五种 `scope` 映射、缺 `scope` 400；收藏 `created = false` 不发、`restore: true` 不发 |
| 路由保护 | `app/papers/[id]/loading.tsx`、`app/papers/(list)/loading.tsx` 存在（§2.3 依赖它们挡预取） |
| 一致性 | §8.2 两类 |

本地验证：`pnpm lint`、`pnpm typecheck`、`pnpm test -- --maxWorkers=2` 全绿。连 mini 网关的联调（AC「采集端点收到 `Article Viewed`」「停掉 analytics 照常」）等后端部署后用 Playwright 冒烟跑：打开一篇文献，在采集端点的 Kafka 里看到 `article_viewed` 带 `device_id`，登录时带 `user_id`、没有 IP 字段（富化后）；停掉 `patra-analytics` 后首页、文献页、检索页照常，浏览器控制台没有错误。冒烟用例本 Issue 写好，跑在部署之后。

## 11. 给后端的契约备注

1. **预取头读不到**（埋点方案 §6.6）：Next 16 把 `Next-Router-Prefetch` 从 proxy 与 `headers()` 里剥掉了。门户靠 `loading.tsx` 边界保证预取不渲染页面（§2.3），效果相同；建议埋点方案 §6.6 改成「预取不渲染页面，因此不发」。
2. **`client_ip` 可能取不到**：门户取不到合法的单个 IP 时填 `"0.0.0.0"`（保留地址，ip2region 查出来地区为 `null`）；`user_agent` 取不到填 `""`。请采集端点确认这两个值不会让 ip2region / Yauaa 报错。
3. **浏览器事件没有外部来源**：`browser-event` 不许带 `referrer_host`，批次请求的 `Referer` 是本站，所以 client 事件的 `referrer_host` 恒为 `null`。外部来源只出现在服务端事件里（直接打开文献页时 `article_viewed` 带上）。如果面板要「访问来源」，要么在 `page_viewed` 的属性里加 `referrer_host`（ADDITION），要么面板按会话的第一条服务端事件取。

## 12. 我的决定（等你审）

1. **会话 Cookie 值是「会话 ID.最后活动毫秒数」**，不签名（改它只能改自己的会话归属）；proxy 与三类服务端入口都滚动它，预取也算活动（§3.2）。
2. **proxy 用请求头把设备 / 会话 ID 传给渲染层**，覆盖浏览器带来的同名头（§3.3）。
3. **页面里的服务端事件在 `after` 里再问 identity 拿 `user_id`**：登录用户每次打开文献、检索多一次 identity 调用（§4.1）。
4. **只改查询串不算新的 `page_viewed`**（§5.1）。
5. **`article_collected` 只在 `created = true` 时发，撤销移除触发的重新收藏不发**（§4.2）。
6. **精确定位直跳详情时也发 `search_performed`**，`result_count = 1`（§4.2）。
7. **复制引用降级到「已选中」时不发**（§5.4）。
8. **路由 `/api/reading-signals`**；**攒 5 秒或 20 条一发**；**失败一律丢弃不重试**（§6）。
9. **ID 用 UUIDv7**（`uuid` 包）：事件、会话、列表、检索都是（§3.2、§4.1）。
10. **类型生成只生成属性类型与映射表，判别联合手写一层**（§8.1）；**一致性检查放在 vitest 里，外加 ajv 按 schema 校验门户造出的每一种事件**（§8.2）。
11. **改 `detect-changes.sh`**：schema 变动也触发 portal job（§8.3）。
12. **Route Handler 事件的 `page_type` 按 `Referer` 推断、`page_path` 取请求路径**（§4.1）。
