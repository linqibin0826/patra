# reaction「有帮助」实施计划（PAP-131）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans（本仓库约定内联执行，不用 subagent-driven）。Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal**：interaction 新建 `reaction` 子域，给评论与投稿经验点 / 取消「有帮助」，同事务维护目标计数与获赞，删除联动，提供获赞读端口。

**Architecture**：domain 定目标、画像、规则与端口；app 的 Handler 在事务外解析目标与查资格、事务内「配额锁 → 目标锁 → 锁内复核 → 明细 → 统计行」；同步 `@EventListener` 订阅评论与经验的删除事件；infra 用 `JdbcClient` 写三张表；adapter 出四个 PUT / DELETE 接口。

**Tech Stack**：Java 25、Spring Boot 4、`JdbcClient`、PostgreSQL 17、Flyway、JUnit 5 + AssertJ + Mockito、Testcontainers、ArchUnit。

**Spec**：`docs/patra/specs/2026-10-29-reaction-helpful-design.md`（下文「spec §N」）。

## Global Constraints

- 模块根：`patra-api/patra-interaction/`；包根 `dev.patra.interaction`；子域名 `reaction`，schema 名 `reaction`。
- 分包按 `.claude/rules/tech/package-layout.md`；domain、app、infra、adapter 四层都要有 `reaction` 包，各带 `package-info.java`。
- 所有方法写 `///` Markdown Javadoc；不用全类名；record ≤ 4 参数用 `of()`，≥ 5 用 `@Builder`；集合字段 `copyOf`。
- 写命令 Handler 注入 `TransactionOperations`，不加 `@Transactional`；infra 只允许 `@Transactional(propagation = MANDATORY)`。
- 订阅一律同步 `@EventListener`，禁止 `@TransactionalEventListener`。
- 迁移版本号用时间戳，必须大于 `20261029100000`；本计划用 `20261029200000` / `20261029200100` / `20261029200200`。
- 类型字符串码固定为 `'COMMENT'`、`'POST'`；每天额度 300、无间隔。
- 测试方法 snake_case；单测 `@Timeout` ≤ 2s 的约定照现有测试（现有测试多数没加，跟随邻近文件）。
- **不提交**：本仓库禁止自动 commit，各任务结束只跑验证，最后停在本地等用户验收。
- 命令一律在仓库根 `/Users/linqibin/Projects/Products/patra/.claude/worktrees/package-layout-object-storage-23bd6f` 执行，绝对路径，不 `cd` 出 worktree。Gradle 项目前缀 `:patra-api:patra-interaction:`。

## Review Focus

1. **删除与点并发**：评论删除事务提交前有人点进来 → 不得留下孤儿明细（目标锁 + 锁内复核，Task 5 单测断言「锁在重读之前」，Task 8 IT 验删除后明细为 0）。
2. **取消已删目标 / 不存在目标**：返回 200 `helpful=false, helpfulCount=0`，不 404（Task 5、Task 8）。
3. **重复 PUT**：第二次不耗额度、计数不变（Task 5 单测断言 `admit` 未调用；Task 8 IT 断言 `publish_quota.day_count` 仍为 1）。
4. **评论所在经验已删**：评论不能点，404（Task 4 单测、Task 8 IT）。
5. **注销后**：清理任务跑完，有帮助明细与统计行原样（Task 8 在 `DeletedUserCleanupFlowIT` 加一例）。

---

### Task 1：底座加发表种类 `HELPFUL`

**Files:**
- Modify: `patra-interaction-domain/src/main/java/dev/patra/interaction/domain/subject/model/enums/PublishKind.java`
- Modify: `patra-interaction-domain/src/main/java/dev/patra/interaction/domain/subject/exception/PublishRateLimitedException.java`（`dailyLimitReached` 的消息）
- Create: `patra-interaction-infra/src/main/resources/db/migration/subject/V20261029200200__allow_helpful_publish_kind.sql`
- Test: `patra-interaction-domain/src/test/java/dev/patra/interaction/domain/subject/policy/PublishQuotaTest.java`、`patra-interaction-infra/src/integrationTest/java/dev/patra/interaction/infra/InteractionMigrationIT.java`

