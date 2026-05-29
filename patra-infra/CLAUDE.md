# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 这个目录是什么

`patra-infra` 是 Patra 的**基建配置目录**，不是应用代码。它只包含：Docker Compose 编排（`docker/`）+ macOS 运维脚本（`scripts/`）。这里没有构建系统、没有测试、没有 lint —— 改动通过 `docker compose` 重启容器或 `launchctl` 重载 agent 来"生效"。

完整的部署手册、服务 URL、凭据、故障排查在 `docker/README.md`，本文件只补充架构大图景和容易踩的非显性约束。MacBook ↔ Mac mini 的连接 / 地址 / 路由类问题（含 tailscale、Shadowrocket、各组件 IP 注册）单独记在 `docs/mac-mini-connectivity.md`。

## 部署拓扑（理解一切的前提）

基础设施容器**不在本机（MacBook）跑**，而是全部部署在一台 **Mac mini**（tailscale MagicDNS 短名 `linqibins-mac-mini`，容器引擎 OrbStack）。MacBook 只跑 patra-api 应用进程，跨网络访问 Mac mini 上的容器。

- **网络无感切换**：在家同 LAN → MagicDNS 解析为 `192.168.1.11` 直连；离家 → 解析为 `100.73.7.112` 走 tailscale 隧道。无需任何切换配置。
- **应用侧单一开关**：所有微服务用一个环境变量 `PATRA_INFRA_HOST=linqibins-mac-mini` 指向容器；yml 默认值 `127.0.0.1` 仅作本地应急 fallback。改基建地址范式时要同步 patra-api 各 boot 模块的 yml（本目录范围外）。

因此**改 compose / .env 不能只在本机改**，要让 Mac mini 上的仓库副本 `git pull` 后重启容器。Mac mini 的部署 SSOT 是 **monorepo 的 sparse-checkout**（`~/Projects/patra`，仅 patra-infra）。日常迭代命令见 README "日常迭代"节，本质是 `ssh linqibin@linqibins-mac-mini 'cd ~/Projects/patra && git pull && docker compose -f patra-infra/docker/docker-compose.dev.yaml up -d --remove-orphans'`。

## Compose 分栈结构

`docker-compose.dev.yaml` 是主入口，用 `include:` 聚合 5 个子栈，可单独启动以加速：

| 子栈 | 内容 |
|---|---|
| `core` | postgres / redis / nacos |
| `storage` | minio |
| `search` | elasticsearch |
| `observability` | otel-collector / prometheus / loki / tempo / grafana / alertmanager |
| `jobs` | rocketmq(namesrv+broker) / xxl-job-admin（依赖独立 `mysql-ops` 容器） |

network 统一为 `patra-net`，project name 统一为 `patra`。容器数据卷根目录是 Mac mini 上的 `~/.patra/docker/`，首次部署前必须跑 `scripts/init-volumes.sh` 建目录骨架（幂等）。

`xxl-job-admin` 用独立 MySQL 8 容器 `mysql-ops`（不暴露宿主机端口），因为它不支持 PG；**业务库一律走 PG**，`mysql-ops` 仅服务于这类不兼容 PG 的运维组件。

## 非显性约束（改之前必须知道）

1. **Nacos gRPC 跨 tailscale 必须走 ssh tunnel，不能直连。** tailscale wireguard MTU=1280，Nacos gRPC 的 HTTP/2 SETTINGS frame 经 Docker bridge(1500)→OrbStack→tailscale 时超过 1280 被静默丢弃，nacos-client 永远收不到 SETTINGS ACK 而卡 STARTING。解决办法是 MacBook 上跑 `scripts/install-nacos-tunnel.sh install` 装一个 launchd agent（`dev.patra.nacos-tunnel`），把本机 `127.0.0.1:{8848,9848,8080}` ssh 转发到 Mac mini。应用因此用 `NACOS_HOST=127.0.0.1`（默认值）即可，**不要**把 Nacos 地址改成 `PATRA_INFRA_HOST`。

2. **Mac mini 非交互 ssh 找不到 docker。** 非登录 shell 不加载完整 profile，OrbStack 的 docker 路径需写进 `~/.zshenv`：`echo 'export PATH=/usr/local/bin:$PATH' >> ~/.zshenv`。否则远程 `ssh ... docker ...` 报 command not found。

3. **RocketMQ broker 注册地址由 `.env` 的 `BROKER_IP1` 决定**，注册错会导致客户端连不上。回退方案是改用 FQDN `linqibins-mac-mini.taild06182.ts.net`（见 README 故障排查）。

4. **`.env` / `.env.dev` 含真实 dev 凭据且随仓库提交。** 这是有意为之 —— Mac mini 靠 `git pull` 同步这些配置（PG/MinIO/MySQL 密码、`NACOS_AUTH_TOKEN`/`IDENTITY` 等）。这些是 dev 默认值、对应服务仅在 tailscale 内网暴露，公网打不到端口。新增密钥时沿用此约定（`.env.example` 是模板，Nacos token 用 `openssl rand -base64 32` 生成）；如未来引入真正敏感的生产密钥，须改走外部 secret 注入、不要提交。

## scripts 一览

| 脚本 | 跑在哪 | 作用 |
|---|---|---|
| `init-volumes.sh` | Mac mini | 首次部署建数据卷目录骨架（幂等） |
| `install-nacos-tunnel.sh {install\|uninstall\|status}` | MacBook | 装/卸 Nacos ssh tunnel launchd agent |
| `install-tailscale-route-guard.sh` + `tailscale-route-guard.sh` | macOS（root LaunchDaemon） | 守护 tailnet 路由：Shadowrocket 等代理拨断重连时清除被抢占的克隆主机路由并 `tailscale down/up` 重协商 |
| `*.plist` | — | 上述两个 launchd 任务的模板 |
