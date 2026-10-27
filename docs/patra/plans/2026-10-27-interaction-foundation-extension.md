# interaction 底座扩展实施计划（PAP-120）

> **For agentic workers:** 本计划在当前会话内联执行（superpowers:executing-plans，用户已定，不用 subagent-driven）。Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让底座 `subject` 支撑讨论、投稿经验、提醒三个新子域：期刊对象与本地对象、所有者与匿名标记、能力矩阵、公开的用户名片、发表守卫、公共注销清理、子域依赖规则，并汇总 v0.14 上线 runbook。

**Architecture:** 改动集中在 interaction 的 `subject` 子域（domain / app / infra / adapter 四层），collection 只改 import 和清理参与方；`patra-tool-test` 的 `SubdomainArchitectureRules` 加事件契约与「跨子域依赖必须声明」。持久化继续用 `JdbcClient` 原生 SQL，两条新 Flyway 迁移在 `db/migration/subject/`。

**Tech Stack:** Java 25、Spring Boot 4、Spring JDBC（`JdbcClient`）、PostgreSQL 17、Flyway、ArchUnit、JUnit 5 + AssertJ + Mockito、WireMock、Testcontainers。

**Spec:** `docs/patra/specs/2026-10-27-interaction-foundation-extension-design.md`（下文「spec §N」指它的第 N 节）

**路径缩写**（下文都相对仓库根）：

| 缩写 | 路径 |
|---|---|
| `D` / `DT` | `patra-api/patra-interaction/patra-interaction-domain/src/{main,test}/java/dev/patra/interaction/domain` |
| `A` / `AT` | `patra-api/patra-interaction/patra-interaction-app/src/{main,test}/java/dev/patra/interaction/app` |
| `I` / `IU` / `IIT` | `patra-api/patra-interaction/patra-interaction-infra/src/{main,test,integrationTest}/java/dev/patra/interaction/infra` |
| `MIG` | `patra-api/patra-interaction/patra-interaction-infra/src/main/resources/db/migration/subject` |
| `AD` / `ADT` | `patra-api/patra-interaction/patra-interaction-adapter/src/{main,test}/java/dev/patra/interaction/adapter` |
| `B` / `BT` / `BIT` | `patra-api/patra-interaction/patra-interaction-boot/src/{main,test,integrationTest}/java/dev/patra/interaction` |
| `TT` | `patra-tool/patra-tool-test/src` |
| `G` | `./gradlew` 加 `:patra-api:patra-interaction:patra-interaction-` 前缀，如 `G domain:test` = `./gradlew :patra-api:patra-interaction:patra-interaction-domain:test` |

## Global Constraints

- **不提交、不推送**：全程停在 worktree 本地，用户同意后再统一提交（编排约定）。计划里没有 commit 步骤。
- worktree 里不用 JetBrains MCP；重命名、挪包用 `git mv` + 文本替换，改完 `G <模块>:compileJava compileTestJava` 验证。
- 代码规范：每个方法都写 `///` Javadoc（Markdown）；不写全限定名；record ≤ 4 个参数用 `of()`、≥ 5 个用 `@Builder`；record 的集合字段在紧凑构造器里 `copyOf`；空集合用 `List.of()` 等。测试方法 snake_case `should_…`。
- 跑 `check` 前先 `./gradlew spotlessApply`；`check` 不跑集成测试，集成测试单独跑（`G infra:integrationTest`、`G boot:integrationTest`）。
- Java 文本块会吃行尾空格：`RETURNING """` 写成 `RETURNING\s"""`。Mockito 不要在 `thenReturn(helper())` 里嵌套 `when`。
- spec 定死的值（逐字照抄）：
  - NLM 格式 `[0-9]{1,19}[A-Z]?`（区分大小写）；`post:` 值格式 `[1-9][0-9]{0,18}`；类型编码 `JOURNAL = 2`、`POST = 3`，slug `journal`、`post`；名词 `文献` / `期刊` / `投稿经验`。
  - 能力矩阵：`PUBLICATION {COLLECT, DISCUSS}`、`JOURNAL {SUBMISSION_EXPERIENCE}`、`POST {DISCUSS}`。
  - 发表：`COMMENT`（评论，每天 100，间隔 10 秒）、`POST`（投稿经验，每天 10，无间隔）；「每天」= `Asia/Shanghai` 自然日；advisory lock 键 `subject.publish:{userId}`。
  - 原因码 `SubjectErrorTrait`：`SUBJECT_UNAVAILABLE`、`ACCOUNT_RESTRICTED`、`EMAIL_UNVERIFIED`、`TOO_FREQUENT`、`DAILY_LIMIT_REACHED`。资格 409、限流 429（`HasRetryAfter`）。
  - 文案：`{名词}不存在或已被删除`、`{名词}信息暂时无法获取，请稍后再试`、`账号状态异常，暂时不能发表`、`验证邮箱后才能发表`、`发得太快了，请隔 10 秒再发`、`今天已经发表了 {上限} 条{名词}，明天再来吧`；任务回写 `已核对 %d 人：清理 %d 人，删除 %d 行`。
  - 迁移版本 `20261027120000`（登记表扩展）、`20261027120100`（`subject.publish_quota`）；任务 handler 名 `deletedUserCleanupJob` 不变。

## Review Focus

1. 带前导零、`R` / `A` 后缀、16 位的 NLM ID 从 JSON 到锚、到库 CHECK、再读回，都原样保留（不丢零、不改大小写）→ Task 5 单元测试与 IT、Task 8 迁移 IT、Task 17 流程 IT 都用 `0404401`、`2985213R`。
2. 同一作者卡在上限边上并发发表，只有一个放行 → Task 19 的双线程用例（Task 12 先证明锁会阻塞）。
3. 北京时间 0 点（UTC 16:00）两侧，计数要重置，上限按新的一天算 → Task 11 的日界用例。
4. 在事务外调用 `registerLocal` / `updateLocal` / `admit`，要抛 `IllegalTransactionStateException` 且不写库 → Task 18、Task 19。
5. 迁移前写下的旧行（快照里没有 `abbreviation` 键、所有者两列为空）读回正常 → Task 8 的读适配器 IT。

---

### Task 1: 子域规则：事件契约、底座的新公开包、跨子域依赖必须声明

**Files:**
- Modify: `TT/main/java/dev/patra/tool/test/archunit/SubdomainArchitectureRules.java`
- Modify: `TT/test/java/dev/patra/tool/test/archunit/SubdomainArchitectureRulesTest.java`
- Create（夹具，`TT/test/java/dev/patra/tool/test/archunit/fixtures/subdomain/` 下）：
  - `events/domain/collection/event/ItemCollectedEvent.java`、`events/app/note/OnItemCollected.java`（字段类型是前者）
  - `notereads/domain/collection/port/read/CollectionReadPort.java`、`notereads/app/note/UsesCollectionReadPort.java`
  - `foundationextras/domain/subject/port/user/UserDirectoryPort.java`、`foundationextras/domain/subject/model/user/UserCard.java`、`foundationextras/domain/subject/exception/SubjectUnavailableException.java`、`foundationextras/app/collection/UsesFoundationExtras.java`（三者都用）

