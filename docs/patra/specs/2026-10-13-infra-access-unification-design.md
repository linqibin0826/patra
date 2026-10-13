# 统一两种运行方式访问基础设施的路径（PAP-72）

> **Issue**：[PAP-72](https://linear.app/papertrace/issue/PAP-72)
> **版本**：[v0.9 Config Center](../release-specs/v0.9-config-center.md)（决策 A；Done 判定中"除 PAP-72 选定的本地变量外读到的配置没有差别"一条）
> **日期**：2026-10-13
> **状态**：已上线（2026-10-13）

## 1. 要解决的问题

同一个服务有两种运行方式：在 MacBook 的 IDE 里跑（`dev` profile），在 mini 的容器里跑（`container` profile）。两者连同一套基础设施，写法却完全不同：

- IDE 用 `${PATRA_INFRA_HOST}` 加宿主机映射端口（15432、16379、19000、7070 等）。
- 容器用 patra-net 上的容器名加容器内端口（`postgres:5432`、`redis:6379`、`minio:9000` 等）。

v0.9 要让每个服务在 Nacos 里只存一份配置（release spec 决策 A）。两种写法并存时做不到，所以要先把差异收敛成少数几个放在 Nacos 之外的本地变量。

**完成标准**：PAP-72 的 Acceptance Criteria，见第 9.3 节的对照表。

## 2. 范围

做：

- 选定容器访问基础设施的方式，在 mini 上实测后写入本设计。
- 确定本地变量的命名原则和清单。
- 按新方式调整 apps compose、jobs compose 的 rocketmq-dashboard、`.env.common`、`.env.<服务>`。
- 调整 6 个服务里与"对外通告地址"有关的配置。
- 统一 ingest 的 MinIO bucket 名；minio-init 随之调整。
- 切换到 mini 并逐项验证；同步更新文档。

不做：

- 去掉 `dev`、`container` 两个 profile，把配置搬进 Nacos（PAP-76、PAP-77）。
- Nacos 引导配置收进 starter、Nacos 地址变量的最终命名（PAP-74，约束见第 13 节）。
- 实际断开 mini 的 tailscale 做测试（理由见 5.3）。
- 网关在容器实例与本机实例之间轮询的问题（第 11 节）。

## 3. 现状事实（2026-10-13 核对）

| 项目 | IDE（`dev` profile） | 容器（`container` profile） |
|---|---|---|
| 基础设施地址 | `${PATRA_INFRA_HOST:127.0.0.1}`，本机 `env.zsh` 里是 `100.103.73.27` | 容器名：`postgres`、`redis`、`nacos`、`minio`、`rocketmq-namesrv`、`xxl-job-admin` |
| 端口 | 宿主机映射端口 | 容器内端口 |
| 注册到 Nacos 的地址 | `application-dev.yml` 里的 `discovery.ip: ${TAILSCALE_IP:}` | 不设，自动取容器 IP |
| xxl 执行器回调地址 | `xxl.job.executor.ip: ${XXL_JOB_EXECUTOR_IP:}`，`env.zsh` 里等于 `$TAILSCALE_IP` | 不设，自动探测 |
| 日志目录 | 默认 `./logs` | `PATRA_LOG_DIR=/app/logs` |
| ingest 的 bucket | `dev-ingest` | `patra-ingest-cache` |

其他事实：

- IDE 用 IP 而不是主机名，是因为 MacBook 上有 Shadowrocket：主机名可能被解析成假 IP，HTTP 客户端也会因为不匹配 `nonProxyHosts=100.*` 被送进代理（`env.zsh` 注释）。
- RocketMQ broker 用 `BROKER_IP1=linqibins-mac-mini` 注册。
- catalog 有两个 bucket：默认 bucket `patra-catalog-cache` 两边一致；期刊封面用代码默认值 `patra-catalog`，有 671 个对象。
- 存储组件首次上传时自动建 bucket（`MinioStorageProvider`）。mini 上实际只有 `dev-ingest`（空）和 `patra-catalog` 两个 bucket，容器配置的两个 cache bucket 从没写过数据。
- 应用容器不加载 OTel agent，不连 otel-collector；也没有服务连 Elasticsearch。

## 4. mini 上的实测（2026-10-13）

### 4.1 容器解析不了 mini 的主机名

在 `patra-ingest` 容器里，`getent hosts` 查 `linqibins-mac-mini` 和完整域名 `linqibins-mac-mini.taild06182.ts.net`，都查不到地址（退出码 2）。

由此得出：容器里的 ingest 从没连上过 broker。

- namesrv 上 broker 的地址是 `linqibins-mac-mini:10911`，容器拿到也解析不了。
- namesrv 上没有 `INGEST_TASK` topic，也没有 `%RETRY%ingest-task-consumer-group`。
- 这条链路在 mini 上还没跑过消息，所以问题一直没暴露。
- release spec 里"broker 两种运行方式都经主机名连接"，实际只对 IDE 成立。

### 4.2 按 IP 能连通

在 patra-ingest 容器里：

- 直连 mini 的 tailscale IP `100.103.73.27`，15432、10911 都通。
- 经 `host.docker.internal`（OrbStack 宿主机网关，0.250.250.254），15432、16379、8848、9848、19000、7070、9876、10911 都通。

### 4.3 `extra_hosts` 映射主机名

临时容器挂在 patra-net 上，加 `--add-host linqibins-mac-mini:host-gateway`：

- `linqibins-mac-mini` 解析为 0.250.250.254。
- 15432、16379、8848、9848、19000、7070、9876、10911、10909 全部能连上。
- PG 看到的来源地址是 patra-net 网关 `192.168.97.1`；用容器名连时，看到的是容器自己的 IP。

### 4.4 两条路径的速度

同一个临时容器里，分别经容器名和主机名测试：

| 测试 | 容器名 | 主机名（宿主机网关） |
|---|---|---|
| `\copy` 导出 114 MB 表（3 次） | 0.69～0.85 s | 0.70～0.81 s |
| `\copy` 导入会话临时表（3 次） | 1.01～1.02 s | 0.96～1.04 s |
| pgbench `SELECT 1`，8 个连接 20 秒 | 平均 0.053 ms，约 15 万次/秒 | 平均 0.263 ms，约 3 万次/秒 |
| pgbench `SELECT 1`，1 个连接 10 秒 | 平均 0.009 ms | 平均 0.085 ms |
| MinIO 512 MB 上传 / 下载（各 6 次） | 0.7～4.4 s / 0.6～1.4 s | 0.7～3.6 s / 0.7～1.6 s |

- **大批量传输：** 两条路径没有差别。MinIO 的波动来自磁盘，两条路径分不出快慢。
- **小查询往返：** 每次多 0.07～0.2 ms，是 OrbStack 端口转发的固定开销。逐条执行 100 万次 SQL 约多 1 分多钟；按批写入时可以忽略。

### 4.5 服务间调用

服务注册地址不变，服务间调用的三条路径都实测通过：

| 方向 | 地址 | 路径 |
|---|---|---|
| 容器 → 容器 | 容器 IP 192.168.97.x | patra-net 直达 |
| MacBook → 容器 | 容器 IP 192.168.97.x | tailscale 网关容器发布的子网路由，MacBook 上走 tailscale 网卡；registry 健康检查返回 200 |
| 容器 → MacBook | MacBook 的 tailscale IP | OrbStack 把容器出站流量交给 mini 宿主机，再经 tailscale 发出；临时 HTTP 监听返回 200 |

## 5. 方案与选择

### 5.1 候选

| 方案 | 做法 | IDE 与容器的差异 | tailscale 断开时 |
|---|---|---|---|
| B | 容器也写 mini 的 tailscale 地址：主机名解析不了，只能写死 `100.103.73.27` | 地址相同，但容器里写死一个 IP | 100.x 地址消失，容器连不上任何基础设施 |
| C | 容器继续用容器名加容器内端口，差异放进本地 env 文件 | 每个连接串的主机和端口都不同 | 不受影响 |
| **E（选定）** | 容器加 `extra_hosts: linqibins-mac-mini:host-gateway`，两边都用主机加映射端口 | 只有 `PATRA_INFRA_HOST` 的值不同 | 不受影响 |

### 5.2 选 E 的理由

- 只有 E 能同时满足"配置写法完全一致"和"容器不依赖 tailscale"。
- C 的差异是每个连接串的主机加端口，达不到"每个服务在 Nacos 里只存一份配置"。broker 主机名在容器里也仍然解析不了。
- E 顺带修好了 4.1 的 broker 问题。
- 代价只有小查询每次多约 0.1 ms，对 dev 环境可以接受。

### 5.3 E 对 tailscale 的依赖

- 容器访问基础设施的路径是：容器 → patra-net 网关 → OrbStack 宿主机网关（0.250.250.254）→ mini 上的映射端口，不经过 tailscale 网卡。tailscale 断开时这条路径不受影响。
- 不实际断开 mini 的 tailscale 做测试：MacBook 只能经 tailscale 到达 mini，断开就失去 SSH；万一自动恢复失败，需要到 mini 跟前处理。
- 跨机调用（4.5 的后两行）本来就依赖 tailscale，E 不改变这一点。

## 6. 本地变量约定

### 6.1 命名原则

应用只读按用途命名的 `PATRA_*` 变量。值从哪来（tailscale、容器 IP、部署平台注入）由各运行环境自己决定，应用不感知。不用 `TAILSCALE_IP` 这类按来源命名的变量：上线后可能根本没有 tailscale，名字就失真了。

### 6.2 清单

两种运行方式只在下列项上不同，它们都不进 Nacos：

| 变量 | 含义 | IDE | 容器（mini） | 以后上线 |
|---|---|---|---|---|
| `PATRA_INFRA_HOST` | dev 环境里基础设施所在的主机 | `100.103.73.27`（`env.zsh`） | `linqibins-mac-mini`（`.env.common`），靠 `extra_hosts` 指向宿主机网关 | 不存在，见 6.3 |
| `PATRA_ADVERTISE_IP` | 本实例通告给 Nacos 和 xxl 调度中心的地址；留空就自动探测 | `env.zsh` 里 `export PATRA_ADVERTISE_IP="$TAILSCALE_IP"` | 不设，自动取容器 IP | 通常不设；网络复杂时由部署平台注入，比如 k8s 的 Pod IP |
| `PATRA_LOG_DIR` | 日志目录 | 不设，默认 `./logs` | `/app/logs` | 按部署平台设 |
| Nacos 凭据 | `patra-app` 账号 | `~/.patra/secrets/nacos.env` | 同名文件，mini 上一份 | 由部署平台的密钥注入 |

`PATRA_ADVERTISE_IP` 替代 `TAILSCALE_IP` 和 `XXL_JOB_EXECUTOR_IP`。两者在 `env.zsh` 里本来就是同一个值，表达的都是"本实例对外通告的地址"。

端口不在清单里：两边都用宿主机映射端口。bucket 名、库名也不在清单里：两边取相同的值，以后放进 Nacos。

### 6.3 `PATRA_INFRA_HOST` 只属于 dev

- 生产环境的 PG、Redis、MinIO 不会在同一台主机上，一个变量表达不了。
- 不为此拆出每种基础设施一个主机变量。namespace 已经解决了这个问题：`dev` namespace 的配置写 `${PATRA_INFRA_HOST}:15432`，将来 `prod` namespace 直接写真实地址。

## 7. 改动

### 7.1 compose

`docker-compose.apps.yaml`：

- 新增锚点 `x-infra-hosts: &infra-hosts ["linqibins-mac-mini:host-gateway"]`。
- registry、object-storage、catalog、ingest、identity、gateway 加 `extra_hosts: *infra-hosts`。
- portal、learn 不加：portal 经容器名 `gateway:9528` 调网关，learn 是纯静态站，都不连基础设施。
- 文件头注释里 `.env.common` 的说明改为"主机名加映射端口"。

jobs 栈的 rocketmq-dashboard：

- 加同样的 `extra_hosts`。它按 broker 注册的主机名连 broker，在容器里同样解析不了。

### 7.2 env 文件

`.env.common`：

```
PATRA_INFRA_HOST=linqibins-mac-mini
MINIO_ENDPOINT=http://linqibins-mac-mini:19000
REDIS_HOST=linqibins-mac-mini
REDIS_PORT=16379
```

- 删除 `NACOS_HOST`、`NACOS_PORT`、`ROCKETMQ_NAMESRV`、`XXL_JOB_ADMIN_ADDRESSES`。删掉后，yml 里的默认值 `${NACOS_HOST:${PATRA_INFRA_HOST}}`、`${ROCKETMQ_NAMESRV:${PATRA_INFRA_HOST}:9876}`、`${XXL_JOB_ADMIN_ADDRESSES:http://${PATRA_INFRA_HOST}:7070/xxl-job-admin}` 自动生效，和 IDE 的写法一致。
- 注释从"patra-net 服务名 + 容器内端口"改为"mini 主机名 + 宿主机映射端口（与 IDE 一致）"。
- `SPRING_PROFILES_ACTIVE`、`PATRA_LOG_DIR`、MinIO 凭据、身份断言公钥不变。

`.env.<服务>`：

- 所有 DB URL 改为 `jdbc:postgresql://linqibins-mac-mini:15432/<库名>`。涉及 `.env.registry`、`.env.object-storage`、`.env.catalog`（业务库和 batch 库）、`.env.ingest`、`.env.identity`。
- `.env.catalog`、`.env.ingest` 删除 `XXL_JOB_EXECUTOR_LOG_PATH`。yml 默认值 `${PATRA_LOG_DIR}/xxl-job/<服务>` 在容器里算出的就是同一个路径。
- `.env.ingest` 的 `MINIO_BUCKET=patra-ingest-cache` 不变。

env 文件里写死主机名，而不引用 `PATRA_INFRA_HOST`：compose 的 env 文件不能引用别的 env 文件里的变量。PAP-77 把配置搬进 Nacos 时，这些行整体删除，改由 Nacos 里的 `${PATRA_INFRA_HOST}` 表达。

### 7.3 应用配置（patra-api）

- 6 个服务：`spring.cloud.nacos.discovery.ip: ${PATRA_ADVERTISE_IP:}` 从 `application-dev.yml` 移到 `application.yml`，两种运行方式共用。`application-dev.yml` 里的这一项及其注释删除。
- ingest、catalog 的 `application.yml`：`xxl.job.executor.ip` 改为 `${PATRA_ADVERTISE_IP:}`，注释同步改。
- ingest 的 `application-dev.yml`：bucket 从 `dev-ingest` 改为 `patra-ingest-cache`。
- 其余提到 `XXL_JOB_EXECUTOR_IP` 或 `TAILSCALE_IP` 的注释一并改掉。

这些改动只换变量名和配置位置，不改运行时逻辑。按项目约定，配置维护不走 TDD，由第 9 节的实测验证。

### 7.4 bucket

| 服务 | IDE 原值 | 容器原值 | 统一为 |
|---|---|---|---|
| ingest | `dev-ingest` | `patra-ingest-cache` | `patra-ingest-cache` |
| catalog 默认 bucket | `patra-catalog-cache` | `patra-catalog-cache` | 不变 |
| catalog 期刊封面 | `patra-catalog` | `patra-catalog` | 不变 |

- 命名不带环境前缀：环境由 namespace 表示。
- storage 栈的 minio-init 改为建 `patra-ingest-cache`，日志里的 bucket 名同步改。
- mini 上空的 `dev-ingest` bucket：切换时问用户是否删除。

### 7.5 本机 `env.zsh`（仓库外，由用户改）

- 删除 `export XXL_JOB_EXECUTOR_IP="$TAILSCALE_IP"`。
- 新增 `export PATRA_ADVERTISE_IP="$TAILSCALE_IP"`。
- `TAILSCALE_IP` 本身保留，应用不再读它。

## 8. 切换步骤

1. 在 feature branch 上完成第 7 节的仓库改动，跑本机验证（9.1）。
2. 用户按 7.5 改 `env.zsh`；IDE 起 ingest、catalog，验证通告地址（9.1 第 3 项）。
3. 经用户当轮明确授权后，把改动推到 main。env 文件改动会让 CD 判定为全量，6 个服务全部重建重部署。
4. 经用户确认后，在 mini 上 `git pull`，再跑 `compose-all.sh up storage jobs`（minio-init、rocketmq-dashboard）。
5. 逐项做 9.2 的验证。
6. 问用户是否删除空的 `dev-ingest` bucket。实际：用户同意，确认 0 个对象后已删除。

第 3、4 步的顺序可以互换：新镜像配旧 env 文件、旧镜像配新 env 文件都能跑（第 10 节）。

## 9. 验证

### 9.1 本机

1. `docker compose -f patra-infra/docker/docker-compose.apps.yaml config --quiet` 通过；`config` 输出里 6 个后端服务和 dashboard 都带 `extra_hosts`，portal、learn 不带。
2. `./gradlew check` 通过。
3. IDE 起 ingest、catalog 后：
   - Nacos 里这两个实例的地址是 MacBook 的 tailscale IP；
   - xxl-job-admin 执行器列表里，回调地址是 `http://<MacBook tailscale IP>:<端口>/`。

### 9.2 mini

| 检查项 | 判据 |
|---|---|
| 6 个服务健康 | deploy.sh 健康检查通过；`docker ps` 里没有 unhealthy |
| 走了新路径 | `pg_stat_activity` 的 `client_addr` 里不再出现 6 个应用容器的 IP，应用连接来自 `192.168.97.1`。实际：6 个业务库连接全部来自 `192.168.97.1`，只有 Nacos 自己按容器名连 `patra_nacos` |
| 主机名解析 | 应用容器里 `getent ahostsv4 linqibins-mac-mini` 返回 0.250.250.254 |
| Nacos 注册稳定 | 容器实例仍注册为 192.168.97.x；10 分钟后再查，实例都在且健康（gRPC 长连接现在经 OrbStack 端口转发）。实际：切换后与 10 分钟后两次 `nacos-verify.sh --expect-services` 均为通过 11 / 失败 0 |
| xxl 回调 | 容器执行器注册为容器 IP，admin 执行器列表显示在线 |
| broker | `mqadmin consumerConnection -g ingest-task-consumer-group` 能看到 ingest 容器的客户端（切换前看不到）。实际：判据不成立——`INGEST_TASK` topic 尚不存在，消费者拿不到路由，不向任何 broker 发心跳。改用 ingest 容器按主机名连 `linqibins-mac-mini:10911` 成功、namesrv 地址为 `linqibins-mac-mini:9876`、dashboard 按主机名取到 broker-a 统计；消费链路端到端留到 topic 首次发送时 |
| dashboard | rocketmq-dashboard 能显示 broker 统计 |
| 容器里没有旧变量 | 应用容器的环境变量里不再有 `NACOS_HOST`、`ROCKETMQ_NAMESRV`、`XXL_JOB_ADMIN_ADDRESSES`、`XXL_JOB_EXECUTOR_LOG_PATH` |

### 9.3 Acceptance Criteria 对照

| AC | 证据 |
|---|---|
| 设计记录实测结果和选择理由 | 本设计第 4、5 节 |
| 两种方式的差异只剩选定的本地变量，且都不在 Nacos 里 | 6.2 的清单；9.4 的对照表 |
| mini 全部服务容器在新做法下健康 | 9.2 的前 5 项 |

### 9.4 连接项对照

切换后，每个连接项在两种运行方式下的值：

| 连接项 | IDE | 容器 |
|---|---|---|
| PG | `${PATRA_INFRA_HOST}:15432/<库名>` | 同左 |
| Redis | `${PATRA_INFRA_HOST}:16379` | 同左 |
| Nacos | `${PATRA_INFRA_HOST}:8848`（gRPC 9848） | 同左 |
| MinIO | `http://${PATRA_INFRA_HOST}:19000`，bucket 见 7.4 | 同左 |
| RocketMQ namesrv | `${PATRA_INFRA_HOST}:9876` | 同左 |
| RocketMQ broker | `linqibins-mac-mini:10911`（broker 注册的地址） | 同左 |
| xxl-job-admin | `http://${PATRA_INFRA_HOST}:7070/xxl-job-admin` | 同左 |

容器侧的 env 文件现在是写死主机名的字面值，展开后与上表相同。

## 10. 回滚

- deploy.sh 回滚时用的是当前检出的 compose 和 env 文件。
- 旧镜像配新 env 文件能跑：
  - 旧镜像在容器里本来就不读 `TAILSCALE_IP`、`XXL_JOB_EXECUTOR_IP`；
  - Nacos、namesrv、xxl 地址回退到 `PATRA_INFRA_HOST`，再经 `extra_hosts` 解析。
- 整体退回旧做法：revert 本设计的提交，重新部署即可。

## 11. 已知限制（接受，本 Issue 不改）

- 小查询每次往返多约 0.1 ms（4.4）。
- 跨机调用依赖 tailscale：tailscale 断开时，MacBook 和 mini 之间互相调不到，mini 容器之间不受影响。
- MacBook 上起了同名服务后，mini 的网关会在容器实例和本机实例之间轮询，门户的部分请求会打到 MacBook。
- RocketMQ 同理：切换后容器里的 ingest 真正连上了 broker，与本机 ingest 同在 `ingest-task-consumer-group` 集群消费组里，消息按队列分给两边。本机调试时会有一部分任务被 mini 消费掉。需要独占时，本机改用别的消费组。切换前已核对 `ing_outbox_message` 为空，xxl 上也没有给 `ingestOutboxRelayJob` 配调度，不会有积压消息在切换后集中发出。
- 若 patra-net 的网关地址或 OrbStack 的宿主机网关机制变化，判据里的 `192.168.97.1` 和 0.250.250.254 要按实际调整。

## 12. 文档

| 文件 | 改动 |
|---|---|
| `patra-infra/docs/mac-mini-connectivity.md` | 新增一节"容器经主机名访问基础设施"：原理、4.3～4.4 的实测、5.3 的 tailscale 分析；§1 的变量名改为 `PATRA_ADVERTISE_IP`；§3 补充"容器靠 `extra_hosts` 解析 broker 主机名"；§6 的"现状"更新 |
| `patra-infra/CLAUDE.md` | "应用侧单一开关"、"容器内 Nacos 走服务名"、"环境文件三层"三处改为新约定 |
| `patra-infra/docker/README.md` | env 文件说明、故障排查里涉及旧变量和容器名坐标的部分 |
| `patra-api/README.md` 及各服务 README | 替换旧变量名 |
| `.claude/skills/patra-backend/skills/patra-hexagonal/resources/configuration.md` | 替换旧变量名，补本地变量约定 |
| `docs/patra/release-specs/v0.9-config-center.md` | 更正"broker 两种运行方式都经主机名连接"；"PAP-72 选 B 还是 C"改为"选定 E"，并链接本设计 |

历史 spec 和 plan 里的旧变量名不改：它们记录的是当时的状态。

## 13. 交给其他 Issue 的约束

- **PAP-74**：Nacos 的地址在启动前就要知道，上线后也一定是本地变量。starter 的引导配置按 6.1 的原则命名（例如 `PATRA_NACOS_ADDR`），默认回退到 `${PATRA_INFRA_HOST}:8848`。
- **PAP-76、PAP-77**：
  - Nacos 里的连接配置统一写 `${PATRA_INFRA_HOST}` 加映射端口；
  - `discovery.ip`、`xxl.job.executor.ip` 写 `${PATRA_ADVERTISE_IP:}`；
  - 迁移完成后，`.env.common`、`.env.<服务>` 只剩 6.2 的本地变量，加上 Nacos 凭据的引用。
