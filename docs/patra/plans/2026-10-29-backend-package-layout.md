# 后端分包规范 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 catalog、identity、interaction、object-storage、registry 五个服务迁到「层模块内子域优先」的统一分包模板，并由共享 ArchUnit 规则在 `./gradlew check` 里强制。

**Architecture:** 第 0 步在 `refactor/package-layout` 上串行：先改 `patra-tool-test` 的共享规则（TDD），再修规则文件，再统一改各服务 api 包名及其全部调用方，再迁 interaction（改动小）。第 1 步按服务并行，每个服务一个 worktree 会话：先做行为不变的实现改动（TDD，在旧包里做），再整体搬包，最后接入完整架构测试。第 2 步合并、全量门控、更新文档、开 PR。

**Tech Stack:** Java 25、Spring Boot 4、Gradle 9（Kotlin DSL）、ArchUnit、JUnit 5 + AssertJ、Testcontainers PG17、Spring `JdbcClient`。

**Spec:** `docs/patra/specs/2026-10-29-backend-package-layout-design.md`，映射附录 `docs/patra/specs/2026-10-29-backend-package-layout-mapping.md`（附录与 spec 冲突时以 spec §8 为准）。

## Global Constraints

- 不改 HTTP 路径、`/_internal` 契约路径、数据库 schema、Flyway 脚本、配置键、指标名。
- 不改业务类名；不删除现有类（spec §8 明确列出的除外：ingest 不迁，故本计划没有删除项）。
- ingest（含 `patra-ingest-expr`、`patra-ingest-provenance`）只改指向 registry / object-storage api 的 import 与字符串全类名，以及 `IngestArchitectureTest` 对改名规则方法的调用。
- 底座对同服务其他子域完全公开；只放被两个以上子域共用的东西。普通子域只公开 `domain.{子域}.{port.read, model.read, event}`。
- `{根}.api.error` 是保留包；`error` 不能作子域名。
- 允许的实现改动只有 spec §8：registry 的 `ProvenanceReadPort`；identity 拆出 `LogoutController`；catalog 的 portal 5 个读适配器改 `JdbcClient`、fulltext 写 publication 改同步事件、author / mesh / organization 三个新读端口、`XmlParsingHelper` 常量内移。全部行为不变。
- 搬包遇到附录遗漏：被底座和至少一个其他子域共用的类型移入底座；只被一个子域用的归那个子域；需要改代码才能化解的，停下交用户决定（spec §10.5）。
- 不用 `FreezingArchRule`、`@Disabled`、少接规则来让 check 变绿。
- 改了 `*.gradle.kts` 要提醒用户在 IDEA 里点「加载 Gradle 更改」。
- 提交信息中文、subject 以中文动词开头（commitlint 拒绝大写开头）；结尾带 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。

## Review Focus

1. **编译查不出的旧包名字面量**：`@ConditionalOnClass(name=…)`、`@ComponentScan(basePackages=…)`、`spotbugs-exclude.xml` 精确全类名、`spring.factories` / `*.imports`、`logging.level.dev.patra…`、`.run/*.run.xml`。搬完后自动配置静默失效、日志级别失效，测试却全绿。每个搬包任务的最后一步都是 §「旧包字面量门控」。
2. **portal 改写后的 SQL 结果与原来不一致**（列别名、排序、空值、分页边界）：Task 18 先补 IT 锁住现状，再改写。
3. **事件改同步监听后不在同一事务**：`PmcFullTextFetchPersister.recordFetched` 是 `REQUIRES_NEW`，监听必须用同步 `@EventListener`（不是 `@TransactionalEventListener`），Task 19 的测试断言监听抛异常时整体回滚。
4. **底座混入只被一个子域用的类**：每个服务的架构测试之外，搬包任务的自检一步要列出底座里每个类的使用子域数。
5. **测试被搬丢**：每个服务搬包前后记录 `test` / `integrationTest` 用例数，必须一致（新增测试除外）。

---

## 第 0 步：公共部分（`refactor/package-layout`，串行，本会话内联执行）

### 文件索引

| 文件 | 职责 |
|---|---|
| `patra-tool/patra-tool-test/src/main/java/dev/patra/tool/test/archunit/SubdomainArchitectureRules.java` | 子域规则：底座完全公开、保留包、模板白名单、infra 与端口同名、子域至少有 domain 包 |
| `patra-tool/patra-tool-test/src/main/java/dev/patra/tool/test/archunit/HexagonalArchitectureRules.java` | 实体规则改按 `@Entity` 注解；删 Orchestrator 规则 |
| `patra-tool/patra-tool-test/src/test/java/dev/patra/tool/test/archunit/SubdomainArchitectureRulesTest.java` + `fixtures/subdomain/<场景>/…` | 场景夹具与断言 |
| `patra-tool/patra-tool-test/src/test/java/dev/patra/tool/test/archunit/HexagonalArchitectureRulesTest.java` + `fixtures/subdomainlayout/…` | 同上 |
| `.claude/rules/layers/*.md`、`.claude/rules/tech/*.md` | `paths:` 修复 |
| `.claude/rules/tech/package-layout.md` | 新规则文件 |

### Task 1：底座完全公开

