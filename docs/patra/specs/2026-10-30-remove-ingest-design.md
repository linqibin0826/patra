# 删除 ingest 及只为它存在的部分

- 日期：2026-10-30
- 分支：`refactor/remove-ingest`（基线 `b554d9961`）
- Issue：PAP-136（Backlog，不挂版本，单独一个 PR）
- 范围：`patra-ingest` 8 个模块、`patra-object-storage` 服务、registry 的 provenance 与 expr 子域、`patra-common` 模块、RocketMQ，以及它们在构建、CI、CD、部署、配置、文档里的全部痕迹

## 1. 为什么做

### 1.1 业务上

ingest 是为「机构按检索式按需跨库采集」设计的：Plan → Task → 调外部 API → 存对象存储 → Outbox 经 RocketMQ 发给 catalog。Patra 从 v0.4 起做的是面向个人专业用户的门户，需要整库镜像加每日增量，这件事由 catalog 完成：下载文件或调 API，解析后直接写进 catalog 自己的库。

ingest 从未在线上跑过：XXL-Job 上没有它的执行器，库里的表全是 0 行，全仓也没有模块依赖它。以后加 EPMC、Crossref 等来源，照 catalog 现有的方式写。

### 1.2 留着的成本

- v0.16 规划里有两项是为它白干的：#6「ingest 改用通用事件模块」，以及 #2 里「下线 RocketMQ」那一半。
- 后端分包规范迁移只剩它没做；CI、CD 各占一个单元；mini 上的容器空占内存。
- 它是 RocketMQ 唯一的真实使用方，也是 `patra-object-storage` 唯一的调用方。

### 1.3 评估结论（10/30，分四路读代码 + 一轮业界调研）

删除前先评估过：有没有东西值得搬进 catalog。结论是**没有，全部删除**，需要时去 git 历史翻（线索见附录 A）。

| 部分 | 结论与理由 |
|---|---|
| 表达式（`patra-ingest-expr` + registry 的 expr 子域） | 它只做一件事：把条件拼成外部 API 的查询串。种子只接了 3 个来源、4 个字段，核心编译器和渲染器没有测试。增量拉取只需要一个日期窗口；门户检索要的是「用户检索式 → PG SQL」，它既没有解析器，输出也不是 SQL |
| 各来源客户端（`patra-ingest-provenance`） | DOAJ、Crossref 是空壳；EPMC 只有半截客户端、没有字段映射；PubMed 那套比 catalog 现有的解析器差（缺月补 1、日期压到 28）。以后要按 PMID 实时补抓单篇，写几十行 EFetch 喂给 catalog 现有的 `PubmedXmlParserPort` 即可 |
| 数据源运行配置（registry provenance 子域，7 张表） | 只有 PubMed 的窗口和分页各配了 1 行，HTTP、重试、限流、批量 4 张表是空的，ingest 自己也只用了两项。catalog 的「每个来源一个配置类 + Nacos 只放环境差异和密钥」更适合单人开发 |
| ingest 编排（Plan / Task / Outbox） | 运行锁、断点续采、失败重试、限流、幂等，catalog 都有且更扎实。唯一空缺「API 类来源按时间水位增量」跟表达式和配置快照绑死，搬不出来 |
| `patra-object-storage` 服务 | 只有一个接口 `POST /files/record`（上传后登记一条记录），没有查询、删除、过期清理、上传授权；唯一调用方是 ingest。catalog 和 identity 一直经 `patra-tool-object-storage` 直接写 MinIO |
| `patra-common` | 删完以上几项后，`CanonicalPublication`、`PartialDate`、`PublicationIdentifierType`、`ProvenanceCode` 只剩 catalog 在用，`DataType` 无人使用。只剩一个使用方的共享模块名不副实 |

对象存储的业界做法调研摘要见附录 B：没有哪家用一个独立服务统一管全系统的对象存储；集中的文件表很常见，但几乎都和业务数据在同一个库里。

## 2. 目标、非目标、反目标

**目标**

