# 计数、热读与看过还看了 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 卡片与文献页显示「N 人看过」「N 人读过全文」，检索结果带「近期热读」，首页有「热读」标签，文献页底部有「看过这篇的人还看了」/「同主题文献」。

**Architecture:** `lib/analytics/insight.ts` 调 PAP-170 的三个不登录接口（只给 ID），卡片内容用现有 `fetchPublicationCards` 配；与 interaction 的收藏、讨论计数并行取、各自降级。展示组件改现有的 `PaperStats`、`PaperCard`、`PaperListItem`、`ExploreFeed`、文献页插槽，新增 `HotBadge`、`PaperReaders`、`AlsoReadSection`；新列表接 PAP-173 的 `TrackedList`。

**Tech Stack:** Next 16.3 RSC + Suspense 流式、React 19、zod、`@base-ui/react`（Tooltip）、Vitest + Testing Library。

**Spec:** `docs/patra/specs/2026-11-02-portal-analytics-display-design.md`（设计简报 `patra-portal/docs/design-briefs/v0.18/analytics.md` §3.1～§3.6、§4.1～§4.3；接口契约见共享笔记 `PAP-170-api-contract.md`，已定稿）

## Global Constraints

- 每个任务不单独提交：用户本地验收后统一提交。任务收尾跑该任务测试 + `pnpm lint`。
- vitest 一律 `--maxWorkers=2`。
- 接口：`POST /patra-analytics/articles/stats` 体 `{ articleIds }`（1～100）→ `{ items: [{ articleId, viewerCount, fulltextReaderCount, hot }] }`；`GET /patra-analytics/hot-reads` → `{ computedAt, rankedTotal, items: [{ articleId, source: "RANKED" | "LATEST", rank: number | null }] }`（≤ 14）；`GET /patra-analytics/articles/{id}/also-read` → `{ items: [{ articleId, source: "CO_READ" | "SAME_MESH" | "SAME_VENUE", meshTerm, venueName }] }`（≤ 6）。不带会话。
- 超时：计数与看过还看了 2 秒，热读 5 秒；任何一批失败整屏 `null`，不出现半屏有数；不抛错。
- 文案照简报：`N 人看过`、`N 人读过全文`（千分位，`formatCount`）、`近期热读`、`热读`、`最新收录`、`看过这篇的人还看了`、`同主题文献`、`同主题词 <MeSH>`、`同一期刊 <刊名>`、三种热读说明句、两条悬停说明。0 不写；卡片上不写读过全文。
- 颜色只用现有 token：`clay-700`（热读标记）、`paper-200`（最新收录胶囊）、`accent-strong`（名次）。

## Review Focus

1. **计数接口回来的条目比请求少或有重复**：按 ID 对，少的当 0、不当取不到（Task 1 用例）。
2. **热读里 catalog 没回的条目**：跳过，名次与补位说明句只按实际显示的算（Task 5 用例）。
3. **看过还看了里有当前这篇或重复 ID**：再过滤一次，不显示自己（Task 6 用例）。
4. **两个服务一个慢一个挂**：读者一行先出到达的那组，挂掉的那组不留空位（Task 4 用例）。
5. **首页切到「热读」再切回**：每个标签一个列表实例（`TrackedList key={tab}`，PAP-173 已有），名次只在热读标签显示（Task 5 用例）。

---

### Task 1: 取数层

**Files:**
- Create: `patra-portal/src/lib/analytics/insight.ts`、`patra-portal/src/lib/analytics/insight-schema.ts`
- Test: `patra-portal/tests/lib/analytics/insight.test.ts`

**Interfaces:**
- Produces: `interface ArticleStats { viewerCount: number; fulltextReaderCount: number; hot: boolean }`；`type ArticleStatsMap = ReadonlyMap<string, ArticleStats>`；`fetchArticleStats(ids: readonly string[]): Promise<ArticleStatsMap | null>`；`fetchHotReads(): Promise<{ rankedTotal: number; items: { articleId: string; source: "RANKED" | "LATEST"; rank: number | null }[] } | null>`；`fetchAlsoRead(articleId: string): Promise<{ articleId: string; source: "CO_READ" | "SAME_MESH" | "SAME_VENUE"; meshTerm: string | null; venueName: string | null }[] | null>`。

- [ ] **Step 1: 失败测试**：请求地址、方法、体；去重；150 个 ID 分两批并行；任一批 503 / 422 / 超时 / 形状不对 → `null` 且打一行日志；响应里没有的 ID 不在 Map 里（调用方当 0）；热读空（`computedAt: null`）→ `{ rankedTotal: 0, items: [] }`；看过还看了 503 → `null`；非正整数 ID 不请求、返回 `null`。
- [ ] **Step 2: 跑 → FAIL；实现（`gatewayFetch` 直接调，不带会话）；跑 → PASS。**

