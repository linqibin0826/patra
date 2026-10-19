# 全文阅读后端 Implementation Plan（PAP-84 ~ PAP-87）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** catalog 用 PubMed updatefiles 增量同步让文献记录跟上 PubMed，再从 PMC Cloud Service 抓取开放获取全文（XML / 图片 / PDF）、解析成 Patra 正文结构入库，并给门户提供详情四字段、全文、PDF、图片四个读接口。

**Architecture:** 全部在 `patra-catalog`。增量同步是一个新的 Spring Batch Job，与 baseline 导入共用 Reader 的下载 / 解析和 Processor；写入按 PMID 分流到"覆盖已有"与"新建"。抓取与解析是两个 Runner / Worker / Persister 任务，靠 `cat_publication_fulltext` 的状态衔接，三个 XXL-Job 用父子任务串起来、PG advisory lock 防并发。读端沿用 Controller → QueryService → ReadPort → ReadAdapter（native SQL）链路，PDF / 图片用 `InputStreamResource` 从 MinIO 边读边写回。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Spring Batch、Spring Data JPA + native SQL、PostgreSQL 17、MinIO（`patra-tool-object-storage`）、XXL-Job、JUnit 5 + AssertJ + Mockito、Testcontainers（PG / MinIO）、WireMock 3。

**Spec:** `docs/patra/specs/2026-10-19-fulltext-reader-backend-design.md`（下称 spec，已批准；附录 B 第 2 条收入了设计简报附录 A 的五条补充：`pdfSizeBytes`、`NO_BODY` 的署名、图片宽高与 `media` 清单、TeX 外壳、字数口径）。设计简报：`patra-portal/docs/design-briefs/v0.11/fulltext-reader.md`（附录 A 是给后端的要求）。Release spec：`docs/patra/release-specs/v0.11-fulltext-reader.md`。本 plan 引用 spec 的节号，表结构、状态机、覆盖规则、正文结构、接口形状以 spec 为准。

## Global Constraints

- 分支 `feat/v0.11-fulltext-api`，在主检出上做，不建 worktree。路径缩写：`CAT` = `patra-api/patra-catalog`，`DOM` / `APP` / `INFRA` / `ADP` = `CAT/patra-catalog-{domain,app,infra,adapter}/src/main/java/dev/patra/catalog/{domain,app,infra,adapter}`，`BOOT` = `CAT/patra-catalog-boot/src/main/java/dev/patra/catalog`（boot 的配置类在 `dev/patra/catalog/config/`，没有 `boot` 这一层包）；测试路径同理换 `src/test` / `src/integrationTest` / `src/e2eTest`。
- 代码规范：Google Java 风格；所有方法写 `///` Markdown Javadoc（长链接独占一行、100 列内）；禁止全限定名；record ≤ 4 参数用 `of()`、≥ 5 参数用 `@Builder`；record 的集合字段在紧凑构造器里 `List.copyOf` / `Map.copyOf`（SpotBugs `EI_EXPOSE_REP`）；空集合 `List.of()`；JSON 只注入 `JsonMapper`（`tools.jackson.databind.json.JsonMapper`），不写 `ObjectMapper`。
- 命名与位置按 `.claude/rules/tech/port-service.md`：Repository 在 `domain/port/repository`、实现 `…RepositoryAdapter` 在 `infra/adapter/persistence`；ReadPort 在 `domain/port/read`、实现 `…ReadAdapter` 在 `infra/adapter/read`；驱动端口 `…Port` 在 `domain/port/<function>`、实现 `…Adapter` 在 `infra/adapter/<function>`。
- 异常：领域异常继承 `CatalogException`（带 `StandardErrorTrait`），由 `DefaultErrorResolutionEngine` 映射；外部调用的"不存在"与"出错"必须区分，不许吞异常返回空。
- 新实体：子表继承 `ValueObjectJpaEntity`，ID 手工 `SnowflakeIdGenerator.getId()`；JSONB 字段 `@JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")` 直接映射 domain record；Dao 是 `JpaRepository` 接口，写操作 `@Modifying @Query`，native SQL 只在需要时用。
- 配置：`@ConfigurationProperties` record + `@DefaultValue`，每个配置类配一个 `@Configuration @EnableConfigurationProperties(X.class)` 空类注册（infra 的配置在 `BOOT/config/CatalogStorageConfiguration` 同处注册）；不涉及密钥的默认值都在 record 里，只有 mini 的环境差异进 `patra-infra/nacos/dev/patra-catalog.yaml`。
- 测试：方法名 snake_case（`should_…` / `…_when_…`）；单元 `{类}Test`、集成 `{类}IT`、端到端 `{场景}E2E`；时间一律注入 `Clock`；禁止反射测私有方法、禁止为测试加 setter。
- 验证：每个任务结束跑 `./gradlew spotlessApply` 再跑该任务列出的测试；任务涉及 `integrationTest` 的单独跑（`check` 不含 IT）；`check` 全仓耗时长，用后台运行。新建 `src/integrationTest` 目录或改模块依赖后重跑 `./gradlew dumpModuleGraph` 并提交 `patra-infra/cd/module-graph.json`。改了 `*.gradle.kts` 或 `gradle/libs.versions.toml` 提醒用户在 IDEA 点「加载 Gradle 更改」。Mockito 不写嵌套 stub（`when(a.x(b.y()))`），先把内层结果存局部变量。
- 提交：subject 中文动词起头（commitlint 的 subject-case 会拒绝以大写拉丁字母开头的 subject，如 `PMC …`、`JATS …`）、带 `type(scope):` 前缀与 Issue 号，例如 `feat(catalog): 解析 PMC 放开日期 (PAP-84)`；body 每行 ≤ 100 字符；只 `git add` 本任务的路径（共享工作区里还有设计会话的 `patra-portal/docs/design-briefs/v0.11/`，不要碰）；不推送、不开 PR。
- 测试文件按任务顺序落地：一个任务不写下一个任务的测试文件。

## Review Focus

1. **覆盖时冲掉别的流程的数据**：已有文献带 `is_oa = true`、`citation_count = 42`、`has_full_text = true`、一条 PMC OA 位置、一条 `source != 'PubMed'` 的标识符和全文两表记录，被 PubMed 修订覆盖后这些全部保留、内部 ID 不变、子表没有旧行残留。→ Task 6 的 `PublicationSyncRepositoryAdapterIT.should_keep_fields_owned_by_other_flows_when_overwriting`。
2. **`EXISTING_ONLY` 下给库里没有的记录建了期刊实例**：mini 追 353 个文件时这会产生上千万个事务和垃圾行。→ Task 7 的 `PubmedUpdateRoutingProcessorTest.should_not_call_delegate_when_absent_and_existing_only`。
3. **同一 PMID 在一个文件里出现两次、或软删后又出现**：前者以后出现的为准，后者复活并覆盖。→ Task 7 的 `PublicationSyncItemWriterTest.should_keep_last_occurrence_when_duplicate_pmid_in_chunk` 与 Task 6 的 `should_revive_soft_deleted_publication_when_overwriting`。
4. **抓取任务的每小时兜底与父子任务同时跑**：第二个实例必须直接返回，不能并发写同一篇。→ Task 9 的 `PgAdvisoryRunLockAdapterIT.should_skip_when_lock_held_by_another_session`。
5. **PDF 开始写出后才发现出错**：异常在首字节之后无法再渲染成 ProblemDetail；不可下载、对象缺失都必须在返回响应前判定为 404。→ Task 24 的 `PortalFullTextFileControllerIT.should_return_404_before_streaming_when_pdf_missing`。

另有一条与 Review Focus 同等重要、由设计简报带出的：**正文图片多为 `.webp`**，下载过滤、尺寸读取、`Content-Type` 三处都要认 webp。→ Task 13 `should_parse_metadata_json`、Task 14 `should_read_dimensions_of_jpg_png_and_webp`、Task 24 `should_stream_figure_with_immutable_cache`（用 `.webp` 文件名断言 `image/webp`）。

---

## 任务总览与顺序

```
A  PAP-84 增量同步      Task 1 – 10
B  PAP-85 全文抓取      Task 11 – 17
C  PAP-86 JATS 解析     Task 18 – 21
D  PAP-87 门户读端点    Task 22 – 24
E  收尾                 Task 25
```

严格串行。B 依赖 A 的 V8 迁移（Task 4）与 PMC 放开日期（Task 1）；C 依赖 B 的全文表实体与存储端口；D 依赖 B、C。

---

## A · PAP-84 PubMed updatefiles 增量同步

### Task 1: 解析 PMC 放开日期

spec 3.4 / 4.6。PubMed 记录 `<PubmedData><History><PubMedPubDate PubStatus="pmc-release">` 现在被 `skipElement` 跳过。

**Files:**
- Modify: `patra-api/patra-common/src/main/java/dev/patra/common/CanonicalPublication.java`（内嵌 `PublicationDates` 加 `LocalDate pmcRelease`）
- Modify: `DOM/model/enums/PublicationDateType.java`（加 `PMC_RELEASE("pmcrelease", "PMC 放开日期")`）
- Modify: `INFRA/parser/strategy/CanonicalPublicationParsingStrategy.java`（`parsePubmedData` 识别 `History`；`ParsedFields` 加 `pmcRelease`；`buildDates` 写入）
- Modify: `INFRA/parser/PubmedXmlElements.java`（加 `HISTORY`、`PUBMED_PUB_DATE`、属性 `PUB_STATUS`、值 `pmc-release`）
- Modify: `INFRA/batch/publication/PubmedArticleItemProcessor.java`（`buildPublicationDateData` 把 `pmcRelease` 映射为 `PMC_RELEASE`，`isPrimary = false`）
- Test: `CAT/patra-catalog-infra/src/test/java/dev/patra/catalog/infra/parser/parser/strategy/CanonicalPublicationParsingStrategyTest.java`、`…/infra/batch/publication/PubmedArticleItemProcessorTest.java`

**Interfaces:**
- Produces: `PublicationDateType.PMC_RELEASE`（Task 15 的候选查询从 `cat_publication_date` 按它读放开日期）；`CanonicalPublication.PublicationDates#getPmcRelease(): LocalDate`。

- [ ] **Step 1: 写失败测试**

```java
@Test
void should_parse_pmc_release_date_from_history() {
  // 文本块 XML：<PubmedData><History>
  //   <PubMedPubDate PubStatus="pubmed"><Year>2026</Year><Month>1</Month><Day>2</Day></PubMedPubDate>
  //   <PubMedPubDate PubStatus="pmc-release"><Year>2027</Year><Month>01</Month><Day>15</Day></PubMedPubDate>
  // </History>…</PubmedData>
  CanonicalPublication p = parse(xml); // 照本测试类已有写法：createReaderAtStartElement + strategy.parseRecord（:2257 附近）
  assertThat(p.getDates().getPmcRelease()).isEqualTo(LocalDate.of(2027, 1, 15));
}

@Test
void should_leave_pmc_release_null_when_absent() { … assertThat(p.getDates().getPmcRelease()).isNull(); }
```

`PubmedArticleItemProcessorTest` 加 `should_map_pmc_release_to_date_type_pmc_release`：断言结果 `dates()` 里有一条 `dateType = "pmcrelease"`、`isPrimary = false`。`PublicationImportResultMapperTest` 的 `parsePublicationDateType` 参数化用例（`@CsvSource`，:517-524 附近）加一行 `"pmcrelease, PMC_RELEASE"`。

- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-api:patra-catalog:patra-catalog-infra:test --tests "*CanonicalPublicationParsingStrategyTest*" --tests "*PubmedArticleItemProcessorTest*"`，预期编译失败或断言失败。
- [ ] **Step 3: 实现**：`parsePubmedData` 遇到 `History` 时逐个读 `PubMedPubDate`，只取 `PubStatus="pmc-release"`，年月日缺省补 1；其余 `PubStatus` 继续忽略（不扩大范围）。
- [ ] **Step 4: 跑测试确认通过**（同上命令，加 `--tests "*PublicationImportResultMapperTest*"`）。
- [ ] **Step 5: 提交** — `feat(catalog): 解析 PubMed 记录里的 PMC 放开日期 (PAP-84)`

### Task 2: 识别 `<DeleteCitation>` 的更新文件解析

spec 4.2。updatefiles 末尾的 `<DeleteCitation><PMID Version="1">…</PMID>…</DeleteCitation>` 要产出删除 PMID 列表。

**Files:**
- Modify: `DOM/port/parser/PubmedXmlParserPort.java`（加方法）
- Create: `INFRA/parser/strategy/PubmedUpdateParsingStrategy.java`
- Modify: `INFRA/parser/PubmedXmlParserAdapter.java`
- Modify: `INFRA/parser/PubmedXmlElements.java`（`DELETE_CITATION`）
- Test: `CAT/patra-catalog-infra/src/integrationTest/java/dev/patra/catalog/infra/parser/parser/PubmedXmlParserAdapterIT.java`（现有，纯 Java，无 Spring）

**Interfaces:**
- Produces: `Stream<CanonicalPublication> PubmedXmlParserPort#parseUpdateFile(InputStream in, Consumer<String> deletedPmidSink)` — 文章照常流出；每遇到一个 `DeleteCitation` 下的 `PMID` 文本就调用一次 sink。

- [ ] **Step 1: 写失败测试** `should_stream_articles_and_report_deleted_pmids`：XML 含 2 个 `PubmedArticle` 与一个含 `PMID 111`、`PMID 222` 的 `DeleteCitation`；断言流出 2 篇、sink 收到 `["111", "222"]`、`parse(InputStream)` 对同一 XML 仍只流出 2 篇且不受影响。
- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest --tests "*PubmedXmlParserAdapterIT*"`。
- [ ] **Step 3: 实现** `PubmedUpdateParsingStrategy implements RecordParsingStrategy<CanonicalPublication>`：构造参数 `Consumer<String> deletedPmidSink`；`matches` 接受 `PubmedArticle` 与 `DeleteCitation`；前者委托 `CanonicalPublicationParsingStrategy.INSTANCE.parseRecord`，后者读完内部所有 `PMID` 交给 sink 并返回 `null`（`AbstractRecordSpliterator` 对 null 继续找下一个）。`rootElementName()` 返回 `PubmedArticle`。
- [ ] **Step 4: 跑测试确认通过。**
- [ ] **Step 5: 提交** — `feat(catalog): 更新文件解析识别 DeleteCitation (PAP-84)`

### Task 3: 更新文件的引用、目录列表与配置

spec 4.2 / 8。

**Files:**
- Create: `DOM/model/vo/publication/PubmedUpdateFileRef.java`
- Create: `DOM/model/enums/PubmedUpdateScope.java`
- Create: `DOM/port/source/PubmedUpdateFileListingPort.java`
- Create: `INFRA/adapter/source/PubmedUpdateFileListingAdapter.java`
- Modify: `ADP/scheduler/config/PubmedDataSourceProperties.java`（删"暂不支持"注释；加 `filePrefix`、`updateScope`、`updateStartSeq`。它是可变 class 不是 record，照现有字段写法加带初始值的字段，不改成 record）
- Create: `BOOT/config/PubmedFtpConfiguration.java`（`RestClient pubmedFtpRestClient`）
- Modify: `patra-infra/nacos/dev/patra-catalog.yaml`（`patra.catalog.pubmed.update-scope: EXISTING_ONLY`）
- Test: `CAT/patra-catalog-domain/src/test/java/dev/patra/catalog/domain/model/vo/publication/PubmedUpdateFileRefTest.java`、`CAT/patra-catalog-infra/src/integrationTest/java/dev/patra/catalog/infra/adapter/source/PubmedUpdateFileListingAdapterIT.java`

**Interfaces:**
- Produces:
  - `record PubmedUpdateFileRef(String filePrefix, int seq)`：`static of(String, int)`；`String fileName()` → `"%s%04d.xml.gz"`；`String downloadUrl(String updateUrl)`（补尾部 `/`）；紧凑构造器校验 `filePrefix` 非空、`seq >= 1`。
  - `enum PubmedUpdateScope { EXISTING_ONLY, ALL }`。
  - `int PubmedUpdateFileListingPort#findLatestSeq(String updateUrl, String filePrefix)`：读目录 HTML 索引，取所有 `<prefix>NNNN.xml.gz` 的最大号；一个都没有抛 `PubmedUpdateListingException`（新建，`CatalogException`，`DEP_UNAVAILABLE`）；HTTP 出错同样抛，不返回 0。
  - `PubmedDataSourceProperties`：`String filePrefix = "pubmed26n"`；`PubmedUpdateScope updateScope = PubmedUpdateScope.ALL`；`int updateStartSeq = 1335`（进度表为空时的起点，2026 年 updatefiles 的第一个文件）。键 `patra.catalog.pubmed.file-prefix` / `update-scope` / `update-start-seq`。
