# ClickHouse 与 Kafka 基础设施（PAP-165）工程 spec

> **Issue**：Linear PAP-165，v0.18 Analytics，Milestone Pipeline Ready。
> **产品定义**：release spec `docs/patra/release-specs/v0.18-analytics.md` 决策 C（内存预算）、D（Kafka）、E（入库）、G（数据分层）、J（保留期）。
> **决策授权**：两处安全与镜像问题经编排会话请用户拍板（第 9 节）；其余是我定的，同样列在第 9 节。
> **状态**：已实施，待 mini 部署（2026-11-01，计划 `docs/patra/plans/2026-11-01-analytics-infra.md`，部署按 `docs/patra/runbooks/v0.18-analytics-go-live-runbook.md`）。本机演练结果记在 runbook 第 0 节
> **创建日期**：2026-11-01

---

## 1. 要做什么

在 mini 上起一个单节点 ClickHouse 和一个单节点 Kafka，内存有硬上限，Kafka 按大小清理、ClickHouse 留够磁盘余量才写（4.1、4.2 节）；Grafana 能查 ClickHouse；本机测试能用 Testcontainers 起这两个容器。这一版后面的采集端点（PAP-167）、入库消费者（PAP-168）、模拟器（PAP-171）、运营面板（PAP-172）都建在它们上面。

本 Issue 只交付「地基」：容器、账号、库、topic、数据源、测试容器、部署手册。表结构、物化视图、TTL、消费者、指标都不在这里（第 3 节）。

## 2. 先讲清几个概念

### 2.1 ClickHouse 是什么，为什么怕内存小

**是什么**：列式数据库。PG 把一行的所有字段放在一起，适合「按 ID 取一行、改一行」；ClickHouse 把一列的值放在一起压缩存放，适合「扫一亿行、只看三列、算个总数」。行为数据的查询几乎都是后一种。

**为什么默认配置吃内存**：它默认按「一台机器只跑它」来配，各种缓存（标记缓存、解压缓存）、后台合并线程、十几张系统日志表都按大机器的量开。放进 2 GB 的容器，空闲时就能吃掉一大半，一跑查询就被 OOM 杀掉。

**业界怎么做**：Altinity（最大的 ClickHouse 服务商之一）的知识库给了一份「低内存环境」样例，思路是三件事：缩缓存、限并发、关掉用不到的系统日志。我们照它做，再按 2 GB 把几个数往下调（第 4.1 节）。

### 2.2 Kafka 的分区、段文件与「按大小清理」

**分区（partition）**：一个 topic 被切成几份，每份是一个只追加的日志文件夹。分区是并行的单位：同一个消费组里，一个分区同一时刻只给一个消费者读，所以 3 个分区最多 3 个消费者并行。分区数只能加不能减。

**段文件（segment）**：每个分区的日志又切成一段段文件，正在写的那段叫活动段。Kafka 清理旧数据时**只删整段、而且只删已经写满关闭的段**。

**按大小清理（`retention.bytes`）的坑**：这个上限是**每个分区**的，不是整个 topic 的；而且因为只删整段，实际占用会比上限多出最多一个段的大小，再加上两次清理检查之间新写入的量。所以「5 GB 总上限」要这样算（第 4.2 节）：

```
最坏占用 ≈ 分区数 × (retention.bytes + segment.bytes) + 检查间隔内的写入量
```

**按时间清理（`retention.ms`）同理**：一段文件里最新的一条也过期了才删这段。流量小时一段文件要很久才写满，所以要用 `segment.ms` 让它按时间滚动，否则「保留 2 天」实际可能是好几天。

### 2.3 KRaft：Kafka 不再需要 ZooKeeper

以前的 Kafka 要另起一个 ZooKeeper 集群存元数据（有哪些 topic、谁是分区的主）。Kafka 4.0 起只支持 KRaft：元数据由 Kafka 自己的「控制器」用 Raft 协议管理。单节点时同一个进程既当控制器又当 broker（`process.roles=broker,controller`）。代价是要固定一个**集群 ID**：数据目录第一次格式化时写入，之后每次启动都要对得上，和 RabbitMQ 要固定 hostname 是同一类坑。

