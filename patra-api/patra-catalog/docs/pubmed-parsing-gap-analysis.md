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
| `<Language>` | `language` (ISO 639-3 原始代码) | `cat_publication.language_raw` + `language_code` | ✅ 完成 |
| `<PublicationStatus>` | `publicationStatus` | `cat_publication.publication_status` | ✅ 完成 |
| `<AuthorList CompleteYN>` | `authorsComplete` | `cat_publication.authors_complete` | ✅ 完成 |
| `<Journal>` + `<MedlineJournalInfo>` | `journal` | `cat_publication.venue_id` + `venue_instance_id` | ✅ 完成 |
| `<PubDate>` | `dates` | `cat_publication.publication_year` + `cat_publication_date` | ✅ 完成 |
| `<AuthorList>/<Author>` | `authors` | `cat_author` + `cat_publication_author` + `cat_publication_author_affiliation` | ⚠️ Parser 已解析，Processor/Writer/Repository 未实现 |
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

查看策略的 `parseMedlineCitation()` 方法，它的 switch 只处理了 7 个子元素，但 `<MedlineCitation>` 下还有大量元素被 `default -> {}` 跳过：

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

### 2.4 Parser 已解析但后续链路未实现

以下元素 Parser 已完整解析，但 Processor → Writer → Repository 链路未打通：

| XML 元素 | 数据库表（已建） | Parser | Processor | Writer/Repository | 优先级 |
|----------|----------------|--------|-----------|-------------------|--------|
| **`<AuthorList>/<Author>`** | `cat_author` + `cat_publication_author` + `cat_publication_author_affiliation` | ✅ 完整解析（姓名/ORCID/机构/贡献标记） | ❌ 无 `buildAuthorData()` | ❌ 无 `writeAuthorAssociations()` | 🔴 高 |

**作者处理的特殊复杂度**：

- 作者是**独立聚合根**（`AuthorAggregate`），不像 Keyword/MeshHeading 那样是文献的附属数据
- 需要跨聚合去重匹配逻辑（ORCID → dedupKey → 新建）
- 涉及多对多关联（`cat_publication_author`）和机构归属（`cat_publication_author_affiliation`）
- 可参考 `Investigator` 的处理模式（也是独立实体 + 去重匹配）

**关键洞察：**

- 观察 Processor 的 9 种关联数据构建方法（`buildKeywordData()`、`buildFundingData()`、`buildInvestigatorData()` 等），它们从 `CanonicalPublication` 的对应字段读取数据。`buildKeywordData()` 已随 KeywordList 解析完成而打通，但其余方法由于**解析器没有填充对应字段**，Processor 实际上拿到的是空列表，这些 `buildXxx()` 方法实际在空转
- 换句话说，**Processor 已经写好了处理逻辑，瓶颈在 Parser 没有解析**。KeywordList 已验证了这一模式：补上解析后，整个写入链路即可打通
- **作者是例外**：Parser 已完成，瓶颈在 Processor/Writer/Repository 层，且因涉及独立聚合根去重，实现复杂度显著高于其他元素

## 三、按优先级排序的缺失清单

### 🔴 高优先级

| # | XML 元素 | 目标表 | 估计数据量 | 说明 | 瓶颈 |
|---|---------|--------|-----------|------|------|
| ~~1~~ | ~~**`<KeywordList>`**~~ | ~~`cat_keyword` + `cat_publication_keyword`~~ | ~~~1500 万/初始~~ | ✅ 已完成 | — |
| 2 | **`<AuthorList>/<Author>`** | `cat_author` + `cat_publication_author` + `cat_publication_author_affiliation` | ~1.4 亿关联/初始 | 文献核心数据，Parser ✅ 已完成 | Processor/Writer/Repository 未实现，涉及独立聚合根去重 |
| 3 | **`<GrantList>`** | `cat_publication_funding` | ~750 万/初始 | 资助来源，科研评估关键数据 | 只差 Parser |
| 4 | **`<ReferenceList>`** | `cat_publication_reference` | ~2 亿/初始 | 引用网络，影响力分析基础 | 只差 Parser + Canonical 模型无字段 |

