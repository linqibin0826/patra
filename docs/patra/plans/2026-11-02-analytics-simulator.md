# 流量模拟器实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans（本会话内联执行，用户已定）to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal：** 在 `patra-analytics` 下建 `patra-analytics-simulator` 命令行模块，按真实访问规律造合成事件：灌历史直写 ClickHouse、实时压测经 HTTP 打采集端点，记真值并对账，出压测报告；另在注销清理里排除合成用户。

**Architecture：** 纯 Java 命令行（picocli），不依赖服务内部代码。一个流量模型生成中性的 `SimSession` / `SimEvent`，灌历史按（种子，日期，块号）确定性地分块序列化成富化事件写 ClickHouse，实时压测把同样的会话按真实时间在虚拟线程里重放、序列化成采集信封发出。真值账本只记已确认送达的事件，对账按天查汇总表。

**Tech Stack：** Java 25、picocli 4.7.7、ClickHouse client-v2 0.11.0、Jackson 3（`tools.jackson`）、JDK `HttpClient` / `HttpServer`、JUnit 5 + AssertJ、networknt json-schema-validator（测试）、Testcontainers ClickHouse（`patra-tool-test`）。

**Spec：** [`docs/patra/specs/2026-11-02-analytics-simulator-design.md`](../specs/2026-11-02-analytics-simulator-design.md)（下称 spec）

## Global Constraints

- 包根 `dev.patra.analytics.simulator`；所有方法写 `///` Markdown JavaDoc；不用全类名；不用 Util / Helper / Manager 命名；只注入/使用 `JsonMapper`（Jackson 3），不用 `ObjectMapper`。
- 时间一律注入 `Clock`；随机一律 `SplittableRandom`，种子由（运行种子，日期，块号）派生；同参数两次生成逐字节相同。
- 时间字符串恰好 `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`（UTC）；UUID 小写；`event_id` 为 UUIDv7。
- 设备 ID = `sim` + 三位运行号 + 37 位 base64url 字符（共 43 位）；合成用户 ID = 9_000_000_000_000_000_000 + 运行号 × 10_000_000 + 序号；运行号 1～899。
- 「天」一律北京时间（`Asia/Shanghai`）。
- 灌历史写入设置与入库完全一致（`ClickHouseInserts.settings(token)` 的五项），格式 `JSONEachRow`，一次写入只含一天；令牌 `backfill:{run}:{fingerprint}:{yyyyMMdd}:{chunk|spill|dup}`。
- 爬虫频率规则阈值：同设备 5 分钟（300,000 毫秒）内 `article_viewed` > 30 条即标 `rate`；真人会话任意 5 分钟 ≤ 15 条。
- 重发比例 1%：`Duplicates.chosen(eventId)` = `eventId` 低 64 位按 100 取模为 0（纯函数，两种模式共用）。
- 实时压测默认 1,200 条/秒、在途请求上限 512、重试最多 5 次（503 / 超时 / 连接错误），400 / 413 不重试。
- 不提交、不 push、不动 mini：每个任务结束只跑测试，提交统一在整体验收后经编排会话问用户。
- 改了 `*.gradle.kts` 后必须 `./gradlew dumpModuleGraph`，并在结束消息里提醒用户在 IDEA 里点「加载 Gradle 更改」。

## Review Focus

1. **跨午夜的会话**：23:50 开始的会话一部分事件落在第二天——真值按事件各自的北京日期计、灌历史把这部分作为第二天的 `spill` 写入；Task 9、Task 6 各有测试钉住。
2. **重跑同一条命令**：令牌、内容都不变，ClickHouse 行数不变；换了参数（指纹变）不会被误当重复丢掉。Task 9（单元）、Task 11（集成）。
3. **采集端点间歇 503 / 超时**：重试保持同一 `event_id` 与请求体，最终失败的事件不进真值；Task 12 测试钉住。
4. **文献池文件异常**：空文件、重复 ID、`venue_id` 为 0、非正整数行——空文件与非法行启动即失败并说明行号，重复 ID 去重；Task 2 测试钉住。
5. **ClickHouse 磁盘红线**：写入返回 `NOT_ENOUGH_SPACE`（错误码 243）立即停止、不重试、退出码非 0；Task 9 测试钉住。

---

### Task 1：模块骨架与格式基元

**Files：**
- Modify：`settings.gradle.kts`（patra-analytics 那组 `includeAt` 之后加一行）、`gradle/libs.versions.toml`（`picocli = "4.7.7"` 与库 `picocli = { module = "info.picocli:picocli", version.ref = "picocli" }`）
- Create：`patra-api/patra-analytics/patra-analytics-simulator/build.gradle.kts`
- Create：`…/simulator/src/main/java/dev/patra/analytics/simulator/event/Timestamps.java`、`UuidV7.java`、`SyntheticIds.java`、`BeijingDays.java`
- Test：`…/simulator/src/test/java/dev/patra/analytics/simulator/event/TimestampsTest.java`、`UuidV7Test.java`、`SyntheticIdsTest.java`、`BeijingDaysTest.java`

**Interfaces：**
- Produces：
  - `Timestamps.format(Instant) -> String`
  - `UuidV7.of(Instant at, SplittableRandom random) -> UUID`
  - `SyntheticIds(int run)`：`deviceId(SplittableRandom) -> String`、`userId(int index) -> long`、`devicePrefix() -> String`（如 `sim007`）、`userIdLow() -> long`、`userIdHigh() -> long`；`run` 不在 1～899 抛 `IllegalArgumentException`
  - `BeijingDays.of(Instant) -> LocalDate`、`BeijingDays.ZONE`

build.gradle.kts：插件 `id("patra.java-base")`、`application`；`application.mainClass = "dev.patra.analytics.simulator.SimulatorMain"`；依赖 `implementation(libs.picocli)`、`implementation(libs.clickhouse.client)`、`implementation("tools.jackson.core:jackson-databind")`；测试 `testImplementation(libs.bundles.testing)`、`testImplementation(project(":patra-api:patra-analytics:patra-analytics-api"))`、`testImplementation(project(":patra-api:patra-analytics:patra-analytics-infra"))`、`testImplementation(libs.json.schema.validator)`；`integrationTestImplementation(project(":patra-tool:patra-tool-test"))`、`integrationTestImplementation(project(":patra-api:patra-analytics:patra-analytics-infra"))`、`integrationTestImplementation(libs.testcontainers.clickhouse)`；照 `patra-analytics-infra/build.gradle.kts` 末尾那段，给所有 `Test` 任务加 ip2region 数据目录的 `jvmArgumentProviders` 与对 identity 下载任务的 `dependsOn`。

- [ ] **Step 1：写失败测试**

