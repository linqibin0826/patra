# patra-analytics 服务与采集端点设计

> **Issue**：PAP-167（v0.18 Analytics，Milestone Pipeline Ready）
> **日期**：2026-11-01
> **依据**：release spec [`v0.18-analytics.md`](../release-specs/v0.18-analytics.md) 决策 C、D、F、J；埋点方案 [`2026-11-01-analytics-tracking-plan-design.md`](2026-11-01-analytics-tracking-plan-design.md)（下称「埋点方案」）；PAP-165 的 [`2026-11-01-analytics-infra-design.md`](2026-11-01-analytics-infra-design.md)（Kafka、ClickHouse 的地址与 topic）
> **前置**：PAP-164（schema）、PAP-165（Kafka 与测试容器）都已在 `feat/v0.18-analytics-api`

---

## 1. 要做什么

1. 新建第 6 个后端服务 `patra-analytics`：六层模块、部署接入（构建、CD、compose、Nacos、网关、XXL-Job、PG 库、文档清单）、子域架构测试。
2. 在它的 `collect` 子域实现**采集端点**：收一批采集信封，逐条校验、补字段、判爬虫、查地区，合格的写 Kafka `events.raw`，不合格的写 `events.dlq`，然后马上返回。
3. 采集的指标经 OTLP 推给 OTel Collector，最终进 Prometheus。

**不做**：入库消费者与 ClickHouse 表（PAP-168）、阅读历史（PAP-169，采集链路写历史那一步也归它）、计数与热读（PAP-170）、门户埋点（PAP-173）、把 ClickHouse / Kafka 部署到 mini（按 PAP-165 的 runbook，部署前另行确认）。PAP-164 终审留下的 9 条 Minor 不在本 Issue 处理。

## 2. 先讲清几个概念

### 2.1 采集端点为什么「收下就走」

采集端点是整条管线里**唯一同步面对产生方**的一环：门户每处理一个页面请求都可能发一批事件，采集端点慢一点，门户就跟着慢。所以业界的采集服务（PostHog 的 capture、Snowplow 的 collector）都只做三件事：校验格式、补上只有服务端知道的字段、写进消息队列，然后立刻返回。入库、聚合、去重这些重活都在消息队列后面异步做，ClickHouse 停机或变慢时，采集照常收，事件在 Kafka 里等着。

### 2.2 「写进 Kafka」怎样才算写成功

Kafka 生产者有三个关键设置，决定「返回成功」时数据到底安不安全：

| 设置 | 含义 | 我们取 |
|---|---|---|
| `acks` | broker 收到后要确认到什么程度才回成功：`0` 不等、`1` 主副本写了、`all` 所有同步副本都写了 | `all`（单节点时等同于 `1`，但换成多节点不用改配置） |
| `enable.idempotence` | 生产者给每条消息编号，网络重试时 broker 能认出重复、只留一条 | `true` |
| `delivery.timeout.ms` | 一条消息从发出到放弃的总时限（含重试） | 5 秒 |

采集端点把一批事件全部交给生产者，然后**等这一批全部确认**再返回 202。等不到（Kafka 停了、超时），返回 503。

### 2.3 Kafka 不可用时：返回 503，门户丢弃

业界两种做法：采集端点把事件先存本地磁盘、恢复后补发（Snowplow 的部分 collector 支持）；或者直接告诉产生方「现在收不了」，由产生方决定重试还是丢弃（PostHog capture 返回 503，SDK 重试）。

我们选后者：采集端点返回 503，**门户直接丢弃这一批，不重试**（PAP-173 按此实现）；模拟器重试（它要测吞吐与去重）。理由：本地缓冲要处理磁盘写满、重启恢复、重复补发，复杂度远超这一版的价值；行为数据丢一小段不影响业务（计数会少几条），而门户为了重试埋点去堆积请求反而会拖慢页面。代价：Kafka 停机期间的真实浏览会丢失。

已经交给生产者、但整批没等到确认的事件，可能有一部分其实已经写进了 Kafka；产生方重试时 `event_id` 不变，PAP-168 入库时按 `event_id` 去重，不会重复计数。