**Interfaces:**
- Produces（Task 15 用）：
  - `PUBLISHED = List.of("port.read", "model.read", "event")`；`FOUNDATION_PUBLISHED = List.of("port.gateway", "port.user", "model.vo", "model.enums", "model.user", "exception")`
  - `SubdomainArchitectureRules allowingDependency(String from, String to)`、`SubdomainArchitectureRules allowingSubscription(String from, String to)`：返回新实例；未知名字、`from == to`、`from` 是底座、`to` 是底座都抛 `IllegalArgumentException`
  - `ArchRule crossSubdomainDependenciesAreDeclared()`；`allRules()` 五条

- [ ] **Step 1: 写失败的测试**（`SubdomainArchitectureRulesTest`，沿用 `rules(scenario)` / `evaluate` / `assertViolationNaming`）

```java
@Test void should_publish_event_contract_of_any_subdomain()      // events：规则 25 无违规
@Test void should_accept_declared_subscription()                 // events + allowingSubscription("note","collection")：新规则无违规
@Test void should_reject_undeclared_subscription()               // events 不声明：新规则违规，点名 ItemCollectedEvent
@Test void should_reject_read_port_across_subscription_edge()    // notereads + allowingSubscription：违规，点名 CollectionReadPort
@Test void should_accept_declared_dependency()                   // notereads + allowingDependency("note","collection")：无违规
@Test void should_reject_undeclared_dependency()                 // notereads 不声明：违规，点名 CollectionReadPort
@Test void should_publish_foundation_user_directory_and_exceptions() // foundationextras：规则 25 无违规
@Test void should_accept_compliant_without_declarations()        // compliant：新规则无违规
@Test void should_reject_invalid_edges()                         // allowingDependency("x","note")、("note","note")、("subject","note")、("note","subject") 都 IllegalArgumentException
@Test void should_list_all_five_rules()                          // 改名自 should_list_all_four_rules：hasSize(5)
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-tool:patra-tool-test:test --tests '*SubdomainArchitectureRulesTest*'`
Expected: 编译失败（`allowingDependency` 等不存在）

- [ ] **Step 3: 实现**：两个常量按 Interfaces 改；新增两个不可变的 `Map<String, Set<String>>` 字段（依赖边、订阅边）与私有拷贝构造；新规则的条件：目标子域是底座 → 放行；`from` 声明了对它的依赖边 → 放行；声明了订阅边且目标包是 `{根}.domain.{to}.event` 或其子包 → 放行；其余违规。类注释与两个常量的注释按 spec §11.1、§11.2 改写（说明 `exception` 公开是推翻 PAP-112 的有意决定）。

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :patra-tool:patra-tool-test:test`
Expected: PASS（含原有 13 条）

---

### Task 2: 期刊与帖子的身份锚格式

**Files:**
- Modify: `D/subject/model/enums/NaturalKeyScheme.java`
- Test: `DT/subject/model/vo/NaturalKeyTest.java`

**Interfaces:**
- Produces: `NaturalKeyScheme.NLM`（前缀 `nlm`，`[0-9]{1,19}[A-Z]?`）、`NaturalKeyScheme.POST`（前缀 `post`，`[1-9][0-9]{0,18}`）

- [ ] **Step 1: 写失败的测试**

```java
@ParameterizedTest @ValueSource(strings = {"0404401", "2985213R", "9300367A", "9918523075306676"})
void should_round_trip_nlm_ids(String nlm) {
  assertThat(NaturalKey.parse("nlm:" + nlm)).isEqualTo(NaturalKey.of(NaturalKeyScheme.NLM, nlm));
  assertThat(NaturalKey.of(NaturalKeyScheme.NLM, nlm).toString()).isEqualTo("nlm:" + nlm);
}
@ParameterizedTest @ValueSource(strings = {"2985213r", "R", "", "12345678901234567890", "0404401RR"})
void should_reject_malformed_nlm_ids(String nlm)   // of(NLM, nlm) 抛 IllegalArgumentException
@Test void should_round_trip_post_ids()           // "post:1"、"post:9223372036854775807"
@ParameterizedTest @ValueSource(strings = {"0", "01", "-1"})
void should_reject_malformed_post_ids(String id)
```

- [ ] **Step 2:** Run `G domain:test --tests '*NaturalKeyTest*'`，Expected: FAIL（`NLM` 不存在）
- [ ] **Step 3:** 加两个常量（注释写明 mini 上的写法：前导零、`R` / `A` 后缀、16 位）
- [ ] **Step 4:** Run 同上，Expected: PASS

---

### Task 3: 快照加期刊缩写

**Files:**
- Modify: `D/subject/model/vo/SubjectSnapshot.java`
- Modify（四参数 `of()` 的调用方）：`I/subject/source/PublicationSubjectSourceAdapter.java`、以及 `grep -rln "SubjectSnapshot.of" patra-api/patra-interaction` 列出的全部测试
- Test: `DT/subject/model/vo/SubjectSnapshotTest.java`

**Interfaces:**
- Produces: `record SubjectSnapshot(String title, String journal, String publishedDate, String abbreviation)`；`static SubjectSnapshot of(String title, String journal, String publishedDate, String abbreviation)`

- [ ] **Step 1: 写失败的测试**：`should_keep_optional_abbreviation()`：`SubjectSnapshot.of("Lancet (London, England)", null, null, "Lancet").abbreviation()` 是 `"Lancet"`；四项全为 null 也能建
- [ ] **Step 2:** Run `G domain:test --tests '*SubjectSnapshotTest*'`，Expected: 编译失败
- [ ] **Step 3:** 加字段与四参数 `of()`，类注释写每种类型各字段的含义（spec §4.4 表）；所有调用方补第四个参数 `null`
- [ ] **Step 4:** Run `G domain:test G app:test G infra:test`（三个模块），Expected: PASS

---

### Task 4: 消息按类型给名词，「对象已失效」原因码

**Files:**
- Modify: `D/subject/model/enums/SubjectType.java`（加 `displayName`，`PUBLICATION` 为 `文献`）
- Create: `D/subject/exception/SubjectErrorTrait.java`
- Modify: `D/subject/exception/SubjectNotFoundException.java`、`D/subject/exception/SubjectSourceUnavailableException.java`
- Modify: `A/subject/service/SubjectGatewayImpl.java`、`I/subject/source/PublicationSubjectSourceAdapter.java`
- Test: `DT/subject/exception/SubjectExceptionsTest.java`、`DT/subject/model/enums/SubjectTypeTest.java`、`AT/subject/service/SubjectGatewayImplTest.java`，以及构造这两个异常的其他测试

**Interfaces:**
- Produces:
  - `String SubjectType.displayName()`
  - `enum SubjectErrorTrait implements ErrorTrait { SUBJECT_UNAVAILABLE }`（Task 11 再加四个）
  - `static SubjectNotFoundException notFound(SubjectType type, String key)`（只带 `NOT_FOUND`）、`static SubjectNotFoundException unavailable(SubjectType type, String key)`（`NOT_FOUND` + `SUBJECT_UNAVAILABLE`）；构造器私有；消息 `type.displayName() + "不存在或已被删除"`
  - `SubjectSourceUnavailableException(SubjectType type, Throwable cause)`；消息 `type.displayName() + "信息暂时无法获取，请稍后再试"`；`@Getter type`

- [ ] **Step 1: 写失败的测试**

```java
// SubjectExceptionsTest
@Test void should_mark_not_found_without_reason()  // notFound(PUBLICATION,"100")：traits 只有 NOT_FOUND，消息「文献不存在或已被删除」
@Test void should_mark_unavailable_with_reason()   // unavailable(PUBLICATION,"100")：traits = {NOT_FOUND, SUBJECT_UNAVAILABLE}
@Test void should_mark_source_unavailable_as_dependency_unavailable() // 改成 (PUBLICATION, cause)，消息不变
// SubjectGatewayImplTest
@Test void should_reject_local_hit_that_is_unavailable()  // 原用例改断言：SUBJECT_UNAVAILABLE 在 getErrorTraits() 里
@Test void should_reject_soft_deleted_source_as_unavailable() // 来源返回 DELETED：同上
@Test void should_reject_missing_source_as_not_found()        // 来源返回空：不带 SUBJECT_UNAVAILABLE
```

- [ ] **Step 2:** Run `G domain:test G app:test`，Expected: 编译失败
- [ ] **Step 3:** 实现；`SubjectGatewayImpl` 里本地命中 UNAVAILABLE、来源返回 DELETED 用 `unavailable`，其余用 `notFound`；`PublicationSubjectSourceAdapter` 抛异常时传 `SubjectType.PUBLICATION`
- [ ] **Step 4:** Run `G domain:test G app:test G infra:test`，Expected: PASS

---

### Task 5: 期刊类型与期刊来源策略

**Files:**
- Modify: `D/subject/model/enums/SubjectType.java`（加 `JOURNAL`）、`D/subject/model/enums/SubjectCapability.java`（加 `DISCUSS`、`SUBMISSION_EXPERIENCE`）
- Create: `I/subject/source/CatalogSourceCalls.java`、`I/subject/source/JournalSubjectSourceAdapter.java`
- Modify: `I/subject/source/PublicationSubjectSourceAdapter.java`（改用 `CatalogSourceCalls`）、`B/config/CatalogClientConfiguration.java`、`IIT/InteractionITBootstrap.java`（加 mock `VenueEndpoint`）
- Test: `IU/subject/source/JournalSubjectSourceAdapterTest.java`（新）、`IIT/subject/source/JournalSubjectSourceAdapterIT.java`（新）、`DT/subject/model/enums/SubjectTypeTest.java`
- Modify（因为加了 `JOURNAL` 必须跟着改的测试）：`AT/subject/service/SubjectSourcesTest.java`、`AT/subject/service/SubjectGatewayImplTest.java`、`AT/subject/usecase/reconcile/ReconcileSubjectsHandlerTest.java`（构造 `SubjectSources` 时多给一个 `JOURNAL` 的 mock 来源）；`DT/.../SubjectTypeTest.should_return_empty_for_unknown_slug` 与 `AT/collection/usecase/library/CollectItemHandlerTest.should_reject_unknown_type` 把当作「未知」的 `"journal"` 换成 `"unknown"`

**Interfaces:**
- Consumes: Task 2 的 `NLM`、Task 3 的四参数快照、Task 4 的 `SubjectSourceUnavailableException(type, cause)`
- Produces:
  - `SubjectType.JOURNAL((short) 2, "journal", "期刊", NaturalKeyScheme.NLM, EnumSet.of(SUBMISSION_EXPERIENCE))`；`PUBLICATION` 的能力改为 `{COLLECT, DISCUSS}`
  - `final class CatalogSourceCalls { static <T> T call(SubjectType type, Supplier<T> request, int keyCount) }`（包内可见）
  - `JournalSubjectSourceAdapter implements SubjectSourcePort`（构造参数 `VenueEndpoint`）
  - `@Bean VenueEndpoint venueEndpoint(RestClient catalogRestClient, RestClientFactory factory)`

- [ ] **Step 1: 写失败的测试**

```java
// JournalSubjectSourceAdapterTest（mock VenueEndpoint）
@Test void should_anchor_first_resolution_on_primary_nlm()
  // resolveByIds 回 {key:"100", exists, ACTIVE, id:100, nlmId:"0404401", title:"Schweizerische medizinische Wochenschrift", abbreviatedTitle:"Swiss Med Wkly"}
  // → SourceRecord(100, nlm:0404401, ACTIVE, snapshot(title, null, null, "Swiss Med Wkly"))
