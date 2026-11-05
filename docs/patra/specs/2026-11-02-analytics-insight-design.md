# 计数、热读、看过还看了设计

> **Issue**：PAP-170（v0.18 Analytics，Milestone Data Ready）
> **日期**：2026-11-02
> **依据**：release spec [`v0.18-analytics.md`](../release-specs/v0.18-analytics.md) 2.1（浏览量、近期热读、热读算法、看过这篇的人还看了）、决策 G、I；[埋点方案](2026-11-01-analytics-tracking-plan-design.md) §6（人、真人会话、看过人数口径）；[入库设计](2026-11-01-analytics-ingest-design.md) §5（ClickHouse 表）；[阅读历史设计](2026-11-02-analytics-reading-history-design.md) §6.4（只给 ID、门户补卡片）；设计简报 `patra-portal/docs/design-briefs/v0.18/analytics.md` 附录 A 第 1～7 条、§3.1～3.6、§3.11
> **前置**：PAP-167、168、169 都在 `feat/v0.18-analytics-api`
> **状态**：定稿（2026-11-02，用户确认共现窗口 14 天）

---

## 1. 要做什么

在 `patra-analytics` 里加 `insight` 子域，给门户三类数据：

1. **计数**：按一组文献 ID 一次取「N 人看过」「N 人读过全文」，顺带给出「在不在热读榜」。查 ClickHouse 汇总表，套 Redis 60 秒缓存。
2. **热读**：每小时一个任务算出热读榜，写进 PG 快照表；首页「热读」标签读快照，上榜的不够 14 篇时用最新收录补位。
3. **看过这篇的人还看了**：每天一个任务算出每篇文献最常被一起读的 20 篇，写进 PG 快照表；文献页读快照，不足 6 篇时向 catalog 要同主要主题词、同期刊的文献补位。

另外：catalog 加两个内部接口、给一个已有内部接口加一个字段（§8）；XXL-Job 登记两个任务（§10）；runbook 加 8.4 节。

**不做**：卡片内容（标题、刊名、作者）不由 analytics 给，门户照文献库的做法按 ID 向 catalog 批量取（§9.4）；检索按热度排序（release spec 第 3 节 D）；个性化推荐（第 3 节 C）；部署到 mini、在 mini 上登记任务（按 runbook，另行确认）。

## 2. 先讲清几个概念

### 2.1 为什么计数现查、排名用快照

两类数据对「新鲜」的要求不一样（release spec 决策 G）：

| | 计数 | 热读、看过还看了 |
|---|---|---|
| 要多新 | B 读完 1 分钟内 +1 | 每小时 / 每天变一次就够 |
| 算一次要多大代价 | 100 篇文献按主键取，毫秒级 | 扫近 14 天的全部阅读、排序、两两配对，秒级 |
| 谁来扛在线请求 | ClickHouse 按主键点查 + Redis 挡住重复请求 | PG 快照表，按主键读几十行 |

ClickHouse 擅长「一次扫几千万行算一个结果」，不擅长「每秒几百个小查询」：每个查询都要起一套并行执行，内存与线程开销是按查询算的。所以计数前面放一层 Redis，同一篇 60 秒内只查一次；排名干脆算好放进 PG，页面请求根本不碰 ClickHouse。

### 2.2 缓存：先查 Redis，没有再查 ClickHouse（cache-aside）

一屏 14 篇的计数请求：先一次 `MGET` 取 14 个键 → 命中的直接用 → 没命中的几篇**合成一条** ClickHouse 查询 → 结果写回 Redis、60 秒过期。没人看过的文献也写一个 `0`（否则冷门文献每次都打到 ClickHouse，业界叫「缓存穿透」）。60 秒就是「1 分钟内更新」的来源：最坏情况是刚缓存完 B 就读了，要等这 60 秒过去。

### 2.3 时间衰减：越近的阅读分量越重

Hacker News、Reddit 的热榜都是「分数 × 随时间变小的系数」。本版用半衰期 3 天：今天的一次阅读算 1，3 天前的算 0.5，6 天前的算 0.25。读了全文的那次再乘 3。

例：文献 A 今天被 3 个人看了摘要，得 3 分；文献 B 6 天前被 4 个人读了全文，得 4 × 3 × 0.25 = 3 分。两篇一样热；明天 A 若没人再看，A 变成 3 × 0.79 = 2.38，B 变成 2.38，仍然一样——同一时刻所有阅读一起变老，名次只因新的阅读而变。所以「天」按整天算就够了，不需要精确到小时。

### 2.4 余弦相似度：压低热门文献的天然优势

