# interaction 收藏与收藏夹 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task（用户指定：会话内联 TDD，不派 subagent）. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 patra-interaction 的 `collection` 子域落地文献库条目、收藏夹（私有 / 公开、多归属、复制、上限）、批量「我的状态」、导出 key 编排与注销清理任务（PAP-113）。

**Architecture:** 三张表在 schema `collection`，两张状态窄表按 `user_id` 哈希分 16 区，`JdbcClient` 原生 SQL；每个写事务先拿该用户的 advisory lock 再做「查上限 / 查重名 → 写」。新增收藏经 subject 的 `SubjectGateway`（事务外），已有条目只用 `subject_id`；按出版日期 / 期刊排序在内存里做（快照经 `SubjectReadPort.findByIds` 分块取）。公开资格与可见性经 identity 批量摘要（`UserEndpoint`）。

**Tech Stack:** Java 25、Spring Boot 4、Spring Data JDBC `JdbcClient`、PostgreSQL 17 分区表、Flyway、XXL-Job、HTTP Interface、JUnit 5 + AssertJ + Mockito、Testcontainers、WireMock、`RestTestClient`。

**Spec:** `docs/patra/specs/2026-10-24-interaction-collection-design.md`（下文「spec §x」指它）。

## Global Constraints

- 包根：`dev.patra.interaction.{domain|app|infra|adapter}.collection`；collection 只 import subject 的公开包（`port.gateway`、`port.read`、`model.read`、`model.vo`、`model.enums`），subject 的异常不 import、不捕获。
- 迁移脚本：`patra-interaction-infra/src/main/resources/db/migration/collection/V20261024210000__create_collection_tables.sql`；表名不带前缀，`collection.set_updated_at()` 在本脚本里建。
- 表里只存 `user_id`，不得出现 `username` / `user_name` 列（R6）。
- 上限：文献库 20,000、收藏夹 200、单夹 5,000、批量 100、导出 5,000；名称 1 ~ 100 字素 / ≤ 400 字节，简介、备注 ≤ 500 字素 / ≤ 2,000 字节。
- 原因码（`CollectionErrorTrait`）：`LIBRARY_FULL`、`COLLECTION_LIMIT_REACHED`、`COLLECTION_NAME_TAKEN`、`ACCOUNT_RESTRICTED`、`EMAIL_UNVERIFIED`、`EXPORT_TOO_LARGE`；字段码：`REQUIRED`、`TOO_LONG`、`INVALID_CHARACTER`、`TAKEN`。
- 用户锁键：`collection.user:{userId}`，SQL `SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))`。
- `SubjectGateway.resolveForInteraction` 与 identity 调用都在事务外；事务用 `TransactionOperations`（同 `SubjectGatewayImpl`）。
- 时间一律注入 `Clock`；Javadoc 用 `///`；record ≤ 4 参用 `of()`，≥ 5 参用 `@Builder`；集合字段 `List.copyOf`。
- Java 文本块行尾空格会被吞：`RETURNING\s"""`。spotless 先于 check：每个任务结束跑 `./gradlew :patra-api:patra-interaction:spotlessApply` 再验证。
- 不按任务提交。全部完成、验证通过并经用户同意后，按 Issue 一次提交（中文 commit message，subject 以中文动词起头）。
- 运行命令都在 worktree 根目录：`/Users/linqibin/Projects/Products/patra/.claude/worktrees/v013-collection-495263`，不 `cd` 到别处。

## Review Focus

1. **已失效对象上的重复收藏**：对象变 UNAVAILABLE 后用户再点收藏，应得到 `created=false` 而不是 404 —— Task 7 的 `should_return_existing_item_when_subject_became_unavailable`。
2. **并发撞上限 / 重名**：同一用户两个请求同时建第 200 个夹或收第 20,000 条，只能成功一个 —— Task 4 的并发 IT。
3. **identity 省略某个 ID**：公开读当不可见（404 / 空列表），注销清理不删 —— Task 10、Task 11 的单测。
4. **整行消失（`key == null`）的条目**：列表里有 `itemId` 和 `title`、没有 `key`；能按 `itemId` 删除、改备注、移出；不进导出 key —— Task 10 的单测 + Task 13 的流程 IT。
5. **复制时库放不下**：整体失败、一行都不写（新夹、新条目都没有）—— Task 9 的单测 + Task 13 的流程 IT 校验行数。

---

### Task 0: 对齐分支（写代码前的门槛）

- [ ] **Step 1: 查 PAP-112 是否已进版本分支**

Run: `git -C /Users/linqibin/Projects/Products/patra log --oneline feat/v0.13-library-api | head -5`
Expected: 能看到「新增对象登记表、能力矩阵、首次互动解析与每日对账」那条提交。没有 → 停下，告诉用户，不往下做。

- [ ] **Step 2: 对齐**

Run: `git reset --hard feat/v0.13-library-api`（本分支还没有提交，spec / plan 是未跟踪文件，不受影响）
Expected: `git log --oneline -1` 是 PAP-112 那条或更新；`ls patra-api/patra-interaction/patra-interaction-domain/src/main/java/dev/patra/interaction/domain/subject/port/read/SubjectReadPort.java` 存在。

- [ ] **Step 3: 基线**

Run: `./gradlew :patra-api:patra-interaction:patra-interaction-boot:test :patra-api:patra-interaction:patra-interaction-infra:integrationTest`
Expected: BUILD SUCCESSFUL（记下耗时；失败先查是不是环境问题，不是本 Issue 引入的）。

---

### Task 1: 文本规则与三个文本值对象

**Files:**
- Create: `patra-api/patra-interaction/patra-interaction-domain/src/main/java/dev/patra/interaction/domain/collection/policy/TextRules.java`
- Create: `…/domain/collection/model/vo/CollectionName.java`、`CollectionDescription.java`、`ItemNote.java`
- Create: `…/domain/collection/exception/InvalidCollectionFieldsException.java`
- Test: `patra-api/patra-interaction/patra-interaction-domain/src/test/java/dev/patra/interaction/domain/collection/model/vo/CollectionTextTest.java`

**Interfaces:**
- Produces:
  - `TextRules`（final，静态）：`String normalize(String raw)`（null → ""；NFC；去首尾空白含 U+00A0、U+3000）、`int graphemeCount(String)`（`BreakIterator.getCharacterInstance(Locale.ROOT)`）、`int utf8Length(String)`、`boolean hasRejectedCharacter(String text, boolean allowLineFeed)`（拒绝 `Character.isISOControl` 与 U+2028/U+2029，`allowLineFeed` 时放行 U+000A）。
  - `record CollectionName(String value)`：`static CollectionName parse(String raw)`，违反时抛 `InvalidCollectionFieldsException`（字段 `name`，码 `REQUIRED` / `TOO_LONG` / `INVALID_CHARACTER`）；`static final int MAX_GRAPHEMES = 100, MAX_BYTES = 400`。
  - `record CollectionDescription(String value)`：`static Optional<CollectionDescription> parse(String raw)`（去完为空 → empty），字段 `description`，`MAX_GRAPHEMES = 500, MAX_BYTES = 2000`，放行换行。
  - `record ItemNote(String value)`：`static Optional<ItemNote> parse(String raw)`，字段 `note`，上限同简介，放行换行。
  - `InvalidCollectionFieldsException extends DomainException implements HasFieldViolations`：`StandardErrorTrait.RULE_VIOLATION`，构造 `(List<FieldViolation>)`。

- [ ] **Step 1: 写失败测试 `CollectionTextTest`**