### 2.4 Kafka 的「两个地址」：监听地址与通告地址

客户端连 Kafka 分两步：先连你给的引导地址（bootstrap），broker 回一份元数据，里面写着「分区的主在哪个地址」，客户端之后**改连元数据里的地址**。这个地址叫通告地址（advertised listener）。

mini 上有两类客户端，它们眼里 broker 的地址不同：

| 客户端 | 怎么到 broker | 用哪个监听器 |
|---|---|---|
| MacBook 上 IDEA 起的服务、模拟器 | tailnet → mini 宿主机 9092 → 容器 | `EXTERNAL`，通告 `linqibins-mac-mini:9092` |
| mini 上 apps 栈的容器（采集服务、入库消费者） | 同上：apps 容器靠 `extra_hosts: linqibins-mac-mini:host-gateway` 回到宿主机 | `EXTERNAL` |
| 同网络里的建 topic 边车、健康检查 | `patra-net` 上直接按容器名 | `INTERNAL`，通告 `patra-kafka:29092` |

只开一个监听器、通告 `linqibins-mac-mini`，边车和健康检查也能用（它们同样可以加 `extra_hosts`），但走宿主机绕一圈；分开两个监听器是业界的标准写法（Confluent、Bitnami 的 compose 样例都这样），内外互不影响。

### 2.5 为什么测试容器用 `kafka-native`，mini 用 `kafka`

`apache/kafka` 是普通 JVM 版；`apache/kafka-native` 是用 GraalVM 提前编译成本地程序的版本，启动不到 1 秒、内存几十 MB，官方明确说只用于开发和测试。mini 上要长期跑、要按 release spec 决策 D 用 512 MB 堆，所以用 JVM 版；测试用 native 版省时间。两者是同一份 Kafka 源码、同一个版本号，CI 检查版本号一致（第 4.6 节）。

## 3. 范围

**做**：

- 新子栈 `pipeline`（compose project `patra-pipeline`）：`clickhouse`、`kafka`、`kafka-init`（建 topic 的一次性边车）。
- ClickHouse 低内存配置、两个账号、建 `patra_analytics` 库、健康检查、内存上限。
- Kafka KRaft 单节点、两个 topic 与它们的保留策略、内存上限、健康检查。
- Grafana 安装 ClickHouse 插件、provisioning 加数据源。
- `patra-tool-test`：`ContainerType` 加两项、`ClickHouseContainerInitializer`、`KafkaContainerInitializer`、版本目录、示例集成测试。
- CI 镜像版本检查覆盖新镜像；`detect-changes.sh` 把新配置目录归为纯基建路径。
- `init-volumes.sh`、`compose-all.sh`、README、`patra-infra/CLAUDE.md`、密钥模板、部署 runbook。

**不做**：

- ClickHouse 的表、物化视图、TTL、注销删除：PAP-168。
- Kafka 生产者与消费者、消费滞后指标、Prometheus 抓取 ClickHouse / Kafka 指标：PAP-167 / PAP-168。
- 在 mini 上部署：本会话不碰 mini，部署命令与预期写进 runbook（第 7 节），由编排会话安排执行；AC 里「mini 上 `docker stats`」「压满 5 GB」「Grafana 能查」三条要部署后在 mini 实测。

## 4. 设计

### 4.1 ClickHouse

**镜像**：`clickhouse/clickhouse-server:26.8.36.3`。ClickHouse 每年 3 月、8 月的版本是长期支持版（LTS，支持一年），其余月份版本只支持 3 个月。26.8 是当前最新的 LTS。

**容器**：`patra-clickhouse`，`restart: unless-stopped`，`deploy.resources.limits.memory: 2g`（决策 C），`ulimits.nofile: 262144`（官方 compose 样例的值，打开文件数默认上限太小会报 `Too many open files`）。

