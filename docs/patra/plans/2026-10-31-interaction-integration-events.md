# interaction 发集成事件 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> 本计划按用户约定在当前会话内用 superpowers:executing-plans 内联执行，严格 TDD。

**Goal:** interaction 把评论发表 / 编辑 / 删除、投稿经验删除、有帮助点五种事实经事件模块外送到 RabbitMQ `interaction.events`（有帮助取消只发领域事件、不外送），事件带齐通知服务算收件人、去重、判乱序要用的事实。

**Architecture:** app 层在业务事务里发布带 `eventId`、版本、纯文本的领域事件；Modulith 事件发布登记写 Outbox；boot 层 `EventExternalizationConfiguration` 选中五种领域事件、路由、映射成 `patra-interaction-api` 里的 record；`patra-tool-events` 等 publisher confirm。

**Tech Stack:** Java 25、Spring Boot 4.1.2、Spring Modulith 2.1.2（events-jdbc / events-amqp）、Spring AMQP、RabbitMQ 4.4.0（Testcontainers）、PostgreSQL 17、JdbcClient、JUnit 5 + AssertJ + Mockito + Awaitility。

**Spec:** `docs/patra/specs/2026-10-31-interaction-integration-events-design.md`（契约在第 5 节）

## Global Constraints

- 交换机 `interaction.events`（topic，持久化）；外送路由键：`comment.published`、`comment.edited`、`comment.deleted`、`post.deleted`、`helpful.marked`。`HelpfulUnmarkedEvent` 不外送。
- `eventId` 在发布领域事件处 `UUID.randomUUID()`；领域事件紧凑构造器 `Objects.requireNonNull(eventId, "eventId")`，不兜底生成。
- 映射只拷字段：不查库、不调转换器；值对象拆成基本类型，枚举转 `name()`。
- 事件里不带对象展示信息（key、标题、期刊、快照）；`SubjectContext` 只有 `subjectId`、`type`、`ownerId`、`ownerAnonymous`。
- 领域层不贴 `@Externalized`；服务代码里不写 `@ApplicationModuleListener`；不用 `EventExternalizationConfiguration.headers(...)`。
- record 规范：≤ 4 个参数用 `of()`，≥ 5 个用 `@Builder`、不同时给 `of()`；集合字段在紧凑构造器里 `List.copyOf`（`null` 视为空）。
- 所有方法写 `///` Javadoc（Markdown）；不用全类名；中文注释与日志。
- 测试命名 `should_...` / `..._when_...`；单测 JUnit 5 + AssertJ + Mockito，IT 用 Testcontainers。
- Flyway 版本号是 14 位时间戳，取写脚本时的当前时间（晚于现有最大值）。
- 改 `*.gradle.kts` 后重跑 `./gradlew dumpModuleGraph`，`patra-infra/cd/module-graph.json` 一起提交。
- 只在本工作树改；提交只加自己的路径，中文 message、subject 以中文动词开头。

## Review Focus

1. 有帮助点的经验在 `post` 里查不到（数据异常）：事件照发，`experience`、`targetPlainText` 为 `null`，不让点失败 → Task 5 `should_describe_post_without_digest`。
2. 投稿经验没有正文：`targetPlainText` 为 `null`，映射与 JSON 照常 → Task 5、Task 6 测试。
3. 文献对象没有所有者（`SubjectOwner` 为 `null`）：映射出 `ownerId = null`、`ownerAnonymous = false` 不抛 → Task 6 `should_map_publication_without_owner`。
4. 同一条评论连续两次编辑：库里版本 2 → 3，事件版本与库一致 → Task 1 `should_persist_incremented_version`、Task 4 编辑测试。
5. 重复点 / 没点过就取消：不发事件 → Task 5 `should_not_publish_when_already_marked`、`should_not_publish_when_nothing_removed`。

---

### Task 1: 评论版本列

