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
   - **流式下载**：从 NLM 官方服务器流式下载 XML，HTTP 响应体直接传递给 Parser（无磁盘落盘）
   - **配置驱动**：URL 从配置文件读取，版本号从文件名自动推断（如 `desc2025.xml` → `2025`）
   - 支持断点续传（Job 级别幂等执行，失败时需重新下载）
   - **一次性初始化语义**：表中有数据时拒绝导入，需手动清空后重新执行
   - 批量 SQL 插入优化（单条 SQL 多行值）
   - XXL-Job 调度入口（`meshDescriptorImportJob`）

4. **MeSH 限定词导入**
   - 使用 StAX 流式解析 XML（约 80 条限定词）
   - **配置驱动**：URL 从配置文件读取，版本号从文件名自动推断（如 `qual2025.xml` → `2025`）
   - XXL-Job 调度入口（`meshQualifierImportJob`）
   - **一次性初始化语义**：表中有数据时拒绝导入，需手动清空后重新执行

5. **MeSH SCR（补充概念记录）导入**（Spring Batch 实现）
   - 使用 Spring Batch 实现大规模 SCR 数据导入（约 350,000 条）
   - **流式下载**：从 NLM 官方服务器流式下载 XML，HTTP 响应体直接传递给 Parser（无磁盘落盘）
   - **配置驱动**：URL 从配置文件读取，版本号从文件名自动推断（如 `supp2025.xml` → `2025`）
   - 批量 SQL 插入优化（单条 SQL 多行值）
   - XXL-Job 调度入口（`meshScrImportJob`）
   - **一次性初始化语义**：表中有数据时拒绝导入，需手动清空后重新执行

6. **OpenAlex Venue 数据批量导入**（Spring Batch 实现）
   - 从 OpenAlex S3 Manifest 动态获取分区文件列表
   - **流式下载**：按需从 OpenAlex S3 流式下载分区文件（无磁盘落盘）
   - 支持多文件顺序读取（gzip 压缩 JSONL 格式）
   - **一次性初始化语义**：纯 INSERT 写入，表中有数据时拒绝导入
   - 支持断点续传（Job 级别幂等执行，失败时需重新下载）
   - XXL-Job 调度入口（`venueInitializeJob`）

7. **ROR 机构数据批量导入**（Spring Batch 实现）
   - **流式下载**：从 ROR Data Dump JSON 流式下载并解析（无磁盘落盘）
   - **一次性初始化语义**：表中有数据时拒绝导入，需手动清空后重新执行
   - 支持断点续传（Job 级别幂等执行，失败时需重新下载）
   - Job 入口：`rorOrganizationImportJob`（参数：`downloadUrl`、`rorVersion`）

8. **多数据源期刊数据融合**
   - 支持多数据源：OpenAlex、PubMed Catalog、DOAJ、Crossref、WOS/JCR
   - 通用评级表设计：支持 JCR、中科院分区、Scopus CiteScore 等多评价体系
   - 数据源溯源：记录各数据源原始 JSON 和提取字段
   - 多标识符匹配：ISSN-L、NLM ID、OpenAlex ID、ISSN 组合匹配

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
- `patra-spring-cloud-starter-feign`：Feign 远程调用支持
- `patra-registry-api`：Registry 服务客户端（DictionaryClient 字典解析）

### 外部依赖
- Spring Boot 3.5.7
- Spring Cloud 2025.0.0
- Spring Data JPA + Hibernate 6.6
- Nacos（配置中心和服务发现）

## ⚙️ 配置说明

### MeSH 数据源配置

```yaml
patra:
  catalog:
    mesh:
      descriptor-url: https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml
      qualifier-url: https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml
      scr-url: https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/supp2025.xml
```

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `descriptor-url` | MeSH 主题词 XML 文件 URL | `desc2025.xml` |
| `qualifier-url` | MeSH 限定词 XML 文件 URL | `qual2025.xml` |
| `scr-url` | MeSH 补充概念记录 XML 文件 URL | `supp2025.xml` |

**版本号推断规则**：从文件名自动提取 4 位年份，支持格式：
- `desc{year}.xml`（如 `desc2025.xml` → 版本 `2025`）
- `qual{year}.xml`（如 `qual2025.xml` → 版本 `2025`）
- `supp{year}.xml`（如 `supp2025.xml` → 版本 `2025`）

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

## 🎯 核心类说明

### Domain 层
- **聚合根**
  - `MeshDescriptorAggregate`：MeSH 主题词聚合根
  - `MeshQualifierAggregate`：MeSH 限定词聚合根
  - `MeshScrAggregate`：MeSH 补充概念记录聚合根
  - `VenueAggregate`：载体（期刊/仓储/会议）聚合根
  - `VenueRatingAggregate`：载体评级聚合根（独立聚合，支持 JCR/CAS/Scopus）
  - `PublicationAggregate`：文献聚合根（规划中）

