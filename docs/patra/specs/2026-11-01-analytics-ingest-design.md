# 入库与建模设计（Kafka → ClickHouse）

> **Issue**：PAP-168（v0.18 Analytics，Milestone Pipeline Ready）
> **日期**：2026-11-01
> **依据**：release spec [`v0.18-analytics.md`](../release-specs/v0.18-analytics.md) 决策 A、E、G、I、J；[埋点方案](2026-11-01-analytics-tracking-plan-design.md)（口径 §6）；[采集设计](2026-11-01-analytics-collector-design.md)（`events.raw` 的消息就是富化事件 ③）；[基础设施](2026-11-01-analytics-infra-design.md)（ClickHouse 账号、库、topic）
> **前置**：PAP-164、165、167 都在 `feat/v0.18-analytics-api`

---

## 1. 要做什么

在 `patra-analytics` 里加 `ingest` 子域：

1. 批量消费 Kafka `events.raw`、`events.dlq`，写进 ClickHouse。
2. 建表：事件明细表、dlq 表，以及两张物化视图汇总表和一张日活表，供 PAP-170（计数、热读、共现）和 PAP-172（面板）查询。
3. 注销清理：用户注销后，删掉他在 ClickHouse 里的事件和汇总（给 PAP-169 的阅读历史留好接入点）。
4. 入库指标：消费滞后、每批条数、写入耗时，在 Grafana 上看得到。

**不做**：计数、热读、共现的查询与接口（PAP-170）；面板的业务指标图（PAP-172）；部署到 mini（按 runbook，部署前另行确认）。

## 2. 先讲清几个概念

### 2.1 为什么自己写消费者，而不用 ClickHouse 的 Kafka 表引擎或 Kafka Connect

业界把 Kafka 接到 ClickHouse 有三种常见做法：

| 做法 | 是什么 | 投递语义 | 为什么不选 |
|---|---|---|---|
| Kafka 表引擎 + 物化视图 | ClickHouse 自己当消费者：建一张 `ENGINE = Kafka` 的表，物化视图从它读、写进真正的表 | 至少一次；ClickHouse 写成功和提交偏移量之间崩溃会重复，消费线程在后台，失败只在 ClickHouse 日志里 | 偏移量、错误、重试都藏在 ClickHouse 内部，出问题不好查；坏消息的处理能力弱 |
| Kafka Connect + ClickHouse Sink | 另起一套 Connect 集群，官方 Sink 插件负责写入 | 可以开「恰好一次」：把每批的偏移量范围存在 ClickHouse 的 KeeperMap 里，重试时按范围认出是同一批 | 要多部署一套 JVM 服务（Connect worker），mini 的内存扛不住 |
| 自写消费者（本版） | 应用里用 spring-kafka 批量拉取，自己写 ClickHouse | 至少一次 + 去重 | 代码要自己写；好处是批次大小、重试、坏消息、指标全在自己手里 |

**「恰好一次」是怎么做到的**：Kafka Connect 的做法是「写入」和「记下这批的偏移量」做成一件事——ClickHouse 收到一批时先看这批的偏移量范围是不是已经写过，写过就跳过。我们的批次去重令牌（§4.3）是同一个思路的轻量版：令牌就是偏移量范围，ClickHouse 在去重窗口内认出同一个令牌就丢弃重复的那批。区别是窗口有限，而且重新拉取时批次的边界可能变（§4.3）。

### 2.2 两道去重：重复投递不影响对外数字

「至少一次」意味着同一条事件可能写进 ClickHouse 两次（批次重试、模拟器故意重发 1%、门户重试）。两道防线：

1. **明细表按 `event_id` 去重**：`events` 用 `ReplacingMergeTree`，排序键末尾是 `event_id`，后台合并时同一事件只留一行；合并前的查询凡是数「条数」的（检索次数、零结果词、曝光、COUNTER Total），一律 `uniqExact(event_id)`，不用 `count()`。
2. **汇总表只存幂等的值**：三张汇总表的列都是 `max` / `min`（「有没有过」「最早一次」），同一事件来两次结果一样；对外数字都是去重人数。汇总表里**不放** `sum` 这类会被重复放大的列。

批次去重令牌（§4.3）在这之前再挡一道：重试同一批时整批丢弃，连物化视图都不触发。

### 2.3 物化视图：写入时就汇总

ClickHouse 的物化视图不是「定时刷新的视图」，而是一个**插入触发器**：每当一批数据写进源表，物化视图就对这一批算一次，把结果追加进目标表。目标表用 `AggregatingMergeTree`，后台合并时把同一个键的多行聚合成一行。查询时用 `-Merge` 函数读出最终结果。

