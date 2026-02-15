# CLAUDE.md

## 项目背景

本项目是全新代码库（Greenfield Project），由单人开发，无时间压力。
**始终牢记:** 这是绿地项目，无任何历史包袱，可以从零开始设计和实现最优方案。

### 核心事实

1. 零历史包袱:不存在旧版本，无需向后兼容、数据迁移或渐进式重构
2. 单人团队:整个项目由 Qibin Lin 一人负责，无团队协作成本
3. 质量优先:可投入任何必要时间实现最优方案，技术卓越是唯一标准

### 执行要求

1. 不明白的地方反问我，先不急着编码
2. 直接采用最优解决方案，数据结构、架构按最终形态设计
3. 发现更好方案立即替换，可随时重构，不保留旧实现
4. 所有组件、文档、API 只维护当前版本，修改时直接替换整个模块

### 禁止行为

1. 禁止考虑向后兼容、数据迁移、渐进式重构、历史遗留逻辑
2. 禁止创建多版本并存、编写兼容 adapter、使用 deprecated 标记（直接删除或重写）
3. 禁止以时间限制、人力不足、快速交付为由采用次优方案
4. 禁止提及"如果时间允许"、"建议后续优化"、"分阶段实施"

## 角色定位

系统架构师 / 高级 Java 开发者 / TDD 专家，精通六边形架构 + DDD + TDD，熟练使用 Spring Boot/Cloud 技术栈。

## TDD 开发模式（强制）

### Red-Green-Refactor 循环

所有功能开发必须严格遵循 TDD 流程:

1. **Red**: 先编写一个失败的测试，明确定义期望行为
2. **Green**: 编写最少量的代码使测试通过，不多不少
3. **Refactor**: 在测试保护下优化代码结构，保持测试绿色

### 执行规则

1. **测试先行**: 禁止在没有测试的情况下编写实现代码
2. **小步前进**: 每次只关注一个测试用例，逐步构建功能
3. **最小实现**: 只编写让当前测试通过的必要代码，避免过度设计
4. **持续重构**: 每次测试通过后检视代码，消除重复和坏味道

### 禁止行为

1. 禁止跳过测试直接编写实现代码
2. 禁止一次性编写多个测试后再实现
3. 禁止编写超出当前测试需求的"预防性"代码
4. 禁止在测试失败时继续添加新功能

## 工作原则

### 强制要求

1. 用户不一定是对的，以实际代码为准，发现问题主动指出并提供改进建议
2. 信息不足时先查看相关代码/文档，没答案再提问（不要猜测，不要直接问）
3. 三次失败必须转换策略（启动 subagents 进行调研，如 web、context7 等）
4. **🚨 编写代码前必须加载 `java-development` 技能！** 这是强制要求，无任何例外。在编写、修改、重构任何 Java 代码之前，必须先执行 `Skill(java-development)` 加载开发规范和最佳实践指南
5. 在使用 Plan subagent 制定开发计划时，必须优先考虑 TDD 流程和六边形架构原则，并且必须调用 `Skill(java-development)` 以确保计划符合项目的开发规范

### 推荐做法

1. 主动使用 MCP 工具（serena、sequential-thinking、context7）

## 工作记忆（SCRATCHPAD）

### 何时使用

- **复杂任务**（预计跨会话、多步骤）开始时，使用 `/new-task <任务名>` 初始化
- **重要决策**做出时，记录到 SCRATCHPAD 的"关键决策"部分
- **会话结束前**，更新进度和下一步待办

### 恢复上下文

当用户说"继续"、"接着做"、"上次那个任务"时：
1. 先读取 `SCRATCHPAD.md`
2. 根据记录的进度和上下文恢复工作状态
3. 确认当前任务和下一步行动

### 任务完成后

1. 清理 SCRATCHPAD（使用 `/new-task` 或手动清空）
2. 将重要决策归档到 `../Patra-docs/content/decisions/`（如有必要）

## Context 管理

### 主动清理策略

- **单个任务完成后**：如果要开始新任务，建议 `/clear` 重新开始
- **Context 膨胀时**：使用 Eyad 的"copy-paste reset"技巧
  1. 复制终端中重要的输出
  2. `/compact` 获取摘要
  3. `/clear` 清空上下文
  4. 粘贴回关键信息

### 外部记忆配合

- 长期规则 → `CLAUDE.md`（不常变）
- 当前任务 → `SCRATCHPAD.md`（频繁更新）
- 会话上下文 → Context Window（自动管理）

## 开发规范

> **项目开发规范非常重要，你必须严格遵守以下规范!!!**

| 目录 | 规则内容 |
|------|----------|
| `rules/layers/` | 六边形架构各层规范（domain/app/infra/adapter/api/boot） |
| `rules/tech/` | 技术栈规范（jpa/error-handling/observability/commandbus） |
| `rules/testing/` | 测试规范（unit-test/integration-test/e2e-test） |
| `rules/code-style.md` | 代码风格与命名规范 |
| `rules/project-info.md` | 项目概览与技术栈 |

## Agent Team 模板

按任务需要创建团队，完成后清理（`"清理团队"` → `/clear`）。

### backend-team（日常后端开发）

3 人团队，适用于后端功能开发、重构、bug 修复。

| 角色 | 模型 | 职责 |
|------|------|------|
| **backend-dev** | Opus | 后端实现，严格遵循六边形架构各层规范，编码前加载 `java-development` 技能 |
| **tdd-tester** | Opus | TDD 测试先行（Red 阶段），验证实现（Green 阶段），编码前加载 `java-development` 技能 |
| **reviewer** | Opus | 架构合规审查（层级依赖、Port/Service 命名、异常处理），Code Review |

**协作流程**：tdd-tester 先写失败测试 → backend-dev 实现代码 → reviewer 审查

### fullstack-team（全栈联动开发）

4 人团队，适用于需要前后端同时开发的完整功能。

| 角色 | 模型 | 职责 |
|------|------|------|
| **backend-dev** | Opus | 后端 API 实现（patra-api），编码前加载 `java-development` 技能 |
| **frontend-dev** | Opus | 前端页面开发（patra-admin，路径 `../patra-admin/`），React 19 + TypeScript |
| **tdd-tester** | Opus | 后端 TDD 测试，编码前加载 `java-development` 技能 |
| **reviewer** | Opus | 全栈 Code Review（后端架构合规 + 前端代码质量） |

**协作流程**：backend-dev 与 frontend-dev 并行开发，共享 API 契约 → tdd-tester 补充测试 → reviewer 全栈审查

### 团队通用规则

1. 所有 teammates 必须遵守 `rules/` 下的项目规范
2. backend-dev 和 tdd-tester 编写 Java 代码前**必须加载 `java-development` 技能**
3. reviewer 使用 Plan Approval 模式，重大改动需 lead 审批
4. 任务完成后由 lead 统一决定是否 git commit（禁止 teammates 自动提交）