### Task 2: 卡片计数

**Files:**
- Modify: `patra-portal/src/components/library/PaperStats.tsx`（加 `viewerCount?: number | null`）、`PaperCard.tsx`、`PaperResults.tsx`、`explore-feed/index.tsx`、`lib/interaction/attach-papers.ts`（`WithPaper` 加 `views: number | null`）、`LibraryItem.tsx`、`PublicCollectionView.tsx`
- Test: `tests/components/library/PaperStats.test.tsx`、`PaperResults.test.tsx`、`ExploreFeed.test.tsx`、`tests/lib/interaction/attach-papers.test.ts`（或现有对应文件）加用例

**Interfaces:**
- Consumes: `fetchArticleStats`（Task 1）。

- [ ] **Step 1: 失败测试**（简报 §4.1）：`card.views`（`1,284 人看过 · 23 人收藏 · 7 条讨论`，看过在最前）；`card.views.zero`（看过为 0 不写，其余照常）；`card.views.all-zero`（三项都 0 或都取不到 → 整行不出现）；`card.analytics-down`（`viewerCount: null` → 只少看过）；`card.interaction-down`（`stats: null` → 只剩看过）；四处调用方把 `fetchArticleStats` 的结果按 ID 传到卡片；`attachPapers` 三个请求并行，analytics 失败只让 `views` 为 `null`、页面照常。
- [ ] **Step 2: 跑 → FAIL；实现；跑 → PASS。**

### Task 3: 「近期热读」标记

**Files:**
- Create: `patra-portal/src/components/ui/tooltip.tsx`（shadcn 的 base-ui Tooltip：`Tooltip`、`TooltipTrigger`、`TooltipContent`）、`patra-portal/src/components/portal/papers/HotBadge.tsx`
- Modify: `patra-portal/src/components/portal/papers/PaperListItem.tsx`（`hot?: boolean`）、`PaperResults.tsx`
- Test: `tests/components/portal/papers/HotBadge.test.tsx`、`PaperResults.test.tsx` 加用例

- [ ] **Step 1: 失败测试**（简报 §3.4、§4.1）：`search.hot`（`hot: true` 的结果来源行右端有 `近期热读`，在文献类型之前；`hot: false` 的没有）；`search.hot.tooltip`（悬停与键盘聚焦都出 `近 14 天读的人多，在热读榜前 100 名。只是标记，不影响检索排序。`）；`search.hot.down`（计数取不到时全部不带）；文献库、公开收藏夹、首页卡片不带标记；排序不变。
- [ ] **Step 2: 跑 → FAIL；实现；跑 → PASS。**

### Task 4: 文献页读者一行

**Files:**
- Create: `patra-portal/src/components/portal/paper-detail/PaperReaders.tsx`（服务端外壳 + 两个 async 子组件 `AnalyticsReaderCounts`、`InteractionReaderCounts`）
- Modify: `PublicationDetailView.tsx`（`interactionFacts` 插槽换成 `readers`，位置在事实条之后、操作按钮之前）、`PaperCountFacts.tsx`（收藏、讨论两项改成读者一行的写法，删掉 `FactCell` 版本）、`app/papers/[id]/page.tsx`；删除 `PaperInteractionFacts.tsx`
- Test: `tests/components/portal/paper-detail/PaperReaders.test.tsx`；改写依赖旧两格的测试

- [ ] **Step 1: 失败测试**（简报 §3.3、§4.2）：`paper.readers`（顺序 看过 → 读过全文 → 收藏 → 讨论，`1,284 人看过` 的 `title` 是简报 §3.1 的悬停说明，`N 条讨论` 链到 `#discussion`）；`paper.readers.fresh`（看过、读过全文都 0 → 只剩收藏、讨论）；`paper.readers.no-fulltext`（读过全文 0 不写）；`paper.readers.down`（analytics 取不到只少前两项；interaction 取不到只少后两项）；四项都没有 → 行是空的（断言容器 `:empty` 并带 `empty:hidden`）；收藏 ±1 跟页头同步；事实条里不再有收藏、讨论两格。
- [ ] **Step 2: 跑 → FAIL；实现（项之间的「·」用 `[&>*+*]:before:content-['·']` 一类兄弟选择器）；跑 → PASS。**

### Task 5: 首页「热读」标签

