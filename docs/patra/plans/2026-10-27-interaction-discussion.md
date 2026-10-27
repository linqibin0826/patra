# interaction 讨论子域（PAP-121）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> 本计划按编排约定由当前会话用 superpowers:executing-plans 内联执行（不派子代理），每个任务先写失败的测试再实现。

**Goal:** 在 interaction 里新增 `discussion` 子域：文献与投稿经验下的评论与回复（两层展示、单楼、对话链）、Markdown 正文与 @ 解析、修改历史与删除占位、@ 联想，并在写入事务里发布三个进程内事件。

**Architecture:** 六层模块下各建 `discussion` 包，只依赖底座 `subject` 的公开包（对象登记、发表守卫、用户名片）。写路径：本地校验 → 事务外的 identity / catalog 调用 → `TransactionOperations` 事务（发表守卫计数、楼锁、写库、发布事件）→ 拼响应。读路径：本地登记表 + `discussion` schema 原生 SQL + 一次批量名片。持久化沿用 `JdbcClient` 原生 SQL。

**Tech Stack:** Java 25、Spring Boot 4、`JdbcClient`、PostgreSQL 17、Flyway、JUnit 5 + AssertJ + Mockito、Testcontainers、WireMock、ArchUnit。

**Spec:** `docs/patra/specs/2026-10-27-interaction-discussion-design.md`（已定稿）。实现时 spec 与本计划冲突以 spec 为准；以下「第 x 节」都指 spec 的章节。

## Global Constraints

- 不提交：编排约定全程不 commit、不 push。每个任务的最后一步是「跑通本任务的测试」，不是提交。用户同意后才一次性提交，只加本 Issue 的路径。
- 代码规范：Google Java 风格（`./gradlew spotlessApply` 格式化）；每个方法（含 private、测试方法）都写 `///` Markdown Javadoc，中文；不写全类名；Lombok 优先。
- record：≤ 4 个参数用 `of()`，≥ 5 个用 `@Builder` 且不再提供 `of()`；集合字段在紧凑构造器里 `List.copyOf` / `Set.copyOf`（允许为 null 的写 `x == null ? List.of() : List.copyOf(x)`）；空集合用 `List.of()`。
- 测试方法名 snake_case `should_…`；单元测试 JUnit 5 + AssertJ + Mockito；集成测试只经真实 PG（Testcontainers）。
- 包：`dev.patra.interaction.{domain|app|infra|adapter}.discussion…`。讨论只 import 底座的公开包（`domain.subject.port.gateway|read|user`、`model.vo|enums|user|read`、`exception`），不依赖 collection、post、notification。`*Aggregate` 放 `model.aggregate`，`*Event` 放 `event`，端口放 `port.*`。
- 事件 record 不实现 `DomainEvent`：它继承 `Serializable`，`SubjectId`、`SubjectOwner` 字段会让 SpotBugs 报 `SE_BAD_FIELD`。异常里同样只存原始值（坑清单 v0.14）。
- 写库的时刻一律 `clock.instant().truncatedTo(ChronoUnit.MICROS)`：库里是 `timestamptz(6)`，截断后事件里的时刻与库里的一致。
- SQL 一律写带 schema 的表名（`discussion.comment`）；文本块拼接注意行尾空格被吃掉（坑清单 v0.12），`RETURNING\s"""` 的写法照 collection。
- `JdbcClient` 的 `param(...)` 与 `paramSource(...)` 不混用；数组参数传 `Long[]`，SQL 里 `CAST(:x AS bigint[])`。
- 常量（`DiscussionLimits`）：字素上限 5,000、字节上限 20,000；一条最多 @ 10 人；@ 候选最多 100 个；每页根评论默认 20、上限 50；每楼预览 3 条回复；楼内回复每页默认 50、上限 50；编辑免记版本 5 分钟（含整 5 分钟）；联想参与者最多 6、合计最多 8、参与者扫描 50 位、identity 联想单次上限 20。
- 楼锁键 `discussion.thread:{rootId}`，`pg_advisory_xact_lock(hashtextextended(:key, 0))`。
- 迁移版本号固定：`V20261027161210__create_discussion_schema.sql`、`V20261027161220__create_discussion_tables.sql`，放 `patra-interaction-infra/src/main/resources/db/migration/discussion/`。
- 原因码：`DiscussionErrorTrait.COMMENT_NOT_FOUND`、`REPLY_TARGET_GONE`；字段错误：字段 `body`，码 `REQUIRED` / `INVALID_CHARACTER` / `TOO_LONG` / `TOO_MANY_MENTIONS`。默认文案照 spec 第 6.5 节。
- 门禁命令：单元测试 `./gradlew :patra-api:patra-interaction:patra-interaction-{domain|app|infra|adapter|boot}:test --tests '…'`；集成测试 `:patra-interaction-infra:integrationTest`、`:patra-interaction-boot:integrationTest`（`check` 不含集成测试）。跑集成测试前 `docker info` 确认 OrbStack 在跑。
- 测 429 不能用 `RestTestClient`：它按 `Retry-After` 自动重发（坑清单 v0.14），用 JDK `HttpClient` 直接发。
- 测试源码里的 U+2028 等字符用 `new String(Character.toChars(0x2028))` 构造，不写 ` ` 转义。

## Review Focus

1. **同一楼里回复与删除父评论同时发生**：不能出现孤儿回复，也不能悄悄丢掉回复；要么回复成功、父评论变占位，要么删除成功、回复 409。→ Task 7 的楼锁阻塞测试与外键测试；Task 17 的 `should_keep_tree_consistent_when_reply_races_delete`。
2. **代码、网址、邮箱里的 `@` 被改写**会破坏正文。→ Task 3 的 `MentionSyntaxTest`；Task 18 的 `should_leave_code_and_urls_alone_and_escape_forged_tokens`。
3. **匿名所有者的用户 ID 从任一响应里漏出**（发表回复的响应、单楼、对话链、联想、修改历史）。→ Task 19 的 `should_never_expose_anonymous_owner_to_others` 逐个接口按字符串断言。
4. **病态长正文**（5,000 字素塞满 `@x`、反引号）拖慢扫描或绕过 100 个候选的上限。→ Task 3 的 `should_scan_pathological_input_quickly`；Task 11 的 `should_reject_more_than_hundred_candidates_without_calling_identity`。
5. **翻页时同一时刻的根评论、翻页间删除**造成重复或漏条。→ Task 8 的 `should_page_roots_in_both_orders_with_cursor`（同一微秒的两条按 ID 排）与 `should_not_skip_or_repeat_when_a_root_is_deleted_between_pages`。

---

### Task 1: 讨论子域骨架：四层包、枚举、游标、限额常量与异常

**Files:**
- Create: `patra-interaction-domain/src/main/java/dev/patra/interaction/domain/discussion/package-info.java`
- Create: `…/domain/discussion/model/enums/CommentState.java`、`ThreadSort.java`
- Create: `…/domain/discussion/model/vo/ThreadCursor.java`
- Create: `…/domain/discussion/policy/DiscussionLimits.java`
- Create: `…/domain/discussion/exception/DiscussionErrorTrait.java`、`CommentNotFoundException.java`、`ReplyTargetGoneException.java`、`CommentNotOwnedException.java`、`InvalidCommentException.java`、`InvalidDiscussionRequestException.java`
- Create: `patra-interaction-app/src/main/java/dev/patra/interaction/app/discussion/package-info.java`、`patra-interaction-infra/…/infra/discussion/package-info.java`、`patra-interaction-adapter/…/adapter/discussion/package-info.java`（规则 22b：一层出现四层都要有）
- Test: `patra-interaction-domain/src/test/java/dev/patra/interaction/domain/discussion/model/vo/ThreadCursorTest.java`、`…/model/enums/ThreadSortTest.java`、`…/exception/DiscussionExceptionsTest.java`

**Interfaces:**
- Produces:
  - `enum CommentState { ACTIVE, DELETED }`
  - `enum ThreadSort { ASC, LATEST; static ThreadSort fromParam(String param) }`：`null` 或空白 → `ASC`；`"asc"` / `"latest"`（只认小写）；其余抛 `InvalidDiscussionRequestException("不支持的排序")`
  - `record ThreadCursor(Instant createdAt, long commentId)`：`static ThreadCursor of(Instant, long)`；`static Optional<ThreadCursor> parse(String raw)`（`null` / 空白 → 空；格式不是 `^[0-9]{1,19}\.[1-9][0-9]{0,18}$` 或溢出 → `InvalidDiscussionRequestException("游标不合法")`）；`String format()` = `{自 EPOCH 的微秒}.{commentId}`
  - `final class DiscussionLimits`：Global Constraints 里的全部常量，名字 `MAX_GRAPHEMES`、`MAX_BYTES`、`MAX_MENTIONS`、`MAX_MENTION_CANDIDATES`、`THREAD_PAGE_DEFAULT`、`PAGE_MAX`、`PREVIEW_REPLIES`、`REPLY_PAGE_DEFAULT`、`EDIT_GRACE`（`Duration.ofMinutes(5)`）、`SUGGEST_PARTICIPANTS`、`SUGGEST_TOTAL`、`PARTICIPANT_SCAN`、`IDENTITY_SUGGEST_MAX`
  - `enum DiscussionErrorTrait implements ErrorTrait { COMMENT_NOT_FOUND, REPLY_TARGET_GONE }`（写法照 `CollectionErrorTrait`）
  - `CommentNotFoundException()`：`NOT_FOUND` + `COMMENT_NOT_FOUND`，「这条评论已删除，或者不在这里的讨论中」
  - `ReplyTargetGoneException()`：`CONFLICT` + `REPLY_TARGET_GONE`，「要回复的评论已被删除」
  - `CommentNotOwnedException()`：`FORBIDDEN`，「只能修改自己的评论」
  - `InvalidCommentException implements HasFieldViolations`（`RULE_VIOLATION`，消息「请求参数不合法」）：常量 `FIELD = "body"`、`REQUIRED`、`INVALID_CHARACTER`、`TOO_LONG`、`TOO_MANY_MENTIONS`；工厂 `required()`「写点什么再发表吧」、`invalidCharacter()`「含有不允许的字符」、`tooLong()`「最多 5,000 字」、`tooManyMentions()`「一条评论最多 @ 10 人」，每个只带一条 `FieldViolation`
  - `InvalidDiscussionRequestException(String message)`：`RULE_VIOLATION`

