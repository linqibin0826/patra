# patra-catalog 目录管理服务

## 📋 概述

patra-catalog 是 Patra 医学文献数据平台的目录管理微服务，负责管理医学文献的分类体系、主题词表、作者、机构、期刊等核心目录数据。本服务采用六边形架构设计，遵循 DDD 战术设计原则。

主要职责：
- 管理 MeSH（Medical Subject Headings）医学主题词表
- 维护期刊（Venue）目录和影响因子
- 管理作者（Author）和机构（Affiliation）信息
- 提供文献分类和标引服务
- 支持目录数据的批量导入和更新

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

2. **MeSH 主题词管理**（v0.2.0 新增）
   - MeSH 数据首次导入（支持 35 万条记录批量导入）
   - 主题词（Descriptor）管理
   - 限定词（Qualifier）管理
   - 树形编号（TreeNumber）层次结构
   - 入口术语（EntryTerm）同义词管理
   - 概念（Concept）关系维护

### 计划功能
- MeSH 增量更新（每年更新）
- 期刊影响因子批量更新
- 作者消歧和合并
- 机构标准化和映射

## 📦 模块依赖

### 内部依赖
- `patra-common-core`：通用工具和基础类
- `patra-common-model`：共享领域模型
- `patra-spring-boot-starter-mybatis-plus`：数据访问层支持
- `patra-spring-boot-starter-mapstruct`：对象转换支持

### 外部依赖
- Spring Boot 3.5.7
- Spring Cloud 2025.0.0
- MyBatis-Plus 3.5.12
- **spring-web**（Infrastructure 层，仅用于 RestClient）
- XXL-Job（任务调度）
- Redisson（分布式锁）
- Redis（缓存和分布式锁后端）
- Nacos（配置中心和服务发现）

### 依赖分层说明
- **Infrastructure 层**：仅引入 `spring-web`，不引入 `spring-boot-starter-web`（避免引入 Web 容器）
- **Boot 层**：引入 `redisson-spring-boot-starter`（分布式锁自动配置）

## 🎯 核心类说明

### Domain 层
- **聚合根**
  - `MeshImportAggregate`：MeSH 数据导入任务聚合根
  - `PublicationAggregate`：文献聚合根（规划中）

- **实体**
  - `MeshDescriptor`：MeSH 主题词实体
  - `MeshQualifier`：MeSH 限定词实体
  - `Author`：作者实体
  - `Affiliation`：机构实体
  - `Venue`：期刊实体

- **值对象**
  - `MeshImportId`：导入任务强类型 ID
  - `DescriptorId`：主题词强类型 ID
  - `TableProgress`：表导入进度值对象

### Application 层
- `MeshImportOrchestrator`：MeSH 导入任务编排器
- `PublicationOrchestrator`：文献管理编排器

### Infrastructure 层
- `MeshImportRepositoryImpl`：导入任务仓储实现
- `MeshDescriptorRepositoryImpl`：MeSH 数据仓储实现
- `StaxXmlParserImpl`：StAX 流式 XML 解析器
- `RestClientMeshFileDownloadImpl`：基于 Spring RestClient 的文件下载器

### Adapter 层
- `MeshImportController`：MeSH 导入管理 API
- `MeshImportJob`：XXL-Job 导入任务执行器

## 🔌 接口定义

### REST API
- **MeSH 导入管理**
  - `POST /api/v1/mesh/import/start`：开始导入任务
  - `GET /api/v1/mesh/import/progress/{taskId}`：查询导入进度
  - `POST /api/v1/mesh/import/retry/{taskId}`：重试失败任务
  - `POST /api/v1/mesh/import/clear`：清除数据重新导入

详细 API 规范见：[contracts/mesh-import-api.yaml](../specs/001-mesh-data-import/contracts/mesh-import-api.yaml)

### 领域事件
- `MeshImportStarted`：MeSH 导入任务启动
- `MeshImportCompleted`：MeSH 导入任务完成
- `MeshImportFailed`：MeSH 导入任务失败

## 📊 数据模型

### 核心数据表
- **MeSH 相关表**
  - `cat_mesh_descriptor`：主题词表（~35,000 条）
  - `cat_mesh_qualifier`：限定词表（~100 条）
  - `cat_mesh_tree_number`：树形编号表（~80,000 条）
  - `cat_mesh_entry_term`：入口术语表（~250,000 条）
  - `cat_mesh_concept`：概念表（~180,000 条）
  - `cat_publication_mesh`：文献-MeSH 关联表

- **导入管理表**
  - `cat_mesh_import_task`：导入任务表
  - `cat_mesh_table_progress`：表进度记录表
  - `cat_mesh_batch_detail`：批次详情表

详细数据模型见：[data-model.md](../specs/001-mesh-data-import/data-model.md)

## 🧪 测试覆盖

| 层级 | 测试类型 | 覆盖率目标 | 当前覆盖率 |
|------|---------|-----------|-----------|
| Domain | 单元测试 | ≥80% | [待测试运行后更新] |
| Application | 单元测试 | ≥70% | [待测试运行后更新] |
| Infrastructure | 单元+集成测试 | ≥70% | [待测试运行后更新] |
| Adapter | 单元+切片测试 | ≥70% | [待测试运行后更新] |
| Boot | E2E 测试 | 核心流程 | [待测试运行后更新] |

## 📝 变更日志

### v0.2.0 (2025-11-20)
- 新增：MeSH 数据首次导入功能
- 新增：批量导入断点续传支持
- 新增：导入进度实时监控
- 新增：XXL-Job 任务调度集成

### v0.1.0 (2025-01-01)
- 初始版本
- 基础目录管理功能
- 文献、作者、机构、期刊 CRUD

## 🚀 快速开始

参见：[quickstart.md](../specs/001-mesh-data-import/quickstart.md)

## 📖 相关文档

- [项目架构宪章](../.specify/memory/constitution.md)
- [MeSH 导入功能规格](../specs/001-mesh-data-import/spec.md)
- [MeSH 导入实施计划](../specs/001-mesh-data-import/plan.md)