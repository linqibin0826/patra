# 对象统计与获赞接口：收藏人数、批量对象统计、获赞、按明细重算（PAP-133）

> **Issue**：Linear PAP-133 / GitHub #318
> **产品依据**：release spec `docs/patra/release-specs/v0.15-helpful.md` §2.1.3、决策 A、决策 C、§5 D3
> **日期**：2026-10-29
> **范围**：只改 `patra-api/patra-interaction`，外加上线 runbook。依赖 PAP-131 的 `reaction` 三张表（`2026-10-29-reaction-helpful-design.md` 第 9 节）与 PAP-132 的讨论统计表（`2026-10-29-discussion-stats-design.md` 第 2 节）。

## 1. 要做成什么

| 要求 | 落法 |
|---|---|
| 收藏人数：把文献收进文献库的人数，一人放进多个收藏夹只算一次 | 收藏子域新表 `collection.subject_stats`，按 `library_item` 行数算（第 2 节） |
| 写完立刻更新（决策 A） | 文献库条目真插入 / 真删除（`RETURNING` 拿到的行）才在同一事务里 ±1，四条写路径都在 `LibraryRepositoryAdapter` 里维护（第 2 节） |
| 注销清理删文献库条目时扣减 | `deleteAllOf` 同样按删掉的行 −1（第 2 节） |
| 现有数据回填 | 建表迁移里一条 `INSERT … SELECT … GROUP BY`（第 2 节） |
| 文献卡片按屏批量取收藏人数与讨论数，未登录也能取（决策 C） | 底座读端 `POST /subjects/stats`，不要会话；读适配器用 SQL 读收藏、讨论两张统计表（第 3 节） |
| 主页获赞，按用户 ID 取，不要会话 | `GET /users/{userId}/helpful-received`（第 4 节） |
| 手动触发的按明细重算，修数据并输出差异条数 | 底座的重算扩展点 + 每个计数一个实现 + XXL-Job `statsRecountJob`（第 5 节） |
| XXL-Job 登记、上线 runbook | `docs/patra/runbooks/v0.15-helpful-go-live-runbook.md`（第 7 节） |

**不做**：按热度排序、计数同步到 catalog、计数轮询、注销用户获赞的特殊处理（主页本来就打不开，接口照常返回数字）、给 `library_item` 加以 `subject_id` 开头的索引（统计表就是这个问题的答案；重算只在第二步按差异行逐个数）。

## 2. 收藏人数

### 2.1 表与回填

迁移 `collection/V20261029230000__create_collection_subject_stats.sql`（建表与回填同一文件）：

```sql
CREATE TABLE collection.subject_stats
(
    subject_id       BIGINT   NOT NULL,   -- subject.registry.subject_id，不建跨 schema 外键
    collector_count  INTEGER  NOT NULL,   -- 这个对象的文献库条目数
    PRIMARY KEY (subject_id),
    CONSTRAINT ck_subject_stats_collectors CHECK (collector_count >= 0)
);

INSERT INTO collection.subject_stats (subject_id, collector_count)
SELECT subject_id, count(*) FROM collection.library_item GROUP BY subject_id;
```

- **没有行 = 0**；减到 0 保留行（与 `reaction.target_stats` 一致）。
- 与讨论子域的 `discussion.subject_stats` 同名不同 schema：两张表各归各的子域，读方都带 schema 名。
- 回填在 Flyway 迁移里跑：部署时旧容器先停、新容器启动才迁移，迁移期间没有写入。

### 2.2 写路径

所有改 `library_item` 的地方都在 `LibraryRepositoryAdapter` 里，统计在同一个方法里跟着维护，调用方不用记得另调。四个方法的签名不变：

