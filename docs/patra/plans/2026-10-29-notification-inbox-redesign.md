# 提醒页重设计与内容删除后的提醒 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> Patra 约定：本计划用 `superpowers:executing-plans` 在当前会话内联执行（用户 2026-10-21 定，不派子代理）。

**Goal:** 评论或投稿经验删除时同事务删掉对应提醒（未读数随之一致），并把提醒列表改成「今天 / 昨天 / 更早」分组、正文当主角的版式。

**Architecture:** 后端在 interaction 服务内：讨论子域已有的 `CommentDeletedEvent` 改为触发「按评论删提醒」；投稿经验子域新增领域事件 `PostDeletedEvent`，由提醒子域同步订阅后「按对象删提醒」；一条 Flyway 迁移清掉库里已有的已删除提醒、去掉 `comment_deleted` 列、补 `subject_id` 索引。门户去掉已删除分支，新增北京时间分组函数，重写 `NotificationRow` 与列表分组渲染。

**Tech Stack:** Java 25 / Spring Boot 4 / JdbcClient / Flyway / JUnit 5 + AssertJ + Mockito / Testcontainers PG17；Next.js 16 / React 19 / TanStack Query v5 / Tailwind v4 / Vitest + Testing Library / Playwright。

**Spec:** `docs/patra/specs/2026-10-28-notification-inbox-redesign-design.md`（画布 https://claude.ai/artifact/Qwn6xpQaJAkTPFnFT6Mdix ，定稿板 `DirA` / `DirA-M` / `Rule3`）

## Global Constraints

- 工作目录：worktree `/Users/linqibin/Projects/Products/patra/.claude/worktrees/pap-127-notification-inbox`，分支 `feat/pap-127-notification-inbox`；所有命令在 worktree 内跑，不 `cd` 回主检出。worktree 内不用 JetBrains MCP（IDEA 开的是主检出）。
- **提交**：各任务不单独提交。全部完成、经用户本地验收后统一提交（用户约定：改动先本地验收再 commit / PR）。spec、plan、快照随这次的代码一起提交，最后开一个前后端合一的 draft PR，描述写 `Closes PAP-127`。
- 写后端代码前加载 `patra-backend:patra-hexagonal`（已加载）、`patra-backend:patra-jpa`（迁移与仓储）、`patra-backend:patra-events`（领域事件）。
- 后端：Google Java 风格；所有方法 `///` Markdown Javadoc；参数 ≤ 4 的 record 用静态 `of()`，不用 `@Builder`；测试方法 snake_case；时间用注入的 `Clock`。
- 门户：Biome 权威（提交前 `pnpm lint`）；禁 `any`、禁 inline `style`；条件 class 用 `cn`；测试查询优先 `getByRole`；禁快照测试。
- 时间一律北京时间（`src/lib/format-time.ts` 的 `partsOf` / `dayNumber`）。
- 文案（spec 4.3，逐字）：分组 `今天` / `昨天` / `更早`；动作 `回复了你`（COMMENT_REPLY）、`评论了你的投稿经验`（POST_COMMENT）、`@ 了你`（MENTION）；无标题文献 `一篇文献的讨论`；自己的经验 `你的投稿经验 · <期刊缩写> · 2026 年 3 月投稿`；别人的经验 `<期刊缩写> 的一条投稿经验`。
- 事务写法：实现 Task 3 前先 `git fetch origin main` 并看「[事务] 盘点事务写法与规范」会话是否已改 `DeleteExperienceHandler` / 监听器写法；有变化就先同步主干再按新写法发布事件。

## Review Focus

