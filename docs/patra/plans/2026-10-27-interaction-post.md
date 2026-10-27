# interaction 投稿经验（PAP-122）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans（用户定：本会话内联 TDD 执行，不派子代理）. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 interaction 里新增 `post` 子域：投稿经验的写、改、删、列表、详情、修改历史、期刊汇总，按帖子结构存并登记成 `POST` 对象。

**Architecture:** 六边形四层各建 `post` 包。domain 放字段解析与一致性规则、编辑决策、汇总规则；app 编排底座网关（期刊解析、发表守卫、本地对象登记）与事务；infra 用 `JdbcClient` 原生 SQL（汇总用 PG `percentile_cont`）；adapter 出 REST 与重复错误的额外字段。

**Tech Stack:** Java 25、Spring Boot 4、PostgreSQL 17、Flyway、`JdbcClient`、Jackson 3 `JsonMapper`、JUnit 5 + AssertJ + Mockito、Testcontainers、WireMock。

**Spec:** `docs/patra/specs/2026-10-27-interaction-post-design.md`

## Global Constraints

- 每个方法写 `///` Markdown JavaDoc；不写全类名；record ≤ 4 参数用 `of()`、≥ 5 用 `@Builder`；集合字段紧凑构造器里 `copyOf`（SpotBugs `EI_EXPOSE_REP`）。
- 测试方法 snake_case `should_…`；单元测试 `src/test`，集成测试 `src/integrationTest`，`{类}IT`。
- 时间一律注入 `Clock`；「今天」= `LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")))`。
- 只依赖底座公开包（`domain.subject.port.gateway / port.read / port.user / model.vo / model.enums / model.user / model.read / exception`）；不改 `InteractionArchitectureTest`、`InteractionMigrationIT`。
- 迁移版本 `20261027153100`、`20261027153200`；表只存用户 ID；schema 内触发器函数 `post.set_updated_at()`。
- 响应里的计数、天数用 `int`（`Long` 会被全局序列化成字符串）；ID 是 `long` → 字符串，符合约定。
- 不提交、不推送（编排约定）；每个任务结束跑该模块测试，`./gradlew spotlessApply` 后再看结果（gjf 会折 `///` 长行）。
- `JdbcClient` 不传 `null` 参数做 `:x IS NULL` 判断（PG 推不出类型）：有无游标写成两条 SQL。

## Review Focus

1. 同一作者并发创建同一投稿日期：只成功一条，另一条 409 带已有 ID → Task 12 并发用例。
2. 编辑把投稿日期改成自己另一条经验的日期：409（排除自身）→ Task 9 `should_reject_edit_onto_another_posts_date`。
3. 匿名作者 ID 经旁路泄露（修改历史、用户列表、错误响应）→ Task 12 用原始响应体断言。
4. 分位数恰好 .5：半进位（PG `round(double)` 是银行家舍入）→ Task 4、Task 7 用 10/20/30/40 钉住。
5. 游标指向已删除的经验仍能翻下一页（帖子不物理删除）→ Task 7 `should_page_after_deleted_cursor`。

---

### Task 1: 子域骨架与正文 `PostBody`

**Files:**
- Create: `patra-api/patra-interaction/patra-interaction-{domain,app,infra,adapter}/src/main/java/dev/patra/interaction/{domain,app,infra,adapter}/post/package-info.java`
- Create: `…domain/post/model/vo/PostBody.java`、`…domain/post/exception/InvalidExperienceException.java`
- Test: `…domain/src/test/java/dev/patra/interaction/domain/post/model/vo/PostBodyTest.java`

**Interfaces:**
- Produces: `record PostBody(String value)`；`static final String FIELD = "body"`、`MAX_GRAPHEMES = 2000`；`static Optional<PostBody> parse(String raw, Collection<FieldViolation> violations)`——违规时往 `violations` 加一条并返回空，空白返回空；`InvalidExperienceException(List<FieldViolation>)`（422 `RULE_VIOLATION`，`HasFieldViolations`，至少一条）。

- [ ] **Step 1: 写失败的测试**：`should_normalize_line_endings_nfc_and_trim`（`" a\r\nb\r "` → `"a\nb"`）、`should_treat_blank_as_absent`、`should_accept_2000_graphemes_and_reject_2001`（`TOO_LONG`）、`should_reject_more_than_8000_bytes`（2000 个 5 码点的组合字素 → `TOO_LONG`）、`should_allow_line_feed_and_tab`、`should_reject_control_and_separator_characters`（`\u0007`、` ` → `INVALID_CHARACTER`），断言 `violations` 的 `field == "body"` 与 `code`。
- [ ] **Step 2:** `./gradlew :patra-api:patra-interaction:patra-interaction-domain:test --tests '*PostBodyTest'` → 编译失败。
- [ ] **Step 3: 实现**：照 `domain.collection.policy.TextRules`（NFC、首尾空白含 U+00A0/U+3000、`BreakIterator` 字素、字节 ≤ 字素上限 × 4），先把 `\r\n`、`\r` 换成 `\n`；控制字符放行 `\n`、`\t`。四个 `package-info.java` 一句话说明「投稿经验（按帖子结构存，PAP-122）」。
- [ ] **Step 4:** 同命令 → PASS；再跑 `:patra-api:patra-interaction:patra-interaction-boot:test --tests '*InteractionArchitectureTest'` → PASS（四层都有 `post` 包）。

