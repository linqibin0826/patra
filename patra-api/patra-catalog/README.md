# patra-catalog 目录管理服务

## 📋 概述

patra-catalog 是 Patra 医学文献数据平台的目录管理微服务，负责管理医学文献的分类体系、主题词表、作者、机构、期刊等核心目录数据。本服务采用六边形架构设计，遵循 DDD 战术设计原则。

主要职责：
- 管理 MeSH（Medical Subject Headings）医学主题词表数据模型
- 维护期刊（Venue）目录和影响因子
- 管理作者（Author）和机构（Affiliation）信息
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
   - 机构（Affiliation）数据管理
   - 期刊（Venue）目录维护

2. **MeSH 数据模型**
   - 主题词（Descriptor）领域模型
   - 限定词（Qualifier）领域模型
   - 树形编号（TreeNumber）层次结构
   - 入口术语（EntryTerm）同义词
   - 概念（Concept）关系

3. **MeSH 数据批量导入**（Spring Batch 实现）
   - 使用 Spring Batch 实现大规模数据导入（约 35,000 条主题词）
   - 从 NLM 官方服务器直接下载 XML 数据文件
   - **配置驱动**：URL 从配置文件读取，版本号从文件名自动推断（如 `desc2025.xml` → `2025`）
   - 支持断点续传（Job 级别幂等执行）
   - **一次性初始化语义**：表中有数据时拒绝导入，需手动清空后重新执行
   - 批量 SQL 插入优化（单条 SQL 多行值）
   - 临时文件自动清理（Job 完成/停止后自动删除，失败时保留支持续传）
   - XXL-Job 调度入口（`meshDescriptorImportJob`）

4. **MeSH 限定词导入**
   - 使用 StAX 流式解析 XML（约 80 条限定词）
   - **配置驱动**：URL 从配置文件读取，版本号从文件名自动推断（如 `qual2025.xml` → `2025`）
   - XXL-Job 调度入口（`meshQualifierImportJob`）
   - **一次性初始化语义**：表中有数据时拒绝导入，需手动清空后重新执行

5. **OpenAlex Venue 数据批量导入**（Spring Batch 实现）
   - 从 OpenAlex S3 Manifest 动态获取分区文件列表
   - 从 OpenAlex 公开 S3 存储桶直接下载分区文件
   - 支持多文件顺序读取（gzip 压缩 JSONL 格式）
   - **一次性初始化语义**：纯 INSERT 写入，表中有数据时拒绝导入
   - 支持断点续传（Job 级别幂等执行）
   - XXL-Job 调度入口（`venueImportJob`）

6. **多数据源期刊数据融合**
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
- `patra-spring-boot-starter-mybatis`：数据访问层支持
- `patra-spring-boot-starter-batch`：批处理支持
- `patra-spring-boot-starter-rest-client`：HTTP 客户端支持（文件下载）
- `patra-spring-boot-starter-core`：核心基础设施

### 外部依赖
- Spring Boot 3.5.7
- Spring Cloud 2025.0.0
- MyBatis-Plus 3.5.12
- Nacos（配置中心和服务发现）

## ⚙️ 配置说明

### MeSH 数据源配置

```yaml
patra:
  catalog:
    mesh:
      descriptor-url: https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml
      qualifier-url: https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml
```

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `descriptor-url` | MeSH 主题词 XML 文件 URL | `desc2025.xml` |
| `qualifier-url` | MeSH 限定词 XML 文件 URL | `qual2025.xml` |

**版本号推断规则**：从文件名自动提取 4 位年份，支持格式：
- `desc{year}.xml`（如 `desc2025.xml` → 版本 `2025`）
- `qual{year}.xml`（如 `qual2025.xml` → 版本 `2025`）

## 🎯 核心类说明

### Domain 层
- **聚合根**
  - `MeshDescriptorAggregate`：MeSH 主题词聚合根
  - `MeshQualifierAggregate`：MeSH 限定词聚合根
  - `VenueAggregate`：载体（期刊/仓储/会议）聚合根
  - `PublicationAggregate`：文献聚合根（规划中）

