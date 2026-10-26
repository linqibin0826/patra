# 门户「收藏与文献库」工程设计（PAP-114）

> 版本：v0.13 Library · Issue：Linear PAP-114（GH #284）· 日期：2026-10-25
> 产品定义：`docs/patra/release-specs/v0.13-library.md` §2.1、决策 D / E / F / H / I
> 页面、状态 ID 与文案：`patra-portal/docs/design-briefs/v0.13/library.md`（下称「简报」）；画布 <https://claude.ai/artifact/BoLjKpkaQhzotxdiNJdeP6>
> 后端契约：interaction `/patra-interaction/v3/api-docs`（mini 上已部署，与共享笔记 `PAP-113-collection-contract.md` 一致）；catalog 卡片 / 导出 / 引用见 `PAP-110-*` 笔记；identity 见 `PAP-108-identity-contract.md`
> 本文只写门户怎么实现。页面长什么样、每个状态的文案以简报为准，这里不重抄。

---

## 1. 目标与范围

医生在检索结果、首页、文献详情页点「收藏」就把文献收进自己的文献库，在浮层里放进收藏夹、写备注；在 `/library` 整理、批量操作、导出、复制引用；把收藏夹设为公开，别人在 `/collections/{id}` 和主页「收藏夹」栏看到并复制。

### 1.1 本 Issue 做

| 块 | 内容 |
|---|---|
| 公共层 | 服务端调用 interaction 的封装（必须登录 / 登录可选两种）；批量「我的状态」 |
| 契约跟进 | 头像地址按用户 ID；公开主页 schema 加 `userId`；`publishedDate` 与按精度格式化；删 `bookmarks` 字段与事实条「收藏」格 |
| 收藏入口 | `SaveButton` 三种外形 + `SavePopover`（勾选收藏夹、行内新建、备注、移除 / 撤销）；详情页页头、首页 `PaperCard`、检索结果 `PaperListItem`、公开收藏夹页条目；删 `store/bookmarks.ts` 与 `BookmarkButton` |
| 文献库 | `/library`、`/library/unfiled`、`/library/[collectionId]`：侧栏 / 移动视图切换、条目（备注、归属、已失效）、排序、分页、条目菜单、选择模式与批量栏、收藏夹新建 / 编辑 / 删除、公开开关卡 |
| 导出与复制引用 | `ExportDialog`、`CiteDialog`（文献库各视图、条目菜单、批量栏、公开收藏夹页）、`useCopy` 三步降级 |
| 公开收藏夹 | `/collections/[id]`：三种视角、复制为我的收藏夹、导出、复制引用、复制链接、404 / 错误 |
| 主页 | `ProfileTabs`「关于 · 收藏夹」、`/u/[username]/collections` |
| 导航 | 顶栏「文献库」、用户菜单「我的文献库」、移动账号块三按钮 |
| 降级 | 每个互动区块独立 Suspense / 查询 + 超时 + 失败降级；交互只改本地缓存，不调 `router.refresh()` |

### 1.2 不做（属 PAP-115）

文献页的分享面板 `SharePanel`（含单篇导出、复制链接、复制引用）、文献页 / 期刊页 / 公开收藏夹页的 `generateMetadata`（标题、描述、canonical、`noindex`）、两个账号的冒烟用例。

**边界说明（我定的）**：简报 §2.1 把 `CiteDialog` 记在 PAP-115 名下，但 PAP-114 的 Issue To-Do 写了「导出与复制引用面板」，Issue 为准：文献库与公开收藏夹页里的复制引用对话框、它依赖的 `useCopy`、公开开关卡与公开页的「复制链接」都在本 Issue 做；PAP-115 复用 `useCopy`、`CitationFormatTabs`、`CitationPreview` 和 catalog 转发路由去做文献页的分享面板。

### 1.3 反目标

- 页面上出现任何「N 人收藏」、被复制次数、浏览数，主页标签带个数（简报 §3.7、§7）。
- 收藏、勾选这类交互之后整页重新渲染（`router.refresh()` 会把详情页的全文重取一遍，决策 H）。
- interaction 出问题拖垮文献页、检索页、首页、主页的主体。
- 门户直接按用户 ID 查人、自己实现可见性规则（决策 E / F：都由 interaction 透传 identity 的判定）。

---

## 2. 数据流

```
浏览器 ──(RSC 渲染)──────────────► 门户服务端 ──gatewayFetch──► 网关 ──► interaction / catalog / identity
   │                                 ▲
   ├─ TanStack Query ─► Route Handler ┤   读：我的状态、文献库概览、条目页、导出范围的 keys
   ├─ Server Action ─────────────────┘   写：收藏、移除、备注、收藏夹增删改、公开、放进 / 移出、复制
   └─ fetch 下载 ──► Route Handler ──gatewayStream──► catalog 导出（流式）
```

