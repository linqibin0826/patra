# 工具层重组 Implementation Plan（PAP-79 ~ PAP-82）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `linqibin-commons/` + `patra-starters/` 重组为 `patra-tool/` 下 12 个能力模块，去掉 `linqibin` 命名，依赖规则 R1–R5、自动配置模板、默认配置归属全部由 `./gradlew check` 守住，6 个服务只扫自己的包。

**Architecture:** 技术能力进 `patra-tool/`（core 纯 Java，其余 Spring starter），业务代码各回各家（patra-common 合一、provenance / expr 回 ingest）；build-logic 的 `patra.tool-module` 插件施加 R1 / R2 / R4，`patra.hexagonal-domain` 施加 R3，根项目接 dependency-analysis 插件做 R5；observability 与 launch 用 `EnvironmentPostProcessor` 出默认配置。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Gradle 9.5 Kotlin DSL + 约定插件、JUnit 5 + AssertJ、Gradle TestKit（build-logic 首次引入）、dependency-analysis-gradle-plugin 3.19.2。

**Spec:** `docs/patra/specs/2026-10-15-tool-reorg-design.md`（下称 spec；本 plan 只引用它的表，类清单、键映射、规则定义以 spec 为准）。Release spec：`docs/patra/release-specs/v0.10-tool-reorg.md`。

## Global Constraints

- **开工前置**：PR #234 已合并到 main；`git fetch origin main && git merge origin/main` 合进 `feat/v0.10-tool-reorg-api`；**在主检出上做**（JetBrains MCP 只认 IDEA 打开的主检出；本 plan 与 spec 写在 worktree，执行时先在主检出 `git checkout feat/v0.10-tool-reorg-api`，把 worktree 上的 spec / plan 提交带过去）。
- 改名一律用 `mcp__jetbrains__rename_refactoring`（先 `ToolSearch select:mcp__jetbrains__rename_refactoring,mcp__jetbrains__search_symbol,mcp__jetbrains__get_file_problems`），禁止文本替换；MCP 调用失败（IDEA 未开、超时）才退回让用户在 IDEA 里手动 Rename package。
- 改了任何 `*.gradle.kts` 要提醒用户在 IDEA 点「加载 Gradle 更改」；改依赖后重跑 `./gradlew dumpModuleGraph` 并提交 `patra-infra/cd/module-graph.json`。
- 每个任务结束跑 `./gradlew check`（不是只跑 `test`；含 spotless、SpotBugs、以及已挂上的 R1–R5），`spotlessApply` 改了文件要 `git add -u` 重新暂存。`check` 全仓约 10 分钟以上，用 `run_in_background` 跑。
- 代码规范：Google Java 风格、`///` Javadoc（长链接独占一行、100 列内）、禁止全限定名、record ≤ 4 参数用 `of()`、不可变集合 `List.of()` / `List.copyOf()`。
- 提交：subject 中文动词起头（commitlint 拒绝大写开头），形如 `refactor(tool): 把错误解析引擎并入 patra-tool-web (PAP-79)`；只 `git add` 自己改的路径（共享工作区）；**不推送、不开 PR、不合并**——这三件只在用户本轮原话授权后做；每个任务的 commit 在用户验收该任务后执行（用户可一次性授权"本分支按任务提交"）。
- TDD：新行为（R1–R5 检查、EPP、`CqrsAutoConfiguration`、`SimpleCommandBus` 重复 handler、契约测试）先写失败测试；纯搬迁 / 改名不要求。
- 不做：新能力、jMolecules、动 Hutool / Jackson 在 domain 的使用、动 ingest 去留、修 catalog 不变量校验与 ingest 事件丢失两个 bug、保留任何兼容层。
- `.claude/` 下的规则与 skill 在 worktree 会话里改不了；在主检出做（Task 32）。
- 工具模块的 Spring 依赖写法：`patra.spring-starter` 统一给 `annotationProcessor(spring-boot-configuration-processor)`、`annotationProcessor(spring-boot-autoconfigure-processor)`、`compileOnly(jakarta.validation-api)`、`testImplementation(spring-boot-starter-test)`。

## Review Focus

1. **服务 yml 覆盖 EPP 默认值**：`application.yml` 写 `logging.config` 或 `management.endpoints.web.exposure.include` 时必须赢过 observability 出的默认值；`PatraApplication` 放进 `defaultProperties` 的键不能被 EPP 的 `addOrMerge` 覆盖。→ Task 24 的 `ObservabilityDefaultsPostProcessorTest` 两条用例。
2. **命令总线：同一命令两个 handler**：现在静默覆盖，改后启动失败且信息点名两个类。→ Task 29 的 `SimpleCommandBusTest.should_fail_when_two_handlers_claim_same_command`。
3. **可选协作缺类时不炸**：observability 没有 web、jpa 没有 web、cqrs 没有 micrometer、security 没有 jpa / http-client 时上下文要能起来。→ Task 18 / 20 / 24 / 25 契约测试的 `FilteredClassLoader` 用例。
4. **`@WebMvcTest` 切片自动带上错误处理与 Long→String**：删掉手写 `@ImportAutoConfiguration` 后，4 个 adapter 的切片 IT 仍要拿到 `ProblemDetail` 与字符串化的 Long。→ Task 19 跑 4 个 adapter 模块的 `integrationTest`。
5. **扫描收窄后 ingest 仍能扫到自己的 `ProcessorRegistry`、catalog 的 `exclude` 删掉后仍能启动**：→ Task 29 的 `--debug` 启动核对。

---

## 任务总览与顺序

```
A  PAP-79 命名与模块就位         Task 1 – 12
B  PAP-80 规则实现（不挂 R4/R5） Task 13 – 16
C  PAP-81 模板与默认配置         Task 17 – 26
B' PAP-80 收尾（R4 挂 check、R5 探针、D3 实测） Task 27 – 28
D  PAP-82 扫描收窄与收尾         Task 29 – 32
```

spec 说 PAP-80 与 PAP-81 可并行；本 plan 串行执行，R4 在模板改完后再挂 `check`（否则 12 个模块全红），R5 在依赖整理完后再探针。

---

### Task 1: 开工前置与基线

**Files:**
- Modify: 无（只做分支与基线）

**Interfaces:**
- Produces: 主检出位于 `feat/v0.10-tool-reorg-api`，含 `origin/main`（#234 已合）、spec、plan；`./gradlew check` 基线绿。

- [ ] **Step 1: 核实 #234 已合并**

Run: `gh pr view 234 --json state,mergedAt --jq '{state,mergedAt}'`
Expected: `state: MERGED`。不是就停下，告诉用户，本 plan 不能开始。

- [ ] **Step 2: 主检出切分支并合入 main**

```bash
git -C /Users/linqibin/Projects/Products/patra fetch origin main
git -C /Users/linqibin/Projects/Products/patra checkout feat/v0.10-tool-reorg-api
git -C /Users/linqibin/Projects/Products/patra merge --no-edit origin/main
```
Expected: 无冲突（分支只比 main 多 release spec 的提交）。spec 与 plan 两个文件若还只在 worktree，先在 worktree 提交（`docs(tool-reorg): 新增 v0.10 工程 spec 与 plan (PAP-79 ~ PAP-82)`）再 `git merge` 进主检出的分支。

- [ ] **Step 3: 基线检查**

Run: `./gradlew check`（后台，约 10 分钟）
Expected: BUILD SUCCESSFUL。记下耗时作为后续对照。

- [ ] **Step 4: 让用户在 IDEA 里确认项目已在该分支并完成 Gradle 同步**（JetBrains MCP 的 rename 依赖索引完整）。

---

### Task 2: 约定插件改名 `linqibin.*` → `patra.*`，group 统一 `dev.patra`

**Files:**
- Rename（`git mv`）：`build-logic/src/main/kotlin/linqibin.<x>.gradle.kts` → `patra.<x>.gradle.kts`，共 18 个文件：`boundary-check`、`dependency-management`、`hexagonal-adapter`、`hexagonal-api`、`hexagonal-app`、`hexagonal-boot`、`hexagonal-domain`、`hexagonal-infra`、`java-base`、`java-library`、`module-commons`、`module-patra`、`spotbugs`、`spotless`、`spring-boot-starter`（→ `patra.spring-starter`）、`spring-library`；`LinqibinDependencyManagement.kt` → `PatraDependencyManagement.kt`
- Delete: `patra.module-patra.gradle.kts`、`patra.module-commons.gradle.kts`（group 改为全局，第 Task 13 再用 `patra.tool-module` 取代 `boundary-check`）
- Modify: `build-logic/src/main/kotlin/patra.java-base.gradle.kts`、`gradle.properties`、根 `build.gradle.kts`、所有 60 个模块的 `build.gradle.kts` 的 `plugins {}` 块

**Interfaces:**
- Produces: 插件 id `patra.java-base`、`patra.java-library`、`patra.spring-library`、`patra.spring-starter`、`patra.hexagonal-{domain,api,app,infra,adapter,boot}`、`patra.dependency-management`、`patra.spotless`、`patra.spotbugs`、`patra.boundary-check`（Task 13 删）；Kotlin 函数 `applyPatraDependencyManagement(libs: VersionCatalog)`。

- [ ] **Step 1: 改名文件**

```bash
cd build-logic/src/main/kotlin
for f in linqibin.*.gradle.kts; do git mv "$f" "patra.${f#linqibin.}"; done
git mv patra.spring-boot-starter.gradle.kts patra.spring-starter.gradle.kts
git mv LinqibinDependencyManagement.kt PatraDependencyManagement.kt
git rm patra.module-patra.gradle.kts patra.module-commons.gradle.kts
```

- [ ] **Step 2: 改内容**

`PatraDependencyManagement.kt`：函数名改 `applyPatraDependencyManagement`，文件头注释去 "Linqibin"。
每个 `patra.*.gradle.kts`：`id("linqibin.` → `id("patra.`；`applyLinqibinDependencyManagement` → `applyPatraDependencyManagement`；注释里的插件名同步。
`patra.java-base.gradle.kts` 在 `plugins {}` 块后加：

```kotlin
// 全仓统一 group：patra-tool/*、patra-api/** 都是 dev.patra（决策 B）
group = "dev.patra"
```

`gradle.properties` 删 `patraGroup`、`commonsGroup` 两行及其注释。
根 `build.gradle.kts`：`id("linqibin.dependency-management")` → `id("patra.dependency-management")`，注释里"由 build-logic 中的身份 convention plugin 显式设置（linqibin.module-commons / linqibin.module-patra）" 改为 "由 patra.java-base 统一设置为 dev.patra"。
60 个模块 `build.gradle.kts` 的 `plugins {}`：删 `id("linqibin.module-commons")` / `id("linqibin.module-patra")` 两行；其余 `id("linqibin.` → `id("patra.`，`linqibin.spring-boot-starter` → `patra.spring-starter`。用 grep 列出再逐个 Edit：

Run: `grep -rln 'id("linqibin\.' --include='build.gradle.kts' .`

- [ ] **Step 3: 编译验证**

Run: `./gradlew help --quiet && ./gradlew compileJava compileTestJava compileIntegrationTestJava compileE2eTestJava`
Expected: BUILD SUCCESSFUL；`grep -rn 'linqibin\.' --include='*.kts' --include='*.kt' . | grep -v docs/` 零命中。提醒用户 IDEA 加载 Gradle 更改。

- [ ] **Step 4: 提交**

```bash
git add build-logic gradle.properties build.gradle.kts $(git ls-files '*/build.gradle.kts')
git commit -m "refactor(build): 约定插件改名为 patra.*，Gradle group 统一 dev.patra (PAP-79)"
```

---

### Task 3: Java 包根改名（全仓 1985 个文件）

**Files:**
- Modify: 所有 `src/**/*.java`（IDEA rename 自动改 `package` / `import`）、`META-INF/spring/*.imports`（14 份）、`META-INF/spring.factories`（2 份）、`logback-spring.xml`、`spotbugs-exclude.xml`、`.run/*.xml`、各 boot 的 `application*.yml` 里的 `spring.autoconfigure.exclude`、`additional-spring-configuration-metadata.json`（2 份）、`patra-starters/patra-spring-boot-starter-security/build.gradle.kts` 的 `mainClass`、`RestClientFactoryIT` / `DistributedLockIT` 的 `excludeName` 字符串、`ObservationInterceptorsAutoConfiguration` / `SecurityForwardingAutoConfiguration` / `BatchProgressMetricsAutoConfiguration` 的 `@ConditionalOnClass(name=)` / `afterName` 字符串、`CatalogArchitectureTest` / `IngestArchitectureTest` 的包名字符串、`UserRepositoryAdapterIT` / `ExprRepositoryAdapterIT` 的 `@ComponentScan(basePackages=)`

**Interfaces:**
- Produces: spec 2.2 节的包根（最终名）。模块目录还是旧的，三个要拆的模块（starter-core、rest-client / http-interface、commons-storage）与目标模块暂时共享包根。

- [ ] **Step 1: 按下表顺序逐条 rename package**（每条做完 `get_file_problems` 抽查两个文件无红）

| 顺序 | 旧包 | 新包 |
|---|---|---|
| 1 | `dev.linqibin.patra` | `dev.patra` |
| 2 | `dev.patra.starter.security` | `dev.patra.tool.security` |
| 3 | `dev.patra.starter.nacos` | `dev.patra.tool.launch` |
| 4 | `dev.patra.starter.provenance` | `dev.patra.ingest.provenance` |
| 5 | `dev.patra.common.provenance.api` | `dev.patra.ingest.provenance.api` |
| 6 | `dev.patra.starter.expr` | `dev.patra.ingest.expr` |
| 7 | `dev.patra.expr`（expr-kernel） | `dev.patra.ingest.expr` |
| 8 | `dev.patra.common.security` | `dev.patra.tool.core.security` |
| 9 | `dev.patra.common.model.enums` | `dev.patra.common` |
| 10 | `dev.patra.common.model` | `dev.patra.common` |
| 11 | `dev.patra.common.enums` | `dev.patra.common` |
| 12 | `dev.linqibin.commons.storage` | `dev.patra.tool.objectstorage.key` |
| 13 | `dev.linqibin.commons.cqrs` | `dev.patra.tool.cqrs` |
| 14 | `dev.linqibin.commons` | `dev.patra.tool.core` |
| 15 | `dev.linqibin.starter.core.cqrs` | `dev.patra.tool.cqrs` |
| 16 | `dev.linqibin.starter.core.error` | `dev.patra.tool.web.error` |
| 17 | `dev.linqibin.starter.core.logging` | `dev.patra.tool.observability.logging` |
| 18 | `dev.linqibin.starter.core.config` | `dev.patra.tool.launch.autoconfigure` |
| 19 | `dev.linqibin.starter.core`（剩 `async`） | `dev.patra.tool.core.async`（Task 11 删） |
| 20 | `dev.linqibin.starter.restclient`、`dev.linqibin.starter.httpinterface` | `dev.patra.tool.httpclient` |
| 21 | `dev.linqibin.starter.{web,jpa,batch,observability,openapi,test,objectstorage,redisson}` | `dev.patra.tool.{web,jpa,batch,observability,openapi,test,objectstorage,redisson}` |

顺序 9–11 把四个类合到一个包：`PublicationIdentifierType`、`CanonicalPublication`、`DataType`、`ProvenanceCode`、`IngestDateType`、`RegistryConfigScope` 同包无重名。顺序 15–16 后 commons-core 与 starter-core 的 `cqrs` / `error` 共享包名，两边类名不重复（spec 3.1）。

- [ ] **Step 2: 改非 Java 引用**

逐文件 Edit（IDEA rename 不改资源文件里的字符串）：14 份 `AutoConfiguration.imports`、2 份 `spring.factories`、`logback-spring.xml` 的两个 `conversionRule class`、`spotbugs-exclude.xml` 全部 `dev.linqibin.` 条目（同时删指向已不存在类的 5 条，spec 3.6）、6 个 `.run/*.xml` 的 `SPRING_BOOT_MAIN_CLASS`、catalog `application-e2e-test.yml` 的 `dev.linqibin.starter.objectstorage.*` 排除项、2 份 `additional-spring-configuration-metadata.json` 的 `type`、security `build.gradle.kts` 的 `mainClass`、Java 字符串：`ObservationInterceptorsAutoConfiguration` 的 `@ConditionalOnClass(name = "dev.patra.tool.web.error.pipeline.ResolutionInterceptor")`、`SecurityForwardingAutoConfiguration` 的 `"dev.patra.tool.httpclient.interceptor.InternalCallInterceptor"`、`BatchProgressMetricsAutoConfiguration` 的 `afterName = "dev.patra.tool.observability.autoconfigure.MicrometerAutoConfiguration"`、`DistributedLockIT` / `RestClientFactoryIT` 的 `excludeName`、两个 `*ArchitectureTest` 的 `"dev.patra.catalog"` / `"dev.patra.ingest"` 与 `List.of("dev.patra.common..", "dev.patra.tool..")`、两个 `@ComponentScan(basePackages = "dev.patra.<svc>.infra.adapter.persistence…")`、6 个启动类 `scanBasePackages = "dev.patra"`（Task 30 再删）。

Run: `grep -rn 'dev\.linqibin' --include='*.java' --include='*.xml' --include='*.imports' --include='*.factories' --include='*.yml' --include='*.yaml' --include='*.json' --include='*.kts' . | grep -v '^./docs/'`
Expected: 零命中。

- [ ] **Step 3: 编译 + 全部测试**

Run: `./gradlew spotlessApply && ./gradlew check`
Expected: BUILD SUCCESSFUL（spotless 会重排 import，所以先 apply）。

- [ ] **Step 4: 提交**

```bash
git add -A -- ':!docs'
git commit -m "refactor(all): Java 包根 dev.linqibin 改为 dev.patra，工具包按能力命名 (PAP-79)"
```

---

### Task 4: `patra-tool/` 目录就位（一对一的 10 个模块）与坐标全换

**Files:**
- Rename（`git mv` 整目录）：

| 旧目录 | 新目录 |
|---|---|
| `linqibin-commons/linqibin-commons-core` | `patra-tool/patra-tool-core` |
| `linqibin-commons/linqibin-spring-boot-starter-web` | `patra-tool/patra-tool-web` |
| `linqibin-commons/linqibin-spring-boot-starter-jpa` | `patra-tool/patra-tool-jpa` |
| `linqibin-commons/linqibin-spring-boot-starter-batch` | `patra-tool/patra-tool-batch` |
| `linqibin-commons/linqibin-spring-boot-starter-object-storage` | `patra-tool/patra-tool-object-storage` |
| `linqibin-commons/linqibin-spring-boot-starter-observability` | `patra-tool/patra-tool-observability` |
| `linqibin-commons/linqibin-spring-boot-starter-openapi` | `patra-tool/patra-tool-openapi` |
| `linqibin-commons/linqibin-spring-boot-starter-test` | `patra-tool/patra-tool-test` |
| `patra-starters/patra-spring-boot-starter-security` | `patra-tool/patra-tool-security` |
| `patra-starters/patra-spring-boot-starter-nacos` | `patra-tool/patra-tool-launch` |
| `linqibin-commons/linqibin-spring-boot-starter-rest-client` | `patra-tool/patra-tool-http-client`（http-interface 在 Task 6 并入） |

  暂留原地：`linqibin-spring-boot-starter-core`（Task 5 拆）、`linqibin-spring-boot-starter-http-interface`（Task 6）、`linqibin-commons-storage`（Task 7）、`linqibin-spring-boot-starter-redisson`（Task 11 删）、`patra-common/*` 四个（Task 8）、`patra-expr-kernel`、`patra-spring-boot-starter-{provenance,expr}`（Task 9）。
- Modify: `settings.gradle.kts`；所有含 `project(":linqibin-commons:…")` / `project(":patra-starters:…")` 的 `build.gradle.kts`（约 40 个）；`build-logic/src/main/kotlin/patra.hexagonal-{domain,api,app,infra,adapter,boot}.gradle.kts`；`.github/workflows/ci.yml`（第 76、81、87 行）、`cd.yml`（第 26–27、108 行）；`patra-infra/CLAUDE.md` 第 61、64 行；`patra-infra/docker/service.Dockerfile` 注释

**Interfaces:**
- Produces: 坐标 `:patra-tool:patra-tool-{core,web,jpa,batch,object-storage,observability,openapi,test,security,launch,http-client}`。

- [ ] **Step 1: `git mv` 上表 11 个目录；`settings.gradle.kts` 按 spec 3.7 的表改 `includeAt`（暂留模块保留旧 include；加 `mapParent(":patra-tool", "patra-tool")`）。**

- [ ] **Step 2: 全仓替换坐标**（这是构建脚本里的字符串，不是 Java 符号，可以 grep + Edit）

Run: `grep -rln ':linqibin-commons:\|:patra-starters:' --include='*.kts' --include='*.yml' .`
对每个文件把下列坐标改掉：`:linqibin-commons:linqibin-commons-core` → `:patra-tool:patra-tool-core`，`…starter-web` → `…patra-tool-web`，`…starter-jpa` → `…patra-tool-jpa`，`…starter-batch` → `…patra-tool-batch`，`…starter-object-storage` → `…patra-tool-object-storage`，`…starter-observability` → `…patra-tool-observability`，`…starter-openapi` → `…patra-tool-openapi`，`…starter-test` → `…patra-tool-test`，`…starter-rest-client` → `…patra-tool-http-client`，`:patra-starters:patra-spring-boot-starter-security` → `:patra-tool:patra-tool-security`，`:patra-starters:patra-spring-boot-starter-nacos` → `:patra-tool:patra-tool-launch`。
`cd.yml` 第 26–27 行改为一行 `- 'patra-tool/**'`（Task 9 后 `patra-starters/**` 不存在）；第 108 行坐标改 launch。`ci.yml` 三处路径改 `patra-tool/patra-tool-test/src/main/java/dev/patra/tool/test/...` 与 `patra-tool/patra-tool-test/src/main/resources/docker-compose-rocketmq.yml`（Task 11 删这一处）。

