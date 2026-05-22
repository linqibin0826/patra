# Patra Monorepo

医学出版物数据平台 —— 采集、解析、存储来自 PubMed / EPMC / Crossref 等 10+ 外部数据源的文献与期刊数据。

## Workspace 布局

| 顶层目录 | 职责 | 技术栈 |
|---|---|---|
| **linqibin-commons/** | 个人通用 Spring Boot 基建（commons + 11 starter） | Java 25 / Spring Boot 4 / Gradle |
| **patra-starters/** | Patra 项目专属 starter（expr / provenance） | Java 25 / Spring Boot 4 |
| **patra-api/** | 后端业务服务（微服务 + 六边形 + DDD） | Java 25 / Spring Boot 4 |
| **patra-portal/** | 前端管理控制台（工程基建在 v0.4 启动，见 [v0.4 Portal Foundation](docs/patra/release-specs/)） | Next.js 15 / React 19 / TypeScript 5 / Tailwind v4 |
| **patra-infra/** | 本地基建配置（Docker Compose、launchd） | Docker Compose / Bash / launchd |

辅助目录：

- `build-logic/` —— Gradle convention plugins 与统一依赖管理（Kotlin DSL）
- `docs/` —— 项目文档（spec / plan / 决策记录，自有 journal 主题）
- `scripts/` —— 一次性运维与数据脚本（业务工具，不在 Gradle 构建链）
- `.github/` —— CI workflow（`ci.yml`）+ PR/Issue 模板 + [CI 优化说明](.github/CI_OPTIMIZATION.md)
- `.claude/` —— Claude Code 工作区配置（settings / hooks / worktrees）

## Gradle 坐标

| 子树 | group |
|---|---|
| linqibin-commons/* | `dev.linqibin.commons` |
| patra-starters/* + patra-api/* | `dev.linqibin.patra` |

**边界约束**：`linqibin-commons/*` 由 `linqibin.boundary-check` convention plugin 强制禁止依赖任何 `:patra-*` 模块（保留未来抽离独立 repo 的通路）。验证方式：`./gradlew :<commons-module>:checkBoundary`。

## 新增模块规则（决策 E）

Gradle path `:parent:child` 必须与物理目录 `parent/child/` **一一对应**：

- ✅ `:linqibin-commons:linqibin-spring-boot-starter-core` → `linqibin-commons/linqibin-spring-boot-starter-core/`
- ✅ `:patra-api:patra-registry:patra-registry-domain` → `patra-api/patra-registry/patra-registry-domain/`
- ❌ 禁止扁平 path + `projectDir` 重映射的拼装（即不要再用以前的 `includeAt` 副作用让 Gradle path 与物理目录分离）

新增模块时：

1. 在物理目录创建 `parent/child/`
2. `settings.gradle.kts` 中用 `includeAt(":parent:child", "parent/child")` 注册
3. 子模块名遵循 `patra-<service>-<layer>`（layer ∈ domain/api/app/infra/adapter/boot）
4. apply 对应的 hexagonal convention plugin（见 `build-logic/src/main/kotlin/`）

> 该规则是项目宪法（决策 E，不可逆），违反会被 CodeRabbit 与 `settings.gradle.kts` 的 path_instructions 拦截。

## 常用命令

```bash
./gradlew clean assemble           # 构建全部模块（不跑测试 / lint）
./gradlew clean build              # 构建 + 测试 + SpotBugs + Spotless + JaCoCo
./gradlew check                    # 仅运行测试 + 代码质量（含 spotbugsMain + spotlessCheck）
./gradlew spotlessApply            # 一次性格式化全部 Java + *.gradle.kts
./gradlew spotbugsMain             # 跑 SpotBugs 静态分析（HTML 报告在 build/reports/spotbugs/）
./gradlew :patra-api:patra-catalog:patra-catalog-boot:bootRun  # 启动 catalog 服务
./gradlew :<commons-module>:checkBoundary                      # 校验依赖边界
./gradlew publishToMavenLocal      # 发布到 ~/.m2 本地仓库
```

## 工具链入口

### Pre-commit hooks

`.pre-commit-config.yaml` 在 `git commit` 时本地拦截。配置请用 `pre-commit install` 一次性安装。

| 触发时机 | Hook | 行为 |
|---|---|---|
| 每次 commit | 通用 hygiene（`pre-commit-hooks`）| 拦截合并冲突标记、YAML/JSON/TOML 语法、 > 2 MB 大文件、私钥、EOL/EOF 不规范 |
| 每次 commit | `gitleaks` | 扫描 secret/token 泄漏 |
| 涉及 `*.java` / `*.gradle.kts` | `spotless-apply` | 自动跑 `./gradlew spotlessApply -q --no-daemon`，把格式问题原地修掉 |
| 每次 `commit-msg` | `commitlint` | 校验 commit message 符合 `@commitlint/config-conventional`（详见下方 commitlint） |

跳过 hooks 仅在用户明确授权时使用 `--no-verify`，正常流程不要绕过。

### Spotless（格式化）

- 由 `linqibin.spotless` convention plugin 应用到所有 Java module
- 规则：缩进 2 空格 / import 顺序 / 行尾 LF / EOF 换行 / 去尾空白（与 `.editorconfig` 一致）
- 本地修：`./gradlew spotlessApply`
- CI 校验：`./gradlew spotlessCheck`，失败**阻断 PR**（决策 C）

### SpotBugs（静态分析）

- 由 `linqibin.spotbugs` convention plugin 应用到所有 Java module
- 配置：`effort = MAX` / `reportLevel = LOW` / `ignoreFailures = false` / **不分析 test 代码**
- 报告输出：`build/reports/spotbugs/main.html` + `.xml`
- CI 校验：包含在 `./gradlew check` 中，失败**阻断 PR**（决策 C）

**告警处理流程**：

1. **优先修代码** —— 90% 的 SpotBugs 告警是真实问题（NPE、资源未关、并发问题等）
2. **确认是误报或框架约定后** —— 在 `spotbugs-exclude.xml` 加 `<Match>` 块，**必须写注释说明排除理由**（参考现有文件的分类注释组织）
3. **禁止**：直接 `@SuppressFBWarnings` 散落在业务代码中、或在 exclude 文件加不带注释的规则

### commitlint（commit 规范）

`commitlint.config.cjs` 强制 Conventional Commits：

- **type**（必填）：`feat / fix / docs / refactor / test / chore / perf / style / ci / build`
- **scope**（可选）：小写
- **subject**（必填）：非空、不以句号结尾
- **header**：≤ 100 字符
- 触发：每次 `git commit`（commit-msg 阶段）

PR squash-merge 时 GitHub 会用 PR title 作为 commit message，所以 **PR 标题也要符合 conventional commits**。

### CodeRabbit AI Review

`.coderabbit.yaml` 配置（PAP-22 深化版）：

- **触发**：每次 push 到 PR 分支自动 review（drafts PR 不触发；title 含 `WIP` / `DO NOT MERGE` / `SKIP REVIEW` 不触发）
- **profile**：`assertive`（扩大设计风险覆盖）+ `request_changes_workflow=false`（**不阻断 PR**，决策 C）
- **语言**：中文 review；engineer 语气，禁礼貌赞美 + 凑评论
- **分层 prompt**：按 `**/patra-*-{domain,app,adapter,infra}/**/*.java` 下达不同的 review 指南（domain 禁框架依赖、app 不放业务规则、adapter 关注 DTO 双向耦合等）
- **Linear KB**：自动拉取 PR 描述中链接的 PAP-XX issue 上下文做 AC 验证（需在 `app.coderabbit.ai/integrations` 完成一次性 OAuth 授权）
- **自动打标**：根据变更路径自动应用 `area:api` / `area:portal` / `area:infra` / `area:migration` / `area:test`
- **CI 盲区工具**：CodeRabbit 兜底跑 `markdownlint` / `yamllint` / `shellcheck` / `hadolint` / `actionlint` / `gitleaks`（这些 CI 没有）
- **TDD 守护**：`finishing_touches.docstrings` / `unit_tests` 都关闭，防止 AI 事后补测试破坏 Red-Green-Refactor
- **交互命令**：在 PR 评论中 `@coderabbitai` + 自然语言指令（如 `@coderabbitai resolve`、`@coderabbitai full review`）

### 测试体系（PAP-19 基线）

- **测试类型 → JVM Test Suite**：`test`（unit）/ `integrationTest` / `e2eTest`，由 `linqibin.java-base` convention plugin 统一定义
- **跨 sourceSet 共享**：`src/testFixtures/`（`java-test-fixtures` plugin），不再用 `sourceSet.output` 链式 hack
- **命名规范**：见 `.claude/rules/` 与 `patra-backend:test-checker` agent
- **跑测试**：
  - 单元：`./gradlew test` 或 `./gradlew :<module>:test`
  - 集成：`./gradlew integrationTest`（启 Testcontainers）
  - E2E：`./gradlew e2eTest`
  - 一把梭：`./gradlew check`

### CI 流水线（`.github/workflows/ci.yml`）

PAP-24 提速版，5 个 job 拆分 + 容器预拉取 + 全套 Gradle cache：

| Job | 内容 |
|---|---|
| **unit-tests** | matrix 4 个分片并行（`commons-and-starters` / `registry` / `ingest` / `catalog-and-misc`），每个分片跑 `check`（test + spotbugs + spotless），不出 JaCoCo 报告 |
| **integration-tests** | 单 job，启 Testcontainers，提前并发预拉取镜像 |
| **e2e-tests** | 单 job，端到端 |
| **coverage-and-upload** | 聚合 unit + integration 的 `jacoco-exec`，产 JaCoCo 聚合报告，上传 Codecov |
| **required-check** | 用 `re-actors/alls-green` 聚合前 4 个 job 的结果，作为 GitHub Required Status Check 的稳定锚点 |

- **缓存层**：Gradle build cache + dependency cache + configuration cache + wrapper verify + 容器镜像预热
- **路径过滤**：`**.md` / `docs/**` / `.gitignore` / `.editorconfig` / `LICENSE` 不触发 CI
- **基线时长**：小改动 ~3-5 min / 大改动 ~8-10 min
- 详细配置说明：[`.github/CI_OPTIMIZATION.md`](.github/CI_OPTIMIZATION.md)

### 分支保护（PAP-15）

- main 分支：禁止直推（force push 也禁）、Required Status Checks 绑定 `CI / required-check`（聚合 unit/integration/e2e/coverage 全绿）、Linear history 强制
- admin bypass 允许（单人项目应急用，正常流程仍走 PR）

## 文档

- 设计规格：`docs/patra/specs/`
- 实现计划：`docs/patra/plans/`
- Release Spec：`docs/patra/release-specs/`（v0.3 Dev-Ready 已完成 / v0.4 Portal Foundation 进行中）
- 文档样式：`docs/patra/styles/journal.css` + `plan-extras.css`

## 服务说明

各微服务的架构与启动方式参见各自的 README：

- [`patra-api/README.md`](patra-api/README.md) —— 后端微服务总览（registry / ingest / catalog / object-storage / gateway）
- [`patra-portal/README.md`](patra-portal/README.md) —— 前端门户（开发中）
