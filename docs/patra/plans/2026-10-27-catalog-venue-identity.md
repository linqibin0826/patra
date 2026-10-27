# catalog 期刊身份与批量接口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task（本会话内联执行，用户已定不用 subagent-driven）。Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 NLM ID 唯一且稳定地标识一个期刊，并提供 interaction 用的内部批量解析接口与门户批量期刊卡片接口（PAP-117）。

**Architecture:** 迁移 V12 给 `cat_venue.nlm_id` 与标识符表的 NLM 值各加一条唯一约束；期刊聚合显式记录写一次的主 NLM；LSIOU 导入改为 NLM 优先匹配并跳过软删期刊的 NLM。读端照 PAP-109：`patra-catalog-api` 的 `@HttpExchange` 契约 → 端点实现 → 无接口查询服务 → CQRS 读端口 → native SQL；门户批量卡片复用期刊浏览卡片的投影。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Spring Data JPA / Hibernate 7（`@SoftDelete`）、PostgreSQL 17、Flyway、JUnit 5 + AssertJ + Mockito、Testcontainers。

**Spec:** `docs/patra/specs/2026-10-27-catalog-venue-identity-design.md`

## Global Constraints

- 迁移文件 `V12__add_venue_nlm_unique.sql`；约束名 `uk_venue_nlm_id`（`cat_venue (nlm_id)`）、`uk_venue_identifier_nlm`（`cat_venue_identifier (identifier_value) WHERE identifier_type = 'NLM'`）。
- NLM ID 一律是字符串，保留前导零；校验写法 `^[0-9]{1,19}[A-Z]?$`，区分大小写。
- 批量接口 1 ~ 100 个键；元素不合规整个请求 422；带 `null` 元素 400；查不到的键 `exists: false`；软删 `DELETED`；只认 `venue_type = 'JOURNAL'`。
- 内部契约路径 `/_internal/venues/resolve/by-ids`（字段 `ids`）、`/_internal/venues/resolve/by-nlm-ids`（字段 `nlmIds`）；门户 `GET /portal/venues/batch?id=…`，响应 `{ "items": [...] }`。
- 代码规范：所有方法写 `///` JavaDoc（Markdown）；record 参数 ≤ 4 个用 `of()`，≥ 5 个用 `@Builder`（语义工厂如 `missing()` 可并存）；带集合的 record 在紧凑构造器里 `List.copyOf`；不写全类名；`///` 行不超过 100 列（gjf 会把超长的折成 `//`）。
- 测试方法 snake_case；单测 `{类}Test`，集成测试 `{类}IT`，端到端 `{场景}E2E`。
- worktree 里不用 JetBrains MCP；不 `cd` 出 worktree（一律绝对路径或从 worktree 根跑 `./gradlew`）。
- **不提交**：PAP-117 全部做完、汇报并经用户同意后才提交（Task 14）。提交信息照 commitlint：`feat(catalog): ` 前缀 + 中文动词开头的 subject，body 每行 ≤ 100 字。
- 每个任务的验证前先 `./gradlew spotlessApply`（`check` 含 `spotlessJavaCheck`）。

## Review Focus

1. LSIOU 记录的 NLM 属于软删期刊：导入跳过这条，不新建、不并进 ISSN-L 命中的别的期刊，整批不因约束回滚（Task 6 单测 + Task 7 E2E）。
2. 合并期刊（主 + 副 NLM）反复「读出 → 更新」后主 NLM 不变，即使标识符表里副 NLM 排在前面（Task 3 IT + Task 7 第二次导入）。
3. 一次请求同时带一个期刊的主、副 NLM：两行、`id` 相同、`nlmId` 都是主 NLM（Task 8 单测 + Task 9 IT + Task 10 IT）。
4. NLM 键的边界写法：前导零、`R` / `A` 后缀、16 位新式 ID 能通过；小写后缀、中间夹字母、超长、空串、尾随空格 422（Task 10 IT）。
5. 非期刊类型的载体 ID：解析返回 `exists: false`，批量卡片不返回（Task 9 IT + Task 11 IT）。

---

命令里的模块前缀：`CAT=:patra-api:patra-catalog`（下文直接写全路径）。所有命令从 worktree 根 `/Users/linqibin/Projects/Products/patra/.claude/worktrees/v014-catalog-e0526e` 运行。

### Task 1: V12 迁移与 NLM 唯一约束

**Files:**
- Create: `patra-api/patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V12__add_venue_nlm_unique.sql`
- Modify: `patra-api/patra-catalog/patra-catalog-infra/src/main/java/dev/patra/catalog/infra/persistence/entity/VenueEntity.java`（`@Table` 的 `@Index(name = "idx_nlm_id")` 换成 `uniqueConstraints = @UniqueConstraint(name = "uk_venue_nlm_id", columnNames = "nlm_id")`）
- Test: `patra-api/patra-catalog/patra-catalog-infra/src/integrationTest/java/dev/patra/catalog/infra/adapter/persistence/VenueRepositoryAdapterIT.java`

**Interfaces:**
- Produces: 两条约束（后续任务的 IT 依赖它们拒绝重复）。

- [ ] **Step 1: 写失败的测试**——新增 `@Nested @DisplayName("NLM 全局唯一（V12）") class NlmUniquenessTests`，注入 `TestEntityManager em`：

```java
@Test
void should_reject_second_venue_with_same_primary_nlm() {
  repository.insertAll(List.of(VenueAggregate.fromPubMed("Journal A", "0404401", "1111-1111")));
  em.flush();
  assertThatThrownBy(() -> {
        repository.insertAll(List.of(VenueAggregate.fromPubMed("Journal B", "0404401", "2222-2222")));
        em.flush();
      })
      .isInstanceOfAny(PersistenceException.class, DataIntegrityViolationException.class)
      .hasStackTraceContaining("uk_venue_nlm_id");
}

@Test
void should_reject_nlm_identifier_already_owned_by_another_venue() {
  repository.insertAll(List.of(
      VenueAggregate.fromPubMed("Journal A", "0404401", "1111-1111"),
      VenueAggregate.fromPubMed("Journal B", "100970884", "2222-2222")));
  em.flush();
  em.clear();
  VenueAggregate b = repository.findByNlmIds(Set.of("100970884")).get("100970884");
  b.addIdentifier(VenueIdentifier.forNlm("0404401"));
  assertThatThrownBy(() -> {
        repository.updateBatch(List.of(b));
        em.flush();
      })
      .isInstanceOfAny(PersistenceException.class, DataIntegrityViolationException.class)
      .hasStackTraceContaining("uk_venue_identifier_nlm");
}

@Test
void should_allow_many_venues_without_nlm() {
  repository.insertAll(List.of(
      VenueAggregate.fromPubMed("Journal A", null, "1111-1111"),
      VenueAggregate.fromPubMed("Journal B", null, "2222-2222")));
  em.flush();
  assertThat(venueDao.findAll()).extracting(VenueEntity::getNlmId).containsOnlyNulls().hasSize(2);
}
```