- [ ] **Step 3: 验证与模块图**

Run: `./gradlew compileJava compileTestJava compileIntegrationTestJava compileE2eTestJava && ./gradlew dumpModuleGraph`
Expected: BUILD SUCCESSFUL；`module-graph.json` 里出现 `patra-tool/patra-tool-core` 等 11 个 `dir`。

- [ ] **Step 4: 提交**

```bash
git add -A -- ':!docs'
git commit -m "refactor(tool): 一对一的 11 个共享模块搬进 patra-tool/ 并全换坐标 (PAP-79)"
```

---

### Task 5: 拆 starter-core：新建 patra-tool-cqrs，错误引擎并入 web，日志并入 observability，Clock 并入 launch

**Files:**
- Create: `patra-tool/patra-tool-cqrs/build.gradle.kts`、`patra-tool/patra-tool-cqrs/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Move（`git mv`，按 Task 3 后的包路径）：

| 从 `linqibin-commons/linqibin-spring-boot-starter-core/src/main/java/dev/patra/tool/…` | 到 |
|---|---|
| `cqrs/{CommandBusAutoConfiguration, CommandBusProperties, SimpleCommandBus}.java`、`cqrs/interceptor/*.java` | `patra-tool/patra-tool-cqrs/src/main/java/dev/patra/tool/cqrs/…` |
| `web/error/{config,engine,model,pipeline,spi,trace}/**`（不含 `config/CircuitBreakerErrorAutoConfiguration`、`pipeline/interceptor/CircuitBreakerInterceptor`，Task 11 删） | `patra-tool/patra-tool-web/src/main/java/dev/patra/tool/web/error/…` |
| `observability/logging/*.java` + `src/main/resources/logback-spring.xml` | `patra-tool/patra-tool-observability/src/main/java/dev/patra/tool/observability/logging/`、`…/src/main/resources/dev/patra/tool/observability/logback-patra.xml`（Task 24 改内容与 EPP；此处 Boot 还按 jar 根找不到它，先在 6 个 boot 的 `application.yml` 临时加 `logging.config: classpath:dev/patra/tool/observability/logback-patra.xml`，Task 24 删） |
| `launch/autoconfigure/CoreAutoConfiguration.java` | `patra-tool/patra-tool-launch/src/main/java/dev/patra/tool/launch/autoconfigure/LaunchAutoConfiguration.java`（IDEA rename 类名） |
| 同样搬 `src/test` 下对应测试：`SimpleCommandBusTest`、`LoggingCommandInterceptorTest` → cqrs；`DefaultErrorResolutionEngineTest`、`ErrorResolutionPipelineTest`、`HeaderBasedTraceProviderTest` → web；`SpanIdConverterTest`、`TraceIdConverterTest` → observability；`src/main/java/dev/patra/tool/web/error/README.md` → web 同位置 |
| 从 `patra-tool/patra-tool-core/src/main/java/dev/patra/tool/cqrs/*.java` + `…/core/error/codes/CoreErrorCode.java` | `patra-tool/patra-tool-cqrs/src/main/java/dev/patra/tool/cqrs/`（`CoreErrorCode` 改名 `CqrsErrorCode`，常量码改 `CQRS-0500`） |
- Modify: `patra-tool/patra-tool-web/build.gradle.kts`、`patra-tool-web` 的 imports（加 `dev.patra.tool.web.error.config.CoreErrorAutoConfiguration`，Task 19 再合并）、`patra-tool-launch` imports（新建，登记 `LaunchAutoConfiguration`）、catalog / identity / ingest / object-storage 的 `*-app/build.gradle.kts`、`settings.gradle.kts`、所有 `project(":patra-tool:patra-tool-core")` 原来是 `starter-core` 的依赖（Task 4 把 starter-core 坐标还留着：这里把 `:linqibin-commons:linqibin-spring-boot-starter-core` 的引用全部删掉或改指 web / cqrs）
- Delete: `linqibin-commons/linqibin-spring-boot-starter-core/`（剩 `async` 包与 CircuitBreaker 两个类随目录删除——它们零引用，spec 3.6）

**Interfaces:**
- Produces: 模块 `:patra-tool:patra-tool-cqrs`；`dev.patra.tool.cqrs.CqrsErrorCode`；`dev.patra.tool.web.error.spi.ErrorMappingContributor`（jpa、security、服务 boot 实现它）；`dev.patra.tool.launch.autoconfigure.LaunchAutoConfiguration#clock`。

- [ ] **Step 1: 新建 cqrs 模块**

`patra-tool/patra-tool-cqrs/build.gradle.kts`：

```kotlin
/**
 * patra-tool-cqrs：命令总线接口、SimpleCommandBus、三个内置拦截器与自动配置。
 */
plugins {
    id("patra.boundary-check")
    id("patra.spring-starter")
}
dependencies {
    api(project(":patra-tool:patra-tool-core"))
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework:spring-context")
    compileOnly("io.micrometer:micrometer-core")
    compileOnly("io.micrometer:micrometer-observation")
    testImplementation(project(":patra-tool:patra-tool-test"))
}
```

imports 文件两行：`dev.patra.tool.cqrs.CommandBusAutoConfiguration`、`dev.patra.tool.cqrs.interceptor.CommandInterceptorAutoConfiguration`（Task 18 合成一个）。`settings.gradle.kts` 加 `includeAt(":patra-tool:patra-tool-cqrs", "patra-tool/patra-tool-cqrs")`。

- [ ] **Step 2: `git mv` 上表文件；用 `rename_refactoring` 把 `CoreErrorCode` 改 `CqrsErrorCode`、`CoreAutoConfiguration` 改 `LaunchAutoConfiguration`。**

- [ ] **Step 3: 依赖改写**

`patra-tool-web/build.gradle.kts`：删 `api(project(":linqibin-commons:linqibin-spring-boot-starter-core"))`，保留 `api(project(":patra-tool:patra-tool-core"))`，加 `compileOnly("io.micrometer:micrometer-core")`（`DefaultErrorResolutionEngine` 若用到 Micrometer；grep `io.micrometer` 确认，没有就不加）。
`patra-tool-jpa`、`patra-tool-batch`、`patra-tool-observability`、`patra-tool-http-client`、`linqibin-spring-boot-starter-redisson`、`patra-starters/patra-spring-boot-starter-expr`、`patra-gateway-boot`、各服务 `*-app` / `*-infra`：把 `starter-core` 那条删掉；jpa 与 observability 改 `compileOnly(project(":patra-tool:patra-tool-web"))`（Task 14 再配条件）；gateway-boot 加 `implementation(project(":patra-tool:patra-tool-web"))` 已有；catalog / identity / ingest / object-storage 的 `*-app` 加 `api(project(":patra-tool:patra-tool-cqrs"))`；registry-boot、ingest-boot 实现 `ErrorMappingContributor` 的类经 `implementation(web)` 已可见。
`patra-tool-launch/build.gradle.kts` 不变（Clock 只需 spring-boot-autoconfigure，已有 `spring-boot-starter`）。
`settings.gradle.kts` 删 starter-core 的 include；`git rm -r linqibin-commons/linqibin-spring-boot-starter-core`。

- [ ] **Step 4: 编译、check、模块图**

Run: `./gradlew spotlessApply && ./gradlew check && ./gradlew dumpModuleGraph`
Expected: BUILD SUCCESSFUL；6 个服务 `./gradlew :patra-api:patra-registry:patra-registry-boot:bootRun` 任选一个起一次看日志有 `[%traceId/%spanId]` 格式（logback 临时键生效）。

- [ ] **Step 5: 提交**

```bash
git add -A -- ':!docs'
git commit -m "refactor(tool): 拆 starter-core，新建 patra-tool-cqrs，错误引擎并入 web、日志并入 observability (PAP-79)"
```

---

### Task 6: 合并 http-interface 进 patra-tool-http-client

**Files:**
- Move: `linqibin-commons/linqibin-spring-boot-starter-http-interface/src/{main,test,integrationTest}/java/dev/patra/tool/httpclient/**` → `patra-tool/patra-tool-http-client/src/{同}/java/dev/patra/tool/httpclient/**`（`config/HttpInterfaceAutoConfiguration.java`、`config/HttpInterfaceProperties.java`、`error/ProblemDetailErrorHandler.java`、`factory/RestClientFactory.java`、`interceptor/InternalCallInterceptor.java`；测试 `HttpInterfaceAutoConfigurationTest`、`ProblemDetailErrorHandlerTest`、`RestClientFactoryTest`、`RestClientFactoryIT`）；README 内容合并到 Task 26
- Modify: `patra-tool/patra-tool-http-client/build.gradle.kts`、它的 imports（追加 `dev.patra.tool.httpclient.config.HttpInterfaceAutoConfiguration`）、`settings.gradle.kts`、所有 `project(":linqibin-commons:linqibin-spring-boot-starter-http-interface")` 引用（catalog-boot、catalog-infra、ingest-boot、ingest-infra、P:security 的 compileOnly 与 test、P:expr 的 compileOnly）、`patra-api/patra-catalog/patra-catalog-infra/build.gradle.kts`（删单独加的 `httpclient5`）
- Delete: `linqibin-commons/linqibin-spring-boot-starter-http-interface/`

- [ ] **Step 1: `git mv` 文件；`build.gradle.kts` 改为并集**

```kotlin
/**
 * patra-tool-http-client：RestClient、流式下载、隧道代理（原 rest-client）与 HTTP Interface 客户端（原 http-interface）。
 */
plugins {
    id("patra.boundary-check")
    id("patra.spring-starter")
}
dependencies {
    api(project(":patra-tool:patra-tool-core"))
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework.boot:spring-boot-restclient")
    api("org.springframework:spring-web")
    api("org.apache.httpcomponents.client5:httpclient5")
    api("org.springframework.cloud:spring-cloud-starter-loadbalancer")
    api("io.micrometer:micrometer-core")
    api(libs.commons.net)
    compileOnly("org.springframework:spring-webflux")
    compileOnly("io.projectreactor.netty:reactor-netty-http")
    testImplementation(project(":patra-tool:patra-tool-test"))
    testImplementation("org.springframework:spring-webflux")
    testImplementation("io.projectreactor.netty:reactor-netty-http")
}
tasks.jacocoTestCoverageVerification {
    violationRules { rule { limit { minimum = "0.75".toBigDecimal() } } }
}
```

- [ ] **Step 2: 引用改写**：上列模块把 `starter-http-interface` 改 `:patra-tool:patra-tool-http-client`（security 两处 `compileOnly` / `testImplementation` / `integrationTestImplementation` 合并成一条 `compileOnly` + 一条 `testImplementation`，expr 的 `compileOnly` 删除，它的 `@AutoConfigureAfter(HttpInterfaceAutoConfiguration.class)` 改 `@AutoConfiguration(afterName = "dev.patra.tool.httpclient.config.HttpInterfaceAutoConfiguration")` 并删 import）；`settings.gradle.kts` 删 include；`git rm -r` 旧目录。

- [ ] **Step 3: 验证**

Run: `./gradlew spotlessApply && ./gradlew check :patra-tool:patra-tool-http-client:integrationTest && ./gradlew dumpModuleGraph`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 提交** `refactor(tool): rest-client 与 http-interface 合并为 patra-tool-http-client (PAP-79)`

---

### Task 7: commons-storage 并入 object-storage

**Files:**
- Move: `linqibin-commons/linqibin-commons-storage/src/main/java/dev/patra/tool/objectstorage/key/{DatePartitionedKeyGenerator, ObjectKeyContext, ObjectKeyGenerator}.java` → `patra-tool/patra-tool-object-storage/src/main/java/dev/patra/tool/objectstorage/key/`
- Delete: `…/key/ObjectKeyTemplate.java`、`linqibin-commons/linqibin-commons-storage/`
- Modify: `patra-tool/patra-tool-object-storage/build.gradle.kts`（删 `api(project(":linqibin-commons:linqibin-commons-storage"))`）、`settings.gradle.kts`

- [ ] **Step 1: `git mv` 三个类，`git rm` 模板类与旧目录，改 build 与 settings。**
- [ ] **Step 2: Run** `./gradlew :patra-tool:patra-tool-object-storage:check && ./gradlew dumpModuleGraph` → BUILD SUCCESSFUL。
- [ ] **Step 3: 提交** `refactor(tool): 对象键生成并入 patra-tool-object-storage (PAP-79)`

---

### Task 8: patra-common 合一，C:security 并入 core

**Files:**
- Create: `patra-api/patra-common/build.gradle.kts`
- Move: `patra-api/patra-common/patra-common-model/src/main/java/dev/patra/common/{CanonicalPublication, DataType, PublicationIdentifierType}.java`、`patra-common-enums/src/main/java/dev/patra/common/ProvenanceCode.java` → `patra-api/patra-common/src/main/java/dev/patra/common/`；`patra-common-model/src/test/java/dev/patra/common/DataTypeTest.java` + `src/test/resources/logback-test.xml` → `patra-api/patra-common/src/test/…`；`patra-common-security/src/main/java/dev/patra/tool/core/security/*.java` → `patra-tool/patra-tool-core/src/main/java/dev/patra/tool/core/security/`，其 4 个测试 → `patra-tool-core/src/test/java/dev/patra/tool/core/security/`；`patra-common-enums/src/main/java/dev/patra/common/RegistryConfigScope.java` → `patra-starters/patra-spring-boot-starter-expr/src/main/java/dev/patra/ingest/expr/compiler/snapshot/RegistryConfigScope.java`（rename package 到 `dev.patra.ingest.expr.compiler.snapshot`）
- Delete: `IngestDateType.java`、`patra-api/patra-common/{patra-common-model,patra-common-enums,patra-common-provenance-api（Task 9 搬走后）,patra-common-security}/`、`patra-api/patra-common/README.md`（Task 26 重写）
- Modify: `settings.gradle.kts`、`build-logic/src/main/kotlin/patra.hexagonal-{domain,api}.gradle.kts`、所有 `project(":patra-api:patra-common:patra-common-{model,enums,security}")` 引用、`patra-tool-security/build.gradle.kts`（删 `api(C:security)`）、`patra-tool-core/build.gradle.kts`

- [ ] **Step 1: 新模块 build**

```kotlin
/**
 * patra-common：跨服务共用的业务词汇（Shared Kernel）。
 */
plugins {
    id("patra.java-library")
}
dependencies {
    api("com.fasterxml.jackson.core:jackson-annotations")
    api("tools.jackson.core:jackson-databind")
}
tasks.jacocoTestCoverageVerification {
    violationRules { rule { limit { minimum = "0.75".toBigDecimal() } } }
}
```

`settings.gradle.kts`：删四个子模块 include，加 `includeAt(":patra-api:patra-common", "patra-api/patra-common")`，去掉 `mapParent(":patra-api:patra-common", …)`。

- [ ] **Step 2: `git mv` 文件；约定插件改依赖**

`patra.hexagonal-domain.gradle.kts` 与 `patra.hexagonal-api.gradle.kts` 的 `dependencies {}`：`api(project(":patra-api:patra-common:patra-common-enums"))` → `api(project(":patra-api:patra-common"))`；`api(project(":linqibin-commons:linqibin-commons-core"))` 已在 Task 4 改为 core。
全仓 `project(":patra-api:patra-common:patra-common-model")` / `-enums` → `:patra-api:patra-common`；`-security` → 删（经 core）。顺手删 G5 列出的与插件重复的显式声明：registry / ingest / catalog / object-storage 四个 `*-api` 的 `api(patra-common)`、`api(patra-tool-core)`。

- [ ] **Step 3: 验证**

Run: `./gradlew spotlessApply && ./gradlew check && ./gradlew dumpModuleGraph`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 提交** `refactor(common): patra-common 合为一个模块，当前用户抽象并入 patra-tool-core (PAP-79)`

---

### Task 9: ingest 收回 provenance 与 expr

**Files:**
- Rename（整目录）：`patra-starters/patra-spring-boot-starter-provenance` → `patra-api/patra-ingest/patra-ingest-provenance`；`patra-starters/patra-spring-boot-starter-expr` → `patra-api/patra-ingest/patra-ingest-expr`
- Move: `patra-api/patra-common/patra-common-provenance-api/src/main/java/dev/patra/ingest/provenance/api/**` → `patra-api/patra-ingest/patra-ingest-provenance/src/main/java/dev/patra/ingest/provenance/api/`；`patra-api/patra-expr-kernel/src/{main,test}/java/dev/patra/ingest/expr/**` → `patra-api/patra-ingest/patra-ingest-expr/src/{同}/java/dev/patra/ingest/expr/`
- Delete: `common/gateway/GatewayRequestBuilder.java`、`epmc/request/EpmcSearchRequestAssembler.java`、`api/endpoints/CrossrefEndpoints.java`（及空的 `endpoints` 目录）、`api/values/epmc/ResultType.java`、`api/values/pubmed/{DateType, UseHistory}.java`、`compiler/model/OperationTypes.java`、`src/main/resources/application-expr-reference.yml`、`patra-api/patra-expr-kernel/`、`patra-api/patra-common/patra-common-provenance-api/`、`patra-starters/`
- Modify: 两个新模块的 `build.gradle.kts`、`settings.gradle.kts`、`patra-api/patra-ingest/patra-ingest-{app,infra}/build.gradle.kts`、`cd.yml`（`patra-starters/**` 行删）、`spotbugs-exclude.xml`（provenance / expr 包名正则已在 Task 3 改）

- [ ] **Step 1: `git mv` 与删除；两个 build 文件**

`patra-ingest-provenance/build.gradle.kts`：

```kotlin
/**
 * patra-ingest-provenance：ingest 对 PubMed / EPMC / Crossref 的客户端、处理器与 API 常量。
 */
plugins {
    id("patra.spring-starter")
}
dependencies {
    api(project(":patra-api:patra-common"))
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework:spring-web")
    api("tools.jackson.core:jackson-databind")
    api("tools.jackson.dataformat:jackson-dataformat-xml")
    api(libs.hutool.core)
    api("org.slf4j:slf4j-api")
    compileOnly("io.micrometer:micrometer-core")
    // 自动配置里的 PubMedClient / EPMCClient 注入 http-client 出的 defaultRestClient bean，编译期不引用它的类
    runtimeOnly(project(":patra-tool:patra-tool-http-client"))
    testImplementation(project(":patra-tool:patra-tool-test"))
}
```

`patra-ingest-expr/build.gradle.kts`：

```kotlin
/**
 * patra-ingest-expr：表达式 AST（原 expr-kernel）与编译器（原 starter-expr）。
 */
plugins {
    id("patra.spring-starter")
}
dependencies {
    api(project(":patra-api:patra-common"))
    api(project(":patra-tool:patra-tool-core"))
    api(project(":patra-api:patra-registry:patra-registry-api"))
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("tools.jackson.core:jackson-databind")
    compileOnly("io.micrometer:micrometer-core")
    testImplementation(project(":patra-tool:patra-tool-test"))
}
```

（`implementation(project(":patra-api:patra-ingest:patra-ingest-domain"))` 在 Task 10 搬 `HashUtils` 时加。）

`settings.gradle.kts`：删 `patra-starters` 四条与 `mapParent(":patra-starters")`、删 expr-kernel、provenance-api；加两条 ingest 子模块 include。
`patra-ingest-app/build.gradle.kts`：`api(P:expr)`、`api(expr-kernel)` → `api(project(":patra-api:patra-ingest:patra-ingest-expr"))`；删 `api(P:provenance)`（零 import）、`api(C:enums)`、`api(commons-core)`（经 domain 传递）、`api(starter-core)`（Task 5 已删）。`patra-ingest-infra/build.gradle.kts`：`api(expr-kernel)` + `api(P:expr)` → `api(ingest-expr)`；`api(P:provenance)` → `api(project(":patra-api:patra-ingest:patra-ingest-provenance"))`；`api(C:model)` → 删（经 domain）。`patra-ingest-adapter`、`patra-ingest-api` 的 `api(ingest-api)` / 重复声明按 spec 6.4 删。

- [ ] **Step 2: 验证**

Run: `./gradlew spotlessApply && ./gradlew check :patra-api:patra-ingest:patra-ingest-infra:integrationTest && ./gradlew dumpModuleGraph`
Expected: BUILD SUCCESSFUL；`ls patra-starters` 不存在。

- [ ] **Step 3: 提交** `refactor(ingest): provenance 与 expr 收回 patra-ingest 子模块 (PAP-79)`

---

### Task 10: commons-core 归位（ingest / catalog 专属类）与 core 去第三方依赖

**Files:**
- Move（`rename_refactoring` 的 move 或 `git mv` + rename package）：

| 类 | 从 `patra-tool/patra-tool-core/src/main/java/dev/patra/tool/core/…` | 到 |
|---|---|---|
| `enums/Priority.java` | | `patra-api/patra-ingest/patra-ingest-domain/src/main/java/dev/patra/ingest/domain/model/enums/Priority.java` |
| `messaging/ChannelKey.java` | | `…/patra-ingest-domain/src/main/java/dev/patra/ingest/domain/messaging/ChannelKey.java` |
| `type/TypeReference.java`、`util/HashUtils.java` | | `…/patra-ingest-domain/src/main/java/dev/patra/ingest/domain/support/{TypeReference, HashUtils}.java` |
| `json/*.java`（7 个） | | `patra-api/patra-ingest/patra-ingest-app/src/main/java/dev/patra/ingest/app/usecase/plan/json/` |
| `query/{PageResult, PagingParams}.java` | | `patra-api/patra-catalog/patra-catalog-domain/src/main/java/dev/patra/catalog/domain/query/` |
| `util/StringUtils.java` | | `patra-api/patra-catalog/patra-catalog-infra/src/main/java/dev/patra/catalog/infra/persistence/support/StringUtils.java` |
| 测试 `TypeReferenceTest` → ingest-domain；`JsonNormalizerTest` → ingest-app；`PageResultTest`、`PagingParamsTest` → catalog-domain；`StringUtilsTest` → catalog-infra | | |
- Delete: `enums/SortDirection.java`、`domain/ReadOnlyAggregate.java`
- Modify: `patra-tool/patra-tool-core/build.gradle.kts`、`patra-api/patra-ingest/patra-ingest-expr/build.gradle.kts`、`patra-portal/src/types/portal.ts` 第 248 行注释

- [ ] **Step 1: 搬类与删除（IDEA move 会改 import；`JsonNormalizer` 一族 3 个包私有类随包搬）。**

- [ ] **Step 2: core 的 build 收口**

```kotlin
/**
 * patra-tool-core：纯 Java 的 DDD 基类、错误类型、当前用户抽象与顺序常量，零 Spring 依赖。
 */
