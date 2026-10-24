# catalog 引用导出与复制引用 Implementation Plan（PAP-110 计划二 / 二）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task（用户已定：本会话内联执行）. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按文献 ID 流式导出 RIS / BibTeX / CSV / CSL-JSON（≤ 5,000），并用 citeproc-java + 官方 CSL 样式生成 GB/T 7714-2015、Vancouver、APA 7、AMA 11 引用文本（≤ 100）。

**Architecture:** `CitationRecordReadPort` 每批 ≤ 500 个 ID、两条 SQL 读出 `CitationRecord`；`CitationExportPort` 打开有状态的写出器逐批写；`CitationFormatterPort` 用一个请求级 `CSL` 实例渲染。控制器返回 `StreamingResponseBody`，导出服务在响应线程里逐批读写。

**Tech Stack:** Java 25、Spring Boot 4.0.8、citeproc-java 3.5.2、`org.citationstyles:styles` / `locales` 26.8、Spring MVC `StreamingResponseBody`、PostgreSQL 17（Testcontainers）、MockMvc、JUnit 5 + AssertJ + Mockito。

**Spec:** `docs/patra/specs/2026-10-24-catalog-citation-data-export-design.md`（第 6、7 节）

**前置**：计划一已完成（`cat_publication` 的页码、缩写、月 / 日列，`Pagination`、`ArticleNumber`、`PublicationDate.toIsoString()`）。

## Global Constraints

- 模块缩写同计划一。
- 导出：`ids` 1 ~ 5,000、`format ∈ {ris, bibtex, csv, csl-json}`、`name` ≤ 100 字；每批 500；不存在与软删跳过；请求顺序、去重。
- 引用：`ids` 1 ~ 100、`style ∈ {gb-t-7714-2015, vancouver, apa-7, ama-11}`；编号样式按请求顺序，APA 按样式顺序；返回 `label` / `text`。
- 样式与 locale：`china-national-standard-gb-t-7714-2015-numeric` / `nlm-citation-sequence` / `apa` / `american-medical-association`，全部 `en-US`。
- 文件名 `patra-<name>-<YYYY-MM-DD>.<ext>`，`Clock` 在 `Asia/Shanghai`；`ContentDisposition` + UTF-8。
- Content-Type 与扩展名照 spec 第 6.1 节表格；RIS 行尾 CRLF；CSV 带 BOM、CRLF。
- 依赖只加在 `patra-catalog-infra`，版本进 `gradle/libs.versions.toml`；不单独声明 `jbibtex` / `antlr4-runtime` / `commons-lang3` / `commons-text`。
- `///` JavaDoc、record 工厂规则、`List.copyOf`、测试命名、提交规则同计划一（**每个提交先征得用户同意**）。

## Review Focus

1. 作者为空的文献（PubMed 里有）：RIS 不写 `AU`、BibTeX 不写 `author`、CSL 无 `author`、引用不崩 → Task 2、Task 3 测试。
2. 标题含内联标记（`CO<sub>2</sub>`）与 BibTeX 特殊字符（`&`、`%`、`_`）→ Task 3 测试。
3. 请求里全部 ID 都不存在：导出得到只有表头 / 空数组的合法文件，引用返回 `entries: []` → Task 4、Task 5 测试。
4. `name` 只有非法字符（`///`）→ 文件名回落 `export` → Task 5 测试。
5. 期刊缩写三级兜底（ISO → MedlineTA → 全称）→ Task 1 IT。

---

### Task 1: `CitationRecord` 读端

**Files:**
- Create: `{domain}/main/java/dev/patra/catalog/domain/model/read/citation/CitationRecord.java`、`CitationAuthor.java`
- Create: `{domain}/main/java/dev/patra/catalog/domain/port/read/CitationRecordReadPort.java`
- Create: `{infra}/main/java/dev/patra/catalog/infra/persistence/dao/CitationRecordDao.java`、`{infra}/main/java/dev/patra/catalog/infra/adapter/read/CitationRecordRow.java`、`CitationAuthorRow.java`、`CitationRecordReadAdapter.java`
- Test: `{infra}/integrationTest/java/dev/patra/catalog/infra/adapter/read/CitationRecordReadAdapterIT.java`

