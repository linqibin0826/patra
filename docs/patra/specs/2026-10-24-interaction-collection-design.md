# interaction 收藏与收藏夹工程设计（PAP-113）

> 状态：已定稿，直接实施（2026-10-24 起不再有写代码前的 Codex 评审）
> 日期：2026-10-24
> Issue：PAP-113（GitHub #283），Linear Project P-PAP-10 v0.13 Library
> 依据：release spec `docs/patra/release-specs/v0.13-library.md` 2.1.1、2.1.2、2.1.5，决策 B、C、D、E、F、G、H；PAP-112 工程 spec `2026-10-24-subject-registry-design.md`（subject 读端口与 gateway）；PAP-108 工程 spec `2026-10-24-identity-external-contract-design.md`（批量用户摘要）；PAP-110 工程 spec `2026-10-24-catalog-citation-data-export-design.md`（导出与复制引用）；PAP-107 设计简报 `patra-portal/docs/design-briefs/v0.13/library.md` 附录 A（页面对后端的要求）

## 1. 要解决的问题

interaction 的 `collection` 子域要让登录用户把文献收进自己的文献库、写备注，用扁平的收藏夹整理，把收藏夹设为公开给别人看和复制；门户还要能一屏一次查清「我收藏过哪些、在哪些收藏夹里」，并能拿到一个范围内的文献 key 交给 catalog 导出或生成引用。注销执行后，这个用户的收藏与收藏夹要在一天内被物理删除。

产品规则都已在 release spec 2.1 与设计简报里定死，本文只定实现：表结构、并发与上限、排序、接口与错误语义、与 subject / identity / catalog 的衔接、注销清理任务。

## 2. 范围

**做**（全部在 patra-interaction 内）：

- domain `collection`：收藏夹与条目的规则（名称、简介、备注、上限、可见性、复制命名）、端口、领域异常与原因码。
- app `collection`：写用例（收藏、移除、备注、收藏夹增删改、设公开、放进 / 移出收藏夹、复制、注销清理）与查询服务（文献库概览、条目列表、我的状态、公开收藏夹、主页列表、导出 key）。
- infra `collection`：三张表的迁移（两张按 `user_id` 哈希分 16 区）、原生 SQL 持久化、identity 批量用户摘要的客户端适配器。
- adapter `collection`：门户接口、注销清理的 XXL-Job 处理器 `deletedUserCleanupJob`。
- boot：接入 `patra-tool-security`（身份断言 → `CurrentUserPort`）；identity 的 HTTP Interface 代理与分组超时。
- 文档：interaction README 的 collection 一节；上线 runbook 补注销清理任务的登记与验证；共享笔记 `PAP-113-collection-contract.md`（给 PAP-114）。

**不做**：

- 网关：不改。`/patra-interaction/**` 已放行（`anyRequest().permitAll()`），需要登录的接口由服务里 `CurrentUserPort.require()` 保证；带令牌的请求网关已查过会话并写入断言。
- 公开计数（多少人收藏、被复制几次）：release spec 3 D。
- 收藏期刊、作者等其他对象：release spec 3 G；`SubjectType` 本版只开「收藏文献」。
- 门户、catalog、identity 的代码：本 Issue 只消费它们的现有契约。

## 3. 已定的前提

| 来源 | 内容 |
|---|---|
| release spec 2.1.1 | 用户 × 文献唯一；登录即可收藏（含邮箱未验证）；备注纯文本 ≤ 500 字素；取消收藏同时移出所有收藏夹、删备注；每人 20,000 条；文献失效时条目保留、显示收藏时的标题 |
| release spec 2.1.2 | 扁平；名称 1 ~ 100 字、同一用户下不重名；简介 ≤ 500 字；多归属；删夹不删条目；默认私有，设公开要求邮箱已验证、账号状态正常；可见当且仅当 identity 判定所有者主页对访客可见；复制 = 同名（重名加序号）的私有夹 + 文献加进自己的库（已有不重复）+ 不复制备注；夹内按加入时间倒序，可切出版日期、期刊；每人 200 个夹，每夹 5,000 条 |
| release spec 2.1.5 | 冷静期照常可用；注销执行后每日任务物理删除收藏与收藏夹（含公开的），别人复制的不受影响；表里只存用户 ID |
| release spec 决策 B | 一个库、子域一个 schema、一份 Flyway 历史；状态窄表（文献库条目、收藏夹条目）按 `user_id` 哈希分 16 区，不建 JPA 实体，原生 upsert；不对 subject 建跨 schema 外键 |
| release spec 决策 E / F | interaction 调 `POST /_internal/users/summaries` 拼所有者信息；设公开时回查邮箱与账号状态；可见性只看 `profileVisible`，只透传访客 ID |
| release spec 决策 G | 注销清理是每日 XXL-Job，分批回查用户状态，`DELETED` 的物理删除 |
| release spec 决策 H | 「我的状态」一次最多 100 个对象、带会话；公开收藏夹页、主页收藏夹栏在访客已登录时带会话 |
| release spec 决策 I | 导出时门户从 interaction 拿文献 key，再调 catalog；一次最多 5,000 条 |
| PAP-112 契约 | 新增互动只经 `SubjectGateway.resolveForInteraction`，且不能在外层事务里调；已有互动直接用表里的 `subject_id`；`SubjectReadPort.findIdsByKeys` ≤ 100、`findByIds` ≤ 5,000；`SubjectView.key` 在 catalog 整行消失时为 null；排序用快照的 `publishedDate`（降精度字符串字典序倒排）与 `journal`；subject 的异常不捕获，直接传播到 web 层 |
| PAP-108 契约 | `UserSummariesReq(userIds ≤ 100, viewerUserId)`；identity 不认识的 ID 从结果里省略（≠ 已注销）；`DELETED` 是占位、`profileVisible=false`；`profileVisible` 为假时不得把该用户的头像地址、用户名放进给这位访客的响应 |
| PAP-110 契约 | 导出 `ids` 1 ~ 5,000、复制引用 1 ~ 100，都按请求顺序；编号样式按请求顺序编号 |
| PAP-107 简报附录 A | 接口要回的字段、两个时间（收藏时间、加入该夹的时间）、「未分类」范围、访客可导出 / 复制引用对他可见的公开收藏夹、复制整体成败、各原因码分开、批量放进收藏夹部分超限要回篇数 |
| 代码事实 | 网关只对 identity 的路径做 `.authenticated()`，其余放行；下游 `SecurityServletAutoConfiguration` 从断言建立 `CurrentUserPort`；断言公钥在 Nacos `patra-common.yaml`，interaction 已导入 |
| 代码事实 | Flyway 未开 `outOfOrder`；已用版本 `20261024100000`、`20261024100100`、`20261024200000`，本 Issue 的脚本版本必须更大 |
| 代码事实 | 错误语义两条路径：领域异常带**一个**标准 `StandardErrorTrait` 加一个自定义原因码 trait（identity 的 `IdentityErrorTrait` 先例，响应 `traits[]` 里带原因码）；字段错误实现 `HasFieldViolations`，响应 `errors[]` 带 `{field, code, message}` |

