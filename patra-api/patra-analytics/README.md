# patra-analytics

行为数据服务（v0.18 起）：采集门户的埋点事件，经 Kafka 入 ClickHouse，供计数、热读、看过还看了与阅读历史使用。

- 版本规划：[`docs/patra/release-specs/v0.18-analytics.md`](../../docs/patra/release-specs/v0.18-analytics.md)
- 事件格式与口径：[埋点方案](../../docs/patra/specs/2026-11-01-analytics-tracking-plan-design.md)，JSON Schema 在 `patra-analytics-api/src/main/resources/analytics/`
- 采集端点：[采集设计](../../docs/patra/specs/2026-11-01-analytics-collector-design.md)
- 入库：[入库与建模设计](../../docs/patra/specs/2026-11-01-analytics-ingest-design.md)（表结构在 `patra-analytics-infra/src/main/resources/clickhouse/`）
- 阅读历史：[阅读历史设计](../../docs/patra/specs/2026-11-02-analytics-reading-history-design.md)（PG 表结构在 `patra-analytics-infra/src/main/resources/db/migration/history/`）
- 计数、热读、看过还看了：[insight 设计](../../docs/patra/specs/2026-11-02-analytics-insight-design.md)（PG 快照表在 `patra-analytics-infra/src/main/resources/db/migration/insight/`）
- 部署：[v0.18 上线 runbook](../../docs/patra/runbooks/v0.18-analytics-go-live-runbook.md)

## 架构

```text
门户服务端 / 模拟器 ──POST /patra-analytics/events──▶ 网关 ──▶ CollectController
  └─ CollectEventsHandler：拆批 → 预检 + JSON Schema 校验 → 富化（时间校正、UA、爬虫、地区、去掉 IP）
       ├─ 合格 → Kafka events.raw（key = device_id）
       ├─ 不合格 → Kafka events.dlq（原因 + 删掉 IP 的原文）
       └─ Kafka 写成后发进程内事件 ArticlesReadEvent ──同步──▶ ReadingHistoryListener（history 子域）
            └─ 按（用户，文献）合并 → 去掉开关关着、清空或删除之前发生的 → 一条 upsert 写 PG history.reading_history
               （语句与锁等待各 500 ms，失败只记指标，采集照常 202）

门户 ──/patra-analytics/me/reading-history──▶ MyReadingHistoryController（登录用户：翻页、删一条、清空、开关）

Kafka events.raw / events.dlq ──批量拉取（一批最多 5 万条）──▶ EventIngestListener
  └─ IngestBatchHandler：整批一次 INSERT FORMAT JSONEachRow，带去重令牌 {topic ID}|p{分区}:{首}-{尾}
       ├─ 写成 → 提交偏移量；物化视图随之写 article_session_reads、session_quality、daily_person_activity
       ├─ ClickHouse 不可用 → 不提交，指数退避（1 秒起、上限 30 秒）重试同一批
       └─ 被拒（坏数据）→ 二分定位坏行，坏行转 dlq_events（ingest_rejected）

XXL-Job deletedUserCleanupJob（每天 06:20）──▶ CleanupDeletedUsersHandler
  └─ 按用户 ID 分批查 identity，已注销的一批用户在各子域删掉：ingest 的明细、dlq、汇总与设备账号对应，history 的三张表

XXL-Job readingHistoryRetentionJob（每天 06:40）──▶ PurgeExpiredHistoryHandler
  └─ 每批 1 万行删 12 个日历月前的历史、2 天前的删除记录

门户 ──POST /patra-analytics/articles/stats──▶ ArticleInsightController（公开，一次 1 ~ 100 篇）
  └─ ArticleStatsQueryService：Redis MGET（键 analytics:insight:stats:{real|all}:{id}，60 秒）
       ├─ 没命中的合成一条 ClickHouse 查询（article_session_reads × session_quality，只数真人会话、按人去重）
       └─ hot 来自 PG insight.hot_reading（只认上榜的）

XXL-Job hotReadingJob（每小时第 5 分钟）──▶ RefreshHotReadingHandler
  └─ ClickHouse events 近 14 天：每人每天每篇一行，全文 ×3、半衰期 3 天，近 7 天 ≥ 3 人才上榜
       → catalog 核对（软删、撤稿去掉）→ 不足 14 篇时取 catalog 最新收录补位 → 一个事务整表替换 insight.hot_reading

XXL-Job coReadingJob（每天 04:30）──▶ RefreshCoReadingHandler
  └─ ClickHouse events 近 14 天：同一人同一天为一组（当天最先读的 20 篇），按文献分 8 片算配对，
     ≥ 3 人、余弦相似度 → catalog 核对 → 每篇前 20 名，一个事务整表替换 insight.co_reading

ClickHouse 刷新视图 *_rmv（每小时，错开分钟）──▶ ADS 表（运营分析看板只读这一层）
  └─ 计算视图 *_calc(from, to) 重算最近两天（留存 32 个同期群，首日来自增量的 ads_person_first_day）→ 追加进 ReplacingMergeTree，面板读 *_latest 视图（每天只取最近一次计算）
     活跃、留存与新增、漏斗与跳出、检索与无结果词、COUNTER 文献使用、迟到分布、重复率；历史用 patra-infra/scripts/backfill-ads.sh 补

门户 ──GET /patra-analytics/hot-reads──▶ HotReadingController（只读 PG 快照，14 篇）
门户 ──GET /patra-analytics/articles/{id}/also-read──▶ AlsoReadQueryService
  └─ 共现快照前 6 篇；不足时向 catalog 要同主题邻居（Redis 缓存 6 小时），catalog 挂了只少补位
```