- 与 spec 第 8 节的差异（我定的，实施时照此）：键名用 `update-scope` 而不是 `update.scope`，与同类已有的 `update-url` 平级；baseline 的 `PublicationImportParams`（`TOTAL_FILE_COUNT = 1334` 与 `pubmed26n` 模板）本版不改，年份切换不在本版范围。

- [ ] **Step 1: 写失败测试**：`PubmedUpdateFileRefTest.should_build_file_name_and_url`（`of("pubmed26n", 1335).fileName()` = `pubmed26n1335.xml.gz`；`downloadUrl("https://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles")` = `…/updatefiles/pubmed26n1335.xml.gz`）与 `should_reject_seq_below_one`；`PubmedUpdateFileListingAdapterIT`（`@WireMockTest`，RestClient 用 `HttpClient.Version.HTTP_1_1`）：`should_return_max_seq_from_index`（索引里有 `pubmed26n1335.xml.gz`、`pubmed26n1687.xml.gz`、`pubmed26n1687.xml.gz.md5`、`README.txt` → 1687）、`should_throw_when_no_matching_file`、`should_throw_when_index_returns_500`。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现**：Adapter 是 `@Component`，**显式构造器**上写 `@Qualifier("pubmedFtpRestClient") RestClient`（仓库没有 `lombok.config`，`@RequiredArgsConstructor` 不会把字段上的 `@Qualifier` 复制到构造参数）；正则 `Pattern.quote(prefix) + "(\\d{4})\\.xml\\.gz\""` 匹配 href。`PubmedFtpConfiguration` 照 `OpenAlexConfiguration` 写法（连接 5 秒、读取 30 秒、无 baseUrl，调用时传完整 URL）。
- [ ] **Step 4: 跑测试确认通过**（domain `test` + infra `integrationTest --tests "*PubmedUpdateFileListingAdapterIT*"`）。
- [ ] **Step 5: 提交** — `feat(catalog): 读取 updatefiles 最新文件号与同步范围配置 (PAP-84)`

### Task 4: V8 迁移与增量同步进度表

spec 3.1 / 3.2 / 3.3。一个 V8 建三张表（全文两表在 B 段才用实体）。

**Files:**
- Create: `CAT/patra-catalog-infra/src/main/resources/db/migration/V8__create_fulltext.sql`
- Create: `DOM/model/vo/publication/PubmedUpdateFileRecord.java`、`DOM/model/enums/PubmedUpdateFileStatus.java`
- Create: `DOM/port/repository/PubmedUpdateFileRepository.java`
- Create: `INFRA/persistence/entity/PubmedUpdateFileEntity.java`、`INFRA/persistence/dao/PubmedUpdateFileDao.java`、`INFRA/adapter/persistence/PubmedUpdateFileRepositoryAdapter.java`
- Test: `CAT/patra-catalog-infra/src/integrationTest/java/dev/patra/catalog/infra/adapter/persistence/PubmedUpdateFileRepositoryAdapterIT.java`

**DDL**（风格照 `V2` 的 `cat_publication_oa_location`：`-- ===` 块注释、列对齐、`TIMESTAMPTZ(6)`、审计列、`trg_<表>_updated_at` 调 `set_updated_at()`）：

```sql
CREATE TABLE cat_pubmed_update_file (
    id                    BIGINT         NOT NULL,
    file_name             VARCHAR(40)    NOT NULL,
    file_seq              INT            NOT NULL,
    status                VARCHAR(20)    NOT NULL,
    scope                 VARCHAR(20)    NOT NULL,
    created_count         INT            NOT NULL DEFAULT 0,
    updated_count         INT            NOT NULL DEFAULT 0,
    deleted_count         INT            NOT NULL DEFAULT 0,
    skipped_absent_count  INT            NOT NULL DEFAULT 0,
    skipped_invalid_count INT            NOT NULL DEFAULT 0,
    doi_conflict_count    INT            NOT NULL DEFAULT 0,
    skip_error_count      INT            NOT NULL DEFAULT 0,
    started_at            TIMESTAMPTZ(6) NULL,
    finished_at           TIMESTAMPTZ(6) NULL,
    last_error            VARCHAR(2000)  NULL,
    -- 审计列同 V2（record_remarks … updated_by_name）
    PRIMARY KEY (id),
    CONSTRAINT uk_pubmed_update_file_name UNIQUE (file_name)
);
CREATE INDEX idx_pubmed_update_file_seq ON cat_pubmed_update_file (file_seq);

CREATE TABLE cat_publication_fulltext (
    id                 BIGINT         NOT NULL,
    publication_id     BIGINT         NOT NULL,
    pmcid              VARCHAR(20)    NOT NULL,
    pmc_version        VARCHAR(30)    NULL,
    status             VARCHAR(20)    NOT NULL,
    unavailable_reason VARCHAR(30)    NULL,
    license_code       VARCHAR(30)    NULL,
    is_manuscript      BOOLEAN        NULL,
    object_prefix      VARCHAR(200)   NULL,
    xml_md5            CHAR(32)       NULL,
    pdf_object_key     VARCHAR(300)   NULL,
    pdf_size           BIGINT         NULL,
    pdf_md5            CHAR(32)       NULL,
    media              JSONB          NOT NULL DEFAULT '[]'::jsonb,
    fetched_at         TIMESTAMPTZ(6) NULL,
    parser_version     INT            NULL,
    parsed_at          TIMESTAMPTZ(6) NULL,
    word_count         INT            NULL,
    attempt_count      INT            NOT NULL DEFAULT 0,
    last_error         VARCHAR(2000)  NULL,
    next_attempt_at    TIMESTAMPTZ(6) NULL,
    -- 审计列同 V2
    PRIMARY KEY (id),
    CONSTRAINT uk_publication_fulltext_publication UNIQUE (publication_id)
);
CREATE INDEX idx_fulltext_status_next   ON cat_publication_fulltext (status, next_attempt_at);
CREATE INDEX idx_fulltext_status_parser ON cat_publication_fulltext (status, parser_version);
CREATE INDEX idx_fulltext_pmcid         ON cat_publication_fulltext (pmcid);

-- 正文表没有 id 列、不建 JPA 实体：写入用 Task 21 的 native upsert（INSERT … ON CONFLICT (publication_id) DO UPDATE），
-- 读取用 native 查询，所以不受"子表继承 ValueObjectJpaEntity"约束。
CREATE TABLE cat_publication_fulltext_body (
    publication_id BIGINT         NOT NULL,
    body           JSONB          NOT NULL,
    parser_version INT            NOT NULL,
    created_at     TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
    PRIMARY KEY (publication_id)
);
```

`skip_error_count` 是 spec 4.4"skip 要计数"的落点（spec 3.1 表里漏了这一列，我补的）。

**Interfaces:**
- Produces:
  - `enum PubmedUpdateFileStatus { RUNNING, COMPLETED, FAILED }`
  - `@Builder record PubmedUpdateFileRecord(String fileName, int fileSeq, PubmedUpdateFileStatus status, PubmedUpdateScope scope, PubmedUpdateFileCounts counts, Instant startedAt, Instant finishedAt, String lastError)`
  - `@Builder record PubmedUpdateFileCounts(int created, int updated, int deleted, int skippedAbsent, int skippedInvalid, int doiConflicts, int skipErrors)`，`static PubmedUpdateFileCounts empty()`
  - `PubmedUpdateFileRepository`：`OptionalInt findLastCompletedSeq()`；`Optional<PubmedUpdateFileRecord> findByFileName(String)`；`void markRunning(PubmedUpdateFileRef ref, PubmedUpdateScope scope, Instant startedAt)`（已有行则覆盖为 RUNNING、清空计数与错误）；`void markCompleted(String fileName, PubmedUpdateFileCounts counts, Instant finishedAt)`；`void markFailed(String fileName, String error, Instant finishedAt)`（`error` 截断到 2000）

- [ ] **Step 1: 写失败测试** `PubmedUpdateFileRepositoryAdapterIT`（注解组合照 `VenueEnrichmentReadAdapterIT`：`@DataJpaTest` + PG 初始化器 + `@AutoConfigureTestDatabase(replace = NONE)` + `@Import({PubmedUpdateFileRepositoryAdapter.class, JpaAuditingConfiguration.class})` + `@ActiveProfiles("test")`）：
  - `should_return_last_completed_seq`：1335 COMPLETED、1336 COMPLETED、1337 FAILED → `findLastCompletedSeq()` = 1336。
  - `should_return_empty_when_no_completed_file`。
  - `should_reset_counts_when_rerunning_failed_file`：1337 FAILED 后 `markRunning` → 状态 RUNNING、`last_error` 为 null。
- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest --tests "*PubmedUpdateFileRepositoryAdapterIT*"`。
- [ ] **Step 3: 实现** 迁移、实体（`PubmedUpdateFileEntity extends ValueObjectJpaEntity`）、Dao、Adapter。Adapter 的三个写方法加 `@Transactional`：调用方 Task 10 的 Handler 不开事务（它要在事务外启动 Spring Batch 作业），`@Modifying` 查询没有事务会直接报错，而 IT 自带的测试事务会掩盖这一点——IT 类上加 `@Transactional(propagation = NOT_SUPPORTED)` 让它暴露出来，测试间用 `@Sql` 或 `@AfterEach` 清表。
- [ ] **Step 4: 跑测试确认通过；** 再跑 `PublicationRepositoryAdapterIT` 确认 V8 不影响现有迁移。
- [ ] **Step 5: 提交** — `feat(catalog): 新增增量同步进度表与全文两表迁移 (PAP-84)`

### Task 5: 子表按文献批量删除

spec 4.3。`type`、`keyword`、`funding`、`suppl_mesh`、`mesh_heading` 只有单条删除，`mesh_qualifier` 只能按 heading 删；标识符要只删 `source = 'PubMed'`。

**Files:**
- Modify: `INFRA/persistence/dao/{PublicationTypeDao,PublicationKeywordDao,PublicationFundingDao,PublicationSupplMeshDao,PublicationMeshHeadingDao,PublicationMeshQualifierDao,PublicationIdentifierDao}.java`
- Test: `CAT/patra-catalog-infra/src/integrationTest/java/dev/patra/catalog/infra/persistence/dao/PublicationChildBatchDeleteIT.java`

**Interfaces:**
- Produces（全部 `@Modifying @Query`，参数 `@Param("publicationIds") Collection<Long>`，返回删除行数 `int`）：
  - `PublicationTypeDao` / `PublicationKeywordDao` / `PublicationFundingDao` / `PublicationSupplMeshDao` / `PublicationMeshHeadingDao`：`deleteByPublicationIdIn`
  - `PublicationMeshQualifierDao#deleteByPublicationIdIn`：native，`DELETE FROM cat_publication_mesh_qualifier q USING cat_publication_mesh_heading h WHERE q.publication_mesh_heading_id = h.id AND h.publication_id IN (:publicationIds)`（表结构分散在多个迁移：mesh 在 `V5__create_mesh.sql`（qualifier 外键列见 :246），keyword 在 V6，funding 在 V7，investigator / personal_name 在 V4，其余在 V2）
  - `PublicationIdentifierDao#deleteByPublicationIdInAndSource(Collection<Long> publicationIds, String source)`

- [ ] **Step 1: 写失败测试** `PublicationChildBatchDeleteIT`（注解组合照 `infra/persistence/dao/AuthorOrcidDaoIT`：`@DataJpaTest` + `CatalogITPostgreSQLContainerInitializer` + `@AutoConfigureTestDatabase(replace = NONE)` + `@Import(JpaAuditingConfiguration.class)`）：两篇文献各写入 type / keyword 关联 / funding / suppl_mesh / mesh_heading（带 qualifier）/ 两条标识符（`source` 分别为 `PubMed`、`PMC`）；对第一篇调用各删除方法，断言第一篇这些行为 0、第二篇不变、第一篇 `source = 'PMC'` 的标识符保留、`cat_keyword` 主表行保留。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现** 各 Dao 方法。
- [ ] **Step 4: 跑测试确认通过。**
- [ ] **Step 5: 提交** — `feat(catalog): 文献子表按文献批量删除 (PAP-84)`

### Task 6: 覆盖更新的仓储

spec 4.3 / 4.4。新端口 `PublicationSyncRepository`，覆盖规则全部落在这里。

**Files:**
- Create: `DOM/port/repository/PublicationSyncRepository.java`、`DOM/model/vo/publication/PublicationSyncTarget.java`
- Create: `INFRA/adapter/persistence/PublicationSyncRepositoryAdapter.java`
- Modify: `INFRA/adapter/persistence/PublicationRepositoryAdapter.java`（把 `insertAllWithAssociations` 里 `writeMetadataAssociations` 之后的 12 个 `writeXxxAssociations` 抽成 `public void writeImportOwnedAssociations(List<PublicationCompleteData> data)`，`insertAllWithAssociations` 改为调用它；行为不变）
- Modify: 主表 Dao（`PublicationRepositoryAdapter` 里 `jpaRepository` 字段的类型）加两条 native 方法：按 PMID 查含软删除的行、按 ID 复活
- Test: `CAT/patra-catalog-infra/src/integrationTest/java/dev/patra/catalog/infra/adapter/persistence/PublicationSyncRepositoryAdapterIT.java`

