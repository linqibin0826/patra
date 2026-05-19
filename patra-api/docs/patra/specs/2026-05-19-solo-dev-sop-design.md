---
title: Patra Solo Dev SOP — 单人全链路开发流程设计
date: 2026-05-19
status: draft
authors: Lin
version: v0.1
---

# Patra Solo Dev SOP — 单人全链路开发流程设计

## 0. 元信息

- **设计日期**：2026-05-19
- **作者**：Lin（独立开发者）
- **适用项目**：Patra（医学出版物数据平台），可推广至其他单人开发的产品
- **目标读者**：作者本人（操作执行）+ Claude Code（消费此文档作为 SOP 指南）
- **演进方式**：随实践迭代，每完成 N 个 Issue 或遇 Review 回路 ≥ R2 时反思更新

**Changelog**
- v0.1 / 2026-05-19 / Lin / 初稿（PRD skill 独立化 + Sentry 移到可选）

---

## 1. 概览

### 1.1 设计目标

为单人完成"产品 → UI/UX → 前端 → 后端 → QA"全链路的开发流程提供一套清晰可执行的 SOP。核心解决四类痛点：

1. **角色切换累**：5 个角色都是自己，频繁切换上下文消耗精力
2. **优先级乱**：能做的事太多，每天不知道先动哪个
3. **质量没人把关**：自测靠不住，缺少独立 review 视角
4. **节奏失控 + 上下文易丢**：无外部督促易拖延；几天后回头看自己的代码/决策要重新理解

### 1.2 设计原则

- **AI 主导执行，人专注决策**：你是决策者 + 把关者，Claude 是执行层；介入点在"起点（需求）"和"终点（review）"
- **Linear 是单一信息源**：所有 feature 生命周期状态挂在 Linear，Claude 跨会话恢复上下文只看 Linear
- **事件驱动而非时间驱动**：节奏由 Issue 状态变化触发，不预设"周几做什么"
- **故意粗糙以聚焦**：PRD 阶段视觉故意粗糙（防完成感幻觉），UI 阶段直接 hi-fi（AI 时代低保真过时）
- **回路成本最小化**：默认串行顺序选择回路代价最低的（消费外部 API 项目 BE 先）

### 1.3 不在范围内的事

- 产品定位 / 市场策略 / 竞品分析的方法论（你是自用，不需要）
- 团队协作流程（团队只有你一个人）
- E2E 测试体系（成本不对称，先不上；Project 复杂后可补）
- 用户运营 / 增长流程（无用户阶段不需要）
- 持续部署管道（已有 GitHub Actions，本 SOP 不涉及）

### 1.4 整体架构（双层结构）

```
┌─────────────────────────────────────────────────────────┐
│  Project 层（产品维度）                                  │
│  Draft → Active → Done                                  │
│  · Entry Gate: 写完版本规划文档 + 派生 Issue 池          │
│  · 产物: docs/patra/release-specs/<version>.md     │
└──────────────────────┬──────────────────────────────────┘
                       │
                       │ Issue 池
                       ↓
┌─────────────────────────────────────────────────────────┐
│  Issue 层（工程维度）                                    │
│  每个 Issue 走一遍：                                      │
│  Tech Design → BE → Design → FE → Review → Done         │
│  · 产物: docs/patra/specs/<topic>-design.md +      │
│         GitHub PR(s) + Linear Sub-issues                │
└─────────────────────────────────────────────────────────┘
```

---

## 2. Project 层（产品维度）

### 2.1 状态机

```
[想法] → Draft → Active → Done
              ↑
        Entry Gate: 完成版本规划文档 + Issue 池派生
```

| 状态 | 含义 | 出口条件 |
|------|------|---------|
| Draft | 你在写版本规划文档 | Project Entry Gate 通过：① 5 字段全填 ② Issue 池已派生 |
| Active | 文档完成，Issue 池开始走 SOP | 所有 Issue 推到 Done |
| Done | 整个版本完成 | （可选）retro 沉淀到 Obsidian |

### 2.2 版本规划文档（5 字段）

**产物路径**：`docs/patra/release-specs/<version>.md`

**工具**：Claude Code + `patra:release-planning`（**待创建**的独立 PRD skill；详见 §9.2 决策 #6）

**5 字段模板**：

