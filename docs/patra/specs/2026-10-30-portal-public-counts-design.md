# 门户公开计数（PAP-135）工程设计

- Issue：PAP-135（[FE] 公开计数），进前端 PR #322（与 PAP-134 同一个 PR）
- 依据：release spec `docs/patra/release-specs/v0.15-helpful.md` 2.1.3、决策 C / D、Done D6 / D8；设计简报 `patra-portal/docs/design-briefs/v0.15/helpful.md` §3.4、§3.6、§3.7、§3.9、§3.11、§3.12、§3.14、§4.2、§4.3、§10；后端接口 PAP-132 / PAP-133（已部署 mini，`b554d9961`）
- 承接：PAP-134（`docs/patra/specs/2026-10-30-portal-helpful-design.md`）已落地的 `formatCount`、`HelpfulButton`、经验行 `article` 结构

## 1. 目标

把后端已经算好的公开计数显示出来，全部「0 不写」：

| 计数 | 位置 | 数据来源 |
|---|---|---|
| 楼内回复数 + 最后回复时间 | 根评论操作行右端（占位根评论右侧照样有） | 楼响应 `replyCount`、`lastReplyAt` |
| 「展开其余 N 条回复」 | 楼内收起处 | 由 `replyCount` 与已加载的回复算出 |
| 讨论区 / 回复区标题条数 | `DiscussionHeader` 右侧 | 列表响应 `total`（第一页） |
| 文献页事实条「收藏 / 讨论」 | `PaperFacts` 末尾两格 | 批量对象统计（单个 key）；讨论格跟着讨论区缓存 |
| 文献卡片「N 人收藏 · N 条讨论」 | 检索结果、我的文献库、公开收藏夹、首页文献流 | 批量对象统计，按屏一批 |
| 经验行回复数 | 期刊页、主页的经验行底部 | 经验响应 `replyCount` |
| 主页「获赞 N」 | 主页头部计数行（三个标签页共用的头部） | `GET /users/{userId}/helpful-received` |

不做：有帮助数合计、参与人数、轮询、骨架占位（release spec 第 3 节）。

## 2. 接口与 schema

后端已部署的字段（`/patra-interaction/v3/api-docs` 核对过）：

- `ThreadResponse` 多了 `replyCount: int`、`lastReplyAt: date-time | null`；`ThreadPageResponse` 多了 `total: int`（对象下未删除评论总数，含回复，不受分页影响）。单楼响应里的 `thread` 也带这两项。
- `ExperienceResponse` 多了 `replyCount: int`（期刊页列表、主页列表、经验页详情都是它）。
- `POST /patra-interaction/subjects/stats`，不要会话：请求 `{ type: "publication", keys: string[] }`（1–100 个），响应 `{ items: [{ key, collectorCount, discussionCount }] }`，每个 key 一行，未登记 / 不合法的 key 两项都是 0；超 100 或空为 422。
- `GET /patra-interaction/users/{userId}/helpful-received` → `{ helpfulReceived: int }`，不要会话。

门户 schema（`src/lib/interaction/*-schema.ts`）：

- `threadSchema` += `replyCount: z.number().int().min(0)`、`lastReplyAt: z.string().nullable()`。
- `threadPageSchema` += `total: z.coerce.number().int().min(0)`（与 PAP-134 经验列表 `total` 一致，数字 / 字符串都收）。
- `experienceSchema` += `replyCount: z.number().int().min(0)`。
- 新 `subjectStatsSchema`（`src/lib/interaction/stats-schema.ts`）与 `helpfulReceivedSchema`。

## 3. 两个新读函数（服务端）

`src/lib/interaction/stats.ts`：

- `fetchPaperStats(keys: readonly string[]): Promise<PaperStatsMap | null>`
  - `PaperStatsMap = ReadonlyMap<string, PaperStats>`，`PaperStats = { collectorCount: number; discussionCount: number }`。
  - 去重；空数组直接返回空 Map、不发请求；超过 100 个按 100 一批并行发（现在一屏最多 50 条，留着防以后改页大小）。
  - 走 `callInteraction`（`no-store`，不带会话）。任何失败（超时、非 2xx、响应解析失败、网络错误）记日志后返回 `null`，绝不抛进页面；只要有一批失败整体就是 `null`（一屏要么都有数、要么都没有，不出现半屏有数）。
  - 超时 `STATS_TIMEOUT_MS = 2_000`（我定的，见第 9 节第 1 条）。