```java
// TimestampsTest
@Test void formats_exactly_three_millis_digits() {
  assertThat(Timestamps.format(Instant.parse("2026-11-01T08:30:10Z"))).isEqualTo("2026-11-01T08:30:10.000Z");
  assertThat(Timestamps.format(Instant.parse("2026-11-01T08:30:10.123456Z"))).isEqualTo("2026-11-01T08:30:10.123Z");
}
// UuidV7Test
@Test void is_lowercase_version_7_and_time_ordered() {
  var r = new SplittableRandom(1);
  UUID a = UuidV7.of(Instant.parse("2026-11-01T00:00:00Z"), r), b = UuidV7.of(Instant.parse("2026-11-01T00:00:01Z"), r);
  assertThat(a.version()).isEqualTo(7);
  assertThat(a.toString()).isEqualTo(a.toString().toLowerCase());
  assertThat(a.toString().compareTo(b.toString())).isNegative();
}
// SyntheticIdsTest
@Test void device_id_has_run_prefix_and_43_base64url_chars() {
  String id = new SyntheticIds(7).deviceId(new SplittableRandom(1));
  assertThat(id).hasSize(43).startsWith("sim007").matches("^[A-Za-z0-9_-]{43}$");
}
@Test void user_ids_live_in_run_range() {
  var ids = new SyntheticIds(7);
  assertThat(ids.userId(0)).isEqualTo(9_000_000_000_070_000_000L);
  assertThat(ids.userIdLow()).isEqualTo(9_000_000_000_070_000_000L);
  assertThat(ids.userIdHigh()).isEqualTo(9_000_000_000_079_999_999L);
}
@Test void rejects_run_outside_1_to_899() {
  assertThatThrownBy(() -> new SyntheticIds(0)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(() -> new SyntheticIds(900)).isInstanceOf(IllegalArgumentException.class);
}
// BeijingDaysTest
@Test void half_past_midnight_beijing_is_that_day() {
  assertThat(BeijingDays.of(Instant.parse("2026-10-31T16:30:00Z"))).isEqualTo(LocalDate.of(2026, 11, 1));
}
```

- [ ] **Step 2：跑测试确认失败** — `./gradlew :patra-api:patra-analytics:patra-analytics-simulator:test`，期望编译失败（类不存在）。
- [ ] **Step 3：实现四个类**（UUIDv7：48 位毫秒时间戳 + 版本 7 + 变体 + 随机位；设备 ID：前缀 + 28 字节随机数 base64url 无填充后取前 37 位）。
- [ ] **Step 4：跑测试确认通过**；`./gradlew dumpModuleGraph` 后 `git diff --stat` 里只有模块图文件与上面的构建文件。

### Task 2：流量模型参数与文献池（Zipf + 主题）

**Files：**
- Create：`…/simulator/src/main/resources/simulator/traffic-model.json`（spec §5 全部默认值）
- Create：`…/model/TrafficModel.java`（record，`static TrafficModel load(Path orNull, JsonMapper)`：`null` 读内置资源）
- Create：`…/model/ArticlePool.java`、`…/model/ZipfSampler.java`
- Test：`…/model/TrafficModelTest.java`、`ArticlePoolTest.java`、`ZipfSamplerTest.java`；测试资源 `…/src/test/resources/simulator/articles-small.csv`（3,000 行 `id,venue_id`，60 个刊、另有 `venue_id = 0` 的 300 行）

**Interfaces：**
- Produces：
  - `TrafficModel`：嵌套 record 覆盖 spec §5 的每个数（字段名照 JSON 键，驼峰）：`zipfExponent`、`maxArticles`、`devices`、`registeredUsers`、`registeredActivityFactor`、`activityParetoAlpha`、`hourly`（24 个 double）、`weekday`（7 个 double，周一起）、`entry`（home/search/article 概率）、`transitions`（`Map<String, Map<String, Double>>`）、`dwellMedianSeconds`、`fulltextDwellMedianSeconds`、`maxPages`、`topicAffinity`（0.7）、`crawlers`（三类的份额与参数）、`regions`、`userAgents`、`searchTerms`、`zeroResultRate`（0.08）
  - `ArticlePool.load(Path csv, int maxArticles) -> ArticlePool`；`size()`、`idAt(int index) -> long`、`topicOf(int index) -> int`、`topicCount()`、`sampleGlobal(SplittableRandom) -> int`、`sampleTopic(SplittableRandom) -> int`（按主题总热度抽主题）、`sampleInTopic(int topic, SplittableRandom) -> int`、`linear(int step) -> int`（爬虫线性遍历）
  - `ZipfSampler(int n, double s)`：`sample(SplittableRandom) -> int`（0 起排名）、`probability(int rank) -> double`

- [ ] **Step 1：写失败测试**

```java
@Test void top_rank_share_matches_harmonic_number() {
  var zipf = new ZipfSampler(200_000, 1.0); var r = new SplittableRandom(42);
  int hits = 0; for (int i = 0; i < 1_000_000; i++) if (zipf.sample(r) == 0) hits++;
  double expected = zipf.probability(0); // 1 / H(200000) ≈ 0.0790
  assertThat(hits / 1e6).isCloseTo(expected, withinPercentage(5));
}
@Test void top_one_percent_takes_about_half() { /* 前 2,000 名命中占比在 0.45～0.65 */ }
@Test void loads_pool_and_groups_topics_by_venue() {
  var pool = ArticlePool.load(resource("articles-small.csv"), 200_000);
  assertThat(pool.size()).isEqualTo(3_000); assertThat(pool.topicCount()).isEqualTo(61); // 60 个刊 + venue 0 一组
}
@Test void deduplicates_ids_and_caps_size() { /* 写临时文件：重复 ID 只留一个；maxArticles=100 时 size()==100 */ }
@Test void rejects_empty_file_and_bad_line_with_line_number() {
  // 空文件 → IllegalArgumentException 含「文献池为空」；第 3 行 "abc,1" → 消息含「第 3 行」
}
@Test void in_topic_sampling_stays_in_topic() { /* sampleInTopic(t) 1 万次全部 topicOf == t */ }
@Test void loads_builtin_model_defaults() {
  var m = TrafficModel.load(null, JsonMapper.builder().build());
  assertThat(m.devices()).isEqualTo(300_000); assertThat(m.registeredUsers()).isEqualTo(30_000);
  assertThat(m.hourly()).hasSize(24); assertThat(m.weekday()).containsExactly(1.0, 1.0, 1.0, 1.0, 1.0, 0.65, 0.6);
  m.transitions().values().forEach(row -> assertThat(row.values().stream().mapToDouble(d -> d).sum()).isCloseTo(1.0, within(1e-9)));
}
```

