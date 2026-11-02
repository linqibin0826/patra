# 入库与建模实施计划（PAP-168）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task（本仓库约定：plan 在当前会话内联执行）. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `patra-analytics` 加 `ingest` 子域：批量消费 `events.raw` / `events.dlq` 写进 ClickHouse（明细、dlq、三张汇总），带批次去重令牌、二分定位坏行、自算消费滞后；底座加注销清理流程。

**Architecture:** ClickHouse 官方 client-v2，启动时由小迁移器按序执行 `clickhouse/V*.sql`；spring-kafka 批量监听（手动提交、指数退避无限重试）→ `IngestBatchHandler` 建令牌、整批 `INSERT FORMAT JSONEachRow`、被拒时二分；注销清理照搬 notification 但按一批用户删。

**Tech Stack:** Java 25、Spring Boot 4.1.2、spring-kafka、`com.clickhouse:client-v2:0.11.0`、Testcontainers（`ClickHouseContainerInitializer`、`KafkaContainerInitializer`）、XXL-Job、HTTP Interface（identity `UserEndpoint`）。

**Spec:** [`docs/patra/specs/2026-11-01-analytics-ingest-design.md`](../specs/2026-11-01-analytics-ingest-design.md)

## Global Constraints

- 库 `patra_analytics`；表 `events`、`dlq_events`、`article_session_reads`、`session_quality`、`daily_person_activity`、`schema_migrations`；DDL 照 spec §5 原样（含 `Asia/Shanghai`、`ReplacingMergeTree`、`ttl_only_drop_parts`、各表 `non_replicated_deduplication_window = 1000`）。
- 物化视图 `SELECT` 写明列名；查看类事件 = `article_viewed`、`fulltext_read`、`pdf_downloaded`、`article_collected`；全文 = `fulltext_read`、`pdf_downloaded`。
- 写入设置：`insert_deduplication_token`、`deduplicate_blocks_in_dependent_materialized_views=1`、`input_format_null_as_default=1`、`date_time_input_format=best_effort`、`input_format_json_read_objects_as_strings=1`。
- 令牌：`{topicId}|p{分区}:{首}-{尾}|…`（按分区号排序）；子批加 `#{起}-{止}`（批内序号，含两端）。
- 消费者：组 `patra-analytics-ingest`、批量、并发 1、`max.poll.records=50000`、`fetch.min.bytes=1048576`、`fetch.max.wait.ms=1000`、`max.partition.fetch.bytes=16777216`、`fetch.max.bytes=67108864`、`max.poll.interval.ms=600000`、`auto.offset.reset=earliest`、`AckMode.MANUAL`、`ExponentialBackOff(1s 起、×2、上限 30s、不限次数)`；监听方法只抛普通异常，不抛 `BatchListenerFailedException`。
- 「重试也不会好」的 ClickHouse 错误（被拒）：数据本身解析不了的那一类——`CANNOT_PARSE_*` 族、`CANNOT_READ_ALL_DATA`、`INCORRECT_DATA`、`TYPE_MISMATCH`、`CANNOT_PARSE_INPUT_ASSERTION_FAILED`；数字码在实现时对照 ClickHouse 源码 `src/Common/ErrorCodes.cpp` 核对写进常量，并由 Task 2 的 IT 钉住「一行坏 JSON 实际返回的码在清单里」；其余一律当可重试。
- dlq 记录新增 `reason_code = ingest_rejected`（改 PAP-164 的 `dlq-record.schema.json` 与 `RejectionReason`，补样例）。
- 注销清理：每天 06:20（`0 20 6 * * ?`）、执行器 `patra-analytics`、批 100；删除顺序 `events` → `dlq_events` → `article_session_reads`（`person_id IN ('u:…')`）→ `daily_person_activity`；`session_quality` 不删；不包事务。
- 指标：`analytics.ingest.lag`（gauge，`topic`、`partition`，30 秒刷新）、`analytics.ingest.batch.size`（分布）、`analytics.ingest.insert`（计时，`table`、`outcome`）、`analytics.ingest.rejected`、`analytics.cleanup.users`（`outcome`）。
- 不按任务提交；全部完成、终审通过并经用户同意后再提交。

## Review Focus

