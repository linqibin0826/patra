# 门户「收藏与文献库」实施计划（PAP-114）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans（本仓库约定内联执行，不派子代理）。Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 门户接上 interaction 的收藏与收藏夹：三处收藏按钮、文献库与收藏夹管理、公开收藏夹页、导出与复制引用、主页收藏夹栏、导航入口。

**Architecture:** 服务端 `callInteraction` 封装（必须登录 / 登录可选）；读走 TanStack Query（RSC 预取 + `HydrationBoundary`，失效重取走 Route Handler），写走 Server Action，交互后只改查询缓存；文献库条目在门户服务端拼 interaction 条目 + catalog 批量卡片；导出经 Route Handler 流式转发 catalog。

**Tech Stack:** Next.js 16 App Router（webpack）、React 19、TypeScript strict、Tailwind v4、base-ui、TanStack Query v5、RHF + Zod、Vitest + Testing Library、Playwright。

**Spec:** `docs/patra/specs/2026-10-25-library-portal-design.md`（页面与文案：`patra-portal/docs/design-briefs/v0.13/library.md`）

## Global Constraints

- 所有命令在 `patra-portal/` 下跑：`pnpm test`、`pnpm typecheck`、`pnpm lint`（`pnpm exec biome check --write .` 自动修）。
- **不提交、不推送**：用户本地验收（桌面 1440×900 + 手机 375×812 截图）通过后才 commit / 开 PR。各任务的收尾是「测试全绿」，不是 commit。
- 文案逐字照简报 §3、§4；不出现任何收藏数 / 被复制次数 / 浏览数；主页标签不带个数。
- 颜色只用语义 token 与现有色阶类（`text-fg-3`、`clay-500` 等），不写 `[var(--x)]`；不写 inline `style`。
- RSC 里的 `try/catch` 先 `unstable_rethrow(error)`。
- 不调用 `router.refresh()` 更新互动状态。
- ID 一律字符串；路径里的 ID 先按 `^[1-9]\d{0,18}$` 校验再打网关。
- 超时：我的状态 3,000 ms（实施中由 1,500 改，见 ledger）；其余 interaction 读 5,000 ms。
- 上限：收藏夹名称 1 ~ 100 字素、简介 ≤ 500、备注 ≤ 500；导出 ≤ 5,000；复制引用 ≤ 100；状态批量 ≤ 100；每页 50。
- 测试：单测 `// @vitest-environment node` + mock `fetch`（同 `tests/lib/identity/profile-actions.test.ts`）；组件测试 `getByRole` 优先；禁止快照、禁止裸 `setTimeout`。

## Review Focus

1. **同一篇文献在一屏出现两次**（首页头条与列表重复、详情页 + 公开页条目）：状态批量请求先去重，两个按钮共享状态，一个收藏后另一个同步变「已收藏」→ Task 7 `dedupes keys and shares status`。
2. **会话在页面打开期间失效**（另一设备退出）：任何 Action 返回 `signed-out` → 跳 `/login?next=<当前路径>`，不显示「没有保存成功」→ Task 8 `redirects to login on signed-out`。
3. **收藏夹名字含 `/`、`"`、emoji、换行**：导出文件名由 catalog 清洗；门户只按字素计长度，emoji 计 1 → Task 12 `counts emoji as one grapheme`。
4. **翻页后当前页被批量移除清空**（最后一页全部移除）：列表回到上一页而不是显示空状态 → Task 11 `falls back to previous page when page empties`。
5. **公开页访客点收藏的是自己库里已失效的文献**：状态 `collected=true`，按钮「已收藏」，浮层照常可移除 → Task 8 `opens popover for collected unavailable item`。

---

### Task 1: 出版日期按精度 + 删收藏数字段

**Files:**
- Create: `src/lib/published-date.ts`
- Modify: `src/types/portal.ts`（`Paper` 加 `publishedDate: string | null`、删 `bookmarks`；`PaperDetail` 加 `publishedDate`、删 `bookmarks`；`PublicationDate` 注释改为降精度 ISO）
- Modify: `src/components/portal/PaperCard.tsx`、`src/components/portal/papers/PaperListItem.tsx`（年份用 `publishedYear(paper)`）
- Modify: `src/components/portal/paper-detail/PaperHeader.tsx`（作者行日期用 `formatPublishedDate(paper.publishedDate)`，回落 `publicationYear`）
- Modify: `src/components/portal/paper-detail/PaperFacts.tsx`（删「收藏」格）
- Modify: `tests/fixtures/paper.ts` 及引用 `bookmarks` 的测试
- Test: `tests/lib/published-date.test.ts`、`tests/components/portal/paper-detail/PaperFacts.test.tsx`、`PaperHeader.test.tsx`、`tests/components/portal/PaperCard.test.tsx`

**Interfaces:**
- Produces: `formatPublishedDate(date: string | null): string | null`；`publishedYear(paper: { publishedDate: string | null; year: number | null }): string | null`

- [ ] **Step 1: 写失败的测试**

```ts
// tests/lib/published-date.test.ts
it.each([
  ["2019", "2019"], ["2019-03", "2019 年 3 月"], ["2019-03-15", "2019 年 3 月 15 日"],
  ["2019-3", null], ["", null], [null, null],
])("formatPublishedDate(%s) = %s", (input, out) => expect(formatPublishedDate(input)).toBe(out));
it("publishedYear 取年份前 4 位，没有日期回落 year", () => {
  expect(publishedYear({ publishedDate: "2019-03-15", year: 2018 })).toBe("2019");
  expect(publishedYear({ publishedDate: null, year: 2018 })).toBe("2018");
  expect(publishedYear({ publishedDate: null, year: null })).toBeNull();
});
```
`PaperFacts.test.tsx` 加 `it("事实条没有「收藏」格")`：`queryByText("收藏")` 为 null。`PaperHeader.test.tsx` 加 `publishedDate: "2019-03"` 时作者行出现 `2019 年 3 月`、不出现 `1 月 1 日`。`PaperCard.test.tsx` 加 `publishedDate: "2019-03-15"` 时只显示 `2019`。

