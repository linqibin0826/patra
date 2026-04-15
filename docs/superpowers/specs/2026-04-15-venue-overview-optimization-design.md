# 期刊详情页·概览 Tab 定向优化设计

- **日期**：2026-04-15
- **作者**：Lin Qibin · Claude（Opus 4.6）
- **基线**：`docs/superpowers/specs/2026-04-12-venue-pages-design.md`（第一版已落地）
- **服务范围**：patra-web（主） + patra-catalog（后端 1 个新端点）
- **状态**：草案，待实施

## 1. 背景

第一版期刊详情页已实现 Header + 4 个 Tab（overview/metrics/indexing/issues）。在使用中暴露三个问题：

1. **Header 与概览左栏字段重复**：出版机构、国家、刊期、语言、创刊年在两处重复展示（头部 meta 行 + 概览 Publishing Info）
2. **窄柱图表可读性差**：年度趋势图放在概览右栏 24rem 侧栏，折线拥挤
3. **概览缺转化出口**：看完概览没有"去读文章"的下一跳，只能靠 tab 切换

## 2. 目标用户与故事线

**目标用户**：文献检索者（非投稿作者）。他们的决策链路：
- 「这本刊可靠吗？」→ 看身份和权威指标
- 「发不发我关心的主题？」→ 看主题分布
- 「最近在发什么？」→ 看最新动态
- 「最有影响力的工作？」→ 看高被引

**故事线（A 方案）**：`身份 → 影响力 → 主题 → 趋势 → 出口`

页面三段的承载：
- **Header**：身份 + 影响力
- **概览 Tab**：主题 + 趋势 + 出口
- **其他 Tab**：不在本次改动范围

## 3. 设计决策摘要

| 决策点 | 选择 | 理由 |
|---|---|---|
| Header 与 Overview 字段划分 | γ · 混合方案 | Header 只保留身份证级 meta（ISSN-L/机构/国家），其他字段下沉 |
| MeSH 主题展示方式 | C · Top-N 条形列表（视觉） + 分组兜底 | 条形比词云更精确传递"占比" |
| MeSH 数据来源（本期） | **前端假数据** | 真实频率需聚合 `cat_publication_mesh`，推迟到 ES 阶段 |
| 出口区内容 | D · 最新一期 + Top5 高被引 + 外链行 | 覆盖"最近" + "最具影响力" + "去官网"三种心智 |
| 指标卡是否升级 | **不升级**（不加 sparkline/分区锚点） | 保留精力给出口区 |

## 4. 页面结构变更

### 4.1 Header 改造

**保留**：头像、标题、缩写、tag 行、4 指标卡、action 按钮
**删除 meta 行的**：刊期（frequency）、主要语言（primaryLanguage）、创刊年（startYear/endYear）
**保留 meta 行的**：ISSN-L、出版机构（`hostOrganization.name`）、国家（`countryCode`）

视觉：meta 行从 6-7 项瘦身到 3 项，视觉更聚焦"身份"。

### 4.2 概览 Tab 新骨架（3 屏滚动）

```
┌─ 第一屏（两栏 50/50）────────────────┬────────────────────────┐
│ 🧬 MeSH 主题分布                      │ 📈 年度发表 / 被引趋势  │
│ （Top-N 条形，假数据）                 │ （VenueRatingTrendChart）│
│                                       │                         │
└──────────────────────────────────────┴─────────────────────────┘

┌─ 第二屏（两栏 60/40）────────────────┬────────────────────────┐
│ 🆕 最新一期卡（整卡可点）              │ 🔥 Top 5 高被引文章     │
│ Volume/Issue · 日期 · 文章数           │ （整行可点，跳 pub 详情）│
│ [浏览当期文章 →]                       │ [查看全部文章 →]        │
└──────────────────────────────────────┴─────────────────────────┘

┌─ 第三屏（两栏 1fr/24rem）────────────┬────────────────────────┐
│ 📚 出版档案                            │ 🔖 Identifiers         │
│   Publishing Info（下沉版，5 字段）    │   ISSN-L / NLM ID /     │
│   Affiliated Societies                │   OpenAlex ID           │
│   Venue Relations                     │                         │
│                                       │ 🔗 外部链接             │
│                                       │   官网 / DOAJ / Crossref│
│                                       │   ISSN Portal / PubMed  │
└──────────────────────────────────────┴─────────────────────────┘
```

