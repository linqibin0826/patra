# interaction 提醒（PAP-123）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans（用户已定：本会话内联执行，不用 subagent-driven）。Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** interaction 新增 `notification` 子域：订阅讨论事件同事务生成提醒，提供未读数（304）、列表、已读接口，每日清理 5 个月前的提醒，注销时删除收件人的提醒。

**Architecture:** 六边形四层各建 `notification` 包。规则与摘录写法在 domain（纯 Java）；Markdown 转纯文本是 domain 端口 `PlainTextPort`，infra 用 commonmark-java 实现；提醒表按收件人哈希 16 区，`JdbcClient` 原生 SQL；app 层同步 `@EventListener` 订阅 `domain.discussion.event` 的三个事件。

**Tech Stack:** Java 25、Spring Boot 4.0.8、`JdbcClient`、PostgreSQL 17、commonmark-java 0.30.1、XXL-Job、JUnit 5 + AssertJ + Mockito、Testcontainers。

**Spec:** `docs/patra/specs/2026-10-27-interaction-notification-design.md`（下文「spec §N」）。

## Global Constraints

- **不提交**：用户同意前不 `git commit`；每个任务结束只跑验证。最后停在本地汇报。
- 包根 `dev.patra.interaction.{domain|app|infra|adapter}.notification`；四层都要有包（规则 22b）；只依赖底座公开包与 `domain.discussion.event`。
- 每个方法写 `///` Markdown JavaDoc；不用全类名；`List.of()` / `copyOf`；record 参数 ≤ 4 用 `of()`，≥ 5 用 `@Builder`；集合字段在紧凑构造器里 `copyOf`（SpotBugs `EI_EXPOSE_REP`）。
- 时间：`created_at` 取事件的 `publishedAt` / `editedAt`；`read_at`、清理截止取注入的 `Clock`；北京时间 `ZoneId.of("Asia/Shanghai")`。
- 长度：存储摘录 300 个单位（字面字素各 1、提及各 1），读取渲染后 120 个扩展字素簇。每页 20 条。保留 5 个自然月。
- 错误码前缀 `INTERACTION`；订阅方出错包成 `ApplicationException`（`HttpStdErrors.of("INTERACTION").INTERNAL_ERROR()`），**不要**抛 `IllegalStateException`（会被映射成 422）。
- 迁移：`db/migration/notification/V20261027190000__create_notification_schema.sql`、`V20261027190100__create_notification_tables.sql`。
- 依赖：`org.commonmark:commonmark:0.30.1`，版本写 `gradle/libs.versions.toml`（`commonmark = "0.30.1"`），`PatraDependencyManagement` 的 `dependencies {}` 里 `dependency("org.commonmark:commonmark:${version("commonmark")}")`，interaction-infra 写 `implementation("org.commonmark:commonmark")`。
- 新流程测试继承 `InteractionFlowITSupport`，不给单个类 `@Import`；测 429 之外都可用 `call(...)`。
- 每个任务的验证先 `./gradlew :patra-api:patra-interaction:<模块>:spotlessApply`（gjf 会折行）。
- 文本块别以行尾空格拼 SQL（文本块吃行尾空格）；`JdbcClient` 的 `paramSource` 与 `param` 不混用。

## Review Focus

1. **提醒写入失败的状态码**：数据库异常被包成 500，而不是被 `JpaErrorMappingContributor` 映射成 409 或兜底成 422 → Task 9 断言 500。
2. **304 的 Cache-Control**：304 上不能出现 `no-store`（Spring Security 补头），否则浏览器丢缓存 → Task 8 断言。
3. **一页的用户超过 100 人**：20 条触发人 + 摘录提及可超 `UserDirectoryPort.MAX_USERS`，要分批 → Task 7 单元测试用 120 个不同用户。
4. **字面的 `<@123>`**（行内代码里、或 ID 不在提及名单里）不能被换成名字 → Task 1、Task 4 测试。
5. **对象已删除时摘录不外泄**：经验删除后，提醒里 `excerpt` 为 null → Task 7 单元测试、Task 8 流程测试。

---

### Task 1: 摘录写法与渲染（domain）

**Files:**
- Create: `patra-interaction-domain/src/main/java/dev/patra/interaction/domain/notification/package-info.java`
- Create: `…/domain/notification/model/vo/ExcerptText.java`
- Create: `…/domain/notification/model/vo/RenderedExcerpt.java`
- Create: `…/domain/notification/policy/ExcerptRules.java`
- Test: `patra-interaction-domain/src/test/java/dev/patra/interaction/domain/notification/model/vo/ExcerptTextTest.java`、`…/policy/ExcerptRulesTest.java`

（路径前缀 `patra-api/patra-interaction/`，下同。）

