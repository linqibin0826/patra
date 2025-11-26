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

### 计划功能
- **MeSH 数据批量导入**（将使用 Spring Batch 实现）
- MeSH 增量更新（每年更新）
- 期刊影响因子批量更新
- 作者消歧和合并
- 机构标准化和映射

## 📦 模块依赖

### 内部依赖
- `patra-common-core`：通用工具和基础类
- `patra-common-model`：共享领域模型
- `patra-spring-boot-starter-mybatis`：数据访问层支持
- `patra-spring-boot-starter-batch`：批处理支持（计划使用）

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
- `MeshDescriptorRepositoryImpl`：主题词仓储实现
- `MeshQualifierRepositoryImpl`：限定词仓储实现
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

### v0.3.0 (开发中)
- 计划：使用 Spring Batch 实现 MeSH 数据批量导入
- 计划：支持断点续传和进度监控

### v0.2.0 (2025-11-26)
- 重构：移除自定义 MeSH 导入实现，准备迁移至 Spring Batch
- 保留：MeSH 数据模型（Descriptor、Qualifier、Concept 等）

### v0.1.0 (2025-01-01)
- 初始版本
- 基础目录管理功能
- 文献、作者、机构、期刊 CRUD

## 📖 相关文档

- [项目架构宪章](../.specify/memory/constitution.md)
- [patra-spring-boot-starter-batch](../patra-spring-boot-starter-batch/README.md)
