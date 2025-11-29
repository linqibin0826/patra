---
title: 基础设施设计
type: moc
updated: 2025-11-29
---

# 基础设施设计

> 基础设施设计文档涵盖项目的基础架构配置，包括端口分配、网络拓扑、容器编排等。

## 文档索引

| 文档 | 说明 | 状态 |
|------|------|------|
| [[01-port-allocation\|端口分配规范]] | 全局端口管理、网络拓扑图 | completed |

## 按状态

### 草稿

```dataview
TABLE date as "创建日期"
FROM "designs/infrastructure"
WHERE type = "design" AND status = "draft"
SORT date DESC
```

### 已完成

```dataview
TABLE date as "创建日期"
FROM "designs/infrastructure"
WHERE type = "design" AND status = "completed"
SORT date DESC
```

## 相关资源

- [[../observability/_MOC|可观测性设计]] - 监控、追踪、日志
- [[../../decisions/_MOC|ADR 索引]] - 架构决策记录
