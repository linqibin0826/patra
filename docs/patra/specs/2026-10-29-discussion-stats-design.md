# 讨论串统计与「最有帮助」排序（PAP-132）

> **Issue**：Linear PAP-132 / GitHub #317
> **产品依据**：release spec `docs/patra/release-specs/v0.15-helpful.md` §2.1.2、§2.1.3、决策 A / B / C；设计简报 `patra-portal/docs/design-briefs/v0.15/helpful.md` 附录 A 第 4–8 条
> **日期**：2026-10-29
> **范围**：只改 `patra-api/patra-interaction` 的 discussion、post 两个子域与底座的一个值对象。有帮助的点与取消（PAP-131，已完成）、批量对象统计 / 获赞接口 / 按明细重算任务（PAP-133）不在本 Issue。

## 1. 要做成什么

| 要求 | 落法 |
|---|---|
| 每楼带未删除回复数、最后一条未删除回复的发表时间（根评论是占位时也带） | 新表 `discussion.thread_stats`，同事务维护；`ThreadView` 加两项（第 2、3、5 节） |
| 讨论列表带对象的评论总数（标题条数，不受分页影响） | 新表 `discussion.subject_stats`；`ThreadPageView` 加 `total`（第 2、5 节） |
| 每条评论带有帮助数；有会话时带「我点过没有」 | 读适配器 SQL 关联 `reaction.target_stats` / `reaction.helpful`（第 5 节） |
| 讨论列表 `sort=helpful`：根评论自己的有帮助数降序，相同时新的在前；楼内回复始终正序 | `ThreadSort.HELPFUL`，游标（有帮助数、时间、ID）（第 6 节） |
| 每条经验（期刊页列表、主页列表、经验页）带有帮助数、「我点过没有」、回复数 | post 读端口批量取三项（第 7 节） |
| 期刊页经验列表加排序：最新投稿（默认、现有口径）/ 最有帮助；带总条数 | `ExperienceSort`，同一种三元组游标；列表响应加 `total`（第 7 节） |
| 现有评论回填 | 迁移里幂等 upsert（第 4 节） |

**不做**：手动重算任务、批量对象统计接口（PAP-133，表口径见第 2、4 节与共享笔记）；楼内回复的有帮助合计（spec 2.1.2：不计入楼主）；讨论串参与人数、按最后回复排序（spec 第 3 节 I）；主页经验列表的排序。

## 2. 数据模型

迁移放 `patra-interaction-infra/src/main/resources/db/migration/discussion/`，建表与回填同一个文件。不对其他 schema 建外键。

```sql
CREATE TABLE discussion.thread_stats
(
    root_id        BIGINT          NOT NULL,
    reply_count    INTEGER         NOT NULL,
    last_reply_at  timestamptz(6)  NULL,
    PRIMARY KEY (root_id),
    CONSTRAINT ck_thread_stats_count CHECK (reply_count >= 0),
    CONSTRAINT ck_thread_stats_last CHECK ((reply_count = 0) = (last_reply_at IS NULL))
);

CREATE TABLE discussion.subject_stats
(
    subject_id     BIGINT   NOT NULL,
    comment_count  INTEGER  NOT NULL,
    PRIMARY KEY (subject_id),
    CONSTRAINT ck_subject_stats_count CHECK (comment_count >= 0)
);
```

**口径**（「未删除」= `state = 'ACTIVE'`，占位不算）：

- `thread_stats.reply_count`：`root_id = X AND parent_id IS NOT NULL AND state = 'ACTIVE'`，任意深度的回复都算（楼内回复平铺显示）。
- `thread_stats.last_reply_at`：同一批回复的 `max(created_at)`；没有时为 `NULL`。
- `subject_stats.comment_count`：`subject_id = S AND state = 'ACTIVE'`，根评论与楼内回复都算。文献的「讨论数」和经验的「回复数」都是它（经验在底座里是一个 POST 对象）。

**没有行 = 0 / NULL**，读方一律 `LEFT JOIN … COALESCE`。行的生命周期：第一次有回复（评论）时 upsert 出行；减到 0 时保留（与 `reaction.target_stats` 一致）；根评论被整条删掉时删它的 `thread_stats` 行（那时计数必为 0）。`subject_stats` 行不删：对象失效（文献下线、经验删除）时评论不动，计数也不动，恢复后照旧正确。