**Interfaces — Produces:**
- `record ExcerptText(String stored, boolean truncated)`：`stored` 是存储写法（`\\` = `\`、`\<` = `<`、`<@ID>` = 提及）。
  - `static Builder builder()`；`Builder literal(String text)`（转义 `\` 与 `<`）、`Builder mention(long userId)`、`ExcerptText build()`（`truncated = false`）。
  - `static ExcerptText of(String stored, boolean truncated)`（从库里还原）。
  - `sealed interface Segment permits Literal, Mention`；`record Literal(String text)`、`record Mention(long userId)`；`List<Segment> segments()`（解码）。
  - `Set<Long> mentionIds()`。
  - `ExcerptText cut(int maxUnits)`：字面按扩展字素簇（`BreakIterator.getCharacterInstance()`）每个算 1，提及算 1；超出时只留前 `maxUnits` 个单位，`truncated = true`；不超出原样返回。
- `record RenderedExcerpt(String text, boolean truncated)`，`of(...)`。
- `final class ExcerptRules`：`MAX_STORED_UNITS = 300`、`MAX_RENDERED_GRAPHEMES = 120`、`DELETED_USER = "已注销用户"`；`static RenderedExcerpt render(ExcerptText excerpt, Map<Long, UserCard> cards)`。

- [ ] **Step 1: 写失败的测试**

`ExcerptTextTest`：
- `should_round_trip_literal_with_backslash_and_angle_bracket`：`builder().literal("a\\b<@7>c").build()` 的 `segments()` 只有一个 `Literal("a\\b<@7>c")`，`mentionIds()` 为空。
- `should_keep_mention_as_token`：`builder().literal("hi ").mention(42).literal("!").build().stored()` 等于 `"hi <@42>!"`，`mentionIds()` = `{42}`。
- `should_cut_by_graphemes_without_splitting_tokens`：300 个 `"中"` + mention(1) 的文本 `cut(300)` → `truncated` 为 true，段里没有 `Mention`；`"👨‍👩‍👧"` × 3 `cut(2)` 后字面是 2 个家庭 emoji。
- `should_not_mark_truncated_when_within_limit`：`literal("abc").build().cut(300)` 的 `truncated` 为 false，`stored` 不变。

`ExcerptRulesTest`（`UserCard` 用 builder 构造）：
- `should_render_visible_mention_as_username`：卡片 `username="chen-yu"`、`profileVisible=true` → `"同意 @chen-yu"`。
- `should_render_hidden_profile_mention_as_display_name`：`profileVisible=false`、`displayName="陈宇"` → `"@陈宇"`。
- `should_render_unknown_user_as_deleted`：cards 里没有 → `"@已注销用户"`。
- `should_cut_rendered_text_to_120_graphemes`：200 个 `"字"` → `text` 长 120、`truncated` true；`ExcerptText.of("短", true)` 渲染后 `truncated` 仍为 true。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-interaction:patra-interaction-domain:test --tests "*notification*"`
Expected: 编译失败（类型不存在）。

- [ ] **Step 3: 实现上面四个类型**