- [ ] **Step 2: 跑测试确认失败** — `pnpm test tests/lib/published-date.test.ts tests/components/portal` → FAIL（模块不存在 / 断言不符）
- [ ] **Step 3: 实现** — 按长度 4 / 7 / 10 与 `^\d{4}(-\d{2}(-\d{2})?)?$` 判精度；月日 `Number()` 去前导零。删 `bookmarks` 后用 `pnpm typecheck` 找出全部引用改掉。
- [ ] **Step 4: 跑测试与类型检查** — `pnpm test` 全绿、`pnpm typecheck` 无错。

### Task 2: 头像按用户 ID + 公开主页带 userId

**Files:**
- Modify: `src/lib/identity/avatar.ts`（正则 `^\/users\/([1-9][0-9]{0,18})\/avatar\?v=([0-9a-f]{16})$`，转发地址 `/api/users/{userId}/avatar?v=…&size=…`）
- Move: `src/app/api/users/[username]/avatar/route.ts` → `src/app/api/users/[userId]/avatar/route.ts`（校验 `^[1-9][0-9]{0,18}$`，`RouteContext<"/api/users/[userId]/avatar">`）
- Modify: `src/lib/identity/public-profile.ts`（schema 加 `userId: z.string()`，删「不含用户 ID」）
- Modify: `tests/fixtures/*`（`avatarUrl` 改为 `/users/<数字>/avatar?v=…`；公开主页夹具加 `userId`）
- Test: `tests/lib/identity/avatar.test.ts`、`tests/app/api/users-avatar.test.ts`、`tests/app/route-structure.test.ts`、`tests/lib/identity/public-profile.test.ts`

**Interfaces:**
- Produces: `PublicProfile.userId: string`

- [ ] **Step 1: 改测试**：`avatarSrc("/users/352303128713027814/avatar?v=0123456789abcdef", 128)` → `/api/users/352303128713027814/avatar?v=0123456789abcdef&size=128`；`avatarSrc("/users/chen-yu/avatar?v=…")` → null；路由对 `chen-yu` 404 不打网关、对数字 ID 转发 `/patra-identity/users/{id}/avatar?size=…&v=…`；`route-structure` 的路径表改成 `[userId]`；公开主页缺 `userId` 时抛错。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**（`git mv` 路由目录；`pnpm typecheck` 会重新生成路由类型）
- [ ] **Step 4: 跑 `pnpm test` 与 `pnpm typecheck`，全绿**

### Task 3: interaction 公共层、schema、公开地址、POST 流式转发

**Files:**
- Create: `src/lib/interaction/request.ts`、`src/lib/interaction/schema.ts`、`src/lib/public-url.ts`
- Modify: `src/lib/gateway.ts`（`GatewayStreamInit` 加 `method?: "GET" | "POST"`、`json?: unknown`）
- Modify: `.env.example`（加 `PATRA_PORTAL_PUBLIC_URL`，注释写「复制链接与 canonical 用的门户公开地址；不设时用请求的 Host」）
- Test: `tests/lib/interaction/request.test.ts`、`tests/lib/interaction/schema.test.ts`、`tests/lib/public-url.test.ts`、`tests/lib/gateway.test.ts`
- Create: `tests/fixtures/interaction.ts`（`COLLECTION`、`OVERVIEW`、`LIBRARY_ITEM`、`UNAVAILABLE_ITEM`、`ITEM_STATUS`、`PUBLIC_COLLECTION`、`PUBLIC_ITEM`、`COPY_RESULT`）

**Interfaces:**
- Produces:
  - `callInteraction(path: string, init?: Omit<GatewayRequestInit, "token" | "client">, options?: { auth?: "required" | "optional" }): Promise<InteractionReply>`；`type InteractionReply = IdentityReply`（同形）
  - schema 与类型：`collectionSchema`/`Collection { collectionId, name, description: string|null, visibility: "PRIVATE"|"PUBLIC", itemCount, createdAt, updatedAt }`；`LibraryOverview { total, unfiled, collections: Collection[] }`；`LibraryItem { itemId, type, key: string|null, state: "ACTIVE"|"UNAVAILABLE", title: string|null, note: string|null, collectedAt, addedAt: string|null, collectionIds: string[] }`；`ItemStatus { key, collected, itemId: string|null, collectedAt: string|null, collectionIds: string[], hasNote }`；`CollectResult { created, item: LibraryItem }`；`AddToCollectionResult { added, alreadyIn, skippedFull, skippedNotInLibrary }`；`Owner { userId, username: string|null, displayName: string|null, avatarUrl: string|null, avatarPalette: number, deleted: boolean }`；`PublicCollection { collectionId, name, description, visibility, itemCount, updatedAt, owner: Owner|null, viewerIsOwner }`；`PublicItem { itemId, type, key, state, title, addedAt }`；`PublicCollectionCard { collectionId, name, description, itemCount, updatedAt }`；`CopyResult { collectionId, name, addedToLibrary, alreadyInLibrary, skippedUnavailable }`；`ExportKeys { keys: string[], skippedUnavailable: number }`；`pageSchema<T>(item)` → `Page<T> { page, pageSize, total, totalPages, items }`
  - `publicUrl(path: string): Promise<string>`
  - `gatewayStream(path, { method: "POST", json, ... })`