物化视图只能看到当前这一批数据，所以「整个会话里有没有爬虫事件」这种跨批、跨事件的判断它做不了——这一步留给查询时 JOIN（§5.4）。

## 3. 子域与分层

| 东西 | 位置 |
|---|---|
| 子域 | 新增 `ingest`（入库）；注销清理的公共流程放底座 `kernel`（PAP-169 的 `history` 也会接进来） |
| 端口：`EventStorePort`（整批写明细、写 dlq） | `domain.ingest.port.<协作者>` |
| 注销清理的 ingest 实现：`IngestUserDataCleanupGateway`（找用户、按一批用户删） | `app.ingest.service` |
| 注销清理底座：`AccountStatePort`（查 identity 账号状态）、`UserDataCleanupGateway`（各子域实现「找用户、按一批用户删数据」） | `domain.kernel.port.identity`、`domain.kernel.port.gateway` |
| 消费者 | `adapter.ingest.mq.EventIngestListener`（`@KafkaListener` 批量） |
| 写入用例 | `app.ingest.usecase.ingest.IngestBatchHandler` |
| 注销清理用例与任务 | `app.kernel.usecase.cleanup.CleanupDeletedUsersHandler`、`adapter.kernel.scheduler.DeletedUserCleanupScheduleJob`（照搬 notification） |
| ClickHouse 客户端、建表 | `infra.ingest.clickhouse`、`infra.ingest.config` |
| 查 identity | `infra.kernel.identity.AccountStateAdapter`（照搬 notification，经 `UserEndpoint.summaries`） |

ClickHouse 客户端用官方 `com.clickhouse:client-v2`（0.11.0，HTTP 协议，不依赖 Jackson）。连接配置 `patra.clickhouse.url` / `username` / `password`（PAP-165 的约定）；mini 上地址 `http://${PATRA_INFRA_HOST}:8123`、账号 `patra`，密码 `CLICKHOUSE_PATRA_PASSWORD` 来自 `~/.patra/secrets/clickhouse.env`（compose 给 analytics 加这个 env_file）。

## 4. 消费与写入

### 4.1 消费者

| 设置 | 值 | 为什么 |
|---|---|---|
| 消费组 | `patra-analytics-ingest` | — |
| 监听方式 | 批量监听（一次拿到一批 `ConsumerRecord`），并发 1 | 单实例；`events.raw` 3 个分区由一个线程轮流拉 |
| `max.poll.records` | 50,000 | 一批最多 5 万条（release spec 决策 E） |
| `fetch.min.bytes` / `fetch.max.wait.ms` | 1 MiB / 1000 | broker 攒够 1 MiB 或等满 1 秒才返回：流量小时也每秒写一批，不会一条一批 |
| `max.partition.fetch.bytes` / `fetch.max.bytes` | 16 MiB / 64 MiB | 默认每分区 1 MiB，一次拉不到 5 万条 |
| `max.poll.interval.ms` | 600,000 | 一批的写入（含被拒时的二分重写）要在这之内完成，否则触发重平衡 |
| 提交偏移量 | 写入成功后手动提交（`AckMode.MANUAL`） | 写失败不提交，下次重拉 |
| `auto.offset.reset` | `earliest` | 新的消费组从头读，不丢 Kafka 里已有的 2 天 |

`events.dlq` 用同一种消费者、另一个监听方法，写进 dlq 表。

### 4.2 写入

**一批一次 `INSERT ... FORMAT JSONEachRow`**（不按分区拆：每次写入在每张表各产生一个数据片段，合并线程只有 2 个，拆成 3 次就是 3 倍的片段）。消息值本来就是富化事件的 JSON，一行一条直接写，不在 Java 里转对象。写入设置：

- `insert_deduplication_token`：这一批的令牌（§4.3）。
- `deduplicate_blocks_in_dependent_materialized_views = 1`：重试同一批时，物化视图的目标表也按令牌去重。不开它会出现：明细写成功、某个物化视图写失败、整条写入报错；带同一令牌重试时明细被当成重复丢掉、物化视图也被跳过，汇总**永久缺这一批**。四张目标表都设 `non_replicated_deduplication_window`。
- `input_format_null_as_default = 1`：JSON 里的 `null` 写成列的默认值（如匿名用户的 `user_id` 写成 0）。
- `date_time_input_format = 'best_effort'`：直接认 `2026-11-01T08:30:10.120Z`。
- `input_format_json_read_objects_as_strings = 1`：`properties` 这个 JSON 对象原样写进 String 列（默认已开，显式写上）。