- `fetchHelpfulReceived(userId: string): Promise<number | null>`：同样的失败处理与超时，成功返回数字。

两者都不缓存：`callInteraction` 已经是 `cache: "no-store"`，失败的那次不留下任何东西，下次请求重新取，所以 interaction 恢复后刷新即回来（D8）。首页、检索页、文献页、主页都是动态渲染（读 `searchParams` / 会话），没有 ISR，计数新鲜度就是请求时刻。

## 4. 文献卡片：四处

### 4.1 取数时机（决策 C 与简报 §3.12 的读法）

决策 C 写「门户与收藏状态同一时机取」，简报（用户已拍板，§10 第 4 条）写「计数随页面在服务端取、未登录也显示」。两者不能是同一个请求：「我的状态」只在登录后由客户端取，计数要给匿名访客看。按已拍板的简报：**计数在服务端随页面一屏一批取**，与收藏状态同一屏、同一批 key；取不到就没有这一行。

| 位置 | 组件 | 怎么取 |
|---|---|---|
| ① 检索结果 `/papers` | `PaperResults`（async RSC） | 拿到检索结果后 `fetchPaperStats(本页 ID)`，Map 往下传 |
| ② 我的文献库 `/library` | `LibraryView` → `LibraryItem` | `attachPapers` 里与卡片并行取，每条 `entry.stats`；客户端换页 / 排序重取时 Route Handler 返回的同一批里就有 |
| ③ 公开收藏夹 `/collections/[id]` | `PublicCollectionView`（RSC） | 同 ②（也走 `attachPapers`） |
| ④ 首页文献流 `/` | `ExploreFeed`（async RSC） | 拿到文献流后 `fetchPaperStats` |

`attachPapers` 里统计与卡片 `Promise.all` 并行；统计 `null` 时每条 `stats: null`，**不**把整页变成 `unavailable`（卡片取不到才是整页不可用，v0.13 不变）。`WithPaper<I>` 多一个字段 `stats: PaperStats | null`（条目已失效、没有 key 时也是 `null`）。

①④ 是先取文献再取统计（统计要 ID），多一次串行往返；超时 2 秒封顶。②③ 与卡片并行，不加时延。

### 4.2 显示：`PaperStats`

`src/components/library/PaperStats.tsx`（client）：`PaperStats({ paperKey, stats, className })`。

- `stats` 为 `null` → 不渲染。收藏人数 = `stats.collectorCount + useCollectDelta(paperKey)`（第 4.3 节），下限 0。
- 两项都为 0 → 不渲染；只有一项非 0 → 只写那一项。
- 文案：`23 人收藏 · 7 条讨论`，数字用 `formatCount`。外层 `font-mono text-2xs tabular-nums text-fg-3`，`·` 用 `text-ink-300`，`aria-hidden`。
- `N 条讨论` 是链接 `/papers/<key>#discussion`（悬停 `text-fg-1` + 下划线）；收藏人数是纯文本（不公开收藏人）。

位置（简报 §3.12，`CardPlace` 方案 A）：

- `PaperListItem` 加 `stats?: ReactNode` 槽，渲染在摘要之后、`children` 之前：检索结果与公开收藏夹（有摘要）落在卡片末尾；文献库（`summary=false`，没有徽章与摘要）落在元信息之后、归属与备注之前。一个位置同时满足两种布局。
- `PaperCard` 底部一行：`阅读详情 … [PaperStats] · N 引用 [收藏]`。计数在宽屏插在「阅读详情」与引用数之间（计数带 `ml-auto`，引用数前加 `·`）；`max-md` 下计数 `order-first basis-full`，移到底部行上方单独一行，引用数恢复 `ml-auto`。

### 4.3 本人收藏 / 取消收藏：收藏人数 ±1

不在每次 `patchItemStatus` 时改计数：它在乐观、成功、失败回滚三处都会调，挂在那里会重复加减。改为**在 `LibraryStatusProvider` 里从「我的状态」的 `collected` 翻转推出增量**：

