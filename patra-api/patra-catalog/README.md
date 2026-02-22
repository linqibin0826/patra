# patra-catalog 目录管理服务

## 📋 概述

patra-catalog 是 Patra 医学文献数据平台的目录管理微服务，负责管理医学文献的分类体系、主题词表、作者、机构、期刊等核心目录数据。本服务采用六边形架构设计，遵循 DDD 战术设计原则。

主要职责：
- 管理 MeSH（Medical Subject Headings）医学主题词表数据模型
- 维护期刊（Venue）目录和影响因子
- 管理作者（Author）和机构（Organization）信息
- 提供文献分类和标引服务

## 🏗️ 架构位置

在 Patra 微服务架构中的位置：
- **上游服务**：patra-ingest（采集服务提供原始数据）
- **下游服务**：patra-search（检索服务使用目录数据）、patra-analysis（分析服务使用分类体系）
- **依赖服务**：patra-registry（获取 Provenance 配置和数据字典）

## 🔧 主要功能

### 已实现功能
1. **文献目录管理**
   - 文献（Publication）基本信息管理
   - 作者（Author）信息维护
   - 机构（Organization）数据管理
   - 期刊（Venue）目录维护

2. **MeSH 数据模型**
   - 主题词（Descriptor）领域模型
   - 限定词（Qualifier）领域模型
   - 补充概念记录（SCR）领域模型
   - 树形编号（TreeNumber）层次结构
   - 入口术语（EntryTerm）同义词
   - 概念（Concept）关系

3. **MeSH 数据批量导入**（Spring Batch 实现）
   - 使用 Spring Batch 实现大规模数据导入（约 35,000 条主题词）
   - **临时文件下载**：从 NLM 官方服务器下载 XML 到临时目录，从本地文件解析（解耦下载与处理）
   - **配置驱动**：URL 从配置文件读取，版本号从文件名自动推断（如 `desc2026.xml` → `2026`）
   - 支持断点续传（Job 级别幂等执行，失败时需重新下载）
   - **一次性初始化语义**：表中有数据时拒绝导入，需手动清空后重新执行
   - 批量 SQL 插入优化（单条 SQL 多行值）
   - XXL-Job 调度入口（`meshDescriptorImportJob`）

4. **MeSH 限定词导入**
   - 使用 StAX 流式解析 XML（约 80 条限定词）
   - **配置驱动**：URL 从配置文件读取，版本号从文件名自动推断（如 `qual2026.xml` → `2026`）
   - XXL-Job 调度入口（`meshQualifierImportJob`）
   - **一次性初始化语义**：表中有数据时拒绝导入，需手动清空后重新执行

5. **MeSH SCR（补充概念记录）导入**（Spring Batch 实现）
   - 使用 Spring Batch 实现大规模 SCR 数据导入（约 350,000 条）
   - **临时文件下载**：从 NLM 官方服务器下载 XML 到临时目录，从本地文件解析（解耦下载与处理）
   - **配置驱动**：URL 从配置文件读取，版本号从文件名自动推断（如 `supp2026.xml` → `2026`）
   - 批量 SQL 插入优化（单条 SQL 多行值）
   - XXL-Job 调度入口（`meshScrImportJob`）
   - **一次性初始化语义**：表中有数据时拒绝导入，需手动清空后重新执行

6. **ROR 机构数据批量导入**（Spring Batch 实现）
   - **临时文件下载**：从 ROR Data Dump ZIP 下载到临时目录，从本地文件解压并解析
   - **一次性初始化语义**：表中有数据时拒绝导入，需手动清空后重新执行
   - 支持断点续传（Job 级别幂等执行，失败时需重新下载）
   - Job 入口：`rorOrganizationImportJob`（参数：`downloadUrl`、`rorVersion`）

