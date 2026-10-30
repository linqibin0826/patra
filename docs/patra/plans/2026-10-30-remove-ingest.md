# 删除 ingest 及附属 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删掉 ingest、object-storage 服务、registry 的 provenance 与 expr、patra-common、RocketMQ，以及它们在全仓的痕迹；其余服务行为不变。

**Architecture:** 先删两个没人依赖的服务（ingest、object-storage），再删 registry 里只给 ingest 用的子域（连同 Flyway 改写），然后把 patra-common 剩下的 4 个类搬进 catalog 并删模块，最后清 RocketMQ、部署配置和文档。只删除和搬家，唯一新增的代码是一个字典种子回归测试。

**Tech Stack:** Java 25、Spring Boot 4、Gradle 9（Kotlin DSL + build-logic 约定插件）、Flyway / PostgreSQL 17、Testcontainers、Docker Compose、GitHub Actions、Next.js（patra-learn）

**Spec:** `docs/patra/specs/2026-10-30-remove-ingest-design.md`（下文「spec §x」指它的章节）

## Global Constraints

- 工作目录固定为工作树 `/Users/linqibin/Projects/Products/patra/.claude/worktrees/remove-ingest`，分支 `refactor/remove-ingest`。一律用绝对路径，不 `cd` 出去；git 命令写成简单形式（工作树隔离会拒绝复杂的 git 调用）。
- **提交要用户同意**（根 CLAUDE.md 禁止自动提交）。开工前问一次：是否授权按任务提交；没授权就攒着，全部做完再一起问。每次提交前跑 `git diff --cached --stat`，只含本任务的路径。
- 提交信息：`type(scope): 中文动词开头`，subject 不以大写字母开头（commitlint）；结尾带 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
- `./gradlew check` 之前先 `./gradlew spotlessApply`；spotless 改过的文件要重新 `git add`。
- 改了任何 `*.gradle.kts` 后跑 `./gradlew dumpModuleGraph`，把 `patra-infra/cd/module-graph.json` 一起提交（CI preflight 会查它是否陈旧）。
- 新写或改动的 Java 方法用 `///` Markdown Javadoc；不写全类名；测试方法 snake_case；IT 加 `@Timeout`（≤ 30 秒）。
- 不改这些历史文档：`docs/patra/specs/**`（本 spec 除外）、`docs/patra/plans/**`（本计划除外）、`docs/patra/release-specs/**`、`docs/patra/runbooks/**`、`patra-portal/docs/**`、`.github/CI_OPTIMIZATION.md`。
- **别误删**：Nacos 共享配置 `patra-infra/nacos/dev/patra-common.yaml` 与 `patra-common` 模块同名但无关，保留；`patra-tool-object-storage` 的读写能力（`ObjectStorageOperations` 等）保留；registry 字典与网关的 registry 路由保留。
- JetBrains MCP 在工作树里用不了（只认主检出）。类搬家靠文本修改 + 编译把关。
- 本计划只有 Task 2 一个新测试（spec §3.2 的回归测试）；其余任务只删除和搬家，不适用 TDD，靠现有测试验证。

## Review Focus

1. **库重建后国家字典缺参考标准**：catalog 按 `country / NAME_EN` 解析时，应解析出 ISO 代码，而不是静默返回空。由 Task 2 的 `DictionarySeedIT` 钉住。
2. **`CanonicalPublication` 去掉 databind 注解后被序列化**：如果 Spring Batch 上下文、缓存或日志里有地方对它做 JSON 序列化，去掉 `@JsonDeserialize` / `@JsonPOJOBuilder` 会在导入时才炸。Task 3 先 grep 确认没有，再用 catalog 的 `e2eTest`（含 `PubmedFileImportE2E`）实跑导入。
3. **删依赖补丁后漏洞版本回来**：去掉为 rocketmq-common 打的 grpc / protobuf / lz4 补丁时，若 Nacos 客户端等其他依赖也带这些构件，版本会退回有漏洞的旧版。Task 5 对 5 个 boot 模块的 `runtimeClasspath` 逐个核实。
4. **catalog、identity 的对象存储读写被连带删坏**：删对象键生成类时，自动装配若还有 bean 依赖它们，启动就会失败。Task 4 用 catalog 的 `FullTextObjectStorageAdapterIT`、identity 的 `AvatarFlowIT` / `AvatarStorageAdapterIT` 验证。
5. **CD 的单元列表与模块图不一致**：`units` 里留着没有模块的单元，CI 会报「单元在 module-graph.json 中无任务」。Task 1、Task 3 都重新生成模块图，并跑 `detect-changes.test.sh`。

---

## 开工前

