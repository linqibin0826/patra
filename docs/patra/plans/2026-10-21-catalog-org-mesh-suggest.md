# catalog 机构与 MeSH 建议接口实施计划（PAP-94）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给门户加 `GET /portal/organizations/suggest` 与 `GET /portal/mesh/suggest`，给 identity 加 `GET /_internal/organizations/{rorId}` 与 `GET /_internal/mesh/descriptors/{ui}`，契约放进 `patra-catalog-api`，Flyway `V9` 建 trigram 索引，网关补测试。

**Architecture:** 读端走 CQRS：领域层定义查询对象 `SuggestQuery`、读模型与两个 `ReadPort`，infra 用 Spring Data native SQL（`pg_trgm` GIN 索引）实现 `ReadAdapter`，app 层两个只读 `QueryService`，adapter 层门户控制器 + 实现 `@HttpExchange` 接口的内部端点。排序是可解释的规则（命中方式 → 中国优先 → 医疗优先 → 名称类型 → 短名优先），不用相似度。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Spring Data JPA（native query + 接口投影）、Flyway、PostgreSQL 17 + `pg_trgm`、Testcontainers（`postgres:17`）、WireMock、JUnit 5 + AssertJ + Mockito、Lombok。

**Spec:** `docs/patra/specs/2026-10-21-catalog-org-mesh-suggest-design.md`

## Global Constraints

- 全部工作在主检出 `/Users/linqibin/Projects/Products/patra` 的分支 `feat/v0.12-user-center-api` 上，不建 worktree、不切分支、不推送、不开 PR。命令在仓库根目录执行，路径一律绝对路径（`cd` 会把 Bash 的工作目录带偏）。
- 不动 identity 的任何文件（另一会话有未提交改动）。
- 查询归一（spec §7.1）：NFKC → `strip()` → 连续空白折成一个空格；长度按码点 2 ~ 100；`limit` 1 ~ 20、缺省 10；含 HAN / HIRAGANA / KATAKANA / HANGUL 字符 → `MatchMode.SUBSTRING`，否则 `WORD_PREFIX`。
- LIKE 转义：`!` → `!!`、`%` → `!%`、`_` → `!_`，SQL 里 `ESCAPE '!'`；三种模式 `q%`、`% q%`、`%q%`；精确比较 `lower(列) = lower(:q)`。候选截 500（spec §7.2 / §7.3）。
- 机构：只认 `status = 'active' AND deleted_at IS NULL`；排序键 `match_rank` → `country_code = 'CN'` → `types` 含 `healthcare` → `type_rank`（ror_display / label 0、alias 1、acronym 2）→ `length(命中名)` → `display_name`。`zhName` 取 `lang = 'zh'` 的名称，label 优先、同级最短。
- MeSH：主题词 `descriptor_class = '1' AND active_status`；入口词 `record_type = 'DESCRIPTOR' AND is_permuted_term = false`；`match_rank` 名称精确 0、入口词精确 1、名称前缀 2、入口词前缀 3、名称词首 4、入口词词首 5；内部查询不限 class。
- ID 格式：ROR `^0[a-z0-9]{8}$`、MeSH `^D\d{6}(\d{3})?$`；不合规直接抛 NotFound，不查库（spec §8.3 / §8.4）。
- Spring Data native 查询里不能写 jsonb 的 `?` 操作符（会被当成位置参数），用 `jsonb_exists(col, 'key')`；`UNION ALL` 里的空列写 `CAST(NULL AS varchar)`；列别名加双引号保持 camelCase 与投影 getter 对应（`VenueDao` 的写法）。
- 错误码前缀：运行时 `CAT`，`@WebMvcTest` 切片里要加 `@TestPropertySource(properties = "patra.web.error.context-prefix=CATALOG")` 才能断言 `CATALOG-0404`（`PortalVenueDetailControllerIT` 的写法）。
- 代码规范：Google Java 格式（spotless）、`///` Javadoc 且每行不超过 100 列（gjf 会把超长的 `///` 折成 `//`）、不用全类名、record ≤ 4 个参数用 `of()`、≥ 5 个用 `@Builder`，集合字段在紧凑构造器 `List.copyOf`。
- 测试规范：单测在 `src/test`，集成测试在 `src/integrationTest`，方法名 snake_case `should_…`；`./gradlew check` 不含 `integrationTest`，两个都要跑；Testcontainers 要本机 Docker（OrbStack）在线；跑 `check` 前先 `./gradlew spotlessApply`；Mockito 不嵌套 stub。
- infra IT 里虚构 ROR ID 用 `0aaaaaaa1` 这种形状（9 位、`0` 开头、小写字母数字），不要用真实 ID（Task 9 的种子按真实 ID 插行，会撞上）。
- 提交：每个任务至少一次提交，格式 `type(scope): 中文 subject (PAP-94)`，subject 用中文动词起头，结尾加 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`；只 `git add` 本任务的路径，提交前看 `git diff --cached --stat`（工作区有别的会话的 identity 改动）。
- 本机起 JVM 连 mini 前先 `export JAVA_TOOL_OPTIONS="-Dhttp.nonProxyHosts=localhost|127.*|100.*|192.168.*|172.*|10.* -Dhttps.nonProxyHosts=localhost|127.*|100.*|192.168.*|172.*|10.* -Dsocks.nonProxyHosts=localhost|127.*|100.*|192.168.*|172.*|10.*"`（macOS 系统代理会把去 100.x 的请求送进 Shadowrocket）；curl 本机端口加 `--noproxy '*'`。
- Linear：Task 1 开工时把 PAP-94 推到 `BE`，Task 10 收尾推到 `Review`（`save_issue`，`state` 用状态名）。

## Review Focus

1. 查询里带 `%`、`_`、`!` 要当字面量，不能变通配或让 SQL 报错——Task 4 的 `should_treat_like_metacharacters_as_literals`。
2. `limit=abc` 这种绑定失败要回 4xx 且不触达查询服务，不能 500——Task 6 的 `should_reject_non_numeric_limit_without_touching_service`。
3. 大写或 URL 形式的 ROR ID（`04JZTAG35`、`https://ror.org/04jztag35`）要 404 且不查库——Task 3 的 `should_reject_malformed_ror_id_without_querying`、Task 7 的 404 用例。
4. MeSH 建议收到中文查询要 200 空列表，不是错误（边界 J 不预留中文）——Task 5 的 `should_return_empty_for_cjk_query`。
5. `types` 为 NULL 的机构照样返回且 `types` 是空列表，不能因 `jsonb_array_elements_text(NULL)` 出错或漏掉——Task 4 的 `should_return_empty_types_when_organization_types_is_null`。

---

### Task 1: 查询对象 `SuggestQuery`

**Files:**
- Create: `patra-api/patra-catalog/patra-catalog-domain/src/main/java/dev/patra/catalog/domain/query/MatchMode.java`
- Create: `patra-api/patra-catalog/patra-catalog-domain/src/main/java/dev/patra/catalog/domain/query/SuggestQuery.java`
- Test: `patra-api/patra-catalog/patra-catalog-domain/src/test/java/dev/patra/catalog/domain/query/SuggestQueryTest.java`

**Interfaces:**
- Produces: `public enum MatchMode { SUBSTRING, WORD_PREFIX }`；`public record SuggestQuery(String text, MatchMode mode, int limit)`，常量 `MIN_LENGTH = 2`、`MAX_LENGTH = 100`、`MIN_LIMIT = 1`、`MAX_LIMIT = 20`、`DEFAULT_LIMIT = 10`，工厂 `public static SuggestQuery of(String raw, int limit)`（不合规抛 `IllegalArgumentException`，用 hutool `Assert`）。

- [ ] **Step 0: Linear 上把 PAP-94 推到 `BE`**

- [ ] **Step 1: 写失败的测试**