- [ ] **Step 2：跑测试确认失败。**
- [ ] **Step 3：实现。** Zipf 用预计算累积数组 + `Arrays.binarySearch`；主题内各建一个按全局排名顺序的 Zipf（主题内排名 = 该刊文献在全局排名里的相对次序）；主题热度 = 主题内文献全局概率之和。`hourly` 在 JSON 里写原始系数（凌晨 2～6 点 0.15、9～11 点与 14～17 点 1.8、20～22 点 1.4，其余按 spec §5.3 平滑），`TrafficModel` 加载时归一化成均值 1。
- [ ] **Step 4：跑测试确认通过。**

### Task 3：到达曲线

**Files：** Create `…/model/ArrivalCurve.java`；Test `…/model/ArrivalCurveTest.java`

**Interfaces：**
- Consumes：`TrafficModel.hourly()`、`weekday()`
- Produces：`ArrivalCurve(TrafficModel)`：`hourWeight(int hour) -> double`、`dayWeight(DayOfWeek) -> double`、`sampleStart(LocalDate day, SplittableRandom) -> Instant`（按小时权重抽小时，小时内均匀，北京时间）、`allocate(long totalEvents, List<LocalDate> days) -> Map<LocalDate, Long>`（按周内系数分配、合计严格等于 total）

- [ ] **Step 1：写失败测试** — `night_to_peak_ratio_matches_model`（`hourWeight(3)/hourWeight(10)` = 0.15/1.8 归一化后之比）；`sampled_starts_follow_hour_weights`（抽 20 万个，10 点所在小时的频率与 `hourWeight(10)/24` 误差 5% 内，且全部落在当天北京时间）；`allocation_sums_exactly_and_sunday_is_lighter`（88 天分 1.02 亿：合计 = 102_000_000，任一周日 / 同周周三 ≈ 0.6）。
- [ ] **Step 2～4：** 确认失败 → 实现 → 确认通过。

### Task 4：人群、UA 表、地区表（与服务富化结果对齐）

**Files：**
- Create：`…/model/Population.java`、`Person.java`（record）、`UaProfile.java`（record）、`Region.java`（record）
- `traffic-model.json` 的 `userAgents`（约 20 个真人 UA + 声明身份爬虫 UA：Googlebot、bingbot、Baiduspider 各 1 个）与 `regions`（每个省份 / 国家 2～3 个真实 IP）
- Test：`…/model/PopulationTest.java`、`…/model/EnrichmentParityTest.java`

**Interfaces：**
- Produces：
  - `record UaProfile(String userAgent, String browserFamily, String osFamily, String deviceClass, boolean crawler, double weight)`
  - `record Region(String countryCode /*null=查不到*/, String province, List<String> ips, double weight)`
  - `record Person(int index, String deviceId, Long userId /*访客 null*/, UaProfile ua, Region region)`；`personKey() -> String`（`u:{userId}` 或 `d:{deviceId}`）
  - `Population.build(TrafficModel, SyntheticIds, long seed) -> Population`：`size()`、`person(int)`、`sample(SplittableRandom) -> Person`（按活跃度权重）、`freshDevice(SplittableRandom) -> Person`（爬虫用的一次性设备，索引从 `size()` 起递增，访客、无用户 ID）、`humanUa(SplittableRandom)`、`crawlerUa(SplittableRandom)`、`region(SplittableRandom)`

- [ ] **Step 1：写失败测试**

```java
// PopulationTest
@Test void builds_devices_and_registered_users() {
  var p = Population.build(model, new SyntheticIds(1), 42);
  assertThat(p.size()).isEqualTo(300_000);
  assertThat(IntStream.range(0, p.size()).filter(i -> p.person(i).userId() != null).count()).isEqualTo(30_000);
}
@Test void about_a_quarter_of_sampled_sessions_are_logged_in() { /* sample 10 万次，登录占比 0.20～0.30 */ }
@Test void same_seed_same_population() { /* 两次 build 第 123 个人完全相同 */ }
// EnrichmentParityTest（直接实例化 infra 的适配器：new YauaaUserAgentAdapter()、new CounterRobotListAdapter(mapper)、
// new Ip2RegionGeolocationAdapter(Path.of(System.getProperty("patra.analytics.ip-geolocation.directory")))）
@Test void human_uas_are_not_robots_and_parse_as_declared() {
  for (UaProfile ua : model.userAgents()) if (!ua.crawler()) {
    assertThat(robots.listed(ua.userAgent())).as(ua.userAgent()).isFalse();
    var info = yauaa.parse(ua.userAgent());
    assertThat(info).isEqualTo(new UserAgentInfo(ua.browserFamily(), ua.osFamily(), ua.deviceClass()));
  }
}
@Test void crawler_uas_hit_counter_robots() { /* crawler == true 的全部 listed == true */ }
@Test void every_region_ip_resolves_to_declared_country_and_province() {
  for (Region r : model.regions()) for (String ip : r.ips())
    assertThat(geo.locate(ip)).as(ip).isEqualTo(new GeoLocation(r.countryCode(), r.province()));
}
```

（`UserAgentInfo`、`GeoLocation` 的构造与 `listed` / `parse` / `locate` 方法照 infra 现有签名；如 Yauaa 对某个 UA 的解析与表中声明不同，改表，不改断言。）

- [ ] **Step 2～4：** 确认失败 → 实现与填表（IP 先查 ip2region 再填，每省挑能查准的地址）→ 确认通过。

### Task 5：中性事件与真人会话生成

**Files：**
- Create：`…/event/SimEventName.java`（enum：10 个事件，带 `wire()` 与 `source()`）、`…/event/EventSource.java`（enum `SERVER` / `CLIENT`）、`…/event/SimEvent.java`、`…/event/PageType.java`（enum，wire 值照 schema）、`…/model/SessionKind.java`（`HUMAN`、`DECLARED_CRAWLER`、`STEALTH_CRAWLER`、`NOSCRIPT`）、`…/model/SimSession.java`、`…/model/SessionGenerator.java`
- Test：`…/model/SessionGeneratorTest.java`

**Interfaces：**
- Consumes：`ArticlePool`、`Population`/`Person`、`TrafficModel`、`UuidV7`
- Produces：
  - `record SimEvent(UUID eventId, SimEventName name, Instant createdAt, Instant sentAt, Person person, UUID sessionId, String pagePath, PageType pageType, String referrerHost, Map<String, Object> properties, int articleIndex /*-1 无*/, String botReason /*灌历史填，null=非爬虫*/)`；`withBotReason(String) -> SimEvent`
  - `record SimSession(UUID sessionId, Person person, SessionKind kind, List<SimEvent> events)`（events 按 `createdAt` 升序）
  - `SessionGenerator(TrafficModel, ArticlePool)`：`human(Person, Instant start, SplittableRandom) -> SimSession`；`noscript(Person, Instant, SplittableRandom) -> SimSession`
  - `SessionGenerator.meanEventsPerHumanSession(Population, long seed) -> double`（抽 10,000 个会话求平均）