**Interfaces:**
- Produces: `PublishKind.HELPFUL`（300 / `Duration.ZERO`）；`PublishKind.dailyLimitPhrase()` 返回如「发表了 100 条评论」，替换原 `displayName` 字段与方法（只有 `dailyLimitReached` 用它）。

- [ ] **Step 1：写失败测试** `PublishQuotaTest.should_admit_three_hundredth_helpful_without_interval_and_reject_next`：`quota(TODAY, 299, NOW.minusSeconds(1))` + `HELPFUL` 放行、`dayCount()` 为 300；`quota(TODAY, 300, …)` 抛 `PublishRateLimitedException`，`getMessage()` 等于 `"今天已经点了 300 次有帮助，明天再来吧"`，trait 含 `DAILY_LIMIT_REACHED`。原有两条消息断言（`"今天已经发表了 100 条评论，明天再来吧"`、`"今天已经发表了 10 条投稿经验，明天再来吧"`）保持不变。
- [ ] **Step 2：跑测试确认失败**：`./gradlew :patra-api:patra-interaction:patra-interaction-domain:test --tests "*PublishQuotaTest*"` → 编译失败（没有 `HELPFUL`）。
- [ ] **Step 3：实现**：`PublishKind(String dailyLimitFormat, int dailyLimit, Duration minInterval)`，三个常量的格式串为 `"发表了 %d 条评论"`、`"发表了 %d 条投稿经验"`、`"点了 %d 次有帮助"`；`dailyLimitPhrase()` = `dailyLimitFormat.formatted(dailyLimit)`；`dailyLimitReached` 消息改为 `"今天已经" + kind.dailyLimitPhrase() + "，明天再来吧"`。枚举类注释补一句「有帮助：点一次算一次，取消不退」。
- [ ] **Step 4：跑测试确认通过**（同 Step 2 命令，PASS）。
- [ ] **Step 5：迁移 IT 失败测试** `InteractionMigrationIT.should_accept_helpful_publish_kind`：`INSERT INTO subject.publish_quota (user_id, kind, quota_day, day_count, last_published_at) VALUES (1, 'HELPFUL', CURRENT_DATE, 1, now())` 不抛异常；`'LIKE'` 抛 `DataIntegrityViolationException`。同时把 `should_create_one_schema_per_subdomain` 的 `isSubsetOf` 加 `"reaction"`。
- [ ] **Step 6：写迁移**：`ALTER TABLE subject.publish_quota DROP CONSTRAINT ck_publish_quota_kind, ADD CONSTRAINT ck_publish_quota_kind CHECK (kind IN ('COMMENT', 'POST', 'HELPFUL'));` 并更新 `kind` 列注释（加 `HELPFUL (300 per day, no interval)`）。文件头注释照现有脚本写明 PAP-131。
- [ ] **Step 7：跑** `./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest --tests "*InteractionMigrationIT*"` → PASS。

### Task 2：reaction 领域层

**Files（均在 `patra-interaction-domain/src/main/java/dev/patra/interaction/domain/reaction/`）:**
- Create: `package-info.java`
- Create: `model/enums/HelpfulTargetType.java`、`model/vo/HelpfulTarget.java`、`model/vo/TargetProfile.java`、`model/vo/HelpfulMark.java`
- Create: `exception/ReactionErrorTrait.java`、`exception/HelpfulTargetNotFoundException.java`、`exception/SelfHelpfulException.java`
- Create: `port/repository/HelpfulRepository.java`、`port/read/HelpfulReadPort.java`
- Test: `patra-interaction-domain/src/test/java/dev/patra/interaction/domain/reaction/model/vo/TargetProfileTest.java`、`.../reaction/exception/ReactionExceptionsTest.java`

