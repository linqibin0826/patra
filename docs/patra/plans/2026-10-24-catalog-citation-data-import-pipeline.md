# catalog 引用数据与可重放导入 Implementation Plan（PAP-110 计划一 / 二）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task（用户已定：本会话内联执行）. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 解析并存储页码、文章号、ISO 缩写与按精度的出版日期；把 baseline / update 两条导入链路合成一条可任意重放的链路（来源位置守卫、逐篇原始 XML、本地重算、md5、坏记录只跳一篇）；门户与内部接口带上按精度的出版日期。

**Architecture:** 读取器（文件 / 来源表）产出 `PubmedSourceRecord(pmid, rawXml, position)` → `PubmedArticleSyncProcessor`（解析 + 守卫 + 组装）→ `PublicationSyncItemWriter`（新增 / 覆盖 + upsert 来源表）→ 删除 Tasklet（带守卫）。两个 Spring Batch 作业：`pubmedFileImportJob`、`pubmedReparseJob`；一个 XXL 入口 `pubmedImportJob` + `pubmedReparseJob`。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Spring Batch、StAX、Spring Data JPA / native SQL、PostgreSQL 17（Testcontainers）、WireMock、JUnit 5 + AssertJ + Mockito。

**Spec:** `docs/patra/specs/2026-10-24-catalog-citation-data-export-design.md`（第 4、5、9 节；第 6、7 节在计划二 `2026-10-24-catalog-citation-export.md`）

**前置**：PAP-109 已实现（Task 12 要给它的 `PublicationResolutionResp` 加字段）。

## Global Constraints

- 模块缩写：`{common}` = `patra-api/patra-common/src`，`{domain}` / `{app}` / `{infra}` / `{adapter}` / `{boot}` = `patra-api/patra-catalog/patra-catalog-<层>/src`；包根 `dev.patra.catalog`。
- 本 worktree 不是 IDEA 打开的主检出，JetBrains MCP 的 `rename_refactoring` 会作用到主检出，**不可用**；改名用 `git mv` + 编辑，改完 `./gradlew compileJava compileTestJava` 兜底。
- 日期部件：年 1800 ~ 2100；月 1 ~ 12；日用 `YearMonth.isValidDay` 判定；`date_precision` 存小写 `year` / `month` / `day`；`date_value` 只在 `day` 精度非空。
- 对外日期 = ISO 8601 降精度字符串（`2019` / `2019-03` / `2019-03-15`）。
- `SourcePosition.rank = yy × 10000 + seq`，文件名正则 `^pubmed(\d{2})n(\d{4})\.xml\.gz$`。
- 守卫、删除、`last_synced_at` 规则严格照 spec 第 5.2 节表格。
- 文章号：无页码且 PII 不含 `(` 时成立。
- md5 旁文件内容正则 `^MD5\((.+)\)= ([0-9a-f]{32})\s*$`，最多尝试 3 次。
- 来源表按 PMID 主键，`raw_xml` 列 `COMPRESSION lz4`。
- 所有方法 `///` Markdown JavaDoc；record ≤ 4 参 `of()`、≥ 5 参 `@Builder`；集合 `List.copyOf`。
- 删除的旧类直接删，不留 `@Deprecated`。
- Spring Batch 上下文只放 `java.*` 类型（文件名、范围名用 `String`，`SourcePosition` 在用的地方重算）；`@StepScope` 代理 bean 不在构造之外访问其字段（记忆里的 v0.11 计划缺陷）。
- 提交信息中文动词起头，结尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`；**每个「提交」步骤先征得用户同意**，未同意则跳过提交继续。不 push、不开 PR。
- 门控：`./gradlew spotlessApply` → `./gradlew check` → 相关 `integrationTest` / `e2eTest`。

## Review Focus

1. 同一个文件里同一 PMID 出现两次（PubMed 偶有）：后出现的为准，来源表只留一行 → Task 8 写入器测试。
2. `DeleteCitation` 与文章在同一文件（同 rank）：删除生效（`<=`）→ Task 8 IT。
3. 重放时 DOI 冲突仍按现有规则保留旧 DOI，且来源表照常更新 → Task 8 写入器测试。
4. 截断的 gzip 在 md5 前就能被发现吗——不依赖 gzip：md5 先于解压校验 → Task 6 fetcher 测试用「截断文件 + 正确 md5」断言重试。
5. 本地重算遇到墓碑行（`raw_xml` 为 NULL）与软删文献：都不读出 → Task 9 IT。

---

### Task 1: `PartialDate` 与出版日期按精度解析

**Files:**
- Create: `{common}/main/java/dev/patra/common/PartialDate.java`
- Modify: `{common}/main/java/dev/patra/common/CanonicalPublication.java`（`PublicationDates` 各字段 `LocalDate` → `PartialDate`）
- Modify: `{infra}/main/java/dev/patra/catalog/infra/parser/strategy/CanonicalPublicationParsingStrategy.java`（`parsePubDate`、`parseMonth`、`buildDates`、`parsePubMedPubDate`；读 `Season`）
- Modify: `{infra}/main/java/dev/patra/catalog/infra/parser/PubmedXmlElements.java`（`SEASON`）
- Modify: `patra-api/patra-ingest/patra-ingest-provenance/src/main/java/dev/patra/ingest/provenance/pubmed/converter/PubmedPublicationConverter.java:380-416`（`PartialDate.ofDay(...)` 包一层）
- Test: `{common}/test/java/dev/patra/common/PartialDateTest.java`、`{infra}/test/java/dev/patra/catalog/infra/parser/parser/strategy/CanonicalPublicationParsingStrategyTest.java`、`.../PubmedPublicationConverterTest.java`

**Interfaces:**
- Produces: `record PartialDate(int year, Integer month, Integer day, String season, String text)`；`static PartialDate ofYear(int)`、`ofYearMonth(int,int)`、`ofDay(LocalDate)`；`String precision()` 返回 `"year"|"month"|"day"`。紧凑构造器校验：月 1 ~ 12、日需有月且 `YearMonth.of(year, month).isValidDay(day)`，否则 `IllegalArgumentException`。

- [ ] **Step 1: 写失败的解析测试**（每条一个最小 `<PubmedArticle>` 片段）

```java
@Test void should_keep_day_precision_when_year_month_day_valid()        // 2021 Oct 14 → (2021,10,14)
@Test void should_drop_day_when_day_invalid_for_month()                  // 2021 Feb 30 → (2021,2,null)
@Test void should_keep_month_precision_when_no_day()                     // 2022 Oct → (2022,10,null)
@Test void should_keep_year_precision_when_year_only()                   // 2023 → (2023,null,null)
@Test void should_store_season_with_year_precision()                     // 2000 Spring → season "Spring"
@Test void should_store_month_range_as_text_with_year_precision()        // 2019 Jun-Jul → text "Jun-Jul"
@Test void should_treat_out_of_range_month_as_year_precision()           // Month 13 → (2019,null,null), text "13"
@Test void should_take_first_year_from_medline_date()                    // MedlineDate "1998 Dec-1999 Jan" → 1998, text 原文
@Test void should_parse_pmc_release_with_precision()                     // pmc-release 2022 May → (2022,5,null)
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:test --tests "*CanonicalPublicationParsingStrategyTest*"`
Expected: FAIL（编译失败：`PartialDate` 不存在）。

- [ ] **Step 3: 实现 `PartialDate`、改 `PublicationDates` 类型、改策略；ingest 转换器包一层并改其测试的期望类型**

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :patra-api:patra-common:test :patra-api:patra-catalog:patra-catalog-infra:test --tests "*CanonicalPublicationParsingStrategyTest*" :patra-api:patra-ingest:patra-ingest-provenance:test --tests "*PubmedPublicationConverterTest*"`
Expected: PASS。（此时 `PubmedArticleItemProcessor` 编译会失败——它在 Task 4 改；本步只编译到策略层的话先用 `compileJava` 确认 common / ingest 通过，infra 的处理器编译错误留给 Task 4。若 infra 测试源集因此编译不过，把 Task 1 与 Task 4 的「处理器读日期」改动合并在本步做最小适配：处理器先按 `PartialDate` 取 `year/month/day`。）