1. 删除 `patra-ingest`（8 个模块）、`patra-object-storage`（6 个模块）、registry 的 provenance 与 expr 子域、`patra-common` 模块、RocketMQ。
2. `patra-common` 里 catalog 在用的 4 个类搬进 catalog-domain，行为不变。
3. 全仓不再有指向已删部分的引用（验收命令见 §5）。
4. mini 上只剩在用的服务，其余服务行为不变。

**非目标**

- 不改 catalog、identity、interaction、gateway 的运行时行为、HTTP 接口和库表；registry 只删 provenance 与 expr，字典部分不动。
- 不合并 catalog 的 `DataSourceCode` 与 `ProvenanceCode`（概念有重叠，另议）。
- 不改门户「十个来源」文案（`Hero.tsx`、`portal-stats.ts`）。
- 不把 registry 并进 catalog。
- 不引入 RabbitMQ 或通用事件模块（v0.16 的事）。
- 不改历史文档：`docs/patra/specs`、`docs/patra/plans`、`docs/patra/release-specs`、`docs/patra/runbooks`、`patra-portal/docs`、`.github/CI_OPTIMIZATION.md`（2026-01-13 的历史说明）。
- 不清理 GHCR 上的旧镜像、`~/.patra/secrets/` 下的旧密钥文件、`~/.patra/docker/rocketmq` 数据目录；这些列为可选手动步骤（§4 第 8 步），由用户决定。

**反目标**（技术上删完了也算失败）

- 借「删干净」之名重构在用代码，例如合并 `DataSourceCode`、重排 catalog 子域。
- 误删在用的东西：`patra-tool-object-storage` 的读写能力、Nacos 共享配置 `patra-common.yaml`（与 `patra-common` 模块同名但无关）、registry 字典、网关的 registry 路由。
- mini 上 registry 因 Flyway 校验失败起不来，导致 catalog 导入时字典解析持续失败。

## 3. 改动清单

### 3.1 后端模块与构建接线

- 删 `patra-api/patra-ingest/**`、`patra-api/patra-object-storage/**`。
- `settings.gradle.kts`：删两者的 `includeAt` / `mapParent`。
- 根 `build.gradle.kts`：删目录 → 单元映射里的 `ingest`、`object-storage`，以及 `units` 列表里的这两项。
- `spotbugs-exclude.xml`：删 `dev.patra.ingest.*`、`dev.patra.objectstorage.*` 的条目。
- 删 `.run/PatraIngestApplication.run.xml`、`.run/PatraObjectStorageApplication.run.xml`。
- `.mcp.json`：删 `patra-pg-ingest`、`patra-pg-storage`。
- `gradle/libs.versions.toml`：只为已删模块存在的条目一起删。已知有 `jmh`、`jmh-core`、`jmh-generator`（只有 ingest 在用）和已无人引用的 `fastjson2` 版本号；删前逐条 grep 确认无人引用。RocketMQ 相关条目见 §3.4。

### 3.2 registry：删 provenance 与 expr

- 删各层的 `provenance/**`、`expr/**` 包（domain / app / infra / adapter / api），连同测试和 IT。
- `V1__init_registry_schema.sql`：删 11 张 `reg_*` 表的 DDL（`reg_provenance`、`reg_prov_window_offset_cfg`、`reg_prov_pagination_cfg`、`reg_prov_http_cfg`、`reg_prov_batching_cfg`、`reg_prov_retry_cfg`、`reg_prov_rate_limit_cfg`、`reg_expr_field_dict`、`reg_prov_api_param_map`、`reg_prov_expr_capability`、`reg_prov_expr_render_rule`），保留 `sys_dict_*` 与 `sys_reference_standard`。
- `V2__seed_provenance_expr.sql` 不能整个删：它的「第0部分：来源标准目录」往 `sys_reference_standard` 插了国家字典的两条参考标准 `ISO_3166_1_ALPHA2`（规范标准）和 `NAME_EN`，而 `V3__seed_dict_country.sql` 只插了 `NAME_ZH`（它头注释写的「共 3 条」与实际内容不符）。catalog 导入期刊时按 `country / NAME_EN` 解析国家（`VenuePubmedImportHandler`），缺了这条标准，registry 会报标准不存在，catalog 的 `DictionaryResolverAdapter` 只打 warn、返回空，国家字段就会静默变空。做法：
  - 把这两条 INSERT 搬进国家种子脚本，修正它的头注释；
  - V2 其余部分（provenance、expr 种子）删除；
  - 国家、语言两个种子脚本改名为 V2、V3。**我的决定**：库反正要重建（§4），版本号保持连续。