- [ ] **Step 2: 跑测试，确认前两条失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest --tests "*VenueRepositoryAdapterIT*NlmUniqueness*"`
Expected: `should_reject_second_venue_with_same_primary_nlm`、`should_reject_nlm_identifier_already_owned_by_another_venue` FAIL（没有抛异常）；第三条 PASS。

- [ ] **Step 3: 写 V12**——内容照 spec 第 6.1 节两条语句，文件头注释写明 PAP-117、上线前核对见 spec 第 11 节；同时改 `VenueEntity` 的注解。

- [ ] **Step 4: 跑测试，确认通过**

Run: 同 Step 2，再跑整类 `--tests "*VenueRepositoryAdapterIT*"`
Expected: 全部 PASS（现有用例里同一测试内的 NLM 互不相同）。

### Task 2: 期刊聚合的主 NLM

**Files:**
- Modify: `patra-api/patra-catalog/patra-catalog-domain/src/main/java/dev/patra/catalog/domain/model/aggregate/VenueAggregate.java`
- Modify（`restore` 签名变了，同步调用方）：`patra-catalog-infra/.../persistence/converter/mapper/VenueJpaMapper.java`（`toAggregate` 传 `entity.getNlmId()`）、`patra-catalog-domain/src/test/.../aggregate/VenueAggregateImageKeyTest.java`、`patra-catalog-domain/src/test/.../aggregate/VenueAggregateTest.java`、`patra-catalog-app/src/test/.../venue/pubmed/VenuePubmedImportHandlerTest.java`（`createExistingVenue` 传 `null`，靠 `addIdentifier` 产生主 NLM）
- Test: `patra-api/patra-catalog/patra-catalog-domain/src/test/java/dev/patra/catalog/domain/model/aggregate/VenueAggregateTest.java`

**Interfaces:**
- Produces:
  - `VenueAggregate.restore(VenueId id, VenueType venueType, String title, String imageObjectKey, String nlmId, Long version)`
  - `String VenueAggregate.getNlmId()`（Lombok `@Getter` 生成；字段 `private String nlmId`）
  - `addIdentifier(VenueIdentifier)`：类型为 NLM 且 `nlmId == null` 时把它设为主 NLM
  - `removeIdentifier(VenueIdentifierType, String)`：移除主 NLM 时抛 `IllegalStateException`（`Assert.state`）

- [ ] **Step 1: 写失败的测试**——新增 `@Nested @DisplayName("主 NLM") class PrimaryNlmTests`：

```java
@Test void fromPubMed_sets_primary_nlm() {
  assertThat(VenueAggregate.fromPubMed("J", "0404401", "0036-7672").getNlmId()).isEqualTo("0404401");
}
@Test void fromPubMed_without_nlm_has_no_primary() {
  assertThat(VenueAggregate.fromPubMed("J", null, "0036-7672").getNlmId()).isNull();
}
@Test void restore_keeps_persisted_primary_nlm() {
  VenueAggregate v = VenueAggregate.restore(VenueId.of(1L), VenueType.JOURNAL, "J", null, "100970884", 0L);
  v.addIdentifier(VenueIdentifier.forNlm("0404401"));
  v.addIdentifier(VenueIdentifier.forNlm("100970884"));
  assertThat(v.getNlmId()).isEqualTo("100970884");
}
@Test void first_added_nlm_becomes_primary_when_absent() {
  VenueAggregate v = VenueAggregate.restore(VenueId.of(1L), VenueType.JOURNAL, "J", null, null, 0L);
  v.addIdentifier(VenueIdentifier.forNlm("0404401"));
  v.addIdentifier(VenueIdentifier.forNlm("100970884"));
  assertThat(v.getNlmId()).isEqualTo("0404401");
  assertThat(v.getIdentifiers(VenueIdentifierType.NLM)).containsExactly("0404401", "100970884");
}
@Test void removing_primary_nlm_is_rejected() {
  VenueAggregate v = VenueAggregate.fromPubMed("J", "0404401", null);
  assertThatThrownBy(() -> v.removeIdentifier(VenueIdentifierType.NLM, "0404401"))
      .isInstanceOf(IllegalStateException.class);
  assertThat(v.getIdentifiers(VenueIdentifierType.NLM)).containsExactly("0404401");
}
@Test void removing_secondary_nlm_keeps_primary() {
  VenueAggregate v = VenueAggregate.fromPubMed("J", "0404401", null);
  v.addIdentifier(VenueIdentifier.forNlm("100970884"));
  assertThat(v.removeIdentifier(VenueIdentifierType.NLM, "100970884")).isTrue();
  assertThat(v.getNlmId()).isEqualTo("0404401");
}
```

另把现有 `toString()` 用例的断言对齐「`nlmId=` 取主 NLM」。

- [ ] **Step 2: 跑测试，确认失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-domain:test --tests "*VenueAggregateTest*"`
Expected: 编译失败（`getNlmId` / 6 参 `restore` 不存在）。

- [ ] **Step 3: 实现**——字段、`fromPubMed`（经 `addIdentifier` 产生主 NLM）、6 参 `restore`、`addIdentifier` 的主 NLM 规则、`removeIdentifier` 守卫、`toString` 用 `nlmId`；类 JavaDoc 写明两条不变式（spec 第 6.2 节）。同步改所有 `restore` 调用方：mapper 传 `entity.getNlmId()`，测试传原值或 `null`。

