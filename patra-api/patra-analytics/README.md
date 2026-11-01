# patra-analytics

行为数据服务（v0.18 起）：采集门户的埋点事件，经 Kafka 入 ClickHouse，供计数、热读、看过还看了与阅读历史使用。

- 版本规划：[`docs/patra/release-specs/v0.18-analytics.md`](../../docs/patra/release-specs/v0.18-analytics.md)
- 事件格式与口径：[埋点方案](../../docs/patra/specs/2026-11-01-analytics-tracking-plan-design.md)，JSON Schema 在 `patra-analytics-api/src/main/resources/analytics/`
- 采集端点：[采集设计](../../docs/patra/specs/2026-11-01-analytics-collector-design.md)
- 入库：[入库与建模设计](../../docs/patra/specs/2026-11-01-analytics-ingest-design.md)（表结构在 `patra-analytics-infra/src/main/resources/clickhouse/`）
- 部署：[v0.18 上线 runbook](../../docs/patra/runbooks/v0.18-analytics-go-live-runbook.md)

## 架构

```text
门户服务端 / 模拟器 ──POST /patra-analytics/events──▶ 网关 ──▶ CollectController
  └─ CollectEventsHandler：拆批 → 预检 + JSON Schema 校验 → 富化（时间校正、UA、爬虫、地区、去掉 IP）
       ├─ 合格 → Kafka events.raw（key = device_id）
       └─ 不合格 → Kafka events.dlq（原因 + 删掉 IP 的原文）

Kafka events.raw / events.dlq ──批量拉取（一批最多 5 万条）──▶ EventIngestListener
  └─ IngestBatchHandler：整批一次 INSERT FORMAT JSONEachRow，带去重令牌 {topic ID}|p{分区}:{首}-{尾}
       ├─ 写成 → 提交偏移量；物化视图随之写 article_session_reads、session_quality、daily_person_activity
       ├─ ClickHouse 不可用 → 不提交，指数退避（1 秒起、上限 30 秒）重试同一批
       └─ 被拒（坏数据）→ 二分定位坏行，坏行转 dlq_events（ingest_rejected）

XXL-Job deletedUserCleanupJob（每天 06:20）──▶ CleanupDeletedUsersHandler
  └─ 按用户 ID 分批查 identity，已注销的一批用户一条语句删掉明细、dlq 与汇总里的数据
```

- 端口 6700（XXL-Job 执行器 6701），容器内存上限 768 MB。
- 子域：`kernel`（事件名、来源、schema 版本、dlq 记录、注销清理流程）、`collect`（采集）、`ingest`（入库）。`insight`（计数与热读）、`history`（阅读历史）随 v0.18 后续 Issue 加入，边界由 `AnalyticsArchitectureTest` 守。
- 依赖：Kafka 与 ClickHouse（pipeline 栈；ClickHouse 建表由启动时的迁移器执行，记录在 `schema_migrations`）、Redis（爬虫频率窗口）、identity（注销清理取账号状态）、PostgreSQL `patra_analytics`（阅读历史起才建表）、ip2region 离线库（与 identity 共用）。
- 返回：202 收下（不论几条不合格）、400 外壳不合格、413 超过 1 MiB、503 Kafka 写不进（门户丢弃这一批，模拟器重试）。
- 指标经 OTLP 推给 OTel Collector：`analytics.collect.events`、`analytics.collect.bot.events`、`analytics.collect.batches`、`analytics.collect.publish`、`analytics.collect.rate.rule.skipped`；入库 `analytics.ingest.lag`、`analytics.ingest.batch.size`、`analytics.ingest.insert`、`analytics.ingest.rejected`，注销清理 `analytics.cleanup.users`。Grafana 看板「Analytics 管线」。
