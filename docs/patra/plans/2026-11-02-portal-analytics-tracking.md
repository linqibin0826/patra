# 门户埋点 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 门户发出埋点方案定义的 10 个事件：服务端 5 个在响应之后发，浏览器 5 个攒批经第一方路径由门户补身份后转给采集端点。

**Architecture:** proxy 下发设备 / 会话 Cookie 并用请求头把两个 ID 传给渲染层；`lib/analytics/server` 组装采集信封、在 `after()` 里发；`lib/analytics/client` 是不碰 DOM 的状态机（曝光、全文停留）+ 一个页面级队列；事件类型由 JSON Schema 生成，单测用 ajv 按 schema 校验门户造出的每一种事件。

**Tech Stack:** Next 16.3 App Router（`after`、proxy）、React 19、TypeScript strict、Vitest + Testing Library、`uuid`（v7）、`json-schema-to-typescript`、`ajv`（2020）。

**Spec:** `docs/patra/specs/2026-11-02-portal-analytics-tracking-design.md`

## Global Constraints

- 不改 `patra-api/`；schema 目录 `patra-api/patra-analytics/patra-analytics-api/src/main/resources/analytics/` 只读。
- 每个任务不单独提交：用户本地验收后统一提交（编排会话要求）。任务的「提交」步骤改为「跑该任务的测试 + `pnpm lint`」。
- vitest 一律 `pnpm test --maxWorkers=2 <路径>`；全量也带 `--maxWorkers=2`。
- 采集端点 `POST /patra-analytics/events`，请求体 `{ events: [...] }`，门户超时 `6_000` ms，非 202 一律丢弃不重试。
- 设备 Cookie：`__Host-patra_device` / `patra_device`，400 天，值 `^[A-Za-z0-9_-]{43}$`。会话 Cookie：`__Host-patra_sid` / `patra_sid`，无 `maxAge`，值 `<uuidv7>.<毫秒>`，30 分钟（`1_800_000` ms）无活动换新，正好 30 分钟沿用。
- 传给渲染层的请求头：`x-patra-device-id`、`x-patra-session-id`。
- 浏览器批次：每批 ≤ 50 条、序列化后 ≤ 48 KiB（`49_152` 字节）；第一条入队后 `5_000` ms 或满 `20` 条发；路由 `/api/reading-signals`，请求体上限 64 KiB（`65_536`）。
- 曝光：比例 ≥ 0.5 且前台、连续 `1_000` ms；全文：在读累计 `10_000` ms，`visible_seconds` 取整秒。
- `client_ip` 取不到填 `"0.0.0.0"`，`user_agent` 取不到填 `""`、超过 1024 截断；浏览器事件 `referrer_host` 恒为 `null`；`is_synthetic` 恒为 `false`。
- 注释、文案中文；标识符英文；Biome 是唯一格式权威；禁止 `any` 与 `@ts-ignore`。

## Review Focus

1. **站内从文献 A 跳到文献 B**：全文计时与已曝光集合不能串到下一篇——`FulltextReadTracker` 以 `articleId` 为 `key`（Task 11 用例「换文献重新计时」）。
2. **事件入队后、发出前跳了页**：`page_path` / `page_type` 必须是入队时的页面，不是发出时的（Task 9 用例）。
3. **超长路径**：`page_path` 超过 512 字符时截断到 512，服务端与浏览器两边都截（Task 5、Task 9 用例），否则整条进 dlq。
4. **浏览器禁用 Cookie**：每个请求都是新设备、新会话，事件照发、页面不报错（Task 4 用例：没有 Cookie 的请求照样带请求头）。
5. **后台标签页打开文献**：`page_viewed` 等到第一次可见才发；可见之前离开就不发（Task 12 用例）。

---

### Task 1: 依赖、类型生成与一致性检查

**Files:**
- Modify: `patra-portal/package.json`（依赖 `uuid`；开发依赖 `json-schema-to-typescript`、`ajv`；脚本 `"gen:analytics": "node scripts/generate-analytics-types.mjs"`）
- Create: `patra-portal/scripts/generate-analytics-types.mjs`
- Create: `patra-portal/src/lib/analytics/generated/events.ts`（生成）
- Create: `patra-portal/src/lib/analytics/types.ts`
- Create: `patra-portal/tests/support/analytics-schema.ts`（ajv 实例）
- Test: `patra-portal/tests/lib/analytics/generated-types.test.ts`、`patra-portal/tests/lib/analytics/schema-examples.test.ts`
- Modify: `patra-infra/cd/detect-changes.sh`、`patra-infra/cd/detect-changes.test.sh`