## 4. 数据模型

### 4.1 三张表（schema `collection`，迁移 `V20261024210000__create_collection_tables.sql`）

```sql
-- 文献库条目：用户 × 对象唯一（状态窄表，按 user_id 哈希 16 区）
collection.library_item (
  user_id       BIGINT       NOT NULL,
  subject_id    BIGINT       NOT NULL,   -- subject.registry 的本地 ID，不建外键
  note          TEXT         NULL,       -- 无备注为 NULL，不存空串
  collected_at  timestamptz  NOT NULL,   -- 收藏时间（进文献库的时间）
  updated_at    timestamptz  NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, subject_id)
) PARTITION BY HASH (user_id);           -- library_item_p00 … p15
INDEX (user_id, collected_at DESC, subject_id DESC)        -- 按加入时间分页

-- 收藏夹（每人 ≤ 200 行，不分区）
collection.collection (
  collection_id BIGINT       NOT NULL PRIMARY KEY,  -- Snowflake
  user_id       BIGINT       NOT NULL,
  name          VARCHAR(400) NOT NULL,              -- 字素 ≤ 100，字节 ≤ 400
  description   TEXT         NULL,                  -- 字素 ≤ 500
  visibility    VARCHAR(16)  NOT NULL,              -- PRIVATE / PUBLIC（CHECK）
  item_count    INTEGER      NOT NULL DEFAULT 0,    -- CHECK 0..5000
  created_at    timestamptz  NOT NULL DEFAULT now(),
  updated_at    timestamptz  NOT NULL DEFAULT now()
)
UNIQUE (user_id, name)
INDEX (user_id, updated_at DESC)
INDEX (user_id) WHERE visibility = 'PUBLIC'          -- 主页公开收藏夹列表

-- 收藏夹条目：收藏夹 × 对象唯一（状态窄表，按 user_id 哈希 16 区）
collection.collection_item (
  user_id       BIGINT       NOT NULL,   -- 收藏夹所有者，冗余进来做分区键
  collection_id BIGINT       NOT NULL,
  subject_id    BIGINT       NOT NULL,
  added_at      timestamptz  NOT NULL,   -- 加入该夹的时间
  PRIMARY KEY (user_id, collection_id, subject_id)
) PARTITION BY HASH (user_id);           -- collection_item_p00 … p15
INDEX (user_id, collection_id, added_at DESC, subject_id DESC)  -- 夹内按加入时间分页
INDEX (user_id, subject_id)                                     -- 某条目在哪些夹里、删条目时连带删除
```

- 分区表的主键、唯一约束都含分区键 `user_id`（PG 的硬要求），所有查询都带 `user_id`，能裁剪到单个分区。
- `collection_item` 不对 `collection` 建外键：删夹时本来就在同一事务里按 `user_id` 显式删它的条目，外键只会多一次逐行检查，级联删除也绕不开按分区定位。同 schema 内的完整性由写入路径保证（同一事务、同一把用户锁），集成测试覆盖。（我定的）
- `collection.set_updated_at()` 在本脚本里建（每个 schema 一份，不引用 `subject.`），只挂在 `library_item` 上（改备注不显式写 `updated_at`）；分区表上建在父表的行级触发器会自动下推到各分区。`collection.collection` **不挂**：它的每条写入都显式写 `updated_at`（应用注入的时钟），而触发器在「新值等于旧值」时会换成数据库时间——复制时同一时刻建夹再放条目，更新时间会早于创建时间。（2026-10-24 用户定）
- 不存用户名、昵称、头像（R6 守着迁移脚本）。
- 不建 JPA 实体，全部 `JdbcClient` 原生 SQL（参照 `SubjectRepositoryAdapter`）。