- [ ] **Step 5: 提交（先征得用户同意）**

```bash
git add patra-api/patra-common patra-api/patra-ingest patra-api/patra-catalog/patra-catalog-infra
git commit -m "feat(catalog): 出版日期按实际精度解析，不再补 1 月 1 日"
```

---

### Task 2: 解析页码、ELocationID、期刊缩写

**Files:**
- Modify: `CanonicalPublicationParsingStrategy.java`（`parsePagination` 读 `StartPage` / `EndPage`；新增 `parseELocationId`；`parseJournal` 读 `ISOAbbreviation`；`parseMedlineJournalInfo` 读 `MedlineTA`；`buildCanonical` 赋值）
- Modify: `PubmedXmlElements.java`（`START_PAGE`、`END_PAGE`、`E_LOCATION_ID`、`ISO_ABBREVIATION`、`MEDLINE_TA`、属性 `EIdType` / `ValidYN`）
- Test: `CanonicalPublicationParsingStrategyTest.java`

**Interfaces:**
- Produces: `CanonicalPublication.pagination.{startPage,endPage,medlinePgn}`、`journal.{isoAbbreviation,medlineAbbreviation}` 有值；ELocationID pii 进 `identifiers`（类型 `PublicationIdentifierType.PII`）；ELocationID doi 只在 `ArticleIdList` 无 DOI 时作为 DOI。

- [ ] **Step 1: 写失败的测试**

```java
@Test void should_parse_medline_pgn_and_start_end_pages()      // <StartPage>1451</StartPage><EndPage>1461</EndPage><MedlinePgn>1451-61</MedlinePgn>
@Test void should_parse_elocation_pii_as_identifier()          // <ELocationID EIdType="pii" ValidYN="Y">e0123456</ELocationID> → PII "e0123456"
@Test void should_ignore_invalid_elocation()                   // ValidYN="N" 不收
@Test void should_use_elocation_doi_only_when_article_id_doi_missing()
@Test void should_parse_iso_abbreviation_and_medline_ta()      // "N Engl J Med" / "N Engl J Med"
```