7. **PubMed Venue 数据导入**
   - 数据来源：NLM LSIOU（List of Serials Indexed for Online Users），约 15,000 条 MEDLINE 索引期刊
   - 增量覆盖模式：ISSN-L → NLM ID → ISSN 三级降级匹配策略
   - 批内去重：NLM ID + ISSN-L 双维度防重复创建
   - 已删除 Serial 过滤：跳过 `deletedTimestamp` 非空的记录
   - **Wikidata 富化**：通过 SPARQL 单次查询 Wikidata，按 ISSN-L 批量获取期刊中文标题、封面图片及官方网站（`WikidataEnrichmentQueryPort` → `WikidataEnrichmentQueryAdapter`）
   - **OpenAlex 富化**：通过 REST API 查询 OpenAlex Sources，按 ISSN-L 批量获取期刊引用指标（H 指数、i10 指数、两年平均被引）、年度发文统计及开放获取信息（OA 状态、DOAJ 收录、APC 定价）（`OpenAlexEnrichmentQueryPort` → `OpenAlexEnrichmentQueryAdapter`）
   - 通用评级表设计：支持 JCR、中科院分区、Scopus CiteScore 等多评价体系
   - 数据源溯源：记录各数据源原始 JSON 和提取字段
   - 多标识符匹配：ISSN-L、NLM ID、ISSN 组合匹配

8. **PubMed Baseline 文献批量导入**（Spring Batch 实现）
   - 使用 Spring Batch 实现大规模 PubMed 文献导入（2025 年约 3,700 万条）
   - **临时文件下载**：从 NLM FTP 下载 gzip 压缩 XML 到临时目录，从本地文件解压并解析
   - **单文件模式**：每次 Job 执行只处理一个文件（1,334 个文件可并行调度）
   - **VenueLookupPort 双索引缓存**：使用 `BatchVenueLookupAdapter`（Step 级别缓存），NLM ID + ISSN 组合匹配
   - **LanguageLookupPort 语言解析**：ISO 639-3 → BCP 47 语言代码转换，支持批量解析
   - **FunderLookupPort 资助机构匹配**：通过 FundRef ID / ROR ID 匹配资助机构，支持批量缓存
   - **VenueInstance 自动创建**：按卷期自动创建或复用期刊实例
   - **SupplMeshName 处理**：提取文献补充 MeSH 概念标引（化学物质、疾病、方案等 SCR 关联）
   - **OtherAbstract 处理**：提取文献的其他语言摘要（翻译摘要），来源包括 Publisher（官方翻译）、AIMSHP/KIEML（专业机构翻译）、plain-language-summary（通俗语言摘要）
   - **PublicationDate 处理**：提取文献生命周期日期（投稿/接收/发表/修订/电子版发表/进入数据库等），支持不完整日期表达（年/月/日三种精度）
   - **Identifier 处理**：提取文献标识符（PMID、DOI、PMC、PII 等），建立反向索引支持按标识符查询
   - **Abstract 处理**：提取文献摘要（支持结构化摘要和纯文本摘要），独立存储便于全文检索
   - **Investigator 处理**：提取非作者研究人员（如临床试验 PI、协调员），支持 ORCID 和 dedupKey 去重匹配
   - **PersonalNameSubject 处理**：提取传记类/历史类/纪念类文献的主题人物信息
   - **Keyword 处理**：解析 `<KeywordList>` 关键词列表（支持多 Owner 分组：NOTNLM=作者关键词、NLM=索引词），含 MajorTopic 标记和规范化去重
   - **标题容错规则**：优先使用 `ArticleTitle`，为空时回退 `VernacularTitle`，两者都为空则跳过该记录
   - **Writer 双阶段去重**：先做 chunk 内 `PMID/DOI` 去重（first-win），再批量查询数据库过滤已存在记录
   - **幂等兜底**：数据库唯一索引（`uk_pmid`/`uk_doi`）作为最终一致性保障，回滚重试不依赖内存状态
   - 支持断点续传（Job 级别幂等执行）
   - XXL-Job 调度入口（`pubmedBaselineImportJob`，参数：`fileIndex=1~1334`）

### 计划功能
- MeSH 年度版本更新（需先手动清空旧数据）
- 期刊影响因子批量更新
- 作者消歧和合并
- 机构标准化和映射

## 📦 模块依赖