### Task 2: 字段解析与一致性 `ExperienceDraft`

**Files:**
- Create: `…domain/post/model/enums/{FirstDecision,FinalOutcome,ArticleType,PostType,PostStatus}.java`、`…domain/post/model/vo/{ExperienceInput,ExperienceFields,ExperienceDraft}.java`、`…domain/post/policy/ExperienceRules.java`
- Test: `…domain/src/test/java/dev/patra/interaction/domain/post/model/vo/ExperienceDraftTest.java`、`ExperienceFieldsTest.java`

**Interfaces:**
- Produces:
  - `enum FirstDecision { DESK_REJECT, REJECT_AFTER_REVIEW, MAJOR_REVISION, MINOR_REVISION, ACCEPT }`，`Optional<FinalOutcome> impliedOutcome()`（两种拒稿 → `REJECTED`，`ACCEPT` → `ACCEPTED`，修改类为空），`boolean isTerminal()`。
  - `enum FinalOutcome { ACCEPTED, REJECTED, WITHDRAWN, IN_PROGRESS }`；`enum ArticleType { ORIGINAL_RESEARCH, REVIEW, SYSTEMATIC_REVIEW, CASE_REPORT, SHORT_COMMUNICATION, OTHER }`；`enum PostType { SUBMISSION_EXPERIENCE }`；`enum PostStatus { ACTIVE, DELETED }`。
  - `ExperienceRules`：`ZoneId ZONE = Asia/Shanghai`、`LocalDate EARLIEST = 1990-01-01`、`int MAX_REVIEW_ROUNDS = 10`、`int MAX_RATING = 5`、`Duration EDIT_GRACE = 5 分钟`、`static LocalDate today(Clock)`。
  - `@Builder record ExperienceInput(String submittedOn, String firstDecisionOn, String firstDecision, String finalOutcome, String finalDecisionOn, String articleType, Integer reviewRounds, Integer reviewQuality, Integer editorCommunication, Boolean apcPaid, Boolean previouslyRejected, String body)`。
  - `@Builder record ExperienceFields(LocalDate submittedOn, LocalDate firstDecisionOn, FirstDecision firstDecision, FinalOutcome finalOutcome, LocalDate finalDecisionOn, ArticleType articleType, Integer reviewRounds, Integer reviewQuality, Integer editorCommunication, Boolean apcPaid, Boolean previouslyRejected)`：`int firstRoundDays()`、`Integer totalDays()`（spec 4.4）、`String submittedMonth()`（`yyyy-MM`）。
  - `record ExperienceDraft(ExperienceFields fields, PostBody body)`（`body` 可空）：`static ExperienceDraft of(…)`、`static ExperienceDraft parse(ExperienceInput input, LocalDate today)`，有错一次抛 `InvalidExperienceException`。

- [ ] **Step 1: 写失败的测试**（`today = 2026-10-27`，合法基准：投稿 2026-03-02、首轮 2026-04-13 大修、录用、最终 2026-06-01、原创研究）：
  - `should_parse_valid_input`：字段全部按值解析，`firstRoundDays() == 42`、`totalDays() == 91`、`submittedMonth() == "2026-03"`。
  - `should_report_all_missing_required_fields_at_once`：空输入 → `errors` 的字段集合正好是 `submittedOn, firstDecisionOn, firstDecision, finalOutcome, articleType`，码都是 `REQUIRED`。
  - `should_reject_malformed_date_and_unknown_enum`（`"2026-13-01"`、`"MAYBE"` → `INVALID`）。
  - `should_reject_future_and_too_early_dates`（`2026-10-28` → `FUTURE`；`1989-12-31` → `TOO_EARLY`；`2026-10-27` 通过）。
  - `should_reject_first_decision_before_submission`（`BEFORE_SUBMISSION`）、`should_reject_final_before_first_decision`（`BEFORE_FIRST_DECISION`）。
  - 4.2 每行一例：`should_require_rejected_after_desk_or_review_reject`、`should_require_accepted_after_first_round_accept`（`finalOutcome`/`INCONSISTENT`）；`should_reject_final_date_when_in_progress_or_terminal`（`finalDecisionOn`/`NOT_APPLICABLE`）；`should_reject_rounds_and_quality_for_desk_reject`（两条 `NOT_APPLICABLE`）；`should_require_single_round_for_terminal_after_review`（`reviewRounds = 2` → `INCONSISTENT`，`1` 通过）。
  - `should_reject_out_of_range_numbers`（轮数 0、11，评分 0、6 → `OUT_OF_RANGE`）。
  - `should_skip_cross_rules_when_a_field_failed`：首轮日期写法错时不再报 `BEFORE_SUBMISSION`。
  - `should_include_body_violation_with_field_errors`：超长正文与缺投稿日期同时出现。
  - `ExperienceFieldsTest.should_compute_total_days_per_outcome`：首轮终局 → 等于首轮；修改类无最终日期 → null。
  - `ExperienceRulesTest.should_use_beijing_date`：`Instant 2026-10-26T16:00:00Z` → `2026-10-27`，`15:59:59Z` → `2026-10-26`。
