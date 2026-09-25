# AGENTS.md

## 产品定义

**Patra** — 医学出版物数据平台，采集、解析、存储来自 PubMed / EPMC / Crossref 等 10+ 外部数据源的文献和期刊数据。

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

## 下游消费者（linqibin-commons 兼容红线）

**"零历史包袱 / 禁止考虑向后兼容"对 `linqibin-commons/` 不再成立**——它有仓库外的下游消费者，改动前必须评估影响：

1. **super-nb-platform**（`~/Projects/Products/super-nb/super-nb-platform`）通过 `scripts/bootstrap-commons.sh` clone 本仓库、checkout `gradle.properties` 中 `patraRef` 钉住的 commit、`publishToMavenLocal` 后以 `dev.linqibin.commons:*:0.1.0-SNAPSHOT` 消费 5 个模块：commons-core、starter-core、starter-web、**starter-jpa**、starter-test。
2. **高危耦合面（starter-jpa）**：`BaseJpaEntity` 家族的字段/列名/类型已物化进 super-nb 的 Flyway baseline 且 `ddl-auto=validate`——改列契约会让下游启动直接失败；`Jackson3JsonFormatMapper` 的 String/Object 透传行为被下游 JSONB `record_remarks` 数据依赖——回退或改动会让下游读数据 500。`commons-core` 的 CQRS（Command/CommandHandler/CommandBus）与错误体系（DomainException/StandardErrorTrait）也被大量 import。
3. **分支操作前检查 patraRef**：删除或强推分支前，确认 super-nb 的 `patraRef` 没有钉在该分支的 commit 上（曾钉过非 main 分支 `fix/jackson3-string-passthrough`）。commons 修复合入 main 后，应提醒用户把下游 `patraRef` 更新到 main commit。
4. 改动 `linqibin-commons/` 时，视同维护一个有外部用户的库：行为变更、删除公开 API、改自动配置默认值，都要先看下游用法（可 grep super-nb-platform 的 import）。

## 安全红线（公开仓库）

**本仓库 `linqibin0826/patra` 是 GitHub 公开仓库**——任何提交内容（含 git 历史）都全互联网可读且永久留痕。

1. 禁止提交真正敏感的密钥：外部数据源 API key（PubMed / EPMC / Crossref 等）、个人访问令牌、生产数据库/对象存储凭据、私钥证书。这类密钥一律走环境变量或外部 secret 注入，绝不进仓库。
2. 一旦发现敏感密钥被提交，立即轮换作废，再清理跟踪——"反正服务连不上"不是不轮换的理由。
3. **已知例外**：`patra-infra/docker/.env`、`.env.dev` 含 dev 默认凭据且有意随仓库提交（Mac mini 靠 git pull 同步），对应服务仅在 tailscale 内网暴露，公网打不到端口。详见 `patra-infra/CLAUDE.md`。
4. 写文档、注释、commit message、Issue/PR 时不要粘贴真实凭据、内网 IP 以外的隐私信息。

## TDD 开发模式（强制）

所有功能开发遵循 Red-Green-Refactor 循环（语言无关，BE/FE 均适用）：

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

## PR 与代码评审

仓库有三位正式 AI reviewer，分工互补：

- **CodeRabbit**：行级 nitpick + lint/安全工具聚合（gitleaks / trivy / fbinfer 等）+ Linear AC 对齐。不自动评审（`auto_review.enabled: false`），由编码 agent 在 PR 评论区发 `@coderabbitai review` 触发。
- **Codex**（`chatgpt-codex-connector`）：PR 以非 draft 打开或转 ready 时自动评审，不占 CodeRabbit 额度。只报 P0/P1，评审关注点见下方 `Code Review Rules`。
- **Claude**（GitHub Action，`.github/workflows/claude-review.yml`，评论身份 `github-actions[bot]`）：与 Codex 同时机自动评审，专看六边形分层、DDD 领域逻辑、项目规范与测试；用订阅 OAuth token（secret `CLAUDE_CODE_OAUTH_TOKEN`），模型在 workflow 的 `--model` 指定。

规则：