### 内部依赖
- `patra-common-core`：通用工具和基础类
- `patra-common-model`：共享领域模型
- `patra-spring-boot-starter-jpa`：数据访问层支持
- `patra-spring-boot-starter-batch`：批处理支持
- `patra-spring-boot-starter-rest-client`：HTTP 客户端支持（文件下载）
- `patra-spring-boot-starter-core`：核心基础设施
- `patra-spring-boot-starter-http-interface`：HTTP Interface 远程调用支持
- `patra-registry-api`：Registry 服务契约（DictionaryEndpoint 字典解析）

### 外部依赖
- Spring Boot 4.0.1
- Spring Cloud 2025.1.0
- Spring Data JPA + Hibernate 7.1
- Consul（服务发现）

## ⚙️ 配置说明

### MeSH 数据源配置

```yaml
patra:
  catalog:
    mesh:
      descriptor-url: https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2026.xml
      qualifier-url: https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2026.xml
      scr-url: https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/supp2026.xml
```

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `descriptor-url` | MeSH 主题词 XML 文件 URL | `desc2026.xml` |
| `qualifier-url` | MeSH 限定词 XML 文件 URL | `qual2026.xml` |
| `scr-url` | MeSH 补充概念记录 XML 文件 URL | `supp2026.xml` |

**版本号推断规则**：从文件名自动提取 4 位年份，支持格式：
- `desc{year}.xml`（如 `desc2026.xml` → 版本 `2026`）
- `qual{year}.xml`（如 `qual2026.xml` → 版本 `2026`）
- `supp{year}.xml`（如 `supp2026.xml` → 版本 `2026`）

### LSIOU 数据源配置

```yaml
patra:
  catalog:
    lsiou:
      url: ftp://ftp.nlm.nih.gov/online/journals/lsi2024.xml
```

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `url` | NLM LSIOU XML 文件 URL（支持 FTP/HTTP/HTTPS） | `lsi2024.xml` |

**版本号推断规则**：从文件名自动提取 4 位年份，支持格式：
- `lsi{year}.xml`（如 `lsi2024.xml` → 版本 `2024`）

**回退规则**：
- 若主目录文件不存在，将自动回退到 `ftp://ftp.nlm.nih.gov/online/journals/archive`

**LSIOU vs SerfileBase**：
- LSIOU（List of Serials Indexed for Online Users）：约 15,000 条 MEDLINE 索引期刊
- SerfileBase：约 35,000 条 NLM Catalog 完整记录（包含未被 MEDLINE 索引的期刊）

### ROR 数据源配置

```yaml
patra:
  catalog:
    ror:
      download-url: https://zenodo.org/records/17953395/files/v2.0-2025-12-16-ror-data.zip?download=1
```

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `download-url` | ROR Data Dump ZIP 文件下载 URL | `v2.0-2025-12-16-ror-data.zip` |

**版本号推断规则**：从文件名自动提取版本号，支持格式：
- `v{version}-{date}-ror-data.zip`（如 `v2.0-2025-12-16-ror-data.zip` → 版本 `v2.0`）

**数据源说明**：
- ROR 数据发布在 Zenodo，每个版本有独立的下载链接
- 最新版本下载地址：<https://ror.readme.io/docs/data-dump>
- ZIP 文件约 60MB（压缩），包含约 120,000 条机构记录
- 从 v2.0 开始仅提供 schema v2 格式

## 🎯 核心类说明

### Domain 层
- **聚合根**
  - `MeshDescriptorAggregate`：MeSH 主题词聚合根
  - `MeshQualifierAggregate`：MeSH 限定词聚合根
  - `MeshScrAggregate`：MeSH 补充概念记录聚合根
  - `VenueAggregate`：载体（期刊/仓储/会议）聚合根
  - `VenueRatingAggregate`：载体评级聚合根（独立聚合，支持 JCR/CAS/Scopus）
  - `AuthorAggregate`：作者聚合根（含名字变体、ORCID 子实体）
  - `OrganizationAggregate`：机构聚合根（基于 ROR Schema v2.0）
  - `PublicationAggregate`：文献聚合根

- **实体**
  - `MeshConcept`：MeSH 概念实体
  - `MeshEntryTerm`：MeSH 入口术语实体
  - `MeshTreeNumber`：MeSH 树形编号实体