- 新增一个回归测试：在 Testcontainers 的 PG 上跑完 Flyway 后，断言 catalog 用到的参考标准都在（`country` 的 `ISO_3166_1_ALPHA2`、`NAME_EN`，`language` 的 `ISO_639_3` 等），并能把一个英文国家名解析成 ISO 代码。现有测试都是自己造数据或 mock，拦不住种子缺失。**我的决定。**
- `RegistryArchitectureTest`：子域声明改为底座 + `dictionary`，删掉 `expr → provenance` 的依赖边。
- registry 底座（`domain/kernel`）里删完后无人使用的类一起删。
- 改 registry README、`PatraRegistryApplication` 的 Javadoc。
- 网关的 registry 路由保留（字典接口还在）。

### 3.3 patra-common 并进 catalog

- `CanonicalPublication`、`PartialDate`、`PublicationIdentifierType`、`ProvenanceCode` 搬进 catalog-domain。落位按 `.claude/rules/tech/package-layout.md`：被多个子域（含 portal）用到的进底座 `kernel`，只被一个子域用的进该子域；以 `CatalogArchitectureTest` 通过为准。
- `CanonicalPublication` 上当年为 MQ 消息契约加的 `@JsonDeserialize` / `@JsonPOJOBuilder`：确认 catalog 不对它做 JSON 序列化（包括 Spring Batch 上下文）后去掉。
- 删 `DataType`。
- 删 `patra-api/patra-common` 模块，以及 build-logic 里的接线：
  - `patra.hexagonal-domain.gradle.kts`、`patra.hexagonal-api.gradle.kts` 里的 `api(project(":patra-api:patra-common"))` 和 `exempt` 集合；
  - `DomainPurityRules.ALLOWED`、`ToolDependencyRules` 的注释；
  - 对应测试 `DeclaredDependencyRulesTest`、`DomainPurityRulesTest`、`ToolDependencyRulesTest`。
- `patra-common` 以 `api` 方式导出了 `jackson-annotations` 和 `jackson-databind`，又被插件塞进了所有服务的 domain 和 api 模块，所以有模块是靠它传递拿到 Jackson 的。已知 catalog-domain 主代码有 11 个文件用 `@JsonIgnore`、`@JsonSubTypes`、`@JsonTypeInfo`、`@JsonCreator`、`@JsonValue`，两个测试用 `JsonMapper`。删接线后，逐个找出这类模块，按 build-logic 现有的依赖声明规则（`DeclaredDependencyRules`）显式声明；两者本来就在 `DomainPurityRules.ALLOWED` 里。以全仓 `compileJava compileTestJava` 通过为准。
- 搬家时只去掉 `CanonicalPublication` 上 databind 的 `@JsonDeserialize` / `@JsonPOJOBuilder`；`@JsonInclude`、`ProvenanceCode` 上的 `@JsonCreator` / `@JsonValue` 属于 jackson-annotations，照原样保留。
- catalog 的 `DataSourceCode` 不动（非目标）。

### 3.4 RocketMQ 下线

- `patra-infra/docker/docker-compose.jobs.yaml`：删 `rocketmq-namesrv`、`rocketmq-broker`、`rocketmq-dashboard`。
- `patra-infra/docker/.env`、`.env.common`、`.env.example`：删 RocketMQ 变量；`patra-infra/scripts/init-volumes.sh`：删 rocketmq 目录。
- `gradle/libs.versions.toml`：删 `rocketmq`、`rocketmq-spring-boot` 版本和库条目。
- catalog-infra、catalog-adapter 的 `build.gradle.kts`：删 rocketmq 依赖（死依赖，catalog 没有 RocketMQ 代码）。
- `PatraDependencyManagement.kt` 里为 rocketmq-common 打的 grpc / protobuf 约束，以及 `Lz4ForkRule` 与 `lz4` 版本：先用 `dependencyInsight` 确认已无其他来源再删；仍有来源的保留，并把注释改成真实来源。
- CI：`ci.yml` 删 `ROCKETMQ_IMAGE`、preflight 镜像一致性检查里的 rocketmq compose 文件、backend job 里的 `docker pull`。
- `patra-tool-test`：删 `ContainerType.ROCKETMQ`。
- catalog 里的过期注释：`PublicationAggregate` 第 33、256 行，`PatraCatalogApplication` 第 26 行。

