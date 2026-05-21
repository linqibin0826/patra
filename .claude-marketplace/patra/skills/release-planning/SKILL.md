---
name: release-planning
description: 启动新版本 / 创建 Linear Project 时使用——产出"5 字段 Release Spec"（版本目标、功能列表、范围边界、关键决策、Done 判定）并派生 Linear Issue 池。当用户说"我想做 v0.X"、"启动新版本"、"这一版要做..."、"准备一份 release spec"、"开个 Project"、"规划下一版"，或描述一组打算在本轮交付的功能集时，必须使用此技能。**这是 Project 层产品 PRD，不是 Issue 层工程 spec**（工程 spec 用 brainstorming）。
---

# Release Planning：版本规划

## 概述

通过自然的协作对话，把"想做什么"转化为单人开发的轻量 PRD（版本规划文档），输出 5 字段 Release Spec + 派生 Linear Issue 池。终止状态是 Linear Project 推到 Active，**不**调用 writing-plans——Issue 层后续各自走 brainstorming + writing-plans。

**开始时宣布：** "我正在使用 patra:release-planning 技能创建版本规划文档。"

**产物保存位置：** `docs/patra/release-specs/<version>.md`（默认）

- **产物是 Markdown，不是 HTML**——release-spec 要直接粘到 Linear Project Description（Linear 不支持 HTML 富文本），且 5 字段 spec 较短不需要富视觉层次

**路径覆盖规则（重要）**：默认路径仅在用户未指定时采用。

- 如果用户在调用时显式指定其他路径（如"保存到 `/tmp/test-spec.md`"、"放到这个测试目录下"），**优先用户指定路径**，不要回退到默认值
- 这条规则适用于 skill-creator / eval 等评测场景的 `outputs/` 目录——不要污染默认的 `docs/patra/release-specs/`
- Linear MCP 接入后未来可能改为直接写到 Project Description——届时本地路径作为备份

## 与 brainstorming 的边界（严格分离）

| 维度 | release-planning（本 skill）| brainstorming |
|------|----------------------------|---------------|
| 层级 | **Project（产品维度）** | **Issue（工程维度）** |
| 时机 | 启动新版本时 | 开始具体 feature 实现时 |
| 涵盖 | 用户故事、范围、Done 判定 | 架构、组件、数据流、错误处理、测试 |
| 产物 | `docs/patra/release-specs/<version>.md`（MD）| `docs/patra/specs/<topic>-design.html`（HTML）|
| 终止 | Linear Project 进 Active + Issue 池就绪 | 调用 writing-plans 写实施计划 |

**HARD-GATE**：在产出版本规划文档并获得用户批准之前，不要进入 Issue 层的 Tech Design / BE / Design 等阶段，不要写任何实现代码。

## 反模式："这个想法太小不需要 release spec"

每个新版本都要走这个流程。哪怕只有 2 个功能，5 字段 spec 强制你回答"不做什么"、"Done 判定"——这两个字段对单人开发**至关重要**（防 scope creep + 防"差不多就行了"的拖延陷阱）。

## 检查清单

为以下每个条目创建任务，按顺序完成：

1. **探索项目上下文** — 按以下顺序检查（视可用性灵活降级，**不阻塞**）：
   - **Linear Backlog**（如有 Linear MCP 接入）— 查未规划的想法 / 上一版未做完的项
   - **`docs/patra/release-specs/`** — 读上一版 release-spec，了解上一版做了什么、留了什么"不做"承诺
   - **Obsidian Inbox**（如有读取权限）— 翻找产品想法记录
   - 都没有时 — 直接从用户当前 prompt 提取版本意图，跳过此步
2. **5 字段对话引导** — 一次只问一个字段，顺序：版本目标 → 功能列表 → 范围边界 → 关键决策 → Done 判定
3. **分节展示草案** — 每个字段问完 + 草拟 + 用户批准后再进下一个
4. **写到文件** — `docs/patra/release-specs/<version>.md`
5. **派生 Linear Issue 列表（建议）** — 第 2 字段每条功能 → 1 个 Issue 建议（Title + Description + AC + `path:full` 标签 + Backlog 状态）
6. **用户审查** — 请用户审阅 spec + Issue 列表
7. **终止** — 提示用户在 Linear 中创建 Issues + Project 推到 Active；**不调用其他 skill**

## 流程图