- **实体**
  - `MeshConcept`：MeSH 概念实体
  - `MeshEntryTerm`：MeSH 入口术语实体
  - `MeshTreeNumber`：MeSH 树形编号实体
  - `VenueIdentifier`：载体标识符实体（ISSN/OpenAlex ID/NLM ID 等）
  - `VenuePublicationStats`：载体年度发文统计实体（发表量/被引量/OA 比例）
  - `VenueSourceData`：载体数据源实体（存储各数据源原始/提取数据）
  - `VenueRating`：载体评级实体（支持 JCR/中科院分区/Scopus 等多评价体系）
  - `VenueMesh`：载体 MeSH 主题词实体（MeSH 主题分类，来源 Serfile）
  - `VenueRelation`：载体关联实体（期刊演变关系：前刊/后刊/合并/分拆）
  - `VenueIndexingHistory`：载体索引历史实体（MEDLINE/PMC 索引收录变迁）
  - `Author`：作者实体
  - `Affiliation`：机构实体

- **值对象**
  - `MeshUI`：MeSH 唯一标识符
  - `DescriptorId`：主题词强类型 ID
  - `EntryCombination`：MeSH 组合条目（ECIN/ECOUT 映射）
  - `AllowableQualifier`：允许的限定词
  - `PharmacologicalAction`：药理作用
  - `SeeRelatedDescriptor`：相关主题词引用
  - `ConceptRelation`：概念关系
  - `HostOrganization`：主办机构信息
  - `VenueStats`：载体统计摘要
  - `ApcInfo`：APC（文章处理费）信息
  - `Society`：学会信息
  - `ProvenanceInfo`：数据来源信息
  - `PublicationHistory`：出版历史（创刊/停刊年份）
  - `IndexingInfo`：MEDLINE 索引收录信息
  - `LatestRating`：最新评级快照（冗余，高频查询优化）
  - `OaStatus`：开放获取状态
  - `VenueLanguages`：期刊语言信息（主要语言 + 摘要语言列表，来源 Serfile）

- **枚举**
  - `MeshDataType`：MeSH 数据类型（QUALIFIER、DESCRIPTOR、TREE_NUMBER、ENTRY_TERM、CONCEPT）
  - `DescriptorClass`：主题词分类
  - `LexicalTag`：词汇标记
  - `VenueType`：载体类型（JOURNAL、REPOSITORY、CONFERENCE、EBOOK_PLATFORM、BOOK_SERIES）
  - `VenueIdentifierType`：标识符类型（ISSN、OPENALEX、NLM、MAG、FATCAT、WIKIDATA、DOAJ、CROSSREF_MEMBER、JCR、CODEN）
  - `DataSourceCode`：数据源代码（OPENALEX、PUBMED、DOAJ、CROSSREF、JCR）
  - `RatingSystem`：评价体系（JCR、CAS、SCOPUS）
  - `VenueRelationType`：载体关联类型（PRECEDING、SUCCEEDING、ABSORBED、ABSORBED_BY、MERGED、SPLIT_FROM、CONTINUED_BY、CONTINUES）
  - `CitationSubset`：引用子集（IM、AIM、N、D、H、K、T、E、S、X、B、C、F、Q）
  - `IndexingTreatment`：索引处理方式（FULL、SELECTIVE）

### Infrastructure 层
- `MeshDescriptorRepositoryAdapter`：主题词仓储适配器
- `MeshQualifierRepositoryAdapter`：限定词仓储适配器
- `VenueRepositoryAdapter`：载体聚合根仓储适配器（批量导入）
- `MeshDescriptorConverter`：主题词对象转换器
- `MeshQualifierConverter`：限定词对象转换器
- `MeshSourceFileAdapter`：MeSH 源文件下载适配器（直接从 NLM 下载）
- `VenueSourceFileAdapter`：OpenAlex Venue 源文件下载适配器（直接从 S3 下载）

## 📊 数据模型

### 核心数据表
- **MeSH 相关表**
  - `cat_mesh_descriptor`：主题词表（~35,000 条）
  - `cat_mesh_qualifier`：限定词表（~100 条）
  - `cat_mesh_tree_number`：树形编号表（~80,000 条）
  - `cat_mesh_entry_term`：入口术语表（~250,000 条）
  - `cat_mesh_concept`：概念表（~180,000 条）
  - `cat_mesh_entry_combination`：组合条目表（~500 条）
  - `cat_publication_mesh`：文献-MeSH 关联表

