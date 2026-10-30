# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 这个目录是什么

`patra-infra` 是 Patra 的**基建配置目录**，不是应用代码。它只包含：Docker Compose 编排（`docker/`）+ CD 路由与部署逻辑（`cd/`）+ macOS 运维脚本（`scripts/`）+ Nacos 配置文件（`nacos/`）。这里没有构建系统、没有 lint —— 改动通过 `docker compose` 重启容器或 `launchctl` 重载 agent 来"生效"。仅有的可执行测试是 `cd/detect-changes.test.sh`、`cd/deploy.test.sh` 与 `scripts/install-github-runner.test.sh`（纯 bash stub 单测，直接 `bash` 运行，改对应脚本时必须跑）；`scripts/nacos-push.sh` 由 starter 的 `NacosPushScriptIT` 对真实 Nacos 测试（`./gradlew :patra-tool:patra-tool-launch:integrationTest`）。

完整的部署手册、服务 URL、凭据、故障排查在 `docker/README.md`，本文件只补充架构大图景和容易踩的非显性约束。MacBook ↔ Mac mini 的连接 / 地址 / 路由类问题（含 tailscale、Shadowrocket、各组件 IP 注册）单独记在 `docs/mac-mini-connectivity.md`。

## 部署拓扑（理解一切的前提）

基础设施容器**不在本机（MacBook）跑**，而是全部部署在一台 **Mac mini**（tailscale MagicDNS 短名 `linqibins-mac-mini`，容器引擎 OrbStack）。MacBook 只跑 patra-api 应用进程，跨网络访问 Mac mini 上的容器。

- **网络无感切换**：在家同 LAN → MagicDNS 解析为 `192.168.1.11` 直连；离家 → 解析为 `100.103.73.27` 走 tailscale 隧道。无需任何切换配置。
- **应用侧单一开关**：所有微服务用 `PATRA_INFRA_HOST` 加宿主机映射端口访问基础设施。IDE 里是 mini 的 tailscale IP，容器里是 `linqibins-mac-mini`（compose 的 `extra_hosts` 指向宿主机网关，见 `docs/mac-mini-connectivity.md` §8）。yml 默认值 `127.0.0.1` 仅作本地应急 fallback。服务对外通告的地址统一读 `PATRA_ADVERTISE_IP`。改基建地址范式时要同步 patra-api 各 boot 模块的 yml（本目录范围外）。

因此**改 compose / .env 不能只在本机改**，要让 Mac mini 上的仓库副本 `git pull` 后重启容器。Mac mini 的部署 SSOT 是 **monorepo 的 sparse-checkout**（`~/Projects/patra`，仅 patra-infra）。日常迭代命令见 README "日常迭代"节，本质是 `ssh linqibin@linqibins-mac-mini 'cd ~/Projects/patra && git pull && bash patra-infra/scripts/compose-all.sh up'`。

## Compose 分栈结构

每个子栈是**独立的 Docker project**（`patra-core` / `patra-storage` / `patra-search` / `patra-observability` / `patra-tailnet` / `patra-jobs`），共享外部网络 `patra-net`。拆成多 project 是为了让 IDEA / OrbStack 的 Docker 视图按子栈分组显示，而非合成一个 `patra` 组。

| 子栈 / project | 内容 |
|---|---|
| `patra-core` | postgres / redis / nacos / rabbitmq |
| `patra-storage` | minio / minio-init |
| `patra-search` | elasticsearch |
| `patra-observability` | otel-collector / prometheus / loki / tempo / grafana / alertmanager |
| `patra-tailnet` | tailscale-gw（共享出向网关 + 发布 `patra-net` 网段 `192.168.97.0/24` 的子网路由，见 `docs/mac-mini-connectivity.md` §7） |
| `patra-jobs` | mysql-ops / xxl-job-admin / xxl-job-tailnet-route |
| `patra-apps` | registry / catalog / identity / interaction / gateway / portal / learn（应用容器，由 CD 自动部署） |