**Interfaces:**
- Produces:
  - 脚本导出 `generateAnalyticsTypes(schemaDir: string): Promise<string>`（返回文件全文），直接运行时写文件。
  - `generated/events.ts`：各事件属性接口（`ArticleViewed`、`SearchPerformed`、`PdfDownloaded`、`ExportCompleted`、`ArticleCollected`、`PageViewed`、`ArticleImpressed`、`ArticleClicked`、`FulltextRead`、`CitationCopied`）、`PageType`、`ListType`、`interface ServerEventProperties { article_viewed: ArticleViewed; … }`、`interface ClientEventProperties { page_viewed: PageViewed; … }`、`const SCHEMA_VERSIONS: { readonly [K in ServerEventName | ClientEventName]: string }`。
  - `types.ts`：`type ServerEventName = keyof ServerEventProperties`、`type ClientEventName = keyof ClientEventProperties`、`type ClientEvent`（判别联合：`event_id, event_name, schema_version, created_at, page_path, page_type, properties`）、`type ServerEventInput`（`{ [K in ServerEventName]: { name: K; properties: ServerEventProperties[K] } }[ServerEventName]`）、`interface CollectEnvelope`（spec §4.2 全部 16 个字段）。
  - `tests/support/analytics-schema.ts`：`validateAgainst(schemaFile: string, value: unknown): string[]`（返回错误列表，空即通过）。

- [ ] **Step 1: 装依赖**：`pnpm add uuid && pnpm add -D json-schema-to-typescript ajv`（`--prefer-offline`）。
- [ ] **Step 2: 写失败测试 `generated-types.test.ts`**：`expect(await generateAnalyticsTypes(SCHEMA_DIR)).toBe(readFileSync(GENERATED, "utf8"))`，失败消息提示跑 `pnpm gen:analytics`。另一条：`SCHEMA_VERSIONS.article_viewed === "1-0-0"`，`ClientEventProperties` 恰好 5 个键（用 `Object.keys(SCHEMA_VERSIONS)` 的 10 个与目录比对）。
- [ ] **Step 3: 写失败测试 `schema-examples.test.ts`**：遍历 `examples/valid|invalid` 里 `browser-batch--*`、`browser-event--*`、`collect-envelope--*`，按文件名前缀选 schema；valid 全部 `validateAgainst(...)` 为空，invalid 全部非空。
- [ ] **Step 4: 跑两测试确认失败**：`pnpm test --maxWorkers=2 tests/lib/analytics` → FAIL（模块不存在）。
- [ ] **Step 5: 实现脚本**：`compile`（`json-schema-to-typescript`）逐个编译 `events/*.schema.json`，选项 `{ bannerComment: "", ignoreMinAndMaxItems: true, additionalProperties: false, cwd: <events 目录> }`；`PageType`、`ListType` 从 `defs.schema.json` 的 `enum` 直接拼联合；从 `event-catalog.schema.json` 的 `$defs.server_event.oneOf[*].properties.event_name.const`（client 同理）得事件名，读各事件 schema 的 `x-patra-version` 拼 `SCHEMA_VERSIONS`；结果过 Biome 格式（`biome format --stdin-file-path`）后返回，保证与提交文件逐字一致。文件头注释「由 scripts/generate-analytics-types.mjs 生成，勿手改」。
- [ ] **Step 6: 实现 `tests/support/analytics-schema.ts`**：`new Ajv2020({ strict: false, allErrors: true })`，`addKeyword("x-patra-version")`，`addSchema` 载入 schema 目录（含 `events/`）全部文件，按 `$id` 取。
- [ ] **Step 7: 跑 `pnpm gen:analytics`，写 `types.ts`，跑测试** → PASS。
- [ ] **Step 8: detect-changes**：`detect-changes.sh` 里 schema 目录（上面那条路径下的 `schema/`）有变动时 `portal_changed=true`；`detect-changes.test.sh` 加一条「只改 schema 文件 → portal_changed 为 true」；`bash patra-infra/cd/detect-changes.test.sh` 全绿。
- [ ] **Step 9: `pnpm lint && pnpm typecheck`** 通过（`biome.json` 若检查生成文件报错，把 `src/lib/analytics/generated` 加入忽略并说明原因）。

