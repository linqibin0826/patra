# interaction 服务骨架与部署接入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: 本仓库约定用 superpowers:executing-plans 在当前会话内联执行（不派子代理），每个任务按 Red-Green-Refactor 走。Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建 `patra-interaction` 六层模块（`subject`、`collection` 两个子域），用 ArchUnit 守住子域边界，建好 `patra_interaction` 库的 schema 与 Flyway 历史，并把 CD、compose、Nacos、网关、XXL-Job 执行器全部接好，附上线 runbook。

**Architecture:** 各层只有子域的 `package-info.java`，依赖只声明真正 import 到的（R5）。子域规则 `SubdomainArchitectureRules` 和放宽后的层规则放在 `patra-tool-test`，用夹具先红后绿；`InteractionArchitectureTest` 只负责应用。部署接入照 identity，逐项落在第 10 节核对单。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Gradle 9.5 约定插件、ArchUnit 1.4.1、Flyway + PostgreSQL 17（Testcontainers）、XXL-Job 3.2.0、Spring Cloud Gateway WebMVC、bash（CD 脚本与其 stub 测试）。

**Spec:** `docs/patra/specs/2026-10-24-interaction-skeleton-design.md`（下称 spec；引用写「spec §N」）

## Global Constraints

- 根包 `dev.patra.interaction`；层包 `dev.patra.interaction.{domain|app|infra|adapter|api}.{subject|collection}`；boot 的包 `dev.patra.interaction`、`dev.patra.interaction.config`。
- 服务名 `patra-interaction`；端口 `6500`，XXL-Job 执行器 `6501`；错误码前缀 `INTERACTION`；网关前缀 `/patra-interaction/**`。
- 库 `patra_interaction`；schema `subject`、`collection`；不设 `spring.flyway.schemas`；迁移脚本版本号 `yyyyMMddHHmmss`，放 `db/migration/{子域}/`。
- 模块只声明主代码真正 import 到的 project 依赖（R5）；boot 不受 R5 约束。
- 不建表、不加安全 starter、不连 Redis、不建 Outbox（spec §2「不做」）。
- 所有方法写 `///` Javadoc（Markdown）；不用全限定类名；Google Java 风格，`./gradlew spotlessApply` 格式化；gjf 会把超长 `///` 折成 `//`，长行手动断开。
- 测试方法 snake_case：`should_…` 或 `…_when_…`；断言用 AssertJ；不用反射访问私有成员。
- 注释、文档、提交信息用中文；提交 subject 用中文动词开头（commitlint 拒绝大写开头）。
- **未经用户明确同意不 commit、不 push**；下面每个任务末尾的「提交」步骤是提请用户同意的点，不同意就保持工作区改动继续下一任务。
- 改了 `*.gradle.kts` 后提醒用户在 IDEA 里点「加载 Gradle 更改」。
- 动 mini 的操作（建库、Nacos 推送、调度中心建执行器）一律先问用户。

## Review Focus

1. **放宽层规则后 catalog、ingest 的架构测试必须照旧通过**——放宽只能更宽。Task 1 Step 6 跑这两个测试。
2. **架构测试必须真的导入了 interaction 的类**，否则规则对空集合永远绿。Task 6 Step 3 放一个故意违规的类看它变红，再删掉。
3. **test-fixtures jar 不能被当成子域代码**（`dev.patra.interaction.infra.config` 会被判成未声明子域）。Task 6 的导入条件排除它，Step 2 的全绿即证明。
4. **`unitOf` 漏改时 CI 照绿、CD 不部署**。Task 7 的新场景断言 interaction 私有改动的 `backend_units` 恰好是 `["interaction"]`，不是 `["foundation"]`。
5. **Flyway 历史只有一份、在 `public`**：子目录脚本被递归扫到且没有第二张历史表。Task 3 的迁移测试逐条断言。

---

### Task 1: 放宽层规则里写死的连续包路径

**Files:**
- Modify: `patra-tool/patra-tool-test/src/main/java/dev/patra/tool/test/archunit/HexagonalArchitectureRules.java`（四条规则：`doClassesShouldResideInPersistenceEntity`、`aggregatesShouldResideInDomainModelAggregate`、`orchestratorsShouldResideInAppUsecase`、`eventsShouldResideInDomainEvent`）
- Create: `patra-tool/patra-tool-test/src/test/java/dev/patra/tool/test/archunit/HexagonalArchitectureRulesTest.java`
- Create（夹具，根包 `dev.patra.tool.test.archunit.fixtures.subdomainlayout`）：
  - `domain/collection/model/aggregate/FooAggregate.java`
  - `domain/collection/event/FooEvent.java`
  - `app/collection/usecase/FooOrchestrator.java`
  - `infra/collection/persistence/entity/FooDO.java`

**Interfaces:**
- Produces: 四条规则的新包模式（spec §5.1 表），签名不变。

- [ ] **Step 1: 写夹具与失败测试**

夹具都是空的 `public class`，各带一行 `///` 说明「按子域布局放置的 X，用来验证规则接受 `layer.子域.x` 布局」。