```markdown
# <Version> 版本规划

## 1. 版本目标 / 价值
1-2 句话描述这版的核心交付价值。
例："Patra 能让我搜 PubMed 文献并加入收藏。"

## 2. 功能列表 / 要做的事
bulleted list，每条 = 1 个用户故事 → 派生 1 个 Linear Issue。
- 用户能输入关键词搜 PubMed → 看到文献列表（标题、作者、年份）
- 用户能点开文献看详情
- 用户能给文献加收藏标签
- 收藏夹页能列出所有已收藏文献

## 3. 范围边界 / 不做的事
明确"不做"以防 scope creep（**关键字段**）。
- 不做：高级筛选（按 journal / year range 过滤）
- 不做：批量收藏 / 导出
- 不做：跨数据源搜索（仅 PubMed，不含 EPMC / Crossref）

## 4. 关键决策 / 技术取舍
跨 Issue 的架构决策。
- 收藏数据：直接存 PostgreSQL，不引入 Redis 缓存
- PubMed API：复用 patra-ingest 模块，不新建独立 adapter
- 前端路由：Next 15 App Router，server / client 组件按页面拆分

## 5. Done 判定
这版"完成"的客观标准。
- 所有 Issue 状态 = Done
- 单元测试 + 集成测试 全部 GREEN
- 核心 flow 自测能跑通（搜索 → 看详情 → 加收藏 → 收藏夹查看）
- 关键 error log 无未处理项
```

### 2.3 Project Entry Gate（关卡）

Draft → Active 必须满足：

- [ ] 5 字段全部填完
- [ ] 第 2 字段每条功能 → 已生成 1 个 Linear Issue（Description = 该故事 + 初步 AC）
- [ ] 第 3 字段"不做的事"显式列出
- [ ] 第 5 字段 Done 判定可验证

---

## 3. Issue 层（工程维度）

### 3.1 状态机

```
Backlog → Tech Design → BE → Design → FE → Review → Done
   ↑                                          │
   └─────── No-Go 回路（退到对应阶段） ──────┘
```

| 状态 | 主导 | 关键工具 / Skill | 入口条件 | 出口条件（DoD） |
|------|------|----------------|---------|---------------|
| Backlog | — | Linear | Project Active | 你决定开始这个 Issue |
| Tech Design | Claude + 你决策 | `superpowers-zh:brainstorming` + `writing-plans` | Issue Description 含用户故事 + AC | `docs/patra/specs/<topic>-design.md` 已写 + API 字段清单已明确 + `path` 标签确定 |
| BE | Claude Code + patra-skills | `patra-hexagonal` / `patra-jpa` / `patra-events` + TDD | Tech Design 通过 | BE PR 创建 + 单测+集成测试 GREEN + Bruno collection 入 git + OpenAPI schema 暴露 + `BE PR` 字段填回 |
| Design (UI) | Claude Design + 你 review | Claude Design + Handoff to Claude Code | BE 通过（或 `flow:design-first` 时 Tech Design 通过） | Claude Design URL + Handoff token + Sub-issue「UI 设计稿」+ `Design URL` 字段填回 |
| FE | Claude Code 消费 Handoff + 你 review | openapi-typescript + openapi-fetch + Bruno | Design 通过 | FE PR 创建 + UI 还原 ≥ 95% + Vitest+RTL 测试 + 类型 100% 来自 openapi-typescript + `FE PR` 字段填回 |
| Review | CodeRabbit + 你做 Go/No-Go | CodeRabbit CLI（push 前）+ GitHub App（PR 后） | FE PR + BE PR 都开 | CodeRabbit 评论全处理 + 自测三件套通过 + `Review Outcome` 字段填回 + PR merge（如已引入 Sentry，挂 release tag） |
| Done | — | — | Review 通过 + PR merged | （进入下一个 Issue） |

### 3.2 Linear 三层结构

| 层级 | 含义 | 状态 | 数量 |
|------|------|------|------|
| Project | 大版本 / Initiative（如 v0.2 PubMed 搜索） | Draft / Active / Done | 1（每版本 1 个） |
| Issue | 用户故事（如"用户能搜 PubMed"） | Backlog / Tech Design / BE / Design / FE / Review / Done | 多个/版本，每个 1-3 天 |
| Sub-issue | 单阶段产物（Tech Design / BE PR / UI 稿 / FE PR / Review 反馈 / 中断快照） | Todo / In Progress / Done | 多个/Issue |

### 3.3 Issue 自定义字段

每个 Issue 必须有这 5 个自定义字段（作为 Claude 跨会话恢复上下文的核心来源）：

| 字段 | 填写时机 | 内容 |
|------|---------|------|
| `Acceptance Criteria` | Tech Design 阶段 | ≥ 3 条可验证条件（Given/When/Then 或简化格式） |
| `Design URL` | Design 阶段 | Claude Design 链接 + Handoff token |
| `FE PR` | FE 阶段 | GitHub PR URL |
| `BE PR` | BE 阶段 | GitHub PR URL |
| `Review Outcome` | Review 阶段 | CodeRabbit 摘要 + Go/No-Go 决策 + 时间戳 |