1. **页面开着跨过北京时间 0 点**：`useNow` 刷新后，昨天的条目要从「今天」挪到「昨天」——分组只依赖传入的 `now`，Task 4 用固定 `now` 覆盖 23:59 / 00:00 两侧。
2. **时钟偏差造成的未来时间**：`createdAt` 比 `now` 晚几秒的条目归「今天」、显示「刚刚」，不能落到「更早」——Task 4 加用例。
3. **加载更多后同组标题重复**：第二页的「更早」条目并入已有「更早」组，标题只出现一次——Task 7 加用例。
4. **列表没刷新时点开一条刚被删掉的提醒**：单条已读返回 404 不回滚、不报错，照常跳转——Task 7 保留并核对现有用例仍覆盖。
5. **删除一条下面没有任何提醒的经验**：事件照发、按对象删返回 0、经验照常删除——Task 3 加用例。

---

### Task 1: 后端——评论删除即删提醒

**Files:**
- Modify: `patra-api/patra-interaction/patra-interaction-domain/src/main/java/dev/patra/interaction/domain/notification/port/repository/NotificationRepository.java`
- Modify: `patra-api/patra-interaction/patra-interaction-infra/src/main/java/dev/patra/interaction/infra/notification/persistence/NotificationRepositoryAdapter.java`
- Modify: `patra-api/patra-interaction/patra-interaction-app/src/main/java/dev/patra/interaction/app/notification/eventhandler/CommentNotificationListener.java`
- Modify: `patra-api/patra-interaction/patra-interaction-domain/src/main/java/dev/patra/interaction/domain/discussion/event/CommentDeletedEvent.java`（Javadoc 第 7 行「提醒列表据此显示『评论已删除』」改为「提醒子域据此删除这条评论的全部提醒」）
- Test: `.../app/src/test/.../notification/eventhandler/CommentNotificationListenerTest.java`、`.../infra/src/integrationTest/.../notification/persistence/NotificationRepositoryAdapterIT.java`、`.../boot/src/integrationTest/.../notification/NotificationRulesFlowIT.java`

**Interfaces:**
- Produces: `NotificationRepository#deleteByComment(long commentId): int`（删除这条评论的全部提醒，跨收件人；返回删除行数）。`markCommentDeleted` 删除。

- [ ] **Step 1: 改单测** `CommentNotificationListenerTest.should_mark_deleted_on_comment_deleted` → `should_delete_notifications_on_comment_deleted`：发 `CommentDeletedEvent`（commentId 500），`verify(notifications).deleteByComment(500L)`、`verifyNoMoreInteractions`。
- [ ] **Step 2: 改仓储 IT**：删掉 `should_clear_excerpt_when_comment_deleted`、`should_reject_excerpt_on_deleted_comment_by_constraint`（约束在 Task 2 去掉，先删用例）；`refreshExcerpt` 那条里调用 `markCommentDeleted` 的前置改成不再需要（第 62 行附近）。`NotificationReadAdapterIT.should_map_excerpt_and_deleted_flag`（第 79–98 行）也调用了 `markCommentDeleted`，同步改名 `should_map_excerpt`、去掉删除标记那部分，否则 integrationTest 编译不过。新增 `should_delete_all_recipients_notifications_of_comment`：给收件人 1、2 各写一条 commentId 500、给收件人 1 再写一条 commentId 501，`deleteByComment(500L)` 返回 2，表里只剩 501 那条。
- [ ] **Step 3: 改流程 IT** `NotificationRulesFlowIT.should_show_deleted_comment` → `should_remove_notification_when_comment_deleted`：B 回复 A 后 A 的未读数 `replies = 1`、记下 ETag；B 删除回复；A 的列表 `items` 为空；`replies = 0`；带旧 ETag 取未读数得 200（不是 304）。
- [ ] **Step 4: 跑测试确认失败**
  Run: `./gradlew :patra-api:patra-interaction:patra-interaction-app:test --tests "*CommentNotificationListenerTest*"`
  Expected: FAIL（`deleteByComment` 不存在，编译失败）
