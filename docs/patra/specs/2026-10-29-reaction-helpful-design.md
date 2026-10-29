# reaction 子域：「有帮助」的点与取消、计数与获赞（PAP-131）

> **Issue**：Linear PAP-131 / GitHub #316
> **产品依据**：release spec `docs/patra/release-specs/v0.15-helpful.md` §2.1.1、决策 A、决策 B、§5 D2
> **日期**：2026-10-29
> **范围**：只改 `patra-api/patra-interaction`。讨论串统计与排序（PAP-132）、对象统计与获赞接口（PAP-133）不在本 Issue，它们要用的表与端口见第 9 节。

## 1. 要做成什么

用户能给别人的评论（根评论、楼内回复）与投稿经验点「有帮助」、再取消；每个目标的有帮助数、每个用户的获赞数与明细在同一事务里维护，任何时刻都等于明细算出来的值。规则全部来自 release spec §2.1.1，这里不重述产品理由，只写怎么落：

| 规则 | 落法 |
|---|---|
| 能点评论与经验，文献、收藏夹不能 | 目标类型只有 `COMMENT`、`POST` 两种（第 2 节） |
| 登录、邮箱已验证、账号正常；冷静期不能 | 复用 `PublishGuardGateway.requireEligible`（第 4 节） |
| 每天 300 次，无间隔，取消不退 | `PublishKind.HELPFUL`，与评论、经验共用作者锁（第 4 节） |
| 不能给自己点，含匿名经验作者 | 目标作者 = 点的人 → 403（第 3 节） |
| 已删除占位不能点 | 占位、整条去掉、经验已删、评论所在对象失效 → 404（第 3 节） |
| 可取消，数字同步减 | 明细真删掉（影响行数 1）才 −1（第 4 节） |
| 匿名经验、作者在自己匿名经验下的评论：计入目标数、不计获赞 | 明细行的受益人为空（第 2、3 节） |
| 删评论（整条或变占位）、删经验：它收到的有帮助一起删 | 同步 `@EventListener`，与删除同事务（第 5 节） |
| 注销不清理 | reaction 不实现 `UserDataCleanupGateway`（第 7 节） |
| 不发提醒 | reaction 不发布任何事件 |

**不做**：有帮助的提醒、点赞人列表、踩与多种回应、给文献 / 收藏夹点、按明细重算任务（PAP-133）、列表里带计数与「我点过没有」、排序（PAP-132）、获赞的 HTTP 接口（PAP-133）。

## 2. 数据模型

新 schema `reaction`，迁移放 `patra-interaction-infra/src/main/resources/db/migration/reaction/`。三张表，都只存用户 ID，不对其他 schema 建外键（沿用 v0.13 决策 B）。

```sql
CREATE SCHEMA reaction;

-- 明细：一人对一个目标最多一条
CREATE TABLE reaction.helpful
(
    user_id           BIGINT          NOT NULL,   -- 点的人
    target_type       VARCHAR(16)     NOT NULL,   -- 'COMMENT' | 'POST'
    target_id         BIGINT          NOT NULL,   -- comment_id | post_id
    credited_user_id  BIGINT          NULL,       -- 这一条计入谁的获赞；匿名经验 / 匿名作者的评论为 NULL
    created_at        timestamptz(6)  NOT NULL,
    PRIMARY KEY (user_id, target_type, target_id),
    CONSTRAINT ck_helpful_target_type CHECK (target_type IN ('COMMENT', 'POST')),
    CONSTRAINT ck_helpful_not_self CHECK (credited_user_id IS DISTINCT FROM user_id)
);
CREATE INDEX idx_helpful_target ON reaction.helpful (target_type, target_id);

-- 每个目标的有帮助数：有过一次有帮助才有行；取消到 0 时保留行（值为 0），目标删除时整行删掉
CREATE TABLE reaction.target_stats
(
    target_type    VARCHAR(16)  NOT NULL,
    target_id      BIGINT       NOT NULL,
    helpful_count  INTEGER      NOT NULL,
    PRIMARY KEY (target_type, target_id),
    CONSTRAINT ck_target_stats_type CHECK (target_type IN ('COMMENT', 'POST')),
    CONSTRAINT ck_target_stats_count CHECK (helpful_count >= 0)
);

-- 每个用户的获赞：第一次被计入时才有行
CREATE TABLE reaction.user_stats
(
    user_id           BIGINT  NOT NULL,
    helpful_received  BIGINT  NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT ck_user_stats_received CHECK (helpful_received >= 0)
);
```