### 4.2 为什么这样切

- 文献库条目与收藏夹条目是两张表（决策 D「库 + 多夹」）：一篇在多个夹里只有一份备注；删夹只删 `collection_item`；「未分类」= 在 `library_item` 里、在 `collection_item` 里没有行。
- `collection_item` 不要求「条目必须先在文献库里」由外键保证，而由写入路径保证：放进收藏夹只接受已在库里的条目；从库里移除时同一事务删光它在各夹里的行。
- `collection.item_count` 是维护列：主页列表、公开页、侧栏都要篇数，单夹上限检查也要它；它只在持有该用户锁的事务里改，和 `collection_item` 的增删同一事务，不会漂。文献库总数与「未分类」数不维护列，按需 `count(*)`（同分区、走主键索引，2 万行以内毫秒级）。（我定的）

## 5. 规则

### 5.1 文本

| 字段 | 规则 | 违反时（422 `errors[]`） |
|---|---|---|
| 收藏夹名称 | NFC 规范化后去掉首尾空白（含不换行、全角空格）；1 ~ 100 字素、UTF-8 ≤ 400 字节；拒绝控制字符与换行 | `name` / `REQUIRED`、`TOO_LONG`、`INVALID_CHARACTER` |
| 收藏夹简介 | 同上去首尾空白；去完为空存 NULL；≤ 500 字素、≤ 2,000 字节；允许换行 U+000A，拒绝其余控制字符 | `description` / `TOO_LONG`、`INVALID_CHARACTER` |
| 备注 | 同简介；去完为空 = 删除备注 | `note` / `TOO_LONG`、`INVALID_CHARACTER` |

- 字素按 `BreakIterator` 扩展字素簇数，字节上限 = 字素上限 × 4（与 identity 资料字段同一比例，防一个字素里塞几百个组合码点）。这几条小规则在 `domain.collection` 里自写（identity 的 `ProfileTextRules` 在别的服务的 domain 里，不能依赖）。
- 重名比较：规范化后逐字相等（区分大小写）。重名 → 409，`errors[]` 带 `name` / `TAKEN`，`traits[]` 带 `COLLECTION_NAME_TAKEN`。（我定的：大小写不同算两个名字，与文件系统、Zotero 一致；没有用户诉求前不引入大小写折叠）

### 5.2 上限（常量在 domain 的 `CollectionLimits`）

| 上限 | 值 | 触达时 |
|---|---|---|
| 每人文献库条目 | 20,000 | 收藏、复制收藏夹：409 `LIBRARY_FULL` |
| 每人收藏夹 | 200 | 新建、复制收藏夹：409 `COLLECTION_LIMIT_REACHED` |
| 每个收藏夹的条目 | 5,000 | 放进收藏夹：不报错，超出的篇数回在 `skippedFull`（简报「批量部分超限回篇数」） |
| 一次批量的条目 / key | 100 | 422 |
| 一次导出的 key | 5,000（= `SubjectReadPort.MAX_IDS`） | 422 `EXPORT_TOO_LARGE` |

### 5.3 并发：每个用户一把事务级锁

所有改动某个用户数据的事务，开头先拿 `pg_advisory_xact_lock(hashtextextended('collection.user:{userId}', 0))`，之后在锁内「查数量 / 查重名 / 查存在 → 写」。

- 上限和重名都是「先查再写」，没有锁时两个并发请求都能通过检查。一个用户自己的请求极少并发（同一人点两个按钮），锁几乎不会等待；不同用户互不影响。
- 收藏用 `INSERT … ON CONFLICT (user_id, subject_id) DO NOTHING`，锁内重复收藏直接命中已有行，返回已有条目。
- 复制别人的收藏夹只锁**复制者**：源收藏夹只读，读到的是事务开始时的快照（READ COMMITTED 下每条语句的快照；源夹在复制过程中被所有者改动，结果是改动前或改动后的某个状态，可以接受）。
- 注销清理删一个用户的数据时也拿这把锁，和该用户可能残留的请求串行。

### 5.4 可见性

| 访客 | 私有夹 | 公开夹 |
|---|---|---|
| 所有者本人（`viewer == owner`） | 可读 | 可读 |
| 其他人 / 匿名 | 404 | 调 identity 批量摘要（`userIds=[owner], viewerUserId=访客或 null`）：结果里有这个人且 `profileVisible == true` → 可读；否则 404 |

- 「省略」（identity 不认识这个 ID）按不可见处理。
- 所有者本人读自己的夹（头部、条目、导出 key）**不依赖 identity**：不做可见性判定；只调一次 identity 拼他自己的昵称、头像，identity 不可用或省略他时 `owner` 为 `null`，请求照常 200。（2026-10-24 用户定）
- 别人读：identity 不可用 → 503（门户显示「收藏夹暂时打不开」，不能当 404）。
- 主页公开收藏夹列表 `GET /users/{userId}/collections`：本人 → 自己全部 `PUBLIC` 的夹；别人 → `profileVisible` 为真时回全部 `PUBLIC` 的夹，否则回**空列表**（不是 404：门户据此决定不出「收藏夹」标签）。

