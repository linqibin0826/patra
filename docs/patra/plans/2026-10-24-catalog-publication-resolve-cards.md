# catalog 文献批量解析与批量卡片接口 Implementation Plan（PAP-109）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task（用户已定：本会话内联执行，不走 subagent-driven）. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 interaction 提供按内部 ID / PMID 的批量解析内部接口，给门户提供按 ID 的批量文献卡片接口，删除写死为 0 的 `bookmarks`。

**Architecture:** 两条 CQRS 读链路。解析：`PublicationEndpointImpl` → `PublicationResolveQueryService` → `PublicationResolveReadPort` → native SQL（含软删行）。卡片：`PortalPublicationController.batch` → `PortalPublicationCardQueryService` → 现有 `PublicationSearchReadPort.findCardsByIds` → 复用 `SELECT_LIST` 的 native SQL。去重与按请求排序都在查询服务里做，SQL 不排序。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Spring Data JPA native query、PostgreSQL 17（Testcontainers）、JUnit 5 + AssertJ + Mockito、`RestTestClient`、WireMock（网关）。

**Spec:** `docs/patra/specs/2026-10-24-catalog-publication-resolve-cards-design.md`

## Global Constraints

- 包根 `dev.patra.catalog`；模块路径前缀 `patra-api/patra-catalog/`，下文写作 `{api}` / `{domain}` / `{app}` / `{infra}` / `{adapter}` 指 `patra-catalog-<层>/src`。
- 所有方法写 `///` Markdown JavaDoc；不用全类名；record ≤ 4 参数用 `of()`，≥ 5 参数用 `@Builder`；带集合的 record 在紧凑构造器里 `List.copyOf`（SpotBugs 不认 `Stream.toList()`）。
- 解析请求：列表 1 ~ 100；`ids` 元素 `@Positive`；`pmids` 元素 `^[1-9][0-9]{0,14}$`；任一不合规整个请求 422。
- 卡片请求：重复参数 `id`，1 ~ 100，每个 `@Positive`。
- 解析响应每个请求键一行、按请求顺序、重复键只留第一次；查不到 `exists=false` 其余全 null；`state` 为 `ACTIVE` / `DELETED`；软删行照样带 `title` / `journal` / `year`。
- 卡片响应只含未软删且存在的文献，按请求顺序、去重，包在 `items` 里，元素是 `PortalPaperResponse`。
- `title` 用库里含内联标记的原文；`journal` 用 `cat_venue.title`；`year` 用 `cat_publication.publication_year`。
- 不新增索引、不写 Flyway 迁移。
- 测试方法 snake_case；切片测试 `patra.web.error.context-prefix=CATALOG`。
- 提交信息中文动词起头（commitlint 拒绝大写开头 subject），结尾带 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。**每个「提交」步骤都要先得到用户同意**；未同意时跳过提交、继续下一任务。
- 不 push、不开 PR。

## Review Focus

1. 雪花 ID 超过 2^53（如 `9007199254740993`）：卡片与解析响应里的 `id` 必须是字符串且数值不丢精度 → Task 4、Task 7 的切片测试各断言一次。
2. `?id=1,2`（逗号连在一个值里）：不能被拆开，也不能静默当成空列表放过 → Task 7 断言 4xx 且服务未被调用。
3. 前导零 PMID `038001234`：按正则拒绝（422），不能当成另一个 PMID 去查 → Task 4 断言。
4. 库里 `pmid` 为 null 的文献按 ID 解析：`exists=true`、`pmid=null`，不能被当成「不存在」→ Task 2 断言。
5. 只有空值的 `?id=`：编辑器丢掉空白后为空列表，必须 422 而不是返回空 `items` → Task 7 断言。

---

### Task 1: 删除 `bookmarks`

