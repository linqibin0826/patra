---
type: design
status: draft
date: {{date:YYYY-MM-DD}}
module:
related_adrs: []
tags:
  - design
---

# {{title}}

## 概述

### 一句话描述

<!-- 用一句话概括这个设计要解决什么问题 -->

### 问题陈述

<!-- 描述当前的问题或需求背景 -->

### 目标

-

### 非目标

<!-- 明确边界，说明这个设计不解决什么问题 -->

-

---

## 架构设计

> [!tip] 本章节是核心，以图表为主

### 组件关系图

<!-- 使用 Mermaid 或 D2 绘制组件之间的关系 -->

```mermaid
%%{init: {
  'theme': 'base',
  'themeVariables': {
    'primaryColor': '#2d2d2d',
    'primaryTextColor': '#ffffff',
    'lineColor': '#888888',
    'edgeLabelBackground': '#333333'
  }
}}%%
flowchart TD
    subgraph Adapter["Adapter 层"]
        direction TB
        Controller[Controller]
    end

    subgraph Application["Application 层"]
        direction TB
        UseCase[UseCase]
    end

    subgraph Domain["Domain 层"]
        direction TB
        Entity[Entity]
        Repository[Repository Port]
    end

    subgraph Infrastructure["Infrastructure 层"]
        direction TB
        RepositoryAdapter[Repository Adapter]
        DB[(Database)]
    end

    Controller --> UseCase
    UseCase --> Entity
    UseCase --> Repository
    Repository -.-> RepositoryAdapter
    RepositoryAdapter --> DB

    classDef default fill:#2d2d2d,stroke:#555,color:#fff;
    classDef port fill:#445566,stroke:#555,color:#fff;
    class Repository port;
```

### 数据流图

<!-- 描述数据如何在系统中流动 -->

### 时序图

<!-- 如有复杂交互，绘制时序图（可选） -->

### 六边形架构视图

| 层级 | 组件 | 职责 |
|------|------|------|
| **Adapter** |  |  |
| **Application** |  |  |
| **Domain** |  |  |
| **Infrastructure** |  |  |

---

## 相关链接

- 关联 ADR：
- 关联设计：