- **Record 值对象**（不可变，通过 `VenueRepository` 统一管理）
  - `VenueSourceData`：载体数据源溯源（存储各数据源原始/提取数据）
  - `VenueIdentifier`：载体标识符（ISSN/OpenAlex ID/NLM ID 等，保留在聚合内）
  - `VenuePublicationStats`：载体年度发文统计（发表量/被引量/OA 比例）
  - `VenueMesh`：载体 MeSH 主题词（MeSH 主题分类，来源 Serfile）
  - `VenueRelation`：载体关联关系（期刊演变关系：前刊/后刊/合并/分拆）
  - `VenueIndexingHistory`：载体索引历史（MEDLINE/PMC 索引收录变迁）

- **读模型**（CQRS 读端查询投影）
  - `VenueSummaryReadModel`：期刊列表摘要读模型
  - `VenueDetailReadModel`：期刊详情读模型（`@Builder`，17 字段）
  - `VenueFilter`：期刊筛选条件（`@Builder`，支持 keyword/provenanceCode/countryCode/issnL/nlmId 独立筛选）

- **领域异常**
  - `VenueNotFoundException`：期刊不存在异常（携带 `StandardErrorTrait.NOT_FOUND`，自动映射为 HTTP 404）

- **嵌入式值对象**（JSON 存储，随聚合根一起保存）
  - `PublicationProfile`：出版概况（缩写标题、备选标题、主页 URL、宿主机构、国家代码）
  - `CitationMetrics`：引用指标（作品数、被引数、H 指数、i10 指数、两年平均被引）
  - `OpenAccessInfo`：开放获取信息（OA 状态 + APC 定价合并）
  - `Society`：关联学会（学会名称、URL）

- **查询传输值对象**（非持久化，用于跨端口传递查询结果）
  - `VenueWikidataEnrichment`：Wikidata 富化数据（中文标题、封面图片 URL、官方网站 URL）
  - `VenueOpenAlexEnrichment`：OpenAlex 富化数据（OpenAlex Source ID、引用指标快照、年度发文统计、开放获取信息）

- **聚合根 ID 值对象**（编译时类型安全，防止 ID 混淆）
  - `VenueId`：载体聚合根 ID
  - `VenueInstanceId`：载体实例聚合根 ID
  - `MeshDescriptorId`：MeSH 主题词聚合根 ID
  - `MeshQualifierId`：MeSH 限定词聚合根 ID
  - `MeshScrId`：MeSH 补充概念记录聚合根 ID
  - `OrganizationId`：机构聚合根 ID
  - `AuthorId`：作者聚合根 ID
  - `PublicationId`：出版物聚合根 ID
  - `VenueRatingId`：载体评级聚合根 ID

- **值对象（author 子包）**：作者领域模型（适配 PubMed Computed Authors）
  - `AuthorNameVariant`：作者名字变体（姓/名/缩写/原始字符串）
  - `Orcid`：ORCID 标识符值对象（格式：0000-0001-2345-6789）
  - `AffiliationIdentifiers`：机构标识符集合（ROR ID / Ringgold ID / GRID ID，支持消歧优先级选择）

- **值对象**
  - `MeshUI`：MeSH 唯一标识符（支持 Descriptor/Qualifier/SCR/Concept/Term）
  - `EntryCombination`：MeSH 组合条目（ECIN/ECOUT 映射）
  - `AllowableQualifier`：允许的限定词
  - `PharmacologicalAction`：药理作用
  - `SeeRelatedDescriptor`：相关主题词引用
  - `ConceptRelation`：概念关系
  - `HeadingMappedTo`：SCR 到 Descriptor 的映射关系（含 majorTopic 标记，NLM 用 `*` 前缀表示主要主题词）
  - `ScrSource`：SCR 数据来源
  - `IndexingInfo`：SCR 索引信息（mesh 包）
  - `HostOrganization`：主办机构信息
  - `ProvenanceInfo`：数据来源信息
  - `PublicationHistory`：出版历史（创刊/停刊年份）
  - `IndexingInfo`：MEDLINE 索引收录信息（venue 包）
  - `LatestRating`：最新评级快照（冗余，高频查询优化）
  - `OaStatus`：开放获取状态
  - `VenueLanguages`：期刊语言信息（主要语言 + 摘要语言列表，来源 Serfile）