plugins {
    id("patra.boundary-check")
    id("patra.java-library")
}
dependencies {
    compileOnly("org.slf4j:slf4j-api")
    testRuntimeOnly("org.slf4j:slf4j-api")
}
tasks.jacocoTestCoverageVerification {
    violationRules { rule { limit { minimum = "0.75".toBigDecimal() } } }
}
```

`patra-ingest-expr/build.gradle.kts` 加 `implementation(project(":patra-api:patra-ingest:patra-ingest-domain"))`。`patra-ingest-domain` 的 Jackson 经 `patra-common` 的 `api(jackson-databind)` 来，不用加。portal 注释改 `dev.patra.catalog.domain.query.PageResult`。

Run: `grep -rn '^import cn.hutool\|^import tools.jackson\|^import com.fasterxml' patra-tool/patra-tool-core/src/main`
Expected: 零命中（否则还有没搬走的类）。

- [ ] **Step 3: 验证**

Run: `./gradlew spotlessApply && ./gradlew check && ./gradlew dumpModuleGraph`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 提交** `refactor(core): 只有单个服务在用的类归位到 ingest / catalog，core 去掉第三方依赖 (PAP-79)`

---

### Task 11: 删除 redisson、RocketMQ 测试支持与其余零引用项

**Files:**
- Delete: `linqibin-commons/linqibin-spring-boot-starter-redisson/`、`linqibin-commons/`（此时应为空目录）、`patra-tool/patra-tool-test/src/main/java/dev/patra/tool/test/container/initializer/RocketMQContainerInitializer.java`、`…/container/rocketmq/{RocketMQContainerSupport, RocketMQTopicAdmin}.java`、`patra-tool-test/src/main/resources/docker-compose-rocketmq.yml`、`patra-tool/patra-tool-jpa/src/main/java/dev/patra/tool/jpa/entity/SoftDeletableChildJpaEntity.java` + `src/test/…/SoftDeletableChildJpaEntityTest.java`
- Modify: `gradle/libs.versions.toml`（删 `redisson`、`redisson-spring-boot`、`resilience4j` 版本与 BOM 条目）、`build-logic/src/main/kotlin/PatraDependencyManagement.kt`（删 resilience4j BOM）、`patra-tool/patra-tool-batch/build.gradle.kts`（Task 5 已删 redisson 行，核对）、`patra-tool-batch/src/testFixtures/resources/application-test.yml`（删 `spring.autoconfigure.exclude` 两行与 `linqibin.starter.batch.observability.*`）、`patra-tool-batch/src/integrationTest/…/{BatchAutoConfigurationIT, DualDataSourceIT}.java`（删 `spring.autoconfigure.exclude=…Redisson…` 与 `redisson.observability.metrics-enabled` 属性）、`patra-api/patra-catalog/patra-catalog-boot/src/testFixtures/resources/application-e2e-test.yml`（删 Redisson V2/V4、`DataRedisAutoConfiguration` 排除与 `linqibin.starter.redisson.enabled`）、`patra-api/patra-catalog/patra-catalog-boot/src/main/java/dev/patra/catalog/PatraCatalogApplication.java`（删 `exclude = DataRedisRepositoriesAutoConfiguration.class` 与 import、注释）、`patra-infra/nacos/dev/patra-catalog.yaml`（删 `spring.data.redis.password` 三行）、`.github/workflows/ci.yml`（删 starter-test 那条 compose 检查）、`spotbugs-exclude.xml`（删 RocketMQ 两条）、`settings.gradle.kts`

- [ ] **Step 1: 记下当前 SHA**：`git rev-parse HEAD` 写进提交信息（"删除前最后一版在 <sha>"）。
- [ ] **Step 2: 删除与修改上列文件。**
- [ ] **Step 3: 验证**

Run: `./gradlew spotlessApply && ./gradlew check :patra-tool:patra-tool-batch:integrationTest :patra-tool:patra-tool-launch:test && ./gradlew dumpModuleGraph`
Expected: BUILD SUCCESSFUL；`NacosConfigFilesTest` 通过（catalog yaml 少了 redis 段）；`grep -rn 'redisson\|Redisson' --include='*.kts' --include='*.java' --include='*.yml' --include='*.yaml' --include='*.toml' . | grep -v docs/` 零命中。

- [ ] **Step 4: 提交** `refactor(tool): 删除 redisson 模块、RocketMQ 测试支持与零引用类型 (PAP-79)`

---

### Task 12: PAP-79 收尾验证

- [ ] **Step 1: 结构核对**

Run:
```bash
ls patra-tool | sort | tr '\n' ' '; echo
test ! -d linqibin-commons && test ! -d patra-starters && test ! -d patra-api/patra-expr-kernel && echo dirs-ok
grep -r linqibin --include='*.java' --include='*.kts' --include='*.yml' --include='*.yaml' . | grep -v '^./docs/' | wc -l
```
Expected: 12 个模块名；`dirs-ok`；`0`。

- [ ] **Step 2: 全栈门控**

Run: `./gradlew check integrationTest`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 6 个服务本机各启动一次**（`.run` 配置或 `./gradlew :patra-api:<svc>:<svc>-boot:bootRun`），确认注册到 Nacos（`http://100.103.73.27:8848` 服务列表）后停掉。

- [ ] **Step 4: 用 Linear MCP 把 PAP-79 置为 In Review，评论里贴 Step 1 的输出。** 本任务无代码提交。

---

## B · PAP-80 依赖规则（实现，R4 / R5 暂不挂 check）

### Task 13: build-logic 测试骨架 + R1 `checkToolBoundary` + `patra.tool-module` 插件

**Files:**
- Modify: `build-logic/build.gradle.kts`
- Create: `build-logic/src/main/kotlin/ToolDependencyRules.kt`、`build-logic/src/main/kotlin/patra.tool-module.gradle.kts`、`build-logic/src/test/kotlin/ToolDependencyRulesTest.kt`、`build-logic/src/test/kotlin/ToolModulePluginFunctionalTest.kt`
- Delete: `build-logic/src/main/kotlin/patra.boundary-check.gradle.kts`
- Modify: 12 个 `patra-tool/*/build.gradle.kts` 的 `plugins {}`（`id("patra.boundary-check")` → `id("patra.tool-module")`）

**Interfaces:**
- Produces: `object ToolDependencyRules { data class Declared(configuration: String, target: String); fun r1Violations(List<Declared>): List<String>; fun r2Violations(project: String, List<Declared>): List<String> }`（R2 在 Task 14 实现，本任务先只写 R1 并让 R2 返回空）；插件 `patra.tool-module` 注册 `checkToolBoundary` 并挂 `check`。

- [ ] **Step 1: build-logic 加测试依赖**

`build-logic/build.gradle.kts` 的 `dependencies {}` 末尾加：

```kotlin
    // build-logic 自己的测试：规则是纯函数用 JUnit 测，插件挂载用 TestKit 冒烟
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(gradleTestKit())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
```

文件末尾加：

```kotlin
tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: 写失败测试 `ToolDependencyRulesTest.kt`**

```kotlin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ToolDependencyRulesTest {

    private fun d(configuration: String, target: String) = ToolDependencyRules.Declared(configuration, target)

    @Test
    fun r1_rejects_any_configuration_pointing_at_patra_api() {
        val violations = ToolDependencyRules.r1Violations(
            listOf(d("implementation", ":patra-api:patra-catalog:patra-catalog-api"), d("api", ":patra-tool:patra-tool-core")),
        )
        assertThat(violations).containsExactly("implementation -> :patra-api:patra-catalog:patra-catalog-api")
    }

    @Test
    fun r1_also_rejects_patra_common_because_it_lives_under_patra_api() {
        assertThat(ToolDependencyRules.r1Violations(listOf(d("testImplementation", ":patra-api:patra-common"))))
            .containsExactly("testImplementation -> :patra-api:patra-common")
    }

    @Test
    fun r1_accepts_tool_and_third_party_targets() {
        assertThat(ToolDependencyRules.r1Violations(listOf(d("api", ":patra-tool:patra-tool-core")))).isEmpty()
    }
}
```

Run: `./gradlew -p build-logic test --tests ToolDependencyRulesTest`
Expected: 编译失败，`ToolDependencyRules` 未定义。

- [ ] **Step 3: 实现 `ToolDependencyRules.kt`（R1 + R2 骨架）**

```kotlin
/// R1 / R2：工具模块之间与工具对业务的 project 依赖规则。
///
/// 纯函数：输入是模块路径与它声明的 `(配置名, 目标模块路径)` 列表，输出违规描述；
/// 插件在配置阶段算好结果作为任务输入，与 configuration cache 兼容。
object ToolDependencyRules {

    const val CORE = ":patra-tool:patra-tool-core"
    const val TEST = ":patra-tool:patra-tool-test"

    /// 工具间允许的硬依赖白名单：security 要用 web 的 ProblemDetail 适配器。
    val HARD_WHITELIST: Map<String, Set<String>> = mapOf(
        ":patra-tool:patra-tool-security" to setOf(":patra-tool:patra-tool-web"),
    )

    private val HARD_CONFIGURATIONS = setOf("api", "implementation", "runtimeOnly", "testFixturesApi", "testFixturesImplementation")

    /// 一条声明的 project 依赖。
    data class Declared(val configuration: String, val target: String)

    /// R1：任何配置都不得指向 `:patra-api` 下的模块（含 patra-common）。
    fun r1Violations(declared: List<Declared>): List<String> =
        declared.filter { it.target.startsWith(":patra-api") }.map { "${it.configuration} -> ${it.target}" }

    /// R2：工具间硬依赖只允许指向 core（白名单例外）；compileOnly 任意；测试配置可用 core、test 与主代码已 compileOnly 的模块。
    fun r2Violations(project: String, declared: List<Declared>): List<String> = emptyList()

    /// 测试类配置：test*（不含 testFixtures*）、integrationTest*、e2eTest*。
    fun isTestConfiguration(name: String): Boolean =
        (name.startsWith("test") && !name.startsWith("testFixtures")) ||
            name.startsWith("integrationTest") || name.startsWith("e2eTest")
}
```

Run: `./gradlew -p build-logic test --tests ToolDependencyRulesTest` → PASS。

- [ ] **Step 4: 写插件 `patra.tool-module.gradle.kts`**

```kotlin
// 工具模块约定（patra-tool/*）：R1 工具不依赖业务、R2 工具间只指向 core、R4 自动配置写法，全部挂 check。
// 规则本体在 ToolDependencyRules / AutoConfigurationRules（纯函数，有单测）；这里只负责收集输入与注册任务。
import org.gradle.api.artifacts.ProjectDependency

abstract class ToolDependencyCheck : DefaultTask() {
    @get:Input abstract val rule: Property<String>
    @get:Input abstract val modulePath: Property<String>
    @get:Input abstract val violations: ListProperty<String>
    @get:Input abstract val advice: Property<String>

    @TaskAction
    fun check() {
        val found = violations.get()
        if (found.isNotEmpty()) {
            throw GradleException(
                "${rule.get()} 违规：${modulePath.get()}\n" + found.joinToString("\n") { "  $it" } + "\n" + advice.get(),
            )
        }
    }
}

val declaredProjectDependencies = provider {
    configurations.flatMap { configuration ->
        configuration.dependencies.withType(ProjectDependency::class.java)
            .map { ToolDependencyRules.Declared(configuration.name, it.path) }
    }
}

tasks.register<ToolDependencyCheck>("checkToolBoundary") {
    group = "verification"
    description = "R1：工具模块不得依赖 patra-api/* 下的模块"
    rule.set("R1")
    modulePath.set(project.path)
    violations.set(declaredProjectDependencies.map { ToolDependencyRules.r1Violations(it) })
    advice.set("工具模块不得依赖 patra-api/* 下的任何模块；业务契约放回对应服务。")
}

tasks.register<ToolDependencyCheck>("checkToolDependencies") {
    group = "verification"
    description = "R2：工具间硬依赖只允许指向 patra-tool-core"
    rule.set("R2")
    modulePath.set(project.path)
    violations.set(declaredProjectDependencies.map { ToolDependencyRules.r2Violations(project.path, it) })
    advice.set("工具间硬依赖只允许指向 :patra-tool:patra-tool-core（白名单：security -> web）。要协作改成 compileOnly，并在自动配置类上加 @ConditionalOnClass。")
}

tasks.named("check") {
    dependsOn("checkToolBoundary", "checkToolDependencies")
}
```

删 `patra.boundary-check.gradle.kts`；12 个工具模块 `plugins {}` 把 `id("patra.boundary-check")` 改 `id("patra.tool-module")`（core、cqrs、web、jpa、batch、http-client、object-storage、observability、security、openapi、launch、test）。

- [ ] **Step 5: TestKit 冒烟测试 `ToolModulePluginFunctionalTest.kt`**

```kotlin
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ToolModulePluginFunctionalTest {

    @TempDir lateinit var root: File

    private fun write(path: String, content: String) {
        File(root, path).apply { parentFile.mkdirs(); writeText(content.trimIndent()) }
    }

    private fun project(): File {
        write("settings.gradle.kts", """
            rootProject.name = "fixture"
            include(":patra-tool:patra-tool-core", ":patra-tool:patra-tool-demo", ":patra-api:patra-fake")
        """)
        write("patra-tool/patra-tool-core/build.gradle.kts", """plugins { id("java-library") }""")
        write("patra-api/patra-fake/build.gradle.kts", """plugins { id("java-library") }""")
        return root
    }

    @Test
    fun check_fails_with_r1_when_tool_depends_on_patra_api() {
        project()
        write("patra-tool/patra-tool-demo/build.gradle.kts", """
            plugins { id("java-library"); id("patra.tool-module") }
            dependencies { implementation(project(":patra-api:patra-fake")) }
        """)
        val result = GradleRunner.create().withProjectDir(root).withPluginClasspath()
            .withArguments(":patra-tool:patra-tool-demo:check").buildAndFail()
        assertThat(result.output).contains("R1 违规：:patra-tool:patra-tool-demo", "implementation -> :patra-api:patra-fake")
    }

    @Test
    fun check_passes_for_a_clean_tool_module() {
        project()
        write("patra-tool/patra-tool-demo/build.gradle.kts", """
            plugins { id("java-library"); id("patra.tool-module") }
            dependencies { api(project(":patra-tool:patra-tool-core")) }
        """)
        val result = GradleRunner.create().withProjectDir(root).withPluginClasspath()
            .withArguments(":patra-tool:patra-tool-demo:check").build()
        assertThat(result.output).doesNotContain("违规")
    }
}
```

Run: `./gradlew -p build-logic test`
Expected: PASS（TestKit 第一次跑要下 Gradle 分发或复用本地，可能慢）。

- [ ] **Step 6: 全仓验证并提交**

Run: `./gradlew check`（12 个模块的 `checkToolBoundary` 应全部通过）
```bash
git add build-logic patra-tool
git commit -m "build(rules): 新建 patra.tool-module 插件，R1 工具不依赖业务挂 check (PAP-80)"
```
提醒用户加载 Gradle 更改。

---

### Task 14: R2 工具间依赖规则 + 横向边改 compileOnly

**Files:**
- Modify: `build-logic/src/main/kotlin/ToolDependencyRules.kt`、`build-logic/src/test/kotlin/ToolDependencyRulesTest.kt`
- Modify: `patra-tool/patra-tool-jpa/build.gradle.kts`、`patra-tool/patra-tool-jpa/src/main/java/dev/patra/tool/jpa/autoconfig/JpaAutoConfiguration.java`、`…/jpa/error/contributor/JpaErrorMappingContributor.java`
- Modify: `patra-tool/patra-tool-observability/build.gradle.kts`、`…/observability/autoconfigure/ObservationInterceptorsAutoConfiguration.java`
- Modify: `patra-tool/patra-tool-security/build.gradle.kts`（核对：`api(web)`、`compileOnly(jpa)`、`compileOnly(http-client)`、`testImplementation(http-client)`、`integrationTestImplementation(jpa)`）

- [ ] **Step 1: 失败测试（追加到 `ToolDependencyRulesTest`）**

```kotlin
    private val jpa = ":patra-tool:patra-tool-jpa"

    @Test
    fun r2_allows_hard_dependency_on_core_only() {
        val v = ToolDependencyRules.r2Violations(jpa, listOf(d("api", ToolDependencyRules.CORE), d("api", ":patra-tool:patra-tool-web")))
        assertThat(v).containsExactly("api -> :patra-tool:patra-tool-web")
    }

    @Test
    fun r2_allows_compile_only_on_any_tool() {
        assertThat(ToolDependencyRules.r2Violations(jpa, listOf(d("compileOnly", ":patra-tool:patra-tool-web")))).isEmpty()
    }

    @Test
    fun r2_whitelists_security_to_web() {
        assertThat(ToolDependencyRules.r2Violations(":patra-tool:patra-tool-security", listOf(d("api", ":patra-tool:patra-tool-web")))).isEmpty()
    }

    @Test
    fun r2_lets_tests_use_core_test_and_compile_only_targets() {
        val declared = listOf(
            d("compileOnly", ":patra-tool:patra-tool-web"),
            d("testImplementation", ToolDependencyRules.TEST),
            d("integrationTestImplementation", ":patra-tool:patra-tool-web"),
            d("integrationTestImplementation", ":patra-tool:patra-tool-batch"),
        )
        assertThat(ToolDependencyRules.r2Violations(jpa, declared)).containsExactly("integrationTestImplementation -> :patra-tool:patra-tool-batch")
    }

    @Test
    fun r2_ignores_third_party_and_self() {
        assertThat(ToolDependencyRules.r2Violations(jpa, listOf(d("api", jpa)))).isEmpty()
    }
```

Run: `./gradlew -p build-logic test --tests ToolDependencyRulesTest` → 前 4 条新用例 FAIL。

- [ ] **Step 2: 实现 `r2Violations`**

```kotlin
    fun r2Violations(project: String, declared: List<Declared>): List<String> {
        val compileOnlyTargets = declared.filter { it.configuration == "compileOnly" }.map { it.target }.toSet()
        val whitelist = HARD_WHITELIST[project].orEmpty()
        return declared
            .filter { it.target.startsWith(":patra-tool:") && it.target != project }
            .filterNot { dependency ->
                val target = dependency.target
                when {
                    dependency.configuration == "compileOnly" -> true
                    dependency.configuration in HARD_CONFIGURATIONS -> target == CORE || target in whitelist
                    isTestConfiguration(dependency.configuration) -> target == CORE || target == TEST || target in compileOnlyTargets
                    else -> true
                }
            }
            .map { "${it.configuration} -> ${it.target}" }
    }
```

Run: 同上 → PASS。

- [ ] **Step 3: jpa 对 web 改可选**

`patra-tool-jpa/build.gradle.kts`：`compileOnly(project(":patra-tool:patra-tool-web"))`（Task 5 已改，核对没有 `api(web)`）；`testImplementation(project(":patra-tool:patra-tool-web"))`（契约测试要能看到 `ErrorMappingContributor`）。
`JpaErrorMappingContributor`：删 `@Component` 与它的 import，保留类上的 `@Order`。
`JpaAutoConfiguration`：

```java
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass(JpaRepository.class)
@Import({JpaAuditingConfig.class, HibernatePropertiesCustomizer.class})
public class JpaAutoConfiguration {

  /// JSON 列编解码，用容器的 `JsonMapper`。
  @Bean
  @ConditionalOnMissingBean
  JsonColumnCodec jsonColumnCodec(JsonMapper jsonMapper) {
    return new JsonColumnCodec(jsonMapper);
  }