### 3.5 patra-tool-object-storage：只删 ingest 用的部分

- 删 `ObjectKeyGenerator`、`DatePartitionedKeyGenerator`、`ObjectKeyContext`、`StorageLocationResolver` 及其测试，以及 `ObjectStorageAutoConfiguration` 里对它们的装配。
- 删完后只被它们引用的类一起删：已知有 `StorageLocation`、`StorageContext`，以及 `spotbugs-exclude.xml` 里提到它们的条目和注释。
- `ObjectStorageOperations` 等 catalog、identity 在用的部分不动，以两者的测试和 IT 为准。

### 3.6 网关

- `application.yml`：删 `patra-ingest` 路由和 springdoc 的 ingest 条目。
- `PatraGatewayApplication` 的 Javadoc。
- `GatewayFailureIT` 拿 ingest 路由测「下游无实例」：改用一条只在测试配置里声明的无实例路由。
- `ProxyFailureClassifierTest` 里的服务名改成在用服务。

### 3.7 基础设施与 CD

- `patra-infra/cd/services.json`：删 `ingest`、`object-storage` 条目。
- `detect-changes.sh` 的 `ALL_UNITS`、`detect-changes.test.sh`、`deploy.sh` 注释（「object-storage 最先」那句）、`deploy.test.sh`。
- `module-graph.json`：用 `dumpModuleGraph` 重新生成，不手改。
- `docker-compose.apps.yaml`：删 `ingest`、`object-storage` 服务。
- `docker-compose.storage.yaml`：删 `patra-ingest-cache` 桶的创建与说明。
- `postgres/init-scripts/02-create-databases.sql`：删 `patra_ingest`、`patra_storage`。
- 删 `nacos/dev/patra-ingest.yaml`、`nacos/dev/patra-object-storage.yaml`；`scripts/nacos-verify.sh` 的 `SERVICES`。
- `service.Dockerfile` 注释里的服务列表。

### 3.8 文档、规则、技能、学习站、测试样例

原则：**§2 列出的历史文档目录一律不改；除此之外的文件都要做到 §5 的零命中**。描述当前系统的段落按新现实改；示例里的服务名、容器名换成在用的服务；不在排除目录里、但带日期的旧记录（例如 `docs/mac-mini-connectivity.md` 的排障条目、patra-learn 里讲过去 CI 经历的段落），改写成不含已删名称的等价说法，或者在已不成立时删掉该条。**我的决定**：规则简单，验收命令不用逐个文件开例外。

- 根 `CLAUDE.md` 产品定义：「采集来自 PubMed / EPMC / Crossref 等 10+ 外部数据源」改成实际来源（PubMed、MeSH、ROR、PMC 开放获取、OpenAlex、Scopus、LetPub）。
- `.claude/rules/project-info.md`：核心服务列表、模块结构、`patra-common` 段、「ingest 专属子模块」段。
- `.claude/rules/tech/package-layout.md`（「ingest 尚未迁移」）、`tech/error-handling.md`（`INGEST` 前缀示例）、`testing/conventions.md`（示例名）、`layers/infrastructure.md`（object-storage 服务的提法）。
- 技能：
  - patra-hexagonal（`SKILL.md`、`resources/configuration.md`、`resources/patra-starters.md`）、patra-troubleshooter（`references/log-config.md`）、release-planning `SKILL.md` 里的 ingest 示例、`agents/test-checker.md` 的 RocketMQ。
  - patra-events：保留进程内领域事件部分；删「Outbox + RocketMQ」一节和 `resources/outbox-guide.md`，它描述的实现随 ingest 删除，v0.16 做通用事件模块时重写。**我的决定。**
