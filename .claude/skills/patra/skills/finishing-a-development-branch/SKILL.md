---
name: finishing-a-development-branch
description: 当实现完成、所有测试通过、需要决定如何集成工作时使用——通过提供合并、PR 或清理等结构化选项来引导开发工作的收尾
---

# 完成开发分支

## 概述

通过提供清晰的选项并执行所选工作流来引导开发工作的收尾。

**核心原则：** 验证测试 → 验证 plan 与审查反馈 → 检测环境 → 展示选项 → 执行选择 → 清理。

**开始时宣布：** "我正在使用 patra:finishing-a-development-branch 技能来完成这项工作。"

## 流程

### 步骤 1：全栈测试门控

**在展示选项之前，跑全栈门控：**

```bash
./gradlew check
```

`check` 包含编译（`compileJava` + `compileTestJava`）、单元测试（`test`）、集成测试（`integrationTest`，TestContainers 起 PG 17）、convention plugin 架构约束（六边形纯净性）—— 一刀切验证。

**如果 `check` 失败：**

```
./gradlew check FAILED（<N> 个失败）。必须先修复才能继续：

[显示失败信息：哪个模块、哪个 task FAILED、哪个测试失败]

在 check 通过之前无法进行合并/PR。
```

停止。不要继续到步骤 2。

**如果 check 通过：** 继续步骤 1.5。

### 步骤 1.5：HTML plan 收尾验证

如果本次开发由 `patra:writing-plans` 出的 plan.html 驱动（绝大多数情况）：

```
打开 plan.html，确认：

1. 所有 <article class="task" data-status="done"> —— 每个任务都已切到 done
2. 任务内 <li class="step" data-status="done"> —— 每一步都完成
3. 右栏 <aside class="notes-panel"> —— notes-log 内 entry 已全部落到对应 task；
   <footer class="notes-stats"> 四个 counter 数字与实际 entry 数一致；
   若有 data-status="blocked" 的 article，确认对应 other entry 描述的阻塞已解除或转入 follow-up
```

如果发现遗漏的 `data-status="in-progress"` 或 `"pending"`，停下来。要么补完、要么显式切到 `"skipped"` 并写明跳过原因。**不允许带遗漏任务合并**——绿地项目禁止分阶段交付。

### 步骤 1.6：审查反馈处理验证

如果本次开发跑过 `patra:requesting-code-review`：

```
确认审查报告里的反馈状态：

- Critical 问题 —— 全部修复（这是合并前提）
- Important 问题 —— 全部修复（绿地不允许带未修 Important 合并）
- Minor 问题 —— 已修复 / 已记录到 follow-up / 已按 YAGNI 评估后跳过
```

如果还有未处理的 Critical 或 Important，停下来。先按 `patra:receiving-code-review` 协议处理完再继续。

### 步骤 2：检测环境

**在展示选项之前，先确定工作区状态：**

```bash
GIT_DIR=$(cd "$(git rev-parse --git-dir)" 2>/dev/null && pwd -P)
GIT_COMMON=$(cd "$(git rev-parse --git-common-dir)" 2>/dev/null && pwd -P)
```

这决定了展示哪种菜单、以及清理方式：

| 状态 | 菜单 | 清理 |
|------|------|------|
| `GIT_DIR == GIT_COMMON`（普通仓库） | 标准 4 个选项 | 无 worktree 可清理 |
| `GIT_DIR != GIT_COMMON`，命名分支 | 标准 4 个选项 | 按来源判断（见步骤 6） |
| `GIT_DIR != GIT_COMMON`，分离 HEAD | 收敛 3 个选项（无合并） | 无清理（由外部管理） |

### 步骤 3：确定基础分支

```bash
# 尝试常见的基础分支
git merge-base HEAD main 2>/dev/null || git merge-base HEAD master 2>/dev/null
```

或者询问："这个分支是从 main 分出来的——对吗？"

### 步骤 4：展示选项

**普通仓库和命名分支 worktree —— 准确展示以下 4 个选项：**

```
实现已完成。你想怎么做？

1. 在本地合并回 <base-branch>
2. 推送并创建 Pull Request
3. 保持分支现状（我稍后处理）
4. 丢弃这项工作

选哪个？
```

**分离 HEAD —— 准确展示以下 3 个选项：**

```
实现已完成。你在分离 HEAD 上（由外部管理的工作区）。

1. 作为新分支推送并创建 Pull Request
2. 保持现状（我稍后处理）
3. 丢弃这项工作

选哪个？
```

**不要添加解释** —— 保持选项简洁。

**Patra 单人项目立场提示：** 本地合并（选项 1）和走 PR（选项 2）都合法，但走 PR 能留 ultrareview / Codex 审查记录和清晰的 git history，推荐 PR；不强制。

### 步骤 5：执行选择

#### 选项 1：本地合并

```bash
# 切到主仓库根目录，保证 CWD 安全
MAIN_ROOT=$(git -C "$(git rev-parse --git-common-dir)/.." rev-parse --show-toplevel)
cd "$MAIN_ROOT"

# 先合并 —— 在删除任何东西之前先验证合并成功
git checkout <base-branch>
git pull --ff-only       # 防止意外 merge commit
git merge <feature-branch>

# 在合并结果上重跑全栈门控
./gradlew check

# check 通过之后再：清理 worktree（步骤 6），然后删除分支
```

然后：清理 worktree（步骤 6），再删除分支：

```bash
git branch -d <feature-branch>
```

#### 选项 2：推送并创建 PR

**绿地 YAGNI 最后一次扫描：** 推送前再过一遍 git diff，是否有"未来可能用得上"的占位代码、灵活性 hooks、deprecated 兼容层？有就删——绿地禁止预防性代码。