- **会话只在需要时带**（决策 H）：catalog 卡片、导出、引用不带令牌；interaction 的 `/me/**` 必须带；`/collections/**`、`/users/{userId}/collections` 是「登录可选」，有令牌就带（可见性取决于访客）。
- **门户是 BFF**：文献库条目 = interaction 一页条目 + 一次 catalog `GET /portal/publications/batch?id=…` 卡片，在门户服务端拼好再给页面；一屏只发一次批量请求，没有 N+1。

### 2.1 读路径与缓存

服务端数据一律进 TanStack Query 缓存（`.claude/rules/state.md`）；首屏由 RSC 在服务端预取，经 `HydrationBoundary` 交给客户端，之后的失效重取走 Route Handler。两条路径调同一个服务端函数，不写两份。

| 查询 | queryKey | 首屏 | 客户端重取 | 超时 |
|---|---|---|---|---|
| 我的状态（一屏一批，≤ 100 个 key） | `["library-status", ...keys]` | 不预取（不阻塞页面） | `POST /api/me/library/status` | 3 s（简报建议 1.5 s，mini 实测有 2.5 s 的毛刺，见 §8） |
| 文献库概览（全部 / 未分类计数 + 全部收藏夹） | `["library", "overview"]` | `/library/**` 布局预取 | `GET /api/me/library` | 5 s |
| 文献库条目页 | `["library", "items", view, sort, page]`，`view` 为 `all` / `unfiled` / 收藏夹 ID | `/library/**` 页面预取 | `GET /api/me/library/items?view&sort&page` | 5 s |
| 公开收藏夹头部与条目 | — | RSC 直接渲染（不进缓存，没有本地写入） | — | 5 s |
| 主页公开收藏夹列表 | — | RSC 直接渲染 | — | 5 s |
| 导出 / 复制引用的范围 keys | 不缓存，对话框打开时取 | — | `POST /api/library/keys` | 5 s |

- 我的状态、概览、条目页的查询都 `retry: false`：失败就显示降级，不让用户等三次重试。
- 我的状态只在 `useCurrentUser()` 为 `authenticated` 时发；`pending` 时按钮是 `save.pending-status`，`anonymous` 时是 `save.anon`，都不请求。
- 首屏预取失败（超时、5xx）时不抛错：`prefetchQuery` 吞掉错误，客户端挂载后照常请求一次；再失败才显示错误块。这样「重试」按钮就是 `refetch()`，不需要 `router.refresh()`。

### 2.2 写路径

Server Action（`"use server"`）调 interaction，返回带 `reason` 的结果联合（同 `profile-actions.ts`：不带文案，组件按 `reason` 选文案）。客户端拿到成功结果后：

1. `setQueriesData({ queryKey: ["library-status"] }, …)` 改所有屏里这条文献的状态（多个批次的查询里只要含这个 key 就改）；
2. 改得动的缓存就地改（概览里的计数与收藏夹、当前条目页里的条目）；
3. 其余 `invalidateQueries({ queryKey: ["library"] })` 失效，用到的视图自己重取。

乐观与否照简报 §6：收藏、浮层勾选、移出收藏夹、关掉公开先改界面，失败回滚并就地报错；备注、收藏夹增删改、打开公开、批量、复制、导出等返回。

---

## 3. 公共层

### 3.1 `src/lib/interaction/request.ts`

照 `callIdentity`：

```ts
export async function callInteraction(
  path: string,                                   // 服务内路径，如 `/me/library`
  init?: Omit<GatewayRequestInit, "token" | "client">,  // 含 timeoutMs
  options?: { auth?: "required" | "optional" },  // 默认 required
): Promise<InteractionReply>                      // ok / problem / no-session / error，同 IdentityReply
```

- 端点前缀 `/patra-interaction`；带浏览器来源；`catch` 里先 `unstable_rethrow`（RSC 里调用时动态渲染信号不能吞）。
- 复用 `parseProblem`、`isSessionRejected`、`settleFailure`、`unexpectedProblem`（`settleFailure` 认 `PENDING_DELETION` 在 interaction 不会出现，无害）。interaction 的 503 区分两种：`code === "INTERACTION-0503"` 是 catalog 不可用（首次收藏），其余 503（含网关自己的、`DEP_UNAVAILABLE`）是服务不可用。

### 3.2 响应 schema：`src/lib/interaction/schema.ts`

