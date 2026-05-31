---
name: executing-plans
description: 当你有一份书面实现计划需要在单独的会话中执行，并设有审查检查点时使用
---

# 执行计划

## 概述

加载计划，批判性审查，执行所有任务，完成后报告。

**开始时宣布：** "我正在使用 executing-plans 技能来实现此计划。"

**注意：** 告诉你的人类伙伴，patra 在有子代理支持时效果好得多。如果在支持子代理的平台上运行（如 Claude Code 或 Codex），其工作质量会显著提高。如果子代理可用，请使用 patra:subagent-driven-development 而非此技能。

## 进度追踪机制

writing-plans 写出的 plan 是 HTML 文件，每个 `<article class="task">` 与 `<li class="step">` 都带 `data-status` 属性。**唯一**的进度追踪方式是修改这个属性——不使用 TodoWrite，避免双源真相漂移。

| 状态 | 时机 |
|---|---|
| `pending` | 任务尚未开始（plan 初始状态） |
| `in-progress` | 开始做某任务或步骤 |
| `done` | 任务或步骤完成 |
| `blocked` | 遇到阻塞，需要外部澄清 / 解决 |
| `skipped` | 任务因前置变更不再需要 |

**小贴士：** plan 是 HTML，**在浏览器中打开** plan 文件可实时看到任务卡片的进度可视化（绿/灰/蓝/红边框 + ○/▸/●/×/⊘ 符号），强烈建议执行时开着浏览器对照。

**TOC 同步（重要）：** 左栏 `<ol class="toc-tasks">` 里每条 `<li data-task-status="…">` 与中栏 `<article id="task-N" data-status="…">` **一一对应**。每次改 article 的 `data-status` 都必须同步改对应 toc-tasks li 的 `data-task-status`，否则左栏目录的状态符号会与中栏卡片不一致——浏览器视图会误导你（左栏看到的是过期状态）。两处改完后可 grep 一遍校验。

## Implementation Notes 维护机制

plan 模板右栏 `<aside class="notes-panel" id="notes">` 是**实施期的偏离日志**——记录"plan 没规定、但实施时不得不做的决定 / 更改 / 权衡，以及任何用户应知事项"。这是给人类伙伴看的，**也是给未来回看这份 plan 的你看的**——半年后没人记得当时为什么 V12 改成 V13。

### 四类 entry

| `data-type` | 何时用 | 例 |
|---|---|---|
| `decision` | plan 留白处你做了选择 | "plan 未指定缓存 key 前缀，采用 `patra:{service}:{entity}`" |
| `change` | 偏离了 plan 的明文要求 | "plan 要求 Flyway V12，但本地已存在 V12，改为 V13" |
| `tradeoff` | 知情让步，未来可能要还的债 | "Repository 暂未抽 interface，因当前只一个实现；若加第二个实现需补抽" |
| `other` | 阻塞原因 / 环境怪事 / 给用户的备忘 | "Nacos 本地 8848 端口被占，临时换 8849" |

### 何时追加

- **即时追加，不要等任务结束**——遇到时立即用 Edit 改 plan HTML。等任务收尾再回忆，细节就丢了。
- **阻塞**：除了把 article `data-status` 改成 `"blocked"`，必须同时追加一条 `other` 或 `tradeoff` entry 说明阻塞原因和建议。
- **任务完成前最后自审**：标 `data-status="done"` 之前过一遍——本任务中所有"plan 没说我做了"的事，是否都已追加？没追加的现在补。

### 怎么追加

打开 plan HTML，找到 `<ol class="notes-log">` 内 `<!-- ENTRY TEMPLATE … -->` 注释，**复制注释里的 `<li class="note">` 块**到 `<ol>` 内（注释保留原位供后续复制），填好字段：

```html
<li class="note" data-type="change" data-time="2026-05-20T14:30">
  <header>
    <span class="note-type">CHANGE</span>
    <time datetime="2026-05-20T14:30">05-20 14:30</time>
    <a href="#task-3">§ Task 3</a>
  </header>
  <p>plan 要求 Flyway V12，但本地已存在 V12（来自另一分支），改为 V13。理由：避免编号冲突，不影响业务语义。</p>
</li>
```

追加后：
1. 同步更新 `<footer class="notes-stats">` 里对应的 `<span data-counter="…">` 数字（`decision` / `change` / `tradeoff` / `other` 四个之一 +1）
2. 若 `<ol class="notes-log">` 第一次有内容，删除其下方的 `<p class="empty-state">no notes yet</p>`