**Files:**
- Create: `patra-api/patra-interaction/patra-interaction-infra/src/main/resources/db/migration/discussion/V<当前时间>__add_comment_version.sql`
- Modify: `patra-api/patra-interaction/patra-interaction-domain/src/main/java/dev/patra/interaction/domain/discussion/model/aggregate/CommentAggregate.java`
- Modify: `patra-api/patra-interaction/patra-interaction-infra/src/main/java/dev/patra/interaction/infra/discussion/persistence/CommentRows.java`（`AGGREGATE_COLUMNS` 加 `version`，映射读 `version`）
- Modify: `patra-api/patra-interaction/patra-interaction-infra/src/main/java/dev/patra/interaction/infra/discussion/persistence/CommentRepositoryAdapter.java`（`updateBody` 写 `version = :version`）
- Test: `patra-interaction-domain/src/test/.../discussion/model/aggregate/CommentAggregateTest.java`、`patra-interaction-infra/src/integrationTest/.../discussion/persistence/CommentRepositoryAdapterIT.java`、`.../discussion/persistence/DiscussionMigrationIT.java`

**Interfaces:**
- Produces: `CommentAggregate.INITIAL_VERSION = 1`（`public static final int`）；`int getVersion()`（Lombok `@Getter`）；builder 参数 `version`（`<= 0` 时 `IllegalStateException`）；`edit(...)` 返回的 `updated` 版本 = 原版本 + 1。

- [ ] **Step 1: 写失败的单测**（`CommentAggregateTest`）

```java
@Test
void should_increment_version_when_body_changes_within_grace() {
  CommentAggregate edited = activeRoot(1).edit(AUTHOR, "新正文", List.of(), CREATED.plusSeconds(60)).orElseThrow().updated();
  assertThat(edited.getVersion()).isEqualTo(2);
}

@Test
void should_increment_version_when_body_changes_after_grace() { /* 版本 2 的评论 1 小时后改 → 3 */ }

@Test
void should_reject_non_positive_version() {
  assertThatThrownBy(() -> activeRootBuilder().version(0).build()).isInstanceOf(IllegalStateException.class);
}
```