- [ ] **Step 2: 跑测试确认失败**　Run: 同 Task 1 Step 2　Expected: FAIL（断言失败）。
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过**　Expected: PASS。
- [ ] **Step 5: 提交（先征得用户同意）**　`feat(catalog): 解析页码、文章号与期刊 ISO 缩写`

---

### Task 3: 领域值对象 `Pagination`、`ArticleNumber`、`SourcePosition`、`PublicationDate.toIsoString`

**Files:**
- Create: `{domain}/main/java/dev/patra/catalog/domain/model/vo/publication/Pagination.java`、`PageRange.java`、`ArticleNumber.java`、`SourcePosition.java`
- Modify: `{domain}/main/java/dev/patra/catalog/domain/model/vo/publication/PublicationDate.java`（加 `toIsoString()`，忽略 `dateString`）
- Test: 各自 `{domain}/test/.../vo/publication/*Test.java`

**Interfaces:**
- Produces:
  - `record Pagination(String medlinePgn, String startPage, String endPage)`，`static of(...)`，`Optional<PageRange> firstRange()`
  - `record PageRange(String first, String last)`（`last` 可空），`of()`
  - `final class ArticleNumber`：`static Optional<String> of(String pii, Pagination pagination)`
  - `record SourcePosition(String fileName, int rank)`：`static SourcePosition ofFileName(String)`（非法抛 `IllegalArgumentException`）、`static SourcePosition of(String fileName, int rank)`、`boolean isOlderThan(Integer storedRank)`（`storedRank == null` → false）
  - `String PublicationDate.toIsoString()`

- [ ] **Step 1: 写失败的测试**——spec 第 4.5 节两张表逐行做成 `@ParameterizedTest`；`SourcePosition`：`pubmed26n1333.xml.gz` → 261333、`pubmed27n0001.xml.gz` 大于 `pubmed26n1999.xml.gz`、`foo.xml.gz` 抛出；`toIsoString` 三种精度，带 `dateString` 时仍输出数字形式。
- [ ] **Step 2: 跑测试确认失败**　Run: `./gradlew :patra-api:patra-catalog:patra-catalog-domain:test`　Expected: FAIL（编译失败）。
- [ ] **Step 3: 实现**（`firstRange` 的正则与补高位按 spec 第 4.5 节规则 1 ~ 3）
- [ ] **Step 4: 跑测试确认通过，并跑六边形纯净性**　Run: `./gradlew :patra-api:patra-catalog:patra-catalog-domain:check`　Expected: PASS。
- [ ] **Step 5: 提交（先征得用户同意）**　`feat(catalog): 新增页码、文章号与来源位置值对象`

---

### Task 4: `V10` 引用列与写入链

**Files:**
- Create: `{infra}/main/resources/db/migration/V10__add_publication_citation_columns.sql`（spec 第 4.3 节 SQL）
- Modify: `PublicationAggregate.java`（加 `publicationMonth`、`publicationDay`、`pagination`、`journalIsoAbbreviation`；`create` / `restore` / 覆盖用的 getter）
- Modify: `PublicationEntity.java`、`PublicationJpaMapper.java`
- Modify: `PubmedArticleItemProcessor.java`（出版日期部件取 `PartialDate`；`JournalInstanceParams` 照传月 / 日可空；PII 进扩展标识符；页码与缩写进聚合）
- Modify: `PublicationImportResult.java:408-440`（`fromLocalDate` / `primaryFromLocalDate` 改为按 `PartialDate` 构造，删除硬编码 `"day"`）、`PublicationImportResultMapper.java`
- Modify: `PublicationRepositoryAdapter.java:689-716`（`date_value` 只在 `day` 精度）
- Modify: `PublicationSyncRepositoryAdapter.java:215-250`（`updatePublicationColumns` 加六列）
- Test: `PubmedArticleItemProcessorTest.java`、`{infra}/integrationTest/.../persistence/PublicationRepositoryAdapterIT.java`、`PublicationSyncRepositoryAdapterIT.java`

**Interfaces:**
- Consumes: Task 1 `PartialDate`、Task 3 `Pagination`。
- Produces: `PublicationAggregate.getPublicationMonth()/getPublicationDay()/getPagination()/getJournalIsoAbbreviation()`；`cat_publication` 新列有值；`cat_publication_date` 按精度写。

- [ ] **Step 1: 写失败的测试**

```java
// PubmedArticleItemProcessorTest
@Test void should_carry_year_only_date_without_fabricated_month_day()   // published=ofYear(2023) → aggregate month/day null；date row precision YEAR
@Test void should_pass_null_month_day_to_journal_instance_when_absent()
@Test void should_add_pii_identifier_and_pagination_and_iso_abbreviation()

// PublicationRepositoryAdapterIT（插入）
@Test void should_persist_citation_columns_and_precise_dates()
// 断言 cat_publication.publication_month/day、medline_pgn、start_page、end_page、journal_iso_abbreviation；
// cat_publication_date：year 精度行 month/day/date_value 为 NULL、date_precision='year'

// PublicationSyncRepositoryAdapterIT（覆盖）
@Test void should_overwrite_citation_columns()
```