**Files:**
- Modify: `{adapter}/main/java/dev/patra/catalog/adapter/rest/portal/response/PortalPaperResponse.java`
- Modify: `{adapter}/main/java/dev/patra/catalog/adapter/rest/portal/response/PortalPublicationDetailResponse.java`
- Modify: `{adapter}/main/java/dev/patra/catalog/adapter/rest/portal/PortalApiConverter.java`（`toResponse` 的 `0, // bookmarks…` 实参；`toPublicationDetailResponse` 的 `.bookmarks(0)`）
- Test: `{adapter}/integrationTest/java/dev/patra/catalog/adapter/rest/portal/PortalPublicationControllerIT.java:110-111, 214-215`

**Interfaces:**
- Produces: `PortalPaperResponse` 16 个组件（原顺序去掉 `bookmarks`）；Task 7 复用。

- [ ] **Step 0: Linear 把 PAP-109 推到 `BE`（团队的「进行中」状态）**

- [ ] **Step 1: 把两处断言改成字段不存在**

```java
.jsonPath("$.items[0].bookmarks").doesNotExist()   // 列表
.jsonPath("$.bookmarks").doesNotExist()            // 详情
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-adapter:integrationTest --tests "*PortalPublicationControllerIT*"`
Expected: FAIL，两条用例报 `bookmarks` 存在。

- [ ] **Step 3: 删两个 record 的 `bookmarks` 组件与 `@param`，删转换器两处**

- [ ] **Step 4: 跑测试确认通过，并确认清零**

Run: 同 Step 2；再 `rg -n bookmarks patra-api/patra-catalog`
Expected: PASS；`rg` 无输出。

- [ ] **Step 5: 提交（先征得用户同意）**

```bash
git add patra-api/patra-catalog/patra-catalog-adapter
git commit -m "refactor(catalog): 删除门户响应里写死为 0 的收藏数"
```

---

### Task 2: 解析读端（domain 读模型 + 读端口 + infra 适配器）

**Files:**
- Create: `{domain}/main/java/dev/patra/catalog/domain/model/read/publication/PublicationResolveReadModel.java`
- Create: `{domain}/main/java/dev/patra/catalog/domain/port/read/PublicationResolveReadPort.java`
- Create: `{infra}/main/java/dev/patra/catalog/infra/adapter/read/PublicationResolveRow.java`
- Create: `{infra}/main/java/dev/patra/catalog/infra/persistence/dao/PublicationResolveDao.java`
- Create: `{infra}/main/java/dev/patra/catalog/infra/adapter/read/PublicationResolveReadAdapter.java`
- Test: `{infra}/integrationTest/java/dev/patra/catalog/infra/adapter/read/PublicationResolveReadAdapterIT.java`

**Interfaces:**
- Produces:
  - `@Builder record PublicationResolveReadModel(Long id, String pmid, String title, String venueName, Integer publicationYear, boolean deleted)`
  - `interface PublicationResolveReadPort { List<PublicationResolveReadModel> findByIds(Collection<Long> ids); List<PublicationResolveReadModel> findByPmids(Collection<String> pmids); }` —— 只返回库里存在的行（含软删），顺序不保证；入参为空时返回 `List.of()` 不查库。
  - `PublicationResolveRow`：接口投影，`getId(): Long`、`getPmid(): String`、`getTitle(): String`、`getVenueName(): String`、`getPublicationYear(): Integer`、`getDeleted(): Boolean`。
  - `PublicationResolveDao extends JpaRepository<PublicationEntity, Long>`：`List<PublicationResolveRow> findByIds(@Param("ids") Long[] ids)`、`List<PublicationResolveRow> findByPmids(@Param("pmids") String[] pmids)`，SQL 见 spec 第 5.5 节，数组参数照 `PublicationSearchDao` 写 `ANY((:ids)::bigint[])` / `ANY((:pmids)::text[])`。

- [ ] **Step 1: 写失败的 IT**

照 `PublicationSearchReadAdapterIT` 的注解与造数辅助方法（`@DataJpaTest` + `CatalogITPostgreSQLContainerInitializer` + `@Import({PublicationResolveReadAdapter.class, JpaAuditingConfiguration.class})`）。用例：

