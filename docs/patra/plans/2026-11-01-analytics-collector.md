# patra-analytics 服务与采集端点实施计划（PAP-167）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task（本仓库约定：plan 在当前会话内联执行）. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建 `patra-analytics` 服务并实现采集端点：一批采集信封进来，逐条预检、校验、富化、判爬虫、查地区，合格的写 `events.raw`、不合格的写 `events.dlq`，返回 202；Kafka 写不进返回 503；指标可被 Prometheus 抓取。

**Architecture:** 六层模块照 notification。`domain.kernel` 放事件名、来源、版本；`domain.collect` 放时间校正、爬虫判定与 6 个端口；`app.collect` 的 `CollectEventsHandler` 编排；`infra.collect.*` 实现端口（networknt + Jackson 3、Yauaa、COUNTER-Robots、Redis Lua、ip2region、KafkaTemplate）；`adapter.collect.rest` 暴露 `POST /events`。

**Tech Stack:** Java 25、Spring Boot 4.1.2、spring-boot-starter-kafka、spring-boot-starter-data-redis、networknt json-schema-validator 3.0.x、Yauaa 8.2.0、ip2region 3.3.7、micrometer-registry-prometheus、Testcontainers（`patra-tool-test` 的 Kafka / Redis 初始化器）。

**Spec:** [`docs/patra/specs/2026-11-01-analytics-collector-design.md`](../specs/2026-11-01-analytics-collector-design.md)；格式与口径见 [`2026-11-01-analytics-tracking-plan-design.md`](../specs/2026-11-01-analytics-tracking-plan-design.md)

## 实施时的偏离（2026-11-01，已同步进 spec）

- 指标改走 `micrometer-registry-otlp` 推给 OTel Collector，不加 prometheus 端点与抓取任务（`.claude/rules/tech/observability.md`）；Task 8 的 `prometheus_endpoint_exposes_collect_metrics` 改为 `collect_metrics_are_registered`（actuator metrics 端点）。
- api 模块不放 Java：`EventSchemaResources` 改为 infra 的 `AnalyticsResources`，infra 对 api 用 `runtimeOnly`。
- 领域类改名以符合架构规则：`CollectedEnvelope`、`RejectedEnvelope`、`OutboundMessage`，`EnvelopeCheck` 移到 `model.vo`。
- Task 1 骨架期即声明 `kernel`、`collect` 两个子域（`-Xpkginfo:always`）。
- 生产者设置集中在 `KafkaProducerSettings`，由 customizer 套用，不写进 `application.yml`。
- 采集用例用 `@RequiredArgsConstructor`；`CounterRobotListAdapter`、`NetworkntEventSchemaAdapter` 为 `final`；`spotbugs-exclude.xml` 加 analytics.infra 的注入依赖豁免。

## Global Constraints

- 根包 `dev.patra.analytics`；启动类 `dev.patra.analytics.PatraAnalyticsApplication`；`PatraApplication.run("patra-analytics", …)`。
- 子域：`kernel`（底座）、`collect`。包按 `.claude/rules/tech/package-layout.md`。
- 端口 6700，XXL 执行器 6701；容器内存 768m；错误码前缀 `ANALYTICS`；PG 库 `patra_analytics`（本 Issue 无表）。
- 只用 Jackson 3（`tools.jackson`），只注入 `JsonMapper`；networknt 取 3.0.x 中能在 Boot BOM 的 Jackson 3.1.7 上跑通全部样例的最高版本。
- 所有方法写 `///` Javadoc；参数 ≤ 4 的 record 用 `of()`，≥ 5 用 `@Builder`；时间一律注入 `Clock`。
- 测试命名：单元 `*Test`（`src/test`），集成 `*IT`（`src/integrationTest`）；方法名 snake_case。
- 富化事件与 dlq 记录里不得出现 `client_ip`、`user_agent`；dlq 的 `raw` 先删这两个键再截断到 16,384 字符。
- 预检顺序：`unknown_event` → `source_mismatch` → `version_rejected` → `schema_invalid`。
- 爬虫顺序：`ua_list` → `ua_class` → `rate`；`ua_class` 集合：`Robot`、`Robot Mobile`、`Robot Imitator`、`Spy`、`Hacker`；频率：5 分钟窗口、> 30 条 `article_viewed`、成员 `event_id`、键 `ana:bot-rate:device:{device_id}`；Redis 出错跳过。
- `derived_at = received_at − (sent_at − created_at)`，不在 `[received_at − 24h, received_at + 5min]` 内时取 `received_at`。
- Kafka：`acks=all`、`enable.idempotence=true`、`compression.type=zstd`、`linger.ms=10`、`delivery.timeout.ms=5000`、`max.block.ms=2000`；整批 5 秒截止，首次 `send()` 抛异常即放弃整批；raw 消息 key = `device_id`，dlq 无 key。
- 请求体上限 1 MiB（1,048,576 字节）→ 413；外壳不合格或不是合法 JSON → 400；Kafka 不可用 → 503；否则 202 `{"accepted":n,"rejected":m}`。
- 指标名：`analytics.collect.events`（`outcome`、`reason`、`event_name`）、`analytics.collect.bot.events`（`reason`）、`analytics.collect.batches`（`status`）、`analytics.collect.publish`（计时）、`analytics.collect.rate.rule.skipped`。
- 不做提交，直到全部任务完成、终审通过并经用户同意（仓库约定：改动先本地验收再提交）。