- [ ] **Step 2:** 跑 `--tests '*ExperienceDraftTest' --tests '*ExperienceFieldsTest' --tests '*ExperienceRulesTest'` → 编译失败。
- [ ] **Step 3: 实现**：逐字段解析（日期 `LocalDate.parse` 捕获 `DateTimeParseException` → `INVALID`；枚举按名字，大小写敏感），再判 4.2 与日期先后（参与字段都成功才判），正文交给 `PostBody.parse`。字段名与码用 spec 4.3 的原文。
- [ ] **Step 4:** 同命令 → PASS。

### Task 3: 帖子聚合与编辑决策 `PostAggregate`

**Files:**
- Create: `…domain/post/model/aggregate/PostAggregate.java`、`…domain/post/model/vo/{PostId,PostRevision,PostEdit}.java`、`…domain/post/model/enums/EditKind.java`、`…domain/post/exception/{PostNotFoundException,PostForbiddenException}.java`
- Test: `…domain/src/test/java/dev/patra/interaction/domain/post/model/aggregate/PostAggregateTest.java`、`…/exception/PostExceptionsTest.java`

**Interfaces:**
- Produces:
  - `record PostId(long value)`：`of(long)`、`static PostId parseCursor(String raw)`（null/空白 → null；不合 `[1-9][0-9]{0,18}` → `InvalidPostRequestException`，同文件建该异常：422 `RULE_VIOLATION`）。
  - `@Builder(toBuilder = true) record PostAggregate(PostId postId, PostType type, SubjectId subjectId, long authorId, boolean anonymous, ExperienceFields fields, PostBody body, PostStatus status, Instant createdAt, Instant revisedAt)`：`static PostAggregate publish(PostId id, SubjectId journal, long authorId, boolean anonymous, ExperienceDraft draft, Instant now)`、`boolean isAuthor(long userId)`、`boolean isDeleted()`、`boolean edited()`（`revisedAt != null`）、`Instant contentWrittenAt()`、`PostEdit edit(ExperienceDraft draft, Instant now)`（已删除抛 `IllegalStateException`）。
  - `enum EditKind { NONE, OVERWRITE, REVISE }`；`record PostRevision(ExperienceFields fields, PostBody body, Instant writtenAt, Instant replacedAt)`（`of`）；`record PostEdit(EditKind kind, PostAggregate after, PostRevision replaced)`（`replaced` 只在 `REVISE` 非空，`of`）。
  - `PostNotFoundException.notFound()`（404 `NOT_FOUND`）、`.deleted()`（另带 `SubjectErrorTrait.SUBJECT_UNAVAILABLE`），消息「投稿经验不存在或已被删除」；`PostForbiddenException()`（403 `FORBIDDEN`，「只有作者能修改或删除这条投稿经验」）。

- [ ] **Step 1: 写失败的测试**：`should_not_change_anything_when_content_is_equal`（NONE，`after == 原聚合`）；`should_overwrite_without_revision_within_grace`（`created + 4m59s` → OVERWRITE，`revisedAt` 仍为 null）；`should_record_revision_from_five_minutes`（`created + 5m` → REVISE，`replaced.writtenAt == createdAt`、`replaced.replacedAt == now`、`after.revisedAt == now`）；`should_date_next_revision_from_previous_revision`（第二次 REVISE 的 `writtenAt` = 第一次的 `revisedAt`）；`should_refuse_editing_deleted_post`；`should_identify_author`；`PostExceptionsTest`：两种 404 的特征、403、`parseCursor` 的三种输入。
- [ ] **Step 2:** 跑这两个测试类 → 编译失败。
- [ ] **Step 3: 实现**（「没变」= `fields` 与 `body` 的 `equals`；宽限期 `now.isBefore(createdAt.plus(EDIT_GRACE))`）。
- [ ] **Step 4:** → PASS。

### Task 4: 汇总规则 `ExperienceSummaryRules`

**Files:**
- Create: `…domain/post/model/read/{ExperienceStats,ExperienceSummary}.java`、`…domain/post/model/enums/SummaryWindow.java`、`…domain/post/policy/ExperienceSummaryRules.java`
- Test: `…domain/src/test/java/dev/patra/interaction/domain/post/policy/ExperienceSummaryRulesTest.java`、`…/model/enums/SummaryWindowTest.java`