```java
@Test void should_return_active_and_soft_deleted_rows_by_ids()
// 造 active（pmid "38001234"、载体 "Lancet"、年份 2021）、软删（deleted_at 非空）各一篇
// findByIds([active, deleted, 999999999L]) → 2 行；active.deleted()==false、venueName=="Lancet"、publicationYear==2021；
// deleted 行 deleted()==true 且 title / venueName / publicationYear 照样有值；999999999 不出现

@Test void should_return_rows_by_pmids_including_soft_deleted()
// findByPmids(["38001234", "37000001", "1"]) → 2 行，pmid 与 id 对应正确

@Test void should_return_null_pmid_and_null_venue_as_is()        // Review Focus 4
// 造一篇 pmid=null、无载体的文献；findByIds([id]) → 1 行，pmid()==null、venueName()==null、deleted()==false

@Test void should_return_empty_without_query_for_empty_input()
// findByIds(List.of()) 与 findByPmids(List.of()) → 空列表
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest --tests "*PublicationResolveReadAdapterIT*"`
Expected: FAIL（编译失败：类型不存在）。

- [ ] **Step 3: 实现读模型、端口、投影、Dao、适配器**

适配器把行转读模型；`getDeleted()` 为 null 时按 false（列表达式不会为 null，防御一行即可）。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2
Expected: PASS。

- [ ] **Step 5: 记录 PMID 查询计划**

在 IT 里临时用 `em.getEntityManager().createNativeQuery("EXPLAIN SELECT … WHERE p.pmid = ANY(CAST(ARRAY['38001234'] AS text[]))")` 打一次，确认出现 `uk_pmid`；把结论写进 spec 第 9 节，然后删掉临时代码（不留成断言）。

- [ ] **Step 6: 提交（先征得用户同意）**

```bash
git add patra-api/patra-catalog/patra-catalog-domain patra-api/patra-catalog/patra-catalog-infra docs/patra/specs/2026-10-24-catalog-publication-resolve-cards-design.md
git commit -m "feat(catalog): 新增按内部 ID 与 PMID 批量解析文献的读端"
```

---

### Task 3: `PublicationResolveQueryService`

**Files:**
- Create: `{app}/main/java/dev/patra/catalog/app/usecase/publication/query/PublicationResolveQueryService.java`
- Create: `{app}/main/java/dev/patra/catalog/app/usecase/publication/query/dto/PublicationResolution.java`
- Test: `{app}/test/java/dev/patra/catalog/app/usecase/publication/query/PublicationResolveQueryServiceTest.java`

**Interfaces:**
- Consumes: `PublicationResolveReadPort`（Task 2）。
- Produces:
  - `record PublicationResolution(String key, PublicationResolveReadModel found)`，`static of(String key, PublicationResolveReadModel found)`；`found == null` 表示不存在。
  - `List<PublicationResolution> resolveByIds(List<Long> ids)`、`List<PublicationResolution> resolveByPmids(List<String> pmids)`，`@Transactional(readOnly = true)`。按入参顺序、去重（`LinkedHashSet`），对去重后的键调一次端口，再按键匹配（ID 用 `found.id()`，PMID 用 `found.pmid()`），查不到补 `found = null`。`key` 为 `Long.toString(id)` 或 PMID 原文。

- [ ] **Step 1: 写失败的单测（Mockito mock 端口）**

```java
@Test void should_keep_request_order_and_fill_missing_by_ids()
// 端口对 [3,1,2] 返回 [m1, m3]（故意乱序、缺 2）
// → keys ["3","1","2"]；found 依次 m3、m1、null

@Test void should_deduplicate_and_query_port_once()
// resolveByIds([5,5,7]) → 2 行；verify(port).findByIds(List.of(5L,7L)) 一次

@Test void should_match_by_pmid()
// resolveByPmids(["2","1"]) 端口返回 pmid "1" 的模型 → [("2",null),("1",m)]
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-app:test --tests "*PublicationResolveQueryServiceTest*"`
Expected: FAIL（编译失败）。

