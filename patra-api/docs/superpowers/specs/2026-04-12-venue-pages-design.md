# Patra 期刊页面信息架构设计

## 概述

为 Patra 医学出版物数据平台设计期刊相关前端页面，覆盖期刊列表、期刊详情、文章列表、期刊对比四个页面。

**目标用户：** 中国医学科研人员和研究生

**核心场景（全覆盖）：**
1. 选刊投稿 — 通过分区、IF、OA 费用、研究方向匹配找到合适期刊
2. 期刊评估 — 通过预警、自引率、趋势判断期刊质量
3. 文献检索入口 — 通过卷/期浏览期刊下的文章
4. 学术信息浏览 — 综合了解一本期刊的全貌

---

## 页面 1：期刊列表页

### 结构

```
搜索栏 → 筛选区 → 工具栏 → 期刊列表区
```

### 搜索

支持刊名、ISSN、缩写的模糊搜索。

### 筛选维度

| 筛选项 | 数据来源 | 类型 |
|--------|---------|------|
| 学科/研究方向 | `jcr_rating.research_direction` | 多选 |
| JCR 分区 | `jcr_rating.jif_quartile` | 多选(Q1/Q2/Q3/Q4) |
| 中科院大类分区 | `cas_rating.major_quartile` | 多选(1区/2区/3区/4区) |
| Top 期刊 | `cas_rating.is_top_journal` | 开关 |
| CiteScore 分区 | `scopus_rating.quartile` | 多选 |
| OA 类型 | `venue.open_access.oa_type` | 多选(Gold/Hybrid/Bronze/非OA) |
| 收录集 | `jcr_rating.collection` | 多选(SCIE/SSCI/AHCI) |
| 国家 | `venue.country_code` | 多选 |
| 预警状态 | `cas_warning.in_warning_list` | 开关 |

### 排序

支持按 IF、CiteScore、h-index、被引量排序。

### 视图切换

**卡片视图（默认）：**
- 网格布局，桌面端一行 3 张
- 每张卡片：封面/占位符、标题、出版商、国家、研究方向标签、核心指标徽章（IF+分区、CAS分区+Top、CiteScore+分区）、OA 标记、预警标记
- 无封面时用期刊首字母 + 学科对应颜色生成占位符

**列表视图：**
- 表格形式，列：标题、IF、JCR分区、CAS大类分区、Top、CiteScore、h-index、OA、国家
- 一屏 15-20 行
- 点击列头排序

### 对比功能

- 卡片/列表中可勾选期刊加入对比（2-5 本）
- 顶部浮动工具条显示已选数量，点击进入对比页

---

## 页面 2：期刊详情页

### 布局策略

混合式：顶部核心信息区（始终可见） + 下方 Tab 切换详细内容。

### 顶部常驻区（Header）

**基本信息：**
- 封面图/占位符
- 期刊全称 + 缩写
- ISSN / ISSN-L
- 出版商 · 国家 · 出版频率 · 语言
- 创刊年份 · MEDLINE 收录状态
- 标签：收录集(SCIE/SSCI)、OA 类型、预警标记(如有)

**核心指标卡片（4 张）：**

| 卡片 | 内容 | 来源 |
|------|------|------|
| IF | 最新 IF 值 + JCR 分区 + Percentile | `cat_venue_jcr_rating` 最新年 |
| 中科院 | 大类分区 + Top 标记 | `cat_venue_cas_rating` 最新年 |
| CiteScore | 最新 CiteScore + 分区 + Percentile | `cat_venue_scopus_rating` 最新年 |
| h-index | h-index 值 | `venue.citation_metrics` |

每张卡片标注数据年份和来源体系。

**操作按钮：** 访问官网、加入对比

### Tab 1：概览

| 区块 | 内容 | 数据来源 |
|------|------|---------|
| 出版信息 | 出版商、国家、频率、语言、创刊年份、是否停刊 | `publicationProfile` |
| 关联学会 | 学会名称 + URL | `affiliatedSocieties` |
| 期刊关系 | 前身/后续/合并关系（可点击跳转） | `VenueRelation` |
| 研究范围 | MeSH 主题词云/标签（主要主题高亮） | `VenueMesh` |
| 年度趋势(精简) | 发文量 + 被引量 双轴折线图（近10年） | `VenuePublicationStats` |
| 标识符 | ISSN、NLM ID、OpenAlex ID、Wikidata 等 | `VenueIdentifier` |

### Tab 2：指标与分区

