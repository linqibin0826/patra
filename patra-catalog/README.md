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
   - 支持从 HTTP/HTTPS URL 直接下载 XML 数据文件
   - **支持对象存储缓存**：优先从 MinIO 读取，减少 NLM 远程下载
   - 支持断点续传（Job 级别幂等执行）
   - 支持增量导入（INCREMENTAL）和全量重导入（TRUNCATE_REIMPORT）模式
   - 批量 SQL 插入优化（单条 SQL 多行值）
   - 临时文件自动清理（Job 完成/停止后自动删除，失败时保留支持续传）
   - XXL-Job 调度入口（`meshDescriptorImportJob`）

4. **MeSH 限定词导入**
   - 使用 StAX 流式解析 XML（约 80 条限定词）
   - 支持从 HTTP/HTTPS URL 下载 Qualifier XML 文件
   - XXL-Job 调度入口（`meshQualifierImportJob`）
   - 每次导入前清空现有数据（TRUNCATE_REIMPORT 模式）

### 计划功能
- MeSH 增量更新（每年更新）
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
- `patra-spring-boot-starter-object-storage`（可选）：MeSH 文件缓存支持
- `patra-spring-boot-starter-core`：异步线程池支持（缓存上传）

### 外部依赖
- Spring Boot 3.5.7
- Spring Cloud 2025.0.0
- MyBatis-Plus 3.5.12
- Nacos（配置中心和服务发现）

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
  - `VenueMetrics`：载体年度指标实体（发表量/被引量/OA 比例）
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

- **枚举**
  - `MeshDataType`：MeSH 数据类型（QUALIFIER、DESCRIPTOR、TREE_NUMBER、ENTRY_TERM、CONCEPT）
  - `DescriptorClass`：主题词分类
  - `LexicalTag`：词汇标记
  - `VenueType`：载体类型（JOURNAL、REPOSITORY、CONFERENCE、EBOOK_PLATFORM、BOOK_SERIES）
  - `VenueIdentifierType`：标识符类型（ISSN、OPENALEX、NLM、MAG、FATCAT、WIKIDATA）

### Infrastructure 层
- `MeshDescriptorRepositoryAdapter`：主题词仓储适配器
- `MeshQualifierRepositoryAdapter`：限定词仓储适配器
- `VenueRepositoryAdapter`：载体仓储适配器（含标识符和指标管理）
- `MeshDescriptorConverter`：主题词对象转换器
- `MeshQualifierConverter`：限定词对象转换器
- `MeshSourceFileConfiguration`：MeSH 源文件适配器条件装配
- `MeshSourceFileAdapter`：带对象存储缓存的 MeSH 文件适配器
- `DefaultMeshSourceFileAdapter`：无缓存回退实现
- `MeshCacheProperties`（record）：MeSH 文件缓存配置属性

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
  - `cat_venue`：载体主表（期刊/仓储/会议）
  - `cat_venue_identifier`：载体标识符表（ISSN/OpenAlex ID/NLM ID 等）
  - `cat_venue_metrics`：载体年度指标表（发表量/被引量）

## 🧪 测试覆盖

| 层级 | 测试类型 | 覆盖率目标 |
|------|---------|-----------|
| Domain | 单元测试 | ≥80% |
| Application | 单元测试 | ≥70% |
| Infrastructure | 单元+集成测试 | ≥70% |
| Adapter | 单元+切片测试 | ≥70% |
| Boot | E2E 测试 | 核心流程 |

## 🗄️ MeSH 数据缓存策略

### 概述

MeSH 源文件（Descriptor/Qualifier XML）支持对象存储缓存，减少从 NLM 官方服务器下载的频率。

### 缓存优先策略

执行流程：
1. 检查对象存储（MinIO）中是否存在缓存文件
2. **缓存命中**：从对象存储下载到本地临时目录
3. **缓存未命中**：从远程 URL 下载，并异步上传到对象存储作为缓存
4. **静默降级**：缓存检查/下载失败时自动回退到远程下载（记录 warn 日志）

### 运行时动态选择

使用 `ObjectProvider` 延迟注入，在运行时根据对象存储的可用性自动选择实现：

```
ObjectStorageOperations 可用?
├── 是 → MeshSourceFileAdapter（缓存优先策略）
└── 否 → DefaultMeshSourceFileAdapter（直接远程下载）
```

> **实现说明**：使用 `ObjectProvider.getIfAvailable()` 延迟获取依赖，避免对 AutoConfiguration 加载顺序的依赖。

### 缓存键格式

```
{keyPrefix}/{dataType}s/{filePrefix}{version}.xml
```

**示例**：
- Descriptor + 2025 → `mesh/descriptors/desc2025.xml`
- Qualifier + 2025 → `mesh/qualifiers/qual2025.xml`

### 配置示例

```yaml
patra:
  # 异步线程池（必须配置 cache-upload 用于缓存上传）
  async:
    pools:
      cache-upload:
        core-size: 2
        max-size: 4
        queue-capacity: 50

  # 对象存储配置
  object-storage:
    active-provider: minio
    providers:
      minio:
        endpoint: http://localhost:19000
        access-key: minioadmin
        secret-key: minioadmin123

  # MeSH 文件缓存配置
  catalog:
    mesh-cache:
      enabled: true                  # 是否启用缓存（默认 false）
      bucket: patra-catalog-cache    # 存储桶名称
      key-prefix: mesh               # 对象键前缀
```

### 配置属性说明

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `patra.catalog.mesh-cache.enabled` | boolean | `false` | 是否启用缓存 |
| `patra.catalog.mesh-cache.bucket` | String | `patra-catalog-cache` | 存储桶名称 |
| `patra.catalog.mesh-cache.key-prefix` | String | `mesh` | 对象键前缀 |

### 依赖关系

- **必需依赖**：`patra-spring-boot-starter-object-storage`（对象存储）
- **必需配置**：`patra.async.pools.cache-upload`（异步上传线程池）
- **推荐配置**：`patra.catalog.mesh-cache.enabled=true`（启用缓存）

## 📝 变更日志
1. v0.4.0 (2025-12-02)：Venue 聚合重构
   - **架构决策**：[[ADR-010]] 分离标识符和年度指标为独立实体
   - 新增 `VenueIdentifier` 实体：支持多种标识符类型（ISSN/OpenAlex/NLM/MAG 等）
   - 新增 `VenueMetrics` 实体：支持年度发表量、被引量、OA 比例时序分析
   - 新增值对象：`HostOrganization`、`VenueStats`、`ApcInfo`、`Society`、`ProvenanceInfo`
   - 新增枚举：`VenueType`、`VenueIdentifierType`
   - Repository 使用内部 Record DTO 模式，解耦领域对象与持久化
   - 新增数据库表：`cat_venue_identifier`、`cat_venue_metrics`
2. v0.3.0 (2025-12-01)：MeSH 源文件缓存功能
   - 新增 `MeshSourceFileAdapter`：带对象存储缓存的文件下载
   - 新增 `DefaultMeshSourceFileAdapter`：无缓存回退实现
   - 运行时动态选择：使用 `ObjectProvider` 延迟注入，根据 `ObjectStorageOperations` 可用性自动选择实现
   - 异步上传：下载完成后异步上传到 MinIO，不阻塞主流程
   - 静默降级：缓存失败时自动回退到远程下载
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