- **值对象（organization 子包）**：基于 ROR Schema v2.0 的机构领域模型
  - `RorId`：ROR 标识符值对象（支持完整 URL 或纯 ID 解析，9 位字符格式）
  - `OrganizationId`：机构聚合根内部 ID（数据库主键包装）
  - `OrganizationName`：机构名称（含类型集合：ror_display/label/alias/acronym，支持多语言）
  - `ExternalId`：外部标识符（GRID/ISNI/Wikidata/FundRef/Ringgold，含首选值和全部值）
  - `GeoLocation`：地理位置（基于 GeoNames，含洲/国家/省/市/经纬度）
  - `OrganizationRelation`：机构关系（层级：parent/child，关联：related，时序：successor/predecessor）
  - `OrganizationLink`：机构链接（官网/维基百科，支持语言标记）
  - `AdminInfo`：管理信息（ROR 创建/更新日期和版本号）

- **值对象（publication 子包）**：文献批量导入关联数据的值对象（`PublicationRepository.insertAllWithAssociations()` 使用）
  - `PublicationCompleteData`：完整文献数据封装（聚合根 + 所有关联数据），Repository 批量写入接口的参数类型
  - `PublicationMeshHeading`：文献 MeSH 标引值对象（含 Descriptor UI、主题标记、顺序）
  - `MeshQualifier`：MeSH 限定词值对象（嵌套在 MeshHeading 中）
  - `PublicationKeyword`：文献关键词值对象（来源、主/副标记、顺序）
  - `PublicationFunding`：文献资助信息值对象（资助机构、项目编号、数据来源）
  - `PublicationTypeInfo`：文献出版类型值对象（类型 ID、类型值、词汇来源）
  - `PublicationSupplMesh`：文献补充 MeSH 概念值对象（SCR UI、顺序）
  - `PublicationInvestigator`：文献研究者值对象（姓名、ORCID、机构、dedupKey）
  - `PublicationPersonalNameSubject`：文献人物主题值对象（传记类/历史类文献的主题人物）

- **值对象（venue/pubmed 子包）**：NLM LSIOU 解析结果的完整领域模型（解析器直接产出）
  - `PubmedSerialData`：PubMed 期刊解析数据（完整版，44 字段，解析器直接产出 Domain 模型）
  - `PubmedLanguage`：PubMed 期刊语言信息（主要/次要语言）
  - `PubmedMeshHeading`：PubMed 期刊 MeSH 主题词（含限定符、UI、类型）
  - `PubmedTitleRelation`：PubMed 期刊关联关系（前刊/后刊，含 ISSN 和 RecordIds）
  - `PubmedIndexingHistory`：PubMed 期刊索引历史（含日期、覆盖范围、索引状态）
  - `PubmedCurrentIndexing`：PubMed 期刊当前索引子集信息（含子集内容）
  - `PubmedBroadHeading`：PubMed 广泛期刊分类
  - `PubmedCrossReference`：PubMed 交叉引用（XrType: A/X/S）
  - `PubmedGeneralNote`：PubMed 通用备注（含 NoteType 属性）
  - `PubmedRecordId`：PubMed 记录 ID（含 Source 属性：NLM/LC/OCLC）