- [ ] **Step 1: 写失败的测试**：`callInteraction("/me/library")` 无 Cookie → `{ kind: "no-session" }` 且 `fetch` 未调用；`auth: "optional"` 无 Cookie 照常请求且不带 `Authorization`；有 Cookie 带 `Bearer`、URL 为 `http://gw.test/patra-interaction/me/library`；`timeoutMs` 透传；404 → `problem.status === 404`；`fetch` 抛错 → `{ kind: "error" }`。schema：数字 ID 被拒（要求字符串）、`owner: null` 通过、`key: null` 通过。`publicUrl("/collections/1")`：设了 `PATRA_PORTAL_PUBLIC_URL=http://mini:4000/` → `http://mini:4000/collections/1`；未设且 `Host: localhost:4000` → `http://localhost:4000/collections/1`。`gatewayStream` POST 带 JSON 体与 `Content-Type`。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**（`request.ts` 结构照抄 `callIdentity`，前缀 `/patra-interaction`）
- [ ] **Step 4: 跑测试，全绿**

### Task 4: 服务端读函数与卡片拼装

**Files:**
- Create: `src/lib/interaction/library.ts`、`src/lib/interaction/collections.ts`、`src/lib/interaction/scope.ts`
- Modify: `src/lib/portal-api/publications.ts`（加 `fetchPublicationCards`）
- Test: `tests/lib/interaction/library.test.ts`、`collections.test.ts`、`scope.test.ts`、`tests/lib/portal-api/publications.test.ts`

**Interfaces:**
- Consumes: Task 3 全部
- Produces:
  - `type Read<T> = { kind: "ok"; data: T } | { kind: "signed-out" } | { kind: "not-found" } | { kind: "unavailable" }`
  - `type LibraryView = "all" | "unfiled" | { collectionId: string }`；`type LibrarySort = "added" | "published" | "journal"`
  - `interface LibraryEntry { item: LibraryItem; paper: Paper | null }`（`paper` 为 null = 按已失效外观显示）
  - `parseLibrarySort(v: unknown): LibrarySort`；`parsePage(v: unknown): number`；`isEntityId(v: string): boolean`
  - `fetchLibraryStatus(keys: string[]): Promise<Read<ItemStatus[]>>`（1,500 ms）
  - `fetchLibraryOverview(): Promise<Read<LibraryOverview>>`
  - `fetchLibraryPage(view: LibraryView, sort: LibrarySort, page: number): Promise<Read<Page<LibraryEntry>>>`
  - `fetchPublicCollection(id: string): Promise<Read<PublicCollection>>`（登录可选）
  - `fetchPublicCollectionPage(id: string, sort: LibrarySort, page: number): Promise<Read<Page<PublicEntry>>>`，`PublicEntry { item: PublicItem; paper: Paper | null }`
  - `fetchUserPublicCollections(userId: string): Promise<Read<PublicCollectionCard[]>>`
  - `type ExportScope = { kind: "library"; view: "all" | "unfiled"; sort: LibrarySort } | { kind: "collection"; id: string; sort: LibrarySort } | { kind: "public-collection"; id: string; sort: LibrarySort }`；`exportScopeSchema`（zod）
  - `fetchScopeKeys(scope: ExportScope): Promise<Read<ExportKeys> | { kind: "too-large" }>`
  - `fetchPublicationCards(ids: string[]): Promise<Paper[]>`（失败抛错）

- [ ] **Step 1: 写失败的测试**：`fetchLibraryPage("all","added",1)` 请求 `/patra-interaction/me/library/items?view=all&sort=added&page=1&size=50` 与 `/patra-catalog/portal/publications/batch?id=11&id=12`（只含 ACTIVE 且有 key 的；卡片请求无 `Authorization`）；返回 entries 顺序同 interaction；catalog 少回一条 → 那条 `paper: null`；`UNAVAILABLE` 条目不请求卡片；整页没有可取卡片时不请求 catalog；`{ collectionId: "9" }` 请求 `/me/collections/9/items?…`，404 → `not-found`；interaction 401 → `signed-out`；超时 / 5xx / schema 不符 → `unavailable`；catalog 抛错 → `unavailable`。`fetchPublicCollection` 503 → `unavailable`（不是 not-found）。`fetchScopeKeys` 422 + `EXPORT_TOO_LARGE` → `too-large`；`public-collection` 走 `/collections/{id}/keys?sort=` 且登录可选。`parseLibrarySort("x")` → `added`；`parsePage("0")` → 1；`isEntityId("01")` → false。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试，全绿**

### Task 5: 读用 Route Handler 与 catalog 转发

**Files:**
- Create: `src/app/api/me/library/route.ts`（GET 概览）、`src/app/api/me/library/items/route.ts`（GET）、`src/app/api/me/library/status/route.ts`（POST）、`src/app/api/library/keys/route.ts`（POST）、`src/app/api/publications/export/route.ts`（POST）、`src/app/api/publications/citations/route.ts`（POST）
- Create: `src/lib/route-reply.ts`（`readReply(read: Read<T>): NextResponse`：ok → 200 `private, no-store`；signed-out → 401 `{ reason: "signed-out" }`；not-found → 404；unavailable → 502）
- Test: `tests/app/api/library-routes.test.ts`、`tests/app/api/publication-export.test.ts`

**Interfaces:**
- Consumes: Task 4
- Produces（客户端调用的形状）：
  - `GET /api/me/library` → `LibraryOverview`
  - `GET /api/me/library/items?view=all|unfiled|<collectionId>&sort&page` → `Page<LibraryEntry>`
  - `POST /api/me/library/status` `{ keys: string[] }` → `{ items: ItemStatus[] }`
  - `POST /api/library/keys` `{ scope: ExportScope }` → `ExportKeys` 或 `{ tooLarge: true }`（200）
  - `POST /api/publications/export` `{ ids: string[] (1..5000), format: "ris"|"bibtex"|"csv"|"csl-json", name?: string ≤ 100 }` → 文件流，透传 `Content-Type`、`Content-Disposition`
  - `POST /api/publications/citations` `{ ids: string[] (1..100), style: "gb-t-7714-2015"|"vancouver"|"apa-7"|"ama-11" }` → `{ entries: { label: string|null; text: string }[] }`

