---
type: devlog
period: daily
date: {{date}}
time_range: "{{time_start}} - {{time_end}}"
commits: {{commit_count}}
files_changed: {{files_count}}
lines_added: {{lines_added}}
lines_deleted: {{lines_deleted}}
modules: [{{modules}}]
linked_bugs: []
linked_tils: []
linked_adrs: []
tags:
  - record/devlog
  - period/daily
---

# {{date}} 开发日志

## 变更统计

| 指标 | 数值 |
|------|------|
| 提交数 | {{commit_count}} |
| 修改文件 | {{files_count}} |
| 新增行数 | +{{lines_added}} |
| 删除行数 | -{{lines_deleted}} |
| 涉及模块 | {{modules}} |

## 提交记录

<!-- 按模块分组的提交列表 -->

{{commits_by_module}}

## 今日完成

{{completed_summary}}

## 明日计划

> 以下是基于今日变更和未完成任务的建议，请确认或修改：

{{suggestions}}

## 关联

<!-- 使用 Wikilink 语法链接相关文档，示例：
- Bug: [[bugs/2025/11/BUG-001-xxx|BUG-001-xxx]]
- TIL: [[til/spring/2025-11-27-xxx|Spring 条件注解优先级]]
- ADR: [[decisions/ADR-001-xxx|ADR-001-xxx]]
-->

- Bug: {{linked_bugs}}
- TIL: {{linked_tils}}
- ADR: {{linked_adrs}}

## 备注

<!-- 可添加个人思考、遇到的问题、心得体会等 -->