**端口**：`8123:8123`（HTTP 接口：应用、Grafana、`curl` 都走它）、`9000:9000`（原生 TCP：MacBook 上的 `clickhouse client`）。宿主机 9000 没被占用（MinIO 映射的是 19000）。只在 tailnet 与局域网可达，不进 Cloudflare Tunnel。

**数据**：`${HOME}/.patra/docker/clickhouse/data:/var/lib/clickhouse`，`.../clickhouse/logs:/var/log/clickhouse-server`。

**配置文件**（仓库 `patra-infra/docker/clickhouse/`，只读挂进容器）：

- `config.d/patra.xml`（服务器级）：
  - 缓存：标记缓存 128 MiB、索引标记缓存 32 MiB、解压缓存 16 MiB（Altinity 样例是给 4~8 GB 机器的 256 / 64 / 16 MiB，按 2 GB 减半）；其他默认很大的缓存一并调小（实测核对后写全）。
  - 内存：`max_server_memory_usage_to_ram_ratio` 0.75。ClickHouse 能读出容器的 cgroup 上限，所以是 2 GB × 0.75 ≈ 1.5 GB，剩下的留给不受它统计的内存。
  - 并发：`max_concurrent_queries` 8、`max_connections` 64，后台合并、拉取、调度各线程池按 Altinity 样例缩到最小，`merge_tree` 的单次合并上限 1 GB。
  - 端口：关掉 MySQL、PostgreSQL 兼容端口。
  - 磁盘兜底：默认磁盘设 `keep_free_space_bytes` 22 GB。ClickHouse 写入和合并前要先「预留」空间，剩余空间低于这个值时拒绝预留，写入报 `NOT_ENOUGH_SPACE`，而不是一直写到看门狗的 20 GB 红线以下。这是兜底，不是容量规划：触发时 PAP-168 的消费者写不进去，事件在 Kafka 里等着（最多 2 天），腾出空间后自动补上。
  - 系统日志：关掉 `metric_log`、`asynchronous_metric_log`、`trace_log`、`text_log`、`query_thread_log`、`query_views_log`、`processors_profile_log`、`opentelemetry_span_log` 等（实测核对 26.8 默认开了哪些）；**保留 `query_log` 与 `part_log`，加 7 天 TTL**：前者是排查慢查询与 PAP-172 数据质量面板的依据，后者能看到每次写入生成了几个数据片段、合并了哪些，是理解 `TOO_MANY_PARTS` 的现场。
- `users.d/patra.xml`（账号与查询级限制）：
  - `default` 配置档：每条查询最多 2 线程、1 GiB 内存，聚合与排序超过 512 MiB 落盘（Altinity 样例是 3 GB，按比例缩）。
  - 账号 `patra`：应用用，`GRANT ALL ON patra_analytics.*`。
  - 两个账号都能读 `system.parts`、`system.part_log`、`system.query_log`：数据片段数与磁盘占用、写入与合并记录、慢查询，PAP-168 / 171 / 172 要用。
  - 账号 `grafana`：只读，`GRANT SELECT ON patra_analytics.*`，配置档 `readonly=2`（不能写，但允许 Grafana 插件设置查询参数），单条查询最多 60 秒。
  - 密码不进仓库：`<password from_env="..."/>` 从容器环境变量读，变量来自 `${HOME}/.patra/secrets/` 下的两个文件（4.4 节）。变量名**不能**叫 `CLICKHOUSE_PASSWORD`：镜像入口脚本看到它就会把 `default` 账号改成用这个密码、并放开网络访问。
  - 镜像自带的 `default` 账号：容器里不出现 `CLICKHOUSE_USER` / `CLICKHOUSE_PASSWORD` 时，镜像入口脚本把它限制为只能从容器内本机登录，留给健康检查和 `docker exec` 维护用。
- `initdb/01-database.sql`：`CREATE DATABASE IF NOT EXISTS patra_analytics`。镜像只在数据目录为空时执行。

**健康检查**：`wget -qO- http://127.0.0.1:8123/ping`（`/ping` 不要账号，返回 `Ok.`）。

### 4.2 Kafka

**镜像**：`apache/kafka:4.3.2`（4.3 的第二个补丁版；4.4.0 十天前才发布，先不追）。