- [ ] **Step 1: 写失败的测试**：POST 路由对缺 `Origin` 或异源 → 403 不打网关；`status` 101 个 key → 422；`status` 的 keys 去重后再发；`items` 的 `view=abc` → 404；`export` 透传 catalog 的 `Content-Disposition` 与响应体字节，网关请求无 `Authorization`、方法 POST、体 `{ids, format, name}`；`format=pdf` → 422；catalog 非 2xx → 502；`citations` 解析 entries，`style=apa` → 422。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试，全绿**

### Task 6: 写操作 Server Actions

**Files:**
- Create: `src/lib/interaction/library-actions.ts`、`src/lib/interaction/collection-actions.ts`、`src/lib/interaction/action-failure.ts`
- Test: `tests/lib/interaction/library-actions.test.ts`、`collection-actions.test.ts`

**Interfaces:**
- Produces（全部 `"use server"`，返回 `Promise<…>`；`Common = { ok: false; reason: "signed-out" | "unavailable" }`）：
  - `collectPublication(key: string)` → `{ ok: true; created: boolean; item: LibraryItem } | { ok: false; reason: "library-full" | "gone" | "catalog-unavailable" } | Common`
  - `removeItems(itemIds: string[])` → `{ ok: true } | Common`
  - `saveNote(itemId: string, note: string)` → `{ ok: true; item: LibraryItem } | { ok: false; reason: "invalid-fields"; fieldErrors: FieldError[] } | { ok: false; reason: "gone" } | Common`
  - `createCollection(input: { name: string; description: string })`、`updateCollection(id: string, input)` → `{ ok: true; collection: Collection } | { ok: false; reason: "invalid-fields"; fieldErrors } | { ok: false; reason: "name-taken" | "collection-limit" | "gone" } | Common`
  - `setVisibility(id: string, visibility: "PRIVATE" | "PUBLIC")` → `{ ok: true; collection } | { ok: false; reason: "email-unverified" | "account-restricted" | "gone" } | Common`
  - `deleteCollection(id: string)` → `{ ok: true } | Common`
  - `addToCollection(id: string, itemIds: string[])` → `{ ok: true; result: AddToCollectionResult } | { ok: false; reason: "gone" } | Common`
  - `removeFromCollection(id: string, itemIds: string[])` → `{ ok: true } | { ok: false; reason: "gone" } | Common`
  - `copyCollection(id: string)` → `{ ok: true; result: CopyResult } | { ok: false; reason: "not-visible" | "library-full" | "collection-limit" } | Common`

- [ ] **Step 1: 写失败的测试**（每个 Action 一个 describe）：请求方法与路径（`PUT /patra-interaction/me/library/publication/123`、`DELETE …/me/library/items?itemIds=1,2`、`PUT …/me/collections/9/visibility` 体 `{visibility:"PUBLIC"}` 等）；409 `traits:["LIBRARY_FULL"]` → `library-full`；503 `code:"INTERACTION-0503"` → `catalog-unavailable`，其余 503 → `unavailable`；404 → `gone` / `not-visible`；409 `COLLECTION_NAME_TAKEN` → `name-taken`；`COLLECTION_LIMIT_REACHED` → `collection-limit`；`ACCOUNT_RESTRICTED` 与 `EMAIL_UNVERIFIED` 同时出现 → `account-restricted`；422 带 `errors` → `invalid-fields`；401 → `signed-out` 且 Cookie 被清；无 Cookie → `signed-out` 不打网关；非法 ID（`"01"`、`"abc"`）或空数组 / 101 个 → `unavailable` 不打网关。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**（`action-failure.ts` 放 traits → reason 的共用判断与 `settleFailure` 包装）
- [ ] **Step 4: 跑测试，全绿**

### Task 7: 查询键、客户端查询与缓存工具、Popover 原语

**Files:**
- Modify: `src/lib/query-keys.ts`
- Create: `src/lib/library/queries.ts`（客户端 hooks）、`src/lib/library/cache.ts`（缓存改写工具）、`src/lib/library/server-query.ts`（服务端 `QueryClient` 工厂 + 预取）
- Create: `src/components/library/LibraryStatusProvider.tsx`
- Create: `src/components/ui/popover.tsx`
- Test: `tests/lib/library/cache.test.ts`、`tests/components/library/LibraryStatusProvider.test.tsx`、`tests/components/ui/popover.test.tsx`

**Interfaces:**
- Consumes: Task 5 路由
- Produces:
  - `queryKeys.libraryStatus(keys: string[])` = `["library-status", ...keys]`、`queryKeys.libraryStatusAll` = `["library-status"]`、`queryKeys.library` = `["library"]`、`queryKeys.libraryOverview` = `["library","overview"]`、`queryKeys.libraryItems(view: string, sort, page)` = `["library","items",view,sort,page]`
  - `useLibraryOverview(): UseQueryResult<LibraryOverview>`；`useLibraryItems(view: string, sort: LibrarySort, page: number): UseQueryResult<Page<LibraryEntry>>`（都 `retry: false`）
  - `type SaveStatus = { kind: "pending" } | { kind: "anon" } | { kind: "unavailable" } | { kind: "ready"; status: ItemStatus }`
  - `<LibraryStatusProvider keys={string[]}>`；`useSaveStatus(key: string): SaveStatus`
  - `cache.ts`：`patchItemStatus(client: QueryClient, key: string, patch: Partial<ItemStatus>): void`（改所有 `library-status` 查询里该 key 那一行）；`invalidateLibrary(client): Promise<void>`
  - `prefetchLibrary(client: QueryClient, …)` / `getServerQueryClient()`（每次请求一个，`cache()` 包）
  - `ui/popover.tsx`：`<ResponsivePopover open onOpenChange trigger={ReactElement} title?: string align?: "start"|"end">{children}</ResponsivePopover>`：> 880px 为 base-ui Popover（宽 320，可传 `className`），≤ 880px 为底部抽屉（最高 80vh）