  /// JPA / Hibernate / SQL 异常到错误码的映射，只在类路径上有 patra-tool-web 时注册。
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "dev.patra.tool.web.error.spi.ErrorMappingContributor")
  static class JpaErrorMappingConfiguration {

    /// 注册映射贡献者；顺序由类上的 @Order 决定，先于服务自己的贡献者。
    @Bean
    @ConditionalOnMissingBean
    JpaErrorMappingContributor jpaErrorMappingContributor(HttpStdErrors.Group http) {
      return new JpaErrorMappingContributor(http);
    }
  }
}
```

（`JpaErrorMappingContributor` 类上的 `@Order(Ordered.HIGHEST_PRECEDENCE)` 保留，Task 17 改成 `PatraOrders.ERROR_MAPPING_JPA`；贡献者列表按类级注解排序，`@Bean` 方法上不写 `@Order`。）

- [ ] **Step 4: observability 对 web 改可选**

`patra-tool-observability/build.gradle.kts`：删 `api(core)` 等（Task 5 已改），确认只有 `compileOnly(project(":patra-tool:patra-tool-web"))` + `testImplementation(project(":patra-tool:patra-tool-web"))`。
`ObservationInterceptorsAutoConfiguration`：把方法上的 `@ConditionalOnClass(name = "…ResolutionInterceptor")` 挪到类上（类级条件不满足时 Spring 不加载这个类，方法返回类型 `ObservationResolutionInterceptor` 实现的 web 接口就不会被解析）。

- [ ] **Step 5: 验证**

Run: `./gradlew check`（12 个 `checkToolDependencies` 通过；jpa / observability 单测通过）
Expected: BUILD SUCCESSFUL。

- [ ] **Step 6: 提交** `build(rules): R2 工具间硬依赖只指向 core 挂 check，jpa / observability 对 web 改可选 (PAP-80)`

---

### Task 15: R3 domain 编译类路径白名单

**Files:**
- Create: `build-logic/src/main/kotlin/DomainPurityRules.kt`、`build-logic/src/test/kotlin/DomainPurityRulesTest.kt`
- Modify: `build-logic/src/main/kotlin/patra.hexagonal-domain.gradle.kts`

- [ ] **Step 1: 失败测试**

```kotlin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DomainPurityRulesTest {

    @Test
    fun allows_only_the_whitelist() {
        val artifacts = listOf(
            "dev.patra:patra-tool-core", "dev.patra:patra-common", "org.projectlombok:lombok",
            "com.fasterxml.jackson.core:jackson-annotations", "tools.jackson.core:jackson-core",
            "tools.jackson.core:jackson-databind", "cn.hutool:hutool-core", "org.mapstruct:mapstruct",
        )
        assertThat(DomainPurityRules.violations(artifacts)).isEmpty()
    }

    @Test
    fun reports_everything_else_sorted() {
        val artifacts = listOf("org.springframework:spring-context", "dev.patra:patra-tool-web", "cn.hutool:hutool-core")
        assertThat(DomainPurityRules.violations(artifacts))
            .containsExactly("dev.patra:patra-tool-web", "org.springframework:spring-context")
    }
}
```

Run: `./gradlew -p build-logic test --tests DomainPurityRulesTest` → 编译失败。

- [ ] **Step 2: 实现**

```kotlin
/// R3：domain 模块编译类路径白名单（决策 C）。
object DomainPurityRules {

    /// 允许出现在 *-domain compileClasspath 上的构件（group:name）；JDK 不是构件，天然允许。
    val ALLOWED: Set<String> = setOf(
        "dev.patra:patra-tool-core",
        "dev.patra:patra-common",
        "org.projectlombok:lombok",
        "com.fasterxml.jackson.core:jackson-annotations",
        "tools.jackson.core:jackson-core",
        "tools.jackson.core:jackson-databind",
        "cn.hutool:hutool-core",
        "org.mapstruct:mapstruct",
    )

    /// 返回名单外的构件，排序后便于比对。
    fun violations(artifacts: Collection<String>): List<String> = artifacts.filterNot { it in ALLOWED }.distinct().sorted()
}
```

Run: 同上 → PASS。

- [ ] **Step 3: 改 `patra.hexagonal-domain.gradle.kts`**

删掉 `bannedGroups`、`DomainPurityCheck`、`enforceDomainPurity` 整段与头注释里"禁止/允许"列表，换成：

```kotlin
// ==================== R3：domain 编译类路径白名单 ====================
abstract class DomainPurityCheck : DefaultTask() {
    @get:Input abstract val modulePath: Property<String>
    @get:Input abstract val violations: ListProperty<String>

    @TaskAction
    fun check() {
        val found = violations.get()
        if (found.isNotEmpty()) {
            throw GradleException(
                "R3 违规：${modulePath.get()}\n" +
                    found.joinToString("\n") { "  compileClasspath 含 $it" } +
                    "\ndomain 编译类路径只允许：${DomainPurityRules.ALLOWED.joinToString(", ")}",
            )
        }
    }
}

tasks.register<DomainPurityCheck>("checkDomainPurity") {
    group = "verification"
    description = "R3：domain 模块编译类路径只允许白名单构件"
    modulePath.set(project.path)
    violations.set(provider {
        configurations.getByName("compileClasspath").resolvedConfiguration.resolvedArtifacts
            .map { "${it.moduleVersion.id.group}:${it.moduleVersion.id.name}" }
            .let(DomainPurityRules::violations)
    })
}

tasks.named("check") {
    dependsOn("checkDomainPurity")
}
```

- [ ] **Step 4: 验证**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-domain:checkDomainPurity :patra-api:patra-ingest:patra-ingest-domain:checkDomainPurity :patra-api:patra-registry:patra-registry-domain:checkDomainPurity :patra-api:patra-identity:patra-identity-domain:checkDomainPurity :patra-api:patra-object-storage:patra-object-storage-domain:checkDomainPurity`
Expected: 5 个都通过。若某个 domain 的类路径上多出构件（如 `jspecify`），把它的来源查清再决定进白名单还是改依赖，不要先放宽名单。然后 `./gradlew check`。

- [ ] **Step 5: 提交** `build(rules): R3 domain 纯净性改为编译类路径白名单 (PAP-80)`

---

### Task 16: R4 自动配置检查（实现 + 单测，先不挂 check）

**Files:**
- Create: `build-logic/src/main/kotlin/AutoConfigurationRules.kt`、`build-logic/src/test/kotlin/AutoConfigurationRulesTest.kt`
- Modify: `build-logic/src/main/kotlin/patra.tool-module.gradle.kts`（注册 `checkAutoConfiguration`，暂不 `dependsOn`）

**Interfaces:**
- Produces: `AutoConfigurationRules.check(input: AutoConfigurationRules.Input): List<String>`；任务 `checkAutoConfiguration`（Task 27 挂 `check`）。

- [ ] **Step 1: 失败测试（每条规则一个用例，用 `@TempDir` 造源码）**

```kotlin
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AutoConfigurationRulesTest {

    @TempDir lateinit var module: File

    private val pkgDir = "dev/patra/tool/demo"

    private fun src(path: String, body: String): File =
        File(module, "src/main/java/$path").apply { parentFile.mkdirs(); writeText(body.trimIndent()) }

    private fun test(path: String) =
        File(module, "src/test/java/$path").apply { parentFile.mkdirs(); writeText("class X {}") }

    private fun imports(vararg lines: String) =
        File(module, "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
            .apply { parentFile.mkdirs(); writeText(lines.joinToString("\n")) }

    private fun check() = AutoConfigurationRules.check(
        AutoConfigurationRules.Input(
            mainJava = File(module, "src/main/java"),
            importsFile = File(module, "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"),
            testJava = File(module, "src/test/java"),
            packageSegment = "demo",
            classPrefix = "Demo",
            configPrefix = "patra.demo",
        ),
    )

    private val goodAutoConfiguration = """
        package dev.patra.tool.demo.autoconfigure;
        @AutoConfiguration
        public final class DemoAutoConfiguration {}
    """

    @Test
    fun a_well_formed_module_has_no_problems() {
        src("$pkgDir/autoconfigure/DemoAutoConfiguration.java", goodAutoConfiguration)
        imports("dev.patra.tool.demo.autoconfigure.DemoAutoConfiguration")
        test("$pkgDir/autoconfigure/DemoAutoConfigurationTest.java")
        assertThat(check()).isEmpty()
    }

    @Test
    fun rule_a_requires_package_final_name_imports_and_test() {
        src("$pkgDir/impl/XAutoConfiguration.java", """
            package dev.patra.tool.demo.impl;
            @AutoConfiguration
            public class XAutoConfiguration {}
        """)
        imports()
        assertThat(check()).anySatisfy { assertThat(it).contains("规则 a") }
            .anySatisfy { assertThat(it).contains("public final class") }
            .anySatisfy { assertThat(it).contains("Demo<Aspect>AutoConfiguration") }
            .anySatisfy { assertThat(it).contains("没有登记") }
            .anySatisfy { assertThat(it).contains("缺少契约测试") }
    }

    @Test
    fun rule_b_rejects_imports_without_a_class() {
        imports("dev.patra.tool.demo.autoconfigure.GhostAutoConfiguration")
        assertThat(check()).singleElement().asString().contains("规则 b", "GhostAutoConfiguration")
    }

    @Test
    fun rule_c_rejects_stereotypes() {
        src("$pkgDir/Svc.java", """
            package dev.patra.tool.demo;
            @Component
            public class Svc {}
        """)
        assertThat(check()).singleElement().asString().contains("规则 c")
    }

    @Test
    fun rule_d_allows_configuration_only_inside_autoconfigure() {
        src("$pkgDir/Cfg.java", """
            package dev.patra.tool.demo;
            @Configuration
            public class Cfg {}
        """)
        src("$pkgDir/autoconfigure/Nested.java", """
            package dev.patra.tool.demo.autoconfigure;
            @Configuration(proxyBeanMethods = false)
            class Nested {}
        """)
        assertThat(check()).singleElement().asString().contains("规则 d", "Cfg.java")
    }

    @Test
    fun rule_e_rejects_bare_numeric_order_but_not_constants() {
        src("$pkgDir/A.java", """
            package dev.patra.tool.demo;
            public class A { @Order(100) void x() {} @Order(PatraOrders.X) void y() {} }
        """)
        assertThat(check()).singleElement().asString().contains("规则 e")
    }

    @Test
    fun rule_f_rejects_mapper_building_outside_autoconfigure() {
        src("$pkgDir/J.java", """
            package dev.patra.tool.demo;
            public class J { Object m = JsonMapper.builder().build(); }
        """)
        assertThat(check()).singleElement().asString().contains("规则 f")
    }

    @Test
    fun rule_g_requires_record_in_autoconfigure_with_module_prefix() {
        src("$pkgDir/autoconfigure/DemoProperties.java", """
            package dev.patra.tool.demo.autoconfigure;
            @ConfigurationProperties(prefix = "linqibin.starter.demo")
            public class DemoProperties {}
        """)
        assertThat(check()).anySatisfy { assertThat(it).contains("必须是 record") }
            .anySatisfy { assertThat(it).contains("配置前缀必须是 patra.demo") }
    }

    @Test
    fun comments_do_not_count() {
        src("$pkgDir/C.java", """
            package dev.patra.tool.demo;
            /// 例子：`@Component` 与 @Order(100) 只是文档
            // @Configuration
            public class C {}
        """)
        assertThat(check()).isEmpty()
    }
}
```

Run: `./gradlew -p build-logic test --tests AutoConfigurationRulesTest` → 编译失败。

- [ ] **Step 2: 实现 `AutoConfigurationRules.kt`**

```kotlin
import java.io.File

/// R4：自动配置写法检查（spec 4.5 的规则 a–g），按源码文本判定，不依赖编译产物。
object AutoConfigurationRules {

    /// 一个工具模块的输入：三个目录 / 文件可以不存在（纯 Java 模块没有自动配置）。
    data class Input(
        val mainJava: File?,
        val importsFile: File?,
        val testJava: File?,
        val packageSegment: String,
        val classPrefix: String,
        val configPrefix: String,
    )

    private val STEREOTYPE = Regex("""^\s*@(Component|Service|Repository)\b""")
    private val CONFIGURATION = Regex("""^\s*@Configuration\b""")
    private val AUTO_CONFIGURATION = Regex("""^\s*@AutoConfiguration\b""")
    private val BARE_ORDER = Regex("""@Order\(\s*-?\d""")
    private val MAPPER_BUILD = Regex("""JsonMapper\.builder\(\)|XmlMapper\.builder\(\)|new ObjectMapper\(|new JsonMapper\(""")
    private val CONFIG_PROPERTIES = Regex("""@ConfigurationProperties\b""")
    private val CONFIG_PREFIX = Regex("""@ConfigurationProperties\s*\(\s*(?:prefix\s*=\s*)?"([^"]+)"""")
    private val PACKAGE = Regex("""^package\s+([\w.]+)\s*;""", RegexOption.MULTILINE)
    private val PUBLIC_FINAL_CLASS = Regex("""^public\s+final\s+class\s+(\w+)""", RegexOption.MULTILINE)
    private val TOP_LEVEL_TYPE = Regex("""^(?:public\s+)?(?:final\s+|abstract\s+)*(?:class|interface|enum|record)\s+(\w+)""", RegexOption.MULTILINE)
    private val RECORD = Regex("""\brecord\s+\w+\s*\(""")

    /// 返回违规描述列表，空表示通过。
    fun check(input: Input): List<String> {
        val problems = mutableListOf<String>()
        val autoConfigurePackage = "dev.patra.tool.${input.packageSegment}.autoconfigure"
        val mainJava = input.mainJava?.takeIf { it.isDirectory }
        val sources = mainJava?.walkTopDown()?.filter { it.isFile && it.extension == "java" }?.sortedBy { it.path }?.toList().orEmpty()
        val imports = input.importsFile?.takeIf { it.isFile }?.readLines()
            ?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") }.orEmpty()
        val declared = mutableSetOf<String>()

        for (file in sources) {
            val rel = file.relativeTo(mainJava!!).path
            val code = stripComments(file.readText())
            val pkg = PACKAGE.find(code)?.groupValues?.get(1).orEmpty()
            val inAutoConfigure = pkg == autoConfigurePackage
            code.lines().forEachIndexed { index, line ->
                val no = index + 1
                if (STEREOTYPE.containsMatchIn(line)) problems += "$rel:$no 类上有 ${line.trim()}（规则 c：工具模块不用构造型注解）"
                if (!inAutoConfigure && CONFIGURATION.containsMatchIn(line)) problems += "$rel:$no @Configuration 只能出现在 $autoConfigurePackage（规则 d）"
                if (BARE_ORDER.containsMatchIn(line)) problems += "$rel:$no 裸数字 @Order，改用 PatraOrders 常量（规则 e）"
                if (!inAutoConfigure && MAPPER_BUILD.containsMatchIn(line)) problems += "$rel:$no autoconfigure 包以外不得自建 Jackson mapper（规则 f）"
            }
            if (CONFIG_PROPERTIES.containsMatchIn(code)) {
                if (!inAutoConfigure) problems += "$rel @ConfigurationProperties 只能出现在 $autoConfigurePackage（规则 g）"
                if (!RECORD.containsMatchIn(code)) problems += "$rel 配置类必须是 record（规则 g）"
                val prefix = CONFIG_PREFIX.find(code)?.groupValues?.get(1)
                val ok = prefix != null && (prefix == input.configPrefix || prefix.startsWith(input.configPrefix + "."))
                if (!ok) problems += "$rel 配置前缀必须是 ${input.configPrefix}，实际 ${prefix ?: "未写"}（规则 g）"
            }
            if (code.lines().any { AUTO_CONFIGURATION.containsMatchIn(it) }) {
                val name = TOP_LEVEL_TYPE.find(code)?.groupValues?.get(1) ?: file.nameWithoutExtension
                val fqn = "$pkg.$name"
                declared += fqn
                if (!inAutoConfigure) problems += "$rel 自动配置类必须在 $autoConfigurePackage（规则 a）"
                if (PUBLIC_FINAL_CLASS.find(code)?.groupValues?.get(1) != name) problems += "$rel 自动配置类必须声明为 public final class（规则 a）"
                if (!Regex("^${input.classPrefix}\\w*AutoConfiguration$").matches(name)) problems += "$rel 自动配置类名必须形如 ${input.classPrefix}<Aspect>AutoConfiguration（规则 a）"
                if (fqn !in imports) problems += "$rel 没有登记在 AutoConfiguration.imports（规则 a）"
                val testPath = "${pkg.replace('.', '/')}/${name}Test.java"
                if (input.testJava?.resolve(testPath)?.isFile != true) problems += "$rel 缺少契约测试 src/test/java/$testPath（规则 a）"
            }
        }
        for (fqn in imports) {
            if (fqn !in declared) problems += "imports 登记了 $fqn，但源码里没有这个带 @AutoConfiguration 的类（规则 b）"
        }
        return problems
    }

    /// 去掉块注释与行注释（含 `///` Javadoc），字符串里的 `//` 也会被截掉，对本检查无影响。
    fun stripComments(code: String): String =
        code.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.replace(Regex("""//.*$"""), "") }
}
```

Run: `./gradlew -p build-logic test --tests AutoConfigurationRulesTest` → PASS（哪条不过就修规则，不改用例的意图）。

- [ ] **Step 3: 在 `patra.tool-module.gradle.kts` 注册任务（先不挂 check）**

追加：

```kotlin
abstract class AutoConfigurationCheck : DefaultTask() {
    @get:Input abstract val modulePath: Property<String>
    @get:Input abstract val packageSegment: Property<String>
    @get:Input abstract val classPrefix: Property<String>
    @get:Input abstract val configPrefix: Property<String>
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val sourceFiles: ConfigurableFileCollection
    @get:Internal abstract val mainJava: DirectoryProperty
    @get:Internal abstract val importsFile: RegularFileProperty
    @get:Internal abstract val testJava: DirectoryProperty

    @TaskAction
    fun check() {
        val problems = AutoConfigurationRules.check(
            AutoConfigurationRules.Input(
                mainJava.asFile.orNull, importsFile.asFile.orNull, testJava.asFile.orNull,
                packageSegment.get(), classPrefix.get(), configPrefix.get(),
            ),
        )
        if (problems.isNotEmpty()) {
            throw GradleException(
                "R4 违规：${modulePath.get()}\n" + problems.joinToString("\n") { "  $it" } +
                    "\n工具模块的 bean 一律由 autoconfigure 包里的自动配置注册，每个自动配置类配同名 ApplicationContextRunner 测试。",
            )
        }
    }
}

val capability = project.name.removePrefix("patra-tool-")
val importsPath = "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"

tasks.register<AutoConfigurationCheck>("checkAutoConfiguration") {
    group = "verification"
    description = "R4：自动配置写法（包位置、public final、imports 双向一致、契约测试、无构造型注解）"
    modulePath.set(project.path)
    packageSegment.set(capability.replace("-", ""))
    classPrefix.set(capability.split("-").joinToString("") { it.replaceFirstChar(Char::uppercase) })
    configPrefix.set("patra.$capability")
    mainJava.set(layout.projectDirectory.dir("src/main/java"))
    importsFile.set(layout.projectDirectory.file(importsPath))
    testJava.set(layout.projectDirectory.dir("src/test/java"))
    sourceFiles.from(fileTree("src/main/java"), fileTree("src/test/java"), layout.projectDirectory.file(importsPath))
}
```

（`tasks.named("check") { dependsOn("checkAutoConfiguration") }` 在 Task 27 加。）

- [ ] **Step 4: 看一眼现状有多少违规（只看，不修）**

Run: `./gradlew checkAutoConfiguration --continue 2>&1 | grep -c '规则'`
Expected: 几十条；记下数字写进 Task 27 的对照。

- [ ] **Step 5: 提交** `build(rules): R4 自动配置写法检查实现与单测，暂不挂 check (PAP-80)`

---

## C · PAP-81 starter 模板与默认配置

### Task 17: `PatraOrders` 常量与 `patra.spring-starter` 模板依赖

**Files:**
- Create: `patra-tool/patra-tool-core/src/main/java/dev/patra/tool/core/PatraOrders.java`、`patra-tool/patra-tool-core/src/test/java/dev/patra/tool/core/PatraOrdersTest.java`
- Modify: `build-logic/src/main/kotlin/patra.spring-starter.gradle.kts`；所有现有 `@Order(…)`：`GlobalRestExceptionHandler`、`SecurityExceptionRethrowAdvice`、`JpaErrorMappingContributor`、三个命令拦截器、`MicrometerAutoConfiguration` 两个 `MeterFilter` bean、`RestClientAutoConfiguration.loggingInterceptor`、`LongToStringHttpMessageConvertersCustomizer`（删它的 `ORDER` 常量）

**Interfaces:**
- Produces: `dev.patra.tool.core.PatraOrders` 的常量（spec 5.7 表）。

- [ ] **Step 1: 失败测试**

```java
package dev.patra.tool.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PatraOrdersTest {

  @Test
  void security_rethrow_runs_before_global_handler() {
    assertThat(PatraOrders.ERROR_ADVICE_SECURITY_RETHROW).isLessThan(PatraOrders.ERROR_ADVICE_GLOBAL);
  }

  @Test
  void command_interceptors_run_tracing_then_logging_then_metrics() {
    assertThat(PatraOrders.CQRS_INTERCEPTOR_TRACING)
        .isLessThan(PatraOrders.CQRS_INTERCEPTOR_LOGGING)
        .isLessThan(PatraOrders.CQRS_INTERCEPTOR_METRICS);
  }

  @Test
  void high_cardinality_filter_runs_before_common_tags() {
    assertThat(PatraOrders.METER_FILTER_HIGH_CARDINALITY).isLessThan(PatraOrders.METER_FILTER_COMMON_TAGS);
  }
}
```

Run: `./gradlew :patra-tool:patra-tool-core:test --tests '*PatraOrdersTest*'` → 编译失败。

- [ ] **Step 2: 实现**

```java
package dev.patra.tool.core;

