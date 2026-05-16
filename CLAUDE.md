# CLAUDE.md

## 项目背景

本项目是全新代码库（Greenfield Project），由单人开发，无时间压力。
**始终牢记:** 这是绿地项目，无任何历史包袱，可以从零开始设计和实现最优方案。

### 核心事实

1. 零历史包袱:不存在旧版本，无需向后兼容、数据迁移或渐进式重构
2. 单人团队:整个项目由 linqibin 一人负责，无团队协作成本
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

1. **🚨 编写代码前必须加载对应的 Patra 技能！** 这是强制要求，无任何例外。根据任务类型加载：`patra-hexagonal`（创建组件/架构决策）、`patra-jpa`（数据层实现）、`patra-events`（事件/Outbox）、`patra-troubleshooter`（异常堆栈/日志链路/报错排查）
2. 在使用 Plan subagent 制定开发计划时，必须优先考虑 TDD 流程和六边形架构原则，并加载 `patra-hexagonal` 确保计划符合项目架构规范

## Skill 加载优先级

Patra 项目使用**两层正交** skill 体系，叠加调用不冲突：

1. **流程层 superpowers** — 决定"怎么推进工作"：
   - 新功能 → `superpowers:brainstorming` 先行
   - 写代码 → `superpowers:test-driven-development` 全程
   - 调 bug → `superpowers:systematic-debugging` 四阶段
   - 完成任务 → `superpowers:requesting-code-review` + `superpowers:finishing-a-development-branch`

2. **领域层 patra-*** — 决定"代码写成什么样"：
   - 架构决策 / 创建组件 → `patra-hexagonal`
   - 数据持久化 → `patra-jpa`
   - 事件 / Outbox → `patra-events`
   - 异常排查 → `patra-troubleshooter`

**调用顺序**：流程层先行，领域层落地。示例"添加 Venue 导入功能"：

`brainstorming` → `writing-plans` → `test-driven-development(RED)` → `patra-hexagonal`（指导 Handler）→ `patra-jpa`（指导 Repository）→ `test-driven-development(GREEN)` → `requesting-code-review`