**容器**：`patra-kafka`，hostname 固定 `patra-kafka`，`restart: unless-stopped`，`limits.memory: 1g`，`KAFKA_HEAP_OPTS=-Xms512m -Xmx512m`（决策 D）。堆外还有元空间、线程栈、网络缓冲，以及 Kafka 重度依赖的页缓存（容器内存统计里可回收的那部分），1 GB 上限留给它们。

**KRaft 单节点**：`node.id=1`、`process.roles=broker,controller`、控制器监听 `CONTROLLER://:9093`（只在容器内，不映射：宿主机 9093 被 alertmanager 占了）。`CLUSTER_ID` 写死在 compose 里（不是密钥，只是数据目录的身份）。

**监听器**（2.4 节）：

```
listeners            = INTERNAL://:29092, EXTERNAL://:9092, CONTROLLER://:9093
advertised.listeners = INTERNAL://patra-kafka:29092, EXTERNAL://${PATRA_INFRA_HOST:-linqibins-mac-mini}:9092
inter.broker.listener.name = INTERNAL
```

端口映射只有 `9092:9092`。通告地址沿用 MinIO 的写法，可用 `PATRA_INFRA_HOST` 覆盖（本机验证时设成 `localhost`）。

**broker 级配置**：内部 topic（消费位点、事务状态、共享组状态）副本数都设 1；`auto.create.topics.enable=false`（写错 topic 名直接报错，不会悄悄建一个没有上限的新 topic）；`log.retention.check.interval.ms=30000`、`log.segment.delete.delay.ms=5000`（超出上限的部分要等清理检查发现、再等删除延迟才真删，默认 5 分钟 + 1 分钟；本机演练时先缩到 1 分钟，不限速写入下峰值仍拖了约 100 秒，再缩到 30 秒 + 5 秒）；`group.initial.rebalance.delay.ms=0`。

**认证**：不设（PLAINTEXT），端口只在 tailnet 与局域网可达，不进 Cloudflare Tunnel（用户 11/01 确认，第 9 节）。

**生产环境要怎么开**（本版不做，写在这里供对照）：Kafka 的安全分三层，业界生产环境三层都开：

1. **加密**（TLS）：监听器协议从 `PLAINTEXT` 换成 `SASL_SSL`，broker 配证书，客户端信任 CA。
2. **认证**（SASL）：常用 `SCRAM-SHA-512`。账号存在 KRaft 元数据里，用 `kafka-configs.sh --alter --add-config 'SCRAM-SHA-512=[password=...]' --entity-type users --entity-name analytics-collector` 创建；客户端配 `security.protocol=SASL_SSL`、`sasl.mechanism=SCRAM-SHA-512`、`sasl.jaas.config=...ScramLoginModule required username=... password=...;`。每个服务一个账号。
3. **授权**（ACL）：broker 开 `authorizer.class.name=org.apache.kafka.metadata.authorizer.StandardAuthorizer`（KRaft 下的实现）、`allow.everyone.if.no.acl.found=false`，再按最小权限授权，例如采集服务只能写 `events.raw` 与 `events.dlq`：`kafka-acls.sh --add --allow-principal User:analytics-collector --operation Write --topic events.raw`；入库消费者只能读 `events.raw` 并使用自己的消费组：`--operation Read --topic events.raw --group analytics-ingest`。

**数据**：`${HOME}/.patra/docker/kafka/data:/var/lib/kafka/data`。镜像以 uid 1000 运行；本机演练确认 OrbStack 的 bind mount 不用改目录权限就能写（Elasticsearch 当初要 `chmod 777`，这里不需要），`init-volumes.sh` 只建目录。

**topic**（由 `kafka-init` 边车建，见下）：

| topic | 分区 | `retention.ms` | `retention.bytes`（每分区） | `segment.bytes` | `segment.ms` | 最坏占用 |
|---|---|---|---|---|---|---|
| `events.raw` | 3 | 2 天 | 1 GiB | 256 MiB | 6 小时 | 3 × 1.25 GiB = 3.75 GiB + 约 35 秒的写入量 |
| `events.dlq` | 1 | 2 天 | 512 MiB | 128 MiB | 6 小时 | 0.625 GiB + 约 35 秒的写入量 |