- **多 project 编排入口是 `scripts/compose-all.sh`**（取代已删除的 `docker-compose.dev.yaml`）。compose 的 `include:` 会把所有子栈合并进同一个 project 无法分组，多 project 只能逐个 `up`，故用脚本编排：`compose-all.sh up [stack...]` / `down` / `ps`。
- **网络 `patra-net` 声明为 `external: true`**，须先于任何子栈存在；`compose-all.sh up` 会幂等创建，网段写死为 `192.168.97.0/24`（子网路由依赖它，改网段须同步 `docker-compose.tailnet.yaml` 的 `TS_ROUTES`）。各 project 在共享网络上靠容器/服务名 DNS 互通（跨 project 同样生效，因 DNS 是网络作用域而非 project 作用域）。
- **路由边车 `xxl-job-tailnet-route` 在 `patra-jobs` 而非 `patra-tailnet`**：它用 `network_mode: "service:xxl-job-admin"` 共享 xxl-job-admin 的网络命名空间，`service:` 模式不能跨 project，故必须与 xxl-job-admin 同 project；`tailscale-gw` 作为共享网关独立在 `patra-tailnet`，边车靠 `patra-net` DNS 解析到它。
- 容器数据卷全是 bind mount，根目录是 Mac mini 上的 `~/.patra/docker/`，首次部署前必须跑 `scripts/init-volumes.sh` 建目录骨架（幂等）。

`xxl-job-admin` 用独立 MySQL 8 容器 `mysql-ops`（不暴露宿主机端口），因为它不支持 PG；**业务库一律走 PG**，`mysql-ops` 仅服务于这类不兼容 PG 的运维组件。

> **一次性迁移（从旧单 project `patra` 切到多 project）**：旧容器的 `container_name` 与新栈相同，不先拆旧 project 会因重名启动失败（安全失败，非数据丢失）。在 Mac mini 上 **git pull 前**先用旧文件拆掉：`docker compose -p patra down`（或对旧 `docker-compose.dev.yaml down`），再 pull、再 `compose-all.sh up`。bind mount 数据不受影响。

## 非显性约束（改之前必须知道）

1. **MacBook 应用直连 Mac mini 的 Nacos，不经 ssh tunnel。** 地址缺省为 `${PATRA_INFRA_HOST:127.0.0.1}:8848`，与其它基建同一个开关：5 个后端服务都由 Nacos starter 注入（`PATRA_NACOS_ADDR` 可整体覆盖）；容器里同样跟随 `PATRA_INFRA_HOST`（`.env.common` 设为 `linqibins-mac-mini`）。曾因 MTU 丢 gRPC HTTP/2 帧而必须走 ssh tunnel，2026-09-23 在当前拓扑（mini 官方 tailscale GUI）实测直连注册与心跳均正常，tunnel 已拆除（见 `docs/mac-mini-connectivity.md` §2）。若直连再次卡 STARTING，先按 §2 复核，别直接恢复 tunnel。

2. **Mac mini 非交互 ssh 找不到 docker。** 非登录 shell 不加载完整 profile，OrbStack 的 docker 路径需写进 `~/.zshenv`：`echo 'export PATH=/usr/local/bin:$PATH' >> ~/.zshenv`。否则远程 `ssh ... docker ...` 报 command not found。

3. **`.env` 含 dev 默认凭据且随仓库提交。** 这是有意为之 —— Mac mini 靠 `git pull` 同步这些配置（mysql-ops 的密码等）。这些是 dev 默认值、对应服务仅在 tailscale 内网暴露，公网打不到端口。公网上只有门户（`patra.linqibin.dev` 经 Cloudflare Tunnel，见 README「门户的公网入口」），往隧道里加别的服务前先确认不会把这些端口带出去。**Nacos 不在此例**：token 签名密钥、identity 和三个账号的密码只放 `~/.patra/secrets/`（见 README「密钥」「Nacos」）；原先提交在 `.env` 里的那组鉴权值已经公开，PAP-71 已轮换作废。新增密钥一律放 `~/.patra/secrets/`，不再往 `.env` 加；如未来引入真正敏感的生产密钥，须改走外部 secret 注入。