- [ ] 确认工作树干净、基线正确：`git status --short` 无输出（spec 与本计划是未跟踪文件除外），`git log --oneline -1` 为 `b554d9961`。
- [ ] 问用户是否授权按任务提交（见 Global Constraints）。
- [ ] 加载 `patra-backend:patra-hexagonal`（patra-api/CLAUDE.md 要求改后端代码前加载）。
- [ ] 提交 spec 与本计划：

```bash
git add docs/patra/specs/2026-10-30-remove-ingest-design.md docs/patra/plans/2026-10-30-remove-ingest.md
git commit -m "docs(api): 添加删除 ingest 的设计与实施计划 (PAP-136)"
```

---

### Task 1：删除 ingest 与 object-storage 服务，以及构建和 CI/CD 接线

**Files:**
- Delete: `patra-api/patra-ingest/`、`patra-api/patra-object-storage/`、`.run/PatraIngestApplication.run.xml`、`.run/PatraObjectStorageApplication.run.xml`
- Modify: `settings.gradle.kts`（ingest 第 78–86 行、object-storage 第 96–102 行）
- Modify: `build.gradle.kts`（`unitOf` 里的 `object-storage`、`ingest` 两个分支；`units` 列表）
- Modify: `spotbugs-exclude.xml`（`dev.patra.ingest.*`、`dev.patra.objectstorage.*` 的条目；registry、tool 的条目不动）
- Modify: `gradle/libs.versions.toml`（`jmh`、`jmh-core`、`jmh-generator`、`fastjson2`：先 `git grep` 确认已无人引用）
- Modify: `patra-api/patra-gateway-boot/src/main/resources/application.yml`（删 `patra-ingest` 路由与 springdoc 的 ingest 条目）
- Modify: `patra-api/patra-gateway-boot/src/main/java/dev/patra/gateway/PatraGatewayApplication.java`（Javadoc 第 10 行的服务列表）
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/GatewayFailureIT.java`
- Modify: `patra-api/patra-gateway-boot/src/test/java/dev/patra/gateway/error/ProxyFailureClassifierTest.java`（第 28 行服务名改成 `patra-interaction`）
- Modify: `patra-infra/cd/services.json`（删 `object-storage`、`ingest` 条目）
- Modify: `patra-infra/cd/detect-changes.sh`（第 6 行注释、第 22 行 `ALL_UNITS`）、`patra-infra/cd/detect-changes.test.sh`
- Modify: `patra-infra/cd/deploy.sh`（第 6、12 行注释）、`patra-infra/cd/deploy.test.sh`
- Regenerate: `patra-infra/cd/module-graph.json`

**Interfaces:**
- Produces：之后的任务都基于「仓库里已没有 `patra-ingest`、`patra-object-storage` 模块」这一前提；`units` 变为 `registry, catalog, identity, interaction, gateway, foundation`。
- 注意：`.github/workflows/ci.yml` 第 87 行的 preflight 引用了 ingest 里的 `docker-compose-rocketmq.yml`，本任务删掉后它会失效，由 Task 5 一并改。中间状态不推送，不影响。

- [ ] **Step 1：删目录与运行配置**，并改 `settings.gradle.kts`、`build.gradle.kts`。
- [ ] **Step 2：清 `spotbugs-exclude.xml`、`libs.versions.toml`**。`libs.versions.toml` 每删一条前先 `git grep -n '<别名>'`，确认零引用。
- [ ] **Step 3：网关**。删路由和 springdoc 条目；`GatewayFailureIT` 的「无实例返回 503」改用 `/patra-interaction/` 下的路径（这个 IT 只给 catalog、registry 配了实例），类注释第 40 行改成「interaction 不配实例」，若断言 `$.detail` 里含服务名，同步改掉。
- [ ] **Step 4：CD 脚本**。`services.json` 删两条；`detect-changes.sh` 的 `ALL_UNITS` 去掉两个单元；`detect-changes.test.sh` 里断言含 `ingest` / `object-storage` 的场景改期望值，`object-storage-api` 扇出那条场景整条删除；`deploy.sh` 注释里「object-storage 最先」那句删掉；`deploy.test.sh` 里涉及这两个服务的用例同步。
- [ ] **Step 5：重新生成模块图**

Run: `./gradlew dumpModuleGraph -q`
Expected: 输出「✓ 写出 patra-infra/cd/module-graph.json」，文件里不再有 `ingest`、`object-storage`。

- [ ] **Step 6：编译与测试**

Run: `./gradlew compileJava compileTestJava`
Expected: BUILD SUCCESSFUL

Run: `bash patra-infra/cd/detect-changes.test.sh && bash patra-infra/cd/deploy.test.sh`
Expected: 全部场景通过、退出码 0

Run: `./gradlew spotlessApply :patra-api:patra-gateway-boot:check :patra-api:patra-gateway-boot:integrationTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7：提交**（需已授权）

