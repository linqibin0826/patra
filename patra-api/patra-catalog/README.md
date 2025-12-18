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

5. **OpenAlex Venue 数据批量导入**（Spring Batch 实现）
   - 从 OpenAlex S3 Manifest 动态获取分区文件列表
   - **流式下载**：按需从 OpenAlex S3 流式下载分区文件（无磁盘落盘）
   - 支持多文件顺序读取（gzip 压缩 JSONL 格式）
   - **一次性初始化语义**：纯 INSERT 写入，表中有数据时拒绝导入
   - 支持断点续传（Job 级别幂等执行，失败时需重新下载）
   - XXL-Job 调度入口（`venueInitializeJob`）

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
- `patra-spring-boot-starter-jpa`：数据访问层支持
- `patra-spring-boot-starter-batch`：批处理支持
- `patra-spring-boot-starter-rest-client`：HTTP 客户端支持（文件下载）
- `patra-spring-boot-starter-core`：核心基础设施

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
  - `VenueRatingAggregate`：载体评级聚合根（独立聚合，支持 JCR/CAS/Scopus）
  - `PublicationAggregate`：文献聚合根（规划中）

- **实体**
  - `MeshConcept`：MeSH 概念实体
  - `MeshEntryTerm`：MeSH 入口术语实体
  - `MeshTreeNumber`：MeSH 树形编号实体
  - `Author`：作者实体
  - `Affiliation`：机构实体

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
  - `AffiliationId`：机构聚合根 ID
  - `AuthorId`：作者聚合根 ID
  - `PublicationId`：出版物聚合根 ID
  - `VenueRatingId`：载体评级聚合根 ID

- **值对象**
  - `MeshUI`：MeSH 唯一标识符
  - `EntryCombination`：MeSH 组合条目（ECIN/ECOUT 映射）
  - `AllowableQualifier`：允许的限定词
  - `PharmacologicalAction`：药理作用
  - `SeeRelatedDescriptor`：相关主题词引用
  - `ConceptRelation`：概念关系
  - `HostOrganization`：主办机构信息
  - `ProvenanceInfo`：数据来源信息
  - `PublicationHistory`：出版历史（创刊/停刊年份）
  - `IndexingInfo`：MEDLINE 索引收录信息
  - `LatestRating`：最新评级快照（冗余，高频查询优化）
  - `OaStatus`：开放获取状态
  - `VenueLanguages`：期刊语言信息（主要语言 + 摘要语言列表，来源 Serfile）

- **值对象（venue/pubmed 子包）**：PubMed Serfile 解析结果的完整领域模型（解析器直接产出）
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
- `VenueRepositoryAdapter`：载体聚合根仓储适配器（含 identifiers 和补充数据）
- `MeshDescriptorJpaMapper`：主题词 Entity ↔ Aggregate 映射器（MapStruct）
- `MeshQualifierJpaMapper`：限定词 Entity ↔ Aggregate 映射器（MapStruct）
- `VenueJpaMapper`：载体聚合根 Entity ↔ Aggregate 映射器（含快速访问字段提取）
- `StreamingDownloadAdapter`：流式下载适配器（HTTP 响应体直接返回 InputStream，无磁盘落盘）
- `VenueSourceFileAdapter`：OpenAlex Venue 源文件适配器（封装 manifest 解析）

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
  - `cat_venue`：载体主表（含 4 个 JSON 嵌入式值对象：publication_profile、citation_metrics、open_access、affiliated_societies）
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
1. v0.9.8 (2025-12-18)：patra-catalog-infra 包结构重构
   - **包结构整合**：将 `infra/batch/` 目录整合到 `infra/adapter/batch/` 下
     - `batch/mesh/*` → `adapter/batch/mesh/*`（MeSH 批处理组件）
     - `batch/venue/*` → `adapter/batch/venue/*`（Venue 批处理组件）
   - **Converter 重命名**：MapStruct 映射器统一命名为 `*JpaMapper`（区分 JPA AttributeConverter）
     - `*JpaConverter.java` → `converter/mapper/*JpaMapper.java`
     - JPA AttributeConverter 移至 `converter/attribute/` 子包
   - **设计说明**：
     - `adapter/batch/{usecase}/` 结构使 Port 适配器与其支撑组件（JobConfig/ItemReader/ItemWriter）共存
     - `converter/mapper/` 与 `converter/attribute/` 分离明确两种转换器的职责
   - 作为 JPA 模块包结构规范示例，供其他模块迁移参考