zod 定义 `Collection`、`LibraryOverview`、`LibraryItem`、`ItemStatus`、`CollectResult`、`AddToCollectionResult`、`PublicCollection`（`owner` 可为 null）、`PublicItem`、`PublicCollectionCard`、`CopyResult`、`ExportKeys`、`Page<T>`。ID 一律字符串；时间是 ISO 字符串。解析失败按「意外响应」记日志、当服务不可用。

### 3.3 服务端读函数：`src/lib/interaction/library.ts`、`collections.ts`

| 函数 | 调用 | 说明 |
|---|---|---|
| `fetchLibraryStatus(keys)` | `POST /me/library/status` | 1.5 s；未登录 → `signed-out` |
| `fetchLibraryOverview()` | `GET /me/library` | |
| `fetchLibraryPage(view, sort, page)` | `GET /me/library/items` 或 `/me/collections/{id}/items` | 再批量取卡片，拼成 `LibraryEntry { item, paper: Paper \| null }`；收藏夹不是自己的 → `not-found` |
| `fetchPublicCollection(id)` | `GET /collections/{id}` | 登录可选；404 → `not-found`；其余失败 → `unavailable`（不是 404，简报 §3.17） |
| `fetchPublicCollectionPage(id, sort, page)` | `GET /collections/{id}/items` + 卡片 | 同上 |
| `fetchUserPublicCollections(userId)` | `GET /users/{userId}/collections` | 登录可选；不可见回空列表 |
| `fetchScopeKeys(scope)` | `GET /me/library/keys` / `/me/collections/{id}/keys` / `/collections/{id}/keys` | 422 `EXPORT_TOO_LARGE` → `too-large` |

- 卡片：`fetchPublicationCards(ids)`（`src/lib/portal-api/publications.ts` 新增）调 `GET /patra-catalog/portal/publications/batch?id=…`，不带会话；只送 `ACTIVE` 且有 `key` 的。catalog 回来缺了某条（刚被软删、对账还没跑）时，这条按「已失效」外观显示，标题用登记表快照，没有快照写 `标题暂不可用`（我定的）。卡片批量失败时整页按失败处理（条目没有可展示的数据）。
- 页面参数：`sort` 只认 `added` / `published` / `journal`，其余回落 `added`；`page` 为正整数，其余回落 1；收藏夹 ID 只认 `^[1-9]\d{0,18}$`，不合格直接 404 不打网关。每页 50。

### 3.4 Route Handlers（客户端重取用，都 `dynamic = "force-dynamic"`）

| 路由 | 用途 | 未登录 |
|---|---|---|
| `POST /api/me/library/status` | `{ keys: string[] ≤ 100 }` → `{ items }` | 401 |
| `GET /api/me/library` | 概览 | 401 |
| `GET /api/me/library/items?view&sort&page` | `{ page: Page<LibraryEntry> }` | 401 |
| `POST /api/library/keys` | `{ scope }` → `{ keys, skippedUnavailable }` 或 `{ tooLarge: true }`；`scope` 见 §6.1 | 公开收藏夹范围不要求登录 |
| `POST /api/publications/export` | `{ ids ≤ 5000, format, name }` → 流式转发 catalog 的文件与 `Content-Disposition` | 不要求 |
| `POST /api/publications/citations` | `{ ids ≤ 100, style }` → `{ entries }` | 不要求 |

请求体用 zod 校验，不合格 422 不打网关；只接受同源请求（沿用 `same-origin.ts`）。导出转发扩展 `gatewayStream`：加可选的 `method: "POST"` 与 `json`，超时仍只计到响应头。

### 3.5 Server Actions：`src/lib/interaction/library-actions.ts`、`collection-actions.ts`

| Action | interaction | 成功 | 失败 `reason` |
|---|---|---|---|
| `collectPublication(key)` | `PUT /me/library/publication/{key}` | `{ created, item }` | `library-full`（409 `LIBRARY_FULL`）、`gone`（404）、`catalog-unavailable`（503 `INTERACTION-0503`）、公共 |
| `removeItems(itemIds)` | `DELETE /me/library/items?itemIds=` | — | 公共 |
| `saveNote(itemId, note)` | `PUT /me/library/items/{id}/note` | `item` | `invalid-fields`（`note` × `TOO_LONG` / `INVALID_CHARACTER`）、`gone`、公共 |
| `createCollection(name, description)` | `POST /me/collections` | `collection` | `invalid-fields`、`name-taken`、`collection-limit`、公共 |
| `updateCollection(id, name, description)` | `PUT /me/collections/{id}` | `collection` | 同上 + `gone` |
| `setVisibility(id, visibility)` | `PUT /me/collections/{id}/visibility` | `collection` | `email-unverified`、`account-restricted`、`gone`、公共 |
| `deleteCollection(id)` | `DELETE /me/collections/{id}` | — | 公共 |
| `addToCollection(id, itemIds)` | `POST /me/collections/{id}/items` | `{ added, alreadyIn, skippedFull, skippedNotInLibrary }` | `gone`、公共 |
| `removeFromCollection(id, itemIds)` | `DELETE /me/collections/{id}/items` | — | `gone`、公共 |
| `copyCollection(id)` | `POST /collections/{id}/copies` | `CopyResult` | `not-visible`（404）、`library-full`、`collection-limit`、公共 |