### 4.3 档案下沉版的 Publishing Info 字段

下沉到第三屏的完整列表：
- Frequency（刊期，从 Header 下沉）
- Primary language（主要语言，从 Header 下沉）
- Founded / Ceased（创刊年-停刊年，从 Header 下沉）
- Publisher name（保留在 Header，概览不重复）
- Country（保留在 Header，概览不重复）
- ISSN-L（保留在 Header，概览不重复）

即：概览档案区**只展示 Header 没显示的 3 项**，确保零重复。

## 5. 模块详细设计

### 5.1 VenueMeshGroupedList（MeSH 主题·假数据 Top-N 条形）

**目的**：概览第一屏左侧，视觉等位于右侧趋势图。

**视觉**
```
MeSH 主题分布（示例数据 · 待 ES 上线）
────────────────────────────────────────
Neoplasms *          ████████████ 42%
Drug Therapy         ███████      26%
Immunology           █████        18%
Prognosis            ████         12%
Molecular Biology    ███          10%
  展开看全部 12 项 ↓
────────────────────────────────────────
* = majorTopic（绿色）  其他（紫色）
```

**数据源**：
- descriptor 列表使用 venue 真实的 `meshHeadings`（来自 `cat_venue_mesh`，Serfile 数据）
- `count` 和 `percentage` **前端 mock**：基于 descriptor 索引生成递减的假数字（或固定权重表）
- 顶栏显示一行小字："示例数据 · ES 上线后替换为真实频率"（i18n key）

**Mock 规则**（纯前端实现）：
- majorTopic 先排序到前面
- count 用递减函数（例如 `Math.round(500 * 0.8 ** index)`），保证视觉"真实但假"
- percentage = `count / sum(counts)`
- 条形宽度 = `count / max(counts)`

**交互**：
- 默认展开 top 5；"展开看全部 N 项 ↓"点击展开剩余
- majorTopic 用 `*` 标记 + emerald 色；非 major 用 violet 色
- 每行 hover：tooltip 显示 descriptor + qualifier（如有）

**边界**：
- `meshHeadings` 为空 → 模块整体不渲染
- count < 3 个 descriptor → 不折叠，全展开

**未来替换路径**：组件 prop 签名保持 `items: Array<{name, count, percentage, isMajor}>`，ES 上线后只替换数据源（从 mock 函数变为后端字段），组件零改动。

### 5.2 VenueLatestIssueCard（最新一期卡）

**目的**：概览第二屏左 60%，出口区主 CTA。

**视觉**
```
┌──────────────────────────────────────┐
│ 🆕 最新一期                           │
│                                      │
│   Vol 30 · Issue 4                   │
│   2026 年 4 月 15 日                  │
│                                      │
│   共 42 篇文章                        │
│                                      │
│   [浏览当期文章 →]                    │
└──────────────────────────────────────┘
```

**数据源**：复用 `useVenueInstances(id, { page: 1, pageSize: 1 })`
- DAO 已默认 `publication_year DESC, volume DESC, issue DESC` 排序（见 `VenueInstanceDao.findVenueInstancesWithPubCount`）
- 返回首条即最新一期
- 零后端改动

**交互**：
- 整卡 `NuxtLink` 到 `/venues/{id}/issues/{instanceId}`
- 日期使用现有 `formatPubDate(year, month, day)` 工具
- Vol/Issue 使用现有 i18n key `venues.detail.issues.volume / issue`

**边界**：
- instances 为空 → 卡降级为提示"暂无已索引的期次"（不隐藏，仍占位，因为是出口区主位）
- 加载中 → `AppSkeleton`

### 5.3 VenueTopPublicationsList（Top 5 高被引）

**目的**：概览第二屏右 40%，出口区次 CTA。