2. v0.9.7 (2025-12-13)：Venue 嵌入式值对象设计
   - **架构决策**：[[ADR-019]] 使用 JSON 字段存储嵌入式值对象，实现 DDD 聚合设计
   - **嵌入式值对象**（4 个 JSON 字段）：
     - `PublicationProfile`：出版概况（缩写标题、备选标题、主页 URL、宿主机构）
     - `CitationMetrics`：引用指标（作品数、被引数、H 指数、i10 指数）
     - `OpenAccessInfo`：开放获取信息（OA 状态 + APC 定价）
     - `Society`：关联学会（学会名称、URL）
   - **设计优势**：单表查询、无 JOIN、嵌入式值对象随聚合根一起保存

2. v0.9.6 (2025-12-12)：VenueRating 独立聚合根设计
   - **架构决策**：[[ADR-018]] `VenueRatingAggregate` 作为独立聚合根设计
   - **设计原则**：
     - 独立生命周期：评级数据有独立的创建/更新/删除周期
     - 独立一致性边界：通过 `venue_id` 逻辑关联，无物理外键
     - 业务唯一键：`(venueId, year, ratingSystem)` 由数据库唯一索引保证
   - **新增文件**：
     - `VenueRatingAggregate`：载体评级聚合根（支持 JCR/CAS/Scopus 三种评价体系）
     - `VenueRatingId`：强类型 ID 值对象（封装数据库主键）
     - `VenueRatingRepository`：仓储接口（Domain 层端口）
     - `VenueRatingRepositoryAdapter`：仓储实现（JPA）
   - **Converter 设计**：`VenueRatingConverter` 实现聚合根与 DO 双向转换
   - **测试覆盖**：54 个聚合根单元测试 + 12 个 Converter 测试 + 27 个仓储集成测试

2. v0.9.5 (2025-12-12)：Venue 快速访问字段优化
   - **CQRS 设计优化**：在 `cat_venue` 主表添加 6 个快速访问字段，优化列表展示和搜索查询性能
   - **冗余字段**（3 个）：`nlm_id`、`issn_l`、`openalex_id` 从 `cat_venue_identifier` 冗余
   - **快照字段**（3 个）：`abbreviated_title`、`primary_language`、`country_code` 从 `cat_venue_detail` 同步
   - **新增值对象**：`VenueDetail`（出版信息、语言信息、宿主机构、OA 状态）
   - **新增数据表**：`cat_venue_detail`（CQRS 补充数据表，1:1 关系）
   - **Converter 增强**：`VenueConverter` 新增 `extractIdentifier()` 方法提取标识符快速访问字段
   - **Repository 增强**：`VenueRepositoryAdapter` 新增 `syncVenueQuickAccessFields()` 同步详情快照字段
   - **字段更新策略**：快速访问字段使用 `FieldStrategy.ALWAYS` 确保 null 值正确更新

2. v0.9.4 (2025-12-11)：PubMed Serfile 解析架构优化 - 删除 DTO 层
   - **架构决策**：基于务实六边形架构原则（Victor Rentea），删除冗余 DTO 层，解析器直接产出 Domain 模型
   - **删除文件**：`dto/serfile/` 目录（10 个 DTO 类）+ `PubmedSerialConverter.java`
   - **代码精简**：净删除约 500 行代码，减少 53% 的类数量
   - **信息完整**：Domain 模型扩展至完整 44 字段，不再有信息丢失
   - **解析器更新**：`SerialParsingStrategy` 直接构建 `PubmedSerialData` 及其嵌套值对象
   - **值对象扩展**：新增 5 个值对象（BroadHeading、CrossReference、CurrentIndexing、GeneralNote、RecordId）
   - **测试更新**：24 个单元测试 + 10 个集成测试全部通过