**Interfaces:**
- Produces:
  - `record PublicationSyncTarget(long publicationId, String pmid, String doi, boolean deleted)`，`static of(…)`
  - `PublicationSyncRepository`：
    - `Map<String, PublicationSyncTarget> findSyncTargetsByPmids(Collection<String> pmids)` — 包含已软删除的行（native，绕过 `@SoftDelete`）；key 是 PMID。
    - `Map<String, Long> findPublicationIdsByDois(Collection<String> dois)` — native，按 trim 后的原值精确匹配，**包含软删除行**：`uk_doi`（`V2__create_publication.sql:53`）不含 `deleted_at` 且区分大小写，库里 DOI 原样存放，所以冲突判定必须与唯一约束同口径，不能转小写、不能排除已删除行。key 为库里的 DOI 原值。
    - `void overwriteAllWithAssociations(List<PublicationCompleteData> data, Map<Long, String> doiOverrides, Instant syncedAt)` — 前置条件：每条 `data.getPublicationId()` 已是库里存在的 ID。`doiOverrides` 是 publicationId → 要写入的 DOI（DOI 冲突时调用方传旧值；`PublicationAggregate.doi` 是 `private final`，不在聚合上改）。
    - `int softDeleteByPmids(Collection<String> pmids, Instant deletedAt)` — 已删除的不重复计数；内部按 5000 一批分片执行（PostgreSQL 绑定参数上限 65535）。`findSyncTargetsByPmids` / `findPublicationIdsByDois` 同样分片。
- `overwriteAllWithAssociations` 的固定顺序（一个事务，由调用方的 chunk 事务承载）：
  1. native `UPDATE cat_publication SET deleted_at = NULL WHERE id IN (:ids) AND deleted_at IS NOT NULL`（复活）。
  2. 按 spec 4.3 删除顺序批量删子表：`mesh_qualifier → mesh_heading`、`abstract`、`alternative_abstract`、`date`、`type`、`suppl_mesh`、`keyword`、`funding`、`investigator`、`personal_name_subject`、`author_affiliation → author`、标识符只删 `source = 'PubMed'`；然后 `entityManager.flush()`。
  3. 主表：主表 Dao 上一条 `@Modifying` native UPDATE，**只列出** spec 4.3 表格"更新"列的字段（含 `last_synced_at = :syncedAt`；DOI 取 `doiOverrides` 里的值，没有则取新值），逐条执行。不用"取托管实体改字段"：Hibernate 按载入时的值写回整行，与本同步并发运行的抓取 / 解析任务（另两把锁）在载入到 flush 之间写入的 `is_oa` / `has_full_text` 会被覆盖回旧值。
  4. metadata：同样用显式列名的 `@Modifying` native UPDATE，只改 `indexing_status`、`indexing_method`、`owner`、`citation_subset`、`data_source`、`import_batch`、`import_date`；UPDATE 影响 0 行（历史数据没有 metadata 行）时按现有插入写法新建一行。
  5. `flush()` 后调用 `publicationRepositoryAdapter.writeImportOwnedAssociations(data)`。

- [ ] **Step 1: 写失败测试** `PublicationSyncRepositoryAdapterIT`（`@DataJpaTest` 组合，插入后与每次 native SQL 改数之后都 `em.flush(); em.clear()`（照 `PublicationAuthorRoundTripIT:101`），否则托管实体会掩盖或冲掉 native 写入；`@Import` 带 `PublicationRepositoryAdapter`、`PublicationSyncRepositoryAdapter`、`PublicationJpaMapper` 所在转换包的 `@ComponentScan`、`JsonColumnCodec`、`JacksonAutoConfiguration`、`JpaAuditingConfiguration`；数据经 `PublicationRepositoryAdapter.insertAllWithAssociations` 写入，与 `PublicationAuthorRoundTripIT` 同法）：
  - `should_keep_fields_owned_by_other_flows_when_overwriting`：先插一篇（标题 A、作者 X、MeSH M1），再用 native SQL 设 `is_oa = true`、`citation_count = 42`、`metadata.has_full_text = true`、`full_text_url = 'https://x'`，插一条 PMC OA 位置、一条 `source = 'PMC'` 的标识符、一行 `cat_publication_fulltext` 与一行 `_body`；用同 ID、标题 B、作者 Y、MeSH M2 的数据覆盖。断言：标题 B；作者只有 Y；MeSH 只有 M2；`is_oa = true`、`citation_count = 42`、`has_full_text = true`、`full_text_url = 'https://x'`；OA 位置、`PMC` 标识符、全文两行都在；`cat_publication` 的 `id` 不变且总行数不变；`last_synced_at = syncedAt`。
  - `should_revive_soft_deleted_publication_when_overwriting`：先 `softDeleteByPmids`，再覆盖 → `deleted_at IS NULL`、内容为新值。
  - `should_find_soft_deleted_target_by_pmid`：`findSyncTargetsByPmids` 返回 `deleted = true`。
  - `should_not_count_already_deleted_when_soft_deleting`：同一 PMID 删两次，第二次返回 0。
  - `should_find_doi_owner_including_soft_deleted_and_exact_case`：一篇未删除、DOI `10.1/ABC`，一篇已软删除、DOI `10.1/x` → `findPublicationIdsByDois(["10.1/ABC", "10.1/x"])` 两个都返回；查 `10.1/abc` 返回空。
  - `should_apply_doi_override_when_overwriting`：传 `doiOverrides = {id: "旧 DOI"}` → 主表 DOI 是旧值。
- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest --tests "*PublicationSyncRepositoryAdapterIT*"`。
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过；** 再跑 `PublicationRepositoryAdapterIT`、`PublicationAbstractRoundTripIT`、`PublicationAuthorRoundTripIT` 确认抽方法没改变插入行为。
- [ ] **Step 5: 提交** — `feat(catalog): 文献覆盖更新仓储，保留其他流程写入的字段 (PAP-84)`

### Task 7: 分流处理器与同步写入器

spec 4.1 / 4.3 / 4.4。

**Files:**
- Create: `INFRA/batch/publication/update/PublicationSyncItem.java`
- Create: `INFRA/batch/publication/update/PubmedUpdateRoutingProcessor.java`
- Create: `INFRA/batch/publication/update/PublicationSyncItemWriter.java`
- Create: `INFRA/batch/publication/update/PubmedUpdateCounters.java`
- Test: `CAT/patra-catalog-infra/src/test/java/dev/patra/catalog/infra/batch/publication/update/{PubmedUpdateRoutingProcessorTest,PublicationSyncItemWriterTest}.java`

**Interfaces:**
- Consumes: Task 6 的 `PublicationSyncRepository`；现有 `PubmedArticleItemProcessor`、`PublicationImportResultMapper`、`PublicationRepository#insertAllWithAssociations`。
- Produces:
  - `record PublicationSyncItem(PublicationImportResult result, PublicationSyncTarget target)`，`target` 为 null 表示新建；`static of(…)`。
  - `PubmedUpdateCounters`（普通类，Task 8 的 JobConfig 把它注册为 `@StepScope` bean，同一 Step 的处理器、写入器、SkipListener 共用一个实例）：`private final AtomicInteger` 字段，只通过方法访问——`addCreated(int)`、`addUpdated(int)`、`incrementSkippedAbsent()`、`incrementSkippedInvalid()`、`addDoiConflicts(int)`、`incrementSkipErrors()`、`PubmedUpdateFileCounts snapshot(int deleted)`。`@StepScope` 注入的是 CGLIB 代理，直接读写字段拿到的是代理自己未初始化的字段，必须走方法。
  - `PubmedUpdateRoutingProcessor implements ItemProcessor<CanonicalPublication, PublicationSyncItem>`：构造 `(PublicationSyncRepository, PubmedArticleItemProcessor delegate, PubmedUpdateScope scope, PubmedUpdateCounters counters)`。逐条按 PMID 查 `findSyncTargetsByPmids(Set.of(pmid))`；`EXISTING_ONLY` 且查不到 → `counters.skippedAbsent++`、返回 null，**不调用 delegate**；否则调 delegate，delegate 返回 null → `skippedInvalid++`、返回 null；否则返回 `PublicationSyncItem.of(result, target)`。
  - `PublicationSyncItemWriter implements ItemWriter<PublicationSyncItem>`：构造 `(PublicationSyncRepository, PublicationItemWriter insertWriter, PublicationImportResultMapper, PubmedUpdateCounters, Clock)`。`write` 的顺序：
    1. chunk 内按 PMID 去重，**后出现的为准**。
    2. 有 `target` 的：`aggregate.assignId(PublicationId.of(target.publicationId()))`；新 DOI（trim 后原值）若在 `findPublicationIdsByDois` 里属于另一个 ID → `doiOverrides.put(target.publicationId(), target.doi())`、本地冲突数 +1、log warn；然后 `overwriteAllWithAssociations(list, doiOverrides, clock.instant())`。
    3. 没有 `target` 的（只在 `ALL` 下出现）：委托注入的现有 `PublicationItemWriter`：`insertWriter.write(new Chunk<>(results))`（它的 DOI 去重是 private，不复制）；新建数取它实际插入的条数（若它不返回条数，按"写入前后 `findSyncTargetsByPmids` 命中数之差"计，实施时选其一并写进 Javadoc）。
    4. **计数最后才加**：所有 repository 调用都成功之后，才 `counters.addUpdated(…)` / `addCreated(…)` / `addDoiConflicts(…)`。Spring Batch 6 写入失败会回滚并逐条重写（`ChunkOrientedStep` 的 scan），中途累加会重复计数。

- [ ] **Step 1: 写失败测试**
  - `PubmedUpdateRoutingProcessorTest`：`should_not_call_delegate_when_absent_and_existing_only`（`verifyNoInteractions(delegate)`、`skippedAbsent = 1`）；`should_create_when_absent_and_scope_all`（返回 item 的 `target` 为 null）；`should_carry_target_when_present`；`should_count_invalid_when_delegate_returns_null`。
  - `PublicationSyncItemWriterTest`：`should_keep_last_occurrence_when_duplicate_pmid_in_chunk`（同 PMID 两条，标题 A 后 B → 覆盖的 data 标题为 B，只有一条）；`should_keep_old_doi_when_new_doi_belongs_to_another_publication`（`doiConflicts = 1`，覆盖 data 的 DOI = 旧值）；`should_assign_existing_id_before_overwrite`；`should_insert_when_no_target`；`should_not_count_when_overwrite_throws`（`overwriteAllWithAssociations` 抛异常 → counters 全为 0）。
- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-api:patra-catalog:patra-catalog-infra:test --tests "*PubmedUpdateRoutingProcessorTest*" --tests "*PublicationSyncItemWriterTest*"`。
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过。**
- [ ] **Step 5: 提交** — `feat(catalog): 增量同步的分流处理器与覆盖写入器 (PAP-84)`

### Task 8: 增量同步 Job

spec 4.1 / 4.2 / 4.4。

**Files:**
- Create: `INFRA/batch/publication/update/PubmedUpdateItemReader.java`
- Create: `INFRA/batch/publication/update/PubmedDeleteCitationTasklet.java`
- Create: `INFRA/batch/publication/update/PubmedUpdateJobConfig.java`
- Modify: `DOM/port/batch/PublicationBatchPort.java`、`INFRA/batch/publication/PublicationBatchAdapter.java`
- Create: `DOM/model/vo/publication/PubmedUpdateImportParams.java`
- Test: `CAT/patra-catalog-infra/src/test/java/dev/patra/catalog/infra/batch/publication/update/PubmedUpdateItemReaderTest.java`、`…/PublicationBatchAdapterTest.java`（现有，补用例）

**Interfaces:**
- Consumes: Task 2 `parseUpdateFile`；Task 3 `PubmedUpdateFileRef`；Task 6 `softDeleteByPmids`；Task 7 处理器、写入器、计数器。
- Produces:
  - `record PubmedUpdateImportParams(PubmedUpdateFileRef file, String updateUrl, PubmedUpdateScope scope, String generation)`，`static of(…)`（4 个参数，按规范用 `of()` 不用 `@Builder`）。
  - `PubmedUpdateFileCounts PublicationBatchPort#launchUpdateImport(PubmedUpdateImportParams params)` — 同步执行，失败抛 `IllegalStateException`（与 `launchBaselineImport` 一致的 `failFastWhenExecutionFailed`），成功返回从 JobExecution 的 ExecutionContext 读出的计数。用 `jobOperatorHelper.launch(job, params, false)`（三参重载，不加时间戳）：同参数的 JobInstance 失败后再跑会从断点续传。启动前先用 `JobRepository.getLastJobExecution(jobName, params)` 查：已 COMPLETED 则不再启动、直接读它的计数返回——覆盖"作业已完成、但进度表 `markCompleted` 前进程中断"的情况，否则每天都会撞 `JobInstanceAlreadyCompleteException`。
  - Job `pubmedUpdateImportJob`：Step `pubmedUpdateArticleStep`（chunk，`<CanonicalPublication, PublicationSyncItem>`，chunk 大小同 baseline 的 `getChunkSize()` 约束，`faultTolerant().skipLimit(1000).skip(DataIntegrityViolationException).skip(DuplicateKeyException)`，`SkipListener` 每次 skip `skipErrors++` 并 log error）→ Step `pubmedDeleteCitationStep`（Tasklet）。
  - JobParameters（identifying）：`downloadUrl`、`importBatch`（`update-<文件名去掉 .xml.gz>`，与 baseline 去后缀的写法一致，如 `update-pubmed26n1335`）、`scope`、`generation`（可空）。chunk 大小：baseline 的 `getChunkSize()` 是 private，把它的约束抽成两边共用的包级静态方法。
  - `PubmedUpdateItemReader`：照 `PubmedArticleItemReader` 写，**不继承它**（baseline 的 Step 按类型注入 Reader，继承后会出现两个候选）（下载到临时文件、`GZIPInputStream`、断点 key `pubmed.update.current.index`），改调 `parseUpdateFile`，sink 收集到 `List<String>`；`update(ExecutionContext)` 时把已收集的删除 PMID 写入 key `pubmed.update.deleted.pmids`（`ArrayList<String>`）。
  - ExecutionContext 里只放 `java.*` 类型：默认的 `DefaultExecutionContextSerializer` 写入要求 `Serializable`、读回只放行 `java.*` 与 `org.springframework.batch.**`，domain record 写不进也读不回。计数按 key 分开存 `Integer`：`pubmed.update.count.created` / `.updated` / `.skipped-absent` / `.skipped-invalid` / `.doi-conflicts` / `.skip-errors` / `.deleted`；删除 PMID 存 `ArrayList<String>`。
  - Step 1 的计数监听器在 `afterStep` 里**直接写 `stepExecution.getJobExecution().getExecutionContext()`**，不经 `ExecutionContextPromotionListener`（`afterStep` 按注册逆序调用，经提升很容易顺序反掉）；删除 PMID 由 Reader 写在 Step 上下文，用 `ExecutionContextPromotionListener`（keys = `pubmed.update.deleted.pmids`）提升。Tasklet 从 JobExecution 读出 PMID 调 `softDeleteByPmids`，把 `.deleted` 写回 JobExecution 上下文。`launchUpdateImport` 从这些 key 组装 `PubmedUpdateFileCounts`。