- [ ] **Step 3: 实现 `PublicationResolution` 与查询服务**

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2　Expected: PASS。

- [ ] **Step 5: 提交（先征得用户同意）**

```bash
git add patra-api/patra-catalog/patra-catalog-app
git commit -m "feat(catalog): 新增文献批量解析查询服务"
```

---

### Task 4: 内部接口契约与 `PublicationEndpointImpl`

**Files:**
- Create: `{api}/main/java/dev/patra/catalog/api/endpoint/PublicationEndpoint.java`
- Create: `{api}/main/java/dev/patra/catalog/api/dto/publication/PublicationResolveByIdsReq.java`
- Create: `{api}/main/java/dev/patra/catalog/api/dto/publication/PublicationResolveByPmidsReq.java`
- Create: `{api}/main/java/dev/patra/catalog/api/dto/publication/PublicationResolveResp.java`
- Create: `{api}/main/java/dev/patra/catalog/api/dto/publication/PublicationResolutionResp.java`
- Create: `{api}/main/java/dev/patra/catalog/api/dto/publication/PublicationStateResp.java`
- Create: `{adapter}/main/java/dev/patra/catalog/adapter/rest/internal/PublicationEndpointImpl.java`
- Test: `{adapter}/integrationTest/java/dev/patra/catalog/adapter/rest/internal/PublicationEndpointIT.java`

**Interfaces:**
- Consumes: `PublicationResolveQueryService`、`PublicationResolution`（Task 3）。
- Produces（PAP-112 的客户端依赖这些名字）：契约原样照 spec 第 5.3 节——`PublicationEndpoint.resolveByIds(PublicationResolveByIdsReq)` → `POST /_internal/publications/resolve/by-ids`，`resolveByPmids(PublicationResolveByPmidsReq)` → `/resolve/by-pmids`；`PublicationResolutionResp` 为 `@Builder` 8 字段 `(String key, boolean exists, PublicationStateResp state, Long id, String pmid, String title, String journal, Integer year)`，另有 `static missing(String key)`；三个带列表的 record 用 `of()`，紧凑构造器 `List.copyOf`（请求 record 列表为 null 时保留 null）。校验消息中文，如 `"ids 1 ~ 100 个"`、`"pmid 格式不对"`。

- [ ] **Step 1: 写失败的切片测试**

照 `MeshEndpointIT` 的注解（`@WebMvcTest(controllers = PublicationEndpointImpl.class)`、`CatalogAdapterITWebMvcConfig`、`@MockitoBean PublicationResolveQueryService`）。用例：

```java
@Test void should_return_rows_in_service_order_with_string_ids()   // Review Focus 1
// service.resolveByIds([9007199254740993L, 1L]) 返回 [存在且 ACTIVE 的行（id 9007199254740993、软删 false）, ("1", null)]
// → $.items[0].key=="9007199254740993"、.exists==true、.state=="ACTIVE"、.id=="9007199254740993"（字符串）、
//   .title/.journal/.year 对应；$.items[1].exists==false、.state/.id/.pmid/.title 为 null

@Test void should_map_soft_deleted_to_deleted_state()
// found.deleted()==true → $.items[0].state=="DELETED"，title 仍有值

@Test void should_round_trip_id_as_long_with_container_json_mapper()
// 取响应体字符串，用注入的 JsonMapper.readValue(body, PublicationResolveResp.class)
// → items().get(0).id() == 9007199254740993L

@Test void should_resolve_by_pmids()
// POST /resolve/by-pmids {"pmids":["38001234"]} → 200；verify(service).resolveByPmids(List.of("38001234"))

@ParameterizedTest void should_reject_invalid_requests_with_422_without_calling_service(String path, String body)
// ("/by-ids", "{\"ids\":[]}")、("/by-ids", "{}")、("/by-ids", 101 个 ID)、("/by-ids", "{\"ids\":[0]}")、
// ("/by-pmids", "{\"pmids\":[\"abc\"]}")、("/by-pmids", "{\"pmids\":[\"038001234\"]}")   // Review Focus 3
// → expectStatus().isEqualTo(422)（照现有 IT，只断言状态）；verifyNoInteractions(service)

@Test void should_reject_null_element_with_400_without_calling_service()
// {"ids":[1,null]} → 断言为实际得到的 4xx 状态（预期 400），verifyNoInteractions(service)；把状态码写回 spec 第 5.6 节
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-adapter:integrationTest --tests "*PublicationEndpointIT*"`
Expected: FAIL（编译失败）。