```java
@DisplayName("SuggestQuery 单元测试")
class SuggestQueryTest {

  @Test
  void should_normalize_nfkc_strip_and_collapse_whitespace() {
    // 全角空格 U+3000 按码点构造：gjf 会把源码里的 unicode 转义改写成肉眼看不见的字面字符
    String raw = Character.toString(0x3000) + "ＰＵＭＣＨ  协和\t医院 ";
    assertThat(SuggestQuery.of(raw, 10).text()).isEqualTo("PUMCH 协和 医院");
  }

  @Test
  void should_use_substring_mode_for_han_kana_and_hangul() {
    assertThat(SuggestQuery.of("协和", 10).mode()).isEqualTo(MatchMode.SUBSTRING);
    assertThat(SuggestQuery.of("北京 Hospital", 10).mode()).isEqualTo(MatchMode.SUBSTRING);
    assertThat(SuggestQuery.of("ソウル", 10).mode()).isEqualTo(MatchMode.SUBSTRING);
    assertThat(SuggestQuery.of("서울", 10).mode()).isEqualTo(MatchMode.SUBSTRING);
  }

  @Test
  void should_use_word_prefix_mode_for_latin_text() {
    assertThat(SuggestQuery.of("Mayo", 10).mode()).isEqualTo(MatchMode.WORD_PREFIX);
    assertThat(SuggestQuery.of("Ärzte", 10).mode()).isEqualTo(MatchMode.WORD_PREFIX);
  }

  @Test
  void should_count_length_in_code_points_between_2_and_100() {
    assertThat(SuggestQuery.of("协和", 10).text()).isEqualTo("协和");
    assertThat(SuggestQuery.of("a".repeat(100), 10).text()).hasSize(100);
    assertThatIllegalArgumentException().isThrownBy(() -> SuggestQuery.of("协", 10));
    assertThatIllegalArgumentException().isThrownBy(() -> SuggestQuery.of("a".repeat(101), 10));
    assertThatIllegalArgumentException().isThrownBy(() -> SuggestQuery.of("   ", 10));
    assertThatIllegalArgumentException().isThrownBy(() -> SuggestQuery.of(null, 10));
  }

  @Test
  void should_reject_limit_outside_1_to_20() {
    assertThat(SuggestQuery.of("Mayo", 1).limit()).isEqualTo(1);
    assertThat(SuggestQuery.of("Mayo", 20).limit()).isEqualTo(20);
    assertThatIllegalArgumentException().isThrownBy(() -> SuggestQuery.of("Mayo", 0));
    assertThatIllegalArgumentException().isThrownBy(() -> SuggestQuery.of("Mayo", 21));
  }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-domain:test --tests "*SuggestQueryTest*"`
Expected: 编译失败（类不存在）

- [ ] **Step 3: 实现 `MatchMode` 与 `SuggestQuery.of(String raw, int limit)`**

归一用 `java.text.Normalizer.normalize(raw, Form.NFKC)`，长度用 `codePointCount`，模式用 `raw.codePoints().mapToObj(Character.UnicodeScript::of)` 看是否含 `HAN / HIRAGANA / KATAKANA / HANGUL`。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-catalog/patra-catalog-domain/src/main/java/dev/patra/catalog/domain/query patra-api/patra-catalog/patra-catalog-domain/src/test/java/dev/patra/catalog/domain/query
git commit -m "feat(catalog): 加建议查询对象 SuggestQuery 与匹配模式 (PAP-94)"
```

---

### Task 2: 读模型、读端口与 NotFound 异常

**Files:**
- Create: `patra-api/patra-catalog/patra-catalog-domain/src/main/java/dev/patra/catalog/domain/model/read/organization/OrganizationSummary.java`
- Create: `.../domain/model/read/organization/OrganizationSuggestion.java`
- Create: `.../domain/model/read/mesh/MeshDescriptorSummary.java`
- Create: `.../domain/model/read/mesh/MeshDescriptorSuggestion.java`
- Create: `.../domain/port/read/OrganizationReadPort.java`
- Create: `.../domain/port/read/MeshDescriptorReadPort.java`
- Create: `.../domain/exception/OrganizationNotFoundException.java`
- Create: `.../domain/exception/MeshDescriptorNotFoundException.java`
- Test: `patra-api/patra-catalog/patra-catalog-domain/src/test/java/dev/patra/catalog/domain/model/read/organization/OrganizationSummaryTest.java`
- Test: `.../domain/model/read/mesh/MeshDescriptorSummaryTest.java`
- Test: `.../domain/exception/SuggestNotFoundExceptionsTest.java`

**Interfaces:**
- Consumes: Task 1 的 `SuggestQuery`。
- Produces:
  - `@Builder public record OrganizationSummary(String rorId, String displayName, String zhName, String countryCode, String cityName, List<String> types)`，紧凑构造器 `types = types != null ? List.copyOf(types) : List.of()`。
  - `public record OrganizationSuggestion(OrganizationSummary organization, String matchedName)` + `of()`。
  - `public record MeshDescriptorSummary(String ui, String name, List<String> treeNumbers)` + `of()`，`treeNumbers` 同样 copyOf / 空列表。
  - `public record MeshDescriptorSuggestion(MeshDescriptorSummary descriptor, String matchedTerm)` + `of()`。
  - `public interface OrganizationReadPort { List<OrganizationSuggestion> suggest(SuggestQuery query); Optional<OrganizationSummary> findByRorId(String rorId); }`
  - `public interface MeshDescriptorReadPort { List<MeshDescriptorSuggestion> suggest(SuggestQuery query); Optional<MeshDescriptorSummary> findByUi(String ui); }`
  - `public class OrganizationNotFoundException extends CatalogException`，构造器 `(String rorId)`，特征 `StandardErrorTrait.NOT_FOUND`，消息含 ID；`MeshDescriptorNotFoundException(String ui)` 同样。

- [ ] **Step 1: 写失败的测试**

```java
class OrganizationSummaryTest {
  @Test
  void should_copy_types_defensively_and_default_to_empty() {
    List<String> types = new ArrayList<>(List.of("healthcare"));
    OrganizationSummary summary =
        OrganizationSummary.builder().rorId("0aaaaaaa1").displayName("X").types(types).build();
    types.add("funder");
    assertThat(summary.types()).containsExactly("healthcare");
    assertThat(OrganizationSummary.builder().rorId("0aaaaaaa1").displayName("X").build().types())
        .isEmpty();
  }
}

class MeshDescriptorSummaryTest {
  @Test
  void should_copy_tree_numbers_and_default_to_empty() {
    assertThat(MeshDescriptorSummary.of("D003920", "Diabetes Mellitus", null).treeNumbers()).isEmpty();
    List<String> numbers = new ArrayList<>(List.of("C19.246"));
    MeshDescriptorSummary summary = MeshDescriptorSummary.of("D003920", "Diabetes Mellitus", numbers);
    numbers.clear();
    assertThat(summary.treeNumbers()).containsExactly("C19.246");
  }
}

class SuggestNotFoundExceptionsTest {
  @Test
  void should_carry_not_found_trait_and_id_in_message() {
    OrganizationNotFoundException org = new OrganizationNotFoundException("0aaaaaaa1");
    assertThat(org.getErrorTraits()).containsExactly(StandardErrorTrait.NOT_FOUND);
    assertThat(org.getMessage()).contains("0aaaaaaa1");
    MeshDescriptorNotFoundException mesh = new MeshDescriptorNotFoundException("D000000");
    assertThat(mesh.getErrorTraits()).containsExactly(StandardErrorTrait.NOT_FOUND);
    assertThat(mesh.getMessage()).contains("D000000");
  }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-domain:test --tests "*OrganizationSummaryTest*" --tests "*MeshDescriptorSummaryTest*" --tests "*SuggestNotFoundExceptionsTest*"`
Expected: 编译失败

- [ ] **Step 3: 实现上面 Interfaces 列出的八个类型**

异常照 `VenueNotFoundException` 的写法（`super("Organization not found with ror id: " + rorId, StandardErrorTrait.NOT_FOUND)`）。

- [ ] **Step 4: 跑测试确认通过；跑领域层 check 确认纯净**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-domain:check`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-catalog/patra-catalog-domain
git commit -m "feat(catalog): 加机构与 MeSH 建议的读模型、读端口与未找到异常 (PAP-94)"
```

---

### Task 3: 查询服务

**Files:**
- Create: `patra-api/patra-catalog/patra-catalog-app/src/main/java/dev/patra/catalog/app/usecase/organization/query/OrganizationQueryService.java`
- Create: `patra-api/patra-catalog/patra-catalog-app/src/main/java/dev/patra/catalog/app/usecase/mesh/query/MeshDescriptorQueryService.java`
- Test: `patra-api/patra-catalog/patra-catalog-app/src/test/java/dev/patra/catalog/app/usecase/organization/query/OrganizationQueryServiceTest.java`
- Test: `patra-api/patra-catalog/patra-catalog-app/src/test/java/dev/patra/catalog/app/usecase/mesh/query/MeshDescriptorQueryServiceTest.java`

**Interfaces:**
- Consumes: Task 2 的读端口、读模型、异常。
- Produces:
  - `@Service @RequiredArgsConstructor public class OrganizationQueryService`：`@Transactional(readOnly = true) List<OrganizationSuggestion> suggest(SuggestQuery query)`；`@Transactional(readOnly = true) OrganizationSummary getByRorId(String rorId)`（先校验 `^0[a-z0-9]{8}$`，不合规或端口返回空都抛 `OrganizationNotFoundException`）。
  - `MeshDescriptorQueryService`：`suggest(SuggestQuery)`、`MeshDescriptorSummary getByUi(String ui)`（校验 `^D\d{6}(\d{3})?$`）。

- [ ] **Step 1: 写失败的测试**

```java
class OrganizationQueryServiceTest {
  private final OrganizationReadPort port = mock(OrganizationReadPort.class);
  private final OrganizationQueryService service = new OrganizationQueryService(port);

