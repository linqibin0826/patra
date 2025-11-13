# 统一数据源端口设计方案

## 📋 文档索引

本目录包含 Patra Ingest 模块的统一数据源端口架构设计方案的完整文档。

### 核心文档

1. **[架构设计方案](./架构设计方案.md)** - 主文档
   - 问题背景和动机
   - 方案设计对比
   - 最终设计方案
   - 核心优势分析

2. **[代码实现指南](./代码实现指南.md)**
   - Domain 层接口和模型
   - Infrastructure 层适配器实现
   - Application 层使用示例
   - 完整代码清单

3. **[快速参考](./快速参考.md)**
   - 关键设计决策
   - 常见使用模式
   - 扩展指南

## 🎯 方案概述

### 核心理念

**统一务实架构 + 策略模式** - 将计划端口（PubmedSearchPort）和执行端口（DataSourcePort）统一为单一的 `DataSourcePort`，使用策略模式实现批次生成，符合开闭原则（OCP）。

### 关键特性

- ✅ **高内聚**：计划和执行在同一端口
- ✅ **类型安全**：使用继承体系处理数据源差异
- ✅ **务实实现**：直接使用 starter-provenance 组件
- ✅ **易于扩展**：新增数据源无需修改现有代码
- ✅ **符合 OCP**：使用策略模式替代 instanceof 判断

### 架构分层与职责边界

```
┌─────────────────────────────────────────────┐
│ Application 层（Ingest 业务逻辑）             │
│  - UnifiedBatchPlanner：批次生成逻辑         │
│  - GenericBatchExecutor：批次执行逻辑        │
└─────────────────┬───────────────────────────┘
                  │ 依赖（Ingest 特定接口）
┌─────────────────▼───────────────────────────┐
│ Domain 层（Ingest 端口定义）                  │
│  - DataSourcePort：接收 ExecutionContext     │
│  - PlanMetadata：通用元数据模型              │
└─────────────────▲───────────────────────────┘
                  │ 实现（参数转换）
┌─────────────────┴───────────────────────────┐
│ Infrastructure 层（协议适配）                 │
│  - DataSourceAdapter：提取通用参数           │
│    ExecutionContext → (query, params, config)│
└─────────────────┬───────────────────────────┘
                  │ 使用（通用参数调用）
┌─────────────────▼───────────────────────────┐
│ Framework 层（starter-provenance）            │
│  - DataSourceProvider：只接收通用参数         │
│    preparePlan(query, params, config)        │
│  - PubMedClient：低层 API 客户端             │
└─────────────────────────────────────────────┘
```

**关键职责边界**：
- **Framework 层**：提供通用的数据源访问能力（技术能力层）
- **Infrastructure 层**：参数提取和协议转换（适配层）
- **Domain 层**：定义 Ingest 特定的端口接口（接口层）
- **Application 层**：实现 Ingest 业务逻辑，如批次生成（业务层）

## 📊 与现有架构的关系

本方案基于并扩展了**多数据类型数据源架构**，主要变化：

| 维度 | 原架构 | 新架构 |
|------|--------|--------|
| 计划端口 | PubmedSearchPort（独立） | DataSourcePort（统一） |
| 执行端口 | DataSourcePort | DataSourcePort（统一） |
| 元数据模型 | PlanMetadata（单一类） | PlanMetadata（继承体系） |
| 适配器数量 | 2 个 | 1 个 |

## 🚀 快速开始

### 1. 查看架构设计

阅读 [架构设计方案.md](./架构设计方案.md) 了解完整的设计背景和决策过程。

### 2. 了解实现细节

查看 [代码实现指南.md](./代码实现指南.md) 获取具体的代码实现。

### 3. 参考快速指南

使用 [快速参考.md](./快速参考.md) 作为日常开发的速查手册。

## 📝 架构决策记录

**决策日期**：2025-11-13

**决策背景**：
- 当前存在 PubmedSearchPort（计划）和 DataSourcePort（执行）两个端口
- 职责割裂，增加复杂度
- 这是绿地项目，可以采用最优设计

**核心决策**：
1. **Framework 层定义 DataSourceProvider**：只接收通用参数 `(query, params, config)`
2. **统一为单一 DataSourcePort**：Ingest Domain 层添加 `preparePlan(ExecutionContext, DataType)` 方法
3. **使用 PlanMetadata 继承体系**：支持多数据源的类型安全扩展（放置在 patra-common-model）
4. **Infrastructure 层参数提取**：从 ExecutionContext 提取通用参数后调用 DataSourceProvider
5. **Application 层策略模式**：使用 BatchGenerationStrategy 替代 instanceof，符合 OCP
6. **Framework 层类型安全**：使用 TypeReference 而非 Class<T>，避免类型转换

**预期收益**：
- 减少端口数量，提高内聚性
- 统一抽象层次，简化架构
- 类型安全，编译时检查
- 易于维护和扩展
- 符合开闭原则，新增数据源无需修改现有代码
- 策略独立，降低维护成本和测试复杂度

## 📞 联系方式

如有疑问或建议，请联系：
- 架构负责人：Patra Lin
- 文档维护：Patra Architecture Team

---

**版本**：v1.0
**最后更新**：2025-11-13
**状态**：✅ 推荐采纳