```java
@Test void should_strip_and_keep_name() {
  assertThat(CollectionName.parse("  组会 　").value()).isEqualTo("组会");
}
@Test void should_reject_blank_name() { assertViolation(() -> CollectionName.parse("  "), "name", "REQUIRED"); }
@Test void should_count_graphemes_not_chars() {
  assertThat(CollectionName.parse("👨‍👩‍👧".repeat(100)).value()).isNotNull();       // 100 字素
  assertViolation(() -> CollectionName.parse("组".repeat(101)), "name", "TOO_LONG");
}
@Test void should_cap_bytes() {   // 一个字素里塞很多组合符
  assertViolation(() -> CollectionName.parse("e" + "́".repeat(400)), "name", "TOO_LONG");
}
@Test void should_reject_line_break_in_name() { assertViolation(() -> CollectionName.parse("a\nb"), "name", "INVALID_CHARACTER"); }
@Test void should_allow_line_feed_in_note() { assertThat(ItemNote.parse("第一行\n第二行")).get().extracting(ItemNote::value).isEqualTo("第一行\n第二行"); }
@Test void should_treat_blank_note_as_absent() { assertThat(ItemNote.parse(" \n ")).isEmpty(); assertThat(ItemNote.parse(null)).isEmpty(); }
@Test void should_reject_long_description() { assertViolation(() -> CollectionDescription.parse("字".repeat(501)), "description", "TOO_LONG"); }
@Test void should_reject_control_char_in_note() { assertViolation(() -> ItemNote.parse("a\u0007"), "note", "INVALID_CHARACTER"); }
```

`assertViolation` 是测试类里的私有方法：断言抛 `InvalidCollectionFieldsException`，其 `getFieldViolations()` 只有一条且 `field`、`code` 如参数。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-interaction:patra-interaction-domain:test --tests "*CollectionTextTest"`
Expected: 编译失败（类不存在）。

- [ ] **Step 3: 实现上述五个类**

检查顺序：规范化 → 空（只 name）→ 拒绝字符 → 字素 / 字节上限（任一超出都是 `TOO_LONG`）。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2。Expected: PASS。

---

### Task 2: 上限、复制命名、领域异常与原因码、枚举

**Files:**
- Create: `…/domain/collection/policy/CollectionLimits.java`、`CopyNaming.java`
- Create: `…/domain/collection/model/enums/CollectionVisibility.java`、`LibraryView.java`、`LibrarySort.java`
- Create: `…/domain/collection/model/vo/CollectionId.java`
- Create: `…/domain/collection/exception/CollectionErrorTrait.java`、`CollectionNotFoundException.java`、`LibraryItemNotFoundException.java`、`LibraryFullException.java`、`CollectionLimitReachedException.java`、`CollectionNameTakenException.java`、`PublishNotAllowedException.java`、`ExportTooLargeException.java`、`InvalidCollectionRequestException.java`、`UserDirectoryUnavailableException.java`
- Test: `…/domain/src/test/java/dev/patra/interaction/domain/collection/policy/CopyNamingTest.java`、`…/collection/model/enums/CollectionEnumsTest.java`、`…/collection/exception/CollectionExceptionsTest.java`

**Interfaces:**
- Produces:
  - `CollectionLimits`：`LIBRARY_MAX_ITEMS = 20_000`、`MAX_COLLECTIONS = 200`、`COLLECTION_MAX_ITEMS = 5_000`、`MAX_BATCH = 100`、`MAX_EXPORT = 5_000`。
  - `CopyNaming.nameFor(CollectionName source, Set<String> taken) → CollectionName`：源名不在 `taken` 里就用它；否则 `n` 从 2 起找第一个不在 `taken` 里的 `"<base> (n)"`；`base` 按字素截短到「base + 后缀」≤ 100 字素且 ≤ 400 字节。
  - `CollectionVisibility { PRIVATE, PUBLIC }`、`static CollectionVisibility fromParam(String)`（大小写不敏感，未知抛 `InvalidCollectionRequestException`）。
  - `LibraryView { ALL, UNFILED }`、`LibrarySort { ADDED, PUBLISHED, JOURNAL }`：各有 `fromParam(String raw)`（null → 默认 `ALL` / `ADDED`；小写参数名 `all`/`unfiled`/`added`/`published`/`journal`；未知抛 `InvalidCollectionRequestException`）。
  - `record CollectionId(long value)` + `of(long)`。
  - `CollectionErrorTrait implements ErrorTrait { LIBRARY_FULL, COLLECTION_LIMIT_REACHED, COLLECTION_NAME_TAKEN, ACCOUNT_RESTRICTED, EMAIL_UNVERIFIED, EXPORT_TOO_LARGE }`。
  - 异常 → 标准 trait + 原因码 + 默认文案（spec §6.4）：`CollectionNotFoundException`（NOT_FOUND，「没有找到这个收藏夹」）、`LibraryItemNotFoundException`（NOT_FOUND，「文献库里没有这一条」）、`LibraryFullException`（CONFLICT + LIBRARY_FULL）、`CollectionLimitReachedException`（CONFLICT + COLLECTION_LIMIT_REACHED）、`CollectionNameTakenException implements HasFieldViolations`（CONFLICT + COLLECTION_NAME_TAKEN，`errors` = `name`/`TAKEN`/「已有同名收藏夹」）、`PublishNotAllowedException`（CONFLICT + 构造参数给的 `ACCOUNT_RESTRICTED` 或 `EMAIL_UNVERIFIED`，提供静态工厂 `accountRestricted()`、`emailUnverified()`）、`ExportTooLargeException`（RULE_VIOLATION + EXPORT_TOO_LARGE）、`InvalidCollectionRequestException(String message)`（RULE_VIOLATION）、`UserDirectoryUnavailableException(Throwable cause)`（DEP_UNAVAILABLE，「用户信息暂时无法获取，请稍后再试」）。

- [ ] **Step 1: 写失败测试**

```java
// CopyNamingTest
@Test void should_keep_name_when_free() { assertThat(nameFor("组会", Set.of("别的"))).isEqualTo("组会"); }
@Test void should_append_smallest_free_number() { assertThat(nameFor("组会", Set.of("组会", "组会 (2)", "组会 (4)"))).isEqualTo("组会 (3)"); }
@Test void should_truncate_base_to_fit_suffix() {
  String base = "字".repeat(100);
  assertThat(nameFor(base, Set.of(base))).isEqualTo("字".repeat(96) + " (2)");
}
// CollectionEnumsTest
@Test void should_parse_params_with_defaults() {
  assertThat(LibrarySort.fromParam(null)).isEqualTo(LibrarySort.ADDED);
  assertThat(LibrarySort.fromParam("journal")).isEqualTo(LibrarySort.JOURNAL);
  assertThat(LibraryView.fromParam("unfiled")).isEqualTo(LibraryView.UNFILED);
  assertThat(CollectionVisibility.fromParam("public")).isEqualTo(CollectionVisibility.PUBLIC);
  assertThatThrownBy(() -> LibrarySort.fromParam("title")).isInstanceOf(InvalidCollectionRequestException.class);
}
// CollectionExceptionsTest：每个异常的 getErrorTraits() 恰好是 {标准 trait, 原因码}（或只有标准 trait）
@Test void should_carry_reason_codes() {
  assertThat(new LibraryFullException().getErrorTraits()).containsExactlyInAnyOrder(StandardErrorTrait.CONFLICT, CollectionErrorTrait.LIBRARY_FULL);
  assertThat(PublishNotAllowedException.emailUnverified().getErrorTraits()).contains(CollectionErrorTrait.EMAIL_UNVERIFIED);
  assertThat(new CollectionNameTakenException().getFieldViolations()).extracting(FieldViolation::field, FieldViolation::code).containsExactly(tuple("name", "TAKEN"));
}
```

`nameFor(String, Set<String>)` 是测试里的私有包装：`CopyNaming.nameFor(CollectionName.parse(s), taken).value()`。

- [ ] **Step 2: 跑测试确认失败** — Run: `./gradlew :patra-api:patra-interaction:patra-interaction-domain:test --tests "*collection*"`；Expected: 编译失败。
- [ ] **Step 3: 实现上述类**
- [ ] **Step 4: 跑测试确认通过** — 同 Step 2；Expected: PASS。

---

### Task 3: 迁移脚本与迁移 IT

**Files:**
- Create: `patra-api/patra-interaction/patra-interaction-infra/src/main/resources/db/migration/collection/V20261024210000__create_collection_tables.sql`
- Modify: `patra-api/patra-interaction/patra-interaction-infra/src/integrationTest/java/dev/patra/interaction/infra/InteractionMigrationIT.java`

**Interfaces:**
- Produces: 表 `collection.library_item`、`collection.collection`、`collection.collection_item`，列与索引照 spec §4.1；分区子表 `library_item_p00…p15`、`collection_item_p00…p15`（`FOR VALUES WITH (MODULUS 16, REMAINDER n)`）；`CHECK (visibility IN ('PRIVATE','PUBLIC'))`、`CHECK (item_count BETWEEN 0 AND 5000)`；`collection.set_updated_at()` 与 `library_item`、`collection` 的 `BEFORE UPDATE` 触发器；每张表、每列 `COMMENT`（英文，同 subject 脚本）。

- [ ] **Step 1: 改 `InteractionMigrationIT`（先红）**

```java
// should_record_migrations_with_timestamp_versions 的期望改为
.containsExactly("20261024100000", "20261024100100", "20261024200000", "20261024210000");