- 分区数 3：PAP-168 的消费者最多 3 个并行，单节点上足够，也给模拟器的吞吐留了余地。
- `segment.ms` 6 小时：流量小时让段文件按时间滚动，「保留 2 天」最多拖到 2 天 6 小时。
- 「5 GB 上限」按整个 Kafka 算：两个 topic 合计最坏约 4.4 GiB，加上内部 topic 与检查间隔内的写入，仍在 5 GB 内。mini 磁盘剩 41 GB，看门狗红线 20 GB。
- ClickHouse 的磁盘兜底见 4.1 节 `keep_free_space_bytes`；正常的增长由 PAP-168 的 90 天 TTL 管，占用记进 PAP-171 的压测报告。

**`kafka-init` 边车**：同一个镜像，`depends_on: kafka (service_healthy)`，执行完退出（`restart: "no"`）。对每个 topic 先 `kafka-topics.sh --create --if-not-exists`，再 `kafka-configs.sh --alter` 把配置写成仓库里的值——这样改了仓库里的保留策略，重跑 `compose-all.sh up pipeline` 就生效。分区数只增不减：仓库里改大会 `--alter --partitions` 扩容，改小时报错退出（不静默）。命令行工具也是 JVM，边车里把 `KAFKA_HEAP_OPTS` 设成 128 MiB。

**健康检查**：命令行工具每次都要起一个 JVM，放在 1 GB 容器里每 10 秒跑一次太重。用 `kafka-broker-api-versions.sh` 连 `INTERNAL` 监听器，`KAFKA_HEAP_OPTS=-Xmx64m`，间隔 30 秒（实测内存峰值后定）。

### 4.3 Grafana

- **插件**：Grafana 12 起用 `GF_PLUGINS_PREINSTALL_SYNC=grafana-clickhouse-datasource@4.22.1`（Grafana 启动前同步装好，provisioning 能立刻用上）。插件装在 `/var/lib/grafana/plugins`，那是已有的 bind mount，重建容器不用重装。钉版本：不钉时每次启动会自动升小版本。4.22.1 是一个月前的版本、要求 Grafana ≥ 11.6，当前 12.3.0 满足。
- **装不上时**：mini 的容器访问 grafana.com 要经过 mini 的出向网络（实测前不确定）。runbook 写备用做法：MacBook 下载插件 zip、`scp` 到 mini 的 `~/.patra/docker/grafana/data/plugins/` 解压，再去掉环境变量重启。
- **数据源**（`provisioning/datasources/datasources.yaml` 加一项，也加进 `deleteDatasources`）：`type: grafana-clickhouse-datasource`、`uid: clickhouse`、`host: patra-clickhouse`、`port: 8123`、`protocol: http`、`defaultDatabase: patra_analytics`、`username: grafana`、`secureJsonData.password: $CLICKHOUSE_GRAFANA_PASSWORD`。provisioning 文件支持环境变量展开，grafana 容器加 `env_file: ${HOME}/.patra/secrets/clickhouse-grafana.env`（`required: true`）：只拿只读账号的密码，拿不到读写账号的。
- **内存**：插件是一个独立的 Go 后端进程。本机实测首次启动约 200 MiB（256 MiB 上限的 80%），30 个并发查询各返回 20 万行时插件进程被 OOM 杀掉（Grafana 自动拉起）。上限调到 384 MiB；面板要在 ClickHouse 里聚合好再返回。

### 4.4 密钥：`~/.patra/secrets/clickhouse.env` 与 `clickhouse-grafana.env`

```
# clickhouse.env
CLICKHOUSE_PATRA_PASSWORD=<64 位十六进制>    # 账号 patra，PAP-167/168 的服务与模拟器用
# clickhouse-grafana.env
CLICKHOUSE_GRAFANA_PASSWORD=<64 位十六进制>  # 账号 grafana，只读
```