- **Venue（载体）相关表**
  - `cat_venue`：载体主表（期刊/仓储/会议，包含多数据源扩展字段）
  - `cat_venue_identifier`：载体标识符表（ISSN/OpenAlex ID/NLM ID/DOAJ/JCR 等）
  - `cat_venue_publication_stats`：载体年度发文统计表（发表量/被引量/OA 发文量）
  - `cat_venue_source_data`：载体数据源表（各数据源原始 JSON 和提取字段）
  - `cat_venue_rating`：载体评级表（JCR/中科院分区/Scopus 等多评价体系年度评级）
  - `cat_venue_mesh`：载体 MeSH 主题表（MeSH 主题词分类，与 cat_publication_mesh 命名风格一致）
  - `cat_venue_relation`：载体关联表（期刊演变关系：前刊/后刊/合并/分拆）
  - `cat_venue_indexing_history`：载体索引历史表（MEDLINE/PMC 索引收录变迁）

## 🧪 测试覆盖

| 层级 | 测试类型 | 覆盖率目标 |
|------|---------|-----------|
| Domain | 单元测试 | ≥80% |
| Application | 单元测试 | ≥70% |
| Infrastructure | 单元+集成测试 | ≥70% |
| Adapter | 单元+切片测试 | ≥70% |
| Boot | E2E 测试 | 核心流程 |

## 📝 变更日志
1. v0.8.0 (2025-12-06)：NLM Serfile 数据扩展
   - 新增 3 个实体：`VenueMesh`（MeSH 主题词）、`VenueRelation`（期刊关联）、`VenueIndexingHistory`（索引历史）
   - 新增值对象：`VenueLanguages`（期刊语言信息，含主要语言和摘要语言）
   - 新增枚举：`VenueRelationType`（期刊关联类型）、`CitationSubset`（引用子集）、`IndexingTreatment`（索引处理方式）
   - `VenueIdentifierType` 扩展：新增 `CODEN`（6 字符标识符）
   - `VenueAggregate` 扩展：新增 `coden`、`frequency`、`languages`、`meshTerms`、`relations`、`indexingHistories` 字段
   - 新增数据库表：`cat_venue_mesh`（与 cat_publication_mesh 命名风格一致）、`cat_venue_relation`、`cat_venue_indexing_history`
   - `cat_venue` 扩展字段：`coden`、`frequency`、`languages`（JSON）
   - `VenueMesh` / `cat_venue_mesh` 新增 `qualifier_ui` 字段支持 MeSH 限定符精确关联

2. v0.7.0 (2025-12-06)：Venue 多数据源表结构重设计
   - **Breaking Change**：`cat_venue_metrics` 重命名为 `cat_venue_publication_stats`
   - **架构决策**：[[ADR-011]] Venue 多数据源架构设计
   - 新增 `cat_venue_source_data` 表：存储各数据源原始 JSON 和提取字段
   - 新增 `cat_venue_rating` 表：通用评级表支持 JCR/中科院分区/Scopus 等多评价体系
   - `cat_venue` 扩展字段：`nlm_id`、`doi_prefix`、`publisher`、出版历史、索引收录、OA 类型、最新评级快照
   - 新增领域实体：`VenueSourceData`（数据源溯源）、`VenueRating`（多评价体系评级）
   - 新增值对象：`PublicationHistory`、`IndexingInfo`、`LatestRating`、扩展 `OaStatus`
   - 新增枚举：`DataSourceCode`（数据源代码）、`RatingSystem`（评价体系）
   - `VenueIdentifierType` 扩展：新增 DOAJ、CROSSREF_MEMBER、JCR
   - 重命名 `VenueMetrics` → `VenuePublicationStats`

2. v0.6.2 (2025-12-05)：Repository 接口简化与依赖倒置
   - **Breaking Change**：Repository 接口改为以聚合根为操作单位
     - `VenueRepository`：删除 `saveBatch`/`saveIdentifiers`/`saveMetrics` 等方法，统一为 `insertAll(List<VenueAggregate>)`
     - `MeshDescriptorRepository`：删除 `saveBatch`/`saveTreeNumbersBatch`/`saveConceptsBatch`/`saveEntryTermsBatch` 等方法，统一为 `insertAll(List<MeshDescriptorAggregate>)`
   - 移除 Repository 内部 Record DTO 模式（`VenueData`、`VenueIdentifierData`、`VenueMetricsData`），直接操作领域聚合根
   - ItemWriter 改为依赖 Repository 接口（而非 Mapper），遵循依赖倒置原则（DIP）
   - 净删除约 1,200 行死代码
