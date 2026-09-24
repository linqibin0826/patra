# 文献浏览 / 检索页 — Design Brief

> 交给 Claude Design 的迭代简报（v0.7 唯一一份）。设计系统与代码库已 onboard，本简报只交增量。
> 复制本文正文贴入 Claude Design chat；附 dev URL（`http://localhost:4000`，首页 + `/journals` + `/papers/[id]`）让它 web capture 保持风格连续。
> 同版相关：release spec `docs/patra/release-specs/v0.7-papers-search.md`；Linear PAP-50（本简报）→ PAP-51（Design）/ PAP-52（BE）。

**目标**：访客能在门户**检索并浏览全部文献**。首页搜索框、主题云、导航"文献"、期刊详情"查看该刊文献"四个出口都落到同一个页面 `/papers`，按年份 / 期刊 / 文献类型 / 证据等级 / 语言筛选、排序、翻页，点任一篇进入已有的文献详情页。
**关联**：Linear v0.7 项目；补齐导航"文献"（当前 SOON 占位）、首页搜索框（提交只弹 toast）、主题云（词条 disabled）的落点。

---

## 0. 策略与定位

- 走全站策略：**服务全体用户 + 渐进式披露**。默认层是一个干净、可扫读的文献列表 + 检索条；**筛选 facet 桌面常显侧栏、移动收进抽屉**。
- **与首页的关系**，同"首页期刊模块 vs `/journals`"：首页是门面给入口，本页是工作台出结果。首页文献流继续做精选展示，不被替代。
- **一个页面两种状态**：
  - **浏览态**（`/papers` 不带参数）：全部文献按最近更新排列；顶部信息行给库总况；facet 带计数供用户决定往哪钻。
  - **检索态**（`/papers?...` 带参数）：关键词 / 作者 / 筛选条件生效；生效条件以 chip 挂在检索条下可单个移除；命中数 + 排序切换。
  - 从浏览态点任一 facet，即自然过渡为检索态，URL 同步变化。
- **职责边界**：本版是**简单检索**（标题子串 + 作者名 + PMID/DOI 精确）+ facet。不做高级检索语法、不做自动补全、不做相关度 / 被引排序（release spec 边界 A/B/C）。
- 闭环：四个出口 → 本页 → 点卡片 → 文献详情页 `/papers/[id]` → 点期刊名 → 期刊详情 → "查看该刊文献" → 回到本页（期刊 chip 已预填）。

---

## 1. 用户与场景

| 用户 | 场景 / 任务 |
|------|-----------|
| 全体（临床 / 科研 / 泛读） | ① 定向检索：在首页输关键词或 PMID/DOI，来这里看结果 ② 漫游浏览：点导航"文献"扫最新入库的文献，按类型 / 年份钻取 ③ 顺藤摸瓜：从期刊详情页看该刊收录了哪些文献 ④ 主题切入：点首页热词看相关文献 |

---

## 2. 要设计的屏 / 流程

| 屏 | 路由 | 用途 | 入口 → 出口 |
|----|------|------|------------|
| 文献页 · 浏览态 | `/papers` | 浏览全部文献 + 钻取 | 导航"文献" / 首页文献流"查看更多" → 本页 → 点 facet 变检索态 / 点卡片 → `/papers/[id]` |
| 文献页 · 检索态 | `/papers?q=…` 等 | 展示一次检索 / 筛选的命中列表 | 首页搜索框 / 主题云 / 期刊详情 → 本页 → 改条件 / 翻页 / 点卡片 → `/papers/[id]` |
| 文献页 · 无结果态 | 同上 | 检索或筛选无命中 | 检索态 → 清除筛选 / 改词 → 回检索态 |

**文字流**：首页搜索框输 "GLP-1" 提交 → `/papers?q=GLP-1` →(左侧勾 "近 3 年" + "随机对照试验") chip 出现、列表收敛 →(点标题) 文献详情 →(点期刊名) 期刊详情 →(点"查看该刊文献") `/papers?venue=id`，期刊 chip 预填。