- [ ] **Step 2: 跑测试确认失败**　Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:test --tests "*PubmedArticleItemProcessorTest*"` 与 `:integrationTest --tests "*PublicationRepositoryAdapterIT*" --tests "*PublicationSyncRepositoryAdapterIT*"`　Expected: FAIL。
- [ ] **Step 3: 实现迁移、聚合、实体、映射、处理器、写入链**
- [ ] **Step 4: 跑测试确认通过**　Expected: PASS。
- [ ] **Step 5: 提交（先征得用户同意）**　`feat(catalog): 存储页码、ISO 缩写与按精度的出版日期`

---

### Task 5: `V11` 来源表与进度表改名

**Files:**
- Create: `{infra}/main/resources/db/migration/V11__add_publication_source_and_rename_pubmed_file.sql`（spec 第 5.3 节建表 + lz4；`ALTER TABLE cat_pubmed_update_file RENAME TO cat_pubmed_file`；约束 / 索引 / 触发器改名；加 `kind VARCHAR(10) NOT NULL DEFAULT 'UPDATE'` 后 `ALTER COLUMN kind DROP DEFAULT`；加 `skipped_stale_count INTEGER NOT NULL DEFAULT 0`）
- Rename（`git mv` + 编辑）：`PubmedUpdateFileStatus` → `PubmedFileStatus`、`PubmedUpdateScope` → `PubmedImportScope`、`PubmedUpdateFileCounts` → `PubmedFileCounts`（加 `skippedStale`）、`PubmedUpdateFileRecord` → `PubmedFileRecord`（加 `kind`）、`PubmedUpdateFileRef` → `PubmedFileRef`（加 `kind`，`downloadUrl(baseUrl)`）、`PubmedUpdateFileRepository` → `PubmedFileRepository`、`PubmedUpdateFileRepositoryAdapter` → `PubmedFileRepositoryAdapter`、`PubmedUpdateFileDao` → `PubmedFileDao`、`PubmedUpdateFileEntity` → `PubmedFileEntity`（`@Table(name = "cat_pubmed_file")`）、对应测试类
- Create: 枚举 `{domain}/.../enums/PubmedFileKind.java`（`BASELINE`、`UPDATE`）
- Test: `{infra}/integrationTest/.../persistence/PubmedFileRepositoryAdapterIT.java`（由旧 IT 改名）、新 `PublicationSourceSchemaIT.java`

**Interfaces:**
- Produces: `PubmedFileRepository.findFirstUnfinishedSeq(PubmedFileKind)`、`findLastCompletedSeq(PubmedFileKind)`（按 kind 过滤）；其余方法保持签名、类型改名。

- [ ] **Step 1: 写失败的 IT**

```java
// PublicationSourceSchemaIT
@Test void should_create_source_table_with_lz4_raw_xml()
// SELECT attcompression FROM pg_attribute WHERE attrelid='cat_publication_source'::regclass AND attname='raw_xml' → 'l'

// PubmedFileRepositoryAdapterIT
@Test void should_resume_update_seq_ignoring_failed_baseline_rows()
// 存 BASELINE 1333 FAILED、UPDATE 1335 COMPLETED → findFirstUnfinishedSeq(UPDATE) 为空、findLastCompletedSeq(UPDATE)=1335
```

- [ ] **Step 2: 跑测试确认失败**　Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest --tests "*PublicationSourceSchemaIT*" --tests "*PubmedFileRepositoryAdapterIT*"`　Expected: FAIL。
- [ ] **Step 3: 写迁移、做改名、加 kind 过滤**
- [ ] **Step 4: `./gradlew compileJava compileTestJava` 后跑测试确认通过**　Expected: PASS。
- [ ] **Step 5: 提交（先征得用户同意）**　`feat(catalog): 新增文献来源表，进度表改为同时记录 baseline 与 update`

---

### Task 6: 逐篇切出原始 XML、`parseArticle`、`PubmedFileFetcher`

**Files:**
- Create: `{domain}/main/java/dev/patra/catalog/domain/model/vo/publication/PubmedSourceRecord.java`
- Modify: `{domain}/.../port/parser/PubmedXmlParserPort.java`（删 `parse`、`parseUpdateFile`；加 `parseArticle(String rawXml)`）、`{infra}/.../parser/PubmedXmlParserAdapter.java`
- Delete: `{infra}/.../parser/strategy/PubmedUpdateParsingStrategy.java`
- Create: `{infra}/main/java/dev/patra/catalog/infra/batch/publication/PubmedArticleSplitter.java`、`PubmedFileFetcher.java`、`PubmedFileItemReader.java`
- Delete: `PubmedArticleItemReader.java`、`update/PubmedUpdateItemReader.java` 及其测试
- Test: `{infra}/test/.../batch/publication/PubmedArticleSplitterTest.java`、`PubmedFileFetcherTest.java`（WireMock）、`PubmedFileItemReaderTest.java`；`PubmedXmlParserAdapterIT.java` 改为测 `parseArticle`