  @Test
  void should_delegate_suggest_to_read_port() {
    SuggestQuery query = SuggestQuery.of("协和", 10);
    OrganizationSuggestion suggestion = OrganizationSuggestion.of(
        OrganizationSummary.builder().rorId("0aaaaaaa1").displayName("X").build(), "协和医院");
    when(port.suggest(query)).thenReturn(List.of(suggestion));
    assertThat(service.suggest(query)).containsExactly(suggestion);
  }

  @Test
  void should_return_summary_when_ror_id_exists() {
    OrganizationSummary summary = OrganizationSummary.builder().rorId("0aaaaaaa1").displayName("X").build();
    when(port.findByRorId("0aaaaaaa1")).thenReturn(Optional.of(summary));
    assertThat(service.getByRorId("0aaaaaaa1")).isEqualTo(summary);
  }

  @Test
  void should_throw_not_found_when_ror_id_absent() {
    when(port.findByRorId("0aaaaaaa1")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getByRorId("0aaaaaaa1"))
        .isInstanceOf(OrganizationNotFoundException.class);
  }

  @Test
  void should_reject_malformed_ror_id_without_querying() {
    for (String bad : List.of("04JZTAG35", "https://ror.org/04jztag35", "abc", "")) {
      assertThatThrownBy(() -> service.getByRorId(bad)).isInstanceOf(OrganizationNotFoundException.class);
    }
    verifyNoInteractions(port);
  }
}
```

`MeshDescriptorQueryServiceTest` 同构：合规 `D003920`、`D000072716`；不合规 `D12`、`C000657245`、`d003920` → `MeshDescriptorNotFoundException` 且 `verifyNoInteractions(port)`。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-app:test --tests "*OrganizationQueryServiceTest*" --tests "*MeshDescriptorQueryServiceTest*"`
Expected: 编译失败

- [ ] **Step 3: 实现两个查询服务**

正则作为 `private static final Pattern` 放在各自服务里。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-catalog/patra-catalog-app
git commit -m "feat(catalog): 加机构与 MeSH 的只读查询服务 (PAP-94)"
```

---

### Task 4: Flyway `V9` 与机构读适配器

**Files:**
- Create: `patra-api/patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V9__add_suggest_trigram_indexes.sql`（spec §10.1 原文）
- Modify: `patra-api/patra-catalog/patra-catalog-infra/src/main/java/dev/patra/catalog/infra/persistence/dao/OrganizationNameDao.java`
- Modify: `patra-api/patra-catalog/patra-catalog-infra/src/main/java/dev/patra/catalog/infra/persistence/dao/OrganizationDao.java`
- Create: `patra-api/patra-catalog/patra-catalog-infra/src/main/java/dev/patra/catalog/infra/adapter/read/OrganizationSummaryRow.java`
- Create: `.../infra/adapter/read/OrganizationSuggestRow.java`
- Create: `.../infra/adapter/read/OrganizationReadAdapter.java`
- Test: `patra-api/patra-catalog/patra-catalog-infra/src/test/java/dev/patra/catalog/infra/adapter/read/OrganizationReadAdapterTest.java`
- Test: `patra-api/patra-catalog/patra-catalog-infra/src/integrationTest/java/dev/patra/catalog/infra/adapter/read/OrganizationReadAdapterIT.java`

**Interfaces:**
- Consumes: Task 1、2。
- Produces:
  - `OrganizationNameDao`：`List<OrganizationSuggestRow> suggestBySubstring(@Param("q") String q, @Param("contains") String contains, @Param("prefix") String prefix, @Param("wordStart") String wordStart, @Param("limit") int limit)`；`List<OrganizationSuggestRow> suggestByWordPrefix(@Param("q") String q, @Param("prefix") String prefix, @Param("wordStart") String wordStart, @Param("limit") int limit)`。两个 `@Query(nativeQuery = true)` 的 SQL 是 spec §10.2，只有候选 `WHERE` 不同，共享的片段放 `static final String` 常量拼接。
  - `OrganizationDao`：`Optional<OrganizationSummaryRow> findSummaryByRorId(@Param("rorId") String rorId)`（spec §10.3）。
  - `interface OrganizationSummaryRow { String getRorId(); String getDisplayName(); String getZhName(); String getCountryCode(); String getCityName(); String getTypes(); }`；`interface OrganizationSuggestRow extends OrganizationSummaryRow { String getMatchedName(); }`。
  - `@Repository @RequiredArgsConstructor public class OrganizationReadAdapter implements OrganizationReadPort`，构造注入两个 Dao；`types` 按空格拆成列表（空串 → 空列表）。

- [ ] **Step 1: 写失败的单元测试（mock Dao，断言拼出的模式）**

```java
class OrganizationReadAdapterTest {
  private final OrganizationNameDao nameDao = mock(OrganizationNameDao.class);
  private final OrganizationDao organizationDao = mock(OrganizationDao.class);
  private final OrganizationReadAdapter adapter = new OrganizationReadAdapter(nameDao, organizationDao);

  @Test
  void should_build_substring_patterns_with_escaped_metacharacters() {
    // 含汉字才走 SUBSTRING 模式
    adapter.suggest(SuggestQuery.of("协和%_a!", 10));
    verify(nameDao).suggestBySubstring("协和%_a!", "%协和!%!_a!!%", "协和!%!_a!!%", "% 协和!%!_a!!%", 10);
  }

  @Test
  void should_use_word_prefix_query_for_latin_text() {
    adapter.suggest(SuggestQuery.of("Mayo", 5));
    verify(nameDao).suggestByWordPrefix("Mayo", "Mayo%", "% Mayo%", 5);
    verify(nameDao, never()).suggestBySubstring(any(), any(), any(), any(), anyInt());
  }

  @Test
  void should_split_types_and_keep_nullable_fields() {
    OrganizationSuggestRow row = mock(OrganizationSuggestRow.class);
    when(row.getRorId()).thenReturn("0aaaaaaa1");
    when(row.getDisplayName()).thenReturn("Peking Union Medical College Hospital");
    when(row.getMatchedName()).thenReturn("PUMCH");
    when(row.getTypes()).thenReturn("funder healthcare");
    when(nameDao.suggestByWordPrefix(any(), any(), any(), any(), anyInt())).thenReturn(List.of(row));

    OrganizationSuggestion suggestion = adapter.suggest(SuggestQuery.of("PUMCH", 10)).getFirst();
    assertThat(suggestion.matchedName()).isEqualTo("PUMCH");
    assertThat(suggestion.organization().types()).containsExactly("funder", "healthcare");
    assertThat(suggestion.organization().zhName()).isNull();
  }