「公共」= `signed-out`（无会话或 401，清 Cookie）/ `unavailable`。原因码按 `traits` 判（不解析 `message`）。客户端拿到 `signed-out` 一律跳 `/login?next=<当前路径>`。入参先按形状校验（ID 正则、数组 1 ~ 100），不合格按 `unavailable` 处理。

### 3.6 门户公开地址

「复制链接」要绝对地址，且不能用 `window.location.origin`（本机、tailscale IP、主机名会复制出不同链接，简报 §3.18）。服务端配置 `PATRA_PORTAL_PUBLIC_URL`（如 `http://linqibins-mac-mini:4000`），`src/lib/public-url.ts` 的 `publicUrl(path)` 拼地址，由 RSC 作为 prop 传给客户端组件；没配时用当前请求的 `Host` 拼 `http://` 地址。写进 `.env.example`。PAP-115 的 canonical 用同一个函数。

**mini 上本版不设这个配置**（2026-10-26 用户定）：mini 现在只在 tailnet 里访问，链接按请求的 `Host` 拼；等门户开放公网、有了固定域名时再设。

---

## 4. 契约跟进（小改动）

1. **出版日期**：`Paper`、`PaperDetail` 加 `publishedDate: string | null`；`src/lib/published-date.ts`：
   - `publishedYear(d)` → `2019`（首页卡片、检索结果列表项，无日期时回落 `year` 字段）；
   - `formatPublishedDate(d)` → `2019` / `2019 年 3 月` / `2019 年 3 月 15 日`（按长度 4 / 7 / 10；形状不对返回 null）；详情页作者行、文献库条目、公开收藏夹条目用它；
   - 「文献档案」的 `dates[].date` 已是降精度 ISO，原样显示，不改。
   数字 `tabular-nums`。
2. **删 `bookmarks`**：`Paper.bookmarks`、`PaperDetail.bookmarks` 与 `PaperFacts` 的「收藏」格；测试夹具同步。
3. **头像按用户 ID**：`avatar.ts` 正则改为 `^/users/([1-9][0-9]{0,18})/avatar\?v=([0-9a-f]{16})$`；路由目录 `api/users/[username]/avatar` → `api/users/[userId]/avatar`，按 `^[1-9][0-9]{0,18}$` 校验；`users-avatar.test.ts`、`avatar.test.ts`、`route-structure.test.ts` 跟着改。
4. **公开主页**：`publicProfileSchema` 加 `userId: z.string()`，注释里「不含用户 ID」删掉。
5. **`Avatar`**：所有者已注销时没有昵称、用户名——`CollectionOwnerLine` 自己画中性占位（`ink-200` 底、无字），不改 `AvatarIdentity` 的类型。

---

## 5. 组件

新组件放 `src/components/library/`（收藏、文献库、收藏夹、导出与引用）；主页标签放 `src/components/profile/`；原语放 `src/components/ui/`。

### 5.1 原语

- **`ui/popover.tsx`**：桌面是 base-ui `Popover`（锚在触发按钮下方，`radius-lg`、`shadow-lg`、`paper-50`），≤ 880px 改为底部抽屉（复用 `Dialog` 的抽屉外观，高度随内容、最高 80vh）。`useMediaQuery("(max-width: 880px)")` 选形态。关闭后焦点回到触发按钮。`CitationPopover` 不迁移（它是正文里的悬停引用，不需要抽屉形态）。
- **`NotFoundState`** 加 `kind="collection"`：标题 `没有找到这个收藏夹`，正文 `地址可能有误，或者这个收藏夹没有对你公开。`。
- **`InteractionUnavailable`**：`CloudOff` + 标题 + 说明 + `重试`（次按钮）。`onRetry` 有值时调它（客户端查询的 `refetch`）；没有时（RSC 区块）`重试` 是指向当前地址的链接，重新进页面（我定的：RSC 区块没有客户端缓存可重取，整页重进是唯一的重试；这不是交互后的刷新，不违反决策 H）。