- [ ] **Step 1: 写失败的测试**

```java
// ThreadCursorTest
@Test void should_round_trip_micros_and_id() {
  ThreadCursor cursor = ThreadCursor.of(Instant.parse("2026-10-27T08:00:00.123456Z"), 42L);
  assertThat(ThreadCursor.parse(cursor.format())).contains(cursor);
  assertThat(cursor.format()).endsWith(".42");
}
@Test void should_be_empty_for_null_or_blank() { /* parse(null)、parse("  ") 都为空 */ }
@Test void should_reject_malformed() {
  for (String raw : List.of("abc", "1", ".5", "1.0", "-1.5", "1.05", "99999999999999999999.1", "1.99999999999999999999"))
    assertThatThrownBy(() -> ThreadCursor.parse(raw)).isInstanceOf(InvalidDiscussionRequestException.class);
}
// ThreadSortTest：null、"" → ASC；"asc" → ASC；"latest" → LATEST；"LATEST"、"hot" → InvalidDiscussionRequestException
// DiscussionExceptionsTest
@Test void should_carry_traits_and_field_codes() {
  assertThat(new CommentNotFoundException().getErrorTraits())
      .containsExactlyInAnyOrder(StandardErrorTrait.NOT_FOUND, DiscussionErrorTrait.COMMENT_NOT_FOUND);
  assertThat(new ReplyTargetGoneException().getErrorTraits())
      .containsExactlyInAnyOrder(StandardErrorTrait.CONFLICT, DiscussionErrorTrait.REPLY_TARGET_GONE);
  assertThat(new CommentNotOwnedException().getErrorTraits()).containsExactly(StandardErrorTrait.FORBIDDEN);
  InvalidCommentException ex = InvalidCommentException.tooManyMentions();
  assertThat(ex.getErrorTraits()).containsExactly(StandardErrorTrait.RULE_VIOLATION);
  assertThat(ex.getFieldViolations()).singleElement()
      .satisfies(v -> { assertThat(v.field()).isEqualTo("body"); assertThat(v.code()).isEqualTo("TOO_MANY_MENTIONS"); });
}
```

- [ ] **Step 2: 跑测试确认失败**：`./gradlew :patra-api:patra-interaction:patra-interaction-domain:test --tests '*domain.discussion*'` → 编译失败（类不存在）。
- [ ] **Step 3: 实现上面 Produces 列出的类与四个 `package-info.java`**（`package-info` 的注释照 collection：写明子域职责与公开包 `port.read`、`model.read`、`event`）。
- [ ] **Step 4: 跑测试确认通过**：同上命令 PASS；再跑 `./gradlew :patra-api:patra-interaction:patra-interaction-boot:test --tests '*InteractionArchitectureTest*'` PASS（规则 22b 看到四层都有 `discussion`）。

### Task 2: 正文规则 `CommentText`

**Files:**
- Create: `…/domain/discussion/policy/CommentText.java`
- Test: `…/domain/discussion/policy/CommentTextTest.java`

**Interfaces:**
- Consumes: `InvalidCommentException`、`DiscussionLimits`（Task 1）
- Produces: `final class CommentText { static String parse(String raw) }`：返回规范化后的原文（含 `@用户名` 的客户端形式）

规则（spec 5.3，按顺序）：`null` 视为空串；`\r\n`、`\r` → `\n`；NFC；去掉首尾的空白与空格类字符（`Character.isWhitespace || Character.isSpaceChar`）；空 → `required()`；含控制字符（`Character.isISOControl`，放行 `\n`、`\t`）或 U+2028 / U+2029 → `invalidCharacter()`；扩展字素簇（`BreakIterator.getCharacterInstance(Locale.ROOT)`）> 5,000 或 UTF-8 字节 > 20,000 → `tooLong()`。

- [ ] **Step 1: 写失败的测试**（`CommentTextTest`）
  - `should_normalize_line_breaks_nfc_and_trim`：`parse("  é\r\nb\r  　")` 等于 `"é\nb"`（`é` 是单码点 U+00E9）。
  - `should_reject_blank`：`parse(null)`、`parse(" \n　")` 抛 `InvalidCommentException`，码 `REQUIRED`。
  - `should_keep_tab_and_line_feed`：`parse("a\tb\nc")` 等于 `"a\tb\nc"`。
  - `should_reject_other_control_characters_and_separators`：`"a\u0000b"`、`"a\u0007b"`、`"a\u0085b"`、`"a" + new String(Character.toChars(0x2028)) + "b"`、U+2029 → `INVALID_CHARACTER`。
  - `should_limit_graphemes`：`"a".repeat(5000)`、`"😀".repeat(5000)`（20,000 字节）通过；`"a".repeat(5001)`、`"😀".repeat(5001)` → `TOO_LONG`。
  - `should_limit_bytes_within_grapheme_limit`：`"👨‍👩‍👧‍👦".repeat(801)`（801 个字素、20,025 字节）→ `TOO_LONG`。
- [ ] **Step 2: 跑测试确认失败**：`…-domain:test --tests '*CommentTextTest*'` → 编译失败。
- [ ] **Step 3: 实现 `CommentText.parse`。**
- [ ] **Step 4: 跑测试确认通过。**

### Task 3: @ 的识别与改写 `MentionSyntax`

**Files:**
- Create: `…/domain/discussion/policy/MentionSyntax.java`
- Test: `…/domain/discussion/policy/MentionSyntaxTest.java`