**Interfaces:**
- Produces:
  - `@Builder record ExperienceStats(long allCount, long windowCount, long firstRoundCount, Double firstRoundP25, Double firstRoundMedian, Double firstRoundP75, long deskRejectCount, Double deskRejectMedian, long accepted, long rejected, long withdrawn, long inProgress)`。
  - `@Builder record ExperienceSummary(int count, int allCount, boolean sufficient, FirstRound firstRound, DeskReject deskReject, Outcomes outcomes, Acceptance acceptance)`，内嵌 `record FirstRound(int median, int p25, int p75, int sampleSize)`、`record DeskReject(int median, int sampleSize)`、`record Outcomes(int accepted, int rejected, int withdrawn, int inProgress)`、`record Acceptance(double rate, int decidedCount)`（各 `of`）。
  - `ExperienceSummaryRules`：`int MIN_SAMPLES = 5`、`static ExperienceSummary summarize(ExperienceStats stats)`、`static int roundDays(double days)`（半进位）。
  - `enum SummaryWindow { THREE_YEARS("3y"), ALL("all") }`：`static SummaryWindow fromParam(String)`（null → `THREE_YEARS`，未知 → `InvalidPostRequestException`）、`LocalDate since(LocalDate today)`（`3y` = `today.minusYears(3)`；`all` = `ExperienceRules.EARLIEST`）、`String param()`。

- [ ] **Step 1: 写失败的测试**：`should_return_only_counts_below_five`（windowCount 4 → `sufficient false`，其余 null，`count 4`、`allCount 9`）；`should_return_full_summary_from_five`；`should_round_half_up`（`roundDays(17.5) == 18`、`32.5 → 33`、`42.49 → 42`）；`should_omit_empty_groups`（首轮样本 0 → `firstRound` null；直接拒稿样本 0 → null）；`should_count_only_accepted_and_rejected_in_acceptance`（录用 6、拒稿 6、撤稿 1、进行中 2 → `rate 0.5`、`decidedCount 12`）；`should_omit_acceptance_without_decided`；`SummaryWindowTest`：`since` 两种、`fromParam`。
- [ ] **Step 2:** → 编译失败。
- [ ] **Step 3: 实现**：`roundDays` 用 `BigDecimal.valueOf(days).setScale(0, RoundingMode.HALF_UP).intValueExact()`。
- [ ] **Step 4:** → PASS；`./gradlew :patra-api:patra-interaction:patra-interaction-domain:check` → PASS（领域纯净性）。

### Task 5: 迁移

**Files:**
- Create: `…infra/src/main/resources/db/migration/post/V20261027153100__create_post_schema.sql`、`V20261027153200__create_post_tables.sql`
- Test: `…infra/src/integrationTest/java/dev/patra/interaction/infra/post/PostMigrationIT.java`

**Interfaces:**
- Produces：spec 第 5 节的三张表、约束与索引。约束名：`ck_post_type`、`ck_post_status`、`ck_post_deleted`、`ck_experience_first_decision`、`ck_experience_final_outcome`、`ck_experience_article_type`、`ck_experience_review_rounds`（1–10）、`ck_experience_review_quality`、`ck_experience_editor_communication`（1–5）、`ck_experience_first_after_submission`、`ck_experience_final_after_first`、`ck_experience_in_progress_open`、`ck_revision_no`（> 0）；外键 `fk_experience_post`、`fk_revision_post`；索引 `idx_post_subject_active`（`(subject_id) WHERE status = 'ACTIVE'`）、`idx_post_author`；`trg_post_updated_at`。每列英文 `COMMENT ON`。

- [ ] **Step 1: 写失败的测试**（`@SpringBootTest(classes = InteractionITBootstrap.class)` 同 `InteractionMigrationIT`；每例前 `TRUNCATE post.revision, post.submission_experience, post.post`）：`should_create_post_tables`（三张表在 `post` schema）；`should_enforce_post_checks`、`should_enforce_experience_checks`：逐条插入违规行，断言 `DataIntegrityViolationException` 的消息含**对应约束名**；`should_require_deleted_at_with_deleted_status`；`should_touch_updated_at_on_update`。
- [ ] **Step 2:** `./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest --tests '*PostMigrationIT'` → FAIL（表不存在）。
- [ ] **Step 3:** 写两条迁移。
- [ ] **Step 4:** 同命令 → PASS；`--tests '*InteractionMigrationIT'` → PASS（新 schema 在白名单里）。

### Task 6: 写端仓储 `PostRepository`

**Files:**
- Create: `…domain/post/port/repository/PostRepository.java`、`…infra/post/persistence/{PostRepositoryAdapter,PostRows}.java`
- Test: `…infra/src/integrationTest/java/dev/patra/interaction/infra/post/persistence/{PostPersistenceITBootstrap,PostPersistenceITSupport,PostRepositoryAdapterIT}.java`