/// 工具模块里过滤器、advice、拦截器、贡献者的顺序常量，全仓只在这里写数字。
///
/// core 是纯 Java，用 `Integer.MIN_VALUE` / `MAX_VALUE` 表示 Spring `Ordered` 的最高 / 最低优先级（值相同）。
public final class PatraOrders {

  /// 最高优先级，等于 `Ordered.HIGHEST_PRECEDENCE`。
  public static final int HIGHEST = Integer.MIN_VALUE;

  /// 最低优先级，等于 `Ordered.LOWEST_PRECEDENCE`。
  public static final int LOWEST = Integer.MAX_VALUE;

  /// security 的 advice：把 Spring Security 异常原样抛回过滤器链，必须排在全局处理器之前。
  public static final int ERROR_ADVICE_SECURITY_RETHROW = HIGHEST;

  /// web 的全局异常处理器。
  public static final int ERROR_ADVICE_GLOBAL = HIGHEST + 1;

  /// jpa 的错误映射贡献者，先于服务自己的贡献者。
  public static final int ERROR_MAPPING_JPA = HIGHEST;

  /// 命令拦截器：追踪最外层。
  public static final int CQRS_INTERCEPTOR_TRACING = 50;

  /// 命令拦截器：日志。
  public static final int CQRS_INTERCEPTOR_LOGGING = 100;

  /// 命令拦截器：指标最内层。
  public static final int CQRS_INTERCEPTOR_METRICS = 200;

  /// 高基数标签过滤器最先执行。
  public static final int METER_FILTER_HIGH_CARDINALITY = HIGHEST;

  /// 公共标签过滤器最后执行。
  public static final int METER_FILTER_COMMON_TAGS = LOWEST;

  /// http-client 的请求日志拦截器。
  public static final int HTTP_CLIENT_LOGGING_INTERCEPTOR = 100;

  /// web 的 Long→String 消息转换器定制，排在 Boot 的 0 之后。
  public static final int WEB_JSON_LONG_TO_STRING = 100;

  /// 出默认配置的 EnvironmentPostProcessor，最后运行。
  public static final int DEFAULTS_POST_PROCESSOR = LOWEST;