## 3. 写路径（决策 A）

写 `discussion.comment` 的只有 `CommentRepositoryAdapter`，调用方只有发表、编辑、删除三个 Handler；讨论子域不实现注销清理（`UserDataCleanupGateway`）。编辑不改计数。所以只改发表、删除两个 Handler，在它们现有的事务里、紧跟评论行的改动、发事件**之前**维护统计（唯一的例外是级联删到根时删楼统计行，见 3.2）。端口方法加在 `CommentRepository`（统计是评论写入的一部分，同一个适配器）。

### 3.1 发表

事务内、插入之后：

- 回复：`thread_stats` upsert `reply_count + 1`，`last_reply_at = GREATEST(last_reply_at, :createdAt)`。用 `GREATEST`：`createdAt` 在拿楼锁之前算好，两条回复抢同一把楼锁时后拿到锁的那条时间可能更早。
- 根评论与回复：`subject_stats` upsert `comment_count + 1`。

插入一定是真插入（新雪花 ID），所以直接 +1。

### 3.2 删除

`CommentAggregate.removalFor` 只对 `ACTIVE` 的评论返回 `PLACEHOLDER` / `REMOVE`（已是占位返回 `NONE`），所以这两种结果都意味着一条未删除评论没了：

- `subject_stats` `comment_count − 1`。
- 是回复：先 `SELECT … FROM discussion.thread_stats WHERE root_id = :rootId FOR UPDATE` 锁住楼统计行，**再用一条新语句**同时改两列：`reply_count = reply_count − 1`，`last_reply_at` = 剩余未删除回复的 `max(created_at)`（没有剩余时为 `NULL`）。决策 A 要求「先锁楼统计行，再取剩余回复的最新时间」：读已提交下，新语句的快照晚于行锁，取到的就是锁之后的事实。两列必须在同一条语句里改：`ck_thread_stats_last` 不能延迟校验，分两句改会在删最后一条回复时违反约束。整个删除本来就在楼锁里，发回复也拿楼锁，楼内不会有并发插入；先锁行的写法同时防住将来不拿楼锁的写方（如重算）。
- 级联去掉的占位祖先（`removeOrphanedAncestors`）已经是占位、不计数；链一直去到根、根被整条删掉时，删除它的 `thread_stats` 行。这一步在级联里，因而在发 `CommentDeletedEvent` 之后（现有顺序是先删这条、发事件、再级联），不调整事件顺序：这行只有持有同一把楼锁的事务会碰，reaction 不碰，没有锁序问题。

### 3.3 锁顺序

全服务顺序（接 reaction spec 4.3）：**配额锁 → 楼锁 → 评论行 → `thread_stats` 行 → `subject_stats` 行 → 发事件**（reaction 的删除联动在发事件时同步拿目标锁、改它自己的统计行）。发根评论不拿楼锁，只动 `subject_stats`。reaction 不碰这两张表，没有反向的锁序。

同一对象下两楼同时有人发回复：各拿各的楼锁和楼统计行，最后在同一个 `subject_stats` 行上排队，提交即放，不成环。

`PostDeletedEvent`：经验下的评论不删（reaction spec 第 5 节同一结论），计数不变。

## 4. 回填

同一迁移文件里，在建表之后：

```sql
INSERT INTO discussion.thread_stats (root_id, reply_count, last_reply_at)
SELECT root_id,
       count(*) FILTER (WHERE state = 'ACTIVE'),
       max(created_at) FILTER (WHERE state = 'ACTIVE')
  FROM discussion.comment
 WHERE parent_id IS NOT NULL
 GROUP BY root_id
ON CONFLICT (root_id) DO UPDATE
   SET reply_count = EXCLUDED.reply_count, last_reply_at = EXCLUDED.last_reply_at;

INSERT INTO discussion.subject_stats (subject_id, comment_count)
SELECT subject_id, count(*) FILTER (WHERE state = 'ACTIVE')
  FROM discussion.comment
 GROUP BY subject_id
ON CONFLICT (subject_id) DO UPDATE SET comment_count = EXCLUDED.comment_count;
```

写成幂等 upsert，PAP-133 的重算任务可以直接复用这两条（它另外要处理「统计行在、明细已没有」的行，见共享笔记）。迁移在服务启动时执行，那时没有并发写入。

## 5. 讨论的读