`segments()` 自己扫一遍 `stored`：遇 `\` 取下一个字符为字面；遇 `<@` 读到 `>` 为提及；其余字面。`cut` 在段上累计单位，字面段内用 `BreakIterator` 找切点。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2。Expected: PASS。

---

### Task 2: 类型、标签与生成规则（domain）

**Files:**
- Create: `…/domain/notification/model/enums/NotificationType.java`、`NotificationTab.java`
- Create: `…/domain/notification/model/vo/Recipient.java`
- Create: `…/domain/notification/policy/NotificationRules.java`、`NotificationRetention.java`
- Create: `…/domain/notification/exception/NotificationErrorTrait.java`、`InvalidNotificationRequestException.java`、`NotificationNotFoundException.java`
- Test: `…/domain/notification/policy/NotificationRulesTest.java`、`NotificationRetentionTest.java`、`…/model/enums/NotificationTabTest.java`

**Interfaces — Produces:**
- `enum NotificationTab { REPLIES("replies"), MENTIONS("mentions") }`：`String param()`；`static NotificationTab fromParam(String)`（null / 空 → `REPLIES`；其余未知 → `InvalidNotificationRequestException`）。
- `enum NotificationType { COMMENT_REPLY(REPLIES), POST_COMMENT(REPLIES), MENTION(MENTIONS) }`：`NotificationTab tab()`；`static List<NotificationType> of(NotificationTab)`。
- `record Recipient(long userId, NotificationType type)`，`of(...)`。
- `NotificationRules`：`static List<Recipient> forPublished(CommentPublishedEvent e)`（spec §4.2）；`static List<Long> forEdited(CommentEditedEvent e)`（`addedMentionedUserIds` 去作者、去重、保序）。
- `NotificationRetention`：`MONTHS = 5`、`ZONE = ZoneId.of("Asia/Shanghai")`；`static Instant cutoff(Instant now)`。
- 异常：照 `domain.post.exception` 的写法。`NotificationErrorTrait` 只有 `NOTIFICATION_NOT_FOUND`；`NotificationNotFoundException()` 带 `NOT_FOUND` + `NOTIFICATION_NOT_FOUND`；`InvalidNotificationRequestException(String message)` 带 `RULE_VIOLATION`（422）。

- [ ] **Step 1: 写失败的测试**

`NotificationRulesTest`（事件用 builder；`A=1, B=2, C=3, OWNER=9`）：
- `should_notify_parent_author_as_reply`：B 回复 A → `[Recipient(A, COMMENT_REPLY)]`。
- `should_not_notify_self`：A 回复 A、并 @A → 空。
- `should_send_one_reply_when_parent_author_is_also_mentioned`：B 回复 A 且 @A、@C → `[(A, COMMENT_REPLY), (C, MENTION)]`。
- `should_notify_post_owner_on_root_comment`：POST、owner=OWNER、根评论、作者 B → `[(OWNER, POST_COMMENT)]`；同时 @OWNER 仍只一条。
- `should_not_notify_post_owner_on_reply_to_others`：POST、B 回复 C → `[(C, COMMENT_REPLY)]`。
- `should_notify_owner_as_reply_when_replied_directly`：POST、B 回复 OWNER 的评论 → `[(OWNER, COMMENT_REPLY)]`。
- `should_ignore_owner_on_publication`：PUBLICATION、`subjectOwner` null、根评论、@C → `[(C, MENTION)]`。
- `should_notify_only_added_mentions_on_edit`：`addedMentionedUserIds=[C, A]`、作者 A → `[C]`。

`NotificationRetentionTest`：
- `should_subtract_five_calendar_months_in_beijing_time`：`2026-10-27T01:00:00Z` → `2026-05-27T01:00:00Z`。
- `should_clamp_month_end`：北京时间 `2026-07-31T10:00` → `2026-02-28T10:00` 北京时间。

`NotificationTabTest`：`fromParam(null)` = `REPLIES`；`"mentions"` = `MENTIONS`；`"all"` 抛 `InvalidNotificationRequestException`。

- [ ] **Step 2: 跑测试确认失败**（命令同 Task 1）
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过**；再跑 `./gradlew :patra-api:patra-interaction:patra-interaction-domain:check`（R3 纯净性）。

---

### Task 3: 迁移与写端仓储（domain 端口 + infra）

**Files:**
- Create: `patra-interaction-infra/src/main/resources/db/migration/notification/V20261027190000__create_notification_schema.sql`、`V20261027190100__create_notification_tables.sql`
- Create: `…/domain/notification/model/vo/NotificationDraft.java`
- Create: `…/domain/notification/port/repository/NotificationRepository.java`
- Create: `patra-interaction-infra/src/main/java/dev/patra/interaction/infra/notification/package-info.java`、`…/infra/notification/persistence/NotificationRepositoryAdapter.java`、`NotificationRows.java`
- Test: `patra-interaction-infra/src/integrationTest/java/dev/patra/interaction/infra/notification/persistence/NotificationPersistenceITBootstrap.java`、`NotificationPersistenceITSupport.java`、`NotificationRepositoryAdapterIT.java`

**Interfaces:**
- Consumes: `ExcerptText`、`NotificationType`（Task 1、2）。
- Produces:
  - `@Builder record NotificationDraft(long recipientId, NotificationType type, long actorId, boolean actorAnonymous, SubjectId subjectId, long commentId, ExcerptText excerpt, Instant createdAt)`。
  - `NotificationRepository`：
    - `int insertAll(List<NotificationDraft> drafts)` — 多行插入，`ON CONFLICT (recipient_id, comment_id) WHERE comment_id IS NOT NULL DO NOTHING`，ID 用 `SnowflakeIdGenerator.getId()`；返回新插入条数；空列表回 0 不查库。
    - `int refreshExcerpt(long commentId, ExcerptText excerpt)` — 只改 `comment_deleted = false` 的行。
    - `int markCommentDeleted(long commentId)` — `comment_deleted = true`、`excerpt = NULL`、`excerpt_truncated = false`。
    - `boolean markRead(long recipientId, long notificationId, Instant readAt)` — `read_at = COALESCE(read_at, :readAt)`；行存在即 true。
    - `int markAllRead(long recipientId, Instant readAt)`。
    - `int deleteCreatedBefore(Instant cutoff)`、`int deleteAllOf(long recipientId)`。
    - `List<Long> findRecipientsAfter(long afterUserId, int limit)`。

表与索引按 spec §5.1 原样（列、CHECK、`PRIMARY KEY (recipient_id, notification_id)`、16 区 `_p00`~`_p15`、五个索引、英文 COMMENT 照 collection 的风格）。CHECK：`ck_notification_type CHECK (type IN ('COMMENT_REPLY','POST_COMMENT','MENTION'))`、`ck_notification_deleted_excerpt CHECK (NOT comment_deleted OR excerpt IS NULL)`。

- [ ] **Step 1: 写失败的集成测试** `NotificationRepositoryAdapterIT`（support 照 `PostPersistenceITSupport`：`@BeforeEach` 只 `TRUNCATE notification.notification`；辅助 `draft(recipient, type, commentId)`）：
  - `should_spread_rows_over_partitions`：给 32 个收件人各插一条，`SELECT count(DISTINCT tableoid)` ≥ 2，总数 32。
  - `should_skip_second_notification_for_same_recipient_and_comment`：同一收件人同一评论插 `COMMENT_REPLY` 再插 `MENTION` → 第二次返回 0，库里只有 `COMMENT_REPLY`。
  - `should_refresh_excerpt_for_all_recipients_of_comment`：两人各一条，`refreshExcerpt` 返回 2、两行 `excerpt` 都更新；已 `markCommentDeleted` 的不被刷回。
  - `should_clear_excerpt_when_comment_deleted`：之后 `excerpt` 为 null、`comment_deleted` 为 true。
  - `should_reject_excerpt_on_deleted_comment_by_constraint`：直接 SQL 设 `comment_deleted = true` 而保留摘录 → 抛异常且消息含 `ck_notification_deleted_excerpt`。
  - `should_mark_read_only_own_notification`：别人的 ID → false；自己的 → true，再标仍 true 且 `read_at` 不变。
  - `should_mark_all_read_for_recipient_only`：两人各 2 条未读，A 全部已读返回 2，B 仍 2 条未读。
  - `should_delete_only_rows_before_cutoff`：`createdAt` = 截止前 1 微秒与正好截止，各一条 → 删 1 条，剩下正好截止那条。
  - `should_delete_all_of_recipient_and_list_recipients_after_cursor`：收件人 5、7、9 → `findRecipientsAfter(5, 10)` = `[7, 9]`；`deleteAllOf(7)` 后 = `[9]`。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest --tests "*NotificationRepositoryAdapterIT"`