**Interfaces:**
- Produces:
  - `record PubmedSourceRecord(String pmid, String rawXml, SourcePosition position)`，`of()`
  - `CanonicalPublication PubmedXmlParserPort.parseArticle(String rawXml)`（缺 PMID 返回 `null`；XML 不合法抛 `XmlParseException`）
  - `PubmedArticleSplitter`：`PubmedArticleSplitter(InputStream xml, Consumer<String> deletedPmidSink)`，`Optional<String> next()`（下一篇 `<PubmedArticle>` 原文）；PMID 由 `parseArticle` 之后取，切分器不解析内容
  - `PubmedFileFetcher(FileDownloadPort, int maxAttempts)`：`Path fetch(URI fileUrl)`（校验 `fileUrl + ".md5"`；失败抛 `PubmedFileFetchException`）
  - `PubmedFileItemReader implements ItemStreamReader<PubmedSourceRecord>`：构造参数 `(PubmedFileFetcher, String downloadUrl)`；`open` 拉取 + 断点跳过；`update` 写 `CURRENT_INDEX`、`DELETED_PMIDS`（键沿用 `PubmedUpdateContextKeys`，类改名 `PubmedImportContextKeys`）；`position = SourcePosition.ofFileName(文件名)`

- [ ] **Step 1: 写失败的测试**

```java
// PubmedArticleSplitterTest（用 {boot}/testFixtures/resources/pubmed/pubmed26n9001.xml）
@Test void should_split_each_pubmed_article_and_collect_delete_citations()
// 篇数 = 夹具里 <PubmedArticle> 个数；每段以 <PubmedArticle 开头、</PubmedArticle> 结尾；deleted PMID 列表与夹具一致
@Test void should_round_trip_split_article_through_parse_article()
// parseArticle(片段) 的 PMID / 标题与整文件旧解析结果一致

// PubmedFileFetcherTest
@Test void should_return_file_when_md5_matches()
@Test void should_retry_then_fail_when_md5_mismatches()            // 3 次下载、抛 PubmedFileFetchException
@Test void should_retry_truncated_file_even_if_gzip_unchecked()    // Review Focus 4
@Test void should_fail_on_malformed_md5_sidecar()
```

- [ ] **Step 2: 跑测试确认失败**　Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:test --tests "*PubmedArticleSplitterTest*" --tests "*PubmedFileFetcherTest*"`　Expected: FAIL（编译失败）。
- [ ] **Step 3: 实现**（切分：`XMLEventReader` + `XMLEventWriter` 写 `StringWriter`，按深度配对；`parseArticle`：新建 `XMLStreamReader` 定位到根元素后调用 `CanonicalPublicationParsingStrategy.INSTANCE.parseRecord`）
- [ ] **Step 4: 跑测试确认通过**　Expected: PASS。
- [ ] **Step 5: 提交（先征得用户同意）**　`feat(catalog): 逐篇切出 PubMed 原始 XML，下载后校验 md5`

---

### Task 7: `PubmedArticleSyncProcessor`（解析 + 守卫 + 坏记录跳过）

**Files:**
- Create: `{infra}/main/java/dev/patra/catalog/infra/batch/publication/PubmedArticleSyncProcessor.java`
- Delete: `update/PubmedUpdateRoutingProcessor.java` 及测试
- Modify: `PublicationSyncTarget.java`（加 `Integer sourceRank`；`publicationId` 改为 `Long` 可空——墓碑行没有文献）、`update/PublicationSyncItem.java`（加 `rawXml`、`position`）、`update/PubmedUpdateCounters.java` → `PubmedImportCounters`（加 `skippedStale`）
- Modify: `PublicationSyncRepository.findSyncTargetsByPmids` 的实现 SQL：`cat_publication_source s FULL OUTER JOIN cat_publication p ON s.pmid = p.pmid`（两边任一有即返回；只有文献 → `sourceRank` 为 null，只有来源 → `publicationId` 为 null）
- Test: `{infra}/test/.../batch/publication/PubmedArticleSyncProcessorTest.java`

**Interfaces:**
- Consumes: Task 3 `SourcePosition`、Task 6 `parseArticle` / `PubmedSourceRecord`、现有 `PubmedArticleItemProcessor`。
- Produces: `PubmedArticleSyncProcessor(PubmedXmlParserPort, PublicationSyncRepository, PubmedArticleItemProcessor, PubmedImportScope, PubmedImportCounters, boolean reparse)`，`PublicationSyncItem process(PubmedSourceRecord)`；`reparse = true` 时只接受 rank 相等。

- [ ] **Step 1: 写失败的测试**（spec 第 5.2 节文章相关的 8 行，各一个用例；外加坏记录三条）

```java
@Test void should_skip_absent_pmid_in_existing_only_scope()          // skippedAbsent +1
@Test void should_insert_absent_pmid_in_all_scope()
@Test void should_skip_older_rank_and_count_stale()                  // stored 261340, incoming 261333 → null, skippedStale +1
@Test void should_overwrite_equal_rank()
@Test void should_overwrite_newer_rank()
@Test void should_overwrite_when_stored_rank_null()
@Test void should_skip_soft_deleted_when_incoming_not_newer()
@Test void should_skip_tombstone_when_incoming_older()                // 墓碑：publicationId null、rank 261350
@Test void should_insert_when_tombstone_is_older_in_all_scope()        // 墓碑 rank 261340、进来 261350、ALL → target 归一为 null（新增）
@Test void should_skip_when_tombstone_is_older_in_existing_only_scope()
@Test void should_skip_record_on_xml_parse_exception()                // skippedInvalid +1
@Test void should_skip_record_on_domain_assertion()                   // 委托抛 IllegalArgumentException → null
@Test void should_propagate_infrastructure_exception()                // 仓储抛 DataAccessException → 抛出
@Test void should_only_accept_equal_rank_in_reparse_mode()
```

- [ ] **Step 2: 跑测试确认失败**　Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:test --tests "*PubmedArticleSyncProcessorTest*"`　Expected: FAIL。
- [ ] **Step 3: 实现**（捕获 `XmlParseException`、`IllegalArgumentException`、`IllegalStateException`、`DateTimeException`；缺 PMID / 年份 / 标题返回 `null`）
- [ ] **Step 4: 跑测试确认通过**　Expected: PASS。
- [ ] **Step 5: 提交（先征得用户同意）**　`feat(catalog): 按来源文件位置判断新旧版本，坏记录只跳过这一篇`