@Test void should_anchor_reconciliation_on_requested_key_not_primary_nlm()
  // resolveByNlmIds(["0404401"]) 回 {key:"0404401", id:200, nlmId:"100970884"} → 映射键与 naturalKey 都是 nlm:0404401，sourceId 200
@Test void should_drop_missing_rows_and_rows_without_anchor()  // exists=false → 无记录；by-ids 的 nlmId=null → 空
@Test void should_map_soft_deleted_state()                       // DELETED → SourceState.DELETED
@Test void should_translate_failures_to_source_unavailable()     // RemoteCallException / ResourceAccessException / IllegalStateException，异常的 getType() 是 JOURNAL
@Test void should_reject_batches_outside_1_to_100()              // IllegalArgumentException
// SubjectTypeTest
@Test void should_define_journal()  // 编码 2、slug journal、名词 期刊、NLM、只支持 SUBMISSION_EXPERIENCE；fromSlug("journal")
@Test void should_support_collect_and_discuss_for_publication()
// JournalSubjectSourceAdapterIT（照 PublicationSubjectSourceAdapterIT 手工建客户端）
@Test void should_post_ids_and_read_string_id_back()             // 请求体 {"ids":[100]}；"id":"100" 读回 100L；nlmId "0404401" 原样
@Test void should_post_nlm_ids_as_strings()                      // 请求体 {"nlmIds":["0404401","2985213R"]}
@Test void should_translate_503_and_refused_connection()
```

- [ ] **Step 2:** Run `G infra:test --tests '*JournalSubjectSourceAdapterTest*'`，Expected: 编译失败
- [ ] **Step 3:** 实现（spec §5.1）；`CatalogSourceCalls` 收下两个适配器原有的 try/catch 与日志（日志带 `type`）；`CatalogClientConfiguration` 用同一个 `catalogRestClient` 再 `createProxy(VenueEndpoint.class)`，类注释补「期刊」
- [ ] **Step 4:** Run `G domain:test G app:test G infra:test`，然后 `G infra:integrationTest --tests '*SubjectSourceAdapterIT*'`，Expected: PASS

---

### Task 6: 本地类型 `POST`、只对外部类型做启动校验和对账

**Files:**
- Create: `D/subject/model/enums/SubjectOrigin.java`
- Modify: `D/subject/model/enums/SubjectType.java`、`A/subject/service/SubjectSources.java`、`A/subject/usecase/reconcile/ReconcileSubjectsHandler.java`、`A/subject/service/SubjectGatewayImpl.java`
- Test: `DT/.../SubjectTypeTest.java`、`AT/subject/service/SubjectSourcesTest.java`、`AT/.../ReconcileSubjectsHandlerTest.java`、`AT/subject/service/SubjectGatewayImplTest.java`

**Interfaces:**
- Produces:
  - `enum SubjectOrigin { EXTERNAL, LOCAL }`
  - `SubjectType` 构造参数改为 `(short code, String slug, String displayName, SubjectOrigin origin, NaturalKeyScheme scheme, EnumSet<SubjectCapability> capabilities)`；`POST((short) 3, "post", "投稿经验", LOCAL, NaturalKeyScheme.POST, EnumSet.of(DISCUSS))`
  - `SubjectOrigin origin()`、`boolean isLocal()`、`static List<SubjectType> externalTypes()`（按声明顺序：`PUBLICATION`、`JOURNAL`）

- [ ] **Step 1: 写失败的测试**

```java
// SubjectTypeTest
@Test void should_define_post_as_local()          // 3 / post / 投稿经验 / LOCAL / 只支持 DISCUSS
@Test void should_list_only_external_types()      // externalTypes() containsExactly(PUBLICATION, JOURNAL)
// SubjectSourcesTest
@Test void should_not_require_port_for_local_type()      // 只给文献与期刊两个来源：不抛
@Test void should_fail_when_local_type_has_port()        // 给 POST 配来源：IllegalStateException，消息含 POST
@Test void should_fail_when_external_type_has_no_port()  // 缺 JOURNAL：消息含 JOURNAL
// ReconcileSubjectsHandlerTest
@Test void should_never_page_local_types()        // verify(repository, never()).findPage(eq(SubjectType.POST), anyLong(), anyInt())
// SubjectGatewayImplTest
@Test void should_reject_unregistered_local_object_without_any_source()
  // resolveForInteraction(POST,"7",DISCUSS)：本地查不到 → notFound（不带 SUBJECT_UNAVAILABLE），来源 mock 零交互