```java
@DisplayName("六边形层规则：按子域分包的布局")
class HexagonalArchitectureRulesTest {
  private static final String BASE = "dev.patra.tool.test.archunit.fixtures.subdomainlayout";
  private static final JavaClasses CLASSES = new ClassFileImporter().importPackages(BASE);
  private static final HexagonalArchitectureRules RULES = new HexagonalArchitectureRules(BASE);

  @Test void should_accept_aggregate_under_subdomain_model_aggregate() {
    assertThat(RULES.aggregatesShouldResideInDomainModelAggregate().evaluate(CLASSES).hasViolation()).isFalse();
  }
  @Test void should_accept_event_under_subdomain_event() { /* eventsShouldResideInDomainEvent，同上 */ }
  @Test void should_accept_orchestrator_under_subdomain_usecase() { /* orchestratorsShouldResideInAppUsecase */ }
  @Test void should_accept_data_object_under_subdomain_persistence_entity() { /* doClassesShouldResideInPersistenceEntity */ }
}
```

- [ ] **Step 2: 运行，确认四个都失败**

Run: `./gradlew :patra-tool:patra-tool-test:test --tests '*HexagonalArchitectureRulesTest'`
Expected: 4 个 FAIL（违规文本分别指向 `FooAggregate`、`FooEvent`、`FooOrchestrator`、`FooDO`）

- [ ] **Step 3: 按 spec §5.1 改四个包模式，并改这四条规则的 Javadoc 说法**

`..infra.persistence.entity..` → `..infra..persistence.entity..`；`..domain.model.aggregate..` → `..domain..model.aggregate..`；`..app.usecase..` → `..app..usecase..`；`..domain.event..` → `..domain..event..`。`.as(...)` 文案同步改成「domain 下任意层级的 model.aggregate 包」这类说法。

- [ ] **Step 4: 运行，确认通过**

Run: 同 Step 2。Expected: 4 PASS

- [ ] **Step 5: 加一个反例，证明放宽没有放成「随便放」**

在夹具里加 `dev.patra.tool.test.archunit.fixtures.misplaced.domain.collection.model.BarAggregate`（不在 `model.aggregate` 下），测试 `should_reject_aggregate_outside_model_aggregate` 用 `new HexagonalArchitectureRules("dev.patra.tool.test.archunit.fixtures.misplaced")` 断言 `hasViolation()` 为真。运行，Expected: PASS（新旧写法都会拒绝它）。注意：`subdomainlayout` 测试的导入范围不能包含 `misplaced`。