## 3. 服务骨架

### 3.1 模块与包

```text
patra-api/patra-analytics/
├── patra-analytics-domain    # 纯 Java：版本规则、时间校正、爬虫判定的组合逻辑、端口
├── patra-analytics-app       # 用例编排：CollectEventsHandler
├── patra-analytics-infra     # 端口实现：JSON Schema 校验、Yauaa、ip2region、COUNTER-Robots、Redis 频率、Kafka 生产者
├── patra-analytics-adapter   # REST：采集端点
├── patra-analytics-api       # 已有：JSON Schema 与样例资源；本 Issue 补 build.gradle.kts，另放错误码
└── patra-analytics-boot      # 启动类、组装、架构测试
```

- 根包 `dev.patra.analytics`，启动类 `dev.patra.analytics.PatraAnalyticsApplication`，用 `PatraApplication.run("patra-analytics", …)`。
- 各层 `build.gradle.kts` 照 notification 的写法（`patra.hexagonal-*` 插件）。依赖新增：`spring-boot-starter-kafka`（Boot 管版本）、`com.networknt:json-schema-validator`（**3.0.x**，进版本目录）、`spring-boot-starter-data-redis`、`libs.yauaa`、`libs.ip2region`、`micrometer-registry-prometheus`（Boot 管版本）。
  - networknt 只有 3.x 这条线用 Jackson 3（`tools.jackson`），1.x / 2.x 依赖 Jackson 2，与仓库「只用 Jackson 3、只注入 `JsonMapper`」的规则冲突。Boot 4.1.2 的 BOM 把 Jackson 定在 3.1.7，会覆盖库自己声明的版本：实现时取 3.0.x 里最高的、在 Jackson 3.1.7 上把 `analytics/examples/` 全部样例跑通的版本（3.0.8 声明的是 Jackson 3.2.1，要实测；3.0.4 声明 3.1.1，可作退路）。
- **api 模块只有资源，没有 Java**（实施时改定）：分包规则下 api 层只能有 `endpoint` / `dto` / `event` / `error` 包，资源路径常量放哪都不合规；错误码直接用 `HttpStdErrors`（自动带 `ANALYTICS-` 前缀），也不需要错误码枚举。所以 infra 对 api 用 `runtimeOnly`（构建规则 R5 只查 `api` / `implementation`），测试读样例另加 `testImplementation`；路径常量 `AnalyticsResources` 放 `infra.collect.support`。
- **子域**：本 Issue 声明 `kernel`（底座）与 `collect` 两个。`ingest`、`insight`、`history` 由 PAP-168 / 170 / 169 各自加入架构测试的子域清单（规则要求每个声明的子域在 domain 层有类）。
- **底座 `kernel`** 放 `EventName`（10 个事件名与各自来源）、`EventSource`、`SchemaVersion`（解析 `MODEL-REVISION-ADDITION`、判断是否接受）。分包规则要求底座只放两个以上子域共用的东西，本 Issue 里只有 `collect` 在用；同一版本的 `ingest`（PAP-168，按事件名分流入表）与 `history`（PAP-169，只记文献浏览与全文阅读）都会用到，所以直接放底座，免得下一个 Issue 再搬家。

### 3.2 运行参数

| 项 | 值 |
|---|---|
| 端口 | 6700（XXL-Job 执行器 6701，不映射到宿主机） |
| 内存 | 容器上限 768 MB（release spec 决策 C）；Yauaa 常驻约 108 MB 堆（identity 的实测） |
| PG | 新建库 `patra_analytics`（`02-create-databases.sql`）；本 Issue 只接上数据源与 Flyway，没有表（PAP-169 起才建表） |
| Redis | 用于爬虫频率规则；密码走 `redis.env` |
| Kafka | `spring.kafka.bootstrap-servers`：mini 上 `linqibins-mac-mini:9092`（apps 容器加 `extra_hosts`），本机开发同一地址（PAP-165 契约） |
| ip2region | 数据目录 `patra.analytics.ip-geolocation.directory`，默认 `${user.home}/.patra/data/ip2region`；容器里只读挂载同一目录（与 identity 共用 `fetch-ip2region.sh` 下载的文件） |
| XXL-Job | 接上执行器配置（照 notification 的 `XxlJobConfig`）；本 Issue 没有任务，执行器分组在 mini 的 admin 里手工建（runbook 写明） |
| 错误码前缀 | `ANALYTICS`（`patra.web.error.context-prefix`） |