## 流程

### 步骤 1：加载并审查计划

1. 读取计划文件（路径形如 `<git-root>/docs/patra/plans/YYYY-MM-DD-<feature>.html`）
2. 批判性审查——识别计划中的任何问题或疑虑
3. 如果有疑虑：在开始之前向你的人类伙伴提出
4. 如果没有疑虑：直接进入步骤 2 开始执行

**审查时重点检查：**
- 步骤之间是否有依赖遗漏？（A 依赖 B，但 B 排在 A 之后）
- 验证条件是否明确？（"确认可用"不算，"运行 `./gradlew :module:test` 全部通过"才算）
- 是否有隐含的环境假设？（JDK 版本、数据库连接、Nacos / MinIO / 外部服务）
- HTML 结构完整性：每个 `<article>` 有唯一 `id` 与初始 `data-status="pending"`？

**审查示例：**
```
计划文件：<git-root>/docs/patra/plans/2026-05-19-add-user-validator.html
任务清单：5 个任务

审查发现：
- 任务 3（添加 Flyway 迁移）应在任务 2（编写 JPA Entity）之后，顺序正确 ✓
- 任务 4 的验证条件写的是"确认功能正常"→ 需澄清：具体跑什么测试？
- 计划未提及 JDK 版本要求 → 已确认 Java 25（patra-api 默认 JDK）

向伙伴提出：
"计划整体可执行。有一个问题：任务 4 的验证条件不够具体，建议改为
'运行 ./gradlew :patra-registry-app:test --tests *UserValidator* 全部通过'。"
```

### 步骤 2：执行任务

对于每个任务：

1. **标记为进行中** — 编辑 plan HTML：`<article id="task-N" data-status="pending">` → `data-status="in-progress"`；**同步**改左栏 `<ol class="toc-tasks">` 内 `<li data-task-status="pending">` → `"in-progress"`
2. **理解目标** — 重读任务描述，明确完成标准
3. **执行实现** — 严格按计划的 5 步 TDD 循环执行（写测试 → 跑测试失败 → 写实现 → 跑测试通过 → commit）。每开始一个步骤把对应 `<li class="step" data-status="pending">` 改为 `"in-progress"`，做完改 `"done"`。**实施中若遇到 plan 未规定的决定 / 必要更改 / 不可避免的权衡，立即按上方"Implementation Notes 维护机制"追加 entry，不要等。**
4. **运行验证** — 按要求运行测试或检查
5. **偏离自审** — 标 done 前最后扫一遍：本任务中所有"plan 没说我做了"的事，是否都在 plan HTML 右栏 notes-panel 有对应 entry？没追加的现在补；确认对应 `notes-stats` counter 数字与实际 entry 数一致。
6. **提交变更** — 每完成一个任务提交一次，commit message 引用任务编号；**plan HTML 的状态切换与 notes 追加一并 commit**（plan 是单源真相）
7. **标记为已完成** — 编辑 plan HTML：article 的 `data-status` 改为 `"done"`，确认 5 个 `<li class="step">` 都是 `"done"`；**同步**改左栏对应 `<li data-task-status="…">` → `"done"`

**每个任务的节奏：**
```
--- 任务 2/5：添加用户输入验证器 ---
[标记进行中]
编辑 plan HTML：<article id="task-2" data-status="pending"> → data-status="in-progress"

目标：在 patra-registry-app 中为 UserCommand 添加输入验证器
完成标准：所有验证测试通过，无效输入抛出 ValidationException

[实现]
- 添加 UserValidator (src/main/java/com/patra/registry/app/validator/UserValidator.java)
- 编写 3 个验证规则（email 格式、密码强度、用户名长度）

[验证]
$ ./gradlew :patra-registry-app:test --tests "*UserValidator*"
  > Task :patra-registry-app:test
  UserValidatorTest > should_reject_invalid_email PASSED
  UserValidatorTest > should_reject_weak_password PASSED
  UserValidatorTest > should_reject_long_username PASSED
  3 tests completed, 0 failed

[提交]
$ git add patra-registry/patra-registry-app/src/main/java/com/patra/registry/app/validator/UserValidator.java \
          patra-registry/patra-registry-app/src/test/java/com/patra/registry/app/validator/UserValidatorTest.java
$ git commit -m "feat(registry): 添加用户输入验证器（任务 2/5）"

[标记完成]
编辑 plan HTML：<article id="task-2" data-status="in-progress"> → data-status="done"
所有 5 个 <li class="step" data-status="done">
--- 任务 2/5 完成 ---
```