@Test void should_hash_partition_state_tables_by_user_into_16() {
  for (String table : List.of("library_item", "collection_item")) {
    assertThat(jdbcTemplate.queryForObject(
        "SELECT count(*) FROM pg_inherits i JOIN pg_class p ON p.oid = i.inhparent "
            + "JOIN pg_namespace n ON n.oid = p.relnamespace WHERE n.nspname = 'collection' AND p.relname = ?",
        Integer.class, table)).isEqualTo(16);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT pg_get_partkeydef(('collection.' || ?)::regclass)", String.class, table))
        .isEqualTo("HASH (user_id)");
  }
}
@Test void should_key_state_tables_by_user_first() {
  assertThat(primaryKeyColumns("library_item")).containsExactly("user_id", "subject_id");
  assertThat(primaryKeyColumns("collection_item")).containsExactly("user_id", "collection_id", "subject_id");
}
@Test void should_keep_collection_names_unique_per_user() {
  insertCollection(1L, 10L, "组会");
  assertThatThrownBy(() -> insertCollection(2L, 10L, "组会")).isInstanceOf(DataIntegrityViolationException.class);
  insertCollection(3L, 11L, "组会");   // 别人可以同名
}
```

`primaryKeyColumns`、`insertCollection` 是测试类私有方法（`information_schema.key_column_usage` 按 `ordinal_position`；直接 INSERT 必填列）。`should_create_one_schema_per_subdomain` 不变。

- [ ] **Step 2: 跑确认失败** — Run: `./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest --tests "*InteractionMigrationIT"`；Expected: 版本列表与分区断言 FAIL。
- [ ] **Step 3: 写迁移脚本**
- [ ] **Step 4: 跑确认通过** — 同 Step 2；再跑 `./gradlew :patra-api:patra-interaction:patra-interaction-infra:checkMigrationColumns`，Expected: 都 PASS。

---

### Task 4: 写端持久化（两个仓储端口与适配器）

**Files:**
- Create: `…/domain/collection/model/read/LibraryEntry.java`、`CollectionSummary.java`、`DeletedUserData.java`
- Create: `…/domain/collection/port/repository/LibraryRepository.java`、`CollectionRepository.java`
- Create: `patra-api/patra-interaction/patra-interaction-infra/src/main/java/dev/patra/interaction/infra/collection/persistence/LibraryRepositoryAdapter.java`、`CollectionRepositoryAdapter.java`、`CollectionRows.java`
- Test: `patra-api/patra-interaction/patra-interaction-infra/src/integrationTest/java/dev/patra/interaction/infra/collection/persistence/LibraryRepositoryAdapterIT.java`、`CollectionRepositoryAdapterIT.java`

**Interfaces:**
- Consumes: `SubjectId`（subject 公开包）、Task 1/2 的值对象与枚举。
- Produces:
  - `record LibraryEntry(SubjectId subjectId, String note, Instant collectedAt)` + `of`。
  - `@Builder record CollectionSummary(CollectionId collectionId, long ownerId, String name, String description, CollectionVisibility visibility, int itemCount, Instant createdAt, Instant updatedAt)`。
  - `record DeletedUserData(int libraryItems, int collections, int collectionItems)` + `of`。
  - `LibraryRepository`（写方法都不开事务，由 app 包住）：
    - `void lockOwner(long userId)`
    - `boolean insertItem(long userId, SubjectId subjectId, Instant collectedAt)` — `ON CONFLICT (user_id, subject_id) DO NOTHING`，返回是否插入
    - `int countItems(long userId)`
    - `Optional<LibraryEntry> findItem(long userId, SubjectId subjectId)`
    - `Set<SubjectId> findExistingItems(long userId, Collection<SubjectId> subjectIds)`
    - `boolean updateNote(long userId, SubjectId subjectId, String noteOrNull)` — 返回是否有这一行
    - `void deleteItems(long userId, Collection<SubjectId> subjectIds, Instant now)` — 同一组语句里：按受影响收藏夹聚合减 `item_count`、`updated_at = now`；删 `collection_item`；删 `library_item`
    - `DeletedUserData deleteAllOf(long userId)` — 三表删该用户全部行，回行数
  - `CollectionRepository`：
    - `int countCollections(long userId)`
    - `Optional<CollectionSummary> findOwned(long userId, CollectionId id)`
    - `boolean nameTaken(long userId, String name, CollectionId excludingOrNull)`
    - `Set<String> findNames(long userId)`
    - `CollectionSummary create(long userId, CollectionName name, String descriptionOrNull, Instant now)` — Snowflake ID（`SnowflakeIdGenerator.getId()`），`PRIVATE`，`item_count = 0`
    - `CollectionSummary updateText(long userId, CollectionId id, CollectionName name, String descriptionOrNull)`、`CollectionSummary updateVisibility(long userId, CollectionId id, CollectionVisibility visibility)`（`RETURNING`；`updated_at` 由触发器在值变化时刷新——触发器只在 `updated_at` 未被显式改时置 `now()`）
    - `void delete(long userId, CollectionId id)` — 先删其 `collection_item` 再删行
    - `Set<SubjectId> findMembers(long userId, CollectionId id, Collection<SubjectId> subjectIds)`
    - `void addMembers(long userId, CollectionId id, List<SubjectId> subjectIds, Instant addedAt)` — 第 `i` 个的 `added_at = addedAt - i 微秒`（spec §5.8 保序）；`item_count += size`、`updated_at = addedAt`
    - `int removeMembers(long userId, CollectionId id, Collection<SubjectId> subjectIds, Instant now)` — 回实际删除数，>0 时 `item_count -= n`、`updated_at = now`
  - `LibraryRepository.insertItems(long userId, List<SubjectId> subjectIds, Instant collectedAt) → int`（复制用，同样按 `i` 微秒递减，`ON CONFLICT DO NOTHING`，回插入数）
  - `CollectionRows`（包内）：`CollectionSummary` 行映射、`utc(Instant)`。

- [ ] **Step 1: 写失败 IT**（`@SpringBootTest(classes = InteractionITBootstrap.class)` + 容器初始化器 + `@ActiveProfiles("test")`，`@BeforeEach` TRUNCATE 三表）

```java
// LibraryRepositoryAdapterIT（每个写调用包在 TransactionTemplate 里）
@Test void should_insert_item_once() {
  assertThat(tx(() -> library.insertItem(U, S1, T0))).isTrue();
  assertThat(tx(() -> library.insertItem(U, S1, T1))).isFalse();
  assertThat(library.findItem(U, S1)).get().extracting(LibraryEntry::collectedAt).isEqualTo(T0);
}
@Test void should_remove_item_from_every_collection() {
  var a = tx(() -> collections.create(U, name("A"), null, T0)); var b = tx(() -> collections.create(U, name("B"), null, T0));
  tx(() -> { library.insertItem(U, S1, T0); collections.addMembers(U, a.collectionId(), List.of(S1), T1); collections.addMembers(U, b.collectionId(), List.of(S1), T1); });
  tx(() -> library.deleteItems(U, List.of(S1), T2));
  assertThat(collections.findOwned(U, a.collectionId())).get().extracting(CollectionSummary::itemCount, CollectionSummary::updatedAt).containsExactly(0, T2);
  assertThat(count("collection.collection_item")).isZero();
  assertThat(count("collection.library_item")).isZero();
}
@Test void should_keep_items_when_collection_deleted() { /* 删夹后 library_item 仍 1 行，collection_item 0 行 */ }
@Test void should_delete_all_data_of_one_user_only() { /* U 与 V 各有条目、夹、夹条目；deleteAllOf(U) 回三个行数，V 的行全在 */ }
@Test void should_serialize_same_user_with_owner_lock() throws Exception {
  // 两个线程各开事务：lockOwner(U) → countItems → sleep 200ms → insertItem；第二个线程的 count 必须看到第一个的插入
  // 断言：第二个事务里读到的 count == 1
}
// CollectionRepositoryAdapterIT
@Test void should_detect_taken_name_excluding_self() { … nameTaken(U, "组会", null) 为真；nameTaken(U, "组会", 自己的 id) 为假 }
@Test void should_preserve_order_with_microsecond_offsets() {
  tx(() -> collections.addMembers(U, c, List.of(S1, S2, S3), T0));
  // 直接查 added_at：S1 = T0、S2 = T0 - 1µs、S3 = T0 - 2µs；item_count = 3
}
@Test void should_report_removed_count() { /* removeMembers 两个、其中一个不在 → 回 1，item_count 减 1 */ }
```

`U`、`V`、`S1…`、`T0…` 是测试常量；`tx` 用 `TransactionTemplate`；`name(s)` = `CollectionName.parse(s)`；`count(table)` 用 `JdbcTemplate`。

- [ ] **Step 2: 跑确认失败** — Run: `./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest --tests "*collection*"`；Expected: 编译失败。
- [ ] **Step 3: 实现端口与适配器**
- [ ] **Step 4: 跑确认通过** — 同 Step 2；Expected: PASS。

---

### Task 5: 读端持久化（`LibraryReadPort`）

**Files:**
- Create: `…/domain/collection/model/read/ItemRef.java`、`LibraryCounts.java`
- Create: `…/domain/collection/port/read/LibraryReadPort.java`
- Create: `…/infra/collection/persistence/LibraryReadAdapter.java`
- Test: `…/infra/src/integrationTest/java/dev/patra/interaction/infra/collection/persistence/LibraryReadAdapterIT.java`

**Interfaces:**
- Consumes: Task 4 的 `LibraryEntry`、`CollectionSummary`、`CollectionRows`。
- Produces:
  - `record ItemRef(SubjectId subjectId, Instant at)` + `of`（`at` 在文献库视图是 `collected_at`，在收藏夹视图是 `added_at`）。
  - `record LibraryCounts(int total, int unfiled)` + `of`。
  - `LibraryReadPort`：
    - `LibraryCounts countLibrary(long userId)`
    - `List<CollectionSummary> findCollections(long userId)` — `updated_at DESC, collection_id DESC`
    - `Optional<CollectionSummary> findCollection(CollectionId id)` — 不限所有者（公开读用）
    - `List<CollectionSummary> findPublicCollections(long ownerId)` — 只 `PUBLIC`，排序同上
    - `long countView(long userId, LibraryView view)`
    - `List<ItemRef> pageLibrary(long userId, LibraryView view, int offset, int limit)` — `collected_at DESC, subject_id DESC`；`UNFILED` 用 `NOT EXISTS`
    - `List<ItemRef> allLibrary(long userId, LibraryView view)`
    - `List<ItemRef> pageCollection(long ownerId, CollectionId id, int offset, int limit)` — `added_at DESC, subject_id DESC`
    - `List<ItemRef> allCollection(long ownerId, CollectionId id)`
    - `Map<SubjectId, LibraryEntry> findEntries(long userId, Collection<SubjectId> subjectIds)`
    - `Map<SubjectId, List<CollectionId>> findMemberships(long userId, Collection<SubjectId> subjectIds)` — 没有归属的不在 map 里
    - `List<Long> findOwnersAfter(long afterUserId, int limit)` — `library_item` 与 `collection` 的 `user_id` 并集，升序

- [ ] **Step 1: 写失败 IT**

```java
@Test void should_count_total_and_unfiled() { /* 3 条，1 条在某夹 → LibraryCounts(3, 2) */ }
@Test void should_page_library_by_collected_time_desc() {
  // S1@T0、S2@T2、S3@T1 → pageLibrary(U, ALL, 0, 2) = [S2, S3]，offset 2 = [S1]
}
@Test void should_page_unfiled_only() { /* S1 在夹里 → UNFILED 只有 S2、S3；countView(UNFILED) = 2 */ }
@Test void should_page_collection_by_added_time_desc() { … }
@Test void should_map_memberships_per_item() { /* S1 在 A、B → {S1: [A, B]}；S2 无归属不在 map */ }
@Test void should_list_public_collections_by_update_desc() { … }
@Test void should_iterate_owners_from_both_tables() {
  // U 只有条目，V 只有一个空收藏夹，W 两样都有 → findOwnersAfter(0, 2) = [min…]，再从游标续；三个人各出现一次
}
```

- [ ] **Step 2: 跑确认失败** — Run: `./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest --tests "*LibraryReadAdapterIT"`
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑确认通过**

---

### Task 6: identity 客户端（`UserDirectoryPort`）与安全接入

**Files:**
- Create: `…/domain/collection/model/user/UserCard.java`、`AccountState.java`
- Create: `…/domain/collection/port/user/UserDirectoryPort.java`
- Create: `…/infra/collection/user/UserDirectoryAdapter.java`
- Modify: `patra-api/patra-interaction/patra-interaction-infra/build.gradle.kts`（`implementation(project(":patra-api:patra-identity:patra-identity-api"))`）
- Modify: `patra-api/patra-interaction/patra-interaction-infra/src/integrationTest/java/dev/patra/interaction/infra/InteractionITBootstrap.java`（加 `@Bean UserEndpoint userEndpoint()` mock）
- Create: `patra-api/patra-interaction/patra-interaction-boot/src/main/java/dev/patra/interaction/config/IdentityClientConfiguration.java`
- Modify: `patra-api/patra-interaction/patra-interaction-boot/build.gradle.kts`（`implementation(project(":patra-tool:patra-tool-security"))`、`implementation(project(":patra-api:patra-identity:patra-identity-api"))`、`integrationTestImplementation(testFixtures(project(":patra-tool:patra-tool-security")))`；`testImplementation` 同理若单测要用）
- Modify: `patra-api/patra-interaction/patra-interaction-boot/src/main/resources/application.yml`（`groups.identity`：`base-url: lb://patra-identity`、`connect-timeout: 1s`、`read-timeout: 2s`）
- Modify: `patra-api/patra-interaction/patra-interaction-boot/src/test/java/dev/patra/interaction/ApplicationConfigTest.java`（若它断言了分组，补 identity）
- Test: `…/infra/src/test/java/dev/patra/interaction/infra/collection/user/UserDirectoryAdapterTest.java`