```bash
# 推送分支
git push -u origin <feature-branch>

# 创建 PR
gh pr create --title "<title>" --body "$(cat <<'EOF'
## 摘要
<2-3 条变更要点>

## 测试计划
- [ ] <验证步骤>
EOF
)"
```

**不要清理 worktree** —— 用户在 PR 反馈迭代时还需要它存活。

#### 选项 3：保持现状

报告："保留分支 <name>。工作树保留在 <path>。"

**不要清理工作树。**

#### 选项 4：丢弃

**先确认：**

```
这将永久删除：
- 分支 <name>
- 所有提交：<commit-list>
- 工作树 <path>

输入 'discard' 确认。
```

等待精确的确认。

确认后：

```bash
MAIN_ROOT=$(git -C "$(git rev-parse --git-common-dir)/.." rev-parse --show-toplevel)
cd "$MAIN_ROOT"
```

然后：清理 worktree（步骤 6），再强制删除分支：

```bash
git branch -D <feature-branch>
```

### 步骤 6：清理工作区

**只对选项 1 和 4 执行。** 选项 2 和 3 始终保留 worktree。

```bash
GIT_DIR=$(cd "$(git rev-parse --git-dir)" 2>/dev/null && pwd -P)
GIT_COMMON=$(cd "$(git rev-parse --git-common-dir)" 2>/dev/null && pwd -P)
WORKTREE_PATH=$(git rev-parse --show-toplevel)
```

**如果 `GIT_DIR == GIT_COMMON`：** 普通仓库，无 worktree 可清理。结束。

**如果 worktree 路径在 `.claude/worktrees/` 或 `worktrees/` 之下：** 这是我们（Claude Code / patra plugin 工作流）创建的 worktree —— 我们负责清理。

```bash
MAIN_ROOT=$(git -C "$(git rev-parse --git-common-dir)/.." rev-parse --show-toplevel)
cd "$MAIN_ROOT"
git worktree remove "$WORKTREE_PATH"
git worktree prune  # 自愈：清理任何过期的注册记录
```

**否则：** 这个工作区由宿主环境（harness）管理。**不要**移除它。如果你的平台提供了工作区退出工具，用它。否则原样保留工作区。

## 快速参考

| 选项 | 合并 | 推送 | 保留工作树 | 清理分支 |
|------|------|------|-----------|---------|
| 1. 本地合并 | ✓ | - | - | ✓ |
| 2. 创建 PR | - | ✓ | ✓ | - |
| 3. 保持现状 | - | - | ✓ | - |
| 4. 丢弃 | - | - | - | ✓（强制） |

## 常见错误

**跳过 ./gradlew check**

- **问题：** 合并损坏的代码、创建失败的 PR
- **修复：** 在提供选项前始终跑全栈门控

**绕过 plan / 审查 sanity check**

- **问题：** 带遗漏任务或未修 Critical/Important 反馈合并
- **修复：** 步骤 1.5 + 1.6 都通过才能进入步骤 2

**开放式问题**

- **问题：** "接下来该做什么？" → 含糊不清
- **修复：** 准确展示 4 个结构化选项（分离 HEAD 时是 3 个）

**为选项 2 清理 worktree**

- **问题：** 删掉用户 PR 迭代还需要的 worktree
- **修复：** 只在选项 1 和 4 时清理

**先删分支再删 worktree**

- **问题：** `git branch -d` 失败，因为 worktree 还引用着该分支
- **修复：** 先合并，再删 worktree，最后删分支

**在 worktree 内部跑 `git worktree remove`**

- **问题：** 当 CWD 在被删除的 worktree 内时，命令静默失败
- **修复：** 跑 `git worktree remove` 前先 `cd` 到主仓库根目录

**清理 harness 拥有的 worktree**

- **问题：** 移除 harness 创建的 worktree 会造成幻影状态
- **修复：** 只清理 `.claude/worktrees/` 或 `worktrees/` 下的 worktree

**丢弃时不确认**

- **问题：** 意外删除工作成果
- **修复：** 要求输入 'discard' 确认

**`git pull` 产生意外 merge commit**

- **问题：** 默认 `git pull` 可能 fetch + merge，污染线性历史
- **修复：** 用 `git pull --ff-only`

## 红线

**绝不：**

- 在 `./gradlew check` 失败时继续
- 合并前不在合并结果上重跑 `./gradlew check`
- 带遗漏 plan 任务或未修 Critical/Important 审查反馈继续推进
- 不确认就删除工作成果
- 未经明确请求就强制推送
- 在确认合并成功之前移除 worktree
- 清理不是你创建的 worktree（按来源判断）
- 在 worktree 内部跑 `git worktree remove`

**始终：**

- 在提供选项前跑 `./gradlew check`
- 在提供选项前完成 plan / 审查 sanity check
- 展示菜单前检测环境
- 准确展示 4 个选项（分离 HEAD 时是 3 个）
- 选项 4 要求输入确认
- 只在选项 1 和 4 时清理 worktree
- 移除 worktree 前 `cd` 到主仓库根目录
- 移除后跑 `git worktree prune`
- `git pull` 加 `--ff-only`

## 集成

**被以下技能调用：**

- **`patra:subagent-driven-development`**（步骤 7）- 所有任务完成后
- **`patra:executing-plans`**（步骤 5）- 所有批次完成后

**配合使用：**

- **`patra:using-git-worktrees`** - 清理由该技能创建的工作树
- **`patra:verification-before-completion`** - 步骤 1 的 `./gradlew check` 门控同源
- **`patra:requesting-code-review` / `patra:receiving-code-review`** - 步骤 1.6 的审查反馈 sanity check 同源