```

- [ ] **Step 2:** Run `G domain:test G app:test`，Expected: FAIL
- [ ] **Step 3:** 实现（spec §6.3、§6.4）；`SubjectSources` 声明为 `final`（已是）
- [ ] **Step 4:** Run 同上，Expected: PASS

---

### Task 7: 所有者与匿名标记（领域模型）

**Files:**
- Create: `D/subject/model/vo/SubjectOwner.java`
- Modify: `D/subject/model/aggregate/SubjectAggregate.java`、`D/subject/model/read/SubjectView.java`
- Test: `DT/subject/model/aggregate/SubjectAggregateTest.java`

**Interfaces:**
- Produces:
  - `record SubjectOwner(long userId, boolean anonymous)`；`static SubjectOwner of(long userId, boolean anonymous)`
  - `SubjectAggregate` 加字段 `SubjectOwner owner`（`@Builder` 参数 `owner`，可空）；不变量：`type.isLocal() == (owner != null)`，否则 `IllegalStateException`；`reconcile` 遇到本地对象抛 `IllegalStateException`
  - `SubjectView` 加组件 `SubjectOwner owner`（外部对象为 null）

- [ ] **Step 1: 写失败的测试**

```java
@Test void should_require_owner_for_local_subject()        // POST 不给 owner：IllegalStateException
@Test void should_forbid_owner_for_external_subject()      // PUBLICATION 给 owner：IllegalStateException
@Test void should_keep_owner_of_local_subject()            // POST + SubjectOwner.of(9L, true)：getOwner() 等于它
@Test void should_refuse_to_reconcile_local_subject()      // POST.reconcile(...)：IllegalStateException
```

- [ ] **Step 2:** Run `G domain:test --tests '*SubjectAggregateTest*'`，Expected: 编译失败
- [ ] **Step 3:** 实现
- [ ] **Step 4:** Run `G domain:test G app:test G infra:test`，Expected: PASS

---

### Task 8: 登记表迁移与持久化

**Files:**
- Create: `MIG/V20261027120000__extend_registry_for_journal_and_post.sql`（spec §10.1 全文 + 每列 COMMENT）
- Modify: `I/subject/persistence/SubjectRows.java`、`I/subject/persistence/SubjectRepositoryAdapter.java`、`I/subject/persistence/SubjectReadAdapter.java`、`D/subject/port/repository/SubjectRepository.java`
- Test: `IIT/InteractionMigrationIT.java`、`IIT/subject/persistence/SubjectRepositoryAdapterIT.java`、`IIT/subject/persistence/SubjectReadAdapterIT.java`

**Interfaces:**
- Consumes: Task 7 的 `SubjectOwner`、聚合的 `owner`
- Produces:
  - `SubjectAggregate insertLocal(SubjectType type, long localId, SubjectOwner owner, SubjectSnapshot snapshot, Instant now)`：`natural_key` 由 `NaturalKey.of(type.naturalKeyScheme(), Long.toString(localId))` 生成，`source_id = localId`，`ACTIVE`，`INSERT … RETURNING`
  - `boolean updateLocal(SubjectType type, long localId, SubjectState state, SubjectSnapshot snapshot, Instant now)`：只写 `state`、`snapshot`、`snapshot_at`，按 `target_type` + `source_id` 定位，返回是否有这一行
  - `SubjectRows.COLUMNS` 多两列；读回时 `owner_user_id` 为空则 owner 为 null

- [ ] **Step 1: 写失败的测试**

```java
// InteractionMigrationIT
@Test void should_create_one_schema_per_subdomain()
  // 改：contains("public","subject","collection") 且 isSubsetOf("public","subject","collection","discussion","post","notification")
@Test void should_record_migrations_with_timestamp_versions()
  // 改：全部 success；allMatch [0-9]{14}；isSorted()；contains 现有四个 + "20261027120000"
@Test void should_enforce_registry_checks()   // 只把未知编码 2 换成 99，其余不动
@Test void should_enforce_journal_post_and_owner_checks()
  // 类型 2：nlm:0404401、nlm:2985213R 能插；nlm:2985213r、pmid:1 被拒
  // 类型 3：post:7 + source_id 7 + 所有者能插；post:7 + source_id 8 被拒；source_id 为空被拒；缺所有者被拒
  // 类型 1 带所有者被拒；owner_user_id 有值而 owner_anonymous 为空被拒