### Task 2: ID 与页面类型

**Files:**
- Create: `patra-portal/src/lib/analytics/ids.ts`、`patra-portal/src/lib/analytics/page-type.ts`
- Test: `patra-portal/tests/lib/analytics/ids.test.ts`、`patra-portal/tests/lib/analytics/page-type.test.ts`

**Interfaces:**
- Produces: `newId(): string`（小写 UUIDv7，`uuid` 的 `v7()`）；`newDeviceId(): string`（32 字节随机 base64url，43 字符，Web Crypto，浏览器与 Node 通用）；`pageTypeOf(pathname: string): PageType`；`truncatePath(path: string): string`（截到 512）。

- [ ] **Step 1: 失败测试**：`newId()` 匹配 `/^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/`、连续两次后者字典序不小于前者；`newDeviceId()` 匹配 `DEVICE_ID_PATTERN`；`pageTypeOf` 表驱动覆盖 spec §5.1 每一行（含 `/papers/` 尾斜杠、`/library/unfiled`、`/u/foo/collections`、`/settings/privacy`、`/account/deletion-pending` → `settings`、`/missing/paper` → `other`、`/history`、`/privacy`、七个 auth 路径）；`truncatePath("/" + "a".repeat(600)).length === 512`。
- [ ] **Step 2: 跑 → FAIL；实现；跑 → PASS。**

### Task 3: 设备与会话 Cookie

**Files:**
- Create: `patra-portal/src/lib/analytics/server/identity-cookies.ts`
- Modify: `patra-portal/src/lib/auth/session-cookie.ts`（`deviceCookie()` 挪走，`ensureDeviceId()` 改用新模块）
- Test: `patra-portal/tests/lib/analytics/identity-cookies.test.ts`；现有 `tests/lib/auth/*session-cookie*` 保持绿

**Interfaces:**
- Produces:
  - `deviceCookie(): CookieSpec`、`sessionTrackingCookie(): CookieSpec`（`{ name, httpOnly: true, sameSite: "lax", path: "/", secure }`；设备带 `maxAge: 400 天`，会话没有 `maxAge`）。
  - `rollSession(raw: string | undefined, now: number): { sessionId: string; value: string }`。
  - `resolveTrackingIds(read: (name: string) => string | undefined, now: number): TrackingIds`，`interface TrackingIds { deviceId: string; sessionId: string; cookies: { name: string; value: string; options: CookieSpec }[] }`（两条都写回）。
  - `ensureTrackingIds(): Promise<TrackingIds>`：Route Handler / Server Action 用，经 `cookies()` 读并写回。
- [ ] **Step 1: 失败测试 `rollSession`**：`undefined`、`"garbage"`、`"<uuid>.abc"`、时间戳比 now 晚 60_001 ms → 新 ID；距上次 `1_799_999`、`1_800_000` → 沿用；`1_800_001` → 新 ID；所有情况 `value` 以 `.${now}` 结尾。
- [ ] **Step 2: 失败测试 `resolveTrackingIds`**：合法设备值沿用、坏值换新；两条 Cookie 的名字随 `PATRA_SESSION_COOKIE_SECURE` 切换（`"false"` → `patra_device` / `patra_sid`，不设 → `__Host-` 前缀）。
- [ ] **Step 3: 失败测试 `ensureTrackingIds`**（`vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"))`）：没有 Cookie → 写出两条 Set-Cookie，设备的带 `Max-Age=34560000`，会话的没有 `Max-Age`。
- [ ] **Step 4: 跑 → FAIL；实现；跑本文件与 `tests/lib/auth` → PASS。**

### Task 4: proxy 下发 Cookie 并传 ID 给渲染层

**Files:**
- Modify: `patra-portal/src/proxy.ts`
- Test: `patra-portal/tests/proxy.test.ts`（新增 describe）

**Interfaces:**
- Consumes: `resolveTrackingIds`（Task 3）。
- Produces: 渲染层与 Server Action 可从 `headers()` 读 `x-patra-device-id`、`x-patra-session-id`；常量 `TRACKING_HEADERS = { device: "x-patra-device-id", session: "x-patra-session-id" }` 从 `identity-cookies.ts` 导出。