- **枚举**
  - `MeshDataType`：MeSH 数据类型（QUALIFIER、DESCRIPTOR、TREE_NUMBER、ENTRY_TERM、CONCEPT）
  - `DescriptorClass`：主题词分类
  - `ScrClass`：SCR 分类（1-6 六种类型：化学物质、化疗方案、疾病、生物体、人群组、解剖结构）
  - `MeshRecordType`：MeSH 记录类型（DESCRIPTOR、SCR，用于共享表区分记录类型）
  - `LexicalTag`：词汇标记
  - `VenueType`：载体类型（JOURNAL、REPOSITORY、CONFERENCE、EBOOK_PLATFORM、BOOK_SERIES、METADATA、IGSN_CATALOG、RAID_REGISTRY、OTHER）
  - `VenueIdentifierType`：标识符类型（ISSN、OPENALEX、NLM、MAG、FATCAT、WIKIDATA、DOAJ、CROSSREF_MEMBER、JCR、CODEN）
  - `DataSourceCode`：数据源代码（OPENALEX、PUBMED、DOAJ、CROSSREF、JCR）
  - `RatingSystem`：评价体系（JCR、CAS、SCOPUS）
  - `VenueRelationType`：载体关联类型（PRECEDING、PRECEDING_IN_PART、SUCCEEDING、SUCCEEDING_IN_PART、ABSORBED、ABSORBED_BY、ABSORBED_IN_PART、ABSORBED_IN_PART_BY、MERGED_TO、MERGER_OF、SPLIT_FROM、SPLIT_TO、SUPERSEDES、SUPERSEDED_BY、SUPERSEDES_IN_PART、SUPERSEDED_IN_PART_BY、ANALYTIC、RELATED、REVERSION、SERIES、SERIES_AUTHORITY、TRANSLATED、OTHER、UNDETERMINED）
  - `CitationSubset`：引用子集（IM、AIM、N、D、H、K、T、E、S、X、B、C、F、Q、J、OM、P、QIS、R）
  - `IndexingTreatment`：索引处理方式（FULL、SELECTIVE、UNKNOWN、REFERENCED_IN、REFERENCED_IN_NO_DETAILS）

- **枚举（organization 子包）**：机构领域枚举
  - `OrganizationType`：机构类型（EDUCATION、HEALTHCARE、COMPANY、GOVERNMENT、NONPROFIT、FACILITY、ARCHIVE、FUNDER、OTHER）
  - `OrganizationStatus`：机构状态（ACTIVE、INACTIVE、WITHDRAWN）
  - `ExternalIdType`：外部标识符类型（GRID、ISNI、WIKIDATA、FUNDREF、RINGGOLD）
  - `LinkType`：链接类型（WEBSITE、WIKIPEDIA）
  - `OrganizationNameType`：名称类型（ROR_DISPLAY、LABEL、ALIAS、ACRONYM）
  - `OrganizationRelationType`：关系类型（PARENT、CHILD、RELATED、SUCCESSOR、PREDECESSOR）

- **枚举（消歧相关）**：机构消歧领域枚举
  - `DisambiguationStatus`：消歧状态（PENDING、MATCHED、UNMATCHED、AMBIGUOUS）
  - `DisambiguationMethod`：消歧方法（ROR_ID、RINGGOLD、GRID、NAME_MATCH、MANUAL）

### Infrastructure 层
- `MeshDescriptorRepositoryAdapter`：主题词仓储适配器
- `MeshQualifierRepositoryAdapter`：限定词仓储适配器
- `MeshScrRepositoryAdapter`：补充概念记录仓储适配器
- `VenueRepositoryAdapter`：载体聚合根仓储适配器（含 identifiers 和补充数据）
- `VenueRatingRepositoryAdapter`：载体评级聚合根仓储适配器（独立聚合）
- `AuthorRepositoryAdapter`：作者聚合根仓储适配器（含名字变体、ORCID 子实体级联保存）
- `OrganizationRepositoryAdapter`：机构聚合根仓储适配器（支持批量插入/更新，子表变更追踪）
- `PublicationRepositoryAdapter`：文献聚合根仓储适配器（支持批量插入/更新，含 17 种关联数据写入）
- `MeshDescriptorJpaMapper`：主题词 Entity ↔ Aggregate 映射器（MapStruct）
- `MeshQualifierJpaMapper`：限定词 Entity ↔ Aggregate 映射器（MapStruct）
- `MeshScrJpaMapper`：SCR Entity ↔ Aggregate 映射器（MapStruct）
- `VenueJpaMapper`：载体聚合根 Entity ↔ Aggregate 映射器（含快速访问字段提取）
- `OrganizationJpaMapper`：机构聚合根 Entity ↔ Aggregate 映射器（支持 5 张子表双向转换）
- `WikidataEnrichmentQueryAdapter`：Wikidata 富化查询适配器（实现 `WikidataEnrichmentQueryPort`，通过 SPARQL 单次批量查询中文标题、封面图片、官方网站）
- `WikidataSparqlClient`：Wikidata SPARQL 查询客户端（构建查询、解析响应、按 ISSN-L 批量匹配）
- `OpenAlexEnrichmentQueryAdapter`：OpenAlex 富化查询适配器（实现 `OpenAlexEnrichmentQueryPort`，通过 REST API 批量查询引用指标和年度统计）
- `OpenAlexSourcesClient`：OpenAlex Sources API 查询客户端（构建 ISSN 过滤查询、子批次拆分、JSON 解析）
- `FileDownloadAdapter`：文件下载适配器（下载到临时目录，供 Batch ItemReader 使用）
- `DictionaryResolverAdapter`：字典解析适配器（调用 patra-registry 字典服务，支持国家、语言等多种字典类型的批量解析）