- [ ] **Step 5: 实现**：接口加 `deleteByComment`、去掉 `markCommentDeleted`；适配器 `DELETE FROM notification.notification WHERE comment_id = :commentId`（走 `idx_notification_comment`）；监听器 `on(CommentDeletedEvent)` 改调 `deleteByComment`，Javadoc 改为「评论已删除：删掉它的全部提醒」。`refreshExcerpt` 的 `AND NOT comment_deleted` 在 Task 2 随列去掉。
- [ ] **Step 6: 跑测试确认通过**
  Run: `./gradlew :patra-api:patra-interaction:patra-interaction-app:test --tests "*CommentNotificationListenerTest*" :patra-api:patra-interaction:patra-interaction-infra:integrationTest --tests "*NotificationRepositoryAdapterIT*" :patra-api:patra-interaction:patra-interaction-boot:integrationTest --tests "*NotificationRulesFlowIT*"`
  Expected: PASS

### Task 2: 后端——迁移清旧数据、去掉删除标记、补对象索引

**Files:**
- Create: `patra-api/patra-interaction/patra-interaction-infra/src/main/resources/db/migration/notification/V20261029100000__remove_deleted_notifications.sql`
- Modify: `NotificationRepositoryAdapter.java`（`refreshExcerpt` 去掉 `AND NOT comment_deleted`，Javadoc「已标删除的不动」删掉；接口同名方法 Javadoc 同步）
- Modify: `.../infra/.../notification/persistence/NotificationReadAdapter.java`（SELECT 列与映射去掉 `comment_deleted`）
- Modify: `.../domain/.../notification/model/read/NotificationEntry.java`、`.../app/.../notification/usecase/query/NotificationView.java`、`NotificationQueryService.java`（第 99 行映射）、`.../adapter/.../notification/rest/response/NotificationResponse.java`：去掉 `commentDeleted` 字段与 `@param`
- Create: `.../infra/src/integrationTest/java/dev/patra/interaction/infra/notification/persistence/NotificationDeletedCleanupMigrationIT.java`
- Test: `NotificationQueryServiceTest.java`（去掉 `commentDeleted` 相关构造）

**Interfaces:**
- Consumes: Task 1 的 `deleteByComment`（迁移后仍可用）。
- Produces: 接口响应 `NotificationResponse` 不再有 `commentDeleted`（门户 Task 5 依赖）。

- [ ] **Step 1: 写迁移 IT** `NotificationDeletedCleanupMigrationIT`：在同一 PG 容器里新建独立数据库，用 Flyway API 先迁到 `target = 20261027190100`，插入：①一条 `comment_deleted = true` 的提醒；②一条挂在 `subject.registry` 中 `target_type = 3`、`state = 'UNAVAILABLE'` 对象下的正常提醒；③一条挂在 ACTIVE 文献对象下的正常提醒；再迁到最新。断言：
  - `should_delete_flagged_and_deleted_post_notifications`：只剩 ③；
  - `should_drop_comment_deleted_column_and_constraint`：`information_schema.columns` 里没有 `notification.notification.comment_deleted`，`pg_constraint` 里没有 `ck_notification_deleted_excerpt`；
  - `should_index_subject_id`：`pg_indexes` 里有 `notification.notification` 上的 `idx_notification_subject`。
- [ ] **Step 2: 跑 IT 确认失败**
  Run: `./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest --tests "*NotificationDeletedCleanupMigrationIT*"`
  Expected: FAIL（迁移不存在，行与列都还在）
- [ ] **Step 3: 写迁移**（按顺序）：`DELETE ... WHERE comment_deleted`；`DELETE ... n USING subject.registry r WHERE n.subject_id = r.subject_id AND r.target_type = 3 AND r.state = 'UNAVAILABLE'`；`ALTER TABLE notification.notification DROP CONSTRAINT ck_notification_deleted_excerpt, DROP COLUMN comment_deleted`；`CREATE INDEX idx_notification_subject ON notification.notification (subject_id)`。文件头注释写明 PAP-127 与「删除即删提醒」，跨 schema 关联只在这条一次性迁移里出现。
- [ ] **Step 4: 去掉读模型与接口里的 `commentDeleted`**（文件见上），改 `NotificationQueryServiceTest`。
- [ ] **Step 5: 跑测试确认通过**
  Run: `./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest --tests "*Notification*" :patra-api:patra-interaction:patra-interaction-app:test --tests "*Notification*"`
  Expected: PASS；`InteractionMigrationIT` 也通过（版本号按时间戳递增）。

