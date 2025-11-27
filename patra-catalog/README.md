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
  - `PublicationAggregate`：文献聚合根（规划中）

- **实体**
  - `MeshConcept`：MeSH 概念实体
  - `MeshEntryTerm`：MeSH 入口术语实体
  - `MeshTreeNumber`：MeSH 树形编号实体
  - `Author`：作者实体
  - `Affiliation`：机构实体
  - `Venue`：期刊实体

- **值对象**
  - `MeshUI`：MeSH 唯一标识符
  - `DescriptorId`：主题词强类型 ID

- **枚举**
  - `MeshDataType`：MeSH 数据类型（QUALIFIER、DESCRIPTOR、TREE_NUMBER、ENTRY_TERM、CONCEPT）
  - `DescriptorClass`：主题词分类
  - `LexicalTag`：词汇标记

### Infrastructure 层
- `MeshDescriptorRepositoryAdapter`：主题词仓储适配器
- `MeshQualifierRepositoryAdapter`：限定词仓储适配器
- `MeshDescriptorConverter`：主题词对象转换器
- `MeshQualifierConverter`：限定词对象转换器

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

## 🧪 测试覆盖

| 层级 | 测试类型 | 覆盖率目标 |
|------|---------|-----------|
| Domain | 单元测试 | ≥80% |
| Application | 单元测试 | ≥70% |
| Infrastructure | 单元+集成测试 | ≥70% |
| Adapter | 单元+切片测试 | ≥70% |
| Boot | E2E 测试 | 核心流程 |

## 📝 变更日志
1. v0.2.0 (2025-11-27)：完善 MeSH 2025 DTD 支持
   - 新增 EntryCombination 组合条目值对象及数据库表
   - 增强 MeshConcept：支持 registryNumbers 多值、translator 字段
   - 增强 MeshEntryTerm：新增 termUi、conceptUi 等 9 个字段
   - 增强 MeshDescriptor：新增 historyNote、onlineNote、nlmClassificationNumber 字段
   - 修复 ConceptList 解析被跳过的问题（P0 Bug）
2. v0.1.0 (2025-11-27)：初始版本，完成 MeSH 主题词和限定词的导入功能。

## 📖 相关文档

- [项目架构宪章](../.specify/memory/constitution.md)
- [patra-spring-boot-starter-batch](../patra-spring-boot-starter-batch/README.md)