- **ReadPort 实现**（CQRS 读端适配器）
  - `VenueReadAdapter`：期刊读适配器（实现 `VenueReadPort`，支持多条件独立筛选分页查询和详情查询）
  - `VenueReadModelMapper`：读模型映射器（MapStruct，Entity → ReadModel 转换）

- **LookupPort 实现**（装饰器模式，支持批处理缓存优化）
  - `DefaultVenueLookupAdapter` / `CachingVenueLookupDecorator` / `BatchVenueLookupAdapter`：Venue 查找（NLM ID + ISSN 双索引）
  - `DefaultLanguageLookupAdapter` / `CachingLanguageLookupDecorator` / `BatchLanguageLookupAdapter`：语言查找（ISO 639-3 → BCP 47）
  - `DefaultFunderLookupAdapter` / `CachingFunderLookupDecorator` / `BatchFunderLookupAdapter`：资助机构查找（FundRef ID + ROR ID）

## 📊 数据模型

### 核心数据表
- **MeSH 相关表**
  - `cat_mesh_descriptor`：主题词表（~35,000 条）
  - `cat_mesh_qualifier`：限定词表（~100 条）
  - `cat_mesh_tree_number`：树形编号表（~80,000 条）
  - `cat_mesh_entry_term`：入口术语表（~250,000 条）
  - `cat_mesh_concept`：概念表（~180,000 条，支持 Descriptor 和 SCR）
  - `cat_mesh_entry_combination`：组合条目表（~500 条）
  - `cat_mesh_scr`：SCR 主表（~350,000 条）
  - `cat_mesh_scr_heading_mapped_to`：SCR 到 Descriptor 映射关系表（含 major_topic 字段）
  - `cat_mesh_scr_source`：SCR 数据来源表
  - `cat_mesh_scr_indexing_info`：SCR 索引信息表
  - `cat_mesh_scr_pharmacological_action`：SCR 药理作用表
  - `cat_publication_mesh_heading`：文献-MeSH 主题标引表（一对多：一篇文献多个标引）
  - `cat_publication_mesh_qualifier`：文献-MeSH 限定词表（一对多：一个标引多个限定词）
  - `cat_publication_suppl_mesh`：文献-补充 MeSH 概念关联表（关联 SCR：化学物质、疾病、方案等）

- **Keyword（关键词）相关表**
  - `cat_keyword`：关键词主表（~800 万条，作者/编辑/索引机构提供的自由关键词，含规范化词形去重、频次统计）
  - `cat_publication_keyword`：文献-关键词关联表（~8500 万条，多对多关系，含主/副关键词标记、来源集合、顺序）

- **PublicationType（出版类型）相关表**
  - `cat_publication_type`：文献出版类型关联表（一对多，含类型 ID、类型值、词汇来源、顺序）