### 3.4 Issue 标签分流

| 标签 | 含义 | 用途 |
|------|------|------|
| `path:full`（默认） | 走完整 5 阶段 SOP | 默认所有 Issue |
| `path:fast-track` | 跳过 Tech Design + Design，直接 BE/FE/Review | typo / UI 微调 / 纯重构 / 配置升级 / BUG fix（必须先写复现测试）|
| `flow:design-first` | 翻转 BE 和 Design 顺序，Design 先 | UI 探索性强 / 数据完全你自己定义 / 你对外部 API 已经非常熟悉 |
| `paused:<原因>` | Issue 暂停 | 配合 Sub-issue「中断快照」使用 |

---

## 4. 阶段详细定义

### 4.1 Tech Design

**入口**：Issue 推到 `Tech Design` 状态

**主导**：Claude Code（起草细化）+ 你（架构决策）

**流程**：
1. 在 Claude Code 里调用 `/superpowers-zh:brainstorming`，输入 Issue Description（用户故事 + 初步 AC）
2. Claude 通过对话探索技术方案、领域模型、外部 API 调研（查 PubMed E-utilities 文档等）、字段清单初稿
3. 用 `/superpowers-zh:writing-plans` 产出实施计划
4. 产物路径：`docs/patra/specs/<topic>-design.md`（含技术方案、领域模型、API 字段清单、AC 细化、实施计划）
5. 把细化后的 AC 回写到 Linear Issue 的 `Acceptance Criteria` 字段
6. 决定 Issue 标签（`path:*` / `flow:*`）

**产物**：
- `docs/patra/specs/<topic>-design.md`
- Linear Issue `Acceptance Criteria` 字段填回
- Issue 标签确定

**DoD**：
- [ ] 技术方案明确（领域模型 + 接口契约 + 数据流）
- [ ] API 字段清单初稿（用于 UI Design 阶段参考）
- [ ] AC ≥ 3 条可验证
- [ ] 实施计划分步
- [ ] `path` / `flow` 标签确定

### 4.2 BE

**入口**：Issue 推到 `BE` 状态（默认，Tech Design 后）或 `flow:design-first` 时在 Design 后

**主导**：Claude Code + patra-* skill 体系；你做架构决策

**流程**：
1. 在 Claude Code 里调用 `/superpowers-zh:test-driven-development`（TDD 主流程，强制）
2. 架构决策 / 创建组件 → 调用 `patra-hexagonal`
3. 数据持久化 → 调用 `patra-jpa`
4. 事件 / Outbox → 调用 `patra-events`
5. 实现过程中如发现字段变化，立即回写 Tech Design 文档的字段清单（保持 SoT 同步）
6. Bruno collection 同步更新（API 契约入 git）
7. OpenAPI schema 自动暴露（已有 `linqibin-spring-boot-starter-openapi`）
8. 创建 GitHub PR；Sub-issue「BE 实现」link PR；Linear `BE PR` 字段填回

**产物**：
- BE GitHub PR
- Bruno collection 更新（入 git）
- OpenAPI schema 自动暴露
- Sub-issue「BE 实现」

**DoD**：
- [ ] 单元测试 + 集成测试全部 GREEN
- [ ] AC 在 BE 层可验证（curl / Bruno 跑通）
- [ ] Bruno collection 已更新并入 PR
- [ ] OpenAPI schema 已 sync
- [ ] `BE PR` 字段填回

### 4.3 Design (UI/UX)

**入口**：Issue 推到 `Design` 状态（默认在 BE 后，或 `flow:design-first` 时在 Tech Design 后）

**主导**：Claude Design 生成 + 你 review 微调

**流程**：
1. 打开 Claude Design，新建 design session
2. 输入：Issue Description（用户故事 + AC）+ Tech Design 字段清单 + 已实现 BE 的 OpenAPI schema（如果 BE 已完成）
3. Claude Design 生成可点击的 hi-fi 原型
4. 你 review 并对话迭代到主要 user flow 可点击
5. 发起 **Handoff to Claude Code**，获得 token
6. Design URL + Handoff token 回写 Linear `Design URL` 字段
7. 创建 Sub-issue「UI 设计稿」link Claude Design URL

**产物**：
- Claude Design URL + Handoff token
- Linear `Design URL` 字段填回
- Sub-issue「UI 设计稿」

**DoD**：
- [ ] 主要 user flow 在 Claude Design 中可点击
- [ ] 每条 AC 在 UI 上能找到对应入口
- [ ] Handoff token 已生成
- [ ] `Design URL` 字段填回