**Interfaces:**
- Produces（纯函数，`final class`）：
  - `static List<String> candidates(String text)`：普通文本里的 @ 候选，按原文去重、保持第一次出现的顺序
  - `static String toStored(String text, Function<String, Long> resolve)`：候选原文 → 用户 ID（返回 `null` 的保持原样），把 `@名字` 整段换成 `<@ID>`；伪造的标记加 `\` 转义
  - `static List<Long> mentionedIds(String stored)`：存储形式里有效标记的 ID，去重、按第一次出现
  - `static String toSource(String stored, Function<Long, String> usernameOf)`：有效标记 → `@用户名`；`usernameOf` 返回 `null` 时 → `@已注销用户`

扫描规则（spec 5.4.1、5.4.3，一次从左到右，线性时间，不用回溯正则扫整段）：

1. `\` 后跟 ASCII 标点（`!"#$%&'()*+,-./:;<=>?@[\]^_\`{|}~`）：这两个字符原样输出，跳过。
2. 反引号串（长度 N）：向后找下一个长度恰好为 N 的反引号串，且中间没有空行（`\n` 后只有空白再接 `\n`）；找到 → 整段是行内代码，原样输出（里面的 `@` 与 `<@数字>` 都不处理）；找不到 → 这串反引号按普通字符输出。
3. 链接 `[文字](目标)`（`[` 起，`]` 前不含 `]`、换行，紧跟 `(`，`)` 前不含空白与 `)`）与网址（`http://` 或 `https://` 起，到空白、`<`、`>` 为止）：里面不识别 `@`；其中的 `<@数字>` 照第 4 条转义。
4. 标记 `<@[1-9][0-9]{0,18}>`（不在行内代码里）：`candidates` 里忽略；`toStored` 里一律在 `<` 前加 `\`；`mentionedIds` / `toSource` 里就是有效标记（存储形式里只有服务端写的标记没被转义）。
5. 普通文本里的 `@`：前一个码点不存在，或者既不是字母 / 数字（`Character.isLetterOrDigit`）也不是 `_`、`@` 时，向后贪婪取 `[A-Za-z0-9_-]+`，去掉末尾的 `_` 与 `-`；剩下的部分满足 `[A-Za-z0-9]([A-Za-z0-9]|[_-][A-Za-z0-9])*`、长度 3 ~ 30、不全是数字时是候选（去掉的尾部字符留在正文里）。

- [ ] **Step 1: 写失败的测试**（`MentionSyntaxTest`）

```java
@Test void should_find_mentions_after_start_space_or_punctuation() {
  assertThat(MentionSyntax.candidates("@chen-yu 你好，@Zhou_Min。(@wang-li) \"@li-hua\" 😀@abc"))
      .containsExactly("chen-yu", "Zhou_Min", "wang-li", "li-hua", "abc");
}
@Test void should_skip_when_preceded_by_letter_digit_underscore_or_at() {
  assertThat(MentionSyntax.candidates("a@bob 你好@bob 1@bob _@bob @@bob mail@x.com")).isEmpty();
}
@Test void should_trim_trailing_connectors_and_check_syntax() {
  assertThat(MentionSyntax.candidates("@chen-yu- @ab @abc @123456 @a--b @" + "x".repeat(31)))
      .containsExactly("chen-yu", "abc");
}
@Test void should_ignore_escapes_code_links_and_urls() {
  assertThat(MentionSyntax.candidates(
          "\\@bob `@bob` ``x @bob`` [@bob](https://x.org/@bob) https://x.org/@bob @carol"))
      .containsExactly("carol");
}
@Test void should_treat_unclosed_or_paragraph_crossing_backticks_as_text() {
  assertThat(MentionSyntax.candidates("`@bob")).containsExactly("bob");
  assertThat(MentionSyntax.candidates("`a\n\n@carol`")).containsExactly("carol");
}
@Test void should_dedupe_by_original_text() {
  assertThat(MentionSyntax.candidates("@Bob @bob @Bob")).containsExactly("Bob", "bob");
}
@Test void should_replace_every_occurrence_of_resolved_names() {
  assertThat(MentionSyntax.toStored("@bob 和 @Bob，@carol", n -> n.equalsIgnoreCase("bob") ? 7L : null))
      .isEqualTo("<@7> 和 <@7>，@carol");
}
@Test void should_escape_forged_tokens_outside_code() {
  assertThat(MentionSyntax.toStored("<@1> `<@2>` [x](<@3>) \\<@4> \\\\<@5>", n -> null))
      .isEqualTo("\\<@1> `<@2>` [x](\\<@3>) \\<@4> \\\\\\<@5>");
}
@Test void should_keep_text_without_mentions_identical() {
  String text = "纯文本 a@b.com `code` https://x.org/@a";
  assertThat(MentionSyntax.toStored(text, n -> 1L)).isEqualTo(text);
}
@Test void should_list_token_ids_in_first_appearance_order() {
  assertThat(MentionSyntax.mentionedIds("<@7> x <@8> <@7> \\<@9> `<@10>`")).containsExactly(7L, 8L);
}
@Test void should_turn_tokens_back_into_usernames() {
  assertThat(MentionSyntax.toSource("hi <@7> and <@8>", id -> id == 7L ? "chen-yu" : null))
      .isEqualTo("hi @chen-yu and @已注销用户");
}
@Test void should_round_trip_source_to_stored() {
  String stored = "见 <@7>-，谢谢";
  String source = MentionSyntax.toSource(stored, id -> "chen-yu");
  assertThat(source).isEqualTo("见 @chen-yu-，谢谢");
  assertThat(MentionSyntax.toStored(source, n -> "chen-yu".equals(n) ? 7L : null)).isEqualTo(stored);
}
@Test void should_scan_pathological_input_quickly() {
  String text = "@ab`".repeat(1250) + "``x".repeat(400) + "[" .repeat(2000);
  assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
    MentionSyntax.candidates(text);
    MentionSyntax.toStored(text, n -> 1L);
  });
}
```

- [ ] **Step 2: 跑测试确认失败**：`…-domain:test --tests '*MentionSyntaxTest*'` → 编译失败。
- [ ] **Step 3: 实现 `MentionSyntax`**：一个包内私有的扫描器同时服务四个方法（按上面 5 条规则把正文切成「原样片段 / 标记 / 候选」），四个公开方法只是对片段的不同处理。
- [ ] **Step 4: 跑测试确认通过。**

### Task 4: 评论聚合与读模型

**Files:**
- Create: `…/domain/discussion/model/read/CommentRecord.java`、`RevisionRecord.java`、`MentionTarget.java`
- Create: `…/domain/discussion/model/vo/NewComment.java`、`CommentEdit.java`
- Create: `…/domain/discussion/model/enums/CommentRemoval.java`
- Create: `…/domain/discussion/model/aggregate/CommentAggregate.java`
- Test: `…/domain/discussion/model/aggregate/CommentAggregateTest.java`

**Interfaces:**
- Consumes: Task 1 的枚举与异常、`DiscussionLimits.EDIT_GRACE`
- Produces:
  - `@Builder record CommentRecord(long commentId, SubjectId subjectId, long rootId, Long parentId, long authorId, CommentState state, String body, Instant createdAt, Instant revisedAt, Long parentAuthorId, CommentState parentState, boolean hasReplies)`，方法 `boolean isRoot()`（`parentId == null`）、`boolean isDeleted()`、`boolean edited()`（`revisedAt != null`）。`parentAuthorId` / `parentState` 是父评论的（根评论为 `null`）
  - `record RevisionRecord(String body, Instant at)` + `of`
  - `record MentionTarget(long userId, AccountState state)` + `of`
  - `@Builder record NewComment(SubjectId subjectId, Long rootId, Long parentId, long authorId, String body, List<Long> mentionedUserIds, Instant createdAt)`：根评论 `rootId`、`parentId` 为 `null`；`mentionedUserIds` `copyOf`
  - `record CommentEdit(CommentAggregate updated, RevisionRecord archived, List<Long> addedMentionedUserIds)` + `of`；`archived` 为 `null` 表示不记版本
  - `enum CommentRemoval { PLACEHOLDER, REMOVE, NONE }`
  - `final class CommentAggregate`（`@Getter`，私有构造器上 `@Builder(toBuilder = true)`，写法照 `SubjectAggregate`）：字段 `long commentId, SubjectId subjectId, long rootId, Long parentId, long authorId, CommentState state, String body, Set<Long> mentionedUserIds, Instant createdAt, Instant revisedAt`；不变量：`state == ACTIVE` ⇔ `body != null`，`parentId == null` ⇔ `rootId == commentId`，违反抛 `IllegalStateException`；`mentionedUserIds` `Set.copyOf`
    - `boolean isRoot()`
    - `void requireAuthor(long userId)`：不是作者 → `CommentNotOwnedException`
    - `void requireRepliable()`：`ACTIVE` 或根评论（占位也行）放行，否则 `ReplyTargetGoneException`
    - `Optional<CommentEdit> edit(long editorId, String newBody, List<Long> newMentions, Instant now)`：先 `requireAuthor`；`DELETED` → `CommentNotFoundException`；`newBody` 与 `body` 相同 → 空；`now` 不晚于 `createdAt + EDIT_GRACE` → 原地替换、`archived = null`、`revisedAt` 不变；否则 `archived = RevisionRecord.of(body, revisedAt != null ? revisedAt : createdAt)`、`revisedAt = now`。`addedMentionedUserIds` = `newMentions` 里不在旧 `mentionedUserIds` 的（保序）；`updated.mentionedUserIds` = 旧的并上 `newMentions`
    - `CommentRemoval removalFor(long actorId, boolean hasReplies)`：先 `requireAuthor`；`DELETED` → `NONE`；`hasReplies` → `PLACEHOLDER`；否则 `REMOVE`

- [ ] **Step 1: 写失败的测试**（`CommentAggregateTest`，`T0 = 2026-10-27T08:00:00Z`，作者 1、他人 2）
  - `should_reject_edit_by_someone_else`：→ `CommentNotOwnedException`。
  - `should_reject_edit_of_placeholder`：`DELETED`、`body = null` → `CommentNotFoundException`。
  - `should_return_empty_when_body_unchanged`。
  - `should_replace_in_place_up_to_five_minutes`：`edit(1, "新", List.of(), T0.plus(5, MINUTES))` → `archived` 为 `null`，`updated.body = "新"`，`updated.revisedAt` 为 `null`。
  - `should_archive_previous_version_after_five_minutes`：`now = T0.plus(5, MINUTES).plusNanos(1000)` → `archived = RevisionRecord.of("旧", T0)`，`updated.revisedAt = now`。
  - `should_archive_with_previous_revision_time_later_on`：`revisedAt = R1`，`now = R1 + 1h` → `archived.at() == R1`。
  - `should_report_only_first_time_mentions`：旧 `{7}`，`newMentions = [8, 7, 9]` → `addedMentionedUserIds = [8, 9]`，`updated.mentionedUserIds = {7, 8, 9}`。
  - `should_allow_replies_to_active_or_root_placeholder_only`：正常回复、占位根评论放行；占位回复 → `ReplyTargetGoneException`。
  - `should_decide_removal`：他人 → 403；占位 → `NONE`；有回复 → `PLACEHOLDER`；否则 `REMOVE`。
  - `should_enforce_invariants`：`ACTIVE` 无正文、根评论带 `parentId`、回复的 `rootId == commentId` 都抛 `IllegalStateException`。
- [ ] **Step 2: 跑测试确认失败**：`…-domain:test --tests '*CommentAggregateTest*'`。
- [ ] **Step 3: 实现上面 Produces 列出的类。**
- [ ] **Step 4: 跑测试确认通过。**

### Task 5: 事件契约

**Files:**
- Create: `…/domain/discussion/event/CommentPublishedEvent.java`、`CommentEditedEvent.java`、`CommentDeletedEvent.java`
- Test: `…/domain/discussion/event/DiscussionEventsTest.java`

**Interfaces:**
- Produces（字段与语义照 spec 第 7 节，`@Builder` record，类注释写明「同步 `@EventListener` 订阅、不要用 `@TransactionalEventListener`」）：
  - `CommentPublishedEvent(long commentId, SubjectId subjectId, SubjectType subjectType, SubjectOwner subjectOwner, long rootCommentId, Long parentCommentId, Long parentAuthorId, long authorId, boolean authorAnonymous, List<Long> mentionedUserIds, String body, Instant publishedAt)`
  - `CommentEditedEvent(long commentId, SubjectId subjectId, SubjectType subjectType, SubjectOwner subjectOwner, long rootCommentId, Long parentCommentId, Long parentAuthorId, long authorId, boolean authorAnonymous, List<Long> mentionedUserIds, List<Long> addedMentionedUserIds, String body, Instant editedAt)`
  - `CommentDeletedEvent(long commentId, SubjectId subjectId, long authorId, boolean placeholder, Instant deletedAt)`

- [ ] **Step 1: 写失败的测试**：`should_copy_lists_and_treat_null_as_empty`（构造时传可变列表，之后改原列表不影响事件；传 `null` 得到空列表；事件里的列表 `add` 抛 `UnsupportedOperationException`），两个带列表的事件各一遍。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现三个 record。**
- [ ] **Step 4: 跑测试确认通过**；再跑一次 `InteractionArchitectureTest`（规则 14：`*Event` 在 `..domain..event..`）。

### Task 6: 迁移与约束

**Files:**
- Create: `patra-interaction-infra/src/main/resources/db/migration/discussion/V20261027161210__create_discussion_schema.sql`、`V20261027161220__create_discussion_tables.sql`
- Create: `patra-interaction-infra/src/integrationTest/java/dev/patra/interaction/infra/discussion/persistence/DiscussionPersistenceITBootstrap.java`、`DiscussionPersistenceITSupport.java`
- Test: `…/infra/discussion/persistence/DiscussionMigrationIT.java`

**Interfaces:**
- Produces：两张表、四个 CHECK、三个外键、四个索引，名字与列照 spec 第 4.1、4.2 节（`ck_comment_state`、`ck_comment_tree`、`ck_comment_body`、`ck_comment_deleted_at`、`fk_comment_root`、`fk_comment_parent`、`ck_comment_revision_no`、`fk_comment_revision_comment`（`ON DELETE CASCADE`）；`idx_comment_roots`、`idx_comment_thread`、`idx_comment_parent`、`idx_comment_participants`）。每列 `COMMENT ON`（英文，同 subject / collection 的写法）；文件头注释写子域与 PAP-121。
- `DiscussionPersistenceITBootstrap`：`@SpringBootApplication`，只扫描本包（照 `SubjectRegistryITBootstrap`）。
- `DiscussionPersistenceITSupport`：`@SpringBootTest(classes = DiscussionPersistenceITBootstrap.class)` + 容器初始化器 + `test` profile；注入 `JdbcTemplate`、`TransactionTemplate`；`@BeforeEach` `TRUNCATE discussion.comment_revision, discussion.comment`；提供 `insertRaw(...)` 之类只给本包测试用的 SQL 辅助方法。

- [ ] **Step 1: 写失败的测试**（`DiscussionMigrationIT extends DiscussionPersistenceITSupport`，直接写 SQL）
  - `should_create_tables_and_indexes`：`pg_indexes` 里四个索引名都在。
  - `should_enforce_comment_checks`：逐条插入违规行，`assertThatThrownBy(...).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("约束名")`：`state = 'HIDDEN'` → `ck_comment_state`；根评论带 `parent_id` → `ck_comment_tree`；`ACTIVE` 且 `body` 为空 → `ck_comment_body`；`DELETED` 而 `body` 非空且有 `deleted_at` → `ck_comment_body`；`DELETED`、`body` 为空、无 `deleted_at` → `ck_comment_deleted_at`。
  - `should_enforce_tree_foreign_keys`：父评论不存在 → `fk_comment_parent`；`root_id` 指向不存在的行 → `fk_comment_root`；删除有回复的回复 → `fk_comment_parent`；删除带修改历史的评论 → 历史行一起没了。
  - `should_reject_non_positive_revision_no`：→ `ck_comment_revision_no`。
- [ ] **Step 2: 跑测试确认失败**：`./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest --tests '*DiscussionMigrationIT*'` → 表不存在。
- [ ] **Step 3: 写两条迁移。**
- [ ] **Step 4: 跑测试确认通过**；再跑 `--tests '*InteractionMigrationIT*'`（新 schema 在子域清单里、版本号是递增的 14 位时间戳）与 `./gradlew :patra-api:patra-interaction:patra-interaction-infra:checkMigrationColumns`（R6）。

### Task 7: 写端口与持久化 `CommentRepository`

> 写代码前加载 `patra-backend:patra-jpa`（数据层）。本服务的数据层是 `JdbcClient` 原生 SQL，照 `CollectionRepositoryAdapter` 写。

**Files:**
- Create: `…/domain/discussion/port/repository/CommentRepository.java`
- Create: `patra-interaction-infra/src/main/java/dev/patra/interaction/infra/discussion/persistence/CommentRows.java`、`CommentRepositoryAdapter.java`
- Test: `…/infra/discussion/persistence/CommentRepositoryAdapterIT.java`

**Interfaces:**
- Consumes: `CommentAggregate`、`NewComment`、`RevisionRecord`（Task 4）
- Produces（写方法不开事务，由 app 层的事务包住）：
  - `void lockThread(long rootId)`：楼锁（Global Constraints）
  - `Optional<CommentAggregate> find(long commentId)`；`findForUpdate(long)`（`FOR UPDATE`，删除用）；`findForEdit(long)`（`FOR NO KEY UPDATE`，编辑用）。读 `mentioned_user_ids` 用 `rs.getArray(…).getArray()`
  - `boolean hasReplies(long commentId)`：有没有 `parent_id` 是它的行
  - `long insert(NewComment comment)`：`SnowflakeIdGenerator.getId()` 生成 ID；`root_id` = `comment.rootId() == null ? 新 ID : comment.rootId()`；`state = 'ACTIVE'`；返回新 ID
  - `void updateBody(CommentAggregate comment)`：只改 `body`、`mentioned_user_ids`、`revised_at`
  - `void appendRevision(long commentId, RevisionRecord revision)`：`revision_no` = 该评论已有最大值 + 1（`INSERT … SELECT COALESCE(max(revision_no), 0) + 1`）
  - `void markDeleted(long commentId, Instant now)`：`state = 'DELETED'`、`body = NULL`、`mentioned_user_ids = '{}'`、`deleted_at = now`，并删掉它的历史版本
  - `void delete(long commentId)`：删行（历史版本随外键级联）
- `CommentRows`：`COLUMNS`、`AGGREGATE` 行映射、`utc(Instant)`、`ids(List<Long>) → Long[]`（包内共用，Task 8 的读适配器也用）

- [ ] **Step 1: 写失败的测试**（`CommentRepositoryAdapterIT`，写操作包在 `transactions` 里）
  - `should_insert_root_and_reply`：根评论 `rootId == commentId`、`parentId` 为空、`ACTIVE`、`mentionedUserIds = {7}`、`createdAt` 原样；回复的 `rootId` 是根评论。
  - `should_update_body_mentions_and_revised_at_only`。
  - `should_number_revisions_in_order`：追加两版后 `revision_no` 依次为 1、2，`version_at` 原样。
  - `should_mark_placeholder_and_drop_revisions`：之后 `find` 得到 `DELETED`、`body` 为空、`mentionedUserIds` 为空；历史表 0 行。
  - `should_delete_comment_with_its_revisions`。
  - `should_report_direct_replies`：根有回复为真，叶子为假。
  - `should_refuse_to_delete_comment_with_replies`：`delete(根)` 抛 `DataIntegrityViolationException`。
  - `should_block_on_thread_lock_held_elsewhere`：照 `PublishQuotaRepositoryAdapterIT.should_block_on_publish_lock_held_elsewhere`，键 `"discussion.thread:" + rootId`。
- [ ] **Step 2: 跑测试确认失败**：`…-infra:integrationTest --tests '*CommentRepositoryAdapterIT*'`。
- [ ] **Step 3: 实现端口与适配器。**
- [ ] **Step 4: 跑测试确认通过。**

### Task 8: 读端口 `DiscussionReadPort`

**Files:**
- Create: `…/domain/discussion/port/read/DiscussionReadPort.java`
- Create: `…/infra/discussion/persistence/DiscussionReadAdapter.java`
- Test: `…/infra/discussion/persistence/DiscussionReadAdapterIT.java`

**Interfaces:**
- Consumes: `CommentRecord`、`RevisionRecord`、`ThreadSort`、`ThreadCursor`
- Produces：
  - `List<CommentRecord> findRoots(SubjectId subjectId, ThreadSort sort, ThreadCursor after, int limit)`：`parent_id IS NULL`；`ASC` 按 `(created_at, comment_id)` 升序、`after` 不为空时 `(created_at, comment_id) > (:at, :id)`；`LATEST` 全部反过来
  - `Map<Long, List<CommentRecord>> findReplyPreviews(Collection<Long> rootIds, int perThread)`：每楼最早的 `perThread` 条回复（`row_number() OVER (PARTITION BY root_id ORDER BY created_at, comment_id)`；父评论与 `hasReplies` 在外层查询里连，免得对整楼算）；没有回复的根不在结果里
  - `List<CommentRecord> findReplies(long rootId, ThreadCursor after, int limit)`：楼内回复正序
  - `List<CommentRecord> findThread(long rootId)`：根在前，回复按 `(created_at, comment_id)` 正序
  - `Optional<CommentRecord> findById(long commentId)`
  - `List<CommentRecord> findAncestry(long commentId)`：递归 CTE，从根到它
  - `List<RevisionRecord> findRevisions(long commentId)`：历史表，`revision_no` 倒序
  - `List<Long> findRecentParticipants(SubjectId subjectId, Collection<Long> excludedUserIds, int limit)`：`state = 'ACTIVE'` 的评论的作者，按各自最近一条倒序（同时间按 `author_id` 倒序），排除给定 ID（`author_id <> ALL(CAST(:excluded AS bigint[]))`，空数组也成立）
  - 每条 `CommentRecord` 都带父评论的 `author_id` 与 `state`（`LEFT JOIN`）和 `hasReplies`（`EXISTS`）

- [ ] **Step 1: 写失败的测试**（`DiscussionReadAdapterIT`，数据经 Task 7 的 `CommentRepository` 写入，时刻显式给定）
  - `should_page_roots_in_both_orders_with_cursor`：r1、r2 同一微秒（ID 升序），r3 晚 1 秒 → `ASC` 每页 2：`[r1, r2]`，以 r2 为游标：`[r3]`；`LATEST`：`[r3, r2]`、再 `[r1]`。
  - `should_not_skip_or_repeat_when_a_root_is_deleted_between_pages`：取完第一页后删掉第一页里的一条，第二页照游标继续，结果与删除前的第二页相同。
  - `should_preview_earliest_replies_per_root`：A 有 5 条回复、B 有 1 条、C 没有 → `findReplyPreviews([A, B, C], 4)`：A 是最早的 4 条（按时间），B 1 条，C 不在。
  - `should_page_replies_after_cursor`。
  - `should_return_whole_thread_root_first`。
  - `should_return_ancestry_from_root_including_placeholders`：根 → X（占位）→ Y → Z，`findAncestry(Z)` = `[根, X, Y, Z]`。
  - `should_carry_parent_author_state_and_replies_flag`：Y 的 `parentAuthorId` 是 X 的作者、`parentState = DELETED`；X `hasReplies = true`，Z 为假。
  - `should_list_revisions_newest_first`。
  - `should_list_recent_participants`：只算正常评论（只有占位的作者不算）、排除给定 ID、按最近评论倒序、`limit` 生效、空排除列表也能查。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现端口与适配器**（行映射放 `CommentRows`）。
- [ ] **Step 4: 跑测试确认通过。**

### Task 9: identity 的 @ 支撑 `MentionDirectoryPort`

**Files:**
- Create: `…/domain/discussion/port/mention/MentionDirectoryPort.java`
- Create: `patra-interaction-infra/src/main/java/dev/patra/interaction/infra/discussion/mention/MentionDirectoryAdapter.java`
- Test: `patra-interaction-infra/src/test/java/dev/patra/interaction/infra/discussion/mention/MentionDirectoryAdapterTest.java`

**Interfaces:**
- Consumes: `MentionTarget`（Task 4）；底座的 `UserCard`、`AccountState`、`UserDirectoryUnavailableException`；`patra-identity-api` 的 `UserEndpoint`
- Produces：
  - `int MAX_USERNAMES = 100; int MAX_SUGGESTIONS = 20;`
  - `Map<String, MentionTarget> resolve(Collection<String> usernames)`：1 ~ 100 个原文（越界 `IllegalArgumentException`，不发请求）→ `resolveByUsernames`；按响应的 `username`（请求原文）作键
  - `List<UserCard> suggest(String prefix, long viewerUserId, int limit)`：`limit` 1 ~ 20（越界 `IllegalArgumentException`）→ `suggest(UserSuggestReq.of(prefix, viewerUserId, limit))`，按 identity 的顺序转成 `UserCard`（映射同 `UserDirectoryAdapter.toCard`，在本类里另写一份私有方法：跨子域不能用 subject 的 infra 类）
  - 两个方法的三类失败（`RemoteCallException`、`ResourceAccessException`、`IllegalStateException`）都翻成 `UserDirectoryUnavailableException`，`try` 只包一次代理调用，WARN 日志写法照 `UserDirectoryAdapter`

- [ ] **Step 1: 写失败的测试**（照 `UserDirectoryAdapterTest`）
  - `should_map_resolutions_by_original_text`：请求 `["Chen-Yu", "old-name"]`，响应两行 → `{"Chen-Yu" → (42, ACTIVE), "old-name" → (43, BANNED)}`。
  - `should_map_suggestions_and_pass_viewer`：`suggest("张", 7L, 14)` 发出 `UserSuggestReq.of("张", 7L, 14)`，结果字段逐一映射。
  - `should_translate_remote_failures`：两个方法 × 三类失败 → `UserDirectoryUnavailableException` 且 `hasCause`。
  - `should_reject_out_of_range_arguments`：0 个、101 个用户名，`limit` 0 与 21 → `IllegalArgumentException`，`verifyNoInteractions(endpoint)`。
- [ ] **Step 2: 跑测试确认失败**：`…-infra:test --tests '*MentionDirectoryAdapterTest*'`。
- [ ] **Step 3: 实现端口与适配器**；`InteractionITBootstrap` 已有 mock `UserEndpoint`，infra 集成测试的上下文不用改。
- [ ] **Step 4: 跑测试确认通过。**

### Task 10: 对象查找与视图拼装 `DiscussionSubjects`、`CommentViews`

**Files:**
- Create: `patra-interaction-app/src/main/java/dev/patra/interaction/app/discussion/service/DiscussionSubjects.java`、`CommentViews.java`
- Create: `…/app/discussion/usecase/query/CommentView.java`、`ReplyToView.java`、`MentionView.java`
- Test: `patra-interaction-app/src/test/java/dev/patra/interaction/app/discussion/DiscussionFixture.java`（测试辅助：`signedIn(long)`、`anonymous()` 的 `CurrentUserPort`，记录事务边界的 `TransactionOperations`，造 `CommentRecord` / `UserCard` / `SubjectView` 的方法；讨论自己的一份，不引用 collection 的测试类）
- Test: `…/app/discussion/service/DiscussionSubjectsTest.java`、`CommentViewsTest.java`

**Interfaces:**
- Consumes: 底座 `SubjectReadPort`、`UserDirectoryPort`、`SubjectView`、`SubjectOwner`；Task 3 的 `MentionSyntax.mentionedIds`；Task 4 的 `CommentRecord`
- Produces：
  - `DiscussionSubjects`（`@Component`）：
    - `SubjectType requireDiscussable(String typeSlug, String key)`：未知 slug → `InvalidDiscussionRequestException("不支持的对象类型")`；`!type.supports(DISCUSS)` → `new SubjectCapabilityNotSupportedException(type, DISCUSS)`；`type.parseKey(key)`（格式不对抛底座的 `InvalidSubjectKeyException`）
    - `Optional<SubjectView> findForRead(SubjectType type, String key)`：`findIdsByKeys` 查不到 → 空；`findByIds` 得到 `UNAVAILABLE` → `SubjectNotFoundException.unavailable(type, key)`
    - `SubjectView requireActive(SubjectId subjectId)`：`UNAVAILABLE` → `unavailable(view.type(), view.key())`；登记表里没有 → `IllegalStateException`
  - `@Builder record CommentView(long commentId, long rootId, Long parentId, boolean deleted, UserCard author, boolean anonymous, boolean owner, boolean mine, ReplyToView replyTo, String body, List<MentionView> mentions, boolean hasReplies, Instant createdAt, boolean edited)`
  - `record ReplyToView(boolean deleted, boolean anonymous, UserCard author)` + `of`；`record MentionView(long userId, String username, String displayName, boolean deleted)` + `of`
  - `CommentViews`（`@Component`）：
    - `List<CommentView> assemble(List<CommentRecord> comments, SubjectView subject, Long viewerId, Map<Long, UserCard> knownCards)`：按 spec 5.7、5.8 拼；需要的名片（非占位评论的作者——匿名所有者只在访客是他本人时要、`replyTo` 的作者——父评论正常且不是匿名所有者时要、正文里的提及）去重、减去 `knownCards`，每 100 个一批调 `findCards(批, viewerId)`；`profileVisible` 为假的名片置空 `username`、`avatarUrl`
    - `List<MentionView> mentions(Collection<Long> userIds, Long viewerId)`：给修改历史用，规则同上
    - `Map<Long, UserCard> cards(Collection<Long> userIds, Long viewerId)`：原样名片（不置空），分批；给编辑原文用（Task 15）

- [ ] **Step 1: 写失败的测试**
  - `DiscussionSubjectsTest`：`should_reject_unknown_type_capability_and_bad_key`（`"book"` 422、`"journal"` → `SubjectCapabilityNotSupportedException`、key `"0"` → `InvalidSubjectKeyException`）；`should_find_registered_subjects_for_reading`（未登记 → 空；`UNAVAILABLE` → 异常带 `SUBJECT_UNAVAILABLE`；`ACTIVE` → 视图）；`should_require_active_subject_by_id`。
  - `CommentViewsTest`（所有者 9 的 `POST`，访客 5）：
    - `should_mask_anonymous_owner_for_others`：9 的评论 → `author` 为空、`anonymous`、`owner` 为真、`mine` 为假，`findCards` 的参数里没有 9。
    - `should_give_anonymous_owner_their_own_card`：访客 9 → `author.userId() == 9`、`anonymous`、`mine` 都为真。
    - `should_mark_non_anonymous_owner`：`owner` 真、`anonymous` 假、`author` 是名片。
    - `should_hide_username_and_avatar_when_profile_invisible`：`username`、`avatarUrl` 为空，`displayName` 照原样。
    - `should_describe_reply_targets`：父评论是根 → `replyTo` 为空；父评论正常（作者 6）→ `author.userId() == 6`；父评论占位 → `deleted`；父评论是匿名所有者 → `anonymous`、`author` 为空。
    - `should_render_placeholders_without_content`：占位 → 作者、正文为空，提及为空，`mine` 假，`hasReplies` 保留。
    - `should_build_mention_entities_in_order`：正文 `"<@7> <@8> <@10> <@11>"`，7 可见、8 不可见、10 已注销、11 被 identity 省略 → `[(7, "u7", "D7", false), (8, null, "D8", false), (10, null, null, true), (11, null, null, true)]`。
    - `should_treat_omitted_author_as_null`：`author` 为空、`anonymous` 假。
    - `should_batch_lookups_by_hundred_and_skip_known_cards`：150 个不同的提及 → `findCards` 两次（100 + 50）；`knownCards` 里的 ID 不再查。
    - `should_propagate_identity_outage`：`findCards` 抛 `UserDirectoryUnavailableException` → 原样抛出。
- [ ] **Step 2: 跑测试确认失败**：`…-app:test --tests '*app.discussion*'`。
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过。**

### Task 11: @ 解析服务 `MentionResolution`

**Files:**
- Create: `…/app/discussion/service/MentionResolution.java`、`MentionDraft.java`、`ResolvedBody.java`
- Test: `…/app/discussion/service/MentionResolutionTest.java`

**Interfaces:**
- Consumes: `MentionSyntax`（Task 3）、`MentionDirectoryPort`（Task 9）、`InvalidCommentException`
- Produces：
  - `record MentionDraft(String text, List<String> candidates)` + `of`；`record ResolvedBody(String body, List<Long> mentionedUserIds)` + `of`
  - `MentionDraft draft(String text)`：本地识别；候选 > 100 → `InvalidCommentException.tooManyMentions()`（不调 identity）
  - `ResolvedBody resolve(MentionDraft draft, Set<Long> carriedOver)`：没有候选不调 identity；有就一次 `resolve`；`MentionTarget` 为 `ACTIVE`、或 `userId` 在 `carriedOver` 里时换成标记（spec 5.4.2 第 3 步）；最终 `mentionedIds` > 10 → `tooManyMentions()`

- [ ] **Step 1: 写失败的测试**
  - `should_not_call_identity_without_candidates`。
  - `should_turn_only_active_users_into_tokens`：bob → (7, ACTIVE)、ann → (8, BANNED)；`"@bob @ann @nobody"` → `"<@7> @ann @nobody"`、`[7]`。
  - `should_keep_carried_over_mentions_whatever_their_state`：`carriedOver = {8}` → `"<@7> <@8> @nobody"`、`[7, 8]`。
  - `should_count_people_not_spellings`：`"@Bob @bob"` 都解析到 7 → `[7]`。
  - `should_reject_more_than_ten_people`：11 个不同的正常账号 → `TOO_MANY_MENTIONS`。
  - `should_reject_more_than_hundred_candidates_without_calling_identity`：101 个不同的合法候选 → `draft` 抛 `TOO_MANY_MENTIONS`，`verifyNoInteractions(directory)`。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过。**

### Task 12: 发表根评论与回复 `PublishCommentHandler`

**Files:**
- Create: `…/app/discussion/usecase/comment/PublishCommentCommand.java`、`PublishCommentHandler.java`
- Test: `…/app/discussion/usecase/comment/PublishCommentHandlerTest.java`

**Interfaces:**
- Consumes: `DiscussionSubjects`、`CommentViews`（Task 10）、`MentionResolution`（Task 11）、`CommentRepository`（Task 7）、`DiscussionReadPort.findById`（Task 8）、`CommentText`、底座 `SubjectGateway`、`PublishGuardGateway`、Spring `ApplicationEventPublisher`、`TransactionOperations`、`Clock`、`CurrentUserPort`
- Produces：
  - `record PublishCommentCommand(String type, String key, Long parentId, String body) implements Command<CommentView>` + `of`
  - `PublishCommentHandler implements CommandHandler<PublishCommentCommand, CommentView>`

顺序（spec 第 6.2 节「发表的顺序」，逐步对应测试）：

```
author = currentUser.require().userId()
type = subjects.requireDiscussable(type, key)                  // 422
parentId 非空且 ≤ 0 → InvalidDiscussionRequestException("回复的评论不合法")
draft = mentions.draft(CommentText.parse(body))                // 422，至此没有远程调用
self = guard.requireEligible(author)                           // 409 / 503
subjectId = gateway.resolveForInteraction(type, key, DISCUSS)  // 404 / 422 / 503
subject = subjects.requireActive(subjectId)
parent = parentId == null ? null : comments.find(parentId) 或 ReplyTargetGoneException
parent 的 subjectId ≠ subjectId → InvalidDiscussionRequestException("回复的评论不在这个讨论里")
resolved = mentions.resolve(draft, Set.of())
now = 截到微秒
id = transactions.execute:
    guard.admit(author, COMMENT)
    parent 非空：comments.lockThread(parent.rootId)；locked = comments.find(parentId) 或 409；locked.requireRepliable()
    newId = comments.insert(NewComment{subjectId, rootId=parent?.rootId, parentId, author, resolved.body, resolved.mentionedUserIds, now})
    events.publishEvent(CommentPublishedEvent{…, parentAuthorId = locked 正常 ? locked.authorId : null,
                                              authorAnonymous = owner 匿名且 owner.userId == author, publishedAt = now})
    return newId