规则（spec §5.4，执行时以 spec 表格为准）：
- 每页先服务端事件（`sentAt = createdAt + 5..50ms`），再浏览器事件；浏览器事件按 5 秒 / 50 条 / 离开页面三者先到攒批，同批共用一个 `sentAt`。
- `list_id`：每次渲染列表新生成；检索结果用 `search_id`；`position` 跨页连续（第 2 页从 21 起）。点击只点已曝光的卡片，`article_clicked` 的 `list_type`/`list_id`/`position` 等于那条曝光。
- 进入文献页的文献：首次按全局 Zipf（入口直达）或兴趣主题（会话开头按 `sampleTopic` 抽），后续 `topicAffinity` 概率取兴趣主题内、否则全局。
- `fulltext_read` 每个页面实例最多一次，`visible_seconds` 10～300；`pdf_downloaded` 20%、`citation_copied` 5%（`article_ids` 为当前文献 1 篇，`style` 随机）、`article_collected` 只在登录会话 5%；文献库页仅登录，2% 会话 `export_completed`。
- 检索词从 `searchTerms` 抽；`zeroResultRate` 的检索 `result_count = 0`、没有曝光、只能「再检」或离开。
- `page_path`：首页 `/`、检索 `/papers`、文献 `/papers/{articleId}`、文献库 `/library`；`referrer_host`：入口直达文献页时为 `www.google.com` / `www.baidu.com` / `cn.bing.com` 之一，其余 `null`。

- [ ] **Step 1：写失败测试**（固定种子生成 5,000 个真人会话后逐条断言）

```java
@Test void every_human_session_has_a_client_event() {}
@Test void fulltext_and_pdf_follow_article_view_of_same_article_in_same_session() {}
@Test void fulltext_read_at_most_once_per_page_instance() {}
@Test void clicks_match_an_earlier_impression() {}
@Test void collected_only_when_logged_in() {}
@Test void article_views_in_any_five_minutes_at_most_15() {}
@Test void events_sorted_and_sent_after_created() {}
@Test void same_session_prefers_interest_topic() {
  // 同一会话里任意两篇文献同主题的比例 ≥ 3 倍于从全局 Zipf 独立抽两篇同主题的比例
}
@Test void mean_events_per_human_session_is_between_8_and_20() {}
@Test void noscript_session_has_only_server_events_and_1_to_3_pages() {}
```

- [ ] **Step 2～4：** 确认失败 → 实现（马尔可夫链用 `transitions` 表按累积概率走，停留时间对数正态：`median * exp(sigma * N(0,1))`，`sigma = 0.8`，读全文页用 `fulltextDwellMedianSeconds`）→ 确认通过。

### Task 6：爬虫、爬虫标记、重发

**Files：**
- Create：`…/model/CrawlerGenerator.java`、`…/model/BotLabeler.java`、`…/model/Duplicates.java`
- Test：`…/model/CrawlerGeneratorTest.java`、`BotLabelerTest.java`、`DuplicatesTest.java`

**Interfaces：**
- Consumes：`SessionGenerator`、`Population.freshDevice`、`ArticlePool.linear`
- Produces：
  - `CrawlerGenerator(TrafficModel, ArticlePool)`：`declared(Person, Instant, SplittableRandom) -> SimSession`（只有服务端 `article_viewed`，每 2～5 秒一篇，50～300 篇，线性遍历，起点随机）；`stealth(Person, Instant, SplittableRandom) -> SimSession`（每篇一个 `page_viewed` + `article_viewed`，每 3～6 秒一篇，60～200 篇）
  - `BotLabeler.label(SimSession) -> SimSession`：逐条复刻采集端点规则（Global Constraints）；`ua.crawler()` → 全部 `ua_list`；否则按 `receivedAt = sentAt + 20ms` 顺序维护该设备的 `article_viewed` 窗口（按 `eventId` 去重、清掉 ≤ now − 300,000ms 的），窗口 > 30 时这条起标 `rate` 直到回落
  - `Duplicates.chosen(UUID eventId) -> boolean`

调用约定：爬虫会话总用 `freshDevice`（一次性设备），所以按会话复刻窗口即与服务端一致——`BotLabeler` 的 JavaDoc 写明这个前提。

- [ ] **Step 1：写失败测试**

```java
@Test void declared_crawler_labeled_ua_list_everywhere() {}
@Test void stealth_crawler_crosses_threshold_and_labels_all_events_after() {
  // 第 31 条 article_viewed 及之后同设备的 page_viewed 都是 "rate"，之前的为 null
}
@Test void window_drops_back_below_threshold_after_five_minutes() {
  // 手工构造：31 条间隔 1 秒，然后停 301 秒再来 1 条 → 最后这条不标
}
@Test void human_sessions_never_labeled() {}
@Test void duplicate_share_is_one_percent() {
  // 100 万个 UUIDv7：chosen 占比在 0.009～0.011
}
@Test void duplicate_choice_is_stable_for_same_event_id() {}
```

- [ ] **Step 2～4：** 确认失败 → 实现 → 确认通过。

### Task 7：两种序列化 + schema 校验

**Files：**
- Create：`…/event/EnrichedEventWriter.java`、`…/event/CollectEnvelopeWriter.java`
- Test：`…/event/EventWritersSchemaTest.java`、`EnrichedEventWriterTest.java`

**Interfaces：**
- Consumes：`SimEvent`、`Timestamps`
- Produces：
  - `EnrichedEventWriter(JsonMapper)`：`write(SimEvent) -> String`（一行 JSON；`received_at = sentAt + 20ms`、`derived_at = received_at − (sentAt − createdAt)`；`country_code`/`province`/`browser_family`/`os_family`/`device_class` 来自 `Person`；`is_bot = botReason != null`；`is_synthetic = true`；不含 `client_ip`、`user_agent`）；`static Instant derivedAt(SimEvent)`
  - `CollectEnvelopeWriter(JsonMapper)`：`write(SimEvent, Instant sentAt, SplittableRandom) -> ObjectNode`（`client_ip` 从 `person.region().ips()` 随机取，查不到地区的用 `198.51.100.x`；`user_agent` = `ua.userAgent()`）；`batch(List<ObjectNode>) -> String`（`{"events":[…]}`）