Expected: 编译失败。

- [ ] **Step 3: 写两条迁移，实现 `NotificationRepositoryAdapter`**（照 `LibraryRepositoryAdapter`：`JdbcClient`；时间用 `OffsetDateTime` UTC，工具方法放 `NotificationRows`）。
- [ ] **Step 4: 跑测试确认通过**；`InteractionMigrationIT` 一并跑：`./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest`。

---

### Task 4: Markdown 转纯文本（domain 端口 + infra，引入 commonmark）

**Files:**
- Modify: `gradle/libs.versions.toml`（`commonmark = "0.30.1"`）、`build-logic/src/main/kotlin/PatraDependencyManagement.kt`（`dependencies {}` 加一行）、`patra-api/patra-interaction/patra-interaction-infra/build.gradle.kts`（`implementation("org.commonmark:commonmark")`，注释说明用途；文件头注释的子域列表补 `discussion、post、notification`）
- Create: `…/domain/notification/port/excerpt/PlainTextPort.java`
- Create: `…/infra/notification/excerpt/CommonmarkPlainTextAdapter.java`
- Test: `patra-interaction-infra/src/test/java/dev/patra/interaction/infra/notification/excerpt/CommonmarkPlainTextAdapterTest.java`

**Interfaces — Produces:** `PlainTextPort.ExcerptText toExcerpt(String markdown, Set<Long> mentionedUserIds)`：返回未截断的 `ExcerptText`（spec §6.2 第 1 步的规则）。

- [ ] **Step 1: 写失败的测试**（`new CommonmarkPlainTextAdapter()` 直接测，比较 `ExcerptRules.render(结果, Map.of())` 的文字或 `segments()`）：
  - `should_strip_emphasis_quote_and_list`：`"**粗** *斜*\n\n> 引用\n\n- 一\n- 二"` → 字面 `"粗 斜 引用 一 二"`。
  - `should_keep_link_text_and_autolink`：`"见 [这篇](https://x.org) 与 https://doi.org/10.1/abc"` → `"见 这篇 与 https://doi.org/10.1/abc"`。
  - `should_keep_inline_code_content_and_image_alt`：`` "`a*b` ![图](u.png)" `` → `"a*b 图"`。
  - `should_keep_inline_html_as_text`：`"<b>x</b>"` → `"<b>x</b>"`（字面）。
  - `should_collapse_whitespace`：`"a  \n\n\n  b\tc"` → `"a b c"`。
  - `should_turn_listed_token_into_mention`：`"问 <@42> 一下"`、名单 `{42}` → 段为 `Literal("问 ")`、`Mention(42)`、`Literal(" 一下")`。
  - `should_keep_unlisted_or_code_token_literal`：`"<@7> 与 `<@42>`"`、名单 `{42}` → 没有 `Mention`，字面 `"<@7> 与 <@42>"`。
  - `should_not_mangle_math_asterisk`：`"p*0.05 与 q*2"` → 原文（commonmark 不把它当强调）。

- [ ] **Step 2: 跑测试确认失败**：`./gradlew :patra-api:patra-interaction:patra-interaction-infra:test --tests "*CommonmarkPlainTextAdapterTest"`，Expected：编译失败。
- [ ] **Step 3: 加依赖，实现适配器**：`Parser.builder().build().parse(markdown)`，继承 `AbstractVisitor`；`Text` 节点按正则 `<@([1-9][0-9]{0,18})>` 切分、ID 在名单里才 `mention`；`Code`、`FencedCodeBlock`、`IndentedCodeBlock`、`HtmlInline`、`HtmlBlock` 的文字整段 `literal`；块级节点与 `SoftLineBreak`、`HardLineBreak` 之间插空格；最后合并空白（合并在 `literal` 前做，不跨提及）。
- [ ] **Step 4: 跑测试确认通过**；提醒用户改了 `*.gradle.kts`（汇报时）。

---

### Task 5: 订阅讨论事件（app）

**Files:**
- Create: `patra-interaction-app/src/main/java/dev/patra/interaction/app/notification/package-info.java`
- Create: `…/app/notification/eventhandler/CommentNotificationListener.java`、`NotificationWriteFailedException.java`
- Test: `patra-interaction-app/src/test/java/dev/patra/interaction/app/notification/eventhandler/CommentNotificationListenerTest.java`