**Interfaces:**
- Consumes: Task 2、3 的类型。
- Produces（`PostRepository`，写方法不开事务）：`PostId nextId()`、`void lockAuthor(long userId)`（`pg_advisory_xact_lock(hashtextextended('post.author:' || userId, 0))`）、`Optional<PostAggregate> find(PostId id)`（含已删除）、`Optional<PostId> findActiveBySubmission(long authorId, SubjectId journal, LocalDate submittedOn)`、`void insert(PostAggregate post)`（两张表）、`void update(PostAggregate post, Instant now)`（正文、扩展表全部字段、`revised_at`）、`void insertRevision(PostId id, PostRevision revision)`（`revision_no` = 现有最大 + 1）、`void markDeleted(PostId id, Instant now)`。
  `PostRows`（包内 `@Component`，注入 `JsonMapper`）：`COLUMNS`、`RowMapper<PostAggregate> postMapper()`、`String writeFields(ExperienceFields)`、`ExperienceFields readFields(String json)`、`static OffsetDateTime utc(Instant)`。

- [ ] **Step 1: 写失败的测试**（启动类只扫描本包；支持类同 `CollectionPersistenceITSupport`，清三张表）：`should_round_trip_post_with_all_fields`（含正文 null、选填全 null 两种）；`should_find_active_by_submission_only`（删除后查不到；别的期刊、别的日期查不到）；`should_update_content_and_revised_at_without_touching_anonymous_or_author`；`should_append_revisions_with_increasing_numbers`（读回 `post.revision` 两行，`fields` jsonb 解析回原值）；`should_mark_deleted`（`status`、`deleted_at`）；`should_block_second_author_lock_until_commit`（关测试事务：线程 A 持锁未提交，线程 B 的 `lockAuthor` 在 A 提交前不返回，用 `CountDownLatch` + `Future.get(timeout)` 断言超时，A 提交后返回）。
- [ ] **Step 2:** `…infra:integrationTest --tests '*PostRepositoryAdapterIT'` → 编译失败。
- [ ] **Step 3:** 实现适配器。ID 由仓储发：`PostId nextId()`（`SnowflakeIdGenerator.getId()` 在 `patra-tool-jpa`，app 不依赖它）。
- [ ] **Step 4:** → PASS。

### Task 7: 读端 `PostReadPort`（列表、历史、统计）

**Files:**
- Create: `…domain/post/port/read/PostReadPort.java`、`…infra/post/persistence/PostReadAdapter.java`
- Test: `…infra/src/integrationTest/java/dev/patra/interaction/infra/post/persistence/PostReadAdapterIT.java`

**Interfaces:**
- Produces（`PostReadPort`）：`List<PostAggregate> pageByJournal(SubjectId journal, PostId after, int limit)`、`List<PostAggregate> pageByAuthor(long authorId, boolean includeAnonymous, PostId after, int limit)`（只 `ACTIVE`；`after` 可空；排序 `submitted_on DESC, created_at DESC, post_id DESC`，游标用行构造器与子查询反查排序键，有无游标两条 SQL）、`List<PostRevision> revisions(PostId id)`（`revision_no` 倒序）、`ExperienceStats summarize(SubjectId journal, LocalDate since)`（spec 第 7 节 SQL；`percentile_cont` 的数组读成 `Double[]`）。

- [ ] **Step 1: 写失败的测试**：`should_order_by_submission_then_publish_time`（同一投稿日的两条按发布时间倒序）；`should_page_with_cursor`（limit 2 翻三页不重不漏）；`should_page_after_deleted_cursor`；`should_exclude_deleted_and_other_journals`；`should_filter_anonymous_for_author_list`；`should_return_revisions_newest_first`；`should_compute_quantiles_like_excel`（非直接拒稿用时 10、20、30、40 天 + 直接拒稿 3、5 天 → P25 17.5、中位 25.0、P75 32.5，直接拒稿中位 4.0；结果计数逐项）；`should_count_window_and_all_separately`（4 年前一条：`allCount` 含、`windowCount` 不含，且不进分位数）。
- [ ] **Step 2:** → 编译失败。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** → PASS。

### Task 8: 写经验 `PublishExperienceHandler` 与重复错误

**Files:**
- Create: `…app/post/usecase/experience/{PublishExperienceCommand,PublishExperienceHandler}.java`、`…app/post/service/ExperienceViews.java`、`…app/post/usecase/query/{AuthorView,ExperienceItemView,JournalRefView,ExperienceDetailView}.java`、`…domain/post/exception/{PostErrorTrait,DuplicateSubmissionException}.java`
- Test: `…app/src/test/java/dev/patra/interaction/app/post/{RecordingTransactions,TestUsers}.java`（照 collection 的同名测试工具）、`…/usecase/experience/PublishExperienceHandlerTest.java`、`…/service/ExperienceViewsTest.java`