「看过 X 的人还看了 Y」最朴素的做法是数 X、Y 被多少人一起读过。问题是热门文献和谁都一起读过：一篇 1,000 人读的综述，跟任何一篇冷门文献都能凑出 3 个共同读者，结果每篇文献的推荐里都是它。

余弦相似度把「一起读的人数」除以两篇各自读者数的几何平均：

```
相似度(X, Y) = 一起读过 X 和 Y 的人数 / √(读过 X 的人数 × 读过 Y 的人数)
```

例：X 有 10 个读者。Y 也是 10 个读者、其中 5 个读过 X → 5 / √(10 × 10) = 0.5。综述 Z 有 1,000 个读者、其中 8 个读过 X → 8 / √(10 × 1000) = 0.08。Y 排在 Z 前面，尽管 Z 的共同读者更多。这是 Amazon 2003 年 item-to-item 推荐的做法。

## 3. 子域与分层

新增子域 `insight`，只依赖底座 `kernel`（事件名等）。各层的东西：

| 东西 | 位置 |
|---|---|
| 规则（热读的窗口、权重、半衰期、门槛；共现的窗口、每组上限、门槛；页面拼装） | `domain.insight.policy`、`domain.insight.model.vo` |
| ClickHouse：计数 | 端口 `domain.insight.port.stats.ReaderStatsPort` → `infra.insight.stats.ClickHouseReaderStatsAdapter`，Redis 缓存装饰器 `infra.insight.stats.CachingReaderStatsDecorator`（同 LookupPort 的装饰器模式） |
| ClickHouse：热读候选、共现配对 | 端口 `domain.insight.port.signal.ReadingSignalPort` → `infra.insight.signal.ClickHouseReadingSignalAdapter` |
| PG 快照：整体替换 | `domain.insight.port.repository.HotReadingSnapshotRepository`、`CoReadingSnapshotRepository` → `infra.insight.persistence.*RepositoryAdapter` |
| PG 快照：页面读 | `domain.insight.port.read.HotReadingReadPort`、`CoReadingReadPort` → `infra.insight.persistence.*ReadAdapter` |
| catalog：文献状态、最新收录、同主题邻居 | 端口 `domain.insight.port.catalog.PublicationCatalogPort` → `infra.insight.catalog.PublicationCatalogAdapter`（HTTP Interface，§8）；邻居结果的缓存装饰器 `infra.insight.catalog.CachingPublicationNeighborsDecorator` |
| 两个任务的用例 | `app.insight.usecase.hot.RefreshHotReadingHandler`、`app.insight.usecase.coread.RefreshCoReadingHandler`；快照写入用 `TransactionOperations`（命令 Handler 不标 `@Transactional`，同 history） |
| 三个查询 | `app.insight.usecase.query.ReaderStatsQueryService`、`HotReadingQueryService`、`AlsoReadQueryService` |
| 接口 | `adapter.insight.rest.ArticleInsightController`、`HotReadingController` |
| 任务入口 | `adapter.insight.scheduler.HotReadingScheduleJob`、`CoReadingScheduleJob` |

- ClickHouse 客户端直接注入库里的 `com.clickhouse.client.api.Client` Bean（PAP-168 在 `infra.ingest.config` 装配），不引用 ingest 包里的任何类型，所以不产生跨子域依赖。
- 架构测试 `SUBDOMAINS` 加 `insight`。
- 「今天」由注入的 `Clock` 按 `Asia/Shanghai` 算出，作为参数传给 ClickHouse 查询（测试用固定时钟）。

### 3.1 合成事件开关

`patra.analytics.include-synthetic`（布尔，默认 `false`）。关着时三类数字都只看 `is_synthetic = 0`；开着时不过滤。Redis 键里带上这个开关的取值（`real` / `all`），开关切换后不会读到另一种口径的旧缓存。

- **mini 上一直关着**：`patra-infra/nacos/dev/` 就是 mini 的配置，mini 的门户对公网开放，打开就等于对外放出合成数字，release spec D6（B、C、E 读的那篇上热读）也会被合成热门挤掉。Nacos 里不写这个键，用默认值。
- **本机开发时打开**（启动参数或本机的 `application-local.yml`），用来看热读和推荐被填满的样子（决策 I）。本机连的是 mini 上的 ClickHouse，所以打开时两个任务面对的是亿级合成事件：任务要在 patra 账号每条查询 1 GiB 内存的限制里跑完（§6.2 的分片），这在 PAP-171 灌完数据后实测一次，结果写进压测报告；它不是本 Issue 能在本地验证的验收项。
- 开关关着时，ClickHouse 查询照样扫过合成事件（`events` 的排序键里没有 `is_synthetic`），只是在 `WHERE` 里过滤掉，内存只随真实数据涨。