- README：根 `README.md`、`patra-api/README.md`、`patra-catalog/README.md` 第 20 行（catalog 只用 registry 的字典）、`patra-registry/README.md`、`patra-gateway-boot/README.md`、`patra-identity/README.md` 里若提到 object-storage 服务。
- `patra-infra/CLAUDE.md`、`docker/README.md`、`nacos/dev/README.md`、`docs/mac-mini-connectivity.md`。
- `.github/git-commit-instructions.md` 里的 `ingest` 范围示例换成在用的范围。
- 测试样例和注释里拿 `patra-ingest` 当示例服务名的（`patra-tool-launch` 的 4 个测试、`patra-tool-web` 的 `ProblemFieldContributor` 与 README、`HexagonalArchitectureRules` 的 Javadoc），改成在用服务。
- patra-learn：`lines.ts` 删「采集管道（规划中）」；`deploy-loop.tsx`、`parallel-exams.tsx` 里描述当前部署顺序、CI 镜像的段落按新现实改。**我的决定。**

## 4. mini 上的运维

每一步在执行时由用户授权。顺序很重要：

1. **合并前，按这个顺序做**：
   1. `docker stop patra-registry`。旧容器一直连着库（连接池常驻 5 个连接），不停它 PG 会拒绝删库；改用 `DROP DATABASE … WITH (FORCE)` 也不行，旧容器配了 `restart: unless-stopped`，一旦重启，旧镜像的 Flyway 会把旧的 V1–V4 灌进空库，又回到校验失败。手动 stop 之后，`unless-stopped` 不会再把它拉起来。
   2. `DROP DATABASE patra_registry`，再建一个同名空库。
   3. 尽快合并，让 CD 拉起新 registry，由 Flyway 建表并灌入字典。

   不重建的话，CD 部署新 registry 时 Flyway 会因 V1 校验和变化、旧 V2 已执行却找不到而校验失败，服务起不来。从停容器到新 registry 起来这段时间，避开 catalog 的导入任务（导入时要解析字典）。本机若另有 PG 实例跑过 registry，同样处理。
2. 合并后 CD 自动部署。`services.json` 里没有了 ingest 和 object-storage，但 CD 不会清理孤儿容器，要手动 `docker rm -f patra-ingest patra-object-storage`。
3. 下线 RocketMQ：`docker rm -f patra-rocketmq-namesrv patra-rocketmq-broker patra-rocketmq-dashboard`（jobs compose 不归 CD 管）。
4. 删库：`DROP DATABASE patra_ingest`、`DROP DATABASE patra_storage`。
5. MinIO：删桶 `patra-ingest-cache`。
6. Nacos：确认推送脚本已删掉 `patra-ingest.yaml`、`patra-object-storage.yaml`（脚本会同步删除仓库里已删的配置，见 `NacosPushScriptIT`）；没删就手动删。
7. XXL-Job：ingest 从没注册过执行器，但 xxl-job-admin 的库里可能还留着它的执行器或任务定义，有就删。
8. 可选，由用户决定：GHCR 上的 `patra-ingest`、`patra-object-storage` 镜像包；`~/.patra/secrets/` 下 ingest 与 object-storage 的 env 文件（两台机器）；`~/.patra/docker/rocketmq` 数据目录。

## 5. 验证与验收

本次只删除和搬家、不改行为，不适用 TDD，主要靠现有测试兜底。现有测试拦不住的种子缺失，由 §3.2 新增的回归测试补上。

**构建与测试**

- `./gradlew compileJava compileTestJava`
- §3.2 的字典种子回归测试通过
- `./gradlew check`；另外显式跑 catalog、registry、gateway、identity 的 `integrationTest`，以及 catalog 的 `e2eTest`（用到搬家的类）
- build-logic 的测试
- `patra-infra/cd/detect-changes.test.sh`、`patra-infra/cd/deploy.test.sh`
- 重新生成后的 `module-graph.json` 与提交内容一致
- patra-learn 的 lint、测试、构建