2. v0.9.3 (2025-12-09)：VenueRating/VenueSourceData Record 值对象设计
   - **值对象设计**：`VenueRating` 和 `VenueSourceData` 采用不可变 Record 实现
     - 位置：`vo/venue/` 包
     - 无技术 ID 字段（值对象无独立身份标识）
     - Record 天然不可变
   - **新增 Converter**：
     - `VenueRatingConverter`：评级值对象 ↔ DO 转换（含枚举降级和 JSON 解析日志）
     - `VenueSourceDataConverter`：数据源值对象 ↔ DO 转换（含枚举降级和 JSON 解析日志）
   - **Converter 健壮性增强**：
     - `VenueIndexingHistoryConverter.toEntity()` 添加 null 检查
     - `VenueRatingConverter` / `VenueSourceDataConverter` 添加 `@Slf4j` 日志记录
   - **测试补充**：新增 `VenueRatingConverterTest`、`VenueSourceDataConverterTest`

2. v0.9.2 (2025-12-09)：Venue Converter 提取 + 时间统计优化
   - **Converter 提取**：将 `VenueRepositoryAdapter` 内联转换逻辑提取为独立的 MapStruct Converter
     - 新增 `VenueIdentifierConverter`：标识符领域实体 ↔ DO 转换
     - 新增 `VenueIndexingHistoryConverter`：索引历史 ↔ DO 转换（含枚举值防御处理）
     - 新增 `VenueMeshConverter`：MeSH 主题词 ↔ DO 转换（含 Boolean 包装类型处理）
     - 新增 `VenuePublicationStatsConverter`：年度统计 ↔ DO 转换（含类型转换和默认值）
     - 新增 `VenueRelationConverter`：关联关系 ↔ DO 转换（无效枚举值降级为 PRECEDING）
   - **Repository 删除**：移除 `VenueRatingRepository` 和 `VenueSourceDataRepository`（功能已合并到 `VenueRepository`）
   - **时间统计优化**：使用 Hutool `TimeInterval` 替换 `System.currentTimeMillis()` 手动计时
     - `VenuePubmedEnrichHandler`：3 处计时点优化
   - **测试补充**：5 个 Converter 新增单元测试

2. v0.9.1 (2025-12-08)：VenueAggregate 聚合边界设计 + DTO 层级优化
   - **架构决策**：[[ADR-014]] 基于 Vaughn Vernon 聚合设计规则，将无聚合级不变量的子实体移出聚合边界
   - **Breaking Change**：5 个实体类（VenueIdentifier、VenuePublicationStats、VenueMesh、VenueRelation、VenueIndexingHistory）从 Class 改为 Record
   - **Breaking Change**：yearlyMetrics、meshTerms、relations、indexingHistories 从 VenueAggregate 移出
   - `VenueRepository` 扩展：合并补充数据管理方法（年度指标/MeSH/关联/索引历史）
   - 新增 `VenueParseResult` record：封装 OpenAlex 解析结果（聚合根 + 年度指标）
   - VenueAggregate 从约 1030 行精简至约 720 行
   - 保留 identifiers 在聚合内（有 ISSN-L 唯一性不变量需要保护）
   - **DTO 迁移**：Serfile DTO（`Serial*` 10 个类）从 Domain 层移至 Infra 层 `adapter/parser/dto/serfile/`
   - **新增领域模型**：`domain/model/vo/venue/pubmed/` 包（精简版，仅保留业务使用的 15 个字段）
     - `PubmedSerialData`：PubMed 期刊解析数据（替代 SerialRecord 的 38 字段）
     - `PubmedLanguage`、`PubmedMeshHeading`、`PubmedTitleRelation`、`PubmedIndexingHistory`
   - **SerfileParserPort 返回类型变更**：从 `SerialRecord` (DTO) 改为 `PubmedSerialData` (领域模型)
   - 新增 `PubmedSerialConverter`：Infra 层转换器，实现 DTO → 领域模型的转换
   - **UseCase 重命名**：`VenueImport*` → `VenueInitialize*`，`SerfileImport*` → `VenuePubmedEnrich*`

2. v0.9.0 (2025-12-07)：流式下载架构优化
   - **Breaking Change**：删除 `FileDownloadPort`、`MeshSourceFilePort` 及其实现
   - 新增 `StreamingDownloadPort` 和 `StreamingDownloadResult`（Domain 层端口）
   - 新增 `StreamingDownloadAdapter`（Infra 层实现）
   - 所有 Parser 端口改为接收 `InputStream` 而非 `Path`
   - MeSH/Venue/Serfile 导入从"下载临时文件"改为"流式下载"
   - 批处理 ItemReader 改为在 `open()` 时建立 HTTP 连接
   - 移除临时文件清理逻辑