record = reads.findById(id)
return views.assemble([record], subject, author, {author → self}).getFirst()
```

- [ ] **Step 1: 写失败的测试**（用 `DiscussionFixture` 的记录事务与 `InOrder`）
  - `should_validate_locally_before_any_remote_call`：未知类型、空正文、`parentId = 0`、101 个候选各自抛错，`verifyNoInteractions(guard, gateway, directory)`。
  - `should_check_eligibility_before_resolving_subject`：`requireEligible` 抛 `PublishIneligibleException` → `gateway` 零调用。
  - `should_publish_root_comment_in_one_transaction`：`InOrder`：`requireEligible` → `resolveForInteraction` → tx-begin → `admit` → `insert` → `publishEvent` → tx-end；`NewComment` 的 `rootId`、`parentId` 为空；事件字段全对（`subjectType`、`subjectOwner`、`mentionedUserIds`、`publishedAt` 是截到微秒的 `now`）；返回 `assemble` 的结果，且 `knownCards` 含自己的名片。
  - `should_lock_thread_and_recheck_parent_for_replies`：事务里 `lockThread(根)` 先于第二次 `find(parentId)`；`NewComment.rootId` 是父评论的 `rootId`；`parentAuthorId` 是父评论作者。
  - `should_reject_reply_to_deleted_reply`：锁内读到的父评论是占位回复 → `ReplyTargetGoneException`，`insert`、`publishEvent` 零调用。
  - `should_allow_reply_to_placeholder_root_without_parent_author`：父评论是占位的根 → 插入成功，事件 `parentAuthorId` 为空。
  - `should_reject_missing_parent_and_parent_elsewhere`：预读不到 → 409；父评论属于别的对象 → 422；两者都在事务之前。
  - `should_flag_anonymous_owner_in_event`：`POST`、所有者 = 作者且匿名 → `authorAnonymous` 为真。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过。**

### Task 13: 编辑 `EditCommentHandler`

**Files:**
- Create: `…/app/discussion/usecase/comment/EditCommentCommand.java`、`EditCommentHandler.java`
- Test: `…/app/discussion/usecase/comment/EditCommentHandlerTest.java`

**Interfaces:**
- Consumes: 同 Task 12，另用 `CommentRepository.findForEdit`、`appendRevision`、`updateBody`，`CommentAggregate.edit`
- Produces: `record EditCommentCommand(long commentId, String body) implements Command<CommentView>` + `of`；`EditCommentHandler`

顺序：登录 → `CommentText.parse` + `mentions.draft`（422）→ `comments.find` 不存在 404 → `requireAuthor`（403）→ 占位 404 → `subjects.requireActive`（对象失效 404）→ `guard.requireEligible`（409 / 503，用户拍板 ②；不调 `admit`）→ `mentions.resolve(draft, MentionSyntax.mentionedIds(当前正文) 的集合)` → 事务：`findForEdit` 重读（没了或成了占位 404）→ `edit(...)`；有结果时先 `appendRevision`（有 `archived` 才写）再 `updateBody`，然后 `reads.findById` 取刚改完的行（带父评论信息）、发布 `CommentEditedEvent`（`parentAuthorId` = 父评论正常时的作者，`editedAt = now`）→ 返回 `views.assemble(…, {editor → self})`；正文没变时不写不发事件，直接拼当前行。

- [ ] **Step 1: 写失败的测试**
  - `should_reject_other_users_before_remote_calls`：403，`guard`、`directory` 零调用。
  - `should_reject_placeholder_and_unavailable_subject`：404 `COMMENT_NOT_FOUND`；对象失效 → `SubjectNotFoundException`。
  - `should_require_eligibility_without_admitting`：`requireEligible` 调一次，`admit` 零调用；不合格 → 409 且不进事务。
  - `should_carry_over_current_mentions`：当前正文 `"<@8> hi"` → `resolve` 收到的 `carriedOver` 是 `{8}`。
  - `should_not_write_or_publish_when_body_unchanged`。
  - `should_archive_then_update_and_publish_after_grace`：`InOrder`：`appendRevision` → `updateBody` → `publishEvent`；事件 `addedMentionedUserIds` 只含第一次 @ 到的人。
  - `should_publish_without_revision_within_grace`。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过。**

### Task 14: 删除 `DeleteCommentHandler`

**Files:**
- Create: `…/app/discussion/usecase/comment/DeleteCommentCommand.java`、`DeleteCommentHandler.java`、`DeleteResult.java`
- Test: `…/app/discussion/usecase/comment/DeleteCommentHandlerTest.java`

**Interfaces:**
- Consumes: `CommentRepository`（`find`、`lockThread`、`findForUpdate`、`hasReplies`、`markDeleted`、`delete`）、`CommentAggregate.removalFor`
- Produces: `record DeleteCommentCommand(long commentId) implements Command<DeleteResult>` + `of`；`record DeleteResult(boolean placeholder, List<Long> removedIds)` + `of`（`copyOf`）；`DeleteCommentHandler`

顺序：登录 → `comments.find` 不存在 404 → `requireAuthor`（403，不查资格）→ 事务：`lockThread(rootId)` → `findForUpdate`（没了 404）→ `removalFor(actor, hasReplies(id))`：
- `NONE` → `DeleteResult.of(true, List.of())`，不发事件；
- `PLACEHOLDER` → `markDeleted`，发 `CommentDeletedEvent(placeholder = true)`，返回 `(true, [])`；
- `REMOVE` → `delete`，发事件（`placeholder = false`），再沿 `parentId` 往上：父评论 `findForUpdate`，是占位且 `hasReplies` 为假就 `delete` 并记下，继续；遇到正常评论、仍有回复的占位或到顶就停；返回 `(false, [它, 级联去掉的…])`。

- [ ] **Step 1: 写失败的测试**
  - `should_reject_other_users_and_missing_comments`：403、404，事务没开。
  - 处理器不注入 `PublishGuardGateway`（删除不查资格）；「账号受限也能删」由 Task 19 的流程测试覆盖。
  - `should_lock_thread_before_reading_children`：`InOrder`：`lockThread` → `findForUpdate` → `hasReplies`。
  - `should_leave_placeholder_when_replied`：`markDeleted`、事件 `placeholder = true`、结果 `(true, [])`。
  - `should_remove_and_cascade_orphaned_placeholders`：根（占位）← X（占位）← Y（叶子），删 Y → 结果 `(false, [Y, X, 根])`，三次 `delete` 按这个顺序，事件只发一次（Y）。
  - `should_stop_cascade_at_active_or_replied_parent`。
  - `should_be_idempotent_for_placeholder`：`(true, [])`，不发事件。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过。**

### Task 15: 读服务 `DiscussionQueryService`

**Files:**
- Create: `…/app/discussion/usecase/query/DiscussionQueryService.java`、`ThreadView.java`、`ThreadPageView.java`、`SingleThreadView.java`、`CommentPageView.java`、`ConversationView.java`、`RevisionsView.java`
- Test: `…/app/discussion/usecase/query/DiscussionQueryServiceTest.java`

**Interfaces:**
- Consumes: `DiscussionSubjects`、`CommentViews`（Task 10）、`DiscussionReadPort`（Task 8）、`ThreadSort`、`ThreadCursor`、`MentionSyntax`、`CurrentUserPort`
- Produces（视图都是 `of()` record）：
  - `ThreadView(CommentView root, List<CommentView> replies, boolean hasMoreReplies, String repliesCursor)`、`ThreadPageView(List<ThreadView> items, String nextCursor)`、`SingleThreadView(long targetId, ThreadView thread)`、`CommentPageView(List<CommentView> items, String nextCursor)`、`ConversationView(long targetId, List<CommentView> items)`、`RevisionsView(List<RevisionRecord> versions, List<MentionView> mentions)`
  - `ThreadPageView threads(String type, String key, String sort, String cursor, Integer size)`：`size` 为空用 20、不在 1 ~ 50 → 422；对象未登记 → 空页；`findRoots(…, size + 1)` 判下一页；`findReplyPreviews(根, 4)` 判「还有更多回复」，`repliesCursor` 是第 3 条回复的游标；一页的根与预览回复一次 `assemble`
  - `SingleThreadView thread(String type, String key, long commentId)`：对象未登记、评论不存在或不在这个对象下 → `CommentNotFoundException`；`findThread(rootId)`，`hasMoreReplies = false`
  - `CommentPageView replies(long rootId, String cursor, Integer size)`：`size` 为空用 50；不存在或不是根 → 404；对象 `requireActive`
  - `ConversationView conversation(long commentId)`：不存在 404；对象 `requireActive`；`findAncestry`
  - `RevisionsView revisions(long commentId)`：不存在或占位 → 404；对象 `requireActive`；`versions` = 当前（正文，`revisedAt ?? createdAt`）+ `findRevisions`；`mentions` = 所有版本里标记的并集（`views.mentions`）
  - `String source(long commentId)`：要登录；不存在或占位 404；不是作者 403；对象 `requireActive`；`MentionSyntax.toSource(正文, id -> 名片存在且不是 DELETED ? username : null)`，名片用 `views.cards(…, 作者)`（原样，不按可见性置空，spec 5.4.5）

- [ ] **Step 1: 写失败的测试**
  - `should_return_empty_page_for_unregistered_subject`：读端口零调用。
  - `should_page_threads_with_three_reply_previews`：`size = 1`、两条根 → 一条、`nextCursor` 是第一条的游标；该楼预览 4 条 → 显示 3 条、`hasMoreReplies` 真、`repliesCursor` 是第 3 条的游标；`assemble` 只调一次。
  - `should_reject_bad_paging_params`：`size` 0 / 51、`sort = "hot"`、`cursor = "x"` → `InvalidDiscussionRequestException`。
  - `should_return_single_thread_with_target`：成功时 `targetId` 与整楼；目标在别的对象下 → 404 `COMMENT_NOT_FOUND`。
  - `should_404_replies_for_non_root`。
  - `should_build_revisions_newest_first`：`revisedAt = R2`，历史 `[(b1, R1), (b0, T0)]` → `[(当前, R2), (b1, R1), (b0, T0)]`；没改过 → `[(正文, createdAt)]`；占位 → 404。
  - `should_give_author_source_with_current_usernames`：正文 `"hi <@7> <@8>"`，7 的名片 `username = "new-name"` 且 `profileVisible = false`，8 已注销 → `"hi @new-name @已注销用户"`；别人取 → 403。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过。**

### Task 16: @ 联想 `MentionSuggestionQueryService`

**Files:**
- Create: `…/app/discussion/usecase/query/MentionSuggestionQueryService.java`、`SuggestionView.java`
- Test: `…/app/discussion/usecase/query/MentionSuggestionQueryServiceTest.java`

**Interfaces:**
- Consumes: `DiscussionSubjects`、`DiscussionReadPort.findRecentParticipants`、底座 `UserDirectoryPort`、`MentionDirectoryPort.suggest`、`CurrentUserPort`
- Produces: `record SuggestionView(UserCard user, boolean participant)` + `of`；`List<SuggestionView> suggest(String type, String key, String q)`

规则（spec 第 6.3 节）：要登录；对象已登记时，参与者 = 非匿名所有者（不是访客本人时，排第一）+ `findRecentParticipants(对象, 排除[访客, 匿名所有者 / 已排在第一的所有者], 50)`；一次 `findCards(参与者, 访客)`，只留 `ACTIVE`、`profileVisible`、有 `username` 的；`q` 为空或空白 → 前 6 位参与者；否则参与者里 `username` 以 `q.toLowerCase(Locale.ROOT)` 开头、或 `displayName.toLowerCase(Locale.ROOT)` 以它开头的前 6 位，再 `directory.suggest(q, 访客, min(20, 8 + 已选参与者数))`，按 `userId` 去重后补到合计 8 个。

- [ ] **Step 1: 写失败的测试**
  - `should_require_login`。
  - `should_list_recent_participants_for_empty_query`：排除列表里有访客与匿名所有者；不可见、非 `ACTIVE` 的参与者被去掉；最多 6；`directory` 零调用。
  - `should_put_non_anonymous_owner_first`。
  - `should_match_participants_then_fill_from_identity`：两位参与者命中（一位按昵称）→ `suggest(q, 访客, 10)`；结果去重、合计不超过 8、参与者在前且 `participant` 为真。
  - `should_skip_participants_for_unregistered_subject`：只有 identity 的结果。
- [ ] **Step 2: 跑测试确认失败。**
- [ ] **Step 3: 实现。**
- [ ] **Step 4: 跑测试确认通过。**

### Task 17: REST 接口与讨论主流程的流程测试

**Files:**
- Create: `patra-interaction-adapter/src/main/java/dev/patra/interaction/adapter/discussion/rest/DiscussionController.java`、`CommentController.java`
- Create: `…/adapter/discussion/rest/request/PublishCommentRequest.java`（`String body, Long parentId`）、`EditCommentRequest.java`（`String body`）
- Create: `…/adapter/discussion/rest/response/AuthorResponse.java`、`ReplyToResponse.java`、`MentionResponse.java`、`CommentResponse.java`、`ThreadResponse.java`、`ThreadPageResponse.java`、`SingleThreadResponse.java`、`CommentPageResponse.java`、`ConversationResponse.java`、`RevisionResponse.java`、`RevisionsResponse.java`、`CommentSourceResponse.java`、`DeleteCommentResponse.java`、`SuggestionResponse.java`、`SuggestionsResponse.java`
- Create: `patra-interaction-boot/src/integrationTest/java/dev/patra/interaction/discussion/DiscussionFlowITSupport.java`
- Test: `…/boot/src/integrationTest/java/dev/patra/interaction/discussion/DiscussionFlowIT.java`

**Interfaces:**
- Consumes: Task 12 ~ 16 的命令与查询服务、`CommandBus`
- Produces（路径与形状照 spec 第 6 节；响应 DTO 都有 `static … from(视图)`，写法照 collection 的 `OwnerResponse`；`@Tag` 照 `PublicCollectionController`）：
  - `DiscussionController`：`GET /discussions/{type}/{key}/threads`（`sort`、`cursor`、`size` 可选）、`GET /discussions/{type}/{key}/comments/{commentId}/thread`、`POST /discussions/{type}/{key}/comments`（201）、`GET /discussions/{type}/{key}/mention-suggestions`（`q` 可选）
  - `CommentController`：`GET /comments/{commentId}/replies`、`GET /comments/{commentId}/conversation`、`GET /comments/{commentId}/revisions`、`GET /comments/{commentId}/source`、`PUT /comments/{commentId}`、`DELETE /comments/{commentId}`（200 + `DeleteCommentResponse`）
  - `AuthorResponse(Long userId, String username, String displayName, String avatarUrl, int avatarPalette, AccountState state, boolean profileVisible)`；`SuggestionResponse` 的 `group` 是 `"PARTICIPANT"` / `"OTHER"`
- `DiscussionFlowITSupport extends InteractionFlowITSupport`：`publication(long id)`（`CATALOG_DATA.put(id, "PMID" + id, "J", "2019")`）、`user(long id)`（`IDENTITY_DATA.put(id, "ACTIVE", true, "PUBLIC")`）、`EntityExchangeResult<String> post(long user, String type, String key, String body, Long parentId)`（先 `DELETE FROM subject.publish_quota WHERE user_id = ?`：流程测试里同一人连发多条要绕开 10 秒间隔）、`long idOf(EntityExchangeResult<String>)`、`JsonNode threads(Long viewer, String path)`

- [ ] **Step 1: 写失败的测试**（`DiscussionFlowIT`）
  - `should_show_two_layers_and_reply_targets`：A 发根评论；B 回复根；C 回复 B；D 回复 C → `GET /discussions/publication/100/threads` 一楼三条回复 `[B, C, D]`；B 的 `replyTo` 为空，C 的 `replyTo.author.userId` 是 B，D 的是 C；`hasMoreReplies` 假；匿名访客也能读（200）。
  - `should_page_threads_and_expand_more_replies`：两楼、`size=1` → `nextCursor` 翻到第二楼；一楼 5 条回复 → 前 3 条 + `hasMoreReplies` + `repliesCursor` → `GET /comments/{根}/replies?cursor=…` 得到剩下 2 条、`nextCursor` 为空。
  - `should_open_single_thread_and_conversation`：`GET …/comments/{D}/thread` → `targetId` 是 D、三条回复全在；用另一篇文献的路径取 → 404，`traits` 含 `COMMENT_NOT_FOUND`；`GET /comments/{D}/conversation` → `[根, B, C, D]`。
  - `should_leave_placeholder_or_remove_on_delete`：删有回复的根 → `placeholder` 真、列表里根 `deleted` 真、楼还在；根（占位）← X（占位）← Y：删 Y → `removedIds = [Y, X, 根]`，列表里这一楼消失。
  - `should_reject_reply_to_deleted_reply_but_allow_reply_to_the_floor`：X 是占位的回复 → 回复 X 409 `REPLY_TARGET_GONE`；回复占位的根 201。
  - `should_enforce_login_ownership_and_validation`：匿名发表 401；B 删 A 的评论 403；空正文 422 `errors[0].code = "REQUIRED"`；`/discussions/journal/1/threads` 422。
  - `should_keep_tree_consistent_when_reply_races_delete`：A 的叶子回复 X；两个线程同时「A 删 X」「B 回复 X」，各做 10 轮（每轮新建 X）→ 每轮要么回复 201 且 X 变占位，要么回复 409 且 X 已去掉；库里没有 `parent_id` 指向不存在的行。
- [ ] **Step 2: 跑测试确认失败**：`./gradlew :patra-api:patra-interaction:patra-interaction-boot:integrationTest --tests '*DiscussionFlowIT*'` → 404（接口不存在）或编译失败。
- [ ] **Step 3: 实现控制器与 DTO。**
- [ ] **Step 4: 跑测试确认通过**；再跑 `InteractionArchitectureTest`。

### Task 18: 假 identity 扩两个接口与 @ 的流程测试

**Files:**
- Modify: `patra-interaction-boot/src/integrationTest/java/dev/patra/interaction/support/FakeIdentity.java`
- Test: `…/boot/src/integrationTest/java/dev/patra/interaction/discussion/DiscussionMentionFlowIT.java`

**Interfaces:**
- Produces（`FakeIdentity`，只追加，现有 `put` / `remove` / `down` / `clear` 与批量摘要行为不变）：
  - `User` 记录加 `username`（默认 `"user-" + id`）；`rename(long id, String newUsername)`：旧名记为保留期内的旧名；`expireFormerNames(long id)`：旧名过期；`setStatus(long id, String status)`
  - `transform` 按请求路径分派：`…/summaries`（原逻辑，`username` 用当前名）、`…/resolve/by-usernames`（对请求里每个原文：不分大小写等于某人当前名 → 他；否则等于未过期的旧名 → 原主；返回 `{username: 原文, userId, accountStatus}`，解析不到的省略）、`…/suggest`（`ACTIVE`、对访客可见、不是访客本人，用户名以小写前缀开头或昵称不分大小写以前缀开头，取前 `limit` 个）

- [ ] **Step 1: 写失败的测试**（`DiscussionMentionFlowIT extends DiscussionFlowITSupport`）
  - `should_resolve_current_and_reserved_names_for_active_users_only`：B 正常、C 改名后用旧名 @、D 被封禁、E 的旧名已过期 → 正文里只有 B、C 变成标记，`mentions` 是 `[B, C]`，其余原样。
  - `should_reject_more_than_ten_people_with_422`：11 个正常账号 → 422，`errors[0].code = "TOO_MANY_MENTIONS"`。
  - `should_leave_code_and_urls_alone_and_escape_forged_tokens`：`` "`@user-2` https://x.org/@user-2 <@99>" `` → 正文只把 `<@99>` 变成 `\<@99>`，`mentions` 为空。
  - `should_give_source_with_current_usernames_and_keep_mentions_on_save`：A @B 后，B 改名 → `GET /comments/{id}/source` 是 `@新名`；B 被封禁后 A 原样 `PUT` 回去 → 正文仍是 `<@B>`；@ 过的 D 注销后原文里是 `@已注销用户`。
  - `should_hide_username_of_invisible_mentions`：B 主页设为 `SELF` → C 看到的提及 `username` 为空、`displayName` 有值。
- [ ] **Step 2: 跑测试确认失败**：假 identity 还没有 `/resolve/by-usernames`（返回空体，解析失败）。
- [ ] **Step 3: 扩展 `FakeIdentity`。**
- [ ] **Step 4: 跑测试确认通过**；顺带跑一遍 `CollectionFlowIT`、`PublishGuardFlowIT`（共用假 identity，行为不能变）。

### Task 19: 匿名经验与修改历史的流程测试

**Files:**
- Test: `…/boot/src/integrationTest/java/dev/patra/interaction/discussion/DiscussionAnonymityFlowIT.java`、`DiscussionRevisionFlowIT.java`

**Interfaces:**
- Consumes: 底座 `SubjectGateway.registerLocal`（在 `TransactionTemplate` 里登记 `POST`，照 `LocalSubjectFlowIT`）；所有者用不会和别的数字撞的 ID（如 `770001`），便于按字符串断言

- [ ] **Step 1: 写失败的测试**
  - `DiscussionAnonymityFlowIT`
    - `should_never_expose_anonymous_owner_to_others`：匿名所有者 O 的经验下，B 发根评论、O 回复 B、C 回复 O 的回复；B、C、匿名访客取列表、单楼、对话链，C 发回复得到的响应，B 取联想，任何人取 O 那条的修改历史 → 每个响应体都不含 `"770001"`；O 的条目 `anonymous`、`isOwner` 为真；C 那条 `replyTo.anonymous` 为真。
    - `should_show_owner_their_own_anonymous_comments`：O 取列表 → O 的条目 `mine` 为真、`author.userId` 是 O。
    - `should_list_non_anonymous_owner_first_in_suggestions`：非匿名所有者 P 的经验，P 没评论过 → B 的联想第一位是 P、`group = "PARTICIPANT"`。
  - `DiscussionRevisionFlowIT`
    - `should_not_record_versions_within_five_minutes`：发表后立即编辑 → `edited` 假，修改历史 1 个版本。
    - `should_record_each_edit_after_five_minutes`：把 `created_at` 改到 6 分钟前，再编辑两次 → `edited` 真；历史 3 个版本，最新在前，时间依次是第二次编辑、第一次编辑、发表；匿名访客能读。
    - `should_drop_history_with_the_comment`：有回复的评论删除变占位 → 修改历史 404 `COMMENT_NOT_FOUND`。
    - `should_block_ineligible_editors_but_let_them_delete`：A 邮箱变成未验证 → 编辑 409 `EMAIL_UNVERIFIED`；删除 200。
- [ ] **Step 2: 跑测试确认失败**（新测试类：若全部一次通过，记录下来并逐条确认断言确实打到了对应规则，比如临时改坏 `CommentViews` 的匿名遮蔽看测试变红再改回）。
- [ ] **Step 3: 修正暴露出来的问题**（没有就跳过）。
- [ ] **Step 4: 跑测试确认通过。**

### Task 20: 事件同事务与 HTTP 上的资格、频率

**Files:**
- Test: `…/boot/src/integrationTest/java/dev/patra/interaction/discussion/DiscussionEventRollbackFlowIT.java`（内嵌 `@TestConfiguration` 的记录型订阅方：同步 `@EventListener` 收下三种事件；正文含 `!boom!` 时抛 `IllegalStateException`；类上 `@Import` 它）

- [ ] **Step 1: 写失败的测试**
  - `should_roll_back_comment_and_quota_when_subscriber_fails`：正文 `"!boom!"` → 500；`discussion.comment` 与 `subject.publish_quota` 都是 0 行（文献的登记行在事务外，留下无妨）。
  - `should_deliver_events_with_contract_fields`：根评论 → 回复（@ 到 C）→ 编辑回复（新加 @D）→ 删除 → 依次收到 `CommentPublishedEvent` ×2、`CommentEditedEvent`（`addedMentionedUserIds = [D]`）、`CommentDeletedEvent`（`placeholder` 假），各字段与库里一致。
  - `should_map_guard_rejections_over_http`：邮箱未验证 409 `EMAIL_UNVERIFIED`；同一人 10 秒内第二条 429 `TOO_FREQUENT` 且带 `Retry-After`（用 JDK `HttpClient` 发，注入 `@LocalServerPort`，头用 `TestIdentity.headers`）；经验登记行改成 `UNAVAILABLE` 后回复 → 404 `SUBJECT_UNAVAILABLE`。
- [ ] **Step 2: 跑测试确认失败**（同 Task 19 的说明：一次通过时用变异确认，比如临时把发布挪到事务外，回滚用例应变红）。
- [ ] **Step 3: 修正暴露出来的问题**（没有就跳过）。
- [ ] **Step 4: 跑测试确认通过。**

### Task 21: 文档与门禁

**Files:**
- Modify: `patra-api/patra-interaction/README.md`（第 1 节 `discussion` 一行补全；第 4 节后加「4.2 discussion 的存储与并发」：两张表、楼锁、行锁、外键、只存用户 ID、事件）
- Modify: `docs/patra/runbooks/v0.14-discussion-go-live-runbook.md`（第 6 节加 `discussion` 一条：schema、两张表、约束与外键、索引、两条版本、经网关匿名读回 200 空列表）
- Modify: `patra-interaction-boot/src/main/java/dev/patra/interaction/config/IdentityClientConfiguration.java`（类注释补「讨论的 @ 解析与联想」）
- Modify: `docs/patra/specs/2026-10-27-interaction-discussion-design.md`（文末加「14. 实施记录」：偏离与原因、验证结果）
- Modify: 共享笔记 `interaction-PAP-121-handoff.md`（定稿：接口、条目形状、事件契约、给 PAP-123 的提示）

- [ ] **Step 1: 改文档。**
- [ ] **Step 2: 门禁**：`./gradlew spotlessApply`；`./gradlew check`（BUILD SUCCESSFUL）；`./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest :patra-api:patra-interaction:patra-interaction-boot:integrationTest`（全部通过）；`./gradlew dumpModuleGraph` 后 `git diff --stat` 里 `module-graph.json` 应无变化（有变化就说明多了依赖，查清楚）。
- [ ] **Step 3: 收尾评审**：按 superpowers:requesting-code-review 请一个新的评审（opus）看整支改动；按评审意见先写失败的测试再修。
- [ ] **Step 4: 停在本地**：不提交；在会话里汇报改动清单、测试结果、事件契约形状，等用户同意后再提交（只加本 Issue 的路径，`git diff --cached` 核对）。