**引用检查**（排除历史文档：`':!docs/patra/specs' ':!docs/patra/plans' ':!docs/patra/release-specs' ':!docs/patra/runbooks' ':!patra-portal/docs' ':!.github/CI_OPTIMIZATION.md'`，本文档与附录也在 `docs/patra/specs` 下）

- `git grep -niw ingest` 零命中。`-w` 按整词匹配，MeSH 数据里的 ingestion、Loki 的 ingestion_rate、Tempo 的 ingester 这类英文词不算。
- `git grep -niE 'patra_ingest|patraingest'` 零命中。`-w` 把下划线和字母都算作词内字符，抓不到库名 `patra_ingest` 和 `PatraIngestApplication` 这类写法，所以单独查。
- `git grep -ni rocketmq` 零命中。
- `git grep -niE 'patra-object-storage|patraobjectstorage|dev\.patra\.objectstorage|patra_storage|:patra-api:patra-common|dev\.patra\.common\.'` 零命中。`patra-tool-object-storage` 和 Nacos 的 `patra-common.yaml` 不会被这条命中。

**mini 上**

- CD 成功；`docker ps` 里没有已删的容器。
- registry 健康，字典接口能返回国家和语言。
- 按 `country / NAME_EN` 解析一次（例如 `China` → `CN`），确认 catalog 期刊导入用的那条标准在；只看字典列表不够，列表只需要 `NAME_ZH` 就能返回。
- 门户检索、文献详情（含全文）、头像上传都正常。

## 6. 和其他工作的关系

- **v0.16 通知中心**：#6「ingest 改用通用事件模块」不再需要，#2 缩成「接入 RabbitMQ」。v0.16 的后端分支在本 PR 合并后再从 main 切出。附录 A 的 Outbox 要点可供 v0.16 参考。
- **v0.15 前端 PR**（进行中）：本 PR 不碰 portal，不冲突。

## 7. 留给实施者

- 提交怎么拆（建议按 §3 的小节拆，便于评审和回滚）。
- 搬家类的具体包位置（按 §3.3 的规则）。
- `GatewayFailureIT` 的替代路由怎么写。
- 每条依赖补丁删不删（按 §3.4 的核实规则）。
- registry 底座里哪些类删完后无人使用。

另外要知道：本分支在工作树 `.claude/worktrees/remove-ingest` 里，JetBrains MCP 只认 IDEA 打开的主检出，在这里用不了。§3.3 的类搬家只能靠文本修改，以 `compileJava compileTestJava` 和 `CatalogArchitectureTest` 把关（没有现成的「移动类」工具，项目规则要求走 IDEA 的是重命名）。如果主检出空出来了，也可以在主检出上做这一步。

## 附录 A：以后用得上的线索

代码都在基线 `b554d9961` 里，git 历史可查，这里只记位置。