- [ ] **Step 1：写失败测试** — `EventWritersSchemaTest`：照 `NetworkntEventSchemaAdapter` 构造 `SchemaRegistry`（`$id` 前缀映射 classpath、注册 `x-patra-version` 注解关键字），固定种子生成 10,000 个会话（真人 + 三类爬虫 + 经 `BotLabeler`），每条 `EnrichedEventWriter.write` 通过 `enriched-event`，每条 `CollectEnvelopeWriter.write` 通过 `collect-envelope`，每批通过 `collect-batch`（≤ 500 条）；失败时断言消息带上那条 JSON。`EnrichedEventWriterTest`：`derived_at` 等于 `created_at + 20ms`；访客 `user_id` 为 JSON `null`；`is_bot` / `bot_reason` 一致。
- [ ] **Step 2～4：** 确认失败 → 实现 → 确认通过。

### Task 8：真值账本、真值文件、运行摘要

**Files：**
- Create：`…/truth/TruthLedger.java`、`TruthRow.java`（record）、`TruthFile.java`、`RunSummary.java`
- Test：`…/truth/TruthLedgerTest.java`、`TruthFileTest.java`

**Interfaces：**
- Consumes：`SimSession`、`SimEvent`、`ArticlePool.idAt`
- Produces：
  - `record TruthRow(LocalDate day, long articleId, int viewers, int fulltextReaders)`
  - `TruthLedger(ArticlePool, Function<SimEvent, Instant> dayInstant)`：`accept(SimSession, List<SimEvent> acked)`（真人 = `kind == HUMAN` 且 acked 里有 `CLIENT` 事件；查看类 = `article_viewed`/`fulltext_read`/`pdf_downloaded`/`article_collected`，全文类 = `fulltext_read`/`pdf_downloaded`；人 = `person.index()`）；`closeDaysBefore(LocalDate) -> List<TruthRow>`（排序去重后输出并释放该天之前的缓冲）；`closeAll() -> List<TruthRow>`
  - `TruthFile.write(Path, Iterable<TruthRow>)` / `TruthFile.read(Path) -> List<TruthRow>`（gzip CSV，表头 `day,article_id,viewers,fulltext_readers`）
  - `RunSummary`（record，Jackson 读写 `summary.json`）：`run`、`seed`、`fingerprint`、`deliveredEvents`、`resentEvents`、`failedEvents`、`sessionsByKind`（`Map<String, Long>`）、`botEventsByReason`、`eventsByDay`（`Map<String, Long>`）

- [ ] **Step 1：写失败测试**

```java
@Test void same_person_same_article_same_day_counts_once() {}
@Test void session_crossing_midnight_counts_on_both_days() {}
@Test void crawler_and_noscript_sessions_do_not_count() {}
@Test void human_session_without_acked_client_event_does_not_count() {}
@Test void unacked_events_do_not_count() {}
@Test void fulltext_column_counts_only_fulltext_and_pdf() {}
@Test void close_days_before_releases_only_earlier_days() {}
@Test void truth_file_round_trips() {}
```

- [ ] **Step 2～4：** 确认失败 → 实现（每天两个 `long` 缓冲：`(articleIndex << 32) | personIndex`，排序后数不同值、按文献分组计数）→ 确认通过。

### Task 9：灌历史（确定性分块、令牌、写入、续跑）

**Files：**
- Create：`…/backfill/BackfillPlan.java`、`DayChunk.java`、`ChunkGenerator.java`、`BackfillTokens.java`、`EventSink.java`（接口）、`ClickHouseEventSink.java`、`BackfillRunner.java`、`DiskFullException.java`
- Test：`…/backfill/BackfillPlanTest.java`、`ChunkGeneratorTest.java`、`BackfillTokensTest.java`、`BackfillRunnerTest.java`（用内存里的假 `EventSink`）

**Interfaces：**
- Consumes：Task 2～8 的全部
- Produces：
  - `record BackfillParams(int run, long seed, long events, int days, Integer onlyDays, LocalDate fromDay, Path articles, Path model)`；`fingerprint(byte[] modelBytes, byte[] articlesBytes) -> String`（SHA-256 前 8 位十六进制，覆盖 seed、events、days、两份文件内容；不含 `onlyDays`、`fromDay`）
  - `BackfillPlan.of(BackfillParams, ArrivalCurve, double meanEventsPerSession, Clock) -> BackfillPlan`：`days() -> List<LocalDate>`（昨天往前 `days` 天、升序）、`sessions(LocalDate) -> long`、`chunks(LocalDate) -> int`、`SESSIONS_PER_CHUNK`（= ⌈250,000 / mean⌉ 由 of 算出后固定）
  - `ChunkGenerator.generate(LocalDate day, int chunk) -> DayChunk`；`record DayChunk(List<SimSession> sessions)`，再由 runner 拆成「当天的行」「次日的行（spill）」「重发副本」
  - `BackfillTokens.of(int run, String fingerprint, LocalDate day, String part)`
  - `EventSink`：`insert(Iterator<String> jsonLines, String token)`（流式；抛 `DiskFullException` 或可重试的 `RuntimeException`）
  - `ClickHouseEventSink(Client)`：表 `events`，设置照 `ClickHouseInserts.settings`（在模拟器里复写同样五项，测试逐项比对 infra 的实现）、`JSONEachRow`、请求压缩；错误码 243 → `DiskFullException`
  - `BackfillRunner(BackfillParams, BackfillPlan, ChunkGenerator, EventSink, TruthLedger, Path outDir, Clock)`：`run() -> RunSummary`（每块写入失败按 1s、2s、4s…上限 30s 重试，`DiskFullException` 直接抛出；写完一块才 `ledger.accept`；每天结束 `closeDaysBefore(day)` 并追加写真值；`onlyDays` 之后的天只生成不写；`fromDay` 之前的天只生成不写）

每块的随机源：`new SplittableRandom(mix(seed, day.toEpochDay(), chunk))`，`mix` 用 SplitMix64 组合三者。会话类型按份额抽（爬虫 3% / 1%、无脚本 2%，其余真人）；真人与无脚本用 `population.sample`，爬虫用 `freshDevice`（索引空间按（日期，块）划段，避免与其他块重叠）。

写入顺序（同一天）：`chunk 0..n-1` 各一次（其中第 k 块的行 = 本块当天行 + 第 k−1 块的重发副本）→ 最后一块的重发副本 `dup` → 前一天产生的 `spill`（作为当天的第一次写入，令牌 part = `spill`）。

- [ ] **Step 1：写失败测试**