拆成两个文件是为了最小权限：grafana 容器只加载后者。

- MacBook 与 mini 放同一份（本机 IDEA 起的服务连 mini 的 ClickHouse）；模板 `secrets/clickhouse.env.example`、`secrets/clickhouse-grafana.env.example`。
- 谁加载：pipeline 栈的 clickhouse 两个都加载（建账号）；observability 栈的 grafana 只加载 `clickhouse-grafana.env`；PAP-167/168 起服务读 `CLICKHOUSE_PATRA_PASSWORD`（Nacos starter 已会读 `~/.patra/secrets/*.env`）。
- 和 RabbitMQ 不同，密码写在 XML 里由 ClickHouse 每次启动读环境变量，**改密码只需改文件并重建容器**，不用进容器改。

### 4.5 `patra-tool-test`

两个初始化器沿用 `RabbitMQContainerInitializer` 的样子：静态 `getXxxContainer()` 懒启动、注册到 `ContainerRegistry`、同 JVM 共用；`initialize` 把连接参数写进 Spring 环境。

| 初始化器 | 容器类 | 镜像 | 写进环境的属性 |
|---|---|---|---|
| `ClickHouseContainerInitializer` | `org.testcontainers.clickhouse.ClickHouseContainer` | `clickhouse/clickhouse-server:26.8.36.3` | `patra.clickhouse.url`（HTTP 地址）、`patra.clickhouse.username`、`patra.clickhouse.password`；库名 `patra_analytics` |
| `KafkaContainerInitializer` | `org.testcontainers.kafka.KafkaContainer` | `apache/kafka-native:4.3.2` | `spring.kafka.bootstrap-servers` |

- ClickHouse 的属性名：Spring Boot 没有 ClickHouse 的自动配置，PAP-168 按这组名字绑定（编排会话已转告）。
- 不往 `api` 依赖里放客户端：ClickHouse 的 JDBC 驱动、`kafka-clients` 都不进 `patra-tool-test` 的对外依赖，由用到它们的服务自己引。示例集成测试里，Kafka 用 `integrationTestImplementation("org.apache.kafka:kafka-clients")`（版本由 Spring Boot 管理）；ClickHouse 直接用 JDK 的 `HttpClient` 打 HTTP 接口，顺便演示 ClickHouse 最原始的用法。
- 版本目录加 `testcontainers-kafka`、`testcontainers-clickhouse`（版本跟 BOM）。`testcontainers-clickhouse` 会传递带上 `testcontainers-jdbc`，不带驱动。
- 测试容器不挂 mini 的低内存配置与账号文件：测试关心的是 SQL 与客户端行为，账号由 `CLICKHOUSE_USER` / `PASSWORD` 环境变量建一个即可。

**示例集成测试**（`src/integrationTest/.../initializer/`）：

- `ClickHouseContainerInitializerIT`：初始化后环境里有三个属性；经 HTTP 建一张 `MergeTree` 表、写一行、读回同一行；多次获取是同一个容器。
- `KafkaContainerInitializerIT`：初始化后有 `bootstrap-servers`；用 Admin 建 topic、生产者发一条、消费者用 `assign` + 从头读（不用订阅，避免消费组重平衡让「发一条收一条」不稳），Awaitility 等到收到；多次获取是同一个容器。

### 4.6 CI 与路由

- `ci.yml` 的 `env` 加 `CLICKHOUSE_IMAGE: clickhouse/clickhouse-server:26.8.36.3`、`KAFKA_VERSION: 4.3.2`。preflight 检查：
  - 初始化器常量 `CLICKHOUSE_IMAGE` 与 `CLICKHOUSE_IMAGE` 相等，`docker-compose.pipeline.yaml` 里 clickhouse 的镜像也相等；
  - 初始化器常量 `KAFKA_IMAGE` 等于 `apache/kafka-native:$KAFKA_VERSION`，compose 里 kafka 与 kafka-init 的镜像都等于 `apache/kafka:$KAFKA_VERSION`。