**Files:**
- Modify: `SubdomainArchitectureRules.java`（删 `FOUNDATION_PUBLISHED`，改 `isPublished`、类与方法注释）
- Modify: `SubdomainArchitectureRulesTest.java`
- Modify fixtures: `fixtures/subdomain/foreignrepository/**`、`fixtures/subdomain/foreignapp/**`
- Create fixtures: `fixtures/subdomain/foundationinternals/{domain/subject/port/repository/SubjectRepository.java, app/subject/SubjectInternal.java, infra/subject/support/SubjectSqlFragments.java, app/collection/UsesFoundationInternals.java}`

**Interfaces:**
- Produces: `isPublished(subdomain, packageName)` 对底座恒为真；公开 API 签名不变。

- [ ] **Step 1：改写两个场景夹具，让「外部仓储 / 外部 app 类」指向非底座子域**
  - `foreignrepository`：`domain/note/port/repository/NoteRepository` + `app/collection/UsesNoteRepository`（删原 `SubjectRepository`、`UsesSubjectRepository`）。
  - `foreignapp`：`app/note/ResolveNoteHandler` + `adapter/collection/UsesNoteHandler`。
  - 对应测试断言的类名改为 `NoteRepository`、`ResolveNoteHandler`。

- [ ] **Step 2：写失败测试**

```java
/// 底座对其他子域完全公开：仓储、app 内部类、infra 辅助类都可以用。
@Test
void should_allow_any_foundation_package() {
  assertThat(
          evaluate("foundationinternals",
                  rules("foundationinternals").crossSubdomainDependenciesGoThroughPublishedPackages())
              .hasViolation())
      .isFalse();
}
```

- [ ] **Step 3：运行，确认失败**

Run: `./gradlew :patra-tool-test:test --tests "*SubdomainArchitectureRulesTest*"`
Expected: `should_allow_any_foundation_package` FAIL（违规点名 `SubjectRepository`），其余通过。

- [ ] **Step 4：实现**：`isPublished` 中 `subdomain.equals(foundationSubdomain)` 时直接返回 `true`；删除 `FOUNDATION_PUBLISHED`；类注释与 `crossSubdomainDependenciesGoThroughPublishedPackages` 的 `.as(...)` 文案改为「跨子域只经对方公开包（port.read、model.read、event）；底座完全公开」。

- [ ] **Step 5：运行全部子域规则测试，确认通过**（同 Step 3 命令，全 PASS）

- [ ] **Step 6：提交** `feat(tool-test): 底座子域对其他子域完全公开`

### Task 2：保留包 `api.error`

**Files:** `SubdomainArchitectureRules.java`；测试；Create fixtures `fixtures/subdomain/reservederror/{api/error/SampleErrorCode.java, app/collection/UsesErrorCode.java, domain/collection/model/vo/CollectionId.java}`

**Interfaces:** Produces: 构造器对 `subdomains` 含 `"error"` 抛 `IllegalArgumentException`。

- [ ] **Step 1：写失败测试**