- [ ] **Step 1: 写失败测试**：`PubmedUpdateItemReaderTest.should_store_deleted_pmids_in_execution_context`（mock `FileDownloadPort` 返回一个临时 `.gz`、mock 解析端口回调 sink；读完后 `update(ctx)` → ctx 里有 `["111","222"]`）；`PublicationBatchAdapterTest.should_launch_update_job_with_identifying_params`（断言调用的是 `launch(job, params, false)`，参数含 `downloadUrl`、`importBatch = "update-pubmed26n1335"`、`scope = "EXISTING_ONLY"`）；`should_return_counts_of_completed_execution_without_relaunch`（`getLastJobExecution` 返回 COMPLETED 且上下文里有计数 → 不调用 `launch`、返回这些计数）。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过；** 再跑 `PubmedBaselineJobConfigTest` 确认 baseline 不受影响。
- [ ] **Step 5: 提交** — `feat(catalog): 增量同步 Spring Batch 作业 (PAP-84)`

### Task 9: 任务互斥锁

spec 2.2"防重复运行"。三个 XXL-Job 共用。

**Files:**
- Create: `DOM/port/lock/ExclusiveRunPort.java`
- Create: `INFRA/adapter/lock/PgAdvisoryRunLockAdapter.java`
- Test: `CAT/patra-catalog-infra/src/integrationTest/java/dev/patra/catalog/infra/adapter/lock/PgAdvisoryRunLockAdapterIT.java`

**Interfaces:**
- Produces: `<T> Optional<T> ExclusiveRunPort#runExclusively(String lockName, Supplier<T> work)` — 取到锁就执行并返回 `Optional.of(结果)`，取不到返回 `Optional.empty()`，`work` 抛出的异常原样抛出且锁已释放。实现：`JdbcTemplate.execute(ConnectionCallback)` 在同一连接上 `SELECT pg_try_advisory_lock(?)`（键 = `UUID.nameUUIDFromBytes(lockName.getBytes(UTF_8)).getMostSignificantBits()`，在 Java 端算好再传入）→ 执行 `work` → finally `pg_advisory_unlock(?)`。锁名常量：`catalog.pubmed-update-sync`、`catalog.fulltext-fetch`、`catalog.fulltext-parse`。

- [ ] **Step 1: 写失败测试** `PgAdvisoryRunLockAdapterIT`（`@JdbcTest` + `CatalogITPostgreSQLContainerInitializer` + `@AutoConfigureTestDatabase(replace = NONE)` + `@Import(PgAdvisoryRunLockAdapter.class)` + 类上 `@Transactional(propagation = NOT_SUPPORTED)`：测试事务会把连接绑在线程上，会话级 advisory lock 在同一会话里可重入，不关事务的话"没释放"也测不出来）：`should_run_when_lock_free`；`should_skip_when_lock_held_by_another_session`（从 `DataSource` 另取一个 `Connection` 手工 `pg_advisory_lock` 同一键，调用返回 empty、`work` 未执行）；`should_release_lock_when_work_throws`（`work` 抛异常后，从另一个 `Connection` 执行 `pg_try_advisory_lock` 同一键返回 true）。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过。**
- [ ] **Step 5: 提交** — `feat(catalog): 用 PG advisory lock 防定时任务重复运行 (PAP-84)`

### Task 10: 同步命令、XXL-Job 入口与端到端测试

spec 4.5 / 4.6 / 2.2。

**Files:**
- Create: `APP/usecase/publication/update/command/PubmedUpdateSyncCommand.java`、`APP/usecase/publication/update/PubmedUpdateSyncHandler.java`、`APP/usecase/publication/update/dto/PubmedUpdateSyncResult.java`
- Create: `ADP/scheduler/job/PubmedUpdateSyncScheduleJob.java`
- Create: `docs/patra/runbooks/v0.11-fulltext-runbook.md`（本任务写"增量同步"一节：handler `pubmedUpdateSyncJob`、cron `0 0 5 * * ?`、参数、子任务 ID 待 Task 17 建好后填）
- Create: `CAT/patra-catalog-boot/src/testFixtures/resources/pubmed/pubmed26n9000.xml.gz`（baseline 夹具：4 条原始记录）与 `pubmed26n9001.xml.gz`（3 条修订 + 1 条新增 + 1 个 `DeleteCitation`），由 Step 1 生成，源 XML 一并提交为同名 `.xml`
- Test: `APP` 单测 `PubmedUpdateSyncHandlerTest`；`ADP` 集成 `PubmedUpdateSyncScheduleJobIT`；`CAT/patra-catalog-boot/src/e2eTest/java/dev/patra/catalog/integration/publication/PubmedUpdateSyncE2E.java`

**Interfaces:**
- Consumes: Task 3 `findLatestSeq` 与配置；Task 4 进度仓储；Task 8 `launchUpdateImport`；Task 9 `runExclusively`。
- Produces:
  - `@Builder record PubmedUpdateSyncCommand(String updateUrl, String filePrefix, int startSeq, PubmedUpdateScope scope, Integer fromFile, Integer maxFiles) implements Command<PubmedUpdateSyncResult>`：`PubmedDataSourceProperties` 在 adapter 模块，app 层依赖不到，所以由 ScheduleJob 从配置补齐 `updateUrl`、`filePrefix`、`startSeq`（`update-start-seq`）与 `scope`（任务参数没给时取 `update-scope`），这四项必填；`fromFile`、`maxFiles` 可空。与 baseline 由 ScheduleJob 往命令里塞 URL 的做法一致（`PubmedBaselineImportScheduleJob.java:109`）。
  - `record PubmedUpdateSyncResult(int filesProcessed, Integer lastSeq, PubmedUpdateFileCounts total, boolean skippedByLock)`。
  - Handler 流程（Handler **不加** `@Transactional`：Spring Batch 不能在外层事务里启动；进度仓储的写方法自带事务，见 Task 4）：起点 = `fromFile` 或 `findLastCompletedSeq() + 1`（都没有时取命令里的 `startSeq`）；终点 = `findLatestSeq(updateUrl, filePrefix)`；按 `maxFiles` 截断；逐个文件 `markRunning` → `launchUpdateImport`（`fromFile` 指定且该文件已 COMPLETED 时 `generation = "replay-" + clock.millis()`）→ `markCompleted`；任一文件抛异常 → `markFailed` 后**停止**并抛 `ApplicationException(CatalogErrorCode.CAT_1501, …)`。整个循环包在 `runExclusively("catalog.pubmed-update-sync", …)` 里，拿不到锁返回 `skippedByLock = true`。
  - `@XxlJob("pubmedUpdateSyncJob")`：参数 `fromFile=N,maxFiles=M,scope=EXISTING_ONLY`（均可省，解析写法照 `PubmedBaselineImportScheduleJob.parseJobParam`，未知 / 重复 key 报错）；成功 `handleSuccess`（拿不到锁也算成功并在消息里写明），失败 `handleFail`。

- [ ] **Step 1: 生成夹具**：从 n1687 里挑 4 条同一期刊的 `PubmedArticle` 作为 baseline 夹具 9000；9001 里放其中 3 条改了标题的"修订"、1 条新 PMID 的"新增"（同一期刊），末尾加删除第 4 条的 `<DeleteCitation><PMID Version="1">…</PMID></DeleteCitation>`；分别 gzip。记下期刊的 ISSN / NLM ID 供 E2E 插 `cat_venue`。
- [ ] **Step 2: 写失败测试**
  - `PubmedUpdateSyncHandlerTest`：`should_start_from_last_completed_plus_one`；`should_stop_and_mark_failed_when_file_fails`（1336 失败 → 1337 不跑、1336 `markFailed`）；`should_replay_with_new_generation_when_from_file_already_completed`；`should_return_skipped_when_lock_held`；`should_cap_files_by_max_files`。
  - `PubmedUpdateSyncScheduleJobIT`（照 `MeshImportScheduleJobIT`：`mockStatic(XxlJobHelper)`；另 `@Import(PubmedDataSourceAutoConfiguration.class)` 让配置类可注入）：参数解析（含没给 `scope` 时取配置）、成功、失败、拿不到锁四种。
  - `PubmedUpdateSyncE2E`（照 `MeshScrImportE2E`：`@MockitoBean FileDownloadPort` 返回夹具副本、`@MockitoBean PubmedUpdateFileListingPort` 返回 9001、`patra.catalog.pubmed.file-prefix=pubmed26n`、`update-start-seq=9001`）：E2E 库没有种子数据，处理器匹配不到期刊就跳过（`PubmedArticleItemProcessor.java:139-147`），所以先用 SQL 插入夹具里期刊对应的 `cat_venue` 与其 ISSN / NLM 标识符行；再用 baseline 夹具 `pubmed26n9000.xml.gz`（4 条原始记录：3 条将被修订 + 1 条将被删除）经 baseline 写入路径插入；`FileDownloadPort` 的 mock 按 URI 末段返回对应夹具副本；跑 `scope=EXISTING_ONLY` → 3 条标题已更新、新增那条不存在、被删那条 `deleted_at` 非空、进度表 9001 COMPLETED 且计数 `updated=3, skippedAbsent=1, deleted=1`；再以 `fromFile=9001, scope=ALL` 重放 → 新增那条存在。
- [ ] **Step 3: 跑测试确认失败。**
- [ ] **Step 4: 实现** Command / Handler / ScheduleJob，写 runbook 增量同步一节。
- [ ] **Step 5: 跑测试确认通过** — app `test`、adapter `integrationTest --tests "*PubmedUpdateSyncScheduleJobIT*"`、`./gradlew :patra-api:patra-catalog:patra-catalog-boot:e2eTest --tests "*PubmedUpdateSyncE2E*"`。
- [ ] **Step 6: 提交** — `feat(catalog): 接入 PubMed 增量同步任务入口与端到端测试 (PAP-84)`

---

## B · PAP-85 PMC 全文抓取

### Task 11: 全文记录的领域模型与仓储

spec 3.2 / 3.4 / 3.5。

**Files:**
- Create: `DOM/model/enums/{FullTextStatus,FullTextUnavailableReason}.java`
- Create: `DOM/model/vo/fulltext/{PublicationFullText,FullTextMedia}.java`
- Create: `DOM/port/repository/PublicationFullTextRepository.java`
- Create: `INFRA/persistence/entity/PublicationFullTextEntity.java`、`INFRA/persistence/dao/PublicationFullTextDao.java`（正文表不建实体，见 Task 4 DDL 的注释与 Task 21）
- Create: `INFRA/adapter/persistence/PublicationFullTextRepositoryAdapter.java`
- Test: `CAT/patra-catalog-infra/src/integrationTest/java/dev/patra/catalog/infra/adapter/persistence/PublicationFullTextRepositoryAdapterIT.java`

**Interfaces:**
- Produces:
  - `enum FullTextStatus { FETCHED, FETCH_FAILED, UNAVAILABLE, PARSED, PARSE_FAILED }`
  - `enum FullTextUnavailableReason { NOT_IN_BUCKET, NOT_OPEN_ACCESS, RETRACTED, NO_BODY }`，`boolean isRecheckable()`（前两个为 true）
  - `record FullTextMedia(String name, Integer width, Integer height)`，`static of(…)`：已下载的一张正文图片，宽高为像素、读不出为 null（spec 3.2 `media` 列）。
  - `@Builder(toBuilder = true) record PublicationFullText(Long publicationId, String pmcid, String pmcVersion, FullTextStatus status, FullTextUnavailableReason unavailableReason, String licenseCode, Boolean manuscript, String objectPrefix, String xmlMd5, String pdfObjectKey, Long pdfSize, String pdfMd5, List<FullTextMedia> media, Instant fetchedAt, Integer parserVersion, Instant parsedAt, Integer wordCount, int attemptCount, String lastError, Instant nextAttemptAt)`（`media` 紧凑构造器 `List.copyOf`，null 视为空）；实体的 `media` 用 JSONB 直接映射 `List<FullTextMedia>`。
  - `PublicationFullTextRepository`：`Optional<PublicationFullText> findByPublicationId(long)`；`void save(PublicationFullText)`（按 `publication_id` upsert：有行则更新全部列，没有则插入；ID 用雪花）。正文写入 `saveBody` 在 Task 21 加（依赖 Task 18 的 `FullTextBody`）。`status`、`unavailable_reason` 用 `@Enumerated(EnumType.STRING)` 存枚举名（读端 SQL 按 `'PARSED'` 等比较）。

- [ ] **Step 1: 写失败测试** `PublicationFullTextRepositoryAdapterIT`（注解组合照 `PublicationAbstractRoundTripIT:76-88`：除 `@DataJpaTest` 一套外还要导入 `PatraHibernatePropertiesCustomizer` 与 `JacksonAutoConfiguration`，否则切片走 Jackson 2 的 FormatMapper，`media` JSONB 的格式与生产不同）：`should_insert_then_update_by_publication_id`（第二次 save 不新增行、字段为新值）；`should_round_trip_all_fields`（含两张图的 `media`，其中一张宽高为 null）；`should_default_media_to_empty_list`。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现**（`save` 先 `findByPublicationId` 取托管实体再改；并发写入由 Task 9 的锁与 `uk_publication_fulltext_publication` 兜底，撞唯一约束时抛出交给调用方按失败处理）。
- [ ] **Step 4: 跑测试确认通过。**
- [ ] **Step 5: 提交** — `feat(catalog): 全文记录领域模型与仓储 (PAP-85)`

### Task 12: 改写 `ofPmc` 与 PMC OA 位置的替换

spec 3.5。

**Files:**
- Modify: `DOM/model/vo/publication/PublicationOaLocation.java`（`ofPmc` 改签名）
- Modify: `DOM/port/repository/PublicationRepository.java`、`INFRA/adapter/persistence/PublicationRepositoryAdapter.java`（加方法）
- Modify: `INFRA/persistence/dao/PublicationOaLocationDao.java`（加 `@Modifying @Query("DELETE FROM PublicationOaLocationEntity e WHERE e.publicationId = :publicationId AND e.locationType = :locationType")` 的批量删除；不用派生 `deleteBy…`：它逐个 `em.remove`，Hibernate flush 时先插后删，新旧 PMC 位置 URL 相同会撞 `uk_oa_url (publication_id, url)`（`V2__create_publication.sql:305`））
- Modify: 所有 `ofPmc(…, boolean)` 的调用方与测试（唯一调用方是 `PublicationRepositoryAdapterIT.java:1411/1419/1436/1439`）
- Test: `CAT/patra-catalog-domain/src/test/java/dev/patra/catalog/domain/model/vo/publication/PublicationOaLocationTest.java`、`PublicationRepositoryAdapterIT`（补用例）