**Interfaces:**
- Consumes: `NotificationRules`、`ExcerptRules.MAX_STORED_UNITS`、`ExcerptText.cut`、`PlainTextPort`、`NotificationRepository`。
- Produces: `@Component CommentNotificationListener`：三个 `@EventListener void on(...)`。`NotificationWriteFailedException extends ApplicationException`：`NotificationWriteFailedException(Throwable cause)`，消息「提醒写入失败」。

处理：发表 → `forPublished`，空则直接返回（不调 `PlainTextPort`）；否则摘录 `plainText.toExcerpt(body, Set.copyOf(mentionedUserIds)).cut(MAX_STORED_UNITS)`，拼 `NotificationDraft`（`createdAt = publishedAt`、`actorAnonymous = authorAnonymous`）`insertAll`。编辑 → 先 `refreshExcerpt(commentId, 新摘录)`，再对 `forEdited` 的人 `insertAll`（`createdAt = editedAt`）。删除 → `markCommentDeleted`。三个方法体都包在 `try { … } catch (DomainException ex) { throw ex; } catch (RuntimeException ex) { throw new NotificationWriteFailedException(ex); }`（抽一个私有方法 `guard(Runnable)`）。

- [ ] **Step 1: 写失败的测试**（Mockito mock 仓储与 `PlainTextPort`）：
  - `should_insert_drafts_for_published_comment`：B 回复 A 且 @C → `insertAll` 收到两条，类型 `COMMENT_REPLY`、`MENTION`，`createdAt` 等于事件的 `publishedAt`，`actorAnonymous` 照事件。
  - `should_skip_excerpt_when_nobody_to_notify`：作者回复自己 → 不调 `PlainTextPort`、不调 `insertAll`。
  - `should_refresh_excerpt_then_notify_added_mentions_on_edit`：`InOrder` 先 `refreshExcerpt` 后 `insertAll`；只含新增的人。
  - `should_mark_deleted_on_comment_deleted`。
  - `should_wrap_data_access_failure_as_internal_error`：`insertAll` 抛 `new DataIntegrityViolationException("x")` → 抛 `NotificationWriteFailedException`，`getErrorCode().httpStatus()` = 500，`getCause()` 是原异常。
  - `should_propagate_domain_exception`：抛一个 `NotificationNotFoundException` → 原样。

- [ ] **Step 2: 失败**：`./gradlew :patra-api:patra-interaction:patra-interaction-app:test --tests "*CommentNotificationListenerTest"`
- [ ] **Step 3: 实现**
- [ ] **Step 4: 通过**

---

### Task 6: 读端端口（domain + infra）

**Files:**
- Create: `…/domain/notification/model/read/NotificationEntry.java`、`UnreadCounts.java`
- Create: `…/domain/notification/port/read/NotificationReadPort.java`
- Create: `…/infra/notification/persistence/NotificationReadAdapter.java`
- Test: `…/infra/notification/persistence/NotificationReadAdapterIT.java`

**Interfaces — Produces:**
- `@Builder record NotificationEntry(long notificationId, NotificationType type, long actorId, boolean actorAnonymous, SubjectId subjectId, Long commentId, ExcerptText excerpt /* 可空 */, boolean commentDeleted, Instant createdAt, Instant readAt)`；`boolean read()` = `readAt != null`。
- `record UnreadCounts(int replies, int mentions)`：`of(...)`、`int total()`。
- `NotificationReadPort`：`List<NotificationEntry> page(long recipientId, NotificationTab tab, Long beforeNotificationId, int limit)`（按 `notification_id DESC`，`type IN (tab 的类型)`）；`UnreadCounts unreadCounts(long recipientId)`。

- [ ] **Step 1: 写失败的集成测试** `NotificationReadAdapterIT`（复用 Task 3 的 support，写入走 `NotificationRepository.insertAll`）：
  - `should_page_tab_newest_first_with_cursor`：A 有 3 条回复类、2 条 @ 类 → `page(A, REPLIES, null, 2)` 是最新两条回复类；以第二条 ID 为游标再取得第三条；`MENTIONS` 只有 2 条 @ 类。
  - `should_not_see_other_recipients`：B 的提醒不出现在 A 的页里。
  - `should_count_unread_per_tab`：A：2 条回复类未读、1 条已读、1 条 @ 未读 → `UnreadCounts(2, 1)`，`total()` = 3。
  - `should_map_excerpt_and_deleted_flag`：一条带摘录（`truncated = true`），一条已删除 → 前者 `excerpt().truncated()` 为 true，后者 `excerpt()` 为 null、`commentDeleted()` 为 true。
- [ ] **Step 2: 失败**：`./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest --tests "*NotificationReadAdapterIT"`
- [ ] **Step 3: 实现**
- [ ] **Step 4: 通过**

---

### Task 7: 查询服务与已读命令（app）

**Files:**
- Create: `…/app/notification/usecase/query/NotificationQueryService.java`、`NotificationView.java`、`SubjectRefView.java`、`NotificationPage.java`
- Create: `…/app/notification/usecase/read/MarkNotificationReadCommand.java`、`MarkNotificationReadHandler.java`、`MarkAllNotificationsReadCommand.java`、`MarkAllNotificationsReadHandler.java`
- Test: `…/app/notification/usecase/query/NotificationQueryServiceTest.java`、`…/usecase/read/MarkNotificationReadHandlerTest.java`

