# Patra 项目文档索引

> **Patra 医学文献数据平台** - 架构设计文档
> **更新日期**: 2025-11-12

---

## 📚 文档导航

### 🎯 核心架构文档

| 文档 | 大小 | 行数 | 描述 | 适用人员 |
|------|------|------|------|----------|
| [多数据类型数据源架构设计方案](./多数据类型数据源架构设计方案.md) | 59KB | 1,676 | **主设计文档**：核心设计理念、类型标记机制、策略模式、端口适配器 | 架构师、技术负责人 |
| [多数据类型数据源架构设计方案-实现指南](./多数据类型数据源架构设计方案-实现指南.md) | 31KB | 1,062 | **实现指南**：完整调用流程、领域建模、实现示例（PubMed、DOAJ） | 开发工程师 |
| [多数据类型数据源架构-快速参考](./多数据类型数据源架构-快速参考.md) | 19KB | 769 | **快速参考卡**：30秒上手、代码模板、故障排查、最佳实践 | 所有开发人员 |
| [数据源端口与提供者架构设计](./数据源端口与提供者架构设计.md) | 60KB | 1,707 | **旧版设计文档**（参考）：单一数据类型架构 | 参考文档 |

---

## 🚀 快速开始

### 第一次阅读？推荐路径

```
1️⃣ 快速参考卡 (10 分钟)
   └─> 了解架构概念和快速上手

2️⃣ 实现指南 (30 分钟)
   └─> 学习完整调用流程和实现示例

3️⃣ 设计方案 (60 分钟)
   └─> 深入理解设计理念和架构细节
```

### 按角色推荐

**👨‍💼 架构师 / 技术负责人**
1. [设计方案](./多数据类型数据源架构设计方案.md) - 完整设计理念
2. [实现指南](./多数据类型数据源架构设计方案-实现指南.md) - 领域建模部分

**👨‍💻 开发工程师**
1. [快速参考卡](./多数据类型数据源架构-快速参考.md) - 快速上手
2. [实现指南](./多数据类型数据源架构设计方案-实现指南.md) - 实现示例
3. [设计方案](./多数据类型数据源架构设计方案.md) - 按需深入