## 4. 计数

### 4.1 口径

照埋点方案 §6.8：两个数都**按人长期去重**、只数真人会话（埋点方案 §6.5：会话里有浏览器事件、且没有任何一条被标为爬虫），排除合成事件（§3.1）。

### 4.2 查询

入库设计 §5.4 已给出骨架，在 `article_session_reads` 上按文献主键取，`session_quality` 只查这些文献涉及的会话：

```sql
SELECT article_id,
       uniqExactIf(person_id, viewed = 1)   AS viewers,
       uniqExactIf(person_id, fulltext = 1) AS fulltext_readers
FROM (
    SELECT article_id, session_id, person_id, max(viewed) AS viewed, max(fulltext) AS fulltext
    FROM article_session_reads
    WHERE article_id IN {ids:Array(UInt64)} [AND is_synthetic = 0]
    GROUP BY article_id, session_id, person_id
)
WHERE session_id IN (
    SELECT session_id FROM session_quality
    WHERE session_id IN (SELECT session_id FROM article_session_reads WHERE article_id IN {ids:Array(UInt64)})
    GROUP BY session_id
    HAVING max(has_client) = 1 AND max(has_bot) = 0)
GROUP BY article_id
```

- 一次最多 100 篇；结果里没有的文献就是 0。
- 设查询级 `max_execution_time = 3`（秒）：门户对计数的超时很短，查不出来不如快速失败。
- 汇总表还没合并时同一个键有多行，先在子查询里 `max` 合并（`SimpleAggregateFunction` 的读法）。

### 4.3 缓存

- 键 `analytics:insight:stats:{real|all}:{articleId}`，值 `"{viewers}:{fulltextReaders}"`，TTL 60 秒；0 也缓存。
- 一次请求：一个 `MGET`，没命中的合成一条 ClickHouse 查询，结果用一次管道 `SET … EX 60` 写回。
- **Redis 不可用**（超时 500 ms，沿用服务配置）：跳过缓存直接查 ClickHouse，记指标，不报错（同 `RedisDeviceRateAdapter` 的降级）。
- **ClickHouse 不可用**：整个计数接口返回 503；门户按简报 §3.11 少显示「看过」那几项。

### 4.4 「在不在热读榜」

计数接口顺带给 `hot`（简报附录 A 第 3 条：检索结果一屏一个请求）。从 PG 热读快照查「这批 ID 里哪些是上榜的」（补位的不算上榜），不缓存（PG 按主键查 100 个 ID，毫秒级）。

## 5. 热读

### 5.1 规则（release spec 2.1，补上没写明的部分）

「今天」= 北京时间的今天，记为 T。

| 项 | 规则 |
|---|---|
| 取哪些阅读 | 查看类事件（`article_viewed`、`fulltext_read`、`pdf_downloaded`、`article_collected`），`article_id != 0`，事件的北京时间日期在 `[T-13, T]`（近 14 天，含今天），只要真人会话里的、排除合成（§3.1） |
| 去重 | 每人每天每篇一行：按（文献，人，天）合并；这一天这个人对这篇有过 `fulltext_read` 或 `pdf_downloaded`，这一行就算「读了全文」 |
| 一行的分量 | `(读了全文 ? 3 : 1) × 0.5 ^ ((T − 天) / 3)` |
| 文献的分数 | 这篇所有行的分量之和 |
| 上榜门槛 | `[T-6, T]`（近 7 天）里读过这篇的不同人数 ≥ 3 |
| 名次 | 分数从高到低；分数相同时近 7 天人数多的在前，再相同按文献 ID 小的在前 |
| 榜单长度 | 前 100 名 |
| 排除 | 已软删、已撤稿、catalog 里已经没有的文献（简报附录 A 第 5 条），由任务向 catalog 核对（§8.1） |

### 5.2 为什么热读从明细表 `events` 算，而不是从汇总表

`article_session_reads` 的排序键是 `(article_id, session_id, …)`，日期不在前面，而且这张表长期保留、不断变大：按「近 14 天」过滤要扫全表。`events` 按天分区，14 天正好是 14 个分区，扫的量固定，并且有每条事件的时间（共现取「当天最先读的 20 篇」要用，§6.1）。代价是「看过的人」这个口径在物化视图（计数）和任务（热读、共现）里各实现一次，release spec 决策 G 已经说了这个代价：测试要覆盖两边一致（§12）。