  @Test
  void should_map_empty_types_string_to_empty_list() {
    OrganizationSummaryRow row = mock(OrganizationSummaryRow.class);
    when(row.getRorId()).thenReturn("0aaaaaaa1");
    when(row.getTypes()).thenReturn("");
    when(organizationDao.findSummaryByRorId("0aaaaaaa1")).thenReturn(Optional.of(row));
    assertThat(adapter.findByRorId("0aaaaaaa1")).get().extracting(OrganizationSummary::types)
        .asInstanceOf(InstanceOfAssertFactories.LIST).isEmpty();
  }
}
```

- [ ] **Step 2: 跑单元测试确认失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:test --tests "*OrganizationReadAdapterTest*"`
Expected: 编译失败

- [ ] **Step 3: 写 `V9`、两个 Dao 方法、两个投影、`OrganizationReadAdapter`**

- [ ] **Step 4: 跑单元测试确认通过**

Run: 同 Step 2
Expected: PASS

- [ ] **Step 5: 写失败的集成测试**

照 `VenueBrowseReadAdapterIT` 的注解组合（`@DataJpaTest` + `CatalogITPostgreSQLContainerInitializer` + `@Import({OrganizationReadAdapter.class, JpaAuditingConfiguration.class})` + `@ActiveProfiles("test")`），用 `TestEntityManager` 造数据，ID 用 `SnowflakeIdGenerator.getId()`。辅助方法：`Long saveOrganization(String rorId, String displayName, String status, Set<String> types)`（`deleted_at` 另给一个重载）、`void saveName(Long orgId, String value, String type, String lang)`、`void saveLocation(Long orgId, String countryCode, String city)`。每个用例 `em.flush(); em.clear();` 后再查。用例与断言：

```java
@Test
void should_match_chinese_substring_and_rank_hospitals_before_colleges() {
  // 0aaaaaaa1 "Peking Union Medical College Hospital" + zh label 北京协和医院 (CN, healthcare)
  // 0aaaaaaa2 "Wuhan Union Hospital" + zh label 武汉协和医院 (CN, healthcare)
  // 0aaaaaaa3 "Shandong Xiehe University" + zh label 山东协和学院 (CN, education)
  // 两家医院各键都并列，最后按 display_name 字典序：Peking 在 Wuhan 前
  List<OrganizationSuggestion> items = adapter.suggest(SuggestQuery.of("协和", 10));
  assertThat(items).extracting(s -> s.organization().rorId())
      .containsExactly("0aaaaaaa1", "0aaaaaaa2", "0aaaaaaa3");
  assertThat(items.getFirst().matchedName()).isEqualTo("北京协和医院");
  assertThat(items.getFirst().organization().zhName()).isEqualTo("北京协和医院");
}

@Test
void should_rank_china_before_other_countries() {
  // 两家 display_name 都是 "Union Hospital"：US 的 ror_id 0aaaaaaa1、CN 的 0aaaaaaa2
  assertThat(adapter.suggest(SuggestQuery.of("Union Hospital", 10)))
      .extracting(s -> s.organization().rorId()).containsExactly("0aaaaaaa2", "0aaaaaaa1");
}

@Test
void should_rank_exact_above_prefix_above_word_start() {
  // "Mayo"(exact) / "Mayo Clinic"(prefix) / "Clinica Mayo"(word start)，都是 US
  assertThat(adapter.suggest(SuggestQuery.of("Mayo", 10)))
      .extracting(OrganizationSuggestion::matchedName)
      .containsExactly("Mayo", "Mayo Clinic", "Clinica Mayo");
}

@Test
void should_match_acronym_and_return_it_as_matched_name() {
  // 北京协和医院：ror_display "Peking Union Medical College Hospital"、label zh "北京协和医院"、acronym "PUMCH"
  OrganizationSuggestion s = adapter.suggest(SuggestQuery.of("PUMCH", 10)).getFirst();
  assertThat(s.matchedName()).isEqualTo("PUMCH");
  assertThat(s.organization().zhName()).isEqualTo("北京协和医院");
}

@Test
void should_not_match_inside_a_word_for_latin_query() {
  assertThat(adapter.suggest(SuggestQuery.of("ayo", 10))).isEmpty();   // 库里有 "Mayo Clinic"
}

@Test
void should_return_each_organization_once_with_its_best_name() {
  // 一家机构：ror_display "Peking University" + alias "Peking Univ"，查 "Peking"
  List<OrganizationSuggestion> items = adapter.suggest(SuggestQuery.of("Peking", 10));
  assertThat(items).hasSize(1);
  assertThat(items.getFirst().matchedName()).isEqualTo("Peking University");
}

@Test
void should_exclude_inactive_withdrawn_and_soft_deleted() {
  // 三家名字都含 "Ghost"：status inactive / withdrawn / active 但 deleted_at 非空
  assertThat(adapter.suggest(SuggestQuery.of("Ghost", 10))).isEmpty();
}

@Test
void should_prefer_zh_label_over_zh_alias_for_zh_name() {
  // 同一机构 zh alias "北京协和医学院" + zh label "中国医学科学院北京协和医学院"
  assertThat(adapter.suggest(SuggestQuery.of("协和医学院", 10)).getFirst().organization().zhName())
      .isEqualTo("中国医学科学院北京协和医学院");
}

@Test
void should_treat_like_metacharacters_as_literals() {
  // "100% Health" 与 "1000 Health"；"A_B Lab" 与 "AXB Lab"
  assertThat(adapter.suggest(SuggestQuery.of("100%", 10))).extracting(OrganizationSuggestion::matchedName)
      .containsExactly("100% Health");
  assertThat(adapter.suggest(SuggestQuery.of("A_B", 10))).extracting(OrganizationSuggestion::matchedName)
      .containsExactly("A_B Lab");
}

@Test
void should_return_empty_types_when_organization_types_is_null() {
  // saveOrganization(..., types = null)
  assertThat(adapter.suggest(SuggestQuery.of("Typeless", 10)).getFirst().organization().types()).isEmpty();
}

@Test
void should_respect_limit() {
  // 五家名字以 "Lim" 开头
  assertThat(adapter.suggest(SuggestQuery.of("Lim", 2))).hasSize(2);
}

@Test
void should_find_active_organization_by_ror_id() {
  OrganizationSummary s = adapter.findByRorId("0aaaaaaa1").orElseThrow();
  assertThat(s.displayName()).isEqualTo("Peking Union Medical College Hospital");
  assertThat(s.zhName()).isEqualTo("北京协和医院");
  assertThat(s.countryCode()).isEqualTo("CN");
  assertThat(s.types()).containsExactly("funder", "healthcare");
}

@Test
void should_return_empty_for_unknown_or_inactive_ror_id() {
  assertThat(adapter.findByRorId("0zzzzzzz9")).isEmpty();
  assertThat(adapter.findByRorId("0aaaaaaa7")).isEmpty();   // status inactive
}
```

- [ ] **Step 6: 跑集成测试（本机 Docker 在线）**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest --tests "*OrganizationReadAdapterIT*"`
Expected: PASS；日志里 Flyway 应用到 `V9`（`CREATE EXTENSION pg_trgm` 在 `postgres:17` 上成功）

- [ ] **Step 7: 提交**

```bash
git add patra-api/patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V9__add_suggest_trigram_indexes.sql patra-api/patra-catalog/patra-catalog-infra/src/main/java/dev/patra/catalog/infra/persistence/dao/OrganizationNameDao.java patra-api/patra-catalog/patra-catalog-infra/src/main/java/dev/patra/catalog/infra/persistence/dao/OrganizationDao.java patra-api/patra-catalog/patra-catalog-infra/src/main/java/dev/patra/catalog/infra/adapter/read/Organization*.java patra-api/patra-catalog/patra-catalog-infra/src/test/java/dev/patra/catalog/infra/adapter/read/OrganizationReadAdapterTest.java patra-api/patra-catalog/patra-catalog-infra/src/integrationTest/java/dev/patra/catalog/infra/adapter/read/OrganizationReadAdapterIT.java
git commit -m "feat(catalog): 加 trigram 索引迁移与机构建议读适配器 (PAP-94)"
```

---

### Task 5: MeSH 读适配器

**Files:**
- Modify: `patra-api/patra-catalog/patra-catalog-infra/src/main/java/dev/patra/catalog/infra/persistence/dao/MeshDescriptorDao.java`
- Create: `patra-api/patra-catalog/patra-catalog-infra/src/main/java/dev/patra/catalog/infra/adapter/read/MeshDescriptorSummaryRow.java`
- Create: `.../infra/adapter/read/MeshDescriptorSuggestRow.java`
- Create: `.../infra/adapter/read/MeshDescriptorReadAdapter.java`
- Test: `patra-api/patra-catalog/patra-catalog-infra/src/test/java/dev/patra/catalog/infra/adapter/read/MeshDescriptorReadAdapterTest.java`
- Test: `patra-api/patra-catalog/patra-catalog-infra/src/integrationTest/java/dev/patra/catalog/infra/adapter/read/MeshDescriptorReadAdapterIT.java`

**Interfaces:**
- Consumes: Task 1、2。
- Produces:
  - `MeshDescriptorDao`：`List<MeshDescriptorSuggestRow> suggest(@Param("q") String q, @Param("prefix") String prefix, @Param("wordStart") String wordStart, @Param("limit") int limit)`（spec §10.4）；`Optional<MeshDescriptorSummaryRow> findSummaryByUi(@Param("ui") String ui)`（spec §10.5）。
  - `interface MeshDescriptorSummaryRow { String getUi(); String getName(); String getTreeNumbers(); }`；`interface MeshDescriptorSuggestRow extends MeshDescriptorSummaryRow { String getMatchedTerm(); }`。
  - `@Repository @RequiredArgsConstructor public class MeshDescriptorReadAdapter implements MeshDescriptorReadPort`：忽略 `MatchMode`（MeSH 只做前缀与词首）；`treeNumbers` 按空格拆（空 / null → 空列表）。

- [ ] **Step 1: 写失败的单元测试**

```java
class MeshDescriptorReadAdapterTest {
  private final MeshDescriptorDao dao = mock(MeshDescriptorDao.class);
  private final MeshDescriptorReadAdapter adapter = new MeshDescriptorReadAdapter(dao);