现有夹具的 builder 不传 `version` 时为 `0` 会触发校验：测试与生产里所有 `CommentAggregate.builder()` 调用点补 `.version(...)`。

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :patra-api:patra-interaction:patra-interaction-domain:test --tests "*CommentAggregateTest*"`
Expected: 编译失败（`getVersion` / `version` 不存在）

- [ ] **Step 3: 实现** `CommentAggregate` 加 `private final int version` 与构造参数（Javadoc：「正文版本：发表为 1，每次正文确有变化加 1」），`edit()` 里 `.version(version + 1)`；构造器校验 `version > 0`。

- [ ] **Step 4: 跑单测确认通过**

- [ ] **Step 5: 写失败的 IT**

`DiscussionMigrationIT`：`should_add_comment_version_with_default_one`（`information_schema.columns`：`discussion.comment.version` 为 integer、NOT NULL、默认 1；`ck_comment_version` 定义含 `version > 0`）。
`CommentRepositoryAdapterIT`：`should_insert_with_initial_version`（insert 后 find → 版本 1）；`should_persist_incremented_version`（insert → findForEdit → edit → updateBody 两次，find 后版本 3、正文为第二次的）。

- [ ] **Step 6: 跑 IT 确认失败**

Run: `./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest --tests "*CommentRepositoryAdapterIT*" --tests "*DiscussionMigrationIT*"`
Expected: FAIL（列不存在）

- [ ] **Step 7: 实现**迁移脚本：

```sql
-- 评论正文版本（PAP-141）：发表为 1，每次正文确有变化加 1；集成事件带上它，消费方按它丢弃晚到的旧编辑
ALTER TABLE discussion.comment ADD COLUMN version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE discussion.comment ADD CONSTRAINT ck_comment_version CHECK (version > 0);
COMMENT ON COLUMN discussion.comment.version IS 'Body version: 1 on publish, +1 on every effective edit; carried by integration events for ordering';
```

`CommentRows`、`updateBody` 按 Files 改；`insert` 不写列，用默认值。

- [ ] **Step 8: 跑 IT 确认通过**

- [ ] **Step 9: 提交** `git commit -m "feat(interaction): 给评论加正文版本列 (PAP-141)"`

---

### Task 2: 投稿经验的结果与正文读端口

**Files:**
- Create: `patra-interaction-domain/src/main/java/dev/patra/interaction/domain/post/model/read/ExperienceOutline.java`
- Create: `patra-interaction-domain/src/main/java/dev/patra/interaction/domain/post/model/read/ExperienceDigest.java`
- Modify: `patra-interaction-domain/src/main/java/dev/patra/interaction/domain/post/port/read/PostReadPort.java`
- Modify: `patra-interaction-infra/src/main/java/dev/patra/interaction/infra/post/persistence/PostReadAdapter.java`
- Test: `patra-interaction-infra/src/integrationTest/java/dev/patra/interaction/infra/post/persistence/PostReadAdapterIT.java`

**Interfaces:**
- Produces:
  - `record ExperienceOutline(FirstDecision firstDecision, FinalOutcome finalOutcome, int firstRoundDays, Integer totalDays)` + `static ExperienceOutline of(...)` + `static ExperienceOutline of(ExperienceFields fields)`。
  - `record ExperienceDigest(ExperienceOutline outline, String body)` + `of(...)`；`body` 可空。
  - `PostReadPort`：`Optional<ExperienceDigest> findDigest(long postId)`——已删除的也返回，没有这条为空。

- [ ] **Step 1: 写失败的 IT**（`PostReadAdapterIT`，沿用该类已有的插入夹具）：`should_find_digest_with_outline_and_body`（MAJOR_REVISION → IN_PROGRESS、首轮 10 天、有正文 → outline 与 body 对上）、`should_find_digest_without_body`、`should_find_digest_of_deleted_post`、`should_return_empty_when_post_missing`。
- [ ] **Step 2: 跑 IT 确认失败**

Run: `./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest --tests "*PostReadAdapterIT*"`
Expected: 编译失败

- [ ] **Step 3: 实现**两个 record 与端口方法；`PostReadAdapter.findDigest` 复用 `PostRows.COLUMNS + PostRows.FROM` 与 `rows.postMapper()` 按 `p.post_id` 查出 `PostAggregate`，转成 `ExperienceDigest`。
- [ ] **Step 4: 跑 IT 确认通过**
- [ ] **Step 5: 提交** `git commit -m "feat(interaction): 投稿经验读端口新增结果与正文摘要 (PAP-141)"`

---

### Task 3: 删除类事件带事件 ID（评论删除带版本）

**Files:**
- Modify: `patra-interaction-domain/.../discussion/event/CommentDeletedEvent.java`（加 `UUID eventId`、`int version`，紧凑构造器校验 `eventId`）
- Modify: `patra-interaction-domain/.../post/event/PostDeletedEvent.java`（加 `UUID eventId`，改 `@Builder`、删 `of()`）
- Modify: `patra-interaction-app/.../discussion/usecase/comment/DeleteCommentHandler.java`（`deleted(...)` 带 `eventId(UUID.randomUUID())`、`version(comment.getVersion())`）
- Modify: `patra-interaction-app/.../post/usecase/experience/DeleteExperienceHandler.java`（改用 builder 并带 `eventId`）
- Modify（编译跟随）：构造这两个事件的测试补字段
- Create: `patra-interaction-domain/src/test/java/dev/patra/interaction/domain/discussion/event/DiscussionEventsTest.java`、`.../post/event/PostDeletedEventTest.java`
- Test: `DeleteCommentHandlerTest`、`DeleteExperienceHandlerTest`

**Interfaces:**
- Produces: `CommentDeletedEvent.eventId()`、`version()`；`PostDeletedEvent.builder().eventId(UUID).postId(long).subjectId(SubjectId).authorId(long).deletedAt(Instant).build()`。

- [ ] **Step 1: 写失败的单测**：`DeleteCommentHandlerTest.should_publish_deleted_event_with_event_id_and_version`（版本 3 的评论删除 → 事件 `eventId` 非空、`version` 3；两次删除的 `eventId` 不同）；`DeleteExperienceHandlerTest` 断言 `eventId` 非空；`DiscussionEventsTest` / `PostDeletedEventTest`：`eventId(null)` 抛 `NullPointerException`。
- [ ] **Step 2: 跑测试确认失败**（编译失败）

Run: `./gradlew :patra-api:patra-interaction:patra-interaction-domain:test :patra-api:patra-interaction:patra-interaction-app:test`

- [ ] **Step 3: 实现**（按 Files）
- [ ] **Step 4: 跑 domain、app 全部单测确认通过**（同上命令）
- [ ] **Step 5: 提交** `git commit -m "feat(interaction): 删除类领域事件带事件 ID (PAP-141)"`

---

### Task 4: 评论发表 / 编辑事件带事件 ID、版本与纯文本

**Files:**
- Modify: `patra-interaction-domain/.../discussion/event/CommentPublishedEvent.java`、`CommentEditedEvent.java`（加 `UUID eventId`、`int version`、`String plainText`）
- Modify: `patra-interaction-app/.../discussion/usecase/comment/PublishCommentHandler.java`、`EditCommentHandler.java`（注入 `PlainTextPort`）
- Modify（编译跟随）：构造这两个事件的测试（`CommentNotificationListenerTest`、`NotificationRules` 相关测试）补字段
- Test: `PublishCommentHandlerTest`、`EditCommentHandlerTest`、`DiscussionEventsTest`

**Interfaces:**
- Consumes: Task 1 `CommentAggregate.INITIAL_VERSION`、`getVersion()`；`PlainTextPort.toPlainText(String, Set<Long>)`。
- Produces: `CommentPublishedEvent` / `CommentEditedEvent` 的 `eventId()`、`version()`、`plainText()`。

- [ ] **Step 1: 写失败的单测**

`PublishCommentHandlerTest`（构造器多传 `PlainTextPort plainText = mock(...)`）：`should_publish_event_with_id_version_and_plain_text`（`toPlainText(存储正文, 提及名单)` 返回 `PlainText.of("…")` → 事件 `eventId` 非空、`version` 1、`plainText` 为返回值）。
`EditCommentHandlerTest`：`should_publish_edited_event_with_next_version_and_plain_text`（当前版本 2 的评论改正文 → 事件 `version` 3、`plainText` 为新正文的转换结果、`eventId` 非空）。
`DiscussionEventsTest`：两个事件 `eventId(null)` 抛 `NullPointerException`。

- [ ] **Step 2: 跑测试确认失败**（编译失败）

Run: `./gradlew :patra-api:patra-interaction:patra-interaction-domain:test :patra-api:patra-interaction:patra-interaction-app:test`

- [ ] **Step 3: 实现**：事件 record 加字段与校验；`PublishCommentHandler` 在事务外算 `String plain = plainText.toPlainText(resolved.body(), Set.copyOf(resolved.mentionedUserIds())).value()`，`published(...)` 增参 `plain`，builder 带 `eventId(UUID.randomUUID())`、`version(CommentAggregate.INITIAL_VERSION)`；`EditCommentHandler` 同样在事务外算纯文本，`edited(...)` 取 `edit.updated().getVersion()`。
- [ ] **Step 4: 跑 domain、app 单测确认通过**
- [ ] **Step 5: 提交** `git commit -m "feat(interaction): 评论事件带事件 ID、版本与纯文本 (PAP-141)"`

---

### Task 5: 有帮助点 / 取消发领域事件

**Files:**
- Create: `patra-interaction-domain/src/main/java/dev/patra/interaction/domain/reaction/event/HelpfulMarkedEvent.java`、`HelpfulUnmarkedEvent.java`（字段见 spec 4.3）
- Modify: `patra-interaction-domain/.../discussion/model/read/CommentRecord.java`（派生方法 `List<Long> mentionedUserIds()`：`body == null ? List.of() : MentionSyntax.mentionedIds(body)`）
- Create: `patra-interaction-app/src/main/java/dev/patra/interaction/app/reaction/service/TargetFacts.java`
- Modify: `patra-interaction-app/.../reaction/service/ReactionTargets.java`（注入 `PostReadPort`、`PlainTextPort`，新增 `describe`）
- Modify: `patra-interaction-app/.../reaction/usecase/helpful/MarkHelpfulHandler.java`、`UnmarkHelpfulHandler.java`（注入 `ApplicationEventPublisher`；取消再注入 `Clock`）
- Modify: `patra-interaction-boot/src/test/java/dev/patra/interaction/architecture/InteractionArchitectureTest.java`（`allowingSubscription("reaction", "post")` → `allowingDependency("reaction", "post")`，改 Javadoc）
- Test: `ReactionTargetsTest`、`MarkHelpfulHandlerTest`、`UnmarkHelpfulHandlerTest`、`patra-interaction-domain/src/test/.../reaction/event/ReactionEventsTest.java`、`.../discussion/model/read/CommentRecordTest.java`

**Interfaces:**
- Consumes: Task 2 `PostReadPort.findDigest`、`ExperienceDigest`、`ExperienceOutline`；`PlainTextPort`；`DiscussionReadPort.findById`。
- Produces:
  - `HelpfulMarkedEvent`（`@Builder`）：`UUID eventId, HelpfulTargetType targetType, long targetId, long targetAuthorId, boolean targetAnonymous, long userId, Instant markedAt, SubjectId subjectId, SubjectType subjectType, SubjectOwner subjectOwner, ExperienceOutline experience, String targetPlainText`。
  - `HelpfulUnmarkedEvent`（`@Builder`）：`UUID eventId, HelpfulTargetType targetType, long targetId, long userId, Instant unmarkedAt`。
  - `record TargetFacts(SubjectView subject, ExperienceOutline experience, String plainText)` + `of(...)`。
  - `ReactionTargets.describe(HelpfulTarget target)` → `TargetFacts`：评论 → 所在对象的 `SubjectView`、`experience` 为 `null`、`plainText.toPlainText(comment.body(), Set.copyOf(comment.mentionedUserIds())).value()`；经验 → 经验自己的 `SubjectView`、`findDigest(target.id())` 的 `outline`、正文非空时 `toPlainText(body, Set.of()).value()`，digest 查不到时两者都为 `null`。目标或对象查不到抛 `HelpfulTargetNotFoundException`（同 `require`）。

- [ ] **Step 1: 写失败的单测**

`CommentRecordTest`：`should_derive_mentioned_ids_from_body`（`"a <@7> b"` → `[7]`）；占位（`body` 为 `null`）为空列表。
`ReactionTargetsTest`：`should_describe_comment_with_subject_and_plain_text`（不读 `PostReadPort`）、`should_describe_post_with_outline_and_body_text`、`should_describe_post_without_body`（不调转换器）、`should_describe_post_without_digest`。
`MarkHelpfulHandlerTest`：`should_publish_marked_event_after_insert`（`eventId` 非空；目标、作者、匿名、`userId`；`markedAt` = 固定时钟截到微秒；`subjectId` / `subjectType` / `subjectOwner`、`experience`、`targetPlainText` 来自 `describe`）、`should_not_publish_when_already_marked`、`should_not_publish_when_insert_conflicts`。
`UnmarkHelpfulHandlerTest`：`should_publish_unmarked_event_after_delete`（`eventId`、目标、`userId`、`unmarkedAt`）、`should_not_publish_when_nothing_removed`。
`ReactionEventsTest`：两个事件 `eventId(null)` 抛 `NullPointerException`。

- [ ] **Step 2: 跑测试确认失败**（编译失败）

Run: `./gradlew :patra-api:patra-interaction:patra-interaction-domain:test :patra-api:patra-interaction:patra-interaction-app:test`

- [ ] **Step 3: 实现**：`MarkHelpfulHandler` 在 `guard.requireEligible` 之后、事务前 `TargetFacts facts = targets.describe(target)`；事务里 `insert` 为真时 `events.publishEvent(marked(...))`。`UnmarkHelpfulHandler` 在 `removed.ifPresent` 里发 `HelpfulUnmarkedEvent`，`unmarkedAt = clock.instant().truncatedTo(MICROS)`。架构测试改依赖边。
- [ ] **Step 4: 跑单测与架构测试确认通过**

Run: `./gradlew :patra-api:patra-interaction:patra-interaction-domain:test :patra-api:patra-interaction:patra-interaction-app:test :patra-api:patra-interaction:patra-interaction-boot:test`

- [ ] **Step 5: 提交** `git commit -m "feat(interaction): 有帮助点与取消发布领域事件 (PAP-141)"`

---

### Task 6: 集成事件契约、映射与外送配置

**Files:**
- Create（`patra-interaction-api/src/main/java/dev/patra/interaction/api/`）：
  - `subject/event/InteractionExchange.java`（`public static final String NAME = "interaction.events"`，私有构造器）
  - `subject/event/SubjectContext.java`（4 参数，`of()`）
  - `post/event/ExperienceOutcome.java`（4 参数，`of()`）
  - `post/event/PostDeleted.java`、`discussion/event/CommentPublished.java`、`CommentEdited.java`、`CommentDeleted.java`、`reaction/event/HelpfulMarked.java`（都 `@Builder`、`implements IntegrationEvent`、`public static final String ROUTING_KEY`，字段见 spec 5.3–5.7；列表字段 `List.copyOf`）
- Create: `patra-interaction-boot/src/main/java/dev/patra/interaction/config/IntegrationEventMapper.java`
- Create: `patra-interaction-boot/src/main/java/dev/patra/interaction/config/InteractionEventsConfiguration.java`
- Create: `patra-interaction-infra/src/main/resources/db/migration/events/V<当前时间>__create_event_publication.sql`（逐字照抄 `patra-tool/patra-tool-events/src/integrationTest/resources/db/migration/V1__create_event_publication.sql`，头注释写来源与「表名、列名是 Modulith 固定的，不能改」）
- Modify: `patra-interaction-boot/build.gradle.kts`（加 `implementation(project(":patra-tool:patra-tool-events"))`；`patra-interaction-api` 改 `implementation`、删原 `testImplementation` 行与注释）
- Modify: `patra-interaction-api/build.gradle.kts` 头注释
- Modify: `patra-infra/cd/module-graph.json`（`./gradlew dumpModuleGraph`）
- Test: `patra-interaction-boot/src/test/java/dev/patra/interaction/config/IntegrationEventMapperTest.java`、`InteractionEventsConfigurationTest.java`

**Interfaces:**
- Consumes: Task 3–5 的领域事件。
- Produces:
  - `IntegrationEventMapper`（`final`，私有构造器，静态方法）：`commentPublished(CommentPublishedEvent)`、`commentEdited(CommentEditedEvent)`、`commentDeleted(CommentDeletedEvent)`、`postDeleted(PostDeletedEvent)`、`helpfulMarked(HelpfulMarkedEvent)`，各返回对应 api record；私有 `SubjectContext subject(SubjectId, SubjectType, SubjectOwner)`、`ExperienceOutcome experience(ExperienceOutline)`（入参 `null` 返回 `null`）。
  - `InteractionEventsConfiguration`（`@Configuration(proxyBeanMethods = false)`）：`@Bean TopicExchange interactionEventsExchange()`、`@Bean EventExternalizationConfiguration eventExternalizationConfiguration()`；包内可见常量 `Set<Class<?>> EXTERNALIZED`（五个领域事件类）。

- [ ] **Step 1: 写失败的单测**

`IntegrationEventMapperTest`：每种一个用例，全字段领域事件 → 期望 api record 整体 `isEqualTo`（覆盖：`eventId` 原样；`SubjectType.POST` → `"POST"`；`SubjectOwner` 拆成 `ownerId` / `ownerAnonymous`；`ExperienceOutline` → `ExperienceOutcome("MAJOR_REVISION", "IN_PROGRESS", 10, null)`；`HelpfulTargetType.COMMENT` → `"COMMENT"`），另加 `should_map_publication_without_owner`、`should_map_helpful_on_comment_without_experience`。
`InteractionEventsConfigurationTest`：`should_externalize_exactly_five_domain_events`（五个样例 `supports` 为真、目标 `interaction.events` + 对应 `ROUTING_KEY`、映射出对应 record；`HelpfulUnmarkedEvent` 与 `"x"` 不支持）；`should_declare_durable_topic_exchange`。

- [ ] **Step 2: 跑测试确认失败**（编译失败）

Run: `./gradlew :patra-api:patra-interaction:patra-interaction-boot:test --tests "*IntegrationEventMapperTest*" --tests "*InteractionEventsConfigurationTest*"`

- [ ] **Step 3: 实现**（按 Files）
- [ ] **Step 4: 跑测试确认通过**，并编译 interaction 全部模块的主代码与测试代码
- [ ] **Step 5:** `./gradlew dumpModuleGraph`，确认 `module-graph.json` 只多了 interaction 对 `patra-tool-events` 与 `-api` 的边
- [ ] **Step 6: 提交** `git commit -m "feat(interaction): 定义集成事件契约并配置外送到 RabbitMQ (PAP-141)"`

---

### Task 7: 真 RabbitMQ 集成测试

**Files:**
- Create: `patra-interaction-boot/src/integrationTest/java/dev/patra/interaction/support/InteractionEventsITSpringConfig.java`（`@TestConfiguration`：`Declarables` 声明非持久队列 `interaction.it.events`，绑定到 `InteractionExchange.NAME` 的五个 `ROUTING_KEY`）
- Modify: `.../support/InteractionFlowITSupport.java`（initializers 加 `RabbitMQContainerInitializer`；`@Import` 加 `InteractionEventsITSpringConfig`；`resetWorld` 先 `TRUNCATE public.event_publication`，再清空 `interaction.it.events`）
- Create: `patra-interaction-boot/src/integrationTest/java/dev/patra/interaction/config/InteractionEventsFlowIT.java`
- Modify: `patra-interaction-infra/src/integrationTest/java/dev/patra/interaction/infra/InteractionMigrationIT.java`（`event_publication` 在 public）

**Interfaces:**
- Consumes: Task 6 的常量与 api record；`RabbitMQContainerInitializer.getRabbitMQContainer()`；`EventPublicationResubmitter.resubmit()`；Modulith `EventSerializer`。

- [ ] **Step 1: 写 IT**（`InteractionEventsFlowIT extends InteractionFlowITSupport`；世界照 `HelpfulFlowIT.world()`，助手照它的 `comment(...)` / `experience(...)`）

辅助：`Message receive(String routingKey)`（从 `interaction.it.events` 循环取，跳过别的路由键，10 秒超时失败）；`<T> T body(Message, Class<T>)`（断言 `__TypeId__` 是 `T` 的全类名后用 `JSON.readValue`）。

用例：`should_deliver_comment_published_after_commit`（整体比较，`messageId` = `eventId`，登记表随后为 0；`plainText` 含 `<@B>`）、`should_deliver_comment_edited_with_version_two`、`should_deliver_comment_deleted`、`should_deliver_post_deleted`（`subjectId` 等于经验下评论事件的 `subject.subjectId`）、`should_deliver_helpful_marked_on_post_with_experience_outcome`、`should_not_externalize_helpful_unmarked`（取消后登记表为 0、2 秒内队列无新消息）、`should_not_externalize_when_publish_rolls_back`（`FAIL_MARKER`）、`should_keep_event_id_when_resubmitted`（`stop_app` → 发表 → 登记行 FAILED → `start_app` → 必要时把 `publication_date` 往前改 2 分钟 → `resubmit()` → 收到的 `eventId` 等于登记行 JSON 里的）、`should_round_trip_domain_events_through_registry_serializer`（五个全字段领域事件经 `EventSerializer` 往返相等）。
`InteractionMigrationIT`：`should_create_event_publication_in_public`。

- [ ] **Step 2: 跑 IT**，并临时去掉 `comment.published` 的外送看到对应用例变红，再恢复

Run: `./gradlew :patra-api:patra-interaction:patra-interaction-boot:integrationTest --tests "*InteractionEventsFlowIT*"`

- [ ] **Step 3: 跑 interaction 全部集成测试**

Run: `./gradlew :patra-api:patra-interaction:patra-interaction-boot:integrationTest :patra-api:patra-interaction:patra-interaction-infra:integrationTest`
Expected: PASS

- [ ] **Step 4: 提交** `git commit -m "test(interaction): 用真 RabbitMQ 验证集成事件的外送与契约 (PAP-141)"`

---

### Task 8: 文档与上线手册

**Files:**
- Modify: `docs/patra/runbooks/v0.16-notification-go-live-runbook.md`（「合并后：其他 Issue 的步骤」追加 interaction 条目，照 spec 第 7 节）
- Modify: `patra-api/patra-interaction/README.md`（补外送的事件与契约文档位置，只写架构概览）
- Modify: 共享笔记 `PAP-141-events-contract.md`（实现后若有出入，回写）

- [ ] **Step 1: 改文档**
- [ ] **Step 2: 提交** `git commit -m "docs(interaction): 上线手册与模块说明补集成事件 (PAP-141)"`

---

### Task 9: 全量验证、独立评审、交付

- [ ] **Step 1:** `./gradlew spotlessApply` 后 `./gradlew check integrationTest --continue`，全绿（缓存锁 / 个别 IT 超时先单独重跑）
- [ ] **Step 2:** 派独立评审子代理（`model: "opus"`），只给 spec 与 `git diff a1eedc4d9..HEAD`；重要问题先写复现测试再修
- [ ] **Step 3:** 修完再跑一次 Step 1
- [ ] **Step 4:** 核对 `git diff --cached` 只含本 Issue 路径后提交；给编排会话发验收汇报