### 4.3 批次去重令牌

令牌 = `{topic ID}|p{分区}:{第一个偏移量}-{最后一个偏移量}|...`（这一批涉及的每个分区一段，按分区号排序）；明细表与三张汇总表都设 `non_replicated_deduplication_window = 1000`（记住最近 1,000 次写入的令牌）。

- **topic ID**：Kafka 给每个 topic 的 UUID，启动时用 AdminClient 取一次。删掉 topic 重建、重置消费组、集成测试复用同一个 ClickHouse 容器时偏移量都会从 0 重来，只用偏移量做令牌的话，新数据会被当成重复静默丢掉；topic ID 一换，令牌就不会撞。
- **重试同一批时令牌不变**：批量监听抛出普通异常时，spring-kafka 的错误处理器用内存里的同一批重新调用监听方法，不重新拉取，所以批次边界不变。监听方法不抛 `BatchListenerFailedException`（那会重新定位偏移量、改变批次边界）。
- 写入成功、提交偏移量之前进程崩溃 → 重启后从上次提交处重拉，批次边界可能和上次不同，令牌不同，会写出部分重复——由 §2.2 的两道去重兜底。

### 4.4 失败与重试

| 失败 | 处理 |
|---|---|
| ClickHouse 连不上、超时、返回 5xx | 不提交偏移量；spring-kafka 的错误处理器按指数退避（1 秒起、上限 30 秒、不限次数）重试同一批。ClickHouse 恢复前消费停在原地，事件在 Kafka 里等（保留 2 天） |
| ClickHouse 拒绝这批数据（解析失败等，重试也不会好） | **二分定位**：把这批对半拆开分别写，写成的那半提交，写不进的那半继续对半拆，直到单条；单条写不进就转成 dlq 记录（`reason_code = ingest_rejected`）写进 dlq 表。每个子批的令牌在批次令牌后加 `#{起}-{止}`（批内序号），互不相撞。5 万条里有一条坏的，约 32 次写入就能定位，不会逐条写出 5 万个片段 |
| 磁盘剩余低于 22 GB（ClickHouse 返回 `NOT_ENOUGH_SPACE`，PAP-165 的保护） | 按「连不上」处理：停住等人清理，不丢数据 |

「重试也不会好」的判断按 ClickHouse 的错误码（解析类、类型不匹配类），清单写在实现里并有测试。

## 5. 表结构

所有表在库 `patra_analytics`。建表语句放 `patra-analytics-infra/src/main/resources/clickhouse/`，按编号排序（`V1__events.sql`、`V2__...`）。服务启动后由一个小的迁移器在后台按顺序执行没执行过的脚本，执行记录在 ClickHouse 的 `schema_migrations` 表里（与 golang-migrate 的做法相同）；脚本只增不改，DDL 一律 `IF NOT EXISTS`（版本号在整个脚本执行完才记，中途失败后重跑要安全）。迁移失败按退避一直重试，成功后才启动入库消费者：ClickHouse 连不上时服务照常启动、采集照收（PAP-168 终审补）。PG 的 Flyway 不管 ClickHouse。

### 5.1 明细表 `events`（ODS + DWD）

```sql
CREATE TABLE events (
    event_id        UUID,
    event_name      LowCardinality(String),
    schema_version  LowCardinality(String),
    source          LowCardinality(String),
    created_at      DateTime64(3, 'UTC'),
    sent_at         DateTime64(3, 'UTC'),
    received_at     DateTime64(3, 'UTC'),
    derived_at      DateTime64(3, 'UTC'),
    device_id       String,
    session_id      UUID,
    user_id         UInt64,                        -- 匿名为 0
    page_path       String,
    page_type       LowCardinality(String),
    referrer_host   String,
    country_code    LowCardinality(String),
    province        LowCardinality(String),
    browser_family  LowCardinality(String),
    os_family       LowCardinality(String),
    device_class    LowCardinality(String),
    is_bot          Bool,
    bot_reason      LowCardinality(String),
    is_synthetic    Bool,
    properties      String,                        -- 事件属性的 JSON 原文
    article_id      UInt64 MATERIALIZED toUInt64OrZero(JSONExtractString(properties, 'article_id')),
    person_id       String MATERIALIZED if(user_id != 0, concat('u:', toString(user_id)), concat('d:', device_id))
) ENGINE = ReplacingMergeTree
PARTITION BY toYYYYMMDD(derived_at, 'Asia/Shanghai')
ORDER BY (event_name, toDate(derived_at, 'Asia/Shanghai'), article_id, person_id, event_id)
TTL toDateTime(derived_at) + INTERVAL 90 DAY DELETE
SETTINGS non_replicated_deduplication_window = 1000, ttl_only_drop_parts = 1;
```