- [ ] **Step 3: 实现契约 DTO、接口与 `PublicationEndpointImpl`**

`PublicationEndpointImpl implements PublicationEndpoint`（`@RestController`），把 `PublicationResolution` 转成 `PublicationResolutionResp`：`found == null` → `missing(key)`；否则 `state = found.deleted() ? DELETED : ACTIVE`、`journal = found.venueName()`、`year = found.publicationYear()`。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2　Expected: PASS。

- [ ] **Step 5: 提交（先征得用户同意）**

```bash
git add patra-api/patra-catalog/patra-catalog-api patra-api/patra-catalog/patra-catalog-adapter docs/patra/specs/2026-10-24-catalog-publication-resolve-cards-design.md
git commit -m "feat(catalog): 新增文献批量解析内部接口"
```

---

### Task 5: 卡片读端 `findCardsByIds`（含无 N+1 断言）

**Files:**
- Modify: `{domain}/main/java/dev/patra/catalog/domain/port/read/PublicationSearchReadPort.java`
- Modify: `{infra}/main/java/dev/patra/catalog/infra/persistence/dao/PublicationSearchDao.java`
- Modify: `{infra}/main/java/dev/patra/catalog/infra/adapter/read/PublicationSearchReadAdapter.java`
- Test: `{infra}/integrationTest/java/dev/patra/catalog/infra/adapter/read/PublicationCardReadIT.java`

**Interfaces:**
- Produces:
  - `PublicationSearchReadPort.List<PortalPaperReadModel> findCardsByIds(Collection<Long> ids)`：只返回存在且未软删的，顺序不保证；空入参返回 `List.of()` 不查库。
  - `PublicationSearchDao.List<PublicationSearchRow> findCardsByIds(@Param("ids") Long[] ids)`，SQL：`SELECT_LIST + "WHERE p.deleted_at IS NULL AND p.id = ANY((:ids)::bigint[])"`。
  - 适配器复用现有 `toReadModel`。

- [ ] **Step 1: 写失败的 IT**

```java
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
// 其余注解照 PublicationSearchReadAdapterIT；注入 EntityManagerFactory，
// Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();

@Test void should_load_100_cards_with_a_single_statement()
// 造 100 篇：每篇同一载体、2 个作者、2 个类型、1 段摘要；再造 1 篇软删；flush() + em.clear()
// stats.clear(); var cards = adapter.findCardsByIds(全部 101 个 ID);
// cards.size()==100；软删 ID 不在；任取一张 authors 有 2 个、studyType 非空、venueName 正确、abstractSnippet 非空
// stats.getPrepareStatementCount() == 1

@Test void should_skip_missing_ids()
// findCardsByIds([存在的, 999999999L]) → 1 张
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest --tests "*PublicationCardReadIT*"`
Expected: FAIL（编译失败）。

- [ ] **Step 3: 实现端口方法、Dao 方法、适配器方法**