- Provider 记下每个 key 第一次拿到的 `collected`（基线；那次请求的服务端值，还没有被本人在这一屏的操作改过）。
- `useCollectDelta(key)`：基线为 `false`、现在为 `true` → `+1`；基线 `true`、现在 `false` → `-1`；其余（未登录、状态未到、取不到、Provider 外）→ `0`。
- 乐观收藏立刻 `+1`，失败回滚把 `collected` 改回去，增量自动回 0；`invalidateLibrary` 重取「我的状态」后 `collected` 仍是新值，增量不变，与服务端快照相加仍然对。
- 统计是服务端在页面请求时取的，「我的状态」稍后才在客户端取；两者之间本人在别的标签页改过收藏会让基线偏一次，刷新即对（我定的，可接受）。
- 文献库页（②）没有 Provider：那里的条目都是已收藏的，移出文献库整条消失，不需要 ±1。

### 4.4 文献页事实条

`PaperHeader` 里的 `LibraryStatusProvider` 现在只包着收藏按钮；提到外层，把事实条和按钮组一起包进去。两格的取数照全文三格的做法：`page.tsx` 传 `interactionFacts={<Suspense fallback={null}><PaperInteractionFacts paperId /></Suspense>}`，`PaperInteractionFacts`（async RSC）里 `await fetchPaperStats([paperId])`，再渲染下面两个 client 格子；页头其余部分不等它。

- 收藏格 `PaperSavedFact`（client）：`收藏` → `N 人`，`N = collectorCount + useCollectDelta`，0 不出现；`stats` 为 `null` 不出现。
- 讨论格 `PaperDiscussionFact`（client）：`讨论` → `N 条`（链接色，`href="#discussion"`）。**跟着讨论区缓存走**：讨论区列表已在缓存里时，取这篇文献任一排序的列表第一页 `total`（本人发表 / 删除后已 ±1，第 5.3 节）；还没有时用服务端统计的 `discussionCount`。0 不出现；`stats` 为 `null` 且讨论区缓存也没有 → 不出现。
- 两格都排在事实条最后（全文三格之后），晚到也不挤动前面。

`FactCell` 需要能放进 client 组件：它现在是纯展示、不读服务端数据，直接在 client 组件里用；事实条 `dl` 的直接子元素仍是 `FactCell` 的 `div`。

## 5. 讨论区

### 5.1 楼内统计 `ThreadStats`

`src/components/discussion/ThreadStats.tsx`：`ThreadStats({ replyCount, lastReplyAt, now })`。

- `replyCount === 0` 或 `lastReplyAt === null` → 不渲染。
- 文案 `N 条回复 · 最后回复 <时间>`；时间用 `formatRelative(lastReplyAt, now)`（v0.14 §3.22 的写法），`<time dateTime title={formatDateTime}>`。`text-xs text-fg-3`，常规字重。
- 放在根评论操作行右端：`CommentItem` 加 `trailing?: ReactNode`，操作行里 `ml-auto`；`max-[880px]` 下 `basis-full ml-0`（换到下一行、左对齐）。占位根评论（`该评论已删除`）同样有 `trailing`，排在占位文字右侧。
- 只给根评论传；回复不传。根评论正在原地编辑（操作行隐藏）时不显示（我定的）。

### 5.2 「展开其余 N 条回复」

`CommentThread` 的按钮：

- `remaining = thread.replyCount − 已加载回复里未删除的条数`。
- `remaining > 0` → `展开其余 ${formatCount(remaining)} 条回复`；`remaining ≤ 0` 且 `hasMoreReplies`（剩下的全是占位）→ `展开全部回复`；加载中 `加载中…`（不变）。
- `ChevronDown` 14px，`aria-hidden`。

### 5.3 本人发表 / 删除后的缓存（纯函数，`lib/discussion/cache.ts`）