**Interfaces:**
- Produces:
  - `@Builder record CitationRecord(Long id, String pmid, String pmcid, String doi, String title, List<CitationAuthor> authors, String journalTitle, String journalAbbreviation, String volume, String issue, Pagination pagination, String articleNumber, PublicationDate publishedDate, String language)`
  - `record CitationAuthor(String family, String given, String initials, String collective)`，`of()`
  - `List<CitationRecord> CitationRecordReadPort.findByIds(Collection<Long> ids)`：只返回存在且未软删的，顺序不保证；入参 ≤ 500（超过抛 `IllegalArgumentException`）；空入参返回 `List.of()`。
  - Dao：`findRecords(Long[] ids)`（主行：文献 + `cat_venue` + `cat_venue_instance` + PMCID / PII 子查询，`deleted_at IS NULL`）、`findAuthors(Long[] ids)`（`ORDER BY publication_id, author_order`）。
  - 适配器：`title` 用 `InlineMarkup.toPlainText`；缩写兜底；`articleNumber = ArticleNumber.of(pii, pagination)`；`publishedDate` 由 `publication_year / month / day` 构造。

- [ ] **Step 1: 写失败的 IT**（`@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")`）

```java
@Test void should_load_500_records_with_two_statements()        // getPrepareStatementCount() 增量 == 2
@Test void should_skip_soft_deleted_and_missing()
@Test void should_fall_back_journal_abbreviation_iso_then_medline_ta_then_title()   // Review Focus 5
@Test void should_derive_article_number_only_without_pagination()
@Test void should_strip_inline_markup_from_title()
@Test void should_keep_author_order_and_collective_author()
```

- [ ] **Step 2: 跑测试确认失败**　Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest --tests "*CitationRecordReadAdapterIT*"`　Expected: FAIL（编译失败）。
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过**　Expected: PASS。
- [ ] **Step 5: 提交（先征得用户同意）**　`feat(catalog): 新增按 ID 批量读取引用记录的读端`

---

### Task 2: `CslItemMapper` 与 `CiteprocCitationFormatterAdapter`

**Files:**
- Modify: `gradle/libs.versions.toml`（`citeproc-java = "3.5.2"`、`citation-styles = "26.8"` 及三个 library 别名）、`build-logic/src/main/kotlin/PatraDependencyManagement.kt`（若该文件按别名登记约束则同步）、`patra-api/patra-catalog/patra-catalog-infra/build.gradle.kts`（`implementation` 三个依赖）
- Create: `{domain}/main/java/dev/patra/catalog/domain/model/enums/CitationStyle.java`（`GB_T_7714_2015("gb-t-7714-2015")`、`VANCOUVER("vancouver")`、`APA_7("apa-7")`、`AMA_11("ama-11")`，`fromCode`）
- Create: `{domain}/main/java/dev/patra/catalog/domain/model/read/citation/CitationEntry.java`（`record CitationEntry(String label, String text)`，`of()`）
- Create: `{domain}/main/java/dev/patra/catalog/domain/port/citation/CitationFormatterPort.java`
- Create: `{infra}/main/java/dev/patra/catalog/infra/adapter/citation/CslItemMapper.java`、`CiteprocCitationFormatterAdapter.java`
- Test: `{infra}/test/java/dev/patra/catalog/infra/adapter/citation/CslItemMapperTest.java`、`CiteprocCitationFormatterAdapterTest.java`、夹具 `{infra}/test/resources/citation/expected/{gb-t-7714-2015,vancouver,apa-7,ama-11}.txt`

**Interfaces:**
- Produces:
  - `List<CitationEntry> CitationFormatterPort.format(CitationStyle style, List<CitationRecord> records)`
  - `CSLItemData CslItemMapper.toCsl(CitationRecord)`（字段照 spec 第 7.2 节；`issued` 按精度；作者为空时不设 `author`）

- [ ] **Step 1: 改构建脚本并确认依赖可解析**　Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:dependencies --configuration runtimeClasspath | grep -E "citeproc|citationstyles"`　Expected: 三个坐标都出现。（改了 `*.gradle.kts`，完成后提醒用户在 IDEA 主检出里「加载 Gradle 更改」。）
- [ ] **Step 2: 写失败的测试**