- [ ] **Step 1: 失败测试**：断言请求头用 `res.headers.get("x-middleware-request-x-patra-device-id")`（Next 把 `request.headers` 编码成这个前缀），Cookie 用 `res.cookies.get(...)`：
  - 首页没有 Cookie：写出设备与会话 Cookie，请求头里的两个 ID 与 Cookie 值（会话取点号前）一致；
  - 已有合法设备 Cookie：值不变；
  - 请求自带 `x-patra-device-id: forged` 被覆盖；
  - `sec-fetch-dest: empty` 放行、`/papers/abc` 改写到 404、冷静期 307、登录页 307 四种返回都带两条 Cookie；
  - 生成 ID 抛错（`vi.spyOn(crypto, "getRandomValues")` 抛）时页面照常放行、不写 Cookie。
- [ ] **Step 2: 跑 → FAIL。**
- [ ] **Step 3: 实现**：原 `proxy` 改名 `route(req, forward: Headers)`，其中每个 `NextResponse.next()` / `rewrite()` 带 `{ request: { headers: forward } }`；新 `proxy(req)`：`try` 算 `TrackingIds`，复制 `req.headers`、删再写两个头 → 调 `route` → 对结果统一 `res.cookies.set(...)`；`catch` 时用原请求头调 `route`、不写 Cookie。
- [ ] **Step 4: 跑 `tests/proxy.test.ts` → PASS**（原有用例全绿）。

### Task 5: 服务端信封与发送

**Files:**
- Create: `patra-portal/src/lib/analytics/server/context.ts`、`envelope.ts`、`send.ts`
- Test: `patra-portal/tests/lib/analytics/envelope.test.ts`、`send.test.ts`

**Interfaces:**
- Consumes: `newId`、`truncatePath`、`pageTypeOf`（Task 2）、`ensureTrackingIds`、`TRACKING_HEADERS`（Task 3、4）、`lookupSessionByToken`、`readClientOrigin` / `clientOriginFromHeaders`。
- Produces:
  - `interface RequestFacts { deviceId: string; sessionId: string; token: string | null; client: ClientOrigin; referrerHost: string | null; isActionRequest: boolean }`。
  - `readPageFacts(): Promise<RequestFacts | null>`（页面：从 proxy 的请求头读 ID，缺任一返回 `null`）；`readHandlerFacts(request?: Request): Promise<RequestFacts>`（Route Handler / Server Action：`ensureTrackingIds()`）。`referrerHost` 规则见 spec §4.1。
  - `resolveUserId(facts: RequestFacts): Promise<string | null>`（调 `lookupSessionByToken(facts.token, { client: facts.client })`）。
  - `buildServerEnvelope(event: ServerEventInput, facts: RequestFacts, userId: string | null, page: { path: string; type: PageType }, now: Date): CollectEnvelope`。
  - `toCollectEnvelope(raw: Record<string, unknown>, sentAt: string, facts: RequestFacts, userId: string | null): CollectEnvelope`（浏览器事件，白名单取 7 个字段）。
  - `sendEnvelopes(envelopes: CollectEnvelope[]): Promise<void>`（永不抛）。
  - `trackServerEvent(event: ServerEventInput, facts: RequestFacts | null, page: { path: string; type: PageType }): void`：`facts` 为 `null` 或 `isActionRequest` 时什么也不做；否则 `after(async () => sendEnvelopes([buildServerEnvelope(event, facts, await resolveUserId(facts), page, new Date())]))`。
- [ ] **Step 1: 失败测试 `envelope.test.ts`**：五种服务端事件（含 `search_performed` 带全部筛选键）的信封各自 `validateAgainst("collect-envelope", ...)` 为空；`client.ip` 为 `null` → `"0.0.0.0"`；UA 2000 字符 → 截到 1024；`userId` 为 `null` 时字段是 `null`；`page_path` 600 字符被截到 512；`toCollectEnvelope` 对带 `user_id: "9"`、`device_id: "x"`、`is_synthetic: true`、`referrer_host: "evil"` 的原始事件，输出里这些字段是会话 / Cookie 的值、`false`、`null`，且输出键集合恰好是 16 个。
- [ ] **Step 2: 失败测试 `send.test.ts`**：请求 `http://gw.test/patra-analytics/events`、方法 POST、体 `{ events }`；`fetch` 的 `signal` 存在（6 秒超时）；503 / 抛错 / 超时时 resolve 且 `console.warn` 一次、不调 `console.error`；`trackServerEvent` 在 `isActionRequest` 时不调 `after`（`vi.mock("next/server")` 里 `after` 替换为立即执行的 spy）。
- [ ] **Step 3: 跑 → FAIL；实现；跑 → PASS。**

