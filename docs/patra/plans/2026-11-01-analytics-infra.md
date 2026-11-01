# ClickHouse 与 Kafka 基础设施（PAP-165）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task（本版约定：当前会话内联执行，不派子代理）. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** mini 上可部署的 ClickHouse 与 Kafka（低内存、磁盘有上限、Grafana 可查），以及本机能起这两个容器的 Testcontainers 初始化器。

**Architecture:** 新 compose 子栈 `pipeline`（project `patra-pipeline`）放 clickhouse、kafka、kafka-init；Grafana 在 observability 栈里装插件、provisioning 加数据源；`patra-tool-test` 加两个初始化器，照 `RabbitMQContainerInitializer` 的样子写。部署手动，不进 CD。

**Tech Stack:** Docker Compose、ClickHouse 26.8 LTS、Kafka 4.3.2（KRaft）、Grafana 12.3.0 + grafana-clickhouse-datasource 4.22.1、Testcontainers 1.21.4、JUnit 5 / AssertJ / Awaitility。

**Spec:** `docs/patra/specs/2026-11-01-analytics-infra-design.md`

## Global Constraints

- 镜像：`clickhouse/clickhouse-server:26.8.36.3`；mini 用 `apache/kafka:4.3.2`，测试用 `apache/kafka-native:4.3.2`；`grafana/grafana:12.3.0` 不变。
- 内存上限：clickhouse `2g`；kafka `1g`、`KAFKA_HEAP_OPTS=-Xms512m -Xmx512m`。
- topic：`events.raw` 3 分区 / `retention.ms=172800000` / `retention.bytes=1073741824` / `segment.bytes=268435456` / `segment.ms=21600000`；`events.dlq` 1 分区 / 同 `retention.ms` / `retention.bytes=536870912` / `segment.bytes=134217728` / `segment.ms=21600000`。
- 密钥只在 `~/.patra/secrets/`：`clickhouse.env`（`CLICKHOUSE_PATRA_PASSWORD`）、`clickhouse-grafana.env`（`CLICKHOUSE_GRAFANA_PASSWORD`）。容器环境里绝不出现 `CLICKHOUSE_USER` / `CLICKHOUSE_PASSWORD`。
- 库名 `patra_analytics`；账号 `patra`（`GRANT ALL ON patra_analytics.*`）、`grafana`（`GRANT SELECT ON patra_analytics.*`，`readonly=2`）。
- 测试容器属性：`spring.kafka.bootstrap-servers`；`patra.clickhouse.url` / `username` / `password`。
- 不在 mini 执行任何命令；本机演练用 scratchpad 里的临时 `HOME` 与 `-p` 独立 project 名，演练后清理容器与数据。
- 提交前 `git diff --cached` 只含本 Issue 路径；commit subject 中文动词开头，body 每行 ≤ 100 字符。

## Review Focus

1. 镜像入口脚本看到 `CLICKHOUSE_PASSWORD` 会改 `default` 账号 → 演练时核对 `default` 从容器外登录被拒（Task 3）。
2. Kafka 通告地址：MacBook 用 `linqibins-mac-mini:9092`、同网络容器用 `patra-kafka:29092` → 演练时分别从宿主机与 `patra-net` 内的容器各收发一次（Task 4）。
3. `kafka-init` 重跑：已存在的 topic 配置被改回仓库值、分区数改小时报错退出 → 演练时手改一个配置后重跑核对（Task 4）。
4. 压满 `events.raw`：写入量超过上限后，分区目录总大小回落并稳定在 ~3.75 GiB（Task 4）。
5. 容器重建后账号、库、topic、Grafana 插件都还在（Task 3、4、5 各自重建一次核对）。

---

### Task 1: Kafka 测试容器

**Files:**
- Modify: `gradle/libs.versions.toml`（`testcontainers-kafka = { module = "org.testcontainers:kafka" }`）
- Modify: `patra-tool/patra-tool-test/build.gradle.kts`（`api(libs.testcontainers.kafka)`；`integrationTestImplementation("org.apache.kafka:kafka-clients")`）
- Modify: `.../container/ContainerType.java`（加 `KAFKA`，注释写镜像）
- Create: `.../container/initializer/KafkaContainerInitializer.java`
- Test: `src/integrationTest/java/dev/patra/tool/test/container/initializer/KafkaContainerInitializerIT.java`