**Interfaces:**
- Produces:
  - `static PublicationOaLocation ofPmc(String pmcid, String license, boolean manuscript)`：`oaStatus = GREEN`、`locationType = PUBMED_CENTRAL`、`url = "https://pmc.ncbi.nlm.nih.gov/articles/" + pmcid + "/"`、`hostDomain = "pmc.ncbi.nlm.nih.gov"`、`repositoryName = "PubMed Central"`、`repositoryId = pmcid`、`pmcid`、`license`、`versionType = manuscript ? ACCEPTED : PUBLISHED`、`evidenceSource = "PMC"`、`isBest = true`、`isActive = true`。旧签名删除。
  - `void PublicationRepository#markPmcOpenAccess(long publicationId, PublicationOaLocation pmcLocation)`：一个方法内完成 ① JPQL 批量删除该文献 `location_type = 'pubmed_central'` 的 OA 位置（库里存小写 code）并 `entityManager.flush()`（照 `replaceOaLocationsBatch`，`PublicationRepositoryAdapter.java:1316-1342`）② 插入新位置 ③ `@Modifying(clearAutomatically = true)` 的 `UPDATE cat_publication SET is_oa = true`。

- [ ] **Step 1: 写失败测试**：`PublicationOaLocationTest.should_build_pmc_location_with_license_and_version`（正式版 → `PUBLISHED`，手稿 → `ACCEPTED`，URL / 域名 / `evidenceSource` 如上）；`PublicationRepositoryAdapterIT.should_replace_only_pmc_location_and_set_is_oa`（先有一条出版商位置 + 一条旧 PMC 位置，**旧 PMC 位置的 URL 与新值相同**，否则测不出先插后删的唯一约束问题 → 调用后出版商位置还在、PMC 位置只有一条且许可、版本类型为新值、`is_oa = true`；断言前 `em.clear()`）。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现**，修所有调用方。
- [ ] **Step 4: 跑测试确认通过**（domain `test`、infra `integrationTest --tests "*PublicationRepositoryAdapterIT*"`）。
- [ ] **Step 5: 提交** — `feat(catalog): 改写 PMC OA 位置并只替换 PMC 行 (PAP-85)`

### Task 13: PMC Cloud Service 客户端

spec 5.1。

**Files:**
- Create: `DOM/port/source/PmcArticleSourcePort.java`、`DOM/model/vo/fulltext/{PmcArticleMetadata,PmcArticleFile}.java`
- Create: `DOM/exception/PmcSourceException.java`
- Create: `INFRA/adapter/integration/pmc/{PmcCloudClient,PmcCloudArticleSourceAdapter}.java`（照 OpenAlex：`PmcCloudClient` 是普通类，由 boot 配置 `new`；`@Component` 的 Adapter 注入它）
- Create: `INFRA/adapter/integration/pmc/FullTextSourceProperties.java`（`bucket` 与 `pmc.baseUrl`）、`APP/usecase/fulltext/FullTextJobProperties.java`（`fetch` 与 `parse`；app 不依赖 infra，Runner 读不到 infra 的配置类，先例 `patra-ingest-app` 的 `OutboxRelayProperties`）。两个 record 都绑定前缀 `patra.catalog.fulltext`，各管各的子键
- Create: `BOOT/config/PmcConfiguration.java`（`RestClient pmcRestClient` 与 `PmcCloudClient` 两个 `@Bean`，照 `BOOT/config/OpenAlexConfiguration.java:42-64`；boot 里已有 4 个 RestClient，注入处按 Bean 名或参数名区分）；在 `BOOT/config/CatalogStorageConfiguration.java` 注册两个配置类
- Test: `CAT/patra-catalog-infra/src/integrationTest/java/dev/patra/catalog/infra/adapter/integration/pmc/PmcCloudArticleSourceAdapterIT.java`（RestClient 照 `DictionaryResolverAdapterIT.java:76-90` 手工构建、`HttpClient.Version.HTTP_1_1`）

**Interfaces:**
- Produces:
  - `record PmcArticleFile(String fileName, String md5)`，`static of(…)`
  - `@Builder record PmcArticleMetadata(String pmcid, String versionPrefix, boolean openAccess, boolean manuscript, boolean retracted, String licenseCode, PmcArticleFile xml, PmcArticleFile pdf, List<PmcArticleFile> media)`（`pdf` 可空；`media` 只含 jpg / jpeg / png / gif / webp，按扩展名忽略大小写过滤。实测 BMJ Open、JAMA Netw Open 的正文图片都是 `.webp`）
  - `PmcArticleSourcePort`：`List<String> listVersionPrefixes(String pmcid)`（如 `PMC123.1`、`PMC123.2`；不在桶里返回空列表）；`PmcArticleMetadata getMetadata(String versionPrefix)`；`URI fileUri(String versionPrefix, String fileName)`（拼 HTTPS 地址给 `FileDownloadPort` 用）。HTTP 404 以外的错误、超时、JSON 不合法一律抛 `PmcSourceException`（`DEP_UNAVAILABLE`）。
  - JSON 的 `xml_url` / `pdf_url` / `media_urls` 是 `s3://pmc-oa-opendata/<prefix>/<file>?md5=<md5>`，解析出文件名与 md5。版本 JSON 的响应 `Content-Type` 是 `binary/octet-stream`：先 `.body(String.class)` 再用 `JsonMapper` 读（照 `OpenAlexSourcesClient.java:104-105`）。字段可能缺失：`media_urls` 整个键不存在（PMC7121018、PMC8000000）、`pdf_url` 不存在（PMC13633532）、`license_code` 为 null，都按空处理。
  - ListObjectsV2 的 XML 带命名空间 `http://s3.amazonaws.com/doc/2006-03-01/`，顶层也有一个 `<Prefix>PMC123.</Prefix>`：只读 `CommonPrefixes` 下的 `Prefix`、按 local name 匹配；StAX 工厂用现成的 `infra/parser/support/SecureXmlInputFactory.getInstance()`。
  - `@ConfigurationProperties("patra.catalog.fulltext") record FullTextSourceProperties(@DefaultValue("patra-catalog-fulltext") String bucket, @DefaultValue Pmc pmc)`，内嵌 `Pmc(@DefaultValue("https://pmc-oa-opendata.s3.amazonaws.com/") String baseUrl)`。
  - `@ConfigurationProperties("patra.catalog.fulltext") record FullTextJobProperties(@DefaultValue Fetch fetch, @DefaultValue Parse parse)`，内嵌 `Fetch(@DefaultValue("200") int batchSize, @DefaultValue("4") int concurrency, @DefaultValue("8") int maxAttempts)`、`Parse(@DefaultValue("500") int batchSize)`。本任务先建好，Task 16 / 21 使用。
  - `PmcConfiguration`：连接 5 秒、读取 60 秒、`User-Agent: Patra/0.11 (+https://github.com/linqibin0826/patra)`、`baseUrl` 取 `FullTextSourceProperties.pmc().baseUrl()`、不走隧道代理。

- [ ] **Step 1: 写失败测试** `PmcCloudArticleSourceAdapterIT`（`@WireMockTest`，HTTP/1.1）：
  - `should_list_version_prefixes_from_common_prefixes`（ListObjectsV2 XML 里两个 `CommonPrefixes` → `["PMC123.1","PMC123.2"]`）；
  - `should_return_empty_when_not_in_bucket`（无 `CommonPrefixes`）；
  - `should_parse_metadata_json`（用 spec 1.1 调研时的真实 JSON 结构：`is_pmc_openaccess`、`is_manuscript`、`is_retracted`、`license_code = "CC BY-NC-ND"`、带 md5 的 `xml_url` / `pdf_url` / `media_urls` 含一个 `.jpg`、一个 `.webp`、一个 `.docx` 与一个 `reviewer_comments.pdf` → `media` 只剩 jpg 与 webp）；
  - `should_tolerate_missing_media_pdf_and_license`（JSON 没有 `media_urls`、`pdf_url` 键，`license_code: null` → `media` 空、`pdf` 与 `licenseCode` 为 null）；
  - `should_throw_when_server_error`（500 → `PmcSourceException`）。
  - 所有版本 JSON 的 stub 都带 `Content-Type: binary/octet-stream`，与真实桶一致。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现**（ListObjectsV2 的 XML 用 JDK 的 StAX 读 `CommonPrefixes/Prefix`，去掉尾部 `/`）。
- [ ] **Step 4: 跑测试确认通过。**
- [ ] **Step 5: 提交** — `feat(catalog): 新增 PMC Cloud Service 客户端 (PAP-85)`

### Task 14: 全文文件存储端口与图片尺寸读取

spec 5.1 / 5.4 / 7.4 / 7.5。

**Files:**
- Create: `DOM/port/storage/FullTextObjectStoragePort.java`、`DOM/model/vo/fulltext/{StoredFile,StoredFileInfo}.java`、`DOM/exception/FullTextFileNotFoundException.java`
- Create: `INFRA/adapter/storage/FullTextObjectStorageAdapter.java`
- Create: `DOM/port/image/ImageDimensionPort.java`、`DOM/model/vo/fulltext/ImageDimensions.java`、`INFRA/adapter/image/ImageIoDimensionAdapter.java`
- Modify: `gradle/libs.versions.toml`（`[versions]` 加 `twelvemonkeys = "3.15.2"`，`[libraries]` 加 `twelvemonkeys-imageio-webp = { module = "com.twelvemonkeys.imageio:imageio-webp", version.ref = "twelvemonkeys" }`；普通三方库都登记在这里，`PatraDependencyManagement.kt` 只放 BOM 与强制版本；3.15.3 发布不满两周，不用）、`CAT/patra-catalog-infra/build.gradle.kts`（`runtimeOnly(libs.twelvemonkeys.imageio.webp)`：代码只 import `javax.imageio`，靠 SPI 生效；传递依赖由 POM 带入）。改完提醒用户在 IDEA 点「加载 Gradle 更改」
- Create: `CAT/patra-catalog-infra/src/test/resources/image/{sample-3x2.jpg,sample-3x2.png,sample-3x2.webp,broken.webp}`（`magick -size 3x2 xc:'#c96442' sample-3x2.<ext>` 生成；`broken.webp` 用 `head -c 20 sample-3x2.webp > broken.webp` 生成：保留 RIFF / WEBP / `VP8 ` 标记但截在宽高字段之前。截一半不行，62 字节的文件宽高在偏移 26–29，截到 31 字节宽高仍完整）
- Test: `CAT/patra-catalog-infra/src/integrationTest/java/dev/patra/catalog/infra/adapter/storage/FullTextObjectStorageAdapterIT.java`、`CAT/patra-catalog-infra/src/test/java/dev/patra/catalog/infra/adapter/image/ImageIoDimensionAdapterTest.java`

**Interfaces:**
- Produces:
  - `static String FullTextObjectStoragePort.objectPrefix(String pmcid, String versionPrefix)` → `"pmc/" + pmcid + "/" + versionPrefix + "/"`（接口静态方法，读写两侧共用）。
  - `void store(String objectKey, Path file, String contentType)`；`Optional<StoredFileInfo> stat(String objectKey)`；`StoredFile open(String objectKey)`（找不到抛 `FullTextFileNotFoundException`，新建，`NOT_FOUND`）。
  - `record StoredFileInfo(long size, String contentType, String etag)`；`StoredFile implements Closeable`：`record StoredFile(InputStream content, StoredFileInfo info)`，`close()` 关流。
  - Adapter 用 `ObjectStorageOperations` 与 `FullTextSourceProperties.bucket()`。
  - `record ImageDimensions(int width, int height)`，`static of(…)`；`Optional<ImageDimensions> ImageDimensionPort#read(Path file)`：`ImageIO.createImageInputStream` + `ImageIO.getImageReaders(...)` 取第一个 reader，`setInput(stream, true, true)` 后读 `getWidth(0)` / `getHeight(0)`（只读文件头，不解码整图），finally `dispose()`；没有 reader、IO 异常或读出非正数都返回 empty，不抛。

- [ ] **Step 1: 写失败测试** `FullTextObjectStorageAdapterIT`（`@SpringBootTest(classes = FullTextObjectStorageAdapter.class)` + `@ImportAutoConfiguration(ObjectStorageAutoConfiguration.class)` + `@EnableConfigurationProperties(FullTextSourceProperties.class)` + `@ContextConfiguration(initializers = CatalogITMinIOContainerInitializer.class)`；`classes` 必须写死，否则会扫到 `CatalogITBootstrap` 起整个应用。该初始化器只建 `patra-catalog-cache` 桶（`CatalogITMinIOContainerInitializer.java:31`），桶不存在时 MinIO 报 `NoSuchBucket` 被包成 `DownloadFailedException`，所以 `@BeforeAll` 先 `MinIOContainerInitializer.ensureBucket("patra-catalog-fulltext")`；本任务是该初始化器的第一个使用者）：`should_store_then_open_with_same_bytes`、`should_stat_existing_object`、`should_return_empty_stat_when_missing`、`should_throw_not_found_when_opening_missing`；`ImageIoDimensionAdapterTest`：`should_read_dimensions_of_jpg_png_and_webp`（三个夹具都是 3×2，参数化）、`should_return_empty_for_broken_file`、`should_return_empty_for_non_image`（一个文本文件）。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过。**
- [ ] **Step 5: 提交** — `feat(catalog): 全文文件的 MinIO 存取与图片尺寸读取 (PAP-85)`

### Task 15: 选版本与再查时间

spec 3.4 / 5.1。纯领域逻辑。

**Files:**
- Create: `DOM/model/vo/fulltext/PmcVersionSelection.java`、`DOM/service/fulltext/PmcVersionSelector.java`、`DOM/service/fulltext/FullTextRetrySchedule.java`（`domain/service` 包若不存在就新建，纯 Java）
- Test: `CAT/patra-catalog-domain/src/test/java/dev/patra/catalog/domain/service/fulltext/{PmcVersionSelectorTest,FullTextRetryScheduleTest}.java`

**Interfaces:**
- Produces:
  - `record PmcVersionSelection(PmcArticleMetadata selected, FullTextUnavailableReason unavailableReason)`，两者恰有一个非空；`static ofSelected(…)` / `ofUnavailable(…)`。
  - `static PmcVersionSelection PmcVersionSelector.select(List<PmcArticleMetadata> versions)`（无状态，`final` 类 + 私有构造器 + 静态方法）：空 → `NOT_IN_BUCKET`；无 `openAccess` → `NOT_OPEN_ACCESS`；候选中优先 `manuscript = false`，再取版本号最大（`versionPrefix` 点号后的整数）；选中的 `retracted` → `RETRACTED`。
  - `FullTextRetrySchedule`（构造参数 `Clock`；domain 不能用 Spring 注解，由 Task 16 的 `BOOT/config/FullTextConfiguration` 以 `@Bean` 注册，先例 identity 的 `IdentityConfiguration.java:135`）：
    - `Instant afterFetchFailure(int attemptCount)`（传**本次失败计入之后**的次数）：第 1、2、3 次失败后分别 +1 小时、+4 小时、+1 天，之后每次 +3 天。
    - `Instant afterUnavailable(int attemptCount, LocalDate pmcReleaseDate)`（同样传计入之后的次数）：`pmcReleaseDate` 在今天之后 → 该日 00:00 UTC；否则第 1、2、3、4 次后 +1、+3、+7、+30 天，之后每次 +90 天。