- [ ] **Step 6: 跑现有服务的架构测试**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-boot:test --tests '*CatalogArchitectureTest' :patra-api:patra-ingest:patra-ingest-boot:test --tests '*IngestArchitectureTest'`
Expected: 全部 PASS

- [ ] **Step 7: 提交（需用户同意）**

```bash
git add patra-tool/patra-tool-test
git commit -m "放宽六边形层规则的连续包路径，接受按子域分包的布局 (PAP-111)"
```

---

### Task 2: 子域规则 `SubdomainArchitectureRules`

**Files:**
- Create: `patra-tool/patra-tool-test/src/main/java/dev/patra/tool/test/archunit/SubdomainArchitectureRules.java`
- Create: `patra-tool/patra-tool-test/src/test/java/dev/patra/tool/test/archunit/SubdomainArchitectureRulesTest.java`
- Create: 夹具，根包 `dev.patra.tool.test.archunit.fixtures.subdomain.<场景>`，每个场景一棵独立的包树（见 Step 1 表）
- Modify: `patra-tool/patra-tool-test/README.md`（ArchUnit 一节加一段：子域规则是什么、怎么用）

**Interfaces:**
- Produces:
  ```java
  public class SubdomainArchitectureRules {
    public SubdomainArchitectureRules(String basePackage, String foundationSubdomain, Set<String> subdomains);
    public ArchRule layersContainOnlyDeclaredSubdomains();
    public ArchRule subdomainsAreFreeOfCycles();
    public ArchRule crossSubdomainDependenciesGoThroughPublishedPackages();
    public ArchRule foundationDependsOnNoOtherSubdomain();
    public List<ArchRule> allRules();
  }
  ```
  构造参数校验：`basePackage` 非空白；`subdomains` 非空且含 `foundationSubdomain`，否则 `IllegalArgumentException`。`subdomains` 用 `Set.copyOf` 拷贝。层固定为 `domain`、`app`、`infra`、`adapter`、`api`（常量）。公开包按 spec §5.2 表。

每条规则一个红绿循环：先写这条规则的合规与违规测试，看它因方法不存在而编译失败或断言失败，再实现。夹具一次建好。

- [ ] **Step 1: 建夹具**

所有夹具是空类或只有一个字段 / 方法参数来制造依赖。下表的 `{R}` 是该场景的根包 `…fixtures.subdomain.<场景>`，三个子域统一叫 `subject`（底座）、`collection`、`note`。

| 场景包 | 类（相对 `{R}`） | 制造的依赖 |
|---|---|---|
| `compliant` | `domain.subject.port.read.SubjectReadPort`（接口）、`domain.subject.model.read.SubjectView`、`domain.subject.port.gateway.SubjectGateway`（接口）、`domain.subject.model.vo.SubjectId`、`domain.subject.model.enums.SubjectType`（枚举）、`domain.collection.port.repository.CollectionRepository`（接口）、`app.collection.CollectFavoriteHandler`、`adapter.collection.FavoriteController`、`config.Wiring` | `CollectFavoriteHandler` 字段用 `SubjectReadPort`、`SubjectGateway`，方法参数用 `SubjectId`、`SubjectType`、返回 `SubjectView`；`FavoriteController` 字段用 `CollectionRepository`；`Wiring` 字段用 `app.collection.CollectFavoriteHandler` 和一个 `app.subject.SubjectInternal`（同场景另建） |
| `foreignrepository` | `domain.subject.port.repository.SubjectRepository`、`app.collection.UsesSubjectRepository` | 后者字段用前者 |
| `foreignapp` | `app.subject.ResolveSubjectHandler`、`adapter.collection.UsesSubjectHandler` | 后者字段用前者 |
| `foreigngateway` | `domain.note.port.gateway.NoteGateway`、`app.collection.UsesNoteGateway` | 后者字段用前者（`note` 不是底座，`port.gateway` 不公开） |
| `foundationreverse` | `domain.collection.port.read.CollectionReadPort`、`app.subject.UsesCollectionReadPort` | 后者字段用前者 |
| `cycle` | `domain.collection.port.read.CollectionReadPort`、`domain.note.port.read.NoteReadPort`、`app.collection.UsesNoteReadPort`、`app.note.UsesCollectionReadPort` | 两两互用 |
| `loose` | `domain.Loose` | 直接在层根下 |
| `undeclared` | `domain.common.Shared` | 未声明的子域 `common` |

规则对象：`compliant`、`foreign*`、`foundationreverse`、`loose`、`undeclared` 用 `new SubdomainArchitectureRules({R}, "subject", Set.of("subject", "collection", "note"))`；每个测试只导入自己场景的包：`new ClassFileImporter().importPackages({R})`。

- [ ] **Step 2: 子域声明规则——写测试**

```java
@Test void should_accept_classes_inside_declared_subdomains_and_boot_packages()   // compliant → hasViolation() false
@Test void should_reject_class_directly_under_layer_root()                        // loose → true，违规文本含 "Loose"
@Test void should_reject_undeclared_subdomain_package()                           // undeclared → true，违规文本含 "Shared"
@Test void should_reject_foundation_missing_from_subdomains()                     // 构造参数 ("x", "subject", Set.of("collection")) → IllegalArgumentException
```

违规文本用 `evaluate(classes).getFailureReport().getDetails()` 取。

- [ ] **Step 3: 运行，确认失败（编译失败也算）**

Run: `./gradlew :patra-tool:patra-tool-test:test --tests '*SubdomainArchitectureRulesTest'`

- [ ] **Step 4: 实现构造器与 `layersContainOnlyDeclaredSubdomains()`**

`classes().that(resideInLayerPackage).should(resideInDeclaredSubdomain)`：前者是「包名以 `{base}.{层}.` 开头或等于 `{base}.{层}`」的 `DescribedPredicate`，后者是自定义 `ArchCondition`，取包名在 `{base}.{层}.` 之后的第一段，必须在 `subdomains` 里。`.allowEmptyShould(true)`。`as(...)` / `because(...)` 用中文，说明「层下面只放已声明的子域；新增子域要改 `subdomains` 参数」。

- [ ] **Step 5: 运行，确认通过**

- [ ] **Step 6: 公开包规则——写测试**

```java
@Test void should_allow_published_packages_across_subdomains()        // compliant → false（含 boot 的 config.Wiring 依赖两个子域的内部类也不违规）
@Test void should_reject_foreign_repository()                          // foreignrepository → true，文本含 "SubjectRepository"
@Test void should_reject_foreign_app_class()                           // foreignapp → true，文本含 "ResolveSubjectHandler"
@Test void should_reject_gateway_of_non_foundation_subdomain()         // foreigngateway → true，文本含 "NoteGateway"
```

- [ ] **Step 7: 运行，确认失败**

- [ ] **Step 8: 实现 `crossSubdomainDependenciesGoThroughPublishedPackages()`**

`classes().that(resideInSomeSubdomain).should(onlyDependOnOtherSubdomainsThroughPublishedPackages)`：自定义 `ArchCondition` 遍历 `javaClass.getDirectDependenciesFromSelf()`，源子域 A、目标子域 B 都存在且 A ≠ B 时，目标包必须以 spec §5.2 的公开包前缀之一开头（前缀判断要按包段比较：`…port.read` 或 `…port.read.`），否则 `SimpleConditionEvent.violated(dependency, dependency.getDescription())`。子域解析抽成一个私有方法 `Optional<String> subdomainOf(String packageName)`，Step 4 与这里共用。

- [ ] **Step 9: 运行，确认通过**

- [ ] **Step 10: 底座规则与环规则——写测试**

```java
@Test void should_reject_foundation_depending_on_other_subdomain()   // foundationreverse，foundationDependsOnNoOtherSubdomain() → true，文本含 "CollectionReadPort"
@Test void should_accept_foundation_free_of_other_subdomains()       // compliant，同一规则 → false
@Test void should_reject_cycle_between_subdomains()                  // cycle，subdomainsAreFreeOfCycles() → true
@Test void should_accept_acyclic_subdomains()                        // compliant，同一规则 → false
@Test void should_list_all_four_rules()                              // allRules() 大小为 4
```

- [ ] **Step 11: 运行，确认失败**

- [ ] **Step 12: 实现两条规则与 `allRules()`**

底座规则：`noClasses().that(resideInSubdomain(foundation)).should().dependOnClassesThat(resideInSubdomainOtherThan(foundation))`。环规则：`slices().assignedFrom(SliceAssignment)`，`getIdentifierOf(JavaClass)` 返回 `SliceIdentifier.of(子域)`，不属于子域的类返回 `SliceIdentifier.ignore()`；`.should().beFreeOfCycles()`。`allRules()` 返回不可变列表。

- [ ] **Step 13: 运行全部，确认通过；再跑 spotless 与模块 check**

Run: `./gradlew spotlessApply :patra-tool:patra-tool-test:check`
Expected: BUILD SUCCESSFUL

- [ ] **Step 14: README 补一段并提交（需用户同意）**

```bash
git add patra-tool/patra-tool-test
git commit -m "新增子域边界的 ArchUnit 规则 (PAP-111)"
```

---

### Task 3: 六个模块、迁移脚本与迁移集成测试

**Files:**
- Modify: `settings.gradle.kts`（identity 段之后加六行 `includeAt` 与 `mapParent(":patra-api:patra-interaction", "patra-api/patra-interaction")`）
- Create: `patra-api/patra-interaction/build.gradle.kts`（只有注释）
- Create: `patra-api/patra-interaction/patra-interaction-{domain,app,infra,adapter,api,boot}/build.gradle.kts`（内容按 spec §4.1、§4.3；boot 这一步先只写插件、`springBoot.mainClass` 与依赖，代码在 Task 4、5）
- Create: `package-info.java`，共八个：`patra-interaction-{domain,app,infra,adapter}/src/main/java/dev/patra/interaction/{domain|app|infra|adapter}/{subject,collection}/package-info.java`
- Create: `patra-interaction-infra/src/main/resources/db/migration/subject/V20261024100000__create_subject_schema.sql`
- Create: `patra-interaction-infra/src/main/resources/db/migration/collection/V20261024100100__create_collection_schema.sql`
- Create: `patra-interaction-infra/src/testFixtures/java/dev/patra/interaction/infra/config/InteractionITPostgreSQLContainerInitializer.java`
- Create: `patra-interaction-infra/src/integrationTest/java/dev/patra/interaction/infra/InteractionITBootstrap.java`
- Create: `patra-interaction-infra/src/integrationTest/java/dev/patra/interaction/infra/InteractionMigrationIT.java`
- Create: `patra-interaction-infra/src/integrationTest/resources/application-test.yml`

**Interfaces:**
- Produces: Gradle 路径 `:patra-api:patra-interaction:patra-interaction-{layer}`；`InteractionITPostgreSQLContainerInitializer`（`getDatabaseName()` 返回 `"patra_interaction"`，`public` 类）供 Task 5 使用；infra 的 `testFixturesImplementation(project(":patra-tool:patra-tool-test"))`。

- [ ] **Step 1: 注册模块、写 build 文件与 package-info，确认能编译**

infra 的依赖：`api(project(":patra-tool:patra-tool-jpa"))`、`testFixturesImplementation(project(":patra-tool:patra-tool-test"))`。domain、app、adapter、api 无 project 依赖。`package-info.java` 内容按 spec §4.2 最后一段的职责说明。

Run: `./gradlew :patra-api:patra-interaction:projects compileJava`
Expected: 列出六个子模块；BUILD SUCCESSFUL。提醒用户在 IDEA 点「加载 Gradle 更改」。

- [ ] **Step 2: 写迁移集成测试**

`InteractionITBootstrap`：`@SpringBootApplication class InteractionITBootstrap {}`，包级可见，`///` 说明「infra 集成测试的启动类，只在测试里存在」。`application-test.yml`：关 Nacos 发现与注册（照 identity 的那份）。