```java
@Test
void should_accept_reserved_api_error_package() {
  assertThat(evaluate("reservederror",
          rules("reservederror").layersContainOnlyDeclaredSubdomains()).hasViolation()).isFalse();
}

@Test
void should_reject_error_as_subdomain_name() {
  assertThatThrownBy(() -> new SubdomainArchitectureRules("x", "error", Set.of("error")))
      .isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 2：运行确认两条 FAIL**（命令同 Task 1）
- [ ] **Step 3：实现**：私有常量 `RESERVED = "api.error"`；`resideInLayer()` 谓词排除包名等于或以 `{根}.api.error.` 开头的类；构造器校验 `!subdomains.contains("error")`。
- [ ] **Step 4：运行确认通过**
- [ ] **Step 5：提交** `feat(tool-test): 子域规则放行服务级错误码保留包`

### Task 3：组件包白名单 `componentPackagesFollowTemplate`

**Files:** `SubdomainArchitectureRules.java`；测试；Create fixtures `fixtures/subdomain/templatecompliant/**`、`fixtures/subdomain/offtemplate/**`

**Interfaces:** Produces: `public ArchRule componentPackagesFollowTemplate()`。

白名单（相对 `{根}.{层}.{子域}`，`*` = 恰好一段自定名，`**` = 任意深度）：

| 层 | 允许 |
|---|---|
| domain | `model.aggregate`、`model.entity`、`model.vo`、`model.vo.**`、`model.enums`、`model.read`、`policy`、`service`、`event`、`exception`、`port.*` |
| app | `usecase.*`、`service`、`listener`、`config` |
| infra | `persistence`、`persistence.entity`、`persistence.dao`、`persistence.mapper`、`persistence.converter`、`support`、`config`、`*`、`*.**`（`*` 是否合法由 Task 4 管） |
| adapter | `rest`、`rest.request`、`rest.response`、`rest.converter`、`internal`、`scheduler`、`mq`、`config` |
| api | `endpoint`、`dto` |

子域根包本身不能直接放类。`api.error` 不受此规则管。

- [ ] **Step 1：建夹具**
  - `templatecompliant`：上表每层各至少一个合法包放一个空类（如 `app/collection/usecase/collect/CollectItemHandler`、`adapter/collection/rest/converter/CollectionApiConverter`、`domain/collection/model/vo/body/Paragraph`）。
  - `offtemplate`：`app/collection/dto/CollectionDto`、`domain/collection/model/snapshot/Snapshot`、`adapter/collection/web/WebController`、`infra/collection/persistence/repo/RepoHelper`、`domain/collection/RootLevel`。
- [ ] **Step 2：写失败测试**

```java
@Test
void should_accept_packages_on_template() {
  assertThat(evaluate("templatecompliant",
          rules("templatecompliant").componentPackagesFollowTemplate()).hasViolation()).isFalse();
}

@Test
void should_reject_packages_off_template() {
  EvaluationResult result = evaluate("offtemplate", rules("offtemplate").componentPackagesFollowTemplate());
  assertThat(result.hasViolation()).isTrue();
  assertThat(result.getFailureReport().getDetails())
      .anyMatch(l -> l.contains("CollectionDto")).anyMatch(l -> l.contains("Snapshot"))
      .anyMatch(l -> l.contains("WebController")).anyMatch(l -> l.contains("RepoHelper"))
      .anyMatch(l -> l.contains("RootLevel"));
}
```

- [ ] **Step 3：运行确认 FAIL（方法不存在，编译失败）**
- [ ] **Step 4：实现** `componentPackagesFollowTemplate()`：对 `resideInSomeSubdomain()` 的类取子域后的相对包，按上表做前缀匹配；不匹配时 `violated(javaClass, 类名 + " 的包 " + 相对包 + " 不在分包模板里")`。`.because("分包模板见 .claude/rules/tech/package-layout.md")`，`allowEmptyShould(true)`。
- [ ] **Step 5：运行确认通过**
- [ ] **Step 6：提交** `feat(tool-test): 子域内组件包按模板白名单检查`

### Task 4：infra 包与端口同名 `infraPackagesMatchPorts`

**Files:** 同上；Create fixtures `fixtures/subdomain/infraports/{domain/collection/port/mail/MailPort.java, infra/collection/mail/MailAdapter.java, infra/collection/smtp/SmtpAdapter.java, infra/collection/support/SqlFragments.java, infra/collection/persistence/CollectionRepositoryAdapter.java}`

**Interfaces:** Produces: `public ArchRule infraPackagesMatchPorts()`。

- [ ] **Step 1：写失败测试**：`should_reject_infra_package_without_matching_port` 断言违规点名 `SmtpAdapter`，且详情中没有 `MailAdapter`、`SqlFragments`、`CollectionRepositoryAdapter`。
- [ ] **Step 2：运行确认 FAIL**
- [ ] **Step 3：实现**：infra 层子域下第一段 X ∉ {`persistence`, `support`, `config`} 时，要求导入的类里存在包名为 `{根}.domain.{子域}.port.X` 或其子包的类；用 `ArchCondition` 的 `init(Collection<JavaClass>)` 先收集 domain 端口包集合。
- [ ] **Step 4：运行确认通过**
- [ ] **Step 5：提交** `feat(tool-test): infra 包名须与本子域端口包同名`

### Task 5：每个声明的子域都有 domain 包 `everyDeclaredSubdomainHasDomainPackage`，`allRules()` 变 8 条

**Files:** 同上；Create fixtures `fixtures/subdomain/ghost/domain/subject/model/vo/SubjectId.java`（只有 subject）

**Interfaces:** Produces: `public ArchRule everyDeclaredSubdomainHasDomainPackage()`；`allRules()` 返回 8 条（原 5 条 + Task 3、4、5）。

- [ ] **Step 1：写失败测试**

```java
@Test
void should_reject_declared_subdomain_without_domain_classes() {
  SubdomainArchitectureRules rules =
      new SubdomainArchitectureRules(FIXTURES + "ghost", "subject", Set.of("subject", "ghostly"));
  assertViolationNaming("ghost", rules.everyDeclaredSubdomainHasDomainPackage(), "ghostly");
}
```

  并把 `should_list_all_five_rules` 改名 `should_list_all_eight_rules`，断言 `hasSize(8)`。
- [ ] **Step 2：运行确认 FAIL**
- [ ] **Step 3：实现**：`classes().that(resideInLayer()).should(...)`，条件在 `init` 里统计 domain 层出现的子域集合，在 `finish(ConditionEvents)` 里为每个缺失的声明子域加一条 `violated`（文案含子域名）。`allowEmptyShould(true)`。
- [ ] **Step 4：运行全部 patra-tool-test 测试确认通过**：`./gradlew :patra-tool-test:test`
- [ ] **Step 5：提交** `feat(tool-test): 声明的子域必须在 domain 层出现`

### Task 6：`HexagonalArchitectureRules` 实体规则按 `@Entity`、删 Orchestrator 规则

**Files:**
- Modify: `HexagonalArchitectureRules.java`（`doClassesShouldResideInPersistenceEntity` → `jpaEntitiesShouldResideInPersistenceEntity`，`dataObjectsShouldNotBeAccessedOutsideInfra` → `jpaEntitiesShouldNotBeAccessedOutsideInfra`，删 `orchestratorsShouldResideInAppUsecase`，`allRules()` 同步）
- Modify: `HexagonalArchitectureRulesTest.java`；fixture `fixtures/subdomainlayout/infra/collection/persistence/entity/FooDO.java` → `FooEntity.java`（带 `@jakarta.persistence.Entity`）；删 `fixtures/subdomainlayout/app/collection/usecase/FooOrchestrator.java`；`fixtures/misplaced` 增加一个放错位置的 `@Entity` 类 `domain/collection/MisplacedEntity.java` 和一个不带注解、名叫 `ResponseEntityLike` 的类
- Modify callers: `patra-api/patra-{catalog,interaction,ingest}/…-boot/src/test/java/…/architecture/*ArchitectureTest.java`（改调用新方法名，删 orchestrator 用例）

**Interfaces:** Produces: `public ArchRule jpaEntitiesShouldResideInPersistenceEntity()`、`public ArchRule jpaEntitiesShouldNotBeAccessedOutsideInfra()`。

- [ ] **Step 1：改测试**：`should_accept_data_object_under_subdomain_persistence_entity` → `should_accept_jpa_entity_under_subdomain_persistence_entity`；新增 `should_reject_misplaced_jpa_entity`（点名 `MisplacedEntity`，详情不含 `ResponseEntityLike`）；删 `should_accept_orchestrator_under_subdomain_usecase`。
- [ ] **Step 2：运行确认 FAIL（编译失败）**：`./gradlew :patra-tool-test:test --tests "*HexagonalArchitectureRulesTest*"`
- [ ] **Step 3：实现**：两条规则的选择器改为 `areAnnotatedWith("jakarta.persistence.Entity")` 且 `resideInAPackage(basePackage + "..")`；删 orchestrator 规则。
- [ ] **Step 4：改三个服务架构测试的调用**（catalog、interaction、ingest），删各自的 orchestrator 用例。
- [ ] **Step 5：运行**：`./gradlew :patra-tool-test:test :patra-catalog-boot:test --tests "*ArchitectureTest*" :patra-interaction-boot:test --tests "*ArchitectureTest*" :patra-ingest-boot:test --tests "*ArchitectureTest*"`
  Expected: 全 PASS（catalog / ingest 的实体都已在 `..infra..persistence.entity..`）。若 interaction 因 Task 3–5 新规则失败，属预期，留给 Task 12；本任务只要求 interaction 测试**编译通过**且原有用例通过——Task 12 前不把新规则接进 interaction 测试。
- [ ] **Step 6：提交** `refactor(tool-test): 实体规则改按 JPA 注解匹配并删除空转的编排器规则`

### Task 7：规则文件 `paths:` 修复与 `package-layout.md`

**Files:**
- Modify `paths:`：`.claude/rules/layers/{adapter,api,application,boot,domain,infrastructure}.md`、`.claude/rules/tech/{commandbus,jpa,observability}.md`。写法统一为 `patra-api/*/patra-*-<层>/**/*.java`（commandbus 两段、jpa 两段、observability 三段照原意改）。
- Create: `.claude/rules/tech/package-layout.md`（`paths: patra-api/**/*.java`），内容只含 spec §3 模板代码块与要点、§4.2 底座三条、§4.3 四条、§4.4 一句、§5 用词表。不写推导。

- [ ] **Step 1：改 `paths:` 与新建文件**
- [ ] **Step 2：实测注入**：在本会话 Read `patra-api/patra-interaction/patra-interaction-app/src/main/java/dev/patra/interaction/app/subject/usecase/reconcile/ReconcileSubjectsHandler.java`（前 5 行），确认 system-reminder 注入了 `layers/application.md` 与 `tech/package-layout.md`；再 Read 一个 infra 文件确认 `layers/infrastructure.md` 注入。若未注入，换写法（如 `patra-api/**/*-app/**/*.java`）重试。
- [ ] **Step 3：提交** `docs(rules): 修复分层规则的路径匹配并新增分包规范`

### Task 8：registry api 改包名 + 调用方

**Files:**
- Move: `patra-api/patra-registry/patra-registry-api/src/main/java/dev/patra/registry/api/endpoint/{Dictionary,Expr,Provenance}Endpoint.java` → `api/{dictionary,expr,provenance}/endpoint/`；`api/dto/dict/*` → `api/dictionary/dto/`；`api/dto/{expr,provenance}/*` → `api/{expr,provenance}/dto/`。`api/error` 不动。
- Modify callers：catalog 4 个文件、ingest 8 个文件（附录 §2 列表），以及 `patra-ingest-expr/.../ExprCompilerAutoConfiguration.java:89-90` 的 `@ConditionalOnClass(name=…)` 字符串；2 处通配 import `dev.patra.registry.api.dto.provenance.*`。
- Modify：registry 自身 adapter 的 import。

搬包做法（第 0 步与第 1 步通用）：用 `git mv` 移文件，用 `sed` 改 `package` 声明与全仓 `import`，脚本放会话 scratchpad，不进仓库。主检出由 IDEA 打开时，可改用 JetBrains MCP 的 move / rename 重构（先 ToolSearch 加载）。

- [ ] **Step 1：搬文件、改 package 与 import、改字符串全类名**
- [ ] **Step 2：编译**：`./gradlew compileJava compileTestJava compileIntegrationTestJava`，Expected: BUILD SUCCESSFUL
- [ ] **Step 3：旧包字面量门控**：`rg -n 'dev\.patra\.registry\.api\.(endpoint|dto)' patra-api patra-tool spotbugs-exclude.xml .run` Expected: 无输出
- [ ] **Step 4：测试**：`./gradlew :patra-registry-adapter:integrationTest :patra-catalog-infra:test :patra-ingest-expr:test :patra-ingest-infra:test` Expected: PASS（`ExprCompilerAutoConfiguration` 的条件生效由 ingest-expr 现有自动配置测试覆盖；若无此测试，先补一个断言 `ExprCompiler` bean 存在的 `ApplicationContextRunner` 测试再改名）
- [ ] **Step 5：提交** `refactor(registry): api 包按子域划分`

### Task 9：object-storage api 改包名 + 调用方

**Files:** `api/endpoint/StorageEndpoint` → `api/file/endpoint/`；`api/dto/*` → `api/file/dto/`；ingest 3 个调用方（附录 §1）；object-storage adapter 的 import。

- [ ] **Step 1：搬与改**
- [ ] **Step 2：编译**（同 Task 8）
- [ ] **Step 3：门控**：`rg -n 'dev\.patra\.objectstorage\.api\.(endpoint|dto)' patra-api spotbugs-exclude.xml .run` 无输出
- [ ] **Step 4：测试**：`./gradlew :patra-object-storage-adapter:integrationTest :patra-ingest-infra:test`
- [ ] **Step 5：提交** `refactor(object-storage): api 包归入 file 子域`

### Task 10：catalog api 改包名 + 调用方

**Files:** `api/endpoint/{Mesh,Organization,Publication,Venue}Endpoint` → `api/{mesh,organization,publication,venue}/endpoint/`；`api/dto/{mesh,organization,publication,venue}` → `api/{同名}/dto/`；`api/error` 不动；identity 4 个、interaction 8 个调用方（含 `CatalogClientConfiguration`）；catalog adapter 的 import。

- [ ] **Step 1–5**：同 Task 9 的五步；门控 `rg -n 'dev\.patra\.catalog\.api\.(endpoint|dto)'`；测试 `:patra-catalog-adapter:integrationTest :patra-identity-infra:test :patra-interaction-infra:test`；提交 `refactor(catalog): api 包按子域划分`

### Task 11：identity api 改包名 + 调用方

**Files:** `api/endpoint/UserEndpoint` + `api/dto/{User*,AccountStatus}` → `api/account/{endpoint,dto}/`；`api/endpoint/SessionEndpoint` + `SessionIntrospection{Req,Resp}` → `api/session/{endpoint,dto}/`；interaction 6 个、gateway-boot 4 个调用方。

- [ ] **Step 1–5**：同上；门控 `rg -n 'dev\.patra\.identity\.api\.(endpoint|dto)'`；测试 `:patra-identity-adapter:integrationTest :patra-interaction-infra:test :patra-gateway-boot:test`；提交 `refactor(identity): api 包按子域划分`

### Task 12：interaction 迁移

**Files:**
- Move: `app/notification/eventhandler/*` → `app/notification/listener/`；`domain/subject/model/user/*` → `domain/subject/model/read/`；`domain/subject/model/source/*` → `domain/subject/model/vo/`（测试镜像）。
- Modify: `InteractionArchitectureTest.java`：删自带的「一层出现则四层都要有」用例，改为执行 `subdomainRules.allRules()`（8 条）；`patra-interaction/README.md` §2、§3：子域内部分包引用 `.claude/rules/tech/package-layout.md`，底座公开范围改为「完全公开」。

- [ ] **Step 1：搬包**
- [ ] **Step 2：运行架构测试**：`./gradlew :patra-interaction-boot:test --tests "*InteractionArchitectureTest*"` Expected: PASS；若白名单仍报违规，按 Global Constraints 的遗漏规则处理
- [ ] **Step 3：门控**：`rg -n 'interaction\.app\.notification\.eventhandler|interaction\.domain\.subject\.model\.(user|source)' patra-api spotbugs-exclude.xml .run` 无输出
- [ ] **Step 4：`./gradlew :patra-interaction:check`**（含 integrationTest）PASS
- [ ] **Step 5：提交** `refactor(interaction): 按分包模板调整监听与底座模型包`

### Task 13：试跑完整六边形规则（identity、registry、object-storage）

**Files:** `patra-api/patra-{identity,registry,object-storage}/…-boot/src/test/java/…/architecture/*ArchitectureTest.java`

- [ ] **Step 1：在三个架构测试里加上 `HexagonalArchitectureRules` 与 `TestingRules` 的 `allRules()` 用例**（照 catalog 的写法，含排除 testFixtures 的导入过滤）
- [ ] **Step 2：运行** `./gradlew :patra-identity-boot:test :patra-registry-boot:test :patra-object-storage-boot:test --tests "*ArchitectureTest*"`
- [ ] **Step 3：把违规按「类放错包 / 命名不符」与「涉及行为」两类写进本计划末尾的「Task 13 结果」小节**。前一类留给各服务第 1 步的搬包任务顺手改；后一类停下交用户。用例保持接入、本步不提交红灯——若有违规，本 Task 的提交推迟到第 1 步对应服务的迁移任务里一起提交。

### Task 14：第 0 步门控与基线

- [ ] **Step 1：`./gradlew check`** Expected: BUILD SUCCESSFUL（Task 13 若有违规且未修，此步先暂时去掉 Task 13 的用例改动 `git stash`，基线不含它）
- [ ] **Step 2：记录各服务 `test` / `integrationTest` 用例数**到本计划末尾「用例数基线」小节（从 `build/test-results/**/TEST-*.xml` 汇总 `tests` 属性）
- [ ] **Step 3：提交** 未提交的改动；推送分支 `refactor/package-layout`（需用户同意）

---

## 第 1 步：各服务并行（每个服务一个 worktree 会话）

每个会话：从第 0 步基线提交切出 `refactor/package-layout-<服务>`，只改本服务目录与 `spotbugs-exclude.xml` 中本服务的行；做完合回 `refactor/package-layout`（由主会话合并）。同时跑全量 `check` 的会话不超过 3 个。开工顺序：catalog → identity → registry → object-storage。

每个服务的「搬包」任务都按同一套步骤：

1. 记录本服务搬包前用例数（应等于基线）。
2. 按附录映射 + spec §8 修正，`git mv` + `sed` 改 package / import；同一子域内同名冲突时停下报告。
3. `./gradlew :<服务>:compileJava compileTestJava compileIntegrationTestJava` 通过。
4. 架构测试接入 `SubdomainArchitectureRules`（子域、底座、边按 spec §8）+ 三组 `allRules()`，运行通过。
5. **底座自检**：列出底座每个类在其他子域的使用数，只被一个子域用的移回该子域。
6. **旧包字面量门控**：`rg -n '<旧包正则>' patra-api patra-tool spotbugs-exclude.xml .run` 无输出；同时检查本服务 `src/main/resources/**/*.{yml,yaml,xml,imports,factories}`。
7. `./gradlew :<服务>:check` 通过；用例数与基线一致（新增测试另计）。
8. 更新本服务 README 的分包说明。
9. 提交 `refactor(<服务>): 按子域重组分包`。

### Task 15：object-storage

- 子域 `file`（底座）；只搬包。架构测试：`new SubdomainArchitectureRules("dev.patra.objectstorage", "file", Set.of("file"))`。
- 额外门控：`spotbugs-exclude.xml:645` 的 `StorageEndpointImpl` 全类名改为 `dev.patra.objectstorage.adapter.file.internal.StorageEndpointImpl`。

### Task 16：registry

**16a ProvenanceReadPort（TDD，在旧包里做）**

**Files:**
- Create: `patra-registry-domain/src/main/java/dev/patra/registry/domain/port/ProvenanceReadPort.java`（搬包时移到 `domain.provenance.port.read`）
- Create: `patra-registry-infra/src/main/java/dev/patra/registry/infra/adapter/persistence/ProvenanceReadAdapter.java`
- Modify: `ExprRepositoryAdapter.java`（改注入 `ProvenanceReadPort`，不再用 `ProvenanceDao`、不再 new `ProvenanceNotFoundException`）
- Test: `patra-registry-infra/src/integrationTest/java/dev/patra/registry/infra/adapter/persistence/ProvenanceReadAdapterIT.java`

**Interfaces:** Produces: `long requireIdByCode(ProvenanceCode code)`——找不到时抛 `ProvenanceNotFoundException`（与 `ExprRepositoryAdapter` 现行为一致）。

- [ ] Step 1：写 IT `should_return_id_for_existing_code`、`should_throw_provenance_not_found_for_unknown_code`
- [ ] Step 2：运行 `./gradlew :patra-registry-infra:integrationTest --tests "*ProvenanceReadAdapterIT*"` 确认 FAIL
- [ ] Step 3：实现适配器（用 `ProvenanceDao` 现有按 code 查询）
- [ ] Step 4：确认 PASS；改 `ExprRepositoryAdapter`，运行 `ExprRepositoryAdapterIT` 仍 PASS
- [ ] Step 5：提交 `refactor(registry): 表达式经读端口解析数据源 ID`

**16b 搬包**：子域 `dictionary`、`provenance`、`expr`，底座 `kernel`，边 `allowingDependency("expr", "provenance")`。额外：4 个 infra IT 的 `@ComponentScan(basePackages=…converter.mapper)` 改为 `basePackageClasses = {各子域 *JpaMapper}.class`。

### Task 17：identity

**17a 拆出 LogoutController**

**Files:** Create `patra-identity-adapter/src/main/java/dev/patra/identity/adapter/rest/auth/LogoutController.java`（搬包时到 `adapter.session.rest`）；Modify `AuthController.java`（删 `logout` 方法）；Test: 现有覆盖 `POST /auth/logout` 的 IT（`rg -l '/auth/logout' patra-api/patra-identity/*/src/integrationTest`）随方法迁到 `LogoutControllerIT`。

- [ ] Step 1：先把 logout 相关 IT 用例移到 `LogoutControllerIT`（指向 `LogoutController`），运行确认 FAIL（类不存在）
- [ ] Step 2：创建 `LogoutController`：`@RestController @RequestMapping("/auth")`，方法签名、注解、返回值与原 `AuthController.logout` 逐字一致
- [ ] Step 3：删 `AuthController.logout`；运行 `./gradlew :patra-identity-adapter:integrationTest` PASS
- [ ] Step 4：提交 `refactor(identity): 登出接口拆为独立控制器`

**17b 搬包**：子域 `account`，底座 `session`（spec §8.4 列出的公共件全部进 session），无边。`XxlJobConfig` → `dev.patra.identity.config`（boot）。架构测试照 interaction 加 testFixtures 排除。

### Task 18：catalog portal 读适配器改 `JdbcClient`

**Files:**
- Modify: `patra-catalog-infra/src/main/java/dev/patra/catalog/infra/adapter/read/{PublicationSearch,PublicationDetail,VenueBrowse,VenueDetail,CitationRecord}ReadAdapter.java`
- Delete: `infra/persistence/dao/{PublicationSearchDao,PublicationDetailDao,CitationRecordDao}.java`（spec §8.6 列出的删除，属于改写的一部分）
- Modify: `infra/persistence/dao/VenueDao.java`（移出被上述适配器使用的门户查询方法）
- Modify: 19 个 Row 投影接口 → 同名 record（访问器名不变），放在适配器同包
- Test: `patra-catalog-infra/src/integrationTest/java/dev/patra/catalog/infra/adapter/read/*ReadAdapterIT.java`

- [ ] Step 1：对 5 个适配器的每个 public 方法，确认已有 IT 覆盖（列出方法 → IT 用例对照表写在提交说明里）；缺的补 IT，断言完整结果（字段值、顺序、分页总数、空结果、null 字段），用 SQL 种子数据
- [ ] Step 2：运行 `./gradlew :patra-catalog-infra:integrationTest --tests "*ReadAdapterIT*"`，确认在旧实现上全 PASS（锁住现状）
- [ ] Step 3：逐个适配器改写：注入 `JdbcClient`，把对应 Dao 方法 `@Query(nativeQuery = true)` 的 SQL 原文搬成私有常量，参数用命名参数，`.query(XxxRow.class)` 或显式 `RowMapper` 映射到 record；每改完一个跑该适配器的 IT
- [ ] Step 4：删除三个专用 Dao 与 `VenueDao` 中已无调用的门户方法；`./gradlew :patra-catalog:check` PASS
- [ ] Step 5：提交 `refactor(catalog): 门户读适配器改用 JdbcClient`

### Task 19：catalog fulltext → publication 改同步事件

**Files:**
- Create: `patra-catalog-domain/src/main/java/dev/patra/catalog/domain/event/PmcFullTextFetchedEvent.java`（搬包时到 `domain.fulltext.event`）
- Create: `patra-catalog-app/src/main/java/dev/patra/catalog/app/usecase/publication/PmcOpenAccessListener.java`（搬包时到 `app.publication.listener`）
- Modify: `PmcFullTextFetchPersister.java:70-72`（改为 `events.publishEvent(...)`，注入 `ApplicationEventPublisher events`）
- Test: `PmcFullTextFetchPersisterTest`（改断言发布事件）、`PmcOpenAccessListenerTest`、`patra-catalog-app` 或 `-boot` 的 IT `PmcOpenAccessListenerIT`

**Interfaces:** Produces: `record PmcFullTextFetchedEvent(long publicationId, String pmcid, String licenseCode, boolean manuscript)`——字段类型与 `PublicationOaLocation.ofPmc(...)` 的参数一致（若不一致，以 `ofPmc` 的参数类型为准，但只能用 JDK 或 kernel 类型）。`PmcOpenAccessListener.on(PmcFullTextFetchedEvent)`：同步 `@EventListener`，调 `publicationRepository.markPmcOpenAccess(e.publicationId(), PublicationOaLocation.ofPmc(e.pmcid(), e.licenseCode(), e.manuscript()))`。

- [ ] Step 1：改 `PmcFullTextFetchPersisterTest` 的两处 `verify(publicationRepository).markPmcOpenAccess` 为断言发布了 `new PmcFullTextFetchedEvent(7L, …)`；不可用路径断言没有发布事件
- [ ] Step 2：写 `PmcOpenAccessListenerTest.should_mark_pmc_open_access_on_event`
- [ ] Step 3：写 IT `should_roll_back_full_text_record_when_listener_fails`：监听抛异常时 `recordFetched` 写入的全文记录也回滚（锁住同事务语义）
- [ ] Step 4：运行确认 FAIL；实现；运行确认 PASS
- [ ] Step 5：提交 `refactor(catalog): 全文抓取经事件通知文献标记开放获取`

### Task 20：catalog 三个新读端口与常量内移

**Files 与 Interfaces**（均先在旧包里加，搬包时归位）：

| 端口（搬包后位置） | 方法 | 替换的现有代码 |
|---|---|---|
| `domain.author.port.read.AuthorOrcidReadPort` | `Map<String, Long> findAuthorIdsByOrcidIn(Collection<String> orcids)` | `PublicationRepositoryAdapter:1089` 直接用 `AuthorOrcidDao` |
| `domain.mesh.port.read.MeshNameReadPort` | `Map<String, String> findDescriptorUisByNameIn(Collection<String> names)`、`Map<String, String> findQualifierUisByNameIn(Collection<String> names)` | `VenuePubmedImportHandler:369` 用 `MeshDescriptorRepository` / `MeshQualifierRepository.findAllByNameIn` |
| `domain.organization.port.read.FunderLookupPort` | 签名不变，整组（端口 + Default / Caching / Batch 三个实现）从 publication 搬到 organization | `DefaultFunderLookupAdapter` 直接用 `OrganizationDao` |

返回值语义与被替换代码现行为一致（键为输入值，未命中不出现在 Map 中）。

- [ ] Step 1：为 `AuthorOrcidReadAdapter`、`MeshNameReadAdapter` 写 IT（命中、部分命中、空输入）；运行 FAIL
- [ ] Step 2：实现并让调用方改用端口；运行相关 IT 与 `VenuePubmedImportHandlerTest`、`PublicationRepositoryAdapterIT` PASS
- [ ] Step 3：`XmlParsingHelper` 去掉 `LsiouXmlElements.Date.*` 静态 import，用到的常量复制为 helper 的私有常量；`./gradlew :patra-catalog-infra:test` PASS
- [ ] Step 4：提交 `refactor(catalog): 跨子域查询改走读端口`

### Task 21：catalog 搬包

子域 `publication`、`venue`、`mesh`、`organization`、`author`、`fulltext`、`portal`，底座 `kernel`（spec §8.6 列表）。边：

```java
.allowingDependency("publication", "venue")
.allowingDependency("publication", "organization")
.allowingDependency("publication", "author")
.allowingDependency("venue", "mesh")
.allowingSubscription("publication", "fulltext")
```

额外：`XxlJobConfig` → `dev.patra.catalog.config`；五组 `*DataSource{AutoConfiguration,Properties}` 回各子域 `adapter.{x}.config`（靠组件扫描，搬后确认仍被扫描到：boot 的 `scanBasePackages` 覆盖 `dev.patra.catalog`）；`infra/parser/parser/…` 与 `infra/persistence/converter/converter/…` 两处测试目录多套的一层随搬包消失。

### Task 22：catalog 真实数据回归

- [ ] Step 1：本机启动迁移后的 catalog，数据源指向 mini 的 PG，关闭 XXL-Job 执行器注册（启动参数 `--xxl.job.executor.enabled=false` 或项目实际开关，先查 `XxlJobConfig` 的条件注解）
- [ ] Step 2：本机网关 + 门户 dev（`preview_start` 配置 `portal-dev`），Playwright 门户 e2e 指向本机网关：`PATRA_GATEWAY_BASE_URL=http://localhost:<网关端口> pnpm test:e2e --workers=1`（在 `patra-portal/` 下）
- [ ] Step 3：对 feed、检索（含 facets、翻页）、文献详情、期刊浏览、期刊详情、引文导出六个页面，与 mini 线上版本逐页比对返回 JSON（同一请求打两边，`diff`）；差异为零
- [ ] Step 4：结果写进 PR 描述

---

## 第 2 步：收尾（`refactor/package-layout`，串行）

### Task 23：合并与全量门控

- [ ] Step 1：按 catalog → identity → registry → object-storage 顺序把各服务分支合回 `refactor/package-layout`；`spotbugs-exclude.xml` 冲突手工合
- [ ] Step 2：`./gradlew check` PASS
- [ ] Step 3：全仓旧包字面量门控（五个服务迁移前的 `domain.model`、`app.usecase`、`infra.adapter`、`adapter.rest`、`adapter.scheduler` 等前缀，按服务逐一 `rg`）无输出
- [ ] Step 4：用例数与「用例数基线」一致（加上新增）

### Task 24：文档

**Files:** `.claude/rules/layers/{domain,application,infrastructure,adapter}.md`、`.claude/rules/tech/port-service.md`（删包位置表，改为引用 `package-layout.md`）；`.claude/skills/patra-backend/skills/patra-hexagonal/resources/{port-service-guide.md,controller-guide.md}` 的目录示例；`.claude/rules/project-info.md`（模块结构一节补一句分包规范的位置）。

- [ ] Step 1：改；`rg -n 'usecase/\{domain\}|adapter/persistence/|adapter/rest/\{entity\}' .claude` 无输出
- [ ] Step 2：提交 `docs(rules): 分层规则与技能示例对齐分包规范`

### Task 25：PR

- [ ] Step 1：`git push`、`gh pr create --draft`（描述：spec 链接、各服务子域表、Task 22 回归结果、`Closes` 无）
- [ ] Step 2：`ccd_pr` 绑定并开监控；完工转 ready，发 `@coderabbitai review`（超过 100 个文件 CodeRabbit 会跳过，不拆 PR）

---

## Task 13 结果

试跑 `HexagonalArchitectureRules.allRules()`（10/29）：

| 服务 | 结果 | 处理 |
|---|---|---|
| object-storage | 全部通过 | 已接入（`hexagonalRulesShouldHold`） |
| registry | 全部通过 | 已接入 |
| identity | 14 处，全是类放错包：`OutgoingMail` 与 13 个邮件 record（`*Mail`）放在 `domain.port.mail`，被端口命名规则和「端口实现须在 infra」规则判违规 | Task 17 搬包时移出端口包：`OutgoingMail` 随 `SecurityNotice` 进底座 `domain.session.model.vo`，13 个邮件 record 进 `domain.account.model.vo`（`NewDeviceLoginMail` 同样归 account）；`EmailDeliveryPort` 留在 `domain.account.port.mail`。接入用例的改动存为补丁，随 Task 17 提交 |

`TestingRules` 的规则检查的是测试类，而各服务架构测试只导入主代码（`DO_NOT_INCLUDE_TESTS`），在 object-storage 上直接报「没有检查到任何类」。第 1 步各服务接入时照 catalog 现有写法（逐条调用、允许为空），不强求 `allRules` 形式。

## 用例数基线

（执行 Task 14 时填写）