- **受益人存在明细行上**（`credited_user_id`）：取消、删除联动按它扣获赞，不用回头再读讨论与登记表判匿名；PAP-133 的「按明细重算」两张统计表都只需 `GROUP BY` 明细，不依赖别的 schema。匿名位登记后不变（`subject.registry.owner_anonymous`、`post.post.anonymous` 的注释都写明「never changes」），所以存下来不会过时。`ck_helpful_not_self` 兜底「不能给自己点」（匿名时受益人为空，这条约束拦不住，由领域层拦）。
- 类型存名字而不是数字码，与 `subject.publish_quota.kind` 一致，PAP-132 / 133 写 SQL 时直接读得懂。
- `helpful_received` 用 `BIGINT`：一个人的获赞没有上限；单个目标的有帮助数受用户数限制，`INTEGER` 足够。
- 发表守卫：`subject/` 下另加一个迁移，把 `ck_publish_quota_kind` 改为 `IN ('COMMENT', 'POST', 'HELPFUL')`，同步更新列注释。

## 3. 目标：怎么读、什么时候能点

### 3.1 子域依赖

`reaction` 进 `InteractionArchitectureTest.SUBDOMAINS`，新增两条边：

| 边 | 声明 | 用到的 |
|---|---|---|
| reaction → discussion | `allowingDependency` | `DiscussionReadPort.findById`、`CommentRecord`、`CommentDeletedEvent` |
| reaction → post | `allowingSubscription` | `PostDeletedEvent` |

底座 `subject` 不用声明（`SubjectReadPort`、`SubjectView`、`SubjectOwner`、`PublishGuardGateway`）。没有任何子域依赖 reaction，无环。PAP-132 / 133 读 reaction 的表只走 SQL（读适配器可以读本服务库的任何表），不加 Java 依赖边——讨论子域如果在 Java 里依赖 reaction 就成环了。

**与决策 B 措辞的出入（我定的）**：决策 B 写「经讨论、投稿经验的读端口取目标」。投稿经验那边不走 `PostReadPort`：它的方法全部返回 `PostAggregate`（`model.aggregate` 不是公开包，reaction 用不了），而登记表里经验那一行已经有作者（`owner_user_id`）、是否匿名（`owner_anonymous`）和状态（删除时同一事务置 `UNAVAILABLE`），走底座 `SubjectReadPort` 就够，post 子域一行不改，reaction → post 只剩订阅边。

### 3.2 解析规则（`app.reaction.service.ReactionTargets`）

把 `(类型, ID)` 解析成目标画像 `TargetProfile(target, authorId, available, creditedUserId)`：

- **评论**：`DiscussionReadPort.findById(commentId)`；不存在 → 查不到。取它的 `subjectId` 用 `SubjectReadPort.findByIds` 取对象视图。
  - `available` = 评论 `ACTIVE` 且对象 `ACTIVE`（评论所在的经验已删、文献已下线时，讨论区整体 404，评论也不能点）。
  - 匿名 = 对象有所有者、所有者匿名、所有者就是评论作者（与 `PublishCommentHandler` 给事件算 `authorAnonymous` 的判定一致）。
- **经验**：`SubjectReadPort.findIdsByKeys(POST, postId)` → `findByIds`；没登记 → 查不到。`authorId` = `owner.userId`，`available` = 对象 `ACTIVE`，匿名 = `owner.anonymous`。
- `creditedUserId` = 匿名时 `null`，否则 `authorId`。

### 3.3 能不能点（领域规则，`TargetProfile.requireMarkableBy(userId)`）

按顺序判：

1. 查不到或 `available = false` → `HelpfulTargetNotFoundException`（404，原因码 `HELPFUL_TARGET_NOT_FOUND`）。
2. `authorId == userId` → `SelfHelpfulException`（403，原因码 `SELF_HELPFUL`）。匿名经验的作者同样被拦，因为判的是作者而不是受益人。

## 4. 点与取消

### 4.1 点：`MarkHelpfulHandler`（`PUT`，幂等）

事务外（不占连接）：

1. 解析目标并 `requireMarkableBy`——不合法的请求不去打 identity、不消耗额度。
2. 已经点过（`exists`）→ 直接返回当前状态，不查资格、不开事务、不耗额度（点过之后进了冷静期的人重复点仍是幂等的 200）。
3. `PublishGuardGateway.requireEligible(userId)`：账号不正常（含冷静期）、邮箱未验证 → 409。

事务（`TransactionOperations`，只有这一段）：