// SubjectRepositoryAdapterIT
@Test void should_insert_local_subject_with_owner()         // natural_key "post:7"、source_id 7、ACTIVE、owner (9,true)
@Test void should_update_local_state_and_snapshot_only()    // UNAVAILABLE + 新快照；owner 不变；snapshot_at = now
@Test void should_report_missing_local_subject()            // updateLocal 不存在的 → false
@Test void should_not_touch_owner_columns_when_upserting_external() // 已有 upsert 用例补断言 owner 为 null
// SubjectReadAdapterIT
@Test void should_return_owner_in_views()                   // findByIds 带 owner；外部对象 owner 为 null
@Test void should_find_local_ids_by_key()                   // findIdsByKeys(POST, ["7"]) → subjectId
@Test void should_read_rows_written_before_migration()      // 手工插 snapshot '{"title":"T","journal":"J","publishedDate":"2019"}'：abbreviation 为 null
```

- [ ] **Step 2:** Run `G infra:integrationTest --tests '*InteractionMigrationIT*' --tests '*SubjectRepositoryAdapterIT*' --tests '*SubjectReadAdapterIT*'`，Expected: FAIL
- [ ] **Step 3:** 写迁移与适配器（`ck_registry_post_key` 括号里先卡正则再比 `'post:' || source_id`，spec §10.1）
- [ ] **Step 4:** Run 同上，再 `G infra:integrationTest` 全量，Expected: PASS

---

### Task 9: 网关登记与更新本地对象

**Files:**
- Modify: `D/subject/port/gateway/SubjectGateway.java`、`A/subject/service/SubjectGatewayImpl.java`
- Test: `AT/subject/service/SubjectGatewayImplTest.java`

**Interfaces:**
- Consumes: Task 8 的 `insertLocal`、`updateLocal`
- Produces（PAP-122 用）：
  - `SubjectId registerLocal(SubjectType type, long localId, SubjectOwner owner, SubjectSnapshot snapshot)`
  - `void updateLocal(SubjectType type, long localId, SubjectState state, SubjectSnapshot snapshot)`
  - 两个实现方法都标 `@Transactional(propagation = Propagation.MANDATORY)`；`type` 非本地 → `IllegalArgumentException`；`updateLocal` 返回 false → `IllegalStateException`

- [ ] **Step 1: 写失败的测试**

```java
@Test void should_register_local_subject_at_clock_now()   // verify(repository).insertLocal(POST, 7L, owner, snapshot, NOW)，返回它的 subjectId
@Test void should_update_local_subject()                  // verify(repository).updateLocal(POST, 7L, UNAVAILABLE, snapshot, NOW)
@Test void should_reject_external_type_for_local_methods() // registerLocal(PUBLICATION,…)、updateLocal(JOURNAL,…)：IllegalArgumentException，仓储零交互
@Test void should_fail_when_updating_unregistered_local()  // updateLocal 返回 false：IllegalStateException
```

- [ ] **Step 2:** Run `G app:test --tests '*SubjectGatewayImplTest*'`，Expected: 编译失败
- [ ] **Step 3:** 实现；接口 Javadoc 写明「须在调用方的事务里调用」（spec §6.2）
- [ ] **Step 4:** Run 同上，Expected: PASS（MANDATORY 在 Task 18 的 boot IT 里验证）

---

### Task 10: 用户名片挪进底座

**Files:**
- Move（`git mv`，只改包名）：
  - `D/collection/port/user/UserDirectoryPort.java` → `D/subject/port/user/`
  - `D/collection/model/user/UserCard.java`、`AccountState.java` → `D/subject/model/user/`
  - `D/collection/exception/UserDirectoryUnavailableException.java` → `D/subject/exception/`
  - `I/collection/user/UserDirectoryAdapter.java` → `I/subject/user/`；`IU/collection/user/UserDirectoryAdapterTest.java` → `IU/subject/user/`
- Modify（只改 import）：`A/collection/service/CollectionAccess.java`、`ReadableCollection.java`、`A/collection/usecase/collection/SetVisibilityHandler.java`、`A/collection/usecase/query/PublicCollectionView.java`、`PublicCollectionQueryService.java`、`A/collection/usecase/cleanup/CleanupDeletedUsersHandler.java`（Task 14 删除）、`AD/collection/rest/response/OwnerResponse.java`，以及 `AT/collection/**` 里引用它们的测试
- Modify: `DT/collection/exception/CollectionExceptionsTest.java`（`UserDirectoryUnavailableException` 一行挪到 `DT/subject/exception/SubjectExceptionsTest.java`）；`D/subject/package-info.java`、`A/subject/package-info.java`、`I/subject/package-info.java` 的公开包说明

**Interfaces:**
- Produces: `dev.patra.interaction.domain.subject.port.user.UserDirectoryPort`、`dev.patra.interaction.domain.subject.model.user.{UserCard, AccountState}`、`dev.patra.interaction.domain.subject.exception.UserDirectoryUnavailableException`，签名不变

- [ ] **Step 1:** 重构前跑一遍：`G domain:test G app:test G infra:test G adapter:test`，Expected: PASS
- [ ] **Step 2:** `git mv` + 包名与 import 替换；`grep -rn "collection.model.user\|collection.port.user\|collection.exception.UserDirectoryUnavailableException\|infra.collection.user" patra-api/patra-interaction` 结果为空
- [ ] **Step 3:** Run `G domain:test G app:test G infra:test G adapter:test G boot:test`，Expected: PASS（`InteractionArchitectureTest` 也过：Task 1 已公开 `port.user`、`model.user`、`exception`）

---

### Task 11: 发表守卫的规则与异常

**Files:**
- Create: `D/subject/model/enums/PublishKind.java`、`D/subject/policy/PublishQuota.java`、`D/subject/exception/PublishIneligibleException.java`、`D/subject/exception/PublishRateLimitedException.java`
- Modify: `D/subject/exception/SubjectErrorTrait.java`（加四个值）
- Test: `DT/subject/policy/PublishQuotaTest.java`（新）、`DT/subject/exception/SubjectExceptionsTest.java`

**Interfaces:**
- Produces:
  - `enum PublishKind { COMMENT("评论", 100, Duration.ofSeconds(10)), POST("投稿经验", 10, Duration.ZERO); String displayName(); int dailyLimit(); Duration minInterval(); }`
  - `record PublishQuota(LocalDate day, int dayCount, Instant lastPublishedAt)`；`static final ZoneId ZONE = ZoneId.of("Asia/Shanghai")`；`static PublishQuota admit(Optional<PublishQuota> current, PublishKind kind, Instant now, ZoneId zone)`（放行返回新值，拒绝抛 `PublishRateLimitedException`）
  - `PublishIneligibleException.accountRestricted()`、`emailUnverified()`：`CONFLICT` + 原因码
  - `PublishRateLimitedException.tooFrequent(PublishKind kind, Duration retryAfter)`、`dailyLimitReached(PublishKind kind, Duration retryAfter)`：`QUOTA_EXCEEDED` + 原因码，实现 `HasRetryAfter`，`@Getter kind`

- [ ] **Step 1: 写失败的测试**（`PublishQuotaTest`，时区参数一律传 `PublishQuota.ZONE`）

```java
static final Instant NOW = Instant.parse("2026-10-27T08:00:00Z");            // 北京 16:00
@Test void should_admit_first_publish()               // admit(empty, COMMENT, NOW) == new PublishQuota(2026-10-27, 1, NOW)
@Test void should_admit_hundredth_comment_and_reject_next()
  // current (2026-10-27, 99, NOW-1h) → dayCount 100；current (…,100,…) → dailyLimitReached，retryAfter = Duration.between(NOW, 2026-10-27T16:00:00Z) = 8h
@Test void should_reset_count_after_beijing_midnight()
  // current (2026-10-27, 100, 2026-10-27T15:59:59Z)，now 2026-10-27T16:00:00Z（北京 28 日 0 点）→ (2026-10-28, 1, now)
@Test void should_reject_comment_within_ten_seconds()
  // last = NOW - 9.999s → tooFrequent，retryAfter 1ms；last = NOW - 10s → 放行
@Test void should_ignore_interval_for_posts()          // POST，last = NOW - 1s → 放行
@Test void should_check_daily_limit_before_interval()  // COMMENT (100, last=NOW-1s) → DAILY_LIMIT_REACHED 而不是 TOO_FREQUENT
@Test void should_cap_posts_at_ten_per_day()           // POST (10, …) → dailyLimitReached，消息「今天已经发表了 10 条投稿经验，明天再来吧」
// SubjectExceptionsTest
@Test void should_mark_ineligible_as_conflict_with_reasons()   // 两种原因码与文案（Global Constraints）
@Test void should_mark_rate_limited_with_retry_after()        // traits {QUOTA_EXCEEDED, TOO_FREQUENT}；getRetryAfterSeconds()；文案「发得太快了，请隔 10 秒再发」
```

- [ ] **Step 2:** Run `G domain:test --tests '*PublishQuotaTest*' --tests '*SubjectExceptionsTest*'`，Expected: 编译失败
- [ ] **Step 3:** 实现（规则见 spec §8.3 伪代码；`today = LocalDate.ofInstant(now, zone)`，明天 0 点 = `today.plusDays(1).atStartOfDay(zone).toInstant()`）
- [ ] **Step 4:** Run 同上，Expected: PASS

---

### Task 12: 发表计数表与仓储

**Files:**
- Create: `MIG/V20261027120100__create_publish_quota.sql`（spec §10.2 + 每列 COMMENT）、`D/subject/port/repository/PublishQuotaRepository.java`、`I/subject/persistence/PublishQuotaRepositoryAdapter.java`
- Test: `IIT/subject/persistence/PublishQuotaRepositoryAdapterIT.java`（新，继承 `SubjectRegistryITSupport`，`@BeforeEach` 再清 `subject.publish_quota`）、`IIT/InteractionMigrationIT.java`

**Interfaces:**
- Produces:
  - `void lock(long userId)`（`pg_advisory_xact_lock(hashtextextended('subject.publish:' || userId, 0))`）
  - `Optional<PublishQuota> find(long userId, PublishKind kind)`
  - `void save(long userId, PublishKind kind, PublishQuota quota)`（`ON CONFLICT (user_id, kind) DO UPDATE`）
  - `List<Long> findUsersAfter(long afterUserId, int limit)`（`DISTINCT user_id … ORDER BY user_id LIMIT`）
  - `int deleteAllOf(long userId)`

- [ ] **Step 1: 写失败的测试**

```java
@Test void should_round_trip_quota()                 // save 后 find 得到同一个值；再 save 覆盖
@Test void should_keep_kinds_apart()                 // 同一用户 COMMENT 与 POST 各一行
@Test void should_block_on_publish_lock_held_elsewhere() // 照 LibraryRepositoryAdapterIT.should_block_on_owner_lock_held_elsewhere，键 "subject.publish:" + U
@Test void should_list_users_after_cursor_and_delete_all_of_one() // 三个用户：findUsersAfter(0,2) = [1,2]；deleteAllOf(1) = 2 行
// InteractionMigrationIT
@Test void should_enforce_publish_quota_checks()     // kind 'LIKE' 被拒；day_count 0 被拒
// should_record_migrations_with_timestamp_versions 的 contains 补 "20261027120100"
```

- [ ] **Step 2:** Run `G infra:integrationTest --tests '*PublishQuotaRepositoryAdapterIT*' --tests '*InteractionMigrationIT*'`，Expected: FAIL
- [ ] **Step 3:** 实现
- [ ] **Step 4:** Run 同上，Expected: PASS

---

### Task 13: 发表守卫网关

**Files:**
- Create: `D/subject/port/gateway/PublishGuardGateway.java`、`A/subject/service/PublishGuardGatewayImpl.java`
- Test: `AT/subject/service/PublishGuardGatewayImplTest.java`（新）

**Interfaces:**
- Consumes: Task 10 的 `UserDirectoryPort`、`UserCard`；Task 11 的 `PublishQuota`、异常；Task 12 的 `PublishQuotaRepository`
- Produces（PAP-121 / 122 用）：`UserCard requireEligible(long userId)`、`void admit(long userId, PublishKind kind)`（实现方法标 `@Transactional(propagation = Propagation.MANDATORY)`）；实现的构造参数 `(UserDirectoryPort users, PublishQuotaRepository quotas, Clock clock)`

- [ ] **Step 1: 写失败的测试**

```java
@Test void should_return_own_card_when_eligible()      // findCards([7],7) 回 ACTIVE + 已验证 → 返回该卡片
@ParameterizedTest @EnumSource(value = AccountState.class, names = {"BANNED","PENDING_DELETION","DELETED"})
void should_reject_restricted_account(AccountState state)   // ACCOUNT_RESTRICTED（哪怕邮箱已验证）
@Test void should_reject_when_identity_omits_user()    // 空结果 → ACCOUNT_RESTRICTED
@Test void should_reject_unverified_email()            // ACTIVE + 未验证 → EMAIL_UNVERIFIED
@Test void should_propagate_directory_unavailable()
@Test void should_lock_then_read_then_save()           // InOrder：lock(7) → find(7,COMMENT) → save(7,COMMENT,新值)
@Test void should_not_save_when_rejected()             // find 回 (今天,100,…) → 抛 DAILY_LIMIT_REACHED，never().save
```

- [ ] **Step 2:** Run `G app:test --tests '*PublishGuardGatewayImplTest*'`，Expected: 编译失败
- [ ] **Step 3:** 实现（spec §8.1、§8.2）
- [ ] **Step 4:** Run 同上，Expected: PASS

---

### Task 14: 注销清理改为底座的公共流程

**Files:**
- Create: `D/subject/port/gateway/UserDataCleanupGateway.java`；`A/subject/usecase/cleanup/CleanupDeletedUsersCommand.java`、`CleanupDeletedUsersHandler.java`、`CleanupResult.java`；`A/subject/service/SubjectUserDataCleanupGatewayImpl.java`；`A/collection/service/CollectionUserDataCleanupGatewayImpl.java`
- Delete: `A/collection/usecase/cleanup/` 下三个类与 `AT/collection/usecase/cleanup/CleanupDeletedUsersHandlerTest.java`
- Move: `AD/collection/scheduler/DeletedUserCleanupScheduleJob.java` → `AD/subject/scheduler/`（及其测试）
- Modify: `BIT/collection/CollectionFlowIT.java`（**只改**清理命令的 import）
- Test: `AT/subject/usecase/cleanup/CleanupDeletedUsersHandlerTest.java`、`AT/collection/service/CollectionUserDataCleanupGatewayImplTest.java`、`AT/subject/service/SubjectUserDataCleanupGatewayImplTest.java`、`ADT/subject/scheduler/DeletedUserCleanupScheduleJobTest.java`

**Interfaces:**
- Produces:
  - `interface UserDataCleanupGateway { String subdomain(); List<Long> findUsersAfter(long afterUserId, int limit); int deleteAllOf(long userId); }`
  - `record CleanupResult(int scannedUsers, int deletedUsers, int deletedRows, int failedBatches)`，`of(...)`
  - `CleanupDeletedUsersHandler(List<UserDataCleanupGateway> participants, UserDirectoryPort users, TransactionOperations transactions)`；`static final int BATCH = UserDirectoryPort.MAX_USERS`
  - `CollectionUserDataCleanupGatewayImpl(LibraryReadPort reads, LibraryRepository library)`，`subdomain()` = `"collection"`；`SubjectUserDataCleanupGatewayImpl(PublishQuotaRepository quotas)`，`subdomain()` = `"subject"`

- [ ] **Step 1: 写失败的测试**

```java
// CleanupDeletedUsersHandlerTest（两个 mock 参与方 P、Q；事务用记录边界的 TransactionOperations）
@Test void should_merge_candidates_across_participants()
  // P.findUsersAfter(0,100)=[1,3]，Q=[2,3]；findCards([1,2,3],null) 调用一次；第二轮游标 3
@Test void should_take_smallest_hundred_when_participants_overflow()
  // P=[1..100]，Q=[50..149] → 第一批 [1..100]，第二轮 findUsersAfter(100,100)
@Test void should_delete_deleted_users_in_every_participant_each_in_own_transaction()
  // 1 DELETED、2 ACTIVE、3 省略 → 只有 1：P.deleteAllOf(1)、Q.deleteAllOf(1)，各自在一对 tx-begin / tx-end 之间；deletedRows 相加
@Test void should_skip_batch_when_identity_down_and_continue()
@Test void should_do_nothing_without_candidates()
// CollectionUserDataCleanupGatewayImplTest
@Test void should_lock_owner_before_deleting()  // InOrder lockOwner(1) → deleteAllOf(1)；返回 DeletedUserData.of(5,2,3) 的和 10
@Test void should_list_owners_from_library()    // 委托 findOwnersAfter
// SubjectUserDataCleanupGatewayImplTest：委托 PublishQuotaRepository 的两个方法
// DeletedUserCleanupScheduleJobTest：成功文案「已核对 12 人：清理 2 人，删除 41 行」，其余两例不变
```

- [ ] **Step 2:** Run `G app:test G adapter:test`，Expected: 编译失败
- [ ] **Step 3:** 实现（spec §9.2 伪代码；合并用 `TreeSet`；INFO 汇总行按子域列行数，`TreeMap`）；删掉 collection 的三个类；挪任务并改成功文案
- [ ] **Step 4:** Run `G app:test G adapter:test G boot:test`，再 `G boot:integrationTest --tests '*CollectionFlowIT*'`，Expected: PASS；`git diff` 里 `CollectionFlowIT.java` 只有一行 import 变化

---

### Task 15: interaction 的架构测试

**Files:**
- Modify: `BT/architecture/InteractionArchitectureTest.java`

**Interfaces:**
- Consumes: Task 1 的 `allowingSubscription`、`crossSubdomainDependenciesAreDeclared()`

- [ ] **Step 1: 改测试**：
  - 常量 `SUBDOMAINS = List.of("subject", "collection", "discussion", "post", "notification")`、`FOUNDATION = "subject"`；规则 `new SubdomainArchitectureRules("dev.patra.interaction", FOUNDATION, Set.copyOf(SUBDOMAINS)).allowingSubscription("notification", "discussion")`
  - 规则 22b 改成自推导（spec §11.4）：底座四层必须都在；其余子域任一层出现就要求四层都在；`@DisplayName("规则 22b: 底座四层都在；其余子域出现在任一层时四层都在")`
  - 新增 `@Test @DisplayName("规则 27: 跨子域依赖必须声明") void crossSubdomainDependenciesShouldBeDeclared()`
- [ ] **Step 2:** Run `G boot:test --tests '*InteractionArchitectureTest*'`，Expected: PASS
- [ ] **Step 3: 确认规则生效**：临时在 `A/collection/service/CollectionAccess.java` 加一个 `SubjectRepository` 字段，规则 25 应该报红；删掉后恢复。再临时建 `A/notification/Probe.java` 引用 `domain.collection.port.read.LibraryReadPort`，规则 27 应该报红（并且 22b 要求 notification 四层都在）；删掉后恢复

---

### Task 16: 流程集成测试的共用底座

**Files:**
- Move: `BIT/collection/FakeCatalog.java`、`BIT/collection/FakeIdentity.java` → `BIT/support/`（改为 `public final`，方法 `public`）
- Create: `BIT/support/InteractionFlowITSupport.java`
- Modify: `BIT/collection/CollectionFlowITSupport.java`（继承前者，只留 `collect`、`createCollection`、`addToCollection`、`publish`）

**Interfaces:**
- Produces（Task 18 ~ 20 与 PAP-121 / 122 / 123 用）：`public abstract class InteractionFlowITSupport`：注解同现在的 `CollectionFlowITSupport`；`protected static final JsonMapper JSON`、`FakeCatalog CATALOG_DATA`、`FakeIdentity IDENTITY_DATA`；两个 `@RegisterExtension static WireMockExtension catalog, identity`；`@DynamicPropertySource`；`@Autowired protected RestTestClient restClient; JdbcTemplate jdbcTemplate`；`@BeforeEach resetWorld()`：`TRUNCATE` 从 `pg_tables` 查出的全部非 `public`、非系统 schema 的表（`quote_ident`），清空两个假服务并把任意请求交给转换器；`call` / `status` / `json` / `count` 照搬

- [ ] **Step 1:** 改动前跑 `G boot:integrationTest --tests '*CollectionFlowIT*'`，Expected: PASS
- [ ] **Step 2:** 挪类、抽底座
- [ ] **Step 3:** Run `G boot:integrationTest`，Expected: PASS（`CollectionFlowIT` 断言未动）

---

### Task 17: 期刊登记与对账的流程测试（AC：重导换 ID 后仍指向同一刊）

**Files:**
- Modify: `BIT/subject/SubjectRegistryFlowIT.java`（加期刊用例与两个桩辅助方法 `stubVenueById`、`stubVenuesByNlm`）

- [ ] **Step 1: 写测试**（先跑会红：期刊用例依赖的桩辅助方法不存在）

```java
@Test void should_anchor_journal_on_nlm_and_follow_reimport()
  // by-ids 100 → nlmId "0404401" → resolveForInteraction(JOURNAL,"100",SUBMISSION_EXPERIENCE) 得 S
  // 库里 natural_key = "nlm:0404401"
  // 改桩：by-nlm-ids ["0404401"] → key "0404401"、id 200、nlmId "100970884"（副 NLM）→ 对账
  // S 的 natural_key 仍是 "nlm:0404401"、key "200"；findIdsByKeys(JOURNAL,["200"]) = S；按 "200" 解析直接得 S 且 catalog 零请求
@Test void should_reject_journal_that_is_missing_soft_deleted_or_without_nlm()
  // exists=false → notFound；DELETED → 带 SUBJECT_UNAVAILABLE；nlmId=null → notFound；三种都不建行
@Test void should_mark_vanished_journal_unavailable()     // 对账 exists=false：UNAVAILABLE、key 为 null、快照保留
@Test void should_reject_collecting_a_journal()           // resolveForInteraction(JOURNAL,"100",COLLECT)：SubjectCapabilityNotSupportedException，catalog 零请求
```

- [ ] **Step 2:** Run `G boot:integrationTest --tests '*SubjectRegistryFlowIT*'`，Expected: FAIL → 补桩辅助方法后 PASS（生产代码在 Task 5、8 已就绪）

---

### Task 18: 本地对象的流程测试（AC：不进对账，状态只由所属子域更新）

**Files:**
- Create: `BIT/subject/LocalSubjectFlowIT.java`（继承 `InteractionFlowITSupport`）

- [ ] **Step 1: 写测试**

```java
@Test void should_keep_local_subject_out_of_reconciliation()
  // CATALOG_DATA.put(100,"1",…)，resolveForInteraction(PUBLICATION,"100",COLLECT)
  // tx 里 registerLocal(POST,7,owner(9,true),快照) 得 P；记下 P 的 snapshot_at 与 updated_at
  // 跑对账：scanned = 1；catalog 收到的请求体里没有 "7" 与 "post"；P 行原样
@Test void should_change_state_only_through_owning_subdomain()
  // tx 里 updateLocal(POST,7,UNAVAILABLE,快照) → resolveForInteraction(POST,"7",DISCUSS) 抛 SubjectNotFoundException 且带 SUBJECT_UNAVAILABLE
  // findByIds([P]).owner() = SubjectOwner.of(9,true)
@Test void should_require_callers_transaction()
  // 事务外 registerLocal / updateLocal：IllegalTransactionStateException；subject.registry 无新行
@Test void should_reject_unregistered_post()      // resolveForInteraction(POST,"8",DISCUSS)：notFound，不带 SUBJECT_UNAVAILABLE
```

- [ ] **Step 2:** Run `G boot:integrationTest --tests '*LocalSubjectFlowIT*'`，Expected: PASS（若红，按 superpowers:systematic-debugging 查）

---

### Task 19: 发表守卫的流程测试（AC：四种拒绝各有明确错误码）

**Files:**
- Create: `BIT/subject/PublishGuardFlowIT.java`（继承 `InteractionFlowITSupport`；注入 `PublishGuardGateway`、`TransactionTemplate`、`ErrorResolutionEngine`）

- [ ] **Step 1: 写测试**

```java
@Test void should_reject_unverified_and_restricted_accounts_with_409()
  // IDENTITY_DATA：ACTIVE 未验证 → EMAIL_UNVERIFIED；BANNED / PENDING_DELETION / DELETED / 省略 → ACCOUNT_RESTRICTED
  // engine.resolve(ex)：errorCode().code() = "INTERACTION-0409"，httpStatus() = 409
@Test void should_reject_second_comment_within_ten_seconds_with_429()
  // 两次 tx(admit(A, COMMENT))：第二次 TOO_FREQUENT，"INTERACTION-0429"，retryAfterSeconds 在 1..10
@Test void should_reject_over_daily_limits()
  // 预写计数行：A COMMENT (今天北京日期, 100, 1 小时前) → DAILY_LIMIT_REACHED；B POST (今天, 10, …) → DAILY_LIMIT_REACHED
@Test void should_admit_only_one_of_two_concurrent_posts_at_the_limit()
  // 预写 C POST (今天, 9, 1 小时前)；两个线程各 tx(admit(C, POST))：恰好一个成功，另一个 DAILY_LIMIT_REACHED；库里 day_count = 10
@Test void should_require_callers_transaction_for_admit()   // 事务外 admit：IllegalTransactionStateException
```

- [ ] **Step 2:** Run `G boot:integrationTest --tests '*PublishGuardFlowIT*'`，Expected: PASS

---

### Task 20: 公共注销清理的流程测试

**Files:**
- Create: `BIT/subject/DeletedUserCleanupFlowIT.java`（继承 `InteractionFlowITSupport`）

- [ ] **Step 1: 写测试**

```java
@Test void should_clean_every_participant_for_deleted_users_only()
  // A：收藏一篇 + COMMENT 计数行；B：只有 POST 计数行；C：收藏一篇
  // IDENTITY_DATA：A、B 为 DELETED，C 为 ACTIVE；identity.resetRequests()
  // 跑 CleanupDeletedUsersCommand：A 在 collection 三表与 publish_quota 都 0 行；B 的计数行 0；C 不动
  // 结果 scannedUsers = 3、deletedUsers = 2；identity 的 /_internal/users/summaries 只被调用 1 次
```

- [ ] **Step 2:** Run `G boot:integrationTest`（全量，含 `CollectionFlowIT`），Expected: PASS

---

### Task 21: 文档：README、runbook、交接笔记

**Files:**
- Modify: `patra-api/patra-interaction/README.md`（spec §14.2）
- Create: `docs/patra/runbooks/v0.14-discussion-go-live-runbook.md`（spec §14.1；PAP-117 的 Q1–Q4 SQL、部署后核对、回滚照抄其 spec 第 11 节；identity V2 的核对写成查 `pg_indexes` 里 `idx_idn_user_username_prefix`、`idx_idn_user_profile_display_name_prefix`；PAP-121 ~ 123 留占位节；PAP-119 已撤出本版，不留）
- Create: 共享笔记 `/Users/linqibin/Library/Application Support/Claude/side-session-notes/local_05d5a37f-fa2d-4775-824c-b51aadd73808/interaction-PAP-120-handoff.md`（spec §12 表格 + 调用示例 + 事件契约规定）
- Modify: spec 末尾加「实施记录」节（与设计的偏离、门禁结果）

- [ ] **Step 1:** 写三份文档；runbook 里每条命令照 v0.13 写法给「预期」与「失败时」
- [ ] **Step 2:** 核对：README 的公开包表与 `SubdomainArchitectureRules` 两个常量一致；runbook 引用的约束名、索引名、handler 名都能在代码或迁移里 grep 到

---

### Task 22: 全量门禁

- [ ] **Step 1:** `./gradlew spotlessApply`
- [ ] **Step 2:** `./gradlew check`，Expected: BUILD SUCCESSFUL
- [ ] **Step 3:** `G infra:integrationTest G boot:integrationTest --rerun`，Expected: PASS
- [ ] **Step 4:** `bash patra-infra/cd/detect-changes.test.sh`，Expected: 全部通过
- [ ] **Step 5:** `./gradlew dumpModuleGraph` 后 `git diff --stat patra-infra/cd/module-graph.json`：预计无变化，有变化就把它列进改动清单
- [ ] **Step 6:** `git status` 列出全部改动路径（只含本 Issue 的文件），写进最终汇报；不提交