## Review Focus

1. **请求体不是合法 JSON**（截断的 JSON、空体、`null`、顶层是数组）——应返回 400，不是 500。由 Task 3 的 `split_batch_rejects_non_object_json` 钉住。
2. **`client_ip` 不是合法 IP**（`"unknown"`、`"::ffff:203.0.113.24"`、IPv6）——外壳只校验长度，地区应得到 `null` 或正确结果，不抛异常。由 Task 4 的 `locate_returns_empty_for_malformed_ip`、`locate_handles_ipv4_mapped_ipv6` 钉住。
3. **`user_agent` 为空字符串**——Yauaa 照常返回、`device_class` 不为空（`Unknown`），富化后的事件仍合格。由 Task 4 的 `parse_empty_user_agent_yields_unknown_class` 钉住。
4. **dlq 原文超长**（一条 20,000 字符的坏事件）——`raw` 截到 16,384 字符并置 `raw_truncated=true`，删 IP 发生在截断前。由 Task 3 的 `dlq_json_truncates_after_removing_ip` 钉住。
5. **同一批里两条事件同一个 `event_id`**——两条都照常写出（去重归入库），频率窗口只算一次。由 Task 5 的 `same_event_id_counts_once` 钉住。

---

### Task 1：模块骨架、底座值对象、架构测试

**Files:**
- Create：`patra-api/patra-analytics/build.gradle.kts`（聚合，空）、6 个层模块的 `build.gradle.kts`
- Modify：`settings.gradle.kts`（6 个 `includeAt` + `mapParent`）、`gradle/libs.versions.toml`（`json-schema-validator`）
- Create：`patra-analytics-domain/.../domain/kernel/model/vo/{EventName,EventSource,SchemaVersion}.java`、各层 `package-info.java`
- Create：`patra-analytics-api/.../api/collect/schema/EventSchemaResources.java`
- Create：`patra-analytics-boot/.../PatraAnalyticsApplication.java`、`src/main/resources/application.yml`、`config/XxlJobConfig.java`
- Test：`domain/src/test/.../kernel/model/vo/{EventNameTest,SchemaVersionTest}.java`、`boot/src/test/.../architecture/AnalyticsArchitectureTest.java`、`boot/src/test/resources/archunit.properties`

**Interfaces:**
- Produces：
  - `enum EventSource { SERVER, CLIENT; String wire(); }`（`"server"` / `"client"`）
  - `enum EventName`：10 个常量，`String wire()`（如 `"article_viewed"`）、`EventSource source()`、`static Optional<EventName> fromWire(String)`
  - `record SchemaVersion(int model, int revision, int addition)`：`static Optional<SchemaVersion> parse(String)`（只认 `^[0-9]+-[0-9]+-[0-9]+$`）、`boolean accepted()`（`model == CURRENT_MODEL`，`CURRENT_MODEL = 1`）
  - `final class EventSchemaResources`：常量 `SCHEMA_DIR = "analytics/schema/"`、`EXAMPLES_DIR = "analytics/examples/"`、`ROBOTS = "analytics/robots/counter-robots.json"`、`BASE_ID = "https://patra.linqibin.dev/schemas/analytics/"`

