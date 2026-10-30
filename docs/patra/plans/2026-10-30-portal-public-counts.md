# 门户公开计数（PAP-135）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 门户显示楼内回复数与最后回复时间、讨论条数、文献卡片与文献页的收藏人数与讨论数、经验行回复数、主页获赞，0 不写，取不到就不显示。

**Architecture:** 讨论、经验的计数随现有响应带回，扩 schema 后在组件里显示，本人发表 / 删除由缓存纯函数 ±1。文献卡片与文献页的统计在门户服务端按屏批量取（`POST /subjects/stats`，2 秒超时，失败为 `null`），本人收藏 ±1 由 `LibraryStatusProvider` 从「我的状态」的翻转推出。获赞在主页头部自己的 Suspense 里取。

**Tech Stack:** Next.js 16 App Router（webpack）/ React 19 / TanStack Query v5 / Zod / Tailwind v4 / Vitest + Testing Library / Playwright

**Spec:** `docs/patra/specs/2026-10-30-portal-public-counts-design.md`

## Global Constraints

- 所有计数为 0 时不显示数字，整段不出现（不写「0 条讨论」「获赞 0」）。
- 数字一律 `formatCount`（`src/lib/format-count.ts`）。
- 文案：`N 条回复 · 最后回复 <时间>`、`展开其余 N 条回复`、`展开全部回复`、`N 条`（标题）、`N 人收藏 · N 条讨论`、`收藏` / `N 人`、`讨论` / `N 条`、`N 条回复`（经验行）、`获赞 N`。
- 计数色 `text-fg-3`；`fg-4` 只许用于占位符（设计守护测试）；不加组件级焦点环类。
- 统计、获赞超时 `STATS_TIMEOUT_MS = 2_000`；任何失败返回 `null`、记日志、不抛。
- 不轮询；不画骨架；不改 identity、catalog。
- 测试先行：每条行为先写失败的测试、看它失败、再写实现。
- 运行命令都在 `patra-portal/` 下：`pnpm test <路径>`、`pnpm lint`、`pnpm typecheck`、`pnpm build`。
- e2e 只用账号 B（`PATRA_E2E_VERIFIED_B_*`）、C（`PATRA_E2E_VERIFIED_C_*`），凭据只从 `~/.patra/secrets/e2e.env` 读，不打印。

## Review Focus

1. 本人收藏后失败回滚、或收藏后又取消：收藏人数回到原值，不出现 +2 / −1。
2. 文献在同一屏出现两次（首页头条与列表、公开收藏夹重复条目）：两处计数一致、±1 一起变。
3. 删除一楼里唯一的回复：楼内统计整段消失（`replyCount` 0、`lastReplyAt` null），不显示「0 条回复」。
4. 讨论区「最有帮助」/「最新」排序间切换后再发表：标题条数只加一次，切回另一个排序后条数仍对（另一份缓存没被改也会在重取时对上）。
5. 统计接口返回的 key 少于请求的 key（不应发生，但防御）：缺的那篇没有计数行，其余照常。

---

### Task 0: schema 与 fixture

**Files:**
- Modify: `patra-portal/src/lib/interaction/discussion-schema.ts`、`experience-schema.ts`
- Modify: `patra-portal/tests/fixtures/discussion.ts`、`experience.ts`，以及手写响应体的测试（Route Handler、`tests/lib/interaction/*.test.ts`、`tests/lib/*/server.test.ts`）
- Test: `patra-portal/tests/lib/interaction/discussion-schema.test.ts`（新）、`experience-schema` 已有测试

**Interfaces:**
- Produces: `Thread.replyCount: number`、`Thread.lastReplyAt: string | null`、`ThreadPage.total: number`、`Experience.replyCount: number`

- [ ] 写失败测试：`threadSchema` 解析带 `replyCount: 2, lastReplyAt: "2026-10-30T03:00:00Z"` 的楼；缺 `replyCount` 时报错；`threadPageSchema` 的 `total` 收数字与字符串 `"7"`；`experienceSchema` 缺 `replyCount` 报错。
- [ ] 运行，确认失败。
- [ ] 加字段：`replyCount: z.number().int().min(0)`、`lastReplyAt: z.string().nullable()`、`total: z.coerce.number().int().min(0)`、经验 `replyCount`。
- [ ] `makeThread` 默认 `replyCount: 0, lastReplyAt: null`；`makeExperience` 默认 `replyCount: 0`；新增 `makeThreadPage(items, overrides)` 默认 `total: 0`。
- [ ] 跑全量 `pnpm test`，把解析失败的手写响应体补上新字段，直到全绿。