| 方法 | 调用方 | 统计怎么动 |
|---|---|---|
| `insertItem` | 收藏 | 影响行数为 1 时对该对象 +1 |
| `insertItems` | 复制收藏夹 | `INSERT … ON CONFLICT DO NOTHING RETURNING subject_id`，对返回的对象各 +1 |
| `deleteItems` | 移出文献库 | `DELETE … RETURNING subject_id`，对返回的对象各 −1 |
| `deleteAllOf` | 注销清理 | 同上 |

- **加**：`INSERT … SELECT unnest(:ids) ORDER BY 1 ON CONFLICT (subject_id) DO UPDATE SET collector_count = collector_count + 1`。
- **减**：只用 `UPDATE`，不用 upsert——PG 先对候选行判 CHECK 再判冲突，负值会先撞上约束（reaction 适配器踩过的坑）。要减时统计行一定在（加的时候建出来的）。
- **多行加锁顺序**：一条语句改多行时，PG 不保证按什么顺序拿行锁。两个用户同时批量收藏 / 移出重叠的文献会互等成死锁。加：`INSERT … SELECT … ORDER BY subject_id` 按升序处理；减：先 `SELECT subject_id … WHERE subject_id IN (:ids) ORDER BY subject_id FOR UPDATE`，再 `UPDATE`。
- **服务内的锁顺序**：用户锁（`collection.user:{userId}`）→ 文献库条目 → `collection.subject_stats` 行。重算只拿统计行锁，不拿用户锁，不会成环。
- 每个用户对一个对象最多一行，所以一次删除里同一对象最多 −1，不用按对象汇总。
- 统计行万一被改坏成比明细少（比如 0），下一次移出这篇会撞上 `ck_subject_stats_collectors` 报 500，直到跑一次重算。这是有意的：不悄悄掩盖漂移（与 reaction 的 `adjustTargetCount` 相同），重算任务就是补救手段。

## 3. 批量对象统计（底座读端）

### 3.1 接口

`POST /subjects/stats`，不要会话（登录与否结果一样），门户服务端按一屏一批调；文献页单篇也走它。

请求：`{ "type": "publication", "keys": ["123", "456"] }`

响应（按去重后的请求顺序，每个 key 一行）：

```json
{ "items": [ { "key": "123", "collectorCount": 2, "discussionCount": 5 },
             { "key": "456", "collectorCount": 0, "discussionCount": 0 } ] }
```

| 情况 | 结果 |
|---|---|
| key 没登记过、格式不对 | 照常返回一行，两项都是 0（不报错） |
| 对象已失效（catalog 软删，可能恢复） | 照常返回真实计数，与「我的状态」一致；门户不给失效文献渲染卡片 |
| `type` 未知，或这种对象既不能收藏也不能讨论（期刊） | 422 |
| `keys` 为空、超过 100 个、含 `null` | 422 |
| 对象支持其中一项（`type=post` 即投稿经验，只能讨论） | 不支持的那项为 0 |

- 字段是 `int`，JSON 里是数字（`Long → String` 只作用于包装类型 `Long`）。
- 和「我的状态」（`POST /me/library/status`）请求形状一致，门户两者同一时机取。
- 匿名 `POST` 经网关可达：网关过滤器链只对 identity 的路径要求会话，其余路由不分方法一律放行、无状态（不校验 CSRF）。

### 3.2 分层

```
adapter.subject.rest.SubjectStatsController                POST /subjects/stats
  request.SubjectStatsRequest(type, keys)                  拷贝：null 或含 null → 空列表，由查询拒绝
  response.SubjectStatsResponse(items) / SubjectStatsItemResponse(key, collectorCount, discussionCount)
app.subject.usecase.query.SubjectStatsQueryService         校验、去重、key → subject_id、补 0
  SubjectStatsView(key, collectorCount, discussionCount)
domain.subject.port.read.SubjectStatsReadPort              Map<SubjectId, SubjectStats> findStats(Collection<SubjectId>)
domain.subject.model.read.SubjectStats(collectorCount, discussionCount)
domain.subject.exception.InvalidSubjectRequestException    RULE_VIOLATION → 422
infra.subject.persistence.SubjectStatsReadAdapter          两条 SQL，各读一张统计表，按 subject_id 合并
```