4. `guard.admit(userId, HELPFUL)`：拿作者锁 `subject.publish:{userId}`、判每天 300 次、记一笔。
5. 拿目标锁 `reaction.target:{类型}:{ID}`（advisory，事务级）。
6. **锁内重读目标**，再判一次 `available`（只判这一项：作者不变）。已不可点 → 404，事务回滚，第 4 步记的那一笔也回滚。
7. `INSERT … ON CONFLICT DO NOTHING`；影响行数 1 才：目标计数 `+1`（upsert），受益人不为空时获赞 `+1`（upsert）。影响行数 0（并发的另一个请求先插了）什么都不加——这次额度照样消耗，属于两个请求同时到的边角，接受。
8. 读目标当前计数，返回 `HelpfulResult(helpful = true, helpfulCount)`。

额度在插入之前记：只有锁内才能确定「真的会插入」，而规范要求配额锁最先拿（4.3）。第 2 步的事务外预检挡住了绝大多数重复点。

### 4.2 取消：`UnmarkHelpfulHandler`（`DELETE`，幂等）

- 只要登录：不查发表资格（冷静期、未验证的人也能撤回自己点过的，与「删除自己的评论不查资格」一致），不查目标是否存在（目标删了，明细已随之删掉，取消就是 no-op）。
- 事务：拿目标锁 → `DELETE … RETURNING credited_user_id`；删到 1 行才：目标计数 `−1`，受益人不为空时获赞 `−1`。
- 返回 `HelpfulResult(helpful = false, helpfulCount)`；目标没有统计行时计数为 0。不退额度。

### 4.3 锁与顺序

全服务统一的顺序：**配额锁（作者）→ 业务对象锁（楼锁 / 经验作者锁）→ 目标锁 → 统计行（目标行先、用户行后，多个用户行按 `user_id` 升序）**。

- 点：配额锁 → 目标锁 → 目标行 → 用户行。取消：目标锁 → 目标行 → 用户行。
- 删评论：楼锁 → 评论行 →（监听）目标锁 → 目标行 → 用户行。删经验：经验作者锁 →（监听）目标锁 → …
- reaction 从不拿楼锁、经验作者锁、评论行锁，与讨论、投稿经验之间不会互等成环。
- 同一事务里改多个用户行时，先 `SELECT … ORDER BY user_id FOR UPDATE` 再改（多行 `UPDATE` 不保证加锁顺序）。

**为什么要目标锁**：删评论的监听在删除事务里 `DELETE` 明细，但点的事务不拿楼锁。读已提交下，点的事务可能在删除事务的 `DELETE` 之后、提交之前插入一条——删除提交后留下一条孤儿明细，获赞多算 1。两边都先拿同一把目标锁就关掉了这个窗口：

- 点先拿到锁：读到 `ACTIVE`、插入、提交、放锁；监听拿到锁后的 `DELETE` 是新语句，看得见这条已提交的明细，一并删掉。
- 监听先拿到锁：删除提交后放锁；点的事务锁内重读，看到占位或已删除，404 回滚。

## 5. 删除联动（`app.reaction.listener.HelpfulTargetDeletedListener`）

同步 `@EventListener`，与发布方同一事务；不加 `@Transactional`，不用 `@TransactionalEventListener`（照 `CommentNotificationListener`）。任何一步失败，删除一起回滚。

| 事件 | 处理 |
|---|---|
| `CommentDeletedEvent`（`placeholder` 为真或假都处理） | 目标 `(COMMENT, commentId)`：拿目标锁 → 删明细（`RETURNING credited_user_id`）→ 删统计行 → 按受益人汇总扣获赞 |
| `PostDeletedEvent` | 目标 `(POST, postId)`：同上 |

- **依赖的事件语义**：级联去掉的占位不再发 `CommentDeletedEvent`（它变占位时已经发过，那时有帮助已经清掉）。以后改这个事件的语义时要回来看这里。
- 扣的数量按删掉的明细行算，不按统计行读——两者在同事务维护下相等，但明细是事实来源。
- **经验下的评论不连带**（2026-10-29 用户定）：经验删除时只删经验自己收到的有帮助；它下面的评论没有被删、只是随经验一起看不到了，它们收到的有帮助与计入的获赞保留。理由：评论作者的获赞不该因为别人删了自己的经验而消失；与删评论留占位时回复的有帮助保留是同一种父子关系。

## 6. 接口

放 `adapter.reaction.rest.HelpfulController`。都要登录；请求体为空；响应同一形状，供门户的乐观更新对账：