### 5.5 设为公开的资格

`PUT …/visibility` 设为 `PUBLIC` 时，调 identity 批量摘要（`userIds=[自己], viewerUserId=自己`）：

| identity 返回 | 结果 |
|---|---|
| `accountStatus != ACTIVE`（BANNED、PENDING_DELETION、DELETED，或结果里没有自己） | 409 `ACCOUNT_RESTRICTED`（「账号状态异常，暂时不能公开收藏夹」） |
| `ACTIVE` 且 `emailVerified == false` | 409 `EMAIL_UNVERIFIED` |
| `ACTIVE` 且已验证 | 写入 |
| identity 不可用 | 503 |

- 先判账号状态再判邮箱：被封禁又没验证的人，门户该给的是「账号异常」而不是「去验证邮箱」。
- PENDING_DELETION 归入 `ACCOUNT_RESTRICTED`：冷静期里私人收藏照常（2.1.5），但他的主页本来就对别人不可见（2.1.2），设成公开没有意义。（我定的）
- 设为 `PRIVATE`、或已经是 `PUBLIC` 再设 `PUBLIC`：不调 identity（后者幂等直接返回）。
- identity 调用在事务外做，拿到结果再开事务写（不占着连接等远程）。

### 5.6 排序（Issue 交给本文定：内存排序，不把排序键抄进条目）

三种排序：

| `sort` | 文献库（全部 / 未分类） | 收藏夹视图（自己的、公开的） |
|---|---|---|
| `added`（默认） | `collected_at` 倒序，再 `subject_id` 倒序 | `added_at` 倒序，再 `subject_id` 倒序 |
| `published` | 快照 `publishedDate` 字典序倒序（「年、月、日依次降序，只到年的排在同年有月份的之后」），无日期的排最后；同值按 `added` 的顺序 | 同左 |
| `journal` | 快照 `journal` 不分大小写升序（`Locale.ROOT` 小写后比），无期刊的排最后；同值按 `added` 的顺序 | 同左 |

- `added`：SQL 里 `ORDER BY … LIMIT/OFFSET`，走 4.1 的两个分页索引；只对当前页的 ≤ 100 个 `subject_id` 调 `findByIds` 取状态与快照。
- `published` / `journal`：先取这个视图的全部 `(subject_id, 时间)`（文献库 ≤ 20,000、收藏夹 ≤ 5,000），按 5,000 一块调 `findByIds` 取快照，在内存里排好再切页。
- **为什么不抄进条目**：
  1. 有界：20,000 条的上限保证最坏一次是 4 次 `findByIds`（每次按主键 IN 5,000 个）加 2 万个快照的反序列化与排序，几十到一两百毫秒，而且只发生在用户主动切到这两种排序时。
  2. 抄本要有人刷新：PAP-112 契约写明「抄本的刷新归 PAP-113」。快照每天 06:30 对账刷新，抄本就要再跑一个全表回刷任务，或者在对账里跨子域写 collection 的表——前者多一个任务和一份可能漂移的冗余，后者破坏子域边界。
  3. 收藏夹条目也要排序：抄进 `library_item` 还不够，`collection_item` 要么再抄一份，要么每次 join 回 `library_item`。
  - 代价：这两种排序的分页每翻一页都要重新取全量、重新排。翻页频率低、规模有界，接受。（我定的）
- 已失效对象照样参与排序（快照还在）；`findByIds` 查不到的行（理论上不会：登记行永不删除）按无日期、无期刊处理。

### 5.7 「已失效」

条目的状态取自 `SubjectView.state`：`ACTIVE` → 正常；`UNAVAILABLE`（catalog 软删或整行消失）→ 已失效，响应带快照标题 `title`。`key` 可能为 null（整行消失），门户此时只能用 `title` 展示。

已失效条目可以改备注、移出收藏夹、从库里移除；不能被复制（复制时跳过并计数）、不进导出 key（跳过并计数）。

「收藏时记下的标题」就是登记表快照里的标题（release spec 决策 C 定的数据来源）：对象正常时每日对账刷新它，软删或整行消失后快照保留不再变，所以失效时显示的是失效前最后一次已知的标题。条目表不再单独存一份标题。

### 5.7.1 撤销移除

简报 §3.2 的「撤销」（没有备注、不在任何夹里的条目移除后 5 秒内）就是再调一次收藏接口：条目的 `collected_at` 变成撤销那一刻，在「加入时间」排序里会排到最前。被移除的条目没有备注和归属，除收藏时间外没有别的东西可丢，不为它单独做软删或「带原时间重建」的接口。（我定的）

### 5.8 复制别人的公开收藏夹

`POST /collections/{id}/copies`，需登录。