**PMID / DOI 特例**：搜索框 PMID / DOI tab 提交后，命中 1 条 → **直接进详情页**，不经过本页；命中 0 条 → 落本页无结果态（"未找到 PMID 38491203 对应的文献"）。

> 共享状态屏（全局 404 / 错误页）已在 v0.5 `detail-pages.md` 设计，本页直接复用，不重画。

---

## 3. 每屏内容与数据 ← 最关键

### 3.1 浏览态 `/papers`

**① 检索条**（页面顶部）
- 四 tab 复用首页 `Composer` 的语义：**关键词**（默认）/ PMID / DOI / 作者，placeholder 沿用首页 `search-modes.ts`（"JAK1 抑制剂 · 特应性皮炎 · 1岁以下" / "38491203" / "10.1016/j.jaci.2024.03.018" / "Topol, Eric J."）。
- 输入框 + 提交按钮。浏览态下输入框为空。
- 可以比首页 Hero 里的 Composer 紧凑，但 tab 的视觉语言一致，让用户认得"这就是首页那个框"。

**② 信息行**（检索条下方，浏览态特有）
- `共 18,807 篇 · 来自 PubMed · 最近同步 2026-08-30`（三段真实数据，见附录）
- 右侧排序切换：**最近更新**（默认）/ 年份

**③ 筛选 facet**（桌面左侧常显栏，移动抽屉）
- **发表年份**：快捷项 近 1 年 / 近 3 年 / 近 5 年 + 年份列表带计数（数据现状：绝大多数是 2026，见附录）
- **文献类型**：勾选列表带计数。展示用中文名 + 原值，如 "综述 Review 2,040"、"随机对照试验 RCT 139"。只列 Top N，其余折叠"更多"
- **证据等级**：5 级 + 未分级，带计数。等级名沿用详情页 `EvidenceBadge` 的 label（系统综述 / Meta 分析、随机对照试验、队列 / 病例对照、非系统综述 / 临床研究、病例报告、未分级）。**数据现状：约 84% 未分级**，"未分级"一项会是最大的，设计上要让它不抢眼（放末尾、弱化）
- **期刊**：**可搜索添加**，不是列表（库里 2,924 本刊）。输入刊名 → 下拉候选 → 选中即加入筛选。从期刊详情跳入时该项已预填
- **语言**：勾选列表带计数（en 占 98%，其余 de / zh / fr / ru / es 小量）
- **开放获取**：仅 OA 开关。**数据现状：本库 OA 字段全为 false**，本版该 facet 计数为 0。设计上保留位置，但要定义"facet 无可用项时整组不渲染"的规则（见 §4 边界）
- 每组可折叠；底部"清除全部筛选"

**④ 文献列表**（主体）
- 复用首页 `PaperCard` 的信息层级，可做紧凑列表变体（单列、无卡片边框、分隔线）：
  1. 来源行（弱）：`PubMed · 10.1016/j.jare.2026.01.066`
  2. **标题**（最重要，衬线，内联标签渲染 `RichInlineText`，最多两行截断）
  3. 期刊 · 年份 · 作者前 2–3 位 + "等 N 位作者"（期刊名可点，进期刊详情）
  4. 徽章行：文献类型 badge（`kind`）+ 证据等级 `EvidenceBadge`（仅成功分级时渲染，沿用 v0.6 降噪规则）
  5. 摘要首两行（纯文本 `abstractPlainText` 截断；无摘要则该行不占位）
- 卡片底部**不放**收藏 / 评论 / 引用占位按钮（首页 PaperCard 有，本页去掉，边界 D）
- 信息密度：高 —— 一屏 6–8 条，参照首页 explore-feed 紧凑卡片。

**⑤ 分页**（底部）
- 页码式（`‹ 1 2 3 … 941 ›`），复用 `/journals` 的 `JournalPagination` 视觉；每页 20 条