- [ ] **Step 1：写失败测试**
  - `EventNameTest`：`from_wire_maps_all_ten_events`（10 个 wire 名都能解析，`ARTICLE_VIEWED.source()==SERVER`、`CITATION_COPIED.source()==CLIENT`）；`from_wire_returns_empty_for_unknown`（`"article_liked"`）。
  - `SchemaVersionTest`：`parse_accepts_model_one_versions`（`"1-0-3"` → accepted）；`parse_rejects_model_two`（`"2-0-0"` → present 但 `accepted()==false`）；`parse_returns_empty_for_garbage`（`"1.0.0"`、`""`、`"1-0-0\n"`）。
  - `AnalyticsArchitectureTest`：照 `NotificationArchitectureTest`，`FOUNDATION = "kernel"`、`SUBDOMAINS = List.of("kernel")`，层规则与子域规则（规则要求每个声明的子域在 domain 有类，`collect` 由 Task 2 加入）。

- [ ] **Step 2：运行确认失败**：`./gradlew :patra-api:patra-analytics:patra-analytics-domain:test` → 编译失败（类不存在）。

- [ ] **Step 3：实现** 模块构建脚本（照 notification；infra 依赖见各任务）、settings、版本目录、三个值对象、`EventSchemaResources`、启动类与 `application.yml`（端口、PG 数据源指向 `patra_analytics`、Flyway、`forward-headers-strategy: framework`、`patra.web.error.context-prefix: ANALYTICS`、XXL 执行器、`management.endpoints.web.exposure.include: health,info,metrics,prometheus`）、`XxlJobConfig`（照 notification）。

- [ ] **Step 4：运行确认通过**：`./gradlew :patra-api:patra-analytics:patra-analytics-domain:check :patra-api:patra-analytics:patra-analytics-boot:test` → PASS。

### Task 2：collect 领域模型、策略与端口

**Files:**
- Create（`domain/.../domain/collect/`）：`model/vo/{CollectedEnvelope,EventEnrichment,UserAgentInfo,GeoLocation,RejectedEnvelope,OutboundMessage}.java`、`model/enums/{BotReason,RejectionReason,EventStream}.java`、`policy/{EventTimeCorrection,BotClassification}.java`、`exception/{InvalidBatchException,BatchTooLargeException,EventPublishUnavailableException}.java`、`port/schema/{EventSchemaPort,EnvelopeCheck}.java`、`port/useragent/UserAgentPort.java`、`port/robot/RobotListPort.java`、`port/rate/DeviceRatePort.java`、`port/geolocation/GeolocationPort.java`、`port/publish/EventPublishPort.java`
- Test：`domain/src/test/.../collect/policy/{EventTimeCorrectionTest,BotClassificationTest}.java`
- Modify：`AnalyticsArchitectureTest`（`SUBDOMAINS` 加 `collect`）