  @Test
  void should_build_prefix_and_word_start_patterns_regardless_of_mode() {
    adapter.suggest(SuggestQuery.of("糖尿病 50%", 10));
    verify(dao).suggest("糖尿病 50%", "糖尿病 50!%%", "% 糖尿病 50!%%", 10);
  }

  @Test
  void should_split_tree_numbers_and_keep_matched_term() {
    MeshDescriptorSuggestRow row = mock(MeshDescriptorSuggestRow.class);
    when(row.getUi()).thenReturn("D009369");
    when(row.getName()).thenReturn("Neoplasms");
    when(row.getMatchedTerm()).thenReturn("Cancer");
    when(row.getTreeNumbers()).thenReturn("C04");
    when(dao.suggest(any(), any(), any(), anyInt())).thenReturn(List.of(row));

    MeshDescriptorSuggestion s = adapter.suggest(SuggestQuery.of("Cancer", 10)).getFirst();
    assertThat(s.descriptor().treeNumbers()).containsExactly("C04");
    assertThat(s.matchedTerm()).isEqualTo("Cancer");
  }

  @Test
  void should_map_null_tree_numbers_to_empty_list() {
    MeshDescriptorSummaryRow row = mock(MeshDescriptorSummaryRow.class);
    when(row.getUi()).thenReturn("D003920");
    when(row.getTreeNumbers()).thenReturn(null);
    when(dao.findSummaryByUi("D003920")).thenReturn(Optional.of(row));
    assertThat(adapter.findByUi("D003920").orElseThrow().treeNumbers()).isEmpty();
  }
}
```

- [ ] **Step 2: 跑单元测试确认失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:test --tests "*MeshDescriptorReadAdapterTest*"`
Expected: 编译失败

- [ ] **Step 3: 写 Dao 方法、投影、`MeshDescriptorReadAdapter`**

- [ ] **Step 4: 跑单元测试确认通过**

- [ ] **Step 5: 写失败的集成测试**

注解组合同 Task 4（`@Import({MeshDescriptorReadAdapter.class, JpaAuditingConfiguration.class})`）。辅助方法：`void saveDescriptor(String ui, String name, String descriptorClass)`（`activeStatus = true`）、`void saveEntryTerm(String ownerUi, String term, boolean permuted)`（`recordType = MeshRecordType.DESCRIPTOR`，`isPrintFlag = true`，`isConceptPreferred = false`）、`void saveScrTerm(String ownerUi, String term)`（`recordType = SCR`）、`void saveTreeNumber(String ui, String treeNumber, int level, boolean primary)`。用例：

```java
@Test
void should_rank_exact_name_first() {
  // D009369 Neoplasms、D009371 "Neoplasms by Site"
  assertThat(adapter.suggest(SuggestQuery.of("Neoplasms", 10)))
      .extracting(s -> s.descriptor().ui()).containsExactly("D009369", "D009371");
}

@Test
void should_return_descriptor_via_exact_entry_term_before_prefix_names() {
  // D009369 Neoplasms 的入口词 "Cancer"；D000072716 "Cancer Pain"
  List<MeshDescriptorSuggestion> items = adapter.suggest(SuggestQuery.of("Cancer", 10));
  assertThat(items).extracting(s -> s.descriptor().ui()).containsExactly("D009369", "D000072716");
  assertThat(items.get(0).matchedTerm()).isEqualTo("Cancer");
  assertThat(items.get(1).matchedTerm()).isNull();
}

@Test
void should_null_matched_term_when_entry_term_equals_name() {
  // D003920 Diabetes Mellitus 的入口词 "Diabetes Mellitus"（首选词也在入口词表里）
  assertThat(adapter.suggest(SuggestQuery.of("Diabetes Mellitus", 10)).getFirst().matchedTerm()).isNull();
}

@Test
void should_ignore_permuted_and_scr_terms() {
  // D003920 的排列入口词 "Mellitus, Diabetes"；SCR C000001 的入口词 "Mellitus Compound"
  assertThat(adapter.suggest(SuggestQuery.of("Mellitus,", 10))).isEmpty();
  assertThat(adapter.suggest(SuggestQuery.of("Mellitus Compound", 10))).isEmpty();
}

@Test
void should_exclude_non_topical_descriptor_classes() {
  // D016454 "Review" 是 class 2
  assertThat(adapter.suggest(SuggestQuery.of("Review", 10))).isEmpty();
}

@Test
void should_match_word_start() {
  assertThat(adapter.suggest(SuggestQuery.of("mellitus", 10)).getFirst().descriptor().name())
      .isEqualTo("Diabetes Mellitus");
}

@Test
void should_order_tree_numbers_primary_first() {
  // D003920：C19.246 非主、C18.452.394.750 主
  assertThat(adapter.findByUi("D003920").orElseThrow().treeNumbers())
      .containsExactly("C18.452.394.750", "C19.246");
}

@Test
void should_return_empty_for_cjk_query() {
  assertThat(adapter.suggest(SuggestQuery.of("糖尿病", 10))).isEmpty();
}

@Test
void should_find_descriptor_by_ui_regardless_of_class_and_return_empty_for_unknown() {
  assertThat(adapter.findByUi("D016454").orElseThrow().name()).isEqualTo("Review");
  assertThat(adapter.findByUi("D999999")).isEmpty();
}

@Test
void should_respect_limit() {
  // 五个以 "Lim" 开头的主题词
  assertThat(adapter.suggest(SuggestQuery.of("Lim", 3))).hasSize(3);
}
```

- [ ] **Step 6: 跑集成测试**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest --tests "*MeshDescriptorReadAdapterIT*"`
Expected: PASS

- [ ] **Step 7: 提交**

```bash
git add patra-api/patra-catalog/patra-catalog-infra/src/main/java/dev/patra/catalog/infra/persistence/dao/MeshDescriptorDao.java patra-api/patra-catalog/patra-catalog-infra/src/main/java/dev/patra/catalog/infra/adapter/read/MeshDescriptor*.java patra-api/patra-catalog/patra-catalog-infra/src/test/java/dev/patra/catalog/infra/adapter/read/MeshDescriptorReadAdapterTest.java patra-api/patra-catalog/patra-catalog-infra/src/integrationTest/java/dev/patra/catalog/infra/adapter/read/MeshDescriptorReadAdapterIT.java
git commit -m "feat(catalog): 加 MeSH 主题词建议与查询的读适配器 (PAP-94)"
```

---

### Task 6: 门户建议控制器

**Files:**
- Create: `patra-api/patra-catalog/patra-catalog-adapter/src/main/java/dev/patra/catalog/adapter/rest/portal/request/PortalSuggestRequest.java`
- Create: `.../adapter/rest/portal/response/PortalSuggestionsResponse.java`
- Create: `.../adapter/rest/portal/response/PortalOrganizationSuggestionResponse.java`
- Create: `.../adapter/rest/portal/response/PortalMeshSuggestionResponse.java`
- Modify: `.../adapter/rest/portal/PortalApiConverter.java`
- Create: `.../adapter/rest/portal/PortalOrganizationController.java`
- Create: `.../adapter/rest/portal/PortalMeshController.java`
- Test: `patra-api/patra-catalog/patra-catalog-adapter/src/test/java/dev/patra/catalog/adapter/rest/portal/request/PortalSuggestRequestTest.java`
- Test: `patra-api/patra-catalog/patra-catalog-adapter/src/test/java/dev/patra/catalog/adapter/rest/portal/PortalApiConverterSuggestTest.java`
- Test: `patra-api/patra-catalog/patra-catalog-adapter/src/integrationTest/java/dev/patra/catalog/adapter/rest/portal/PortalOrganizationControllerIT.java`
- Test: `patra-api/patra-catalog/patra-catalog-adapter/src/integrationTest/java/dev/patra/catalog/adapter/rest/portal/PortalMeshControllerIT.java`

**Interfaces:**
- Consumes: Task 3 的查询服务、Task 2 的读模型、Task 1 的 `SuggestQuery`。
- Produces:
  - `public record PortalSuggestRequest(@NotBlank(message = "q 不能为空") String q, @Min(value = 1, message = "limit 最小为 1") @Max(value = 20, message = "limit 最大为 20") Integer limit)`：紧凑构造器对 `q` 做 NFKC + `strip()` + 空白折叠（与 `SuggestQuery` 相同，`null` 保持 `null`）；`@AssertTrue(message = "q 须为 2–100 个字符") public boolean isQLengthValid()` 按码点算，`q == null` 时返回 `true`（交给 `@NotBlank` 报）；`public int limitOrDefault()` 空时给 `SuggestQuery.DEFAULT_LIMIT`。
  - `public record PortalSuggestionsResponse<T>(List<T> items)` + `static <T> PortalSuggestionsResponse<T> of(List<T> items)`。
  - `@Builder public record PortalOrganizationSuggestionResponse(String rorId, String displayName, String zhName, String matchedName, String countryCode, String cityName, List<String> types)`。
  - `public record PortalMeshSuggestionResponse(String ui, String name, String matchedTerm, List<String> treeNumbers)` + `of()`。
  - `PortalApiConverter.toOrganizationSuggestionResponse(OrganizationSuggestion)`、`toMeshSuggestionResponse(MeshDescriptorSuggestion)`。
  - `@RestController @RequestMapping("/portal/organizations") PortalOrganizationController`：`@GetMapping("/suggest") PortalSuggestionsResponse<PortalOrganizationSuggestionResponse> suggest(@Valid PortalSuggestRequest req)`，把 `SuggestQuery.of(req.q(), req.limitOrDefault())` 交给 `OrganizationQueryService.suggest`。`@RequestMapping("/portal/mesh") PortalMeshController` 同构。类上加 `@Tag(name = "Portal", ...)` 与 `@Validated`（照 `PortalVenueController`）。

- [ ] **Step 1: 写失败的单元测试**

```java
class PortalSuggestRequestTest {
  @Test
  void should_normalize_q_and_default_limit() {
    PortalSuggestRequest req =
        new PortalSuggestRequest(Character.toString(0x3000) + "ＰＵＭＣＨ  协和 ", null);
    assertThat(req.q()).isEqualTo("PUMCH 协和");
    assertThat(req.limitOrDefault()).isEqualTo(10);
    assertThat(new PortalSuggestRequest(null, 5).q()).isNull();
  }