**Interfaces:**
- Consumes: `SubjectGateway`、`SubjectReadPort`、`PublishGuardGateway`、`UserDirectoryPort`、`PostRepository`、`CurrentUserPort`、`TransactionOperations`、`Clock`。
- Produces:
  - `record PublishExperienceCommand(String journalKey, ExperienceInput input, boolean anonymous) implements Command<ExperienceDetailView>`（`of`）。
  - `PostErrorTrait { DUPLICATE_SUBMISSION }`；`DuplicateSubmissionException(PostId existing)`：409 `CONFLICT` + `DUPLICATE_SUBMISSION`，`HasFieldViolations` → `[submittedOn / DUPLICATE]`，`PostId existingPostId()`，消息「同一天投这本期刊的经验只能写一条」。
  - `@Builder record AuthorView(long userId, String username, String displayName, String avatarUrl, int avatarPalette, AccountState state, boolean profileVisible)`：`static AuthorView of(UserCard card)`——`profileVisible` 为假时 `username`、`avatarUrl` 置 null。
  - `record JournalRefView(String key, SubjectState state, String title, String abbreviation)`：`static JournalRefView of(SubjectView view)`。
  - `@Builder record ExperienceItemView(PostId postId, ExperienceFields fields, String body, Instant createdAt, boolean edited, AuthorView author, boolean anonymous, boolean mine)`。
  - `@Builder record ExperienceDetailView(PostId postId, boolean deleted, JournalRefView journal, ExperienceItemView experience)`（已删时 `experience` 为 null）。
  - `ExperienceViews`（`@Component`）：`List<ExperienceItemView> items(List<PostAggregate> posts, Long viewer)`（一次 `findCards`：非匿名作者 + 访客自己的匿名经验；没有要查的就不调 identity）、`ExperienceDetailView detail(PostAggregate post, Long viewer)`、`ExperienceDetailView detail(PostAggregate post, UserCard self)`（写入后用 `requireEligible` 返回的名片，不再调 identity）、`JournalRefView journal(SubjectId subjectId)`。

- [ ] **Step 1: 写失败的测试**：
  - `should_publish_in_order`：`events` 依次为 `resolve`、`eligible`、`tx-begin`、`admit`、`lock`、`dup-check`、`insert`、`register`、`tx-end`（各 mock 用 `doAnswer` 记事件）；`registerLocal(POST, id, SubjectOwner.of(U, true), SubjectSnapshot.of(null, "Journal of X", "2026-03", "J X"))`。
  - `should_validate_before_remote_calls`：非法输入 → `InvalidExperienceException`，`verifyNoInteractions(gateway, guard)`。
  - `should_reject_duplicate_with_existing_id`：`findActiveBySubmission` 返回 `PostId.of(77)` → 异常的 `existingPostId() == 77`、`insert` 与 `registerLocal` 未调用。
  - `should_return_detail_with_self_card`：`mine true`、`author` 是 `requireEligible` 的名片、`anonymous` 按命令。
  - `ExperienceViewsTest`：spec 6.5 遮蔽表四行；`should_strip_username_and_avatar_when_profile_hidden`；`should_skip_identity_when_all_anonymous_for_others`。
- [ ] **Step 2:** `:patra-interaction-app:test --tests '*PublishExperienceHandlerTest' --tests '*ExperienceViewsTest'` → 编译失败。
- [ ] **Step 3:** 实现（spec 6.1）。
- [ ] **Step 4:** → PASS。

### Task 9: 编辑与删除

**Files:**
- Create: `…app/post/usecase/experience/{EditExperienceCommand,EditExperienceHandler,DeleteExperienceCommand,DeleteExperienceHandler}.java`
- Test: `…app/src/test/java/dev/patra/interaction/app/post/usecase/experience/{EditExperienceHandlerTest,DeleteExperienceHandlerTest}.java`

**Interfaces:**
- Produces: `record EditExperienceCommand(long postId, ExperienceInput input) implements Command<ExperienceDetailView>`；`record DeleteExperienceCommand(long postId) implements Command<Void>`。

- [ ] **Step 1: 写失败的测试**：
  - 编辑：`should_reject_missing_deleted_and_foreign_posts`（404、404 + `SUBJECT_UNAVAILABLE`、403，三者都不调资格）；`should_check_eligibility_outside_transaction_without_admit`（`verify(guard, never()).admit(...)`）；`should_overwrite_within_grace_without_revision`；`should_record_revision_after_grace`（`insertRevision` 收到旧字段，`writtenAt == createdAt`）；`should_do_nothing_when_unchanged`（`update` 未调用）；`should_reject_edit_onto_another_posts_date`（查重返回另一 ID → 409；返回自身 ID 视为不重复）；`should_update_registry_snapshot_when_month_changes`（`updateLocal(POST, id, ACTIVE, 新月份快照)`；月份不变不调）；`should_recheck_after_lock`（锁内再取到已删除 → 404）。
  - 删除：`should_soft_delete_and_mark_registry_unavailable`（`markDeleted` + `updateLocal(POST, id, UNAVAILABLE, 当前快照)`，都在事务里）；`should_be_idempotent_for_deleted_post`；`should_not_check_eligibility`；`should_reject_foreign_post`（403）；`should_reject_missing_post`（404）。