**Interfaces:**
- Produces：
  - `@Builder record CollectedEnvelope(String eventId, EventName eventName, String schemaVersion, EventSource source, Instant createdAt, Instant sentAt, String deviceId, String sessionId, String userId, String pagePath, String pageType, String referrerHost, String clientIp, String userAgent, boolean synthetic, String propertiesJson)`（`userId`、`pagePath`、`referrerHost` 可为 `null`）
  - `@Builder record EventEnrichment(Instant receivedAt, Instant derivedAt, String countryCode, String province, UserAgentInfo userAgent, BotReason botReason)`，`boolean bot()` = `botReason != null`
  - `record UserAgentInfo(String browserFamily, String osFamily, String deviceClass)`、`record GeoLocation(String countryCode, String province)`（`static GeoLocation unknown()`）
  - `enum BotReason { UA_LIST, UA_CLASS, RATE; String wire(); }`、`enum RejectionReason { SCHEMA_INVALID, UNKNOWN_EVENT, SOURCE_MISMATCH, VERSION_REJECTED; String wire(); }`、`enum EventStream { RAW, DLQ }`
  - `record RejectedEnvelope(RejectionReason reason, String detail, String eventName, String rawJson, Instant receivedAt)`、`record OutboundMessage(EventStream stream, String key, String payload)`
  - `final class EventTimeCorrection`：`static Instant derive(Instant createdAt, Instant sentAt, Instant receivedAt)`
  - `final class BotClassification`：`static Optional<BotReason> fromUserAgent(boolean listedInRobots, String deviceClass)`、`static final int RATE_LIMIT = 30`、`static final Duration RATE_WINDOW = Duration.ofMinutes(5)`
  - `sealed interface EnvelopeCheck permits EnvelopeCheck.Valid, EnvelopeCheck.Invalid`：`record Valid(CollectedEnvelope event)`、`record Invalid(RejectionReason reason, String detail, String eventName)`
  - 端口：
    - `EventSchemaPort`：`List<String> splitBatch(String body)`（外壳不合格抛 `InvalidBatchException`）、`EnvelopeCheck check(String eventJson)`、`String enrichedJson(CollectedEnvelope event, EventEnrichment enrichment)`（产物不合格抛 `IllegalStateException`）、`String dlqJson(RejectedEnvelope rejected)`
    - `UserAgentPort`：`UserAgentInfo parse(String userAgent)`
    - `RobotListPort`：`boolean listed(String userAgent)`
    - `DeviceRatePort`：`OptionalInt observe(String deviceId, String eventId, boolean counted, Instant at)`（返回窗口内计数；Redis 出错返回空）
    - `GeolocationPort`：`GeoLocation locate(String ip)`
    - `EventPublishPort`：`void publish(List<OutboundMessage> events)`（失败抛 `EventPublishUnavailableException`）
  - 异常：`InvalidBatchException`（`RULE_VIOLATION`）、`BatchTooLargeException`（`RULE_VIOLATION`）、`EventPublishUnavailableException`（`DEP_UNAVAILABLE`），都继承 `DomainException`。

- [ ] **Step 1：写失败测试**
  - `EventTimeCorrectionTest`：`derive_corrects_client_clock_skew`（created 08:00:00、sent 08:00:02、received 09:00:05 → 09:00:03）；`derive_falls_back_when_more_than_24h_early`；`derive_falls_back_when_more_than_5min_late`；`derive_keeps_value_exactly_at_bounds`（恰好 24h 前、恰好 5 分钟后都保留）。
  - `BotClassificationTest`：`robots_list_wins_over_device_class`（listed=true、`"Desktop"` → `UA_LIST`）；`robot_device_classes_are_bots`（5 个类别各一）；`desktop_is_not_bot`（`"Desktop"`、`"Phone"`、`"Unknown"` → empty）。

- [ ] **Step 2：运行确认失败**（编译失败）。
- [ ] **Step 3：实现** 上述类型。
- [ ] **Step 4：运行确认通过**：`./gradlew :patra-api:patra-analytics:patra-analytics-domain:check :patra-api:patra-analytics:patra-analytics-boot:test` → PASS（domain 纯净、子域规则过）。

### Task 3：JSON Schema 校验适配器

**Files:**
- Create：`infra/.../infra/collect/schema/{NetworkntEventSchemaAdapter,SchemaRegistryLoader}.java`
- Modify：`patra-analytics-infra/build.gradle.kts`（`implementation(libs.json.schema.validator)`、`implementation(project(api))`、`testImplementation(project(api))`）
- Test：`infra/src/test/.../collect/schema/NetworkntEventSchemaAdapterTest.java`

**Interfaces:**
- Consumes：Task 2 的 `EventSchemaPort`、`EnvelopeCheck`、`CollectedEnvelope`、`RejectedEnvelope`；Task 1 的 `EventSchemaResources`、`EventName`、`SchemaVersion`。
- Produces：`@Component NetworkntEventSchemaAdapter implements EventSchemaPort`（构造参数 `JsonMapper`）。schema 用 networknt 的 `SchemaMapper` 把 `BASE_ID` 映射到 classpath `SCHEMA_DIR`；`x-patra-version` 注册为注解关键字；schema 只在启动时加载一次。