**Interfaces:**
- Produces:
  - `enum HelpfulTargetType { COMMENT, POST }`
  - `record HelpfulTarget(HelpfulTargetType type, long id)`，`of(type, id)`；紧凑构造器拒绝 `type == null`、`id <= 0`（`IllegalArgumentException`）。
  - `record TargetProfile(HelpfulTarget target, long authorId, boolean available, boolean anonymous)`，`of(...)`；`Long creditedUserId()`（匿名为 `null`，否则 `authorId`）；`void requireAvailable()`（不可点抛 `HelpfulTargetNotFoundException`）；`void requireMarkableBy(long userId)`（先 `requireAvailable()`，再判 `authorId == userId` 抛 `SelfHelpfulException`）。
  - `record HelpfulMark(Long creditedUserId)`，`of(...)`：删掉的一条明细计入了谁。
  - `enum ReactionErrorTrait implements ErrorTrait { HELPFUL_TARGET_NOT_FOUND, SELF_HELPFUL }`
  - `HelpfulTargetNotFoundException()`：`NOT_FOUND` + `HELPFUL_TARGET_NOT_FOUND`，消息「这条内容不存在或已删除，不能点有帮助」。
  - `SelfHelpfulException()`：`FORBIDDEN` + `SELF_HELPFUL`，消息「不能给自己的内容点有帮助」。
  - `HelpfulRepository`（写方法须在调用方事务里调用）：
    - `void lockTarget(HelpfulTarget target)`——事务级 advisory lock `reaction.target:{TYPE}:{id}`
    - `boolean exists(long userId, HelpfulTarget target)`
    - `boolean insert(long userId, HelpfulTarget target, Long creditedUserId, Instant markedAt)`——真插入为 `true`
    - `Optional<HelpfulMark> delete(long userId, HelpfulTarget target)`——真删掉才有值
    - `Map<Long, Integer> deleteAllOn(HelpfulTarget target)`——删目标的全部明细与统计行，返回受益人 → 条数（受益人为空的不在结果里）
    - `void adjustTargetCount(HelpfulTarget target, int delta)`——upsert 加减
    - `void adjustReceived(long userId, long delta)`——upsert 加减
    - `int countOf(HelpfulTarget target)`——没有统计行为 0
  - `HelpfulReadPort`：`long helpfulReceived(long userId)`——没有行为 0（spec §9 的 Javadoc 原文）。

- [ ] **Step 1：失败测试** `TargetProfileTest`：
  - `should_credit_author_when_not_anonymous` → `creditedUserId()` 等于 authorId；`should_credit_nobody_when_anonymous` → `null`。
  - `should_reject_unavailable_target` → `requireMarkableBy(其他人)` 抛 `HelpfulTargetNotFoundException`，traits 含 `NOT_FOUND`、`HELPFUL_TARGET_NOT_FOUND`。
  - `should_reject_author_even_when_anonymous` → `anonymous = true`、`requireMarkableBy(authorId)` 抛 `SelfHelpfulException`（traits 含 `FORBIDDEN`、`SELF_HELPFUL`）。
  - `should_report_not_found_before_self` → 不可点且是自己 → `HelpfulTargetNotFoundException`。
  - `should_reject_invalid_target` → `HelpfulTarget.of(COMMENT, 0)` 抛 `IllegalArgumentException`。
  - `ReactionExceptionsTest` 断言两条异常的消息原文。
- [ ] **Step 2：跑** `./gradlew :patra-api:patra-interaction:patra-interaction-domain:test --tests "*reaction*"` → 编译失败。
- [ ] **Step 3：实现上面全部类型**；`package-info.java` 写子域职责与「只公开 `port.read`、`model.read`、`event`；本子域不发布事件，PAP-132 / 133 只经 SQL 读三张表」。
- [ ] **Step 4：跑同一命令** → PASS；再跑 `./gradlew :patra-api:patra-interaction:patra-interaction-domain:check` → PASS（R3 纯净性）。

### Task 3：reaction 迁移与持久化适配器

**Files:**
- Create: `patra-interaction-infra/src/main/resources/db/migration/reaction/V20261029200000__create_reaction_schema.sql`（`CREATE SCHEMA reaction;` + 注释）
- Create: `.../db/migration/reaction/V20261029200100__create_reaction_tables.sql`（spec §2 的三张表 + 索引，每列 `COMMENT ON`，英文，照现有脚本风格）
- Create: `patra-interaction-infra/src/main/java/dev/patra/interaction/infra/reaction/package-info.java`
- Create: `.../infra/reaction/persistence/HelpfulRepositoryAdapter.java`、`.../persistence/HelpfulReadAdapter.java`
- Test（`patra-interaction-infra/src/integrationTest/java/dev/patra/interaction/infra/reaction/persistence/`）：`ReactionPersistenceITBootstrap.java`、`ReactionPersistenceITSupport.java`（每例 `TRUNCATE reaction.helpful, reaction.target_stats, reaction.user_stats`）、`HelpfulRepositoryAdapterIT.java`、`HelpfulReadAdapterIT.java`、`ReactionMigrationIT.java`