### 🟡 中优先级（有表、Processor 部分准备）

| # | XML 元素 | 目标表 | 说明 |
|---|---------|--------|------|
| 5 | **`<History>/<PubMedPubDate>`** | `cat_publication_history` | 投稿/接收/发表时间线，审稿周期分析 |
| 6 | **`<InvestigatorList>`** | `cat_investigator` + `cat_publication_investigator` | 临床试验 PI 信息 |
| 7 | **`<CommentsCorrectionsList>`** | `cat_publication_related_item` | 撤稿、勘误、评论关系 |
| 8 | **`<DataBankList>`** | `cat_publication_external_reference` | GenBank、ClinicalTrials.gov 关联 |
| 9 | **`<ChemicalList>`** | 需新建表或用 `ext_data` | 化学物质（CAS 号、注册号） |

### 🟢 低优先级（数据量小或使用频率低）

| # | XML 元素 | 目标表 | 说明 |
|---|---------|--------|------|
| 10 | **`<PersonalNameSubjectList>`** | `cat_publication_personal_name_subject` | 传记/历史类文献主题人物 |
| 11 | `<CoiStatement>` | `cat_publication.conflict_of_interest` | 利益冲突声明 |
| 12 | `<Article PubModel>` | `cat_publication.media_type` | Print/Electronic/Both |
| 13 | `<ObjectList>` | `cat_publication_supplemental_object` | 补充材料 |
| 14 | `<GeneSymbolList>` | 需新建表 | 基因符号列表 |

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

- 表结构设计得非常前瞻——34 张表覆盖了 PubMed DTD 的几乎所有数据元素。**XML 解析器目前已解析约 65% 的可用数据**（KeywordList 完成后提升约 5%）
- 架构上的好消息是：六边形架构的分层设计让"补解析"非常简单——只需修改 `CanonicalPublicationParsingStrategy` 添加新的 `parseXxx()` 方法，Processor 和 Writer 的处理逻辑大多已就绪（KeywordList 已验证此模式）
- 当前最有价值的缺失是 **GrantList**（科研评估刚需）和 **ReferenceList**（引用网络分析），建议优先补上

## 五、解析器补全路线图建议

如果要逐步补全，推荐按以下顺序：

```
Phase 1: 高价值（核心数据）
├── ✅ KeywordList → cat_keyword + cat_publication_keyword (已完成)
├── ⚠️ AuthorList/Author → cat_author + cat_publication_author（Parser ✅，需实现 Processor/Writer/Repository）
├── GrantList → cat_publication_funding (含 FunderLookupPort 匹配，只差 Parser)
└── CoiStatement → cat_publication.conflict_of_interest
    PubModel 属性 → cat_publication.media_type

Phase 2: 中价值 + 中复杂度
├── History/PubMedPubDate → cat_publication_history
├── CommentsCorrectionsList → cat_publication_related_item
├── DataBankList → cat_publication_external_reference
└── InvestigatorList → cat_investigator + cat_publication_investigator

Phase 3: 高价值 + 高复杂度
└── ReferenceList → cat_publication_reference（~2 亿条，需 Canonical 模型扩展）

Phase 4: 补充数据
├── PersonalNameSubjectList → cat_publication_personal_name_subject
├── ChemicalList → 新建 cat_publication_chemical 或用 ext_data
├── ObjectList → cat_publication_supplemental_object
└── GeneSymbolList → 新建表或用 ext_data
```

各 Phase 改动范围差异：

- **大部分元素**（GrantList、CoiStatement 等）：只需补 Parser → Processor 已有 `buildXxxData()` → Writer/Repository 已就绪
- **Author（特殊）**：Parser ✅ 已完成 → 需新建 `buildAuthorData()` + `writeAuthorAssociations()` + 独立聚合根去重逻辑
- **ReferenceList（特殊）**：需从 Canonical 模型层开始设计，数据量巨大需性能优化