**批量审查检查点：**
- 每完成 3 个任务后，暂停回顾：整体方向还对吗？有没有偏离计划？
- 如果发现前面的实现有问题，先修复再继续，不要带着问题往下走

### 步骤 3：处理常见异常

**测试失败：**
1. 读错误信息，定位失败原因
2. 区分：是实现 bug？还是测试本身有问题？还是计划描述有误？
3. 实现 bug → 修复并重跑
4. 测试有问题 → 修复测试，向伙伴说明
5. 计划有误 → 停下来，向伙伴报告并建议修正

**依赖缺失：**
```
任务 3 需要 Redis 连接，但计划中没有提及 Redis 配置。
→ 编辑 plan HTML：<article id="task-3"> 的 data-status="in-progress" 改为 data-status="blocked"
→ 同步改左栏 <li data-task-status="in-progress"> → "blocked"
→ 在右栏 notes-panel 追加一条 data-type="other" entry，描述阻塞原因 + 建议
  （示例 <p>：任务 3 需 Redis 连接，plan 未包含配置；建议在任务 3 前插入"配置 Redis 连接"步骤）
→ 同步 notes-stats 的 other counter +1
→ 停止执行
→ 向伙伴报告
```

**指令不清：**
- 不要猜测意图，不要"合理推断"
- 列出你的理解和困惑，让伙伴澄清
- 等待回复后再继续

### 步骤 4：完成开发

所有任务完成并验证后：
- 宣布："我正在使用 finishing-a-development-branch 技能来完成此工作。"
- **必需子技能：** 使用 patra:finishing-a-development-branch
- 按照该技能的指引验证测试、展示选项、执行选择

**完成报告模板：**
```
## 执行报告

**计划：** <git-root>/docs/patra/plans/2026-05-19-add-user-validator.html
**分支：** feat/registry-user-validator
**任务：** 5/5 已完成

### 完成的任务
1. ✅ 初始化 UserCommand DTO
2. ✅ 添加 UserValidator 验证器
3. ✅ 添加 Flyway 迁移脚本
4. ✅ 实现 REST 端点
5. ✅ 添加集成测试

### 验证结果
- ./gradlew :patra-registry-app:test 全部通过（23/23）
- ./gradlew check 全部通过（含 Checkstyle / Spotless / 集成测试）
- HTML 进度面板：所有 5 个 article data-status="done"

### 偏离计划的地方
详见 plan HTML 右栏 notes-panel（共 N 条 entries：X decisions / Y changes / Z tradeoffs / W others）。
摘要示例：任务 3 - Flyway 编号从 V12 改为 V13（CHANGE，避开本地已存在的 V12）。

### 下一步
按 patra:finishing-a-development-branch 技能处理合并 / PR
```

## 何时停下来求助

**在以下情况立即停止执行：**
- 遇到阻塞（缺少依赖、测试失败、指令不清）
- 计划有严重缺陷导致无法开始
- 你不理解某条指令
- 验证反复失败（同一测试失败 2 次以上）

**不确定时就问，不要猜测。**

## 何时回到之前的步骤

**回到审查（步骤 1）当：**
- 伙伴根据你的反馈更新了计划
- 根本性的方案需要重新考虑

**不要硬闯阻塞** — 停下来问。

## 注意事项
- 先批判性审查计划
- 严格按照计划步骤执行
- 不要跳过验证
- 每个任务单独提交，commit message 引用任务编号
- 计划要求时引用相应技能
- 遇到阻塞时停下来，不要猜测
- 未经用户明确同意，绝不在 main/master 分支上开始实现
- 进度追踪**只用** plan HTML 的 `data-status`，不使用 TodoWrite（避免双源真相漂移）
- 偏离记录**即时**追加到 plan HTML 右栏 notes-panel，遇到决定 / 更改 / 权衡当下就 Edit，不要等任务结束补写

## 集成

**必需的工作流技能：**
- **patra:using-git-worktrees** - 必需：开始前建立隔离的工作空间
- **patra:writing-plans** - 创建此技能要执行的 HTML 计划
- **patra:finishing-a-development-branch** - 所有任务完成后收尾开发