- key → `subject_id` 复用 `SubjectReadPort.findIdsByKeys`（已登记才有，不合法的 key 自动跳过）。
- 读适配器两条语句，各自 `WHERE subject_id IN (:ids)` 走主键：`collection.subject_stats`、`discussion.subject_stats`。这是本版唯一一处跨子域读（决策 C），只经 SQL，不 import 收藏、讨论的 Java 类（底座被它们依赖，反过来会成环）。查不到的对象不在结果里，由查询服务补 0。

## 4. 获赞

`GET /users/{userId}/helpful-received` → `{ "helpfulReceived": 3 }`，不要会话。

```
adapter.reaction.rest.UserHelpfulController
  response.HelpfulReceivedResponse(long helpfulReceived)
app.reaction.usecase.query.HelpfulQueryService            调 HelpfulReadPort.helpfulReceived
```

- 没有行为 0；不查 identity，用户不存在也返回 0（与公开收藏夹列表一样，用户存在与否由门户的主页路由判断）。注销用户的主页打不开，不需要接口再判断。
- 字段是原始类型 `long`，JSON 里是数字。

## 5. 按明细重算

### 5.1 为什么分两步

不能一条 `UPDATE stats SET c = (SELECT count(*) …)`：读已提交下子查询用语句开始时的快照，遇到并发写入时会把别人刚加的 +1 覆盖掉（决策 A 否决「按明细重数再覆盖」的同一个理由）。所以：

1. **找差异**（不加锁）：一条聚合语句把明细数与统计行 `FULL JOIN` 对比，返回不一致的键。这一步可能把正在写的键误报进来，没关系，第二步会再核对。
2. **逐键修正**（每个键一个短事务）：**先加锁、再用新语句数**，不一致才写，返回是否改了。锁住以后，所有写这一行的事务要么已经提交（新语句看得见），要么排在后面（在我们写的值上再 ±1），不会丢。

差异条数 = 第二步里真改了的行数。数据一致时为 0。

### 5.2 扩展点

照注销清理（`UserDataCleanupGateway`）的写法：底座定接口、各子域在自己的 app 层实现，底座的 Handler 统一开事务、汇总。一个计数一个实现：

```java
// domain.subject.port.gateway
public interface StatsRecountGateway<K> {
  String counter();          // 报告里的名字，如 "collection.collector_count"
  List<K> findDrifted();     // 第一步：不一致的键
  Optional<StatsCorrection> recount(K key);   // 第二步：在底座开的事务里调用，一个键一个事务；改了才有值
}
```

`domain.subject.model.read.StatsCorrection(long before, long after)`：改之前与改之后的值，Handler 用它打日志。

`app.subject.usecase.recount.RecountStatsHandler`（命令 `RecountStatsCommand`，结果 `RecountResult(List<CounterResult> counters)`，每项一行 `CounterResult(counter, corrected)`、按计数名排序，带 `corrected()` 总数与 `summary()` 摘要 `{计数名=行数, …}`）：按 `counter()` 排序逐个跑；每个键 `TransactionOperations.execute` 一次。子域抛出的意外异常不吞，任务失败标红。