### Task 3: 后端——经验删除事件与按对象删提醒

**Files:**
- Create: `patra-api/patra-interaction/patra-interaction-domain/src/main/java/dev/patra/interaction/domain/post/event/PostDeletedEvent.java`
- Modify: `.../app/src/main/java/dev/patra/interaction/app/post/service/PostRegistry.java`
- Modify: `.../app/src/main/java/dev/patra/interaction/app/post/usecase/experience/DeleteExperienceHandler.java`
- Create: `.../app/src/main/java/dev/patra/interaction/app/notification/eventhandler/PostNotificationListener.java`
- Modify: `NotificationRepository.java`、`NotificationRepositoryAdapter.java`
- Modify: `patra-api/patra-interaction/patra-interaction-boot/src/test/java/dev/patra/interaction/architecture/InteractionArchitectureTest.java:54-55`
- Test: `DeleteExperienceHandlerTest.java`、Create `.../app/src/test/.../notification/eventhandler/PostNotificationListenerTest.java`、`NotificationRepositoryAdapterIT.java`、`.../boot/src/integrationTest/.../notification/NotificationReadFlowIT.java`、`NotificationRollbackFlowIT.java`

**Interfaces:**
- Produces:
  - `record PostDeletedEvent(long postId, SubjectId subjectId, long authorId, Instant deletedAt)`，`static PostDeletedEvent of(long postId, SubjectId subjectId, long authorId, Instant deletedAt)`；`subjectId` 是**这条经验自己**在 `subject.registry` 的对象（POST，`source_id` = 帖子 ID），不是 `post.subject_id`（期刊）。
  - `PostRegistry#subjectIdOf(PostId id): SubjectId`（查不到抛 `IllegalStateException`，与 `snapshotOf` 同一说法；`snapshotOf` 改为复用它）。
  - `NotificationRepository#deleteBySubject(SubjectId subjectId): int`。
  - `PostNotificationListener#on(PostDeletedEvent event): void`（同步 `@EventListener`，不加 `@Transactional`；异常处理与 `CommentNotificationListener.guard` 相同：`DomainException` 原样抛、其余包成 `NotificationWriteFailedException`）。

- [ ] **Step 1: 写单测（handler）** `DeleteExperienceHandlerTest`：
  - `should_publish_post_deleted_event_when_deleting`：作者删自己的经验，`ArgumentCaptor<PostDeletedEvent>` 捕获，断言 `postId`、`subjectId == registry.subjectIdOf(id)`、`authorId`、`deletedAt == clock.instant()`（与 `markDeleted` 用同一时刻）。
  - `should_not_publish_when_already_deleted`：锁前已删、锁内复查已删两种情况都 `verify(events, never()).publishEvent(any())`。
  - `should_not_publish_when_not_author` / 不存在：抛原有异常且不发布。
- [ ] **Step 2: 写单测（监听器）** `PostNotificationListenerTest.should_delete_notifications_of_subject_on_post_deleted`：`verify(notifications).deleteBySubject(SubjectId.of(77L))`；`should_wrap_unexpected_failure`：仓储抛 `RuntimeException` 时抛 `NotificationWriteFailedException`。
- [ ] **Step 3: 写仓储 IT** `should_delete_all_notifications_of_subject`：对象 77 下给收件人 1、2 各一条，对象 78 下一条，`deleteBySubject(SubjectId.of(77L))` 返回 2、只剩 78 那条；`should_return_zero_when_subject_has_no_notifications`。
- [ ] **Step 4: 改流程 IT**：`NotificationReadFlowIT.should_hide_excerpt_after_post_deleted` → `should_remove_notifications_after_post_deleted`：B 在 A 的经验下评论、C 回复 B 后，A、B 各有一条提醒；A 删除经验；A、B 的列表都为空、未读数都为 0。`NotificationRollbackFlowIT` 新增 `should_keep_post_when_notification_delete_fails`：让 `deleteBySubject` 抛异常（照该文件现有的注入失败做法），删经验返回 500，经验仍在、提醒仍在。
- [ ] **Step 5: 跑测试确认失败**
  Run: `./gradlew :patra-api:patra-interaction:patra-interaction-app:test --tests "*DeleteExperienceHandlerTest*" --tests "*PostNotificationListenerTest*"`
  Expected: FAIL（类与方法不存在）