1. 读源收藏夹，按 5.4 判可见（所有者本人复制自己的公开夹也允许，结果同样是一个新的私有夹）；源夹必须是 `PUBLIC`，私有夹即使是本人的也不能复制（404）。不可见 → 404。
2. 取源夹全部条目（≤ 5,000，按 `added_at` 倒序），`findByIds` 取状态；`ACTIVE` 的进入复制清单，其余计入 `skippedUnavailable`。
3. 事务（锁复制者）：
   1. 收藏夹数已到 200 → 409 `COLLECTION_LIMIT_REACHED`。
   2. 清单里不在自己库里的篇数 `n`；库里已有 + `n` > 20,000 → 409 `LIBRARY_FULL`，**整体失败、什么都不写**。
   3. 定名称：源名称不重名就用它；重名用 `名称 (2)`、`(3)`…取最小的空号；加后缀超 100 字素时截短原名称再加后缀。
   4. 建私有收藏夹（简介照抄，简报「我定的」第 18 条）；新篇目写进 `library_item`（`collected_at = now`，无备注）；全部清单写进 `collection_item`。
4. 回 `{collectionId, name, addedToLibrary, alreadyInLibrary, skippedUnavailable}`，201。

- 新夹里的「加入时间」：`added_at = now - i 微秒`（`i` 是该条在源夹 `added_at` 倒序里的序号），新文献库条目的 `collected_at` 同理。这样新夹按加入时间排序时与源夹的顺序一致，而不是一堆相同时间戳再按 ID 乱序。（我定的）
- 不经 `SubjectGateway`：源夹里的对象都已登记（PAP-112 契约）。
- 复制品不记来源（简报 §10 第 3 条），所以源夹所有者注销后清理任务不会碰到复制品。

### 5.9 导出与复制引用的 key（导出编排）

门户在四种范围下要拿「这个范围里可导出的 key」，再调 catalog：

| 范围 | 接口 | 谁能调 |
|---|---|---|
| 整个文献库 / 未分类 | `GET /me/library/keys?view=all\|unfiled&sort=` | 本人 |
| 自己的一个收藏夹 | `GET /me/collections/{id}/keys?sort=` | 本人 |
| 别人的公开收藏夹 | `GET /collections/{id}/keys?sort=` | 任何对它可见的访客（5.4），含匿名 |
| 单篇 / 选中的 | 不需要 interaction：门户手里已有列表里的 `key` 与 `state` | — |

- 返回 `{keys: [...], skippedUnavailable: n}`。`keys` 的顺序 = 同一 `sort` 下列表的顺序（catalog 的编号样式按请求顺序编号，引用的序号要与列表一致）。
- 只收 `state == ACTIVE` 且 `key != null` 的；其余计入 `skippedUnavailable`。
- 可导出的超过 5,000 → 422 `EXPORT_TOO_LARGE`（门户事先已能从计数判断，这里兜底）。复制引用一次 100 条的上限由门户与 catalog 管，interaction 不管。

### 5.10 注销清理任务 `deletedUserCleanupJob`

- XXL-Job，执行器 `patra-interaction`，Cron `0 0 6 * * ?`（PAP-111 定的时刻），超时 600 秒，不重试；无参数。
- 按 `user_id` 升序游标，每批取 100 个「有数据的用户」：`library_item` 与 `collection` 两表 `user_id` 的并集（只有收藏夹没有条目的人也要扫到）。
- 每批调一次 identity 批量摘要（`viewerUserId = null`），**只**对 `accountStatus == DELETED` 的人：拿该用户的锁，在一个事务里删 `collection_item`、`library_item`、`collection` 三表中他的全部行。结果里被省略的 ID 不删，记 WARN。
- identity 不可用：跳过这一批、计数，继续下一批；结束时有失败批次则回写失败（调度中心标红），次日重跑。
- 日志与回写：扫描人数、删除人数、删除的条目 / 收藏夹 / 收藏夹条目行数、失败批次。
- 别人复制过的收藏夹是别人的行，不在被删用户名下，天然不受影响。

## 6. 接口

网关前缀 `/patra-interaction`，下表省略。所有 ID（`itemId`、`collectionId`、`userId`）在 JSON 里是字符串（`patra-tool-web` 的 Long → String），请求里字符串或数字都收。时间是 UTC ISO 8601。`type` 是 `SubjectType.slug()`，本版只有 `publication`；`key` 是门户 URL 里的 catalog 文献 ID。

**`itemId`** = 条目对应的 `subject_id`。条目在 catalog 整行消失后没有 `key`，已有条目的一切操作（备注、移除、放进 / 移出收藏夹）都按 `itemId`；只有「收藏」按 `(type, key)`。

### 6.1 我的文献库（需登录，未登录 401）

| 方法 | 路径 | 请求 | 响应 |
|---|---|---|---|
| GET | `/me/library` | — | 200 `LibraryOverview`：`{total, unfiled, collections: [Collection…]}`，收藏夹按 `updatedAt` 倒序、一次全部（≤ 200） |
| GET | `/me/library/items` | `view=all\|unfiled`（默认 all）、`sort=added\|published\|journal`（默认 added）、`page` ≥ 1（默认 1）、`size` 1 ~ 100（默认 50） | 200 `Page<LibraryItem>` |
| PUT | `/me/library/{type}/{key}` | — | 200 `{created, item: LibraryItem}`；已在库里 `created=false`，返回已有条目 |
| DELETE | `/me/library/items?itemIds=1,2,…` | 1 ~ 100 个 | 204；不在库里的忽略（幂等） |
| PUT | `/me/library/items/{itemId}/note` | `{note}`；null / 去空白后为空 = 删除 | 200 `LibraryItem`；不在库里 404 |
| POST | `/me/library/status` | `{type, keys: [≤ 100]}` | 200 `{items: [ItemStatus…]}`，按去重后的请求顺序、每个 key 一行 |
| GET | `/me/library/keys` | `view`、`sort` | 200 `{keys, skippedUnavailable}` |