- [ ] **Step 2:** → 编译失败。
- [ ] **Step 3:** 实现（spec 6.2、6.3；当前快照经 `findIdsByKeys(POST, [id])` + `findByIds` 在事务外取）。
- [ ] **Step 4:** → PASS。

### Task 10: 查询 `ExperienceQueryService`

**Files:**
- Create: `…app/post/usecase/query/{ExperienceQueryService,CursorPage,RevisionVersionView,UserExperienceView}.java`
- Test: `…app/src/test/java/dev/patra/interaction/app/post/usecase/query/ExperienceQueryServiceTest.java`

**Interfaces:**
- Produces:
  - `record CursorPage<T>(List<T> items, String nextCursor)`（`of`，`items` copyOf）；`record RevisionVersionView(Instant writtenAt, boolean current, ExperienceFields fields, String body)`；`record UserExperienceView(JournalRefView journal, ExperienceItemView experience)`。
  - `ExperienceQueryService`：`CursorPage<ExperienceItemView> journalExperiences(String journalKey, String cursor, Integer limit)`（默认 10，1–10）、`ExperienceSummary summary(String journalKey, String window)`、`ExperienceDetailView detail(long postId)`、`List<RevisionVersionView> revisions(long postId)`、`CursorPage<UserExperienceView> userExperiences(long userId, String cursor, Integer limit)`（默认 20，1–20）。

- [ ] **Step 1: 写失败的测试**：`should_return_empty_page_for_unregistered_journal`（不调 `PostReadPort`）；`should_reject_malformed_journal_key_and_limit`（422）；`should_set_next_cursor_only_when_more`（读 `limit + 1`）；`should_summarize_with_window_since`（`3y` → `since = today - 3y`）；`should_return_deleted_detail_with_journal_only`；`should_404_revisions_of_deleted_post`；`should_list_current_version_first`（当前版本 `writtenAt = contentWrittenAt()`、`current true`）；`should_list_all_own_posts_including_anonymous`；`should_list_only_named_posts_for_others`（`includeAnonymous false`）；`should_return_empty_when_profile_hidden_or_omitted`。
- [ ] **Step 2:** → 编译失败。
- [ ] **Step 3:** 实现（spec 6.4）。
- [ ] **Step 4:** → PASS；`./gradlew :patra-api:patra-interaction:patra-interaction-app:check` → PASS。

### Task 11: REST 与重复错误的额外字段

**Files:**
- Create: `…adapter/post/rest/{JournalPostController,PostController,UserPostController,PostProblemFieldContributor}.java`、`…adapter/post/rest/request/ExperienceRequest.java`、`…adapter/post/rest/response/{AuthorResponse,JournalRefResponse,ExperienceResponse,ExperienceDetailResponse,UserExperienceResponse,CursorPageResponse,ExperienceSummaryResponse,RevisionResponse,RevisionsResponse}.java`
- Test: `…adapter/src/test/java/dev/patra/interaction/adapter/post/rest/PostProblemFieldContributorTest.java`

**Interfaces:**
- Produces：spec 第 8 节的路径；JSON 形状见共享笔记第 3 节（Task 13 写）。
  - `ExperienceRequest`：spec 4.1 的字段（日期、枚举为字符串）+ `Boolean anonymous`；`ExperienceInput toInput()`。
  - `ExperienceResponse` 平铺 `ExperienceItemView`：`postId`、11 个字段、`firstRoundDays`、`totalDays`、`body`、`createdAt`、`edited`、`author`、`anonymous`、`mine`。
  - `ExperienceSummaryResponse`：`window`、`count`、`allCount`、`sufficient`、`firstRound`、`deskReject`、`outcomes`、`acceptance`（全部 `int` / `double`）。
  - `PostProblemFieldContributor implements ProblemFieldContributor`（`@Component`）：`DuplicateSubmissionException` 时放 `existingPostId` = ID 的字符串。

- [ ] **Step 1: 写失败的测试**：`should_add_existing_post_id_as_string`、`should_ignore_other_exceptions`。
- [ ] **Step 2:** `:patra-interaction-adapter:test --tests '*PostProblemFieldContributorTest'` → 编译失败。
- [ ] **Step 3:** 实现控制器（`@Tag`，写法同 collection；`POST` 201、`DELETE` 204）与响应映射。
- [ ] **Step 4:** → PASS；`:patra-interaction-boot:test` → PASS（架构测试）。

### Task 12: 流程集成测试（全部 AC）

**Files:**
- Modify: `…boot/src/integrationTest/java/dev/patra/interaction/support/FakeCatalog.java`（末尾追加期刊）
- Create: `…boot/src/integrationTest/java/dev/patra/interaction/post/ExperienceFlowIT.java`

