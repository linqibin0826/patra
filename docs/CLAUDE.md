# docs 目录使用指南

本目录是项目的知识管理中心，使用 Obsidian 管理。

## 目录结构

| 目录 | 用途 | 触发时机 |
|------|------|----------|
| `devlog/daily/` | 每日开发日志 | 每天结束 |
| `devlog/weekly/` | 周报 | 每周结束 |
| `devlog/monthly/` | 月报 | 每月结束 |
| `bugs/{YYYY}/{MM}/` | Bug 记录 | 修复非平凡 Bug |
| `learning/{topic}/` | 详细学习材料 | 学完一个章节 |
| `til/{YYYY}/{MM}/` | 每日学习总结 | 一天学习结束 |
| `decisions/` | ADR 架构决策 | 做出架构决策 |
| `designs/{module}/` | 设计文档 | 开发复杂功能前 |
| `templates/` | 模板（只读） | — |

## 文件命名规范

**强制 kebab-case**：`spring-boot-startup.md`、`adr-001-microservice-split.md`、`bug-redis-connection-timeout.md`

**禁止**：Snake_case、自然语言空格、中文文件名

## 语法规范

- **Obsidian 原生语法**：包括内部链接、Callout 提示框、YAML 元数据和 Dataview 查询，详见 @.claude/memories/obsidian.md
- **Mermaid 图表**：用于绘制流程图、时序图、状态图和甘特图，详见 @.claude/memories/mermaid.md
- **Charts 插件**：用于绘制柱状图、折线图、饼图和雷达图等统计图表，详见 @.claude/memories/charts.md
- **D2 声明式图表**：用于绘制复杂架构图、ERD 数据库建模、UML 类图和嵌套容器拓扑，详见 @.claude/memories/d2.md

## 图表选型

| 需求 | 推荐工具 |
|------|----------|
| 简单流程/时序 | Mermaid |
| 统计数据可视化 | Charts |
| 嵌套容器/数据库建模 | D2 |

## 禁止行为

1. 禁止修改 `templates/` 目录下的文件
2. 禁止手动修改 `_MOC.md` 文件（Dataview 自动生成）
3. 禁止使用 ASCII 艺术字符（`┌─┐│└┘▼█▓░`）绘制图表
4. 禁止混用图表工具（同一张图不混合 Mermaid 和 D2）
5. 禁止硬编码颜色（使用 `classes`/`classDef` 集中管理样式）