```bash
git add -A settings.gradle.kts build.gradle.kts spotbugs-exclude.xml gradle/libs.versions.toml .run patra-api/patra-ingest patra-api/patra-object-storage patra-api/patra-gateway-boot patra-infra/cd
git diff --cached --stat
git commit -m "refactor(api): 删除 ingest 与 object-storage 服务及其构建和 CD 接线 (PAP-136)"
```

---

### Task 2：registry 删 provenance 与 expr，改写 Flyway 并加字典种子回归测试

**Files:**
- Create: `patra-api/patra-registry/patra-registry-infra/src/integrationTest/java/dev/patra/registry/infra/dictionary/persistence/DictionarySeedIT.java`
- Delete: registry 各层的 `provenance/`、`expr/` 包（domain 57 个、infra 30 个、api 15 个、app 8 个、adapter 8 个文件，含测试与 IT）
- Delete: `patra-registry-infra/src/main/resources/db/migration/V2__seed_provenance_expr.sql`
- Modify: `V1__init_registry_schema.sql`（删第 245–699 行，即 `reg_provenance` 段注释到 `reg_prov_expr_render_rule` 的 COMMENT 为止，保留文件头尾）
- Rename: `V3__seed_dict_country.sql` → `V2__seed_dict_country.sql`、`V4__seed_dict_language.sql` → `V3__seed_dict_language.sql`（用 `git mv`）
- Modify: `RegistryArchitectureTest.java`（子域集合改为底座 + `dictionary`，删 `allowingDependency("expr", "provenance")`）
- Modify: `PatraRegistryApplication.java`（Javadoc）、`patra-registry-boot/src/main/resources/application.yml`（provenance / expr 相关配置）、`RegistryITWebMvcConfig.java`、`DictionaryJpaMapper.java` / `ReferenceStandardJpaMapper.java`（注释里的提法）、`patra-registry/README.md`、`patra-registry-domain/TEST_COVERAGE_SUMMARY.md`
- Modify: registry 底座（`domain/kernel`）里删完后无人引用的类：删除；`RegistryException`、`RegistryKeyPlaceholders` 里提到 provenance / expr 的注释改掉

**Interfaces:**
- Consumes：Task 1 之后已没有任何模块依赖 `patra-registry-api` 的 provenance、expr 接口。
- Produces：registry 只剩 `kernel` 与 `dictionary` 两个子域；registry 不再 import `dev.patra.common.*`（Task 3 依赖这一点）。

- [ ] **Step 1：写回归测试 `DictionarySeedIT`**

仿照同目录 `ReferenceStandardRepositoryAdapterIT` 的注解组合（`@DataJpaTest`、`RegistryITPostgreSQLContainerInitializer`、`FlywayAutoConfiguration`、`@ActiveProfiles("test")`），`@Import` 换成 `ReferenceStandardRepositoryAdapter` 与 `DictionaryRepositoryAdapter`。不在测试里造数据，只读 Flyway 灌入的种子：

```java
@Test
void should_seed_reference_standards_used_by_catalog() {
  assertThat(standards.findByDictTypeCodeAndStandardCode("country", "ISO_3166_1_ALPHA2"))
      .hasValueSatisfying(s -> assertThat(s.canonical()).isTrue());
  assertThat(standards.findByDictTypeCodeAndStandardCode("country", "NAME_EN")).isPresent();
  assertThat(standards.findByDictTypeCodeAndStandardCode("language", "ISO_639_3")).isPresent();
}

@Test
void should_resolve_english_country_name_to_iso_code() {
  DictionaryType country = dictionaries.findTypeByCode("country").orElseThrow();
  Map<String, DictionaryItem> items =
      dictionaries.findItemsByAliases(country.id(), "name_en", Set.of("China"));
  assertThat(items).containsKey("China");
  assertThat(items.get("China").itemCode()).isEqualTo("CN");
}
```

- [ ] **Step 2：在基线上跑，确认测试本身是对的**

Run: `./gradlew :patra-api:patra-registry:patra-registry-infra:integrationTest --tests "*DictionarySeedIT"`
Expected: PASS（基线的旧 V2 还在，两条标准存在）

- [ ] **Step 3：删 provenance 与 expr 的代码、改 V1、删 V2、两个种子改名**

种子改名后，**先不要**把两条参考标准搬过去，下一步要看到测试失败。

- [ ] **Step 4：确认回归测试能拦住种子缺失**