**Interfaces:**
- Produces: `public static KafkaContainer getKafkaContainer()`（`org.testcontainers.kafka.KafkaContainer`）；常量 `private static final String KAFKA_IMAGE = "apache/kafka-native:4.3.2";`（CI 按这行 grep）；`initialize` 写 `spring.kafka.bootstrap-servers`。

- [x] **Step 1: 写失败的 IT**
  - `should_start_kafka_and_publish_bootstrap_servers`：`new KafkaContainerInitializer().initialize(context)` 后 `spring.kafka.bootstrap-servers` 非空；用 `AdminClient` 建 topic `pap165.it`（1 分区）；`KafkaProducer<String,String>` 发 `("k","hello")` 并 `get()`；`KafkaConsumer` `assign` 到分区 0、`seekToBeginning`，Awaitility 30 秒内 `poll` 到一条，`value()` 为 `hello`。
  - `should_reuse_the_same_container`：两次 `getKafkaContainer()` `isSameAs`。
- [x] **Step 2: 跑 IT，确认因缺类编译失败**：`./gradlew :patra-tool:patra-tool-test:integrationTest --tests '*KafkaContainerInitializerIT'`
- [x] **Step 3: 实现初始化器**（同 `RabbitMQContainerInitializer`：`LOCK` + `ContainerRegistry`，日志打 bootstrap 地址）
- [x] **Step 4: 跑 IT 通过**（同上命令）
- [x] **Step 5: 提交** `feat(test): 新增 Kafka 测试容器初始化器`

### Task 2: ClickHouse 测试容器

**Files:**
- Modify: `gradle/libs.versions.toml`（`testcontainers-clickhouse = { module = "org.testcontainers:clickhouse" }`）
- Modify: `patra-tool/patra-tool-test/build.gradle.kts`（`api(libs.testcontainers.clickhouse)`）
- Modify: `ContainerType.java`（加 `CLICKHOUSE`）
- Create: `.../initializer/ClickHouseContainerInitializer.java`
- Test: `.../initializer/ClickHouseContainerInitializerIT.java`

**Interfaces:**
- Produces: `public static ClickHouseContainer getClickHouseContainer()`（`org.testcontainers.clickhouse.ClickHouseContainer`，`withDatabaseName("patra_analytics")`）；常量 `private static final String CLICKHOUSE_IMAGE = "clickhouse/clickhouse-server:26.8.36.3";`；`initialize` 写 `patra.clickhouse.url=http://{host}:{mapped 8123}`、`patra.clickhouse.username`、`patra.clickhouse.password`。

- [x] **Step 1: 写失败的 IT**
  - `should_start_clickhouse_and_publish_connection_properties`：三个属性非空；用 `HttpClient`（`proxy(HttpClient.Builder.NO_PROXY)`）带 `X-ClickHouse-User` / `X-ClickHouse-Key` 头，POST `CREATE TABLE patra_analytics.pap165_it (id UInt32, name String) ENGINE = MergeTree ORDER BY id`、`INSERT INTO ... VALUES (1, 'hello')`，再 `SELECT id, name FROM patra_analytics.pap165_it FORMAT TabSeparated` 得 `1\thello\n`；每个请求状态码 200。
  - `should_reuse_the_same_container`。
- [x] **Step 2: 跑 IT 确认失败**：`--tests '*ClickHouseContainerInitializerIT'`
- [x] **Step 3: 实现初始化器**
- [x] **Step 4: 跑 IT 通过**
- [x] **Step 5: 提交** `feat(test): 新增 ClickHouse 测试容器初始化器`

### Task 3: pipeline 栈的 ClickHouse

**Files:**
- Create: `patra-infra/docker/docker-compose.pipeline.yaml`（先只有 clickhouse）
- Create: `patra-infra/docker/clickhouse/config.d/patra.xml`、`users.d/patra.xml`、`initdb/01-database.sql`
- Create: `patra-infra/docker/secrets/clickhouse.env.example`、`clickhouse-grafana.env.example`
- Modify: `patra-infra/scripts/compose-all.sh`（`ORDER=(core pipeline storage search observability tailnet jobs apps)`，注释与用法同步）
- Modify: `patra-infra/scripts/init-volumes.sh`（建 `clickhouse/data`、`clickhouse/logs`、`kafka/data`）