**Interfaces:**
- Consumes: `NotificationReadPort`、`NotificationRepository.markRead/markAllRead`、`SubjectReadPort.findByIds`、`UserDirectoryPort.findCards`（≤ `MAX_USERS` 一批）、`CurrentUserPort`、`Clock`、`ExcerptRules.render`。
- Produces:
  - `NotificationQueryService`：`PAGE_SIZE = 20`；`UnreadCounts unreadCounts()`；`NotificationPage page(String tab, String cursor)`（cursor 必须匹配 `[1-9][0-9]{0,18}`，否则 `InvalidNotificationRequestException`）。
  - `record NotificationPage(List<NotificationView> items, String nextCursor)`，`of(...)`。
  - `@Builder record NotificationView(long notificationId, NotificationType type, boolean read, Instant createdAt, UserCard actor /* 匿名或不认识为 null */, boolean actorAnonymous, SubjectRefView subject /* 可空 */, Long commentId, boolean commentDeleted, String excerpt /* 可空 */, boolean excerptTruncated)`。
  - `@Builder record SubjectRefView(SubjectType type, String key, boolean available, String title, String journal, String abbreviation, String submittedMonth, boolean mine)`：文献填 `title`；经验填 `journal`、`abbreviation`、`submittedMonth`（快照 `publishedDate`）、`mine = owner.userId == 收件人`；`available = state == ACTIVE`。
  - `MarkNotificationReadCommand(long notificationId) implements Command<UnreadCounts>`、`MarkAllNotificationsReadCommand implements Command<UnreadCounts>`（`of()`）；处理器：`require()` → 仓储 → 读 `unreadCounts`；单条不存在抛 `NotificationNotFoundException`。

- [ ] **Step 1: 写失败的测试** `NotificationQueryServiceTest`（mock 端口；收件人 R=1）：
  - `should_mask_anonymous_actor`：`actorAnonymous=true` 的条目 → `actor` null，`findCards` 的参数里没有它的 `actorId`。
  - `should_hide_excerpt_when_subject_unavailable`：对象 `UNAVAILABLE` → `excerpt` null、`subject.available()` false，触发人照常。
  - `should_build_post_context_for_owner`：POST 对象 owner = R、快照 `SubjectSnapshot.of(null, "Journal of Tests", "2026-03", "J Tests")` → `mine` true、`abbreviation` `"J Tests"`、`submittedMonth` `"2026-03"`。
  - `should_render_mentions_in_excerpt`：摘录含 `<@5>`，卡片 5 的 `username` = `"chen-yu"` → `excerpt` 含 `"@chen-yu"`。
  - `should_fetch_cards_in_batches_of_100`：20 条，触发人各不同、每条摘录 6 个不同提及 → `findCards` 被调 2 次，每次 ≤ 100 个。
  - `should_return_next_cursor_when_more`：读端口返回 21 条 → 20 条视图，`nextCursor` = 第 20 条的 ID；返回 ≤ 20 条时为 null。
  - `should_reject_bad_cursor`：`"abc"` 抛 `InvalidNotificationRequestException`。
  `MarkNotificationReadHandlerTest`：`markRead` 返回 false → 抛 `NotificationNotFoundException`；true → 返回读端口的计数。
- [ ] **Step 2: 失败**：`./gradlew :patra-api:patra-interaction:patra-interaction-app:test --tests "*notification*"`
- [ ] **Step 3: 实现**
- [ ] **Step 4: 通过**

---

### Task 8: 接口与主流程测试（adapter + boot）

**Files:**
- Create: `patra-interaction-adapter/src/main/java/dev/patra/interaction/adapter/notification/package-info.java`、`…/adapter/notification/rest/MyNotificationController.java`
- Create: `…/adapter/notification/rest/response/UnreadCountsResponse.java`、`NotificationResponse.java`、`ActorResponse.java`、`SubjectRefResponse.java`、`NotificationPageResponse.java`
- Test: `patra-interaction-boot/src/integrationTest/java/dev/patra/interaction/notification/NotificationFlowITSupport.java`、`NotificationRulesFlowIT.java`、`NotificationReadFlowIT.java`

**Interfaces:**
- Consumes: Task 7 的服务与命令、`CommandBus`。
- Produces（spec §9）：`@RequestMapping("/me/notifications")`：
  - `GET /unread-count` → `ResponseEntity<UnreadCountsResponse>`：ETag `"\"" + replies + "-" + mentions + "\""`；`@RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false)` 逐个（逗号分隔、去 `W/`）比较，命中 → 304 无体；两条路径都 `.eTag(etag).cacheControl(CacheControl.noCache().cachePrivate())`。
  - `GET ?tab=&cursor=` → `NotificationPageResponse { items, nextCursor }`。
  - `PUT /{notificationId}/read`、`PUT /read` → `UnreadCountsResponse`。
  - `ActorResponse(Long userId, String username, String displayName, String avatarUrl, int avatarPalette, AccountState state, boolean profileVisible)`：`from(UserCard)`，`profileVisible == false` 时 `username`、`avatarUrl` 置 null（同 `adapter.post.rest.response.AuthorResponse`）。
  - `SubjectRefResponse` 的 `type` 输出 `SubjectType.slug()`。