- [ ] **Step 1：写失败测试**（样例从 classpath `analytics/examples/` 读）
  - `all_valid_envelopes_pass`：`valid/collect-envelope--*.json` 每个 → `Valid`。
  - `invalid_envelopes_get_expected_reason`：`invalid/collect-envelope--*.json` 每个 → `Invalid`，`--unknown-event` → `UNKNOWN_EVENT`，`--source-mismatch` → `SOURCE_MISMATCH`，`--version-model-2` → `VERSION_REJECTED`，其余 → `SCHEMA_INVALID`。
  - `unevaluated_properties_are_rejected`：`--extra-top-level` 的 detail 提到 `username`。
  - `valid_check_parses_envelope_fields`：`--article-viewed` → `eventName==ARTICLE_VIEWED`、`createdAt==Instant.parse("2026-11-01T08:30:10.120Z")`、`propertiesJson` 里有 `"39871245"`。
  - `split_batch_accepts_valid_batches`、`split_batch_rejects_invalid_shells`：`collect-batch--*` 合格 / 不合格（不合格抛 `InvalidBatchException`）。
  - `split_batch_rejects_non_object_json`：`"{"`、`""`、`"null"`、`"[]"` 都抛 `InvalidBatchException`。
  - `enriched_json_is_valid_and_has_no_ip`：用 `--article-viewed` 的事件 + 一份富化 → 结果按 `enriched-event` 合格，且不含 `client_ip` / `user_agent` 键。
  - `enriched_json_rejects_inconsistent_fields`：`countryCode="US"` + `province="浙江省"` 的富化让产物不合格 → 抛 `IllegalStateException`。
  - `dlq_json_truncates_after_removing_ip`：20,000 字符、含 `client_ip` 的原文 → `raw` 长 16,384、`raw_truncated=true`、不含 `client_ip`；结果按 `dlq-record` 合格。
- [ ] **Step 2：运行确认失败**：`./gradlew :patra-api:patra-analytics:patra-analytics-infra:test --tests "*NetworkntEventSchemaAdapterTest"`。
- [ ] **Step 3：实现**。预检按 Global Constraints 的顺序在 schema 校验前做；`Invalid.detail` 取第一条错误的 JSON 指针 + 说明，截到 1,000 字符。选定 networknt 版本后用 `./gradlew :patra-api:patra-analytics:patra-analytics-infra:dependencyInsight --dependency tools.jackson.core` 确认实际 Jackson 为 3.1.7，记进台账。
- [ ] **Step 4：运行确认通过**。

### Task 4：UA 解析、爬虫列表、地区

**Files:**
- Create：`infra/.../infra/collect/useragent/YauaaUserAgentAdapter.java`、`infra/collect/robot/CounterRobotListAdapter.java`、`infra/collect/geolocation/Ip2RegionGeolocationAdapter.java`
- Modify：`patra-analytics-infra/build.gradle.kts`（`libs.yauaa`、`libs.ip2region`；照 identity-infra 的 `downloadIp2Region*` / `DirectorySystemProperty` 块，系统属性名 `patra.analytics.ip-geolocation.directory`）
- Test：`infra/src/test/.../collect/{useragent/YauaaUserAgentAdapterTest,robot/CounterRobotListAdapterTest,geolocation/Ip2RegionGeolocationAdapterTest}.java`

**Interfaces:**
- Produces：三个 `@Component` 分别实现 `UserAgentPort`、`RobotListPort`、`GeolocationPort`。Yauaa 字段：`DEVICE_CLASS`、`AGENT_NAME`、`OPERATING_SYSTEM_NAME`，缓存 10,000，单例。Robots：启动时把 327 个 `pattern` 编译为 `Pattern.CASE_INSENSITIVE`。地区：`@Value("${patra.analytics.ip-geolocation.directory}")`，文件缺失启动失败。

- [ ] **Step 1：写失败测试**
  - `parse_chrome_on_mac`（Chrome 141 UA → `browserFamily="Chrome"`、`osFamily="Mac OS"`、`deviceClass="Desktop"`）；`parse_googlebot_is_robot_class`；`parse_empty_user_agent_yields_unknown_class`（`deviceClass` 非空）。
  - `googlebot_is_listed`（`"Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"`）；`plain_chrome_is_not_listed`；`matching_is_case_insensitive`（`"GOOGLEBOT/2.1"`）；`loads_all_patterns`（327）。
  - `locate_china_ip_gives_cn_and_province`（`"114.114.114.114"` → `CN` + 非空省份，省份以「省」「市」「自治区」「特别行政区」之一结尾）；`locate_foreign_ip_gives_country_without_province`（`"8.8.8.8"` → `US`、`null`）；`locate_private_ip_is_unknown`（`"192.168.97.1"` → 全 `null`）；`locate_returns_empty_for_malformed_ip`（`"unknown"` → 全 `null`，不抛）；`locate_handles_ipv4_mapped_ipv6`（`"::ffff:114.114.114.114"` → `CN`）。