4. **CD 在 runner 工作区里执行，被 git 忽略的文件每次都会被清掉。** `actions/checkout` 默认 `clean: true`，每次运行前执行 `git clean -ffdx`，放在 compose 目录里的密钥文件活不过下一次部署。所以密钥放在仓库外的 `~/.patra/secrets/`，compose 按 `${HOME}` 绝对路径加载（runner 进程的 `HOME` 就是用户主目录），见 README「密钥」。

5. **RabbitMQ 的三个坑**（详见 README「RabbitMQ」）：hostname 固定为 `patra-rabbitmq`，变了会另起空的数据目录；它不读容器内存上限，`rabbitmq/conf.d/20-patra.conf` 的可用内存要和 compose 的 `limits.memory` 一起改；`patra` 账号只在首次启动时按 `rabbitmq.env` 创建，改文件不改密码，要在容器里 `rabbitmqctl change_password`。

## Self-hosted Runner 与 CD（多服务，Mac mini 原生构建）

5 个后端应用 + portal + learn 由 GitHub Actions CD 自动部署，链路见 `.github/workflows/cd.yml` / `portal-cd.yml` / `learn-cd.yml`，设计见 `docs/patra/specs/2026-06-08-backend-multiservice-cd-design.html`（单服务首版见 `2026-06-07-cd-macmini-design.html`）。**2026-08-27 架构修订：构建从 ubuntu(QEMU 交叉编译) 搬回 Mac mini 原生 arm64**——部署不再经翻墙代理从 GHCR 拉大镜像（曾致 EOF/20 分钟超时），amd64 架构错配事故结构性消除；GHCR 降级为「归档/回滚备源」，推送 best-effort 失败不阻塞部署。