**代表性数据样例**（真实 feed 响应，字段与本页列表 DTO 同形）：
```json
{
  "page": 1, "pageSize": 20, "total": 18807, "totalPages": 941,
  "items": [
    {
      "id": "352303128713027974",
      "title": "OSBPL6 protects against demyelination and behavioral disorder via promoting cholesterol transport in oligodendrocyte.",
      "journal": "Journal of advanced research",
      "year": 2026,
      "authors": ["Chang Mengni", "Zhang Kaiqi", "Li Ye", "Chen Xiao", "Lan Tian", "Zhu Yan"],
      "doi": "10.1016/j.jare.2026.01.066",
      "pmid": "41605285",
      "source": "PubMed",
      "kind": "Journal Article",
      "evidenceLevel": { "level": "UNKNOWN", "rank": 0, "label": "未分级", "derived": false },
      "abstractSnippet": "INTRODUCTION: Demyelination is associated with behavioral disorder and cognitive impairment in neuropsychiatric diseases…"
    },
    {
      "id": "352303128713027973",
      "title": "The risk of latent tuberculosis infection in patients with type 2 diabetes mellitus according to use of sodium-glucose cotransporter 2 inhibitors: A national database cohort study.",
      "journal": "International journal of infectious diseases : IJID : official publication of the International Society for Infectious Diseases",
      "year": 2026,
      "authors": ["Lee Chung-Shu", "Ho Chung-Han", "Liao Kuang-Ming", "Wu Yu-Cih", "Shu Chin-Chung"],
      "doi": "10.1016/j.ijid.2026.108436",
      "pmid": "41605283",
      "source": "PubMed",
      "kind": "Journal Article",
      "evidenceLevel": { "level": "COHORT_OR_CASE_CONTROL", "rank": 3, "label": "队列 / 病例对照", "derived": true },
      "abstractSnippet": "In this nationwide cohort, SGLT2 inhibitor use was associated with a lower risk of…"
    }
  ]
}
```

**facets 响应样例**（形态对齐 `/portal/venues/facets`）：
```json
{
  "years": [ { "value": "2026", "count": 17520 }, { "value": "2025", "count": 1268 }, { "value": "2024", "count": 14 } ],
  "types": [ { "value": "Journal Article", "count": 17680 }, { "value": "Review", "count": 2040 }, { "value": "Letter", "count": 471 }, { "value": "Case Reports", "count": 318 }, { "value": "Systematic Review", "count": 236 }, { "value": "Randomized Controlled Trial", "count": 139 }, { "value": "Meta-Analysis", "count": 110 } ],
  "evidence": [ { "value": "SYSTEMATIC_REVIEW", "count": 346 }, { "value": "RANDOMIZED_CONTROLLED_TRIAL", "count": 139 }, { "value": "COHORT_OR_CASE_CONTROL", "count": 421 }, { "value": "NON_SYSTEMATIC_REVIEW", "count": 2040 }, { "value": "CASE_REPORT", "count": 318 }, { "value": "UNKNOWN", "count": 15543 } ],
  "languages": [ { "value": "en", "count": 18488 }, { "value": "de", "count": 107 }, { "value": "zh", "count": 93 } ],
  "openAccess": 0,
  "total": 18807,
  "lastSyncedAt": "2026-08-30T04:06:51Z"
}
```

### 3.2 检索态 `/papers?q=GLP-1&year_from=2024&evidence=RANDOMIZED_CONTROLLED_TRIAL`

与浏览态同一骨架，差异只有四处：

- **检索条**：输入框已填当前关键词（或作者名），tab 停在对应项。
- **信息行 → 结果行**：`共 1,284 篇` 命中数 + 排序切换（最近更新 / 年份）。浏览态的"来源 / 最近同步"在检索态不显示。
- **chip 条**（结果行下方，新增元素）：每个生效条件一个 chip，如 `GLP-1 ×` `近 3 年 ×` `随机对照试验 ×` `Lancet ×`，末尾"清除全部"。关键词 / 作者也是 chip，叉掉即清空输入框。
- **列表**：关键词在**标题**中的命中片段高亮（仅标题；摘要不高亮，本版不做全文匹配）。

**facet 计数**随当前条件收窄（勾了 RCT 之后，年份各项的计数变成 RCT 内的分布）。

### 3.3 无结果态