`NotificationFlowITSupport extends InteractionFlowITSupport`：`publication(long)`、`users(long...)`、`registerPost(long postId, long owner, boolean anonymous)`（`SubjectGateway.registerLocal` 包在 `TransactionTemplate` 里，快照同 Task 7）、`comment(long user, String type, long key, String body, Long parentId)`（先删 `subject.publish_quota` 里他的行，期望 201，回 `commentId`）、`edit(long user, long commentId, String body)`（`PUT /comments/{id}`）、`delete(long user, long commentId)`、`inbox(long user, String tab)`（GET 列表回 JSON）、`unread(long user)`。用户名默认 `user-{id}`（`FakeIdentity`）。

- [ ] **Step 1: 写失败的流程测试**

`NotificationRulesFlowIT`（文献 100；用户 A=1001、B=1002、C=1003；经验 5001 所有者 OWNER=770001）：
- `should_not_notify_when_replying_to_self`：A 发根评论、A 回复自己并 @A → A 的未读 `total` = 0。
- `should_send_single_reply_when_replied_and_mentioned`：A 根评论；B 回复它并写 `@user-1001 @user-1003` → A 的 `replies` 标签 1 条 `COMMENT_REPLY`、`mentions` 标签 0 条；C 的 `mentions` 1 条。
- `should_notify_only_added_mentions_after_edit`：B 发 `@user-1001`；编辑为 `@user-1001 @user-1003` → A 仍 1 条，C 1 条；再编辑删掉 C 并加回 C → C 仍 1 条。
- `should_notify_post_owner_on_root_comment_only`：B 在经验下发根评论 → OWNER 1 条 `POST_COMMENT`；C 回复 B → OWNER 仍 1 条、B 收 `COMMENT_REPLY`。
- `should_show_deleted_comment`：B 回复 A 后删除 → A 的那条 `commentDeleted` true、`excerpt` null；未读数不变。
- `should_render_plain_excerpt`：B 回复 A，正文 `"**同意**，见 [这里](https://x.org) @user-1003"` → `excerpt` = `"同意，见 这里 @user-1003"`、`excerptTruncated` false。

`NotificationReadFlowIT`：
- `should_return_304_when_unread_count_unchanged`：A 有 1 条未读；第一次 GET 200 带 `ETag`；带 `If-None-Match` 再取 → 304、`Cache-Control` 含 `no-cache` 与 `private`、不含 `no-store`；标已读后带旧 ETag → 200、`total` 0。
- `should_mark_one_and_all_read`：A 有回复类 2 条、@ 类 1 条 → `PUT /{id}/read` 回 `{total:2, replies:1, mentions:1}`；`PUT /read` 回全 0；别人的 ID → 404，`traits` 含 `NOTIFICATION_NOT_FOUND`。
- `should_hide_anonymous_owner_id_from_recipient`：经验 5001 匿名所有者 OWNER；B 发根评论，OWNER 回复 B → B 的列表里那条 `actor` 为 null、`actorAnonymous` true，响应原文不含 `"770001"`。
- `should_hide_excerpt_after_post_deleted`：OWNER 非匿名经验，B 发根评论；`SubjectGateway.updateLocal(POST, 5001, UNAVAILABLE, 快照)` → OWNER 那条 `subject.available` false、`excerpt` null。
- `should_page_twenty_per_tab`：B 给 A 发 21 条回复类 → 第一页 20 条、`nextCursor` 非空，第二页 1 条、`nextCursor` null。
- `should_reject_bad_tab_and_require_login`：`tab=all` → 422；匿名 → 401。

- [ ] **Step 2: 失败**：`./gradlew :patra-api:patra-interaction:patra-interaction-boot:integrationTest --tests "*notification*"`
- [ ] **Step 3: 实现控制器与响应**
- [ ] **Step 4: 通过**

---

### Task 9: 同事务回滚（boot 流程测试）

**Files:**
- Test: `patra-interaction-boot/src/integrationTest/java/dev/patra/interaction/notification/NotificationRollbackFlowIT.java`

**Interfaces — Consumes:** `NotificationFlowITSupport`（Task 8）、Task 5 的包装。

- [ ] **Step 1: 写测试**（触发器的函数建在 `public` schema；每例 `try/finally` 删触发器与函数）：
  - `should_leave_no_notification_when_comment_rolls_back`：`CREATE FUNCTION public.test_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test failure'; END $$`；`CREATE CONSTRAINT TRIGGER test_fail_comment AFTER INSERT ON discussion.comment DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.test_fail()`；A 有根评论（先发，再建触发器）；B 回复它 → 状态非 2xx；`discussion.comment` 只有 A 那 1 条；`notification.notification` 为 0。
  - `should_roll_back_comment_when_notification_write_fails`：`CREATE TRIGGER test_fail_notification BEFORE INSERT ON notification.notification FOR EACH ROW EXECUTE FUNCTION public.test_fail()`；B 回复 A → 状态 **500**；`discussion.comment` 只有 1 条；`subject.publish_quota` 里 B 没有行（`post(...)` 前已清）；提醒 0。
  - `should_still_publish_when_nobody_to_notify`（对照）：建 `test_fail_notification` 后 A 回复自己 → 201（没人要提醒、不写表）。
