# interaction 投稿经验工程设计（PAP-122，`post` 子域）

> **Issue**：[PAP-122](https://linear.app/papertrace/issue/PAP-122)（GitHub #299），Linear Project P-PAP-11 v0.14 Discussion，Milestone BE Ready
> **版本**：[v0.14 Discussion](../release-specs/v0.14-discussion.md)（2.1.2；决策 B、E、F、G、H）
> **前序**：[interaction 底座扩展（PAP-120）](2026-10-27-interaction-foundation-extension-design.md) 第 6、8、12 节；[collection（PAP-113）](2026-10-24-interaction-collection-design.md)
> **页面要求**：设计简报 `patra-portal/docs/design-briefs/v0.14/discussion.md` §3.13 ~ §3.17、§3.20、§4.3 ~ §4.5、附录 A「PAP-122」
> **下游**：PAP-125（门户期刊经验区、经验页、主页标签）；PAP-121 / PAP-123 只经底座登记表看投稿经验（所有者、匿名、快照）
> **日期**：2026-10-27
> **状态**：已定稿（2026-10-27，用户「都按建议」），实施中

## 1. 要解决的问题

期刊页要能写、看同行的投稿经验，并显示从经验算出的审稿周期汇总（spec 2.1.2）。数据按「帖子」结构存：帖子基表 + 经验扩展表，本版只有「投稿经验」一种帖子类型；以后加求助帖只新增类型，不改表（决策 B）。经验是本地对象 `POST`，由本子域在写入的同一事务里经底座登记、改状态，讨论区和提醒只靠登记表的所有者两列与快照认识它。

## 2. 范围

**做**：`post` schema 与三张表；写、改、删经验；字段校验与一致性规则；同人同刊同投稿日期查重；匿名遮蔽；修改历史（发布后 5 分钟内的修改不记版本）；期刊页汇总（读时计算）；期刊下的经验列表、经验详情、修改历史、用户的经验列表；资格与频率走底座发表守卫；README、runbook、共享交接笔记。

**不做**（spec 第 3 节）：吧、标题、图片、帖子列表页、求助帖类型；经验下的回复（PAP-121，经验作为 `POST` 对象可被讨论）；提醒（PAP-123）；公开计数、回复数；DOI 核实（3 节 J）；检索与按周期排序（3 节 I）；PubMed 审稿周期（3 节 K）。注销清理不登记参与方：经验内容保留（决策 H，PAP-120 第 9.3 节）。

## 3. 已定的前提

| 来源 | 内容 |
|---|---|
| spec 2.1.2 | 必填：投稿日期、首轮决定日期、首轮结果（直接拒稿 / 审后拒稿 / 大修 / 小修 / 录用）、最终结果（录用 / 拒稿 / 撤稿 / 进行中）、文章类型；选填：最终决定日期、审稿轮数、审稿质量与编辑沟通评分（1–5）、是否付 APC、是否转投过、正文（Markdown 子集，≤ 2,000 字，不解析 @）；日期不晚于今天、先后有序、进行中不填最终决定日期；只收日期，时长由系统算；同一人对同一刊投稿日期不能相同；匿名发布时选、之后不能改；删除后从列表与汇总去掉，经验页显示「已删除」；每人每天最多 10 条 |
| 决策 G | 汇总读时计算；中位数与 P25–P75 用 `percentile_cont`（线性插值，同 Excel `MEDIAN` / `PERCENTILE.INC`），四舍五入到整天 |
| 简报附录 A | `window = 3y | all` 另返回全部条数；首轮用时不含直接拒稿；列表按投稿日期倒序、同日按发布时间倒序、每页 10、游标；主页列表每页 20，「关于」页只要「有没有」；重复时附已有经验 ID；写接口原因码分开 |
| PAP-120 交接 | `resolveForInteraction(JOURNAL, 期刊 ID, SUBMISSION_EXPERIENCE)`、`requireEligible` 在事务外；事务里第一个写操作前 `admit(作者, POST)`；`registerLocal` / `updateLocal` 必须在经验写入的同一事务里（`MANDATORY`），快照、状态、所有者都不能为 null，删除传当前快照；POST 快照建议 `journal` = 期刊全称、`abbreviation` = 缩写、`publishedDate` = 投稿月份 `yyyy-MM`；新迁移时间戳晚于 `20261027120100` |
| 用户拍板（2026-10-27） | 见第 4 节：文章类型 6 个；一致性规则；「是否转投过」= 投本刊之前投过别的期刊、被拒后改投；编辑也查资格、删除不查；我定的几处全部认可 |

## 4. 字段与校验（`domain.post`）

### 4.1 取值

| 字段（JSON） | 类型 | 必填 | 取值 |
|---|---|---|---|
| `submittedOn` | 日期 | 是 | 1990-01-01 ~ 今天 |
| `firstDecisionOn` | 日期 | 是 | 投稿日期 ~ 今天 |
| `firstDecision` | 枚举 | 是 | `DESK_REJECT` 直接拒稿、`REJECT_AFTER_REVIEW` 审后拒稿、`MAJOR_REVISION` 大修、`MINOR_REVISION` 小修、`ACCEPT` 录用 |
| `finalOutcome` | 枚举 | 是 | `ACCEPTED` 录用、`REJECTED` 拒稿、`WITHDRAWN` 撤稿、`IN_PROGRESS` 进行中 |
| `finalDecisionOn` | 日期 | 否 | 首轮决定日期 ~ 今天 |
| `articleType` | 枚举 | 是 | `ORIGINAL_RESEARCH` 原创研究、`REVIEW` 综述、`SYSTEMATIC_REVIEW` 系统综述 / Meta 分析、`CASE_REPORT` 病例报告、`SHORT_COMMUNICATION` 短篇通讯、`OTHER` 其他 |
| `reviewRounds` | 整数 | 否 | 1–10 |
| `reviewQuality` | 整数 | 否 | 1–5 |
| `editorCommunication` | 整数 | 否 | 1–5 |
| `apcPaid` | 布尔 | 否 | 付了 / 没付 |
| `previouslyRejected` | 布尔 | 否 | 是否转投过：投本刊之前投过别的期刊、被拒后改投到本刊 |
| `body` | 文本 | 否 | Markdown 原文，≤ 2,000 字素且 UTF-8 ≤ 8,000 字节，不解析 @ |
| `anonymous` | 布尔 | 否（缺省 false） | 只在创建时生效 |

「今天」是北京时间（`Asia/Shanghai`）的日期，由注入的 `Clock` 算；与发表守卫的「每天」同一时区，常量在本子域另定义一份（底座的 `PublishQuota` 不公开）。

### 4.2 一致性规则（用户 2026-10-27 拍板）

首轮结果分两类：**终局**（直接拒稿、审后拒稿、录用：首轮就是最终决定）和**修改**（大修、小修）。

| 规则 | 违反时的字段错误 |
|---|---|
| 直接拒稿、审后拒稿 → 最终结果只能是拒稿；录用 → 只能是录用 | `finalOutcome` / `INCONSISTENT` |
| 首轮终局或最终结果为进行中 → 不填最终决定日期 | `finalDecisionOn` / `NOT_APPLICABLE` |
| 直接拒稿（没有送审）→ 不填审稿轮数、审稿质量 | `reviewRounds`、`reviewQuality` / `NOT_APPLICABLE` |
| 审后拒稿、录用 → 审稿轮数只能是 1 | `reviewRounds` / `INCONSISTENT` |

申诉成功这类少见情况不收（用户定）。

### 4.3 字段错误码

422 `RULE_VIOLATION`，`errors[]` 一次列出全部问题（表单提交时全部校验、焦点去第一个出错的字段）。先逐字段解析，日期先后与首轮 / 最终结果的一致性只在参与的字段都解析成功时才判；例外是「这种组合下不该填」：最终结果是进行中或首轮终局时，最终决定日期只要填了就报 `NOT_APPLICABLE`，不再看它的写法（告诉用户「不用填」比「写法不对」有用）。请求体类型不对（如 `reviewRounds` 传字符串）在反序列化阶段就是 400，不带 `errors[]`。

| 码 | 含义 | 简报文案 |
|---|---|---|
| `REQUIRED` | 必填缺失 | 请填写投稿日期 / 请选择首轮结果 …… |
| `INVALID` | 日期写法不对、枚举值未知 | — |
| `FUTURE` | 晚于今天 | 日期不能晚于今天 |
| `TOO_EARLY` | 早于 1990-01-01 | — |
| `BEFORE_SUBMISSION` | 首轮决定日期早于投稿日期 | 首轮决定日期不能早于投稿日期 |
| `BEFORE_FIRST_DECISION` | 最终决定日期早于首轮决定日期 | 最终决定日期不能早于首轮决定日期 |
| `NOT_APPLICABLE` | 这种组合下不该填 | 稿件还在进行中，不用填最终决定日期 …… |
| `INCONSISTENT` | 与首轮结果矛盾 | 首轮直接拒稿的稿件，最终结果只能是拒稿 |
| `OUT_OF_RANGE` | 轮数、评分越界 | 审稿轮数填 1 到 10 之间的整数 |
| `TOO_LONG` | 正文超长 | 正文最多 2,000 字 |
| `INVALID_CHARACTER` | 正文含控制字符（换行、制表符除外）或 U+2028 / U+2029 | — |

正文规范化：换行统一成 `\n`，NFC，去首尾空白；去完为空等于没有正文。写法照 collection 的 `TextRules`，在本子域另写一份（子域之间不共享私有包）。

### 4.4 算出的时长

- `firstRoundDays` = 首轮决定日期 − 投稿日期。
- `totalDays` = 最终决定日期 − 投稿日期；首轮终局时等于 `firstRoundDays`；修改类没填最终决定日期（含进行中）时为 null。

## 5. 存储（`db/migration/post/`）

两条迁移：`V20261027153100__create_post_schema.sql`（`CREATE SCHEMA post`）、`V20261027153200__create_post_tables.sql`。表不对 `subject` 建跨 schema 外键；只存用户 ID。

### 5.1 `post.post`（帖子基表，各类型共用）

| 列 | 类型 | 说明 |
|---|---|---|
| `post_id` | `BIGINT` PK | Snowflake；对外的经验 ID，也是登记表 `POST` 行的 `source_id` |
| `post_type` | `VARCHAR(32)` | `SUBMISSION_EXPERIENCE`（CHECK） |
| `subject_id` | `BIGINT` | 挂在哪个对象下：期刊在 `subject.registry` 的本地 ID |
| `author_user_id` | `BIGINT` | 作者 |
| `anonymous` | `BOOLEAN` | 发布时是否匿名，之后不变（仓储没有改它的语句） |
| `body` | `TEXT` NULL | Markdown 原文 |
| `status` | `VARCHAR(16)` | `ACTIVE` / `DELETED`（CHECK） |
| `created_at` | `timestamptz(6)` | 发布时刻 |
| `revised_at` | `timestamptz(6)` NULL | 最近一次**记了版本**的编辑；NULL = 没有修改记录（不显示「已编辑」） |
| `deleted_at` | `timestamptz(6)` NULL | 与 `status = 'DELETED'` 同真同假（CHECK） |
| `updated_at` | `timestamptz(6)` | 任何改动（含 5 分钟内的编辑）；`post.set_updated_at()` 触发器 |

索引：`(subject_id) WHERE status = 'ACTIVE'`（期刊列表与汇总）、`(author_user_id)`（作者列表、查重）。

### 5.2 `post.submission_experience`（经验扩展表，与基表一对一）

`post_id` PK + 外键到 `post.post`；第 4.1 节除 `body`、`anonymous` 外的全部字段，列名蛇形（`submitted_on`、`first_decision_on`、`first_decision`、`final_outcome`、`final_decision_on`、`article_type`、`review_rounds`、`review_quality`、`editor_communication`、`apc_paid`、`previously_rejected`）。CHECK 兜底：三个枚举的取值、轮数与评分范围、`first_decision_on >= submitted_on`、`final_decision_on` 为空或 `>= first_decision_on`、进行中时 `final_decision_on` 为空。一致性规则（4.2）只在领域层判，不写进 CHECK。

### 5.3 `post.revision`（修改历史，各类型共用）

| 列 | 说明 |
|---|---|
| `post_id`、`revision_no` | PK；`revision_no` 从 1 起 |
| `body` | 这一版的正文 |
| `fields` | `jsonb`：这一版的结构化字段（经验即 4.1 的字段），按 `post_type` 解释；以后的帖子类型存自己的字段 |
| `written_at` | 这一版成为当前内容的时刻（第一版是 `created_at`，之后是上一次记版本的 `revised_at`） |
| `replaced_at` | 被替换的时刻 |

存的是**被替换掉的旧版本**；当前版本在基表 + 扩展表里。历史接口返回「当前版本 + 旧版本倒序」。

### 5.4 查重为什么不建唯一索引

「同一作者、同一期刊、同一投稿日期只能有一条**未删除**的经验」跨两张表（作者、期刊、状态在基表，投稿日期在扩展表），又只对 `ACTIVE` 生效，建不成一条局部唯一索引；要建就得把三列抄进扩展表或另建一张登记表并在三处维护。改用作者锁串行化：写、改经验的事务先拿事务级 advisory lock `post.author:{userId}`（`hashtextextended`，写法同 collection 的 `lockOwner`），在锁里查重再写。改投稿日期的只有这两条路径，都在本子域、都拿锁；集成测试覆盖并发同日创建只成功一条。

## 6. 用例（`app.post`）

### 6.1 写经验 `PublishExperienceHandler`

```
userId = currentUser.require()
draft  = ExperienceDraft.parse(请求, today)                 // 422：一次列出全部字段错误
journal = subjects.resolveForInteraction(JOURNAL, journalKey, SUBMISSION_EXPERIENCE)  // 事务外；404 / 422 / 503
self    = publishGuard.requireEligible(userId)              // 事务外；409 / 503
journalView = subjectReads.findByIds([journal])             // 期刊全称与缩写，拼 POST 快照
transactions.execute:
  publishGuard.admit(userId, POST)                          // 第一个写操作之前；429
  posts.lockAuthor(userId)
  posts.findActiveBySubmission(userId, journal, submittedOn) → 有就 409 DUPLICATE_SUBMISSION（带已有 ID）
  posts.insert(新帖子 + 扩展行)
  subjects.registerLocal(POST, postId, SubjectOwner.of(userId, anonymous), 快照)
返回经验详情（mine = true，作者 = self）
```

- 快照：`SubjectSnapshot.of(null, 期刊全称, 投稿月份 yyyy-MM, 期刊缩写)`（PAP-123 的上下文行「<期刊缩写> · 2026 年 3 月投稿」从这里读；缩写为 null 时它退回全称）。
- 先校验再远程调用：表单错误不必等 catalog、identity。
- 事务里不 catch 底座异常（它们会把事务标成只能回滚）。

### 6.2 编辑 `EditExperienceHandler`

```
userId = currentUser.require()
post = posts.find(postId)            // 无 → 404；已删 → 404 + SUBJECT_UNAVAILABLE；不是作者 → 403
draft = ExperienceDraft.parse(请求, today)   // 422；请求里的 anonymous 不读
publishGuard.requireEligible(userId)        // 事务外；冷静期、封禁、邮箱未验证不能编辑（用户定）
transactions.execute:
  posts.lockAuthor(userId)
  post = posts.find(postId) 再取一次并重判（删除与编辑并发）
  投稿日期变了 → 查重（排除自己）→ 409
  edit = post.edit(draft, now)
    内容没变        → NONE：什么都不写
    发布 < 5 分钟   → OVERWRITE：只改内容，不记版本
    否则            → REVISE：旧版本写进 revision，revised_at = now
  posts.update(...)（必要时 posts.insertRevision）
  投稿月份比锁内重读的 post 变了 → 锁内读登记快照、换月份 → subjects.updateLocal(POST, postId, ACTIVE, 新快照)
返回经验详情
```

- 编辑不调 `admit`（不算发表）。
- 「5 分钟」从 `created_at` 算（spec「发布后 5 分钟内」），按注入的 `Clock`；恰好 5 分钟起记版本。
- 判「内容没变」比较规范化后的字段与正文。

### 6.3 删除 `DeleteExperienceHandler`

```
userId = currentUser.require()
post = posts.find(postId)            // 无 → 404；不是作者 → 403；已删 → 直接返回（幂等 204）
transactions.execute:
  posts.lockAuthor(userId)
  post = 再取一次；已删 → 返回
  postSnapshot = 登记表里的当前快照（锁内读）
  posts.markDeleted(postId, now)
  subjects.updateLocal(POST, postId, UNAVAILABLE, postSnapshot)
```

- 删除不查资格（用户定）。软删：内容留在库里，不再展示（以后的审核与合规包可能要看；清掉的数据找不回来，保留的随时能清）。删除不退还当天额度（底座规则）。
- 删除后：列表、汇总、用户列表都排除；详情 200 + `deleted: true`；修改历史 404 + `SUBJECT_UNAVAILABLE`；讨论区因登记行 `UNAVAILABLE` 不能再回复（底座）。

### 6.4 查询 `ExperienceQueryService`（登录可选，访客已登录时带会话）

| 方法 | 规则 |
|---|---|
| 期刊列表 | key 不合法 → 422；期刊未登记 → 空页（不调 catalog）；按 `(submitted_on DESC, created_at DESC, post_id DESC)` 游标分页，取 `limit + 1` 判下一页 |
| 汇总 | 同上，未登记 → 条数 0；`window` 只认 `3y`（默认）/ `all` |
| 详情 | 不存在 → 404；已删 → 只回 `deleted: true` 与期刊；否则全部字段 + 期刊（key、状态、全称、缩写） |
| 修改历史 | 不存在 → 404；已删 → 404 + `SUBJECT_UNAVAILABLE`；当前版本在前，旧版本按 `revision_no` 倒序 |
| 用户列表 | 本人 → 全部（含匿名，带 `anonymous`）；别人 → 先问 identity 这个人的主页对访客是否可见（同 `PublicCollectionQueryService.userCollections`），不可见或 identity 省略 → 空页；可见 → 只列非匿名的 |

作者名片：一页里非匿名经验的作者（与本人自己的匿名经验）一次 `findCards(ids, viewer)`；identity 不可用 → 503（列表、详情整体不可用；汇总不调 identity，照常）。用户列表不带作者（就是主页主人）。

### 6.5 匿名遮蔽与作者对象（用户 2026-10-27 拍板）

| 访客 | `author` | `anonymous` | `mine` |
|---|---|---|---|
| 别人看非匿名经验 | 名片（主页不可见时 `username`、`avatarUrl` 为 null） | false | false |
| 别人看匿名经验 | null | true | false |
| 本人看自己的匿名经验 | 自己的名片 | true | true |
| identity 省略作者 | null | false | 按会话 |

- 匿名的作者 ID 不出现在任何对他人的响应里：列表、详情、修改历史（版本里不带作者）、用户列表（别人看不到匿名条目）、错误响应（查重只会命中本人的经验）。
- `profileVisible == false` 时不返回用户名和头像（v0.13 `UserCard` 规则），讨论区（PAP-121）同样做。

## 7. 汇总（决策 G）

一条 SQL 在 PG 里算，`since` 为窗口起点（`3y` = 今天减 3 年，含当天；`all` = 1990-01-01）：

```sql
SELECT count(*)                                                     AS all_count,
       count(*) FILTER (WHERE e.submitted_on >= :since)             AS window_count,
       count(*) FILTER (WHERE in_window AND e.first_decision <> 'DESK_REJECT') AS first_round_n,
       percentile_cont(ARRAY[0.25, 0.5, 0.75]) WITHIN GROUP (ORDER BY e.first_decision_on - e.submitted_on)
         FILTER (WHERE in_window AND e.first_decision <> 'DESK_REJECT')        AS first_round_q,
       count(*) FILTER (WHERE in_window AND e.first_decision = 'DESK_REJECT')  AS desk_n,
       percentile_cont(0.5) WITHIN GROUP (ORDER BY e.first_decision_on - e.submitted_on)
         FILTER (WHERE in_window AND e.first_decision = 'DESK_REJECT')         AS desk_median,
       count(*) FILTER (WHERE in_window AND e.final_outcome = 'ACCEPTED')      AS accepted,
       …REJECTED、WITHDRAWN、IN_PROGRESS 同理
  FROM post.post p JOIN post.submission_experience e USING (post_id)
 WHERE p.subject_id = :journal AND p.status = 'ACTIVE' AND p.post_type = 'SUBMISSION_EXPERIENCE'
```

（`in_window` 即 `e.submitted_on >= :since`，实际 SQL 里展开写。）

领域规则 `ExperienceSummaryRules`：

- 窗口内不到 5 条：只返回 `count` 与 `allCount`，其余为 null（`sufficient: false`）。
- 满 5 条：首轮用时（中位数、P25、P75、样本数，样本 0 时整组 null）、直接拒稿用时（中位数、样本数，样本 0 时 null）、结果分布（按**最终结果**，与录用率同口径）、录用率 = 录用 ÷（录用 + 拒稿），返回比例与参与计算的条数（两者都为 0 时 null）。
- **四舍五入在 Java 里做**（半进位）：`percentile_cont` 返回 double，PG 的 `round(double)` 对 `.5` 按平台规则（通常银行家舍入），Excel 是半进位。10、20、30、40 四条：P25 = 17.5 → 18、P75 = 32.5 → 33，有单元测试和集成测试钉住。
- 列表不受窗口影响，永远列全部（简报 §3.13）。写完下一次读就反映（读时计算，没有缓存）。

## 8. 接口（adapter，网关前缀 `/patra-interaction`）

| 方法与路径 | 登录 | 成功 | 说明 |
|---|---|---|---|
| `GET /journals/{journalId}/posts?cursor=&limit=` | 可选 | 200 | `limit` 1–10，默认 10；`cursor` 是上一页最后一条的 ID（写法不对 422；指向不存在的经验时回空页） |
| `GET /journals/{journalId}/posts/summary?window=3y` | 可选 | 200 | `window` = `3y` / `all` |
| `POST /journals/{journalId}/posts` | 要 | 201 | 返回经验详情 |
| `GET /posts/{postId}` | 可选 | 200 | 已删除也是 200（`deleted: true`） |
| `PUT /posts/{postId}` | 要 | 200 | 整体替换字段与正文；不能改匿名、不能换期刊 |
| `DELETE /posts/{postId}` | 要 | 204 | 幂等 |
| `GET /posts/{postId}/revisions` | 可选 | 200 | |
| `GET /users/{userId}/posts?cursor=&limit=` | 可选 | 200 | `limit` 1–20，默认 20；「关于」页用 `limit=1` 判有没有 |

响应形状写进共享笔记 `interaction-PAP-122-handoff.md` 第 3 节（PAP-125 用）。`Long` 一律序列化成字符串（starter 全局规则）。

**错误**（错误码前缀 `INTERACTION`）：

| 情况 | HTTP | `traits[]` | 其他 |
|---|---|---|---|
| 字段错误 | 422 | `RULE_VIOLATION` | `errors[]` |
| 参数不合法（`window`、`limit`、`cursor`、期刊 key） | 422 | `RULE_VIOLATION` | |
| 未登录 | 401 | | |
| 不是作者 | 403 | `FORBIDDEN` | |
| 经验不存在 | 404 | `NOT_FOUND` | |
| 经验已删除（编辑、看修改历史） | 404 | `NOT_FOUND`、`SUBJECT_UNAVAILABLE` | |
| 期刊不存在 / 已失效 | 404 | `NOT_FOUND`（失效另带 `SUBJECT_UNAVAILABLE`） | 底座 |
| 同刊同投稿日期重复 | 409 | `CONFLICT`、`DUPLICATE_SUBMISSION` | `existingPostId`（字符串）、`errors[]` 带 `submittedOn` / `DUPLICATE` |
| 账号状态异常 / 邮箱未验证 | 409 | `CONFLICT`、`ACCOUNT_RESTRICTED` / `EMAIL_UNVERIFIED` | 底座 |
| 当天第 11 条 | 429 | `QUOTA_EXCEEDED`、`DAILY_LIMIT_REACHED` | `Retry-After`；底座 |
| catalog / identity 不可用 | 503 | `DEP_UNAVAILABLE` | 底座 |

`existingPostId` 经 `patra-tool-web` 的 `ProblemFieldContributor` SPI 写进 ProblemDetail（错误引擎本身只输出 `traits[]`、`errors[]`、`retryAfterSeconds`），实现放 `adapter.post.rest`。

## 9. 代码落点

| 层 | 包 | 内容 |
|---|---|---|
| domain | `domain.post.model.enums` | `PostType`、`PostStatus`、`FirstDecision`、`FinalOutcome`、`ArticleType`、`SummaryWindow` |
| | `domain.post.model.vo` | `PostId`、`ExperienceFields`、`PostBody`、`ExperienceDraft`（解析请求、收集字段错误） |
| | `domain.post.model.aggregate` | `PostAggregate`（作者判定、编辑决策：NONE / OVERWRITE / REVISE） |
| | `domain.post.model.read` | `ExperienceStats`（SQL 算出的原始统计）、`ExperienceSummary`（规则处理后的汇总）；列表与历史直接返回聚合与 `PostRevision`，不另建读模型（没有别的子域读它们） |
| | `domain.post.policy` | `ExperienceRules`（时区、日期下界、一致性）、`ExperienceSummaryRules`（门槛、取整、录用率） |
| | `domain.post.exception` | `PostErrorTrait`、`InvalidExperienceException`、`InvalidPostRequestException`、`PostNotFoundException`、`PostForbiddenException`、`DuplicateSubmissionException` |
| | `domain.post.port.repository` / `port.read` | `PostRepository`（写端：锁、查重、插入、更新、记版本、软删）、`PostReadPort`（列表、详情、历史、统计） |
| app | `app.post.usecase.experience` | 三个命令与处理器 |
| | `app.post.usecase.query` | `ExperienceQueryService` 与视图 |
| infra | `infra.post.persistence` | `PostRepositoryAdapter`、`PostReadAdapter`、`PostRows`（`JdbcClient` 原生 SQL，jsonb 用注入的 `JsonMapper`） |
| adapter | `adapter.post.rest` | `JournalPostController`、`PostController`、`UserPostController`、请求与响应 record、`PostProblemFieldContributor` |

四层都有 `post` 包（规则 22b）；只依赖底座公开包；不改 `InteractionArchitectureTest`、`InteractionMigrationIT`。没有新模块、新依赖。

## 10. 测试

| 测试 | 位置 | 覆盖 |
|---|---|---|
| `ExperienceDraftTest` | domain | 每个字段的必填、写法、范围；4.2 的每条一致性规则；多处错误一次列出；交叉规则只在字段解析成功时判；今天按北京时间（UTC 16:00 两侧） |
| `PostBodyTest` | domain | 规范化（CRLF、NFC、首尾空白）；2,000 字素与 8,000 字节边界；控制字符（换行、制表符放行）；空白等于没有 |
| `PostAggregateTest` | domain | 作者判定；没变 → NONE；4 分 59 秒 → OVERWRITE；恰好 5 分钟 → REVISE，旧版本的 `written_at` 第一次是 `created_at`、之后是上一次的 `revised_at`；已删除不能编辑；时长计算 |
| `ExperienceSummaryRulesTest` | domain | 4 条 → 只有条数；5 条 → 全部；半进位（17.5 → 18、32.5 → 33）；样本 0 的组为 null；录用率分母只算录用与拒稿 |
| 三个处理器的测试 | app | 远程调用在事务外、`admit` 是事务里第一个调用、`registerLocal` / `updateLocal` 在事务里；查重 409 带 ID；编辑的三种决策；删除幂等、不查资格；编辑查资格；不是作者 403 |
| `ExperienceQueryServiceTest` | app | 遮蔽表（6.5）每一行；主页不可见时去掉用户名与头像；用户列表的本人 / 别人 / 主页不可见；未登记期刊不调 catalog |
| `PostMigrationIT` | infra | 约束名逐条断言（CHECK 遇 NULL 放行的坑） |
| `PostRepositoryAdapterIT`、`PostReadAdapterIT` | infra | 读写往返；排序与游标（同日按发布时间）；删除后排除；汇总的分位数与窗口；作者锁阻塞 |
| `ExperienceFlowIT` | boot | 全部 AC（第 11 节），经 HTTP、真实 PG、假 catalog（期刊解析）与假 identity |

`FakeCatalog` 加期刊解析分支（`/_internal/venues/resolve/by-ids`、`by-nlm-ids`，按 URL 区分，追加在类末尾），PAP-121 只动 `FakeIdentity`，冲突面最小。

## 11. AC 对应

| AC | 测试 |
|---|---|
| 汇总口径：满 5 条才出统计；近 3 年与全部切换；撤稿与进行中不计入录用率 | `ExperienceFlowIT.should_compute_summary_by_window_and_threshold` 等 |
| 匿名：他人视角的期刊列表、详情、主页标签拿不到作者 ID；本人可见并标注匿名 | `ExperienceFlowIT.should_hide_anonymous_author_from_others`：以他人身份取四个接口的**原始响应体**断言不含作者 ID |
| 删除后从列表与汇总中消失，详情返回「已删除」 | `ExperienceFlowIT.should_remove_deleted_post_from_list_and_summary` |
| 同一人对同一刊相同投稿日期重复创建被拒 | `ExperienceFlowIT.should_reject_duplicate_submission_date`（含 `existingPostId`）；并发同日只成功一条 |
| 写完经验后下一次读汇总立即反映 | `ExperienceFlowIT.should_reflect_new_post_in_next_summary` |

另：资格与频率（第 11 条 429、未验证邮箱 409）、编辑记版本与 5 分钟、登记表的所有者与快照、编辑与删除后的登记状态。

## 12. 上线与文档

- runbook `v0.14-discussion-go-live-runbook.md` 第 6 节补：`post` schema 与两条迁移已执行（`flyway_schema_history` 有 `20261027153100`、`20261027153200`）；不需要新任务、新配置。
- README：第 1 节 `post` 一行补实现要点；第 4 节加「post 的存储与并发」一小节。
- 共享笔记 `interaction-PAP-122-handoff.md`：接口形状、作者对象、两条共用规则。

## 13. 由我定的点（用户 2026-10-27 已认可）

1. 列表返回正文原文，摘录由门户截（经验页的页面描述本来就要门户把正文转成纯文本）；不在后端解析 Markdown。
2. 日期最早 1990-01-01；审稿轮数 1–10；评分 1–5。
3. 删除是软删，内容留在库里只是不展示。
4. 主页标签对别人沿用收藏夹的规则（先问 identity 主页是否可见）。
5. 作者主页对访客不可见时不返回用户名和头像。

以下是写 spec 时定的，未单独征求：

6. 查重靠作者锁，不建唯一索引（5.4 节）。
7. 修改历史存被替换的旧版本，结构化字段放 jsonb、各帖子类型共用；当前版本不重复存。
8. 内容没变的编辑什么都不写；「5 分钟」从发布时刻算，恰好 5 分钟起记版本。
9. 已删除经验的详情是 200 + `deleted: true`（简报 `post.deleted` 不是 404），修改历史与编辑是 404 + `SUBJECT_UNAVAILABLE`（与讨论区「对象已删除」同一个原因码）。
10. 不是作者编辑或删除 → 403（经验本来公开，不用 404 掩盖存在）。
11. 游标就是上一页最后一条的 ID：帖子不物理删除，服务端总能反查它的排序键。
12. 先校验字段再做远程调用。

## 14. 实施记录（2026-10-27）

计划 `docs/patra/plans/2026-10-27-interaction-post.md`，本会话内联 TDD 执行（13 个任务）。按编排约定全程未提交。

### 14.1 与本设计的偏离

| # | 偏离 | 原因 |
|---|---|---|
| 1 | 读端口返回聚合与 `PostRevision`，`model.read` 只有 `ExperienceStats`、`ExperienceSummary`（第 9 节已同步） | 没有别的子域读投稿经验，另建一套读模型只是重复 |
| 2 | 新增 `app.post.service.PostRegistry`（取经验当前的登记快照）；编辑只在投稿月份变了时才取 | 编辑与删除都要原样或换月份传回快照（`updateLocal` 不接受 null） |
| 3 | 帖子 ID 由仓储发（`PostRepository.nextId()`） | Snowflake 生成器在 `patra-tool-jpa`，app 不依赖它 |
| 4 | 用户列表的条目不带作者 | 作者就是主页主人；本人看自己时也不必调 identity |
| 5 | `FakeCatalog` 除末尾追加期刊段外，改了 `clear()` 与 `transform()` 开头的分流 | 期刊请求也以 `/by-ids` 结尾，必须先按 `/venues/` 分流 |
| 6 | 详情与修改历史的「取这一条」用写端仓储 `PostRepository.find`，`PostReadPort` 只有列表、历史版本与统计 | 写、改、删也要按 ID 取聚合，同一个方法不在两个端口各写一份 |
| 7 | 编辑、删除的登记快照改在锁内读（第 6.2、6.3 节已同步） | 收尾评审 I-1：锁外读会让并发编辑把旧月份留在登记表；这只是本地查询，放进事务不占远程调用 |

### 14.2 实施中发现的坑

- `RestTestClient`（RANDOM_PORT 下底层是 Apache HttpClient 5）遇到 429 会按 `Retry-After` 自动重发；每日上限的 `Retry-After` 是到明天 0 点，测试直接卡到超时。流程测试里测 429 改用 JDK `HttpClient` 直接发（`ExperienceFlowIT.postWithoutRetry`）。
- `JdbcClient.paramSource(...)` 会替换掉之前逐个 `param(...)` 设的参数，两者不能混用；扩展表的写入把帖子 ID 也放进同一个 `MapSqlParameterSource`。
- 源码里写 `" "` 的测试字符串，经编辑工具写盘时变成了真换行、字符串断开；改用 `Character.toChars(0x2028)` 构造。

### 14.3 收尾评审（opus，全分支）

结论没有 Critical。处理：

| 发现 | 处理 | 测试 |
|---|---|---|
| I-1 编辑、删除在锁外读登记快照与「月份变没变」，同一作者并发编辑时登记快照的月份会和经验本身不一致 | 改到锁内按重读的内容判、读 | `EditExperienceHandlerTest.should_decide_snapshot_from_post_read_under_lock`、`DeleteExperienceHandlerTest.should_read_snapshot_under_lock`（先红后绿） |
| I-2 并发同日发布的流程测试删掉作者锁也会过（发表守卫的锁已经串行化发布）；只靠作者锁的编辑路径没有用例 | 补顺序测试：编辑先拿作者锁、再重读、再查重；删掉锁后它变红（变异验证） | `EditExperienceHandlerTest.should_check_duplicates_under_author_lock` |
| 两个 8 参数的响应 record 没用 `@Builder`（代码规范） | 改用 `@Builder` | 流程测试照常通过 |
| M 201 响应里的 `createdAt` 是纳秒、之后读回是微秒（PG 精度），两次拿到的字符串不一样 | 写经验时把发布时刻截到微秒再落库、再返回（用户 2026-10-27 要求修） | `PublishExperienceHandlerTest.should_truncate_created_at_to_micros`（先红后绿） |

需要判断的「大修 / 小修 + 录用 + 轮数 1」不加规则（用户拍板的一致性规则里没有）。

### 14.4 已知限制

收尾评审里没修的 Minor，用户 2026-10-27 同意留着：

1. **查重依赖不变量**：`findActiveBySubmission` 用 `LIMIT 1` 取一条，编辑时在 Java 里排除自身。前提是「同人同刊同日只有一条未删除的经验」一直成立（写、改都在作者锁内查重）；如果有人绕过用例直接写库造出两条，编辑其中一条时可能取到自己而漏判另一条。
2. **汇总响应用了领域 record**：`ExperienceSummaryResponse` 直接嵌入 `ExperienceSummary` 的嵌套 record（FirstRound、DeskReject、Outcomes、Acceptance），领域里改字段名会直接改变接口形状。
3. **`FakeCatalog.Journal` 没用 `@Builder`**：5 个参数，按代码规范应改 `@Builder`；它是集成测试替身，与同文件的 `Publication` 写法一致，留着。

### 14.5 验证

- 单元测试：domain 42 例、app 37 例、adapter 2 例（`post` 部分）；infra 集成测试 `PostMigrationIT`、`PostRepositoryAdapterIT`、`PostReadAdapterIT`（20 例）；boot 流程测试 `ExperienceFlowIT`（11 例，覆盖第 11 节全部 AC）。
- interaction 全部集成测试：infra 86 例、boot 47 例全部通过（`CollectionFlowIT` 等不受影响）。