**Interfaces:**
- Produces（`FakeCatalog`）：`void putJournal(long id, String nlmId, String title, String abbreviation)`、`void softDeleteJournal(long id)`；`transform` 先按 URL 含 `/venues/` 分流：`by-ids` 读 `ids`、`by-nlm-ids` 读 `nlmIds`，行字段 `key, exists, state, id(字符串), nlmId, title, abbreviatedTitle`；`clear()` 一并清期刊。

- [ ] **Step 1: 写失败的测试**（继承 `InteractionFlowITSupport`；用户 A、B 在 `FakeIdentity` 里 ACTIVE、已验证、PUBLIC；期刊 100；日期以北京时间今天为基准倒推）：
  - `should_compute_summary_by_window_and_threshold`：4 条 → `sufficient false`、`count 4`；第 5 条后 `sufficient true`，分位数与结果分布按值断言；一条 4 年前的 → `window=3y` 不计、`window=all` 计、两者 `allCount` 相同；撤稿、进行中不进 `acceptance.decidedCount`。
  - `should_reflect_new_post_in_next_summary`：写前 `count 0`，写后立即 `count 1`。
  - `should_hide_anonymous_author_from_others`：A 匿名写一条；B 与匿名访客取期刊列表、详情、修改历史（A 先在宽限期外改一次——用 `jdbcTemplate` 把 `created_at` 拨回 10 分钟再 PUT）、`/users/{A}/posts`，原始响应体都不含 A 的 ID 字符串；A 自己取详情 → `mine true`、`anonymous true`、`author.userId == A`，主页列表含这条。
  - `should_remove_deleted_post_from_list_and_summary`：删除 204 → 列表、汇总、用户列表都没有；详情 200 `deleted true` 带期刊；修改历史 404 含 `SUBJECT_UNAVAILABLE`；登记表 `POST` 行 `UNAVAILABLE`；再删一次 204。
  - `should_reject_duplicate_submission_date`：同日第二条 409，`traits` 含 `DUPLICATE_SUBMISSION`、`existingPostId` 等于第一条 ID；删掉第一条后同日可再写。
  - `should_allow_only_one_of_concurrent_same_date_posts`：两线程同时 POST → 一个 201、一个 409，库里一行。
  - `should_enforce_eligibility_and_daily_limit`：未验证邮箱 409 `EMAIL_UNVERIFIED`；同一天第 11 条 429 `DAILY_LIMIT_REACHED`；未登录 401；编辑时被封禁 → 409 `ACCOUNT_RESTRICTED`，删除照常 204。
  - `should_register_post_subject_with_owner_and_snapshot`：登记行 `owner_user_id`、`owner_anonymous`、快照 `{"journal":"Journal 100","publishedDate":"2026-03","abbreviation":"J 100"}`（按实际月份）；改投稿月份后快照随之变。
  - `should_record_revision_only_after_grace`：发布后立即改 → `edited false`、历史 1 版；拨回 `created_at` 10 分钟再改 → `edited true`、历史 2 版，当前版本在前。
  - `should_reject_foreign_edit_and_unknown_post`：B 改 A 的 → 403；不存在的 → 404。
  - `should_validate_fields`：缺必填与一致性错误 → 422，`errors` 的字段与码。
- [ ] **Step 2:** `./gradlew :patra-api:patra-interaction:patra-interaction-boot:integrationTest --tests '*ExperienceFlowIT'` → 先在扩 `FakeCatalog` 前 FAIL，扩完后按实现情况修到 PASS。
- [ ] **Step 3:** 修复测试暴露的问题（只改本子域）。
- [ ] **Step 4:** 全部 interaction 集成测试：`…infra:integrationTest`、`…boot:integrationTest` → PASS（`CollectionFlowIT` 等不受影响）。

### Task 13: 文档与门禁

**Files:**
- Modify: `patra-api/patra-interaction/README.md`（第 1 节 `post` 一行、第 4 节加「4.2 post 的存储与并发」）、`docs/patra/runbooks/v0.14-discussion-go-live-runbook.md`（第 6 节 PAP-122 一条）、`docs/patra/specs/2026-10-27-interaction-post-design.md`（实施记录与偏离）
- Modify: 共享笔记 `interaction-PAP-122-handoff.md` 第 3 节（接口形状）

- [ ] **Step 1:** 写文档。
- [ ] **Step 2:** `./gradlew spotlessApply` → 再 `./gradlew check` → BUILD SUCCESSFUL。
- [ ] **Step 3:** `./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest :patra-api:patra-interaction:patra-interaction-boot:integrationTest` → PASS。
- [ ] **Step 4:** `./gradlew dumpModuleGraph` → `git diff --stat module-graph.json` 无变化（有就一起留在工作区）；`bash patra-infra/cd/detect-changes.test.sh` → 全部通过。
- [ ] **Step 5:** `git status` 核对改动只在 `post` 子域、迁移、`FakeCatalog`、文档；不提交，停在本地汇报。
