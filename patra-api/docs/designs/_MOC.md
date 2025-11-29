---
title: 设计文档
type: moc
updated: 2025-11-30
---

# 设计文档

> 设计文档记录功能/模块的架构设计，是 ADR 决策的具体落地方案。
>
> - **ADR**：记录"为什么选择这个方案"
> - **Design**：记录"这个功能/模块的架构是什么"

## 按状态

### 草稿

```dataview
TABLE module as "模块", date as "创建日期"
FROM "designs"
WHERE type = "design" AND status = "draft"
SORT date DESC
```

### 已完成

```dataview
TABLE module as "模块", date as "创建日期"
FROM "designs"
WHERE type = "design" AND status = "completed"
SORT date DESC
```

## 按模块

```dataview
TABLE status as "状态", date as "创建日期"
FROM "designs"
WHERE type = "design"
GROUP BY module
SORT module ASC
```

## 模块索引

| 模块 | 说明 |
|------|------|
| [[infrastructure/_MOC\|基础设施]] | 端口分配、网络拓扑、容器编排 |
| [[observability/_MOC\|可观测性]] | 监控、追踪、日志 |

## 关联 ADR

```dataview
TABLE related_adrs as "关联 ADR", status as "状态"
FROM "designs"
WHERE type = "design" AND length(related_adrs) > 0
SORT file.name ASC
```

## 模板

- [[templates/design|设计文档模板]]

## 参考资料

- [[decisions/_MOC|ADR 索引]] - 架构决策记录