| 操作 | 楼 | 第一页 `total` |
|---|---|---|
| 发根评论 `insertRoot` | 新楼 `replyCount: 0, lastReplyAt: null` | `+1`（`top` / `bottom` / `offpage` 三种都加） |
| 发回复 `appendReply` | `replyCount + 1`，`lastReplyAt = 新回复 createdAt` | `+1` |
| 本人删除 `removeOwnComment`（新，结构变化用 `applyDeletion`） | 删的是回复：它那一楼 `replyCount − 1`（这条不在已加载回复里也减；到 0 时 `lastReplyAt = null`），`lastReplyAt` 其余情况不动 | `−1` |
| 删后对账 `replaceThread` / `applyDeletion`（已有） | 整楼换成单楼接口的服务端值，`replyCount`、`lastReplyAt` 以服务端为准；服务端已没有这一楼时只去掉 | 不动 |

- 删除只 `−1`：能删的只有本人未删除的那条；级联去掉的占位祖先本来就不计数。
- 删回复后的「最后回复时间」本地算不出来（不知道剩下回复里谁最新、未加载的回复），先保留原值，由已有的 `reconcileThread` 重取这一楼纠正。
- `total` 只改 `pages[0]`；页面上的条数读 `pages[0].total`。
- 单楼视图（`SingleData`）同样处理楼内两项（`appendReplySingle`、新的 `removeOwnCommentSingle`）；单楼视图标题不显示条数（它显示的是「回到全部讨论」那一套，v0.14 不变）。

### 5.4 标题条数

`DiscussionHeader` 加 `total?: number`：`SectionEyebrow` 右侧 `N 条`（`font-mono text-2xs text-fg-3 tabular-nums`）。`DiscussionView` 只在列表已成功加载且 `total > 0` 时传；加载中、空、暂不可用不传。经验页回复区就是同一个组件，标题是 `回复 · Replies`，自动得到 `N 条`。

## 6. 经验行回复数与主页获赞

### 6.1 经验行

`ExperienceRow` 底部行（PAP-134 的 `data-row-foot`）在有帮助按钮后加 `[MessageCircle 14px] N 条回复`，`text-xs text-fg-3`，纯文本（点了落在铺满的标题链接上，进经验页）。0 不显示。底部行的渲染条件改为 `!mine || helpfulCount > 0 || replyCount > 0`。期刊页、主页两种变体都是这个组件。

### 6.2 主页获赞

- 新 RSC `ProfileHelpfulReceived({ userId })`：`await fetchHelpfulReceived(userId)`，`null` 或 0 → `null`；否则 `<p>` `获赞` 标签 `text-sm text-fg-3` + 数字 `font-semibold text-fg-1`（`formatCount`）。
- `ProfileHeader`（RSC）自己在 `@用户名` 之下、简介之上（v0.12 留的计数行）放 `<Suspense fallback={null}><ProfileHelpfulReceived userId={profile.userId} /></Suspense>`，不阻塞头部其余内容。三处主页（`/u/[username]`、`/experiences`、`/collections`）都用这个头部，不用各自传（我定的，见第 9 节第 9 条）。
- 本人视角与别人视角一样；关注、粉丝两格不出现、不占位。点了主页上经验的有帮助，获赞不即时变（简报 §10 第 9 条）。

## 7. 时延与降级（D8）

- 统计、获赞都在各自的边界里：卡片统计失败只是没有计数行；获赞在 Suspense 里，失败整行不出现；楼内统计、条数、经验回复数随讨论 / 经验数据一起，讨论 / 经验不可用时按 v0.14 显示「暂不可用」，计数随内容一起没有。
- 文献页统计不阻塞页头：两格在自己的 Suspense 里（第 4.4 节），fallback 是空，晚到时追加在事实条末尾；超时 2 秒，失败为 `null`。
- **验证方式**：这些请求都在门户服务端发出，Playwright 的 `page.route` 拦不到；把网关地址指向死端口会连 catalog 一起断，证明不了「卡片照常」。所以 D8 在本 Issue 用单测覆盖：读函数在超时 / 5xx / 网络错误 / 响应不合法时返回 `null` 且不抛；`PaperStats`、事实条两格、获赞在 `null` 时不渲染、卡片其余内容照常；`attachPapers` 在统计失败时仍返回 `ok`。真停 interaction 容器的那次实测归版本收尾（D8 原文就在 mini 上做），本 Issue 不在 mini 上停服务。