### 3.3 部署接入清单（照 notification）

- 构建：`settings.gradle.kts` 加 6 个 `includeAt` 与 `mapParent`；根 `build.gradle.kts` 的 `unitOf` 加 `analytics`、`units` 列表加一项；重跑 `./gradlew dumpModuleGraph`。
- CD：`cd/detect-changes.sh` 的 `ALL_UNITS` 与两个测试脚本的期望；`cd/services.json` 加一项（端口 6700，排在 notification 之后、gateway 之前，网关要认得新路由）；`.run/` 加 IDEA 启动配置。
- compose：`docker-compose.apps.yaml` 加 `analytics` 服务块（768m、healthcheck、`extra_hosts`、env_file：`.env.common`、nacos.env、redis.env；ip2region 只读挂载）。
- Nacos：`patra-infra/nacos/dev/patra-analytics.yaml`；`nacos-verify.sh` 的服务列表。
- 网关：路由 `Path=/patra-analytics/**` + `StripPrefix=1`；Scalar 文档源；采集路径匿名放行（网关默认就是 `permitAll`，不用改安全配置）。
- 指标：经 OTLP 推给 OTel Collector，不改 `prometheus.yml`（§6）。
- 文档：根 `README.md`、`patra-api/README.md`、`patra-infra/CLAUDE.md`、`.claude/rules/project-info.md`、`patra-infra/docker/README.md` 的服务清单；compose 与 `service.Dockerfile` 注释里的「6 个服务」改成 7 个；新服务自己的 `README.md`。

## 4. 采集端点

### 4.1 接口

`POST /patra-analytics/events`（经网关；服务内路径 `/events`）

- **请求体**：`collect-batch`（1～500 条采集信封），`Content-Type: application/json`，请求体上限 **1 MiB**。
- **调用方**：门户服务端、模拟器。不要求登录、不读身份断言：`user_id` 以信封里的为准（埋点方案 §2）。IP 也以信封里的 `client_ip` 为准，不看请求的来源地址（门户代多个访客发）。
- **响应**：

| 情况 | 状态 | 响应体 |
|---|---|---|
| 收下（不论其中有几条不合格） | 202 | `{"accepted": 合格条数, "rejected": 进 dlq 的条数}` |
| 不是合法 JSON、不符合 `collect-batch`（0 条、超过 500 条、多余字段） | 400 | ProblemDetail，错误码 `ANALYTICS-0400` |
| 请求体超过 1 MiB | 413 | ProblemDetail，`ANALYTICS-0413` |
| Kafka 写不进去（§2.3） | 503 | ProblemDetail，`ANALYTICS-0503` |

### 4.2 逐条处理

整批先按 `collect-batch` 校验外壳，然后对每一条：

```text
① 预检（按顺序，第一条不过就停）
   event_name 不在目录里      → dlq: unknown_event
   source 与事件名的绑定不对   → dlq: source_mismatch
   schema_version 的 MODEL ≠ 1 → dlq: version_rejected
② 按 collect-envelope 做完整 schema 校验 → 不过：dlq: schema_invalid（带第一条错误）
③ 富化
   received_at = 本批收到的时刻（整批同一个值）
   derived_at  = received_at − (sent_at − created_at)，超出 [received_at − 24h, received_at + 5min] 时取 received_at
   UA 解析     → browser_family、os_family、device_class
   爬虫判定    → is_bot、bot_reason（§4.3）
   地区        → country_code、province（§4.4）
   去掉 client_ip、user_agent
④ 按 enriched-event 校验产物（防自己的 bug 把 IP 或坏字段带进 Kafka；不过就记错误日志与指标并进 dlq: schema_invalid，不应发生）
⑤ 写 events.raw，key = device_id
```