```java
// CslItemMapperTest
@Test void should_map_year_only_date_parts()            // [[2023]]
@Test void should_map_year_month_date_parts()           // [[2022, 10]]
@Test void should_use_article_number_as_page_and_number_when_no_pagination()
@Test void should_map_collective_author_as_literal()
@Test void should_omit_author_when_empty()              // Review Focus 1

// CiteprocCitationFormatterAdapterTest（三篇样例 = spec 第 7.4 节）
@ParameterizedTest void should_match_fixture(CitationStyle style)
// 每行 = label + "\t" + text，与 expected/<code>.txt 逐行相等
@Test void should_render_gb_t_with_et_al_not_deng()
@Test void should_keep_request_order_for_numeric_styles()
@Test void should_return_apa_in_alphabetical_order_with_null_label()
@Test void should_return_empty_for_empty_input()
```

夹具内容先用 spike 的输出生成，再逐条对照样式指南核对（核对记录写在夹具文件同目录的 `README.md` 一行一条）。

- [ ] **Step 3: 跑测试确认失败**　Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:test --tests "*CslItemMapperTest*" --tests "*CiteprocCitationFormatterAdapterTest*"`　Expected: FAIL（编译失败）。
- [ ] **Step 4: 实现**（每次 `format` 新建 `CSL(new ListItemDataProvider(items), styleId, "en-US")`；`registerCitationItems` 按请求顺序；`html` 渲染取 `<div class="csl-left-margin">` 文本作 `label`，`text` 渲染去掉开头 `label` 后 `strip()`；无 `csl-left-margin` 时 `label = null`）
- [ ] **Step 5: 跑测试确认通过**　Expected: PASS。
- [ ] **Step 6: 提交（先征得用户同意）**　`feat(catalog): 用 citeproc-java 与官方 CSL 样式生成四种引用`

---

### Task 3: 四个导出写出器

**Files:**
- Create: `{domain}/main/java/dev/patra/catalog/domain/model/enums/ExportFormat.java`（`RIS("ris", "application/x-research-info-systems; charset=UTF-8", "ris")`、`BIBTEX("bibtex", "application/x-bibtex; charset=UTF-8", "bib")`、`CSV("csv", "text/csv; charset=UTF-8", "csv")`、`CSL_JSON("csl-json", "application/vnd.citationstyles.csl+json", "json")`，`fromCode`）
- Create: `{domain}/main/java/dev/patra/catalog/domain/port/citation/CitationExportPort.java`、`CitationExportWriter.java`
- Create: `{infra}/main/java/dev/patra/catalog/infra/adapter/citation/CitationExportAdapter.java`、`RisExportWriter.java`、`BibtexExportWriter.java`、`CsvExportWriter.java`、`CslJsonExportWriter.java`
- Test: 四个 `*ExportWriterTest.java`，夹具 `{infra}/test/resources/citation/export/sample.{ris,bib,csv,json}`

**Interfaces:**
- Produces:
  - `CitationExportWriter CitationExportPort.open(ExportFormat format, OutputStream out)`
  - `interface CitationExportWriter { void begin() throws IOException; void write(List<CitationRecord> records) throws IOException; void end() throws IOException; }`（不关闭 `out`）
  - CSL-JSON 写出器复用 Task 2 的 `CslItemMapper` 与 citeproc-java 的 `toJson`

- [ ] **Step 1: 写失败的测试**（三篇样例，分两次 `write` 写入，断言与夹具逐字节相等）

```java
// RisExportWriterTest
@Test void should_write_full_record_with_crlf()
@Test void should_write_da_by_precision()                   // 2021/10/14/、2022/10//、2023///
@Test void should_write_article_number_as_sp_and_c7()
@Test void should_write_pmid_as_an_and_m2_and_pubmed_url()
@Test void should_skip_au_when_no_authors()                 // Review Focus 1
// BibtexExportWriterTest
@Test void should_omit_month_for_year_precision()
@Test void should_escape_tex_specials_and_brace_title()     // Review Focus 2
@Test void should_use_double_dash_page_range()
// CsvExportWriterTest
@Test void should_start_with_bom_and_header_once_across_batches()
@Test void should_quote_fields_with_comma_quote_newline()
// CslJsonExportWriterTest
@Test void should_write_single_json_array_across_batches()
@Test void should_write_empty_array_when_no_records()       // Review Focus 3
```

- [ ] **Step 2: 跑测试确认失败**　Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:test --tests "*ExportWriterTest*"`　Expected: FAIL。
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过**　Expected: PASS。
- [ ] **Step 5: 提交（先征得用户同意）**　`feat(catalog): 新增 RIS、BibTeX、CSV、CSL-JSON 导出写出器`