Run: `./gradlew :patra-api:patra-registry:patra-registry-infra:integrationTest --tests "*DictionarySeedIT"`
Expected: FAIL，`country` 的 `ISO_3166_1_ALPHA2` 与 `NAME_EN` 查不到

- [ ] **Step 5：把旧 V2「第0部分：来源标准目录」的两条 `INSERT INTO sys_reference_standard`（id `900000000000000002` 的 `ISO_3166_1_ALPHA2`、`900000000000000003` 的 `NAME_EN`）原样搬进 `V2__seed_dict_country.sql`**，放在它插 `NAME_ZH` 标准的语句之前；修正文件头注释（「V3」改「V2」，参考标准确为 3 条）。语言种子只改头注释里的版本号。

- [ ] **Step 6：回归测试转绿，registry 全量验证**

Run: `./gradlew :patra-api:patra-registry:patra-registry-infra:integrationTest --tests "*DictionarySeedIT"`
Expected: PASS

Run: `./gradlew spotlessApply compileJava compileTestJava :patra-api:patra-registry:check` 以及 registry 各模块的 `integrationTest`（`git ls-files patra-api/patra-registry | grep src/integrationTest` 列出的模块）
Expected: BUILD SUCCESSFUL；`RegistryArchitectureTest` 通过

- [ ] **Step 7：提交**

```bash
git add -A patra-api/patra-registry
git diff --cached --stat
git commit -m "refactor(registry): 删除数据源配置与表达式子域，国家参考标准并入国家种子 (PAP-136)"
```

---

### Task 3：patra-common 并进 catalog，删除模块

**Files:**
- Move（从 `patra-api/patra-common/src/main/java/dev/patra/common/`）：
  - `ProvenanceCode.java` → `patra-api/patra-catalog/patra-catalog-domain/src/main/java/dev/patra/catalog/domain/kernel/model/enums/ProvenanceCode.java`（publication、venue、portal 都在用，进底座）
  - `PublicationIdentifierType.java` → 同目录 `.../domain/kernel/model/enums/`（publication 与 portal 在用，portal 只能依赖底座）
  - `CanonicalPublication.java` → `.../domain/publication/model/vo/CanonicalPublication.java`（只有 publication 在用）
  - `PartialDate.java` → `.../domain/publication/model/vo/PartialDate.java`（只有 publication 在用）
- Delete: `DataType.java`、整个 `patra-api/patra-common/`（含其测试，测试随类迁到 catalog-domain 对应的 test 目录）
- Modify: catalog 里所有 `import dev.patra.common.*`（main、test、integrationTest、e2eTest、testFixtures，约 35 个文件）
- Modify: `patra-api/patra-catalog/patra-catalog-domain/build.gradle.kts`（显式声明 Jackson，见 Step 3）
- Modify: `build-logic/src/main/kotlin/patra.hexagonal-domain.gradle.kts`（第 7 行注释、第 53 行依赖、第 66–67 行 exempt）、`patra.hexagonal-api.gradle.kts`（第 19 行依赖、第 39–40 行 exempt）、`patra.hexagonal-boot.gradle.kts`（第 44 行注释）
- Modify: `build-logic/src/main/kotlin/DomainPurityRules.kt`（`ALLOWED` 删 `dev.patra:patra-common`）、`ToolDependencyRules.kt`（第 20 行注释）
- Modify: `build-logic/src/test/kotlin/DeclaredDependencyRulesTest.kt`、`DomainPurityRulesTest.kt`、`ToolDependencyRulesTest.kt`（把 `patra-common` 换成仍存在的模块，例如 `:patra-api:patra-catalog:patra-catalog-api`，测试意图不变）
- Modify: `settings.gradle.kts`（第 50–51 行）
- Modify: `patra-infra/cd/detect-changes.test.sh`（第 44 行 `patra-api/patra-common/src/main/java/X.java` 的扇出场景删除，或换成 `patra-tool/patra-tool-core` 下的文件；第 99 行 Nacos 共享配置 `patra-common.yaml` 的场景与本模块无关，保留）
- Regenerate: `patra-infra/cd/module-graph.json`

**Interfaces:**
- Consumes：Task 1、Task 2 之后，`dev.patra.common.*` 只剩 catalog 引用。
- Produces：`dev.patra.catalog.domain.kernel.model.enums.ProvenanceCode`、`dev.patra.catalog.domain.kernel.model.enums.PublicationIdentifierType`、`dev.patra.catalog.domain.publication.model.vo.CanonicalPublication`、`dev.patra.catalog.domain.publication.model.vo.PartialDate`；类名、成员与行为不变，只换包。