### 5.2 收藏

- **`LibraryStatusProvider`**（客户端）：RSC 列表用它包住，传本屏的 keys。它发一次批量查询，向下提供 `statusOf(key)`：`pending` / `anon` / `unavailable` / `{ collected, itemId, collectedAt, collectionIds, hasNote }`。
- **`SaveButton`**：`variant: "pill" | "icon"`；状态 `save.idle / saved / pending-status / anon / unavailable / open`（简报 §3.1）。点 `idle` 立即乐观收藏并打开浮层；点 `saved` 只打开浮层；`anon` 跳登录（`next` 为当前路径，回来不自动收藏）。读屏名称、`aria-pressed`、悬停 `title` 照简报。
- **`SavePopover`**：头部（`正在收藏…` / `已收进文献库` / `已在文献库中` + 收藏日期 / 收藏失败四种）+ `CollectionChecklist` + `NoteEditor` + 移除（无备注且不在夹里 → 立即移除 + toast 撤销；否则就地确认，toast 不带撤销）。收藏失败时下面三块不出（没有条目可整理）。
- **`CollectionChecklist`**：数据来自概览查询（浮层打开时才请求，一次取全）；每行复选框勾上 / 取消立即生效（乐观、行内 12px 转圈、失败回滚 + `没有保存成功`）；`skippedFull > 0` → `这个收藏夹已满 5,000 篇` 并回滚；≥ 9 个时本地筛选框；满 5,000 且不在里面的行禁用 + `已满`；行内新建（自动聚焦，`Esc` 收起，创建后排第一行并自动放进去）；已有 200 个时新建禁用。三态模式（批量栏用）：全部在 / 部分在（`indeterminate`）/ 都不在。
- **`NoteEditor`**：展示态（3 行截断 + `编辑` / `+ 添加备注`）与编辑态（4 → 8 行自动增高、字素计数、剩 20% 变 `amber-700`、超出 `status-danger` 且保存禁用）；不乐观，`保存中…`；清空保存 = 删除备注。浮层与文献库条目共用。
- 三处入口：`PaperHeader` 操作行（胶囊；≤ 540px 的竖排紧凑形态在 PAP-115 加「分享」时一起改，本 Issue 只换按钮，不改操作行布局——我定的，操作行方案 A 的三等分要等「分享」按钮存在才成立）；`PaperCard` 底栏最右端（图标）；`PaperListItem` 标题行右端（图标，`grid-cols-[minmax(0,1fr)_32px] gap-4`，注释「本版边界 D」改掉）。`PaperListItem` 加 `action?: ReactNode` 槽，检索结果传 `SaveButton`，文献库条目传 `···` 菜单。

### 5.3 文献库

- **路由**：`src/app/library/layout.tsx`（TopNav + 外壳 + 侧栏；预取概览）；`library/page.tsx`（全部）、`library/unfiled/page.tsx`、`library/[collectionId]/page.tsx`；各页 `requireUser(path)` 守卫（未登录跳 `/login?next=`）、预取本页条目；`[collectionId]` 不在概览的收藏夹列表里 → `notFound()`（`NotFoundState kind="collection"`）。浏览器标题照简报 §3.9。
- **`LibraryShell`**：h1 `我的文献库`；> 880px 两栏（侧栏 240px 吸顶 + 内容列）；≤ 880px 视图切换按钮 + 底部抽屉。
- **`LibrarySidebar`**：全部 / 未分类 / 收藏夹小标题 + `+` / 各收藏夹（图标、名称截断、篇数）；当前项样式同设置页分区导航；`usePathname` 判当前项。
- **`LibraryView`**（客户端）：视图头部（`全部文献` / `未分类` / `CollectionHeader`）+ 工具条（`选择`、排序下拉写 `?sort=`、篇数）+ 条目列表 + `BrowsePagination`（`unit="篇"`）+ 空状态 + 选择模式 + `LibrarySelectionBar`。排序、翻页走 `Link`/`router.push`（换 URL = 换查询键，不是刷新）。
- **`LibraryItem`**：`PaperListItem` 结构 + 归属胶囊（当前收藏夹视图里去掉当前夹）+ `10 月 24 日收藏` / `10 月 24 日加入` + 备注块（`NotebookPen`、3 行 + `展开`）+ 已失效形态；不显示摘要与证据徽章。
- **`LibraryItemMenu`**（`dropdown-menu`）：放进收藏夹…（锚在菜单按钮上的 `CollectionChecklist` 浮层）、编辑备注（条目内展开 `NoteEditor`）、复制引用、导出…、移出「x」（收藏夹视图）、从文献库移除（有备注或在夹里时用确认对话框，否则立即移除 + 撤销 toast）。已失效条目只有编辑备注、移出、移除。
- **选择模式与 `LibrarySelectionBar`**：`role="toolbar"`，吸底；放进收藏夹…（三态清单）、移出「x」、复制引用、导出、移除（确认对话框）；部分因满未放 → 列表上方 `amber` 提示条。只在当前页内选。
- **`CollectionHeader` + `PublicToggleCard`**：名称、简介（4 行 + `展开`）、元信息行；右端复制引用 / 导出 / `···`（编辑名称和简介、复制公开链接、在阅读页查看、删除收藏夹）；公开开关卡九种状态见简报 §3.13。`public.unverified` 由当前用户的 `emailVerified` 提前判；`public.on.*` 的三种说明由 `/me/privacy` 的主页可见性决定（`[collectionId]` 页面在服务端读一次 `fetchMyPrivacy()`，作为 prop 传入；读不到时按 `public.on.public` 显示）。打开公开不乐观（`设置中…`），关闭乐观。
- **`CollectionDialog` / `DeleteCollectionDialog`**：RHF + zod（名称 1 ~ 100 字素、简介 ≤ 500 字素）；后端字段错误与 `name-taken` 落在字段下；新建成功 → 进新收藏夹 + toast；删除成功 → 跳 `/library` + toast。
- **计数与缓存**：移除、放进、移出之后，概览（全部、未分类、各夹篇数）与条目页失效重取；`updatedAt` 排序靠概览重取。