- **catalog 还没解析的 PubMed 字段**：参考文献、评论与勘误关联（CommentsCorrections）、利益冲突声明（CoiStatement）、基因（GeneSymbolList）、化学物质（ChemicalList）、数据库关联（DataBankList）、补充对象。字段对照见 `patra-api/patra-ingest/patra-ingest-provenance/src/main/java/dev/patra/ingest/provenance/pubmed/converter/PubmedPublicationConverter.java`（最近改动 `3cf6fa575`）；要补时按 catalog 的 StAX 解析器重写。
- **增量窗口公式**（当前时间减水位延迟、水位减回看量）：`patra-api/patra-ingest/patra-ingest-app/src/main/java/dev/patra/ingest/app/usecase/plan/window/PlanningWindowResolverImpl.java`；游标表 `patra-api/patra-ingest/patra-ingest-infra/src/main/resources/db/migration/V3__init_ingest_cursor.sql`。
- **PubMed E-utilities 经验值**：PMID 超过 200 个就走 EPost + WebEnv 再 EFetch，EPost 后固定等 600 ms（注释称是 NCBI 建议，未核实），见 `patra-api/patra-ingest/patra-ingest-provenance/src/main/java/dev/patra/ingest/provenance/pubmed/processor/PubmedPublicationProcessor.java`。NCBI 官方限流：无 key 3 次/秒，有 key 10 次/秒。
- **三个来源的增量查询写法**：PubMed `mindate` / `maxdate` / `datetype`（右开区间转右闭要减 1 天）；EPMC `FIRST_PDATE:[a TO b]`；Crossref `filter=from-pub-date:..,until-pub-date:..`。见 `patra-api/patra-registry/patra-registry-infra/src/main/resources/db/migration/V2__seed_provenance_expr.sql`。
- **API 地址与文档**：PubMed `https://eutils.ncbi.nlm.nih.gov/entrez/eutils/`（文档 NBK25501）；EPMC `https://www.ebi.ac.uk/europepmc/webservices/rest/`；Crossref `https://api.crossref.org/`。
- **ingest Outbox 的设计要点**（供 v0.16 参考，Spring Modulith 的事件发布登记不自带这些）：`(channel, dedup_key)` 唯一索引保证源端幂等；version 乐观锁加租约领取，回收过期的 PUBLISHING；`not_before` 延迟投递加指数退避，区分 FATAL / TRANSIENT；`partition_key` 保序；按状态建部分索引；`relay_log` 逐次审计。见 `patra-api/patra-ingest/patra-ingest-infra/src/main/resources/db/migration/V4__init_ingest_outbox.sql`。

## 附录 B：对象存储做法调研摘要（2026-10-30）

- **国内 Java 框架**（RuoYi-Cloud、RuoYi-Vue-Plus、芋道、pig、JeecgBoot、SpringBlade）：集中文件服务主要出现在这一类，但统一的只是入口和配置；多数没有元数据表或业务表只存 URL；6 个都没有孤儿文件清理。
- **用户上传多的开源产品**（GitLab、Discourse、Mastodon、Mattermost、Chatwoot）：都是单体，进程内统一上传模块 + 集中文件表 + 业务引用；直传先到临时位置再转正；孤儿按宽限期清理（Discourse 48 小时、Mastodon 1 天）。
- **学术平台**（InvenioRDM / Zenodo、DSpace、OJS）：同进程文件模块，有定期校验和核对。独立文件服务只有 OSF 的 WaterButler（对接第三方存储）和 arXiv 的 filemanager（投稿解包检查）。
- **框架文件库**：Rails Active Storage 最完整（待定记录 → 预签名直传 → 挂上转正 → 提交后异步删 → 定时清理未挂载）；Django 1.3 因「事务回滚但文件已删」撤掉了自动删文件；没有哪个库自动清孤儿。
- **云厂商与大厂**：预签名直传是主流（Azure Valet Key、Slack 2024 年的上传接口改造）；微服务方法论只说数据归服务私有；大厂集中建的是媒体平台（Uber Eats 图片去重、Facebook Haystack），为去重、CDN、转码。
- **对 Patra 的含义**：现在用「各服务直连 MinIO + 共享库」；以后讨论区要传图或附件时，在用到的服务里照 Active Storage / Discourse 的流程做；多个服务都要时，把流程抽进 `patra-tool-object-storage`，不另起服务。

主要来源：[Azure Valet Key](https://learn.microsoft.com/en-us/azure/architecture/patterns/valet-key)、[Slack 上传接口改造](https://docs.slack.dev/changelog/2024-04-a-better-way-to-upload-files-is-here-to-stay)、[Rails Active Storage 指南](https://guides.rubyonrails.org/active_storage_overview.html)、[Discourse 上传清理](https://meta.discourse.org/t/cleaning-up-uploads-and-purging-uploads-from-s3/248343)、[GitLab 上传开发文档](https://docs.gitlab.com/development/uploads)、[Django 1.3 发布说明](https://docs.djangoproject.com/en/dev/releases/1.3/)、[WaterButler 概览](https://waterbutler.readthedocs.io/en/latest/overview.html)、[Uber Eats 图片去重](https://www.uber.com/blog/deduping-and-storing-images-at-uber-eats/)、[芋道文件文档](https://doc.iocoder.cn/file/)。