- [ ] **Step 1：搬 4 个类及其测试、改包声明、批量改 catalog 的 import**；删 `DataType`。`ProvenanceCode` 与 catalog 已有的 `DataSourceCode` 放在同一个包里，两者都保留（spec 非目标）。
- [ ] **Step 2：处理 `CanonicalPublication` 的 databind 注解**。先确认没人对它做 JSON 序列化：

Run: `git grep -nE 'CanonicalPublication' -- patra-api/patra-catalog | grep -iE 'json|mapper|serial|ExecutionContext'`
Expected: 无输出（或只有与 JSON 无关的命中）。有命中就保留注解并在提交说明里写明原因。

确认后去掉 `@JsonDeserialize` / `@JsonPOJOBuilder`；`@JsonInclude`、`ProvenanceCode` 上的 `@JsonCreator` / `@JsonValue` 保留。

- [ ] **Step 3：删 patra-common 模块与 build-logic 接线**；在 catalog-domain 的 `build.gradle.kts` 里声明 `api("com.fasterxml.jackson.core:jackson-annotations")`（主代码 11 个文件在用）和 `testImplementation("tools.jackson.core:jackson-databind")`（2 个测试用 `JsonMapper`），写法照 build-logic 现有的依赖声明规则；其余服务的 domain、api 模块都没用 Jackson，不用加。
- [ ] **Step 4：重新生成模块图、更新 `detect-changes.test.sh`**

Run: `./gradlew dumpModuleGraph -q && bash patra-infra/cd/detect-changes.test.sh`
Expected: 模块图里没有 `patra-common`；测试全部通过

- [ ] **Step 5：验证**

Run: `./gradlew compileJava compileTestJava`
Expected: BUILD SUCCESSFUL（全仓）

Run: `./gradlew -p build-logic test`
Expected: BUILD SUCCESSFUL

Run: `./gradlew spotlessApply :patra-api:patra-catalog:check`，再跑 catalog 各模块的 `integrationTest` 与 `:patra-api:patra-catalog:patra-catalog-boot:e2eTest`
Expected: BUILD SUCCESSFUL；`CatalogArchitectureTest` 通过（底座只放被两个以上子域共用的东西）

- [ ] **Step 6：提交**

```bash
git add -A patra-api/patra-common patra-api/patra-catalog build-logic settings.gradle.kts patra-infra/cd
git diff --cached --stat
git commit -m "refactor(catalog): 把 patra-common 的四个类并入 catalog 并删除该模块 (PAP-136)"
```

---

### Task 4：patra-tool-object-storage 删对象键生成

**Files:**
- Delete: `patra-tool/patra-tool-object-storage/src/main/java/dev/patra/tool/objectstorage/` 下的 `StorageLocationResolver.java`、`StorageLocation.java`、`StorageContext.java`、`key/ObjectKeyGenerator.java`、`key/DatePartitionedKeyGenerator.java`、`key/ObjectKeyContext.java`，以及对应测试（含 `StorageLocationResolverTest.java`）
- Modify: `autoconfigure/ObjectStorageAutoConfiguration.java`（删 `storageLocationResolver` bean 及第 35、168 行附近的说明）
- Modify: `spotbugs-exclude.xml`（第 395 行附近提到 `StorageLocation` / `StorageContext` 的条目与注释）

**Interfaces:**
- Consumes：Task 1 之后，这些类在工具库外已无引用。
- Produces：`ObjectStorageOperations` 及 catalog、identity 在用的 API 不变。

- [ ] **Step 1：删类、删 bean、清 spotbugs 条目**。删完后 `git grep -nE 'StorageLocation|StorageContext|ObjectKey' -- patra-tool patra-api` 应无输出。
- [ ] **Step 2：验证**

Run: `./gradlew spotlessApply :patra-tool:patra-tool-object-storage:check`
Expected: BUILD SUCCESSFUL

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest --tests "*FullTextObjectStorageAdapterIT" :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*AvatarStorageAdapterIT" :patra-api:patra-identity:patra-identity-boot:integrationTest --tests "*AvatarFlowIT"`
Expected: PASS

- [ ] **Step 3：提交**

```bash
git add -A patra-tool/patra-tool-object-storage spotbugs-exclude.xml
git diff --cached --stat
git commit -m "refactor(tool): 删除只给 ingest 用的对象键生成 (PAP-136)"
```

---

### Task 5：RocketMQ 下线