- **PR 粒度：Issue 是跟踪单位，PR 按技术栈合并**：每个版本最多两个 PR（后端一个、前端一个），design / 纯文档 Issue 不单独开 PR；release spec、设计简报、设计快照、工程 spec、plan 随同版本第一个代码 PR 提交（仍写在 feature branch 上，不直接提交 main）；PR 描述逐行列出 `Closes PAP-xx` 关闭所承载的全部 Issue。设计文档由用户在写代码前本地让 Codex 评审，不为评审开 PR。详见 `patra:release-planning`。
- **评审由编码 agent 驱动，用户不参与**：开发期 PR 挂 `draft`（都不评）→ 完工转 ready：Codex 与 Claude Action 自动首评，编码 agent 发 `@coderabbitai review` 触发 CodeRabbit 首评。
- **只做首评，不复评**：CodeRabbit 对 0 star 公开仓库的 OSS 额度仅**每小时 1 次** PR 评审（滚动窗口），复评几乎必然限流。修复是否正确由编码 agent 自行验证 + CI 兜底，不再 @ 任何 reviewer 复评。首评被限流时，等距上次评审满 60 分钟后再触发一次。
- **必启 Monitor**：每次 `gh pr create` 后，同一工作会话内立即启动 Monitor 并绑定该 PR，覆盖**两条流**（AI reviewer + 人工），持续到 PR 合并或关闭。
- **处理状态**：对每条 review 意见必须在 PR 评论中明确给出处理状态——`已修复`（附 commit SHA）/ `不修复`（附明确理由）。

## Code Review Rules

供 Codex PR 评审使用。行级风格、lint、安全扫描由 CodeRabbit 与 CI 覆盖，这里只列本仓库特有、出错代价高的行为。

### linqibin-commons 下游兼容

- `linqibin-commons/` 有仓库外下游（super-nb-platform），改动它时，下列变更视为 P1：`BaseJpaEntity` 家族的字段名、列名、类型变化（下游 Flyway baseline + `ddl-auto=validate`，启动即失败）；`Jackson3JsonFormatMapper` 对 String/Object 的透传行为变化（下游 JSONB 读数据会 500）；删除或改签名 `commons-core` 的 CQRS（Command / CommandHandler / CommandBus）与错误体系（DomainException / StandardErrorTrait）公开 API。
  安全做法：保持契约不变；确需变更时 PR 描述写明下游影响与 `patraRef` 更新计划。
- `linqibin-commons/` 以外的模块是绿地代码：不要因为缺少向后兼容、数据迁移或 deprecated 过渡而提意见，直接替换旧实现是预期做法。

### 公开仓库密钥

- 本仓库公开，提交外部数据源 API key、个人访问令牌、生产数据库 / 对象存储凭据、私钥证书一律 P0。
  例外：`patra-infra/docker/.env`、`.env.dev` 中的 dev 默认凭据是有意提交的（服务仅在 tailscale 内网暴露），不要报。

### 异常传播

- 应用层把携带 `StandardErrorTrait` 的 `DomainException` 捕获后包装成 `ApplicationException` 或通用异常，会丢失语义特征、让本应 404/409/422 的请求变成 500，视为 P1。
  安全做法：领域异常直接传播，只对意外异常用 `ApplicationException` 包装。
- 调用下游服务时直接捕获 `RestClientException` 会绕过统一错误语义。
  安全做法：捕获 `RemoteCallException`，按 `getErrorTraits()` 转换为领域异常。

## Workspace Layout

Patra 工作区包含以下子项目：

| 目录 | 用途 | 技术栈 |
|------|------|--------|
| **patra-api** | 后端服务（微服务 + 六边形 + DDD） | Java 25 / Spring Boot 4 / Gradle |
| **patra-portal** | 前端门户（管理控制台） | Next.js 15 / React 19 / TypeScript 5/6 strict / Tailwind v4 / shadcn/ui |
| **patra-learn** | 学习站（内部 onboarding / 回顾，地铁线路图式课程） | Next.js 15 / React 19 / TypeScript 5/6 strict / Tailwind v4 |
| **patra-infra** | 基建配置（Docker Compose、DB 脚本） | Docker Compose / Bash / launchd |

## JetBrains MCP 语义工具

代码的语义级操作优先用 JetBrains MCP（`mcp__jetbrains__*`，依赖 IntelliJ IDEA 已打开本项目）；文本级读写用内置 Read / Grep / Edit。

| 任务 | JetBrains 工具 |
|------|---------------|
| 按名查找类 / 方法 / 字段 | `search_symbol` |
| 查看符号声明、类型与文档 | `get_symbol_info` |
| 查调用方 / 调用链 | `analyze_calls` |
| 重命名符号（同步所有引用） | `rename_refactoring` |
| 改完检查编译错误与告警 | `get_file_problems` |
| 按项目代码风格格式化 | `reformat_file` |
| 增量编译验证 | `build_project` |

重命名一律走 `rename_refactoring`，禁止用文本替换代替。IDEA 未打开或 MCP 不可用时退回内置工具，不阻塞工作。