**Interfaces:**
- Produces:
  - `enum AccountState { ACTIVE, BANNED, PENDING_DELETION, DELETED }`
  - `@Builder record UserCard(long userId, String username, String displayName, String avatarUrl, int avatarPalette, AccountState state, boolean emailVerified, boolean profileVisible)`
  - `UserDirectoryPort.findCards(Collection<Long> userIds, Long viewerUserIdOrNull) → Map<Long, UserCard>`：1 ~ 100 个；identity 省略的 ID 不在 map 里；失败抛 `UserDirectoryUnavailableException`。
  - `UserDirectoryAdapter`：`RemoteCallException`、`ResourceAccessException`、`IllegalStateException`（无实例）→ `UserDirectoryUnavailableException`（同 `PublicationSubjectSourceAdapter.call`）；`AccountStatus` → `AccountState` 按名字映射。
  - `IdentityClientConfiguration`：照 `CatalogClientConfiguration`，`identityRestClient`（分组 `identity`、`lb://patra-identity`）与 `UserEndpoint` 代理。

- [ ] **Step 1: 写失败单测 `UserDirectoryAdapterTest`**（mock `UserEndpoint`）

```java
@Test void should_map_summaries_and_pass_viewer() { /* 请求 = UserSummariesReq.of([7, 8], 9)；回一条 7 → map 只有 7，字段一一对应 */ }
@Test void should_translate_remote_failure() {
  when(endpoint.summaries(any())).thenThrow(new ResourceAccessException("down"));
  assertThatThrownBy(() -> adapter.findCards(List.of(7L), null)).isInstanceOf(UserDirectoryUnavailableException.class);
}
@Test void should_reject_empty_or_oversized_batch() { /* 0 个、101 个 → IllegalArgumentException，不调 endpoint */ }
```