- 检索态骨架不变（检索条 + chip 条 + facet 都在），列表区替换为空态：
  - 有关键词：`未找到匹配 "xxx" 的文献` + 两条建议（换个关键词 / 清除筛选条件）+ "清除全部筛选"按钮
  - 仅筛选无关键词：`当前筛选条件下没有文献` + "清除全部筛选"
  - PMID / DOI 未命中：`未找到 PMID 38491203 对应的文献`，附"用关键词检索"入口
- 视觉沿用 `explore-feed/empty` 与 `/journals` 的 `JournalEmptyResult`。

---

## 4. 必做状态（逐屏点名，一个不漏）

- [ ] **浏览态默认**（有列表 + 信息行 + 带计数 facet）
- [ ] **检索态默认**（有列表 + 结果行 + chip 条 + 标题高亮）
- [ ] **加载中**：列表 skeleton（沿用 `explore-feed/skeleton`）+ facet skeleton（沿用 `JournalFilterSkeleton`）
- [ ] **无结果**（§3.3 三种文案）
- [ ] **首屏空库**（极端：库里暂无文献 → 友好引导，区别于"检索无结果"；信息行显示 0 篇）
- [ ] **错误**（取数失败）→ 复用全局错误页
- [ ] **分页**：页码式；翻页时列表局部 loading，facet 与检索条不动
- [ ] **facet 局部态**：某组无可用项（如 OA 全 0）→ **整组不渲染**；某组项过多 → 只显 Top N + "更多"展开；期刊 facet 的搜索下拉（输入中 / 有候选 / 无候选）
- **边界**：
  - 标题超长两行截断省略（衬线 + 内联标签渲染，不能截断在标签中间——由 `RichInlineText` 保证，设计只需给两行高度）
  - 期刊名超长（如 "International journal of infectious diseases : IJID : official publication of…"）单行截断
  - 无摘要（库里 3,154 篇）→ 摘要行不占位
  - 证据等级未分级 → 徽章不渲染，不占位；文献类型缺失 → 同
  - 作者超 3 位 → "等 N 位作者"；作者为空 → 该段不显
  - `cites` 全库为 0 → 卡片**不显示被引数**
  - 移动端 facet 抽屉：触发按钮在检索条旁，带当前生效条件数角标（如 "筛选 · 3"）

---

## 5. 复用 vs 新增组件

**复用**（按名引用，别重造）：
- `TopNav`（"文献"项由 PAP-54 解 disabled）· `Footer`
- `Composer` 的四 tab 语义与 `search-modes.ts` 数据 · `PaperCard` 的信息层级 · `RichInlineText` · `EvidenceBadge`
- `/journals` 页整套骨架组件的视觉语言：`JournalsBrowseHead`（面包屑 + eyebrow + h1 + 副文案）· `JournalSearchSortControls` · `JournalActiveChips` · `JournalFilters` / `JournalFiltersServer`（facet 组 / 勾选行 / 开关行 / 可搜索勾选列表）· `JournalPagination` · `JournalEmptyResult` · `JournalGridSkeleton` / `JournalFilterSkeleton`
- `explore-feed/skeleton` `explore-feed/empty` 约定 · 原语 `button` `badge` `input` `sheet` `tabs`
- **全局 404 / 错误页**（v0.5 已设计）

**新增**（描述用途，视觉交 Claude Design）：
- `PapersBrowseHead` —— 面包屑 + eyebrow"按文献浏览"+ h1"浏览全部文献"+ 副文案（对仗 `JournalsBrowseHead`）
- `PaperSearchBar` —— 四 tab + 输入 + 提交的紧凑检索条（`Composer` 的页内变体）
- `PapersInfoRow` / `PapersResultRow` —— 浏览态信息行 / 检索态结果行 + 排序切换
- `PaperListItem` —— `PaperCard` 的紧凑列表变体（无占位按钮、带摘要两行、标题高亮）
- `PaperFilters` —— facet 侧栏 / 抽屉，比 `JournalFilters` 多两样：年份快捷项、期刊可搜索添加
- `PapersEmptyResult` —— 三种无结果文案

---

## 6. 交互与响应式