**Interfaces:**
- Consumes：Task 2 的 `HelpfulRepository`、`HelpfulReadPort`、`HelpfulTarget`、`HelpfulMark`。
- Produces：两个 `@Component`，供 app 注入。

- [ ] **Step 1：失败测试**（写操作包在 `TransactionTemplate` 里）：
  - `HelpfulRepositoryAdapterIT.should_insert_once_and_ignore_duplicate`：第一次 `insert` 为 `true`，第二次为 `false`，表里 1 行，`credited_user_id` 等于传入值。
  - `should_return_mark_only_when_deleted`：删已有的 → `Optional.of(HelpfulMark.of(9L))`；再删 → `Optional.empty()`；受益人为空的那条删掉 → `HelpfulMark.of(null)`。
  - `should_adjust_counters_by_upsert`：`adjustTargetCount(t, 1)` 两次、`-1` 一次 → `countOf(t)` 为 1；未出现过的目标 `countOf` 为 0；`adjustReceived(9, 1)` 两次 → `user_stats` 为 2。
  - `should_delete_all_on_target_and_group_by_credited_user`：同目标 3 条（受益人 9、9、`null`）+ 别的目标 1 条，`deleteAllOn` 返回 `{9=2}`，目标的明细与统计行都没了，别的目标不动。
  - `should_require_transaction_for_lock`：事务外调 `lockTarget` 抛 `IllegalTransactionStateException`。
  - `should_serialize_on_target_lock`：线程 A 在事务里拿锁后等待信号，线程 B 拿同一目标锁会阻塞（B 的 `Future` 在 A 提交前 200ms 内未完成，A 提交后完成）；不同目标不阻塞。
  - `HelpfulReadAdapterIT.should_read_received_or_zero`：有行返回值，无行返回 0。
  - `ReactionMigrationIT`：`target_type = 'LIKE'` 拒绝（约束名 `ck_helpful_target_type`）；`credited_user_id = user_id` 拒绝（`ck_helpful_not_self`）；`helpful_count = -1` 拒绝（`ck_target_stats_count`）；`helpful_received = -1` 拒绝（`ck_user_stats_received`）；`idx_helpful_target` 定义含 `(target_type, target_id)`。断言异常消息含约束名（PG CHECK 遇 NULL 放行，必须断言约束名而不是只断言抛异常）。
- [ ] **Step 2：跑** `./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest --tests "*reaction*"` → 失败（表不存在 / 类不存在）。
- [ ] **Step 3：写两个迁移与两个适配器**：
  - 锁：`SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))`，键 `"reaction.target:" + type.name() + ":" + id`，方法上 `@Transactional(propagation = Propagation.MANDATORY)`（照 `PublishQuotaRepositoryAdapter.lock`）。
  - `insert`：`INSERT … ON CONFLICT (user_id, target_type, target_id) DO NOTHING`，返回 `update() == 1`。
  - `delete`：`DELETE … RETURNING credited_user_id`，`query(...).optional()` 映射为 `HelpfulMark`（`rs.getObject("credited_user_id", Long.class)`）。
  - `deleteAllOn`：`DELETE FROM reaction.helpful WHERE target_type = :type AND target_id = :id RETURNING credited_user_id`，Java 里对非空值分组计数；再 `DELETE FROM reaction.target_stats WHERE …`。
  - 两个 `adjust*`：`INSERT … VALUES (:delta) ON CONFLICT (…) DO UPDATE SET col = 表.col + EXCLUDED.col`。
  - 时间写入照 `SubjectRows.utc(...)` 的做法转 `OffsetDateTime`（infra.reaction 内自写一个私有方法，不 import 别的子域的 Row 类）。
- [ ] **Step 4：跑同一命令** → PASS；再跑 `InteractionMigrationIT` → PASS。