`session_quality` 按 `first_day >= T-14` 先过滤再 `GROUP BY`（入库设计 §5.5 的提示；patra 账号每条查询上限 1 GiB 内存）。

### 5.3 每小时的任务

XXL-Job `hotReadingJob`，每小时第 5 分钟（Cron `0 5 * * * ?`）：

1. 在 ClickHouse 按 §5.1 算出上榜的候选，取前 150 名（给撤稿、软删留余量）。
2. 向 catalog 核对这些文献（§8.1），去掉不合格的，留前 100 名。
3. 补位：上榜的少于 14 篇时，向 catalog 要最新收录的 28 篇（§8.2），去掉已经上榜的，按顺序补到合计 14 篇。上榜的 ≥ 14 篇时不补。
4. 一个事务里整表替换快照（§7），并记下这次的计算时间。

任何一步失败（ClickHouse、catalog、PG 不可用），整个任务失败、调度中心标红，**旧快照原样保留**，下一个小时再算。

### 5.4 为什么补位在任务里做，而不在接口里做

接口只读 PG，不依赖 catalog：catalog 出问题时首页「热读」照常有内容（只是补位停在上一次成功的样子）。补位最多晚一小时，对「最新收录」来说可以接受。

## 6. 看过这篇的人还看了

### 6.1 规则（release spec 2.1，补上没写明的部分）

| 项 | 规则 |
|---|---|
| 取哪些阅读 | 同热读：查看类事件、近 14 天 `[T-13, T]`、真人会话、排除合成。窗口 14 天（release spec 没写，用户 11/02 定；入库设计 §5.5 给 PAP-170 的提示也是「热读与共现只看近 14 天」） |
| 一组 | 同一个人同一天（北京时间）读过的文献 |
| 每组最多 20 篇 | 超过 20 篇时取当天**最先读**的 20 篇（按这个人当天对每篇的第一条事件时间），其余不参与这一组 |
| 人数 | 读过 X 的人数 `n(X)`：在上面截断后的各组里出现过 X 的不同人数；一起读过的人数 `co(X, Y)`：至少有一组同时含 X 和 Y 的不同人数 |
| 门槛 | `co(X, Y) ≥ 3` |
| 相似度 | `co(X, Y) / √(n(X) × n(Y))` |
| 每篇留多少 | 每篇 X 按相似度从高到低留前 20 名；相同时一起读的人数多的在前，再相同按文献 ID 小的在前 |
| 排除 | 软删、撤稿、catalog 里没有的（任务向 catalog 核对，§8.1） |

### 6.2 每天的任务

XXL-Job `coReadingJob`，每天北京时间 04:30（Cron `0 30 4 * * ?`，错开 06:20 注销清理、06:40 历史清理）：

1. 在 ClickHouse 先算出每篇的读者数 `n(X)`，再**按 X 分片**算配对：`cityHash64(X) % 8 = k` 的 8 片各跑一次查询（片数可配置），每片只展开 X 落在这一片的有序配对（X, Y），满足门槛的算出相似度，每篇 X 取前 25 名（给核对留余量）。分片是为了合成数据灌满、开关打开时每条查询也守在 1 GiB 内存里（§3.1）；真实数据下每片只有几行。两个方向都有：Y 在 X 的列表里，X 也在 Y 的列表里（各自按各自的分母排）。
2. 把结果里出现过的全部文献 ID 去重，按 100 个一批向 catalog 核对（合成数据规模下是几千次内部调用，每天一次可以接受），去掉不合格的 X 与 Y，每篇留前 20 名。
3. 一个事务里整表替换快照（§7）。

失败处理同热读：旧快照保留，次日重算。

### 6.3 请求时：拼出最多 6 篇

`GET /articles/{id}/also-read`（§9.3）：

1. 从 PG 快照读 X 的前 6 篇，来源 `CO_READ`。
2. 不足 6 篇时，向 catalog 要 X 的同主题邻居（§8.3），去掉已经在第 1 步里的，按顺序补到 6 篇。邻居的来源是 `SAME_MESH`（带命中的主要主题词原文）或 `SAME_VENUE`（带期刊名）。
3. 邻居结果在 Redis 缓存 6 小时（键 `analytics:insight:neighbors:{articleId}`，只按文献 ID：每次固定向 catalog 要 11 篇，第 1 步最多 5 篇，去重后一定还够补）。主题词、期刊几乎不变，6 小时足够新。
4. **catalog 不可用**：只返回第 1 步的 `CO_READ` 部分，不缓存这次的空结果，记指标；不返回错误（文献页这块按实际篇数展示，简报 §3.6 的组合规则照常适用）。
5. X 在 catalog 里不存在：邻居为空，第 1 步也通常为空，返回空列表。