**Files:**
- Modify: `patra-infra/docker/docker-compose.jobs.yaml`（删 `rocketmq-namesrv`、`rocketmq-broker`、`rocketmq-dashboard`）
- Modify: `patra-infra/docker/.env`、`.env.common`、`.env.example`（RocketMQ 变量）、`patra-infra/scripts/init-volumes.sh`（rocketmq 目录）
- Modify: `gradle/libs.versions.toml`（`rocketmq`、`rocketmq-spring-boot` 版本与库条目；第 71 行起「经 rocketmq-common 传递引入」那组按 Step 2 的结果处理）
- Modify: `patra-api/patra-catalog/patra-catalog-infra/build.gradle.kts`（第 30–31 行）、`patra-api/patra-catalog/patra-catalog-adapter/build.gradle.kts`（第 17 行）
- Modify: `build-logic/src/main/kotlin/PatraDependencyManagement.kt`（第 70 行 grpc / protobuf 约束、第 95–97 行 lz4 规则）；可能 Delete `build-logic/src/main/kotlin/Lz4ForkRule.kt`
- Modify: `.github/workflows/ci.yml`（第 25 行 `ROCKETMQ_IMAGE`；第 87–90 行 preflight 里 rocketmq compose 的镜像一致性检查；第 149 行 `docker pull "$ROCKETMQ_IMAGE"`）
- Modify: `patra-tool/patra-tool-test/src/main/java/dev/patra/tool/test/container/ContainerType.java`（删 `ROCKETMQ`）
- Modify: `PublicationAggregate.java`（第 33、256 行注释）、`PatraCatalogApplication.java`（第 26 行注释）
- Regenerate: `patra-infra/cd/module-graph.json`

- [ ] **Step 1：删 compose 服务、环境变量、目录初始化、catalog 的两处依赖、CI 三处、`ContainerType.ROCKETMQ`、catalog 的两处过期注释。**
- [ ] **Step 2：逐条核实依赖补丁还要不要**。先只删 rocketmq 依赖本身，然后对 5 个 boot 模块看运行时类路径：

Run（每个 boot 模块各跑一次：`patra-registry:patra-registry-boot`、`patra-catalog:patra-catalog-boot`、`patra-identity:patra-identity-boot`、`patra-interaction:patra-interaction-boot`、`patra-gateway-boot`）：
`./gradlew :patra-api:<模块>:dependencies --configuration runtimeClasspath | grep -E 'org\.lz4|at\.yawk|io\.grpc|com\.google\.protobuf'`
Expected: 某一组在 5 个模块里都没有命中，就删掉它的补丁（`Lz4ForkRule` + `lz4` 版本，或 grpc / protobuf 约束与版本）；仍有命中的保留，把注释里的「rocketmq-common 传递引入」改成真实来源。

- [ ] **Step 3：验证**

Run: `./gradlew dumpModuleGraph -q && ./gradlew -p build-logic test && ./gradlew spotlessApply compileJava compileTestJava :patra-tool:patra-tool-test:check :patra-api:patra-catalog:check`
Expected: BUILD SUCCESSFUL

Run: `docker compose -f patra-infra/docker/docker-compose.jobs.yaml config -q`（本机有 Docker 时）
Expected: 无输出、退出码 0

- [ ] **Step 4：提交**

```bash
git add -A patra-infra/docker patra-infra/scripts gradle/libs.versions.toml patra-api/patra-catalog build-logic .github/workflows/ci.yml patra-tool/patra-tool-test patra-infra/cd/module-graph.json
git diff --cached --stat
git commit -m "chore(infra): 下线 RocketMQ 及只为它存在的依赖补丁 (PAP-136)"
```

---

### Task 6：部署配置

**Files:**
- Modify: `patra-infra/docker/docker-compose.apps.yaml`（删 `object-storage`、`ingest` 服务）
- Modify: `patra-infra/docker/docker-compose.storage.yaml`（第 46–63 行：删 `patra-ingest-cache` 桶的创建，提示语改成只剩 identity 的桶）
- Modify: `patra-infra/docker/postgres/init-scripts/02-create-databases.sql`（删 `patra_ingest`、`patra_storage`）
- Delete: `patra-infra/nacos/dev/patra-ingest.yaml`、`patra-infra/nacos/dev/patra-object-storage.yaml`
- Modify: `patra-infra/scripts/nacos-verify.sh`（第 29 行 `SERVICES`）、`patra-infra/docker/service.Dockerfile`（第 2 行注释）
- Modify: `.mcp.json`（删 `patra-pg-ingest`、`patra-pg-storage`）

- [ ] **Step 1：按上面的清单改。**
- [ ] **Step 2：验证**

Run: `docker compose -f patra-infra/docker/docker-compose.apps.yaml config -q && docker compose -f patra-infra/docker/docker-compose.storage.yaml config -q`（本机有 Docker 时；apps 引用的 env 文件缺失导致报错时，确认报错与本次改动无关即可）
Expected: 无输出、退出码 0

Run: `python3 -c "import json;json.load(open('.mcp.json'))"`
Expected: 无输出（JSON 合法）