要点（取值见 spec 4.1）：容器 `patra-clickhouse`、hostname 同名、端口 `8123`/`9000`、`limits.memory: 2g`、`ulimits.nofile 262144`、`env_file` 两个密钥文件 `required: true`、健康检查 `/ping`；`config.d` 含 Altinity 低内存项、缓存缩到 128/32/16 MiB、关多余系统日志（按 26.8 镜像实际默认开启的列表）、`query_log`/`part_log` 7 天 TTL、`storage_configuration` 默认磁盘 `keep_free_space_bytes` 22 GB；`users.d` 两个账号 `from_env`。

- [x] **Step 1: 写验收脚本**（scratchpad，非仓库）：临时 `HOME` 放假密钥；`docker compose -p pap165-spike -f docker-compose.pipeline.yaml up -d clickhouse`；断言：容器 healthy；`patra` 能建表写读 `patra_analytics`；`grafana` 能 `SELECT`、`INSERT` 报 `ACCESS_DENIED`；`default` 从容器外登录失败、容器内 `clickhouse-client -q 'SELECT 1'` 成功；`system.disks` 的 `keep_free_space` = 22 GB；`system.tables` 里关掉的日志表不存在、`query_log` 存在；`docker stats` < 2 GiB；`--force-recreate` 后再跑一遍。
- [x] **Step 2: 在没有 compose 文件时跑，确认失败**
- [x] **Step 3: 写 compose、XML、SQL、模板、脚本改动**
- [x] **Step 4: 跑验收脚本全部通过**；`docker compose ... config --quiet` 通过；记录空闲内存
- [x] **Step 5: 提交** `feat(infra): 新增 pipeline 子栈的 ClickHouse`

### Task 4: pipeline 栈的 Kafka 与建 topic 边车

**Files:**
- Modify: `patra-infra/docker/docker-compose.pipeline.yaml`（加 kafka、kafka-init）
- Create: `patra-infra/docker/kafka/init-topics.sh`（边车执行：读一张 topic 表，`--create --if-not-exists`、`--alter --partitions`（只增）、`kafka-configs.sh --alter --add-config`）

要点（spec 4.2）：`patra-kafka`、hostname 同名、`CLUSTER_ID` 固定、三个监听器、`EXTERNAL` 通告 `${PATRA_INFRA_HOST:-linqibins-mac-mini}:9092`、只映射 9092、broker 配置（副本数 1、关自动建 topic、检查间隔 60 秒、初始重平衡 0）、健康检查 `KAFKA_HEAP_OPTS=-Xmx64m` 跑 `kafka-broker-api-versions.sh` 间隔 30 秒；边车 `depends_on: kafka: service_healthy`、`restart: "no"`、`KAFKA_HEAP_OPTS=-Xmx128m`。

- [x] **Step 1: 写验收脚本**：`PATRA_INFRA_HOST=localhost` 起 kafka + kafka-init；断言：边车退出码 0；`kafka-topics.sh --describe` 分区数 3 / 1；`kafka-configs.sh --describe` 五项与 Global Constraints 一致；宿主机经 9092、`patra-net` 内临时容器经 `patra-kafka:29092` 各收发一条；手改 `events.raw` 的 `retention.ms` 后重跑边车被改回；把脚本里分区数临时改成 2 重跑，边车非零退出；自动建 topic 被拒（往不存在的 topic 发送报错）；`--force-recreate` 后 topic 与数据仍在；`docker stats` < 1 GiB。
- [x] **Step 2: 压满测试脚本**：`kafka-producer-perf-test.sh --topic events.raw --num-records <约 5 GiB / 1 KiB> --record-size 1024 --throughput -1` 写两轮约 7 GiB，期间每 30 秒记 `du -sb` 三个分区目录之和；断言写完 2 分钟内总和 ≤ 4 GiB 且不再增长。
- [x] **Step 3: 跑脚本确认失败（还没有 kafka 服务）**
- [x] **Step 4: 写 compose 与 `init-topics.sh`**
- [x] **Step 5: 跑两个脚本全部通过**，记录 `du` 曲线与内存峰值
- [x] **Step 6: 提交** `feat(infra): 新增 pipeline 子栈的 Kafka 与两个 topic`