### 5.4 导出与复制引用

- **范围** `ExportScope`：`{ kind: "items", items: { key, title }[], label }`（单篇、选中）/ `{ kind: "library", view: "all" | "unfiled", sort }` / `{ kind: "collection", id, sort }`（自己的）/ `{ kind: "public-collection", id, sort }`（公开页）。范围标签与文件名用的 `name` 照简报 §3.6 与附录 A：收藏夹名 / `未分类` / `library` / `selection` / 单篇的 PMID（没有 PMID 用文献 ID）。
- **`ExportDialog`**：打开时按范围取 keys（`items` 范围直接用已有 key、滤掉已失效），算出 `skippedUnavailable`；`tooLarge` 或 keys > 5,000 → 超上限提示条、主按钮禁用；2×2 格式卡（移动 1 列），记住上次的格式（`localStorage`，键 `patra.export-format`）；`下载 .ris` → `准备中…` → `fetch` 拿 blob → 用 `Content-Disposition` 的 `filename*` 解出文件名 → `<a download>` 触发下载 → 关对话框 + toast `已开始下载 <文件名>`；失败不关，提示条 `导出没有成功，请稍后再试`。
- **`CiteDialog`**：同样先取 keys；> 100 不请求，显示说明 + `导出…`（关闭本框、以同一范围打开导出框）；`CitationFormatTabs`（四项，记住上次，键 `patra.citation-style`）+ `CitationPreview`（`label + 空格 + text`，单篇只用 `text`；最高 360px 滚动；加载两行骨架；失败 `引用暂时生成不了` + `重试`）；`复制 N 条引用` 用 `useCopy`。
- **`useCopy`**（简报 §3.8）：`navigator.clipboard.writeText` → 选中目标元素文字后 `document.execCommand("copy")` → 都失败时保持选中、按钮位置换成 `已选中，按 ⌘C 复制`（按 `navigator.platform` 判 Mac / 其他、`(pointer: coarse)` 判触屏），`aria-live="polite"`。成功时按钮内 `已复制` 1.6 秒后恢复。

### 5.5 公开收藏夹页 `/collections/[id]`

- RSC：并行取会话与公开收藏夹头部（`Suspense` 包住头部 + 列表整块，fallback 骨架）；`not-found` → `notFound()`；`unavailable` → `InteractionUnavailable`（`收藏夹暂时打不开`）。
- 视角：`viewerIsOwner` → 本人提示条（公开 / 私有两种）+ `在文献库中管理`；`owner` 为 null 且本人视角时，所有者行用当前用户（`lookupSession`）画；匿名 → `登录后复制`（链接到 `/login?next=/collections/<id>`）；已登录别人 → `复制为我的收藏夹`（Server Action，`复制中…`，toast 带 `去看看`；错误提示条照简报 §4.6，`not-visible` 时提示后整页重进得到 404）。
- 条目：`PaperListItem` + 访客自己的 `SaveButton`（`LibraryStatusProvider` 包住本页 keys），不显示所有者备注；已失效条目同文献库外观、无菜单；排序 `?sort=`、分页 `?page=`。
- 头部操作：导出、复制引用（范围 `public-collection`，匿名也能用）、`分享`（本 Issue 只做「复制链接」浮层；PAP-115 再统一成 `SharePanel` 的链接块）。
- 所有者已注销：中性占位 + `已注销用户`，无 `@用户名`。