---

### Task 8: 写入器、来源表 upsert、删除守卫与 `last_synced_at`

**Files:**
- Modify: `update/PublicationSyncItemWriter.java`（覆盖后 upsert 来源行；新增只为回查到的 PMID 写；chunk 内同 PMID 后者为准）
- Modify: `update/PubmedDeleteCitationTasklet.java`（从作业参数读文件名字符串与范围名，在 Tasklet 里重算 `SourcePosition`；守卫 SQL；`ALL` 写墓碑）
- Modify: `PublicationSyncRepository.java` + `PublicationSyncRepositoryAdapter.java`：
  - `overwriteAllWithAssociations(List<PublicationCompleteData>, Map<Long,String> doiOverrides, Instant now, Set<Long> refreshSyncedAt)`——只对 `refreshSyncedAt` 里的文献写 `last_synced_at`
  - `upsertSources(List<PublicationSourceRow>)`（`ON CONFLICT (pmid) DO UPDATE`）
  - `softDeleteByPmids(Collection<String> pmids, SourcePosition position, boolean writeTombstones, Instant now)` 返回实际软删数
- Create: `{domain}/.../vo/publication/PublicationSourceRow.java`（`pmid, sourceFile, sourceRank, rawXml, parsedAt`，`@Builder`）
- Test: `PublicationSyncItemWriterTest.java`、`{infra}/integrationTest/.../persistence/PublicationSourceGuardIT.java`

**Interfaces:**
- Consumes: Task 7 的 `PublicationSyncItem(result, target, rawXml, position)`。
- Produces: 来源表与 `last_synced_at` 行为照 spec 第 5.2、5.3 节。

- [ ] **Step 1: 写失败的测试**

```java
// PublicationSyncItemWriterTest（mock 仓储）
@Test void should_upsert_source_for_overwrites_and_inserted_pmids_only()   // 插入写入器丢掉的 PMID 不写来源；回查只算 publicationId 非空的目标
@Test void should_keep_last_item_for_duplicate_pmid_in_chunk()             // Review Focus 1
@Test void should_refresh_synced_at_only_for_newer_non_null_rank()
@Test void should_still_upsert_source_when_doi_conflict_keeps_old_doi()    // Review Focus 3

// PublicationSourceGuardIT（Testcontainers，用真实适配器）
@Test void publication_without_source_row_is_found_with_null_rank_and_overwritten()   // 守住回填：旧数据不能被当成「库里没有」
@Test void deleted_in_later_file_stays_deleted_when_earlier_file_replayed()
@Test void earlier_delete_does_not_undo_later_revival()                    // 1360 复活后 1340 的删除不生效、rank 不降
@Test void delete_in_same_file_as_article_applies()                        // Review Focus 2
@Test void tombstone_blocks_insert_from_earlier_baseline_in_all_scope()
@Test void equal_rank_and_null_rank_overwrites_keep_last_synced_at()
```