  private PatraOrders() {}
}
```

Run: 同上 → PASS。

- [ ] **Step 3: 替换现有 `@Order`**

每处改成 `@Order(PatraOrders.<常量>)` 并 import `dev.patra.tool.core.PatraOrders`；`LongToStringHttpMessageConvertersCustomizer.getOrder()` 返回 `PatraOrders.WEB_JSON_LONG_TO_STRING`，删 `public static final int ORDER`（`WebJsonAutoConfigurationTest` 若引用了 `ORDER` 一起改）。

Run: `grep -rn '@Order(' patra-tool/*/src/main | grep -v PatraOrders`
Expected: 零命中。

- [ ] **Step 4: 模板依赖进插件**

`patra.spring-starter.gradle.kts` 的 `dependencies {}`：

```kotlin
dependencies {
    // 配置元数据与自动配置元数据（Boot 官方要求 autoconfigure 模块都加）
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")
    // record 配置类上的 @Validated / @Min 等；运行时没有校验器就当没写
    compileOnly("jakarta.validation:jakarta.validation-api")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

各模块 `build.gradle.kts` 里重复的 `annotationProcessor(spring-boot-configuration-processor)` / `annotationProcessor(spring-boot-autoconfigure-processor)` 删掉。

- [ ] **Step 5: 验证并提交**

Run: `./gradlew check`
```bash
git add build-logic patra-tool
git commit -m "feat(core): 新增 PatraOrders 顺序常量，starter 插件统一模板依赖 (PAP-81)"
```

---

### Task 18: cqrs 模板：`CqrsProperties`、`CqrsAutoConfiguration`、契约测试

**Files:**
- Create: `patra-tool/patra-tool-cqrs/src/main/java/dev/patra/tool/cqrs/autoconfigure/{CqrsProperties, CqrsAutoConfiguration}.java`、`src/test/java/dev/patra/tool/cqrs/autoconfigure/CqrsAutoConfigurationTest.java`
- Delete: `cqrs/{CommandBusAutoConfiguration, CommandBusProperties}.java`、`cqrs/interceptor/CommandInterceptorAutoConfiguration.java`
- Modify: 三个拦截器（删 `@Component`、`@ConditionalOn*`、`@Order` 保留为常量）、imports 文件（只剩 `dev.patra.tool.cqrs.autoconfigure.CqrsAutoConfiguration`）

**Interfaces:**
- Produces: 键 `patra.cqrs.{enabled, async.*, interceptors.*}`；bean `commandBus`、`commandBusAsyncExecutor`、`loggingCommandInterceptor`、`tracingCommandInterceptor`、`metricsCommandInterceptor`。

- [ ] **Step 1: 失败的契约测试**

```java
package dev.patra.tool.cqrs.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.patra.tool.cqrs.CommandBus;
import dev.patra.tool.cqrs.interceptor.LoggingCommandInterceptor;
import dev.patra.tool.cqrs.interceptor.MetricsCommandInterceptor;
import dev.patra.tool.cqrs.interceptor.TracingCommandInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CqrsAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CqrsAutoConfiguration.class));

  @Test
  void should_register_command_bus_and_logging_interceptor_by_default() {
    runner.run(
        ctx ->
            assertThat(ctx)
                .hasSingleBean(CommandBus.class)
                .hasSingleBean(LoggingCommandInterceptor.class)
                .doesNotHaveBean(TracingCommandInterceptor.class)
                .doesNotHaveBean(MetricsCommandInterceptor.class));
  }

  @Test
  void should_register_nothing_when_disabled() {
    runner
        .withPropertyValues("patra.cqrs.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(CommandBus.class));
  }

  @Test
  void should_back_off_when_user_defines_command_bus() {
    CommandBus custom = mock(CommandBus.class);
    runner
        .withBean("customBus", CommandBus.class, () -> custom)
        .run(ctx -> assertThat(ctx).getBean(CommandBus.class).isSameAs(custom));
  }

  @Test
  void should_add_tracing_and_metrics_interceptors_when_registries_exist() {
    runner
        .withBean(ObservationRegistry.class, ObservationRegistry::create)
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .run(
            ctx ->
                assertThat(ctx)
                    .hasSingleBean(TracingCommandInterceptor.class)
                    .hasSingleBean(MetricsCommandInterceptor.class));
  }

  @Test
  void should_skip_optional_interceptors_without_micrometer_on_classpath() {
    runner
        .withClassLoader(new FilteredClassLoader(ObservationRegistry.class, MeterRegistry.class))
        .run(ctx -> assertThat(ctx).hasSingleBean(CommandBus.class).hasSingleBean(LoggingCommandInterceptor.class));
  }

  @Test
  void should_honour_interceptor_switches() {
    runner
        .withPropertyValues("patra.cqrs.interceptors.logging=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(LoggingCommandInterceptor.class));
  }
}
```

Run: `./gradlew :patra-tool:patra-tool-cqrs:test --tests '*CqrsAutoConfigurationTest*'` → 编译失败。

- [ ] **Step 2: `CqrsProperties`**（spec 5.2 模板原文）

```java
package dev.patra.tool.cqrs.autoconfigure;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// 命令总线配置，前缀 `patra.cqrs`。
///
/// @param enabled 总开关，关掉后本模块不注册任何 bean
/// @param async 异步执行器
/// @param interceptors 三个内置拦截器的开关
@Validated
@ConfigurationProperties(prefix = "patra.cqrs")
public record CqrsProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue Async async,
    @DefaultValue Interceptors interceptors) {

  /// 异步执行器的线程池参数。
  ///
  /// @param corePoolSize 核心线程数
  /// @param maxPoolSize 最大线程数
  /// @param queueCapacity 队列容量
  /// @param threadNamePrefix 线程名前缀
  public record Async(
      @DefaultValue("4") @Min(1) int corePoolSize,
      @DefaultValue("16") @Min(1) int maxPoolSize,
      @DefaultValue("100") @Min(0) int queueCapacity,
      @DefaultValue("cmd-bus-") String threadNamePrefix) {}

  /// 内置拦截器开关。
  ///
  /// @param logging 日志拦截器
  /// @param tracing 追踪拦截器（还要类路径上有 Observation 且容器里有 `ObservationRegistry`）
  /// @param metrics 指标拦截器（还要容器里有 `MeterRegistry`）
  public record Interceptors(
      @DefaultValue("true") boolean logging,
      @DefaultValue("true") boolean tracing,
      @DefaultValue("true") boolean metrics) {}
}
```

- [ ] **Step 3: `CqrsAutoConfiguration`**

```java
package dev.patra.tool.cqrs.autoconfigure;

import dev.patra.tool.cqrs.CommandBus;
import dev.patra.tool.cqrs.CommandHandler;
import dev.patra.tool.cqrs.CommandInterceptor;
import dev.patra.tool.cqrs.SimpleCommandBus;
import dev.patra.tool.cqrs.interceptor.LoggingCommandInterceptor;
import dev.patra.tool.cqrs.interceptor.MetricsCommandInterceptor;
import dev.patra.tool.cqrs.interceptor.TracingCommandInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/// 命令总线自动配置：`SimpleCommandBus`、异步执行器与三个内置拦截器。
///
/// 排在 Boot 的任务执行器之后，否则这里的 `Executor` bean 会让 `applicationTaskExecutor` 退让；
/// 排在可观测性之后，`ObservationRegistry` / `MeterRegistry` 存在与否才判得准。
@AutoConfiguration(
    afterName = {
      "org.springframework.boot.task.autoconfigure.TaskExecutionAutoConfiguration",
      "org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration",
      "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
      "dev.patra.tool.observability.autoconfigure.ObservabilityAutoConfiguration"
    })
@ConditionalOnProperty(prefix = "patra.cqrs", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CqrsProperties.class)
public final class CqrsAutoConfiguration {

  /// 命令总线，收集容器里全部 handler 与拦截器。
  @Bean
  @ConditionalOnMissingBean
  CommandBus commandBus(
      List<CommandHandler<?, ?>> handlers,
      List<CommandInterceptor> interceptors,
      @Qualifier("commandBusAsyncExecutor") Executor commandBusAsyncExecutor) {
    return new SimpleCommandBus(handlers, interceptors, commandBusAsyncExecutor);
  }

  /// `handleAsync` 用的线程池。
  @Bean
  @ConditionalOnMissingBean(name = "commandBusAsyncExecutor")
  Executor commandBusAsyncExecutor(CqrsProperties properties) {
    CqrsProperties.Async async = properties.async();
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(async.corePoolSize());
    executor.setMaxPoolSize(async.maxPoolSize());
    executor.setQueueCapacity(async.queueCapacity());
    executor.setThreadNamePrefix(async.threadNamePrefix());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }

  /// 日志拦截器，无外部依赖。
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(prefix = "patra.cqrs.interceptors", name = "logging", havingValue = "true", matchIfMissing = true)
  static class LoggingInterceptorConfiguration {

    /// 注册日志拦截器。
    @Bean
    @ConditionalOnMissingBean
    LoggingCommandInterceptor loggingCommandInterceptor() {
      return new LoggingCommandInterceptor();
    }
  }

  /// 追踪拦截器，要有 Micrometer Observation 且容器里已有 `ObservationRegistry`。
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(ObservationRegistry.class)
  @ConditionalOnBean(ObservationRegistry.class)
  @ConditionalOnProperty(prefix = "patra.cqrs.interceptors", name = "tracing", havingValue = "true", matchIfMissing = true)
  static class TracingInterceptorConfiguration {

    /// 注册追踪拦截器。
    @Bean
    @ConditionalOnMissingBean
    TracingCommandInterceptor tracingCommandInterceptor(ObservationRegistry observationRegistry) {
      return new TracingCommandInterceptor(observationRegistry);
    }
  }

  /// 指标拦截器，要有 Micrometer Core 且容器里已有 `MeterRegistry`。
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(MeterRegistry.class)
  @ConditionalOnBean(MeterRegistry.class)
  @ConditionalOnProperty(prefix = "patra.cqrs.interceptors", name = "metrics", havingValue = "true", matchIfMissing = true)
  static class MetricsInterceptorConfiguration {

    /// 注册指标拦截器。
    @Bean
    @ConditionalOnMissingBean
    MetricsCommandInterceptor metricsCommandInterceptor(MeterRegistry meterRegistry) {
      return new MetricsCommandInterceptor(meterRegistry);
    }
  }
}
```

三个拦截器类：删 `@Component` 与所有 `@ConditionalOn*`（条件归嵌套配置），保留 `@Order(PatraOrders.CQRS_INTERCEPTOR_*)`（类级注解，`SimpleCommandBus` 用 `AnnotationAwareOrderComparator` 排序）。删 `CommandBusAutoConfiguration`、`CommandBusProperties`、`CommandInterceptorAutoConfiguration`；imports 只剩新类。

Run: `./gradlew :patra-tool:patra-tool-cqrs:test` → PASS（含搬来的 `SimpleCommandBusTest`、`LoggingCommandInterceptorTest`）。

- [ ] **Step 4: 服务配置键**

全仓 grep `command-bus`：没有服务配置过它（盘点结论），只需 README。

- [ ] **Step 5: 验证并提交**

Run: `./gradlew check`
```bash
git add patra-tool/patra-tool-cqrs
git commit -m "feat(cqrs): 命令总线自动配置按模板重写，拦截器改由自动配置注册 (PAP-81)"
```

---

### Task 19: web 模板：`WebProperties`、合并错误自动配置、切片登记、删三份 error-config

**Files:**
- Create: `patra-tool/patra-tool-web/src/main/java/dev/patra/tool/web/autoconfigure/{WebProperties, WebErrorAutoConfiguration, WebJsonAutoConfiguration}.java`、`src/main/resources/META-INF/spring/org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc.imports`、`src/test/java/dev/patra/tool/web/autoconfigure/WebErrorAutoConfigurationTest.java`
- Delete: `web/error/config/{CoreErrorAutoConfiguration, ErrorProperties, TracingProperties, WebErrorAutoConfiguration, WebErrorProperties}.java`、`web/json/config/WebJsonAutoConfiguration.java`（搬到 autoconfigure）、三份 `patra-api/*/…-boot/src/main/resources/*-error-config.yaml`、`META-INF/additional-spring-configuration-metadata.json`（原 starter-core 的，三个键都由 record 生成）
- Modify: `DefaultErrorResolutionEngine`、`ProblemDetailBuilder`、`HeaderBasedTraceProvider` 的构造参数；`WebJsonAutoConfigurationTest`（搬包）；imports 文件；6 个 boot 的 `application.yml`；security IT 的 `application.yml`；`patra-api/{object-storage,identity,registry,catalog}-adapter` 的 `*ITWebMvcConfig`、`patra-tool-web/src/integrationTest/.../WebErrorITWebMvcConfig`、`patra-tool-security/src/integrationTest/.../SecurityWebMvcSliceIT`、`SecurityServletAutoConfigurationTest`（`AutoConfigurations.of(CoreError…, WebError…)` → `WebErrorAutoConfiguration`）

**Interfaces:**
- Produces: 键 `patra.web.{enabled, error.{enabled, context-prefix, type-base-url, include-stack, engine.*, observation.*, trace.header-names}}`；`WebProperties.Error` 作为引擎 / builder / trace provider 的构造参数类型；切片 imports 文件。

- [ ] **Step 1: 失败的契约测试**

```java
package dev.patra.tool.web.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.core.error.codes.HttpStdErrors;
import dev.patra.tool.web.error.adapter.ProblemDetailAdapter;
import dev.patra.tool.web.error.engine.ErrorResolutionEngine;
import dev.patra.tool.web.error.handler.GlobalRestExceptionHandler;
import dev.patra.tool.web.error.pipeline.ErrorResolutionPipeline;
import dev.patra.tool.web.error.spi.TraceProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class WebErrorAutoConfigurationTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(WebErrorAutoConfiguration.class))
          .withPropertyValues("patra.web.error.context-prefix=TST");

  @Test
  void should_register_engine_pipeline_and_handler_by_default() {
    runner.run(
        ctx -> {
          assertThat(ctx)
              .hasSingleBean(ErrorResolutionEngine.class)
              .hasSingleBean(ErrorResolutionPipeline.class)
              .hasSingleBean(TraceProvider.class)
              .hasSingleBean(ProblemDetailAdapter.class)
              .hasSingleBean(GlobalRestExceptionHandler.class);
          assertThat(ctx.getBean(HttpStdErrors.Group.class).notFound().code()).startsWith("TST-");
        });
  }

  @Test
  void should_register_nothing_when_module_disabled() {
    runner
        .withPropertyValues("patra.web.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(GlobalRestExceptionHandler.class).doesNotHaveBean(ErrorResolutionEngine.class));
  }

  @Test
  void should_back_off_when_user_defines_trace_provider() {
    TraceProvider custom = () -> java.util.Optional.of("fixed");
    runner
        .withBean("customTrace", TraceProvider.class, () -> custom)
        .run(ctx -> assertThat(ctx).getBean(TraceProvider.class).isSameAs(custom));
  }

  @Test
  void should_bind_nested_defaults() {
    runner.run(
        ctx -> {
          WebProperties props = ctx.getBean(WebProperties.class);
          assertThat(props.error().engine().maxCauseDepth()).isEqualTo(10);
          assertThat(props.error().trace().headerNames()).containsExactly("traceId", "X-B3-TraceId", "traceparent");
        });
  }
}
```

（`HttpStdErrors.Group#notFound()` 的方法名以现有类为准；`TraceProvider` 的函数式签名以现有接口为准，不对就改测试里的 lambda。）

Run: `./gradlew :patra-tool:patra-tool-web:test --tests '*WebErrorAutoConfigurationTest*'` → 编译失败。

- [ ] **Step 2: `WebProperties`**

```java
package dev.patra.tool.web.autoconfigure;

import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// Web 模块配置，前缀 `patra.web`。
///
/// @param enabled 总开关
/// @param error 错误解析与 ProblemDetail 输出
@Validated
@ConfigurationProperties(prefix = "patra.web")
public record WebProperties(@DefaultValue("true") boolean enabled, @DefaultValue Error error) {

  /// 错误处理。
  ///
  /// @param enabled 关掉后不注册引擎与全局处理器
  /// @param contextPrefix 错误码前缀（CAT、REG…），空则用 UNKNOWN
  /// @param typeBaseUrl ProblemDetail `type` 的基址
  /// @param includeStack 响应里是否带堆栈
  /// @param engine 解析引擎
  /// @param observation 慢解析观测
  /// @param trace 追踪 ID 来源
  public record Error(
      @DefaultValue("true") boolean enabled,
      String contextPrefix,
      @DefaultValue("https://errors.example.com/") String typeBaseUrl,
      @DefaultValue("false") boolean includeStack,
      @DefaultValue Engine engine,
      @DefaultValue Observation observation,
      @DefaultValue Trace trace) {

    /// 解析引擎参数。
    ///
    /// @param maxCauseDepth 遍历 cause 链的最大深度
    /// @param enableTraitMapping 是否按 ErrorTrait 语义映射
    /// @param enableNamingHeuristic 是否按异常类名启发式映射
    public record Engine(
        @DefaultValue("10") @Min(1) int maxCauseDepth,
        @DefaultValue("true") boolean enableTraitMapping,
        @DefaultValue("true") boolean enableNamingHeuristic) {}

    /// 慢解析观测。
    ///
    /// @param enabled 开关
    /// @param slowThresholdMs 超过多少毫秒算慢
    /// @param logSlowResolution 慢解析是否打 WARN
    public record Observation(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("200") @Min(0) long slowThresholdMs,
        @DefaultValue("true") boolean logSlowResolution) {}

    /// 追踪 ID 从 MDC 的哪些键取。
    ///
    /// @param headerNames 候选键，按顺序取第一个非空的
    public record Trace(@DefaultValue({"traceId", "X-B3-TraceId", "traceparent"}) List<String> headerNames) {
      public Trace {
        headerNames = List.copyOf(headerNames);
      }
    }
  }
}
```

- [ ] **Step 3: 合并自动配置**

`WebErrorAutoConfiguration`（吸收 `CoreErrorAutoConfiguration` 的 4 个 bean；`public final`、`@AutoConfiguration`；类级 `@ConditionalOnWebApplication(type = SERVLET)`、`@ConditionalOnProperty(prefix = "patra.web", name = "enabled", havingValue = "true", matchIfMissing = true)`、`@ConditionalOnProperty(prefix = "patra.web.error", name = "enabled", havingValue = "true", matchIfMissing = true)`、`@EnableConfigurationProperties(WebProperties.class)`）；bean 方法包私有，顺序：`defaultTraceProvider(WebProperties)` → `new HeaderBasedTraceProvider(properties.error().trace().headerNames())`；`errorResolutionEngine(WebProperties, List<ErrorMappingContributor>)` → `new DefaultErrorResolutionEngine(properties.error(), contributors)`；`errorResolutionPipeline`；`httpStdErrorsGroup(WebProperties)`；`defaultValidationErrorsFormatter`；`problemDetailBuilder(WebProperties, TraceProvider, List<ProblemFieldContributor>, List<WebProblemFieldContributor>)` → `new ProblemDetailBuilder(properties.error(), …)`；`problemDetailAdapter`；`webMvcErrorMappingContributor`；`globalRestExceptionHandler`。日志语句照搬。
`DefaultErrorResolutionEngine`、`ProblemDetailBuilder`、`HeaderBasedTraceProvider` 构造参数改为 `WebProperties.Error` / `List<String>`，内部 `getXxx()` 改 record 访问器；对应单测同步。
`WebJsonAutoConfiguration` 搬到 `autoconfigure`，`public final`，加 `@ConditionalOnProperty(prefix = "patra.web", name = "enabled", …)`。
imports：`dev.patra.tool.web.autoconfigure.WebErrorAutoConfiguration`、`dev.patra.tool.web.autoconfigure.WebJsonAutoConfiguration`。
新建 `META-INF/spring/org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc.imports`，内容同上两行。

Run: `./gradlew :patra-tool:patra-tool-web:test` → PASS。

- [ ] **Step 4: 删切片里的手写导入**

6 处（object-storage / identity / registry / catalog 的 `*ITWebMvcConfig`、web 的 `WebErrorITWebMvcConfig`、security 的 `SecurityWebMvcSliceIT.SliceConfig`）删 `@ImportAutoConfiguration({CoreError…, WebError…, WebJson…})` 及 import（security 的保留它自己的两个 Security 自动配置）。`SecurityServletAutoConfigurationTest` 与其他 `AutoConfigurations.of(…)` 里把 `CoreErrorAutoConfiguration.class, WebErrorAutoConfiguration.class` 改成一个 `WebErrorAutoConfiguration.class`（新包）。

Run: `./gradlew :patra-api:patra-object-storage:patra-object-storage-adapter:integrationTest :patra-api:patra-identity:patra-identity-adapter:integrationTest :patra-api:patra-registry:patra-registry-adapter:integrationTest :patra-api:patra-catalog:patra-catalog-adapter:integrationTest :patra-tool:patra-tool-web:integrationTest :patra-tool:patra-tool-security:integrationTest`
Expected: 全部通过；object-storage 的切片现在也有 Long→String（它原来漏导 `WebJsonAutoConfiguration`，若有断言依赖数字型 Long 的测试要改成字符串）。

- [ ] **Step 5: 删 error-config，改服务 yml（spec 5.4 表）**

- registry / catalog / ingest：`git rm` 三份 `*-error-config.yaml`；`application.yml` 的 `spring.config.import` 删对应行（ingest 保留 `classpath:ingest-rocketmq.yaml`；registry / catalog 删空后整个 `spring.config:` 段删掉）；加 `patra.web.error.context-prefix: REG|CAT|ING`；把原 yaml 里的 `registry.error-catalog.*` / `catalog.error-catalog.*` / `ingest.error-catalog.*` 段原样贴进 `application.yml` 末尾；registry 加 `patra.web.error.type-base-url: https://errors.patra.com`。`linqibin.starter.http-interface.*` 的 `error-handling.*`、`observation.*` 一律不搬（默认值或死键）。
- identity、gateway：`linqibin.starter.core.error.context-prefix` → `patra.web.error.context-prefix`，删空的 `linqibin:` 树。
- ingest 的 `application-e2e-test.yml`、`application-integration-test.yml`、`application-test-common.yml` 与 catalog 的 `application-e2e-test.yml` 里对 `*-error-config.yaml` 的 `spring.config.import` 删；security IT 的 `application.yml` 改 `patra.web.error.context-prefix: TEST`。

Run: `grep -rn 'error-config\|linqibin.starter.core\|starter.web.problem' --include='*.yml' --include='*.yaml' --include='*.java' . | grep -v docs/`
Expected: 零命中。

- [ ] **Step 6: 验证并提交**

Run: `./gradlew check :patra-tool:patra-tool-launch:test`（`JarConfigOverlapCheckerTest` 检查 jar yml 与 Nacos 不重复）
```bash
git add -A -- ':!docs'
git commit -m "feat(web): 错误处理自动配置按模板合并，切片自动登记，删三份 error-config (PAP-81)"
```

---

### Task 20: jpa 模板

**Files:**
- Rename（`rename_refactoring` + 目录）：`jpa/autoconfig/` → `jpa/autoconfigure/`；`JpaAuditingConfig` → `JpaAuditingConfiguration`；`HibernatePropertiesCustomizer` → `PatraHibernatePropertiesCustomizer`
- Create: `jpa/autoconfigure/JpaProperties.java`、`src/test/java/dev/patra/tool/jpa/autoconfigure/JpaAutoConfigurationTest.java`
- Modify: `JpaAutoConfiguration`（`public final`、总开关、嵌套配置里的贡献者改 `@Order(PatraOrders.ERROR_MAPPING_JPA)` 放在类上）、`JpaAuditingConfiguration`（删 `clock()`，`dateTimeProvider(ObjectProvider<Clock>)`）、`PatraHibernatePropertiesCustomizer`（去掉全限定名写法：类改名后可直接 `implements HibernatePropertiesCustomizer`）、`spotbugs-exclude.xml`（删 `NM_SAME_SIMPLE_NAME_AS_INTERFACE` 条目）、9 个 `@DataJpaTest` IT 与 2 个单测里的 `@Import(JpaAuditingConfig.class, HibernatePropertiesCustomizer.class, …)`、imports 文件

- [ ] **Step 1: 失败的契约测试**

```java
package dev.patra.tool.jpa.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.jpa.error.contributor.JpaErrorMappingContributor;
import dev.patra.tool.jpa.json.JsonColumnCodec;
import dev.patra.tool.web.error.spi.ErrorMappingContributor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.auditing.DateTimeProvider;
import tools.jackson.databind.json.JsonMapper;

class JpaAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, JpaAutoConfiguration.class))
          .withBean(dev.patra.tool.core.error.codes.HttpStdErrors.Group.class, () -> dev.patra.tool.core.error.codes.HttpStdErrors.of("TST"));

  @Test
  void should_register_codec_auditing_and_error_mapping_by_default() {
    runner.run(
        ctx ->
            assertThat(ctx)
                .hasSingleBean(JsonColumnCodec.class)
                .hasSingleBean(DateTimeProvider.class)
                .hasSingleBean(JpaErrorMappingContributor.class)
                .doesNotHaveBean(java.time.Clock.class));
  }

  @Test
  void should_register_nothing_when_disabled() {
    runner.withPropertyValues("patra.jpa.enabled=false").run(ctx -> assertThat(ctx).doesNotHaveBean(JsonColumnCodec.class));
  }

  @Test
  void should_back_off_when_user_defines_codec() {
    runner
        .withBean("myCodec", JsonColumnCodec.class, () -> new JsonColumnCodec(JsonMapper.builder().build()))
        .run(ctx -> assertThat(ctx).getBean(JsonColumnCodec.class).isSameAs(ctx.getBean("myCodec")));
  }

  @Test
  void should_skip_error_mapping_without_web_on_classpath() {
    runner
        .withClassLoader(new FilteredClassLoader(ErrorMappingContributor.class))
        .run(ctx -> assertThat(ctx).hasSingleBean(JsonColumnCodec.class).doesNotHaveBean(JpaErrorMappingContributor.class));
  }
}
```

（测试里的 `HttpStdErrors` 写成 import，这里为了行宽写了全名，落地时改 import。`JsonMapper.builder()` 在测试代码里允许，R4 只扫 `src/main`。）

Run: `./gradlew :patra-tool:patra-tool-jpa:test --tests '*JpaAutoConfigurationTest*'` → 编译失败（`JpaProperties`、`patra.jpa.enabled` 不存在）。

- [ ] **Step 2: 实现**

`JpaProperties`：

```java
package dev.patra.tool.jpa.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// JPA 模块配置，前缀 `patra.jpa`。
///
/// @param enabled 总开关
@Validated
@ConfigurationProperties(prefix = "patra.jpa")
public record JpaProperties(@DefaultValue("true") boolean enabled) {}
```

`JpaAutoConfiguration`：`public final`；加 `@ConditionalOnProperty(prefix = "patra.jpa", name = "enabled", havingValue = "true", matchIfMissing = true)`、`@EnableConfigurationProperties(JpaProperties.class)`；`@Import({JpaAuditingConfiguration.class, PatraHibernatePropertiesCustomizer.class})`；嵌套配置里的 `@Bean` 删 `@Order`，改在 `JpaErrorMappingContributor` 类上 `@Order(PatraOrders.ERROR_MAPPING_JPA)`（Task 17 已改成常量，核对保留）。
`JpaAuditingConfiguration`：删 `clock()`；`dateTimeProvider(ObjectProvider<Clock> clock)` → `() -> Optional.of(Instant.now(clock.getIfAvailable(Clock::systemUTC)))`。
`PatraHibernatePropertiesCustomizer`：`public class PatraHibernatePropertiesCustomizer implements HibernatePropertiesCustomizer`（import Boot 的接口），保留 `@Configuration`（PAP-82 决定仍可被 `@DataJpaTest` 显式 `@Import`）。
9 个 IT 与单测改导入（`grep -rln 'JpaAuditingConfig\b\|HibernatePropertiesCustomizer' patra-api patra-tool --include='*.java'`）。imports 文件：`dev.patra.tool.jpa.autoconfigure.JpaAutoConfiguration`。

Run: `./gradlew :patra-tool:patra-tool-jpa:test :patra-tool:patra-tool-jpa:integrationTest :patra-api:patra-catalog:patra-catalog-infra:integrationTest` → PASS。

- [ ] **Step 3: 验证并提交**

Run: `./gradlew check`
```bash
git add -A -- ':!docs'
git commit -m "feat(jpa): JPA 自动配置按模板重写，Clock 交给 launch (PAP-81)"
```

---

### Task 21: batch 模板

**Files:**
- Rename：`batch/autoconfigure/BatchDataSourceConfiguration` → `BatchDataSourceAutoConfiguration`、`BatchSchemaInitializerConfiguration` → `BatchSchemaAutoConfiguration`、`BatchProgressMetricsAutoConfiguration` → `BatchMetricsAutoConfiguration`；`batch/config/BatchProperties` → `batch/autoconfigure/BatchProperties`（改成 record）；测试 `BatchProgressMetricsAutoConfigurationTest` → `BatchMetricsAutoConfigurationTest`、`BatchPropertiesTest` 搬包
- Create: `src/test/java/dev/patra/tool/batch/autoconfigure/{BatchAutoConfigurationTest, BatchDataSourceAutoConfigurationTest, BatchSchemaAutoConfigurationTest}.java`
- Modify: 四个自动配置类（`public final`、总开关、`afterName` 指 `dev.patra.tool.observability.autoconfigure.ObservabilityMicrometerAutoConfiguration`、`@ConditionalOnProperty(prefix = "patra.batch…")`）、`JobOperatorHelper` / `BatchSchemaInitializer` / `BatchProgressMetricsListener` 等读 `BatchProperties` 的地方改 record 访问器、imports 文件、catalog-infra 里 `BatchProperties` 的用法（`getChunk().getDefaultSize()` → `chunk().defaultSize()`）、`MeshScrImportE2E` 的属性 `patra.batch.chunk.default-size=2`、batch `application-test.yml`（`patra.batch.enabled`、`patra.batch.chunk.*`）、两个 IT 的属性键

**Interfaces:**
- Produces: 键 `patra.batch.{enabled, table-prefix, chunk.{default-size,max-size}, datasource.{url,username,password,driver-class-name,hikari.*}, schema.initialize, import-limit.max-records, metrics.enabled}`。

- [ ] **Step 1: 失败的契约测试（四个类各一份；这里给 `BatchAutoConfigurationTest`，其余三份同结构，断言各自的 bean）**

```java
package dev.patra.tool.batch.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.batch.core.JobOperatorHelper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class BatchAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(BatchAutoConfiguration.class));

  @Test
  void should_bind_defaults() {
    runner.run(
        ctx -> {
          BatchProperties props = ctx.getBean(BatchProperties.class);
          assertThat(props.tablePrefix()).isEqualTo("BATCH_");
          assertThat(props.chunk().defaultSize()).isEqualTo(5000);
          assertThat(props.datasource().configured()).isFalse();
          assertThat(props.importLimit().hasLimit()).isFalse();
        });
  }

  @Test
  void should_register_nothing_when_disabled() {
    runner.withPropertyValues("patra.batch.enabled=false").run(ctx -> assertThat(ctx).doesNotHaveBean(BatchProperties.class));
  }

  @Test
  void should_report_datasource_configured_when_url_set() {
    runner
        .withPropertyValues("patra.batch.datasource.url=jdbc:postgresql://x/y")
        .run(ctx -> assertThat(ctx.getBean(BatchProperties.class).datasource().configured()).isTrue());
  }
}
```

`BatchDataSourceAutoConfigurationTest`：有 `patra.batch.datasource.url` 时注册名为 `batchDataSource` 的 `DataSource`，没有时不注册；`BatchSchemaAutoConfigurationTest`：`patra.batch.schema.initialize=false` 时无 `BatchSchemaInitializer`；`BatchMetricsAutoConfigurationTest`：有 `MeterRegistry` bean 时注册 `BatchProgressMetricsListener`，`patra.batch.metrics.enabled=false` 时不注册，`FilteredClassLoader(MeterRegistry.class)` 下上下文正常。bean 名以现有类为准。

Run: `./gradlew :patra-tool:patra-tool-batch:test --tests '*AutoConfigurationTest*'` → 编译失败。

- [ ] **Step 2: `BatchProperties` record**

```java
package dev.patra.tool.batch.autoconfigure;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/// Spring Batch 基础设施配置，前缀 `patra.batch`。
///
/// @param enabled 总开关
/// @param tablePrefix JobRepository 表前缀
/// @param chunk 分块大小
/// @param datasource 独立的 Batch 元数据库；不配 `url` 则用主数据源
/// @param schema 启动时建表
/// @param importLimit 导入条数上限（调试用）
/// @param metrics 进度指标
@Validated
@ConfigurationProperties(prefix = "patra.batch")
public record BatchProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("BATCH_") String tablePrefix,
    @DefaultValue Chunk chunk,
    @DefaultValue Datasource datasource,
    @DefaultValue Schema schema,
    @DefaultValue ImportLimit importLimit,
    @DefaultValue Metrics metrics) {

  /// 分块大小。
  public record Chunk(@DefaultValue("5000") @Min(1) int defaultSize, @DefaultValue("10000") @Min(1) int maxSize) {}

  /// 独立数据源。
  public record Datasource(
      String url,
      String username,
      String password,
      String driverClassName,
      @DefaultValue Hikari hikari) {

    /// 是否配置了独立数据源。
    public boolean configured() {
      return StringUtils.hasText(url);
    }

    /// HikariCP 连接池参数（毫秒）。
    public record Hikari(
        @DefaultValue("5") @Min(1) int maximumPoolSize,
        @DefaultValue("2") @Min(0) int minimumIdle,
        @DefaultValue("30000") long connectionTimeout,
        @DefaultValue("600000") long idleTimeout,
        @DefaultValue("1800000") long maxLifetime) {}
  }

  /// 建表开关。
  public record Schema(@DefaultValue("true") boolean initialize) {}

  /// 导入上限，`maxRecords <= 0` 表示不限。
  public record ImportLimit(@DefaultValue("-1") long maxRecords) {

    /// 是否设了上限。
    public boolean hasLimit() {
      return maxRecords > 0;
    }
  }

  /// 进度指标开关。
  public record Metrics(@DefaultValue("true") boolean enabled) {}
}
```

四个自动配置类改 `public final`、类级 `@ConditionalOnProperty(prefix = "patra.batch", name = "enabled", havingValue = "true", matchIfMissing = true)`（`BatchAutoConfiguration` 带 `@EnableConfigurationProperties(BatchProperties.class)`，其余 `after = BatchAutoConfiguration.class`）；`BatchDataSourceAutoConfiguration` 的 `@ConditionalOnProperty(prefix = "patra.batch.datasource", name = "url")`；`BatchMetricsAutoConfiguration` 的 `prefix = "patra.batch.metrics"`。调用方 getter → 访问器。imports 四行新名。

Run: `./gradlew :patra-tool:patra-tool-batch:test :patra-tool:patra-tool-batch:integrationTest :patra-api:patra-catalog:patra-catalog-infra:test` → PASS。

- [ ] **Step 3: 验证并提交**

Run: `./gradlew check`
```bash
git add -A -- ':!docs'
git commit -m "feat(batch): 批处理自动配置按模板重写，配置类改 record (PAP-81)"
```

---

### Task 22: http-client 模板

**Files:**
- Rename：`httpclient/config/RestClientAutoConfiguration` → `httpclient/autoconfigure/HttpClientAutoConfiguration`、`TunnelProxyAutoConfiguration` → `HttpClientProxyAutoConfiguration`、`StreamingWebClientAutoConfiguration` → `HttpClientStreamingAutoConfiguration`、`DownloadClientAutoConfiguration` → `HttpClientDownloadAutoConfiguration`、`HttpInterfaceAutoConfiguration` → `HttpClientInterfaceAutoConfiguration`、`LongRunningClientEnabledCondition` 搬包；测试同名改
- Create: `httpclient/autoconfigure/HttpClientProperties.java`；契约测试 5 份（现有 `RestClientAutoConfigurationTest`、`TunnelProxyAutoConfigurationTest`、`HttpInterfaceAutoConfigurationTest` 改名补齐 4 条，新增 `HttpClientStreamingAutoConfigurationTest`、`HttpClientDownloadAutoConfigurationTest`）
- Delete: `RestClientProperties`、`DownloadProperties`、`HttpInterfaceProperties`、`RestClientPropertiesTest`
- Modify: 读旧 Properties 的类（`DefaultDownloadClient`、`FtpStreamingDownloader`、`HttpStreamingDownloader`、`TunnelProxyConfigurer`、`RestClientFactory`、`ProblemDetailErrorHandler`、`LoggingInterceptor`…按编译错误改）、catalog-boot / ingest-boot 的 `HttpClientConfiguration`（`@ConditionalOnProperty(prefix = "patra.http-client.http-interface", name = "enabled", …)`）、catalog / ingest `application.yml`、catalog `application-e2e-test.yml`（`patra.http-client.proxy.tunnel.enabled=false`）、`patra-infra/nacos/dev/patra-catalog.yaml`、imports 文件

**Interfaces:**
- Produces: 键 `patra.http-client.{enabled, rest.{timeout.*, logging.*, clients.<name>.*, streaming.enabled}, proxy.tunnel.*, download.*, http-interface.{enabled, connect-timeout, read-timeout, connection-pool.*, error-handling.*, groups.<name>.*}}`（`interface` 是 Java 关键字，record 组件名用 `httpInterface`，键因此是 `http-interface`；spec 5.2 表同步改）。bean 名不变：`defaultRestClient`、`longRunningRestClient`、`loggingInterceptor`、`tunnelProxyConfigurer`、`streamingWebClient`、`downloadClient`、`httpInterfaceRestClientBuilder`、`httpInterfaceLoadBalancedRestClientBuilder`、`restClientFactory`、`problemDetailErrorHandler`。

- [ ] **Step 1: 失败的契约测试（`HttpClientAutoConfigurationTest` 示例，其余四份同结构）**

```java
package dev.patra.tool.httpclient.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.httpclient.interceptor.LoggingInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class HttpClientAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(HttpClientAutoConfiguration.class));

  @Test
  void should_register_default_and_long_running_clients() {
    runner.run(
        ctx ->
            assertThat(ctx)
                .hasBean("defaultRestClient")
                .hasBean("longRunningRestClient")
                .hasSingleBean(LoggingInterceptor.class));
  }

  @Test
  void should_register_nothing_when_disabled() {
    runner.withPropertyValues("patra.http-client.enabled=false").run(ctx -> assertThat(ctx).doesNotHaveBean(RestClient.class));
  }

  @Test
  void should_back_off_when_user_defines_default_client() {
    RestClient custom = RestClient.create();
    runner
        .withBean("defaultRestClient", RestClient.class, () -> custom)
        .run(ctx -> assertThat(ctx.getBean("defaultRestClient")).isSameAs(custom));
  }

  @Test
  void should_bind_timeouts_and_clients() {
    runner
        .withPropertyValues("patra.http-client.rest.timeout.read=3s", "patra.http-client.rest.clients.long-running.enabled=false")
        .run(
            ctx -> {
              assertThat(ctx.getBean(HttpClientProperties.class).rest().timeout().read()).hasSeconds(3);
              assertThat(ctx).doesNotHaveBean("longRunningRestClient");
            });
  }
}
```

其余：`HttpClientProxyAutoConfigurationTest`（`proxy.tunnel.enabled=true` + host/port 时有 `tunnelProxyConfigurer`，默认没有）；`HttpClientStreamingAutoConfigurationTest`（默认有 `streamingWebClient`，`FilteredClassLoader(WebClient.class)` 下没有且不报错，`rest.streaming.enabled=false` 没有）；`HttpClientDownloadAutoConfigurationTest`（默认有 `downloadClient`、`ftpStreamingDownloader`，`download.ftp.enabled=false` 无 FTP）；`HttpClientInterfaceAutoConfigurationTest`（默认有 `httpInterfaceRestClientBuilder`、`restClientFactory`；`http-interface.enabled=false` 无；`http-interface.connect-timeout=2s` 绑定生效）。

- [ ] **Step 2: `HttpClientProperties` record**

```java
package dev.patra.tool.httpclient.autoconfigure;

import dev.patra.tool.httpclient.download.WriteStrategy;
import jakarta.validation.constraints.Min;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// HTTP 客户端配置，前缀 `patra.http-client`。
///
/// @param enabled 总开关
/// @param rest 外部站点用的 `RestClient`
/// @param proxy 出站代理
/// @param download 流式下载
/// @param httpInterface 服务间调用（`@HttpExchange` + `lb://`）
@Validated
@ConfigurationProperties(prefix = "patra.http-client")
public record HttpClientProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue Rest rest,
    @DefaultValue Proxy proxy,
    @DefaultValue Download download,
    @DefaultValue HttpInterface httpInterface) {

  /// 外部站点客户端。
  public record Rest(
      @DefaultValue Timeout timeout,
      @DefaultValue Logging logging,
      @DefaultValue Map<String, Client> clients,
      @DefaultValue Streaming streaming) {
    public Rest {
      clients = Map.copyOf(clients);
    }

    /// 超时。
    public record Timeout(
        @DefaultValue("10s") Duration connect, @DefaultValue("30s") Duration read, @DefaultValue("30s") Duration write) {}

    /// 请求日志拦截器。
    public record Logging(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("false") boolean logHeaders,
        @DefaultValue("false") boolean logBody,
        @DefaultValue("1024") @Min(0) int maxBodyLogLength) {}

    /// 具名客户端（`long-running`、`pubmed`…）。
    public record Client(
        @DefaultValue("true") boolean enabled,
        String baseUrl,
        @DefaultValue Map<String, String> defaultHeaders,
        Timeout timeout) {
      public Client {
        defaultHeaders = Map.copyOf(defaultHeaders);
      }
    }

    /// WebClient 流式下载。
    public record Streaming(@DefaultValue("true") boolean enabled) {}
  }

  /// 出站代理。
  public record Proxy(@DefaultValue Tunnel tunnel) {

    /// 隧道代理（青果）。
    public record Tunnel(
        @DefaultValue("false") boolean enabled, String host, @DefaultValue("0") int port, String authKey, String authPwd) {}
  }

  /// 流式下载。
  public record Download(
      @DefaultValue("true") boolean enabled,
      Path baseDir,
      Path tempDir,
      @DefaultValue("OVERWRITE") WriteStrategy writeStrategy,
      @DefaultValue("true") boolean createDirs,
      @DefaultValue("true") boolean cleanupOnFailure,
      @DefaultValue("65536") @Min(1024) int bufferSize,
      @DefaultValue Retry retry,
      @DefaultValue Ftp ftp) {
    public Download {
      Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
      baseDir = baseDir != null ? baseDir : tmp.resolve("patra-downloads");
      tempDir = tempDir != null ? tempDir : tmp;
    }

    /// 下载重试。
    public record Retry(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("3") @Min(1) int maxAttempts,
        @DefaultValue("2s") Duration initialBackoff,
        @DefaultValue("30s") Duration maxBackoff) {}

    /// FTP 下载。
    public record Ftp(
        @DefaultValue("true") boolean enabled,
        String username,
        String password,
        @DefaultValue("30s") Duration connectTimeout,
        @DefaultValue("30m") Duration dataTimeout,
        @DefaultValue("true") boolean passiveMode,
        @DefaultValue("application/xml") String defaultContentType) {}
  }

  /// 服务间调用。
  public record HttpInterface(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("5s") Duration connectTimeout,
      @DefaultValue("30s") Duration readTimeout,
      @DefaultValue ConnectionPool connectionPool,
      @DefaultValue ErrorHandling errorHandling,
      @DefaultValue Map<String, Group> groups) {
    public HttpInterface {
      groups = Map.copyOf(groups);
    }

    /// Apache HttpClient 连接池。
    public record ConnectionPool(
        @DefaultValue("100") @Min(1) int maxConnTotal,
        @DefaultValue("20") @Min(1) int maxConnPerRoute,
        @DefaultValue("5s") Duration validateAfterInactivity,
        @DefaultValue("30s") Duration evictIdleConnectionsAfter) {}

    /// 下游 ProblemDetail 的处理。
    public record ErrorHandling(
        @DefaultValue("true") boolean problemDetailEnabled,
        @DefaultValue("true") boolean tolerant,
        @DefaultValue("65536") @Min(0) int maxErrorBodySize) {}

    /// 一组下游服务（`registry`、`storage`）。
    public record Group(String baseUrl, Duration connectTimeout, Duration readTimeout) {}
  }
}
```

删掉的字段（主代码从不读，spec J11）：`rest-client.retry.*`、`interceptors.tracing.*`、`interceptors.metrics.*`、`http-interface.retry.*`。
五个自动配置类：`public final`，类级 `@ConditionalOnProperty(prefix = "patra.http-client", name = "enabled", …)`，各自子开关前缀改 `patra.http-client.rest.streaming` / `.proxy.tunnel` / `.download` / `.download.ftp` / `.http-interface`，`HttpClientAutoConfiguration` 带 `@EnableConfigurationProperties(HttpClientProperties.class)`，其余 `after = HttpClientAutoConfiguration.class`（download 另 `after` streaming）。`LongRunningClientEnabledCondition` 读 `patra.http-client.rest.clients.long-running.enabled`。imports 五行。

- [ ] **Step 3: 服务与 Nacos**

catalog `application.yml`：`patra.http-client.proxy.tunnel.{host,port}`、`patra.http-client.http-interface.{connect-timeout: 2s, read-timeout: 5s, groups.registry.{base-url, read-timeout}}`（`enabled`、`error-handling.*` 删）；ingest 同理加 `groups.storage`；`patra-infra/nacos/dev/patra-catalog.yaml`：`patra.http-client.proxy.tunnel.{enabled, auth-key, auth-pwd}`；catalog e2e yml：`patra.http-client.proxy.tunnel.enabled: false`。

Run: `grep -rn 'linqibin.starter.rest-client\|linqibin.starter.http-interface\|starter.restclient\|starter.httpinterface' . --include='*.yml' --include='*.yaml' --include='*.java' --include='*.md' | grep -v docs/`
Expected: 零命中（README 在 Task 26 重写）。

- [ ] **Step 4: 验证并提交**

Run: `./gradlew check :patra-tool:patra-tool-http-client:integrationTest :patra-tool:patra-tool-launch:test`
```bash
git add -A -- ':!docs'
git commit -m "feat(http-client): 五个自动配置按模板重写，三套配置合成 patra.http-client (PAP-81)"
```

---

### Task 23: object-storage 模板

**Files:**
- Rename：根包里的 `ObjectStorageProvider`、`AbstractObjectStorageProvider`、`MinioStorageProvider`、`S3StorageProvider`、`ProviderType` → `objectstorage/provider/`；`ObjectStorageAutoConfiguration`、`S3StorageAutoConfiguration`（→ `ObjectStorageS3AutoConfiguration`）、`ObjectStorageProperties` → `objectstorage/autoconfigure/`
- Create: `src/test/java/dev/patra/tool/objectstorage/autoconfigure/{ObjectStorageAutoConfigurationTest, ObjectStorageS3AutoConfigurationTest}.java`
- Modify: `ObjectStorageProperties` 改 record；`StorageLocationResolver`、`ObjectStorageTemplate`、两个 provider 读属性处；`patra-tool-test` 的 `MinIOContainerInitializer`（动态属性键 `patra.object-storage.active-provider`、`patra.object-storage.providers.minio.*`）；ingest `application-test-common.yml`；catalog `application.yml`（`patra.object-storage.max-file-size: 5368709120`）；`patra-infra/nacos/dev/patra-common.yaml`；catalog `application-e2e-test.yml` 排除项 FQN；ingest-infra 里 `ObjectStorageProperties` 的 import

**Interfaces:**
- Produces: 键 `patra.object-storage.{enabled, active-provider, providers.<id>.{endpoint,region,access-key,secret-key}, retry.{max-attempts,wait-duration}, max-file-size}`。

- [ ] **Step 1: 失败的契约测试**

```java
package dev.patra.tool.objectstorage.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.objectstorage.ObjectStorageOperations;
import dev.patra.tool.objectstorage.StorageLocationResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ObjectStorageAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ObjectStorageAutoConfiguration.class))
          .withPropertyValues(
              "patra.object-storage.providers.minio.endpoint=http://localhost:9000",
              "patra.object-storage.providers.minio.access-key=a",
              "patra.object-storage.providers.minio.secret-key=b");

  @Test
  void should_register_minio_operations_by_default() {
    runner.run(ctx -> assertThat(ctx).hasSingleBean(ObjectStorageOperations.class).hasSingleBean(StorageLocationResolver.class));
  }

  @Test
  void should_register_nothing_when_disabled() {
    runner.withPropertyValues("patra.object-storage.enabled=false").run(ctx -> assertThat(ctx).doesNotHaveBean(ObjectStorageOperations.class));
  }

  @Test
  void should_skip_minio_when_active_provider_is_s3() {
    runner.withPropertyValues("patra.object-storage.active-provider=s3").run(ctx -> assertThat(ctx).doesNotHaveBean(ObjectStorageOperations.class));
  }

  @Test
  void should_bind_max_file_size_default() {
    runner.run(ctx -> assertThat(ctx.getBean(ObjectStorageProperties.class).maxFileSize()).isEqualTo(104_857_600L));
  }
}
```

`ObjectStorageS3AutoConfigurationTest`：`active-provider=s3` + region 等时有 `ObjectStorageOperations`；`FilteredClassLoader(software.amazon.awssdk.services.s3.S3Client.class)` 下不报错。

- [ ] **Step 2: record 与自动配置**

```java
package dev.patra.tool.objectstorage.autoconfigure;

import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// 对象存储配置，前缀 `patra.object-storage`。
///
/// @param enabled 总开关
/// @param activeProvider 生效的 provider id（`minio` / `s3`）
/// @param providers 各 provider 的连接参数
/// @param retry 上传下载重试
/// @param maxFileSize 单文件上限（字节）
@Validated
@ConfigurationProperties(prefix = "patra.object-storage")
public record ObjectStorageProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("minio") String activeProvider,
    @DefaultValue Map<String, Provider> providers,
    @DefaultValue Retry retry,
    @DefaultValue("104857600") @Min(1) long maxFileSize) {
  public ObjectStorageProperties {
    providers = Map.copyOf(providers);
  }

  /// 一个 provider 的连接参数。
  public record Provider(String endpoint, String region, String accessKey, String secretKey) {}

  /// 重试（毫秒）。
  public record Retry(@DefaultValue("3") @Min(1) int maxAttempts, @DefaultValue("1000") @Min(0) long waitDuration) {}
}
```

两个自动配置 `public final`、类级总开关、`@ConditionalOnProperty(prefix = "patra.object-storage", name = "active-provider", havingValue = "minio", matchIfMissing = true)` / `"s3"`；imports 两行新名。

- [ ] **Step 3: 验证并提交**

Run: `./gradlew check :patra-api:patra-ingest:patra-ingest-infra:integrationTest :patra-tool:patra-tool-launch:test`
```bash
git add -A -- ':!docs'
git commit -m "feat(object-storage): 对象存储自动配置按模板重写，Nacos 键改 patra.object-storage (PAP-81)"
```

---

### Task 24: observability 模板：EPP 默认值、logback、三个自动配置

**Files:**
- Create: `patra-tool/patra-tool-observability/src/main/java/dev/patra/tool/observability/autoconfigure/{ObservabilityDefaultsPostProcessor, ObservabilityProperties}.java`、`src/main/resources/META-INF/spring.factories`、`src/test/java/dev/patra/tool/observability/autoconfigure/{ObservabilityDefaultsPostProcessorTest, ObservabilityAutoConfigurationTest, ObservabilityMicrometerAutoConfigurationTest, ObservabilityErrorAutoConfigurationTest}.java`
- Rename：`MicrometerAutoConfiguration` → `ObservabilityMicrometerAutoConfiguration`、`ObservationInterceptorsAutoConfiguration` → `ObservabilityErrorAutoConfiguration`；`config/ObservabilityProperties` → `autoconfigure/`（record）；`OtelMeterRegistryBridgeTest` 搬包
- Modify: `src/main/resources/dev/patra/tool/observability/logback-patra.xml`（Task 5 搬来的）、三个自动配置、imports、`additional-spring-configuration-metadata.json`（删：键全由 record 生成）、`build.gradle.kts`、6 个 boot 的 `application.yml`（删 `logging` / `management` 段与 Task 5 的临时 `logging.config`）、`patra-tool-batch` 的 `afterName`

**Interfaces:**
- Produces: EPP 出的 6 个键（spec 5.3）；键 `patra.observability.{enabled, application-name, environment, region, cluster, metrics.{enabled, common-tags.*}}`；`dev.patra.tool.observability.autoconfigure.ObservabilityMicrometerAutoConfiguration`（batch `afterName` 用）。

- [ ] **Step 1: EPP 的失败测试**

```java
package dev.patra.tool.observability.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

class ObservabilityDefaultsPostProcessorTest {

  private final ObservabilityDefaultsPostProcessor processor = new ObservabilityDefaultsPostProcessor();

  @Test
  void should_add_defaults_at_lowest_priority() {
    StandardEnvironment env = new StandardEnvironment();
    processor.postProcessEnvironment(env, new SpringApplication());
    assertThat(env.getProperty("logging.config")).isEqualTo("classpath:dev/patra/tool/observability/logback-patra.xml");
    assertThat(env.getProperty("logging.level.dev.patra")).isEqualTo("DEBUG");
    assertThat(env.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health,info,metrics");
    assertThat(env.getProperty("management.endpoint.health.show-details")).isEqualTo("when-authorized");
    assertThat(env.getPropertySources().get(DefaultPropertiesPropertySource.NAME)).isNotNull();
  }

  @Test
  void should_let_any_other_source_win() {
    MockEnvironment env = new MockEnvironment().withProperty("logging.config", "classpath:mine.xml");
    processor.postProcessEnvironment(env, new SpringApplication());
    assertThat(env.getProperty("logging.config")).isEqualTo("classpath:mine.xml");
  }

  @Test
  void should_not_override_existing_default_properties() {
    StandardEnvironment env = new StandardEnvironment();
    DefaultPropertiesPropertySource.addOrMerge(Map.of("management.endpoints.web.exposure.include", "health"), env.getPropertySources());
    processor.postProcessEnvironment(env, new SpringApplication());
    assertThat(env.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
    assertThat(env.getProperty("logging.level.dev.patra")).isEqualTo("DEBUG");
  }

  @Test
  void should_resolve_placeholders_against_environment() {
    StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources().addFirst(new MapPropertySource("test", Map.of("spring.application.name", "patra-x", "PATRA_LOG_DIR", "/var/log/patra")));
    processor.postProcessEnvironment(env, new SpringApplication());
    assertThat(env.getProperty("management.metrics.tags.application")).isEqualTo("patra-x");
    assertThat(env.getProperty("logging.file.path")).isEqualTo("/var/log/patra");
  }
}
```

Run: `./gradlew :patra-tool:patra-tool-observability:test --tests '*DefaultsPostProcessorTest*'` → 编译失败。

- [ ] **Step 2: 实现 EPP**

```java
package dev.patra.tool.observability.autoconfigure;

import dev.patra.tool.core.PatraOrders;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

/// 日志与 actuator 的默认配置（决策 D）：以最低优先级并入 `defaultProperties`，服务 yml、Nacos、环境变量都能覆盖。
///
/// 每个键只能有一个归属模块出默认值，本模块出的键见 `DEFAULTS`；`logging.*` 必须在这里给，
/// `@PropertySource` 加入得太晚，日志系统初始化时读不到。
public final class ObservabilityDefaultsPostProcessor implements EnvironmentPostProcessor, Ordered {

  /// 本模块出的默认键。
  static final Map<String, Object> DEFAULTS =
      Map.of(
          "logging.file.path", "${PATRA_LOG_DIR:logs}",
          "logging.level.dev.patra", "DEBUG",
          "logging.config", "classpath:dev/patra/tool/observability/logback-patra.xml",
          "management.endpoints.web.exposure.include", "health,info,metrics",
          "management.endpoint.health.show-details", "when-authorized",
          "management.metrics.tags.application", "${spring.application.name}");

  /// 并入默认值；`defaultProperties` 里已有的键不动（`addOrMerge` 会用新值覆盖旧值，所以先剔除）。
  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    Map<String, Object> defaults = new LinkedHashMap<>(DEFAULTS);
    PropertySource<?> existing = environment.getPropertySources().get(DefaultPropertiesPropertySource.NAME);
    if (existing != null) {
      defaults.keySet().removeIf(existing::containsProperty);
    }
    DefaultPropertiesPropertySource.addOrMerge(defaults, environment.getPropertySources());
  }

  /// 最后运行：别的 EPP（含 launch 的 Nacos 默认值）先放进去的键优先。
  @Override
  public int getOrder() {
    return PatraOrders.DEFAULTS_POST_PROCESSOR;
  }
}
```

`src/main/resources/META-INF/spring.factories`：

```
org.springframework.boot.EnvironmentPostProcessor=\
dev.patra.tool.observability.autoconfigure.ObservabilityDefaultsPostProcessor
```

`build.gradle.kts` 加 `api(project(":patra-tool:patra-tool-core"))`（`PatraOrders`；core 是纯 Java，R2 允许）。

Run: 同上 → PASS（`MockEnvironment` 来自 spring-test，`spring-boot-starter-test` 已带）。

- [ ] **Step 3: logback-patra.xml**

在 Task 5 搬来的文件上：`conversionRule` 的 `class` 改 `dev.patra.tool.observability.logging.TraceIdConverter` / `SpanIdConverter`（Task 3 已改，核对）；删整个 `<logger name="dev.linqibin" …>` 块（级别由 `logging.level.dev.patra` 属性给，root 已挂两个 appender）；文件头加一行注释：`<!-- 由 ObservabilityDefaultsPostProcessor 以 logging.config 默认属性指向；服务 yml 配 logging.config 即可整体换掉 -->`。

- [ ] **Step 4: Properties record 与三个自动配置**

```java
package dev.patra.tool.observability.autoconfigure;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// 可观测性配置，前缀 `patra.observability`。
///
/// @param enabled 总开关
/// @param applicationName 指标公共标签 application（空则用 `spring.application.name`）
/// @param environment 环境标签
/// @param region 区域标签
/// @param cluster 集群标签
/// @param metrics 指标
@Validated
@ConfigurationProperties(prefix = "patra.observability")
public record ObservabilityProperties(
    @DefaultValue("true") boolean enabled,
    String applicationName,
    String environment,
    String region,
    @DefaultValue("default") String cluster,
    @DefaultValue Metrics metrics) {

  /// 指标开关与公共标签。
  public record Metrics(@DefaultValue("true") boolean enabled, @DefaultValue Map<String, String> commonTags) {
    public Metrics {
      commonTags = Map.copyOf(commonTags);
    }
  }
}
```

`ObservabilityAutoConfiguration`（`public final`，`@ConditionalOnProperty(prefix = "patra.observability", name = "enabled", …)`，内容不变）；`ObservabilityMicrometerAutoConfiguration`（原 `MicrometerAutoConfiguration`，`prefix = "patra.observability.metrics"`，两个 `MeterFilter` 的 `@Order` 已是 `PatraOrders` 常量）；`ObservabilityErrorAutoConfiguration`（原 `ObservationInterceptorsAutoConfiguration`，类级 `@ConditionalOnClass(name = "dev.patra.tool.web.error.pipeline.ResolutionInterceptor")`，Task 14 已挪）。getter → 访问器。imports 三行新名。`patra-tool-batch` 的 `afterName` 改新名。

契约测试三份：`ObservabilityAutoConfigurationTest`（默认有 `ObservationRegistry`；`enabled=false` 无；用户 bean 覆盖）；`ObservabilityMicrometerAutoConfigurationTest`（有 `MeterRegistry` 时两个 `MeterFilter` 存在；`metrics.enabled=false` 无）；`ObservabilityErrorAutoConfigurationTest`（有 web 时有 `ObservationResolutionInterceptor`；`FilteredClassLoader(ResolutionInterceptor.class)` 下无且不报错）。

- [ ] **Step 5: 服务 yml 删默认段**

6 个 boot 的 `application.yml`：删 `logging.file.path`（gateway 保留 `logging.level.org.springframework.cloud.*` 两行）、整个 `management` 段、Task 5 加的临时 `logging.config`。object-storage 原来暴露 `health,info,prometheus`，统一为默认（spec J9）。

- [ ] **Step 6: 对照启动（D4）**

改前（`git stash` 不行——共享 stash；改用 `git worktree` 或直接按 Task 23 的提交 `git checkout <sha> -- patra-api/*/…/application.yml` 临时对照）各起 registry 一次，记录：日志文件 `logs/patra-registry.log` 存在；控制台 `dev.patra` 的 DEBUG 行可见；`curl -s localhost:6000/actuator | jq '._links | keys'` = `health, health-path, info, metrics, metrics-requiredMetricName, self`；`curl -s localhost:6000/actuator/metrics/jvm.memory.used | jq '.availableTags[] | select(.tag=="application")'` 有 `patra-registry`；`curl -si localhost:6000/nope | grep -i '"code"'` 的前缀 `REG-`。改后再起一次，六项一致。再在 registry yml 临时加 `logging.config: classpath:logback-test.xml` 起一次，日志格式变化即覆盖生效，然后删掉。

- [ ] **Step 7: 验证并提交**

Run: `./gradlew check :patra-tool:patra-tool-launch:test`
```bash
git add -A -- ':!docs'
git commit -m "feat(observability): 日志与 actuator 默认值随模块走，logback 移入包路径 (PAP-81)"
```

---

### Task 25: security / openapi / launch / test 模板

**Files:**
- security：`config/` → `autoconfigure/`；`SecurityCoreAutoConfiguration` → `SecurityAutoConfiguration`；`PatraSecurityProperties` → `SecurityProperties`（加 `@DefaultValue("true") boolean enabled` 组件，放第一位，保留现有紧凑构造器校验）；四个类 `public final` + 类级 `@ConditionalOnProperty(prefix = "patra.security", name = "enabled", …)`；现有 5 个契约测试改名搬包并补 `enabled=false` 用例；`StatelessSecurityDefaults` 留 `autoconfigure`（它是配置辅助）；testFixtures 的 `TestIdentityAssertionEnvironmentPostProcessor` 引用 `SecurityProperties.IdentityAssertion.PUBLIC_KEYS_PROPERTY` 同步
- openapi：`config/OpenApiProperties` → `autoconfigure/OpenApiProperties`（record：`enabled`、`title`、`version` 默认 `0.1.0`、`description`），`OpenApiAutoConfiguration` `public final`，`@ConditionalOnProperty` 补 `havingValue = "true"`；`OpenApiAutoConfigurationTest` 补齐 4 条
- launch：`autoconfigure/LaunchProperties`（`enabled`）；`LaunchAutoConfiguration` `public final` + 总开关 + `@EnableConfigurationProperties`；新建 `LaunchAutoConfigurationTest`（默认有 `Clock` 且 `clock.getZone()` 是 UTC；`patra.launch.enabled=false` 无；用户 `Clock` bean 覆盖）；两个 EPP 不动
- test：`TestMeterRegistryAutoConfiguration` 的 `@Bean` 并进 `TestAutoConfiguration`（`public final`，`@ConditionalOnClass(MeterRegistry.class)` 放嵌套静态配置里），删前者；`autoconfigure/TestProperties`（`enabled`）；新建 `TestAutoConfigurationTest`

- [ ] **Step 1: 四个模块各先写失败的契约测试（用 Task 18 的模板，断言各自 bean）**，跑一次确认编译失败或断言失败。
- [ ] **Step 2: 按上表实现；imports 文件各自改新名；`generateIdentityAssertionKey` 的 `mainClass` 核对。**
- [ ] **Step 3: 验证**

Run: `./gradlew :patra-tool:patra-tool-security:check :patra-tool:patra-tool-security:integrationTest :patra-tool:patra-tool-openapi:check :patra-tool:patra-tool-launch:check :patra-tool:patra-tool-test:check :patra-api:patra-gateway-boot:test :patra-api:patra-identity:patra-identity-boot:integrationTest`
Expected: BUILD SUCCESSFUL（gateway / identity 的 testFixtures 公钥注入照旧）。

- [ ] **Step 4: 提交** `feat(tool): security / openapi / launch / test 四个模块按模板统一 (PAP-81)`

---

### Task 26: ingest 前缀、12 份 README、`patra-common` 与 `patra-api` README

**Files:**
- ingest：`provenance/{autoconfig,boot}/` → `provenance/autoconfigure/`，`ProvenanceProperties` 前缀 `patra.ingest.provenance`；`expr/compiler/boot/` → `expr/autoconfigure/`，`CompilerProperties` / `ExprModeProperties` 前缀 `patra.ingest.expr.compiler` / `patra.ingest.expr.mode`；`ProvenanceAutoConfiguration` 的 `@ConditionalOnProperty` 前缀同步；ingest `application.yml` 的 `patra.starter.expr.*` → `patra.ingest.expr.*`（ingest 两个模块不受 R4 约束，配置类可以先不改 record）
- README：`patra-tool/patra-tool-{core,cqrs,web,jpa,batch,http-client,object-storage,observability,security,openapi,launch,test}/README.md` 按 spec 5.8 四节重写（删旧文；配置键表从各 record 抄，默认值一致；"出哪些默认值"只有 observability、launch 非空）；`patra-api/patra-common/README.md` 一页；`patra-api/README.md` 第 73–77 行 starter 表改成指向 `patra-tool/` 的一句话 + 链接；`patra-api/patra-ingest/patra-ingest-{provenance,expr}/README.md` 从旧 starter README 精简（前缀改新）

- [ ] **Step 1: ingest 前缀与包改名，`grep -rn 'patra.starter' . --include='*.java' --include='*.yml' --include='*.yaml' | grep -v docs/` 零命中。**
- [ ] **Step 2: 写 README。每份结构固定：**

```markdown
# patra-tool-<x>