```java
@SpringBootTest(classes = InteractionITBootstrap.class)
@ContextConfiguration(initializers = InteractionITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
@DisplayName("interaction 的 Flyway 迁移")
class InteractionMigrationIT {
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test void should_create_one_schema_per_subdomain() {
    // information_schema.schemata 里排除 pg_% 与 information_schema 后，恰好是 public、subject、collection
  }
  @Test void should_keep_a_single_flyway_history_in_public() {
    // information_schema.tables 里 table_name = 'flyway_schema_history' 的行恰好一条，table_schema = 'public'
  }
  @Test void should_record_both_migrations_with_timestamp_versions() {
    // SELECT version FROM public.flyway_schema_history WHERE success ORDER BY installed_rank
    // containsExactly("20261024100000", "20261024100100")
  }
}
```

- [ ] **Step 3: 运行，确认失败**

Run: `./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest --tests '*InteractionMigrationIT'`
Expected: 三个都 FAIL（没有脚本：没有两个 schema，没有历史表或历史为空）

- [ ] **Step 4: 写两份脚本**

各一句 `CREATE SCHEMA subject;` / `CREATE SCHEMA collection;`，上面一行 `--` 注释说明这个 schema 放什么（spec §6.2）。

- [ ] **Step 5: 运行，确认通过；跑 R6 与 R5**