`PUT /me/library/{type}/{key}` 的顺序（PAP-112 契约 + AC「重复收藏不报错」）：

1. `findIdsByKeys([key])` 查本地登记；命中且本人库里已有这一条 → 直接返回 `created=false`（catalog 软删、对象已失效也不报错）。catalog 整行消失时登记行没有来源 ID，按旧 key 查不到，走第 2 步回 404；重导后按新 ID 收藏，gateway 按 PMID 对回原 `subject_id`，回 `created=false`。
2. 否则 `SubjectGateway.resolveForInteraction(type, key, COLLECT)`（事务外；未登记会同步调 catalog；失效 / 不存在 404、catalog 不可用 503、key 不合法 422）。
3. 事务：锁本人 → `INSERT … ON CONFLICT DO NOTHING`；命中已有行 → `created=false`；新插入前库里已满 20,000 → 409 `LIBRARY_FULL`。

`LibraryItem`：

```json
{
  "itemId": "7391…", "type": "publication", "key": "123456" | null,
  "state": "ACTIVE" | "UNAVAILABLE", "title": "收藏时记下的标题" | null,
  "note": "…" | null, "collectedAt": "…", "addedAt": "…" | null,
  "collectionIds": ["…"]
}
```

- `title` 只在 `UNAVAILABLE` 时给（正常条目的展示数据由门户向 catalog 取卡片）。
- `addedAt` 只在收藏夹视图里有（加入该夹的时间）。
- `collectionIds`：本人的、这一条所在的全部收藏夹。

`ItemStatus`：`{key, collected, itemId | null, collectedAt | null, collectionIds: [], hasNote}`；没收藏的 `collected=false`、其余为空。没登记过、key 不合法的 key 都是 `collected=false`（不报错）。

`Page<T>`：`{page, pageSize, total, totalPages, items}`（与 catalog 门户分页同形）。页码超出时 `items` 为空。

### 6.2 我的收藏夹（需登录）

| 方法 | 路径 | 请求 | 响应 |
|---|---|---|---|
| POST | `/me/collections` | `{name, description}` | 201 `Collection`（私有） |
| PUT | `/me/collections/{id}` | `{name, description}`（整体替换） | 200 `Collection` |
| PUT | `/me/collections/{id}/visibility` | `{visibility: PRIVATE\|PUBLIC}` | 200 `Collection` |
| DELETE | `/me/collections/{id}` | — | 204；不存在或不是自己的也 204（幂等，不泄露别人的夹是否存在） |
| GET | `/me/collections/{id}/items` | `sort`、`page`、`size` | 200 `Page<LibraryItem>`（带 `addedAt`） |
| POST | `/me/collections/{id}/items` | `{itemIds: [1 ~ 100]}` | 200 `{added, alreadyIn, skippedFull, skippedNotInLibrary}` |
| DELETE | `/me/collections/{id}/items?itemIds=…` | 1 ~ 100 个 | 204；不在夹里的忽略 |
| GET | `/me/collections/{id}/keys` | `sort` | 200 `{keys, skippedUnavailable}` |

- 收藏夹不存在或不是自己的：除 DELETE 外一律 404。
- 放进收藏夹按请求顺序处理：不在库里的计入 `skippedNotInLibrary`；已在夹里的计入 `alreadyIn`；夹满后剩下的计入 `skippedFull`；`added > 0` 或移出了条目时更新 `item_count` 与 `updated_at`。
- `Collection`：`{collectionId, name, description | null, visibility, itemCount, createdAt, updatedAt}`。
- `updated_at` 的口径：名称、简介、可见性变化，以及条目进出（含从文献库移除连带移出）都算「更新」。门户显示的「更新于」就是它，侧栏与主页按它倒序。（我定的）

### 6.3 公开读与复制

| 方法 | 路径 | 登录 | 响应 |
|---|---|---|---|
| GET | `/collections/{id}` | 可选 | 200 `PublicCollection`；不可见 404 |
| GET | `/collections/{id}/items` | 可选 | 200 `Page<PublicItem>`；不可见 404 |
| GET | `/collections/{id}/keys` | 可选 | 200 `{keys, skippedUnavailable}`；不可见 404 |
| POST | `/collections/{id}/copies` | 必须 | 201 `CopyResult`；不可见 404 |
| GET | `/users/{userId}/collections` | 可选 | 200 `{collections: [PublicCollectionCard…]}`，按 `updatedAt` 倒序；不可见回空列表 |

```json
PublicCollection {
  "collectionId", "name", "description", "visibility", "itemCount", "updatedAt",
  "owner": { "userId", "username", "displayName", "avatarUrl", "avatarPalette", "deleted" },
  "viewerIsOwner": true | false
}
PublicItem { "itemId", "type", "key" | null, "state", "title" | null, "addedAt" }
PublicCollectionCard { "collectionId", "name", "description", "itemCount", "updatedAt" }
CopyResult { "collectionId", "name", "addedToLibrary", "alreadyInLibrary", "skippedUnavailable" }
```