| 方法与路径 | 作用 | 成功 |
|---|---|---|
| `PUT /comments/{commentId}/helpful` | 给评论点 | 200 |
| `DELETE /comments/{commentId}/helpful` | 取消 | 200 |
| `PUT /posts/{postId}/helpful` | 给经验点 | 200 |
| `DELETE /posts/{postId}/helpful` | 取消 | 200 |

```json
{ "helpful": true, "helpfulCount": 3 }
```

| 情况 | 状态 | `traits[]` 原因码 |
|---|---|---|
| 未登录 | 401 | — |
| 目标不存在、是占位、已删除、所在对象已失效 | 404 | `HELPFUL_TARGET_NOT_FOUND` |
| 给自己点 | 403 | `SELF_HELPFUL` |
| 账号状态异常（封禁、冷静期、已注销） / 邮箱未验证 | 409 | `ACCOUNT_RESTRICTED` / `EMAIL_UNVERIFIED`（沿用发表守卫） |
| 今天第 301 次 | 429，带 `Retry-After` | `DAILY_LIMIT_REACHED` |

- 原因码放新的 `ReactionErrorTrait`，门户按 `traits[]` 判断（同 `discussion-actions.ts`）。
- `PublishRateLimitedException.dailyLimitReached` 现在拼「今天已经发表了 N 条{名词}」，对有帮助读不通。给 `PublishKind` 加一句当天用完时的说法（评论「今天已经发表了 100 条评论」，经验同理，有帮助「今天已经点了 300 次有帮助」），异常改用它。资格不符的文案「暂时不能发表」「验证邮箱后才能发表」不改：门户按原因码出自己的文案。

## 7. 注销

reaction 不实现 `UserDataCleanupGateway`：注销用户点过的明细继续计入数字，他收到的明细与 `user_stats` 行都保留（release spec §2.1.1「注销」）。`subject.publish_quota` 里他的 `HELPFUL` 行照常被底座清理删掉——那是频率计数器，不是有帮助数据。

## 8. 测试（对照 AC）

| AC | 测试 |
|---|---|
| 多线程同时点同一目标，计数 = 明细条数 | boot `HelpfulFlowIT`：N 个用户并发 `PUT` 同一条评论，`target_stats.helpful_count` = `count(*)` 明细 = N，作者获赞 = N |
| 重复点不重复计数；取消减 1；取消未点过的不报错、计数不变 | `HelpfulFlowIT` + infra `HelpfulRepositoryAdapterIT`（`ON CONFLICT`、`RETURNING`） |
| 给自己点（含匿名经验作者）被拒；未验证、冷静期被拒；第 301 次被拒 | `HelpfulFlowIT`（经 HTTP 验状态码与原因码；第 301 次预置当天 300 的计数行，用不自动重发的客户端，照 `DiscussionEventRollbackFlowIT`）+ `TargetProfileTest`、`PublishQuotaTest` |
| 匿名经验及作者匿名评论不计获赞 | `HelpfulFlowIT`：匿名经验被点、作者在自己匿名经验下的评论被点 → 目标数 +1、作者获赞 0；同一经验下别人的评论照常计入 |
| 删除后明细被删、获赞扣回，同事务（订阅方失败时删除回滚） | `HelpfulFlowIT`：删评论（整条 / 变占位）、删经验后明细与统计行没了、获赞扣回；测试订阅方在删除事件上抛异常时，评论 / 经验仍在，明细与计数原样 |
| InteractionArchitectureTest 通过、无环 | 更新 `SUBDOMAINS` 与两条边后跑 boot `test` |

另外：领域与 app 的单测（`ReactionTargetsTest`、`MarkHelpfulHandlerTest`、`UnmarkHelpfulHandlerTest`、监听测试，用 `RecordingTransactions` 断言事务内外顺序）；迁移 IT 验约束（类型取值、不能给自己、计数非负、`HELPFUL` 种类可写）；`InteractionMigrationIT` 的 schema 白名单加 `reaction`；注销清理流程测试加一条「有帮助数据原样保留」。

## 9. 给 PAP-132 / PAP-133 的接口

**目标类型**：字符串码 `'COMMENT'`（ID = `discussion.comment.comment_id`）与 `'POST'`（ID = `post.post.post_id`）。Java 枚举 `domain.reaction.model.enums.HelpfulTargetType` 不是公开包，别的子域在 SQL 里写字符串码，不 import 它。

**表**（读适配器直接 SQL 关联，不 import reaction 的类）：