- [ ] **Step 6: 实现**：建 `domain/post/event/` 包与事件；`PostRegistry` 加 `subjectIdOf`；`DeleteExperienceHandler` 注入 `ApplicationEventPublisher`，在事务 lambda 里 `markDeleted`、`updateLocal` 之后发布事件（两处幂等返回都在发布之前），类 Javadoc 补一句「同事务发布 `PostDeletedEvent`，提醒子域据此删掉这条经验下的全部提醒」；仓储加 `deleteBySubject`（`DELETE ... WHERE subject_id = :subjectId`，走 Task 2 的索引）；新建监听器；架构测试链上 `.allowingSubscription("notification", "post")`，并把上方注释「提醒只经事件契约依赖讨论」改为「依赖讨论与投稿经验」。
- [ ] **Step 7: 跑测试确认通过**
  Run: `./gradlew :patra-api:patra-interaction:patra-interaction-app:test :patra-api:patra-interaction:patra-interaction-boot:test --tests "*InteractionArchitectureTest*" :patra-api:patra-interaction:patra-interaction-infra:integrationTest --tests "*NotificationRepositoryAdapterIT*" :patra-api:patra-interaction:patra-interaction-boot:integrationTest --tests "*Notification*" --tests "*ExperienceFlowIT*"`
  Expected: PASS
- [ ] **Step 8: 文档**：`patra-api/patra-interaction/README.md` 进程内事件一节补 `PostDeletedEvent`（投稿经验子域发布、提醒子域订阅），提醒子域的说明改成「评论或经验删除时删掉对应提醒」；runbook 里与「已删除提醒」相关的核对步骤（如有，`grep -rn "已删除" docs/patra/runbooks` 之类）同步。

### Task 4: 门户——北京时间分组与按组的时间写法

**Files:**
- Modify: `patra-portal/src/lib/format-time.ts`
- Create: `patra-portal/src/lib/notifications/groups.ts`
- Test: `patra-portal/tests/lib/format-time.test.ts`（已有则追加，没有则新建）、Create `patra-portal/tests/lib/notifications/groups.test.ts`

**Interfaces:**
- Produces:
  - `export type DayGroup = "today" | "yesterday" | "earlier"`
  - `export function dayGroupOf(iso: string, now: number): DayGroup`（北京时间日序号差 ≤ 0 → today，=1 → yesterday，其余 earlier）
  - `export function formatGroupedTime(iso: string, now: number): string`（today → `formatRelative(iso, now)`；yesterday → `HH:mm`；earlier → 今年 `10 月 21 日`、往年 `2025 年 10 月 21 日`）
  - `export interface NotificationGroup { group: DayGroup; label: "今天" | "昨天" | "更早"; items: Notification[] }`
  - `export function groupByDay(items: readonly Notification[], now: number): NotificationGroup[]`（固定顺序 今天 → 昨天 → 更早，空组不返回，组内保持传入顺序）