### 5.1 有帮助数与「我点过没有」

- `CommentRows.RECORD_SELECT` 加 `LEFT JOIN reaction.target_stats hs ON hs.target_type = 'COMMENT' AND hs.target_id = c.comment_id`，选出 `COALESCE(hs.helpful_count, 0) AS helpful_count`；`CommentRecord` 加 `helpfulCount`。所有读评论的方法（列表、预览、楼内回复、单楼、对话链、按 ID）都带上。
- 「我点过没有」不给每个读方法加访客参数，`DiscussionReadPort` 加一个批量方法：`Set<Long> findHelpfulMarked(long viewerId, Collection<Long> commentIds)`（走 `reaction.helpful` 主键）。`DiscussionQueryService` 在有访客时查一次，交给 `CommentViews.assemble`。
- `CommentView` 加 `int helpfulCount`、`Boolean helpful`：没有会话为 `null`（AC：只在有会话时返回），有会话为真假。占位强制 `helpfulCount = 0`、`helpful = null`（占位不显示数字、不能点；它的有帮助在变占位时已被 reaction 删掉，这里只是兜底）。
- 发表、编辑的响应（访客就是作者）：`helpful = false`，不去查（不能给自己点）。

### 5.2 楼统计与总数

- `DiscussionReadPort` 加 `Map<Long, ThreadStats> findThreadStats(Collection<Long> rootIds)`（没行的不在结果里，视为 0 / `null`）与 `int countComments(SubjectId subjectId)`（没行为 0）。`ThreadStats(int replyCount, Instant lastReplyAt)` 放 `domain.discussion.model.read`。
- `ThreadView` 加 `int replyCount`、`Instant lastReplyAt`（可空），讨论列表与单楼视图都带；根评论是占位时照样带。「展开其余 N 条回复」的 N 由门户用 `replyCount` 减去预览里未删除的条数，`hasMoreReplies` 不变。
- `ThreadPageView` 加 `int total`：对象没登记时 0；登记了按 `subject_stats` 取。每页都带（一次主键查询）。

## 6. 讨论的「最有帮助」

- `ThreadSort` 加 `HELPFUL`，参数值 `helpful`（默认仍是 `asc`）。
- 排序：`ORDER BY helpful_count DESC, c.created_at DESC, c.comment_id DESC`，其中 `helpful_count = COALESCE(hs.helpful_count, 0)`。占位根评论没有统计行，按 0 排。
- 游标：三元组（有帮助数、时间、ID），条件 `(COALESCE(hs.helpful_count, 0), c.created_at, c.comment_id) < (:helpful, :at, :id)`。游标里的有帮助数取自同一条查询读出的那一行，排序与游标一致。
- 游标格式：底座新增值对象 `domain.subject.model.vo.RankCursor(int helpfulCount, Instant createdAt, long id)`，对外 `{有帮助数}.{自 EPOCH 的微秒}.{ID}`，讨论与经验共用（底座只放两个以上子域共用的东西，这个正好两个用）。底座不能抛讨论或经验的异常（会多出 subject → 子域的依赖），所以 `RankCursor.parse(String)` 只认非空原文、格式不对返回空，由讨论、经验各自转成自己的 422「游标不合法」。`asc` / `latest` 仍用现有的两段式 `ThreadCursor`；查询服务按排序选解析器。
- 不加索引：要按另一个 schema 的统计排序，建不出覆盖索引；一个对象下的根评论很少（几十楼），PG 现排即可。
- **翻页不重复的边界**：数字不变时键集分页保证不重复不遗漏；翻页期间有人点或取消，同一条可能出现两次或被跳过——release spec 决策 D 已定由门户按 ID 去重，后端不做快照。

## 7. 经验

### 7.1 每条经验的三项

`PostReadPort` 加批量方法 `Map<PostId, PostStats> findStats(Collection<PostId> postIds, Long viewerId)`，`PostStats(int helpfulCount, int replyCount, Boolean helpful)` 放 `domain.post.model.read`；没有任何统计时三项为 0 / 0 / （有访客时 `false`，没有时 `null`）。回复数要两跳：`post.post` 的 `subject_id` 是期刊，经验自己的对象 ID 在登记表里——