```java
@Test void days_end_yesterday_and_span_requested_count() { /* Clock 定在 2026-11-02T10:00+08:00，days=88 → 首日 2026-08-06、末日 2026-11-01 */ }
@Test void same_chunk_generated_twice_is_identical() {}
@Test void each_insert_holds_a_single_beijing_day() { /* 假 sink 记录每次写入的行，解析 derived_at 的北京日期唯一 */ }
@Test void resent_copy_lands_in_a_later_insert_than_original() {}
@Test void spill_written_as_next_day_with_spill_token() {}
@Test void token_format_and_fingerprint_change_with_params() {
  assertThat(BackfillTokens.of(1, "ab12cd34", LocalDate.of(2026, 11, 1), "3")).isEqualTo("backfill:1:ab12cd34:20261101:3");
}
@Test void only_days_writes_first_days_but_truth_covers_all() {}
@Test void rerun_sends_identical_tokens_and_bodies() {}
@Test void retries_same_body_then_succeeds() {}
@Test void disk_full_stops_immediately() { /* 假 sink 抛 DiskFullException：run() 抛出，sink 只被调用一次 */ }
@Test void insert_settings_match_ingest() { /* 与 ClickHouseInserts.settings("t") 的 serverSetting 逐项相等 */ }
```

- [ ] **Step 2～4：** 确认失败 → 实现 → 确认通过。

### Task 10：对账

**Files：**
- Create：`…/truth/Reconciler.java`、`ReconcileResult.java`、`RunScope.java`
- Test：`…/truth/ReconcilerTest.java`（比对逻辑，纯内存）

**Interfaces：**
- Consumes：`TruthRow`、`TruthFile`、`SyntheticIds`
- Produces：
  - `RunScope(int run)`：`eventsFilter() -> String`（`startsWith(device_id, 'sim007')`）、`personFilter() -> String`（`(startsWith(person_id, 'd:sim007') OR (startsWith(person_id, 'u:') AND toUInt64OrZero(substring(person_id, 3)) BETWEEN lo AND hi))`）
  - `Reconciler(Client, RunScope)`：`actual(LocalDate day) -> List<TruthRow>`（spec §7.3 的 SQL，`day` 与前一天参数化）；`uniqueEvents(LocalDate day) -> long`；`reconcile(List<TruthRow> truth, RunSummary) -> ReconcileResult`
  - `static ReconcileResult compare(List<TruthRow> truth, List<TruthRow> actual)`
  - `record ReconcileResult(long truthViewers, long actualViewers, double viewersError, long truthFulltext, long actualFulltext, double fulltextError, int mismatchedRows, List<String> sampleMismatches, long writtenRows, long uniqueEvents, boolean passed)`（`passed` = 两个误差都 ≤ 0.01）；写 `reconcile.json`

- [ ] **Step 1：写失败测试**

```java
@Test void identical_sets_have_zero_error() {}
@Test void error_is_sum_of_absolute_differences_over_truth() {
  // truth: (d,1)=10,(d,2)=10；actual: (d,1)=11,(d,2)=9 → viewersError = 2/20 = 0.10，不会抵消成 0
}
@Test void missing_rows_on_either_side_count_fully() {}
@Test void passes_at_one_percent_fails_above() {}
```

- [ ] **Step 2～4：** 确认失败 → 实现 → 确认通过。

### Task 11：灌历史 + 对账集成测试（Testcontainers ClickHouse）

**Files：** Test `…/simulator/src/integrationTest/java/dev/patra/analytics/simulator/backfill/BackfillReconcileIT.java`

**Interfaces：**
- Consumes：`ClickHouseContainerInitializer.getClickHouseContainer()`、infra 的 `ClickHouseMigrator(client).migrate()`（照 `ClickHouseITFixture.newDatabase()` 建独立库）、Task 9、Task 10

- [ ] **Step 1：写测试** — 每个用例新建库并迁移，`SYSTEM STOP MERGES events`（以及三张汇总表），`Clock` 固定；参数 `days = 3、events = 150_000、articles-small.csv`：
  - `backfill_then_reconcile_has_zero_error`：`ReconcileResult.passed()`、两个误差都 = 0。
  - `written_rows_and_unique_events`：`SELECT count() FROM events` = `summary.deliveredEvents + summary.resentEvents`；按天 `uniqExact(event_id)` 之和 = `deliveredEvents`。
  - `rerun_is_idempotent`：同参数再跑一次，`count()` 不变。
  - `only_days_then_full_equals_single_full_run`：先 `onlyDays = 1` 再全量，`count()` 与另一库里一次跑完的相同。
  - `one_partition_per_day`：`system.parts` 里 `events` 的分区 = 3 天（加 spill 也不多出分区）。
- [ ] **Step 2：跑** `./gradlew :patra-api:patra-analytics:patra-analytics-simulator:integrationTest`，失败就按 superpowers:systematic-debugging 查，修到通过。

### Task 12：实时压测——发送端

**Files：**
- Create：`…/live/CollectorClient.java`、`SendOutcome.java`、`LatencyHistogram.java`、`LiveStats.java`
- Test：`…/live/CollectorClientTest.java`（JDK `HttpServer` 假端点）、`LatencyHistogramTest.java`、`LiveStatsTest.java`

**Interfaces：**
- Produces：
  - `CollectorClient(URI endpoint, int maxInFlight, int maxAttempts, Duration requestTimeout, LiveStats stats, Sleeper sleeper)`（`Sleeper` 函数式接口，测试替换成不睡）：`send(String body, int eventCount) -> SendOutcome`（阻塞，供虚拟线程调用；`Semaphore` 控在途；503 / 超时 / `IOException` 按 200ms 起翻倍、上限 5s 重试同一 body；202 解析 `accepted` / `rejected`；`rejected > 0` 记错误）
  - `sealed interface SendOutcome`：`Delivered(int accepted, int rejected)`、`Failed(int status /*-1 网络*/, String reason)`
  - `LatencyHistogram`：`record(long nanos)`、`percentile(double) -> Duration`（对数分桶，误差 ≤ 5%）
  - `LiveStats(Clock)`：`request(int status, long latencyNanos, int events)`、`failed(int events)`、`delivered(int events)`、`onlineSessions(IntSupplier)`、`inFlight(IntSupplier)`、`flushSecond(Writer csv)`（列：`second,sent,delivered,failed,status_202,status_503,status_other,p50_ms,p95_ms,p99_ms,online,in_flight`）

- [ ] **Step 1：写失败测试**

```java
@Test void retries_503_with_identical_body_then_delivers() {}
@Test void does_not_retry_400() {}
@Test void gives_up_after_max_attempts() {}
@Test void in_flight_never_exceeds_limit() { /* 假端点阻塞 200ms，100 个虚拟线程同时 send，最大并发观测值 ≤ 8（maxInFlight=8） */ }
@Test void rejected_count_is_reported() {}
@Test void histogram_percentiles_within_five_percent() {}
@Test void stats_row_per_second() {}
```