- [ ] **Step 0: 装依赖**：worktree 的 `patra-portal/` 里 `pnpm install`（worktree 没装依赖）；`.claude/launch.json` 不在 git，要本机预览时从主检出拷。
- [ ] **Step 1: 写测试**（`now` 取北京时间 `2026-10-29T00:00:30+08:00`，即 `2026-10-28T16:00:30Z`）：
  - `dayGroupOf`：`2026-10-28T16:00:00Z`（北京 00:00:00）→ `today`；`2026-10-28T15:59:59Z`（北京昨天 23:59:59）→ `yesterday`；`2026-10-27T15:59:59Z` → `earlier`；比 `now` 晚 5 秒 → `today`。
  - `formatGroupedTime`：今天 30 秒前 → `刚刚`；昨天 21:40 → `21:40`；`2026-10-21` → `10 月 21 日`；`2025-10-21` → `2025 年 10 月 21 日`。
  - `groupByDay`：传入顺序 [昨天, 今天, 更早, 今天]（打乱）→ 组顺序 今天、昨天、更早，今天组内两条保持传入顺序；只有更早的条目 → 只返回一个「更早」组；空数组 → `[]`。
- [ ] **Step 2: 跑测试确认失败**
  Run: `cd patra-portal && pnpm exec vitest run tests/lib/format-time.test.ts tests/lib/notifications/groups.test.ts`
  Expected: FAIL（导出不存在）
- [ ] **Step 3: 实现**：`format-time.ts` 内部复用 `partsOf` / `dayNumber`（不必导出），加上面两个函数与类型；`groups.ts` 按 `dayGroupOf` 分桶。
- [ ] **Step 4: 跑测试确认通过**（同 Step 2 命令）Expected: PASS

### Task 5: 门户——去掉删除分支、改动作文字与上下文

**Files:**
- Modify: `patra-portal/src/lib/interaction/notification-schema.ts`（删 `commentDeleted: z.boolean()`；第 17 行注释的三种动作文字同步）
- Modify: `patra-portal/src/lib/notifications/row.ts`
- Modify: `patra-portal/tests/fixtures/notification.ts`（删 `commentDeleted`）
- Test: `patra-portal/tests/lib/notifications/row.test.ts`

**Interfaces:**
- Produces（`row.ts`）：
  - `actionLabel(type)`：`回复了你` / `评论了你的投稿经验` / `@ 了你`
  - `excerptOf(notification): string | null`（截断时末尾加 `…`；`excerpt` 为 null 返回 null；不再有 deleted 分支）
  - `contextLabel(subject): RowContext | null`，`RowContext = { kind: "paper"; title: string } | { kind: "text"; text: string; post: boolean }`：文献有标题 → `paper`；文献无标题 → `{ kind: "text", text: "一篇文献的讨论", post: false }`；经验 → `post: true`，文字同现状（自己 / 别人两种）；「一条已删除的投稿经验」分支删除。
  - `notificationHref`：删掉「经验已删除去经验页」分支，其余不变。

- [ ] **Step 1: 改测试**：`actionLabel` 三条断言改成新文字；`excerptOf` 删掉 `commentDeleted` 用例，改为断言返回字符串（截断加 `…`、null 返回 null）；`contextLabel` 删掉已删除经验用例，断言经验带 `post: true`、无标题文献 `post: false`；`notificationHref` 删掉经验已删除用例。
- [ ] **Step 2: 跑测试确认失败**
  Run: `cd patra-portal && pnpm exec vitest run tests/lib/notifications/row.test.ts`
  Expected: FAIL
- [ ] **Step 3: 实现**（文件见上）。
- [ ] **Step 4: 跑测试确认通过**（同 Step 2）Expected: PASS；`pnpm typecheck` 此时会在 `NotificationRow.tsx` 报错，Task 6 修。

### Task 6: 门户——`NotificationRow` 新版式

**Files:**
- Modify: `patra-portal/src/components/notifications/NotificationRow.tsx`
- Test: `patra-portal/tests/components/notifications/NotificationRow.test.tsx`

**Interfaces:**
- Consumes: Task 4 `formatGroupedTime`；Task 5 `actionLabel` / `excerptOf` / `contextLabel` / `notificationHref`。
- Produces: `NotificationRow({ notification, now, onOpen })`，props 不变。