## 能力
（两三句）

## 配置（前缀 `patra.<x>`）
| 键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `enabled` | boolean | `true` | 总开关 |
| … | | | |

## 怎么覆盖
```yaml
patra:
  <x>:
    …
```

## 出哪些默认值
无。（observability / launch 列 spec 5.3 的键）
```

- [ ] **Step 3: 验证并提交**

Run: `./gradlew check :patra-api:patra-ingest:patra-ingest-infra:integrationTest :patra-tool:patra-tool-launch:test`
```bash
git add -A -- ':!docs/patra/specs' ':!docs/patra/plans'
git commit -m "docs(tool): 12 个工具模块与 patra-common 的 README 按模板重写，ingest 配置前缀归位 (PAP-81)"
```

---

## B' · PAP-80 收尾

### Task 27: R4 挂 `check`，D3 五条实测记入 spec

**Files:**
- Modify: `build-logic/src/main/kotlin/patra.tool-module.gradle.kts`（`tasks.named("check") { dependsOn("checkAutoConfiguration") }`）、`docs/patra/specs/2026-10-15-tool-reorg-design.md` 第 4.9 节

- [ ] **Step 1: 先跑一遍看余量**

Run: `./gradlew checkAutoConfiguration --continue`
Expected: 全绿。有命中的逐条修（通常是漏写 `public final`、测试文件名不同名、`@Configuration` 放错包）。与 Task 16 Step 4 的数字对照写进 spec 4.9。

- [ ] **Step 2: 挂 `check`，再跑 `./gradlew check`。**

- [ ] **Step 3: D3 五条实测，每条：改 → `./gradlew check` 失败 → 把首段失败信息原文贴进 spec 4.9 → `git checkout --` 撤销 → `./gradlew check` 通过**

| 实测 | 改动 |
|---|---|
| R1 | `patra-tool/patra-tool-web/build.gradle.kts` 加 `implementation(project(":patra-api:patra-catalog:patra-catalog-api"))` |
| R2 | `patra-tool/patra-tool-jpa/build.gradle.kts` 把 `compileOnly(project(":patra-tool:patra-tool-web"))` 改 `api(...)` |
| R3 | `patra-api/patra-catalog/patra-catalog-domain/build.gradle.kts` 加 `implementation("org.springframework:spring-context")` |
| R4 | `patra-tool-cqrs` 的 `LoggingCommandInterceptor` 加 `@Component` |
| 删契约测试 | `git rm patra-tool/patra-tool-web/src/test/java/dev/patra/tool/web/autoconfigure/WebJsonAutoConfigurationTest.java` |

- [ ] **Step 4: 提交** `build(rules): R4 自动配置写法挂 check，记录 D3 实测 (PAP-80)`（spec 文件一起提交）

---

### Task 28: R5 探针、挂 `check`、死依赖与重复声明清理

**Files:**
- Modify: `gradle/libs.versions.toml`（`dependency-analysis = "3.19.2"`、`[plugins] dependency-analysis = { id = "com.autonomousapps.dependency-analysis", version.ref = "dependency-analysis" }`）、根 `build.gradle.kts`、受 advice 影响的 `build.gradle.kts`、`docs/patra/specs/2026-10-15-tool-reorg-design.md` 第 6.4 节

- [ ] **Step 1: 接入插件，探针模式**

根 `build.gradle.kts` 的 `plugins {}` 加 `alias(libs.plugins.dependency.analysis)`，并加：

```kotlin
dependencyAnalysis {
    issues {
        all {
            onAny { severity("warn") }
        }
    }
}
```

Run: `./gradlew buildHealth`
Expected: 输出 advice 列表；按类型计数记进 spec 6.4（unused / used-transitive / ABI / runtimeOnly / annotation-processor 各几条，涉及哪些模块）。

- [ ] **Step 2: 处理 advice**

- 死依赖与重复声明（spec 6.4 表 A1–A14、B、C1 与 28 处重复）：按建议删。
- boot 对 starter 的 `implementation` 被判只需 `runtimeOnly`：按建议改 `runtimeOnly`（boot 主代码真 import 了的保留 `implementation`）。
- `patra-tool-test`、`patra-tool-launch`（EPP 靠 `spring.factories`）、`patra-ingest-provenance` 对 http-client 的 `runtimeOnly` 被判 unused：

```kotlin
dependencyAnalysis {
    issues {
        all {
            onAny { severity("fail") }
            onUnusedDependencies {
                exclude(":patra-tool:patra-tool-test", ":patra-tool:patra-tool-launch")
            }
            onUsedTransitiveDependencies { severity("ignore") }
        }
    }
}
```

规则：一条 exclude 只对应一类"靠自动配置 / 资源生效"的依赖；超过 10 条还压不下，或某条建议改了会让运行出错，就按 spec 4.6 退回自写 `checkDeclaredDependencies`（算法在 spec），本任务改为实现它并挂 `check`。

- [ ] **Step 3: 挂 `check`**

根 `build.gradle.kts`：`tasks.named("check") { dependsOn("buildHealth") }`（根项目没有 `check` 时先 `plugins { base }`——已有）。

Run: `./gradlew check && ./gradlew dumpModuleGraph`
Expected: BUILD SUCCESSFUL；`module-graph.json` 有变化并提交。

- [ ] **Step 4: 提交** `build(rules): R5 接入 dependency-analysis 并挂 check，清理死依赖与重复声明 (PAP-80)`；用 Linear MCP 把 PAP-80、PAP-81 置 In Review。

---

## D · PAP-82 扫描收窄与收尾

### Task 29: 启动类收窄、`SimpleCommandBus` 快速失败、`--debug` 核对

**Files:**
- Modify: 6 个 `Patra*Application.java`（`@SpringBootApplication(scanBasePackages = "dev.patra")` → `@SpringBootApplication`；catalog 的 `exclude` 已在 Task 11 删）、`patra-tool/patra-tool-cqrs/src/main/java/dev/patra/tool/cqrs/SimpleCommandBus.java`、`src/test/java/dev/patra/tool/cqrs/SimpleCommandBusTest.java`

- [ ] **Step 1: 失败测试（追加到 `SimpleCommandBusTest`）**

```java
  @Test
  void should_fail_when_two_handlers_claim_same_command() {
    CommandHandler<PingCommand, String> first = command -> "a";
    CommandHandler<PingCommand, String> second = command -> "b";
    assertThatThrownBy(() -> new SimpleCommandBus(List.of(first, second), List.of(), Runnable::run))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(PingCommand.class.getName())
        .hasMessageContaining("两个 Handler");
  }