```dot
digraph release_planning {
    "探索 Backlog/Inbox/上一版" [shape=box];
    "5 字段对话引导（顺序）" [shape=box];
    "分节展示草案" [shape=box];
    "每字段用户批准?" [shape=diamond];
    "写 release-spec.md（路径见正文）" [shape=box];
    "派生 Linear Issue 列表（建议）" [shape=box];
    "用户审查 spec + Issue 列表?" [shape=diamond];
    "提示用户：创建 Issues + Project → Active" [shape=doublecircle];

    "探索 Backlog/Inbox/上一版" -> "5 字段对话引导（顺序）";
    "5 字段对话引导（顺序）" -> "分节展示草案";
    "分节展示草案" -> "每字段用户批准?";
    "每字段用户批准?" -> "5 字段对话引导（顺序）" [label="否，调整"];
    "每字段用户批准?" -> "写 release-spec.md（路径见正文）" [label="是"];
    "写 release-spec.md（路径见正文）" -> "派生 Linear Issue 列表（建议）";
    "派生 Linear Issue 列表（建议）" -> "用户审查 spec + Issue 列表?";
    "用户审查 spec + Issue 列表?" -> "5 字段对话引导（顺序）" [label="修改"];
    "用户审查 spec + Issue 列表?" -> "提示用户：创建 Issues + Project → Active" [label="批准"];
}
```

**终止状态：提示用户在 Linear 中创建 Issues + 把 Project 推到 Active。** 不要调用任何其他 skill——Issue 层的 Tech Design / brainstorming / writing-plans 是后续阶段，不归此 skill。

## 5 字段对话引导

按以下顺序，**一次只问一个字段**。每个字段问完 + 草拟 + 用户批准后再进下一个。

### 字段 1：版本目标 / 价值

**提问**："这一版（如 v0.2）核心要交付什么价值？用 1-2 句话写一个用户视角的目标。"

**优秀示例**："Patra 能让我搜 PubMed 文献并加入收藏。"
**反面示例**："实现搜索功能"（技术化，缺用户视角）

**字段 DoD**：
- [ ] 1-2 句话
- [ ] 用户视角（不是技术视角）
- [ ] 能被一个具体使用场景验证

### 字段 2：功能列表 / 要做的事

**提问**："这一版要做哪些事？bulleted list，每条 = 1 个用户故事 → 后面会派生 1 个 Linear Issue。"

**形式**：每条用"用户能 …"或"作为 X，我希望 Y，以便 Z"。

**字段 DoD**：
- [ ] 每条是用户可见的能力（不是技术任务）
- [ ] 每条能用 1 个 Linear Issue 装下（1-3 天工作量）
- [ ] 整版功能数量合理（建议 3-7 个；超过 7 个考虑拆分版本）

### 字段 3：范围边界 / 不做的事

**提问**："明确列出'这版不做的事'——3-5 条，防 scope creep。"

**为什么这个字段最重要**：单人开发最容易的就是"哎，顺便把那个也做了"——这是版本永远 ship 不出去的主因。把"不做的事"写下来等于给自己拍一个心理锚定。

**字段 DoD**：
- [ ] ≥ 3 条具体不做的事
- [ ] 每条都是和当前 Project 高度相关但被显式排除的（如"不做高级筛选"、"不做批量收藏"）
- [ ] 不写不相关的发散事项（"不做 iOS app" 这种过度泛化无意义）

### 字段 4：关键决策 / 技术取舍

**提问**："跨 Issue 的架构决策、技术取舍有哪些？这些不能下放到 Issue 层 Tech Design，因为多个 Issue 会受影响。"

**判断规则（决定一条决策是否该进字段 4）**：

进字段 4，当且仅当满足以下任一：
1. **影响 ≥ 2 个 Issue 的代码 / 数据 / 架构**（如选 PostgreSQL 还是 Redis 影响所有 BE Issue 的数据层）
2. **跨层级**（同时影响 BE + FE，如选 Next.js App Router 决定 server / client 组件如何分布）
3. **不可逆 / 难逆**（一旦定下后续 Issue 都受约束，如统一引入 Outbox 模式 / 显式不引入）

**不属于字段 4 的（留到各自 Issue 层 Tech Design）**：
- 单 Issue 内的实现细节（如 SearchHandler 用 Strategy 模式还是 Template Method）
- 单 Issue 内的数据结构选择（如某个枚举字段的具体值）
- 局部优化（如某个查询的索引设计）
- 项目规约已定的事项（如字段命名 camelCase——不是本版新决策）

**优秀示例**：
- 收藏数据存 PostgreSQL，不引入 Redis 缓存（影响所有 BE Issue 的数据层）
- 复用 patra-ingest 模块，不新建 adapter（影响所有"采集 PubMed"相关 Issue）
- 前端按页面拆 server / client 组件（跨 FE Issue 的渲染策略）

**反例（不该写在字段 4）**：
- "SearchHandler 用 Strategy 模式" — 属单 Issue Tech Design
- "搜索结果分页大小默认 20" — 单 Issue 局部实现选择
- "字段命名采用 camelCase" — 已由项目规约决定