- **不存 IP、不存原始 UA**（富化事件里本来就没有）。
- **「天」一律按北京时间**（`Asia/Shanghai`）：用户都在国内，日活、留存、热读的「一天」要和读者的一天对得上；分区与所有汇总表的 `day` 都这样切，写进去后改不了。
- **`ReplacingMergeTree` + 排序键末尾 `event_id`**：后台合并时同一事件只留一行（§2.2）。
- **按天分区 + `ttl_only_drop_parts`**：到期整个分区丢掉，不用逐行删，最省事。90 个分区在官方建议的 100～1000 之内。
- **ORDER BY**：查询大多是「某种事件、某段日期、某些文献」，所以事件名在前、日期次之、文献 ID 再次之；`person_id` 便于按人去重；`event_id` 放最后用于去重。
- **两个计算列**（`MATERIALIZED`）：写入时从属性里取出 `article_id`、按埋点方案 §6.1 算出「人」，物化视图和查询都直接用。`citation_copied` 带的是 `article_ids` 数组，`article_id` 为 0（引用不算查看，埋点方案 §6.8）。

### 5.2 dlq 表 `dlq_events`

按 `dlq-record` 的字段建，另加 `reason_code` 的新取值 `ingest_rejected`（§4.4），以及计算列 `user_id UInt64 MATERIALIZED toUInt64OrZero(JSONExtractString(raw, 'user_id'))`（注销清理要能按用户删，§6）。按天（北京时间）分区、90 天 TTL。PAP-172 的数据质量面板读它。

### 5.3 文献 × 会话汇总 `article_session_reads`（DWS）

```sql
CREATE TABLE article_session_reads (
    article_id    UInt64,
    session_id    UUID,
    person_id     String,
    day           Date,                                  -- 北京时间
    is_synthetic  Bool,
    viewed        SimpleAggregateFunction(max, UInt8),   -- 有过查看类事件
    fulltext      SimpleAggregateFunction(max, UInt8)    -- 有过全文阅读
) ENGINE = AggregatingMergeTree
ORDER BY (article_id, session_id, person_id, day, is_synthetic);
```

物化视图（`SELECT` 里写明列名：`SELECT *` 不含计算列）从 `events` 取 `article_id != 0` 的查看类事件（埋点方案 §6.8：`article_viewed`、`fulltext_read`、`pdf_downloaded`、`article_collected`），按上面的键写一行：`viewed = 1`，`fulltext` 在事件是 `fulltext_read` 或 `pdf_downloaded` 时为 1。**不设 TTL**（长期保留，release spec 决策 J「汇总长期」）。爬虫事件也写进来，排除留给查询（§5.4），这样口径改了不用重算。

### 5.4 会话特征 `session_quality`（DWS）

```sql
CREATE TABLE session_quality (
    session_id    UUID,
    has_client    SimpleAggregateFunction(max, UInt8),   -- 会话里有浏览器事件
    has_bot       SimpleAggregateFunction(max, UInt8),   -- 会话里有事件被标为爬虫
    first_day     SimpleAggregateFunction(min, Date)
) ENGINE = AggregatingMergeTree
ORDER BY session_id;
```

物化视图从 `events` 的每一条都写一行。**真人会话**（埋点方案 §6.5）= `has_client = 1 AND has_bot = 0`。PAP-170 算看过人数时：

```sql
SELECT article_id, uniqExact(person_id) AS viewers
FROM article_session_reads
WHERE article_id IN (...) AND is_synthetic = 0 AND viewed = 1
  AND session_id IN (
      SELECT session_id FROM session_quality
      WHERE session_id IN (SELECT session_id FROM article_session_reads WHERE article_id IN (...))
      GROUP BY session_id HAVING max(has_client) = 1 AND max(has_bot) = 0)
GROUP BY article_id;
```

（这条查询由 PAP-170 实现与压测；本 Issue 只保证表和视图能支撑它，并在集成测试里跑通一次。）不设 TTL，与 `article_session_reads` 同生命周期。

### 5.5 日活 `daily_person_activity`（DWS）