### 6.4 为什么补位在请求时做，而不在任务里做

共现快照只覆盖「至少有一对一起读过 ≥ 3 人」的文献，绝大多数文献（尤其是新收录的）没有共现，却都需要「同主题文献」。给 catalog 里每一篇都预先算邻居，量是全部文献 × 6；请求时算加缓存，只算有人打开的那些。

## 7. 快照表（PG）

迁移脚本 `patra-analytics-infra/src/main/resources/db/migration/insight/V20261102120000__create_insight_schema.sql`：

```sql
-- insight 子域：热读与共现的快照（PAP-170）。只存 catalog 的文献 ID，不建跨库外键
CREATE SCHEMA insight;

-- 热读快照：上榜的（rank 1..100）在前，补位的接在后面；整表替换
CREATE TABLE insight.hot_reading (
    position     SMALLINT          PRIMARY KEY,          -- 1 起，页面按它排
    article_id   BIGINT            NOT NULL UNIQUE,
    ranked       BOOLEAN           NOT NULL,             -- false = 最新收录补位
    score        DOUBLE PRECISION,                       -- 补位为 NULL
    readers_7d   INTEGER                                 -- 补位为 NULL
);

-- 共现快照：每篇最多 20 行；整表替换
CREATE TABLE insight.co_reading (
    article_id          BIGINT            NOT NULL,
    position            SMALLINT          NOT NULL,      -- 1..20
    related_article_id  BIGINT            NOT NULL,
    similarity          DOUBLE PRECISION  NOT NULL,
    co_readers          INTEGER           NOT NULL,      -- 只供排查，不对外
    PRIMARY KEY (article_id, position)
);

-- 每种快照最近一次成功计算的时间
CREATE TABLE insight.snapshot_run (
    kind         VARCHAR(16)  PRIMARY KEY,               -- HOT_READING / CO_READING
    computed_at  TIMESTAMPTZ  NOT NULL,
    row_count    INTEGER      NOT NULL
);
```

**写法：一个事务里 `DELETE` 全表 + 批量 `INSERT` + 更新 `snapshot_run`**（Issue 要求二选一：整表替换或版本切换）。

- PG 的多版本并发控制（MVCC）保证：事务提交前，页面读到的一直是旧快照；提交后一下子全换成新的，不会读到「删了一半」的样子。
- 用 `DELETE` 不用 `TRUNCATE`：`TRUNCATE` 要拿排他锁，提交前所有读这张表的请求都会被卡住。
- 共现快照在合成数据灌满时约百万行，每天删掉旧的百万行留下的死行由 autovacuum 回收；真实数据下只有几行。
- 不用版本切换（写新版本 → 切指针 → 删旧版本）：它多出来的好处是可以退回上一版，本版用不上；读的时候还要多关联一次指针表。

批量插入用 `JdbcClient`（同 history 的写法）：每 5,000 行一条 `INSERT … SELECT * FROM unnest(:a::bigint[], …)`，几百万行也只有几百条语句，不用改连接串。

## 8. catalog 的契约（analytics 调 catalog）

在 `patra-catalog-api` 的 `PublicationEndpoint`（`/_internal/publications`）上改一处；两个新接口放在同前缀的新契约 `PublicationNeighborEndpoint`（`PublicationEndpoint` 类上声明了 JSON 请求体，GET 放进去会要求带请求体）。analytics 的 `application.yml` 加 `patra.http-client.http-interface.groups.catalog`（`lb://patra-catalog`，连接超时 1 秒、读超时 2 秒），`patra-analytics-infra` 依赖 `patra-catalog-api`。

「撤稿」的判定沿用 catalog 详情页的：PubMed 发表类型含 `D016441`，或全文不可用原因为 `RETRACTED`（`PublicationDetailReadAdapter`）。

### 8.1 核对：`POST /_internal/publications/resolve/by-ids` 加 `retracted`

已有接口，每个请求键一行。返回项加一个字段 `retracted`（布尔；不存在时为 `false`）。任务按「`exists` 且 `state = ACTIVE` 且不撤稿」判定合格。interaction 现在也调这个接口，加字段不影响它。

### 8.2 最新收录：`GET /_internal/publications/latest?limit={n}`

```json
{ "ids": ["7391204485120001", "7391204485120000"] }
```

- `limit` 1～100。按收录时间新的在前（文献主键是雪花 ID，主键倒序就是首次入库时间倒序），去掉软删、撤稿。
- 只给热读补位用。与首页「最近更新」标签（按 `last_synced_at`）不是一个排序：简报写的是「最新收录」「按收录时间新的在前」。