- `PublicItem` 不带备注、不带所有者的其他收藏夹（备注是私人的，简报 §10 第 4 条）。访客自己的收藏状态走 `POST /me/library/status`。
- `owner` 直接来自 identity 摘要；所有者本人读自己的夹而 identity 不可用或省略他时为 `null`（门户降级显示）。`deleted = accountStatus == DELETED`（这时 `username` 等为 null）。实际上已注销用户的 `profileVisible` 恒为假、页面是 404，这个字段只在所有者本人或将来规则变化时兜底。
- `visibility` 在公开页响应里保留：所有者本人打开自己的私有夹时门户要显示「这个收藏夹是私有的」。

### 6.4 错误语义

错误码前缀 `INTERACTION`，`code` 形如 `INTERACTION-0409`；门户按 `traits[]` 里的原因码与 `errors[]` 里的字段码选文案，不解析 `message`。

| 情况 | HTTP | 原因码（`traits[]`）/ 字段码（`errors[]`） | 默认文案 |
|---|---|---|---|
| 未登录调需登录的接口 | 401 | （`patra-tool-security` 现有） | — |
| 收藏夹不存在 / 不可见 / 不是自己的 | 404 | `NOT_FOUND` | 没有找到这个收藏夹 |
| 条目不在文献库（改备注） | 404 | `NOT_FOUND` | 文献库里没有这一条 |
| 收藏的文献不存在、已下线（subject） | 404 | `NOT_FOUND` | 文献不存在或已被删除 |
| `key` 不是规范的十进制正整数（subject） | 422 | — | 对象标识格式不正确 |
| `type` 不是已知 slug | 422 | — | 不支持的对象类型 |
| 名称 / 简介 / 备注不合法 | 422 | `errors[]`：`name` / `description` / `note` × `REQUIRED`、`TOO_LONG`、`INVALID_CHARACTER` | 见 5.1 |
| 批量超 100、分页参数越界、`view` / `sort` / `visibility` 取值未知 | 422 | — | 参数不合法 |
| `page` / `size` 不是整数 | 400 | — | （Spring 参数绑定失败） |
| 重名 | 409 | `COLLECTION_NAME_TAKEN` + `errors[]` `name` / `TAKEN` | 已有同名收藏夹 |
| 文献库满 | 409 | `LIBRARY_FULL` | 文献库已满 20,000 篇 |
| 收藏夹个数满 | 409 | `COLLECTION_LIMIT_REACHED` | 最多 200 个收藏夹 |
| 设公开：账号状态异常 | 409 | `ACCOUNT_RESTRICTED` | 账号状态异常，暂时不能公开收藏夹 |
| 设公开：邮箱未验证 | 409 | `EMAIL_UNVERIFIED` | 验证邮箱后才能公开收藏夹 |
| 导出 key 超 5,000 | 422 | `EXPORT_TOO_LARGE` | 一次最多导出 5,000 篇 |
| catalog 不可用（首次收藏，subject） | 503 | — | 文献信息暂时无法获取，请稍后再试 |
| identity 不可用（设公开、别人读公开收藏夹、别人看主页列表；本人读自己的收藏夹除外） | 503 | `DEP_UNAVAILABLE` | 用户信息暂时无法获取，请稍后再试 |

- 原因码放在 domain 的 `CollectionErrorTrait` 枚举，与一个标准 trait 同放进异常（identity 的做法）；单夹满不报错，见 6.2。
- 「收藏夹已不可见」（复制前一刻被设为私有）就是 404：门户按简报 §4.6 刷新页面得到 404。
- identity 的 `RemoteCallException`（含 4xx）、连不上、没有实例，在 infra 适配器里一律转成领域异常 `UserDirectoryUnavailableException`（503），与 subject 调 catalog 的做法一致；4xx 是本服务的程序错误，靠适配器日志里的 `httpStatus`、`errorCode` 区分。

## 7. 模块落点

| 层 | 包（`dev.patra.interaction.…collection`） | 内容 |
|---|---|---|
| domain | `model.vo` | `CollectionId`、`CollectionName`、`CollectionDescription`、`ItemNote`（文本规则在构造里查） |
| | `model.enums` | `CollectionVisibility`、`LibrarySort`、`LibraryView` |
| | `model.read` | 查询结果的读模型（条目行、收藏夹行、状态行） |
| | `policy` | `CollectionLimits`（常量）、`CopyNaming`（序号命名）、`TextRules`（字素、字节、拒绝字符） |
| | `port.repository` | `LibraryRepository`（条目、收藏夹条目、收藏夹的写与锁）|
| | `port.read` | `LibraryReadPort`（列表、计数、状态、注销清理的用户游标） |
| | `port.user` | `UserDirectoryPort`：按 ID 批量取摘要（领域自己的 `UserCard`、`AccountState`，不暴露 identity DTO） |
| | `exception` | `CollectionNotFoundException`、`LibraryItemNotFoundException`、`LibraryFullException`、`CollectionLimitReachedException`、`CollectionNameTakenException`、`PublishNotAllowedException`（两种原因码）、`ExportTooLargeException`、`InvalidCollectionFieldsException`、`UserDirectoryUnavailableException`、`CollectionErrorTrait` |
| app | `usecase.…` | 每个写用例一个 Command + Handler（`CommandBus`）；事务用 `TransactionOperations` 显式包（gateway / identity 调用必须在事务外） |
| | `usecase.query` | `LibraryQueryService`、`PublicCollectionQueryService`（无接口）；内存排序在这里 |
| infra | `persistence` | `LibraryRepositoryAdapter`、`LibraryReadAdapter`（`JdbcClient`） |
| | `user` | `UserDirectoryAdapter`（调 `UserEndpoint`，`RemoteCallException` → 领域异常） |
| adapter | `rest` | `MyLibraryController`、`MyCollectionController`、`PublicCollectionController`，请求 / 响应 DTO |
| | `scheduler` | `DeletedUserCleanupScheduleJob`（`deletedUserCleanupJob`） |
| boot | `config` | `IdentityClientConfiguration`（`lb://patra-identity`，分组 `identity`：连接 1s、读 2s）；依赖加 `patra-tool-security`、`patra-identity-api` |

