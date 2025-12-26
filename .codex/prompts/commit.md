---
description: 智能分析本地变更并生成规范的 Git 提交（可拆分多个提交，禁止推送）
argument-hint: [空]
---

你将帮助我把当前仓库的本地变更整理成一个或多个高质量提交。**绝对禁止执行 `git push`**；提交前必须先输出提交计划并征求确认。

## 0. 收集 Git 变更上下文（先执行命令）

依次运行并基于输出继续：
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git diff --cached --stat`
- `git diff HEAD`

如果没有任何变更（未暂存 + 已暂存均为空），直接提示“无变更可提交”并结束。

## 1. 目标与原则

目标：分析所有未提交变更（含未暂存与已暂存），按逻辑单元拆分为多个提交，并为每个提交生成规范提交消息（Conventional Commits）。

原则：
- 小步提交：每个提交只包含一个逻辑变更单元
- 关联性分组：同一功能/修复/重构的文件归为同一提交
- 提交消息：`type(scope): 中文简短描述`（type 必须英文，描述中文）
- 禁止推送：仅本地提交
- 禁止在用户未确认前执行任何 `git add`/`git commit`

## 2. Conventional Commits 规则

格式：

```
<type>(<scope>): <简短描述>

<详细说明（可选）>
```

Type（英文）建议：`feat`/`fix`/`refactor`/`docs`/`test`/`style`/`chore`/`perf`/`ci`/`build`

Scope（英文）建议（任选其一）：
- 微服务：`ingest`/`registry`/`catalog`/`gateway`
- 分层：`domain`/`app`/`infra`/`adapter`/`api`
- Starter：`starter-web`/`starter-jpa`/`starter-rest-client` 等
- Common：`common`
- 领域：`auth`/`batch`/`storage`/`provenance` 等

简短描述要求：
- 中文，不超过 50 字
- 动词开头：添加/修复/优化/重构/更新/移除
- 不加句号

## 3. 生成提交计划（必须先输出）

请根据 `git diff` 内容与文件路径，生成“提交计划”，形式如下：

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
提交计划
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

共检测到 N 个变更文件，建议拆分为 M 个提交：

提交 1/M
   message: feat(scope): ...
   files:
   - path/to/file1 (新增/修改/删除)
   - ...

提交 2/M
   message: fix(scope): ...
   files:
   - ...

是否按此计划执行提交？(Y/n/调整)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

如果变更关联性不清晰，先给出 2~3 种拆分方案并询问我选择哪一种（不要擅自提交）。

## 4. 执行提交（仅在我明确确认后）

在我回复“Y/确认”后，再按顺序执行：

1. 若存在已暂存文件但计划需要重新分组：先 `git reset HEAD` 取消暂存
2. 对每个提交：
   - `git add <files...>`
   - `git commit -m "<message>"`

提交过程中如遇到错误：
- 先停止，输出错误与下一步建议
- 不要尝试 `git push`

## 5. 输出结果

提交完成后输出：
- 新创建的提交列表（hash + message）
- 统计信息（新增/修改/删除文件数量）
- 提醒：如需推送请我手动执行 `git push`