### Task 4：目标解析 `ReactionTargets`

**Files:**
- Create: `patra-interaction-app/src/main/java/dev/patra/interaction/app/reaction/package-info.java`
- Create: `.../app/reaction/service/ReactionTargets.java`
- Test: `patra-interaction-app/src/test/java/dev/patra/interaction/app/reaction/service/ReactionTargetsTest.java`、`.../app/reaction/ReactionFixtures.java`（共用的 `CommentRecord`、`SubjectView` 构造）

**Interfaces:**
- Consumes：`DiscussionReadPort.findById(long)`、`SubjectReadPort.findIdsByKeys(SubjectType, Collection<String>)`、`SubjectReadPort.findByIds(Collection<SubjectId>)`、Task 2 的类型。
- Produces：`@Component ReactionTargets`，`TargetProfile require(HelpfulTarget target)`——查不到抛 `HelpfulTargetNotFoundException`；查到但不可点照常返回（`available = false`），由调用方判。

- [ ] **Step 1：失败测试** `ReactionTargetsTest`（mock 两个读端口）：
  - `should_profile_comment_under_publication`：评论 `ACTIVE`、对象是文献（`owner = null`、`ACTIVE`）→ `authorId` 为评论作者、`available` 真、`anonymous` 假。
  - `should_mark_owner_comment_anonymous_under_anonymous_post`：对象 `owner = SubjectOwner.of(作者, true)` 且评论作者 = 所有者 → `anonymous` 真；评论作者 ≠ 所有者 → 假；所有者不匿名 → 假。
  - `should_mark_placeholder_unavailable`：评论 `DELETED` → `available` 假。
  - `should_mark_comment_unavailable_when_subject_unavailable`：对象 `UNAVAILABLE` → `available` 假。
  - `should_profile_post_from_registry`：`findIdsByKeys(POST, List.of("500"))` → 对象，`owner = (7, true)`、`ACTIVE` → `authorId` 7、`anonymous` 真；`UNAVAILABLE` → `available` 假。
  - `should_throw_when_target_missing`：评论不存在、经验未登记 → `HelpfulTargetNotFoundException`。
- [ ] **Step 2：跑** `./gradlew :patra-api:patra-interaction:patra-interaction-app:test --tests "*ReactionTargetsTest*"` → 编译失败。
- [ ] **Step 3：实现**：按 spec §3.2；评论所在对象在登记表里查不到时视为不可点（不抛 `IllegalStateException`：有帮助不该因为数据异常 500）。
- [ ] **Step 4：跑同一命令** → PASS。

### Task 5：点与取消

**Files:**
- Create（`patra-interaction-app/src/main/java/dev/patra/interaction/app/reaction/usecase/helpful/`）：`MarkHelpfulCommand.java`、`MarkHelpfulHandler.java`、`UnmarkHelpfulCommand.java`、`UnmarkHelpfulHandler.java`、`HelpfulResult.java`
- Test：`patra-interaction-app/src/test/java/dev/patra/interaction/app/reaction/usecase/helpful/MarkHelpfulHandlerTest.java`、`UnmarkHelpfulHandlerTest.java`

**Interfaces:**
- Consumes：Task 4 `ReactionTargets.require`；Task 2 `HelpfulRepository`；底座 `PublishGuardGateway.requireEligible(long)`、`admit(long, PublishKind)`；`CurrentUserPort`；`Clock`；`TransactionOperations`。
- Produces：
  - `record MarkHelpfulCommand(HelpfulTargetType type, long targetId) implements Command<HelpfulResult>`，`of(...)`；`UnmarkHelpfulCommand` 同形。
  - `record HelpfulResult(boolean helpful, int helpfulCount)`，`of(...)`。

