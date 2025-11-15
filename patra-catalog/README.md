# patra-catalog

## 概述

**patra-catalog** 是 Patra 医学文献数据平台的文献目录服务,负责文献数据的存储、管理和查询。作为核心业务服务,它接收来自 patra-ingest 的采集数据,并为其他服务提供文献信息查询能力。

**当前状态**: 🚧 **规划中** - 核心业务逻辑和数据持久化层尚在开发中。

**架构决策变更（2025-01-16）**：
- ✅ 已移除服务特定的 DTO（LiteratureDTO, AuthorDTO, JournalDTO）
- ✅ 统一使用共享内核模型 `patra-common-model.CanonicalLiterature`
- ✅ 简化跨服务数据传输，避免重复的模型转换

## 核心职责

- **文献数据存储**: 持久化采集的文献元数据（使用共享内核模型）
- **数据管理**: 提供文献信息的增删改查操作
- **查询服务**: 为其他微服务提供文献信息检索接口（规划中）

## 模块结构

```
patra-catalog/
├── patra-catalog-api/              # API 契约层（占位模块）
│   └── pom.xml                     # 依赖声明（无 Java 代码）
├── patra-catalog-domain/           # 领域层（待实现）
├── patra-catalog-app/              # 应用层（待实现）
├── patra-catalog-infra/            # 基础设施层（待实现）
├── patra-catalog-adapter/          # 适配器层（待实现）
├── patra-catalog-boot/             # 启动模块（待实现）
└── pom.xml
```

## 数据契约

### 共享内核模型

本服务不再定义独立的 DTO，而是直接使用 `patra-common-model` 中的共享内核模型：

#### CanonicalLiterature（标准文献模型）

**模块**: `com.patra:patra-common-model`

**核心字段**:
```java
public record CanonicalLiterature(
    Map<String, String> identifiers,     // 外部标识符（PMID, DOI, PMC）
    String title,                        // 文献标题
    String abstractText,                 // 摘要
    List<CanonicalAuthor> authors,       // 作者列表
    CanonicalJournal journal,            // 期刊信息
    LocalDate publicationDate,           // 发表日期
    List<String> keywords,               // 关键词/MeSH 术语
    String language,                     // 语言代码（ISO 639-1）
    List<String> publicationTypes        // 出版类型
)
```

**设计优势**:
- ✅ **单一事实来源**: 跨服务统一使用同一模型
- ✅ **零转换成本**: 消除服务边界的 DTO 转换
- ✅ **强类型约束**: 通过共享模型保证数据一致性
- ✅ **简化维护**: 模型演进只需修改一处

**关联模型**:
- `CanonicalAuthor`: 标准作者模型
- `CanonicalJournal`: 标准期刊模型
- `CanonicalAffiliation`: 标准机构模型

## 规划架构

patra-catalog 将采用**六边形架构 + DDD** 模式，包含以下层次：

### Domain 层（领域层）
- 纯 Java 实现，无框架依赖
- 定义文献、作者、期刊等核心领域实体
- 实现业务规则和领域逻辑

### Application 层（应用层）
- 编排领域服务完成业务用例
- 事务管理和服务编排

### Infrastructure 层（基础设施层）
- 数据持久化（MyBatis-Plus + MySQL）
- 仓储实现（Repository）
- 外部服务集成

### Adapter 层（适配器层）
- REST 控制器（提供 HTTP API）
- 事件监听器（接收 patra-ingest 的文献采集事件）

### Boot 层（启动模块）
- Spring Boot 启动类
- 依赖装配和配置

## 技术栈

| 组件 | 版本/说明 |
|------|----------|
| **Spring Boot** | 3.5.7 |
| **patra-common-core** | Patra 公共核心库 |
| **patra-common-model** | 共享内核模型（CanonicalLiterature） |
| **MyBatis-Plus** | 数据持久化（规划中） |
| **MySQL** | 数据存储（规划中） |

## 与其他服务的集成

### patra-ingest → patra-catalog
- **数据流向**: patra-ingest 采集文献数据后，通过 `CanonicalLiterature` 传递给 patra-catalog
- **集成方式**: 对象存储 + 元数据记录服务
- **存储格式**: JSON 序列化的 `CanonicalLiterature` 列表

### patra-catalog → 其他服务
- **查询接口**: 其他服务可通过 REST API 查询文献信息（规划中）
- **数据契约**: 统一使用 `CanonicalLiterature` 模型

## 开发路线图

- [x] ~~定义 API 契约层（LiteratureDTO, JournalDTO, AuthorDTO）~~（已废弃，迁移到共享内核）
- [x] 采用共享内核模型（CanonicalLiterature）
- [ ] 实现 Domain 层（文献、作者、期刊实体和领域服务）
- [ ] 实现 Infrastructure 层（数据库表设计和 MyBatis 仓储）
- [ ] 实现 Application 层（文献管理用例）
- [ ] 实现 Adapter 层（REST API 和事件监听器）
- [ ] 实现 Boot 启动模块并集成到网关路由
- [ ] 编写单元测试和集成测试

---

**当前状态**: 🚧 规划中，已完成共享内核模型迁移
**最后更新**: 2025-01-16
**版本**: 0.1.0-SNAPSHOT