- [ ] **Step 1: 写失败的测试**：Provider 在 `useCurrentUser` 为 anonymous 时 `useSaveStatus` → `anon` 且不发请求；authenticated 时发一次 `POST /api/me/library/status`，体内 keys 去重（`dedupes keys and shares status`：两个 `["1","1","2"]` 的消费者只发一次请求、都拿到 `ready`）；请求失败 → `unavailable`；pending 用户 → `pending`。`patchItemStatus` 改到两个包含同一 key 的批次查询、不碰不含它的查询。Popover：桌面模式点触发器打开、`Esc` 关闭后焦点回触发器；`matchMedia` 命中 880 时渲染为 `role="dialog"` 抽屉。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试，全绿**

### Task 8: 收藏按钮与收藏浮层，替换三处入口

**Files:**
- Create: `src/components/library/SaveButton.tsx`、`SavePopover.tsx`、`CollectionChecklist.tsx`、`NoteEditor.tsx`、`src/lib/library/use-login-redirect.ts`
- Modify: `src/components/portal/paper-detail/PaperHeader.tsx`（`<SaveButton variant="pill" paperKey={paper.id} title={…} />`，外层 `LibraryStatusProvider keys={[paper.id]}`）
- Modify: `src/components/portal/PaperCard.tsx`（`variant="icon"`）、`src/components/portal/explore-feed/index.tsx`（`FeedLayout` 外包 Provider，keys = 本屏全部文献 ID）
- Modify: `src/components/portal/papers/PaperListItem.tsx`（加 `action?: ReactNode` 槽：标题行 `grid grid-cols-[minmax(0,1fr)_32px] gap-4`，删「本版边界 D」注释）、`src/components/portal/papers/PaperResults.tsx`（传 `SaveButton`，外包 Provider）
- Delete: `src/store/bookmarks.ts`、`src/components/portal/paper-detail/BookmarkButton.tsx`（并更新 `src/store/README.md`）
- Test: `tests/components/library/SaveButton.test.tsx`、`SavePopover.test.tsx`、`CollectionChecklist.test.tsx`、`NoteEditor.test.tsx`；更新 `PaperListItem.test.tsx`、`PaperResults.test.tsx`、`ExploreFeed.test.tsx`、`PaperHeader.test.tsx`

**Interfaces:**
- Consumes: Task 6 Actions、Task 7 Provider / cache / popover
- Produces:
  - `<SaveButton paperKey: string; title: string; variant: "pill" | "icon" />`
  - `<CollectionChecklist itemIds: string[]; memberOf: (collectionId) => "all" | "some" | "none"; onChanged?: () => void />`（单篇时 `itemIds.length === 1`，只有两态）
  - `<NoteEditor itemId: string; note: string | null; onSaved: (item: LibraryItem) => void; startEditing?: boolean />`
  - `useLoginRedirect(): () => void`（跳 `/login?next=<当前路径+查询>`）

- [ ] **Step 1: 写失败的测试**（文案逐字照简报 §3.1、§3.2、§4.1）：
  - SaveButton：六态外观（`save.idle` 胶囊文字 `收藏`、`saved` 为 `已收藏` 且 `aria-pressed="true"`、`pending-status` 有 `aria-disabled` 点击不调 Action、`anon` 点击跳登录、`unavailable` 文字 `收藏暂不可用`、图标形态 `title="收藏暂不可用"`）；读屏名 `收藏「<标题前 40 字>」` / `已收藏，管理收藏`；点 idle → 立即 `aria-pressed=true` 并打开浮层、调 `collectPublication`；点 saved → 只开浮层不调 Action；`opens popover for collected unavailable item`。
  - SavePopover：头部 `正在收藏…` → `已收进文献库`；已收藏打开 → `已在文献库中` + `10 月 24 日收藏`；`library-full` → `文献库已满 20,000 篇，先移除一些再收藏` 无 `重试`，按钮回 idle；`gone` → `这篇文献已下线，无法收藏`；`catalog-unavailable` → `暂时无法收藏这篇文献，请稍后再试` + `重试`；`unavailable` → `没有收藏成功` + `重试`；`redirects to login on signed-out`；移除：无备注无夹 → 立即 `removeItems`、toast `已从文献库移除` + `撤销`（点撤销再调 `collectPublication`）；有备注且在 2 个夹 → 确认块 `移除后，这篇的备注会删除，也会移出 2 个收藏夹。`，确认后 toast 无撤销；`移除失败` → `没有移除成功，请稍后再试`。
  - CollectionChecklist：无夹 `还没有收藏夹，新建一个来整理文献。`；勾选调 `addToCollection` 并显示进行中；失败回滚 + `没有保存成功`；`skippedFull: 1` → 回滚 + `这个收藏夹已满 5,000 篇`；`itemCount 5000` 且不在里面 → 禁用 + `已满`；9 个夹时出现 `筛选收藏夹`，无结果 `没有匹配的收藏夹`；新建：空名 `请输入名称`、101 字 `名称最多 100 个字`、`name-taken` → `已有同名收藏夹`、`unavailable` → `没有创建成功，请稍后再试`；新建成功的夹排第一行并已勾上；200 个夹时新建禁用 + `最多 200 个收藏夹`；三态：`memberOf` 为 some 时复选框 `indeterminate`。
  - NoteEditor：`+ 添加备注` → 编辑态；计数 `86 / 500`（按字素，emoji 计 1）；401 字时计数 `amber-700`、501 字 `备注最多 500 字` 且 `保存` 禁用；保存中 `保存中…`；失败 `备注没有保存成功，请稍后再试` 且保留输入；清空保存调 `saveNote(id, "")`。
  - PaperListItem：传 `action` 时标题行右端渲染它；不传时结构不变。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**（收藏成功后 `patchItemStatus` + `invalidateLibrary`；勾选成功后改 `collectionIds`；全部不调 `router.refresh()`）