**视觉**
```
┌────────────────────────────────────┐
│ 🔥 高被引文章                        │
│                                    │
│ 1. CAR-T cell therapy in...  2023  │
│    ⬢ 1,247 次引用                   │
│                                    │
│ 2. Genomic landscape of...   2022  │
│    ⬢ 983 次引用                     │
│                                    │
│ ...                                │
│  [查看全部文章 →]                   │
└────────────────────────────────────┘
```

**数据源**：**后端新端点 BE-1**（详见 §6）
```
GET /catalog/venues/{id}/top-publications?limit=5
```

**交互**：
- 每行 `NuxtLink` 到 `/publications/{pubId}`
- title 两行截断，超出 tooltip
- "查看全部文章 →" 链接到 issues tab 或 publications 列表（视现有路由）

**边界**：
- 响应为空 / 接口错误 → 整个模块 collapse 不渲染
- 加载中 → 5 条 skeleton

### 5.4 VenueExternalLinks（外链行）

**目的**：概览第三屏右下角，次级能力。

**视觉**
```
🔗 外部链接
┌────────┐ ┌────────┐ ┌─────────┐ ┌──────┐ ┌────────┐
│ 🌐 官网 │ │ 📚 DOAJ│ │Crossref│ │ ISSN │ │ PubMed │
└────────┘ └────────┘ └─────────┘ └──────┘ └────────┘
```

**数据源**：**纯前端组装**（零后端改动）

| 链接 | 条件 | URL 模板 |
|---|---|---|
| 官网 | `publicationProfile.homepageUrl` 存在 | 直接用 |
| DOAJ | `issnL` 存在 | `https://doaj.org/toc/{issnL}` |
| Crossref | `title` 存在 | `https://search.crossref.org/?from_ui=yes&container-title={encodeURIComponent(title)}` |
| ISSN Portal | `issnL` 存在 | `https://portal.issn.org/resource/ISSN/{issnL}` |
| PubMed | `nlmId` 存在 | `https://www.ncbi.nlm.nih.gov/nlmcatalog/{nlmId}` |

**交互**：
- 所有链接 `target="_blank" rel="noopener"`
- 缺字段的链接**直接不渲染**（不要灰态 chip）
- chip 样式统一：浅色背景 + 图标 + hover 浅紫描边

### 5.5 年度趋势图（位置调整）

**无组件改动**，只调整父布局：
- 从第三屏侧栏（24rem 宽）搬到第一屏右栏（lg 下 50%，约 560px）
- 复用 `VenueRatingTrendChart`

## 6. 后端改动清单

### BE-1 · Top-N 高被引文章端点

**端点**：`GET /catalog/venues/{id}/top-publications`

**参数**：
| 参数 | 类型 | 必填 | 默认 | 范围 | 说明 |
|---|---|---|---|---|---|
| `limit` | int | 否 | 5 | 1-20 | 返回条数 |
| `since` | int | 否 | 无（全期）| 合法年份 | 发表年下限 |

**响应**（`List<PublicationCardView>`）：
```json
[
  {
    "id": 12345,
    "title": "CAR-T cell therapy in refractory acute leukemia",
    "publicationYear": 2023,
    "citationCount": 1247,
    "doi": "10.xxxx/xxxxx"
  }
]
```

**查询逻辑**：
```sql
SELECT id, title, publication_year, citation_count, doi
FROM cat_publication
WHERE venue_id = :venueId
  [AND publication_year >= :since]
  AND deleted_at IS NULL
ORDER BY citation_count DESC, publication_year DESC
LIMIT :limit
```

**性能**：非聚合查询，需确认 `cat_publication` 上存在 `(venue_id, citation_count)` 或 `(venue_id)` + `citation_count` 的有效索引。若缺失，作为子任务补索引。

**六边形落位**：
- Domain：`Publication` 聚合；新增 Domain 类 `PublicationCardReadModel`（或复用既有）
- Port：`PublicationReadPort` 新增 `findTopByVenue(venueId, limit, since)`
- Infra：`PublicationReadAdapter` 实现 + `PublicationDao` 对应方法
- Adapter：挂在 `VenueController` 下（保持以 venue 为主体的 URL）

**不做**：
- 不做"近 5 年 top"时间窗下拉（前端不暴露 `since`，仅端点保留参数）
- 不做领域分区 top（如按 MeSH 分组）

## 7. 前端改动清单