`RemoteCallException` 的构造参照 `PublicationSubjectSourceAdapterTest` 的写法再加一例。

- [ ] **Step 2: 跑确认失败** — Run: `./gradlew :patra-api:patra-interaction:patra-interaction-infra:test --tests "*UserDirectoryAdapterTest"`
- [ ] **Step 3: 实现，改两份 build.gradle.kts、application.yml、IT 启动类**
- [ ] **Step 4: 跑确认通过**；再跑 `./gradlew :patra-api:patra-interaction:patra-interaction-boot:test`（架构测试、配置测试）Expected: PASS。提醒用户在 IDEA 里点「加载 Gradle 更改」。

---

### Task 7: 收藏、移除、备注（app 写用例）

**Files:**
- Create: `…/app/collection/usecase/library/CollectItemCommand.java`、`CollectItemHandler.java`、`CollectResult.java`
- Create: `…/app/collection/usecase/library/RemoveItemsCommand.java`、`RemoveItemsHandler.java`
- Create: `…/app/collection/usecase/library/EditNoteCommand.java`、`EditNoteHandler.java`
- Create: `…/app/collection/service/Batches.java`
- Test: `…/app/src/test/java/dev/patra/interaction/app/collection/usecase/library/CollectItemHandlerTest.java`、`RemoveItemsHandlerTest.java`、`EditNoteHandlerTest.java`

**Interfaces:**
- Consumes: `SubjectGateway`、`SubjectReadPort`、`SubjectType.fromSlug`、`SubjectCapability.COLLECT`、`CurrentUserPort`、Task 4 仓储。
- Produces:
  - `CollectItemCommand(String type, String key) implements Command<CollectResult>`；`CollectResult(boolean created, SubjectId itemId)` + `of`。
  - `RemoveItemsCommand(List<Long> itemIds) implements Command<Void>`。
  - `EditNoteCommand(long itemId, String note) implements Command<Void>`。
  - `Batches.requireItemIds(List<Long>) → List<SubjectId>`：null / 空 / > 100 / 含非正数 → `InvalidCollectionRequestException`；去重保序。
  - `Batches.subjectType(String slug) → SubjectType`：未知 → `InvalidCollectionRequestException("不支持的对象类型")`。

`CollectItemHandler.handle` 顺序（spec §6.1）：`require()` 用户 → `subjectType` → `readPort.findIdsByKeys(type, [key])` 命中且 `libraryRepository.findItem` 有 → `CollectResult(false, id)`（不开写事务、不调 gateway）→ 否则 `gateway.resolveForInteraction`（事务外）→ `transactions.execute`：`lockOwner` → `findItem` 有则 `(false, id)` → `countItems >= 20000` 抛 `LibraryFullException` → `insertItem(now)` → `(true, id)`。

- [ ] **Step 1: 写失败测试**

```java
// CollectItemHandlerTest（Mockito；TransactionOperations 用直接执行回调的桩）
@Test void should_collect_new_item() { /* findIdsByKeys 空 → gateway 回 S1 → countItems 0 → insertItem 真 → created=true */ }
@Test void should_return_existing_item_when_subject_became_unavailable() {
  when(readPort.findIdsByKeys(PUBLICATION, List.of("100"))).thenReturn(Map.of("100", S1));
  when(library.findItem(U, S1)).thenReturn(Optional.of(LibraryEntry.of(S1, null, T0)));
  assertThat(handler.handle(CollectItemCommand.of("publication", "100"))).isEqualTo(CollectResult.of(false, S1));
  verifyNoInteractions(gateway);
}
@Test void should_reject_when_library_full() { /* countItems 20000 → LibraryFullException，insertItem 未调 */ }
@Test void should_not_check_limit_for_existing_item() { /* 锁内 findItem 命中 → created=false，即使 countItems 20000 */ }
@Test void should_reject_unknown_type() { /* "journal" → InvalidCollectionRequestException */ }
@Test void should_resolve_outside_transaction() { /* InOrder：gateway.resolveForInteraction 在 transactions.execute 之前 */ }
// RemoveItemsHandlerTest
@Test void should_lock_then_delete_items() { /* InOrder lockOwner → deleteItems(U, [S1,S2], now) */ }
@Test void should_reject_101_ids() { … }
// EditNoteHandlerTest
@Test void should_clear_note_when_blank() { /* note "  " → updateNote(U, S1, null) */ }
@Test void should_404_when_item_missing() { /* updateNote 回 false → LibraryItemNotFoundException */ }
```

- [ ] **Step 2: 跑确认失败** — Run: `./gradlew :patra-api:patra-interaction:patra-interaction-app:test --tests "*collection*"`
- [ ] **Step 3: 实现**（app 的 build.gradle.kts 若缺 `patra-tool-core` 的 `CurrentUserPort` 依赖，经 cqrs 已传递则不加）
- [ ] **Step 4: 跑确认通过**

---

### Task 8: 收藏夹的增删改、设公开、放进 / 移出

**Files:**
- Create: `…/app/collection/usecase/collection/`：`CreateCollectionCommand/Handler`、`UpdateCollectionCommand/Handler`、`SetVisibilityCommand/Handler`、`DeleteCollectionCommand/Handler`、`AddToCollectionCommand/Handler`、`AddToCollectionResult`、`RemoveFromCollectionCommand/Handler`
- Test: 对应的 `*HandlerTest`

**Interfaces:**
- Consumes: Task 4 仓储、Task 6 `UserDirectoryPort`、Task 7 `Batches`。
- Produces:
  - `CreateCollectionCommand(String name, String description) → CollectionSummary`：解析文本 → 锁 → `countCollections >= 200` 抛 `CollectionLimitReachedException` → `nameTaken` 抛 `CollectionNameTakenException` → `create`。
  - `UpdateCollectionCommand(long collectionId, String name, String description) → CollectionSummary`：锁 → `findOwned` 无则 404 → `nameTaken(…, 自己)` → `updateText`。
  - `SetVisibilityCommand(long collectionId, String visibility) → CollectionSummary`：解析 → 事务外先 `findOwned`（无则 404）；目标 `PUBLIC` 且当前不是 `PUBLIC` 时 `findCards([me], me)`：缺席或 `state != ACTIVE` → `PublishNotAllowedException.accountRestricted()`；未验证 → `emailUnverified()`；然后事务：锁 → 再 `findOwned` → `updateVisibility`。其余情况不调 identity。
  - `DeleteCollectionCommand(long collectionId) → Void`：锁 → `findOwned` 有才 `delete`（没有也不报错）。
  - `AddToCollectionCommand(long collectionId, List<Long> itemIds) → AddToCollectionResult(int added, int alreadyIn, int skippedFull, int skippedNotInLibrary)`（`@Builder`）：锁 → `findOwned`（404）→ `findExistingItems` 得在库集合 → `findMembers` 得已在夹集合 → 按请求顺序分类，余量 `5000 - itemCount` 用完后计 `skippedFull` → `addMembers(now)`（非空时）。
  - `RemoveFromCollectionCommand(long collectionId, List<Long> itemIds) → Void`：锁 → `findOwned`（404）→ `removeMembers`。