- **实体**
  - `MeshConcept`：MeSH 概念实体
  - `MeshEntryTerm`：MeSH 入口术语实体
  - `MeshTreeNumber`：MeSH 树形编号实体
  - `Author`：作者实体
  - `Organization`：机构实体（基于 ROR Schema v2.0）

- **Record 值对象**（不可变，通过 `VenueRepository` 统一管理）
  - `VenueSourceData`：载体数据源溯源（存储各数据源原始/提取数据）
  - `VenueIdentifier`：载体标识符（ISSN/OpenAlex ID/NLM ID 等，保留在聚合内）
  - `VenuePublicationStats`：载体年度发文统计（发表量/被引量/OA 比例）
  - `VenueMesh`：载体 MeSH 主题词（MeSH 主题分类，来源 Serfile）
  - `VenueRelation`：载体关联关系（期刊演变关系：前刊/后刊/合并/分拆）
  - `VenueIndexingHistory`：载体索引历史（MEDLINE/PMC 索引收录变迁）

- **嵌入式值对象**（JSON 存储，随聚合根一起保存）
  - `PublicationProfile`：出版概况（缩写标题、备选标题、主页 URL、宿主机构、国家代码）
  - `CitationMetrics`：引用指标（作品数、被引数、H 指数、i10 指数、两年平均被引）
  - `OpenAccessInfo`：开放获取信息（OA 状态 + APC 定价合并）
  - `Society`：关联学会（学会名称、URL）

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
  - `VenueType`：载体类型（JOURNAL、REPOSITORY、CONFERENCE、EBOOK_PLATFORM、BOOK_SERIES）
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

### Infrastructure 层
- `MeshDescriptorRepositoryAdapter`：主题词仓储适配器
- `MeshQualifierRepositoryAdapter`：限定词仓储适配器
- `MeshScrRepositoryAdapter`：补充概念记录仓储适配器
- `VenueRepositoryAdapter`：载体聚合根仓储适配器（含 identifiers 和补充数据）
- `OrganizationRepositoryAdapter`：机构聚合根仓储适配器（支持批量插入/更新，子表变更追踪）
- `MeshDescriptorJpaMapper`：主题词 Entity ↔ Aggregate 映射器（MapStruct）
- `MeshQualifierJpaMapper`：限定词 Entity ↔ Aggregate 映射器（MapStruct）
- `MeshScrJpaMapper`：SCR Entity ↔ Aggregate 映射器（MapStruct）
- `VenueJpaMapper`：载体聚合根 Entity ↔ Aggregate 映射器（含快速访问字段提取）
- `OrganizationJpaMapper`：机构聚合根 Entity ↔ Aggregate 映射器（支持 5 张子表双向转换）
- `StreamingDownloadAdapter`：流式下载适配器（HTTP 响应体直接返回 InputStream，无磁盘落盘）
- `VenueSourceFileAdapter`：OpenAlex Venue 源文件适配器（封装 manifest 解析）
- `DictionaryResolverAdapter`：字典解析适配器（调用 patra-registry 字典服务，支持国家、语言等多种字典类型的批量解析）

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
  - `cat_publication_mesh`：文献-MeSH 关联表

- **Venue（载体）相关表**
  - `cat_venue`：载体主表（含 4 个 JSON 嵌入式值对象：publication_profile、citation_metrics、open_access、affiliated_societies）
  - `cat_venue_identifier`：载体标识符表（ISSN/OpenAlex ID/NLM ID/DOAJ/JCR 等）
  - `cat_venue_publication_stats`：载体年度发文统计表（发表量/被引量/OA 发文量）
  - `cat_venue_source_data`：载体数据源表（各数据源原始 JSON 和提取字段）
  - `cat_venue_rating`：载体评级表（JCR/中科院分区/Scopus 等多评价体系年度评级）
  - `cat_venue_mesh`：载体 MeSH 主题表（MeSH 主题词分类，与 cat_publication_mesh 命名风格一致）
  - `cat_venue_relation`：载体关联表（期刊演变关系：前刊/后刊/合并/分拆）
  - `cat_venue_indexing_history`：载体索引历史表（MEDLINE/PMC 索引收录变迁）

- **Organization（机构）相关表**（基于 ROR Schema v2.0）
  - `cat_organization`：机构主表（含 JSON 字段：types、domains、links、admin_info）
  - `cat_organization_name`：机构名称表（多语言名称，含类型：ror_display/label/alias/acronym）
  - `cat_organization_external_id`：机构外部标识符表（GRID/ISNI/Wikidata/FundRef/Ringgold）
  - `cat_organization_relation`：机构关系表（层级关系：parent/child，关联关系：related，时序关系：successor/predecessor）
  - `cat_organization_location`：机构地理位置表（基于 GeoNames，含洲/国家/省/市/经纬度）

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