| 类型 | 文件 | 说明 |
|---|---|---|
| 改造 | `app/components/venue/VenueDetailHeader.vue` | Meta 行瘦身到 3 项 |
| 重写 | `app/components/venue/VenueTabOverview.vue` | 3 屏骨架，重排模块 |
| 新建 | `app/components/venue/VenueMeshGroupedList.vue` | MeSH 假数据 Top-N 条形 |
| 新建 | `app/components/venue/VenueLatestIssueCard.vue` | 最新一期卡 |
| 新建 | `app/components/venue/VenueTopPublicationsList.vue` | Top 5 高被引 |
| 新建 | `app/components/venue/VenueExternalLinks.vue` | 外链 chip 行 |
| 新建 | `app/composables/useVenueTopPublications.ts` | 接 BE-1 |
| 新建 | `app/utils/mesh-mock.ts` | MeSH 假数据生成函数 |
| i18n | `i18n/locales/zh-CN.json` + `en.json` | 新增 key |

**新 i18n key**：
- `venues.detail.overview.meshDistribution`（标题）
- `venues.detail.overview.meshDistributionNote`（"示例数据 · ES 上线后替换为真实频率"）
- `venues.detail.overview.expandAll` / `collapseAll`
- `venues.detail.overview.latestIssue`
- `venues.detail.overview.browseCurrentIssue`
- `venues.detail.overview.topCited`
- `venues.detail.overview.citationCount`（"{count} 次引用"）
- `venues.detail.overview.viewAllPublications`
- `venues.detail.overview.externalLinks`
- `venues.detail.overview.links.*`（官网、DOAJ、Crossref、ISSN、PubMed）

## 8. 验收标准

- [ ] Header 三项瘦身（刊期/语言/创刊年消失）
- [ ] 概览第一屏：左 MeSH 条形 + 右趋势图同高度
- [ ] 概览第二屏：左最新一期卡（可点）+ 右 Top5 高被引（可点）
- [ ] 概览第三屏：左档案区（不含 ISSN-L/机构/国家）+ 右 identifiers + 外链行
- [ ] MeSH 模块显示"示例数据"提示，count/percentage 为假数据但视觉合理
- [ ] Top5 文章列表由真实后端 BE-1 驱动，点击跳 publication 详情
- [ ] 最新一期卡点击跳 `/venues/{id}/issues/{iid}`
- [ ] 外链按字段可用性动态渲染，缺字段的 chip 不显示
- [ ] `pnpm typecheck` 通过，无 any、无死 i18n key
- [ ] 加载中 / 无数据 / 错误 三态均有合理兜底
- [ ] BE-1 有单元测试 + 集成测试（基于现有 `VenueReadAdapterInstancesIT` 模式）
- [ ] BE-1 端点契约文档化到 OpenAPI（自动由 springdoc 生成）

## 9. Not-doing（本次明确不做）

1. ❌ Header 指标卡升级（sparkline / 分区锚点）
2. ❌ MeSH 真实频率聚合 → 推迟到 ES 阶段
3. ❌ MeSH 时间窗口切片（近 3/5 年选择器）
4. ❌ 官网元数据丰富（icon fetch / 截图）
5. ❌ 最新一期卡的封面图
6. ❌ Top-N 高被引行的引用曲线 sparkline
7. ❌ Metrics / Indexing / Issues Tab 的任何改动
8. ❌ 对比页面（compare）的改动
9. ❌ 列表页 `/venues` 的改动

## 10. 后续演进（不在本 spec 范围）

- **ES 接入后**：替换 MeSH 假数据源为真实频率，组件签名不变
- **高被引升级**：可加 MeSH 分类的"本刊在 X 领域的代表作"
- **最新一期封面**：若后续采集封面图，可加图片层
- **编辑推荐**：若有编辑部精选，可增加"编辑推荐" section

## 11. 参考

- 第一版设计：`docs/superpowers/specs/2026-04-12-venue-pages-design.md`
- 第一版前端实现计划：`patra-web/docs/plans/2026-04-12-venue-pages-frontend.md`
- DESIGN.md（patra-web 设计语言）
- GEMINI.md（patra-web 编码规范）