**Files:**
- Modify: `patra-portal/src/types/portal.ts`（`FeedTab` 加 `"hot"`）、`src/app/page.tsx`（`safeTab` 认 `hot`）、`explore-feed/tabs.tsx`（第三格 `热读`，`TrendingUp`）、`explore-feed/index.tsx`（热读分支、区块说明句、`TrackedList` 的 `feed_hot`）、`PaperCard.tsx`（`filler?: boolean`）
- Create: `patra-portal/src/components/portal/explore-feed/FeedNote.tsx`、`FillerTag.tsx`
- Test: `ExploreFeed.test.tsx`、`tests/components/portal/explore-feed/FeedNote.test.tsx`

- [ ] **Step 1: 失败测试**（简报 §3.5、§4.3）：`feed.tab.hot/.recent/.cited`（三格，`/?tab=hot`）；`feed.hot.full`（14 篇都上榜：序号 `01`… 是名次，说明句 `按近 14 天的阅读排：读了全文的分量更重，越近的阅读分量越重；每小时更新一次。`）；`feed.hot.partial`（`rankedTotal: 4`：第 5 张起序号位置是 `最新收录` 胶囊、读屏在标题前读出，说明句 `近 7 天有 3 位以上读者的文献只有 4 篇；后面 10 篇是最新收录，卡片上标着「最新收录」。`，补位篇数按 catalog 实际回的算）；`feed.hot.none`（`近 7 天还没有文献的读者达到 3 位，下面先列出最新收录。`）；`feed.error`（analytics 或 catalog 失败 → 加载失败）；空 → 空状态；区块说明句换成 `最近入库、高被引与近期读的人多的文献。…`；列表 `list_type` 为 `feed_hot`。
- [ ] **Step 2: 跑 → FAIL；实现；跑 → PASS。**

### Task 6: 看过这篇的人还看了 / 同主题文献

**Files:**
- Create: `patra-portal/src/lib/analytics/also-read.ts`（`groupAlsoRead`）、`patra-portal/src/components/portal/paper-detail/AlsoReadSection.tsx`、`AlsoReadCard.tsx`、`AlsoReadSkeleton.tsx`
- Modify: `PublicationDetailView.tsx`（两栏之后整宽插槽 `alsoRead`）、`app/papers/[id]/page.tsx`（`<Suspense fallback={<AlsoReadSkeleton />}>`）
- Test: `tests/lib/analytics/also-read.test.ts`、`tests/components/portal/paper-detail/AlsoReadSection.test.tsx`

**Interfaces:**
- Produces: `groupAlsoRead(items, currentId): { kind: "co-read" | "topic"; items: AlsoReadItem[] }[]`（去掉当前这篇与重复 ID，保持顺序）。

- [ ] **Step 1: 失败测试**（简报 §3.6、§4.2）：`paper.also.full`（6 篇一起读的 → 一组 `看过这篇的人还看了` + 说明 `常和这篇在同一天被同一批读者读过的文献`）；`.mixed`（两组，第二组 `同主题文献` + `主要主题词与这篇相同；不够时再看同一本期刊`）；`.same`（只有同主题一组）；`.none`（空 → 整块不出现）；`.loading`（标题条 + 3 张卡片骨架）；analytics 或 catalog 失败 → 整块不出现；补位原因 `同主题词 Shock, Septic`（主题词衬线）/ `同一期刊 Chest`；卡片：来源 · 年份 · 类型、三行截断标题、刊名斜体 · 第一作者「等」，无收藏按钮与计数；两组分别是 `related` / `related_topic` 两个列表实例，位置各从 1 起。
- [ ] **Step 2: 跑 → FAIL；实现；跑 → PASS。**

### Task 7: 冒烟用例与全量验证

**Files:**
- Create: `patra-portal/tests/e2e/analytics-display.spec.ts`

- [ ] **Step 1: 写 Playwright 用例**（不在本地跑，等后端部署）：B 读一篇全文（停 12 秒）后 70 秒内，卡片「看过」与文献页两个数字各加一（相对基线）；B、C、E 都读同一篇后它出现在首页「热读」、检索结果带「近期热读」（热读每小时重算，用例注释写明可能要等下一次计算）；停掉 `patra-analytics` 时首页、检索、文献页主体照常、计数与推荐消失（注释写人工步骤）；375 宽首页与文献页不横向溢出。
- [ ] **Step 2: 全量**：`pnpm lint`、`pnpm typecheck`、`pnpm test --maxWorkers=2` 全绿。
- [ ] **Step 3: 本地起门户**：analytics 未部署时首页、检索、文献页照常（计数与推荐不出现），「热读」标签显示加载失败。
