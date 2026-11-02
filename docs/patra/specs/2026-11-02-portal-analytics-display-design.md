# 门户计数、热读与看过还看了设计

> **Issue**：PAP-174 的另一部分（v0.18 Analytics，Milestone FE Ready）
> **日期**：2026-11-02
> **依据**：设计简报 `patra-portal/docs/design-briefs/v0.18/analytics.md`（下称「简报」）§0、§3.1～§3.6、§3.11、§4.1～§4.3、§6；画板快照 `Cards`、`HotMark`、`Paper`、`Feed`；PAP-170 对外接口（`docs/patra/specs/2026-11-02-analytics-insight-design.md` §9，已定稿）；[门户埋点设计](2026-11-02-portal-analytics-tracking-design.md)（列表埋点）
> **范围**：PAP-174 里依赖 PAP-170 的部分。阅读历史、阅读记录卡片、隐私政策另见 [2026-11-02-portal-reading-history-privacy-design.md](2026-11-02-portal-reading-history-privacy-design.md)

---

## 1. 要做什么

1. **卡片计数**：检索结果、首页文献流、我的文献库、公开收藏夹四处卡片的计数行加「N 人看过」，排在收藏之前（简报 §3.2）。
2. **文献页读者一行**：事实条下方单独一行「看过 · 读过全文 · 收藏 · 讨论」，收藏、讨论两格从事实条挪下来（简报 §3.3）。
3. **检索结果「近期热读」标记**（简报 §3.4）。
4. **首页文献流第三个标签「热读」**，含补位卡片与说明句（简报 §3.5）。
5. **文献页底部「看过这篇的人还看了」/「同主题文献」**（简报 §3.6）。

版式、文案、状态以简报与画板为准，本文只定数据怎么取、组件怎么改、怎么降级。

## 2. 后端接口（PAP-170，已定）

都在 `/patra-analytics` 下，不要登录、不带会话，校验失败 422、服务不可用 503：

| 接口 | 用途 |
|---|---|
| `POST /articles/stats`，`{ articleIds }`（1～100） | 每个 ID 一行 `{ articleId, viewerCount, fulltextReaderCount, hot }`，按请求顺序；`hot` = 在热读前 100 名（补位不算）。60 秒缓存 |
| `GET /hot-reads` | `{ computedAt, rankedTotal, items: [{ articleId, source: RANKED / LATEST, rank }] }`，最多 14 条，上榜在前、补位在后 |
| `GET /articles/{articleId}/also-read` | `{ items: [{ articleId, source: CO_READ / SAME_MESH / SAME_VENUE, meshTerm, venueName }] }`，最多 6 条，已按显示顺序排好 |

analytics 只给 ID，卡片内容由门户用现有的 `fetchPublicationCards`（catalog 批量）按 ID 取，catalog 没回的（软删）跳过。

## 3. 门户的取数层

`lib/analytics/insight.ts`（server-only，不带会话，`gatewayFetch` 直接调）：

- `fetchArticleStats(ids)` → `ReadonlyMap<id, { viewerCount, fulltextReaderCount, hot }> | null`。照 `fetchPaperStats` 的规矩：去重、按 100 个一批并行取、超时 2 秒；任何一批失败（超时、非 200、响应格式不对、网络错误）整屏返回 `null`，不出现半屏有数；不抛错、打一行日志。
- `fetchHotReads()` → `{ rankedTotal, items } | null`，超时 5 秒。
- `fetchAlsoRead(id)` → `items | null`，超时 2 秒。
- 响应都用 zod 校验。

**两类计数并行取、各自降级**：看过、读过全文、热读标记来自 analytics；收藏、讨论来自 interaction（`fetchPaperStats`）。每处都 `Promise.all` 两个请求，谁取不到就只少谁那几项（简报 §3.1、§3.11）。

## 4. 卡片计数

- **`PaperStats` 组件**加一个可选的 `viewerCount: number | null` 属性，与现有的 `stats`（收藏、讨论）各自为 `null` 表示取不到。显示顺序 看过 → 收藏 → 讨论；为 0 的一项不写；三项都没有时整行不出现。卡片上不写读过全文（简报 §10 第 1 条）。分隔符照现有写法。
- 四处怎么拿到数：
  - **检索结果**（`PaperResults`）、**首页文献流**（`ExploreFeed`）：服务端组件里与 `fetchPaperStats` 并行调 `fetchArticleStats`，按 ID 传给卡片。
  - **我的文献库**、**公开收藏夹**：都经 `lib/interaction/attach-papers.ts` 的 `attachPapers` 配卡片与计数。在它里面加第三个并行请求，`WithPaper` 多一个 `views: number | null` 字段。文献库是客户端组件，经 `/api/me/library/items` 拿这份数据，字段随之带过去。