### Task 5: Grafana 的 ClickHouse 数据源

**Files:**
- Modify: `patra-infra/docker/docker-compose.observability.yaml`（grafana：`GF_PLUGINS_PREINSTALL_SYNC: grafana-clickhouse-datasource@4.22.1`、`env_file` `clickhouse-grafana.env` required；内存上限按实测）
- Modify: `patra-infra/docker/grafana/provisioning/datasources/datasources.yaml`（`deleteDatasources` 加 `ClickHouse`；数据源见 spec 4.3）

- [x] **Step 1: 写验收脚本**：在同一演练 project 里起一个按 observability 定义的 grafana（只这一个服务，`depends_on` 去掉的覆盖文件放 scratchpad）；断言 `GET /api/datasources/uid/clickhouse` 返回类型 `grafana-clickhouse-datasource`；`POST /api/ds/query` 对 ClickHouse 执行 `SELECT 1` 得到 1；`GET /api/datasources/uid/clickhouse/health` 为 OK；重建后插件仍在、不重新下载；记录内存。
- [x] **Step 2: 跑确认失败**
- [x] **Step 3: 改 compose 与 provisioning**
- [x] **Step 4: 跑通过**
- [x] **Step 5: 提交** `feat(infra): 给 Grafana 加 ClickHouse 数据源`

### Task 6: CI 与变更路由

**Files:**
- Modify: `.github/workflows/ci.yml`（`env` 加 `CLICKHOUSE_IMAGE`、`KAFKA_VERSION`；preflight 加检查，见 spec 4.6）
- Modify: `patra-infra/cd/detect-changes.sh`（`patra-infra/docker/clickhouse/*`、`patra-infra/docker/kafka/*` 归纯基建）
- Test: `patra-infra/cd/detect-changes.test.sh`

- [x] **Step 1: 在 `detect-changes.test.sh` 加用例**：只改 `patra-infra/docker/clickhouse/config.d/patra.xml` 与 `patra-infra/docker/kafka/init-topics.sh` 时后端单元为空、不判 docs-only。
- [x] **Step 2: `bash patra-infra/cd/detect-changes.test.sh` 确认新用例失败**
- [x] **Step 3: 改 `detect-changes.sh`**
- [x] **Step 4: 测试通过**
- [x] **Step 5: 把 preflight 的镜像检查段抽到本机跑一遍**（`bash -c` 执行同一段脚本，带 workflow 的 env），确认通过；把常量故意改错一次确认报错
- [x] **Step 6: 提交** `ci: 镜像版本检查覆盖 ClickHouse 与 Kafka`

### Task 7: 文档与 runbook

**Files:**
- Modify: `patra-infra/docker/README.md`（结构、密钥表与生成命令、服务 URL、新增「ClickHouse」「Kafka」两节、选择性启动、资源表、依赖图）
- Modify: `patra-infra/CLAUDE.md`（子栈表、非显性约束加 Kafka 集群 ID / 通告地址 / ClickHouse 变量名三条）
- Modify: `patra-tool/patra-tool-test/README.md`（初始化器清单）
- Create: `docs/patra/runbooks/v0.18-analytics-go-live-runbook.md`（spec 第 7 节的结构；附本机演练记录的内存与磁盘实测）

- [x] **Step 1: 写文档**
- [x] **Step 2: 核对**：README 里每条命令在本机演练里跑过或语法检查过；runbook 的验收命令与 AC 一一对应
- [x] **Step 3: 提交** `docs(infra): 补 ClickHouse 与 Kafka 的部署手册`

### Task 8: 全量验证与收尾

- [x] `./gradlew :patra-tool:patra-tool-test:check :patra-tool:patra-tool-test:integrationTest` 通过
- [x] `./gradlew dumpModuleGraph -q` 后 `git diff --quiet patra-infra/cd/module-graph.json`，有变化则提交
- [x] 清理演练容器、网络、scratchpad 数据目录
- [x] spec 状态行改「已实施，待 mini 部署」，附提交 SHA；提交 `docs(spec): 标记 PAP-165 工程 spec 已实施`
