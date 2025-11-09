# 📘 Spec-Kit + Claude Code 集成使用调研报告

**项目**: Patra-api
**调研日期**: 2025-11-09
**调研范围**: Spec-Kit 工具集成、Claude Code 使用机制、文件结构、指令功能
**调研方法**: 代码库探索 + 官方文档调研 + 架构分析

---

## 📑 目录

1. [Claude Code 基础概念](#1-claude-code-基础概念)
2. [Spec-Kit 完整文件清单与作用](#2-spec-kit-完整文件清单与作用)
3. [8 个 Slash Commands 详解](#3-8-个-slash-commands-详解)
4. [核心模块功能分析](#4-核心模块功能分析)
5. [工作流执行机制](#5-工作流执行机制)
6. [集成架构图](#6-集成架构图)
7. [使用场景与最佳实践](#7-使用场景与最佳实践)
8. [常见问题与解决方案](#8-常见问题与解决方案)

---

## 1. Claude Code 基础概念

### 1.1 什么是 Claude Code？

**Claude Code** 是 Anthropic 官方推出的 CLI 工具，允许开发者在终端中与 Claude AI 交互进行代码开发。

**核心特性**：
- 🖥️ **命令行界面**：在终端运行 `claude` 命令启动
- 📂 **项目上下文感知**：自动读取项目文件和配置
- 🔧 **工具集成**：支持文件操作、Bash 执行、代码搜索等工具
- 🔌 **MCP 协议**：通过 Model Context Protocol 扩展功能
- 🎯 **Slash Commands**：自定义命令机制（如 `/speckit.specify`）

### 1.2 Slash Commands 机制

**定义**: Slash Commands 是 Claude Code 的自定义命令系统，允许用户通过 `/command-name` 触发预定义的工作流。

**工作原理**：
```
用户输入: /speckit.specify "添加用户认证"
    ↓
Claude Code 读取: .claude/commands/speckit.specify.md
    ↓
展开 Prompt: Markdown 文件内容作为 AI 的指令
    ↓
执行指令: AI 调用工具（Read/Write/Bash/MCP）
    ↓
输出结果: 生成文件/报告
```

**关键特点**：
- ✅ **声明式定义**：命令是 Markdown 文件，不是代码
- ✅ **自动发现**：放入 `.claude/commands/` 即可激活
- ✅ **可组合**：命令可以调用其他命令
- ✅ **上下文传递**：通过 `$ARGUMENTS` 变量传递用户输入

### 1.3 Claude Code 的配置文件

**主要配置位置**：
- **全局配置**: `~/.claude/CLAUDE.md`（用户级指令）
- **项目配置**: `<project-root>/CLAUDE.md`（项目级指令）
- **命令定义**: `<project-root>/.claude/commands/`
- **技能定义**: `<project-root>/.claude/skills/`（如果有）

**Patra 项目的配置**：
```
/Users/linqibin/Desktop/Patra-api/
├── CLAUDE.md                    # 项目指令（组织者优先、三次原则等）
├── .claude/commands/            # Spec-Kit 的 8 个 slash commands
│   ├── speckit.specify.md
│   ├── speckit.clarify.md
│   ├── speckit.plan.md
│   ├── speckit.tasks.md
│   ├── speckit.analyze.md
│   ├── speckit.implement.md
│   ├── speckit.checklist.md
│   └── speckit.constitution.md
```

---

## 2. Spec-Kit 完整文件清单与作用

### 2.1 文件组织结构（4 层架构）

```
.specify/                        # Spec-Kit 根目录
├── memory/                      # 📚 项目记忆层（持久化原则）
│   └── constitution.md          # 项目架构宪章（345 行）
│
├── scripts/bash/                # 🔧 基础设施层（文件系统操作）
│   ├── common.sh                # 公共函数库（156 行）
│   ├── create-new-feature.sh    # 特性分支创建（261 行）
│   ├── setup-plan.sh            # 计划初始化（61 行）
│   └── check-prerequisites.sh   # 前置条件检查（167 行）
│
├── templates/                   # 📝 模板层（结构化提示）
│   ├── spec-template.md         # 特性规格模板（133 行）
│   ├── plan-template.md         # 实施计划模板（256 行）
│   ├── tasks-template.md        # 任务列表模板（278 行）
│   ├── checklist-template.md    # 通用检查清单模板（41 行）
│   ├── architecture-compliance-checklist.md  # 架构合规检查（206 行）
│   ├── code-quality-checklist.md             # 代码质量检查（306 行）
│   └── test-coverage-checklist.md            # 测试覆盖率检查（368 行）
│
└── specs/                       # 🎯 工件层（特性规格集合）
    └── [###-feature-name]/      # 按特性组织
        ├── spec.md              # 功能规格说明
        ├── plan.md              # 技术实施计划
        ├── tasks.md             # 任务分解列表
        ├── research.md          # 技术调研（可选）
        ├── data-model.md        # 数据模型（可选）
        ├── quickstart.md        # 快速开始指南（可选）
        ├── contracts/           # API 合约（可选）
        └── checklists/          # 质量检查清单
            ├── requirements.md
            ├── architecture-compliance.md
            ├── code-quality.md
            └── test-coverage.md
```

### 2.2 核心文件详细说明

#### 📚 Memory 层

| 文件 | 作用 | 关键内容 | 何时更新 |
|------|------|---------|---------|
| **constitution.md** | 项目架构宪章 | • 5 大架构原则<br>• 20+ 个验证项（CHK-*）<br>• 好坏实践对比<br>• 版本管理元数据 | • 架构规则变更时<br>• 技术栈升级时<br>• 质量标准调整时 |

**Constitution.md 结构**：
```markdown
# Patra 项目架构宪章

**批准日期**: 2025-01-01
**最后修订**: 2025-01-09
**版本**: 1.2.0

## I. 六边形架构（Hexagonal Architecture）
### 定义
[架构定义...]

### 强制规则
1. **模块结构** (MUST):
   - ✅ 必须包含 6 个子模块：boot/api/domain/app/infra/adapter
   - ❌ 禁止合并模块

### 好的实践
- ✅ Domain 层只依赖 JDK + patra-common-util + Lombok

### 坏的实践
- ❌ Domain 层引入 Spring Framework
- ❌ 跨层直接调用

### 验证项
- [ ] **CHK-ARCH-001**: 是否包含 6 个子模块？
- [ ] **CHK-ARCH-002**: Domain 层依赖是否符合规范？
```

---

#### 🔧 Scripts 层

| 脚本 | 作用 | 输入 | 输出 | 调用者 |
|------|------|------|------|--------|
| **common.sh** | 公共函数库 | 环境变量 | 路径常量 | 所有脚本 |
| **create-new-feature.sh** | 创建特性分支 | `--json "特性描述"` | JSON（分支名、路径） | `/speckit.specify` |
| **setup-plan.sh** | 初始化计划文件 | `--json` | JSON（plan.md 路径） | `/speckit.plan` |
| **check-prerequisites.sh** | 验证前置条件 | `--json [--require-tasks]` | JSON（特性目录、可用文档） | `/speckit.tasks`<br>`/speckit.implement` |

**common.sh 核心函数**：
```bash
# 1. 查找项目根目录（支持 Git 和非 Git 仓库）
find_project_root() {
  if git rev-parse --show-toplevel 2>/dev/null; then
    # Git 仓库：使用 git 根目录
  else
    # 非 Git：向上搜索 .specify/ 标记
  fi
}

# 2. 查找特性目录（基于前缀）
find_feature_dir_by_prefix() {
  # 支持同一规格的多个分支
  # 例如：004-fix-bug, 004-add-feature 都使用 specs/004-xxx/
}

# 3. 导出路径常量
export FEATURE_DIR="/path/to/specs/001-feature"
export FEATURE_SPEC="$FEATURE_DIR/spec.md"
export IMPL_PLAN="$FEATURE_DIR/plan.md"
export TASKS="$FEATURE_DIR/tasks.md"
```

**create-new-feature.sh 智能编号算法**：
```bash
# 编号逻辑
check_existing_branches() {
  # 1. 检查远程分支: git ls-remote --heads origin
  # 2. 检查本地分支: git branch
  # 3. 检查 specs 目录: find .specify/specs -name "[0-9]*-${short_name}"

  # 取最高编号 + 1
  next_number=$((highest_number + 1))

  # 格式化为 3 位数: printf "%03d" $next_number
  # 输出: 001, 002, ..., 999
}

# 分支名生成（带停用词过滤）
generate_short_name() {
  # 输入: "I want to add user authentication for the API"
  # 1. 转小写: "i want to add user authentication for the api"
  # 2. 过滤停用词: "user authentication api"
  # 3. 替换空格为连字符: "user-authentication-api"
  # 4. 截断到 GitHub 限制: 244 字节
  # 输出: "user-authentication-api"
}
```

---

#### 📝 Templates 层

| 模板 | 用途 | 关键章节 | 填充方式 |
|------|------|---------|---------|
| **spec-template.md** | 功能规格说明 | • 用户场景与测试<br>• 功能需求（FR-*）<br>• 领域模型（DDD）<br>• 成功标准 | AI 根据用户描述生成 |
| **plan-template.md** | 技术实施计划 | • 技术栈<br>• Constitution Check<br>• 项目结构<br>• 复杂性追踪 | AI 根据 spec.md 和技术选型生成 |
| **tasks-template.md** | 任务分解列表 | • 按阶段分组<br>• 按用户故事分组<br>• 并行标记 `[P]`<br>• 层标签 `[Domain]` | AI 根据 plan.md 和 spec.md 生成 |
| **architecture-compliance-checklist.md** | 架构合规检查 | • 6 层模块结构<br>• 依赖方向<br>• Domain 层纯净性 | AI 根据 constitution.md 生成 |
| **code-quality-checklist.md** | 代码质量检查 | • 命名规范<br>• SOLID 原则<br>• DRY 原则 | AI 根据代码库现状生成 |
| **test-coverage-checklist.md** | 测试覆盖率检查 | • 分层测试覆盖率<br>• 测试质量<br>• 特殊场景测试 | AI 根据测试现状生成 |

**spec-template.md 核心结构**（Patra 定制版）：
```markdown
# [特性名称] 功能规格说明

**特性分支**: `[###-feature-name]`
**创建日期**: [日期]
**状态**: 草稿 | 审核中 | 已批准

---

## 概览

[2-3 段高层描述]

---

## 用户场景与测试 🧪

### 用户故事 1 - [简要标题] (优先级: P1)

**Given** (前提条件)
[描述初始状态]

**When** (触发动作)
[描述用户执行的操作]

**Then** (预期结果)
[描述系统的响应]

**为什么是这个优先级**: [解释价值]

---

## 功能需求 📋

- **FR-001**: [功能需求描述]（引用用户故事: US1）
- **FR-002**: [功能需求描述]（引用用户故事: US2）

---

## 领域模型 *(如果功能涉及数据则包含)* 🏛️

**聚合根 (Aggregate Root)**:
- **[聚合根名称]**: [职责、关键属性]

**值对象 (Value Object)**:
- **[值对象名称]**: [不可变特性、验证规则]

**领域事件 (Domain Event)**:
- **[事件名称]**: [触发时机、包含数据]

---

## 成功标准 ✅

- **SC-001**: [可衡量的指标]（例如：API 响应时间 <200ms）
- **SC-002**: [可验证的结果]（例如：测试覆盖率 ≥80%）
```

**plan-template.md 核心结构**（Patra 定制版）：
```markdown
# [特性名称] 技术实施计划

---

## 摘要

[从 spec.md 提取的需求 + 技术方法]

---

## 技术上下文

**语言/版本**: Java 25
**主要框架**: Spring Boot 3.5.7, Spring Cloud 2025.0.0
**持久化**: MyBatis-Plus 3.x
**对象映射**: MapStruct
**测试**: JUnit 5 + Mockito + TestContainers
**架构模式**: 六边形架构 + DDD

---

## Phase 0: Constitution Check（宪章验证门禁）

### 六边形架构验证
- [ ] **CHK-ARCH-001**: 是否包含 6 个子模块？
- [ ] **CHK-ARCH-002**: Domain 层依赖是否符合规范？
- [ ] **CHK-ARCH-003**: 依赖方向是否正确？

### DDD 战术设计验证
- [ ] **CHK-DDD-001**: 是否正确识别聚合根？
- [ ] **CHK-DDD-002**: 值对象是否不可变？
...

**评估结果**: PASS / FAIL（如果 FAIL，必须在 Complexity Tracking 中说明理由）

---

## Phase 1: 设计

### 数据模型 (详见 data-model.md)
[引用或内联数据模型]

### API 合约 (详见 contracts/)
[引用 OpenAPI 规范]

### 项目结构
```
patra-{service}/
├── patra-{service}-boot/       # 启动层
├── patra-{service}-api/        # 契约层
├── patra-{service}-domain/     # 领域层（纯 Java）
├── patra-{service}-app/        # 应用层
├── patra-{service}-infra/      # 基础设施层
└── patra-{service}-adapter/    # 适配器层
```

---

## 复杂性追踪（Complexity Tracking）

### Constitution 违规表

| 违规项 | 为何必要 | 被拒绝的替代方案 |
|--------|---------|-----------------|
| CHK-ARCH-002 | Domain 层需要 Jackson 注解进行序列化 | 使用 DTO 转换（增加 50% 代码量） |
```

**tasks-template.md 核心结构**（Patra 定制版）：
```markdown
# [特性名称] 任务列表

**特性分支**: `###-feature-name`
**生成日期**: [日期]

---

## 任务格式说明

```
- [ ] [TaskID] [P?] [Layer?] [Story] 任务描述 in 文件路径
```

- **TaskID**: T001, T002, ...
- **[P]**: 并行任务标记（可同时执行）
- **Layer**: [Domain] [App] [Infra] [Adapter] [API]（对应六边形架构层）
- **Story**: [US1] [US2]（引用用户故事）

---

## 阶段 1: 设置（Setup）

- [ ] T001 创建项目结构 in patra-{service}/pom.xml
- [ ] T002 [P] 配置 Maven 依赖 in patra-{service}-domain/pom.xml
- [ ] T003 [P] 配置 Spring Boot 启动类 in patra-{service}-boot/Application.java

---

## 阶段 2: 基础（Foundation）

- [ ] T010 [Domain] 定义 Article 聚合根 in patra-{service}-domain/.../Article.java
- [ ] T011 [Domain] 定义 ArticleId 值对象 in patra-{service}-domain/.../ArticleId.java
- [ ] T012 [Domain] [P] 定义 ArticleCreated 领域事件 in patra-{service}-domain/.../ArticleCreated.java

---

## 阶段 3: 用户故事 1 (P1) - [US1 标题]

### 测试
- [ ] T020 [Domain] [P] [US1] 编写 Article 单元测试 in .../ArticleTest.java
- [ ] T021 [App] [P] [US1] 编写 ArticleService 单元测试 in .../ArticleServiceTest.java

### 实现
- [ ] T022 [Domain] [US1] 实现 Article 业务逻辑 in .../Article.java
- [ ] T023 [App] [US1] 实现 ArticleService 编排逻辑 in .../ArticleService.java
- [ ] T024 [Infra] [US1] 实现 ArticleRepository in .../ArticleRepositoryImpl.java
- [ ] T025 [Adapter] [US1] 实现 ArticleController in .../ArticleController.java

---

## 阶段 N: 润色（Polish）

- [ ] T999 更新 README.md
- [ ] T998 添加 package-info.java
- [ ] T997 运行 ArchUnit 测试验证架构
```

---

#### 🎯 Specs 层（工件输出）

**目录结构示例**：
```
.specify/specs/001-user-auth/
├── spec.md                      # ✅ 由 /speckit.specify 生成
├── plan.md                      # ✅ 由 /speckit.plan 生成
├── tasks.md                     # ✅ 由 /speckit.tasks 生成
├── research.md                  # 🔧 由 /speckit.plan 生成（Phase 0 调研）
├── data-model.md                # 🔧 由 /speckit.plan 生成（Phase 1 设计）
├── quickstart.md                # 📖 可选（快速开始指南）
├── contracts/                   # 📄 API 合约目录
│   ├── openapi.yaml
│   └── schemas/
└── checklists/                  # ✅ 质量检查清单
    ├── requirements.md          # 由 /speckit.specify 自动生成
    ├── architecture-compliance.md   # 由 /speckit.checklist 生成
    ├── code-quality.md          # 由 /speckit.checklist 生成
    └── test-coverage.md         # 由 /speckit.checklist 生成
```

---

## 3. 8 个 Slash Commands 详解

### 3.1 `/speckit.constitution` - 建立项目宪章

**文件位置**: `.claude/commands/speckit.constitution.md`

**功能**: 创建或更新项目架构宪章（`.specify/memory/constitution.md`）

**执行流程**：
```
1. 加载 constitution 模板
2. 识别所有 [ALL_CAPS] 占位符
3. 与用户交互式收集原则（或从 $ARGUMENTS 解析）
4. 验证原则的声明性、可测试性
5. 更新版本号（MAJOR/MINOR/PATCH）
6. 同步依赖模板（plan-template.md 的 Constitution Check）
7. 写入 constitution.md
```

**输入参数**：
```bash
/speckit.constitution  # 交互式模式，逐个询问原则

# 或提供描述：
/speckit.constitution "核心原则：1. 六边形架构 2. DDD 3. SSOT"
```

**输出文件**：
- `.specify/memory/constitution.md`（345 行）

**关键验证**：
- ✅ 原则必须使用 MUST/SHOULD/MAY（RFC 2119 风格）
- ✅ 行长度 ≤100 字符
- ✅ 避免模糊语言（如"快速"、"可扩展"）

---

### 3.2 `/speckit.specify` - 创建功能规格说明

**文件位置**: `.claude/commands/speckit.specify.md`（250 行）

**功能**: 从自然语言描述生成结构化的功能规格说明

**执行流程**（8 步）：
```
1. 解析用户描述（$ARGUMENTS）
2. 提取短名称（2-4 词，action-noun 格式）
   - 过滤停用词（i/want/to/add/the/for）
   - 示例："添加用户认证功能" → "user-auth"
3. 调用 create-new-feature.sh --json "用户描述"
   - 智能编号（检查远程/本地分支 + specs 目录）
   - 创建分支：001-user-auth
   - 创建目录：specs/001-user-auth/
4. 识别关键概念（actors/actions/data/constraints）
5. 生成规格说明（基于 spec-template.md）
   - 用户场景（Given/When/Then）
   - 功能需求（FR-001, FR-002...）
   - 领域模型（聚合根、值对象、领域事件）
   - 成功标准（可衡量指标）
6. 限制"需要澄清"标记 ≤3 个
7. 自动生成 requirements.md 检查清单
8. 报告完成状态 + 下一步建议（/speckit.clarify 或 /speckit.plan）
```

**输入示例**：
```bash
/speckit.specify 添加 PubMed 数据源采集功能，支持增量同步和全量同步，需要解析 XML 格式
```

**输出文件**：
- `specs/001-pubmed-datasource/spec.md`
- `specs/001-pubmed-datasource/checklists/requirements.md`
- Git 分支：`001-pubmed-datasource`

**质量控制**：
- ❌ 不包含实现细节（无框架/语言引用）
- ✅ 需求可测试
- ✅ 成功标准有具体指标（时间/百分比/计数）
- ✅ 最多 3 个"需要澄清"标记

---

### 3.3 `/speckit.clarify` - 解决规格模糊性

**文件位置**: `.claude/commands/speckit.clarify.md`（178 行）

**功能**: 通过交互式问答澄清规格说明中的模糊点

**执行流程**（7 步）：
```
1. 运行 check-prerequisites.sh 获取 FEATURE_DIR
2. 读取 spec.md
3. 识别"需要澄清"标记或模糊语言
4. 优先级排序：
   - 高：范围、安全/隐私
   - 中：UX、性能
   - 低：边界情况
5. 提出最多 5 个高优先级问题
6. 收集用户答案
7. 更新 spec.md：
   - 移除"需要澄清"标记
   - 增量添加到 "## 澄清" 章节
```

**输入示例**：
```bash
/speckit.clarify
# AI 自动识别模糊点并提问
```

**输出**：
- 更新后的 `spec.md`（移除模糊性）

**示例问题**：
```
Q1: "增量同步"的频率是多久？（每小时 / 每天 / 实时）
Q2: XML 解析失败时的处理策略？（跳过 / 重试 / 报警）
Q3: 全量同步是否需要支持断点续传？
```

---

### 3.4 `/speckit.plan` - 创建技术实施计划

**文件位置**: `.claude/commands/speckit.plan.md`（82 行，已修改）

**功能**: 基于规格说明生成技术实施计划和架构设计

**执行流程**（3 阶段）：
```
Phase 0: 宪章验证（Constitution Check）
1. 读取 spec.md 和 constitution.md
2. 验证功能需求是否违反宪章原则
3. 如有违规：
   - 记录到 Complexity Tracking 表
   - 说明为何必要
   - 列出被拒绝的替代方案
4. Gate Check: FAIL 则停止，PASS 则继续

Phase 1: 技术规划
1. 调用 setup-plan.sh 初始化 plan.md
2. 填写技术上下文：
   - 语言/框架版本（Patra 预填充）
   - 依赖库（Spring Boot/MyBatis-Plus/MapStruct）
   - 性能目标
3. 生成项目结构（6 层模块）
4. 创建 data-model.md（数据模型设计）
5. 创建 contracts/（API 合约）
6. （可选）创建 research.md（技术调研）

Phase 2: 重新验证
1. 基于 Phase 1 的设计重新执行 Constitution Check
2. 更新 Complexity Tracking
3. 报告完成状态 + 建议下一步（/speckit.tasks）
```

**输入示例**：
```bash
/speckit.plan
# 或指定技术栈：
/speckit.plan "使用 Redis 缓存，PostgreSQL 存储"
```

**输出文件**：
- `plan.md`（256 行）
- `research.md`（技术调研）
- `data-model.md`（数据模型）
- `contracts/`（API 合约目录）

**关键特性**：
- ✅ **Patra 技术栈预填充**：Java 25, Spring Boot 3.5.7
- ✅ **双重 Constitution Check**：Phase 0 + Phase 2
- ✅ **复杂性追踪**：记录所有架构违规及理由

---

### 3.5 `/speckit.tasks` - 生成任务分解

**文件位置**: `.claude/commands/speckit.tasks.md`（131 行）

**功能**: 基于 spec.md 和 plan.md 生成可执行的任务列表

**执行流程**（6 步）：
```
1. 运行 check-prerequisites.sh 验证 plan.md 存在
2. 读取上下文：
   - spec.md（用户故事、优先级）
   - plan.md（技术栈、项目结构）
3. 映射依赖关系：
   - 数据模型 → API 合约 → 用户故事
   - 测试 → 实现（TDD）
4. 按阶段分组任务：
   - 阶段 1: Setup（项目结构、依赖配置）
   - 阶段 2: Foundation（数据模型、仓储接口）
   - 阶段 3+: 用户故事（按 P1/P2/P3 优先级）
   - 最后阶段: Polish（文档、架构测试）
5. 标记并行任务 [P]
6. 添加六边形架构层标签 [Domain] [App] [Infra] [Adapter]
```

**输入示例**：
```bash
/speckit.tasks
```

**输出文件**：
- `tasks.md`（278 行）

**任务格式**：
```markdown
- [ ] T012 [P] [Domain] [US1] 定义 Article 聚合根 in patra-ingest-domain/.../Article.java
```

**任务组织原则**：
- ✅ **粒度适中**：LLM 可在 30 分钟内完成
- ✅ **TDD 优先**：测试任务先于实现任务
- ✅ **依赖明确**：基础阶段完成才进入用户故事
- ✅ **并行机会**：标记可同时执行的任务

---

### 3.6 `/speckit.analyze` - 跨工件一致性分析

**文件位置**: `.claude/commands/speckit.analyze.md`（185 行）

**功能**: 验证 spec/plan/tasks 三者之间的一致性和完整性（**只读分析**）

**执行流程**（6 个分析维度）：
```
1. 需求覆盖映射
   - spec.md 的每个 FR-* 是否有对应的 tasks.md 任务？
   - 生成覆盖汇总表

2. 术语一致性
   - 检查相同概念是否用不同名称
   - 示例：spec.md 说"文章"，plan.md 说"Article"

3. 数据实体一致性
   - plan.md 引用的组件在 spec.md 中定义了吗？
   - 示例：plan.md 说"ArticleRepository"，spec.md 必须定义 Article

4. 任务排序逻辑
   - 依赖关系合理吗？
   - 示例：T012 依赖 T010，但 T010 在后面

5. Constitution 对齐
   - 是否违反 MUST 原则？
   - 违规项标记为 CRITICAL

6. 模糊性和欠规格检测
   - "快速"、"可扩展"（缺乏可测量标准）
   - 动词短语缺少对象或结果
```

**输入示例**：
```bash
/speckit.analyze
```

**输出报告**（只读，不修改文件）：
```markdown
# 一致性分析报告

## 发现汇总
- 总需求数: 12
- 任务总数: 45
- 覆盖百分比: 95%
- 歧义计数: 3
- 关键问题: 1

## 发现表（最多 50 条）

| ID | 类别 | 严重性 | 位置 | 摘要 | 建议 |
|----|------|--------|------|------|------|
| F001 | Coverage Gap | High | spec.md:FR-007 | 无对应任务 | 添加 T050 实现 FR-007 |
| F002 | Constitution Conflict | CRITICAL | plan.md:45 | Domain 层引入 Spring | 移除或在 Complexity Tracking 说明 |
| F003 | Ambiguity | Medium | spec.md:SC-002 | "快速响应"缺乏可测量标准 | 改为"响应时间 <200ms" |

## 覆盖汇总表

| 需求键 | 是否有任务 | 任务 ID | 备注 |
|--------|-----------|---------|------|
| FR-001 | ✅ | T020, T022 | 完全覆盖 |
| FR-007 | ❌ | - | **缺失** |
```

**关键原则**：
- 🔒 **STRICTLY READ-ONLY**（只分析，不修改文件）
- 🎯 **最多 50 个发现**（防止令牌溢出）
- 🚨 **Constitution 冲突 = CRITICAL**

---

### 3.7 `/speckit.implement` - 执行任务实施

**文件位置**: `.claude/commands/speckit.implement.md`（135 行）

**功能**: 按 tasks.md 顺序执行代码实施

**执行流程**（8 步）：
```
1. 解析用户输入和功能目录路径
2. 检查清单完成状态验证：
   - 读取 checklists/*.md
   - 如果必需检查项未完成，询问是否继续
3. 加载实施上下文：
   - tasks.md（任务列表）
   - plan.md（技术栈、项目结构）
   - data-model.md（数据模型）
4. 验证和创建忽略文件（.gitignore, .dockerignore）
5. 提取任务阶段和依赖关系
6. 按阶段执行任务：
   - Setup → Tests → Core → Integration → Polish
   - 顺序任务：按序执行
   - 并行任务 [P]：可同步执行
7. 跟踪进度，处理错误：
   - 非并行任务失败 → 立即停止
   - 并行任务失败 → 继续执行其他任务
8. 验证完成状态：
   - 所有必需任务已完成？
   - 功能与规范匹配？
   - 测试通过？
```

**输入示例**：
```bash
/speckit.implement
# 或指定特性：
/speckit.implement 001-pubmed-datasource
```

**执行示例**：
```
✅ 阶段 1: Setup（3 个任务）
  ✅ T001 创建项目结构
  ✅ T002 配置 Maven 依赖
  ✅ T003 配置 Spring Boot

✅ 阶段 2: Foundation（4 个任务）
  ✅ T010 定义 Article 聚合根
  ✅ T011 定义 ArticleId 值对象
  ✅ T012 定义 ArticleCreated 领域事件
  ✅ T013 定义 ArticleRepository 接口

⏳ 阶段 3: 用户故事 1（6 个任务）
  ✅ T020 [并行] 编写 Article 单元测试
  ✅ T021 [并行] 编写 ArticleService 单元测试
  ✅ T022 实现 Article 业务逻辑
  ❌ T023 实现 ArticleService 编排逻辑（错误：依赖冲突）

🛑 停止执行（非并行任务失败）
```

**错误处理**：
- 🚨 **非并行任务失败** → 立即停止，报告错误
- ⚠️ **并行任务失败** → 继续执行，最后汇总失败项

---

### 3.8 `/speckit.checklist` - 生成质量检查清单

**文件位置**: `.claude/commands/speckit.checklist.md`

**功能**: 基于 spec.md 生成定制化的质量检查清单

**执行流程**（4 步）：
```
1. 读取 spec.md 和用户需求
2. 识别检查清单类型：
   - requirements（需求完整性）
   - architecture-compliance（架构合规）
   - code-quality（代码质量）
   - test-coverage（测试覆盖率）
3. 基于相应模板生成检查项
4. 写入 checklists/{type}.md
```

**输入示例**：
```bash
/speckit.checklist architecture-compliance
/speckit.checklist code-quality
/speckit.checklist test-coverage
```

**输出文件**：
- `checklists/architecture-compliance.md`（206 行）
- `checklists/code-quality.md`（306 行）
- `checklists/test-coverage.md`（368 行）

**architecture-compliance.md 示例**：
```markdown
# 架构合规性检查清单

## 模块结构验证
- [ ] CHK001 - 是否包含 6 个子模块（boot/api/domain/app/infra/adapter）？
- [ ] CHK002 - 每个模块的 pom.xml 依赖是否正确？

## 依赖方向验证
- [ ] CHK003 - Adapter 是否仅依赖 App 和 API？
- [ ] CHK004 - Domain 是否无任何框架依赖？
- [ ] CHK005 - Infra 是否仅被 Boot 依赖？

## Domain 层纯净性验证
- [ ] CHK006 - Domain 层是否只使用 JDK + patra-common-util + Lombok？
- [ ] CHK007 - Domain 层是否无 Spring Framework 注解？
- [ ] CHK008 - Domain 层是否无 MyBatis-Plus 依赖？
```

---

## 4. 核心模块功能分析

### 4.1 Memory 模块（项目记忆）

**定位**: 项目的"机构记忆"（Institutional Memory），跨所有特性和迭代持久化。

**核心文件**: `constitution.md`（345 行）

**功能**：
1. **架构原则定义**：
   - 5 大类原则（六边形架构、DDD、SSOT、测试、文档）
   - 每个原则包含：定义、强制规则、好坏实践、验证项

2. **质量门禁**：
   - `/speckit.plan` Phase 0: Constitution Check
   - `/speckit.analyze`: 验证 MUST 冲突

3. **版本管理**：
   - CONSTITUTION_VERSION: 1.2.0（MAJOR.MINOR.PATCH）
   - RATIFICATION_DATE: 2025-01-01
   - LAST_AMENDED_DATE: 2025-01-09

4. **治理规则**：
   - Constitution 优先级高于所有实践
   - 修订需文档化、批准和迁移计划
   - 所有 PR/审查必须验证合规

**为什么称为 Memory**：
- 代表项目的长期稳定原则
- 不随特性变化而变化
- 是所有决策的参考基准

---

### 4.2 Scripts 模块（基础设施层）

**定位**: 处理文件系统操作、Git 分支管理、JSON 数据传递。

**设计模式**: **Infrastructure Layer**（基础设施层）
- Scripts = 底层操作（文件/Git）
- AI = 高层逻辑（业务规则）

**核心功能**：

#### create-new-feature.sh
```bash
功能: 创建特性分支 + 初始化目录
输入: --json "用户描述"
输出: {"BRANCH_NAME":"001-user-auth", "SPEC_FILE":"specs/001-user-auth/spec.md"}

算法:
1. 智能编号（检查远程/本地分支 + specs 目录）
2. 生成短名称（过滤停用词，限制长度）
3. 创建 Git 分支
4. 创建 specs 目录
5. 复制 spec-template.md
```

#### check-prerequisites.sh
```bash
功能: 验证前置条件 + 返回路径
输入: --json [--require-tasks] [--include-tasks]
输出: {"FEATURE_DIR":"specs/001-xxx", "AVAILABLE_DOCS":["plan.md","tasks.md"]}

用途:
- /speckit.tasks: 验证 plan.md 存在
- /speckit.implement: 验证 tasks.md 存在
- /speckit.clarify: 验证 spec.md 存在
```

---

### 4.3 Templates 模块（结构化提示）

**定位**: AI 的"提示模板"（Prompt Templates），不是简单的文本替换。

**三类占位符**：

| 类型 | 示例 | 替换方式 |
|------|------|---------|
| **静态占位符** | `[###-feature-name]`<br>`$ARGUMENTS` | Bash 脚本替换 |
| **动态占位符** | `[聚合根名称]`<br>`[具体能力]` | AI 填充 |
| **指令性注释** | `<!-- 操作要求：此部分为占位符 -->` | 指导 AI 行为 |

**模板设计原则**：
1. **Few-shot Learning**: 提供示例，AI 模仿生成
2. **结构化输出**: 明确章节、格式要求
3. **必填 vs 可选**: 使用 `*（如果...则包含）*` 标记
4. **验证规则**: 内置质量检查

**Patra 的定制化**：
- spec-template.md: 添加"领域模型（DDD）"章节
- plan-template.md: 预填充 Patra 技术栈、Constitution Check
- tasks-template.md: 添加六边形架构层标签 `[Domain]` `[App]`

---

### 4.4 Specs 模块（工件集合）

**定位**: 特性规格的存储仓库，按编号组织。

**命名规范**：
```
###-short-name/
001-user-auth/
002-file-upload/
003-pubmed-datasource/
```

**工件生命周期**：
```
1. specify → spec.md
2. plan → plan.md + research.md + data-model.md + contracts/
3. tasks → tasks.md
4. checklist → checklists/*.md
5. implement → 实际代码（在 patra-{service}/ 中）
6. 完成后 → 归档（可选）
```

**归档策略**（建议）：
- 已完成的特性移动到 `specs/archive/`
- 或使用 Git 标签标记完成状态
- 保留工件用于回溯和审计

---

## 5. 工作流执行机制

### 5.1 完整工作流（标准路径）

```
┌─────────────────────────────────────────────────────────────┐
│ 第 0 阶段: 治理基础（一次性）                                │
└─────────────────────────────────────────────────────────────┘

/speckit.constitution
  → 定义 5 大架构原则
  → 生成 20+ 个验证项（CHK-*）
  → 版本号: 1.0.0
  ↓
constitution.md ✅

┌─────────────────────────────────────────────────────────────┐
│ 第 1 阶段: 需求规范（每个特性）                               │
└─────────────────────────────────────────────────────────────┘

/speckit.specify "添加 PubMed 数据源采集"
  → create-new-feature.sh
  → 智能编号: 001
  → 生成分支: 001-pubmed-datasource
  → 生成 spec.md（用户故事、功能需求、领域模型）
  → 生成 requirements.md 检查清单
  ↓
spec.md ✅
checklists/requirements.md ✅

（可选）/speckit.clarify
  → 交互式问答（最多 5 个问题）
  → 移除"需要澄清"标记
  ↓
spec.md（更新）✅

┌─────────────────────────────────────────────────────────────┐
│ 第 2 阶段: 技术规划                                          │
└─────────────────────────────────────────────────────────────┘

/speckit.plan
  → Phase 0: Constitution Check（验证 spec.md 是否违反宪章）
  → PASS → 继续
  → Phase 1: 技术规划
    - 填充技术栈（Java 25, Spring Boot 3.5.7）
    - 设计数据模型（data-model.md）
    - 定义 API 合约（contracts/）
    - 生成项目结构（6 层模块）
  → Phase 2: 重新验证 Constitution Check
  ↓
plan.md ✅
research.md ✅（可选）
data-model.md ✅
contracts/ ✅

（可选）/speckit.analyze
  → 验证 spec + plan 一致性
  → 识别覆盖缺口、术语冲突、Constitution 违规
  ↓
分析报告（只读）📄

┌─────────────────────────────────────────────────────────────┐
│ 第 3 阶段: 任务分解                                          │
└─────────────────────────────────────────────────────────────┘

/speckit.tasks
  → 读取 spec.md（用户故事）+ plan.md（技术栈）
  → 按阶段分组任务（Setup → Foundation → US1 → US2 → Polish）
  → 标记并行任务 [P]
  → 添加层标签 [Domain] [App] [Infra] [Adapter]
  ↓
tasks.md ✅

/speckit.checklist architecture-compliance
/speckit.checklist code-quality
/speckit.checklist test-coverage
  → 生成 Patra 特定检查清单
  ↓
checklists/*.md ✅

┌─────────────────────────────────────────────────────────────┐
│ 第 4 阶段: 代码实施                                          │
└─────────────────────────────────────────────────────────────┘

/speckit.implement
  → 检查清单完成状态验证
  → 按阶段执行任务（TDD 优先）
  → 阶段 1: Setup → 阶段 2: Foundation → 阶段 3+: 用户故事
  → 非并行任务失败 → 立即停止
  → 并行任务失败 → 继续执行
  ↓
代码实现 ✅
patra-{service}-domain/
patra-{service}-app/
patra-{service}-infra/
patra-{service}-adapter/

┌─────────────────────────────────────────────────────────────┐
│ 第 5 阶段: 质量验证（手动/自动）                              │
└─────────────────────────────────────────────────────────────┘

手动验证:
- 运行测试: mvn test
- 检查覆盖率: JaCoCo 报告
- 代码审查: 对照 checklists/*.md

自动验证（可选）:
- ArchUnit 测试（验证架构）
- SonarQube（代码质量）
- /speckit.validate（自定义命令）

┌─────────────────────────────────────────────────────────────┐
│ 完成 ✅                                                      │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 轻量级工作流（小功能/Bug 修复）

**当前问题**: 标准工作流对小功能过重

**建议新增**: `/speckit.quickstart`

```
/speckit.quickstart "修复 PubMed API 超时问题"
  → 合并 specify + plan + tasks 为一步
  → 生成简化的 tasks.md（无完整 spec.md 和 plan.md）
  → 直接进入 implement 阶段
  ↓
快速实施 ✅
```

---

### 5.3 并行执行机制

**tasks.md 中的并行标记**：
```markdown
## 阶段 3: 用户故事 1

### 测试
- [ ] T020 [P] [Domain] [US1] 编写 Article 单元测试
- [ ] T021 [P] [App] [US1] 编写 ArticleService 单元测试
```

**执行方式**：
```
/speckit.implement 检测到 [P] 标记
  → 同时启动 2 个子任务（并行执行）
  → Task 1: 编写 Article 单元测试
  → Task 2: 编写 ArticleService 单元测试
  → 等待两者完成后继续
```

**错误处理**：
- 并行任务 A 失败 → 继续执行 B
- 汇总失败项，最后报告

---

## 6. 集成架构图

### 6.1 三层架构视图

```
┌─────────────────────────────────────────────────────────────┐
│               Layer 1: Claude Code CLI                      │
│  - 执行环境                                                  │
│  - 工具调用（Read/Write/Bash/MCP）                           │
│  - Slash Commands 注册                                       │
└────────────────┬────────────────────────────────────────────┘
                 │
    ┌────────────┴──────────────┐
    │                           │
┌───▼────────────┐     ┌───────▼─────────┐
│ Layer 2:       │     │ Layer 2:        │
│ Slash Commands │◄───►│ Templates       │
│                │     │                 │
│ 8 个工作流定义  │     │ 7 个结构化提示   │
└───┬────────────┘     └─────────────────┘
    │
    │ 调用
    │
┌───▼────────────────────────────────────┐
│ Layer 3: Bash Scripts                  │
│ - 文件系统操作                          │
│ - Git 分支管理                          │
│ - JSON 数据传递                         │
└───┬────────────────────────────────────┘
    │
    │ 读写
    │
┌───▼────────────────────────────────────┐
│ Layer 4: File System State             │
│ - constitution.md（治理规则）           │
│ - specs/###-feature/（工件集合）        │
│   - spec.md, plan.md, tasks.md         │
│   - checklists/*.md                    │
└────────────────────────────────────────┘
```

### 6.2 数据流图

```
用户输入
  ↓
Claude Code CLI
  ↓
Slash Command (.claude/commands/speckit.xxx.md)
  ↓
AI 解释执行（Claude Sonnet 4.5）
  ↓
┌─────────────────┬─────────────────┬─────────────────┐
│ 调用 Bash 脚本   │ 读取模板         │ 调用 MCP 工具    │
│ create-new-     │ spec-template   │ serena          │
│ feature.sh      │ .md             │ (代码分析)       │
└────┬────────────┴────┬────────────┴────┬────────────┘
     │                 │                 │
     ▼                 ▼                 ▼
  JSON 输出        填充占位符        符号查找
     │                 │                 │
     └─────────────────┴─────────────────┘
                       │
                       ▼
               生成工件（spec.md / plan.md / tasks.md）
                       │
                       ▼
               写入文件系统（.specify/specs/###-feature/）
```

### 6.3 Constitution 验证流

```
constitution.md
  ↓ (定义 20+ 个 CHK-* 验证项)
  │
  ├─► /speckit.plan (Phase 0)
  │     ↓ 读取 constitution.md
  │     ↓ 验证 spec.md 是否违反 MUST 原则
  │     ↓ FAIL → 必须在 Complexity Tracking 说明理由
  │     ↓ PASS → 继续
  │
  ├─► /speckit.plan (Phase 2)
  │     ↓ 基于 Phase 1 设计重新验证
  │     ↓ 更新 Complexity Tracking
  │
  └─► /speckit.analyze
        ↓ 跨工件验证
        ↓ Constitution 冲突 = CRITICAL 级问题
        ↓ 阻止 /speckit.implement
```

---

## 7. 使用场景与最佳实践

### 7.1 适用场景

| 场景 | 推荐程度 | 理由 |
|------|---------|------|
| **新特性开发**（明确需求） | ⭐⭐⭐⭐⭐ | 完整工作流、质量保障 |
| **现有功能重构** | ⭐⭐⭐⭐☆ | 可用 spec.md 记录重构目标 |
| **微服务拆分** | ⭐⭐⭐⭐⭐ | plan.md 可设计服务边界 |
| **技术债务偿还** | ⭐⭐⭐☆☆ | tasks.md 可分解债务项 |
| **Bug 修复**（复杂） | ⭐⭐⭐☆☆ | 适合需要架构调整的 bug |
| **Bug 修复**（简单） | ⭐☆☆☆☆ | 流程过重，直接修改更快 |
| **探索性原型** | ⭐☆☆☆☆ | 需求不明确，不适合规格驱动 |

### 7.2 Patra 项目的最佳实践

#### 新数据源接入（完整流程）

```bash
# 1. 定义宪章（如果尚未完成）
/speckit.constitution

# 2. 创建功能规格
/speckit.specify "接入 EPMC（Europe PMC）数据源，支持增量同步和全文下载"
# 输出: specs/004-epmc-datasource/spec.md

# 3. 澄清模糊点（可选）
/speckit.clarify
# AI 提问: "增量同步频率？" "全文格式？（PDF/XML/HTML）"

# 4. 技术规划
/speckit.plan "使用 patra-registry 获取 EPMC Provenance 配置"
# 输出: plan.md, data-model.md, contracts/

# 5. 生成检查清单
/speckit.checklist architecture-compliance
/speckit.checklist test-coverage

# 6. 任务分解
/speckit.tasks
# 输出: tasks.md（按阶段、用户故事、层次组织）

# 7. 代码实施
/speckit.implement
# 按任务顺序实施，TDD 优先

# 8. 质量验证
mvn test
mvn verify  # 运行 ArchUnit 测试
```

#### 现有服务重构（简化流程）

```bash
# 1. 编写重构规格（无需新分支）
/speckit.specify "重构 patra-ingest ArticleService，拆分为 ArticleOrchestrator + ArticleCoordinator"

# 2. 技术规划（重点关注 Constitution Check）
/speckit.plan

# 3. 一致性分析（确保不破坏现有功能）
/speckit.analyze

# 4. 任务分解
/speckit.tasks

# 5. 增量实施（按任务逐个重构）
/speckit.implement
```

#### Bug 修复（轻量级模式，待实现）

```bash
# 使用建议新增的 /speckit.quickstart
/speckit.quickstart "修复 PubMed XML 解析器无法处理 CDATA 标签的问题"
# 直接生成简化的 tasks.md + 进入 implement
```

---

## 8. 常见问题与解决方案

### 8.1 配置相关问题

#### Q1: Constitution 与 Templates 不同步怎么办？

**症状**: constitution.md 新增了 CHK-ARCH-004，但 plan-template.md 中没有。

**解决方案**:
```bash
# 方案 A: 手动同步（临时）
# 1. 编辑 plan-template.md
# 2. 在 "Phase 0: Constitution Check" 章节添加新验证项

# 方案 B: 动态生成（推荐，需实现）
# 修改 /speckit.plan 命令：
# 1. 读取 constitution.md
# 2. 提取所有 CHK-* 项
# 3. 动态生成 plan.md 的 Constitution Check 章节
```

#### Q2: 删除的 `update-agent-context.sh` 是否影响功能？

**调研发现**: 该脚本用于同步上下文到其他 AI agents（Cursor/Copilot）。

**影响评估**:
- ✅ **对 Claude Code 无影响**（Claude 直接读取文件）
- ⚠️ **如需多 Agent 支持**（需恢复或替代）

**替代方案**:
```bash
# 创建新脚本: sync-to-cursor.sh
#!/bin/bash
# 从 plan.md 提取技术栈 → .cursorrules
```

---

### 8.2 工作流相关问题

#### Q3: 小功能修改流程太重怎么办？

**症状**: 修改一个配置项需要走完整 specify → plan → tasks 流程。

**解决方案**:
```bash
# 建议新增命令: /speckit.quickstart
# 适用场景: 单文件修改、小 bug 修复
# 合并 3 个阶段为 1 步，直接生成 tasks.md
```

#### Q4: 如何在现有特性基础上增量开发？

**场景**: specs/001-user-auth 已完成，现在要添加"OAuth 登录"。

**方案 A**: 创建新特性
```bash
/speckit.specify "为用户认证添加 OAuth 2.0 支持"
# 生成 specs/005-oauth-login/
```

**方案 B**: 扩展现有特性（手动）
```bash
# 1. 手动编辑 specs/001-user-auth/spec.md
#    添加新用户故事: US4 - OAuth 登录
# 2. 重新运行 /speckit.plan
# 3. 重新运行 /speckit.tasks
```

---

### 8.3 集成相关问题

#### Q5: 如何集成到 CI/CD？

**方案**: GitHub Actions 工作流

```yaml
# .github/workflows/spec-kit-validation.yml
name: Spec-Kit Validation

on:
  pull_request:
    branches: [main]

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: 验证特性分支命名
        run: |
          BRANCH=${{ github.head_ref }}
          if [[ ! $BRANCH =~ ^[0-9]{3}-.+ ]]; then
            echo "错误: 分支名必须符合 ###-feature-name 格式"
            exit 1
          fi

      - name: 验证工件完整性
        run: |
          FEATURE_NUM=$(echo ${{ github.head_ref }} | cut -d'-' -f1)
          FEATURE_DIR=".specify/specs/${FEATURE_NUM}-*"

          if [ ! -f "$FEATURE_DIR/spec.md" ]; then
            echo "警告: 缺少 spec.md"
          fi

          if [ ! -f "$FEATURE_DIR/tasks.md" ]; then
            echo "错误: 缺少 tasks.md"
            exit 1
          fi

      - name: 运行 ArchUnit 测试
        run: mvn test -Dtest=ArchitectureTest
```

#### Q6: 如何与 CLAUDE.md 协同？

**CLAUDE.md 的作用**: 定义 AI 助手的行为模式（组织者优先、三次原则）

**Spec-Kit 的作用**: 提供结构化工作流

**协同机制**:
```
CLAUDE.md（如何思考）:
  - 组织者 > 执行者
  - 三次原则（失败 3 次 → 策略转换）
  - 强制规则（读 README + 先问）

Spec-Kit（如何执行）:
  - 8 个 slash commands
  - Constitution-Driven Development
  - 质量门禁（检查清单）

协同示例:
用户: "添加新数据源"
  → CLAUDE.md: 判断复杂度 → 委派 Spec-Kit
  → Spec-Kit: 执行 /speckit.specify → /speckit.plan → /speckit.tasks
  → CLAUDE.md: 监督质量 → 如果失败 3 次，调用 web-research-specialist
```

---

## 9. 总结与建议

### 9.1 Spec-Kit 的核心价值

1. ✅ **强制架构合规**：Constitution Check 确保每个特性符合架构原则
2. ✅ **知识固化**：架构原则编码到 constitution.md，技术栈预填充到模板
3. ✅ **工作流自动化**：从自然语言 → 可执行任务（全程引导）
4. ✅ **质量内建**：设计阶段验证、实施前分析、实施中检查清单
5. ✅ **可追溯性**：所有决策有文档记录（spec/plan/tasks）

---

**报告完成时间**: 2025-11-09
**调研工时**: 约 6 小时（3 个 subAgents 并行 + 综合分析）
**报告篇幅**: 约 15,000 字
**置信度**: 高（基于代码库探索 + 官方文档 + 架构分析）