```

（`PingCommand` 用测试类里已有的命令类型；lambda 实现 `CommandHandler` 时泛型参数要能被 `extractCommandType` 读到，若现有测试用的是具名内部类，就照它的写法写两个具名类。）

Run: `./gradlew :patra-tool:patra-tool-cqrs:test --tests '*SimpleCommandBusTest*'` → 新用例 FAIL（现在只打 warn）。

- [ ] **Step 2: 实现**

`registerHandlers` 里：

```java
        CommandHandler<?, ?> existing = handlers.putIfAbsent(commandType, handler);
        if (existing != null) {
          throw new IllegalStateException(
              "命令 " + commandType.getName() + " 注册了两个 Handler：" + existing.getClass().getName() + " 与 " + handler.getClass().getName());
        }
```

删 `log.warn` 分支。Run: 同上 → PASS。

- [ ] **Step 3: 启动类改裸注解**

6 个文件删 `scanBasePackages`，`grep -rn scanBasePackages patra-api` 零命中。

- [ ] **Step 4: `--debug` 核对（D5）**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-boot:bootRun --args='--debug' 2>&1 | tee /tmp/catalog-debug.log`（后台起，起来后 Ctrl-C）
在日志 `CONDITIONS EVALUATION REPORT` 的 `Positive matches` 里确认：`CqrsAutoConfiguration.LoggingInterceptorConfiguration`、`CqrsAutoConfiguration.TracingInterceptorConfiguration`、`CqrsAutoConfiguration.MetricsInterceptorConfiguration`、`WebErrorAutoConfiguration#globalRestExceptionHandler`、`JpaAutoConfiguration.JpaErrorMappingConfiguration`、`JpaAuditingConfiguration`、`PatraHibernatePropertiesCustomizer`；`curl -s localhost:6300/actuator/beans | jq '.contexts[].beans | to_entries[] | select(.key | test("globalRestExceptionHandler|jpaErrorMappingContributor|loggingCommandInterceptor")) | {k: .key, r: .value.resource}'` 的 `resource` 都是自动配置类。ingest 同样起一次，确认 `processorRegistry` bean 存在（它靠 ingest 自己的扫描）。identity 起一次确认 `securityExceptionRethrowAdvice` 来自 `SecurityServletAutoConfiguration`。6 个服务都起一遍。

- [ ] **Step 5: 验证并提交**

Run: `./gradlew check`
```bash
git add patra-api patra-tool
git commit -m "feat(boot): 启动类只扫自己的包，命令总线重复 handler 启动即失败 (PAP-82)"
```

---

### Task 30: CI/CD、基建文档、根文档、IDE 配置扫尾

**Files:**
- Modify: `.github/workflows/ci.yml`、`.github/workflows/cd.yml`（Task 4、11 改过，再核对）、`patra-infra/CLAUDE.md`、`patra-infra/docker/service.Dockerfile`、`AGENTS.md` 第 87 行、根 `README.md`（布局表、Gradle 坐标表、边界约束段、新增模块规则示例）、`patra-api/README.md`、`.run/*.xml`（Task 3 改过，核对）、`patra-portal/src/types/portal.ts`（Task 10 改过，核对）、`spotbugs-exclude.xml`（核对无 `linqibin`）

- [ ] **Step 1: 全仓最后一次 grep**

Run: `grep -rn 'linqibin' . --include='*.md' --include='*.yml' --include='*.yaml' --include='*.kts' --include='*.xml' --include='*.json' --include='*.sh' --include='*.ts' --include='*.tsx' | grep -v '^./docs/' | grep -v 'linqibin0826\|linqibins-mac-mini\|@author linqibin\|TARGET_AUTHOR\|由 linqibin 一人\|plugin.json'`
Expected: 零命中。剩下的逐个改。

- [ ] **Step 2: 根 README**

布局表：`linqibin-commons/` 与 `patra-starters/` 两行换成一行 `patra-tool/ | 12 个按技术能力划分的工具模块（core 纯 Java，其余 Spring Boot starter）`；Gradle 坐标表换成一行 `全部 | dev.patra`；"边界约束"段改为：`patra-tool/*` 由 `patra.tool-module` 施加 R1（不依赖 `patra-api`）、R2（工具间只指向 core）、R4（自动配置写法），`*-domain` 由 `patra.hexagonal-domain` 施加 R3 白名单，根项目 `buildHealth` 做 R5，全部挂 `check`；"新增模块规则"示例路径改 `:patra-tool:patra-tool-web → patra-tool/patra-tool-web/`。

- [ ] **Step 3: 模拟一次受影响单元路由**

Run: `bash patra-infra/cd/detect-changes.sh`（按脚本 usage 传一个只改 `patra-tool/patra-tool-jpa/README.md` 的 diff 范围，或看 CI 上本分支最近一次 `detect-changes` job 输出）
Expected: `backend_units` 含 `foundation` 与用 jpa 的 5 个服务。

- [ ] **Step 4: 提交** `docs(repo): CI/CD 路径、基建说明与根 README 改到 patra-tool 结构 (PAP-82)`

---

### Task 31: `.claude/` 规则与 skill 更新（主检出）

**Files:**
- Modify: `.claude/rules/project-info.md`、`.claude/rules/tech/commandbus.md`、`.claude/rules/tech/error-handling.md`、`.claude/rules/tech/observability.md`、`.claude/rules/layers/infrastructure.md`、`.claude/rules/testing/integration-test.md`、`.claude/skills/patra-backend/skills/patra-hexagonal/SKILL.md`、`…/resources/patra-starters.md`、`…/resources/configuration.md`、`.claude/skills/patra-backend/skills/patra-troubleshooter/SKILL.md`、`…/references/log-config.md`

本任务在主检出会话执行（worktree 会话的 PreToolUse 钩子拒绝改 `.claude/`）。

- [ ] **Step 1: 逐文件改**（spec 6.5 表）：

- `project-info.md`：通用库段改为 "**工具模块**（`patra-tool/`，12 个，R1–R5 由 `./gradlew check` 守）：core（纯 Java：DDD 基类、错误类型、当前用户、`PatraOrders`）、cqrs、web（含错误解析引擎）、jpa、batch、http-client、object-storage、observability（出日志与 actuator 默认值）、security、openapi、launch（`PatraApplication`、Nacos 默认值、`Clock`）、test"；删"有仓库外下游，改动前见根 CLAUDE.md「下游消费者」"；Patra 共享模块段改 `patra-common`（一个模块）；专属 starter 段改 ingest 两个子模块；构建逻辑段插件名 `patra.*`，加 `patra.tool-module`。
- `commandbus.md`：组件表位置改 `patra-tool-cqrs`。
- `error-handling.md`：引擎、`ErrorMappingContributor` SPI、`DefaultErrorResolutionEngine` 位置改 `patra-tool-web`；错误码前缀键 `patra.web.error.context-prefix`。
- `observability.md`、`layers/infrastructure.md`：模块名。
- `integration-test.md` 第 38–52 行：删 `@ImportAutoConfiguration` 示例与"必须显式导入"说明，改为"web 模块已把错误处理与 Long→String 登记进 `@WebMvcTest` 切片，切片根只需 `@SpringBootConfiguration` + `@EnableAutoConfiguration`"。
- `patra-hexagonal/SKILL.md` 第 97–103 行与 `resources/patra-starters.md`：starter 选择树改 `patra-tool-*`，删 redisson 行，加 `需要命令总线 → patra-tool-cqrs`；`resources/configuration.md` 的 `starter-nacos` → `patra-tool-launch`。
- `patra-troubleshooter/SKILL.md`、`references/log-config.md`：`dev.linqibin` → `dev.patra`；logback 路径 `patra-tool/patra-tool-observability/src/main/resources/dev/patra/tool/observability/logback-patra.xml`；日志目录示例路径里的 `/Users/linqibin/Desktop/Patra/` 改 `~/Projects/Products/patra/`。

- [ ] **Step 2: 提交** `docs(claude): 规则与 skill 改到 patra-tool 结构 (PAP-82)`

---

### Task 32: 收尾：release spec 与 Linear 修正、D1–D7 本地核对、PR 描述草稿

**Files:**
- Modify: `docs/patra/release-specs/v0.10-tool-reorg.md`（spec 第 10 节的修正）、Linear PAP-79 / 80 / 82 描述（MCP）
- Create: `docs/patra/plans/2026-10-15-tool-reorg-pr-body.md`（PR 描述草稿，不开 PR）

- [ ] **Step 1: release spec 修正**：决策 C 的 R3 白名单补 Jackson 3 core/databind、R4 禁用列表去掉两个 advice 注解、D2 / D3 加"纯 Java 的 core 除外"；决策 G 删"catalog 保留 exclude"；第 2 节 PAP-79 行"common-security 进 core"、"错误解析引擎进 web"。
- [ ] **Step 2: Linear**（`save_issue`）：PAP-79 模块清单改"common-security 进 core"；PAP-80 R4 禁用列表；PAP-82 零引用清单去 `ChildEntityChange`、补 `SoftDeletableChildJpaEntity` 与 RocketMQ 三件套，死依赖改"按工程 spec 6.4"，catalog exclude 改"删"。
- [ ] **Step 3: D1–D7 本地核对**（spec 第 8 节）：

Run:
```bash
./gradlew check integrationTest :patra-api:patra-catalog:patra-catalog-boot:e2eTest
find patra-tool -name '*.imports' | wc -l        # 11
find patra-tool -type d -name autoconfigure | wc -l   # 11
grep -rn scanBasePackages patra-api | wc -l       # 0
ls patra-api/*/*-boot/src/main/resources/*-error-config.yaml 2>/dev/null | wc -l   # 0
```
D7（mini）在合并后由 CD 完成，验收时再跑 portal e2e。

- [ ] **Step 4: PR 描述草稿**写到上面的文件：标题 `feat(tool): 工具层重组为 patra-tool 12 个能力模块，规则挂 check (PAP-79 ~ PAP-82)`；正文逐行 `Closes PAP-79` … `Closes PAP-82`；附 release spec、工程 spec、plan 三个链接；列 D1–D6 的本地结果与 D7 待 mini 验证；末尾 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`。

- [ ] **Step 5: 提交** `docs(release): 按工程 spec 修正 v0.10 决策与 Done 判定 (PAP-82)`，然后停下，把 D1–D6 结果与 PR 草稿交给用户验收；开 draft PR、转 ready、推送都等用户原话。