### Task 1: 统计与获赞读函数

**Files:**
- Create: `patra-portal/src/lib/interaction/stats-schema.ts`、`stats.ts`
- Test: `patra-portal/tests/lib/interaction/stats.test.ts`

**Interfaces:**
- Produces: `type PaperStats = { collectorCount: number; discussionCount: number }`；`type PaperStatsMap = ReadonlyMap<string, PaperStats>`；`fetchPaperStats(keys: readonly string[]): Promise<PaperStatsMap | null>`；`fetchHelpfulReceived(userId: string): Promise<number | null>`；`STATS_TIMEOUT_MS = 2_000`

- [ ] 写失败测试（mock `callInteraction`，参照 `tests/lib/interaction/library.test.ts` 的写法）：
  - `should_post_deduped_keys_as_publication`：`["1","2","1"]` → 请求 `POST /subjects/stats`，体 `{type:"publication",keys:["1","2"]}`，`timeoutMs: 2000`，不带会话。
  - `should_return_empty_map_without_request_when_no_keys`。
  - `should_split_into_batches_of_100`：150 个 key → 两次请求（100 + 50），结果合并。
  - `should_map_items_by_key`。
  - `should_return_null_when_any_batch_fails`；超时、5xx、网络错误、响应不合法各一条，都返回 `null` 并调用 `logRequestError`。
  - `fetchHelpfulReceived`：成功返回数字；失败返回 `null`。
- [ ] 运行，确认失败。
- [ ] 实现。请求体用 `callInteraction(path, { method: "POST", json, timeoutMs })`（照现有 `callInteraction` 的签名；不支持 `json` 时按它的写法传 body）。
- [ ] 运行，确认通过。

### Task 2: 收藏人数增量与 `PaperStats`

**Files:**
- Modify: `patra-portal/src/components/library/LibraryStatusProvider.tsx`
- Create: `patra-portal/src/components/library/PaperStats.tsx`
- Test: `patra-portal/tests/components/library/LibraryStatusProvider.test.tsx`（已有则追加）、`tests/components/library/PaperStats.test.tsx`

**Interfaces:**
- Consumes: Task 1 的 `PaperStats`
- Produces: `useCollectDelta(key: string): -1 | 0 | 1`；`PaperStats({ paperKey: string; stats: PaperStats | null; className?: string })`

- [ ] 写失败测试 `useCollectDelta`：基线 false → `patchItemStatus` 改 true → `1`；基线 true → 改 false → `-1`；改 true 再改回 false → `0`；未登录 `0`；状态请求失败 `0`；Provider 外 `0`；`invalidateLibrary` 重取后（服务端返回 collected true）仍是 `1`。
- [ ] 运行，确认失败；实现：Provider 用 `useState` 记基线（每个 key 第一次在 `query.data` 里出现时的 `collected`，渲染期间补登新 key），context 多给 `baseline`。
- [ ] 写失败测试 `PaperStats`：`{2,5}` → 文本 `2 人收藏 · 5 条讨论`，`5 条讨论` 是指向 `/papers/k#discussion` 的链接；`{0,5}` 只有讨论；`{2,0}` 只有收藏、无链接；`{0,0}` 不渲染；`null` 不渲染；在 Provider 里收藏后 `{0,3}` → `1 人收藏 · 3 条讨论`；千分位 `1,234`。
- [ ] 运行，确认失败；实现；运行，确认通过。

### Task 3: 四处文献卡片

**Files:**
- Modify: `patra-portal/src/components/portal/papers/PaperListItem.tsx`、`PaperResults.tsx`、`src/components/portal/PaperCard.tsx`、`src/components/portal/explore-feed/index.tsx`、`src/lib/interaction/read.ts`（`attachPapers`）、`src/components/library/LibraryItem.tsx`、`src/components/library/LibraryView.tsx`（如需传参）、`src/components/library/PublicCollectionView.tsx`
- Test: 对应的已有测试文件追加（`tests/components/portal/...`、`tests/lib/interaction/read.test.ts` 或 `library.test.ts`、`tests/components/library/...`）

**Interfaces:**
- Consumes: Task 1 `fetchPaperStats`、Task 2 `PaperStats`
- Produces: `WithPaper<I>.stats: PaperStats | null`；`PaperListItem` 新 prop `stats?: ReactNode`；`PaperCard` 新 prop `stats?: PaperStats | null`