- [ ] **Step 1: 写失败测试**：`PmcVersionSelectorTest`（`should_prefer_non_manuscript`、`should_pick_highest_version_among_equals`、`should_report_not_in_bucket`、`should_report_not_open_access_when_only_manuscript_tdm`、`should_report_retracted`）；`FullTextRetryScheduleTest`（固定 `Clock`，逐档断言上面每个间隔；`should_use_release_date_when_in_future`；`should_ignore_release_date_in_past`）。
- [ ] **Step 2: 跑测试确认失败** — `./gradlew :patra-api:patra-catalog:patra-catalog-domain:test --tests "*fulltext*"`。
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过；** 跑 `:patra-catalog-domain:check` 确认纯净性规则通过。
- [ ] **Step 5: 提交** — `feat(catalog): 新增 PMC 选版本规则与全文再查时间 (PAP-85)`

### Task 16: 抓取用例（Runner / Worker / Persister 与候选查询）

spec 5.2 / 5.3。

**Files:**
- Create: `DOM/port/read/FullTextCandidateReadPort.java`、`DOM/model/read/fulltext/FetchCandidate.java`
- Create: `INFRA/adapter/read/FullTextCandidateReadAdapter.java`
- Create: `APP/usecase/fulltext/fetch/{PmcFullTextFetchRunner,PmcFullTextFetchWorker,PmcFullTextFetchPersister,FullTextFetchRunStats,FetchOutcome}.java`
- Create: `BOOT/config/FullTextConfiguration.java`（`@Bean FullTextRetrySchedule fullTextRetrySchedule(Clock clock)`）
- Test: `APP` 单测 `{PmcFullTextFetchRunnerTest,PmcFullTextFetchWorkerTest,PmcFullTextFetchPersisterTest}`；`CAT/patra-catalog-infra/src/integrationTest/java/dev/patra/catalog/infra/adapter/read/FullTextCandidateReadAdapterIT.java`

**Interfaces:**
- Consumes: Task 11 仓储与 `FullTextMedia`；Task 12 `markPmcOpenAccess`；Task 13 客户端；Task 14 存储与 `ImageDimensionPort`；Task 15 选择器与再查时间；现有 `FileDownloadPort`。
- Produces:
  - `@Builder record FetchCandidate(long publicationId, String pmcid, FullTextStatus currentStatus, int attemptCount, LocalDate pmcReleaseDate)`（`currentStatus` 没有全文记录时为 null）
  - `FullTextCandidateReadPort`：
    - `List<FetchCandidate> findFetchCandidates(Instant now, int maxAttempts, long lastPublicationId, int limit)` — spec 5.2 四类候选的并集，按 `publication_id` 升序 keyset，`DISTINCT ON (publication_id)`（`cat_publication_identifier` 没有 `(publication_id, type)` 唯一约束，同一篇可能有两条 pmc 标识符，不去重会被两个线程同时处理）；`pmcid` 取当前 `cat_publication_identifier.type = 'pmc'` 的值；`pmcReleaseDate` 取 `cat_publication_date` 里 `PMC_RELEASE` 类型的日期（`date_type` 的存储形式照 `writeDateAssociations` 现有写法）；排除已软删除的文献。
    - `List<FetchCandidate> findByPublicationIds(Collection<Long> ids)` — 手动重抓，忽略状态。
  - `record FullTextFetchRunStats(int processed, int fetched, int unavailable, int failed)`，`static of(…)`
  - `enum FetchOutcome { FETCHED, UNAVAILABLE, FAILED }`。
  - `PmcFullTextFetchWorker#process(FetchCandidate c): FetchOutcome`（不开事务）：`listVersionPrefixes` → 对每个前缀 `getMetadata` → `PmcVersionSelector.select` → 不可用则交 Persister 记 `UNAVAILABLE` + 原因 + `afterUnavailable(...)`（`RETRACTED` 的 `nextAttemptAt` 为 null）；可用则依次下载 XML、PDF（有则下）、media，每个文件用 `FileDownloadPort` 下到临时文件、校验 md5（不符抛 `PmcSourceException`）、图片在删临时文件前用 `ImageDimensionPort.read` 取宽高（empty 则宽高为 null，不算失败）、`store` 到 `objectPrefix + 文件名`、finally 删临时文件；成功交 Persister 记 `FETCHED`，带上 PDF 大小（`FileDownloadResult.fileSize`；元数据里只有文件名和 md5）与按 `media` 顺序的 `List<FullTextMedia>`。任何异常 → Persister 记 `FETCH_FAILED`、`attemptCount + 1`、`nextAttemptAt = afterFetchFailure(...)`、`lastError` 截断 2000。
  - `PmcFullTextFetchPersister`：`@Transactional(propagation = REQUIRES_NEW, rollbackFor = Exception.class)` 的三个方法：
    - `recordFetched(FetchCandidate c, PmcArticleMetadata selected, Long pdfSize, List<FullTextMedia> media, Instant fetchedAt)`：`save` 全文记录（`FETCHED`、`attemptCount = 0`、清空 `lastError` / `nextAttemptAt` / `unavailableReason`）+ `markPmcOpenAccess(publicationId, PublicationOaLocation.ofPmc(pmcid, licenseCode, manuscript))`。
    - `recordUnavailable(FetchCandidate c, FullTextUnavailableReason reason, Instant now)`：次数 = `c.currentStatus() == UNAVAILABLE ? c.attemptCount() + 1 : 1`；`nextAttemptAt = reason.isRecheckable() ? retrySchedule.afterUnavailable(次数, c.pmcReleaseDate()) : null`。
    - `recordFailure(FetchCandidate c, Exception error, Instant now)`：次数 = `c.currentStatus() == FETCH_FAILED ? c.attemptCount() + 1 : 1`；`nextAttemptAt = retrySchedule.afterFetchFailure(次数)`；`lastError` 取异常类名 + 消息，截断 2000。
    - 次数只在同一状态连续出现时累加，换状态从 1 起算，成功清零。
  - `PmcFullTextFetchRunner#run(List<Long> publicationIds): FullTextFetchRunStats`：`publicationIds` 非空走手动重抓；否则 keyset 循环 `findFetchCandidates`（每批 `FullTextJobProperties.fetch().batchSize()`）**直到取不到候选为止**（"越快越好"；spec 2.2 已同步：一次运行处理完所有到期候选，兜底任务撞锁直接返回），每批用固定大小 `fetch().concurrency()` 的线程池并发调 Worker，批内等全部完成再取下一批；单篇异常计 `failed` 不中断；`InterruptedException` 恢复中断标志后返回。

- [ ] **Step 1: 写失败测试**
  - `FullTextCandidateReadAdapterIT`：构造五篇文献——有 PMCID 无记录（应选）、`FETCH_FAILED` 到期（应选）、`FETCH_FAILED` 超过重试上限（不选）、`UNAVAILABLE/NOT_OPEN_ACCESS` 到期（应选）、`UNAVAILABLE/RETRACTED`（不选），再加一篇记录里 `pmcid` 与当前标识符不同（应选）和一篇已软删除（不选）；断言结果集合与 `pmcReleaseDate` 取值。
  - `PmcFullTextFetchWorkerTest`：`should_record_fetched_and_store_all_files`（XML + PDF + 2 张图，`store` 的 key 为 `pmc/PMC1/PMC1.2/<文件名>`；`recordFetched` 收到的 `media` 为两张图，一张宽高 1600×2159、另一张 `ImageDimensionPort` 返回 empty → 宽高 null）；`should_record_unavailable_not_in_bucket`；`should_record_failure_with_backoff_when_md5_mismatch`；`should_skip_pdf_when_absent`。
  - `PmcFullTextFetchPersisterTest`：`should_reset_attempts_and_mark_pmc_open_access_when_fetched`；`should_increment_attempts_when_same_status_repeats`；`should_restart_attempts_from_one_when_status_changes`；`should_leave_next_attempt_null_when_retracted`；`should_truncate_last_error_to_2000`。
  - `PmcFullTextFetchRunnerTest`：`should_page_by_last_publication_id`；`should_count_failed_and_continue`；`should_use_given_ids_for_manual_refetch`。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过。** 测试库只有几行，`EXPLAIN` 必然全表扫描、证明不了什么：把 `findFetchCandidates` 的 `EXPLAIN (ANALYZE, BUFFERS)` 写进 runbook 的 mini 验收步骤（预期第 1 类按 `publication_id` keyset 走 `idx_pub_type (publication_id, type)`，见 `V2:92`），提交说明里不贴执行计划。
- [ ] **Step 5: 提交** — `feat(catalog): 实现 PMC 全文抓取用例与候选查询 (PAP-85)`

### Task 17: 抓取命令与 XXL-Job 入口

spec 2.2 / 5.5。

**Files:**
- Create: `APP/usecase/fulltext/fetch/command/PmcFullTextFetchCommand.java`、`APP/usecase/fulltext/fetch/PmcFullTextFetchHandler.java`
- Create: `ADP/scheduler/job/PmcFullTextFetchScheduleJob.java`
- Modify: `docs/patra/runbooks/v0.11-fulltext-runbook.md`（加"全文抓取"一节：handler、子任务链、每小时兜底 cron `0 30 * * * ?`、`publicationIds` 参数；回填增量同步的子任务 ID 步骤）
- Test: `APP` 单测 `PmcFullTextFetchHandlerTest`；`ADP` 集成 `PmcFullTextFetchScheduleJobIT`

**Interfaces:**
- Produces: `record PmcFullTextFetchCommand(List<Long> publicationIds) implements Command<FullTextFetchRunStats>`（紧凑构造器 `List.copyOf`，null 视为空）；Handler 包 `runExclusively("catalog.fulltext-fetch", …)`，拿不到锁返回 `FullTextFetchRunStats.of(0,0,0,0)` 并 log info；`@XxlJob("pmcFullTextFetchJob")` 参数 `publicationIds=1,2,3`（可省）：自己解析——去掉 `publicationIds=` 前缀后整段按逗号切成 ID 列表，不复用 `parseJobParam`（它先按逗号拆键值对，`2`、`3` 会被判为格式错误）；其他参数报错。Handler 不包装异常、不新增错误码，异常交给 ScheduleJob 记 `handleFail`。

- [ ] **Step 1: 写失败测试**：Handler 的锁与透传；ScheduleJob 的参数解析、成功、失败。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过。**
- [ ] **Step 5: 提交** — `feat(catalog): 全文抓取任务入口 (PAP-85)`

---

## C · PAP-86 JATS 解析入库

### Task 18: 正文结构的领域模型

spec 6.2。

**Files:**
- Create: `DOM/model/vo/fulltext/body/{FullTextBody,Section,Block,Inline,Reference,TableCell}.java`
- Test: `CAT/patra-catalog-domain/src/test/java/dev/patra/catalog/domain/model/vo/fulltext/body/FullTextBodyJsonTest.java`

**Interfaces:**
- Produces（全部 record，集合字段 `List.copyOf`）：
  - `record FullTextBody(int schemaVersion, List<Block> blocks, List<Section> sections, List<Section> back, List<Reference> references, int wordCount)`，常量 `SCHEMA_VERSION = 1`。`blocks` 放 `<body>` 里第一个 `<sec>` 之前的直接内容（如 BMJ Open 开头的 "Strengths and limitations" `boxed-text`），spec 6.2 已同步。
  - `record Section(String id, List<Inline> title, List<Section> sections, List<Block> blocks)`。
  - `sealed interface Block`，`@JsonTypeInfo(use = Id.NAME, property = "kind")` + `@JsonSubTypes`，子类型名与 spec 6.2 表一致：`Paragraph("paragraph", List<Inline> content)`、`Figure("figure", id, label, List<Block> caption, String image, Integer width, Integer height)`、`Table("table", id, label, List<Block> caption, List<List<TableCell>> head, List<List<TableCell>> body, List<Block> footnotes)`、`Formula("formula", id, label, String mathml, String image, Integer width, Integer height)`（`mathml` 与 `image` 至多一个非空；`image` 只用于只有公式图片的情况，见 Task 20）、`ListBlock("list", boolean ordered, List<List<Block>> items)`、`Quote("quote", List<Block> blocks)`、`Box("box", String id, List<Inline> title, List<Block> blocks)`、`Code("code", String text)`。
  - `record TableCell(List<Inline> content, int colspan, int rowspan, boolean header)`。
  - `sealed interface Inline`，同样以 `kind` 区分：`Text("text", String text)`、`Styled("style", String style, List<Inline> children)`（style ∈ italic / bold / sup / sub / underline / smallcaps / monospace）、`Xref("xref", String refType, List<String> targets, List<Inline> children)`、`Link("link", String href, List<Inline> children)`、`MathInline("math", String mathml)`、`Break("break")`。
  - `record Reference(String id, String label, List<Inline> content, String pmid, String doi)`。
  - 写法：`Paragraph("paragraph", …)` 只是标出类型名，**类型名只写在 `@JsonSubTypes.Type(name = …)` 里，record 不要有 `kind` 字段**（否则输出两个 `kind`）。多态信息只在经由 `FullTextBody` 序列化时完整，测试序列化整个 `FullTextBody`，不要单独序列化 `List<Block>`。

- [ ] **Step 1: 写失败测试** `FullTextBodyJsonTest`：用 `JsonMapper.builder().build()` 序列化一个含每种 Block / Inline 的样例，断言 JSON 里 `kind` 值逐个等于 spec 6.2 的名字；再反序列化回来 `isEqualTo` 原对象。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过；** 跑 `./gradlew :patra-api:patra-catalog:patra-catalog-domain:check`（只用 `jackson-annotations`，在白名单内）。
- [ ] **Step 5: 提交** — `feat(catalog): 全文正文结构领域模型 (PAP-86)`

### Task 19: JATS 解析器（章节、块、行内）

spec 6.1 / 6.2 / 6.3。

**Files:**
- Create: `DOM/port/parser/JatsBodyParserPort.java`、`DOM/model/vo/fulltext/JatsParseResult.java`
- Create: `INFRA/parser/jats/{JatsBodyParserAdapter,JatsBlockReader,JatsInlineReader}.java`（与现有 `infra/parser/` 下的 PubMed 解析器同处；按职责拆文件，入口是 Adapter）
- Create: `CAT/patra-catalog-infra/src/test/resources/jats/`（夹具，见 Step 1）与 `jats/README.md`（列出每个夹具的 PMCID、出版商、许可、覆盖的特征）
- Test: `CAT/patra-catalog-infra/src/test/java/dev/patra/catalog/infra/parser/jats/JatsBodyParserAdapterTest.java`