Run: `./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest --tests '*InteractionMigrationIT' :patra-api:patra-interaction:patra-interaction-infra:checkMigrationColumns && ./gradlew -p patra-api/patra-interaction check`（聚合模块自己没有 `check` 任务，用 `-p` 跑它下面的六个子模块）
Expected: 全部通过（R6 的 `checkMigrationColumns` 不是 NO-SOURCE，说明子目录被扫到；R5 的 `checkDeclaredDependencies` 通过）

- [ ] **Step 6: 提交（需用户同意）**

```bash
git add settings.gradle.kts patra-api/patra-interaction
git commit -m "新建 interaction 六层模块与子域 schema 迁移 (PAP-111)"
```

---

### Task 4: boot 的 jar 内配置

**Files:**
- Create: `patra-interaction-boot/src/main/resources/application.yml`
- Create: `patra-interaction-boot/src/test/java/dev/patra/interaction/ApplicationConfigTest.java`

**Interfaces:**
- Produces: `application.yml` 的键值（spec §7.2）。

- [ ] **Step 1: 写失败测试**

照 identity 的 `ApplicationConfigTest` 写（`property`、`propertyNames` 两个私有辅助方法同款），测试：

```java
@Test void should_not_ship_profile_files()                                   // application-dev.yml、application-container.yml 都不在 classpath
@Test void should_leave_name_profile_and_nacos_to_launcher_and_starter()     // 无 spring.application.name / spring.profiles.active / spring.cloud.nacos*
@Test void should_listen_on_6500_with_interaction_error_prefix()             // server.port == "6500"，patra.web.error.context-prefix == "INTERACTION"
@Test void should_keep_flyway_history_in_default_schema()                    // spring.flyway.enabled == "true"，locations == "classpath:db/migration"，无 spring.flyway.schemas*
@Test void should_not_configure_redis()                                      // 无 spring.data.redis*
```

- [ ] **Step 2: 运行，确认失败**

Run: `./gradlew :patra-api:patra-interaction:patra-interaction-boot:test --tests '*ApplicationConfigTest'`
Expected: 读 `application.yml` 的用例 FAIL（文件不存在）

- [ ] **Step 3: 写 `application.yml`**

键值按 spec §7.2 的表；头部注释写明「库地址、账号在 Nacos：patra-interaction.yaml、patra-common.yaml（源文件在 patra-infra/nacos/dev/），由启动器导入」，XXL-Job 段注释同 identity，日志目录 `${XXL_JOB_EXECUTOR_LOG_PATH:${PATRA_LOG_DIR:logs}/xxl-job/patra-interaction}`。

- [ ] **Step 4: 运行，确认通过**

- [ ] **Step 5: 提交（需用户同意）**

```bash
git add patra-api/patra-interaction/patra-interaction-boot
git commit -m "补上 interaction 的 jar 内配置 (PAP-111)"
```

---

### Task 5: 启动类、XXL-Job 执行器、Nacos 配置与启动集成测试

**Files:**
- Create: `patra-interaction-boot/src/main/java/dev/patra/interaction/PatraInteractionApplication.java`（spec §7.1）
- Create: `patra-interaction-boot/src/main/java/dev/patra/interaction/config/XxlJobConfig.java`（照 `patra-identity-adapter/.../scheduler/config/XxlJobConfig.java`，Javadoc 改成 interaction 的任务：对象对账、注销清理，执行器端口 6501）
- Create: `patra-interaction-boot/src/integrationTest/java/dev/patra/interaction/PatraInteractionApplicationIT.java`
- Create: `patra-interaction-boot/src/integrationTest/resources/application-test.yml`（关 Nacos 发现与注册、`xxl.job.enabled: false`）
- Create: `patra-infra/nacos/dev/patra-interaction.yaml`（spec §7.4）
- Create: `.run/PatraInteractionApplication.run.xml`（照 identity，module `patra.patra-api.patra-interaction.patra-interaction-boot.main`，主类 `dev.patra.interaction.PatraInteractionApplication`）

**Interfaces:**
- Consumes: Task 3 的 `InteractionITPostgreSQLContainerInitializer`（经 boot 的 `testImplementation(testFixtures(infra))`）。
- Produces: 主类 `dev.patra.interaction.PatraInteractionApplication`（`services.json` 的 bootJar 由它打出）。

- [ ] **Step 1: 写失败的启动测试**

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = InteractionITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
@DisplayName("interaction 应用启动")
class PatraInteractionApplicationIT {
  @Autowired private RestTestClient restClient;
  @Autowired private Environment environment;
  @Autowired private ApplicationContext context;