**字段 DoD**：
- [ ] 每条决策按上述判断规则确认应进字段 4（≥2 Issue / 跨层级 / 不可逆，三条任一）
- [ ] 每条决策有明确选择（X 而非 Y）
- [ ] 单 Issue 内的决策放进各自 Tech Design，不在此

### 字段 5：Done 判定

**提问**："这版'完成'的客观标准是什么？必须可验证（不是'用户满意'这种主观词）。"

**优秀示例**：
- 所有 Issue 状态 = Done
- 单元测试 + 集成测试 全部 GREEN
- 核心 flow 自测能跑通（搜索 → 看详情 → 加收藏 → 收藏夹查看）

**字段 DoD**：
- [ ] ≥ 3 条客观可验证条件
- [ ] 不含"用户满意"、"质量好"这类主观描述
- [ ] 可以用 boolean 判断每条是否达成

## 绿地项目 YAGNI 强化（产品维度）

patra-api 是单人开发的绿地项目（无历史包袱、无外部用户），写 release-spec 时主动剔除以下维度——它们都是 YAGNI 反模式：

- ❌ "向后兼容旧版用户" / "数据迁移" / "灰度发布"（无外部用户，全量切换即可）
- ❌ "用户增长 / 留存 / 转化率"目标（无用户基数）
- ❌ "竞品对标 / 市场调研"维度（自用产品，自我需求即可）
- ❌ "运营 / SEO / 营销"目标（无需推广）
- ❌ "如果时间允许 / 建议后续优化"语义包装

直接写"做什么 / 不做什么"，单一版本切换。

## 写文件 + 派生 Issue 列表

5 字段全部用户批准后：

1. **写到 `docs/patra/release-specs/<version>.md`**（Markdown 格式）
2. 文件结构按 5 字段顺序，二级标题 `## 1. 版本目标 / 价值` ~ `## 5. Done 判定`
3. **对第 2 字段的每条功能，提议在 Linear 创建 1 个 Issue**：
   - **Title**：用户故事的简短描述
   - **Description**：该用户故事 + 初步 AC（≥ 3 条 Given/When/Then 或简化"完成标准"格式）
   - **Status**：`Backlog`
   - **Tag**：`path:full`（默认；如属小修则 `path:fast-track`）
4. **不自动创建 Issue**——除非用户明确同意 + 有 Linear MCP 接入；默认只列出建议清单由用户在 Linear 手动创建

## 用户审查关卡

派生 Issue 列表后，请用户审查：

> "版本规划文档已写到 `<path>`。下面是建议派生的 Issue 列表：
>
> 1. **<Issue 1 title>** — <一句话描述>
> 2. **<Issue 2 title>** — <一句话描述>
> ...
>
> 请审阅 spec 和 Issue 列表。如果都 OK，告诉我后我会停止——你在 Linear 中：① 创建 Issues ② Project 状态推到 Active。"

等待用户回复。如果他们要求修改，调整 spec 或 Issue 列表，再次请求审查。**只有在用户批准后才进入终止状态。**

## 自检

写完版本规划文档后，以全新视角审视：

1. **占位符扫描**：是否有"待定"、"TODO"、未完成的字段？修复。
2. **5 字段 DoD**：每个字段的 DoD 是否都 ✓？
3. **Issue 派生完整性**：第 2 字段每条功能是否都有对应 Issue 建议？
4. **范围一致性**：第 3 字段"不做的事"是否真的与第 2 字段"要做的事"互补、不冲突？
5. **Done 判定可验证性**：第 5 字段每条是否 boolean 可判断？

发现问题就直接内联修复。无需重新审查——修好继续推进。

## 终止与交接

用户批准后：

> "Release Spec v0.X 已确认。请在 Linear 中：
> 1. 创建以下 Issues（建议清单已附上，状态全部置 `Backlog`，标签 `path:full`）
> 2. Project 状态推到 `Active`
>
> 后续每个 Issue 自行走 SOP（Tech Design → BE → Design → FE → Review → Done），各自触发 brainstorming + writing-plans 等 skill。"

**到此结束。不调用任何其他 skill。**

## 与 SOP 设计文档的关系

本 skill 是 `docs/patra/specs/2026-05-19-solo-dev-sop-design.md` 中 §2.2 Project 层执行 skill。Skill 行为细节（字段定义、产物路径、终止状态）必须与 SOP 文档保持一致。SOP 文档修订时同步检查本 skill；本 skill 修订时同步检查 SOP §2.2 / §5.1 / §8.1 / §9.2 / §9.4。