### 8.3 同主题邻居：`GET /_internal/publications/{id}/subject-neighbors?limit={n}`

```json
{
  "items": [
    { "id": "7391…", "reason": "SAME_MESH", "meshDescriptorUi": "D012772", "meshTerm": "Shock, Septic", "venueName": null },
    { "id": "7390…", "reason": "SAME_VENUE", "meshDescriptorUi": null, "meshTerm": null, "venueName": "Chest" }
  ]
}
```

- `limit` 1～20。
- **主要主题词**按 PubMed `[majr]` 的含义：主题词本身标了 major（`cat_publication_mesh_heading.is_major_topic`），**或**它的任一副主题词标了 major（`cat_publication_mesh_qualifier.is_major_topic`，如 `Sepsis/drug therapy*`，这时主题词本身是 N）。只看主题词一级会让很多文献找不到主要主题词。
- **先同主要主题词**：取 X 的主要主题词，对每个主题词找「也把它当主要主题词」的其他文献，各自按收录时间新的在前；多个主题词之间**轮流取**（第 1 个词的第 1 篇、第 2 个词的第 1 篇……再第 1 个词的第 2 篇），免得一个词占满（PubMed 的主题词顺序是字母序，不代表重要程度）。
- **再同期刊**：同 `venue_id` 的其他文献，收录时间新的在前。
- 都去掉 X 本身、软删、撤稿、前面已经出现过的；凑够 `limit` 就停。`meshTerm` 是主题词的英文原文（`cat_mesh_descriptor.name`），`venueName` 是期刊全称。
- X 不存在或已软删：`items` 为空。
- 性能：两步都要靠索引按「词 / 期刊 + 主键倒序」直接取前几行，不排序整组（大主题词有上十万篇）。catalog 加迁移 `V14__add_subject_neighbor_indexes.sql`：`cat_publication_mesh_heading (descriptor_ui, publication_id DESC)`（不做部分索引：major 可能标在副主题词上，按索引倒序扫、逐行判断是否 major，一般扫几十行就够）、`cat_publication (venue_id, id DESC) WHERE deleted_at IS NULL`。

### 8.4 为什么让 catalog 算邻居

主题词、期刊、撤稿都是 catalog 的数据，analytics 冗余一份就要跟着同步。analytics 只负责「什么时候补、补几篇、排在哪」这条产品规则。

## 9. 对外接口

都在网关前缀 `/patra-analytics` 下，**不要登录**（公开数字，登录与否结果一样）。ID 一律序列化成字符串。校验失败一律 422，服务不可用 503（错误体是项目统一的 ProblemDetail，错误码前缀 `ANALYTICS`）。

### 9.1 计数：`POST /articles/stats`

请求：

```json
{ "articleIds": ["39871245", "39871246"] }
```

响应 200：

```json
{
  "items": [
    { "articleId": "39871245", "viewerCount": 1284, "fulltextReaderCount": 312, "hot": true },
    { "articleId": "39871246", "viewerCount": 0, "fulltextReaderCount": 0, "hot": false }
  ]
}
```

- `articleIds` 1～100 个正整数（字符串）；重复的去掉，每个请求 ID 一行，按去重后的请求顺序。没人看过、不存在的文献都是 0 / 0 / `false`。
- `viewerCount`：看过的人数；`fulltextReaderCount`：读过全文的人数（§4.1）；`hot`：在当前热读榜前 100 名里（补位的不算）。
- 422：`articleIds` 缺失、为空、超过 100 个、含 0 或负数。400：含不是数字的元素（如 `"abc"`，请求体解析不了，同 catalog 内部接口的约定）。
- 503：ClickHouse 或 PG 不可用。门户按简报 §3.11 少显示「看过」「读过全文」与「近期热读」。
- 数字 60 秒内可能是旧的（§4.3）；本人刚看过不会立刻 +1（简报 §3.1 已按这个设计）。

### 9.2 热读：`GET /hot-reads`

响应 200：

```json
{
  "computedAt": "2026-11-02T08:05:03.120Z",
  "rankedTotal": 4,
  "items": [
    { "articleId": "39871245", "source": "RANKED", "rank": 1 },
    { "articleId": "39871301", "source": "RANKED", "rank": 2 },
    { "articleId": "39871288", "source": "RANKED", "rank": 3 },
    { "articleId": "39871250", "source": "RANKED", "rank": 4 },
    { "articleId": "39880012", "source": "LATEST", "rank": null }
  ]
}
```