版式（spec 4.3，画布 `DirA` / `DirA-M`）：行 `relative`，`grid-cols-[32px_minmax(0,1fr)]`，`gap-x-3`（窄屏 `max-[880px]:gap-x-2.5`），`py-4`（窄屏 `py-3.5`），`border-b border-border-subtle`，悬停 `bg-bg-sunken`，**无** `bg-paper-50`；未读圆点绝对定位在行左侧页边距（`-left-4`，窄屏 `-left-3`，`size-1.5 rounded-full bg-clay-500`，`aria-hidden`）；头像 `AuthorAvatar size={32} mobileSize={32}`；右栏三层 `gap-1.5`：上行 `text-sm` 名字（字重未读 600 / 已读 500，颜色沿用 `nameColor`）+ 动作 `text-fg-3`（窄屏截断）+ 时间靠右 `ml-auto shrink-0 tabular-nums text-fg-3`、`title` 为 `formatDateTime`；正文 `text-base leading-6 line-clamp-2`，未读 `text-fg-1`、已读 `text-fg-2`，`excerptOf` 为 null 时不渲染；下行 `text-xs text-fg-3`，前置 13px lucide `FileText`（文献）或 `PenLine`（经验），文字一行截断，文献标题用 `RichInlineText` 渲染、不包《》。

- [ ] **Step 1: 改测试**：
  - `陈宇回复了你` 在行内、链接名以 `^未读，陈宇回复了你` 开头；
  - 下行文献只显示标题文本 `Hydrocortisone, Vitamin C, and Thiamine for Sepsis`，没有「的讨论」；带内联标记的标题用例（第 166 行）改成断言标题渲染、无《》；
  - 时间：`now` 与 `createdAt` 同日 3 小时 → `3 小时前`；昨天 → `HH:mm`；更早 → `10 月 21 日`；`<time>` 的 `title` 是 `2026-10-20 09:12` 格式；
  - 未读行没有 `bg-paper-50` class，有圆点（`data-unread="true"`）；已读行名字 `font-medium`、正文 `text-fg-2`；
  - `excerpt: null`（文献下线）时只有上下两行；
  - 删掉「该评论已删除」用例；动作文字断言（第 93、102 行）改成 `回复了你`；
  - 保留：对象 key 为空时是按钮、点开调 `onOpen`、中键也算打开。
- [ ] **Step 2: 跑测试确认失败**
  Run: `cd patra-portal && pnpm exec vitest run tests/components/notifications/NotificationRow.test.tsx`
  Expected: FAIL
- [ ] **Step 3: 实现**（版式见上）。
- [ ] **Step 4: 跑测试确认通过**（同 Step 2）Expected: PASS；`pnpm typecheck` 通过。

### Task 7: 门户——列表按天分组

**Files:**
- Modify: `patra-portal/src/components/notifications/NotificationInbox.tsx`
- Test: `patra-portal/tests/components/notifications/NotificationInbox.test.tsx`

**Interfaces:**
- Consumes: Task 4 `groupByDay`、Task 6 `NotificationRow`。

渲染：`groupByDay(items, now)` 每组一个 `<section aria-labelledby>`，标题 `<h2>`（`text-xs font-medium tracking-wide text-fg-3`，下边 `border-b border-border-subtle pb-2`，首组 `mt-5`、后续 `mt-7`），组内 `<ol>` 每条 `<li><NotificationRow/></li>`。其余（标题栏、标签、骨架、失败、空状态、加载更多、「提醒保留 5 个月。」、跳登录）不动。

- [ ] **Step 1: 写测试**：
  - `should_group_by_day`：mock 列表返回今天 2 条、昨天 1 条、更早 1 条 → `getAllByRole("heading", { level: 2 })` 依次为 `今天`、`昨天`、`更早`；
  - `should_not_repeat_group_after_load_more`：第一页最后一条是更早、第二页全是更早 → 点「加载更多提醒」后标题 `更早` 只有一个，其下条目数 = 两页之和；
  - `should_skip_empty_groups`：只有更早 → 只有一个 h2；
  - 核对现有「单条已读 404 不回滚」用例仍在（`tests/lib/notifications/mark-read.test.tsx`），不改。