### Task 6: `article_viewed` 与 `search_performed`

**Files:**
- Create: `patra-portal/src/lib/analytics/server/search-properties.ts`
- Modify: `patra-portal/src/app/papers/[id]/page.tsx`、`patra-portal/src/app/papers/(list)/page.tsx`、`patra-portal/src/components/portal/papers/PaperResults.tsx`
- Test: `patra-portal/tests/lib/analytics/search-properties.test.ts`、`patra-portal/tests/app/papers/analytics-events.test.tsx`、`patra-portal/tests/app/papers/loading-boundaries.test.ts`

**Interfaces:**
- Consumes: `readPageFacts`、`trackServerEvent`（Task 5）、`newId`。
- Produces: `searchPerformedProperties(query: PaperSearchQuery, searchId: string, resultCount: number): SearchPerformed`；`isBrowseState(query: PaperSearchQuery): boolean`（无检索词、无筛选、无精确定位、第 1 页、`sort === "latest"`）。`PaperResults` 新增可选 prop `searchId?: string` 不需要——它自己生成并下传给 Task 10 的 `TrackedList`（Task 10 再接线）。

- [ ] **Step 1: 失败测试 `search-properties`**：关键词 `"  sepsis  "` → `mode: "keyword", query: "sepsis"`；`pmid: "39871245"` → `mode: "exact_pmid", query: "39871245"`；`doi` → `exact_doi` 与 DOI 原文；只用筛选 → `query: ""`；`yearFrom: 2020` → `filters.year_from: 2020`，未用的键不出现；`oa: false` 不出现；数组去重、25 项截到 20；`author` 300 字符截到 200；输出过 `events/search_performed` schema。`isBrowseState(EMPTY_PAPER_QUERY) === true`，`{...EMPTY, page: 2}` 为 `false`，`{...EMPTY, sort: "year"}` 为 `false`。
- [ ] **Step 2: 失败测试 `analytics-events`**（mock `trackServerEvent`、`readPageFacts`、取数函数）：详情页文献存在 → `trackServerEvent({ name: "article_viewed", properties: { article_id: "42" } }, facts, { path: "/papers/42", type: "article" })`；文献不存在 → 不调；`PaperResults` 有结果 → 一次 `search_performed` 且 `result_count` 为 `total`；空结果 → `result_count: 0`；浏览态 → 不调；精确定位命中 1 条 → 页面顶层在 `redirect` 前发一次 `result_count: 1`。
- [ ] **Step 3: 失败测试 `loading-boundaries`**：`existsSync` 断言 `src/app/papers/[id]/loading.tsx`、`src/app/papers/(list)/loading.tsx` 存在（注释说明 spec §2.3：它们挡住预取渲染页面）。
- [ ] **Step 4: 跑 → FAIL；实现；跑 → PASS。** 页面里在 `notFound()` 之后调 `readPageFacts()` 与 `trackServerEvent`；`PaperResults` 里 `const searchId = newId()`，浏览态不发。

### Task 7: PDF、导出、收藏

**Files:**
- Modify: `patra-portal/src/app/api/papers/[id]/pdf/route.ts`、`patra-portal/src/lib/portal-api/gateway-file.ts`（返回上游状态供判断，或在路由里看响应 `status === 200`）、`patra-portal/src/app/api/publications/export/route.ts`、`patra-portal/src/components/library/ExportDialog.tsx`、`patra-portal/src/components/library/SharePanel.tsx`、`patra-portal/src/lib/library/scope.ts`（新增 `exportScopeOf(scope: DialogScope): ExportCompleted["scope"]`）、`patra-portal/src/lib/interaction/library-actions.ts`、`patra-portal/src/components/library/SavePopover.tsx`、`patra-portal/src/components/library/LibraryView.tsx`
- Test: 现有 `tests/app/api/publication-export.test.ts`、`tests/lib/interaction/library-actions.test.ts`、`tests/components/library/ExportDialog.test.tsx`、`SharePanel.test.tsx`、`SavePopover.test.tsx`、`LibraryView.test.tsx` 加用例；新增 `tests/app/api/pdf-analytics.test.ts`、`tests/lib/library/export-scope.test.ts`