---

### Task 4: 应用层导出与引用服务

**Files:**
- Create: `{app}/main/java/dev/patra/catalog/app/usecase/portal/query/PortalCitationQueryService.java`
- Create: `{app}/main/java/dev/patra/catalog/app/usecase/portal/query/PortalPublicationExportService.java`
- Test: `{app}/test/java/dev/patra/catalog/app/usecase/portal/query/PortalCitationQueryServiceTest.java`、`PortalPublicationExportServiceTest.java`

**Interfaces:**
- Consumes: Task 1 `CitationRecordReadPort`、Task 2 `CitationFormatterPort`、Task 3 `CitationExportPort`。
- Produces:
  - `List<CitationEntry> PortalCitationQueryService.format(List<Long> ids, CitationStyle style)`：去重、调一次读端口、按请求顺序排好再交给格式化端口（APA 的重排在端口里发生）。`@Transactional(readOnly = true)`。
  - `void PortalPublicationExportService.export(List<Long> ids, ExportFormat format, OutputStream out)`：去重后按 500 一批；每批经一个独立的只读事务读取（用 `TransactionTemplate` 或拆一个 `@Transactional(readOnly = true)` 的批读方法到同类以外的 bean）、按请求顺序排好、`write`；`begin` / `end` 各一次。

- [ ] **Step 1: 写失败的单测**

```java
// PortalPublicationExportServiceTest（mock 端口）
@Test void should_read_5000_ids_in_10_batches_of_at_most_500()
@Test void should_write_batches_in_request_order_and_skip_missing()
@Test void should_begin_and_end_once_even_when_all_missing()        // Review Focus 3
@Test void should_deduplicate_ids_before_batching()
// PortalCitationQueryServiceTest
@Test void should_pass_records_in_request_order_to_formatter()
@Test void should_return_empty_entries_when_all_missing()
```

- [ ] **Step 2: 跑测试确认失败**　Run: `./gradlew :patra-api:patra-catalog:patra-catalog-app:test --tests "*PortalPublicationExportServiceTest*" --tests "*PortalCitationQueryServiceTest*"`　Expected: FAIL。
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过**　Expected: PASS。
- [ ] **Step 5: 提交（先征得用户同意）**　`feat(catalog): 新增分批导出与复制引用的应用服务`

---

### Task 5: 门户接口 `export` 与 `citations`

**Files:**
- Create: `{adapter}/main/java/dev/patra/catalog/adapter/rest/portal/request/PortalPublicationExportRequest.java`、`PortalCitationRequest.java`
- Create: `{adapter}/main/java/dev/patra/catalog/adapter/rest/portal/response/PortalCitationResponse.java`
- Create: `{adapter}/main/java/dev/patra/catalog/adapter/rest/portal/ExportFileNames.java`
- Modify: `{adapter}/main/java/dev/patra/catalog/adapter/rest/portal/PortalPublicationController.java`
- Test: `{adapter}/test/java/dev/patra/catalog/adapter/rest/portal/ExportFileNamesTest.java`、`{adapter}/integrationTest/java/dev/patra/catalog/adapter/rest/portal/PortalPublicationExportControllerIT.java`（MockMvc）、`PortalPublicationControllerIT.java`（引用）