## 8. 测试

- 单测（Vitest + Testing Library），每条行为先写失败的测试：
  - schema：新字段解析、`total` 数字 / 字符串、缺字段时报错。
  - `fetchPaperStats`：去重、空数组不发请求、100 一批、请求体、按 key 建 Map、各种失败返回 `null` 并记日志、任一批失败整体 `null`；`fetchHelpfulReceived` 同理。
  - `attachPapers`：统计与卡片并行、每条带 `stats`、统计失败时 `ok` + `stats: null`、失效条目 `stats: null`。
  - `useCollectDelta`：基线 false→true +1、true→false −1、回滚回 0、未登录 / 取不到 / Provider 外 0。
  - `PaperStats`：有数、只有一项、全 0 不渲染、`null` 不渲染、收藏 ±1 后 0→1 出现、讨论数是链接且指向 `#discussion`。
  - `PaperCard` / `PaperListItem` 计数位置；四处调用方把统计接上（检索、文献库、公开收藏夹、首页）。
  - 事实条两格：有数、0 不出现、`null` 不出现、收藏 ±1、讨论格跟随讨论缓存 `total`。
  - cache 纯函数：第 5.3 节表格每一格；对账后服务端值生效。
  - `ThreadStats`、`CommentThread` 展开按钮三种文案、占位根评论右侧的统计、`DiscussionHeader` 条数。
  - `ExperienceRow` 回复数与底部行条件；`ProfileHelpfulReceived`、`ProfileHeader` 槽位。
- 门禁：`pnpm test`、`pnpm lint`、`pnpm typecheck`、`pnpm build`。
- e2e（本地门户连 mini 网关，1440×900 与 375×812，只用账号 B、C）：新 `tests/e2e/public-counts.spec.ts` 覆盖 release spec 第 1 节验证场景 1–4 的计数部分；断言一律相对操作前读到的基线（文献随机、账号已有数据）。先跑旧的 `experience.spec`、`discussion.spec`（PAP-134 留下的待办），额度见第 9 节第 6 条。

## 9. 我定的

1. 统计、获赞的超时 2 秒（`READ_TIMEOUT_MS` 是 5 秒）：简报要「短超时」，计数是附加信息，不值得让列表多等 5 秒。
2. 统计按屏一次，有一批失败整屏都不显示计数（不出现半屏有数）。
3. 收藏人数 ±1 从「我的状态」的翻转推出（第 4.3 节），不另存计数缓存。
4. 文献页讨论格优先读讨论区缓存的 `total`，没有时用服务端统计（简报 §4.3「讨论格跟着讨论数据走」的落法）。
5. 根评论原地编辑时楼内统计随操作行一起隐藏。
6. e2e 额度：先跑 `experience.spec`（C 写 5 条），新冒烟的经验作者用 B；`discussion.spec` 里写死的账号 A 改成 C（只改测试账号，不改用例），因为 A 是用户本人账号、不能留公开痕迹。
7. 删回复后「最后回复时间」先保留原值、由对账纠正（第 5.3 节）。
8. 讨论数链接、事实条讨论格都指向 `#discussion`；经验页回复区不另做链接。
9. 获赞由 `ProfileHeader` 自己放（不是由三个页面各传一个槽）：三处主页用同一个头部，写一处即可，也不会漏传。
10. `attachPapers` 与 `WithPaper` 从 `read.ts` 挪到 `attach-papers.ts`：`stats.ts` 要用 `read.ts` 的 `toRead`，`attachPapers` 又要用 `fetchPaperStats`，放在一起会互相引用。
11. 首页卡片的计数与引用数之间不加 `·`，靠间距与引用图标分开（计数整行可能不渲染，外面加分隔符要先知道它渲不渲染）。
12. `FactCell` 挪到 `FactCell.tsx`：事实条两格是 client 组件，`PaperFacts.tsx` 里有服务端取全文的函数，不能进客户端包。
13. e2e 检索结果用标题开头一段检索，不用 PMID：`/papers?pmid=` 精确定位会直接跳到文献页。
14. e2e 的页面跳转只对 `net::ERR_ABORTED` 重试一次（next dev 首次编译路由会整页刷新、打断跳转）。