**Interfaces:**
- Consumes: `readHandlerFacts`、`trackServerEvent`。
- Produces: `collectPublication(key: string, options?: { restore?: boolean }): Promise<CollectResult>`。导出请求体新增必填 `scope: "items" | "collection" | "public_collection" | "library_all" | "library_unfiled"`。

- [ ] **Step 1: 失败测试**：
  - PDF：上游 200 → `trackServerEvent` 收到 `pdf_downloaded` `{ article_id: "7" }`、页面 `{ path: "/api/papers/7/pdf", type: <Referer 推断> }`；304、404、502 → 不调。
  - 导出：缺 `scope` → 400；上游 200 → `export_completed` `{ format: "ris", scope: "library_all", article_count: 3 }`；上游 502 → 不调。
  - `exportScopeOf`：五种映射（spec §4.2）。
  - `ExportDialog` 请求体带映射后的 `scope`；`SharePanel` 单篇导出带 `scope: "items"`。
  - `collectPublication`：`created: true` → 发 `article_collected`；`created: false` → 不发；`{ restore: true }` → 不发；失败 → 不发。`SavePopover`、`LibraryView` 的撤销调用带 `{ restore: true }`。
- [ ] **Step 2: 跑 → FAIL；实现；跑上列测试 → PASS。**

### Task 8: 第一方批次路由

**Files:**
- Create: `patra-portal/src/app/api/reading-signals/route.ts`
- Test: `patra-portal/tests/app/api/reading-signals.test.ts`

**Interfaces:**
- Consumes: `readHandlerFacts`、`resolveUserId`、`toCollectEnvelope`、`sendEnvelopes`（Task 5）、`isSameOrigin`。
- Produces: `POST /api/reading-signals`，请求体 `{ sent_at: string; events: object[] }`。

- [ ] **Step 1: 失败测试**（`after` mock 成立即执行）：缺 `Origin` → 403；体 70_000 字节 → 413；非 JSON、`events: []`、51 条、`sent_at: "2026-11-01T08:30:12Z"`（无毫秒）→ 400；合法批次 → 204、写出会话 Cookie、网关收到 `{ events }` 每条 `source: "client"`、`sent_at` 为批次的、`device_id` 为 Cookie 的；登录（mock `lookupSessionByToken` 返回 `authenticated` `userId: "77"`）→ `user_id: "77"`，匿名 → `null`；事件里塞的 `user_id: "1"` 不出现；网关 503 / 抛错 → 仍 204。用样例 `examples/valid/browser-batch--mixed.json` 当输入，转出的每条按 `collect-envelope` 校验为空。
- [ ] **Step 2: 跑 → FAIL；实现（zod 外壳：`sent_at` 用 defs 的时间正则、`events` 为 1～50 个 `z.record(z.string(), z.unknown())`）；跑 → PASS。**

### Task 9: 浏览器队列

**Files:**
- Create: `patra-portal/src/lib/analytics/client/queue.ts`
- Test: `patra-portal/tests/lib/analytics/queue.test.ts`

**Interfaces:**
- Consumes: `newId`、`pageTypeOf`、`truncatePath`、`SCHEMA_VERSIONS`、`ClientEventProperties`。
- Produces: `track<K extends ClientEventName>(name: K, properties: ClientEventProperties[K]): void`；`flushWithBeacon(): void`（页面隐藏 / 离开时调）；`createQueue(deps: { now: () => number; fetch: typeof fetch; sendBeacon: ((url: string, data: Blob) => boolean) | null; location: () => string; setTimer: (fn: () => void, ms: number) => unknown; clearTimer: (handle: unknown) => void })`（测试注入用，`track` / `flushWithBeacon` 是默认实例的绑定）；`splitBatches(events: ClientEvent[], maxCount = 50, maxBytes = 49_152): ClientEvent[][]`。