- [ ] **Step 2: 跑测试确认失败**　Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:test --tests "*PublicationSyncItemWriterTest*"` 与 `:integrationTest --tests "*PublicationSourceGuardIT*"`　Expected: FAIL。
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过**　Expected: PASS。
- [ ] **Step 5: 提交（先征得用户同意）**　`feat(catalog): 写入时保存原始 XML，删除也按来源位置判断`

---

### Task 9: 作业配置合并与本地重算读取器

**Files:**
- Create: `{infra}/main/java/dev/patra/catalog/infra/batch/publication/PubmedImportJobConfig.java`（`pubmedFileImportJob` = 文章 Step + 删除 Step；`pubmedReparseJob` = 重算 Step）
- Create: `{infra}/main/java/dev/patra/catalog/infra/batch/publication/PubmedSourceRecordReader.java`（JDBC 分页，`raw_xml IS NOT NULL AND p.deleted_at IS NULL`，按 `pmid`）
- Delete: `PubmedBaselineJobConfig.java`、`update/PubmedUpdateJobConfig.java`、`PublicationImportJobParams.java` 及 `PubmedBaselineJobConfigTest.java`
- Rename: `PubmedUpdateImportJobParams` → `PubmedFileImportJobParams`（加 `kind`、`reprocess` 不进标识参数——重处理靠新 generation）
- Modify: `{domain}/.../port/batch/PublicationBatchPort.java`（删 `launchBaselineImport`；`launchUpdateImport` → `launchFileImport(PubmedFileImportParams)`；加 `PubmedFileCounts launchReparse()`）、`PublicationBatchAdapter.java`
- Rename: `PubmedUpdateImportParams` → `PubmedFileImportParams`（加 `kind`、`baseUrl`）
- Test: `{infra}/integrationTest/.../batch/PubmedImportJobIT.java`（Spring Batch + Testcontainers + WireMock 提供文件与 md5）

**Interfaces:**
- Consumes: Task 6 读取器 / 抓取器、Task 7 处理器、Task 8 写入器与 Tasklet。
- Produces: `PublicationBatchPort.launchFileImport(PubmedFileImportParams) → PubmedFileCounts`、`launchReparse() → PubmedFileCounts`。

- [ ] **Step 1: 写失败的 IT**

```java
@Test void should_import_baseline_file_in_all_scope_and_store_raw_xml()
@Test void should_overwrite_from_update_file_and_soft_delete()
@Test void should_reparse_from_stored_raw_xml_without_network()
// 改写某篇 raw_xml 的 <MedlinePgn> → launchReparse → medline_pgn 更新、last_synced_at 不变、WireMock 无请求
@Test void reparse_skips_tombstones_and_soft_deleted()                  // Review Focus 5
```

- [ ] **Step 2: 跑测试确认失败**　Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest --tests "*PubmedImportJobIT*"`　Expected: FAIL。
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过**　Expected: PASS。
- [ ] **Step 5: 提交（先征得用户同意）**　`refactor(catalog): 合并 baseline 与 update 导入作业，新增本地重算作业`

---

### Task 10: 应用层 `PubmedFileImportHandler` / `PubmedReparseHandler`

**Files:**
- Rename + Modify: `PubmedUpdateSyncHandler` → `{app}/.../usecase/publication/sync/PubmedFileImportHandler.java`；`PubmedUpdateSyncCommand` → `PubmedFileImportCommand`（`kind, baseUrl, filePrefix, startSeq, scope, from, to, maxFiles, reprocess`，`@Builder`）；`PubmedUpdateSyncResult` → `PubmedFileImportResult`
- Create: `{app}/.../usecase/publication/sync/PubmedReparseHandler.java`、命令 `PubmedReparseCommand`（无字段）、结果复用 `PubmedFileImportResult`
- Delete: `{app}/.../usecase/publication/baseline/**`
- Modify: `ExclusiveRunPort.java`（`PUBMED_UPDATE_SYNC` → `PUBMED_IMPORT`）
- Rename: `PubmedUpdateFileListingPort` → `PubmedFileListingPort`（只用于 UPDATE 求最新序号）
- Test: `{app}/test/.../usecase/publication/sync/PubmedFileImportHandlerTest.java`（由旧测试改）、`PubmedReparseHandlerTest.java`

**Interfaces:**
- Produces: `PubmedFileImportResult handle(PubmedFileImportCommand)`；规则照 spec 第 5.8 节表格。

- [ ] **Step 1: 写失败的单测**

```java
@Test void baseline_requires_from_and_defaults_to_equal_from()
@Test void update_resumes_from_update_progress_only()
@Test void skips_completed_files_unless_reprocess()
@Test void reprocess_uses_new_generation_for_completed_file()
@Test void reparse_and_import_share_the_same_lock()          // 拿不到锁返回 skippedByLock
@Test void marks_file_failed_and_stops_on_error()            // 沿用旧行为
```

- [ ] **Step 2: 跑测试确认失败**　Run: `./gradlew :patra-api:patra-catalog:patra-catalog-app:test --tests "*PubmedFileImportHandlerTest*" --tests "*PubmedReparseHandlerTest*"`　Expected: FAIL。
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过**　Expected: PASS。
- [ ] **Step 5: 提交（先征得用户同意）**　`refactor(catalog): 统一 PubMed 文件导入入口，支持显式重处理与本地重算`

---

### Task 11: XXL 入口与配置

**Files:**
- Rename + Modify: `PubmedUpdateSyncScheduleJob` → `{adapter}/.../scheduler/job/PubmedImportScheduleJob.java`（`@XxlJob("pubmedImportJob")`，参数 `kind,from,to,maxFiles,scope,reprocess`）
- Create: `{adapter}/.../scheduler/job/PubmedReparseScheduleJob.java`（`@XxlJob("pubmedReparseJob")`）
- Delete: `PubmedBaselineImportScheduleJob.java`、`PubmedBaselineImportScheduleJobTest.java`
- Modify: `PubmedDataSourceProperties.java`（`updateScope` → `scope`，默认 `EXISTING_ONLY`；JavaDoc 示例同步）
- Modify: `patra-infra/nacos/dev/patra-catalog.yaml`（`update-scope:` → `scope:`）
- Test: `{adapter}/integrationTest/.../scheduler/job/PubmedImportScheduleJobIT.java`（由旧 IT 改）