- [ ] **Step 2：运行确认失败**。
- [ ] **Step 3：实现**。
- [ ] **Step 4：运行确认通过**：`./gradlew :patra-api:patra-analytics:patra-analytics-infra:test`。

### Task 5：Redis 频率规则

**Files:**
- Create：`infra/.../infra/collect/rate/RedisDeviceRateAdapter.java`、`infra/src/main/resources/redis/device-rate.lua`
- Modify：infra 构建（`spring-boot-starter-data-redis`、`integrationTestImplementation(project(":patra-tool:patra-tool-test"))`）
- Test：`infra/src/integrationTest/.../collect/rate/RedisDeviceRateAdapterIT.java`

**Interfaces:**
- Produces：`@Component RedisDeviceRateAdapter implements DeviceRatePort`（`StringRedisTemplate`）。Lua 参数：`KEYS[1]` 键、`ARGV` = 现在毫秒、窗口毫秒、`event_id`、是否计入（`1`/`0`）；返回 `ZCARD`。异常一律返回 `OptionalInt.empty()` 并打 WARN。

- [ ] **Step 1：写失败测试**（`RedisContainerInitializer`）
  - `count_grows_with_article_views`（同一设备 31 个不同 `event_id`、计入 → 第 31 次返回 31）；`same_event_id_counts_once`；`uncounted_events_only_read`（计入 3 次后，不计入的一次返回 3，再查仍 3）；`window_slides_after_five_minutes`（`at` 推到 5 分 1 秒后 → 返回 1）；`different_devices_are_independent`。
  - 另一个上下文 `RedisDeviceRateUnavailableIT`：Redis 指向 `127.0.0.1:1` → `observe` 返回空，不抛。
- [ ] **Step 2：运行确认失败**：`./gradlew :patra-api:patra-analytics:patra-analytics-infra:integrationTest --tests "*RedisDeviceRate*"`。
- [ ] **Step 3：实现**。
- [ ] **Step 4：运行确认通过**。

### Task 6：Kafka 写入

**Files:**
- Create：`infra/.../infra/collect/publish/{KafkaEventPublishAdapter,KafkaPublishProperties}.java`
- Modify：infra 构建（`spring-boot-starter-kafka`）；boot `application.yml` 的 `spring.kafka.producer.*`（Global Constraints 的值）
- Test：`infra/src/integrationTest/.../collect/publish/{KafkaEventPublishAdapterIT,KafkaEventPublishUnavailableIT}.java`

**Interfaces:**
- Produces：`@Component KafkaEventPublishAdapter implements EventPublishPort`（`KafkaTemplate<String,String>`、`Clock`、`MeterRegistry`）。topic 名由 `@ConfigurationProperties("patra.analytics.kafka")` 的 `rawTopic`（默认 `events.raw`）、`dlqTopic`（默认 `events.dlq`）、`batchDeadline`（默认 5s）给出。发送耗时记 `analytics.collect.publish`。

- [ ] **Step 1：写失败测试**（`KafkaContainerInitializer`）
  - `publishes_raw_with_device_key_and_dlq_without_key`：发 2 条 RAW（key `d1`、`d2`）+ 1 条 DLQ → 用测试消费者读到 `events.raw` 2 条、key 对应，`events.dlq` 1 条、key 为 `null`，值原样。
  - `KafkaEventPublishUnavailableIT`（`spring.kafka.bootstrap-servers=127.0.0.1:1`）：`publish_fails_fast_when_broker_unreachable` → 抛 `EventPublishUnavailableException`，耗时 < 6 秒（500 条也一样）。
- [ ] **Step 2：运行确认失败**。
- [ ] **Step 3：实现**。
- [ ] **Step 4：运行确认通过**。

### Task 7：采集用例

**Files:**
- Create：`app/.../app/collect/usecase/collect/{CollectEventsCommand,CollectEventsHandler,CollectEventsResult}.java`
- Test：`app/src/test/.../collect/usecase/collect/CollectEventsHandlerTest.java`