- [ ] **Step 1: 失败测试**（假时钟、注入 deps）：入队一条，4_999 ms 未发、5_000 ms 发一次 `fetch("/api/reading-signals", { method: "POST", keepalive: true, … })`，体过 `browser-batch` schema、每条过 `browser-event`；连入 20 条立即发；`flushWithBeacon` 用 `sendBeacon` 发剩余，`sendBeacon` 为 `null` 时退回 `fetch keepalive`；`splitBatches` 51 条 → 50 + 1，按字节超 48 KiB 时切开，单条超 48 KiB 被丢；入队后改 `location` 再发，`page_path` 仍是入队时的；`page_path` 600 字符截到 512；`fetch` reject / 500 → 不抛、`console.error` 未被调用；服务端环境（`typeof window === "undefined"`，用 `// @vitest-environment node`）`track` 不抛不发。
- [ ] **Step 2: 跑 → FAIL；实现；跑 → PASS。**

### Task 10: 曝光与点击

**Files:**
- Create: `patra-portal/src/lib/analytics/client/impression.ts`、`patra-portal/src/lib/analytics/client/TrackedList.tsx`、`patra-portal/src/lib/analytics/client/TrackedArticle.tsx`
- Modify: `patra-portal/src/components/portal/explore-feed/index.tsx`、`patra-portal/src/components/portal/papers/PaperResults.tsx`、`patra-portal/src/components/library/PublicCollectionView.tsx`、`patra-portal/src/components/library/LibraryView.tsx`
- Test: `patra-portal/tests/lib/analytics/impression.test.ts`、`patra-portal/tests/lib/analytics/tracked-article.test.tsx`

**Interfaces:**
- Consumes: `track`（Task 9）、`newId`、`ListType`。
- Produces: `createImpressionTimer(opts: { onImpress: () => void; setTimer; clearTimer }): { ratio(r: number): void; visibility(visible: boolean): void; dispose(): void }`；`<TrackedList listType: ListType; listId?: string; children>`；`<TrackedArticle articleId: string; position: number; children>`。

- [ ] **Step 1: 失败测试 `impression`**（spec §6.4 例子）：`ratio(0.6)` 800 ms 后 `ratio(0)` → 不发；`ratio(0.6)` 1_200 ms → 发一次；`ratio(0.4)` 2 秒 → 不发；`visibility(false)` 时 `ratio(0.6)` 2 秒 → 不发，`visibility(true)` 再 1_000 ms → 发；发过后再进出不再发（计时器本身一次性）。
- [ ] **Step 2: 失败测试 `tracked-article`**（stub `IntersectionObserver`，手动触发回调）：同一 `TrackedList` 里同一篇在两个 `TrackedArticle` 都满足 → 只 `track` 一次；两个列表各一次；点 `a[href="/papers/42"]` → `track("article_clicked", { article_id: "42", list_type: "search", list_id, position: 21 })`；点 `a[href="/journals/3"]` → 不发；没有 `IntersectionObserver` 时不报错；`listId` 传入时用它，不传时是 UUID 且重渲染不变。
- [ ] **Step 3: 跑 → FAIL；实现；跑 → PASS。**
- [ ] **Step 4: 接线四个列表**：文献流 `listType` 按 `tab`（`recent` → `feed_latest`、`cited` → `feed_cited`），`position` 用现有 `index`；检索结果 `listId = searchId`、`position = (query.page - 1) * page.pageSize + i + 1`；公开收藏夹 `collection`、文献库 `library`，`position` 按各自 `(page - 1) * pageSize + i + 1`。在现有各组件测试里各加一条：列表里有 `TrackedArticle` 包住卡片（断言点击标题后 `track` 被以正确 `list_type` / `position` 调用）。

### Task 11: 全文停留

**Files:**
- Create: `patra-portal/src/lib/analytics/client/dwell.ts`、`patra-portal/src/lib/analytics/client/FulltextReadTracker.tsx`
- Modify: `patra-portal/src/components/portal/fulltext/FullTextSection.tsx`（只包 `rendered.content`）
- Test: `patra-portal/tests/lib/analytics/dwell.test.ts`、`patra-portal/tests/lib/analytics/fulltext-read-tracker.test.tsx`

**Interfaces:**
- Produces: `isReadingVisible(rect: { top: number; bottom: number; height: number }, viewportHeight: number): boolean`；`createDwellTracker(opts: { thresholdMs: number; onReach: (seconds: number) => void; now: () => number; setTimer; clearTimer }): { reading(active: boolean): void; dispose(): void }`；`<FulltextReadTracker articleId>`（`FullTextSection` 里以 `key={paper.id}` 使用）。