- [ ] **Step 1: 写失败测试**（每个 Handler 一个测试类）

```java
@Test void should_reject_201st_collection() { /* countCollections 200 → CollectionLimitReachedException */ }
@Test void should_reject_taken_name() { … }
@Test void should_publish_when_active_and_verified() { /* card ACTIVE+verified → updateVisibility(PUBLIC) */ }
@Test void should_reject_publish_for_each_identity_state() {
  // 参数化：BANNED / PENDING_DELETION / DELETED / 缺席 → ACCOUNT_RESTRICTED；ACTIVE+未验证 → EMAIL_UNVERIFIED；BANNED+未验证 → ACCOUNT_RESTRICTED
}
@Test void should_not_call_identity_when_making_private() { verifyNoInteractions(users); }
@Test void should_propagate_identity_outage() { /* findCards 抛 UserDirectoryUnavailableException → 原样传播，未写 */ }
@Test void should_add_until_full_and_count_the_rest() {
  // itemCount 4998；请求 [S1(不在库), S2(已在夹), S3, S4, S5] → added 2 (S3,S4)、alreadyIn 1、skippedFull 1、skippedNotInLibrary 1；addMembers(U, C, [S3, S4], now)
}
@Test void should_404_for_someone_elses_collection() { /* findOwned 空 → CollectionNotFoundException */ }
@Test void should_ignore_delete_of_missing_collection() { /* findOwned 空 → 不抛、不 delete */ }
```

- [ ] **Step 2: 跑确认失败** — Run: `./gradlew :patra-api:patra-interaction:patra-interaction-app:test --tests "*collection*"`
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑确认通过**

---

### Task 9: 可见性判定与复制收藏夹

**Files:**
- Create: `…/app/collection/service/CollectionAccess.java`、`ReadableCollection.java`、`SubjectViews.java`
- Create: `…/app/collection/usecase/copy/CopyCollectionCommand.java`、`CopyCollectionHandler.java`、`CopyResult.java`
- Test: `…/app/src/test/java/dev/patra/interaction/app/collection/service/CollectionAccessTest.java`、`SubjectViewsTest.java`、`…/usecase/copy/CopyCollectionHandlerTest.java`

**Interfaces:**
- Consumes: `LibraryReadPort.findCollection / allCollection`、`UserDirectoryPort`、`SubjectReadPort.findByIds`、Task 4 仓储、`CopyNaming`。
- Produces:
  - `record ReadableCollection(CollectionSummary collection, UserCard owner, boolean viewerIsOwner)` + `of`。
  - `CollectionAccess.open(CollectionId id, Optional<Long> viewer) → ReadableCollection`（spec §5.4）：`findCollection` 无 → 404；`findCards([owner], viewer.orElse(null))`；`viewer == owner` → 可读（owner 缺席时仍可读，`owner` 用只含 `userId` 的占位卡 `AccountState.ACTIVE`——本人能登录说明账号在）；否则要求 `PUBLIC` 且卡在且 `profileVisible` → 否则 `CollectionNotFoundException`。
  - `SubjectViews.fetch(Collection<SubjectId>) → Map<SubjectId, SubjectView>`：按 `SubjectReadPort.MAX_IDS` 分块调 `findByIds` 合并。
  - `CopyCollectionCommand(long collectionId) → CopyResult(CollectionId collectionId, String name, int addedToLibrary, int alreadyInLibrary, int skippedUnavailable)`（`@Builder`）。
  - `CopyCollectionHandler`（spec §5.8）：`require()` → `access.open`；不是 `PUBLIC` → 404 → `allCollection(owner, id)`（`added_at` 倒序）→ `SubjectViews.fetch` → 只留 `state == ACTIVE` → 事务：锁复制者 → `countCollections >= 200` → `findExistingItems` → `countItems + 新篇数 > 20000` 抛 `LibraryFullException` → `CopyNaming.nameFor(源名, findNames)` → `create(…, 源简介)` → `insertItems(新篇目, now)` → `addMembers(全部清单, now)`。

- [ ] **Step 1: 写失败测试**

```java
// CollectionAccessTest
@Test void should_let_owner_read_private() { … }
@Test void should_404_private_for_others() { … verifyNoInteractions? 不：仍可不调 identity；断言抛 404 }
@Test void should_follow_profile_visible_for_public() { /* 真 → 可读；假 → 404；缺席 → 404 */ }
@Test void should_propagate_identity_outage_for_public() { /* 503 不变成 404 */ }
// SubjectViewsTest
@Test void should_chunk_by_5000() { /* 12001 个 → findByIds 调 3 次，大小 5000/5000/2001 */ }
// CopyCollectionHandlerTest
@Test void should_copy_with_counts_and_free_name() {
  // 源 [S1 ACTIVE, S2 ACTIVE, S3 UNAVAILABLE]，自己库里已有 S2，已有名「组会」
  // → create(name「组会 (2)」, 源简介)，insertItems([S1])，addMembers([S1, S2])，结果 (added 1, already 1, skipped 1)
}
@Test void should_fail_whole_copy_when_library_cannot_fit() { /* countItems 19999、新篇 2 → LibraryFullException；create/insertItems/addMembers 都没调 */ }
@Test void should_reject_when_collections_full() { … }
@Test void should_not_copy_private_even_for_owner() { … 404 }
```

- [ ] **Step 2: 跑确认失败**
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑确认通过**

---

### Task 10: 查询服务（文献库、状态、公开读、导出 key）与排序

**Files:**
- Create: `…/app/collection/usecase/query/LibraryOrdering.java`
- Create: `…/app/collection/usecase/query/LibraryQueryService.java`、`PublicCollectionQueryService.java`
- Create: `…/app/collection/usecase/query/` 视图 record：`LibraryItemView`、`ItemStatusView`、`PageView`、`ExportKeysView`、`LibraryOverviewView`、`PublicCollectionView`、`PublicItemView`
- Test: `…/app/src/test/java/dev/patra/interaction/app/collection/usecase/query/LibraryOrderingTest.java`、`LibraryQueryServiceTest.java`、`PublicCollectionQueryServiceTest.java`