**Interfaces:**
- Consumes：Task 2 的全部端口与策略。
- Produces：`record CollectEventsCommand(String body) implements Command<CollectEventsResult>`（`of(String)`）、`record CollectEventsResult(int accepted, int rejected)`（`of`）、`CollectEventsHandler implements CommandHandler<…>`（端口 + `Clock` + `MeterRegistry`）。流程按 spec §4.2；`received_at` 取一次 `clock.instant()` 全批共用；爬虫先 `RobotListPort` / `UserAgentPort` 判，未命中才调 `DeviceRatePort`（计入 = `ARTICLE_VIEWED`），计数 > `RATE_LIMIT` → `RATE`，空 → 记 `analytics.collect.rate.rule.skipped`；`enrichedJson` 抛 `IllegalStateException` 时记 ERROR 日志、转成 `RejectedEnvelope(SCHEMA_INVALID)`；最后一次 `publish` 所有 `OutboundMessage`；记 `analytics.collect.events`、`analytics.collect.bot.events`。不开事务。

- [ ] **Step 1：写失败测试**（端口用手写假实现，`Clock.fixed`，`SimpleMeterRegistry`）
  - `valid_and_invalid_events_are_routed`：校验器返回 2 个 `Valid`、1 个 `Invalid(SOURCE_MISMATCH)` → 结果 `(2,1)`，发布 2 条 RAW（key 为各自 deviceId）+ 1 条 DLQ。
  - `received_at_is_shared_across_batch`。
  - `robots_listed_skips_rate_rule`（列表命中 → `UA_LIST`，频率端口未被调用）。
  - `rate_over_limit_marks_rate`（频率返回 31 → `RATE`；返回 30 → 不是爬虫）。
  - `rate_unavailable_counts_skip_metric`（频率返回空 → 不是爬虫、指标 +1）。
  - `only_article_viewed_is_counted`（`PAGE_VIEWED` 调频率时 `counted=false`）。
  - `geolocation_and_user_agent_fill_enrichment`。
  - `publish_failure_propagates`（发布抛 `EventPublishUnavailableException` → 原样抛出）。
  - `enriched_output_violation_goes_to_dlq`（`enrichedJson` 抛 → 该条进 DLQ、结果 rejected+1）。
- [ ] **Step 2：运行确认失败**：`./gradlew :patra-api:patra-analytics:patra-analytics-app:test`。
- [ ] **Step 3：实现**。
- [ ] **Step 4：运行确认通过**。

### Task 8：采集端点、错误映射与端到端集成测试

**Files:**
- Create：`adapter/.../adapter/collect/rest/{CollectController,response/CollectResponse}.java`、`boot/.../config/AnalyticsErrorMappingContributor.java`
- Modify：boot 构建（`micrometer-registry-prometheus`、kafka / redis / tool-test 测试依赖）
- Test：`boot/src/integrationTest/.../CollectFlowIT.java`、`boot/src/integrationTest/.../AnalyticsITBootstrap.java`（如需）

**Interfaces:**
- Consumes：Task 7 的命令与结果。
- Produces：`POST /events`（`Content-Type: application/json`）：以 `InputStream` 读取，超过 1,048,576 字节抛 `BatchTooLargeException`；`commandBus.handle(CollectEventsCommand.of(body))` → 202 `CollectResponse(accepted, rejected)`；`analytics.collect.batches{status}` 在 Controller 记。`AnalyticsErrorMappingContributor`（`@Order(50)`）：`InvalidBatchException` → `BAD_REQUEST`，`BatchTooLargeException` → `CONTENT_TOO_LARGE`，`EventPublishUnavailableException` → `UNAVAILABLE`。

- [ ] **Step 1：写失败测试** `CollectFlowIT`（Kafka + Redis 初始化器，`RestTestClient`）
  - `mixed_batch_returns_202_and_routes_events`：2 条合格（`--article-viewed`、`--page-viewed` 的信封）+ 1 条 `--source-mismatch` → 202 `{"accepted":2,"rejected":1}`；`events.raw` 2 条，JSON 不含 `client_ip` / `user_agent`，含 `received_at`、`derived_at`、`is_bot`，key 为设备 ID；`events.dlq` 1 条，`reason_code=source_mismatch`，`raw` 不含 IP。
  - `bot_user_agent_is_marked`：UA 换成 Googlebot → raw 里 `is_bot=true`、`bot_reason=ua_list`。
  - `empty_batch_returns_400`（`{"events":[]}` → 400，错误码 `ANALYTICS-0400`）；`invalid_json_returns_400`（`"{"`）。
  - `oversized_body_returns_413`（1,048,577 字节 → 413，`ANALYTICS-0413`）。
  - `prometheus_endpoint_exposes_collect_metrics`（`GET /actuator/prometheus` 含 `analytics_collect_events_total`）。
  - 另一个上下文 `CollectKafkaUnavailableIT`：Kafka 指向 `127.0.0.1:1` → 503（`ANALYTICS-0503`），6 秒内返回。