- [ ] 写失败测试 `PaperListItem`：传 `stats` 节点时渲染在摘要之后；`summary={false}` 时在元信息之后、`children` 之前。
- [ ] 写失败测试 `PaperCard`：传 `stats={{2,5}}` 时底部行有 `2 人收藏 · 5 条讨论`，位于「阅读详情」与引用数之间（DOM 顺序）；`null` 时没有。
- [ ] 写失败测试 `attachPapers`：统计与卡片都请求、每条带 `stats`；统计 `null` 时仍 `ok`、每条 `stats: null`；失效条目 `stats: null`；统计缺某个 key 时那条 `stats: null`。
- [ ] 写失败测试：`PaperResults`、`ExploreFeed` 用 `fetchPaperStats(本页 ID)` 并把统计渲染到卡片上；统计 `null` 时卡片照常、无计数。`LibraryItem`、`PublicCollectionView` 渲染 `entry.stats`。
- [ ] 运行，确认失败；逐个实现；运行，确认通过。

### Task 4: 文献页事实条两格

**Files:**
- Create: `patra-portal/src/components/portal/paper-detail/PaperInteractionFacts.tsx`（async RSC）、`PaperCountFacts.tsx`（client：`PaperSavedFact`、`PaperDiscussionFact`）
- Modify: `PaperFacts.tsx`（新 prop `interactionFacts?: ReactNode`，排在 `fullTextFacts` 之后）、`PaperHeader.tsx`（Provider 外提）、`PublicationDetailView`、`src/app/papers/[id]/page.tsx`
- Test: `tests/components/portal/paper-detail/PaperCountFacts.test.tsx`、`PaperInteractionFacts.test.tsx`、`PaperFacts` / `PaperHeader` 已有测试追加

**Interfaces:**
- Consumes: Task 1、Task 2 `useCollectDelta`；`queryKeys.discussionSubject("publication", key)` 前缀下的 `threads` 缓存
- Produces: `PaperSavedFact({ paperKey, collectorCount })`、`PaperDiscussionFact({ paperKey, discussionCount })`

- [ ] 写失败测试：`PaperSavedFact` `5` → 格子 `收藏` / `5 人`；`0` 不渲染；Provider 里收藏后 `0` → `1 人`。`PaperDiscussionFact` `7` → `讨论` / `7 条`，链接 `#discussion`；缓存里有这篇的讨论列表第一页 `total: 8` 时显示 `8 条`；`0` 且无缓存不渲染。`PaperInteractionFacts`：统计 `null` → 两格都没有；`{0, 3}` → 只有讨论格。`PaperFacts` 的 `interactionFacts` 在全文三格之后。
- [ ] 运行，确认失败；实现（讨论格用 `useQueryClient` + `useSyncExternalStore` 订阅 query cache，取 `discussionSubject` 前缀下 `queryKey[3] === "threads"` 的第一个有数据的第一页 `total`）；`PaperHeader` 的 `LibraryStatusProvider` 包住事实条与按钮组；页面传 `interactionFacts`。
- [ ] 运行，确认通过。

### Task 5: 讨论缓存 ±1

**Files:**
- Modify: `patra-portal/src/lib/discussion/cache.ts`
- Test: `patra-portal/tests/lib/discussion/cache.test.ts`

**Interfaces:**
- Produces: 现有函数行为扩展（签名不变）

- [ ] 写失败测试（spec 5.3 表格逐格）：`insertRoot` 三种落点都让 `pages[0].total + 1`，新楼 `replyCount 0 / lastReplyAt null`；`appendReply` 楼 `replyCount + 1`、`lastReplyAt = 回复 createdAt`、`total + 1`；`applyDeletion` 删回复 `replyCount − 1`、`total − 1`、`lastReplyAt` 保留；删到 0 `lastReplyAt null`；删根评论（占位）只 `total − 1`、楼内两项不变；整楼删掉 `total − 1`；`total` 不小于 0；`replaceThread` 后楼内两项是传入的服务端值；`appendReplySingle` / `applyDeletionSingle` 楼内两项同理。
- [ ] 运行，确认失败；实现；运行，确认通过。

### Task 6: 楼内统计与展开按钮

**Files:**
- Create: `patra-portal/src/components/discussion/ThreadStats.tsx`
- Modify: `CommentItem.tsx`（`trailing?: ReactNode`）、`CommentThread.tsx`
- Test: `tests/components/discussion/ThreadStats.test.tsx`、`CommentItem.test.tsx`、`CommentThread.test.tsx`（已有则追加）