- 一页固定最多 14 篇（首页一屏，同现在的文献流），无参数、不翻页。上榜的按名次在前，补位的（`source = LATEST`，`rank` 为 `null`）接在后面。
- `rankedTotal`：榜上一共几篇（0～100），简报 §3.5 的说明句「只有 N 篇」用它；≥ 14 时不会有补位。
- `computedAt`：快照的计算时间（UTC）。任务从没成功跑过时：`computedAt = null`、`rankedTotal = 0`、`items = []`，门户按空状态处理。
- 卡片内容由门户拿 `items` 的 ID 调 catalog `GET /patra-catalog/portal/publications/batch` 取（§9.4）。

### 9.3 看过还看了：`GET /articles/{articleId}/also-read`

响应 200：

```json
{
  "items": [
    { "articleId": "39871245", "source": "CO_READ", "meshTerm": null, "venueName": null },
    { "articleId": "39875510", "source": "SAME_MESH", "meshTerm": "Shock, Septic", "venueName": null },
    { "articleId": "39876620", "source": "SAME_VENUE", "meshTerm": null, "venueName": "Chest" }
  ]
}
```

- 最多 6 篇，顺序就是展示顺序：`CO_READ` 在前（按相似度），`SAME_MESH` 再 `SAME_VENUE`。不含 X 本身、软删、撤稿的文献。
- 简报 §3.6 的标题与分组按 `source` 选：全是 `CO_READ` → 一组「看过这篇的人还看了」；有 `CO_READ` 也有补位 → 两组；没有 `CO_READ` → 一组「同主题文献」；`items` 为空 → 整块不出现。
- 不返回「几个人一起读过」（简报 §0：门槛只有 3 人，显示人数等于半公开别人的阅读）。
- 422：`articleId` 不是正整数。503：PG 不可用。catalog 不可用时不报错，只是没有补位（§6.3）。

### 9.4 卡片内容为什么由门户取

同阅读历史 spec §6.4：门户已经有按 ID 批量取卡片的现成做法（`fetchPublicationCards`，≤ 100 个、按请求顺序、软删的不返回），卡片字段与首页文献流完全一致，能直接喂给 `PaperCard`；analytics 不冗余文献标题，也不用跟着 catalog 的卡片字段改。页面的流程是「先调 analytics 拿到有序 ID → 一次批量取卡片 → 一起渲染」，6 篇或 14 篇是一次拿全的，不会先画一组再插一组（简报附录 A 第 6 条担心的情况）。快照算出到页面请求之间被软删的文献，批量接口不返回，门户直接跳过那一篇（极少见，接受少一篇）。

## 10. 定时任务

| handler | 执行器 | Cron | 做什么 |
|---|---|---|---|
| `hotReadingJob` | `patra-analytics` | `0 5 * * * ?`（每小时第 5 分钟） | §5.3 |
| `coReadingJob` | `patra-analytics` | `0 30 4 * * ?`（每天 04:30） | §6.2 |

照 `ReadingHistoryRetentionScheduleJob` 的写法：无任务参数；成功时回写「上榜 N 篇、补位 M 篇」/「N 篇文献、M 条推荐」；失败回写失败（调度中心标红）。两个任务都可以重复跑（整表替换是幂等的）。

## 11. 指标

| 指标 | 说明 |
|---|---|
| `analytics.insight.job`（计时，标签 `job`、`outcome`） | 两个任务每次的耗时与成败 |
| `analytics.insight.snapshot.rows`（gauge，标签 `kind`） | 最近一次写入的快照行数 |
| `analytics.insight.stats.cache`（计数，标签 `result` = `hit` / `miss` / `error`） | 计数缓存命中率；`error` 是 Redis 不可用时的降级 |
| `analytics.insight.neighbors`（计数，标签 `outcome` = `hit` / `fetched` / `unavailable`） | 补位邻居的缓存与 catalog 调用情况 |

## 12. 测试