- **服务 SSOT**：`patra-infra/cd/services.json`（`name / gradleTask / context / port / image / healthPath / healthMatch`）。**加新服务 = 加一个条目**（再在 compose 加 service 块、在 `patra-infra/nacos/dev/` 加 `<服务名>.yaml`），workflow 逻辑不变。条目顺序就是 `deploy.sh` 的部署顺序。portal / learn 条目为 deploy-only（构建在 portal-cd.yml / learn-cd.yml 的 docker build 内）。
- **learn（学习站）**：端口 4001，健康检查 `/api/health`（无 healthMatch，HTTP 成功即算健康），专属 workflow `learn-cd.yml`（pnpm 构建全在 `patra-learn/Dockerfile` 内），compose 服务名 `learn` / 容器 `patra-learn`，环境文件 `.env.learn`（纯静态站，不读 `.env.common`）。首次部署需等 learn-cd 首跑产出镜像，期间整栈 `compose-all.sh up` 报缺 learn 镜像属预期。
- **选择性构建**：`patra-infra/cd/detect-changes.sh` 按 git diff 做受影响单元路由，只构建/部署改动的服务。**改公共面**（`patra-tool/*` / `build-logic` / `gradle` / 根构建脚本 / `service.Dockerfile` / `docker-compose.apps.yaml` / `cd.yml` / `patra-infra/cd/*`）→ **重建全部 5 个**（正确性优先，宁可多建不可漏建）；docs / markdown 与**纯基建路径**（`patra-infra/scripts/*`、apps 以外的 `docker-compose.*.yaml` 及其配置目录、基建栈 `.env` / `*.example`、`otel-agent/`）不触发后端构建（它们靠 mini 上 `compose-all.sh` 手动生效）；应用容器的 `.env.common` 仍判全量（改了需重部署才生效）。`patra-infra/nacos/*/*.yaml` 置 `nacos_changed`，路由到 `foundation`（CI 跑配置检查）加用到它的服务：`patra-<服务名>.yaml` 对应该服务，`patra-common.yaml` 对应全部 5 个——CD 推送后重部署它们，因为库地址、MinIO 这类启动时读取的配置只有重启才生效。推送脚本（`nacos-push.sh`、`nacos-lib.sh`）只路由到 `foundation`（CI 跑推送集成测试）并置 `nacos_changed`，不重建服务。注意 `patra-infra/cd/*` **整体**视为 CD 关键输入（含 `deploy.sh`——部署逻辑变更也应触发全量重建+重部署以立即得到验证，属有意设计而非误伤）。
- **三段 job**：`detect-changes`（ubuntu，受影响单元路由）→ `nacos-push`（macmini；push 事件每次都跑，dispatch 只在 `nacos_changed` 时跑、回滚单服务不跑：先跑 `NacosConfigFilesTest`，再把 `nacos/dev` 同步到 Nacos；失败则不部署）→ `build-deploy`（macmini：`gradlew bootJar` → 原生 `docker build` → `deploy.sh` → GHCR 归档推送 best-effort）。
- **deploy.sh**（`patra-infra/cd/deploy.sh`，有单测 `deploy.test.sh`）：镜像就位（本地优先，缺失才回源 GHCR）→ arm64 断言 → 按 services.json 的条目顺序 up（identity 先于 gateway，interaction 在 catalog 之后）→ 健康检查（127.0.0.1，不用 localhost——IPv6 误报实际踩坑）→ 部署后验证（运行容器 tag == 期望）→ 不健康自动回滚到 last-good（记录在 mini `~/.patra/cd/last-good-<svc>`，服务级）。
- **共享分层 Dockerfile**：`patra-infra/docker/service.Dockerfile` 一份供 5 个服务共用（`--build-arg APP_PORT` 区分端口）；5 个服务都用 `patra.hexagonal-boot` 打 fat jar，Spring Boot 4 `jarmode=tools` 分层结构通用，依赖层在本机 daemon 长期缓存。
- **Nacos 配置写错时镜像回滚没用**：CD 先推送 Nacos、再部署，值不合法时服务刷新失败或启动失败，`deploy.sh` 回滚到 last-good 镜像也读同一份坏配置。恢复靠 git revert 该配置文件后推送（CD 推送正确配置并重部署对应服务），见 v0.9 release spec 决策 B。只用 `nacos-push.sh` 手动推回原文恢复不了：内容与出错前完全相同时 Nacos 客户端不再通知（失败的那版没被处理成功，监听器记的还是原版 md5），要重启服务或先推一版内容不同的配置。
- **触发与回滚**：push main 命中相关 paths 自动跑；`workflow_dispatch` 填 `service`（单服务名）+ `image_tag`（旧 sha）即回滚（跳过构建，本地镜像缓存优先、缺失才拉 GHCR）。健康检查失败时 deploy.sh 也会自动回滚。回滚用的是当前检出的 compose 和环境文件：删掉或改名旧镜像要读的已提交环境变量，就放弃了对那些旧镜像的直接回滚。例：PAP-66 删了 `CATALOG_REDIS_URL`，回滚到 10/05 那版 catalog 前要先在 `~/.patra/secrets/catalog.env` 补回这一行，且只在 Redis 加密码之前有效（见 v0.8 上线 runbook 第 4 节）。安全：不监听 `pull_request`，self-hosted runner 绝不跑 fork PR 代码；对 GitHub 出向长轮询、无入站端口。
- **构建环境（mini）**：与 MacBook 同套——Homebrew + brew 装 mise + `java@zulu-25.30.17.0` 全局钉版（升级时两台一起升）；`JAVA_HOME` 固化在 `~/actions-runner/.env`（连同 Clash 代理变量，launchd 不继承 shell 环境）；docker PATH 靠 `~/actions-runner/.path` 补 `/usr/local/bin` 与 `/opt/homebrew/bin`；Gradle/镜像层缓存常驻本机。
- **失败通知**：走 GitHub 原生（失败 run 推 App/邮件给触发者），不设自建通知通道——单人 dev 环境，自己 push 自己看结果（2026-08-28 决策，曾配过 ntfy 后拆除）。
- **runner 看门狗**：`runner-watchdog.yml` 每日 API 查在线 + mini canary（docker/磁盘/unhealthy 容器）。防「离线 30 天被 GitHub 注销」（2026-08 实际发生）。需 secrets `RUNNER_ADMIN_TOKEN`（fine-grained PAT，仅本仓库 Administration:Read）。
- **运维红线**：派发任务期间严禁重启 runner（杀 Worker）；runner 自更新已禁用（`--disableupdate`），升级=闲时不带参数重跑 `install-github-runner.sh`（已注册时跳过 config.sh，registration 不动）。
- **容器经宿主机网关访问基础设施**：应用容器加 `extra_hosts: linqibins-mac-mini:host-gateway`，PG、Redis、Nacos（含 gRPC 9848）、MinIO、xxl admin 都走映射端口，不经 tailscale。新增连基础设施的容器要同样加。
- **环境文件，密钥在仓库外**：`env_file` 顺序叠加 `.env.common`（本地变量 `PATRA_INFRA_HOST`、`PATRA_LOG_DIR`；5 个后端服务的其余配置都在 Nacos，源文件在 `nacos/dev/`）→ `${HOME}/.patra/secrets/` 下的密钥文件（`redis.env` 给 catalog/identity、`rabbitmq.env` 给 interaction/identity/notification、`gateway.env` 给 gateway、`nacos.env` 给 5 个后端服务，都是必需；`<svc>.env` 可选；后者覆盖同名）。**Redis 密码、网关私钥、外部数据源 API key（Scopus / 青果 proxy 等）一律只进 `~/.patra/secrets/`**，committed 文件只放内网 dev 默认值和公钥，Nacos 配置里只写 `${变量}`。必需文件缺一个，compose 对整个 apps 项目的命令都会失败（portal 也部署不了）。