Run: `./gradlew :patra-tool:patra-tool-launch:test`（`NacosConfigFilesTest` 会扫 `nacos/dev/` 下的真实文件）
Expected: BUILD SUCCESSFUL

- [ ] **Step 3：提交**

```bash
git add -A patra-infra/docker patra-infra/nacos patra-infra/scripts .mcp.json
git diff --cached --stat
git commit -m "chore(infra): 删除 ingest 与 object-storage 的部署配置 (PAP-136)"
```

---

### Task 7：文档、规则、技能、学习站与测试样例

规则见 spec §3.8：历史文档目录不改，其余文件做到 Task 8 的零命中；描述当前系统的按新现实改，示例名换成在用服务，带日期的旧记录改写成不含已删名称的说法，已不成立的删掉。

**Files:**
- Modify: `CLAUDE.md`（产品定义一句：来源改为 PubMed、MeSH、ROR、PMC 开放获取、OpenAlex、Scopus、LetPub）
- Modify: `.claude/rules/project-info.md`、`.claude/rules/tech/package-layout.md`、`.claude/rules/tech/error-handling.md`、`.claude/rules/testing/conventions.md`、`.claude/rules/layers/infrastructure.md`
- Modify: `.claude/skills/patra-backend/skills/patra-hexagonal/SKILL.md`、`resources/configuration.md`、`resources/patra-starters.md`；`patra-troubleshooter/references/log-config.md`；`.claude/skills/patra/skills/release-planning/SKILL.md`；`.claude/skills/patra-backend/agents/test-checker.md`
- Modify: `.claude/skills/patra-backend/skills/patra-events/SKILL.md`（frontmatter 描述与正文删「Outbox + RocketMQ」相关内容，保留进程内领域事件）；Delete: `resources/outbox-guide.md`
- Modify: `README.md`、`patra-api/README.md`、`patra-api/patra-catalog/README.md`（第 20 行）、`patra-api/patra-gateway-boot/README.md`、`patra-api/patra-identity/README.md`（只改指 object-storage 服务的地方）
- Modify: `patra-infra/CLAUDE.md`、`patra-infra/docker/README.md`、`patra-infra/nacos/dev/README.md`、`patra-infra/docs/mac-mini-connectivity.md`
- Modify: `.github/git-commit-instructions.md`（`ingest` 范围的示例换成 `catalog`；范围表删 `ingest` 行）
- Modify（样例服务名 `patra-ingest` → `patra-catalog`，测试意图不变）：`patra-tool/patra-tool-launch/src/test/java/dev/patra/tool/launch/NacosConfigFilesTest.java`、`ConfigSourceCheckPostProcessorTest.java`、`JarConfigOverlapCheckerTest.java`、`src/integrationTest/java/dev/patra/tool/launch/NacosPushScriptIT.java`；`patra-tool/patra-tool-web/src/main/java/dev/patra/tool/web/error/spi/ProblemFieldContributor.java` 与同包 `README.md`；`patra-tool/patra-tool-test/src/main/java/dev/patra/tool/test/archunit/HexagonalArchitectureRules.java`（Javadoc）
- Modify: `patra-learn/src/content/lines.ts`（删 `ingest-pipeline` 站点）、`articles/l2/deploy-loop.tsx`、`articles/l1/parallel-exams.tsx`

- [ ] **Step 1：按清单改。** `NacosPushScriptIT` 里两个样例文件原是 `patra-common.yaml` 与 `patra-ingest.yaml`，换名时别和 `patra-common.yaml` 撞名。`patra-learn` 改完若有测试断言站点数量，同步改期望值。
- [ ] **Step 2：验证**

Run: `./gradlew spotlessApply :patra-tool:patra-tool-launch:check :patra-tool:patra-tool-launch:integrationTest :patra-tool:patra-tool-web:check :patra-tool:patra-tool-test:check`
Expected: BUILD SUCCESSFUL

Run（在 `patra-learn/` 下，用它自己的 pnpm）：`pnpm lint && pnpm typecheck && pnpm test && pnpm build`
Expected: 全部通过

- [ ] **Step 3：提交**

```bash
git add -A CLAUDE.md .claude/rules .claude/skills README.md patra-api patra-infra .github/git-commit-instructions.md patra-tool patra-learn
git diff --cached --stat
git commit -m "docs: 删除 ingest、object-storage、RocketMQ 的文档与示例引用 (PAP-136)"
```

---

### Task 8：全量验证、引用检查、开 PR

- [ ] **Step 1：引用检查**（spec §5，排除历史文档）