- 本人看了不即时 +1（简报 §3.1，60 秒缓存）。

## 5. 文献页读者一行

- 新组件 `components/portal/paper-detail/PaperReaders.tsx`：事实条之后、操作按钮之前的一行（上边距 14px，`text-sm` `fg-3`，数字 `fg-1` 字重 500）。
- 里面两个 `Suspense`（fallback 都是 `null`），分别流式到达、分别插入（简报 §3.3「不画骨架」）：
  - `AnalyticsReaderCounts`（async RSC）：`fetchArticleStats([id])`，渲染「N 人看过」「N 人读过全文」两项，带简报 §3.1 的悬停说明（`title`）。
  - `InteractionReaderCounts`（async RSC）：现在 `PaperInteractionFacts` 取数的那一套，渲染「N 人收藏」（本人收藏 / 取消同步 ±1，沿用 `PaperSavedFact` 的 delta 逻辑）与「N 条讨论」（链到 `#discussion`）。
- 项之间的「·」用 CSS 兄弟选择器画（`[&>*+*]:before`），不靠 React 判断前面有没有项：两组各自到达时，分隔符自然正确。四项都没有时行是空的，用 `empty:hidden` 收起（`:empty` 忽略注释节点；流式占位的 `<template>` 在解析后被移除）。
- 事实条去掉收藏、讨论两格：`PublicationDetailView` 的 `interactionFacts` 插槽改成 `readers` 插槽，位置挪到事实条下方。`PaperInteractionFacts` 与 `PaperCountFacts` 里不再用的格子组件删掉，相关测试改写。

## 6. 检索结果「近期热读」

- `PaperListItem` 加 `hot?: boolean`，为真时在来源行右端、文献类型之前放 `HotBadge`（lucide `TrendingUp` 12px + `近期热读`，等宽 `text-2xs` 字重 500 `clay-700`）；窄屏文献类型隐藏时标记仍在右端。
- `HotBadge` 是客户端组件，带 Tooltip（键盘聚焦也能出）。门户现在没有 Tooltip 原语：按 shadcn 的 base-ui 版本新增 `components/ui/tooltip.tsx`（`@base-ui/react` 已是依赖，不加新包）。说明文字：`近 14 天读的人多，在热读榜前 100 名。只是标记，不影响检索排序。`
- 只有 `PaperResults` 传 `hot`（取自 `fetchArticleStats` 的 `hot`）；文献库、公开收藏夹、首页卡片不传。analytics 取不到时全部不带。不改排序。

## 7. 首页「热读」标签

- `FeedTab` 加 `"hot"`，地址 `/?tab=hot`；`ExploreFeedTabs` 第三格 `热读`（`TrendingUp`），默认仍是「最近更新」。区块说明句按简报 §3.5 改。
- `ExploreFeed` 在 `tab = hot` 时：`fetchHotReads()` → `fetchPublicationCards(ids)` → 按热读顺序排、去掉 catalog 没回的 → 与其他标签同一个 `FeedLayout`。
  - 上榜卡片：`PaperCard` 的序号就是名次（`rank`，两位数）。
  - 补位卡片：`PaperCard` 加 `filler` 属性，序号位置换成 `FillerTag`（小胶囊 `最新收录`），读屏在标题前读出。
  - 标签栏与网格之间的 `FeedNote`（只在热读标签）：按「上榜的够 14 篇 / 不够 / 一篇都没有」三句选，数字用实际显示的上榜篇数与补位篇数（catalog 去掉的都不算）；上榜卡片的名次也按显示顺序重新编号，不断号。
  - 计数行照常（看过、收藏、讨论）。
  - analytics 或 catalog 取不到 → `ExploreFeedEmpty` 的「加载失败」；`items` 为空（任务从没跑过、catalog 也没有文献）→「空」。
- 埋点：列表 `list_type = feed_hot`（PAP-173 的 `TrackedList`）。

## 8. 看过这篇的人还看了 / 同主题文献

