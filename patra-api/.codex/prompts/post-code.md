---
description: 代码变更后的完整质量检查（测试编译/架构测试/代码审查/测试审查/文档一致性）
argument-hint: [模块名 | committed | uncommitted | 空]
---

你将对本仓库的代码变更执行一次“提交前质量门”检查。**必须严格按阶段顺序执行**：先确定范围 → 再编译/架构测试 → 通过后再做三类审查并汇总结论。

## 0. 参数与范围（必须先做）

用户传入参数：`$ARGUMENTS`

按优先级解析：
1. 空：检查所有本地变更（未提交 + 已提交未推送）
2. `committed` / `已提交未推送`：只检查已提交但未推送的变更
3. `uncommitted` / `未提交`：只检查工作区未提交的变更
4. 其他：视为模块名（如 `patra-spring-boot-starter-web`、`patra-ingest`），只检查该模块相关变更

输出一行结论：
- 审查范围：`...`

## 1. 收集 Git 变更上下文（必须执行命令）

请依次运行并基于输出继续：
- `git status`
- `git diff HEAD --stat`
- `git log origin/main..HEAD --oneline`（若不存在 `origin/main`，尝试 `origin/master`；若仍不存在则提示用户给出基准分支）

然后根据第 0 阶段解析结果，生成“变更文件清单（去重）”：
- 空：合并（未提交文件 + 已提交未推送文件）
- committed：使用 `git diff origin/main..HEAD --name-only`
- uncommitted：使用 `git diff HEAD --name-only`
- 模块名：
  - 如果参数是顶层模块名（如 `patra-ingest`），使用 `git diff origin/main...HEAD -- patra-ingest`
  - 如果参数是具体模块（如 `patra-spring-boot-starter-web`），使用 `git diff origin/main...HEAD -- patra-spring-boot-starter-web`
  - 并额外合并未提交的同路径变更（`git diff HEAD --name-only -- <path>`）

把最终文件清单按模块分组输出（仅列出路径，不要贴代码）。

## 2. 第一阶段：测试编译 + 架构测试（质量门槛）

### 2.1 识别需要编译的 Gradle 模块

根据"变更文件清单"识别模块列表：
- 对每个变更文件取顶层目录名 `X`（如 `patra-ingest`、`patra-spring-boot-starter-web`）
- 若存在目录 `X/X-boot`（例如 `patra-ingest/patra-ingest-boot`），则编译目标为 `:X:X-boot`
- 否则编译目标为 `:X`
- 若用户参数是模块名：优先以参数解析出的目标为准（`patra-ingest` → `:patra-ingest:patra-ingest-boot`；`patra-spring-boot-starter-web` → `:patra-spring-boot-starter-web`）

输出一行：
- 涉及模块：`:m1,:m2,...`

### 2.2 执行测试编译

使用 Gradle Wrapper：

- `./gradlew :m1:testClasses :m2:testClasses`

若编译失败：
- 汇总关键错误（不要刷屏），指出失败模块
- 询问是否需要我先修复编译错误，再继续后续审查
- **不要进入第二阶段**

### 2.3 执行架构测试（ArchUnit）

编译通过后，执行：

- `./gradlew :m1:test :m2:test --tests "*ArchTest" --tests "*ArchitectureTest"`

若失败：
- 汇总违规信息与可能原因
- 询问是否需要我修复架构违规，再继续后续审查
- **不要进入第二阶段**

## 3. 第二阶段：三类审查（编译与架构测试通过后才能做）

Codex 不支持 Claude 那种 subagent 并行调用；这里改为同一轮里按顺序完成三类审查，并分别输出报告，然后给综合结论。

### 3.1 代码质量与架构合规性审查

使用 `$code-reviewer`，审查范围为第 1 阶段确定的“变更文件清单”。

### 3.2 测试质量检查

使用 `$test-checker`，重点检查：
- 新增/修改的测试是否满足分层与命名规则
- 对关键业务变更是否有回归保护

### 3.3 文档一致性检查（只检查不修改）

使用 `$doc-checker`，重点检查：
- 服务 README 是否需要更新
- 是否需要新增/更新 ADR（`../Patra-docs/content/decisions/`）

## 4. 最终汇总报告（必须输出）

请输出一份综合报告，包含：
- 审查范围（参数解析结果）
- 涉及模块列表
- 测试编译/架构测试结论
- 三类审查的关键发现摘要（每类最多 5 条）
- 总体评估：`可以提交 / 需要改进 / 存在严重问题`

## 使用示例

- `/prompts:post-code`
- `/prompts:post-code committed`
- `/prompts:post-code uncommitted`
- `/prompts:post-code patra-spring-boot-starter-web`