1. **批次里有空值或非 JSON 的消息**（采集端点保证不会，但 topic 是共享的）——应走「被拒 → 二分 → dlq」，不让消费卡死。由 Task 3 的 `garbage_record_goes_to_dlq_and_rest_ingested` 钉住。
2. **批次只含一个分区或含多个分区**——令牌按分区号排序、确定。由 Task 3 的 `token_is_stable_across_partition_order` 钉住。
3. **北京时间跨天**——UTC 16:30（北京 00:30）的事件落在北京的当天。由 Task 1 的 `day_is_beijing_date` 钉住。
4. **注销清理时 identity 省略某个用户**——不删他。由 Task 5 的 `omitted_users_are_not_deleted` 钉住。
5. **topic 重建后偏移量从 0 重来**——新数据不被当成重复。由 Task 1 的 `new_topic_id_is_not_deduplicated` 钉住。

---

### Task 1：ClickHouse 客户端、迁移器与表结构

**Files:**
- Create：`infra/.../infra/ingest/config/{ClickHouseProperties,ClickHouseClientConfiguration}.java`、`infra/.../infra/ingest/clickhouse/ClickHouseMigrator.java`
- Create：`infra/src/main/resources/clickhouse/V1__events.sql`、`V2__dlq_events.sql`、`V3__article_session_reads.sql`、`V4__session_quality.sql`、`V5__daily_person_activity.sql`
- Create：各层 `ingest` 子域 `package-info.java`；Modify：`AnalyticsArchitectureTest`（`SUBDOMAINS` 加 `ingest`）
- Test：`infra/src/integrationTest/.../ingest/clickhouse/{ClickHouseMigratorIT,ClickHouseSchemaIT}.java`

**Interfaces:**
- Produces：`@ConfigurationProperties("patra.clickhouse") record ClickHouseProperties(String url, String username, String password, @DefaultValue("patra_analytics") String database)`；`Client` bean（socket 超时 30s、连接超时 5s）；`ClickHouseMigrator.migrate()` 按文件名序执行未执行的脚本（一个文件可含多条语句，以 `;\n` 分隔），记录 `(version, applied_at)` 进 `schema_migrations`（MergeTree）。迁移在 `SmartInitializingSingleton` 里同步执行，先于消费者启动。

- [ ] **Step 1：写失败测试**（`ClickHouseContainerInitializer`，每个 IT 用独立的库名避免互相影响）
  - `ClickHouseMigratorIT`：`migrates_all_scripts_once`（执行两次，`schema_migrations` 5 行）；`migration_creates_ttl_on_events`（`system.tables` 的 `create_table_query` 含 `TTL` 与 `INTERVAL 90 DAY`）。
  - `ClickHouseSchemaIT`（迁移后用 PAP-164 的富化事件样例造行，以 JSONEachRow + spec §4.2 的设置写入）：
    - `computed_columns_are_filled`（`article_id = 39871245`、匿名 `person_id` 以 `d:` 开头、登录以 `u:` 开头）
    - `day_is_beijing_date`（`derived_at = 2026-11-01T16:30:00.000Z` → `toDate(derived_at,'Asia/Shanghai') = 2026-11-02`，`daily_person_activity.day = 2026-11-02`）
    - `same_token_twice_writes_once_everywhere`（同一令牌写两次：四张表 `count()` 都不变）
    - `mv_failure_retry_fills_aggregates`（先 `DROP TABLE article_session_reads`、带令牌 T 写入并断言抛错；重建表（V3 的建表部分）后带同一令牌重写；`article_session_reads` 有行、`events` 未重复）
    - `new_topic_id_is_not_deduplicated`（令牌 `A|p0:0-0` 与 `B|p0:0-0` 两批不同内容都入库）
    - `same_event_id_in_two_batches_counts_once`（不同令牌、同一 `event_id` 写两次：`uniqExact(event_id) = 1`；看过人数（spec §5.4 的查询）= 1）
    - `same_person_same_session_counts_one_reader`（同一人同一会话打开同一篇三次 → 1）
    - `bot_and_server_only_sessions_are_excluded`（会话 S1 有浏览器事件 + 文献浏览 → 计 1；S2 只有服务端事件 → 不计；S3 有一条爬虫事件 → 不计；合成事件 `is_synthetic=1` 可单独区分）
- [ ] **Step 2：运行确认失败**：`./gradlew :patra-api:patra-analytics:patra-analytics-infra:integrationTest --tests "*ClickHouse*"`。
- [ ] **Step 3：实现**（infra 构建加 `libs.clickhouse.client`；迁移脚本照 spec §5；物化视图写在对应表的脚本里）。
- [ ] **Step 4：运行确认通过**。

### Task 2：明细写入端口与 ClickHouse 适配器