  @Test void should_report_up_on_health_endpoint() {
    // GET /actuator/health → 200，JSON 的 status 为 "UP"
  }
  @Test void should_use_interaction_error_prefix() {
    // environment.getProperty("patra.web.error.context-prefix") == "INTERACTION"
  }
  @Test void should_not_start_xxl_job_executor_in_tests() {
    // context.getBeansOfType(XxlJobSpringExecutor.class) 为空
  }
}
```

- [ ] **Step 2: 运行，确认失败**

Run: `./gradlew :patra-api:patra-interaction:patra-interaction-boot:integrationTest --tests '*PatraInteractionApplicationIT'`
Expected: 编译失败（没有 `PatraInteractionApplication`，`@SpringBootTest` 找不到配置类）

- [ ] **Step 3: 写启动类与 `XxlJobConfig`**

- [ ] **Step 4: 运行，确认通过**

Expected: 3 PASS；日志里 Flyway 应用了两个脚本。

- [ ] **Step 5: 写 Nacos 配置，跑配置检查**

Run: `./gradlew :patra-tool:patra-tool-launch:test --tests '*NacosConfigFilesTest' --tests '*JarConfigOverlapCheckerTest'`
Expected: PASS（仓库目录用例覆盖了新文件；jar 与 Nacos 没有重复键）

- [ ] **Step 6: 跑 bootJar，确认能打出可执行 jar**

Run: `./gradlew :patra-api:patra-interaction:patra-interaction-boot:bootJar && ls patra-api/patra-interaction/patra-interaction-boot/build/libs/`
Expected: 一个不带 classifier 的 jar

- [ ] **Step 7: 提交（需用户同意）**

```bash
git add patra-api/patra-interaction/patra-interaction-boot patra-infra/nacos/dev/patra-interaction.yaml .run/PatraInteractionApplication.run.xml
git commit -m "接上 interaction 启动入口、XXL-Job 执行器与 Nacos 配置 (PAP-111)"
```

---

### Task 6: `InteractionArchitectureTest`

**Files:**
- Create: `patra-interaction-boot/src/test/java/dev/patra/interaction/architecture/InteractionArchitectureTest.java`
- Create: `patra-interaction-boot/src/test/resources/archunit.properties`（`archRule.failOnEmptyShould = false`，同 catalog）

**Interfaces:**
- Consumes: Task 1 的 `HexagonalArchitectureRules`、Task 2 的 `SubdomainArchitectureRules`、现有 `TestingRules`。

- [ ] **Step 1: 写测试**

结构照 `CatalogArchitectureTest` 的 `@Nested` 分类，规则对象：

```java
new HexagonalArchitectureRules("dev.patra.interaction", List.of("dev.patra.common..", "dev.patra.tool.."));
new SubdomainArchitectureRules("dev.patra.interaction", "subject", Set.of("subject", "collection"));
```

新增第七类「子域边界」，四个测试分别调用 `SubdomainArchitectureRules` 的四条规则。导入：`DO_NOT_INCLUDE_TESTS`，加自定义 `ImportOption`——路径含 `test-fixtures` 或 `/testFixtures/` 的排除；含 `/patra-interaction-` 的包含；其余 `.jar` 排除；其余包含。`importPackages("dev.patra.interaction")`。

- [ ] **Step 2: 运行，确认通过**

Run: `./gradlew :patra-api:patra-interaction:patra-interaction-boot:test --tests '*InteractionArchitectureTest'`
Expected: 全部 PASS

- [ ] **Step 3: 证明它真的看到了 interaction 的类（做完即删）**

临时新建 `patra-interaction-domain/src/main/java/dev/patra/interaction/domain/Loose.java`（空类），再跑 Step 2 的命令。Expected: `layersContainOnlyDeclaredSubdomains` 那个测试 FAIL，违规文本含 `dev.patra.interaction.domain.Loose`。删掉该文件，再跑一次，Expected: 全部 PASS。

- [ ] **Step 4: 提交（需用户同意）**

```bash
git add patra-api/patra-interaction/patra-interaction-boot/src/test
git commit -m "为 interaction 加架构测试，应用层规则与子域规则 (PAP-111)"
```

---

### Task 7: 受影响单元门控（`unitOf`、模块图、`ALL_UNITS`）

**Files:**
- Modify: `build.gradle.kts`（`dumpModuleGraph` 的 `unitOf` 加 `patra-api/patra-interaction` 分支；`"units"` 列表加 `"interaction"`）
- Modify（生成）: `patra-infra/cd/module-graph.json`
- Modify: `patra-infra/cd/detect-changes.sh`（第 6 行注释与第 22 行 `ALL_UNITS` 加 `interaction`）
- Modify: `patra-infra/cd/detect-changes.test.sh`

- [ ] **Step 1: 改测试**

第 44 行期望改为 `["catalog","foundation","identity","ingest","interaction","object-storage","registry"]`；第 99 行改为 `["catalog","foundation","gateway","identity","ingest","interaction","object-storage","registry"]`，描述改成「全部 7 个服务」。在场景 13 之后加：

```bash
echo "== 场景 15：interaction 单元（私有改动只跑 interaction；全量运行包含 interaction；它的 nacos yaml 只重部署它）=="
F="patra-api/patra-interaction/patra-interaction-infra/src/main/resources/db/migration/subject/V1__x.sql"
assert_field "$F" '.backend_units' '["interaction"]' "interaction 私有只跑 interaction"
assert_field "settings.gradle.kts" '.backend_units | index("interaction") != null' 'true' "全量运行包含 interaction"
assert_field "patra-infra/nacos/dev/patra-interaction.yaml" '.backend_units' '["foundation","interaction"]' "interaction 的 nacos yaml 重部署 interaction"
```

（场景编号若与现有冲突，取下一个空号。）

- [ ] **Step 2: 运行，确认失败**

Run: `bash patra-infra/cd/detect-changes.test.sh`
Expected: 新场景三条与改过的两条 ✗（`backend_units` 实得 `["foundation"]` 一类）

- [ ] **Step 3: 改 `unitOf`、`units`，重新生成模块图；改 `ALL_UNITS`**

Run: `./gradlew dumpModuleGraph -q && git diff --stat patra-infra/cd/module-graph.json`
Expected: 模块图里六个 interaction 模块的 `unit` 都是 `interaction`；`patra-common`、tool 模块的 `impacts` 多出 `interaction`；`units` 含 `interaction`。

- [ ] **Step 4: 运行，确认通过**

Run: `bash patra-infra/cd/detect-changes.test.sh`
Expected: 全部 ✓，`FAIL=0`

- [ ] **Step 5: 提交（需用户同意）**

```bash
git add build.gradle.kts patra-infra/cd/module-graph.json patra-infra/cd/detect-changes.sh patra-infra/cd/detect-changes.test.sh
git commit -m "把 interaction 纳入受影响单元门控 (PAP-111)"
```

---

### Task 8: 部署清单（`services.json`、compose、建库、`nacos-verify.sh`）

**Files:**
- Modify: `patra-infra/cd/services.json`（spec 核对单 #7，条目放在 catalog 与 ingest 之间，`_comment` 补「interaction 在 catalog 之后」）
- Modify: `patra-infra/cd/deploy.test.sh`（新增场景 7）
- Modify: `patra-infra/docker/docker-compose.apps.yaml`（spec §10.1 的服务块放在 catalog 块之后；头部注释「6 个后端服务必需」→ 7 个）
- Modify: `patra-infra/docker/postgres/init-scripts/02-create-databases.sql`（`patra_identity` 之后加 `CREATE DATABASE patra_interaction;`）
- Modify: `patra-infra/scripts/nacos-verify.sh`（`SERVICES` 末尾加 `patra-interaction`；头部注释与第 10 项描述「6 个」→ 7 个）

- [ ] **Step 1: 写 deploy 的失败场景**

照场景 6 写场景 7：镜像桩里放 `patra-catalog:newsha`、`patra-interaction:newsha`，健康桩放 `newsha`，`deploy.sh newsha '["interaction","catalog"]'`；断言退出码 0，`up -d` 顺序为 `catalog interaction`，两个 last-good 都是 `newsha`。文件头的场景说明补一行。

- [ ] **Step 2: 运行，确认失败**

Run: `bash patra-infra/cd/deploy.test.sh`
Expected: 场景 7 ✗（退出码 2，「未知服务: interaction」）

- [ ] **Step 3: 加 `services.json` 条目**

- [ ] **Step 4: 运行，确认通过**

Expected: `FAIL=0`

- [ ] **Step 5: 改 compose、建库脚本、`nacos-verify.sh`，做静态检查**

```bash
T="$(mktemp -d)"; mkdir -p "$T/.patra/secrets"; touch "$T/.patra/secrets/nacos.env" "$T/.patra/secrets/redis.env" "$T/.patra/secrets/gateway.env"
HOME="$T" docker compose -f patra-infra/docker/docker-compose.apps.yaml config --format json | jq '.services.interaction | {image, ports, env_file: [.env_file[].path]}'
bash -n patra-infra/scripts/nacos-verify.sh
```

Expected: `image` 为 `ghcr.io/linqibin0826/patra-interaction:latest`，端口 6500，`env_file` 依次是 `.env.common`、`nacos.env`、`interaction.env`；`bash -n` 无输出。

- [ ] **Step 6: 提交（需用户同意）**

```bash
git add patra-infra/cd/services.json patra-infra/cd/deploy.test.sh patra-infra/docker/docker-compose.apps.yaml patra-infra/docker/postgres/init-scripts/02-create-databases.sql patra-infra/scripts/nacos-verify.sh
git commit -m "把 interaction 加进 CD 服务清单、compose 与 Nacos 验收 (PAP-111)"
```

---

### Task 9: 网关路由

**Files:**
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/GatewayRoutingIT.java`
- Modify: `patra-api/patra-gateway-boot/src/main/resources/application.yml`（spec §9 的路由与 `scalar.sources`）
- Modify: `patra-api/patra-gateway-boot/src/main/java/dev/patra/gateway/PatraGatewayApplication.java`（Javadoc 服务清单）