```sql
SELECT p.post_id,
       COALESCE(hs.helpful_count, 0)  AS helpful_count,
       COALESCE(ds.comment_count, 0)  AS reply_count,
       EXISTS (SELECT 1 FROM reaction.helpful h
                WHERE h.user_id = :viewer AND h.target_type = 'POST' AND h.target_id = p.post_id) AS helpful
  FROM post.post p
  LEFT JOIN reaction.target_stats hs ON hs.target_type = 'POST' AND hs.target_id = p.post_id
  LEFT JOIN subject.registry r ON r.target_type = 3 AND r.source_id = p.post_id
  LEFT JOIN discussion.subject_stats ds ON ds.subject_id = r.subject_id
 WHERE p.post_id IN (:ids)
```

（没有访客时不查 `EXISTS`，`helpful` 为 `null`。）`ExperienceViews` 的 `items`、`withoutAuthor`（主页列表）、`detail`、`authored`（发表 / 编辑的响应）都带上；`ExperienceItemView` 与 `ExperienceResponse` 加 `helpfulCount`、`helpful`、`replyCount`。现有分页方法签名不变。

### 7.2 期刊页列表的排序与总条数

- `ExperienceSort { LATEST, HELPFUL }`（`domain.post.model.enums`），参数 `latest`（默认，可不传）/ `helpful`。`LATEST` 完全是现有口径（投稿日期倒序、同日发布时间倒序、再 ID 倒序），游标仍是经验 ID。
- `HELPFUL`：`PostReadPort` 加 `pageByJournalHelpful(SubjectId journal, RankCursor after, int limit)`，返回 `HelpfulRankedPost(经验, 排序时的有帮助数)`，`ORDER BY COALESCE(hs.helpful_count, 0) DESC, p.created_at DESC, p.post_id DESC`（相同时按发布时间新的在前，设计简报附录 A 第 7 条），游标 `RankCursor`，条件同第 6 节。现有「游标只存 ID、反查排序键」对它不成立（有帮助数会变，反查会漂），所以把数嵌进游标。下一页游标用的有帮助数取自排序那条查询读出的值（与讨论一致）；不取 7.1 的批量查询，否则两条查询之间有人取消有帮助，下一页会跳过同数的经验，门户去重补不回来（终审发现，2026-10-30 改）。
- 总条数：列表响应加 `total` = 期刊下未删除的经验数（`count(*) … WHERE subject_id = :journal AND status = 'ACTIVE'`，走 `idx_post_subject_active`），两种排序都带。只加在期刊页列表，主页列表不加。

## 8. 接口变化

都是加字段、加参数取值，没有新路径。

| 接口 | 变化 |
|---|---|
| `GET /discussions/{type}/{key}/threads` | `sort` 多一个取值 `helpful`；响应加 `total`；每楼加 `replyCount`、`lastReplyAt`；每条评论加 `helpfulCount`、`helpful` |
| `GET /discussions/{type}/{key}/comments/{id}/thread` | 楼加 `replyCount`、`lastReplyAt`；评论加两项 |
| `GET /comments/{rootId}/replies`、对话链、发表 / 编辑评论的响应 | 评论加两项 |
| `GET /journals/{journalId}/posts` | 新参数 `sort`（`latest` / `helpful`）；响应加 `total`；每条加 `helpfulCount`、`helpful`、`replyCount` |
| `GET /users/{userId}/posts`、`GET /posts/{id}`、发表 / 编辑经验的响应 | 每条经验加三项 |

`helpful` 在没有会话时为 `null`（JSON 里是 `null`，沿用现有的序列化设置）。未知的 `sort` 取值 422，与现有一致。

## 9. 包与类

```
domain.subject.model.vo.RankCursor                         三元组游标（底座，讨论与经验共用）
domain.discussion
├─ model.enums.ThreadSort                                  + HELPFUL
├─ model.read.{CommentRecord (+helpfulCount), ThreadStats}
├─ port.read.DiscussionReadPort                            + findHelpfulMarked / findThreadStats / countComments / findRootsByHelpful
└─ port.repository.CommentRepository                       + 统计的四个写方法
domain.post
├─ model.enums.ExperienceSort
├─ model.read.PostStats
└─ port.read.PostReadPort                                  + findStats / pageByJournalHelpful / countByJournal
app.discussion  CommentViews、DiscussionQueryService、PublishCommentHandler、DeleteCommentHandler、ThreadView、ThreadPageView、CommentView
app.post        ExperienceViews、ExperienceQueryService、ExperienceItemView、新的期刊列表页视图（带 total）
infra.discussion.persistence  CommentRepositoryAdapter、DiscussionReadAdapter、CommentRows
infra.post.persistence        PostReadAdapter
adapter.discussion.rest / adapter.post.rest                 响应与参数
```