| 区块 | 内容 | 数据来源 |
|------|------|---------|
| JCR 评级 | IF、JCI、自引率、学科排名("2/136")、分区、收录集 | `cat_venue_jcr_rating` |
| 中科院分区 | 大类+小类分区、Top标记、版本(升级版/基础版)、综述期刊标记 | `cat_venue_cas_rating` |
| Scopus 评级 | CiteScore、SJR、SNIP、文献数/引用数、被引比例 | `cat_venue_scopus_rating` |
| 预警信息 | 预警级别、历史预警记录、原始描述文本 | `cat_venue_cas_warning` |
| 历史趋势图 | IF + CiteScore + SJR 多线叠加折线图（全部年份） | 三张 rating 表按年 |
| 分区变化 | JCR 分区 + CAS 分区逐年变化表格/色块图 | rating 表按年 |

### Tab 3：收录与索引

| 区块 | 内容 | 数据来源 |
|------|------|---------|
| 当前索引状态 | MEDLINE 状态(C/Y/N/D)、ISO 缩写、MedlineTa | `indexingInfo` |
| 索引历史时间线 | 各索引源(MEDLINE/PMC)的收录起止年份、全量/选择性 | `VenueIndexingHistory` |
| OA 详情 | OA 类型、DOAJ 收录、APC 费用(多币种) | `openAccess` |
| 年度 OA 比例 | OA 论文占比趋势图 | `VenuePublicationStats.oaWorksCount` |

### Tab 4：卷期与文章

| 区块 | 内容 | 数据来源 |
|------|------|---------|
| 年份导航 | 按年份分组（最新在前），展开显示该年所有卷/期 | `VenueInstance` |
| 卷/期卡片 | Volume X, Issue Y · 出版日期 · 文章数 | `VenueInstance` |
| 点击行为 | 跳转到独立的文章列表页 | — |

---

## 页面 3：文章列表页

**路由：** `/venues/:id/issues/:instanceId`

### 结构

```
面包屑: 期刊名 > 年份 > Volume X, Issue Y
期号摘要: 出版日期 · 共 N 篇文章
排序: [默认(页码)] [被引量↓] [发表日期]
文章列表
```

### 文章卡片

每篇文章展示：
- 文章标题（可点击跳转文章详情）
- 作者列表（超过3人显示 et al.）
- Pages · DOI · 被引次数
- 文章类型标签（Review/Original Article/Letter 等）
- OA 标记

### 导航

- 面包屑支持快速返回期刊详情
- 可切换到同期刊其他卷/期（侧边或下拉）

---

## 页面 4：期刊对比页

**路由：** `/venues/compare?ids=1,2,3`

### 结构

```
指标对照表 → 趋势对比图 → 分区变化对比
```

### 指标对照表

并排展示 2-5 本期刊的核心指标：

| 对比维度 | 来源 |
|---------|------|
| IF | `cat_venue_jcr_rating` |
| JCR 分区 | `cat_venue_jcr_rating` |
| CAS 大类分区 + Top | `cat_venue_cas_rating` |
| CiteScore | `cat_venue_scopus_rating` |
| SJR | `cat_venue_scopus_rating` |
| h-index | `venue.citation_metrics` |
| 自引率 | `cat_venue_jcr_rating` |
| OA 类型 | `venue.open_access` |
| APC (USD) | `venue.open_access` |
| 国家 | `venue.country_code` |
| 出版商 | `venue.publication_profile` |
| 预警状态 | `cat_venue_cas_warning` |

同一指标中最优值高亮显示。

### 趋势对比图

- IF 趋势叠加（多线折线图，每刊一色）
- CiteScore 趋势叠加
- 发文量趋势叠加

### 分区变化对比

色块时间轴，直观展示近 5 年各期刊 JCR/CAS 分区升降。

### 交互

- 支持随时添加/移除对比期刊
- URL 携带 ids 参数，可分享对比结果

---

## 设计约束与说明

1. **封面图策略：** 仅 SCIE 期刊有封面（来自 LetPub），其余使用期刊首字母 + 学科对应颜色生成占位符
2. **数据年份标注：** 所有指标必须标注数据年份，避免用户误解时效性
3. **三级钻取：** 期刊详情页(含卷期Tab) → 独立文章列表页，URL 可独立分享
4. **响应式：** 桌面端优先设计，移动端做适配
5. **评级数据来源：** JCR/CAS 来自 LetPub 爬取，Scopus 来自官方 API，OpenAlex 指标来自 OpenAlex API