- [ ] **Step 4: 跑 `pnpm test`、`pnpm typecheck`，全绿；`grep -rn "useBookmarkStore\|BookmarkButton" src tests` 无结果**

### Task 9: 复制、导出与复制引用

**Files:**
- Create: `src/lib/use-copy.ts`、`src/lib/download.ts`（`saveResponseAsFile(res: Response): Promise<string>` 解 `filename*` 并触发下载，返回文件名）、`src/lib/remembered-choice.ts`（`localStorage` 读写，try/catch）
- Create: `src/components/library/ExportDialog.tsx`、`CiteDialog.tsx`、`CitationFormatTabs.tsx`、`CitationPreview.tsx`、`CopyButton.tsx`、`CopyLinkField.tsx`
- Test: `tests/lib/use-copy.test.tsx`、`tests/lib/download.test.ts`、`tests/components/library/ExportDialog.test.tsx`、`CiteDialog.test.tsx`、`CopyLinkField.test.tsx`

**Interfaces:**
- Consumes: Task 5 的 `/api/library/keys`、`/api/publications/export`、`/api/publications/citations`
- Produces:
  - `type DialogScope = { kind: "items"; items: { key: string | null; state: "ACTIVE" | "UNAVAILABLE"; pmid: string | null; title: string }[]; label: string; name: string } | (ExportScope & { label: string; name: string; count: number })`
  - `<ExportDialog open onOpenChange scope: DialogScope />`、`<CiteDialog open onOpenChange scope: DialogScope onExport: () => void />`
  - `useCopy(): { copy(text: string, target: HTMLElement | null): Promise<"copied" | "selected">; state: "idle" | "copied" | "selected"; hint: string }`
  - `<CopyLinkField url: string />`（只读输入框 + `复制链接`）
  - `<CitationFormatTabs value onChange />`、`<CitationPreview entries single />`

- [ ] **Step 1: 写失败的测试**：
  - useCopy：`clipboard.writeText` 成功 → `copied`；不存在 / 拒绝 → 选中目标文字后 `execCommand("copy")` 返回 true → `copied`；也失败 → `selected`，提示 Mac 为 `已选中，按 ⌘C 复制`、其他 `已选中，按 Ctrl+C 复制`、触屏 `已选中，长按文字复制`，`aria-live="polite"`；`已复制` 1.6 秒后回 idle（fake timers）。
  - download：`filename*=UTF-8''patra-%E7%BB%84%E4%BC%9A-2026-10-24.ris` → `patra-组会-2026-10-24.ris`。
  - ExportDialog：范围行（`收藏夹「组会」· 23 篇`、`选中的 12 篇`、`这一篇：<标题前 30 字>…`）；四张格式卡与说明；默认 RIS，选 BibTeX 后重开仍是 BibTeX；按钮 `下载 .ris`；keys 回 `skippedUnavailable: 2` → `2 篇已失效，不会导出。`；`tooLarge` → amber `一次最多导出 5,000 篇。整个文献库有 12,480 篇，可以分别导出各个收藏夹和「未分类」。` 且主按钮禁用（未分类超限换成 `可以在列表里选中部分再导出。`）；下载中 `准备中…`；成功关闭 + toast `已开始下载 patra-组会-2026-10-24.ris`；失败不关 + `导出没有成功，请稍后再试`。
  - CiteDialog：标题 `复制引用 · 12 篇` / 单篇 `复制引用`；GB/T 预览为 `[1] …`；APA 无序号；超 100 → `一次最多复制 100 条引用。这个收藏夹有 230 篇，可以选中部分再复制，或者改用导出。` + `导出…` 调 `onExport`、不请求；`2 篇已失效，不含在内。`；失败 `引用暂时生成不了` + `重试`、复制按钮禁用；`复制 12 条引用` 调 `useCopy`。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试，全绿**

### Task 10: 文献库外壳、侧栏、视图、条目与菜单

**Files:**
- Create: `src/app/library/layout.tsx`、`src/app/library/page.tsx`、`src/app/library/unfiled/page.tsx`、`src/app/library/[collectionId]/page.tsx`、`src/app/library/[collectionId]/not-found.tsx`
- Create: `src/components/library/LibraryShell.tsx`、`LibrarySidebar.tsx`、`LibraryViewSwitcher.tsx`、`LibraryView.tsx`、`LibraryItem.tsx`、`LibraryItemMenu.tsx`、`LibraryEmpty.tsx`、`LibrarySkeleton.tsx`、`src/components/library/InteractionUnavailable.tsx`
- Create: `src/lib/library/format.ts`（`collectedLabel(iso)` → `10 月 24 日收藏`、`addedLabel(iso)` → `10 月 24 日加入`，按北京时间；`count(n)` → `1,284`）
- Modify: `src/components/portal/status/NotFoundState.tsx`（`kind="collection"`）
- Test: `tests/app/library-pages.test.tsx`、`tests/components/library/LibrarySidebar.test.tsx`、`LibraryView.test.tsx`、`LibraryItem.test.tsx`、`LibraryItemMenu.test.tsx`、`InteractionUnavailable.test.tsx`、`tests/lib/library/format.test.ts`、`tests/components/portal/status-screens.test.tsx`、`tests/app/route-structure.test.ts`

**Interfaces:**
- Consumes: Task 4 读函数、Task 7 查询与预取、Task 8 `CollectionChecklist` / `NoteEditor`、Task 9 对话框
- Produces: `<LibraryView view: string; sort: LibrarySort; page: number; header: ReactNode />`（Task 11、12 往里加选择模式与收藏夹头部）；`<InteractionUnavailable title: string; body?: string; onRetry?: () => void; retryHref?: string />`