- **Publication（文献）补充数据表**
  - `cat_publication_abstract`：文献摘要表（独立存储，支持结构化摘要 JSON 和纯文本摘要，含版权信息）
  - `cat_publication_alternative_abstract`：文献翻译摘要表（支持同语言多来源类型摘要，含 language_code、source_type、translation_type、官方/专业翻译标记）
  - `cat_publication_date`：文献日期表（支持不完整日期，含年/月/日三种精度，日期类型：received/accepted/published/revised/epublish/entrezdate 等）
  - `cat_publication_identifier`：文献标识符表（支持 PMID/DOI/PMC/PII 等多种标识符类型，建立反向索引）

- **Investigator（研究者）相关表**
  - `cat_investigator`：研究者主表（非作者研究人员，如临床试验 PI，支持 ORCID 和 dedupKey 去重）
  - `cat_publication_investigator`：文献-研究者关联表（多对多关系，含角色、顺序、职责信息）
  - `cat_publication_personal_name_subject`：人物主题表（传记类/历史类文献的主题人物，按文献独立存储）

- **Venue（载体）相关表**
  - `cat_venue`：载体主表（含 title_zh 中文标题字段、image_url 封面图片字段，4 个 JSON 嵌入式值对象：publication_profile、citation_metrics、open_access、affiliated_societies）
  - `cat_venue_identifier`：载体标识符表（ISSN/OpenAlex ID/NLM ID/DOAJ/JCR 等）
  - `cat_venue_publication_stats`：载体年度发文统计表（发表量/被引量/OA 发文量）
  - `cat_venue_source_data`：载体数据源表（各数据源原始 JSON 和提取字段）
  - `cat_venue_rating`：载体评级表（JCR/中科院分区/Scopus 等多评价体系年度评级）
  - `cat_venue_mesh`：载体 MeSH 主题表（MeSH 主题词分类）
  - `cat_venue_relation`：载体关联表（期刊演变关系：前刊/后刊/合并/分拆）
  - `cat_venue_indexing_history`：载体索引历史表（MEDLINE/PMC 索引收录变迁）

- **Author（作者）相关表**（适配 PubMed Computed Authors）
  - `cat_author`：作者主表（~2100 万条，含 normalized_key、display_name、status）
  - `cat_author_name_variant`：作者名字变体表（~4200 万条，平均 2 个变体/作者）
  - `cat_author_orcid`：作者 ORCID 表（~500 万条，约 25% 作者有 ORCID）

- **Publication-Author 关联表**
  - `cat_publication_author`：文献-作者关联表（~1.4 亿条，含作者顺序、角色标记）
  - `cat_publication_author_affiliation`：作者-机构归属表（~2.1 亿条，支持多机构归属和延迟消歧）

- **Organization（机构）相关表**（基于 ROR Schema v2.0）
  - `cat_organization`：机构主表（含 JSON 字段：types、domains、links、admin_info）
  - `cat_organization_name`：机构名称表（多语言名称，含类型：ror_display/label/alias/acronym）
  - `cat_organization_external_id`：机构外部标识符表（GRID/ISNI/Wikidata/FundRef/Ringgold）
  - `cat_organization_relation`：机构关系表（层级关系：parent/child，关联关系：related，时序关系：successor/predecessor）
  - `cat_organization_location`：机构地理位置表（基于 GeoNames，含洲/国家/省/市/经纬度）

- **Funding（资助）相关表**
  - `cat_publication_funding`：文献-资助关联表（含资助机构 ID、项目编号、原始数据字段、数据来源追踪）

## 🧪 测试覆盖

| 层级 | 测试类型 | 覆盖率目标 |
|------|---------|-----------|
| Domain | 单元测试 | ≥80% |
| Application | 单元测试 | ≥70% |
| Infrastructure | 单元+集成测试 | ≥70% |
| Adapter | 单元+切片测试 | ≥70% |
| Boot | E2E 测试 | 核心流程 |

## 📝 变更日志

## 📖 相关文档

- [patra-spring-boot-starter-batch](../patra-spring-boot-starter-batch/README.md)
- [ADR-025: Author 聚合根设计](../../../Patra-docs/content/decisions/ADR-025-author-aggregate-design.md)
- [ADR-026: 作者多机构归属支持](../../../Patra-docs/content/decisions/ADR-026-author-multi-affiliation-support.md)