**Files:**
- Create：`domain/.../domain/ingest/port/store/EventStorePort.java`、`domain/.../domain/ingest/exception/{IngestUnavailableException,IngestRejectedException}.java`
- Create：`infra/.../infra/ingest/store/ClickHouseEventStoreAdapter.java`
- Test：`infra/src/integrationTest/.../ingest/store/ClickHouseEventStoreAdapterIT.java`、`infra/src/test/.../ingest/store/RejectionCodesTest.java`

**Interfaces:**
- Produces：`EventStorePort`：`void insertEvents(List<String> jsonLines, String token)`、`void insertDlq(List<String> jsonLines, String token)`、`List<Long> findUsersAfter(long afterUserId, int limit)`、`void deleteUsers(List<Long> userIds)`。写入抛 `IngestRejectedException`（错误码在被拒清单内）或 `IngestUnavailableException`（其余，含连接与超时）。`deleteUsers` 按 Global Constraints 的顺序执行轻量删除。

- [ ] **Step 1：写失败测试**
  - `RejectionCodesTest`：清单里的码 → 被拒；`TABLE_NOT_FOUND`、`NOT_ENOUGH_SPACE`(243)、超时 → 可重试。
  - `ClickHouseEventStoreAdapterIT`：`inserts_events_with_token`；`malformed_line_is_rejected`（一行 `{"event_id":"x"}` → `IngestRejectedException`）；`unreachable_server_is_unavailable`（指向 `127.0.0.1:1` 的客户端 → `IngestUnavailableException`）；`inserts_dlq_records`；`finds_users_after_cursor`（用户 3、5、9 → `findUsersAfter(4, 10) = [5, 9]`）；`deletes_users_everywhere`（删 5 后四张表里都没有 `user_id = 5` / `person_id = 'u:5'`，用户 9 还在）。
- [ ] **Step 2：运行确认失败**。
- [ ] **Step 3：实现**。
- [ ] **Step 4：运行确认通过**。

### Task 3：入库用例（令牌、二分、转 dlq）

**Files:**
- Modify：`domain/.../collect/model/enums/RejectionReason.java`（加 `INGEST_REJECTED("ingest_rejected")`）、`api/.../schema/dlq-record.schema.json`（枚举加 `ingest_rejected`，scratchpad 生成脚本同步）、`examples/valid/dlq-record--ingest-rejected.json`
- Create：`domain/.../domain/ingest/model/vo/IngestRecord.java`、`app/.../app/ingest/usecase/ingest/{IngestBatchCommand,IngestBatchHandler,IngestBatchResult}.java`
- Test：`app/src/test/.../ingest/usecase/ingest/IngestBatchHandlerTest.java`

**Interfaces:**
- Consumes：Task 2 的 `EventStorePort`；collect 的 `EventSchemaPort.dlqJson`。
- Produces：`record IngestRecord(String topicId, int partition, long offset, String value)`（`of`）；`record IngestBatchCommand(EventStream stream, List<IngestRecord> records)`（`of`）；`record IngestBatchResult(int inserted, int rejected)`（`of`）。`stream = RAW` 写 `insertEvents`，`DLQ` 写 `insertDlq`（dlq 流被拒的单条只记日志与指标，不再转 dlq）。

- [ ] **Step 1：写失败测试**（假 `EventStorePort`：可设定「含某个标记的行被拒」「前 N 次不可用」）
  - `token_covers_each_partition_range`（p0:10-12、p2:5-5 → `T|p0:10-12|p2:5-5`）
  - `token_is_stable_across_partition_order`（记录乱序给入，令牌相同）
  - `unavailable_propagates_without_writing_dlq`
  - `garbage_record_goes_to_dlq_and_rest_ingested`（8 条里第 6 条被拒：写入调用 ≤ 7 次；其余 7 条全部写入；dlq 收到 1 条 `ingest_rejected`；子批令牌互不相同且都以批次令牌开头）
  - `rejected_dlq_record_is_not_reprocessed`（`DLQ` 流里被拒的单条不调 `insertDlq` 以外的写入、不抛）
  - `metrics_record_batch_size_and_rejections`
- [ ] **Step 2：运行确认失败**。
- [ ] **Step 3：实现**（二分：`write(range)`：成功即返回；被拒且长度 > 1 → 左右两半各 `write`；长度 = 1 → 转 dlq）。同时重跑 PAP-164 的 schema 校验脚本。
- [ ] **Step 4：运行确认通过**：app 的 test + schema 校验脚本。