- 预检单独做，是因为 schema 里的 `oneOf` 失败时只会报「没有分支匹配」，分不出是哪种原因（PAP-164 终审的结论）。
- dlq 记录按 `dlq-record` 生成：`raw` 是原事件的 JSON 文本，先删掉 `client_ip`、`user_agent` 再存，超过 16,384 字符截断并置 `raw_truncated`。dlq 消息不带 key。
- 富化与校验都只在内存里做，不调外部服务；唯一的外部调用是 Redis（频率规则）和 Kafka。

### 4.3 爬虫判定

按埋点方案 §6.7，先后检查，命中即停：

1. **`ua_list`**：UA 匹配 COUNTER-Robots 列表的任一模式（不区分大小写）。启动时把 327 个模式编译好，按顺序匹配；列表从 `patra-analytics-api` 的 classpath 资源读。
2. **`ua_class`**：Yauaa 的 DeviceClass 是 `Robot`、`Robot Mobile`、`Robot Imitator`、`Spy`、`Hacker` 之一。
3. **`rate`**：同一个 `device_id` 在 5 分钟内的 `article_viewed` 超过 30 条。用 Redis 的有序集合做滑动窗口（与 identity 限流同一种写法），一段 Lua 脚本按参数分两种动作：
   - `article_viewed`：`ZREMRANGEBYSCORE` 清掉 5 分钟前的 → `ZADD`（成员是 `event_id`，分数是 `received_at` 的毫秒数；同一事件重发只占一个位置，不会把计数顶高）→ `ZCARD` → `PEXPIRE` 5 分钟。
   - 其他事件：只清过期、只 `ZCARD`，不加入。
   - 读到的数超过 30，这条事件标为 `rate`。键名 `ana:bot-rate:device:{device_id}`。
   - **Redis 出错时跳过这条规则**（事件照常收，`is_bot` 只看前两条），记一个指标。频率规则是锦上添花，不能让 Redis 故障拖垮采集。

`ua_list` / `ua_class` 命中的事件不进频率窗口。

### 4.4 地区

- ip2region v4 / v6 两个库（与 identity 相同的加载方式：启动时校验文件、`VIndexCache`、多个 searcher），查出「国家|省份|城市|ISP|ISO 码」。
- `country_code` 取 ISO 码（大写两位）；查不到、是保留地址或私有地址时为 `null`。
- `province` 只在 `country_code = CN` 时取省份字段原文（如 `浙江省`、`上海市`、`香港特别行政区`），其余为 `null`。不做 identity 那种去后缀的展示处理，展示由用的地方决定。
- 数据文件缺失时**启动失败**（与 identity 一致，避免悄悄全变成 `null`）。
- 现状提醒：公网访客的 IP 现在都是同一个内网地址（release spec 第 3 节 L），真实流量的地区会全是 `null`；模拟器自带地区分布的 IP。

### 4.5 Kafka 生产者

| 设置 | 值 |
|---|---|
| `acks` | `all` |
| `enable.idempotence` | `true` |
| `compression.type` | `zstd` |
| `linger.ms` | 10 |
| `delivery.timeout.ms` | 5000 |
| `max.block.ms` | 2000（Kafka 连不上时，发送调用最多卡 2 秒） |
| 值的序列化 | 富化事件 / dlq 记录的 JSON 文本（UTF-8 字符串） |

这些设置集中在 `infra.collect.publish.KafkaProducerSettings`，由 `DefaultKafkaProducerFactoryCustomizer` 套到 Boot 的生产者工厂上，测试用同一份；broker 地址仍来自 `spring.kafka.bootstrap-servers`（Nacos）。

**整批有一个总截止时间：从开始发送起 5 秒。**逐条调用 `send()`；任何一次 `send()` 抛异常（连不上时最多卡 `max.block.ms` 2 秒），立刻放弃本批剩下的、不再发送，返回 503。全部交出去后统一等确认，等到截止时间还没全部确认，也返回 503。所以 Kafka 不可用时，一次请求最长约 2～5 秒就返回，门户调采集端点的超时设 6 秒（PAP-173）。