- [ ] **Step 4: 跑测试确认通过；同时跑原检索 IT 确认未受影响**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest --tests "*PublicationCardReadIT*" --tests "*PublicationSearchReadAdapterIT*"`
Expected: PASS。

- [ ] **Step 5: 提交（先征得用户同意）**

```bash
git add patra-api/patra-catalog/patra-catalog-domain patra-api/patra-catalog/patra-catalog-infra
git commit -m "feat(catalog): 新增按 ID 批量取文献卡片的读端"
```

---

### Task 6: `PortalPublicationCardQueryService`

**Files:**
- Create: `{app}/main/java/dev/patra/catalog/app/usecase/portal/query/PortalPublicationCardQueryService.java`
- Test: `{app}/test/java/dev/patra/catalog/app/usecase/portal/query/PortalPublicationCardQueryServiceTest.java`

**Interfaces:**
- Consumes: `PublicationSearchReadPort.findCardsByIds`（Task 5）。
- Produces: `List<PortalPaperReadModel> findByIds(List<Long> ids)`，`@Transactional(readOnly = true)`；去重（保留第一次出现）、调一次端口、按请求顺序重排、端口没返回的 ID 跳过。

- [ ] **Step 1: 写失败的单测**

```java
@Test void should_reorder_to_request_order_and_skip_missing()
// 端口对 [3,1,2] 返回 [m1, m3] → 结果 id 依次 3、1

@Test void should_deduplicate_before_querying()
// findByIds([4,4,6]) → verify(port).findCardsByIds(List.of(4L,6L))
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-app:test --tests "*PortalPublicationCardQueryServiceTest*"`
Expected: FAIL（编译失败）。

- [ ] **Step 3: 实现查询服务**

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2　Expected: PASS。

- [ ] **Step 5: 提交（先征得用户同意）**

```bash
git add patra-api/patra-catalog/patra-catalog-app
git commit -m "feat(catalog): 新增门户批量文献卡片查询服务"
```

---

### Task 7: `GET /portal/publications/batch`

**Files:**
- Create: `{adapter}/main/java/dev/patra/catalog/adapter/rest/portal/request/PortalPublicationBatchRequest.java`
- Create: `{adapter}/main/java/dev/patra/catalog/adapter/rest/portal/response/PortalPaperBatchResponse.java`
- Modify: `{adapter}/main/java/dev/patra/catalog/adapter/rest/portal/PortalPublicationController.java`
- Test: `{adapter}/integrationTest/java/dev/patra/catalog/adapter/rest/portal/PortalPublicationControllerIT.java`

**Interfaces:**
- Consumes: `PortalPublicationCardQueryService.findByIds`（Task 6）、`PortalApiConverter.toResponse`（Task 1 之后的形状）。
- Produces:
  - `record PortalPublicationBatchRequest(@NotEmpty @Size(max = 100) List<@Positive Long> id)`，紧凑构造器 `id == null ? List.of() : List.copyOf(id.stream().filter(Objects::nonNull).toList())`（照 `PortalPublicationSearchRequest.venue`）。
  - `record PortalPaperBatchResponse(List<PortalPaperResponse> items)`，`of()`，`List.copyOf`。
  - 控制器方法 `@GetMapping("/batch") PortalPaperBatchResponse batch(@Valid PortalPublicationBatchRequest request)`。

- [ ] **Step 1: 写失败的切片测试（加进现有 IT，mock `PortalPublicationCardQueryService`）**

```java
@Test void should_return_cards_in_service_order()                   // Review Focus 1
// service.findByIds([9007199254740993L, 2L]) 返回两张卡 → $.items.length()==2、$.items[0].id=="9007199254740993"、
// $.items[0].bookmarks 不存在；verify(service).findByIds(List.of(9007199254740993L, 2L))

@Test void should_bind_repeated_id_params_and_drop_blank_ones()
// ?id=5&id=&id=6 → verify(service).findByIds(List.of(5L, 6L))

@ParameterizedTest void should_reject_invalid_ids_with_422_without_calling_service(String query)
// "" （无参数）、"?id="（只有空值，Review Focus 5）、"?id=0"、101 个 id → 422；verifyNoInteractions(service)

@Test void should_reject_comma_joined_ids()                         // Review Focus 2
// ?id=1,2 → is4xxClientError()；verifyNoInteractions(service)