- [ ] **Step 1: 失败测试 `dwell`**：视口 1000、正文 top 300 bottom 3000 → 可见 700 ≥ 500 → `true`；top 800 → 可见 200 → `false`；正文高 400 且整段在视口内 → `true`、露出一半 → `false`；`reading(true)` 6 s → `reading(false)` 60 s → `reading(true)` 4 s → `onReach(10)` 一次；之后再读不再调；9.9 s 后 `dispose` → 不调。
- [ ] **Step 2: 失败测试 tracker**：mock `getBoundingClientRect`、触发 `scroll` 与 `visibilitychange`，满 10 s 后 `track("fulltext_read", { article_id: "42", visible_seconds: 10 })`；`hidden` 期间不累计；换 `articleId`（重挂载）重新计时。
- [ ] **Step 3: 跑 → FAIL；实现（scroll / resize 用 `requestAnimationFrame` 节流，passive 监听）；跑 → PASS。**

### Task 12: 页面浏览与队列生命周期

**Files:**
- Create: `patra-portal/src/lib/analytics/client/AnalyticsRoot.tsx`
- Modify: `patra-portal/src/app/layout.tsx`（`<Suspense fallback={null}><AnalyticsRoot /></Suspense>`，放在 `QueryProvider` 里）
- Test: `patra-portal/tests/lib/analytics/analytics-root.test.tsx`

**Interfaces:**
- Consumes: `track`、`flushWithBeacon`（Task 9）。

- [ ] **Step 1: 失败测试**（mock `next/navigation` 的 `usePathname`、mock 队列）：首次渲染且可见 → `track("page_viewed", {})` 一次；`pathname` 变化 → 再一次；同路径重渲染 → 不再发；StrictMode 下首次只发一次；`document.visibilityState = "hidden"` 时挂载 → 不发，派发 `visibilitychange` 变 `visible` → 发；隐藏中路径变了再变回可见 → 只为当前路径发一次；`visibilitychange` 变 `hidden` 与 `pagehide` → 调 `flushWithBeacon`。
- [ ] **Step 2: 跑 → FAIL；实现；跑 → PASS。**

### Task 13: 复制引用

**Files:**
- Modify: `patra-portal/src/components/library/CopyButton.tsx`（`onCopied?: () => void`）、`patra-portal/src/components/library/CiteDialog.tsx`、`patra-portal/src/components/library/SharePanel.tsx`
- Test: 现有 `tests/components/library/CiteDialog.test.tsx`、`SharePanel.test.tsx`、`CopyButton` 所在测试加用例

- [ ] **Step 1: 失败测试**：复制成功 → `onCopied` 调用一次；降级到「已选中」→ 不调；`CiteDialog` 复制 → `track("citation_copied", { article_ids: ["1", "2"], style: "gb-t-7714-2015" })`（ID 去重）；`SharePanel` 复制引用 → `article_ids: [paperId]` 与当前格式；复制链接 → 不 `track`；切格式 → 不 `track`。
- [ ] **Step 2: 跑 → FAIL；实现；跑 → PASS。**

### Task 14: 冒烟用例与全量验证

**Files:**
- Create: `patra-portal/tests/e2e/analytics-tracking.spec.ts`

- [ ] **Step 1: 写 Playwright 用例**（不在本地跑，等后端部署 mini）：没 Cookie 打开首页 → 有设备 Cookie；登录 B 后设备 Cookie 值不变；打开一篇文献 → 页面对 `/api/reading-signals` 发出带 `page_viewed` 的请求（`page.waitForRequest`）且响应 204；页面控制台没有 error。注释写明「采集端点收到 `article_viewed` 带 `device_id` / `user_id`、没有 IP」在 mini 的 Kafka 里人工核对，以及「停掉 analytics」的步骤。
- [ ] **Step 2: 全量**：`pnpm lint`、`pnpm typecheck`、`pnpm test --maxWorkers=2` 全绿。
- [ ] **Step 3: 本地起门户**（`.claude/launch.json` 的 `portal-dev`）：首页、检索、文献页、文献库正常；浏览器网络面板能看到 `/api/reading-signals` 204；控制台无错误；服务端日志里采集端点不可达只有 `warn`。