## 5. 分层落点

| 东西 | 位置 |
|---|---|
| `EventName`、`EventSource`、`SchemaVersion` | `domain.kernel.model.vo` |
| 时间校正 `EventTimeCorrection`、爬虫判定组合 `BotClassification` | `domain.collect.policy` |
| 端口：`EventSchemaPort`（预检与 schema 校验）、`UserAgentPort`、`RobotListPort`、`DeviceRatePort`、`GeolocationPort`、`EventPublishPort` | `domain.collect.port.<协作者>` |
| 值对象：`CollectedEnvelope`、`EnvelopeCheck`、`EventEnrichment`、`RejectedEnvelope`、`OutboundMessage` 等（不叫 `*Event`：架构规则要求 `*Event` 是领域事件、放 `domain.event`） | `domain.collect.model.vo` |
| 用例 `CollectEventsCommand` / `CollectEventsHandler` / `CollectEventsResult` | `app.collect.usecase.collect` |
| 端口实现（networknt、Yauaa、COUNTER-Robots、Redis Lua、ip2region、KafkaTemplate） | `infra.collect.<协作者>` |
| 采集端点 `CollectController` 与请求 / 响应 | `adapter.collect.rest` |

- 事件在 domain 里的表示：信封里固定的字段用强类型（时间用 `Instant`，ID 用字符串值对象），`properties` 以不解析的 JSON 文本原样带过。domain 层的白名单其实允许 Jackson，这里不用它是因为采集链路上没有任何业务规则读属性（只有 schema 校验看它），原样带过最简单，也不会因为重新序列化改变字段顺序或数字写法。
- 采集链路不开数据库事务（不写 PG）。

## 6. 指标

按 `.claude/rules/tech/observability.md` 的统一管道：指标经 OTLP 推给 OTel Collector，再由它的 Prometheus 导出器（`:8889`，Prometheus 本来就在抓）给 Prometheus（实施时改定；原写「新开 Prometheus 拉取」与规范冲突）。

- boot 加 `micrometer-registry-otlp`，推到 `http://${PATRA_INFRA_HOST}:4318/v1/metrics`，30 秒一次；不挂 OTel agent，也不给 Prometheus 加抓取任务。测试里关掉推送。
- Prometheus 里的名字带 Collector 的命名空间前缀：`patra_analytics_collect_events_total` 等。
- 指标：

| 指标 | 类型 | 标签 |
|---|---|---|
| `analytics.collect.events` | 计数 | `outcome`（`accepted` / `rejected`）、`reason`（dlq 原因，accepted 时为 `none`）、`event_name` |
| `analytics.collect.bot.events` | 计数 | `reason`（`ua_list` / `ua_class` / `rate`） |
| `analytics.collect.batches` | 计数 | `status`（`202` / `400` / `413` / `503`） |
| `analytics.collect.publish` | 计时 | — |
| `analytics.collect.rate.rule.skipped` | 计数 | — （Redis 出错跳过频率规则的次数） |

其他服务暂不改（与本版无关）；PAP-168 的消费滞后指标沿用这条通路。

## 7. 测试