@Test void should_not_route_batch_to_detail_endpoint()
// ?id=1 时 verifyNoInteractions(portalPublicationDetailQueryService)
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-adapter:integrationTest --tests "*PortalPublicationControllerIT*"`
Expected: FAIL（编译失败）。

- [ ] **Step 3: 实现请求、响应与控制器方法**

控制器注入 `PortalPublicationCardQueryService`，`items` 用 `portalApiConverter::toResponse` 映射。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2　Expected: PASS。

- [ ] **Step 5: 提交（先征得用户同意）**

```bash
git add patra-api/patra-catalog/patra-catalog-adapter
git commit -m "feat(catalog): 新增门户批量文献卡片接口"
```

---

### Task 8: 网关集成测试

**Files:**
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/GatewayBlockedPathsIT.java`
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/GatewayRoutingIT.java`

- [ ] **Step 1: 补用例**

- `GatewayBlockedPathsIT`：`blocked` 列表加 `"/patra-catalog/_internal/publications/resolve/by-ids"`；新增 `should_block_internal_post_with_json_body`：匿名与带令牌各 `POST /patra-catalog/_internal/publications/resolve/by-ids`，体 `{"ids":[1]}` → 403；`catalog.verify(0, anyRequestedFor(anyUrl()))`。
- `GatewayRoutingIT`：新增 `should_forward_anonymous_batch_request_with_repeated_ids`：WireMock 桩 `GET /portal/publications/batch` 回 200；匿名请求 `/patra-catalog/portal/publications/batch?id=1&id=2` → 200；`verify(getRequestedFor(urlEqualTo("/portal/publications/batch?id=1&id=2")).withoutHeader("Authorization"))`。

规则不改，这两条预期直接通过——它们是 AC 的证据，不是 TDD 的红灯。

- [ ] **Step 2: 跑测试确认通过**

Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*GatewayBlockedPathsIT*" --tests "*GatewayRoutingIT*"`
Expected: PASS（下游与 identity 都是 WireMock，这两个 IT 不起容器）。

- [ ] **Step 3: 提交（先征得用户同意）**

```bash
git add patra-api/patra-gateway-boot
git commit -m "test(gateway): 覆盖文献解析内部接口的拒绝与批量卡片的转发"
```

---

### Task 9: README、全量门控与交接

**Files:**
- Modify: `patra-api/patra-catalog/README.md`
- Modify: `docs/patra/specs/2026-10-24-catalog-publication-resolve-cards-design.md`（状态行改为「已实施」，回填第 5.6、9 节的实测）
- Create: `/Users/linqibin/Library/Application Support/Claude/side-session-notes/local_c04766c5-6ac7-4771-97aa-718035535462/pap-109-catalog-contract.md`

- [ ] **Step 1: 更新 README**

「已实现功能」加一条；「内部接口」一节加两个 `_internal/publications/resolve/*` 路径与契约位置（`patra-catalog-api` 的 `PublicationEndpoint`）；变更日志一行。

- [ ] **Step 2: 全量门控**

Run（依次）:
```bash
./gradlew spotlessApply
./gradlew check
./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest :patra-api:patra-catalog:patra-catalog-adapter:integrationTest :patra-api:patra-gateway-boot:integrationTest
```
Expected: 全部 BUILD SUCCESSFUL。spotless 改了文件就 `git add -u` 后再提交。

- [ ] **Step 3: 写交接笔记**

共享笔记文件写：两个端点路径、请求 / 响应 record 名与字段、`id` 字符串往返、422 规则、spec 第 12 节对 PAP-112 / PAP-114 的约束摘要、提交 SHA（若已提交）。

- [ ] **Step 4: 提交（先征得用户同意）**

```bash
git add patra-api/patra-catalog/README.md docs/patra/specs/2026-10-24-catalog-publication-resolve-cards-design.md docs/patra/plans/2026-10-24-catalog-publication-resolve-cards.md
git commit -m "docs(catalog): 记录文献批量解析与批量卡片接口"
```

- [ ] **Step 5: 告诉用户 PAP-109 可以先合回 `feat/v0.13-library-api`，好让 PAP-112 开工；Linear 状态推到 Review 由用户确认后再做**