```sql
CREATE TABLE daily_person_activity (
    day           Date,
    person_id     String,
    user_id       UInt64,
    is_synthetic  Bool,
    is_bot        SimpleAggregateFunction(max, UInt8),
    has_client    SimpleAggregateFunction(max, UInt8),   -- 当天有浏览器事件
    first_seen    SimpleAggregateFunction(min, DateTime64(3, 'UTC'))
) ENGINE = AggregatingMergeTree
ORDER BY (day, person_id, is_synthetic)
TTL day + INTERVAL 400 DAY DELETE;
```

每人每天（北京时间）一行，PAP-172 的日活 / 周活 / 月活、新增、留存都从这里算（明细表只留 90 天，算不了 90 天以前的留存）。保留 400 天，够看一年的同比。面板默认只数 `is_bot = 0 AND has_client = 1` 的人（「人 × 天」粒度近似真人会话规则）；不放「当天事件条数」这类 `sum` 列（§2.2）。

**给 PAP-170 的提示**：热读与共现只看近 14 天，`session_quality` 按 `first_day` 过滤后再 GROUP BY，规模受控；不要对全部会话 GROUP BY（patra 账号每条查询上限 1 GiB 内存）。

## 6. 注销清理

照搬 notification 的 `CleanupDeletedUsersHandler`（放 `kernel`，PAP-169 的 `history` 接入同一个流程）：

1. 每天北京时间 06:20 由 XXL-Job 任务 `deletedUserCleanupJob` 触发（执行器 `patra-analytics`）。
2. 按用户 ID 升序分批：各子域给出游标之后有数据的用户，取最小的 100 个，调 identity 的批量摘要（`UserEndpoint.summaries`）取账号状态。
3. 只对 `DELETED` 的用户，逐个子域删除。identity 没返回的不删（省略不等于已注销）；identity 不可用就跳过这一批、记指标，次日重跑。

`ingest` 子域的实现（`IngestUserDataCleanupGateway`）：

- 找用户：日活表 `daily_person_activity` 的 `user_id`，并上 `article_session_reads` 里 `person_id` 以 `u:` 开头的用户，取游标之后最小的一批（明细表只留 90 天，日活表 400 天，文献 × 会话汇总长期保留：只看日活表，400 天前活跃过的用户注销时汇总里的记录永远找不到）。（PAP-168 终审补）
- 删数据：**一批用户一条语句**（`WHERE user_id IN (...)`）：`events` 的排序键里没有 `user_id`，每条删除都要扫全表，逐个用户删会扫上百遍。顺序是 `events`、`dlq_events`、`article_session_reads`（`WHERE person_id IN ('u:…', …)`）、最后 `daily_person_activity`——它是找用户的唯一来源，先删了它，中途失败的话其他表的残留就再也找不到了。用轻量删除（`DELETE FROM`），立即对查询不可见，后台合并时真正删掉。`session_quality` 不含用户信息，不删。
- ClickHouse 没有事务，不包 `TransactionOperations`；中途失败任务标红，次日重跑时按同样顺序再删一遍（删除是幂等的）。
- 因此底座的 `UserDataCleanupGateway` 是「按一批用户删」（`deleteAllOf(List<Long> userIds)`），与 notification 的「按单个用户」不同；PAP-169 的阅读历史按同一接口实现。
- 删掉 `article_session_reads` 里的行，会让对外的「N 人看过」少掉这些人：这是删除权的本意（个保法第 47 条），隐私政策写明。
- 同一用户未登录时用设备 ID 产生的数据（`person_id` 是 `d:...`）不删：无法认定那台设备只属于他（与 release spec 决策 A 的说明一致，隐私政策写明）。

## 7. 指标

沿用采集的 OTLP 通路（采集设计 §6）：

| 指标 | 来源 |
|---|---|
| 消费滞后 `analytics.ingest.lag`（标签 `topic`、`partition`） | 每 30 秒用 AdminClient 对比「已提交偏移量」与「末端偏移量」。不用 Kafka 客户端自带的 `records-lag-max`：它在拉取时采样，ClickHouse 宕机、消费暂停期间不拉取，值变成 NaN，正好在最需要的时候看不到 |
| `analytics.ingest.batch.size`（分布） | 每次写入的条数 |
| `analytics.ingest.dedup.dropped`（计数） | — （ClickHouse 不回报去重丢弃数，不做；重复率在面板上用 `count()` 与 `uniqExact(event_id)` 的差算） |
| `analytics.ingest.insert`（计时，标签 `table`、`outcome`） | 每次写入的耗时与成败 |
| `analytics.ingest.rejected`（计数） | 被 ClickHouse 拒绝、转进 dlq 的条数 |
| `analytics.cleanup.users`（计数，标签 `outcome`） | 注销清理删掉 / 跳过的用户数 |