- [ ] **Step 2～4：** 确认失败 → 实现 → 确认通过。

### Task 13：实时压测——会话重放与到达调度

**Files：**
- Create：`…/live/SessionReplayer.java`、`LiveRunner.java`、`LiveParams.java`（record）
- Test：`…/live/SessionReplayerTest.java`、`LiveRunnerTest.java`

**Interfaces：**
- Consumes：`SessionGenerator`、`CrawlerGenerator`、`Population`、`CollectEnvelopeWriter`、`CollectorClient`、`TruthLedger`、`Duplicates`、`LiveStats`
- Produces：
  - `record LiveParams(int run, long seed, List<RateStep> ramp /*单速率即一级*/, Duration duration, URI collector, Path articles, Path model, int maxInFlight)`；`record RateStep(double eventsPerSecond, Duration length)`
  - `SessionReplayer(CollectEnvelopeWriter, CollectorClient, Clock, Sleeper, Executor resendExecutor)`：`replay(SimSession) -> List<SimEvent>`（按 `sentAt` 分组成请求：同一 `sentAt` 同一来源的事件一个请求；按「会话墙钟起点 + (sentAt − 计划起点)」睡到点再发，`sent_at` 写实际发出时刻；返回已送达的事件；送达后 `Duplicates.chosen` 的事件交给 `resendExecutor` 在 2～10 秒后单独再发一次，重发结果只记统计、不进真值）
  - `LiveRunner(LiveParams, …)`：`run() -> RunSummary`（平台线程按指数间隔产生会话，λ = 速率 ÷ 平均事件数；每个会话 `Thread.ofVirtual().start`；会话类型按份额；真人抽人时跳过在线中的人（`ConcurrentHashMap.newKeySet()`），连续 100 次都忙则用 `freshDevice`；结束后停止产生、最多再等 5 分钟让在线会话走完；`ledger.accept(session, delivered)`；真值的「天」用 `createdAt` 的北京日期）

- [ ] **Step 1：写失败测试**

```java
@Test void groups_events_into_requests_by_sent_at_and_source() {}
@Test void sleeps_until_planned_offset() { /* 假 Sleeper 记录睡眠时长：之和 = 会话最后一次 sentAt − 首个 sentAt */ }
@Test void sent_at_is_actual_send_time() {}
@Test void only_delivered_events_returned() {}
@Test void resends_chosen_events_in_separate_request() {}
@Test void live_rate_within_ten_percent() {
  // 假端点 + 不睡的 Sleeper + 时间压缩：LiveRunner 以 2,000 条/秒跑 10 秒（Clock 驱动），送达事件数在 18,000～22,000
}
@Test void a_person_is_never_in_two_sessions_at_once() {}
```

- [ ] **Step 2～4：** 确认失败 → 实现 → 确认通过。`live_rate_within_ten_percent` 若时间压缩难以做稳，改为真实时间 3 秒、速率 500/秒、区间 ±15%，并在测试注释里写明原因。

### Task 14：parts / 新鲜度采样、滞后导出、报告

**Files：**
- Create：`…/live/PartsSampler.java`、`…/live/LagExporter.java`、`…/report/ReportWriter.java`、`…/report/StorageStats.java`
- Test：`…/report/ReportWriterTest.java`（固定的 run 目录夹具 + 假存储数据）、`…/live/LagExporterTest.java`（JDK `HttpServer` 假 Prometheus）；集成测试 `…/integrationTest/…/live/PartsSamplerIT.java`

**Interfaces：**
- Produces：
  - `PartsSampler(Client, RunScope, Clock)`：`sample() -> String`（CSV 一行：`time,events_active_parts,max_parts_in_partition,asr_parts,sq_parts,dpa_parts,merges_running,freshness_seconds`；`freshness_seconds` = `dateDiff('millisecond', max(received_at), now64(3)) / 1000.0`，只看本运行、近 10 分钟、今天的分区）；`LiveRunner` 给了 ClickHouse 地址时每 30 秒调一次写 `parts.csv`
  - `LagExporter(URI prometheus, String query)`：`export(Instant from, Instant to, Writer csv)`（`/api/v1/query_range`，步长 15s；默认查询 `max by (topic) (patra_analytics_ingest_lag)`）
  - `StorageStats.query(Client) -> List<StorageRow>`（`system.parts` active，按表：`rows`、`bytes_on_disk`、`data_compressed_bytes`、`data_uncompressed_bytes`、`partitions`）
  - `ReportWriter.write(List<Path> runDirs, List<StorageRow>, Writer out)`：按 spec §10 的模板输出，能算的填好（每行字节 = `bytes_on_disk / rows`；吞吐取 `live-stats.csv` 每分钟均值；延迟取全程直方图；新鲜度峰值与末 5 分钟均值 vs 首 5 分钟均值判「是否持续上涨」；对账取 `reconcile.json`），容器内存与真实浏览、瓶颈结论留 `（人工填写）`

- [ ] **Step 1：写失败测试** — `ReportWriterTest`：夹具里两个运行目录（灌历史、实时压测），输出包含 spec §10 的各级标题、`每行字节` 列算对、对账表两行、未提供 `mem.csv` 时内存节写 `（人工填写）`；`LagExporterTest`：请求带对的 `query`/`start`/`end`/`step`，CSV 每个点一行；`PartsSamplerIT`：灌入一批本运行的事件后 `sample()` 的 `events_active_parts > 0`、`freshness_seconds` ≥ 0。
- [ ] **Step 2～4：** 确认失败 → 实现 → 确认通过。

### Task 15：命令行

**Files：**
- Create：`…/SimulatorMain.java`、`…/command/BackfillCommand.java`、`LiveCommand.java`、`ReconcileCommand.java`、`ReportCommand.java`、`ClickHouseOptions.java`（picocli `@Mixin`）、`OutputDirectory.java`
- Test：`…/command/CommandLineTest.java`

**Interfaces：**
- Consumes：全部前序任务
- Produces：选项照 spec §8～§10 的命令示例：`--run`、`--seed`、`--events`、`--days`、`--only-days`、`--from-day`、`--articles`、`--model`、`--out`（默认 `./simulator-out`）、`--clickhouse-url`、`--clickhouse-user`（默认 `patra`）、`--clickhouse-database`（默认 `patra_analytics`）、密码只读环境变量 `CLICKHOUSE_PATRA_PASSWORD`；`live` 另有 `--rate`（默认 1200）、`--ramp`（如 `500,1000,1500,2000`，每级 `--step-length` 默认 5m）、`--duration`（默认 30m）、`--collector-url`、`--max-in-flight`（默认 512）、`--prometheus-url`；`report` 的 `--run` 可重复、`--output` 指定 Markdown 路径。每次运行在 `--out/run-{NNN}/` 写 `params.json`。退出码：成功 0、对账未通过 1、参数错误 2、`DiskFullException` 3。