2. v0.8.1 (2025-12-07)：NLM Serfile DTD 完整支持
   - **SerialRecord 字段扩展**：从 15 个字段扩展到 44 个字段，完整覆盖 NLM Serfile DTD (nlmserials_230101.dtd)
   - **新增 6 个 DTO 类**：
     - `SerialLanguage`：语言信息（含 LangType 属性：Primary/Summary）
     - `SerialBroadHeading`：广泛期刊分类
     - `SerialCrossReference`：交叉引用（XrType: A/X/S）
     - `SerialGeneralNote`：通用备注（含 NoteType 属性）
     - `SerialCurrentlyIndexedForSubset`：当前索引子集信息
     - `SerialRecordId`：记录 ID（含 Source 属性：NLM/LC/OCLC）
   - **DTO 修改**：
     - `SerialMeshHeading`：新增 `descriptorUi`、`descriptorType` 属性
     - `SerialTitleRelated`：新增 `recordIds` 列表
   - **解析器增强**：
     - `SerialParsingStrategy`：支持所有 Serial 元素属性和子元素解析
     - `XmlParsingHelper`：新增 `parseTimestamp()`、`parseYesNoAttributeNullable()` 方法
   - **Handler 适配**：`VenuePubmedEnrichHandler` 新增 `toVenueLanguages()` 转换方法

3. v0.8.0 (2025-12-06)：NLM Serfile 数据扩展
   - 新增 3 个实体：`VenueMesh`（MeSH 主题词）、`VenueRelation`（期刊关联）、`VenueIndexingHistory`（索引历史）
   - 新增值对象：`VenueLanguages`（期刊语言信息，含主要语言和摘要语言）
   - 新增枚举：`VenueRelationType`（期刊关联类型）、`CitationSubset`（引用子集）、`IndexingTreatment`（索引处理方式）
   - `VenueIdentifierType` 扩展：新增 `CODEN`（6 字符标识符）
   - `VenueAggregate` 扩展：新增 `coden`、`frequency`、`languages`、`meshTerms`、`relations`、`indexingHistories` 字段
   - 新增数据库表：`cat_venue_mesh`（与 cat_publication_mesh 命名风格一致）、`cat_venue_relation`、`cat_venue_indexing_history`
   - `cat_venue` 扩展字段：`coden`、`frequency`、`languages`（JSON）
   - `VenueMesh` / `cat_venue_mesh` 新增 `qualifier_ui` 字段支持 MeSH 限定符精确关联

4. v0.7.0 (2025-12-06)：Venue 多数据源表结构重设计
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

5. v0.6.2 (2025-12-05)：Repository 接口简化与依赖倒置
   - **Breaking Change**：Repository 接口改为以聚合根为操作单位
     - `VenueRepository`：删除 `saveBatch`/`saveIdentifiers`/`saveMetrics` 等方法，统一为 `insertAll(List<VenueAggregate>)`
     - `MeshDescriptorRepository`：删除 `saveBatch`/`saveTreeNumbersBatch`/`saveConceptsBatch`/`saveEntryTermsBatch` 等方法，统一为 `insertAll(List<MeshDescriptorAggregate>)`
   - 移除 Repository 内部 Record DTO 模式（`VenueData`、`VenueIdentifierData`、`VenueMetricsData`），直接操作领域聚合根
   - ItemWriter 改为依赖 Repository 接口（而非 Mapper），遵循依赖倒置原则（DIP）
   - 净删除约 1,200 行死代码
6. v0.6.1 (2025-12-05)：MeSH 导入配置驱动简化
   - **Breaking Change**：移除 XXL-Job 参数传递，改为配置文件驱动
     - URL 从 `patra.catalog.mesh.descriptor-url` / `qualifier-url` 配置读取
     - 版本号从文件名自动推断（`desc2025.xml` → `2025`）
   - 新增 `MeshDataSourceProperties`：MeSH 数据源配置属性类
   - 新增 `MeshFileNameParser`：MeSH 文件名解析工具
   - 新增 `MeshConfigurationException`：Adapter 层配置异常（替代 Domain 层异常）