**Interfaces:**
- Consumes: `LibraryReadPort`、`SubjectReadPort`、`SubjectViews`、`CollectionAccess`、`CurrentUserPort`。
- Produces:
  - `LibraryOrdering.order(List<ItemRef> refs, LibrarySort sort, Map<SubjectId, SubjectView> views) → List<ItemRef>`：`ADDED` = `at` 倒序、`subjectId` 倒序；`PUBLISHED` = `publishedDate` 字典序倒序、null 在后，同值按 `ADDED`；`JOURNAL` = `journal.toLowerCase(Locale.ROOT)` 升序、null 在后，同值按 `ADDED`。map 里没有的按 null 快照处理。
  - `@Builder LibraryItemView(SubjectId itemId, SubjectType type, String key, SubjectState state, String title, String note, Instant collectedAt, Instant addedAt, List<CollectionId> collectionIds)`：`title` 只在 `UNAVAILABLE` 时取快照标题；`findByIds` 缺行时 `state = UNAVAILABLE`、`key = null`、`title = null`。
  - `@Builder ItemStatusView(String key, boolean collected, SubjectId itemId, Instant collectedAt, List<CollectionId> collectionIds, boolean hasNote)`。
  - `PageView<T>(int page, int pageSize, long total, int totalPages, List<T> items)` + `static <T> PageView<T> of(List<T>, int page, int pageSize, long total)`（`total == 0` 时 `totalPages = 0`）。
  - `ExportKeysView(List<String> keys, int skippedUnavailable)` + `of`。
  - `LibraryOverviewView(int total, int unfiled, List<CollectionSummary> collections)` + `of`。
  - `PublicCollectionView(CollectionSummary collection, UserCard owner, boolean viewerIsOwner)` + `of`。
  - `@Builder PublicItemView(SubjectId itemId, SubjectType type, String key, SubjectState state, String title, Instant addedAt)`。
  - `LibraryQueryService`（都先 `require()`）：
    - `LibraryOverviewView overview()`
    - `PageView<LibraryItemView> libraryItems(String view, String sort, int page, int size)`
    - `PageView<LibraryItemView> collectionItems(long collectionId, String sort, int page, int size)`（自己的夹，否则 404）
    - `LibraryItemView item(SubjectId itemId)`（不在库 → `LibraryItemNotFoundException`；收藏 / 改备注后控制器回它）
    - `List<ItemStatusView> status(String type, List<String> keys)`（1 ~ 100，否则 `InvalidCollectionRequestException`；按去重后的请求顺序）
    - `ExportKeysView libraryKeys(String view, String sort)`、`ExportKeysView collectionKeys(long collectionId, String sort)`
  - `PublicCollectionQueryService`（`current()` 可空）：
    - `PublicCollectionView collection(long collectionId)`
    - `PageView<PublicItemView> items(long collectionId, String sort, int page, int size)`
    - `ExportKeysView keys(long collectionId, String sort)`
    - `List<CollectionSummary> userCollections(long userId)`：本人 → `findPublicCollections`；别人 → `findCards([userId], viewer)` 可见才查，否则 `List.of()`；identity 不可用 → 抛 503
  - 分页算法：`ADDED` 走 `page*` SQL（offset = `(page-1)*size`）+ `countView` / `itemCount` 作 total；`PUBLISHED`/`JOURNAL` 走 `all*` → `SubjectViews.fetch` → `LibraryOrdering.order` → 切页，total = 列表长度。之后对当前页的 ID：`findByIds`（排序路径已有就复用）、`findEntries`（取备注、收藏时间）、`findMemberships`。
  - 导出 key：`all*` → `fetch` → `order` → 留 `ACTIVE && key != null` → 超 5000 抛 `ExportTooLargeException`。

- [ ] **Step 1: 写失败测试**

```java
// LibraryOrderingTest
@Test void should_sort_published_desc_with_coarse_dates_after_finer_and_nulls_last() {
  // 快照：A "2019"、B "2019-03"、C "2020"、D null、E "2019-03-15" → [C, E, B, A, D]
}
@Test void should_sort_journal_case_insensitively_nulls_last() { /* "lancet"、"BMJ"、null、"Cell" → [BMJ, Cell, lancet, null] */ }
@Test void should_break_ties_by_added_time() { … }
// LibraryQueryServiceTest
@Test void should_page_added_sort_via_sql_only_for_page_ids() { /* pageLibrary 被调、allLibrary 没调；findByIds 只传本页 ID */ }
@Test void should_sort_in_memory_for_journal() { /* allLibrary 被调、按期刊切第 2 页 */ }
@Test void should_show_snapshot_title_only_when_unavailable() { /* ACTIVE → title null；UNAVAILABLE 且 key null → title 快照、key null */ }
@Test void should_report_status_for_every_requested_key() {
  // keys ["1","x","2","1"] → 3 行按 ["1","x","2"]；"1" 已收藏、在 [A,B]、有备注；"x" 不合法 → collected=false；"2" 未登记 → false
}
@Test void should_reject_101_status_keys() { … }
@Test void should_skip_unavailable_in_export_keys_and_keep_list_order() { … ExportKeysView(["k2","k1"], 1) }
@Test void should_reject_export_over_5000() { … ExportTooLargeException }
// PublicCollectionQueryServiceTest
@Test void should_return_empty_profile_list_when_owner_not_visible() { /* profileVisible 假 → List.of()；缺席 → List.of() */ }
@Test void should_list_own_public_collections_without_identity() { verifyNoInteractions(users); }
@Test void should_hide_notes_in_public_items() { /* PublicItemView 没有 note 字段：以类型保证；断言 findEntries 未被调用 */ }
```

- [ ] **Step 2: 跑确认失败**
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑确认通过**

---

### Task 11: 注销清理（用例 + XXL-Job）

**Files:**
- Create: `…/app/collection/usecase/cleanup/CleanupDeletedUsersCommand.java`、`CleanupDeletedUsersHandler.java`、`CleanupResult.java`
- Create: `patra-api/patra-interaction/patra-interaction-adapter/src/main/java/dev/patra/interaction/adapter/collection/scheduler/DeletedUserCleanupScheduleJob.java`
- Test: `…/app/src/test/java/dev/patra/interaction/app/collection/usecase/cleanup/CleanupDeletedUsersHandlerTest.java`、`…/adapter/src/test/java/dev/patra/interaction/adapter/collection/scheduler/DeletedUserCleanupScheduleJobTest.java`

**Interfaces:**
- Produces:
  - `CleanupDeletedUsersCommand() implements Command<CleanupResult>` + `of()`。
  - `@Builder CleanupResult(int scannedUsers, int deletedUsers, int libraryItems, int collections, int collectionItems, int failedBatches)`。
  - Handler：`findOwnersAfter(cursor, 100)` 循环；每批 `findCards(batch, null)`，`UserDirectoryUnavailableException` → `failedBatches++`、WARN、继续；`state == DELETED` 的每人一个事务：`lockOwner` → `deleteAllOf`，累加行数；缺席的 WARN 不删；结束 INFO 一行汇总。
  - Job：`@XxlJob("deletedUserCleanupJob")`，结构照 `SubjectReconciliationScheduleJob`：`failedBatches > 0` → `handleFail`，否则 `handleSuccess("已核对 %d 人：清理 %d 人，条目 %d、收藏夹 %d")`；`RuntimeException` → `handleFail`。

- [ ] **Step 1: 写失败测试**

```java
@Test void should_delete_only_deleted_users() { /* 批 [1,2,3]：1 DELETED、2 ACTIVE、3 缺席 → 只 deleteAllOf(1)；scanned 3、deleted 1 */ }
@Test void should_skip_batch_when_identity_down_and_continue() {
  // 第一批 [1..100] 抛 503，第二批 [101] DELETED → failedBatches 1、deleteAllOf(101)
}
@Test void should_advance_cursor_by_last_user_id() { /* findOwnersAfter(0,100) 回 100 个 → 下一次 findOwnersAfter(第 100 个的 id, 100) */ }
// Job 测试：照 SubjectReconciliationScheduleJobTest，mockStatic XxlJobHelper 验证 handleFail / handleSuccess
```

- [ ] **Step 2: 跑确认失败** — Run: `./gradlew :patra-api:patra-interaction:patra-interaction-app:test :patra-api:patra-interaction:patra-interaction-adapter:test --tests "*Cleanup*"`
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑确认通过**

---

### Task 12: REST 控制器

**Files:**
- Create: `patra-api/patra-interaction/patra-interaction-adapter/src/main/java/dev/patra/interaction/adapter/collection/rest/MyLibraryController.java`、`MyCollectionController.java`、`PublicCollectionController.java`
- Create: `…/adapter/collection/rest/request/`：`CollectionTextRequest(String name, String description)`、`VisibilityRequest(String visibility)`、`NoteRequest(String note)`、`ItemIdsRequest(List<Long> itemIds)`、`StatusRequest(String type, List<String> keys)`、`PageRequest`（`@Min(1) Integer page`、`@Min(1) @Max(100) Integer size`，默认 1 / 50）
- Create: `…/adapter/collection/rest/response/`：`CollectResponse(boolean created, LibraryItemResponse item)`、`LibraryItemResponse`、`CollectionResponse`、`LibraryOverviewResponse`、`ItemStatusResponse`、`StatusResponse(List<ItemStatusResponse> items)`、`ExportKeysResponse`、`PublicCollectionResponse`（含 `OwnerResponse(userId, username, displayName, avatarUrl, avatarPalette, deleted)`）、`PublicItemResponse`、`PublicCollectionCardResponse`、`UserCollectionsResponse(List<PublicCollectionCardResponse> collections)`、`CopyResultResponse`、`AddToCollectionResponse`、`PageResponse<T>`
- Modify: `patra-api/patra-interaction/patra-interaction-adapter/build.gradle.kts`（若需 `spring-boot-starter-validation` / web 依赖，照 identity-adapter）