- [ ] **Step 1: 写失败测试**

加第二个 `WireMockExtension interaction`（与 `catalog` 同样的选项），`@DynamicPropertySource` 里加 `spring.cloud.discovery.client.simple.instances.patra-interaction[0].uri`。

```java
@Test void should_route_interaction_prefix_to_interaction_service() {
  // interaction 桩：GET /collections/1，queryParam view=reader → okJson("{}")
  // restClient GET /patra-interaction/collections/1?view=reader → 200
  // interaction.verify(getRequestedFor(urlPathEqualTo("/collections/1")).withQueryParam("view", equalTo("reader")))
}
```

- [ ] **Step 2: 运行，确认失败**

Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests '*GatewayRoutingIT'`
Expected: 新用例 FAIL（没有路由，404）；其余用例 PASS

- [ ] **Step 3: 加路由与文档聚合源，改 Javadoc**

- [ ] **Step 4: 运行，确认通过；再跑网关的拒绝名单与配置测试**

Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests '*GatewayRoutingIT' --tests '*GatewayBlockedPathsIT' :patra-api:patra-gateway-boot:test`
Expected: 全部 PASS

- [ ] **Step 5: 提交（需用户同意）**

```bash
git add patra-api/patra-gateway-boot
git commit -m "网关加 interaction 路由与文档聚合 (PAP-111)"
```