- [ ] **Step 1: 写失败的测试**：
  - 页面：未登录 `/library` → `redirect("/login?next=%2Flibrary")`；`/library/abc` 与不在概览里的收藏夹 → `notFound()`；标题 `我的文献库 · Patra` / `未分类 · 我的文献库 · Patra` / `组会 · 我的文献库 · Patra`。
  - 侧栏：`全部 1,284`、`未分类 312`、收藏夹按 `updatedAt` 倒序、图标读屏 `私有` / `公开`、当前项 `aria-current="page"`；无夹时 `还没有收藏夹` + `新建收藏夹`；`+` 读屏名 `新建收藏夹`。
  - 视图：头部 `全部文献` + `收藏的每一篇都在这里。`、`未分类` + `不在任何收藏夹里的文献。`；工具条排序选项 `加入时间` / `出版日期` / `期刊`，选择后地址变 `?sort=published`；`23 篇`；三种空状态文案与按钮（简报 §3.11 表）；查询失败 → `文献库暂时打不开` + `请稍后再试，你收藏的文献都还在。` + `重试`（调 `refetch`）；加载时 5 行骨架。
  - 条目：标题链到 `/papers/{key}`；日期 `2019 年 3 月`；归属胶囊（收藏夹视图里不含当前夹）；`10 月 24 日收藏`（全部）/ `10 月 24 日加入`（收藏夹视图）；备注 3 行 + `展开`；已失效：标题不是链接、`已失效` 胶囊、`这篇文献已从数据源下线，恢复后会自动显示。`；无摘要。
  - 菜单：项目顺序与文案照简报 §3.10；已失效只有 `编辑备注`、`移出「组会」`、`从文献库移除`；`从文献库移除` 有备注时弹确认对话框，无备注无夹时立即移除 + 撤销 toast；移除后条目从列表消失、概览失效重取。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**（layout 预取概览、page 预取条目页，`HydrationBoundary` 包客户端视图；布局断点 880px）
- [ ] **Step 4: 跑测试，全绿**

### Task 11: 选择模式与批量栏

**Files:**
- Create: `src/components/library/LibrarySelectionBar.tsx`、`src/lib/library/use-selection.ts`
- Modify: `src/components/library/LibraryView.tsx`、`LibraryItem.tsx`（选择模式复选框）
- Test: `tests/components/library/LibrarySelection.test.tsx`

**Interfaces:**
- Consumes: Task 6、8、9、10
- Produces: `useSelection(ids: string[]): { selected: Set<string>; toggle(id); togglePage(); clear(); allSelected: boolean }`

- [ ] **Step 1: 写失败的测试**：点 `选择` → 每条出现复选框，工具条变 `全选本页` · `已选 0 篇` · `完成`；选 1 篇出现 `role="toolbar"` 批量栏（`放进收藏夹…`、`移出「组会」`（仅收藏夹视图）、`复制引用`、`导出`、`移除`），读屏播报 `已选 1 篇`；批量放进的三态勾选调 `addToCollection(id, 选中的 itemIds)`，成功 toast `已放进「组会」12 篇`；`skippedFull: 3` → amber `3 篇没有放进「组会」：收藏夹已满 5,000 篇。`；移出 toast `已从「组会」移出 12 篇`；移除确认 `从文献库移除 12 篇？` + `这些文献的备注会删除，也会移出所有收藏夹。` + `移除 12 篇`，成功 toast `已从文献库移除 12 篇`；`falls back to previous page when page empties`（第 3 页全部移除后地址变 `?page=2`）；`完成` 清空选择。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试，全绿**

### Task 12: 收藏夹头部、公开开关卡、新建 / 编辑 / 删除对话框

**Files:**
- Create: `src/components/library/CollectionHeader.tsx`、`PublicToggleCard.tsx`、`CollectionDialog.tsx`、`DeleteCollectionDialog.tsx`、`src/lib/library/collection-form.ts`（zod schema，字素计长度）
- Modify: `src/app/library/[collectionId]/page.tsx`（服务端读 `fetchMyPrivacy()` 与 `publicUrl("/collections/<id>")`，作为 prop 传入）、`LibrarySidebar.tsx`（`+` 打开新建对话框）
- Test: `tests/components/library/CollectionHeader.test.tsx`、`PublicToggleCard.test.tsx`、`CollectionDialog.test.tsx`、`tests/lib/library/collection-form.test.ts`

**Interfaces:**
- Consumes: Task 6、9（`CopyLinkField`）、10
- Produces: `<PublicToggleCard collection: Collection; emailVerified: boolean; email: string; profileVisibility: "PUBLIC" | "SIGNED_IN" | "SELF" | null; publicLink: string />`；`<CollectionDialog mode: "create" | "edit"; collection?: Collection; open; onOpenChange; onSaved(c: Collection) />`

- [ ] **Step 1: 写失败的测试**：
  - 头部：名称、简介（4 行 + `展开`）、`私有 · 23 篇 · 更新于 2026 年 10 月 24 日`；`···` 菜单 `编辑名称和简介`、`复制公开链接` 与 `在阅读页查看`（仅公开）、`删除收藏夹`。
  - 公开开关卡九态（简报 §3.13 表逐字）：`public.off` 说明；`public.unverified` 开关禁用 + `验证邮箱后才能公开收藏夹。验证邮件已发到 chen.yu@example.com。` + `重新发送验证邮件`；打开 → `设置中…`、开关禁用；成功按 `profileVisibility` 显示 `public.on.public` / `.signed-in` / `.self` 三种说明与 `更改主页可见性`（→ `/settings/privacy`）和链接框；`email-unverified` → 回到关 + 未验证文案；`account-restricted` → `账号状态异常，暂时不能公开收藏夹。`；`unavailable` → `没有设置成功，请稍后再试。` 且回原值；关闭乐观、立即生效、无确认。
  - 对话框：标题 `新建收藏夹` / `编辑收藏夹`；计数 `0 / 100`、`0 / 500`；新建说明 `新建的收藏夹只有你能看到，之后可以设为公开。`；`请输入名称`、`名称最多 100 个字`、`简介最多 500 字`、`已有同名收藏夹`（字段下）、`没有保存成功，请稍后再试`（提示条）；`counts emoji as one grapheme`；提交中 `创建中…` / `保存中…`；新建成功跳 `/library/<新 id>` + toast `已创建收藏夹「组会」`；编辑成功 toast `收藏夹已保存`；删除对话框标题 `删除收藏夹「组会」？`、正文两句（公开的多一句），成功跳 `/library` + toast `已删除收藏夹「组会」`，失败 `没有删除成功，请稍后再试`。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试，全绿**