  @Test
  void should_validate_q_length_by_code_points() {
    assertThat(new PortalSuggestRequest("协和", null).isQLengthValid()).isTrue();
    assertThat(new PortalSuggestRequest("协", null).isQLengthValid()).isFalse();
    assertThat(new PortalSuggestRequest("a".repeat(101), null).isQLengthValid()).isFalse();
    assertThat(new PortalSuggestRequest(null, null).isQLengthValid()).isTrue();
  }
}

class PortalApiConverterSuggestTest {
  private final PortalApiConverter converter = new PortalApiConverter();

  @Test
  void should_map_organization_suggestion() {
    OrganizationSuggestion s = OrganizationSuggestion.of(OrganizationSummary.builder()
        .rorId("0aaaaaaa1").displayName("Peking Union Medical College Hospital").zhName("北京协和医院")
        .countryCode("CN").cityName("Beijing").types(List.of("funder", "healthcare")).build(), "PUMCH");
    PortalOrganizationSuggestionResponse r = converter.toOrganizationSuggestionResponse(s);
    assertThat(r.rorId()).isEqualTo("0aaaaaaa1");
    assertThat(r.matchedName()).isEqualTo("PUMCH");
    assertThat(r.types()).containsExactly("funder", "healthcare");
  }

  @Test
  void should_map_mesh_suggestion() {
    PortalMeshSuggestionResponse r = converter.toMeshSuggestionResponse(
        MeshDescriptorSuggestion.of(MeshDescriptorSummary.of("D009369", "Neoplasms", List.of("C04")), "Cancer"));
    assertThat(r).isEqualTo(PortalMeshSuggestionResponse.of("D009369", "Neoplasms", "Cancer", List.of("C04")));
  }
}
```

- [ ] **Step 2: 跑单元测试确认失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-adapter:test --tests "*PortalSuggestRequestTest*" --tests "*PortalApiConverterSuggestTest*"`
Expected: 编译失败

- [ ] **Step 3: 实现请求记录、响应记录、转换器方法、两个控制器**

- [ ] **Step 4: 跑单元测试确认通过**

- [ ] **Step 5: 写失败的切片测试**

照 `PortalVenueControllerIT`：`@WebMvcTest(controllers = PortalOrganizationController.class)` + `@ContextConfiguration(classes = CatalogAdapterITWebMvcConfig.class)` + `@Import({PortalOrganizationController.class, PortalApiConverter.class})` + `@AutoConfigureRestTestClient`，`@MockitoBean OrganizationQueryService`。

```java
@Test
void should_return_items_with_default_limit_10() {
  when(service.suggest(any())).thenReturn(List.of(OrganizationSuggestion.of(OrganizationSummary.builder()
      .rorId("0aaaaaaa1").displayName("Peking Union Medical College Hospital").zhName("北京协和医院")
      .countryCode("CN").cityName("Beijing").types(List.of("funder", "healthcare")).build(), "北京协和医院")));
  restClient.get().uri(b -> b.path("/portal/organizations/suggest").queryParam("q", "协和").build())
      .exchange().expectStatus().isOk().expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody()
      .jsonPath("$.items[0].rorId").isEqualTo("0aaaaaaa1")
      .jsonPath("$.items[0].zhName").isEqualTo("北京协和医院")
      .jsonPath("$.items[0].types[1]").isEqualTo("healthcare");
  ArgumentCaptor<SuggestQuery> captor = ArgumentCaptor.forClass(SuggestQuery.class);
  verify(service).suggest(captor.capture());
  assertThat(captor.getValue()).isEqualTo(SuggestQuery.of("协和", 10));
}

@Test
void should_pass_normalized_query_and_limit() {
  when(service.suggest(any())).thenReturn(List.of());
  restClient.get().uri(b -> b.path("/portal/organizations/suggest").queryParam("q", "  Mayo  Clinic ")
      .queryParam("limit", 5).build()).exchange().expectStatus().isOk()
      .expectBody().jsonPath("$.items").isArray().jsonPath("$.items.length()").isEqualTo(0);
  verify(service).suggest(SuggestQuery.of("Mayo Clinic", 5));
}

@Test
void should_reject_invalid_parameters_with_422() {
  for (String uri : List.of("/portal/organizations/suggest", "/portal/organizations/suggest?q=a",
      "/portal/organizations/suggest?q=" + "a".repeat(101), "/portal/organizations/suggest?q=Mayo&limit=0",
      "/portal/organizations/suggest?q=Mayo&limit=21")) {
    restClient.get().uri(uri).exchange().expectStatus().isEqualTo(422);
  }
  verifyNoInteractions(service);
}

@Test
void should_reject_non_numeric_limit_without_touching_service() {
  restClient.get().uri("/portal/organizations/suggest?q=Mayo&limit=abc").exchange()
      .expectStatus().is4xxClientError();
  verifyNoInteractions(service);
}
```

`PortalMeshControllerIT` 同构：`?q=Cancer` → `$.items[0].ui = D009369`、`$.items[0].matchedTerm = Cancer`、`$.items[0].treeNumbers[0] = C04`；`?q=diabetes mellitus` 传给服务的是 `SuggestQuery.of("diabetes mellitus", 10)`；422 与 `limit=abc` 两条。

- [ ] **Step 6: 跑切片测试**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-adapter:integrationTest --tests "*PortalOrganizationControllerIT*" --tests "*PortalMeshControllerIT*"`
Expected: PASS

- [ ] **Step 7: 提交**

```bash
git add patra-api/patra-catalog/patra-catalog-adapter
git commit -m "feat(catalog): 加门户的机构与 MeSH 建议接口 (PAP-94)"
```

---

### Task 7: `patra-catalog-api` 契约与内部端点

**Files:**
- Create: `patra-api/patra-catalog/patra-catalog-api/src/main/java/dev/patra/catalog/api/endpoint/OrganizationEndpoint.java`
- Create: `patra-api/patra-catalog/patra-catalog-api/src/main/java/dev/patra/catalog/api/endpoint/MeshEndpoint.java`
- Create: `patra-api/patra-catalog/patra-catalog-api/src/main/java/dev/patra/catalog/api/dto/organization/OrganizationResp.java`
- Create: `patra-api/patra-catalog/patra-catalog-api/src/main/java/dev/patra/catalog/api/dto/mesh/MeshDescriptorResp.java`
- Create: `patra-api/patra-catalog/patra-catalog-adapter/src/main/java/dev/patra/catalog/adapter/rest/internal/OrganizationEndpointImpl.java`
- Create: `patra-api/patra-catalog/patra-catalog-adapter/src/main/java/dev/patra/catalog/adapter/rest/internal/MeshEndpointImpl.java`
- Test: `patra-api/patra-catalog/patra-catalog-adapter/src/integrationTest/java/dev/patra/catalog/adapter/rest/internal/OrganizationEndpointIT.java`
- Test: `patra-api/patra-catalog/patra-catalog-adapter/src/integrationTest/java/dev/patra/catalog/adapter/rest/internal/MeshEndpointIT.java`

**Interfaces:**
- Consumes: Task 3 的查询服务。
- Produces（spec §8.6 原文）：`OrganizationEndpoint`（`@HttpExchange(url = "/_internal/organizations", accept = "application/json")`，`@GetExchange("/{rorId}") OrganizationResp getOrganization(@PathVariable("rorId") String rorId)`）；`MeshEndpoint`（`@HttpExchange(url = "/_internal/mesh", ...)`，`@GetExchange("/descriptors/{ui}") MeshDescriptorResp getDescriptor(@PathVariable("ui") String ui)`）；`@Builder record OrganizationResp(String rorId, String displayName, String zhName, String countryCode, List<String> types)`；`record MeshDescriptorResp(String ui, String name, List<String> treeNumbers)` + `of()`；两个 `@RestController @RequiredArgsConstructor ...EndpointImpl implements ...Endpoint`，方法体只是「查询服务 → DTO」。api 模块的 `tasks.test` 被插件关了，DTO 不写测试；两个 record 的 `List` 字段紧凑构造器 `copyOf`。

- [ ] **Step 1: 写失败的切片测试**

`@WebMvcTest(controllers = OrganizationEndpointImpl.class)` + `@ContextConfiguration(classes = CatalogAdapterITWebMvcConfig.class)` + `@Import(OrganizationEndpointImpl.class)` + `@AutoConfigureRestTestClient` + `@TestPropertySource(properties = "patra.web.error.context-prefix=CATALOG")`，`@MockitoBean OrganizationQueryService`。

```java
@Test
void should_return_organization_json() {
  when(service.getByRorId("0aaaaaaa1")).thenReturn(OrganizationSummary.builder().rorId("0aaaaaaa1")
      .displayName("Peking Union Medical College Hospital").zhName("北京协和医院").countryCode("CN")
      .cityName("Beijing").types(List.of("funder", "healthcare")).build());
  restClient.get().uri("/_internal/organizations/0aaaaaaa1").exchange().expectStatus().isOk()
      .expectHeader().contentType(MediaType.APPLICATION_JSON).expectBody()
      .jsonPath("$.rorId").isEqualTo("0aaaaaaa1")
      .jsonPath("$.zhName").isEqualTo("北京协和医院")
      .jsonPath("$.countryCode").isEqualTo("CN")
      .jsonPath("$.types[0]").isEqualTo("funder")
      .jsonPath("$.cityName").doesNotExist();
}

@Test
void should_return_404_with_catalog_code_when_not_found() {
  when(service.getByRorId("0zzzzzzz9")).thenThrow(new OrganizationNotFoundException("0zzzzzzz9"));
  restClient.get().uri("/_internal/organizations/0zzzzzzz9").exchange().expectStatus().isNotFound()
      .expectBody().jsonPath("$.code").isEqualTo("CATALOG-0404");
}
```

`MeshEndpointIT` 同构：`/_internal/mesh/descriptors/D003920` → `$.ui`、`$.name`、`$.treeNumbers[0] = C18.452.394.750`；`D999999` → 404 + `CATALOG-0404`。

- [ ] **Step 2: 跑切片测试确认失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-adapter:integrationTest --tests "*OrganizationEndpointIT*" --tests "*MeshEndpointIT*"`
Expected: 编译失败

- [ ] **Step 3: 写 api 模块的两个接口、两个 DTO，adapter 的两个端点实现**

- [ ] **Step 4: 跑切片测试确认通过；跑 api 模块 check 确认 R5 不报**

Run: 同 Step 2；`./gradlew :patra-api:patra-catalog:patra-catalog-api:check`
Expected: PASS / `BUILD SUCCESSFUL`

- [ ] **Step 5: 提交**

```bash
git add patra-api/patra-catalog/patra-catalog-api/src patra-api/patra-catalog/patra-catalog-adapter/src/main/java/dev/patra/catalog/adapter/rest/internal patra-api/patra-catalog/patra-catalog-adapter/src/integrationTest/java/dev/patra/catalog/adapter/rest/internal
git commit -m "feat(catalog): 加机构与 MeSH 的内部查询契约与端点 (PAP-94)"
```

---

### Task 8: 网关集成测试

**Files:**
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/GatewayBlockedPathsIT.java`（`should_answer_403_for_blocked_paths_whether_anonymous_or_logged_in` 的 `blocked` 列表）
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/GatewayRoutingIT.java`

- [ ] **Step 1: 加用例**

`GatewayBlockedPathsIT` 的 `blocked` 列表加 `"/patra-catalog/_internal/organizations/04jztag35"`、`"/patra-catalog/_internal/mesh/descriptors/D003920"`（既有断言已覆盖匿名与登录两种身份、WireMock 收不到请求）。

`GatewayRoutingIT` 加：

```java
@Test
void should_forward_anonymous_suggest_request_with_chinese_query_unchanged() {
  catalog.stubFor(get(urlPathEqualTo("/portal/organizations/suggest"))
      .withQueryParam("q", equalTo("协和")).withQueryParam("limit", equalTo("5"))
      .willReturn(okJson("{\"items\":[]}")));

  restClient.get().uri(b -> b.path("/patra-catalog/portal/organizations/suggest")
          .queryParam("q", "协和").queryParam("limit", 5).build())
      .exchange().expectStatus().isOk().expectBody().json("{\"items\":[]}");

  catalog.verify(getRequestedFor(urlPathEqualTo("/portal/organizations/suggest"))
      .withQueryParam("q", equalTo("协和")).withoutHeader(HttpHeaders.AUTHORIZATION));
}
```

- [ ] **Step 2: 跑网关集成测试（Redis 容器）**

Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*GatewayBlockedPathsIT*" --tests "*GatewayRoutingIT*"`
Expected: PASS

- [ ] **Step 3: 提交**

```bash
git add patra-api/patra-gateway-boot/src/integrationTest
git commit -m "test(gateway): 补 catalog 内部接口拒绝与建议接口匿名转发的用例 (PAP-94)"
```

---

### Task 9（待拍板 1 选方案 A 时执行；选 B 跳过）: 机构别名表、种子与查询并入

必须在 Task 10 之前做完（Task 10 会让 Flyway 把 `V9` 应用到 mini，之后再改 `V9` 会校验失败）。

**Files:**
- Modify: `patra-api/patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V9__add_suggest_trigram_indexes.sql`（追加建表）
- Create: `patra-api/patra-catalog/patra-catalog-infra/src/main/resources/db/migration/R__cat_organization_alias_seed.sql`
- Modify: `OrganizationNameDao`（两个建议 SQL）、`OrganizationDao.findSummaryByRorId`
- Modify: `OrganizationReadAdapterIT`

**Interfaces:**
- Produces：表 `cat_organization_alias(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, ror_id VARCHAR(50) NOT NULL, alias VARCHAR(500) NOT NULL, lang VARCHAR(10), alias_type VARCHAR(20) NOT NULL CHECK (alias_type IN ('LABEL','ALIAS')), source VARCHAR(20) NOT NULL CHECK (source IN ('SEED','WIKIDATA')), created_at TIMESTAMPTZ(6) NOT NULL DEFAULT now(), CONSTRAINT uk_org_alias UNIQUE (ror_id, alias))`，索引 `idx_org_alias_ror_id (ror_id)`、`idx_org_alias_trgm USING gin (alias gin_trgm_ops)`。没有 JPA 实体（只有 SQL 读写）。
- 建议 SQL 的改法（spec §16）：候选 `cand` 改成 `SELECT ... FROM (名称表子查询 UNION ALL SELECT o2.id AS org_id, a.alias AS name_value, <同样的 match_rank 表达式>, 1 AS type_rank FROM cat_organization_alias a JOIN cat_organization o2 ON o2.ror_id = a.ror_id WHERE a.alias ILIKE ...) u ORDER BY match_rank, type_rank, length(name_value) LIMIT 500`；`zh` LATERAL 改成 `SELECT name_value FROM (SELECT a.alias AS name_value, 0 AS pri FROM cat_organization_alias a WHERE a.ror_id = o.ror_id AND a.alias_type = 'LABEL' AND a.lang = 'zh' UNION ALL SELECT z.name_value, CASE WHEN jsonb_exists(z.types, 'label') THEN 1 ELSE 2 END FROM cat_organization_name z WHERE z.org_id = o.id AND z.lang = 'zh') x ORDER BY pri, length(name_value), name_value LIMIT 1`；`findSummaryByRorId` 的 `zh` 同样改。
- 种子文件格式：文件头注释写明来源（手工清单；Wikidata SPARQL 原文与取数日期）；正文 `DELETE FROM cat_organization_alias WHERE source IN ('SEED', 'WIKIDATA');` 然后一条多行 `INSERT INTO cat_organization_alias (ror_id, alias, lang, alias_type, source) VALUES (...)`。

- [ ] **Step 1: 写失败的集成测试（加到 `OrganizationReadAdapterIT`，别名行用 `em.getEntityManager().createNativeQuery("INSERT INTO cat_organization_alias ...").executeUpdate()` 插）**

```java
@Test
void should_match_patra_alias_and_use_seed_label_as_zh_name() {
  // 0aaaaaaa4 "West China Hospital of Sichuan University"，无 ROR 中文名；别名表 LABEL zh "四川大学华西医院"、ALIAS zh "华西"
  OrganizationSuggestion s = adapter.suggest(SuggestQuery.of("华西", 10)).getFirst();
  assertThat(s.organization().rorId()).isEqualTo("0aaaaaaa4");
  assertThat(s.matchedName()).isEqualTo("华西");
  assertThat(s.organization().zhName()).isEqualTo("四川大学华西医院");
}

@Test
void should_let_seed_label_override_ror_zh_label() {
  // 0aaaaaaa5 ROR zh label "北京市心肺血管疾病研究所"；别名表 LABEL zh "北京安贞医院"
  assertThat(adapter.findByRorId("0aaaaaaa5").orElseThrow().zhName()).isEqualTo("北京安贞医院");
}
```

- [ ] **Step 2: 跑集成测试确认失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest --tests "*OrganizationReadAdapterIT*"`
Expected: FAIL（表不存在 / 查不到）

- [ ] **Step 3: 改 `V9`、写种子文件骨架（先只放第 4 节那 18 家的手工行）、改三条 SQL**

- [ ] **Step 4: 跑集成测试确认通过（含原有用例）**

- [ ] **Step 5: 生成 Wikidata 部分的种子行**

在 scratchpad 写脚本（不入库）：用 mini 上的 SQL 导出「CN、active、无 zh 名、有 wikidata 外部 ID」的 `(ror_id, wikidata QID)`（spec §4 的 199 家），对 `https://query.wikidata.org/sparql` 发 SPARQL（`VALUES ?item {...}`，取 `rdfs:label` 与 `skos:altLabel`，语言依次取 `zh-hans`、`zh-cn`、`zh`），标签生成 `LABEL` 行、别名生成 `ALIAS` 行，`source = 'WIKIDATA'`，追加进种子文件；SPARQL 原文与日期写进文件头注释。再按常见三甲清单起草手工行到百家左右（`source = 'SEED'`）。

- [ ] **Step 6: 给用户审种子文件（阻塞点），审完再提交**

```bash
git add patra-api/patra-catalog/patra-catalog-infra/src/main/resources/db/migration patra-api/patra-catalog/patra-catalog-infra/src/main/java/dev/patra/catalog/infra/persistence/dao/OrganizationNameDao.java patra-api/patra-catalog/patra-catalog-infra/src/main/java/dev/patra/catalog/infra/persistence/dao/OrganizationDao.java patra-api/patra-catalog/patra-catalog-infra/src/integrationTest/java/dev/patra/catalog/infra/adapter/read/OrganizationReadAdapterIT.java
git commit -m "feat(catalog): 加 Patra 自维护的机构别名表与种子，建议查询并入别名 (PAP-94)"
```

---

### Task 10: README、HTTP 级实测、全量门控、文档写回

**Files:**
- Modify: `patra-api/patra-catalog/README.md`（spec §13：功能 14、「内部接口」一节、数据模型的索引说明、变更日志一行）
- Modify: `docs/patra/specs/2026-10-21-catalog-org-mesh-suggest-design.md`（§11 写「实际：」行；状态行改「已实施」）

- [ ] **Step 1: 改 README**

- [ ] **Step 2: 全量门控**

Run: `./gradlew spotlessApply && ./gradlew check`
Expected: `BUILD SUCCESSFUL`

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest :patra-api:patra-catalog:patra-catalog-adapter:integrationTest :patra-api:patra-gateway-boot:integrationTest`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 本机起 catalog 连 mini，做 HTTP 级实测**

在 IDEA 里按平时的方式启动 `PatraCatalogApplication`（dev profile，`~/.patra/secrets/` 的密钥文件齐全；若从终端起，先 export Global Constraints 里的 `JAVA_TOOL_OPTIONS`）。日志里 Flyway 对 mini 的 `patra_catalog` 应用 `V9`（有 Task 9 时还有 `R__` 种子）。然后：

```bash
BASE=http://localhost:6300
for q in 协和 华西 北京大学 人民医院 医院 大学 中山 医学院 Mayo Harvard PUMCH University Hospital "union hospital" "Peking University" ab Un Med "Johns Hopkins" "General Hospital"; do
  for i in $(seq 10); do curl --noproxy '*' -s -o /dev/null -w '%{time_total}\n' -G "$BASE/portal/organizations/suggest" --data-urlencode "q=$q"; done
done | sort -n | awk '{a[NR]=$1} END {printf "org p95=%.1f ms max=%.1f ms\n", a[int(NR*0.95)]*1000, a[NR]*1000}'
for q in Diabetes Cancer Neoplasms mellitus ca Heart disease COVID stroke immun He Pa "Myocardial Infarction" Hypertension "lung cancer" alzheimer cell protein; do
  for i in $(seq 10); do curl --noproxy '*' -s -o /dev/null -w '%{time_total}\n' -G "$BASE/portal/mesh/suggest" --data-urlencode "q=$q"; done
done | sort -n | awk '{a[NR]=$1} END {printf "mesh p95=%.1f ms max=%.1f ms\n", a[int(NR*0.95)]*1000, a[NR]*1000}'
```

同时用 `curl --noproxy '*' "$BASE/portal/organizations/suggest?q=协和"`、`"$BASE/portal/mesh/suggest?q=Cancer"`、`"$BASE/_internal/organizations/04jztag35"`、`"$BASE/_internal/mesh/descriptors/D003920"`、`"$BASE/_internal/organizations/0zzzzzzz9"`（404）各看一眼，并在 mini 上 `EXPLAIN (ANALYZE)` 一条「北京大学」与一条「Diabetes」确认走了 `idx_org_name_value_trgm` / `idx_mesh_desc_name_trgm`。

Expected: 两个 p95 都 < 200 ms；数字写进 spec §11「实际：」行（含日期、本机 → mini 的网络路径说明）。

- [ ] **Step 4: 写回设计文档、提交**

```bash
git add patra-api/patra-catalog/README.md docs/patra/specs/2026-10-21-catalog-org-mesh-suggest-design.md docs/patra/plans/2026-10-21-catalog-org-mesh-suggest.md
git commit -m "docs(catalog): 更新 README 与建议接口设计的实测结果 (PAP-94)"
```

- [ ] **Step 5: 收尾**

`git status` 确认只提交了本 Issue 的路径（identity 的改动仍留在工作区不动）；Linear 上把 PAP-94 推到 `Review`，在 Issue 评论里按 AC 逐条给证据（spec §15 的表）；经网关的 p95 与 mini 容器内的数字留作部署后的待办，写进会话收尾汇报。

---

## 自查记录

- **Spec 覆盖**：§7.1 → Task 1；§7.2 / §10.2 / §10.3 → Task 4；§7.3 / §10.4 / §10.5 → Task 5；§8.1 / §8.2 / §8.5 的 422 → Task 6；§8.3 / §8.4 / §8.6 / §8.5 的 404 → Task 3（格式校验）+ Task 7；§9 → Task 8；§10.1 → Task 4；§11 → Task 10；§12 → 各任务；§13 → Task 10；§14 只是约束，不需任务；§16 方案 A → Task 9（条件）。
- **类型一致**：`SuggestQuery` / `MatchMode`（Task 1）在 Task 3、4、5、6 的签名与测试里同名；`OrganizationSummary` / `OrganizationSuggestion` / `MeshDescriptorSummary` / `MeshDescriptorSuggestion`（Task 2）在 Task 3 ~ 7 一致；Dao 方法 `suggestBySubstring` / `suggestByWordPrefix` / `findSummaryByRorId` / `suggest` / `findSummaryByUi` 在 Task 4、5 的单测 verify 与实现一致；`limitOrDefault()` 只在 Task 6 用。
- **Review Focus**：五条各有归属用例（Task 4 两条、Task 3 + 7、Task 5、Task 6）。
- **比例**：代码块只放测试断言与 SQL 改法，没有实现体。