**Interfaces:**
- Produces:
  - `record JatsParseResult(FullTextBody body, int warningCount, boolean noBody)`；`static ofBody(FullTextBody, int)`、`static ofNoBody()`。
  - `JatsParseResult JatsBodyParserPort#parse(InputStream xml, List<FullTextMedia> media)`：`media` 是该篇全文记录里的图片清单（Task 11）；图片匹配先按 href 全名，再按"两边都去扩展名后同名"，匹配上则 `image` 取清单里的文件名、`width` / `height` 取清单里的值，找不到则三者为 null 并计警告；解析器不访问存储；XML 不合法抛现有的 `domain/exception/XmlParseException`（`RULE_VIOLATION`，类注释即写明适用于所有 XML 解析适配器），不新建异常。
  - `int JatsBodyParserPort.PARSER_VERSION = 1`（接口常量；改解析规则时 +1）。
  - 解析用 JDK DOM：`DocumentBuilderFactory` 设 `setNamespaceAware(true)`、`setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)`、`setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)`、`setExpandEntityReferences(false)`、`setXIncludeAware(false)`。JATS 文件普遍带 DOCTYPE，所以**不能**开 `disallow-doctype-decl`；上面的设置让它解析 DOCTYPE 但不去下载 DTD、不展开外部实体（预检在 JDK 25 上实测通过）。
  - 结构规则（预检对真实文章实测得出，spec 6.2 / 6.3 已同步）：
    - 只取 `<article>` 的**直接子元素** `<body>` / `<back>`，不用 `getElementsByTagName`：BMJ Open 等文章末尾的 `<sub-article>`（审稿意见）里还有一个 `<body>`。
    - `<body>` 里第一个 `<sec>` 之前的直接块进 `FullTextBody.blocks`；`<sec>` 之后又出现的直接块，归入一个无标题、生成 id 的 Section。
    - **`<p>` 里嵌着块级元素时切开段落**：`disp-formula`、`fig`、`table-wrap`、`list`、`disp-quote`、`boxed-text`、`preformat` 出现在 `<p>` 内（Wiley Stat Med 的 59 个行间公式、BMC 的图表都是这样）时，前面的行内内容成一段，块按块解析，后面的行内内容另起一段；空段落不输出。
    - 表格单元格里的 `<list>`（BMJ Open 有）降级为行内：每项内容之间插 `Break`。
    - `xref` 的 `ref-type` 只认 bibr / fig / table / disp-formula / sec / fn / boxed-text（`Box` 有 `id`）；其他值（`supplementary-material`、`table-fn`、空串）或 `rid` 在本篇找不到目标时，只输出子节点、不生成 `Xref`，计一条警告。

- [ ] **Step 1: 收集夹具**：从 PMC 桶下载至少 20 篇开放获取文章的 XML（`curl https://pmc-oa-opendata.s3.amazonaws.com/<PMCID>.<v>/<PMCID>.<v>.xml`），覆盖 spec 6.5 列出的出版商与特征（多级章节、图、合并单元格的表、行间与行内 MathML、只有 TeX 的公式、列表、`boxed-text`、`mixed-citation` 与 `element-citation`、无 `<body>`）；挑选方法：Europe PMC 检索 `OPEN_ACCESS:y AND PUBLISHER:"<名字>"` 取 PMCID。每篇在 `README.md` 登记一行。
- [ ] **Step 2: 写失败测试**（本任务只测块与行内，公式 / 参考文献 / back / 字数在 Task 20）：
  - `should_parse_nested_sections_with_ids`（取一篇多级章节的夹具，断言顶层章节数、第一节标题文本、子节数，没有 `id` 的节生成了非空 id）。
  - `should_parse_figure_with_caption_and_image`（`media` 传夹具 README 登记的文件名，宽高给 1600×2159；`image` 等于 `graphic` 的 href，`width = 1600`、`height = 2159`）。
  - `should_match_image_without_extension`（href 无扩展名、清单里有同名 `.webp` → 匹配上并带宽高；找不到 → `image`、宽高都为 null、`warningCount` +1）。
  - `should_parse_table_with_colspan_and_rowspan`。
  - `should_split_paragraph_around_nested_block`（`<p>前文<disp-formula>…</disp-formula>后文<list>…</list></p>` → `Paragraph`、`Formula`、`Paragraph`、`ListBlock`，无空段）。
  - `should_put_blocks_before_first_section_into_top_level_blocks`（`<body><boxed-text id="bx1">…</boxed-text><sec>…</sec></body>` → `blocks` 一个 `Box`，`id = "bx1"`）。
  - `should_ignore_sub_article_body`（`<article><body>A</body><sub-article><body>B</body></sub-article></article>` → 只有 A）。
  - `should_degrade_unknown_xref_type_to_children_with_warning`（`ref-type="supplementary-material"` → 只剩子节点文本，`warningCount` +1）。
  - `should_parse_inline_styles_and_xref`（`<italic>`、`<sup>`、`<xref ref-type="bibr" rid="B1 B2">` → `Xref.targets = ["B1","B2"]`）。
  - `should_keep_only_http_https_doi_links`（`ext-link` 为 `ftp://` 的降级为纯文本）。
  - `should_turn_unknown_block_into_paragraph_with_warning`。
  - `should_skip_supplementary_material`。
  - `should_report_no_body_when_body_absent`。
  - `should_reject_malformed_xml`。
- [ ] **Step 3: 跑测试确认失败** — `./gradlew :patra-api:patra-catalog:patra-catalog-infra:test --tests "*JatsBodyParserAdapterTest*"`。
- [ ] **Step 4: 实现。**
- [ ] **Step 5: 跑测试确认通过。**
- [ ] **Step 6: 提交** — `feat(catalog): 解析 JATS 正文的章节、块与行内节点 (PAP-86)`

### Task 20: JATS 解析器（公式、参考文献、back、字数）

spec 6.2 / 6.3 / 6.4。

**Files:**
- Modify: `INFRA/parser/jats/*`；Create: `INFRA/parser/jats/JatsReferenceReader.java`
- Create: `CAT/patra-catalog-infra/src/test/resources/jats/mathml-tags.txt`（夹具里出现过的全部 MathML 本地名，去重排序，交给 PAP-88）
- Test: `JatsBodyParserAdapterTest`（加用例）、`CAT/patra-catalog-infra/src/test/java/dev/patra/catalog/infra/parser/jats/JatsFixtureSweepTest.java`

**Interfaces:**
- 公式：取值顺序为 ① 直接子元素或 `<alternatives>` 里的 MathML `math`（PLOS 是 `alternatives(graphic | mml:math)`，Nat Commun 是 `alternatives(tex-math | mml:math)`）② `tex-math` ③ `graphic`。MathML 序列化为字符串并去掉 `mml:` 前缀与命名空间声明（保留 `display`、`columnalign` 等属性）。**任何位置**的 MathML `math` 元素（不只 `inline-formula` 里的，Wiley 的 941 个里有 882 个直接挂在 `p` / `td` / `th` / `title` 下）都输出 `MathInline`；`disp-formula` 输出 `Formula`。只有 `graphic` 的公式（PLOS PMC12779161 有 5 个）输出 `Formula` 的 `image` / `width` / `height`（按 Task 19 的 `media` 匹配规则），警告 +1。只有 `tex-math` 时输出 `Code` 块、警告 +1，`Code.text` 去掉外壳：含 `\begin{document}` 时取它与 `\end{document}` 之间，再去掉外层 `$$…$$` 或 `$…$`，两端 trim；不含时原样；`inline-formula` 同理输出 `MathInline`（只有 TeX 时输出 `Text` 并警告）。
- 参考文献：`<ref>` 下可能直接是 `mixed-citation` / `element-citation`，也可能是 `citation-alternatives` 包着两种（BMC、Nat Commun 几乎全是）。优先取 `mixed-citation`（含 `citation-alternatives` 里的），原样转行内；没有时用 `element-citation` 按"作者. 标题. 期刊. 年;卷:页."拼；`pub-id[pub-id-type=pmid|doi]` 在实际选用的那种 citation 里找，找不到再到另一种里找。
- back：`ack`、`fn-group`、`app-group/app`、`notes`、`sec`（如数据可用性声明）→ `back` 的 Section；`ref-list` → `references`；除上述元素以外的 `back` 子元素按未知块处理。
- 字数：`blocks`、`sections` 与 `back` 里的 `Text.text` 按 `\\s+` 切分计数，章节标题、图题、表题、表注照计；不进入 `references`、`Formula`、`MathInline`、`Table.head` / `Table.body` 的单元格、`Code`（表题与表注照计）。设计简报附录 A.4 的口径：PMC12670376 约 11,000 词、49 分钟。

- [ ] **Step 1: 写失败测试**：`should_emit_mathml_without_namespace_prefix`（以 `<math` 开头、不含 `mml:`）；`should_fallback_to_code_when_only_tex`；`should_strip_documentclass_wrapper_from_tex`（输入 `\documentclass[12pt]{minimal}\usepackage{amsmath}\begin{document}$$a^2+b^2$$\end{document}` → `Code.text = "a^2+b^2"`；无外壳的 `x_1` 原样）；`should_parse_mixed_and_element_citations_with_pmid_doi`；`should_put_ack_and_footnotes_into_back`；`should_count_words_excluding_references_math_table_cells_and_code`（一节标题 `Methods`（1 词），含 3 词段落、一个行间公式、一个直接挂在 `<p>` 下的 `mml:math`、一张两格表（表题 2 词、单元格各 5 词）、一个 code 块，参考文献 10 词 → `wordCount = 6`）；`should_emit_math_inline_for_bare_mathml_in_paragraph_and_cell`；`should_prefer_mathml_inside_alternatives`；`should_use_formula_image_when_only_graphic`；`should_read_citation_alternatives`；`JatsFixtureSweepTest.should_parse_every_fixture_and_resolve_all_xref_targets`（遍历 `jats/*.xml`：每篇 `noBody` 与 README 登记一致；所有**输出的** `Xref.targets` 都能在 references / 图 / 表 / 公式 / 章节 / box / 脚注 id 里找到（找不到的已按 Task 19 规则降级并计警告，不会出现在结果里）；所有 `Figure.image` 非空或已计警告）与 `should_collect_mathml_tag_names`（断言生成的标签集合等于 `mathml-tags.txt` 内容，文件内容首次由测试辅助方法生成后人工核对提交）。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过。**
- [ ] **Step 5: 提交** — `feat(catalog): 解析 JATS 公式、参考文献与字数 (PAP-86)`

### Task 21: 解析用例与 XXL-Job 入口

spec 6.4 / 6.5。

**Files:**
- Modify: `DOM/port/repository/PublicationFullTextRepository.java`、`INFRA/adapter/persistence/PublicationFullTextRepositoryAdapter.java`（加 `saveBody`、`setHasFullText`）
- Modify: `DOM/port/read/FullTextCandidateReadPort.java`、`INFRA/adapter/read/FullTextCandidateReadAdapter.java`（加解析候选）
- Create: `APP/usecase/fulltext/parse/{JatsFullTextParseRunner,JatsFullTextParsePersister,FullTextParseRunStats}.java`、`APP/usecase/fulltext/parse/command/JatsFullTextParseCommand.java`、`APP/usecase/fulltext/parse/JatsFullTextParseHandler.java`
- Create: `ADP/scheduler/job/JatsFullTextParseScheduleJob.java`
- Modify: `docs/patra/runbooks/v0.11-fulltext-runbook.md`（加"解析"一节与"全量重新解析"操作）
- Test: `APP` 单测 `JatsFullTextParseRunnerTest`；`CAT/patra-catalog-infra/src/integrationTest/java/dev/patra/catalog/infra/adapter/persistence/PublicationFullTextBodyIT.java`；`ADP` 集成 `JatsFullTextParseScheduleJobIT`

**Interfaces:**
- Produces:
  - `void PublicationFullTextRepository#saveBody(long publicationId, FullTextBody body, int parserVersion)`：正文表没有实体，Adapter 用 `EntityManager.createNativeQuery("INSERT INTO cat_publication_fulltext_body (publication_id, body, parser_version) VALUES (:id, CAST(:body AS jsonb), :v) ON CONFLICT (publication_id) DO UPDATE SET body = EXCLUDED.body, parser_version = EXCLUDED.parser_version, updated_at = now()")`，`body` 用注入的 `JsonMapper` 序列化为字符串；`void setHasFullText(long publicationId, boolean value)`（更新 `cat_publication_metadata.has_full_text`；无 metadata 行时不新建）。
  - `List<ParseCandidate> FullTextCandidateReadPort#findParseCandidates(int parserVersion, long lastPublicationId, int limit)`：`status = FETCHED`，或 `status IN (PARSED, PARSE_FAILED) AND parser_version < :parserVersion`，或 `status = UNAVAILABLE AND unavailable_reason = 'NO_BODY' AND parser_version < :parserVersion`（解析器误判无正文时，升版本能救回）；`record ParseCandidate(long publicationId, String pmcid, String pmcVersion, String objectPrefix, List<FullTextMedia> media)`（`media` 来自全文记录的同名列）。
  - Runner：keyset 循环 `findParseCandidates`（每批 `FullTextJobProperties.parse().batchSize()`）直到取不到为止；每篇从存储 `open(objectPrefix + pmcVersion + ".xml")` → `JatsBodyParserPort.parse(xml, candidate.media())` → Persister（`REQUIRES_NEW`）：成功 `saveBody` + 记录 `PARSED`、`wordCount`、`parserVersion`、`parsedAt` + `setHasFullText(true)`；`noBody` → `UNAVAILABLE/NO_BODY` + `parserVersion` + `setHasFullText(false)`；`XmlParseException` 或解析器内部的运行时异常 → `PARSE_FAILED` + `lastError` + `parserVersion`（防止同版本反复重试）+ `setHasFullText(false)`。**存储异常不算解析失败**：`open` 抛的存储类异常（MinIO 抖动、`FullTextFileNotFoundException`）不写记录、保持 `FETCHED`，计入本轮 `failed` 并继续下一篇，下次运行再试。
  - `record JatsFullTextParseCommand() implements Command<FullTextParseRunStats>`；锁名 `catalog.fulltext-parse`；`@XxlJob("jatsFullTextParseJob")` 无参数。

- [ ] **Step 1: 写失败测试**：`PublicationFullTextBodyIT.should_upsert_body_as_jsonb_and_read_back`；`JatsFullTextParseRunnerTest`（`should_mark_parsed_and_set_has_full_text`、`should_mark_no_body_with_parser_version`、`should_mark_parse_failed_with_parser_version`、`should_keep_fetched_when_storage_fails`）；`FullTextCandidateReadAdapterIT` 补 `should_include_outdated_parsed_failed_and_no_body`。spec 6.5 的"升版本重新解析不发 HTTP 请求"由结构保证（解析链路的 Runner / Persister / 解析器都不依赖 `PmcArticleSourcePort`，评审时核对），不另写集成测试：catalog 的 E2E 配置排除了对象存储自动配置，为此单建 MinIO 版 E2E 不划算；`JatsFullTextParseScheduleJobIT`。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过。**
- [ ] **Step 5: 提交** — `feat(catalog): 全文解析用例与任务入口 (PAP-86)`

---

## D · PAP-87 门户全文读端点

### Task 22: 详情接口四字段

spec 7.1 / 7.2。撤稿判定按 UI 码：`cat_publication_type.type_id = 'D016441'`（Retracted Publication；`D016440` 是撤稿声明，不算）。