### 5.6 主页标签

- `ProfileTabs`（`tabs` 原语的下划线样式，标签是链接）：`关于`（`/u/<用户名>`）、`收藏夹`（`/u/<用户名>/collections`），不带个数。
- `/u/[username]`：本人视角总出两个标签；别人视角在 `Suspense`（fallback 为空）里取 `fetchUserPublicCollections(profile.userId)`，至少一个才渲染标签栏；失败或空都不渲染。
- `/u/[username]/collections`（新页面，主页的取数与旧名跳转同 `/u/[username]`，跳转目标带 `/collections`）：总渲染两个标签；卡片网格（桌面 2 列、移动 1 列）；本人视角提示行与空状态；别人视角空 → `还没有公开的收藏夹`；失败 → `收藏夹暂时打不开` + `重试`（两种视角一样）。标题 `昵称 (@用户名) 的收藏夹 · Patra`，`indexable` 为 false 时 `noindex`（与主页一致）。

### 5.7 导航

- `NavAccount`（已登录、> 880px）：头像左边 `文献库`（`Bookmark` 15px + 文字，`btnText` 系；`/library/**` 时 `fg-1` + 下划线，其余 `fg-2`）。
- `UserMenu`：`个人主页`、`我的文献库`（`Library`）、`设置` | `退出登录`。
- `MobileAccount`：`主页`、`文献库`、`设置` 三等分，下面整行 `退出登录`。

---

## 6. 降级与超时（决策 H，简报 §3.17）

| 位置 | 实现 | 失败呈现 |
|---|---|---|
| 收藏按钮 | 客户端批量查询，路由里 3 s 超时，`retry: false` | `save.unavailable`；页面其余照常（文献页主体、检索结果、首页都是 catalog 的 RSC，与 interaction 无关） |
| 收藏浮层写入 | Server Action | 就地错误，不关浮层 |
| 文献库侧栏 / 列表 | 预取 + 客户端查询，5 s | 侧栏与列表位置一块 `InteractionUnavailable`（`文献库暂时打不开` + `请稍后再试，你收藏的文献都还在。`），导航与 h1 照常 |
| 公开收藏夹页 | RSC，5 s | `收藏夹暂时打不开` + `重试`（不是 404） |
| 主页收藏夹标签 | RSC，5 s | 「关于」上：不渲染标签栏；`/collections` 上：错误块 |

catalog 卡片失败与 interaction 失败在文献库与公开页同样处理（这一块没有可展示的数据）。

---

## 7. 测试

前端 TDD：每个任务先写失败的测试。

- **单元**（Vitest，`tests/` 镜像路径）：`published-date`、`avatar`、`public-url`；`callInteraction`（令牌、可选登录、`unstable_rethrow`、错误归类）；schema；每个 Server Action 的原因码映射（mock `gatewayFetch`）；Route Handler 的校验、401、超时、流式转发与响应头；`fetchLibraryPage` 的卡片拼装与缺卡兜底。
- **组件**（Testing Library，`getByRole` 优先）：简报 §4 的状态 ID 各一条——`SaveButton` 六态、`SavePopover` 头部四态与移除两种、`CollectionChecklist`（勾选成功 / 失败回滚 / 满 / 新建三种错误 / 筛选 / 三态）、`NoteEditor`（计数、超长、保存失败）、`LibraryItem` 各形态、`PublicToggleCard` 九态、`ExportDialog`（默认 / 超限 / 跳过 / 失败）、`CiteDialog`（默认 / 超限 / 跳过 / APA 无序号）、`useCopy` 三步、`ProfileTabs` 显隐规则、公开收藏夹页三种视角与 404 / 错误、导航三处入口。
- **e2e**（Playwright，连 mini：`PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528`，`--workers=1`）：
  1. 新注册账号（邮箱未验证也能收藏）：检索结果、首页、详情页各收藏一篇 → 刷新三处仍是已收藏 → 文献库「全部」有这三篇；建「组会」、放进两篇、写备注 → 刷新仍在；删「组会」→ 三篇仍在、归入「未分类」（AC 1、D4 的门户部分）。
  2. 互动不可用：用 `page.route` 让 `/api/me/library/status` 失败 → 详情页主体正常、按钮显示 `收藏暂不可用`；让 `/api/me/library` 失败 → 文献库错误块（AC 4 的门户部分；停容器的实测在 mini 上手动做，见 §11）。
  3. 公开收藏夹可见性（AC 3）：需要一个**邮箱已验证**的账号 A。用用户自己的账号，凭据放 `~/.patra/secrets/e2e.env`（`PATRA_E2E_VERIFIED_EMAIL`、`PATRA_E2E_VERIFIED_PASSWORD` 两行，不进仓库、不打印），跑 Playwright 前 `set -a; source ~/.patra/secrets/e2e.env; set +a`。A 为测试新收藏一篇、建夹设公开 → 匿名与新账号 B 能打开、B 复制成功 → A 关公开 → 匿名与 B 404；本人始终能看（私有时有提示条）。因为是真实账号，用例无论在哪一步失败都在 `finally` 里收尾：收藏夹先设回私有再删除、确认公开页 404，并移除为测试新收藏的那一篇。没有凭据时 `test.skip` 并写明原因。
  - 测试注册的账号邮箱用 Resend 的测试地址 `delivered+e2e-<随机>@resend.dev`（mini 的 identity 走真实邮件服务商，`@example.org` 会真发出去再退信）；v0.12 的 `user-center.spec` 共用同一个 `freshAccount`，一并改过来（2026-10-26 用户定）。
  4. 两种尺寸（1440×900、375×812）截图核对布局（AC 2）。