---

### Task 10: 文档与上线 runbook

**Files:**
- Create: `patra-api/patra-interaction/README.md`（spec §13 第一行列的内容，含公开包表、存储约定、拆分条件）
- Create: `docs/patra/runbooks/v0.13-library-go-live-runbook.md`（spec §14 的结构；命令格式、预期、失败动作照 v0.8 runbook）
- Modify: `patra-api/README.md`、根 `README.md`、`.claude/rules/project-info.md`、`patra-infra/CLAUDE.md`、`patra-infra/docker/README.md`、`patra-infra/nacos/dev/README.md`、`patra-infra/docker/.env.common`（头注释）

- [ ] **Step 1: 写服务 README**

- [ ] **Step 2: 写 runbook**

第 4 步给出调度中心建执行器的两种做法：控制台路径（执行器管理 → 新增：AppName `patra-interaction`、名称 `互动服务`、自动注册），以及接口做法（登录走 `/auth/doLogin` 取 cookie，再 POST `/jobgroup/insert`；命令里不写密码明文，读环境变量）。第 6 步的两个任务参数按 spec §11.2 的表。

- [ ] **Step 3: 同步各处服务清单**

Run: `git grep -n "6 个后端\|6 个服务\|全部 6 个\|六个后端\|registry / object-storage / catalog / ingest / identity"`
逐条判断是否指后端服务清单，是就改成含 interaction / 7 个。`.claude/rules/project-info.md` 的「核心服务」加一行 `patra-interaction` - 社区互动服务（文献库、收藏夹；以后的讨论、关注、通知）。若编辑 `.claude/` 下的文件被钩子拒绝，不要绕过，把改动写成补丁文件交给用户。

- [ ] **Step 4: 再扫一遍，确认没有遗漏**

Run: 同 Step 3 的 `git grep`。Expected: 剩下的命中都不是指后端服务清单（如历史 spec、计划文档，不改）。

- [ ] **Step 5: 提交（需用户同意）**

```bash
git add patra-api/patra-interaction/README.md docs/patra/runbooks/v0.13-library-go-live-runbook.md patra-api/README.md README.md .claude/rules/project-info.md patra-infra
git commit -m "补 interaction 的 README、上线 runbook 与服务清单 (PAP-111)"
```

---

### Task 11: 全量门禁与本机启动

**Files:** 无新文件；实测结果写进 spec 末尾新增的「实施记录」一节。

- [ ] **Step 1: 格式化与全量检查**

Run: `./gradlew spotlessApply && ./gradlew check`
Expected: BUILD SUCCESSFUL。若 spotless 改了文件，`git add -u` 后再提交。

- [ ] **Step 2: 显式跑相关集成测试**（`check` 是否带上 `integrationTest` 以实际输出为准）

Run: `./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest :patra-api:patra-interaction:patra-interaction-boot:integrationTest :patra-api:patra-gateway-boot:integrationTest :patra-tool:patra-tool-launch:test`
Expected: 全部 PASS

- [ ] **Step 3: shell 测试**

Run: `bash patra-infra/cd/detect-changes.test.sh && bash patra-infra/cd/deploy.test.sh`
Expected: 两个都 `FAIL=0`

- [ ] **Step 4: 本机启动（先问用户，要动 mini）**

按 spec §12.1：征得同意后在 mini 建库（runbook 第 2 步）、`nacos-push.sh --check` 核对差异后推送（runbook 第 3 步）。然后：

```bash
N='localhost|127.*|100.*|192.168.*|172.*|10.*'
export JAVA_TOOL_OPTIONS="-Dhttp.nonProxyHosts=$N -Dhttps.nonProxyHosts=$N -Dsocks.nonProxyHosts=$N"
# 不用设 profile：Nacos 账号由 tool-launch 的 NacosDefaultsPostProcessor 从 ~/.patra/secrets/*.env 自动读
PATRA_INFRA_HOST=100.103.73.27 PATRA_ADVERTISE_IP=100.70.109.37 ./gradlew :patra-api:patra-interaction:patra-interaction-boot:bootRun
```

（后台运行。）Expected：日志有 Flyway 两个脚本、`XXL-Job 执行器初始化完成，appName=patra-interaction … port=6501`；`curl -s 127.0.0.1:6500/actuator/health` 含 `"status":"UP"`；Nacos 控制台或接口里 `public` 下 `patra-interaction` 有 `100.70.109.37:6500` 实例。验证完立即停掉。

- [ ] **Step 5: 勾核对单**

按 spec §10 的表逐条写下证据（命令与输出摘要）到 spec「实施记录」；依赖部署的 #9（mini 部分）、#11、#13（mini 部分）、#14、#15 标「待后端 PR 合进 main 后」。

- [ ] **Step 6: 提交（需用户同意）并告诉用户可以合回版本分支**

```bash
git add docs/patra/specs/2026-10-24-interaction-skeleton-design.md
git commit -m "记录 interaction 骨架的实施与核对结果 (PAP-111)"
```

告诉用户：分支 `claude/v013-interaction-skeleton-f5f6e7` 可以合回 `feat/v0.13-library-api`，PAP-112 随后开工；合并时如 `module-graph.json` 或 `detect-changes.test.sh` 冲突，按 spec §10 开头的说明重新生成。