- [ ] **Step 1：失败测试** `MarkHelpfulHandlerTest`（用 `patra-tool-test` 的 `dev.patra.tool.test.transaction.RecordingTransactions` 记事务边界；mock 的调用用 `doAnswer` 往同一事件列表记名字）：
  - `should_mark_inside_transaction_in_lock_order`：事件序列严格为 `require`、`eligible`、`exists`、`BEGIN`、`admit`、`lock`、`require`、`insert`、`target+1`、`received+1`、`count`、`END`；结果 `(true, 3)`（`countOf` 桩返回 3）；`insert` 收到的 `creditedUserId` 为作者、`markedAt` 为固定时钟的时刻。
  - `should_not_credit_anonymous_author`：画像 `anonymous = true` → `insert` 的受益人为 `null`，不调 `adjustReceived`。
  - `should_skip_counters_when_insert_lost_race`：`insert` 返回 `false` → 不调两个 `adjust*`，结果仍 `(true, countOf)`。
  - `should_return_without_quota_when_already_marked`：`exists` 为真 → 不开事务、不调 `admit`，结果 `(true, countOf)`。
  - `should_reject_before_remote_call`：画像是自己的 → 抛 `SelfHelpfulException`，`requireEligible` 未调用；不可点 → `HelpfulTargetNotFoundException`，同样未调用。
  - `should_reject_when_target_gone_inside_lock`：事务内第二次 `require` 返回 `available = false` → 抛 `HelpfulTargetNotFoundException`，`insert` 未调用。
  - `UnmarkHelpfulHandlerTest.should_unmark_and_decrement_credited_user`：`delete` 返回 `HelpfulMark.of(7L)` → 序列 `BEGIN`、`lock`、`delete`、`target-1`、`received-1`（用户 7）、`count`、`END`；结果 `(false, n)`；不调 `requireEligible`、`admit`、`ReactionTargets`。
  - `should_not_touch_received_for_uncredited_mark`：`HelpfulMark.of(null)` → 只调 `adjustTargetCount(-1)`。
  - `should_be_noop_when_not_marked`：`delete` 为空 → 两个 `adjust*` 都未调用，结果 `(false, countOf)`。
- [ ] **Step 2：跑** `./gradlew :patra-api:patra-interaction:patra-interaction-app:test --tests "*HelpfulHandlerTest*"` → 编译失败。
- [ ] **Step 3：实现两个 Handler**：顺序照 spec §4.1、§4.2；当前用户取 `currentUser.require().userId()`；`markedAt = clock.instant().truncatedTo(ChronoUnit.MICROS)`，在事务前取好；类注释写明锁顺序与「额度在插入之前记」的理由。
- [ ] **Step 4：跑同一命令** → PASS。

### Task 6：删除联动监听

**Files:**
- Create：`patra-interaction-app/src/main/java/dev/patra/interaction/app/reaction/listener/HelpfulTargetDeletedListener.java`、`.../listener/HelpfulWriteFailedException.java`
- Test：`patra-interaction-app/src/test/java/dev/patra/interaction/app/reaction/listener/HelpfulTargetDeletedListenerTest.java`

**Interfaces:**
- Consumes：`CommentDeletedEvent`（`commentId`、`placeholder`）、`PostDeletedEvent`（`postId`）；Task 2 `HelpfulRepository.lockTarget`、`deleteAllOn`、`adjustReceived`。
- Produces：两个 `@EventListener` 方法 `on(CommentDeletedEvent)`、`on(PostDeletedEvent)`。

- [ ] **Step 1：失败测试**：
  - `should_remove_comment_helpful_on_delete`：`placeholder = false` → 依次 `lockTarget(COMMENT, id)`、`deleteAllOn(COMMENT, id)`；`deleteAllOn` 返回 `{9=2, 3=1}` → `adjustReceived(3, -1)` 在 `adjustReceived(9, -2)` 之前（`InOrder`，按用户 ID 升序）。
  - `should_remove_comment_helpful_when_it_becomes_placeholder`：`placeholder = true` 同样处理。
  - `should_remove_post_helpful_on_delete`：`(POST, postId)`；返回空 Map → 不调 `adjustReceived`。
  - `should_wrap_unexpected_failure`：`deleteAllOn` 抛 `RuntimeException` → 抛 `HelpfulWriteFailedException`（原因链保留）；抛 `DomainException` 原样传播（照 `PostNotificationListener`）。