- [ ] **Step 4: 跑测试，确认通过且下游能编译**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-domain:test :patra-api:patra-catalog:patra-catalog-app:compileTestJava :patra-api:patra-catalog:patra-catalog-infra:compileIntegrationTestJava`
Expected: PASS / BUILD SUCCESSFUL。

### Task 3: 主 NLM 落库不再随标识符顺序改变

**Files:**
- Modify: `patra-api/patra-catalog/patra-catalog-infra/src/main/java/dev/patra/catalog/infra/persistence/converter/mapper/VenueJpaMapper.java`（`populateQuickAccessFields`：`nlmId` 改写 `aggregate.getNlmId()`，为 null 时不写）
- Test: `VenueRepositoryAdapterIT`

**Interfaces:**
- Consumes: Task 2 的 `getNlmId()`。

- [ ] **Step 1: 写失败的测试**——新增 `@Nested @DisplayName("主 NLM 稳定") class PrimaryNlmStabilityTests`。用 native SQL 造一个「列是 `100970884`、标识符表里 `0404401` 先插入」的期刊，读出再更新两次：

```java
@Test
void should_keep_primary_nlm_when_another_nlm_comes_first_in_identifiers() {
  em.getEntityManager().createNativeQuery(
      "INSERT INTO cat_venue (id, venue_type, title, provenance_code, nlm_id)"
          + " VALUES (1001, 'JOURNAL', 'Swiss medical weekly', 'PUBMED', '100970884')").executeUpdate();
  em.getEntityManager().createNativeQuery(
      "INSERT INTO cat_venue_identifier (id, venue_id, identifier_type, identifier_value)"
          + " VALUES (2001, 1001, 'NLM', '0404401'), (2002, 1001, 'NLM', '100970884')").executeUpdate();
  for (int round = 0; round < 2; round++) {
    VenueAggregate v = repository.findByNlmIds(Set.of("100970884")).get("100970884");
    repository.updateBatch(List.of(v));
    em.flush();
    em.clear();
    assertThat(venueDao.findById(1001L)).get().extracting(VenueEntity::getNlmId).isEqualTo("100970884");
  }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest --tests "*VenueRepositoryAdapterIT*PrimaryNlmStability*"`
Expected: FAIL，`nlm_id` 变成 `0404401`。

- [ ] **Step 3: 改 `populateQuickAccessFields`**，并更新 `VenueEntity` 类注释里快速访问字段表 `nlm_id` 一行（「主 NLM，写一次」）。

- [ ] **Step 4: 跑整类 `VenueRepositoryAdapterIT`，确认通过**

### Task 4: `findByNlmIds` 主、副 NLM 都返回

**Files:**
- Modify: `patra-api/patra-catalog/patra-catalog-infra/src/main/java/dev/patra/catalog/infra/adapter/persistence/VenueRepositoryAdapter.java`（`findByNlmIds` 按标识符行逐条放进结果）；`patra-catalog-domain/.../port/repository/VenueRepository.java` 的 `findByNlmIds` JavaDoc（「主、副 NLM 都命中；只返回未软删的期刊；优先级最高」）
- Test: `VenueRepositoryAdapterIT`

- [ ] **Step 1: 写失败的测试**（放进现有 `findByNlmIds` 的 Nested 类）：

```java
@Test
void should_return_every_requested_nlm_of_a_merged_venue() {
  repository.insertAll(List.of(VenueAggregate.fromPubMed("J", "0404401", "0036-7672")));
  em.flush();
  em.clear();
  VenueAggregate v = repository.findByNlmIds(Set.of("0404401")).get("0404401");
  v.addIdentifier(VenueIdentifier.forNlm("100970884"));
  repository.updateBatch(List.of(v));
  em.flush();
  em.clear();

  Map<String, VenueAggregate> result = repository.findByNlmIds(Set.of("0404401", "100970884"));

  assertThat(result).containsOnlyKeys("0404401", "100970884");
  assertThat(result.get("100970884").getId()).isEqualTo(result.get("0404401").getId());
  assertThat(result.get("100970884").getNlmId()).isEqualTo("0404401");
}
```

- [ ] **Step 2: 跑测试，确认失败**（只返回一个键）

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest --tests "*VenueRepositoryAdapterIT*"`

- [ ] **Step 3: 实现**——遍历 `identifierEntities`，`result.put(row.getIdentifierValue(), venueIdToAggregate.get(row.getVenueId()))`（聚合为 null 即软删被 JPA 过滤，跳过）；删掉 `venueIdToNlmId`。

- [ ] **Step 4: 跑整类，确认通过**

### Task 5: 查出属于软删期刊的 NLM

**Files:**
- Modify: `patra-catalog-domain/.../port/repository/VenueRepository.java`（加方法）
- Modify: `patra-catalog-infra/.../persistence/dao/VenueIdentifierDao.java`（加 native 查询）
- Modify: `VenueRepositoryAdapter.java`（实现）
- Test: `VenueRepositoryAdapterIT`

**Interfaces:**
- Produces: `Set<String> VenueRepository.findSoftDeletedNlmIds(Collection<String> nlmIds)`——返回入参中属于已软删期刊的 NLM（主或副）；空入参返回空集合、不查库。
- Produces: `List<String> VenueIdentifierDao.findNlmValuesOfSoftDeletedVenues(String[] nlmIds)`，SQL 照 spec 第 6.3 节。

- [ ] **Step 1: 写失败的测试**——新增 `@Nested @DisplayName("findSoftDeletedNlmIds()") class FindSoftDeletedNlmIdsTests`：

```java
@Test
void should_find_primary_and_secondary_nlm_of_soft_deleted_venues_only() {
  repository.insertAll(List.of(
      VenueAggregate.fromPubMed("Active", "0404401", "1111-1111"),
      VenueAggregate.fromPubMed("Gone", "0000003R", "3333-3333")));
  em.flush();
  em.clear();
  VenueAggregate gone = repository.findByNlmIds(Set.of("0000003R")).get("0000003R");
  gone.addIdentifier(VenueIdentifier.forNlm("0000005"));
  repository.updateBatch(List.of(gone));
  em.flush();
  em.getEntityManager().createNativeQuery(
      "UPDATE cat_venue SET deleted_at = now() WHERE nlm_id = '0000003R'").executeUpdate();
  em.clear();

  assertThat(repository.findSoftDeletedNlmIds(List.of("0404401", "0000003R", "0000005", "9999999")))
      .containsExactlyInAnyOrder("0000003R", "0000005");
  assertThat(repository.findByNlmIds(Set.of("0000003R"))).isEmpty();
  assertThat(repository.findSoftDeletedNlmIds(List.of())).isEmpty();
}
```

- [ ] **Step 2: 跑测试，确认编译失败**
- [ ] **Step 3: 实现端口方法、Dao 查询（`nativeQuery = true`）与适配器**
- [ ] **Step 4: 跑整类 `VenueRepositoryAdapterIT`，确认通过**

### Task 6: 导入改为 NLM 优先，跳过软删期刊的 NLM

**Files:**
- Modify: `patra-api/patra-catalog/patra-catalog-app/src/main/java/dev/patra/catalog/app/usecase/venue/pubmed/VenuePubmedImportHandler.java`
- Modify: `patra-catalog-app/.../venue/pubmed/command/VenuePubmedImportCommand.java`（JavaDoc 里的匹配顺序）
- Test: `patra-api/patra-catalog/patra-catalog-app/src/test/java/dev/patra/catalog/app/usecase/venue/pubmed/VenuePubmedImportHandlerTest.java`

**Interfaces:**
- Consumes: Task 5 的 `findSoftDeletedNlmIds`，Task 2 的主 NLM 规则。
- 内部：`ExistingVenuesLookup` 加第 4 个组件 `Set<String> softDeletedNlmIds`；`findMatch` 顺序改为 NLM → ISSN-L → ISSN Print → ISSN Electronic。

- [ ] **Step 1: 改写与新增测试**（`MatchingPriorityTest` 里现有的「优先级 1：ISSN-L」「优先级 2：NLM」两条按新顺序改写，新增软删用例）：

```java
@Test
void should_prefer_nlm_over_issn_l_when_they_point_to_different_venues() {
  PubmedSerialData record = createSerialData("0000001", "Test Journal", "1111-1111", null, null);
  VenueAggregate x = createExistingVenue("ISSN-L Matched", null, "1111-1111");
  VenueAggregate y = createExistingVenue("NLM ID Matched", "0000001", null);
  // findByIssnLs → {1111-1111: x}，findByNlmIds → {0000001: y}，findByIssns → {}
  handler.handle(command);
  verify(venueRepository).updateBatch(updateBatchCaptor.capture());
  assertThat(updateBatchCaptor.getValue()).containsExactly(y);
  assertThat(x.getIdentifiers(VenueIdentifierType.NLM)).isEmpty();
}

@Test
void should_fall_back_to_issn_l_and_adopt_unowned_nlm_as_primary() {
  // record NLM 0000009（无主）+ ISSN-L 1111-1111；x 只有 ISSN-L
  // 期望：更新 x，x.getNlmId() == "0000009"
}

@Test
void should_add_unowned_nlm_as_secondary_to_issn_l_match() {
  // x 主 NLM 0000001 + ISSN-L 1111-1111；record NLM 0000002（无主）+ ISSN-L 1111-1111
  // 期望：更新 x，x.getNlmId() == "0000001"，x.getIdentifiers(NLM) 含 0000001 与 0000002
}

@Nested
@DisplayName("软删期刊的 NLM")
class SoftDeletedVenueTest {
  @Test
  void should_skip_record_whose_nlm_belongs_to_soft_deleted_venue() {
    PubmedSerialData record = createSerialData("0000003R", "Gone Journal", "3333-3333", null, null);
    VenueAggregate x = createExistingVenue("Has same ISSN-L", null, "3333-3333");
    // findByIssnLs → {3333-3333: x}；findByNlmIds → {}；findSoftDeletedNlmIds → {"0000003R"}
    VenuePubmedImportResult result = handler.handle(command);
    assertThat(result.skippedCount()).isEqualTo(1);
    assertThat(result.updatedCount()).isZero();
    assertThat(result.createdCount()).isZero();
    verify(venueRepository, never()).insertAll(anyList());
    verify(venueRepository, never()).updateBatch(anyList());
    verify(venueRepository).findSoftDeletedNlmIds(Set.of("0000003R"));
  }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-app:test --tests "*VenuePubmedImportHandlerTest*"`
Expected: 新顺序与软删用例 FAIL（编译期没缺符号：`findSoftDeletedNlmIds` 已在 Task 5 加上）。

- [ ] **Step 3: 实现**——`processBatch` 多查一次 `findSoftDeletedNlmIds(identifiers.nlmIds())`；`matchAndClassifyRecords` 在 LSIOU 删除检查之后、匹配之前跳过软删期刊的 NLM（`result.markSkipped()` + debug 日志）；`findMatch` 改顺序与注释；类 JavaDoc「匹配策略」改为 NLM → ISSN-L → ISSN 并写一句软删跳过。
- [ ] **Step 4: 跑整个测试类，确认通过**（`BatchInternalDeduplicationTest` 等现有用例不应改动）

### Task 7: 端到端：真实处理器 + PG 跑两遍导入

**Files:**
- Create: `patra-api/patra-catalog/patra-catalog-boot/src/e2eTest/java/dev/patra/catalog/integration/venue/VenuePubmedImportE2E.java`
- Create: `patra-api/patra-catalog/patra-catalog-boot/src/testFixtures/resources/lsiou/lsiou-identity-e2e.xml`

**Interfaces:**
- Consumes: Task 1 ~ 6 的全部行为；`CommandBus.handle(VenuePubmedImportCommand.of("https://test.nlm.nih.gov/online/journals/lsi2026.xml", "2026"))`。

- [ ] **Step 1: 写夹具**（DOCTYPE 与 `patra-catalog-infra/src/test/resources/lsiou/multiple-serials.xml` 相同）：

```xml
<SerialsSet>
  <Serial Status="NLMCollection">  <!-- NLM 属于 Y、ISSN-L 属于 X：应更新 Y -->
    <NlmUniqueID>0000002</NlmUniqueID><Title>Journal Y</Title><MedlineTA>J Y</MedlineTA>
    <ISSNLinking>1000-0001</ISSNLinking><SortSerialName>JOURNAL Y</SortSerialName>
  </Serial>
  <Serial Status="NLMCollection">  <!-- NLM 属于软删的 Z：应跳过 -->
    <NlmUniqueID>0000003R</NlmUniqueID><Title>Journal Z</Title>
    <ISSNLinking>3000-0003</ISSNLinking><SortSerialName>JOURNAL Z</SortSerialName>
  </Serial>
  <Serial Status="NLMCollection">  <!-- NLM 无主、ISSN-L 属于 X：应并进 X 成为副 NLM -->
    <NlmUniqueID>0000004</NlmUniqueID><Title>Journal X Renamed</Title>
    <ISSNLinking>1000-0001</ISSNLinking><SortSerialName>JOURNAL X RENAMED</SortSerialName>
  </Serial>
  <Serial Status="NLMCollection">  <!-- 16 位新式 ID：应新建 -->
    <NlmUniqueID>9918523075306676</NlmUniqueID><Title>Journal New</Title>
    <ISSNLinking>4000-0004</ISSNLinking><SortSerialName>JOURNAL NEW</SortSerialName>
  </Serial>
</SerialsSet>
```

- [ ] **Step 2: 写测试**——`@SpringBootTest(properties = "spring.cloud.nacos.discovery.enabled=false")`、`@ContextConfiguration(initializers = CatalogE2EPostgreSQLContainerInitializer.class)`、`@ActiveProfiles("e2e-test")`；`@MockitoBean` `FileDownloadPort`（每次调用把夹具复制成新的临时文件返回，处理器会删掉它）、`DictionaryResolverPort`（返回 `Map.of()`）、`OpenAlexEnrichmentQueryPort`（返回 `Map.of()`）、`ObjectStorageOperations`。`@BeforeEach` TRUNCATE 所有 `cat_venue%` 表，再用 SQL 预置：X（主 NLM `0000001`、ISSN-L `1000-0001`，标识符 NLM + ISSN_L）、Y（主 NLM `0000002`，标识符 NLM）、Z（主 NLM `0000003R`，标识符 NLM，`deleted_at = now()`）。

```java
@Test
void should_merge_by_nlm_skip_soft_deleted_and_keep_primary_across_imports() {
  VenuePubmedImportResult first = commandBus.handle(command());
  assertThat(first).extracting(VenuePubmedImportResult::totalParsed, VenuePubmedImportResult::updatedCount,
          VenuePubmedImportResult::createdCount, VenuePubmedImportResult::skippedCount)
      .containsExactly(4, 2, 1, 1);
  assertIdentity();

  VenuePubmedImportResult second = commandBus.handle(command());
  assertThat(second).extracting(VenuePubmedImportResult::updatedCount, VenuePubmedImportResult::createdCount,
          VenuePubmedImportResult::skippedCount)
      .containsExactly(3, 0, 1);
  assertIdentity();
}

/// NLM 归属、主 NLM、期刊数、Z 仍软删。
private void assertIdentity() {
  assertThat(nlmOwners()).containsExactlyInAnyOrderEntriesOf(Map.of(
      "0000001", X_ID, "0000004", X_ID, "0000002", Y_ID, "0000003R", Z_ID,
      "9918523075306676", newVenueId()));
  assertThat(primaryNlm(X_ID)).isEqualTo("0000001");
  assertThat(jdbc.queryForObject("SELECT count(*) FROM cat_venue", Integer.class)).isEqualTo(4);
  assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM cat_venue WHERE id = ?",
      Boolean.class, Z_ID)).isTrue();
}
```

（`nlmOwners()`：`SELECT identifier_value, venue_id FROM cat_venue_identifier WHERE identifier_type = 'NLM'`；`newVenueId()`：`nlm_id = '9918523075306676'` 那一行的 `id`。）

- [ ] **Step 3: 跑测试，确认通过**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-boot:e2eTest --tests "*VenuePubmedImportE2E*"`
Expected: PASS。本用例在 Task 1 ~ 6 之后写，首跑即绿属预期；若失败，说明单元测试没覆盖到的集成缺口，按 superpowers:systematic-debugging 排查，不改断言迁就。

### Task 8: 解析读端口与查询服务

**Files:**
- Create: `patra-catalog-domain/src/main/java/dev/patra/catalog/domain/model/read/venue/VenueResolveReadModel.java`
- Create: `patra-catalog-domain/src/main/java/dev/patra/catalog/domain/port/read/VenueResolveReadPort.java`
- Create: `patra-catalog-app/src/main/java/dev/patra/catalog/app/usecase/venue/query/dto/VenueResolution.java`
- Create: `patra-catalog-app/src/main/java/dev/patra/catalog/app/usecase/venue/query/VenueResolveQueryService.java`
- Test: `patra-catalog-app/src/test/java/dev/patra/catalog/app/usecase/venue/query/VenueResolveQueryServiceTest.java`

**Interfaces:**
- Produces:
  - `@Builder record VenueResolveReadModel(Long id, String nlmId, String title, String abbreviatedTitle, boolean deleted)`
  - `VenueResolveReadPort`: `List<VenueResolveReadModel> findByIds(Collection<Long> ids)`；`Map<String, VenueResolveReadModel> findByNlmIds(Collection<String> nlmIds)`（键是命中的 NLM，可能是副 NLM）；空入参返回空、不查库
  - `record VenueResolution(String key, VenueResolveReadModel found)` + `static of(String, VenueResolveReadModel)`
  - `@Service VenueResolveQueryService`: `List<VenueResolution> resolveByIds(List<Long> ids)`、`List<VenueResolution> resolveByNlmIds(List<String> nlmIds)`，均 `@Transactional(readOnly = true)`

- [ ] **Step 1: 写失败的测试**（mock 读端口）：

```java
@Test
void should_output_one_row_per_distinct_id_in_request_order() {
  when(port.findByIds(List.of(3L, 1L, 2L))).thenReturn(List.of(model(1L, "0000001"), model(3L, "0000003")));
  List<VenueResolution> rows = service.resolveByIds(List.of(3L, 1L, 3L, 2L));
  assertThat(rows).extracting(VenueResolution::key).containsExactly("3", "1", "2");
  assertThat(rows.get(2).found()).isNull();
  verify(port, times(1)).findByIds(List.of(3L, 1L, 2L));
}

@Test
void should_map_secondary_nlm_key_to_venue_with_primary_nlm() {
  VenueResolveReadModel swiss = model(9L, "0404401");
  when(port.findByNlmIds(List.of("0404401", "100970884", "9999999")))
      .thenReturn(Map.of("0404401", swiss, "100970884", swiss));
  List<VenueResolution> rows = service.resolveByNlmIds(List.of("0404401", "100970884", "0404401", "9999999"));
  assertThat(rows).extracting(VenueResolution::key).containsExactly("0404401", "100970884", "9999999");
  assertThat(rows.get(1).found().nlmId()).isEqualTo("0404401");
  assertThat(rows.get(2).found()).isNull();
}
```

- [ ] **Step 2: 跑测试，确认编译失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-app:test --tests "*VenueResolveQueryServiceTest*"`

- [ ] **Step 3: 实现四个类**（去重用 `LinkedHashSet`，照 `PublicationResolveQueryService`）
- [ ] **Step 4: 跑测试，确认通过**

### Task 9: 解析读适配器与 SQL

**Files:**
- Create: `patra-catalog-infra/src/main/java/dev/patra/catalog/infra/adapter/read/VenueResolveRow.java`（`getId`、`getNlmId`、`getTitle`、`getAbbreviatedTitle`、`getDeleted`）
- Create: `patra-catalog-infra/src/main/java/dev/patra/catalog/infra/adapter/read/VenueNlmMatchRow.java`（`extends VenueResolveRow`，加 `getMatchedNlmId`）
- Create: `patra-catalog-infra/src/main/java/dev/patra/catalog/infra/persistence/dao/VenueResolveDao.java`（`extends JpaRepository<VenueEntity, Long>`）
- Create: `patra-catalog-infra/src/main/java/dev/patra/catalog/infra/adapter/read/VenueResolveReadAdapter.java`（`@Repository`，实现 `VenueResolveReadPort`）
- Test: `patra-catalog-infra/src/integrationTest/java/dev/patra/catalog/infra/adapter/read/VenueResolveReadAdapterIT.java`

**Interfaces:**
- Produces: `List<VenueResolveRow> VenueResolveDao.findResolveRowsByIds(Long[] ids)`、`List<VenueNlmMatchRow> VenueResolveDao.findResolveRowsByNlmIds(String[] nlmIds)`，SQL 照 spec 第 7.5 节（`= ANY((:ids)::bigint[])` / `(:nlmIds)::text[]`）。

- [ ] **Step 1: 写失败的测试**——`@DataJpaTest` + `CatalogITPostgreSQLContainerInitializer` + `@Import({VenueResolveReadAdapter.class, JpaAuditingConfiguration.class})`；夹具用 `VenueEntity` / `VenueIdentifierEntity` 落库，软删用 native `UPDATE cat_venue SET deleted_at = now()`：

```java
@Test
void should_return_active_and_soft_deleted_journals_by_ids_and_ignore_others() {
  long active = journal("Lancet (London, England)", "Lancet", "2985213R");
  long deleted = journal("Gone Journal", "Gone J", "0000003R");
  long repository = venue("REPOSITORY", "Some repository", null);
  softDelete(deleted);
  Map<Long, VenueResolveReadModel> byId = index(adapter.findByIds(List.of(active, deleted, repository, 999L)));
  assertThat(byId).containsOnlyKeys(active, deleted);
  assertThat(byId.get(active)).extracting(VenueResolveReadModel::nlmId, VenueResolveReadModel::title,
      VenueResolveReadModel::abbreviatedTitle, VenueResolveReadModel::deleted)
      .containsExactly("2985213R", "Lancet (London, England)", "Lancet", false);
  assertThat(byId.get(deleted).deleted()).isTrue();
}

@Test
void should_resolve_primary_and_secondary_nlm_to_the_same_journal() {
  long swiss = journal("Schweizerische medizinische Wochenschrift", "Swiss Med Wkly", "0404401");
  secondaryNlm(swiss, "100970884");
  Map<String, VenueResolveReadModel> byNlm = adapter.findByNlmIds(List.of("0404401", "100970884", "9999999"));
  assertThat(byNlm).containsOnlyKeys("0404401", "100970884");
  assertThat(byNlm.values()).extracting(VenueResolveReadModel::id, VenueResolveReadModel::nlmId)
      .containsOnly(tuple(swiss, "0404401"));
}

@Test
void should_include_soft_deleted_and_exclude_non_journal_by_nlm() { /* 软删期刊命中且 deleted=true；REPOSITORY 类型带 NLM 不命中 */ }

@Test
void should_return_empty_for_empty_input() {
  assertThat(adapter.findByIds(List.of())).isEmpty();
  assertThat(adapter.findByNlmIds(List.of())).isEmpty();
}
```

（`journal(...)` 同时写 `cat_venue.nlm_id` 与一行 NLM 标识符，符合主 NLM 在标识符里的不变式。）

- [ ] **Step 2: 跑测试，确认编译失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest --tests "*VenueResolveReadAdapterIT*"`

- [ ] **Step 3: 实现两个投影、Dao、适配器**（文本块以换行结束再拼 `WHERE`，避免吃掉空格）
- [ ] **Step 4: 跑测试，确认通过**

### Task 10: 内部契约与端点

**Files:**
- Create（`patra-catalog-api/src/main/java/dev/patra/catalog/api/dto/venue/`）：`VenueResolveByIdsReq.java`、`VenueResolveByNlmIdsReq.java`、`VenueResolveResp.java`、`VenueResolutionResp.java`、`VenueStateResp.java`
- Create: `patra-catalog-api/src/main/java/dev/patra/catalog/api/endpoint/VenueEndpoint.java`
- Create: `patra-catalog-adapter/src/main/java/dev/patra/catalog/adapter/rest/internal/VenueEndpointImpl.java`
- Test: `patra-catalog-adapter/src/integrationTest/java/dev/patra/catalog/adapter/rest/internal/VenueEndpointIT.java`

**Interfaces:**
- Produces: spec 第 7.3 节的全部签名；`VenueResolutionResp.missing(String key)`；`VenueEndpointImpl` 只做 `VenueResolution` → DTO 转换（`deleted` → `DELETED`）。
- Consumes: Task 8 的 `VenueResolveQueryService`。

- [ ] **Step 1: 写失败的测试**——照 `PublicationEndpointIT` 的注解（`@WebMvcTest(controllers = VenueEndpointImpl.class)` + `@Import(VenueEndpointImpl.class)` + `CatalogAdapterITWebMvcConfig` + `@AutoConfigureRestTestClient` + `patra.web.error.context-prefix=CATALOG`），mock `VenueResolveQueryService`：
  - `should_return_rows_in_service_order_with_string_ids`：by-ids `[9007199254740993, 1]`；第一行 `key`="9007199254740993"、`exists`=true、`state`="ACTIVE"、`id` 是字符串 "9007199254740993"、`nlmId`="0404401"、`title`、`abbreviatedTitle`；第二行 `exists`=false，`state`/`id`/`nlmId`/`title`/`abbreviatedTitle` 全为 JSON null。
  - `should_keep_leading_zero_and_return_primary_for_secondary_key`：by-nlm-ids `["0404401","100970884"]`，服务返回两行同一读模型；`items[1].key`="100970884"、`items[1].nlmId`="0404401"；`verify(service).resolveByNlmIds(List.of("0404401", "100970884"))`。
  - `should_map_soft_deleted_to_deleted_state`。
  - `should_round_trip_id_as_long_with_default_json_mapper`：`JsonMapper.builder().build().readValue(body, VenueResolveResp.class).items().get(0).id()` == `9_007_199_254_740_993L`。
  - `@ParameterizedTest should_accept_valid_nlm_shapes`：`0404401`、`2985213R`、`9300367A`、`9918523075306676` → 200。
  - `@ParameterizedTest should_reject_invalid_requests_with_422_without_calling_service`：by-ids `{"ids":[]}`、`{}`、101 个、`[0]`；by-nlm-ids `["2985213r"]`、`["12A34"]`、`[""]`、`["0404401 "]`、`["12345678901234567890"]`（20 位数字超长）、101 个 → 422 且 `verifyNoInteractions(service)`。
  - `should_reject_null_element_with_400_without_calling_service`：`{"ids":[1,null]}` → 400。

- [ ] **Step 2: 跑测试，确认编译失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-adapter:integrationTest --tests "*VenueEndpointIT*"`

- [ ] **Step 3: 实现契约与端点**（注释照 `PublicationEndpoint`；`VenueResolveByNlmIdsReq` 的 `@Pattern` 消息写「nlmId 格式不对」）
- [ ] **Step 4: 跑测试，确认通过**

### Task 11: 期刊卡片带主 NLM，按 ID 批量读卡片

**Files:**
- Modify: `patra-catalog-domain/.../model/read/portal/VenueBrowseReadModel.java`（末尾加 `String nlmId`）、`patra-catalog-domain/.../port/read/VenueBrowseReadPort.java`（加 `findCardsByIds`）
- Modify: `patra-catalog-infra/.../adapter/read/VenueBrowseRow.java`（加 `getNlmId`）、`patra-catalog-infra/.../persistence/dao/VenueDao.java`（`findPortalVenueBrowsePage` 的投影与 FROM 抽成常量 `PORTAL_VENUE_CARD_SELECT`，投影加 `v.nlm_id AS "nlmId"`；新增 `findPortalVenueCardsByIds(Long[] ids)`）、`patra-catalog-infra/.../adapter/read/VenueBrowseReadAdapter.java`（`toReadModel` 带 `nlmId`；实现 `findCardsByIds`）
- Test: `patra-catalog-infra/src/integrationTest/java/dev/patra/catalog/infra/adapter/read/VenueBrowseReadAdapterIT.java`

**Interfaces:**
- Produces: `List<VenueBrowseReadModel> VenueBrowseReadPort.findCardsByIds(Collection<Long> ids)`——只返回 `JOURNAL` 且未软删的，顺序不保证；空入参返回空、不查库。
- Produces: `VenueBrowseReadModel.nlmId()`。

- [ ] **Step 1: 写失败的测试**（加 `saveJournalWithNlm(String title, String nlmId)` 夹具）：

```java
@Test
void should_return_cards_by_ids_excluding_soft_deleted_and_non_journals() {
  Long swiss = saveJournalWithNlm("Schweizerische medizinische Wochenschrift", "0404401");
  Long lancet = saveJournalWithNlm("Lancet (London, England)", "2985213R");
  Long gone = saveJournalWithNlm("Gone Journal", "0000003R");
  Long repo = saveVenueOfType("REPOSITORY", "Some repository");
  saveJcr(lancet, 2023, new BigDecimal("98.4"), "Q1", "MEDICINE, GENERAL & INTERNAL");
  saveJcr(lancet, 2024, new BigDecimal("88.5"), "Q1", "MEDICINE, GENERAL & INTERNAL");
  em.flush();
  softDelete(gone);
  em.clear();

  List<VenueBrowseReadModel> cards = adapter.findCardsByIds(List.of(swiss, lancet, gone, repo, 999L));

  assertThat(cards).extracting(VenueBrowseReadModel::id).containsExactlyInAnyOrder(swiss, lancet);
  assertThat(cards).filteredOn(c -> c.id().equals(lancet)).singleElement()
      .extracting(VenueBrowseReadModel::nlmId, VenueBrowseReadModel::impactFactor)
      .containsExactly("2985213R", new BigDecimal("88.5000"));
}

@Test
void browse_items_carry_primary_nlm() { /* search(...) 的条目 nlmId 等于夹具写入的值 */ }
```

- [ ] **Step 2: 跑测试，确认编译失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest --tests "*VenueBrowseReadAdapterIT*"`

- [ ] **Step 3: 实现**（`VenueBrowseReadModel` 加字段后，`PortalApiConverter` 与其测试暂不动——转换器用 builder，不受影响）
- [ ] **Step 4: 跑整类，确认通过**（现有浏览用例不变）

### Task 12: 门户批量期刊卡片接口

**Files:**
- Create: `patra-catalog-app/src/main/java/dev/patra/catalog/app/usecase/portal/query/PortalVenueCardQueryService.java`
- Create: `patra-catalog-adapter/src/main/java/dev/patra/catalog/adapter/rest/portal/request/PortalVenueBatchRequest.java`
- Create: `patra-catalog-adapter/src/main/java/dev/patra/catalog/adapter/rest/portal/response/PortalVenueBatchResponse.java`
- Modify: `PortalVenueBrowseResponse.java`（末尾加 `String nlmId`，JavaDoc `@param`）、`PortalApiConverter.toVenueBrowseResponse`（带 `nlmId`）、`PortalVenueController.java`（注入新服务，加 `@GetMapping("/batch")`）
- Modify: `patra-catalog-adapter/src/integrationTest/.../portal/PortalVenueDetailControllerIT.java`（同样切片 `PortalVenueController`，要补 `@MockitoBean PortalVenueCardQueryService`）
- Test: `patra-catalog-app/src/test/.../portal/query/PortalVenueCardQueryServiceTest.java`、`patra-catalog-adapter/src/integrationTest/.../portal/PortalVenueControllerIT.java`、`PortalApiConverterTest`

**Interfaces:**
- Consumes: Task 11 的 `findCardsByIds`、`nlmId()`。
- Produces: `List<VenueBrowseReadModel> PortalVenueCardQueryService.findByIds(List<Long> ids)`（去重、按请求顺序、端口没返回的丢掉；`@Transactional(readOnly = true)`）；`record PortalVenueBatchRequest(@NotEmpty @Size(max = 100) List<@Positive Long> id)`（紧凑构造器丢 null 后 `List.copyOf`）；`record PortalVenueBatchResponse(List<PortalVenueBrowseResponse> items)` + `of()`；`PortalVenueBatchResponse PortalVenueController.batch(@Valid PortalVenueBatchRequest req)`。

- [ ] **Step 1: 写失败的测试**
  - `PortalVenueCardQueryServiceTest.should_reorder_by_request_dedupe_and_drop_missing`：端口对 `[2, 1, 3]` 返回 `[card(1), card(2)]`，请求 `[2, 1, 2, 3]` → `containsExactly(card(2), card(1))`；端口只被调一次、入参已去重。
  - `PortalVenueControllerIT.should_return_batch_cards_in_service_order_with_nlm_id`：`GET /portal/venues/batch?id=2&id=1` → `$.items[0].id`="2"、`$.items[0].nlmId`="0404401"、`$.items[1].id`="1"；`verify(service).findByIds(List.of(2L, 1L))`。
  - `PortalVenueControllerIT.should_reject_invalid_batch_ids_with_422`：`""`、`"?id="`、`"?id=0"`、101 个 → 422，`verifyNoInteractions`。
  - `PortalVenueControllerIT.shouldReturnBrowseResult`：模型加 `.nlmId("0404401")`，断言 `$.items[0].nlmId`。
  - `PortalApiConverterTest`：浏览卡片转换带 `nlmId`。
- [ ] **Step 2: 跑测试，确认编译失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-app:test --tests "*PortalVenueCardQueryServiceTest*" :patra-api:patra-catalog:patra-catalog-adapter:test :patra-api:patra-catalog:patra-catalog-adapter:integrationTest --tests "*PortalVenue*"`

- [ ] **Step 3: 实现**（`/batch` 字面路径；不注册 `RepeatedParamListEditor`）
- [ ] **Step 4: 跑同一组测试，确认通过**

### Task 13: 网关集成测试

**Files:**
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/GatewayBlockedPathsIT.java`（路径表加 `/patra-catalog/_internal/venues/resolve/by-ids`、`/patra-catalog/_internal/venues/resolve/by-nlm-ids`；`should_block_internal_post_with_json_body` 对两个期刊路径也各发一次带 JSON 体的匿名与登录 `POST`）
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/GatewayRoutingIT.java`（新增 `should_forward_anonymous_venue_batch_request_with_repeated_ids`，照现有文献批量用例：下游收到 `/portal/venues/batch?id=1&id=2`、没有 `Authorization`）

- [ ] **Step 1: 加用例**
- [ ] **Step 2: 跑测试，确认通过**（规则本就通配，属于守护测试，首跑即绿）

Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*GatewayBlockedPathsIT*" --tests "*GatewayRoutingIT*"`

### Task 14: 文档、全量门控与汇报

**Files:**
- Modify: `patra-api/patra-catalog/README.md`（spec 第 12 节三处）
- Modify: `docs/patra/specs/2026-10-27-catalog-venue-identity-design.md`（状态行改为「已实施，待提交」，按实测回填与设计不同之处）

- [ ] **Step 1: 改 README 与 spec 状态行**
- [ ] **Step 2: 核对实测问题**——验证 spec 第 15 节「观察」（期刊浏览列表参数按逗号拆分）：读 `PortalVenueControllerIT` 现有 `?subject=Medicine,Oncology` 用例与 `PortalVenueListRequest` 注释，判断是否构成 bug，结论写进汇报，不改代码。
- [ ] **Step 3: 全量门控**

```bash
./gradlew spotlessApply
./gradlew check
./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest :patra-api:patra-catalog:patra-catalog-adapter:integrationTest
./gradlew :patra-api:patra-catalog:patra-catalog-boot:e2eTest
./gradlew :patra-api:patra-gateway-boot:integrationTest
```

Expected: 全部 BUILD SUCCESSFUL；`git status` 只有本计划列出的文件与本 spec / plan。

- [ ] **Step 4: 汇报并等用户同意**——向用户（经编排会话转达）汇报：改动清单、测试结果、mini 核对结论、上线步骤要点。**不提交**，等用户同意。
- [ ] **Step 5: 用户同意后提交**——只加本 Issue 的路径（`git add` 逐个列出，提交前 `git diff --cached --stat` 核对）：

```bash
git commit -m "$(cat <<'EOF'
feat(catalog): 新增期刊身份约束与批量解析接口 (PAP-117)

- V12：cat_venue.nlm_id 与标识符表的 NLM 值全局唯一
- 期刊聚合记录写一次的主 NLM；导入改为 NLM → ISSN-L → ISSN，跳过软删期刊的 NLM
- 内部 /_internal/venues/resolve/by-ids|by-nlm-ids（副 NLM 也能解析）
- 门户 /portal/venues/batch，期刊卡片带 nlmId
- 附 v0.14 PAP-117 工程 spec 与实施计划

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

（commitlint：`type(scope):` 前缀必填、subject 以中文动词开头、header ≤ 100 字；body 每行 ≤ 100 字。spotless 若在 pre-commit 改了文件，`git add -u` 后重提。）