| 实现（`app.{子域}.service`） | 计数 | 第二步怎么锁、怎么数 |
|---|---|---|
| `CollectorCountRecountGatewayImpl` | `collection.collector_count` | 统计行没有就先 `INSERT (id, 0) ON CONFLICT DO NOTHING`，再 `SELECT … FOR UPDATE` 锁住，新语句 `count(*) FROM library_item WHERE subject_id = ?`（扫 16 个分区，只对差异行做），不一致就 `UPDATE` |
| `HelpfulCountRecountGatewayImpl` | `reaction.helpful_count` | `HelpfulRepository.lockTarget`（点 / 取消 / 删除联动用的同一把目标锁），锁内没有并发写，直接数、不一致就 upsert；明细没了统计行置 0、不删 |
| `HelpfulReceivedRecountGatewayImpl` | `reaction.helpful_received` | 写方不拿用户级锁，所以同收藏人数：先建出行、`FOR UPDATE` 锁住、新语句 `count(*) WHERE credited_user_id = ?`。只拿用户行锁，与写方「目标锁 → 目标行 → 用户行」的顺序不成环 |
| `ThreadStatsRecountGatewayImpl` | `discussion.thread_stats` | 拿楼锁（`discussion.thread:{rootId}`），锁内数回复数与最新回复时间；根评论已不存在就删统计行 |
| `CommentCountRecountGatewayImpl` | `discussion.comment_count` | 同收藏人数：建出行、`FOR UPDATE`、新语句数 `state = 'ACTIVE'` 的评论 |

- 第一步的聚合语句与第二步的锁内计数放在各子域自己的 Repository 里，口径与回填一致：`LibraryRepository.findCollectorCountDrift / recountCollectorCount(SubjectId)`、`HelpfulRepository.findHelpfulCountDrift / recountHelpfulCount(HelpfulTarget)`（调用方已拿目标锁）、`HelpfulRepository.findReceivedDrift / recountReceived(long)`、讨论的 Repository 同理。第二步的方法都标 `@Transactional(propagation = MANDATORY)`：锁要活到事务结束。
- 讨论两项的口径、锁名以 PAP-132 的工程 spec 与迁移为准（5.4）。
- 统计行在、明细一条都没有：收藏人数、有帮助数、获赞、评论总数置 0（保留行，读方等价）；楼统计删行（根评论已没有）。
- 有帮助数不判断目标是否还在（有意）：目标删除时删除联动已在同事务里删掉统计行，正常不会残留；万一残留，置 0 对读方（只 `LEFT JOIN` 现存评论 / 经验）没有影响，也就不必跨 schema 查评论、帖子。

### 5.4 讨论统计表（PAP-132，摘自其接口笔记）

| 表 | 主键 | 列与口径 | 写方的锁 |
|---|---|---|---|
| `discussion.thread_stats` | `root_id` | `reply_count`：`root_id` = 它、`parent_id IS NOT NULL`、`state = 'ACTIVE'` 的条数；`last_reply_at`：这些回复的 `max(created_at)`，没有为 `NULL` | 楼锁 `pg_advisory_xact_lock(hashtextextended('discussion.thread:' \|\| root_id, 0))` |
| `discussion.subject_stats` | `subject_id` | `comment_count`：`subject_id` = 它、`state = 'ACTIVE'` 的评论（根评论 + 楼内回复，占位不算） | 统计行锁（发根评论不拿楼锁） |

两表都是没行 = 0（`last_reply_at` 没行 = `NULL`），迁移版本 `V20261029210000`。重算方法放在 `CommentRepository`：`findThreadStatsDrift / recountThreadStats(long rootId)`（调用方已拿楼锁；回复数或最后回复时间任一不对都算差异，修正里的前后值是回复数）、`findCommentCountDrift / recountCommentCount(SubjectId)`。

### 5.3 任务

`adapter.subject.scheduler.StatsRecountScheduleJob`，handler `statsRecountJob`，无参数。只手动触发：mini 的 xxl-job-admin 登记为「手动」（调度类型 NONE），超时 600 秒、不重试。成功时回写 `差异 N 行：{collection.collector_count=0, …}`，日志 INFO 打同样内容；每个改了的键再打一行 INFO（计数名、键、旧值、新值），方便查哪条坏了。

## 6. 测试（对照 AC）