| 层 | 测什么 |
|---|---|
| domain 单元 | `SchemaVersion` 的解析与「MODEL 为 1 才接受」；`EventTimeCorrection` 的正常、过早、过晚三种；`BotClassification` 的先后顺序与「命中前两条不计频率」 |
| infra 单元 | schema 校验器把 `analytics/examples/` 下**全部样例**跑一遍：合格的都通过，不合格的都拒，且预检给出的 `reason_code` 符合文件名（如 `--source-mismatch` 是 `source_mismatch`、`--unknown-event` 是 `unknown_event`、`--version-model-2` 是 `version_rejected`，其余是 `schema_invalid`）；`--extra-top-level`、`--has-device-id` 这类靠 `unevaluatedProperties` 拦下的单独写成用例。COUNTER-Robots：Googlebot 命中、普通 Chrome 不命中。地区：已知 IP 得到 `CN` / 省份，境外 IP 得到国家码、省份为 `null`，私有地址全是 `null` |
| infra 集成（Testcontainers） | Redis 频率：同一设备第 31 条 `article_viewed` 起标 `rate`，5 分钟后恢复（用注入的 Clock 推进窗口）；同一个 `event_id` 重发不增加计数；非 `article_viewed` 事件不计入但会被标；Redis 不可用时跳过规则不报错 |
| 端到端集成（Testcontainers Kafka + Redis） | 一批里 2 条合格、1 条不合格：返回 202 `{accepted:2, rejected:1}`，`events.raw` 收到 2 条且没有 `client_ip` / `user_agent`、字段齐全、key 为设备 ID，`events.dlq` 收到 1 条且 `raw` 里没有 IP；爬虫 UA 的事件 `is_bot = true`；空批返回 400；Kafka 不可用时 6 秒内返回 503 |

**「Redis / Kafka 不可用」怎么造**：`patra-tool-test` 的容器在同一个 JVM 里被所有集成测试共用，真把它停掉会连带打挂别的测试。所以这两类用例另起一个 Spring 上下文，把 Redis / Kafka 地址指到一个不通的本机端口，不动共用容器。
| 架构 | ArchUnit 层规则与子域规则（`kernel`、`collect`）；domain 层纯净 |

## 8. 上线

部署到 mini 的步骤写进 PAP-165 的 runbook（`docs/patra/runbooks/v0.18-analytics-go-live-runbook.md`）新的一节：**合并前在 mini 的 PG 里手工建库 `patra_analytics`**（`02-create-databases.sql` 只在 PG 容器第一次初始化时执行，库不在 Flyway 会让服务第一次启动就失败，与 v0.16 建 `patra_notification` 同一步）；先部署 `pipeline` 栈（PAP-165），再由 CD 部署本服务（合并到 main 时），网关随同一次 CD 重新部署以加载新路由；在 XXL-Job admin 里手工建执行器分组 `patra-analytics`；确认容器挂上了 ip2region 目录。部署前在会话里先请用户确认。

## 9. 我的决定（等你审）

1. **Kafka 不可用时返回 503，门户丢弃不重试**，模拟器重试（§2.3）。
2. **采集端点不看身份断言、不看请求来源 IP**，`user_id` 与 `client_ip` 以信封为准（§4.1）。
3. **产物再按 `enriched-event` 校验一次**，防自己的 bug 把 IP 带进 Kafka（§4.2 ④）。
4. **Redis 出错时跳过频率规则**，不拖垮采集（§4.3）。
5. **省份存 ip2region 原文**，不做展示处理（§4.4）。
6. ~~本服务新开 Prometheus 拉取通路~~ → 实施时改为**经 OTLP 推给 OTel Collector**（§6，遵守可观测性规范）。
7. **本 Issue 只声明 `kernel`、`collect` 两个子域**，其余子域由各自 Issue 加入（§3.1）。
8. **PG 库与 XXL 执行器现在就接上**（Issue 要求），但没有表、没有任务。
9. 端口 6700 / 6701。
10. **JSON Schema 校验器用 networknt 3.0.x**（唯一用 Jackson 3 的版本线），具体版本以在 Boot 的 Jackson 3.1.7 上跑通全部样例为准（§3.1）。
11. ~~api 模块放错误码与资源路径常量~~ → 实施时改为 **api 只有资源，infra 用 `runtimeOnly` 依赖它**（§3.1）。
12. **`EventName` 等放底座**，虽然本 Issue 只有一个子域在用，同一版本的 `ingest`、`history` 马上会用（§3.1）。
13. **Kafka 写入整批 5 秒截止，第一次 `send()` 失败就放弃整批**；门户超时 6 秒（§4.5）。
14. **频率窗口的成员用 `event_id`**，重发不顶高计数；非文献浏览事件只读不计（§4.3）。
15. **「依赖不可用」的测试另起上下文、指向不通的端口**，不停共用容器（§7）。