- 预拉镜像：不加。现有预拉的三个镜像每个带集成测试的单元都要用；这两个只有 `patra-tool-test` 和以后的 analytics 用，Testcontainers 用到时自己拉。
- `detect-changes.sh`：`patra-infra/docker/clickhouse/*`、`patra-infra/docker/kafka/*`（如有）归为纯基建路径，补 `detect-changes.test.sh` 用例。
- `module-graph.json`：改依赖后跑 `./gradlew dumpModuleGraph`，有变化一并提交。

## 5. 文件清单

| 文件 | 变化 |
|---|---|
| `patra-infra/docker/docker-compose.pipeline.yaml` | 新增：clickhouse、kafka、kafka-init |
| `patra-infra/docker/clickhouse/config.d/patra.xml`、`users.d/patra.xml`、`initdb/01-database.sql` | 新增 |
| `patra-infra/docker/docker-compose.observability.yaml` | grafana 加插件环境变量、`env_file`，内存视实测 |
| `patra-infra/docker/grafana/provisioning/datasources/datasources.yaml` | 加 ClickHouse 数据源 |
| `patra-infra/docker/secrets/clickhouse.env.example`、`clickhouse-grafana.env.example` | 新增 |
| `patra-infra/scripts/compose-all.sh` | `ORDER` 加 `pipeline`（在 core 之后、observability 之前：grafana 要读 clickhouse-grafana.env，数据源启动时不要求 ClickHouse 已在线） |
| `patra-infra/scripts/init-volumes.sh` | 建 clickhouse、kafka 目录 |
| `patra-infra/cd/detect-changes.sh` + `.test.sh` | 新配置目录归为纯基建 |
| `patra-infra/docker/README.md`、`patra-infra/CLAUDE.md` | 结构、密钥表、服务 URL、新增「ClickHouse」「Kafka」两节、资源表 |
| `docs/patra/runbooks/v0.18-analytics-go-live-runbook.md` | 新增：v0.18 上线手册，本 Issue 建立并写 pipeline 栈的部署、验收与回滚，后续 Issue 往里加小节 |
| `gradle/libs.versions.toml` | 加两个 Testcontainers 模块 |
| `patra-tool/patra-tool-test/build.gradle.kts` | `api` 加两个模块；`integrationTestImplementation` 加 `kafka-clients` |
| `patra-tool/patra-tool-test/.../ContainerType.java` | 加 `CLICKHOUSE`、`KAFKA` |
| `.../initializer/ClickHouseContainerInitializer.java`、`KafkaContainerInitializer.java` | 新增 |
| `src/integrationTest/.../ClickHouseContainerInitializerIT.java`、`KafkaContainerInitializerIT.java` | 新增 |
| `patra-tool/patra-tool-test/README.md` | 初始化器清单 |
| `.github/workflows/ci.yml` | 镜像版本检查 |

## 6. 验证

本会话能做的：

1. `./gradlew :patra-tool:patra-tool-test:check :patra-tool:patra-tool-test:integrationTest` 通过（两个新 IT 起真实容器收发）。
2. **本机整栈演练**（MacBook，不碰 mini）：用临时 `HOME` 放假密钥，`docker compose -p` 起 pipeline 栈和一个 grafana，核对：
   - `docker stats`：ClickHouse 空闲与跑查询时都在 2 GiB 内；Kafka 在 1 GiB 内；记录实测值写进 runbook 作为对照。
   - 两个账号能登录；`grafana` 账号 `INSERT` 被拒；重建容器后照常。
   - `kafka-configs.sh --describe` 显示两个 topic 的配置与表格一致；重跑 `kafka-init` 幂等。
   - **压满测试**：用 `kafka-producer-perf-test.sh` 往 `events.raw` 写约 7 GB，观察 `events.raw` 三个分区目录总大小回落到 3.75 GiB 以内、不再增长。
   - Grafana 插件装上、数据源对 ClickHouse 执行 `SELECT 1` 成功。
3. `bash patra-infra/cd/detect-changes.test.sh` 通过；`docker compose -f ... config --quiet` 校验通过。