Grafana 加一块 provisioning 的看板「Analytics 管线」：消费滞后、每批条数、写入耗时、采集的合格与拒收条数。业务指标面板归 PAP-172。

## 8. 测试

| 层 | 测什么 |
|---|---|
| 单元 | 令牌格式；按分区拆批；「重试也不会好」的错误码判定；注销清理流程（照搬 notification 的用例：游标、identity 省略不删、identity 不可用跳过） |
| ClickHouse 集成（Testcontainers） | 迁移器按序执行且只执行一次；用 PAP-164 的富化事件样例写入，`article_id`、`person_id` 计算正确；**同一令牌写两次只有一份，且四张目标表都只有一份**（用 `count()` 验，不只看去重后的人数）；明细写成功、物化视图失败后带同一令牌重试，汇总补齐；topic 重建后偏移量从 0 开始的新数据不被当成重复；同一个 `event_id` 在不同批次写两次，`uniqExact(event_id)` 为 1、看过人数不变；「天」按北京时间切（北京时间 00:30 的事件落在当天）；同一个人一次会话里反复打开同一篇只算一个读者；爬虫事件与合成事件在汇总里可区分；§5.4 的查询在「有爬虫事件的会话」「只有服务端事件的会话」下都不计入；`system.tables` 里能看到 90 天 TTL；按用户删除后明细与汇总里都没有他 |
| Kafka + ClickHouse 端到端 | 往 `events.raw` 写 N 条 → 1 秒内（加上测试的等待）全部入库、偏移量已提交；ClickHouse 不可用期间不提交、恢复后补齐；一条被 ClickHouse 拒绝的消息进 dlq 表、其余照常，且写入次数是对数级 |
| 架构 | 新子域 `ingest` 加进架构测试 |

## 9. 给 PAP-171 模拟器的约束

灌历史直接写 `events` 时：**一次写入只放一天（北京时间）的数据**，每天一个令牌（`backfill:{运行 ID}:{日期}:{序号}`），按顺序写、写完一批再写下一批。一次写入跨 90 天，会在每张表各产生 90 个数据片段，合并线程只有 2 个，很快 `TOO_MANY_PARTS`。物化视图照常触发，汇总表随之生成。

## 10. 上线

写进 v0.18 runbook 第 8 节新的一小节：

- compose 给 analytics 加 `clickhouse.env`（必需）；Nacos 加 `patra.clickhouse.*`。
- 部署后 `schema_migrations` 里有全部脚本；`events.raw` 里 PAP-165 填满测试留下的假数据要先删 topic 重建（PAP-165 runbook 第 7 节），否则会被入库。
- XXL-Job 登记 `deletedUserCleanupJob`（每天 06:20）。

## 11. 我的决定（等你审）

1. **自写消费者**，不用 Kafka 表引擎或 Kafka Connect（§2.1）。
2. **两道去重**：明细表 `ReplacingMergeTree` 按 `event_id`、查询数条数用 `uniqExact(event_id)`；汇总表只放 `max` / `min`，不放 `sum`；批次令牌再挡一道（§2.2、§4.3）。
3. **「真人会话」在查询时 JOIN 判定**，物化视图只写「文献 × 会话」与「会话特征」两张表；爬虫事件也入汇总，排除留给查询（§5.3、§5.4）。
4. **消息原样以 JSONEachRow 写入**，不在 Java 里转对象（§4.2）。
5. **ClickHouse 不可用时停住重试，不丢数据**；被拒绝的单条进 dlq 表（§4.4）。
6. **ClickHouse 建表用自己的小迁移器**，记录在 `schema_migrations`（§5）。
7. **日活表保留 400 天**，明细 90 天，两张文献 / 会话汇总长期（§5）。
8. **注销清理照搬 notification 的流程，放底座，但按一批用户删**；设备 ID 产生的匿名数据不删；删除会让「N 人看过」变小（§6）。
9. **「天」一律按北京时间切**（§5.1）。
10. **令牌带 topic ID**，删 topic 重建后不会误判重复；重试时令牌不变（§4.3）。
11. **被拒时二分定位坏行**，不逐条写（§4.4）。
12. **消费滞后自己算**（已提交 vs 末端偏移量），不用客户端自带的采样值（§7）。