- [ ] **Step 2：运行确认失败**：`./gradlew :patra-api:patra-analytics:patra-analytics-boot:integrationTest`。
- [ ] **Step 3：实现**。
- [ ] **Step 4：运行确认通过**。

### Task 9：部署接入与文档

**Files（Modify 除注明外）：**
- `build.gradle.kts`（`unitOf` 加 `analytics`、`units` 加一项）→ 重跑 `./gradlew dumpModuleGraph` 更新 `patra-infra/cd/module-graph.json`
- `patra-infra/cd/{detect-changes.sh,detect-changes.test.sh,deploy.test.sh,services.json}`（端口 6700，排在 notification 之后、gateway 之前）
- `patra-infra/docker/docker-compose.apps.yaml`（`analytics` 块：768m、healthcheck、`extra_hosts: *infra-hosts`、env_file `.env.common` / nacos.env / redis.env、ip2region 只读挂载、`PATRA_ANALYTICS_IP_GEOLOCATION_DIRECTORY`），头部注释的服务数；`service.Dockerfile` 注释
- Create：`patra-infra/nacos/dev/patra-analytics.yaml`；Modify：`patra-infra/scripts/nacos-verify.sh`
- `patra-api/patra-gateway-boot/src/main/resources/application.yml`（路由 + Scalar 源）与其 `ApplicationConfigTest`
- `patra-infra/docker/prometheus/prometheus.yml`（抓取任务 `patra-analytics`，`patra-analytics:6700`，`/actuator/prometheus`，15s）
- `patra-infra/docker/postgres/init-scripts/02-create-databases.sql`（`CREATE DATABASE patra_analytics;`）
- Create：`.run/PatraAnalyticsApplication.run.xml`、`patra-api/patra-analytics/README.md`
- 文档清单：根 `README.md`、`patra-api/README.md`、`patra-api/patra-gateway-boot/README.md`、`patra-infra/CLAUDE.md`、`.claude/rules/project-info.md`、`patra-infra/docker/README.md`
- `docs/patra/runbooks/v0.18-analytics-go-live-runbook.md`：新增一节（合并前手工建 `patra_analytics` 库；ip2region 目录已在；XXL admin 建执行器 `patra-analytics`；CD 部署本服务与网关；部署后 `docker stats` 不超 768m、`/actuator/health` UP、Prometheus 目标 UP）

- [ ] **Step 1：先改测试与检查**：`detect-changes.test.sh` / `deploy.test.sh` 的期望加入 `analytics`；网关 `ApplicationConfigTest` 断言有 `patra-analytics` 路由与 Scalar 源。
- [ ] **Step 2：运行确认失败**：`bash patra-infra/cd/detect-changes.test.sh`、`bash patra-infra/cd/deploy.test.sh`、`./gradlew :patra-api:patra-gateway-boot:test --tests "*ApplicationConfigTest"`。
- [ ] **Step 3：改配置与文档**（上面的清单），重跑 `dumpModuleGraph`。
- [ ] **Step 4：运行确认通过**：Step 2 的三条命令 + `./gradlew :patra-tool:patra-tool-launch:test --tests "*NacosConfigFilesTest"` + `docker compose -f patra-infra/docker/docker-compose.apps.yaml config -q`（带临时 `DOCKER_CONFIG`，见记忆里的坑）。

### Task 10：全量门控

- [ ] **Step 1**：`./gradlew spotlessApply` 后 `./gradlew check`（全仓库，含各模块 `integrationTest`），输出写进 scratchpad，读尾部。Expected：BUILD SUCCESSFUL。
- [ ] **Step 2**：提醒用户在 IDEA 里点「加载 Gradle 更改」（改了 `settings.gradle.kts`、版本目录与多个构建脚本）。
- [ ] **Step 3**：终审（新上下文的 reviewer），修 Critical / Important，等用户同意后提交。