**🆕 新人入职**
1. [快速参考卡](./多数据类型数据源架构-快速参考.md) - 30 秒快速上手
2. [实现指南 - 第1章](./多数据类型数据源架构设计方案-实现指南.md#1-完整调用流程) - 了解调用流程
3. [实现指南 - 第3章](./多数据类型数据源架构设计方案-实现指南.md#3-实现示例) - 参考代码示例

---

## 📖 文档内容概览

### [多数据类型数据源架构设计方案](./多数据类型数据源架构设计方案.md)

**核心内容**：
- ✅ 设计背景与目标
- ✅ 整体架构图（Application → Domain → Infrastructure → Framework）
- ✅ 核心设计理念（类型安全、策略模式、领域独立）
- ✅ 类型标记机制（`DataType` 枚举、`TypeReference` 设计）
- ✅ 策略模式设计（`DataProcessor<T>` 接口、`ProcessorRegistry`）
- ✅ 端口与适配器（`DataSourcePort`、`DataSourceAdapter`）
- ✅ 提供者与注册表（`DataSourceProvider`、二维索引 `ProviderRegistry`）

**关键亮点**：
- 🎯 类型安全：编译期 + 运行时双重保障
- 🎯 一对多支持：一个 Provider 管理多个 Processor
- 🎯 完全独立建模：数据类型无需强制继承
- 🎯 O(1) 查找：二维索引高性能

### [多数据类型数据源架构设计方案-实现指南](./多数据类型数据源架构设计方案-实现指南.md)

**核心内容**：
- ✅ 端到端时序图（14 步详细流程）
- ✅ 领域建模指南（`CanonicalEntity`、值对象设计）
- ✅ 完整实现示例：
  - PubMed 多类型 Provider（支持 LITERATURE、CITATION、AUTHOR）
  - PubMed 文献 Processor（ESearch → EFetch → Transform → Validate）
  - DOAJ 期刊 Provider
- ✅ 领域模型示例：
  - `CanonicalLiterature`（文献聚合根）
  - `Journal`（期刊聚合根）
  - `ProvenanceInfo`、`ISSN` 等值对象

**关键亮点**：
- 📊 Mermaid 时序图：清晰展示完整调用链
- 💼 真实代码示例：可直接参考使用
- 🏗️ DDD 最佳实践：聚合根、值对象、领域行为

### [多数据类型数据源架构-快速参考](./多数据类型数据源架构-快速参考.md)

**核心内容**：
- ✅ 30 秒快速上手（4 个步骤）
- ✅ 常见场景代码模板：
  - 新增数据类型（完整流程）
  - 新增数据源（完整流程）
  - Application 层调用示例
- ✅ 故障排查检查清单：
  - Provider 未找到
  - Processor 未找到
  - 类型不匹配
  - 数据转换失败
- ✅ 性能优化建议
- ✅ 最佳实践速查

**关键亮点**：
- ⚡ 快速：30 秒上手
- 📋 实用：代码模板即拿即用
- 🔍 全面：故障排查覆盖常见问题

---

## 🎯 核心概念速查

### 架构分层

```
Application Layer (应用层)
    ↓ 调用
Domain Layer (领域层 - 纯 Java)
    ├─ DataSourcePort (接口)
    ├─ DataType (枚举)
    └─ TypeReference (类型引用)
    ↓ 实现
Infrastructure Layer (基础设施层)
    ├─ DataSourceAdapter (适配器)
    └─ ProviderRegistry (注册表)
    ↓ 委托
Framework Layer (框架层)
    ├─ DataSourceProvider (提供者)
    └─ DataProcessor (处理器)
    ↓ HTTP 调用
External APIs (外部 API)
```

### 关键组件

| 组件 | 层次 | 职责 | 文件位置 |
|------|------|------|----------|
| `DataType` | Domain | 类型标记枚举 | [设计方案 §4.1](./多数据类型数据源架构设计方案.md#41-datatype-枚举设计) |
| `TypeReference<T>` | Domain | 保持泛型信息 | [设计方案 §4.2](./多数据类型数据源架构设计方案.md#42-typereference-设计) |
| `DataSourcePort` | Domain | 数据获取契约 | [设计方案 §6.1](./多数据类型数据源架构设计方案.md#61-datasourceport-接口设计) |
| `DataSourceAdapter` | Infrastructure | 桥接层 | [设计方案 §6.2](./多数据类型数据源架构设计方案.md#62-datasourceadapter-适配器实现) |
| `ProviderRegistry` | Infrastructure | 二维索引 | [设计方案 §7.2](./多数据类型数据源架构设计方案.md#72-providerregistry-二维索引) |
| `DataSourceProvider` | Framework | 数据源提供者 | [设计方案 §7.1](./多数据类型数据源架构设计方案.md#71-datasourceprovider-接口) |
| `DataProcessor<T>` | Framework | 策略处理器 | [设计方案 §5.1](./多数据类型数据源架构设计方案.md#51-dataprocessor-策略接口) |

### 数据类型支持

当前支持的数据类型（`DataType` 枚举）：

- 📄 **LITERATURE**: 文献数据（来源：PubMed, EPMC, Crossref）
- 📄 **LITERATURE_FULLTEXT**: 文献全文（来源：EPMC, PMC）
- 📚 **JOURNAL**: 期刊数据（来源：DOAJ, JCR）
- 📊 **JOURNAL_METRICS**: 期刊指标（来源：JCR, Scopus）
- 🔗 **CITATION**: 引用关系（来源：Crossref, OpenCitations）
- 📝 **REFERENCE**: 参考文献（来源：PubMed, EPMC）
- 💊 **DRUG**: 药品数据（来源：DrugBank, ChEMBL）
- ⚠️ **DRUG_INTERACTION**: 药品相互作用（来源：DrugBank）
- 👤 **AUTHOR**: 作者信息（来源：PubMed, ORCID）
- 🏢 **AFFILIATION**: 机构信息（来源：PubMed, ROR）

---

## 🔗 相关资源

### 代码示例位置

- **PubMed Provider**: `patra-spring-boot-starter-provenance-pubmed/src/main/java/.../PubmedDataSourceProvider.java`
- **DOAJ Provider**: `patra-spring-boot-starter-provenance-doaj/src/main/java/.../DoajDataSourceProvider.java`
- **Literature Processor**: `patra-spring-boot-starter-provenance-pubmed/src/main/java/.../processor/PubmedLiteratureProcessor.java`
- **DataSourceAdapter**: `patra-ingest-infra/src/main/java/.../adapter/DataSourceAdapter.java`
- **ProviderRegistry**: `patra-starter-provenance-common/src/main/java/.../registry/ProviderRegistry.java`

### 配置文件

- **Provenance 配置**: `application-provenance.yml`
- **数据源配置**: `patra.provenance.datasources.*`

### 测试用例

- **单元测试**: `*Test.java`
- **集成测试**: `*IntegrationTest.java`
- **ArchUnit 测试**: `ArchitectureTest.java`

---

## 📝 文档维护

### 修订记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|

### 文档贡献

如发现文档问题或需要补充内容，请：
1. 提交 Issue 说明问题
2. 或直接提交 PR 修改文档

### 联系方式

- **架构负责人**: Patra 架构团队
- **技术支持**: [在此填写联系方式]

---

## 🎓 学习路径建议

### 初级开发者（0-6 个月）

**第 1 周：快速上手**
- 阅读 [快速参考卡](./多数据类型数据源架构-快速参考.md)
- 完成"30 秒快速上手"示例
- 运行现有的 PubMed 采集任务

**第 2-3 周：理解流程**
- 阅读 [实现指南 - 完整调用流程](./多数据类型数据源架构设计方案-实现指南.md#1-完整调用流程)
- 调试跟踪一次完整的数据获取过程
- 理解 Provider、Processor、Adapter 的职责

**第 4-5 周：实践开发**
- 参考 [快速参考卡 - 场景 1](./多数据类型数据源架构-快速参考.md#场景-1-新增数据类型完整流程)
- 实现一个简单的新数据类型
- 编写单元测试

### 中级开发者（6-12 个月）

**深入理解架构**
- 完整阅读 [设计方案](./多数据类型数据源架构设计方案.md)
- 理解类型标记机制和策略模式的设计意图
- 学习领域建模最佳实践

**独立开发能力**
- 独立开发新的数据源 Provider
- 优化现有 Processor 的性能
- 处理复杂的数据转换和验证逻辑

### 高级开发者 / 架构师（12+ 个月）

**架构优化**
- 评估架构设计的合理性
- 提出改进建议
- 指导团队成员开发

**技术决策**
- 新数据源的技术选型
- 性能瓶颈分析和优化
- 架构规划

---

**最后更新**: 2025-11-12
**维护者**: Patra 架构团队