- 新组件 `components/portal/paper-detail/AlsoReadSection.tsx`（async RSC），放在文献页两栏网格之后、页脚之前的整宽区块，包在 `Suspense` 里，骨架是标题一条 + 3 张卡片骨架。
- 取数：`fetchAlsoRead(id)` → `fetchPublicationCards(ids)`，按返回顺序、去掉 catalog 没回的。
- 分组（纯函数 `groupAlsoRead(items)`）：`CO_READ` 一组，`SAME_MESH` 与 `SAME_VENUE` 合成一组；有 `CO_READ` 的那组标题 `看过这篇的人还看了`，另一组 `同主题文献`，各带简报的说明；只有一组时只出一组；都没有 → 整个区块不出现。
- 卡片 `AlsoReadCard`：来源行（来源 · 年份 · 类型）→ 衬线标题三行截断 → 刊名斜体 · 第一作者「等」；补位卡片多一行原因：`同主题词 <MeSH 原文>`（衬线）或 `同一期刊 <刊名>`。不放收藏按钮与计数。三列网格，窄屏一列。
- analytics 或 catalog 取不到 → 整个区块不出现（`null`），页面其余照常。
- 埋点：两组各是一个列表，`list_type` 分别为 `related`、`related_topic`，`position` 在各自组内从 1 起。

## 9. 测试

TDD，按简报 §4.1～§4.3 的状态矩阵，每个状态一条用例：

| 对象 | 用例 |
|---|---|
| `fetchArticleStats` 等三个取数函数 | 请求路径、方法、请求体；超过 100 个分批；任一批失败整屏 `null`；422 / 503 / 超时 / 格式不对 → `null` 并打日志 |
| `PaperStats` | `card.views`（看过在收藏前）、`card.views.zero`、`card.views.all-zero`（整行不出现）、`card.analytics-down`、`card.interaction-down` |
| `PaperResults` / `PaperListItem` / `HotBadge` | `search.hot`（只有 `hot` 的带标记）、`search.hot.tooltip`（悬停与聚焦）、`search.hot.down` |
| `attachPapers` / 文献库路由 | 三个请求并行；analytics 失败只让 `views` 为 `null` |
| `PaperReaders` | `paper.readers`（四项顺序与悬停说明）、`paper.readers.fresh`、`paper.readers.no-fulltext`（读过全文为 0 不写）、`paper.readers.down`（两种各少各的）、全都没有时行收起、收藏 ±1 |
| `ExploreFeed` 热读 | `feed.tab.hot` / `.recent` / `.cited`、`feed.hot.full`（序号即名次、算法说明句）、`feed.hot.partial`（补位胶囊与「只有 N 篇」）、`feed.hot.none`、`feed.error`；catalog 没回的补位不计入说明句 |
| `groupAlsoRead` / `AlsoReadSection` | `paper.also.full` / `.mixed` / `.same` / `.none`；`loading` 骨架；analytics 或 catalog 失败整块不出现；补位原因两种写法 |

Playwright（连 mini 网关，等后端部署后跑）：B 读一篇全文后 1 分钟内卡片与文献页两个数字各加一；B、C、E 都读同一篇后它出现在首页热读里、检索结果带标记；停掉 `patra-analytics` 时首页、检索、文献页主体照常、计数与推荐消失；375 宽不横向溢出。

## 10. 我的决定（等你审）

1. **计数与热读标记一次取**（`/articles/stats` 带 `hot`），与 interaction 的计数并行、各自降级（§3）。
2. **`PaperStats` 加 `viewerCount` 属性**，不新做组件；文献库与公开收藏夹在 `attachPapers` 里一起取（§4）。
3. **读者一行两组各自流式到达，分隔符用 CSS 画、空行用 `:empty` 收起**（§5）。
4. **事实条里收藏、讨论两格的旧组件删掉**，不留并存（§5）。
5. **热读卡片的名次就是序号；补位卡片用 `PaperCard` 的 `filler` 属性**，不另做卡片（§7）。
6. **看过还看了的补位原因只信 analytics 给的 `meshTerm` / `venueName`**，门户不再查 catalog 的主题词（§8）。
7. **新增 Tooltip 原语**（shadcn 的 base-ui 版本），第一个用它的是「近期热读」（§6）。
8. 读者一行在两组都还没到达时，流式占位可能让空行短暂留出 14px 上边距，到达后消失；实现时在浏览器里确认，不另做处理（§5）。