- [ ] **Step 2：跑** `./gradlew :patra-api:patra-interaction:patra-interaction-app:test --tests "*HelpfulTargetDeletedListenerTest*"` → 编译失败。
- [ ] **Step 3：实现**：同步 `@EventListener`，不加 `@Transactional`；`HelpfulWriteFailedException extends ApplicationException`，`HttpStdErrors.of("INTERACTION").INTERNAL_ERROR()`、消息「有帮助清理失败」。类注释写明 spec §5 的「依赖的事件语义」与「经验下的评论不连带」。
- [ ] **Step 4：跑同一命令** → PASS。

### Task 7：接口与架构规则

**Files:**
- Create：`patra-interaction-adapter/src/main/java/dev/patra/interaction/adapter/reaction/package-info.java`
- Create：`.../adapter/reaction/rest/HelpfulController.java`、`.../rest/response/HelpfulResponse.java`
- Modify：`patra-interaction-boot/src/test/java/dev/patra/interaction/architecture/InteractionArchitectureTest.java`
- Create：`patra-interaction-infra/.../infra/reaction/package-info.java` 若 Task 3 未建（四层齐全）

**Interfaces:**
- Consumes：Task 5 的命令与 `HelpfulResult`；`CommandBus`。
- Produces：`PUT|DELETE /comments/{commentId}/helpful`、`PUT|DELETE /posts/{postId}/helpful`，响应 `HelpfulResponse(boolean helpful, int helpfulCount)`，`from(HelpfulResult)`。

- [ ] **Step 1：失败测试**：`InteractionArchitectureTest` 的 `SUBDOMAINS` 加 `"reaction"`，`subdomainRules` 加 `.allowingDependency("reaction", "discussion")`、`.allowingSubscription("reaction", "post")`；更新清单上方注释（v0.15 PAP-131）。跑 `./gradlew :patra-api:patra-interaction:patra-interaction-boot:test --tests "*InteractionArchitectureTest*"`——若前面任务已建全四层，此时应因缺 adapter 层 reaction 包而失败（规则 22b）。
- [ ] **Step 2：实现 Controller 与响应**：`@Tag(name = "Helpful", description = "有帮助")`；四个方法都 200；Javadoc 写清 404 / 403 / 409 / 429 与原因码（spec §6）。
- [ ] **Step 3：跑同一命令** → PASS（含子域无环、跨子域只经公开包）。

### Task 8：端到端流程测试

**Files:**
- Create：`patra-interaction-boot/src/integrationTest/java/dev/patra/interaction/reaction/HelpfulFlowIT.java`
- Modify：`patra-interaction-boot/src/integrationTest/java/dev/patra/interaction/support/DiscussionEventRecorder.java`（加删除失败开关）
- Modify：`patra-interaction-boot/src/integrationTest/java/dev/patra/interaction/subject/DeletedUserCleanupFlowIT.java`

**Interfaces:**
- Consumes：Task 7 的接口；现有 `/discussions/{type}/{key}/comments`、`/journals/{id}/posts`、`DELETE /comments/{id}`、`DELETE /posts/{id}`；`InteractionFlowITSupport` 的 `call`/`status`/`json`/`count`。
- Produces：`DiscussionEventRecorder.FAIL_ON_DELETE`（`public static volatile boolean`，为真时 `on(CommentDeletedEvent)` 抛 `IllegalStateException`；`InteractionFlowITSupport.resetWorld` 里复位为假）；记录器另加 `on(PostDeletedEvent)`，同一开关下抛异常、否则什么都不做（不进 `EVENTS`，免得影响现有事件计数断言）。