要在 mini 上做的（runbook，部署后）：AC 1~3 条的实测，记录部署前后 `docker stats` 与 `df -h`。

## 7. 部署方式：手动起，不进 CD

**选择**：和 PG、Redis、RabbitMQ 一样，在 mini 上 `git pull` 后 `bash patra-infra/scripts/compose-all.sh up pipeline observability`。

**为什么**：

- CD（`services.json` + `deploy.sh`）管的是**我们自己构建的镜像**：构建、比对镜像 tag、健康检查失败回滚到上一个好镜像。第三方中间件不需要构建，「回滚」也不是换镜像 tag，而是改回配置、处理数据目录，CD 那套逻辑用不上。
- 有状态的中间件不该跟着每次推送重启：CD 一旦把它们纳入，改一行 compose 就会重建 Kafka 与 ClickHouse。
- 仓库里纯基建路径本来就刻意不触发 CD（`detect-changes.sh`），现有所有中间件都是手动生效，保持一套规则。

**代价**：改了 pipeline 栈的配置要记得去 mini 手动 `up`。runbook 写清楚。

runbook（`docs/patra/runbooks/v0.18-analytics-go-live-runbook.md`，按项目惯例一版一份）内容：部署前记录（`docker stats --no-stream`、`df -h`、OrbStack 内存）→ 放密钥 → `init-volumes.sh` → `compose-all.sh up pipeline observability` → 验收（AC 逐条的命令与预期）→ 部署后记录 → 回滚（停栈并按需清数据目录；Grafana 去掉插件与数据源）。

## 8. 风险

| 风险 | 应对 |
|---|---|
| OrbStack 余量只剩约 0.6 GB（决策 C） | 部署前后记录内存；不够时先停 pipeline 栈（它是独立子栈，`compose-all.sh down pipeline` 一键腾出 3 GB），再定收紧上限还是调高 OrbStack |
| MacBook 解析 `linqibins-mac-mini` 走 MagicDNS，Shadowrocket 的假 IP 可能拖慢首次连接 | 现有 MinIO 跳转用的也是这个名字；连不上时用 `PATRA_INFRA_HOST` 改通告地址重建 kafka |
| Kafka 与 ClickHouse 的数据放在 OrbStack 的 bind mount 上 | PG、ES、RabbitMQ 已在同样的挂载上稳定运行；本机演练先验证 |
| mini 的 Grafana 拉不到插件 | runbook 的离线安装步骤 |

## 9. 决策记录

**用户确认**（11/01 经编排会话，原话「可以的」）：

1. Kafka 不设认证（PLAINTEXT）：事件里只有用户 ID、设备 ID、文献 ID、省级地区，没有令牌或密码；RabbitMQ 加密码是因为消息里有安全邮件的一次性令牌链接。生产环境的开法写在 4.2 节。
2. mini 用 `apache/kafka`（JVM），测试用 `apache/kafka-native`：打破「compose 与测试容器同一镜像」的惯例，CI 改为核对版本号（4.3.2）。

**我定的**：

- 子栈名 `pipeline`（project `patra-pipeline`），避开 apps 栈里将来的 `patra-analytics` 容器。
- 独立子栈而不是塞进 core / storage：内存紧张时能整栈停掉腾出 3 GB，又不影响核心服务。
- ClickHouse 26.8 LTS、Kafka 4.3.2、Grafana 插件 4.22.1。
- `events.raw` 3 个分区、两个 topic 的保留参数（4.2 节表格）；「5 GB」按整个 Kafka 算；关闭自动建 topic；保留检查间隔 1 分钟。
- ClickHouse 留 22 GB 磁盘余量才写（`keep_free_space_bytes`）。
- 密码变量避开镜像保留的 `CLICKHOUSE_PASSWORD`；Grafana 的只读密码单独一个文件。
- ClickHouse 两个账号，Grafana 只读；保留 `query_log`、`part_log` 7 天。
- 测试容器写 `patra.clickhouse.*` 属性；不往 `api` 依赖放客户端。
- 不在 CI 预拉两个新镜像。
- 手动部署、不进 CD（第 7 节）。