## scripts 一览

| 脚本 | 跑在哪 | 作用 |
|---|---|---|
| `init-volumes.sh` | Mac mini | 首次部署建数据卷目录骨架和密钥目录 ~/.patra/secrets（幂等，不生成密钥） |
| `fetch-ip2region.sh` + `ip2region.properties` | Mac mini / MacBook | 按固定 tag 与 SHA-256 下载 identity 查 IP 属地的两个 xdb 到 ~/.patra/data/ip2region（幂等，已是该版本则跳过）；identity 的 Gradle 测试按同一份 properties 下载测试数据 |
| `install-github-runner.sh [token]` | Mac mini | 安装或升级 GitHub self-hosted runner（launchd 常驻服务，CD deploy job 在此执行）：首次安装带 registration token，已注册时不带参数即升级 |
| `install-tailscale-route-guard.sh` + `tailscale-route-guard.sh` | macOS（root LaunchDaemon） | 守护 tailnet 路由：Shadowrocket 等代理拨断重连时清除被抢占的克隆主机路由并 `tailscale down/up` 重协商 |
| `dev.patra.tailscale-route-guard.plist` | — | 上述路由守护 LaunchDaemon 的模板 |
| `nacos-init.sh` | Mac mini | Nacos 初始化：管理员、`dev` namespace、`patra-app` / `patra-push` 账号、角色和权限。幂等；权限以脚本里的清单为准，多的会被删掉 |
| `nacos-verify.sh [--expect-services]` | Mac mini | Nacos 加固验收，9 项 PASS / FAIL（加 `--expect-services` 为 10 项）；只读写探针 |
| `nacos-push.sh [--check] [目录]` | Mac mini（CD）/ MacBook | 把 nacos/dev 同步到 Nacos dev / DEFAULT_GROUP：发布、跳过未变化、删除多余；--check 只对比（0 一致 / 2 有差异 / 1 出错） |
| `nacos-lib.sh` | — | nacos-init、nacos-verify、nacos-push 共用的函数，只供 source |
