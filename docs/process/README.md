# 业务流程文档索引

> Papertrace 核心业务流程与数据流文档

---

## 📄 文档列表

### 采集流程（Ingest）
- **[采集数据流](./ingest-dataflow.md)** - 完整的采集任务执行流程（调度 → 任务创建 → 数据抓取 → 存储）
- 包含序列图、状态机、时序说明

### 事件驱动（Event-Driven）
- **[Outbox 发布流程](./outbox-publishing.md)** - 事件发布的可靠性保证机制（本地事务 + Outbox 表 + 定时轮询）
- 包含 Outbox 表结构、发布流程、失败重试策略

### 配置管理（Registry）
- **[Registry 配置生命周期](./registry-config-lifecycle.md)** - 配置从创建到生效的完整生命周期
- 包含配置版本控制、灰度切换、时间有效性

---

## 🔗 相关文档

### 系统架构
- [系统架构总览](../overview/architecture.md)
- [C4 容器架构图](../overview/architecture.md#1-c4-container-架构图系统总览)

### 微服务架构
- [patra-ingest 六边形架构](../modules/ingest/architecture-diagram.md)
- [patra-registry 六边形架构](../modules/registry/architecture-diagram.md)

### 数据模型
- [核心数据模型 ER 图](../database/er-diagrams.md)

---

## 📝 贡献指南

### 添加新流程文档
1. 使用 Mermaid 绘制流程图（sequence/flowchart/stateDiagram）
2. 遵循命名规范：`{module}-{flow-name}.md`（如：`ingest-batch-processing.md`）
3. 包含以下章节：
   - 流程概述
   - 参与者/系统
   - 详细步骤
   - 异常场景
   - Mermaid 图表
   - 相关文档链接

### 文档模板
```markdown
# {流程名称}

> 简要说明流程的目的和范围

## 1. 流程概述
- **触发条件**：什么情况下触发
- **参与者**：涉及的系统/服务/用户
- **输入**：流程的输入数据
- **输出**：流程的输出结果

## 2. 详细步骤
[步骤1]
[步骤2]
...

## 3. 流程图
```mermaid
sequenceDiagram
    ...
```

## 4. 异常场景
- 场景1：失败处理
- 场景2：超时处理
- ...

## 5. 相关文档
- 链接到相关的模块文档、API 文档等
```

---

## 📊 流程图类型说明

### Sequence Diagram（时序图）
- **用途**：展示系统间交互的时序关系
- **示例**：采集任务执行流程、RPC 调用链路

### Flowchart（流程图）
- **用途**：展示业务逻辑的分支和决策
- **示例**：配置生效流程、错误处理流程

### State Diagram（状态图）
- **用途**：展示实体的状态转换
- **示例**：任务状态机、Outbox 状态机

---

**更新记录**

| 版本 | 日期 | 变更说明 | 作者 |
|-----|------|---------|------|
| 1.0 | 2025-10-08 | 初始版本：业务流程文档索引 | docs-engineer |

---

**许可证**

Copyright © 2025 Papertrace