**Interfaces:**
- Produces: `ThreadStats({ replyCount, lastReplyAt, now })`；`CommentItem` 新 prop `trailing`

- [ ] 写失败测试：`ThreadStats` `4 / 20 小时前的时间` → `4 条回复 · 最后回复 20 小时前`，`<time>` 带 `dateTime` 与 `title`；`0` 不渲染。`CommentItem` 的 `trailing` 在操作行里、在占位根评论的「该评论已删除」之后、编辑时不出现。`CommentThread` 根评论带统计、回复不带；展开按钮：`replyCount 5`、已加载 3 条未删除 → `展开其余 2 条回复`；已加载里 1 条占位 → 按未删除数算；剩下全是占位 → `展开全部回复`；加载中 `加载中…`。
- [ ] 运行，确认失败；实现；运行，确认通过。

### Task 7: 标题条数

**Files:**
- Modify: `patra-portal/src/components/discussion/discussion-parts.tsx`、`DiscussionView.tsx`
- Test: `tests/components/discussion/DiscussionView.test.tsx`

- [ ] 写失败测试：列表第一页 `total: 7` → 标题旁 `7 条`；`total: 0`、加载中、暂不可用都没有；发表一条根评论后 `8 条`；删一条后 `6 条`；经验页（subject 为 post）标题 `回复 · Replies` 旁同样有条数。
- [ ] 运行，确认失败；实现；运行，确认通过。

### Task 8: 经验行回复数

**Files:**
- Modify: `patra-portal/src/components/experience/ExperienceRow.tsx`
- Test: `tests/components/experience/ExperienceRow.test.tsx`

- [ ] 写失败测试：`replyCount 3` → 底部行 `3 条回复`；`0` 不显示；本人、有帮助 0、回复 2 → 底部行出现、只有回复数；两种变体（期刊页、主页）都有。
- [ ] 运行，确认失败；实现；运行，确认通过。

### Task 9: 主页获赞

**Files:**
- Create: `patra-portal/src/components/profile/ProfileHelpfulReceived.tsx`
- Modify: `ProfileHeader.tsx`（`stats?: ReactNode`）、`src/app/u/[username]/page.tsx`、`experiences/page.tsx`、`collections/page.tsx`
- Test: `tests/components/profile/ProfileHelpfulReceived.test.tsx`、`ProfileHeader.test.tsx`、三个页面已有测试追加

- [ ] 写失败测试：`7` → `获赞 7`；`0`、`null` 不渲染；`ProfileHeader` 的 `stats` 在 `@用户名` 之后、简介之前；三个页面都传了获赞（按 `profile.userId` 请求）。
- [ ] 运行，确认失败；实现；运行，确认通过。

### Task 10: 门禁

- [ ] `pnpm test`（全量）、`pnpm lint`、`pnpm typecheck`、`pnpm build` 全部通过；输出写到 scratchpad 读尾部。

### Task 11: e2e（连 mini）

**Files:**
- Create: `patra-portal/tests/e2e/public-counts.spec.ts`、`tests/e2e/support/counts.ts`（如需）
- Modify: `tests/e2e/discussion.spec.ts`（账号 A → C，只改账号）

- [ ] 先跑 `experience.spec.ts`（C 写 5 条），再跑改过账号的 `discussion.spec.ts`。
- [ ] 写 `public-counts.spec.ts`，两种尺寸，断言相对基线：
  - 讨论：C 发根评论、B 回复 → 根评论下 `1 条回复 · 最后回复 刚刚`，标题条数 +2；B 再回复一条后展开前后的按钮文案按条数；B 删回复后统计回退。
  - 卡片与文献页：B 收藏这篇 → 文献页 `收藏` +1、`讨论` 等于讨论条数；检索 `/papers?q=<标题词>` 的这张卡片显示收藏人数与讨论数；B 把它放进两个收藏夹，收藏人数只 +1；公开收藏夹页与文献库页的卡片也有计数。
  - 经验与获赞：B 写一条经验，C 点有帮助、C 回复一条 → 期刊页这条 `1 条回复`；B 的主页 `获赞` 比基线 +1（在清理之前断言）。
  - 收尾：删掉写的评论、经验，移出收藏、删测试收藏夹。
- [ ] 跑 `public-counts.spec.ts` 两种尺寸通过。