- **检索**：输入 → 提交（Enter / 按钮）→ URL 更新 → 列表与 facet 同步刷新。不做输入中联想。
- **tab 切换**：切到 PMID / DOI 时输入框切等宽字体（沿用首页 `mono: true`）。
- **排序**：切换即时重排，URL 同步。
- **facet**：勾选即应用（无"应用"按钮），列表收敛 + chip 出现 + 其他组计数收窄。期刊 facet 是"搜索 → 选候选 → 加入"三步。
- **chip**：单个叉掉 / 清除全部，均触发同一套刷新。
- **分页**：页码点击 → 列表局部 loading → 滚回列表顶部。
- **元素状态**：hover / active / focus / disabled（卡片、tab、排序项、facet 项、chip、页码、搜索框均需 focus 可见）。
- **响应式**：
  - 桌面（≥768px）：左 facet 栏（固定宽）+ 右列表；检索条通栏在上。
  - 移动（<768px）：单列；facet 进抽屉 / sheet，触发按钮带生效条件数；检索条精简（tab 可横滑）；chip 条横向滚动。
  - 具体断点与列宽交 Claude Design，简报只给意图。

---

## 7. 约束

- **视觉**：沿用暖纸感 editorial 风格 + 高信息密度；与首页文献流、`/journals`、文献详情页风格连续——用户从任一出口跳过来不该有"换了个站"的感觉。
- **技术**：映射到 **Next 15 App Router / Tailwind v4 / base-ui(shadcn)**；URL 即状态，服务端取数，交互态走 client component（与 `/journals` 同一套做法）。
- **可访问性**：语义化 HTML + ARIA；列表 `list`/`listitem`；facet `group`/`checkbox`；chip 可键盘删除；分页可键盘操作；高亮用 `<mark>`。
- **暗色模式**：不做（边界 G）。
- **本版边界**：无高级检索语法、无自动补全、无相关度 / 被引排序、无收藏 / 导出 / 保存检索式、无 MeSH facet。设计稿里**不要**出现这些入口。

---

## 8. 参考

- **现有相关页**：dev URL `http://localhost:4000`（首页看 Composer 与 PaperCard；`/journals` 看骨架、facet、chip、分页、空态；`/papers/[id]` 看 EvidenceBadge 与标题渲染）。
- **竞品 / 灵感**（取其"什么"）：
  - **PubMed 结果页**：左 facet + 右列表的骨架；facet 计数；结果行的"N results"+ 排序位置。
  - **Europe PMC**：chip 条表达生效条件；facet 组折叠。
  - **Semantic Scholar**：紧凑列表卡的信息层级（标题 > 元信息 > 徽章 > 摘要片段）。

---

## 9. Done 判定

- 覆盖：浏览态 / 检索态 / 无结果态 三屏 × §4 全部状态（含 facet 局部态）× 桌面 + 移动 全部产出。
- 骨架与 `/journals` 同构、卡片与首页 `PaperCard` 同源，不另起视觉体系。
- 交回：记录 handoff prompt + URL 到 PAP-51；下载 zip 快照入 `docs/patra/design/snapshots/`。

---

## 附录 A. 理想字段 → 数据现状映射（给 BE 定缺口）

> 图例：✅ 已暴露（feed / 详情端点已返回）｜🟡 DB 已有·待暴露（需新端点或加字段）｜🔶 需衍生（DB 有原料、需计算）｜🔴 DB 缺失 / 无数据（本版该处按"无数据"设计）
> 数据现状取自 Mac mini `patra_catalog`，2026-09-24，18,807 篇（单个 baseline 文件）。

### A.1 列表卡片