**注意**：跳过传统 wireframe 阶段——AI 时代 hi-fi 成本接近零，思考流程在 Tech Design 阶段已完成；wireframe 反而易产生"完成感幻觉"。

### 4.4 FE

**入口**：Issue 推到 `FE` 状态（Design 完成后）

**主导**：Claude Code 消费 Handoff + 你 review

**流程**：
1. 在 Claude Code 把 Handoff token 喂给 Claude，让它把 Claude Design 产物翻译到前端栈
2. 跑 `pnpm gen:bootstrap`（一键 sync）：
   - `pnpm gen:types` — openapi-typescript 生成 TS types
   - `pnpm gen:hooks` — openapi-react-query 生成 type-safe TanStack Query hooks
   - `pnpm gen:zod` — openapi-zod-client 生成 Zod schemas
3. 实现页面 / 组件 / 状态管理（Zustand）/ 表单（RHF + Zod）
4. 用 Bruno collection 做本地联调(数据真实形态）
5. Vitest + RTL 写关键交互单测
6. Husky pre-commit：Biome + tsc --noEmit + Vitest 自动跑
7. 创建 GitHub PR；Sub-issue「FE 实现」link PR；Linear `FE PR` 字段填回

**产物**：
- FE GitHub PR
- 前端组件 / 页面代码
- Vitest + RTL 测试

**DoD**：
- [ ] UI 还原 ≥ 95% 与 Claude Design 一致
- [ ] 关键交互可手动跑通
- [ ] 类型 100% 来自 openapi-typescript（零手写 API 类型）
- [ ] Vitest + RTL 测试覆盖关键交互
- [ ] `FE PR` 字段填回

### 4.5 Review

**入口**：FE PR + BE PR 都开（或单 PR，如纯 FE/纯 BE 改动）

**主导**：CodeRabbit（守门）+ 你做 Go/No-Go

**三层质量门**（细化）：

| 层 | 触发时机 | 工具 | 检查内容 |
|----|---------|------|---------|
| 1 | pre-commit（每次 `git commit`） | Husky + lint-staged | FE: Biome + tsc --noEmit + Vitest / BE: pre-commit-config.yaml |
| 2 | push 前（每次 `git push`） | CodeRabbit CLI | 本地快审，找语义 / 漏边界 |
| 3 | PR 后 / merge 前 | CodeRabbit GitHub App | 自动 inline review + summary + sequence diagram |

**流程**：
1. PR 创建 → CodeRabbit 自动 review，发 inline 评论 + summary
2. 你 review 评论：每条**处理**（修复 / "why 不改"回复 / 关闭）
3. **自测三件套**：
   - 跑 Bruno collection（API 契约验证）
   - 手测 UI flow（与 AC 一一对照）
   - （可选）检查 Sentry dashboard（如已引入 Sentry，针对 hotfix / 之前有问题场景）
4. **Go/No-Go 决策**：
   - Go → merge PR；Issue 状态推到 `Done`（如已引入 Sentry，release tag 自动打上）
   - No-Go → Issue 状态退回出问题阶段（`BE` / `FE` / `Tech Design`）；新增 Sub-issue「Review 反馈 Rn」

**Linear `Review Outcome` 字段格式**：
```
CodeRabbit: 12 条，已处理 12
自测: Bruno ✓ | UI flow ✓ | AC 1/2/3 ✓
决策: Go @ 2026-05-19 14:30
```

**Done 的 DoD**：
- [ ] CodeRabbit 评论 100% 已处理（修复或解释）
- [ ] AC 全部手测通过
- [ ] `Review Outcome` 字段填回
- [ ] （如已引入 Sentry）release tag 已挂上
- [ ] 关联 GitHub PR 已 merge

---

## 5. 工具栈

### 5.1 必备 11 项

| 环节 | 工具 | 角色 / 用途 |
|------|------|-----------|
| 流程载体 | **Linear** | 单一信息源；Project / Issue / Sub-issue 三层结构 |
| 项目规则 | CLAUDE.md + patra-* skill 体系 + `.claude/rules/` | Claude 跨会话规则；已构建完整 |
| 想法 / 长文沉淀 | Obsidian | 已有 vault；放想法 inbox、retro、长论证 |
| **PRD 起草** | Claude Code + `patra:release-planning`（**待创建**） | Project 层版本规划文档（详见 §9.2 决策 #6） |
| **Tech Design 起草** | Claude Code + `superpowers-zh:brainstorming` + `writing-plans` | Issue 层工程规格（已有 skill） |
| UI/UX | **Claude Design** + Handoff to Claude Code | hi-fi UI 设计；Handoff → 前端实现闭环 |
| API 契约 | **Bruno** | Collection 入 git，与 PR 一起 review；CodeRabbit 也能看 |
| 前端类型 | **openapi-typescript**（+ openapi-fetch / openapi-react-query / openapi-zod-client） | 后端 OpenAPI → 前端 TS types + Query hooks + Zod schemas，零手写 |
| 后端实现 | Claude Code + patra-hexagonal + patra-jpa + patra-events | 已有 skill 体系，TDD 主流程 |
| VCS / PR | GitHub | 已用；PR 标题模板支持 `LIN-123` 引用 |
| Code Review | **CodeRabbit**（CLI 预审 + GitHub App 守门） | 双档 review；Pro $15/月 |

### 5.2 可选

| 环节 | 工具 | 启用条件 |
|------|------|---------|
| 用户问卷 | Tally | 未来有用户需要调研时 |
| PRD 流程图 | Excalidraw 或纸笔 | 当 PRD 阶段需要手绘用户流程时 |
| 运行时监控 | **Sentry** | 上线运行后引入错误监控时（FE 装 `@sentry/nextjs`，BE 用 `sentry-spring-boot-starter`，免费版 5K events/月） |

### 5.3 显式不引入（思考过为什么不加）

- ~~ChatPRD~~ — `patra:release-planning` skill + 版本规划模板替代
- ~~v0.dev / Lovable / Bolt~~ — Claude Design 已覆盖
- ~~Whimsical~~ — Excalidraw / 纸笔已足够；AI 时代 wireframe 工具过时
- ~~PostHog / Mixpanel / Amplitude~~ — 自用 + 无用户阶段无价值；未来有用户时再加
- ~~Canny / Featurebase~~ — 早期 Linear native 足够，用户群 100+ 再加
- ~~Playwright / Cypress~~ — E2E 单人维护成本高；Project 复杂后再补
- ~~Notion / Confluence~~ — 已有 Linear + Obsidian + docs/，不重复
- ~~Miro / Figma Pro~~ — 单人无需协作设计软件
- ~~Productboard / Aha!~~ — 面向 PM 团队，单人 overkill
- ~~Datadog / NewRelic~~ — 后端已有 observability starter；Sentry 可作可选补漏（详见 §5.2）

---

## 6. 横切机制

### 6.1 上下文管理（解"上下文易丢"痛点）

每个 Linear Issue 是跨会话**单一信息源**。Claude 恢复上下文只看三处：

1. Linear Issue Description（用户故事 + AC）
2. Linear 5 个自定义字段（AC / Design URL / FE PR / BE PR / Review Outcome）
3. 关联 GitHub PR + Sub-issues

**项目级规则**：`CLAUDE.md` + `.claude/rules/` + skill 体系（已完备）。

**禁止**：Claude 不在 Linear 之外另起"记忆库"；每次新会话先读 Issue 状态再决策。

### 6.2 中断恢复（解"节奏失控 + 上下文易丢"）

**暂停 Issue 流程**：
1. Issue 加标签 `paused:<原因>`（如 `paused:waiting-pubmed-api-quota`）
2. 创建 Sub-issue「中断快照」记录：
   - 当前状态（哪一阶段）
   - 下一步具体动作（≤ 3 条）
   - 关键决策（选 X 而非 Y 的理由）
   - 引用的代码 / 文档 / Bruno collection link
3. 关闭 Claude Code 会话

**恢复 Issue 流程**：
1. 打开 Linear Issue
2. 读「中断快照」Sub-issue
3. 把 Sub-issue 内容粘贴到 Claude Code 新会话作为起始上下文
4. Claude 自动恢复 → 删除 `paused:` 标签 + 关闭「中断快照」Sub-issue

### 6.3 回路机制（解"质量没把关"）

**No-Go 处理**：
1. Issue 状态退到出问题阶段（`BE` / `FE` / `Design` / `Tech Design`）
2. 创建 Sub-issue「Review 反馈 R1」（R1/R2/R3... 递增）记录每轮反馈
3. **不允许 Skip Review**：再次到 Review 要重跑三层门

**升级机制**：
- R3 仍 No-Go → 强制升级到 Tech Design 重审，从头梳理（避免在低层反复修补）
- 新增 Sub-issue「Tech Design v2」记录重审原因

### 6.4 三层质量门（贯穿整个 SOP）

| 层 | 触发时机 | 工具 | 检查内容 |
|----|---------|------|---------|
| 1 | pre-commit | Husky + lint-staged | FE: Biome check + tsc --noEmit + Vitest / BE: `.pre-commit-config.yaml` |
| 2 | push 前 | CodeRabbit CLI | 本地快审 |
| 3 | PR 后 | CodeRabbit GitHub App | 自动 inline + summary + sequence diagram |

### 6.5 节奏(事件驱动，无时间表）

不预设"周几做什么"。SOP 的节奏由 **Issue 状态机驱动**：

| Issue 状态变化 | 你的工作模式 |
|---------------|-------------|
| → `Tech Design` | Claude Code + `/superpowers-zh:brainstorming` + `writing-plans` |
| → `BE` | Claude Code + patra-* skill + TDD |
| → `Design` | Claude Design + Handoff |
| → `FE` | patra-web 仓库 + Handoff 消费 + `pnpm gen:bootstrap` |
| → `Review` | GitHub PR + CodeRabbit + 自测三件套 |

**避免角色切换的可选策略**（不强制）：

1. **Batch by stage**：等多个 Issue 进同一阶段再统一处理（"3 个 Issue 都到 BE 后再连续做 BE"），减少切换成本
2. **单 in-progress 原则**：单时段只让 1 个 Issue 处于活跃实施（`FE` / `BE` 状态）；其余放 `Backlog` / `paused:*`
3. **`paused:` 标签 + 中断快照**：临时不做的 Issue 立即快照暂停，不要让 Issue 永远卡在某阶段
4. **Retrospective 触发条件**：每完成 N 个 Issue、或遇 Review 回路 ≥ R2、或 SOP 阻塞超 2 天 时，写一次 retro 沉淀到 Obsidian

---

## 7. 补丁路径

### 7.1 `path:fast-track`（小修小 enhance）

**触发**：Issue 创建时打 `path:fast-track` 标签。

**精简流程**：跳过 Tech Design + Design，直接进 `FE` 或 `BE`（看改哪边）→ Review → Done。

**适用白名单**（不在此列禁用 fast-track）：
- typo / 措辞 / 文案
- UI 微调（border、间距、颜色）
- 纯重构（行为不变 + 测试 GREEN）
- 配置 / 依赖升级
- BUG 修复 + 单测覆盖（**必须先写复现测试**）

**fast-track 不豁免**：
- CodeRabbit 双档审查
- tsc / Vitest / 集成测试必须 GREEN
- `Review Outcome` 仍需填回

**fast-track Issue 可独立存在**：不必挂任何 Project（直接挂在 Linear Team / Workspace 下），适合零散小修。

### 7.2 `flow:design-first`（UI 探索 / 内部数据）

**触发**：Issue 创建时打 `flow:design-first` 标签。

**翻转顺序**：`Tech Design → Design → BE → FE → Review → Done`

**适用条件**（**任一**）：
- UI 探索性强（想先看体验决定要不要做）
- 数据完全你自己定义（不依赖外部 API，如内部标签、收藏夹、笔记等）
- 你对该外部 API 已经非常熟悉，确认字段不会变

**默认 BE 先的理由**：Patra 是消费外部 API 项目，字段稳定性来自真实调用；BE 先 = 回路最少。`flow:design-first` 是 escape hatch，不是默认路径。

---

## 8. 落地

### 8.1 一次性工具初始化清单（按顺序）

1. **Linear**
   - 创建 workspace + Patra Team
   - 自定义状态：
     - Project: `Draft` / `Active` / `Done`
     - Issue: `Backlog` / `Tech Design` / `BE` / `Design` / `FE` / `Review` / `Done`
     - Sub-issue: `Todo` / `In Progress` / `Done`
   - 自定义字段（Issue 层）：`Acceptance Criteria` / `Design URL` / `FE PR` / `BE PR` / `Review Outcome`
   - 标签：`path:full` / `path:fast-track` / `flow:design-first` / `paused:*`
   - 启用 Linear ↔ GitHub 集成（commit message 引用 `LIN-123` 自动关联）

2. **CodeRabbit**
   - GitHub App 安装（Pro $15/月）
   - 本地装 CLI（`npm i -g @coderabbit/cli` 或官方推荐方式）
   - 项目根加 `.coderabbit.yaml`（自定义规则引用 `.claude/rules/`）

3. **Claude Design**
   - 订阅 Pro 以上
   - 初始化 Patra 工作区（让它读 patra-api / patra-web 仓库提取色彩、字体、组件库）

4. **Bruno**
   - 在 patra-web / patra-api 各自仓库根创建 `bruno/` 目录
   - 首批 collection 入 git

5. **`patra:release-planning` skill**（**前置项**）
   - 在 `.claude-marketplace/patra/skills/release-planning/` 创建 SKILL.md
   - 设计参考 §9.2 决策 #6 的说明
   - 必须在第一次创建 Linear Project 之前完成

6. **patra-web**
   - 新建仓库 / monorepo 子项目
   - `pnpm create next-app`，按完整前端栈初始化：
     - Next.js 15 App Router
     - React 19
     - TypeScript 5 strict
     - Tailwind CSS v4
     - shadcn/ui
     - TanStack Query v5
     - Zustand
     - React Hook Form + Zod
     - Biome
     - Vitest + Testing Library + jsdom
     - Husky + lint-staged
   - 添加 `pnpm gen:bootstrap` 一键命令（types + hooks + zod）

### 8.2 首个 Issue 试运行清单

**选题建议**：选一个小而完整的 Issue（能走完全部阶段、风险低）。例如"用户能查看某个 Provenance 的元数据"。

**逐阶段 checklist**：
- [ ] **Tech Design**：`docs/patra/specs/<topic>-design.md` 写完 + AC ≥ 3 条 + `path` 标签
- [ ] **BE**：调用 patra-hexagonal/jpa skill；BE PR 创建；Bruno collection 更新
- [ ] **Design**：Claude Design URL + Handoff token 入 Linear
- [ ] **FE**：消费 Handoff token；`pnpm gen:bootstrap`；FE PR 创建
- [ ] **Review**：CodeRabbit 处理 + 自测三件套 + `Review Outcome` 字段填写 + Go merge

**回顾**：Issue Done 后写 retro 沉淀到 Obsidian `notes/patra-retros/`（"哪步最累 / 最顺 / SOP 哪里要调"），作为 SOP v0.2 输入。

### 8.3 5 条红线（常见踩坑）

1. **Tech Design 偷懒**：AC / 字段清单 / 实施计划写得模糊 → 强制 Tech Design DoD 全过，否则状态不能推进
2. **Skip Review 走捷径**：任何 Issue 必须经三层门（包括 `path:fast-track`）；无例外
3. **Project Draft 漂移**：在 Draft 阶段开始写实现代码 → Draft 只产出版本规划 + Issue 池，不允许写实现
4. **回路无限循环**：Review No-Go > R3 → 强制升级到 Tech Design 重审，避免在低层反复修补
5. **Linear 字段不填**：状态推进了但字段没更新 → 设 Linear 自动化在状态切换时检查必填字段；Claude 在新会话开始时主动检查并提示补全

### 8.4 演进与反馈

SOP v0.1 是起点，不是终点。**触发反思的时机**：

- 完成 N 个 Issue（N 自定，建议 3-5）
- 遇 Review 回路 ≥ R2
- SOP 阻塞超 2 天
- 工具栈中某项工具出现 friction

**反思方式**：在 Obsidian 写一篇 retro，记录：
- 哪个阶段累 / 顺
- 哪个工具有效 / 摩擦
- 哪条 DoD 经常过不去
- SOP 哪里该简化 / 加强

**修订**：SOP 任何修改都更新本文档，commit 时附 `docs(sop): vN.M [修改要点]`。

---

## 9. 附录

### 9.1 主流程图（端到端）

```
脑子里的想法
    ↓
Linear Project: Draft
    ↓ 跑 patra:release-planning（版本规划模板）
docs/patra/release-specs/<version>.md
    ↓ 功能列表 → 派生 Linear Issues
Linear Project: Active
    ↓
══ 每个 Issue 走 SOP ══
Tech Design → docs/patra/specs/<topic>-design.md
    ↓ (含 API 字段清单初稿)
BE → BE PR + Bruno collection + OpenAPI schema
    ↓ (API 字段稳定下来)
Design (UI) → Claude Design URL + Handoff token
    ↓ (UI 设计基于真实 API)
FE → FE PR + 组件代码 + Vitest/RTL 测试
    ↓ (UI + API 集成)
Review → CodeRabbit + 自测三件套 + Go/No-Go
    ↓
Issue: Done
══════════════════
    ↓
所有 Issue Done → Project: Done
    ↓
（可选）retro 沉淀到 Obsidian
```

### 9.2 关键决策记录

本 SOP 设计过程中做过的关键决策：

| # | 决策 | 选择 | 理由 |
|---|------|------|------|
| 1 | SOP 主轴 | Feature 全生命周期 | 而非节奏切片或角色协议（更适合 solo + 5 类痛点全中） |
| 2 | 任务卡载体 | Linear（非 GitHub Issues / Obsidian Bases） | UI / workflow 灵活，Linear ↔ GitHub 集成成熟 |
| 3 | 切片粒度 | 三层（Project / Issue / Sub-issue） | 支持版本规划 + 用户故事 + 任务三层表达 |
| 4 | 人 vs Claude 边界 | 决策者 + 把关者 | AI 是执行层；人介入"起点（需求）"和"终点（review）" |
| 5 | 三个新工具是否引入 | Bruno / openapi-typescript 全部引入；Sentry 移到可选 | 必要：契约入 git / 类型自动 sync；Sentry 当前无用户场景延后 |
| 6 | PRD skill | 独立 `patra:release-planning`（**待创建**），不是 Claude Design 也不复用 brainstorming | Claude Design 不支持 PRD 级抽象生成；brainstorming 已为工程 spec 特化（路径绑定 `docs/patra/specs/` / 涵盖范围"架构、组件、数据流" / HARD-GATE 终点 writing-plans），强行复用违背"职责清晰"。设计草稿：5 字段对话引导 → 派生 Issue 池 → 终止于 Linear Project Active |
| 7 | 线框 vs 高保真 | 直接 hi-fi，跳过 wireframe | AI 时代 wireframe 工具过时；思考流程在 Tech Design 完成 |
| 8 | Project 子阶段 | 简化为 Draft → Active → Done | 单人 + 无用户，不需要 Validation 等中间步骤 |
| 9 | Issue 状态机顺序 | Tech Design → BE → Design → FE → Review → Done | 消费外部 API 项目 BE 先 = 回路最少；`flow:design-first` 作为 escape hatch |
| 10 | 节奏 | 事件驱动而非时间驱动 | 项目语境"无时间压力"匹配 |
| 11 | PostHog | 不引入 | 自用 + 无用户阶段无价值 |
| 12 | Sentry | 移到可选（未来引入） | 当前自用 + 无外部用户，运行时监控价值低；上线后再引入 |

### 9.3 参考资料

**调研轮 1**（节 D 工具栈背景）：
- Claude Design 官方发布：https://www.anthropic.com/news/claude-design-anthropic-labs
- Claude Design Handoff 机制：https://claudefa.st/blog/guide/mechanics/claude-design-handoff
- CodeRabbit 文档：https://docs.coderabbit.ai/
- AI-augmented solo dev 工作流：https://www.nxcode.io/resources/news/one-person-unicorn-context-engineering-solo-founder-guide-2026
- Solo dev LLM workflow：https://medium.com/@addyosmani/my-llm-coding-workflow-going-into-2026-52fe1681325e

**调研轮 2**（PRD 阶段方法论）：
- Claude Design 功能评测：https://uxpilot.ai/blogs/claude-design-review
- Claude Design vs Figma：https://www.eigent.ai/blog/claude-design-vs-figma-make
- 线框 vs 高保真传统对比（NN/g）：https://www.nngroup.com/articles/ux-prototype-hi-lo-fidelity/
- AI 时代线框图地位变化：https://medium.com/design-bootcamp/high-fidelity-ai-prototypes-as-replacement-for-wireframes-c2e84053539b
- ChatPRD AI codegen 用法：https://www.chatprd.ai/learn/prd-for-ai-codegen
- 1-page Lean PRD：https://plan.io/blog/one-pager-prd-product-requirements-document/

**调研轮 3**（产品工作流全景）：
- Pieter Levels MVP 方法：https://levels.io/how-i-build-my-minimum-viable-products/
- Tony Dinh solopreneur 成长：https://news.tonydinh.com/p/my-solopreneur-story-zero-to-45kmo
- Solo Founder 2026 工具栈：https://www.opc.community/blog/solo-founder-tools-2026
- AI 产品管理工具横向对比：https://www.telos-ai.org/blog/ai-product-management-tools-compared

### 9.4 术语表

| 术语 | 含义 |
|------|------|
| Tech Design | Issue 内的工程规格设计阶段，产物为 `docs/patra/specs/*-design.md` |
| 版本规划文档 / Release Spec | Project 内的产品规划文档，产物为 `docs/patra/release-specs/*.md`；由 `patra:release-planning` skill 产出 |
| `patra:release-planning` | **待创建**的独立 PRD skill；与 brainstorming 严格分离 |
| Handoff token | Claude Design 生成的桥接 token，可让 Claude Code 接住设计稿 |
| Bruno collection | API 契约文件，存入 git 与 PR 一起 review |
| AC（Acceptance Criteria） | 验收标准，每个 Issue ≥ 3 条可验证 |
| Sub-issue | Linear 三层结构的最底层，记录单阶段产物或中断快照 |
| DoD（Definition of Done） | 阶段或 Issue 的"完成定义"，所有项 ✓ 才能推进状态 |
| 三层质量门 | pre-commit (Husky) → push 前 (CodeRabbit CLI) → PR 后 (CodeRabbit App) |
| 回路（R1/R2/R3） | Review No-Go 后状态退回的轮次计数 |
| escape hatch | 默认路径之外的逃生机制（如 `flow:design-first`） |