**Files:**
- Modify: `INFRA/persistence/dao/PublicationDetailDao.java`（`findDetailById` 的 SQL 加 `LEFT JOIN cat_publication_fulltext f` 与撤稿 `EXISTS`）
- Modify: `INFRA/adapter/read/PublicationDetailRow.java`、`PublicationDetailReadAdapter.java`
- Modify: `DOM/model/read/portal/PublicationDetailReadModel.java`（加 `boolean hasFullText`、`boolean hasPdf`、`Long pdfSizeBytes`、`Integer wordCount`）
- Modify: `ADP/rest/portal/response/PortalPublicationDetailResponse.java`（末尾加 `Boolean hasFullText`、`Boolean hasPdf`、`Long pdfSizeBytes`，补 `@param`。`Long` 会被 starter-web 的 Long→String 转换器输出为字符串，与其他 Long 字段一致，PAP-88 的 zod 按字符串解析）
- Modify: `ADP/rest/portal/PortalApiConverter.java`（`:284` 的 `TODO(read-time)` 换成 `ceil(wordCount / 230.0)`；`:49` 的列表卡片 TODO 不动）
- Create: `DOM/model/read/portal/FullTextAvailability.java`（判定规则放这里，读端共用）
- Test: `PublicationDetailReadAdapterIT`（加用例）、`ADP/…/PortalPublicationControllerIT`（改 `:208-209` 的 `estimatedReadMin` 断言并加用例）、`DOM` 单测 `FullTextAvailabilityTest`

**Interfaces:**
- Produces:
  - `record FullTextAvailability(boolean readable, boolean pdfDownloadable)`；`static FullTextAvailability of(FullTextStatus status, boolean hasPdfObject, boolean retracted)`：`readable = status == PARSED && !retracted`；`pdfDownloadable = hasPdfObject && !retracted`（不要求 PARSED）。SQL 只取原始列（`f.status`、`f.pdf_object_key IS NOT NULL`、`f.pdf_size`、`f.word_count`、撤稿 `EXISTS`），判定在 Java 里调用它。
  - `pdfSizeBytes = pdfDownloadable ? pdfSize : null`。
  - `estimatedReadMin = readable && wordCount != null ? ceil(wordCount / 230.0) : null`。

- [ ] **Step 1: 写失败测试**：`FullTextAvailabilityTest`（五种组合：没有全文记录 `of(null, false, false)` 两项都 false、PARSED、PARSED+撤稿、UNAVAILABLE/NO_BODY+PDF、FETCHED）；`PublicationDetailReadAdapterIT.should_expose_full_text_flags_and_word_count`（PARSED、有 PDF 2621440 字节、5230 字）与 `should_hide_full_text_when_retracted`（现有测试辅助 `saveType(pubId, typeValue, typeOrder)` 的 `type_id` 固定为 null（`PublicationDetailReadAdapterIT.java:255-259`），加一个带 `typeId` 的重载，经 `PublicationTypeEntity.of` 的第二个参数写入 `D016441`）；`PortalPublicationControllerIT.should_return_estimated_read_min_when_full_text_readable`（`wordCount = 5230` → `$.estimatedReadMin = 23`、`$.hasFullText = true`、`$.hasPdf = true`、`$.pdfSizeBytes = "2621440"`），原 `shouldReturnPublicationDetail` 里 `estimatedReadMin` 断言改为"模型无全文时为空"。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过**（domain `test`、infra `integrationTest --tests "*PublicationDetailReadAdapterIT*"`、adapter `integrationTest --tests "*PortalPublicationControllerIT*"`）。
- [ ] **Step 5: 提交** — `feat(catalog): 文献详情接口增加全文与 PDF 可用标志、PDF 大小及阅读时长 (PAP-87)`

### Task 23: 全文接口

spec 7.3。

**Files:**
- Create: `DOM/port/read/PublicationFullTextReadPort.java`、`DOM/model/read/portal/FullTextReadModel.java`
- Create: `INFRA/adapter/read/PublicationFullTextReadAdapter.java`
- Create: `APP/usecase/portal/query/PortalPublicationFullTextQueryService.java`
- Create: `DOM/exception/FullTextNotAvailableException.java`（照 `VenueNotFoundException`，`NOT_FOUND`）
- Create: `ADP/rest/portal/response/PortalFullTextResponse.java`、`ADP/rest/portal/CcLicenseLinks.java`
- Modify: `ADP/rest/portal/PortalPublicationController.java`（加端点）、`PortalApiConverter.java`（加转换）；`PortalPublicationControllerIT` 加 `@MockitoBean PortalPublicationFullTextQueryService`（Controller 新增了构造依赖，不加则现有用例全部起不来）
- Test: `PublicationFullTextReadAdapterIT`、`PortalPublicationControllerIT`（加用例）、`ADP` 单测 `CcLicenseLinksTest`

**Interfaces:**
- Produces:
  - `@Builder record FullTextReadModel(long publicationId, String pmcid, String licenseCode, Instant fetchedAt, Integer wordCount, boolean manuscript, FullTextBody body)`；`Optional<FullTextReadModel> PublicationFullTextReadPort#findForPortal(long publicationId)`：用 Task 22 的 `FullTextAvailability` 判定，`readable` 时带 `body` 与 `wordCount`；不可读但 `pdfDownloadable`（典型是 `NO_BODY`）时 `body`、`wordCount` 为 null、其余署名字段照常；两者都不满足返回 empty（spec 7.3）。不可读时不查 `_body` 表。
  - QueryService `@Transactional(readOnly = true) FullTextReadModel getForPortal(long id)`，empty → `FullTextNotAvailableException(id)`。
  - `GET /portal/publications/{id}/fulltext` → `PortalFullTextResponse`，字段与 spec 7.3 的 JSON 一致：`publicationId`（String）、`pmcid`、`pmcUrl`（`https://pmc.ncbi.nlm.nih.gov/articles/<PMCID>/`）、`license`（`record License(String code, String url)`，可空）、`source`（固定文案 `数据来源：美国国家医学图书馆（NLM）PubMed Central`）、`fetchedAt`、`wordCount`（可空）、`estimatedReadMin`（`body` 为 null 时为 null）、`isManuscript`、`body`（直接输出 `FullTextBody`，可空）。
  - `Optional<String> CcLicenseLinks.urlOf(String licenseCode)`：`CC BY` → `https://creativecommons.org/licenses/by/4.0/`，`CC BY-NC`、`CC BY-NC-ND`、`CC BY-ND`、`CC BY-SA`、`CC BY-NC-SA` 同理（4.0），`CC0` → `https://creativecommons.org/publicdomain/zero/1.0/`；大小写与多余空格不敏感；其他返回 empty → `license` 为 null。

- [ ] **Step 1: 写失败测试**：`CcLicenseLinksTest`（七种映射 + `cc by-nc-nd` 小写 + 未知值）；`PublicationFullTextReadAdapterIT`（可读返回正文且 `body` 反序列化正确；`UNAVAILABLE/NO_BODY` 且有 PDF 返回署名、`body` 为 null；撤稿返回 empty；`FETCHED` 无 PDF 返回 empty）；`PortalPublicationControllerIT.should_return_full_text`（断言 `$.publicationId` 是字符串、`$.source` 文案、`$.license.url`、`$.body.sections[0].blocks[0].kind = "paragraph"`）、`should_return_attribution_without_body_when_only_pdf`（200，`$.body` 与 `$.estimatedReadMin` 为 null、`$.license.url` 与 `$.source` 照常）与 `should_return_404_when_full_text_not_available`（`$.code = CATALOG-0404`）。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过。**
- [ ] **Step 5: 提交** — `feat(catalog): 门户全文接口 (PAP-87)`

### Task 24: PDF 与图片接口

spec 7.4 / 7.5。

**Files:**
- Create: `DOM/port/read/FullTextFileReadPort.java`、`DOM/model/read/portal/FullTextFileRef.java`
- Create: `INFRA/adapter/read/FullTextFileReadAdapter.java`
- Create: `APP/usecase/portal/query/PortalFullTextFileQueryService.java`
- Create: `ADP/rest/portal/PortalFullTextFileController.java`（独立 Controller，同前缀 `/portal/publications`，二进制响应与 JSON 端点分开）
- Modify: `BOOT/src/main/resources/application.yml`（`spring.jpa.open-in-view: false`：OSIV 默认开着，下载请求会从查库一直占着 JDBC 连接到写完，池子只有 20 条）
- Test: `ADP/src/integrationTest/java/dev/patra/catalog/adapter/rest/portal/PortalFullTextFileControllerIT.java`、`PortalFullTextFileQueryServiceTest`、`CAT/patra-catalog-infra/src/integrationTest/java/dev/patra/catalog/infra/adapter/read/FullTextFileReadAdapterIT.java`

**Interfaces:**
- Produces:
  - `@Builder record FullTextFileRef(String objectKey, String fileName, String contentType, long size, String etag)`。
  - `FullTextFileReadPort`：`Optional<FullTextFileRef> findPdf(long publicationId)`（按 `FullTextAvailability.pdfDownloadable`，`etag = pdf_md5`、`fileName = <PMCID>.pdf`）；`Optional<FullTextFileRef> findFigure(long publicationId, String fileName)`（按 `readable`，`fileName` 必须在全文记录的 `media` 清单里（spec 7.5），再用 `FullTextObjectStoragePort.stat(objectPrefix + fileName)` 取 `size` 与 `etag`；stat 为空同样返回 empty）。
  - QueryService：`FullTextFileRef getPdf(long id)` / `getFigure(long id, String fileName)`（empty → `FullTextNotAvailableException`）；`StoredFile open(FullTextFileRef ref)`。
  - Controller：
    - **用 `ResponseEntity<Resource>` + `InputStreamResource` 同步写回，不用 `StreamingResponseBody`**：后者走异步分派，`RestTestClient`（`MockMvcClientHttpRequestFactory` 不做 asyncDispatch）读不到响应体，且受 Tomcat 默认 30 秒异步超时限制（网关是 60 秒）。`InputStreamResource` 由 `ResourceHttpMessageConverter` 边读边写、写完关流，不整篇进内存；显式设 `Content-Length` 后不会为算长度去读流。
    - 条件请求用 `WebRequest.checkNotModified(etag)`（处理 `W/"…"`、逗号列表与 `*`）返回 304；`ETag` 一律带引号（MinIO 返回的 etag 不带）。
    - `GET /{id}/fulltext/pdf`：响应头 `Content-Type: application/pdf`、`Content-Length`、`ETag: "<md5>"`、`Cache-Control: public, max-age=86400`、`Content-Disposition: attachment; filename="<PMCID>.pdf"`。
    - `GET /{id}/fulltext/figures/{fileName}`：`fileName` **不加 `@Pattern`**（Controller 有 `@Validated`，路径变量约束会在进方法前变成 422，见 `PortalPublicationControllerIT.java:216-220`），在方法里用 `[A-Za-z0-9._-]+` 手工校验，不匹配抛 `FullTextNotAvailableException` → 404；`Content-Type` 按扩展名（jpg / jpeg → `image/jpeg`、png、gif、webp → `image/webp`）；`Cache-Control: public, max-age=604800, immutable`；`ETag`。
    - **返回前完成所有判定并打开对象**：`getPdf` / `getFigure`（可能 404）→ `open`（对象缺失抛 `FullTextFileNotFoundException` → 404）→ 把流包成 `InputStreamResource` 返回。状态码与头写出之后才发现对象不存在，客户端只会拿到截断文件。

- [ ] **Step 1: 写失败测试** `PortalFullTextFileControllerIT`（`@WebMvcTest` 组合照 `PortalPublicationControllerIT`，`@MockitoBean PortalFullTextFileQueryService`；`RestTestClient`）：
  - `should_stream_pdf_with_headers`（体字节等于 mock 的输入流内容；五个头的值逐一断言）；
  - `should_return_304_when_etag_matches`；
  - `should_return_404_before_streaming_when_pdf_missing`（`getPdf` 抛 `FullTextNotAvailableException` → 404 且 `Content-Type` 为 `application/problem+json`，`open` 从未被调用）；
  - `should_stream_figure_with_immutable_cache`（文件名 `g001.webp` → `Content-Type: image/webp`）；
  - `should_return_404_for_illegal_file_name`（`..%2Fsecret`、`a b.jpg`）；
  - `should_return_404_when_object_missing_in_storage`（`open` 抛 `FullTextFileNotFoundException` → 404 problem+json）；
  - `should_return_304_for_weak_and_list_if_none_match`（`W/"abc"`、`"x", "abc"`）。
  - `FullTextFileReadAdapterIT`（PG + MinIO，`CatalogITMinIOContainerInitializer`）：可读篇的图在 `media` 里 → 返回；不在 `media` 里 → empty；在 `media` 里但 MinIO 没有 → empty；撤稿篇 PDF 与图都 empty；`NO_BODY` 有 PDF → PDF 返回、图 empty。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过；** 关 OSIV 后再跑 catalog adapter 的全部 `integrationTest` 与 boot 的 `e2eTest`，确认没有依赖懒加载的现有端点（读端都是 native SQL，预期无影响）。
- [ ] **Step 5: 提交** — `feat(catalog): 门户全文 PDF 与图片的流式下发 (PAP-87)`

---

## E · 收尾

### Task 25: 文档、全量验证与交付清单

**Files:**
- Modify: `CAT/README.md`（核心职责加"PubMed 增量同步""PMC 全文抓取与解析"；删掉"上游 ingest / 下游 search、analysis"的过时描述）
- Modify: `docs/patra/runbooks/v0.11-fulltext-runbook.md`（补"mini 验收步骤"：首次 `EXISTING_ONLY` 追赶的分批参数、D2 / D3 / D6 的查数 SQL、最大 PDF 的 md5 比对命令）
- Modify: `patra-infra/cd/module-graph.json`（若前面任务新增了 `src/integrationTest` 目录，`./gradlew dumpModuleGraph` 重新生成）
- Create: `docs/patra/plans/2026-10-19-fulltext-reader-backend-pr-body.md`（PR 描述草稿：`Closes PAP-84` ~ `Closes PAP-87` 逐行，Done 判定本地结果表，评审提示含"改了 `*.gradle.kts` 需在 IDEA 加载 Gradle 更改"、Nacos `update-scope` 合并后由 CD 推送）

- [ ] **Step 1:** `./gradlew spotlessApply`，然后后台跑 `./gradlew check integrationTest :patra-api:patra-catalog:patra-catalog-boot:e2eTest`，预期 BUILD SUCCESSFUL、无 FAILED 用例。
- [ ] **Step 2:** 对照 spec 第 4.7 / 5.5 / 6.5 / 7.6 的验收清单逐条打勾，未覆盖的在 PR 描述"Deferred"里写明原因。
- [ ] **Step 3:** 写 runbook 的 mini 验收步骤与 PR 描述草稿。mini 上的 D2 / D3 / D6 实测在后端 PR 合入、CD 部署后按 runbook 执行，结果回写 release spec 第 5 节"实际："行（不在本 plan 内执行）。
- [ ] **Step 4: 提交** — `docs(catalog): 全文阅读后端收尾文档与 PR 描述 (PAP-84 ~ PAP-87)`