### Task 4：Kafka 批量消费、topic ID、消费滞后

**Files:**
- Create：`domain/.../domain/ingest/port/topic/TopicIdPort.java`、`infra/.../infra/ingest/topic/KafkaTopicIdAdapter.java`、`infra/.../infra/ingest/lag/KafkaIngestLagGauge.java`
- Create：`adapter/.../adapter/ingest/mq/EventIngestListener.java`、`adapter/.../adapter/ingest/config/IngestListenerConfiguration.java`
- Test：`boot/src/integrationTest/.../ingest/IngestFlowIT.java`、`IngestClickHouseDownIT.java`

**Interfaces:**
- Produces：`TopicIdPort.topicId(String topic): String`（AdminClient `describeTopics`，启动后缓存）；`EventIngestListener` 两个 `@KafkaListener`（`events.raw`、`events.dlq`，`containerFactory = "ingestListenerFactory"`、`autoStartup` 由 `patra.analytics.ingest.enabled` 控制，默认 `true`、`application-test.yml` 里设 `false`，只有入库 IT 打开）；`KafkaIngestLagGauge` 每 30 秒刷新。

- [ ] **Step 1：写失败测试**（Kafka + ClickHouse + PG + Redis 初始化器，`patra.analytics.ingest.enabled=true`，每个 IT 唯一的 topic 名与库名）
  - `records_are_ingested_and_committed`（往 raw topic 写 20 条富化事件 → 20 秒内 `events` 有 20 行、消费组已提交位点 = 20）
  - `garbage_record_goes_to_dlq_table`（混入一条非 JSON → `dlq_events` 1 行 `ingest_rejected`，其余入库）
  - `lag_gauge_reports_backlog`（暂停监听容器、写 5 条 → `analytics.ingest.lag` 的分区值之和 ≥ 5）
  - `IngestClickHouseDownIT`：`clickhouse_down_keeps_offsets_until_recovered`（ClickHouse 指向一个可控的 TCP 代理端口：先关代理，写 3 条，等 5 秒断言未提交；开代理后 20 秒内 3 条入库并提交）
- [ ] **Step 2：运行确认失败**。
- [ ] **Step 3：实现**（容器工厂用 Boot 的 `ConsumerFactory` 加 Global Constraints 的属性、`setBatchListener(true)`、`AckMode.MANUAL`、`DefaultErrorHandler(new ExponentialBackOff(...))`；监听方法：转成 `IngestBatchCommand` → `commandBus.handle` → `ack.acknowledge()`）。
- [ ] **Step 4：运行确认通过**。

### Task 5：注销清理

**Files:**
- Create：`domain/.../domain/kernel/model/enums/AccountState.java`、`domain/.../domain/kernel/port/identity/AccountStatePort.java`、`domain/.../domain/kernel/port/gateway/UserDataCleanupGateway.java`、`domain/.../domain/kernel/exception/IdentityUnavailableException.java`
- Create：`app/.../app/kernel/usecase/cleanup/{CleanupDeletedUsersCommand,CleanupDeletedUsersHandler,CleanupResult}.java`、`app/.../app/ingest/service/IngestUserDataCleanupGateway.java`
- Create：`infra/.../infra/kernel/identity/AccountStateAdapter.java`、`boot/.../config/IdentityClientConfiguration.java`、`adapter/.../adapter/kernel/scheduler/DeletedUserCleanupScheduleJob.java`
- Modify：infra / boot 构建（`patra-identity-api`、`patra-tool-http-client`）；boot `application.yml`（`patra.http-client.http-interface.groups.identity`，照 notification）
- Test：`app/src/test/.../kernel/usecase/cleanup/CleanupDeletedUsersHandlerTest.java`、`infra/src/test/.../kernel/identity/AccountStateAdapterTest.java`、`adapter/src/test/.../kernel/scheduler/DeletedUserCleanupScheduleJobTest.java`

**Interfaces:**
- Produces：`UserDataCleanupGateway`：`String subdomain()`、`List<Long> findUsersAfter(long afterUserId, int limit)`、`int deleteAllOf(List<Long> userIds)`（返回删掉的用户数）；`CleanupDeletedUsersHandler`（照 notification 的游标算法，批 100，不包事务）；`DeletedUserCleanupScheduleJob`（`@XxlJob("deletedUserCleanupJob")`）。