- [ ] **Step 1：写 `HelpfulFlowIT`**（用户都 `ACTIVE`、邮箱已验证；文献 PAPER；期刊 100 下用 `POST /journals/100/posts` 发经验，请求体照 `ExperienceFlowIT.revised(...)` 最小字段；评论前清作者的 `publish_quota` 绕开 10 秒间隔）：
  - `should_count_equal_to_rows_under_concurrent_marks`：20 个用户用 `ExecutorService` 并发 `PUT /comments/{c}/helpful`（`CountDownLatch` 齐发），全部 200；`target_stats.helpful_count` = `count(*) reaction.helpful` = 20；作者 `user_stats.helpful_received` = 20。
  - `should_not_double_count_and_should_unmark`：B 连点两次 → 计数 1、B 的 `HELPFUL` 额度 `day_count` 为 1；取消 → 响应 `helpful=false, helpfulCount=0`、获赞 0；再取消 → 200、计数 0；C 取消从未点过的 → 200。
  - `should_reject_self_including_anonymous_post_author`：A 点自己的评论 → 403 `SELF_HELPFUL`；A 发匿名经验后点它 → 403 `SELF_HELPFUL`。
  - `should_reject_ineligible_users`：邮箱未验证 → 409 `EMAIL_UNVERIFIED`；`PENDING_DELETION` → 409 `ACCOUNT_RESTRICTED`；未登录 → 401。
  - `should_reject_three_hundred_first_mark`：预置 B 当天（北京时间）`HELPFUL` 行 `day_count = 300`，用不自动重发的 `java.net.http.HttpClient` 发 `PUT`（照 `DiscussionEventRollbackFlowIT.postWithoutRetry`）→ 429、带 `Retry-After`、体含 `DAILY_LIMIT_REACHED`；明细 0 行。
  - `should_not_credit_anonymous_post_or_author_comment_under_it`：A 发匿名经验 P；A 在 P 下评论 c1，C 在 P 下评论 c2；B 点 P、c1、c2 → 三个目标计数各 1；A 的获赞 0，C 的获赞 1。
  - `should_reject_placeholder_and_deleted_targets`：评论变占位后点 → 404 `HELPFUL_TARGET_NOT_FOUND`；整条删掉的评论、不存在的评论、已删经验、已删经验下的评论 → 404。
  - `should_remove_helpful_with_deleted_targets`：c 被 B、C 点过 → A 删 c（无回复，整条去掉）→ 明细与统计行为 0、A 获赞扣回 0；另一条 c' 有回复、被点后删成占位 → 同样清空，回复收到的有帮助保留；经验 P（非匿名）被点 2 次后删除 → 清空、作者获赞扣回；P 下 C 的评论收到的 1 次保留。
  - `should_roll_back_delete_when_subscriber_fails`：c 被点 1 次；`FAIL_ON_DELETE = true` 后 A 删 c → 非 200；评论仍 `ACTIVE`，明细 1 行、计数 1、获赞 1。经验同理。
- [ ] **Step 2：`DeletedUserCleanupFlowIT` 加** `should_keep_helpful_data_of_deleted_users`：A 给 B 的评论点、B 给 A 的评论点，两人都标 `DELETED` 后跑清理 → `reaction.helpful` 2 行、两条统计行与两个 `user_stats` 行原样。
- [ ] **Step 3：跑** `./gradlew :patra-api:patra-interaction:patra-interaction-boot:integrationTest --tests "*HelpfulFlowIT*" --tests "*DeletedUserCleanupFlowIT*"` → PASS（这里新代码已在前面任务实现，失败即说明前面任务有缺口，按 systematic-debugging 处理）。

### Task 9：文档同步与全量验证

**Files:**
- Modify：`patra-api/patra-interaction/README.md`（第 1 节子域表加 `reaction` 一行；包路径一行加 `reaction`；第 3 节依赖边段落加两条边；新增「reaction 的存储与并发」小节：三张表、锁顺序、删除联动、注销不清理，指向工程 spec）
- Modify：`.claude/rules/project-info.md` 第 18 行 interaction 的描述加「有帮助」
- Modify：`patra-interaction-infra/build.gradle.kts`、`patra-interaction-app/build.gradle.kts`、`patra-interaction-adapter/build.gradle.kts` 顶部注释里的子域列表（若列了）

- [ ] **Step 1：改文档**。
- [ ] **Step 2：格式化**：`./gradlew :patra-api:patra-interaction:spotlessApply`（gjf 会把过长的 `///` 折成 `//`，跑完检查新文件的 Javadoc 是否被折断）。
- [ ] **Step 3：全量门控**：`./gradlew :patra-api:patra-interaction:check`（含各模块 `test`；`integrationTest` 若不在 `check` 里，再跑 `./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest :patra-api:patra-interaction:patra-interaction-boot:integrationTest`）→ 全部 BUILD SUCCESSFUL。后台运行，等完成通知。
- [ ] **Step 4：停在本地**，不提交；汇报改动、测试结果、给 PAP-132 / 133 的接口、遗留问题。