| 表 | 主键 | 列 | 用途 |
|---|---|---|---|
| `reaction.target_stats` | `(target_type, target_id)` | `helpful_count INTEGER ≥ 0` | 每条评论 / 经验的有帮助数。**没有行 = 0**，关联用 `LEFT JOIN … COALESCE(helpful_count, 0)` |
| `reaction.helpful` | `(user_id, target_type, target_id)` | `credited_user_id BIGINT NULL`、`created_at` | 「我点过没有」：`EXISTS (… WHERE user_id = :viewer AND target_type = 'COMMENT' AND target_id = c.comment_id)`，走主键；按明细重算：`GROUP BY target_type, target_id` 与 `GROUP BY credited_user_id`（`credited_user_id IS NOT NULL`） |
| `reaction.user_stats` | `user_id` | `helpful_received BIGINT ≥ 0` | 获赞。没有行 = 0 |

例：讨论列表带有帮助数与「我点过没有」

```sql
SELECT c.*, COALESCE(s.helpful_count, 0) AS helpful_count,
       EXISTS (SELECT 1 FROM reaction.helpful h
                WHERE h.user_id = :viewer AND h.target_type = 'COMMENT' AND h.target_id = c.comment_id) AS helpful_by_me
  FROM discussion.comment c
  LEFT JOIN reaction.target_stats s ON s.target_type = 'COMMENT' AND s.target_id = c.comment_id
```

「最有帮助」排序要按 `COALESCE(helpful_count, 0) DESC, created_at DESC, comment_id DESC` 走索引还是现排，由 PAP-132 定；本 Issue 只保证 `target_stats` 主键可按 `(target_type, target_id)` 关联。

**获赞读端口**：`domain.reaction.port.read.HelpfulReadPort`

```java
/// 用户收到的有帮助（获赞）：本人未删除、非匿名的评论与经验收到的有帮助总数；没有记录为 0。
long helpfulReceived(long userId);
```

实现 `infra.reaction.persistence.HelpfulReadAdapter` 读 `reaction.user_stats`。PAP-133 的获赞 HTTP 接口放 `adapter.reaction.rest`，经 `app.reaction.usecase.query` 的 QueryService 调它。

**重算的口径**（给 PAP-133 的「按明细重算」）：`target_stats.helpful_count = count(*) GROUP BY (target_type, target_id)`；`user_stats.helpful_received = count(*) WHERE credited_user_id IS NOT NULL GROUP BY credited_user_id`。重算时要与点 / 取消互斥，按目标锁、再按用户行的顺序拿锁（第 4.3 节）。

## 10. 包与类

```
domain.reaction
├─ model.enums.HelpfulTargetType          COMMENT / POST
├─ model.vo.HelpfulTarget                 (type, id)
├─ model.vo.TargetProfile                 作者、可点、受益人；requireMarkableBy
├─ exception.{ReactionErrorTrait, HelpfulTargetNotFoundException, SelfHelpfulException}
├─ port.repository.HelpfulRepository      锁、明细、两张统计表
└─ port.read.HelpfulReadPort              获赞
app.reaction
├─ service.ReactionTargets                解析目标（讨论读端口 + 底座读端口）
├─ usecase.helpful.{MarkHelpful*, UnmarkHelpful*, HelpfulResult}
└─ listener.HelpfulTargetDeletedListener
infra.reaction.persistence.{HelpfulRepositoryAdapter, HelpfulReadAdapter}
adapter.reaction.rest.{HelpfulController, response.HelpfulResponse}
```

底座改动：`PublishKind` 加 `HELPFUL("有帮助", 300, Duration.ZERO)` 与当天用完的说法；迁移改 `ck_publish_quota_kind`。

## 11. 我定的与用户定的

**我定的**（读的时候请核对）：

1. 经验目标走底座登记表，不改 `PostReadPort`（3.1）。
2. 受益人存在明细行上（第 2 节）。
3. 取消只要登录、不查资格、不查目标（4.2）。
4. 预检已点过时不耗额度；并发重复点仍可能多耗一次（4.1）。
5. 评论所在对象失效（经验已删、文献下线）时评论不能点，404（3.2）。
6. 自己 403、不可点 404、资格 409、超额 429（第 6 节）；有帮助用完的文案按种类改（第 6 节）。
7. 统计行取消到 0 时保留（第 2 节）。

**用户定的**（2026-10-29）：经验删除后，它下面评论收到的有帮助**保留**，继续计入评论作者的获赞（第 5 节）。