- [ ] **Step 1: 写失败的测试**——参数解析：无参数 → UPDATE / 续跑 / 配置范围；`kind=BASELINE` 缺 `from` → 参数错误；未知键、重复键、`reprocess=yes` → 参数错误；`kind=BASELINE,from=1333,scope=EXISTING_ONLY,reprocess=true` 组出的命令字段正确。
- [ ] **Step 2: 跑测试确认失败**　Run: `./gradlew :patra-api:patra-catalog:patra-catalog-adapter:integrationTest --tests "*PubmedImportScheduleJobIT*"`　Expected: FAIL。
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过**　Expected: PASS。
- [ ] **Step 5: 提交（先征得用户同意）**　`refactor(catalog): XXL 合并为 pubmedImportJob，新增 pubmedReparseJob`

---

### Task 12: 读端：按精度的日期与检索排序

**Files:**
- Modify: `PublicationDetailDao.java:132-141`（读 `year, month, day, date_precision`）、`DateRow`、`PublicationDetailReadAdapter.java`、`PublicationDetailReadModel.java`（`PublicationDateView(String type, String date)`；加 `publishedDate`）
- Modify: `PortalPublicationDetailResponse.java`（`PublicationDate.date` → `String`；加 `publishedDate`）、`PortalApiConverter.java`
- Modify: `PublicationSearchDao.java`（`SELECT_LIST` 加 `publication_month / day`；`ORDER_BY` 按 spec 第 4.6 节）、`PublicationSearchRow`、`PublicationSearchReadAdapter`、`PortalPaperReadModel`（加 `publishedDate`）、`PortalPaperResponse`（加 `publishedDate`）
- Modify（PAP-109 产物）：`PublicationResolveDao` 两条 SQL 加月 / 日、`PublicationResolveReadModel`、`PublicationResolutionResp`（加 `String publishedDate`，`@Builder` 字段）、`PublicationEndpointImpl`
- Test: `PublicationDetailReadAdapterIT`、`PublicationSearchReadAdapterIT`、`PortalPublicationControllerIT`、`PublicationEndpointIT`

- [ ] **Step 1: 写失败的测试**

```java
// PublicationSearchReadAdapterIT
@Test void sort_by_year_puts_less_precise_dates_after_precise_ones()
// 同年 2021、2021-03、2021-03-05、2021-12 → 顺序 2021-12、2021-03-05、2021-03、2021
@Test void cards_carry_published_date_in_reduced_iso()

// PublicationDetailReadAdapterIT
@Test void dates_use_reduced_iso_by_precision()                     // published "2023"、pmcrelease "2022-05"

// PortalPublicationControllerIT / PublicationEndpointIT
@Test void year_only_publication_has_no_fabricated_month_day()      // $.publishedDate == "2023"；$.dates[0].date == "2023"
```

- [ ] **Step 2: 跑测试确认失败**　Expected: FAIL。
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过**　Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest :patra-api:patra-catalog:patra-catalog-adapter:integrationTest`　Expected: PASS。
- [ ] **Step 5: 提交（先征得用户同意）**　`feat(catalog): 门户与解析接口按实际精度输出出版日期，检索按日期排序区分精度`

---

### Task 13: e2e 改写

**Files:**
- Rename + Modify: `{boot}/e2eTest/.../integration/publication/PubmedUpdateSyncE2E.java` → `PubmedFileImportE2E.java`
- 夹具：`{boot}/testFixtures/resources/pubmed/pubmed26n9000-baseline.xml`、`pubmed26n9001.xml` 改名为合法文件名（如 `pubmed26n9000.xml`、`pubmed26n9001.xml`，gzip 后由 WireMock 提供）并各配 `.md5`；在 9000 里加一篇只到年、一篇带 `Season`、一篇日不合法、一篇年份 1700

- [ ] **Step 1: 写场景**：BASELINE 9000 `ALL` → UPDATE 9001 覆盖 + 删除 → 重放 BASELINE 9000（`reprocess=true`）不回退、软删保持 → `pubmedReparseJob`；断言每步计数（`skipped_stale`、`skipped_invalid = 1`）与库内日期精度。
- [ ] **Step 2: 跑**　Run: `./gradlew :patra-api:patra-catalog:patra-catalog-boot:e2eTest --tests "*PubmedFileImportE2E*"`　Expected: PASS（若因前序任务的缺陷失败，回到对应任务修）。
- [ ] **Step 3: 提交（先征得用户同意）**　`test(catalog): e2e 覆盖 baseline、update、重放与本地重算`

---

### Task 14: README 与全量门控

**Files:**
- Modify: `patra-api/patra-catalog/README.md`（导入流水线、来源表、守卫规则、XXL 参数、runbook 摘要）

- [ ] **Step 1: 更新 README**
- [ ] **Step 2: 门控**

```bash
./gradlew spotlessApply
./gradlew check
./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest :patra-api:patra-catalog:patra-catalog-adapter:integrationTest :patra-api:patra-catalog:patra-catalog-boot:e2eTest
```

Expected: 全部 BUILD SUCCESSFUL。

- [ ] **Step 3: 提交（先征得用户同意）**　`docs(catalog): 记录可重放导入流水线`
- [ ] **Step 4: 转计划二**（`docs/patra/plans/2026-10-24-catalog-citation-export.md`）