7. v0.6.0 (2025-12-04)：简化导入语义为一次性初始化
   - **Breaking Change**：删除 `DataImportMode` 枚举（INCREMENTAL/TRUNCATE_REIMPORT 模式）
   - **Breaking Change**：移除对象存储缓存机制，改为直接从源站下载
     - 删除 `MeshCacheProperties`、`OpenAlexCacheProperties` 配置类
     - 删除 `MeshSourceFileConfiguration`、`VenueSourceFileConfiguration` 条件装配
     - MeSH 直接从 NLM 官方服务器下载，Venue 直接从 OpenAlex S3 下载
   - 新增 `DataAlreadyExistsException`：表中已有数据时抛出，拒绝重复导入
   - Repository 接口变更：`truncateAll()` → `hasAnyData()`
   - Venue 导入策略变更：从 Upsert 改为纯 INSERT
   - 设计理念：数据导入为一次性初始化操作，需重新导入时由用户手动清空数据库

8. v0.5.0 (2025-12-03)：OpenAlex Venue 批量导入
   - 新增 `VenueInitializeHandler`：Venue 初始化命令处理器
   - 新增 `VenueInitializeScheduleJob`：XXL-Job 调度入口（`venueInitializeJob`）
   - 新增 `OpenAlexSourceParser`：OpenAlex JSONL 数据解析
   - 新增 `VenueSourceFilePort`/`VenueInitializeBatchPort`：源文件和批处理端口
9. v0.4.0 (2025-12-02)：Venue 聚合设计
   - **架构决策**：[[ADR-010]] 分离标识符和年度指标为独立实体
   - 新增 `VenueIdentifier` 实体：支持多种标识符类型（ISSN/OpenAlex/NLM/MAG 等）
   - 新增 `VenueMetrics` 实体：支持年度发表量、被引量、OA 比例时序分析
   - 新增值对象：`HostOrganization`、`VenueStats`、`ApcInfo`、`Society`、`ProvenanceInfo`
   - 新增枚举：`VenueType`、`VenueIdentifierType`
   - Repository 以聚合根为操作单位，保持 DDD 一致性边界
   - 新增数据库表：`cat_venue_identifier`、`cat_venue_metrics`
10. v0.3.1 (2025-12-06)：Parser Port 单一职责设计
   - `MeshDescriptorParserPort`/`MeshDescriptorParserAdapter` 专用主题词解析
   - `MeshQualifierParserPort`/`MeshQualifierParserAdapter` 专用限定词解析
   - `DescriptorListParsers` 工具类封装列表解析逻辑
   - `ReferredTo` record 提供类型安全
11. v0.3.0 (2025-12-01)：MeSH 源文件下载适配器
   - 新增 `MeshSourceFileAdapter`：从 NLM 官方服务器下载 MeSH XML 文件
   - 新增 `MeshSourceFilePort`：定义源文件获取端口接口
12. v0.2.2 (2025-11-27)：MeSH 子表关联键优化
   - 将 MeSH 子表（TreeNumber、EntryTerm、Concept 等）的关联键从数据库自增 ID 改为 MeSH 原生 UI 标识符
   - Domain 层使用 `MeshUI` 值对象，Infra 层使用 `String`
   - 简化数据导入流程，无需先查询主表获取自增 ID
13. v0.2.1 (2025-11-27)：XML 解析器策略模式设计
   - XmlParserAdapter 门面类设计
   - 5 个解析策略：Descriptor、Qualifier、Concept、EntryTerm、TreeNumber
   - 支持 ConceptRelation 概念关系解析和持久化
14. v0.2.0 (2025-11-27)：完善 MeSH 2025 DTD 支持
   - 新增 EntryCombination 组合条目值对象及数据库表
   - 增强 MeshConcept：支持 registryNumbers 多值、translator 字段
   - 增强 MeshEntryTerm：新增 termUi、conceptUi 等 9 个字段
   - 增强 MeshDescriptor：新增 historyNote、onlineNote、nlmClassificationNumber 字段
   - 修复 ConceptList 解析被跳过的问题（P0 Bug）
15. v0.1.0 (2025-11-27)：初始版本，完成 MeSH 主题词和限定词的导入功能。

## 📖 相关文档

- [patra-spring-boot-starter-batch](../patra-spring-boot-starter-batch/README.md)