**Interfaces:**
- Produces:
  - `record PortalPublicationExportRequest(@NotEmpty @Size(max = 5000) List<@Positive Long> ids, @NotBlank String format, @Size(max = 100) String name)`
  - `record PortalCitationRequest(@NotEmpty @Size(max = 100) List<@Positive Long> ids, @NotBlank String style)`
  - `record PortalCitationResponse(String style, List<Entry> entries)`，嵌套 `record Entry(String label, String text)`
  - `static String ExportFileNames.of(String name, ExportFormat format, LocalDate date)`
  - `@PostMapping("/export") ResponseEntity<StreamingResponseBody> export(@Valid @RequestBody PortalPublicationExportRequest)`
  - `@PostMapping("/citations") PortalCitationResponse citations(@Valid @RequestBody PortalCitationRequest)`
  - 未知 `format` / `style`：控制器里 `fromCode` 抛出的领域异常带 `StandardErrorTrait` 映射 422（或用 `@Pattern` 在请求上拦，二选一，测试只断言 422）

- [ ] **Step 1: 写失败的测试**

```java
// ExportFileNamesTest
@Test void should_build_name_with_date_and_extension()        // ("组会", RIS, 2026-10-24) → "patra-组会-2026-10-24.ris"
@Test void should_strip_illegal_chars_and_collapse_spaces()   // "a/b  c?" → "patra-ab-c-…"
@Test void should_fall_back_to_export_when_name_empty_after_strip()   // Review Focus 4

// PortalPublicationExportControllerIT（@WebMvcTest + MockMvc；mock 导出服务写固定字节；Clock 固定）
@Test void should_stream_ris_with_headers()
// mockMvc.perform(post(...)).andExpect(request().asyncStarted()) → asyncDispatch → 200、
// Content-Type 以 application/x-research-info-systems 开头、Content-Disposition 含 filename*=UTF-8''patra-%E7%BB%84%E4%BC%9A-2026-10-24.ris、正文等于 mock 写入的字节
@ParameterizedTest void should_reject_invalid_export_requests_with_422(String body)
// ids 空 / 5001 个 / 0；format "docx"；name 101 字 → 422；verifyNoInteractions(exportService)

// PortalPublicationControllerIT（引用）
@Test void should_return_citation_entries()
@ParameterizedTest void should_reject_invalid_citation_requests_with_422(String body)   // 101 个；style "mla"
```

- [ ] **Step 2: 跑测试确认失败**　Run: `./gradlew :patra-api:patra-catalog:patra-catalog-adapter:test --tests "*ExportFileNamesTest*"` 与 `:integrationTest --tests "*PortalPublicationExportControllerIT*" --tests "*PortalPublicationControllerIT*"`　Expected: FAIL。
- [ ] **Step 3: 实现**（`RestTestClient` 读不到 `StreamingResponseBody`，导出的切片测试只用 MockMvc）
- [ ] **Step 4: 跑测试确认通过**　Expected: PASS。
- [ ] **Step 5: 提交（先征得用户同意）**　`feat(catalog): 新增门户导出与复制引用接口`

---

### Task 6: README、全量门控与交接

**Files:**
- Modify: `patra-api/patra-catalog/README.md`（导出与引用一节；CSL 样式库出处与 CC BY-SA 3.0 许可；citeproc-java Apache-2.0）
- Modify: spec 状态行改「已实施」，回填第 6.4 节手工 Zotero 结果（D6 做完后）

- [ ] **Step 1: 更新 README**
- [ ] **Step 2: 门控**

```bash
./gradlew spotlessApply
./gradlew check
./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest :patra-api:patra-catalog:patra-catalog-adapter:integrationTest :patra-api:patra-catalog:patra-catalog-boot:e2eTest
```

Expected: 全部 BUILD SUCCESSFUL。

- [ ] **Step 3: 给 PAP-107 设计会话（`local_86f27078-59e1-42c5-9476-e0faf9c832d7`）发消息**：`label` / `text` 分开、APA 字母序、`publishedDate` 降精度字符串、导出 `name` 参数与文件名规则。
- [ ] **Step 4: 提交（先征得用户同意）**　`docs(catalog): 记录引用导出与复制引用`
- [ ] **Step 5: 告诉用户 runbook（spec 第 9 节）可以开始，逐步等用户点头**