ArchUnit 不加子域、不加边：读 reaction、subject、discussion 的表都是读适配器里的 SQL（分包规范允许），没有 Java 依赖。

## 10. 测试（对照 AC）

| AC | 测试 |
|---|---|
| 发回复、删回复后楼内回复数与最后回复时间正确；删最后一条回退到上一条；删光为空 | infra `CommentRepositoryAdapterIT`（统计写方法）+ boot `DiscussionStatsFlowIT`（经 HTTP：发 3 条回复，删最新那条，时间回退；全删后 `replyCount = 0`、`lastReplyAt = null`） |
| 变成「该评论已删除」占位的评论不计入回复数与讨论数 | `DiscussionStatsFlowIT`：有回复的根评论删成占位 → `total − 1`、楼统计不变且仍随占位返回；有回复的回复删成占位 → 楼内 −1；级联去掉占位不再减 |
| `sort=helpful` 根评论按有帮助数降序、楼内回复正序；翻页不重复 | infra `DiscussionReadAdapterIT`（预置 `reaction.target_stats`，同数按新的在前，`size=1` 逐页翻完不重复不遗漏）+ `DiscussionStatsFlowIT` 经 HTTP 点有帮助后排序 |
| 经验列表两种排序都对；不传参数时与现有顺序一致 | infra `PostReadAdapterIT`（helpful 排序与游标）+ boot `ExperienceFlowIT`（不传 `sort` 的顺序与现有断言一致；`helpful` 排序；`total`） |
| 「我点过没有」只在有会话时返回，且与明细一致 | boot IT：匿名请求 `helpful` 为 `null`；登录用户点过的为 `true`、没点的为 `false`（评论与经验都测） |
| 回填后楼统计、对象统计与按明细重算一致 | infra `DiscussionStatsMigrationIT`：用 Flyway 迁到本迁移之前一版，插入含占位、多层回复、多对象的评论，再迁到最新，比对两张表与 `GROUP BY` 结果 |

另外：`ThreadSortTest`、`RankCursorTest`、`ExperienceSortTest`（解析与格式）；`DeleteCommentHandlerTest`、`PublishCommentHandlerTest` 用 `RecordingTransactions` 之类断言统计写在事务里、在发事件之前（级联删到根时删楼统计行除外）；`CommentViewsTest`（占位强制 0 / `null`、匿名访客 `null`）；`DiscussionQueryServiceTest`、`ExperienceQueryServiceTest`、`ExperienceViewsTest` 补新字段；`InteractionMigrationIT` 若有表清单则补两张表。

## 11. 我定的与待用户定的

**我定的**（读的时候请核对）：

1. 统计写在发表、删除两个 Handler 的事务里、发事件之前，端口方法加在 `CommentRepository`，不走事件监听（3、3.3）。
2. 删回复用「先 `SELECT … FOR UPDATE` 锁统计行、再一条新语句同时改回复数与最新时间」，而不是一条带子查询的 `UPDATE`；`DiscussionReadPort` 类注释的「只查 `discussion` schema」随改动更新（3.2、第 5 节）。
3. 统计行减到 0 时保留；根评论整条删掉时删楼统计行；对象统计行永不删（第 2 节）。
4. `helpful` 用可空布尔：没有会话为 `null`（5.1、第 8 节）。
5. 「我点过没有」与楼统计用独立的批量读方法，不给每个读方法加访客参数（5.1、5.2）。
6. 三元组游标做成底座值对象 `RankCursor`，讨论与经验共用；`asc` / `latest` 的游标格式不变（第 6 节）。
7. 经验的三项统计用一个独立批量查询；期刊页「最有帮助」的下一页游标取排序查询读出的数（7.1、7.2）。
8. 不为「最有帮助」加索引（第 6 节）。
9. 讨论列表每页都带 `total`；期刊页列表两种排序都带 `total`（5.2、7.2）。

**用户定的**（2026-10-29）：期刊页经验列表两种排序都带 `total`（7.2），不让门户改用汇总接口的 `allCount`。