- `port.read` 是 collection 的公开包（PAP-111 的子域规则）。`LibraryReadPort` 签名只用 JDK 类型、subject 公开的类型与 `collection.model.read` 的类型，满足「非底座子域的读端口」约束；目前没有别的子域用它。
- collection 只 import subject 的公开包：`SubjectGateway`、`SubjectReadPort`、`SubjectView`、`SubjectId`、`SubjectSnapshot`、`SubjectType`、`SubjectCapability`、`SubjectState`。

## 8. 测试

| AC / 规则 | 测试 |
|---|---|
| 幂等：重复收藏不产生重复条目，重复取消不报错 | infra IT：同一 `(user, subject)` 插两次只有一行；boot 流程 IT：PUT 两次第二次 `created=false`、DELETE 两次都 204；对象失效后再 PUT 仍 `created=false` |
| 删收藏夹不删条目；删条目时同时移出所有收藏夹 | infra IT + boot 流程 IT：建两个夹各放同一条 → 删一个夹，条目还在、`unfiled` 不变；移除条目 → 两个夹的 `item_count` 都减 1、`collection_item` 无残留 |
| 邮箱未验证设公开被拒；主页不可见时访客 404，覆盖 identity 的各种状态 | boot 流程 IT（WireMock 扮 identity）：设公开 × {ACTIVE+已验证、ACTIVE+未验证、BANNED、PENDING_DELETION、DELETED、省略}；公开页 × {`profileVisible` 真 / 假、省略、identity 503} × {匿名、他人、本人}；私有夹对他人 404、对本人 200 |
| 注销清理只删 DELETED，不影响别人复制的收藏夹 | app 单测（批次、省略、503 跳过）+ boot 流程 IT：A 公开 → B 复制 → identity 回 A 为 DELETED → 跑任务 → A 的三表无行，B 的复制品与条目完整 |
| 触达上限返回明确错误 | domain / app 单测用小上限之外的计数桩：20,000、200 → 409 与原因码；单夹满回 `skippedFull`；复制时库放不下整体失败、什么都没写（infra IT 校验行数） |
| 「我的状态」一次 100 个 | boot 流程 IT：100 个 key（含未登记、不合法、已收藏、在两个夹里、有备注）一次请求返回 100 行；101 个 422 |
| 分区与原生 upsert（release spec D2） | `InteractionMigrationIT`：两张表各 16 个分区、主键含 `user_id`；版本号列表加 `20261024210000`；R6 不报用户名列 |
| 排序 | app 单测：三种排序的比较规则（降精度日期、空值在后、同值按加入时间）；infra IT：`added` 分页的 SQL 顺序与 offset |
| 并发 | infra IT：两个线程同时给同一个用户建第 200 个夹 / 收第 20,000 条，只有一个成功 |
| 复制 | boot 流程 IT：重名得到 `组会 (2)`、已在库里的不重复、失效的跳过计数、备注不复制、顺序与源一致 |
| 文本规则 | domain 单测：字素与字节上限、首尾空白、控制字符、换行只在简介 / 备注放行 |
| 子域边界 | `InteractionArchitectureTest` 现有规则（collection 只经 subject 公开包） |

`InteractionITBootstrap` 加一个 mock `UserEndpoint`；boot 流程 IT 照 `SubjectRegistryFlowIT` 用 WireMock + `SimpleDiscoveryClient` 扮 catalog 与 identity，用 `patra-tool-security` 测试夹具的 `TestSigningKey` 签身份断言。

## 9. 上线与运维

- 上线 runbook `docs/patra/runbooks/v0.13-library-go-live-runbook.md` 第 6 步之后补：xxl-job-admin 登记 `deletedUserCleanupJob`（执行器 `patra-interaction`，`0 0 6 * * ?`，超时 600、不重试）；手动触发一次，日志有「注销清理完成」与扫描人数、无报错。
- Nacos：不新增配置项（identity 分组超时在 jar 的 `application.yml`；断言公钥已在 `patra-common.yaml`）。
- 迁移只建新表，不碰 subject 的表。

## 10. 留给实现的小事

- 分区子表命名 `library_item_p00` … `p15`、`collection_item_p00` … `p15`；索引在父表上建，PG 自动下推。
- 文本块行尾空格（`RETURNING\s"""`）、Mockito 嵌套 stub、spotless 先于 check 等，按 `project_plan_pitfalls_found_by_preflight` 的清单处理。
- `Page` 的 `totalPages` 在 `total == 0` 时为 0。