四条命令末尾都带同一组排除：`-- . ':!docs/patra/specs' ':!docs/patra/plans' ':!docs/patra/release-specs' ':!docs/patra/runbooks' ':!patra-portal/docs' ':!.github/CI_OPTIMIZATION.md'`（直接写在命令里，不用变量展开，工作树隔离会拒绝带变量的 git 调用）。

```bash
git grep -niw ingest -- . ':!docs/patra/specs' ':!docs/patra/plans' ':!docs/patra/release-specs' ':!docs/patra/runbooks' ':!patra-portal/docs' ':!.github/CI_OPTIMIZATION.md'
git grep -niE 'patra_ingest|patraingest' -- . ':!docs/patra/specs' ':!docs/patra/plans' ':!docs/patra/release-specs' ':!docs/patra/runbooks' ':!patra-portal/docs' ':!.github/CI_OPTIMIZATION.md'
git grep -ni rocketmq -- . ':!docs/patra/specs' ':!docs/patra/plans' ':!docs/patra/release-specs' ':!docs/patra/runbooks' ':!patra-portal/docs' ':!.github/CI_OPTIMIZATION.md'
git grep -niE 'patra-object-storage|patraobjectstorage|dev\.patra\.objectstorage|patra_storage|:patra-api:patra-common|dev\.patra\.common\.' -- . ':!docs/patra/specs' ':!docs/patra/plans' ':!docs/patra/release-specs' ':!docs/patra/runbooks' ':!patra-portal/docs' ':!.github/CI_OPTIMIZATION.md'
```

Expected: 四条都无输出。有命中就回到所属任务补改，再提交。

- [ ] **Step 2：全量构建**

Run: `./gradlew spotlessApply && ./gradlew check`，再跑全部 `integrationTest` 与 `e2eTest`（`./gradlew integrationTest e2eTest`）
Expected: BUILD SUCCESSFUL

Run: `./gradlew -p build-logic test && bash patra-infra/cd/detect-changes.test.sh && bash patra-infra/cd/deploy.test.sh`
Expected: 全部通过

Run（在 `patra-learn/` 下）：`pnpm lint && pnpm typecheck && pnpm test && pnpm build`
Expected: 全部通过

- [ ] **Step 3：请用户在本地验收**（记忆：改动做完先停在本地，确认后才开 PR）。给出改动摘要与 Step 1、2 的结果。
- [ ] **Step 4：用户同意后推送并开 draft PR**：标题 `refactor: 删除 ingest 及只为它存在的模块与基础设施 (PAP-136)`；描述逐行写 `Closes PAP-136`，列出 spec §4 的 mini 运维步骤，结尾带 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`。开 PR 后用 ccd_pr 工具绑定 PR 并开监控。CI 绿后转 ready：Claude Action 自动首评，再在评论区发 `@coderabbitai review`；每条评审意见在 PR 里回复「已修复（附 SHA）」或「不修复（附理由）」。

---

### Task 9：合并与 mini 运维（spec §4，每一步执行前都要用户授权）

- [ ] **Step 1：用户授权合并后、合并前**：避开 catalog 导入时段，在 mini 上依次执行 `docker stop patra-registry` → `DROP DATABASE patra_registry;` → `CREATE DATABASE patra_registry;`。
- [ ] **Step 2：合并 PR**，等 CD 跑完。

Expected: CD 成功；`docker logs patra-registry` 里 Flyway 执行了 V1–V3，服务健康。

- [ ] **Step 3：清理**：`docker rm -f patra-ingest patra-object-storage patra-rocketmq-namesrv patra-rocketmq-broker patra-rocketmq-dashboard`；`DROP DATABASE patra_ingest;`、`DROP DATABASE patra_storage;`；删 MinIO 桶 `patra-ingest-cache`；确认 Nacos 上已没有 `patra-ingest.yaml`、`patra-object-storage.yaml`（没删就手动删）；检查 xxl-job-admin 里有没有 ingest 的执行器或任务，有则删。
- [ ] **Step 4：验收**：`docker ps` 里没有已删容器；字典接口能返回国家和语言；按 `country / NAME_EN` 解析 `China` 得到 `CN`；门户检索、文献详情（含全文）、头像上传正常。
- [ ] **Step 5：问用户要不要做 spec §4 第 8 步的可选清理**（GHCR 镜像包、两台机器上的旧 env 文件、`~/.patra/docker/rocketmq`）。
- [ ] **Step 6：收尾**：PAP-136 由 `Closes` 自动关闭；更新记忆 `project_pap136_remove_ingest.md`（已完成）与 `project_direction_rethink_2026_10.md`；提醒 v0.16 规划会话：#6 取消、#2 缩成「接入 RabbitMQ」，后端分支可以从 main 切了。