| 层 | 测什么 |
|---|---|
| 单元（domain） | 热读页拼装：上榜 ≥ 14 不补位；上榜 4 篇补 10 篇且去掉已上榜的；补位来源不够时有几篇给几篇。看过还看了拼装：`CO_READ` 6 篇不补；3 篇补 3 篇且去重；补位顺序保持 catalog 给的顺序 |
| 单元（app） | 任务：catalog 核对去掉不合格的后取前 100 / 前 20；ClickHouse、catalog、PG 任一失败时不写快照；计数缓存：全命中不查 ClickHouse、部分命中只查没命中的、0 也写缓存、Redis 出错时直接查 ClickHouse；看过还看了：catalog 不可用时只返回 `CO_READ` 且不缓存 |
| ClickHouse 集成（Testcontainers） | **热读**：三个不同的人（真人会话）各读一次 X → X 上榜；只有两人 → 不上榜；读全文的分量是 3 倍、6 天前的分量是四分之一（构造两篇算出相等分数）；同一个人同一天读 3 次只算一行；14 天外的阅读不算。**共现**：三人同一天都读了 X 与 Y → Y 在 X 的列表里、X 在 Y 的列表里；两人 → 不出现；三人在不同天分别读 X、Y → 不出现；一组 25 篇时只取当天最先读的 20 篇；热门文献 Z 的相似度低于共同读者更少的 Y（§2.4 的例子）。**计数**：同一个人反复看只算 1；读全文计入两个数。**排除**：爬虫会话（含一条爬虫事件）、只有服务端事件的会话、合成事件，对三类数字都没有影响；打开 `include-synthetic` 后合成事件计入。**一致性**（决策 G）：同一批事件，热读算出的近 7 天人数与计数接口的看过人数相同 |
| PG 集成（Testcontainers） | 快照整表替换：替换后只剩新行；替换事务未提交时另一个连接读到的是旧快照；`hot` 只认 `ranked = true`；读前 14 篇的顺序 |
| catalog 集成 | `resolve/by-ids` 的 `retracted`（发表类型 `D016441`、全文原因 `RETRACTED` 两种）；`latest` 去掉软删与撤稿、按主键倒序；`subject-neighbors`：多个主要主题词轮流取、只认对方也是主要主题词、major 标在副主题词上也算（两边都测）、再同期刊、去掉自己 / 软删 / 撤稿 / 重复，X 不存在时为空 |
| boot 集成（Testcontainers：ClickHouse、PG、Redis、Kafka；catalog 用 `@MockitoBean` 替身） | 三个接口的 JSON 形状、422、503；**事件经采集端点 → Kafka → 入库 → 计数接口在 60 秒内反映**（缓存为冷时立即可见；另断言缓存的 TTL ≤ 60 秒）；两个任务跑一次后接口读到快照 |
| 架构 | `insight` 加进子域清单，规则通过 |

## 13. 上线

写进 v0.18 runbook 新的 8.4 节：

- catalog 先于 analytics 部署（新的内部接口与索引；索引迁移在 catalog 启动时执行，`cat_publication_mesh_heading` 很大，记录建索引用了多久）。
- XXL-Job 在执行器 `patra-analytics` 下登记 `hotReadingJob`、`coReadingJob`（Cron 见 §10），各手动执行一次。
- 验收：`insight.snapshot_run` 两行都有；`GET /patra-analytics/hot-reads` 有 14 篇（真实数据下几乎全是补位）；B、C、E 三人读同一篇后手动跑一次热读任务，它出现在榜上（release spec D6）。

## 14. 决定

1. **看过还看了的统计窗口取近 14 天**（与热读一致；release spec 没写；用户 11/02 定），见 §6.1。
2. **每组超过 20 篇时取当天最先读的 20 篇**，不是整组丢掉。
3. **热读与共现从明细表 `events` 算**，计数从汇总表算；两边口径用测试守住一致（§5.2）。
4. **快照整表替换：一个事务里 DELETE + INSERT**，不用版本切换（§7）。任务失败时旧快照保留。
5. **热读的补位在任务里做**（接口只读 PG），**看过还看了的补位在请求时做**（向 catalog 要、缓存 6 小时）（§5.4、§6.4）。
6. **「最新收录」按首次入库时间**（主键倒序），不是首页「最近更新」的同步时间（§8.2）。
7. **同主题邻居在多个主要主题词之间轮流取**，且要求对方也把这个词当主要主题词（§8.3）。
8. **analytics 只给 ID 和来源**，卡片由门户向 catalog 批量取（§9.4）。
9. **`hot` 并进计数接口**，不单独开接口（§4.4）。
10. **ClickHouse 不可用时计数接口 503**；Redis 不可用时绕过缓存；catalog 不可用时看过还看了只少补位（§4.3、§6.3）。
11. **本 Issue 改 catalog**：`resolve/by-ids` 加 `retracted`、两个新内部接口、一个索引迁移（§8）。
12. **「主要主题词」按 PubMed `[majr]`**：主题词或它的副主题词标了 major 都算（§8.3）。
13. **`include-synthetic` 在 mini 上一直关着**，只在本机开发打开；开着时的任务规模（亿级合成事件、1 GiB 内存）靠共现查询按文献分 8 片保证，等 PAP-171 灌完数据实测（§3.1、§6.2）。