- [ ] **Step 2: 跑测试确认失败**
  Run: `cd patra-portal && pnpm exec vitest run tests/components/notifications/NotificationInbox.test.tsx`
  Expected: FAIL
- [ ] **Step 3: 实现**（渲染见上）。
- [ ] **Step 4: 跑测试确认通过**（同 Step 2）Expected: PASS

### Task 8: 门户——Playwright 冒烟

**Files:**
- Modify: `patra-portal/tests/e2e/notifications.spec.ts`

- [ ] **Step 1: 改断言文字**：第 201、380 行 `回复了你的评论` → `回复了你`；第 206 行 `在评论中 @ 了你` → `@ 了你`；第 357 行不变。
- [ ] **Step 2: 加删除场景**（放在第一个用例「全部已读」之前）：B 删掉他回复 A 的那条评论 → A 刷新「回复我的」，这条提醒不在列表里，铃铛数字比删除前少 1。
- [ ] **Step 3: 跑**：新后端部署前，mini 上还是旧后端，删除场景会失败；本地验收时跑除删除场景以外的部分：
  Run: `cd patra-portal && PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528 pnpm test:e2e tests/e2e/notifications.spec.ts --workers=1`
  Expected: 文字断言通过；删除场景标注「待部署」，合并部署后在 mini 跑全量补齐。

### Task 9: 设计资料

**Files:**
- Create: `docs/patra/design/snapshots/2026-10-28-notification-inbox-hifi.zip`
- Modify: `docs/patra/design/snapshots/README.md`
- Modify: `patra-portal/docs/design-briefs/v0.14/discussion.md`（§3.19、§4.6）

- [ ] **Step 1: 读回画布**：`Artifact read` 画布的 `project/canvas.json` 与全部 `.dc.html`、`p127.css`、`patra.css`（用户可能手改过），存到 scratchpad。
- [ ] **Step 2: 打快照**：zip 内 `project/`（全部画板与样式）+ `README.md`（画布地址；定稿板 `DirA` 桌面、`DirA-M` 窄屏、`Rule3` 删除规则；`Main` 现状、`DirB` / `DirC` / `Rule1` / `Rule2` 未采用；示例数据是示意）。`snapshots/README.md` 索引加一行。
- [ ] **Step 3: 改简报**：§3.19「一行」与 ASCII 示意换成新版式（分组、三层、动作文字、时间写法、未读圆点、无浅底、32px 头像），注明出处是本 spec 与画布；§4.6 删 `inbox.row.comment-deleted` / `.post-deleted`，加 `inbox.groups`。只写当前版本，不留旧版对照。

### Task 10: 全量验证与本地验收

- [ ] **Step 1: 后端全量**（耗时长，后台跑）
  Run: `./gradlew check`（worktree 根目录）
  Expected: BUILD SUCCESSFUL（含各模块 test、integrationTest、spotless、ArchUnit）
- [ ] **Step 2: 门户全量**
  Run: `cd patra-portal && pnpm lint && pnpm typecheck && pnpm test`
  Expected: 全部通过
- [ ] **Step 3: 本机对照画布**：门户 dev（`preview_start` 的 portal 配置，网关指 mini；mini 是旧后端，接口多出的 `commentDeleted` 字段会被 zod 忽略，不影响页面）打开 `/notifications`，桌面与 375 宽各截一张，与画布 `DirA`、`DirA-M` 对照：分组标题、三层字号、圆点位置、无浅底、窄屏动作截断。
- [ ] **Step 4: 停下等用户验收**：汇报改动与截图，不提交、不开 PR。用户确认后统一提交（commitlint：中文动词开头的 subject），开 draft PR（`Closes PAP-127`），按根 CLAUDE.md「PR 与代码评审」走评审与监控；合并部署后在 mini 跑 Task 8 的删除场景、核对用户账号 A 的「该评论已删除」提醒已清零。