| 字段 | 现状 | 来源 / 备注 |
|------|------|------------|
| 标题 | ✅ | `cat_publication.title`，feed DTO 已返回；含内联标签需 `RichInlineText` |
| 期刊名 | ✅ | feed DTO `journal` |
| 期刊 id（可点跳期刊详情） | 🟡 | `cat_publication.venue_id` 已持久化，feed DTO 未暴露，search DTO 需加 `venueId` |
| 年份 | ✅ | `publication_year` |
| 作者前几位 | ✅ | feed DTO `authors`（展示名数组，131,153 行关联） |
| 文献类型 badge | ✅ | feed DTO `kind`（= primary type） |
| 证据等级 badge | 🔶 | `EvidenceLevel.classify(types)` 已有，详情端点已返回；search DTO 需加 `evidenceLevel` |
| 摘要首两行 | 🟡 | `cat_publication_abstract.plain_text` 已有；search DTO 需加 `abstractSnippet`（BE 截断到 ~300 字）；**3,154 篇无摘要** |
| 来源 | ✅ | feed DTO `source`（当前全部 "PubMed"） |
| DOI / PMID | ✅ | feed DTO |
| 被引数 | 🔴 | `citation_count` 全库 0 → **卡片不显示** |
| 标题高亮片段 | 🔶 | 前端按 `q` 在标题上做子串高亮即可，BE 不需返回 highlight |

### A.2 检索与筛选

| 能力 | 现状 | 来源 / 备注 |
|------|------|------------|
| 关键词（标题子串） | 🟡→新端点 | `title ILIKE '%q%'`，决策 A：不做全文检索、不加索引 |
| 作者名 | 🟡→新端点 | `cat_author` 展示名匹配，经 `cat_publication_author` 关联 |
| PMID / DOI 精确 | 🟡→新端点 | `uk_pmid` / `uk_doi` 唯一约束已有，精确命中 0 或 1 条 |
| 排序：最近更新 | 🟡→新端点 | `last_synced_at` / `created_at`（feed 已按此排） |
| 排序：年份 | 🟡→新端点 | `publication_year` |
| 排序：相关度 / 被引 | 🔴 | 边界 C，不做 |
| 筛选：年份（含近 1/3/5 年快捷） | 🟡→新端点 | `publication_year`；现状 2026: 17,520 / 2025: 1,268 / 2024: 14 |
| 筛选：文献类型 | 🟡→新端点 | `cat_publication_type.type_value`；Top: Journal Article 17,680 / Review 2,040 / Letter 471 / Editorial 349 / Case Reports 318 / Systematic Review 236 / Observational Study 166 / Comparative Study 146 / RCT 139 / Meta-Analysis 110 |
| 筛选：证据等级 | 🔶→新端点 | 决策 D：BE 把等级翻译成类型集合过滤；估算 5 级 346 / 4 级 139 / 3 级 421 / 2 级 2,040 / 1 级 318 / **未分级 ~15,500（约 84%）** |
| 筛选：期刊 | 🟡→新端点 | `venue_id`，2,924 本不同期刊 → facet 必须可搜索；候选下拉复用 `/portal/venues?q=` |
| 筛选：语言 | 🟡→新端点 | `language_base` 生成列；en 18,488 / de 107 / zh 93 / fr 52 / ru 27 / es 19 |
| 筛选：开放获取 | 🔴 | `is_oa` 全库 false、`oa_status` 全 null（PubMed baseline 不含 OA 信息，需 Unpaywall 等来源）→ **本版 facet 计数为 0，整组不渲染**；端点仍支持 `oa=` 参数，数据到位即生效 |
| 筛选：MeSH | — | 边界 A，不做 |
| facet 计数随条件收窄 | 🟡→新端点 | 与 `/portal/venues/facets` 同模式 |

### A.3 浏览态信息行

| 字段 | 现状 | 来源 / 备注 |
|------|------|------------|
| 总篇数 | 🟡→新端点 | search 无条件时的 `total`，或 facets 响应带 `total` |
| 来源 | 🟡 | 当前单一 PubMed；多来源后按 provenance distinct |
| 最近同步时间 | 🟡→新端点 | `max(last_synced_at)`（现值 2026-08-30 12:06 +08）；facets 响应带 `lastSyncedAt` |

**BE 缺口小结**：本页**不需要新采集数据**，也**不需要 schema 变更**。核心工作是新建 `GET /portal/publications/search` + `/search/facets` 两个端点（PAP-52），列表 DTO 在 feed DTO 基础上加 `venueId` / `evidenceLevel` / `abstractSnippet` 三个字段。两处真实的数据空洞——被引数全 0、OA 全 false——本版按"不显示 / 不渲染"设计，不造假数据。