- [ ] **Step 1：写失败测试**（照 notification 的同名测试改写为「按一批删」）
  - `deletes_only_deleted_users`、`omitted_users_are_not_deleted`、`identity_unavailable_skips_batch_and_counts`、`cursor_advances_across_batches`（250 个用户分 3 批）、`gateway_deletes_in_one_call_per_batch`
  - `AccountStateAdapterTest`：映射状态；`RemoteCallException` → `IdentityUnavailableException`
  - `DeletedUserCleanupScheduleJobTest`：成功 / 失败时的 `XxlJobHelper` 结果
- [ ] **Step 2：运行确认失败**。
- [ ] **Step 3：实现**。
- [ ] **Step 4：运行确认通过**：domain / app / infra / adapter 的 test。

### Task 6：部署接入与文档

**Files（Modify 除注明外）：**
- `patra-infra/docker/docker-compose.apps.yaml`（analytics 加 `clickhouse.env` 必需）、`patra-infra/docker/README.md` 密钥表
- `patra-infra/nacos/dev/patra-analytics.yaml`（`patra.clickhouse.url: http://${PATRA_INFRA_HOST:127.0.0.1}:8123`、`username: patra`、`password: ${CLICKHOUSE_PATRA_PASSWORD}`）
- Create：`patra-infra/docker/grafana/provisioning/dashboards/analytics-pipeline.json`（照现有看板的 provisioning 方式）：消费滞后、每批条数、写入耗时、采集合格 / 拒收
- `docs/patra/runbooks/v0.18-analytics-go-live-runbook.md` 新增 8.2（clickhouse.env、迁移核对、删 topic 重建清掉 PAP-165 的假数据、登记 `deletedUserCleanupJob`）
- `patra-api/patra-analytics/README.md`（加 ingest）

- [ ] **Step 1**：`docker compose config -q`（临时 HOME）、`NacosConfigFilesTest`、`detect-changes.test.sh`。Expected：全部通过。

### Task 7：全量门控与终审

- [ ] **Step 1**：`./gradlew spotlessApply` 后 `./gradlew check`。Expected：BUILD SUCCESSFUL。
- [ ] **Step 2**：新上下文 reviewer 终审，修 Critical / Important，等用户同意后提交；提醒用户在 IDEA 里「加载 Gradle 更改」。

## 实施时的偏离

- `daily_person_activity.user_id` 写成 `SimpleAggregateFunction(max, UInt64)`：ClickHouse 26.8 不许 AggregatingMergeTree 有既不在排序键、也不是聚合的列；同一个 `person_id` 的 `user_id` 不变，`max` 就是原值。
- 迁移器与写入设置放 `infra.ingest.support`，消费滞后指标放 `infra.ingest.topic`（plan 写的 `clickhouse`、`lag` 包没有同名端口，分包规则不许）。
- 被拒清单在解析类之外加了 70 `CANNOT_CONVERT_TYPE`、321 `VALUE_IS_OUT_OF_RANGE_OF_DATA_TYPE`、349 `CANNOT_INSERT_NULL_IN_ORDINARY_COLUMN`：同一行永远写不进，当可重试会让消费永久卡住。数字码按 26.8 的 `errorCodeToName()` 核对。
- `RejectionReason`、`EventStream`、`RejectedEnvelope` 挪进底座 `kernel`，生成 dlq 记录拆成底座端口 `DlqRecordPort`（仍由 `NetworkntEventSchemaAdapter` 实现）：ingest 不能用 collect 的非公开包。
- 空消息与不以 `{` 开头的消息不送 ClickHouse，直接按 `ingest_rejected` 转 dlq（JSONEachRow 会静默跳过空行；最常见的垃圾不进二分）。
- 消费组名放 `patra.analytics.ingest.group-id`（boot `application.yml`），监听工厂与滞后指标都读它；`@KafkaListener` 显式写 `groupId`（不写时 Spring 拿 `id` 当组名，集成测试抓到的）。
- 清理实现叫 `IngestUserDataCleanupGatewayImpl`；`CleanupResult` 没有删除行数（ClickHouse 轻量删除不返回行数）。
- OTLP 导出加 `base-time-unit: seconds`，Prometheus 侧的计时名与其他服务一样是 `*_seconds_*`。
- 终审后补：DDL 一律 `IF NOT EXISTS`；迁移改在后台重试，成功后才由 boot 的 `IngestStartup` 启动两个监听容器（ClickHouse 连不上时服务照常启动、采集照收）；写入逐行流式写进请求体，不拼整批字符串；注销清理找用户并上 `article_session_reads`（日活表 400 天过期后仍能找到）。