| AC / 要点 | 测试 |
|---|---|
| 一人把一篇文献放进两个收藏夹，收藏人数为 1；移出文献库后为 0 | boot 流程 IT `ObjectStatsFlowIT`：收藏 → 加进两个收藏夹 → 批量接口 1 → 移出 → 0 |
| 复制收藏夹只给新插入的条目 +1 | 同上：B 复制 A 的公开收藏夹（其中一篇 B 已收藏）→ 计数各对 |
| 注销清理删文献库条目后收藏人数减少 | 扩展 `DeletedUserCleanupFlowIT` |
| 批量接口一屏两项，未知 key 不报错，未登录可调 | `ObjectStatsFlowIT`：未登录调，含未登记 key、不合法 key、重复 key；422（期刊、空、含 `null`）。投稿经验类型照常返回在 `SubjectStatsQueryServiceTest` 里测 |
| 获赞 | `ObjectStatsFlowIT`：B 给 A 点有帮助 → A 的获赞 1；没有记录 → 0 |
| 重算：一致时差异为 0；改坏一行后修正并报告 | boot 流程 IT `StatsRecountFlowIT`：先跑一遍得 0；用 `JdbcClient` 改坏收藏人数、有帮助数、获赞各一行（含删掉一行、多出一行）→ 跑 → 每项 1 且值已正确 → 再跑 0 |
| 并发不丢 | infra IT：多线程同时收藏 / 移出同一文献，最后计数等于明细；重算与并发写同时跑，结束后计数等于明细 |
| 维护与回填的 SQL | infra IT：`LibraryRepositoryAdapter` 四条写路径；迁移 IT：回填后与明细一致 |
| Handler 汇总、一键一事务 | app 单测：`RecountStatsHandler`（假实现） |
| 查询服务的校验 | app 单测：`SubjectStatsQueryService`、`HelpfulQueryService` |
| 讨论两项 | `StatsRecountFlowIT` 一并改坏楼回复数与对象评论总数；`CommentRepositoryAdapterIT` 覆盖只差最后回复时间、根评论已不在的统计行；`ObjectStatsFlowIT` 的讨论数随回复发表、删除变化 |

## 7. 上线 runbook

`docs/patra/runbooks/v0.15-helpful-go-live-runbook.md`（本 Issue 起草，PAP-131 / 132 的迁移一并列入）：

1. 上线前记录 `docker stats` 与磁盘剩余（D10）。
2. 合并后 CD 部署 interaction，Flyway 自动执行 v0.15 全部迁移（建表 + 回填），核对 `flyway_schema_history`。
3. 在 xxl-job-admin 登记 `statsRecountJob`（手动、600 秒、不重试），手动触发一次，结果应为「差异 0 行」，写进 release spec D3 的「实际：」。
4. 上线后再记录一次资源。
5. 回滚：表是新增的，旧版本不读；不回滚数据。

## 8. 我定的（读的时候请核对）

**用户定的**（2026-10-29）：重算发现差异直接修正并报告差异条数，不做只报告的模式。

**我定的**：

1. 统计维护放在 `LibraryRepositoryAdapter` 的四个写方法里，签名不变，调用方不用改（2.2）。
2. 多行 ±1 按 `subject_id` 升序加锁，防批量操作互等（2.2）。
3. 批量接口用 `POST /subjects/stats`、请求形状同「我的状态」；不认识的 key 返回 0 而不是省略（3.1）。
4. 批量接口对期刊类型 422，对投稿经验类型照常返回（收藏数 0）（3.1）。
5. 获赞接口不查 identity，用户不存在也是 0（第 4 节）。
6. 重算的扩展点「一个计数一个实现」，事务由底座的 Handler 开（5.2）。
7. 明细没了的统计行置 0 保留，只有楼统计删行（5.2）。
8. 重算任务只手动触发，差异不算失败（5.3）。
9. 收藏统计表迁移版本 `V20261029230000`，避开 PAP-131 / 132 的版本（2.1）。
10. 已失效（可能恢复）的对象批量接口照常返回真实计数，与「我的状态」一致（3.1）。