- [ ] **Step 1：写失败测试** — `rejects_run_out_of_range_with_exit_2`、`password_never_accepted_as_option`（传 `--clickhouse-password` 退出 2）、`ramp_parses_into_steps`、`params_json_written_without_password`、`help_lists_four_subcommands`。
- [ ] **Step 2～4：** 确认失败 → 实现 → 确认通过。

### Task 16：注销清理排除合成用户（PAP-168 代码）

**Files：**
- Modify：`patra-api/patra-analytics/patra-analytics-infra/src/main/java/dev/patra/analytics/infra/ingest/store/ClickHouseEventStoreAdapter.java`（`findUsersAfter` 的 SQL 两处子查询各加 `AND user_id < {synthetic:UInt64}`，参数常量 `SYNTHETIC_USER_ID_FLOOR = 9_000_000_000_000_000_000L`，JavaDoc 说明原因与 spec 链接）
- Test：`…/infra/src/integrationTest/java/dev/patra/analytics/infra/ingest/store/ClickHouseEventStoreAdapterIT.java` 新增用例

**Interfaces：** 签名不变：`findUsersAfter(long afterUserId, int limit) -> List<Long>`

- [ ] **Step 1：写失败测试**

```java
@Test
void skips_synthetic_user_id_range() {
  adapter.insertEvents(
      List.of(json(userEvent(5)), json(userEvent(9_000_000_000_070_000_001L))), "T|p0:0-1");

  assertThat(adapter.findUsersAfter(0, 10)).containsExactly(5L);
}
```

（`userEvent(long)` 若现有夹具只收 `int`，改成 `long` 重载。）

- [ ] **Step 2：** `./gradlew :patra-api:patra-analytics:patra-analytics-infra:integrationTest --tests "*ClickHouseEventStoreAdapterIT*"`，确认新用例失败。
- [ ] **Step 3：** 改 SQL。
- [ ] **Step 4：** 再跑，全部通过。

### Task 17：模块说明、本地端到端、全量门控

**Files：**
- Create：`patra-api/patra-analytics/patra-analytics-simulator/README.md`（架构概览：两种模式、真值与对账、运行号隔离、命令速查、mini 上的执行步骤指向 spec §12）
- Modify：plan 本文件末尾追加「验证记录」

- [ ] **Step 1：本机 pipeline 栈** — 本机 OrbStack 用 `patra-infra` 同一份 compose 起 Kafka + ClickHouse（runbook 第 0 节的「本机演练」方式），analytics 服务用本机配置指向它们（启动前按本机惯例导出 `JAVA_TOOL_OPTIONS` 关系统代理）。
- [ ] **Step 2：本机实时压测** — `live --run 11 --rate 200 --duration 2m --collector-url http://localhost:6700/events --clickhouse-url http://localhost:8123 --articles <测试资源 articles-small.csv>`；期望：`delivered` ≈ 24,000、失败 0、`parts.csv` 新鲜度 < 10 秒。滞后归零后 `reconcile --run 11` 通过。
- [ ] **Step 3：本机灌历史** — `backfill --run 12 --events 1000000 --days 3`；`reconcile --run 12` 误差 0；记下 `system.parts` 每行字节数（给 mini 的预估做参照）。
- [ ] **Step 4：全量门控** — `./gradlew dumpModuleGraph` 后 `./gradlew check`（含 integrationTest）。负载高时 portal 无关，此处只跑 Gradle；按 `.claude/rules/testing/conventions.md`。
- [ ] **Step 5：** 在本文件末尾写验证记录（命令、关键数字），停下等编排会话转达用户验收与提交许可。

---

## 验证记录

2026-11-02，本机（MacBook、OrbStack），不碰 mini。

- **本机端到端（替代计划 Task 17 Step 1～2）**：本机起 analytics 服务会从 mini 的 Nacos 取配置、连 mini 的 PG / Redis / Kafka，等于动 mini，所以改为 `patra-analytics-boot` 的 `e2eTest`：`LiveSimulationE2E` 在进程内起真的服务（采集 → Kafka → 入库 → ClickHouse 物化视图），依赖全用 Testcontainers。300 条/秒目标跑 60 秒 + 20 秒收尾：送达 8,555 条（另重发 68 条），请求 2,661 个、失败 0、进 dlq 0；会话 734 个（真人 694、声明爬虫 22、伪装爬虫 4、无脚本 14）。对账：看过 462 / 462、读过全文 20 / 20，误差 0，不一致 0 行；ClickHouse 去重后 8,555 条 = 送达数。`./gradlew :patra-api:patra-analytics:patra-analytics-boot:e2eTest`
- **灌历史（替代 Step 3）**：`BackfillReconcileIT`（Testcontainers ClickHouse，服务自己的迁移）3 天 15 万条：对账误差 0、`count()` = 送达 + 重发、按天去重 = 送达、重跑行数不变、试灌后全量与一次跑完相同、每天一个分区。1 百万条的本机实测省略：每条字节数在 mini 试灌时实测（spec §12 第 3 步）。
- **全量门控（Step 4）**：`./gradlew dumpModuleGraph` 后 `./gradlew check` 通过（第一次 SpotBugs 报模拟器 17 处：EI_EXPOSE_REP* 照 analytics-infra 的先例在 `spotbugs-exclude.xml` 按包排除，另修 3 类真问题——`toUpperCase` 不带 Locale、`formatted` 的 SQL 里有换行、int 乘法后才转 long；第二次 interaction-infra 的 Mockito MockMaker 初始化失败，负载 120 时 agent 挂载失败，单独重跑通过，第三次全量通过）。`e2eTest` 不在 `check` 里，上面单独跑过。
- **终审修复后复验**（新上下文 reviewer 终审：0 Critical、3 Important + 2 条 Minor 升级，全部按 RED→GREEN 修完）：`dumpModuleGraph` + `:patra-analytics-simulator:check` + `:patra-analytics-infra:integrationTest` + `:patra-analytics-boot:e2eTest` 全绿。端到端（开预热）300 条/秒 × 60 秒 + 20 秒收尾：送达 21,689 条（预热前同参数只有 8,555 条）、重发 207 条、失败 0、结果不明 0、进 dlq 0；看过 2,226 / 2,226、读过全文 320 / 320，误差 0；ClickHouse 去重后 21,689 条 = 送达数；声明爬虫事件 4,547 条全部标 `ua_list`。
