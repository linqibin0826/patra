# PubMed XML 解析 vs 数据库表 — 差距分析报告

## 总览：三层比对

```
PubMed XML (DTD 元素)
    ↓  CanonicalPublicationParsingStrategy 解析
CanonicalPublication (Shared Kernel 模型)
    ↓  PubmedArticleItemProcessor 处理
PublicationImportResult (9 种关联数据)
    ↓  PublicationItemWriter 写入
MySQL 表 (34 张 publication 相关表)
```

对比目标：找出**XML 中有但没解析**、**解析了但没入库**的数据。

## 一、已完成的数据链路（XML → 解析 → 入库）

| PubMed XML 元素 | 解析到 CanonicalPublication | 入库表 | 状态 |
|-----------------|---------------------------|--------|------|
| `<PMID>` | `identifiers` (PMID) | `cat_publication.pmid` + `cat_publication_identifier` | ✅ 完成 |
| `<ArticleId IdType="doi">` | `identifiers` (DOI) | `cat_publication.doi` + `cat_publication_identifier` | ✅ 完成 |
| `<ArticleId IdType="pmc">` | `identifiers` (PMC) | `cat_publication_identifier` | ✅ 完成 |
| `<ArticleTitle>` | `title` | `cat_publication.title` | ✅ 完成 |
| `<VernacularTitle>` | `originalTitle` | `cat_publication.original_title` | ✅ 完成 |
| `<Language>` | `language` (ISO 639-3→639-1) | `cat_publication.language_raw` + `language_code` | ✅ 完成 |
| `<PublicationStatus>` | `publicationStatus` | `cat_publication.publication_status` | ✅ 完成 |
| `<AuthorList CompleteYN>` | `authorsComplete` | `cat_publication.authors_complete` | ✅ 完成 |
| `<Journal>` + `<MedlineJournalInfo>` | `journal` | `cat_publication.venue_id` + `venue_instance_id` | ✅ 完成 |
| `<PubDate>` | `dates` | `cat_publication.publication_year` + `cat_publication_date` | ✅ 完成 |
| `<AuthorList>/<Author>` | `authors` | `cat_author` + `cat_publication_author` + `cat_publication_author_affiliation` | ✅ 完成 |
| `<Abstract>/<AbstractText>` | `abstractContent` | `cat_publication_abstract` | ✅ 完成 |
| `<OtherAbstract>` | `alternativeAbstracts` | `cat_publication_alternative_abstract` | ✅ 完成 |
| `<MeshHeadingList>` | `meshHeadings` | `cat_publication_mesh_heading` + `cat_publication_mesh_qualifier` | ✅ 完成 |
| `<SupplMeshList>` | `supplMeshNames` | `cat_publication_suppl_mesh` | ✅ 完成 |
| `<PublicationTypeList>` | `publicationTypes` | `cat_publication_type` | ✅ 完成 |
| `<Pagination>/<MedlinePgn>` | `pagination` | 通过 `cat_publication.ext_data` 或主表字段 | ✅ 完成 |
| `<KeywordList>` | `keywords` | `cat_keyword` + `cat_publication_keyword` | ✅ 完成 |
| `<MedlineCitation Status>` | (Processor 提取) | `cat_publication_metadata.indexing_status` | ✅ 完成 |

## 二、XML 中有但未解析的数据（核心缺失）

以下是 PubMed XML DTD 中存在、`CanonicalPublication` 模型也有对应字段、但 **`CanonicalPublicationParsingStrategy` 没有解析**的数据：

### 2.1 `parseMedlineCitation()` 中跳过的元素

查看策略的 `parseMedlineCitation()` 方法，它的 switch 只处理了 6 个子元素，但 `<MedlineCitation>` 下还有大量元素被 `default -> {}` 跳过：

| XML 元素 | 数据库表（已建） | CanonicalPublication 字段 | 优先级 |
|----------|----------------|--------------------------|--------|
| ~~`<KeywordList>`~~ | `cat_keyword` + `cat_publication_keyword` | `keywords` | ✅ 已完成 |
| **`<InvestigatorList>`** | `cat_investigator` + `cat_publication_investigator` | `investigators` | 🟡 中 |
| **`<PersonalNameSubjectList>`** | `cat_publication_personal_name_subject` | `personalNameSubjects` | 🟢 低 |
| **`<ChemicalList>`** | (无独立表，可用 `ext_data`) | `substances` | 🟡 中 |
| **`<GeneSymbolList>`** | (无独立表) | `genes` | 🟢 低 |
| **`<CommentsCorrectionsList>`** | `cat_publication_related_item` | `relatedItems` | 🟡 中 |
| **`<CoiStatement>`** | `cat_publication.conflict_of_interest` | `conflictOfInterestStatement` | 🟢 低 |

### 2.2 `parseArticle()` 中跳过的元素

`<Article>` 下的 switch 也只处理了 8 个元素：

| XML 元素 | 数据库表（已建） | CanonicalPublication 字段 | 优先级 |
|----------|----------------|--------------------------|--------|
| **`<GrantList>`** | `cat_publication_funding` | `funding` | 🔴 高 |
| **`<DataBankList>`** | `cat_publication_external_reference` | `externalReferences` | 🟡 中 |
| `<Article PubModel="...">` 属性 | `cat_publication.media_type` | `mediaType` | 🟢 低 |

### 2.3 `parsePubmedData()` 中跳过的元素

| XML 元素 | 数据库表（已建） | CanonicalPublication 字段 | 优先级 |
|----------|----------------|--------------------------|--------|
| **`<History>/<PubMedPubDate>`** | `cat_publication_history` | `publicationHistory` | 🟡 中 |
| **`<ReferenceList>`** | `cat_publication_reference` | (无直接字段) | 🔴 高 |
| `<ObjectList>` | `cat_publication_supplemental_object` | `supplementalObjects` | 🟢 低 |