2. v0.6.1 (2025-12-05)：MeSH 导入配置驱动简化
   - **Breaking Change**：移除 XXL-Job 参数传递，改为配置文件驱动
     - URL 从 `patra.catalog.mesh.descriptor-url` / `qualifier-url` 配置读取
     - 版本号从文件名自动推断（`desc2025.xml` → `2025`）
   - 新增 `MeshDataSourceProperties`：MeSH 数据源配置属性类
   - 新增 `MeshFileNameParser`：MeSH 文件名解析工具
   - 新增 `MeshConfigurationException`：Adapter 层配置异常（替代 Domain 层异常）

2. v0.6.0 (2025-12-04)：简化导入语义为一次性初始化
   - **Breaking Change**：删除 `DataImportMode` 枚举（INCREMENTAL/TRUNCATE_REIMPORT 模式）
   - **Breaking Change**：移除对象存储缓存机制，改为直接从源站下载
     - 删除 `MeshCacheProperties`、`OpenAlexCacheProperties` 配置类
     - 删除 `MeshSourceFileConfiguration`、`VenueSourceFileConfiguration` 条件装配
     - MeSH 直接从 NLM 官方服务器下载，Venue 直接从 OpenAlex S3 下载
   - 新增 `DataAlreadyExistsException`：表中已有数据时抛出，拒绝重复导入
   - Repository 接口变更：`truncateAll()` → `hasAnyData()`
   - Venue 导入策略变更：从 Upsert 改为纯 INSERT
   - 设计理念：数据导入为一次性初始化操作，需重新导入时由用户手动清空数据库

2. v0.5.0 (2025-12-03)：OpenAlex Venue 批量导入
   - 新增 `VenueImportOrchestrator`：Venue 导入用例编排
   - 新增 `VenueImportScheduleJob`：XXL-Job 调度入口（`venueImportJob`）
   - 新增 `OpenAlexSourceParser`：OpenAlex JSONL 数据解析
   - 新增 `VenueSourceFilePort`/`VenueImportBatchPort`：源文件和批处理端口
2. v0.4.0 (2025-12-02)：Venue 聚合重构
   - **架构决策**：[[ADR-010]] 分离标识符和年度指标为独立实体
   - 新增 `VenueIdentifier` 实体：支持多种标识符类型（ISSN/OpenAlex/NLM/MAG 等）
   - 新增 `VenueMetrics` 实体：支持年度发表量、被引量、OA 比例时序分析
   - 新增值对象：`HostOrganization`、`VenueStats`、`ApcInfo`、`Society`、`ProvenanceInfo`
   - 新增枚举：`VenueType`、`VenueIdentifierType`
   - Repository 以聚合根为操作单位，保持 DDD 一致性边界
   - 新增数据库表：`cat_venue_identifier`、`cat_venue_metrics`
2. v0.3.0 (2025-12-01)：MeSH 源文件下载适配器
   - 新增 `MeshSourceFileAdapter`：从 NLM 官方服务器下载 MeSH XML 文件
   - 新增 `MeshSourceFilePort`：定义源文件获取端口接口
2. v0.2.2 (2025-11-27)：MeSH 子表关联键优化
   - 将 MeSH 子表（TreeNumber、EntryTerm、Concept 等）的关联键从数据库自增 ID 改为 MeSH 原生 UI 标识符
   - Domain 层使用 `MeshUI` 值对象，Infra 层使用 `String`
   - 简化数据导入流程，无需先查询主表获取自增 ID
2. v0.2.1 (2025-11-27)：XML 解析器策略模式重构
   - XmlParserAdapter 重构为门面类（1800 行 → 156 行）
   - 新增 5 个解析策略：Descriptor、Qualifier、Concept、EntryTerm、TreeNumber
   - 支持 ConceptRelation 概念关系解析和持久化
3. v0.2.0 (2025-11-27)：完善 MeSH 2025 DTD 支持
   - 新增 EntryCombination 组合条目值对象及数据库表
   - 增强 MeshConcept：支持 registryNumbers 多值、translator 字段
   - 增强 MeshEntryTerm：新增 termUi、conceptUi 等 9 个字段
   - 增强 MeshDescriptor：新增 historyNote、onlineNote、nlmClassificationNumber 字段
   - 修复 ConceptList 解析被跳过的问题（P0 Bug）
4. v0.1.0 (2025-11-27)：初始版本，完成 MeSH 主题词和限定词的导入功能。

## 📖 相关文档

- [项目架构宪章](../.specify/memory/constitution.md)
- [patra-spring-boot-starter-batch](../patra-spring-boot-starter-batch/README.md)