- [ ] **Step 2: 跑**：`./gradlew :patra-api:patra-interaction:patra-interaction-boot:integrationTest --tests "*NotificationRollbackFlowIT"`。Task 5、8 已实现，预期直接 PASS；若第二例不是 500，回到 Task 5 查包装（不改断言）。

---

### Task 10: 清理（每日任务 + 注销）

**Files:**
- Create: `…/app/notification/usecase/purge/PurgeExpiredNotificationsCommand.java`、`PurgeExpiredNotificationsHandler.java`
- Create: `…/app/notification/service/NotificationUserDataCleanupGatewayImpl.java`
- Create: `…/adapter/notification/scheduler/NotificationRetentionScheduleJob.java`
- Test: `…/app/notification/usecase/purge/PurgeExpiredNotificationsHandlerTest.java`、`patra-interaction-boot/src/integrationTest/java/dev/patra/interaction/notification/NotificationCleanupFlowIT.java`

**Interfaces — Produces:**
- `PurgeExpiredNotificationsCommand implements Command<Integer>`（`of()`）；处理器：`repository.deleteCreatedBefore(NotificationRetention.cutoff(clock.instant()))`，日志「已清理 N 条 5 个月前的提醒」。
- `NotificationUserDataCleanupGatewayImpl implements UserDataCleanupGateway`：`subdomain()` = `"notification"`；其余委托仓储。
- `NotificationRetentionScheduleJob`：`@XxlJob("notificationRetentionJob")`，写法照 `DeletedUserCleanupScheduleJob`；类注释写 Cron `0 45 6 * * ?`、超时 300 秒、不重试。

- [ ] **Step 1: 写失败的测试**
  - `PurgeExpiredNotificationsHandlerTest`：固定时钟 `2026-10-27T01:00:00Z` → `deleteCreatedBefore(2026-05-27T01:00:00Z)`，返回值透传。
  - `NotificationCleanupFlowIT`：
    - `should_purge_only_notifications_older_than_five_months`：B 回复 A 两次；直接 SQL 把一条的 `created_at` 改成 `now() - interval '5 months' - interval '1 hour'`，另一条 `now() - interval '5 months' + interval '1 hour'`；`commandBus.handle(PurgeExpiredNotificationsCommand.of())` 返回 1，A 剩 1 条。
    - `should_delete_notifications_of_deleted_user`：B 回复 A、A 回复 B；`IDENTITY_DATA.put(A, "DELETED", false, "PUBLIC")`；`commandBus.handle(CleanupDeletedUsersCommand.of())` → A 作为收件人的提醒 0 条，B 的 1 条还在（触发人 A 读时显示已注销：`actor` null、`actorAnonymous` false）。
- [ ] **Step 2: 失败**：`./gradlew :patra-api:patra-interaction:patra-interaction-app:test --tests "*Purge*" :patra-api:patra-interaction:patra-interaction-boot:integrationTest --tests "*NotificationCleanupFlowIT"`
- [ ] **Step 3: 实现**
- [ ] **Step 4: 通过**

---

### Task 11: 文档、交接与全量验证

**Files:**
- Modify: `patra-api/patra-interaction/README.md`（定时任务行加 `notificationRetentionJob`；注销清理「PAP-123 起加上提醒」改为现在时；`notification` 子域的存储写一节「4.x notification 的存储」：分区、唯一索引、摘录写法）
- Modify: `docs/patra/runbooks/v0.14-discussion-go-live-runbook.md` 第 6 节：标题去掉「（占位）」；原占位里 PAP-121 的条目整理成 6.1，6.2 不动，新增 6.3（迁移 `20261027190000`、`20261027190100`；`\dt notification.*` 有 `notification` 与 16 个分区；登记任务 `notificationRetentionJob`：执行器 `patra-interaction`、Cron `0 45 6 * * ?`、超时 300 秒、不重试，手动执行一次看日志；冒烟：经网关 `GET /patra-interaction/me/notifications/unread-count` 带登录回 200 与 `ETag`，原样带 `If-None-Match` 再取回 304、ETag 不变；D8 注销实测）
- Modify: spec 末尾加「13. 实施记录」（偏离、坑、验证结果）
- Create: 共享笔记 `/Users/linqibin/Library/Application Support/Claude/side-session-notes/local_05d5a37f-fa2d-4775-824c-b51aadd73808/interaction-PAP-123-handoff.md`（接口形状、304 用法、门户要转发 `If-None-Match`、摘录已是纯文本、评论整条去掉时单楼视图 404 显示「该评论已删除」、对象不可用时无摘录）
- Modify（按需）: `module-graph.json`（若 `check` 报不一致，跑 `./gradlew dumpModuleGraph`）

- [ ] **Step 1**: `./gradlew :patra-api:patra-interaction:spotlessApply`（四个子模块各自）后 `./gradlew :patra-api:patra-interaction:check`（含 ArchUnit、SpotBugs）→ BUILD SUCCESSFUL。
- [ ] **Step 2**: `./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest :patra-api:patra-interaction:patra-interaction-boot:integrationTest` → 全绿。
- [ ] **Step 3**: 全仓 `./gradlew check`（PR 前门控；后台跑）→ BUILD SUCCESSFUL。
- [ ] **Step 4**: 写文档与交接笔记；`git status` 核对只动了本 Issue 的路径；停在本地汇报（提醒用户在 IDEA 里点「加载 Gradle 更改」）。