**关键洞察：**

- 观察 Processor 的 9 种关联数据构建方法（`buildKeywordData()`、`buildFundingData()`、`buildInvestigatorData()` 等），它们从 `CanonicalPublication` 的对应字段读取数据。但由于**解析器没有填充这些字段**，Processor 实际上拿到的是空列表，这些 `buildXxx()` 方法实际在空转
- 换句话说，**Processor 已经写好了处理逻辑，瓶颈在 Parser 没有解析**。补上解析后，整个写入链路就打通了

## 三、按优先级排序的缺失清单

### 🔴 高优先级（有表、有处理逻辑、只差解析）

| # | XML 元素 | 目标表 | 估计数据量 | 说明 |
|---|---------|--------|-----------|------|
| ~~1~~ | ~~**`<KeywordList>`**~~ | ~~`cat_keyword` + `cat_publication_keyword`~~ | ~~~1500 万/初始~~ | ✅ 已完成（Parser 解析 + 端到端链路打通） |
| 2 | **`<GrantList>`** | `cat_publication_funding` | ~750 万/初始 | 资助来源，科研评估关键数据 |
| 3 | **`<ReferenceList>`** | `cat_publication_reference` | ~2 亿/初始 | 引用网络，影响力分析基础 |

### 🟡 中优先级（有表、Processor 部分准备）

| # | XML 元素 | 目标表 | 说明 |
|---|---------|--------|------|
| 4 | **`<History>/<PubMedPubDate>`** | `cat_publication_history` | 投稿/接收/发表时间线，审稿周期分析 |
| 5 | **`<InvestigatorList>`** | `cat_investigator` + `cat_publication_investigator` | 临床试验 PI 信息 |
| 6 | **`<CommentsCorrectionsList>`** | `cat_publication_related_item` | 撤稿、勘误、评论关系 |
| 7 | **`<DataBankList>`** | `cat_publication_external_reference` | GenBank、ClinicalTrials.gov 关联 |
| 8 | **`<ChemicalList>`** | 需新建表或用 `ext_data` | 化学物质（CAS 号、注册号） |

### 🟢 低优先级（数据量小或使用频率低）

| # | XML 元素 | 目标表 | 说明 |
|---|---------|--------|------|
| 9 | **`<PersonalNameSubjectList>`** | `cat_publication_personal_name_subject` | 传记/历史类文献主题人物 |
| 10 | `<CoiStatement>` | `cat_publication.conflict_of_interest` | 利益冲突声明 |
| 11 | `<Article PubModel>` | `cat_publication.media_type` | Print/Electronic/Both |
| 12 | `<ObjectList>` | `cat_publication_supplemental_object` | 补充材料 |
| 13 | `<GeneSymbolList>` | 需新建表 | 基因符号列表 |

## 四、数据库表有但完全无数据源的表

以下表已建好，但当前 **PubMed Baseline 导入流程中完全没有数据写入**：

| 表名 | 设计目的 | 数据来源说明 |
|------|---------|-------------|
| `cat_publication_oa_location` | OA 获取位置 | 数据来自 Unpaywall/OpenAlex，非 PubMed |
| `cat_publication_reference` | 参考文献 | PubMed XML 有 `<ReferenceList>`，但未解析 |
| `cat_publication_history` | 发布历史 | PubMed XML 有 `<History>`，但未解析 |
| `cat_publication_external_reference` | 外部引用 | PubMed XML 有 `<DataBankList>`，但未解析 |
| `cat_publication_related_item` | 相关项目 | PubMed XML 有 `<CommentsCorrectionsList>`，但未解析 |
| `cat_publication_supplemental_object` | 补充对象 | PubMed XML 有 `<ObjectList>`，但未解析 |

**关键洞察：**

- 表结构设计得非常前瞻——34 张表覆盖了 PubMed DTD 的几乎所有数据元素。但 **XML 解析器目前只解析了约 60% 的可用数据**
- 架构上的好消息是：六边形架构的分层设计让"补解析"非常简单——只需修改 `CanonicalPublicationParsingStrategy` 添加新的 `parseXxx()` 方法，Processor 和 Writer 的处理逻辑大多已就绪
- 最有价值的缺失是 **KeywordList**（影响全文检索质量）和 **GrantList**（科研评估刚需），这两个建议优先补上

## 五、解析器补全路线图建议

如果要逐步补全，推荐按以下顺序：

```
Phase 1: 高价值 + 低复杂度
├── ✅ KeywordList → cat_keyword + cat_publication_keyword (已完成)
├── GrantList → cat_publication_funding (含 FunderLookupPort 匹配)
└── CoiStatement → cat_publication.conflict_of_interest
    PubModel 属性 → cat_publication.media_type

Phase 2: 中价值 + 中复杂度
├── History/PubMedPubDate → cat_publication_history
├── CommentsCorrectionsList → cat_publication_related_item
├── DataBankList → cat_publication_external_reference
└── InvestigatorList → cat_investigator + cat_publication_investigator

Phase 3: 补充数据
├── PersonalNameSubjectList → cat_publication_personal_name_subject
├── ChemicalList → 新建 cat_publication_chemical 或用 ext_data
├── ObjectList → cat_publication_supplemental_object
└── GeneSymbolList → 新建表或用 ext_data
```

每个 Phase 的改动范围都是：

1. `PubmedXmlElements` 添加新常量
2. `CanonicalPublicationParsingStrategy` 添加 `parseXxx()` 方法 + `ParsedFields` 新字段 + `buildPublication()` 新映射
3. 确认 Processor 的 `buildXxxData()` 能处理新数据（大部分已实现）