- 门禁：`pnpm lint`、`pnpm typecheck`、`pnpm test` 全绿；`tests/design-system/*` 守护语义 token。

---

## 8. 我定的设计点（看到不对就推翻）

1. CiteDialog、`useCopy`、文献库与公开页的「复制链接」在 PAP-114 做（Issue 的 To-Do 为准），文献页分享面板留 PAP-115。
2. 读走 TanStack Query：RSC 预取 + `HydrationBoundary`，失效重取走 Route Handler；写走 Server Action。公开收藏夹页与主页标签是只读页，直接 RSC 渲染不进缓存。
3. 「我的状态」不在服务端预取，客户端挂载后一屏一批，3 s 超时（简报建议 1.5 s；mini 实测平时 0.1 ~ 1 s、偶有 2.5 s 毛刺，1.5 s 会误报不可用）、不重试。
4. 详情页操作行的窄屏方案 A 留到 PAP-115 加「分享」时一起改，本 Issue 只把假收藏按钮换成真的。
5. RSC 区块的「重试」是重新进入当前地址；客户端区块的「重试」是 `refetch()`。
6. 门户公开地址配置 `PATRA_PORTAL_PUBLIC_URL`，没配时用请求的 `Host`。
7. catalog 批量卡片缺了某条 ACTIVE 条目时按「已失效」外观显示，标题用快照或 `标题暂不可用`。
8. 导出范围的 keys 在对话框打开时取一次（用于跳过篇数与超限判断），下载时把这批 ID 交给 catalog 导出转发路由；导出 / 引用转发路由不绑定 interaction，PAP-115 的单篇导出可直接复用。
9. 记住上次格式用 `localStorage`（读写都 try/catch，读不到用默认）。

## 9. 风险

- **interaction 条目页 + catalog 批量卡片串行两跳**：一页 50 条，两跳都在 tailnet 内，可接受；超时各 5 s。
- **导出 5,000 条的流式下载在门户内存里成 blob**：RIS 5,000 条约 5 MB，浏览器端可接受；门户服务端只转发流、不缓冲。
- **`setQueriesData` 跨批次同步**：同一篇在多个批次查询里时都改到；漏改只会让别的屏在下次失效重取前显示旧状态，不会写错数据。

## 10. 验收对照（PAP-114 AC）

| AC | 证据 |
|---|---|
| 三处收藏按钮刷新后保持状态，并与文献库一致 | e2e 1 |
| 1440×900 与 375×812 布局符合简报 | e2e 4 的截图 + 本地验收截图 |
| 公开收藏夹页对匿名、他人、本人的可见性与 spec 2.1.2 一致 | e2e 3（需已验证账号）+ 组件测试三种视角 |
| interaction 不可用时文献页主体正常，收藏区块「暂不可用」 | e2e 2 + 组件测试；mini 上停容器手动核对 |

## 11. 用户的决定（2026-10-26）

1. **已验证邮箱的测试账号**：用用户自己的账号，凭据由用户写进 `~/.patra/secrets/e2e.env`（见 §7 e2e 3）。
2. **`PATRA_PORTAL_PUBLIC_URL`**：mini 上先不设，按请求的 `Host` 拼链接，开放公网时再定（§3.6）。
3. **测试邮箱**：e2e 注册账号改用 `delivered+<标识>@resend.dev`（§7）。