**Interfaces:**
- Consumes: `CommandBus`、Task 10 两个查询服务。
- Produces: spec §6.1 ~ §6.3 的全部路由，字段名照 spec（`itemId`、`collectionId`、`userId` 为 Long，由 tool-web 序列化为字符串；`type` 用 `SubjectType.slug()`；`state`、`visibility` 用枚举名）。
  - `PUT /me/library/{type}/{key}` → `CollectItemCommand` 后 `libraryQuery.item(itemId)` 组 `CollectResponse`。
  - `PUT /me/library/items/{itemId}/note` → `EditNoteCommand` 后回 `item(itemId)`。
  - `DELETE` 的 `itemIds` 走 `@RequestParam List<Long>`（逗号分隔绑定）。
  - `POST /me/collections` 回 201；`POST /collections/{id}/copies` 回 201；DELETE 回 204。
  - `owner.deleted = state == DELETED`；`PublicCollectionResponse.visibility` 照给。

本任务不单写控制器测试：路由、序列化、状态码由 Task 13 的流程 IT 逐条覆盖（先写 Task 13 的用例再写控制器亦可，按 TDD 顺序：Task 13 Step 1 先红，再回来实现本任务）。

- [ ] **Step 1: 先完成 Task 13 Step 1（流程 IT 红）**
- [ ] **Step 2: 实现控制器与 DTO**
- [ ] **Step 3: 跑 Task 13 的流程 IT 转绿**（见 Task 13 Step 3）

---

### Task 13: 完整流程 IT（真实 PG + WireMock 扮 catalog 与 identity + 真实断言验签）

**Files:**
- Create: `patra-api/patra-interaction/patra-interaction-boot/src/integrationTest/java/dev/patra/interaction/collection/CollectionFlowIT.java`
- Create: `…/boot/src/integrationTest/java/dev/patra/interaction/collection/CollectionFlowITSupport.java`（WireMock 桩、断言头、HTTP 封装；非测试类）

**Interfaces:**
- Consumes: Task 12 的路由；`TestIdentity.headers(long userId)`；`SubjectRegistryFlowIT` 的 catalog 桩写法。
- 配置：`@SpringBootTest(webEnvironment = RANDOM_PORT)`、`@AutoConfigureRestTestClient`、两个 `WireMockExtension`（`patra-catalog`、`patra-identity` 各一个 simple discovery 实例）、`@BeforeEach` TRUNCATE `subject.registry` 与三张 collection 表。
- identity 桩：`POST /_internal/users/summaries` 按请求体里的 `userIds` / `viewerUserId` 回预设（WireMock `equalToJson` 匹配，或用一个按用户 ID 查表的 `ResponseDefinitionTransformer`；选前者，用例里逐个 stub）。

- [ ] **Step 1: 写失败 IT（每条一个 `@Test`，对应 AC 与 Review Focus）**

```java
@Test void should_collect_idempotently_and_remove_idempotently() {
  // catalog by-ids 回 100 → PUT /me/library/publication/100 两次：200，created true / false，itemId 相同
  // DELETE /me/library/items?itemIds={id} 两次都 204；GET /me/library 的 total = 0
}
@Test void should_keep_items_when_collection_deleted_and_remove_from_all_on_uncollect() {
  // 收 3 篇，建「组会」「综述」，S1 放进两个、S2 放进「组会」→ 删「综述」→ /me/library: total 3、unfiled 1（S3）
  // 移除 S1 → 「组会」itemCount 1；GET /me/collections/{组会}/items 只剩 S2
}
@Test void should_gate_publishing_by_identity_state() {
  // 对 ACTIVE+未验证 → 409、traits 含 EMAIL_UNVERIFIED；BANNED → ACCOUNT_RESTRICTED；identity 503 → 503；ACTIVE+已验证 → 200 visibility PUBLIC
}
@Test void should_404_public_collection_when_owner_profile_hidden() {
  // A 公开「组会」；identity 对匿名回 profileVisible=false → GET /collections/{id} 404；对 B 回 true → 200 带 owner；
  // A 自己读私有夹 → 200；B 读 A 的私有夹 → 404；identity 省略 A → 404；identity 503 → 503
}
@Test void should_copy_public_collection_with_counts() {
  // A 的公开夹 3 篇（其中 1 篇对账后 UNAVAILABLE），B 已收其中 1 篇、已有同名夹 → 201，name「组会 (2)」，added 1、already 1、skipped 1；B 的新夹无备注
}
@Test void should_clean_up_deleted_user_without_touching_copies() {
  // A 公开 → B 复制 → identity 对 [A, B] 回 A DELETED、B ACTIVE → commandBus.handle(CleanupDeletedUsersCommand.of())
  // → A 三表 0 行；B 的复制品与条目都在
}
@Test void should_report_status_for_100_keys_in_one_request() {
  // 预置：1 篇已收且在两个夹、有备注；其余 99 个 key 混合未登记与不合法 → 200、100 行、第一行 collectionIds 两个、hasNote true；101 个 → 422
}
@Test void should_return_clear_errors_at_limits() {
  // 用 JdbcTemplate 直接灌 20,000 行 library_item（generate_series）→ 再收一篇 409 LIBRARY_FULL；
  // 灌 200 个收藏夹 → 新建 409 COLLECTION_LIMIT_REACHED；重名 409 COLLECTION_NAME_TAKEN + errors name/TAKEN
}
@Test void should_manage_vanished_item_by_item_id() {
  // 收藏后对账让 catalog 整行消失（by-pmids 回 exists=false）→ 列表里 key null、title 快照、state UNAVAILABLE；
  // 改备注 200、DELETE 204；导出 key 不含它、skippedUnavailable 1
}
@Test void should_require_login_for_my_endpoints() { /* 不带头 GET /me/library → 401 */ }
```

- [ ] **Step 2: 跑确认失败** — Run: `./gradlew :patra-api:patra-interaction:patra-interaction-boot:integrationTest --tests "*CollectionFlowIT"`；Expected: 404 / 编译失败。
- [ ] **Step 3: 完成 Task 12 后再跑** — 同 Step 2；Expected: PASS。参数越界（`size=101`、`page=0`）的实际状态码以 tool-web 映射为准，与 spec §6.4 不一致时改 spec 与共享笔记，不改 tool-web。

---

### Task 14: 文档、共享笔记与全量门控

**Files:**
- Modify: `patra-api/patra-interaction/README.md`（§1 collection 一行补工程设计链接；新增「collection：表、锁、排序、上限」小节，不列端点）
- Modify: `docs/patra/runbooks/v0.13-library-go-live-runbook.md`（第 6 步之后：登记 `deletedUserCleanupJob`、手动触发核对日志）
- Create: `/Users/linqibin/Library/Application Support/Claude/side-session-notes/local_c04766c5-6ac7-4771-97aa-718035535462/PAP-113-collection-contract.md`（spec §6 的接口、字段、错误码表 + `itemId` 语义 + 撤销即重新收藏 + 已失效展示 + 实测的参数越界状态码）

- [ ] **Step 1: 写三处文档**
- [ ] **Step 2: spotless 与模块门控**

Run: `./gradlew :patra-api:patra-interaction:spotlessApply && ./gradlew :patra-api:patra-interaction:check :patra-api:patra-interaction:patra-interaction-infra:integrationTest :patra-api:patra-interaction:patra-interaction-boot:integrationTest`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 全量门控（后台跑）**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL；若 `dumpModuleGraph` / `detect-changes.test.sh` 因 interaction 新增依赖（identity-api、tool-security）报模块图过期，重跑 `./gradlew dumpModuleGraph` 并纳入改动。

- [ ] **Step 4: 向用户报告改动与验证结果，等同意后再提交并快进合回 `feat/v0.13-library-api`**