### Task 13: 公开收藏夹页

**Files:**
- Create: `src/app/collections/[id]/page.tsx`、`src/app/collections/[id]/not-found.tsx`、`src/app/collections/[id]/loading.tsx`
- Create: `src/components/library/PublicCollectionView.tsx`、`CollectionOwnerLine.tsx`、`OwnerNotice.tsx`、`CopyCollectionButton.tsx`、`ShareLinkPopover.tsx`
- Test: `tests/app/collection-page.test.tsx`、`tests/components/library/PublicCollectionView.test.tsx`、`CopyCollectionButton.test.tsx`

**Interfaces:**
- Consumes: Task 4（`fetchPublicCollection`、`fetchPublicCollectionPage`）、Task 6（`copyCollection`）、Task 8（`SaveButton`、Provider）、Task 9（对话框、`CopyLinkField`）、Task 10（`InteractionUnavailable`、`LibraryItem` 的已失效外观）

- [ ] **Step 1: 写失败的测试**：`id=abc` / 404 → `notFound()`（`没有找到这个收藏夹`）；503 → `收藏夹暂时打不开` + `重试`（不是 404）；匿名：`登录后复制` 链到 `/login?next=%2Fcollections%2F9`、条目按钮 `save.anon`；别人：`复制为我的收藏夹` → `复制中…` → toast `已复制为「组会 (2)」：新收进 18 篇，5 篇原本就在文献库` + `去看看`（→ `/library/<新 id>`），`skippedUnavailable: 2` 时末尾 `，2 篇已失效没有复制`，`alreadyInLibrary: 0` 时省掉后半句；错误提示条四种（简报 §4.6）；本人：提示条（公开 `这是你的收藏夹，别人看到的就是这样。` / 私有 `这个收藏夹是私有的，只有你能看到。`）+ `在文献库中管理`；`owner: null` 的本人视角用当前用户画所有者行；所有者已注销 → `已注销用户` 且无 `@`；不显示备注；空 `这个收藏夹还没有文献。`；`?sort=journal` 透传。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试，全绿**

### Task 14: 主页「收藏夹」标签

**Files:**
- Create: `src/components/profile/ProfileTabs.tsx`、`ProfileCollectionCard.tsx`、`ProfileCollections.tsx`、`src/app/u/[username]/collections/page.tsx`
- Modify: `src/app/u/[username]/page.tsx`（`Suspense` 里条件渲染标签栏）
- Test: `tests/components/profile/ProfileTabs.test.tsx`、`ProfileCollections.test.tsx`、`tests/app/user-collections-page.test.tsx`、更新 `tests/app/user-page.test.tsx`

- [ ] **Step 1: 写失败的测试**：「关于」页本人视角总有两个标签（链接 `/u/x` 与 `/u/x/collections`，无数字）；别人视角接口回空或失败 → 无 `tablist`；回 1 个 → 有。`/u/x/collections`：旧名 → `redirect("/u/<新名>/collections")`；主页 404 → `notFound()`；卡片名称、简介两行、`23 篇 · 更新于 10 月 24 日`、整卡链到 `/collections/<id>`；本人提示 `这里只显示公开的收藏夹。` + `管理收藏夹`；本人空 `还没有公开的收藏夹` + `在文献库里把收藏夹设为公开，它就会出现在这里。` + `去文献库`；别人空只有 `还没有公开的收藏夹`；失败 `收藏夹暂时打不开` + `重试`；标题 `陈宇 (@chen-yu) 的收藏夹 · Patra`，不可收录时 `noindex`。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试，全绿**

### Task 15: 导航入口

**Files:**
- Modify: `src/components/portal/NavAccount.tsx`、`UserMenu.tsx`、`MobileAccount.tsx`
- Test: `tests/components/portal/NavAccount.test.tsx`、`MobileAccount.test.tsx`、`TopNav.test.tsx`

- [ ] **Step 1: 写失败的测试**：已登录桌面有 `文献库` 链接到 `/library`，路径 `/library/unfiled` 时 `aria-current="page"`；未登录没有；用户菜单项顺序 `个人主页`、`我的文献库`、`设置`、`退出登录`；移动账号块三个按钮 `主页`、`文献库`、`设置` + 整行 `退出登录`。
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试，全绿**

### Task 16: e2e、全量门禁与验收截图

**Files:**
- Create: `tests/e2e/library.spec.ts`、`tests/e2e/support/library.ts`（`saveFrom(page, place)`、`openLibrary(page)` 等小工具）

- [ ] **Step 1: 写 e2e**（spec §7 的 1 ~ 4；`3` 在没有 `PATRA_E2E_VERIFIED_EMAIL` 时 `test.skip` 并写明原因）
- [ ] **Step 2: 跑** — `PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528 pnpm test:e2e tests/e2e/library.spec.ts --workers=1` → 全部通过（3 视环境 skip）
- [ ] **Step 3: 全量门禁** — `pnpm lint`、`pnpm typecheck`、`pnpm test` 全绿
- [ ] **Step 4: 截图** — `preview_start portal-dev`；或 Playwright 无头截图：详情页、首页、检索结果、收藏浮层、文献库三视图、收藏夹头部与公开开关、导出 / 引用对话框、公开收藏夹页三视角、主页收藏夹标签，各 1440×900 与 375×812
- [ ] **Step 5: 停在本地等用户验收**（不 commit、不 push）
