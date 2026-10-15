# PR 描述草稿（v0.10 工具层重组）

> 标题：`feat(tool): 工具层重组为 patra-tool 12 个能力模块，规则挂 check (PAP-79 ~ PAP-82)`
> 分支：`feat/v0.10-tool-reorg-api` → `main`，开 draft，完工转 ready。

---

Closes PAP-79
Closes PAP-80
Closes PAP-81
Closes PAP-82

## 做了什么

- **命名与模块就位（PAP-79）**：`linqibin-commons/` + `patra-starters/` 重组为 `patra-tool/` 12 个按技术能力划分的模块（core 纯 Java，其余 Spring Boot starter），包根 `dev.patra`、Gradle group `dev.patra`、约定插件 `patra.*`；`patra-common` 合成一个模块；provenance / expr 收回 `patra-ingest/`；删 redisson、Async / CircuitBreaker 自动配置。
- **依赖规则由构建守（PAP-80）**：R1 工具不依赖业务、R2 工具间只指向 core（security → web 白名单）、R3 domain 编译类路径白名单、R4 自动配置写法、R5 声明的 project 依赖必须被 import，全部由 `patra.tool-module` / `patra.hexagonal-*` 施加并挂 `check`；build-logic 首批 TestKit + 单元测试。R5 的 DAGP 探针噪音压不下（unused 575 / undeclared 2207），按 spec 4.6 退回自写 `checkDeclaredDependencies`。
- **starter 统一模板与默认配置（PAP-81）**：每模块 `autoconfigure` 包 + `public final` 自动配置类 + record 配置类 + `patra.<能力>` 前缀 + 总开关 + 同名契约测试；observability 的 `EnvironmentPostProcessor` 出日志 / actuator 默认值，logback 搬到包路径；删三份 `*-error-config.yaml`；`PatraOrders` 统一顺序常量；12 份模块 README。
- **扫描收窄与收尾（PAP-82）**：6 个启动类改裸 `@SpringBootApplication`，工具 bean 一律由自动配置注册；`SimpleCommandBus` 重复 handler 启动即失败；清零引用类型与死依赖；CI/CD 路径、`module-graph.json`、根 README、`.claude` 规则与 skill 同步。
- **顺手修的真 bug**：`CqrsAutoConfiguration` 只排在 `MetricsAutoConfiguration` 之后，`@ConditionalOnBean(MeterRegistry)` 评估时 bean 还没建，Metrics 拦截器在 6 个服务里从未装上；`afterName` 补 `CompositeMeterRegistryAutoConfiguration`，契约测试先红后绿。

## 文档

- release spec：`docs/patra/release-specs/v0.10-tool-reorg.md`
- 工程 spec：`docs/patra/specs/2026-10-15-tool-reorg-design.md`（4.9 节 D3 实测、6.4 节 R5 探针结果）
- plan：`docs/patra/plans/2026-10-15-tool-reorg.md`

## Done 判定本地结果

| 判定 | 结果 |
|---|---|
| D1 Issue 全 Done | 合并后由 `Closes` 关闭 |
| D2 结构就位 | `patra-tool/` 12 个模块；`AutoConfiguration.imports` 11 份、`autoconfigure` 包 11 个（core 除外）；`patra-common` 单模块；`git grep linqibin` 在 `*.java` / `*.kts` / `*.yml` / `*.yaml` 零命中（`@author`、远端地址、`docs/` 除外） |
| D3 规则由构建守住 | R1–R4 各加一条违规 `check` 即失败，删契约测试文件 `check` 失败，失败原文见工程 spec 4.9；R5 全仓扫出 8 条死依赖后清零 |
| D4 默认配置生效 | 6 个 `application.yml` 无 `logging` / `management` 段，无 `*-error-config.yaml`；registry 本机对照日志路径、actuator 端点、`application` 标签、错误码前缀与改前一致，`logging.config` 一键覆盖生效 |
| D5 扫描收窄 | `scanBasePackages` 零命中；6 个服务 `--debug` 启动，`globalRestExceptionHandler` / `jpaErrorMappingContributor` / `loggingCommandInterceptor` / `metricsCommandInterceptor` / `securityExceptionRethrowAdvice` 全部来自 `autoconfigure` 类，ingest `processorRegistry` 来自自身扫描 |
| D6 检查和测试全绿 | 本地 `./gradlew check integrationTest :patra-api:patra-catalog:patra-catalog-boot:e2eTest`：见下方「D6 实跑」；CI 待 PR 触发 |
| D7 mini 上跑通 | 待合并后 CD 推送 Nacos 改名配置并部署，再跑 portal e2e |

D6 实跑：2026-10-15 本地 `./gradlew check integrationTest :patra-api:patra-catalog:patra-catalog-boot:e2eTest` BUILD SUCCESSFUL（exit 0，无 FAILED 用例；build-logic 29 个测试另跑绿）。

## 评审提示

- 改了多份 `*.gradle.kts`，本地 IDEA 需「加载 Gradle 更改」。
- Nacos 的 `patra-infra/nacos/dev/*.yaml` 前缀已改为 `patra.<能力>`，合并后 CD 推送；在此之前本机起服务要用 `--spring.config.import` 追加 `optional:file:` 导入这些文件。

🤖 Generated with [Claude Code](https://claude.com/claude-code)