- 端口 6700（XXL-Job 执行器 6701），容器内存上限 768 MB。
- 子域：`kernel`（事件名、来源、schema 版本、dlq 记录、注销清理流程）、`collect`（采集）、`ingest`（入库）、`history`（阅读历史，订阅 `collect` 的阅读事件）、`insight`（计数、热读、看过还看了），边界由 `AnalyticsArchitectureTest` 守。
- 对外数字（计数、热读、看过还看了）只数真人会话、排除合成事件；`patra.analytics.include-synthetic=true` 时计入合成事件（只在本机开发打开，mini 上一直关着）。卡片内容由门户拿 ID 向 catalog 批量取。
- 登录：`patra-tool-security` 验网关签发的身份断言，阅读历史的接口经 `CurrentUserPort.require()` 取当前用户，未登录 401；网关对 `/patra-analytics/**` 放行。
- 依赖：Kafka 与 ClickHouse（pipeline 栈；ClickHouse 建表由启动时的迁移器执行，记录在 `schema_migrations`）、Redis（爬虫频率窗口）、identity（注销清理取账号状态）、catalog（热读与共现核对文献、最新收录、同主题邻居，`lb://patra-catalog`）、PostgreSQL `patra_analytics`（阅读历史；Hikari 拿连接最多等 500 ms）、ip2region 离线库（与 identity 共用）。
- 返回：202 收下（不论几条不合格）、400 外壳不合格、413 超过 1 MiB、503 Kafka 写不进（门户丢弃这一批，模拟器重试）。
- 指标经 OTLP 推给 OTel Collector：`analytics.collect.events`、`analytics.collect.bot.events`、`analytics.collect.batches`、`analytics.collect.publish`、`analytics.collect.rate.rule.skipped`；入库 `analytics.ingest.lag`、`analytics.ingest.batch.size`、`analytics.ingest.insert`、`analytics.ingest.rejected`，注销清理 `analytics.cleanup.users`；阅读历史 `analytics.history.writes`（`recorded` / `disabled` / `erased` / `failed`）、`analytics.history.purged`；数据产品 `analytics.insight.job`、`analytics.insight.snapshot.rows`、`analytics.insight.stats.cache`、`analytics.insight.neighbors`。Grafana 看板「Analytics 管线」；业务指标在看板「运营分析」（读 ADS 表，口径见 `docs/patra/specs/2026-11-02-analytics-dashboard-design.md`）。
